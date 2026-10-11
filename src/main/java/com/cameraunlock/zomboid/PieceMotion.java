package com.cameraunlock.zomboid;

/**
 * A cut-off piece falling as a rigid body: a rod with rounded ends, its weight at its middle,
 * thrown and spinning, struck by the ground at either end, bouncing a little, dragged by the
 * ground's friction until it stops. A rod comes to lie along the ground because nothing else
 * it can rest on is stable, not because it is turned to lie.
 *
 * Units are the model's, y up, the ground at y 0. The piece's own frame is where it was cut:
 * {@link #turn} and {@link #pivot} take a point of it to where it is now.
 */
final class PieceMotion {
    /** Each step of the motion, in seconds: short enough that an end does not pass through the ground. */
    static final float STEP = 1f / 240f;
    /** How much of its speed into the ground an end keeps out of it, and how hard the ground holds it along. */
    static final float BOUNCE = 0.25f, FRICTION = 0.6f;
    /** The piece is at rest once its middle has stayed within this many units, and its length this close to one way, for {@link #STILL_FOR} seconds. */
    static final float STILL_MOVE = 0.004f, STILL_TURN = 0.9995f, STILL_FOR = 0.3f;
    /** Below this speed into the ground an end does not bounce: a piece lying on the ground would otherwise never stop. */
    static final float BOUNCES_FROM = 0.3f;
    /** How many times a step settles the ends against the ground. */
    static final int CONTACT_PASSES = 8;
    /** How hard a piece is to turn about its length, as a share of across it. */
    static final float NOT_ROUND = 0.5f;
    /** How much of its speed, and of its spin, a piece on the ground loses each second besides friction: rolling, it is not round. */
    static final float GROUND_DRAG = 1.5f, GROUND_SPIN_DRAG = 6f;

    private final float length, radius, gravity;
    /** The direction along the piece from the cut, in its own frame, as cut. */
    private final float ax, ay, az;
    /** The middle, its speed, the turn from the frame as cut as rows, and the spin. */
    final float[] middle = new float[3], speed = new float[3], turn = {1, 0, 0, 0, 1, 0, 0, 0, 1}, spin = new float[3];
    /** The cut as it was in its own frame. */
    private final float cutX, cutY, cutZ;
    private float still;
    /** Where the middle was, and which way the piece ran, as it last began to keep still. */
    private final float[] stillAt = new float[6];
    boolean resting, touched;

    /**
     * @param cutX the joint the piece was cut at, in its own frame
     * @param ax   the unit direction along the piece from the joint
     */
    PieceMotion(float cutX, float cutY, float cutZ, float ax, float ay, float az, float length, float radius, float gravity) {
        this.cutX = cutX;
        this.cutY = cutY;
        this.cutZ = cutZ;
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.length = length;
        this.radius = radius;
        this.gravity = gravity;
        middle[0] = cutX + ax * length / 2f;
        middle[1] = cutY + ay * length / 2f;
        middle[2] = cutZ + az * length / 2f;
    }

    void throwIt(float vx, float vy, float vz, float wx, float wy, float wz) {
        speed[0] = vx;
        speed[1] = vy;
        speed[2] = vz;
        spin[0] = wx;
        spin[1] = wy;
        spin[2] = wz;
    }

    /** Where the joint the piece was cut at is now. */
    void pivot(float[] out) {
        float hx = turn[0] * ax + turn[1] * ay + turn[2] * az, hy = turn[3] * ax + turn[4] * ay + turn[5] * az, hz = turn[6] * ax + turn[7] * ay + turn[8] * az;
        out[0] = middle[0] - hx * length / 2f;
        out[1] = middle[1] - hy * length / 2f;
        out[2] = middle[2] - hz * length / 2f;
    }

    float cutX() {
        return cutX;
    }

    float cutY() {
        return cutY;
    }

    float cutZ() {
        return cutZ;
    }

