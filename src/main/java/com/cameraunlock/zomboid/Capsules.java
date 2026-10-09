package com.cameraunlock.zomboid;

/** Which of a set of capsules a ray is on: the first it enters, or failing any, the nearest it only just misses. */
final class Capsules {
    /** None of them. */
    static final int NONE = -1;

    private Capsules() {}

    private static final float[] approach = new float[3];

    /** How far along the segment at {@code ends[at..at+5]}, 0 to 1, the point {@code distance} along the ray lies. */
    private static float on(ViewCamera.Pose ray, float distance, float[] ends, int at) {
        float sx = ends[at + 3] - ends[at], sy = ends[at + 4] - ends[at + 1], su = ends[at + 5] - ends[at + 2];
        float px = ray.eyeX + ray.forwardX * distance - ends[at], py = ray.eyeY + ray.forwardY * distance - ends[at + 1],
                pu = ray.eyeUp + ray.forwardUp * distance - ends[at + 2];
        float c = sx * sx + sy * sy + su * su;
        return c > 1e-9f ? Math.clamp((px * sx + py * sy + pu * su) / c, 0f, 1f) : 0f;
    }

    /**
     * The index of the capsule the ray enters first, or failing any, of the one it passes nearest
     * within {@code nearMiss} of its surface; {@link #NONE} when it passes none of them.
     *
     * @param ends  each capsule's two ends, x, y and up of each, six floats a capsule
     * @param radii each capsule's radius
     * @param along given back: how far along the capsule's own length, 0 at its first end and 1 at its
     *              second, the ray enters it or passes nearest
     */
    static int first(ViewCamera.Pose ray, float[] ends, float[] radii, int count, float nearMiss, float[] along) {
        int entered = NONE, nearest = NONE;
        float enteredAt = Float.POSITIVE_INFINITY, nearestBy = nearMiss, enteredOn = 0f, nearestOn = 0f;
        for (int i = 0; i < count; i++) {
            closest(ray, ends, i * 6, approach);
            float distance = approach[0], apart = approach[1];
            if (distance <= 0f) {
                continue;
            }
            float r = radii[i];
            if (apart <= r) {
                float entry = distance - (float) Math.sqrt(r * r - apart * apart);
                if (entry < enteredAt) {
                    enteredAt = entry;
                    entered = i;
                    enteredOn = on(ray, entry, ends, i * 6);
                }
            } else if (apart - r < nearestBy) {
                nearestBy = apart - r;
                nearest = i;
                nearestOn = approach[2];
            }
        }
        along[0] = entered != NONE ? enteredOn : nearestOn;
        return entered != NONE ? entered : nearest;
    }

    /**
     * The closest approach of the ray to the segment at {@code ends[at..at+5]}: how far along the
     * ray it is, how far apart they are there, and how far along the segment, 0 to 1.
     */
    static void closest(ViewCamera.Pose ray, float[] ends, int at, float[] out) {
        float dx = ray.forwardX, dy = ray.forwardY, du = ray.forwardUp;
        float sx = ends[at + 3] - ends[at], sy = ends[at + 4] - ends[at + 1], su = ends[at + 5] - ends[at + 2];
        float wx = ray.eyeX - ends[at], wy = ray.eyeY - ends[at + 1], wu = ray.eyeUp - ends[at + 2];
        float a = dx * dx + dy * dy + du * du, b = dx * sx + dy * sy + du * su, c = sx * sx + sy * sy + su * su;
        float d = dx * wx + dy * wy + du * wu, e = sx * wx + sy * wy + su * wu;
        float denominator = a * c - b * b;
        float s = denominator > 1e-9f ? Math.clamp((a * e - b * d) / denominator, 0f, 1f) : 0f;
        // The ray's point nearest that point of the segment, then the segment's nearest that.
        float t = (b * s - d) / a;
        if (c > 1e-9f) {
            s = Math.clamp((b * t + e) / c, 0f, 1f);
            t = (b * s - d) / a;
        }
        float px = wx + dx * t - sx * s, py = wy + dy * t - sy * s, pu = wu + du * t - su * s;
        out[0] = t;
        out[1] = (float) Math.sqrt(px * px + py * py + pu * pu);
        if (out.length > 2) {
            out[2] = s;
        }
    }
}
