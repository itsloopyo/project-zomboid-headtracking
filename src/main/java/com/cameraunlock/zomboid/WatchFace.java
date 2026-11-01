package com.cameraunlock.zomboid;

import java.util.Locale;

/**
 * The watch the viewer wears, as the mod draws it on the wrist of its own arm: a strap round the
 * forearm, a case on the back of the wrist, and on it the game's time of day, as hands at their
 * angles on a dial or as four digits of seven segments on a grey display. Every piece is a box
 * or a disc, laid out here in metres in the axes of the wrist: x along the forearm towards the
 * hand, y out of the back of the wrist and z across it, the origin on the forearm's line under
 * the middle of the face.
 */
final class WatchFace {
    private WatchFace() {}

    /** The pieces a watch is made of: a box of side one, and a disc of radius one, one thick along its own y. */
    static final int BOX = 0, DISC = 1;

    /** How thick the strap is and how wide along the forearm, metres. */
    static final float STRAP_THICK = 0.0024f, STRAP_WIDE = 0.019f;
    /** How many pieces the strap is laid round the wrist in. */
    static final int STRAP_PIECES = 16;
    /** An analogue case's radius and height; a digital case's sides from three to nine and from twelve to six, and its height. */
    static final float ROUND_RADIUS = 0.0185f, ROUND_HIGH = 0.008f, SQUARE_ACROSS = 0.034f, SQUARE_TALL = 0.036f, SQUARE_HIGH = 0.009f;
    static final float DIAL_RADIUS = 0.0158f, MARK_RADIUS = 0.0132f;
    static final float HOUR_LONG = 0.0088f, MINUTE_LONG = 0.0132f;
    /** A digit's width and height, a segment's thickness, and where the four digits stand from three to nine. */
    static final float DIGIT_WIDE = 0.0044f, DIGIT_TALL = 0.0082f, SEGMENT_THICK = 0.0009f;
    static final float[] DIGIT_AT = {-0.0084f, -0.0034f, 0.0034f, 0.0084f};
    static final float DISPLAY_ACROSS = 0.0245f, DISPLAY_TALL = 0.0125f;

    /** Seven segments, a the top then clockwise round to f, and g the bar across the middle: which are lit for each figure. */
    static final int[] FIGURES = {0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110, 0b1101101, 0b1111101, 0b0000111, 0b1111111, 0b1101111};

    /**
     * The time of day a watch shows at a time of day, hours: as the game's clock shows it, to the
     * ten minutes before.
     */
    static float shown(float timeOfDay) {
        float hours = (float) Math.floor(timeOfDay);
        return hours + (float) Math.floor((timeOfDay - hours) * 6f) / 6f;
    }

    /** The hour hand's angle, clockwise from twelve, radians, at a time of day in hours. */
    static float hourAngle(float timeOfDay) {
        float hours = floorMod(timeOfDay, 12f);
        return (float) (2 * Math.PI * hours / 12.0);
    }

    /** The minute hand's angle, clockwise from twelve, radians. */
    static float minuteAngle(float timeOfDay) {
        float minutes = floorMod(timeOfDay, 1f) * 60f;
        return (float) (2 * Math.PI * minutes / 60.0);
    }

    /**
     * The four figures a digital watch shows at a time of day, hours then minutes, as the game's
     * clock shows them: on a twelve hour watch midnight and noon are 12, and an hour under ten has
     * no figure before it, which is -1 here.
     */
    static int[] figures(float timeOfDay, boolean twentyFour) {
        int minutesOfDay = (int) Math.floor(floorMod(timeOfDay, 24f) * 60f) % (24 * 60);
        int hour = minutesOfDay / 60, minute = minutesOfDay % 60;
        if (!twentyFour) {
            hour = hour % 12 == 0 ? 12 : hour % 12;
        }
        int tens = hour / 10;
        return new int[] {tens == 0 && !twentyFour ? -1 : tens, hour % 10, minute / 10, minute % 10};
    }

    /**
     * Where a segment of a figure lies in it, from the figure's middle: across (towards three), up
     * (towards twelve), its length, and whether it runs across.
     */
    static float[] segment(int which) {
        float halfWide = (DIGIT_WIDE - SEGMENT_THICK) / 2f, halfTall = (DIGIT_TALL - SEGMENT_THICK) / 2f;
        float across = DIGIT_WIDE - 2f * SEGMENT_THICK, down = halfTall - SEGMENT_THICK;
        return switch (which) {
            case 0 -> new float[] {0f, halfTall, across, 1f};
            case 1 -> new float[] {halfWide, halfTall / 2f, down, 0f};
            case 2 -> new float[] {halfWide, -halfTall / 2f, down, 0f};
            case 3 -> new float[] {0f, -halfTall, across, 1f};
            case 4 -> new float[] {-halfWide, -halfTall / 2f, down, 0f};
            case 5 -> new float[] {-halfWide, halfTall / 2f, down, 0f};
            case 6 -> new float[] {0f, 0f, across, 1f};
            default -> throw new IllegalArgumentException("a figure has seven segments, not " + (which + 1));
        };
    }

