package com.cameraunlock.zomboid;

/**
 * The game's rules the mod changes when played alone, each a row of [Rules] in CameraUnlock.ini,
 * on unless the player says otherwise. With a row off, or on a server whatever the rows say, the
 * game's own rule holds.
 *
 * The rows are read again while the game runs: the thread that watches the file and the game's
 * options screen set them, and the logic thread asks them as shots land and vehicles move.
 */
final class Rules {
    /** The bits of the rows that are on, in the order Config describes the rows of [Rules]. */
    static final int THROUGH_WALLS = 1, BREAKS_LAMPS = 2, WOUNDS = 4, SMASHES_THROUGH = 8, FACES_THE_VIEW = 16, PAST_TREES = 32;
    static final int ALL = THROUGH_WALLS | BREAKS_LAMPS | WOUNDS | SMASHES_THROUGH | FACES_THE_VIEW | PAST_TREES;

    private static volatile int on = ALL;

    private Rules() {}

    static int rows() {
        return on;
    }

    static void set(int bits) {
        on = bits;
    }

    /** Whether a first person shot goes on through thin walls and doors: see Penetration. */
    static boolean throughWalls() {
        return !Online.joined() && (on & THROUGH_WALLS) != 0;
    }

    /** Whether a first person shot breaks the lamps it passes and the lamps of the vehicles it lands on: see ShotLights. */
    static boolean breaksLamps() {
        return !Online.joined() && (on & BREAKS_LAMPS) != 0;
    }

    /** Whether first person shots wound a zombie's limbs, can take them off and can take off or burst its head: see Online.woundsTell. */
    static boolean wounds() {
        return Online.woundsTell() && (on & WOUNDS) != 0;
    }

    /**
     * Whether the game's rules for a vehicle meeting things hold: fences and lamp posts stop it,
     * bushes slow it and its engine gives less off the road. See Knockdown.
     */
    static boolean vehiclesStock() {
        return Online.joined() || (on & SMASHES_THROUGH) == 0;
    }

    /** Whether a character walked sideways or backwards in first person keeps facing the view, at the game's pace for the walk: see Controls and Sidestep. */
    static boolean facesTheView() {
        return !Online.joined() && (on & FACES_THE_VIEW) != 0;
    }

    /** Whether a first person shot goes on past a tree it does not meet the bole of: see Trunks. */
    static boolean pastTrees() {
        return !Online.joined() && (on & PAST_TREES) != 0;
    }

    /** The rows in words, for the log. */
    static String described(int bits) {
        return "shots through walls " + ((bits & THROUGH_WALLS) != 0) + ", shots break lamps " + ((bits & BREAKS_LAMPS) != 0)
                + ", shots wound limbs " + ((bits & WOUNDS) != 0) + ", vehicles smash through " + ((bits & SMASHES_THROUGH) != 0)
                + ", faces the view walking aside " + ((bits & FACES_THE_VIEW) != 0) + ", shots past trees " + ((bits & PAST_TREES) != 0);
    }
}
