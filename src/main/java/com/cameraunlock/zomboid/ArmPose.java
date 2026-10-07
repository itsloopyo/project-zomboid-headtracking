package com.cameraunlock.zomboid;

/**
 * Where every bone of one of the mod's own arms is, for a hand that holds something at a
 * given place a given way round: the hand is laid on what it holds, the forearm comes to it
 * from below and behind as a first person view shows an arm, turned along its length to meet
 * the hand as a forearm does, the upper arm runs on to the shoulder, and the fingers are bent
 * as the hold has them.
 *
 * Matrices are column-major float[16], the layout OpenGL loads, as in ViewModelMath.
 */
final class ArmPose {
    private ArmPose() {}

    /** How much of the turn of the hand about the forearm's length each half of the forearm takes up. */
    static final float TWIST_NEAR_ELBOW = 0.25f, TWIST_NEAR_WRIST = 0.8f;
    /** How far a forearm is drawn turned along its length from how it rests, in radians. */
    static final float TWIST_MOST = (float) Math.toRadians(80);
    /** How far a wrist is ever drawn bent, in radians. */
    static final float WRIST_MOST = (float) Math.toRadians(32);
    /** How many floats `solve` says of what it found. */
    static final int SAYS = 9;
    /** Where among them: how far the wrist is bent and the forearm turned along its length, in radians, then the elbow and the wrist, and how many times its own length the upper arm is drawn. */
    static final int BENT = 0, TWISTED = 1, ELBOW = 2, WRIST = 5, STRETCHED = 8;

