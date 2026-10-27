package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/** Tests of which piece of track each sprite is, and of the rails and sleepers Track builds of them. */
final class TrackTests {
    private TrackTests() {}

    static void run() {
        Checks.run("eachSpriteIsThePieceItsPicturePaints", TrackTests::eachSpriteIsThePieceItsPicturePaints);
        Checks.run("everyRailwaySpriteTheMapLaysIsInTheTable", TrackTests::everyRailwaySpriteTheMapLaysIsInTheTable);
        Checks.run("aRailAloneHasNoSleepersAndOtherwiseIsItsTrackedTwin", TrackTests::aRailAloneHasNoSleepersAndOtherwiseIsItsTrackedTwin);
        Checks.run("aSidedRailTakesTheSideItsTrackIsOn", TrackTests::aSidedRailTakesTheSideItsTrackIsOn);
        Checks.run("aSquareWhoseTrackCannotBeReadIsLeftWhole", TrackTests::aSquareWhoseTrackCannotBeReadIsLeftWhole);
        Checks.run("aTrackLaidWithoutItsMiddleRowHasItsMiddleOnTheSeam", TrackTests::aTrackLaidWithoutItsMiddleRowHasItsMiddleOnTheSeam);
        Checks.run("aWagonsDeckCoversTheTrackUnderIt", TrackTests::aWagonsDeckCoversTheTrackUnderIt);
        Checks.run("aBendMeetsBothItsLinesAlongThem", TrackTests::aBendMeetsBothItsLinesAlongThem);
        Checks.run("theRailsAreAtStandardGauge", TrackTests::theRailsAreAtStandardGauge);
        Checks.run("aStraightsRailsRunOnUnbrokenAcrossItsSquares", TrackTests::aStraightsRailsRunOnUnbrokenAcrossItsSquares);
        Checks.run("aDiagonalsRailsRunOnUnbrokenAcrossItsSquares", TrackTests::aDiagonalsRailsRunOnUnbrokenAcrossItsSquares);
        Checks.run("aBendsRailsRunOnUnbrokenFromOneStraightToTheOther", TrackTests::aBendsRailsRunOnUnbrokenFromOneStraightToTheOther);
        Checks.run("everySleeperIsBuiltOnceAndClearOfTheNext", TrackTests::everySleeperIsBuiltOnceAndClearOfTheNext);
        Checks.run("aSleeperIsBuiltWhereverItLiesOnTrack", TrackTests::aSleeperIsBuiltWhereverItLiesOnTrack);
        Checks.run("aBendJoinedToItsStraightsKeepsItsSleepersEvenlyApart", TrackTests::aBendJoinedToItsStraightsKeepsItsSleepersEvenlyApart);
        Checks.run("everyFaceOfARailFacesOutOfIt", TrackTests::everyFaceOfARailFacesOutOfIt);
        Checks.run("aSleepersTopFacesUpAndIsTexturedEdgeToEdge", TrackTests::aSleepersTopFacesUpAndIsTexturedEdgeToEdge);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static Track.Piece of(String name) {
        Track.Piece piece = Track.of(name);
        check(piece != null, name + " is a piece of track");
        return piece;
    }

    private static void eachSpriteIsThePieceItsPicturePaints() {
        // The straights, three squares across: the middle is the middle square's.
        check(of("industry_railroad_01_0").way == Track.NS && of("industry_railroad_01_0").c == 1.5f, "01_0 is the west square of a straight down the map");
        check(of("industry_railroad_01_1").c == 0.5f && of("industry_railroad_01_2").c == -0.5f, "01_1 and 01_2 are its middle and east squares");
        check(of("industry_railroad_01_5").way == Track.EW && of("industry_railroad_01_5").c == 1.5f, "01_5 is the north square of a straight across");
        check(of("industry_railroad_01_4").c == 0.5f && of("industry_railroad_01_3").c == -0.5f, "01_4 and 01_3 are its middle and south squares");
        check(of("industry_railroad_01_12").way == Track.NE && of("industry_railroad_01_54").c == of("industry_railroad_01_12").c,
                "01_54 is the same square of a diagonal as 01_12");
        check(of("industry_railroad_01_18").way == Track.NW && of("industry_railroad_01_18").c == 0f, "01_18 is the middle of a diagonal the other way");
        check(Track.of("industry_railroad_01_14") == null && Track.of("industry_railroad_05_48") == null && Track.of(null) == null,
                "an empty picture and the rest of the railway are no track");
        check(Track.of("industry_railroad_03_45") == Track.NOTHING, "03_45, two pixels, is a piece of nothing");
        check(Track.of("industry_railroad_05_38") == Track.WAGON_FLOOR && Track.of("industry_railroad_05_47") == Track.WAGON_FLOOR
                && Track.of("industry_railroad_05_0") == null && Track.of("industry_railroad_05_40") == null,
                "a wagon's deck is known, and its frame and wheels on the ground and its walls are not");
        // A bend's pieces share it, each at its own place.
        check(of("industry_railroad_01_24").bend == Track.A && of("industry_railroad_01_41").bend == Track.A, "01_24 and 01_41 are bend A's");
        check(of("industry_railroad_01_41").dx == 1 && of("industry_railroad_01_41").dy == -2, "01_41 is two north and one east of 01_24");
        check(of("industry_railroad_02_0").bend == Track.WIDE_E && of("industry_railroad_02_45").bend == Track.WIDE_H, "02 is the wide bends");
        HashSet<String> places = new HashSet<>();
        for (String name : Track.names()) {
            Track.Piece piece = Track.of(name);
            if (piece.bend != null) {
                check(places.add(piece.bend.name + "," + piece.dx + "," + piece.dy + "," + piece.ties), name + " has a place in its bend no other sprite has");
            }
        }
    }

    /** Every sprite of the sheets of track the game's map lays, by sheet: read off the map's lotpacks of build 42. */
    private static final int[][] LAID = {
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39,
            40, 41, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63},
        {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 45, 46, 47,
            48, 49, 50, 51, 52, 53, 54},
        {0, 2, 3, 5, 7, 8, 9, 10, 12, 13, 15, 16, 17, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 33, 34, 35, 36, 37, 38, 39, 41, 45, 46, 47, 49, 51, 52,
            53, 54, 55, 56, 57, 58, 60, 62, 63},
        {0, 2, 3, 4, 5, 7, 8, 9, 10, 11, 12, 16, 18, 19, 20, 21, 23, 24, 25, 26, 27, 28, 32, 34, 35, 36, 37, 39, 40, 42, 46, 47, 48, 50, 51, 52, 53}};

