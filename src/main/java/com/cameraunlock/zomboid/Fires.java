package com.cameraunlock.zomboid;

import java.util.ArrayList;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoFire;
import zombie.iso.objects.IsoFireManager;

/**
 * Fire as first person shows it: flames that lick up out of what burns, smoke that rolls up off
 * them and drifts with the wind, and the light they throw. The game draws a burning square as a
 * few flat sprites over it, and a fireplace, a stove or a burn barrel as a sprite of flames laid
 * over the piece. Here each is a body of flame and smoke, marched through along each pixel's ray
 * (see FireDraw), and a light that flickers as the flames do.
 *
 * Two kinds: a square that burns (a house on fire, a Molotov's spill, a campfire), which the game
 * keeps as an IsoFire in IsoFireManager.FireStack and the logic thread gathers each picture, and
 * a hearth, a fire inside a piece the mod stands a model of, which the chunk's build notes where
 * the model is stood (Models.Instances.fire). The OwnFire row of [Content] switches both off,
 * and the game's own look (Quality.plain) has neither: the picture then draws the game's sprites.
 */
final class Fires {
    /**
     * One fire as the picture takes it, FLOATS long: where its foot is on the map, its flames'
     * half width at the foot, how high they reach and how high its smoke rises over them, in
     * tiles; how strongly the flames burn and how thick the smoke is; a number of its own that
     * sets its flames apart from the next fire's; whether it is a body of flame (VOLUME) or the
     * glow through a stove's grille (WINDOW). A window also has the way along it and the way out
     * of it on the map, and its half width and height. Then the light: how strong, and where.
     */
    static final int X = 0, Y = 1, UP = 2, RADIUS = 3, TALL = 4, SMOKE = 5, FLAME = 6, THICK = 7, SEED = 8, KIND = 9,
            ALONG_X = 10, ALONG_Y = 11, OUT_X = 12, OUT_Y = 13, WIDE = 14, HIGH = 15, LIGHT = 16, LIGHT_X = 17, LIGHT_Y = 18, LIGHT_UP = 19;
    static final int FLOATS = 20;
    static final float VOLUME = 0f, WINDOW = 1f;

    /** The most fires a picture draws, the nearest, and the most of them that light it. */
    static final int MOST = 24, LIGHTS = 8;
    /** How far from the eye a square that burns is gathered, in tiles. */
    static final float WITHIN = 70f;

    /** The colour of firelight, red, green and blue. */
    static final float RED = 1f, GREEN = 0.5f, BLUE = 0.17f;

    /** The light shaders take from the fires: LIGHTS of them, each where it is and how strong, and their colour. */
    static final String GLSL = """
            uniform vec4 uFireAt[8];
            uniform vec3 uFireColor;

            // What the fires give a surface at p that faces n: lights thinning with the square of
            // the distance, as the muzzle flash's does, a little reaching round to what faces away.
            vec3 fireLight(vec3 p, vec3 n) {
                vec3 sum = vec3(0.0);
                for (int i = 0; i < 8; i++) {
                    vec4 at = uFireAt[i];
                    if (at.w <= 0.0) {
                        break;
                    }
                    vec3 to = at.xyz - p;
                    float far2 = dot(to, to);
                    vec3 dir = to * inversesqrt(max(far2, 0.0001));
                    sum += vec3(at.w * (0.15 + 0.85 * max(dot(n, dir), 0.0)) / (1.0 + 1.3 * far2));
                }
                return sum * uFireColor;
            }
            """;

    private Fires() {}

    /** Whether the picture draws its own fire in place of the game's sprites. */
    static boolean own() {
        return Own.fire();
    }

    /**
     * The shape of a square's fire, into a record at `at`: by the stage of its life the game
     * has it at, whether it is smoke alone and whether it is a campfire. The game's stages are
     * 0 a small flame, 1 and 3 a larger one with smoke, 2 the fire at its height and 4 smoke
     * alone as it dies (IsoFire.updateFromTimer).
     */
    static void square(int stage, boolean smokeOnly, boolean campfire, float[] to, int at) {
        float radius, tall, smoke, flame, thick, light;
        if (smokeOnly) {
            radius = 0.5f; tall = 0.3f; smoke = 5f; flame = 0f; thick = 1.6f; light = 0f;
        } else if (campfire) {
            radius = 0.2f; tall = 0.5f; smoke = 2f; flame = 0.9f; thick = 0.35f; light = 0.9f;
        } else {
            switch (stage) {
                case 0 -> { radius = 0.25f; tall = 0.55f; smoke = 0f; flame = 0.8f; thick = 0f; light = 0.7f; }
                case 2 -> { radius = 0.5f; tall = 2.2f; smoke = 6f; flame = 1f; thick = 1.5f; light = 2f; }
                case 4 -> { radius = 0.35f; tall = 0.4f; smoke = 2.5f; flame = 0f; thick = 0.6f; light = 0f; }
                default -> { radius = 0.38f; tall = 1.2f; smoke = 3.5f; flame = 1f; thick = 1f; light = 1.2f; }
            }
        }
        to[at + RADIUS] = radius;
        to[at + TALL] = tall;
        to[at + SMOKE] = smoke;
        to[at + FLAME] = flame;
        to[at + THICK] = thick;
        to[at + KIND] = VOLUME;
        to[at + LIGHT] = light;
        to[at + LIGHT_X] = to[at + X];
        to[at + LIGHT_Y] = to[at + Y];
        to[at + LIGHT_UP] = to[at + UP] + tall * 0.4f;
    }

