package com.cameraunlock.zomboid;

import java.util.Locale;

/**
 * What the hands do before the eye while the character is busy with one of the game's timed
 * actions: eating, drinking, reading, bandaging, opening a can, hammering, sawing, searching a
 * container and the rest. The game names what its own animation of the character is doing in
 * the animation variable PerformingAction, which every timed action sets when it starts; each
 * name is sorted here into one of a few ways of working with the hands, and each way is a
 * motion the hands go through for as long as the action lasts.
 *
 * Places are in metres from the eye in the aim's axes: x to the right, y up and z towards the
 * viewer, so straight ahead is -z.
 */
final class TimedActs {
    private TimedActs() {}

    static final int NONE = 0, MOUTH = 1, DRINK = 2, READ = 3, WORK = 4, CRANK = 5, WRAP = 6, HAMMER = 7, SAW = 8, RUMMAGE = 9,
            SCRUB = 10, POUR = 11, SWING = 12, WELD = 13, FACE = 14;
    /** The hand poses of the mod's own arms a hand takes here, by their names in the arm model. */
    static final int OPEN = 0, GRIP = 1, PINCH = 2, HANDLE = 3;
    static final String[] POSE_NAMES = {"open", "grip", "pinch", "handle"};

    /** Where a hand that has nothing to do is: below the picture, the right's then the left's. */
    static final float[][] AWAY = {{0.20f, -0.52f, -0.30f}, {-0.20f, -0.52f, -0.30f}};
    /** How much lower and further ahead the hands work at something on the floor, in metres. */
    static final float LOW_DOWN = 0.06f, LOW_AHEAD = 0.10f;

    /**
     * The way of working an animation of the game's is, by its name. Reloading a firearm and
     * taking up or putting away what is held are the view model's own, and are NONE here.
     */
    static int kind(String anim) {
        String a = anim.toLowerCase(Locale.ROOT);
        if (a.isEmpty() || a.equals("reload") || a.equals("equipitem") || a.equals("unequipitem") || a.equals("dropwhilemoving")) {
            return NONE;
        }
        if (a.equals("eat") || a.equals("takepills")) {
            return MOUTH;
        }
        if (a.equals("drink")) {
            return DRINK;
        }
        if (a.equals("read")) {
            return READ;
        }
        if (a.equals("washface") || a.equals("shave") || a.equals("drink_tap")) {
            return FACE;
        }
        if (a.equals("bandage") || a.equals("applyalcohol") || a.equals("medicalcheck")) {
            return WRAP;
        }
        if (a.startsWith("opentincan") || a.startsWith("openpopcan") || a.startsWith("openbeer") || a.startsWith("uncork") || a.startsWith("openchampagne")) {
            return CRANK;
        }
        if (a.contains("saw")) {
            return SAW;
        }
        if (a.startsWith("scrub") || a.equals("paint") || a.equals("vehiclewash")) {
            return SCRUB;
        }
        if (a.startsWith("blowtorch") || a.startsWith("welding")) {
            return WELD;
        }
        if (a.startsWith("pour") || a.startsWith("fill_") || a.startsWith("refuel") || a.startsWith("takegas") || a.equals("mixfluids")) {
            return POUR;
        }
        if (a.startsWith("chop") || a.startsWith("dig") || a.startsWith("scyth") || a.startsWith("removebush") || a.equals("removegrass")
                || a.startsWith("destroy") || a.equals("threshing") || a.equals("hammerore")) {
            return SWING;
        }
        if (a.startsWith("build") || a.contains("hammer") || a.equals("removebarricade") || a.startsWith("makewithbrick")) {
            return HAMMER;
        }
        if (a.equals("loot") || a.equals("transferitemonself") || a.equals("forage") || a.startsWith("take_off") || a.equals("detachitem")
                || a.equals("attachitem") || a.startsWith("vehicle") || a.equals("examinevehicle") || a.startsWith("animallure")) {
            return RUMMAGE;
        }
        return WORK;
    }

    /** Whether the animation works at something on the floor. */
    static boolean low(String anim) {
        String a = anim.toLowerCase(Locale.ROOT);
        return a.endsWith("low") || a.contains("_low") || a.contains("floor") || a.equals("forage") || a.contains("tire");
    }