    private static void everyRailwaySpriteTheMapLaysIsInTheTable() {
        String[] sheets = {Track.WITH_TIES, Track.WIDE_BENDS, Track.RAILS, Track.WIDE_RAILS};
        for (int sheet = 0; sheet < sheets.length; sheet++) {
            for (int place : LAID[sheet]) {
                check(Track.of(sheets[sheet] + place) != null, sheets[sheet] + place + ", which the map lays, is in the table");
            }
        }
    }

    private static void aRailAloneHasNoSleepersAndOtherwiseIsItsTrackedTwin() {
        for (String name : Track.names()) {
            Track.Piece piece = Track.of(name);
            if (piece == Track.NOTHING || piece == Track.WAGON_FLOOR) {
                continue;
            }
            if (!name.startsWith(Track.RAILS) && !name.startsWith(Track.WIDE_RAILS)) {
                check(piece.ties || piece.sided, name + " paints sleepers");
                continue;
            }
            check(!piece.ties, name + " paints no sleepers");
            if (piece.sided) {
                continue;
            }
            String twin = (name.startsWith(Track.RAILS) ? Track.WITH_TIES : Track.WIDE_BENDS) + name.substring(name.lastIndexOf('_') + 1);
            Track.Piece tracked = Track.of(twin);
            check(tracked != null && tracked.way == piece.way && tracked.c == piece.c && tracked.bend == piece.bend && tracked.dx == piece.dx
                    && tracked.dy == piece.dy, name + " is the rails of " + twin);
        }
    }

    /** The middle of each line a square builds, on the map, read as the map gives it. */
    private static ArrayList<Double> middles(HashMap<Long, String[]> squares, int x, int y) {
        Track.Square square = new Track.Square();
        check(new Track.Reader(area(squares, null)).read(x, y, square) == Track.LAID, "the track at " + x + "," + y + " is built");
        ArrayList<Double> middles = new ArrayList<>();
        for (int i = 0; i < square.lines; i++) {
            middles.add(square.lineC[i]);
        }
        return middles;
    }

