package com.cameraunlock.zomboid;

import java.util.ArrayList;
import zombie.GameTime;
import zombie.characters.BodyDamage.BodyDamage;
import zombie.characters.BodyDamage.BodyPart;
import zombie.characters.BodyDamage.BodyPartType;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoGridSquare;
import zombie.iso.weather.ClimateManager;

/**
 * How wet the viewer's own hands, sleeves and what they hold are in the first person picture,
 * for the shaders that draw them (GLSL): two numbers a view.
 *
 * The water standing on them, which beads on the backs of the hands and on the weapon and runs
 * down it, and gives skin, metal and wood their sheen: it builds over SECONDS_TO_WET while rain
 * falls on the viewer, faster the heavier it falls, and dries over SECONDS_TO_DRY out of it, in a
 * room, under a roof or in a vehicle. Snow that melts off the viewer indoors (SnowCarried) leaves
 * its water on them at once.
 *
 * How soaked cloth and skin look, which darkens them: the water on them, or the game's own
 * wetness of the wettest hand or forearm (BodyPart.getWetness, 0 to 100) where that is more. The game
 * dries clothes over its hours, so a coat soaked through stays dark after the beads are gone.
 *
 * Nothing in Low, which draws the viewer as the game does, or with WetOnThePlayer off. Render
 * thread: taken once a frame by the first draw of the viewer that asks.
 */
final class Soaked {
    private Soaked() {}

    /** Seconds of the heaviest rain to wet the viewer through, and seconds out of it for the water on them to dry. */
    static final float SECONDS_TO_WET = 60f, SECONDS_TO_DRY = 240f;
    /** Of the heaviest rain's pace, the pace the lightest wets at. */
    static final float DRIZZLE_PACE = 0.35f;
    /** Rain this light or lighter wets nothing, as the world's ground takes it (SkyState). */
    static final float NO_RAIN = 0.05f;
    /** Of the snow that melts off the viewer, the water it leaves on them. */
    static final float MELT_WETS = 0.6f;
    /** The game's wetness of a body part when it is wet through. */
    static final float GAME_WET_THROUGH = 100f;

    /** Where in a view's record each number is: the water, the soak, and the seconds the running drops go by. */
    static final int WATER = 0, SOAK = 1, CLOCK = 2, FLOATS = 3;
    /** Running drops start over after this many seconds, so the clock keeps its fine steps. */
    private static final float CLOCK_WRAPS = 600f;

    private static final float[][] kept = new float[View.COUNT][FLOATS], shown = new float[View.COUNT][FLOATS];
    private static final float[] dustBefore = new float[View.COUNT];
    private static final int[] takenIn = new int[View.COUNT];
    private static final long[] takenAt = new long[View.COUNT];
    private static final float[] DRY = new float[FLOATS];

    static {
        java.util.Arrays.fill(takenIn, Integer.MIN_VALUE);
    }

    /**
     * The water standing on the viewer seconds on: rain is how hard it falls, 0 to 1, sheltered
     * whether the viewer is out of it, melted how much of the snow on them has melted since.
     */
    static float water(float water, float rain, boolean sheltered, float melted, float seconds) {
        float pace = !sheltered && rain > NO_RAIN ? (DRIZZLE_PACE + (1f - DRIZZLE_PACE) * Math.min(rain, 1f)) / SECONDS_TO_WET : -1f / SECONDS_TO_DRY;
        return Math.clamp(water + pace * seconds + MELT_WETS * Math.max(melted, 0f), 0f, 1f);
    }

    /** How soaked cloth and skin look: the water on them, or the game's wetness of the arms, 0 to GAME_WET_THROUGH, where that is more. */
    static float soak(float water, float gameWetness) {
        return Math.max(water, Math.clamp(gameWetness / GAME_WET_THROUGH, 0f, 1f));
    }

    /**
     * Moves the viewer's record on to this frame and returns it, by WATER, SOAK and CLOCK: not to
     * be changed by the caller. dust is the snow on them as SnowCarried has it this frame.
     */
    static float[] on(int view, IsoGameCharacter viewer, float dust) {
        if (forgotten) {
            forgotten = false;
            for (int i = 0; i < View.COUNT; i++) {
                java.util.Arrays.fill(kept[i], 0f);
                dustBefore[i] = 0f;
                takenIn[i] = Integer.MIN_VALUE;
            }
        }
        float[] record = kept[view];
        int frame = Pipeline.frame();
        if (takenIn[view] != frame) {
            long now = System.nanoTime();
            float seconds = takenIn[view] == Integer.MIN_VALUE || GameTime.isGamePaused() ? 0f : Math.min((now - takenAt[view]) / 1e9f, 0.25f);
            takenIn[view] = frame;
            takenAt[view] = now;
            IsoGridSquare square = viewer.getCurrentSquare();
            boolean sheltered = viewer.getVehicle() != null || square == null || !square.isOutside() || square.haveRoof;
            ClimateManager climate = ClimateManager.getInstance();
            float rain = climate.isSnowing() ? 0f : Math.clamp(climate.getRainIntensity(), 0f, 1f);
            float melted = sheltered ? dustBefore[view] - dust : 0f;
            dustBefore[view] = dust;
            record[WATER] = water(record[WATER], rain, sheltered, melted, seconds);
            record[SOAK] = soak(record[WATER], armsWetness(viewer));
            record[CLOCK] = (record[CLOCK] + seconds) % CLOCK_WRAPS;
        }
        if (!Part.WET_ON_THE_PLAYER.on() || Quality.plain(view)) {
            return DRY;
        }
        System.arraycopy(record, 0, shown[view], 0, FLOATS);
        return shown[view];
    }

