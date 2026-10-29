package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL15;
import zombie.core.Core;
import zombie.core.opengl.RenderThread;
import zombie.core.skinnedmodel.model.ItemModelRenderer;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelMesh;
import zombie.core.skinnedmodel.model.VertexBufferObject;
import zombie.core.skinnedmodel.shader.ShaderManager;
import zombie.core.textures.Texture;
import zombie.inventory.InventoryItem;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoWorldInventoryObject;

/**
 * The game's own models of loose items, as its "3D ground items" option draws them, for the
 * first-person picture to build into the world where it would lay the item's picture flat.
 *
 * The game's own renderer of an item on the ground is asked on the logic thread, as the game
 * asks it, which model the item takes, in what texture and colour, and how it is turned and
 * placed. The model's triangles are on the graphics card only, and are read back once per mesh
 * on the render thread: until they are, the item lies as its picture.
 */
public final class ItemModels {
    private ItemModels() {}

    /**
     * How near the eye a chunk's band is, in tiles, for its items to be built as models: past
     * it an item is a few pixels across and lies as its picture.
     */
    static final float REACH = 24f;
    /** How near the eye a chunk's band is, in tiles, for the models of its items to be loaded ahead of REACH. */
    static final float AHEAD = 40f;
    /** How far above what it lies on the game draws an item's model, in levels (WorldItemModelDrawer). */
    private static final float LIFT = 0.00175f;
    /**
     * How long, in tiles, the items whose game model is far off the thing's size are at their
     * longest, by full type. As the game draws them on the ground, and as they are: pool ball 94
     * and 57.15 mm across, pool cue 1.06 and 1.47 m long, baseball 133 and 74 mm, tennis ball 117
     * and 67 mm, golf ball 102 and 42.7 mm, basketball 263 and 240 mm, American football 356 and
     * 280 mm long. The game's soccer ball (230 mm, a real one 216 to 223) and shuttlecock (84 mm,
     * a real one about 85) are near enough and keep their size.
     */
    static final Map<String, Float> LIFE_SIZE = Map.of("Base.PoolBall", 0.05715f, "Base.Poolcue", 1.47f, "Base.Baseball", 0.074f,
            "Base.TennisBall", 0.067f, "Base.GolfBall", 0.0427f, "Base.Basketball", 0.24f, "Base.Football", 0.28f);
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_BUFFER_SIZE = 0x8764;

    /** A mesh's triangles as read: places and normals three floats a vertex, texture places two, and three vertices a triangle. */
    record Shape(float[] places, float[] normals, float[] uvs, int[] corners) {
        int triangles() {
            return corners.length / 3;
        }
    }

    /** A mesh that could not be read: its items lie as their pictures. */
    private static final Shape UNREADABLE = new Shape(new float[0], new float[0], new float[0], new int[0]);

    /** What was read of each mesh. Written on the render thread, read on the logic thread. */
    private static final ConcurrentHashMap<ModelMesh, Shape> shapes = new ConcurrentHashMap<>();
    /** The meshes the logic thread has asked for and the render thread has not read yet. */
    private static final Set<ModelMesh> wanted = ConcurrentHashMap.newKeySet();
    /** Items laid as their pictures while the game loads their models or their meshes are read: see waiting. Logic thread. */
    private static final Set<InventoryItem> waiting = Collections.newSetFromMap(new WeakHashMap<>());

    /** One item's model as it is to be built. */
    static final class Placed {
        Shape shape;
        Texture texture;
        float red, green, blue;
        /** Where the item lies: x, y, and up in tiles. */
        float x, y, up;
        /** From a point of the model to the world from (x, y, up): see GroundItems.placing. */
        final float[] placing = new float[12];
    }

    /** Made on first use: ItemModels is loaded with the game's first model (see createShader), before the renderer may be. */
    private static ItemModelRenderer renderer;
    /** The thread asking the game's renderer for items' models, while it asks: see createShader. */
    private static volatile Thread asking;
    private static final float[] transform = new float[16];
    private static Field modelField, transformField, tintRField, tintGField, tintBField, tintMaskField;
    private static Field handleField, elementsField, formatField;

    /** Whether the player has the game draw items on the ground as models, its own option, which this follows, and has ItemsAsModels on. */
    static boolean shown() {
        return Part.ITEMS_AS_MODELS.on() && Core.getInstance().isOption3DGroundItem();
    }

