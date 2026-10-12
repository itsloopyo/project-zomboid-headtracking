package com.cameraunlock.zomboid;

import java.util.Locale;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import zombie.characters.IsoPlayer;
import zombie.core.skinnedmodel.HelperFunctions;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;

/**
 * The character's own head, turned as the player's is. In the game's own view
 * it is there to be seen. In first person it is what a mirror, a window and
 * the character's shadow show.
 *
 * The game poses a skeleton in steps: every bone from the animations, then the
 * spine twisted towards the aim, then the whole made into the matrices the
 * model is drawn with. The head is turned between the last two. A bone is kept
 * as a place, a turn and a size in its parent, and turned about its own joint
 * only its turn changes. Once the matrices are made the
 * two bones are put back as the animations had them, so nothing but the
 * picture has the turn. The game mixes each new pose with the bones as the
 * last frame left them whenever the animations playing weigh less than one
 * between them, as they do going from one to the next: a turn left in would be
 * turned again on top of itself every frame of that, and the arms hang from
 * the neck.
 *
 * Seen from outside, the neck takes part of the turn and the head the rest.
 * Seen from the eye the head's joint takes all of it: a neck that bends brings
 * the skin of the collar with it, into what the viewer sees of their own chest
 * looking down, while what turns with the head alone stays as near the eye as
 * the head is, and that near nothing of the viewer's own body is drawn.
 * Neither turns further than a neck does.
 */
public final class ModelHead {
    final View view;

    ModelHead(View view) {
        this.view = view;
    }

    private static final String NECK = "Bip01_Neck", HEAD = "Bip01_Head";
    /** How much of the turn the neck takes, seen from outside. The head's joint, which rides on it, takes what is left. */
    private static final float NECK_SHARE = 0.4f;
    private static final float MOST_YAW = (float) Math.toRadians(80), MOST_UP = (float) Math.toRadians(50),
            MOST_DOWN = (float) Math.toRadians(45), MOST_ROLL = (float) Math.toRadians(35);

    private final BoneTurn boneTurn = new BoneTurn();
    private final Matrix4f inModel = new Matrix4f();
    private final Vector3f joint = new Vector3f();
    private final Vector3f neckPlace = new Vector3f(), neckScale = new Vector3f(), topPlace = new Vector3f(),
            topScale = new Vector3f();
    private final Quaternion neckTurn = new Quaternion(), topTurn = new Quaternion();
    /** The skeleton whose neck and head are turned and not yet put back, between pose and posed. */
    private AnimationPlayer turnedIn;
    private int neckIndex, topIndex;
    /**
     * For the trace: the least and the most each bone was turned in its parent as the game
     * left it, since the last line. The animations hold both within a few degrees. A turn
     * of the mod's that the game had mixed back into its pose would show here.
     */
    private float neckLeast = Float.MAX_VALUE, neckMost, topLeast = Float.MAX_VALUE, topMost;
    private static boolean gapSaid, bonesSaid;
    private long said;
    /**
     * How high the joint of the player's head stood above their feet when they were last
     * posed, in the model's own measure, or not a number before the first pose. Written by
     * the thread that poses and read by the one that draws.
     */
    volatile float jointUp = Float.NaN;

    private final Matrix4f cameraHead = new Matrix4f();
    private final Vector3f cameraJoint = new Vector3f();

    boolean eyeOffset(IsoPlayer player, float yaw, float[] out) {
        AnimationPlayer animation = player.getAnimationPlayer();
        if (animation == null || !animation.isReady()) {
            return false;
        }
        SkinningBone head = animation.getSkinningData().getBone(HEAD);
        if (head == null) {
            return false;
        }
        animation.getBoneModelTransform(head.index, cameraHead);
        HelperFunctions.getPosition(cameraHead, cameraJoint);
        ViewCamera.eyeOffset(cameraJoint.x, cameraJoint.y, cameraJoint.z, animation.getRenderedAngle(), yaw, out);
        return true;
    }