    private static void aSidedRailTakesTheSideItsTrackIsOn() {
        // A straight down the map from x 10 to 12, its middle at 11.5, with a sided rail beside each of its outer squares.
        HashMap<Long, String[]> squares = new HashMap<>();
        for (int y = 0; y < 6; y++) {
            put(squares, 10, y, "industry_railroad_01_0");
            put(squares, 11, y, "industry_railroad_01_1");
            put(squares, 12, y, "industry_railroad_01_2");
        }
        put(squares, 12, 7, "industry_railroad_03_0");
        put(squares, 10, 8, "industry_railroad_03_2");
        check(middles(squares, 12, 7).equals(java.util.List.of(11.5)), "a sided rail at the east of the track is the east rail of it");
        check(middles(squares, 10, 8).equals(java.util.List.of(11.5)), "a sided rail at the west of the track is the west rail of it");
        // Two sided rails across the map two squares apart, and nothing else: they are one track's.
        HashMap<Long, String[]> pair = new HashMap<>();
        for (int x = 0; x < 4; x++) {
            put(pair, x, 20, "industry_railroad_03_3");
            put(pair, x, 22, "industry_railroad_01_7");
        }
        check(middles(pair, 1, 20).equals(java.util.List.of(21.5)) && middles(pair, 2, 22).equals(java.util.List.of(21.5)),
                "two sided rails two squares apart are the two rails of a track between them");
    }

    private static void aSquareWhoseTrackCannotBeReadIsLeftWhole() {
        HashMap<Long, String[]> squares = new HashMap<>();
        put(squares, 5, 5, "industry_railroad_03_0", "industry_railroad_03_45");
        Track.Square square = new Track.Square();
        check(new Track.Reader(area(squares, null)).read(5, 5, square) == Track.FLAT, "a sided rail with no track either side leaves its square's pictures as they are");
        HashSet<Long> unloaded = new HashSet<>();
        unloaded.add(key(7, 9));
        check(new Track.Reader(area(squares, unloaded)).read(5, 5, square) == Track.WAITING, "and one whose track is on a square not loaded waits for it");
        put(squares, 4, 5, "industry_railroad_01_1");
        check(new Track.Reader(area(squares, null)).read(5, 5, square) == Track.LAID && square.lines == 1 && square.lineC[0] == 4.5,
                "with the track beside it, both the square's pieces are built");
    }

    private static void aTrackLaidWithoutItsMiddleRowHasItsMiddleOnTheSeam() {
        // As the map has it at 1958 to 2043, 10730 to 10733: the north and south rows of two straights across, with no middle rows.
        HashMap<Long, String[]> squares = new HashMap<>();
        for (int x = 0; x < 6; x++) {
            put(squares, x, 30, "industry_railroad_01_5");
            put(squares, x, 31, "industry_railroad_01_3");
            put(squares, x, 32, "industry_railroad_01_5");
            put(squares, x, 33, "industry_railroad_01_3");
        }
        check(middles(squares, 2, 30).equals(java.util.List.of(31.0)) && middles(squares, 2, 31).equals(java.util.List.of(31.0)),
                "the first two rows are a track with its middle where they meet");
        check(middles(squares, 3, 32).equals(java.util.List.of(33.0)) && middles(squares, 3, 33).equals(java.util.List.of(33.0)), "and so are the next two");
        Built built = build(squares);
        for (float[] q : built.heads) {
            float y = (q[1] + q[4] + q[7] + q[10]) / 4f, off = Math.min(Math.abs(y - 31f), Math.abs(y - 33f));
            check(Math.abs(off - Track.RAIL_OFF) < 1e-4f, "each rail is at standard gauge from its track's middle: " + y);
        }
        check(built.ties.size() > 20, "they have sleepers");
        // A straight's middle row is not a track's outer one.
        HashMap<Long, String[]> whole = new HashMap<>();
        for (int x = 0; x < 3; x++) {
            put(whole, x, 0, "industry_railroad_01_5");
            put(whole, x, 1, "industry_railroad_01_4");
            put(whole, x, 2, "industry_railroad_01_3");
            put(whole, x, 3, "industry_railroad_01_5");
        }
        check(middles(whole, 1, 2).equals(java.util.List.of(1.5)) && middles(whole, 1, 3).equals(java.util.List.of(4.5)),
                "a whole straight and the next one beside it keep their own middles");
    }

