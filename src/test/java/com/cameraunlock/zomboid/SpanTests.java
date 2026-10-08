package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Tests of what a length of overhead wire hangs between, on made-up maps, and of the curve it hangs in. */
final class SpanTests {
    private SpanTests() {}

    static void run() {
        Checks.run("everyLengthOfASpanHangsBetweenTheSameTwoInsulators", SpanTests::everyLengthOfASpanHangsBetweenTheSameTwoInsulators);
        Checks.run("lengthThatPassesOverAnInsulatorIsHeldThere", SpanTests::lengthThatPassesOverAnInsulatorIsHeldThere);
        Checks.run("wiresOfOneRunKeepToTheirOwnInsulators", SpanTests::wiresOfOneRunKeepToTheirOwnInsulators);
        Checks.run("runThatTurnsAtAPoleIsMadeFastToItFromBothWays", SpanTests::runThatTurnsAtAPoleIsMadeFastToItFromBothWays);
        Checks.run("runThatStopsInTheAirIsHeldWhereItStops", SpanTests::runThatStopsInTheAirIsHeldWhereItStops);
        Checks.run("spanTheMapIsNotLoadedToTheEndOfIsTakenForAUsualOne", SpanTests::spanTheMapIsNotLoadedToTheEndOfIsTakenForAUsualOne);
        Checks.run("wireHangsLowestInTheMiddleAndNotAtAllAtItsEnds", SpanTests::wireHangsLowestInTheMiddleAndNotAtAllAtItsEnds);
        Checks.run("lengthsOfASpanSwingAsOne", SpanTests::lengthsOfASpanSwingAsOne);
    }

    /** A map of one level: the lines on each square, the poles, and how far along x it is loaded. */
    private static final class Map implements Spans.Hung {
        final HashMap<Long, List<float[]>> lines = new HashMap<>();
        final HashMap<Long, float[]> poles = new HashMap<>();
        int loadedTo = Integer.MAX_VALUE;

        private static long key(int x, int y) {
            return (long) x << 32 | y & 0xFFFFFFFFL;
        }

        void line(int x, int y, float x0, float y0, float x1, float y1) {
            lines.computeIfAbsent(key(x, y), none -> new ArrayList<>()).add(new float[] {x0, y0, x1, y1});
        }

        /** A pole on a square with its axis at the square's middle and insulators the given ways off it. */
        void pole(int x, int y, float... offs) {
            float[] pins = new float[offs.length * 2];
            for (int i = 0; i < offs.length; i += 2) {
                pins[i * 2] = x + 0.5f + offs[i];
                pins[i * 2 + 1] = y + 0.5f + offs[i + 1];
                pins[i * 2 + 2] = x + 0.5f;
                pins[i * 2 + 3] = y + 0.5f;
            }
            poles.put(key(x, y), pins);
        }

        @Override
        public int lines(int x, int y, float[] out) {
            if (x >= loadedTo) {
                return -1;
            }
            int count = 0;
            for (float[] line : lines.getOrDefault(key(x, y), List.of())) {
                System.arraycopy(line, 0, out, count, 4);
                count += 4;
            }
            return count;
        }

        @Override
        public int pins(int x, int y, float[] out, int at) {
            float[] pins = poles.get(key(x, y));
            if (pins == null || x >= loadedTo) {
                return 0;
            }
            System.arraycopy(pins, 0, out, at, pins.length);
            return pins.length;
        }
    }

    /**
     * A wire along x at the height y on the squares from one to another, a length a square as
     * the game's sprites have it: each reaches a little into the squares either side, and
     * every third is drawn from its far end back.
     */
    private static void wire(Map map, int from, int to, int row, float y) {
        for (int x = from; x <= to; x++) {
            if (x % 3 == 0) {
                map.line(x, row, x + 0.62f, y, x - 0.4f, y);
            } else {
                map.line(x, row, x - 0.4f, y, x + 0.62f, y);
            }
        }
    }

    private static float[] piecesOf(Map map, int x, int y, int which) {
        float[] line = map.lines.get(Map.key(x, y)).get(which);
        return new Spans(map).find(x, y, line[0], line[1], line[2], line[3]);
    }

    /** The two ends of a piece's span, the one at the lower x first: where each is, where its land is taken and its kind. */
    private static float[] ends(float[] pieces, int piece) {
        boolean swap = pieces[piece + Spans.FROM] > pieces[piece + Spans.TO];
        float[] ends = new float[10];
        System.arraycopy(pieces, piece + (swap ? Spans.TO : Spans.FROM), ends, 0, 5);
        System.arraycopy(pieces, piece + (swap ? Spans.FROM : Spans.TO), ends, 5, 5);
        return ends;
    }

