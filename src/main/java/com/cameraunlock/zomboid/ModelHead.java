package com.cameraunlock.zomboid;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import zombie.characters.IsoPlayer;
import zombie.core.skinnedmodel.HelperFunctions;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;
import zombie.network.GameClient;

/**
 * The character's own head, turned as the player's is. In the game's own view
 * it is there to be seen. In first person it is what a mirror, a window and
 * the character's shadow show.
 *
 * The game poses a skeleton in steps: every bone from the animations, then the
 * spine twisted towards the aim, then the whole made into the matrices the
 * model is drawn with. The head is turned between the last two, the way the
 * game twists the spine: the bone's place in the model is turned about its own
 * joint and put back as a place in its parent. The pose is made afresh every
 * frame, so nothing is left to undo, and nothing but the picture reads it.
 *
 * Seen from outside, the neck takes part of the turn and the head the rest.
 * Seen from the eye the head's joint takes all of it: a neck that bends brings
 * the skin of the collar with it, into what the viewer sees of their own chest
 * looking down, while what turns with the head alone stays as near the eye as
 * the head is, and that near nothing of the viewer's own body is drawn.
 * Neither turns further than a neck does.
 */
public final class ModelHead {
    private static final String NECK = "Bip01_Neck", HEAD = "Bip01_Head";
    /** How much of the turn the neck takes, seen from outside. The head's joint, which rides on it, takes what is left. */
    private static final float NECK_SHARE = 0.4f;
    private static final float MOST_YAW = (float) Math.toRadians(80), MOST_UP = (float) Math.toRadians(50),
            MOST_DOWN = (float) Math.toRadians(45), MOST_ROLL = (float) Math.toRadians(35);

    private static final Matrix4f inParent = new Matrix4f(), fromParent = new Matrix4f(), inModel = new Matrix4f(),
            turned = new Matrix4f(), turn = new Matrix4f();
    private static final Vector3f joint = new Vector3f();
    private static final Quaternion turning = new Quaternion();
    private static boolean gapSaid, bonesSaid;
    private static long said;
    /**
     * How high the joint of the player's head stood above their feet when they were last
     * posed, in the model's own measure, or not a number before the first pose. Written by
     * the thread that poses and read by the one that draws.
     */
    static volatile float jointUp = Float.NaN;

    private ModelHead() {}

    /** Called from AnimationPlayer.updateAnimation_StandardAnimation once the bones are posed and the spine is twisted. */
    public static void pose(AnimationPlayer animation) {
        IsoPlayer player = IsoPlayer.getInstance();
        if (player == null || player.getAnimationPlayer() != animation) {
            return;
        }
        SkinningData skeleton = animation.getSkinningData();
        SkinningBone neck = skeleton.getBone(NECK), top = skeleton.getBone(HEAD);
        if (neck == null || top == null || neck.parent == null) {
            if (!bonesSaid) {
                bonesSaid = true;
                Log.info("the character's head is not turned: its skeleton has no " + NECK + " under a parent and " + HEAD);
            }
            return;
        }
        float[] head = HeadTracking.headTurn();
        if (head != null && !player.isDead() && !GameClient.client && IsoPlayer.numPlayers == 1) {
            float yaw = Math.clamp(head[0], -MOST_YAW, MOST_YAW);
            float pitch = Math.clamp(head[1], -MOST_DOWN, MOST_UP);
            float roll = Math.clamp(head[2], -MOST_ROLL, MOST_ROLL);
            float neckShare = FirstPerson.seenFromTheEye() ? 0f : NECK_SHARE;
            if (neckShare > 0f) {
                turn(animation, neck, yaw * neckShare, pitch * neckShare, roll * neckShare);
            }
            turn(animation, top, yaw * (1f - neckShare), pitch * (1f - neckShare), roll * (1f - neckShare));
        }
        // The animation carries the head up and down as the character stands, crouches and
        // readies a weapon, and the view's eye does not follow it. What shows the two side by
        // side, the viewer in a window's glass, asks where the head is.
        animation.getBoneModelTransform(top.index, inModel);
        HelperFunctions.getPosition(inModel, joint);
        jointUp = joint.y;
        if (FirstPerson.TRACE && System.nanoTime() - said > 1_000_000_000L) {
            said = System.nanoTime();
            Log.info(String.format(java.util.Locale.ROOT, "head joint in the model at (%.3f, %.3f, %.3f)", joint.x, joint.y, joint.z));
        }
    }

    /** Says once why the head is not turned on this build of the game, if it is not. */
    static void sayGap() {
        if (!gapSaid) {
            gapSaid = true;
            String gap = Agent.modelHeadGap();
            if (gap != null) {
                Log.info("the character's head is not turned with the player's: " + gap);
            }
        }
    }

    /**
     * Turns one bone about its own joint, in the model's axes: yaw about straight up, then
     * pitch about the turned side, then roll about the way the head then faces.
     */
    private static void turn(AnimationPlayer animation, SkinningBone bone, float yaw, float pitch, float roll) {
        if (Matrix4f.invert(animation.getBoneModelTransform(bone.parent.index, inParent), fromParent) == null) {
            return;
        }
        animation.getBoneModelTransform(bone.index, inModel);
        HelperFunctions.getPosition(inModel, joint);
        turned.load(inModel);
        HelperFunctions.setPosition(turned, 0f, 0f, 0f);
        float sy = (float) Math.sin(yaw / 2f), cy = (float) Math.cos(yaw / 2f);
        float sx = (float) Math.sin(pitch / 2f), cx = (float) Math.cos(pitch / 2f);
        // The tracker's roll is positive tilting to the left, which about the model's forward axis is a negative turn.
        float sz = (float) Math.sin(-roll / 2f), cz = (float) Math.cos(roll / 2f);
        float w = cy * cx, x = cy * sx, y = sy * cx, z = -sy * sx;
        turning.set(cz * x + y * sz, cz * y - x * sz, w * sz + cz * z, w * cz - z * sz);
        HelperFunctions.CreateFromQuaternion(turning, turn);
        Matrix4f.mul(turned, turn, turned);
        HelperFunctions.setPosition(turned, joint);
        animation.getBoneTransformAt(bone.index).mul(turned, fromParent);
    }
}
