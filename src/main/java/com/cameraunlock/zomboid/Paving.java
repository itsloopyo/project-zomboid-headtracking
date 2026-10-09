package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * Made ground against what is built. Terrain lets a street roll with the land and stands a
 * building level, and where the two touch, a pavement up to a shop's wall or a car park up to
 * a canopy, one of them has to give. Paving is laid: it is a plane or a gentle ramp, and it
 * does not hump up to a doorstep and down again. So here it is the land that gives.
 *
 * <ul>
 * <li>Broad made ground, a street with its pavements, a car park, a forecourt, a plaza, an
 * alley between walls, lies level at the footing of what is built against it, straight out
 * from the wall across to its far side, both kerbs alike. The land's own fall is taken up
 * beyond it, in the verge.</li>
 * <li>Past the end of the wall it eases to the height the land gives the street: a ramp, the
 * way a street comes down off a terrace. Between two footings at different heights it passes
 * from one to the other.</li>
 * <li>A narrow strip of made ground against a wall, a drive up to a garage or a path to a
 * door, is level at the wall and eases to the land's height over a few squares, and takes
 * nothing with it: the road a drive opens onto stays where the land has it.</li>
 * </ul>
 *
 * What holds broad made ground level is a wall or a canopy, a thing three squares across or
 * more. A sign on its posts or a booth holds no more than the strip that runs up to it.
 *
 * Works on the squares Terrain has gathered round a chunk, after them, and gives each corner
 * the same height whichever chunk asks. Logic thread.
 */
final class Paving {
    /** How far straight out from a wall broad made ground lies level at the wall's footing: across a street and its pavements. */
    static final int LEVEL_FOR = 12;
    /** Over how far from there, going over broad made ground, it eases to the land's height. */
    static final int EASED_OVER = 20;
    /** Over how far a narrow strip of made ground eases from the footing it runs up to. */
    static final int APRON = 4;
    /** Over how far a narrow strip of made ground eases from the broad made ground it opens onto: a side street or a drive off a street that is held. */
    static final int MOUTH = 8;
    /** Made ground is broad where a square of it this many squares to a side lies within one of made ground all over, twice this and one more to a side. */
    static final int BROAD = 3;
    /** How far from a corner the footings that hold it are looked for, either way: as far as made ground can carry the hold of one. */
    static final int HELD_FROM = Terrain.LEVELLED + EASED_OVER + LEVEL_FOR + 1;
    /** How far round a chunk Terrain has to gather for what is worked out here to come out the same from the next chunk. */
    static final int GATHERED = HELD_FROM + 2 * BROAD;

    private static final int SPAN = Terrain.SPAN;
    /** Going over made ground costs this a square along a side and DIAGONAL across a corner. */
    private static final int STRAIGHT = 5, DIAGONAL = 7;
    private static final int NEVER = Integer.MAX_VALUE;
    private static final int[] STEP_I = {1, -1, 0, 0}, STEP_J = {0, 0, 1, -1};

    /** Open made ground, and what is under something built that has made ground against it. */
    private static final boolean[] made = new boolean[SPAN * SPAN], front = new boolean[SPAN * SPAN];
    /** That of front which is stout enough to hold broad made ground level. */
    private static final boolean[] holds = new boolean[SPAN * SPAN];
    /** Broad made ground, and that of it which lies straight out from a wall. */
    private static final boolean[] broad = new boolean[SPAN * SPAN], level = new boolean[SPAN * SPAN];
    /** Running counts over the squares: of made ground or what is built, of squares with that all round them, of squares on a footing. */
    private static final int[] firm = new int[(SPAN + 1) * (SPAN + 1)], whole = new int[(SPAN + 1) * (SPAN + 1)], footed = new int[(SPAN + 1) * (SPAN + 1)];
    /** How far each square of broad made ground is from what lies level, going over broad made ground, and each square of made ground from a footing, going over any. */
    private static final int[] overBroad = new int[SPAN * SPAN], overMade = new int[SPAN * SPAN];
    /** How far each square of made ground is from broad made ground, going over made ground. */
    private static final int[] offBroad = new int[SPAN * SPAN];
    /** How far each square of footway is from the road it runs along, going over footway. */
    private static final int[] offRoad = new int[SPAN * SPAN];
    /** A footway is no wider than this, in squares: made ground further from a road is a yard. */
    static final int FOOTWAY = 6;
    /** The narrowest strip along a wall that is ruled from the wall's footing to what the road is held to, in squares. */
    private static final float RULED = 2f;
    private static final boolean[] road = new boolean[SPAN * SPAN], walked = new boolean[SPAN * SPAN];
    /** The footing each square of broad made ground that is held is held to: those round it, each by how near it is. */
    private static final float[] heldTo = new float[SPAN * SPAN];
    /** How far from a wall a corner goes by that wall's own footing alone, in squares: beyond, by what the street is held to from kerb to kerb. */
    private static final int BESIDE = Terrain.REACH;
    /** The squares still to go on from, in a list for each distance: the first of each list, and for each entry its square and the next. */
    private static final int[] firstAt = new int[Math.max(EASED_OVER, MOUTH) * STRAIGHT + 1];
    private static int[] waiting = new int[SPAN * SPAN], nextWaiting = new int[SPAN * SPAN];
    private static int waited;
    /** The squares of front, in the order they lie in, x first. */
    private static int[] fronts = new int[256];
    private static int frontCount;
    private static float[] pads;
    private static boolean[] footway;
    /** Whether nothing built has made ground against it, anywhere round the chunk. */
    private static boolean idle = true;

