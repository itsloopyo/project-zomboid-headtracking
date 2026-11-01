package com.cameraunlock.zomboid;

/**
 * How a vehicle lies on the land the first person picture draws under it. The game's map is
 * level and its physics stands every vehicle on it level; the picture raises the land into
 * hills (Terrain), and a vehicle on one is tipped onto the plane through the ground under its
 * four wheels. It is turned the least way that takes straight up to that plane's up, so it is
 * turned about straight up not at all, and whatever the game's physics tips it by (its springs,
 * a crash) is on top of that.
 *
 * A wheel stands on as much ground as it touches, not a point: the picture's kerbs are steps,
 * and a point at each corner had the whole vehicle jump the frame one crossed. And a vehicle
 * someone rides in is carried on a spring at each wheel (Suspension), so a wheel taking a kerb
 * rocks its own end of it rather than snapping the whole of it to the new slope.
 */
final class VehicleSlope {
    /** How far round a wheel's middle the ground under it is read, in tiles: a road wheel's reach on the ground as it rolls onto a step. */
    private static final float WHEEL_REACH = 0.4f;

    /** The turn, three columns of three: where the map's x, y and up go. */
    final float[] turn = new float[9];
    /** The plane's height under the vehicle's middle. */
    float groundUp;
    /** The plane's up, a unit vector. */
    private float upX, upY, upUp = 1f;
    /** The height under each wheel it was laid on: front left, front right, back left, back right. */
    final float[] corners = new float[4];
    /** The way it faces on the map, a unit vector, and how far apart its wheels are along it and across. */
    float facingX = 1f, facingY, length = 1f, width = 1f;

    VehicleSlope() {
        up(0f, 0f, 1f);
    }

    /**
     * Lies a vehicle whose middle is at (x, y) on level z, facing (forwardX, forwardY) along the
     * map, on the ground under its wheels, which are length apart along it and width across.
     */
    void lie(Terrain terrain, float x, float y, float z, float forwardX, float forwardY, float length, float width) {
        float flat = Math.max((float) Math.hypot(forwardX, forwardY), 1e-6f);
        float fx = forwardX / flat, fy = forwardY / flat, rx = fy, ry = -fx;
        float base = z * ViewCamera.LEVEL_HEIGHT, halfL = length / 2f, halfW = width / 2f;
        float frontLeft = base + under(terrain, x + fx * halfL - rx * halfW, y + fy * halfL - ry * halfW, z, fx, fy);
        float frontRight = base + under(terrain, x + fx * halfL + rx * halfW, y + fy * halfL + ry * halfW, z, fx, fy);
        float backLeft = base + under(terrain, x - fx * halfL - rx * halfW, y - fy * halfL - ry * halfW, z, fx, fy);
        float backRight = base + under(terrain, x - fx * halfL + rx * halfW, y - fy * halfL + ry * halfW, z, fx, fy);
        on(frontLeft, frontRight, backLeft, backRight, fx, fy, length, width);
    }

    /** The ground under a wheel at (x, y): the mean of its middle and WHEEL_REACH ahead, behind and to either side of it. */
    private static float under(Terrain terrain, float x, float y, float z, float fx, float fy) {
        float r = WHEEL_REACH;
        return (terrain.lift(x, y, z) + terrain.lift(x + fx * r, y + fy * r, z) + terrain.lift(x - fx * r, y - fy * r, z)
                + terrain.lift(x + fy * r, y - fx * r, z) + terrain.lift(x - fy * r, y + fx * r, z)) / 5f;
    }

    /**
     * The same from the ground's height under each corner, for a vehicle facing (fx, fy), a unit
     * vector along the map; right of it is (fy, -fx).
     */
    void on(float frontLeft, float frontRight, float backLeft, float backRight, float fx, float fy, float length, float width) {
        corners[0] = frontLeft;
        corners[1] = frontRight;
        corners[2] = backLeft;
        corners[3] = backRight;
        facingX = fx;
        facingY = fy;
        this.length = length;
        this.width = width;
        groundUp = (frontLeft + frontRight + backLeft + backRight) / 4f;
        float along = (frontLeft + frontRight - backLeft - backRight) / (2f * length);
        float across = (frontRight + backRight - frontLeft - backLeft) / (2f * width);
        float rx = fy, ry = -fx;
        float riseX = fx * along + rx * across, riseY = fy * along + ry * across;
        float normal = (float) Math.sqrt(riseX * riseX + riseY * riseY + 1f);
        up(-riseX / normal, -riseY / normal, 1f / normal);
    }

    /** Makes this lie as other does. */
    void copy(VehicleSlope other) {
        groundUp = other.groundUp;
        System.arraycopy(other.corners, 0, corners, 0, corners.length);
        facingX = other.facingX;
        facingY = other.facingY;
        length = other.length;
        width = other.width;
        up(other.upX, other.upY, other.upUp);
    }

    /** The sine of how steep the plane is, and the way down it along the map: a unit vector, or none on level ground. */
    float steep() {
        return (float) Math.hypot(upX, upY);
    }

    float downX() {
        float sin = steep();
        return sin < 1e-7f ? 0f : upX / sin;
    }

    float downY() {
        float sin = steep();
        return sin < 1e-7f ? 0f : upY / sin;
    }

    /** Sets the plane's up, a unit vector, and the turn and the way down that follow from it. */
    private void up(float x, float y, float up) {
        upX = x;
        upY = y;
        upUp = up;
        float sin = (float) Math.hypot(x, y), cos = up;
        if (sin < 1e-7f) {
            java.util.Arrays.fill(turn, 0f);
            turn[0] = turn[4] = turn[8] = 1f;
            return;
        }
        // The axis, up crossed with the plane's up, made a unit vector.
        float kx = -y / sin, ky = x / sin, less = 1f - cos;
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

    /** Turns the three floats of v from at, map axes x, y and up, in place. */
    void apply(float[] v, int at) {
        float x = v[at], y = v[at + 1], up = v[at + 2];
        v[at] = turn[0] * x + turn[3] * y + turn[6] * up;
        v[at + 1] = turn[1] * x + turn[4] * y + turn[7] * up;
        v[at + 2] = turn[2] * x + turn[5] * y + turn[8] * up;
    }
}
