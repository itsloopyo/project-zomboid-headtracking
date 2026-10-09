package com.cameraunlock.zomboid;

import java.util.Locale;

/**
 * How much the first person picture costs to draw: the Quality row of CameraUnlock.ini. High
 * is the whole picture. Medium and Low draw it with fewer pixels, shorter sight, smaller
 * shadow maps and without the passes that shade corners, light the air, mirror and give
 * surfaces depth, for a graphics card that cannot keep up with the whole of it. A vehicle's
 * mirror shows Low's picture in every mode where it shows one. Automatic begins at High and steps
 * down while the picture is being drawn slower than {@link #SLOWEST} frames a second. Custom
 * draws the picture the rows of [Graphics] describe, a row for each thing the other modes
 * decide (Config).
 *
 * The rows are read again while the game runs. The thread that watches the file sets them, the
 * logic thread sets them on its hotkey and from the game's options screen, reads how far to build
 * and steps Automatic down, and the render thread reads the rest as it draws a frame. set, custom
 * and frame take turns.
 */
final class Quality {
    /** The row's words by number: the number is the word's place in words(), which Config describes the row with. */
    static final int AUTOMATIC = 0, LOW = 1, MEDIUM = 2, HIGH = 3, CUSTOM = 4;
    private static final String[] NAMES = {"Automatic", "Low", "Medium", "High", "Custom"};

    /**
     * What a mode draws. Low, Medium and High are each one of these, Automatic is High's, Medium's
     * or Low's with neither of the first two, and Custom is what the rows of [Graphics] hold.
     *
     * @param stockLook        see plain
     * @param spritePlants     see gamesPlants
     * @param renderScale      see scale
     * @param sharpShadows     shadow maps 4096 texels across, where they are otherwise 2048
     * @param viewDistance     see sight
     * @param drawDistance     how far off people, zombies, vehicles and the furniture the mod has models of are drawn, in tiles: see DrawDistance
     * @param drivingDrawDistance the same ahead of a vehicle the viewer rides in
     * @param rainOnGlass      see glassRain
     * @param shadingAndLight  see shadedAndLit
     * @param reflections      see reflections and surfacesMirror
     * @param fullSizeTextures the opposite of smallerPictures
     * @param farTrees         see crownsToSight
     * @param surfaceDepth     see relief
     */
    record Picture(boolean stockLook, boolean spritePlants, float renderScale, boolean sharpShadows, int viewDistance, int drawDistance,
            int drivingDrawDistance, int rainOnGlass, boolean shadingAndLight, boolean reflections, boolean fullSizeTextures, boolean farTrees,
            boolean surfaceDepth) {}

    static final Picture LOW_PICTURE = new Picture(true, false, 0.62f, false, 48, 48, 48, 0, false, true, false, false, false);
    static final Picture MEDIUM_PICTURE = new Picture(false, true, 0.8f, false, 64, 64, 64, 1, false, true, false, false, false);
    static final Picture HIGH_PICTURE = new Picture(false, false, 1f, true, 80, 80, 80, 2, true, true, true, true, true);
    /** Automatic at each level: the level's picture, still with everything the mod made. */
    private static final Picture[] AUTOMATIC_AT = {null, automatic(LOW_PICTURE), automatic(MEDIUM_PICTURE), HIGH_PICTURE};

    /** Automatic steps down when the picture has been drawn slower than this many frames a second. */
    private static final float SLOWEST = 45f;
    /** How long the frame rate is counted for before it is judged, and how long after a step before counting begins again. */
    private static final long JUDGED_OVER_NANOS = 3_000_000_000L, SETTLES_IN_NANOS = 2_000_000_000L;

