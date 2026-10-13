package com.cameraunlock.zomboid;

import java.util.Locale;

/**
 * Leaves coming off the broad-leaved trees round the eye in autumn: each lets go from somewhere
 * in its tree's crown, the colour that crown has turned, falls fluttering, swinging from side to
 * side and spinning, is carried by the wind, lands and lies a few seconds, and fades into the
 * litter already on the ground. One of these for each view, worked on the logic thread.
 *
 * Where a leaf is and how it is turned are worked out from how long it has been falling, for the
 * swing and the turn, and for the drift by the exact motion of something the air drags towards
 * its own speed over the elapsed time: a frame of any length moves it to where frames of any
 * other length would have.
 */
final class LeafFall {
    /** How far from the eye a tree sheds leaves that are drawn, and past what a leaf carried off is let go, in tiles. */
    static final float REACH = 32f, KEPT = REACH + 8f;
    /** The most leaves in the air and lying at once, and the most trees that shed them. */
    static final int MOST = 768, MOST_TREES = 512;
    /**
     * One leaf as a frame carries it to the picture, FLOATS long: its middle, the half of its
     * length and the half of its breadth as ways in the world, the colour its tree turns
     * (Flora's hues), how far it has turned (0 green, 1 brown) and how much of it shows.
     */
    static final int X = 0, Y = 1, UP = 2, ALONG_X = 3, ALONG_Y = 4, ALONG_UP = 5, ACROSS_X = 6, ACROSS_Y = 7, ACROSS_UP = 8, HUE = 9, TURNED = 10, SHOWN = 11;
    static final int FLOATS = 12;
    /** Leaves a second a tree lets go of in still air as its crown thins fastest, and the share of that while its leaves only turn. */
    static final float PEAK = 1.5f, TRICKLE = 0.12f;
    /** How long a landed leaf lies before it fades, the least and the most, and how long it fades over, in seconds. */
    static final float LIES_LEAST = 3f, LIES_MOST = 7f, FADES = 1.5f;
    /** How long the air takes to bring a leaf to its own speed, in seconds. */
    static final float DRAG = 0.5f;
    /** A leaf's half length, the least and the most, in tiles, and its breadth over its length. */
    private static final float HALF_LEAST = 0.045f, HALF_MOST = 0.07f, BROAD = 0.65f;
    /** A crown, from the ground, its least and its most height and how far out it reaches, in tiles; and a jumbo tree's. */
    private static final float CROWN_LOW = 1.8f, CROWN_HIGH = 5f, CROWN_REACH = 1.1f, JUMBO_LOW = 2.6f, JUMBO_HIGH = 7.5f, JUMBO_REACH = 1.8f;
    /** A leaf that has fallen to this near the ground asks what the ground is under it again, in tiles. */
    private static final float GROUND_ASKED = 0.6f;
    /** The longest step taken at once, in seconds: past it the game has hitched, and the leaves wait. */
    static final float STEP_MOST = 0.25f;
    /** An eye that moves further than this between two steps has jumped: its leaves are let go. */
    private static final float JUMPED = 40f;

    /** The ground under the leaves and what stands on it. */
    interface Ground {
        /** The height of the ground at (x, y), in tiles. */
        float height(float x, float y);

        /** Whether a leaf cannot be on the square: a building's inside, behind its walls. */
        boolean walled(int x, int y);
    }

    // The trees that shed, set whole by trees().
    private final float[] treeX = new float[MOST_TREES], treeY = new float[MOST_TREES], treeGround = new float[MOST_TREES], owed = new float[MOST_TREES];
    private final int[] treeHash = new int[MOST_TREES];
    private final byte[] treeHue = new byte[MOST_TREES];
    private final boolean[] treeJumbo = new boolean[MOST_TREES];
    private int trees;

    // Each leaf: where its drift has got to, how fast it drifts, how long it has fallen and how long since it landed (or below 0 in the air).
    private final float[] x = new float[MOST], y = new float[MOST], up = new float[MOST], vx = new float[MOST], vy = new float[MOST];
    private final float[] age = new float[MOST], landed = new float[MOST], lies = new float[MOST], ground = new float[MOST];
    // Its own: how fast it falls, its swing's reach, rate, start and way, its spin, whether it tumbles over and over, its size, colour and turn.
    private final float[] fall = new float[MOST], swing = new float[MOST], rate = new float[MOST], phase = new float[MOST], way = new float[MOST];
    private final float[] spin = new float[MOST], turn = new float[MOST], tumble = new float[MOST], half = new float[MOST], turned = new float[MOST];
    private final byte[] hue = new byte[MOST];
    private final int[] square = new int[MOST];
    private int count;
    private int random = 0x2545F491;
    private float lastEyeX = Float.NaN, lastEyeY = Float.NaN;
    /** Counted since the sim was made: leaves let go of by trees, and leaves landed. */
    long spawned, landings;