    /**
     * Logic thread: puts into placed the model of a loose item and says whether it has one to
     * build now. An item whose model the game is still loading, or whose mesh is not read yet,
     * is noted as waiting (see stillWaiting) and has none yet.
     */
    static boolean place(IsoWorldInventoryObject object, Placed placed) {
        InventoryItem item = object.getItem();
        try {
            ItemModelRenderer.RenderStatus status = ask(object);
            if (status == ItemModelRenderer.RenderStatus.Loading) {
                waiting.add(item);
                return false;
            }
            if (status != ItemModelRenderer.RenderStatus.Ready) {
                waiting.remove(item);
                return false;
            }
            fields();
            Model model = (Model) modelField.get(renderer);
            ModelMesh mesh = model.mesh;
            if (model.tex == null || model.tex.isFailure()) {
                waiting.remove(item);
                return false;
            }
            Shape shape = shapes.get(mesh);
            if (shape == null) {
                wanted.add(mesh);
            }
            // Until the texture is on the graphics card it has no name to build under.
            if (shape == null || !WorldScene.usable(model.tex) || !model.tex.isReady()) {
                waiting.add(item);
                return false;
            }
            waiting.remove(item);
            if (shape == UNREADABLE) {
                return false;
            }
            ((Matrix4f) transformField.get(renderer)).get(transform);
            GroundItems.placing(transform, renderer.angle.x, renderer.angle.y, renderer.angle.z, placed.placing);
            Float life = LIFE_SIZE.get(item.getFullType());
            if (life != null) {
                GroundItems.toLength(placed.placing, shape.places(), life);
            }
            placed.shape = shape;
            placed.texture = model.tex;
            boolean tinted = tintMaskField.get(renderer) == null;
            placed.red = tinted ? tintRField.getFloat(renderer) : 1f;
            placed.green = tinted ? tintGField.getFloat(renderer) : 1f;
            placed.blue = tinted ? tintBField.getFloat(renderer) : 1f;
            placed.x = renderer.x;
            placed.y = renderer.y;
            placed.up = renderer.z * ViewCamera.LEVEL_HEIGHT;
            return true;
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("the game's item renderer could not be read: " + failure, failure);
        } finally {
            renderer.reset();
        }
    }

    /**
     * Logic thread: has the game start loading the model of a loose item a little past REACH,
     * so that it is ready when the item comes near enough to be built as one, and has its mesh
     * read where the model is ready already.
     */
    static void ahead(IsoWorldInventoryObject object) {
        try {
            if (ask(object) == ItemModelRenderer.RenderStatus.Ready) {
                fields();
                ModelMesh mesh = ((Model) modelField.get(renderer)).mesh;
                if (!shapes.containsKey(mesh)) {
                    wanted.add(mesh);
                }
            }
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("the game's item renderer could not be read: " + failure, failure);
        } finally {
            renderer.reset();
        }
    }

    private static ItemModelRenderer.RenderStatus ask(IsoWorldInventoryObject object) {
        if (renderer == null) {
            renderer = new ItemModelRenderer();
        }
        IsoGridSquare square = object.getSquare();
        asking = Thread.currentThread();
        try {
            return renderer.renderMain(object.getItem(), square, square, object.getWorldPosX(), object.getWorldPosY(), object.getWorldPosZ() + LIFT,
                    0f, -1f, false);
        } finally {
            asking = null;
        }
    }

    /**
     * Model.<init> calling CreateShader. The game makes a new model's shader on the render
     * thread and waits for it, and that thread runs what it is handed only between frames and
     * after every texture queued before it: a whole frame or more for each model, which the
     * first sight of a littered street took dozens of. For the models asked for here the
     * shader is handed over without waiting. ItemModels reads only the mesh; a draw of the
     * model by the game before the shader is set makes it a basicEffect one of its own
     * (ItemModelRenderer.renderModel), which this one then replaces.
     */
    public static void createShader(Model model, String name) {
        if (asking != Thread.currentThread()) {
            model.CreateShader(name);
            return;
        }
        RenderThread.queueInvokeOnRenderContext(() -> model.effect = ShaderManager.instance.getOrCreateShader(name, model.isStatic, false));
    }

    /**
     * Logic thread, as a chunk is read: whether a loose item laid as its picture for want of
     * its model still waits for it. One that no longer does changes the chunk's sum, and the
     * chunk is built again with it.
     */
    static boolean stillWaiting(IsoWorldInventoryObject object) {
        if (waiting.isEmpty() || !waiting.contains(object.getItem())) {
            return false;
        }
        place(object, scratch);
        return waiting.contains(object.getItem());
    }

    private static final Placed scratch = new Placed();

