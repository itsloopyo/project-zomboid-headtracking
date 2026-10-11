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
 * that has taken all of it is ruined. A limb only gets worse.
 *
 * Written on the logic thread, read by the threads that pose skeletons.
 */
final class Wounds {
    static final int LEFT_ARM = 0, RIGHT_ARM = 1, LEFT_LEG = 2, RIGHT_LEG = 3, LIMBS = 4;
    static final int SOUND = 0, WOUNDED = 1, RUINED = 2;
    /** How much of the health left a limb's shots wound it at. */
    static final float WOUNDED_SHARE = 0.5f;

    private static final class Limbs {
        final float[] taken = new float[LIMBS];
        final int[] stages = new int[LIMBS];
    }

    /** By the zombie's object, which the game neither compares nor hashes by value: kept no longer than the game keeps it. */
    private final Map<Object, Limbs> zombies = new WeakHashMap<>();
    /** Whether any zombie is on record, read without the lock by a pose that has no wounded zombie to look for. */
    private volatile boolean any;

    /**
     * Adds a shot to a zombie's limb and says what it did: the stage the limb came to, or
     * {@link #SOUND} when the shot left it as it was.
     *
     * @param damage the shot's whole damage, before the game's cut for a limb
     * @param health the zombie's health once the shot has been taken off it
     */
    synchronized int shot(Object zombie, int limb, float damage, float health) {
        Limbs limbs = zombies.computeIfAbsent(zombie, z -> new Limbs());
        any = true;
        limbs.taken[limb] += damage;
        int stage = limbs.taken[limb] >= health ? RUINED : limbs.taken[limb] >= health * WOUNDED_SHARE ? WOUNDED : SOUND;
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

    /** Whether any zombie may be hurt, for a pose to pass by without the lock when none is. */
    boolean anyone() {
        return any;
    }

    /** Forgets a zombie: it is dead, or the game is making a new zombie of its object. */
    synchronized void forget(Object zombie) {
        zombies.remove(zombie);
        any = !zombies.isEmpty();
    }

    /** How many zombies are on record. */
    synchronized int remembered() {
        return zombies.size();
    }
}
