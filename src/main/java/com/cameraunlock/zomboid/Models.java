package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * The mod's own furniture: models made for it with tools/models/furniture.py and shipped in
 * its jar, which stand in the first-person view where the game has a flat sprite of a bed or
 * a wardrobe.
 *
 * A model is in metres, Z up, its origin on the floor at the centre of its footprint, with +Y
 * the head of a bed and the back of everything else, so its front faces -Y. Each triangle
 * belongs to one surface, which says what it is made of. There are no texture coordinates:
 * the picture gives a surface its colour and lays its grain on by where it is in the world.
 *
 * A model comes twice. The whole one is what is seen from near. The plain one leaves out the
 * narrow bevels and some of the roundness, in about a third of the triangles: it casts the
 * model's shadow, and is drawn where the two are less than a pixel of the picture apart.
 *
 * The file is gzip over this, all little-endian:
 *
 *   4 bytes   the ASCII letters CUFM
 *   uint32    format version, 3
 *   uint32    number of models
 *   per model:
 *     uint8     length of the name, then that many ASCII bytes
 *     float32   x 6: the least x, y, z and the greatest x, y, z of the whole one
 *     float32   the furthest a vertex of either one is from the surface of the other
 *     the whole one, then the plain one, each:
 *       uint32    number of vertices, fewer than 65536
 *       uint32    number of triangles
 *       per vertex, in the order of their surfaces:
 *         float32 x 3   position
 *         float32 x 3   unit normal
 *         uint8         surface: WOOD to VINYL below
 *         uint8         how much of the room's light reaches the vertex past the rest of the
 *                       model and the floor, 255 for all of it
 *       per triangle, in the order of their surfaces:
 *         uint16 x 3    its vertices, counted from the first of this one
 */
final class Models {
    static final int WOOD = 0, DARK_WOOD = 1, CLOTH = 2, LINEN = 3, PILLOW = 4, METAL = 5, PAINT = 6, ENAMEL = 7, STEEL = 8, PLASTIC = 9,
            SCREEN = 10, CERAMIC = 11, BOOKS = 12, SHADE = 13, RUBBER = 14, WORKTOP = 15, PANE = 16, VINYL = 17;
    static final int SURFACES = 18;

    /**
     * What a model's surface is made of, as the picture is told it: this plus the surface. The
     * colour a vertex carries is the light on it, as it is for a sprite, so a surface's own
     * colour is a short table in the picture's shader under these numbers. The other way was
     * to pack a colour into the texture coordinates plain geometry leaves at zero, which needs
     * three numbers in two and a second mark to say the vertex is a model's.
     */
    static final float FIRST_MATERIAL = 16f;
    /**
     * The same surfaces where the game's sprite of the piece says what colour they are: such
     * a vertex is drawn with the sprite's texture and looks at one texel of it. What a frame
     * holds, its pane, is the one surface that shows the sprite itself: dyed, it is the picture
     * the sprite paints, each vertex looking at its own place on it, and plain it is a
     * mirror's glass.
     */
    static final float FIRST_DYED_MATERIAL = FIRST_MATERIAL + SURFACES;

    private static final String RESOURCE = "/models/furniture.bin.gz";
    private static final int MAGIC = 'C' | 'U' << 8 | 'F' << 16 | 'M' << 24;
    private static final int VERSION = 3;
    private static final int VERTEX_BYTES = 6 * Float.BYTES + 2;
    /** Floats per vertex of a Model: its position, then the way it looks. */
    static final int VERTEX_FLOATS = 6;
    /**
     * Floats per vertex in the buffer the graphics card keeps of every model: its position, the
     * way it looks, its surface with the piece of it the vertex is on, and what the light it is given is multiplied by.
     */
    static final int BUFFER_FLOATS = 8;
    /**
     * A vertex's surface goes to the graphics card as the surface plus this times its piece: see Model.piece. The world's
     * vertex shader takes the two apart again.
     */
    static final int PIECE_STEP = 32;
    /** How many different pieces a surface tells apart. */
    static final int PIECES = 4096;

