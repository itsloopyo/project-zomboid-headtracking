package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
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
 * The file is gzip over this, all little-endian:
 *
 *   4 bytes   the ASCII letters CUFM
 *   uint32    format version, 2
 *   uint32    number of models
 *   per model:
 *     uint8     length of the name, then that many ASCII bytes
 *     float32   x 6: the least x, y, z and the greatest x, y, z
 *     uint32    number of triangles, in the order of their surfaces
 *     per vertex, three to a triangle:
 *       float32 x 3   position
 *       float32 x 3   unit normal
 *       uint8         surface: WOOD to WORKTOP below
 *       uint8         how much of the room's light reaches the vertex past the rest of the
 *                     model and the floor, 255 for all of it
 */
final class Models {
    static final int WOOD = 0, DARK_WOOD = 1, CLOTH = 2, LINEN = 3, PILLOW = 4, METAL = 5, PAINT = 6, ENAMEL = 7, STEEL = 8, PLASTIC = 9,
            SCREEN = 10, CERAMIC = 11, BOOKS = 12, SHADE = 13, RUBBER = 14, WORKTOP = 15;
    static final int SURFACES = 16;

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
     * a vertex is drawn with the sprite's texture and looks at one texel of it.
     */
    static final float FIRST_DYED_MATERIAL = FIRST_MATERIAL + SURFACES;

    private static final String RESOURCE = "/models/furniture.bin.gz";
    private static final int MAGIC = 'C' | 'U' << 8 | 'F' << 16 | 'M' << 24;
    private static final int VERSION = 2;
    private static final int VERTEX_BYTES = 6 * Float.BYTES + 2;
    /** Floats per vertex of a Model: its position, then the way it looks. */
    static final int VERTEX_FLOATS = 6;
    /**
     * Floats per vertex in the buffer the graphics card keeps of every model: its position, the
     * way it looks, its surface, and what the light it is given is multiplied by.
     */
    static final int BUFFER_FLOATS = 8;

    static final class Model {
        final String name;
        final float minX, minY, minZ, maxX, maxY, maxZ;
        /** VERTEX_FLOATS for each vertex, three vertices to a triangle. */
        final float[] vertices;
        /** What each vertex is made of. They are in the order of their surfaces. */
        final byte[] surface;
        /** The first vertex of each surface, and after the last surface the number of vertices. */
        final int[] first = new int[SURFACES + 1];
        /** The model's first vertex in the buffer of every model: see buffer. */
        final int start;
        /** For each vertex, how much of the room's light reaches it, 0 to 1. */
        final float[] reached;
        /**
         * What the light a vertex carries is multiplied by to leave that share of it. The
         * picture reads the colour a vertex carries as light to the power 2.2, so this is the
         * share to the power 1 / 2.2.
         */
        final float[] dimmed;

        private Model(String name, int start, float[] bounds, float[] vertices, byte[] surface, float[] reached) {
            this.name = name;
            this.start = start;
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
        }

        int triangles() {
            return surface.length / 3;
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
        int start = 0;
        for (int i = 0; i < count; i++) {
            need(in, 1, "a model's name");
            byte[] letters = new byte[in.get() & 0xff];
            need(in, letters.length + 6 * Float.BYTES + Integer.BYTES, "a model's name and bounds");
            in.get(letters);
            String name = new String(letters, StandardCharsets.US_ASCII);
            float[] bounds = new float[6];
            for (int k = 0; k < 6; k++) {
                bounds[k] = in.getFloat();
            }
            int triangles = in.getInt();
            if (triangles < 0 || (long) triangles * 3 * VERTEX_BYTES > in.remaining()) {
                throw new IOException(name + " says it has " + triangles + " triangles, and the file ends before that many");
            }
            float[] vertices = new float[triangles * 3 * VERTEX_FLOATS];
            byte[] surface = new byte[triangles * 3];
            float[] reached = new float[triangles * 3];
            for (int vertex = 0; vertex < surface.length; vertex++) {
                for (int k = 0; k < VERTEX_FLOATS; k++) {
                    vertices[vertex * VERTEX_FLOATS + k] = in.getFloat();
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
            if (models.put(name, new Model(name, start, bounds, vertices, surface, reached)) != null) {
                throw new IOException("two models are called " + name);
            }
            start += surface.length;
        }
        if (in.hasRemaining()) {
            throw new IOException(in.remaining() + " bytes follow the last model");
        }
        return models;
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

    /** The models one after another, each from its start, BUFFER_FLOATS to a vertex. */
    static float[] buffer(Collection<Model> models) {
        int vertices = 0;
        for (Model model : models) {
            vertices += model.surface.length;
        }
        float[] out = new float[vertices * BUFFER_FLOATS];
        for (Model model : models) {
            for (int vertex = 0, at = model.start * BUFFER_FLOATS; vertex < model.surface.length; vertex++, at += BUFFER_FLOATS) {
                System.arraycopy(model.vertices, vertex * VERTEX_FLOATS, out, at, VERTEX_FLOATS);
                out[at + 6] = model.surface[vertex];
                out[at + 7] = model.dimmed[vertex];
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
        static final int FLOATS = 34;
        /** The model's first vertex in the buffer of every model, how many it has, and the texture that dyes the piece or 0. */
        static final int FIRST = 0, COUNT = 1, TEXTURE = 2;
        static final int INTS = 3;

        float[] floats = new float[FLOATS * 8];
        int[] ints = new int[INTS * 8];
        int count;

        void clear() {
            count = 0;
        }

        int triangles() {
            int vertices = 0;
            for (int i = 0; i < count; i++) {
                vertices += ints[i * INTS + COUNT];
            }
            return vertices / 3;
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
            }
            int at = count * FLOATS, whole = count * INTS;
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
            to[at + SURFACES] = light.open;
            to[at + SURFACES + 1] = dyedSurfaces;
            to[at + SURFACES + 2] = alsoDyed & ~dyedSurfaces;
            to[at + DYE] = dyeU;
            to[at + DYE + 1] = dyeV;
            to[at + DYE + 2] = alsoU;
            to[at + DYE + 3] = alsoV;
            ints[whole + FIRST] = model.start;
            ints[whole + COUNT] = model.surface.length;
            ints[whole + TEXTURE] = dyeTexture;
            stand(count, (model.minX + model.maxX) / 2f, (model.minY + model.maxY) / 2f, (model.minZ + model.maxZ) / 2f, to, at + BALL);
            float halfWide = model.width() * scale / 2f, halfDeep = model.depth() * scale / 2f, halfHigh = model.height() * scaleUp / 2f;
            to[at + BALL + 3] = (float) Math.sqrt(halfWide * halfWide + halfDeep * halfDeep + halfHigh * halfHigh);
            count++;
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
