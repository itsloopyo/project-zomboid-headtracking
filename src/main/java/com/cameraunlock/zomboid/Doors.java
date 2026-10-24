package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * What a door's leaf is, read off its sprite.
 *
 * A door is measured as a window is (see Windows): laid flat on its edge, its outline and the
 * lights of glass in it come out as rectangles, and its colour is what most of it is painted
 * in. What is left is how the wood between is worked, which the sprite shows only as a few
 * darker lines: two pairs of them where the edges of two rows of panels run up the leaf, or
 * one every board's width where it is boards nailed to ledges, or none on a door that is flush.
 * The picture builds the leaf from that, panels and boards standing out as they do.
 */
final class Doors {
    static final int FLUSH = 0, PANELLED = 1, PLANKED = 2;
    /** A line of the leaf darker than this share of what its lines usually are is a line drawn on it. */
    private static final float DRAWN_LINE = 0.93f;
    /** How far in from the leaf's edges the lines are looked for, across and down, in dots, and the fewest dots of leaf a line of it has to hold to count. */
    private static final int IN_ACROSS = 2, IN_DOWN = 4, FEWEST = 10;
    /** The levels between which a handle is, and how far in from each end of the leaf, as a share of its width, the handle is looked for. */
    private static final float HANDLE_FOOT = 0.3f, HANDLE_HEAD = 0.5f, HANDLE_WITHIN = 0.3f;
    /**
     * A dot is of the handle where it is off the leaf's colour by more than this share of the
     * most any dot there is. The handle is the dots so off that touch the most off one.
     */
    private static final float HANDLE_PART = 0.5f;
    /**
     * A handle is a lever where its longest row is this many times as long as the handle is
     * high, or LEVER_ROW times as long as its usual row: a lever's arm alone, or an arm on a
     * plate standing up the leaf. A knob is about as wide as it is high.
     */
    static final float LEVER_LONG = 1.8f, LEVER_ROW = 2f;
    /** How far in from its end of the leaf a handle may reach, as a share of the leaf's width: a lever's arm runs on past where the handle is looked for. */
    private static final float HANDLE_REACH = 0.5f;

    /** One door: its outline and lights as a window's, and how its leaf is worked. */
    static final class Shape {
        Windows.Shape leaf;
        int kind;
        /** For boards: how wide each is, as a share of the wall's length. */
        float board;
        /** Whether the handle is nearer the end of the leaf than its start. */
        boolean handleAtEnd;
        /** Whether the handle is a lever, not a knob. */
        boolean lever;
        /** The handle as it was measured: its longest row, how many rows high, its usual row, in dots. */
        int[] handle;
    }

    private record Asked(boolean north, float originU, float originV, float scale, float left, float top, float width, float height) {}

    private static final Shape NONE = new Shape();
    private static final ConcurrentHashMap<Texture, Asked> asked = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Texture, Shape> known = new ConcurrentHashMap<>();

    private Doors() {}

    /**
     * The door the sprite paints on the north or west edge of its square. Null until a frame
     * has been drawn since it was first asked for, and for a sprite that is no leaf with four
     * straight sides. A sprite is read for the first edge it is asked for on.
     */
    static Shape of(Texture texture, SpriteGeometry.Frame frame, boolean north) {
        Shape shape = known.get(texture);
        if (shape != null) {
            return shape == NONE || shape.leaf.north != north ? null : shape;
        }
        if (!asked.containsKey(texture) && asked.putIfAbsent(texture, new Asked(north, frame.originU, frame.originV, frame.scale, frame.left, frame.top, frame.width, frame.height)) == null) {
            SpriteColours.readAgain(texture);
        }
        return null;
    }

