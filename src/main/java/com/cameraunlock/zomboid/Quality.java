package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Locale;

/**
 * How much the picture costs to draw: the Quality row of CameraUnlock.ini for first person, and
 * the IsometricQuality row for the game's isometric view. The graphics key steps the mode of the
 * view it is pressed in and sets the other view's to match; the options screen sets them apart. While any view is in first person, flying to or
 * from it or getting ready for it, every view is drawn with first person's mode. Otherwise the
 * isometric view's mode is drawn, but for Low, where the game draws that view itself and the mod
 * draws nothing: the picture then stays first person's, so what was made for first person is not
 * given back and made again on every switch. High
 * is the whole picture. Medium and Low draw it with fewer pixels, cheaper trees, smaller
 * shadow maps, fewer and broader grass plants, shorter sight and without the passes that shade
 * corners, light the air, mirror and give surfaces depth, for a graphics card that cannot keep up
 * with the whole of it, and Low with shorter sight still. A vehicle's mirror shows Low's
 * picture in every mode where it shows one.
 * A first start is in Automatic in first person and in Low in the isometric view, which has no
 * Automatic: its frames are not counted. Automatic begins at High, steps down while the picture is drawn too slowly and back up while it
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
     */
    record Picture(boolean stockLook, int treeQuality, int grassDensity, float renderScale, boolean sharpShadows, int viewDistance, int drawDistance,
            int farTreeDistance, int rainOnGlass, boolean shadingAndLight, boolean reflections, boolean fullSizeTextures, boolean surfaceDepth,
            boolean wholeFigureShadows, boolean frameBlending) {}

    /** The TreeQuality row's words by number: the game's sprites, the mod's crowns within Crowns.REACH, the mod's crowns as far as the view goes. */
    static final int TREES_SPRITES = 0, TREES_NEAR = 1, TREES_TO_SIGHT = 2;
    static final String TREE_QUALITIES = "Low,Medium,High";
    /** The GrassDensity row's range, a percentage of the plants grass grows, and the share Medium and Low grow. */
    static final int LEAST_GRASS = 10, ALL_GRASS = 100, SOME_GRASS = 40;
    /** How far the trees beyond the loaded map can be drawn, in tiles: the far land reaches no further. */
    static final int MOST_FAR_TREES = (int) FarLand.REACH;

    static final Picture LOW_PICTURE = new Picture(true, TREES_SPRITES, SOME_GRASS, 0.62f, false, 48, 48, 300, 0, false, true, false, false, false, false);
    static final Picture MEDIUM_PICTURE = new Picture(false, TREES_NEAR, SOME_GRASS, 0.7f, false, 60, 60, 150, 1, false, true, false, false, false, true);
    static final Picture HIGH_PICTURE = new Picture(false, TREES_TO_SIGHT, ALL_GRASS, 1f, true, 80, 80, MOST_FAR_TREES, 2, true, true, true, true, true, true);
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

    /** First person's mode, and the isometric view's, which is never AUTOMATIC. */
    private static volatile int row = AUTOMATIC, overheadRow = LOW;
    /** By view: whether it is in first person, flying to or from it, or getting ready for it. */
    private static final boolean[] inFirstPerson = new boolean[View.COUNT];
    /** Whether any view is: first person's mode is then the one in effect. */
    private static volatile boolean firstPersonInEffect;
    /** The level Automatic is at. */
    private static volatile int level = HIGH;
    /** What Custom draws: the rows of [Graphics] as the session last took them. */
    private static volatile Picture custom = HIGH_PICTURE;
    /** What the mode in effect draws now. */
    private static volatile Picture drawn = HIGH_PICTURE;
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

    private static boolean splitScreen;

    static synchronized boolean players(int count) {
        boolean wasSplit = splitScreen;
        splitScreen = count > 1;
        if (!splitScreen || wasSplit) {
            return false;
        }
        boolean changed = row == HIGH || overheadRow == HIGH;
        if (row == HIGH) {
            set(MEDIUM);
        }
        if (overheadRow == HIGH) {
            setOverhead(MEDIUM);
        }
        if (changed) {
            Log.info("High graphics lowered to Medium for split screen");
        }
        return changed;
    }

    static synchronized void set(int asked) {
        if (asked == row) {
            return;
        }
        row = asked;
        level = HIGH;
        countedSince = 0L;
        settlesAt = 0L;
        lastCut = 0L;
        fastFor = 0L;
        Arrays.fill(barredUntil, 0L);
        Arrays.fill(cameUpAt, 0L);
        Arrays.fill(failures, 0);
        stepUnsaid = false;
        refresh();
        Log.info("first person graphics mode: " + NAMES[asked] + (asked == CUSTOM ? ", " + described(custom) : ""));
    }

    /** Sets the isometric view's mode: Low, Medium, High or Custom. */
    static synchronized void setOverhead(int asked) {
        if (asked == AUTOMATIC) {
            throw new IllegalArgumentException("the isometric view has no Automatic graphics mode");
        }
        if (asked == overheadRow) {
            return;
        }
        overheadRow = asked;
        refresh();
        Log.info("isometric graphics mode: " + NAMES[asked] + (asked == CUSTOM ? ", " + described(custom) : ""));
    }

    /**
     * A view says, each frame, whether it is in first person, flying to or from it, or getting
     * ready for it. Logic thread.
     */
    static synchronized void inFirstPerson(int view, boolean in) {
        if (inFirstPerson[view] == in) {
            return;
        }
        inFirstPerson[view] = in;
        boolean any = false;
        for (boolean other : inFirstPerson) {
            any |= other;
        }
        if (any != firstPersonInEffect) {
            firstPersonInEffect = any;
            refresh();
            Log.info("graphics mode in effect: " + said());
        }
    }

    /** Whether first person's mode is the one in effect, which the graphics key steps. */
    static boolean firstPersonInEffect() {
        return firstPersonInEffect;
    }

    private static void refresh() {
        drawn = firstPersonInEffect || overheadPlain() ? firstPersonPicture() : overheadRow == CUSTOM ? custom : preset(overheadRow);
    }

    /** What first person's mode draws, in effect or not: Custom's rows, the mode's picture, or that of the level Automatic is at. */
    static Picture firstPersonPicture() {
        return row == CUSTOM ? custom : row == AUTOMATIC ? AUTOMATIC_AT[level] : preset(row);
    }

    /** Takes what Custom draws. Low, Medium and High leave it as it is, for the next time the mode is Custom. */
    static synchronized void custom(Picture picture) {
        if (picture.equals(custom)) {
            return;
        }
        custom = picture;
        refresh();
        if (row == CUSTOM || overheadRow == CUSTOM) {
            Log.info("graphics mode Custom: " + described(picture));
        }
    }

    static Picture custom() {
        return custom;
    }

    /** What the mode in effect draws now: Custom's rows in Custom, and in the other modes the mode's picture, or that of the level Automatic is at. */
    static Picture drawn() {
        return drawn;
    }

    /**
     * The picture a row of [Graphics] changed on the options screen starts Custom from, the change
     * then made to it: what first person's mode draws, and in Automatic what the level it is at draws, but
     * for STOCK, which starts it from the level above, Low's cost with what the mod made. Stock is
     * how Automatic saves the graphics card, not a look the player chose.
     */
    static synchronized Picture customFrom() {
        return row == AUTOMATIC && level == STOCK ? AUTOMATIC_AT[STOCK + 1] : firstPersonPicture();
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
                at.frameBlending);
    }

    static String described(Picture picture) {
        return String.format(Locale.ROOT, "stock look %b, tree quality %d, grass density %d, render scale %.2f, sharp shadows %b, view distance %d, "
                        + "zombies and props draw distance %d, far tree distance %d, rain on glass %d, shading and light %b, reflections %b, "
                        + "full size textures %b, surface depth %b, whole figure shadows %b, frame blending %b",
                picture.stockLook, picture.treeQuality, picture.grassDensity, picture.renderScale, picture.sharpShadows, picture.viewDistance, picture.drawDistance,
                picture.farTreeDistance, picture.rainOnGlass, picture.shadingAndLight, picture.reflections, picture.fullSizeTextures,
                picture.surfaceDepth, picture.wholeFigureShadows, picture.frameBlending);
    }

    /** The mode the hotkey goes to from first person's: Automatic, Low, Medium, High, Custom and round again. */
    static int next() {
        return (row + 1) % NAMES.length;
    }

    /** The mode the hotkey goes to from the isometric view's: Low, Medium, High, Custom and round again. */
    static int nextOverhead() {
        return overheadRow == CUSTOM ? LOW : overheadRow + 1;
    }

    static int row() {
        return row;
    }

    static int overheadRow() {
        return overheadRow;
    }

    /** The words CameraUnlock.ini holds for the isometric view's row, which numbers them from 0 for LOW. */
    static String overheadWords() {
        return String.join(",", Arrays.copyOfRange(NAMES, LOW, NAMES.length));
    }

    /** What the screen says of the mode in effect: "First person graphics: Automatic (Medium)" or "Isometric graphics: Low". */
    static String said() {
        return firstPersonInEffect ? "First person graphics: " + name() : "Isometric graphics: " + NAMES[overheadRow];
    }

    /**
     * What the screen says when the graphics key sets both views: "Graphics: Medium", or where
     * they differ, as in Automatic, "Graphics: Automatic (High), isometric Low".
     */
    static String saidOfBoth() {
        return row == overheadRow ? "Graphics: " + NAMES[row] : "Graphics: " + name() + ", isometric " + NAMES[overheadRow];
    }

    /** First person's mode, and in Automatic the level it is at: "Automatic (Medium)". */
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
     * Whether the picture is Low's, the nearest there is to the game's own look, or Automatic's
     * at STOCK: the first person view, which the game has no picture of, is then drawn with
     * nothing the mod made where the game has its own but the furniture and the windows and
     * doors (Own.WITHOUT_PICTURES), each piece of furniture as its plain model. Automatic at Low's level draws less and is
     * not this.
     */
    static boolean plain() {
        return drawn.stockLook;
    }

    /** Whether the isometric view is the game's own picture: in its Low. */
    static boolean overheadPlain() {
        return overheadRow == LOW;
    }

    /**
     * Whether grass, trees, bushes and rocks are the game's own sprites, TreeQuality at Low: the
     * mod's are the dearest thing in the picture to draw. Automatic keeps the mod's at every level.
     */
    static boolean gamesPlants() {
        return drawn.treeQuality == TREES_SPRITES;
    }

    /**
     * Counts a frame of a view's first person drawn while the game runs. With the row at
     * Automatic, frames are counted until JUDGED_OVER_NANOS have passed, less a run of stalled
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
        if (row != AUTOMATIC) {
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
    static float scale() {
        return drawn.renderScale;
    }

    /** How many texels a shadow map is across. */
    static int shadowSize() {
        return drawn.sharpShadows ? 4096 : 2048;
    }

    /** Whether shadows are drawn finer: more of the fires throw them too (FireShadows). */
    static boolean sharpShadows() {
        return drawn.sharpShadows;
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
     * view is built: TreeQuality at High. Otherwise one further off than Crowns.REACH is the game's
     * sprite on a fan of cards, which costs less to build and draw and changes to a crown as the eye nears it.
     */
    static boolean crownsToSight() {
        return drawn.treeQuality == TREES_TO_SIGHT;
    }

    /** The share of its plants grass grows, as a percentage: see Grass.density. */
    static int grassDensity() {
        return drawn.grassDensity;
    }

    /**
     * How far from the eye the trees beyond the loaded map stand, in tiles, 0 for none. They stand
     * only where the game has no map loaded, where nobody is drawn, so they hide no one from anyone.
     */
    static int farTreeDistance() {
        return drawn.farTreeDistance;
    }

    /**
     * Whether each frame of the first person picture is mixed with the one before it: what changes
     * from frame to frame is steadied and the whole picture is a little softer.
     */
    static boolean frameBlending() {
        return drawn.frameBlending;
    }

    /** Whether near surfaces are given the depth of what they show. */
    static boolean relief() {
        return drawn.surfaceDepth;
    }

    /**
     * Whether everything a person wears and carries throws its shadow at every distance.
     * Otherwise a person further off than CharacterDraw.SHADOW_WHOLE_WITHIN throws the shadow of
     * their body alone, and a crowd costs the sun's maps one part a person and not every part
     * they have.
     */
    static boolean wholeFigureShadows() {
        return drawn.wholeFigureShadows;
    }

}
