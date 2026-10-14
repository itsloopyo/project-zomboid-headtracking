package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Random;
import zombie.iso.weather.ThunderStorm;

/**
 * Lightning, as the eye sees it in first person. The game decides when and where lightning
 * strikes, and says so through ThunderStorm.enqueueThunderEvent, on its own and from a server
 * alike. The game's own view shows a strike only as the whole map brightening for a moment, and
 * its thunder comes later by how far off the strike was.
 *
 * Here a strike is a bolt: a channel from the base of the cloud to the ground, crooked at every
 * scale, with branches off it in the first stroke. It runs as lightning does. A faint leader
 * feels its way down; the return stroke lights the whole channel at once, far brighter than
 * the sun; the light falls away in tens of milliseconds, and the channel is struck again down
 * the same path, without its branches, two or three times more. For as long as it is bright it
 * is the light of the world: the cloud it came out of glows from inside, the sky lightens, and
 * what stands in the open is lit from the bolt's side and throws its shadow away from it.
 *
 * The game's own flash, the whole map's light lifted towards noon's and let down over as much
 * as a second and a half, is taken back for a player in first person: there it read as a
 * second of daylight with no shadow after the bolt had gone. Thunder the game has, and nothing
 * here touches it. A rumble with no strike in it is lightning
 * hidden in the cloud: no bolt, a flicker through the cloud deck. The game's option to have no
 * lightning in storms is respected: the game sends no lightning for a strike then, and no
 * flicker is made of a rumble.
 *
 * The game's logic thread, or the thread a server's word arrives on, tells of a strike. Each
 * view's scene works out on the logic thread what it makes of the strikes at that moment, into
 * its SkyState, and the render thread draws from that. A bolt's shape, once made, is not changed.
 */
final class Lightning {
    private Lightning() {}

    /** How high the base of the storm cloud stands over the ground, in tiles: a bolt is this tall. */
    static final float CLOUD_BASE = 900f;
    /** How long a strike is kept after it is told, in seconds: its last light is out well before. */
    static final float LASTS = 1.4f;
    /** How long the leader takes to feel its way down to the ground, in seconds, before the first return stroke. */
    static final float LEADER = 0.045f;
    /** How fast a stroke's light falls away, in seconds: about a frame and a half at 60 frames a second. */
    static final float STROKE_FADES = 0.028f;
    /** How fast the current that some strokes carry on with after their peak falls away, in seconds. */
    static final float GLOW_FADES = 0.16f;
    /** How bright the leader is beside the first return stroke. */
    static final float LEADER_LIGHT = 0.07f;
    /** The most strikes kept at once: a storm rarely has two in the same second. */
    static final int MOST = 4;
    /**
     * How the light a strike gives the world falls off with distance, in tiles: at this far it
     * is half what it is under the bolt. It falls slowly, as a long line of light's does.
     */
    static final float HALF_AT = 450f;
    /** Past this far, in tiles, nothing of a strike is seen: the game's own lightning goes no further. */
    static final float SEEN_WITHIN = 7500f;
    /**
     * At its peak under the bolt, how much light a strike gives what faces it, in the picture's
     * measure: the sun at noon gives 2.1.
     */
    static final float LIGHT = 5.5f;
    /** And how much it gives the whole sky, and what the sky lights. */
    static final float SKY_LIGHT = 0.35f;
    /** The most light a strike gives what faces it, however near: twice the sun at noon. Nearer than that the picture would be white. */
    static final float LIGHT_MOST = 4.2f;
    /** How bright the cloud the bolt came out of glows inside, at its peak under the bolt. */
    static final float CLOUD_LIGHT = 6f;
    /** Lightning's light: a cold white, a little towards violet. */
    static final float RED = 0.82f, GREEN = 0.86f, BLUE = 1f;
    /** How far up the bolt, as a share of its height, its light is taken to come from for a shadow. */
    private static final float LIT_FROM = 0.3f;
    /** A shadow's light is never lower than this, as the sine of its height: from low down a shadow reaches past the shadow maps. */
    private static final float LOWEST_LIGHT = 0.22f;