    private static void aWagonsDeckCoversTheTrackUnderIt() {
        // As the map has it at 11665 to 11669, 10101 and 10102, and at 12733, 4433 the wheels of one beside it.
        HashMap<Long, String[]> squares = new HashMap<>();
        put(squares, 0, 0, "industry_railroad_01_4", "industry_railroad_05_38", "industry_railroad_05_40");
        put(squares, 0, 1, "industry_railroad_01_4");
        put(squares, 0, 2, "industry_railroad_01_2", "industry_railroad_05_0", "industry_railroad_05_40");
        check(new Track.Reader(area(squares, null)).read(0, 0, new Track.Square()) == Track.COVERED, "a wagon's deck covers the track on its square");
        check(new Track.Reader(area(squares, null)).read(0, 1, new Track.Square()) == Track.LAID, "and not the track beside it");
        check(new Track.Reader(area(squares, null)).read(0, 2, new Track.Square()) == Track.LAID, "and a wagon's wheels on the ground lie under nothing");
    }

    private static final Track.Bend[] BENDS = {Track.A, Track.B, Track.C, Track.D, Track.WIDE_E, Track.WIDE_F, Track.WIDE_G, Track.WIDE_H};

    private static void aBendMeetsBothItsLinesAlongThem() {
        for (Track.Bend bend : BENDS) {
            double[] n1 = normal(bend.wayOne), n2 = normal(bend.wayTwo);
            check(Math.abs(n1[0] * bend.oneX + n1[1] * bend.oneY - bend.cOne) < 1e-9, "bend " + bend.name + " leaves its first line on it");
            check(Math.abs(n2[0] * bend.twoX + n2[1] * bend.twoY - bend.cTwo) < 1e-9, "bend " + bend.name + " leaves its second line on it");
            check(Math.abs(Math.hypot(bend.oneX - bend.centreX, bend.oneY - bend.centreY) - bend.radius) < 1e-9
                    && Math.abs(Math.hypot(bend.twoX - bend.centreX, bend.twoY - bend.centreY) - bend.radius) < 1e-9, "bend " + bend.name + " is a circle through both");
            check(Math.abs((bend.oneX - bend.centreX) * bend.alongOneX + (bend.oneY - bend.centreY) * bend.alongOneY) < 1e-9
                    && Math.abs((bend.twoX - bend.centreX) * bend.alongTwoX + (bend.twoY - bend.centreY) * bend.alongTwoY) < 1e-9,
                    "bend " + bend.name + " runs along each line where it leaves it");
            check(Math.abs(Math.abs(bend.sweep) - Math.toRadians(45)) < 1e-9, "bend " + bend.name + " turns from a straight to a diagonal");
        }
    }

    /** The normal a way's lines are given by: the constant of the line through (x, y) is normal . (x, y). */
    private static double[] normal(int way) {
        double[] d = Track.direction(way);
        double scale = way == Track.NE || way == Track.NW ? Math.sqrt(2) : 1;
        return new double[] {d[1] * scale, -d[0] * scale};
    }

    private static void theRailsAreAtStandardGauge() {
        Built built = build(new String[][] {{"industry_railroad_01_0", "industry_railroad_01_1", "industry_railroad_01_2"}}, 10, 5, 1, 0);
        ArrayList<Float> heads = new ArrayList<>();
        for (float[] q : built.heads) {
            float inner = Math.min(Math.abs(q[0] - 11.5f), Math.abs(q[6] - 11.5f));
            heads.add(inner);
        }
        check(!heads.isEmpty(), "a straight has rails");
        for (float inner : heads) {
            check(Math.abs(inner - Track.GAUGE / 2f) < 1e-4f, "the inner edge of a rail's head is half the gauge from the middle: " + inner);
        }
    }

    private static final String[] DOWN = {"industry_railroad_01_0", "industry_railroad_01_1", "industry_railroad_01_2"};

    private static void aStraightsRailsRunOnUnbrokenAcrossItsSquares() {
        String[][] rows = new String[8][];
        for (int row = 0; row < rows.length; row++) {
            rows[row] = DOWN;
        }
        Built built = build(rows, 20, 30, 1, 0);
        checkUnbroken(built, (x, y) -> y > 30.001f && y < 37.999f, "a straight down the map");
    }