    /**
     * Where the fire is in a model of a hearth, in the model's own metres (x along its front, y
     * back from it, up from its foot), and its shape, as HEARTH floats, or null for a model that
     * holds no fire. A wood stove's fire is shut in: what shows is its glow through the grille of
     * its door, a window over the door's face just behind the grille's bars.
     */
    static final int HEARTH_X = 0, HEARTH_Y = 1, HEARTH_UP = 2, HEARTH_RADIUS = 3, HEARTH_TALL = 4, HEARTH_SMOKE = 5, HEARTH_THICK = 6,
            HEARTH_KIND = 7, HEARTH_WIDE = 8, HEARTH_HIGH = 9, HEARTH_LIGHT = 10, HEARTH_LIGHT_OUT = 11, HEARTH = 12;
    private static final float[] FIREPLACE = {0f, 0f, 0.08f, 0.17f, 0.42f, 0f, 0f, VOLUME, 0f, 0f, 0.8f, 0.25f};
    private static final float[] BURN_BARREL = {0f, 0f, 0.58f, 0.2f, 0.65f, 2.5f, 0.4f, VOLUME, 0f, 0f, 1f, 0f};
    private static final float[] COOKING_PIT = {0f, 0f, 0.05f, 0.2f, 0.5f, 2f, 0.35f, VOLUME, 0f, 0f, 0.9f, 0f};
    private static final float[] COOKING_PIT_SIMPLE = {0f, 0f, 0.02f, 0.2f, 0.55f, 2f, 0.35f, VOLUME, 0f, 0f, 0.9f, 0f};
    /**
     * The stove's door is its front, 0.24 m before its middle and 0.02 m thick, and the bars of
     * its grille stand 0.004 m proud of the door from x -0.16 to 0.11 and from 0.3 m up to 0.69 m
     * (tools/models/furniture.py, wood_stove): the glow is 1.5 mm out of the door's face.
     */
    private static final float[] WOOD_STOVE = {-0.025f, -0.2615f, 0.3f, 0f, 0f, 0f, 0f, WINDOW, 0.135f, 0.39f, 0.45f, 0.35f};

    static float[] hearth(String model) {
        return switch (model) {
            case "fireplace" -> FIREPLACE;
            case "burn_barrel" -> BURN_BARREL;
            case "cooking_pit" -> COOKING_PIT;
            case "cooking_pit_simple" -> COOKING_PIT_SIMPLE;
            case "wood_stove" -> WOOD_STOVE;
            default -> null;
        };
    }

    /**
     * The record of a hearth's fire, into to at `at`, for a model stood with its middle at
     * (centreX, centreY) on the map and its foot at foot, turned and scaled as Models.Instances.add
     * has it. seed sets it apart from the next one.
     */
    static void placeHearth(float[] hearth, float centreX, float centreY, float foot, int quarterTurns, float scale, float scaleUp, float seed,
            float[] to, int at) {
        float[] turned = new float[2];
        Models.turn(quarterTurns, hearth[HEARTH_X] * scale, hearth[HEARTH_Y] * scale, turned);
        to[at + X] = centreX + turned[0];
        to[at + Y] = centreY + turned[1];
        to[at + UP] = foot + hearth[HEARTH_UP] * scaleUp;
        to[at + RADIUS] = hearth[HEARTH_RADIUS] * scale;
        to[at + TALL] = hearth[HEARTH_TALL] * scaleUp;
        to[at + SMOKE] = hearth[HEARTH_SMOKE] * scaleUp;
        to[at + FLAME] = 1f;
        to[at + THICK] = hearth[HEARTH_THICK];
        to[at + SEED] = seed;
        to[at + KIND] = hearth[HEARTH_KIND];
        Models.turn(quarterTurns, 1f, 0f, turned);
        to[at + ALONG_X] = turned[0];
        to[at + ALONG_Y] = turned[1];
        // The model's front is its -y.
        Models.turn(quarterTurns, 0f, -1f, turned);
        to[at + OUT_X] = turned[0];
        to[at + OUT_Y] = turned[1];
        to[at + WIDE] = hearth[HEARTH_WIDE] * scale;
        to[at + HIGH] = hearth[HEARTH_HIGH] * scaleUp;
        to[at + LIGHT] = hearth[HEARTH_LIGHT];
        float out = hearth[HEARTH_LIGHT_OUT] * scale;
        to[at + LIGHT_X] = to[at + X] + to[at + OUT_X] * out;
        to[at + LIGHT_Y] = to[at + Y] + to[at + OUT_Y] * out;
        to[at + LIGHT_UP] = foot + (hearth[HEARTH_UP] + Math.max(hearth[HEARTH_TALL], hearth[HEARTH_HIGH]) * 0.5f) * scaleUp;
    }

