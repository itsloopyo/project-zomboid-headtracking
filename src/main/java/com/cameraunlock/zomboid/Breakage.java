package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * What else goes when holes are shot through a fence, a wall or a door's leaf (BlastHoles): what
 * the holes leave with nothing to hold it, and what they leave too weak to stand. Worked out on a
 * grid of CELL squares in the plane of the thing from its holes alone, the same on every thread
 * and on every load, so the picture cuts it (MeshCut) and a shot goes through it (BlastHoles.through)
 * alike, and nothing more is saved.
 *
 * A wall goes on past its square into the next, and what holes cut free of it may be of both, so
 * what goes of a wall is worked out for a run of walls stood end to end along one line at once,
 * each a square long, and given back for each along its own square. Nothing is held where one of
 * the run meets the next: what is cut free of both falls out of both. Each square's holes are cut
 * in along its own square alone, as the picture cuts each square by its own, and a hole that
 * reaches over the join into the next is kept by both (BlastHoles.add).
 *
 * A fence of boards is boards stood side by side, each BOARD_WIDE across at the places along the
 * map where the picture draws their joints (timber in the world's shader), and each held at its foot
 * and its top, by the rails it is nailed to. A board shot most of the way through at some height
 * breaks across there, its two ends splintered raggedly along the grain, and a hole splits some of
 * the boards it went through on up or down the grain. A length of board then held at neither end
 * falls out. A wall's plasterboard or a door's leaf is one sheet, held where the panel says (a
 * wall at its foot, its top and the ends of its run, a leaf where it hangs from its hinges) and by
 * the frames of the openings through it, a doorway's or a window's: what is left narrower than
 * BRIDGE between two holes crumbles, and what is no longer joined to anything that holds it falls
 * out.
 *
 * What goes is given as rectangles of the grid, in the plane's own measure: along in tiles and up
 * in levels, as the holes are. Any thread.
 */
final class Breakage {
    private Breakage() {}

    /** Kinds of thing: a fence of upright boards, or one sheet. */
    static final int BOARDS = 0, SHEET = 1;
    /** The sides of a sheet that may hold it: where it starts along, where it ends, its foot and its top. */
    static final int HELD_START = 1, HELD_END = 2, HELD_FOOT = 4, HELD_TOP = 8, HELD_ALL = HELD_START | HELD_END | HELD_FOOT | HELD_TOP;
    /** No openings through a thing. */
    static final float[] NO_FRAMES = new float[0];

    /**
     * What is shot through: its kind; how far along it reaches, in tiles from the corner of the first
     * square of its run, and how high, in levels over its floor; where along the map its run starts,
     * which places a fence's boards; which of a sheet's sides hold it (HELD_START and the rest); and the
     * frames of the openings through it, which hold the sheet round them, four numbers each: where along
     * the run each starts and ends, and its foot and its head.
     */
    record Panel(int kind, float alongFrom, float alongTo, float upFrom, float upTo, int startsAt, int held, float[] frames) {
        @Override
        public boolean equals(Object other) {
            return other instanceof Panel p && kind == p.kind && alongFrom == p.alongFrom && alongTo == p.alongTo && upFrom == p.upFrom
                    && upTo == p.upTo && startsAt == p.startsAt && held == p.held && Arrays.equals(frames, p.frames);
        }

        @Override
        public int hashCode() {
            int h = kind;
            h = h * 31 + Float.hashCode(alongFrom);
            h = h * 31 + Float.hashCode(alongTo);
            h = h * 31 + Float.hashCode(upFrom);
            h = h * 31 + Float.hashCode(upTo);
            h = h * 31 + startsAt;
            h = h * 31 + held;
            return h * 31 + Arrays.hashCode(frames);
        }
    }

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

    /** What goes of one thing besides its holes: rectangles, each the four corners along and up wound anticlockwise. */
    static final class Pieces {
        static final Pieces NONE = new Pieces(new float[0][]);
        final float[][] rectangles;

        Pieces(float[][] rectangles) {
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

    /** What was worked out for a run: each thing's list of holes as it was then, for its edge and its panel. */
    private record Worked(float[][] entries, boolean north, Panel panel, Pieces[] pieces) {
        boolean isFor(float[][] others, boolean onNorth, Panel on) {
            if (onNorth != north || others.length != entries.length || !on.equals(panel)) {
                return false;
            }
            for (int k = 0; k < entries.length; k++) {
                // A list is never changed, only replaced: the same list is the same holes.
                if (others[k] != entries[k]) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * The runs last worked out, the latest first. Each holds the lists it was worked from, so they are
     * kept to a few: those of the things being looked at and built.
     */
    private static final ArrayList<Worked> worked = new ArrayList<>();
    private static final int MOST_WORKED = 64;

    /** What goes of one thing as its holes on its north or west edge leave it. */
    static Pieces of(float[] entries, boolean north, Panel panel) {
        return of(new float[][] {entries}, north, panel)[0];
    }

    /**
     * What goes of each of a run of things stood end to end along one line, a square each, as their holes
     * on their north or west edges leave them: entries[k] are the k'th's holes and what goes of it is
     * given, along its own square both. The panel spans the run: for a run of more than one from 0
     * along to the count of them, and for a run of one the part of its square it fills, as a door's leaf does.
     */
    static Pieces[] of(float[][] entries, boolean north, Panel panel) {
        synchronized (worked) {
            for (int i = 0; i < worked.size(); i++) {
                Worked known = worked.get(i);
                if (known.isFor(entries, north, panel)) {
                    worked.add(0, worked.remove(i));
                    return known.pieces;
                }
            }
        }
        Pieces[] made = work(entries, north, panel);
        synchronized (worked) {
            worked.add(0, new Worked(entries.clone(), north, panel, made));
            if (worked.size() > MOST_WORKED) {
                worked.remove(worked.size() - 1);
            }
        }
        return made;
    }

    static Pieces[] work(float[][] entries, boolean north, Panel panel) {
        int across = Math.max(1, (int) Math.ceil((panel.alongTo() - panel.alongFrom()) / CELL - 1e-4f));
        int rows = Math.min(MOST_ROWS, Math.max(1, (int) Math.ceil((panel.upTo() - panel.upFrom()) * ViewCamera.LEVEL_HEIGHT / CELL - 1e-4f)));
        byte[] grid = new byte[across * rows];
        // The columns of each thing's own square: a run of one may cover only part of its square, as a door's leaf does.
        int[] first = new int[entries.length], end = new int[entries.length];
        for (int k = 0; k < entries.length; k++) {
            first[k] = entries.length == 1 ? 0 : Math.round(k / CELL);
            end[k] = entries.length == 1 ? across : Math.min(across, Math.round((k + 1) / CELL));
        }
        boolean holed = false;
        for (int k = 0; k < entries.length; k++) {
            float[] own = entries[k];
            for (int at = 0; at < own.length; at += BlastHoles.FLOATS) {
                if ((own[at + BlastHoles.NORTH] != 0f) != north) {
                    continue;
                }
                float[] outline = BlastHoles.outlineOf(own, at);
                if (outline != null) {
                    // The k'th thing's square starts k tiles along the run.
                    for (int i = 0; i < outline.length; i += 2) {
                        outline[i] += k;
                    }
                    // A hole is cut in its own thing alone, as the picture cuts it: the next one's is whole beside it.
                    holed |= fill(grid, across, rows, panel, outline, first[k], end[k]);
                }
            }
        }
        Pieces[] out = new Pieces[entries.length];
        if (!holed) {
            Arrays.fill(out, Pieces.NONE);
            return out;
        }
        if (panel.kind() == BOARDS) {
            breakBoards(grid, across, rows, panel);
        } else {
            crumble(grid, across, rows);
            fallFromHolds(grid, across, rows, panel);
        }
        boolean[] take = taken(grid, across, rows);
        for (int k = 0; k < entries.length; k++) {
            out[k] = new Pieces(cover(take, across, rows, first[k], end[k], panel, k));
        }
        return out;
    }

    /** Marks the squares from column columnFrom to before columnEnd whose middles are in the outline (along the run, and up in tiles) as holed: whether any were. */
    private static boolean fill(byte[] grid, int across, int rows, Panel panel, float[] outline, int columnFrom, int columnEnd) {
        float lowA = Float.POSITIVE_INFINITY, highA = Float.NEGATIVE_INFINITY, lowU = Float.POSITIVE_INFINITY, highU = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < outline.length; i += 2) {
            lowA = Math.min(lowA, outline[i]);
            highA = Math.max(highA, outline[i]);
            lowU = Math.min(lowU, outline[i + 1]);
            highU = Math.max(highU, outline[i + 1]);
        }
        float footTiles = panel.upFrom() * ViewCamera.LEVEL_HEIGHT;
        int i0 = Math.max(columnFrom, (int) Math.floor((lowA - panel.alongFrom()) / CELL)), i1 = Math.min(columnEnd - 1, (int) Math.floor((highA - panel.alongFrom()) / CELL));
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
        // One for every board: each board's search keeps to its own columns, so none sees another's marks.
        boolean[] held = new boolean[grid.length];
        int first = 0;
        while (first < across) {
            int board = boardOf(panel, first), last = first;
            while (last + 1 < across && boardOf(panel, last + 1) == board) {
                last++;
            }
            breakBoard(grid, across, rows, first, last, board, held);
            first = last + 1;
        }
    }

    /** Breaks one board, the columns first to last, across where it is shot most of the way through, splits it, and lets fall what is held at neither end. */
    private static void breakBoard(byte[] grid, int across, int rows, int first, int last, int board, boolean[] held) {
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
        fallFromEnds(grid, across, rows, first, last, held);
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
    private static void fallFromEnds(byte[] grid, int across, int rows, int first, int last, boolean[] held) {
        int[] queue = new int[(last - first + 1) * rows];
        int head = 0, tail = 0;
        for (int i = first; i <= last; i++) {
            tail = reach(grid, held, queue, tail, i);
            tail = reach(grid, held, queue, tail, (rows - 1) * across + i);
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

    /** Lets fall what of a sheet is no longer joined to a side of it that holds it, nor to the frame of an opening through it. */
    private static void fallFromHolds(byte[] grid, int across, int rows, Panel panel) {
        boolean[] held = new boolean[grid.length];
        int[] queue = new int[grid.length];
        int head = 0, tail = 0;
        int sides = panel.held();
        float[] frames = panel.frames();
        float footTiles = panel.upFrom() * ViewCamera.LEVEL_HEIGHT;
        for (int at = 0; at < grid.length; at++) {
            int i = at % across, j = at / across;
            if (grid[at] != SOLID) {
                continue;
            }
            boolean holds = i == 0 && (sides & HELD_START) != 0 || i == across - 1 && (sides & HELD_END) != 0
                    || j == 0 && (sides & HELD_FOOT) != 0 || j == rows - 1 && (sides & HELD_TOP) != 0;
            if (!holds && frames.length > 0) {
                // The opening's own squares hold: no hole is shot through an opening, and its frame holds the sheet it meets.
                float along = panel.alongFrom() + (i + 0.5f) * CELL, up = (footTiles + (j + 0.5f) * CELL) / ViewCamera.LEVEL_HEIGHT;
                for (int f = 0; f < frames.length && !holds; f += 4) {
                    holds = along >= frames[f] && along <= frames[f + 1] && up >= frames[f + 2] && up <= frames[f + 3];
                }
            }
            if (holds) {
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

    /** The squares that go: every broken one, and a hole's beside a broken one, so no sliver of what fell is left in a square the hole's outline crosses. */
    private static boolean[] taken(byte[] grid, int across, int rows) {
        boolean[] take = new boolean[grid.length];
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
        return take;
    }

    /**
     * Rectangles that cover every square taken in the columns from to before to and no other, along the
     * k'th thing's own square: the run's place along less k.
     */
    private static float[][] cover(boolean[] take, int across, int rows, int from, int to, Panel panel, int k) {
        boolean[] covered = new boolean[take.length];
        ArrayList<float[]> out = new ArrayList<>();
        float footTiles = panel.upFrom() * ViewCamera.LEVEL_HEIGHT;
        for (int j = 0; j < rows; j++) {
            for (int i = from; i < to; i++) {
                int at = j * across + i;
                if (!take[at] || covered[at]) {
                    continue;
                }
                int right = i;
                while (right + 1 < to && take[j * across + right + 1] && !covered[j * across + right + 1]) {
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
                float a0 = panel.alongFrom() + i * CELL - k, a1 = panel.alongFrom() + (right + 1) * CELL - k;
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
