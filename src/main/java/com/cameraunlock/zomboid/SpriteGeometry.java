package com.cameraunlock.zomboid;

/**
 * Turns one of the game's isometric sprites into textured triangles in the
 * world.
 *
 * The game draws a sprite's frame with its top left corner at the screen
 * position of the tile's (x, y) corner minus the object's offset, and its
 * isometric projection puts a point a tiles along x, b tiles along y and h
 * levels up at ((a - b) * 32, (a + b) * 16 - h * 96) pixels from that corner,
 * times the tile scale. So every point of a flat face on the tile has a known
 * texel in the frame, the map between the two is affine, and a face drawn with
 * those texels shows exactly the part of the sprite the game drew over it.
 *
 * A texture holds only the frame's trimmed, non-empty rectangle, so a face is
 * clipped to that rectangle before it is written.
 */
final class SpriteGeometry {
    /** Floats per vertex in a Mesh: x, y, up, u, v, red, green, blue. */
    static final int VERTEX_FLOATS = 8;

    /** Tiles per frame pixel across the screen at tile scale 1: a tile's diagonal is 64 pixels. */
    private static final float TILES_PER_PIXEL_ACROSS = (float) (Math.sqrt(2.0) / 64.0);
    /** Tiles of height per frame pixel at tile scale 1: a level is 96 pixels. */
    private static final float TILES_PER_PIXEL_UP = ViewCamera.LEVEL_HEIGHT / 96f;

    private static final float MIN_FRAME_PIXELS = 0.5f;
    /**
     * A sprite's edge is a staircase of pixels, half of them empty, and two tiles that meet
     * along it would show a dotted gap. Each vertex takes its texel from this far inside the face.
     */
    private static final float EDGE_INSET_PIXELS = 2f;
    private static final float EDGE_SLACK_PIXELS = 2f;
    private static final int MAX_CORNERS = 12;
    private static final int CORNER_FLOATS = 5;

    /** Triangles that share one OpenGL texture. */
    static final class Mesh {
        float[] vertices = new float[VERTEX_FLOATS * 3 * 256];
        int floats;

        void clear() {
            floats = 0;
        }

        int triangles() {
            return floats / (VERTEX_FLOATS * 3);
        }

        private void add(float x, float y, float up, float u, float v, float red, float green, float blue) {
            if (floats + VERTEX_FLOATS > vertices.length) {
                vertices = java.util.Arrays.copyOf(vertices, vertices.length * 2);
            }
            vertices[floats++] = x;
            vertices[floats++] = y;
            vertices[floats++] = up;
            vertices[floats++] = u;
            vertices[floats++] = v;
            vertices[floats++] = red;
            vertices[floats++] = green;
            vertices[floats++] = blue;
        }
    }

    /** Where a sprite's frame sits and which part of which texture holds it. */
    static final class Frame {
        /** Tile scale: 1 for the original 64 pixel tiles, 2 for the doubled ones. */
        float scale;
        /** Frame pixel of the tile's (x, y) corner at its own floor: the object's offset. */
        float originU, originV;
        /** The trimmed rectangle of the frame the texture holds, in frame pixels. */
        float left, top, width, height;
        /** The same rectangle in texture coordinates. */
        float uStart, vStart, uEnd, vEnd;
        float red, green, blue;
    }

    private final float[] corners = new float[MAX_CORNERS * CORNER_FLOATS];
    private final float[] clipped = new float[MAX_CORNERS * CORNER_FLOATS];
    private float centreU, centreV;

    /** Frame pixel across of a point on the tile. */
    static float frameU(Frame frame, float a, float b) {
        return (a - b) * 32f * frame.scale + frame.originU;
    }

    /** Frame pixel down of a point on the tile. */
    static float frameV(Frame frame, float a, float b, float h) {
        return ((a + b) * 16f - h * 96f) * frame.scale + frame.originV;
    }

