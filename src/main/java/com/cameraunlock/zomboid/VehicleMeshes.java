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

    private static Field handleField, elementsField, bufferField, strideField;
    private static final float[] xfrm = new float[16];

    /**
     * Called on the render thread for the vehicle the viewer sits in, with the matrix that
     * stands it in the world, how far below that the game's draw then sets the body (it hangs
     * the model from the vehicle's centre of mass), and where the vehicle stands.
     */
    static void note(String script, ModelInstanceRenderData body, float[] placed, float lowered, float standsX, float standsY, float groundUp) {
        if (!(body.modelInstance instanceof VehicleModelInstance) || body.modelInstance.model == null) {
            return;
        }
        ModelMesh mesh = body.modelInstance.model.mesh;
        if (mesh == null) {
            return;
        }
        float[] middle = middles.get(mesh);
        if (middle == null) {
            if (!mesh.isReady() || mesh.vb == null) {
                return;
            }
            middle = find(mesh.vb, body.modelInstance.model.name);
            middles.put(mesh, middle);
        }
        if (middle == NONE) {
            return;
        }
        body.xfrm.get(xfrm);
        float mx = xfrm[0] * middle[0] + xfrm[4] * middle[1] + xfrm[8] * middle[2] + xfrm[12];
        float my = xfrm[1] * middle[0] + xfrm[5] * middle[1] + xfrm[9] * middle[2] + xfrm[13];
        float mz = xfrm[2] * middle[0] + xfrm[6] * middle[1] + xfrm[10] * middle[2] + xfrm[14];
        float x = placed[0] * mx + placed[4] * my + placed[8] * mz + placed[12];
        float y = placed[1] * mx + placed[5] * my + placed[9] * mz + placed[13];
        float up = placed[2] * mx + placed[6] * my + placed[10] * mz + placed[14] - lowered;
        float aheadX = FirstPerson.aheadX, aheadY = FirstPerson.aheadY;
        float along = (x - standsX) * aheadX + (y - standsY) * aheadY;
        float across = -(x - standsX) * aheadY + (y - standsY) * aheadX;
        float[] known = wheels.get(script);
        if (known == null) {
            Log.info(String.format(java.util.Locale.ROOT, "wheel of %s (%s): %.3f along, %.3f across, %.3f up", script, body.modelInstance.model.name, along, across, up - groundUp));
        }
        wheels.put(script, new float[] {along, across, up - groundUp});
    }

    /** The middle of the wheel in a body's vertices, read back from the graphics card once. */
    private static float[] find(VertexBufferObject buffer, String name) {
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
            return wheelIn(raw, stride, bytes / stride, elements, name);
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

    private static int root(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }
}