    /** A number of a square's own, the same each time it is asked, that sets one fire's flames apart from the next. */
    static float seed(int x, int y, int z) {
        int h = x * 73856093 ^ y * 19349663 ^ z * 83492791;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xffff) / 65536f * 97f;
    }

    /**
     * How bright a fire's light is at seconds on the clock, as a share of its whole: the flames
     * rise and fall, so the light breathes and now and then dips. 0.62 to 1.
     */
    static float flicker(float seconds, float seed) {
        float wave = 0.5f * (float) Math.sin(7.3 * seconds + seed) + 0.3f * (float) Math.sin(13.1 * seconds + 2.1 * seed)
                + 0.2f * (float) Math.sin(23.7 * seconds + 3.7 * seed);
        return 0.81f + 0.19f * wave;
    }

    /**
     * Gathers the squares that burn near the eye into sky.fires, the nearest first-come up to
     * MOST, with their feet stood on the ground as the picture has it. Logic thread, which is the
     * one that updates the game's fires. Nothing when the picture draws the game's sprites.
     */
    static void gather(SkyState sky, Terrain terrain, float eyeX, float eyeY, float eyeUp) {
        sky.fireCount = 0;
        if (!own()) {
            return;
        }
        ArrayList<IsoFire> fires = IsoFireManager.FireStack;
        float[] to = sky.fires;
        float[] far = sky.fireFar;
        for (int i = 0; i < fires.size(); i++) {
            IsoFire fire = fires.get(i);
            IsoGridSquare square = fire.getSquare();
            if (square == null) {
                continue;
            }
            int x = square.getX(), y = square.getY(), z = square.getZ();
            float dx = x + 0.5f - eyeX, dy = y + 0.5f - eyeY;
            float distance = dx * dx + dy * dy;
            if (distance > WITHIN * WITHIN) {
                continue;
            }
            int slot = sky.fireCount;
            if (slot == MOST) {
                // Full: this one takes the place of the furthest, if it is nearer.
                slot = 0;
                for (int k = 1; k < MOST; k++) {
                    if (far[k] > far[slot]) {
                        slot = k;
                    }
                }
                if (far[slot] <= distance) {
                    continue;
                }
            } else {
                sky.fireCount++;
            }
            far[slot] = distance;
            int at = slot * FLOATS;
            to[at + X] = x + 0.5f;
            to[at + Y] = y + 0.5f;
            to[at + UP] = z * ViewCamera.LEVEL_HEIGHT + terrain.lift(x + 0.5f, y + 0.5f, z);
            to[at + SEED] = seed(x, y, z);
            square(fire.lifeStage, fire.smoke, fire.isCampfire(), to, at);
        }
    }

    /**
     * Picks the LIGHTS strongest lights where the eye is among the n records of fires, flickered
     * for seconds, into out as x, y, up and strength each, with a strength of 0 after the last.
     */
    static void lights(float[] fires, int n, float eyeX, float eyeY, float eyeUp, float seconds, float[] out, float[] weight) {
        int kept = 0;
        for (int i = 0; i < n; i++) {
            int at = i * FLOATS;
            float strength = fires[at + LIGHT];
            if (strength <= 0f) {
                continue;
            }
            float dx = fires[at + LIGHT_X] - eyeX, dy = fires[at + LIGHT_Y] - eyeY, du = fires[at + LIGHT_UP] - eyeUp;
            float reach = strength / (1f + 0.05f * (dx * dx + dy * dy + du * du));
            int slot = kept;
            if (kept == LIGHTS) {
                slot = 0;
                for (int k = 1; k < LIGHTS; k++) {
                    if (weight[k] < weight[slot]) {
                        slot = k;
                    }
                }
                if (weight[slot] >= reach) {
                    continue;
                }
            } else {
                kept++;
            }
            weight[slot] = reach;
            out[slot * 4] = fires[at + LIGHT_X];
            out[slot * 4 + 1] = fires[at + LIGHT_Y];
            out[slot * 4 + 2] = fires[at + LIGHT_UP];
            out[slot * 4 + 3] = strength * flicker(seconds, fires[at + SEED]);
        }
        for (int slot = kept; slot < LIGHTS; slot++) {
            out[slot * 4 + 3] = 0f;
        }
    }
}