    /**
     * Adds the parallelogram with one corner at (a, b, h) on the tile and edges
     * (da1, db1, dh1) and (da2, db2, dh2), textured with what the sprite shows
     * there. Returns the number of triangles added, which is 0 when the face
     * misses the trimmed sprite.
     */
    int addFace(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ,
            float a, float b, float h, float da1, float db1, float dh1, float da2, float db2, float dh2) {
        setCorner(frame, 0, a, b, h);
        setCorner(frame, 1, a + da1, b + db1, h + dh1);
        setCorner(frame, 2, a + da1 + da2, b + db1 + db2, h + dh1 + dh2);
        setCorner(frame, 3, a + da2, b + db2, h + dh2);
        centreU = (corners[3] + corners[CORNER_FLOATS * 2 + 3]) / 2f;
        centreV = (corners[4] + corners[CORNER_FLOATS * 2 + 4]) / 2f;

        // A sprite often stops a pixel or two short of the edge of the face it covers. Cutting the
        // face there would leave a slit between two walls, so the last texels are stretched over it.
        float slack = EDGE_SLACK_PIXELS * frame.scale;
        float[] from = corners;
        float[] to = clipped;
        int count = 4;
        for (int edge = 0; edge < 4 && count > 0; edge++) {
            int axis = edge < 2 ? 3 : 4;
            boolean keepAbove = (edge & 1) == 0;
            float limit = switch (edge) {
                case 0 -> frame.left - slack;
                case 1 -> frame.left + frame.width + slack;
                case 2 -> frame.top - slack;
                default -> frame.top + frame.height + slack;
            };
            count = clip(from, count, to, axis, limit, keepAbove);
            float[] swap = from;
            from = to;
            to = swap;
        }
        if (count < 3 || framePixels(from, count) < MIN_FRAME_PIXELS) {
            return 0;
        }
        for (int i = 1; i < count - 1; i++) {
            emit(mesh, frame, tileX, tileY, tileZ, from, 0);
            emit(mesh, frame, tileX, tileY, tileZ, from, i);
            emit(mesh, frame, tileX, tileY, tileZ, from, i + 1);
        }
        return count - 2;
    }

    /**
     * Adds the whole trimmed sprite as an upright rectangle standing on the
     * point (a, b, h) of the tile, the frame pixel the game drew there staying
     * on that point, turned so that (rightX, rightY) runs along its width.
     */
    void addUpright(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ,
            float a, float b, float h, float rightX, float rightY) {
        float anchorU = frameU(frame, a, b);
        float anchorV = frameV(frame, a, b, h);
        float across = TILES_PER_PIXEL_ACROSS / frame.scale;
        float up = TILES_PER_PIXEL_UP / frame.scale;

        float leftOffset = (frame.left - anchorU) * across;
        float rightOffset = (frame.left + frame.width - anchorU) * across;
        float topHeight = (anchorV - frame.top) * up;
        float bottomHeight = (anchorV - frame.top - frame.height) * up;

        float baseX = tileX + a;
        float baseY = tileY + b;
        float baseUp = (tileZ + h) * ViewCamera.LEVEL_HEIGHT;
        float x0 = baseX + rightX * leftOffset, y0 = baseY + rightY * leftOffset;
        float x1 = baseX + rightX * rightOffset, y1 = baseY + rightY * rightOffset;

        mesh.add(x0, y0, baseUp + bottomHeight, frame.uStart, frame.vEnd, frame.red, frame.green, frame.blue);
        mesh.add(x1, y1, baseUp + bottomHeight, frame.uEnd, frame.vEnd, frame.red, frame.green, frame.blue);
        mesh.add(x1, y1, baseUp + topHeight, frame.uEnd, frame.vStart, frame.red, frame.green, frame.blue);
        mesh.add(x0, y0, baseUp + bottomHeight, frame.uStart, frame.vEnd, frame.red, frame.green, frame.blue);
        mesh.add(x1, y1, baseUp + topHeight, frame.uEnd, frame.vStart, frame.red, frame.green, frame.blue);
        mesh.add(x0, y0, baseUp + topHeight, frame.uStart, frame.vStart, frame.red, frame.green, frame.blue);
    }

    private void setCorner(Frame frame, int index, float a, float b, float h) {
        int at = index * CORNER_FLOATS;
        corners[at] = a;
        corners[at + 1] = b;
        corners[at + 2] = h;
        corners[at + 3] = frameU(frame, a, b);
        corners[at + 4] = frameV(frame, a, b, h);
    }

    /** The area of the frame a clipped face covers. A face that only touches the sprite's edge covers none. */
    private static float framePixels(float[] polygon, int count) {
        float twice = 0f;
        for (int i = 0; i < count; i++) {
            int current = i * CORNER_FLOATS;
            int next = ((i + 1) % count) * CORNER_FLOATS;
            twice += polygon[current + 3] * polygon[next + 4] - polygon[next + 3] * polygon[current + 4];
        }
        return Math.abs(twice) / 2f;
    }

