package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import zombie.GameTime;
import zombie.core.textures.Texture;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;

/**
 * Pieces of what is knocked down or shot out, falling in the first person picture: a fence a
 * vehicle drives through throws boards, rails and pickets, a lamp post falls over whole or in
 * two, a road barricade comes apart into its boards and legs, and what shots cut free of a fence
 * or a wall (Breakage) drops out of it. Each piece is a box the size of what it is a piece of,
 * in that thing's colour, thrown, spinning, falling under gravity onto the ground of its level,
 * bouncing once or twice, sliding to a stop and toppling onto its broadest face. It lies a few
 * seconds and sinks into the ground, and is gone.
 *
 * Only the picture. Nothing here is in the game's world: a piece meets nothing but the ground,
 * not walls, people or vehicles, and the game never hears of it. It is thrown only where the
 * mod itself knocked down or shot out the thing, which it does alone (Knockdown, BlastHoles).
 *
 * The pieces are held on the flat map at their height over the floor of their level, and the
 * picture lifts each by the ground under it (Terrain) as a view gathers them, so each view's
 * lie of the land carries them. One pool for every view, moved on to the moment a view gathers
 * it, at most once for any moment. Logic thread, as is all that throws pieces.
 */
final class Debris {
    private Debris() {}

    /** The most pieces at once. A new piece past that takes the place of the oldest. */
    static final int MOST = 256;

    /**
     * One piece as a frame carries it to the picture, FLOATS long: its middle, in the picture's
     * measure (x and y in tiles, up in tiles over the flat map), then its three half axes, each
     * a way in the world as long as half the box is that way, then its colour as the game paints
     * the thing, 0 to 1.
     */
    static final int X = 0, Y = 1, UP = 2, A = 3, B = 6, C = 9, RED = 12, GREEN = 13, BLUE = 14;
    static final int FLOATS = 15;

    /** How far from the eye pieces are drawn, in tiles. */
    static final float REACH = 60f;

    /** Each step of the motion, in seconds, and the longest step taken at once: past it the game has hitched, and the pieces wait. */
    static final float STEP = 1f / 120f, STEP_MOST = 0.25f;
    /** In tiles a second squared: a tile is a metre. */
    static final float GRAVITY = 9.8f;
    /** How much of its speed a piece loses to the air each second. */
    static final float AIR_DRAG = 0.4f;
    /** How much of its speed into the ground a piece keeps out of it, and below what speed, in tiles a second, it does not bounce. */
    static final float BOUNCE = 0.3f, BOUNCES_FROM = 0.6f;
    /**
     * How hard the ground holds a piece sliding on it, as a share of its weight, and how much of
     * its speed along the ground and of its spin a bounce leaves it: a board landing at speed
     * tumbles and digs in, and does not skate on down the road.
     */
    static final float GRIP = 0.7f, KEPT_BY_A_BOUNCE = 0.5f, SPIN_KEPT_BY_A_BOUNCE = 0.6f;
    /** How much of its spin a piece on the ground loses each second. */
    static final float GROUND_SPIN_DRAG = 8f;
    /**
     * How fast a piece on the ground topples onto its broadest face, against how fast a thing its
     * length falls over, sqrt(g / its longest half), lying nearly flat. Standing nearly on end it
     * starts at a quarter of that, as a thing standing goes over slowly at first: a lamp post
     * takes a second or so, a board stood on its end a quarter of one.
     */
    static final float TOPPLES = 1.5f, TOPPLES_FROM_UPRIGHT = 0.25f;
    /** A piece is on the ground within this of it, in tiles. */
    static final float ON_GROUND = 0.002f;
    /** A piece is still once it slides slower than this, in tiles a second, spins slower than STILL_SPIN radians a second and lies within STILL_TILT radians of flat, for STILL_FOR seconds. */
    static final float STILL_SPEED = 0.05f, STILL_SPIN = 0.3f, STILL_TILT = 0.02f, STILL_FOR = 0.25f;
    /** How long a still piece lies, the least and the most, and how long it takes to sink out of sight, in seconds. */
    static final float LIES_LEAST = 6f, LIES_MOST = 10f, SINKS_FOR = 2f;
    /** A piece still moving after this many seconds lies where it is. */
    static final float LONGEST = 25f;

    /** What a thing is made of, as far as its pieces go: Impacts.WOOD, Impacts.METAL or anything else. */
    static final float[][] UNPAINTED = {{0.45f, 0.33f, 0.2f}, {0.4f, 0.4f, 0.42f}, {0.55f, 0.53f, 0.5f}};

    /** Every piece in the air or lying. */
    static final Pool POOL = new Pool(MOST, new Random());

    private static long lastNanos;