    /** The door the sprite was read as, whichever edge it was read for: null for one not read yet or that is no leaf with four straight sides. */
    static Shape known(Texture texture) {
        Shape shape = known.get(texture);
        return shape == NONE ? null : shape;
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
        int across = Windows.across(wanted.scale), high = Windows.high(wanted.scale);
        int[] at = new int[across * high];
        byte[] dots = Windows.laid(sheet, width, left, top, right, bottom, wanted.north, false, wanted.originU, wanted.originV, wanted.scale,
                wanted.left, wanted.top, wanted.width, wanted.height, at);
        Windows.Shape leaf = Windows.measure(dots, across, high, true);
        if (FirstPerson.TRACE) {
            StringBuilder seen = new StringBuilder("door " + texture.getName() + (wanted.north ? " north" : " west") + " origin " + wanted.originU + "," + wanted.originV
                    + " frame " + wanted.left + "," + wanted.top + " " + wanted.width + "x" + wanted.height + (leaf == null ? " is no leaf" : " leaf " + leaf.start + ".." + leaf.end + " high " + leaf.foot + ".." + leaf.head));
            for (int row = 0; row < high; row += 8) {
                seen.append((char) 10);
                for (int column = 0; column < across; column++) {
                    seen.append(dots[row * across + column] == Windows.FRAME ? '#' : dots[row * across + column] == Windows.GLASS ? '.' : ' ');
                }
            }
            Log.info(seen.toString());
        }
        if (leaf == null) {
            known.put(texture, NONE);
            return;
        }
        Windows.colour(sheet, dots, at, leaf);
        leaf.north = wanted.north;
        leaf.originU = wanted.originU;
        leaf.originV = wanted.originV;
        leaf.scale = wanted.scale;
        float[] bright = new float[dots.length];
        for (int i = 0; i < dots.length; i++) {
            bright[i] = dots[i] == Windows.FRAME
                    ? (299 * (sheet.get(at[i]) & 0xff) + 587 * (sheet.get(at[i] + 1) & 0xff) + 114 * (sheet.get(at[i] + 2) & 0xff)) / 255000f : -1f;
        }
        Shape shape = worked(leaf, bright, across, high);
        if (FirstPerson.TRACE) {
            Log.info("door " + texture.getName() + " reads as " + (shape.kind == PANELLED ? "panelled" : shape.kind == PLANKED ? "planked" : "flush")
                    + ", handle at its " + (shape.handleAtEnd ? "end" : "start") + ", a " + (shape.lever ? "lever" : "knob")
                    + (shape.handle == null ? "" : " (longest row " + shape.handle[0] + ", " + shape.handle[1] + " rows, usual row " + shape.handle[2] + ")")
                    + ", " + leaf.panes.length / 5 + " lights");
        }
        known.put(texture, shape);
    }

    private static final java.util.Set<Texture> pairsTold = ConcurrentHashMap.newKeySet();

    /** Says once, under FirstPerson.TRACE, which shut sprite an open one was read from. */
    static void tellPair(Texture open, Texture shut) {
        if (FirstPerson.TRACE && pairsTold.add(open)) {
            Log.info("door " + open.getName() + " open is read from " + shut.getName() + " shut");
        }
    }

