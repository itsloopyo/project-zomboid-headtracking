package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * The outline of what a sprite paints on the ground, as straight edges and smooth bends.
 *
 * The game paints a road line as a diamond of dots, sixty-four to a tile, and a line's edge
 * in it is a staircase. Laid flat and magnified to a road underfoot the staircase is a saw
 * edge. But a painted line is a simple shape: a stripe with two straight edges, a bar, a
 * triangle, a bend. Here the sprite's dots, laid flat, are read for that shape. Its outline is
 * traced where the dots are half covered, the long runs of it are fitted with straight lines,
 * and a line that is within a few degrees of running along the map, across it, or at one of
 * the slants a mapper lays lines on is set to run exactly so, at a whole number of the
 * sprite's own dots from the square's corner. Two squares that hold the same sprite, or two
 * sprites drawn to meet, then meet on the edge they share to the last digit, and a line of
 * many squares is one straight stripe of one width.
 *
 * What comes out is triangles, each corner with how far inside the outline it is, so the
 * picture can tell how much of a pixel the paint covers: see RoadPaint.
 */
final class PaintFit {
    /** Rings of bare dots put round what was read before the outline is traced, so every outline closes. */
    private static final int RING = 3;
    /**
     * A corner of an outline made of lengths shorter in all than the first, in dots, between
     * two straight lines each at least as long as the last, is those two lines' own corner
     * when none of it lies further than the second outside either line, or than the third
     * inside both: the rest is the staircase of the sprite's diamond. A corner cut off further
     * in than that is cut off in the paint.
     */
    private static final float CORNER_LONGEST = 12f, CORNER_OUT = 2.2f, CORNER_IN = 1.3f, CORNER_BETWEEN = 6f;
    /** Short lengths between two straight ones are one straight length in a direction lines are laid in when they are at least this long together, in dots, and none of them lies further than the second from it. */
    private static final float CHAIN_LONG = 4f, CHAIN_OFF = 0.9f;
    /** How far, in dots, the outline may lie from a straight line and still be that line. */
    private static final float STRAIGHT = 0.7f;
    /** Two fitted lines this near to one direction, in radians, are tried as one. */
    private static final float SAME_WAY = 0.06f;
    /** A line this near, in radians, to a direction lines are laid in runs exactly in it. */
    private static final float NEAR_WAY = 0.05f;
    /** An edge at least this long, in dots, is fitted with a line of its own direction, and one this long is straight whatever its direction. */
    private static final float FITTED = 5f, LONG = 9f;
    /** Paint smaller than this, in dots squared, is a speck and is left out. A hole larger than it is a shape this does not read. */
    private static final float SPECK = 10f;
    /** Paint thinner than this, in dots, is not read. */
    private static final float THINNEST = 1.5f;
    /** How far past its outline, in dots, a shape's triangles go on, carrying how far outside they are. */
    private static final float SKIRT_TILES = 0.15f;
    /** How many times each bend is cut to round it. */
    private static final int ROUNDINGS = 2;
    /** An outline that keeps within this many dots of the square's own edge for at least the second runs along that edge: the sprite ends there with its diamond, in a staircase. */
    private static final float EDGE_WITHIN = 1.75f, EDGE_LONG = 3f;
    /** A length of outline that turns the same way at both its ends, by more than the first and less than the second, in radians, is part of a curve and is not made straight. */
    private static final float CURVE_LEAST = 0.07f, CURVE_MOST = 0.7f;
    /** And is itself, with the length before it and the one after, at least this long in dots: shorter, it is a corner and not a curve. */
    private static final float CURVE_LONG = 6f;
    /** Two lines cross at a usable point when the sine of the angle between them is at least this, and the point is no further than the second, in dots, from where the outline turns. */
    private static final float CROSSING = 0.17f, FAR_CORNER = 12f;

