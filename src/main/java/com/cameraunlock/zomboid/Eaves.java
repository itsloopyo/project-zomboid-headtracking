package com.cameraunlock.zomboid;

/**
 * How an eave ends along its run.
 *
 * An eave is made a square at a time: the roof's slope carried out past the wall, a board
 * down its edge and a soffit under it. Each square's length of it stopped at the square's
 * sides, so at the corner of a hipped roof the two eaves that meet there left a square notch
 * between them the size of the overhang, and at a gable the eave stopped at the wall where
 * the roof beside it runs on over the wall: in both the end of the hollow under the eave
 * was open to the eye.
 */
final class Eaves {
    /** The eave goes on in the next square. */
    static final int RUNS_ON = 0;
    /** The roof stops here: the eave runs on as far as the roof's edge over the gable does, and is closed across its end. */
    static final int GABLE = 1;
    /** Another eave of the same tile meets this one at the tile's corner: both are cut on the slant to it. */
    static final int MEETS = 2;

    private Eaves() {}

    /** How one end of an eave's run ends, from whether another eave meets it there and whether there is roof beside it. */
    static int end(boolean meets, boolean roofBeside) {
        return meets ? MEETS : roofBeside ? RUNS_ON : GABLE;
    }

    /** How far past its square the eave runs along the wall, in tiles, for an end of that kind. */
    static float atWall(int end, float rake) {
        return end == GABLE ? rake : 0f;
    }

    /**
     * How far past its square the eave's outer edge runs. Where two meet, each goes as far as
     * the other stands out, so their edges meet on the corner's diagonal.
     */
    static float atEdge(int end, float eave, float rake) {
        return end == GABLE ? rake : end == MEETS ? eave : 0f;
    }

    /** What is on a square past an eave at some level: nothing, a floor out of doors, or a room or a roof. */
    static final int AIR = 0, FLOOR = 1, ROOM = 2;
    /** The shortest pipe worth hanging, in tiles, how high its shoe is and how far out it turns. */
    static final float SHORTEST_PIPE = 0.6f, SHOE_HIGH = 0.16f, SHOE_OUT = 0.12f;

    /** What is on the square past the eave at a level. */
    interface Under {
        int at(int level);
    }

    /** Whether a wall stands on a level in the line under the eave, at the end of its run where the pipe goes. */
    interface Walled {
        boolean at(int level);
    }

    /**
     * How high above the ground's own level the foot of a gutter's pipe stands, in tiles, for
     * an eave at level z: on the ground, or on a floor out of doors it comes to on the way, a
     * balcony or a lower flat roof. NaN where there is no pipe:
     * - where it would have to come down through a room or a roof: an eave round a skylight in
     *   the middle of a roof has a room under every side of it, and a pipe from it stood from
     *   floor to ceiling in the middle of that room;
     * - where there is no wall under the eave to carry it, on any level from the one the eave
     *   hangs at the top of (the level below a roof's, or a roof on the ground level's own) down
     *   to the pipe's foot: the open front of a porch or an awning has none, and a pipe hung
     *   there stood alone in front of it like a post with its foot turned out.
     */
    static float pipeFoot(int z, Under under, Walled walled) {
        int foot = 0;
        for (int level = z; level >= 0; level--) {
            int found = under.at(level);
            if (found == ROOM) {
                return Float.NaN;
            }
            if (found == FLOOR && level > 0) {
                foot = level;
                break;
            }
        }
        for (int level = foot; level <= Math.max(z - 1, 0); level++) {
            if (!walled.at(level)) {
                return Float.NaN;
            }
        }
        return foot * ViewCamera.LEVEL_HEIGHT;
    }
}
