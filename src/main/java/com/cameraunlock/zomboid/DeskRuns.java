package com.cameraunlock.zomboid;

/**
 * The squares of a desk the game lays in an L, or in a run, over a grid it leaves partly empty:
 * which of the mod's desk squares each one is and which way it is turned. Each square is a piece
 * of its own: its share of the top, and under it what the game's picture of that square has, a
 * column at the corner, drawers where the square holds them, a panel leg at a free end and a
 * board along the back. Whoever sits at an L sits inside it, so the back of each arm is on the
 * side away from the other arm. No game types, so the tests can ask it.
 */
final class DeskRuns {
    private DeskRuns() {}

    /** A square's model and its quarter turns, as Models.turn takes them. */
    record Square(String model, int turns) {}

    /** The ways along the map, as x then y: west, east, north, south. */
    private static final int[][] WAYS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    /**
     * @param filled  which squares of the piece's grid the game lays a sprite on, by x then y
     * @param drawers whether this square holds the desk's drawers
     * @param turns   the quarter turns of the way the game says the piece faces, for a run with no corner
     */
    static Square of(boolean[][] filled, int x, int y, boolean drawers, int turns) {
        boolean alongX = at(filled, x - 1, y) || at(filled, x + 1, y), alongY = at(filled, x, y - 1) || at(filled, x, y + 1);
        if (alongX && alongY) {
            // The top's two sides with no square beyond them are the model's +X and +Y.
            int wayX = at(filled, x - 1, y) ? -1 : 1, wayY = at(filled, x, y - 1) ? -1 : 1;
            for (int t = 0; t < 4; t++) {
                if (matches(t, -1, 0, wayX, 0) && matches(t, 0, -1, 0, wayY) || matches(t, -1, 0, 0, wayY) && matches(t, 0, -1, wayX, 0)) {
                    return new Square("desk_corner", t);
                }
            }
            throw new IllegalStateException("no turn puts a desk corner's open sides at " + wayX + ", " + wayY);
        }
        int[] corner = corner(filled);
        int backX, backY;
        if (corner == null) {
            // A run with no corner has its back across the run, on the side the game's facing says or else the north or west.
            float[] back = new float[2];
            Models.turn(turns, 0f, 1f, back);
            int facedX = Math.round(back[0]), facedY = Math.round(back[1]);
            backX = alongX ? 0 : alongY && facedX == 0 ? -1 : facedX;
            backY = alongY ? 0 : alongX && facedY == 0 ? -1 : facedY;
        } else if (alongX) {
            backX = 0;
            backY = -otherArm(filled, corner, false);
        } else {
            backX = -otherArm(filled, corner, true);
            backY = 0;
        }
        int faced = -1;
        for (int t = 0; t < 4 && faced < 0; t++) {
            if (matches(t, 0, 1, backX, backY)) {
                faced = t;
            }
        }
        float[] right = new float[2];
        Models.turn(faced, 1f, 0f, right);
        int rightX = Math.round(right[0]), rightY = Math.round(right[1]);
        boolean rightFree = !at(filled, x + rightX, y + rightY), leftFree = !at(filled, x - rightX, y - rightY);
        String kind = drawers ? "desk_pedestal" : rightFree || leftFree ? "desk_end" : "desk_run";
        return new Square(kind + (leftFree ? "_left" : rightFree ? "_right" : ""), faced);
    }

    private static boolean at(boolean[][] filled, int x, int y) {
        return x >= 0 && y >= 0 && x < filled.length && y < filled[x].length && filled[x][y];
    }

    private static boolean matches(int turns, float modelX, float modelY, int mapX, int mapY) {
        float[] way = new float[2];
        Models.turn(turns, modelX, modelY, way);
        return Math.round(way[0]) == mapX && Math.round(way[1]) == mapY;
    }

    /** The square that has a neighbour along x and one along y, or null for a piece with no corner. */
    private static int[] corner(boolean[][] filled) {
        for (int x = 0; x < filled.length; x++) {
            for (int y = 0; y < filled[x].length; y++) {
                if (filled[x][y] && (at(filled, x - 1, y) || at(filled, x + 1, y)) && (at(filled, x, y - 1) || at(filled, x, y + 1))) {
                    return new int[] {x, y};
                }
            }
        }
        return null;
    }

    /** Which way from the corner the arm along x runs (alongX) or the arm along y, -1 or 1. */
    private static int otherArm(boolean[][] filled, int[] corner, boolean alongX) {
        for (int[] way : WAYS) {
            if ((way[0] != 0) == alongX && at(filled, corner[0] + way[0], corner[1] + way[1])) {
                return alongX ? way[0] : way[1];
            }
        }
        throw new IllegalStateException("a desk's corner with no arm along " + (alongX ? "x" : "y"));
    }
}
