package com.cameraunlock.zomboid;

import fmod.fmod.FMODSoundEmitter;
import fmod.javafmod;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Puts what Acoustics works out into the game's sound: a low pass and a fall in level on each
 * sound the walls muffle, and a reverb for the space the listener is in on everything heard in
 * the game. Both through FMOD's own effects (Fmod), which the game's sound engine runs and the
 * game's Java binding does not reach.
 *
 * Every event a sound emitter of the game's starts is noted as it starts. Each frame, for each
 * that is still playing and placed in the world, the first person view nearest it says how
 * muffled it is, scaled by how far that view's camera has come from the game's own, so that
 * the game's own view hears what it always has. A sound the walls do not muffle has no effect
 * put on it. The reverb is the one first person view's that is furthest in, and is out of the
 * mix while none is. The game mixes one sound for every player at the screen, so in split
 * screen with one player in first person and one not, both hear the first person player's
 * walls.
 *
 * Logic thread: the game starts its sounds there. A sound's effects go into its mix on FMOD's
 * own Studio thread, from the instance's callback as it starts (onStudioEvent): FMOD frees an
 * instance's mix on that thread whenever the sound ends, so only there is the mix certain to
 * be there while the effects go on.
 */
final class SoundMix {
    /** The bus every sound of the world is mixed through, and not the music or the menus. */
    private static final String WORLD_BUS = "bus:/InGame";
    /** How quickly what is applied follows what is worked out: a door opening or a doorway walked through, not a click. */
    private static final float MUFFLE_SECONDS = 0.08f, RING_SECONDS = 0.35f;
    /** How often each sound's muffling is worked out again, and the reverb's space. */
    private static final long MUFFLE_EVERY_NANOS = 100_000_000L;
    /**
     * A sound muffled this much as it starts is held until its effects are on it, which FMOD
     * lets be put on at its next update, so that the first moments of a shot through a wall are
     * muffled too. Never held longer than HELD_AT_MOST_NANOS.
     */
    private static final float HELD_FROM = 0.5f;
    private static final long HELD_AT_MOST_NANOS = 250_000_000L;
    private static final long TRACE_INTERVAL_NANOS = 1_000_000_000L;
    private static final int PLACED = 1, FLAT = 2;
    /** FMOD's effects are found by these names in what FMOD_DSP_GetInfo says of each type it has. */
    private static final String EQ_NAME = "FMOD Multiband Equalizer", FADER_NAME = "FMOD Fader", REVERB_NAME = "FMOD Reverb";

    private static int count;
    private static long[] instance = new long[256], eq = new long[256], fader = new long[256];
    private static Attachment[] attachment = new Attachment[256];
    private static FMODSoundEmitter[] emitter = new FMODSoundEmitter[256];
    private static int[] state = new int[256];
    /** Decibels the walls take from each sound now, what is applied as that is followed, and what its effects were last set to. */
    private static float[] muffled = new float[256], applied = new float[256], set = new float[256];
    private static boolean[] bypassed = new boolean[256], held = new boolean[256];
    private static boolean heldTooLongSaid;
    private static long[] workedOut = new long[256], startedAt = new long[256];
    /** Effects let go of with the sounds they were on, to be put on the next: an EQ and a fader each. */
    private static long[] spareEq = new long[64], spareFader = new long[64];
    private static int spare;

