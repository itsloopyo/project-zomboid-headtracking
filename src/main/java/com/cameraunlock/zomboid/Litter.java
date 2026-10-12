package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Locale;

/**
 * What lies fallen under trees: leaf from the broad-leaved trees, which deepens through autumn
 * and spreads out from under them onto lawns, paths and the gutters of the street, and a duff
 * of needles under the evergreens, which lies the same all year.
 *
 * A chunk's build lays the litter as deep as it lies at the end of autumn, and the picture
 * shows as much of that as the season says (Seasons.litter, through spread), so the ground
 * changes through the year with no chunk built again. Everything is worked out from where the
 * trees and the squares are, so two chunks that share a corner give it the same litter.
 */
final class Litter {
    /**
     * What a tree drops: nothing, leaf in the colour its crown turns (LEAF and one of Flora's
     * HUES after it), needles and cones, or holly's leaves.
     */
    static final byte NONE = 0, LEAF = 1, PINE = LEAF + Flora.HUES, HOLLY = PINE + 1;
    /** How many squares round a chunk its trees are read from: further than any tree's litter reaches. */
    static final int RING = 6;
    /**
     * Leaf lies furthest the way the wind mostly blows, and the wind here is from the south
     * west, to +x and -y. It is the same every day: what has built up over an autumn is what
     * the wind has done over weeks, and a build made under one day's wind would not meet a
     * build made under the next day's at the chunk's edge.
     */
    static final float WIND_X = 0.7071f, WIND_Y = -0.7071f;
    /**
     * How far from under a tree its leaf reaches across the wind, in tiles, how much further
     * along it, how far downwind its middle lies, and how deep it is there. A tree's needles
     * reach NEEDLE_REACH all round. Both stay within RING of the corner they are worked out at.
     */
    static final float LEAF_REACH = 3.4f, LEAF_STRETCH = 1.4f, LEAF_DRIFT = 0.8f, LEAF_GAIN = 0.9f;
    static final float NEEDLE_REACH = 3f, NEEDLE_GAIN = 1.3f;
    /**
     * How much of what falls on ground of each kind stays there: all of it on open ground,
     * most on a path or a yard, and on a street all of it in the gutter and ROAD_MIDDLE of it
     * where traffic went, a kerb's width out.
     */
    static final float MADE_KEEPS = 0.8f, ROAD_MIDDLE = 0.15f, GUTTER = 0.15f, GUTTER_ENDS = 0.7f;
    /**
     * How far the litter a build laid is spread over the ground by the season: a corner shows
     * as much as its depth times this, so at SPREAD_SUMMER only the floor of a wood is leaf,
     * and at SPREAD_MOST, the end of autumn, a tree's leaf reaches well out across a lawn.
     */
    static final float SPREAD_SUMMER = 0.79f, SPREAD_MOST = 2.6f;
    /**
     * At most so many leaves stand proud of the litter on a square, each shown from one of
     * STEPS steps of the spread, the first where the litter shows CARD_FROM deep.
     */
    static final int CARDS_MOST = 5, CONES_MOST = 2, STEPS = 15;
    static final float CARD_FROM = 0.3f;
    /** The share of fallen leaves in their tree's own autumn colour, the rest gone brown, and how much darker on the ground than in the crown. */
    static final float OWN_SHARE = 0.75f, FALLEN_DIM = 0.8f;
    /** The brown of fallen leaf gone brown. */
    static final float BROWN_RED = 0.42f, BROWN_GREEN = 0.25f, BROWN_BLUE = 0.12f;
    /** How many fewer of them show where winter has matted them into the litter. */
    private static final float CARDS_MATTED = 0.4f;
    /**
     * How far through autumn fresh leaf starts to lie on top, and from how far through it the
     * fresh fades, to none at autumn's end. How far through winter the litter has matted down
     * dark, and how dark the old litter of a summer is.
     */
    static final float FRESH_FROM = 0.05f, FRESH_TAKES = 0.2f, FRESH_FADES = 0.7f, MATS_IN = 0.3f, SUMMER_DARK = 0.4f;

