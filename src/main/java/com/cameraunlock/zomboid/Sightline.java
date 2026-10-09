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
 * shut door, a drawn curtain. So everything in a room is built whether or not the game has
 * shown the room, ahead of any line to it, and the picture's own walls and doors hide it
 * until the line opens: a door swings, a corner is turned, the head leans round a frame.
 * That holds within 40 tiles of the eye (WorldScene.UNSHOWN_FILLED_WITHIN): see leftEmpty.
 *
 * Where players can fight, a room is filled only as the game shows it to everyone.
 */
final class Sightline {
    private Sightline() {}

    /**
     * Whether what stands in a room is left out of a build of the room's square.
     *
     * @param asTheGameShows the viewer is shown no more than the game shows everyone: see Online.contested
     * @param gamesView      the build is of the game's own view, where a room not shown has the engine's light, which is none
     * @param inRoom         the square is in a room
     * @param shown          the game has had the square in the character's sight
     */
    static boolean leftOut(boolean asTheGameShows, boolean gamesView, boolean inRoom, boolean shown) {
        return asTheGameShows && !gamesView && inRoom && !shown;
    }

    /**
     * Whether what stands on a square is left out of the build: in a room shown as the game shows
     * it (leftOut), and in a room the game has not shown when the chunk is further from the eye
     * than such rooms are filled. Every room of a town is one the game has not shown, and
     * filling all of them to the end of sight is building every house's contents in view; past
     * that distance a room stands empty and is filled as its chunk comes nearer.
     *
     * @param leftOut     what leftOut answered for the square
     * @param unshownRoom the square is in a room the game has not shown, in a frame from the eye
     * @param near        the chunk is near enough for such a room to be filled
     */
    static boolean leftEmpty(boolean leftOut, boolean unshownRoom, boolean near) {
        return leftOut || unshownRoom && !near;
    }

    /**
     * The step the light given to rooms the game has not shown moves in. That light is part of
     * what every build goes by, so each move of it has every chunk in view built again: a
     * sixteenth is seldom crossed, and is less than the eye tells one room's light from the next by.
     */
    static final float LIGHT_STEP = 1f / 16f;

    /**
     * A light as it is kept: the one kept before while the light found is within a step of
     * it, and otherwise the light found, on the nearest step.
     *
     * @param kept the light kept before, or less than 0 for none
     */
    static float stepped(float found, float kept) {
        return kept >= 0f && Math.abs(found - kept) < LIGHT_STEP ? kept : Math.round(found / LIGHT_STEP) * LIGHT_STEP;
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
