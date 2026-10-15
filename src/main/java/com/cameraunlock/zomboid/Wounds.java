package com.cameraunlock.zomboid;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * How much each of a zombie's limbs has been shot, and how badly that has left it.
 *
 * The game takes a twentieth of a bullet's damage off a zombie's health for a hit on an arm or a
 * leg and keeps no record of where it was hit, so a zombie shot through both knees walks on as
 * before. Here each limb keeps the whole damage of the shots it has taken, upper and lower part
 * together. A limb that has taken half as much as the zombie has health left is wounded, and one
 * that has taken all of it is ruined. One that has taken a few times the health left comes off, at
 * the joint above the part the last shot was on: a hand or a foot for less than a forearm or a
 * calf, and an upper arm or a thigh for most. A limb only gets worse.
 *
 * Written on the logic thread, read by the threads that pose skeletons.
 */
final class Wounds {
    static final int LEFT_ARM = 0, RIGHT_ARM = 1, LEFT_LEG = 2, RIGHT_LEG = 3, LIMBS = 4;
    /** The head, which is not shot off as a limb is but bursts: cut {@link #UPPER} once it has. Then how many parts can be cut. */
    static final int HEAD = 4, PARTS = 5;
    static final int SOUND = 0, WOUNDED = 1, RUINED = 2, SEVERED = 3;
    /**
     * Where a limb is cut off: none, at the wrist or ankle, at the elbow or knee, or at the
     * shoulder or hip. Where a shot is on a limb is told the same way: the hand or foot, the
     * forearm or calf, the upper arm or thigh. Then how many ways a cut is told.
     */
    static final int WHOLE = 0, END = 1, LOWER = 2, UPPER = 3, CUTS = 4;
    /** How much of the health left a limb's shots wound it at. */
    static final float WOUNDED_SHARE = 0.5f;
    /**
     * How many times the health left a limb's shots add up to for it to come off, by where the last
     * shot is on it. A zombie has about 1.9 health and an M16 round does 1 to 1.6, four times that
     * on one shot in five, so a leg is ruined by a round or two and takes some four at the calf to
     * come off.
     */
    static final float[] SEVERING_SHARE = {0f, 3f, 4f, 6f};

    private static final class Limbs {
        final float[] taken = new float[LIMBS];
        final int[] stages = new int[LIMBS];
        final int[] cuts = new int[PARTS];
        boolean headShot;
    }

    /** By the zombie's object, which the game neither compares nor hashes by value: kept no longer than the game keeps it. */
    private final Map<Object, Limbs> zombies = new WeakHashMap<>();
    /** Whether any zombie is on record, read without the lock by a pose that has no wounded zombie to look for. */
    private volatile boolean any;
    /** Whether any zombie on record has a limb cut off, read without the lock as {@link #any} is. */
    private volatile boolean anyCut;

    /**
     * Adds a shot to a zombie's limb and says what it did: the stage the limb came to, or
     * {@link #SOUND} when the shot left it as it was.
     *
     * @param damage the shot's whole damage, before the game's cut for a limb
     * @param health the zombie's health once the shot has been taken off it
     * @param at     where on the limb the shot was: {@link #END}, {@link #LOWER} or {@link #UPPER}
     */
    synchronized int shot(Object zombie, int limb, float damage, float health, int at) {
        Limbs limbs = zombies.computeIfAbsent(zombie, z -> new Limbs());
        any = true;
        limbs.taken[limb] += damage;
        boolean severs = limbs.taken[limb] >= health * SEVERING_SHARE[at];
        int stage = severs ? SEVERED : limbs.taken[limb] >= health ? RUINED : limbs.taken[limb] >= health * WOUNDED_SHARE ? WOUNDED : SOUND;
        if (stage == SEVERED) {
            if (at > limbs.cuts[limb]) {
                limbs.cuts[limb] = at;
                anyCut = true;
                limbs.stages[limb] = SEVERED;
                return SEVERED;
            }
        }
        if (stage <= limbs.stages[limb]) {
            return SOUND;
        }
        limbs.stages[limb] = stage;
        return stage;
    }

    synchronized int stage(Object zombie, int limb) {
        Limbs limbs = zombies.get(zombie);
        return limbs == null ? SOUND : limbs.stages[limb];
    }

    /** Whether this is the first killing head shot on a zombie to be rolled for. */
    synchronized boolean firstHeadShot(Object zombie) {
        Limbs limbs = zombies.computeIfAbsent(zombie, z -> new Limbs());
        any = true;
        boolean first = !limbs.headShot;
        limbs.headShot = true;
        return first;
    }

    /** Bursts a zombie's head. */
    synchronized void burst(Object zombie) {
        zombies.computeIfAbsent(zombie, z -> new Limbs()).cuts[HEAD] = UPPER;
        any = true;
        anyCut = true;
    }

    /** Where a zombie's limb is cut off: {@link #WHOLE}, {@link #END}, {@link #LOWER} or {@link #UPPER}. */
    synchronized int cut(Object zombie, int limb) {
        Limbs limbs = zombies.get(zombie);
        return limbs == null ? WHOLE : limbs.cuts[limb];
    }

    /** Whether any zombie may have a limb cut off, for a pose to pass by without the lock when none has. */
    boolean anyoneCut() {
        return anyCut;
    }

    /** Where each limb of a zombie is cut, as one number: each limb's cut a digit in fours, left arm first. Zero for none. */
    synchronized int cuts(Object zombie) {
        Limbs limbs = zombies.get(zombie);
        int code = 0;
        for (int limb = PARTS - 1; limbs != null && limb >= 0; limb--) {
            code = code * CUTS + limbs.cuts[limb];
        }
        return code;
    }

    /** Where a limb is cut in a number {@link #cuts} made. */
    static int cutIn(int cuts, int limb) {
        for (int i = 0; i < limb; i++) {
            cuts /= CUTS;
        }
        return cuts % CUTS;
    }

    /** Adds every zombie on record with a limb cut off to {@code into}. */
    synchronized void cutZombies(java.util.List<Object> into) {
        for (Map.Entry<Object, Limbs> entry : zombies.entrySet()) {
            int[] cuts = entry.getValue().cuts;
            if (cuts[0] + cuts[1] + cuts[2] + cuts[3] + cuts[4] > 0) {
                into.add(entry.getKey());
            }
        }
    }

    /** Whether any zombie may be hurt, for a pose to pass by without the lock when none is. */
    boolean anyone() {
        return any;
    }

    /** Forgets a zombie: the game is making a new zombie of its object. */
    synchronized void forget(Object zombie) {
        zombies.remove(zombie);
        any = !zombies.isEmpty();
        anyCut = any && zombies.values().stream().anyMatch(limbs -> limbs.cuts[0] + limbs.cuts[1] + limbs.cuts[2] + limbs.cuts[3] + limbs.cuts[4] > 0);
    }

    /** How many zombies are on record. */
    synchronized int remembered() {
        return zombies.size();
    }
}