    static final class Model {
        final String name;
        final float minX, minY, minZ, maxX, maxY, maxZ;
        /** VERTEX_FLOATS for each vertex, which is here once however many triangles meet at it. */
        final float[] vertices;
        /** What each vertex is made of. They are in the order of their surfaces. */
        final byte[] surface;
        /** The first vertex of each surface, and after the last surface the number of vertices. */
        final int[] first = new int[SURFACES + 1];
        /** The vertices of each triangle, three to a triangle, counted from the model's first. */
        final int[] corners;
        /** The model's first vertex in the buffer of every model: see buffer. */
        final int start;
        /** The model's first corner in the list of every model's: see corners. */
        final int from;
        /** The furthest a vertex of this model or of its plain one is from the surface of the other, in metres. */
        final float apart;
        /** The same thing in fewer triangles, or null if this is the plain one. */
        final Model plain;
        /** For each vertex, how much of the room's light reaches it, 0 to 1. */
        final float[] reached;
        /**
         * What the light a vertex carries is multiplied by to leave that share of it. The
         * picture reads the colour a vertex carries as light to the power 2.2, so this is the
         * share to the power 1 / 2.2.
         */
        final float[] dimmed;
        /**
         * Which piece of the model each vertex is on, for the surfaces whose pieces differ from each other: the books on
         * a shelf are each a colour of their own. A piece is the triangles that meet at their corners, a book's six
         * faces, and is known by where its lowest corner is, so the plain model's books are the whole one's. 0 for
         * every other surface.
         */
        final int[] piece;

        private Model(String name, float[] bounds, float apart, Shape shape, int start, int from, Model plain) {
            this.name = name;
            this.start = start;
            this.from = from;
            this.apart = apart;
            this.plain = plain;
            float[] vertices = shape.vertices;
            byte[] surface = shape.surface;
            float[] reached = shape.reached;
            corners = shape.corners;
            minX = bounds[0];
            minY = bounds[1];
            minZ = bounds[2];
            maxX = bounds[3];
            maxY = bounds[4];
            maxZ = bounds[5];
            this.vertices = vertices;
            this.surface = surface;
            this.reached = reached;
            dimmed = new float[reached.length];
            for (int i = 0; i < reached.length; i++) {
                dimmed[i] = (float) Math.pow(reached[i], 1.0 / 2.2);
            }
            for (int i = 0; i < surface.length; i++) {
                first[surface[i] + 1] = i + 1;
            }
            for (int i = 1; i <= SURFACES; i++) {
                first[i] = Math.max(first[i], first[i - 1]);
            }
            piece = pieces(vertices, corners, first[BOOKS], first[BOOKS + 1]);
        }

        int triangles() {
            return corners.length / 3;
        }

        /** From side to side, in metres. */
        float width() {
            return maxX - minX;
        }

        /** From front to back, in metres. */
        float depth() {
            return maxY - minY;
        }

        float height() {
            return maxZ - minZ;
        }
    }

