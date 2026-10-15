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

    static final String NECK = "Bip01_Neck", HEAD = "Bip01_Head";
    /** How much of the turn the neck takes, seen from outside. The head's joint, which rides on it, takes what is left. */
    static final float NECK_SHARE = 0.4f;
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

    /**
     * Where the eye is from the feet, in the world, into out, as the game's animation has the
     * head's joint, followed through two eases in the body's own axes, so the bob and sway of
     * each step are taken out and a crouch or a sit still moves the eye. False where the
     * character is not posed.
     */
    boolean eyeOffset(IsoPlayer player, float yaw, float seconds, float[] out) {
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
        boolean follow = steadyFor != player;
        steadyFor = player;
        steady(0, cameraJoint.x, follow, seconds);
        steady(1, cameraJoint.y, follow, seconds);
        steady(2, cameraJoint.z, follow, seconds);
        ViewCamera.eyeOffset(steadyJoint[0], steadyJoint[1], steadyJoint[2], animation.getRenderedAngle(), yaw, out);
        return true;
    }

    /**
     * The time constant of each of the two eases the joint is followed through. A walk bobs the head about twice a second, which two of these leave a seventh of.
     */
    private static final float STEADY_SECONDS = 0.2f;
    private final float[] steadyNear = new float[3], steadyJoint = new float[3];

    private void steady(int axis, float joint, boolean follow, float seconds) {
        if (follow) {
            steadyNear[axis] = joint;
            steadyJoint[axis] = joint;
        } else {
            steadyNear[axis] = ViewModelMath.ease(steadyNear[axis], joint, seconds, STEADY_SECONDS);
            steadyJoint[axis] = ViewModelMath.ease(steadyJoint[axis], steadyNear[axis], seconds, STEADY_SECONDS);
        }
    }
    private IsoPlayer steadyFor;

    /**
     * The head as the game's animation last posed it, before the player's own head turn is put
     * in, kept by the thread that poses for the one that builds the frame: the bones themselves
     * hold the turn between pose and posed, and are being written while they are read.
     */
    private final Object posedLock = new Object();
    private final float[] posedHead = new float[12];
    private AnimationPlayer posedFor;
    private float posedAngle;
    private final Matrix4f untouched = new Matrix4f();

    /**
     * The head as the game's animation has it, the player's own head turn not in it: its turn in
     * the model, column after column, into turn, and where its joint is from the feet in the
     * world, in tiles, into joint.
     *
     * @return the angle the body is drawn at, or not a number before this player's skeleton is first posed
     */
    float head(IsoPlayer player, float[] turn, float[] joint) {
        float bodyAngle;
        float x, y, z;
        synchronized (posedLock) {
            if (posedFor == null || posedFor != player.getAnimationPlayer()) {
                return Float.NaN;
            }
            System.arraycopy(posedHead, 0, turn, 0, 9);
            x = posedHead[9];
            y = posedHead[10];
            z = posedHead[11];
            bodyAngle = posedAngle;
        }
        float c = (float) Math.cos(bodyAngle + Math.PI) * Characters.CHARACTER_SCALE;
        float s = (float) Math.sin(bodyAngle + Math.PI) * Characters.CHARACTER_SCALE;
        joint[0] = c * x + s * z;
        joint[1] = s * x - c * z;
        joint[2] = y * Characters.CHARACTER_SCALE;
        return bodyAngle;
    }

    /** Keeps the head as posed, for head: see posedHead. */
    private void keepPosed(AnimationPlayer animation, SkinningBone top) {
        animation.getBoneModelTransform(top.index, untouched);
        synchronized (posedLock) {
            // The game's matrices are kept transposed, the place in m03, m13 and m23 (HelperFunctions.getPosition): a bone's own x axis is m00, m10, m20.
            posedHead[0] = untouched.m00;
            posedHead[1] = untouched.m10;
            posedHead[2] = untouched.m20;
            posedHead[3] = untouched.m01;
            posedHead[4] = untouched.m11;
            posedHead[5] = untouched.m21;
            posedHead[6] = untouched.m02;
            posedHead[7] = untouched.m12;
            posedHead[8] = untouched.m22;
            posedHead[9] = untouched.m03;
            posedHead[10] = untouched.m13;
            posedHead[11] = untouched.m23;
            posedFor = animation;
            posedAngle = animation.getRenderedAngle();
        }
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
        keepPosed(animation, top);
        float[] head = view.headTracking.headTurn();
        if (head != null && !player.isDead()) {
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
            turnHead(boneTurn, animation, neck, top, head[0], head[1], head[2], neckShare);
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

    /**
     * Turns a posed head by the tracker's yaw, pitch and roll in radians, held to what a neck does,
     * the neck taking neckShare of it and the head's joint the rest.
     */
    static void turnHead(BoneTurn boneTurn, AnimationPlayer animation, SkinningBone neck, SkinningBone top, float yaw, float pitch, float roll,
            float neckShare) {
        yaw = Math.clamp(yaw, -MOST_YAW, MOST_YAW);
        pitch = Math.clamp(pitch, -MOST_DOWN, MOST_UP);
        roll = Math.clamp(roll, -MOST_ROLL, MOST_ROLL);
        if (neckShare > 0f) {
            turn(boneTurn, animation, neck, yaw * neckShare, pitch * neckShare, roll * neckShare);
        }
        turn(boneTurn, animation, top, yaw * (1f - neckShare), pitch * (1f - neckShare), roll * (1f - neckShare));
    }

    /** Turns one bone about its own joint, in the model's axes: yaw about straight up, then pitch about the turned side, then roll about the way the head then faces. */
    private static void turn(BoneTurn boneTurn, AnimationPlayer animation, SkinningBone bone, float yaw, float pitch, float roll) {
        float sy = (float) Math.sin(yaw / 2f), cy = (float) Math.cos(yaw / 2f);
        float sx = (float) Math.sin(pitch / 2f), cx = (float) Math.cos(pitch / 2f);
        // The tracker's roll is positive tilting to the left, which about the model's forward axis is a negative turn.
        float sz = (float) Math.sin(-roll / 2f), cz = (float) Math.cos(roll / 2f);
        float w = cy * cx, x = cy * sx, y = sy * cx, z = -sy * sx;
        boneTurn.turn(animation, bone, cz * x + y * sz, cz * y - x * sz, w * sz + cz * z, w * cz - z * sz);
    }
}
