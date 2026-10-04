package com.cameraunlock.zomboid;

import java.util.Arrays;

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
    /**
     * Floats per vertex in a Mesh: x, y, up, u, v, red, green, blue, how open to the sky the
     * vertex's square is, then the unit vector its face looks along.
     */
    static final int VERTEX_FLOATS = 12;
    /** Added to a vertex's openness to mark it as part of an upright card, which is lit from every side. */
    static final float CARD = 2f;
    /** Added to a vertex's openness to mark it as the surface of water. */
    static final float WATER = 4f;
    /** Added to a vertex's openness: a card of grass blades, cut out of it by the picture. */
    static final float GRASS = 8f;
    /** In place of GRASS for long grass, which is grown as far as the eye sees and so does not die away with distance. */
    static final float LONG_GRASS = 16f;
    /** A plant's top moves fully with the wind from this many tiles above its root: 1 over it. */
    private static final float SWAY_PER_TILE = 0.4f;
    /** A sprite whose outline gives a box narrower than twice this, in tiles, or lower than MIN_BOX_HEIGHT levels, is not a box. */
    private static final float MIN_BOX_HALF = 0.1f;
    private static final float MIN_BOX_HEIGHT = 0.04f;

    /** The game's projection at tile scale 1: a tile along x or y is this many frame pixels across and down. */
    private static final float PIXELS_ACROSS_PER_TILE = 32f;
    private static final float PIXELS_DOWN_PER_TILE = 16f;
    /** Frame pixels a level rises by at tile scale 1. */
    private static final float PIXELS_PER_LEVEL = 96f;

    /** Tiles per frame pixel across the screen at tile scale 1: a tile's diagonal is 64 pixels. */
    private static final float TILES_PER_PIXEL_ACROSS = (float) (Math.sqrt(2.0) / (2.0 * PIXELS_ACROSS_PER_TILE));
    /** Tiles of height per frame pixel at tile scale 1. */
    private static final float TILES_PER_PIXEL_UP = ViewCamera.LEVEL_HEIGHT / PIXELS_PER_LEVEL;

    /** A face that covers less of the frame than this, in square pixels, only touches the sprite's edge. */
    private static final float MIN_FRAME_PIXELS = 0.5f;
    /**
     * A sprite's edge is a staircase of pixels, half of them empty, and two tiles that meet
     * along it would show a dotted gap. Each vertex takes its texel from this far inside the
     * face, in frame pixels at tile scale 1.
     */
    private static final float EDGE_INSET_PIXELS = 1.5f;
    private static final float EDGE_SLACK_PIXELS = 2f;
    /** A texel is taken no nearer the trimmed rectangle's edge than the centre of its outermost pixel. */
    private static final float HALF_PIXEL = 0.5f;
    private static final int MAX_CORNERS = 12;
    /** Floats per corner of a face being clipped: a, b, h on the tile, then the frame pixel u, v. */
    private static final int CORNER_FLOATS = 5;
    private static final int CORNER_U = 3;
    private static final int CORNER_V = 4;

    /** Triangles that share one OpenGL texture. */
    static final class Mesh {
        /** The OpenGL name of the texture, set by whoever keeps meshes by texture. */
        int texture;
        /**
         * Starts small and doubles. A scene keeps a mesh for every texture it has ever drawn,
         * and most of them only ever hold a few sprites.
         */
        float[] vertices = new float[VERTEX_FLOATS * 3 * 8];
        int floats;
        /** What the vertices added next carry: 1 out of doors, 0 under a roof. */
        float open;
        /** Which way the face the vertices added next belong to looks. Either side will do: it is one sheet. */
        private float normalX, normalY, normalUp = 1f;
        /**
         * How far the vertices added next move with the wind, 0 for none. It rides on the
         * normal's length, which is 1 plus this: the shader takes the length off before it
         * uses the direction.
         */
        private float sway;

        void face(float x, float y, float up) {
            float length = (float) Math.sqrt(x * x + y * y + up * up);
            normalX = x / length;
            normalY = y / length;
            normalUp = up / length;
        }

        void clear() {
            floats = 0;
        }

        int triangles() {
            return floats / (VERTEX_FLOATS * 3);
        }

        private void add(float x, float y, float up, float u, float v, float red, float green, float blue) {
            if (floats + VERTEX_FLOATS > vertices.length) {
                vertices = Arrays.copyOf(vertices, vertices.length * 2);
            }
            vertices[floats++] = x;
            vertices[floats++] = y;
            vertices[floats++] = up;
            vertices[floats++] = u;
            vertices[floats++] = v;
            vertices[floats++] = red;
            vertices[floats++] = green;
            vertices[floats++] = blue;
            vertices[floats++] = open;
            vertices[floats++] = normalX * (1f + sway);
            vertices[floats++] = normalY * (1f + sway);
            vertices[floats++] = normalUp * (1f + sway);
        }
    }

    /**
     * Meshes by texture name: open addressing with a power of two length, at most half full.
     * A lookup runs for every sprite of every frame, and a map keyed by Integer would box each one.
     */
    static final class MeshSet {
        private Mesh[] byTexture = new Mesh[64];
        final java.util.ArrayList<Mesh> all = new java.util.ArrayList<>();

        Mesh get(int texture) {
            int slot = slotFor(byTexture, texture);
            Mesh mesh = byTexture[slot];
            if (mesh != null) {
                return mesh;
            }
            mesh = new Mesh();
            mesh.texture = texture;
            all.add(mesh);
            if (all.size() * 2 > byTexture.length) {
                byTexture = new Mesh[byTexture.length * 2];
                for (int i = 0; i < all.size(); i++) {
                    Mesh moved = all.get(i);
                    byTexture[slotFor(byTexture, moved.texture)] = moved;
                }
            } else {
                byTexture[slot] = mesh;
            }
            return mesh;
        }

        /** Empties every mesh and keeps them: a scene draws much the same textures every frame. */
        void clear() {
            for (int i = 0; i < all.size(); i++) {
                all.get(i).clear();
            }
        }

        int triangles() {
            int triangles = 0;
            for (int i = 0; i < all.size(); i++) {
                triangles += all.get(i).triangles();
            }
            return triangles;
        }

        /** The slot holding the texture's mesh, or the empty one it belongs in. */
        private static int slotFor(Mesh[] table, int texture) {
            int mask = table.length - 1;
            int slot = texture & mask;
            while (table[slot] != null && table[slot].texture != texture) {
                slot = (slot + 1) & mask;
            }
            return slot;
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
        /** 1 for a square out of doors, 0 for one under a roof. */
        float open;
        /** What the next face is, added to its openness: 0 for an ordinary surface, WATER for water. */
        float kind;
        /** What the next face is made of: 0 nothing in particular, or one of the pictures of materials the picture lays over it. */
        float material;
        /**
         * The light at the square's four corners, (0, 0), (1, 0), (1, 1) then (0, 1). A face
         * takes its light from these by where on the square each of its vertices is, so the
         * light runs smoothly from one square into the next.
         */
        final float[] cornerRed = new float[4], cornerGreen = new float[4], cornerBlue = new float[4];

        /** One light for the whole square. */
        void light(float r, float g, float b) {
            red = r;
            green = g;
            blue = b;
            java.util.Arrays.fill(cornerRed, r);
            java.util.Arrays.fill(cornerGreen, g);
            java.util.Arrays.fill(cornerBlue, b);
        }
    }

    private final float[] corners = new float[MAX_CORNERS * CORNER_FLOATS];
    private final float[] clipped = new float[MAX_CORNERS * CORNER_FLOATS];
    private final float[] spare = new float[MAX_CORNERS * CORNER_FLOATS];
    private float[] result;
    private float centreU, centreV;
    /** How far the face being added stands from where its texels are taken, along a and b: see addBox. */
    float shownA, shownB;
    /**
     * How many frame pixels in from the face's low and high edge across the sprite its texels
     * are taken, beyond the usual inset: see WorldScene.addWall.
     */
    float sealLow, sealHigh;
    /**
     * How far the ground stands above the flat map at the four corners of the square being
     * added, (0, 0), (1, 0), (1, 1) and (0, 1), in tiles: see Terrain. Everything on the square
     * is lifted with it.
     */
    final float[] lift = new float[4];
    /**
     * When set, how much of the face shows at each corner of the square, in the corner order of
     * lift: 1 all of it, 0 none. For ground that spreads over the edge of its neighbour.
     */
    float[] cover;
    /** How far an upright card is moved from where it stands, in tiles: see WorldScene.addOver. */
    float nudgeX, nudgeY;
    /** How much of an upright card is kept, from its foot up: 1 all of it. */
    float keep = 1f;

    private float liftAt(float a, float b) {
        a = Math.clamp(a, 0f, 1f);
        b = Math.clamp(b, 0f, 1f);
        return (lift[0] * (1f - a) + lift[1] * a) * (1f - b) + (lift[3] * (1f - a) + lift[2] * a) * b;
    }

    /** Frame pixel across of a point on the tile. */
    static float frameU(Frame frame, float a, float b) {
        return (a - b) * PIXELS_ACROSS_PER_TILE * frame.scale + frame.originU;
    }

    /** Frame pixel down of a point on the tile. */
    static float frameV(Frame frame, float a, float b, float h) {
        return ((a + b) * PIXELS_DOWN_PER_TILE - h * PIXELS_PER_LEVEL) * frame.scale + frame.originV;
    }

    /** The height, in levels, at which the point (a, b) of the tile is drawn on frame row v. */
    static float heightDrawnAt(Frame frame, float a, float b, float v) {
        return ((a + b) * PIXELS_DOWN_PER_TILE - (v - frame.originV) / frame.scale) / PIXELS_PER_LEVEL;
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
        centreU = (corners[CORNER_U] + corners[CORNER_FLOATS * 2 + CORNER_U]) / 2f;
        centreV = (corners[CORNER_V] + corners[CORNER_FLOATS * 2 + CORNER_V]) / 2f;

        // A face the sprite does not cover adds nothing. One it does cover may find the sprite a
        // pixel or two short of its edge; cutting the face there would leave a slit between two
        // walls, so the face is allowed to run that far past the sprite and takes the last texels.
        int covered = clipToFrame(frame, 0f);
        if (covered < 3 || framePixels(result, covered) < MIN_FRAME_PIXELS) {
            return 0;
        }
        int count = clipToFrame(frame, EDGE_SLACK_PIXELS * frame.scale);
        float up1 = dh1 * ViewCamera.LEVEL_HEIGHT, up2 = dh2 * ViewCamera.LEVEL_HEIGHT;
        mesh.open = frame.open + frame.kind + 32f * frame.material;
        if (up1 == 0f && up2 == 0f) {
            // A face lying on the ground tilts with it, and is lit as tilted.
            float turn = Math.signum(da1 * db2 - db1 * da2);
            mesh.face(-turn * (lift[1] - lift[0] + lift[2] - lift[3]) / 2f, -turn * (lift[3] - lift[0] + lift[2] - lift[1]) / 2f, turn);
        } else {
            mesh.face(db1 * up2 - up1 * db2, up1 * da2 - da1 * up2, da1 * db2 - db1 * da2);
        }
        float[] from = result;
        for (int i = 1; i < count - 1; i++) {
            emit(mesh, frame, tileX, tileY, tileZ, from, 0);
            emit(mesh, frame, tileX, tileY, tileZ, from, i);
            emit(mesh, frame, tileX, tileY, tileZ, from, i + 1);
        }
        return count - 2;
    }

    /**
     * Adds a piece of furniture as the box it fills. The game drew the sprite looking down from
     * the south-east, so its outline says how big the box is: its width is the box's footprint,
     * its lowest pixel the footprint's near corner, its highest the far corner of the top. The
     * top and the two sides the sprite shows are textured with what it shows there, exactly as
     * the game drew them. The two sides it does not show are the mirror of the two it does.
     *
     * box is what boxOf found for the sprite.
     */
    void addBox(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ, float[] box) {
        float a = box[0], b = box[1], half = box[2], height = box[3];
        float side = 2f * half;
        addFace(mesh, frame, tileX, tileY, tileZ, a - half, b - half, height, side, 0f, 0f, 0f, side, 0f);
        addFace(mesh, frame, tileX, tileY, tileZ, a - half, b + half, 0f, side, 0f, 0f, 0f, 0f, height);
        addFace(mesh, frame, tileX, tileY, tileZ, a + half, b - half, 0f, 0f, side, 0f, 0f, 0f, height);
        shownB = -side;
        addFace(mesh, frame, tileX, tileY, tileZ, a - half, b + half, 0f, side, 0f, 0f, 0f, 0f, height);
        shownB = 0f;
        shownA = -side;
        addFace(mesh, frame, tileX, tileY, tileZ, a + half, b - half, 0f, 0f, side, 0f, 0f, 0f, height);
        shownA = 0f;
    }

    /**
     * The box a sprite's outline describes, into box as the middle of its footprint (a, b), half
     * the footprint's side and its height in levels. False for a sprite too thin or too flat to
     * be a box.
     */
    static boolean boxOf(Frame frame, float[] box) {
        float across = PIXELS_ACROSS_PER_TILE * frame.scale, down = PIXELS_DOWN_PER_TILE * frame.scale;
        float half = frame.width / (4f * across);
        float sum = (frame.top + frame.height - frame.originV) / down - 2f * half;
        float difference = (frame.left + frame.width / 2f - frame.originU) / across;
        float height = ((sum - 2f * half) * down + frame.originV - frame.top) / (PIXELS_PER_LEVEL * frame.scale);
        box[0] = (sum + difference) / 2f;
        box[1] = (sum - difference) / 2f;
        box[2] = half;
        box[3] = height;
        return half >= MIN_BOX_HALF && height >= MIN_BOX_HEIGHT;
    }

    /** Clips the face in corners to the trimmed sprite grown by slack, into result. Returns its corner count. */
    private int clipToFrame(Frame frame, float slack) {
        System.arraycopy(corners, 0, clipped, 0, 4 * CORNER_FLOATS);
        float[] from = clipped;
        float[] to = spare;
        int count = 4;
        for (int edge = 0; edge < 4 && count > 0; edge++) {
            int axis = edge < 2 ? CORNER_U : CORNER_V;
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
        result = from;
        return count;
    }

    /**
     * Adds the whole trimmed sprite as an upright rectangle standing on the
     * point (a, b, h) of the tile, the frame pixel the game drew there staying
     * on that point, turned so that (rightX, rightY) runs along its width.
     */
    void addUpright(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ,
            float a, float b, float h, float rightX, float rightY, boolean sways) {
        float anchorU = frameU(frame, a, b);
        float anchorV = frameV(frame, a, b, h);
        float across = TILES_PER_PIXEL_ACROSS / frame.scale;
        float up = TILES_PER_PIXEL_UP / frame.scale;

        float leftOffset = (frame.left - anchorU) * across;
        float rightOffset = (frame.left + frame.width - anchorU) * across;
        float bottomHeight = (anchorV - frame.top - frame.height) * up;
        float topHeight = bottomHeight + frame.height * up * keep;
        float vTop = frame.vEnd + (frame.vStart - frame.vEnd) * keep;

        float baseX = tileX + a + nudgeX;
        float baseY = tileY + b + nudgeY;
        float baseUp = (tileZ + h) * ViewCamera.LEVEL_HEIGHT + liftAt(a, b);
        float x0 = baseX + rightX * leftOffset, y0 = baseY + rightY * leftOffset;
        float x1 = baseX + rightX * rightOffset, y1 = baseY + rightY * rightOffset;

        // A flat card stands in for something round, a bush or a chair, so it is lit as if it
        // leaned back: partly from the side it faces and partly from above.
        mesh.open = frame.open + CARD + 32f * frame.material;
        mesh.face(rightY, -rightX, 0.9f);
        // What grows moves with the wind, the more the higher it stands above its root.
        float low = sways ? Math.clamp(bottomHeight * SWAY_PER_TILE, 0f, 1f) : 0f;
        float high = sways ? Math.clamp(topHeight * SWAY_PER_TILE, 0f, 1f) : 0f;
        mesh.sway = low;
        mesh.add(x0, y0, baseUp + bottomHeight, frame.uStart, frame.vEnd, frame.red, frame.green, frame.blue);
        mesh.add(x1, y1, baseUp + bottomHeight, frame.uEnd, frame.vEnd, frame.red, frame.green, frame.blue);
        mesh.sway = high;
        mesh.add(x1, y1, baseUp + topHeight, frame.uEnd, vTop, frame.red, frame.green, frame.blue);
        mesh.sway = low;
        mesh.add(x0, y0, baseUp + bottomHeight, frame.uStart, frame.vEnd, frame.red, frame.green, frame.blue);
        mesh.sway = high;
        mesh.add(x1, y1, baseUp + topHeight, frame.uEnd, vTop, frame.red, frame.green, frame.blue);
        mesh.add(x0, y0, baseUp + topHeight, frame.uStart, vTop, frame.red, frame.green, frame.blue);
        mesh.sway = 0f;
    }

    /**
     * Adds a tuft of grass standing on the point (a, b) of the ground sprite in frame: an
     * upright card halfWidth either side of the point along (rightX, rightY), the colour of the
     * ground under it. The picture cuts the blades out of the card and bends them in the wind.
     */
    void addTuft(Mesh mesh, Frame frame, int tileX, int tileY, float a, float b, float rightX, float rightY,
            float halfWidth, float height, float kind) {
        float pixelU = Math.clamp(frameU(frame, a, b), frame.left + HALF_PIXEL, frame.left + frame.width - HALF_PIXEL);
        float pixelV = Math.clamp(frameV(frame, a, b, 0f), frame.top + HALF_PIXEL, frame.top + frame.height - HALF_PIXEL);
        float u = frame.uStart + (pixelU - frame.left) / frame.width * (frame.uEnd - frame.uStart);
        float v = frame.vStart + (pixelV - frame.top) / frame.height * (frame.vEnd - frame.vStart);
        float x0 = tileX + a - rightX * halfWidth, y0 = tileY + b - rightY * halfWidth;
        float x1 = tileX + a + rightX * halfWidth, y1 = tileY + b + rightY * halfWidth;
        float base = liftAt(a, b);
        mesh.open = frame.open + kind;
        mesh.face(rightY, -rightX, 0.9f);
        mesh.sway = 0f;
        mesh.add(x0, y0, base, u, v, frame.red, frame.green, frame.blue);
        mesh.add(x1, y1, base, u, v, frame.red, frame.green, frame.blue);
        mesh.sway = 1f;
        mesh.add(x1, y1, base + height, u, v, frame.red, frame.green, frame.blue);
        mesh.sway = 0f;
        mesh.add(x0, y0, base, u, v, frame.red, frame.green, frame.blue);
        mesh.sway = 1f;
        mesh.add(x1, y1, base + height, u, v, frame.red, frame.green, frame.blue);
        mesh.add(x0, y0, base + height, u, v, frame.red, frame.green, frame.blue);
        mesh.sway = 0f;
    }

    /**
     * Adds a block over the whole of the square, high levels high, all of it the colour the
     * sprite has at the point (a, b, h): for a thing whose sprite is a part of something
     * larger and cannot be wrapped onto a box of its own.
     */
    void addBlock(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ, float high, float a, float b, float h) {
        float pixelU = Math.clamp(frameU(frame, a, b), frame.left + HALF_PIXEL, frame.left + frame.width - HALF_PIXEL);
        float pixelV = Math.clamp(frameV(frame, a, b, h), frame.top + HALF_PIXEL, frame.top + frame.height - HALF_PIXEL);
        float u = frame.uStart + (pixelU - frame.left) / frame.width * (frame.uEnd - frame.uStart);
        float v = frame.vStart + (pixelV - frame.top) / frame.height * (frame.vEnd - frame.vStart);
        float foot = tileZ * ViewCamera.LEVEL_HEIGHT + liftAt(0.5f, 0.5f), top = foot + high * ViewCamera.LEVEL_HEIGHT;
        mesh.open = frame.open + 32f * frame.material;
        mesh.sway = 0f;
        float x0 = tileX, y0 = tileY, x1 = tileX + 1f, y1 = tileY + 1f;
        mesh.face(0f, 0f, 1f);
        blockFace(mesh, frame, u, v, x0, y0, top, x1, y0, top, x1, y1, top, x0, y1, top);
        mesh.face(0f, 1f, 0f);
        blockFace(mesh, frame, u, v, x0, y1, foot, x1, y1, foot, x1, y1, top, x0, y1, top);
        mesh.face(0f, -1f, 0f);
        blockFace(mesh, frame, u, v, x0, y0, foot, x1, y0, foot, x1, y0, top, x0, y0, top);
        mesh.face(1f, 0f, 0f);
        blockFace(mesh, frame, u, v, x1, y0, foot, x1, y1, foot, x1, y1, top, x1, y0, top);
        mesh.face(-1f, 0f, 0f);
        blockFace(mesh, frame, u, v, x0, y0, foot, x0, y1, foot, x0, y1, top, x0, y0, top);
    }

    private static void blockFace(Mesh mesh, Frame frame, float u, float v, float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz) {
        mesh.add(ax, ay, az, u, v, frame.red, frame.green, frame.blue);
        mesh.add(bx, by, bz, u, v, frame.red, frame.green, frame.blue);
        mesh.add(cx, cy, cz, u, v, frame.red, frame.green, frame.blue);
        mesh.add(ax, ay, az, u, v, frame.red, frame.green, frame.blue);
        mesh.add(cx, cy, cz, u, v, frame.red, frame.green, frame.blue);
        mesh.add(dx, dy, dz, u, v, frame.red, frame.green, frame.blue);
    }

    private void setCorner(Frame frame, int index, float a, float b, float h) {
        int at = index * CORNER_FLOATS;
        corners[at] = a;
        corners[at + 1] = b;
        corners[at + 2] = h;
        corners[at + CORNER_U] = frameU(frame, a, b);
        corners[at + CORNER_V] = frameV(frame, a, b, h);
    }

    /** The area of the frame a clipped face covers. A face that only touches the sprite's edge covers none. */
    private static float framePixels(float[] polygon, int count) {
        float twice = 0f;
        for (int i = 0; i < count; i++) {
            int current = i * CORNER_FLOATS;
            int next = ((i + 1) % count) * CORNER_FLOATS;
            twice += polygon[current + CORNER_U] * polygon[next + CORNER_V]
                    - polygon[next + CORNER_U] * polygon[current + CORNER_V];
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
        float pixelU = polygon[at + CORNER_U];
        float pixelV = polygon[at + CORNER_V];
        // Across and down separately: a wall's side edges are upright, and an inset towards the
        // face's centre would run along them without leaving the sprite's empty edge columns.
        float inset = EDGE_INSET_PIXELS * frame.scale;
        float insetAcross = inset + (pixelU < centreU ? sealLow : sealHigh);
        pixelU += Math.clamp(centreU - pixelU, -insetAcross, insetAcross);
        pixelV += Math.clamp(centreV - pixelV, -inset, inset);
        pixelU = Math.clamp(pixelU, frame.left + HALF_PIXEL, frame.left + frame.width - HALF_PIXEL);
        pixelV = Math.clamp(pixelV, frame.top + HALF_PIXEL, frame.top + frame.height - HALF_PIXEL);
        float u = frame.uStart + (pixelU - frame.left) / frame.width * (frame.uEnd - frame.uStart);
        float v = frame.vStart + (pixelV - frame.top) / frame.height * (frame.vEnd - frame.vStart);
        float a = Math.clamp(polygon[at], 0f, 1f), b = Math.clamp(polygon[at + 1], 0f, 1f);
        float w0 = (1f - a) * (1f - b), w1 = a * (1f - b), w2 = a * b, w3 = (1f - a) * b;
        mesh.sway = cover == null ? 0f : 1f - (cover[0] * w0 + cover[1] * w1 + cover[2] * w2 + cover[3] * w3);
        mesh.add(tileX + polygon[at] + shownA, tileY + polygon[at + 1] + shownB,
                (tileZ + polygon[at + 2]) * ViewCamera.LEVEL_HEIGHT + liftAt(polygon[at], polygon[at + 1]), u, v,
                frame.cornerRed[0] * w0 + frame.cornerRed[1] * w1 + frame.cornerRed[2] * w2 + frame.cornerRed[3] * w3,
                frame.cornerGreen[0] * w0 + frame.cornerGreen[1] * w1 + frame.cornerGreen[2] * w2 + frame.cornerGreen[3] * w3,
                frame.cornerBlue[0] * w0 + frame.cornerBlue[1] * w1 + frame.cornerBlue[2] * w2 + frame.cornerBlue[3] * w3);
    }

    /** Adds an untextured four-cornered face, its corners given in order round it as x, y and height in tiles. */
    static void addPlainQuad(Mesh mesh, float[] c, float red, float green, float blue) {
        float ux = c[3] - c[0], uy = c[4] - c[1], uz = c[5] - c[2];
        float vx = c[9] - c[0], vy = c[10] - c[1], vz = c[11] - c[2];
        mesh.face(uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx);
        for (int corner : QUAD) {
            mesh.add(c[corner * 3], c[corner * 3 + 1], c[corner * 3 + 2], 0f, 0f, red, green, blue);
        }
    }

    private static final int[] QUAD = {0, 1, 2, 0, 2, 3};

    /**
     * Adds an untextured face over the whole of a square, its corners (0, 0), (1, 0), (1, 1) and
     * (0, 1) at the given heights in tiles.
     */
    static void addPlainOver(Mesh mesh, int x, int y, float h00, float h10, float h11, float h01, float red, float green, float blue) {
        mesh.face(-(h10 - h00 + h11 - h01) / 2f, -(h01 - h00 + h11 - h10) / 2f, 1f);
        mesh.add(x, y, h00, 0f, 0f, red, green, blue);
        mesh.add(x + 1f, y, h10, 0f, 0f, red, green, blue);
        mesh.add(x + 1f, y + 1f, h11, 0f, 0f, red, green, blue);
        mesh.add(x, y, h00, 0f, 0f, red, green, blue);
        mesh.add(x + 1f, y + 1f, h11, 0f, 0f, red, green, blue);
        mesh.add(x, y + 1f, h01, 0f, 0f, red, green, blue);
    }

    /**
     * Adds an untextured upright rectangle centred on (x, y), from bottom to top above baseUp, with
     * (rightX, rightY) along its width. For things the game draws with a model and has no sprite for.
     */
    static void addPlainUpright(Mesh mesh, float x, float y, float baseUp, float rightX, float rightY,
            float halfWidth, float bottom, float top, float red, float green, float blue) {
        float x0 = x - rightX * halfWidth, y0 = y - rightY * halfWidth;
        float x1 = x + rightX * halfWidth, y1 = y + rightY * halfWidth;
        mesh.face(rightY, -rightX, 0.9f);
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
        mesh.face(0f, 0f, 1f);
        mesh.add(x - halfX, y - halfY, up, 0f, 0f, red, green, blue);
        mesh.add(x + halfX, y - halfY, up, 0f, 0f, red, green, blue);
        mesh.add(x + halfX, y + halfY, up, 0f, 0f, red, green, blue);
        mesh.add(x - halfX, y - halfY, up, 0f, 0f, red, green, blue);
        mesh.add(x + halfX, y + halfY, up, 0f, 0f, red, green, blue);
        mesh.add(x - halfX, y + halfY, up, 0f, 0f, red, green, blue);
    }
}
