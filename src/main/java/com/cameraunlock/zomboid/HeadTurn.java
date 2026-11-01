package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.function.Supplier;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.SkinningBone;

/**
 * A posed character's head turned with ModelHead.turnHead, and put back as the animations had it
 * once the matrices the model is drawn with are made (ModelHead says why it has to be).
 *
 * The clavicles hang from Bip01_Neck, so a neck taking part of the turn would carry both arms
 * with it. Whatever else hangs from the neck is kept where the animations had it in the model.
 */
final class HeadTurn {
    private final BoneTurn boneTurn = new BoneTurn();
    private final Matrix4f neckInModel = new Matrix4f(), inNeck = new Matrix4f();
    /** The bones written, the neck and the head first, each with its place, turn and size in its parent as the animations had them. */
    private int[] bones = new int[2];
    private Vector3f[] places = {new Vector3f(), new Vector3f()}, sizes = {new Vector3f(), new Vector3f()};
    private Quaternion[] turns = {new Quaternion(), new Quaternion()};
    /** Of the bones after the neck and the head, where each was in the model as the animations had it. */
    private Matrix4f[] inModel = {};
    private int count;
    /** The skeleton turned and not yet put back, between turn and putBack. */
    private AnimationPlayer turnedIn;

    /** Turns the head as ModelHead.turnHead does, keeping what else hangs from the neck where it was in the model. */
    void turn(AnimationPlayer animation, SkinningBone neck, SkinningBone top, float yaw, float pitch, float roll, float neckShare) {
        count = 0;
        keep(animation, neck);
        keep(animation, top);
        if (neckShare > 0f && neck.children != null) {
            for (SkinningBone child : neck.children) {
                if (child != top) {
                    keep(animation, child);
                    animation.getBoneModelTransform(child.index, inModel[count - 3]);
                }
            }
        }
        turnedIn = animation;
        ModelHead.turnHead(boneTurn, animation, neck, top, yaw, pitch, roll, neckShare);
        if (count > 2) {
            animation.getBoneModelTransform(neck.index, neckInModel);
            Matrix4f.invert(neckInModel, neckInModel);
            for (int i = 2; i < count; i++) {
                // The game's matrices are kept transposed (ModelHead.keepPosed), so a bone's matrix in the model is its matrix in its parent times its parent's in the model.
                Matrix4f.mul(inModel[i - 2], neckInModel, inNeck);
                animation.getBoneTransformAt(bones[i]).set(inNeck);
            }
        }
    }

    /** Puts back what turn wrote, once the matrices the model is drawn with are made. */
    void putBack(AnimationPlayer animation) {
        if (turnedIn != animation) {
            return;
        }
        turnedIn = null;
        for (int i = 0; i < count; i++) {
            animation.getBoneTransformAt(bones[i]).set(places[i], turns[i], sizes[i]);
        }
    }

    private void keep(AnimationPlayer animation, SkinningBone bone) {
        if (count == bones.length) {
            int grown = count * 2;
            bones = Arrays.copyOf(bones, grown);
            places = grow(places, grown, Vector3f::new);
            sizes = grow(sizes, grown, Vector3f::new);
            turns = grow(turns, grown, Quaternion::new);
            inModel = grow(inModel, grown - 2, Matrix4f::new);
        }
        bones[count] = bone.index;
        animation.getBoneTransformAt(bone.index).getPRS(places[count], turns[count], sizes[count]);
        count++;
    }

    private static <T> T[] grow(T[] was, int length, Supplier<T> make) {
        T[] grown = Arrays.copyOf(was, length);
        for (int i = was.length; i < length; i++) {
            grown[i] = make.get();
        }
        return grown;
    }
}
