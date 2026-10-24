package com.cameraunlock.zomboid;

import java.util.IdentityHashMap;
import zombie.core.properties.PropertyContainer;
import zombie.iso.sprite.IsoSprite;

/**
 * A string of lights as the map has one: a cord hung in swags along a fence, a porch or a
 * shop front, with a bulb every hand or two. The game paints one swag on two squares, the half
 * that comes down from a support on the first and the half that goes up to the next support on
 * the second, each seen from the game's own view. Stood up as a picture, each half is a card
 * on the middle of its own square, turned to the eye on its own.
 *
 * Here a swag is one cord from the top of one support to the top of the next, two squares on,
 * hanging between them as an overhead wire does between its poles (Spans), with a bulb under
 * it wherever the sprites show one. A square's sprite stands for its own half of that cord, so
 * a half whose other half the map leaves out still hangs from its support down to where the
 * two would have met.
 *
 * The game calls all of them "Lights", of the kinds "Large", "Small" and "Rainbow". One that
 * faces south hangs along the north edge of its square and runs east, one that faces east
 * along the west edge and runs south. All the numbers are read off the sprites: a point (a, b)
 * of a square and h levels up is frame pixel ((a - b) * 64 + 64, (a + b) * 32 - h * 192 + 192).
 */
final class Lights {
    static final int NONE = -1, LARGE = 0, SMALL = 1, RAINBOW = 2;

    /** How far it is from one support to the next, in tiles. */
    static final float SPAN = 2f;
    /** How high over the floor the cord is made fast, and how far below that it hangs in the middle of a swag, in levels. */
    static final float HELD = 0.905f, SAG = 0.095f;
    /**
     * How far in from the edge of its square the cord hangs, in tiles: clear of a wall or a
     * fence on that edge by the width of a bulb and its swing, which for a large bulb in the
     * middle of a swag is 0.075 in the hardest wind, and 0.04 off the face of a fence's corner
     * post, which reaches 0.13 in.
     */
    static final float OFF = 0.17f;
    /** How many straight lengths the half of a swag on one square is hung as. */
    static final int LENGTHS = 5;

    /** Where along a swag its bulbs hang, in tiles from its first support, by kind. */
    private static final float[] GLOBES = {0.05f, 0.26f, 0.38f, 0.5f, 0.77f, 0.9f, 1.13f, 1.32f, 1.55f, 1.85f};
    private static final float[][] PLACES = {GLOBES,
            {0.07f, 0.1f, 0.16f, 0.31f, 0.35f, 0.48f, 0.55f, 0.59f, 0.72f, 0.77f, 0.95f, 0.98f, 1.13f, 1.42f, 1.46f, 1.62f, 1.79f}, GLOBES};
    /** How thick a bulb's glass is at its widest, as a radius in tiles, by kind. */
    private static final float[] SIZES = {0.05f, 0.014f, 0.05f};
    /** How many sides a bulb is turned with, by kind, near the eye and far from it. */
    private static final int[] SIDES = {8, 5, 8}, FAR_SIDES = {5, 3, 5};

    /** The colour of clear glass with the lamp out and with it burning: red, green and blue, 0 to 255, by kind. */
    private static final int[][] CLEAR = {{178, 178, 178, 244, 233, 194}, {134, 134, 134, 235, 221, 195}};
    /** The colours of a rainbow string's bulbs in the order they hang, out and burning. */
    private static final int[][] DYED = {
            {83, 121, 165, 108, 187, 242}, {145, 167, 177, 206, 231, 240}, {103, 154, 111, 147, 229, 164}, {152, 159, 94, 228, 235, 129},
            {169, 164, 106, 239, 235, 147}, {147, 81, 83, 232, 109, 117}, {182, 162, 173, 241, 223, 235}, {163, 111, 82, 240, 169, 103},
            {133, 91, 105, 210, 131, 158}, {116, 108, 146, 174, 159, 223}};
    /** The colour of a bulb's holder. */
    static final float HOLDER_RED = 0.05f, HOLDER_GREEN = 0.05f, HOLDER_BLUE = 0.055f;

    /**
     * A bulb hanging under its cord as a thing turned on a lathe: how far below the cord and
     * how thick, pair by pair from the top down, in bulb radii. The holder, and then the glass
     * that comes out of it.
     */
    static final float[] HOLDER = {0f, 0f, 0f, 0.42f, 0.95f, 0.46f};
    static final float[] GLASS = {0.94f, 0.42f, 1.21f, 0.77f, 1.59f, 0.97f, 2.02f, 0.98f, 2.42f, 0.82f, 2.72f, 0.5f, 2.85f, 0f};
    /**
     * How near the eye a string is, in tiles, for its bulbs to be turned whole, and for it to
     * have bulbs at all: further off than the first a bulb is the same holder and glass in four
     * rings and fewer sides, and further off than the second the cord of a string that is out
     * hangs bare. A square of
     * string is some 600 triangles whole, 200 far and 20 bare, in the view and in every shadow
     * map, and one place on the shipped map has 264 squares within sight.
     */
    static final float FINE_WITHIN = 16f, BULBS_WITHIN = 40f;
    static final float[] FAR_HOLDER = {0f, 0.44f, 0.95f, 0.44f};
    static final float[] FAR_GLASS = {0.94f, 0.42f, 1.59f, 0.97f, 2.42f, 0.82f, 2.85f, 0f};

