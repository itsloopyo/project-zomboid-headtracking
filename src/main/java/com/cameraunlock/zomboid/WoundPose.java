package com.cameraunlock.zomboid;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Quaternion;
import org.lwjgl.util.vector.Vector3f;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoZombie;
import zombie.core.skinnedmodel.HelperFunctions;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;

/**
 * What a standing zombie's wounds look like: a shot arm hangs, and the body lurches over a wounded
 * leg while its foot is down, as a body favours a leg it cannot trust.
 *
 * A wounded arm sags most of the way down from where its walk holds it and a ruined one hangs
 * straight from the shoulder: the upper arm and then the forearm are each turned about their own
 * joint towards straight down, a little out from the body so the hand clears the hip. The lurch
 * leans the chest towards the wounded leg's side, more the lower that foot is than the other, so it
 * keeps time with whatever walk the game plays. The legs themselves are left as the walk has them,
 * since a foot moved off the walk's own path slides or sinks into the ground.
 *
 * The bones are turned after the game has posed them and put back once the matrices the model is
 * drawn with are made, as {@link HitFlinch} does, so the game's next pose starts from its own.
 * Only while the zombie is up: crawling, its arms carry it.
 */
final class WoundPose {
    private WoundPose() {}

    /** How much of the way to hanging straight down a wounded arm is turned, and a ruined one. */
    static final float WOUNDED_SAG = 0.6f, RUINED_SAG = 1f;
    /** How far out from the body a hanging arm points, for each unit down. */
    private static final float OUT = 0.12f;
    /** How far the chest leans over a wounded leg whose foot is down and the other up. */
    static final float LURCH = (float) Math.toRadians(14);
    /** How much lower than the other foot, as a share of the thigh's length, a wounded leg's foot has the whole lurch on it. */
    static final float PLANTED = 0.15f;

    private static final String[][] ARMS = {
            {"Bip01_L_UpperArm", "Bip01_L_Forearm", "Bip01_L_Hand"},
            {"Bip01_R_UpperArm", "Bip01_R_Forearm", "Bip01_R_Hand"},
    };
    /** Each leg's hip, knee and foot. */
    private static final String[][] LEGS = {
            {"Bip01_L_Thigh", "Bip01_L_Calf", "Bip01_L_Foot"},
            {"Bip01_R_Thigh", "Bip01_R_Calf", "Bip01_R_Foot"},
    };
    private static final String NECK = "Bip01_Neck", CHEST = "Bip01_Spine1";

    /** The bones turned on this thread's skeleton and not yet put back, and its scratch: characters are posed on more than one thread. */
    private static final class Posing {
        AnimationPlayer turnedIn;
        final int[] turned = new int[8];
        int count;
        final Vector3f[] places = new Vector3f[8], sizes = new Vector3f[8];
        final Quaternion[] turns = new Quaternion[8];
        final BoneTurn boneTurn = new BoneTurn();
        final Matrix4f joint = new Matrix4f();
        final Vector3f from = new Vector3f(), to = new Vector3f(), neck = new Vector3f(), hip = new Vector3f(), knee = new Vector3f(),
                foot = new Vector3f(), otherHip = new Vector3f(), otherFoot = new Vector3f();

        Posing() {
            for (int i = 0; i < turned.length; i++) {
                places[i] = new Vector3f();
                sizes[i] = new Vector3f();
                turns[i] = new Quaternion();
            }
        }
    }

    private static final ThreadLocal<Posing> posing = ThreadLocal.withInitial(Posing::new);
    private static volatile boolean chestChecked;
    /** For the trace: when a lurch and a hang were last written, a line every so often whichever thread poses. */
    private static volatile long lurchSaid, hangSaid;
    private static final long LURCH_EVERY = 100_000_000L, HANG_EVERY = 1_000_000_000L;
    /** For the trace: the poses of wounded zombies and the time they took, since the last line. */
    private static final AtomicLong posed = new AtomicLong(), posingNanos = new AtomicLong();
    private static volatile long costSaid;

