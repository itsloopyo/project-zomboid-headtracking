package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
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

    static final class Model {
        final String name;
        final float minX, minY, minZ, maxX, maxY, maxZ;
        /** VERTEX_FLOATS for each vertex, three vertices to a triangle. */
        final float[] vertices;
        /** What each vertex is made of. They are in the order of their surfaces. */
        final byte[] surface;
        /** The first vertex of each surface, and after the last surface the number of vertices. */
        final int[] first = new int[SURFACES + 1];
        /**
         * The vertices as they stand on the map after each number of quarter turns, made the first
         * time a piece is turned that way: turning five thousand vertices is the same sum for every
         * bed that faces east, and a chunk near the eye is built again every frame.
         */
        private final float[][] turned = new float[4][];
        /** For each vertex, how much of the room's light reaches it, 0 to 1. */
        final float[] reached;
        /**
         * What the light a vertex carries is multiplied by to leave that share of it. The
         * picture reads the colour a vertex carries as light to the power 2.2, so this is the
         * share to the power 1 / 2.2.
         */
        final float[] dimmed;

        private Model(String name, float[] bounds, float[] vertices, byte[] surface, float[] reached) {
            this.name = name;
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

        /** Position then the way it looks, for each vertex, in the map's axes after that many quarter turns. */
        float[] turned(int quarterTurns) {
            int turns = quarterTurns & 3;
            if (turned[turns] == null) {
                float[] made = new float[vertices.length];
                float[] to = new float[2];
                for (int at = 0; at < vertices.length; at += VERTEX_FLOATS) {
                    turn(turns, vertices[at], vertices[at + 1], to);
                    made[at] = to[0];
                    made[at + 1] = to[1];
                    made[at + 2] = vertices[at + 2];
                    turn(turns, vertices[at + 3], vertices[at + 4], to);
                    made[at + 3] = to[0];
                    made[at + 4] = to[1];
                    made[at + 5] = vertices[at + 5];
                }
                turned[turns] = made;
            }
            return turned[turns];
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

    private static Map<String, Model> loaded;

    private Models() {}

    /** The model of that name, read from the jar the first time one is asked for. */
    static Model get(String name) {
        if (loaded == null) {
            try (InputStream packed = Models.class.getResourceAsStream(RESOURCE)) {
                if (packed == null) {
                    throw new IllegalStateException("the jar has no " + RESOURCE);
                }
                loaded = read(packed);
            } catch (IOException e) {
                throw new UncheckedIOException(RESOURCE + " could not be read", e);
            }
        }
        Model model = loaded.get(name);
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
            if (models.put(name, new Model(name, bounds, vertices, surface, reached)) != null) {
                throw new IOException("two models are called " + name);
            }
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

    /**
     * Adds a model standing with the centre of its footprint at (centreX, centreY) on the map
     * and its foot at baseUp, turned by quarterTurns as turn says, one metre of it scale tiles
     * along the ground and scaleUp tiles up.
     *
     * The surfaces whose bit is set in dyedSurfaces go into dyed, which is the mesh of a
     * sprite's texture, and all look at the texel (dyeU, dyeV) of it. Those whose bit is set
     * in alsoDyed go into the mesh also, and look at (alsoU, alsoV). The rest go into plain.
     * Each vertex is lit as the square at (tileX, tileY) is, by where over that square it is:
     * light holds that square's light.
     */
    static void add(Model model, SpriteGeometry.Mesh plain, SpriteGeometry.Mesh dyed, int dyedSurfaces, float dyeU, float dyeV,
            SpriteGeometry.Mesh also, int alsoDyed, float alsoU, float alsoV,
            SpriteGeometry.Frame light, int tileX, int tileY, float centreX, float centreY, float baseUp, int quarterTurns, float scale, float scaleUp) {
        float[] from = model.turned(quarterTurns);
        float[] dim = model.dimmed;
        // The light at a place over the square is its first corner's, and what is added to that along each edge and across.
        float red = light.cornerRed[0], redA = light.cornerRed[1] - red, redB = light.cornerRed[3] - red, redAB = light.cornerRed[2] - light.cornerRed[1] - redB;
        float green = light.cornerGreen[0], greenA = light.cornerGreen[1] - green, greenB = light.cornerGreen[3] - green,
                greenAB = light.cornerGreen[2] - light.cornerGreen[1] - greenB;
        float blue = light.cornerBlue[0], blueA = light.cornerBlue[1] - blue, blueB = light.cornerBlue[3] - blue,
                blueAB = light.cornerBlue[2] - light.cornerBlue[1] - blueB;
        float originX = centreX - tileX, originY = centreY - tileY;
        for (int surface = 0; surface < SURFACES; surface++) {
            int start = model.first[surface], end = model.first[surface + 1];
            if (start == end) {
                continue;
            }
            boolean takesDye = (dyedSurfaces >> surface & 1) != 0, takesOther = !takesDye && (alsoDyed >> surface & 1) != 0;
            SpriteGeometry.Mesh mesh = takesDye ? dyed : takesOther ? also : plain;
            float u = takesDye ? dyeU : takesOther ? alsoU : 0f, v = takesDye ? dyeV : takesOther ? alsoV : 0f;
            float open = light.open + 32f * ((takesDye || takesOther ? FIRST_DYED_MATERIAL : FIRST_MATERIAL) + surface);
            float[] to = mesh.room((end - start) * SpriteGeometry.VERTEX_FLOATS);
            int out = mesh.floats;
            for (int vertex = start, at = start * VERTEX_FLOATS; vertex < end; vertex++, at += VERTEX_FLOATS) {
                float overX = originX + from[at] * scale, overY = originY + from[at + 1] * scale;
                float a = Math.min(Math.max(overX, 0f), 1f), b = Math.min(Math.max(overY, 0f), 1f), ab = a * b, dims = dim[vertex];
                to[out] = tileX + overX;
                to[out + 1] = tileY + overY;
                to[out + 2] = baseUp + from[at + 2] * scaleUp;
                to[out + 3] = u;
                to[out + 4] = v;
                to[out + 5] = dims * (red + a * redA + b * redB + ab * redAB);
                to[out + 6] = dims * (green + a * greenA + b * greenB + ab * greenAB);
                to[out + 7] = dims * (blue + a * blueA + b * blueB + ab * blueAB);
                to[out + 8] = open;
                to[out + 9] = from[at + 3];
                to[out + 10] = from[at + 4];
                to[out + 11] = from[at + 5];
                out += SpriteGeometry.VERTEX_FLOATS;
            }
            mesh.floats = out;
        }
    }
}
