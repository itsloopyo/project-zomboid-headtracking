package com.cameraunlock.zomboid;

import java.util.ArrayList;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
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
 * keeps as an IsoFire and the logic thread gathers each picture, and
 * a hearth, a fire inside a piece the mod stands a model of, which the chunk's build notes where
 * the model is stood (Models.Instances.fire), or where the game's sprite stands for a hearth the
 * mod has no model of. With the OwnFire row of [Content] off, and in the game's own look
 * (Quality.plain), the same fires are drawn as the game's own flames and smoke, its frames stood
 * up where they burn, with no light of the picture's: the game lights round them itself.
 */
final class Fires {
    /**
     * One fire as the picture takes it, FLOATS long: where its foot is on the map, its flames'
     * half width at the foot, how high they reach and how high its smoke rises over them, in
     * tiles; how strongly the flames burn and how thick the smoke is; a number of its own that
     * sets its flames apart from the next fire's; whether it is a body of flame (VOLUME) or the
     * glow through a stove's grille (WINDOW). A window also has the way along it and the way out
     * of it on the map, and its half width and height. Then the light: how strong, and where.
     * Then how far over its foot a ceiling stops its smoke, or 0 where nothing does. Last, the
     * scale the game draws its own frames of the fire at: 1 for a square that burns, half for a hearth,
     * and how far its light reaches, in tiles.
     */
    static final int X = 0, Y = 1, UP = 2, RADIUS = 3, TALL = 4, SMOKE = 5, FLAME = 6, THICK = 7, SEED = 8, KIND = 9,
            ALONG_X = 10, ALONG_Y = 11, OUT_X = 12, OUT_Y = 13, WIDE = 14, HIGH = 15, LIGHT = 16, LIGHT_X = 17, LIGHT_Y = 18, LIGHT_UP = 19, CEILING = 20, LOOK = 21, REACH = 22;
    static final int FLOATS = 23;
    static final float VOLUME = 0f, WINDOW = 1f;

    /** The most squares that burn a picture draws, the nearest, and the most fires that light it. */
    static final int MOST = 64, LIGHTS = 8;
    /** How far from the eye a square that burns is gathered, in tiles: as far as the game keeps the map loaded round it. */
    static final float WITHIN = 90f;
    /** How much further off than it is a square of smoke alone is taken to be, when there are more squares than MOST: flames are kept first. */
    private static final float SMOKE_FARTHER = 2f;
    /** How much of the thickness of a square's smoke goes into the column its block of two squares by two raises: four as thick fires make it twice as thick. */
    private static final float BLOCK_SHARE = 1f / 3f;

    /** The colour of firelight, red, green and blue. */
    static final float RED = 1f, GREEN = 0.5f, BLUE = 0.17f;

    /** The light shaders take from the fires: LIGHTS of them, each where it is and how strong, and their colour. */
    static final String GLSL = """
            uniform vec4 uFireAt[8];
            uniform float uFireReach[8];
            uniform vec3 uFireColor;

            // How much of a fire's light is left far tiles from it: thinning with the square of the
            // distance, and gone at its reach, the game's own radius for it.
            float fireFalloff(float far, float reach) {
                return (1.0 - smoothstep(reach * 0.55, reach, far)) / (1.0 + 0.6 * far * far);
            }

            // What the fires give a figure, a held weapon or a scope at p that faces n, a little reaching round to what faces away.
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
                    // Unshadowed, it thins faster: it reaches a figure through the walls between.
                    sum += vec3(at.w * (0.15 + 0.85 * max(dot(n, dir), 0.0)) * (1.0 - smoothstep(uFireReach[i] * 0.55, uFireReach[i], sqrt(far2)))
                            / (1.0 + 1.3 * far2));
                }
                return sum * uFireColor;
            }
            """;

    private Fires() {}

    /** Whether the picture draws its own fire, and not the game's flames and smoke. */
    static boolean own() {
        return Own.fire();
    }

