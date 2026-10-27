package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        Checks.run("squareStackedWithMoreWireThanUsualIsFollowedLikeAnother", SpanTests::squareStackedWithMoreWireThanUsualIsFollowedLikeAnother);
        Checks.run("runThatStopsShortOfAnInsulatorItPointsAtIsMadeFastToIt", SpanTests::runThatStopsShortOfAnInsulatorItPointsAtIsMadeFastToIt);
        Checks.run("runDrawnALevelOffItsPolesHangsOnTheirLevel", SpanTests::runDrawnALevelOffItsPolesHangsOnTheirLevel);
        Checks.run("runThatEndsByAPoleOnMapNotLoadedIsNotLeftLoose", SpanTests::runThatEndsByAPoleOnMapNotLoadedIsNotLeftLoose);
        Checks.run("poleTopDrawnALevelUpStandsOnItsPole", SpanTests::poleTopDrawnALevelUpStandsOnItsPole);
        Checks.run("runWithASquareLeftOutIsFollowedOverIt", SpanTests::runWithASquareLeftOutIsFollowedOverIt);
        Checks.run("aPoleAtTheEdgeOfTheLoadedMapIsReadFromItsOwnSquare", SpanTests::aPoleAtTheEdgeOfTheLoadedMapIsReadFromItsOwnSquare);
        Checks.run("poleLengthsDrawnOffTheirFootStandOnIt", SpanTests::poleLengthsDrawnOffTheirFootStandOnIt);
        Checks.run("aRunHangsFromAPoleTheMapMayYetShowMoreOf", SpanTests::aRunHangsFromAPoleTheMapMayYetShowMoreOf);
        Checks.run("aTallPoleWhoseTopMayBeDrawnUpOntoMapNotLoadedWantsTheMap", SpanTests::aTallPoleWhoseTopMayBeDrawnUpOntoMapNotLoadedWantsTheMap);
        Checks.run("spansFoundTogetherInAFrameAreThoseFoundOneByOne", SpanTests::spansFoundTogetherInAFrameAreThoseFoundOneByOne);
        Checks.run("spansOfOneSquareOnDifferentLevelsAreKeptApart", SpanTests::spansOfOneSquareOnDifferentLevelsAreKeptApart);
    }

    /** Build 42's levels run past 15: the span found on level 17 of a square is neither the one on its level 1 nor the one on the next square's. */
    private static void spansOfOneSquareOnDifferentLevelsAreKeptApart() {
        Set<Long> keys = new HashSet<>();
        for (int z = 0; z <= 31; z++) {
            for (int line = 0; line <= 15; line++) {
                keys.add(WireBuilder.spanKey(12345, 6789, z, 135, line));
            }
        }
        Checks.check(keys.size() == 32 * 16, "every level and line of one square has a key of its own");
        Checks.check(WireBuilder.spanKey(12345, 6789, 1, 96, 0) != WireBuilder.spanKey(12345, 6789, 17, 96, 0), "levels 1 and 17 of one square get different keys");
        Checks.check(WireBuilder.spanKey(12345, 6789, 17, 96, 0) != WireBuilder.spanKey(12345, 6790, 1, 96, 0), "level 17 of a square is not level 1 of the square next to it");
        Checks.check(WireBuilder.spanKey(12345, 6789, 31, 255, 15) != WireBuilder.spanKey(12345, 6790, 0, 0, 0), "the top level, pole and line stay off the next square's key");
        boolean refused = false;
        try {
            WireBuilder.spanKey(12345, 6789, 1, 96, 16);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        Checks.check(refused, "a line index past 15 is refused");
        refused = false;
        try {
            WireBuilder.spanKey(12345, 6789, 1, 256, 0);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        Checks.check(refused, "a pole index past 255 is refused");
    }

    /**
     * Every length of every square of a map hung by one Spans in turn, as a frame's build hangs
     * them, in one order and in the other, comes to just what each hung by a Spans of its own
     * does: what one span found and kept for the frame is what the next would have found.
     */
    private static void spansFoundTogetherInAFrameAreThoseFoundOneByOne() {
        List<Map> maps = new ArrayList<>();
        Map straight = new Map();
        straight.pole(10, 5, 0f, -0.8f);
        straight.pole(23, 5, 0f, -0.8f);
        straight.pole(36, 5, 0f, -0.8f);
        wire(straight, 10, 36, 4, 4.7f);
        maps.add(straight);
        Map unloaded = new Map();
        unloaded.pole(10, 5, 0f, -0.8f);
        unloaded.pole(42, 5, 0f, -0.8f);
        wire(unloaded, 10, 42, 4, 4.7f);
        unloaded.loadedTo = 20;
        maps.add(unloaded);
        Map poleUnsettled = new Map();
        poleUnsettled.pole(10, 5, 0f, -0.8f);
        poleUnsettled.pole(23, 5, 0f, -0.8f);
        wire(poleUnsettled, 6, 30, 4, 4.7f);
        poleUnsettled.unsettled.add(Map.key(23, 5));
        maps.add(poleUnsettled);
        Map gap = new Map();
        gap.pole(10, 5, 0f, -0.8f);
        gap.pole(36, 5, 0f, -0.8f);
        wire(gap, 10, 19, 4, 4.7f);
        wire(gap, 21, 36, 4, 4.7f);
        maps.add(gap);
        Map loose = new Map();
        loose.pole(10, 5, 0f, -0.8f);
        wire(loose, 10, 24, 4, 4.7f);
        wire(loose, 3, 9, 4, 4.7f);
        loose.polesLoadedTo = 30;
        maps.add(loose);
        int compared = 0;
        boolean same = true;
        for (Map map : maps) {
            List<long[]> squares = new ArrayList<>();
            for (java.util.Map.Entry<Long, List<float[]>> square : map.lines.entrySet()) {
                for (int which = 0; which < square.getValue().size(); which++) {
                    squares.add(new long[] {square.getKey(), which});
                }
            }
            squares.sort((a, b) -> Long.compare(a[0], b[0]));
            for (int order = 0; order < 2; order++) {
                Spans shared = new Spans(map);
                for (int i = 0; i < squares.size(); i++) {
                    long[] at = squares.get(order == 0 ? i : squares.size() - 1 - i);
                    long key = at[0];
                    int z = (int) (key >>> 48), x = (int) (key >>> 24 & 0xFFFFFF), y = (int) (key & 0xFFFFFF);
                    float[] line = map.lines.get(key).get((int) at[1]);
                    float[] alone = new Spans(map).find(x, y, z, line[0], line[1], line[2], line[3]);
                    float[] together = shared.find(x, y, z, line[0], line[1], line[2], line[3]);
                    same &= java.util.Arrays.equals(alone, together);
                    compared++;
                }
            }
        }
        check(same && compared > 200, "every length hung with the others in a frame hangs as it does alone: " + compared + " compared");
    }

    /** The level the poles of the made-up maps stand their tops on, as the map's do. */
    private static final int LEVEL = 2;

    /** A made-up map: the lines on each square of each level, the poles' tops, and how far along x it is loaded, for the lines and for the poles. */
    private static final class Map implements Spans.Hung {
        final HashMap<Long, List<float[]>> lines = new HashMap<>();
        final HashMap<Long, float[]> poles = new HashMap<>();
        int loadedTo = Integer.MAX_VALUE, polesLoadedTo = Integer.MAX_VALUE;
        /** The squares whose pins the map may yet show more of, see Hung.settled. */
        final HashSet<Long> unsettled = new HashSet<>();

        private static long key(int x, int y) {
            return key(x, y, LEVEL);
        }

        private static long key(int x, int y, int z) {
            return (long) z << 48 | (long) (x & 0xFFFFFF) << 24 | y & 0xFFFFFF;
        }

        void line(int x, int y, float x0, float y0, float x1, float y1) {
            lineOn(x, y, LEVEL, x0, y0, x1, y1);
        }

        void lineOn(int x, int y, int z, float x0, float y0, float x1, float y1) {
            lines.computeIfAbsent(key(x, y, z), none -> new ArrayList<>()).add(new float[] {x0, y0, x1, y1});
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
        public int lines(int x, int y, int z, float[] out) {
            if (x >= loadedTo) {
                return -1;
            }
            int count = 0;
            for (float[] line : lines.getOrDefault(key(x, y, z), List.of())) {
                if (count + 4 <= out.length) {
                    System.arraycopy(line, 0, out, count, 4);
                }
                count += 4;
            }
            return count;
        }

        @Override
        public int pins(int x, int y, int z, float[] out, int at) {
            if (x >= loadedTo || x >= polesLoadedTo) {
                return -1;
            }
            float[] pins = poles.get(key(x, y, z));
            if (pins == null) {
                return 0;
            }
            System.arraycopy(pins, 0, out, at, pins.length);
            return pins.length;
        }

        @Override
        public boolean settled(int x, int y, int z) {
            return !unsettled.contains(key(x, y, z));
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
        return new Spans(map).find(x, y, LEVEL, line[0], line[1], line[2], line[3]);
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

    /** A map that stacks a square with more lengths than Spans.MOST_LINES holds: twenty copies of the one a run has there. */
    private static void squareStackedWithMoreWireThanUsualIsFollowedLikeAnother() {
        Map usual = new Map(), stacked = new Map();
        for (Map map : List.of(usual, stacked)) {
            map.pole(10, 5, 0f, -0.8f);
            map.pole(36, 5, 0f, -0.8f);
            wire(map, 10, 36, 4, 4.7f);
        }
        float[] length = stacked.lines.get(Map.key(20, 4)).get(0);
        for (int copy = 0; copy < 19; copy++) {
            stacked.line(20, 4, length[0], length[1], length[2], length[3]);
        }
        check(stacked.lines.get(Map.key(20, 4)).size() * 4 > Spans.MOST_LINES, "the stacked square holds more than usual");
        float[] ends = ends(piecesOf(stacked, 15, 4, 0), 0), usualEnds = ends(piecesOf(usual, 15, 4, 0), 0);
        check(java.util.Arrays.equals(ends, usualEnds), "a run over a stacked square hangs between the same insulators: "
                + java.util.Arrays.toString(ends) + " against " + java.util.Arrays.toString(usualEnds));
    }

    /** Lengths of wire a square each from (x, y) on, the way (wayX, wayY) of length one, each reaching a little into the next and keyed to the square its middle is over. */
    private static void run(Map map, int z, float x, float y, float wayX, float wayY, int count) {
        for (int k = 0; k < count; k++) {
            float x0 = x + wayX * k, y0 = y + wayY * k, x1 = x0 + wayX * 1.02f, y1 = y0 + wayY * 1.02f;
            map.lineOn((int) Math.floor((x0 + x1) / 2f), (int) Math.floor((y0 + y1) / 2f), z, x0, y0, x1, y1);
        }
    }

    /** The piece of a length that has an end nearest a point, and the span end there: where its length ends there, where its span ends and its kind. */
    private static float[] endNear(Map map, float x, float y, float wayX, float wayY) {
        int squareX = (int) Math.floor(x + wayX * 0.5f), squareY = (int) Math.floor(y + wayY * 0.5f);
        for (float[] line : map.lines.get(Map.key(squareX, squareY))) {
            if (Math.hypot(line[0] - x, line[1] - y) > 0.01) {
                continue;
            }
            float[] pieces = new Spans(map).find(squareX, squareY, LEVEL, line[0], line[1], line[2], line[3]);
            check(pieces.length == Spans.PIECE, "the length at " + x + ", " + y + " is one piece: " + pieces.length / Spans.PIECE);
            return new float[] {pieces[0], pieces[1], pieces[Spans.FROM], pieces[Spans.FROM + 1], pieces[Spans.FROM + Spans.END_KIND]};
        }
        throw new IllegalStateException("no length starts at " + x + ", " + y);
    }

    /**
     * The map's runs of wire that stop short of the arm they meet, where the arm hides the gap
     * in the game's view: three along the diagonal stop 0.5 to 1.38 short of an arm along x
     * (the pole at 10581, 9299, the numbers off its axis as the sheet's sprites give them),
     * and a run along x passes a pole with the square it should cross the arm on not drawn.
     */
    private static void runThatStopsShortOfAnInsulatorItPointsAtIsMadeFastToIt() {
        Map map = new Map();
        // Insulators of an arm along x, 0.157 off the axis to the south, as Poles.insulator puts them for a shaft 0.102 thin.
        map.pole(10, 5, -0.8f, 0.157f, -0.16f, 0.157f, 0.8f, 0.157f);
        float wayX = (float) Math.sqrt(0.5), wayY = -wayX;
        float[][] stops = {{10.601f, 4.608f}, {11.069f, 4.64f}, {11.601f, 5.233f}};
        float[] insulators = {9.7f, 10.34f, 11.3f};
        for (float[] stop : stops) {
            run(map, LEVEL, stop[0], stop[1], wayX, wayY, 20);
        }
        for (int wire = 0; wire < 3; wire++) {
            float[] end = endNear(map, stops[wire][0], stops[wire][1], wayX, wayY);
            check(end[4] == Spans.FAST && near(end[2], insulators[wire]) && near(end[3], 5.657f),
                    "the wire that stops " + Math.hypot(stops[wire][0] - insulators[wire], stops[wire][1] - 5.657f) + " short of its insulator is made fast to it: " + end[2] + ", " + end[3]);
            check(near(end[0], end[2]) && near(end[1], end[3]), "and its last length goes on to it: " + end[0] + ", " + end[1]);
        }
        // A length further from the pole hangs from the same insulator, and keeps its own end.
        float[] on = endNear(map, stops[1][0] + 3f * wayX, stops[1][1] + 3f * wayY, wayX, wayY);
        check(on[4] == Spans.FAST && near(on[2], 10.34f) && near(on[0], stops[1][0] + 3f * wayX), "a length further on hangs from it and stays where it is drawn: " + on[0] + ", " + on[2]);

        // A run along x over a pole whose arm lies along y, with no wire drawn on the square it crosses the arm on.
        Map passed = new Map();
        passed.pole(20, 5, 0.157f, -0.8f);
        passed.pole(46, 5, 0.157f, -0.8f);
        passed.pole(-6, 5, 0.157f, -0.8f);
        run(passed, LEVEL, -5.343f, 4.7f, 1f, 0f, 25);
        run(passed, LEVEL, 20.657f, 4.7f, 1f, 0f, 25);
        float[] last = new Spans(passed).find(19, 4, LEVEL, 18.657f, 4.7f, 19.677f, 4.7f);
        check(last.length == Spans.PIECE && last[Spans.TO + Spans.END_KIND] == Spans.FAST && near(last[Spans.TO], 20.657f) && near(last[2], 20.657f),
                "the last length before the gap is made fast to the insulator a square on and reaches it: " + java.util.Arrays.toString(last));
        float[] after = endNear(passed, 20.657f, 4.7f, 1f, 0f);
        check(after[4] == Spans.FAST && near(after[2], 20.657f), "and the run after the gap hangs from the same insulator: " + after[2]);
        // A run that stops in the air with no insulator before it stays loose.
        Map air = new Map();
        air.pole(10, 5, 0f, -0.8f);
        run(air, LEVEL, 10.5f, 4.7f, 1f, 0f, 10);
        air.pole(22, 5, 0f, -0.8f);
        float[] stopped = new Spans(air).find(15, 4, LEVEL, 15.5f, 4.7f, 16.52f, 4.7f);
        check(stopped[Spans.TO + Spans.END_KIND] == Spans.LOOSE, "a run that stops further than REACH short of the next pole is held where it stops: " + stopped[Spans.TO]);
    }

    /**
     * A run drawn on the level over or under its poles' tops and three squares on along x and
     * y, where the game's view draws it on the same place: the run by the pole at (10718, 9852)
     * in Riverside, whose last two squares are drawn on the poles' level and the rest on the
     * one over. And a run between a pole that stands on ground a level up and one that does
     * not, drawn on the level of the first: the run from (10678, 9723) to (10718, 9763).
     */
    private static void runDrawnALevelOffItsPolesHangsOnTheirLevel() {
        int on = Spans.LEVEL_SQUARES;
        float[] place = new float[3];
        for (int up = -1; up <= 1; up += 2) {
            Map map = new Map();
            map.pole(10, 5, 0f, -0.8f);
            map.pole(36, 5, 0f, -0.8f);
            for (int x = 10; x <= 36; x++) {
                int z = x >= 34 ? LEVEL : LEVEL + up, s = z == LEVEL ? 0 : on * up;
                map.lineOn(x + s, 4 + s, z, x - 0.4f + s, 4.7f + s, x + 0.62f + s, 4.7f + s);
            }
            for (int x = 12; x <= 33; x++) {
                int s = on * up;
                float[] pieces = new Spans(map).find(x + s, 4 + s, LEVEL + up, x - 0.4f + s, 4.7f + s, x + 0.62f + s, 4.7f + s);
                check(pieces.length == Spans.PIECE && pieces[Spans.FROM + Spans.END_KIND] == Spans.FAST && pieces[Spans.TO + Spans.END_KIND] == Spans.FAST
                        && pieces[Spans.FROM + Spans.END_LEVEL] == LEVEL && pieces[Spans.TO + Spans.END_LEVEL] == LEVEL,
                        "a length drawn on the level " + (up > 0 ? "over" : "under") + " its poles is made fast to their insulators, on theirs, at " + x);
                float[] ends = ends(pieces, 0);
                Spans.placed(pieces, 0, ends[0], ends[1], place);
                boolean fromPole = near(place[0], 10.5f) && near(place[1], 4.7f) && place[2] == LEVEL;
                Spans.placed(pieces, 0, ends[5], ends[6], place);
                check(fromPole && near(place[0], 36.5f) && near(place[1], 4.7f) && place[2] == LEVEL, "and hangs between them, at " + x + ": to " + place[0] + ", " + place[1] + " on " + place[2]);
                Spans.placed(pieces, 0, Math.min(pieces[0], pieces[2]), pieces[1], place);
                check(near(place[0], x - 0.4f) && near(place[1], 4.7f) && place[2] == LEVEL, "where it is drawn on their level, at " + x + ": " + place[0] + ", " + place[1] + " on " + place[2]);
                check(near(Spans.span(pieces, 0), 26f), "its span is from pole to pole: " + Spans.span(pieces, 0));
            }
        }
        // A run drawn on the level over poles that stand their tops there stays there.
        Map high = new Map();
        high.poles.put(Map.key(10, 5, LEVEL + 1), new float[] {10.5f, 4.7f, 10.5f, 5.5f});
        high.poles.put(Map.key(36, 5, LEVEL + 1), new float[] {36.5f, 4.7f, 36.5f, 5.5f});
        for (int x = 10; x <= 36; x++) {
            high.lineOn(x, 4, LEVEL + 1, x - 0.4f, 4.7f, x + 0.62f, 4.7f);
        }
        float[] pieces = new Spans(high).find(20, 4, LEVEL + 1, 19.6f, 4.7f, 20.62f, 4.7f);
        Spans.placed(pieces, 0, 19.6f, 4.7f, place);
        check(ends(pieces, 0)[4] == Spans.FAST && ends(pieces, 0)[9] == Spans.FAST && near(place[0], 19.6f) && place[2] == LEVEL + 1, "a run on the level of its poles hangs on it");
        // From a pole whose top is a level up to one whose top is not, the run drawn on the level of the first.
        Map step = new Map();
        step.poles.put(Map.key(10, 5, LEVEL + 1), new float[] {10.5f, 4.7f, 10.5f, 5.5f});
        step.pole(33, 2, 0f, -0.8f);
        for (int x = 10; x <= 36; x++) {
            step.lineOn(x, 4, LEVEL + 1, x - 0.4f, 4.7f, x + 0.62f, 4.7f);
        }
        float[] stepped = new Spans(step).find(23, 4, LEVEL + 1, 22.6f, 4.7f, 23.62f, 4.7f);
        float[] stepEnds = ends(stepped, 0);
        check(stepEnds[4] == Spans.FAST && stepEnds[9] == Spans.FAST && stepped[Spans.TO + Spans.END_LEVEL] == LEVEL && near(stepEnds[5], 36.5f),
                "a run drawn on to where a pole on the level under draws its insulator is made fast to it: " + stepEnds[5] + " " + stepEnds[9]);
        Spans.placed(stepped, 0, stepEnds[5], stepEnds[6], place);
        boolean atPole = near(place[0], 33.5f) && near(place[1], 1.7f) && place[2] == LEVEL;
        Spans.placed(stepped, 0, 23.5f, 4.7f, place);
        check(atPole && near(place[2], LEVEL + 0.5f) && near(place[0], 22f) && near(place[1], 3.2f),
                "and goes down to it a level over its span, where the view draws it: half way it is half a level down and half of three squares back: " + place[0] + ", " + place[1] + " on " + place[2]);
        check(near(Spans.span(stepped, 0), (float) Math.hypot(23.0, 3.0)), "its span is from where one pole stands to where the other does: " + Spans.span(stepped, 0));
    }

    /** The runs along x at (10886, 9460) and (11901, 7175), each with one square drawn with no wire, and one along the diagonal at (11545, 6814). */
    private static void runWithASquareLeftOutIsFollowedOverIt() {
        Map map = new Map();
        map.pole(10, 5, 0f, -0.8f);
        map.pole(36, 5, 0f, -0.8f);
        wire(map, 10, 19, 4, 4.7f);
        wire(map, 21, 36, 4, 4.7f);
        float[] before = new Spans(map).find(19, 4, LEVEL, 18.6f, 4.7f, 19.62f, 4.7f), after = new Spans(map).find(21, 4, LEVEL, 21.62f, 4.7f, 20.6f, 4.7f);
        float[] beforeEnds = ends(before, 0), afterEnds = ends(after, 0);
        check(beforeEnds[4] == Spans.FAST && beforeEnds[9] == Spans.FAST && near(beforeEnds[5], 36.5f), "the length before the gap hangs to the pole past it: " + beforeEnds[5] + " " + beforeEnds[9]);
        check(afterEnds[4] == Spans.FAST && near(afterEnds[0], 10.5f), "and the length after it from the pole before it: " + afterEnds[0] + " " + afterEnds[4]);
        check(near(Math.max(before[0], before[2]), 20.6f) && near(Math.min(after[0], after[2]), 19.62f),
                "and each reaches over the gap to the other: " + Math.max(before[0], before[2]) + ", " + Math.min(after[0], after[2]));
        float[] middle = ends(piecesOf(map, 15, 4, 0), 0);
        check(near(middle[5], 36.5f), "every length of the run hangs between the same two poles: " + middle[5]);
        // Along the diagonal the gap is a square's diagonal.
        Map diagonal = new Map();
        float way = (float) Math.sqrt(0.5);
        run(diagonal, LEVEL, 10f, 10f, way, way, 8);
        run(diagonal, LEVEL, 10f + way * 9.4f, 10f + way * 9.4f, way, way, 8);
        float[] last = new Spans(diagonal).find(15, 15, LEVEL, 10f + way * 7f, 10f + way * 7f, 10f + way * 8.02f, 10f + way * 8.02f);
        check(near(last[2], 10f + way * 9.4f) && near(Spans.span(last, 0), 17.42f), "a run along the diagonal is followed over a square left out of it: " + last[2] + ", " + Spans.span(last, 0));
    }

    private static void runThatEndsByAPoleOnMapNotLoadedIsNotLeftLoose() {
        Map map = new Map();
        map.pole(10, 5, 0f, -0.8f);
        map.pole(36, 5, 0f, -0.8f);
        wire(map, 10, 35, 4, 4.7f);
        map.polesLoadedTo = 36;
        float[] ends = ends(piecesOf(map, 20, 4, 0), 0);
        check(ends[9] == Spans.WANTING, "a run that stops beside a pole the map has not loaded is not known at that end, and is found again when it is: " + ends[9]);
        map.polesLoadedTo = Integer.MAX_VALUE;
        ends = ends(piecesOf(map, 20, 4, 0), 0);
        check(ends[9] == Spans.FAST && near(ends[5], 36.5f), "with the pole loaded the run hangs from it: " + ends[5]);
    }

    /** The poles at (10718, 9763) and (10730, 9864) in Riverside: two lengths on their square, and the top with its arm drawn on the level over the third and three squares on. */
    private static void poleTopDrawnALevelUpStandsOnItsPole() {
        HashMap<Long, String> drawn = new HashMap<>();
        Poles.Drawn map = (x, y, z) -> drawn.get(Map.key(x, y, z));
        int on = Spans.LEVEL_SQUARES;
        drawn.put(Map.key(10, 5, 0), "appliances_com_01_88");
        drawn.put(Map.key(10, 5, 1), "appliances_com_01_89");
        drawn.put(Map.key(10 + on, 5 + on, 3), "appliances_com_01_93");
        check("appliances_com_01_93".equals(Poles.standsOn(map, 10, 5, 2, 0)), "the top drawn a level up and three squares on stands on its pole: " + Poles.standsOn(map, 10, 5, 2, 0));
        check(Poles.drawnUp(map, 10 + on, 5 + on, 3, 0) && Poles.standsOn(map, 10 + on, 5 + on, 3, 0) == null, "and not where it is drawn");
        check("appliances_com_01_89".equals(Poles.standsOn(map, 10, 5, 1, 0)) && !Poles.drawnUp(map, 10, 5, 1, 0), "the lengths under it stand where they are drawn");
        // A pole that stands its foot on the level over: its lengths are its own.
        drawn.put(Map.key(30, 5, 1), "appliances_com_01_88");
        drawn.put(Map.key(30, 5, 2), "appliances_com_01_89");
        drawn.put(Map.key(30, 5, 3), "appliances_com_01_93");
        drawn.put(Map.key(30 - on, 5 - on, 0), "appliances_com_01_88");
        drawn.put(Map.key(30 - on, 5 - on, 1), "appliances_com_01_89");
        for (int z = 1; z <= 3; z++) {
            check(!Poles.drawnUp(map, 30, 5, z, -1) && drawn.get(Map.key(30, 5, z)).equals(Poles.standsOn(map, 30, 5, z, -1)),
                    "a pole with its foot on the level over a shorter one three squares back keeps its length on level " + z);
        }
        check(Poles.standsOn(map, 30 - on, 5 - on, 2, -1) == null, "and the shorter one takes nothing of it");
        // A pole whole to its arm, and three squares on another whose foot stands a level over that arm.
        drawn.put(Map.key(50, 5, 0), "appliances_com_01_88");
        drawn.put(Map.key(50, 5, 1), "appliances_com_01_89");
        drawn.put(Map.key(50, 5, 2), "appliances_com_01_93");
        drawn.put(Map.key(50 + on, 5 + on, 3), "appliances_com_01_88");
        check(!Poles.drawnUp(map, 50 + on, 5 + on, 3, 0) && Poles.standsOn(map, 50, 5, 3, 0) == null, "the foot of a pole over the top of a whole one is no part of it");
    }

    /**
     * Poles in Muldraugh and Ekron whose upper lengths the map draws off their foot, which were built as tops in the
     * air: (10675, 9692) with its second length and its top both a level up and three squares on, (468, 9812) with its
     * upper two lengths a level up and one square on, (4504, 6069) with its top so, and (10640, 9623), all three lengths a level up over the street.
     */
    private static void poleLengthsDrawnOffTheirFootStandOnIt() {
        HashMap<Long, String> drawn = new HashMap<>();
        Poles.Drawn map = (x, y, z) -> drawn.get(Map.key(x, y, z));
        int on = Spans.LEVEL_SQUARES;
        drawn.put(Map.key(10, 5, 0), "appliances_com_01_88");
        drawn.put(Map.key(10 + on, 5 + on, 2), "appliances_com_01_89");
        drawn.put(Map.key(10 + on, 5 + on, 3), "appliances_com_01_93");
        check(Poles.drawnUp(map, 10 + on, 5 + on, 3, 0), "a top over a length drawn up is drawn up with it");
        check("appliances_com_01_89".equals(Poles.standsOn(map, 10, 5, 1, 0)) && "appliances_com_01_93".equals(Poles.standsOn(map, 10, 5, 2, 0)),
                "and the pole stands whole on its foot: " + Poles.standsOn(map, 10, 5, 2, 0));
        check(Poles.standsOn(map, 10 + on, 5 + on, 2, 0) == null && Poles.standsOn(map, 10 + on, 5 + on, 3, 0) == null, "and nothing stands where they are drawn");

        drawn.put(Map.key(30, 5, 0), "appliances_com_01_88");
        drawn.put(Map.key(31, 6, 1), "appliances_com_01_89");
        drawn.put(Map.key(31, 6, 2), "appliances_com_01_93");
        check(Poles.setBack(map, 30, 5, 0) && Poles.drawnFrom(map, 31, 6, 0, 0) == Poles.BACK, "a foot one square back from the rest of its pole is set back under it");
        check("appliances_com_01_88".equals(Poles.standsOn(map, 31, 6, 0, 0)) && Poles.standsOn(map, 30, 5, 0, 0) == null, "and stands there, not where it is drawn");
        check("appliances_com_01_93".equals(Poles.standsOn(map, 31, 6, 2, 0)), "and the top over it is its own square's");
        check(!Poles.setBack(map, 31, 6, 2) && !Poles.setBack(map, 31, 6, 1), "the upper lengths are not set back");
        Poles.Loaded toThirty = (x, y) -> x <= 30, all = (x, y) -> true;
        check(Poles.unsettled(map, toThirty, 30, 5, 0, 0), "a bare length with nothing over it may be set back under map not loaded");
        check(!Poles.unsettled(map, all, 30, 5, 0, 0), "and is known once it is loaded");
        check(Poles.unsettled(map, (x, y) -> x >= 31, 31, 6, 1, 0), "the length it holds up may stand on it from map not loaded");

        // (4504, 6069): two lengths on their square, the top a level up and one square on.
        drawn.put(Map.key(70, 5, 0), "appliances_com_01_94");
        drawn.put(Map.key(70, 5, 1), "appliances_com_01_95");
        drawn.put(Map.key(71, 6, 2), "appliances_com_01_92");
        check(Poles.setBack(map, 70, 5, 0) && Poles.setBack(map, 70, 5, 1), "the lengths under a length set back are set back with it");
        check("appliances_com_01_94".equals(Poles.standsOn(map, 71, 6, 0, 0)) && "appliances_com_01_95".equals(Poles.standsOn(map, 71, 6, 1, 0))
                && "appliances_com_01_92".equals(Poles.standsOn(map, 71, 6, 2, 0)), "and the pole stands whole under its top");

        drawn.put(Map.key(50, 5, 1), "appliances_com_01_88");
        drawn.put(Map.key(50, 5, 2), "appliances_com_01_89");
        drawn.put(Map.key(50, 5, 3), "appliances_com_01_93");
        check(!Poles.drawnUp(map, 50, 5, 1, 0) && Poles.standsOn(map, 50, 5, 0, 0) == null, "a pole drawn whole a level up has no foot under it");
        Poles.Floored street = (x, y, z) -> z == 0;
        check(Poles.footLevel(street, 50, 5, 1) == 0, "and stands on the street under it: " + Poles.footLevel(street, 50, 5, 1));
        Poles.Floored roof = (x, y, z) -> z <= 1;
        check(Poles.footLevel(roof, 50, 5, 1) == 1, "a pole with a floor under its foot stands on it");
        check(Poles.footLevel((x, y, z) -> false, 50, 5, 2) == 0, "and none stands lower than the ground");
    }

    /**
     * A pole whose square is loaded and the square three on along x and y is not: whether its top is drawn a level up
     * there can change the answer only where its own square has nothing on the level over a length. And a length with
     * lengths over and under it reads the same whatever is loaded round it.
     */
    private static void aPoleAtTheEdgeOfTheLoadedMapIsReadFromItsOwnSquare() {
        HashMap<Long, String> drawn = new HashMap<>();
        Poles.Drawn map = (x, y, z) -> drawn.get(Map.key(x, y, z));
        int on = Spans.LEVEL_SQUARES;
        drawn.put(Map.key(10, 5, 0), "appliances_com_01_88");
        drawn.put(Map.key(10, 5, 1), "appliances_com_01_89");
        drawn.put(Map.key(10, 5, 2), "appliances_com_01_93");
        Poles.Loaded toTwelve = (x, y) -> x < 12, all = (x, y) -> true, none = (x, y) -> false;
        check(!Poles.unsettled(map, toTwelve, 10, 5, 2, 0), "the top with its arm on its own square is known with the squares three on not loaded");
        check(Poles.unsettled(map, toTwelve, 10, 5, 3, 0), "the level over it may yet have its top drawn up three on");
        check(!Poles.unsettled(map, all, 10, 5, 3, 0), "and not once that is loaded");
        check(!Poles.unsettled(map, none, 10, 5, 1, 0) && !Poles.unsettled(map, none, 10, 5, 2, 0),
                "a length with lengths over and under it is known with nothing round it loaded");
        check(!Poles.unsettled(map, none, 10, 5, 0, 0), "and so is the foot on the lowest level");
        // The poles at (10718, 9763) in Riverside: two lengths on their square, the top a level up and three on.
        drawn.remove(Map.key(10, 5, 2));
        drawn.put(Map.key(10 + on, 5 + on, 3), "appliances_com_01_93");
        Poles.Loaded fromTwelve = (x, y) -> x >= 12;
        check(Poles.unsettled(map, fromTwelve, 10 + on, 5 + on, 3, 0), "a top with nothing under it may be drawn up from a pole on map not loaded");
        check(!Poles.unsettled(map, all, 10 + on, 5 + on, 3, 0), "and is known once that is loaded");
    }

    /**
     * A run whose middle pole's insulators are known, though the map may yet show more of the pole: the run hangs from
     * it and does not run on past it to the next. A run that stops by such a square with no pole on it is not known
     * at that end.
     */
    private static void aRunHangsFromAPoleTheMapMayYetShowMoreOf() {
        Map map = new Map();
        map.pole(10, 5, 0f, -0.8f);
        map.pole(23, 5, 0f, -0.8f);
        map.pole(36, 5, 0f, -0.8f);
        wire(map, 10, 36, 4, 4.7f);
        map.unsettled.add(Map.key(23, 5));
        float[] ends = ends(piecesOf(map, 16, 4, 0), 0);
        check(ends[9] == Spans.FAST && near(ends[5], 23.5f), "the run hangs to the pole the map may yet show more of: " + ends[5]);
        ends = ends(piecesOf(map, 30, 4, 0), 0);
        check(ends[4] == Spans.FAST && near(ends[0], 23.5f), "and from it: " + ends[0]);
        Map stops = new Map();
        stops.pole(10, 5, 0f, -0.8f);
        wire(stops, 10, 20, 4, 4.7f);
        stops.unsettled.add(Map.key(21, 5));
        ends = ends(piecesOf(stops, 15, 4, 0), 0);
        check(ends[9] == Spans.WANTING, "a run that stops by a square the map may yet show a pole's top on is not known at that end: " + ends[9]);
        stops.unsettled.clear();
        ends = ends(piecesOf(stops, 15, 4, 0), 0);
        check(ends[9] == Spans.LOOSE, "and is loose where nothing more can be shown there: " + ends[9]);
    }

    /**
     * A pole two lengths tall with nothing on the level over them, the square three on not loaded: its top may be drawn
     * up there, so the build of its foot, which climbs to its top, wants the map, as the length under that level does.
     */
    private static void aTallPoleWhoseTopMayBeDrawnUpOntoMapNotLoadedWantsTheMap() {
        HashMap<Long, String> drawn = new HashMap<>();
        Poles.Drawn map = (x, y, z) -> drawn.get(Map.key(x, y, z));
        drawn.put(Map.key(10, 5, 0), "appliances_com_01_88");
        drawn.put(Map.key(10, 5, 1), "appliances_com_01_89");
        Poles.Loaded toTwelve = (x, y) -> x < 12, all = (x, y) -> true;
        check(Poles.unsettledPole(map, toTwelve, 10, 5, 0, 0), "the foot of a pole whose top may be drawn up onto map not loaded wants the map");
        check(!Poles.unsettledPole(map, all, 10, 5, 0, 0), "and not once that map is loaded");
        drawn.put(Map.key(10, 5, 2), "appliances_com_01_93");
        for (Poles.Loaded loaded : new Poles.Loaded[] {toTwelve, all}) {
            check(Poles.unsettledPole(map, loaded, 10, 5, 0, 0) == Poles.unsettledPole(map, loaded, 10, 5, 2, 0),
                    "with its arm on its own square the foot asks what the arm's own length asks");
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 2e-3f;
    }
}