    private static void fields() {
        if (modelField != null) {
            return;
        }
        try {
            modelField = declared(ItemModelRenderer.class, Links.ITEM_MODEL);
            transformField = declared(ItemModelRenderer.class, Links.ITEM_TRANSFORM);
            tintRField = declared(ItemModelRenderer.class, Links.ITEM_TINT_R);
            tintGField = declared(ItemModelRenderer.class, Links.ITEM_TINT_G);
            tintBField = declared(ItemModelRenderer.class, Links.ITEM_TINT_B);
            tintMaskField = declared(ItemModelRenderer.class, Links.ITEM_TINT_MASK);
        } catch (NoSuchFieldException failure) {
            throw new IllegalStateException("the game's item renderer has no field " + failure.getMessage(), failure);
        }
    }

    private static Field declared(Class<?> owner, Links.Field link) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(link.name());
        field.setAccessible(true);
        return field;
    }

    /** Render thread, once a frame: reads back the meshes the logic thread has asked for. */
    static void read() {
        if (wanted.isEmpty()) {
            return;
        }
        for (ModelMesh mesh : wanted) {
            Shape shape = read(mesh);
            shapes.put(mesh, shape);
            wanted.remove(mesh);
        }
    }

    private static Shape read(ModelMesh mesh) {
        VertexBufferObject buffer = mesh.vb;
        if (buffer == null) {
            Log.info("an item's model is drawn as its picture: its mesh has no vertex buffer");
            return UNREADABLE;
        }
        VertexBufferObject.Vbo handle;
        int[] elements;
        VertexBufferObject.VertexFormat format;
        try {
            if (formatField == null) {
                handleField = declared(VertexBufferObject.class, Links.BUFFER_HANDLE);
                elementsField = declared(VertexBufferObject.class, Links.BUFFER_ELEMENTS);
                formatField = declared(VertexBufferObject.class, Links.BUFFER_FORMAT);
            }
            handle = (VertexBufferObject.Vbo) handleField.get(buffer);
            elements = (int[]) elementsField.get(buffer);
            format = (VertexBufferObject.VertexFormat) formatField.get(buffer);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("the game's vertex buffer cannot be read: " + failure, failure);
        }
        int place = -1, normal = -1, uv = -1;
        for (int element = 0; format != null && element < format.getNumElements(); element++) {
            VertexBufferObject.VertexElement at = format.getElement(element);
            switch (at.type) {
                case VertexArray -> place = place < 0 ? at.byteOffset : place;
                case NormalArray -> normal = normal < 0 ? at.byteOffset : normal;
                case TextureCoordArray -> uv = uv < 0 ? at.byteOffset : uv;
                default -> { }
            }
        }
        String lacks = handle == null ? "it has no buffer on the graphics card" : handle.faceDataOnly ? "its buffer holds no vertices"
                : place < 0 ? "it has no places" : normal < 0 ? "it has no normals" : uv < 0 ? "it has no texture places"
                : elements == null || elements.length < handle.numElements || handle.numElements % 3 != 0 || handle.numElements == 0 ? "its triangles are not a list" : null;
        if (lacks != null) {
            Log.info("an item's model " + mesh.fullPath + " is drawn as its picture: " + lacks);
            return UNREADABLE;
        }
        int count = handle.numElements, stride = handle.vertexStride;
        GL15.glBindBuffer(GL_ARRAY_BUFFER, handle.vboId);
        int bytes = GL15.glGetBufferParameteri(GL_ARRAY_BUFFER, GL_BUFFER_SIZE);
        ByteBuffer raw = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        GL15.glGetBufferSubData(GL_ARRAY_BUFFER, 0L, raw);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        int vertices = bytes / stride;
        int[] corners = new int[count];
        for (int corner = 0; corner < count; corner++) {
            if (elements[corner] < 0 || elements[corner] >= vertices) {
                Log.info("an item's model is drawn as its picture: a triangle names vertex " + elements[corner] + " of " + vertices);
                return UNREADABLE;
            }
            corners[corner] = elements[corner];
        }
        float[] places = new float[vertices * 3], normals = new float[vertices * 3], uvs = new float[vertices * 2];
        for (int vertex = 0; vertex < vertices; vertex++) {
            for (int axis = 0; axis < 3; axis++) {
                places[vertex * 3 + axis] = raw.getFloat(vertex * stride + place + axis * 4);
                normals[vertex * 3 + axis] = raw.getFloat(vertex * stride + normal + axis * 4);
            }
            uvs[vertex * 2] = raw.getFloat(vertex * stride + uv);
            uvs[vertex * 2 + 1] = raw.getFloat(vertex * stride + uv + 4);
        }
        if (FirstPerson.TRACE) {
            Log.info("an item's model is read: " + mesh.fullPath + ", " + vertices + " vertices, " + count / 3 + " triangles");
        }
        return new Shape(places, normals, uvs, corners);
    }
}
