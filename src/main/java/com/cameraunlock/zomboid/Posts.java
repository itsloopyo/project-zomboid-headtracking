package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * A sprite that is a picture of something standing upright on one spot: a column, a porch
 * post, a lamp post, a bollard, a timber post with braces to a beam.
 *
 * The game says nothing of such a sprite but that it is on its square. Wrapped on the box its
 * outline fills, a round column is a crate with a column painted on it, and stood up as a
 * card it leans back at the slant it was drawn from. Its outline says what it is: every row
 * of it is centred on one upright line, the axis, and is as wide as the thing is thick there.
 *
 * Seen down the map's diagonal a round thing r tiles in radius is drawn 2 * sqrt(2) * r tiles
 * of the grid wide, and a square one of half side s, its sides along the grid, 4 * s wide. The
 * lowest rows tell the two apart: a square foot comes to a point, widening two pixels each
 * side for every row up, and a round one is the lower half of an ellipse, nearly as wide as
 * the thing a few rows up.
 *
 * Above where the rows stop being centred the thing has arms: the braces of a timber post,
 * the head of a lamp. The tips of the arms are kept, for a brace to be built to.
 */
final class Posts {
    static final int ROUND = 1, SQUARE = 2;

    /** The least a post stands, in levels, and the least it is tall for its width. */
    private static final float LOWEST = 0.42f, SLENDER = 2f;
    /** How far a row's middle may be off the axis, in frame pixels at tile scale 1, and the least of a row between its ends that is painted. */
    private static final float OFF_AXIS = 1.3f, FILLED = 0.78f;
    /** A row of the shaft is no wider than this many times the usual: past that it is the start of an arm. A round thing may swell to a globe. */
    private static final float SQUARE_SWELL = 1.45f, ROUND_SWELL = 3.3f;
    /** The widest a post is, in tiles: a round one's diameter, a square one's side. */
    private static final float WIDEST = 1.1f;
    /** The most stations a round post's profile is kept at, and how far off the line between two a radius may be, in tiles. */
    private static final int STATIONS = 20;
    private static final float PROFILE_SLACK = 0.012f;
    /** An arm reaches at least this many shaft widths past the shaft. */
    private static final float ARM_REACH = 0.85f;
    /** How much of its full width a foot is measured at, and the rows per pixel of that width between a round foot reaching it and a square one. */
    private static final float FOOT_SHARE = 0.6f, FOOT_ROWS_PER_PIXEL = 0.1f;
    /** How far off the usual a row's width may be to count as the shaft's own, as a share of it, and the share of the rows that must be so. */
    private static final float EVEN_SLACK = 0.2f, EVEN_SHARE = 0.6f;
    /** The most of a sprite's height that may be a foot of another shape under its shaft. */
    private static final float FOOT_MOST = 0.3f;
    /** A share of the rows of the shaft that are on the axis: a sprite with less is no post. */
    private static final float SHAFT_SHARE = 0.5f;

    final int kind;
    /** Where on its square the axis stands. */
    final float a, b;
    /** The profile, foot to top: heights in levels, and at each the radius of a round post or the half side of a square one, in tiles. */
    final float[] heights, halves;
    /** How thick the shaft mostly is: the usual radius or half side. */
    final float half;
    /**
     * The arms above the shaft, four numbers each: how far across the frame the tip is from
     * the axis and how far up from where the arms leave it, in frame pixels at tile scale 1,
     * then the frame pixel the arm is painted at half way, for its colour. Empty for a plain post.
     */
    final float[] arms;
    /** The height, in levels, at which the arms leave the shaft. */
    final float armsFrom;
    /** Whether anything is painted above the shaft that is no arm: a lamp's head, a pole's cross-arm. */
    final boolean headed;
    /** The share of the sprite's height, from its foot, that the shaft takes. */
    final float shaftShare;
    /** The share of the sprite's height, from its lowest row, that is a foot of another shape than the shaft: 0 for a post that is shaft to the floor. */
    final float footShare;

    private Posts(int kind, float a, float b, float[] heights, float[] halves, float half, float[] arms, float armsFrom, boolean headed, float shaftShare,
            float footShare) {
        this.kind = kind;
        this.a = a;
        this.b = b;
        this.heights = heights;
        this.halves = halves;
        this.half = half;
        this.arms = arms;
        this.armsFrom = armsFrom;
        this.headed = headed;
        this.shaftShare = shaftShare;
        this.footShare = footShare;
    }

    float top() {
        return heights[heights.length - 1];
    }

