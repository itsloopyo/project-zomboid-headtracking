package com.cameraunlock.zomboid;

/**
 * The arithmetic of holding a weapon up in the first person view: the matrix that takes the
 * weapon from where the game's animation has it to a place before the eye, and the easing,
 * recoil and bob that move it there.
 *
 * View axes are x to the right, y up and z towards the viewer, so straight ahead is -z.
 * Matrices are column-major float[16], the layout OpenGL loads.
 */
final class ViewModelMath {
    private ViewModelMath() {}

    /**
     * A value moved towards its target over a frame. In `constant` seconds it covers all but
     * 1/e of what was left, however many frames that time is cut into.
     */
    static float ease(float value, float target, float seconds, float constant) {
        return target + (value - target) * (float) Math.exp(-seconds / constant);
    }

    /** The shortest way from one angle to another, in radians, between minus and plus half a turn. */
    static float turned(float from, float to) {
        return (float) Math.IEEEremainder(to - from, 2 * Math.PI);
    }

    /**
     * Where a step's sway puts the weapon: side to side once every two steps, and down and back
     * up once a step.
     *
     * @param phase  half a turn for every step taken, in radians
     * @param amount 0 standing still, 1 at a walk
     * @param out    how far to the right and how far up, in tiles
     */
    static void bob(float phase, float amount, float[] out) {
        out[0] = (float) Math.sin(phase) * BOB_ACROSS * amount;
        out[1] = -(float) Math.abs(Math.sin(phase)) * BOB_DOWN * amount;
    }

    static final float BOB_ACROSS = 0.012f, BOB_DOWN = 0.01f;

    /**
     * The matrix that takes a model from the place the game posed it to a place held before
     * the eye. The weapon's own point `origin` goes `ahead` in front of the eye, its `forward`
     * goes straight ahead and its `up` straight up; everything else of the model keeps its
     * place against the weapon, which is what makes the hands that hold it come with it.
     *
     * With a muzzle the line of the barrel passes through (atX, atY), whatever height the
     * barrel is above the grip. Without one the origin itself is put there.
     *
     * The game draws its models mirrored, and so does this: a triangle faces the same way
     * through this matrix as through the one the game stands the model in the world with.
     *
     * @param origin  the weapon's own origin, in the model's axes
     * @param forward the way the weapon points, in the model's axes, of any length but nought
     * @param up      the weapon's top, in the model's axes, not along forward
     * @param muzzle  the end of the barrel in the model's axes, or null
     * @param scale   the model's units to tiles
     * @return false if forward and up do not make a frame, with out left as it was
     */
    static boolean hold(float[] origin, float[] forward, float[] up, float[] muzzle, float scale,
            float atX, float atY, float ahead, float[] out) {
        float fx = forward[0], fy = forward[1], fz = forward[2];
        float length = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (!(length > NO_LENGTH)) {
            return false;
        }
        fx /= length;
        fy /= length;
        fz /= length;
        float along = up[0] * fx + up[1] * fy + up[2] * fz;
        float ux = up[0] - along * fx, uy = up[1] - along * fy, uz = up[2] - along * fz;
        length = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (!(length > NO_LENGTH)) {
            return false;
        }
        ux /= length;
        uy /= length;
        uz /= length;
        float sx = fy * uz - fz * uy, sy = fz * ux - fx * uz, sz = fx * uy - fy * ux;

        // The rows are where each view axis reads its part of a model point from: the side
        // axis goes to the left, which is the mirror.
        out[0] = -sx * scale; out[4] = -sy * scale; out[8] = -sz * scale;
        out[1] = ux * scale; out[5] = uy * scale; out[9] = uz * scale;
        out[2] = -fx * scale; out[6] = -fy * scale; out[10] = -fz * scale;
        out[3] = 0f; out[7] = 0f; out[11] = 0f; out[15] = 1f;
        float x = atX, y = atY;
        if (muzzle != null) {
            float mx = muzzle[0] - origin[0], my = muzzle[1] - origin[1], mz = muzzle[2] - origin[2];
            x -= out[0] * mx + out[4] * my + out[8] * mz;
            y -= out[1] * mx + out[5] * my + out[9] * mz;
        }
        out[12] = x - (out[0] * origin[0] + out[4] * origin[1] + out[8] * origin[2]);
        out[13] = y - (out[1] * origin[0] + out[5] * origin[1] + out[9] * origin[2]);
        out[14] = -ahead - (out[2] * origin[0] + out[6] * origin[1] + out[10] * origin[2]);
        return true;
    }

    private static final float NO_LENGTH = 1e-6f;

    /**
     * Turns and moves what a matrix has placed, about a point that stays where it is but for
     * the move: rolled about the line ahead, then pitched, then yawed, in radians. Positive
     * pitch raises what points ahead, positive yaw swings it to the right and positive roll
     * tips its top to the left.
     */
    static void turn(float[] matrix, float pitch, float yaw, float roll, float pivotX, float pivotY, float pivotZ,
            float moveX, float moveY, float moveZ) {
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float cy = (float) Math.cos(-yaw), sy = (float) Math.sin(-yaw);
        float cr = (float) Math.cos(roll), sr = (float) Math.sin(roll);
        // Yaw about y, times pitch about x, times roll about z.
        float r00 = cy * cr + sy * sp * sr, r01 = -cy * sr + sy * sp * cr, r02 = sy * cp;
        float r10 = cp * sr, r11 = cp * cr, r12 = -sp;
        float r20 = -sy * cr + cy * sp * sr, r21 = sy * sr + cy * sp * cr, r22 = cy * cp;
        for (int column = 0; column < 4; column++) {
            int at = column * 4;
            float x = matrix[at], y = matrix[at + 1], z = matrix[at + 2];
            if (column == 3) {
                x -= pivotX;
                y -= pivotY;
                z -= pivotZ;
            }
            matrix[at] = r00 * x + r01 * y + r02 * z;
            matrix[at + 1] = r10 * x + r11 * y + r12 * z;
            matrix[at + 2] = r20 * x + r21 * y + r22 * z;
        }
        matrix[12] += pivotX + moveX;
        matrix[13] += pivotY + moveY;
        matrix[14] += pivotZ + moveZ;
    }