    private static void aDiagonalsRailsRunOnUnbrokenAcrossItsSquares() {
        // A diagonal is laid in squares whose x + y falls in a band six wide.
        HashMap<Long, String[]> squares = new HashMap<>();
        String[] byC = {"industry_railroad_01_12", "industry_railroad_01_13", "industry_railroad_01_10", "industry_railroad_01_11", "industry_railroad_01_8",
            "industry_railroad_01_9"};
        float line = 40.5f;
        for (int x = 10; x < 30; x++) {
            for (int y = 10; y < 30; y++) {
                float c = line - x - y;
                int at = Math.round(3.5f - c);
                if (Math.abs(3.5f - c - at) < 1e-6 && at >= 0 && at < byC.length) {
                    put(squares, x, y, byC[at]);
                }
            }
        }
        Built built = build(squares);
        checkUnbroken(built, (x, y) -> x > 14 && x < 25, "a diagonal");
    }

    /** A bend's squares laid alone, its first at (50, 50). */
    private static HashMap<Long, String[]> bendAlone(Track.Bend bend) {
        HashMap<Long, String[]> squares = new HashMap<>();
        for (String name : Track.names()) {
            Track.Piece piece = Track.of(name);
            if (piece.bend == bend && piece.ties) {
                put(squares, 50 + piece.dx, 50 + piece.dy, name);
            }
        }
        return squares;
    }