    /** Reads a sprite as a post, or null for one that is none. knownRound is for a thing the game says is a trunk: a pole's foot is drawn to a point. */
    static Posts read(SpriteShape.Painted painted, SpriteGeometry.Frame frame, boolean knownRound) {
        int left = Math.round(frame.left), top = Math.round(frame.top), width = Math.round(frame.width), height = Math.round(frame.height);
        float across = SpriteGeometry.PIXELS_ACROSS_PER_TILE * frame.scale, down = SpriteGeometry.PIXELS_DOWN_PER_TILE * frame.scale, level = SpriteGeometry.PIXELS_PER_LEVEL * frame.scale;
        if (width < 3 || height < LOWEST * level) {
            return null;
        }
        int[] first = new int[height], last = new int[height], filled = new int[height];
        scanRows(painted, left, top, width, first, last, filled);
        int foot = height - 1;
        while (foot >= 0 && first[foot] < 0) {
            foot--;
        }
        if (foot < LOWEST * level) {
            return null;
        }
        // The axis and the usual width, from the lower half: arms and heads are above.
        int lower = (foot + 1) / 2;
        float[] middles = new float[foot + 1 - lower], widths = new float[foot + 1 - lower];
        for (int row = lower; row <= foot; row++) {
            if (first[row] < 0) {
                return null;
            }
            middles[row - lower] = middleAt(first, last, row);
            widths[row - lower] = wideAt(first, last, row);
        }
        Arrays.sort(middles);
        Arrays.sort(widths);
        float axis = middles[middles.length / 2], usual = widths[widths.length / 2];
        float slack = OFF_AXIS * frame.scale;

        // Round or square, by how fast the foot widens from its lowest row.
        float footWide = 0f;
        for (int row = foot; row > foot - Math.max(usual / 2f, 3f) && row >= 0; row--) {
            footWide = Math.max(footWide, wideAt(first, last, row));
        }
        // A square foot is a point that widens four pixels a row, and takes a row for every
        // 6.7 pixels of its width to be 0.6 of it. A round one is there in a third of that.
        int rowsUp = 0;
        while (rowsUp < foot && wideAt(first, last, foot - rowsUp) < FOOT_SHARE * footWide) {
            rowsUp++;
        }
        // What a post stands on may be no part of its shaft: the bend at the foot of a
        // drainpipe, the weighted base of a standing lamp. The shaft starts above it.
        int base = foot, least = foot - Math.round(FOOT_MOST * (foot + 1));
        while (base > least && (Math.abs(middleAt(first, last, base) - axis) > slack + wideAt(first, last, base) * 0.04f
                || wideAt(first, last, base) > ROUND_SWELL * usual + frame.scale)) {
            base--;
        }
        boolean footed = foot - base > usual / 2f + frame.scale && base > least;
        if (!footed) {
            base = foot;
        }
        int kind = knownRound || footed || rowsUp < footWide * FOOT_ROWS_PER_PIXEL ? ROUND : SQUARE;
        float swell = kind == ROUND ? ROUND_SWELL : SQUARE_SWELL;

        // The shaft: the rows from the foot up that are on the axis, whole, and no wider than a shaft swells.
        int shaftTop = base, hollow = 0;
        for (int row = base; row >= 0; row--) {
            if (first[row] < 0) {
                break;
            }
            float wide = wideAt(first, last, row), middle = middleAt(first, last, row);
            // The lowest rows are the near corner of the foot, which the game's light and line draw to one side.
            float off = slack + wide * 0.04f + (foot - row < usual / 2f ? usual / 3f : 0f);
            if (Math.abs(middle - axis) > off || wide > swell * usual + frame.scale) {
                break;
            }
            if (filled[row] < wide * FILLED) {
                // The top of a length of column that goes on in the square above is cut to the
                // curve of its near side: rows with only their two ends painted, as wide as the shaft.
                if (Math.abs(wide - wideAt(first, last, shaftTop)) > frame.scale || ++hollow > usual * 0.3f + 2f) {
                    break;
                }
                continue;
            }
            if (hollow > 0) {
                break;
            }
            shaftTop = row;
        }
        shaftTop -= hollow;
        // A shaft is one thickness most of the way up. A spire is centred on its axis too, and is no post.
        int even = 0;
        for (int row = shaftTop; row <= base; row++) {
            if (Math.abs(wideAt(first, last, row) - usual) <= usual * EVEN_SLACK + frame.scale) {
                even++;
            }
        }
        if (even < EVEN_SHARE * (base - shaftTop + 1)) {
            return null;
        }
        int shaftRows = base - shaftTop + 1;
        if (shaftRows < LOWEST * level || shaftRows < SLENDER * usual || shaftRows < SHAFT_SHARE * (foot + 1)) {
            return null;
        }
        float perHalf = kind == ROUND ? 2f * (float) Math.sqrt(2.0) * across : 4f * across;
        float half = usual / perHalf;
        if (2f * half > WIDEST) {
            return null;
        }
        // The lowest row is the near edge of the foot: the axis is back from it by the foot's reach along the diagonal.
        // A post on a foot of another shape is taken to stand where its shaft would come down to the floor.
        float reach = (footed ? usual : footWide) / perHalf * (kind == ROUND ? (float) Math.sqrt(2.0) : 2f);
        float axisV = top + foot + 1 - reach * down;
        float sum = (axisV - frame.originV) / down, difference = (left + axis - frame.originU) / across;
        float a = (sum + difference) / 2f, b = (sum - difference) / 2f;

        // The profile. A ring of the thing at some height is widest on the row its middle is drawn at.
        // The top's own first rows are its far edge coming to a point or a curve: it is as wide as the shaft just under them.
        float topWide = 0f;
        for (int row = shaftTop; row <= Math.min(foot, shaftTop + Math.round(usual / 2f)); row++) {
            topWide = Math.max(topWide, wideAt(first, last, row));
        }
        float topReach = topWide / perHalf * (kind == ROUND ? (float) Math.sqrt(2.0) : 2f);
        // A top cut to the curve of its near side has the middle of its ring on its first row.
        float topRow = hollow > 0 ? shaftTop : shaftTop + topReach * down, footRow = foot + 1 - reach * down;
        if (footRow - topRow < LOWEST * level * 0.8f) {
            return null;
        }
        float[] heights, halves;
        if (kind == SQUARE) {
            heights = new float[] {0f, (footRow - topRow) / level};
            halves = new float[] {half, half};
        } else {
            float fromRow = footed ? Math.min(base, footRow) : footRow;
            int rows = Math.max(2, Math.round(fromRow - topRow) + 1);
            float[] allHeights = new float[rows], allHalves = new float[rows];
            for (int i = 0; i < rows; i++) {
                float row = fromRow - (fromRow - topRow) * i / (rows - 1);
                int at = Math.clamp(Math.round(row - 0.5f), shaftTop, base);
                allHeights[i] = (footRow - row) / level;
                allHalves[i] = wideAt(first, last, at) / perHalf;
            }
            int[] kept = simplify(allHeights, allHalves);
            heights = new float[kept.length];
            halves = new float[kept.length];
            for (int i = 0; i < kept.length; i++) {
                heights[i] = allHeights[kept[i]];
                halves[i] = allHalves[kept[i]];
            }
        }

        // What is painted above the shaft: arms that reach out from it, or a head.
        float[] arms = new float[0];
        boolean headed = false;
        float armsFrom = (footRow - shaftTop) / level;
        if (shaftTop > 2 * frame.scale) {
            int leftTip = -1, rightTip = -1, leftRow = 0, rightRow = 0;
            for (int row = 0; row < shaftTop; row++) {
                if (first[row] < 0) {
                    continue;
                }
                if (leftTip < 0 || first[row] < leftTip) {
                    leftTip = first[row];
                    leftRow = row;
                }
                if (rightTip < 0 || last[row] > rightTip) {
                    rightTip = last[row];
                    rightRow = row;
                }
            }
            float[] found = new float[8];
            int count = 0;
            if (leftTip >= 0 && axis - leftTip > (ARM_REACH + 0.5f) * usual) {
                count = arm(found, count, leftTip + 0.5f - axis, shaftTop - leftRow, left + (leftTip + axis) / 2f, top + (leftRow + shaftTop) / 2f, frame.scale);
            }
            if (rightTip >= 0 && rightTip + 1 - axis > (ARM_REACH + 0.5f) * usual) {
                count = arm(found, count, rightTip + 0.5f - axis, shaftTop - rightRow, left + (rightTip + axis) / 2f, top + (rightRow + shaftTop) / 2f, frame.scale);
            }
            arms = Arrays.copyOf(found, count * 4);
            headed = count == 0 || kind == ROUND;
            if (kind == ROUND) {
                arms = new float[0];
            }
        }
        return new Posts(kind, a, b, heights, halves, half, arms, armsFrom, headed, (foot + 1f - shaftTop) / height, footed ? (float) (foot - base) / height : 0f);
    }