    /** How many times each half of the main channel is halved, and a branch's, and a twig's. */
    private static final int MAIN_LEVELS = 9, BRANCH_LEVELS = 7, TWIG_LEVELS = 5;
    /** How far the middle of a stretch is pushed aside, as a share of its length: across, and up and down. */
    private static final float JAG = 0.24f, JAG_UP = 0.07f;
    /** How far below the ground the channel goes on, in tiles, so it meets ground of any height and the ground hides the rest. */
    static final float UNDER_GROUND = 40f;
    /** Floats a vertex has in each of its three arrays. */
    static final int FLOATS = 4;

    /**
     * A strike as the game told it, with what is made of it: when its strokes fall and how
     * bright each is, and the shape of its bolt, or none for lightning hidden in the cloud.
     */
    static final class Strike {
        final float x, y;
        final long at;
        final Bolt bolt;
        /** When each stroke falls, in seconds from the strike, and how bright it is, the first 1. */
        final float[] strokes, peaks;
        /** How much of each stroke's light carries on after its peak. */
        final float[] glows;
        /** Which way the bolt's top leans from the strike, in tiles. Where it went into the cloud. */
        final float topX, topY;

        Strike(float x, float y, long at, Bolt bolt, float[] strokes, float[] peaks, float[] glows, float topX, float topY) {
            this.x = x;
            this.y = y;
            this.at = at;
            this.bolt = bolt;
            this.strokes = strokes;
            this.peaks = peaks;
            this.glows = glows;
            this.topX = topX;
            this.topY = topY;
        }

        /** How bright the main channel is drawn, seconds after the strike: 1 at the peak of the first stroke. */
        float channel(float seconds) {
            return leader(seconds) + struck(seconds);
        }

        /** How bright the branches are drawn: only the first stroke goes down them, as in lightning. */
        float branches(float seconds) {
            return leader(seconds) + struckBranches(seconds);
        }

        /** The leader's light, while it comes down: too faint to light anything but itself. */
        private float leader(float seconds) {
            return bolt != null && seconds >= 0f && seconds < strokes[0] ? LEADER_LIGHT : 0f;
        }

        /** The strokes' light down the main channel. */
        private float struck(float seconds) {
            float light = 0f;
            for (int i = 0; i < strokes.length; i++) {
                light += peaks[i] * pulse(seconds - strokes[i], glows[i]);
            }
            return light;
        }

        private float struckBranches(float seconds) {
            return bolt == null ? 0f : peaks[0] * pulse(seconds - strokes[0], glows[0]);
        }

        /** How far down the leader has reached, as a share of the bolt from its top: 1 once the first stroke has fallen. */
        float reached(float seconds) {
            return bolt == null || seconds >= strokes[0] ? 1f : Math.clamp(seconds / strokes[0], 0f, 1f);
        }

        /** The light the strokes give the sky and the world, seconds after the strike, before distance takes its share. */
        float light(float seconds) {
            return struck(seconds) + 0.5f * struckBranches(seconds);
        }
    }

    /** The light of one stroke, seconds after its peak: none before it. */
    static float pulse(float seconds, float glow) {
        return seconds < 0f ? 0f : (float) (Math.exp(-seconds / STROKE_FADES) + glow * Math.exp(-seconds / GLOW_FADES));
    }

    /** How much of a strike's light reaches an eye this far from it along the ground, in tiles. */
    static float nearness(float distance) {
        return distance >= SEEN_WITHIN ? 0f : (1f / (1f + distance / HALF_AT)) * (1f - distance / SEEN_WITHIN);
    }