    private static void everyLengthOfASpanHangsBetweenTheSameTwoInsulators() {
        Map map = new Map();
        map.pole(10, 5, 0f, -0.8f);
        map.pole(36, 5, 0f, -0.8f);
        wire(map, 10, 36, 4, 4.7f);
        for (int x = 11; x <= 35; x++) {
            float[] pieces = piecesOf(map, x, 4, 0);
            check(pieces.length == Spans.PIECE, "a length between two poles is one piece, on the square at " + x + ": " + pieces.length / Spans.PIECE);
            float[] ends = ends(pieces, 0);
            check(near(ends[0], 10.5f) && near(ends[1], 4.7f) && ends[4] == Spans.FAST && near(ends[2], 10.5f) && near(ends[3], 5.5f),
                    "it hangs from the insulator of the pole behind it, whose land is taken at the pole's axis, on the square at " + x + ": " + ends[0] + ", " + ends[1]);
            check(near(ends[5], 36.5f) && near(ends[6], 4.7f) && ends[9] == Spans.FAST && near(ends[7], 36.5f) && near(ends[8], 5.5f),
                    "and to the insulator of the pole ahead, on the square at " + x + ": " + ends[5] + ", " + ends[6]);
            check(near(Spans.span(pieces, 0), 26f), "its span is from pole to pole: " + Spans.span(pieces, 0));
        }
        // The length next to a pole is made fast to it: its end is moved onto the insulator.
        float[] first = piecesOf(map, 11, 4, 0);
        check(near(Math.min(first[0], first[2]), 10.5f), "the end of the length beside a pole is on its insulator: " + first[0] + " to " + first[2]);
    }

    private static void lengthThatPassesOverAnInsulatorIsHeldThere() {
        Map map = new Map();
        map.pole(10, 5, 0f, -0.8f);
        map.pole(23, 5, -0.4f, -0.8f);
        map.pole(36, 5, 0f, -0.8f);
        wire(map, 10, 36, 4, 4.7f);
        // The pole in the middle has its insulator at 23.1, half way along the length on its square: further than a hand from both its ends.
        float[] pieces = piecesOf(map, 23, 4, 0);
        check(pieces.length == 2 * Spans.PIECE, "a length that passes over an insulator is two pieces: " + pieces.length / Spans.PIECE);
        for (int piece = 0; piece < pieces.length; piece += Spans.PIECE) {
            float[] ends = ends(pieces, piece);
            boolean behind = Math.max(pieces[piece], pieces[piece + 2]) < 23.2f;
            check(near(ends[0], behind ? 10.5f : 23.1f) && near(ends[5], behind ? 23.1f : 36.5f) && ends[4] == Spans.FAST && ends[9] == Spans.FAST,
                    "each piece hangs between the pole it passes over and the next: " + ends[0] + " to " + ends[5]);
        }
        float[] before = ends(piecesOf(map, 16, 4, 0), 0), after = ends(piecesOf(map, 30, 4, 0), 0);
        check(near(before[0], 10.5f) && near(before[5], 23.1f), "a length before that pole hangs to it and no further: " + before[5]);
        check(near(after[0], 23.1f) && near(after[5], 36.5f), "and one after it hangs from it: " + after[0]);
    }

    private static void wiresOfOneRunKeepToTheirOwnInsulators() {
        Map map = new Map();
        // Two wires 0.36 apart, the nearest two of a run ever are, each with its insulator on both poles.
        map.pole(10, 5, 0f, -0.8f, 0f, -0.44f);
        map.pole(30, 5, 0f, -0.8f, 0f, -0.44f);
        wire(map, 10, 30, 4, 4.7f);
        wire(map, 10, 30, 4, 5.06f);
        for (int x = 11; x <= 29; x++) {
            for (int which = 0; which < 2; which++) {
                float[] pieces = piecesOf(map, x, 4, which);
                float y = which == 0 ? 4.7f : 5.06f;
                check(pieces.length == Spans.PIECE && near(pieces[Spans.FROM + 1], y) && near(pieces[Spans.TO + 1], y) && near(pieces[1], y) && near(pieces[3], y),
                        "a wire is made fast to its own insulators and not to those of the wire beside it, on the square at " + x);
            }
        }
    }

