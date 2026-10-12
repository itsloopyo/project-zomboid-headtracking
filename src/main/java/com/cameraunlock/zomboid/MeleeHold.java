package com.cameraunlock.zomboid;

/**
 * How something swung is held and hit with, by what its mesh says of it: which of its sides strikes, and how far it reaches
 * from the hand. The game's art makes every melee weapon along the mesh's y from where the hand holds it, and puts its
 * striking side, an axe's or a machete's edge, a hammer's face, a knife's edge, the face of a club's head, on the mesh's -z:
 * the game's own swings carry the head within 55 degrees of that way on all twenty weapons measured. A head that is a
 * plate facing that way, a pan's, strikes with its face, whichever way the plate is turned about the handle: the game's
 * frying pan is turned 45 degrees.
 *
 * Mesh axes throughout: y along the handle from the hand, z and x across it.
 */
final class MeleeHold {
    private MeleeHold() {}

    /**
     * What a mesh says of what is swung.
     *
     * @param butt     how far along y the end behind the hand is, in the mesh's units: nought or less
     * @param tip      how far along y the far end is
     * @param strikeX  the striking side, a way across the handle of length one: its x
     * @param strikeZ  and its z
     */
    record Shape(float butt, float tip, float strikeX, float strikeZ) {}

    /** The share of the length, from the far end, that is the head. */
    static final float HEAD = 0.35f;
    /**
     * A head whose thinnest spread across is under this share of its next thinnest is a plate. A plate turned with its
     * face to within 60 degrees of the mesh's -z, a pan's, strikes with its face; one edge on to it, the blade of an axe,
     * a knife or a machete, strikes with its edge, the -z.
     */
    static final float PLATE = 0.35f, FACING = 0.5f;

    /**
     * Reads a mesh.
     *
     * @param places the mesh's vertices, three floats each
     * @return null for a mesh with no length along y
     */
    static Shape of(float[] places) {
        float butt = Float.MAX_VALUE, tip = -Float.MAX_VALUE;
        for (int i = 1; i < places.length; i += 3) {
            butt = Math.min(butt, places[i]);
            tip = Math.max(tip, places[i]);
        }
        if (!(tip > butt)) {
            return null;
        }
        float from = tip - (tip - butt) * HEAD;
        double[] mean = new double[3];
        int count = 0;
        for (int i = 0; i < places.length; i += 3) {
            if (places[i + 1] >= from) {
                for (int axis = 0; axis < 3; axis++) {
                    mean[axis] += places[i + axis];
                }
                count++;
            }
        }
        double[] spread = new double[9];
        for (int i = 0; i < places.length; i += 3) {
            if (places[i + 1] >= from) {
                for (int row = 0; row < 3; row++) {
                    for (int column = 0; column < 3; column++) {
                        spread[row * 3 + column] += (places[i + row] - mean[row] / count) * (places[i + column] - mean[column] / count);
                    }
                }
            }
        }
        double[] values = new double[3], vectors = new double[9];
        symmetric(spread, values, vectors);
        int thinnest = 0, middle = 1;
        for (int axis = 1; axis < 3; axis++) {
            if (values[axis] < values[thinnest]) {
                thinnest = axis;
            }
        }
        for (int axis = 0; axis < 3; axis++) {
            if (axis != thinnest && (middle == thinnest || values[axis] < values[middle])) {
                middle = axis;
            }
        }
        float sx = 0f, sz = -1f;
        double nx = vectors[thinnest], ny = vectors[3 + thinnest], nz = vectors[6 + thinnest];
        if (Math.sqrt(Math.max(values[thinnest], 0)) < PLATE * Math.sqrt(Math.max(values[middle], 0)) && Math.abs(ny) < 0.5 && Math.abs(nz) >= FACING) {
            double across = Math.hypot(nx, nz), sign = nz > 0 ? -1 : 1;
            sx = (float) (nx / across * sign);
            sz = (float) (nz / across * sign);
        }
        return new Shape(Math.min(butt, 0f), tip, sx, sz);
    }

