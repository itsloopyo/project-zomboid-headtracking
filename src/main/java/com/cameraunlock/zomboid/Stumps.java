package com.cameraunlock.zomboid;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.util.vector.Matrix4f;
import zombie.characterTextures.BloodBodyPartType;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoZombie;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;

/**
 * A zombie's limb cut off: everything of the limb past the cut is pressed flat into the joint,
 * so the mesh, its clothes with it, ends there in a flat stump the width of the limb.
 *
 * The bone at the cut and every bone that hangs from it are given one more move on top of what
 * the game posed: about the joint, nothing is left along the line from the joint to the next
 * joint down, and a little is taken off across it so the end sits inside the skin above. That
 * is done to the matrices of the bones in the model, once they are made and before the game
 * works out from them the ones the model is drawn with, so both the game's draw and the mod's
 * draw it.
 *
 * The game's matrices take a point as a row on their left: each of the first three rows is
 * where an axis goes, and the last row is the bone's place.
 */
final class Stumps {
    private Stumps() {}

    /** How much of a limb's width its stump keeps across, and of its length along. */
    static final float ACROSS = 0.85f, ALONG = 0.01f;
    /** How much of its width a burst head keeps across: a ragged knot on top of the neck, not a disc as wide as the head. */
    static final float HEAD_ACROSS = 0.35f;

    /**
     * For each of {@link Wounds}' limbs: the bone cut at the wrist or ankle and one further along
     * the hand or foot, then the same at the elbow or knee, then at the shoulder or hip.
     */
    private static final String[][] JOINTS = {
            {"Bip01_L_Hand", "Bip01_L_Finger1", "Bip01_L_Forearm", "Bip01_L_Hand", "Bip01_L_UpperArm", "Bip01_L_Forearm"},
            {"Bip01_R_Hand", "Bip01_R_Finger1", "Bip01_R_Forearm", "Bip01_R_Hand", "Bip01_R_UpperArm", "Bip01_R_Forearm"},
            {"Bip01_L_Foot", "Bip01_L_Toe0", "Bip01_L_Calf", "Bip01_L_Foot", "Bip01_L_Thigh", "Bip01_L_Calf"},
            {"Bip01_R_Foot", "Bip01_R_Toe0", "Bip01_R_Calf", "Bip01_R_Foot", "Bip01_R_Thigh", "Bip01_R_Calf"},
    };
    /** The head is pressed into its own joint, along the line up the neck to it. */
    private static final String HEAD = "Bip01_Head", NECK = "Bip01_Neck";

    /** For each limb, the parts of the game's blood on a body and its clothes from the hip or shoulder down. */
    private static final BloodBodyPartType[][] PARTS = {
            {BloodBodyPartType.UpperArm_L, BloodBodyPartType.ForeArm_L, BloodBodyPartType.Hand_L},
            {BloodBodyPartType.UpperArm_R, BloodBodyPartType.ForeArm_R, BloodBodyPartType.Hand_R},
            {BloodBodyPartType.UpperLeg_L, BloodBodyPartType.LowerLeg_L, BloodBodyPartType.Foot_L},
            {BloodBodyPartType.UpperLeg_R, BloodBodyPartType.LowerLeg_R, BloodBodyPartType.Foot_R},
            {BloodBodyPartType.Head, BloodBodyPartType.Neck, BloodBodyPartType.Torso_Upper},
    };
    /** How many times the game's blood is put on each part: each time adds some. */
    private static final int BLOODIED = 4;

    /** For the trace: whether a zombie's cut and a picture's cut have been described. */
    private static final Set<String> said = ConcurrentHashMap.newKeySet();

    /**
     * Bloodies a limb from the part its stump is on down, skin and clothes, as the game bloodies
     * a wound: the stump and the flat end the limb is pressed into are then red. A hand or a foot
     * leaves its stump on the forearm or calf; any other cut, the whole limb.
     */
    static void bloody(IsoZombie zombie, int limb, int cut) {
        for (int at = cut == Wounds.END ? 1 : 0; at < PARTS[limb].length; at++) {
            for (int i = 0; i < BLOODIED; i++) {
                zombie.addBlood(PARTS[limb][at], false, false, true);
            }
        }
    }

    /**
     * The skeletons the game is posing to draw a picture of a body with its limbs cut, and where:
     * a body's picture is drawn from a model of its own, which no character owns.
     */
    private static final Map<AnimationPlayer, Integer> baking = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean anyBaking;

    /** Says where the limbs of the body a skeleton is posed for a picture of are cut, {@link Wounds#cuts} zero for none. */
    static void baking(AnimationPlayer animation, int cuts) {
        if (cuts == 0) {
            baking.remove(animation);
        } else {
            baking.put(animation, cuts);
            anyBaking = true;
        }
    }