    private static void runThatTurnsAtAPoleIsMadeFastToItFromBothWays() {
        Map map = new Map();
        map.pole(10, 5, 0f, -0.8f);
        map.pole(30, 5, 0f, -0.8f);
        map.pole(10, 25, -0.3f, 0f);
        // The wire along x stops a quarter of a tile short of the insulator it turns at, and so does the one that leaves it along y.
        for (int x = 11; x <= 30; x++) {
            map.line(x, 4, x == 11 ? 10.75f : x - 0.4f, 4.7f, x + 0.62f, 4.7f);
        }
        for (int y = 5; y <= 25; y++) {
            map.line(10, y, 10.5f, y == 5 ? 4.95f : y - 0.4f, 10.5f, y + 0.62f);
        }
        float[] along = piecesOf(map, 11, 4, 0), down = piecesOf(map, 10, 5, 0);
        check(near(along[0], 10.5f) && near(along[1], 4.7f), "the wire that comes to the pole is made fast to its insulator: " + along[0] + ", " + along[1]);
        check(near(down[0], 10.5f) && near(down[1], 4.7f), "and so is the one that leaves it another way: " + down[0] + ", " + down[1]);
        float[] far = ends(piecesOf(map, 10, 15, 0), 0);
        check(far[4] == Spans.FAST && far[9] == Spans.FAST && near(Spans.span(piecesOf(map, 10, 15, 0), 0), (float) Math.hypot(0.3, 20.8)),
                "the run that leaves the pole hangs from it to the next pole that way");
    }

    private static void runThatStopsInTheAirIsHeldWhereItStops() {
        Map map = new Map();
        map.pole(10, 5, 0f, -0.8f);
        wire(map, 10, 22, 4, 4.7f);
        float[] ends = ends(piecesOf(map, 15, 4, 0), 0);
        check(ends[4] == Spans.FAST && near(ends[0], 10.5f), "a run with a pole at one end hangs from it");
        check(ends[9] == Spans.LOOSE && near(ends[5], 22.62f) && near(ends[7], 22.62f) && near(ends[8], 4.7f),
                "and is held where its last length stops, over the land there: " + ends[5]);
        // The length beyond the pole, where no run goes on: it is held at the pole and where it stops.
        float[] beyond = piecesOf(map, 10, 4, 0);
        check(beyond.length == Spans.PIECE && near(Spans.span(beyond, 0), 0.9f), "the stub of wire drawn past a run's last pole hangs between its end and the pole: " + Spans.span(beyond, 0));
    }

    private static void spanTheMapIsNotLoadedToTheEndOfIsTakenForAUsualOne() {
        Map map = new Map();
        map.pole(10, 5, 0f, -0.8f);
        map.pole(42, 5, 0f, -0.8f);
        wire(map, 10, 42, 4, 4.7f);
        map.loadedTo = 20;
        float[] pieces = piecesOf(map, 15, 4, 0);
        float[] ends = ends(pieces, 0);
        check(Spans.wanting(pieces) && ends[9] == Spans.WANTING, "a span the map ends in the middle of is not known at that end");
        check(near(ends[5], 10.5f + Spans.USUAL) && near(ends[6], 4.7f), "a pole is taken to stand the usual span on: " + ends[5]);
        check(near(ends[7], 19.62f) && near(ends[8], 4.7f), "on land as high as the last that is loaded: " + ends[7]);
        float[] next = ends(piecesOf(map, 18, 4, 0), 0);
        check(near(next[5], ends[5]) && near(next[7], ends[7]), "and every length of it takes the same one");
        // With the map loaded past where a usual pole would stand and still no pole: the wire is held where the map ends.
        map.loadedTo = 40;
        float[] longer = ends(piecesOf(map, 15, 4, 0), 0);
        check(longer[9] == Spans.WANTING && near(longer[5], 39.62f), "a span longer than usual is held where the map ends: " + longer[5]);
        map.loadedTo = Integer.MAX_VALUE;
        float[] whole = piecesOf(map, 15, 4, 0);
        check(!Spans.wanting(whole) && near(Spans.span(whole, 0), 32f), "and with the map loaded it hangs to its pole: " + Spans.span(whole, 0));
    }