    /** A hand: where the middle of what it holds is, the way its fingers run, the way out of its palm, and its pose. */
    static final class Hand {
        final float[] at = new float[3], fingers = new float[3], out = new float[3];
        int pose;
    }

    /**
     * Where both hands are, a time into an action.
     *
     * @param seconds   how long the action has gone on
     * @param rightItem whether the game has something in the right hand for it
     * @param leftItem  and in the left
     * @param up        0 with the hands below the picture, 1 up at the work: they come up as the action starts and go down as it ends
     */
    static void hands(int kind, boolean low, float seconds, boolean rightItem, boolean leftItem, float up, Hand right, Hand left) {
        float t = seconds;
        away(right, 0);
        away(left, 1);
        switch (kind) {
            case MOUTH -> {
                // A bite every 2.4 s: up to the mouth, a moment there, and down again. With a spoon
                // in the right hand, what is eaten is in the left, held under it in an open palm.
                float p = phase(t, 2.4f), lift = smooth((p - 0.05f) / 0.25f) - smooth((p - 0.6f) / 0.25f);
                float[] rest = leftItem ? new float[] {0.03f, -0.15f, -0.34f} : new float[] {0.11f, -0.16f, -0.37f};
                set(right, lerp(rest, MOUTH_AT, lift), HANDSHAKE_FINGERS, HANDSHAKE_PALM, PINCH);
                tip(right, X, lift * (float) Math.toRadians(35));
                if (leftItem) {
                    set(left, new float[] {-0.06f, -0.17f, -0.34f}, new float[] {0.35f, 0f, -1f}, new float[] {0.85f, 0.45f, 0.2f}, OPEN);
                }
            }
            case DRINK -> {
                // Up to the mouth and tipped, and kept there gulping.
                float lift = smooth(t / 0.6f);
                set(right, lerp(new float[] {0.11f, -0.16f, -0.37f}, new float[] {0.03f, -0.10f, -0.22f}, lift), HANDSHAKE_FINGERS, HANDSHAKE_PALM, GRIP);
                tip(right, X, lift * (float) Math.toRadians(55 + 8 * Math.sin(2 * Math.PI * t / 1.1)));
            }
            case READ -> {
                // The book open in both hands, and a page turned by the right every 6 s.
                float p = t % 6f, turn = smooth((p - 4.6f) / 0.5f) - smooth((p - 5.3f) / 0.5f);
                set(left, new float[] {-0.09f, -0.17f, -0.38f}, new float[] {0.55f, 0.15f, -0.8f}, new float[] {0.8f, 0.45f, 0.4f}, OPEN);
                float[] at = lerp(new float[] {0.10f, -0.17f, -0.38f}, new float[] {-0.02f, -0.17f, -0.38f}, turn);
                at[1] += 0.03f * (float) Math.sin(Math.PI * turn);
                set(right, at, new float[] {-0.55f, 0.15f, -0.8f}, new float[] {-0.7f, 0.6f, 0.4f}, turn > 0.05f ? PINCH : OPEN);
            }
            case CRANK -> {
                // The can held up in the left, and the right working round on top of it.
                set(left, new float[] {-0.04f, -0.18f, -0.36f}, new float[] {0.25f, 0.3f, -0.9f}, new float[] {1f, 0f, 0.1f}, GRIP);
                float turn = (float) (2 * Math.PI * t / 0.7);
                set(right, new float[] {0.02f * (float) Math.cos(turn), -0.12f, -0.36f + 0.02f * (float) Math.sin(turn)},
                        new float[] {-0.2f, 0f, -1f}, new float[] {0f, -1f, 0f}, PINCH);
            }
            case WRAP -> {
                // The left forearm across before the body, and the right taking the bandage over it and round under it.
                set(left, new float[] {0.03f, -0.18f, -0.40f}, new float[] {1f, 0.1f, -0.25f}, new float[] {0f, -1f, 0.1f}, OPEN);
                float round = (float) (2 * Math.PI * t / 1.2);
                float s = (float) Math.sin(round), c = (float) Math.cos(round);
                set(right, new float[] {-0.04f + 0.015f * c, -0.17f + 0.04f * s, -0.38f + 0.03f * c}, new float[] {-1f, 0.2f, -0.3f}, new float[] {-0.5f, -0.75f, -0.3f}, PINCH);
            }
            case HAMMER -> {
                // Drawn back for 0.4 s, struck in 0.1, and on the work until the next.
                float p = phase(t, 0.8f), raised = p < 0.5f ? smooth(p / 0.5f) : 1f - smooth((p - 0.5f) / 0.12f);
                float[] struck = lowered(new float[] {0.09f, -0.19f, -0.50f}, low), drawn = lowered(new float[] {0.15f, -0.06f, -0.36f}, low);
                set(right, lerp(struck, drawn, raised), HANDSHAKE_FINGERS, HANDSHAKE_PALM, HANDLE);
                tip(right, X, (float) Math.toRadians(-70 + 85 * raised));
                // The left holds what is struck where it goes: a nail, a board.
                set(left, new float[] {struck[0] - 0.13f, struck[1] + 0.02f, struck[2] - 0.02f}, new float[] {0f, 1f, 0f}, new float[] {0.2f, 0f, -1f}, PINCH);
            }
            case SAW -> {
                float stroke = (float) Math.sin(2 * Math.PI * t / 0.75);
                set(right, lowered(new float[] {0.10f, -0.17f, -0.44f - 0.07f * stroke}, low), HANDSHAKE_FINGERS, HANDSHAKE_PALM, HANDLE);
                tip(right, X, (float) Math.toRadians(-60));
                set(left, lowered(new float[] {-0.13f, -0.21f, -0.47f}, low), new float[] {0.2f, 0f, -1f}, new float[] {0f, -1f, 0.1f}, OPEN);
            }
            case RUMMAGE -> {
                // Each hand in turn goes out and down into what is searched, takes hold, and comes back.
                rummage(right, 0, phase(t, 1.6f), low);
                rummage(left, 1, phase(t + 0.8f, 1.6f), low);
            }
            case SCRUB -> {
                float turn = (float) (2 * Math.PI * t / 0.9);
                if (low) {
                    set(right, new float[] {0.07f + 0.06f * (float) Math.cos(turn), -0.27f, -0.54f + 0.05f * (float) Math.sin(turn)},
                            new float[] {0f, 0f, -1f}, new float[] {0f, -1f, 0f}, rightItem ? GRIP : OPEN);
                } else {
                    set(right, new float[] {0.07f + 0.06f * (float) Math.cos(turn), -0.17f + 0.06f * (float) Math.sin(turn), -0.48f},
                            new float[] {0f, 1f, 0f}, new float[] {-0.5f, 0.05f, -0.85f}, rightItem ? GRIP : OPEN);
                }
            }
            case WELD -> {
                set(right, lowered(new float[] {0.06f + 0.03f * (float) Math.sin(2 * Math.PI * t / 2.5), -0.18f, -0.46f}, low), HANDSHAKE_FINGERS, HANDSHAKE_PALM, GRIP);
                tip(right, X, (float) Math.toRadians(-40));
            }
            case POUR -> {
                // Tipped over to the left as the pouring starts, and held tipped.
                set(right, new float[] {0.06f, -0.15f, -0.38f}, HANDSHAKE_FINGERS, HANDSHAKE_PALM, GRIP);
                tip(right, Z, smooth(t / 0.8f) * (float) Math.toRadians(75));
                if (leftItem) {
                    set(left, new float[] {-0.08f, -0.18f, -0.38f}, mirror(HANDSHAKE_FINGERS), mirror(HANDSHAKE_PALM), GRIP);
                }
            }
            case FACE -> {
                // Both palms at the face, rubbing up and down against each other.
                float rub = 0.03f * (float) Math.sin(2 * Math.PI * t / 0.8);
                set(right, new float[] {0.05f, -0.06f + rub, -0.20f}, new float[] {0f, 1f, 0f}, new float[] {-0.4f, 0f, 1f}, rightItem ? PINCH : OPEN);
                set(left, new float[] {-0.05f, -0.06f - rub, -0.20f}, new float[] {0f, 1f, 0f}, new float[] {0.4f, 0f, 1f}, OPEN);
            }
            case SWING -> {
                // Both fists on a long handle, the right above the left: drawn up over the right shoulder and swung down ahead.
                float p = phase(t, 1.1f), raised = p < 0.6f ? smooth(p / 0.6f) : 1f - smooth((p - 0.6f) / 0.15f);
                float[] r = lerp(lowered(new float[] {0.05f, -0.17f, -0.52f}, low), new float[] {0.17f, -0.03f, -0.30f}, raised);
                float[] l = lerp(lowered(new float[] {0.00f, -0.22f, -0.42f}, low), new float[] {0.10f, -0.12f, -0.26f}, raised);
                float[] handle = new float[3];
                ArmPose.unit(r[0] - l[0], r[1] - l[1], r[2] - l[2], handle);
                float[] rightPalm = square(new float[] {-0.6f, 0f, -0.8f}, handle), leftPalm = square(new float[] {1f, 0f, 0.3f}, handle);
                float[] rightFingers = new float[3], leftFingers = new float[3];
                // Each thumb runs along the handle towards its head.
                ArmPose.cross(rightPalm, handle, rightFingers);
                ArmPose.cross(handle, leftPalm, leftFingers);
                set(right, r, rightFingers, rightPalm, HANDLE);
                set(left, l, leftFingers, leftPalm, HANDLE);
            }
            default -> {
                // Both hands at the work before the chest: the right working at it, the left steadying it.
                float turn = (float) (2 * Math.PI * t / 0.85);
                set(right, lowered(new float[] {0.08f + 0.02f * (float) Math.cos(turn), -0.16f + 0.02f * (float) Math.sin(turn),
                    -0.36f + 0.015f * (float) Math.sin(2 * Math.PI * t / 1.9)}, low), new float[] {-0.3f, 0.25f, -0.9f}, new float[] {-0.8f, -0.45f, -0.1f},
                        rightItem ? GRIP : PINCH);
                set(left, lowered(new float[] {-0.07f, -0.17f + 0.008f * (float) Math.sin(2 * Math.PI * t / 1.4), -0.35f}, low),
                        new float[] {0.35f, 0.1f, -0.9f}, new float[] {0.85f, 0.4f, 0.1f}, leftItem ? GRIP : OPEN);
            }
        }
        alongTheForearm(right, 0);
        alongTheForearm(left, 1);
        rise(right, 0, up);
        rise(left, 1, up);
    }