    /** Called from AnimationPlayer as the matrices of the bones in the model are made, before the ones the model is drawn with. */
    static void cut(AnimationPlayer animation, Wounds wounds) {
        if (!wounds.anyoneCut() && !anyBaking) {
            return;
        }
        IsoGameCharacter character = animation.getIsoGameCharacter();
        int cuts;
        if (character instanceof IsoZombie) {
            cuts = wounds.anyoneCut() ? wounds.cuts(character) : 0;
        } else if (character == null && anyBaking) {
            Integer baked = baking.get(animation);
            cuts = baked == null ? 0 : baked;
        } else {
            return;
        }
        if (cuts == 0) {
            return;
        }
        SkinningData skeleton = animation.getSkinningData();
        for (int limb = 0; limb < Wounds.PARTS; limb++) {
            int cut = Wounds.cutIn(cuts, limb);
            if (cut == Wounds.WHOLE) {
                continue;
            }
            boolean head = limb == Wounds.HEAD;
            int at = (cut - 1) * 2;
            String named = head ? HEAD : JOINTS[limb][at], towards = head ? NECK : JOINTS[limb][at + 1];
            SkinningBone bone = bone(skeleton, named), next = bone(skeleton, towards);
            Matrix4f joint = animation.getModelTransformAt(bone.index), below = animation.getModelTransformAt(next.index);
            float jx = joint.m03, jy = joint.m13, jz = joint.m23;
            // Along the limb from the joint, and for the head along the neck below it, which is the line its stump is pressed across.
            float ax = below.m03 - jx, ay = below.m13 - jy, az = below.m23 - jz;
            float length = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            if (!(length > 1e-5f)) {
                continue;
            }
            String whose = character == null ? "a body's picture" : "zombie " + character.getID();
            if (FirstPerson.TRACE && said.add(character == null ? "body" : "zombie")) {
                StringBuilder names = new StringBuilder();
                name(bone, skeleton, names);
                Log.info(String.format(Locale.ROOT, "stumps: %s's limb %d cut at %s, %.3f to %s, pressing%s",
                        whose, limb, named, length, towards, names));
            }
            if (character != null && Pieces.holdsBack(character, limb, jx, jy, jz, ax / length, ay / length, az / length, length)) {
                continue;
            }
            press(animation, bone, jx, jy, jz, ax / length, ay / length, az / length, head ? HEAD_ACROSS : ACROSS);
        }
    }

    private static void press(AnimationPlayer animation, SkinningBone bone, float jx, float jy, float jz, float ax, float ay, float az, float across) {
        Matrix4f m = animation.getModelTransformAt(bone.index);
        float x, y, z, d;
        // The three axes: across kept at across, along at ALONG.
        x = m.m00; y = m.m10; z = m.m20; d = (ALONG - across) * (x * ax + y * ay + z * az);
        m.m00 = across * x + d * ax; m.m10 = across * y + d * ay; m.m20 = across * z + d * az;
        x = m.m01; y = m.m11; z = m.m21; d = (ALONG - across) * (x * ax + y * ay + z * az);
        m.m01 = across * x + d * ax; m.m11 = across * y + d * ay; m.m21 = across * z + d * az;
        x = m.m02; y = m.m12; z = m.m22; d = (ALONG - across) * (x * ax + y * ay + z * az);
        m.m02 = across * x + d * ax; m.m12 = across * y + d * ay; m.m22 = across * z + d * az;
        // The place, about the joint.
        x = m.m03 - jx; y = m.m13 - jy; z = m.m23 - jz; d = (ALONG - across) * (x * ax + y * ay + z * az);
        m.m03 = jx + across * x + d * ax; m.m13 = jy + across * y + d * ay; m.m23 = jz + across * z + d * az;
        if (bone.children != null) {
            for (SkinningBone child : bone.children) {
                press(animation, child, jx, jy, jz, ax, ay, az, across);
            }
        }
    }

    /** The bone a part is cut at: {@link Wounds#LOWER} or {@link Wounds#UPPER} for a limb, the head's own for the head. */
    static String boneOf(int part, int cut) {
        return part == Wounds.HEAD ? HEAD : JOINTS[part][(cut - 1) * 2];
    }

    private static void name(SkinningBone bone, SkinningData skeleton, StringBuilder into) {
        for (var entry : skeleton.boneIndices.entrySet()) {
            if (entry.getValue() == bone.index) {
                into.append(' ').append(entry.getKey());
            }
        }
        if (bone.children != null) {
            for (SkinningBone child : bone.children) {
                name(child, skeleton, into);
            }
        }
    }

    private static SkinningBone bone(SkinningData skeleton, String name) {
        SkinningBone bone = skeleton.getBone(name);
        if (bone == null) {
            throw new IllegalStateException("the game's skeleton has no " + name);
        }
        return bone;
    }
}
