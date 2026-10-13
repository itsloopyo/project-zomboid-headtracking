package com.cameraunlock.zomboid;

import java.util.Arrays;
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
    static final float REACH = 32f, KEPT = REACH + 8f, NEAR = 10f;
    /** The most leaves in the air and lying at once, the most trees that shed them, the nearest, and the most offered in a read of the square round the eye. */
    static final int MOST = 1024, MOST_TREES = 512, CANDIDATES = (2 * (int) REACH + 1) * (2 * (int) REACH + 1);
    /**
     * One leaf as a frame carries it to the picture, FLOATS long: its middle, the half of its
     * length and the half of its breadth as ways in the world, the colour its tree turns
     * (Flora's hues), how far it has turned (0 green, 1 brown) and how much of it shows.
     */
    static final int X = 0, Y = 1, UP = 2, ALONG_X = 3, ALONG_Y = 4, ALONG_UP = 5, ACROSS_X = 6, ACROSS_Y = 7, ACROSS_UP = 8, HUE = 9, TURNED = 10, SHOWN = 11;
    static final int FLOATS = 12;
    /** Leaves a second a tree lets go of in still air as its crown thins fastest, and the share of that once its leaves have turned and before it thins. */
    static final float PEAK = 4f, TRICKLE = 0.3f;
    /** How long a landed leaf lies before it fades, the least and the most, and how long it fades over, in seconds. */
    static final float LIES_LEAST = 3f, LIES_MOST = 7f, FADES = 1.5f;
    /** How long the air takes to bring a leaf to its own speed, in seconds. */
    static final float DRAG = 0.5f;
    /** A leaf's half length, the least and the most, in tiles, and its breadth over its length. */
    private static final float HALF_LEAST = 0.045f, HALF_MOST = 0.07f, BROAD = 0.65f;
    /** How far out into its crown's reach a leaf lets go from, at the most. */
    private static final float OUT_MOST = 0.85f;
    /** A leaf this near what it last found under it asks again each step, in tiles: more than the steepest roof climbs across a tile (Roofs.STEEPEST, 0.55 levels), so a slope rising under a drifting leaf is met. */
    private static final float GROUND_ASKED = 1.5f;
    /** The longest step taken at once, in seconds: past it the game has hitched, and the leaves wait. */
    static final float STEP_MOST = 0.25f;
    /** How many lying leaves room is never made by fading, and how long a leaf lies before it may be faded for room, in seconds. */
    static final int CARPET = MOST / 4;
    static final float PRESSED_AFTER = 1.5f;
    /** How much of a leaf showing in one step and gone in the next has vanished, rather than faded or been carried off. */
    private static final float SHOWING = 0.5f;
    /** An eye that moves further than this between two steps has jumped: its leaves are let go. */
    private static final float JUMPED = 40f;

    /** The ground under the leaves and what stands on it. */
    interface Ground {
        /** The height of the land at (x, y), in tiles: what the buildings stand on. */
        float ground(float x, float y);

        /**
         * The height of what a leaf at height up over (x, y) comes down onto, in tiles: the roof
         * whose storey it is in or is over, as the picture draws the roof, even where the leaf is
         * already inside it, or else the land.
         */
        float surface(float x, float y, float up);

        /** Whether a leaf at that height cannot be on the square: inside a storey of a building, behind its walls, the storey counted (see storey) from the land in the middle of the square. */
        boolean walled(int x, int y, float up);
    }

    // The trees that shed, nearest first, set whole by endTrees: where each stands, its crown's lowest and highest leaves over the ground and how far out they reach.
    private final float[] treeX = new float[MOST_TREES], treeY = new float[MOST_TREES], treeGround = new float[MOST_TREES], owed = new float[MOST_TREES];
    private final float[] treeLow = new float[MOST_TREES], treeHigh = new float[MOST_TREES], treeReach = new float[MOST_TREES];
    private final int[] treeHash = new int[MOST_TREES], released = new int[MOST_TREES];
    private final byte[] treeHue = new byte[MOST_TREES];
    private int trees;
    // The trees offered since beginTrees, and the eye they are offered round.
    private final float[] offeredX = new float[CANDIDATES], offeredY = new float[CANDIDATES], offeredGround = new float[CANDIDATES];
    private final float[] offeredLow = new float[CANDIDATES], offeredHigh = new float[CANDIDATES], offeredReach = new float[CANDIDATES];
    private final int[] offeredHash = new int[CANDIDATES];
    private final byte[] offeredHue = new byte[CANDIDATES];
    private final long[] nearest = new long[CANDIDATES];
    private int offered;
    private float offeredFromX, offeredFromY;

    // Each leaf: where its drift has got to, how fast it drifts, how long it has fallen and how long since it landed (or below 0 in the air).
    private final float[] x = new float[MOST], y = new float[MOST], up = new float[MOST], vx = new float[MOST], vy = new float[MOST];
    private final float[] age = new float[MOST], landed = new float[MOST], lies = new float[MOST], ground = new float[MOST];
    // Its own: how fast it falls, its swing's reach, rate, start and way, its spin, whether it tumbles over and over, its size, colour and turn.
    private final float[] fall = new float[MOST], swing = new float[MOST], rate = new float[MOST], phase = new float[MOST], way = new float[MOST];
    private final float[] spin = new float[MOST], turn = new float[MOST], tumble = new float[MOST], half = new float[MOST], turned = new float[MOST];
    private final byte[] hue = new byte[MOST];
    private final int[] square = new int[MOST];
    /** A leaf the wind has caught against a wall: it stays where it was stopped and fades. */
    private final boolean[] caught = new boolean[MOST];
    private int count;
    /** Leaves no room was left for in the last step: as many lying leaves start to fade in the next. */
    private int refused;
    private int random = 0x2545F491;
    private float lastEyeX = Float.NaN, lastEyeY = Float.NaN;
    /**
     * Counted since the sim was made: leaves let go of by trees, leaves landed, and leaves gone
     * from one step to the next that showed more than SHOWING before it. A leaf that fades, or is
     * carried off past KEPT in steps of a frame, is all but gone in the step before it is: one
     * vanishes when the eye jumps, or when a gale carries it from well in sight past KEPT in one
     * long step.
     */
    long spawned, landings, vanished;
    /** Leaves caught against a wall or a roof, counted since the sim was made. */
    long caughts;

    /** Starts a list of the trees round the eye at (eyeX, eyeY), offered one by one, which endTrees makes the trees that shed. */
    void beginTrees(float eyeX, float eyeY) {
        offered = 0;
        offeredFromX = eyeX;
        offeredFromY = eyeY;
    }

    /**
     * A tree standing at (x, y) on ground at that height, its sprite named name, its square
     * hashing to hash (Seasons.treeHash), with the game's leaves on it or not, and its crown's
     * leaves from low to high over the ground and reaching that far out, in tiles, or high 0
     * where no crown of it has been built. Only a broad-leaved tree in leaf with a crown is kept:
     * endTrees keeps the nearest of them, and step lets none further than REACH from the eye shed.
     */
    void offer(float x, float y, float ground, String name, int hash, boolean gameLeaves, float low, float high, float reach) {
        float dx = x - offeredFromX, dy = y - offeredFromY;
        if (!gameLeaves || high <= 0f || !sheds(name)) {
            return;
        }
        int k = offered++;
        offeredX[k] = x;
        offeredY[k] = y;
        offeredGround[k] = ground;
        offeredLow[k] = low;
        offeredHigh[k] = high;
        offeredReach[k] = reach;
        offeredHash[k] = hash;
        offeredHue[k] = (byte) Flora.autumnHue(name, hash);
        // The distance's bits order as the distance does, with the tree's place in the low half.
        nearest[k] = (long) Float.floatToIntBits(dx * dx + dy * dy) << 32 | k;
    }

    /** Makes the MOST_TREES nearest of the trees offered the trees that shed, nearest first, so where there is not room for every leaf the nearest have it. */
    void endTrees() {
        Arrays.sort(nearest, 0, offered);
        trees = Math.min(offered, MOST_TREES);
        for (int t = 0; t < trees; t++) {
            int k = (int) nearest[t];
            treeX[t] = offeredX[k];
            treeY[t] = offeredY[k];
            treeGround[t] = offeredGround[k];
            treeLow[t] = offeredLow[k];
            treeHigh[t] = offeredHigh[k];
            treeReach[t] = offeredReach[k];
            treeHash[t] = offeredHash[k];
            treeHue[t] = offeredHue[k];
            released[t] = 0;
            // A start of its own between nothing and one leaf owed, so a tree sheds as often as it should whenever the list is made again.
            owed[t] = next();
        }
    }

    /** How many leaves lie on the ground and are not fading. */
    int carpet() {
        int lying = 0;
        for (int i = 0; i < count; i++) {
            lying += landed[i] >= 0f && landed[i] < lies[i] ? 1 : 0;
        }
        return lying;
    }

    /** How many leaves the tree t places from the nearest has let go of since the list was made. */
    int released(int t) {
        return released[t];
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
     * How much leaf a deciduous tree's crown is drawn with, 1 full and 0 bare, as PlantBuilder
     * draws it: none where the game has no leaves on the tree (gameLeaves false: its own erosion
     * has it bare), else Seasons.leafOn at the step of the season the crowns are built at.
     */
    static float crownLeaf(boolean gameLeaves, int season, float progression, int treeHash) {
        return gameLeaves ? Seasons.leafOn(season, Seasons.stepped(season, progression), treeHash) : 0f;
    }

    /**
     * How many leaves a second a deciduous tree whose square hashes to treeHash lets go of in
     * still air: none outside autumn and none from a crown drawn bare; while its leaves turn, more
     * the further they have turned, and most as its crown thins fastest.
     */
    static float shedding(boolean gameLeaves, int season, float progression, int treeHash) {
        if (season != Seasons.AUTUMN) {
            return 0f;
        }
        float on = crownLeaf(gameLeaves, season, progression, treeHash), turned = Seasons.turned(season, Seasons.stepped(season, progression), treeHash);
        if (on <= 0f || turned <= 0f) {
            return 0f;
        }
        // leafOn is 1 less a smoothstep, whose slope, 6t(1 - t), is 4 * on * (1 - on) to within a few hundredths, and peaks at 1.
        return PEAK * Math.max(TRICKLE * smooth(turned / 0.5f) * on, 4f * on * (1f - on));
    }

    /** The storey of a building a leaf at height up is in, over ground at that height: the map's buildings stand on the ground as the hills lift it. */
    static int storey(float up, float ground) {
        return Math.max((int) Math.floor((up - ground) / ViewCamera.LEVEL_HEIGHT), 0);
    }

    /**
     * A number for the square and storey a leaf at (x, y, up) is in, the storey counted from the
     * ground in the middle of that square, as the walls are asked: they are asked again only when it changes.
     */
    private static int squareOf(float x, float y, float up, Ground on) {
        int sx = (int) Math.floor(x), sy = (int) Math.floor(y);
        return sx * 73856093 ^ sy * 19349663 ^ storey(up, on.ground(sx + 0.5f, sy + 0.5f)) * 83492791;
    }

    /** The share of its leaves a tree that far from the eye is given to let go of: all within NEAR, less further off. */
    static float nearness(float away) {
        return 1f - 0.7f * smooth((away - NEAR) / (REACH - NEAR));
    }

    /** How many times as many leaves the wind takes as still air would let fall, by the game's wind intensity, 0 still to 1 at 120 km/h. */
    static float letGo(float intensity) {
        return 0.8f + 3.2f * smooth(intensity / 0.5f);
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
            for (int i = 0; i < count; i++) {
                vanished += shown(i, lastEyeX, lastEyeY) > SHOWING ? 1 : 0;
            }
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
        int crowded = refused, carpet = crowded > 0 ? carpet() : 0;
        refused = 0;
        for (int i = 0; i < count; ) {
            float showed = shown(i, eyeX, eyeY);
            // Where there was no room, lying leaves that have lain a while make it by fading now, none
            // taken away while it shows, and a carpet of CARPET of them is left lying out its time.
            if (crowded > 0 && carpet > CARPET && landed[i] >= PRESSED_AFTER && landed[i] < lies[i]) {
                lies[i] = landed[i];
                crowded--;
                carpet--;
            }
            if (move(i, dt, driftX, driftY, eyeX, eyeY, on)) {
                i++;
            } else {
                vanished += showed > SHOWING ? 1 : 0;
                drop(i);
            }
        }
        float windy = Math.clamp(intensity / 0.5f, 0f, 1f), more = letGo(intensity);
        for (int t = 0; t < trees; t++) {
            float dx = treeX[t] - eyeX, dy = treeY[t] - eyeY;
            if (dx * dx + dy * dy > REACH * REACH) {
                continue;
            }
            // A tree near the eye sheds all it would; one further off a share of that, so in a wood the room goes to the leaves that can be seen as leaves.
            float rate = shedding(true, season, progression, treeHash[t]) * nearness((float) Math.sqrt(dx * dx + dy * dy));
            if (rate <= 0f) {
                continue;
            }
            owed[t] += rate * more * dt;
            while (owed[t] >= 1f) {
                owed[t] -= 1f;
                spawn(t, season, progression, windy, driftX, driftY, on);
            }
        }
    }

    /** Moves leaf i on by dt; false once it is gone. */
    private boolean move(int i, float dt, float driftX, float driftY, float eyeX, float eyeY, Ground on) {
        if (landed[i] >= 0f) {
            landed[i] += dt;
            return landed[i] < lies[i] + FADES;
        }
        float wasX = x[i], wasY = y[i], wasUp = up[i], wasAge = age[i];
        float k = (float) Math.exp(-dt / DRAG);
        // Falls to the ground within the step: only as much of the step as that takes moves it.
        float into = dt;
        if (up[i] - fall[i] * dt <= ground[i] + GROUND_ASKED) {
            ground[i] = on.surface(x[i] + swungX(i), y[i] + swungY(i), up[i]);
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
        }
        float fromX = x[i] - eyeX, fromY = y[i] - eyeY;
        if (fromX * fromX + fromY * fromY > KEPT * KEPT) {
            return false;
        }
        // The walls are asked where the leaf is drawn, its swing included.
        float drawnX = x[i] + swungX(i), drawnY = y[i] + swungY(i);
        int at = squareOf(drawnX, drawnY, up[i], on);
        if (at != square[i]) {
            square[i] = at;
            if (on.walled((int) Math.floor(drawnX), (int) Math.floor(drawnY), up[i])) {
                // Caught against the wall or on the roof it met, drawn where it was before the step took it through, and fades there.
                x[i] = wasX;
                y[i] = wasY;
                up[i] = wasUp;
                age[i] = wasAge;
                caught[i] = true;
                caughts++;
                landed[i] = 0f;
                lies[i] = 0f;
                return true;
            }
            // Over another square or in another storey there may be another roof under it.
            ground[i] = on.surface(drawnX, drawnY, up[i]);
        } else if (up[i] <= ground[i] + GROUND_ASKED) {
            // Near what it falls onto, which on a pitched roof is higher a little way up the slope.
            ground[i] = on.surface(drawnX, drawnY, up[i]);
        }
        if (into < dt || up[i] <= ground[i]) {
            // It lies where it is drawn, its swing held where it came down, on what is there.
            up[i] = on.surface(drawnX, drawnY, wasUp);
            landed[i] = dt - into;
            landings++;
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
        caught[i] = caught[last];
    }

    /** Lets a leaf go from somewhere in tree t's crown. With every place taken none is, and a lying leaf starts to fade in the next step to make room. */
    private void spawn(int t, int season, float progression, float windy, float driftX, float driftY, Ground on) {
        if (count == MOST) {
            refused++;
            return;
        }
        // From the outer part of the crown, which is where the wind gets at the leaves.
        double angle = 2.0 * Math.PI * next();
        float out = OUT_MOST * treeReach[t] * (float) Math.sqrt(0.3f + 0.7f * next());
        float fromX = treeX[t] + out * (float) Math.cos(angle), fromY = treeY[t] + out * (float) Math.sin(angle);
        float fromUp = treeGround[t] + treeLow[t] + (treeHigh[t] - treeLow[t]) * next();
        int i = count;
        x[i] = fromX;
        y[i] = fromY;
        up[i] = fromUp;
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
        turned[i] = Math.clamp(Seasons.turned(season, Seasons.stepped(season, progression), treeHash[t]) + 0.35f * (next() - 0.4f), 0f, 1f);
        caught[i] = false;
        // A crown that reaches over a house has no leaves inside its rooms to let go of: the slot is left for the next.
        float drawnX = fromX + swungX(i), drawnY = fromY + swungY(i);
        if (on.walled((int) Math.floor(drawnX), (int) Math.floor(drawnY), fromUp)) {
            return;
        }
        square[i] = squareOf(drawnX, drawnY, fromUp, on);
        ground[i] = on.surface(drawnX, drawnY, fromUp);
        count++;
        released[t]++;
        spawned++;
    }

    /**
     * Writes the leaves within KEPT of the eye as the picture takes them (see FLOATS) into
     * into, which holds MOST, and gives how many.
     */
    int write(float[] into, float eyeX, float eyeY) {
        int written = 0;
        for (int i = 0; i < count; i++) {
            float shown = shown(i, eyeX, eyeY);
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

    /** How much of leaf i shows: it comes in over its first moments, fades towards KEPT of the eye, and fades once it has lain its time. */
    private float shown(int i, float eyeX, float eyeY) {
        float fromX = x[i] - eyeX, fromY = y[i] - eyeY;
        float far = (float) Math.sqrt(fromX * fromX + fromY * fromY);
        float shown = (1f - smooth((far - REACH) / (KEPT - REACH))) * smooth(age[i] / 0.2f);
        return landed[i] >= 0f ? shown * (1f - smooth((landed[i] - lies[i]) / FADES)) : shown;
    }

    /** Where leaf i is and how it is turned, into its record at at. */
    private void pose(int i, float[] into, int at) {
        // Its swing, its spin and how it rocks or tumbles are of how long it has been falling: on the ground, of when it landed.
        float t = age[i];
        float swung = rate[i] * t + phase[i];
        float wayNow = way[i] + 0.25f * t;
        float dx = (float) Math.cos(wayNow), dy = (float) Math.sin(wayNow);
        float s = (float) Math.sin(swung), c = (float) Math.cos(swung);
        float cx = x[i] + swungX(i), cy = y[i] + swungY(i);
        // At either end of its swing a leaf glides up a little, as a pendulum's bob does.
        float cz = up[i] + 0.3f * swing[i] * s * s;
        float rock;
        if (landed[i] >= 0f && !caught[i]) {
            cz = up[i] + 0.004f;
            rock = 0.12f * (float) Math.sin(turn[i] * 3f);
        } else if (tumble[i] != 0f) {
            rock = tumble[i] * t + turn[i];
        } else {
            // Tilted along the way it swings, the most as it passes the middle.
            rock = 0.9f * c;
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

    /** How far leaf i's swing takes where it is drawn from where it drifts, along x and along y, at how long it has been falling. */
    private float swungX(int i) {
        return (float) Math.cos(way[i] + 0.25f * age[i]) * swing[i] * (float) Math.sin(rate[i] * age[i] + phase[i]);
    }

    private float swungY(int i) {
        return (float) Math.sin(way[i] + 0.25f * age[i]) * swing[i] * (float) Math.sin(rate[i] * age[i] + phase[i]);
    }

    /** What the leaves are now, for a log line or a probe. */
    String describe() {
        int lying = 0;
        for (int i = 0; i < count; i++) {
            lying += landed[i] >= 0f ? 1 : 0;
        }
        return String.format(Locale.ROOT, "leaves %d (%d lying) from %d trees in leaf with a crown, the nearest %.1f tiles off, which has let go %d since the trees were read; %d let go, %d landed, %d vanished in all",
                count, lying, trees, trees == 0 ? 0f : (float) Math.hypot(treeX[0] - lastEyeX, treeY[0] - lastEyeY), trees == 0 ? 0 : released[0], spawned, landings, vanished);
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
