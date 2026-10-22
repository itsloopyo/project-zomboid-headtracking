package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * How a tiled floor is laid, read off its sprite.
 *
 * The game paints a square of tiled floor sixty dots across: two tiles each way, or four, each
 * with a soft dark line round it. Magnified to a floor underfoot the lines are smears a thumb
 * wide. But tiles are squares of one colour each, so the sprite, laid flat, only has to say
 * how many there are to a side and what colour each is: the count is the one that leaves every
 * tile the most nearly one colour inside its own edges. The picture lays the floor as those
 * tiles, each a face of its own, on a bed the colour of the joints between them.
 *
 * A floor that is no grid of plain tiles is not read as one: boards, bricks laid in bond, a
 * pattern across the tiles.
 */
final class Tiles {
    /** The numbers of tiles to a side that are tried. */
    private static final int[] COUNTS = {1, 2, 3, 4, 6, 8};
    /** How much of each tile, in from its edges, is left out of its colour: the joint and the soft dots beside it. */
    private static final float EDGE = 0.14f;
    /** A count is as good as the best when its tiles are this near to as even, or simply this even. */
    private static final float AS_GOOD = 1.6f, EVEN = 0.0003f;
    /** Tiles are a grid when they are this many times as even as the square taken whole, and no more uneven than the first; one tile is a floor when it is as even as the second. */
    private static final float GRID_GAIN = 4f, GRID_UNEVEN = 0.006f, PLAIN_UNEVEN = 0.0006f;
    /** How much of the square has to be painted. */
    private static final float PAINTED = 0.9f;
    /**
     * A floor of one tile to a side is a slab with a joint round it when the dots along one of
     * its edges, this share of the side deep, are this much as light as the tile: a drive's
     * slabs and a kitchen's big tiles. Plain lino and a carpet are as light to the edge.
     */
    private static final float RIM = 0.03f, RIM_DARK = 0.92f;

    /** One sprite's floor. */
    static final class Shape {
        float originU, originV, scale;
        /** How many tiles to a side of the square, or 0 for a floor that is no grid of tiles, of which only its colour and its evenness are known. */
        int count;
        /** How far the square's dots lie from its one colour, squared: a carpet's is small, a pattern's is not. */
        float uneven;
        /** Each tile's red, green and blue, the tile at x and y at (x * count + y) * 3. */
        float[] colours;
        /** The colour of the joints, and of the floor as a whole. */
        float jointRed, jointGreen, jointBlue, red, green, blue;
        /** Whether a floor of one tile to a side is drawn with a joint round it: see RIM. */
        boolean rimmed;
    }

    private record Asked(float originU, float originV, float scale, float left, float top, float width, float height) {}

    private static final Shape NONE = new Shape();
    private static final ConcurrentHashMap<Texture, Asked> asked = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Texture, Shape> known = new ConcurrentHashMap<>();

    private Tiles() {}

    /**
     * The tiles the floor sprite paints, or with a count of 0 the colour of a floor that is no
     * grid of plain tiles. Null until a frame has been drawn since it was first asked for, and
     * for a sprite that is not a whole square of floor.
     */
    static Shape of(Texture texture, SpriteGeometry.Frame frame) {
        Shape shape = known.get(texture);
        if (shape != null) {
            return shape == NONE ? null : shape;
        }
        if (asked.putIfAbsent(texture, new Asked(frame.originU, frame.originV, frame.scale, frame.left, frame.top, frame.width, frame.height)) == null) {
            SpriteColours.readAgain(texture);
        }
        return null;
    }

    /** Whether the sprite has still to be read. */
    static boolean unread(Texture texture) {
        return !known.containsKey(texture);
    }

    /** Reads the sprite if it was asked for: see Windows.read. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        Asked wanted = asked.get(texture);
        if (wanted == null || known.containsKey(texture)) {
            return;
        }
        int side = Math.round(SpriteGeometry.PIXELS_ACROSS_PER_TILE * wanted.scale);
        float[] dots = new float[side * side * 3];
        int painted = 0;
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                float a = (i + 0.5f) / side, b = (j + 0.5f) / side;
                float u = (a - b) * SpriteGeometry.PIXELS_ACROSS_PER_TILE * wanted.scale + wanted.originU;
                float v = (a + b) * SpriteGeometry.PIXELS_DOWN_PER_TILE * wanted.scale + wanted.originV;
                int x = left + (int) Math.floor((u - wanted.left) / wanted.width * (right - left));
                int y = top + (int) Math.floor((v - wanted.top) / wanted.height * (bottom - top));
                int at = (i * side + j) * 3;
                dots[at] = -1f;
                if (u < wanted.left || v < wanted.top || x >= right || y >= bottom) {
                    continue;
                }
                int place = (y * width + x) * 4;
                if ((sheet.get(place + 3) & 0xff) < Windows.FRAME_COVER) {
                    continue;
                }
                dots[at] = (sheet.get(place) & 0xff) / 255f;
                dots[at + 1] = (sheet.get(place + 1) & 0xff) / 255f;
                dots[at + 2] = (sheet.get(place + 2) & 0xff) / 255f;
                painted++;
            }
        }
        Shape shape = painted >= PAINTED * side * side ? laid(dots, side) : null;
        if (shape == null && painted >= PAINTED * side * side) {
            shape = new Shape();
            shape.colours = new float[3];
            shape.uneven = uneven(dots, side, 1, shape);
            shape.count = 0;
        }
        if (FirstPerson.TRACE) {
            Log.info("floor " + texture.getName() + (shape == null ? " is not a square of floor" : shape.count == 0 ? " is no grid of plain tiles, uneven " + shape.uneven
                    : " is " + shape.count + " tiles a side"));
        }
        if (shape == null) {
            known.put(texture, NONE);
            return;
        }
        shape.originU = wanted.originU;
        shape.originV = wanted.originV;
        shape.scale = wanted.scale;
        known.put(texture, shape);
    }

    /**
     * The tiles in a square of floor laid flat: dots holds side dots a row and a red, green
     * and blue for each, the red below 0 for a dot with nothing painted. Null for what is no
     * grid of plain tiles.
     */
    static Shape laid(float[] dots, int side) {
        float[] uneven = new float[COUNTS.length];
        float least = Float.MAX_VALUE;
        for (int k = 0; k < COUNTS.length; k++) {
            uneven[k] = uneven(dots, side, COUNTS[k], null);
            least = Math.min(least, uneven[k]);
        }
        int picked = 0;
        while (uneven[picked] > Math.max(AS_GOOD * least, EVEN)) {
            picked++;
        }
        boolean grid = picked > 0 && uneven[0] >= GRID_GAIN * uneven[picked] && uneven[picked] <= GRID_UNEVEN;
        if (!grid && !(picked == 0 && uneven[0] <= PLAIN_UNEVEN)) {
            return null;
        }
        Shape shape = new Shape();
        shape.count = COUNTS[picked];
        shape.colours = new float[shape.count * shape.count * 3];
        shape.uneven = uneven[picked];
        uneven(dots, side, shape.count, shape);
        shape.rimmed = shape.count == 1 && rimmed(dots, side, shape.colours);
        return shape;
    }

