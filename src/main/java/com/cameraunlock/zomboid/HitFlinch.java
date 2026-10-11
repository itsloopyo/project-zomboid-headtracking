package com.cameraunlock.zomboid;

import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import zombie.characters.IsoGameCharacter;
import zombie.core.physics.RagdollBodyPart;
import zombie.core.skinnedmodel.HelperFunctions;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;

/**
 * A zombie shot in first person that stays up is knocked by the round where it went in.
 *
 * The game plays a stagger for the part it was hit in, and nothing of the body answers the round
 * itself: a shot in the face and one in the shoulder both start the same canned step. On top of
 * the stagger, the bones between the body and where the round went in are turned so that point is
 * driven along the line the round flew, at once, then let go over half a second: the head snaps
 * back, a shoulder is spun round, a knee is knocked back, a shot in the belly folds the body over it.
 * How far, is the push the game gives a body that round drops in the chest, so a pistol jolts and
 * a shotgun throws. Not the push for the part hit: the game's table gives a 9 mm round half as much
 * on the head or an arm as on the chest, and measured on a zombie that stayed up, that little turn
 * of the head was lost in the game's own stagger for a head shot.
 *
 * Only the picture is turned. The bones are turned after the game has posed them and put back
 * once the matrices the model is drawn with are made, as {@link ModelHead} does with the
 * player's head, so what the game measures a hit or a reach against is its own pose.
 */
public final class HitFlinch {
    private HitFlinch() {}

    /** Seconds from the hit to the most the body is turned, and how long after the hit it has let go. */
    static final float RISE = 0.05f, LASTS = 0.6f;
    /** How far a part is turned for each unit of the push the game gives a body the round drops in the chest, and the most, in radians: a pistol (20) about 15 degrees, a rifle or a shotgun the most. */
    static final float TURN_PER_PUSH = (float) Math.toRadians(0.75), MOST = (float) Math.toRadians(35);
    /** How many hits a body answers at once: the latest. */
    private static final int HELD = 4;

    /** The bones a hit on each of the game's body parts turns, and each one's share of the turn. Apart, so the rest is used without the game's classes. */
    private static final class Chains {
        static final String[][] BONES = new String[RagdollBodyPart.values().length][];
        static final float[][] SHARES = new float[RagdollBodyPart.values().length][];

        static {
            chain(RagdollBodyPart.BODYPART_PELVIS, "Bip01_Spine", 0.6f, "Bip01_Spine1", 0.4f);
            chain(RagdollBodyPart.BODYPART_SPINE, "Bip01_Spine", 0.4f, "Bip01_Spine1", 0.6f);
            chain(RagdollBodyPart.BODYPART_HEAD, "Bip01_Neck", 0.4f, "Bip01_Head", 0.6f);
            chain(RagdollBodyPart.BODYPART_LEFT_UPPER_ARM, "Bip01_L_Clavicle", 0.3f, "Bip01_L_UpperArm", 0.7f);
            chain(RagdollBodyPart.BODYPART_LEFT_LOWER_ARM, "Bip01_L_UpperArm", 0.4f, "Bip01_L_Forearm", 0.6f);
            chain(RagdollBodyPart.BODYPART_RIGHT_UPPER_ARM, "Bip01_R_Clavicle", 0.3f, "Bip01_R_UpperArm", 0.7f);
            chain(RagdollBodyPart.BODYPART_RIGHT_LOWER_ARM, "Bip01_R_UpperArm", 0.4f, "Bip01_R_Forearm", 0.6f);
            chain(RagdollBodyPart.BODYPART_LEFT_UPPER_LEG, "Bip01_L_Thigh", 1f);
            chain(RagdollBodyPart.BODYPART_LEFT_LOWER_LEG, "Bip01_L_Thigh", 0.4f, "Bip01_L_Calf", 0.6f);
            chain(RagdollBodyPart.BODYPART_RIGHT_UPPER_LEG, "Bip01_R_Thigh", 1f);
            chain(RagdollBodyPart.BODYPART_RIGHT_LOWER_LEG, "Bip01_R_Thigh", 0.4f, "Bip01_R_Calf", 0.6f);
        }

        private static void chain(RagdollBodyPart part, Object... bonesAndShares) {
            int n = bonesAndShares.length / 2;
            BONES[part.ordinal()] = new String[n];
            SHARES[part.ordinal()] = new float[n];
            for (int i = 0; i < n; i++) {
                BONES[part.ordinal()][i] = (String) bonesAndShares[i * 2];
                SHARES[part.ordinal()][i] = (Float) bonesAndShares[i * 2 + 1];
            }
        }
    }

    /**
     * A hit: the part, which way the round flew and where it went in from the character's feet, in
     * the world's axes (x, y, up) and the picture's tiles, how far it turns the part, and when.
     */
    record Hit(int part, float forwardX, float forwardY, float forwardUp, float atX, float atY, float atUp, float most, long at) {}

    /** What a body is answering, and the bones turned for it and not yet put back. */
    private static final class Flinching {
        final Hit[] hits = new Hit[HELD];
        int next;
        final int[] turned = new int[16];
        int turnedCount;
        final Vector3f[] places = new Vector3f[16], sizes = new Vector3f[16];
        final Quaternion[] turns = new Quaternion[16];

        Flinching() {
            for (int i = 0; i < turned.length; i++) {
                places[i] = new Vector3f();
                sizes[i] = new Vector3f();
                turns[i] = new Quaternion();
            }
        }
    }

    private static final ConcurrentHashMap<Integer, Flinching> flinching = new ConcurrentHashMap<>();