    /**
     * The piece each vertex from one to before another is on, as Model.piece says, and 0 for every other vertex. The
     * same corner of two faces is two vertices where the faces are flat, so corners are joined by where they are.
     */
    static int[] pieces(float[] vertices, int[] corners, int from, int to) {
        int[] piece = new int[vertices.length / VERTEX_FLOATS];
        if (from == to) {
            return piece;
        }
        int[] joined = new int[to - from];
        for (int i = 0; i < joined.length; i++) {
            joined[i] = i;
        }
        Map<List<Float>, Integer> at = new HashMap<>();
        for (int vertex = from; vertex < to; vertex++) {
            Integer same = at.putIfAbsent(List.of(vertices[vertex * VERTEX_FLOATS], vertices[vertex * VERTEX_FLOATS + 1], vertices[vertex * VERTEX_FLOATS + 2]),
                    vertex - from);
            if (same != null) {
                join(joined, same, vertex - from);
            }
        }
        for (int corner = 0; corner < corners.length; corner += 3) {
            if (corners[corner] >= from && corners[corner] < to) {
                join(joined, corners[corner] - from, corners[corner + 1] - from);
                join(joined, corners[corner] - from, corners[corner + 2] - from);
            }
        }
        // A piece's lowest corner: least z, then y, then x.
        int[] lowest = new int[joined.length];
        for (int i = 0; i < joined.length; i++) {
            lowest[i] = -1;
        }
        for (int i = 0; i < joined.length; i++) {
            int root = root(joined, i), held = lowest[root];
            if (held < 0 || below(vertices, from + i, from + held)) {
                lowest[root] = i;
            }
        }
        for (int i = 0; i < joined.length; i++) {
            int low = (from + lowest[root(joined, i)]) * VERTEX_FLOATS;
            int key = Arrays.hashCode(new float[] {vertices[low], vertices[low + 1], vertices[low + 2]});
            piece[from + i] = Math.floorMod(key, PIECES);
        }
        return piece;
    }

    private static boolean below(float[] vertices, int a, int b) {
        for (int k = 2; k >= 0; k--) {
            float x = vertices[a * VERTEX_FLOATS + k], y = vertices[b * VERTEX_FLOATS + k];
            if (x != y) {
                return x < y;
            }
        }
        return false;
    }

    private static int root(int[] joined, int i) {
        while (joined[i] != i) {
            joined[i] = joined[joined[i]];
            i = joined[i];
        }
        return i;
    }

    private static void join(int[] joined, int a, int b) {
        joined[root(joined, a)] = root(joined, b);
    }

    /** One of a model's two, as the file holds it. */
    private record Shape(float[] vertices, byte[] surface, float[] reached, int[] corners) {}

    /** Read by the logic thread, which stands the models, and by the render thread, which hands them to the graphics card. */
    private static volatile Map<String, Model> loaded;

    private Models() {}

    /** The model of that name, read from the jar the first time one is asked for. */
    static Model get(String name) {
        Map<String, Model> models = loaded;
        if (models == null) {
            try (InputStream packed = Models.class.getResourceAsStream(RESOURCE)) {
                if (packed == null) {
                    throw new IllegalStateException("the jar has no " + RESOURCE);
                }
                loaded = models = read(packed);
            } catch (IOException e) {
                throw new UncheckedIOException(RESOURCE + " could not be read", e);
            }
        }
        Model model = models.get(name);
        if (model == null) {
            throw new IllegalStateException(RESOURCE + " has no model called " + name);
        }
        return model;
    }

