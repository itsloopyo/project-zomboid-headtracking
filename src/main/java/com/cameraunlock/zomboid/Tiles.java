package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;
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
 * Three more ways of laying a floor are read the same way. Tiles laid on the diagonal: their
 * joints run corner to corner, and the dots inside each diamond between them are one colour,
 * or one of two for a chequer. Parquet: blocks in a grid, each of a few slats side by side,
 * the slats of each block running across those of the blocks beside it. And a grid of tiles
 * each shaded lighter or darker towards its edges, whose colours are read further in.
 *
 * A floor that is none of these is not read as one: boards, bricks laid in bond, a pattern
 * across the tiles.
 */
final class Tiles {
    /** The numbers of tiles to a side that are tried. */
    private static final int[] COUNTS = {1, 2, 3, 4, 6, 8};
    /** How much of each tile, in from its edges, is left out of its colour: the joint and the soft dots beside it. */
    private static final float EDGE = 0.14f;
    /** A count is as good as the best when its tiles are this near to as even, or simply this even. */
    private static final float AS_GOOD = 1.6f, EVEN = 0.0003f;
    /** Tiles are a grid when they are this many times as even as the square taken whole, and no more uneven than the first; one tile is a floor when it is as even as the second. */
    private static final float GRID_GAIN = 4f, GRID_UNEVEN = 0.006f, PLAIN_UNEVEN = 0.0008f;
    /**
     * How much of each tile is left out of its colour for a grid of tiles shaded towards their
     * edges, how many tiles a side such a grid has at most, and how many times as even its tiles
     * have to be as the square taken whole: tiles any smaller leave too few dots that far in to
     * tell them from bricks or boards.
     */
    private static final float BEVEL_EDGE = 0.25f, BEVEL_GAIN = 8f;
    private static final int BEVEL_MOST = 4;
    /** Such tiles have joints all round when the dots by one pair of their edges are at least this share as far from the tile's colour as those by the other: see jointedAllRound. */
    private static final float ALL_ROUND = 0.5f;
    /** The numbers of joints to a tile, along a + b and along a - b, tried for tiles laid on the diagonal, and how many places of the first joint between two whole numbers. */
    private static final int[] DIAGONAL_COUNTS = {1, 2, 3, 4};
    private static final int DIAGONAL_PHASES = 16;
    /** A count of joints on the diagonal is as good as the best when its diamonds are this near to as even: see diagonal. */
    private static final float DIAGONAL_AS_GOOD = 2.5f;
    /** Diamonds are tiles when they are this many times as even as the square taken whole, and no more uneven than GRID_UNEVEN. */
    private static final float DIAGONAL_GAIN = 3f;
    /** Two sets of diamonds are a chequer of two colours when their colours are this far apart, summed over red, green and blue. */
    private static final float TWO_COLOURS = 0.1f;
    /**
     * How many places from one joint on the diagonal to the next its light is folded into, to
     * find where along them the joints are; a fold whose light is nowhere this far from its
     * middle, summed over red, green and blue, has none to find.
     */
    private static final int FOLDS = 32;
    private static final float FOLD_FLAT = 0.03f;
    /** How far either side of a joint on the diagonal its own dots lie, as a share of one joint to the next: the joint's colour is theirs. */
    private static final float GROUT = 0.04f;
    /**
     * A chequer's joints are grout of their own when their colour is at least this share of the
     * way from the two colours' mean to either colour: nearer the mean is the two colours
     * meeting, with nothing laid between.
     */
    private static final float GROUT_APART = 0.5f;
    /** The numbers of parquet blocks to a side that are tried, and the numbers of slats to a block. */
    static final int FEWEST_BLOCKS = 2, MOST_BLOCKS = 4, FEWEST_SLATS = 2, MOST_SLATS = 4;
    /** A block's slats run along one way when the means of its rows of dots across them vary this many times as much as those of its rows along them. */
    private static final float LEANS = 4f;
    /** The share of a floor's blocks whose slats have to run the way a parquet's would. None may run the other way. */
    private static final float PARQUET_AGREE = 0.75f;
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
        /**
         * For tiles laid on the diagonal, how many joints there are to a tile along a + b, as
         * many as along a - b, or 0. The joints are where diagonal times a + b, less phase, is
         * a whole number, and where diagonal times a - b, less phaseAcross, is. colours holds
         * the diamonds between whose two counts of joints add to an even number, then the odd
         * ones. The joints are grouted in the joint colour or, for a chequer whose two colours
         * simply meet, not grouted.
         */
        int diagonal;
        float phase, phaseAcross;
        boolean grouted;
        /** Whether a floor laid on the diagonal is a chequer of two colours, and if so its even and its odd diamonds cut to the square: see diamondTriangles. */
        boolean checkered;
        float[] evenTriangles, oddTriangles;
        /**
         * For parquet, how many blocks a side, how many slats to each, and whether the slats of
         * a block run along y where its two counts of blocks along x and y add to an even
         * number. 0 blocks for what is no parquet.
         */
        int blocks, slats;
        boolean evenAlongY;
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
            shape.uneven = uneven(dots, side, 1, EDGE, shape);
            shape.count = 0;
        }
        if (FirstPerson.TRACE) {
            Log.info("floor " + texture.getName() + (shape == null ? " is not a square of floor"
                    : shape.blocks > 0 ? " is parquet, " + shape.blocks + " blocks a side of " + shape.slats + " slats"
                    : shape.diagonal > 0 ? " is tiles on the diagonal, " + shape.diagonal + " joints a tile from " + shape.phase + " and " + shape.phaseAcross + (shape.checkered ? ", chequered" : "")
                    : shape.count == 0 ? " is no grid of plain tiles, uneven " + shape.uneven
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
        // Parquet first: its blocks, each of slats nearly one colour, can pass for a grid of plain tiles.
        Shape shape = parquet(dots, side);
        if (shape == null) {
            shape = grid(dots, side, EDGE, COUNTS[COUNTS.length - 1], GRID_GAIN, true);
        }
        if (shape == null) {
            shape = diagonal(dots, side);
        }
        if (shape == null) {
            // Its uneven is the square's taken whole, as for a floor that is no grid: a carpet goes by that.
            shape = grid(dots, side, BEVEL_EDGE, BEVEL_MOST, BEVEL_GAIN, false);
            if (shape != null && !jointedAllRound(dots, side, shape)) {
                shape = null;
            }
            if (shape != null) {
                shape.uneven = uneven(dots, side, 1, EDGE, null);
            }
        }
        return shape;
    }

    /**
     * The grid of plain tiles in a square of floor laid flat, each tile's colour taken from
     * edge of it in from its edges, at most most tiles a side and gain times as even as the
     * square taken whole, or null for what is no grid. Where plain is set, a square of one
     * colour all over is a grid of one tile.
     */
    private static Shape grid(float[] dots, int side, float edge, int most, float gain, boolean plain) {
        int tried = 0;
        while (tried < COUNTS.length && COUNTS[tried] <= most) {
            tried++;
        }
        float[] uneven = new float[tried];
        float least = Float.MAX_VALUE;
        for (int k = 0; k < tried; k++) {
            uneven[k] = uneven(dots, side, COUNTS[k], edge, null);
            least = Math.min(least, uneven[k]);
        }
        int picked = 0;
        while (uneven[picked] > Math.max(AS_GOOD * least, EVEN)) {
            picked++;
        }
        boolean grid = picked > 0 && uneven[0] >= gain * uneven[picked] && uneven[picked] <= GRID_UNEVEN;
        if (!grid && !(plain && picked == 0 && uneven[0] <= PLAIN_UNEVEN)) {
            return null;
        }
        Shape shape = new Shape();
        shape.count = COUNTS[picked];
        shape.colours = new float[shape.count * shape.count * 3];
        shape.uneven = uneven[picked];
        uneven(dots, side, shape.count, edge, shape);
        shape.rimmed = shape.count == 1 && rimmed(dots, side, shape.colours);
        return shape;
    }

    /**
     * Whether the tiles of a grid read BEVEL_EDGE in from their edges have joints along all
     * their edges: the dots by the edges across a, and those by the edges across b, lie about
     * as far from their tile's colour. Boards have joints only along their length, and a grid
     * read that far in can take a stretch of each board for a tile.
     */
    private static boolean jointedAllRound(float[] dots, int side, Shape shape) {
        int count = shape.count, margin = Math.max(1, Math.round(BEVEL_EDGE * side / count));
        double[] off = new double[2];
        int[] held = new int[2];
        for (int x = 0; x < count; x++) {
            for (int y = 0; y < count; y++) {
                int i0 = x * side / count, i1 = (x + 1) * side / count, j0 = y * side / count, j1 = (y + 1) * side / count, tile = (x * count + y) * 3;
                for (int i = i0; i < i1; i++) {
                    for (int j = j0; j < j1; j++) {
                        int at = (i * side + j) * 3;
                        boolean byA = i < i0 + margin || i >= i1 - margin, byB = j < j0 + margin || j >= j1 - margin;
                        if (dots[at] < 0f || byA == byB) {
                            continue;
                        }
                        int way = byA ? 0 : 1;
                        off[way] += Math.abs(dots[at] - shape.colours[tile]) + Math.abs(dots[at + 1] - shape.colours[tile + 1]) + Math.abs(dots[at + 2] - shape.colours[tile + 2]);
                        held[way]++;
                    }
                }
            }
        }
        double alongA = off[0] / Math.max(held[0], 1), alongB = off[1] / Math.max(held[1], 1);
        return Math.min(alongA, alongB) >= ALL_ROUND * Math.max(alongA, alongB);
    }

    /**
     * Tiles laid on the diagonal in a square of floor laid flat, or null: the fewest joints
     * whose diamonds, with the first joint where it leaves them most nearly one colour each,
     * are nearly as even as the evenest. Twice the joints are always about as even, and on a
     * plain floor a little more, its tiles' faint shading split in two.
     */
    static Shape diagonal(float[] dots, int side) {
        float whole = uneven(dots, side, 1, EDGE, null);
        float[] uneven = new float[DIAGONAL_COUNTS.length];
        int[] phases = new int[DIAGONAL_COUNTS.length];
        float least = Float.MAX_VALUE;
        for (int k = 0; k < DIAGONAL_COUNTS.length; k++) {
            uneven[k] = Float.MAX_VALUE;
            for (int phase = 0; phase < DIAGONAL_PHASES; phase++) {
                float here = diamonds(dots, side, DIAGONAL_COUNTS[k], phase / (float) DIAGONAL_PHASES, phase / (float) DIAGONAL_PHASES, null);
                if (here < uneven[k]) {
                    uneven[k] = here;
                    phases[k] = phase;
                }
            }
            least = Math.min(least, uneven[k]);
        }
        int picked = 0;
        while (uneven[picked] > Math.max(DIAGONAL_AS_GOOD * least, EVEN)) {
            picked++;
        }
        if (uneven[picked] > GRID_UNEVEN || whole < DIAGONAL_GAIN * uneven[picked]) {
            return null;
        }
        Shape shape = new Shape();
        shape.diagonal = DIAGONAL_COUNTS[picked];
        float evenest = phases[picked] / (float) DIAGONAL_PHASES;
        // The evenest phase is good to a place or two either way: the joints are where the light across them turns.
        shape.phase = jointAt(dots, side, shape.diagonal, 1f, evenest);
        shape.phaseAcross = jointAt(dots, side, shape.diagonal, -1f, evenest);
        shape.colours = new float[6];
        shape.uneven = whole;
        diamonds(dots, side, shape.diagonal, shape.phase, shape.phaseAcross, shape);
        float[] joint = {shape.jointRed, shape.jointGreen, shape.jointBlue};
        float apart = 0f, fromMean = 0f;
        for (int part = 0; part < 3; part++) {
            apart += Math.abs(shape.colours[part] - shape.colours[part + 3]);
            fromMean += Math.abs(joint[part] - (shape.colours[part] + shape.colours[part + 3]) / 2f);
        }
        shape.checkered = apart > TWO_COLOURS;
        shape.grouted = !shape.checkered || fromMean >= GROUT_APART * apart / 2f;
        if (shape.checkered) {
            shape.evenTriangles = diamondTriangles(shape.diagonal, shape.phase, shape.phaseAcross, false);
            shape.oddTriangles = diamondTriangles(shape.diagonal, shape.phase, shape.phaseAcross, true);
        }
        return shape;
    }

    /**
     * Where the joints on the diagonal are, count of them to a tile, along a + b for a way of 1
     * and along a - b for -1: the light of the square folded from one joint to the next, and
     * the middle of the run of places in the fold furthest from its middle, lighter or darker,
     * taken where their dots lie. Evenest where the fold is flat. A sprite read at the smaller size has dots at only
     * some places of the fold: the rest are left out.
     */
    private static float jointAt(float[] dots, int side, int count, float way, float evenest) {
        double[] light = new double[FOLDS], place = new double[FOLDS];
        int[] held = new int[FOLDS];
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                int at = (i * side + j) * 3;
                if (dots[at] < 0f) {
                    continue;
                }
                float s = count * ((i + 0.5f) / side + way * (j + 0.5f) / side), in = s - (float) Math.floor(s);
                int fold = Math.min((int) (in * FOLDS), FOLDS - 1);
                light[fold] += dots[at] + dots[at + 1] + dots[at + 2];
                place[fold] += in;
                held[fold]++;
            }
        }
        double[] sorted = new double[FOLDS];
        int filled = 0;
        for (int fold = 0; fold < FOLDS; fold++) {
            if (held[fold] > 0) {
                light[fold] /= held[fold];
                place[fold] /= held[fold];
                sorted[filled++] = light[fold];
            }
        }
        Arrays.sort(sorted, 0, filled);
        double middle = (sorted[(filled - 1) / 2] + sorted[filled / 2]) / 2.0, furthest = 0.0;
        int joint = 0;
        for (int fold = 0; fold < FOLDS; fold++) {
            if (held[fold] > 0 && Math.abs(light[fold] - middle) > furthest) {
                furthest = Math.abs(light[fold] - middle);
                joint = fold;
            }
        }
        if (furthest < FOLD_FLAT) {
            return evenest;
        }
        // A joint a few dots wide fills a run of places about as far from the middle: its own place is the run's middle.
        double sign = Math.signum(light[joint] - middle), weight = 0.0, at = 0.0;
        for (int toward = -1; toward <= 1; toward += 2) {
            for (int step = toward < 0 ? 0 : 1; step < FOLDS / 2; step++) {
                int fold = Math.floorMod(joint + toward * step, FOLDS);
                if (held[fold] == 0) {
                    continue;
                }
                double off = (light[fold] - middle) * sign;
                if (off < 0.5 * furthest) {
                    break;
                }
                // Measured from the furthest place, so a run across the fold's end stays in one piece.
                double from = place[fold] - place[joint];
                at += off * (from - Math.rint(from));
                weight += off;
            }
        }
        double centre = place[joint] + at / weight;
        return (float) (centre - Math.floor(centre));
    }

    /**
     * How far the diamonds between joints laid on the diagonal are from one colour each, count
     * joints to a tile and the first at phase along a + b and at phaseAcross along a - b: the
     * mean over the dots inside them, EDGE of a diamond in from its edges, of how far each lies
     * from its own diamond's colour, squared. Given a shape, fills in the colours of the even
     * diamonds and the odd ones (see Shape.diagonal), the floor's, and the joints': the mean of
     * the dots no more than GROUT from a joint, lighter or darker than the tiles as they are.
     */
    private static float diamonds(float[] dots, int side, int count, float phase, float phaseAcross, Shape into) {
        // Diamonds counted along a + b from -1 to 2 count, along a - b from -count - 1 to count.
        int span = 2 * count + 2;
        double[] sum = new double[span * span * 3];
        double[] squares = new double[span * span];
        int[] held = new int[span * span];
        double[] parity = new double[6], joint = new double[3], whole = new double[3];
        int[] parityHeld = new int[2];
        int joints = 0, every = 0;
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                int at = (i * side + j) * 3;
                if (dots[at] < 0f) {
                    continue;
                }
                float a = (i + 0.5f) / side, b = (j + 0.5f) / side;
                float s = count * (a + b) - phase, t = count * (a - b) - phaseAcross;
                int k = (int) Math.floor(s), m = (int) Math.floor(t);
                float inS = s - k, inT = t - m;
                every++;
                for (int part = 0; part < 3; part++) {
                    whole[part] += dots[at + part];
                }
                if (Math.min(Math.min(inS, 1f - inS), Math.min(inT, 1f - inT)) <= GROUT) {
                    joints++;
                    for (int part = 0; part < 3; part++) {
                        joint[part] += dots[at + part];
                    }
                }
                if (inS <= EDGE || inS >= 1f - EDGE || inT <= EDGE || inT >= 1f - EDGE) {
                    continue;
                }
                int cell = (k + 1) * span + m + count + 1;
                held[cell]++;
                for (int part = 0; part < 3; part++) {
                    sum[cell * 3 + part] += dots[at + part];
                    squares[cell] += dots[at + part] * dots[at + part];
                }
            }
        }
        double apart = 0.0;
        int counted = 0;
        for (int cell = 0; cell < span * span; cell++) {
            if (held[cell] < 4) {
                continue;
            }
            double meanSquare = 0.0;
            for (int part = 0; part < 3; part++) {
                double mean = sum[cell * 3 + part] / held[cell];
                meanSquare += mean * mean;
            }
            apart += squares[cell] - held[cell] * meanSquare;
            counted += held[cell];
            if (into != null) {
                int odd = Math.floorMod(cell / span - 1 + cell % span - count - 1, 2);
                for (int part = 0; part < 3; part++) {
                    parity[odd * 3 + part] += sum[cell * 3 + part];
                }
                parityHeld[odd] += held[cell];
            }
        }
        if (into != null) {
            for (int part = 0; part < 6; part++) {
                into.colours[part] = (float) (parity[part] / parityHeld[part / 3]);
            }
            into.red = (float) (whole[0] / every);
            into.green = (float) (whole[1] / every);
            into.blue = (float) (whole[2] / every);
            into.jointRed = (float) (joint[0] / joints);
            into.jointGreen = (float) (joint[1] / joints);
            into.jointBlue = (float) (joint[2] / joints);
        }
        return counted == 0 ? Float.MAX_VALUE : (float) (apart / counted);
    }

    /**
     * Parquet in a square of floor laid flat, or null: the count of blocks a side for which
     * the slats of nearly every block run one way and those of the blocks beside it the other,
     * and none the wrong way. The slats to a block are the count whose spacing the light and
     * dark across them, all the blocks taken together, comes round at best.
     */
    static Shape parquet(float[] dots, int side) {
        for (int blocks = FEWEST_BLOCKS; blocks <= MOST_BLOCKS; blocks++) {
            int size = side / blocks, margin = Math.max(1, Math.round(EDGE * size));
            int[] lean = new int[blocks * blocks];
            int[] even = new int[2];
            for (int x = 0; x < blocks; x++) {
                for (int y = 0; y < blocks; y++) {
                    // The block's light taken along each of its columns and along each of its rows, and how much each set varies.
                    int inside = size - 2 * margin;
                    double[] byA = new double[inside], byB = new double[inside];
                    int[] heldA = new int[inside], heldB = new int[inside];
                    for (int i = 0; i < inside; i++) {
                        for (int j = 0; j < inside; j++) {
                            float dot = light(dots, side, x * size + margin + i, y * size + margin + j);
                            if (dot >= 0f) {
                                byA[i] += dot;
                                heldA[i]++;
                                byB[j] += dot;
                                heldB[j]++;
                            }
                        }
                    }
                    double acrossA = spread(byA, heldA), acrossB = spread(byB, heldB);
                    // Light that changes along a and not along b is slats running along b, which is y: 1. Along x: -1.
                    int way = acrossA > LEANS * acrossB ? 1 : acrossB > LEANS * acrossA ? -1 : 0;
                    lean[x * blocks + y] = way;
                    if ((x + y) % 2 == 0 && way != 0) {
                        even[way > 0 ? 1 : 0]++;
                    }
                }
            }
            int evenWay = even[1] >= even[0] ? 1 : -1, agree = 0, against = 0;
            for (int x = 0; x < blocks; x++) {
                for (int y = 0; y < blocks; y++) {
                    int wanted = (x + y) % 2 == 0 ? evenWay : -evenWay, way = lean[x * blocks + y];
                    agree += way == wanted ? 1 : 0;
                    against += way == -wanted ? 1 : 0;
                }
            }
            if (against > 0 || agree < PARQUET_AGREE * blocks * blocks) {
                continue;
            }
            // The light and dark across the slats, every block's folded into one block's width.
            float[] across = new float[size];
            for (int x = 0; x < blocks; x++) {
                for (int y = 0; y < blocks; y++) {
                    boolean alongY = (x + y) % 2 == 0 == evenWay > 0;
                    for (int i = 0; i < size; i++) {
                        for (int j = margin; j < size - margin; j++) {
                            across[i] += Math.max(alongY ? light(dots, side, x * size + i, y * size + j) : light(dots, side, x * size + j, y * size + i), 0f);
                        }
                    }
                }
            }
            float mean = 0f;
            for (float dot : across) {
                mean += dot / size;
            }
            int slats = FEWEST_SLATS;
            double best = -1.0;
            for (int count = FEWEST_SLATS; count <= MOST_SLATS; count++) {
                double re = 0.0, im = 0.0;
                for (int i = 0; i < size; i++) {
                    double turn = 2.0 * Math.PI * count * (i + 0.5) / size;
                    re += (across[i] - mean) * Math.cos(turn);
                    im += (across[i] - mean) * Math.sin(turn);
                }
                if (re * re + im * im > best) {
                    best = re * re + im * im;
                    slats = count;
                }
            }
            Shape shape = new Shape();
            shape.blocks = blocks;
            shape.slats = slats;
            shape.evenAlongY = evenWay > 0;
            shape.colours = new float[3];
            shape.uneven = uneven(dots, side, 1, EDGE, shape);
            return shape;
        }
        return null;
    }

    /** How far the means of the sums, each over so many dots, lie from their own mean, squared, over those with any dots. */
    private static double spread(double[] sums, int[] held) {
        double total = 0.0, squares = 0.0;
        int count = 0;
        for (int i = 0; i < sums.length; i++) {
            if (held[i] > 0) {
                double mean = sums[i] / held[i];
                total += mean;
                squares += mean * mean;
                count++;
            }
        }
        return count == 0 ? 0.0 : squares / count - (total / count) * (total / count);
    }

    /** The red, green and blue of a dot of the square summed, or -1 for one off the square or not painted. */
    private static float light(float[] dots, int side, int i, int j) {
        int at = (i * side + j) * 3;
        return dots[at] < 0f ? -1f : dots[at] + dots[at + 1] + dots[at + 2];
    }

    /**
     * The triangles of the diamonds of one colour of a chequer laid on the diagonal, count
     * joints to a tile and the first at phase along a + b and at phaseAcross along a - b (see
     * Shape.diagonal), each cut to the square: a and b of each corner in turn, the corners
     * going round the way the square's do.
     */
    static float[] diamondTriangles(int count, float phase, float phaseAcross, boolean odd) {
        float[] out = new float[64];
        int made = 0;
        float[] piece = new float[16], cut = new float[16];
        for (int k = -1; k <= 2 * count; k++) {
            for (int m = -count - 1; m <= count; m++) {
                if (Math.floorMod(k + m, 2) == 1 != odd) {
                    continue;
                }
                piece[0] = 0f;
                piece[1] = 0f;
                piece[2] = 1f;
                piece[3] = 0f;
                piece[4] = 1f;
                piece[5] = 1f;
                piece[6] = 0f;
                piece[7] = 1f;
                // The diamond is where count * (a + b) - phase is from k to k + 1 and count * (a - b) - phaseAcross from m to m + 1.
                int corners = keep(piece, 4, cut, count, count, -(k + phase));
                corners = keep(cut, corners, piece, -count, -count, k + 1 + phase);
                corners = keep(piece, corners, cut, count, -count, -(m + phaseAcross));
                corners = keep(cut, corners, piece, -count, count, m + 1 + phaseAcross);
                if (corners < 3 || area(piece, corners) < 1e-6f) {
                    continue;
                }
                if (out.length < (made + corners - 2) * 6) {
                    out = Arrays.copyOf(out, out.length * 2);
                }
                for (int c = 1; c < corners - 1; c++) {
                    float twice = (piece[c * 2] - piece[0]) * (piece[c * 2 + 3] - piece[1]) - (piece[c * 2 + 2] - piece[0]) * (piece[c * 2 + 1] - piece[1]);
                    if (twice < 1e-7f) {
                        continue;
                    }
                    int at = made++ * 6;
                    out[at] = piece[0];
                    out[at + 1] = piece[1];
                    out[at + 2] = piece[c * 2];
                    out[at + 3] = piece[c * 2 + 1];
                    out[at + 4] = piece[c * 2 + 2];
                    out[at + 5] = piece[c * 2 + 3];
                }
            }
        }
        return Arrays.copyOf(out, made * 6);
    }

    /** Into to, the part of the polygon of count corners where ka * a + kb * b + c is 0 or more; how many corners that has. */
    private static int keep(float[] from, int count, float[] to, float ka, float kb, float c) {
        int kept = 0;
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            float a0 = from[i * 2], b0 = from[i * 2 + 1], a1 = from[next * 2], b1 = from[next * 2 + 1];
            float d0 = ka * a0 + kb * b0 + c, d1 = ka * a1 + kb * b1 + c;
            if (d0 >= 0f) {
                to[kept * 2] = a0;
                to[kept * 2 + 1] = b0;
                kept++;
            }
            if (d0 > 0f && d1 < 0f || d0 < 0f && d1 > 0f) {
                float along = d0 / (d0 - d1);
                to[kept * 2] = a0 + (a1 - a0) * along;
                to[kept * 2 + 1] = b0 + (b1 - b0) * along;
                kept++;
            }
        }
        return kept;
    }

    private static float area(float[] polygon, int count) {
        float twice = 0f;
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            twice += polygon[i * 2] * polygon[next * 2 + 1] - polygon[next * 2] * polygon[i * 2 + 1];
        }
        return Math.abs(twice) / 2f;
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
     * the tiles of how far the dots inside each, edge of it in from its edges, lie from the
     * tile's own colour, squared. Given
     * a shape, fills in its tiles' colours, its joints' and the whole floor's.
     */
    private static float uneven(float[] dots, int side, int count, float edge, Shape into) {
        float all = 0f;
        int tiles = 0;
        float[] sum = new float[3], joint = new float[3], whole = new float[3];
        int joints = 0, every = 0;
        boolean[] inside = into == null ? null : new boolean[side * side];
        int margin = Math.max(1, Math.round(edge * side / count));
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
