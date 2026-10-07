package com.cameraunlock.zomboid;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * The overhead wires a sprite shows.
 *
 * The game hangs a line of poles' wires as sprites on the squares of air under them, every
 * square of the run holding the lengths of wire that cross it: one, two or three thin
 * straight lines, each a dot or two thick. They are a run of the sprites of one sheet. The
 * lengths of the pole hang on such squares too, and come before them on the sheet, and what
 * is fixed to a pole, a street lamp's arm or a transformer, comes after. Each line is read as
 * its two ends, and each end as the place on the square it is drawn over at the height the
 * wires hang at.
 */
final class Wires {
    /**
     * How high over the floor of their square's level the wires hang, in levels: where the
     * insulators on a pole's arm are. Worked out on a run along y (the sprites 96 and 97 of
     * appliances_com_01): at this height its outer two wires are 0.8 of a tile either side of
     * the middle of the pole's square, and the third a hand off the pole.
     */
    static final float UP = 0.54f;
    /** The fewest dots a line is found by: fewer is a speck, or a piece of a line found by a longer piece. */
    private static final int FEWEST_DOTS = 4;
    /** How far off a line, in dots, the end of another piece of the same wire may be. */
    private static final float OFF_LINE = 1.6f;
    /** How far past the middle of its last dot each end of a line is taken, in dots. */
    private static final float OVERLAP = 1f;
    static final float[] NONE = new float[0];
    /** The first and the last sprite of the poles' sheet that is wire: before them come the poles, and aerials that are thin lines too, and after them what is fixed to a pole. */
    private static final int FIRST = 96, LAST = 135;
    /**
     * The ways a wire runs on the map, as the frame shows each, across then down: along x,
     * along y and along the two diagonals. A line is drawn in dots, and its two ends say which
     * way it runs only to within a dot: read off its ends alone, every square's length of a
     * run points a little off the last one's and the run is a saw's edge.
     */
    private static final float[] WAYS = {
        2f / (float) Math.sqrt(5.0), 1f / (float) Math.sqrt(5.0), -2f / (float) Math.sqrt(5.0), 1f / (float) Math.sqrt(5.0), 1f, 0f, 0f, 1f,
    };
    /** A line whose ends are within this of one of the ways, as the sine of the angle between them, runs that way. */
    private static final float SAME_WAY = 0.17f;

    private Wires() {}

    /** Whether a sprite is one of the sheet's wires, by its name. */
    static boolean hangs(String name) {
        int index = Poles.index(name);
        return index >= FIRST && index <= LAST;
    }