    /** How far a hand's fingers run off the line its forearm comes up along, at the most, in radians: a wrist bends about 32 degrees before the forearm has to follow. */
    static final float OFF_THE_FOREARM = (float) Math.toRadians(25);

    /**
     * Brings a hand's fingers to within OFF_THE_FOREARM of the line from where its forearm
     * seems to come from (ArmHolds.FROM) to the hand, the least turn that does it, and turns
     * its palm with them.
     */
    private static void alongTheForearm(Hand hand, int side) {
        float[] line = new float[3], from = ArmHolds.FROM[side];
        ArmPose.unit(hand.at[0] - from[0], hand.at[1] - from[1], hand.at[2] - from[2], line);
        float off = (float) Math.acos(Math.clamp(ArmPose.dot(line, hand.fingers), -1f, 1f));
        if (off <= OFF_THE_FOREARM) {
            return;
        }
        float[] axis = new float[3], turn = new float[9];
        ArmPose.cross(hand.fingers, line, axis);
        if (!ArmPose.unit(axis[0], axis[1], axis[2], axis)) {
            ArmPose.cross(hand.out, line, axis);
            ArmPose.unit(axis[0], axis[1], axis[2], axis);
        }
        ArmPose.about(axis, off - OFF_THE_FOREARM, turn);
        ArmPose.turned(turn, hand.fingers.clone(), hand.fingers);
        ArmPose.turned(turn, hand.out.clone(), hand.out);
    }

