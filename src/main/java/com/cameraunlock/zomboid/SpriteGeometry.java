package com.cameraunlock.zomboid;

import java.util.ArrayList;
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
     * vertex's square is, the vector its face looks along, its length 1 plus how far the vertex
     * moves with the wind, then what Mesh keeps as along and nearer, and 1 for a card that turns
     * to the camera or 2 for one that stands clear of its plane. A strip of addStrip carries
     * other things in the vector's place.
     */
    static final int VERTEX_FLOATS = 15;
    /** Added to a vertex's openness to mark it as part of an upright card, which is lit from every side. */
    static final float CARD = 2f;
    /** Added to a vertex's openness to mark it as the surface of water. */
    static final float WATER = 4f;
    /** Added to a vertex's openness: a card of long grass far from the eye, its blades cut out of it by the picture. */
    static final float LONG_GRASS = 16f;
    /** Added to a vertex's openness: a strip of what Grass grows, which is a blade, a stem or a head of its own shape and not cut out of a card. */
    static final float BLADES = 24f;
    /** A plant's top moves fully with the wind from this many tiles above its root: 1 over it. */
    private static final float SWAY_PER_TILE = 0.4f;
    /** The up share of a tuft's normal where grass barely grows, and how much more it is where it thrives. The world shader has the same two numbers. */
    static final float TUFT_UP_LEAST = 0.3f, TUFT_UP_SPAN = 0.6f;
    /** A sprite whose outline gives a box narrower than twice this, in tiles, or lower than MIN_BOX_HEIGHT levels, is not a box. */
    private static final float MIN_BOX_HALF = 0.1f;
    private static final float MIN_BOX_HEIGHT = 0.04f;

    /** The game's projection at tile scale 1: a tile along x or y is this many frame pixels across and down. */
    static final float PIXELS_ACROSS_PER_TILE = 32f;
    static final float PIXELS_DOWN_PER_TILE = 16f;
    /** Frame pixels a level rises by at tile scale 1. */
    static final float PIXELS_PER_LEVEL = 96f;

    /** Tiles per frame pixel across the screen at tile scale 1: a tile's diagonal is 64 pixels. */
    static final float TILES_PER_PIXEL_ACROSS = (float) (Math.sqrt(2.0) / (2.0 * PIXELS_ACROSS_PER_TILE));
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
        /** The colour its triangles are painted over in, as WallSides packs one, or 0 for the sprite's own. */
        int tint;
        /**
         * Starts small and doubles. A set keeps a mesh for every texture a chunk has lately
         * worn, and most of them only ever hold a few sprites.
         */
        private static final int START_FLOATS = VERTEX_FLOATS * 3 * 8, SHRINK_ABOVE = 1 << 16;
        float[] vertices = new float[START_FLOATS];
        int floats;
        /** Which filling of its set last asked for the mesh, and the most it has held in any filling since the set was last trimmed. */
        private int round, most;
        private boolean asked;
        /** What the vertices added next carry: 1 out of doors, 0 under a roof. */
        float open;
        /** Which way the face the vertices added next belong to looks. Either side will do: it is one sheet. */
        private float normalX, normalY, normalUp = 1f;
        /**
         * How far the vertices added next move with the wind, 0 for none. It rides on the
         * normal's length, which is 1 plus this: the shader takes the length off before it
         * uses the direction.
         */
        float sway;
        /**
         * For a card that turns to face the camera, which the picture does as it draws: how
         * far along the card from its foot the vertices added next are, and that they are of
         * such a card. Their place is then given as the foot's. And for anything, how far
         * towards the camera the vertices added next stand off from where they are given.
         */
        float along, nearer;
        boolean faces;
        /** For a card that does not turn: that along is how far the vertices added next stand clear of the card's own plane, on the side the camera is. */
        boolean clears;

        void face(float x, float y, float up) {
            float length = (float) Math.sqrt(x * x + y * y + up * up);
            normalX = x / length;
            normalY = y / length;
            normalUp = up / length;
        }

        void clear() {
            most = Math.max(most, floats);
            floats = 0;
        }

        int triangles() {
            return floats / (VERTEX_FLOATS * 3);
        }

        void add(float x, float y, float up, float u, float v, float red, float green, float blue) {
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
            vertices[floats++] = along;
            vertices[floats++] = nearer;
            vertices[floats++] = faces ? 1f : clears ? 2f : 0f;
        }
    }

    /**
     * Meshes by texture name: open addressing with a power of two length, at most half full.
     * A lookup runs for every sprite of every chunk built, and a map keyed by Integer would box each one.
     *
     * A set is filled with one chunk's triangles, read, and emptied for the next chunk's. It
     * keeps its meshes between fillings, each with the room its texture last needed, so that
     * filling it makes no garbage.
     */
    static final class MeshSet {
        private Mesh[] byTexture = new Mesh[64];
        private final ArrayList<Mesh> all = new ArrayList<>();
        /** The meshes asked for since the set was last emptied, in the order they were first asked for. */
        final ArrayList<Mesh> used = new ArrayList<>();
        private int round = 1;

        Mesh get(int texture) {
            return get(texture, 0);
        }

        Mesh get(int texture, int tint) {
            int slot = slotFor(byTexture, texture, tint);
            Mesh mesh = byTexture[slot];
            if (mesh == null) {
                mesh = new Mesh();
                mesh.texture = texture;
                mesh.tint = tint;
                all.add(mesh);
                if (all.size() * 2 > byTexture.length) {
                    byTexture = new Mesh[byTexture.length * 2];
                    for (int i = 0; i < all.size(); i++) {
                        Mesh moved = all.get(i);
                        byTexture[slotFor(byTexture, moved.texture, moved.tint)] = moved;
                    }
                } else {
                    byTexture[slot] = mesh;
                }
            }
            if (mesh.round != round) {
                mesh.round = round;
                mesh.asked = true;
                used.add(mesh);
            }
            return mesh;
        }

        /** Empties every mesh and keeps them: one chunk wears much the same textures as the next. */
        void clear() {
            for (int i = 0; i < used.size(); i++) {
                used.get(i).clear();
            }
            used.clear();
            round++;
        }

        /** How many meshes the set keeps, and how many floats they have room for. */
        int kept() {
            return all.size();
        }

        long room() {
            long room = 0;
            for (int i = 0; i < all.size(); i++) {
                room += all.get(i).vertices.length;
            }
            return room;
        }

        /**
         * Lets go of the meshes no filling has asked for since this was last run, and of the
         * room a mesh has that is many times what any filling since then put in it. Run now
         * and then on an emptied set: a set carried across the map would otherwise keep a mesh
         * for every texture on it, and a house's worth of room in each mesh a house once filled.
         */
        void trim() {
            if (!used.isEmpty()) {
                throw new IllegalStateException("a set is trimmed when it is empty, and this one holds " + used.size() + " meshes");
            }
            int kept = 0;
            for (int i = 0; i < all.size(); i++) {
                Mesh mesh = all.get(i);
                if (!mesh.asked) {
                    continue;
                }
                if (mesh.vertices.length > Mesh.SHRINK_ABOVE && mesh.most * 4 < mesh.vertices.length) {
                    mesh.vertices = new float[Math.max(mesh.most * 2, Mesh.START_FLOATS)];
                }
                mesh.asked = false;
                mesh.most = 0;
                all.set(kept++, mesh);
            }
            if (kept == all.size()) {
                return;
            }
            all.subList(kept, all.size()).clear();
            int length = 64;
            while (length < kept * 2) {
                length *= 2;
            }
            byTexture = new Mesh[length];
            for (int i = 0; i < kept; i++) {
                Mesh moved = all.get(i);
                byTexture[slotFor(byTexture, moved.texture, moved.tint)] = moved;
            }
        }

        /** The slot holding the mesh of the texture in that tint, or the empty one it belongs in. */
        private static int slotFor(Mesh[] table, int texture, int tint) {
            int mask = table.length - 1;
            int slot = (texture ^ tint * 0x9E3779B1 >>> 16) & mask;
            while (table[slot] != null && (table[slot].texture != texture || table[slot].tint != tint)) {
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
            Arrays.fill(cornerRed, r);
            Arrays.fill(cornerGreen, g);
            Arrays.fill(cornerBlue, b);
        }
    }

    private final float[] corners = new float[MAX_CORNERS * CORNER_FLOATS];
    private final float[] clipped = new float[MAX_CORNERS * CORNER_FLOATS];
    private final float[] spare = new float[MAX_CORNERS * CORNER_FLOATS];
    private float[] result;
    private float centreU, centreV;
    /** For an upright face, how many frame pixels down its level lines run for each pixel across. */
    private float alongSlant;
    /**
     * For a face that stands straight up, half its width in frame pixels across and half its
     * height in frame pixels down. 0 for any other face.
     */
    private float halfAcross, halfDown;
    /** How far the face being added stands from where its texels are taken, along a and b: see addBox. */
    float shownA, shownB;
    /** The same up, in levels: the underside of a beam shows what its top does. */
    float shownUp;
    /**
     * How many frame pixels in from the face's low and high edge across the sprite its texels
     * are taken, beyond the usual inset: see WorldScene.addPiece.
     */
    float sealLow, sealHigh;
    /** The same down from the top edge of a face that stands straight up. */
    float sealTop;
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
    /** How far towards the camera that draws it the upright added next stands off from its place, in tiles. */
    float nearer;
    /** How far the upright added next stands clear of its own plane on the side of the camera that draws it, in tiles: for one that does not turn. */
    float clear;
    /** How much of an upright card is kept, from its foot up: 1 all of it. */
    float keep = 1f;
    /** How much of an upright card is left out, from its foot up: the shaft of a post that stands there as a solid, under the head the card still shows. */
    float skipped;
    /** When above 0, half the box's footprint along b where it is not square: see addBox. */
    float boxHalfB;
    /**
     * How far above its square's floor the thing being added stands, in levels: what stands on
     * a table is painted as if it stood on the floor, and the game draws it that much higher.
     */
    float raised;
    /**
     * Whether the square has a floor under what stands on it. A sprite shows what is nearer
     * the game's view than the middle of its square lower down the picture, so a card stood on
     * the middle reaches below the floor by that much. On the ground that part is in the
     * earth. Upstairs it hung out of the ceiling of the room below.
     */
    boolean floored;
    /**
     * Whether an upright face being added looks the other way from the one its edges give
     * it. A sheet is lit from whichever side it is seen, so this changes nothing but what the
     * picture can tell from it: which side of a window its room is on.
     */
    boolean turned;

    /**
     * Where the kerbs of the chunk run, as Kerbs keeps it, when one crosses the square being
     * added, and null when none does. The square is then at two heights: lift is its ground
     * on the side it is itself, street or land, and the other side lies a kerb below that or
     * above it.
     */
    float[] kerb;
    /** The square's place in its chunk, for reading kerb. */
    int kerbX, kerbY;
    /** Whether the square a kerb crosses is itself street. */
    boolean kerbStreet;
    /** Which side of the kerb a face laid on the ground is added on: both, the land's alone or the street's alone. */
    int kerbKeep;
    /** The side the corners now being added are on, where that is known and not read from where they are. */
    private int kerbSide;
    static final int BOTH = 0, LAND = 1, STREET = 2;

    /** Whether the point (a, b) of the square is on the street's side of a kerb that crosses it. */
    boolean onStreet(float a, float b) {
        return Kerbs.at(kerb, kerbX + a, kerbY + b) > 0f;
    }

    float liftAt(float a, float b) {
        a = Math.clamp(a, 0f, 1f);
        b = Math.clamp(b, 0f, 1f);
        float up = (lift[0] * (1f - a) + lift[1] * a) * (1f - b) + (lift[3] * (1f - a) + lift[2] * a) * b;
        if (kerb != null) {
            boolean street = kerbSide == BOTH ? onStreet(a, b) : kerbSide == STREET;
            if (street != kerbStreet) {
                up += street ? -Terrain.KERB : Terrain.KERB;
            }
        }
        return up;
    }

    /** Frame pixel across of a point on the tile. */
    static float frameU(Frame frame, float a, float b) {
        return (a - b) * PIXELS_ACROSS_PER_TILE * frame.scale + frame.originU;
    }

    /** Frame pixel down of a point on the tile. */
    static float frameV(Frame frame, float a, float b, float h) {
        return ((a + b) * PIXELS_DOWN_PER_TILE - h * PIXELS_PER_LEVEL) * frame.scale + frame.originV;
    }

    /** Where in its texture the sprite shows the point (a, b) of the tile, across: the nearest texel it has. */
    static float textureU(Frame frame, float a, float b) {
        float pixel = Math.clamp(frameU(frame, a, b), frame.left + HALF_PIXEL, frame.left + frame.width - HALF_PIXEL);
        return frame.uStart + (pixel - frame.left) / frame.width * (frame.uEnd - frame.uStart);
    }

    /** Where in its texture the sprite shows the point (a, b, h) of the tile, down. */
    static float textureV(Frame frame, float a, float b, float h) {
        float pixel = Math.clamp(frameV(frame, a, b, h), frame.top + HALF_PIXEL, frame.top + frame.height - HALF_PIXEL);
        return frame.vStart + (pixel - frame.top) / frame.height * (frame.vEnd - frame.vStart);
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
        float acrossU = corners[CORNER_FLOATS + CORNER_U] - corners[CORNER_U];
        alongSlant = dh1 == 0f && dh2 != 0f && Math.abs(acrossU) > 1e-3f ? (corners[CORNER_FLOATS + CORNER_V] - corners[CORNER_V]) / acrossU : 0f;
        boolean straightUp = alongSlant != 0f && da2 == 0f && db2 == 0f;
        halfAcross = straightUp ? Math.abs(acrossU) / 2f : 0f;
        halfDown = straightUp ? Math.abs(corners[CORNER_FLOATS * 3 + CORNER_V] - corners[CORNER_V]) / 2f : 0f;

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
            float way = turned ? -1f : 1f;
            mesh.face(way * (db1 * up2 - up1 * db2), way * (up1 * da2 - da1 * up2), way * (da1 * db2 - db1 * da2));
        }
        float[] from = result;
        if (kerb != null && tileZ == 0 && up1 == 0f && up2 == 0f && h < LAID_BELOW) {
            int before = mesh.triangles();
            System.arraycopy(from, 0, split[0], 0, count * CORNER_FLOATS);
            split(mesh, frame, tileX, tileY, 0, count, 0, 0, Kerbs.FINE);
            return mesh.triangles() - before;
        }
        for (int i = 1; i < count - 1; i++) {
            emit(mesh, frame, tileX, tileY, tileZ, from, 0);
            emit(mesh, frame, tileX, tileY, tileZ, from, i);
            emit(mesh, frame, tileX, tileY, tileZ, from, i + 1);
        }
        return count - 2;
    }

    /** A face this near the floor, in levels, lies on the ground and steps down a kerb with it. A table's top does not. */
    private static final float LAID_BELOW = 0.05f;
    private static final int SPLIT_CORNERS = 24;
    /** The face being cut along a kerb, at each halving of the square, and the pieces of it. */
    private final float[][] split = new float[7][SPLIT_CORNERS * CORNER_FLOATS];

    /**
     * Adds the part of a face laid on the ground that is in one block of the square, the block
     * given in points of kerb: all of it at one height where the block is all on one side of
     * the kerb, and otherwise by quarters, down to a block between four points, which is cut
     * in two triangles and each of those along the line of the kerb through it.
     */
    private void split(Mesh mesh, Frame frame, int tileX, int tileY, int depth, int count, int p, int q, int size) {
        if (count < 3) {
            return;
        }
        boolean street = false, land = false;
        for (int i = p; i <= p + size; i++) {
            for (int j = q; j <= q + size; j++) {
                float value = kerb[(kerbX * Kerbs.FINE + i) * Kerbs.POINTS + kerbY * Kerbs.FINE + j];
                street |= value > 0f;
                land |= value < 0f;
            }
        }
        float[] face = split[depth];
        if (!street || !land) {
            addSide(mesh, frame, tileX, tileY, face, count, street ? STREET : LAND);
            return;
        }
        float a = (float) p / Kerbs.FINE, b = (float) q / Kerbs.FINE, side = (float) size / Kerbs.FINE;
        if (size > 1) {
            int half = size / 2;
            float[] inner = split[depth + 1];
            for (int quarter = 0; quarter < 4; quarter++) {
                boolean far = (quarter & 1) != 0, down = (quarter & 2) != 0;
                int kept = cut(face, count, split[6], 1f, 0f, -a - side / 2f, far);
                kept = cut(split[6], kept, inner, 0f, 1f, -b - side / 2f, down);
                split(mesh, frame, tileX, tileY, depth + 1, kept, p + (far ? half : 0), q + (down ? half : 0), half);
            }
            return;
        }
        int at = (kerbX * Kerbs.FINE + p) * Kerbs.POINTS + kerbY * Kerbs.FINE + q;
        float here = kerb[at], across = kerb[at + Kerbs.POINTS], opposite = kerb[at + Kerbs.POINTS + 1], along = kerb[at + 1];
        float[] half = split[depth + 1], part = split[depth + 2];
        for (int triangle = 0; triangle < 2; triangle++) {
            // The diagonal of the block from (a, b) parts the two, and in each the field runs evenly.
            int kept = cut(face, count, half, 1f, -1f, b - a, triangle == 0);
            float perA = (triangle == 0 ? across - here : opposite - along) * Kerbs.FINE, perB = (triangle == 0 ? opposite - across : along - here) * Kerbs.FINE;
            float atOrigin = here - perA * a - perB * b;
            float third = triangle == 0 ? across : along;
            if (here > 0f == third > 0f && here > 0f == opposite > 0f) {
                addSide(mesh, frame, tileX, tileY, half, kept, here > 0f ? STREET : LAND);
                continue;
            }
            addSide(mesh, frame, tileX, tileY, part, cut(half, kept, part, perA, perB, atOrigin, true), STREET);
            addSide(mesh, frame, tileX, tileY, part, cut(half, kept, part, perA, perB, atOrigin, false), LAND);
        }
    }

    /** Adds a piece of a face laid on the ground, all of it on one side of the kerb, if that side is kept. */
    private void addSide(Mesh mesh, Frame frame, int tileX, int tileY, float[] piece, int count, int side) {
        if (count < 3 || kerbKeep != BOTH && kerbKeep != side) {
            return;
        }
        float twice = 0f;
        for (int i = 1; i < count - 1; i++) {
            int one = i * CORNER_FLOATS, other = one + CORNER_FLOATS;
            twice += (piece[one] - piece[0]) * (piece[other + 1] - piece[1]) - (piece[one + 1] - piece[1]) * (piece[other] - piece[0]);
        }
        // What a cut leaves along its own line is no piece at all.
        if (Math.abs(twice) < 1e-6f) {
            return;
        }
        kerbSide = side;
        for (int i = 1; i < count - 1; i++) {
            emit(mesh, frame, tileX, tileY, 0, piece, 0);
            emit(mesh, frame, tileX, tileY, 0, piece, i);
            emit(mesh, frame, tileX, tileY, 0, piece, i + 1);
        }
        kerbSide = BOTH;
    }

    /**
     * Sutherland-Hodgman against the line where perA * a + perB * b + atOrigin is nothing,
     * keeping the side where it is above nothing or the side where it is not.
     */
    private static int cut(float[] from, int count, float[] to, float perA, float perB, float atOrigin, boolean keepAbove) {
        int out = 0;
        for (int i = 0; i < count; i++) {
            int current = i * CORNER_FLOATS;
            int next = ((i + 1) % count) * CORNER_FLOATS;
            float currentValue = perA * from[current] + perB * from[current + 1] + atOrigin;
            float nextValue = perA * from[next] + perB * from[next + 1] + atOrigin;
            boolean currentIn = currentValue > 0f == keepAbove;
            boolean nextIn = nextValue > 0f == keepAbove;
            if (currentIn) {
                System.arraycopy(from, current, to, out * CORNER_FLOATS, CORNER_FLOATS);
                out++;
            }
            if (currentIn != nextIn) {
                float t = currentValue / (currentValue - nextValue);
                int at = out * CORNER_FLOATS;
                for (int k = 0; k < CORNER_FLOATS; k++) {
                    to[at + k] = from[current + k] + (from[next + k] - from[current + k]) * t;
                }
                out++;
            }
        }
        return out;
    }

    /**
     * Adds the triangle with its corners at three points of the tile, each (a, b, h) in points,
     * textured with what the sprite shows there: one half of a roof tile that is folded along
     * its diagonal. Returns the number of triangles added, which is 0 when the triangle misses
     * the trimmed sprite.
     */
    int addTriangle(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ, float[] points) {
        // The clip works on four corners: the last is given twice.
        for (int corner = 0; corner < 4; corner++) {
            int from = Math.min(corner, 2) * 3;
            setCorner(frame, corner, points[from], points[from + 1], points[from + 2]);
        }
        centreU = (corners[CORNER_U] + corners[CORNER_FLOATS + CORNER_U] + corners[CORNER_FLOATS * 2 + CORNER_U]) / 3f;
        centreV = (corners[CORNER_V] + corners[CORNER_FLOATS + CORNER_V] + corners[CORNER_FLOATS * 2 + CORNER_V]) / 3f;
        alongSlant = 0f;
        halfAcross = 0f;
        halfDown = 0f;
        int covered = clipToFrame(frame, 0f);
        if (covered < 3 || framePixels(result, covered) < MIN_FRAME_PIXELS) {
            return 0;
        }
        int count = clipToFrame(frame, EDGE_SLACK_PIXELS * frame.scale);
        float da1 = points[3] - points[0], db1 = points[4] - points[1], up1 = (points[5] - points[2]) * ViewCamera.LEVEL_HEIGHT;
        float da2 = points[6] - points[0], db2 = points[7] - points[1], up2 = (points[8] - points[2]) * ViewCamera.LEVEL_HEIGHT;
        // Turned to face up. A triangle that stands upright, in a wall, faces neither up nor
        // down: it keeps the way its corners give it, and is lit from whichever side it is seen.
        float turn = da1 * db2 - db1 * da2 < 0f ? -1f : 1f;
        mesh.open = frame.open + frame.kind + 32f * frame.material;
        mesh.face(turn * (db1 * up2 - up1 * db2), turn * (up1 * da2 - da1 * up2), turn * (da1 * db2 - db1 * da2));
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
        float a = box[0], b = box[1], halfA = box[2], halfB = boxHalfB > 0f ? boxHalfB : box[2], height = box[3];
        float sideA = 2f * halfA, sideB = 2f * halfB;
        addFace(mesh, frame, tileX, tileY, tileZ, a - halfA, b - halfB, height, sideA, 0f, 0f, 0f, sideB, 0f);
        addFace(mesh, frame, tileX, tileY, tileZ, a - halfA, b + halfB, 0f, sideA, 0f, 0f, 0f, 0f, height);
        addFace(mesh, frame, tileX, tileY, tileZ, a + halfA, b - halfB, 0f, 0f, sideB, 0f, 0f, 0f, height);
        shownB = -sideB;
        addFace(mesh, frame, tileX, tileY, tileZ, a - halfA, b + halfB, 0f, sideA, 0f, 0f, 0f, 0f, height);
        shownB = 0f;
        shownA = -sideA;
        addFace(mesh, frame, tileX, tileY, tileZ, a + halfA, b - halfB, 0f, 0f, sideB, 0f, 0f, 0f, height);
        shownA = 0f;
    }

    /**
     * Adds a beam as the box SpriteShape read off its sprite: a slab over a shop front, a sill,
     * a rafter, a pipe. Its top, its south side and its east end are what the sprite shows
     * there. The three the game's view never saw are the same again: the underside is the
     * top, and it is the underside that is seen from the street under an awning.
     */
    void addBeam(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ, SpriteShape beam) {
        float a = beam.a0, b = beam.b0, h = beam.h0, da = beam.a1 - beam.a0, db = beam.b1 - beam.b0, dh = beam.h1 - beam.h0;
        addFace(mesh, frame, tileX, tileY, tileZ, a, b, h + dh, da, 0f, 0f, 0f, db, 0f);
        addFace(mesh, frame, tileX, tileY, tileZ, a, b + db, h, da, 0f, 0f, 0f, 0f, dh);
        addFace(mesh, frame, tileX, tileY, tileZ, a + da, b, h, 0f, db, 0f, 0f, 0f, dh);
        shownUp = -dh;
        addFace(mesh, frame, tileX, tileY, tileZ, a, b, h + dh, da, 0f, 0f, 0f, db, 0f);
        shownUp = 0f;
        shownB = -db;
        addFace(mesh, frame, tileX, tileY, tileZ, a, b + db, h, da, 0f, 0f, 0f, 0f, dh);
        shownB = 0f;
        shownA = -da;
        addFace(mesh, frame, tileX, tileY, tileZ, a + da, b, h, 0f, db, 0f, 0f, 0f, dh);
        shownA = 0f;
    }

    /**
     * Adds a panel where SpriteShape found it standing: a sheet of a billboard, a shop's sign.
     * It is the upright rectangle its sprite paints, on the line of the map's grid it runs along.
     */
    void addPanel(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ, SpriteShape panel) {
        addFace(mesh, frame, tileX, tileY, tileZ, panel.a0, panel.b0, panel.h0, panel.a1 - panel.a0, panel.b1 - panel.b0, 0f, 0f, 0f, panel.h1 - panel.h0);
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

    /**
     * Clips the face in corners to the trimmed sprite, into result, and returns its corner
     * count. Across, the face may run slack past the sprite and takes the sprite's last
     * texels there. Up and down it may do so only where its own edge is within slack of the
     * sprite's: cut slack past the sprite's top or bottom anywhere else, the corners of the
     * cut would take the sprite's last row, and all of the sprite between two such corners
     * would be drawn squeezed towards its middle, by a different amount for every sprite.
     */
    private int clipToFrame(Frame frame, float slack) {
        System.arraycopy(corners, 0, clipped, 0, 4 * CORNER_FLOATS);
        float[] from = clipped;
        float[] to = spare;
        int count = 4;
        for (int edge = 0; edge < 4 && count > 0; edge++) {
            int axis = edge < 2 ? CORNER_U : CORNER_V;
            boolean keepAbove = (edge & 1) == 0;
            float furthest = corners[axis];
            for (int corner = 1; corner < 4; corner++) {
                float value = corners[corner * CORNER_FLOATS + axis];
                furthest = keepAbove ? Math.min(furthest, value) : Math.max(furthest, value);
            }
            float side = switch (edge) {
                case 0 -> frame.left;
                case 1 -> frame.left + frame.width;
                case 2 -> frame.top;
                default -> frame.top + frame.height;
            };
            float limit = axis == CORNER_U ? side + (keepAbove ? -slack : slack) : Math.abs(furthest - side) <= slack ? furthest : side;
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
     * on that point, turned so that (rightX, rightY) runs along its width, or, where faces is
     * set, left for the picture to turn to the camera that draws it.
     */
    void addUpright(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ,
            float a, float b, float h, float rightX, float rightY, boolean faces, boolean sways) {
        float anchorU = frameU(frame, a, b);
        float anchorV = frameV(frame, a, b, h);
        float across = TILES_PER_PIXEL_ACROSS / frame.scale;
        float up = TILES_PER_PIXEL_UP / frame.scale;

        float leftOffset = (frame.left - anchorU) * across;
        float rightOffset = (frame.left + frame.width - anchorU) * across;
        float bottomHeight = (anchorV - frame.top - frame.height) * up;
        float topHeight = bottomHeight + frame.height * up * keep;
        float vTop = frame.vEnd + (frame.vStart - frame.vEnd) * keep;
        float vBottom = frame.vEnd;
        float cut = skipped, lowestKept = bottomHeight + frame.height * up * skipped;
        if (floored) {
            float lowest = -(h + raised) * ViewCamera.LEVEL_HEIGHT;
            cut = Math.max(cut, cutBelow(bottomHeight, bottomHeight + frame.height * up, lowest));
            lowestKept = Math.max(lowestKept, lowest);
        }
        if (cut > 0f) {
            if (cut >= keep) {
                return;
            }
            vBottom = frame.vEnd + (frame.vStart - frame.vEnd) * cut;
            bottomHeight = Math.max(bottomHeight, lowestKept);
        }

        float baseX = tileX + a + nudgeX;
        float baseY = tileY + b + nudgeY;
        float baseUp = (tileZ + h + raised) * ViewCamera.LEVEL_HEIGHT + liftAt(a, b);
        float x0 = faces ? baseX : baseX + rightX * leftOffset, y0 = faces ? baseY : baseY + rightY * leftOffset;
        float x1 = faces ? baseX : baseX + rightX * rightOffset, y1 = faces ? baseY : baseY + rightY * rightOffset;
        float along0 = faces ? leftOffset : clear, along1 = faces ? rightOffset : clear;

        // A flat card stands in for something round, a bush or a chair, so it is lit as if it
        // leaned back: partly from the side it faces and partly from above.
        mesh.open = frame.open + CARD + 32f * frame.material;
        mesh.face(rightY, -rightX, 0.9f);
        mesh.faces = faces;
        mesh.clears = !faces && clear != 0f;
        mesh.nearer = nearer;
        // What grows moves with the wind, the more the higher it stands above its root.
        float low = sways ? Math.clamp(bottomHeight * SWAY_PER_TILE, 0f, 1f) : 0f;
        float high = sways ? Math.clamp(topHeight * SWAY_PER_TILE, 0f, 1f) : 0f;
        mesh.sway = low;
        mesh.along = along0;
        mesh.add(x0, y0, baseUp + bottomHeight, frame.uStart, vBottom, frame.red, frame.green, frame.blue);
        mesh.along = along1;
        mesh.add(x1, y1, baseUp + bottomHeight, frame.uEnd, vBottom, frame.red, frame.green, frame.blue);
        mesh.sway = high;
        mesh.add(x1, y1, baseUp + topHeight, frame.uEnd, vTop, frame.red, frame.green, frame.blue);
        mesh.sway = low;
        mesh.along = along0;
        mesh.add(x0, y0, baseUp + bottomHeight, frame.uStart, vBottom, frame.red, frame.green, frame.blue);
        mesh.sway = high;
        mesh.along = along1;
        mesh.add(x1, y1, baseUp + topHeight, frame.uEnd, vTop, frame.red, frame.green, frame.blue);
        mesh.along = along0;
        mesh.add(x0, y0, baseUp + topHeight, frame.uStart, vTop, frame.red, frame.green, frame.blue);
        mesh.sway = 0f;
        mesh.along = 0f;
        mesh.nearer = 0f;
        mesh.faces = false;
        mesh.clears = false;
    }

    /**
     * How much of a card that stands from bottom to top is below lowest, as a share of its
     * height: 0 for a card that stands clear, 1 or more for one that is all below.
     */
    static float cutBelow(float bottom, float top, float lowest) {
        return bottom >= lowest ? 0f : (lowest - bottom) / (top - bottom);
    }

    /**
     * Adds a tuft of grass standing on the point (a, b) of the ground sprite in frame: an
     * upright card halfWidth either side of the point along (rightX, rightY), the colour of the
     * ground under it. The picture cuts the blades out of the card and bends them in the wind.
     *
     * vigour is how well the grass does where the tuft stands, 1 on open lawn and towards 0 at
     * the edge of a path and under trees. A tuft is lit from above whichever way its card faces,
     * so how far its normal leans up is free to carry it: see upShare.
     */
    void addTuft(Mesh mesh, Frame frame, int tileX, int tileY, float a, float b, float rightX, float rightY,
            float halfWidth, float height, float kind, float vigour) {
        // Nothing grows on the part of a verge's square that a kerb has given to the street.
        if (kerb != null && onStreet(a, b)) {
            return;
        }
        // The colour is taken from inside the square, wherever the tuft stands: past the
        // square's edge the ground's sprite is its empty corner.
        float inA = Math.clamp(a, 0.1f, 0.9f), inB = Math.clamp(b, 0.1f, 0.9f);
        float pixelU = Math.clamp(frameU(frame, inA, inB), frame.left + HALF_PIXEL, frame.left + frame.width - HALF_PIXEL);
        float pixelV = Math.clamp(frameV(frame, inA, inB, 0f), frame.top + HALF_PIXEL, frame.top + frame.height - HALF_PIXEL);
        float u = frame.uStart + (pixelU - frame.left) / frame.width * (frame.uEnd - frame.uStart);
        float v = frame.vStart + (pixelV - frame.top) / frame.height * (frame.vEnd - frame.vStart);
        float x0 = tileX + a - rightX * halfWidth, y0 = tileY + b - rightY * halfWidth;
        float x1 = tileX + a + rightX * halfWidth, y1 = tileY + b + rightY * halfWidth;
        float base = liftAt(a, b);
        mesh.open = frame.open + kind;
        float share = upShare(vigour);
        mesh.face(rightY, -rightX, share / (float) Math.sqrt(1f - share * share));
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

    /** The outline of a strip: see addStrip. */
    static final int POINTED = 0, EVEN = 1, FAN = 2;
    /** How wide a fan is at its foot, as a share of its width at its end. The world shader has the same number. */
    static final float FAN_FOOT = 0.3f;
    /** Where the plant being grown stands on the map, and where on the ground's sprite it takes its colour. */
    private float plantX, plantY, plantUp, plantU, plantV;
    /** Where the strip added last ended, along its own way and up from the plant's root, and how far over it leant there: a head starts where its stem stops. */
    float endAlong, endUp, endLean;

    /** Stands the plant whose strips are added next on the point (a, b) of the ground sprite in frame. */
    void plant(Frame frame, int tileX, int tileY, float a, float b) {
        // The colour is taken from inside the square, wherever the plant stands: past the
        // square's edge the ground's sprite is its empty corner.
        float inA = Math.clamp(a, 0.1f, 0.9f), inB = Math.clamp(b, 0.1f, 0.9f);
        float pixelU = Math.clamp(frameU(frame, inA, inB), frame.left + HALF_PIXEL, frame.left + frame.width - HALF_PIXEL);
        float pixelV = Math.clamp(frameV(frame, inA, inB, 0f), frame.top + HALF_PIXEL, frame.top + frame.height - HALF_PIXEL);
        plantU = frame.uStart + (pixelU - frame.left) / frame.width * (frame.uEnd - frame.uStart);
        plantV = frame.vStart + (pixelV - frame.top) / frame.height * (frame.vEnd - frame.vStart);
        plantX = tileX + a;
        plantY = tileY + b;
        plantUp = liftAt(a, b);
    }

    /**
     * Adds one strip of the plant last stood: a blade, a stem, or the card a head is cut from.
     * It starts fromAlong out from the root along (dirX, dirY) and fromUp above it, leaning
     * lean from upright, and has bent over by curl more at its end, length further on. It
     * turns about its own length by twist from foot to tip. A POINTED strip narrows to
     * nothing, an EVEN one is as wide at its end as at its foot, and a FAN is FAN_FOOT of its
     * width at its foot and all of it at its end.
     *
     * Its vertices carry no facing. Where one would be they carry what the picture needs to
     * shape and move the strip: which edge the vertex is on (-2 and 2, 0 at a point) with the
     * strip's own random number added; twice what the strip is, with how far along it the
     * vertex is added; and how well the grass does there in sixteenths, with the vertex's
     * height over the root added, which is how far the wind moves it.
     */
    void addStrip(Mesh mesh, Frame frame, float dirX, float dirY, float fromAlong, float fromUp, float lean, float curl, float length, float width,
            float twist, int steps, int shape, int form, float seed, float vigour) {
        float half = 0.5f * length;
        float midAlong = fromAlong + half * Grass.sin(lean), midUp = fromUp + half * Grass.cos(lean);
        float tipAlong = midAlong + half * Grass.sin(lean + curl), tipUp = Math.max(midUp + half * Grass.cos(lean + curl), 0.004f);
        endAlong = tipAlong;
        endUp = tipUp;
        endLean = lean + curl;
        float level = (float) Math.floor(Math.clamp(vigour, 0f, 1f) * 15.99f);
        seed = Math.min(seed, 0.999f);
        // Written straight into the mesh: a lawn is a hundred thousand of these corners a frame.
        int need = mesh.floats + steps * 6 * VERTEX_FLOATS;
        if (need > mesh.vertices.length) {
            mesh.vertices = Arrays.copyOf(mesh.vertices, Math.max(mesh.vertices.length * 2, need));
        }
        float[] to = mesh.vertices;
        int at = mesh.floats;
        float open = frame.open + BLADES;
        float red = frame.red, green = frame.green, blue = frame.blue;
        float leftEdge = -2f + seed, rightEdge = 2f + seed;
        float lastLeftX = 0f, lastLeftY = 0f, lastRightX = 0f, lastRightY = 0f, lastUp = 0f, lastCode = 0f, lastHigh = 0f;
        for (int step = 0; step <= steps; step++) {
            float s = step / (float) steps;
            // More of the strip's corners towards its tip, where it bends most.
            float t = s * (1.28f - 0.28f * s), k = 1f - t;
            float along = k * k * fromAlong + 2f * t * k * midAlong + t * t * tipAlong;
            float up = k * k * fromUp + 2f * t * k * midUp + t * t * tipUp;
            float halfWidth = 0.5f * width * (shape == POINTED ? 1f - t * t : shape == FAN ? FAN_FOOT + (1f - FAN_FOOT) * t : 1f);
            float turned = twist * t;
            float sideX = (-dirY + dirX * turned) * halfWidth, sideY = (dirX + dirY * turned) * halfWidth;
            float midX = plantX + dirX * along, midY = plantY + dirY * along;
            float leftX = midX - sideX, leftY = midY - sideY, rightX = midX + sideX, rightY = midY + sideY;
            float code = form * 2f + t, high = level + Math.min(up, 0.999f);
            if (step > 0) {
                boolean point = shape == POINTED && step == steps;
                at = corner(to, at, lastLeftX, lastLeftY, plantUp + lastUp, red, green, blue, open, leftEdge, lastCode, lastHigh);
                at = corner(to, at, lastRightX, lastRightY, plantUp + lastUp, red, green, blue, open, rightEdge, lastCode, lastHigh);
                at = corner(to, at, rightX, rightY, plantUp + up, red, green, blue, open, point ? seed : rightEdge, code, high);
                if (!point) {
                    at = corner(to, at, lastLeftX, lastLeftY, plantUp + lastUp, red, green, blue, open, leftEdge, lastCode, lastHigh);
                    at = corner(to, at, rightX, rightY, plantUp + up, red, green, blue, open, rightEdge, code, high);
                    at = corner(to, at, leftX, leftY, plantUp + up, red, green, blue, open, leftEdge, code, high);
                }
            }
            lastLeftX = leftX;
            lastLeftY = leftY;
            lastRightX = rightX;
            lastRightY = rightY;
            lastUp = up;
            lastCode = code;
            lastHigh = high;
        }
        mesh.floats = at;
    }

    private int corner(float[] to, int at, float x, float y, float up, float red, float green, float blue, float open, float edge, float code,
            float high) {
        to[at] = x;
        to[at + 1] = y;
        to[at + 2] = up;
        to[at + 3] = plantU;
        to[at + 4] = plantV;
        to[at + 5] = red;
        to[at + 6] = green;
        to[at + 7] = blue;
        to[at + 8] = open;
        to[at + 9] = edge;
        to[at + 10] = code;
        to[at + 11] = high;
        to[at + 12] = 0f;
        to[at + 13] = 0f;
        to[at + 14] = 0f;
        return at + VERTEX_FLOATS;
    }

    /** The part of a tuft's unit normal that points up, for grass doing as well as vigour says: the picture reads vigour back off it. */
    static float upShare(float vigour) {
        return TUFT_UP_LEAST + TUFT_UP_SPAN * Math.clamp(vigour, 0f, 1f);
    }

    /**
     * Adds a block over the whole of the square, high levels high, all of it the colour the
     * sprite has at the point (a, b, h): for a thing whose sprite is a part of something
     * larger and cannot be wrapped onto a box of its own.
     */
    void addBlock(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ, float high, float a, float b, float h) {
        float u = textureU(frame, a, b), v = textureV(frame, a, b, h);
        float foot = (tileZ + raised) * ViewCamera.LEVEL_HEIGHT + liftAt(0.5f, 0.5f), top = foot + high * ViewCamera.LEVEL_HEIGHT;
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

    /** How far in from a post's outline its texels are taken, as a share of its thickness: the outline itself is the sprite's dark line. */
    private static final float POST_TEXELS_IN = 0.86f;
    private static final float DIAGONAL = (float) (Math.PI / 4.0);
    /** The most sides a post is drawn with. */
    private static final int MOST_POST_SIDES = 20;
    /** The cosine and sine at each corner round the post being added, and at the middle of each side. */
    private final float[] postCos = new float[MOST_POST_SIDES + 1], postSin = new float[MOST_POST_SIDES + 1];
    private final float[] postMiddleCos = new float[MOST_POST_SIDES], postMiddleSin = new float[MOST_POST_SIDES];

    /**
     * Stands a post as the solid Posts read off its sprite: a round one turned about its axis
     * to the radius its outline has at every height, a square one with its sides along the
     * grid. Each point of its surface the game's view saw is painted with what the sprite
     * shows there. The far side shows the near side again, mirrored in the plane across the
     * view through the axis, so that the two meet without a seam.
     *
     * (a, b) is where the axis stands, top how high the post is made, in levels, where that is
     * above what its sprite shows of the shaft, and capped whether its top is closed. With
     * oneTexel every vertex takes the texel (texelU, texelV): timber, whose grain the picture makes.
     */
    void addPost(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ, Posts post, float a, float b, float top, boolean capped,
            boolean oneTexel, float texelU, float texelV) {
        boolean round = post.kind == Posts.ROUND;
        int sides = !round ? 4 : post.half >= 0.2f ? MOST_POST_SIDES : post.half >= 0.05f ? 12 : 8;
        // Worked out once for the post: every station up it has the same corners round it.
        for (int side = 0; side <= sides; side++) {
            float angle = DIAGONAL + side * 2f * (float) Math.PI / sides;
            postCos[side] = (float) Math.cos(angle);
            postSin[side] = (float) Math.sin(angle);
            if (side < sides) {
                float middle = (angle + (DIAGONAL + (side + 1) * 2f * (float) Math.PI / sides)) / 2f;
                postMiddleCos[side] = (float) Math.cos(middle);
                postMiddleSin[side] = (float) Math.sin(middle);
            }
        }
        float out = round ? 1f : (float) Math.sqrt(2.0);
        float inA = Math.clamp(a, 0f, 1f), inB = Math.clamp(b, 0f, 1f);
        float w0 = (1f - inA) * (1f - inB), w1 = inA * (1f - inB), w2 = inA * inB, w3 = (1f - inA) * inB;
        float red = frame.cornerRed[0] * w0 + frame.cornerRed[1] * w1 + frame.cornerRed[2] * w2 + frame.cornerRed[3] * w3;
        float green = frame.cornerGreen[0] * w0 + frame.cornerGreen[1] * w1 + frame.cornerGreen[2] * w2 + frame.cornerGreen[3] * w3;
        float blue = frame.cornerBlue[0] * w0 + frame.cornerBlue[1] * w1 + frame.cornerBlue[2] * w2 + frame.cornerBlue[3] * w3;
        float foot = (tileZ + raised) * ViewCamera.LEVEL_HEIGHT + liftAt(a, b);
        float x = tileX + a, y = tileY + b;
        mesh.open = frame.open + frame.kind + 32f * frame.material;
        mesh.sway = 0f;
        int stations = post.heights.length;
        float highest = Math.max(top, post.heights[stations - 1]);
        for (int station = 0; station < stations - 1; station++) {
            float h0 = post.heights[station], h1 = station == stations - 2 ? highest : post.heights[station + 1];
            float r0 = post.halves[station] * out, r1 = post.halves[station + 1] * out;
            // How far the surface leans back between the two: a step in the profile is a ledge.
            float rise = (h1 - h0) * ViewCamera.LEVEL_HEIGHT, lean = r0 - r1;
            float length = (float) Math.sqrt(rise * rise + lean * lean);
            if (length < 1e-5f) {
                continue;
            }
            float flat = rise / length, upward = lean / length;
            for (int side = 0; side < sides; side++) {
                for (int corner = 0; corner < 6; corner++) {
                    // Two triangles: low from, low to, high to, then low from, high to, high from.
                    boolean far = corner == 1 || corner == 2 || corner == 4, high = corner == 2 || corner >= 4;
                    int at = far ? side + 1 : side;
                    float cos = postCos[at], sin = postSin[at];
                    float r = high ? r1 : r0, h = high ? h1 : h0;
                    mesh.face((round ? cos : postMiddleCos[side]) * flat, (round ? sin : postMiddleSin[side]) * flat, upward);
                    postVertex(mesh, frame, x, y, foot, a, b, cos * r, sin * r, h, highest, oneTexel, texelU, texelV, red, green, blue);
                }
            }
        }
        if (!capped) {
            return;
        }
        float r = post.halves[stations - 1] * out;
        mesh.face(0f, 0f, 1f);
        for (int side = 0; side < sides; side++) {
            postVertex(mesh, frame, x, y, foot, a, b, 0f, 0f, highest, highest, oneTexel, texelU, texelV, red, green, blue);
            postVertex(mesh, frame, x, y, foot, a, b, postCos[side] * r, postSin[side] * r, highest, highest, oneTexel, texelU, texelV, red, green, blue);
            postVertex(mesh, frame, x, y, foot, a, b, postCos[side + 1] * r, postSin[side + 1] * r, highest, highest, oneTexel, texelU, texelV, red, green, blue);
        }
    }

    private void postVertex(Mesh mesh, Frame frame, float x, float y, float foot, float a, float b, float da, float db, float h, float highest,
            boolean oneTexel, float texelU, float texelV, float red, float green, float blue) {
        float u = texelU, v = texelV;
        if (!oneTexel) {
            v = postTexel(frame, a, b, da, db, h, highest, postAt);
            u = postAt[0];
        }
        mesh.add(x + da, y + db, foot + h * ViewCamera.LEVEL_HEIGHT, u, v, red, green, blue);
    }

    private final float[] postAt = new float[1];

    /**
     * The texel a point of a post's surface shows: the one the sprite paints at that point if
     * the game's view saw it, and at its mirror image in the plane across the view if not.
     * Returns the texture's v and leaves its u in into.
     */
    static float postTexel(Frame frame, float a, float b, float da, float db, float h, float highest, float[] into) {
        float ea = da * POST_TEXELS_IN, eb = db * POST_TEXELS_IN;
        if (ea + eb < 0f) {
            float swap = ea;
            ea = -eb;
            eb = -swap;
        }
        float edge = Math.min(0.02f, highest / 4f);
        into[0] = textureU(frame, a + ea, b + eb);
        return textureV(frame, a + ea, b + eb, Math.clamp(h, edge, highest - edge));
    }

    /**
     * Adds a square timber from one point to another, each (x, y, up) in tiles, half thick each
     * way from its middle line, closed at both ends, every vertex with the one texel (u, v): a
     * brace from a post up to the beam it carries.
     */
    static void addStrut(Mesh mesh, float[] from, float[] to, float half, float u, float v, float red, float green, float blue) {
        float ax = to[0] - from[0], ay = to[1] - from[1], az = to[2] - from[2];
        float length = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= length;
        ay /= length;
        az /= length;
        // Across it, level, and then square to both.
        float flat = (float) Math.sqrt(ax * ax + ay * ay);
        float sx = flat < 1e-4f ? 1f : -ay / flat, sy = flat < 1e-4f ? 0f : ax / flat;
        float tx = -az * sy, ty = az * sx, tz = ax * sy - ay * sx;
        float[] c = new float[12];
        for (int side = 0; side < 4; side++) {
            float s0 = side == 0 || side == 3 ? -half : half, t0 = side < 2 ? -half : half;
            float s1 = side < 2 ? half : -half, t1 = side == 0 || side == 3 ? -half : half;
            strutCorner(c, 0, from, s0, t0, sx, sy, tx, ty, tz);
            strutCorner(c, 1, from, s1, t1, sx, sy, tx, ty, tz);
            strutCorner(c, 2, to, s1, t1, sx, sy, tx, ty, tz);
            strutCorner(c, 3, to, s0, t0, sx, sy, tx, ty, tz);
            addQuad(mesh, c, u, v, red, green, blue);
        }
        for (int end = 0; end < 2; end++) {
            float[] at = end == 0 ? from : to;
            strutCorner(c, 0, at, -half, -half, sx, sy, tx, ty, tz);
            strutCorner(c, 1, at, half, -half, sx, sy, tx, ty, tz);
            strutCorner(c, 2, at, half, half, sx, sy, tx, ty, tz);
            strutCorner(c, 3, at, -half, half, sx, sy, tx, ty, tz);
            addQuad(mesh, c, u, v, red, green, blue);
        }
    }

    private static void strutCorner(float[] c, int corner, float[] at, float s, float t, float sx, float sy, float tx, float ty, float tz) {
        c[corner * 3] = at[0] + sx * s + tx * t;
        c[corner * 3 + 1] = at[1] + sy * s + ty * t;
        c[corner * 3 + 2] = at[2] + tz * t;
    }

    /** What a timber post and its braces are made of: sawn wood the picture gives its grain, with no joints in it. */
    static final float TIMBER = 66f;
    /** A brace is this much of its post's thickness, and stops this many of its own thicknesses short of where its sprite's tip is. */
    private static final float BRACE_THICK = 0.82f;
    /** A column this thick or thicker, in tiles of radius, has a capital. */
    static final float CAPITAL_FROM = 0.25f;
    /** A capital: how far below the column's top it starts, in levels, how far its swelling and its slab stand out, as shares of the radius, and how much of it is the slab. */
    private static final float CAPITAL_HIGH = 0.11f, CAPITAL_SWELL = 1.16f, CAPITAL_SLAB = 1.3f, CAPITAL_SLAB_SHARE = 0.4f;

    private final float[] armTo = new float[3], strutFrom = new float[3], strutTo = new float[3];

    /**
     * Stands all of a post: its shaft as a solid, the braces of a timber one as timbers of
     * their own, a capital on a column, and whatever else its sprite shows above the shaft, a
     * lamp's head or a sign's plate, as the part of the sprite it is, on the post's axis and
     * turned to the camera by the picture.
     *
     * timber is for a post of sawn wood: it takes one colour from its sprite and the picture
     * gives it grain. bracesAlongX is which upright plane its braces lie in. capital is
     * whether a column's top is its own and not the foot of another length of it.
     */
    void standPost(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ, Posts post, float a, float b, boolean timber, boolean bracesAlongX,
            boolean capital) {
        float material = frame.material;
        float half = post.half;
        float u = textureU(frame, a + half * POST_TEXELS_IN, b), v = textureV(frame, a + half * POST_TEXELS_IN, b, post.top() / 2f);
        int braces = timber ? post.arms.length / 4 : 0;
        float top = post.top();
        for (int brace = 0; brace < braces; brace++) {
            post.armTo(brace, bracesAlongX, armTo);
            top = Math.max(top, post.armsFrom + armTo[2]);
        }
        if (timber) {
            frame.material = TIMBER;
        }
        boolean headed = post.headed || braces == 0 && post.arms.length > 0;
        addPost(mesh, frame, tileX, tileY, tileZ, post, a, b, top, !headed, timber, u, v);
        float inA = Math.clamp(a, 0f, 1f), inB = Math.clamp(b, 0f, 1f);
        float w0 = (1f - inA) * (1f - inB), w1 = inA * (1f - inB), w2 = inA * inB, w3 = (1f - inA) * inB;
        float red = frame.cornerRed[0] * w0 + frame.cornerRed[1] * w1 + frame.cornerRed[2] * w2 + frame.cornerRed[3] * w3;
        float green = frame.cornerGreen[0] * w0 + frame.cornerGreen[1] * w1 + frame.cornerGreen[2] * w2 + frame.cornerGreen[3] * w3;
        float blue = frame.cornerBlue[0] * w0 + frame.cornerBlue[1] * w1 + frame.cornerBlue[2] * w2 + frame.cornerBlue[3] * w3;
        float foot = (tileZ + raised) * ViewCamera.LEVEL_HEIGHT + liftAt(a, b);
        for (int brace = 0; brace < braces; brace++) {
            post.armTo(brace, bracesAlongX, armTo);
            float thick = half * BRACE_THICK;
            float rise = armTo[2] * ViewCamera.LEVEL_HEIGHT, reach = (float) Math.sqrt(armTo[0] * armTo[0] + armTo[1] * armTo[1] + rise * rise);
            // The sprite's tip is the far corner of the brace's end: its middle line stops a thickness short of it.
            float kept = Math.max(0.2f, 1f - thick / reach);
            strutFrom[0] = tileX + a;
            strutFrom[1] = tileY + b;
            strutFrom[2] = foot + post.armsFrom * ViewCamera.LEVEL_HEIGHT - thick;
            strutTo[0] = strutFrom[0] + armTo[0] * kept;
            strutTo[1] = strutFrom[1] + armTo[1] * kept;
            strutTo[2] = strutFrom[2] + rise * kept;
            addStrut(mesh, strutFrom, strutTo, thick, u, v, red, green, blue);
        }
        if (capital && post.kind == Posts.ROUND && half >= CAPITAL_FROM) {
            float r = post.halves[post.halves.length - 1];
            float under = foot + (top - CAPITAL_HIGH) * ViewCamera.LEVEL_HEIGHT, over = foot + top * ViewCamera.LEVEL_HEIGHT;
            float slab = over - (over - under) * CAPITAL_SLAB_SHARE;
            float[] c = strutCorners;
            int sides = 20;
            for (int side = 0; side < sides; side++) {
                float from = DIAGONAL + side * 2f * (float) Math.PI / sides, to = DIAGONAL + (side + 1) * 2f * (float) Math.PI / sides;
                float cos0 = (float) Math.cos(from), sin0 = (float) Math.sin(from), cos1 = (float) Math.cos(to), sin1 = (float) Math.sin(to);
                float x = tileX + a, y = tileY + b, wide = r * CAPITAL_SWELL;
                putCorner(c, 0, x + cos0 * r, y + sin0 * r, under);
                putCorner(c, 1, x + cos1 * r, y + sin1 * r, under);
                putCorner(c, 2, x + cos1 * wide, y + sin1 * wide, slab);
                putCorner(c, 3, x + cos0 * wide, y + sin0 * wide, slab);
                addQuad(mesh, c, u, v, red, green, blue);
            }
            strutFrom[0] = strutTo[0] = tileX + a;
            strutFrom[1] = strutTo[1] = tileY + b;
            strutFrom[2] = slab;
            strutTo[2] = over;
            addStrut(mesh, strutFrom, strutTo, r * CAPITAL_SLAB, u, v, red, green, blue);
        }
        frame.material = material;
        if (headed) {
            skipped = post.shaftShare;
            addUpright(mesh, frame, tileX, tileY, tileZ, a, b, 0f, 1f, 0f, true, false);
            skipped = 0f;
        }
        if (post.footShare > 0f) {
            float kept = keep;
            keep = post.footShare;
            addUpright(mesh, frame, tileX, tileY, tileZ, a, b, 0f, 1f, 0f, true, false);
            keep = kept;
        }
    }

    private final float[] strutCorners = new float[12];

    private static void putCorner(float[] c, int corner, float x, float y, float up) {
        c[corner * 3] = x;
        c[corner * 3 + 1] = y;
        c[corner * 3 + 2] = up;
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
        if (halfAcross > inset && halfDown > inset) {
            // A face that stands straight up is drawn smaller onto itself about its middle,
            // so every point of it moves in by its share and not by the whole inset. A sprite
            // that covers part of the face cuts it there, and the corners the cut makes are
            // inside the face: moved the whole inset like the face's own corners, each sprite
            // would be stretched by where it happens to be cut, and a pipe that runs along a
            // wall from one square's sprite to the next would step at every square.
            float fromCentre = pixelU - centreU;
            // How far below the face's middle the point is, with the slant of its courses taken out.
            float below = pixelV - centreV - fromCentre * alongSlant;
            float movedAcross = -Math.clamp(fromCentre / halfAcross, -1f, 1f) * insetAcross;
            float insetDown = inset + (below < 0f ? sealTop : 0f);
            pixelU += movedAcross;
            // A wall's courses run across its sprite at a slant. Moved across alone, a corner
            // of the face would take its texels from a course up or down, and the boards of
            // one wall would step where it meets the next: the move follows the slant.
            pixelV += movedAcross * alongSlant - Math.clamp(below / halfDown, -1f, 1f) * insetDown;
        } else {
            float movedAcross = Math.clamp(centreU - pixelU, -insetAcross, insetAcross);
            pixelU += movedAcross;
            pixelV += movedAcross * alongSlant;
            pixelV += Math.clamp(centreV - pixelV, -inset, inset);
        }
        float keptU = Math.clamp(pixelU, frame.left + HALF_PIXEL, frame.left + frame.width - HALF_PIXEL);
        // On a face that stands straight up, a texel kept inside the sprite's side stays on
        // its own course: left on its row, the courses of the face would tilt towards that side.
        if (halfAcross > 0f) {
            pixelV += (keptU - pixelU) * alongSlant;
        }
        pixelU = keptU;
        pixelV = Math.clamp(pixelV, frame.top + HALF_PIXEL, frame.top + frame.height - HALF_PIXEL);
        float u = frame.uStart + (pixelU - frame.left) / frame.width * (frame.uEnd - frame.uStart);
        float v = frame.vStart + (pixelV - frame.top) / frame.height * (frame.vEnd - frame.vStart);
        float a = Math.clamp(polygon[at], 0f, 1f), b = Math.clamp(polygon[at + 1], 0f, 1f);
        float w0 = (1f - a) * (1f - b), w1 = a * (1f - b), w2 = a * b, w3 = (1f - a) * b;
        mesh.sway = cover == null ? 0f : 1f - (cover[0] * w0 + cover[1] * w1 + cover[2] * w2 + cover[3] * w3);
        mesh.add(tileX + polygon[at] + shownA, tileY + polygon[at + 1] + shownB,
                (tileZ + polygon[at + 2] + shownUp + raised) * ViewCamera.LEVEL_HEIGHT + liftAt(polygon[at], polygon[at + 1]), u, v,
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

    /** As addPlainOver, every vertex with the one texel (u, v). */
    static void addOver(Mesh mesh, int x, int y, float h00, float h10, float h11, float h01, float u, float v, float red, float green, float blue) {
        mesh.face(-(h10 - h00 + h11 - h01) / 2f, -(h01 - h00 + h11 - h10) / 2f, 1f);
        mesh.add(x, y, h00, u, v, red, green, blue);
        mesh.add(x + 1f, y, h10, u, v, red, green, blue);
        mesh.add(x + 1f, y + 1f, h11, u, v, red, green, blue);
        mesh.add(x, y, h00, u, v, red, green, blue);
        mesh.add(x + 1f, y + 1f, h11, u, v, red, green, blue);
        mesh.add(x, y + 1f, h01, u, v, red, green, blue);
    }

    /**
     * As addPlainQuad, every vertex with the one texel (u, v): for a mesh whose picture gives
     * the face nothing but a colour.
     */
    static void addQuad(Mesh mesh, float[] c, float u, float v, float red, float green, float blue) {
        float ux = c[3] - c[0], uy = c[4] - c[1], uz = c[5] - c[2];
        float vx = c[9] - c[0], vy = c[10] - c[1], vz = c[11] - c[2];
        mesh.face(uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx);
        for (int corner : QUAD) {
            mesh.add(c[corner * 3], c[corner * 3 + 1], c[corner * 3 + 2], u, v, red, green, blue);
        }
    }

    /** Adds the triangle with corners c, each (x, y, up), facing up, every vertex with the one texel (u, v). */
    static void addTriangle(Mesh mesh, float[] c, float u, float v, float red, float green, float blue) {
        float ux = c[3] - c[0], uy = c[4] - c[1], uz = c[5] - c[2];
        float vx = c[6] - c[0], vy = c[7] - c[1], vz = c[8] - c[2];
        float turn = Math.signum(ux * vy - uy * vx);
        mesh.face(turn * (uy * vz - uz * vy), turn * (uz * vx - ux * vz), turn * (ux * vy - uy * vx));
        for (int corner = 0; corner < 3; corner++) {
            mesh.add(c[corner * 3], c[corner * 3 + 1], c[corner * 3 + 2], u, v, red, green, blue);
        }
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