    /**
     * Dev only: the ground as it is with no made ground laid to what is built, to set beside
     * the ground as it is. Terrain and its footings go by it too, and every height kept is
     * one worked out the other way until Terrain.forget.
     */
    static boolean without = Boolean.getBoolean("cameraunlock.zomboid.withoutPaving");

    private Paving() {}

    /** No made ground is laid to anything in the heights asked for next: onto gives back what it is given. */
    static void rest() {
        idle = true;
    }

    /**
     * Looks over the squares Terrain has gathered: what each is, SPAN by SPAN with x first,
     * the footing each stands on or NaN, and whether it is a street's footway. The corners
     * that will be asked for are those numbered from to to, either way.
     */
    static void lay(byte[] around, float[] padsAround, boolean[] footwayAround, int from, int to) {
        pads = padsAround;
        footway = footwayAround;
        idle = true;
        if (without) {
            return;
        }
        for (int i = 0; i < SPAN; i++) {
            for (int j = 0; j < SPAN; j++) {
                int at = i * SPAN + j;
                byte kind = around[at];
                boolean isFooted = !Float.isNaN(padsAround[at]);
                made[at] = !isFooted && (kind == Terrain.STREET || kind == Terrain.MADE);
                // Ground that is not loaded counts as made, as it does in Terrain.
                boolean isFirm = isFooted || kind == Terrain.STREET || kind == Terrain.MADE || kind == Terrain.UNKNOWN;
                int into = (i + 1) * (SPAN + 1) + j + 1;
                firm[into] = (isFirm ? 1 : 0) + firm[into - 1] + firm[into - SPAN - 1] - firm[into - SPAN - 2];
                footed[into] = (isFooted ? 1 : 0) + footed[into - 1] + footed[into - SPAN - 1] - footed[into - SPAN - 2];
            }
        }
        frontCount = 0;
        for (int i = 0; i < SPAN; i++) {
            for (int j = 0; j < SPAN; j++) {
                int at = i * SPAN + j;
                front[at] = !Float.isNaN(padsAround[at]) && touches(made, i, j);
                holds[at] = front[at] && stout(i, j);
                if (front[at]) {
                    if (frontCount == fronts.length) {
                        fronts = Arrays.copyOf(fronts, frontCount * 2);
                    }
                    fronts[frontCount++] = at;
                }
            }
        }
        idle = frontCount == 0;
        if (idle) {
            return;
        }
        int across = 2 * BROAD + 1;
        for (int i = 0; i < SPAN; i++) {
            for (int j = 0; j < SPAN; j++) {
                boolean all = i >= BROAD && j >= BROAD && i < SPAN - BROAD && j < SPAN - BROAD && count(firm, i, j) == across * across;
                int into = (i + 1) * (SPAN + 1) + j + 1;
                whole[into] = (all ? 1 : 0) + whole[into - 1] + whole[into - SPAN - 1] - whole[into - SPAN - 2];
            }
        }
        for (int i = 0; i < SPAN; i++) {
            for (int j = 0; j < SPAN; j++) {
                broad[i * SPAN + j] = made[i * SPAN + j] && count(whole, i, j) > 0;
            }
        }
        Arrays.fill(level, false);
        for (int i = 0; i < SPAN; i++) {
            for (int j = 0; j < SPAN; j++) {
                if (!holds[i * SPAN + j]) {
                    continue;
                }
                for (int side = 0; side < 4; side++) {
                    for (int out = 1; out <= LEVEL_FOR; out++) {
                        int toI = i + STEP_I[side] * out, toJ = j + STEP_J[side] * out;
                        if (toI < 0 || toJ < 0 || toI >= SPAN || toJ >= SPAN || !broad[toI * SPAN + toJ]) {
                            break;
                        }
                        level[toI * SPAN + toJ] = true;
                    }
                }
            }
        }
        // No further than a corner asked for can be held from: a square within a street's width of it, and how far that square can be from what holds it.
        int reach = Terrain.LEVELLED + 1;
        spread(overBroad, level, broad, EASED_OVER * STRAIGHT, from - reach - EASED_OVER, to + reach + EASED_OVER);
        spread(overMade, front, made, APRON * STRAIGHT, from - 2 - APRON, to + 2 + APRON);
        spread(offBroad, broad, made, MOUTH * STRAIGHT, from - 2 - MOUTH, to + 2 + MOUTH);
        for (int at = 0; at < SPAN * SPAN; at++) {
            road[at] = broad[at] && !footway[at];
            walked[at] = made[at] && footway[at];
        }
        spread(offRoad, road, walked, FOOTWAY * STRAIGHT, from - 2 - FOOTWAY, to + 2 + FOOTWAY);
        for (int i = Math.max(0, from - reach); i <= Math.min(SPAN - 1, to + reach); i++) {
            for (int j = Math.max(0, from - reach); j <= Math.min(SPAN - 1, to + reach); j++) {
                if (overBroad[i * SPAN + j] != NEVER) {
                    // Twice over, to ask of the square's middle what is asked of a corner.
                    heldTo[i * SPAN + j] = (float) footing(2 * i + 1, 2 * j + 1, EASED_OVER + LEVEL_FOR + 1);
                }
            }
        }
    }