    /** The right hand held as for a handshake, thumb up: fingers forward, up and in, palm to the left. */
    private static final float[] HANDSHAKE_FINGERS = {-0.25f, 0.35f, -0.9f}, HANDSHAKE_PALM = {-1f, 0f, -0.1f};
    private static final float[] MOUTH_AT = {0.02f, -0.12f, -0.21f};
    private static final int X = 0, Z = 2;

    /** A hand's place at the far and near end of a reach into a container, the right's; the left's is its mirror. */
    private static final float[] REACHED = {0.08f, -0.30f, -0.60f}, BACK = {0.10f, -0.15f, -0.33f};

    private static void rummage(Hand hand, int side, float p, boolean low) {
        float reach = p < 0.35f ? smooth(p / 0.35f) : p < 0.5f ? 1f : 1f - smooth((p - 0.5f) / 0.35f);
        float[] far = lowered(side == 0 ? REACHED : mirror(REACHED), low), near = side == 0 ? BACK : mirror(BACK);
        // Palm down out there, turned up towards the eye on the way back with what it took.
        float[] palm = lerp(side == 0 ? new float[] {-0.8f, 0.4f, 0.3f} : new float[] {0.8f, 0.4f, 0.3f}, new float[] {0f, -1f, 0f}, reach);
        float[] fingers = lerp(side == 0 ? new float[] {-0.3f, 0.2f, -0.9f} : new float[] {0.3f, 0.2f, -0.9f}, new float[] {0f, -0.2f, -1f}, reach);
        set(hand, lerp(near, far, reach), fingers, palm, reach > 0.8f ? PINCH : OPEN);
    }