    /** Scratch of the thread posing a skeleton: characters are posed on more than one. */
    private static final class Scratch {
        final BoneTurn boneTurn = new BoneTurn();
        final Matrix4f joint = new Matrix4f();
        final Vector3f at = new Vector3f();
    }

    private static final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);

    /**
     * Called as a first person shot is found to be on a part of a zombie.
     *
     * @param push the push the game gives a body that round drops, hit in the chest
     */
    static void hit(IsoGameCharacter target, int part, ViewCamera.Pose aim, HitPart where, float standsUp, float push) {
        Flinching f = flinching.computeIfAbsent(target.getID(), id -> new Flinching());
        synchronized (f) {
            f.hits[f.next] = new Hit(part, aim.forwardX, aim.forwardY, aim.forwardUp, where.hitX - target.getX(), where.hitY - target.getY(),
                    where.hitUp - standsUp, Math.min(MOST, push * TURN_PER_PUSH), System.nanoTime());
            f.next = (f.next + 1) % HELD;
        }
    }

    /** How much of a hit's turn is on at {@code seconds} after it: up to all of it at {@link #RISE}, then let go. */
    static float envelope(float seconds) {
        if (seconds < 0f || seconds >= LASTS) {
            return 0f;
        }
        float t = seconds / RISE;
        return t * (float) Math.exp(1f - t);
    }

    /** Called from AnimationPlayer.updateAnimation_StandardAnimation once the bones are posed and the spine is twisted. */
    public static void pose(AnimationPlayer animation) {
        if (flinching.isEmpty()) {
            return;
        }
        IsoGameCharacter character = animation.getIsoGameCharacter();
        if (character == null) {
            return;
        }
        Flinching f = flinching.get(character.getID());
        if (f == null) {
            return;
        }
        long now = System.nanoTime();
        synchronized (f) {
            f.turnedCount = 0;
            boolean any = false;
            for (Hit hit : f.hits) {
                if (hit != null && (now - hit.at) / 1e9f < LASTS) {
                    any = true;
                }
            }
            if (!any || animation.isRagdolling() || character.isDead()) {
                flinching.remove(character.getID());
                return;
            }
            Scratch s = scratch.get();
            SkinningData skeleton = animation.getSkinningData();
            // The model faces the way the body is drawn turned, and is the world reflected: see ViewCamera.eyeOffset.
            float c = (float) Math.cos(animation.getRenderedAngle() + Math.PI), sn = (float) Math.sin(animation.getRenderedAngle() + Math.PI);
            for (Hit hit : f.hits) {
                if (hit == null) {
                    continue;
                }
                float amount = envelope((now - hit.at) / 1e9f) * hit.most;
                if (amount <= 0f) {
                    continue;
                }
                float px = c * hit.forwardX + sn * hit.forwardY, py = hit.forwardUp, pz = sn * hit.forwardX - c * hit.forwardY;
                float hx = (c * hit.atX + sn * hit.atY) / Characters.CHARACTER_SCALE, hy = hit.atUp / Characters.CHARACTER_SCALE,
                        hz = (sn * hit.atX - c * hit.atY) / Characters.CHARACTER_SCALE;
                String[] bones = Chains.BONES[hit.part];
                for (int i = 0; i < bones.length; i++) {
                    SkinningBone bone = skeleton.getBone(bones[i]);
                    if (bone == null || bone.parent == null) {
                        throw new IllegalStateException("the game's skeleton has no " + bones[i] + " under a parent");
                    }
                    keep(animation, f, bone.index);
                    HelperFunctions.getPosition(animation.getBoneModelTransform(bone.index, s.joint), s.at);
                    // The turn that drives where the round went in along the way it flew: about the arm from the joint to there, crossed with that way.
                    float ax = (hy - s.at.y) * pz - (hz - s.at.z) * py, ay = (hz - s.at.z) * px - (hx - s.at.x) * pz, az = (hx - s.at.x) * py - (hy - s.at.y) * px;
                    float length = (float) Math.sqrt(ax * ax + ay * ay + az * az);
                    if (length < 1e-4f) {
                        continue;
                    }
                    float half = amount * Chains.SHARES[hit.part][i] / 2f, sin = (float) Math.sin(half) / length;
                    s.boneTurn.turn(animation, bone, ax * sin, ay * sin, az * sin, (float) Math.cos(half));
                }
            }
        }
    }

    /** Keeps a bone's place, turn and size as the game posed it, once a frame, to be put back. */
    private static void keep(AnimationPlayer animation, Flinching f, int index) {
        for (int i = 0; i < f.turnedCount; i++) {
            if (f.turned[i] == index) {
                return;
            }
        }
        if (f.turnedCount == f.turned.length) {
            throw new IllegalStateException("more bones turned at once than are kept: " + f.turnedCount);
        }
        animation.getBoneTransformAt(index).getPRS(f.places[f.turnedCount], f.turns[f.turnedCount], f.sizes[f.turnedCount]);
        f.turned[f.turnedCount++] = index;
    }

    /** Called from AnimationPlayer.updateAnimation_StandardAnimation once the matrices the model is drawn with are made. */
    public static void posed(AnimationPlayer animation) {
        if (flinching.isEmpty()) {
            return;
        }
        IsoGameCharacter character = animation.getIsoGameCharacter();
        Flinching f = character == null ? null : flinching.get(character.getID());
        if (f == null) {
            return;
        }
        synchronized (f) {
            for (int i = 0; i < f.turnedCount; i++) {
                animation.getBoneTransformAt(f.turned[i]).set(f.places[i], f.turns[i], f.sizes[i]);
            }
            f.turnedCount = 0;
        }
    }
}