    /** Whether the dots along some edge of a square of one tile are darker than the tile: see RIM. */
    static boolean rimmed(float[] dots, int side, float[] tile) {
        float light = tile[0] + tile[1] + tile[2];
        int deep = Math.max(1, Math.round(RIM * side));
        for (int edge = 0; edge < 4; edge++) {
            float sum = 0f;
            int held = 0;
            for (int along = 0; along < side; along++) {
                for (int in = 0; in < deep; in++) {
                    int across = edge % 2 == 0 ? in : side - 1 - in;
                    int at = (edge < 2 ? across * side + along : along * side + across) * 3;
                    if (dots[at] >= 0f) {
                        sum += dots[at] + dots[at + 1] + dots[at + 2];
                        held++;
                    }
                }
            }
            if (held > 0 && sum / held < RIM_DARK * light) {
                return true;
            }
        }
        return false;
    }

    /**
     * How far the tiles are from one colour each, with count of them to a side: the mean over
     * the tiles of how far the dots inside each lie from the tile's own colour, squared. Given
     * a shape, fills in its tiles' colours, its joints' and the whole floor's.
     */
    private static float uneven(float[] dots, int side, int count, Shape into) {
        float all = 0f;
        int tiles = 0;
        float[] sum = new float[3], joint = new float[3], whole = new float[3];
        int joints = 0, every = 0;
        boolean[] inside = into == null ? null : new boolean[side * side];
        int margin = Math.max(1, Math.round(EDGE * side / count));
        for (int x = 0; x < count; x++) {
            for (int y = 0; y < count; y++) {
                int i0 = x * side / count + margin, i1 = (x + 1) * side / count - margin, j0 = y * side / count + margin, j1 = (y + 1) * side / count - margin;
                sum[0] = sum[1] = sum[2] = 0f;
                int held = 0;
                for (int i = i0; i < i1; i++) {
                    for (int j = j0; j < j1; j++) {
                        int at = (i * side + j) * 3;
                        if (dots[at] < 0f) {
                            continue;
                        }
                        sum[0] += dots[at];
                        sum[1] += dots[at + 1];
                        sum[2] += dots[at + 2];
                        held++;
                        if (inside != null) {
                            inside[i * side + j] = true;
                        }
                    }
                }
                if (held == 0) {
                    continue;
                }
                float red = sum[0] / held, green = sum[1] / held, blue = sum[2] / held, apart = 0f;
                for (int i = i0; i < i1; i++) {
                    for (int j = j0; j < j1; j++) {
                        int at = (i * side + j) * 3;
                        if (dots[at] >= 0f) {
                            apart += (dots[at] - red) * (dots[at] - red) + (dots[at + 1] - green) * (dots[at + 1] - green) + (dots[at + 2] - blue) * (dots[at + 2] - blue);
                        }
                    }
                }
                all += apart / held;
                tiles++;
                if (into != null) {
                    int at = (x * count + y) * 3;
                    into.colours[at] = red;
                    into.colours[at + 1] = green;
                    into.colours[at + 2] = blue;
                }
            }
        }
        if (into != null) {
            for (int dot = 0; dot < side * side; dot++) {
                if (dots[dot * 3] < 0f) {
                    continue;
                }
                for (int part = 0; part < 3; part++) {
                    whole[part] += dots[dot * 3 + part];
                    joint[part] += inside[dot] ? 0f : dots[dot * 3 + part];
                }
                every++;
                joints += inside[dot] ? 0 : 1;
            }
            into.red = whole[0] / every;
            into.green = whole[1] / every;
            into.blue = whole[2] / every;
            into.jointRed = joint[0] / Math.max(joints, 1);
            into.jointGreen = joint[1] / Math.max(joints, 1);
            into.jointBlue = joint[2] / Math.max(joints, 1);
        }
        return tiles == 0 ? Float.MAX_VALUE : all / tiles;
    }
}
