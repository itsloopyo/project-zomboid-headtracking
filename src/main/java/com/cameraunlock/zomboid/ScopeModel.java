package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * The shape of a rifle scope, turned out of nothing but its measurements: a tube with a bell
 * at each end, the saddle and its two turrets, the rings that hold it and the rail under
 * them. The game's own scopes are a few dozen triangles meant to be seen from a rooftop, and
 * this one is looked along from a hand's breadth away.
 *
 * The axes are those the game's weapon parts are made in: y runs forward along the barrel and
 * z is up. The origin is the point the game fixes a scope to its rifle by, and lengths are
 * metres. Everything turned on a lathe is: the tube about its own axis, each turret about its
 * own.
 *
 * A vertex is twelve floats: its place, the way its surface faces, how rough, how light and
 * how much a metal that surface is, whether it is knurled (1) or graduated (2), and its place
 * round what it was turned on and along it.
 */
final class ScopeModel {
    static final int FLOATS = 12;
    /** The parts are drawn one after another: the body, the eyepiece's glass and the objective's. */
    static final int BODY = 0, EYE_GLASS = 1, FRONT_GLASS = 2, PARTS = 3;

    /** How far over the point it is fixed by the scope's axis runs, and how far under it the rifle's receiver is. */
    static final float AXIS_UP = 0.010f, SEAT = -0.022f;
    /** How far inside its housing each glass sits. */
    static final float EYE_GLASS_IN = 0.004f, FRONT_GLASS_IN = 0.012f;

    private static final float TUBE = 0.0127f, OCULAR = 0.0205f, RUBBER = 0.0218f, LOCK_RING = 0.0214f, SADDLE = 0.0170f, RING = 0.0162f;
    private static final float CHAMFER = 0.0008f;
    private static final float OCULAR_ENDS = 0.062f, TUBE_STARTS = 0.090f;
    private static final float WALL = 0.004f;
    private static final int ROUND = 72, TURRET_ROUND = 48;

    /** A surface: how rough, how light, how much a metal, and whether knurled (1) or graduated (2). */
    private record Finish(float rough, float tone, float metal, float knurl) {}

    private static final Finish ANODISED = new Finish(0.40f, 0.022f, 0f, 0f), EDGE = new Finish(0.24f, 0.045f, 0f, 0f);
    private static final Finish RUBBER_RING = new Finish(0.85f, 0.014f, 0f, 0f), KNURLED = new Finish(0.42f, 0.024f, 0f, 1f);
    private static final Finish CLAMP = new Finish(0.50f, 0.028f, 0f, 0f), STEEL = new Finish(0.34f, 0.07f, 1f, 1f);
    /** Anodising with a line of white paint filled into it, and with a ring of such lines. */
    private static final Finish FILLED = new Finish(0.70f, 0.62f, 0f, 0f), GRADUATED = new Finish(0.40f, 0.022f, 0f, 2f);
    private static final Finish BORE = new Finish(0.95f, 0.006f, 0f, 0f), GLASS = new Finish(0f, 0f, 0f, 0f);

    /** The triangles, three vertices each, and where each part's begin and how many vertices it has. */
    final float[] vertices;
    final int[] first = new int[PARTS], count = new int[PARTS];
    /** Where the middle of the eyepiece's glass is. */
    final float eyeGlassY;

    private float[] made = new float[1 << 16];
    private int floats;
    private Finish finish = ANODISED;
    private int around = ROUND;
    /** What is turned is turned about this line: a point on it, its way, and two ways across it. */
    private float ox, oy, oz, ax, ay, az, ux, uy, uz, vx, vy, vz;

