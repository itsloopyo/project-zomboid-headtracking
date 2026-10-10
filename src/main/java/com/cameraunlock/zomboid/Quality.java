package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Locale;

/**
 * How much the first person picture costs to draw: the Quality row of CameraUnlock.ini. High
 * is the whole picture. Medium and Low draw it with fewer pixels, shorter sight, smaller
 * shadow maps and without the passes that shade corners, light the air, mirror and give
 * surfaces depth, for a graphics card that cannot keep up with the whole of it. A vehicle's
 * mirror shows Low's picture in every mode where it shows one. Automatic, which a first start
 * is in, begins at High, steps down while the picture is drawn too slowly and back up while it
 * is drawn with room to spare: see frame. Custom draws the picture the rows of [Graphics]
 * describe, a row for each thing the other modes decide (Config).
 *
 * The rows are read again while the game runs. The thread that watches the file sets them, the
 * logic thread sets them on its hotkey and from the game's options screen, reads how far to build
 * and steps Automatic, and the render thread reads the rest as it draws a frame. set, custom
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
    /**
     * Automatic's levels, cheapest first. STOCK is the Low mode's picture, the game's own look;
     * the three above it are Low's, Medium's and High's cost, still with everything the mod made.
     */
    static final int STOCK = 0;
    private static final String[] LEVELS = {"Stock", "Low", "Medium", "High"};
    private static final Picture[] AUTOMATIC_AT = {LOW_PICTURE, automatic(LOW_PICTURE), automatic(MEDIUM_PICTURE), HIGH_PICTURE};

    /**
     * Automatic steps down when the picture has been drawn slower than SLOWEST frames a second,
     * and up when it has been drawn at RISES_AT or faster for RISES_AFTER_NANOS. Under a frame limit
     * the game was set to, the two are at most SLOWEST_OF_LIMIT and RISES_AT_OF_LIMIT of the limit.
     */
    private static final float SLOWEST = 45f, RISES_AT = 57f, SLOWEST_OF_LIMIT = 0.8f, RISES_AT_OF_LIMIT = 0.95f;
    /** How long the frame rate is counted for before it is judged, and how long after a step down or up before counting begins again. */
    private static final long JUDGED_OVER_NANOS = 3_000_000_000L, SETTLES_IN_NANOS = 2_000_000_000L, SETTLES_UP_NANOS = 5_000_000_000L;
    private static final long RISES_AFTER_NANOS = 15_000_000_000L;
    /**
     * A level stepped down from is not stepped up into again for BARRED_NANOS, doubled for each time
     * a step up into it had to be taken back within TRIAL_NANOS, at most MOST_DOUBLINGS times.
     */
    private static final long BARRED_NANOS = 120_000_000_000L, TRIAL_NANOS = 60_000_000_000L;
    private static final int MOST_DOUBLINGS = 4;

    private static volatile int row = LOW;
    /** The level Automatic is at. */
    private static volatile int level = HIGH;
    /** What Custom draws: the rows of [Graphics] as the session last took them. */
    private static volatile Picture custom = HIGH_PICTURE;
    /** What the mode draws now. */
    private static volatile Picture drawn = LOW_PICTURE;
    /** A frame longer than HITCH_NANOS is long; one longer than STALL_NANOS and no longer than that is stalled. */
    private static final long HITCH_NANOS = 250_000_000L, STALL_NANOS = 100_000_000L;
    /** When the count began (0 for none begun), when the frame before was drawn, and when the level last stepped to has settled. */
    private static long countedSince, lastFrame, settlesAt;
    /** The frames of the count, how many of them were long and how many stalled, and the time each kind took. */
    private static int counted, longFrames, stalledFrames;
    private static long longNanos, stalledNanos;
    /** How long the frame rate has been judged at RISES_AT or over without a break. */
    private static long fastFor;
    /** By level: when it may be stepped up into again, when it was last stepped up into (0 for never), and how many of those steps were taken back. */
    private static final long[] barredUntil = new long[LEVELS.length], cameUpAt = new long[LEVELS.length];
    private static final int[] failures = new int[LEVELS.length];
    /** Whether Automatic has stepped since the screen last said so. */
    private static boolean stepUnsaid;
    /** The views whose frames are being counted. The count is one for all of them: see frame. */
    private static final boolean[] counting = new boolean[View.COUNT];

    private Quality() {}

    static synchronized void set(int asked) {
        if (asked == row) {
            return;
        }
        row = asked;
        level = HIGH;
        countedSince = 0L;
        settlesAt = 0L;
        fastFor = 0L;
        Arrays.fill(barredUntil, 0L);
        Arrays.fill(cameUpAt, 0L);
        Arrays.fill(failures, 0);
        stepUnsaid = false;
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

    /**
     * The picture a row of [Graphics] changed on the options screen starts Custom from, the change
     * then made to it: what the mode draws, and in Automatic what the level it is at draws, but
     * for STOCK, which starts it from the level above, Low's cost with what the mod made. Stock is
     * how Automatic saves the graphics card, not a look the player chose.
     */
    static synchronized Picture customFrom() {
        return row == AUTOMATIC && level == STOCK ? AUTOMATIC_AT[STOCK + 1] : drawn;
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

    /** The mode the hotkey goes to from the one the session is in: Automatic, Low, Medium, High, Custom and round again. */
    static int next() {
        return (row + 1) % NAMES.length;
    }

    static int row() {
        return row;
    }

    /** The mode, and in Automatic the level it is at: "Automatic (Medium)". */
    static String name() {
        return choice(row);
    }

    /** How the options screen's list names a mode: Automatic, while the session is in it, with the level it is at. */
    static String choice(int mode) {
        return mode == AUTOMATIC && row == AUTOMATIC ? NAMES[AUTOMATIC] + " (" + LEVELS[level] + ")" : NAMES[mode];
    }

    /** Whether Automatic has stepped since the screen last said the mode, which it then says. Logic thread. */
    static synchronized boolean stepToSay() {
        return stepUnsaid;
    }

    static synchronized void stepSaid() {
        stepUnsaid = false;
    }

    /** The words CameraUnlock.ini holds for the row, in the order of their numbers, with commas between. */
    static String words() {
        return String.join(",", NAMES);
    }

    /**
     * Whether the picture is Low's, the nearest there is to the game's own look, Custom's with
     * StockLook, or Automatic's at STOCK: the game's own view is then the game's own picture, and
     * the first person view, which the game has no picture of, is drawn with nothing the mod made
     * where the game has its own. Automatic at Low's level draws less and is not this.
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
     * Automatic, the frames are counted for JUDGED_OVER_NANOS at a time. A count that comes out
     * under SLOWEST frames a second takes the picture down a level, to STOCK at the bottom. Counts
     * at RISES_AT or over, one after another for RISES_AFTER_NANOS, take it up a level, unless the
     * level above was stepped down from too lately: see BARRED_NANOS.
     *
     * The game's frame is counted once however many views are drawn in it: the first of the
     * views being counted counts for all.
     *
     * @param limit the frame rate the game holds itself to, or 0 for none: the picture is never
     *              drawn faster, so it is not judged against a rate over it
     */
    static synchronized void frame(int view, long now, float limit) {
        counting[view] = true;
        for (int before = 0; before < view; before++) {
            if (counting[before]) {
                return;
            }
        }
        if (row != AUTOMATIC) {
            return;
        }
        long since = lastFrame;
        lastFrame = now;
        if (now < settlesAt) {
            countedSince = 0L;
            return;
        }
        if (countedSince == 0L) {
            countedSince = now;
            counted = longFrames = stalledFrames = 0;
            longNanos = stalledNanos = 0L;
            return;
        }
        counted++;
        long took = now - since;
        if (took > HITCH_NANOS) {
            longFrames++;
            longNanos += took;
        } else if (took > STALL_NANOS) {
            stalledFrames++;
            stalledNanos += took;
        }
        long span = now - countedSince;
        if (span < JUDGED_OVER_NANOS) {
            return;
        }
        float slowest = limit > 0f ? Math.min(SLOWEST, limit * SLOWEST_OF_LIMIT) : SLOWEST;
        float risesAt = limit > 0f ? Math.min(RISES_AT, limit * RISES_AT_OF_LIMIT) : RISES_AT;
        // Long frames that are half of the count or more are the picture itself, at three or four
        // frames a second or stalling on every other frame. Fewer are hitches, a save being written
        // or a jump across the map, however many come together, and so are stalled frames under a
        // quarter of the rest, the burst of a save written as the game goes on: the frames between
        // them are judged without them, and only as too slow, since such a count is not one of room
        // to spare. Stalled frames more often than that are the picture.
        boolean picture = longFrames > 1 && longFrames * 2 >= counted;
        boolean burst = stalledFrames > 0 && stalledFrames * 4 < counted - longFrames;
        boolean hitches = !picture && (longFrames > 0 || burst);
        int between = counted - longFrames - (burst ? stalledFrames : 0);
        long betweenNanos = span - longNanos - (burst ? stalledNanos : 0L);
        float rate = hitches ? between * 1e9f / betweenNanos : counted * 1e9f / span;
        countedSince = now;
        counted = longFrames = stalledFrames = 0;
        longNanos = stalledNanos = 0L;
        if (hitches) {
            fastFor = 0L;
            // Too little time between the hitches to tell a rate from.
            if (betweenNanos >= JUDGED_OVER_NANOS / 2 && rate < slowest && level > STOCK) {
                stepDown(now, rate);
            }
            return;
        }
        if (rate < slowest) {
            fastFor = 0L;
            if (level > STOCK) {
                stepDown(now, rate);
            }
        } else if (rate >= risesAt && level < HIGH) {
            fastFor += span;
            if (fastFor >= RISES_AFTER_NANOS && now >= barredUntil[level + 1]) {
                stepUp(now, rate, risesAt);
            }
        } else {
            fastFor = 0L;
        }
    }

    private static void stepDown(long now, float rate) {
        if (cameUpAt[level] != 0L && now - cameUpAt[level] < TRIAL_NANOS) {
            failures[level]++;
        }
        long barred = BARRED_NANOS << Math.min(failures[level], MOST_DOUBLINGS);
        barredUntil[level] = now + barred;
        int left = level;
        level--;
        drawn = AUTOMATIC_AT[level];
        countedSince = 0L;
        settlesAt = now + SETTLES_IN_NANOS;
        fastFor = 0L;
        stepUnsaid = true;
        Log.info(String.format(Locale.ROOT, "first person picture quality: Automatic goes down to %s, the picture was drawn %.0f times a second; "
                + "it is not stepped up to %s again for %d minutes", LEVELS[level], rate, LEVELS[left], barred / 60_000_000_000L));
    }

    private static void stepUp(long now, float rate, float risesAt) {
        level++;
        cameUpAt[level] = now;
        drawn = AUTOMATIC_AT[level];
        countedSince = 0L;
        settlesAt = now + SETTLES_UP_NANOS;
        fastFor = 0L;
        stepUnsaid = true;
        Log.info(String.format(Locale.ROOT, "first person picture quality: Automatic goes up to %s, the picture was drawn %.0f times a second, and %.0f or more for %d seconds",
                LEVELS[level], rate, risesAt, RISES_AFTER_NANOS / 1_000_000_000L));
    }

    /**
     * A view's frames are no longer counted: it was off, the game paused, or the content is being
     * made. With none counted the count begins again, and not before the level last stepped to
     * has settled.
     */
    static synchronized void rest(int view) {
        counting[view] = false;
        for (boolean other : counting) {
            if (other) {
                return;
            }
        }
        countedSince = 0L;
        fastFor = 0L;
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
     * Whether the world is mirrored at all. Without, a vehicle's mirrors are plain glass and
     * their picture of the road behind is not drawn, and surfacesMirror is false.
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