    /**
     * The shape of a bolt, in the world's axes and tiles, as three arrays of floats a vertex,
     * four vertices a stretch of channel: a ribbon the drawing widens on the screen. The first
     * holds the vertex's end of the stretch and which side of the ribbon it is (-1 or 1); the
     * second the stretch's other end, and 0 at its top end and 1 at its bottom; the third how
     * far down the bolt the leader has to reach before it is lit, how bright it is beside the
     * main channel, and 0 for the main channel, 1 for a branch and 2 for a twig. Direct buffers,
     * never moved once made, for the render thread to draw from.
     */
    static final class Bolt {
        final FloatBuffer ends, others, kinds;
        final int vertices;
        /** The top of the channel, where it leaves the cloud, and its foot. */
        final float topX, topY, topUp, footX, footY, footUp;

        Bolt(float[] ends, float[] others, float[] kinds, int vertices, float topX, float topY, float topUp, float footX, float footY, float footUp) {
            this.ends = direct(ends, vertices);
            this.others = direct(others, vertices);
            this.kinds = direct(kinds, vertices);
            this.vertices = vertices;
            this.topX = topX;
            this.topY = topY;
            this.topUp = topUp;
            this.footX = footX;
            this.footY = footY;
            this.footUp = footUp;
        }

        private static FloatBuffer direct(float[] values, int vertices) {
            FloatBuffer buffer = ByteBuffer.allocateDirect(vertices * FLOATS * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
            buffer.put(values, 0, vertices * FLOATS);
            buffer.flip();
            return buffer;
        }
    }

    private static final Object told = new Object();
    /** The strikes being shown, newest last. Replaced whole under told, read without it. */
    private static volatile Strike[] strikes = new Strike[0];
    private static long struckCount;

    /**
     * The game has told of a strike at map square (x, y). A bolt is made of it when the game
     * has it lit; a rumble with lightning allowed is lightning hidden in the cloud.
     */
    private static Field lightningInfos, lightningState, lightningStrength;
    private static Object idle;

