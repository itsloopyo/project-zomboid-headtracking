package com.cameraunlock.zomboid;

/**
 * Which side of a vehicle a shot fired from a seat leaves through, seen from above in the
 * vehicle's own frame: x across it and z along it, the body a box of the script's extents
 * round its middle. The glass in that side is what the shot goes through.
 */
final class GlassExit {
    /** The sides: +x, -x, +z, -z, or none where the shot has no way along the ground. */
    static final int NONE = 0, PLUS_X = 1, MINUS_X = 2, PLUS_Z = 3, MINUS_Z = 4;

    private GlassExit() {}

    /**
     * The side a shot from (fromX, fromZ) the way (wayX, wayZ) leaves the box through, and into
     * along[0] where on that side it leaves: z for the sides across, x for the ends. A start
     * outside the box, which a seat at the door can be by a hair, is taken from the nearest
     * place inside it.
     */
    static int leaves(float fromX, float fromZ, float wayX, float wayZ, float halfWide, float halfLong, float[] along) {
        if (Math.abs(wayX) < 1e-6f && Math.abs(wayZ) < 1e-6f) {
            return NONE;
        }
        float x = Math.clamp(fromX, -halfWide, halfWide), z = Math.clamp(fromZ, -halfLong, halfLong);
        float acrossT = wayX > 0f ? (halfWide - x) / wayX : wayX < 0f ? (-halfWide - x) / wayX : Float.POSITIVE_INFINITY;
        float alongT = wayZ > 0f ? (halfLong - z) / wayZ : wayZ < 0f ? (-halfLong - z) / wayZ : Float.POSITIVE_INFINITY;
        if (acrossT <= alongT) {
            along[0] = z + wayZ * acrossT;
            return wayX > 0f ? PLUS_X : MINUS_X;
        }
        along[0] = x + wayX * alongT;
        return wayZ > 0f ? PLUS_Z : MINUS_Z;
    }
}
