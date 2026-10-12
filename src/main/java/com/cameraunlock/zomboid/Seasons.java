package com.cameraunlock.zomboid;

import zombie.erosion.season.ErosionSeason;

/**
 * Where the year is, for what the season does to plants: how much of a deciduous tree's crown
 * is still on it, how much fallen leaf lies on the ground, how many leaves the wind takes.
 * Captured on the game's logic thread with the sky, read from any thread.
 *
 * The season and how far through it the game is come from the game's ErosionSeason, whose
 * progression is the day of the season over the days it lasts, 0 to 1.
 */
final class Seasons {
    /** The game's season numbers, as ErosionSeason has them. */
    static final int DEFAULT = 0, SPRING = 1, SUMMER = 2, SUMMER2 = 3, AUTUMN = 4, WINTER = 5;
    /** How far through autumn the earliest and the latest tree start to drop, and how much of autumn the drop takes. */
    private static final float FALL_EARLIEST = 0.05f, FALL_LATEST = 0.45f, FALL_TAKES = 0.45f;
    /** How far through spring the earliest and the latest tree are back in full leaf. */
    private static final float LEAF_EARLIEST = 0.3f, LEAF_LATEST = 0.7f;
    /** The fallen leaf that lies through the summer under deciduous trees, as a share of what lies at the end of autumn. */
    private static final float LITTER_SUMMER = 0.12f;

    private static volatile int season = SUMMER;
    private static volatile float progression = 0.5f;

    private Seasons() {}

    static void capture(ErosionSeason erosion) {
        season = erosion.getSeason();
        progression = Math.min(Math.max(erosion.getSeasonProgression(), 0f), 1f);
    }

    static int season() {
        return season;
    }

    static float progression() {
        return progression;
    }

    /** How much of its crown a deciduous tree has on it now, 1 in full leaf and 0 bare. */
    static float leafOn(int treeHash) {
        return leafOn(season, progression, treeHash);
    }

    /**
     * How much of its crown a deciduous tree whose square hashes to treeHash has on it, 1 in
     * full leaf and 0 bare. Each tree drops and comes back into leaf on a day of its own, so a
     * street does not turn at once.
     */
    static float leafOn(int season, float progression, int treeHash) {
        float own = Flora.unit(treeHash, 0);
        switch (season) {
            case AUTUMN: {
                float from = FALL_EARLIEST + (FALL_LATEST - FALL_EARLIEST) * own;
                return 1f - smooth((progression - from) / FALL_TAKES);
            }
            case WINTER:
                return 0f;
            case SPRING: {
                float full = LEAF_EARLIEST + (LEAF_LATEST - LEAF_EARLIEST) * own;
                return smooth(progression / full);
            }
            default:
                return 1f;
        }
    }

    /** How much fallen leaf lies under deciduous trees now, 1 at its deepest at the end of autumn. */
    static float litter() {
        return litter(season, progression);
    }

    static float litter(int season, float progression) {
        switch (season) {
            case AUTUMN:
                return LITTER_SUMMER + (1f - LITTER_SUMMER) * smooth(progression / (FALL_LATEST + FALL_TAKES));
            case WINTER:
                return 1f;
            case SPRING:
                return LITTER_SUMMER + (1f - LITTER_SUMMER) * (1f - smooth(progression));
            default:
                return LITTER_SUMMER;
        }
    }

    private static float smooth(float t) {
        float c = Math.min(Math.max(t, 0f), 1f);
        return c * c * (3f - 2f * c);
    }
}
