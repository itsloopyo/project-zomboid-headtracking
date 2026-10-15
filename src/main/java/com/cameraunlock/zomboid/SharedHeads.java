package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import zombie.characters.IsoPlayer;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;
import zombie.network.packets.VariableSyncPacket;

/**
 * The head turn and the mouth of each player online who has this mod, shown on the characters of
 * the other players who have it.
 *
 * They travel as one of the character's animation variables, which the game keeps in step between
 * players itself: a variable named in VariableSyncPacket.syncedVariables that a client sets on its
 * own player goes to the server, which sets it on its copy of the player and hands it on to every
 * client near them, and to anyone who comes near later. No server mod and nothing else is needed.
 * A client without this mod keeps the variable on that character and nothing of the game's reads
 * it, since no animation names it.
 *
 * Shared where players can fight too: the owner ruled on 2026-10-15 that seeing another player's
 * head turn and mouth is no real advantage in a fight.
 */
final class SharedHeads {
    private SharedHeads() {}

    static final String VARIABLE = "CameraUnlockHead";
    /** The least time between two sends of a player's head and mouth: 15 a second. */
    private static final long SEND_EVERY_NANOS = 1_000_000_000L / 15;
    /** The time constant a shared head and mouth are eased towards each value that arrives with, in seconds, so 15 a second reads as smooth. */
    private static final float EASE_SECONDS = 0.08f;

    private static final String[] sent = new String[View.COUNT];
    private static final long[] checkedAt = new long[View.COUNT];
    private static boolean listed;

    /** What is shared of a player, written as the variable's value, and read back. Apart, so it is used without the game's classes. */
    static final class Wire {
        private Wire() {}

        static final int HEAD = 1, MOUTH = 2;
        /** Each value travels in hundredths: of a radian for the head, of the whole for the mouth. */
        private static final float STEP = 100f;
        /** The most a value read may be, in hundredths: a head is held to a neck's reach where it is turned, and a mouth is 0 to 1. */
        private static final int MOST_HEAD = 315, MOST_MOUTH = 100;

        /**
         * The value for a head turn (yaw, pitch, roll in radians) and a mouth (open, lips), either
         * null: "yaw,pitch,roll;open,lips" with either side left empty, and "" for neither.
         */
        static String write(float[] head, Mouths.Shape mouth) {
            if (head == null && mouth == null) {
                return "";
            }
            StringBuilder out = new StringBuilder(32);
            if (head != null) {
                out.append(Math.round(head[0] * STEP)).append(',').append(Math.round(head[1] * STEP)).append(',').append(Math.round(head[2] * STEP));
            }
            out.append(';');
            if (mouth != null) {
                out.append(Math.round(mouth.open() * STEP)).append(',').append(Math.round(mouth.lips() * STEP));
            }
            return out.toString();
        }

        /**
         * Reads a value into out (yaw, pitch, roll, open, lips) and says which of HEAD and MOUTH it
         * holds, or -1 for a value that is not one this writes. Another player's game wrote it.
         */
        static int read(String value, float[] out) {
            if (value.isEmpty()) {
                return 0;
            }
            int split = value.indexOf(';');
            if (split < 0 || value.indexOf(';', split + 1) >= 0) {
                return -1;
            }
            int has = 0;
            if (split > 0) {
                if (!numbers(value, 0, split, out, 0, 3, MOST_HEAD)) {
                    return -1;
                }
                has |= HEAD;
            }
            if (split + 1 < value.length()) {
                if (!numbers(value, split + 1, value.length(), out, 3, 2, MOST_MOUTH)) {
                    return -1;
                }
                if (out[3] < 0f) {
                    return -1;
                }
                has |= MOUTH;
            }
            return has;
        }

        /** Reads exactly count whole numbers apart by commas from value[from, to) into out from at, in hundredths, each within most of 0. */
        private static boolean numbers(String value, int from, int to, float[] out, int at, int count, int most) {
            int i = from;
            for (int n = 0; n < count; n++) {
                if (n > 0) {
                    if (i >= to || value.charAt(i) != ',') {
                        return false;
                    }
                    i++;
                }
                boolean negative = i < to && value.charAt(i) == '-';
                if (negative) {
                    i++;
                }
                int start = i, whole = 0;
                while (i < to && value.charAt(i) >= '0' && value.charAt(i) <= '9' && i - start < 4) {
                    whole = whole * 10 + value.charAt(i) - '0';
                    i++;
                }
                if (i == start || whole > most) {
                    return false;
                }
                out[at + n] = (negative ? -whole : whole) / STEP;
            }
            return i == to;
        }
    }

    /**
     * Called once a frame for each player at the screen, on the logic thread: shares their head
     * turn, and their mouth where TalkingMouths is on and their microphone is read, at most 15
     * times a second and only when it has changed.
     */
    static void send(View view, IsoPlayer player) {
        int at = view.index;
        if (player == null || !Online.joined()) {
            sent[at] = null;
            return;
        }
        long now = System.nanoTime();
        if (sent[at] != null && now - checkedAt[at] < SEND_EVERY_NANOS) {
            return;
        }
        checkedAt[at] = now;
        boolean shared = !player.isDead();
        String value = Wire.write(shared ? view.headTracking.headTurn() : null, shared && Mouths.on() && Microphones.listening(at) ? Microphones.mouth(at) : null);
        if (value.equals(sent[at])) {
            return;
        }
        if (!listed) {
            VariableSyncPacket.syncedVariables.add(VARIABLE);
            listed = true;
        }
        player.setVariable(VARIABLE, value);
        if (sent[at] == null || sent[at].isEmpty() != value.isEmpty()) {
            Log.info("player " + (at + 1) + (value.isEmpty() ? ": nothing of the head or mouth to share (tracking and microphone off, or the player is dead)"
                    : ": head and mouth shared with other players here who have this mod"));
        }
        sent[at] = value;
    }

