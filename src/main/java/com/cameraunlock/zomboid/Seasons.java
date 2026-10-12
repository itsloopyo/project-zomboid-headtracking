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
     * How many steps a season is taken in by what a build holds: leafOn and turned of a tree
     * now, and stage. A build is kept while what it was made from is the same to the bit, and a
     * season read to the last bit would make every build another every frame.
     */
    static final int STEPS = 48;

    /** The season, in the high half, and the bits of how far through it, as one number: a thread reading both never has one of them a season behind the other. */
    private static volatile long now = (long) SUMMER << 32 | Float.floatToRawIntBits(0.5f);

    private Seasons() {}

    static void capture(ErosionSeason erosion) {
        now = (long) erosion.getSeason() << 32 | Float.floatToRawIntBits(Math.min(Math.max(erosion.getSeasonProgression(), 0f), 1f));
    }

    static int season() {
        return seasonOf(now);
    }

    /** How far through the season the game is, 0 to 1, as the game has it. */
    static float progression() {
        return progressionOf(now);
    }

    /** The season and how far through it as one number, for a thread that wants both of one moment: seasonOf and progressionOf take it apart. */
    static long now() {
        return now;
    }

    static int seasonOf(long now) {
        return (int) (now >>> 32);
    }

    static float progressionOf(long now) {
        return Float.intBitsToFloat((int) now);
    }

    /**
     * How far through the season a build takes the year to be: progression in whole STEPS,
     * and past where every tree has come to how it stays for the rest of the season (bare and
     * brown in autumn, in full leaf in spring) that point itself, so nothing is made again for
     * a change no tree shows.
     */
    static float stepped(int season, float progression) {
        float settled = settled(season);
        return progression >= settled ? settled : (float) Math.floor(progression * STEPS) / STEPS;
    }

    private static float settled(int season) {
        return season == AUTUMN ? FALL_LATEST + FALL_TAKES : season == SPRING ? LEAF_LATEST : 0f;
    }

    /** How much of its crown a deciduous tree has on it now, 1 in full leaf and 0 bare, as a build holds it: see stepped. */
    static float leafOn(int treeHash) {
        long at = now;
        int season = seasonOf(at);
        return leafOn(season, stepped(season, progressionOf(at)), treeHash);
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
        long at = now;
        return stage(seasonOf(at), progressionOf(at));
    }

    static int stage(int season, float progression) {
        if (season != AUTUMN && season != SPRING) {
            return season == WINTER ? -1 : 0;
        }
        return season * (STEPS + 2) + (progression >= settled(season) ? STEPS + 1 : (int) Math.floor(progression * STEPS));
    }

    /** The hash leafOn and turned take for the tree standing on a square. */
    static int treeHash(int x, int y) {
        return Flora.mix(x, y, 21);
    }

    /** How far a deciduous tree's leaves have turned now, as a build holds it: see turned(int, float, int) and stepped. */
    static float turned(int treeHash) {
        long at = now;
        int season = seasonOf(at);
        return turned(season, stepped(season, progressionOf(at)), treeHash);
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
        long at = now;
        return litter(seasonOf(at), progressionOf(at));
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
     * How much of its leaves a deciduous tree whose own number is own, 0 to 1, has on it on the
     * game's own schedule (ErosionCategory.currentSeason, with own as the square's magicNum):
     * all of them until a day of its own in the second half of autumn, none through winter, all
     * again from a day of its own in spring. What the mod's trees follow where the game's plants
     * are drawn as the game draws them (TreeQuality Low).
     */
    static float gameLeafOn(int season, float progression, float own) {
        switch (season) {
            case AUTUMN:
                return progression < 0.5f + 0.5f * own ? 1f : 0f;
            case WINTER:
                return 0f;
            case SPRING:
                return progression < own ? 0f : 1f;
            default:
                return 1f;
        }
    }

    /** How far a tree's leaves have turned on the game's own schedule: half way when the game shows its autumn picture, from a day of its own in the first half of autumn, and all the way bare. */
    static float gameTurned(int season, float progression, float own) {
        switch (season) {
            case AUTUMN:
                return (progression < 0.5f * own ? 0f : 0.5f) + (progression < 0.5f + 0.5f * own ? 0f : 0.5f);
            case WINTER:
                return 1f;
            default:
                return 0f;
        }
    }

    /**
     * leafOn and turned for a shader, and the game's own schedule of each, of a tree whose own
     * number is own, 0 to 1, where leafOn and turned take it from the hash's low eight bits: the
     * far trees', which are no tree of the map's and have no square's hash. Each function is a
     * run of "if (abs(season - N) < 0.5) { return ...; }" and a last return, which SeasonsTests
     * evaluates against the Java.
     */
    static final String GLSL = String.format(Locale.ROOT, """
            float seasonSmooth(float t) {
                return clamp(t, 0.0, 1.0) * clamp(t, 0.0, 1.0) * (3.0 - 2.0 * clamp(t, 0.0, 1.0));
            }

            float leafOn(float season, float progression, float own) {
                if (abs(season - %1$d.0) < 0.5) {
                    return 1.0 - seasonSmooth((progression - (%4$f + %5$f * own)) / %6$f);
                }
                if (abs(season - %2$d.0) < 0.5) {
                    return 0.0;
                }
                if (abs(season - %3$d.0) < 0.5) {
                    return seasonSmooth(progression / (%7$f + %8$f * own));
                }
                return 1.0;
            }

            float leafTurned(float season, float progression, float own) {
                if (abs(season - %1$d.0) < 0.5) {
                    return max(clamp((progression - (%4$f + %5$f * own - %9$f)) / %10$f, 0.0, 1.0), step(%4$f + %5$f * own + %6$f, progression));
                }
                if (abs(season - %2$d.0) < 0.5) {
                    return 1.0;
                }
                return 0.0;
            }

            float gameLeafOn(float season, float progression, float own) {
                if (abs(season - %1$d.0) < 0.5) {
                    return 1.0 - step(0.5 + 0.5 * own, progression);
                }
                if (abs(season - %2$d.0) < 0.5) {
                    return 0.0;
                }
                if (abs(season - %3$d.0) < 0.5) {
                    return step(own, progression);
                }
                return 1.0;
            }

            float gameTurned(float season, float progression, float own) {
                if (abs(season - %1$d.0) < 0.5) {
                    return 0.5 * step(0.5 * own, progression) + 0.5 * step(0.5 + 0.5 * own, progression);
                }
                if (abs(season - %2$d.0) < 0.5) {
                    return 1.0;
                }
                return 0.0;
            }
            """, AUTUMN, WINTER, SPRING, FALL_EARLIEST, FALL_LATEST - FALL_EARLIEST, FALL_TAKES, LEAF_EARLIEST, LEAF_LATEST - LEAF_EARLIEST,
            TURN_LEADS, TURN_LEADS + FALL_TAKES);

    private static float smooth(float t) {
        float c = Math.min(Math.max(t, 0f), 1f);
        return c * c * (3f - 2f * c);
    }
}
