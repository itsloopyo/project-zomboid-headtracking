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
 * Last, pavers: a grid of cells, of which some joints are laid and some are not, so that the
 * cells make bricks one by two, two by two and so on, laid in rows or herringbone, each paver
 * in a colour of its own. And hexagonal pavers, whose middles lie on a lattice that the light
 * of the square repeats along three ways at once.
 *
 * A floor that is none of these is not read as one: slabs laid out of line, a pattern across
 * the tiles.
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
    /**
     * The numbers of pavers' cells to a side that are tried, and the fewest a floor of pavers
     * has: slabs half a square across have too few joints to tell a pattern of them from the
     * shading on the slabs, and the sprite draws them well enough.
     */
    private static final int[] PAVER_CELLS = {2, 3, 4, 6, 8};
    private static final int FEWEST_CELLS = 4;
    /** How many places between two dots the first joint of a grid of pavers' cells is looked for at. */
    private static final int PAVER_PHASES = 4;
    /**
     * A count of cells is taken, the most of them, where the light of the lines it puts its
     * joints on stands out from the light either side at least this share of the most any count's
     * does: a count that divides the true one stands out as much, its joints being some of them.
     */
    private static final float PAVER_AS_GOOD = 0.75f;
    /**
     * Along each side of each cell, how far the joint's dots stand out from the dots beside it,
     * against the median of the joints that run that way: a joint is laid there at JOINED of the
     * median or more, and the two cells are one paver at APART or less. Between the two is no
     * pattern of pavers, and nor is a floor whose joints stand out at the median by less than
     * PAVER_JOINTS in light, a plain floor's dots differing that much among themselves.
     */
    private static final float JOINED = 0.5f, APART = 0.35f, PAVER_JOINTS = 0.03f;
    /**
     * The joints of pavers, at the median of all of them, stand out at least this many times as
     * far as the dots inside a paver lie from its light: a lawn, a carpet's pile or boards' grain
     * have lines in them as strong as the speckle round them, and are no pavers.
     */
    private static final float PAVER_CLEAR = 3.5f;
    /** The fewest dots a side a sprite has to be read at to be read for pavers: a cell of the smaller sprites is a few dots across. */
    private static final int PAVER_SIDE = 48;
    /**
     * A floor that is no grid is chips of stone in a ground (terrazzo) where its light strays from
     * its mean by SPECKLED_LEAST or more, and no more than SPECKLED_RUNS of that is in the means of
     * its rows or of its columns: boards and planks laid end to end have half or more there.
     */
    private static final float SPECKLED_LEAST = 0.03f, SPECKLED_RUNS = 0.15f;
    /**
     * Hexagons: the most times the light is looked for to repeat across the square each way,
     * and how many of the strongest repeats are tried as the two of a lattice. A honeycomb's
     * light repeats along three ways at once, k1, k2 and k1 - k2 or k1 + k2: the three have to
     * stand at least HEX_PEAK times as strong as the light's median repeat, the strongest no
     * more than HEX_EVEN times the weakest, and the longest no more than HEX_ROUND times the
     * shortest, which a grid of squares, its third way along the diagonal, is not.
     */
    private static final int HEX_MOST = 16, HEX_TRIED = 8;
    private static final float HEX_PEAK = 3f, HEX_EVEN = 2.5f, HEX_ROUND = 1.25f;
    /** The fewest hexagons to a square: fewer are slabs, not pavers. */
    private static final int FEWEST_HEXES = 9;
    /**
     * How many places along each step of the lattice a hexagon's middle is looked for at, and
     * how many of them either way of the first guess it is then looked for at again by its joints.
     */
    private static final int HEX_PHASES = 16, HEX_NEAR = 2;
    /** A dot is inside its hexagon this far from its edge, in dots, and on the joint this near to it. */
    private static final float HEX_INSIDE = 1.5f, HEX_JOINT = 0.75f;

    /** One sprite's floor. */
    static final class Shape {
        float originU, originV, scale;
        /** How many tiles to a side of the square, or 0 for a floor that is no grid of tiles, of which only its colour and its evenness are known. */
        int count;
        /** How far the square's dots lie from its one colour, squared: a carpet's is small, a pattern's is not. */
        float uneven;
        /** For a floor that is no grid, whether it is chips of stone in a ground, scattered the same every way: see speckled. */
        boolean speckled;
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
        /**
         * For pavers, how many cells there are along a and along b, and where along each the
         * first joint is as a share of the square, each next joint a cell further on: see
         * pavers. 0 cells for what is no floor of pavers.
         */
        int cellsA, cellsB;
        float firstA, firstB;
        /**
         * Each paver as seven numbers: its first cell along a and along b, how many cells it
         * runs along a and along b, and its red, green and blue. A paver may run over the edge
         * of the square into the next, where the floor goes on the same.
         */
        float[] pavers;
        /**
         * Each hexagonal paver as five numbers: its middle along a and along b as a share of the
         * square, and its red, green and blue; null for what is no floor of hexagons. A paver is
         * every point nearer its middle than any other paver's, and may run over the edge of the
         * square into the next. hexCorners holds the six corners of every one of them, along a
         * and along b from its middle, in turn round it the way from a towards b.
         */
        float[] hexes, hexCorners;
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
            shape.speckled = speckled(dots, side);
        }
        if (FirstPerson.TRACE) {
            Log.info("floor " + texture.getName() + (shape == null ? " is not a square of floor"
                    : shape.blocks > 0 ? " is parquet, " + shape.blocks + " blocks a side of " + shape.slats + " slats"
                    : shape.pavers != null ? " is " + shape.pavers.length / 7 + " pavers on " + shape.cellsA + " by " + shape.cellsB + " cells"
                    : shape.hexes != null ? " is " + shape.hexes.length / 5 + " hexagonal pavers"
                    : shape.diagonal > 0 ? " is tiles on the diagonal, " + shape.diagonal + " joints a tile from " + shape.phase + " and " + shape.phaseAcross + (shape.checkered ? ", chequered" : "")
                    : shape.count == 0 ? " is no grid of plain tiles, uneven " + shape.uneven + (shape.speckled ? ", speckled" : "")
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
        // A honeycomb before pavers: its rows of hexagons, half a hexagon out from row to row, can pass for bricks in bond.
        if (shape == null && side >= PAVER_SIDE) {
            shape = hexes(dots, side);
        }
        if (shape == null && side >= PAVER_SIDE) {
            shape = pavers(dots, side);
        }
        return shape;
    }

    /**
     * Pavers in a square of floor laid flat, or null. The grid of cells along each way is the
     * one whose lines of dots stand out most from the dots either side, and then each side of
     * each cell is a joint or is not, by how far its dots stand out: cells with no joint between
     * are one paver, which has to be a rectangle of them. Every paver's colour is read inside
     * its cells away from their lines, and the joints' on the lines where a joint is laid.
     */
    static Shape pavers(float[] dots, int side) {
        float[] light = new float[side * side];
        for (int dot = 0; dot < side * side; dot++) {
            light[dot] = dots[dot * 3] < 0f ? Float.NaN : 0.299f * dots[dot * 3] + 0.587f * dots[dot * 3 + 1] + 0.114f * dots[dot * 3 + 2];
        }
        float[] alongA = new float[2], alongB = new float[2];
        cellLines(light, side, true, alongA);
        cellLines(light, side, false, alongB);
        int cellsA = (int) alongA[0], cellsB = (int) alongB[0];
        // Cells longer one way than the other are boards, laid end to end.
        if (cellsA != cellsB || cellsA < FEWEST_CELLS) {
            return null;
        }
        float cellA = side / (float) cellsA, cellB = side / (float) cellsB;
        // The joints across a, before cell i along a in its row j along b; and those across b, before row j in its column i.
        float[] acrossA = new float[cellsA * cellsB], acrossB = new float[cellsA * cellsB];
        for (int i = 0; i < cellsA; i++) {
            for (int j = 0; j < cellsB; j++) {
                float fromA = alongA[1] + i * cellA, fromB = alongB[1] + j * cellB;
                acrossA[i * cellsB + j] = jointStands(light, side, true, fromA, fromB + 1.5f, fromB + cellB - 1.5f);
                acrossB[i * cellsB + j] = jointStands(light, side, false, fromB, fromA + 1.5f, fromA + cellA - 1.5f);
            }
        }
        boolean[] laidA = laid(acrossA), laidB = laid(acrossB);
        if (laidA == null || laidB == null) {
            return null;
        }
        // Cells with no joint between are one paver: each cell is joined to its paver's lowest cell.
        int[] paverOf = new int[cellsA * cellsB];
        for (int cell = 0; cell < paverOf.length; cell++) {
            paverOf[cell] = cell;
        }
        for (int i = 0; i < cellsA; i++) {
            for (int j = 0; j < cellsB; j++) {
                if (!laidA[i * cellsB + j]) {
                    join(paverOf, i * cellsB + j, Math.floorMod(i - 1, cellsA) * cellsB + j);
                }
                if (!laidB[i * cellsB + j]) {
                    join(paverOf, i * cellsB + j, i * cellsB + Math.floorMod(j - 1, cellsB));
                }
            }
        }
        float[] pavers = new float[paverOf.length * 7];
        int count = 0;
        int[] slot = new int[paverOf.length];
        Arrays.fill(slot, -1);
        for (int cell = 0; cell < paverOf.length; cell++) {
            int root = root(paverOf, cell);
            if (slot[root] >= 0) {
                continue;
            }
            int[] box = rectangle(paverOf, root, cellsA, cellsB);
            if (box == null) {
                return null;
            }
            slot[root] = count;
            for (int k = 0; k < 4; k++) {
                pavers[count * 7 + k] = box[k];
            }
            count++;
        }
        Shape shape = new Shape();
        shape.cellsA = cellsA;
        shape.cellsB = cellsB;
        shape.firstA = alongA[1] / side;
        shape.firstB = alongB[1] / side;
        shape.pavers = Arrays.copyOf(pavers, count * 7);
        shape.uneven = uneven(dots, side, 1, EDGE, null);
        float rough = paverColours(dots, light, side, shape, paverOf, slot, laidA, laidB);
        float[] both = new float[acrossA.length + acrossB.length];
        int held = 0;
        for (float stands : acrossA) {
            if (!Float.isNaN(stands)) {
                both[held++] = stands;
            }
        }
        for (float stands : acrossB) {
            if (!Float.isNaN(stands)) {
                both[held++] = stands;
            }
        }
        return median(both, held) >= PAVER_CLEAR * rough ? shape : null;
    }

    /**
     * Into found, how many cells there are along a for alongA, else along b, and where the
     * first of their joints is, in dots: of the counts whose joints stand out at least
     * PAVER_AS_GOOD of the most, the most cells.
     */
    private static void cellLines(float[] light, int side, boolean alongA, float[] found) {
        float[] profile = new float[side];
        for (int k = 0; k < side; k++) {
            double sum = 0;
            int held = 0;
            for (int other = 0; other < side; other++) {
                float dot = light[alongA ? k * side + other : other * side + k];
                if (!Float.isNaN(dot)) {
                    sum += dot;
                    held++;
                }
            }
            profile[k] = held == 0 ? Float.NaN : (float) (sum / held);
        }
        float[] stands = new float[PAVER_CELLS.length], first = new float[PAVER_CELLS.length];
        float most = 0f;
        for (int c = 0; c < PAVER_CELLS.length; c++) {
            float cell = side / (float) PAVER_CELLS[c];
            int half = Math.round(cell / 2f);
            for (int place = 0; place < cell * PAVER_PHASES; place++) {
                float phase = place / (float) PAVER_PHASES, sum = 0f;
                int held = 0;
                for (int m = 0; m < PAVER_CELLS[c]; m++) {
                    float line = phase + m * cell;
                    float low = standsOut(profile, (int) Math.floor(line - 0.5f), half), high = standsOut(profile, (int) Math.floor(line + 0.5f), half);
                    float here = Float.isNaN(low) ? high : Float.isNaN(high) ? low : Math.max(low, high);
                    if (!Float.isNaN(here)) {
                        sum += here;
                        held++;
                    }
                }
                float mean = held == 0 ? 0f : sum / held;
                if (mean > stands[c]) {
                    stands[c] = mean;
                    first[c] = phase;
                }
            }
            most = Math.max(most, stands[c]);
        }
        int picked = 0;
        for (int c = 0; c < PAVER_CELLS.length; c++) {
            if (stands[c] >= PAVER_AS_GOOD * most) {
                picked = c;
            }
        }
        found[0] = PAVER_CELLS[picked];
        found[1] = first[picked];
    }

    /**
     * How far the light of line k of a profile stands from the median of the lines within half
     * of it either side, leaving out the two beside it, which a joint's soft edge darkens too.
     * NaN by the square's edges, whose outermost dots the game shades.
     */
    private static float standsOut(float[] profile, int k, int half) {
        int side = profile.length;
        if (k < 2 || k > side - 3 || Float.isNaN(profile[k])) {
            return Float.NaN;
        }
        float[] round = new float[2 * half + 1];
        int held = 0;
        for (int o = -half; o <= half; o++) {
            int at = k + o;
            if (Math.abs(o) > 1 && at >= 2 && at <= side - 3 && !Float.isNaN(profile[at])) {
                round[held++] = profile[at];
            }
        }
        return held == 0 ? Float.NaN : Math.abs(profile[k] - median(round, held));
    }

    /**
     * How far the dots of a joint across a for acrossA, else across b, on the line at line dots
     * along the way it is across, stand from the dots either side, from from to to along it: the
     * mean over the dots along it of the more of how much darker the darker of the two dots
     * nearest the line is, and how much lighter the lighter, than the median of the dots two and
     * three away. NaN where the joint lies by the square's edges.
     */
    private static float jointStands(float[] light, int side, boolean acrossA, float line, float from, float to) {
        int low = Math.floorMod((int) Math.floor(line - 0.5f), side), high = Math.floorMod((int) Math.floor(line + 0.5f), side);
        if (low < 1 || low > side - 2 || high < 1 || high > side - 2) {
            return Float.NaN;
        }
        float sum = 0f;
        int held = 0;
        float[] beside = new float[4];
        for (int t = (int) Math.ceil(from); t < to; t++) {
            int along = Math.floorMod(t, side);
            if (along < 1 || along > side - 2) {
                continue;
            }
            float a = lightAt(light, side, acrossA, low, along), b = lightAt(light, side, acrossA, high, along);
            beside[0] = lightAt(light, side, acrossA, Math.floorMod(low - 2, side), along);
            beside[1] = lightAt(light, side, acrossA, Math.floorMod(low - 3, side), along);
            beside[2] = lightAt(light, side, acrossA, Math.floorMod(high + 2, side), along);
            beside[3] = lightAt(light, side, acrossA, Math.floorMod(high + 3, side), along);
            if (Float.isNaN(a) || Float.isNaN(b) || Float.isNaN(beside[0]) || Float.isNaN(beside[1]) || Float.isNaN(beside[2]) || Float.isNaN(beside[3])) {
                continue;
            }
            float middle = median(beside, 4);
            sum += Math.max(middle - Math.min(a, b), Math.max(a, b) - middle);
            held++;
        }
        return held == 0 ? Float.NaN : sum / held;
    }

    /** Whether dot k is one of the two either side of a line at line dots, as jointStands reads them. */
    private static boolean besideLine(int k, float line, int side) {
        return k == Math.floorMod((int) Math.floor(line - 0.5f), side) || k == Math.floorMod((int) Math.floor(line + 0.5f), side);
    }

    private static float lightAt(float[] light, int side, boolean acrossA, int across, int along) {
        return light[acrossA ? across * side + along : along * side + across];
    }

    /**
     * Which of the joints one way are laid, by how far each stands out against their median, or
     * null where one is neither laid nor not, or the median is a plain floor's. A joint by the
     * square's edge, which cannot be read, is laid.
     */
    private static boolean[] laid(float[] stands) {
        float[] read = new float[stands.length];
        int held = 0;
        for (float s : stands) {
            if (!Float.isNaN(s)) {
                read[held++] = s;
            }
        }
        if (held == 0) {
            return null;
        }
        float middle = median(read, held);
        if (middle < PAVER_JOINTS) {
            return null;
        }
        boolean[] laid = new boolean[stands.length];
        for (int k = 0; k < stands.length; k++) {
            float s = stands[k];
            if (Float.isNaN(s) || s >= JOINED * middle) {
                laid[k] = true;
            } else if (s > APART * middle) {
                return null;
            }
        }
        return laid;
    }

    private static float median(float[] values, int count) {
        float[] sorted = Arrays.copyOf(values, count);
        Arrays.sort(sorted);
        return count % 2 == 1 ? sorted[count / 2] : (sorted[count / 2 - 1] + sorted[count / 2]) / 2f;
    }

    private static int root(int[] paverOf, int cell) {
        while (paverOf[cell] != cell) {
            cell = paverOf[cell];
        }
        return cell;
    }

    private static void join(int[] paverOf, int one, int other) {
        int a = root(paverOf, one), b = root(paverOf, other);
        paverOf[Math.max(a, b)] = Math.min(a, b);
    }

    /**
     * The first cell along a and along b of the paver whose lowest cell is root, and how many
     * cells it runs along each, if its cells are a rectangle of them, running on over the
     * square's edge into the next where they do; else null.
     */
    private static int[] rectangle(int[] paverOf, int root, int cellsA, int cellsB) {
        int size = 0;
        for (int cell = 0; cell < paverOf.length; cell++) {
            size += root(paverOf, cell) == root ? 1 : 0;
        }
        for (int cell = 0; cell < paverOf.length; cell++) {
            if (root(paverOf, cell) != root) {
                continue;
            }
            int i = cell / cellsB, j = cell % cellsB;
            for (int wide = 1; wide <= cellsA; wide++) {
                if (size % wide != 0 || size / wide > cellsB) {
                    continue;
                }
                int deep = size / wide;
                boolean all = true;
                for (int di = 0; di < wide && all; di++) {
                    for (int dj = 0; dj < deep && all; dj++) {
                        all = root(paverOf, ((i + di) % cellsA) * cellsB + (j + dj) % cellsB) == root;
                    }
                }
                if (all) {
                    return new int[] {i, j, wide, deep};
                }
            }
        }
        return null;
    }

    /**
     * Hexagonal pavers in a square of floor laid flat, or null. The light of a honeycomb repeats
     * across the square along three ways at once, and of the strongest repeats the two that
     * make such a three with the fewest pavers to the square give the steps between the pavers'
     * middles. The middles are put where the pavers' edges, half way between neighbouring
     * middles, run over the darkest dots: the joints. Every paver's colour is read inside it,
     * away from its edges, and the joints' on its edges.
     */
    static Shape hexes(float[] dots, int side) {
        float[] light = new float[side * side];
        double sum = 0;
        int painted = 0;
        for (int dot = 0; dot < side * side; dot++) {
            if (dots[dot * 3] >= 0f) {
                light[dot] = 0.299f * dots[dot * 3] + 0.587f * dots[dot * 3 + 1] + 0.114f * dots[dot * 3 + 2];
                sum += light[dot];
                painted++;
            }
        }
        float mean = (float) (sum / painted);
        float[] cos = new float[side], sin = new float[side];
        for (int n = 0; n < side; n++) {
            cos[n] = (float) Math.cos(2.0 * Math.PI * n / side);
            sin[n] = (float) Math.sin(2.0 * Math.PI * n / side);
        }
        // How strongly the light repeats kA times across the square along a and kB times along b,
        // kB from 0 up: a repeat and its opposite are one.
        // Summed along b for each row first, then along a: the read is on the thread that draws.
        int rows = HEX_MOST + 1;
        double[] rowRe = new double[side * rows], rowIm = new double[side * rows];
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                int dot = i * side + j;
                if (dots[dot * 3] < 0f) {
                    continue;
                }
                float off = light[dot] - mean;
                for (int kB = 0; kB <= HEX_MOST; kB++) {
                    int turn = kB * j % side;
                    rowRe[i * rows + kB] += off * cos[turn];
                    rowIm[i * rows + kB] -= off * sin[turn];
                }
            }
        }
        float[] strength = new float[(2 * HEX_MOST + 1) * rows], re = new float[strength.length], im = new float[strength.length];
        Integer[] ranked = new Integer[(2 * HEX_MOST + 1) * rows - rows];
        int candidates = 0;
        for (int kA = -HEX_MOST; kA <= HEX_MOST; kA++) {
            for (int kB = 0; kB <= HEX_MOST; kB++) {
                if (kB == 0 && kA <= 0) {
                    continue;
                }
                double sumRe = 0, sumIm = 0;
                for (int i = 0; i < side; i++) {
                    int turn = Math.floorMod(kA * i, side);
                    double r = rowRe[i * rows + kB], m = rowIm[i * rows + kB];
                    sumRe += r * cos[turn] + m * sin[turn];
                    sumIm += m * cos[turn] - r * sin[turn];
                }
                int at = (kA + HEX_MOST) * rows + kB;
                re[at] = (float) sumRe;
                im[at] = (float) sumIm;
                strength[at] = (float) Math.hypot(sumRe, sumIm);
                ranked[candidates++] = at;
            }
        }
        Arrays.sort(ranked, 0, candidates, (p, q) -> Float.compare(strength[q], strength[p]));
        float[] rest = new float[candidates - HEX_TRIED];
        for (int n = HEX_TRIED; n < candidates; n++) {
            rest[n - HEX_TRIED] = strength[ranked[n]];
        }
        float usual = median(rest, rest.length);
        int pickedA1 = 0, pickedB1 = 0, pickedA2 = 0, pickedB2 = 0, pickedThird = 0, fewest = Integer.MAX_VALUE;
        float pickedWeakest = 0f;
        for (int p = 0; p < HEX_TRIED; p++) {
            for (int q = p + 1; q < HEX_TRIED; q++) {
                int a1 = ranked[p] / rows - HEX_MOST, b1 = ranked[p] % rows, a2 = ranked[q] / rows - HEX_MOST, b2 = ranked[q] % rows;
                for (int sign = -1; sign <= 1; sign += 2) {
                    int a3 = a1 + sign * a2, b3 = b1 + sign * b2;
                    if (b3 < 0 || b3 == 0 && a3 < 0) {
                        a3 = -a3;
                        b3 = -b3;
                    }
                    int count = Math.abs(a1 * b2 - b1 * a2);
                    if (Math.abs(a3) > HEX_MOST || b3 > HEX_MOST || count < FEWEST_HEXES) {
                        continue;
                    }
                    int third = (a3 + HEX_MOST) * rows + b3;
                    float s1 = strength[ranked[p]], s2 = strength[ranked[q]], s3 = strength[third];
                    float weakest = Math.min(s1, Math.min(s2, s3)), strongest = Math.max(s1, Math.max(s2, s3));
                    double l1 = Math.hypot(a1, b1), l2 = Math.hypot(a2, b2), l3 = Math.hypot(a3, b3);
                    if (weakest < HEX_PEAK * usual || strongest > HEX_EVEN * weakest
                            || Math.max(l1, Math.max(l2, l3)) > HEX_ROUND * Math.min(l1, Math.min(l2, l3))) {
                        continue;
                    }
                    if (count < fewest || count == fewest && weakest > pickedWeakest) {
                        fewest = count;
                        pickedWeakest = weakest;
                        pickedA1 = a1;
                        pickedB1 = b1;
                        pickedA2 = a2;
                        pickedB2 = b2;
                        pickedThird = third;
                    }
                }
            }
        }
        if (fewest == Integer.MAX_VALUE) {
            return null;
        }
        // The steps between middles, in dots: each a whole repeat of one of the two and none of the other.
        double turned = pickedA1 * pickedB2 - pickedB1 * pickedA2;
        double[] steps = {pickedB2 * side / turned, -pickedA2 * side / turned, -pickedB1 * side / turned, pickedA1 * side / turned};
        // Made as short and as square to each other as they go: each paver's neighbours are then a step either way along each and along the shorter of their sum and difference.
        while (true) {
            if (steps[2] * steps[2] + steps[3] * steps[3] < steps[0] * steps[0] + steps[1] * steps[1]) {
                double a = steps[0], b = steps[1];
                steps[0] = steps[2];
                steps[1] = steps[3];
                steps[2] = a;
                steps[3] = b;
            }
            double m = Math.rint((steps[0] * steps[2] + steps[1] * steps[3]) / (steps[0] * steps[0] + steps[1] * steps[1]));
            if (m == 0) {
                break;
            }
            steps[2] -= m * steps[0];
            steps[3] -= m * steps[1];
        }
        double[] corners = hexCorners(steps);
        // A first guess at a middle: where the three repeats are all at their lightest, the
        // pavers being lighter than their joints. Then the place near it, to a sixteenth of a
        // step each way, where the edges as the lattice puts them run over the darkest dots.
        int[] three = {(pickedA1 + HEX_MOST) * rows + pickedB1, (pickedA2 + HEX_MOST) * rows + pickedB2, pickedThird};
        double guessA = 0, guessB = 0, lightest = -Double.MAX_VALUE;
        for (int s1 = 0; s1 < HEX_PHASES; s1++) {
            for (int s2 = 0; s2 < HEX_PHASES; s2++) {
                double atA = (s1 * steps[0] + s2 * steps[2]) / HEX_PHASES, atB = (s1 * steps[1] + s2 * steps[3]) / HEX_PHASES, lit = 0;
                for (int at : three) {
                    double turn = 2.0 * Math.PI * ((at / rows - HEX_MOST) * (atA - 0.5) + at % rows * (atB - 0.5)) / side;
                    lit += re[at] * Math.cos(turn) - im[at] * Math.sin(turn);
                }
                if (lit > lightest) {
                    lightest = lit;
                    guessA = atA;
                    guessB = atB;
                }
            }
        }
        double[] nearest = new double[3];
        double bestA = 0, bestB = 0, clearest = -Double.MAX_VALUE;
        for (int s1 = -HEX_NEAR; s1 <= HEX_NEAR; s1++) {
            for (int s2 = -HEX_NEAR; s2 <= HEX_NEAR; s2++) {
                double fromA = guessA + (s1 * steps[0] + s2 * steps[2]) / HEX_PHASES, fromB = guessB + (s1 * steps[1] + s2 * steps[3]) / HEX_PHASES;
                double inside = 0, onJoint = 0;
                int insides = 0, onJoints = 0;
                for (int i = 0; i < side; i++) {
                    for (int j = 0; j < side; j++) {
                        if (dots[(i * side + j) * 3] < 0f) {
                            continue;
                        }
                        hexEdge(i + 0.5 - fromA, j + 0.5 - fromB, steps, nearest);
                        if (nearest[0] > HEX_INSIDE) {
                            inside += light[i * side + j];
                            insides++;
                        } else if (nearest[0] < HEX_JOINT) {
                            onJoint += light[i * side + j];
                            onJoints++;
                        }
                    }
                }
                double clear = insides == 0 || onJoints == 0 ? -Double.MAX_VALUE : inside / insides - onJoint / onJoints;
                if (clear > clearest) {
                    clearest = clear;
                    bestA = fromA;
                    bestB = fromB;
                }
            }
        }
        // Each dot's paver, by where its middle falls on the square, a quarter dot either way.
        java.util.HashMap<Integer, Integer> slots = new java.util.HashMap<>();
        int[] paverAt = new int[side * side];
        float[] edgeAt = new float[side * side];
        float[] middles = new float[fewest * 2];
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                int dot = i * side + j;
                paverAt[dot] = -1;
                if (dots[dot * 3] < 0f) {
                    continue;
                }
                hexEdge(i + 0.5 - bestA, j + 0.5 - bestB, steps, nearest);
                double midA = bestA + nearest[1] * steps[0] + nearest[2] * steps[2], midB = bestB + nearest[1] * steps[1] + nearest[2] * steps[3];
                int key = Math.floorMod((int) Math.round(midA * 4), side * 4) * side * 4 + Math.floorMod((int) Math.round(midB * 4), side * 4);
                Integer slot = slots.get(key);
                if (slot == null) {
                    if (slots.size() == fewest) {
                        return null;
                    }
                    slot = slots.size();
                    slots.put(key, slot);
                    // The middle exactly where the lattice puts it, rounded only for the key: two pavers each a quarter dot off opened a crack along their edge.
                    middles[slot * 2] = (float) (midA / side - Math.floor(midA / side));
                    middles[slot * 2 + 1] = (float) (midB / side - Math.floor(midB / side));
                }
                paverAt[dot] = slot;
                edgeAt[dot] = (float) nearest[0];
            }
        }
        if (slots.size() != fewest) {
            return null;
        }
        Shape shape = new Shape();
        shape.hexes = new float[fewest * 5];
        double[] sums = new double[fewest * 3], whole = new double[3];
        int[] held = new int[fewest];
        for (int dot = 0; dot < side * side; dot++) {
            int paver = paverAt[dot];
            if (paver < 0) {
                continue;
            }
            for (int part = 0; part < 3; part++) {
                whole[part] += dots[dot * 3 + part];
                if (edgeAt[dot] > HEX_INSIDE) {
                    sums[paver * 3 + part] += dots[dot * 3 + part];
                }
            }
            if (edgeAt[dot] > HEX_INSIDE) {
                held[paver]++;
            }
        }
        float[] paverLight = new float[fewest];
        for (int paver = 0; paver < fewest; paver++) {
            if (held[paver] == 0) {
                return null;
            }
            shape.hexes[paver * 5] = middles[paver * 2];
            shape.hexes[paver * 5 + 1] = middles[paver * 2 + 1];
            for (int part = 0; part < 3; part++) {
                shape.hexes[paver * 5 + 2 + part] = (float) (sums[paver * 3 + part] / held[paver]);
            }
            paverLight[paver] = 0.299f * shape.hexes[paver * 5 + 2] + 0.587f * shape.hexes[paver * 5 + 3] + 0.114f * shape.hexes[paver * 5 + 4];
        }
        shape.red = (float) (whole[0] / painted);
        shape.green = (float) (whole[1] / painted);
        shape.blue = (float) (whole[2] / painted);
        // How far the dots inside a paver lie from its light, on average, and how far below it those on its edges lie.
        double off = 0;
        int read = 0, onJoints = 0;
        float[] dips = new float[side * side], jointLight = new float[side * side];
        for (int dot = 0; dot < side * side; dot++) {
            int paver = paverAt[dot];
            if (paver < 0) {
                continue;
            }
            if (edgeAt[dot] > HEX_INSIDE) {
                off += Math.abs(light[dot] - paverLight[paver]);
                read++;
            } else if (edgeAt[dot] < HEX_JOINT) {
                dips[onJoints] = paverLight[paver] - light[dot];
                jointLight[onJoints++] = light[dot];
            }
        }
        if (onJoints == 0 || median(dips, onJoints) < PAVER_CLEAR * (float) (off / read)) {
            return null;
        }
        float darkHalf = median(jointLight, onJoints);
        double[] joint = new double[3];
        int joints = 0;
        for (int dot = 0; dot < side * side; dot++) {
            if (paverAt[dot] >= 0 && edgeAt[dot] < HEX_JOINT && light[dot] <= darkHalf) {
                for (int part = 0; part < 3; part++) {
                    joint[part] += dots[dot * 3 + part];
                }
                joints++;
            }
        }
        shape.jointRed = (float) (joint[0] / joints);
        shape.jointGreen = (float) (joint[1] / joints);
        shape.jointBlue = (float) (joint[2] / joints);
        shape.hexCorners = new float[12];
        for (int k = 0; k < 12; k++) {
            shape.hexCorners[k] = (float) (corners[k] / side);
        }
        shape.uneven = uneven(dots, side, 1, EDGE, null);
        return shape;
    }

    /**
     * The six corners of a paver of the lattice whose steps, the shortest it has and as square
     * to each other as they go, are (steps[0], steps[1]) and (steps[2], steps[3]): each where
     * the paver's middle and two neighbours next to each other round it are as far from it.
     * Along a and along b from the middle, in turn the way from a towards b.
     */
    private static double[] hexCorners(double[] steps) {
        double sumA = steps[0] + steps[2], sumB = steps[1] + steps[3], diffA = steps[0] - steps[2], diffB = steps[1] - steps[3];
        boolean sum = sumA * sumA + sumB * sumB <= diffA * diffA + diffB * diffB;
        double[] near = {steps[0], steps[1], steps[2], steps[3], sum ? sumA : diffA, sum ? sumB : diffB};
        double[][] around = new double[6][];
        for (int n = 0; n < 3; n++) {
            around[n * 2] = new double[] {near[n * 2], near[n * 2 + 1]};
            around[n * 2 + 1] = new double[] {-near[n * 2], -near[n * 2 + 1]};
        }
        Arrays.sort(around, (p, q) -> Double.compare(Math.atan2(p[1], p[0]), Math.atan2(q[1], q[0])));
        double[] corners = new double[12];
        for (int k = 0; k < 6; k++) {
            double[] p = around[k], q = around[(k + 1) % 6];
            double pp = (p[0] * p[0] + p[1] * p[1]) / 2, qq = (q[0] * q[0] + q[1] * q[1]) / 2, turn = p[0] * q[1] - p[1] * q[0];
            corners[k * 2] = (pp * q[1] - qq * p[1]) / turn;
            corners[k * 2 + 1] = (p[0] * qq - q[0] * pp) / turn;
        }
        return corners;
    }

    /**
     * For the point (a, b) from a middle of the lattice whose steps are as hexCorners has them:
     * into found, how far it is from the edge of the paver whose middle is nearest it, and how
     * many of each step lie between that middle and the one it was measured from.
     */
    private static void hexEdge(double a, double b, double[] steps, double[] found) {
        double turned = steps[0] * steps[3] - steps[1] * steps[2];
        double along1 = (a * steps[3] - b * steps[2]) / turned, along2 = (steps[0] * b - steps[1] * a) / turned;
        int base1 = (int) Math.floor(along1), base2 = (int) Math.floor(along2);
        double first = Double.MAX_VALUE, second = Double.MAX_VALUE;
        int at1 = 0, at2 = 0, next1 = 0, next2 = 0;
        for (int n1 = base1 - 1; n1 <= base1 + 2; n1++) {
            for (int n2 = base2 - 1; n2 <= base2 + 2; n2++) {
                double dA = a - n1 * steps[0] - n2 * steps[2], dB = b - n1 * steps[1] - n2 * steps[3], far = dA * dA + dB * dB;
                if (far < first) {
                    second = first;
                    next1 = at1;
                    next2 = at2;
                    first = far;
                    at1 = n1;
                    at2 = n2;
                } else if (far < second) {
                    second = far;
                    next1 = n1;
                    next2 = n2;
                }
            }
        }
        double apartA = (next1 - at1) * steps[0] + (next2 - at2) * steps[2], apartB = (next1 - at1) * steps[1] + (next2 - at2) * steps[3];
        found[0] = (second - first) / (2 * Math.hypot(apartA, apartB));
        found[1] = at1;
        found[2] = at2;
    }

    /**
     * Each paver's colour, from its dots more than a dot and a half from any line of the grid
     * of cells; the joints' colour, from the darker half of the two dots either side of each line
     * where a joint is laid, the game painting a joint as a dark line a dot wide with the paver's
     * lit edge beside it; and the floor's as a whole. Returns how far, in light, the dots read for
     * a paver's colour lie from the paver's light, on average.
     */
    private static float paverColours(float[] dots, float[] light, int side, Shape shape, int[] paverOf, int[] slot, boolean[] laidA, boolean[] laidB) {
        int cellsA = shape.cellsA, cellsB = shape.cellsB;
        float cellA = side / (float) cellsA, cellB = side / (float) cellsB;
        float firstA = shape.firstA * side, firstB = shape.firstB * side;
        int pavers = shape.pavers.length / 7;
        double[] sums = new double[pavers * 3], joint = new double[3], whole = new double[3];
        int[] held = new int[pavers], paverAt = new int[side * side];
        Arrays.fill(paverAt, -1);
        boolean[] onJoints = new boolean[side * side];
        int joints = 0, every = 0;
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                int at = (i * side + j) * 3;
                if (dots[at] < 0f) {
                    continue;
                }
                // Where the dot is in the grid of cells, in cells from the first joint, round the square.
                double fromA = (i + 0.5 - firstA) / cellA, fromB = (j + 0.5 - firstB) / cellB;
                float inA = (float) (fromA - Math.floor(fromA / cellsA) * cellsA), inB = (float) (fromB - Math.floor(fromB / cellsB) * cellsB);
                int ci = Math.min((int) inA, cellsA - 1), cj = Math.min((int) inB, cellsB - 1);
                float toA = Math.min(inA - ci, ci + 1 - inA) * cellA, toB = Math.min(inB - cj, cj + 1 - inB) * cellB;
                // The line nearest each way, and whether a joint is laid on it beside this cell.
                int lineA = inA - ci < 0.5f ? ci : (ci + 1) % cellsA, lineB = inB - cj < 0.5f ? cj : (cj + 1) % cellsB;
                boolean onJoint = besideLine(i, firstA + lineA * cellA, side) && laidA[lineA * cellsB + cj]
                        || besideLine(j, firstB + lineB * cellB, side) && laidB[ci * cellsB + lineB];
                for (int part = 0; part < 3; part++) {
                    whole[part] += dots[at + part];
                }
                every++;
                if (onJoint) {
                    onJoints[i * side + j] = true;
                } else if (toA > 1.5f && toB > 1.5f) {
                    int paver = slot[root(paverOf, ci * cellsB + cj)];
                    paverAt[i * side + j] = paver;
                    for (int part = 0; part < 3; part++) {
                        sums[paver * 3 + part] += dots[at + part];
                    }
                    held[paver]++;
                }
            }
        }
        for (int paver = 0; paver < pavers; paver++) {
            for (int part = 0; part < 3; part++) {
                shape.pavers[paver * 7 + 4 + part] = (float) (held[paver] == 0 ? whole[part] / every : sums[paver * 3 + part] / held[paver]);
            }
        }
        shape.red = (float) (whole[0] / every);
        shape.green = (float) (whole[1] / every);
        shape.blue = (float) (whole[2] / every);
        float[] jointLight = new float[side * side];
        int onLines = 0;
        for (int dot = 0; dot < side * side; dot++) {
            if (onJoints[dot]) {
                jointLight[onLines++] = light[dot];
            }
        }
        float darkHalf = onLines == 0 ? 0f : median(jointLight, onLines);
        for (int dot = 0; dot < side * side; dot++) {
            if (onJoints[dot] && light[dot] <= darkHalf) {
                for (int part = 0; part < 3; part++) {
                    joint[part] += dots[dot * 3 + part];
                }
                joints++;
            }
        }
        shape.jointRed = (float) (joint[0] / Math.max(joints, 1));
        shape.jointGreen = (float) (joint[1] / Math.max(joints, 1));
        shape.jointBlue = (float) (joint[2] / Math.max(joints, 1));
        double off = 0;
        int read = 0;
        for (int dot = 0; dot < side * side; dot++) {
            int paver = paverAt[dot];
            if (paver >= 0) {
                float[] at = shape.pavers;
                off += Math.abs(light[dot] - (0.299f * at[paver * 7 + 4] + 0.587f * at[paver * 7 + 5] + 0.114f * at[paver * 7 + 6]));
                read++;
            }
        }
        return read == 0 ? Float.MAX_VALUE : (float) (off / read);
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

    /**
     * Whether a square of floor laid flat is speckled: its light strays from its mean as chips do,
     * and runs no way, as the joints of boards laid end to end do (SPECKLED_LEAST, SPECKLED_RUNS).
     */
    static boolean speckled(float[] dots, int side) {
        float[] light = new float[side * side];
        double sum = 0;
        int painted = 0;
        for (int dot = 0; dot < side * side; dot++) {
            if (dots[dot * 3] >= 0f) {
                light[dot] = 0.299f * dots[dot * 3] + 0.587f * dots[dot * 3 + 1] + 0.114f * dots[dot * 3 + 2];
                sum += light[dot];
                painted++;
            }
        }
        float mean = (float) (sum / painted);
        double strays = 0;
        for (int dot = 0; dot < side * side; dot++) {
            if (dots[dot * 3] < 0f) {
                light[dot] = mean;
            }
            strays += (light[dot] - mean) * (light[dot] - mean);
        }
        strays /= side * side;
        if (strays < SPECKLED_LEAST * SPECKLED_LEAST) {
            return false;
        }
        double rows = 0, columns = 0;
        for (int k = 0; k < side; k++) {
            double row = 0, column = 0;
            for (int other = 0; other < side; other++) {
                row += light[k * side + other] - mean;
                column += light[other * side + k] - mean;
            }
            rows += (row / side) * (row / side);
            columns += (column / side) * (column / side);
        }
        return Math.max(rows, columns) / side <= SPECKLED_RUNS * strays;
    }

}
