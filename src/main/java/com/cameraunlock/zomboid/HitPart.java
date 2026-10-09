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
 * head runs on from the head bone, which is at the base of the skull, the way the neck points. The
 * body is one capsule from the pelvis to the neck, whose rounded top ends at the neck bone: the
 * neck bone is just under the skull, and a body running past it took the lower head. Where on the
 * body the aim lands says chest or belly, split halfway from the pelvis bone to the chest bone, as
 * the game's own ray splits them (measured on a standing zombie: belly up to 0.86 tiles, chest
 * from 0.98, the halfway point 0.88).
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

    private static final int HEAD = 0, BODY = 1;
    private static final String CHEST = "Bip01_Spine1";

    private static final Part[] PARTS = {
            new Part("Bip01_Head", "Bip01_Neck", 0.11f, RagdollBodyPart.BODYPART_HEAD),
            new Part("Bip01_Pelvis", "Bip01_Neck", 0.17f, RagdollBodyPart.BODYPART_SPINE),
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
    private final float[] chest = new float[3];
    private final float[] along = new float[1];
    private final Vector3 bone = new Vector3();
    /** After {@link #part} has found one: the point on the part's own bone level with where the aim is on it, in the picture's units. */
    float boneX, boneY, boneUp;

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
        int head = HEAD * 6, body = BODY * 6;
        // The head runs on from the head bone, away from the neck.
        float alongX = ends[head] - ends[head + 3], alongY = ends[head + 1] - ends[head + 4], alongUp = ends[head + 2] - ends[head + 5];
        float length = (float) Math.sqrt(alongX * alongX + alongY * alongY + alongUp * alongUp);
        ends[head + 3] = ends[head] + alongX / length * HEAD_LENGTH;
        ends[head + 4] = ends[head + 1] + alongY / length * HEAD_LENGTH;
        ends[head + 5] = ends[head + 2] + alongUp / length * HEAD_LENGTH;
        // The body's rounded top ends at the neck bone.
        float upX = ends[body + 3] - ends[body], upY = ends[body + 4] - ends[body + 1], upUp = ends[body + 5] - ends[body + 2];
        float tall = (float) Math.sqrt(upX * upX + upY * upY + upUp * upUp);
        float shorter = (tall - radii[BODY]) / tall;
        ends[body + 3] = ends[body] + upX * shorter;
        ends[body + 4] = ends[body + 1] + upY * shorter;
        ends[body + 5] = ends[body + 2] + upUp * shorter;
        int found = Capsules.first(aim, ends, radii, COUNT, NEAR_MISS, along);
        if (found == NONE) {
            return NONE;
        }
        int at = found * 6;
        boneX = ends[at] + (ends[at + 3] - ends[at]) * along[0];
        boneY = ends[at + 1] + (ends[at + 4] - ends[at + 1]) * along[0];
        boneUp = ends[at + 2] + (ends[at + 5] - ends[at + 2]) * along[0];
        if (found != BODY) {
            return PARTS[found].game.ordinal();
        }
        place(character, CHEST, terrain, chest, 0);
        float chestOn = ((chest[0] - ends[body]) * upX + (chest[1] - ends[body + 1]) * upY + (chest[2] - ends[body + 2]) * upUp) / (tall * tall * shorter);
        return (along[0] >= chestOn / 2f ? RagdollBodyPart.BODYPART_SPINE : RagdollBodyPart.BODYPART_PELVIS).ordinal();
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