    private static volatile int row = LOW;
    private static volatile int level = LOW;
    /** What Custom draws: the rows of [Graphics] as the session last took them. */
    private static volatile Picture custom = HIGH_PICTURE;
    /** What the mode draws now. */
    private static volatile Picture drawn = LOW_PICTURE;
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
        level = asked == AUTOMATIC || asked == CUSTOM ? HIGH : asked;
        countedSince = 0L;
        drawn = asked == CUSTOM ? custom : asked == AUTOMATIC ? AUTOMATIC_AT[level] : preset(asked);
        Log.info("graphics mode: " + NAMES[asked] + (asked == CUSTOM ? ", " + described(custom) : ""));
    }

    /** Takes what Custom draws. Low, Medium and High leave it as it is, for the next time the mode is Custom. */
    static synchronized void custom(Picture picture) {
        if (picture.equals(custom)) {
            return;
        }
        custom = picture;
        if (row == CUSTOM) {
            drawn = picture;
            Log.info("graphics mode: Custom, " + described(picture));
        }
    }

    static Picture custom() {
        return custom;
    }

    /** What the mode draws now: Custom's rows in Custom, and in the other modes the mode's picture, or that of the level Automatic is at. */
    static Picture drawn() {
        return drawn;
    }

    static Picture preset(int mode) {
        return switch (mode) {
            case LOW -> LOW_PICTURE;
            case MEDIUM -> MEDIUM_PICTURE;
            case HIGH -> HIGH_PICTURE;
            default -> throw new IllegalArgumentException("graphics mode " + mode + " is not one of Low, Medium and High");
        };
    }

    private static Picture automatic(Picture at) {
        return new Picture(false, false, at.renderScale, at.sharpShadows, at.viewDistance, at.drawDistance, at.drivingDrawDistance, at.rainOnGlass,
                at.shadingAndLight, at.reflections, at.fullSizeTextures, at.farTrees, at.surfaceDepth);
    }

    static String described(Picture picture) {
        return String.format(Locale.ROOT, "stock look %b, sprite plants %b, render scale %.2f, sharp shadows %b, view distance %d, "
                        + "zombies and props draw distance %d, forward driving draw distance %d, rain on glass %d, shading and light %b, reflections %b, "
                        + "full size textures %b, far trees %b, surface depth %b",
                picture.stockLook, picture.spritePlants, picture.renderScale, picture.sharpShadows, picture.viewDistance, picture.drawDistance,
                picture.drivingDrawDistance, picture.rainOnGlass, picture.shadingAndLight, picture.reflections, picture.fullSizeTextures, picture.farTrees,
                picture.surfaceDepth);
    }

    /** The mode the hotkey goes to from the one the session is in: Low, Medium, High, Custom and round again. Automatic is only ever set in the file. */
    static int next() {
        return row == LOW ? MEDIUM : row == MEDIUM ? HIGH : row == HIGH ? CUSTOM : LOW;
    }

    static int row() {
        return row;
    }

    static String name() {
        return NAMES[row];
    }

    /** The words CameraUnlock.ini holds for the row, in the order of their numbers, with commas between. */
    static String words() {
        return String.join(",", NAMES);
    }

    /**
     * Whether the picture is Low's, the nearest there is to the game's own look, or Custom's
     * with StockLook: the game's own view is then the game's own picture, and the first person
     * view, which the game has no picture of, is drawn with nothing the mod made where the game
     * has its own. Automatic come down to Low's level draws less and is not this.
     */
    static boolean plain() {
        return drawn.stockLook;
    }

    /**
     * Whether the picture is Medium's, or Custom's with SpritePlants, where grass, trees, bushes
     * and rocks are the game's own sprites: the mod's are the dearest thing in the picture to
     * draw. Automatic come down to Medium's level keeps the mod's.
     */
    static boolean gamesPlants() {
        return drawn.spritePlants;
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
            drawn = AUTOMATIC_AT[level];
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
        return drawn.renderScale;
    }

    /** How many texels a shadow map is across. */
    static int shadowSize() {
        return drawn.sharpShadows ? 4096 : 2048;
    }

    /** How far the view is built and drawn, in tiles, where the loaded map reaches that far. */
    static int sight() {
        return drawn.viewDistance;
    }

    /**
     * How far a vehicle's mirror shows, in tiles. Its picture is Low's in every mode: it is a
     * second drawing of the world for a strip of glass a hand wide.
     */
    static int sightBehind() {
        return LOW_PICTURE.viewDistance;
    }

    /**
     * How much of the rain on the panes of the viewer's vehicle is drawn: see GlassRain. 0 is
     * drops of one grid, 1 specks among them and trickles down the panes no wiper reaches, 2
     * three sizes of drop and drops that run down and leave a line of wet behind them.
     */
    static int glassRain() {
        return drawn.rainOnGlass;
    }

    /** Whether corners are shaded and lent the light of what stands near, and the air is lit. */
    static boolean shadedAndLit() {
        return drawn.shadingAndLight;
    }

    /**
     * Whether anything mirrors at all. Without, a vehicle's mirrors are plain glass and their
     * picture of the road behind is not drawn, and nothing else mirrors either.
     */
    static boolean reflections() {
        return drawn.reflections;
    }

    /**
     * Whether wet roads, polished floors, glass and water mirror the world, and a window shows
     * the viewer in it. Only with the shading and light: Low and Medium draw neither and keep a
     * vehicle's mirrors, and the one row covers both kinds of mirror.
     */
    static boolean surfacesMirror() {
        return drawn.reflections && drawn.shadingAndLight;
    }

    /**
     * Whether the mod's pictures of surfaces, on walls and of skin are made at half their width
     * and height, which is a quarter of what they take on the graphics card and in the PC's memory.
     * Only High draws a dot of picture to a dot of the window and near enough to tell them apart.
     */
    static boolean smallerPictures() {
        return !drawn.fullSizeTextures;
    }

    /**
     * Whether a tree or a bush is the mod's own, a crown of sprigs on a round bole, as far as the
     * view is built. Below High one further off than Crowns.REACH is the game's sprite
     * on a fan of cards, which costs less to build and draw and changes to a crown as the eye nears it.
     */
    static boolean crownsToSight() {
        return drawn.farTrees;
    }

    /** Whether near surfaces are given the depth of what they show. */
    static boolean relief() {
        return drawn.surfaceDepth;
    }

}
