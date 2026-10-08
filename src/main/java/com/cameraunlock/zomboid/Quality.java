package com.cameraunlock.zomboid;

import java.util.Locale;

/**
 * How much the first person picture costs to draw: the Quality row of CameraUnlock.ini. High
 * is the whole picture. Medium and Low draw it with fewer pixels, shorter sight, smaller
 * shadow maps and without the passes that shade corners, light the air, mirror and give
 * surfaces depth, for a graphics card that cannot keep up with the whole of it. A vehicle's
 * mirror shows Low's picture in every mode. Automatic begins at High and steps
 * down while the picture is being drawn slower than {@link #SLOWEST} frames a second.
 *
 * The row is read again while the game runs. The thread that watches the file sets it, the
 * logic thread sets it on its hotkey, reads how far to build and steps Automatic down, and the render
 * thread reads the rest as it draws a frame. set and frame take turns.
 */
final class Quality {
    /** The row's words by number: the number is the word's place in words(), which Config describes the row with. */
    static final int AUTOMATIC = 0, LOW = 1, MEDIUM = 2, HIGH = 3;
    private static final String[] NAMES = {"Automatic", "Low", "Medium", "High"};

    /** Automatic steps down when the picture has been drawn slower than this many frames a second. */
    private static final float SLOWEST = 45f;
    /** How long the frame rate is counted for before it is judged, and how long after a step before counting begins again. */
    private static final long JUDGED_OVER_NANOS = 3_000_000_000L, SETTLES_IN_NANOS = 2_000_000_000L;

    private static final int LOW_SIGHT = 48;

    private static volatile int row = LOW;
    private static volatile int level = LOW;
    private static final long HITCH_NANOS = 250_000_000L;
    private static long countedSince, lastFrame;
    private static int counted;
    /** The views whose frames are being counted. The count is one for all of them: see frame. */
    private static final boolean[] counting = new boolean[View.COUNT];

    private Quality() {}

    static synchronized void set(int asked) {
        if (asked == row) {
            return;
        }
        row = asked;
        level = asked == AUTOMATIC ? HIGH : asked;
        countedSince = 0L;
        Log.info("graphics mode: " + NAMES[asked]);
    }

    /** The mode the hotkey goes to from the one the session is in: Low, Medium, High and round again. Automatic is only ever set in the file. */
    static int next() {
        return row == LOW ? MEDIUM : row == MEDIUM ? HIGH : LOW;
    }

    static String name() {
        return NAMES[row];
    }

    /** The words CameraUnlock.ini holds for the row, in the order of their numbers, with commas between. */
    static String words() {
        return String.join(",", NAMES);
    }

    /**
     * Whether the mode is Low, the nearest there is to the game's own look: the game's own
     * view is then the game's own picture, and the first person view, which the game has no
     * picture of, is drawn with nothing the mod made where the game has its own. Automatic
     * come down to Low's level draws less and is not this.
     */
    static boolean plain() {
        return row == LOW;
    }

    /**
     * Whether the mode is Medium, where grass, trees, bushes and rocks are the game's own
     * sprites: the mod's are the dearest thing in the picture to draw. Automatic come down to
     * Medium's level keeps the mod's.
     */
    static boolean gamesPlants() {
        return row == MEDIUM;
    }

    /**
     * Counts a frame of a view's first person drawn while the game runs. With the row at
     * Automatic, a count that comes out under {@link #SLOWEST} frames a second takes the
     * picture down a level. It is never taken up again in a session: what made the frames slow
     * may be the level left behind, and trying it again would be the stutter back.
     *
     * The game's frame is counted once however many views are drawn in it: the first of the
     * views being counted counts for all.
     */
    static synchronized void frame(int view, long now) {
        counting[view] = true;
        for (int before = 0; before < view; before++) {
            if (counting[before]) {
                return;
            }
        }
        if (row != AUTOMATIC || level == LOW) {
            return;
        }
        if (countedSince == 0L || now < countedSince) {
            if (countedSince == 0L) {
                countedSince = now;
            }
            counted = 0;
            return;
        }
        // One frame that took a quarter of a second is a hitch, a save being written or a jump
        // across the map, and not a picture too heavy for the card: the count begins again.
        boolean hitch = now - lastFrame > HITCH_NANOS;
        lastFrame = now;
        if (hitch) {
            countedSince = now;
            counted = 0;
            return;
        }
        counted++;
        if (now - countedSince < JUDGED_OVER_NANOS) {
            return;
        }
        float rate = counted * 1e9f / (now - countedSince);
        counted = 0;
        countedSince = now;
        if (rate < SLOWEST) {
            level--;
            countedSince = now + SETTLES_IN_NANOS;
            Log.info(String.format(Locale.ROOT, "first person picture quality: Automatic goes down to %s, the picture was drawn %.0f times a second",
                    NAMES[level], rate));
        }
    }

    /** A view's frames are no longer counted: it was off, or the game paused. With none counted the count begins again. */
    static synchronized void rest(int view) {
        counting[view] = false;
        for (boolean other : counting) {
            if (other) {
                return;
            }
        }
        countedSince = 0L;
    }

    /** How many of the picture's pixels there are to each of the window's, each way. */
    static float scale() {
        return level == HIGH ? 1f : level == MEDIUM ? 0.8f : 0.62f;
    }

    /** How many texels a shadow map is across. */
    static int shadowSize() {
        return level == HIGH ? 4096 : 2048;
    }

    /** How far the view is built and drawn, in tiles, where the loaded map reaches that far. */
    static int sight() {
        return level == HIGH ? 80 : level == MEDIUM ? 64 : LOW_SIGHT;
    }

    /**
     * How far a vehicle's mirror shows, in tiles. Its picture is Low's in every mode: it is a
     * second drawing of the world for a strip of glass a hand wide.
     */
    static int sightBehind() {
        return LOW_SIGHT;
    }

    /**
     * How much of the rain on the panes of the viewer's vehicle is drawn: see GlassRain. 0 is
     * drops of one grid, 1 specks among them and trickles down the panes no wiper reaches, 2
     * three sizes of drop and drops that run down and leave a line of wet behind them.
     */
    static int glassRain() {
        return level - LOW;
    }

    /** Whether corners are shaded and lent the light of what stands near, the air is lit, and wet roads, polished floors and glass mirror. */
    static boolean shadedAndLit() {
        return level == HIGH;
    }

    /**
     * Whether the mod's pictures of surfaces, on walls and of skin are made at half their width
     * and height, which is a quarter of what they take on the graphics card and in the PC's memory.
     * Only High draws a dot of picture to a dot of the window and near enough to tell them apart.
     */
    static boolean smallerPictures() {
        return level != HIGH;
    }

    /** Whether near surfaces are given the depth of what they show. */
    static boolean relief() {
        return level == HIGH;
    }

}