    /** The first and the last painted column of each row of the frame, -1 for the first of a row with none, and how many are painted. */
    private static void scanRows(SpriteShape.Painted painted, int left, int top, int width, int[] first, int[] last, int[] filled) {
        for (int row = 0; row < first.length; row++) {
            first[row] = -1;
            for (int column = 0; column < width; column++) {
                if (painted.at(left + column, top + row)) {
                    if (first[row] < 0) {
                        first[row] = column;
                    }
                    last[row] = column;
                    filled[row]++;
                }
            }
        }
    }

    private static int wideAt(int[] first, int[] last, int row) {
        return last[row] - first[row] + 1;
    }

    private static float middleAt(int[] first, int[] last, int row) {
        return (first[row] + last[row] + 1) / 2f;
    }

    private static int arm(float[] into, int count, float acrossPixels, float upPixels, float paintedU, float paintedV, float scale) {
        into[count * 4] = acrossPixels / scale;
        into[count * 4 + 1] = upPixels / scale;
        into[count * 4 + 2] = paintedU;
        into[count * 4 + 3] = paintedV;
        return count + 1;
    }

    /** The stations of a profile that say all of it to within PROFILE_SLACK: its ends, and wherever it bends. */
    private static int[] simplify(float[] heights, float[] halves) {
        boolean[] keep = new boolean[heights.length];
        keep[0] = keep[heights.length - 1] = true;
        int kept = 2;
        while (kept < STATIONS) {
            int worst = -1;
            float worstBy = PROFILE_SLACK;
            int from = 0;
            for (int to = 1; to < heights.length; to++) {
                if (!keep[to]) {
                    continue;
                }
                for (int i = from + 1; i < to; i++) {
                    float on = halves[from] + (halves[to] - halves[from]) * (heights[i] - heights[from]) / (heights[to] - heights[from]);
                    float by = Math.abs(halves[i] - on);
                    if (by > worstBy) {
                        worstBy = by;
                        worst = i;
                    }
                }
                from = to;
            }
            if (worst < 0) {
                break;
            }
            keep[worst] = true;
            kept++;
        }
        int[] out = new int[kept];
        int at = 0;
        for (int i = 0; i < keep.length; i++) {
            if (keep[i]) {
                out[at++] = i;
            }
        }
        return out;
    }