    /** The eigenvalues and eigenvectors (by columns) of a symmetric 3 by 3 matrix, by rows, by Jacobi's turns. */
    private static void symmetric(double[] matrix, double[] values, double[] vectors) {
        double[] a = matrix.clone();
        for (int i = 0; i < 9; i++) {
            vectors[i] = i % 4 == 0 ? 1 : 0;
        }
        for (int sweep = 0; sweep < 50; sweep++) {
            double off = a[1] * a[1] + a[2] * a[2] + a[5] * a[5];
            if (off < 1e-30) {
                break;
            }
            for (int p = 0; p < 2; p++) {
                for (int q = p + 1; q < 3; q++) {
                    double apq = a[p * 3 + q];
                    if (Math.abs(apq) < 1e-300) {
                        continue;
                    }
                    double theta = (a[q * 3 + q] - a[p * 3 + p]) / (2 * apq);
                    double t = Math.signum(theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1));
                    if (theta == 0) {
                        t = 1;
                    }
                    double c = 1 / Math.sqrt(t * t + 1), s = t * c;
                    for (int k = 0; k < 3; k++) {
                        double akp = a[k * 3 + p], akq = a[k * 3 + q];
                        a[k * 3 + p] = c * akp - s * akq;
                        a[k * 3 + q] = s * akp + c * akq;
                    }
                    for (int k = 0; k < 3; k++) {
                        double apk = a[p * 3 + k], aqk = a[q * 3 + k];
                        a[p * 3 + k] = c * apk - s * aqk;
                        a[q * 3 + k] = s * apk + c * aqk;
                    }
                    for (int k = 0; k < 3; k++) {
                        double vkp = vectors[k * 3 + p], vkq = vectors[k * 3 + q];
                        vectors[k * 3 + p] = c * vkp - s * vkq;
                        vectors[k * 3 + q] = s * vkp + c * vkq;
                    }
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            values[i] = a[i * 4];
        }
    }

    /**
     * The game's classes of what is swung, as its WeaponType has them: each is held and swung its own way. A stone is
     * one-handed to the game, which swings it by an animation of its own, and has no handle.
     */
    static final int ONE_HANDED = 0, TWO_HANDED = 1, HEAVY = 2, KNIFE = 3, SPEAR = 4, STONE = 5;

    /**
     * Where something swung is held at rest, as ViewModelMath.attack takes a hold: upright, low and to the right, its far
     * end raised this far in the mod's own hand, which holds a handle nearly square to its forearm, and in the game's own.
     */
    static final float UPRIGHT_IN_HAND = (float) Math.toRadians(60), UPRIGHT = (float) Math.toRadians(35);
    private static final float[] RESTS = {0f, (float) Math.toRadians(-12), (float) Math.toRadians(-10), 0.2f, -0.22f, 0.38f, 0f};
    /** Those holds upright, in the mod's own hand and in the game's: what is held while its mesh says nothing. Not to be written. */
    static final float[] UPRIGHT_HELD_IN_HAND = upright(UPRIGHT_IN_HAND), UPRIGHT_HELD = upright(UPRIGHT);

    private static float[] upright(float pitch) {
        float[] out = RESTS.clone();
        out[0] = pitch;
        return out;
    }
    /**
     * What is too long to stand upright in the picture leans over across the body towards this, as far as keeps its far
     * end in the picture: its far end up and to the left, its top tipped over to the left.
     */
    private static final float[] ACROSS = {(float) Math.toRadians(28), (float) Math.toRadians(-38), (float) Math.toRadians(-48), 0.2f, -0.23f, 0.36f, 0f};
    /** A stone at rest is held low in the right fist, at the foot of the picture. */
    private static final float[] IN_THE_FIST = {(float) Math.toRadians(40), (float) Math.toRadians(-12), (float) Math.toRadians(-10), 0.2f, -0.235f, 0.42f, 0f};
    /** A spear at rest is held low and level, its point ahead, a little in from the right. */
    private static final float[] LEVELLED = {(float) Math.toRadians(7), (float) Math.toRadians(-7), 0f, 0.15f, -0.22f, 0.27f, 0f};
    /** How far up the picture, in parts of the way from its middle to its top edge, the far end of what is held at rest may come. */
    static final float REST_TOP = 0.85f;
    /** The tangent of half the height of the picture the rest is fitted to: the default field of view's. */
    private static final float TAN = (float) Math.tan(Math.toRadians(FieldOfView.DEFAULT_DEGREES) / 2);

    /**
     * How something swung is held at rest.
     *
     * @param kind      one of the classes
     * @param reach     how far its far end is from the hand, in tiles
     * @param trueHands whether it is in the mod's own hands
     * @param out       the hold, as ViewModelMath.attack takes it
     */
    static void rest(int kind, float reach, boolean trueHands, float[] out) {
        if (kind == SPEAR || kind == STONE) {
            System.arraycopy(kind == SPEAR ? LEVELLED : IN_THE_FIST, 0, out, 0, ViewModelMath.HOLD);
            return;
        }
        float[] upright = trueHands ? UPRIGHT_HELD_IN_HAND : UPRIGHT_HELD;
        float low = 0f, high = 1f;
        if (farEndUp(upright, reach) <= REST_TOP * TAN) {
            high = 0f;
        } else {
            for (int step = 0; step < 16; step++) {
                float mid = (low + high) / 2f;
                between(upright, ACROSS, mid, out);
                if (farEndUp(out, reach) <= REST_TOP * TAN) {
                    high = mid;
                } else {
                    low = mid;
                }
            }
        }
        between(upright, ACROSS, high, out);
    }

    private static void between(float[] from, float[] to, float share, float[] out) {
        for (int i = 0; i < ViewModelMath.HOLD; i++) {
            out[i] = from[i] + (to[i] - from[i]) * share;
        }
    }

    /** How high in the picture the far end of something held is, as the tangent of its angle above the line ahead: positive up. */
    static float farEndUp(float[] hold, float reach) {
        float[] matrix = new float[16], end = new float[3];
        ViewModelMath.hold(new float[3], new float[] {0f, 1f, 0f}, new float[] {0f, 0f, 1f}, null, 1f, hold[3], hold[4], hold[5], matrix);
        ViewModelMath.turn(matrix, hold[0], hold[1], hold[2], hold[3], hold[4], -hold[5], 0f, 0f, 0f);
        ViewModelMath.place(matrix, 0f, reach, 0f, end);
        return -end[2] > 1e-3f ? end[1] / -end[2] : Float.POSITIVE_INFINITY;
    }

    /**
     * Where the hands go on a handle, in metres. The game's own hand is this far in from the end of the handle on the
     * one-handed weapons measured, in its idle and its swings (0.06 to 0.13); the same is where the left fist is on
     * something held in both, with the right one a fist above it. A spear's rear hand is this far in from its butt (the
     * game's own: 0.22 to 0.30), and the left one is out along the shaft this far ahead of it (the game's own: 0.46 to 0.55).
     */
    static final float HAND_IN = 0.1f, SPEAR_BUTT = 0.24f, SPEAR_APART = 0.5f;

    /**
     * Where along the mesh the right hand holds something swung, in the mesh's units. Taken from the mesh and not from
     * the game's hand as its animation has it: that slides up and down a long handle as the game's own swings go, half
     * the length of a sledgehammer's, and the weapon would slide through the fist with it.
     *
     * @param perUnit how long one of the mesh's units is, in metres, as the game draws the model: its script's scale is in it
     */
    static float hand(int kind, Shape shape, float perUnit) {
        if (kind == STONE) {
            // In the middle of the fist.
            return (shape.butt() + shape.tip()) / 2f;
        }
        float in = switch (kind) {
            case SPEAR -> SPEAR_BUTT;
            case TWO_HANDED, HEAVY -> HAND_IN + ArmHolds.LOWER_FIST;
            default -> HAND_IN;
        };
        return shape.butt() + in / perUnit;
    }

    /**
     * Where the left fist is on something held in both hands, as ArmHolds.Weapon has it: how far along from the right
     * fist, in metres. A spear is held with the left hand out along the shaft ahead of the right, which drives it from
     * behind, and never past half way from the right hand to the point. Anything else is held with the left fist under
     * the right at the end of the handle.
     */
    static float second(int kind, Shape shape, float perUnit) {
        if (kind == SPEAR) {
            return Math.min(SPEAR_APART, (shape.tip() - hand(kind, shape, perUnit)) * perUnit / 2f);
        }
        return -ArmHolds.LOWER_FIST;
    }

    /**
     * How a weapon is held, worked out once for it and kept while it is held: where the right hand is on its mesh, in
     * its units, how far its far end is from the hand, in tiles, where the left fist is, ArmHolds.Weapon's `second`,
     * the hold at rest, and for each way of hitting how far it is turned to land where a bat does (`aimed`).
     */
    record Fit(int kind, Shape shape, float perUnit, float tilesPerUnit, boolean trueHands, float hand, float reach, float second,
            float[] rest, float[] aimed) {
        boolean fits(int kind, Shape shape, float perUnit, float tilesPerUnit, boolean trueHands) {
            return this.kind == kind && this.shape == shape && this.perUnit == perUnit && this.tilesPerUnit == tilesPerUnit && this.trueHands == trueHands;
        }
    }

    /**
     * @param perUnit      how long one of the mesh's units is, in metres
     * @param tilesPerUnit and in the view's tiles
     */
    static Fit fit(int kind, Shape shape, float perUnit, float tilesPerUnit, boolean trueHands) {
        float hand = hand(kind, shape, perUnit), reach = (shape.tip() - hand) * tilesPerUnit;
        float[] rest = new float[ViewModelMath.HOLD], aimed = new float[ViewModelMath.STABBED + 1];
        rest(kind, reach, trueHands, rest);
        for (int way = 0; way < aimed.length; way++) {
            aimed[way] = aimed(way, reach);
        }
        return new Fit(kind, shape, perUnit, tilesPerUnit, trueHands, hand, reach, second(kind, shape, perUnit), rest, aimed);
    }

    /** How far into an attack the hands are, 0 at rest and 1 from when it is drawn back until it is being brought back, in step with ViewModelMath.attack. */
    static float share(float reached, float past, boolean struck) {
        if (!(past > 0f)) {
            float r = Math.clamp(reached, 0f, 1f);
            return r < ViewModelMath.WINDS ? ViewModelMath.smooth(r / ViewModelMath.WINDS) : 1f;
        }
        float p = Math.min(past, 1f), turns = struck ? ViewModelMath.SPRINGS : ViewModelMath.FOLLOWS;
        return p < turns ? 1f : 1f - ViewModelMath.smooth((p - turns) / (1f - turns));
    }

    /**
     * How far the far end of a baseball bat held as `hand` puts it is from the hand, in tiles. The swings were tuned on it and
     * seen to land on what they hit: its head is where a blow lands.
     */
    static final float BAT_REACH = 0.6f;

    /**
     * How far an attack with something longer than a bat is turned to the right as it lands, in radians, so that its head
     * lands on what it hits and not past it: on the line a bat's lands on, and from there, the longer it is, nearer the
     * line ahead, on which something FULL_REACH long or longer lands. A sledgehammer swung from the end of its handle,
     * landing on a bat's line, landed past a zombie 1.1 tiles off, at its shoulder. Nothing shorter than a bat is turned,
     * and nothing thrust, stabbed or shoved: those carry the head straight to what they hit.
     *
     * @param reach how far the far end is from the hand, in tiles
     */
    static float aimed(int way, float reach) {
        if (!(reach > BAT_REACH) || way == ViewModelMath.THRUST || way == ViewModelMath.SHOVED || way == ViewModelMath.STABBED) {
            return 0f;
        }
        float[] pose = new float[ViewModelMath.HOLD];
        ViewModelMath.attack(way, 1f, 0f, false, RESTS, pose);
        float target = across(pose, BAT_REACH) * Math.clamp((FULL_REACH - reach) / (FULL_REACH - BAT_REACH), 0f, 1f), yaw = pose[1], low = yaw - MOST_AIMED, high = yaw + MOST_AIMED;
        for (int step = 0; step < 20; step++) {
            pose[1] = (low + high) / 2f;
            // Turning to the right moves the head to the right.
            if (across(pose, reach) < target) {
                low = pose[1];
            } else {
                high = pose[1];
            }
        }
        return (low + high) / 2f - yaw;
    }

    /** How long something is whose head lands on the line ahead, in tiles: a sledgehammer held at its end reaches 0.89. */
    static final float FULL_REACH = 0.85f;

    /** The most an attack is turned by `aimed`, either way, in radians. */
    private static final float MOST_AIMED = (float) Math.toRadians(60);

    /** How far to the right of the line ahead the far end of something held is, as the tangent of its angle from it. */
    static float across(float[] hold, float reach) {
        float[] matrix = new float[16], end = new float[3];
        ViewModelMath.hold(new float[3], new float[] {0f, 1f, 0f}, new float[] {0f, 0f, 1f}, null, 1f, hold[3], hold[4], hold[5], matrix);
        ViewModelMath.turn(matrix, hold[0], hold[1], hold[2], hold[3], hold[4], -hold[5], 0f, 0f, 0f);
        ViewModelMath.place(matrix, 0f, reach, 0f, end);
        return end[0] / -end[2];
    }

    /**
     * The way the game's attack is drawn with something of a class: a knife or a spear brought down on what lies on the ground
     * is stabbed into it point first, where anything else is brought down on it.
     */
    static int way(int kind, int attack) {
        return attack == ViewModelMath.FLOORED && (kind == KNIFE || kind == SPEAR) ? ViewModelMath.STABBED : attack;
    }

    /**
     * How far each way of hitting turns what is swung about its handle, in radians, so that its striking side leads
     * the blow as it lands, as `top` turns it: worked out from where ViewModelMath.attack carries the far end
     * just before it lands and as it lands. A thrust and a stab lead with the point and a shove with the shaft, and are not turned.
     */
    static final float[] LEADS = new float[ViewModelMath.STABBED + 1];
    static {
        for (int way = ViewModelMath.SWUNG; way <= ViewModelMath.FLOORED; way++) {
            LEADS[way] = lead(way);
        }
    }

    /** How far the far end of something held in `swung` is carried across its handle while it lands, in the mesh's axes: x and z. */
    static float[] landing(int way) {
        float[] before = new float[16], after = new float[16], pose = new float[ViewModelMath.HOLD];
        float[] origin = {0f, 0f, 0f}, along = {0f, 1f, 0f}, up = {0f, 0f, 1f};
        float[][] matrices = {before, after};
        float[] reached = {LANDING_FROM, 1f};
        for (int i = 0; i < 2; i++) {
            // The way to landing does not depend on the hold at rest.
            ViewModelMath.attack(way, reached[i], 0f, false, RESTS, pose);
            ViewModelMath.hold(origin, along, up, null, 1f, pose[3], pose[4], pose[5], matrices[i]);
            ViewModelMath.turn(matrices[i], pose[0], pose[1], pose[2], pose[3], pose[4], -pose[5], 0f, 0f, 0f);
        }
        float[] from = new float[3], to = new float[3];
        ViewModelMath.place(before, 0f, 1f, 0f, from);
        ViewModelMath.place(after, 0f, 1f, 0f, to);
        float mx = to[0] - from[0], my = to[1] - from[1], mz = to[2] - from[2];
        // The matrix's columns are the mesh's axes in the view, square to each other and of length one.
        return new float[] {after[0] * mx + after[1] * my + after[2] * mz, after[8] * mx + after[9] * my + after[10] * mz};
    }

    /** How near landing the far end's way is taken from: the share of the way to landing. */
    static final float LANDING_FROM = 0.96f;

    /** The twist that turns a striking side on the mesh's -z onto the way the far end goes as it lands. */
    private static float lead(int way) {
        float[] going = landing(way);
        // Turning the hold's top by a twist turns the mesh under it by the opposite: this twist brings the way the head
        // goes onto the mesh's -z.
        return -(float) Math.atan2(-going[0], -going[1]);
    }

    /**
     * The mesh's way that is held as the weapon's top, ViewModelMath.hold's `up`, at a moment of an attack. At rest
     * it is the mesh's z, as the game's art stands it in the hand. As the attack is drawn back the weapon turns about
     * its handle until its striking side leads the way the attack carries its head as it lands, and turns back as it
     * is brought back, `share` of the way: see `share`. A positive turn takes the striking side from below towards the
     * weapon's left as it is held.
     */
    static void top(Shape shape, int way, float share, float[] out) {
        // How far round from -z the striking side is, the same way.
        float strikes = (float) Math.atan2(-shape.strikeX(), -shape.strikeZ());
        float turn = (strikes + LEADS[way]) * share;
        out[0] = (float) Math.sin(turn);
        out[1] = 0f;
        out[2] = (float) Math.cos(turn);
    }
}
