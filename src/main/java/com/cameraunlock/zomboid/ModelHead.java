package com.cameraunlock.zomboid;

import java.util.Locale;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import zombie.characters.IsoPlayer;
import zombie.core.skinnedmodel.HelperFunctions;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.animation.AnimatorsBoneTransform;
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

    private final Matrix4f inParent = new Matrix4f(), inModel = new Matrix4f();
    private final Vector3f joint = new Vector3f(), place = new Vector3f(), size = new Vector3f();
    private final Quaternion parentTurn = new Quaternion(), was = new Quaternion();
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

    /**
     * Turns one bone about its own joint, in the model's axes: yaw about straight up, then
     * pitch about the turned side, then roll about the way the head then faces.
     *
     * The bone's place and size in its parent stay. Its turn in its parent takes the turn in
     * the model's axes carried into the parent's: with p the parent's turn in the model, t the
     * turn and r the bone's turn in its parent, the new turn is p undone, then t, then p, on r.
     */
    private void turn(AnimationPlayer animation, SkinningBone bone, float yaw, float pitch, float roll) {
        animation.getBoneModelTransform(bone.parent.index, inParent);
        // A turn is read out of a matrix that has no size in it: the parent's is taken out first.
        float scale = (float) Math.sqrt(inParent.m00 * inParent.m00 + inParent.m01 * inParent.m01 + inParent.m02 * inParent.m02);
        if (!(scale > 0f)) {
            return;
        }
        inParent.m00 /= scale;
        inParent.m01 /= scale;
        inParent.m02 /= scale;
        inParent.m10 /= scale;
        inParent.m11 /= scale;
        inParent.m12 /= scale;
        inParent.m20 /= scale;
        inParent.m21 /= scale;
        inParent.m22 /= scale;
        Quaternion.setFromMatrix(inParent, parentTurn);
        float px = parentTurn.x, py = parentTurn.y, pz = parentTurn.z, pw = parentTurn.w;
        float sy = (float) Math.sin(yaw / 2f), cy = (float) Math.cos(yaw / 2f);
        float sx = (float) Math.sin(pitch / 2f), cx = (float) Math.cos(pitch / 2f);
        // The tracker's roll is positive tilting to the left, which about the model's forward axis is a negative turn.
        float sz = (float) Math.sin(-roll / 2f), cz = (float) Math.cos(roll / 2f);
        float w = cy * cx, x = cy * sx, y = sy * cx, z = -sy * sx;
        float tx = cz * x + y * sz, ty = cz * y - x * sz, tz = w * sz + cz * z, tw = w * cz - z * sz;
        // p undone, then t.
        float ax = pw * tx - px * tw - py * tz + pz * ty, ay = pw * ty + px * tz - py * tw - pz * tx;
        float az = pw * tz - px * ty + py * tx - pz * tw, aw = pw * tw + px * tx + py * ty + pz * tz;
        // Then p.
        float bx = aw * px + ax * pw + ay * pz - az * py, by = aw * py - ax * pz + ay * pw + az * px;
        float bz = aw * pz + ax * py - ay * px + az * pw, bw = aw * pw - ax * px - ay * py - az * pz;
        AnimatorsBoneTransform kept = animation.getBoneTransformAt(bone.index);
        kept.getPRS(place, was, size);
        float rx = was.x, ry = was.y, rz = was.z, rw = was.w;
        was.set(bw * rx + bx * rw + by * rz - bz * ry, bw * ry - bx * rz + by * rw + bz * rx,
                bw * rz + bx * ry - by * rx + bz * rw, bw * rw - bx * rx - by * ry - bz * rz);
        kept.set(place, was, size);
    }
}