    /** Replaces the trees that shed: tree i stands at (xs[i], ys[i]) on ground grounds[i], hashes to hashes[i] (Seasons.treeHash), turns hues[i], and is a jumbo tree if jumbos[i]. */
    void trees(int count, float[] xs, float[] ys, float[] grounds, int[] hashes, byte[] hues, boolean[] jumbos) {
        if (count > MOST_TREES) {
            throw new IllegalArgumentException("more trees than " + MOST_TREES + ": " + count);
        }
        for (int i = 0; i < count; i++) {
            treeX[i] = xs[i];
            treeY[i] = ys[i];
            treeGround[i] = grounds[i];
            treeHash[i] = hashes[i];
            treeHue[i] = hues[i];
            treeJumbo[i] = jumbos[i];
            // A start of its own between nothing and one leaf owed, so a tree sheds as often as it should whenever the list is made again.
            owed[i] = next();
        }
        trees = count;
    }

    int trees() {
        return trees;
    }

    int count() {
        return count;
    }

    void clear() {
        count = 0;
    }

    /** Whether a tree whose sprite is named so drops its leaves in autumn: no evergreen, and no dead tree, which has none. */
    static boolean sheds(String name) {
        return name != null && !Flora.evergreen(name) && !Flora.deadTree(name);
    }

    /**
     * How many leaves a second a deciduous tree whose square hashes to treeHash lets go of in
     * still air: none outside autumn, a few while its leaves turn, most as its crown thins
     * fastest, and none once it is bare.
     */
    static float shedding(int season, float progression, int treeHash) {
        if (season != Seasons.AUTUMN) {
            return 0f;
        }
        float on = Seasons.leafOn(season, progression, treeHash);
        if (on <= 0f || Seasons.turned(season, progression, treeHash) <= 0f) {
            return 0f;
        }
        // leafOn is 1 less a smoothstep: its slope, 6t(1 - t), is 4 * on * (1 - on) to within a few hundredths, and peaks at 1.
        return PEAK * Math.max(TRICKLE, 4f * on * (1f - on));
    }

    /** How many times as many leaves the wind takes as still air would let fall, by the game's wind intensity, 0 still to 1 at 120 km/h. */
    static float letGo(float intensity) {
        return 0.5f + 3.5f * smooth(intensity / 0.5f);
    }

    /** How much of the air's speed a falling leaf takes on: little in the lightest air, near the ground and among trees, and more of a strong wind. */
    static float carried(float intensity) {
        return 0.15f + 0.45f * smooth((intensity - 0.05f) / 0.35f);
    }

    /**
     * Moves every leaf on by elapsed seconds, in which the air moves at (airX, airY) tiles a
     * second, the game's wind is of intensity (see letGo), and the year is at season and
     * progression; lets go of new leaves from the trees and of old ones that have faded, been
     * carried past KEPT of the eye at (eyeX, eyeY) or blown into a building.
     */
    void step(float elapsed, float airX, float airY, float intensity, int season, float progression, float eyeX, float eyeY, Ground on) {
        if (!Float.isNaN(lastEyeX) && Math.hypot(eyeX - lastEyeX, eyeY - lastEyeY) > JUMPED) {
            count = 0;
        }
        lastEyeX = eyeX;
        lastEyeY = eyeY;
        float dt = Math.min(elapsed, STEP_MOST);
        if (dt <= 0f) {
            return;
        }
        float take = carried(intensity);
        float driftX = airX * take, driftY = airY * take;
        for (int i = 0; i < count; ) {
            if (move(i, dt, driftX, driftY, eyeX, eyeY, on)) {
                i++;
            } else {
                drop(i);
            }
        }
        float windy = Math.clamp(intensity / 0.5f, 0f, 1f), more = letGo(intensity);
        for (int t = 0; t < trees; t++) {
            float dx = treeX[t] - eyeX, dy = treeY[t] - eyeY;
            if (dx * dx + dy * dy > REACH * REACH) {
                continue;
            }
            float rate = shedding(season, progression, treeHash[t]);
            if (rate <= 0f) {
                continue;
            }
            owed[t] += rate * more * dt;
            while (owed[t] >= 1f) {
                owed[t] -= 1f;
                spawn(t, season, progression, windy, driftX, driftY);
            }
        }
    }