    /** Called from AnimationPlayer.updateAnimation_StandardAnimation once the bones are posed and the spine is twisted. */
    static void pose(AnimationPlayer animation, Wounds wounds) {
        if (!wounds.anyone()) {
            return;
        }
        IsoGameCharacter character = animation.getIsoGameCharacter();
        if (!(character instanceof IsoZombie zombie) || animation.isRagdolling() || zombie.isDead() || zombie.isCrawling() || zombie.isOnFloor()) {
            return;
        }
        int leftArm = wounds.stage(zombie, Wounds.LEFT_ARM), rightArm = wounds.stage(zombie, Wounds.RIGHT_ARM);
        int leftLeg = wounds.stage(zombie, Wounds.LEFT_LEG), rightLeg = wounds.stage(zombie, Wounds.RIGHT_LEG);
        if (leftArm == Wounds.SOUND && rightArm == Wounds.SOUND && leftLeg != Wounds.WOUNDED && rightLeg != Wounds.WOUNDED) {
            return;
        }
        long start = FirstPerson.TRACE ? System.nanoTime() : 0L;
        Posing p = posing.get();
        p.turnedIn = animation;
        p.count = 0;
        SkinningData skeleton = animation.getSkinningData();
        if (leftLeg == Wounds.WOUNDED) {
            lurch(animation, p, skeleton, 0);
        }
        if (rightLeg == Wounds.WOUNDED) {
            lurch(animation, p, skeleton, 1);
        }
        if (leftArm != Wounds.SOUND) {
            hang(animation, p, skeleton, ARMS[0], leftArm == Wounds.RUINED ? RUINED_SAG : WOUNDED_SAG);
        }
        if (rightArm != Wounds.SOUND) {
            hang(animation, p, skeleton, ARMS[1], rightArm == Wounds.RUINED ? RUINED_SAG : WOUNDED_SAG);
        }
        if (FirstPerson.TRACE) {
            long now = System.nanoTime();
            posed.incrementAndGet();
            posingNanos.addAndGet(now - start);
            if (now - costSaid > HANG_EVERY) {
                costSaid = now;
                Log.info(String.format(Locale.ROOT, "wound pose: %d poses of wounded zombies in %.3f ms since the last line", posed.getAndSet(0), posingNanos.getAndSet(0) / 1e6));
            }
        }
    }

    /** How much of the lurch is on: all of it with the wounded leg's foot {@link #PLANTED} of the thigh lower than the other, none with it as much higher. */
    static float stance(float woundedFootUp, float otherFootUp, float thighLength) {
        return Math.clamp(0.5f + (otherFootUp - woundedFootUp) / (2f * PLANTED * thighLength), 0f, 1f);
    }

    private static void lurch(AnimationPlayer animation, Posing p, SkinningData skeleton, int leg) {
        SkinningBone chest = bone(skeleton, CHEST);
        if (!chestChecked) {
            // A leg hung from the chest would be swung with it, foot and all.
            for (String[] each : LEGS) {
                for (SkinningBone up = bone(skeleton, each[0]); up != null; up = up.parent) {
                    if (up == chest) {
                        throw new IllegalStateException("the game's skeleton hangs " + each[0] + " from " + CHEST + ", which a lurch turns");
                    }
                }
            }
            chestChecked = true;
        }
        String[] wounded = LEGS[leg], other = LEGS[1 - leg];
        position(animation, p, bone(skeleton, wounded[0]), p.hip);
        position(animation, p, bone(skeleton, wounded[1]), p.knee);
        position(animation, p, bone(skeleton, wounded[2]), p.foot);
        position(animation, p, bone(skeleton, other[0]), p.otherHip);
        position(animation, p, bone(skeleton, other[2]), p.otherFoot);
        float kx = p.knee.x - p.hip.x, ky = p.knee.y - p.hip.y, kz = p.knee.z - p.hip.z;
        float thigh = (float) Math.sqrt(kx * kx + ky * ky + kz * kz);
        // Towards the wounded leg's side, level: from the other hip to this one.
        float sideX = p.hip.x - p.otherHip.x, sideZ = p.hip.z - p.otherHip.z;
        float side = (float) Math.sqrt(sideX * sideX + sideZ * sideZ);
        if (!(thigh > 0f) || side < 1e-5f) {
            return;
        }
        sideX /= side;
        sideZ /= side;
        float angle = LURCH * stance(p.foot.y, p.otherFoot.y, thigh);
        if (FirstPerson.TRACE && System.nanoTime() - lurchSaid > LURCH_EVERY) {
            lurchSaid = System.nanoTime();
            Log.info(String.format(Locale.ROOT, "lurch: zombie %d over its %s leg %.1f degrees, its foot %.3f and the other %.3f up, thigh %.3f",
                    animation.getIsoGameCharacter().getID(), leg == 0 ? "left" : "right", Math.toDegrees(angle), p.foot.y, p.otherFoot.y, thigh));
        }
        if (angle <= 0f) {
            return;
        }
        // Turned about the level line square to the side, the way that tips the chest's top over that side.
        float s = (float) Math.sin(angle / 2f);
        keep(animation, p, chest.index);
        p.boneTurn.turn(animation, chest, sideZ * s, 0f, -sideX * s, (float) Math.cos(angle / 2f));
    }

