package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Locale;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelMesh;
import zombie.core.skinnedmodel.model.VehicleModelInstance;
import zombie.core.skinnedmodel.model.VertexBufferObject;
import zombie.core.textures.Texture;

/**
 * Where the steering wheel is in each vehicle, read from the vehicle's own model. Every body
 * has its wheel as a loose piece of 36 triangles, a six-sided ring, and its middle is found
 * once per model on the render thread. The driver's eye and the driver's place the picture
 * builds are both put from it, so a pickup's driver sits as high as a pickup's wheel is and a
 * van's as far forward.
 */
final class VehicleMeshes {
    private VehicleMeshes() {}

    private static final int WHEEL_TRIANGLES = 36;
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_ARRAY_BUFFER_BINDING = 0x8894, GL_BUFFER_SIZE = 0x8764;
    private static final float[] NONE = new float[0];

    /**
     * What is read of a body is read from three things: its mesh, the mask that says which of
     * its triangles are glass, and the script whose seats tell its steering wheel and its seats.
     * Two vehicles can share a mesh and differ in either.
     */
    private record Body(ModelMesh mesh, Texture mask, String script) {}

    /** The wheel's middle in each body's own space, or NONE for a body with no such piece. */
    private static final HashMap<Body, float[]> middles = new HashMap<>();
    /** Which of each body's model axes is its up and which its length: see Cabins.upright. */
    private static final HashMap<Body, int[]> uprights = new HashMap<>();
    private static final String[] AXES = {"x", "y", "z"};
    /**
     * The panes of each body, as loose triangles in the model's own space: the vehicle's mask
     * texture says which of its triangles are glass. Empty for a body with none found.
     */
    private static final HashMap<Body, FloatBuffer> panes = new HashMap<>();
    private static final HashMap<Body, Integer> rearPanes = new HashMap<>();
    private static final float[] shifted = new float[16], stands = new float[16];

    /** The triangles of a body that are not glass, sorted by what part of the cabin each is, in the model's own space. */
    static final class Parts {
        final FloatBuffer triangles;
        /** For each kind CabinParts tells, where its vertices begin and how many they are. */
        final int[] first = new int[CabinParts.KINDS], count = new int[CabinParts.KINDS];
        /** Whether the seats were found as pieces of their own. Where they were not, they are not told from the shell. */
        final boolean seats;
        /**
         * From the wheel's middle, in tiles: the foot of the windscreen, along and up; the
         * sill of the driver's window, up; how far out from the wheel that window is; how far
         * across the vehicle's middle line is, by sign; the top of the dashboard, up; and
         * where the shelf of the dashboard begins, along.
         */
        final float[] measures;
        /** For each triangle of the body, whether it is a pane of the front of the glasshouse. */
        final boolean[] front;

        Parts(FloatBuffer triangles, boolean seats, float[] measures, boolean[] front) {
            this.triangles = triangles;
            this.seats = seats;
            this.measures = measures;
            this.front = front;
        }
    }

    private static final HashMap<Body, Parts> parts = new HashMap<>();

    /**
     * The vehicle one viewer sits in, as its body was drawn for that viewer's picture this
     * frame: two viewers may sit in two vehicles, or in two seats of one. Render thread.
     */
    static final class Seated {
        /**
         * The panes of the vehicle, and the matrix that puts them on the screen. Null with
         * none. The first rearPanes vertices are the panes behind the front of the glasshouse;
         * the rest are the screen and the front windows, where the picture builds its own.
         */
        FloatBuffer panes;
        int rearPanes;
        final float[] paneMatrix = new float[16];
        /** The parts of the vehicle, drawn with paneMatrix as the panes are. Null with none. */
        Parts parts;
        /** Where the wheel of the vehicle is in the world this frame, or null: x, y and up. */
        float[] wheel;
        /**
         * The way the vehicle faces, the side to the left of that and its own up, in the
         * world, as its body is drawn this frame. On its springs a vehicle tips and rolls, a
         * crash throws it on its side or its roof, and what the picture builds in its cabin is
         * laid out along these.
         */
        final float[] axes = new float[9];
        /** The vehicle's paint. */
        final float[] paint = new float[3];
        private final float[] wheelNow = new float[3];

