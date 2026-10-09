package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * One of the mod's own weapon models, read out of the jar and given to the graphics card: its
 * mesh in parts, its colour and the shape and finish of its surface, and what the head of its
 * file says of where the muzzle and the port are. Made by tools/models and kept in the jar.
 *
 * All of it is the render thread's but {@link #onCard}.
 */
final class OwnModel {
    /** A vertex in the file: its place, its normal, the tangent along the texture, which way the texture's other axis runs, its place in the texture. */
    private static final int VERTEX_BYTES = 3 * Float.BYTES + 3 * Short.BYTES + 3 * Short.BYTES + Short.BYTES + 2 * Short.BYTES;
    private static final int MAGIC = 'C' | 'U' << 8 | 'W' << 16 | 'M' << 24;
    private static final int VERSION = 4;
    private static final int HEAD_BYTES = 4 + 4 + 12 * Float.BYTES + 6 * Integer.BYTES;
    static final int PARTS = 5;

    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_ELEMENT_ARRAY_BUFFER = 0x8893, GL_STATIC_DRAW = 0x88E4;
    private static final int GL_FLOAT = 0x1406, GL_SHORT = 0x1402, GL_UNSIGNED_SHORT = 0x1403;

    /** What the model is, for what the log says went wrong making it. */
    private final String what;
    private final String mesh, colourPath, surfacePath;

    /** Read by the game's logic thread too, to ask whether the model stands for what is held. */
    volatile boolean onCard;
    int array, vertexBuffer, indexBuffer, colour, surface;
    final int[] firstIndex = new int[PARTS], indexCount = new int[PARTS];
    /** Where the muzzle and the port are from the grip, in the model's metres, and how far back the parts that slide go. */
    final float[] muzzle = new float[3], port = new float[3];
    float travel;
    /**
     * In the model's metres: how far the muzzle is from the grip, and where the eye is with the
     * sights lined up, over the bore and behind the grip.
     */
    float muzzleFar, sightLine, eyeBehind;
    /**
     * Where the hand under the fore-end holds it and how far the fore-end travels, as shares of
     * the way from the grip to the muzzle.
     */
    float foreEnd, foreEndTravel;

    /**
     * @param what  what the model is, as the log names it: "the rifle's model"
     * @param name  the name its three files in the jar's models folder begin with
     */
    OwnModel(String what, String name) {
        this.what = what;
        mesh = "/models/" + name + ".mesh.gz";
        colourPath = "/models/" + name + ".colour.bc";
        surfacePath = "/models/" + name + ".surface.bc";
    }

    /** Reads the model out of the jar and gives it to the graphics card, its vertices laid out for the program. Null, or what went wrong. */
    String prepare(Program program) {
        String problem = make(program);
        if (problem == null) {
            onCard = true;
        } else {
            letGo();
        }
        return problem;
    }

    private String make(Program program) {
        try {
            ByteBuffer read = ByteBuffer.wrap(Ahead.take(mesh, () -> unpacked(mesh))).order(ByteOrder.LITTLE_ENDIAN);
            if (read.remaining() < HEAD_BYTES || read.getInt() != MAGIC || read.getInt() != VERSION) {
                return mesh + " is not a weapon model of version " + VERSION;
            }
            for (int axis = 0; axis < 3; axis++) {
                muzzle[axis] = read.getFloat();
            }
            read.getFloat();
            float heldY = read.getFloat();
            read.getFloat();
            for (int axis = 0; axis < 3; axis++) {
                port[axis] = read.getFloat();
            }
            travel = read.getFloat();
            sightLine = read.getFloat();
            eyeBehind = read.getFloat();
            int vertices = read.getInt(), indices = 0;
            for (int part = 0; part < PARTS; part++) {
                firstIndex[part] = indices;
                indexCount[part] = read.getInt();
                indices += indexCount[part];
            }
            if (read.remaining() != vertices * VERTEX_BYTES + indices * Integer.BYTES) {
                return mesh + " holds " + read.remaining() + " bytes after its head, not the " + vertices + " vertices and " + indices + " indices it says";
            }
            muzzleFar = (float) Math.sqrt(muzzle[0] * muzzle[0] + muzzle[1] * muzzle[1] + muzzle[2] * muzzle[2]);
            foreEnd = heldY / muzzle[1];
            foreEndTravel = travel / muzzle[1];
            upload(program, read, vertices, indices);
            colour = texture(colourPath);
            surface = texture(surfacePath);
        } catch (IOException e) {
            return e.getMessage();
        }
        int error = GL11.glGetError();
        Pipeline.noted(error);
        if (error != 0) {
            return "OpenGL error 0x" + Integer.toHexString(error) + " making " + what;
        }
        return null;
    }

