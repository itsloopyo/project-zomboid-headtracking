package com.cameraunlock.zomboid;

/**
 * What the eye has a line to, and what the picture leaves out of a room.
 *
 * The game looks down on its map and shows a room only once the character has had it in
 * sight, which it works out square by square from where the character stands, some frames
 * after the character got there. A build that goes by that has a room empty as its door
 * opens and fills it with the viewer inside.
 *
 * From the eye what hides a thing is what stands between the two in the picture: a wall, a
 * shut door, a drawn curtain. So what is in a room is built whether or not the game has shown
 * the room, ahead of any line to it: a room the eye could see into from about where it is,
 * whichever way it turned, or through a door near it once opened, and the picture draws it
 * while a line of sight reaches into it (RoomSight). A frame drawn from elsewhere, which does
 * not go by sight, builds every room within 40 tiles of the eye (WorldScene.UNSHOWN_FILLED_WITHIN):
 * see emptied.
 */
final class Sightline {
    private Sightline() {}

    /**
     * Whether a room is filled only as the game shows it: in Low, which shows everything the
     * game's way, and with the development switch that turns the line of sight off.
     */
    static boolean asTheGameShows(boolean switchedOff) {
        return switchedOff || Quality.plain();
    }

    /** Whether the chunk the view does not face is built ahead of a turn: with the development switch left alone, and out of Low. */
    static boolean buildsAhead(boolean switchedOff) {
        return !switchedOff && !Quality.plain();
    }

    /**
     * Whether what stands in a room is left out of a build of the room's square.
     *
     * @param asTheGameShows the viewer is shown no more than the game shows: see asTheGameShows(boolean)
     * @param gamesView      the build is of the game's own view, where a room not shown has the engine's light, which is none
     * @param inRoom         the square is in a room
     * @param shown          the game has had the square in the character's sight
     */
    static boolean leftOut(boolean asTheGameShows, boolean gamesView, boolean inRoom, boolean shown) {
        return asTheGameShows && !gamesView && inRoom && !shown;
    }

    /** What emptied answers: what stands on the square is built, left out, or left out only when its chunk is far from the eye. */
    static final int KEPT = 0, LEFT_OUT = 1, LEFT_OUT_FAR = 2;

    /**
     * Whether what stands on a square is left out of the build: in a room shown as the game shows
     * it (leftOut), and in a room the game has not shown, from the eye, when the chunk is further
     * from the eye than such rooms are filled. Every room of a town is one the game has not
     * shown, and filling all of them to the end of sight is building every house's contents in
     * view; past that distance a room stands empty and is filled as its chunk comes nearer.
     * The arguments are leftOut's.
     */
    static int emptied(boolean asTheGameShows, boolean gamesView, boolean inRoom, boolean shown) {
        if (leftOut(asTheGameShows, gamesView, inRoom, shown)) {
            return LEFT_OUT;
        }
        return !gamesView && inRoom && !shown ? LEFT_OUT_FAR : KEPT;
    }

    /**
     * The step the light given to rooms the game has not shown moves in. That light is part of
     * what every build goes by, so each move of it has every chunk in view built again. A
     * sixteenth is less than the eye tells one room's light from the next by.
     */
    static final float LIGHT_STEP = 1f / 16f;

    /**
     * How much of each new reading of the rooms' light is taken (followed). The rooms in sight
     * change as the eye goes past houses, and from a car what they come to swung from a half
     * to an eighth and back within two seconds while the light did not change: each swing had
     * every chunk in view built again. Taken a share at a time it follows a change in the light
     * in a few seconds and the houses going by hardly at all.
     */
    static final float LIGHT_FOLLOWED = 0.15f;
    /** How far what is followed has to be from the light kept, in steps, for the kept light to move: the light kept is never further off than this. */
    static final float LIGHT_HELD_STEPS = 2f;

    /** A reading followed a share of the way from what was followed before, or the reading itself where nothing was. */
    static float followed(float found, float before) {
        return before < 0f ? found : before + (found - before) * LIGHT_FOLLOWED;
    }

    /**
     * The light kept from what is followed: as it was while that is within LIGHT_HELD_STEPS of
     * it, and otherwise that, on the nearest step.
     *
     * @param kept the light kept before, or less than 0 for none
     */
    static float held(float followed, float kept) {
        return kept >= 0f && Math.abs(followed - kept) < LIGHT_HELD_STEPS * LIGHT_STEP ? kept : Math.round(followed / LIGHT_STEP) * LIGHT_STEP;
    }

    /**
     * Whether a straight line along the map from one point to another crosses no edge that
     * hides what is behind it. A line that ends in the square it starts in is clear.
     */
    static boolean clear(GridTrace trace, GridTrace.Edges hides, float fromX, float fromY, float toX, float toY) {
        float alongX = toX - fromX, alongY = toY - fromY;
        float length = (float) Math.sqrt(alongX * alongX + alongY * alongY);
        if (length < 1e-6f) {
            return true;
        }
        return trace.firstBlockedEdge(hides, fromX, fromY, alongX / length, alongY / length, length) == Float.POSITIVE_INFINITY;
    }
}
