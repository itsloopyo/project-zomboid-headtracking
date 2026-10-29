package com.cameraunlock.zomboid;

/**
 * The underside of a canopy: the roof over a filling station's pumps, which the map has as a
 * floor in the air with a band of sprites hung round its edge.
 *
 * Seen from under it, the floor's own sprite is whatever the game painted to be looked down
 * on, a flat sheet with nothing on it. A canopy is a box as deep as the band round it is
 * high, and its underside is lined: pale panels half a pace square with a dark open joint
 * between them, and every third square each way a light let into the lining, a pale lens in
 * a splayed recess. With the power on the lenses are lit, and at night they light what is
 * under them.
 */
final class Soffits {
    /** A panel's side, in tiles, half the width of the joint between two, and how far a joint stands below the panels so as not to lie in their plane. */
    static final float PANEL = 0.5f, JOINT_HALF = 0.008f, JOINT_PROUD = 0.004f;
    /** Half the side of a fitting's opening in the lining and of its lens, and how far up the lens is let in. */
    static final float OPENING_HALF = 0.2f, LENS_HALF = 0.15f, RECESS = 0.04f;
    /** A fitting every this many squares each way. */
    static final int EVERY = 3;
    /** What a lit canopy throws on the ground under it, where that is more than the light already there. */
    static final float GLOW_RED = 0.94f, GLOW_GREEN = 0.92f, GLOW_BLUE = 0.84f;
    /** How dark a joint and the sides of a recess are against the panels. */
    private static final float JOINT_SHADE = 0.45f, RECESS_SHADE = 0.7f;

    private Soffits() {}

    /** Whether the square has a light in its lining: the same squares whichever chunk asks. */
    static boolean fitted(int x, int y) {
        return Math.floorMod(x, EVERY) == 1 && Math.floorMod(y, EVERY) == 1;
    }

    /**
     * Lines the underside of one square of canopy at height up, in the light at the corners of
     * light. panel and lens are what a vertex of each carries for its kind and material: see
     * SpriteGeometry.Mesh.open.
     */
    static void add(SpriteGeometry.Mesh mesh, int x, int y, float up, SpriteGeometry.Frame light, float panel, float lens, boolean fitted, boolean lit) {
        mesh.sway = 0f;
        mesh.face(0f, 0f, -1f);
        mesh.open = panel;
        if (!fitted) {
            flat(mesh, x, y, 0f, 0f, 1f, 1f, up, light, 1f);
        } else {
            float low = 0.5f - OPENING_HALF, high = 0.5f + OPENING_HALF;
            flat(mesh, x, y, 0f, 0f, 1f, low, up, light, 1f);
            flat(mesh, x, y, 0f, high, 1f, 1f, up, light, 1f);
            flat(mesh, x, y, 0f, low, low, high, up, light, 1f);
            flat(mesh, x, y, high, low, 1f, high, up, light, 1f);
            // The splayed sides of the recess, from the opening up and in to the lens.
            float in = 0.5f - LENS_HALF, out = 0.5f + LENS_HALF;
            for (int side = 0; side < 4; side++) {
                boolean alongA = side < 2, far = (side & 1) == 1;
                float edge = far ? high : low, lensEdge = far ? out : in;
                mesh.face(alongA ? 0f : far ? -1f : 1f, alongA ? far ? -1f : 1f : 0f, -1f);
                for (int corner : QUAD) {
                    boolean second = corner == 1 || corner == 2, upper = corner >= 2;
                    float along = upper ? (second ? out : in) : (second ? high : low);
                    float across = upper ? lensEdge : edge;
                    float a = alongA ? along : across, b = alongA ? across : along;
                    vertex(mesh, x, y, a, b, upper ? up + RECESS : up, light, RECESS_SHADE);
                }
            }
            mesh.face(0f, 0f, -1f);
            mesh.open = lens;
            if (lit) {
                mesh.lightStatic();
                for (int corner : QUAD) {
                    boolean second = corner == 1 || corner == 2, upper = corner >= 2;
                    mesh.add(x + (second ? out : in), y + (upper ? out : in), up + RECESS, 0f, 0f, 1f, 1f, 1f);
                }
            } else {
                flat(mesh, x, y, in, in, out, out, up + RECESS, light, 0.9f);
            }
            mesh.open = panel;
        }
        // The joints: two each way on a square, a quarter in from its edges, so that every panel
        // is whole on its square and a fitting sits in the middle one clear of them.
        for (int joint = 0; joint < 4; joint++) {
            float at = (joint & 1) == 0 ? PANEL / 2f : 1f - PANEL / 2f;
            if (joint < 2) {
                flat(mesh, x, y, 0f, at - JOINT_HALF, 1f, at + JOINT_HALF, up - JOINT_PROUD, light, JOINT_SHADE);
            } else {
                flat(mesh, x, y, at - JOINT_HALF, 0f, at + JOINT_HALF, 1f, up - JOINT_PROUD, light, JOINT_SHADE);
            }
        }
    }