    ScopeModel(ScopeOptics.Glass glass) {
        float length = glass.length(), outer = glass.objective() + WALL;
        float flare = outer > TUBE + 0.006f ? 0.040f : 0.020f, straight = 0.034f;
        float bell = length - flare - straight;
        float turrets = TUBE_STARTS + (bell - TUBE_STARTS) * 0.45f;
        float apart = Math.min(0.050f, Math.min(turrets - TUBE_STARTS, bell - turrets) - 0.010f);
        eyeGlassY = EYE_GLASS_IN;

        about(0f, 0f, AXIS_UP, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
        // The eyepiece: a rubber ring to rest a brow on, ribbed to turn for focus.
        finish = RUBBER_RING;
        face(0f, ScopeOptics.EYE_LENS, RUBBER - CHAMFER, -1f);
        straight(0f, RUBBER - CHAMFER, CHAMFER, RUBBER);
        float rib = CHAMFER;
        for (int ribs = 0; ribs < 4; ribs++) {
            straight(rib, RUBBER, rib + 0.0022f, RUBBER);
            straight(rib + 0.0022f, RUBBER, rib + 0.0027f, RUBBER - 0.0007f);
            straight(rib + 0.0027f, RUBBER - 0.0007f, rib + 0.0032f, RUBBER);
            rib += 0.0032f;
        }
        face(rib, OCULAR, RUBBER, 1f);
        finish = ANODISED;
        straight(rib, OCULAR, 0.0300f, OCULAR);
        finish = FILLED;
        straight(0.0300f, OCULAR, 0.0305f, OCULAR);
        finish = ANODISED;
        straight(0.0305f, OCULAR, 0.046f, OCULAR);
        finish = EDGE;
        straight(0.046f, OCULAR, 0.0468f, LOCK_RING);
        finish = KNURLED;
        straight(0.0468f, LOCK_RING, 0.0552f, LOCK_RING);
        finish = EDGE;
        straight(0.0552f, LOCK_RING, 0.056f, OCULAR);
        finish = ANODISED;
        straight(0.056f, OCULAR, OCULAR_ENDS, OCULAR);
        flared(OCULAR_ENDS, OCULAR, TUBE_STARTS, TUBE);
        // The tube, and the bell that holds the objective.
        straight(TUBE_STARTS, TUBE, bell, TUBE);
        flared(bell, TUBE, bell + flare, outer);
        straight(bell + flare, outer, length - CHAMFER, outer);
        finish = EDGE;
        straight(length - CHAMFER, outer, length, outer - CHAMFER);
        finish = ANODISED;
        face(length, glass.objective(), outer - CHAMFER, 1f);
        // What holds each glass is black inside so that it throws no light onto it.
        finish = BORE;
        bore(0f, EYE_GLASS_IN, ScopeOptics.EYE_LENS);
        bore(length - FRONT_GLASS_IN, length, glass.objective());

        // The saddle the turrets stand on.
        finish = EDGE;
        straight(turrets - 0.017f, TUBE, turrets - 0.016f, SADDLE);
        finish = ANODISED;
        straight(turrets - 0.016f, SADDLE, turrets + 0.016f, SADDLE);
        finish = EDGE;
        straight(turrets + 0.016f, SADDLE, turrets + 0.017f, TUBE);

        // The two rings that clamp the tube, each on a foot, and the rail the feet stand on.
        finish = CLAMP;
        for (float ring : new float[] {turrets - apart, turrets + apart}) {
            face(ring - 0.007f, TUBE, RING - CHAMFER, -1f);
            straight(ring - 0.007f, RING - CHAMFER, ring - 0.007f + CHAMFER, RING);
            straight(ring - 0.007f + CHAMFER, RING, ring + 0.007f - CHAMFER, RING);
            straight(ring + 0.007f - CHAMFER, RING, ring + 0.007f, RING - CHAMFER);
            face(ring + 0.007f, TUBE, RING - CHAMFER, 1f);
            box(-0.009f, ring - 0.007f, SEAT + 0.008f, 0.009f, ring + 0.007f, AXIS_UP - 0.010f);
        }
        box(-0.0105f, turrets - apart - 0.012f, SEAT, 0.0105f, turrets + apart + 0.012f, SEAT + 0.008f);

        // The elevation turret on top and the windage turret on the shooter's right, which is
        // the model's left: the game draws its models mirrored. Then the nut of each foot's bolt.
        around = TURRET_ROUND;
        about(0f, turrets, AXIS_UP, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 0f);
        turret();
        about(0f, turrets, AXIS_UP, -1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);
        turret();
        for (float ring : new float[] {turrets - apart, turrets + apart}) {
            about(0f, ring, SEAT + 0.012f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);
            finish = STEEL;
            straight(0.009f, 0.0042f, 0.0125f, 0.0042f);
            face(0.0125f, 0f, 0.0042f, 1f);
        }
        count[BODY] = floats / FLOATS;

        around = ROUND;
        finish = GLASS;
        about(0f, 0f, AXIS_UP, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
        first[EYE_GLASS] = floats / FLOATS;
        face(EYE_GLASS_IN, 0f, ScopeOptics.EYE_LENS, -1f);
        count[EYE_GLASS] = floats / FLOATS - first[EYE_GLASS];
        first[FRONT_GLASS] = floats / FLOATS;
        face(length - FRONT_GLASS_IN, 0f, glass.objective(), 1f);
        count[FRONT_GLASS] = floats / FLOATS - first[FRONT_GLASS];
        vertices = Arrays.copyOf(made, floats);
        made = null;
    }

    /** A turret, from inside the saddle out to the flat of its cap. */
    private void turret() {
        finish = GRADUATED;
        straight(0.012f, 0.0105f, 0.0215f, 0.0105f);
        finish = ANODISED;
        face(0.0215f, 0.0105f, 0.0118f - CHAMFER, -1f);
        finish = EDGE;
        straight(0.0215f, 0.0118f - CHAMFER, 0.0215f + CHAMFER, 0.0118f);
        finish = KNURLED;
        straight(0.0215f + CHAMFER, 0.0118f, 0.0310f - CHAMFER, 0.0118f);
        finish = EDGE;
        straight(0.0310f - CHAMFER, 0.0118f, 0.0310f, 0.0118f - CHAMFER);
        finish = ANODISED;
        face(0.0310f, 0f, 0.0118f - CHAMFER, 1f);
    }

    private void about(float x, float y, float z, float alongX, float alongY, float alongZ,
            float acrossX, float acrossY, float acrossZ, float otherX, float otherY, float otherZ) {
        ox = x; oy = y; oz = z;
        ax = alongX; ay = alongY; az = alongZ;
        ux = acrossX; uy = acrossY; uz = acrossZ;
        vx = otherX; vy = otherY; vz = otherZ;
    }

    /** A band turned between two rings, straight from one to the other, facing outwards. */
    private void straight(float from, float fromRadius, float to, float toRadius) {
        float along = to - from, out = toRadius - fromRadius;
        float length = (float) Math.sqrt(along * along + out * out);
        band(from, fromRadius, -out / length, along / length, to, toRadius, -out / length, along / length);
    }

    /** A flat ring across the axis, facing along it or back along it. */
    private void face(float at, float inner, float outer, float facing) {
        band(at, inner, facing, 0f, at, outer, facing, 0f);
    }

    /** The inside of a housing, facing the axis. */
    private void bore(float from, float to, float radius) {
        band(from, radius, 0f, -1f, to, radius, 0f, -1f);
    }

    /** A band that leaves one radius and arrives at another without a corner at either end. */
    private void flared(float from, float fromRadius, float to, float toRadius) {
        int steps = 20;
        float lastAt = from, lastRadius = fromRadius, lastAlong = 0f, lastOut = 1f;
        for (int step = 1; step <= steps; step++) {
            float t = (float) step / steps;
            float at = from + (to - from) * t, radius = fromRadius + (toRadius - fromRadius) * t * t * (3f - 2f * t);
            float slope = (toRadius - fromRadius) * 6f * t * (1f - t) / (to - from);
            float length = (float) Math.sqrt(1f + slope * slope);
            band(lastAt, lastRadius, lastAlong, lastOut, at, radius, -slope / length, 1f / length);
            lastAt = at;
            lastRadius = radius;
            lastAlong = -slope / length;
            lastOut = 1f / length;
        }
    }

    /** A band between two rings, each with the way its surface faces: along the axis and out from it. */
    private void band(float from, float fromRadius, float fromAlong, float fromOut, float to, float toRadius, float toAlong, float toOut) {
        float widest = Math.max(fromRadius, toRadius);
        for (int step = 0; step < around; step++) {
            double a = 2 * Math.PI * step / around, b = 2 * Math.PI * (step + 1) / around;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a), cb = (float) Math.cos(b), sb = (float) Math.sin(b);
            float ga = (float) a * widest, gb = (float) b * widest;
            turned(from, fromRadius, fromAlong, fromOut, ca, sa, ga);
            turned(to, toRadius, toAlong, toOut, ca, sa, ga);
            turned(to, toRadius, toAlong, toOut, cb, sb, gb);
            turned(from, fromRadius, fromAlong, fromOut, ca, sa, ga);
            turned(to, toRadius, toAlong, toOut, cb, sb, gb);
            turned(from, fromRadius, fromAlong, fromOut, cb, sb, gb);
        }
    }

    private void turned(float at, float radius, float along, float out, float c, float s, float grip) {
        float rx = ux * c + vx * s, ry = uy * c + vy * s, rz = uz * c + vz * s;
        vertex(ox + ax * at + rx * radius, oy + ay * at + ry * radius, oz + az * at + rz * radius,
                ax * along + rx * out, ay * along + ry * out, az * along + rz * out, grip, at);
    }

    private void box(float x0, float y0, float z0, float x1, float y1, float z1) {
        side(x0, y0, z0, 0f, y1 - y0, 0f, 0f, 0f, z1 - z0, -1f, 0f, 0f);
        side(x1, y0, z0, 0f, y1 - y0, 0f, 0f, 0f, z1 - z0, 1f, 0f, 0f);
        side(x0, y0, z0, x1 - x0, 0f, 0f, 0f, 0f, z1 - z0, 0f, -1f, 0f);
        side(x0, y1, z0, x1 - x0, 0f, 0f, 0f, 0f, z1 - z0, 0f, 1f, 0f);
        side(x0, y0, z0, x1 - x0, 0f, 0f, 0f, y1 - y0, 0f, 0f, 0f, -1f);
        side(x0, y0, z1, x1 - x0, 0f, 0f, 0f, y1 - y0, 0f, 0f, 0f, 1f);
    }

    private void side(float x, float y, float z, float ex, float ey, float ez, float fx, float fy, float fz, float nx, float ny, float nz) {
        vertex(x, y, z, nx, ny, nz, 0f, 0f);
        vertex(x + ex, y + ey, z + ez, nx, ny, nz, 0f, 0f);
        vertex(x + ex + fx, y + ey + fy, z + ez + fz, nx, ny, nz, 0f, 0f);
        vertex(x, y, z, nx, ny, nz, 0f, 0f);
        vertex(x + ex + fx, y + ey + fy, z + ez + fz, nx, ny, nz, 0f, 0f);
        vertex(x + fx, y + fy, z + fz, nx, ny, nz, 0f, 0f);
    }

    private void vertex(float x, float y, float z, float nx, float ny, float nz, float grip, float along) {
        if (floats + FLOATS > made.length) {
            made = Arrays.copyOf(made, made.length * 2);
        }
        made[floats++] = x;
        made[floats++] = y;
        made[floats++] = z;
        made[floats++] = nx;
        made[floats++] = ny;
        made[floats++] = nz;
        made[floats++] = finish.rough;
        made[floats++] = finish.tone;
        made[floats++] = finish.metal;
        made[floats++] = finish.knurl;
        made[floats++] = grip;
        made[floats++] = along;
    }
}