    /**
     * Gives the model's buffers and pictures back to the graphics card, made or half made, and
     * whatever was unpacked ahead for a making that will not now happen. Render thread.
     */
    void letGo() {
        onCard = false;
        for (String path : new String[] {mesh, colourPath, surfacePath}) {
            Ahead.forget(path);
        }
        GL11.glDeleteTextures(colour);
        GL11.glDeleteTextures(surface);
        GL15.glDeleteBuffers(vertexBuffer);
        GL15.glDeleteBuffers(indexBuffer);
        GL30.glDeleteVertexArrays(array);
        colour = 0;
        surface = 0;
        vertexBuffer = 0;
        indexBuffer = 0;
        array = 0;
    }

    /** Begins unpacking the model and its pictures on other threads, for {@link #prepare} to find them unpacked. */
    void readAhead() {
        Ahead.begin(mesh, () -> unpacked(mesh));
        for (String path : new String[] {colourPath, surfacePath}) {
            Ahead.begin(path, () -> Pictures.packed(path, false));
        }
    }

    /** Whether everything {@link #readAhead} began is unpacked. */
    boolean isRead() {
        return Ahead.done(mesh) && Ahead.done(colourPath) && Ahead.done(surfacePath);
    }

    /** Sets the fields a step at a time, so that letGo can give back a half made model. */
    private void upload(Program program, ByteBuffer read, int vertices, int indices) {
        array = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(array);
        // The driver takes its copy from memory outside the heap, which is given back as soon as it has.
        OffHeap.Block data = OffHeap.take(vertices * VERTEX_BYTES + indices * Integer.BYTES);
        try {
            data.bytes.put(0, read.array(), read.position(), data.bytes.capacity());
            vertexBuffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
            GL15.glBufferData(GL_ARRAY_BUFFER, data.bytes.slice(0, vertices * VERTEX_BYTES), GL_STATIC_DRAW);
            indexBuffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
            GL15.glBufferData(GL_ELEMENT_ARRAY_BUFFER, data.bytes.slice(vertices * VERTEX_BYTES, indices * Integer.BYTES), GL_STATIC_DRAW);
        } finally {
            data.free();
        }
        attribute(program, "aPlace", 3, GL_FLOAT, false, 0);
        attribute(program, "aNormal", 3, GL_SHORT, true, 12);
        attribute(program, "aTangent", 3, GL_SHORT, true, 18);
        attribute(program, "aWay", 1, GL_SHORT, false, 24);
        attribute(program, "aUv", 2, GL_UNSIGNED_SHORT, true, 26);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private static byte[] unpacked(String path) throws IOException {
        try (InputStream packed = OwnModel.class.getResourceAsStream(path)) {
            if (packed == null) {
                throw new IOException("the jar has no " + path);
            }
            return new GZIPInputStream(packed).readAllBytes();
        }
    }

    private static void attribute(Program linked, String name, int size, int type, boolean normalized, int offset) {
        int at = GL20.glGetAttribLocation(linked.id, name);
        GL20.glEnableVertexAttribArray(at);
        GL20.glVertexAttribPointer(at, size, type, normalized, VERTEX_BYTES, offset);
    }

    private static int texture(String path) throws IOException {
        OffHeap.Block file = Ahead.take(path, () -> Pictures.packed(path, false));
        try {
            // The barrel is seen along its length, so it is read with 16 samples along the slant.
            return Pictures.texture(file, GL_TEXTURE_2D, false, 16, path);
        } finally {
            file.free();
        }
    }
}