    /** The pieces round the eye as this frame has them, moved on to now: none while FallingDebris is off, which lets every piece go. */
    static void gather(SkyState sky, Terrain terrain, float eyeX, float eyeY, long now) {
        if (!Part.FALLING_DEBRIS.on()) {
            POOL.clear();
            sky.debrisCount = 0;
            return;
        }
        if (now > lastNanos) {
            float elapsed = lastNanos == 0L || GameTime.isGamePaused() ? 0f : (now - lastNanos) / 1e9f;
            lastNanos = now;
            POOL.advance(elapsed);
        }
        sky.debrisCount = POOL.write(sky.debris, terrain, eyeX, eyeY);
    }

    /** The save has been left: every piece goes. */
    static void forget() {
        POOL.clear();
        lastNanos = 0L;
    }

    private static final float[] PAINT = new float[3];

    /**
     * The colour a sprite is painted, as SpriteColours reads it, into into: before the picture
     * has read it, the usual colour of what the sprite is made of (UNPAINTED).
     */
    static void paint(IsoSprite sprite, Texture texture, float[] into) {
        float[] read = SpriteColours.of(texture);
        if (read != null) {
            System.arraycopy(read, 0, into, 0, 3);
            return;
        }
        int made = Impacts.of(sprite, false);
        System.arraycopy(UNPAINTED[made == Impacts.WOOD ? 0 : made == Impacts.METAL ? 1 : 2], 0, into, 0, 3);
    }

    /** How high a sprite paints what stands on its square, in tiles: see paintedHigh. */
    static float paintedHigh(Texture texture, boolean onEdge) {
        return paintedHigh(texture.getWidthOrig(), texture.getHeightOrig(), texture.getOffsetY(), onEdge);
    }

