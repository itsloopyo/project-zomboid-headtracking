package com.cameraunlock.zomboid;

import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.animation.AnimatorsBoneTransform;
import zombie.core.skinnedmodel.model.SkinningBone;

/**
 * Turns one bone of a posed skeleton about its own joint by a turn given in the model's axes.
 *
 * The bone's place and size in its parent stay. Its turn in its parent takes the turn in the
 * model's axes carried into the parent's: with p the parent's turn in the model, t the turn and
 * r the bone's turn in its parent, the new turn is p undone, then t, then p, on r. The parent's
 * turn in the model is worked out from the bones as they are posed now, a parent this turned
 * already included.
 */
final class BoneTurn {
    private final Matrix4f inParent = new Matrix4f();
    private final Quaternion parentTurn = new Quaternion(), was = new Quaternion();
    private final Vector3f place = new Vector3f(), size = new Vector3f();

    /** Turns {@code bone} by the unit quaternion (tx, ty, tz, tw) in the model's axes. */
    void turn(AnimationPlayer animation, SkinningBone bone, float tx, float ty, float tz, float tw) {
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