    private Litter() {}

    /**
     * What the tree whose sprite is named name and whose square hashes to treeHash
     * (Seasons.treeHash) drops: what Flora says of its crown, evergreen or the colour it turns,
     * so the leaf on the ground is always the leaf that was on the tree.
     */
    static byte kindOf(String name, int treeHash) {
        String species = name == null ? "" : name;
        if (Flora.evergreen(species)) {
            return species.contains("holly") ? HOLLY : PINE;
        }
        return (byte) (LEAF + Flora.autumnHue(species, treeHash));
    }

    static boolean evergreen(byte kind) {
        return kind >= PINE;
    }

    /**
     * How deep each corner of a chunk is in fallen leaf at the end of autumn and in needles,
     * 0 to 1, which kind of broad-leaved tree drops most of the leaf on each square, and which
     * evergreen most of the needles (PINE or HOLLY).
     *
     * kinds says what tree, if any, stands on each square of the chunk and of RING more on
     * every side, x first. A corner takes something from every tree whose square is within
     * RING of it each way, in the same order from either chunk that has the corner.
     */
    static void fields(byte[] kinds, int tiles, float[] leaves, float[] needles, byte[] leafKinds, byte[] needleKinds) {
        int span = tiles + 2 * RING, across = tiles + 1;
        Arrays.fill(leaves, 0f);
        Arrays.fill(needles, 0f);
        Arrays.fill(leafKinds, LEAF);
        Arrays.fill(needleKinds, PINE);
        boolean any = false;
        for (byte kind : kinds) {
            any |= kind != NONE;
        }
        if (!any) {
            return;
        }
        for (int i = 0; i < across; i++) {
            for (int j = 0; j < across; j++) {
                float leaf = 0f, needle = 0f;
                for (int di = 0; di < 2 * RING; di++) {
                    int row = (i + di) * span + j;
                    for (int dj = 0; dj < 2 * RING; dj++) {
                        byte kind = kinds[row + dj];
                        if (kind == NONE) {
                            continue;
                        }
                        if (evergreen(kind)) {
                            needle += CORNER_NEEDLE[di * 2 * RING + dj];
                        } else {
                            leaf += CORNER_LEAF[di * 2 * RING + dj];
                        }
                    }
                }
                leaves[i * across + j] = Math.min(leaf * LEAF_GAIN, 1f);
                needles[i * across + j] = Math.min(needle * NEEDLE_GAIN, 1f);
            }
        }
        int side = 2 * RING + 1;
        for (int i = 0; i < tiles; i++) {
            for (int j = 0; j < tiles; j++) {
                float most = 0f, mostNeedles = 0f;
                for (int di = 0; di < side; di++) {
                    int row = (i + di) * span + j;
                    for (int dj = 0; dj < side; dj++) {
                        byte kind = kinds[row + dj];
                        if (kind == NONE) {
                            continue;
                        }
                        if (evergreen(kind)) {
                            if (SQUARE_NEEDLE[di * side + dj] > mostNeedles) {
                                mostNeedles = SQUARE_NEEDLE[di * side + dj];
                                needleKinds[i * tiles + j] = kind;
                            }
                        } else if (SQUARE_LEAF[di * side + dj] > most) {
                            most = SQUARE_LEAF[di * side + dj];
                            leafKinds[i * tiles + j] = kind;
                        }
                    }
                }
            }
        }
    }

    /**
     * What a tree drops at a corner of the squares and at the middle of a square, by how far
     * its square is from it, worked out once: a corner is a whole number of tiles from the
     * middle of every square and half a tile more, a square's middle a whole number. The
     * corner's are for the squares from RING before it to RING after, the square's for those
     * RING either side of it.
     */
    private static final float[] CORNER_LEAF = table(2 * RING, 0.5f, true), CORNER_NEEDLE = table(2 * RING, 0.5f, false);
    private static final float[] SQUARE_LEAF = table(2 * RING + 1, 0f, true), SQUARE_NEEDLE = table(2 * RING + 1, 0f, false);