    private static void aBendsRailsRunOnUnbrokenFromOneStraightToTheOther() {
        // Each bend laid alone; its rails may end only where they leave its squares, along the straights it joins.
        for (Track.Bend bend : BENDS) {
            HashMap<Long, String[]> squares = bendAlone(bend);
            Built built = build(squares);
            check(!built.heads.isEmpty(), "bend " + bend.name + " has rails");
            // Inside is clear of the edge of the bend's squares.
            checkUnbroken(built, (x, y) -> {
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sy = -1; sy <= 1; sy += 2) {
                        if (!squares.containsKey(key((int) Math.floor(x + sx * 1e-3f), (int) Math.floor(y + sy * 1e-3f)))) {
                            return false;
                        }
                    }
                }
                return true;
            }, "bend " + bend.name);
        }
    }

    private static final float WHOLE = Track.TIE_LONG * Track.TIE_WIDE;

    private static void everySleeperIsBuiltOnceAndClearOfTheNext() {
        Built straight = build(new String[][] {DOWN, DOWN, DOWN, DOWN}, 0, 0, 1, 0);
        int whole = 0;
        for (Tie tie : straight.ties) {
            check(tie.area < WHOLE + 1e-4f, "a sleeper of a straight is built once: " + tie.area);
            whole += tie.area > WHOLE - 1e-4f ? 1 : 0;
        }
        check(straight.ties.size() == 9 && whole == 7, "four squares of straight have seven sleepers and the parts of two more at its ends: " + straight.ties.size() + ", " + whole);
        checkSleepersClear(straight, Track.TIE_APART - 1e-4f, "a straight");
        for (Track.Bend bend : BENDS) {
            Built built = build(bendAlone(bend));
            check(built.ties.size() > 3, "bend " + bend.name + " has sleepers");
            for (Tie tie : built.ties) {
                check(tie.area < WHOLE + 1e-4f, "bend " + bend.name + ": a sleeper is built once: " + tie.area);
            }
            checkSleepersClear(built, 0.35f, "bend " + bend.name);
        }
    }

    private static void aSleeperIsBuiltWhereverItLiesOnTrack() {
        // A straight's outer squares with no middle one between them, as under a wagon's side: each builds its part of every sleeper.
        HashMap<Long, String[]> squares = new HashMap<>();
        for (int y = 0; y < 4; y++) {
            put(squares, 0, y, "industry_railroad_01_0");
            put(squares, 2, y, "industry_railroad_01_2");
        }
        Built built = build(squares);
        float out = 1.5f - Track.TIE_LONG / 2f;
        for (Tie tie : built.ties) {
            check(tie.area < 2 * (1 - out) * Track.TIE_WIDE + 1e-4f, "only the parts of a sleeper on the outer squares are built: " + tie.area);
        }
        int whole = 0;
        for (Tie tie : built.ties) {
            whole += Math.abs(tie.area - 2 * (1 - out) * Track.TIE_WIDE) < 1e-4f ? 1 : 0;
        }
        check(whole == 7, "the seven sleepers wholly along the squares have both their ends: " + whole);
    }

    /** The joined yard the map has at 12724 to 12733, 4438 to 4449: a straight down the map, bend A, and the diagonal on from it. */
    private static final String[] YARD = {
        ". . . . . . . 01_0 01_1 01_2",
        ". . . . . . . 01_0 01_1 01_2",
        ". . . . . . . 01_0 01_1 01_2",
        ". . . . . . 01_26 01_29 01_31 01_41",
        ". . . . . 01_12 01_13 01_25 01_28 01_30",
        ". . . . 01_12 01_13 01_10 01_11 01_24 01_27",
        ". . . 01_12 01_13 01_10 01_11 01_8 01_9 .",
        ". . 01_12 01_13 01_10 01_11 01_8 01_9 . .",
        ". 01_12 01_13 01_10 01_11 01_8 01_9 . . .",
        "01_12 01_13 01_10 01_11 01_8 01_9 . . . .",
        "01_13 01_10 01_11 01_8 01_9 . . . . .",
        "01_10 01_11 01_8 01_9 . . . . . .",
    };

    private static void aBendJoinedToItsStraightsKeepsItsSleepersEvenlyApart() {
        HashMap<Long, String[]> squares = new HashMap<>();
        for (int row = 0; row < YARD.length; row++) {
            String[] names = YARD[row].split(" ");
            for (int x = 0; x < names.length; x++) {
                if (!names[x].equals(".")) {
                    put(squares, 24 + x, 38 + row, "industry_railroad_" + names[x]);
                }
            }
        }
        Built built = build(squares);
        for (Tie tie : built.ties) {
            check(tie.area < WHOLE + 1e-4f, "a sleeper of the yard is built once at " + tie.x + "," + tie.y + ": " + tie.area);
        }
        checkSleepersClear(built, 0.35f, "the yard");
        // Along the track no sleeper is further from the next than a gap and a half, away from its two ends.
        for (Tie tie : built.ties) {
            if (tie.y < 38.5 || tie.x < 26.5) {
                continue;
            }
            double nearest = Double.MAX_VALUE;
            for (Tie other : built.ties) {
                if (other != tie) {
                    nearest = Math.min(nearest, Math.hypot(other.x - tie.x, other.y - tie.y));
                }
            }
            check(nearest < 1.5 * Track.TIE_APART, "the yard: the next sleeper from " + tie.x + "," + tie.y + " is " + nearest + " away");
        }
    }

    private static void checkSleepersClear(Built built, float apart, String what) {
        ArrayList<Tie> ties = built.ties;
        for (int i = 0; i < ties.size(); i++) {
            Tie a = ties.get(i);
            for (int j = i + 1; j < ties.size(); j++) {
                Tie b = ties.get(j);
                double d = Math.hypot(a.x - b.x, a.y - b.y);
                check(d >= apart, what + ": two sleepers' middles " + d + " apart at " + a.x + "," + a.y);
            }
        }
    }

    private static void everyFaceOfARailFacesOutOfIt() {
        Built straight = build(new String[][] {DOWN, DOWN}, 10, 5, 1, 0);
        for (float[] q : straight.railFaces) {
            float[] n = normalOf(q);
            float cx = (q[0] + q[3] + q[6] + q[9]) / 4f;
            float axis = cx < 11.5f ? 11.5f - Track.RAIL_OFF : 11.5f + Track.RAIL_OFF;
            check(n[0] * (cx - axis) >= -1e-7f && (Math.abs(cx - axis) > 1e-5f || n[2] > 0f), "a rail's face at " + cx + " faces out of it");
        }
        for (Track.Bend bend : BENDS) {
            Built built = build(bendAlone(bend));
            double ox = 50 + bend.centreX, oy = 50 + bend.centreY;
            for (float[] q : built.railFaces) {
                float cx = (q[0] + q[3] + q[6] + q[9]) / 4f, cy = (q[1] + q[4] + q[7] + q[10]) / 4f;
                double r = Math.hypot(cx - ox, cy - oy);
                double turned = Math.IEEEremainder(Math.atan2(cy - oy, cx - ox) - bend.startAngle, 2 * Math.PI) * Math.signum(bend.sweep);
                // Only the arc's own rails: past where it leaves them the lines' rails are straight.
                if (turned <= 0 || turned >= Math.abs(bend.sweep)) {
                    continue;
                }
                double axis = r > bend.radius ? bend.radius + Track.RAIL_OFF : bend.radius - Track.RAIL_OFF;
                float[] n = normalOf(q);
                double radial = (n[0] * (cx - ox) + n[1] * (cy - oy)) / r;
                check(radial * (r - axis) >= -1e-6 && (Math.abs(r - axis) > 1e-4 || n[2] > 0f), "bend " + bend.name + ": a rail's face faces out of it");
            }
        }
    }

    private static void aSleepersTopFacesUpAndIsTexturedEdgeToEdge() {
        String[] across = {"industry_railroad_01_16", "industry_railroad_01_17", "industry_railroad_01_18", "industry_railroad_01_19", "industry_railroad_01_20"};
        HashMap<Long, String[]> squares = new HashMap<>();
        for (int i = 0; i < across.length; i++) {
            for (int k = 0; k < 6; k++) {
                put(squares, i + k, k, across[i]);
            }
        }
        Built built = build(squares);
        check(!built.tieTops.isEmpty(), "a diagonal has sleepers");
        for (int i = 0; i < built.tieTops.size(); i++) {
            check(normalOf(built.tieTops.get(i))[2] > 0f, "a sleeper's top faces up");
            float[] along = built.tieAlong.get(i);
            for (int corner = 0; corner < 4; corner++) {
                check(along[corner * 2] > -1e-4f && along[corner * 2] < 1 + 1e-4f && along[corner * 2 + 1] > -1e-4f && along[corner * 2 + 1] < 1 + 1e-4f,
                        "a sleeper's top takes its picture from inside the picture's sleeper");
            }
        }
        int whole = 0;
        for (Tie tie : built.ties) {
            if (tie.area > WHOLE - 1e-4f) {
                whole++;
                check(tie.fromS < 1e-4f && tie.toS > 1 - 1e-4f && tie.fromT < 1e-4f && tie.toT > 1 - 1e-4f,
                        "a whole sleeper's top takes its picture from end to end and side to side");
            }
        }
        check(whole > 4, "the diagonal has whole sleepers in the middle of it: " + whole);
    }

    private interface Inside {
        boolean at(float x, float y);
    }

    /** Every end of a length of rail inside the given part of the map meets the end of another. */
    private static void checkUnbroken(Built built, Inside inside, String what) {
        ArrayList<float[]> ends = new ArrayList<>();
        for (float[] q : built.heads) {
            ends.add(new float[] {(q[0] + q[9]) / 2f, (q[1] + q[10]) / 2f});
            ends.add(new float[] {(q[3] + q[6]) / 2f, (q[4] + q[7]) / 2f});
        }
        int inner = 0;
        for (int i = 0; i < ends.size(); i++) {
            float[] end = ends.get(i);
            if (!inside.at(end[0], end[1])) {
                continue;
            }
            inner++;
            boolean met = false;
            for (int j = 0; j < ends.size() && !met; j++) {
                met = j != i && Math.abs(ends.get(j)[0] - end[0]) < 1e-4f && Math.abs(ends.get(j)[1] - end[1]) < 1e-4f;
            }
            check(met, what + ": a rail ends at " + end[0] + "," + end[1] + " with nothing on");
        }
        check(inner > 0, what + " has rails inside the part looked at");
    }

    private static long key(int x, int y) {
        return (long) x << 32 | (y & 0xffffffffL);
    }

    private static void put(HashMap<Long, String[]> squares, int x, int y, String... names) {
        squares.put(key(x, y), names);
    }

    /** The map as the squares given have it, every other square loaded and empty but those given as not loaded. */
    private static Track.Area area(HashMap<Long, String[]> squares, HashSet<Long> unloaded) {
        return (x, y, pieces) -> {
            if (unloaded != null && unloaded.contains(key(x, y))) {
                return -1;
            }
            String[] names = squares.get(key(x, y));
            int n = 0;
            for (String name : names == null ? new String[0] : names) {
                if (Track.of(name) != null) {
                    pieces[n++] = Track.of(name);
                }
            }
            return n;
        };
    }

    /** A sleeper, from the parts of its top that were built: its middle, the area of its top, and how far along and across its picture they reach. */
    private static final class Tie {
        double x, y;
        float area, fromS = Float.MAX_VALUE, toS = -Float.MAX_VALUE, fromT = Float.MAX_VALUE, toT = -Float.MAX_VALUE;
    }

    /** What Track builds of squares, gathered by what each face is, and the sleepers by where their middles are. */
    private static final class Built {
        final ArrayList<float[]> heads = new ArrayList<>(), railFaces = new ArrayList<>(), tieTops = new ArrayList<>(), tieAlong = new ArrayList<>();
        final ArrayList<Tie> ties = new ArrayList<>();
    }

    /** Rows of squares from (x, y), each row's squares stepping by (dx, dy) and each row the next one down or across. */
    private static Built build(String[][] rows, int x, int y, int dx, int dy) {
        HashMap<Long, String[]> squares = new HashMap<>();
        for (int row = 0; row < rows.length; row++) {
            for (int i = 0; i < rows[row].length; i++) {
                put(squares, x + i * dx + row * dy, y + i * dy + row * dx, rows[row][i]);
            }
        }
        return build(squares);
    }

    private static Built build(HashMap<Long, String[]> squares) {
        Built built = new Built();
        float top = Track.TIE_TOP + Track.RAIL_HIGH;
        Track.Builder builder = new Track.Builder(new Track.Sink() {
            @Override
            public float ground(float x, float y) {
                return 0f;
            }

            @Override
            public void quad(float[] corners, float[] along, int part) {
                float[] q = corners.clone();
                if (part == Track.RAIL) {
                    built.railFaces.add(q);
                    if (q[2] == top && q[5] == top && q[8] == top && q[11] == top) {
                        built.heads.add(q);
                    }
                } else if (q[2] == Track.TIE_TOP && q[5] == Track.TIE_TOP && q[8] == Track.TIE_TOP && q[11] == Track.TIE_TOP) {
                    built.tieTops.add(q);
                    built.tieAlong.add(along.clone());
                    gather(built, q, along);
                }
            }
        });
        Track.Reader reader = new Track.Reader(area(squares, null));
        for (Long at : squares.keySet()) {
            int x = (int) (at >> 32), y = (int) (long) at;
            check(reader.readAround(x, y) == Track.LAID, "the track at " + x + "," + y + " is built");
            builder.build(reader.square(), x, y, reader);
        }
        return built;
    }

    /** Adds a part of a sleeper's top to its sleeper, whose middle is where along and across its picture are both a half. */
    private static void gather(Built built, float[] q, float[] along) {
        double s1 = along[2] - along[0], t1 = along[3] - along[1], s2 = along[4] - along[0], t2 = along[5] - along[1];
        double det = s1 * t2 - s2 * t1;
        double ax = 0.5 - along[0], at = 0.5 - along[1];
        double u = (ax * t2 - s2 * at) / det, v = (s1 * at - ax * t1) / det;
        double x = q[0] + u * (q[3] - q[0]) + v * (q[6] - q[0]), y = q[1] + u * (q[4] - q[1]) + v * (q[7] - q[1]);
        Tie tie = null;
        for (Tie known : built.ties) {
            if (Math.hypot(known.x - x, known.y - y) < 0.05) {
                tie = known;
            }
        }
        if (tie == null) {
            tie = new Tie();
            tie.x = x;
            tie.y = y;
            built.ties.add(tie);
        }
        tie.area += (float) (Math.abs(cross(q, 1, 2)) + Math.abs(cross(q, 2, 3))) / 2f;
        for (int corner = 0; corner < 4; corner++) {
            tie.fromS = Math.min(tie.fromS, along[corner * 2]);
            tie.toS = Math.max(tie.toS, along[corner * 2]);
            tie.fromT = Math.min(tie.fromT, along[corner * 2 + 1]);
            tie.toT = Math.max(tie.toT, along[corner * 2 + 1]);
        }
    }

    /** The cross product, in the map's plane, of corners a and b of a quad less its first. */
    private static double cross(float[] q, int a, int b) {
        return (q[a * 3] - q[0]) * (q[b * 3 + 1] - q[1]) - (q[a * 3 + 1] - q[1]) * (q[b * 3] - q[0]);
    }

    private static float[] normalOf(float[] q) {
        float ux = q[3] - q[0], uy = q[4] - q[1], uz = q[5] - q[2];
        float vx = q[9] - q[0], vy = q[10] - q[1], vz = q[11] - q[2];
        return new float[] {uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
    }
}