        void clear() {
            panes = null;
            parts = null;
            wheel = null;
        }
    }

    private static final HashSet<String> turnedSaid = new HashSet<>();
    private static int tipSaid;
    /** What turns a vertex of the body into tiles along the vehicle, across it and up from the ground: three rows of four. */
    private static final float[] frame = new float[12];
    private static final int GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE_BINDING_2D = 0x8069, GL_TEXTURE_WIDTH = 0x1000, GL_TEXTURE_HEIGHT = 0x1001;
    private static final int GL_RGBA = 0x1908, GL_UNSIGNED_BYTE = 0x1401;

    private static final int PLACE_NORMAL_AND_TEXTURE_PLACE = 32;
    private static Field handleField, elementsField, bufferField, strideField;
    private static final float[] xfrm = new float[16];

    /**
     * Called on the render thread for the vehicle a viewer sits in, with the matrix that
     * stands it in the world, how far the steering wheel read from its model sits above the
     * one the game draws, along the body's up, and where the vehicle stands.
     *
     * @param seated what that viewer's picture is told of the vehicle
     * @param ride   that viewer's vehicle as the logic thread has it
     * @param faces  the way the vehicle faces, its left and its up, as the logic thread had them when it seated the eye: see Ride.stands
     */
    static void note(Seated seated, Ride ride, String script, ModelInstanceRenderData body, float[] placed, float[] faces, float settles, float standsX, float standsY,
            float groundUp, float centreOfMass, float[] eye) {
        seated.clear();
        float[] seatedAxes = seated.axes, paneMatrix = seated.paneMatrix;
        float aheadX = ride.aheadX, aheadY = ride.aheadY;
        Cabins.level(aheadX, aheadY, seatedAxes);
        if (!(body.modelInstance instanceof VehicleModelInstance instance) || body.modelInstance.model == null) {
            return;
        }
        ModelMesh mesh = body.modelInstance.model.mesh;
        if (mesh == null) {
            return;
        }
        body.xfrm.get(xfrm);
        // The game hangs the body from the vehicle's centre of mass, along the model's own up.
        System.arraycopy(placed, 0, shifted, 0, 16);
        for (int k = 0; k < 3; k++) {
            shifted[12 + k] += placed[4 + k] * centreOfMass;
        }
        multiply(shifted, xfrm, stands);
        multiply(eye, stands, paneMatrix);
        Body read = new Body(mesh, instance.textureMask, script);
        int[] upright = uprights.get(read);
        if (upright == null) {
            upright = Cabins.upright(stands, faces);
            if (upright != null) {
                uprights.put(read, upright);
                if (DevBuild.ON) {
                    Log.info(String.format(Locale.ROOT, "the body of %s stands with its model's %s axis up and its %s axis along it", script, AXES[upright[0] / 4], AXES[upright[2] / 4]));
                }
            }
        }
        if (upright == null || !Cabins.axes(stands, upright, faces, seatedAxes)) {
            System.arraycopy(faces, 0, seatedAxes, 0, 9);
            if (turnedSaid.add(script)) {
                Log.info("the body of " + script + " does not lie along the way its vehicle faces: its cabin is laid out by the vehicle as the physics has it, not by the body as it is drawn");
            }
        }
        float[] axes = seatedAxes;
        if (FirstPerson.TRACE && (tipSaid++ & 7) == 0) {
            Log.info(String.format(Locale.ROOT, "body of %s: nose up %.2f, left side up %.2f, its up %.2f off straight up; off the vehicle's own as the physics has it, the nose %.2f and the up %.2f, in degrees",
                    script, Math.toDegrees(Math.asin(axes[2])), Math.toDegrees(Math.asin(axes[5])), Math.toDegrees(Math.acos(Math.clamp(axes[8], -1f, 1f))),
                    Math.toDegrees(Math.acos(Math.clamp(axes[0] * faces[0] + axes[1] * faces[1] + axes[2] * faces[2], -1f, 1f))),
                    Math.toDegrees(Math.acos(Math.clamp(axes[6] * faces[6] + axes[7] * faces[7] + axes[8] * faces[8], -1f, 1f)))));
        }
        float[] middle = middles.get(read);
        if (middle == null) {
            // A mask still on its way from the disc reads as empty, and no glass would be the
            // answer kept for the body from then on.
            if (!mesh.isReady() || mesh.vb == null || instance.textureMask != null && !instance.textureMask.isReady()) {
                return;
            }
            // So does a body the game has not placed yet, whose matrix is all noughts.
            float turns = 0f;
            for (int column = 0; column < 12; column += 4) {
                turns += stands[column] * stands[column] + stands[column + 1] * stands[column + 1] + stands[column + 2] * stands[column + 2];
            }
            if (!(turns > 0f)) {
                return;
            }
            for (int column = 0; column < 4; column++) {
                float shift = column == 3 ? 1f : 0f;
                float x = stands[column * 4] - standsX * shift, y = stands[column * 4 + 1] - standsY * shift, up = stands[column * 4 + 2] - groundUp * shift;
                for (int axis = 0; axis < 3; axis++) {
                    frame[axis * 4 + column] = x * axes[axis * 3] + y * axes[axis * 3 + 1] + up * axes[axis * 3 + 2];
                }
            }
            middle = find(read, instance, body.modelInstance.model.name, ride.seats);
            middles.put(read, middle);
        }
        FloatBuffer glass = panes.get(read);
        if (glass != null && glass.capacity() > 0) {
            seated.panes = glass;
            seated.rearPanes = rearPanes.get(read);
        }
        seated.parts = parts.get(read);
        paint(instance.painColor.x, Math.clamp(instance.painColor.y, 0f, 1f), Math.clamp(instance.painColor.z, 0f, 1f), seated.paint);
        if (middle == NONE) {
            return;
        }
        // On the body as it is drawn, tipped and rolled with it.
        float bodyX = stands[0] * middle[0] + stands[4] * middle[1] + stands[8] * middle[2] + stands[12];
        float bodyY = stands[1] * middle[0] + stands[5] * middle[1] + stands[9] * middle[2] + stands[13];
        float bodyUp = stands[2] * middle[0] + stands[6] * middle[1] + stands[10] * middle[2] + stands[14];
        float x = bodyX - axes[6] * settles, y = bodyY - axes[7] * settles, up = bodyUp - axes[8] * settles;
        seated.wheelNow[0] = x;
        seated.wheelNow[1] = y;
        seated.wheelNow[2] = up;
        seated.wheel = seated.wheelNow;
        // The game's physics tips and rolls the body about its centre of mass, so the wheel is
        // measured from there, and the eye is turned about the same point.
        float toX = x - shifted[12], toY = y - shifted[13], toUp = up - shifted[14];
        float along = toX * axes[0] + toY * axes[1] + toUp * axes[2];
        float across = toX * axes[3] + toY * axes[4] + toUp * axes[5];
        float above = toX * axes[6] + toY * axes[7] + toUp * axes[8];
        float side = (bodyX - stands[12]) * axes[3] + (bodyY - stands[13]) * axes[4] + (bodyUp - stands[14]) * axes[5];
        float tall = (float) Math.sqrt(placed[4] * placed[4] + placed[5] * placed[5] + placed[6] * placed[6]);
        float centre = ((shifted[12] - standsX) * placed[4] + (shifted[13] - standsY) * placed[5] + (shifted[14] - groundUp) * placed[6]) / tall;
        float[] known = ride.wheels.get(script);
        if (known == null) {
            if (DevBuild.ON) {
                Log.info(String.format(Locale.ROOT, "wheel of %s (%s): %.3f along, %.3f across, %.3f up from the centre of mass %.3f above the ground, %.3f from the body's middle",
                        script, body.modelInstance.model.name, along, across, above, centre, side));
            }
            // Repeated world-to-local measurements feed motion and rounding into the next frame's seat position.
            ride.wheels.put(script, new float[] {along, across, above, side, centre});
        }
    }