    private static final int[] QUAD = {0, 1, 2, 0, 2, 3};

    private static void flat(SpriteGeometry.Mesh mesh, int x, int y, float a0, float b0, float a1, float b1, float up, SpriteGeometry.Frame light, float shade) {
        for (int corner : QUAD) {
            vertex(mesh, x, y, corner == 1 || corner == 2 ? a1 : a0, corner >= 2 ? b1 : b0, up, light, shade);
        }
    }

    private static void vertex(SpriteGeometry.Mesh mesh, int x, int y, float a, float b, float up, SpriteGeometry.Frame light, float shade) {
        float[] red = light.cornerRed, green = light.cornerGreen, blue = light.cornerBlue;
        float w0 = (1f - a) * (1f - b), w1 = a * (1f - b), w2 = a * b, w3 = (1f - a) * b;
        mesh.lightCorners(light, a, b, shade, shade, shade);
        mesh.add(x + a, y + b, up, 0f, 0f, shade * (red[0] * w0 + red[1] * w1 + red[2] * w2 + red[3] * w3),
                shade * (green[0] * w0 + green[1] * w1 + green[2] * w2 + green[3] * w3), shade * (blue[0] * w0 + blue[1] * w1 + blue[2] * w2 + blue[3] * w3));
    }

    /** One colour of the light under a lit canopy: what is there already, or the canopy's own if that is more. */
    static float under(float light, float glow) {
        return Math.max(light, glow);
    }

    /** Half the width of a strip light's body and of its lens, and how far the body hangs below the ceiling, in tiles. */
    static final float STRIP_HALF = 0.15f, STRIP_LENS_HALF = 0.12f, STRIP_DEEP = 0.06f;
    private static final float STRIP_SHADE = 0.78f;

    /**
     * Adds one square's length of a strip light under a ceiling at that height: a shallow
     * body the whole square long, so that each length meets the next, with a pale lens
     * along its underside. alongX is whether it runs along the map's x. The lens carries the
     * room's light, light's flat light: where the game has lit the room the picture makes it glow.
     */
    static void strip(SpriteGeometry.Mesh mesh, int x, int y, float ceiling, boolean alongX, SpriteGeometry.Frame light, float body, float lens) {
        float red = light.red, green = light.green, blue = light.blue;
        mesh.sway = 0f;
        float under = ceiling - STRIP_DEEP;
        mesh.open = body;
        mesh.lightFlat(light, STRIP_SHADE, STRIP_SHADE, STRIP_SHADE);
        for (int side = -1; side <= 1; side += 2) {
            mesh.face(alongX ? 0f : side, alongX ? side : 0f, 0f);
            for (int corner : QUAD) {
                float along = corner == 1 || corner == 2 ? 1f : 0f, up = corner >= 2 ? ceiling : under, across = 0.5f + side * STRIP_HALF;
                mesh.add(x + (alongX ? along : across), y + (alongX ? across : along), up, 0f, 0f, STRIP_SHADE * red, STRIP_SHADE * green, STRIP_SHADE * blue);
            }
        }
        mesh.face(0f, 0f, -1f);
        for (int band = 0; band < 3; band++) {
            float from = band == 0 ? 0.5f - STRIP_HALF : band == 1 ? 0.5f - STRIP_LENS_HALF : 0.5f + STRIP_LENS_HALF;
            float to = band == 0 ? 0.5f - STRIP_LENS_HALF : band == 1 ? 0.5f + STRIP_LENS_HALF : 0.5f + STRIP_HALF;
            float shade = band == 1 ? 1f : STRIP_SHADE;
            mesh.open = band == 1 ? lens : body;
            mesh.lightFlat(light, shade, shade, shade);
            for (int corner : QUAD) {
                float along = corner == 1 || corner == 2 ? 1f : 0f, across = corner >= 2 ? to : from;
                mesh.add(x + (alongX ? along : across), y + (alongX ? across : along), under, 0f, 0f, shade * red, shade * green, shade * blue);
            }
        }
        mesh.open = body;
    }
}
