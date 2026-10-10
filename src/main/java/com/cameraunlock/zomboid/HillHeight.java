package com.cameraunlock.zomboid;

/**
 * How high the hills of the first person picture stand, as a share of the height the mod gives
 * them: HillHeight in CameraUnlock.ini, 0 for land as level as the game's map, 1 by default.
 * Set by the thread that watches the file or by the options screen, and read by the logic
 * thread, which raises the land to it (WorldScene.build, Terrain.heighten).
 */
final class HillHeight {
    /** The row's default and range in the config table (Config). */
    static final float DEFAULT = 1f, MOST = 1.5f;

    private static volatile float asked = DEFAULT;

    private HillHeight() {}

    /** Takes effect on the next frame built. As cameraunlock-core read it, which holds it to the row's range. */
    static void set(float gain) {
        asked = gain;
    }

    static float asked() {
        return asked;
    }

    /**
     * The height the land is drawn at. Where players can fight, the land is at the height
     * everyone with the mod has unless they move it: flatter land would show a player a crest
     * hides from the others, and the land is never a thing one player sets for another.
     */
    static float drawn() {
        return Online.contested() ? DEFAULT : asked;
    }
}
