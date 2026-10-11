package com.cameraunlock.zomboid;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * How much each of a zombie's legs has been shot, and when a leg can no longer carry it.
 *
 * The game takes a twentieth of a bullet's damage off a zombie's health for a hit on a leg and
 * keeps no record of where it was hit, so a zombie shot through both knees walks on as before and
 * only a one in thirty roll on any hit makes a crawler. Here each leg keeps the whole damage of the
 * shots it has taken, thigh and shin together, and a leg that has taken as much as the zombie has
 * health left is ruined.
 *
 * Logic thread only.
 */
final class LegWounds {
    static final int LEFT = 0, RIGHT = 1;

    /** By the zombie's object, which the game neither compares nor hashes by value: kept no longer than the game keeps it. */
    private final Map<Object, float[]> taken = new WeakHashMap<>();

    /**
     * Adds a shot to a zombie's leg and says whether that leg is ruined by it. A zombie whose leg is
     * ruined is forgotten: what happens to it then is the game's.
     *
     * @param leg    {@link #LEFT} or {@link #RIGHT}
     * @param damage the shot's whole damage, before the game's cut for a leg
     * @param health the zombie's health once the shot has been taken off it
     */
    boolean ruins(Object zombie, int leg, float damage, float health) {
        float[] legs = taken.computeIfAbsent(zombie, z -> new float[2]);
        legs[leg] += damage;
        if (legs[leg] < health) {
            return false;
        }
        taken.remove(zombie);
        return true;
    }

    /** Forgets a zombie: it is dead, or the game is making a new zombie of its object. */
    void forget(Object zombie) {
        taken.remove(zombie);
    }

    /** How many zombies have a shot leg on record. */
    int remembered() {
        return taken.size();
    }
}