    /**
     * The head as the game's animation has it, the player's own head turn not in it: its turn in
     * the model, column after column, into turn, and where its joint is from the feet in the
     * world, in tiles, into joint.
     *
     * @return the angle the body is drawn at, or not a number before the skeleton is posed
     */
    float head(IsoPlayer player, float[] turn, float[] joint) {
        AnimationPlayer animation = player.getAnimationPlayer();
        if (animation == null || !animation.isReady()) {
            return Float.NaN;
        }
        SkinningBone head = animation.getSkinningData().getBone(HEAD);
        if (head == null) {
            return Float.NaN;
        }
        animation.getBoneModelTransform(head.index, cameraHead);
        // The game's matrices are kept transposed, the place in m03, m13 and m23 (HelperFunctions.getPosition): a bone's own x axis is m00, m10, m20.
        turn[0] = cameraHead.m00;
        turn[1] = cameraHead.m10;
        turn[2] = cameraHead.m20;
        turn[3] = cameraHead.m01;
        turn[4] = cameraHead.m11;
        turn[5] = cameraHead.m21;
        turn[6] = cameraHead.m02;
        turn[7] = cameraHead.m12;
        turn[8] = cameraHead.m22;
        float bodyAngle = animation.getRenderedAngle();
        float c = (float) Math.cos(bodyAngle + Math.PI) * Characters.CHARACTER_SCALE;
        float s = (float) Math.sin(bodyAngle + Math.PI) * Characters.CHARACTER_SCALE;
        joint[0] = c * cameraHead.m03 + s * cameraHead.m23;
        joint[1] = s * cameraHead.m03 - c * cameraHead.m23;
        joint[2] = cameraHead.m13 * Characters.CHARACTER_SCALE;
        return bodyAngle;
    }


    /** Called from AnimationPlayer.updateAnimation_StandardAnimation once the bones are posed and the spine is twisted. */
    public void pose(AnimationPlayer animation) {
        IsoPlayer player = view.player();
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
        float[] head = view.headTracking.headTurn();
        if (head != null && !player.isDead()) {
            float yaw = Math.clamp(head[0], -MOST_YAW, MOST_YAW);
            float pitch = Math.clamp(head[1], -MOST_DOWN, MOST_UP);
            float roll = Math.clamp(head[2], -MOST_ROLL, MOST_ROLL);
            float neckShare = view.firstPerson.seenFromTheEye() ? 0f : NECK_SHARE;
            animation.getBoneTransformAt(neck.index).getPRS(neckPlace, neckTurn, neckScale);
            animation.getBoneTransformAt(top.index).getPRS(topPlace, topTurn, topScale);
            neckIndex = neck.index;
            topIndex = top.index;
            turnedIn = animation;
            if (FirstPerson.TRACE) {
                float neckAngle = angle(neckTurn), topAngle = angle(topTurn);
                neckLeast = Math.min(neckLeast, neckAngle);
                neckMost = Math.max(neckMost, neckAngle);
                topLeast = Math.min(topLeast, topAngle);
                topMost = Math.max(topMost, topAngle);
            }
            if (neckShare > 0f) {
                turn(animation, neck, yaw * neckShare, pitch * neckShare, roll * neckShare);
            }
            turn(animation, top, yaw * (1f - neckShare), pitch * (1f - neckShare), roll * (1f - neckShare));
        }
        animation.getBoneModelTransform(top.index, inModel);
        HelperFunctions.getPosition(inModel, joint);
        jointUp = joint.y;
        if (FirstPerson.TRACE && System.nanoTime() - said > 1_000_000_000L) {
            said = System.nanoTime();
            Log.info(String.format(Locale.ROOT, "head joint in the model at (%.3f, %.3f, %.3f)", joint.x, joint.y, joint.z)
                    + (neckLeast > neckMost ? ", head not turned" : String.format(Locale.ROOT, ", as the game left them the neck"
                            + " turned %.1f to %.1f degrees in its parent and the head %.1f to %.1f", neckLeast, neckMost, topLeast, topMost)));
            neckLeast = Float.MAX_VALUE;
            neckMost = 0f;
            topLeast = Float.MAX_VALUE;
            topMost = 0f;
        }
    }

    private float angle(Quaternion turn) {
        return (float) Math.toDegrees(2.0 * Math.acos(Math.min(1f, Math.abs(turn.w))));
    }

    /** Called from AnimationPlayer.updateAnimation_StandardAnimation once the matrices the model is drawn with are made. */
    public void posed(AnimationPlayer animation) {
        if (turnedIn != animation) {
            return;
        }
        turnedIn = null;
        animation.getBoneTransformAt(neckIndex).set(neckPlace, neckTurn, neckScale);
        animation.getBoneTransformAt(topIndex).set(topPlace, topTurn, topScale);
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

    /** Turns one bone about its own joint, in the model's axes: yaw about straight up, then pitch about the turned side, then roll about the way the head then faces. */
    private void turn(AnimationPlayer animation, SkinningBone bone, float yaw, float pitch, float roll) {
        float sy = (float) Math.sin(yaw / 2f), cy = (float) Math.cos(yaw / 2f);
        float sx = (float) Math.sin(pitch / 2f), cx = (float) Math.cos(pitch / 2f);
        // The tracker's roll is positive tilting to the left, which about the model's forward axis is a negative turn.
        float sz = (float) Math.sin(-roll / 2f), cz = (float) Math.cos(roll / 2f);
        float w = cy * cx, x = cy * sx, y = sy * cx, z = -sy * sx;
        boneTurn.turn(animation, bone, cz * x + y * sz, cz * y - x * sz, w * sz + cz * z, w * cz - z * sz);
    }
}
