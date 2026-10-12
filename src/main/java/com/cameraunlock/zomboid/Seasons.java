package com.cameraunlock.zomboid;

import java.util.Locale;
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
    /** How much of autumn before a tree starts to drop its leaves they start to turn. */
    private static final float TURN_LEADS = 0.35f;
    /**
     * How many steps a season is taken in. Every build the season shapes is made again when it
     * moves on a step (stage), and a build is kept while what it was made from is the same to
     * the bit: a season read to the last bit would make every build another every frame.
     */
    static final int STEPS = 48;

    private static volatile int season = SUMMER;
    private static volatile float progression = 0.5f;

    private Seasons() {}

    static void capture(ErosionSeason erosion) {
        season = erosion.getSeason();
        progression = (float) Math.floor(Math.min(Math.max(erosion.getSeasonProgression(), 0f), 1f) * STEPS) / STEPS;
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

    /**
     * What the season shapes, as a number that is another whenever any of it is: the steps
     * through autumn and spring, and winter's bare trees against summer's full ones.
     */
    static int stage() {
        return stage(season, progression);
    }

    static int stage(int season, float progression) {
        return season == AUTUMN || season == SPRING ? season * (STEPS + 1) + Math.round(progression * STEPS) : season == WINTER ? -1 : 0;
    }

    /** The hash leafOn and turned take for the tree standing on a square. */
    static int treeHash(int x, int y) {
        return Flora.mix(x, y, 21);
    }


    /** How far a deciduous tree's leaves have turned now: see turned(int, float, int). */
    static float turned(int treeHash) {
        return turned(season, progression, treeHash);
    }

    /**
     * How far the leaves of a deciduous tree whose square hashes to treeHash have turned, 0
     * green and 1 brown as they fall: they start to turn TURN_LEADS of autumn before the tree
     * starts to drop them, and are brown as the last of them falls. Spring's new leaves are green.
     */
    static float turned(int season, float progression, int treeHash) {
        switch (season) {
            case AUTUMN: {
                if (leafOn(season, progression, treeHash) == 0f) {
                    return 1f;
                }
                float from = FALL_EARLIEST + (FALL_LATEST - FALL_EARLIEST) * Flora.unit(treeHash, 0) - TURN_LEADS;
                return Math.min(Math.max((progression - from) / (TURN_LEADS + FALL_TAKES), 0f), 1f);
            }
            case WINTER:
                return 1f;
            default:
                return 0f;
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

    /**
     * leafOn and turned for a shader, of a tree whose own number is own, 0 to 1, where leafOn
     * and turned take it from the hash's low eight bits: the far trees', which are no tree of
     * the map's and have no square's hash.
     */
    static final String GLSL = String.format(Locale.ROOT, """
            float seasonSmooth(float t) {
                float c = clamp(t, 0.0, 1.0);
                return c * c * (3.0 - 2.0 * c);
            }

            float leafOn(float season, float progression, float own) {
                if (abs(season - %d.0) < 0.5) {
                    return 1.0 - seasonSmooth((progression - (%f + %f * own)) / %f);
                }
                if (abs(season - %d.0) < 0.5) {
                    return 0.0;
                }
                if (abs(season - %d.0) < 0.5) {
                    return seasonSmooth(progression / (%f + %f * own));
                }
                return 1.0;
            }

            float leafTurned(float season, float progression, float own) {
                if (abs(season - %d.0) < 0.5) {
                    return clamp((progression - (%f + %f * own - %f)) / %f, 0.0, 1.0);
                }
                return abs(season - %d.0) < 0.5 ? 1.0 : 0.0;
            }
            """, AUTUMN, FALL_EARLIEST, FALL_LATEST - FALL_EARLIEST, FALL_TAKES, WINTER, SPRING, LEAF_EARLIEST, LEAF_LATEST - LEAF_EARLIEST,
            AUTUMN, FALL_EARLIEST, FALL_LATEST - FALL_EARLIEST, TURN_LEADS, TURN_LEADS + FALL_TAKES, WINTER);

    private static float smooth(float t) {
        float c = Math.min(Math.max(t, 0f), 1f);
        return c * c * (3f - 2f * c);
    }
}