    /**
     * Where an arm's tip is from the top of the shaft, into out as tiles along x, tiles along y
     * and levels up, taking the arms to lie in the upright plane along x or the one along y.
     * An arm drawn going left runs towards +y or towards -x, and one drawn going right
     * towards -y or +x: the frame's pixel says how far, and what is left of its rise is height.
     */
    void armTo(int arm, boolean alongX, float[] out) {
        float acrossPixels = arms[arm * 4], upPixels = arms[arm * 4 + 1];
        float run = acrossPixels / SpriteGeometry.PIXELS_ACROSS_PER_TILE;
        if (alongX) {
            // A step along +x is drawn 32 across and 16 down.
            out[0] = run;
            out[1] = 0f;
            out[2] = (upPixels + SpriteGeometry.PIXELS_DOWN_PER_TILE * run) / SpriteGeometry.PIXELS_PER_LEVEL;
        } else {
            out[0] = 0f;
            out[1] = -run;
            out[2] = (upPixels - SpriteGeometry.PIXELS_DOWN_PER_TILE * run) / SpriteGeometry.PIXELS_PER_LEVEL;
        }
    }

    /**
     * Which upright plane the arms lie in when nothing beside the post says: the one in
     * which two arms rise alike, or a single arm rises nearest to as far as it reaches.
     */
    boolean armsAlongX() {
        float[] to = new float[3];
        float alongX = 0f, alongY = 0f;
        float[] rises = new float[4];
        int count = arms.length / 4;
        for (int arm = 0; arm < count; arm++) {
            armTo(arm, true, to);
            rises[arm * 2] = to[2] * ViewCamera.LEVEL_HEIGHT;
            alongX += Math.abs(rises[arm * 2] - Math.abs(to[0]));
            armTo(arm, false, to);
            rises[arm * 2 + 1] = to[2] * ViewCamera.LEVEL_HEIGHT;
            alongY += Math.abs(rises[arm * 2 + 1] - Math.abs(to[1]));
        }
        if (count == 2) {
            return Math.abs(rises[0] - rises[2]) < Math.abs(rises[1] - rises[3]);
        }
        return alongX < alongY;
    }
}
