package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.skinnedmodel.ModelCamera;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;

/**
 * The game's models in the first-person picture: zombies, survivors and
 * animals with their clothes, wounds and whatever they carry, and vehicles.
 *
 * Nothing about a model is rebuilt here. Each frame the game poses every model
 * in view and draws it into its own picture through a camera that looks down
 * from the south-east. This class notes each model the game draws, and has the
 * game draw it again through a camera that stands where the first-person eye
 * is, or where the sun is for its shadow. The game's own shaders, textures,
 * bones and lights do the rest.
 *
 * Render thread only.
 */
public final class Characters {
    /** The model's own units to tiles: the game draws its characters at one and a half times their size. */
    private static final float CHARACTER_SCALE = 1.5f;
    /** The depth the game's model shader leaves a model at when it is not being fitted among sprites. */
    private static final float NO_DEPTH_SHIFT = 0.5f;
    /** How far from the eye a model still casts a shadow, in tiles. */
    private static final float SHADOW_REACH = 45f;
    /**
     * How far above where the game's camera stands a vehicle its wheels reach the ground, in
     * tiles. Set by eye against Base.CarNormal on a level road: without it the car stands in
     * the road up to its sills.
     */
    private static final float VEHICLE_RAISE = 0.6f;
    private static final float[] IDENTITY = {1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};

    private static final ArrayList<ModelSlotRenderData> noted = new ArrayList<>();
    /** Where the game's own camera stood each noted model: x, y, floor level and the angle it turned it by. */
    private static float[] stood = new float[64 * 4];
    /** The frame the noted models belong to: the game reuses their render data for the next. */
    private static Object notedIn;
    /** Set on the logic thread: whether the first-person view wants the models at all. */
    static volatile boolean wanted;
    private static final float[] placed = new float[16];
    private static final float[] combined = new float[16];
    private static final float[] projection = new float[16];
    private static int drawnLast;

    /**
     * What the game's vehicle draw asks for its matrices: it calls Begin and End on whatever
     * camera is current, where its character draw takes them from the caller.
     */
    private static final ModelCamera EYE = new ModelCamera() {
        @Override
        public void Begin() {
            Core core = Core.getInstance();
            Matrix4f p = core.projectionMatrixStack.alloc();
            p.set(projection);
            core.projectionMatrixStack.push(p);
            Matrix4f m = core.modelViewMatrixStack.alloc();
            m.set(combined);
            core.modelViewMatrixStack.push(m);
        }

        @Override
        public void End() {
            Core core = Core.getInstance();
            core.modelViewMatrixStack.pop();
            core.projectionMatrixStack.pop();
        }
    };

    private Characters() {}

    static int drawnLast() {
        return drawnLast;
    }

    /** Called at the end of the game's own draw of a model, while the game's camera still holds where it stood it. */
    public static void drawn(ModelSlotRenderData data) {
        if (!wanted) {
            return;
        }
        Object frame = SpriteRenderer.instance.getRenderingState();
        if (frame != notedIn) {
            noted.clear();
            notedIn = frame;
        }
        int at = noted.size() * 4;
        if (at == stood.length) {
            stood = Arrays.copyOf(stood, stood.length * 2);
        }
        ModelCamera camera = ModelCamera.instance;
        stood[at] = camera.x;
        stood[at + 1] = camera.y;
        stood[at + 2] = camera.z;
        stood[at + 3] = camera.useAngle;
        noted.add(data);
    }

    /** Whether there is anything to draw this frame. The noted models of an earlier frame are dropped. */
    static boolean ready() {
        if (noted.isEmpty() || notedIn != SpriteRenderer.instance.getRenderingState()) {
            noted.clear();
            return false;
        }
        return true;
    }

    /**
     * Draws the models the game drew this frame through the first-person camera, into
     * whatever target is bound. The target's depth has to be the world's, so a zombie behind a
     * wall stays behind it. The viewer's own model is left out: the eye is inside its head.
     */
    static void draw(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far) {
        System.arraycopy(ViewCamera.projection(tanHalfFieldOfView, aspect, near, far), 0, projection, 0, 16);
        drawnLast = draw(ViewCamera.view(camera), false, camera.eyeX, camera.eyeY, Float.MAX_VALUE);
        noted.clear();
    }

    /**
     * Draws the models within reach of the eye as the light sees them, into a shadow map. The
     * viewer is among them: a first-person view has no body yet, but it has a shadow.
     */
    static void cast(float[] lightMatrix, ViewCamera.Pose camera) {
        System.arraycopy(IDENTITY, 0, projection, 0, 16);
        draw(lightMatrix, true, camera.eyeX, camera.eyeY, SHADOW_REACH);
    }