    /** A ceiling stops smoke this far under it, in tiles: it spreads there. */
    static final float UNDER_CEILING = 0.08f;

    /** Whether a sprite's name is one of the game's animations of flames or of smoke: "Fire01" .. "Smoke03". */
    static boolean isLook(String name, String kind) {
        return name != null && name.length() == kind.length() + 2 && name.startsWith(kind) && Character.isDigit(name.charAt(kind.length()))
                && Character.isDigit(name.charAt(kind.length() + 1));
    }

    /** The sizes of a square's fire: a small flame, a larger one with smoke, the fire at its height, and smoke alone as it dies. */
    static final int SMALL = 0, MEDIUM = 1, HEIGHT = 2, DYING = 3;

    /**
     * The size of a square's fire at a stage of its life. A fire that spreads runs through stages
     * 0 to 5: a small flame at 0 and 1, larger with smoke at 2 and 4, its height at 3 and smoke alone
     * at 5 (IsoFire.updateFromTimer). A permanent one, a campfire, is set to stages 0 to 4: small,
     * larger, height, larger and smoke (IsoFire.setLifeStage).
     */
    static int size(int stage, boolean permanent) {
        if (permanent) {
            return switch (stage) {
                case 0 -> SMALL;
                case 2 -> HEIGHT;
                case 4 -> DYING;
                default -> MEDIUM;
            };
        }
        return switch (stage) {
            case 0, 1 -> SMALL;
            case 3 -> HEIGHT;
            case 5 -> DYING;
            default -> MEDIUM;
        };
    }

    /**
     * The shape of a square's fire, into a record at `at`: by its size (see size), whether it is
     * smoke alone and whether it is a campfire.
     */
    static void square(int size, boolean smokeOnly, boolean campfire, float[] to, int at) {
        float radius, tall, smoke, flame, thick, light, reach;
        if (smokeOnly) {
            radius = 0.5f; tall = 0.3f; smoke = 5f; flame = 0f; thick = 1.6f; light = 0f; reach = 0f;
        } else if (campfire && size != DYING) {
            radius = 0.2f; tall = 0.5f; smoke = 2f; flame = 0.9f; thick = 0.35f; light = 0.9f; reach = 5f;
        } else {
            // The light reaches as far as the game's light of the fire does at that stage (IsoFire.updateFromTimer: 5, 8, 12 squares).
            switch (size) {
                case SMALL -> { radius = 0.25f; tall = 0.55f; smoke = 0f; flame = 0.8f; thick = 0f; light = 0.7f; reach = 5f; }
                case HEIGHT -> { radius = 0.5f; tall = 2.2f; smoke = 6f; flame = 1f; thick = 1.5f; light = 2f; reach = 12f; }
                case DYING -> { radius = 0.35f; tall = 0.4f; smoke = 2.5f; flame = 0f; thick = 0.6f; light = 0f; reach = 0f; }
                default -> { radius = 0.38f; tall = 1.2f; smoke = 3.5f; flame = 1f; thick = 1f; light = 1.2f; reach = 8f; }
            }
        }
        to[at + REACH] = reach;
        to[at + RADIUS] = radius;
        to[at + TALL] = tall;
        to[at + SMOKE] = smoke;
        to[at + FLAME] = flame;
        to[at + THICK] = thick;
        to[at + KIND] = VOLUME;
        to[at + LOOK] = 1f;
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
            HEARTH_KIND = 7, HEARTH_WIDE = 8, HEARTH_HIGH = 9, HEARTH_LIGHT = 10, HEARTH_LIGHT_OUT = 11, HEARTH_LIGHT_UP = 12, HEARTH_REACH = 13, HEARTH = 14;
    private static final float[] FIREPLACE = {0f, 0f, 0.08f, 0.17f, 0.42f, 0f, 0f, VOLUME, 0f, 0f, 0.8f, 0.25f, 0f, 6f};
    // The light of a hearth open at its top is over its rim, out of its own body: a drum's 0.88, a pit's grill 0.48.
    private static final float[] BURN_BARREL = {0f, 0f, 0.58f, 0.2f, 0.65f, 2.5f, 0.4f, VOLUME, 0f, 0f, 1f, 0f, 0.35f, 6f};
    private static final float[] COOKING_PIT = {0f, 0f, 0.05f, 0.2f, 0.5f, 2f, 0.35f, VOLUME, 0f, 0f, 0.9f, 0f, 0.35f, 6f};
    private static final float[] COOKING_PIT_SIMPLE = {0f, 0f, 0.02f, 0.2f, 0.55f, 2f, 0.35f, VOLUME, 0f, 0f, 0.9f, 0f, 0.15f, 6f};
    /**
     * The stove's door is its front, 0.24 m before its middle and 0.02 m thick, and the bars of
     * its grille stand 0.004 m proud of the door from x -0.16 to 0.11 and from 0.3 m up to 0.69 m
     * (tools/models/furniture.py, wood_stove): the glow is 1.5 mm out of the door's face.
     */
    private static final float[] WOOD_STOVE = {-0.025f, -0.2615f, 0.3f, 0f, 0f, 0f, 0f, WINDOW, 0.135f, 0.39f, 0.45f, 0.35f, 0f, 4f};

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
        // A shut hearth, a forge, shows no flame: it glows and smokes.
        to[at + FLAME] = hearth[HEARTH_TALL] > 0f || hearth[HEARTH_KIND] == WINDOW ? 1f : 0f;
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
        to[at + LIGHT_UP] = foot + (hearth[HEARTH_UP] + Math.max(hearth[HEARTH_TALL], hearth[HEARTH_HIGH]) * 0.5f + hearth[HEARTH_LIGHT_UP]) * scaleUp;
        to[at + CEILING] = 0f;
        to[at + REACH] = hearth[HEARTH_REACH];
        // IsoFireplace.updateFireSprite: a hearth's flames are drawn at half the game's scale.
        to[at + LOOK] = 0.5f;
    }