    /** Set when the save has been left, for the render thread to start the next save's viewers dry. */
    private static volatile boolean forgotten;

    /** The save has been left: the viewers of the next start dry, with no snow on them to melt. Any thread. */
    static void forget() {
        forgotten = true;
    }

    /** What on last returned for a view: for what is drawn after the viewer in the same frame, the weapon. */
    static float[] shown(int view) {
        return !Part.WET_ON_THE_PLAYER.on() || Quality.plain(view) || takenIn[view] != Pipeline.frame() ? DRY : shown[view];
    }

    /** The game's wetness of the wettest of the viewer's hands and forearms, 0 to GAME_WET_THROUGH. */
    private static float armsWetness(IsoGameCharacter viewer) {
        // A zombie has none: the eye is a zombie's head once the viewer's corpse has risen.
        BodyDamage damage = viewer.getBodyDamage();
        if (damage == null) {
            return 0f;
        }
        ArrayList<BodyPart> parts = damage.getBodyParts();
        float wettest = 0f;
        for (int i = 0; i < parts.size(); i++) {
            BodyPart part = parts.get(i);
            BodyPartType type = part.getType();
            if (type == BodyPartType.Hand_L || type == BodyPartType.Hand_R || type == BodyPartType.ForeArm_L || type == BodyPartType.ForeArm_R) {
                wettest = Math.max(wettest, part.getWetness());
            }
        }
        return wettest;
    }

    /** The bead cells across a metre, the running drops' cells, how high a bead stands in metres, and how many runs a second each drop makes. */
    static final float BEAD_CELLS = 220f, RUN_CELLS = 40f, BEAD_HIGH = 0.0015f, RUN_PACE = 0.25f;

    /**
     * For the shaders that draw the viewer and what they hold: uWet is a view's WATER and SOAK,
     * uWetClock its CLOCK. waterOn says how high water stands at a point, 0 to 1: beads a few
     * millimetres across that sit where the surface faces up, more and larger the wetter it is,
     * and on steep faces a few drops that run a little way down and are gone, each with a thin
     * trail. wetFacing tilts the way a surface faces by that water, each bead a lens.
     *
     * wetChance is hash13 of David Hoskins's Hash without Sine, used under its MIT licence: the
     * notice is in THIRD-PARTY-NOTICES.md.
     */
    static final String GLSL = """
            uniform vec2 uWet;
            uniform float uWetClock;

            float wetChance(vec3 p) {
                p = fract(p * 0.1031);
                p += dot(p, p.zyx + 31.32);
                return fract((p.x + p.y) * p.z);
            }

            // own is the point in the thing's own metres, down the world's down in the same axes,
            // up how far the surface faces up, -1 to 1.
            float waterOn(vec3 own, vec3 down, float up) {
                if (uWet.x <= 0.0) {
                    return 0.0;
                }
                // Where a pixel is wider than a bead, no bead shows.
                float fine = max(length(dFdx(own)), length(dFdy(own)));
                float shown = 1.0 - smoothstep(0.001, 0.003, fine);
                vec3 p = own * BEAD_CELLS;
                vec3 cell = floor(p);
                vec3 within = fract(p) - 0.5 - 0.3 * (vec3(wetChance(cell + 17.0), wetChance(cell + 41.0), wetChance(cell + 73.0)) - 0.5);
                float size = (0.16 + 0.16 * wetChance(cell + 5.0)) * step(wetChance(cell), uWet.x * smoothstep(-0.3, 0.6, up));
                float bead = size > 0.0 ? sqrt(clamp(1.0 - dot(within, within) / (size * size), 0.0, 1.0)) : 0.0;
                // A drop runs through its own cell and starts again: the cells stay where they are
                // on the thing, so a turn of it never moves the runs.
                vec3 q = own * RUN_CELLS;
                vec3 runCell = floor(q);
                float runs = step(wetChance(runCell + 7.0), 0.15 * smoothstep(0.3, 0.9, uWet.x)) * smoothstep(0.3, 0.8, 1.0 - abs(up));
                float phase = fract(uWetClock * RUN_PACE + wetChance(runCell + 3.0));
                vec3 r = fract(q) - 0.5 - (phase - 0.5) * 0.7 * down;
                float along = dot(r, down);
                float wide = dot(r - down * along, r - down * along);
                float drop = sqrt(clamp(1.0 - (wide + 0.4 * along * along) / 0.02, 0.0, 1.0));
                float trail = 0.4 * step(-0.7 * phase, along) * step(along, 0.0) * clamp(1.0 - wide / 0.002, 0.0, 1.0);
                float life = smoothstep(0.0, 0.15, phase) * (1.0 - smoothstep(0.85, 1.0, phase));
                return shown * max(bead, runs * life * max(drop, trail));
            }

            // at is the point in the axes facing is in, own the same point as waterOn had it.
            vec3 wetFacing(vec3 facing, vec3 at, vec3 own, float water) {
                vec3 across = dFdx(at), down = dFdy(at);
                float metre = length(across) / max(length(dFdx(own)), 1e-9);
                float h = water * BEAD_HIGH * metre;
                vec3 first = cross(down, facing), second = cross(facing, across);
                float room = dot(across, first);
                vec3 tilted = abs(room) * facing - sign(room) * (dFdx(h) * first + dFdy(h) * second);
                return dot(tilted, tilted) > 0.0 ? normalize(tilted) : facing;
            }
            """.replace("BEAD_CELLS", glsl(BEAD_CELLS)).replace("RUN_CELLS", glsl(RUN_CELLS)).replace("BEAD_HIGH", glsl(BEAD_HIGH))
            .replace("RUN_PACE", glsl(RUN_PACE));

    private static String glsl(float value) {
        return Float.toString(value);
    }
}