    private static void away(Hand hand, int side) {
        set(hand, AWAY[side], new float[] {0f, 1f, -0.3f}, side == 0 ? new float[] {-1f, 0f, 0f} : new float[] {1f, 0f, 0f}, OPEN);
    }

    /** The hand taken from where it waits below the picture to where it works, `up` of the way. */
    private static void rise(Hand hand, int side, float up) {
        for (int axis = 0; axis < 3; axis++) {
            hand.at[axis] = AWAY[side][axis] + (hand.at[axis] - AWAY[side][axis]) * up;
        }
    }

    private static void set(Hand hand, float[] at, float[] fingers, float[] palm, int pose) {
        System.arraycopy(at, 0, hand.at, 0, 3);
        ArmPose.unit(palm[0], palm[1], palm[2], hand.out);
        float along = ArmPose.dot(fingers, hand.out);
        ArmPose.unit(fingers[0] - hand.out[0] * along, fingers[1] - hand.out[1] * along, fingers[2] - hand.out[2] * along, hand.fingers);
        hand.pose = pose;
    }

    /** The hand turned about an axis of the aim's through its middle: about x a positive angle takes its top towards the eye, about z to the left. */
    private static void tip(Hand hand, int axis, float angle) {
        float[] turn = new float[9];
        ArmPose.about(axis == X ? new float[] {1f, 0f, 0f} : new float[] {0f, 0f, 1f}, angle, turn);
        ArmPose.turned(turn, hand.fingers.clone(), hand.fingers);
        ArmPose.turned(turn, hand.out.clone(), hand.out);
    }

    /** A direction made square to a line, of length one. */
    private static float[] square(float[] way, float[] line) {
        float along = ArmPose.dot(way, line);
        float[] out = new float[3];
        ArmPose.unit(way[0] - line[0] * along, way[1] - line[1] * along, way[2] - line[2] * along, out);
        return out;
    }

    private static float[] lowered(float[] at, boolean low) {
        return low ? new float[] {at[0], at[1] - LOW_DOWN, at[2] - LOW_AHEAD} : at;
    }

    private static float[] mirror(float[] at) {
        return new float[] {-at[0], at[1], at[2]};
    }

    private static float[] lerp(float[] from, float[] to, float share) {
        return new float[] {from[0] + (to[0] - from[0]) * share, from[1] + (to[1] - from[1]) * share, from[2] + (to[2] - from[2]) * share};
    }

    /** How far through a cycle of a length a time is, 0 to 1. */
    private static float phase(float seconds, float length) {
        return (seconds % length) / length;
    }

    private static float smooth(float t) {
        return ViewModelMath.smooth(t);
    }
}