    /**
     * Where the fire is in a lit hearth the mod has no model of, standing as the game's sprite on
     * the middle of its square, by the sprite's name: a brazier's basket of coals, a kiln's or a
     * forge's shut body with its chimney, which shows no flame and smokes at the chimney's top,
     * and any other a small fire on the floor. In tiles over the floor; HEARTH floats as hearth gives.
     * The heights are read off the sprites, at 192 pixels a level.
     */
    static float[] unmodelled(String sprite) {
        if (sprite == null) {
            return OPEN;
        }
        return switch (sprite) {
            case "crafted_02_42" -> BRAZIER;
            case "crafted_05_4", "crafted_05_5" -> OVEN_LOW;
            case "crafted_05_6", "crafted_05_7" -> OVEN_HIGH;
            default -> OPEN;
        };
    }

    private static final float[] BRAZIER = {0f, 0f, 0.42f, 0.16f, 0.5f, 1.5f, 0.3f, VOLUME, 0f, 0f, 0.9f, 0f, 0.25f, 6f};
    /**
     * A crafted oven is shut: its smoke rises from its chimney's top and its light is its body's
     * glow, low on it. The chimney of crafted_05_4/5 tops out at pixel 87 and of 6/7 at 53.
     */
    private static final float[] OVEN_LOW = {0f, 0f, 1.72f, 0.06f, 0f, 1.6f, 0.35f, VOLUME, 0f, 0f, 0.6f, 0f, -1.32f, 4f};
    private static final float[] OVEN_HIGH = {0f, 0f, 2.15f, 0.06f, 0f, 1.6f, 0.35f, VOLUME, 0f, 0f, 0.6f, 0f, -1.75f, 4f};
    private static final float[] OPEN = {0f, 0f, 0.02f, 0.18f, 0.45f, 1.5f, 0.3f, VOLUME, 0f, 0f, 0.8f, 0f, 0f, 5f};
    /** A lit barbecue shows no flame: its smoke rises off the grill. */
    static final float[] BARBECUE = {0f, 0f, 0.85f, 0.15f, 0f, 1.2f, 0.25f, VOLUME, 0f, 0f, 0.3f, 0f, -0.5f, 3f};

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
     * Gathers the squares that burn near the eye into sky.fires, up to MOST of the nearest, with
     * their feet stood on the ground as the picture has it. A fire on a square the character
     * cannot see from where they stand gives no light: a light of the picture's casts no shadow,
     * and would come through the walls between. Where fires burn side by side the smoke of each
     * block of two squares by two rises as one column, from the block's first square. In a room
     * the smoke spreads under the ceiling. Logic thread, which is the one that updates the game's
     * fires.
     */
    static void gather(SkyState sky, Terrain terrain, int playerIndex, float eyeX, float eyeY) {
        sky.fireCount = 0;
        ArrayList<IsoFire> spreading = IsoFireManager.FireStack;
        for (int i = 0; i < spreading.size(); i++) {
            take(sky, terrain, spreading.get(i), playerIndex, eyeX, eyeY);
        }
        // A permanent fire, a campfire, is not in FireStack: the game updates it with the cell's other static objects (IsoFire.addToWorld).
        ArrayList<IsoObject> statics = IsoWorld.instance.currentCell.getStaticUpdaterObjectList();
        long now = System.nanoTime();
        if (statics != lookedIn || statics.size() != lookedInSize || now - lookedAt >= LOOK_EVERY_NANOS) {
            standing.clear();
            for (int i = 0; i < statics.size(); i++) {
                if (statics.get(i) instanceof IsoFire fire) {
                    standing.add(fire);
                }
            }
            lookedIn = statics;
            lookedInSize = statics.size();
            lookedAt = now;
        }
        for (int i = 0; i < standing.size(); i++) {
            IsoFire fire = standing.get(i);
            // Put out, a campfire is taken off its square as it leaves the static objects (SCampfireGlobalObject.removeFireObject, IsoFire.extinctFire).
            if (fire.getObjectIndex() >= 0) {
                take(sky, terrain, fire, playerIndex, eyeX, eyeY);
            }
        }
        smokeInBlocks(sky.fires, sky.fireCount);
    }