    /**
     * The colours a watch is made in, and what each part is as CharacterDraw names what is worn:
     * the strap, the case, the face and what is drawn on the face.
     */
    record Look(float[] strap, int strapWear, float[] metal, float[] face, float[] marks) {}

    private static final float[] STEEL = {0.62f, 0.63f, 0.64f}, GOLD = {0.78f, 0.6f, 0.27f}, BLACK = {0.05f, 0.05f, 0.055f}, BROWN = {0.33f, 0.18f, 0.08f},
            OLIVE = {0.27f, 0.29f, 0.16f}, CREAM = {0.92f, 0.9f, 0.83f}, INK = {0.04f, 0.04f, 0.045f}, WHITE = {0.93f, 0.93f, 0.9f},
            DISPLAY = {0.6f, 0.64f, 0.55f}, RED = {0.55f, 0.07f, 0.06f}, NAVY = {0.07f, 0.11f, 0.24f};

    /** The look of one of the game's watches, by its name: its classic black, brown, military and gold watches, its digital black, red and dress ones, and its expensive one. */
    static Look look(String fullType, boolean digital) {
        String name = fullType.toLowerCase(Locale.ROOT);
        if (digital) {
            if (name.contains("red")) {
                return new Look(RED, CharacterDraw.SLEEK, RED, DISPLAY, INK);
            }
            if (name.contains("dress")) {
                return new Look(STEEL, CharacterDraw.SLEEK, STEEL, DISPLAY, INK);
            }
            return new Look(BLACK, CharacterDraw.SLEEK, BLACK, DISPLAY, INK);
        }
        if (name.contains("military")) {
            return new Look(OLIVE, CharacterDraw.WEAVE, BLACK, BLACK, WHITE);
        }
        if (name.contains("gold")) {
            return new Look(BROWN, CharacterDraw.LEATHER, GOLD, CREAM, INK);
        }
        if (name.contains("expensive")) {
            return new Look(STEEL, CharacterDraw.SLEEK, STEEL, NAVY, WHITE);
        }
        if (name.contains("brown")) {
            return new Look(BROWN, CharacterDraw.LEATHER, STEEL, CREAM, INK);
        }
        return new Look(BLACK, CharacterDraw.LEATHER, STEEL, CREAM, INK);
    }

    /** The pieces of a watch: for each, what it is, the matrix from its own measure to the wrist's axes, its colour and what it is made of. */
    static final class Pieces {
        static final int MOST = STRAP_PIECES + 40;
        final int[] kind = new int[MOST], wear = new int[MOST];
        final float[] matrices = new float[MOST * 16];
        final float[][] colour = new float[MOST][];
        int count;

        private void add(int what, float[] paint, int madeOf, float[] u, float su, float[] v, float sv, float[] w, float sw, float x, float y, float z) {
            if (count == MOST) {
                throw new IllegalStateException("a watch has room for " + MOST + " pieces");
            }
            int at = count * 16;
            float[][] columns = {{u[0] * su, u[1] * su, u[2] * su}, {v[0] * sv, v[1] * sv, v[2] * sv}, {w[0] * sw, w[1] * sw, w[2] * sw}};
            for (int c = 0; c < 3; c++) {
                matrices[at + c * 4] = columns[c][0];
                matrices[at + c * 4 + 1] = columns[c][1];
                matrices[at + c * 4 + 2] = columns[c][2];
                matrices[at + c * 4 + 3] = 0f;
            }
            matrices[at + 12] = x;
            matrices[at + 13] = y;
            matrices[at + 14] = z;
            matrices[at + 15] = 1f;
            kind[count] = what;
            colour[count] = paint;
            wear[count] = madeOf;
            count++;
        }
    }

    private static final float[] X = {1f, 0f, 0f}, Y = {0f, 1f, 0f}, Z = {0f, 0f, 1f};

