package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import zombie.core.textures.Texture;

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
     * moves with the wind, then three the picture places the vertex by as it draws. The last
     * says what the two before it are: 0 nothing, FACES for a card that turns to the camera,
     * CLEARS for one that stands clear of its plane, LAYERS_X, LAYERS_Y or LAYERS_UP for a
     * face that lies some layers off its plane along that axis, and SHEET for a floor or a
     * ceiling, which lies up as LAYERS_UP does and is whole in the shadow maps where its sprite
     * has clear pixels: a roof of one sheet lets no sun through them; and WOOD for a ring of a
     * limb, a branch or a twig, whose first is how thick the wood is there, as a radius in tiles,
     * coded as Flora.woodCode says for the snow on it (see SnowShader.RIDGE). The first of the three is how
     * far along its card a vertex of FACES is, in tiles, how many layers of cards one of
     * CLEARS stands clear by, and how many layers one of LAYERS lies off. The second is how
     * many layers of cards the vertex stands towards the camera. How far a layer is, the
     * picture works out from how far off the vertex is: see PipelineShaders.FACED. A strip of
     * addStrip carries other things in the vector's place. Last, the square of the tree the
     * vertex is part of, NO_ROOT for anything that is no tree's: the game opens or leaves a
     * tree whole in its own view (see CanopyWindow). Blood is no tree, and carries there the
     * colour its texels are drawn in, red and then green and blue (see BloodBuilder). Last of
     * all, what the vertex has of a light the game has not given, and of the day its room
     * lets in: see Mesh.unlit and Mesh.day.
     */
    static final int VERTEX_FLOATS = 19;
    /** Where in a vertex what the picture places it by is: how far along or how many layers, and what that is; its tree's square; and what it has of a light the game has not given, then of the day. */
    static final int ALONG_AT = 12, WAY_AT = 14, ROOT_AT = 15, UNLIT_AT = 17, DAY_AT = 18;
    static final float NO_ROOT = -1f;
    /** What a mesh is of when it is of no building's storey: see MeshSet.storey. */
    static final long NO_BUILDING = Long.MIN_VALUE;
    /**
     * What a vertex carries in Mesh.unlit where its light is all of the kind its openness
     * names (see WorldScene.LIT_STEP), and the step a kind of light the game has not given
     * is counted in above how much of it a vertex has: see Frame.cornerUnlit.
     */
    static final float AS_FACE = -1f, UNLIT_KIND_STEP = 16f;
    static final float FACES = 1f, CLEARS = 2f, LAYERS_X = 3f, LAYERS_Y = 4f, LAYERS_UP = 5f, SHEET = 6f, WOOD = -1f;

    /**
     * How many layers off its plane everything added next lies, in every mesh that shares
     * this, and along which axis. Two sprites on one plane fight for depth, the worse the
     * further off they are, so each is drawn a layer clear of the one under it and the picture
     * makes a layer as thick as the distance needs: nothing of where the eye is goes into the
     * vertices. A card has no room for it and is layered by nearer and clear.
     */
    static final class Layers {
        private float way, count;

        void lie(float way, float count) {
            this.way = count == 0f ? 0f : way;
            this.count = count;
        }

        void sheet(float count) {
            this.way = SHEET;
            this.count = count;
        }

        void none() {
            way = count = 0f;
        }

        boolean lying() {
            return way != 0f || count != 0f;
        }

        /** How many layers off its plane everything added next lies. */
        float count() {
            return count;
        }
    }
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
    /**
     * How far the wind carries a vertex that takes the whole of it, in tiles, at most: the
     * strongest push, Wind.push(1), by the most the world shader's blown() leans, 0.115.
     */
    static final float SWAY_REACH = 0.24f;
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
    static final float TILES_PER_PIXEL_UP = ViewCamera.LEVEL_HEIGHT / PIXELS_PER_LEVEL;

    /** A face that covers less of the frame than this, in square pixels, only touches the sprite's edge. */
    private static final float MIN_FRAME_PIXELS = 0.5f;
    /**
     * A sprite's edge is a staircase of pixels, half of them empty, and two tiles that meet
     * along it would show a dotted gap. Each vertex takes its texel from this far inside the
     * face, in frame pixels at tile scale 1.
     */
    private static final float EDGE_INSET_PIXELS = 1.5f;
    /** How far, in frame pixels at tile scale 1, a face cut to its sprite runs on past the sprite's edge, taking its last texels: see addFace. */
    static final float EDGE_SLACK_PIXELS = 2f;
    /** A texel is taken no nearer the trimmed rectangle's edge than the centre of its outermost pixel. */
    static final float HALF_PIXEL = 0.5f;
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
        /** The room whose contents its triangles are, drawn only while the eye sees into it, or RoomSight.OUTSIDE for what is drawn whenever its chunk is: see MeshSet.room. */
        long room = RoomSight.OUTSIDE;
        /** The building and the level of the squares its triangles are of, or NO_BUILDING: see MeshSet.storey. */
        long building = NO_BUILDING;
        int level;
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
         * such a card. Their place is then given as the foot's. And for anything, how many
         * layers of cards towards the camera the vertices added next stand off from where they
         * are given.
         */
        float along, nearer;
        boolean faces;
        /** For a card that does not turn: that along is how many layers of cards the vertices added next stand clear of the card's own plane, on the side the camera is. */
        boolean clears;
        /** For a ring of wood, how thick it is and what snow it holds, as Flora.woodCode codes it: see WOOD. 0 for anything else. */
        float wood;
        /** How many layers off its plane anything added next that is no card lies: the set's own, for a mesh of a set. */
        Layers layers = new Layers();
        /** The square of the tree the vertices added next are part of, or NO_ROOT. */
        float rootX = NO_ROOT, rootY = NO_ROOT;
        /**
         * What the vertices added next have of a light the game has not given: AS_FACE where
         * their light is all of the kind their openness names, or as Frame.unlitAt gives it. A
         * builder that sets it for some vertices puts it back to AS_FACE after them.
         */
        float unlit = AS_FACE;
        /**
         * How much of the open ground's light the room of the vertices added next lets in by
         * day (RoomDaylight), where their light is a kind the game has not given: the picture
         * lights them at least by that much of the day's light. 0 for none. Set and put back
         * with unlit.
         */
        float day;
        /**
         * The runs of the mesh's floats that a thing that may be outlined drew, each its key
         * (Outlines.key), its first float and the float after its last: see MeshSet.own.
         */
        long[] spanKey = new long[0];
        int[] spanFrom = new int[0], spanTo = new int[0];
        int spans;
        /** Where the floats of the thing being drawn begin in this mesh. */
        private int ownedFrom;
        /** The set the mesh is of, or null for one of its own, and whether it has grown since the set's last own: see MeshSet.grown. */
        MeshSet set;
        private boolean grown;

        /** Has what the vertices added next carry be what another mesh's would. */
        void carryAs(Mesh other) {
            open = other.open;
            normalX = other.normalX;
            normalY = other.normalY;
            normalUp = other.normalUp;
            sway = other.sway;
            along = other.along;
            nearer = other.nearer;
            faces = other.faces;
            clears = other.clears;
            wood = other.wood;
            rootX = other.rootX;
            rootY = other.rootY;
            unlit = other.unlit;
            day = other.day;
        }

        /** Notes in the mesh's set that the mesh has grown since the set's last own. */
        private void growing() {
            if (!grown && set != null) {
                grown = true;
                set.grown.add(this);
            }
        }

        private void span(long key, int from, int to) {
            if (spans == spanKey.length) {
                int more = Math.max(4, spans * 2);
                spanKey = Arrays.copyOf(spanKey, more);
                spanFrom = Arrays.copyOf(spanFrom, more);
                spanTo = Arrays.copyOf(spanTo, more);
            }
            spanKey[spans] = key;
            spanFrom[spans] = from;
            spanTo[spans] = to;
            spans++;
        }

        /** The vertices added next are part of the tree on that square. */
        void rooted(int x, int y) {
            rootX = x;
            rootY = y;
        }

        /** The vertices added next are no tree's. */
        void unrooted() {
            rootX = rootY = NO_ROOT;
        }

        void face(float x, float y, float up) {
            float length = (float) Math.sqrt(x * x + y * y + up * up);
            normalX = x / length;
            normalY = y / length;
            normalUp = up / length;
        }

        /** As face, for a way that is already of length 1. */
        void faceUnit(float x, float y, float up) {
            normalX = x;
            normalY = y;
            normalUp = up;
        }

        void clear() {
            most = Math.max(most, floats);
            floats = 0;
            unlit = AS_FACE;
            day = 0f;
            spans = 0;
            ownedFrom = 0;
            grown = false;
        }

        int triangles() {
            return floats / (VERTEX_FLOATS * 3);
        }

        void add(float x, float y, float up, float u, float v, float red, float green, float blue) {
            growing();
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
            vertices[floats++] = wood != 0f ? wood : faces || clears ? along : layers.count;
            vertices[floats++] = nearer;
            vertices[floats++] = wood != 0f ? WOOD : faces ? FACES : clears ? CLEARS : layers.way;
            vertices[floats++] = rootX;
            vertices[floats++] = rootY;
            vertices[floats++] = unlit;
            vertices[floats++] = day;
        }

        /** unlit and day for vertices with the frame's corners' light in the given shares. */
        void litAt(Frame frame, float w0, float w1, float w2, float w3) {
            unlit = frame.unlitAt(w0, w1, w2, w3);
            day = frame.dayAt(w0, w1, w2, w3);
        }

        /** Puts unlit and day back for vertices whose light is all of the kind their openness names. */
        void litAsFace() {
            unlit = AS_FACE;
            day = 0f;
        }

        /** Adds the vertex that starts at the given float once more, as it is. */
        void again(int from) {
            growing();
            if (floats + VERTEX_FLOATS > vertices.length) {
                vertices = Arrays.copyOf(vertices, vertices.length * 2);
            }
            System.arraycopy(vertices, from, vertices, floats, VERTEX_FLOATS);
            floats += VERTEX_FLOATS;
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
        /**
         * The meshes that have grown since the last own. Only they can hold what the thing
         * drawn since has drawn: with a mesh for each storey of a tower, going through every mesh
         * used for every thing drawn was most of a build.
         */
        private final ArrayList<Mesh> grown = new ArrayList<>();
        /** The meshes asked for since the set was last emptied, in the order they were first asked for. */
        final ArrayList<Mesh> used = new ArrayList<>();
        private int round = 1;
        /** What every mesh of the set lies by: see Layers. */
        private final Layers layers;
        /**
         * The room what is asked for next is the contents of, or RoomSight.OUTSIDE. Each room's
         * contents have meshes of their own, so that a picture can leave out a room it cannot
         * see into and draw the rest of the chunk.
         */
        long room = RoomSight.OUTSIDE;
        /**
         * The building and the level of the square what is asked for next is of, or NO_BUILDING
         * and 0. Each storey of a building has meshes of its own, so that a picture from inside
         * it can leave out the floors over the viewer's that it cannot see.
         */
        long building = NO_BUILDING;
        int level;
        /** The key of the thing being drawn into the set, or Outlines.NONE: see own. */
        private long owner = Outlines.NONE;

        MeshSet() {
            this(new Layers());
        }

        /** A set whose meshes lie by the given layers, which another set may share. */
        MeshSet(Layers layers) {
            this.layers = layers;
        }

        Mesh get(int texture) {
            return get(texture, 0);
        }

        Mesh get(int texture, int tint) {
            int slot = slotFor(byTexture, texture, tint, room, building, level);
            Mesh mesh = byTexture[slot];
            if (mesh == null) {
                mesh = new Mesh();
                mesh.layers = layers;
                mesh.texture = texture;
                mesh.tint = tint;
                mesh.room = room;
                mesh.set = this;
                mesh.building = building;
                mesh.level = level;
                all.add(mesh);
                if (all.size() * 2 > byTexture.length) {
                    byTexture = new Mesh[byTexture.length * 2];
                    for (int i = 0; i < all.size(); i++) {
                        Mesh moved = all.get(i);
                        byTexture[slotFor(byTexture, moved.texture, moved.tint, moved.room, moved.building, moved.level)] = moved;
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

        /** What is asked for from now on is of the building's storey on that level, or of none with NO_BUILDING: see building. */
        void storey(long ofBuilding, int onLevel) {
            building = ofBuilding;
            level = ofBuilding == NO_BUILDING ? 0 : onLevel;
        }

        /**
         * What is drawn into the set from now on is the thing with this key (Outlines.key), or
         * no thing's for Outlines.NONE, until the next call. Each mesh notes the floats each
         * thing drew in it.
         */
        void own(long key) {
            for (int i = 0; i < grown.size(); i++) {
                Mesh mesh = grown.get(i);
                if (owner != Outlines.NONE && mesh.floats > mesh.ownedFrom) {
                    mesh.span(owner, mesh.ownedFrom, mesh.floats);
                }
                mesh.ownedFrom = mesh.floats;
                mesh.grown = false;
            }
            grown.clear();
            owner = key;
        }

        /** The key of the thing being drawn into the set, or Outlines.NONE: see own. */
        long owner() {
            return owner;
        }

        /** Empties every mesh and keeps them: one chunk wears much the same textures as the next. */
        void clear() {
            if (owner != Outlines.NONE) {
                throw new IllegalStateException("a set is emptied while a thing is still being drawn into it");
            }
            for (int i = 0; i < used.size(); i++) {
                used.get(i).clear();
            }
            used.clear();
            grown.clear();
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
                byTexture[slotFor(byTexture, moved.texture, moved.tint, moved.room, moved.building, moved.level)] = moved;
            }
        }

        /** The slot holding the mesh of the texture in that tint for that room's contents and that storey, or the empty one it belongs in. */
        private static int slotFor(Mesh[] table, int texture, int tint, long room, long building, int level) {
            int mask = table.length - 1;
            int slot = (texture ^ tint * 0x9E3779B1 >>> 16 ^ (int) (room ^ room >>> 32) * 0x85EBCA6B ^ (int) (building ^ building >>> 32) * 0xC2B2AE35
                    ^ level * 0x27D4EB2F) & mask;
            while (table[slot] != null && (table[slot].texture != texture || table[slot].tint != tint || table[slot].room != room
                    || table[slot].building != building || table[slot].level != level)) {
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
        /** 1 for a square out of doors, 0 for one under a roof, and above that the kind of light it takes: see WorldScene.LIT_STEP. */
        float open;
        /** What the next face is, added to its openness: 0 for an ordinary surface, WATER for water. */
        float kind;
        /** What the next face is made of: 0 nothing in particular, or one of the pictures of materials the picture lays over it. */
        float material;
        /**
         * Whether the texture is the square's own floor laid flat, or the wall along one of its
         * edges stood square: see Flats. A face then takes its texels by where on the square,
         * or how far along the wall and how high, each of its corners is.
         */
        int flat;
        /**
         * The light at the square's four corners, (0, 0), (1, 0), (1, 1) then (0, 1). A face
         * takes its light from these by where on the square each of its vertices is, so the
         * light runs smoothly from one square into the next.
         */
        final float[] cornerRed = new float[4], cornerGreen = new float[4], cornerBlue = new float[4];
        /**
         * Where a corner's light is the mean of squares the game has lit and squares it has not
         * (WorldScene.OPEN_LIT and the rest), which count in cornerRed and its fellows as 1:
         * how much of that mean the squares it has not lit are, which the picture makes the
         * light of the kind unlitKind names for this frame. A shade a builder lays on the
         * corner's light leaves it as it is. AS_FACE at every corner of a frame lit as one
         * light, whose openness says what it is lit by.
         */
        final float[] cornerUnlit = {AS_FACE, AS_FACE, AS_FACE, AS_FACE};
        float unlitKind;
        /** At each corner, how much of the open ground's light the rooms of the squares counted in cornerUnlit let in by day: see Mesh.day. */
        final float[] cornerDay = new float[4];
        /** How many floats cornersInto copies out of cornerUnlit, unlitKind and cornerDay. */
        static final int UNLIT_FLOATS = 9;
        /** What a crown made with the frame is worked out in: see Flora.Workings. Null until one is. */
        Flora.Workings crownWorkings;

        /** The rectangle of the frame a texture holds, and where in the texture it is. */
        void takeTexels(Texture texture) {
            left = texture.getOffsetX();
            top = texture.getOffsetY();
            width = texture.getWidth();
            height = texture.getHeight();
            uStart = texture.getXStart();
            uEnd = texture.getXEnd();
            vStart = texture.getYStart();
            vEnd = texture.getYEnd();
        }

        /** Another frame's rectangle and texels. */
        void texelsOf(Frame other) {
            left = other.left;
            top = other.top;
            width = other.width;
            height = other.height;
            uStart = other.uStart;
            uEnd = other.uEnd;
            vStart = other.vStart;
            vEnd = other.vEnd;
        }

        /** Copies the light at the corners out, to be put back by {@link #cornersFrom}: into unlit, UNLIT_FLOATS long, cornerUnlit, unlitKind and then cornerDay. */
        void cornersInto(float[] red, float[] green, float[] blue, float[] unlit) {
            System.arraycopy(cornerRed, 0, red, 0, 4);
            System.arraycopy(cornerGreen, 0, green, 0, 4);
            System.arraycopy(cornerBlue, 0, blue, 0, 4);
            System.arraycopy(cornerUnlit, 0, unlit, 0, 4);
            unlit[4] = unlitKind;
            System.arraycopy(cornerDay, 0, unlit, 5, 4);
        }

        /** The light at the corners as {@link #cornersInto} copied it out. */
        void cornersFrom(float[] red, float[] green, float[] blue, float[] unlit) {
            System.arraycopy(red, 0, cornerRed, 0, 4);
            System.arraycopy(green, 0, cornerGreen, 0, 4);
            System.arraycopy(blue, 0, cornerBlue, 0, 4);
            System.arraycopy(unlit, 0, cornerUnlit, 0, 4);
            unlitKind = unlit[4];
            System.arraycopy(unlit, 5, cornerDay, 0, 4);
        }

        /** One light for the whole square. */
        void light(float r, float g, float b) {
            red = r;
            green = g;
            blue = b;
            Arrays.fill(cornerRed, r);
            Arrays.fill(cornerGreen, g);
            Arrays.fill(cornerBlue, b);
            Arrays.fill(cornerUnlit, AS_FACE);
            Arrays.fill(cornerDay, 0f);
        }

        /** What a vertex with the corners' light in the given shares has of a light the game has not given, for Mesh.unlit. */
        float unlitAt(float w0, float w1, float w2, float w3) {
            if (cornerUnlit[0] < 0f) {
                return AS_FACE;
            }
            return unlitKind * UNLIT_KIND_STEP + cornerUnlit[0] * w0 + cornerUnlit[1] * w1 + cornerUnlit[2] * w2 + cornerUnlit[3] * w3;
        }

        /** What a vertex with the corners' light in the given shares has of the day its room lets in, for Mesh.day. */
        float dayAt(float w0, float w1, float w2, float w3) {
            return cornerDay[0] * w0 + cornerDay[1] * w1 + cornerDay[2] * w2 + cornerDay[3] * w3;
        }
    }

    private final float[] corners = new float[MAX_CORNERS * CORNER_FLOATS];
    private final float[] clipped = new float[MAX_CORNERS * CORNER_FLOATS];
    private final float[] spare = new float[MAX_CORNERS * CORNER_FLOATS];
    /** Where clipToFrame cuts the face being added at each edge of the sprite: its left, right, top and bottom. */
    private final float[] limits = new float[4];
    private float[] result;
    private float centreU, centreV;
    /** For an upright face, how many frame pixels down its level lines run for each pixel across. */
    private float alongSlant;
    /**
     * For a face that stands straight up, half its width in frame pixels across and half its
     * height in frame pixels down. 0 for any other face.
     */
    private float halfAcross, halfDown;
    /**
     * The two edges of the face from its first corner, in frame pixels, for one that lies or
     * leans: its inset is taken along them. Zero for a roof's triangle, which keeps the inset
     * along the frame's own rows and columns.
     */
    private float edgeU1, edgeV1, edgeU2, edgeV2;
    /** For a face that lies or leans, what emit takes its inset along its edges by, the same for each of its corners: see fan. */
    private float sheet, in1, in2;
    /** How far the face being added stands from where its texels are taken, along a and b: see addBox. */
    float shownA, shownB;
    /** The same up, in levels: the underside of a beam shows what its top does. */
    float shownUp;
    /**
     * For a face of a band of tiles (WallSides.TILED), what its texture coordinates are in place
     * of texels: across, how many joints along the wall from the first a point is, the wall
     * running along (alongA, alongB) on the square; up, footV at height footH in levels and
     * perLevel more each level higher, and for a floor's tiles how many joints across from the
     * first the point is, the rows running along (acrossA, acrossB). NaN step for an ordinary face.
     */
    float alongA, alongB, acrossA, acrossB, jointPhase, jointStep = Float.NaN, footH, footV, perLevel;
    /**
     * How many frame pixels in from the face's low and high edge across the sprite its texels
     * are taken, beyond the usual inset: see WorldScene.addPiece.
     */
    float sealLow, sealHigh;
    /** The same down from the top edge of a face that stands straight up. */
    float sealTop;
    /**
     * How many frame pixels the wall skin being added runs on past its wall's end, to the outside
     * of a corner: a face is cut that much further past its sprite's side. Cut at the usual slack,
     * the skin stopped short of the corner by as much as its sprite stops short of its square,
     * and the sky showed up the corner between it and the other wall's skin.
     */
    float runOn;
    /**
     * For a face that stands straight up and is a strip of a wider one, a stripe of paper, the
     * wider face's first corner on the tile and its length along it: the strip takes its texels
     * as that face would there. Drawn in about its own middle, a stripe at a wall's end was too
     * narrow to reach past the dots its sprite stops short by, and the joint was a slit to the
     * world outside. NaN for a face that is whole.
     */
    float wholeA = Float.NaN, wholeB, wholeDa, wholeDb;
    /**
     * Whether the wall skin being added meets another wall in an inside corner at its low or its
     * high end, along a (creaseAlongA) or along b. The game lights a wall only at the corners of
     * its square, and two walls meeting at a corner were lit evenly right into it, two flat
     * pictures meeting at a line: the light into such a corner falls to CREASE_SHADE of itself
     * from CREASE_REACH out from the face of the other wall (see creased).
     */
    boolean creaseLow, creaseHigh, creaseAlongA;
    static final float CREASE_REACH = 0.2f, CREASE_SHADE = 0.7f;
    /** How many frame pixels further in than the usual inset a face's texels are taken from its edges: see Tents.DRAWN_IN. */
    float drawnIn;
    /**
     * Whether a face that stands takes its texels across from just where it lies on the sprite, with no inset: a
     * door's casing, whose post has to end where the doorway is cut through the wall's skin, and the cut is measured
     * off the wall's sprite where it lies. Drawn in, the post took texels 3 pixels further along at the face's start
     * and fell short of the cut, so a strip of the wall showed between it and the leaf, and at the other end it ran
     * on over the leaf.
     */
    boolean exactAcross;
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
    /**
     * A cover of this many is a grid of three by three across the square, row by row from (0, 0): its
     * corners, the middles of its edges and its middle. A face laid with one is added a quarter of the square at a time.
     */
    static final int GRID_COVER = 9;
    /** How far an upright card is moved from where it stands, in tiles: see WorldScene.addOver. */
    float nudgeX, nudgeY;
    /** How many layers of cards towards the camera that draws it the upright added next stands off from its place. */
    float nearer;
    /** How many layers of cards the upright added next stands clear of its own plane on the side of the camera that draws it: for one that does not turn. */
    float clear;
    /**
     * The part of the square, from (roomLowA, roomLowB) to (roomHighA, roomHighB), an upright
     * card that does not turn is cut to, swaying included: a plant beside a wall or a fence ends
     * on its own side of it. Unbounded on a side with nothing there.
     */
    float roomLowA = -Float.MAX_VALUE, roomHighA = Float.MAX_VALUE, roomLowB = -Float.MAX_VALUE, roomHighB = Float.MAX_VALUE;
    /** How much of an upright card is kept, from its foot up: 1 all of it. */
    float keep = 1f;
    /** How tall an upright card stands, as a share of the height its sprite paints over its foot: 1 as painted. */
    float upright = 1f;
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

    /** How many frame pixels at tile scale 1 a face's texels are taken in from its edges, with drawnIn more. */
    static float insetPixels(float drawnIn) {
        return EDGE_INSET_PIXELS + drawnIn;
    }

    /** The texture's across for frame pixel u, kept on the sprite. */
    static float textureAtU(Frame frame, float pixelU) {
        float pixel = Math.clamp(pixelU, frame.left + HALF_PIXEL, frame.left + frame.width - HALF_PIXEL);
        return frame.uStart + (pixel - frame.left) / frame.width * (frame.uEnd - frame.uStart);
    }

    /** The texture's down for frame pixel v, kept on the sprite. */
    static float textureAtV(Frame frame, float pixelV) {
        float pixel = Math.clamp(pixelV, frame.top + HALF_PIXEL, frame.top + frame.height - HALF_PIXEL);
        return frame.vStart + (pixel - frame.top) / frame.height * (frame.vEnd - frame.vStart);
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
        edgeU1 = corners[CORNER_FLOATS + CORNER_U] - corners[CORNER_U];
        edgeV1 = corners[CORNER_FLOATS + CORNER_V] - corners[CORNER_V];
        edgeU2 = corners[CORNER_FLOATS * 3 + CORNER_U] - corners[CORNER_U];
        edgeV2 = corners[CORNER_FLOATS * 3 + CORNER_V] - corners[CORNER_V];
        float acrossU = edgeU1;
        alongSlant = dh1 == 0f && dh2 != 0f && Math.abs(acrossU) > 1e-3f ? (corners[CORNER_FLOATS + CORNER_V] - corners[CORNER_V]) / acrossU : 0f;
        boolean straightUp = alongSlant != 0f && da2 == 0f && db2 == 0f;
        halfAcross = straightUp ? Math.abs(acrossU) / 2f : 0f;
        halfDown = straightUp ? Math.abs(corners[CORNER_FLOATS * 3 + CORNER_V] - corners[CORNER_V]) / 2f : 0f;
        if (straightUp && !Float.isNaN(wholeA)) {
            float fromU = frameU(frame, wholeA, wholeB), toU = frameU(frame, wholeA + wholeDa, wholeB + wholeDb);
            centreU = (fromU + toU) / 2f;
            centreV = (frameV(frame, wholeA, wholeB, h) + frameV(frame, wholeA + wholeDa, wholeB + wholeDb, h + dh2)) / 2f;
            halfAcross = Math.abs(toU - fromU) / 2f;
        }
        // A face that stands is drawn smaller onto itself before it is cut to its sprite, so the
        // cut falls where its texels meet the sprite's edge. Cut first and drawn smaller after,
        // a corner on the sprite's top row moved off it, and kept on the row it bent the face's
        // texels: brick laid over the foot of a parapet stepped at every square.
        float inset = EDGE_INSET_PIXELS * frame.scale;
        // Only a face that shows its sprite, and one wide and tall enough for its insets: drawn
        // in further, a strip over a window would turn inside out before its cut. One drawn in to
        // less than a pixel across covers none of the sprite and is dropped: the piece of wall
        // between a door and the corner, carried past the corner, came to nothing on its seal.
        drawnInFirst = frame.flat == 0 && halfAcross > 0f && halfDown > 0f && 2f * inset + sealLow + sealHigh + 1f < 2f * halfAcross
                && 2f * Math.min(inset, halfDown / 2f) + sealTop < 2f * halfDown;
        for (int corner = 0; drawnInFirst && corner < 4; corner++) {
            standIn(frame, corners, corner * CORNER_FLOATS);
        }

        // A face the sprite does not cover adds nothing. One it does cover may find the sprite a
        // pixel or two short of its edge; cutting the face there would leave a slit between two
        // walls, so the face is allowed to run that far past the sprite and takes the last texels.
        boolean laidFlat = frame.flat == FLAT_FLOOR || frame.flat == FLAT_RUG;
        int covered = laidFlat ? whole() : clipToFrame(frame, 0f);
        if (covered < 3 || framePixels(result, covered) < MIN_FRAME_PIXELS) {
            return 0;
        }
        // A face wholly on the sprite came through uncut, and with slack would come through the same.
        int count = !laidFlat && (wasCut || afresh) ? clipToFrame(frame, EDGE_SLACK_PIXELS * frame.scale) : covered;
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
            // Made the long way, a check builds the square as split cuts it, to hold laySquare to that.
            if (!afresh && wholeSquare(from, count)) {
                laySquare(mesh, frame, tileX, tileY, from);
            } else {
                System.arraycopy(from, 0, split[0], 0, count * CORNER_FLOATS);
                split(mesh, frame, tileX, tileY, 0, count, 0, 0, Kerbs.FINE);
            }
            return mesh.triangles() - before;
        }
        fan(mesh, frame, tileX, tileY, tileZ, from, count);
        return count - 2;
    }

    /**
     * Adds the triangles of a face of count corners, each from its first corner to two that
     * follow each other. A corner is worked out once and written again for each triangle it is in.
     */
    private void fan(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ, float[] polygon, int count) {
        sheet = edgeU1 * edgeV2 - edgeV1 * edgeU2;
        if (frame.flat == 0 && !drawnInFirst && Math.abs(sheet) > 1e-3f && halfAcross == 0f) {
            float inset = insetPixels(drawnIn) * frame.scale;
            float length1 = (float) Math.hypot(edgeU1, edgeV1), length2 = (float) Math.hypot(edgeU2, edgeV2);
            // Each corner ends half the inset inside both edges it lies on, square to them: a
            // diamond's left and right corners as far in as the inset across put them, its top
            // and bottom ones less far than the inset down did.
            float deep = inset / 2f / (Math.abs(sheet) / (length1 * length2));
            in1 = Math.min(deep, length1 / 4f) / length1;
            in2 = Math.min(deep, length2 / 4f) / length2;
        }
        if (afresh) {
            for (int i = 1; i < count - 1; i++) {
                emit(mesh, frame, tileX, tileY, tileZ, polygon, 0);
                emit(mesh, frame, tileX, tileY, tileZ, polygon, i);
                emit(mesh, frame, tileX, tileY, tileZ, polygon, i + 1);
            }
            return;
        }
        int first = mesh.floats;
        emit(mesh, frame, tileX, tileY, tileZ, polygon, 0);
        emit(mesh, frame, tileX, tileY, tileZ, polygon, 1);
        emit(mesh, frame, tileX, tileY, tileZ, polygon, 2);
        for (int i = 3; i < count; i++) {
            mesh.again(first);
            mesh.again(mesh.floats - 2 * VERTEX_FLOATS);
            emit(mesh, frame, tileX, tileY, tileZ, polygon, i);
        }
    }

    /** Dev only, set round the second build of a check (WorldScene.check): every face is made the long way. */
    boolean afresh;
    /** Whether the last clipToFrame cut anything off the face. */
    private boolean wasCut;

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
            if (kerbKeep != LAND) {
                addSide(mesh, frame, tileX, tileY, part, cut(half, kept, part, perA, perB, atOrigin, true), STREET);
            }
            if (kerbKeep != STREET) {
                addSide(mesh, frame, tileX, tileY, part, cut(half, kept, part, perA, perB, atOrigin, false), LAND);
            }
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
        if (cutting) {
            step(PIECE, piece, null, count, side, 0, 0f);
            return;
        }
        kerbSide = side;
        fan(mesh, frame, tileX, tileY, 0, piece, count);
        kerbSide = BOTH;
    }

    /**
     * What split does to the whole of the square, worked out once for the square and the kerb
     * across it (the one through the square at the points of kerbField) and done again for every
     * face of its ground: each step a corner copied, or mixed from two corners, from one of the
     * arrays of split into another, or a piece added. Done again from the face's own corners,
     * the same steps make the same floats split would, without looking at the kerb.
     */
    private static final int COPIED = 0, MIXED = 1, PIECE = 2;
    private int[] stepKind = new int[256], stepFrom = new int[256], stepTo = new int[256], stepCurrent = new int[256], stepNext = new int[256], stepInto = new int[256];
    private float[] stepMix = new float[256];
    private int steps;
    private final float[] kerbField = new float[(Kerbs.FINE + 1) * (Kerbs.FINE + 1)];
    private boolean stepsKnown;
    /** Whether split is keeping its steps (step) and not adding what it cuts. */
    private boolean cutting;

    /** Whether the face of count corners is the whole square, from (0, 0) round by (1, 0). */
    private static boolean wholeSquare(float[] face, int count) {
        return count == 4 && face[0] == 0f && face[1] == 0f && face[CORNER_FLOATS] == 1f && face[CORNER_FLOATS + 1] == 0f
                && face[2 * CORNER_FLOATS] == 1f && face[2 * CORNER_FLOATS + 1] == 1f && face[3 * CORNER_FLOATS] == 0f && face[3 * CORNER_FLOATS + 1] == 1f;
    }

    /** Adds the whole of the square, whose corners are those of face, as split does, by the steps split took over it. */
    private void laySquare(Mesh mesh, Frame frame, int tileX, int tileY, float[] face) {
        boolean same = stepsKnown;
        for (int i = 0; i <= Kerbs.FINE && same; i++) {
            for (int j = 0; j <= Kerbs.FINE; j++) {
                same &= kerbField[i * (Kerbs.FINE + 1) + j] == kerb[(kerbX * Kerbs.FINE + i) * Kerbs.POINTS + kerbY * Kerbs.FINE + j];
            }
        }
        if (!same) {
            for (int i = 0; i <= Kerbs.FINE; i++) {
                for (int j = 0; j <= Kerbs.FINE; j++) {
                    kerbField[i * (Kerbs.FINE + 1) + j] = kerb[(kerbX * Kerbs.FINE + i) * Kerbs.POINTS + kerbY * Kerbs.FINE + j];
                }
            }
            System.arraycopy(face, 0, split[0], 0, 4 * CORNER_FLOATS);
            steps = 0;
            int keep = kerbKeep;
            kerbKeep = BOTH;
            cutting = true;
            split(mesh, frame, tileX, tileY, 0, 4, 0, 0, Kerbs.FINE);
            cutting = false;
            kerbKeep = keep;
            stepsKnown = true;
        }
        System.arraycopy(face, 0, split[0], 0, 4 * CORNER_FLOATS);
        for (int at = 0; at < steps; at++) {
            float[] from = split[stepFrom[at]];
            if (stepKind[at] == PIECE) {
                int side = stepNext[at];
                if (kerbKeep == BOTH || kerbKeep == side) {
                    kerbSide = side;
                    fan(mesh, frame, tileX, tileY, 0, from, stepCurrent[at]);
                    kerbSide = BOTH;
                }
                continue;
            }
            float[] to = split[stepTo[at]];
            int current = stepCurrent[at], next = stepNext[at], into = stepInto[at];
            if (stepKind[at] == COPIED) {
                System.arraycopy(from, current, to, into, CORNER_FLOATS);
            } else {
                float t = stepMix[at];
                for (int k = 0; k < CORNER_FLOATS; k++) {
                    to[into + k] = from[current + k] + (from[next + k] - from[current + k]) * t;
                }
            }
        }
    }

    /** Keeps a step of split's over the whole square, for laySquare: see COPIED. */
    private void step(int kind, float[] from, float[] to, int current, int next, int into, float mix) {
        if (steps == stepKind.length) {
            int more = steps * 2;
            stepKind = Arrays.copyOf(stepKind, more);
            stepFrom = Arrays.copyOf(stepFrom, more);
            stepTo = Arrays.copyOf(stepTo, more);
            stepCurrent = Arrays.copyOf(stepCurrent, more);
            stepNext = Arrays.copyOf(stepNext, more);
            stepInto = Arrays.copyOf(stepInto, more);
            stepMix = Arrays.copyOf(stepMix, more);
        }
        stepKind[steps] = kind;
        stepFrom[steps] = splitOf(from);
        stepTo[steps] = to == null ? 0 : splitOf(to);
        stepCurrent[steps] = current;
        stepNext[steps] = next;
        stepInto[steps] = into;
        stepMix[steps++] = mix;
    }

    private int splitOf(float[] array) {
        for (int i = 0; i < split.length; i++) {
            if (split[i] == array) {
                return i;
            }
        }
        throw new IllegalStateException("a kerb's piece is in none of split's arrays");
    }

    /**
     * Sutherland-Hodgman against the line where perA * a + perB * b + atOrigin is nothing,
     * keeping the side where it is above nothing or the side where it is not.
     */
    private int cut(float[] from, int count, float[] to, float perA, float perB, float atOrigin, boolean keepAbove) {
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
                if (cutting) {
                    step(COPIED, from, to, current, 0, out * CORNER_FLOATS, 0f);
                }
                out++;
            }
            if (currentIn != nextIn) {
                float t = currentValue / (currentValue - nextValue);
                int at = out * CORNER_FLOATS;
                if (cutting) {
                    step(MIXED, from, to, current, next, at, t);
                }
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
        edgeU1 = edgeV1 = edgeU2 = edgeV2 = 0f;
        drawnInFirst = false;
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
        fan(mesh, frame, tileX, tileY, tileZ, from, count);
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
        wasCut = false;
        boolean inside = true;
        for (int edge = 0; edge < 4; edge++) {
            int axis = edge < 2 ? CORNER_U : CORNER_V;
            boolean keepAbove = (edge & 1) == 0;
            float furthest = corners[axis];
            for (int corner = 1; corner < 4; corner++) {
                float value = corners[corner * CORNER_FLOATS + axis];
                furthest = keepAbove ? Math.min(furthest, value) : Math.max(furthest, value);
            }
            // A face drawn in before its cut is cut where its texels are kept inside the sprite's
            // rows, so none of its corners is moved onto them afterwards: see addFace.
            float kept = drawnInFirst ? HALF_PIXEL : 0f;
            float side = switch (edge) {
                case 0 -> frame.left;
                case 1 -> frame.left + frame.width;
                case 2 -> frame.top + kept;
                default -> frame.top + frame.height - kept;
            };
            float across = slack > 0f ? slack + runOn : 0f;
            float limit = axis == CORNER_U ? side + (keepAbove ? -across : across) : Math.abs(furthest - side) <= slack ? furthest : side;
            limits[edge] = limit;
            inside &= keepAbove ? furthest >= limit : furthest <= limit;
        }
        // Most faces are wholly on the sprite: they come through as they are, with no pass over their corners.
        if (inside) {
            result = clipped;
            return 4;
        }
        float[] from = clipped;
        float[] to = spare;
        int count = 4;
        for (int edge = 0; edge < 4 && count > 0; edge++) {
            int axis = edge < 2 ? CORNER_U : CORNER_V;
            count = clip(from, count, to, axis, limits[edge], (edge & 1) == 0);
            float[] swap = from;
            from = to;
            to = swap;
        }
        result = from;
        return count;
    }

    /** What Frame.flat says: the sprite's own texels, a floor laid flat, a wall stood square along its square's north edge or its west, or a square of rug drawn again (Rugs). */
    static final int FLAT_FLOOR = 1, FLAT_NORTH = 2, FLAT_WEST = 3, FLAT_RUG = 5;
    /** In Frame.flat while addGiven adds: a corner's texel is what the corner was given with. */
    private static final int GIVEN = 4;

    /**
     * Adds a triangle lying on the ground of the square, its three corners given from the
     * float at of given, each as where on the square it is, along x and y, and the two numbers
     * the picture is handed where a texel's place would be. A corner may lie past the square's
     * edge: it is then as high as the edge is there. Lit, tilted and stepped down a kerb as a
     * face of the square's floor is.
     */
    void addGiven(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ, float[] given, int at) {
        for (int corner = 0; corner < 3; corner++) {
            int from = at + corner * 4, to = corner * CORNER_FLOATS;
            clipped[to] = given[from];
            clipped[to + 1] = given[from + 1];
            clipped[to + 2] = 0f;
            clipped[to + CORNER_U] = given[from + 2];
            clipped[to + CORNER_V] = given[from + 3];
        }
        int flat = frame.flat;
        frame.flat = GIVEN;
        mesh.open = frame.open + frame.kind + 32f * frame.material;
        mesh.face(-(lift[1] - lift[0] + lift[2] - lift[3]) / 2f, -(lift[3] - lift[0] + lift[2] - lift[1]) / 2f, 1f);
        if (kerb != null && tileZ == 0) {
            System.arraycopy(clipped, 0, split[0], 0, 3 * CORNER_FLOATS);
            split(mesh, frame, tileX, tileY, 0, 3, 0, 0, Kerbs.FINE);
        } else {
            fan(mesh, frame, tileX, tileY, tileZ, clipped, 3);
        }
        frame.flat = flat;
    }

    /** The face in corners as it is, into result: a flat picture covers its whole square. */
    private int whole() {
        System.arraycopy(corners, 0, clipped, 0, 4 * CORNER_FLOATS);
        result = clipped;
        wasCut = false;
        return 4;
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
        float up = TILES_PER_PIXEL_UP / frame.scale * upright;

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
        // What grows moves with the wind, the more the higher it stands above its root.
        float low = sways ? Math.clamp(bottomHeight * SWAY_PER_TILE, 0f, 1f) : 0f;
        float high = sways ? Math.clamp(topHeight * SWAY_PER_TILE, 0f, 1f) : 0f;
        float uLeft = frame.uStart, uRight = frame.uEnd;
        if (!faces) {
            // Cut to the room, short of each side of it by as far as the wind can carry the card's top.
            float swayed = SWAY_REACH * high;
            float dx = x1 - x0, dy = y1 - y0;
            float from = Math.max(enters(x0, dx, tileX + roomLowA + swayed, tileX + roomHighA - swayed), enters(y0, dy, tileY + roomLowB + swayed, tileY + roomHighB - swayed));
            float to = Math.min(leaves(x0, dx, tileX + roomLowA + swayed, tileX + roomHighA - swayed), leaves(y0, dy, tileY + roomLowB + swayed, tileY + roomHighB - swayed));
            if (from >= to) {
                return;
            }
            float uSpan = frame.uEnd - frame.uStart;
            if (to < 1f) {
                x1 = x0 + dx * to;
                y1 = y0 + dy * to;
                uRight = frame.uStart + uSpan * to;
            }
            if (from > 0f) {
                x0 += dx * from;
                y0 += dy * from;
                uLeft = frame.uStart + uSpan * from;
            }
        }

        // A flat card stands in for something round, a bush or a chair, so it is lit as if it
        // leaned back: partly from the side it faces and partly from above.
        mesh.open = frame.open + CARD + 32f * frame.material;
        mesh.face(rightY, -rightX, 0.9f);
        mesh.faces = faces;
        mesh.clears = !faces && clear != 0f;
        mesh.nearer = nearer;
        mesh.sway = low;
        mesh.along = along0;
        mesh.add(x0, y0, baseUp + bottomHeight, uLeft, vBottom, frame.red, frame.green, frame.blue);
        mesh.along = along1;
        mesh.add(x1, y1, baseUp + bottomHeight, uRight, vBottom, frame.red, frame.green, frame.blue);
        mesh.sway = high;
        mesh.add(x1, y1, baseUp + topHeight, uRight, vTop, frame.red, frame.green, frame.blue);
        mesh.sway = low;
        mesh.along = along0;
        mesh.add(x0, y0, baseUp + bottomHeight, uLeft, vBottom, frame.red, frame.green, frame.blue);
        mesh.sway = high;
        mesh.along = along1;
        mesh.add(x1, y1, baseUp + topHeight, uRight, vTop, frame.red, frame.green, frame.blue);
        mesh.along = along0;
        mesh.add(x0, y0, baseUp + topHeight, uLeft, vTop, frame.red, frame.green, frame.blue);
        mesh.sway = 0f;
        mesh.along = 0f;
        mesh.nearer = 0f;
        mesh.faces = false;
        mesh.clears = false;
    }

    /** Where along from + way * t, for t from 0 to 1, the line comes into low..high: 0 if it starts inside, and no earlier than leaves says it leaves if it is never in. */
    private static float enters(float from, float way, float low, float high) {
        if (way == 0f) {
            return from >= low && from <= high ? 0f : Float.MAX_VALUE;
        }
        return Math.max((way > 0f ? low - from : high - from) / way, 0f);
    }

    /** Where along the same line it leaves low..high: 1 if it ends inside. */
    private static float leaves(float from, float way, float low, float high) {
        if (way == 0f) {
            return from >= low && from <= high ? 1f : -Float.MAX_VALUE;
        }
        return Math.min((way > 0f ? high - from : low - from) / way, 1f);
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
        float u = textureU(frame, inA, inB);
        float v = textureV(frame, inA, inB, 0f);
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
        plantU = textureU(frame, inA, inB);
        plantV = textureV(frame, inA, inB, 0f);
        plantX = tileX + a;
        plantY = tileY + b;
        plantUp = liftAt(a, b);
    }

    /** Stands the plant whose strips are added next on the point (a, b) of the square, coloured from the texture's point (u, v). */
    void plant(Frame frame, int tileX, int tileY, float a, float b, float u, float v) {
        plant(frame, tileX, tileY, a, b);
        plantU = u;
        plantV = v;
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
     * height over the root added, which is how far the wind moves it. Where a card's vertex
     * carries how it stands off its plane they carry the way along the ground to the plant's
     * root: lying snow is as deep for the whole plant as it is there.
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
        if (at > mesh.floats) {
            mesh.growing();
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
        to[at + 12] = plantX - x;
        to[at + 13] = plantY - y;
        to[at + 14] = 0f;
        to[at + ROOT_AT] = NO_ROOT;
        to[at + ROOT_AT + 1] = NO_ROOT;
        to[at + UNLIT_AT] = AS_FACE;
        to[at + DAY_AT] = 0f;
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
    /**
     * By how many sides it has, the cosine and sine at each corner round a post, from the
     * diagonal on, and at the middle of each side; and at each corner round a round thing
     * (addRound), from along x on.
     */
    private static final float[][] POST_COS = new float[MOST_POST_SIDES + 1][], POST_SIN = new float[MOST_POST_SIDES + 1][];
    private static final float[][] POST_MIDDLE_COS = new float[MOST_POST_SIDES + 1][], POST_MIDDLE_SIN = new float[MOST_POST_SIDES + 1][];
    static final float[][] ROUND_COS = new float[MOST_POST_SIDES + 1][], ROUND_SIN = new float[MOST_POST_SIDES + 1][];

    static {
        for (int sides = 3; sides <= MOST_POST_SIDES; sides++) {
            POST_COS[sides] = new float[sides + 1];
            POST_SIN[sides] = new float[sides + 1];
            POST_MIDDLE_COS[sides] = new float[sides];
            POST_MIDDLE_SIN[sides] = new float[sides];
            ROUND_COS[sides] = new float[sides + 1];
            ROUND_SIN[sides] = new float[sides + 1];
            for (int side = 0; side <= sides; side++) {
                float angle = DIAGONAL + side * 2f * (float) Math.PI / sides;
                POST_COS[sides][side] = (float) Math.cos(angle);
                POST_SIN[sides][side] = (float) Math.sin(angle);
                if (side < sides) {
                    float middle = (angle + (DIAGONAL + (side + 1) * 2f * (float) Math.PI / sides)) / 2f;
                    POST_MIDDLE_COS[sides][side] = (float) Math.cos(middle);
                    POST_MIDDLE_SIN[sides][side] = (float) Math.sin(middle);
                }
                double round = side * 2.0 * Math.PI / sides;
                ROUND_COS[sides][side] = (float) Math.cos(round);
                ROUND_SIN[sides][side] = (float) Math.sin(round);
            }
        }
    }

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
        float[] postCos = POST_COS[sides], postSin = POST_SIN[sides], postMiddleCos = POST_MIDDLE_COS[sides], postMiddleSin = POST_MIDDLE_SIN[sides];
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
        mesh.litAt(frame, w0, w1, w2, w3);
        int stations = post.heights.length;
        float highest = Math.max(top, post.heights[stations - 1]);
        // Each corner round a station is in the faces of two sides, and of the stations either side of it: where it is and its texel are worked out once.
        int around = sides + 1;
        if (cornerX.length < stations * around) {
            cornerX = new float[stations * around];
            cornerY = new float[stations * around];
            cornerUp = new float[stations * around];
            cornerU = new float[stations * around];
            cornerV = new float[stations * around];
        }
        for (int station = 0; station < stations; station++) {
            float h = station == stations - 1 ? highest : post.heights[station], r = post.halves[station] * out;
            for (int at = 0; at <= sides; at++) {
                int corner = station * around + at;
                float da = postCos[at] * r, db = postSin[at] * r;
                cornerX[corner] = x + da;
                cornerY[corner] = y + db;
                cornerUp[corner] = foot + h * ViewCamera.LEVEL_HEIGHT;
                cornerU[corner] = texelU;
                cornerV[corner] = texelV;
                if (!oneTexel) {
                    cornerV[corner] = postTexel(frame, a, b, da, db, h, highest, postAt);
                    cornerU[corner] = postAt[0];
                }
            }
        }
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
            if (round) {
                for (int at = 0; at <= sides; at++) {
                    float faceX = postCos[at] * flat, faceY = postSin[at] * flat;
                    float faceLength = (float) Math.sqrt(faceX * faceX + faceY * faceY + upward * upward);
                    facingX[at] = faceX / faceLength;
                    facingY[at] = faceY / faceLength;
                    facingUp[at] = upward / faceLength;
                }
            }
            for (int side = 0; side < sides; side++) {
                if (!round) {
                    mesh.face(postMiddleCos[side] * flat, postMiddleSin[side] * flat, upward);
                }
                for (int corner = 0; corner < 6; corner++) {
                    // Two triangles: low from, low to, high to, then low from, high to, high from.
                    boolean far = corner == 1 || corner == 2 || corner == 4, high = corner == 2 || corner >= 4;
                    int at = far ? side + 1 : side, of = (high ? station + 1 : station) * around + at;
                    if (round) {
                        mesh.faceUnit(facingX[at], facingY[at], facingUp[at]);
                    }
                    mesh.add(cornerX[of], cornerY[of], cornerUp[of], cornerU[of], cornerV[of], red, green, blue);
                }
            }
        }
        if (!capped) {
            mesh.litAsFace();
            return;
        }
        float middleU = texelU, middleV = texelV;
        if (!oneTexel) {
            middleV = postTexel(frame, a, b, 0f, 0f, highest, highest, postAt);
            middleU = postAt[0];
        }
        mesh.face(0f, 0f, 1f);
        for (int side = 0; side < sides; side++) {
            int from = (stations - 1) * around + side;
            mesh.add(x, y, foot + highest * ViewCamera.LEVEL_HEIGHT, middleU, middleV, red, green, blue);
            mesh.add(cornerX[from], cornerY[from], cornerUp[from], cornerU[from], cornerV[from], red, green, blue);
            mesh.add(cornerX[from + 1], cornerY[from + 1], cornerUp[from + 1], cornerU[from + 1], cornerV[from + 1], red, green, blue);
        }
        mesh.litAsFace();
    }

    /** Each corner of the post being added, station by station and round each: where it is, and its texel. */
    private float[] cornerX = new float[0], cornerY = new float[0], cornerUp = new float[0], cornerU = new float[0], cornerV = new float[0];
    /** The way a round post being added faces at each corner round one station. */
    private final float[] facingX = new float[MOST_POST_SIDES + 1], facingY = new float[MOST_POST_SIDES + 1], facingUp = new float[MOST_POST_SIDES + 1];

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
     * Adds a round thing standing upright on (x, y) from up0 to up1, all in tiles, r0 thick at
     * its foot and r1 at its top and closed there, every vertex with the one texel (u, v).
     */
    static void addRound(Mesh mesh, float x, float y, float up0, float up1, float r0, float r1, int sides, float u, float v,
            float red, float green, float blue) {
        float rise = up1 - up0, lean = r0 - r1, length = (float) Math.sqrt(rise * rise + lean * lean);
        float flat = rise / length, upward = lean / length;
        float[] cosAround = ROUND_COS[sides], sinAround = ROUND_SIN[sides];
        for (int side = 0; side < sides; side++) {
            for (int corner = 0; corner < 6; corner++) {
                // Two triangles: low from, low to, high to, then low from, high to, high from.
                boolean far = corner == 1 || corner == 2 || corner == 4, high = corner == 2 || corner >= 4;
                float cos = cosAround[far ? side + 1 : side], sin = sinAround[far ? side + 1 : side], r = high ? r1 : r0;
                mesh.faceUnit(cos * flat, sin * flat, upward);
                mesh.add(x + cos * r, y + sin * r, high ? up1 : up0, u, v, red, green, blue);
            }
        }
        mesh.face(0f, 0f, 1f);
        for (int side = 0; side < sides; side++) {
            mesh.add(x, y, up1, u, v, red, green, blue);
            mesh.add(x + cosAround[side] * r1, y + sinAround[side] * r1, up1, u, v, red, green, blue);
            mesh.add(x + cosAround[side + 1] * r1, y + sinAround[side + 1] * r1, up1, u, v, red, green, blue);
        }
    }

    /**
     * Adds a round bar from one point to another, each (x, y, up) in tiles, closed at both ends,
     * every vertex with the one texel (u, v) = (0, 0).
     */
    static void addTube(Mesh mesh, float[] from, float[] to, float radius, int sides, float red, float green, float blue) {
        float ax = to[0] - from[0], ay = to[1] - from[1], az = to[2] - from[2];
        float length = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= length;
        ay /= length;
        az /= length;
        // Across it, level, and then square to both.
        float flat = (float) Math.sqrt(ax * ax + ay * ay);
        float sx = -ay / flat, sy = ax / flat;
        float tx = -az * sy, ty = az * sx, tz = ax * sy - ay * sx;
        float[] cosAround = ROUND_COS[sides], sinAround = ROUND_SIN[sides];
        for (int side = 0; side < sides; side++) {
            for (int corner = 0; corner < 6; corner++) {
                boolean next = corner == 1 || corner == 2 || corner == 4, far = corner == 2 || corner >= 4;
                float cos = cosAround[next ? side + 1 : side], sin = sinAround[next ? side + 1 : side];
                float nx = sx * cos + tx * sin, ny = sy * cos + ty * sin, nz = tz * sin;
                float[] at = far ? to : from;
                mesh.faceUnit(nx, ny, nz);
                mesh.add(at[0] + nx * radius, at[1] + ny * radius, at[2] + nz * radius, 0f, 0f, red, green, blue);
            }
        }
        for (int end = 0; end < 2; end++) {
            float[] at = end == 0 ? from : to;
            float way = end == 0 ? -1f : 1f;
            mesh.faceUnit(ax * way, ay * way, az * way);
            for (int side = 0; side < sides; side++) {
                for (int corner = 0; corner < 3; corner++) {
                    float cos = corner == 0 ? 0f : cosAround[side + corner - 1], sin = corner == 0 ? 0f : sinAround[side + corner - 1];
                    float r = corner == 0 ? 0f : radius;
                    mesh.add(at[0] + (sx * cos + tx * sin) * r, at[1] + (sy * cos + ty * sin) * r, at[2] + tz * sin * r, 0f, 0f, red, green, blue);
                }
            }
        }
    }

    /**
     * Adds a square timber from one point to another, each (x, y, up) in tiles, half thick each
     * way from its middle line, closed at both ends, every vertex with the one texel (u, v): a
     * brace from a post up to the beam it carries.
     */
    void addStrut(Mesh mesh, float[] from, float[] to, float half, float u, float v, float red, float green, float blue) {
        float ax = to[0] - from[0], ay = to[1] - from[1], az = to[2] - from[2];
        float length = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= length;
        ay /= length;
        az /= length;
        // Across it, level, and then square to both.
        float flat = (float) Math.sqrt(ax * ax + ay * ay);
        float sx = flat < 1e-4f ? 1f : -ay / flat, sy = flat < 1e-4f ? 0f : ax / flat;
        float tx = -az * sy, ty = az * sx, tz = ax * sy - ay * sx;
        float[] c = strutCorners;
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
        mesh.litAt(frame, w0, w1, w2, w3);
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
            float[] cosAround = POST_COS[sides], sinAround = POST_SIN[sides];
            for (int side = 0; side < sides; side++) {
                float cos0 = cosAround[side], sin0 = sinAround[side], cos1 = cosAround[side + 1], sin1 = sinAround[side + 1];
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
        mesh.litAsFace();
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
    private int clip(float[] from, int count, float[] to, int axis, float limit, boolean keepAbove) {
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
            } else {
                wasCut = true;
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

    /** Whether the face being added stands and was drawn smaller onto itself before it was cut: see addFace. */
    private boolean drawnInFirst;

    /**
     * Moves the texel of a point of a face that stands in towards the face's middle. The face
     * is drawn smaller onto itself about its middle, so every point of it moves in by its
     * share and not by the whole inset: a pipe that runs along a wall from one square's sprite
     * to the next does not step where one sprite or the other ends.
     */
    private void standIn(Frame frame, float[] polygon, int at) {
        float inset = EDGE_INSET_PIXELS * frame.scale;
        float pixelU = polygon[at + CORNER_U], pixelV = polygon[at + CORNER_V];
        float insetAcross = (exactAcross ? 0f : inset) + (pixelU < centreU ? sealLow : sealHigh);
        float fromCentre = pixelU - centreU;
        // How far below the face's middle the point is, with the slant of its courses taken out.
        float below = pixelV - centreV - fromCentre * alongSlant;
        float movedAcross = -Math.clamp(fromCentre / halfAcross, -1f, 1f) * insetAcross;
        // A face less than four insets tall, the side of a coping, is drawn no further onto
        // itself than half its height: moved past its middle course, its two ends would take
        // their texels from courses either side of it, and it would lean across its square.
        float insetDown = Math.min(inset, halfDown / 2f) + (below < 0f ? sealTop : 0f);
        // A wall's courses run across its sprite at a slant. Moved across alone, a corner of
        // the face would take its texels from a course up or down, and the boards of one wall
        // would step where it meets the next: the move follows the slant.
        polygon[at + CORNER_U] = pixelU + movedAcross;
        polygon[at + CORNER_V] = pixelV + movedAcross * alongSlant - Math.clamp(below / halfDown, -1f, 1f) * insetDown;
    }

    private void emit(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ, float[] polygon, int index) {
        int at = index * CORNER_FLOATS;
        float pixelU = polygon[at + CORNER_U];
        float pixelV = polygon[at + CORNER_V];
        if (frame.flat == FLAT_FLOOR) {
            emitAt(mesh, frame, tileX, tileY, tileZ, polygon, at, Flats.FROM + Flats.SPAN * polygon[at], Flats.FROM + Flats.SPAN * polygon[at + 1]);
            return;
        }
        if (frame.flat == FLAT_RUG) {
            emitAt(mesh, frame, tileX, tileY, tileZ, polygon, at, Rugs.FROM + Rugs.SPAN * polygon[at], Rugs.FROM + Rugs.SPAN * polygon[at + 1]);
            return;
        }
        if (frame.flat == GIVEN) {
            emitAt(mesh, frame, tileX, tileY, tileZ, polygon, at, pixelU, pixelV);
            return;
        }
        if (frame.flat != 0) {
            emitAt(mesh, frame, tileX, tileY, tileZ, polygon, at, Flats.WALL_FROM + Flats.WALL_SPAN * polygon[at + (frame.flat == FLAT_NORTH ? 0 : 1)], 1f - polygon[at + 2]);
            return;
        }
        // Across and down separately: a wall's side edges are upright, and an inset towards the
        // face's centre would run along them without leaving the sprite's empty edge columns.
        float inset = insetPixels(drawnIn) * frame.scale;
        float insetAcross = (exactAcross ? 0f : inset) + (pixelU < centreU ? sealLow : sealHigh);
        // A face that stands was drawn in before it was cut: see addFace. One too narrow for that,
        // the piece of wall beside a door, is moved in across with its seals: drawn in along its own
        // edges, its far end took the dots the sprite stops short by, and the sky showed up the wall.
        float keptIn = HALF_PIXEL;
        if (!drawnInFirst && Math.abs(sheet) > 1e-3f && halfAcross == 0f) {
            // A face that lies or leans is drawn smaller onto itself along its own two edges, so
            // its edges keep the slant the sprite paints them at. Moved towards its middle by
            // the frame's rows and columns, a long narrow face, a coping's top, would take its
            // texels from across its own width at a slant and step where it meets the next.
            float dU = pixelU - centreU, dV = pixelV - centreV;
            float s = Math.clamp(2f * (dU * edgeV2 - dV * edgeU2) / sheet, -1f, 1f), t = Math.clamp(2f * (edgeU1 * dV - edgeV1 * dU) / sheet, -1f, 1f);
            pixelU -= s * in1 * edgeU1 + t * in2 * edgeU2;
            pixelV -= s * in1 * edgeV1 + t * in2 * edgeV2;
        } else if (!drawnInFirst) {
            float movedAcross = Math.clamp(centreU - pixelU, -insetAcross, insetAcross);
            pixelU += movedAcross;
            pixelV += movedAcross * alongSlant;
            pixelV += Math.clamp(centreV - pixelV, -inset, inset);
            // Its texels are kept the inset inside the sprite's side as well: the piece of wall
            // between a door and a corner lies mostly past its sprite, took the side's half
            // empty column all across, and was cut through to the sky where it met the corner.
            keptIn = exactAcross ? HALF_PIXEL : Math.min(inset, frame.width / 2f);
        }
        float keptU = Math.clamp(pixelU, frame.left + keptIn, frame.left + frame.width - keptIn);
        // On a face that stands straight up, a texel kept inside the sprite's side stays on
        // its own course: left on its row, the courses of the face would tilt towards that side.
        if (halfAcross > 0f) {
            pixelV += (keptU - pixelU) * alongSlant;
        }
        pixelU = keptU;
        // A face drawn in before its cut is on the sprite's rows already: kept inside its side
        // above, a corner moves along its course, and pulled back onto the top row as well it
        // would bend the face's texels again.
        if (!drawnInFirst) {
            pixelV = Math.clamp(pixelV, frame.top + HALF_PIXEL, frame.top + frame.height - HALF_PIXEL);
        }
        float u = frame.uStart + (pixelU - frame.left) / frame.width * (frame.uEnd - frame.uStart);
        float v = frame.vStart + (pixelV - frame.top) / frame.height * (frame.vEnd - frame.vStart);
        emitAt(mesh, frame, tileX, tileY, tileZ, polygon, at, u, v);
    }

    /** Adds the corner of a face that starts at the given float of polygon, with the texel (u, v). */
    private void emitAt(Mesh mesh, Frame frame, int tileX, int tileY, int tileZ, float[] polygon, int at, float u, float v) {
        if (!Float.isNaN(jointStep)) {
            u = (polygon[at] * alongA + polygon[at + 1] * alongB - jointPhase) / jointStep;
            v = footV + (polygon[at + 2] - footH) * perLevel + (polygon[at] * acrossA + polygon[at + 1] * acrossB) / jointStep;
        }
        float a = Math.clamp(polygon[at], 0f, 1f), b = Math.clamp(polygon[at + 1], 0f, 1f);
        float w0 = (1f - a) * (1f - b), w1 = a * (1f - b), w2 = a * b, w3 = (1f - a) * b;
        mesh.sway = cover == null ? 0f : 1f - (cover.length == GRID_COVER ? gridCover(cover, a, b) : cover[0] * w0 + cover[1] * w1 + cover[2] * w2 + cover[3] * w3);
        mesh.litAt(frame, w0, w1, w2, w3);
        float shade = creaseLow || creaseHigh ? creased(polygon[at], polygon[at + 1]) : 1f;
        mesh.add(tileX + polygon[at] + shownA, tileY + polygon[at + 1] + shownB,
                (tileZ + polygon[at + 2] + shownUp + raised) * ViewCamera.LEVEL_HEIGHT + liftAt(polygon[at], polygon[at + 1]), u, v,
                (frame.cornerRed[0] * w0 + frame.cornerRed[1] * w1 + frame.cornerRed[2] * w2 + frame.cornerRed[3] * w3) * shade,
                (frame.cornerGreen[0] * w0 + frame.cornerGreen[1] * w1 + frame.cornerGreen[2] * w2 + frame.cornerGreen[3] * w3) * shade,
                (frame.cornerBlue[0] * w0 + frame.cornerBlue[1] * w1 + frame.cornerBlue[2] * w2 + frame.cornerBlue[3] * w3) * shade);
        mesh.litAsFace();
    }

    /** How much of its light the point (a, b) of the square has, on a wall skin that ends in an inside corner. */
    float creased(float a, float b) {
        float along = creaseAlongA ? a : b;
        float out = Math.min(creaseLow ? along : 1f, creaseHigh ? 1f - along : 1f) - WorldScene.WALL_HALF;
        return 1f - (1f - CREASE_SHADE) * Math.clamp(1f - out / CREASE_REACH, 0f, 1f);
    }

    /** How much of the face shows at (a, b) of the square, from a cover of GRID_COVER: within the quarter of the square the point is in. */
    static float gridCover(float[] cover, float a, float b) {
        int i = a < 0.5f ? 0 : 1, j = b < 0.5f ? 0 : 1;
        float u = a * 2f - i, v = b * 2f - j;
        int at = j * 3 + i;
        return cover[at] * (1f - u) * (1f - v) + cover[at + 1] * u * (1f - v) + cover[at + 4] * u * v + cover[at + 3] * (1f - u) * v;
    }

    /** Adds an untextured four-cornered face, its corners given in order round it as x, y and height in tiles. */
    static void addPlainQuad(Mesh mesh, float[] c, float red, float green, float blue) {
        addQuad(mesh, c, 0f, 0f, red, green, blue);
    }

    private static final int[] QUAD = {0, 1, 2, 0, 2, 3};

    /**
     * Adds an untextured face over the whole of a square, its corners (0, 0), (1, 0), (1, 1) and
     * (0, 1) at the given heights in tiles.
     */
    static void addPlainOver(Mesh mesh, int x, int y, float h00, float h10, float h11, float h01, float red, float green, float blue) {
        addOver(mesh, x, y, h00, h10, h11, h01, 0f, 0f, red, green, blue);
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
}