    private static double nearestFront;

    /**
     * The footing a place is held to: those of the squares of front within reach of it either
     * way, each by how near it is, so much so that the wall a place is beside is all but the
     * only one it hears. The place is (twiceI, twiceJ) in half squares. Leaves how far the
     * nearest of them is, squared, in nearestFront. In the order the squares lie in on the
     * map, so that two chunks add up the same numbers in the same order.
     */
    private static double footing(int twiceI, int twiceJ, int reach) {
        double footings = 0.0, weights = 0.0;
        nearestFront = Double.POSITIVE_INFINITY;
        // The squares of front lie in order of x: those within reach in x are a run of them, found by halving.
        int low = 0, high = frontCount;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (2 * (fronts[middle] / SPAN) + 1 - twiceI < -2 * reach) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        for (int next = low; next < frontCount; next++) {
            int di = 2 * (fronts[next] / SPAN) + 1 - twiceI, dj = 2 * (fronts[next] % SPAN) + 1 - twiceJ;
            if (di > 2 * reach) {
                break;
            }
            if (dj < -2 * reach || dj > 2 * reach) {
                continue;
            }
            // From the nearest point of the square, as a corner's distance to a square is counted in Terrain.
            double awayI = Math.max(0, Math.abs(di) - 1) / 2.0, awayJ = Math.max(0, Math.abs(dj) - 1) / 2.0;
            double away = awayI * awayI + awayJ * awayJ;
            double weight = 1.0 / (away * away);
            footings += pads[fronts[next]] * weight;
            weights += weight;
            nearestFront = Math.min(nearestFront, away);
        }
        return footings / weights;
    }

    /** How many squares of a running count are set within BROAD of (i, j), as far as the squares gathered go. */
    private static int count(int[] sums, int i, int j) {
        int fromI = Math.max(0, i - BROAD), toI = Math.min(SPAN, i + BROAD + 1), fromJ = Math.max(0, j - BROAD), toJ = Math.min(SPAN, j + BROAD + 1);
        return sums[toI * (SPAN + 1) + toJ] - sums[fromI * (SPAN + 1) + toJ] - sums[toI * (SPAN + 1) + fromJ] + sums[fromI * (SPAN + 1) + fromJ];
    }