    /**
     * How the leaf is worked: bright holds across numbers a row, the top row first, how bright
     * each dot of the leaf is from 0 to 1 and below 0 for what is no leaf.
     */
    static Shape worked(Windows.Shape leaf, float[] bright, int across, int high) {
        Shape shape = new Shape();
        shape.leaf = leaf;
        int c0 = Math.round(leaf.start * across), c1 = Math.round(leaf.end * across);
        int r0 = Math.round((1f - leaf.head) * high), r1 = Math.round((1f - leaf.foot) * high);
        int columns = c1 - c0 - 2 * IN_ACROSS;
        if (columns < 4 || r1 - r0 <= 2 * IN_DOWN + FEWEST) {
            return shape;
        }
        float[] lines = new float[columns];
        float[] sorted = new float[columns];
        int held = 0;
        for (int column = 0; column < columns; column++) {
            float sum = 0f;
            int count = 0;
            for (int row = r0 + IN_DOWN; row < r1 - IN_DOWN; row++) {
                float dot = bright[row * across + c0 + IN_ACROSS + column];
                if (dot >= 0f) {
                    sum += dot;
                    count++;
                }
            }
            lines[column] = count >= FEWEST ? sum / count : -1f;
            if (lines[column] >= 0f) {
                sorted[held++] = lines[column];
            }
        }
        if (held < 4) {
            return shape;
        }
        Arrays.sort(sorted, 0, held);
        float usual = sorted[held / 2];
        int drawn = 0, widest = 0, first = -1, last = -1;
        for (int column = 0, run = 0; column <= columns; column++) {
            boolean dark = column < columns && lines[column] >= 0f && lines[column] < DRAWN_LINE * usual;
            if (dark) {
                run++;
            } else if (run > 0) {
                drawn++;
                widest = Math.max(widest, run);
                first = first < 0 ? column - run : first;
                last = column - run;
                run = 0;
            }
        }
        boolean glazed = leaf.panes.length > 0;
        if (drawn == 4 || glazed && drawn >= 2) {
            shape.kind = PANELLED;
        } else if (!glazed && (drawn >= 5 || drawn >= 2 && widest <= 1)) {
            shape.kind = PLANKED;
            shape.board = (float) (last - first) / ((drawn - 1) * across);
        }
        // The handle is what is least the leaf's colour at a hand's height, near one end of it.
        int from = Math.round((1f - HANDLE_HEAD) * high), to = Math.round((1f - HANDLE_FOOT) * high), within = Math.round((c1 - c0) * HANDLE_WITHIN);
        float[] odd = new float[2];
        for (int end = 0; end < 2; end++) {
            for (int row = Math.max(from, r0); row < Math.min(to, r1); row++) {
                for (int column = 0; column < within; column++) {
                    float dot = bright[row * across + (end == 0 ? c0 + column : c1 - 1 - column)];
                    if (dot >= 0f) {
                        odd[end] = Math.max(odd[end], Math.abs(dot - usual));
                    }
                }
            }
        }
        shape.handleAtEnd = odd[1] > odd[0];
        int handleFrom = Math.max(from, r0), handleTo = Math.min(to, r1), reach = Math.round((c1 - c0) * HANDLE_REACH);
        shape.handle = handle(bright, across, handleFrom, handleTo, shape.handleAtEnd ? c1 - within : c0, shape.handleAtEnd ? c1 : c0 + within,
                shape.handleAtEnd ? c1 - reach : c0, shape.handleAtEnd ? c1 : c0 + reach, usual);
        shape.lever = isLever(shape.handle);
        return shape;
    }

    /** Whether a handle measured as handle gives it is a lever: see LEVER_LONG. */
    static boolean isLever(int[] handle) {
        return handle[1] > 0 && (handle[0] >= LEVER_LONG * handle[1] || handle[0] >= LEVER_ROW * handle[2]);
    }

