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
    /** Not the game's: the viewer glancing at the watch on a wrist (WatchGlance), and holding the world map up (HeldMap). */
    static final int WATCH = 15, MAP = 16;
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
        if (a.startsWith("scrub") || a.equals("paint") || a.equals("vehiclewash") || a.equals("rake")) {
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

    /**
     * Whether the animation works at something on the floor.
     *
     * @param lootPosition the game's animation variable LootPosition, or null: how high what is searched is, which the
     *                     game sets for its Loot animation (Low, Mid or High)
     */
    static boolean low(String anim, String lootPosition) {
        String a = anim.toLowerCase(Locale.ROOT);
        return a.endsWith("low") || a.contains("_low") || a.contains("floor") || a.equals("forage") || a.contains("tire") || a.equals("rake")
                || a.equals("loot") && "low".equalsIgnoreCase(lootPosition);
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
                // Up to the mouth and tipped, and kept there gulping, in the hand the game puts the drink in: the left whenever
                // it has one there, from a bottle (ISDrinkFluidAction) and from a can, with a spoon or a fork in the right
                // (ISEatFoodAction).
                float lift = smooth(t / 0.6f);
                boolean inLeft = leftItem;
                Hand drinking = inLeft ? left : right;
                float[] at = lerp(new float[] {0.11f, -0.16f, -0.37f}, new float[] {0.03f, -0.10f, -0.22f}, lift);
                set(drinking, inLeft ? mirror(at) : at, inLeft ? mirror(HANDSHAKE_FINGERS) : HANDSHAKE_FINGERS, inLeft ? mirror(HANDSHAKE_PALM) : HANDSHAKE_PALM, GRIP);
                tip(drinking, X, lift * (float) Math.toRadians(55 + 8 * Math.sin(2 * Math.PI * t / 1.1)));
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
                // The fist at the end of the handle with the forearm behind it, the handle up and to the left across the
                // picture and the head at the top of it, its face the way the forearm points. Drawn back on the wrist and
                // up for 0.4 s, struck in 0.1 so the head lands ahead under the reticle, and on the work until the next.
                float p = phase(t, 0.8f), raised = p < 0.5f ? smooth(p / 0.5f) : 1f - smooth((p - 0.5f) / 0.12f);
                float[] fist = lerp(HAMMER_STRUCK, HAMMER_DRAWN, raised);
                float[] face = new float[3], palm = new float[3], cock = new float[9];
                ArmPose.unit(fist[0] - ArmHolds.FROM[0][0], fist[1] - ArmHolds.FROM[0][1], fist[2] - ArmHolds.FROM[0][2], face);
                ArmPose.cross(square(lerp(HAMMER_STRUCK_HANDLE, HAMMER_DRAWN_HANDLE, raised), face), face, palm);
                set(right, fist, face, palm, HANDLE);
                // Turned on the wrist past square to the forearm: the head ahead as it strikes, and further over and down at
                // work on the floor, from the same fist: reached for lower and further, the elbow would rise over the wrist and
                // the fist leave the picture.
                ArmPose.about(right.out, (float) Math.toRadians((low ? 25 : 10) - 15 * raised), cock);
                ArmPose.turned(cock, right.fingers, right.fingers);
                // The left holds what is struck where it goes, a nail or a board, beside where the head lands.
                set(left, lowered(new float[] {-0.18f, -0.07f, -0.50f}, low), new float[] {0f, 1f, 0f}, new float[] {0.2f, 0f, -1f}, PINCH);
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

    /**
     * Where the eye looks at a watch on its left wrist, in the body's axes, the level ones the body
     * stands in facing ahead (x to the right, y up, z towards the viewer): WATCH_FAR off,
     * WATCH_DOWN below the level and WATCH_ASIDE to the side of the wrist. A forearm held so,
     * across the body, has the back of the wrist turned up to the eye.
     */
    static final float WATCH_FAR = 0.32f, WATCH_DOWN = (float) Math.toRadians(40), WATCH_ASIDE = (float) Math.toRadians(12);
    static final float[] WATCH_AT = watchAt();
    /** How far along the fingers from the watch the middle of the hand is, metres. */
    static final float WATCH_TO_HAND = 0.095f;
    /**
     * How far the fingers drop below the level of the glance's picture, and how far the palm turns
     * about them, radians. Held level and square the forearm would have to twist past its limit to
     * turn the face up; dropped and turned so, the wrist stays inside its twist and the face is
     * within 25 degrees of square to the eye.
     */
    static final float WATCH_FINGERS_DROP = (float) Math.toRadians(15), WATCH_PALM_TURN = (float) Math.toRadians(-30);

    private static float[] watchAt() {
        return new float[] {(float) (-WATCH_FAR * Math.sin(WATCH_ASIDE) * Math.cos(WATCH_DOWN)), (float) (-WATCH_FAR * Math.sin(WATCH_DOWN)),
            (float) (-WATCH_FAR * Math.cos(WATCH_ASIDE) * Math.cos(WATCH_DOWN))};
    }

    /**
     * Both hands as the viewer glances at the watch on a wrist: that hand up before the chest with
     * the back of its wrist to the eye, the other below the picture. The place is the body's, so a
     * view pitched down by the mouse looks down at the same wrist as a head turned down does.
     *
     * @param left  whether the watch is on the left wrist
     * @param pitch the clean view's pitch, radians, up positive: the aim's axes are the body's turned by it
     * @param up    0 with the arm down, 1 with the watch before the eye
     */
    static void watch(boolean left, float pitch, float up, Hand right, Hand leftHand) {
        away(right, 0);
        away(leftHand, 1);
        Hand hand = left ? leftHand : right;
        int side = left ? 1 : 0;
        float sign = left ? 1f : -1f;
        float[] watch = {WATCH_AT[0] * sign, WATCH_AT[1], WATCH_AT[2]};
        // The palm faces away from the eye, so the back of the wrist faces it, and the forearm lies across the picture of the
        // glance, the hand a little lower than the wrist, on the other side of the watch from the wrist's own.
        float[] palm = new float[3], upSeen = new float[3], across = new float[3];
        ArmPose.unit(watch[0], watch[1], watch[2], palm);
        ArmPose.unit(-palm[0] * palm[1], 1f - palm[1] * palm[1], -palm[2] * palm[1], upSeen);
        ArmPose.cross(palm, upSeen, across);
        float c = (float) Math.cos(WATCH_FINGERS_DROP), s = (float) Math.sin(WATCH_FINGERS_DROP);
        float[] fingers = {across[0] * sign * c - upSeen[0] * s, across[1] * sign * c - upSeen[1] * s, across[2] * sign * c - upSeen[2] * s};
        float[] at = {watch[0] + fingers[0] * WATCH_TO_HAND, watch[1] + fingers[1] * WATCH_TO_HAND, watch[2] + fingers[2] * WATCH_TO_HAND};
        float[] turnedIn = new float[3];
        ArmPose.cross(fingers, palm, turnedIn);
        float tc = (float) Math.cos(WATCH_PALM_TURN * sign), ts = (float) Math.sin(WATCH_PALM_TURN * sign);
        float[] held = {palm[0] * tc + turnedIn[0] * ts, palm[1] * tc + turnedIn[1] * ts, palm[2] * tc + turnedIn[2] * ts};
        set(hand, fromBody(at, pitch), fromBody(fingers, pitch), fromBody(held, pitch), OPEN);
        rise(hand, side, up);
    }

    /** A place or a way in the body's axes in the aim's, the aim pitched up by pitch radians. */
    static float[] fromBody(float[] v, float pitch) {
        float c = (float) Math.cos(pitch), s = (float) Math.sin(pitch);
        return new float[] {v[0], v[1] * c + v[2] * s, -v[1] * s + v[2] * c};
    }

    /** The world map held up in both hands: its middle, how wide and tall it is, metres, and how far its top leans away from the eye, radians. */
    static final float[] SHEET_AT = {0f, -0.07f, -0.42f};
    static final float SHEET_WIDE = 0.40f, SHEET_TALL = 0.29f, SHEET_LEANS = (float) Math.toRadians(25);
    /** Where a hand holds the sheet's side, from its middle down, as a share of its height. */
    private static final float HELD_LOW = 0.18f;

    /**
     * The sheet of the held map before the eye, in the aim's axes: a matrix from a square of side
     * one about its middle, x across it, y up it and z out of its face to the eye, to metres.
     *
     * @param up 0 below the picture, 1 held up
     */
    static void sheet(float up, float[] out) {
        float c = (float) Math.cos(SHEET_LEANS), s = (float) Math.sin(SHEET_LEANS);
        float[] across = {1f, 0f, 0f}, upIt = {0f, c, -s}, face = {0f, s, c};
        float[] middle = {SHEET_AT[0], SHEET_AT[1] + (AWAY[0][1] - SHEET_AT[1]) * (1f - up), SHEET_AT[2] + (AWAY[0][2] - SHEET_AT[2]) * (1f - up)};
        for (int axis = 0; axis < 3; axis++) {
            out[axis] = across[axis] * SHEET_WIDE;
            out[4 + axis] = upIt[axis] * SHEET_TALL;
            out[8 + axis] = face[axis];
            out[12 + axis] = middle[axis];
        }
        out[3] = 0f;
        out[7] = 0f;
        out[11] = 0f;
        out[15] = 1f;
    }

    /** Both hands holding the map up by its sides, the thumbs on its face and the fingers behind. */
    static void map(float up, Hand right, Hand left) {
        float[] sheet = new float[16];
        sheet(up, sheet);
        for (int side = 0; side < 2; side++) {
            Hand hand = side == 0 ? right : left;
            float x = side == 0 ? 0.5f : -0.5f, y = -HELD_LOW;
            float[] at = {sheet[0] * x + sheet[4] * y + sheet[12], sheet[1] * x + sheet[5] * y + sheet[13], sheet[2] * x + sheet[6] * y + sheet[14]};
            // The fingers run up the sheet's side and in over it a little; the palm faces in across it, towards the eye a little.
            float[] fingers = {-x * 0.5f, sheet[5] / SHEET_TALL, sheet[6] / SHEET_TALL};
            float[] palm = {-x * 2f, sheet[9] * 0.35f, sheet[10] * 0.35f};
            set(hand, at, fingers, palm, PINCH);
        }
        alongTheForearm(right, 0);
        alongTheForearm(left, 1);
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
        ArmPose.turned(turn, hand.fingers, hand.fingers);
        ArmPose.turned(turn, hand.out, hand.out);
    }

    /** The right hand held as for a handshake, thumb up: fingers forward, up and in, palm to the left. */
    private static final float[] HANDSHAKE_FINGERS = {-0.25f, 0.35f, -0.9f}, HANDSHAKE_PALM = {-1f, 0f, -0.1f};
    private static final float[] MOUTH_AT = {0.02f, -0.12f, -0.21f};
    /**
     * Where the fist holding a hammer is as the head strikes and drawn back, and the way the
     * handle runs from it as near as square to the forearm lets: the head lands just left of the
     * reticle and about half a metre ahead (Characters lays the game's hammer in the fist by its
     * mesh, the end of its handle in the fist).
     */
    private static final float[] HAMMER_STRUCK = {0.20f, -0.30f, -0.56f}, HAMMER_DRAWN = {0.21f, -0.22f, -0.48f},
            HAMMER_STRUCK_HANDLE = {-0.65f, 0.76f, 0f}, HAMMER_DRAWN_HANDLE = {-0.42f, 0.88f, 0.15f};
    private static final int X = 0, Z = 2;
    private static final float[] X_AXIS = {1f, 0f, 0f}, Z_AXIS = {0f, 0f, 1f};

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

    private static final float[] AWAY_FINGERS = {0f, 1f, -0.3f};
    private static final float[][] AWAY_PALM = {{-1f, 0f, 0f}, {1f, 0f, 0f}};

    private static void away(Hand hand, int side) {
        set(hand, AWAY[side], AWAY_FINGERS, AWAY_PALM[side], OPEN);
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
        ArmPose.about(axis == X ? X_AXIS : Z_AXIS, angle, turn);
        ArmPose.turned(turn, hand.fingers, hand.fingers);
        ArmPose.turned(turn, hand.out, hand.out);
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
