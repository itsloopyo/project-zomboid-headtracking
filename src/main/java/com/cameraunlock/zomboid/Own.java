package com.cameraunlock.zomboid;

/**
 * What the mod made itself and shows in first person in place of the game's own: its weapon
 * models, furniture, wall pictures, plants, surface textures and built windows and doors. Each
 * is a row of [Content] in CameraUnlock.ini, on unless the player says otherwise, and with a
 * row off the picture draws what the game has there, which may be another mod's.
 *
 * The rows are read again while the game runs. The thread that watches the file sets them, the
 * logic thread reads them as it builds the world and the render thread as it draws a frame.
 * What was built with the rows as they were is let go by the logic thread: see taken.
 */
final class Own {
    /** The bits of the rows that are on, as the mod's DLL hands them over (native/src/bridge.h holds the same numbers). */
    static final int WEAPONS = 1, FURNITURE = 2, PICTURES = 4, PLANTS = 8, TEXTURES = 16, BUILDINGS = 32;
    static final int ALL = WEAPONS | FURNITURE | PICTURES | PLANTS | TEXTURES | BUILDINGS;

    private static volatile int on = ALL;
    /** The rows the world was last built with, known to the logic thread alone. */
    private static int built = ALL;

    private Own() {}

    /** The rows that are on, as bits. */
    static int on() {
        return on;
    }

    static void set(int bits) {
        on = bits;
    }

    static boolean weapons() {
        return (on & WEAPONS) != 0;
    }

    static boolean furniture() {
        return (on & FURNITURE) != 0;
    }

    static boolean pictures() {
        return (on & PICTURES) != 0;
    }

    static boolean plants() {
        return (on & PLANTS) != 0;
    }

    static boolean textures() {
        return (on & TEXTURES) != 0;
    }

    static boolean buildings() {
        return (on & BUILDINGS) != 0;
    }

    /**
     * Whether a row has changed since the world was last built, asked by the logic thread
     * before it builds: true once for each change, and whatever was built before it is then
     * to be built again.
     */
    static boolean taken() {
        int now = on;
        if (now == built) {
            return false;
        }
        built = now;
        return true;
    }

    /** The rows in words, for the log. */
    static String described(int bits) {
        return "weapons " + ((bits & WEAPONS) != 0) + ", furniture " + ((bits & FURNITURE) != 0) + ", pictures " + ((bits & PICTURES) != 0)
                + ", plants " + ((bits & PLANTS) != 0) + ", textures " + ((bits & TEXTURES) != 0) + ", buildings " + ((bits & BUILDINGS) != 0);
    }
}
