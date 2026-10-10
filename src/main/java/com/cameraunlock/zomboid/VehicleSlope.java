package com.cameraunlock.zomboid;

/**
 * How a vehicle lies on the land the first person picture draws under it. The game's map is
 * level and its physics stands every vehicle on it level; the picture raises the land into
 * hills (Terrain), and a vehicle on one is tipped onto the plane through the ground under its
 * four corners. It is turned the least way that takes straight up to that plane's up, so it is
 * turned about straight up not at all, and whatever the game's physics tips it by (its springs,
 * a crash) is on top of that.
 */
final class VehicleSlope {
    /** The turn, three columns of three: where the map's x, y and up go. */
    final float[] turn = new float[9];
    /** The plane's height under the vehicle's middle. */
    float groundUp;
    /** The way down the plane along the map, a unit vector, and the sine of how steep the plane is. */
    float downX, downY, steep;

    /**
     * Lies a vehicle whose middle is at (x, y) on level z, facing (forwardX, forwardY) along the
     * map, on the ground under its corners, which are length apart along it and width across.
     */
    void lie(Terrain terrain, float x, float y, float z, float forwardX, float forwardY, float length, float width) {
        float flat = Math.max((float) Math.hypot(forwardX, forwardY), 1e-6f);
        float fx = forwardX / flat, fy = forwardY / flat, rx = fy, ry = -fx;
        float base = z * ViewCamera.LEVEL_HEIGHT, halfL = length / 2f, halfW = width / 2f;
        float frontLeft = base + terrain.lift(x + fx * halfL - rx * halfW, y + fy * halfL - ry * halfW, z);
        float frontRight = base + terrain.lift(x + fx * halfL + rx * halfW, y + fy * halfL + ry * halfW, z);
        float backLeft = base + terrain.lift(x - fx * halfL - rx * halfW, y - fy * halfL - ry * halfW, z);
        float backRight = base + terrain.lift(x - fx * halfL + rx * halfW, y - fy * halfL + ry * halfW, z);
        on(frontLeft, frontRight, backLeft, backRight, fx, fy, length, width);
    }

    /**
     * The same from the ground's height under each corner, for a vehicle facing (fx, fy), a unit
     * vector along the map; right of it is (fy, -fx).
     */
    void on(float frontLeft, float frontRight, float backLeft, float backRight, float fx, float fy, float length, float width) {
        groundUp = (frontLeft + frontRight + backLeft + backRight) / 4f;
        float along = (frontLeft + frontRight - backLeft - backRight) / (2f * length);
        float across = (frontRight + backRight - frontLeft - backLeft) / (2f * width);
        float rx = fy, ry = -fx;
        float riseX = fx * along + rx * across, riseY = fy * along + ry * across;
        float rise = (float) Math.hypot(riseX, riseY);
        float normal = (float) Math.sqrt(rise * rise + 1f);
        // The plane's up, (-riseX, -riseY, 1) made a unit vector: the turn is about the line across
        // the slope, by the angle whose sine is how far the up leans and whose cosine how far it stands.
        float sin = rise / normal, cos = 1f / normal;
        steep = sin;
        if (rise < 1e-7f) {
            downX = downY = 0f;
            identity();
            return;
        }
        downX = -riseX / rise;
        downY = -riseY / rise;
        // The axis, up crossed with the plane's up, made a unit vector: (riseY, -riseX) / rise.
        float kx = riseY / rise, ky = -riseX / rise, less = 1f - cos;
        turn[0] = cos + less * kx * kx;
        turn[1] = less * kx * ky;
        turn[2] = -sin * ky;
        turn[3] = less * kx * ky;
        turn[4] = cos + less * ky * ky;
        turn[5] = sin * kx;
        turn[6] = sin * ky;
        turn[7] = -sin * kx;
        turn[8] = cos;
    }

    private void identity() {
        java.util.Arrays.fill(turn, 0f);
        turn[0] = turn[4] = turn[8] = 1f;
    }

    /** Turns the three floats of v from at, map axes x, y and up, in place. */
    void apply(float[] v, int at) {
        float x = v[at], y = v[at + 1], up = v[at + 2];
        v[at] = turn[0] * x + turn[3] * y + turn[6] * up;
        v[at + 1] = turn[1] * x + turn[4] * y + turn[7] * up;
        v[at + 2] = turn[2] * x + turn[5] * y + turn[8] * up;
    }
}