    private static long system, bus, busGroup, reverb;
    private static int eqType = -1, faderType = -1, reverbType = -1;
    private static int eqFilter, eqFrequency, faderGain;
    private static int lowpassFilter;
    private static int decay, early, late, hfDecay, diffusion, density, highCut, wet, dry;
    private static final Acoustics.Ring ringWanted = new Acoustics.Ring(), ringApplied = new Acoustics.Ring();
    private static float ringShare;
    private static boolean ringOn, ringStarted;
    private static long lastNanos, lastTrace;
    /** Dev only: set by a probe to hear the game's own mix where the mod's would be, for comparison. Logic thread. */
    static boolean withheld;
    /** Why sound is left as the game mixes it for the rest of the session, or null. */
    private static String failure;
    private static final int[] result = new int[1];
    /** The sounds whose effects the Studio thread is still to put on, by instance. */
    private static final ConcurrentHashMap<Long, Attachment> waiting = new ConcurrentHashMap<>();
    private static MemorySegment callback;
    /** The callback's own, as it runs on FMOD's thread: whether it is to do nothing more, and what it threw. */
    private static volatile boolean callbacksOff;
    private static volatile Throwable callbackFault;
    private static final int[] callbackResult = new int[1];
    private static boolean notLoadedSaid;
    /**
     * Instances the game has FMOD call back on, which are left as the game mixes them: FMOD
     * keeps one callback an instance, and the game's says when the power goes off
     * (AmbientStreamManager).
     */
    private static final Set<Long> gamesCallbacks = ConcurrentHashMap.newKeySet();
    private static long gamesCallbacksChecked;
    /** How the effects handed out came out since the last trace line, by Attachment state: set on the Studio thread, and ABANDONED on the game's. */
    private static final AtomicIntegerArray outcomes = new AtomicIntegerArray(9);

    /**
     * A sound's EQ and fader, and how far the Studio thread got putting them into its mix. The
     * Studio thread takes it from WAITING to ADDING and then to how that came out; the game's
     * thread takes it from WAITING to ABANDONED, and while it is ADDING waits for the outcome
     * before the effects go to another sound.
     */
    private static final class Attachment {
        static final int WAITING = 0, ADDING = 1, ON = 2, GONE = 3, REFUSED = 4, NOT_LOADED = 5, ABANDONED = 6, FAULTED = 7, NOT_STARTED = 8;
        final long instance, eq, fader;
        final AtomicInteger state = new AtomicInteger(WAITING);
        volatile int refusal;

        Attachment(long instance, long eq, long fader) {
            this.instance = instance;
            this.eq = eq;
            this.fader = fader;
        }
    }

    private SoundMix() {}

    /** Called by a sound emitter of the game's as it starts one of its events. */
    static void started(FMODSoundEmitter from, long event) {
        if (failure != null || event == 0L) {
            return;
        }
        try {
            note(from, event);
        } catch (RuntimeException | LinkageError thrown) {
            fail(thrown);
        }
    }

    private static void note(FMODSoundEmitter from, long event) {
        if (count == instance.length) {
            // Sounds started where no frame comes to let go of them, as in the menus, are let go of here.
            int kept = 0;
            for (int i = 0; i < count; i++) {
                if (Fmod.instanceValid(instance[i])) {
                    move(i, kept++);
                } else {
                    giveBack(i);
                }
            }
            for (int i = kept; i < count; i++) {
                emitter[i] = null;
                attachment[i] = null;
            }
            count = kept;
        }
        if (count == instance.length) {
            int grown = count * 2;
            instance = Arrays.copyOf(instance, grown);
            eq = Arrays.copyOf(eq, grown);
            fader = Arrays.copyOf(fader, grown);
            attachment = Arrays.copyOf(attachment, grown);
            emitter = Arrays.copyOf(emitter, grown);
            state = Arrays.copyOf(state, grown);
            muffled = Arrays.copyOf(muffled, grown);
            applied = Arrays.copyOf(applied, grown);
            set = Arrays.copyOf(set, grown);
            bypassed = Arrays.copyOf(bypassed, grown);
            held = Arrays.copyOf(held, grown);
            workedOut = Arrays.copyOf(workedOut, grown);
            startedAt = Arrays.copyOf(startedAt, grown);
        }
        instance[count] = event;
        emitter[count] = from;
        eq[count] = 0L;
        fader[count] = 0L;
        attachment[count] = null;
        long description = Fmod.instanceDescription(event);
        state[count] = description != 0L && Fmod.description3D(description) ? PLACED : FLAT;
        long now = System.nanoTime();
        float wanted = state[count] == PLACED ? muffledFor(from) : 0f;
        muffled[count] = wanted;
        applied[count] = wanted;
        workedOut[count] = now;
        startedAt[count] = now;
        held[count] = false;
        // Until the mix is found nothing is put on a sound, so nothing is held for it.
        if (state[count] == PLACED && system != 0L && !gamesCallbacks.contains(event) && !attach(count, event, wanted)) {
            giveBack(count);
            emitter[count] = null;
            return;
        }
        held[count] = attachment[count] != null && wanted >= HELD_FROM;
        if (held[count]) {
            Fmod.pause(event, true);
        }
        count++;
    }