    private static float[] table(int side, float half, boolean leaf) {
        float[] table = new float[side * side];
        for (int di = 0; di < side; di++) {
            for (int dj = 0; dj < side; dj++) {
                float awayX = di - RING + half, awayY = dj - RING + half;
                table[di * side + dj] = leaf ? leafReached(awayX, awayY) : reached(awayX, awayY, NEEDLE_REACH);
            }
        }
        return table;
    }

    /** How much a tree whose square's middle is (awayX, awayY) from a place drops there, as the near of a round reach. */
    private static float reached(float awayX, float awayY, float reach) {
        float near = 1f - (float) Math.sqrt(awayX * awayX + awayY * awayY) / reach;
        return near > 0f ? near * near : 0f;
    }

    /** The same for a broad-leaved tree's leaf, which the wind has taken downwind of it and spread further along its way. */
    static float leafReached(float awayX, float awayY) {
        // From the middle of the tree's leaf, which is downwind of its foot, to the place.
        float x = -awayX - LEAF_DRIFT * WIND_X, y = -awayY - LEAF_DRIFT * WIND_Y;
        float along = (x * WIND_X + y * WIND_Y) / LEAF_STRETCH, across = -x * WIND_Y + y * WIND_X;
        float near = 1f - (float) Math.sqrt(along * along + across * across) / LEAF_REACH;
        return near > 0f ? near * near : 0f;
    }

    /** How much of what falls on a square of the ground stays: by what the ground is, and none under a roof or on water. */
    static float keeps(byte ground, boolean open) {
        if (!open) {
            return 0f;
        }
        return ground == Terrain.NATURAL || ground == Terrain.STREET ? 1f : ground == Terrain.MADE ? MADE_KEEPS : 0f;
    }

    /** How much of it stays on a point of a street by how far out from the kerb it is (Kerbs' field): all in the gutter, a little in the middle. */
    static float gutter(float kerb) {
        if (kerb <= 0f) {
            return 1f;
        }
        float t = Math.clamp((kerb - GUTTER) / (GUTTER_ENDS - GUTTER), 0f, 1f);
        return 1f - (1f - ROAD_MIDDLE) * t * t * (3f - 2f * t);
    }