    private static int draw(float[] view, boolean withViewer, float eyeX, float eyeY, float reach) {
        IsoPlayer viewer = IsoPlayer.getInstance();
        int drawn = 0;
        for (int i = 0; i < noted.size(); i++) {
            ModelSlotRenderData data = noted.get(i);
            // A character in a vehicle is placed by its seat, which is not worked out here yet.
            if (data.inVehicle || !withViewer && data.character != null && data.character == viewer) {
                continue;
            }
            float toX = data.x - eyeX, toY = data.y - eyeY;
            if (toX * toX + toY * toY > reach * reach) {
                continue;
            }
            if (data.character == null) {
                vehicle(data, i * 4, view);
            } else {
                character(data, view);
            }
            drawn++;
        }
        return drawn;
    }

    private static void character(ModelSlotRenderData data, float[] view) {
        place(data.x, data.y, data.z * ViewCamera.LEVEL_HEIGHT + Terrain.lift(data.x, data.y, data.z), data.animPlayerAngle, CHARACTER_SCALE);
        multiply(view, placed, combined);
        EYE.Begin();
        for (int part = 0; part < data.modelData.size(); part++) {
            ModelInstanceRenderData model = data.modelData.get(part);
            model.modelInstance.targetDepth = NO_DEPTH_SHIFT;
            // The game's draw ends by putting its own remembered state back, which has depth
            // writes off. Without them the next part, or the next model, is painted over
            // whatever is nearer.
            GL11.glDepthMask(true);
            model.RenderCharacter(data);
        }
        EYE.End();
    }

    /**
     * The game's vehicle draw sets its matrices up itself, from the current camera, and then
     * moves the vehicle's depth so that its middle lands at the depth of the square it stands
     * on in the game's own picture. Here the middle is to stay where the camera puts it, so the
     * draw is told that the square's depth is exactly the depth the middle already has. The
     * game takes that depth as the middle's z after the matrices and before any divide by w,
     * its own camera having none, so that is the figure given here. With the divide the two
     * differ, and the vehicle is drawn nearer than it is: through the wall of a house.
     */
    private static void vehicle(ModelSlotRenderData data, int at, float[] view) {
        place(stood[at], stood[at + 1], stood[at + 2] * ViewCamera.LEVEL_HEIGHT + Terrain.lift(stood[at], stood[at + 1], stood[at + 2]) + VEHICLE_RAISE,
                stood[at + 3], 1f);
        multiply(view, placed, combined);
        float eyeX = combined[4] * data.centerOfMassY + combined[12];
        float eyeY = combined[5] * data.centerOfMassY + combined[13];
        float eyeZ = combined[6] * data.centerOfMassY + combined[14];
        float eyeW = combined[7] * data.centerOfMassY + combined[15];
        float clipZ = projection[2] * eyeX + projection[6] * eyeY + projection[10] * eyeZ + projection[14] * eyeW;
        float squareDepth = data.squareDepth;
        data.squareDepth = (clipZ + 1f) / 2f;
        ModelCamera game = ModelCamera.instance;
        ModelCamera.instance = EYE;
        for (int part = 0; part < data.modelData.size(); part++) {
            GL11.glDepthMask(true);
            data.modelData.get(part).RenderVehicle(data);
        }
        ModelCamera.instance = game;
        data.squareDepth = squareDepth;
    }

    /**
     * The matrix that stands a model in the world. A model's own axes are x to its side, y up
     * and z along its back; the game turns it by its angle plus half a turn, mirrors it across
     * x and scales it. In world axes (x, y, up) that comes to this.
     */
    private static void place(float x, float y, float up, float angle, float scale) {
        float cos = (float) Math.cos(angle + Math.PI) * scale;
        float sin = (float) Math.sin(angle + Math.PI) * scale;
        placed[0] = cos; placed[1] = sin; placed[2] = 0f; placed[3] = 0f;
        placed[4] = 0f; placed[5] = 0f; placed[6] = scale; placed[7] = 0f;
        placed[8] = sin; placed[9] = -cos; placed[10] = 0f; placed[11] = 0f;
        placed[12] = x; placed[13] = y; placed[14] = up; placed[15] = 1f;
    }

    /** out = a times b, all three column-major. */
    private static void multiply(float[] a, float[] b, float[] out) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                out[column * 4 + row] = a[row] * b[column * 4] + a[4 + row] * b[column * 4 + 1]
                        + a[8 + row] * b[column * 4 + 2] + a[12 + row] * b[column * 4 + 3];
            }
        }
    }
}