    /** How a string's bulbs are made: turned whole, turned plainer, or left off the cord. */
    static final int WHOLE = 0, FAR = 1, BARE = 2;

    /**
     * How a string's bulbs are made by whether it is within FINE_WITHIN of the eye and within
     * BULBS_WITHIN. A bulb that burns is a point of light as far as the string is seen, and stays.
     */
    static int made(boolean fine, boolean near, boolean burning) {
        return fine ? WHOLE : near || burning ? FAR : BARE;
    }

    /**
     * The least a vertex of what hangs on a cord is given as hanging below the line between the
     * cord's supports, in tiles. The picture tells a vertex of the stuff of a lamp that hangs
     * from one of a lamp that stands by its hanging at all, and only what hangs swings: see hung
     * in PipelineShaders, whose line is drawn at half of this.
     */
    static final float HUNG_LEAST = 0.004f;

    /** The numbers cordEnd and bulb write: a place on the map, how far along its swag that is from the first support, 0 to 1, and how far the cord hangs there below the line between the supports. How many they are. */
    static final int X = 0, Y = 1, ALONG = 2, HANGS = 3, NUMBERS = 4;

    private static final String CALLED = "Lights";
    private static final IdentityHashMap<IsoSprite, Integer> slices = new IdentityHashMap<>();

    private Lights() {}

    /** For a map loaded anew: its sprites are not the ones before's. */
    static void forget() {
        slices.clear();
    }

    /**
     * Which half of which swag a sprite is, by the name the game gives the piece, its kind, the
     * way it faces and where it is in its piece, or NONE. The answer is the kind, whether the
     * string runs south and whether the half is the second, as one number: see kind, runsSouth
     * and second.
     */
    static int sliceOf(String called, String kind, String facing, String place) {
        if (!CALLED.equals(called) || kind == null || facing == null || place == null) {
            return NONE;
        }
        int which = switch (kind) {
            case "Large" -> LARGE;
            case "Small" -> SMALL;
            case "Rainbow" -> RAINBOW;
            default -> NONE;
        };
        boolean south = facing.equals("E");
        if (which == NONE || !south && !facing.equals("S")) {
            return NONE;
        }
        boolean first = place.equals("0,0");
        if (!first && !place.equals(south ? "0,1" : "1,0")) {
            return NONE;
        }
        return which * 4 + (south ? 2 : 0) + (first ? 0 : 1);
    }

    static int slice(IsoSprite sprite) {
        Integer known = slices.get(sprite);
        if (known == null) {
            PropertyContainer properties = sprite.getProperties();
            known = sliceOf(properties.get("CustomName"), properties.get("GroupName"), properties.get("Facing"), properties.get("SpriteGridPos"));
            slices.put(sprite, known);
        }
        return known;
    }

    static int kind(int slice) {
        return slice / 4;
    }

    static boolean runsSouth(int slice) {
        return (slice & 2) != 0;
    }

    static boolean second(int slice) {
        return (slice & 1) != 0;
    }

    /**
     * Writes the swag a half on a square is of as a piece of Spans: the piece's own ends are
     * the half's, from the support it hangs from to the middle of the swag or from the middle
     * to the other support, and its span's ends are the two supports.
     */
    static void span(int slice, int x, int y, float[] piece) {
        boolean south = runsSouth(slice);
        float from = (south ? y : x) - (second(slice) ? 1f : 0f), across = (south ? x : y) + OFF;
        float start = from + (second(slice) ? SPAN / 2f : 0f);
        end(piece, 0, south, start, across);
        end(piece, 2, south, start + SPAN / 2f, across);
        for (int support = 0; support < 2; support++) {
            int at = support == 0 ? Spans.FROM : Spans.TO;
            end(piece, at, south, from + support * SPAN, across);
            piece[at + Spans.END_LAND] = piece[at];
            piece[at + Spans.END_LAND + 1] = piece[at + 1];
            piece[at + Spans.END_KIND] = Spans.FAST;
        }
    }

    private static void end(float[] piece, int at, boolean south, float along, float across) {
        piece[at] = south ? across : along;
        piece[at + 1] = south ? along : across;
    }

    /** The squares, from the one a corner post stands on the north-west corner of, that a half made fast at that corner may be on: see fastAt. */
    static final int[] HELD_FROM_X = {0, -1, 0}, HELD_FROM_Y = {0, 0, -1};