    /** Sutherland-Hodgman against one side of the trimmed rectangle. */
    private static int clip(float[] from, int count, float[] to, int axis, float limit, boolean keepAbove) {
        int out = 0;
        for (int i = 0; i < count; i++) {
            int current = i * CORNER_FLOATS;
            int next = ((i + 1) % count) * CORNER_FLOATS;
            float currentValue = from[current + axis];
            float nextValue = from[next + axis];
            boolean currentIn = keepAbove ? currentValue >= limit : currentValue <= limit;
            boolean nextIn = keepAbove ? nextValue >= limit : nextValue <= limit;
            if (currentIn) {
                System.arraycopy(from, current, to, out * CORNER_FLOATS, CORNER_FLOATS);
                out++;
            }
            if (currentIn != nextIn) {
                float t = (limit - currentValue) / (nextValue - currentValue);
                int at = out * CORNER_FLOATS;
                for (int k = 0; k < CORNER_FLOATS; k++) {
                    to[at + k] = from[current + k] + (from[next + k] - from[current + k]) * t;
                }
                out++;
            }
        }
        return out;
    }

    private void emit(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ, float[] polygon, int index) {
        int at = index * CORNER_FLOATS;
        float pixelU = polygon[at + 3];
        float pixelV = polygon[at + 4];
        float toCentreU = centreU - pixelU;
        float toCentreV = centreV - pixelV;
        float distance = (float) Math.sqrt(toCentreU * toCentreU + toCentreV * toCentreV);
        if (distance > EDGE_INSET_PIXELS) {
            pixelU += toCentreU / distance * EDGE_INSET_PIXELS;
            pixelV += toCentreV / distance * EDGE_INSET_PIXELS;
        }
        pixelU = Math.clamp(pixelU, frame.left + 0.5f, frame.left + frame.width - 0.5f);
        pixelV = Math.clamp(pixelV, frame.top + 0.5f, frame.top + frame.height - 0.5f);
        float u = frame.uStart + (pixelU - frame.left) / frame.width * (frame.uEnd - frame.uStart);
        float v = frame.vStart + (pixelV - frame.top) / frame.height * (frame.vEnd - frame.vStart);
        mesh.add(tileX + polygon[at], tileY + polygon[at + 1], (tileZ + polygon[at + 2]) * ViewCamera.LEVEL_HEIGHT,
                u, v, frame.red, frame.green, frame.blue);
    }

    /**
     * Adds an untextured upright rectangle centred on (x, y), from bottom to top above baseUp, with
     * (rightX, rightY) along its width. For things the game draws with a model and has no sprite for.
     */
    static void addPlainUpright(Mesh mesh, float x, float y, float baseUp, float rightX, float rightY,
            float halfWidth, float bottom, float top, float red, float green, float blue) {
        float x0 = x - rightX * halfWidth, y0 = y - rightY * halfWidth;
        float x1 = x + rightX * halfWidth, y1 = y + rightY * halfWidth;
        mesh.add(x0, y0, baseUp + bottom, 0f, 0f, red, green, blue);
        mesh.add(x1, y1, baseUp + bottom, 0f, 0f, red, green, blue);
        mesh.add(x1, y1, baseUp + top, 0f, 0f, red, green, blue);
        mesh.add(x0, y0, baseUp + bottom, 0f, 0f, red, green, blue);
        mesh.add(x1, y1, baseUp + top, 0f, 0f, red, green, blue);
        mesh.add(x0, y0, baseUp + top, 0f, 0f, red, green, blue);
    }

    /** Adds an untextured rectangle lying flat at height up, centred on (x, y). */
    static void addPlainFlat(Mesh mesh, float x, float y, float up, float halfX, float halfY,
            float red, float green, float blue) {
        mesh.add(x - halfX, y - halfY, up, 0f, 0f, red, green, blue);
        mesh.add(x + halfX, y - halfY, up, 0f, 0f, red, green, blue);
        mesh.add(x + halfX, y + halfY, up, 0f, 0f, red, green, blue);
        mesh.add(x - halfX, y - halfY, up, 0f, 0f, red, green, blue);
        mesh.add(x + halfX, y + halfY, up, 0f, 0f, red, green, blue);
        mesh.add(x - halfX, y + halfY, up, 0f, 0f, red, green, blue);
    }
}