    /**
     * The part of a polygon of count corners, (a, b) each with its value of Kerbs' field, on the
     * street's side of the kerb or on the land's (where the field is not above nothing), cut
     * where the field, taken as running evenly along each side, is nothing. Into the out arrays,
     * how many corners it has given back: under 3 for none.
     */
    static int side(int count, float[] a, float[] b, float[] kerb, boolean street, float[] outA, float[] outB, float[] outKerb) {
        int out = 0;
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            boolean here = kerb[i] > 0f == street, next = kerb[j] > 0f == street;
            if (here) {
                outA[out] = a[i];
                outB[out] = b[i];
                outKerb[out++] = kerb[i];
            }
            if (here != next) {
                float t = kerb[i] / (kerb[i] - kerb[j]);
                outA[out] = a[i] + (a[j] - a[i]) * t;
                outB[out] = b[i] + (b[j] - b[i]) * t;
                outKerb[out++] = 0f;
            }
        }
        return out;
    }

    /** How far the season spreads the litter: see SPREAD_SUMMER. */
    static float spread(float litter) {
        return SPREAD_SUMMER + (SPREAD_MOST - SPREAD_SUMMER) * (litter - litterSummer()) / (1f - litterSummer());
    }

    private static float litterSummer() {
        return Seasons.litter(Seasons.SUMMER, 0.5f);
    }

    /** How much of the leaf on top is freshly fallen, bright in its autumn colours: through autumn, fading to none by its end. */
    static float fresh(int season, float progression) {
        if (season != Seasons.AUTUMN) {
            return 0f;
        }
        return smooth((progression - FRESH_FROM) / FRESH_TAKES) * (1f - smooth((progression - FRESH_FADES) / (1f - FRESH_FADES)));
    }

    /** How dark and matted the litter under the fresh leaf is: a summer's old leaf part way, last autumn's at its darkest through winter, brightening as autumn's falls on it. */
    static float dark(int season, float progression) {
        return switch (season) {
            case Seasons.AUTUMN -> SUMMER_DARK * (1f - smooth(progression / 0.6f));
            case Seasons.WINTER -> smooth(progression / MATS_IN);
            case Seasons.SPRING -> 1f - (1f - SUMMER_DARK) * smooth(progression);
            default -> SUMMER_DARK;
        };
    }

    /**
     * From which step of the spread the leaf a square's card number card is shows, where the
     * leaf a build laid there is cover deep: 1 to STEPS, or 0 for a leaf that never shows.
     * The more leaf, the more of the square's leaves show, and they show in the same order
     * every time.
     */
    static int cardStep(int x, int y, int card, float cover) {
        if (cover <= 0f) {
            return 0;
        }
        float wants = CARD_FROM + (1f - CARD_FROM) * (card + Flora.unit(Flora.mix(x, y, 150 + card), 0)) / CARDS_MOST;
        float spread = wants / cover;
        return spread > SPREAD_MOST ? 0 : Math.max(1, (int) Math.ceil(spread / SPREAD_MOST * STEPS));
    }

    /** How far the leaves that stand proud of the litter are spread (see cardStep): as the litter, and fewer once matted. */
    static float cardSpread(int season, float progression) {
        return spread(Seasons.litter(season, progression)) * (1f - CARDS_MATTED * dark(season, progression) * (1f - fresh(season, progression)));
    }

    /** The last step of the spread a leaf shows from that a spread this far shows: the shader's own count of it. */
    static int stepOf(float spread) {
        return (int) Math.floor(spread / SPREAD_MOST * STEPS + 1e-4f);
    }

    /** Which fallen leaf a card under a broad-leaved tree of the kind is, by a chance from 0 to 1: OWN_SHARE of them the tree's own autumn colour, the rest brown. */
    static int leafPick(byte kind, float chance) {
        return chance < OWN_SHARE ? Flora.FALLEN_HUE + kind - LEAF : Flora.FALLEN_BROWN;
    }

    /** How many cones, or holly leaves, lie on a square as deep in needles as needle says. */
    static int cones(int x, int y, float needle) {
        return Math.min((int) (needle * (CONES_MOST + 0.6f) * Flora.unit(Flora.mix(x, y, 160), 0)), CONES_MOST);
    }

    /**
     * What the world's fragment shader draws litter with, joined on before its main: it uses
     * GROUND_NOISE's grain and patches. uLitterSeason is spread, fresh, dark and cardSpread,
     * and the constants are this class's.
     */
    static final String GLSL = String.format(Locale.ROOT, """
            uniform vec4 uLitterSeason;
            const float LITTER_SPREAD_MOST = %f;
            const float LITTER_STEPS = %d.0;
            const float LITTER_KIND_STEP = %f;
            const float LITTER_LEAF = %d.0;
            const float LITTER_OWN_SHARE = %f;
            const float LITTER_FALLEN_DIM = %f;
            const vec3 LITTER_BROWN = vec3(%f, %f, %f);
            const float LEAF_BROWN = %d.0;
            const float LEAF_TWIG = %d.0;
            const float LEAF_CONE = %d.0;
            const float LEAF_HUE = %d.0;

            """, SPREAD_MOST, STEPS, Flora.LITTER_KIND_STEP, LEAF, OWN_SHARE, FALLEN_DIM, BROWN_RED, BROWN_GREEN, BROWN_BLUE,
            Flora.FALLEN_BROWN, Flora.TWIG, Flora.CONE, Flora.FALLEN_HUE) + """
            // The colour of a leaf fallen from a tree of the kind (LITTER_LEAF and on), by a chance from 0 to 1, as Litter.leafPick chooses a card's:
            // the colour the tree's crown turns (autumnHue), or brown.
            vec3 litterLeafColour(float kind, float chance) {
                return chance < LITTER_OWN_SHARE ? autumnHue(kind - LITTER_LEAF) * LITTER_FALLEN_DIM : LITTER_BROWN * (0.8 + 0.4 * chance);
            }

            // Leaves freshly fallen on litter of the colour under: in some cells of two grids
            // laid askew of the map's lines, as many as amount says, each a pointed oval its
            // own way round. foot is how much ground a pixel covers: where a leaf is finer than
            // that, the leaves are drawn as the colour they make together.
            vec3 litterFresh(vec2 p, vec3 under, float kind, float amount, float foot) {
                if (amount <= 0.0) {
                    return under;
                }
                vec3 result = under;
                for (int layer = 0; layer < 2; layer++) {
                    float size = layer == 0 ? 7.0 : 9.0;
                    vec2 turn = layer == 0 ? vec2(0.8, 0.6) : vec2(0.6, -0.8);
                    vec2 q = vec2(p.x * turn.x - p.y * turn.y, p.x * turn.y + p.y * turn.x) * size + float(layer) * 17.3;
                    vec2 cell = floor(q);
                    float chance = grain(cell + 31.0 * float(layer));
                    float drawn = 1.0 - smoothstep(0.35, 0.7, foot * size);
                    float has = step(chance, amount * 0.8);
                    float lie = grain(cell + 57.0) * 6.2832, shade = grain(cell + 91.0);
                    vec2 way = vec2(cos(lie), sin(lie));
                    vec2 r = fract(q) - 0.5 - (vec2(shade, fract(shade * 7.0)) - 0.5) * 0.3;
                    float along = dot(r, way) / 0.68 + 0.5, across = abs(dot(r, vec2(-way.y, way.x)));
                    float broad = along > 0.0 && along < 1.0 ? 0.2 * pow(max(sin(3.14159 * pow(along, 0.75)), 0.0), 0.8) : 0.0;
                    float edge = max(foot * size, 0.01);
                    float leaf = has * (1.0 - smoothstep(broad - edge, broad + edge, across));
                    vec3 colour = litterLeafColour(kind, shade) * (0.85 + 0.3 * grain(cell + 3.0)) * (0.8 + 0.2 * smoothstep(0.0, 0.4, across / max(broad, 1e-3)));
                    // About a fifth of a cell is leaf: what the leaves of a far cell come to.
                    result = mix(mix(result, litterLeafColour(kind, 0.4), amount * 0.8 * 0.18), mix(result, colour, leaf), drawn);
                }
                return result;
            }

            // A duff of fallen needles over litter whose colour is under: redder and darker,
            // strewn with needles in short straight strokes a little paler than it, drawn as
            // their mean colour where they are finer than a pixel.
            vec3 litterNeedles(vec2 p, vec3 under, float foot) {
                vec3 duff = vec3(dot(under, vec3(0.3, 0.55, 0.15))) * vec3(1.05, 0.72, 0.5) * (0.8 + 0.3 * patches(p * 1.7 + 3.0));
                float strokes = 0.0;
                for (int layer = 0; layer < 2; layer++) {
                    float size = layer == 0 ? 22.0 : 31.0;
                    vec2 q = p * size + float(layer) * 5.1;
                    vec2 cell = floor(q);
                    float lie = grain(cell + 13.0 * float(layer)) * 3.14159;
                    vec2 way = vec2(cos(lie), sin(lie)), r = fract(q) - 0.5;
                    float along = abs(dot(r, way)), across = abs(dot(r, vec2(-way.y, way.x)));
                    float thick = 0.05 + 0.7 * foot * size;
                    float stroke = (1.0 - smoothstep(thick * 0.5, thick, across)) * (1.0 - smoothstep(0.36, 0.46, along)) * step(0.3, grain(cell + 3.7));
                    strokes = max(strokes, mix(stroke, 0.3, smoothstep(0.3, 0.7, foot * size)));
                }
                return mix(duff * 0.75, duff * vec3(1.7, 1.45, 1.2), strokes);
            }

            """;

    private static float smooth(float t) {
        float c = Math.clamp(t, 0f, 1f);
        return c * c * (3f - 2f * c);
    }
}
