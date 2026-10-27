package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;

/**
 * The clouds of the sky's dome (Sky.CLOUDS) as they lie between the eye and the sun, worked out
 * on this side from the same noise: how much of the sun they take, so the sun's light and
 * shafts are dimmed by the cloud the dome draws over the sun.
 *
 * density is the shader's density, step for step, and the noise is read as the graphics card
 * reads its texture of it, filtered between the eight cells round a place and repeating. The
 * march goes through the middle of each of its steps where the shader's are jittered, so the
 * two agree on the cloud and differ by the grain the shader adds.
 */
final class CloudDome {
    private CloudDome() {}

    /** The heights the dome's clouds lie between, and how many steps the shader marches them in: Sky.CLOUDS's BOTTOM, TOP and STEPS. */
    static final float BOTTOM = 1f, TOP = 2.3f;
    static final int STEPS = 48;
    /** Under this height of the sun over the horizon the shader draws no cloud, and so takes none of the sun. */
    static final float LOWEST = 0.03f;

    private static volatile byte[] cells;
    private static int size;

    /** Keeps a copy of the noise the clouds' texture is made from, size cells each way, two bytes a cell: see CloudNoise. */
    static void keep(ByteBuffer noise, int cellsEachWay) {
        byte[] copy = new byte[cellsEachWay * cellsEachWay * cellsEachWay * 2];
        noise.duplicate().get(copy);
        size = cellsEachWay;
        cells = copy;
    }

    /** Whether the noise is kept yet: until it is, the dome draws no cloud. */
    static boolean kept() {
        return cells != null;
    }

    /**
     * How far the dome draws its clouds over a sun sunUp high, 0 to 1: as the shader's fade, none
     * under LOWEST and none until the noise is kept.
     */
    static float shown(float sunUp) {
        return sunUp < LOWEST || cells == null ? 0f : 1f - smooth(9f, 30f, BOTTOM / sunUp);
    }

    /**
     * How much of the sun the dome's clouds take before the fade, 0 to 1, the sun being the way
     * (sunX, sunY, sunUp), a unit vector, under a cover and a deck drifted by (driftX, driftY), as
     * the shader has them. The shader draws it as much as shown says.
     */
    static float taken(float sunX, float sunY, float sunUp, float cover, float deck, float driftX, float driftY) {
        if (shown(sunUp) == 0f) {
            return 0f;
        }
        float near = BOTTOM / sunUp;
        float stride = (TOP - BOTTOM) / sunUp / STEPS;
        float left = 1f;
        for (int i = 0; i < STEPS; i++) {
            float t = near + stride * (i + 0.5f);
            float dense = density(sunX * t, sunY * t, sunUp * t, cover, deck, driftX, driftY);
            if (dense > 0f) {
                left *= (float) Math.exp(-dense * stride * 9f);
                if (left < 0.02f) {
                    break;
                }
            }
        }
        return 1f - left;
    }

    /** Sky.CLOUDS's density at a place p in the dome's measure. */
    static float density(float px, float py, float pz, float cover, float deck, float driftX, float driftY) {
        float h = (pz - BOTTOM) / (TOP - BOTTOM);
        float shape = smooth(0f, 0.08f, h) * (1f - smooth(0.25f, 1f, h));
        float decked = Math.clamp(deck * shape * 0.6f, 0f, 1f);
        float most = shape - (1f - cover);
        if (most <= -0.171f) {
            return decked;
        }
        float qx = px * 0.17f + driftX, qy = py * 0.17f + driftY, qz = pz * 0.21f;
        float bank = noise(px * 0.021f + driftX * 0.4f, py * 0.021f + driftY * 0.4f, 0.37f, 1);
        if (most - (0.5f - bank) * 0.34f <= -0.001f) {
            return decked;
        }
        float pulledX = qx + (noise(qx * 0.9f + 0.31f, qy * 0.9f + 0.31f, qz * 0.9f + 0.31f, 0) - 0.5f) * 0.22f;
        float pulledY = qy + (noise(qx * 0.9f + 0.31f, qy * 0.9f + 0.31f, qz * 0.9f + 0.31f, 1) - 0.5f) * 0.22f;
        float pulledZ = qz + (noise(qx * 0.9f + 0.31f, qy * 0.9f + 0.31f, qz * 0.9f + 0.31f, 0) - 0.5f) * 0.22f;
        float body = noise(pulledX, pulledY, pulledZ, 0) * 0.68f + noise(pulledX * 2.3f + 0.17f, pulledY * 2.3f + 0.17f, pulledZ * 2.3f + 0.17f, 1) * 0.32f;
        float d = body * shape - (1f - cover) - (0.5f - bank) * 0.34f;
        if (d <= 0f) {
            return decked;
        }
        float wear = noise(qx * 3.7f + driftX * 0.3f, qy * 3.7f + driftY * 0.3f, qz * 3.7f, 1) * 0.62f
                + noise(qx * 9.1f + driftX * 0.6f, qy * 9.1f + driftY * 0.6f, qz * 9.1f + 0.4f, 0) * 0.38f;
        d -= wear * 0.34f * (1f - Math.min(d * 3.2f, 1f));
        return Math.max(Math.clamp(d * 7f, 0f, 1f), decked);
    }

    /** One channel of the noise at texture coordinates (u, v, w), filtered and repeating as the shader's texture3D reads it. */
    static float noise(float u, float v, float w, int channel) {
        byte[] at = cells;
        int n = size;
        float x = u * n - 0.5f, y = v * n - 0.5f, z = w * n - 0.5f;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        float fx = x - x0, fy = y - y0, fz = z - z0;
        float sum = 0f;
        for (int corner = 0; corner < 8; corner++) {
            int dx = corner & 1, dy = corner >> 1 & 1, dz = corner >> 2;
            int cx = Math.floorMod(x0 + dx, n), cy = Math.floorMod(y0 + dy, n), cz = Math.floorMod(z0 + dz, n);
            float weight = (dx == 1 ? fx : 1f - fx) * (dy == 1 ? fy : 1f - fy) * (dz == 1 ? fz : 1f - fz);
            sum += weight * (at[((cz * n + cy) * n + cx) * 2 + channel] & 0xff);
        }
        return sum / 255f;
    }

    private static float smooth(float from, float to, float value) {
        float t = Math.clamp((value - from) / (to - from), 0f, 1f);
        return t * t * (3f - 2f * t);
    }
}