    /** Moves leaf i on by dt; false once it is gone. */
    private boolean move(int i, float dt, float driftX, float driftY, float eyeX, float eyeY, Ground on) {
        if (landed[i] >= 0f) {
            landed[i] += dt;
            return landed[i] < lies[i] + FADES;
        }
        float k = (float) Math.exp(-dt / DRAG);
        // Falls to the ground within the step: only as much of the step as that takes moves it.
        float into = dt;
        if (up[i] - fall[i] * dt <= ground[i] + GROUND_ASKED) {
            ground[i] = on.height(x[i], y[i]);
            if (up[i] - fall[i] * dt <= ground[i]) {
                into = Math.max((up[i] - ground[i]) / fall[i], 0f);
                k = (float) Math.exp(-into / DRAG);
            }
        }
        x[i] += driftX * into + (vx[i] - driftX) * DRAG * (1f - k);
        y[i] += driftY * into + (vy[i] - driftY) * DRAG * (1f - k);
        vx[i] = driftX + (vx[i] - driftX) * k;
        vy[i] = driftY + (vy[i] - driftY) * k;
        up[i] -= fall[i] * into;
        age[i] += into;
        if (into < dt) {
            up[i] = ground[i];
            landed[i] = dt - into;
            landings++;
        }
        float fromX = x[i] - eyeX, fromY = y[i] - eyeY;
        if (fromX * fromX + fromY * fromY > KEPT * KEPT) {
            return false;
        }
        int sx = (int) Math.floor(x[i]), sy = (int) Math.floor(y[i]);
        int at = sx * 73856093 ^ sy * 19349663;
        if (at != square[i]) {
            square[i] = at;
            return !on.walled(sx, sy);
        }
        return true;
    }

    private void drop(int i) {
        int last = --count;
        if (i == last) {
            return;
        }
        x[i] = x[last];
        y[i] = y[last];
        up[i] = up[last];
        vx[i] = vx[last];
        vy[i] = vy[last];
        age[i] = age[last];
        landed[i] = landed[last];
        lies[i] = lies[last];
        ground[i] = ground[last];
        fall[i] = fall[last];
        swing[i] = swing[last];
        rate[i] = rate[last];
        phase[i] = phase[last];
        way[i] = way[last];
        spin[i] = spin[last];
        turn[i] = turn[last];
        tumble[i] = tumble[last];
        half[i] = half[last];
        turned[i] = turned[last];
        hue[i] = hue[last];
        square[i] = square[last];
    }

    /** Lets a leaf go from tree t's crown. When every place is taken, a lying leaf gives up its place, or none is let go. */
    private void spawn(int t, int season, float progression, float windy, float driftX, float driftY) {
        int i = count;
        if (i == MOST) {
            i = -1;
            float longest = -1f;
            for (int j = 0; j < MOST; j++) {
                if (landed[j] > longest) {
                    longest = landed[j];
                    i = j;
                }
            }
            if (longest < 0f) {
                return;
            }
        } else {
            count++;
        }
        boolean jumbo = treeJumbo[t];
        float reach = jumbo ? JUMBO_REACH : CROWN_REACH, low = jumbo ? JUMBO_LOW : CROWN_LOW, high = jumbo ? JUMBO_HIGH : CROWN_HIGH;
        // From the outer part of the crown, which is where the wind gets at the leaves.
        double angle = 2.0 * Math.PI * next();
        float out = reach * (float) Math.sqrt(0.3f + 0.7f * next());
        x[i] = treeX[t] + out * (float) Math.cos(angle);
        y[i] = treeY[t] + out * (float) Math.sin(angle);
        up[i] = treeGround[t] + low + (high - low) * next();
        ground[i] = treeGround[t];
        vx[i] = driftX * 0.3f;
        vy[i] = driftY * 0.3f;
        age[i] = 0f;
        landed[i] = -1f;
        lies[i] = LIES_LEAST + (LIES_MOST - LIES_LEAST) * next();
        // The wind sets more of them tumbling over and over, which fall faster and swing less.
        boolean tumbles = next() < 0.2f + 0.6f * windy;
        fall[i] = tumbles ? 1.1f + 0.6f * next() : 0.55f + 0.5f * next();
        swing[i] = (tumbles ? 0.06f : 0.15f + 0.3f * next()) * (1f - 0.5f * windy);
        rate[i] = (float) (2.0 * Math.PI / (1.3f + 1.3f * next()));
        phase[i] = (float) (2.0 * Math.PI * next());
        way[i] = (float) (2.0 * Math.PI * next());
        spin[i] = (next() - 0.5f) * 5f;
        turn[i] = (float) (2.0 * Math.PI * next());
        tumble[i] = tumbles ? (next() < 0.5f ? -1f : 1f) * (6f + 8f * next()) : 0f;
        half[i] = HALF_LEAST + (HALF_MOST - HALF_LEAST) * next();
        hue[i] = treeHue[t];
        turned[i] = Math.clamp(Seasons.turned(season, progression, treeHash[t]) + 0.35f * (next() - 0.4f), 0f, 1f);
        square[i] = Integer.MIN_VALUE;
        spawned++;
    }

