package com.cameraunlock.zomboid;

/** The plinth and the top course on the outside of a plain wall: see WallBuilder.addCourses. */
final class Courses {
    /** How high the plinth is and how deep the course under the wall's top, in tiles, and how far both stand out from the middle of the wall. */
    static final float PLINTH = 0.2f, COURSE = 0.12f, PROUD = 0.062f;
    /** How the plinth and the course are shaded against the wall. */
    static final float PLINTH_SHADE = 0.78f, COURSE_SHADE = 0.88f;
    /** An opening whose foot is lower than this, in levels, is a doorway. */
    static final float DOORWAY_FOOT = 0.04f;

    private Courses() {}

    /**
     * Adds both along the wall on an edge of square (x, y): the edge runs from the square's
     * corner (x + a, y + b) by (da, db), at height base0 above the ground at its start and base1
     * at its end, and (outX, outY) is the way out of doors from it, a square's side long. Both
     * stand on the edge's own line, proud of it by PROUD: the way out says which side, not how far.
     */
    static void onEdge(SpriteGeometry.Mesh mesh, int x, int y, float a, float b, float da, float db, float base0, float base1, float outX, float outY,
            float doorFrom, float doorTo, float red, float green, float blue) {
        float x0 = x + a, y0 = y + b;
        add(mesh, x0, y0, base0, x0 + da, y0 + db, base1, outX, outY, doorFrom, doorTo, red, green, blue);
    }

    /**
     * Adds both along a wall that runs from (x0, y0) at height base0 to (x1, y1) at base1, on
     * the side (outX, outY) of it, in the wall's colour as lit. doorFrom to doorTo is the
     * share of the way along that a doorway takes, or negative for a wall with none.
     */
    static void add(SpriteGeometry.Mesh mesh, float x0, float y0, float base0, float x1, float y1, float base1, float outX, float outY,
            float doorFrom, float doorTo, float red, float green, float blue) {
        float ox = outX * PROUD, oy = outY * PROUD;
        if (doorFrom < 0f) {
            band(mesh, x0, y0, base0, x1, y1, base1, ox, oy, 0f, PLINTH, PLINTH_SHADE, red, green, blue);
        } else {
            float dx = x1 - x0, dy = y1 - y0, rise = base1 - base0;
            if (doorFrom > 0.02f) {
                band(mesh, x0, y0, base0, x0 + dx * doorFrom, y0 + dy * doorFrom, base0 + rise * doorFrom, ox, oy, 0f, PLINTH, PLINTH_SHADE, red, green, blue);
            }
            if (doorTo < 0.98f) {
                band(mesh, x0 + dx * doorTo, y0 + dy * doorTo, base0 + rise * doorTo, x1, y1, base1, ox, oy, 0f, PLINTH, PLINTH_SHADE, red, green, blue);
            }
        }
        float under = ViewCamera.LEVEL_HEIGHT - COURSE;
        band(mesh, x0, y0, base0, x1, y1, base1, ox, oy, under, ViewCamera.LEVEL_HEIGHT, COURSE_SHADE, red, green, blue);
    }

    /** A face standing proud of the wall from low to high above its foot, with a ledge from its top back to the wall. */
    private static void band(SpriteGeometry.Mesh mesh, float x0, float y0, float base0, float x1, float y1, float base1, float ox, float oy,
            float low, float high, float shade, float red, float green, float blue) {
        float r = red * shade, g = green * shade, b = blue * shade;
        mesh.face(ox, oy, 0f);
        mesh.add(x0 + ox, y0 + oy, base0 + low, 0f, 0f, r, g, b);
        mesh.add(x1 + ox, y1 + oy, base1 + low, 0f, 0f, r, g, b);
        mesh.add(x1 + ox, y1 + oy, base1 + high, 0f, 0f, r, g, b);
        mesh.add(x0 + ox, y0 + oy, base0 + low, 0f, 0f, r, g, b);
        mesh.add(x1 + ox, y1 + oy, base1 + high, 0f, 0f, r, g, b);
        mesh.add(x0 + ox, y0 + oy, base0 + high, 0f, 0f, r, g, b);
        mesh.face(0f, 0f, 1f);
        mesh.add(x0 + ox, y0 + oy, base0 + high, 0f, 0f, r, g, b);
        mesh.add(x1 + ox, y1 + oy, base1 + high, 0f, 0f, r, g, b);
        mesh.add(x1, y1, base1 + high, 0f, 0f, r, g, b);
        mesh.add(x0 + ox, y0 + oy, base0 + high, 0f, 0f, r, g, b);
        mesh.add(x1, y1, base1 + high, 0f, 0f, r, g, b);
        mesh.add(x0, y0, base0 + high, 0f, 0f, r, g, b);
    }
}
