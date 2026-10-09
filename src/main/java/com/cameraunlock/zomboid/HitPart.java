package com.cameraunlock.zomboid;

import zombie.CombatManager;
import zombie.characters.IsoGameCharacter;
import zombie.core.physics.RagdollBodyPart;
import zombie.iso.Vector3;

/**
 * Which part of a character a first person shot hits: the limb the clean aim passes into first.
 *
 * The game takes a shot's body part from a ray its physics library casts through the reticle along
 * its own overhead camera, and in first person that ray comes back with no part on most shots, or
 * with the head for a shot at the chest (measured: 90 aimed shots, the chest never reported once).
 * With no part the game rolls one at random, an arm or a leg among them at a twentieth of the damage.
 * In first person the player sees which part the aim is on, so that part is the one hit.
 *
 * Each part is a capsule along two of the character's bones as the game posed them this frame. The
 * head runs on from the head bone, which is at the base of the skull, the way the neck points.
 */
final class HitPart {
    /** No part: the aim passes none of them, and the game decides as it would. */
    static final int NONE = Capsules.NONE;

    /** How far the head reaches past its bone, and the radius of each part, in tiles. */
    static final float HEAD_LENGTH = 0.22f;
    /**
     * How far from a part the aim may pass and still be on it. The to-hit roll is the game's: when
     * it has the character as its target, an aim that only just misses an arm or the head was
     * still meant for the body, and the nearest part is the one hit.
     */
    static final float NEAR_MISS = 0.12f;

    /** A part: the bone it starts at, the bone it runs to, its radius in tiles, and the game's name for it. */
    private record Part(String from, String to, float radius, RagdollBodyPart game) {}

    private static final Part[] PARTS = {
            new Part("Bip01_Head", "Bip01_Neck", 0.11f, RagdollBodyPart.BODYPART_HEAD),
            new Part("Bip01_Spine1", "Bip01_Neck", 0.17f, RagdollBodyPart.BODYPART_SPINE),
            new Part("Bip01_Pelvis", "Bip01_Spine1", 0.16f, RagdollBodyPart.BODYPART_PELVIS),
            new Part("Bip01_L_UpperArm", "Bip01_L_Forearm", 0.06f, RagdollBodyPart.BODYPART_LEFT_UPPER_ARM),
            new Part("Bip01_L_Forearm", "Bip01_L_Hand", 0.05f, RagdollBodyPart.BODYPART_LEFT_LOWER_ARM),
            new Part("Bip01_R_UpperArm", "Bip01_R_Forearm", 0.06f, RagdollBodyPart.BODYPART_RIGHT_UPPER_ARM),
            new Part("Bip01_R_Forearm", "Bip01_R_Hand", 0.05f, RagdollBodyPart.BODYPART_RIGHT_LOWER_ARM),
            new Part("Bip01_L_Thigh", "Bip01_L_Calf", 0.08f, RagdollBodyPart.BODYPART_LEFT_UPPER_LEG),
            new Part("Bip01_L_Calf", "Bip01_L_Foot", 0.06f, RagdollBodyPart.BODYPART_LEFT_LOWER_LEG),
            new Part("Bip01_R_Thigh", "Bip01_R_Calf", 0.08f, RagdollBodyPart.BODYPART_RIGHT_UPPER_LEG),
            new Part("Bip01_R_Calf", "Bip01_R_Foot", 0.06f, RagdollBodyPart.BODYPART_RIGHT_LOWER_LEG),
    };
    static final int COUNT = PARTS.length;

    private final float[] ends = new float[COUNT * 6];
    private final float[] radii = new float[COUNT];
    private final Vector3 bone = new Vector3();

    /**
     * The game's ordinal of the part of {@code character} the aim is on, or {@link #NONE}.
     *
     * @param aim     the clean aim: where the shot is aimed from and which way, in the picture's units
     * @param terrain how far the picture lifts the land above the game's flat map
     */
    int part(ViewCamera.Pose aim, Terrain terrain, IsoGameCharacter character) {
        for (int i = 0; i < COUNT; i++) {
            place(character, PARTS[i].from, terrain, ends, i * 6);
            place(character, PARTS[i].to, terrain, ends, i * 6 + 3);
            radii[i] = PARTS[i].radius;
        }
        // The head capsule runs from the head bone on, away from the neck.
        float alongX = ends[0] - ends[3], alongY = ends[1] - ends[4], alongUp = ends[2] - ends[5];
        float length = (float) Math.sqrt(alongX * alongX + alongY * alongY + alongUp * alongUp);
        ends[3] = ends[0] + alongX / length * HEAD_LENGTH;
        ends[4] = ends[1] + alongY / length * HEAD_LENGTH;
        ends[5] = ends[2] + alongUp / length * HEAD_LENGTH;
        int found = Capsules.first(aim, ends, radii, COUNT, NEAR_MISS);
        return found == NONE ? NONE : PARTS[found].game.ordinal();
    }

    /** Where a bone of the character is in the picture: tiles across, and up as the picture has it. */
    private void place(IsoGameCharacter character, String name, Terrain terrain, float[] out, int at) {
        if (character.getAnimationPlayer() == null || character.getAnimationPlayer().getSkinningBoneIndex(name, -1) < 0) {
            throw new IllegalStateException("the game's skeleton has no bone " + name);
        }
        CombatManager.getBoneWorldPos(character, name, bone);
        out[at] = bone.x;
        out[at + 1] = bone.y;
        out[at + 2] = bone.z * ViewCamera.LEVEL_HEIGHT + terrain.lift(bone.x, bone.y, character.getZ());
    }
}