    /**
     * The campfires among the cell's static objects at the last look, which list that was and
     * how long, and when. They are looked for again when the list is another or of another
     * length, which a campfire lit makes it, and every LOOK_EVERY_NANOS. Logic thread.
     */
    private static final ArrayList<IsoFire> standing = new ArrayList<>();
    private static ArrayList<IsoObject> lookedIn;
    private static int lookedInSize;
    private static long lookedAt;
    private static final long LOOK_EVERY_NANOS = 250_000_000L;

    /** Lets go of the campfires found, as the game leaves a save. */
    static void forget() {
        standing.clear();
        lookedIn = null;
    }

    private static void take(SkyState sky, Terrain terrain, IsoFire fire, int playerIndex, float eyeX, float eyeY) {
        IsoGridSquare square = fire.getSquare();
        if (square == null) {
            return;
        }
        float[] to = sky.fires;
        float[] far = sky.fireFar;
        int x = square.getX(), y = square.getY(), z = square.getZ();
        float dx = x + 0.5f - eyeX, dy = y + 0.5f - eyeY;
        float distance = dx * dx + dy * dy;
        if (distance > WITHIN * WITHIN) {
            return;
        }
        int size = size(fire.lifeStage, fire.perm);
        if (fire.smoke || size == DYING) {
            distance *= SMOKE_FARTHER * SMOKE_FARTHER;
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
                return;
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
        square(size, fire.smoke, fire.perm && fire.isCampfire(), to, at);
        if (!square.isCouldSee(playerIndex)) {
            to[at + LIGHT] = 0f;
        }
        // A room's ceiling is the floor over it, as long as that has not burnt away.
        IsoGridSquare over = IsoWorld.instance.currentCell.getGridSquare(x, y, z + 1);
        boolean ceiled = square.getRoomID() != -1L && over != null && over.getFloor() != null;
        to[at + CEILING] = ceiled ? ViewCamera.LEVEL_HEIGHT - UNDER_CEILING : 0f;
        sky.flameLook[slot] = FireLooks.look(fire, "Fire");
        sky.smokeLook[slot] = FireLooks.look(fire, "Smoke");
    }

    /**
     * Lets the smoke of each block of two squares by two that has fires on more than its first
     * square rise from the first alone, thicker and as high as the highest, off flames as big as
     * the block's biggest: one column where four would stand through each other, each the cost of
     * a fire to draw. The first square's own flames are drawn as big as that too.
     */
    static void smokeInBlocks(float[] fires, int n) {
        for (int i = 0; i < n; i++) {
            int at = i * FLOATS;
            int x = (int) Math.floor(fires[at + X]), y = (int) Math.floor(fires[at + Y]);
            int firstX = x & ~1, firstY = y & ~1;
            if (fires[at + SMOKE] <= 0f || x == firstX && y == firstY) {
                continue;
            }
            for (int k = 0; k < n; k++) {
                int first = k * FLOATS;
                if (k != i && (int) Math.floor(fires[first + X]) == firstX && (int) Math.floor(fires[first + Y]) == firstY
                        && Math.abs(fires[first + UP] - fires[at + UP]) < ViewCamera.LEVEL_HEIGHT * 0.5f && fires[first + SMOKE] > 0f) {
                    // The column rises off the block's flames, the highest of them, and is lit by them.
                    fires[first + THICK] += fires[at + THICK] * BLOCK_SHARE;
                    fires[first + SMOKE] = Math.max(fires[first + SMOKE], fires[at + SMOKE]);
                    fires[first + TALL] = Math.max(fires[first + TALL], fires[at + TALL]);
                    fires[first + RADIUS] = Math.max(fires[first + RADIUS], fires[at + RADIUS]);
                    fires[first + FLAME] = Math.max(fires[first + FLAME], fires[at + FLAME]);
                    fires[at + SMOKE] = 0f;
                    break;
                }
            }
        }
    }

    /**
     * Picks the LIGHTS strongest lights where the eye is among the n records of fires, flickered
     * for seconds, into out as x, y, up and strength each, with a strength of 0 after the last,
     * how far each reaches into reach, and whether it has flames to see into flaming.
     */
    static void lights(float[] fires, int n, float eyeX, float eyeY, float eyeUp, float seconds, float[] out, float[] reach, boolean[] flaming,
            float[] weight) {
        int kept = 0;
        for (int i = 0; i < n; i++) {
            int at = i * FLOATS;
            float strength = fires[at + LIGHT];
            if (strength <= 0f) {
                continue;
            }
            float dx = fires[at + LIGHT_X] - eyeX, dy = fires[at + LIGHT_Y] - eyeY, du = fires[at + LIGHT_UP] - eyeUp;
            float pull = strength / (1f + 0.05f * (dx * dx + dy * dy + du * du));
            int slot = kept;
            if (kept == LIGHTS) {
                slot = 0;
                for (int k = 1; k < LIGHTS; k++) {
                    if (weight[k] < weight[slot]) {
                        slot = k;
                    }
                }
                if (weight[slot] >= pull) {
                    continue;
                }
            } else {
                kept++;
            }
            weight[slot] = pull;
            out[slot * 4] = fires[at + LIGHT_X];
            out[slot * 4 + 1] = fires[at + LIGHT_Y];
            out[slot * 4 + 2] = fires[at + LIGHT_UP];
            out[slot * 4 + 3] = strength * flicker(seconds, fires[at + SEED]);
            reach[slot] = fires[at + REACH];
            flaming[slot] = fires[at + FLAME] > 0f;
        }
        for (int slot = kept; slot < LIGHTS; slot++) {
            out[slot * 4 + 3] = 0f;
        }
    }
}