    /**
     * The handle the sprite paints in rows from to to of bright, found in columns c0 to c1 and
     * reaching as far as columns g0 to g1: its longest row, how many rows high it is and its
     * usual row, in dots, all 0 where there is none. It is the dots off the leaf's colour usual
     * by at least HANDLE_PART of the most off one in c0 to c1 that touch that one, side by
     * side or one over another.
     */
    static int[] handle(float[] bright, int across, int from, int to, int c0, int c1, int g0, int g1, float usual) {
        int rows = to - from, columns = g1 - g0;
        if (rows <= 0 || c1 <= c0 || columns <= 0) {
            return new int[3];
        }
        float most = 0f;
        int seed = -1;
        for (int row = from; row < to; row++) {
            for (int column = c0; column < c1; column++) {
                float dot = bright[row * across + column];
                if (dot >= 0f && Math.abs(dot - usual) > most) {
                    most = Math.abs(dot - usual);
                    seed = row * across + column;
                }
            }
        }
        if (seed < 0) {
            return new int[3];
        }
        boolean[] part = new boolean[bright.length];
        int[] queue = new int[rows * columns];
        int head = 0, tail = 0;
        part[seed] = true;
        queue[tail++] = seed;
        while (head < tail) {
            int at = queue[head++], row = at / across, column = at % across;
            for (int step = 0; step < 4; step++) {
                int r = row + (step == 0 ? -1 : step == 1 ? 1 : 0), c = column + (step == 2 ? -1 : step == 3 ? 1 : 0);
                if (r < from || r >= to || c < g0 || c >= g1) {
                    continue;
                }
                int next = r * across + c;
                float dot = bright[next];
                if (!part[next] && dot >= 0f && Math.abs(dot - usual) >= HANDLE_PART * most) {
                    part[next] = true;
                    queue[tail++] = next;
                }
            }
        }
        int high = 0, longest = 0;
        int[] lengths = new int[rows];
        for (int row = from; row < to; row++) {
            int length = 0;
            for (int column = g0; column < g1; column++) {
                if (part[row * across + column]) {
                    length++;
                }
            }
            if (length > 0) {
                lengths[high++] = length;
                longest = Math.max(longest, length);
            }
        }
        Arrays.sort(lengths, 0, high);
        return new int[] {longest, high, lengths[high / 2]};
    }

    /**
     * An open door as its shut sprite shows it. The game draws an open leaf half turned, and
     * read off that picture it is a flush leaf: its panels, colour and handle are read off the
     * sprite it shows shut, and laid on the outline of the open one, which is where the leaf
     * stands. A leaf turns on hinges at the doorway's corner, so open it has them at its start
     * (the end at that corner) and its handle at its end; the shut leaf's working is turned end
     * for end where its handle was read at its start.
     */
    static Shape swung(Shape open, Shape shut) {
        Windows.Shape from = shut.leaf, to = open.leaf, leaf = new Windows.Shape();
        leaf.north = to.north;
        leaf.originU = to.originU;
        leaf.originV = to.originV;
        leaf.scale = to.scale;
        leaf.start = to.start;
        leaf.end = to.end;
        leaf.foot = to.foot;
        leaf.head = to.head;
        leaf.red = from.red;
        leaf.green = from.green;
        leaf.blue = from.blue;
        float along = (to.end - to.start) / (from.end - from.start), up = (to.head - to.foot) / (from.head - from.foot);
        boolean turned = !shut.handleAtEnd;
        leaf.startWide = (turned ? from.endWide : from.startWide) * along;
        leaf.endWide = (turned ? from.startWide : from.endWide) * along;
        leaf.footHigh = from.footHigh * up;
        leaf.headHigh = from.headHigh * up;
        leaf.bars = laidOn(from.bars, 4, from, to, along, up, turned);
        leaf.panes = laidOn(from.panes, 5, from, to, along, up, turned);
        Shape made = new Shape();
        made.leaf = leaf;
        made.kind = shut.kind;
        made.board = shut.board * along;
        made.handleAtEnd = true;
        made.lever = shut.lever;
        return made;
    }

    /** Pieces of a leaf, size numbers each, from start to end along it then foot to head, moved off the leaf from onto the leaf to. */
    private static float[] laidOn(float[] pieces, int size, Windows.Shape from, Windows.Shape to, float along, float up, boolean turned) {
        float[] laid = pieces.clone();
        for (int at = 0; at < laid.length; at += size) {
            float a = (pieces[at] - from.start) * along, b = (pieces[at + 1] - from.start) * along;
            laid[at] = turned ? to.end - b : to.start + a;
            laid[at + 1] = turned ? to.end - a : to.start + b;
            laid[at + 2] = to.foot + (pieces[at + 2] - from.foot) * up;
            laid[at + 3] = to.foot + (pieces[at + 3] - from.foot) * up;
        }
        return laid;
    }
}
