package com.cameraunlock.zomboid;

import zombie.network.GameServer;

/**
 * The game's rules the mod changes when played alone, each a row of [Rules] in CameraUnlock.ini,
 * on unless the player says otherwise, bar Rewind30s, which is off unless they say so and is a row
 * only in a development build (see Rewind). With a row off the game's own rule holds. On a server
 * a rule that changes what the server keeps (a lamp, the glass, a zombie, a vehicle, the world) is
 * the game's whatever the rows say. What a shot reaches the server takes as the shooter's game reports
 * it, so a shot through a wall or past a tree holds where players cannot fight (Online.contested), as
 * does a rule that changes only this player's own screen or stance; one that shows nothing of another
 * player holds on every server.
 * Where players can fight, FacesTheView is the one rule the mod applies even with its row off: see facing.
 *
 * The rows are read again while the game runs: the thread that watches the file and the game's
 * options screen set them, and the logic thread asks them as shots land and vehicles move.
 */
final class Rules {
    /** The bits of the rows that are on, in the order Config describes the rows of [Rules]. */
    static final int THROUGH_WALLS = 1, BREAKS_LAMPS = 2, BREAKS_GLASS = 4, WOUNDS = 8, SMASHES_THROUGH = 16, FACES_THE_VIEW = 32, PAST_TREES = 64,
            SEEN_AT_ONCE = 128, REWIND = 256, HIDDEN_WALLS = 512, HIDDEN_WALLS_SAVED = 1024;
    static final int ALL = THROUGH_WALLS | BREAKS_LAMPS | BREAKS_GLASS | WOUNDS | SMASHES_THROUGH | FACES_THE_VIEW | PAST_TREES | SEEN_AT_ONCE | REWIND
            | HIDDEN_WALLS | HIDDEN_WALLS_SAVED;

    private static volatile int on = ALL & ~REWIND;

    private Rules() {}

    static int rows() {
        return on;
    }

    static void set(int bits) {
        on = bits;
    }

    /** Changes some bits at once: a reload of the file on the config thread and a tick on the options screen may change them together. */
    static synchronized void update(java.util.function.IntUnaryOperator change) {
        on = change.applyAsInt(on);
    }

    /** Whether a first person shot goes on through thin walls and doors: see Penetration. */
    static boolean throughWalls() {
        return !Online.contested() && (on & THROUGH_WALLS) != 0;
    }

    /** Whether a first person shot breaks the lamps it passes and the lamps of the vehicles it lands on: see ShotLights. */
    static boolean breaksLamps() {
        return !Online.joined() && (on & BREAKS_LAMPS) != 0;
    }

    /** Whether a shot breaks the glass of every shut window it crosses: see ShotGlass. */
    static boolean breaksGlass() {
        return !Online.joined() && (on & BREAKS_GLASS) != 0;
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

    /** How a character moved any way but straight ahead in first person faces: see Controls and Sidestep. */
    enum Facing {
        /** It turns to the way it moves, as the game has it. */
        TURNS_TO_THE_WAY_IT_MOVES,
        /** Moved sideways or backwards it is held in the game's aiming stance, facing the view, at the pace the game gives that stance. */
        FACES_THE_VIEW_AT_THE_STANCES_PACE,
        /**
         * Moved sideways or backwards, or ahead and off straight ahead, it is held in the aiming stance
         * facing the view and moved at the pace the game gives the same gait straight ahead (Sidestep).
         */
        FACES_THE_VIEW_AT_THE_PACE_AHEAD;

        /**
         * Whether a character is held in the aiming stance to face the view, moved sideways or
         * backwards ({@code aside}) or ahead but off straight ahead at a pace the stance stands in
         * for ({@code aheadOffStraight}).
         */
        boolean holds(boolean aside, boolean aheadOffStraight) {
            return switch (this) {
                case TURNS_TO_THE_WAY_IT_MOVES -> false;
                case FACES_THE_VIEW_AT_THE_STANCES_PACE -> aside;
                case FACES_THE_VIEW_AT_THE_PACE_AHEAD -> aside || aheadOffStraight;
            };
        }

        /** Whether the stance a character is held in to face the view is raised to the pace ahead: see Hooks.moved. */
        boolean raises() {
            return this == FACES_THE_VIEW_AT_THE_PACE_AHEAD;
        }
    }

    /**
     * The FacesTheView row, but where players can fight the character faces the view whatever the
     * row says, so other players, a mirror and the game's own vision cone see it face where its
     * player looks and never its back. There it goes at the stance's own pace: raised past it, it
     * would back away and strafe faster than any other player facing one way and moving another.
     */
    static Facing facing() {
        if (Online.contested()) {
            return Facing.FACES_THE_VIEW_AT_THE_STANCES_PACE;
        }
        return (on & FACES_THE_VIEW) != 0 ? Facing.FACES_THE_VIEW_AT_THE_PACE_AHEAD : Facing.TURNS_TO_THE_WAY_IT_MOVES;
    }

    /** Whether a first person shot goes on past a tree it does not meet the bole of: see Trunks. */
    static boolean pastTrees() {
        return !Online.contested() && (on & PAST_TREES) != 0;
    }

    /** Whether in the isometric view a zombie shows once the game's test for seeing it passes on the map as it is now: see LiveSight. */
    static boolean seenAtOnce() {
        return !Online.contested() && (on & SEEN_AT_ONCE) != 0;
    }

    /** Whether a player at this screen who dies is asked whether to go back 30 seconds: see Rewind. */
    static boolean rewind() {
        return !Online.joined() && (on & REWIND) != 0;
    }

    /**
     * Whether a room's walls the overhead view never shows are hung with the game's own things the
     * first time it is loaded: see HiddenWalls. That changes the save, so it takes HiddenWallDecorSaved
     * as well as HiddenWallDecor, and a player who turned HiddenWallDecor off before there were two
     * rows still has nothing added. A server's world is the server's to furnish, so a server's own
     * process hangs nothing either.
     */
    static boolean dressesHiddenWalls() {
        return !Online.joined() && !GameServer.server && (on & (HIDDEN_WALLS | HIDDEN_WALLS_SAVED)) == (HIDDEN_WALLS | HIDDEN_WALLS_SAVED);
    }

    /**
     * Whether the first person view draws pictures on the walls of rooms the overhead view never
     * shows: see HiddenWalls.plan. On a server too: nothing is saved or sent, and a picture lies
     * flat on a wall that already hides what is behind it.
     */
    static boolean drawsHiddenWallPictures() {
        return !GameServer.server && (on & HIDDEN_WALLS) != 0;
    }

    /** The rows in words, for the log. */
    static String described(int bits) {
        return "shots through walls " + ((bits & THROUGH_WALLS) != 0) + ", shots break lamps " + ((bits & BREAKS_LAMPS) != 0)
                + ", shots break glass " + ((bits & BREAKS_GLASS) != 0) + ", shots wound limbs " + ((bits & WOUNDS) != 0) + ", vehicles smash through " + ((bits & SMASHES_THROUGH) != 0)
                + ", faces the view walking aside " + ((bits & FACES_THE_VIEW) != 0) + ", shots past trees " + ((bits & PAST_TREES) != 0)
                + ", zombies seen at once " + ((bits & SEEN_AT_ONCE) != 0) + (DevBuild.ON ? ", rewind 30s " + ((bits & REWIND) != 0) : "")
                + ", hidden wall decor " + ((bits & HIDDEN_WALLS) != 0) + ", hidden wall decor saved " + ((bits & HIDDEN_WALLS_SAVED) != 0);
    }
}
