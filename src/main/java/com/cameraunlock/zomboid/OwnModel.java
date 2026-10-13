package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Function;
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
    /** Version 4 has five parts; version 5 says how many it has, and after them the numbers that say how they move. */
    private static final int FIVE_PARTS = 4, ANY_PARTS = 5;
    private static final int HEAD_BYTES = 4 + 4 + 12 * Float.BYTES + Integer.BYTES;
    /** More parts, or more numbers saying how they move, than any model has: a file that says so is not read. */
    private static final int MOST_PARTS = 64, MOST_MOVES = 1024;

    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_ELEMENT_ARRAY_BUFFER = 0x8893, GL_STATIC_DRAW = 0x88E4;
    private static final int GL_FLOAT = 0x1406, GL_SHORT = 0x1402, GL_UNSIGNED_SHORT = 0x1403;

    /**
     * Every model of the mod's own, each with the class that works its action and draws it: a
     * gun is added by a line here, its files in the jar and its class.
     */
    private static int made;
    static final OwnModel[] ALL = {
        new OwnModel("the shotgun's model", "shotgun", "shotgun", "Base.Shotgun", true, true, false, OwnShotgun.PARTS, OwnShotgun.MOVES,
                OwnShotgun::new),
        new OwnModel("the sawn-off shotgun's model", "sawnoff", "sawnoff", "Base.ShotgunSawnoff", true, true, false, OwnShotgun.PARTS,
                OwnShotgun.MOVES, OwnShotgun::new),
        new OwnModel("the rifle's model", "rifle", "rifle", "Base.HuntingRifle", false, false, false, OwnRifle.PARTS, OwnRifle.MOVES, OwnRifle::new),
        new OwnModel("the revolver's model", "revolver", "revolver", "Base.Revolver", false, false, true, OwnRevolver.PARTS, OwnRevolver.MOVES, OwnRevolver::new),
        new OwnModel("the magnum's model", "magnum", "magnum", "Base.Revolver_Long", false, false, true, OwnRevolver.PARTS, OwnRevolver.MOVES, OwnRevolver::new),
        new OwnModel("the snub-nosed revolver's model", "snubnose", "snubnose", "Base.Revolver_Short", false, false, true, OwnRevolver.PARTS,
                OwnRevolver.MOVES, OwnRevolver::new),
        new OwnModel("the cap gun's model", "capgun", "capgun", "Base.Revolver_CapGun", false, false, true, OwnRevolver.PARTS,
                OwnRevolver.MOVES, OwnRevolver::new),
        new OwnModel("the service pistol's model", "pistol", "pistol", "Base.Pistol", false, false, true, OwnPistol.PARTS, OwnPistol.MOVES, OwnPistol::new),
        new OwnModel("the forty-five's model", "pistol2", "pistol2", "Base.Pistol2", false, false, true, OwnPistol.PARTS, OwnPistol.MOVES, OwnPistol::new),
        new OwnModel("the magnum pistol's model", "pistol3", "pistol3", "Base.Pistol3", false, false, true, OwnPistol.PARTS, OwnPistol.MOVES,
                OwnPistol::new),
        new OwnModel("the double barrelled shotgun's model", "dbarrel", "dbarrel", "Base.DoubleBarrelShotgun", false, true, false, OwnDouble.PARTS, OwnDouble.MOVES,
                OwnDouble::new),
        new OwnModel("the sawn-off double barrelled shotgun's model", "dbarrelsawn", "dbarrel", "Base.DoubleBarrelShotgunSawnoff", false, true, false,
                OwnDouble.PARTS, OwnDouble.MOVES, OwnDouble::new),
    };

    /**
     * Each picture on the graphics card, by its path in the jar, and how many models made are
     * drawn with it: two models may share one. Render thread.
     */
    private record Shared(int texture, int users) {}
    private static final java.util.HashMap<String, Shared> PICTURES = new java.util.HashMap<>();

    /** What the model is, for what the log says went wrong making it. */
    final String what;
    /** Where its mesh and pictures are in the jar: two models may share pictures, which are then on the graphics card once. */
    final String mesh;
    final String colourPath;
    private final String surfacePath;
    /** The one item of the game's that the model stands for. */
    private final String item;
    /** Whether it is worked by sliding its fore-end, which its file has as a part of its own: else by a bolt, if it has one. */
    final boolean pump;
    /**
     * Whether the hand under it holds its fore-end with the fixed hold the pump gun's has, and
     * the hands on it are drawn the size hands are: a gun made to a gun's true size, held by the
     * left hand on its fore-end.
     */
    final boolean foreEndHeld;
    /**
     * Whether it is a handgun, made upside down from the long guns as the game makes its own:
     * its top is towards -z in its file.
     */
    final boolean handgun;
    /** Makes the class that works the model's action and draws it, one for each view. */
    final Function<View, OwnGun> works;
    /** How many parts that class draws, which the model's file must have. */
    private final int parts;
    /** How many numbers saying how its parts move its class reads. */
    private final int movesRead;
    /** Where the model is in ALL. */
    final int index = made++;

    /** Read by the game's logic thread too, to ask whether the model stands for what is held. */
    volatile boolean onCard;
    /**
     * Set by whichever thread finds the item held while the model is not on the graphics card:
     * it is made then, and not before, and stays until the content is given back.
     */
    private volatile boolean wanted;
    /** Whether its reading out of the jar has been begun. Render thread. */
    private boolean begun;
    int array, vertexBuffer, indexBuffer, colour, surface;
    /** Where each part's triangles begin among the indices, and how many indices it has. */
    int[] firstIndex = new int[0], indexCount = new int[0];
    /** The numbers that say how the parts move, as the model's file has them: what each is, its class knows. */
    float[] moves = new float[0];
    /** Where the muzzle and the port are from the grip, in the model's metres, and how far back the parts that slide go. */
    final float[] muzzle = new float[3], port = new float[3];
    float travel;
    /**
     * In the model's metres: how far the muzzle is from the grip, and where the eye is with the
     * sights lined up, over the grip and behind it.
     */
    float muzzleFar, sightLine, eyeBehind;
    /** In the model's metres, how far ahead of the grip its rear sight is, which with the sights up is what the eye is behind. */
    float rearSight;
    /**
     * Where the hand under the fore-end holds it and how far the fore-end travels, as shares of
     * the way from the grip to the muzzle.
     */
    float foreEnd, foreEndTravel;
    /** Where hands go on it, in its own axes and metres. */
    WeaponShape shape;

    /**
     * @param what        what the model is, as the log names it: "the rifle's model"
     * @param name        the name its mesh in the jar's models folder begins with
     * @param pictures    the name its two pictures there begin with
     * @param item        the item of the game's it stands for
     * @param pump        whether it is worked by its fore-end
     * @param foreEndHeld whether the left hand holds its fore-end with the pump gun's hold
     * @param handgun     whether it is a handgun, its top towards -z
     * @param parts       how many parts the class that works it draws: its file must have as many
     * @param moves       how many numbers saying how its parts move that class reads: its file must have at least as many
     * @param works       makes the class that works its action and draws it
     */
    private OwnModel(String what, String name, String pictures, String item, boolean pump, boolean foreEndHeld, boolean handgun, int parts,
            int moves, Function<View, OwnGun> works) {
        this.what = what;
        this.item = item;
        this.pump = pump;
        this.foreEndHeld = foreEndHeld;
        this.handgun = handgun;
        this.parts = parts;
        this.movesRead = moves;
        this.works = works;
        mesh = "/models/" + name + ".mesh.gz";
        colourPath = "/models/" + pictures + ".colour.bc";
        surfacePath = "/models/" + pictures + ".surface.bc";
    }

    /**
     * What the head of a model's file says, and where its vertices begin: in the model's metres,
     * from the grip, as the file has them.
     *
     * @param counts how many indices each part has
     * @param moves  the numbers that say how the parts move: none in a version 4 file
     */
    record Layout(float[] muzzle, float heldY, float[] port, float travel, float sightLine, float eyeBehind, int vertices, int[] counts, float[] moves,
            int vertexStart) {
        int indices() {
            int all = 0;
            for (int count : counts) {
                all += count;
            }
            return all;
        }

        /** The places of the vertices of a file read whole, three floats each. */
        float[] places(ByteBuffer read) {
            float[] places = new float[vertices * 3];
            for (int vertex = 0; vertex < vertices; vertex++) {
                for (int axis = 0; axis < 3; axis++) {
                    places[vertex * 3 + axis] = read.getFloat(vertexStart + vertex * VERTEX_BYTES + axis * Float.BYTES);
                }
            }
            return places;
        }

        /** The corners of the triangles of a file read whole, every part's. */
        int[] corners(ByteBuffer read) {
            int[] corners = new int[indices()];
            for (int index = 0; index < corners.length; index++) {
                corners[index] = read.getInt(vertexStart + vertices * VERTEX_BYTES + index * Integer.BYTES);
            }
            return corners;
        }
    }

    /**
     * Reads the head of a model's file, little-endian, from its start, and leaves the buffer at
     * its first vertex. Throws where it is not a weapon model of version 4 or 5, says what
     * cannot be, or is not as long as it says.
     */
    static Layout layout(ByteBuffer read, String path) throws IOException {
        if (read.remaining() < HEAD_BYTES || read.getInt() != MAGIC) {
            throw new IOException(path + " is not a weapon model");
        }
        int version = read.getInt();
        if (version != FIVE_PARTS && version != ANY_PARTS) {
            throw new IOException(path + " is a weapon model of version " + version + ", not 4 or 5");
        }
        float[] muzzle = {read.getFloat(), read.getFloat(), read.getFloat()};
        read.getFloat();
        float heldY = read.getFloat();
        read.getFloat();
        float[] port = {read.getFloat(), read.getFloat(), read.getFloat()};
        float travel = read.getFloat(), sightLine = read.getFloat(), eyeBehind = read.getFloat();
        int vertices = read.getInt();
        int[] counts = new int[version == FIVE_PARTS ? 5 : counted(read, MOST_PARTS, "parts", path)];
        if (counts.length == 0 || read.remaining() < counts.length * Integer.BYTES) {
            throw new IOException(path + " is cut short in its head, or says it has no parts");
        }
        long indices = 0;
        for (int part = 0; part < counts.length; part++) {
            counts[part] = read.getInt();
            if (counts[part] < 0 || counts[part] % 3 != 0) {
                throw new IOException(path + " says its part " + part + " has " + counts[part] + " indices, which are not whole triangles");
            }
            indices += counts[part];
        }
        float[] moves = new float[version == FIVE_PARTS ? 0 : counted(read, MOST_MOVES, "numbers that say how its parts move", path)];
        if (read.remaining() < moves.length * Float.BYTES) {
            throw new IOException(path + " is cut short in its head");
        }
        for (int at = 0; at < moves.length; at++) {
            moves[at] = read.getFloat();
        }
        long body = (long) vertices * VERTEX_BYTES + indices * Integer.BYTES;
        if (vertices < 0 || read.remaining() != body) {
            throw new IOException(path + " holds " + read.remaining() + " bytes after its head, not the " + vertices + " vertices and " + indices + " indices it says");
        }
        return new Layout(muzzle, heldY, port, travel, sightLine, eyeBehind, vertices, counts, moves, read.position());
    }

    /** A count in a file's head that is at most most, or what the file says of it that cannot be. */
    private static int counted(ByteBuffer read, int most, String what, String path) throws IOException {
        if (read.remaining() < Integer.BYTES) {
            throw new IOException(path + " is cut short in its head");
        }
        int count = read.getInt();
        if (count < 0 || count > most) {
            throw new IOException(path + " says it has " + count + " " + what + ", where a model has from 0 to " + most);
        }
        return count;
    }

    /** Which way the top of the model is in its own axes: +z, as the game's long guns are made, or -z for a handgun. */
    float[] top() {
        return new float[] {0f, 0f, handgun ? -1f : 1f};
    }

    /**
     * The model drawn for an item held, or null where it is the game's own: for a model of ours
     * that is not on the graphics card yet, the game's is drawn until it is, and it is asked for.
     * The logic thread's, as it makes ViewModel.Held, which the render thread reads it from.
     */
    static OwnModel of(String held) {
        if (!Own.weapons() || !OwnGun.linked()) {
            return null;
        }
        for (OwnModel model : ALL) {
            if (model.item.equals(held)) {
                if (model.onCard) {
                    return model;
                }
                model.wanted = true;
                return null;
            }
        }
        return null;
    }

    /**
     * A model asked for by {@link #of} that is not on the graphics card, its reading out of the
     * jar begun on other threads, or null. Render thread.
     */
    static OwnModel asked() {
        if (!Own.weapons()) {
            // Asked for while the row was on: nobody will draw it now, so it is not made. What was made of
            // it is given back to the graphics card, and what was begun of its reading is let go.
            for (OwnModel model : ALL) {
                model.wanted = false;
                if (model.onCard) {
                    continue;
                }
                if (model.steps > 0) {
                    model.letGo();
                } else {
                    model.forgetReading();
                }
            }
            return null;
        }
        // One model at a time, from its reading to its last step: the one begun is finished before another is begun.
        for (OwnModel model : ALL) {
            if (model.begun && !model.onCard) {
                return model;
            }
        }
        for (OwnModel model : ALL) {
            if (model.wanted && !model.onCard) {
                if (!model.begun) {
                    model.begun = true;
                    model.readAhead();
                }
                return model;
            }
        }
        return null;
    }

    /** Reads the model out of the jar and gives it to the graphics card, its vertices laid out for the program. Null, or what went wrong. */
    String prepare(Program program) {
        String problem;
        try {
            problem = switch (steps) {
                case 0 -> make(program);
                case 1 -> made(() -> colour = picture(colourPath));
                default -> made(() -> surface = picture(surfacePath));
            };
        } catch (RuntimeException | Error thrown) {
            letGo();
            throw thrown;
        }
        if (problem != null) {
            letGo();
        } else if (++steps == STEPS) {
            onCard = true;
        }
        return problem;
    }

    /**
     * The making is in three steps, each on a frame of its own: the mesh, the colour and the
     * surface. Made in one, the longest took 74 ms of the render thread.
     */
    static final int STEPS = 3;
    private int steps;

    /** How many steps of the making are done. Render thread. */
    int steps() {
        return steps;
    }

    private interface Step {
        void run() throws IOException;
    }

    /** Null once a step has run, or what went wrong. */
    private String made(Step step) {
        try {
            step.run();
        } catch (IOException e) {
            return e.getMessage();
        }
        int error = GL11.glGetError();
        Pipeline.noted(error);
        return error == 0 ? null : "OpenGL error 0x" + Integer.toHexString(error) + " making " + what;
    }

    /**
     * The model's file unpacked and read, and where the hands go on it: worked out on another
     * thread, as reading where the hands go took a whole frame of the render thread's.
     */
    private record Read(ByteBuffer bytes, Layout layout, WeaponShape shape, float rearSight, String why) {}

    private Read read() throws IOException {
        ByteBuffer read = ByteBuffer.wrap(unpacked(mesh)).order(ByteOrder.LITTLE_ENDIAN);
        Layout layout = layout(read, mesh);
        if (layout.counts.length != parts) {
            throw new IOException(mesh + " has " + layout.counts.length + " parts, where what draws " + what + " draws " + parts);
        }
        if (layout.moves.length < movesRead) {
            throw new IOException(mesh + " has " + layout.moves.length + " numbers saying how its parts move, where what draws " + what + " reads " + movesRead);
        }
        StringBuilder why = new StringBuilder();
        float[] places = layout.places(read);
        WeaponShape shape = WeaponShape.read(places, layout.corners(read), new float[] {0f, 1f, 0f}, top(), layout.muzzle.clone(), 1f, why);
        return new Read(read, layout, shape, rearSight(places, layout.muzzle, top()), why.toString());
    }

    /**
     * How far ahead of the grip a handgun's rear sight is, in the model's metres, along its bore:
     * the highest point of the back half of the model, over its top. Behind the grip it is less
     * than nought.
     */
    static float rearSight(float[] places, float[] muzzle, float[] top) {
        float highest = Float.NEGATIVE_INFINITY, along = Float.NaN;
        for (int at = 0; at < places.length; at += 3) {
            float up = places[at] * top[0] + places[at + 1] * top[1] + places[at + 2] * top[2];
            if (places[at + 1] < muzzle[1] / 2f && up > highest) {
                highest = up;
                along = places[at + 1];
            }
        }
        return along;
    }

    private String make(Program program) {
        try {
            Read made = Ahead.take(mesh, this::read);
            if (made.shape == null) {
                return "the hands cannot be put on " + what + ": " + made.why;
            }
            ByteBuffer read = made.bytes;
            Layout layout = made.layout;
            shape = made.shape;
            System.arraycopy(layout.muzzle, 0, muzzle, 0, 3);
            System.arraycopy(layout.port, 0, port, 0, 3);
            travel = layout.travel;
            sightLine = layout.sightLine;
            eyeBehind = layout.eyeBehind;
            rearSight = made.rearSight;
            moves = layout.moves;
            int parts = layout.counts.length, indices = 0;
            firstIndex = new int[parts];
            indexCount = layout.counts.clone();
            for (int part = 0; part < parts; part++) {
                firstIndex[part] = indices;
                indices += indexCount[part];
            }
            int vertices = layout.vertices;
            muzzleFar = (float) Math.sqrt(muzzle[0] * muzzle[0] + muzzle[1] * muzzle[1] + muzzle[2] * muzzle[2]);
            foreEnd = layout.heldY / muzzle[1];
            foreEndTravel = travel / muzzle[1];
            upload(program, read, vertices, indices);
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
        steps = 0;
        wanted = false;
        forgetReading();
        letGoOf(colourPath, colour);
        letGoOf(surfacePath, surface);
        GL15.glDeleteBuffers(vertexBuffer);
        GL15.glDeleteBuffers(indexBuffer);
        GL30.glDeleteVertexArrays(array);
        colour = 0;
        surface = 0;
        vertexBuffer = 0;
        indexBuffer = 0;
        array = 0;
    }

    /** Lets go of whatever was begun of reading the model for a making that will not now happen. */
    private void forgetReading() {
        if (!begun) {
            return;
        }
        begun = false;
        Ahead.forget(mesh);
        // A picture is read for one model at a time: asked() finishes the model it has begun before it begins another.
        for (String path : new String[] {colourPath, surfacePath}) {
            if (!PICTURES.containsKey(path)) {
                Ahead.forget(path);
            }
        }
    }

    /** Begins unpacking the model and its pictures on other threads, for {@link #prepare} to find them unpacked. */
    private void readAhead() {
        Ahead.begin(mesh, this::read);
        for (String path : new String[] {colourPath, surfacePath}) {
            if (!PICTURES.containsKey(path)) {
                Ahead.begin(path, () -> Pictures.packed(path, false));
            }
        }
    }

    /** Whether what the next step of the making reads is unpacked: each step takes what it reads, so a step's is not there after it. */
    boolean isRead() {
        return switch (steps) {
            case 0 -> Ahead.done(mesh);
            case 1 -> PICTURES.containsKey(colourPath) || Ahead.done(colourPath);
            default -> PICTURES.containsKey(surfacePath) || Ahead.done(surfacePath);
        };
    }

    /**
     * Fills into with the model's matrix where the game has its weapon, held up in the view, and
     * says which way it is handed: -1 where that leaves it a mirror image of itself as it was
     * made, 1 where it does not.
     *
     * @param heldView    ViewModel's matrix for the weapon held up: from the character's axes to the view's
     * @param weapon      where the game has the weapon in its character's axes, read by rows
     * @param finalScale  the scale the game draws the character's parts at
     * @param gameMuzzle  how far the muzzle of the game's model is from its grip, in that model's units
     */
    float hold(float[] into, float[] heldView, float[] weapon, float finalScale, float gameMuzzle) {
        // The model is sized so that its muzzle is where the game's is.
        float size = finalScale * gameMuzzle / muzzleFar;
        for (int column = 0; column < 3; column++) {
            into[column * 4] = weapon[column] * size;
            into[column * 4 + 1] = weapon[4 + column] * size;
            into[column * 4 + 2] = weapon[8 + column] * size;
            into[column * 4 + 3] = 0f;
        }
        into[12] = weapon[3] * finalScale;
        into[13] = weapon[7] * finalScale;
        into[14] = weapon[11] * finalScale;
        into[15] = 1f;
        ViewModelMath.onto(heldView, into);
        float turned = into[0] * (into[5] * into[10] - into[9] * into[6]) - into[4] * (into[1] * into[10] - into[9] * into[2])
                + into[8] * (into[1] * into[6] - into[5] * into[2]);
        return turned < 0f ? -1f : 1f;
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

    /** A picture on the graphics card for one more model: the one there if another model has it, made otherwise. */
    private static int picture(String path) throws IOException {
        Shared there = PICTURES.get(path);
        int texture = there != null ? there.texture : texture(path);
        PICTURES.put(path, new Shared(texture, there != null ? there.users + 1 : 1));
        return texture;
    }

    /** One model fewer drawn with a picture, given back to the graphics card once none is. 0 for a picture the model never had. */
    private static void letGoOf(String path, int texture) {
        Shared there = PICTURES.get(path);
        if (texture == 0 || there == null) {
            return;
        }
        if (there.users > 1) {
            PICTURES.put(path, new Shared(there.texture, there.users - 1));
        } else {
            PICTURES.remove(path);
            GL11.glDeleteTextures(there.texture);
        }
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
