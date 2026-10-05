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
}
