package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * What the mod made itself and shows in first person in place of the game's own: its weapon
 * models, furniture, wall pictures, plants, surface textures, built windows and doors and the
 * skins of zombies, of the living and of animals, the cloth, leather and hair of what they wear, and fire. Each
 * is a row of [Content] in CameraUnlock.ini, on unless the player says otherwise, and with a
 * row off the picture draws what the game has there, which may be another mod's.
 *
 * The rows are read again while the game runs. The thread that watches the file sets them, the
 * logic thread reads them as it builds the world and the render thread as it draws a frame.
 * What was built with the rows as they were is let go by the logic thread: see taken.
 */
final class Own {
    /** The bits of the rows that are on, in the order Config describes the rows of [Content]. */
    static final int WEAPONS = 1, FURNITURE = 2, PICTURES = 4, PLANTS = 8, TEXTURES = 16, BUILDINGS = 32, ZOMBIES = 64, PEOPLE = 128, ARMS = 256, CLOTHES = 512, ANIMALS = 1024, FIRE = 2048,
            CABINS = 4096;
    static final int ALL = WEAPONS | FURNITURE | PICTURES | PLANTS | TEXTURES | BUILDINGS | ZOMBIES | PEOPLE | ARMS | CLOTHES | ANIMALS | FIRE | CABINS;

    private static volatile int on = ALL;
    /** The rows each view's world was last built with, known to the logic thread alone. */
    private static final int[] built = new int[View.COUNT];

    static {
        Arrays.fill(built, ALL | TEXTURES << 16 | FIRE << 16);
    }

    private Own() {}

    /**
     * Whether the pictures the mod's own surfaces, plants and weapons are drawn from are on the
     * graphics card. They are made only once a mode shows them (Pipeline.CONTENT), and until
     * they are, only the rows WITHOUT_PICTURES are shown: what was built without the rest is
     * built again when they arrive. Set by the render thread.
     */
    private static volatile boolean picturesMade;

    /**
     * The rows drawn from nothing but the core of the picture: the furniture's models and the
     * windows and doors the mod builds are coloured from the game's own sprites and grained with
     * the surfaces the core makes. So they are shown in Low, where the game's flat sprites of
     * them are the worst of its look from the eye, and before the rest of the mod's own is made.
     */
    static final int WITHOUT_PICTURES = FURNITURE | BUILDINGS;

    /** The rows that are on. In the Low graphics mode only those WITHOUT_PICTURES are, and in Medium the plants are the game's, whatever the file says. The arms go by their row alone: see arms. */
    static int on() {
        return Quality.plain() || !picturesMade ? on & WITHOUT_PICTURES : Quality.gamesPlants() ? on & ~PLANTS : on;
    }

    /** The rows as the file has them, whatever the graphics mode shows of them. */
    static int rows() {
        return on;
    }

    static void picturesMade(boolean made) {
        picturesMade = made;
    }

    static void set(int bits) {
        on = bits;
    }

    static boolean weapons() {
        return (on() & WEAPONS) != 0;
    }

    static boolean furniture() {
        return (on() & FURNITURE) != 0;
    }

    static boolean pictures() {
        return (on() & PICTURES) != 0;
    }

    static boolean plants() {
        return (on() & PLANTS) != 0;
    }

    static boolean textures() {
        return (on() & TEXTURES) != 0;
    }

    static boolean buildings() {
        return (on() & BUILDINGS) != 0;
    }

    static boolean zombies() {
        return (on() & ZOMBIES) != 0;
    }

    /**
     * Whether the arms on a held weapon are the mod's own: the row as the file has it, in every
     * graphics mode, Low included. The game's own arms are posed for the game's view from above,
     * and moved with the weapon to before the eye they hang in the middle of the picture. The arms
     * are made with the core of the picture, not with the rest of the mod's own: see Pipeline.
     */
    static boolean arms() {
        return (on & ARMS) != 0;
    }

    static boolean people() {
        return (on() & PEOPLE) != 0;
    }

    /**
     * Whether the interior of a vehicle sat in is the mod's own model of it: the row as the file
     * has it, in every graphics mode. Off, the game's own is drawn, its glass cut away.
     */
    static boolean cabins() {
        return (on & CABINS) != 0;
    }

    /**
     * Whether the lines painted on roads are laid as paint: see RoadPaint. They are the game's
     * own markings in the game's own colours and need no picture of the mod's, so outside Low
     * it is the row as the file has it that says, whether or not the pictures are made yet.
     * Low draws the game's sprites of them.
     */
    static boolean paint() {
        return !Quality.plain() && (on & TEXTURES) != 0;
    }

    /**
     * Whether the picture draws fire of its own, flames, smoke and their light (Fires), in place
     * of the game's sprites of flames. Fire needs no picture of the mod's, so outside Low it is the
     * row as the file has it that says. Low draws the game's flames on hearths and none on a burning square.
     */
    static boolean fire() {
        return !Quality.plain() && (on & FIRE) != 0;
    }

    /**
     * Whether a row has changed since a view's world was last built, asked by the logic thread
     * before it builds: true once for each change, and whatever that view built before it is
     * then to be built again.
     */
    static boolean taken(int view) {
        // The rows the mode shows, and over them the two rows that are asked as the file has them: see paint and fire.
        int now = on() | (paint() ? TEXTURES << 16 : 0) | (fire() ? FIRE << 16 : 0);
        if (now == built[view]) {
            return false;
        }
        built[view] = now;
        return true;
    }

    /** The rows in words, for the log. */
    static String described(int bits) {
        return "weapons " + ((bits & WEAPONS) != 0) + ", furniture " + ((bits & FURNITURE) != 0) + ", pictures " + ((bits & PICTURES) != 0)
                + ", plants " + ((bits & PLANTS) != 0) + ", textures " + ((bits & TEXTURES) != 0) + ", buildings " + ((bits & BUILDINGS) != 0)
                + ", zombies " + ((bits & ZOMBIES) != 0) + ", people " + ((bits & PEOPLE) != 0) + ", arms " + ((bits & ARMS) != 0)
                + ", clothes " + ((bits & CLOTHES) != 0) + ", animals " + ((bits & ANIMALS) != 0) + ", fire " + ((bits & FIRE) != 0)
                + ", interiors " + ((bits & CABINS) != 0);
    }
}