    /** For a test's failure: how fast it moves and spins. */
    String said() {
        return String.format(java.util.Locale.ROOT, "speed (%.3f, %.3f, %.3f) spin (%.3f, %.3f, %.3f) middle at %.3f", speed[0], speed[1], speed[2], spin[0], spin[1], spin[2], middle[1]);
    }

    /** How far the piece is from lying along the ground, in radians. */
    float tilt() {
        float hy = turn[3] * ax + turn[4] * ay + turn[5] * az;
        return (float) Math.asin(Math.clamp(Math.abs(hy), 0f, 1f));
    }

    /** Moves the piece on by {@code seconds}, in steps of {@link #STEP}. */
    void advance(float seconds) {
        for (float done = 0f; done < seconds && !resting; done += STEP) {
            step(Math.min(STEP, seconds - done));
        }
    }

    private void step(float dt) {
        speed[1] -= gravity * dt;
        for (int i = 0; i < 3; i++) {
            middle[i] += speed[i] * dt;
        }
        rotate(dt);
        // Both ends at once: settled one after the other, the push at the first tips the piece and the second only partly undoes it, which never stops.
        boolean onGround = false;
        for (int pass = 0; pass < CONTACT_PASSES; pass++) {
            for (int end = -1; end <= 1; end += 2) {
                onGround |= contact(end);
            }
        }
        if (onGround) {
            touched = true;
            float keep = Math.max(0f, 1f - GROUND_DRAG * dt), keepSpin = Math.max(0f, 1f - GROUND_SPIN_DRAG * dt);
            for (int i = 0; i < 3; i++) {
                speed[i] *= keep;
                spin[i] *= keepSpin;
            }
            // Still by where it is, not by how fast: lying on two ends, each step's push at one tips it
            // a hair onto the other, a spin that turns about every step and goes nowhere.
            float hx = turn[0] * ax + turn[1] * ay + turn[2] * az, hy = turn[3] * ax + turn[4] * ay + turn[5] * az, hz = turn[6] * ax + turn[7] * ay + turn[8] * az;
            float dx = middle[0] - stillAt[0], dy = middle[1] - stillAt[1], dz = middle[2] - stillAt[2];
            if (still > 0f && dx * dx + dy * dy + dz * dz < STILL_MOVE * STILL_MOVE && hx * stillAt[3] + hy * stillAt[4] + hz * stillAt[5] > STILL_TURN) {
                still += dt;
            } else {
                still = dt;
                stillAt[0] = middle[0];
                stillAt[1] = middle[1];
                stillAt[2] = middle[2];
                stillAt[3] = hx;
                stillAt[4] = hy;
                stillAt[5] = hz;
            }
            resting = still >= STILL_FOR;
        }
    }

