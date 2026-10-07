package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;

/**
 * The noise the clouds are cut from: a cube that repeats in all three
 * directions, three bytes a cell.
 *
 * The first byte is the shape of the clouds: smooth noise over a few scales,
 * pushed towards billows by cellular noise. The second is finer cellular noise
 * that wears the edges of the shapes away. The third is not used. No file is
 * read and nothing is random between runs, so the sky is the same every time
 * for the same game clock.
 */
final class CloudNoise {
    private CloudNoise() {}

    static ByteBuffer build(int size) {
        ByteBuffer out = ByteBuffer.allocateDirect(size * size * size * 3);
        for (int z = 0; z < size; z++) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float u = (x + 0.5f) / size, v = (y + 0.5f) / size, w = (z + 0.5f) / size;
                    float smooth = 0.5f * value(u, v, w, 4) + 0.3f * value(u, v, w, 8) + 0.2f * value(u, v, w, 16);
                    float fine = cells(u, v, w, 8);
                    float billows = 0.6f * cells(u, v, w, 4) + 0.4f * fine;
                    float shape = Math.clamp(smooth * 0.62f + billows * 0.5f, 0f, 1f);
                    float wear = 0.55f * fine + 0.45f * cells(u, v, w, 16);
                    out.put((byte) Math.round(shape * 255f));
                    out.put((byte) Math.round((1f - wear) * 255f));
                    out.put((byte) 0);
                }
            }
        }
        out.flip();
        return out;
    }

    /** A number in [0, 1) for a lattice point, the same at points one period apart. */
    private static float lattice(int x, int y, int z, int period, int salt) {
        int h = Math.floorMod(x, period) * 0x1F1F1F1F ^ Math.floorMod(y, period) * 0x3C6EF372 ^ Math.floorMod(z, period) * 0x7F4A7C15
                ^ period * 0x165667B1 ^ salt * 0x27D4EB2F;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h >>> 8) / (float) (1 << 24);
    }

    /** Smooth noise with the given number of lattice cells across the cube. */
    private static float value(float u, float v, float w, int period) {
        float x = u * period, y = v * period, z = w * period;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        float fx = ease(x - x0), fy = ease(y - y0), fz = ease(z - z0);
        float near = mix(mix(lattice(x0, y0, z0, period, 0), lattice(x0 + 1, y0, z0, period, 0), fx),
                mix(lattice(x0, y0 + 1, z0, period, 0), lattice(x0 + 1, y0 + 1, z0, period, 0), fx), fy);
        float far = mix(mix(lattice(x0, y0, z0 + 1, period, 0), lattice(x0 + 1, y0, z0 + 1, period, 0), fx),
                mix(lattice(x0, y0 + 1, z0 + 1, period, 0), lattice(x0 + 1, y0 + 1, z0 + 1, period, 0), fx), fy);
        return mix(near, far, fz);
    }

    /** Cellular noise: 1 at a cell's own point, falling to 0 a cell's width from the nearest point. */
    private static float cells(float u, float v, float w, int period) {
        float x = u * period, y = v * period, z = w * period;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        float nearest = Float.MAX_VALUE;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int cx = x0 + dx, cy = y0 + dy, cz = z0 + dz;
                    float px = cx + lattice(cx, cy, cz, period, 1) - x;
                    float py = cy + lattice(cx, cy, cz, period, 2) - y;
                    float pz = cz + lattice(cx, cy, cz, period, 3) - z;
                    nearest = Math.min(nearest, px * px + py * py + pz * pz);
                }
            }
        }
        return 1f - Math.min(1f, (float) Math.sqrt(nearest));
    }

    private static float ease(float t) {
        return t * t * (3f - 2f * t);
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * amount;
    }
}