    /**
     * Writes the leaves within KEPT of the eye as the picture takes them (see FLOATS) into
     * into, which holds MOST, and gives how many.
     */
    int write(float[] into, float eyeX, float eyeY) {
        int written = 0;
        for (int i = 0; i < count; i++) {
            float fromX = x[i] - eyeX, fromY = y[i] - eyeY;
            float far = (float) Math.sqrt(fromX * fromX + fromY * fromY);
            float shown = (1f - smooth((far - REACH) / (KEPT - REACH))) * smooth(age[i] / 0.2f);
            if (landed[i] >= 0f) {
                shown *= 1f - smooth((landed[i] - lies[i]) / FADES);
            }
            if (shown <= 0f) {
                continue;
            }
            int at = written++ * FLOATS;
            pose(i, into, at);
            into[at + HUE] = hue[i];
            into[at + TURNED] = turned[i];
            into[at + SHOWN] = shown;
        }
        return written;
    }

    /** Where leaf i is and how it is turned, into its record at at. */
    private void pose(int i, float[] into, int at) {
        // Its swing, its spin and how it rocks or tumbles are of how long it has been falling: on the ground, of when it landed.
        float t = age[i];
        float swung = rate[i] * t + phase[i];
        float wayNow = way[i] + 0.25f * t;
        float dx = (float) Math.cos(wayNow), dy = (float) Math.sin(wayNow);
        float s = (float) Math.sin(swung), c = (float) Math.cos(swung);
        float cx = x[i] + dx * swing[i] * s, cy = y[i] + dy * swing[i] * s;
        // At either end of its swing a leaf glides up a little, as a pendulum's bob does.
        float cz = up[i] + 0.3f * swing[i] * s * s;
        float rock;
        if (landed[i] >= 0f) {
            cz = up[i] + 0.004f;
            rock = 0.12f * (float) Math.sin(turn[i] * 3f);
        } else if (tumble[i] != 0f) {
            rock = tumble[i] * t + turn[i];
        } else {
            // Tilted along the way it swings, the most as it passes the middle.
            rock = 0.6f * c;
        }
        // The leaf's face: up, tipped towards the way it swings by rock.
        float sr = (float) Math.sin(rock), cr = (float) Math.cos(rock);
        float nx = dx * sr, ny = dy * sr, nz = cr;
        // e lies level across the swing, f in the face square to it.
        float ex = -dy, ey = dx;
        float fx = ey * nz, fy = -ex * nz, fz = ex * ny - ey * nx;
        float spun = turn[i] + spin[i] * t;
        float ss = (float) Math.sin(spun), cs = (float) Math.cos(spun);
        float length = half[i], breadth = half[i] * BROAD;
        into[at + X] = cx;
        into[at + Y] = cy;
        into[at + UP] = cz;
        into[at + ALONG_X] = (ex * cs + fx * ss) * length;
        into[at + ALONG_Y] = (ey * cs + fy * ss) * length;
        into[at + ALONG_UP] = fz * ss * length;
        into[at + ACROSS_X] = (-ex * ss + fx * cs) * breadth;
        into[at + ACROSS_Y] = (-ey * ss + fy * cs) * breadth;
        into[at + ACROSS_UP] = fz * cs * breadth;
    }

    /** What the leaves are now, for a log line or a probe. */
    String describe() {
        int lying = 0;
        for (int i = 0; i < count; i++) {
            lying += landed[i] >= 0f ? 1 : 0;
        }
        return String.format(Locale.ROOT, "leaves %d (%d lying) from %d trees, %d let go and %d landed in all", count, lying, trees, spawned, landings);
    }

    /** 0 to 1, from the xorshift kept here: the same leaves for the same steps. */
    private float next() {
        random ^= random << 13;
        random ^= random >>> 17;
        random ^= random << 5;
        return (random >>> 8) / (float) (1 << 24);
    }

    private static float smooth(float t) {
        float c = Math.clamp(t, 0f, 1f);
        return c * c * (3f - 2f * c);
    }
}