    /**
     * Takes back the flash the game has just started for each player in first person
     * (ThunderStorm.enqueueThunderEvent sets their PlayerLightningInfo to ApplyLightning), so
     * neither its lifted daylight nor its global light reaches what they see. Logic thread.
     */
    static void leaveTheGamesFlash(ThunderStorm storm) {
        try {
            if (lightningInfos == null) {
                lightningInfos = ThunderStorm.class.getDeclaredField("lightningInfos");
                lightningInfos.setAccessible(true);
                Class<?> info = lightningInfos.getType().getComponentType();
                lightningState = info.getDeclaredField("lightningState");
                lightningState.setAccessible(true);
                lightningStrength = info.getDeclaredField("lightningStrength");
                lightningStrength.setAccessible(true);
                for (Object state : lightningState.getType().getEnumConstants()) {
                    if (((Enum<?>) state).name().equals("Idle")) {
                        idle = state;
                    }
                }
                if (idle == null) {
                    throw new NoSuchFieldException("ThunderStorm.LightningState.Idle");
                }
            }
            Object[] infos = (Object[]) lightningInfos.get(storm);
            for (int player = 0; player < infos.length; player++) {
                View view = View.of(player);
                if (view != null && view.firstPerson.active()) {
                    lightningState.set(infos[player], idle);
                    lightningStrength.setFloat(infos[player], 0f);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the game's ThunderStorm has no lightningInfos with a lightningState and lightningStrength to take its flash back with", e);
        }
    }

    static void struck(int x, int y, boolean bolt, boolean hidden, long now) {
        if (!bolt && !hidden) {
            return;
        }
        long seed;
        synchronized (told) {
            seed = (++struckCount) * 0x9E3779B97F4A7C15L ^ ((long) x << 32 | y & 0xffffffffL) ^ now;
        }
        Strike strike = make(x + 0.5f, y + 0.5f, now, bolt, seed);
        synchronized (told) {
            ArrayList<Strike> kept = new ArrayList<>(MOST);
            for (Strike each : strikes) {
                if ((now - each.at) / 1e9f < LASTS) {
                    kept.add(each);
                }
            }
            while (kept.size() >= MOST) {
                kept.remove(0);
            }
            kept.add(strike);
            strikes = kept.toArray(new Strike[0]);
        }
        if (DevBuild.ON) {
            Log.info(String.format(java.util.Locale.ROOT, "lightning: %s at (%d, %d), %d strokes", bolt ? "a bolt" : "a flicker in the cloud", x, y,
                    strike.strokes.length));
        }
    }

    /** The strikes still showing. */
    static Strike[] showing() {
        return strikes;
    }

    /** No strikes: a load, or a test. */
    static void clear() {
        synchronized (told) {
            strikes = new Strike[0];
        }
    }

    /** What is made of a strike at (x, y) told at the moment at: its strokes and, for a bolt, its shape. */
    static Strike make(float x, float y, long at, boolean bolt, long seed) {
        Random random = new Random(seed);
        int count = bolt ? 2 + random.nextInt(3) : 3 + random.nextInt(4);
        float[] times = new float[count], peaks = new float[count], glows = new float[count];
        float time = bolt ? LEADER : 0.02f + random.nextFloat() * 0.05f;
        for (int i = 0; i < count; i++) {
            times[i] = time;
            if (bolt) {
                peaks[i] = i == 0 ? 1f : 0.35f + random.nextFloat() * 0.5f;
                // About one stroke in three carries a current on after it, which holds the channel lit.
                glows[i] = random.nextFloat() < 0.35f ? 0.12f + random.nextFloat() * 0.15f : 0.02f;
                time += 0.045f + random.nextFloat() * 0.09f;
            } else {
                peaks[i] = 0.25f + random.nextFloat() * 0.75f;
                glows[i] = 0.05f + random.nextFloat() * 0.25f;
                time += 0.03f + random.nextFloat() * 0.14f;
            }
        }
        // The channel leaves the cloud a way off to one side of where it strikes: few bolts stand upright.
        double lean = random.nextDouble() * Math.PI * 2;
        float leanBy = CLOUD_BASE * (0.08f + 0.3f * random.nextFloat());
        float topX = x + (float) Math.cos(lean) * leanBy, topY = y + (float) Math.sin(lean) * leanBy;
        Bolt shape = bolt ? shape(topX, topY, CLOUD_BASE, x, y, -UNDER_GROUND, random) : null;
        return new Strike(x, y, at, shape, times, peaks, glows, topX, topY);
    }

    /** A growing list of a bolt's vertices, in the three arrays Bolt holds. */
    private static final class Ribbons {
        float[] ends = new float[4096], others = new float[4096], kinds = new float[4096];
        int vertices;

        void stretch(float[] a, float[] b, float alongA, float alongB, float brightA, float brightB, float rank) {
            if ((vertices + 4) * FLOATS > ends.length) {
                ends = java.util.Arrays.copyOf(ends, ends.length * 2);
                others = java.util.Arrays.copyOf(others, others.length * 2);
                kinds = java.util.Arrays.copyOf(kinds, kinds.length * 2);
            }
            vertex(a, -1f, b, 0f, alongA, brightA, rank);
            vertex(a, 1f, b, 0f, alongA, brightA, rank);
            vertex(b, 1f, a, 1f, alongB, brightB, rank);
            vertex(b, -1f, a, 1f, alongB, brightB, rank);
        }

        private void vertex(float[] here, float side, float[] there, float end, float along, float bright, float rank) {
            int at = vertices * FLOATS;
            ends[at] = here[0];
            ends[at + 1] = here[1];
            ends[at + 2] = here[2];
            ends[at + 3] = side;
            others[at] = there[0];
            others[at + 1] = there[1];
            others[at + 2] = there[2];
            others[at + 3] = end;
            kinds[at] = along;
            kinds[at + 1] = bright;
            kinds[at + 2] = rank;
            kinds[at + 3] = 0f;
            vertices++;
        }
    }

    /**
     * A bolt from (topX, topY, topUp) down to (footX, footY, footUp): its main channel, and
     * branches off the upper part of it that fork again.
     */
    static Bolt shape(float topX, float topY, float topUp, float footX, float footY, float footUp, Random random) {
        Ribbons ribbons = new Ribbons();
        float height = topUp - footUp;
        float[][] main = channel(new float[] {topX, topY, topUp}, new float[] {footX, footY, footUp}, MAIN_LEVELS, random);
        for (int i = 0; i + 1 < main.length; i++) {
            // A jag near the top can rise over the cloud's base: the leader is there from the start.
            float alongA = Math.max((topUp - main[i][2]) / height, 0f), alongB = Math.max((topUp - main[i + 1][2]) / height, 0f);
            ribbons.stretch(main[i], main[i + 1], alongA, alongB, 1f, 1f, 0f);
        }
        int branchCount = 5 + random.nextInt(6);
        for (int b = 0; b < branchCount; b++) {
            // Off the upper three quarters of the channel, more of them near the top, as a tree's boughs are.
            float share = 0.04f + 0.7f * (float) Math.pow(random.nextFloat(), 1.3);
            float[] from = main[Math.min((int) (share * (main.length - 1)), main.length - 2)];
            float alongFrom = Math.max((topUp - from[2]) / height, 0f);
            float reach = height * (0.1f + 0.32f * random.nextFloat()) * (1f - 0.6f * alongFrom);
            float[][] branch = fork(from, reach, BRANCH_LEVELS, random);
            float bright = 0.22f + 0.2f * random.nextFloat();
            lay(ribbons, branch, alongFrom, height, bright, 1f);
            int twigs = random.nextInt(4);
            for (int t = 0; t < twigs; t++) {
                int at = 1 + random.nextInt(branch.length - 2);
                float alongTwig = alongFrom + (from[2] - branch[at][2]) / height;
                float[][] twig = fork(branch[at], reach * (0.2f + 0.3f * random.nextFloat()), TWIG_LEVELS, random);
                lay(ribbons, twig, alongTwig, height, bright * (0.45f + 0.2f * random.nextFloat()) * (1f - (float) at / branch.length), 2f);
            }
        }
        return new Bolt(ribbons.ends, ribbons.others, ribbons.kinds, ribbons.vertices, topX, topY, topUp, footX, footY, footUp);
    }

    /** A branch's ribbons, from bright where it leaves its channel to nothing at its tip. */
    private static void lay(Ribbons ribbons, float[][] points, float alongFrom, float height, float bright, float rank) {
        float top = points[0][2];
        for (int i = 0; i + 1 < points.length; i++) {
            float shareA = (float) i / (points.length - 1), shareB = (float) (i + 1) / (points.length - 1);
            ribbons.stretch(points[i], points[i + 1], alongFrom + (top - points[i][2]) / height, alongFrom + (top - points[i + 1][2]) / height,
                    bright * (1f - 0.85f * shareA), bright * (1f - 0.85f * shareB), rank);
        }
    }

    /** A branch from a point, down and out to one side, about reach long. */
    private static float[][] fork(float[] from, float reach, int levels, Random random) {
        double way = random.nextDouble() * Math.PI * 2;
        // Mostly down: a branch leaves the channel at thirty to fifty degrees from the upright.
        float out = 0.55f + 0.65f * random.nextFloat();
        float[] to = {from[0] + (float) Math.cos(way) * reach * out, from[1] + (float) Math.sin(way) * reach * out, from[2] - reach * 1.2f};
        return channel(from, to, levels, random);
    }

    /**
     * A crooked line from a to b: the straight line, its middle pushed aside, and then each half's
     * middle and so on levels times, each push in proportion to the stretch it is on. Lightning's
     * path is crooked alike at every scale, which this gives.
     */
    static float[][] channel(float[] a, float[] b, int levels, Random random) {
        int count = (1 << levels) + 1;
        float[][] points = new float[count][];
        points[0] = a.clone();
        points[count - 1] = b.clone();
        int level = 0;
        for (int step = count - 1; step > 1; step /= 2, level++) {
            // The first halvings are pushed aside less: a bolt keeps to its way down overall and is crooked close to.
            float jag = JAG * (level < 2 ? 0.4f : level < 4 ? 0.75f : 1f);
            for (int start = 0; start + step < count; start += step) {
                float[] p = points[start], q = points[start + step];
                float dx = q[0] - p[0], dy = q[1] - p[1], du = q[2] - p[2];
                float length = (float) Math.sqrt(dx * dx + dy * dy + du * du);
                points[start + step / 2] = new float[] {
                    (p[0] + q[0]) / 2f + (float) random.nextGaussian() * jag * length,
                    (p[1] + q[1]) / 2f + (float) random.nextGaussian() * jag * length,
                    (p[2] + q[2]) / 2f + (float) random.nextGaussian() * JAG_UP * length,
                };
            }
        }
        return points;
    }

    /**
     * What one view draws of one strike this frame: its bolt and how bright the channel and the
     * branches are and how far down the leader is. Each SkyState holds its own, filled again each frame.
     */
    static final class Seen {
        Bolt bolt;
        float channel, branches, reached;

        Bolt bolt() {
            return bolt;
        }

        float channel() {
            return channel;
        }

        float branches() {
            return branches;
        }

        float reached() {
            return reached;
        }
    }

    /**
     * What the strikes showing at now make of the sky an eye at (x, y, up) sees, into sky: the
     * bolts to draw, the light added to the sky, the cloud's glow, and, while a strike is
     * brighter than the sun or moon, the light that shadows are cast from. Called once the
     * eye has adapted to the light as it was: the flash comes and goes faster than an eye can.
     * Nothing in the game's own look, which draws no bolt.
     */
    static void see(SkyState sky, float x, float y, float up, long now) {
        sky.flashRed = sky.flashGreen = sky.flashBlue = 0f;
        sky.flashX = 0f;
        sky.flashY = 0f;
        sky.flashUp = 1f;
        sky.burst = 0f;
        sky.burstX = 0f;
        sky.burstY = 0f;
        sky.burstUp = 1f;
        sky.seenCount = 0;
        if (Quality.plain()) {
            return;
        }
        float strongest = 0f, strongestCloud = 0f;
        float lightWayX = 0f, lightWayY = 0f, lightWayUp = 1f;
        for (Strike strike : strikes) {
            float seconds = (now - strike.at) / 1e9f;
            if (seconds < 0f || seconds > LASTS) {
                continue;
            }
            float distance = (float) Math.hypot(strike.x - x, strike.y - y);
            float near = nearness(distance);
            if (near <= 0f) {
                continue;
            }
            float light = strike.light(seconds) * near;
            float skyGiven = light * SKY_LIGHT;
            sky.flashRed += RED * skyGiven;
            sky.flashGreen += GREEN * skyGiven;
            sky.flashBlue += BLUE * skyGiven;
            // The cloud is lit about where the channel leaves it, and further off than the ground is.
            float cloud = strike.light(seconds) * nearness(distance * 0.5f) * CLOUD_LIGHT;
            if (cloud > strongestCloud) {
                strongestCloud = cloud;
                float toX = strike.topX - x, toY = strike.topY - y, toUp = CLOUD_BASE - up;
                float length = (float) Math.sqrt(toX * toX + toY * toY + toUp * toUp);
                sky.burstX = toX / length;
                sky.burstY = toY / length;
                sky.burstUp = toUp / length;
            }
            if (strike.bolt != null) {
                if (sky.seenCount < sky.seen.length) {
                    // The channel itself is as bright near or far: what dims it with distance is the air, which the drawing has.
                    float seen = 1f - distance / SEEN_WITHIN;
                    Seen drawn = sky.seen[sky.seenCount++];
                    drawn.bolt = strike.bolt;
                    drawn.channel = strike.channel(seconds) * seen;
                    drawn.branches = strike.branches(seconds) * seen;
                    drawn.reached = strike.reached(seconds);
                }
                if (light > strongest) {
                    strongest = light;
                    float litX = strike.topX + (strike.x - strike.topX) * (1f - LIT_FROM), litY = strike.topY + (strike.y - strike.topY) * (1f - LIT_FROM);
                    float toX = litX - x, toY = litY - y, toUp = CLOUD_BASE * LIT_FROM - up;
                    float length = (float) Math.sqrt(toX * toX + toY * toY + toUp * toUp);
                    lightWayX = toX / length;
                    lightWayY = toY / length;
                    lightWayUp = toUp / length;
                }
            }
        }
        sky.burst = strongestCloud;
        if (strongest <= 0f) {
            return;
        }
        float upAtLeast = Math.max(lightWayUp, LOWEST_LIGHT);
        if (upAtLeast != lightWayUp) {
            float flat = (float) Math.hypot(lightWayX, lightWayY), keep = (float) Math.sqrt(1f - upAtLeast * upAtLeast) / Math.max(flat, 1e-6f);
            lightWayX *= keep;
            lightWayY *= keep;
            lightWayUp = upAtLeast;
        }
        sky.flashX = lightWayX;
        sky.flashY = lightWayY;
        sky.flashUp = lightWayUp;
        // The picture lights a surface by its light's colour squared up and times 2.1: the
        // strike's own colour is put in those terms, and takes the light over while it is the
        // stronger of the two, the shadows with it.
        float given = Math.min(strongest * LIGHT, LIGHT_MOST);
        float sunGives = 2.1f * (float) Math.pow(Math.max(sky.lightRed, Math.max(sky.lightGreen, sky.lightBlue)), 2.2);
        if (given > sunGives) {
            sky.lightX = lightWayX;
            sky.lightY = lightWayY;
            sky.lightUp = lightWayUp;
            sky.lightRed = shown(RED * given);
            sky.lightGreen = shown(GREEN * given);
            sky.lightBlue = shown(BLUE * given);
            // The light shafts stay the sun's: cast from a bolt low over the land they fan across the whole picture as streaks.
        }
    }

    /**
     * How bright the core of the channel is, in the picture's measure, at the peak of the first
     * stroke: far past white at any exposure, so it blooms. And the glow round it, which the
     * air the channel heats and the rain round it give off.
     */
    static final float CORE_LIGHT = 120f, GLOW_LIGHT = 3f;
    /**
     * Half the width of the white core, as the bell of its light, and how far the glow round it
     * reaches before it is a third, in tiles, with the least each is drawn at, in dots of the
     * picture.
     */
    static final float CORE_WIDE = 0.45f, GLOW_WIDE = 2.5f, CORE_DOTS = 1.1f, GLOW_DOTS = 2.5f;

    /**
     * A bolt's stretches, each a box on the picture round the line between its two ends, wide
     * and long enough for its glow, and the two ends on the picture for the light to be worked
     * out from how far each dot is from that line: so each stretch is rounded at both ends and
     * the channel bends at every joint without a seam or a spike. A stretch beyond the
     * picture's far plane is drawn at it, behind all the world; one behind the eye is left out.
     */
    static final String BOLT_VERTEX = """
            #version 120
            uniform vec2 uPixels;
            // The main channel's light, the branches', and how far down the bolt the leader has reached.
            uniform vec3 uGlow;
            uniform vec2 uCore;
            uniform vec2 uHalo;
            varying vec2 vTop;
            varying vec2 vFoot;
            varying float vCoreWide;
            varying float vGlowWide;
            varying float vLight;
            varying float vRange;

            void main() {
                vec4 here = gl_ModelViewProjectionMatrix * vec4(gl_Vertex.xyz, 1.0);
                vec4 there = gl_ModelViewProjectionMatrix * vec4(gl_MultiTexCoord0.xyz, 1.0);
                float side = gl_Vertex.w;
                float end = gl_MultiTexCoord0.w;
                float along = gl_MultiTexCoord1.x;
                float rank = gl_MultiTexCoord1.z;
                float light = gl_MultiTexCoord1.y * (rank < 0.5 ? uGlow.x : uGlow.y);
                // The leader lights only as far down as it has reached.
                light *= 1.0 - smoothstep(uGlow.z - 0.015, uGlow.z, along);
                // The channel comes out of the cloud's base, not off a line.
                light *= smoothstep(0.0, 0.04, along);
                vLight = light;
                vRange = here.w;
                vTop = vec2(0.0);
                vFoot = vec2(0.0);
                vCoreWide = 1.0;
                vGlowWide = 1.0;
                if (here.w < 0.1 || there.w < 0.1 || light <= 0.0) {
                    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                    return;
                }
                vec2 hereDots = (here.xy / here.w * 0.5 + 0.5) * uPixels;
                vec2 thereDots = (there.xy / there.w * 0.5 + 0.5) * uPixels;
                vTop = end < 0.5 ? hereDots : thereDots;
                vFoot = end < 0.5 ? thereDots : hereDots;
                vec2 way = vFoot - vTop;
                float length2 = dot(way, way);
                way = length2 > 1e-8 ? way * inversesqrt(length2) : vec2(0.0, 1.0);
                vec2 across = vec2(-way.y, way.x);
                float dotsPerTile = 0.5 * uPixels.y * gl_ProjectionMatrix[1][1] / here.w;
                float thin = rank < 0.5 ? 1.0 : rank < 1.5 ? 0.55 : 0.35;
                vCoreWide = max(uCore.x * dotsPerTile, uCore.y) * thin;
                vGlowWide = max(uHalo.x * dotsPerTile, uHalo.y) * mix(thin, 1.0, 0.5);
                float reach = max(5.0 * vGlowWide, 3.0 * vCoreWide);
                vec2 aside = across * (side * reach) + way * ((end < 0.5 ? -1.0 : 1.0) * reach);
                here.xy += aside / (0.5 * uPixels) * here.w;
                here.z = min(here.z, here.w * 0.99999);
                gl_Position = here;
            }
            """;

    /**
     * The light of a stretch at a dot of the picture, by how far the dot is from the line
     * between its two ends: the channel's white core and the violet glow round it, through
     * whatever rain or fog lies between the eye and it. The second picture is the one the
     * frame blending reads for what moves by itself: a bolt is there for a few frames and gone,
     * and blended with the frames before it would smear.
     */
    static final String BOLT_FRAGMENT = "#version 120\n" + Sky.ATMOSPHERE + """
            uniform vec2 uLights;
            varying vec2 vTop;
            varying vec2 vFoot;
            varying float vCoreWide;
            varying float vGlowWide;
            varying float vLight;
            varying float vRange;

            void main() {
                vec2 way = vFoot - vTop;
                vec2 from = gl_FragCoord.xy - vTop;
                float along = clamp(dot(from, way) / max(dot(way, way), 1e-6), 0.0, 1.0);
                float off = length(from - way * along);
                float core = exp(-2.0 * off * off / (vCoreWide * vCoreWide));
                float glow = exp(-off / vGlowWide);
                vec3 color = (vec3(1.0, 0.97, 1.0) * (core * uLights.x) + vec3(0.6, 0.58, 1.0) * (glow * uLights.y)) * vLight;
                // Rain and fog hide the channel and spread its light: the glow comes through more of it than the core.
                if (uVeil.x > 0.0) {
                    float veiled = veiling(vRange);
                    color *= mix(1.0 - 0.92 * veiled, 1.0 - 0.6 * veiled, glow * (1.0 - core));
                }
                gl_FragData[0] = vec4(color, 0.0);
                gl_FragData[1] = vec4(vLight * glow > 0.02 ? 1.0 : 0.0);
            }
            """;

    /** A light the picture gives a surface as given, in the colour terms SkyState holds it in. */
    private static float shown(float given) {
        return (float) Math.pow(given / 2.1f, 1 / 2.2);
    }
}
