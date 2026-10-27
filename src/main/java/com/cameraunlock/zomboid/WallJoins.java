package com.cameraunlock.zomboid;

/**
 * How the two skins of a wall end where the wall does.
 *
 * A wall is two skins, one each side of the line between two squares, and each stops at the
 * end of that line. Where two walls meet at a corner their four skins then leave a notch on
 * the outside of it, as wide as a wall is thick, and through the notch the eye sees in
 * between the skins to the back of the inside ones, which are the colour of the room: a
 * sliver of wallpaper down the corner of a house. The skin on the outside of the corner is
 * carried on past the end of its wall, half a wall's thickness, to meet the other wall's.
 * A wall that ends with nothing beside it is closed across its end.
 */
final class WallJoins {
    /** The skin on the wall's own square's side, or the one on the other side, runs on past this end to the outside of a corner. */
    static final int NEAR = 1, FAR = 2;
    /** Nothing meets the wall at this end: the end is closed with a face of its own. */
    static final int OPEN = 4;
    /** The ends insideCorners names. */
    static final int START = 1, END = 2;

    private WallJoins() {}

    /**
     * What becomes of one end of a wall.
     *
     * @param runsOn     whether a wall carries on in the same line past the end
     * @param turnsNear  whether a wall leaves the end square to the first, on the side of the wall's own square
     * @param turnsFar   whether one leaves it on the other side
     */
    static int end(boolean runsOn, boolean turnsNear, boolean turnsFar) {
        if (runsOn || turnsNear && turnsFar) {
            return 0;
        }
        return turnsNear ? FAR : turnsFar ? NEAR : OPEN;
    }

    /** Which walls stand where: on the north or on the west edge of a square. */
    interface Walls {
        boolean stands(int x, int y, boolean north);
    }

    /**
     * What becomes of the two ends of the wall on the north or west edge of a square: the
     * end nearer the square's north-west corner in the low three bits, the other in the next three.
     */
    static int of(Walls walls, int x, int y, boolean north) {
        int start, finish;
        if (north) {
            start = end(walls.stands(x - 1, y, true), walls.stands(x, y, false), walls.stands(x, y - 1, false));
            finish = end(walls.stands(x + 1, y, true), walls.stands(x + 1, y, false), walls.stands(x + 1, y - 1, false));
        } else {
            start = end(walls.stands(x, y - 1, false), walls.stands(x, y, true), walls.stands(x - 1, y, true));
            finish = end(walls.stands(x, y + 1, false), walls.stands(x, y + 1, true), walls.stands(x - 1, y + 1, true));
        }
        return start | finish << 3;
    }

    /**
     * Which ends of the wall on the north or west edge of a square a wall leaves on one side: there that
     * side's skin is in the inside of a corner. START for the end nearer the square's north-west corner,
     * END for the other. near is the side of the wall's own square.
     */
    static int insideCorners(Walls walls, int x, int y, boolean north, boolean near) {
        boolean start, finish;
        if (north) {
            start = walls.stands(x, near ? y : y - 1, false);
            finish = walls.stands(x + 1, near ? y : y - 1, false);
        } else {
            start = walls.stands(near ? x : x - 1, y, true);
            finish = walls.stands(near ? x : x - 1, y + 1, true);
        }
        return (start ? START : 0) | (finish ? END : 0);
    }
}