    private static void hang(AnimationPlayer animation, Posing p, SkinningData skeleton, String[] names, float sag) {
        SkinningBone upper = bone(skeleton, names[0]), fore = bone(skeleton, names[1]), hand = bone(skeleton, names[2]);
        position(animation, p, bone(skeleton, NECK), p.neck);
        position(animation, p, upper, p.from);
        // Out to this arm's side of the neck, level.
        float outX = p.from.x - p.neck.x, outZ = p.from.z - p.neck.z, out = (float) Math.sqrt(outX * outX + outZ * outZ);
        if (out < 1e-5f) {
            return;
        }
        outX *= OUT / out;
        outZ *= OUT / out;
        float before = FirstPerson.TRACE ? fromDown(animation, p, upper, hand) : 0f;
        keep(animation, p, upper.index);
        turnTowards(animation, p, upper, fore, outX, outZ, sag);
        keep(animation, p, fore.index);
        turnTowards(animation, p, fore, hand, outX, outZ, sag);
        if (FirstPerson.TRACE && System.nanoTime() - hangSaid > HANG_EVERY) {
            hangSaid = System.nanoTime();
            Log.info(String.format(Locale.ROOT, "hang: zombie %d's %s sagged %.1f, shoulder to hand %.0f degrees from straight down, %.0f as the game posed it",
                    animation.getIsoGameCharacter().getID(), names[0], sag, fromDown(animation, p, upper, hand), before));
        }
    }

    /** For the trace: how far, in degrees, the line from one bone's joint to another's is from straight down. */
    private static float fromDown(AnimationPlayer animation, Posing p, SkinningBone from, SkinningBone to) {
        position(animation, p, from, p.from);
        position(animation, p, to, p.to);
        float dx = p.to.x - p.from.x, dy = p.to.y - p.from.y, dz = p.to.z - p.from.z;
        return (float) Math.toDegrees(Math.acos(Math.clamp(-dy / Math.sqrt(dx * dx + dy * dy + dz * dz), -1.0, 1.0)));
    }

    /** Turns {@code bone} about its joint by {@code share} of the turn that points it, at {@code child}, down (the model's -y) and out by (outX, outZ). */
    private static void turnTowards(AnimationPlayer animation, Posing p, SkinningBone bone, SkinningBone child, float outX, float outZ, float share) {
        position(animation, p, bone, p.from);
        position(animation, p, child, p.to);
        float dx = p.to.x - p.from.x, dy = p.to.y - p.from.y, dz = p.to.z - p.from.z;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-5f) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        float targetLength = (float) Math.sqrt(outX * outX + outZ * outZ + 1f);
        float tx = outX / targetLength, ty = -1f / targetLength, tz = outZ / targetLength;
        float ax = dy * tz - dz * ty, ay = dz * tx - dx * tz, az = dx * ty - dy * tx;
        float sin = (float) Math.sqrt(ax * ax + ay * ay + az * az), cos = dx * tx + dy * ty + dz * tz;
        if (sin < 1e-5f) {
            return;
        }
        float half = (float) Math.atan2(sin, cos) * share / 2f, s = (float) Math.sin(half) / sin;
        p.boneTurn.turn(animation, bone, ax * s, ay * s, az * s, (float) Math.cos(half));
    }

    private static void position(AnimationPlayer animation, Posing p, SkinningBone bone, Vector3f out) {
        HelperFunctions.getPosition(animation.getBoneModelTransform(bone.index, p.joint), out);
    }

    private static SkinningBone bone(SkinningData skeleton, String name) {
        SkinningBone bone = skeleton.getBone(name);
        if (bone == null || bone.parent == null) {
            throw new IllegalStateException("the game's skeleton has no " + name + " under a parent");
        }
        return bone;
    }

    /** Keeps a bone's place, turn and size as they are now, to be put back. */
    private static void keep(AnimationPlayer animation, Posing p, int index) {
        animation.getBoneTransformAt(index).getPRS(p.places[p.count], p.turns[p.count], p.sizes[p.count]);
        p.turned[p.count++] = index;
    }

    /** Called from AnimationPlayer.updateAnimation_StandardAnimation once the matrices the model is drawn with are made. */
    static void posed(AnimationPlayer animation) {
        Posing p = posing.get();
        if (p.turnedIn != animation) {
            return;
        }
        // Last turned first: a bone's kept pose is the one before any of this frame's turns of it.
        for (int i = p.count - 1; i >= 0; i--) {
            animation.getBoneTransformAt(p.turned[i]).set(p.places[i], p.turns[i], p.sizes[i]);
        }
        p.count = 0;
        p.turnedIn = null;
    }
}
