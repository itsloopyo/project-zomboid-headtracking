package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Locale;

/**
 * How much the picture costs to draw, for each player at the screen: the Quality row of
 * CameraUnlock.ini for the first player's first person, and the IsometricQuality row for their
 * isometric view, and SecondPlayerQuality, SecondPlayerIsometricQuality and so on for the other
 * players (Config). The graphics key steps a player's mode of the view they are in and sets
 * their other view's to match; the options screen sets them apart. While a player's view is in
 * first person, flying to or from it or getting ready for it, it is drawn with their first person
 * mode. Otherwise their isometric mode is drawn, but for Low, where the game draws that view
 * itself and the mod draws nothing: the picture then stays first person's, so what was made for
 * first person is not given back and made again on every switch. What the views share on the
 * graphics card, the mod's own pictures and the size they are made at, is made for every view
 * that wants it: see plainEverywhere and smallerPictures. High
 * is the whole picture. Medium and Low draw it with fewer pixels, cheaper trees, smaller
 * shadow maps, fewer and broader grass plants, shorter sight and without depth in surfaces, for a
 * graphics card that cannot keep up with the whole of it. Low also leaves out the passes that shade
 * corners, light the air and mirror, with shorter sight still and nothing mirrored at all: no mirror
 * in a room or on a vehicle shows anything. A vehicle's mirror shows Low's picture in every mode
 * where it shows one.
 * A first start is in Automatic in first person and in Low in the isometric view, which has no
 * Automatic: its frames are not counted. Automatic is one level for every player in it, since it
 * judges the frames of the whole screen. It begins at High, steps down while the picture is drawn too slowly and back up while it
 * is drawn with room to spare: see frame. It judges the picture by the frames the player sees
 * in a stretch of time, frames over wall time, with one thing cut out: a single unbroken run
 * of stalled frames in the stretch that is a save or a load and not the picture: a frame or
 * two of hitch, however long, and stalled frames of 2 s at most, and no other run cut in the
 * 10 s before it. Stalls spread through the stretch, more than one run of them, or runs that
 * come back within 10 s are what the player sees, and the stretch is judged whole. Custom draws the picture the rows of [Graphics]
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
     * or Low's without the game's own look and with the mod's trees, and Custom is what the rows of [Graphics] hold.
     *
     * @param stockLook        see plain
     * @param treeQuality      TREES_SPRITES, TREES_NEAR or TREES_TO_SIGHT: see gamesPlants and crownsToSight
     * @param grassDensity     see grassDensity
     * @param renderScale      see scale
     * @param sharpShadows     shadow maps 4096 texels across, where they are otherwise 2048
     * @param viewDistance     see sight
     * @param drawDistance     how far off people, zombies, vehicles and the furniture the mod has models of are drawn, in tiles: see DrawDistance
     * @param farTreeDistance  see farTreeDistance
     * @param rainOnGlass      see glassRain
     * @param shadingAndLight  see shadedAndLit
     * @param reflections      see reflections and surfacesMirror
     * @param fullSizeTextures the opposite of smallerPictures
     * @param surfaceDepth     see relief
     * @param wholeFigureShadows see wholeFigureShadows
     * @param frameBlending    see frameBlending
     * @param lightDistance    see lightDistance
     */
    record Picture(boolean stockLook, int treeQuality, int grassDensity, float renderScale, boolean sharpShadows, int viewDistance, int drawDistance,
            int farTreeDistance, int rainOnGlass, boolean shadingAndLight, boolean reflections, boolean fullSizeTextures, boolean surfaceDepth,
            boolean wholeFigureShadows, boolean frameBlending, int lightDistance) {}

    /** The TreeQuality row's words by number: the game's sprites, the mod's crowns within Crowns.REACH, the mod's crowns as far as the view goes. */
    static final int TREES_SPRITES = 0, TREES_NEAR = 1, TREES_TO_SIGHT = 2;
    static final String TREE_QUALITIES = "Low,Medium,High";
    /** The GrassDensity row's range, a percentage of the plants grass grows, and the share Medium and Low grow. */
    static final int LEAST_GRASS = 10, ALL_GRASS = 100, SOME_GRASS = 40;
    /** How far the trees beyond the loaded map can be drawn, in tiles: the far land reaches no further. */
    static final int MOST_FAR_TREES = (int) FarLand.REACH;
    /** The most pixels a preset draws a view with before its render scale is lowered, 2560 by 1440: see scale. */
    static final long MOST_PRESET_PIXELS = 2560L * 1440L;
    /** How far from the eye lamps can light the picture themselves, in tiles. */
    static final int MOST_LIGHT_DISTANCE = 80;

    static final Picture LOW_PICTURE = new Picture(true, TREES_SPRITES, SOME_GRASS, 0.62f, false, 48, 48, 300, 0, false, false, false, false, false, false, 20);
    static final Picture MEDIUM_PICTURE = new Picture(false, TREES_NEAR, SOME_GRASS, 0.8f, false, 60, 60, 150, 1, true, true, false, false, false, true, 30);
    static final Picture HIGH_PICTURE = new Picture(false, TREES_TO_SIGHT, ALL_GRASS, 1f, true, 80, 80, 400, 2, true, true, true, true, true, true, 60);
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

    /**
     * By player: their first person mode, and their isometric view's, which is never AUTOMATIC.
     * Each array is replaced whole when a player's changes, so a thread reading it sees one or the other.
     */
    private static volatile int[] rows = filled(AUTOMATIC), overheadRows = filled(LOW);
    /** By view: whether it is in first person, flying to or from it, or getting ready for it. */
    private static final boolean[] inFirstPerson = new boolean[View.COUNT];
    /**
     * By view: whether its player is at the screen, which it says once it is drawn. The first
     * player's view is there from the start.
     */
    private static final boolean[] present = new boolean[View.COUNT];

    static {
        present[0] = true;
    }

    /** The level Automatic is at. */
    private static volatile int level = HIGH;
    /** What Custom draws: the rows of [Graphics] as the session last took them. */
    private static volatile Picture custom = HIGH_PICTURE;
    /**
     * By view: what its mode in effect draws now, and whether scale holds it to MOST_PRESET_PIXELS,
     * which it does in first person in any mode but Custom. One field, replaced whole, so the render
     * thread never reads one view's picture with the other's say on its size.
     */
    private record Shown(Picture[] pictures, boolean[] held) {}
    private static volatile Shown shown = new Shown(pictures(HIGH_PICTURE), new boolean[View.COUNT]);
    /** See smallerPictures, plainEverywhere and gamesPlantsEverywhere: worked out by refresh from every view's picture. */
    private static volatile boolean smaller, plainEverywhere, gamesPlantsEverywhere;
    /**
     * A frame longer than STALL_NANOS is stalled, and one longer than HITCH_NANOS is a hitch. A run
     * of stalled frames one after another is cut out of a count when it is the count's only run,
     * holds CUT_HITCHES hitches at most, however long, and its other frames take CUT_MOST_NANOS at
     * most, and no run was cut in the CUT_EVERY_NANOS before it began. A save or a load comes
     * minutes apart; a card that dips again and again is the picture, and a run of hitches is a
     * picture drawn a few times a second.
     */
    private static final long STALL_NANOS = 100_000_000L, HITCH_NANOS = 250_000_000L, CUT_MOST_NANOS = 2_000_000_000L, CUT_EVERY_NANOS = 10_000_000_000L;
    private static final int CUT_HITCHES = 2;
    /** When the count began (0 for none begun), when the frame before was drawn, and when the level last stepped to has settled. */
    private static long countedSince, lastFrame, settlesAt;
    /** The frame limit the count is judged against: a count begins again when the game's limit changes. */
    private static float countLimit;
    /**
     * The frames of the count; its runs of stalled frames; of the first, its frames, the time of
     * its hitches and of its other frames, how many hitches, and when it began; whether the frame
     * before was stalled; whether any frame was a hitch.
     */
    private static int counted, runs, runFrames, runHitches;
    private static long runHitchNanos, runStallNanos, runFrom;
    private static boolean inRun, hitched;
    /** When the last run cut out of a count began, 0 for none since the mode was set. */
    private static long lastCut;
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

    private static int[] filled(int mode) {
        int[] all = new int[View.COUNT];
        Arrays.fill(all, mode);
        return all;
    }

    private static Picture[] pictures(Picture picture) {
        Picture[] all = new Picture[View.COUNT];
        Arrays.fill(all, picture);
        return all;
    }

    private static boolean splitScreen;

    /**
     * Told how many players are at the screen. As the screen comes to be split, each player at
     * the screen in High in either view goes to Medium there: one picture of the screen is then
     * drawn more than once. The rows of a player not at the screen are left as they are.
     *
     * @return the players whose modes were lowered, a bit each by number
     */
    static synchronized int players(int count) {
        boolean wasSplit = splitScreen;
        splitScreen = count > 1;
        if (!splitScreen || wasSplit) {
            return 0;
        }
        int lowered = 0;
        for (int player = 0; player < Math.min(count, View.COUNT); player++) {
            boolean high = rows[player] == HIGH || overheadRows[player] == HIGH;
            if (rows[player] == HIGH) {
                set(player, MEDIUM);
            }
            if (overheadRows[player] == HIGH) {
                setOverhead(player, MEDIUM);
            }
            lowered |= high ? 1 << player : 0;
        }
        if (lowered != 0) {
            Log.info("High graphics lowered to Medium for split screen");
        }
        return lowered;
    }

    /**
     * Sets a player's first person mode. Automatic's level and what it has counted begin again
     * when a player at the screen goes to it and no other player at the screen is in it.
     */
    static synchronized void set(int player, int asked) {
        if (asked == rows[player]) {
            return;
        }
        boolean automaticBegins = present[player] && asked == AUTOMATIC && !anyIn(AUTOMATIC);
        int[] next = rows.clone();
        next[player] = asked;
        rows = next;
        if (automaticBegins) {
            beginAutomatic();
        }
        // A step of Automatic is said to the players in it, and with none left in it there is nobody to say it to.
        stepUnsaid &= anyIn(AUTOMATIC);
        refresh();
        Log.info("player " + (player + 1) + "'s first person graphics mode: " + NAMES[asked] + (asked == CUSTOM ? ", " + described(custom) : ""));
    }

    /** Sets a player's isometric view's mode: Low, Medium, High or Custom. */
    static synchronized void setOverhead(int player, int asked) {
        if (asked == AUTOMATIC) {
            throw new IllegalArgumentException("the isometric view has no Automatic graphics mode");
        }
        if (asked == overheadRows[player]) {
            return;
        }
        int[] next = overheadRows.clone();
        next[player] = asked;
        overheadRows = next;
        refresh();
        Log.info("player " + (player + 1) + "'s isometric graphics mode: " + NAMES[asked] + (asked == CUSTOM ? ", " + described(custom) : ""));
    }

    private static void beginAutomatic() {
        level = HIGH;
        countedSince = 0L;
        settlesAt = 0L;
        lastCut = 0L;
        fastFor = 0L;
        Arrays.fill(barredUntil, 0L);
        Arrays.fill(cameUpAt, 0L);
        Arrays.fill(failures, 0);
    }

    /** Whether a player at the screen has their first person mode at mode. */
    private static boolean anyIn(int mode) {
        int[] now = rows;
        for (int view = 0; view < now.length; view++) {
            if (present[view] && now[view] == mode) {
                return true;
            }
        }
        return false;
    }

    /**
     * A view says, each frame, whether it is in first person, flying to or from it, or getting
     * ready for it. A player who comes to the screen in Automatic, with no other player at the
     * screen in it, begins it again as set does. Logic thread.
     */
    static synchronized void inFirstPerson(int view, boolean in) {
        if (present[view] && inFirstPerson[view] == in) {
            return;
        }
        if (!present[view] && rows[view] == AUTOMATIC && !anyIn(AUTOMATIC)) {
            beginAutomatic();
        }
        present[view] = true;
        inFirstPerson[view] = in;
        refresh();
        Log.info("player " + (view + 1) + "'s graphics mode in effect: " + said(view));
    }

    /**
     * A view has no player: its frames are no longer counted, and what the views share is no
     * longer made for it. It is at the screen again once a player there says where they are.
     * Logic thread.
     */
    static synchronized void gone(int view) {
        rest(view);
        if (!present[view]) {
            return;
        }
        present[view] = false;
        inFirstPerson[view] = false;
        stepUnsaid &= anyIn(AUTOMATIC);
        refresh();
        Log.info("player " + (view + 1) + "'s view is gone, and draws nothing");
    }

    /** Whether a view's player is at the screen. */
    static synchronized boolean present(int view) {
        return present[view];
    }

    /** Whether a view's first person mode is the one in effect, which the graphics key steps. */
    static synchronized boolean firstPersonInEffect(int view) {
        return inFirstPerson[view];
    }

    private static void refresh() {
        int[] overheads = overheadRows;
        Picture[] next = new Picture[View.COUNT];
        boolean[] held = new boolean[View.COUNT];
        boolean anyPresent = false, anyFullSize = false, allPlain = true, allGamesPlants = true;
        for (int view = 0; view < next.length; view++) {
            int overhead = overheads[view];
            boolean firstPerson = inFirstPerson[view] || overhead == LOW;
            next[view] = firstPerson ? firstPersonPicture(view) : overhead == CUSTOM ? custom : preset(overhead);
            // The isometric view is drawn to the game's picture of the world, whose size goes with the zoom, and is never held.
            held[view] = inFirstPerson[view] && rows[view] != CUSTOM;
            // A view in its isometric Low draws none of the mod's pictures, and holds first person's made for the switch.
            if (present[view]) {
                anyPresent = true;
                anyFullSize |= next[view].fullSizeTextures;
                allPlain &= next[view].stockLook;
                allGamesPlants &= next[view].treeQuality == TREES_SPRITES;
            }
        }
        shown = new Shown(next, held);
        // With no player at the screen the pictures are left as they were made, not made again for nobody.
        if (anyPresent) {
            smaller = !anyFullSize;
        }
        plainEverywhere = allPlain;
        gamesPlantsEverywhere = allGamesPlants;
    }

    /** What a player's first person mode draws, in effect or not: Custom's rows, the mode's picture, or that of the level Automatic is at. */
    static Picture firstPersonPicture(int player) {
        int row = rows[player];
        return row == CUSTOM ? custom : row == AUTOMATIC ? AUTOMATIC_AT[level] : preset(row);
    }

    /** Takes what Custom draws. Low, Medium and High leave it as it is, for the next time a mode is Custom. */
    static synchronized void custom(Picture picture) {
        if (picture.equals(custom)) {
            return;
        }
        custom = picture;
        refresh();
        boolean shown = anyIn(CUSTOM);
        for (int view = 0; view < View.COUNT; view++) {
            shown |= present[view] && overheadRows[view] == CUSTOM;
        }
        if (shown) {
            Log.info("graphics mode Custom: " + described(picture));
        }
    }

    static Picture custom() {
        return custom;
    }

    /** What a view's mode in effect draws now: Custom's rows in Custom, and in the other modes the mode's picture, or that of the level Automatic is at. */
    static Picture drawn(int view) {
        return shown.pictures[view];
    }

    /**
     * The picture a row of [Graphics] changed on the options screen starts Custom from, the change
     * then made to it: what the first player's first person mode draws, and in Automatic what the level it is at draws, but
     * for STOCK, which starts it from the level above, Low's cost with what the mod made. Stock is
     * how Automatic saves the graphics card, not a look the player chose. The rows of [Graphics]
     * are the first player's, as the options screen, where they are set, is.
     */
    static synchronized Picture customFrom() {
        return rows[0] == AUTOMATIC && level == STOCK ? AUTOMATIC_AT[STOCK + 1] : firstPersonPicture(0);
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
        return new Picture(false, Math.max(at.treeQuality, TREES_NEAR), at.grassDensity, at.renderScale, at.sharpShadows, at.viewDistance, at.drawDistance,
                at.farTreeDistance, at.rainOnGlass, at.shadingAndLight, at.reflections, at.fullSizeTextures, at.surfaceDepth, at.wholeFigureShadows,
                at.frameBlending, at.lightDistance);
    }

    static String described(Picture picture) {
        return String.format(Locale.ROOT, "stock look %b, tree quality %d, grass density %d, render scale %.2f, sharp shadows %b, view distance %d, "
                        + "zombies and props draw distance %d, far tree distance %d, rain on glass %d, shading and light %b, reflections %b, "
                        + "full size textures %b, surface depth %b, whole figure shadows %b, frame blending %b, light distance %d",
                picture.stockLook, picture.treeQuality, picture.grassDensity, picture.renderScale, picture.sharpShadows, picture.viewDistance, picture.drawDistance,
                picture.farTreeDistance, picture.rainOnGlass, picture.shadingAndLight, picture.reflections, picture.fullSizeTextures,
                picture.surfaceDepth, picture.wholeFigureShadows, picture.frameBlending, picture.lightDistance);
    }

    /** The mode the hotkey goes to from a player's first person mode: Automatic, Low, Medium, High, Custom and round again. */
    static int next(int player) {
        return (rows[player] + 1) % NAMES.length;
    }

    /** The mode the hotkey goes to from a player's isometric mode: Low, Medium, High, Custom and round again. */
    static int nextOverhead(int player) {
        int row = overheadRows[player];
        return row == CUSTOM ? LOW : row + 1;
    }

    static int row(int player) {
        return rows[player];
    }

    static int overheadRow(int player) {
        return overheadRows[player];
    }

    /** The words CameraUnlock.ini holds for the isometric view's row, which numbers them from 0 for LOW. */
    static String overheadWords() {
        return String.join(",", Arrays.copyOfRange(NAMES, LOW, NAMES.length));
    }

    /** What a view's screen says of its mode in effect: "First person graphics: Automatic (Medium)" or "Isometric graphics: Low". */
    static synchronized String said(int view) {
        return inFirstPerson[view] ? "First person graphics: " + name(view) : "Isometric graphics: " + NAMES[overheadRows[view]];
    }

    /**
     * What a player's screen says when the graphics key sets both their views: "Graphics: Medium",
     * or where they differ, as in Automatic, "Graphics: Automatic (High), isometric Low".
     */
    static String saidOfBoth(int player) {
        int row = rows[player], overheadRow = overheadRows[player];
        return row == overheadRow ? "Graphics: " + NAMES[row] : "Graphics: " + name(player) + ", isometric " + NAMES[overheadRow];
    }

    /** A player's first person mode, and in Automatic the level it is at: "Automatic (Medium)". */
    static String name(int player) {
        return choice(rows[player]);
    }

    /** How the options screen's list names a mode: Automatic, while a player at the screen is in it, with the level it is at. */
    static synchronized String choice(int mode) {
        return mode == AUTOMATIC && anyIn(AUTOMATIC) ? NAMES[AUTOMATIC] + " (" + LEVELS[level] + ")" : NAMES[mode];
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
     * Whether a view's picture is Low's, the nearest there is to the game's own look, or Automatic's
     * at STOCK: the first person view, which the game has no picture of, is then drawn with
     * nothing the mod made where the game has its own but the furniture and the windows and
     * doors (Own.WITHOUT_PICTURES), each piece of furniture as its plain model. Automatic at Low's level draws less and is
     * not this.
     */
    static boolean plain(int view) {
        return shown.pictures[view].stockLook;
    }

    /**
     * Whether every player at the screen has a plain picture: what the mod made once for every
     * view, its pictures, skins and models, is then shown to nobody.
     */
    static boolean plainEverywhere() {
        return plainEverywhere;
    }

    /** Whether a player's isometric view is the game's own picture: in its Low. */
    static boolean overheadPlain(int player) {
        return overheadRows[player] == LOW;
    }

    /**
     * Whether a view's grass, trees, bushes and rocks are the game's own sprites, TreeQuality at Low: the
     * mod's are the dearest thing in the picture to draw. Automatic keeps the mod's at every level.
     */
    static boolean gamesPlants(int view) {
        return shown.pictures[view].treeQuality == TREES_SPRITES;
    }

    /** Whether every player at the screen has the game's own plants: see gamesPlants. */
    static boolean gamesPlantsEverywhere() {
        return gamesPlantsEverywhere;
    }

    /**
     * Counts a frame of a view's first person drawn while the game runs. With a counted view's
     * player in Automatic, frames are counted until JUDGED_OVER_NANOS have passed, less a run of stalled
     * frames cut out (see CUT_MOST_NANOS), and the count is judged by its rate: its frames over
     * its time, both without the run cut out, against the frame limit it was counted under. A count under SLOWEST frames a second takes the
     * picture down a level, to STOCK at the bottom. Counts at RISES_AT or over, one after another
     * for RISES_AFTER_NANOS of their time, take it up a level, unless the level above was stepped
     * down from too lately: see BARRED_NANOS. A count under RISES_AT, or with a hitch in it, cut
     * out or not, begins that time again: a picture that hitches has no room to spare.
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
        boolean automatic = false;
        for (int other = 0; other < counting.length; other++) {
            automatic |= counting[other] && rows[other] == AUTOMATIC;
        }
        if (!automatic) {
            return;
        }
        long since = lastFrame;
        lastFrame = now;
        if (now < settlesAt) {
            countedSince = 0L;
            return;
        }
        if (countedSince == 0L || limit != countLimit) {
            // Frames drawn under another limit are another picture: 29.5 a second under 30 and 59.5 under 60 are both whole.
            countedSince = now;
            countLimit = limit;
            counted = runs = runFrames = runHitches = 0;
            runHitchNanos = runStallNanos = 0L;
            inRun = hitched = false;
            return;
        }
        long took = now - since;
        counted++;
        boolean stalled = took > STALL_NANOS, hitchFrame = took > HITCH_NANOS;
        if (stalled && !inRun && ++runs == 1) {
            runFrom = since;
        }
        if (stalled && runs == 1) {
            runFrames++;
            if (hitchFrame) {
                runHitches++;
                runHitchNanos += took;
            } else {
                runStallNanos += took;
            }
        }
        inRun = stalled;
        hitched |= hitchFrame;
        boolean cut = runs == 1 && runHitches <= CUT_HITCHES && runStallNanos <= CUT_MOST_NANOS && (lastCut == 0L || runFrom - lastCut >= CUT_EVERY_NANOS);
        long time = now - countedSince - (cut ? runHitchNanos + runStallNanos : 0L);
        if (time < JUDGED_OVER_NANOS) {
            return;
        }
        float rate = (counted - (cut ? runFrames : 0)) * 1e9f / time;
        boolean hitch = hitched;
        if (cut) {
            lastCut = runFrom;
        }
        countedSince = now;
        counted = runs = runFrames = runHitches = 0;
        runHitchNanos = runStallNanos = 0L;
        inRun = hitched = false;
        float slowest = countLimit > 0f ? Math.min(SLOWEST, countLimit * SLOWEST_OF_LIMIT) : SLOWEST;
        float risesAt = countLimit > 0f ? Math.min(RISES_AT, countLimit * RISES_AT_OF_LIMIT) : RISES_AT;
        if (rate < slowest) {
            fastFor = 0L;
            if (level > STOCK) {
                stepDown(now, rate);
            }
        } else if (hitch || rate < risesAt) {
            fastFor = 0L;
        } else if (level < HIGH) {
            fastFor += time;
            if (fastFor >= RISES_AFTER_NANOS && now >= barredUntil[level + 1]) {
                stepUp(now, rate, risesAt);
            }
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
        refresh();
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
        refresh();
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
    static float scale(int view) {
        return shown.pictures[view].renderScale;
    }

    /**
     * scale for a view wide by high pixels. In first person, Low, Medium, High and Automatic draw a
     * view of more pixels than MOST_PRESET_PIXELS as if it were that size: past it the cost goes on
     * growing with the pixels and the picture barely sharpens. Custom draws its RenderScale row at
     * any size, and so does the isometric view.
     */
    static float scale(int view, int wide, int high) {
        Shown now = shown;
        float share = now.pictures[view].renderScale;
        long pixels = (long) wide * high;
        if (!now.held[view] || pixels <= MOST_PRESET_PIXELS) {
            return share;
        }
        return share * (float) Math.sqrt((double) MOST_PRESET_PIXELS / pixels);
    }

    /** How many texels a shadow map is across. */
    static int shadowSize(int view) {
        return shown.pictures[view].sharpShadows ? 4096 : 2048;
    }

    /** Whether shadows are drawn finer: more of the fires throw them too (FireShadows). */
    static boolean sharpShadows(int view) {
        return shown.pictures[view].sharpShadows;
    }

    /** How far the view is built and drawn, in tiles, where the loaded map reaches that far. */
    static int sight(int view) {
        return shown.pictures[view].viewDistance;
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
    static int glassRain(int view) {
        return shown.pictures[view].rainOnGlass;
    }

    /** Whether corners are shaded and lent the light of what stands near, and the air is lit. */
    static boolean shadedAndLit(int view) {
        return shown.pictures[view].shadingAndLight;
    }

    /**
     * Whether the world is mirrored at all. Without, a vehicle's mirrors are plain glass and
     * their picture of the road behind is not drawn, and surfacesMirror is false.
     */
    static boolean reflections(int view) {
        return shown.pictures[view].reflections;
    }

    /**
     * Whether wet roads, polished floors, glass and water mirror the world, and a window shows
     * the viewer in it. Only with the shading and light, which Low leaves out, and the one row
     * covers both kinds of mirror.
     */
    static boolean surfacesMirror(int view) {
        Picture picture = shown.pictures[view];
        return picture.reflections && picture.shadingAndLight;
    }

    /**
     * Whether the mod's pictures of surfaces, on walls and of skin are made at half their width
     * and height, which is a quarter of what they take on the graphics card and in the PC's memory.
     * Only High draws a dot of picture to a dot of the window, up to MOST_PRESET_PIXELS, and near enough to tell them apart.
     * One set is made for every view, full size while any view asks for it.
     */
    static boolean smallerPictures() {
        return smaller;
    }

    /**
     * Whether a tree or a bush is the mod's own, a crown of sprigs on a round bole, as far as the
     * view is built: TreeQuality at High. Otherwise one further off than Crowns.REACH is the game's
     * sprite on a fan of cards, which costs less to build and draw and changes to a crown as the eye nears it.
     */
    static boolean crownsToSight(int view) {
        return shown.pictures[view].treeQuality == TREES_TO_SIGHT;
    }

    /** The share of its plants grass grows, as a percentage: see Grass.density. */
    static int grassDensity(int view) {
        return shown.pictures[view].grassDensity;
    }

    /**
     * How far from the eye the trees beyond the loaded map stand, in tiles, 0 for none. They stand
     * only where the game has no map loaded, where nobody is drawn, so they hide no one from anyone.
     */
    static int farTreeDistance(int view) {
        return shown.pictures[view].farTreeDistance;
    }

    /**
     * Whether each frame of the first person picture is mixed with the one before it: what changes
     * from frame to frame is steadied and the whole picture is a little softer.
     */
    static boolean frameBlending(int view) {
        return shown.pictures[view].frameBlending;
    }

    /**
     * How far from the eye a lamp lights the picture with a light of its own (Lamps), in tiles, 0
     * for none: past it, and with none, lamps light the squares round them as the game lights them.
     */
    static int lightDistance(int view) {
        return shown.pictures[view].lightDistance;
    }

    /** Whether near surfaces are given the depth of what they show. */
    static boolean relief(int view) {
        return shown.pictures[view].surfaceDepth;
    }

    /**
     * Whether everything a person wears and carries throws its shadow at every distance.
     * Otherwise a person further off than CharacterDraw.SHADOW_WHOLE_WITHIN throws the shadow of
     * their body alone, and a crowd costs the sun's maps one part a person and not every part
     * they have.
     */
    static boolean wholeFigureShadows(int view) {
        return shown.pictures[view].wholeFigureShadows;
    }

}