    /**
     * Whether the walls are put into what an emitter plays, so that the game's own occlusion
     * of it is to give way: every sound of it still playing has its effects on or going on,
     * and an emitter with none noted yet would have them on what it starts.
     */
    static boolean wallsIn(FMODSoundEmitter from) {
        if (failure != null || system == 0L || withheld) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            // A flat sound is never given walls of its own, so it says nothing about the placed ones.
            if (emitter[i] != from || state[i] == FLAT) {
                continue;
            }
            if (attachment[i] == null) {
                return false;
            }
            int reached = attachment[i].state.get();
            if (reached != Attachment.WAITING && reached != Attachment.ADDING && reached != Attachment.ON
                    && reached != Attachment.GONE && reached != Attachment.NOT_STARTED) {
                return false;
            }
        }
        return true;
    }

    /** Called as the game has FMOD call it back on an instance's events, before it starts it. */
    static void gameCallsBack(long event) {
        gamesCallbacks.add(event);
        long now = System.nanoTime();
        if (now - gamesCallbacksChecked > TRACE_INTERVAL_NANOS) {
            gamesCallbacksChecked = now;
            gamesCallbacks.removeIf(called -> !Fmod.instanceValid(called));
        }
    }

    /** Once a frame, after the first view's world is drawn. */
    static void frame() {
        // The hearing patches are out of the game's code with either out, and nothing of the mod's goes into the mix.
        if (failure != null || !Part.FIRST_PERSON.patched() || !Part.HEAR_FROM_THE_EYE.patched()) {
            return;
        }
        try {
            mix();
        } catch (RuntimeException | LinkageError thrown) {
            fail(thrown);
        }
    }

    /** Fmod's own setup failing, its library or a function in it missing, comes as a LinkageError, and sound is left to the game as for any fault. */
    private static void fail(Throwable thrown) {
        failure = thrown.toString();
        Log.error("sound is left as the game mixes it for this session: putting the walls into it threw", thrown);
        try {
            letGo();
        } catch (RuntimeException | LinkageError again) {
            Log.error("and the mod's effects could not all be taken out of the mix", again);
        }
    }

    private static void mix() {
        long now = System.nanoTime();
        float seconds = lastNanos == 0L ? 0f : Math.min(0.25f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        if (system == 0L && !findTheMix()) {
            return;
        }
        Throwable thrown = callbackFault;
        if (thrown != null) {
            throw new IllegalStateException("putting a sound's effects into its mix on FMOD's Studio thread threw", thrown);
        }
        Hearing ring = null;
        for (int i = 0; i < View.COUNT; i++) {
            View view = View.of(i);
            if (!withheld && view != null && view.hearing.share() > 0f && (ring == null || view.hearing.share() > ring.share())) {
                ring = view.hearing;
            }
        }
        float muffleStep = 1f - (float) Math.exp(-seconds / MUFFLE_SECONDS);
        int kept = 0;
        for (int i = 0; i < count; i++) {
            if (!Fmod.instanceValid(instance[i])) {
                giveBack(i);
                continue;
            }
            if (state[i] == PLACED && muffle(i, now, muffleStep)) {
                move(i, kept++);
            } else if (state[i] == FLAT) {
                move(i, kept++);
            } else {
                giveBack(i);
            }
        }
        for (int i = kept; i < count; i++) {
            emitter[i] = null;
            attachment[i] = null;
        }
        count = kept;
        reverb(ring, seconds);
        if (FirstPerson.TRACE && now - lastTrace > TRACE_INTERVAL_NANOS && (ring != null || ringOn)) {
            lastTrace = now;
            int muffledNow = 0, on = 0;
            float most = 0f;
            for (int i = 0; i < count; i++) {
                if (attachment[i] != null && attachment[i].state.get() == Attachment.ON) {
                    on++;
                }
                if (applied[i] > 0.1f) {
                    muffledNow++;
                    most = Math.max(most, applied[i]);
                }
            }
            Log.info(String.format(Locale.ROOT, "sound: %d events, %d muffled (most %.1f dB), %d spare effects, reverb %s at %.2f: %s; "
                            + "%d with effects on; effects handed out since: put on %d, no mix at the start %d, ended before its start %d, FMOD did not start %d, let go before its start %d; "
                            + "%d instances left to the game's callbacks",
                    count, muffledNow, most, spare, ringOn ? "in" : "out", ringShare, ringApplied, on,
                    outcomes.getAndSet(Attachment.ON, 0), outcomes.getAndSet(Attachment.NOT_LOADED, 0), outcomes.getAndSet(Attachment.GONE, 0),
                    outcomes.getAndSet(Attachment.NOT_STARTED, 0), outcomes.getAndSet(Attachment.ABANDONED, 0), gamesCallbacks.size()));
        }
    }

    /**
     * Works out how muffled a placed sound is now and puts it on the sound, and says whether it
     * is still to be kept: a sound FMOD no longer has a mix for is not.
     */
    private static boolean muffle(int i, long now, float step) {
        if (now - workedOut[i] >= MUFFLE_EVERY_NANOS) {
            workedOut[i] = now;
            muffled[i] = muffledFor(emitter[i]);
        }
        Attachment on = attachment[i];
        if (on == null) {
            // Started before the mix was found: nothing is put on it.
            applied[i] = 0f;
            return true;
        }
        int reached = on.state.get();
        if (reached == Attachment.GONE || reached == Attachment.NOT_STARTED) {
            return false;
        }
        if (reached == Attachment.REFUSED) {
            Fmod.check(on.refusal, "ChannelGroup_AddDSP or EventInstance_GetChannelGroup");
        }
        if (reached == Attachment.NOT_LOADED && !notLoadedSaid) {
            notLoadedSaid = true;
            Log.info("sound: a sound had no mix from FMOD as it started and is heard unmuffled");
        }
        if (held[i] && reached == Attachment.WAITING) {
            if (now - startedAt[i] <= HELD_AT_MOST_NANOS) {
                return true;
            }
            if (!heldTooLongSaid) {
                heldTooLongSaid = true;
                Log.info("sound: a muffled sound had no mix from FMOD after 250 ms and was let go on unmuffled");
            }
        }
        applied[i] = held[i] ? muffled[i] : applied[i] + (muffled[i] - applied[i]) * step;
        boolean off = applied[i] < 0.05f;
        if (off != bypassed[i]) {
            Fmod.bypass(eq[i], off);
            Fmod.bypass(fader[i], off);
            bypassed[i] = off;
        }
        if (!off && Math.abs(applied[i] - set[i]) > 0.05f) {
            Fmod.setFloat(eq[i], eqFrequency, Acoustics.cutoff(applied[i]));
            Fmod.setFloat(fader[i], faderGain, -applied[i]);
            set[i] = applied[i];
        }
        if (held[i]) {
            held[i] = false;
            return Fmod.pause(instance[i], false);
        }
        return true;
    }

    /** Decibels the walls take from what an emitter plays, by the first person view nearest it, as far in as that view is. */
    private static float muffledFor(FMODSoundEmitter from) {
        float muffledBy = 0f, nearest = Float.MAX_VALUE;
        for (int v = 0; v < View.COUNT && !withheld; v++) {
            View view = View.of(v);
            if (view == null || view.hearing.share() <= 0f || !view.hearing.hearsRound()) {
                continue;
            }
            float far = view.hearing.distanceTo(from.x, from.y);
            if (far < nearest) {
                nearest = far;
                muffledBy = view.hearing.muffled(from.x, from.y, from.z) * view.hearing.share();
            }
        }
        return muffledBy;
    }

    /**
     * Gives a placed sound an EQ and a fader, set for how muffled it is as it starts, for the
     * Studio thread to put into its mix as it starts, and says whether the instance is still
     * there. Either way the effects are the sound's until giveBack.
     */
    private static boolean attach(int i, long event, float wanted) {
        if (spare > 0) {
            spare--;
            eq[i] = spareEq[spare];
            fader[i] = spareFader[spare];
        } else {
            eq[i] = make(eqType);
            Fmod.setInt(eq[i], eqFilter, lowpassFilter);
            fader[i] = make(faderType);
        }
        boolean off = wanted < 0.05f;
        Fmod.bypass(eq[i], off);
        Fmod.bypass(fader[i], off);
        bypassed[i] = off;
        set[i] = -1f;
        if (!off) {
            Fmod.setFloat(eq[i], eqFrequency, Acoustics.cutoff(wanted));
            Fmod.setFloat(fader[i], faderGain, -wanted);
            set[i] = wanted;
        }
        Attachment on = new Attachment(event, eq[i], fader[i]);
        attachment[i] = on;
        waiting.put(event, on);
        return Fmod.setCallback(event, callback, Fmod.STARTING | Fmod.STARTED | Fmod.START_FAILED);
    }

    /**
     * FMOD_STUDIO_EVENT_CALLBACK, on FMOD's Studio thread: puts a starting sound's effects at
     * the end of its mix's chain nearest its output. FMOD frees a sound's mix on this thread as
     * the sound ends, so only here can the mix not go between being asked for and taking them.
     */
    private static int onStudioEvent(int type, MemorySegment event, MemorySegment parameters) {
        try {
            Attachment on = callbacksOff ? null : waiting.get(event.address());
            if (on == null) {
                return Fmod.OK;
            }
            if (type == Fmod.START_FAILED) {
                if (on.state.compareAndSet(Attachment.WAITING, Attachment.NOT_STARTED)) {
                    waiting.remove(event.address(), on);
                    outcomes.incrementAndGet(Attachment.NOT_STARTED);
                }
                return Fmod.OK;
            }
            long group = Fmod.instanceGroup(event.address(), callbackResult);
            int found = callbackResult[0];
            if (found == Fmod.NOT_LOADED && type == Fmod.STARTING || !on.state.compareAndSet(Attachment.WAITING, Attachment.ADDING)) {
                return Fmod.OK;
            }
            // Taken: the state leaves ADDING whatever happens, as the game's thread waits on it.
            int outcome = Attachment.FAULTED;
            try {
                outcome = attachOn(on, found, group);
            } catch (Throwable thrown) {
                callbackFault = thrown;
            } finally {
                on.state.set(outcome);
                outcomes.incrementAndGet(outcome);
            }
        } catch (Throwable thrown) {
            callbackFault = thrown;
        }
        return Fmod.OK;
    }

    /** On the Studio thread, puts a taken sound's effects into the mix FMOD gave for it, and says how that came out. */
    private static int attachOn(Attachment on, int found, long group) {
        waiting.remove(on.instance, on);
        if (found == Fmod.INVALID_HANDLE) {
            return Attachment.GONE;
        }
        if (found == Fmod.NOT_LOADED) {
            return Attachment.NOT_LOADED;
        }
        if (found == Fmod.OK) {
            found = Fmod.addEffect(group, Fmod.HEAD, on.fader);
        }
        if (found == Fmod.OK) {
            found = Fmod.addEffect(group, Fmod.HEAD, on.eq);
        }
        on.refusal = found;
        return found == Fmod.OK ? Attachment.ON : Attachment.REFUSED;
    }

    /** Takes a sound's effects out of its mix, which FMOD may already have let go of with the sound, to be put on another. */
    private static void giveBack(int i) {
        if (eq[i] == 0L) {
            return;
        }
        Attachment on = attachment[i];
        if (on != null) {
            waiting.remove(instance[i], on);
            if (on.state.compareAndSet(Attachment.WAITING, Attachment.ABANDONED)) {
                outcomes.incrementAndGet(Attachment.ABANDONED);
            }
            while (on.state.get() == Attachment.ADDING) {
                Thread.onSpinWait();
            }
            if (on.state.get() == Attachment.FAULTED) {
                throw new IllegalStateException("putting a sound's effects into its mix on FMOD's Studio thread threw", callbackFault);
            }
        }
        attachment[i] = null;
        Fmod.disconnect(eq[i]);
        Fmod.disconnect(fader[i]);
        if (spare == spareEq.length) {
            spareEq = Arrays.copyOf(spareEq, spare * 2);
            spareFader = Arrays.copyOf(spareFader, spare * 2);
        }
        spareEq[spare] = eq[i];
        spareFader[spare] = fader[i];
        spare++;
        eq[i] = 0L;
        fader[i] = 0L;
    }

    private static void move(int from, int to) {
        if (from == to) {
            return;
        }
        instance[to] = instance[from];
        emitter[to] = emitter[from];
        eq[to] = eq[from];
        fader[to] = fader[from];
        attachment[to] = attachment[from];
        state[to] = state[from];
        muffled[to] = muffled[from];
        applied[to] = applied[from];
        set[to] = set[from];
        bypassed[to] = bypassed[from];
        held[to] = held[from];
        workedOut[to] = workedOut[from];
        startedAt[to] = startedAt[from];
    }

    /** Follows what the furthest in first person view's space asks of the reverb, and has it out of the mix while no view is in. */
    private static void reverb(Hearing ring, float seconds) {
        float shareWanted = ring == null ? 0f : ring.share();
        if (ring != null) {
            ring.ring(ringWanted);
        }
        if (!ringStarted && ring != null) {
            ringApplied.set(ringWanted);
            ringStarted = true;
        }
        float step = 1f - (float) Math.exp(-seconds / RING_SECONDS);
        ringShare += (shareWanted - ringShare) * step;
        if (ring != null) {
            ringApplied.decayMs += (ringWanted.decayMs - ringApplied.decayMs) * step;
            ringApplied.earlyMs += (ringWanted.earlyMs - ringApplied.earlyMs) * step;
            ringApplied.lateMs += (ringWanted.lateMs - ringApplied.lateMs) * step;
            ringApplied.hfDecayPercent += (ringWanted.hfDecayPercent - ringApplied.hfDecayPercent) * step;
            ringApplied.highCutHz += (ringWanted.highCutHz - ringApplied.highCutHz) * step;
            ringApplied.diffusion += (ringWanted.diffusion - ringApplied.diffusion) * step;
            ringApplied.density += (ringWanted.density - ringApplied.density) * step;
            ringApplied.wetDb += (ringWanted.wetDb - ringApplied.wetDb) * step;
        }
        boolean on = ringShare > 0.01f && ringApplied.wetDb > -79f;
        if (on != ringOn) {
            ringOn = on;
            Fmod.bypass(reverb, !on);
            if (DevBuild.ON) {
                Log.info(on ? "the space the view is in rings on what is heard" : "what is heard rings as the game mixes it");
            }
        }
        if (on) {
            Fmod.setFloat(reverb, decay, ringApplied.decayMs);
            Fmod.setFloat(reverb, early, ringApplied.earlyMs);
            Fmod.setFloat(reverb, late, ringApplied.lateMs);
            Fmod.setFloat(reverb, hfDecay, ringApplied.hfDecayPercent);
            Fmod.setFloat(reverb, highCut, ringApplied.highCutHz);
            Fmod.setFloat(reverb, diffusion, ringApplied.diffusion);
            Fmod.setFloat(reverb, density, ringApplied.density);
            Fmod.setFloat(reverb, wet, Math.max(-80f, ringApplied.wetDb + 20f * (float) Math.log10(Math.max(ringShare, 1e-4f))));
        }
    }

    /**
     * Finds the world's bus and FMOD's effects, and puts the reverb, out of the mix, at the end
     * of the bus's chain. FMOD makes a bus's mix at its update after it is first asked for, so
     * this says whether it is there yet.
     */
    private static boolean findTheMix() {
        if (bus == 0L) {
            bus = javafmod.FMOD_Studio_System_GetBus(WORLD_BUS);
            if (bus == 0L || !Fmod.busValid(bus)) {
                throw new IllegalStateException("the game's FMOD has no " + WORLD_BUS);
            }
            Fmod.lockBus(bus);
        }
        busGroup = Fmod.busGroup(bus);
        if (busGroup == 0L) {
            return false;
        }
        long found = Fmod.groupSystem(busGroup);
        StringBuilder offered = new StringBuilder();
        for (int type = 0; type < 64 && (eqType < 0 || faderType < 0 || reverbType < 0); type++) {
            long dsp = Fmod.createEffect(found, type, result);
            if (dsp == 0L) {
                continue;
            }
            String name = Fmod.effectName(dsp);
            offered.append(offered.isEmpty() ? "" : ", ").append(type).append(' ').append(name);
            if (name.equals(EQ_NAME)) {
                eqType = type;
            } else if (name.equals(FADER_NAME)) {
                faderType = type;
            } else if (name.equals(REVERB_NAME)) {
                reverbType = type;
            }
            Fmod.release(dsp);
        }
        if (eqType < 0 || faderType < 0 || reverbType < 0) {
            throw new IllegalStateException("FMOD has no " + (eqType < 0 ? EQ_NAME : faderType < 0 ? FADER_NAME : REVERB_NAME) + " effect: it has " + offered);
        }
        callback = Fmod.eventCallback(MethodHandles.lookup(), "onStudioEvent");
        system = found;
        long probe = make(eqType);
        eqFilter = Fmod.parameter(probe, "A) Filter", Fmod.INT);
        eqFrequency = Fmod.parameter(probe, "A) Frequency", Fmod.FLOAT);
        lowpassFilter = Fmod.valueOf(probe, eqFilter, "Low-pass 24dB");
        Fmod.release(probe);
        probe = make(faderType);
        faderGain = Fmod.parameter(probe, "Gain", Fmod.FLOAT);
        Fmod.release(probe);
        reverb = make(reverbType);
        decay = Fmod.parameter(reverb, "Decay Time", Fmod.FLOAT);
        early = Fmod.parameter(reverb, "Early Delay", Fmod.FLOAT);
        late = Fmod.parameter(reverb, "Late Delay", Fmod.FLOAT);
        hfDecay = Fmod.parameter(reverb, "HF Decay Ratio", Fmod.FLOAT);
        diffusion = Fmod.parameter(reverb, "Diffusion", Fmod.FLOAT);
        density = Fmod.parameter(reverb, "Density", Fmod.FLOAT);
        highCut = Fmod.parameter(reverb, "High Cut", Fmod.FLOAT);
        wet = Fmod.parameter(reverb, "Wet Level", Fmod.FLOAT);
        dry = Fmod.parameter(reverb, "Dry Level", Fmod.FLOAT);
        Fmod.setFloat(reverb, dry, 0f);
        Fmod.setFloat(reverb, wet, -80f);
        Fmod.bypass(reverb, true);
        Fmod.check(Fmod.addEffect(busGroup, Fmod.HEAD, reverb), "ChannelGroup_AddDSP reverb");
        Log.info("sound: the walls and the room are put into " + WORLD_BUS + " through FMOD's " + EQ_NAME + ", " + FADER_NAME + " and " + REVERB_NAME);
        return true;
    }

    private static long make(int type) {
        long dsp = Fmod.createEffect(system, type, result);
        Fmod.check(result[0], "System_CreateDSPByType " + type);
        return dsp;
    }

    /** After a fault: every effect of the mod's out of the mix, as far as FMOD lets them be taken out. */
    private static void letGo() {
        callbacksOff = true;
        waiting.clear();
        for (int i = 0; i < count; i++) {
            if (eq[i] != 0L) {
                Fmod.bypass(eq[i], true);
                Fmod.bypass(fader[i], true);
            }
            if (held[i]) {
                Fmod.pause(instance[i], false);
            }
            emitter[i] = null;
            attachment[i] = null;
        }
        count = 0;
        if (reverb != 0L) {
            Fmod.bypass(reverb, true);
        }
    }
}