    /** Turns the piece by its spin for dt, about its middle. */
    private void rotate(float dt) {
        float wx = spin[0], wy = spin[1], wz = spin[2], w = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
        if (w < 1e-6f) {
            return;
        }
        float angle = w * dt, c = (float) Math.cos(angle), s = (float) Math.sin(angle), k = 1f - c;
        wx /= w;
        wy /= w;
        wz /= w;
        float[] r = {c + wx * wx * k, wx * wy * k - wz * s, wx * wz * k + wy * s,
                wy * wx * k + wz * s, c + wy * wy * k, wy * wz * k - wx * s,
                wz * wx * k - wy * s, wz * wy * k + wx * s, c + wz * wz * k};
        float[] was = turn.clone();
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                turn[row * 3 + column] = r[row * 3] * was[column] + r[row * 3 + 1] * was[3 + column] + r[row * 3 + 2] * was[6 + column];
            }
        }
        orthonormalise();
    }

    private void orthonormalise() {
        float[] t = turn;
        float l0 = (float) Math.sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2]);
        t[0] /= l0;
        t[1] /= l0;
        t[2] /= l0;
        float d = t[0] * t[3] + t[1] * t[4] + t[2] * t[5];
        t[3] -= d * t[0];
        t[4] -= d * t[1];
        t[5] -= d * t[2];
        float l1 = (float) Math.sqrt(t[3] * t[3] + t[4] * t[4] + t[5] * t[5]);
        t[3] /= l1;
        t[4] /= l1;
        t[5] /= l1;
        t[6] = t[1] * t[5] - t[2] * t[4];
        t[7] = t[2] * t[3] - t[0] * t[5];
        t[8] = t[0] * t[4] - t[1] * t[3];
    }

    /**
     * Pushes the end {@code end} (-1 the cut, 1 the far one) out of the ground and gives the
     * piece the push the ground gives it there: up, enough to stop it going in and a share of
     * that back out, and along, as much as friction allows. Says whether it touched.
     */
    private boolean contact(int end) {
        float hx = turn[0] * ax + turn[1] * ay + turn[2] * az, hy = turn[3] * ax + turn[4] * ay + turn[5] * az, hz = turn[6] * ax + turn[7] * ay + turn[8] * az;
        float half = length / 2f * end;
        // From the middle to the ground under this end's round.
        float rx = hx * half, ry = hy * half - radius, rz = hz * half;
        float depth = middle[1] + ry;
        if (depth > 0f) {
            return false;
        }
        middle[1] -= depth;
        // The speed of that point.
        float ux = speed[0] + spin[1] * rz - spin[2] * ry, uy = speed[1] + spin[2] * rx - spin[0] * rz, uz = speed[2] + spin[0] * ry - spin[1] * rx;
        if (uy >= 0f) {
            return true;
        }
        float jn = -(1f + (uy < -BOUNCES_FROM ? BOUNCE : 0f)) * uy / effective(rx, ry, rz, 0f, 1f, 0f, hx, hy, hz);
        push(rx, ry, rz, 0f, jn, 0f, hx, hy, hz);
        float tx = ux, tz = uz, along = (float) Math.sqrt(tx * tx + tz * tz);
        if (along > 1e-6f) {
            tx /= along;
            tz /= along;
            float jt = Math.min(along / effective(rx, ry, rz, tx, 0f, tz, hx, hy, hz), FRICTION * jn);
            push(rx, ry, rz, -tx * jt, 0f, -tz * jt, hx, hy, hz);
        }
        return true;
    }

    /** How much a unit push along n at r changes the speed along n there: the mass and the turn it gives, a rod's. */
    private float effective(float rx, float ry, float rz, float nx, float ny, float nz, float hx, float hy, float hz) {
        float cx = ry * nz - rz * ny, cy = rz * nx - rx * nz, cz = rx * ny - ry * nx;
        float[] i = inverseInertia(cx, cy, cz, hx, hy, hz);
        float qx = i[1] * rz - i[2] * ry, qy = i[2] * rx - i[0] * rz, qz = i[0] * ry - i[1] * rx;
        return 1f + qx * nx + qy * ny + qz * nz;
    }

    private void push(float rx, float ry, float rz, float jx, float jy, float jz, float hx, float hy, float hz) {
        speed[0] += jx;
        speed[1] += jy;
        speed[2] += jz;
        float[] i = inverseInertia(ry * jz - rz * jy, rz * jx - rx * jz, rx * jy - ry * jx, hx, hy, hz);
        spin[0] += i[0];
        spin[1] += i[1];
        spin[2] += i[2];
    }

    /**
     * A rod of unit mass turning across its length. About its length it is held as hard as a
     * share of that: a limb with a hand or a foot on it, or a head, is not round, and a round rod
     * on flat ground rolls for ever.
     */
    private float[] inverseInertia(float x, float y, float z, float hx, float hy, float hz) {
        float acrossInertia = length * length / 12f + radius * radius / 4f, alongInertia = acrossInertia * NOT_ROUND;
        float along = x * hx + y * hy + z * hz;
        float px = hx * along, py = hy * along, pz = hz * along;
        return new float[] {px / alongInertia + (x - px) / acrossInertia, py / alongInertia + (y - py) / acrossInertia, pz / alongInertia + (z - pz) / acrossInertia};
    }
}
