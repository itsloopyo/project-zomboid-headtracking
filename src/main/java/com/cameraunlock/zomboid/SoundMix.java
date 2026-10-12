package com.cameraunlock.zomboid;

import fmod.fmod.FMODSoundEmitter;
import fmod.javafmod;
import java.util.Arrays;
import java.util.Locale;

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
 * Logic thread: the game starts its sounds there.
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
            }
            count = kept;
        }
        if (count == instance.length) {
            int grown = count * 2;
            instance = Arrays.copyOf(instance, grown);
            eq = Arrays.copyOf(eq, grown);
            fader = Arrays.copyOf(fader, grown);
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
        long description = Fmod.instanceDescription(event);
        state[count] = description != 0L && Fmod.description3D(description) ? PLACED : FLAT;
        long now = System.nanoTime();
        float wanted = state[count] == PLACED ? muffledFor(from) : 0f;
        muffled[count] = wanted;
        applied[count] = wanted;
        workedOut[count] = now;
        startedAt[count] = now;
        // Until the mix is found nothing is put on a sound, so nothing is held for it.
        held[count] = system != 0L && wanted >= HELD_FROM;
        if (held[count]) {
            Fmod.pause(event, true);
        }
        count++;
    }

    /** Once a frame, after the first view's world is drawn. */
    static void frame() {
        if (failure != null) {
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
        }
        count = kept;
        reverb(ring, seconds);
        if (FirstPerson.TRACE && now - lastTrace > TRACE_INTERVAL_NANOS && (ring != null || ringOn)) {
            lastTrace = now;
            int muffledNow = 0;
            float most = 0f;
            for (int i = 0; i < count; i++) {
                if (applied[i] > 0.1f) {
                    muffledNow++;
                    most = Math.max(most, applied[i]);
                }
            }
            Log.info(String.format(Locale.ROOT, "sound: %d events, %d muffled (most %.1f dB), %d spare effects, reverb %s at %.2f: %s",
                    count, muffledNow, most, spare, ringOn ? "in" : "out", ringShare, ringApplied));
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
        if (eq[i] == 0L && muffled[i] < 0.1f && applied[i] < 0.1f) {
            applied[i] = 0f;
            return true;
        }
        if (eq[i] == 0L) {
            long group = Fmod.instanceGroup(instance[i], result);
            if (result[0] == Fmod.NOT_LOADED) {
                if (held[i] && now - startedAt[i] > HELD_AT_MOST_NANOS) {
                    held[i] = false;
                    if (!Fmod.pause(instance[i], false)) {
                        return false;
                    }
                    if (!heldTooLongSaid) {
                        heldTooLongSaid = true;
                        Log.info("sound: a muffled sound had no mix from FMOD after 250 ms and was let go on unmuffled");
                    }
                }
                return true;
            }
            if (result[0] == Fmod.INVALID_HANDLE) {
                return false;
            }
            Fmod.check(result[0], "EventInstance_GetChannelGroup");
            if (!put(i, group)) {
                // The sound ended between FMOD giving its mix and the effects going on.
                return false;
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
     * Puts an EQ and a fader on a sound's mix, at the end of its chain nearest its output, and
     * says whether the mix was still there. Either way the effects are the sound's until giveBack.
     */
    private static boolean put(int i, long group) {
        if (spare > 0) {
            spare--;
            eq[i] = spareEq[spare];
            fader[i] = spareFader[spare];
        } else {
            eq[i] = make(eqType);
            Fmod.setInt(eq[i], eqFilter, lowpassFilter);
            fader[i] = make(faderType);
        }
        Fmod.bypass(eq[i], false);
        Fmod.bypass(fader[i], false);
        bypassed[i] = false;
        set[i] = -1f;
        return Fmod.addEffectIfThere(group, Fmod.HEAD, fader[i]) && Fmod.addEffectIfThere(group, Fmod.HEAD, eq[i]);
    }

    /** Takes a sound's effects out of its mix, which FMOD may already have let go of with the sound, to be put on another. */
    private static void giveBack(int i) {
        if (eq[i] == 0L) {
            return;
        }
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
            Log.info(on ? "the space the view is in rings on what is heard" : "what is heard rings as the game mixes it");
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
        for (int i = 0; i < count; i++) {
            if (eq[i] != 0L) {
                Fmod.bypass(eq[i], true);
                Fmod.bypass(fader[i], true);
            }
            if (held[i]) {
                Fmod.pause(instance[i], false);
            }
            emitter[i] = null;
        }
        count = 0;
        if (reverb != 0L) {
            Fmod.bypass(reverb, true);
        }
    }
}