    /** What has been read of another player, and their neck and head as the game posed them while they are turned. */
    private static final class Seen {
        String read = "";
        int has;
        final float[] wanted = new float[5], head = new float[3];
        long at;
        volatile float open = Float.NaN, lips;
        AnimationPlayer turnedIn;
        int neck, top;
        final Vector3f neckPlace = new Vector3f(), neckScale = new Vector3f(), topPlace = new Vector3f(), topScale = new Vector3f();
        final Quaternion neckTurn = new Quaternion(), topTurn = new Quaternion();
    }

    /** By the player's online number. Characters are posed on more than one thread. */
    private static final ConcurrentHashMap<Short, Seen> seen = new ConcurrentHashMap<>();
    private static final ThreadLocal<BoneTurn> boneTurn = ThreadLocal.withInitial(BoneTurn::new);
    private static boolean malformedSaid;

    /** Called once the bones of a skeleton are posed and the spine is twisted: turns another player's head as theirs is. */
    static void pose(AnimationPlayer animation) {
        if (!Online.joined() || !(animation.getIsoGameCharacter() instanceof IsoPlayer player) || player.isLocalPlayer()) {
            return;
        }
        String value = player.getVariableString(VARIABLE);
        Seen s = seen.get(player.getOnlineID());
        if (s == null) {
            if (value.isEmpty()) {
                return;
            }
            s = seen.computeIfAbsent(player.getOnlineID(), id -> new Seen());
        }
        synchronized (s) {
            if (!value.equals(s.read)) {
                s.read = value;
                s.has = Wire.read(value, s.wanted);
                if (s.has < 0) {
                    if (!malformedSaid) {
                        malformedSaid = true;
                        Log.info(String.format(Locale.ROOT, "player %d shared a head this does not read, left as the game poses it: \"%s\"",
                                player.getOnlineID(), value));
                    }
                    s.has = 0;
                }
            }
            boolean mouth = (s.has & Wire.MOUTH) != 0;
            s.lips = s.wanted[4];
            s.open = mouth ? s.wanted[3] : Float.NaN;
            long now = System.nanoTime();
            float seconds = s.at == 0L ? 1f : Math.min((now - s.at) / 1e9f, 1f);
            s.at = now;
            boolean head = (s.has & Wire.HEAD) != 0 && !player.isDead();
            float ease = 1f - (float) Math.exp(-seconds / EASE_SECONDS), most = 0f;
            for (int i = 0; i < 3; i++) {
                s.head[i] += ((head ? s.wanted[i] : 0f) - s.head[i]) * ease;
                most = Math.max(most, Math.abs(s.head[i]));
            }
            if (most < 1e-3f) {
                return;
            }
            SkinningData skeleton = animation.getSkinningData();
            SkinningBone neck = skeleton.getBone(ModelHead.NECK), top = skeleton.getBone(ModelHead.HEAD);
            if (neck == null || top == null || neck.parent == null) {
                return;
            }
            animation.getBoneTransformAt(neck.index).getPRS(s.neckPlace, s.neckTurn, s.neckScale);
            animation.getBoneTransformAt(top.index).getPRS(s.topPlace, s.topTurn, s.topScale);
            s.neck = neck.index;
            s.top = top.index;
            s.turnedIn = animation;
            ModelHead.turnHead(boneTurn.get(), animation, neck, top, s.head[0], s.head[1], s.head[2], ModelHead.NECK_SHARE);
        }
    }

    /** Called once the matrices the model is drawn with are made: puts back what pose turned. */
    static void posed(AnimationPlayer animation) {
        if (seen.isEmpty() || !(animation.getIsoGameCharacter() instanceof IsoPlayer player) || player.isLocalPlayer()) {
            return;
        }
        Seen s = seen.get(player.getOnlineID());
        if (s == null) {
            return;
        }
        synchronized (s) {
            if (s.turnedIn != animation) {
                return;
            }
            s.turnedIn = null;
            animation.getBoneTransformAt(s.neck).set(s.neckPlace, s.neckTurn, s.neckScale);
            animation.getBoneTransformAt(s.top).set(s.topPlace, s.topTurn, s.topScale);
        }
    }

    /** How far another player says their mouth is open, 0 to 1, or not a number where they share none. */
    static float open(IsoPlayer player) {
        Seen s = seen.get(player.getOnlineID());
        return s == null ? Float.NaN : s.open;
    }

    /** How another player says their lips are shaped, -1 to 1, where open answers a number. */
    static float lips(IsoPlayer player) {
        Seen s = seen.get(player.getOnlineID());
        return s == null ? 0f : s.lips;
    }

    /** Called as a save or a server is left. */
    static void forget() {
        seen.clear();
        Arrays.fill(sent, null);
    }
}