    /**
     * Lays a watch out on a wrist.
     *
     * @param left        whether it is on the left wrist: three o'clock is towards the hand there, and towards the elbow on the right
     * @param upRadius    how far the back of the forearm is from its line where the watch sits, metres
     * @param aroundRadius how far its side is
     */
    static void lay(Look look, boolean digital, boolean left, float timeOfDay, boolean twentyFour, float upRadius, float aroundRadius, Pieces out) {
        out.count = 0;
        float ry = upRadius + STRAP_THICK / 2f, rz = aroundRadius + STRAP_THICK / 2f;
        for (int piece = 0; piece < STRAP_PIECES; piece++) {
            double from = 2 * Math.PI * piece / STRAP_PIECES, to = 2 * Math.PI * (piece + 1) / STRAP_PIECES, mid = (from + to) / 2;
            float y0 = (float) (ry * Math.cos(from)), z0 = (float) (rz * Math.sin(from)), y1 = (float) (ry * Math.cos(to)), z1 = (float) (rz * Math.sin(to));
            float[] along = new float[3], outward = new float[3];
            ArmPose.unit(0f, y1 - y0, z1 - z0, along);
            ArmPose.cross(X, along, outward);
            float length = (float) Math.hypot(y1 - y0, z1 - z0) * 1.08f;
            out.add(BOX, look.strap(), look.strapWear(), X, STRAP_WIDE, along, length, outward, STRAP_THICK,
                    0f, (float) (ry * Math.cos(mid)), (float) (rz * Math.sin(mid)));
        }
        // The face's axes: three o'clock, twelve, and out of the face. The left arm is the right one in a mirror, so twelve is
        // the same way of the model on either wrist (OwnArms.watch).
        float[] three = left ? X : new float[] {-1f, 0f, 0f}, twelve = Z, normal = Y;
        float base = upRadius + STRAP_THICK;
        float top;
        if (digital) {
            out.add(BOX, look.metal(), CharacterDraw.SLEEK, three, SQUARE_ACROSS, normal, SQUARE_HIGH, twelve, SQUARE_TALL, 0f, base + SQUARE_HIGH / 2f, 0f);
            top = base + SQUARE_HIGH;
            out.add(BOX, look.face(), CharacterDraw.WEAVE, three, DISPLAY_ACROSS, normal, 0.0004f, twelve, DISPLAY_TALL, 0f, top + 0.0002f, 0f);
            float drawn = top + 0.0006f;
            int[] figures = figures(timeOfDay, twentyFour);
            for (int digit = 0; digit < 4; digit++) {
                if (figures[digit] < 0) {
                    continue;
                }
                for (int segment = 0; segment < 7; segment++) {
                    if ((FIGURES[figures[digit]] & 1 << segment) == 0) {
                        continue;
                    }
                    float[] lies = segment(segment);
                    boolean acrossBar = lies[3] > 0.5f;
                    float a = DIGIT_AT[digit] + lies[0], u = lies[1];
                    out.add(BOX, look.marks(), CharacterDraw.WEAVE, three, acrossBar ? lies[2] : SEGMENT_THICK, normal, 0.0002f, twelve,
                            acrossBar ? SEGMENT_THICK : lies[2], three[0] * a, drawn, u);
                }
            }
            for (float dot : new float[] {-0.0018f, 0.0018f}) {
                out.add(BOX, look.marks(), CharacterDraw.WEAVE, three, SEGMENT_THICK, normal, 0.0002f, twelve, SEGMENT_THICK, 0f, drawn, dot);
            }
            return;
        }
        out.add(DISC, look.metal(), CharacterDraw.SLEEK, three, ROUND_RADIUS, normal, ROUND_HIGH, twelve, ROUND_RADIUS, 0f, base + ROUND_HIGH / 2f, 0f);
        top = base + ROUND_HIGH;
        out.add(DISC, look.face(), CharacterDraw.WEAVE, three, DIAL_RADIUS, normal, 0.0004f, twelve, DIAL_RADIUS, 0f, top + 0.0002f, 0f);
        float drawn = top + 0.0005f;
        for (int mark = 0; mark < 12; mark++) {
            float angle = (float) (2 * Math.PI * mark / 12.0), markLength = mark % 3 == 0 ? 0.0042f : 0.0024f;
            float[] radial = clock(three, twelve, angle), side = clock(three, twelve, angle + (float) (Math.PI / 2));
            float r = MARK_RADIUS - markLength / 2f + 0.0012f;
            out.add(BOX, look.marks(), CharacterDraw.WEAVE, radial, markLength, normal, 0.0002f, side, mark % 3 == 0 ? 0.0011f : 0.0007f,
                    radial[0] * r, drawn, radial[2] * r);
        }
        hand(out, look, three, twelve, normal, hourAngle(timeOfDay), HOUR_LONG, 0.0017f, drawn + 0.0003f);
        hand(out, look, three, twelve, normal, minuteAngle(timeOfDay), MINUTE_LONG, 0.0012f, drawn + 0.0007f);
        out.add(DISC, look.marks(), CharacterDraw.SLEEK, three, 0.0013f, normal, 0.0012f, twelve, 0.0013f, 0f, drawn + 0.0006f, 0f);
    }

    private static void hand(Pieces out, Look look, float[] three, float[] twelve, float[] normal, float angle, float length, float width, float height) {
        float[] radial = clock(three, twelve, angle), side = clock(three, twelve, angle + (float) (Math.PI / 2));
        // A hand reaches a little back past the middle, as a real one does.
        float back = 0.0018f, middle = (length - back) / 2f;
        out.add(BOX, look.marks(), CharacterDraw.SLEEK, radial, length + back, normal, 0.0003f, side, width, radial[0] * middle, height, radial[2] * middle);
    }

    /** The way from the middle of the face to a place on its rim at an angle clockwise from twelve. */
    static float[] clock(float[] three, float[] twelve, float angle) {
        float s = (float) Math.sin(angle), c = (float) Math.cos(angle);
        return new float[] {three[0] * s + twelve[0] * c, three[1] * s + twelve[1] * c, three[2] * s + twelve[2] * c};
    }

    private static float floorMod(float value, float by) {
        float r = value % by;
        return r < 0f ? r + by : r;
    }
}