    /**
     * The wires the sprite in the frame shows: four numbers a wire, where on the square its
     * one end is, along x and along y in tiles from the square's north-west corner, then its
     * other end. A wire that leaves the square has an end outside 0 to 1. Empty for a sprite
     * with nothing but specks on it. A wire that runs level across the game's view fills the
     * whole of its sprite's rectangle, so how much is painted says nothing.
     */
    static float[] read(SpriteShape.Painted painted, SpriteGeometry.Frame frame) {
        int left = (int) frame.left, top = (int) frame.top, wide = (int) frame.width, high = (int) frame.height;
        boolean[] dots = new boolean[wide * high];
        int count = 0;
        for (int v = 0; v < high; v++) {
            for (int u = 0; u < wide; u++) {
                if (painted.at(left + u, top + v)) {
                    dots[v * wide + u] = true;
                    count++;
                }
            }
        }
        if (count < FEWEST_DOTS) {
            return NONE;
        }
        // Every run of dots that touch, with its two ends: the two dots furthest apart the
        // way it is longer.
        int[] ends = new int[0], sizes = new int[0];
        // The dots of each run added up, across and down: where its middle is.
        long[] sums = new long[0];
        ArrayDeque<Integer> open = new ArrayDeque<>();
        for (int start = 0; start < dots.length; start++) {
            if (!dots[start]) {
                continue;
            }
            int found = 0, lowU = start % wide, highU = lowU, lowV = start / wide, highV = lowV;
            int vAtLowU = lowV, vAtHighU = lowV, uAtLowV = lowU, uAtHighV = lowU;
            long sumU = 0, sumV = 0;
            dots[start] = false;
            open.add(start);
            while (!open.isEmpty()) {
                int at = open.poll(), u = at % wide, v = at / wide;
                found++;
                sumU += u;
                sumV += v;
                if (u < lowU) {
                    lowU = u;
                    vAtLowU = v;
                }
                if (u > highU) {
                    highU = u;
                    vAtHighU = v;
                }
                if (v < lowV) {
                    lowV = v;
                    uAtLowV = u;
                }
                if (v > highV) {
                    highV = v;
                    uAtHighV = u;
                }
                for (int dv = -1; dv <= 1; dv++) {
                    for (int du = -1; du <= 1; du++) {
                        int nu = u + du, nv = v + dv;
                        if (nu >= 0 && nu < wide && nv >= 0 && nv < high && dots[nv * wide + nu]) {
                            dots[nv * wide + nu] = false;
                            open.add(nv * wide + nu);
                        }
                    }
                }
            }
            boolean across = highU - lowU >= highV - lowV;
            ends = Arrays.copyOf(ends, ends.length + 4);
            ends[ends.length - 4] = across ? lowU : uAtLowV;
            ends[ends.length - 3] = across ? vAtLowU : lowV;
            ends[ends.length - 2] = across ? highU : uAtHighV;
            ends[ends.length - 1] = across ? vAtHighU : highV;
            sizes = Arrays.copyOf(sizes, sizes.length + 1);
            sizes[sizes.length - 1] = found;
            sums = Arrays.copyOf(sums, sums.length + 2);
            sums[sums.length - 2] = sumU;
            sums[sums.length - 1] = sumV;
        }
        // A wire painted a dot thick is broken wherever a dot of it is too faint to count, and
        // each piece would be a dash. The pieces that lie along one line are one wire: from
        // the longest piece down, each takes in every piece whose two ends are on its line.
        float[] lines = new float[0];
        boolean[] taken = new boolean[sizes.length];
        while (true) {
            int longest = -1;
            for (int i = 0; i < sizes.length; i++) {
                if (!taken[i] && sizes[i] >= FEWEST_DOTS && (longest < 0 || sizes[i] > sizes[longest])) {
                    longest = i;
                }
            }
            if (longest < 0) {
                break;
            }
            taken[longest] = true;
            float u0 = ends[longest * 4], v0 = ends[longest * 4 + 1], u1 = ends[longest * 4 + 2], v1 = ends[longest * 4 + 3];
            float alongU = u1 - u0, alongV = v1 - v0, length = (float) Math.sqrt(alongU * alongU + alongV * alongV);
            alongU /= length;
            alongV /= length;
            long joined = sizes[longest], sumU = sums[longest * 2], sumV = sums[longest * 2 + 1];
            float from = 0f, to = length;
            for (int i = 0; i < sizes.length; i++) {
                if (taken[i]) {
                    continue;
                }
                boolean onLine = true;
                for (int end = 0; end < 2; end++) {
                    float du = ends[i * 4 + end * 2] - u0, dv = ends[i * 4 + end * 2 + 1] - v0;
                    onLine &= Math.abs(du * alongV - dv * alongU) <= OFF_LINE;
                }
                if (!onLine) {
                    continue;
                }
                taken[i] = true;
                joined += sizes[i];
                sumU += sums[i * 2];
                sumV += sums[i * 2 + 1];
                for (int end = 0; end < 2; end++) {
                    float at = (ends[i * 4 + end * 2] - u0) * alongU + (ends[i * 4 + end * 2 + 1] - v0) * alongV;
                    from = Math.min(from, at);
                    to = Math.max(to, at);
                }
            }
            // A dot further at each end: the ends are the middles of the line's last dots, and
            // the faint dot beyond each is the one that joins it to the next square's line.
            float fromU = u0 + alongU * from, fromV = v0 + alongV * from, toU = u0 + alongU * to, toV = v0 + alongV * to;
            // The way it runs on the map, through the middle of its dots, as far as its ends reach.
            for (int way = 0; way < WAYS.length; way += 2) {
                float wayU = WAYS[way], wayV = WAYS[way + 1];
                if (Math.abs(alongU * wayV - alongV * wayU) > SAME_WAY) {
                    continue;
                }
                float middleU = (float) sumU / joined, middleV = (float) sumV / joined;
                float start = (fromU - middleU) * wayU + (fromV - middleV) * wayV, end = (toU - middleU) * wayU + (toV - middleV) * wayV;
                fromU = middleU + wayU * start;
                fromV = middleV + wayV * start;
                toU = middleU + wayU * end;
                toV = middleV + wayV * end;
                alongU = end > start ? wayU : -wayU;
                alongV = end > start ? wayV : -wayV;
                break;
            }
            lines = Arrays.copyOf(lines, lines.length + 4);
            place(frame, left + fromU - alongU * OVERLAP, top + fromV - alongV * OVERLAP, lines, lines.length - 4);
            place(frame, left + toU + alongU * OVERLAP, top + toV + alongV * OVERLAP, lines, lines.length - 2);
        }
        return lines;
    }

    /** Where on the square the frame's dot (u, v) is drawn over at the height the wires hang at, into two places of out. */
    private static void place(SpriteGeometry.Frame frame, float u, float v, float[] out, int at) {
        float across = (u + 0.5f - frame.originU) / (SpriteGeometry.PIXELS_ACROSS_PER_TILE * frame.scale);
        float down = ((v + 0.5f - frame.originV) / frame.scale + SpriteGeometry.PIXELS_PER_LEVEL * UP) / SpriteGeometry.PIXELS_DOWN_PER_TILE;
        out[at] = (down + across) / 2f;
        out[at + 1] = (down - across) / 2f;
    }
}