    /**
     * A vehicle's paint. The game gives a body's skin this hue and moves the skin's strength
     * and lightness by the other two. The skin is not read here: the two are taken as the
     * paint's own, which is right for a skin of middling strength and lightness.
     */
    private static void paint(float hue, float strength, float light, float[] out) {
        for (int channel = 0; channel < 3; channel++) {
            float turn = (hue * 6f + (channel == 0 ? 0f : channel == 1 ? 4f : 2f)) % 6f;
            float ramp = Math.clamp(Math.abs(turn - 3f) - 1f, 0f, 1f);
            out[channel] = light * (1f - strength + strength * ramp);
        }
    }

    /**
     * The middle of the wheel in a body's vertices, read back from the graphics card once a body.
     *
     * @param seats where the vehicle's script seats its passengers, as Ride has them
     */
    private static float[] find(Body body, VehicleModelInstance instance, String name, float[] seats) {
        VertexBufferObject buffer = body.mesh().vb;
        try {
            if (handleField == null) {
                handleField = VertexBufferObject.class.getDeclaredField(Links.BUFFER_HANDLE.name());
                handleField.setAccessible(true);
                elementsField = VertexBufferObject.class.getDeclaredField(Links.BUFFER_ELEMENTS.name());
                elementsField.setAccessible(true);
            }
            Object handle = handleField.get(buffer);
            int[] elements = (int[]) elementsField.get(buffer);
            if (handle == null || elements == null) {
                throw new IllegalStateException("the vehicle model " + name + " has no buffer to read");
            }
            if (bufferField == null) {
                bufferField = handle.getClass().getDeclaredField(Links.HANDLE_BUFFER.name());
                bufferField.setAccessible(true);
                strideField = handle.getClass().getDeclaredField(Links.HANDLE_STRIDE.name());
                strideField.setAccessible(true);
            }
            int stride = strideField.getInt(handle);
            int bound = GL11.glGetInteger(GL_ARRAY_BUFFER_BINDING);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, bufferField.getInt(handle));
            int bytes = GL15.glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE);
            String lacks = lacks(stride, bytes, elements);
            if (lacks != null) {
                GL15.glBindBuffer(GL_ARRAY_BUFFER, bound);
                Log.info("the vehicle model " + name + " is not laid out as the game's own bodies are (" + lacks + "): its driver's place and its glass are the game's own");
                return NONE;
            }
            // The body's vertices as the graphics card has them, read once and given back when what is kept of them is made.
            OffHeap.Block read = OffHeap.take(bytes);
            try {
                ByteBuffer raw = read.bytes;
                GL15.glGetBufferSubData(GL_ARRAY_BUFFER, 0L, raw);
                GL15.glBindBuffer(GL_ARRAY_BUFFER, bound);
                boolean[] glass = glassIn(raw, stride, elements, instance, name);
                float[] wheel = wheelIn(raw, stride, bytes / stride, elements, name, seats);
                boolean[] front = new boolean[glass.length];
                if (wheel != NONE) {
                    Parts made = partsIn(raw, stride, bytes / stride, elements, glass, wheel, name, seats);
                    parts.put(body, made);
                    front = made.front;
                }
                FloatBuffer loose = loose(raw, stride, elements, glass, front);
                panes.put(body, loose);
                int rear = 0;
                for (int t = 0; t < glass.length; t++) {
                    rear += glass[t] && !front[t] ? 3 : 0;
                }
                rearPanes.put(body, rear);
                return wheel;
            } finally {
                read.free();
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the game's vertex buffer is not laid out as this build of the mod knows it: " + e, e);
        }
    }

    /**
     * What of a body's buffer is not as the reads below take it, or null: a place, a normal and a
     * texture place of floats at bytes 0, 12 and 24 of each vertex, as every body of the game's
     * has them, and triangles that name only vertices the buffer holds. A body a vehicle mod
     * supplies need not be.
     */
    static String lacks(int stride, int bytes, int[] elements) {
        if (stride < PLACE_NORMAL_AND_TEXTURE_PLACE) {
            return stride + " bytes a vertex";
        }
        if (elements.length % 3 != 0) {
            return elements.length + " corners, which is not a list of triangles";
        }
        int vertices = bytes / stride;
        for (int element : elements) {
            if (element < 0 || element >= vertices) {
                return "a corner at vertex " + element + " of " + vertices;
            }
        }
        return null;
    }

    private static float[] wheelIn(ByteBuffer raw, int stride, int vertices, int[] elements, String name, float[] seats) {
        // The body as it stands this frame, in tiles along the vehicle, across it and up:
        // which of its pieces is the wheel is told by where the driver sits.
        float[] at = new float[vertices * 3];
        for (int i = 0; i < vertices; i++) {
            for (int row = 0; row < 3; row++) {
                float place = frame[row * 4 + 3];
                for (int axis = 0; axis < 3; axis++) {
                    place += frame[row * 4 + axis] * raw.getFloat(i * stride + axis * 4);
                }
                at[i * 3 + row] = place;
            }
        }
        int[] ring = CabinParts.wheelOf(at, elements, seats == null || seats.length < 3 ? null : new float[] {frame[3] + seats[2], Math.abs(seats[0])}, frame[7]);
        if (ring == null) {
            Log.info("the vehicle model " + name + " has no steering wheel the picture can find (" + elements.length / 3 + " triangles, none a piece of " + WHEEL_TRIANGLES
                    + "): its driver's place is the game's own");
            return NONE;
        }
        float[] low = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, high = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i : ring) {
            for (int axis = 0; axis < 3; axis++) {
                float v = raw.getFloat(i * stride + axis * 4);
                low[axis] = Math.min(low[axis], v);
                high[axis] = Math.max(high[axis], v);
            }
        }
        return new float[] {(low[0] + high[0]) / 2f, (low[1] + high[1]) / 2f, (low[2] + high[2]) / 2f};
    }

    /**
     * Which triangles of a body are glass. The game's vehicle mask paints each window in a
     * colour of its own, every channel of it nothing or a half, and a triangle is a pane when
     * the mask at its middle is one of those. None are, for a body whose mask does not say.
     */
    private static boolean[] glassIn(ByteBuffer raw, int stride, int[] elements, VehicleModelInstance instance, String name) {
        boolean[] none = new boolean[elements.length / 3];
        if (instance.textureMask == null || stride < 32) {
            Log.info("the vehicle model " + name + " has no mask to tell its glass by: its windows are told by their colour");
            return none;
        }
        int was = GL11.glGetInteger(GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL_TEXTURE_2D, instance.textureMask.getID());
        int width = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH), height = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        if (width <= 0 || height <= 0) {
            GL11.glBindTexture(GL_TEXTURE_2D, was);
            Log.info("the vehicle model " + name + " has an empty mask: its windows are told by their colour");
            return none;
        }
        if ((long) width * height > MOST_TEXELS) {
            // Four bytes a texel would pass what an int holds at 32768 a side, and the read would write past the block.
            GL11.glBindTexture(GL_TEXTURE_2D, was);
            Log.info("the vehicle model " + name + " has a mask of " + width + " by " + height + ", too large to read back: its windows are told by their colour");
            return none;
        }
        // The mask is as big as the vehicle's skin: read out of memory that goes back when the triangles are told apart.
        OffHeap.Block read = OffHeap.take(width * height * 4);
        try {
            GL11.glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, read.bytes);
            GL11.glBindTexture(GL_TEXTURE_2D, was);
            return glassBy(read.bytes, width, height, raw, stride, elements, name);
        } finally {
            read.free();
        }
    }

    private static boolean[] glassBy(ByteBuffer mask, int width, int height, ByteBuffer raw, int stride, int[] elements, String name) {
        int triangles = elements.length / 3;
        boolean[][] glass = new boolean[2][triangles];
        int[] found = new int[2];
        for (int flip = 0; flip < 2; flip++) {
            for (int t = 0; t < triangles; t++) {
                float u = 0f, v = 0f;
                for (int corner = 0; corner < 3; corner++) {
                    int at = elements[t * 3 + corner] * stride + 24;
                    u += raw.getFloat(at) / 3f;
                    v += raw.getFloat(at + 4) / 3f;
                }
                u -= (float) Math.floor(u);
                v -= (float) Math.floor(v);
                int x = Math.min((int) (u * width), width - 1), y = Math.min((int) ((flip == 0 ? v : 1f - v) * height), height - 1);
                int texel = (y * width + x) * 4;
                int halves = 0, nones = 0;
                for (int channel = 0; channel < 3; channel++) {
                    int value = mask.get(texel + channel) & 0xff;
                    if (value < 40) {
                        nones++;
                    } else if (value > 100 && value < 156) {
                        halves++;
                    }
                }
                glass[flip][t] = halves + nones == 3 && halves >= 1 && halves <= 2;
                if (glass[flip][t]) {
                    found[flip]++;
                }
            }
        }
        // The mask lies the same way up against every model: the first way.
        int way = 0;
        if (DevBuild.ON) {
            Log.info("glass of " + name + ": " + found[0] + " triangles one way up, " + found[1] + " the other, of " + triangles + "; mask " + width + "x" + height);
        }
        return found[way] < 6 || found[way] > triangles / 3 ? new boolean[triangles] : glass[way];
    }

    /**
     * The panes of a body as loose triangles in the model's own space, those of the front of
     * the glasshouse after the rest. Empty for a body with none found.
     */
    private static FloatBuffer loose(ByteBuffer raw, int stride, int[] elements, boolean[] glass, boolean[] front) {
        int found = 0;
        for (boolean pane : glass) {
            found += pane ? 1 : 0;
        }
        FloatBuffer out = ByteBuffer.allocateDirect(found * 9 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        for (int pass = 0; pass < 2; pass++) {
            for (int t = 0; t < glass.length; t++) {
                if (glass[t] && front[t] == (pass == 1)) {
                    for (int corner = 0; corner < 3; corner++) {
                        int at = elements[t * 3 + corner] * stride;
                        out.put(raw.getFloat(at)).put(raw.getFloat(at + 4)).put(raw.getFloat(at + 8));
                    }
                }
            }
        }
        out.flip();
        return out;
    }

    /**
     * What part of the cabin each triangle of a body is. The body is measured as it stands
     * this frame, in tiles along the vehicle, across it and up, which is the frame the script
     * seats its passengers in.
     */
    private static Parts partsIn(ByteBuffer raw, int stride, int vertices, int[] elements, boolean[] glass, float[] wheel, String name, float[] scriptSeats) {
        float[] at = new float[vertices * 3], facing = new float[vertices * 3];
        for (int i = 0; i < vertices; i++) {
            for (int row = 0; row < 3; row++) {
                float place = frame[row * 4 + 3], faces = 0f;
                for (int axis = 0; axis < 3; axis++) {
                    place += frame[row * 4 + axis] * raw.getFloat(i * stride + axis * 4);
                    faces += frame[row * 4 + axis] * raw.getFloat(i * stride + 12 + axis * 4);
                }
                at[i * 3 + row] = place;
                facing[i * 3 + row] = faces;
            }
        }
        float[] middle = new float[3];
        for (int row = 0; row < 3; row++) {
            middle[row] = frame[row * 4] * wheel[0] + frame[row * 4 + 1] * wheel[1] + frame[row * 4 + 2] * wheel[2] + frame[row * 4 + 3];
        }
        float[] seats = CabinParts.seats(scriptSeats, frame[3], frame[7], middle[1]);
        CabinParts.Found found = CabinParts.classify(at, facing, elements, glass, middle, seats);
        Parts made = new Parts(ByteBuffer.allocateDirect(elements.length * 3 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer(), found.seatPieces > 0,
                new float[] {found.footAlong - middle[0], found.footUp - middle[2], found.sillUp - middle[2], found.glassOut - Math.abs(middle[1] - found.middleAcross),
                    found.middleAcross - middle[1], found.dashTop - middle[2], found.dashBack - middle[0]}, found.front);
        for (int kind = 0; kind < CabinParts.KINDS; kind++) {
            made.first[kind] = made.triangles.position() / 3;
            for (int t = 0; t < found.kinds.length; t++) {
                if (found.kinds[t] == kind) {
                    for (int corner = 0; corner < 3; corner++) {
                        int v = elements[t * 3 + corner] * stride;
                        made.triangles.put(raw.getFloat(v)).put(raw.getFloat(v + 4)).put(raw.getFloat(v + 8));
                    }
                }
            }
            made.count[kind] = made.triangles.position() / 3 - made.first[kind];
        }
        made.triangles.flip();
        if (DevBuild.ON) {
            Log.info(String.format(Locale.ROOT,
                    "cabin of %s: %d seat pieces, and %d seat, %d dashboard, %d door and %d bonnet vertices; the driver sits %.3f behind the wheel and %.3f beside it;"
                            + " windscreen foot %.3f ahead of the wheel and %.3f above, sill %.3f above, dashboard up to %.3f",
                    name, found.seatPieces, made.count[CabinParts.SEAT], made.count[CabinParts.DASH], made.count[CabinParts.DOOR], made.count[CabinParts.BONNET],
                    seats.length == 0 ? Float.NaN : middle[0] - seats[0], seats.length == 0 ? Float.NaN : seats[1] - middle[1],
                    made.measures[0], made.measures[1], made.measures[2], made.measures[5]));
        }
        if (!made.seats) {
            Log.info("the vehicle model " + name + " has no seat that is a piece of its own: its seats are told by their colour");
        }
        return made;
    }

    private static final IdentityHashMap<Texture, float[]> means = new IdentityHashMap<>();
    /** A picture bigger than this many texels is not read back for its colour. */
    private static final int MOST_TEXELS = 4096 * 4096;

    /**
     * The mean colour of a picture of the game's, each texel counted by how solid it is, read
     * back from the graphics card once. Null while the picture is not loaded, and for one
     * that is empty or too big to read. A picture may be one piece of a larger sheet.
     */
    static float[] meanOf(Texture picture) {
        if (means.containsKey(picture)) {
            return means.get(picture);
        }
        if (!picture.isReady() || picture.getID() <= 0) {
            return null;
        }
        int was = GL11.glGetInteger(GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL_TEXTURE_2D, picture.getID());
        int width = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH), height = GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT);
        float[] mean = null;
        if (width > 0 && height > 0 && (long) width * height <= MOST_TEXELS) {
            // A garment's picture is one piece of a sheet of up to 64 MB, read whole: out of memory that goes back at once.
            OffHeap.Block read = OffHeap.take(width * height * 4);
            try {
                GL11.glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, read.bytes);
                mean = meanOver(read.bytes, picture, width, height);
            } finally {
                read.free();
            }
        }
        GL11.glBindTexture(GL_TEXTURE_2D, was);
        means.put(picture, mean);
        return mean;
    }

    /**
     * Gives up what was read of the game's meshes and pictures, which are the game's to load
     * anew between saves: each is read again when it is next sat in or worn. They are read
     * once for every view and given up when none draws: see Characters.letGo. Render thread.
     */
    static void letGo() {
        middles.clear();
        panes.clear();
        rearPanes.clear();
        parts.clear();
        means.clear();
    }

    private static float[] meanOver(ByteBuffer texels, Texture picture, int width, int height) {
        int fromX = Math.clamp((int) (Math.min(picture.getXStart(), picture.getXEnd()) * width), 0, width - 1);
        int toX = Math.clamp((int) Math.ceil(Math.max(picture.getXStart(), picture.getXEnd()) * width), fromX + 1, width);
        int fromY = Math.clamp((int) (Math.min(picture.getYStart(), picture.getYEnd()) * height), 0, height - 1);
        int toY = Math.clamp((int) Math.ceil(Math.max(picture.getYStart(), picture.getYEnd()) * height), fromY + 1, height);
        long red = 0, green = 0, blue = 0, solid = 0;
        for (int y = fromY; y < toY; y++) {
            for (int x = fromX; x < toX; x++) {
                int at = (y * width + x) * 4;
                int alpha = texels.get(at + 3) & 0xff;
                red += (texels.get(at) & 0xff) * alpha;
                green += (texels.get(at + 1) & 0xff) * alpha;
                blue += (texels.get(at + 2) & 0xff) * alpha;
                solid += alpha;
            }
        }
        return solid > 0 ? new float[] {red / (255f * solid), green / (255f * solid), blue / (255f * solid)} : null;
    }

    private static void multiply(float[] a, float[] b, float[] out) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[column * 4 + k];
                }
                out[column * 4 + row] = sum;
            }
        }
    }
}
