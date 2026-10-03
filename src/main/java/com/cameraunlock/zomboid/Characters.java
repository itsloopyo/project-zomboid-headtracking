package com.cameraunlock.zomboid;

import java.util.ArrayList;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;

/**
 * The game's characters in the first-person picture: zombies, survivors and
 * animals as the game's own animated models, with their clothes, wounds and
 * whatever they carry.
 *
 * Nothing about a character is rebuilt here. Each frame the game poses every
 * character in view and draws it into its own picture through a camera that
 * looks down from the south-east. This class notes each character the game
 * draws, and has the game draw it once more into the first-person picture
 * through a camera that stands where the first-person eye is. The game's own
 * shader, textures, bones and lights do the rest.
 *
 * Render thread only.
 */
public final class Characters {
    /** The model's own units to tiles: the game draws its models at one and a half times their size. */
    private static final float MODEL_SCALE = 1.5f;
    /** The depth the game's model shader leaves a model at when it is not being fitted among sprites. */
    private static final float NO_DEPTH_SHIFT = 0.5f;
    /** How far from the eye a character still casts a shadow, in tiles. */
    private static final float SHADOW_REACH = 45f;
    private static final float[] IDENTITY = {1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};

    private static final ArrayList<ModelSlotRenderData> noted = new ArrayList<>();
    /** The frame the noted characters belong to: the game reuses their render data for the next. */
    private static Object notedIn;
    /** Set on the logic thread: whether the first-person view wants the characters at all. */
    static volatile boolean wanted;
    private static final float[] placed = new float[16];
    private static final float[] combined = new float[16];
    private static int drawnLast;

    private Characters() {}

    static int drawnLast() {
        return drawnLast;
    }

    /** Called at the end of the game's own draw of a character. */
    public static void drawn(ModelSlotRenderData data) {
        if (!wanted) {
            return;
        }
        Object frame = SpriteRenderer.instance.getRenderingState();
        if (frame != notedIn) {
            noted.clear();
            notedIn = frame;
        }
        noted.add(data);
    }

    /** Whether there is anything to draw this frame. The noted characters of an earlier frame are dropped. */
    static boolean ready() {
        if (noted.isEmpty() || notedIn != SpriteRenderer.instance.getRenderingState()) {
            noted.clear();
            return false;
        }
        return true;
    }

    /**
     * Draws the characters the game drew this frame through the first-person camera, into
     * whatever target is bound. The target's depth has to be the world's, so a character
     * behind a wall stays behind it.
     *
     * The viewer's own model is left out: the eye is inside its head.
     */
    static void draw(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far) {
        drawnLast = draw(ViewCamera.projection(tanHalfFieldOfView, aspect, near, far), null, ViewCamera.view(camera),
                camera.eyeX, camera.eyeY, Float.MAX_VALUE);
        noted.clear();
    }

    /**
     * Draws the characters within reach of the eye as the light sees them, into a shadow map.
     * The viewer is among them: a first-person view has no body yet, but it has a shadow.
     */
    static void cast(float[] lightMatrix, ViewCamera.Pose camera) {
        draw(IDENTITY, IDENTITY, lightMatrix, camera.eyeX, camera.eyeY, SHADOW_REACH);
    }

    /** @param viewerProjection the projection the viewer's own model is drawn with, or null to leave it out */
    private static int draw(float[] projectionMatrix, float[] viewerProjection, float[] view, float eyeX, float eyeY, float reach) {
        Core core = Core.getInstance();
        IsoPlayer viewer = IsoPlayer.getInstance();
        int drawn = 0;
        for (int i = 0; i < noted.size(); i++) {
            ModelSlotRenderData data = noted.get(i);
            // A character in a vehicle is placed by the vehicle, which the view does not draw yet.
            if (data.inVehicle || data.character == viewer && viewerProjection == null) {
                continue;
            }
            float toX = data.x - eyeX, toY = data.y - eyeY;
            if (toX * toX + toY * toY > reach * reach) {
                continue;
            }
            Matrix4f projection = core.projectionMatrixStack.alloc();
            projection.set(data.character == viewer ? viewerProjection : projectionMatrix);
            core.projectionMatrixStack.push(projection);
            place(data.x, data.y, data.z * ViewCamera.LEVEL_HEIGHT, data.animPlayerAngle);
            multiply(view, placed, combined);
            Matrix4f modelView = core.modelViewMatrixStack.alloc();
            modelView.set(combined);
            core.modelViewMatrixStack.push(modelView);
            for (int part = 0; part < data.modelData.size(); part++) {
                ModelInstanceRenderData model = data.modelData.get(part);
                model.modelInstance.targetDepth = NO_DEPTH_SHIFT;
                // The game's draw ends by putting its own remembered state back, which has depth
                // writes off. Without them the next part, or the next character, is painted over
                // whatever is nearer.
                GL11.glDepthMask(true);
                model.RenderCharacter(data);
            }
            core.modelViewMatrixStack.pop();
            core.projectionMatrixStack.pop();
            drawn++;
        }
        return drawn;
    }

    /**
     * The matrix that stands a model in the world. A model's own axes are x to its side, y up
     * and z along its back; the game turns it by the character's angle plus half a turn,
     * mirrors it across x and scales it. In world axes (x, y, up) that comes to this.
     */
    private static void place(float x, float y, float up, float angle) {
        float cos = (float) Math.cos(angle + Math.PI) * MODEL_SCALE;
        float sin = (float) Math.sin(angle + Math.PI) * MODEL_SCALE;
        placed[0] = cos; placed[1] = sin; placed[2] = 0f; placed[3] = 0f;
        placed[4] = 0f; placed[5] = 0f; placed[6] = MODEL_SCALE; placed[7] = 0f;
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