    /**
     * A thing a vehicle knocked down comes apart in the picture: of the square at (x, y) on a
     * level, along its north edge, its west edge or neither, high tiles high (paintedHigh, or a
     * lamp post's whole height), of metal or not, painted the colour given, struck by a vehicle
     * going (goX, goY) tiles a second. Nothing while FallingDebris is off. Logic thread.
     */
    static void knockedDown(Shape shape, int x, int y, int level, boolean north, boolean west, float high, boolean metal, float goX, float goY, float[] paint) {
        if (!Part.FALLING_DEBRIS.on()) {
            return;
        }
        float atX = x + (west && !north ? 0f : 0.5f), atY = y + (north ? 0f : 0.5f);
        float alongX = north || !west ? 1f : 0f, alongY = north || !west ? 0f : 1f;
        POOL.knockedDown(shape, atX, atY, level * ViewCamera.LEVEL_HEIGHT, alongX, alongY, 1f, high, metal, goX, goY, paint[0], paint[1], paint[2]);
        if (DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "the picture throws pieces of what was knocked down at %d,%d,%d as %s, %.2f tiles high: %d pieces in the air or lying",
                    x, y, level, shape, high, POOL.count));
        }
    }

    /** The most pieces one hole shoots out. */
    static final int SHOT_PIECES = 12;
    /** A piece shot out smaller than this across, in tiles, both ways, is a speck: none is thrown. */
    static final float SPECK = 0.03f;
    /** How fast, in tiles a second, a piece a round shot out of a thing is pushed off the way it went, and one a blast blew out. */
    static final float ROUND_PUSH_LEAST = 0.6f, ROUND_PUSH_MOST = 1.4f, BLAST_PUSH_LEAST = 1.8f, BLAST_PUSH_MOST = 3.2f;
    /** The most a piece shot out is pushed along the thing besides, for each tile a second it is pushed off. */
    static final float SHOT_ASIDE = 0.25f;
    /** How fast, in radians a second, a piece shot out tips over the way it went, and the most it turns any way besides. */
    static final float SHOT_TIPS_LEAST = 1.5f, SHOT_TIPS_MOST = 4f, SHOT_SPIN_LOOSE = 1.5f;

    /**
     * What a hole shot through the north or west edge of a fence, a wall or a shut door's leaf
     * cut free of it falls out in the picture: what goes of it now (after, BlastHoles.fallen) and
     * did not before. A fence of boards drops a board at a time. The pieces go the way the shot
     * went: across the edge away from shotFrom, the side it came from (BlastHoles.SIDE's sign),
     * and with a round's hole along its slant too (BlastHoles.slant), a blast's harder than a
     * round's. Nothing while FallingDebris is off. Logic thread.
     */
    static void shotLoose(IsoObject object, boolean north, Breakage.Pieces before, Breakage.Pieces after, boolean blast, int shotFrom, float slantAlong,
            float slantUp) {
        if (!Part.FALLING_DEBRIS.on()) {
            return;
        }
        float[] boxes = loosened(before, after, SHOT_PIECES);
        IsoGridSquare square = object.getSquare();
        IsoSprite sprite = object.getSprite();
        Texture texture = sprite == null ? null : sprite.getTextureForCurrentFrame(object.getDir());
        if (boxes.length == 0 || square == null || texture == null) {
            return;
        }
        paint(sprite, texture, PAINT);
        float thick = DoorGlass.isDoor(object) ? DOOR_HALF : Impacts.of(sprite, false) == Impacts.METAL ? BAR_HALF : BOARD_HALF;
        boolean boards = BlastHoles.boards(object);
        float floor = square.getZ() * ViewCamera.LEVEL_HEIGHT;
        shotWay(north, shotFrom, slantAlong, slantUp, WAY);
        int thrown = 0;
        for (int at = 0; at < boxes.length; at += 4) {
            float a0 = boxes[at], a1 = boxes[at + 2], up0 = boxes[at + 1] * ViewCamera.LEVEL_HEIGHT, up1 = boxes[at + 3] * ViewCamera.LEVEL_HEIGHT;
            if (a1 - a0 < SPECK && up1 - up0 < SPECK) {
                continue;
            }
            int parts = boards ? Math.max(1, Math.round((a1 - a0) / Breakage.BOARD_WIDE)) : 1;
            float wide = (a1 - a0) / parts;
            for (int part = 0; part < parts; part++) {
                float along = a0 + (part + 0.5f) * wide;
                POOL.shotLoose(square.getX() + (north ? along : 0f), square.getY() + (north ? 0f : along), floor, (up0 + up1) / 2f, north ? 1f : 0f, north ? 0f : 1f,
                        wide / 2f, (up1 - up0) / 2f, thick, WAY[0], WAY[1], WAY[2], blast, PAINT[0], PAINT[1], PAINT[2]);
                thrown++;
            }
        }
        if (DevBuild.ON && thrown > 0) {
            Log.info(String.format(Locale.ROOT, "the picture drops %d piece(s) shot out of %s on the %s edge of %d,%d,%d by a %s from the %s side, thrown %.2f,%.2f,%.2f",
                    thrown, DoorGlass.isDoor(object) ? "a door" : sprite.getName(), north ? "north" : "west", square.getX(), square.getY(), square.getZ(),
                    blast ? "blast" : "round", shotFrom < 0 ? "lower" : "higher", WAY[0], WAY[1], WAY[2]));
        }
    }

    private static final float[] WAY = new float[3];

    /**
     * The unit way a shot went through the north or west edge, as x, y and up into way: across
     * the edge away from shotFrom, the side it came from, which is the side of lower y for the
     * north edge and of lower x for the west (BlastHoles.SIDE), and along and up by slantAlong
     * and slantUp for every tile across (BlastHoles.slant).
     */
    static void shotWay(boolean north, int shotFrom, float slantAlong, float slantUp, float[] way) {
        float across = shotFrom < 0 ? 1f : -1f, along = across * slantAlong, up = across * slantUp;
        float length = (float) Math.sqrt(1f + along * along + up * up);
        way[0] = (north ? along : across) / length;
        way[1] = (north ? across : along) / length;
        way[2] = up / length;
    }

    /** What a thing knocked down comes apart into. */
    enum Shape {
        /** A length of fence taken away: boards, rails and pickets. */
        SPAN,
        /** A length of fence the game breaks or bends, which stays standing or lying broken: a few fragments. */
        FRAGMENTS,
        /** A lamp post: one long piece, or two at speed. */
        POLE,
        /** A road barricade's A frame: its boards and its legs. */
        FRAME
    }

    /** How many pieces a span throws, and fragments. */
    static final int SPAN_PIECES = 7, FRAGMENT_PIECES = 4;
    /** A lamp post hit faster than this, in tiles a second, breaks in two. */
    static final float POLE_BREAKS = 8f;
    /** Half a lamp post's width, a board's thickness and a wire or bar's, in tiles. */
    static final float POLE_HALF = 0.06f, BOARD_HALF = 0.0125f, BAR_HALF = 0.008f, DOOR_HALF = 0.02f;

    /** The pieces in the air and lying, and how they move. */
    static final class Pool {
        final int most;
        private final Random random;
        int count;
        final float[] x, y, floor, up, vx, vy, vup, wx, wy, wup, half, axes, colour, age, lying, liesFor, sinkRate, still;
        final boolean[] resting;
        private final float[] scratch = new float[9], unit = new float[9];

        Pool(int most, Random random) {
            this.most = most;
            this.random = random;
            x = new float[most];
            y = new float[most];
            floor = new float[most];
            up = new float[most];
            vx = new float[most];
            vy = new float[most];
            vup = new float[most];
            wx = new float[most];
            wy = new float[most];
            wup = new float[most];
            half = new float[3 * most];
            axes = new float[9 * most];
            colour = new float[3 * most];
            age = new float[most];
            lying = new float[most];
            liesFor = new float[most];
            sinkRate = new float[most];
            still = new float[most];
            resting = new boolean[most];
        }

        synchronized void clear() {
            count = 0;
        }

        /**
         * A piece: where its middle is on the flat map and how high over the floor of its level,
         * which is floor tiles over the flat map; its axes, three unit ways in the world as columns
         * (x, y, up each), and half its size along each; how fast it goes and spins; and its
         * colour. Past the most pieces it takes the place of the oldest. Gives the piece's slot.
         */
        synchronized int add(float atX, float atY, float atFloor, float atUp, float[] unitAxes, float halfA, float halfB, float halfC,
                float goX, float goY, float goUp, float spinX, float spinY, float spinUp, float red, float green, float blue) {
            int k = count;
            if (count == most) {
                k = 0;
                for (int i = 1; i < count; i++) {
                    if (age[i] > age[k]) {
                        k = i;
                    }
                }
            } else {
                count++;
            }
            x[k] = atX;
            y[k] = atY;
            floor[k] = atFloor;
            up[k] = atUp;
            System.arraycopy(unitAxes, 0, axes, 9 * k, 9);
            half[3 * k] = halfA;
            half[3 * k + 1] = halfB;
            half[3 * k + 2] = halfC;
            vx[k] = goX;
            vy[k] = goY;
            vup[k] = goUp;
            wx[k] = spinX;
            wy[k] = spinY;
            wup[k] = spinUp;
            colour[3 * k] = red;
            colour[3 * k + 1] = green;
            colour[3 * k + 2] = blue;
            age[k] = 0f;
            lying[k] = 0f;
            still[k] = 0f;
            sinkRate[k] = 0f;
            liesFor[k] = LIES_LEAST + (LIES_MOST - LIES_LEAST) * random.nextFloat();
            resting[k] = false;
            return k;
        }

        /** Moves every piece on by seconds, at most STEP_MOST, in steps of STEP, letting go of those sunk out of sight. */
        synchronized void advance(float seconds) {
            seconds = Math.min(seconds, STEP_MOST);
            for (float done = 0f; done < seconds && count > 0; done += STEP) {
                float dt = Math.min(STEP, seconds - done);
                for (int k = count - 1; k >= 0; k--) {
                    if (step(k, dt)) {
                        move(count - 1, k);
                        count--;
                    }
                }
            }
        }

        /** How far the piece reaches down from its middle, as it is turned now. */
        float reachDown(int k) {
            int at = 9 * k;
            return Math.abs(axes[at + 2]) * half[3 * k] + Math.abs(axes[at + 5]) * half[3 * k + 1] + Math.abs(axes[at + 8]) * half[3 * k + 2];
        }

        /** Moves piece k on by dt: true once it has sunk out of sight. */
        private boolean step(int k, float dt) {
            age[k] += dt;
            if (resting[k]) {
                lying[k] += dt;
                if (lying[k] > liesFor[k]) {
                    up[k] -= sinkRate[k] * dt;
                    return up[k] + reachDown(k) < 0f;
                }
                return false;
            }
            vup[k] -= GRAVITY * dt;
            float keep = Math.max(0f, 1f - AIR_DRAG * dt);
            vx[k] *= keep;
            vy[k] *= keep;
            vup[k] *= keep;
            x[k] += vx[k] * dt;
            y[k] += vy[k] * dt;
            up[k] += vup[k] * dt;
            turn(k, wx[k], wy[k], wup[k], dt);
            float down = reachDown(k);
            if (up[k] - down > ON_GROUND) {
                still[k] = 0f;
                return false;
            }
            up[k] = Math.max(up[k], down);
            if (vup[k] < -BOUNCES_FROM) {
                vup[k] = -vup[k] * BOUNCE;
                vx[k] *= KEPT_BY_A_BOUNCE;
                vy[k] *= KEPT_BY_A_BOUNCE;
                wx[k] *= SPIN_KEPT_BY_A_BOUNCE;
                wy[k] *= SPIN_KEPT_BY_A_BOUNCE;
                wup[k] *= SPIN_KEPT_BY_A_BOUNCE;
            } else if (vup[k] < 0f) {
                vup[k] = 0f;
            }
            float sliding = (float) Math.hypot(vx[k], vy[k]);
            if (sliding > 0f) {
                float kept = Math.max(0f, sliding - GRIP * GRAVITY * dt) / sliding;
                vx[k] *= kept;
                vy[k] *= kept;
            }
            float spinKept = Math.max(0f, 1f - GROUND_SPIN_DRAG * dt);
            wx[k] *= spinKept;
            wy[k] *= spinKept;
            wup[k] *= spinKept;
            float tilt = topple(k, dt);
            // Turned over on the ground, it goes over about where it touches and stays on it: turned about its middle, its end lifted off.
            up[k] = reachDown(k);
            float spin = (float) Math.sqrt(wx[k] * wx[k] + wy[k] * wy[k] + wup[k] * wup[k]);
            if (sliding < STILL_SPEED && spin < STILL_SPIN && tilt < STILL_TILT && vup[k] == 0f) {
                still[k] += dt;
            } else {
                still[k] = 0f;
            }
            if (still[k] >= STILL_FOR || age[k] > LONGEST) {
                lieDown(k);
            }
            return false;
        }

        private void lieDown(int k) {
            resting[k] = true;
            vx[k] = vy[k] = vup[k] = 0f;
            wx[k] = wy[k] = wup[k] = 0f;
            float down = reachDown(k);
            up[k] = down;
            sinkRate[k] = 2f * down / SINKS_FOR;
        }

        /**
         * Turns a piece on the ground towards lying on its broadest face: the thinnest of its
         * axes, or of those nearly as thin the one nearest upright, is turned upright the way it
         * leans. Gives how far it was from that, in radians, before the turn.
         */
        private float topple(int k, float dt) {
            int at = 9 * k, h = 3 * k;
            float thinnest = Math.min(half[h], Math.min(half[h + 1], half[h + 2]));
            float longest = Math.max(half[h], Math.max(half[h + 1], half[h + 2]));
            int axis = -1;
            float upright = -1f;
            for (int i = 0; i < 3; i++) {
                float leans = Math.abs(axes[at + 3 * i + 2]);
                if (half[h + i] <= 1.3f * thinnest && leans > upright) {
                    axis = i;
                    upright = leans;
                }
            }
            float sign = axes[at + 3 * axis + 2] < 0f ? -1f : 1f;
            float ax = axes[at + 3 * axis] * sign, ay = axes[at + 3 * axis + 1] * sign;
            float tilt = (float) Math.acos(Math.min(1f, upright));
            float across = (float) Math.hypot(ax, ay);
            if (across < 1e-6f) {
                return tilt;
            }
            float by = Math.min(tilt, TOPPLES * (float) Math.sqrt(GRAVITY / longest) * Math.max(TOPPLES_FROM_UPRIGHT, (float) Math.cos(tilt)) * dt);
            // About the level way across the axis, which turns it up.
            float nx = ay / across, ny = -ax / across;
            rotate(k, nx, ny, 0f, by);
            return tilt;
        }

        /** Turns piece k by its spin for dt. */
        private void turn(int k, float spinX, float spinY, float spinUp, float dt) {
            float w = (float) Math.sqrt(spinX * spinX + spinY * spinY + spinUp * spinUp);
            if (w < 1e-6f) {
                return;
            }
            rotate(k, spinX / w, spinY / w, spinUp / w, w * dt);
        }

        /** Turns each of piece k's axes about the unit way n by angle, then makes them square to each other again. */
        private void rotate(int k, float nx, float ny, float nz, float angle) {
            float c = (float) Math.cos(angle), s = (float) Math.sin(angle), t = 1f - c;
            int at = 9 * k;
            for (int i = 0; i < 3; i++) {
                int a = at + 3 * i;
                float vx0 = axes[a], vy0 = axes[a + 1], vz0 = axes[a + 2];
                float dot = (nx * vx0 + ny * vy0 + nz * vz0) * t;
                scratch[3 * i] = vx0 * c + (ny * vz0 - nz * vy0) * s + nx * dot;
                scratch[3 * i + 1] = vy0 * c + (nz * vx0 - nx * vz0) * s + ny * dot;
                scratch[3 * i + 2] = vz0 * c + (nx * vy0 - ny * vx0) * s + nz * dot;
            }
            square(scratch);
            System.arraycopy(scratch, 0, axes, at, 9);
        }

        /** Copies piece from into slot to. */
        private void move(int from, int to) {
            if (from == to) {
                return;
            }
            x[to] = x[from];
            y[to] = y[from];
            floor[to] = floor[from];
            up[to] = up[from];
            vx[to] = vx[from];
            vy[to] = vy[from];
            vup[to] = vup[from];
            wx[to] = wx[from];
            wy[to] = wy[from];
            wup[to] = wup[from];
            System.arraycopy(half, 3 * from, half, 3 * to, 3);
            System.arraycopy(axes, 9 * from, axes, 9 * to, 9);
            System.arraycopy(colour, 3 * from, colour, 3 * to, 3);
            age[to] = age[from];
            lying[to] = lying[from];
            liesFor[to] = liesFor[from];
            sinkRate[to] = sinkRate[from];
            still[to] = still[from];
            resting[to] = resting[from];
        }

        /**
         * Writes the pieces within REACH of the eye into records, FLOATS each, each lifted by the
         * ground under it, and gives how many.
         */
        synchronized int write(float[] records, Terrain terrain, float eyeX, float eyeY) {
            int written = 0;
            for (int k = 0; k < count; k++) {
                float dx = x[k] - eyeX, dy = y[k] - eyeY;
                if (dx * dx + dy * dy > REACH * REACH) {
                    continue;
                }
                write(k, records, written * FLOATS, floor[k] + terrain.lift(x[k], y[k], 0f));
                written++;
            }
            return written;
        }

        /** Writes piece k into records from at, standing on ground tiles over the flat map. */
        void write(int k, float[] records, int at, float ground) {
            records[at + X] = x[k];
            records[at + Y] = y[k];
            records[at + UP] = ground + up[k];
            for (int i = 0; i < 3; i++) {
                float h = half[3 * k + i];
                for (int j = 0; j < 3; j++) {
                    records[at + A + 3 * i + j] = axes[9 * k + 3 * i + j] * h;
                }
            }
            records[at + RED] = colour[3 * k];
            records[at + GREEN] = colour[3 * k + 1];
            records[at + BLUE] = colour[3 * k + 2];
        }

        private float between(float least, float most) {
            return least + (most - least) * random.nextFloat();
        }

        /** A spin of a random way, between least and most radians a second, into scratch's first three. */
        private void randomSpin(float least, float most) {
            float sx = random.nextFloat() * 2f - 1f, sy = random.nextFloat() * 2f - 1f, sz = random.nextFloat() * 2f - 1f;
            float length = Math.max((float) Math.sqrt(sx * sx + sy * sy + sz * sz), 1e-3f), w = between(least, most);
            scratch[0] = sx / length * w;
            scratch[1] = sy / length * w;
            scratch[2] = sz / length * w;
        }

        /**
         * Throws the pieces of a thing a vehicle knocked down: its middle on the flat map and the
         * floor of its level; the way along it on the ground, a unit way, for a fence the way its
         * edge runs; how long and how high it is, in tiles; whether it is of metal, whose pieces
         * are bars and not boards; the vehicle's speed on the map, in tiles a second; and its
         * colour.
         */
        synchronized void knockedDown(Shape shape, float atX, float atY, float atFloor, float alongX, float alongY, float length, float high, boolean metal,
                float goX, float goY, float red, float green, float blue) {
            float speed = (float) Math.hypot(goX, goY);
            float wayX = speed > 0f ? goX / speed : alongY, wayY = speed > 0f ? goY / speed : -alongX;
            switch (shape) {
                case SPAN -> pieces(SPAN_PIECES, 1f, atX, atY, atFloor, alongX, alongY, length, high, metal, goX, goY, wayX, wayY, speed, red, green, blue);
                case FRAGMENTS -> pieces(FRAGMENT_PIECES, 0.5f, atX, atY, atFloor, alongX, alongY, length, high, metal, goX, goY, wayX, wayY, speed, red, green, blue);
                case POLE -> pole(atX, atY, atFloor, high, wayX, wayY, speed, red, green, blue);
                case FRAME -> frame(atX, atY, atFloor, wayX, wayY, goX, goY, speed, red, green, blue);
            }
        }

        private void pieces(int many, float size, float atX, float atY, float atFloor, float alongX, float alongY, float length, float high, boolean metal,
                float goX, float goY, float wayX, float wayY, float speed, float red, float green, float blue) {
            float thin = metal ? BAR_HALF : BOARD_HALF;
            for (int i = 0; i < many; i++) {
                float s = (i + random.nextFloat()) / many - 0.5f;
                float px = atX + alongX * s * length, py = atY + alongY * s * length;
                float lifted = high * between(0.15f, 0.9f);
                // Some of a fence's pieces are its pickets or posts, standing as they stood, and the rest its boards or rails, along it.
                boolean upright = random.nextFloat() < 0.4f;
                float stretch = size * (upright ? between(0.2f, 0.45f) * high : between(0.15f, 0.4f) * length);
                float wide = metal ? thin : size * between(0.04f, 0.07f);
                if (upright) {
                    columns(unit, 0f, 0f, 1f, alongX, alongY, 0f);
                } else {
                    columns(unit, alongX, alongY, 0f, 0f, 0f, 1f);
                }
                fling(px, py, atFloor, Math.max(lifted, upright ? stretch : wide), unit, stretch, wide, thin, goX, goY, wayX, wayY, speed, red, green, blue);
            }
        }

        /** A piece flung on by the vehicle, as fast as it or faster, as something light struck by something heavy is, a little to the side, up and spinning. */
        private void fling(float px, float py, float atFloor, float atUp, float[] unit, float halfA, float halfB, float halfC, float goX, float goY,
                float wayX, float wayY, float speed, float red, float green, float blue) {
            float on = between(1f, 1.4f), aside = between(-0.3f, 0.3f) * speed;
            randomSpin(3f, 12f);
            add(px, py, atFloor, atUp, unit, halfA, halfB, halfC, goX * on - wayY * aside, goY * on + wayX * aside, between(1.5f, 3.5f) + speed * between(0f, 0.15f),
                    scratch[0], scratch[1], scratch[2], red, green, blue);
        }

        /** A lamp post going over the way the vehicle went, whole, or in two at speed with the top going further. */
        private void pole(float atX, float atY, float atFloor, float high, float wayX, float wayY, float speed, float red, float green, float blue) {
            // Leaning a little the way it is pushed, and turning about the level way across that, which tips its top over.
            float lean = 0.2f, c = (float) Math.cos(lean), s = (float) Math.sin(lean);
            float spinX = -wayY, spinY = wayX;
            columns(unit, wayX * s, wayY * s, c, -wayY, wayX, 0f);
            if (speed <= POLE_BREAKS) {
                float w = between(0.8f, 1.4f);
                add(atX, atY, atFloor, high / 2f * c, unit, high / 2f, POLE_HALF, POLE_HALF, wayX * speed * 0.25f, wayY * speed * 0.25f, 0.3f,
                        spinX * w, spinY * w, 0f, red, green, blue);
                return;
            }
            float lower = 0.4f * high, upper = high - lower;
            float w = between(0.8f, 1.4f);
            add(atX + wayX * s * lower / 2f, atY + wayY * s * lower / 2f, atFloor, lower / 2f * c, unit, lower / 2f, POLE_HALF, POLE_HALF,
                    wayX * speed * 0.3f, wayY * speed * 0.3f, 0.3f, spinX * w, spinY * w, 0f, red, green, blue);
            float along = lower + upper / 2f, wTop = between(1.6f, 2.6f);
            add(atX + wayX * s * along, atY + wayY * s * along, atFloor, along * c, unit, upper / 2f, POLE_HALF, POLE_HALF,
                    wayX * speed * 0.55f, wayY * speed * 0.55f, 1f, spinX * wTop, spinY * wTop, 0f, red, green, blue);
        }

        /** A road barricade's two boards across the way the vehicle came and its four legs. */
        private void frame(float atX, float atY, float atFloor, float wayX, float wayY, float goX, float goY, float speed, float red, float green, float blue) {
            for (int board = 0; board < 2; board++) {
                columns(unit, -wayY, wayX, 0f, 0f, 0f, 1f);
                fling(atX, atY, atFloor, board == 0 ? 0.55f : 0.8f, unit, between(0.4f, 0.5f), 0.08f, BOARD_HALF, goX, goY, wayX, wayY, speed, red, green, blue);
            }
            for (int leg = 0; leg < 4; leg++) {
                float side = leg < 2 ? -0.45f : 0.45f;
                columns(unit, 0f, 0f, 1f, -wayY, wayX, 0f);
                fling(atX - wayY * side, atY + wayX * side, atFloor, 0.45f, unit, 0.45f, 0.025f, 0.025f, goX, goY, wayX, wayY, speed, red, green, blue);
            }
        }

        /**
         * Drops a piece shot out of a fence, a wall or a door: a slab standing in the plane of its
         * edge, its middle at (atX, atY) and atUp over the floor, alongX and alongY the way the edge
         * runs, halfAlong and halfUp half its size in that plane and halfThick half its thickness.
         * It is pushed off the way the shot went (wayX, wayY, wayUp, a unit way), harder by a blast,
         * its top tipping over that way first.
         */
        synchronized void shotLoose(float atX, float atY, float atFloor, float atUp, float alongX, float alongY, float halfAlong, float halfUp, float halfThick,
                float wayX, float wayY, float wayUp, boolean blast, float red, float green, float blue) {
            columns(unit, alongX, alongY, 0f, 0f, 0f, 1f);
            float push = blast ? between(BLAST_PUSH_LEAST, BLAST_PUSH_MOST) : between(ROUND_PUSH_LEAST, ROUND_PUSH_MOST);
            float aside = between(-SHOT_ASIDE, SHOT_ASIDE) * push;
            randomSpin(0f, SHOT_SPIN_LOOSE);
            // Spinning about up x way tips the top of the slab over the way it was pushed.
            float tips = between(SHOT_TIPS_LEAST, SHOT_TIPS_MOST);
            add(atX, atY, atFloor, atUp, unit, halfAlong, halfUp, halfThick, wayX * push + alongX * aside, wayY * push + alongY * aside, wayUp * push,
                    scratch[0] - wayY * tips, scratch[1] + wayX * tips, scratch[2], red, green, blue);
        }
    }

    /** Three unit axes as columns into into: a and b as given, square to each other, and their cross. */
    static void columns(float[] into, float ax, float ay, float az, float bx, float by, float bz) {
        into[0] = ax;
        into[1] = ay;
        into[2] = az;
        into[3] = bx;
        into[4] = by;
        into[5] = bz;
        into[6] = ay * bz - az * by;
        into[7] = az * bx - ax * bz;
        into[8] = ax * by - ay * bx;
    }

    /** Makes three axes, columns of nine, unit and square to each other again, the first kept as it points. */
    static void square(float[] t) {
        float l0 = (float) Math.sqrt(t[0] * t[0] + t[1] * t[1] + t[2] * t[2]);
        t[0] /= l0;
        t[1] /= l0;
        t[2] /= l0;
        float d = t[0] * t[3] + t[1] * t[4] + t[2] * t[5];
        t[3] -= d * t[0];
        t[4] -= d * t[1];
        t[5] -= d * t[2];
        float l1 = (float) Math.sqrt(t[3] * t[3] + t[4] * t[4] + t[5] * t[5]);
        t[3] /= l1;
        t[4] /= l1;
        t[5] /= l1;
        t[6] = t[1] * t[5] - t[2] * t[4];
        t[7] = t[2] * t[3] - t[0] * t[5];
        t[8] = t[0] * t[4] - t[1] * t[3];
    }

    /**
     * How high a sprite paints what stands on its square, in tiles over its foot: from the top of
     * what is painted down to the square's north corner, where a thing on its north or west edge
     * reaches highest on the screen, or to its middle for a thing that stands in the middle.
     * The game's projection: a level rises 96 frame pixels for a sprite 64 wide, and the
     * square's north corner is 32 of those above the bottom of the frame, its middle 16.
     */
    static float paintedHigh(float widthOrig, float heightOrig, float offsetY, boolean onEdge) {
        float scale = widthOrig / 64f;
        float foot = heightOrig - (onEdge ? 32f : 16f) * scale;
        float levels = (foot - offsetY) / (SpriteGeometry.PIXELS_PER_LEVEL * scale);
        return Math.clamp(levels, 0.1f, 1f) * ViewCamera.LEVEL_HEIGHT;
    }

    /** What is new in after against before (Breakage.Pieces) as boxes along and up, four numbers each (along from, up from, along to, up to, up in levels), the largest first, at most most of them. */
    static float[] loosened(Breakage.Pieces before, Breakage.Pieces after, int most) {
        ArrayList<float[]> boxes = new ArrayList<>();
        for (float[] r : after.rectangles) {
            float midA = (r[0] + r[2]) / 2f, midU = (r[1] + r[5]) / 2f;
            if (!before.inside(midA, midU)) {
                boxes.add(new float[] {r[0], r[1], r[2], r[5]});
            }
        }
        // What fell out together is one piece: rectangles that touch are one box.
        float touch = 1.5f * Breakage.CELL, touchUp = touch / ViewCamera.LEVEL_HEIGHT;
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int i = 0; i < boxes.size() && !merged; i++) {
                for (int j = i + 1; j < boxes.size(); j++) {
                    float[] a = boxes.get(i), b = boxes.get(j);
                    if (a[0] <= b[2] + touch && b[0] <= a[2] + touch && a[1] <= b[3] + touchUp && b[1] <= a[3] + touchUp) {
                        a[0] = Math.min(a[0], b[0]);
                        a[1] = Math.min(a[1], b[1]);
                        a[2] = Math.max(a[2], b[2]);
                        a[3] = Math.max(a[3], b[3]);
                        boxes.remove(j);
                        merged = true;
                        break;
                    }
                }
            }
        }
        boxes.sort((a, b) -> Float.compare((b[2] - b[0]) * (b[3] - b[1]), (a[2] - a[0]) * (a[3] - a[1])));
        int many = Math.min(most, boxes.size());
        float[] out = new float[4 * many];
        for (int i = 0; i < many; i++) {
            System.arraycopy(boxes.get(i), 0, out, 4 * i, 4);
        }
        return out;
    }
}
