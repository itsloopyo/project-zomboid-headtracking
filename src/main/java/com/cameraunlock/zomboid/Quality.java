package com.cameraunlock.zomboid;

import java.util.Locale;

/**
 * How much the first person picture costs to draw: the Quality row of CameraUnlock.ini. High
 * is the whole picture. Medium and Low draw it with fewer pixels, shorter sight, smaller
 * shadow maps and, on Low, without the passes that shade corners and light the air, for a
 * graphics card that cannot keep up with the whole of it. Automatic begins at High and steps
 * down while the picture is being drawn slower than {@link #SLOWEST} frames a second.
 *
 * The row is read again while the game runs. The thread that watches the file sets it, the
 * logic thread reads how far to build and steps Automatic down, and the render thread reads
 * the rest as it draws a frame.
 */
final class Quality {
    /** The row's words, as the mod's DLL hands them over (native/src/bridge.h holds the same numbers). */
    static final int AUTOMATIC = 0, LOW = 1, MEDIUM = 2, HIGH = 3;
    private static final String[] NAMES = {"Automatic", "Low", "Medium", "High"};

    /** Automatic steps down when the picture has been drawn slower than this many frames a second. */
    private static final float SLOWEST = 45f;
    /** How long the frame rate is counted for before it is judged, and how long after a step before counting begins again. */
    private static final long JUDGED_OVER_NANOS = 3_000_000_000L, SETTLES_IN_NANOS = 2_000_000_000L;

    private static volatile int row = AUTOMATIC;
    private static volatile int level = HIGH;
    private static long countedSince;
    private static int counted;

    private Quality() {}

    static void set(int asked) {
        if (asked == row) {
            return;
        }
        row = asked;
        level = asked == AUTOMATIC ? HIGH : asked;
        countedSince = 0L;
        Log.info("first person picture quality: " + NAMES[asked]);
    }

    /**
     * Counts a frame of the first person view drawn while the game runs. With the row at
     * Automatic, a count that comes out under {@link #SLOWEST} frames a second takes the
     * picture down a level. It is never taken up again in a session: what made the frames slow
     * may be the level left behind, and trying it again would be the stutter back.
     */
    static void frame(long now) {
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

    /** The count begins again: the view was off, or the game paused. */
    static void rest() {
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
        return level == HIGH ? 80 : level == MEDIUM ? 64 : 48;
    }

    /** Whether corners are shaded and lent the light of what stands near, and the air is lit, each by a pass of its own. */
    static boolean shadedAndLit() {
        return level != LOW;
    }

    /** Whether near surfaces are given the depth of what they show. */
    static boolean relief() {
        return level != LOW;
    }

    /**
     * Whether the far shadow map and the map of what is overhead are each drawn every other
     * frame, turn about. Both are of things tens of tiles off or straight overhead, and each
     * is read through the matrix it was last drawn with, so a frame late is not seen.
     */
    static boolean mapsTakeTurns() {
        return level != HIGH;
    }
}