    /** Reads every model out of a packed file, by name. */
    static Map<String, Model> read(InputStream packed) throws IOException {
        ByteBuffer in = ByteBuffer.wrap(new GZIPInputStream(packed).readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
        need(in, 12, "its header");
        if (in.getInt() != MAGIC) {
            throw new IOException("not a file of models: it does not start with CUFM");
        }
        int version = in.getInt();
        if (version != VERSION) {
            throw new IOException("models of format " + version + ", and this reads format " + VERSION);
        }
        int count = in.getInt();
        Map<String, Model> models = new LinkedHashMap<>();
        int start = 0, from = 0;
        for (int i = 0; i < count; i++) {
            need(in, 1, "a model's name");
            byte[] letters = new byte[in.get() & 0xff];
            need(in, letters.length + 7 * Float.BYTES, "a model's name and bounds");
            in.get(letters);
            String name = new String(letters, StandardCharsets.US_ASCII);
            float[] bounds = new float[6];
            for (int k = 0; k < 6; k++) {
                bounds[k] = in.getFloat();
            }
            float apart = in.getFloat();
            Shape whole = shape(in, name), less = shape(in, name);
            Model plain = new Model(name, bounds, apart, less, start + whole.surface.length, from + whole.corners.length, null);
            if (models.put(name, new Model(name, bounds, apart, whole, start, from, plain)) != null) {
                throw new IOException("two models are called " + name);
            }
            start += whole.surface.length + less.surface.length;
            from += whole.corners.length + less.corners.length;
        }
        if (in.hasRemaining()) {
            throw new IOException(in.remaining() + " bytes follow the last model");
        }
        return models;
    }

    private static Shape shape(ByteBuffer in, String name) throws IOException {
        need(in, 2 * Integer.BYTES, "the counts of " + name);
        int vertices = in.getInt(), triangles = in.getInt();
        if (vertices < 0 || vertices >= 1 << 16 || triangles < 0 || (long) vertices * VERTEX_BYTES + (long) triangles * 3 * Short.BYTES > in.remaining()) {
            throw new IOException(name + " says it has " + vertices + " vertices and " + triangles + " triangles, and the file ends before that many");
        }
        float[] places = new float[vertices * VERTEX_FLOATS];
        byte[] surface = new byte[vertices];
        float[] reached = new float[vertices];
        for (int vertex = 0; vertex < vertices; vertex++) {
            for (int k = 0; k < VERTEX_FLOATS; k++) {
                places[vertex * VERTEX_FLOATS + k] = in.getFloat();
            }
            surface[vertex] = in.get();
            if (surface[vertex] < 0 || surface[vertex] >= SURFACES) {
                throw new IOException(name + " has a vertex of surface " + surface[vertex] + ", and there are " + SURFACES);
            }
            reached[vertex] = (in.get() & 0xff) / 255f;
            if (vertex > 0 && surface[vertex] < surface[vertex - 1]) {
                throw new IOException(name + " has its vertices out of the order of their surfaces");
            }
        }
        int[] corners = new int[triangles * 3];
        for (int corner = 0; corner < corners.length; corner++) {
            corners[corner] = in.getShort() & 0xffff;
            if (corners[corner] >= vertices) {
                throw new IOException(name + " has a triangle at vertex " + corners[corner] + ", and it has " + vertices);
            }
        }
        return new Shape(places, surface, reached, corners);
    }

    private static void need(ByteBuffer in, int bytes, String what) throws IOException {
        if (in.remaining() < bytes) {
            throw new IOException("the file ends in " + what);
        }
    }

    /**
     * Turns a direction along the floor of a model into the map's, into out as x then y, for a
     * model stood with its front to the north and then turned quarterTurns quarter turns the
     * way north goes to east. The map's y runs south, so seen from above its axes are the
     * mirror of a model's, and a model's +x is the map's west when it faces north: whoever
     * stands at the foot of a bed looking at its head has the same side of it on their right
     * in the picture as in the model.
     */
    static void turn(int quarterTurns, float x, float y, float[] out) {
        switch (quarterTurns & 3) {
            case 0 -> {
                out[0] = -x;
                out[1] = y;
            }
            case 1 -> {
                out[0] = -y;
                out[1] = -x;
            }
            case 2 -> {
                out[0] = x;
                out[1] = -y;
            }
            default -> {
                out[0] = y;
                out[1] = x;
            }
        }
    }

    /** Every model the jar holds, as the graphics card keeps them: see buffer. Asked for once a piece has been stood, so they are read by then. */
    static float[] buffer() {
        Map<String, Model> models = loaded;
        if (models == null) {
            throw new IllegalStateException("the models are asked for before any was stood");
        }
        return buffer(models.values());
    }

    /** The models one after another, each from its start and its plain one after it, BUFFER_FLOATS to a vertex. */
    static float[] buffer(Collection<Model> models) {
        int vertices = 0;
        for (Model model : models) {
            vertices += model.surface.length + model.plain.surface.length;
        }
        float[] out = new float[vertices * BUFFER_FLOATS];
        for (Model whole : models) {
            for (Model model : new Model[] {whole, whole.plain}) {
                for (int vertex = 0, at = model.start * BUFFER_FLOATS; vertex < model.surface.length; vertex++, at += BUFFER_FLOATS) {
                    System.arraycopy(model.vertices, vertex * VERTEX_FLOATS, out, at, VERTEX_FLOATS);
                    out[at + 6] = model.surface[vertex] + PIECE_STEP * model.piece[vertex];
                    out[at + 7] = model.dimmed[vertex];
                }
            }
        }
        return out;
    }

    /** Every model read so far. */
    static Collection<Model> loadedModels() {
        Map<String, Model> models = loaded;
        if (models == null) {
            throw new IllegalStateException("the models are asked for before any was stood");
        }
        return models.values();
    }

    /** Every model's triangles as the graphics card keeps them: see corners. */
    static int[] corners() {
        Map<String, Model> models = loaded;
        if (models == null) {
            throw new IllegalStateException("the models are asked for before any was stood");
        }
        return corners(models.values());
    }

    /** The triangles of the models one after another, each from where its model says and its plain one's after them, as vertices of buffer. */
    static int[] corners(Collection<Model> models) {
        int corners = 0;
        for (Model model : models) {
            corners += model.corners.length + model.plain.corners.length;
        }
        int[] out = new int[corners];
        for (Model whole : models) {
            for (Model model : new Model[] {whole, whole.plain}) {
                for (int corner = 0; corner < model.corners.length; corner++) {
                    out[model.from + corner] = model.start + model.corners[corner];
                }
            }
        }
        return out;
    }

    /**
     * The quarter turns as the picture's programs are told them, into out from at: what a
     * model's x and y are multiplied by to give the map's x, then what gives the map's y.
     */
    static void turnRows(int quarterTurns, float[] out, int at) {
        float[] to = new float[2];
        turn(quarterTurns, 1f, 0f, to);
        out[at] = to[0];
        out[at + 2] = to[1];
        turn(quarterTurns, 0f, 1f, to);
        out[at + 1] = to[0];
        out[at + 3] = to[1];
    }

    /**
     * The pieces of furniture standing on one chunk. A piece is a few numbers here and its
     * model is on the graphics card once: a street of furnished houses written out vertex by
     * vertex, every frame for the chunks near the eye, was more than the game's memory holds.
     *
     * The floats of a piece are in the order the picture's programs are told them, each group
     * one uniform.
     */
    static final class Instances {
        /** The square that lights the piece, then tiles to a metre of the model along the ground and up. */
        static final int PLACE = 0;
        /** Where the model's origin is from that square's corner, and how high its foot is. */
        static final int FOOT = 4;
        /** The quarter turns, as turnRows gives them. */
        static final int TURN = 7;
        /** The light at the square's first corner, then what is added to it along each edge and across: red, green and blue of each. */
        static final int LIGHT = 11;
        /** How open to the sky the square is, then the surfaces dyed from the first place on the sprite and from the second, a bit for each. */
        static final int SURFACES = 23;
        /** Where on the sprite's texture the first colour is taken, then the second. */
        static final int DYE = 26;
        /** The middle of a ball that holds the whole piece, on the map, and how far the ball reaches. */
        static final int BALL = 30;
        /**
         * For a piece whose pane shows a picture: how far across the sprite's texture and down it
         * a metre along the model's x goes, then a metre up it. The first place the colour is
         * taken is then where the model's origin is on the picture.
         */
        static final int PICTURE = 34;
        /** The furthest the piece's plain model is from its whole one, in tiles. The picture's programs are not told it. */
        static final int APART = 38;
        /**
         * What the piece has of a light the game has not given, as the light is held at LIGHT:
         * at the first corner and added along each edge and across. Then the kind of that light
         * plus 1, or 0 where the piece is lit as its openness says: see Frame.cornerUnlit.
         */
        static final int UNLIT = 39;
        /** What the piece has of the day its room lets in, held as UNLIT holds the rest: see Frame.cornerDay. */
        static final int DAY = 44;
        /** 1 for a piece laid as a wall's own face, which is opened and cut as the wall is (see wall), or 0. */
        static final int WALL = 48;
        static final int FLOATS = 49;
        /**
         * The model's first corner in the list of every model's and how many it has, the same of
         * its plain one, the texture that dyes the piece or 0, and 1 for a piece that is part of
         * a building, which is drawn as far as the building is (see built), or 0.
         */
        static final int FIRST = 0, COUNT = 1, PLAIN_FIRST = 2, PLAIN_COUNT = 3, TEXTURE = 4, BUILT = 5;
        static final int INTS = 6;
        /**
         * How much of a pixel a piece's two models may be apart where the plain one is drawn.
         * A bevel the plain one leaves out shows as the groove between two boards, which is
         * both their bevels and the dark between them: with the two a whole pixel apart a
         * crate's grooves were two pixels wide in one and broken lines in the other.
         */
        static final float SEEN_APART = 0.3f;

        float[] floats = new float[FLOATS * 8];
        int[] ints = new int[INTS * 8];
        /**
         * For each piece, the region it is seen from, as ChunkMeshes.runRoom holds a run's, and
         * in roomsToo the other of a pair.
         */
        long[] rooms = new long[8], roomsToo = new long[8];
        int count;
        /** The room the pieces added next are the contents of: see SpriteGeometry.MeshSet.room. */
        long room = RoomSight.OUTSIDE;
        /** For each piece, the key of the thing it is of (Outlines.key), or Outlines.NONE. */
        long[] keys = new long[8];
        /** The key of the thing the pieces added next are of: see SpriteGeometry.MeshSet.own. */
        long key = Outlines.NONE;

        /**
         * The fires burning in the pieces, Fires.FLOATS each (see Fires.placeHearth), and the room
         * each is in, as rooms holds a piece's.
         */
        float[] fires = new float[0];
        long[] fireRooms = new long[0];
        /**
         * The game's own animation of each fire's flames (an IsoAnim), drawn in its place where the
         * picture draws the game's look. Held as objects: the tests build without the game's classes.
         */
        Object[] fireLooks = new Object[0];
        /** The same of each fire's smoke. */
        Object[] smokeLooks = new Object[0];
        int fireCount;
        /** The glass of each mirror among the pieces, LookingGlass.FLOATS each, and which piece each is. */
        float[] glasses = new float[0];
        int[] glassPieces = new int[0];
        int glassCount;

        void clear() {
            count = 0;
            fireCount = 0;
            glassCount = 0;
            room = RoomSight.OUTSIDE;
            key = Outlines.NONE;
        }

        /** Notes a fire burning in a hearth just stood, as Fires.placeHearth gives it, in the room the pieces added now are in. */
        void fire(float[] hearth, float centreX, float centreY, float foot, int quarterTurns, float scale, float scaleUp, float seed, Object look, Object smokeLook) {
            if ((fireCount + 1) * Fires.FLOATS > fires.length) {
                fires = Arrays.copyOf(fires, Math.max(2, fireCount * 2) * Fires.FLOATS);
                fireRooms = Arrays.copyOf(fireRooms, Math.max(2, fireCount * 2));
                fireLooks = Arrays.copyOf(fireLooks, Math.max(2, fireCount * 2));
                smokeLooks = Arrays.copyOf(smokeLooks, Math.max(2, fireCount * 2));
            }
            Fires.placeHearth(hearth, centreX, centreY, foot, quarterTurns, scale, scaleUp, seed, fires, fireCount * Fires.FLOATS);
            fireLooks[fireCount] = look;
            smokeLooks[fireCount] = smokeLook;
            fireRooms[fireCount++] = room;
        }

        int triangles() {
            int corners = 0;
            for (int i = 0; i < count; i++) {
                corners += ints[i * INTS + COUNT];
            }
            return corners / 3;
        }

        /**
         * Stands a model with the centre of its footprint at (centreX, centreY) on the map and
         * its foot at baseUp, turned by quarterTurns as turn says, one metre of it scale tiles
         * along the ground and scaleUp tiles up.
         *
         * The surfaces whose bit is set in dyedSurfaces look at the texel (dyeU, dyeV) of
         * dyeTexture, and those whose bit is set in alsoDyed and not in dyedSurfaces at (alsoU,
         * alsoV). The rest have their own colour. Each vertex is lit as the square at (tileX,
         * tileY) is, by where over that square it is: light holds that square's light.
         */
        void add(Model model, SpriteGeometry.Frame light, int tileX, int tileY, float centreX, float centreY, float baseUp, int quarterTurns,
                float scale, float scaleUp, int dyeTexture, int dyedSurfaces, float dyeU, float dyeV, int alsoDyed, float alsoU, float alsoV) {
            if ((count + 1) * FLOATS > floats.length) {
                floats = Arrays.copyOf(floats, floats.length * 2);
                ints = Arrays.copyOf(ints, ints.length * 2);
                rooms = Arrays.copyOf(rooms, rooms.length * 2);
                roomsToo = Arrays.copyOf(roomsToo, roomsToo.length * 2);
                keys = Arrays.copyOf(keys, keys.length * 2);
            }
            int at = count * FLOATS, whole = count * INTS;
            long[] two = RoomSight.pairOf(room);
            rooms[count] = two == null ? room : two[0];
            roomsToo[count] = two == null ? room : two[1];
            keys[count] = key;
            float[] to = floats;
            to[at + PLACE] = tileX;
            to[at + PLACE + 1] = tileY;
            to[at + PLACE + 2] = scale;
            to[at + PLACE + 3] = scaleUp;
            to[at + FOOT] = centreX - tileX;
            to[at + FOOT + 1] = centreY - tileY;
            to[at + FOOT + 2] = baseUp;
            turnRows(quarterTurns, to, at + TURN);
            corners(light.cornerRed, to, at + LIGHT);
            corners(light.cornerGreen, to, at + LIGHT + 1);
            corners(light.cornerBlue, to, at + LIGHT + 2);
            boolean blended = light.cornerUnlit[0] >= 0f;
            float[] unlit = light.cornerUnlit;
            to[at + UNLIT] = blended ? unlit[0] : 0f;
            to[at + UNLIT + 1] = blended ? unlit[1] - unlit[0] : 0f;
            to[at + UNLIT + 2] = blended ? unlit[3] - unlit[0] : 0f;
            to[at + UNLIT + 3] = blended ? unlit[2] - unlit[1] - (unlit[3] - unlit[0]) : 0f;
            to[at + UNLIT + 4] = blended ? light.unlitKind + 1f : 0f;
            float[] day = light.cornerDay;
            to[at + DAY] = day[0];
            to[at + DAY + 1] = day[1] - day[0];
            to[at + DAY + 2] = day[3] - day[0];
            to[at + DAY + 3] = day[2] - day[1] - (day[3] - day[0]);
            to[at + SURFACES] = light.open;
            to[at + SURFACES + 1] = dyedSurfaces;
            to[at + SURFACES + 2] = alsoDyed & ~dyedSurfaces;
            to[at + DYE] = dyeU;
            to[at + DYE + 1] = dyeV;
            to[at + DYE + 2] = alsoU;
            to[at + DYE + 3] = alsoV;
            to[at + PICTURE] = to[at + PICTURE + 1] = to[at + PICTURE + 2] = to[at + PICTURE + 3] = 0f;
            to[at + APART] = model.apart * Math.max(scale, scaleUp);
            to[at + WALL] = 0f;
            ints[whole + FIRST] = model.from;
            ints[whole + COUNT] = model.corners.length;
            ints[whole + PLAIN_FIRST] = model.plain.from;
            ints[whole + PLAIN_COUNT] = model.plain.corners.length;
            ints[whole + TEXTURE] = dyeTexture;
            ints[whole + BUILT] = 0;
            stand(count, (model.minX + model.maxX) / 2f, (model.minY + model.maxY) / 2f, (model.minZ + model.maxZ) / 2f, to, at + BALL);
            float halfWide = model.width() * scale / 2f, halfDeep = model.depth() * scale / 2f, halfHigh = model.height() * scaleUp / 2f;
            to[at + BALL + 3] = (float) Math.sqrt(halfWide * halfWide + halfDeep * halfDeep + halfHigh * halfHigh);
            count++;
        }

        /**
         * Has the piece last added drawn as far as the land and the buildings are, and not only
         * as far as props are: it is part of a building or stands as one (DrawDistance).
         */
        void built() {
            ints[(count - 1) * INTS + BUILT] = 1;
        }

        /**
         * Has the piece last added be part of the wall it is laid over, as the wall's skins are: the round
         * window the picture opens about the viewer leaves it in, where the game cuts a wall away instead,
         * and it is cut with the wall, where what hangs on a cut wall may stay.
         */
        void wall() {
            built();
            floats[(count - 1) * FLOATS + WALL] = 1f;
        }

        /** Has the pane of the piece last added, of this model, be a mirror's glass: see LookingGlass. */
        void glass(Model model) {
            if (glassCount == glassPieces.length) {
                glasses = Arrays.copyOf(glasses, Math.max(2, glassCount * 2) * LookingGlass.FLOATS);
                glassPieces = Arrays.copyOf(glassPieces, Math.max(2, glassCount * 2));
            }
            LookingGlass.place(this, count - 1, model, glasses, glassCount * LookingGlass.FLOATS);
            glassPieces[glassCount++] = count - 1;
        }

        /**
         * Has the pane of the piece last added show the sprite as a picture: mapping holds where
         * on the texture the model's origin is, then what a metre along the model's x adds to
         * that and what a metre up it adds, as WallHung.shows leaves them.
         */
        void picture(float[] mapping) {
            int at = (count - 1) * FLOATS;
            floats[at + DYE] = mapping[0];
            floats[at + DYE + 1] = mapping[1];
            System.arraycopy(mapping, 2, floats, at + PICTURE, 4);
        }

        /**
         * Whether a piece is drawn as its plain model in a picture whose eye its middle is ahead
         * tiles in front of, where two things a tile from that eye and pixel apart are a pixel
         * apart: whether its two models, at the piece's nearest, are nowhere SEEN_APART of a
         * pixel apart.
         */
        boolean plain(int piece, float ahead, float pixel) {
            int at = piece * FLOATS;
            return floats[at + APART] < (ahead - floats[at + BALL + 3]) * pixel * SEEN_APART;
        }

        /** The light at the first corner, and what is added to it along each edge and across, into every third float from at. */
        private static void corners(float[] corner, float[] to, int at) {
            float down = corner[3] - corner[0];
            to[at] = corner[0];
            to[at + 3] = corner[1] - corner[0];
            to[at + 6] = down;
            to[at + 9] = corner[2] - corner[1] - down;
        }

        /**
         * Where on the map the point (x, y, z) of the piece's model is, into out from at as x, y
         * and height: the sum the picture's programs do for every vertex, in the same order.
         */
        void stand(int piece, float x, float y, float z, float[] out, int at) {
            int from = piece * FLOATS;
            float[] f = floats;
            float overX = f[from + FOOT] + (f[from + TURN] * x + f[from + TURN + 1] * y) * f[from + PLACE + 2];
            float overY = f[from + FOOT + 1] + (f[from + TURN + 2] * x + f[from + TURN + 3] * y) * f[from + PLACE + 2];
            out[at] = f[from + PLACE] + overX;
            out[at + 1] = f[from + PLACE + 1] + overY;
            out[at + 2] = f[from + FOOT + 2] + z * f[from + PLACE + 3];
        }
    }
}