    private static boolean touches(boolean[] what, int i, int j) {
        for (int di = -1; di <= 1; di++) {
            for (int dj = -1; dj <= 1; dj++) {
                int toI = i + di, toJ = j + dj;
                if (toI >= 0 && toJ >= 0 && toI < SPAN && toJ < SPAN && what[toI * SPAN + toJ]) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the footed square (i, j) is one of three by three, all footed. A sign on its
     * posts or a booth stands at the height the ground has where it is and holds no street
     * to it: paving is laid to a wall or a canopy, not to a post.
     */
    private static boolean stout(int i, int j) {
        for (int midI = Math.max(1, i - 1); midI <= Math.min(SPAN - 2, i + 1); midI++) {
            for (int midJ = Math.max(1, j - 1); midJ <= Math.min(SPAN - 2, j + 1); midJ++) {
                int low = (midI - 1) * (SPAN + 1) + midJ - 1, high = (midI + 2) * (SPAN + 1) + midJ + 2;
                if (footed[high] - footed[low + 3] - footed[high - 3] + footed[low] == 9) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Fills far with how far each square of through is from the nearest square of from, going
     * over through, and NEVER past limit. Only the squares numbered low to high, either way,
     * are gone over or started from: no other is asked about.
     */
    private static void spread(int[] far, boolean[] from, boolean[] through, int limit, int low, int high) {
        low = Math.max(0, low);
        high = Math.min(SPAN - 1, high);
        Arrays.fill(far, NEVER);
        Arrays.fill(firstAt, 0, limit + 1, -1);
        waited = 0;
        for (int i = low; i <= high; i++) {
            for (int j = low; j <= high; j++) {
                if (from[i * SPAN + j]) {
                    far[i * SPAN + j] = 0;
                    wait(i * SPAN + j, 0);
                }
            }
        }
        for (int gone = 0; gone <= limit; gone++) {
            for (int entry = firstAt[gone]; entry != -1; entry = nextWaiting[entry]) {
                int at = waiting[entry];
                // A square reached again by a shorter way has been gone on from already.
                if (far[at] != gone) {
                    continue;
                }
                int i = at / SPAN, j = at % SPAN;
                for (int di = -1; di <= 1; di++) {
                    for (int dj = -1; dj <= 1; dj++) {
                        int toI = i + di, toJ = j + dj;
                        if (toI < low || toJ < low || toI > high || toJ > high || !through[toI * SPAN + toJ]) {
                            continue;
                        }
                        int to = toI * SPAN + toJ, going = gone + (di != 0 && dj != 0 ? DIAGONAL : STRAIGHT);
                        if (going <= limit && going < far[to]) {
                            far[to] = going;
                            wait(to, going);
                        }
                    }
                }
            }
        }
    }

    private static void wait(int at, int far) {
        if (waited == waiting.length) {
            waiting = Arrays.copyOf(waiting, waited * 2);
            nextWaiting = Arrays.copyOf(nextWaiting, waited * 2);
        }
        waiting[waited] = at;
        nextWaiting[waited] = firstAt[far];
        firstAt[far] = waited++;
    }

    /**
     * The height of the corner (cornerI, cornerJ) of the squares gathered, which stands on no
     * footing itself and which the land would have at height: as much nearer the footings
     * round it as made ground holds it to them. After lay.
     */
    static float onto(float height, int cornerI, int cornerJ) {
        if (idle) {
            return height;
        }
        float strip = 0f;
        for (int di = -1; di <= 0; di++) {
            for (int dj = -1; dj <= 0; dj++) {
                int i = cornerI + di, j = cornerJ + dj;
                // Not of broad made ground that is held: that goes by what it is held to, further down.
                if (i >= 0 && j >= 0 && i < SPAN && j < SPAN && overMade[i * SPAN + j] != NEVER && overBroad[i * SPAN + j] == NEVER) {
                    strip = Math.max(strip, 1f - Terrain.smooth(overMade[i * SPAN + j] / (float) (APRON * STRAIGHT)));
                }
            }
        }
        // How far broad made ground has eased and what it is held to, taken over all the road within a street's width of
        // the corner, as Terrain takes a street's own height: both kerbs take it over the same squares, so a road is held
        // as high at one kerb as at the other. Over all the broad made ground where there is no road, a square or a yard.
        float eased = 0f, across = 0f, easedRoad = 0f, acrossRoad = 0f, nearest = Terrain.REACH, toFootway = Float.POSITIVE_INFINITY;
        int broads = 0, roads = 0, mouth = NEVER, toRoad = NEVER, offLevel = NEVER;
        boolean laid = false, onRoad = false, heldHere = false;
        for (int di = -1; di <= 0; di++) {
            for (int dj = -1; dj <= 0; dj++) {
                int i = cornerI + di, j = cornerJ + dj;
                if (i < 0 || j < 0 || i >= SPAN || j >= SPAN || !made[i * SPAN + j]) {
                    continue;
                }
                int at = i * SPAN + j;
                laid = true;
                mouth = Math.min(mouth, offBroad[at]);
                heldHere |= overBroad[at] != NEVER;
                offLevel = Math.min(offLevel, overBroad[at]);
                onRoad |= broad[at] && !footway[at];
                toRoad = Math.min(toRoad, offRoad[at]);
            }
        }
        for (int di = -Terrain.LEVELLED; di < Terrain.LEVELLED; di++) {
            for (int dj = -Terrain.LEVELLED; dj < Terrain.LEVELLED; dj++) {
                int i = cornerI + di, j = cornerJ + dj;
                if (i < 0 || j < 0 || i >= SPAN || j >= SPAN || !broad[i * SPAN + j]) {
                    continue;
                }
                int at = i * SPAN + j;
                float awayI = di >= 0 ? di : -di - 1, awayJ = dj >= 0 ? dj : -dj - 1;
                float away = (float) Math.sqrt(awayI * awayI + awayJ * awayJ);
                float here = overBroad[at] == NEVER ? 0f : 1f - Terrain.smooth(overBroad[at] / (float) (EASED_OVER * STRAIGHT));
                broads++;
                eased += here;
                across += here == 0f ? 0f : here * heldTo[at];
                nearest = Math.min(nearest, away);
                if (footway[at]) {
                    toFootway = Math.min(toFootway, away);
                } else {
                    roads++;
                    easedRoad += here;
                    acrossRoad += here == 0f ? 0f : here * heldTo[at];
                }
            }
        }
        // A corner on a road goes by the road alone, and one on the footway that runs along it more by the road the nearer
        // it is: FOOTWAY squares from any road it is a yard, and goes by all the broad made ground round it.
        float byRoad = onRoad ? 1f : toRoad == NEVER ? 0f : 1f - Terrain.smooth(toRoad / (float) (FOOTWAY * STRAIGHT));
        // The verge beside it comes with it, less the further off it is. A narrow strip of made ground comes with it only where
        // it opens onto it, and eases along its own length: a lane that runs past a held yard behind a verge keeps its own height,
        // both kerbs alike, and the verge between them is the bank.
        float with = nearest == 0f ? 1f : !laid ? 1f - Terrain.smooth(nearest / Terrain.REACH) : mouth == NEVER ? 0f : 1f - Terrain.smooth(mouth / (float) (MOUTH * STRAIGHT));
        boolean held = eased != 0f && with != 0f;
        if (strip == 0f && !held && !heldHere) {
            return height;
        }
        float beside = (float) footing(2 * cornerI, 2 * cornerJ, HELD_FROM);
        height += (beside - height) * strip;
        if (held) {
            float asYard = height + (across / eased - height) * (eased / broads * with);
            float asRoad = easedRoad == 0f ? height : height + (acrossRoad / easedRoad - height) * (easedRoad / roads * with);
            height = asYard + (asRoad - asYard) * byRoad;
        }
        if (!heldHere) {
            return height;
        }
        // What is left between the wall's footing and what the road is held to, where two footings across a street are at
        // different heights or the road is held to none, is the footway's: it is ruled from the wall to the road's edge, and
        // the road keeps one height from kerb to kerb. A footway a square wide cannot take it without standing on end, so
        // the ruled strip is never narrower than RULED and the edge of the road shares it. A road that runs right up to a
        // wall, with no footway between, has to take it itself, and so has a yard.
        float toWall = (float) Math.sqrt(nearestFront);
        float alone = 1f - Terrain.smooth(toWall / BESIDE);
        // Past the end of the wall there is no wall to rule from: the footway goes to the road's height over a few squares, and stands no ridge between the road and the verge.
        float beyond = offLevel == NEVER ? 0f : 1f - Terrain.smooth(offLevel / (float) (FOOTWAY * STRAIGHT));
        float ruled = onRoad ? toFootway <= toWall ? Math.max(0f, 1f - toWall / RULED) : alone
                : toRoad != NEVER ? (1f - toWall / Math.max(RULED, toWall + toRoad / (float) STRAIGHT)) * beyond : alone;
        return height + (beside - height) * (alone + (ruled - alone) * byRoad);
    }
}