    /**
     * Places an arm.
     *
     * @param scale    the model's metres to the lengths the answer is in
     * @param weights  for each of the model's poses, how much of it the fingers take: together 1
     * @param centre   the middle of what is held
     * @param across   the way across the hand to its thumb's side, of any length
     * @param out      the way out of the palm, into what is held, of any length and not along `across`
     * @param shoulder where the arm starts
     * @param from     where the forearm is to seem to come from: its elbow is on the line from there to the wrist, or as near it as the wrist bends
     * @param left     whether this is the left arm: the same hand, mirrored
     * @param round    whether what is held is round about the line across the hand, a handle: the palm is then put
     *                 wherever round it brings the forearm nearest to where it is to come from, and `out` is not used
     * @param skins    the answer: for each bone the matrix that takes a corner of the model at rest to where it is drawn
     * @param said     the answer: what BENT, TWISTED, ELBOW and WRIST name
     * @return false if the lines given make no frame or the arm has no elbow, with the answers not to be used
     */
    static boolean solve(ArmModel model, float scale, float[] weights, float[] centre, float[] across, float[] out,
            float[] shoulder, float[] from, boolean left, boolean round, float[] skins, float[] said) {
        // The left arm is the right one in a mirror: what is asked is mirrored, the right arm placed, and the answer mirrored back.
        float side = left ? -1f : 1f;
        float[] c = {centre[0] * side, centre[1], centre[2]};
        float[] s = {shoulder[0] * side, shoulder[1], shoulder[2]};
        float[] f = {from[0] * side, from[1], from[2]};
        float[] a = new float[3], o = new float[3], l = new float[3];
        if (!unit(across[0] * side, across[1], across[2], a)) {
            return false;
        }
        // The hand's three lines at rest are either a right hand's or a left's, and the lines it is laid along are made the same.
        float[] cross = new float[3];
        cross(model.along, model.across, cross);
        float handed = dot(cross, model.out) < 0f ? -1f : 1f;
        if (round) {
            // The forearm at rest leaves the hand this way, in the hand's own lines. Turned about
            // the handle, the palm is put where that way is the way to where the arm comes from.
            float[] rest = new float[3], want = {c[0] - f[0], c[1] - f[1], c[2] - f[2]}, turned = new float[9];
            unit(model.hand[0] - model.elbow[0], model.hand[1] - model.elbow[1], model.hand[2] - model.elbow[2], rest);
            float along = dot(want, a);
            if (!unit(want[0] - a[0] * along, want[1] - a[1] * along, want[2] - a[2] * along, want)) {
                return false;
            }
            float angle = (float) Math.atan2(dot(rest, model.along), dot(rest, model.out));
            about(a, -handed * angle, turned);
            turned(turned, want, o);
        } else {
            float into = out[0] * side * a[0] + out[1] * a[1] + out[2] * a[2];
            if (!unit(out[0] * side - a[0] * into, out[1] - a[1] * into, out[2] - a[2] * into, o)) {
                return false;
            }
        }
        cross(a, o, l);
        for (int axis = 0; axis < 3; axis++) {
            l[axis] *= handed;
        }

        int count = model.bones.length;
        float[] turns = new float[count * 4];
        float[] hold = new float[3];
        for (int pose = 0; pose < weights.length; pose++) {
            float weight = weights[pose];
            if (weight == 0f) {
                continue;
            }
            for (int axis = 0; axis < 3; axis++) {
                hold[axis] += model.holds[pose][axis] * weight;
            }
            float[] of = model.turns[pose];
            for (int bone = 0; bone < count; bone++) {
                int at = bone * 4;
                // A quaternion and its negative are the same turn: each is added the same way round as what is there.
                float same = turns[at] * of[at] + turns[at + 1] * of[at + 1] + turns[at + 2] * of[at + 2] + turns[at + 3] * of[at + 3];
                float way = same < 0f ? -weight : weight;
                for (int i = 0; i < 4; i++) {
                    turns[at + i] += of[at + i] * way;
                }
            }
        }

        // The hand, rigid: its lines at rest go to the lines asked for, and what it holds to the middle asked for.
        float[] hand = new float[16], turn = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                turn[column * 3 + row] = l[row] * model.along[column] + a[row] * model.across[column] + o[row] * model.out[column];
                hand[column * 4 + row] = turn[column * 3 + row] * scale;
            }
        }
        hand[15] = 1f;
        for (int row = 0; row < 3; row++) {
            hand[12 + row] = c[row] - (hand[row] * hold[0] + hand[4 + row] * hold[1] + hand[8 + row] * hold[2]);
        }
        float[] wrist = new float[3], elbow = new float[3];
        ViewModelMath.place(hand, model.hand[0], model.hand[1], model.hand[2], wrist);
        float[] upper0 = new float[3], fore0 = new float[3], square0 = new float[3];
        float[] upper = new float[3], fore = new float[3], square = new float[3];
        unit(model.elbow[0] - model.shoulder[0], model.elbow[1] - model.shoulder[1], model.elbow[2] - model.shoulder[2], upper0);
        unit(model.hand[0] - model.elbow[0], model.hand[1] - model.elbow[1], model.hand[2] - model.elbow[2], fore0);
        // The forearm comes to the wrist from where it was asked to seem to come from, as near
        // as the wrist lets it: the hand says where it would have its forearm, straight behind
        // its wrist, and the forearm is never further off that than a wrist bends.
        float[] asHeld = new float[3], toward = new float[3], way = new float[3];
        turned(turn, fore0, asHeld);
        if (!unit(wrist[0] - f[0], wrist[1] - f[1], wrist[2] - f[2], toward)) {
            return false;
        }
        float apart = (float) Math.acos(Math.clamp(dot(asHeld, toward), -1f, 1f));
        if (apart > WRIST_MOST) {
            float[] swung = new float[9];
            float[] axis = new float[3];
            cross(asHeld, toward, axis);
            if (!unit(axis[0], axis[1], axis[2], axis)) {
                return false;
            }
            about(axis, WRIST_MOST, swung);
            turned(swung, asHeld, way);
        } else {
            System.arraycopy(toward, 0, way, 0, 3);
        }
        float reach = model.foreLength * scale;
        for (int axis = 0; axis < 3; axis++) {
            elbow[axis] = wrist[axis] - way[axis] * reach;
        }
        // The upper arm runs from the shoulder to wherever that puts the elbow, as long as that is.
        float stretch = apart(s, elbow) / (model.upperLength * scale);
        if (!(stretch > 1e-3f)) {
            return false;
        }

        cross(upper0, fore0, square0);
        unit(square0[0], square0[1], square0[2], square0);
        unit(elbow[0] - s[0], elbow[1] - s[1], elbow[2] - s[2], upper);
        unit(wrist[0] - elbow[0], wrist[1] - elbow[1], wrist[2] - elbow[2], fore);
        cross(upper, fore, square);
        if (!unit(square[0], square[1], square[2], square)) {
            return false;
        }
        float[] upperTurn = new float[9], foreTurn = new float[9], withHand = new float[9];
        between(upper0, square0, upper, square, upperTurn);
        between(fore0, square0, fore, square, foreTurn);
        // The forearm as the hand would have it: the hand's own turn, swung the least that brings its forearm onto the arm's.
        float[] swing = new float[9];
        float bent = (float) Math.acos(Math.clamp(dot(asHeld, fore), -1f, 1f));
        leastTurn(asHeld, fore, square, swing);
        multiply3(swing, turn, withHand);
        float[] carried = new float[3], side3 = new float[3];
        turned(withHand, square0, carried);
        cross(square, carried, side3);
        float twist = (float) Math.atan2(dot(side3, fore), dot(square, carried));
        // A forearm turns so far along its length and no further. What is left over is the whole
        // arm's, turned about the forearm from the elbow up, where it is out of sight.
        float within = Math.clamp(twist, -TWIST_MOST, TWIST_MOST), over = twist - within;
        float[] nearElbow = new float[9], nearWrist = new float[9], about = new float[9], rolled = new float[9];
        about(fore, over + within * TWIST_NEAR_ELBOW, about);
        multiply3(about, foreTurn, nearElbow);
        about(fore, over + within * TWIST_NEAR_WRIST, about);
        multiply3(about, foreTurn, nearWrist);
        about(upper, over, about);
        multiply3(about, upperTurn, rolled);
        System.arraycopy(rolled, 0, upperTurn, 0, 9);

        // Drawn out along its own length, before it is turned: the shoulder end is out of sight.
        float[] drawn = new float[9], long3 = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                long3[column * 3 + row] = (row == column ? 1f : 0f) + (stretch - 1f) * upper0[row] * upper0[column];
            }
        }
        multiply3(upperTurn, long3, drawn);
        System.arraycopy(drawn, 0, upperTurn, 0, 9);
        pivot(upperTurn, scale, model.shoulder, s, skins, model.upper * 16);
        System.arraycopy(skins, model.upper * 16, skins, model.upperTwist * 16, 16);
        pivot(nearElbow, scale, model.elbow, elbow, skins, model.fore * 16);
        // Both halves of the forearm turn about the one line from the elbow to the wrist, so both end at the wrist.
        pivot(nearWrist, scale, model.elbow, elbow, skins, model.foreTwist * 16);
        System.arraycopy(hand, 0, skins, model.wrist * 16, 16);

        // The fingers, each bone from the one before: where it rests against its parent, then its own bend.
        float[] worlds = new float[count * 16], bend = new float[16], placed = new float[16];
        System.arraycopy(model.rests, model.wrist * 16, worlds, model.wrist * 16, 16);
        for (int bone = model.wrist + 1; bone < count; bone++) {
            quaternion(turns, bone * 4, bend);
            multiply(model.locals, bone * 16, bend, 0, placed, 0);
            multiply(worlds, model.parents[bone] * 16, placed, 0, worlds, bone * 16);
            multiply(worlds, bone * 16, model.unrests, bone * 16, placed, 0);
            multiply(hand, 0, placed, 0, skins, bone * 16);
        }

        if (left) {
            for (int bone = 0; bone < count; bone++) {
                for (int column = 0; column < 4; column++) {
                    skins[bone * 16 + column * 4] = -skins[bone * 16 + column * 4];
                }
            }
        }
        said[BENT] = bent;
        said[TWISTED] = within;
        said[STRETCHED] = stretch;
        for (int axis = 0; axis < 3; axis++) {
            said[ELBOW + axis] = elbow[axis] * (axis == 0 ? side : 1f);
            said[WRIST + axis] = wrist[axis] * (axis == 0 ? side : 1f);
        }
        return true;
    }

    /** The turn that takes one pair of lines square to each other to another such pair, by columns, three by three. */
    private static void between(float[] from, float[] fromSquare, float[] to, float[] toSquare, float[] out) {
        float[] fromThird = new float[3], toThird = new float[3];
        cross(from, fromSquare, fromThird);
        cross(to, toSquare, toThird);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                out[column * 3 + row] = to[row] * from[column] + toSquare[row] * fromSquare[column] + toThird[row] * fromThird[column];
            }
        }
    }

    /** The least turn that takes one line of length one to another. Dead against each other, it is half a turn about what is square to `spare`. */
    private static void leastTurn(float[] from, float[] to, float[] spare, float[] out) {
        float[] axis = new float[3];
        cross(from, to, axis);
        float sin = (float) Math.sqrt(dot(axis, axis)), cos = dot(from, to);
        if (!unit(axis[0], axis[1], axis[2], axis)) {
            if (cos > 0f) {
                for (int i = 0; i < 9; i++) {
                    out[i] = i % 4 == 0 ? 1f : 0f;
                }
                return;
            }
            cross(from, spare, axis);
            if (!unit(axis[0], axis[1], axis[2], axis)) {
                cross(from, new float[] {from[1], from[2], from[0]}, axis);
                unit(axis[0], axis[1], axis[2], axis);
            }
        }
        about(axis, (float) Math.atan2(sin, cos), out);
    }

    /** The turn about a line of length one by an angle in radians, by columns, three by three. */
    static void about(float[] axis, float angle, float[] out) {
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle), rest = 1f - cos;
        float x = axis[0], y = axis[1], z = axis[2];
        out[0] = cos + x * x * rest;
        out[1] = y * x * rest + z * sin;
        out[2] = z * x * rest - y * sin;
        out[3] = x * y * rest - z * sin;
        out[4] = cos + y * y * rest;
        out[5] = z * y * rest + x * sin;
        out[6] = x * z * rest + y * sin;
        out[7] = y * z * rest - x * sin;
        out[8] = cos + z * z * rest;
    }

    /** The matrix that turns and scales about one point and puts that point at another. */
    private static void pivot(float[] turn, float scale, float[] from, float[] to, float[] out, int at) {
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) {
                out[at + column * 4 + row] = turn[column * 3 + row] * scale;
            }
            out[at + column * 4 + 3] = 0f;
        }
        for (int row = 0; row < 3; row++) {
            out[at + 12 + row] = to[row] - (out[at + row] * from[0] + out[at + 4 + row] * from[1] + out[at + 8 + row] * from[2]);
        }
        out[at + 15] = 1f;
    }

    /** The matrix of a quaternion kept as w, x, y, z, of any length but nought. */
    static void quaternion(float[] from, int at, float[] out) {
        float w = from[at], x = from[at + 1], y = from[at + 2], z = from[at + 3];
        float length = (float) Math.sqrt(w * w + x * x + y * y + z * z);
        w /= length;
        x /= length;
        y /= length;
        z /= length;
        out[0] = 1f - 2f * (y * y + z * z);
        out[1] = 2f * (x * y + w * z);
        out[2] = 2f * (x * z - w * y);
        out[3] = 0f;
        out[4] = 2f * (x * y - w * z);
        out[5] = 1f - 2f * (x * x + z * z);
        out[6] = 2f * (y * z + w * x);
        out[7] = 0f;
        out[8] = 2f * (x * z + w * y);
        out[9] = 2f * (y * z - w * x);
        out[10] = 1f - 2f * (x * x + y * y);
        out[11] = 0f;
        out[12] = 0f;
        out[13] = 0f;
        out[14] = 0f;
        out[15] = 1f;
    }

    /** One matrix times another, each at a place in its array. The answer may be neither. */
    static void multiply(float[] a, int aAt, float[] b, int bAt, float[] out, int outAt) {
        for (int column = 0; column < 4; column++) {
            float x = b[bAt + column * 4], y = b[bAt + column * 4 + 1], z = b[bAt + column * 4 + 2], w = b[bAt + column * 4 + 3];
            for (int row = 0; row < 4; row++) {
                out[outAt + column * 4 + row] = a[aAt + row] * x + a[aAt + 4 + row] * y + a[aAt + 8 + row] * z + a[aAt + 12 + row] * w;
            }
        }
    }

    private static void multiply3(float[] a, float[] b, float[] out) {
        for (int column = 0; column < 3; column++) {
            float x = b[column * 3], y = b[column * 3 + 1], z = b[column * 3 + 2];
            for (int row = 0; row < 3; row++) {
                out[column * 3 + row] = a[row] * x + a[3 + row] * y + a[6 + row] * z;
            }
        }
    }

    private static void turned(float[] turn, float[] line, float[] out) {
        float x = line[0], y = line[1], z = line[2];
        out[0] = turn[0] * x + turn[3] * y + turn[6] * z;
        out[1] = turn[1] * x + turn[4] * y + turn[7] * z;
        out[2] = turn[2] * x + turn[5] * y + turn[8] * z;
    }

    /** The inverse of a matrix that turns and moves and does nothing else. */
    static void rigidInverse(float[] from, int at, float[] out, int outAt) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                out[outAt + column * 4 + row] = from[at + row * 4 + column];
            }
            out[outAt + row * 4 + 3] = 0f;
        }
        for (int row = 0; row < 3; row++) {
            out[outAt + 12 + row] = -(out[outAt + row] * from[at + 12] + out[outAt + 4 + row] * from[at + 13] + out[outAt + 8 + row] * from[at + 14]);
        }
        out[outAt + 15] = 1f;
    }

    static float apart(float[] a, float[] b) {
        float x = a[0] - b[0], y = a[1] - b[1], z = a[2] - b[2];
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    static void cross(float[] a, float[] b, float[] out) {
        float x = a[1] * b[2] - a[2] * b[1], y = a[2] * b[0] - a[0] * b[2], z = a[0] * b[1] - a[1] * b[0];
        out[0] = x;
        out[1] = y;
        out[2] = z;
    }

    /** False for a line of no length, with out left as it was. */
    static boolean unit(float x, float y, float z, float[] out) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (!(length > 1e-6f)) {
            return false;
        }
        out[0] = x / length;
        out[1] = y / length;
        out[2] = z / length;
        return true;
    }
}
