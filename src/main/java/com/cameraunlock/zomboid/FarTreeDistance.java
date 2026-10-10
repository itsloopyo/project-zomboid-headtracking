package com.cameraunlock.zomboid;

/**
 * How far from the eye the trees beyond the loaded map stand in the first person picture, in
 * tiles: FarTreeDistance in CameraUnlock.ini, 0 for none. Set by the thread that watches the
 * file or by the options screen, and read by the render thread (Pipeline.drawFarTrees). Not a
 * row of [Graphics]: changing it leaves the graphics mode as it is. The far trees stand only
 * where the game has no map loaded, where nobody is drawn, so they hide no one from anyone.
 */
final class FarTreeDistance {
    /** The row's default and range in the config table (Config): the far land reaches no further than MOST. */
    static final int DEFAULT = 400, MOST = (int) FarLand.REACH;

    private static volatile int asked = DEFAULT;

    private FarTreeDistance() {}

    /** Takes effect on the next frame drawn. As cameraunlock-core read it, which holds it to the row's range. */
    static void set(int tiles) {
        asked = tiles;
    }

    static int asked() {
        return asked;
    }
}