    /**
     * Whether a half on the square (alongX, alongY) from another is made fast at that other
     * square's north-west corner, where a corner post stands: a first half on the square
     * itself, and a second half on the square before it the way the string runs.
     */
    static boolean fastAt(int slice, int alongX, int alongY) {
        if (!second(slice)) {
            return alongX == 0 && alongY == 0;
        }
        return runsSouth(slice) ? alongX == 0 && alongY == -1 : alongX == -1 && alongY == 0;
    }

    /**
     * Where an end of one of the straight lengths the half of a swag is hung as lies on the
     * cord: end 0 is where the half starts and end LENGTHS where it stops, and every end is on
     * the curve of the whole swag, so the two halves are one cord. sag is how far the swag
     * hangs in the middle.
     */
    static void cordEnd(float[] piece, int end, float sag, float[] at) {
        float share = end / (float) LENGTHS;
        at[X] = piece[0] + (piece[2] - piece[0]) * share;
        at[Y] = piece[1] + (piece[3] - piece[1]) * share;
        at[ALONG] = Spans.along(piece, 0, at[X], at[Y]);
        at[HANGS] = Spans.hangs(at[ALONG], sag);
    }

    /** Where on the cord of a swag a bulb of its kind hangs from. */
    static void bulb(float[] piece, int kind, int bulb, float sag, float[] at) {
        float along = place(kind, bulb) / SPAN;
        at[X] = piece[Spans.FROM] + (piece[Spans.TO] - piece[Spans.FROM]) * along;
        at[Y] = piece[Spans.FROM + 1] + (piece[Spans.TO + 1] - piece[Spans.FROM + 1]) * along;
        at[ALONG] = along;
        at[HANGS] = Spans.hangs(along, sag);
    }

    /**
     * Adds a round thing that hangs under a cord and swings with it: turned about the plumb
     * line under (x, y, up) to a profile of pairs, how far below the cord and how thick, in
     * units of size. hangs is how far the cord hangs there below the line between its
     * supports, and each vertex is that much and its own depth under the cord below the
     * line, so the whole of it is carried round the line as the cord is: see swung in
     * PipelineShaders. across is what WorldScene.cable gives a cord's vertex where a texel's
     * place would be, for the first corner. Where a ring closes on the plumb line it is one
     * triangle a side: the other of the two would have two corners in one place.
     */
    static void round(SpriteGeometry.Mesh mesh, float x, float y, float up, float hangs, float acrossX, float acrossY, float[] profile, float size, int sides,
            float red, float green, float blue) {
        for (int ring = 0; ring + 3 < profile.length; ring += 2) {
            float highDeep = profile[ring] * size, highWide = profile[ring + 1] * size, lowDeep = profile[ring + 2] * size, lowWide = profile[ring + 3] * size;
            float drop = lowDeep - highDeep, swell = lowWide - highWide, slant = (float) Math.sqrt(drop * drop + swell * swell);
            float[] cosAround = SpriteGeometry.ROUND_COS[sides], sinAround = SpriteGeometry.ROUND_SIN[sides];
            for (int side = 0; side < sides; side++) {
                // Two triangles, as SpriteGeometry.addRound makes them: low from, low to, high to, then low from, high to, high from.
                for (int corner = lowWide == 0f ? 3 : 0; corner < (highWide == 0f ? 3 : 6); corner++) {
                    boolean far = corner == 1 || corner == 2 || corner == 4, high = corner == 2 || corner >= 4;
                    float cos = cosAround[far ? side + 1 : side], sin = sinAround[far ? side + 1 : side];
                    float deep = high ? highDeep : lowDeep, wide = high ? highWide : lowWide;
                    mesh.face(cos * drop / slant, sin * drop / slant, swell / slant);
                    mesh.sway = hangs + deep + HUNG_LEAST;
                    mesh.add(x + cos * wide, y + sin * wide, up - deep, acrossX, acrossY, red, green, blue);
                }
            }
        }
        mesh.sway = 0f;
    }

    /** How many bulbs a swag of a kind has, and where along it one hangs, in tiles from the first support. */
    static int bulbs(int kind) {
        return PLACES[kind].length;
    }

    static float place(int kind, int bulb) {
        return PLACES[kind][bulb];
    }

    /** Whether a bulb of a swag hangs on the half a slice is. */
    static boolean on(int slice, int bulb) {
        return place(kind(slice), bulb) >= SPAN / 2f == second(slice);
    }

    static float size(int kind) {
        return SIZES[kind];
    }

    static int sides(int kind, boolean far) {
        return far ? FAR_SIDES[kind] : SIDES[kind];
    }

    static float[] holder(boolean far) {
        return far ? FAR_HOLDER : HOLDER;
    }

    static float[] glass(boolean far) {
        return far ? FAR_GLASS : GLASS;
    }

    /** One part of the colour of a bulb's glass, 0 to 1: part 0 red, 1 green, 2 blue. */
    static float colour(int kind, int bulb, boolean burning, int part) {
        int[] colours = kind == RAINBOW ? DYED[bulb] : CLEAR[kind];
        return colours[(burning ? 3 : 0) + part] / 255f;
    }
}
