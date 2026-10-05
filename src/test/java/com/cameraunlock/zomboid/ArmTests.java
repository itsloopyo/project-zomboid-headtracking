package com.cameraunlock.zomboid;

final class ArmTests {
    private ArmTests() {}

    static void run() {
        wristIsMeasuredAsTheOvalRoundItsCorners();
        handWithinAnEasyBendIsLeftAsItWasPosed();
        handIsTurnedAboutItsPalmUntilTheWristBendsNoFurtherThanAWristDoes();
        handPosedDeadAgainstItsArmIsTurnedRoundToIt();
        forearmComesToTheGripFromBehindAndBelow();
        handTurnsWithoutAJumpAsItsPoseDoes();
        cutEndOfTheHandIsInsideItsCuff();
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    /** A forearm and the hand's own length from palm to wrist, in tiles. */
    private static final float FORE = 0.27f, UPPER = 1.15f, PALM = 0.09f;
    /** Half a wrist's width across and through, in tiles. */
    private static final float ACROSS = 0.036f, THROUGH = 0.022f;
    private static final float ELBOW = 0.027f;
    private static final int WAYS = 600;

    /**
     * Where each hand's palm is held, in the view: a firearm's grip at the hip, half way up
     * and with the sights up, something swung as it is held, drawn back and swept across, and
     * the fore-end of a firearm at the hip and with the sights up.
     */
    private static final float[][] RIGHT_PALMS = {{0.13f, -0.14f, -0.27f}, {0.065f, -0.125f, -0.345f}, {0f, -0.06f, -0.36f},
        {0.2f, -0.22f, -0.38f}, {0.25f, -0.17f, -0.33f}, {-0.1f, -0.29f, -0.48f}};
    private static final float[][] LEFT_PALMS = {{0.1f, -0.17f, -0.5f}, {0f, -0.1f, -0.6f}};
    private static final int HIP = 0, AIMED = 2;

    /** One arm fitted to one hand. */
    private static final class Fit {
        final float[] shoulder, pole, wrist = new float[3], rigid = new float[3], palm, turn = new float[16], elbow = new float[3], said = new float[2];
        final float[] posedWrist = new float[3], posedRigid = new float[3], across = new float[3];
        final boolean fits;

        /**
         * @param way  the way the forearm the hand was posed for leaves its wrist, of length one
         * @param roll how far round that the line across the wrist is, in radians
         */
        Fit(boolean left, float[] palm, float[] way, float roll) {
            this.palm = palm.clone();
            shoulder = (left ? ViewModel.LEFT_SHOULDER : ViewModel.RIGHT_SHOULDER).clone();
            pole = (left ? ViewModel.LEFT_ELBOW : ViewModel.RIGHT_ELBOW).clone();
            float[] side = square(way);
            float[] other = cross(way, side);
            for (int axis = 0; axis < 3; axis++) {
                // The wrist is not dead in line with the palm: a hand is thicker on one side.
                posedWrist[axis] = palm[axis] + way[axis] * PALM + side[axis] * 0.012f;
                posedRigid[axis] = posedWrist[axis] + way[axis] * FORE;
                across[axis] = side[axis] * (float) Math.cos(roll) + other[axis] * (float) Math.sin(roll);
            }
            System.arraycopy(posedWrist, 0, wrist, 0, 3);
            System.arraycopy(posedRigid, 0, rigid, 0, 3);
            fits = ViewModelMath.arm(shoulder, pole, UPPER, wrist, rigid, this.palm, turn, elbow, said);
        }

        float bent() {
            return between(minus(elbow, wrist), minus(rigid, wrist));
        }

        /** How far the hand was turned, in radians. */
        float turned() {
            return (float) Math.acos(Math.clamp((turn[0] + turn[5] + turn[10] - 1f) / 2f, -1f, 1f));
        }
    }

    /** Ways spread evenly over every way there is. */
    private static float[] way(int index) {
        double up = 1 - 2 * (index + 0.5) / WAYS, round = index * Math.PI * (3 - Math.sqrt(5));
        double flat = Math.sqrt(1 - up * up);
        return new float[] {(float) (flat * Math.cos(round)), (float) up, (float) (flat * Math.sin(round))};
    }

    private static float[] minus(float[] a, float[] b) {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static float length(float[] a) {
        return (float) Math.sqrt(dot(a, a));
    }

    private static float[] unit(float[] a) {
        float length = length(a);
        return new float[] {a[0] / length, a[1] / length, a[2] / length};
    }

    private static float between(float[] a, float[] b) {
        return (float) Math.acos(Math.clamp(dot(a, b) / (length(a) * length(b)), -1f, 1f));
    }

    /** A line of length one square to another. */
    private static float[] square(float[] to) {
        float[] from = Math.abs(to[1]) < 0.9f ? new float[] {0f, 1f, 0f} : new float[] {1f, 0f, 0f};
        return unit(cross(to, from));
    }

    private static float[] placed(float[] matrix, float[] point) {
        float[] out = new float[3];
        ViewModelMath.place(matrix, point[0], point[1], point[2], out);
        return out;
    }

    private static float degrees(float radians) {
        return (float) Math.toDegrees(radians);
    }

    /** A right arm of four rings along x, as wide in y as given and 0.1 either way in z: upper arm, two of forearm, hand. */
    private static Limb arm(float wide, float turnedBy) {
        int rings = 4;
        float[] places = new float[rings * 4 * 3], uvs = new float[rings * 4 * 2];
        int[] kinds = new int[rings * 4];
        int[] ringKind = {CharacterDraw.UPPER, CharacterDraw.FORE, CharacterDraw.FORE, CharacterDraw.HAND};
        float cos = (float) Math.cos(turnedBy), sin = (float) Math.sin(turnedBy);
        for (int ring = 0; ring < rings; ring++) {
            for (int corner = 0; corner < 4; corner++) {
                int vertex = ring * 4 + corner;
                float y = corner == 0 || corner == 3 ? -wide : wide, z = corner < 2 ? -0.1f : 0.1f;
                places[vertex * 3] = ring;
                places[vertex * 3 + 1] = y * cos - z * sin;
                places[vertex * 3 + 2] = y * sin + z * cos;
                kinds[vertex] = ringKind[ring];
            }
        }
        int[] elements = new int[(rings - 1) * 4 * 6];
        int at = 0;
        for (int ring = 0; ring + 1 < rings; ring++) {
            for (int corner = 0; corner < 4; corner++) {
                int here = ring * 4 + corner, next = ring * 4 + (corner + 1) % 4;
                elements[at++] = here;
                elements[at++] = next;
                elements[at++] = here + 4;
                elements[at++] = next;
                elements[at++] = next + 4;
                elements[at++] = here + 4;
            }
        }
        return Limb.read(places, uvs, kinds, elements, elements.length, 0);
    }

    private static void wristIsMeasuredAsTheOvalRoundItsCorners() {
        for (float turnedBy : new float[] {0f, 0.4f, 1.3f, 2.9f}) {
            Limb arm = arm(0.25f, turnedBy);
            float[] wide = {0f, (float) Math.cos(turnedBy), (float) Math.sin(turnedBy)};
            check(Math.abs(arm.across[0]) < 1e-4f && Math.abs(Math.abs(dot(arm.across, wide)) - 1f) < 1e-4f, "the line across a wrist is the way it is widest, square to the forearm");
            check(Math.abs(arm.wristAcross - 0.25f * (float) Math.sqrt(2)) < 1e-4f && Math.abs(arm.wristThrough - 0.1f * (float) Math.sqrt(2)) < 1e-4f,
                    "the oval keeps the wrist's proportions and passes through its four corners");
        }
        Limb round = arm(0.1f, 0f);
        check(Math.abs(round.wristAcross - round.wristThrough) < 1e-4f && Math.abs(length(round.across) - 1f) < 1e-4f, "a wrist as wide as it is deep is a circle, with a line across it all the same");
        check(arm(0f, 0f).across == null, "a wrist that is a flat card has no shape");
    }

    private static void handWithinAnEasyBendIsLeftAsItWasPosed() {
        // The arm an untouched hand gets, and then a hand posed for a forearm 20 degrees off that arm's.
        Fit straight = new Fit(false, RIGHT_PALMS[HIP], unit(new float[] {0.45f, -0.5f, 0.73f}), 0f);
        check(straight.fits, "a hand has an arm");
        float[] arms = unit(minus(straight.elbow, straight.wrist)), off = square(arms);
        float cos = (float) Math.cos(Math.toRadians(20)), sin = (float) Math.sin(Math.toRadians(20));
        Fit easy = new Fit(false, RIGHT_PALMS[HIP], new float[] {arms[0] * cos + off[0] * sin, arms[1] * cos + off[1] * sin, arms[2] * cos + off[2] * sin}, 0f);
        check(easy.fits && easy.said[0] < ViewModelMath.WRIST_EASY, "a hand posed for nearly the arm it gets bends its wrist with ease");
        for (int i = 0; i < 16; i++) {
            check(easy.turn[i] == (i % 5 == 0 ? 1f : 0f), "and is not turned at all");
        }
        check(length(minus(easy.wrist, easy.posedWrist)) == 0f && easy.said[1] == easy.said[0], "its wrist is where the game put it");
    }

    private static void handIsTurnedAboutItsPalmUntilTheWristBendsNoFurtherThanAWristDoes() {
        float worst = 0f, furthest = 0f;
        int turned = 0, fitted = 0;
        for (int side = 0; side < 2; side++) {
            for (float[] palm : side == 0 ? RIGHT_PALMS : LEFT_PALMS) {
                for (int index = 0; index < WAYS; index++) {
                    Fit fit = new Fit(side == 1, palm, way(index), 0f);
                    check(fit.fits, "every hand has an arm");
                    fitted++;
                    float bent = fit.bent();
                    worst = Math.max(worst, bent);
                    check(bent <= ViewModelMath.WRIST_MOST + 1e-3f, "no wrist is bent further than a wrist goes: " + degrees(bent));
                    check(Math.abs(bent - fit.said[1]) < 1e-3f, "and the trace says how far it is");
                    check(Math.abs(length(minus(fit.elbow, fit.wrist)) - FORE) < 1e-4f && Math.abs(length(minus(fit.shoulder, fit.elbow)) - FORE * UPPER) < 1e-4f,
                            "both bones are their own lengths");
                    check(length(minus(placed(fit.turn, fit.palm), fit.palm)) < 1e-5f, "the palm stays where it holds");
                    check(length(minus(placed(fit.turn, fit.posedWrist), fit.wrist)) < 1e-4f && length(minus(placed(fit.turn, fit.posedRigid), fit.rigid)) < 1e-4f,
                            "the wrist and the hand's own forearm are where the turn puts them");
                    float[] x = {fit.turn[0], fit.turn[1], fit.turn[2]}, y = {fit.turn[4], fit.turn[5], fit.turn[6]}, z = {fit.turn[8], fit.turn[9], fit.turn[10]};
                    check(Math.abs(length(x) - 1f) < 1e-4f && Math.abs(length(y) - 1f) < 1e-4f && Math.abs(dot(x, y)) < 1e-4f
                            && length(minus(cross(x, y), z)) < 1e-4f, "the turn is a turn: the hand keeps its size and shape and is not mirrored");
                    if (fit.said[0] > ViewModelMath.WRIST_EASY) {
                        turned++;
                        furthest = Math.max(furthest, fit.turned() - (fit.said[0] - ViewModelMath.WRIST_EASY));
                    } else {
                        check(fit.turned() == 0f, "a wrist within an easy bend is not turned");
                    }
                }
            }
        }
        check(turned > fitted / 2, "most ways a hand may be posed need a turn: " + turned + " of " + fitted);
        check(worst <= ViewModelMath.WRIST_EASY + (float) Math.toRadians(2), "turning the hand alone leaves the wrist all but at an easy bend: " + degrees(worst));
        check(furthest < (float) Math.toRadians(40), "and no hand is turned far past what its wrist was over, for all that turning it moves the arm: " + degrees(furthest));
    }

    private static void handPosedDeadAgainstItsArmIsTurnedRoundToIt() {
        Fit straight = new Fit(false, RIGHT_PALMS[AIMED], unit(new float[] {0.5f, -0.5f, 0.7f}), 0f);
        float[] against = unit(minus(straight.wrist, straight.elbow));
        Fit fit = new Fit(false, RIGHT_PALMS[AIMED], against, 0f);
        check(fit.fits && fit.said[0] > (float) Math.toRadians(120), "a hand posed for a forearm the other way from its arm has one");
        check(fit.bent() <= ViewModelMath.WRIST_MOST + 1e-3f, "and its wrist is bent no further than any other: " + degrees(fit.bent()));
    }

    private static void forearmComesToTheGripFromBehindAndBelow() {
        for (int at : new int[] {HIP, 1, AIMED}) {
            for (int index = 0; index < WAYS; index++) {
                Fit fit = new Fit(false, RIGHT_PALMS[at], way(index), 0f);
                float[] forearm = unit(minus(fit.elbow, fit.wrist));
                check(forearm[2] > 0f && forearm[1] < -0.5f && forearm[0] > 0f && forearm[0] < -forearm[1],
                        "however a firearm's hand was posed, its forearm leaves it towards the viewer and downwards, and less to the right than down: way " + index + " at " + at);
                check(fit.elbow[1] < RIGHT_PALMS[at][1] - 0.15f, "and the elbow is well below the grip");
            }
        }
    }

    /** The hand's pose is taken round a ring of ways half a degree at a time, clear of the one way that is dead against the arm. */
    private static void handTurnsWithoutAJumpAsItsPoseDoes() {
        float[] about = unit(new float[] {0.3f, 1f, 0.2f}), from = square(about), to = cross(about, from);
        float[] last = null;
        for (int step = 0; step <= 720; step++) {
            double angle = Math.toRadians(step * 0.5);
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            float[] way = {from[0] * cos + to[0] * sin, from[1] * cos + to[1] * sin, from[2] * cos + to[2] * sin};
            Fit fit = new Fit(false, RIGHT_PALMS[HIP], way, 0f);
            check(fit.fits && fit.said[0] < (float) Math.toRadians(170), "the ring keeps clear of the way dead against the arm");
            if (last != null) {
                check(length(minus(fit.wrist, last)) < 0.004f, "half a degree of the hand's pose moves its wrist no more than a few millimetres: step " + step);
            }
            last = fit.wrist;
        }
    }

    /**
     * How much of its oval a cuff's flat faces reach: twelve sides round it, and the ball that
     * closes it is four bands deep.
     */
    private static final float FACES = (float) (Math.cos(Math.PI / 12) * Math.cos(Math.PI / 16));

    /** Whether a point is inside the arm as it is drawn from the wrist to the elbow, closed over the wrist. */
    private static boolean inCuff(float[] point, Fit fit, float[] cuff, float wide, float deep, float atElbow) {
        float[] way = unit(minus(fit.elbow, fit.wrist)), through = cross(way, cuff), from = minus(point, fit.wrist);
        float along = dot(from, way) / FORE, a = dot(from, cuff), b = dot(from, through);
        if (along >= 0f) {
            float w = (wide + (atElbow - wide) * along) * FACES, d = (deep + (atElbow - deep) * along) * FACES;
            return along <= 1f && a * a / (w * w) + b * b / (d * d) <= 1f;
        }
        float w = wide * FACES, d = deep * FACES, closed = Math.max(wide, deep) * FACES, behind = along * FORE;
        return a * a / (w * w) + b * b / (d * d) + behind * behind / (closed * closed) <= 1f;
    }

    private static void cutEndOfTheHandIsInsideItsCuff() {
        float wide = ACROSS * ViewModelMath.CUFF, deep = THROUGH * ViewModelMath.CUFF, atElbow = Math.max(ELBOW, deep);
        float[] cuff = new float[3], across = new float[3];
        for (int side = 0; side < 2; side++) {
            for (float[] palm : side == 0 ? RIGHT_PALMS : LEFT_PALMS) {
                for (int index = 0; index < WAYS; index++) {
                    for (int rolled = 0; rolled < 6; rolled++) {
                        Fit fit = new Fit(side == 1, palm, way(index), (float) (rolled * Math.PI / 6));
                        ViewModelMath.turned(fit.turn, fit.across, across);
                        ViewModelMath.cuff(across, fit.wrist, fit.elbow, cuff);
                        check(Math.abs(length(cuff) - 1f) < 1e-4f && Math.abs(dot(cuff, minus(fit.elbow, fit.wrist))) < 1e-4f,
                                "the line across the cuff is square to the forearm");
                        check(dot(cuff, unit(across)) > Math.cos(ViewModelMath.WRIST_MOST) - 1e-3f, "and is the hand's own, as near as the bend lets it be");
                        float[] hands = unit(minus(fit.rigid, fit.wrist)), through = cross(hands, unit(across));
                        for (int round = 0; round < 24; round++) {
                            float cos = (float) Math.cos(round * Math.PI / 12), sin = (float) Math.sin(round * Math.PI / 12);
                            float[] cut = new float[3];
                            for (int axis = 0; axis < 3; axis++) {
                                cut[axis] = fit.wrist[axis] + across[axis] / length(across) * cos * ACROSS + through[axis] * sin * THROUGH;
                            }
                            check(inCuff(cut, fit, cuff, wide, deep, atElbow), "every point of the hand's cut end is inside the cuff: way " + index + " rolled " + rolled + " round " + round);
                        }
                    }
                }
            }
        }
        // And the cuff is no wider than it has to be: a point half as far out again is outside it.
        Fit fit = new Fit(false, RIGHT_PALMS[HIP], unit(new float[] {0.45f, -0.5f, 0.73f}), 0f);
        ViewModelMath.turned(fit.turn, fit.across, across);
        ViewModelMath.cuff(across, fit.wrist, fit.elbow, cuff);
        float[] out = {fit.wrist[0] + cuff[0] * ACROSS * 1.5f, fit.wrist[1] + cuff[1] * ACROSS * 1.5f, fit.wrist[2] + cuff[2] * ACROSS * 1.5f};
        check(!inCuff(out, fit, cuff, wide, deep, atElbow), "the cuff is the wrist's size and not a ball round the hand");
    }
}
