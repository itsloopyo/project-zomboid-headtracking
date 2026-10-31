package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What else goes when holes are shot through a fence, a wall or a door's leaf (BlastHoles): what
 * the holes leave with nothing to hold it, and what they leave too weak to stand. Worked out on a
 * grid of CELL squares in the plane of the thing from its holes alone, the same on every thread
 * and on every load, so the picture cuts it (MeshCut) and a shot goes through it (BlastHoles.through)
 * alike, and nothing more is saved.
 *
 * A fence of boards is boards stood side by side, each BOARD_WIDE across at the places along the
 * map where the picture draws their joints (timber in the world's shader), and each held at its foot
 * and its top, by the rails it is nailed to. A board shot most of the way through at some height
 * breaks across there, its two ends splintered raggedly along the grain, and a blast's hole splits
 * some of the boards it went through on up or down the grain. A length of board then held at
 * neither end falls out. A wall's plasterboard or a door's leaf is one sheet held all round its
 * edge: what is left narrower than BRIDGE between two holes crumbles, and what is no longer joined
 * to the edge falls out.
 *
 * What goes is given as rectangles of the grid, in the plane's own measure: along in tiles and up
 * in levels, as the holes are. Any thread.
 */
final class Breakage {
    private Breakage() {}

    /** Kinds of thing: a fence of upright boards, or one sheet held at its edge. */
    static final int BOARDS = 0, SHEET = 1;

    /**
     * What is shot through: its kind, how far along its edge it reaches (tiles) and how high (levels
     * over its floor), and where along the map its edge starts, which places a fence's boards.
     */
    record Panel(int kind, float alongFrom, float alongTo, float upFrom, float upTo, int startsAt) {}

    /** The side of a square of the grid, in tiles. */
    static final float CELL = 0.01f;
    /** How wide a board is, in tiles: the shader's BOARD_WIDE (PipelineShaders), so a board breaks between the joints drawn. */
    static final float BOARD_WIDE = 0.15f;
    /** A board left with less than this share of its width beside a hole breaks right across there. */
    static final float SNAP = 0.45f;
    /** How many squares of the grid a broken board's end runs on past the break, at most, and how wide each splinter of it is. */
    static final int SPLINTERED = 6, SPLINTER_FROM = 2, SPLINTER_TO = 4;
    /** Of the boards a hole goes through, the share split on up and down the grain, and how long a split runs, in squares. */
    static final float SPLITS = 0.5f;
    static final int SPLIT_SHORTEST = 3, SPLIT_LONGEST = 16;
    /** Plasterboard left narrower than this between holes crumbles, in squares. */
    static final int BRIDGE = 3;
    /** The most squares the grid takes up a thing: a few levels. */
    private static final int MOST_ROWS = 1024;

    private static final byte SOLID = 0, HOLE = 1, BROKEN = 2;

    /** What goes besides the holes: rectangles, each the four corners along and up wound anticlockwise. */
    static final class Pieces {
        static final Pieces NONE = new Pieces(null, new float[0][]);
        final Panel panel;
        final float[][] rectangles;

        Pieces(Panel panel, float[][] rectangles) {
            this.panel = panel;
            this.rectangles = rectangles;
        }

        /** Whether (along, up) is in what went. */
        boolean inside(float along, float up) {
            for (float[] r : rectangles) {
                if (along >= r[0] && along <= r[2] && up >= r[1] && up <= r[5]) {
                    return true;
                }
            }
            return false;
        }
    }

    /** What was last worked out for each list of holes, by its edge: a list is never changed, only replaced. */
    private static final Map<float[], Pieces[]> worked = new WeakHashMap<>();

    /** What goes of the thing as its holes on its north or west edge leave it. */
    static Pieces of(float[] entries, boolean north, Panel panel) {
        if (panel == null || entries == null) {
            return Pieces.NONE;
        }
        int edge = north ? 1 : 0;
        synchronized (worked) {
            Pieces[] known = worked.get(entries);
            if (known != null && known[edge] != null && panel.equals(known[edge].panel)) {
                return known[edge];
            }
        }
        Pieces made = work(entries, north, panel);
        synchronized (worked) {
            worked.computeIfAbsent(entries, any -> new Pieces[2])[edge] = made;
        }
        return made;
    }

    static Pieces work(float[] entries, boolean north, Panel panel) {
        int across = Math.max(1, (int) Math.ceil((panel.alongTo() - panel.alongFrom()) / CELL - 1e-4f));
        int rows = Math.min(MOST_ROWS, Math.max(1, (int) Math.ceil((panel.upTo() - panel.upFrom()) * ViewCamera.LEVEL_HEIGHT / CELL - 1e-4f)));
        byte[] grid = new byte[across * rows];
        boolean holed = false;
        for (int at = 0; at < entries.length; at += BlastHoles.FLOATS) {
            if ((entries[at + BlastHoles.NORTH] != 0f) != north) {
                continue;
            }
            float[] outline = BlastHoles.outlineOf(entries, at);
            if (outline != null) {
                holed |= fill(grid, across, rows, panel, outline);
            }
        }
        if (!holed) {
            return new Pieces(panel, new float[0][]);
        }
        if (panel.kind() == BOARDS) {
            breakBoards(grid, across, rows, panel);
        } else {
            crumble(grid, across, rows);
            fallFromRim(grid, across, rows);
        }
        return new Pieces(panel, cover(grid, across, rows, panel));
    }

    /** Marks the squares whose middles are in the outline (along, and up in tiles) as holed: whether any were. */
    private static boolean fill(byte[] grid, int across, int rows, Panel panel, float[] outline) {
        float lowA = Float.POSITIVE_INFINITY, highA = Float.NEGATIVE_INFINITY, lowU = Float.POSITIVE_INFINITY, highU = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < outline.length; i += 2) {
            lowA = Math.min(lowA, outline[i]);
            highA = Math.max(highA, outline[i]);
            lowU = Math.min(lowU, outline[i + 1]);
            highU = Math.max(highU, outline[i + 1]);
        }
        float footTiles = panel.upFrom() * ViewCamera.LEVEL_HEIGHT;
        int i0 = Math.max(0, (int) Math.floor((lowA - panel.alongFrom()) / CELL)), i1 = Math.min(across - 1, (int) Math.floor((highA - panel.alongFrom()) / CELL));
        int j0 = Math.max(0, (int) Math.floor((lowU - footTiles) / CELL)), j1 = Math.min(rows - 1, (int) Math.floor((highU - footTiles) / CELL));
        boolean any = false;
        for (int j = j0; j <= j1; j++) {
            float up = footTiles + (j + 0.5f) * CELL;
            for (int i = i0; i <= i1; i++) {
                if (MeshCut.inside(outline, panel.alongFrom() + (i + 0.5f) * CELL, up)) {
                    grid[j * across + i] = HOLE;
                    any = true;
                }
            }
        }
        return any;
    }

    /** The board a column of the grid is in, counted along the map, as the shader counts them. */
    private static int boardOf(Panel panel, int column) {
        return (int) Math.floor((panel.startsAt() + panel.alongFrom() + (column + 0.5f) * CELL) / BOARD_WIDE);
    }

    private static void breakBoards(byte[] grid, int across, int rows, Panel panel) {
        int first = 0;
        while (first < across) {
            int board = boardOf(panel, first), last = first;
            while (last + 1 < across && boardOf(panel, last + 1) == board) {
                last++;
            }
            breakBoard(grid, across, rows, first, last, board);
            first = last + 1;
        }
    }

    /** Breaks one board, the columns first to last, across where it is shot most of the way through, splits it, and lets fall what is held at neither end. */
    private static void breakBoard(byte[] grid, int across, int rows, int first, int last, int board) {
        int wide = last - first + 1, top = -1, foot = -1;
        boolean[] snapped = new boolean[rows];
        for (int j = 0; j < rows; j++) {
            int holed = 0;
            for (int i = first; i <= last; i++) {
                holed += grid[j * across + i] == HOLE ? 1 : 0;
            }
            if (holed > 0) {
                top = j;
                foot = foot < 0 ? j : foot;
                snapped[j] = wide - holed < SNAP * wide;
            }
        }
        if (top < 0) {
            return;
        }
        // Split first: a split starts from the hole's own squares, which a break across then takes.
        split(grid, across, rows, first, last, top, true, board);
        split(grid, across, rows, first, last, foot, false, board);
        for (int j = 0; j < rows; j++) {
            if (!snapped[j] || j > 0 && snapped[j - 1]) {
                continue;
            }
            int end = j;
            while (end + 1 < rows && snapped[end + 1]) {
                end++;
            }
            splinter(grid, across, rows, first, last, j, end, board);
        }
        fallFromEnds(grid, across, rows, first, last);
    }

    /** Breaks the board right across over the rows from to to, each splinter of it running on a little further up and down. */
    private static void splinter(byte[] grid, int across, int rows, int first, int last, int from, int to, int board) {
        int seed = board * 7919 + from;
        int i = first, k = 0;
        while (i <= last) {
            int wide = SPLINTER_FROM + (int) ((SPLINTER_TO - SPLINTER_FROM + 1) * share(seed, 3 * k));
            int down = (int) ((SPLINTERED + 1) * share(seed, 3 * k + 1)), up = (int) ((SPLINTERED + 1) * share(seed, 3 * k + 2));
            for (int c = i; c <= Math.min(last, i + wide - 1); c++) {
                // The hole's squares go too: a square the hole only grazes at its middle would leave a sliver of board standing between the outline and the break.
                for (int j = Math.max(0, from - down); j <= Math.min(rows - 1, to + up); j++) {
                    grid[j * across + c] = BROKEN;
                }
            }
            i += wide;
            k++;
        }
    }

    /** Some of the time, splits the board on along its grain from the end of its hole, up from the top or down from the foot. */
    private static void split(byte[] grid, int across, int rows, int first, int last, int row, boolean up, int board) {
        int seed = board * 104729 + row * 31 + (up ? 1 : 0);
        if (share(seed, 0) >= SPLITS) {
            return;
        }
        int column = -1;
        // The split starts where the hole reaches that end: of the columns holed in its last row, the one the seed picks.
        int holed = 0;
        for (int i = first; i <= last; i++) {
            holed += grid[row * across + i] == HOLE ? 1 : 0;
        }
        int pick = (int) (holed * share(seed, 1));
        for (int i = first; i <= last && column < 0; i++) {
            if (grid[row * across + i] == HOLE && pick-- == 0) {
                column = i;
            }
        }
        int length = SPLIT_SHORTEST + (int) ((SPLIT_LONGEST - SPLIT_SHORTEST + 1) * share(seed, 2));
        for (int step = 1; step <= length; step++) {
            int j = up ? row + step : row - step;
            if (j < 0 || j >= rows) {
                break;
            }
            if (grid[j * across + column] == SOLID) {
                grid[j * across + column] = BROKEN;
            }
        }
    }

    /** Lets fall what of the board is joined to neither its foot nor its top. */
    private static void fallFromEnds(byte[] grid, int across, int rows, int first, int last) {
        boolean[] held = new boolean[grid.length];
        int[] queue = new int[(last - first + 1) * rows];
        int head = 0, tail = 0;
        for (int i = first; i <= last; i++) {
            for (int j : new int[] {0, rows - 1}) {
                int at = j * across + i;
                if (grid[at] == SOLID && !held[at]) {
                    held[at] = true;
                    queue[tail++] = at;
                }
            }
        }
        while (head < tail) {
            int at = queue[head++], i = at % across, j = at / across;
            tail = reach(grid, held, queue, tail, i > first ? at - 1 : -1);
            tail = reach(grid, held, queue, tail, i < last ? at + 1 : -1);
            tail = reach(grid, held, queue, tail, j > 0 ? at - across : -1);
            tail = reach(grid, held, queue, tail, j < rows - 1 ? at + across : -1);
        }
        for (int j = 0; j < rows; j++) {
            for (int i = first; i <= last; i++) {
                int at = j * across + i;
                if (grid[at] == SOLID && !held[at]) {
                    grid[at] = BROKEN;
                }
            }
        }
    }

    private static int reach(byte[] grid, boolean[] held, int[] queue, int tail, int at) {
        if (at >= 0 && grid[at] == SOLID && !held[at]) {
            held[at] = true;
            queue[tail++] = at;
        }
        return tail;
    }

    /** Crumbles what is left narrower than BRIDGE between two holes, across or up. */
    private static void crumble(byte[] grid, int across, int rows) {
        byte[] was = grid.clone();
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < across; i++) {
                if (was[j * across + i] != SOLID) {
                    continue;
                }
                boolean narrow = holeWithin(was, across, rows, i, j, -1, 0) && holeWithin(was, across, rows, i, j, 1, 0)
                        || holeWithin(was, across, rows, i, j, 0, -1) && holeWithin(was, across, rows, i, j, 0, 1);
                if (narrow) {
                    grid[j * across + i] = BROKEN;
                }
            }
        }
    }

    private static boolean holeWithin(byte[] grid, int across, int rows, int i, int j, int di, int dj) {
        for (int step = 1; step <= BRIDGE; step++) {
            int x = i + di * step, y = j + dj * step;
            if (x < 0 || y < 0 || x >= across || y >= rows) {
                return false;
            }
            if (grid[y * across + x] == HOLE) {
                return true;
            }
        }
        return false;
    }

    /** Lets fall what of a sheet is no longer joined to its edge. */
    private static void fallFromRim(byte[] grid, int across, int rows) {
        boolean[] held = new boolean[grid.length];
        int[] queue = new int[grid.length];
        int head = 0, tail = 0;
        for (int at = 0; at < grid.length; at++) {
            int i = at % across, j = at / across;
            if ((i == 0 || j == 0 || i == across - 1 || j == rows - 1) && grid[at] == SOLID) {
                held[at] = true;
                queue[tail++] = at;
            }
        }
        while (head < tail) {
            int at = queue[head++], i = at % across, j = at / across;
            tail = reach(grid, held, queue, tail, i > 0 ? at - 1 : -1);
            tail = reach(grid, held, queue, tail, i < across - 1 ? at + 1 : -1);
            tail = reach(grid, held, queue, tail, j > 0 ? at - across : -1);
            tail = reach(grid, held, queue, tail, j < rows - 1 ? at + across : -1);
        }
        for (int at = 0; at < grid.length; at++) {
            if (grid[at] == SOLID && !held[at]) {
                grid[at] = BROKEN;
            }
        }
    }

    /**
     * Rectangles that take every broken square and keep every solid one. A hole's squares beside a
     * broken one go too, so no sliver of what fell is left in a square the hole's outline crosses.
     */
    private static float[][] cover(byte[] grid, int across, int rows, Panel panel) {
        boolean[] take = new boolean[grid.length], covered = new boolean[grid.length];
        for (int at = 0; at < grid.length; at++) {
            if (grid[at] != BROKEN) {
                continue;
            }
            take[at] = true;
            int i = at % across, j = at / across;
            for (int n : new int[] {i > 0 ? at - 1 : -1, i < across - 1 ? at + 1 : -1, j > 0 ? at - across : -1, j < rows - 1 ? at + across : -1}) {
                if (n >= 0 && grid[n] == HOLE) {
                    take[n] = true;
                }
            }
        }
        ArrayList<float[]> out = new ArrayList<>();
        float footTiles = panel.upFrom() * ViewCamera.LEVEL_HEIGHT;
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < across; i++) {
                int at = j * across + i;
                if (!take[at] || covered[at]) {
                    continue;
                }
                int right = i;
                while (right + 1 < across && take[j * across + right + 1] && !covered[j * across + right + 1]) {
                    right++;
                }
                int high = j;
                while (high + 1 < rows && rowTaken(take, covered, across, high + 1, i, right)) {
                    high++;
                }
                for (int y = j; y <= high; y++) {
                    for (int x = i; x <= right; x++) {
                        covered[y * across + x] = true;
                    }
                }
                float a0 = panel.alongFrom() + i * CELL, a1 = panel.alongFrom() + (right + 1) * CELL;
                float u0 = (footTiles + j * CELL) / ViewCamera.LEVEL_HEIGHT, u1 = (footTiles + (high + 1) * CELL) / ViewCamera.LEVEL_HEIGHT;
                out.add(new float[] {a0, u0, a1, u0, a1, u1, a0, u1});
            }
        }
        return out.toArray(new float[0][]);
    }

    private static boolean rowTaken(boolean[] take, boolean[] covered, int across, int row, int from, int to) {
        for (int x = from; x <= to; x++) {
            int at = row * across + x;
            if (!take[at] || covered[at]) {
                return false;
            }
        }
        return true;
    }

    /** A number in [0, 1) from a seed and an index, the same every time. */
    private static float share(int seed, int index) {
        return Math.min(0.99999f, (MeshCut.noise(seed, index) + 1f) * 0.5f);
    }
}
