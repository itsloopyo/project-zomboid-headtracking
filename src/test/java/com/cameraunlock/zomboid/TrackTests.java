package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeSet;

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
        Checks.run("aTurnoutsRoutesEachMeetItsLinesAlongThem", TrackTests::aTurnoutsRoutesEachMeetItsLinesAlongThem);
        Checks.run("aTurnoutWhoseRoutesCannotBeReadIsLeftThePicture", TrackTests::aTurnoutWhoseRoutesCannotBeReadIsLeftThePicture);
        Checks.run("aRouteByADiagonalIsTwoArcsAndTheStraightBetween", TrackTests::aRouteByADiagonalIsTwoArcsAndTheStraightBetween);
        Checks.run("aStraightRunsOnAcrossOtherTrackAsItsRailsAlone", TrackTests::aStraightRunsOnAcrossOtherTrackAsItsRailsAlone);
        Checks.run("aTurnoutIsReadTheSameFromEachOfItsSquares", TrackTests::aTurnoutIsReadTheSameFromEachOfItsSquares);
        Checks.run("everyRailOfABuiltTurnoutHasASleeperUnderIt", TrackTests::everyRailOfABuiltTurnoutHasASleeperUnderIt);
        Checks.run("everyRailOfATurnoutEndsOnAnotherOrAtABuffer", TrackTests::everyRailOfATurnoutEndsOnAnotherOrAtABuffer);
        Checks.run("aGapInAStraightIsBridged", TrackTests::aGapInAStraightIsBridged);
        Checks.run("aSquareWithMoreTrackThanCanBeBuiltIsLeftThePicture", TrackTests::aSquareWithMoreTrackThanCanBeBuiltIsLeftThePicture);
    }

    /** Lays the pieces of sheet of the straight of way with its middle at c on every square from (x0, y0) to (x1, y1) it crosses that where takes in, by its middle. */
    private static void line(HashMap<Long, String[]> squares, String sheet, int way, double c, int x0, int y0, int x1, int y1, Inside where) {
        double[] n = normal(way);
        long nx = Math.round(n[0]), ny = Math.round(n[1]);
        HashSet<Float> laid = new HashSet<>();
        for (String name : Track.names()) {
            Track.Piece p = Track.of(name);
            if (!name.startsWith(sheet) || p == Track.NOTHING || p == Track.WAGON_FLOOR || p.bend != null || p.sided || p.way != way || !laid.add(p.c)) {
                continue;
            }
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    if (p.c + nx * x + ny * y == c && (where == null || where.at(x + 0.5f, y + 0.5f))) {
                        lay(squares, x, y, name);
                    }
                }
            }
        }
    }

    /** Lays the pieces of sheet of a bend with its first square at (x, y): all of them, or those at the places given, x then y from the first. */
    private static void bend(HashMap<Long, String[]> squares, String sheet, Track.Bend bend, int x, int y, int... places) {
        for (String name : Track.names()) {
            Track.Piece p = Track.of(name);
            if (!name.startsWith(sheet) || p.bend != bend) {
                continue;
            }
            boolean given = places.length == 0;
            for (int i = 0; i < places.length; i += 2) {
                given |= places[i] == p.dx && places[i + 1] == p.dy;
            }
            if (given) {
                lay(squares, x + p.dx, y + p.dy, name);
            }
        }
    }

    private static void lay(HashMap<Long, String[]> squares, int x, int y, String name) {
        String[] had = squares.getOrDefault(key(x, y), new String[0]);
        String[] now = Arrays.copyOf(had, had.length + 1);
        now[had.length] = name;
        squares.put(key(x, y), now);
    }

    /** Whether (x, y) is ahead of (fromX, fromY) along (alongX, alongY). */
    private static boolean ahead(float x, float y, double fromX, double fromY, double alongX, double alongY) {
        return (x - fromX) * alongX + (y - fromY) * alongY > 0;
    }

    /**
     * A yard's turnout: a straight down the map with its middle at x 11.5, off which bend A's
     * rails alone turn south west onto a diagonal, with one square of the straight under the
     * points left with nothing on it, as the map has one at 12809,2621.
     */
    private static HashMap<Long, String[]> yardTurnout() {
        HashMap<Long, String[]> squares = new HashMap<>();
        line(squares, Track.WITH_TIES, Track.NS, 11.5, 10, 0, 12, 23, null);
        bend(squares, Track.RAILS, Track.A, 11, 12);
        Track.Bend a = Track.A;
        line(squares, Track.WITH_TIES, Track.NE, 11 + 12 + a.cOne, 0, 0, 24, 24, (x, y) -> ahead(x, y, 11 + a.oneX, 12 + a.oneY, a.alongOneX, a.alongOneY));
        squares.remove(key(11, 9));
        return squares;
    }

    /**
     * A wye, as the map lays them: a straight down the map that ends where bend B with its
     * sleepers turns off it north east and bend E's rails alone north west, each onto a
     * diagonal.
     */
    private static HashMap<Long, String[]> wye() {
        HashMap<Long, String[]> squares = new HashMap<>();
        Track.Bend b = Track.B, e = Track.WIDE_E;
        line(squares, Track.WITH_TIES, Track.NS, 19.5, 18, 13, 20, 22, null);
        bend(squares, Track.WITH_TIES, b, 20, 11);
        bend(squares, Track.WIDE_RAILS, e, 16, 10);
        line(squares, Track.WITH_TIES, Track.NE, 20 + 11 + b.cOne, 0, 0, 40, 40, (x, y) -> ahead(x, y, 20 + b.oneX, 11 + b.oneY, b.alongOneX, b.alongOneY));
        line(squares, Track.WITH_TIES, Track.NW, 16 - 10 + e.cOne, 0, 0, 40, 40, (x, y) -> ahead(x, y, 16 + e.oneX, 10 + e.oneY, e.alongOneX, e.alongOneY));
        return squares;
    }

    /**
     * A junction the map has as at 12663,4444: a line across that ends short of a straight down
     * the map, joined to it by bend F's rails alone, a diagonal of rails alone and bend G's
     * rails alone, which no one arc between the two lines follows. As on the map, the diagonal's
     * middle row, which the picture paints nothing of, is left with nothing on it.
     */
    private static HashMap<Long, String[]> bendDiagonalBend() {
        HashMap<Long, String[]> squares = new HashMap<>();
        Track.Bend g = Track.WIDE_G, f = Track.WIDE_F;
        line(squares, Track.WITH_TIES, Track.NS, 11.5, 10, 0, 12, 23, null);
        bend(squares, Track.WIDE_RAILS, g, 11, 10);
        bend(squares, Track.WIDE_RAILS, f, 12, 11);
        double diagonal = 11 - 10 + g.cOne;
        line(squares, Track.RAILS, Track.NW, diagonal, 0, 0, 40, 40, (x, y) -> ahead(x, y, 11 + g.oneX, 10 + g.oneY, g.alongOneX, g.alongOneY)
                && ahead(x, y, 12 + f.oneX, 11 + f.oneY, f.alongOneX, f.alongOneY) && !squares.containsKey(key((int) Math.floor(x), (int) Math.floor(y))) && x - y != diagonal);
        line(squares, Track.WITH_TIES, Track.EW, 11 + f.cTwo, 16, 0, 30, 40, null);
        return squares;
    }

    /**
     * As the map has it at 11639,9625: a straight down the map with a switch on it, bend A's
     * rails alone turning off onto a diagonal, and below the switch a second diagonal crossing
     * it, where the straight is laid as its rails alone on either side of its track.
     */
    private static HashMap<Long, String[]> crossing() {
        HashMap<Long, String[]> squares = new HashMap<>();
        Track.Bend a = Track.A;
        line(squares, Track.WITH_TIES, Track.NS, 11.5, 10, 0, 12, 30, (x, y) -> y < 13 || y > 21);
        for (int y = 13; y <= 20; y++) {
            lay(squares, 10, y, Track.WITH_TIES + 6);
            lay(squares, 12, y, Track.WITH_TIES + 6);
        }
        bend(squares, Track.RAILS, a, 11, 11);
        line(squares, Track.WITH_TIES, Track.NE, 11 + 11 + a.cOne, 0, 0, 24, 24, (x, y) -> ahead(x, y, 11 + a.oneX, 11 + a.oneY, a.alongOneX, a.alongOneY));
        line(squares, Track.WITH_TIES, Track.NE, CROSSING, 0, 0, 40, 40, null);
        return squares;
    }

    /** The middle of the crossing's second diagonal, which crosses the straight at y 17. */
    private static final double CROSSING = 28.5;

    /** As the map has it at 11729 to 11738, 10075 to 10088: a straight down the map with two pieces of bend E's rails alone beside it, the rest of the bend not laid. */
    private static HashMap<Long, String[]> incompleteSwitch() {
        HashMap<Long, String[]> squares = new HashMap<>();
        line(squares, Track.WITH_TIES, Track.NS, 13.5, 12, 0, 14, 12, null);
        bend(squares, Track.WIDE_RAILS, Track.WIDE_E, 10, 5, 0, 0, 4, 1);
        return squares;
    }

    private interface Fixture {
        HashMap<Long, String[]> squares();
    }

    /** A fixture with a turnout, a square of the turnout, and how many ways it turns where it is built. */
    private record Named(String name, Fixture fixture, int x, int y, int routes) {}

    /** The fixtures whose turnouts must be built. */
    private static final Named[] BUILT = {new Named("the yard's turnout", TrackTests::yardTurnout, 11, 11, 1), new Named("the wye", TrackTests::wye, 19, 13, 2),
        new Named("the junction by a diagonal", TrackTests::bendDiagonalBend, 11, 10, 2), new Named("the switch above a crossing", TrackTests::crossing, 11, 12, 1)};

    /** The fixtures whose turnouts are left the pictures they are. */
    private static final Named[] LEFT = {new Named("the incomplete switch", TrackTests::incompleteSwitch, 14, 6, 0)};

    private static final Fixture[] FIXTURES = {TrackTests::yardTurnout, TrackTests::wye, TrackTests::bendDiagonalBend, TrackTests::crossing, TrackTests::incompleteSwitch};

    private static void aRouteByADiagonalIsTwoArcsAndTheStraightBetween() {
        Track.Turnout turnout = new Track.Reader(area(bendDiagonalBend(), null)).turnout(11, 10);
        check(turnout.unread == null, "the junction by a diagonal is built: " + turnout.unread);
        double diagonal = 11 - 10 + Track.WIDE_G.cOne;
        Track.Line straight = null;
        for (Track.Line line : turnout.lines) {
            if (line.way == Track.NW && line.c == diagonal) {
                straight = line;
            }
        }
        check(straight != null && straight.from < straight.to && !Double.isInfinite(straight.from) && !Double.isInfinite(straight.to),
                "the route runs straight along the diagonal between its arcs: " + (straight == null ? "no straight" : straight.from + " to " + straight.to));
        double[] d = Track.direction(Track.NW);
        int ends = 0;
        for (Track.Route route : turnout.routes) {
            check(Math.abs(Math.abs(route.sweep) - Math.PI / 4) < 1e-9, "each arc of the route turns through 45 degrees: " + Math.toDegrees(route.sweep));
            for (double angle : new double[] {route.start, route.start + route.sweep}) {
                double px = route.x + route.r * Math.cos(angle), py = route.y + route.r * Math.sin(angle), along = px * d[0] + py * d[1];
                boolean onStraight = Math.abs(px - py - diagonal) < 1e-6;
                if (onStraight) {
                    ends++;
                    check(Math.abs(along - straight.from) < 1e-6 || Math.abs(along - straight.to) < 1e-6,
                            "an arc meets the straight where it ends, at " + px + "," + py + ": " + along + " not " + straight.from + " or " + straight.to);
                }
            }
        }
        check(ends == 2, "each end of the straight is where an arc leaves it: " + ends + " arc ends on it");
    }

    private static void aStraightRunsOnAcrossOtherTrackAsItsRailsAlone() {
        Track.Turnout turnout = new Track.Reader(area(crossing(), null)).turnout(11, 12);
        check(turnout.unread == null, "the switch above a crossing is built: " + turnout.unread);
        boolean through = false, crossed = false;
        for (Track.Line line : turnout.lines) {
            through |= line.way == Track.NS && line.c == 11.5 && line.from == Double.NEGATIVE_INFINITY && line.to == Double.POSITIVE_INFINITY;
            crossed |= line.way == Track.NE && line.c == CROSSING;
        }
        check(through, "the straight runs on through the crossing as one line");
        check(crossed, "the crossing diagonal is a line of the turnout");
    }

    private static void aTurnoutsRoutesEachMeetItsLinesAlongThem() {
        for (Named named : BUILT) {
            Track.Turnout turnout = new Track.Reader(area(named.fixture().squares(), null)).turnout(named.x(), named.y());
            String where = named.name();
            check(turnout != Track.Turnout.NONE && turnout.unread == null, where + " is built: " + turnout.unread);
            check(turnout.routes.size() == named.routes(), where + " turns " + named.routes() + " ways: " + turnout.routes.size());
            for (Track.Route route : turnout.routes) {
                check(route.r >= Track.ROUTE_TIGHTEST, where + " turns no tighter than it may: " + route.r);
                for (double angle : new double[] {route.start, route.start + route.sweep}) {
                    double px = route.x + route.r * Math.cos(angle), py = route.y + route.r * Math.sin(angle);
                    boolean meets = false;
                    for (Track.Line line : turnout.lines) {
                        double[] n = normal(line.way), d = Track.direction(line.way);
                        meets |= Math.abs(n[0] * px + n[1] * py - line.c) < 1e-6 && Math.abs(Math.cos(angle) * d[0] + Math.sin(angle) * d[1]) < 1e-6;
                    }
                    check(meets, where + ": a route meets a line along it where it leaves it, at " + px + "," + py);
                }
            }
        }
    }

    private static void aTurnoutWhoseRoutesCannotBeReadIsLeftThePicture() {
        for (Named named : LEFT) {
            HashMap<Long, String[]> squares = named.fixture().squares();
            Track.Turnout turnout = new Track.Reader(area(squares, null)).turnout(named.x(), named.y());
            check(turnout != Track.Turnout.NONE && turnout.unread != null, named.name() + " is a turnout left the picture it is");
            for (long at : turnout.squares) {
                int x = (int) (at >> 32), y = (int) at;
                check(new Track.Reader(area(squares, null)).read(x, y, new Track.Square()) == Track.FLAT, named.name() + ": the square at " + x + "," + y + " is left as it is");
            }
        }
    }

    /** What a reader made of a turnout, in full. */
    private static String described(Track.Turnout turnout) {
        StringBuilder what = new StringBuilder(String.valueOf(turnout.unread));
        for (Track.Line line : turnout.lines) {
            what.append(" line ").append(line.way).append(' ').append(line.c).append(' ').append(line.from).append(' ').append(line.to);
        }
        for (Track.Route route : turnout.routes) {
            what.append(" route ").append(route.x).append(' ').append(route.y).append(' ').append(route.r).append(' ').append(route.start).append(' ').append(route.sweep);
        }
        return what.append(" on ").append(new TreeSet<>(turnout.squares)).toString();
    }

    private static void aTurnoutIsReadTheSameFromEachOfItsSquares() {
        for (Fixture fixture : FIXTURES) {
            HashMap<Long, String[]> squares = fixture.squares();
            Track.Reader all = new Track.Reader(area(squares, null));
            int read = 0;
            for (long at : squares.keySet()) {
                int x = (int) (at >> 32), y = (int) at;
                Track.Turnout turnout = all.turnout(x, y);
                if (turnout == Track.Turnout.NONE) {
                    continue;
                }
                read++;
                // Read first, with nothing read before it.
                Track.Turnout first = new Track.Reader(area(squares, null)).turnout(x, y);
                check(described(first).equals(described(turnout)), "the square at " + x + "," + y + " read first reads its turnout as " + described(turnout) + ": " + described(first));
                int built = new Track.Reader(area(squares, null)).read(x, y, new Track.Square());
                check(built == (turnout.unread == null ? Track.LAID : Track.FLAT), "the square at " + x + "," + y + " is built as its turnout is: " + built);
            }
            check(read > 30, "a fixture's turnout has its squares: " + read);
        }
        // The square of the yard's straight left with nothing on it under the points is built as part of the turnout.
        Track.Square hole = new Track.Square();
        check(new Track.Reader(area(yardTurnout(), null)).read(11, 9, hole) == Track.LAID && hole.lines + hole.arcs > 1,
                "the empty square under the yard's points is built as its turnout is: " + hole.lines + " lines and " + hole.arcs + " arcs");
    }

    private static void everyRailOfABuiltTurnoutHasASleeperUnderIt() {
        for (Named named : BUILT) {
            HashMap<Long, String[]> squares = named.fixture().squares();
            Track.Turnout turnout = new Track.Reader(area(squares, null)).turnout(named.x(), named.y());
            Built built = build(squares);
            check(built.low == 0, named.name() + ": every sleeper's top is as high as the rest: " + built.low + " are not");
            double looked = 0;
            for (float[] q : built.heads) {
                double x0 = (q[0] + q[9]) / 2, y0 = (q[1] + q[10]) / 2, x1 = (q[3] + q[6]) / 2, y1 = (q[4] + q[7]) / 2;
                double length = Math.hypot(x1 - x0, y1 - y0);
                int steps = (int) Math.ceil(length / 0.05);
                for (int k = 0; k < steps; k++) {
                    double px = x0 + (x1 - x0) * (k + 0.5) / steps, py = y0 + (y1 - y0) * (k + 0.5) / steps;
                    long at = key((int) Math.floor(px), (int) Math.floor(py));
                    if (!turnout.squares.contains(at) && squares.containsKey(at)) {
                        continue;
                    }
                    looked += length / steps;
                    double nearest = Double.MAX_VALUE;
                    for (float[] top : built.tieTops) {
                        nearest = Math.min(nearest, distance(top, px, py));
                    }
                    check(nearest <= 0.2, named.name() + ": the rail at " + px + "," + py + " is " + nearest + " from the nearest sleeper");
                }
            }
            check(looked > 30, named.name() + " has its rails looked at: " + looked + " m");
            for (int i = 0; i < built.tieTops.size(); i++) {
                for (int j = i + 1; j < built.tieTops.size(); j++) {
                    if (built.topOf.get(i) != built.topOf.get(j)) {
                        double over = overlap(built.tieTops.get(i), built.tieTops.get(j));
                        Tie one = built.topOf.get(i), two = built.topOf.get(j);
                        check(over < 1e-4, named.name() + ": the sleeper at " + one.x + "," + one.y + " lies over the one at " + two.x + "," + two.y + ", " + over + " m2");
                    }
                }
            }
        }
    }

    /** How far a point is from a quad in the map's plane: 0 inside it. */
    private static double distance(float[] q, double px, double py) {
        boolean inside = true;
        double sign = 0, nearest = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            double ex = q[j * 3] - q[i * 3], ey = q[j * 3 + 1] - q[i * 3 + 1], side = ex * (py - q[i * 3 + 1]) - ey * (px - q[i * 3]);
            if (Math.abs(side) > 1e-12) {
                if (sign == 0) {
                    sign = Math.signum(side);
                } else if (Math.signum(side) != sign) {
                    inside = false;
                }
            }
            double l2 = ex * ex + ey * ey, u = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - q[i * 3]) * ex + (py - q[i * 3 + 1]) * ey) / l2));
            nearest = Math.min(nearest, Math.hypot(q[i * 3] + u * ex - px, q[i * 3 + 1] + u * ey - py));
        }
        return inside ? 0 : nearest;
    }

    /** The area, in the map's plane, where two convex quads lie over each other. */
    private static double overlap(float[] a, float[] b) {
        ArrayList<double[]> polygon = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            polygon.add(new double[] {a[i * 3], a[i * 3 + 1]});
        }
        double turning = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            turning += b[i * 3] * b[j * 3 + 1] - b[j * 3] * b[i * 3 + 1];
        }
        for (int i = 0; i < 4 && !polygon.isEmpty(); i++) {
            int j = (i + 1) % 4;
            double ex = b[j * 3] - b[i * 3], ey = b[j * 3 + 1] - b[i * 3 + 1];
            ArrayList<double[]> kept = new ArrayList<>();
            for (int k = 0; k < polygon.size(); k++) {
                double[] p = polygon.get(k), n = polygon.get((k + 1) % polygon.size());
                double dp = Math.signum(turning) * (ex * (p[1] - b[i * 3 + 1]) - ey * (p[0] - b[i * 3]));
                double dn = Math.signum(turning) * (ex * (n[1] - b[i * 3 + 1]) - ey * (n[0] - b[i * 3]));
                if (dp >= 0) {
                    kept.add(p);
                }
                if ((dp >= 0) != (dn >= 0)) {
                    double f = dp / (dp - dn);
                    kept.add(new double[] {p[0] + (n[0] - p[0]) * f, p[1] + (n[1] - p[1]) * f});
                }
            }
            polygon = kept;
        }
        double area = 0;
        for (int k = 0; k < polygon.size(); k++) {
            double[] p = polygon.get(k), n = polygon.get((k + 1) % polygon.size());
            area += p[0] * n[1] - n[0] * p[1];
        }
        return Math.abs(area) / 2;
    }

    private static void everyRailOfATurnoutEndsOnAnotherOrAtABuffer() {
        for (Fixture fixture : FIXTURES) {
            HashMap<Long, String[]> squares = fixture.squares();
            Track.Reader reader = new Track.Reader(area(squares, null));
            ArrayList<float[]> lengths = new ArrayList<>();
            float top = Track.TIE_TOP + Track.RAIL_HIGH;
            Track.Builder builder = new Track.Builder(new Track.Sink() {
                @Override
                public float ground(float x, float y) {
                    return 0f;
                }

                @Override
                public void quad(float[] q, float[] along, int part) {
                    if (part == Track.RAIL && q[2] == top && q[5] == top && q[8] == top && q[11] == top) {
                        lengths.add(new float[] {(q[0] + q[9]) / 2f, (q[1] + q[10]) / 2f, (q[3] + q[6]) / 2f, (q[4] + q[7]) / 2f});
                    }
                }
            });
            for (long at : squares.keySet()) {
                int x = (int) (at >> 32), y = (int) at;
                if (reader.readAround(x, y) == Track.LAID) {
                    builder.build(reader, x, y);
                }
            }
            int looked = 0;
            for (int i = 0; i < lengths.size(); i++) {
                float[] length = lengths.get(i);
                for (int end = 0; end < 2; end++) {
                    float ex = length[end * 2], ey = length[end * 2 + 1];
                    looked++;
                    boolean met = false;
                    for (int j = 0; j < lengths.size() && !met; j++) {
                        float[] other = lengths.get(j);
                        double dx = other[2] - other[0], dy = other[3] - other[1];
                        double u = Math.max(0, Math.min(1, ((ex - other[0]) * dx + (ey - other[1]) * dy) / (dx * dx + dy * dy)));
                        met = j != i && Math.hypot(other[0] + u * dx - ex, other[1] + u * dy - ey) < 0.03;
                    }
                    if (met) {
                        continue;
                    }
                    boolean onPicture = false;
                    for (int cx = -1; cx <= 1; cx += 2) {
                        for (int cy = -1; cy <= 1; cy += 2) {
                            int qx = (int) Math.floor(ex + cx * 0.02), qy = (int) Math.floor(ey + cy * 0.02);
                            onPicture |= squares.containsKey(key(qx, qy)) && reader.read(qx, qy, new Track.Square()) == Track.FLAT;
                        }
                    }
                    if (onPicture) {
                        continue;
                    }
                    // A buffer: a straight rail that ends where the square on along it has no track.
                    double ax = length[2] - length[0], ay = length[3] - length[1], run = Math.hypot(ax, ay);
                    ax /= end == 0 ? -run : run;
                    ay /= end == 0 ? -run : run;
                    double eighths = Math.toDegrees(Math.atan2(ay, ax)) / 45;
                    boolean straight = Math.abs(eighths - Math.round(eighths)) < 1e-3;
                    long on = key((int) Math.floor(ex + ax * 0.05), (int) Math.floor(ey + ay * 0.05));
                    check(straight && !squares.containsKey(on), "a rail ends at " + ex + "," + ey + " on nothing" + (straight ? ", short of the rest of its line" : ""));
                }
            }
            check(looked > 0, "a fixture's rail ends are looked at");
        }
    }

    private static void aGapInAStraightIsBridged() {
        // As the map has it at 12651,4258: a square of a straight laid with nothing.
        HashMap<Long, String[]> squares = new HashMap<>();
        line(squares, Track.WITH_TIES, Track.NS, 11.5, 10, 0, 12, 12, null);
        squares.remove(key(11, 6));
        Built built = build(squares);
        checkUnbroken(built, (x, y) -> y > 0.001f && y < 12.999f, "a straight with a square left out");
        boolean tied = false;
        for (Tie tie : built.ties) {
            tied |= tie.y > 6 && tie.y < 7 && Math.abs(tie.area - WHOLE) < 1e-4f;
        }
        check(tied, "the square left out has whole sleepers");
    }

    private static void aSquareWithMoreTrackThanCanBeBuiltIsLeftThePicture() {
        HashMap<Long, String[]> squares = new HashMap<>();
        line(squares, Track.WITH_TIES, Track.NS, 11.5, 10, 0, 12, 12, null);
        for (int i = 0; i < Track.Square.MOST / 2; i++) {
            lay(squares, 11, 6, "industry_railroad_01_1");
        }
        Track.Reader reader = new Track.Reader(area(squares, null));
        check(reader.read(11, 6, new Track.Square()) == Track.FLAT && reader.full != null && reader.full.contains("11,6"),
                "a square with more pieces than it can be built of is left the picture it is, and says so: " + reader.full);
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
                if (Track.of(name) != null && n < pieces.length) {
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
        /** The sleeper each part of a top in tieTops is of, and how many flat parts of sleepers are not at the height of a top. */
        final ArrayList<Tie> topOf = new ArrayList<>();
        int low;
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
                    built.topOf.add(gather(built, q, along));
                } else if (q[2] == q[5] && q[5] == q[8] && q[8] == q[11]) {
                    built.low++;
                }
            }
        });
        Track.Reader reader = new Track.Reader(area(squares, null));
        for (Long at : squares.keySet()) {
            int x = (int) (at >> 32), y = (int) (long) at;
            check(reader.readAround(x, y) == Track.LAID, "the track at " + x + "," + y + " is built");
            builder.build(reader, x, y);
        }
        return built;
    }

    /** Adds a part of a sleeper's top to its sleeper, whose middle is where along and across its picture are both a half, and says which that is. */
    private static Tie gather(Built built, float[] q, float[] along) {
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
        return tie;
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