    /** Where a matrix puts a point. */
    static void place(float[] matrix, float x, float y, float z, float[] out) {
        out[0] = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
        out[1] = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
        out[2] = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
    }

    /**
     * The elbow of an arm that runs from a shoulder to a wrist, with its two bones the lengths
     * given: of all the places an elbow could be, the one furthest the way the pole points.
     *
     * An arm is never drawn quite straight, which has no elbow to place, and never folded flat.
     * A wrist further off than that, or nearer, moves the shoulder along the line to the wrist
     * until the arm fits: the bones keep their lengths and the body is taken to lean.
     *
     * @param shoulder where the arm starts, moved if the wrist is out of reach
     * @param pole     the way the elbow drops, of any length
     * @param elbow    the answer
     * @return false if the wrist is at the shoulder or the pole runs along the arm, with
     *         nothing changed
     */
    static boolean elbow(float[] shoulder, float[] wrist, float upper, float fore, float[] pole, float[] elbow) {
        float x = wrist[0] - shoulder[0], y = wrist[1] - shoulder[1], z = wrist[2] - shoulder[2];
        float apart = (float) Math.sqrt(x * x + y * y + z * z);
        if (!(apart > NO_LENGTH)) {
            return false;
        }
        x /= apart;
        y /= apart;
        z /= apart;
        float along = pole[0] * x + pole[1] * y + pole[2] * z;
        float px = pole[0] - along * x, py = pole[1] - along * y, pz = pole[2] - along * z;
        float length = (float) Math.sqrt(px * px + py * py + pz * pz);
        if (!(length > NO_LENGTH)) {
            return false;
        }
        float fits = Math.clamp(apart, Math.abs(upper - fore) + (upper + fore) * (1f - STRAIGHTEST), (upper + fore) * STRAIGHTEST);
        shoulder[0] = wrist[0] - x * fits;
        shoulder[1] = wrist[1] - y * fits;
        shoulder[2] = wrist[2] - z * fits;
        // The elbow is on the circle where a ball of each bone's length round its own end
        // meets the other: this far along the line from the shoulder, and this far off it.
        float foot = (upper * upper - fore * fore + fits * fits) / (2f * fits);
        float off = (float) Math.sqrt(Math.max(upper * upper - foot * foot, 0f)) / length;
        elbow[0] = shoulder[0] + x * foot + px * off;
        elbow[1] = shoulder[1] + y * foot + py * off;
        elbow[2] = shoulder[2] + z * foot + pz * off;
        return true;
    }

    /** How much of its full length an arm is drawn out to at the most. */
    static final float STRAIGHTEST = 0.97f;

    /**
     * How a swing moves what is swung, over and above where it is held: drawn back to the
     * right, swept across the view to the left, and brought back to where it was.
     *
     * @param progress 0 as the swing starts, 1 as it ends
     * @param out      pitch, yaw and roll in radians as `turn` takes them, then how far to the
     *                 right, up and towards the viewer, in tiles
     */
    static void swing(float progress, float[] out) {
        float p = Math.clamp(progress, 0f, 1f);
        float wound, swept;
        if (p < SWING_WOUND) {
            wound = smooth(p / SWING_WOUND);
            swept = 0f;
        } else if (p < SWING_SWEPT) {
            swept = smooth((p - SWING_WOUND) / (SWING_SWEPT - SWING_WOUND));
            wound = 1f - swept;
        } else {
            wound = 0f;
            swept = 1f - smooth((p - SWING_SWEPT) / (1f - SWING_SWEPT));
        }
        for (int i = 0; i < 6; i++) {
            out[i] = WOUND[i] * wound + SWEPT[i] * swept;
        }
    }

    /** The parts of a swing's time at which it is drawn all the way back, and swept all the way across. */
    static final float SWING_WOUND = 0.24f, SWING_SWEPT = 0.56f;
    private static final float[] WOUND = {(float) Math.toRadians(16), (float) Math.toRadians(26), (float) Math.toRadians(-14), 0.05f, 0.05f, 0.05f};
    private static final float[] SWEPT = {(float) Math.toRadians(-24), (float) Math.toRadians(-64), (float) Math.toRadians(50), -0.3f, -0.07f, -0.1f};

    /** 0 to 1 as its argument goes 0 to 1, leaving and arriving without a jerk. */
    static float smooth(float t) {
        float within = Math.clamp(t, 0f, 1f);
        return within * within * (3f - 2f * within);
    }

    /**
     * How far off the straight line between two holds a weapon is while it is moved from one
     * to the other: nought at both ends and 1 half way.
     */
    static float arc(float between) {
        return (float) Math.sin(Math.PI * Math.clamp(between, 0f, 1f));
    }
}