    private static void wireHangsLowestInTheMiddleAndNotAtAllAtItsEnds() {
        check(near(Spans.sag(26f), 0.78f), "a wire sags by three hundredths of its span: " + Spans.sag(26f));
        check(Spans.sag(10f) < Spans.sag(20f) && Spans.sag(200f) == Spans.MOST_SAG, "more the longer the span, and never more than the most");
        check(Spans.MOST_SAG < Wires.UP * ViewCamera.LEVEL_HEIGHT - 0.4f, "the most leaves a wire clear of the floor of the level it hangs on");
        float sag = Spans.sag(26f);
        check(Spans.hangs(0f, sag) == 0f && Spans.hangs(1f, sag) == 0f, "a wire does not hang at all at its insulators");
        check(near(Spans.hangs(0.5f, sag), sag), "and by the whole of its sag in the middle");
        float last = 0f;
        for (int step = 1; step <= 50; step++) {
            float hangs = Spans.hangs(step / 100f, sag);
            check(hangs > last && near(hangs, Spans.hangs(1f - step / 100f, sag)), "it falls all the way from an insulator to the middle, the same from both ends");
            last = hangs;
        }
        // The lengths of a span meet end to end: each takes its height from where between the two insulators its end is.
        Map map = new Map();
        map.pole(10, 5, 0f, -0.8f);
        map.pole(36, 5, 0f, -0.8f);
        wire(map, 10, 36, 4, 4.7f);
        float lowest = 0f;
        for (int x = 11; x < 35; x++) {
            float[] here = piecesOf(map, x, 4, 0), next = piecesOf(map, x + 1, 4, 0);
            float hereEnd = Math.max(here[0], here[2]), nextStart = Math.min(next[0], next[2]);
            float drop = Spans.hangs(Spans.along(here, 0, hereEnd, 4.7f), sag) - Spans.hangs(Spans.along(next, 0, nextStart, 4.7f), sag);
            check(Math.abs(drop) < 0.005f, "two lengths of a span meet at one height, at " + hereEnd + ": " + drop);
            lowest = Math.max(lowest, Spans.hangs(Spans.along(here, 0, hereEnd, 4.7f), sag));
        }
        check(lowest > sag - 0.01f, "and the span they make hangs by its sag: " + lowest);
    }

    private static void lengthsOfASpanSwingAsOne() {
        Map map = new Map();
        map.pole(10, 5, 0f, -0.8f, 0f, -0.44f);
        map.pole(36, 5, 0f, -0.8f, 0f, -0.44f);
        map.pole(62, 5, 0f, -0.8f, 0f, -0.44f);
        wire(map, 10, 62, 4, 4.7f);
        wire(map, 10, 62, 4, 5.06f);
        float swing = Spans.swing(piecesOf(map, 11, 4, 0), 0);
        check(swing >= 0f && swing < 1f, "where a span is in its swing is a share of one swing: " + swing);
        for (int x = 12; x <= 35; x++) {
            check(Spans.swing(piecesOf(map, x, 4, 0), 0) == swing, "every length of a span is at the same place in its swing, drawn from either end, at " + x);
        }
        float beside = Spans.swing(piecesOf(map, 20, 4, 1), 0), apart = Math.abs(beside - swing);
        check(Math.min(apart, 1f - apart) < 0.05f && beside != swing, "the wire beside it on the same poles swings nearly with it and not quite: " + swing + " and " + beside);
        float next = Spans.swing(piecesOf(map, 50, 4, 0), 0), nextApart = Math.abs(next - swing);
        check(Math.min(nextApart, 1f - nextApart) > 0.05f, "the next span of the run swings in its own time: " + swing + " and " + next);
        // The way across a wire is one way for a length read from either end, for every way a wire runs on the map.
        float[][] ways = {{1f, 0f}, {0f, 1f}, {0.7071f, 0.7071f}, {0.7071f, -0.7071f}, {0.9999f, 0.012f}, {0.9999f, -0.012f}, {0.012f, 0.9999f}, {-0.012f, 0.9999f}};
        for (float[] way : ways) {
            float one = Spans.acrossSign(-way[1], way[0]), other = Spans.acrossSign(way[1], -way[0]);
            check(one == -other && -way[1] * one == way[1] * other && way[0] * one == -way[0] * other,
                    "a length along " + way[0] + ", " + way[1] + " has the same way across it read from either end");
        }
        check(Spans.acrossSign(-0.012f, 0.9999f) == Spans.acrossSign(0.012f, 0.9999f) && Spans.acrossSign(0.7f, 0.72f) == Spans.acrossSign(0.72f, 0.7f),
                "and two lengths that run a hair apart have the same one");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 2e-3f;
    }
}