    /** The directions lines are laid in, as whole steps along the map each way: along, across, the diagonals, and one square over for two along. */
    private static final int[][] WAYS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}, {2, 1}, {1, 2}, {2, -1}, {1, -2}};

    /** Floats to a corner of a triangle: where on the square it is, along x then y in tiles, how far inside the outline, and how thick the paint is there, both in tiles. */
    static final int CORNER = 4;

    private PaintFit() {}

    /** One length of an outline: the line it lies on, n . p = c with n pointing out of the paint, and what kind of length it is. */
    private static final class Edge {
        double nx, ny, c;
        /** The points of the traced outline it was fitted to, as places in the loop, first to last. */
        int from, to;
        boolean straight;
        /** Whether it lies along the square's own edge. */
        boolean onEdge;
    }

    /**
     * The triangles of what the sprite paints. cover holds side + 2 * margin dots a row, the
     * dot at x and y of the square, each counted from margin dots before the square's corner,
     * at x * (side + 2 * margin) + y, each from 0 for bare to 1 for painted: a sprite may paint
     * past its own square. Null for a sprite that paints nothing that can be read: nothing at
     * all, the whole square, a shape with a hole in it, a hairline.
     */
    static float[] triangles(float[] cover, int side, int margin) {
        int wide = side + 2 * margin, n = wide + 2 * RING;
        float[] field = field(cover, wide, n);
        ArrayList<double[]> loops = trace(field, n, margin + RING);
        Out out = new Out();
        boolean any = false;
        for (double[] loop : loops) {
            double area = area(loop, loop.length / 2);
            if (Math.abs(area) < SPECK) {
                continue;
            }
            if (area < 0.0) {
                return null;
            }
            double[] outline = outline(loop, side);
            int count = outline.length / 2;
            if (count < 3 || area(outline, count) < SPECK) {
                continue;
            }
            if (!mesh(outline, count, side, cover, margin, out)) {
                return null;
            }
            any = true;
        }
        return any ? out.toTiles(side) : null;
    }

    /**
     * How well triangles this made agree with the dots they were made from: of the dots that
     * either paints, the share both paint. 1 for a perfect reading. A staircase read as a
     * straight edge costs a few hundredths.
     */
    static float agreement(float[] cover, int side, int margin, float[] triangles) {
        int wide = side + 2 * margin;
        boolean[] painted = new boolean[wide * wide];
        for (int at = 0; at < triangles.length; at += 3 * CORNER) {
            float ax = triangles[at] * side + margin, ay = triangles[at + 1] * side + margin, ad = triangles[at + 2];
            float bx = triangles[at + CORNER] * side + margin, by = triangles[at + CORNER + 1] * side + margin, bd = triangles[at + CORNER + 2];
            float cx = triangles[at + 2 * CORNER] * side + margin, cy = triangles[at + 2 * CORNER + 1] * side + margin, cd = triangles[at + 2 * CORNER + 2];
            float twice = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
            if (Math.abs(twice) < 1e-6f) {
                continue;
            }
            int fromI = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx)))), toI = Math.min(wide - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
            int fromJ = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy)))), toJ = Math.min(wide - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
            for (int i = fromI; i <= toI; i++) {
                for (int j = fromJ; j <= toJ; j++) {
                    float x = i + 0.5f, y = j + 0.5f;
                    float a = ((bx - x) * (cy - y) - (by - y) * (cx - x)) / twice, b = ((cx - x) * (ay - y) - (cy - y) * (ax - x)) / twice, c = 1f - a - b;
                    painted[i * wide + j] |= a >= 0f && b >= 0f && c >= 0f && a * ad + b * bd + c * cd >= 0f;
                }
            }
        }
        int both = 0, either = 0;
        for (int at = 0; at < painted.length; at++) {
            boolean dot = cover[at] >= 0.5f;
            both += dot && painted[at] ? 1 : 0;
            either += dot || painted[at] ? 1 : 0;
        }
        return either == 0 ? 0f : (float) both / either;
    }

    /** The dots with rings of bare dots round them, softened so a staircase is a slope. */
    private static float[] field(float[] cover, int wide, int n) {
        float[] field = new float[n * n], spare = new float[n * n];
        for (int i = 0; i < wide; i++) {
            System.arraycopy(cover, i * wide, field, (i + RING) * n + RING, wide);
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    float before = i > 0 ? field[(i - 1) * n + j] : 0f, after = i < n - 1 ? field[(i + 1) * n + j] : 0f;
                    spare[i * n + j] = 0.25f * before + 0.5f * field[i * n + j] + 0.25f * after;
                }
            }
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    float before = j > 0 ? spare[i * n + j - 1] : 0f, after = j < n - 1 ? spare[i * n + j + 1] : 0f;
                    field[i * n + j] = 0.25f * before + 0.5f * spare[i * n + j] + 0.25f * after;
                }
            }
        }
        return field;
    }

    /** Where the field is half covered, as closed loops of points in dots from the square's corner, each with the paint on its left. The middle of the field's dot i lies i + 0.5 - before dots from the corner. */
    private static ArrayList<double[]> trace(float[] field, int n, int before) {
        int[] first = new int[2 * n * n], second = new int[2 * n * n];
        Arrays.fill(first, -1);
        Arrays.fill(second, -1);
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1; j++) {
                float v0 = field[i * n + j], v1 = field[(i + 1) * n + j], v2 = field[(i + 1) * n + j + 1], v3 = field[i * n + j + 1];
                int which = (v0 >= 0.5f ? 1 : 0) | (v1 >= 0.5f ? 2 : 0) | (v2 >= 0.5f ? 4 : 0) | (v3 >= 0.5f ? 8 : 0);
                if (which == 0 || which == 15) {
                    continue;
                }
                int low = 2 * (i * n + j), right = 2 * ((i + 1) * n + j) + 1, high = 2 * (i * n + j + 1), left = 2 * (i * n + j) + 1;
                boolean middle = v0 + v1 + v2 + v3 >= 2f;
                switch (which) {
                    case 1, 14 -> join(first, second, left, low);
                    case 2, 13 -> join(first, second, low, right);
                    case 4, 11 -> join(first, second, right, high);
                    case 8, 7 -> join(first, second, high, left);
                    case 3, 12 -> join(first, second, left, right);
                    case 6, 9 -> join(first, second, low, high);
                    default -> {
                        if (which == 5 == middle) {
                            join(first, second, low, right);
                            join(first, second, high, left);
                        } else {
                            join(first, second, left, low);
                            join(first, second, right, high);
                        }
                    }
                }
            }
        }
        ArrayList<double[]> loops = new ArrayList<>();
        boolean[] seen = new boolean[first.length];
        double[] points = new double[64];
        for (int start = 0; start < first.length; start++) {
            if (first[start] < 0 || seen[start]) {
                continue;
            }
            int count = 0, at = start, from = -1;
            while (!seen[at]) {
                seen[at] = true;
                if (count * 2 + 2 > points.length) {
                    points = Arrays.copyOf(points, points.length * 2);
                }
                int cell = at >> 1, i = cell / n, j = cell % n;
                if ((at & 1) == 0) {
                    float here = field[cell], there = field[cell + n];
                    points[count * 2] = i + (0.5f - here) / (there - here) + 0.5 - before;
                    points[count * 2 + 1] = j + 0.5 - before;
                } else {
                    float here = field[cell], there = field[cell + 1];
                    points[count * 2] = i + 0.5 - before;
                    points[count * 2 + 1] = j + (0.5f - here) / (there - here) + 0.5 - before;
                }
                count++;
                int next = first[at] != from ? first[at] : second[at];
                from = at;
                at = next;
            }
            if (count < 4) {
                continue;
            }
            double[] loop = Arrays.copyOf(points, count * 2);
            // The paint is put on the left: a point just left of the first step is asked.
            double dx = loop[2] - loop[0], dy = loop[3] - loop[1], length = Math.hypot(dx, dy);
            double askX = (loop[0] + loop[2]) / 2.0 - dy / length * 0.3 + before - 0.5, askY = (loop[1] + loop[3]) / 2.0 + dx / length * 0.3 + before - 0.5;
            if (at(field, n, askX, askY) < 0.5f) {
                for (int k = 0; k < count / 2; k++) {
                    int other = count - 1 - k;
                    double x = loop[k * 2], y = loop[k * 2 + 1];
                    loop[k * 2] = loop[other * 2];
                    loop[k * 2 + 1] = loop[other * 2 + 1];
                    loop[other * 2] = x;
                    loop[other * 2 + 1] = y;
                }
            }
            loops.add(loop);
        }
        return loops;
    }

    private static void join(int[] first, int[] second, int one, int other) {
        if (first[one] < 0) {
            first[one] = other;
        } else {
            second[one] = other;
        }
        if (first[other] < 0) {
            first[other] = one;
        } else {
            second[other] = one;
        }
    }

    /** The field between its points, at a place in the field's own count of dots. */
    private static float at(float[] field, int n, double x, double y) {
        int i = Math.clamp((int) Math.floor(x), 0, n - 2), j = Math.clamp((int) Math.floor(y), 0, n - 2);
        float fx = (float) (x - i), fy = (float) (y - j);
        return (field[i * n + j] * (1f - fx) + field[(i + 1) * n + j] * fx) * (1f - fy) + (field[i * n + j + 1] * (1f - fx) + field[(i + 1) * n + j + 1] * fx) * fy;
    }

    private static double area(double[] points, int count) {
        double twice = 0.0;
        for (int k = 0; k < count; k++) {
            int next = (k + 1) % count;
            twice += points[k * 2] * points[next * 2 + 1] - points[next * 2] * points[k * 2 + 1];
        }
        return twice / 2.0;
    }

    /** A traced loop as the corners of its outline: straight where it is straight, rounded where it bends gently, sharp where it turns a corner. */
    private static double[] outline(double[] loop, int side) {
        int count = loop.length / 2;
        boolean[] kept = new boolean[count];
        double mx = 0.0, my = 0.0;
        for (int k = 0; k < count; k++) {
            mx += loop[k * 2] / count;
            my += loop[k * 2 + 1] / count;
        }
        int start = farthest(loop, count, mx, my);
        int opposite = farthest(loop, count, loop[start * 2], loop[start * 2 + 1]);
        kept[start] = kept[opposite] = true;
        split(loop, count, start, opposite, kept);
        split(loop, count, opposite, start, kept);
        ArrayList<Edge> edges = new ArrayList<>();
        int from = start;
        do {
            int to = (from + 1) % count;
            while (!kept[to]) {
                to = (to + 1) % count;
            }
            edges.add(fitted(loop, count, from, to));
            from = to;
        } while (from != start);
        // Two lengths that run the same way and lie on one line are one length.
        for (boolean merged = edges.size() > 3; merged && edges.size() > 3;) {
            merged = false;
            for (int k = 0; k < edges.size() && edges.size() > 3; k++) {
                Edge one = edges.get(k), next = edges.get((k + 1) % edges.size());
                if (one.nx * next.nx + one.ny * next.ny < Math.cos(SAME_WAY)) {
                    continue;
                }
                Edge both = fitted(loop, count, one.from, next.to);
                if (off(loop, count, both) < STRAIGHT) {
                    edges.set(k, both);
                    edges.remove((k + 1) % edges.size());
                    merged = true;
                }
            }
        }
        boolean[] curved = new boolean[edges.size()];
        for (int k = 0; k < edges.size(); k++) {
            Edge before = edges.get((k + edges.size() - 1) % edges.size()), edge = edges.get(k), after = edges.get((k + 1) % edges.size());
            double in = Math.asin(Math.clamp(before.nx * edge.ny - before.ny * edge.nx, -1.0, 1.0)), out = Math.asin(Math.clamp(edge.nx * after.ny - edge.ny * after.nx, -1.0, 1.0));
            curved[k] = length(loop, before) >= CURVE_LONG && length(loop, edge) >= CURVE_LONG && length(loop, after) >= CURVE_LONG && in * out > 0.0 && Math.abs(in) > CURVE_LEAST && Math.abs(in) < CURVE_MOST && Math.abs(out) > CURVE_LEAST && Math.abs(out) < CURVE_MOST;
        }
        for (int k = 0; k < edges.size(); k++) {
            Edge edge = edges.get(k);
            double length = Math.hypot(loop[edge.to * 2] - loop[edge.from * 2], loop[edge.to * 2 + 1] - loop[edge.from * 2 + 1]);
            if (length >= EDGE_LONG && setToTheSquaresEdge(loop, count, edge, side)) {
                edge.straight = edge.onEdge = true;
                continue;
            }
            if (curved[k]) {
                chord(loop, edge);
                continue;
            }
            boolean set = length >= FITTED && setToAWay(loop, count, edge);
            edge.straight = set || length >= LONG;
            if (length < FITTED) {
                chord(loop, edge);
            }
        }
        ownCorners(loop, count, edges);
        straightChains(loop, count, edges);
        int size = edges.size();
        double[] corners = new double[size * 2];
        boolean[] straight = new boolean[size];
        for (int k = 0; k < size; k++) {
            Edge before = edges.get((k + size - 1) % size), after = edges.get(k);
            double x = loop[after.from * 2], y = loop[after.from * 2 + 1];
            double det = before.nx * after.ny - before.ny * after.nx;
            double crossX = x, crossY = y;
            if (Math.abs(det) >= CROSSING) {
                crossX = (before.c * after.ny - after.c * before.ny) / det;
                crossY = (before.nx * after.c - after.nx * before.c) / det;
            }
            if (Math.abs(det) < CROSSING || Math.hypot(crossX - x, crossY - y) > FAR_CORNER) {
                // No corner to speak of: the traced point, put on the straight length it ends.
                Edge on = before.straight ? before : after.straight ? after : null;
                crossX = x;
                crossY = y;
                if (on != null) {
                    double away = on.nx * x + on.ny * y - on.c;
                    crossX -= away * on.nx;
                    crossY -= away * on.ny;
                }
            }
            corners[k * 2] = crossX;
            corners[k * 2 + 1] = crossY;
            straight[k] = after.straight;
        }
        for (int round = 0; round < ROUNDINGS; round++) {
            double[] cut = new double[size * 4];
            boolean[] cutStraight = new boolean[size * 2];
            int made = 0;
            for (int k = 0; k < size; k++) {
                int before = (k + size - 1) % size, after = (k + 1) % size;
                double inX = corners[k * 2] - corners[before * 2], inY = corners[k * 2 + 1] - corners[before * 2 + 1];
                double outX = corners[after * 2] - corners[k * 2], outY = corners[after * 2 + 1] - corners[k * 2 + 1];
                double turn = Math.abs(Math.atan2(inX * outY - inY * outX, inX * outX + inY * outY));
                boolean bend = !(straight[before] && straight[k]) && turn > 0.03 && turn < 0.9;
                if (!bend) {
                    cut[made * 2] = corners[k * 2];
                    cut[made * 2 + 1] = corners[k * 2 + 1];
                    cutStraight[made++] = straight[k];
                    continue;
                }
                // A straight length gives up no more than a dot or two of itself to the bend.
                double inShare = straight[before] ? Math.min(0.25, 2.0 / Math.hypot(inX, inY)) : 0.25;
                double outShare = straight[k] ? Math.min(0.25, 2.0 / Math.hypot(outX, outY)) : 0.25;
                cut[made * 2] = corners[k * 2] - inX * inShare;
                cut[made * 2 + 1] = corners[k * 2 + 1] - inY * inShare;
                cutStraight[made++] = false;
                cut[made * 2] = corners[k * 2] + outX * outShare;
                cut[made * 2 + 1] = corners[k * 2 + 1] + outY * outShare;
                cutStraight[made++] = straight[k];
            }
            corners = Arrays.copyOf(cut, made * 2);
            straight = Arrays.copyOf(cutStraight, made);
            size = made;
        }
        return corners;
    }

    /**
     * Takes out of an outline the short lengths that lie between two long straight ones and
     * keep close to them: the two then meet in a corner of their own. The sprite's diamond is
     * a staircase with a blunt end, and where a line runs into it the line's corner is a dot
     * or two out of true.
     */
    private static void ownCorners(double[] loop, int count, ArrayList<Edge> edges) {
        for (int k = 0; k < edges.size() && edges.size() > 3; k++) {
            Edge before = edges.get(k);
            if (!before.straight || !before.onEdge && length(loop, before) < CORNER_BETWEEN) {
                continue;
            }
            int chain = 0;
            double run = 0.0;
            Edge after = null;
            while (chain < edges.size() - 2) {
                Edge next = edges.get((k + 1 + chain) % edges.size());
                if (next.straight && (next.onEdge || length(loop, next) >= CORNER_BETWEEN)) {
                    after = next;
                    break;
                }
                run += length(loop, next);
                chain++;
            }
            if (after == null || chain == 0 || run >= CORNER_LONGEST || Math.abs(before.nx * after.ny - before.ny * after.nx) < CROSSING) {
                continue;
            }
            boolean close = true;
            for (int at = before.to; close; at = (at + 1) % count) {
                double x = loop[at * 2], y = loop[at * 2 + 1];
                double one = before.nx * x + before.ny * y - before.c, other = after.nx * x + after.ny * y - after.c;
                close = Math.min(Math.abs(one), Math.abs(other)) <= (one < 0.0 && other < 0.0 ? CORNER_IN : CORNER_OUT);
                if (at == after.from) {
                    break;
                }
            }
            if (!close) {
                continue;
            }
            for (int gone = 0; gone < chain; gone++) {
                int at = (k + 1) % edges.size();
                edges.remove(at);
                if (at < k) {
                    k--;
                }
            }
            before.to = after.from;
        }
    }

    /**
     * Makes one straight length of the short lengths between two straight ones, where one line
     * in a direction lines are laid in lies close to all of them: the short side of a stripe
     * cut off by the corner of its square, which is too short to be fitted for itself.
     */
    private static void straightChains(double[] loop, int count, ArrayList<Edge> edges) {
        for (int k = 0; k < edges.size() && edges.size() > 3; k++) {
            if (!edges.get(k).straight) {
                continue;
            }
            int chain = 0;
            while (chain < edges.size() - 2 && !edges.get((k + 1 + chain) % edges.size()).straight) {
                chain++;
            }
            if (chain == 0) {
                continue;
            }
            Edge whole = new Edge();
            whole.from = edges.get(k).to;
            whole.to = edges.get((k + chain) % edges.size()).to;
            if (length(loop, whole) < CHAIN_LONG) {
                continue;
            }
            chord(loop, whole);
            double chordX = whole.nx, chordY = whole.ny;
            boolean set = false;
            int points = (whole.to - whole.from + count) % count + 1;
            for (int[] way : WAYS) {
                double long1 = Math.hypot(way[0], way[1]);
                double nx = way[1] / long1, ny = -way[0] / long1;
                if (nx * chordX + ny * chordY < 0.0) {
                    nx = -nx;
                    ny = -ny;
                }
                if (nx * chordX + ny * chordY < 0.94) {
                    continue;
                }
                double sum = 0.0;
                for (int at = 0; at < points; at++) {
                    int p = (whole.from + at) % count;
                    sum += nx * loop[p * 2] + ny * loop[p * 2 + 1];
                }
                double c = Math.round(sum / points * long1) / long1, most = 0.0;
                for (int at = 1; at < points - 1; at++) {
                    int p = (whole.from + at) % count;
                    most = Math.max(most, Math.abs(nx * loop[p * 2] + ny * loop[p * 2 + 1] - c));
                }
                if (most <= CHAIN_OFF) {
                    whole.nx = nx;
                    whole.ny = ny;
                    whole.c = c;
                    set = true;
                    break;
                }
            }
            if (!set) {
                continue;
            }
            whole.straight = true;
            for (int gone = 0; gone < chain; gone++) {
                int at = (k + 1) % edges.size();
                edges.remove(at);
                if (at < k) {
                    k--;
                }
            }
            edges.add(k + 1, whole);
        }
    }

    private static double length(double[] loop, Edge edge) {
        return Math.hypot(loop[edge.to * 2] - loop[edge.from * 2], loop[edge.to * 2 + 1] - loop[edge.from * 2 + 1]);
    }

    private static int farthest(double[] loop, int count, double x, double y) {
        int best = 0;
        double most = -1.0;
        for (int k = 0; k < count; k++) {
            double away = Math.hypot(loop[k * 2] - x, loop[k * 2 + 1] - y);
            if (away > most) {
                most = away;
                best = k;
            }
        }
        return best;
    }

    /** Keeps the point of the loop between two kept ones that lies farthest from the line through them, if it lies further than a straight line may, and so on either side of it. */
    private static void split(double[] loop, int count, int from, int to, boolean[] kept) {
        double x = loop[from * 2], y = loop[from * 2 + 1], dx = loop[to * 2] - x, dy = loop[to * 2 + 1] - y, length = Math.hypot(dx, dy);
        int worst = -1;
        double most = STRAIGHT;
        for (int k = (from + 1) % count; k != to; k = (k + 1) % count) {
            double px = loop[k * 2] - x, py = loop[k * 2 + 1] - y;
            double away = length < 1e-9 ? Math.hypot(px, py) : Math.abs(px * dy - py * dx) / length;
            if (away > most) {
                most = away;
                worst = k;
            }
        }
        if (worst >= 0) {
            kept[worst] = true;
            split(loop, count, from, worst, kept);
            split(loop, count, worst, to, kept);
        }
    }

    /** The line that lies nearest the loop's points from one place to another, clear of the two ends where the outline is turning. */
    private static Edge fitted(double[] loop, int count, int from, int to) {
        Edge edge = new Edge();
        edge.from = from;
        edge.to = to;
        int points = (to - from + count) % count + 1;
        int trim = Math.min(2, (points - 1) / 3);
        int used = points - 2 * trim;
        if (used < 3) {
            chord(loop, edge);
            return edge;
        }
        double mx = 0.0, my = 0.0;
        for (int k = 0; k < used; k++) {
            int at = (from + trim + k) % count;
            mx += loop[at * 2] / used;
            my += loop[at * 2 + 1] / used;
        }
        double xx = 0.0, xy = 0.0, yy = 0.0;
        for (int k = 0; k < used; k++) {
            int at = (from + trim + k) % count;
            double px = loop[at * 2] - mx, py = loop[at * 2 + 1] - my;
            xx += px * px;
            xy += px * py;
            yy += py * py;
        }
        double angle = 0.5 * Math.atan2(2.0 * xy, xx - yy);
        double dx = Math.cos(angle), dy = Math.sin(angle);
        if (dx * (loop[to * 2] - loop[from * 2]) + dy * (loop[to * 2 + 1] - loop[from * 2 + 1]) < 0.0) {
            dx = -dx;
            dy = -dy;
        }
        edge.nx = dy;
        edge.ny = -dx;
        edge.c = edge.nx * mx + edge.ny * my;
        return edge;
    }

    /** Sets a length to the line through its two ends. */
    private static void chord(double[] loop, Edge edge) {
        double x = loop[edge.from * 2], y = loop[edge.from * 2 + 1], dx = loop[edge.to * 2] - x, dy = loop[edge.to * 2 + 1] - y, length = Math.hypot(dx, dy);
        edge.nx = length < 1e-9 ? 1.0 : dy / length;
        edge.ny = length < 1e-9 ? 0.0 : -dx / length;
        edge.c = edge.nx * x + edge.ny * y;
    }

    /** How far the loop's points along a length lie from its line at most, the turning ends left out. */
    private static double off(double[] loop, int count, Edge edge) {
        int points = (edge.to - edge.from + count) % count + 1;
        int trim = Math.min(2, (points - 1) / 3);
        double most = 0.0;
        for (int k = trim; k < points - trim; k++) {
            int at = (edge.from + k) % count;
            most = Math.max(most, Math.abs(edge.nx * loop[at * 2] + edge.ny * loop[at * 2 + 1] - edge.c));
        }
        return most;
    }

    /**
     * Sets a length every point of which lies close to one edge of the square to lie along
     * that edge, facing out of the square. False, changing nothing, for any other length.
     */
    private static boolean setToTheSquaresEdge(double[] loop, int count, Edge edge, int side) {
        int points = (edge.to - edge.from + count) % count + 1;
        for (int which = 0; which < 4; which++) {
            int axis = which & 1;
            double place = which < 2 ? 0.0 : side;
            boolean near = true;
            for (int k = 0; k < points && near; k++) {
                near = Math.abs(loop[((edge.from + k) % count) * 2 + axis] - place) <= EDGE_WITHIN;
            }
            // The paint lies on the square's side of the edge: the length faces out as the edge does.
            double out = which < 2 ? -1.0 : 1.0;
            if (near && (axis == 0 ? edge.nx : edge.ny) * out > 0.5) {
                edge.nx = axis == 0 ? out : 0.0;
                edge.ny = axis == 0 ? 0.0 : out;
                edge.c = out * place;
                return true;
            }
        }
        return false;
    }

    /**
     * Turns a length that runs nearly in one of the directions lines are laid in to run in it
     * exactly, a whole number of dots from the square's corner as that direction counts them.
     * False, changing nothing, for a length that runs no such way.
     */
    private static boolean setToAWay(double[] loop, int count, Edge edge) {
        for (int[] way : WAYS) {
            double length = Math.hypot(way[0], way[1]);
            // The line runs along the way, so its normal is the way turned a quarter.
            double nx = way[1] / length, ny = -way[0] / length;
            double along = edge.nx * nx + edge.ny * ny;
            if (Math.abs(along) < Math.cos(NEAR_WAY)) {
                continue;
            }
            if (along < 0.0) {
                nx = -nx;
                ny = -ny;
            }
            int points = (edge.to - edge.from + count) % count + 1;
            int trim = Math.min(2, (points - 1) / 3);
            double sum = 0.0;
            for (int k = trim; k < points - trim; k++) {
                int at = (edge.from + k) % count;
                sum += nx * loop[at * 2] + ny * loop[at * 2 + 1];
            }
            edge.nx = nx;
            edge.ny = ny;
            edge.c = Math.round(sum / (points - 2 * trim) * length) / length;
            return true;
        }
        return false;
    }

    /** The triangles being made, in dots. */
    private static final class Out {
        double[] corners = new double[256];
        int floats;

        void add(double x, double y, double inside, double thick) {
            if (floats + CORNER > corners.length) {
                corners = Arrays.copyOf(corners, corners.length * 2);
            }
            corners[floats++] = x;
            corners[floats++] = y;
            corners[floats++] = inside;
            corners[floats++] = thick;
        }

        void triangle(double ax, double ay, double ad, double bx, double by, double bd, double cx, double cy, double cd, double thick) {
            if (Math.abs((bx - ax) * (cy - ay) - (by - ay) * (cx - ax)) < 1e-4) {
                return;
            }
            add(ax, ay, ad, thick);
            add(bx, by, bd, thick);
            add(cx, cy, cd, thick);
        }

        float[] toTiles(int side) {
            float[] tiles = new float[floats];
            for (int k = 0; k < floats; k++) {
                tiles[k] = (float) (corners[k] / side);
            }
            return tiles;
        }
    }

    /**
     * Adds the triangles of one outline, its corners in dots with the paint on the left. An
     * edge the paint goes on across into the next square is open and has no edge there: one
     * that lies along the square's own edge, and one that the same sprite laid on the next
     * square would paint up against, which is how a bar drawn a little off its square meets
     * the next length of itself. From every other edge a band runs in to half the paint's
     * thickness, each corner carrying how far inside the outline it is, and a skirt runs out,
     * carrying how far outside. False for an outline that is all open or too thin.
     */
    private static boolean mesh(double[] corners, int count, int side, float[] cover, int margin, Out out) {
        boolean[] open = new boolean[count];
        double[] nx = new double[count], ny = new double[count], c = new double[count];
        boolean closed = false;
        int wide = side + 2 * margin;
        for (int k = 0; k < count; k++) {
            int next = (k + 1) % count;
            double x = corners[k * 2], y = corners[k * 2 + 1], dx = corners[next * 2] - x, dy = corners[next * 2 + 1] - y, length = Math.hypot(dx, dy);
            nx[k] = dy / length;
            ny[k] = -dx / length;
            c[k] = nx[k] * x + ny[k] * y;
            boolean along = Math.abs(nx[k]) > 0.9999, across = Math.abs(ny[k]) > 0.9999;
            if (along || across) {
                double outward = along ? nx[k] : ny[k], place = along ? x : y;
                // On the square's own edge, facing out of the square.
                open[k] = outward < 0.0 ? Math.abs(place) < 1e-6 : Math.abs(place - side) < 1e-6;
                // Or met by the sprite's own paint a square further on.
                boolean met = !open[k] && length >= 3.0;
                for (int at = 1; at <= 3 && met; at++) {
                    double px = x + dx * at / 4.0 + nx[k] * (0.75 - side), py = y + dy * at / 4.0 + ny[k] * (0.75 - side);
                    int i = (int) Math.floor(px + margin), j = (int) Math.floor(py + margin);
                    met = i >= 0 && j >= 0 && i < wide && j < wide && cover[i * wide + j] >= 0.5f;
                }
                open[k] |= met;
            }
            closed |= !open[k];
        }
        if (!closed) {
            return false;
        }
        double half = halfThickness(corners, count, open, nx, ny, c);
        if (half * 2.0 < THINNEST) {
            return false;
        }
        double thick = half * 2.0, skirt = SKIRT_TILES * side;
        // Each corner moved in to the middle of the paint: half its thickness in from every edge that is one, and along an open edge.
        double[] inner = new double[count * 2];
        for (int k = 0; k < count; k++) {
            int before = (k + count - 1) % count;
            double x = corners[k * 2], y = corners[k * 2 + 1];
            double c1 = c[before] - (open[before] ? 0.0 : half), c2 = c[k] - (open[k] ? 0.0 : half);
            double det = nx[before] * ny[k] - ny[before] * nx[k];
            double inX, inY;
            if (Math.abs(det) < 1e-3) {
                double in = open[k] ? (open[before] ? 0.0 : half) : half;
                inX = x - (open[k] ? nx[before] : nx[k]) * in;
                inY = y - (open[k] ? ny[before] : ny[k]) * in;
            } else {
                inX = (c1 * ny[k] - c2 * ny[before]) / det;
                inY = (nx[before] * c2 - nx[k] * c1) / det;
                double far = Math.hypot(inX - x, inY - y);
                if (far > 4.0 * half) {
                    inX = x + (inX - x) * 4.0 * half / far;
                    inY = y + (inY - y) * 4.0 * half / far;
                }
            }
            inner[k * 2] = inX;
            inner[k * 2 + 1] = inY;
        }
        for (int k = 0; k < count; k++) {
            if (open[k]) {
                continue;
            }
            int next = (k + 1) % count, before = (k + count - 1) % count, after = (k + 2) % count;
            double ax = corners[k * 2], ay = corners[k * 2 + 1], bx = corners[next * 2], by = corners[next * 2 + 1];
            double iax = inner[k * 2], iay = inner[k * 2 + 1], ibx = inner[next * 2], iby = inner[next * 2 + 1];
            double da = c[k] - (nx[k] * iax + ny[k] * iay), db = c[k] - (nx[k] * ibx + ny[k] * iby);
            if ((ibx - iax) * (bx - ax) + (iby - iay) * (by - ay) < 0.0) {
                // The edge is shorter than the paint is thick, and the two corners cross on their way in: the band is a triangle, to where they cross.
                double ux = iax - ax, uy = iay - ay, vx = ibx - bx, vy = iby - by;
                double det = ux * vy - uy * vx;
                double t = Math.abs(det) > 1e-9 ? ((bx - ax) * vy - (by - ay) * vx) / det : 0.5;
                // Two corners that run in side by side never cross: the band then ends half way between them.
                boolean crossed = t > 0.0 && t <= 1.0;
                double px = crossed ? ax + ux * t : (iax + ibx) / 2.0, py = crossed ? ay + uy * t : (iay + iby) / 2.0;
                out.triangle(ax, ay, 0.0, bx, by, 0.0, px, py, c[k] - (nx[k] * px + ny[k] * py), thick);
            } else {
                out.triangle(ax, ay, 0.0, bx, by, 0.0, ibx, iby, db, thick);
                out.triangle(ax, ay, 0.0, ibx, iby, db, iax, iay, da, thick);
            }
            // The skirt. Where the edge ends on an open one the skirt ends on that edge's line too, so the next square's skirt meets it.
            double pax = ax + nx[k] * skirt, pay = ay + ny[k] * skirt, pbx = bx + nx[k] * skirt, pby = by + ny[k] * skirt;
            if (open[before]) {
                double det = nx[k] * ny[before] - ny[k] * nx[before];
                if (Math.abs(det) > 0.1) {
                    pax = ((c[k] + skirt) * ny[before] - c[before] * ny[k]) / det;
                    pay = (nx[k] * c[before] - nx[before] * (c[k] + skirt)) / det;
                }
            }
            if (open[next]) {
                double det = nx[k] * ny[next] - ny[k] * nx[next];
                if (Math.abs(det) > 0.1) {
                    pbx = ((c[k] + skirt) * ny[next] - c[next] * ny[k]) / det;
                    pby = (nx[k] * c[next] - nx[next] * (c[k] + skirt)) / det;
                }
            }
            out.triangle(ax, ay, 0.0, pax, pay, -skirt, pbx, pby, -skirt, thick);
            out.triangle(ax, ay, 0.0, pbx, pby, -skirt, bx, by, 0.0, thick);
            // Round an outward corner between this edge and the next, the wedge the two skirts leave.
            if (!open[next] && nx[k] * ny[next] - ny[k] * nx[next] > 1e-6) {
                out.triangle(bx, by, 0.0, bx + nx[k] * skirt, by + ny[k] * skirt, -skirt, bx + nx[next] * skirt, by + ny[next] * skirt, -skirt, thick);
            }
        }
        fill(corners, inner, count, half, thick, out);
        return true;
    }

    /**
     * Half the paint's thickness in dots: the least of how far it is across from the middle of
     * each of its edges to whatever edge faces it, and never more than the widest circle that
     * fits in it clear of its edges.
     */
    private static double halfThickness(double[] corners, int count, boolean[] open, double[] nx, double[] ny, double[] c) {
        double least = Double.MAX_VALUE;
        for (int k = 0; k < count; k++) {
            int next = (k + 1) % count;
            double length = Math.hypot(corners[next * 2] - corners[k * 2], corners[next * 2 + 1] - corners[k * 2 + 1]);
            if (open[k] || length < 3.0) {
                continue;
            }
            double x = (corners[k * 2] + corners[next * 2]) / 2.0, y = (corners[k * 2 + 1] + corners[next * 2 + 1]) / 2.0;
            double nearest = Double.MAX_VALUE;
            for (int other = 0; other < count; other++) {
                if (other == k) {
                    continue;
                }
                int after = (other + 1) % count;
                double ox = corners[other * 2], oy = corners[other * 2 + 1], ex = corners[after * 2] - ox, ey = corners[after * 2 + 1] - oy;
                // The ray from (x, y) along -n against the edge from o along e.
                double det = -nx[k] * ey + ny[k] * ex;
                if (Math.abs(det) < 1e-9) {
                    continue;
                }
                double t = ((ox - x) * ey - (oy - y) * ex) / det;
                double along = (-(ox - x) * ny[k] + (oy - y) * nx[k]) / det;
                if (t > 1e-3 && along >= -1e-6 && along <= 1.0 + 1e-6) {
                    nearest = Math.min(nearest, t);
                }
            }
            if (nearest < Double.MAX_VALUE) {
                least = Math.min(least, nearest / 2.0);
            }
        }
        double lowX = Double.MAX_VALUE, lowY = Double.MAX_VALUE, highX = -Double.MAX_VALUE, highY = -Double.MAX_VALUE;
        for (int k = 0; k < count; k++) {
            lowX = Math.min(lowX, corners[k * 2]);
            highX = Math.max(highX, corners[k * 2]);
            lowY = Math.min(lowY, corners[k * 2 + 1]);
            highY = Math.max(highY, corners[k * 2 + 1]);
        }
        double widest = 0.0;
        for (double x = lowX + 0.25; x < highX; x += 0.5) {
            for (double y = lowY + 0.25; y < highY; y += 0.5) {
                if (!within(corners, count, x, y)) {
                    continue;
                }
                double nearest = Double.MAX_VALUE;
                for (int k = 0; k < count && nearest > widest; k++) {
                    if (!open[k]) {
                        int next = (k + 1) % count;
                        nearest = Math.min(nearest, fromSegment(x, y, corners[k * 2], corners[k * 2 + 1], corners[next * 2], corners[next * 2 + 1]));
                    }
                }
                widest = Math.max(widest, nearest);
            }
        }
        return Math.min(least, widest + 0.25);
    }

    private static boolean within(double[] corners, int count, double x, double y) {
        boolean inside = false;
        for (int k = 0, before = count - 1; k < count; before = k++) {
            double ax = corners[k * 2], ay = corners[k * 2 + 1], bx = corners[before * 2], by = corners[before * 2 + 1];
            if (ay > y != by > y && x < (bx - ax) * (y - ay) / (by - ay) + ax) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static double fromSegment(double x, double y, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, square = dx * dx + dy * dy;
        double t = square < 1e-12 ? 0.0 : Math.clamp(((x - ax) * dx + (y - ay) * dy) / square, 0.0, 1.0);
        return Math.hypot(x - ax - dx * t, y - ay - dy * t);
    }

    /**
     * Adds what is left of the paint inside its bands, every corner half the thickness inside:
     * an ear at a time. A stripe, a bar and a triangle leave nothing, their bands meeting along
     * the middle, and what they leave is a line gone over twice, which has ears that are no
     * part of it: an ear is cut only where it lies inside the outline. Where no ear is left to
     * cut the rest is left out.
     */
    private static void fill(double[] corners, double[] inner, int count, double half, double thick, Out out) {
        int[] left = new int[count];
        int size = 0;
        for (int k = 0; k < count; k++) {
            int before = size == 0 ? -1 : left[size - 1];
            if (before >= 0 && Math.hypot(inner[k * 2] - inner[before * 2], inner[k * 2 + 1] - inner[before * 2 + 1]) < 1e-3) {
                continue;
            }
            left[size++] = k;
        }
        while (size >= 3) {
            boolean cut = false;
            for (int k = 0; k < size && !cut; k++) {
                int a = left[(k + size - 1) % size], b = left[k], d = left[(k + 1) % size];
                double ax = inner[a * 2], ay = inner[a * 2 + 1], bx = inner[b * 2], by = inner[b * 2 + 1], dx = inner[d * 2], dy = inner[d * 2 + 1];
                double turn = (bx - ax) * (dy - ay) - (by - ay) * (dx - ax);
                if (turn < -1e-9) {
                    continue;
                }
                boolean clear = true;
                for (int other = 0; other < size && clear && turn > 1e-6; other++) {
                    int p = left[other];
                    if (p == a || p == b || p == d) {
                        continue;
                    }
                    double px = inner[p * 2], py = inner[p * 2 + 1];
                    double one = (bx - ax) * (py - ay) - (by - ay) * (px - ax), two = (dx - bx) * (py - by) - (dy - by) * (px - bx), three = (ax - dx) * (py - dy) - (ay - dy) * (px - dx);
                    clear = !(one > 1e-9 && two > 1e-9 && three > 1e-9);
                }
                if (!clear) {
                    continue;
                }
                if (turn > 1e-4 && !(within(corners, count, (ax + bx + dx) / 3.0, (ay + by + dy) / 3.0) && within(corners, count, ax * 0.45 + bx * 0.1 + dx * 0.45, ay * 0.45 + by * 0.1 + dy * 0.45))) {
                    continue;
                }
                out.triangle(ax, ay, half, bx, by, half, dx, dy, half, thick);
                System.arraycopy(left, k + 1, left, k, size - k - 1);
                size--;
                cut = true;
            }
            if (!cut) {
                return;
            }
        }
    }
}
