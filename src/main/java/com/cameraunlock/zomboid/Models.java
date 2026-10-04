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
 *   uint32    format version, 1
 *   uint32    number of models
 *   per model:
 *     uint8     length of the name, then that many ASCII bytes
 *     float32   x 6: the least x, y, z and the greatest x, y, z
 *     uint32    number of triangles
 *     per vertex, three to a triangle:
 *       float32 x 3   position
 *       float32 x 3   unit normal
 *       uint8         surface: WOOD to PAINT below
 *       uint8         how much of the room's light reaches the vertex past the rest of the
 *                     model and the floor, 255 for all of it
 */
final class Models {
    static final int WOOD = 0, DARK_WOOD = 1, CLOTH = 2, LINEN = 3, PILLOW = 4, METAL = 5, PAINT = 6;
    private static final int SURFACES = 7;

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
    static final float FIRST_DYED_MATERIAL = 24f;

    private static final String RESOURCE = "/models/furniture.bin.gz";
    private static final int MAGIC = 'C' | 'U' << 8 | 'F' << 16 | 'M' << 24;
    private static final int VERSION = 1;
    private static final int VERTEX_BYTES = 6 * Float.BYTES + 2;
    /** Floats per vertex of a Model: its position, then the way it looks. */
    static final int VERTEX_FLOATS = 6;

    static final class Model {
        final String name;
        final float minX, minY, minZ, maxX, maxY, maxZ;
        /** VERTEX_FLOATS for each vertex, three vertices to a triangle. */
        final float[] vertices;
        final byte[] surface;
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
     * and its foot at baseUp, turned by quarterTurns as turn says, one metre of it scale tiles.
     *
     * The surfaces whose bit is set in dyedSurfaces go into dyed, which is the mesh of a
     * sprite's texture, and all look at the texel (dyeU, dyeV) of it. The rest go into plain.
     * Each vertex is lit as the square at (tileX, tileY) is, by where over that square it is:
     * light holds that square's light.
     */
    static void add(Model model, SpriteGeometry.Mesh plain, SpriteGeometry.Mesh dyed, int dyedSurfaces, float dyeU, float dyeV,
            SpriteGeometry.Frame light, int tileX, int tileY, float centreX, float centreY, float baseUp, int quarterTurns, float scale) {
        // What each of a model's axes is on the map: turn is linear, so two calls give its matrix.
        float[] axis = new float[2];
        turn(quarterTurns, 1f, 0f, axis);
        float xToX = axis[0], xToY = axis[1];
        turn(quarterTurns, 0f, 1f, axis);
        float yToX = axis[0], yToY = axis[1];
        float[] from = model.vertices;
        for (int vertex = 0; vertex < model.surface.length; vertex++) {
            int at = vertex * VERTEX_FLOATS;
            int surface = model.surface[vertex];
            boolean takesDye = (dyedSurfaces >> surface & 1) != 0;
            SpriteGeometry.Mesh mesh = takesDye ? dyed : plain;
            mesh.open = light.open + 32f * ((takesDye ? FIRST_DYED_MATERIAL : FIRST_MATERIAL) + surface);
            float x = centreX + (from[at] * xToX + from[at + 1] * yToX) * scale;
            float y = centreY + (from[at] * xToY + from[at + 1] * yToY) * scale;
            float a = Math.clamp(x - tileX, 0f, 1f), b = Math.clamp(y - tileY, 0f, 1f);
            float w0 = (1f - a) * (1f - b), w1 = a * (1f - b), w2 = a * b, w3 = (1f - a) * b;
            float dim = model.dimmed[vertex];
            mesh.addRounded(x, y, baseUp + from[at + 2] * scale, takesDye ? dyeU : 0f, takesDye ? dyeV : 0f,
                    dim * (light.cornerRed[0] * w0 + light.cornerRed[1] * w1 + light.cornerRed[2] * w2 + light.cornerRed[3] * w3),
                    dim * (light.cornerGreen[0] * w0 + light.cornerGreen[1] * w1 + light.cornerGreen[2] * w2 + light.cornerGreen[3] * w3),
                    dim * (light.cornerBlue[0] * w0 + light.cornerBlue[1] * w1 + light.cornerBlue[2] * w2 + light.cornerBlue[3] * w3),
                    from[at + 3] * xToX + from[at + 4] * yToX, from[at + 3] * xToY + from[at + 4] * yToY, from[at + 5]);
        }
    }
}
