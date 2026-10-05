package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelMesh;
import zombie.core.skinnedmodel.model.VehicleModelInstance;
import zombie.core.skinnedmodel.model.VertexBufferObject;

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

    /** The wheel's middle in each model's own space, or NONE for a model with no such piece. */
    private static final IdentityHashMap<ModelMesh, float[]> middles = new IdentityHashMap<>();
    /**
     * For each vehicle script the viewer has sat in: the wheel's middle from where the vehicle
     * stands, along it, across it and up from the ground under it, in tiles.
     */
    static final ConcurrentHashMap<String, float[]> wheels = new ConcurrentHashMap<>();

    /**
     * The panes of each model, as loose triangles in the model's own space: the vehicle's mask
     * texture says which of its triangles are glass. Empty for a model with none found.
     */
    private static final IdentityHashMap<ModelMesh, java.nio.FloatBuffer> panes = new IdentityHashMap<>();
    /**
     * The panes of the vehicle the viewer sits in, and the matrix that puts them on the screen.
     * Null with none. The first seatedRearPanes vertices are the panes behind the front of the
     * glasshouse; the rest are the screen and the front windows, where the picture builds its own.
     */
    static java.nio.FloatBuffer seatedPanes;
    static int seatedRearPanes;
    private static final IdentityHashMap<ModelMesh, Integer> rearPanes = new IdentityHashMap<>();
    static final float[] paneMatrix = new float[16];
    private static final float[] shifted = new float[16], stands = new float[16];

    /** The triangles of a body that are not glass, sorted by what part of the cabin each is, in the model's own space. */
    static final class Parts {
        final java.nio.FloatBuffer triangles;
        /** For each kind CabinParts tells, where its vertices begin and how many they are. */
        final int[] first = new int[CabinParts.KINDS], count = new int[CabinParts.KINDS];
        /** Whether the seats were found as pieces of their own. Where they were not, they are not told from the shell. */
        final boolean seats;
        /**
         * From the wheel's middle, in tiles: the foot of the windscreen, along and up; the
         * sill of the driver's window, up; how far out from the wheel that window is; how far
         * across the vehicle's middle line is, by sign; the top of the dashboard, up; and
         * the mirror on the driver's door, along, out and up; and where the shelf of the
         * dashboard begins, along.
         */
        final float[] measures;
        /** For each triangle of the body, whether it is a pane of the front of the glasshouse. */
        final boolean[] front;

        Parts(java.nio.FloatBuffer triangles, boolean seats, float[] measures, boolean[] front) {
            this.triangles = triangles;
            this.seats = seats;
            this.measures = measures;
            this.front = front;
        }
    }

    private static final IdentityHashMap<ModelMesh, Parts> parts = new IdentityHashMap<>();
    /** The parts of the vehicle the viewer sits in, drawn with paneMatrix as the panes are. Null with none. */
    static Parts seatedParts;
    /** Where the wheel of the vehicle the viewer sits in is in the world this frame, or null: x, y and up. */
    static float[] seatedWheel;
    private static final float[] wheelNow = new float[3];
    /** The paint of the vehicle the viewer sits in. */
    static final float[] seatedPaint = new float[3];
    /** What turns a vertex of the body into tiles along the vehicle, across it and up from the ground: three rows of four. */
    private static final float[] frame = new float[12];
    private static final int GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE_BINDING_2D = 0x8069, GL_TEXTURE_WIDTH = 0x1000, GL_TEXTURE_HEIGHT = 0x1001;
    private static final int GL_RGBA = 0x1908, GL_UNSIGNED_BYTE = 0x1401;

    private static Field handleField, elementsField, bufferField, strideField;
    private static final float[] xfrm = new float[16];

    /**
     * Called on the render thread for the vehicle the viewer sits in, with the matrix that
     * stands it in the world, how far below that the game's draw then sets the body (it hangs
     * the model from the vehicle's centre of mass), and where the vehicle stands.
     */
    static void note(String script, ModelInstanceRenderData body, float[] placed, float lowered, float standsX, float standsY, float groundUp,
            float centreOfMass, float[] eye) {
        seatedPanes = null;
        seatedParts = null;
        seatedWheel = null;
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
        float aheadX = FirstPerson.aheadX, aheadY = FirstPerson.aheadY;
        float[] middle = middles.get(mesh);
        if (middle == null) {
            if (!mesh.isReady() || mesh.vb == null) {
                return;
            }
            for (int column = 0; column < 4; column++) {
                float shift = column == 3 ? 1f : 0f;
                frame[column] = (stands[column * 4] - standsX * shift) * aheadX + (stands[column * 4 + 1] - standsY * shift) * aheadY;
                frame[4 + column] = -(stands[column * 4] - standsX * shift) * aheadY + (stands[column * 4 + 1] - standsY * shift) * aheadX;
                frame[8 + column] = stands[column * 4 + 2] - groundUp * shift;
            }
            middle = find(mesh, instance, body.modelInstance.model.name);
            middles.put(mesh, middle);
        }
        java.nio.FloatBuffer glass = panes.get(mesh);
        if (glass != null && glass.capacity() > 0) {
            seatedPanes = glass;
            seatedRearPanes = rearPanes.get(mesh);
        }
        seatedParts = parts.get(mesh);
        paint(instance.painColor.x, Math.clamp(instance.painColor.y, 0f, 1f), Math.clamp(instance.painColor.z, 0f, 1f), seatedPaint);
        if (middle == NONE) {
            return;
        }
        float mx = xfrm[0] * middle[0] + xfrm[4] * middle[1] + xfrm[8] * middle[2] + xfrm[12];
        float my = xfrm[1] * middle[0] + xfrm[5] * middle[1] + xfrm[9] * middle[2] + xfrm[13];
        float mz = xfrm[2] * middle[0] + xfrm[6] * middle[1] + xfrm[10] * middle[2] + xfrm[14];
        float x = placed[0] * mx + placed[4] * my + placed[8] * mz + placed[12];
        float y = placed[1] * mx + placed[5] * my + placed[9] * mz + placed[13];
        float up = placed[2] * mx + placed[6] * my + placed[10] * mz + placed[14] - lowered;
        wheelNow[0] = x;
        wheelNow[1] = y;
        wheelNow[2] = up;
        seatedWheel = wheelNow;
        float along = (x - standsX) * aheadX + (y - standsY) * aheadY;
        float across = -(x - standsX) * aheadY + (y - standsY) * aheadX;
        float[] known = wheels.get(script);
        if (known == null) {
            Log.info(String.format(java.util.Locale.ROOT, "wheel of %s (%s): %.3f along, %.3f across, %.3f up", script, body.modelInstance.model.name, along, across, up - groundUp));
        }
        wheels.put(script, new float[] {along, across, up - groundUp});
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

    /** The middle of the wheel in a body's vertices, read back from the graphics card once. */
    private static float[] find(ModelMesh mesh, VehicleModelInstance instance, String name) {
        VertexBufferObject buffer = mesh.vb;
        try {
            if (handleField == null) {
                handleField = VertexBufferObject.class.getDeclaredField("handle");
                handleField.setAccessible(true);
                elementsField = VertexBufferObject.class.getDeclaredField("elements");
                elementsField.setAccessible(true);
            }
            Object handle = handleField.get(buffer);
            int[] elements = (int[]) elementsField.get(buffer);
            if (handle == null || elements == null) {
                throw new IllegalStateException("the vehicle model " + name + " has no buffer to read");
            }
            if (bufferField == null) {
                bufferField = handle.getClass().getDeclaredField("vboId");
                bufferField.setAccessible(true);
                strideField = handle.getClass().getDeclaredField("vertexStride");
                strideField.setAccessible(true);
            }
            int stride = strideField.getInt(handle);
            int bound = GL11.glGetInteger(GL_ARRAY_BUFFER_BINDING);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, bufferField.getInt(handle));
            int bytes = GL15.glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE);
            ByteBuffer raw = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
            GL15.glGetBufferSubData(GL_ARRAY_BUFFER, 0L, raw);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, bound);
            boolean[] glass = glassIn(raw, stride, elements, instance, name);
            float[] wheel = wheelIn(raw, stride, bytes / stride, elements, name);
            boolean[] front = new boolean[glass.length];
            if (wheel != NONE) {
                Parts made = partsIn(raw, stride, bytes / stride, elements, glass, wheel, name);
                parts.put(mesh, made);
                front = made.front;
            }
            java.nio.FloatBuffer loose = loose(raw, stride, elements, glass, front);
            panes.put(mesh, loose);
            int rear = 0;
            for (int t = 0; t < glass.length; t++) {
                rear += glass[t] && !front[t] ? 3 : 0;
            }
            rearPanes.put(mesh, rear);
            return wheel;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the game's vertex buffer is not laid out as this build of the mod knows it: " + e, e);
        }
    }

    private static float[] wheelIn(ByteBuffer raw, int stride, int vertices, int[] elements, String name) {
        // Vertices at one place are one vertex, however many faces meet there with their own
        // normals and texture coordinates.
        java.util.HashMap<Long, Integer> places = new java.util.HashMap<>();
        int[] welded = new int[vertices];
        for (int i = 0; i < vertices; i++) {
            long key = Math.round(raw.getFloat(i * stride) * 1000f) * 73856093L ^ Math.round(raw.getFloat(i * stride + 4) * 1000f) * 19349663L
                    ^ Math.round(raw.getFloat(i * stride + 8) * 1000f) * 83492791L;
            Integer first = places.putIfAbsent(key, i);
            welded[i] = first == null ? i : first;
        }
        int[] parent = new int[vertices];
        for (int i = 0; i < vertices; i++) {
            parent[i] = i;
        }
        int triangles = elements.length / 3;
        for (int t = 0; t < triangles; t++) {
            int a = root(parent, welded[elements[t * 3]]);
            int b = root(parent, welded[elements[t * 3 + 1]]);
            int c = root(parent, welded[elements[t * 3 + 2]]);
            parent[b] = a;
            parent[c] = a;
        }
        int[] count = new int[vertices];
        for (int t = 0; t < triangles; t++) {
            count[root(parent, welded[elements[t * 3]])]++;
        }
        float[] best = NONE;
        float flattest = Float.MAX_VALUE;
        for (int piece = 0; piece < vertices; piece++) {
            if (count[piece] != WHEEL_TRIANGLES) {
                continue;
            }
            float[] low = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE}, high = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int i = 0; i < vertices; i++) {
                if (root(parent, welded[i]) != piece) {
                    continue;
                }
                for (int axis = 0; axis < 3; axis++) {
                    float v = raw.getFloat(i * stride + axis * 4);
                    low[axis] = Math.min(low[axis], v);
                    high[axis] = Math.max(high[axis], v);
                }
            }
            // A wheel is as wide as it is tall and thin through: of the pieces of the right
            // size, the one whose two larger sides are most alike.
            float[] sides = {high[0] - low[0], high[1] - low[1], high[2] - low[2]};
            java.util.Arrays.sort(sides);
            float unlike = sides[2] / Math.max(sides[1], 1e-6f);
            if (unlike < flattest) {
                flattest = unlike;
                best = new float[] {(low[0] + high[0]) / 2f, (low[1] + high[1]) / 2f, (low[2] + high[2]) / 2f};
            }
        }
        if (best == NONE) {
            Log.info("the vehicle model " + name + " has no steering wheel the picture can find (" + triangles + " triangles, none a piece of " + WHEEL_TRIANGLES
                    + "): its driver's place is the game's own");
        }
        return best;
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
        ByteBuffer mask = ByteBuffer.allocateDirect(Math.max(width * height * 4, 4)).order(ByteOrder.nativeOrder());
        if (width > 0 && height > 0) {
            GL11.glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, mask);
        }
        GL11.glBindTexture(GL_TEXTURE_2D, was);
        if (width <= 0 || height <= 0) {
            Log.info("the vehicle model " + name + " has an empty mask: its windows are told by their colour");
            return none;
        }
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
        Log.info("glass of " + name + ": " + found[0] + " triangles one way up, " + found[1] + " the other, of " + triangles + "; mask " + width + "x" + height);
        return found[way] < 6 || found[way] > triangles / 3 ? none : glass[way];
    }

    /**
     * The panes of a body as loose triangles in the model's own space, those of the front of
     * the glasshouse after the rest. Empty for a body with none found.
     */
    private static java.nio.FloatBuffer loose(ByteBuffer raw, int stride, int[] elements, boolean[] glass, boolean[] front) {
        int found = 0;
        for (boolean pane : glass) {
            found += pane ? 1 : 0;
        }
        java.nio.FloatBuffer out = ByteBuffer.allocateDirect(found * 9 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
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
    private static Parts partsIn(ByteBuffer raw, int stride, int vertices, int[] elements, boolean[] glass, float[] wheel, String name) {
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
        float[] seats = CabinParts.seats(FirstPerson.seats, frame[3], frame[7], middle[1]);
        CabinParts.Found found = CabinParts.classify(at, facing, elements, glass, middle, seats);
        Parts made = new Parts(ByteBuffer.allocateDirect(elements.length * 3 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer(), found.seatPieces > 0,
                new float[] {found.footAlong - middle[0], found.footUp - middle[2], found.sillUp - middle[2], found.glassOut - Math.abs(middle[1] - found.middleAcross),
                    found.middleAcross - middle[1], found.dashTop - middle[2],
                    found.mirror[0] - middle[0], Math.abs(found.mirror[1] - found.middleAcross) - Math.abs(middle[1] - found.middleAcross), found.mirror[2] - middle[2],
                    found.dashBack - middle[0]}, found.front);
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
        Log.info(String.format(java.util.Locale.ROOT,
                "cabin of %s: %d seat pieces, and %d seat, %d dashboard, %d door and %d bonnet vertices; the driver sits %.3f behind the wheel and %.3f beside it;"
                        + " windscreen foot %.3f ahead of the wheel and %.3f above, sill %.3f above, dashboard up to %.3f, door mirror %.3f ahead, %.3f out, %.3f above",
                name, found.seatPieces, made.count[CabinParts.SEAT], made.count[CabinParts.DASH], made.count[CabinParts.DOOR], made.count[CabinParts.BONNET],
                seats.length == 0 ? Float.NaN : middle[0] - seats[0], seats.length == 0 ? Float.NaN : seats[1] - middle[1],
                made.measures[0], made.measures[1], made.measures[2], made.measures[5], made.measures[6], made.measures[7], made.measures[8]));
        if (!made.seats) {
            Log.info("the vehicle model " + name + " has no seat that is a piece of its own: its seats are told by their colour");
        }
        return made;
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

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }
}
