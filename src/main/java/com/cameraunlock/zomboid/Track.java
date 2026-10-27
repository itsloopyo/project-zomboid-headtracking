package com.cameraunlock.zomboid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Railway track, built as rails on sleepers where the map lays it.
 *
 * The map paints track as floor pictures, industry_railroad_01 to _04, each the piece of a
 * stamp of several squares that falls on its square: a straight is three squares across, a
 * diagonal five or six, a bend nine to thirteen. Laid flat a picture has no rails, and its
 * sleepers stop square at its edges, so a bend breaks into steps where one square meets the
 * next. Here each sprite says which line or bend of its stamp it is, and the line and the bend
 * are worked out in the map's own terms: every square of a track works out the same line, and
 * what each builds of it meets what the next builds along the seam between them.
 *
 * The middle of a track is the middle of the band of squares the map lays it in. Its rails
 * are at standard gauge either side of that, inside the picture's own, which are two squares
 * apart. A bend is a circle meeting its two straights, as wide as the squares of its stamp hold.
 *
 * Sheet 01 is track with sleepers and 03 the rails of each of its pieces without them. Sheet
 * 02 is the wider bends of the diagonals one way, and 04 their rails alone. A rail alone whose
 * picture is the same on either side of its track (sided) is told which side by the track
 * around it. Where the map lays a straight as its two outer rows with no middle row between
 * them, its rails a square apart, the track's middle is the seam between the two. A wagon's
 * deck, where the map lays one over track, covers it. The pictures of a wagon's frame and
 * wheels on the ground beside and between its decks do not: the game draws the track there.
 */
final class Track {
    /** Which way a line runs: down the map, across it, and the diagonals along which x + y and x - y keep. */
    static final int NS = 0, EW = 1, NE = 2, NW = 3;
    /** For each way the normal of its lines: a line is where NX * x + NY * y is its constant. */
    private static final double[] NX = {1, 0, 1, 1}, NY = {0, 1, 1, -1};

    /** Standard gauge, between the inner faces of the heads, in tiles: a tile is a metre. */
    static final float GAUGE = 1.435f;
    /** An AREMA 115 lb rail: how wide its head, its web and its foot are and how high it stands. */
    static final float HEAD = 0.069f, WEB = 0.016f, FOOT = 0.14f, RAIL_HIGH = 0.168f;
    /** From the middle of the track to the middle of each rail. */
    static final float RAIL_OFF = GAUGE / 2f + HEAD / 2f;
    /**
     * Sleepers as American track has them: 8 feet 6 long, 9 inches wide and laid 19.5 inches
     * apart, which is the two to a square the map's picture paints. How far the top stands over
     * the ground and how far into it the foot goes.
     */
    static final float TIE_LONG = 2.59f, TIE_WIDE = 0.23f, TIE_APART = 0.495f, TIE_TOP = 0.09f, TIE_SUNK = 0.05f;
    /** How far apart a turnout's sleepers are at most along the outer rail of a route, so that none of the rail is more than 0.19 from one. */
    static final float TIE_APART_MOST = TIE_WIDE + 2 * 0.19f;
    /** How long a length of rail is at most along a straight, and round a bend. */
    static final float LINE_STEP = 0.5f, ARC_STEP = 0.2f;
    /**
     * A rail's section, from the edge of its foot to the edge of its head, one side of it: how far
     * out from its middle, and how high over the sleeper's top. The other side is the same.
     */
    static final float[] SECTION_OUT = {FOOT / 2f, FOOT / 2f, WEB / 2f, WEB / 2f, HEAD / 2f, HEAD / 2f},
            SECTION_UP = {0f, 0.011f, 0.033f, 0.12f, 0.128f, RAIL_HIGH};

    /** What a face built is. */
    static final int RAIL = 0, TIE = 1;

    /**
     * Where the game's own pictures are read for what is built: a sleeper's top from the north
     * one of the two the middle of a straight down the map paints, along its length and across
     * its top face, and the steel of a rail at one point of the rail of a straight's west square.
     */
    static final String TIE_SPRITE = "industry_railroad_01_1", RAIL_SPRITE = "industry_railroad_01_0";
    static final float TIE_A_FROM = 0.02f, TIE_A_TO = 0.98f, TIE_B_FROM = 0.20f, TIE_B_TO = 0.38f, RAIL_A = 0.22f, RAIL_B = 0.5f;

    /** Takes what a square of track is built of. */
    interface Sink {
        /** The height of what the track lies on at a point of the map. */
        float ground(float x, float y);

        /**
         * One face, four corners each (x, y, up) on the map. For a sleeper (TIE), where each
         * corner is on it: along its length from 0 to 1 and across it from 0 to 1, each corner
         * two floats of along. A rail's face has none.
         */
        void quad(float[] corners, float[] along, int part);
    }

    /** What one sprite is of a track. */
    static final class Piece {
        /** For a straight, its way and its middle as a line's constant in its square's terms; NS etc. -1 for a piece of a bend. */
        final int way;
        final float c;
        /** For a piece of a bend, which bend and where its square is from the bend's first square; null for a straight. */
        final Bend bend;
        final int dx, dy;
        /** Whether the picture paints sleepers: the rails alone of 03, 04 and two of 01 do not. */
        final boolean ties;
        /**
         * Whether it is a straight rail alone painted the same either side of its track. Its c is
         * the middle's with the track towards higher x (NS) or y (EW), and two less the other way.
         */
        final boolean sided;

        private Piece(int way, float c, Bend bend, int dx, int dy, boolean ties) {
            this.way = way;
            this.c = c;
            this.bend = bend;
            this.dx = dx;
            this.dy = dy;
            this.ties = ties;
            sided = false;
        }

        private Piece(int way, float c) {
            this.way = way;
            this.c = c;
            bend = null;
            dx = dy = 0;
            ties = false;
            sided = true;
        }

        @Override
        public String toString() {
            return this == NOTHING ? "nothing" : this == WAGON_FLOOR ? "a wagon's deck" : bend != null ? "bend " + bend.name + " at " + dx + "," + dy + (ties ? "" : ", rails alone")
                    : "straight " + way + " c " + c + (ties ? "" : ", rails alone") + (sided ? ", sided" : "");
        }
    }

    /**
     * A bend: two lines, each in its first square's terms, the way along each from where they
     * cross towards its straight, and the radius it turns on. The arc meets each line where it
     * leaves it.
     */
    static final class Bend {
        final String name;
        final int wayOne, wayTwo;
        final double cOne, cTwo;
        /** The centre of the arc and where it meets each line, in its first square's terms. */
        final double centreX, centreY, oneX, oneY, twoX, twoY;
        /** The ways towards each straight, as unit vectors. */
        final double alongOneX, alongOneY, alongTwoX, alongTwoY;
        final double radius, startAngle, sweep;
        /** Where each of its squares is from its first's, x then y. */
        int[] places = {};

        Bend(String name, int wayOne, double cOne, int compassOne, int wayTwo, double cTwo, int compassTwo, double radius) {
            this.name = name;
            this.wayOne = wayOne;
            this.wayTwo = wayTwo;
            this.cOne = cOne;
            this.cTwo = cTwo;
            this.radius = radius;
            double det = NX[wayOne] * NY[wayTwo] - NY[wayOne] * NX[wayTwo];
            double crossX = (cOne * NY[wayTwo] - NY[wayOne] * cTwo) / det, crossY = (NX[wayOne] * cTwo - cOne * NX[wayTwo]) / det;
            double[] one = compass(compassOne), two = compass(compassTwo);
            if (Math.abs(one[0] * NX[wayOne] + one[1] * NY[wayOne]) > 1e-9 || Math.abs(two[0] * NX[wayTwo] + two[1] * NY[wayTwo]) > 1e-9) {
                throw new IllegalArgumentException("bend " + name + ": a way along does not run along its line");
            }
            alongOneX = one[0];
            alongOneY = one[1];
            alongTwoX = two[0];
            alongTwoY = two[1];
            double cos = one[0] * two[0] + one[1] * two[1];
            double half = Math.acos(cos) / 2;
            double bisX = one[0] + two[0], bisY = one[1] + two[1], bis = Math.hypot(bisX, bisY);
            double toCentre = radius / Math.sin(half), toTangent = radius / Math.tan(half);
            centreX = crossX + bisX / bis * toCentre;
            centreY = crossY + bisY / bis * toCentre;
            oneX = crossX + one[0] * toTangent;
            oneY = crossY + one[1] * toTangent;
            twoX = crossX + two[0] * toTangent;
            twoY = crossY + two[1] * toTangent;
            startAngle = Math.atan2(oneY - centreY, oneX - centreX);
            double end = Math.atan2(twoY - centreY, twoX - centreX);
            sweep = Math.IEEEremainder(end - startAngle, 2 * Math.PI);
        }
    }

    /** The eight ways, clockwise from north (towards lower y). */
    static final int N = 0, NEAST = 1, E = 2, SE = 3, S = 4, SW = 5, W = 6, NWEST = 7;

    private static double[] compass(int way) {
        double r = Math.sqrt(0.5);
        return switch (way) {
            case N -> new double[] {0, -1};
            case NEAST -> new double[] {r, -r};
            case E -> new double[] {1, 0};
            case SE -> new double[] {r, r};
            case S -> new double[] {0, 1};
            case SW -> new double[] {-r, r};
            case W -> new double[] {-1, 0};
            case NWEST -> new double[] {-r, -r};
            default -> throw new IllegalArgumentException("way " + way);
        };
    }

    private static final HashMap<String, Piece> PIECES = new HashMap<>();
    static final String WITH_TIES = "industry_railroad_01_", WIDE_BENDS = "industry_railroad_02_", RAILS = "industry_railroad_03_", WIDE_RAILS = "industry_railroad_04_",
            WAGONS = "industry_railroad_05_";
    /** A sprite of the sheets of track whose picture paints none of it, and a wagon's deck, which covers the track under it. */
    static final Piece NOTHING = new Piece(-1, 0f, null, 0, 0, false), WAGON_FLOOR = new Piece(-1, 0f, null, 0, 0, false);

    /**
     * The bends. Each line is given in the terms of the bend's first sprite's square, as the
     * straights that meet it are. Each radius is the widest whose rails stay on the squares
     * of its stamp, where the map lays the bend's pieces and its straights do not reach.
     */
    static final Bend A = new Bend("A", NE, -0.5, SW, NS, 0.5, N, 2.4), B = new Bend("B", NE, -0.5, NEAST, NS, -0.5, S, 1.4),
            C = new Bend("C", NE, -0.5, SW, EW, -0.5, E, 1.4), D = new Bend("D", NE, 1.5, NEAST, EW, 0.5, W, 2.4),
            WIDE_E = new Bend("E", NW, 2, NWEST, NS, 3.5, S, 1.2), WIDE_F = new Bend("F", NW, 2, NWEST, EW, 1.5, E, 1.2),
            WIDE_G = new Bend("G", NW, 2, SE, NS, 0.5, N, 3.4), WIDE_H = new Bend("H", NW, 1, SE, EW, 0.5, W, 3.4);

    private static void straight(int way, float c, int... places) {
        for (int place : places) {
            PIECES.put(WITH_TIES + place, new Piece(way, c, null, 0, 0, true));
            PIECES.put(RAILS + place, new Piece(way, c, null, 0, 0, false));
        }
    }

    /** A bend's sprites, each its place on the sheet then where its square is from the first's. */
    private static void bend(Bend bend, String sheet, String railsAlone, int... placeDxDy) {
        bend.places = new int[placeDxDy.length / 3 * 2];
        for (int i = 0; i < placeDxDy.length; i += 3) {
            PIECES.put(sheet + placeDxDy[i], new Piece(-1, 0f, bend, placeDxDy[i + 1], placeDxDy[i + 2], true));
            PIECES.put(railsAlone + placeDxDy[i], new Piece(-1, 0f, bend, placeDxDy[i + 1], placeDxDy[i + 2], false));
            bend.places[i / 3 * 2] = placeDxDy[i + 1];
            bend.places[i / 3 * 2 + 1] = placeDxDy[i + 2];
        }
    }

    private static void sided(String name, int way) {
        PIECES.put(name, new Piece(way, 1.5f));
    }

    static {
        // Straights, three squares across (the diagonals five or six), each square's piece with
        // the line's constant in its own terms: the middle of the band.
        straight(NS, 1.5f, 0);
        straight(NS, 0.5f, 1);
        straight(NS, -0.5f, 2);
        straight(EW, 1.5f, 5);
        straight(EW, 0.5f, 4);
        straight(EW, -0.5f, 3);
        straight(NE, 3.5f, 12, 54);
        straight(NE, 2.5f, 13);
        straight(NE, 1.5f, 10);
        straight(NE, 0.5f, 11);
        straight(NE, -0.5f, 8);
        straight(NE, -1.5f, 9);
        straight(NW, 2f, 16);
        straight(NW, 1f, 17, 21, 23);
        straight(NW, 0f, 18);
        straight(NW, -1f, 19, 15, 22);
        straight(NW, -2f, 20);
        // The rails of a straight alone, painted alike on either side of the track.
        for (String name : new String[] {WITH_TIES + 6, RAILS + 0, RAILS + 2, RAILS + 6}) {
            sided(name, NS);
        }
        for (String name : new String[] {WITH_TIES + 7, RAILS + 3, RAILS + 5, RAILS + 7}) {
            sided(name, EW);
        }
        bend(A, WITH_TIES, RAILS, 24, 0, 0, 25, -1, -1, 26, -2, -2, 27, 1, 0, 28, 0, -1, 29, -1, -2, 30, 1, -1, 31, 0, -2, 41, 1, -2);
        bend(B, WITH_TIES, RAILS, 46, 0, 0, 48, -2, 1, 49, -2, 0, 50, -1, 1, 51, 0, 1, 52, -1, 0, 53, -2, -1, 55, -1, -1);
        bend(C, WITH_TIES, RAILS, 47, 0, 0, 56, -1, -1, 57, -2, -2, 58, -1, -2, 59, 0, -1, 60, 1, 0, 61, 1, -1, 62, 0, -2, 63, 1, -2);
        bend(D, WITH_TIES, RAILS, 32, 0, 0, 33, 1, 1, 34, 0, -1, 35, 1, 0, 36, 2, 1, 37, 0, -2, 38, 1, -1, 39, 2, 0, 40, 0, 1);
        bend(WIDE_E, WIDE_BENDS, WIDE_RAILS, 0, 0, 0, 1, 1, -1, 2, 2, -2, 3, 1, 0, 4, 2, -1, 5, 1, 1, 6, 2, 0, 7, 3, -1, 8, 2, 1, 9, 3, 0,
                10, 3, 1, 11, 4, 0, 12, 4, 1);
        bend(WIDE_F, WIDE_BENDS, WIDE_RAILS, 16, 0, 0, 17, 1, -1, 18, 2, -2, 19, 1, 0, 20, 2, -1, 21, 1, 1, 22, 2, 0, 23, 3, -1, 24, 2, 1,
                25, 3, 0, 26, 3, 1, 27, 3, 2, 28, 2, 2);
        bend(WIDE_G, WIDE_BENDS, WIDE_RAILS, 32, 0, 0, 33, 1, -1, 34, 2, -2, 35, 0, -1, 36, 1, -2, 37, -1, -1, 38, 0, -2, 39, 1, -3, 40, -1, -2,
                41, 0, -3, 42, -1, -3);
        bend(WIDE_H, WIDE_BENDS, WIDE_RAILS, 45, 0, 0, 46, 1, -1, 47, 0, -1, 48, 1, 2, 49, 2, 1, 50, 3, 0, 51, 1, 1, 52, 2, 0, 53, 0, 1, 54, 1, 0);
        // Two pixels at the top of its picture.
        PIECES.put(RAILS + 45, NOTHING);
        for (int place : new int[] {22, 23, 38, 39, 44, 45, 46, 47}) {
            PIECES.put(WAGONS + place, WAGON_FLOOR);
        }
    }

    private Track() {}

    /** What piece of track a sprite is, or null for none. */
    static Piece of(String name) {
        return name == null ? null : PIECES.get(name);
    }

    static Iterable<String> names() {
        return PIECES.keySet();
    }

    /** What a square's track is: built, left the picture it is, waiting on squares not loaded, or covered by a wagon. */
    static final int LAID = 0, FLAT = 1, WAITING = 2, COVERED = 3;
    /** How far along and across a sided rail's track the squares are that tell its side. */
    static final int SIDE_ALONG = 4, SIDE_ACROSS = 2;

    /** The map's track. */
    interface Area {
        /** Puts the pieces of track on the ground square at (x, y) into pieces, as many as it holds, and says how many, or -1 for a square not loaded. */
        int pieces(int x, int y, Piece[] pieces);
    }

    /** The squares round one being built, as a Reader has read them. */
    interface Around {
        /** What the square at (x, y) builds, or null where it builds no track. */
        Square at(int x, int y);
    }

    /** The squares next to a square: north, west, east, south. */
    static final int[] NEXT_X = {0, -1, 1, 0}, NEXT_Y = {-1, 0, 0, 1};

    /** How many squares either way round a square a sleeper of its can have its middle on. */
    static final int AROUND = 2;

    /**
     * How far round a square where the pieces of a switch meet the squares of its turnout reach,
     * how many squares a turnout can have before it is left the picture it is, the narrowest a
     * turnout's route may turn on and how finely its radius is tried, and how near to a route's
     * middle a rail alone of the picture must be for the route to be the one it paints.
     */
    static final int TURNOUT_REACH = 5, TURNOUT_MOST = 600;
    static final double ROUTE_TIGHTEST = 1.5, ROUTE_STEP = 0.05, PAINTED_NEAR = 2.0;
    /** How far round a turnout's squares a change to the map has its turnout read again. */
    static final int TURNOUT_KEPT_ROUND = 2;

    /** Works out what each square of the map's track builds. */
    static final class Reader implements Around {
        private final Area area;
        private final Piece[] here = new Piece[Square.MOST], near = new Piece[Square.MOST];
        private float shift;
        private static final int SIDE = 2 * AROUND + 1;
        private final Square[] around = new Square[SIDE * SIDE];
        private final int[] read = new int[SIDE * SIDE];
        private int aroundX, aroundY;

        Reader(Area area) {
            this.area = area;
            for (int i = 0; i < around.length; i++) {
                around[i] = new Square();
            }
        }

        /**
         * Reads the square at (x, y), which is loaded, and those round it whose sleepers may
         * reach it, and says what it is: see read. It waits while any of those is not loaded.
         */
        int readAround(int x, int y) {
            aroundX = x;
            aroundY = y;
            for (int dy = -AROUND; dy <= AROUND; dy++) {
                for (int dx = -AROUND; dx <= AROUND; dx++) {
                    int i = (dy + AROUND) * SIDE + dx + AROUND;
                    read[i] = area.pieces(x + dx, y + dy, near) < 0 ? WAITING : read(x + dx, y + dy, around[i]);
                }
            }
            int centre = read[AROUND * SIDE + AROUND];
            if (centre != LAID) {
                return centre;
            }
            for (int status : read) {
                if (status == WAITING) {
                    return WAITING;
                }
            }
            return LAID;
        }

        /** What the square last read round builds. */
        Square square() {
            return around[AROUND * SIDE + AROUND];
        }

        @Override
        public Square at(int x, int y) {
            int dx = x - aroundX, dy = y - aroundY;
            if (Math.abs(dx) > AROUND || Math.abs(dy) > AROUND) {
                throw new IllegalArgumentException(x + "," + y + " is not round " + aroundX + "," + aroundY);
            }
            int i = (dy + AROUND) * SIDE + dx + AROUND;
            return read[i] == LAID && (around[i].lines > 0 || around[i].arcs > 0) ? around[i] : null;
        }

        /**
         * Fills square with what the square at (x, y), which is loaded, builds, and says LAID;
         * or says why it builds nothing. A square any of whose pieces cannot be read is left
         * the picture it is, all of it.
         */
        int read(int x, int y, Square square) {
            square.clear();
            int n = area.pieces(x, y, here);
            if (hasWagonFloor(here, n)) {
                return COVERED;
            }
            if (n == 0) {
                return hole(x, y, square);
            }
            if (n > Square.MOST / 2) {
                full("the square at " + x + "," + y + " has " + n + " pieces of track");
                return FLAT;
            }
            Turnout turnout = turnout(x, y);
            if (turnout == Turnout.AWAITED) {
                return WAITING;
            }
            if (turnout != Turnout.NONE) {
                return turnout.fill(square);
            }
            n = area.pieces(x, y, here);
            for (int i = 0; i < n; i++) {
                Piece piece = here[i];
                shift = 0f;
                int read = piece.sided ? side(piece, x, y) : piece.bend == null && (piece.way == NS || piece.way == EW) ? narrow(piece, x, y) : LAID;
                if (read != LAID) {
                    return read;
                }
                square.add(piece, x, y, shift);
            }
            return LAID;
        }

        /**
         * A sided rail's side: the track around it has a straight of the same way whose middle
         * is the one that side gives it, or another sided rail two squares across that side.
         */
        private int side(Piece sided, int x, int y) {
            int own = sided.way == NS ? x : y;
            boolean higher = false, lower = false;
            for (int along = -SIDE_ALONG; along <= SIDE_ALONG; along++) {
                for (int across = -SIDE_ACROSS; across <= SIDE_ACROSS; across++) {
                    int n = sided.way == NS ? area.pieces(x + across, y + along, near) : area.pieces(x + along, y + across, near);
                    if (n < 0) {
                        return WAITING;
                    }
                    for (int i = 0; i < n; i++) {
                        Piece p = near[i];
                        if (p.bend != null || p.way != sided.way) {
                            continue;
                        }
                        double middle = own + across + p.c;
                        higher |= p.sided ? across == 2 : middle == own + sided.c;
                        lower |= p.sided ? across == -2 : middle == own + sided.c - 2;
                    }
                }
            }
            if (higher == lower) {
                return FLAT;
            }
            shift = higher ? 0f : -2f;
            return LAID;
        }

        /**
         * Whether a straight down or across the map is one of the two outer rows of a track laid
         * without its middle row: the next square across has a straight whose middle is a
         * square nearer, from the other side. The middle is then the seam between them.
         */
        private int narrow(Piece piece, int x, int y) {
            for (int across = -1; across <= 1; across += 2) {
                int n = piece.way == NS ? area.pieces(x + across, y, near) : area.pieces(x, y + across, near);
                if (n < 0) {
                    return WAITING;
                }
                for (int i = 0; i < n; i++) {
                    Piece p = near[i];
                    if (p.bend == null && p.way == piece.way && !p.sided && across + p.c == piece.c - across) {
                        shift = -across / 2f;
                    }
                }
            }
            return LAID;
        }

        /**
         * The turnout of each square read since the map was last read afresh, and those read
         * before that, which stand while their squares and those round them are as they were.
         */
        private final HashMap<Long, Turnout> turnouts = new HashMap<>(), kept = new HashMap<>();
        private final HashMap<Long, Boolean> switches = new HashMap<>(), nears = new HashMap<>();
        private final Piece[] other = new Piece[Square.MOST], holeA = new Piece[Square.MOST], holeB = new Piece[Square.MOST],
                bends = new Piece[Square.MOST];
        private final Square scratch = new Square();
        /** The first time a square or a turnout has more than a square can be built of, which it was; null till then. */
        String full;

        /** Reads the map afresh from now on, as it may have changed since: see turnouts. */
        void forget() {
            turnouts.clear();
            switches.clear();
            nears.clear();
        }

        private void full(String what) {
            if (full == null) {
                full = what;
            }
        }

        private static boolean hasWagonFloor(Piece[] pieces, int n) {
            for (int i = 0; i < n; i++) {
                if (pieces[i] == WAGON_FLOOR) {
                    return true;
                }
            }
            return false;
        }

        private static long key(int x, int y) {
            return (long) x << 32 | (y & 0xffffffffL);
        }

        /**
         * What a square with no track of its own builds: the turnout of the squares either side of
         * it, across the map or down it, where both are squares of that one turnout and it is
         * built; or a straight across or down the map the squares either side of it along it both
         * have. Anything else builds nothing there.
         */
        private int hole(int x, int y, Square square) {
            for (int step = 0; step < 2; step++) {
                int dx = 1 - step, dy = step;
                int a = area.pieces(x - dx, y - dy, holeA), b = a == 0 ? 0 : area.pieces(x + dx, y + dy, holeB);
                if (a == 0 || b == 0) {
                    continue;
                }
                if (a < 0 || b < 0) {
                    return WAITING;
                }
                if (hasWagonFloor(holeA, a) || hasWagonFloor(holeB, b)) {
                    continue;
                }
                Turnout one = turnout(x - dx, y - dy), two = turnout(x + dx, y + dy);
                if (one == Turnout.AWAITED || two == Turnout.AWAITED) {
                    return WAITING;
                }
                if (one != Turnout.NONE && one == two) {
                    return one.unread == null ? one.fill(square) : LAID;
                }
                if (one != Turnout.NONE || two != Turnout.NONE) {
                    continue;
                }
                int way = step == 0 ? EW : NS;
                for (int i = 0; i < a; i++) {
                    if (holeA[i].bend != null || !holeA[i].ties || holeA[i].way != way) {
                        continue;
                    }
                    double c = straightC(holeA[i], x - dx, y - dy);
                    if (Double.isNaN(c)) {
                        return WAITING;
                    }
                    for (int j = 0; j < b; j++) {
                        if (holeB[j].bend == null && holeB[j].ties && holeB[j].way == way) {
                            double there = straightC(holeB[j], x + dx, y + dy);
                            if (Double.isNaN(there)) {
                                return WAITING;
                            }
                            if (there == c) {
                                square.line(way, c, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, true);
                            }
                        }
                    }
                }
                if (square.lines > 0) {
                    return LAID;
                }
            }
            return LAID;
        }

        /** The constant on the map of the line a straight with sleepers on (x, y) is of, or NaN while a square it needs is not loaded. */
        private double straightC(Piece p, int x, int y) {
            shift = 0f;
            if ((p.way == NS || p.way == EW) && narrow(p, x, y) == WAITING) {
                return Double.NaN;
            }
            return p.c + shift + NX[p.way] * x + NY[p.way] * y;
        }

        /**
         * Whether the square at (x, y), which has track, builds the square at (hx, hy) next to it
         * as well as its own: 1 where that square has no track of its own but builds some, and
         * this is the first of the squares next to it, north, west, east then south, that builds
         * any; 0 where it does not; WAITING while a square that says is not loaded.
         */
        int builds(int x, int y, int hx, int hy) {
            int n = area.pieces(hx, hy, other);
            if (n != 0) {
                return n < 0 ? WAITING : 0;
            }
            int hole = read(hx, hy, scratch);
            if (hole == WAITING) {
                return WAITING;
            }
            if (scratch.lines == 0 && scratch.arcs == 0) {
                return 0;
            }
            for (int i = 0; i < NEXT_X.length; i++) {
                int nx = hx + NEXT_X[i], ny = hy + NEXT_Y[i];
                int m = area.pieces(nx, ny, other);
                if (m < 0) {
                    return WAITING;
                }
                if (m == 0) {
                    continue;
                }
                int read = read(nx, ny, scratch);
                if (read == WAITING) {
                    return WAITING;
                }
                if (read == LAID && (scratch.lines > 0 || scratch.arcs > 0)) {
                    return nx == x && ny == y ? 1 : 0;
                }
            }
            return 0;
        }

        /**
         * Whether the square at (x, y) is where the pieces of a switch meet: a bend's rails alone
         * on it, with track with sleepers, another bend's pieces or a straight that is not one of
         * that bend's own, or with squares of that bend's that do not have it; or null for a
         * square not loaded. A bend laid whole, alone or with sleepers, is not.
         */
        private Boolean isSwitch(int x, int y) {
            long k = key(x, y);
            Boolean known = switches.get(k);
            if (known != null) {
                return known;
            }
            int n = area.pieces(x, y, other);
            if (n < 0) {
                return null;
            }
            Piece[] pieces = Arrays.copyOf(other, n);
            Piece first = null;
            for (Piece p : pieces) {
                if (p.bend != null && !p.ties) {
                    first = p;
                }
            }
            if (first == null) {
                switches.put(k, false);
                return false;
            }
            Bend bend = first.bend;
            int ox = x - first.dx, oy = y - first.dy;
            boolean is = false;
            for (Piece p : pieces) {
                if (p.bend != null) {
                    is |= p.bend != bend || x - p.dx != ox || y - p.dy != oy;
                } else if (p.ties) {
                    is = true;
                } else if (p.way >= 0 && !p.sided) {
                    double c = p.c + NX[p.way] * x + NY[p.way] * y;
                    is |= !(p.way == bend.wayOne && c == bend.cOne + NX[p.way] * ox + NY[p.way] * oy)
                            && !(p.way == bend.wayTwo && c == bend.cTwo + NX[p.way] * ox + NY[p.way] * oy);
                }
            }
            for (int i = 0; i < bend.places.length && !is; i += 2) {
                int m = area.pieces(ox + bend.places[i], oy + bend.places[i + 1], other);
                if (m < 0) {
                    return null;
                }
                boolean has = false;
                for (int q = 0; q < m; q++) {
                    has |= other[q].bend == bend && other[q].dx == bend.places[i] && other[q].dy == bend.places[i + 1];
                }
                is = !has;
            }
            switches.put(k, is);
            return is;
        }

        /**
         * Whether a switch is within TURNOUT_REACH of the square at (x, y), or null if none of the
         * loaded squares is one and a square that may be one is not loaded.
         */
        private Boolean nearSwitch(int x, int y) {
            long k = key(x, y);
            Boolean known = nears.get(k);
            if (known != null) {
                return known;
            }
            boolean waiting = false;
            for (int dy = -TURNOUT_REACH; dy <= TURNOUT_REACH; dy++) {
                for (int dx = -TURNOUT_REACH; dx <= TURNOUT_REACH; dx++) {
                    Boolean is = isSwitch(x + dx, y + dy);
                    if (is == null) {
                        waiting = true;
                    } else if (is) {
                        nears.put(k, true);
                        return true;
                    }
                }
            }
            if (waiting) {
                return null;
            }
            nears.put(k, false);
            return false;
        }

        /** Whether the square at (x, y), which is loaded, has a piece of a bend. */
        private boolean hasBend(int x, int y) {
            int n = area.pieces(x, y, bends);
            for (int i = 0; i < n; i++) {
                if (bends[i].bend != null) {
                    return true;
                }
            }
            return false;
        }

        /**
         * The turnout the square at (x, y), which has track, is a square of: NONE for a square of
         * no turnout, AWAITED while a square it needs is not loaded. Two squares with track next
         * to each other are joined where both are near a switch, or both have pieces of bends; a
         * turnout is the squares so joined to each other, all one connected whole, among which
         * one at least is near a switch. Every square of it reads the same turnout, whichever is
         * read first.
         */
        Turnout turnout(int x, int y) {
            long k = key(x, y);
            Turnout known = turnouts.get(k);
            if (known != null) {
                return known;
            }
            Turnout before = kept.get(k);
            if (before != null) {
                long now = signature(before.round);
                if (now == WAITING_SIGNATURE) {
                    return Turnout.AWAITED;
                }
                if (now == before.signature) {
                    for (long at : before.squares) {
                        turnouts.put(at, before);
                    }
                    return before;
                }
                for (long at : before.squares) {
                    kept.remove(at);
                }
            }
            Boolean near = nearSwitch(x, y);
            if (near == null) {
                return Turnout.AWAITED;
            }
            if (!near && !hasBend(x, y)) {
                turnouts.put(k, Turnout.NONE);
                return Turnout.NONE;
            }
            HashSet<Long> squares = new HashSet<>();
            ArrayDeque<Long> next = new ArrayDeque<>();
            squares.add(k);
            next.add(k);
            boolean anyNear = false;
            while (!next.isEmpty() && squares.size() <= TURNOUT_MOST) {
                long at = next.poll();
                int ax = (int) (at >> 32), ay = (int) at;
                Boolean nearHere = nearSwitch(ax, ay);
                if (nearHere == null) {
                    return Turnout.AWAITED;
                }
                anyNear |= nearHere;
                boolean bendHere = hasBend(ax, ay);
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        long nk = key(ax + dx, ay + dy);
                        if (squares.contains(nk)) {
                            continue;
                        }
                        int m = area.pieces(ax + dx, ay + dy, other);
                        if (m < 0) {
                            return Turnout.AWAITED;
                        }
                        if (m == 0) {
                            continue;
                        }
                        boolean joins = bendHere && hasBend(ax + dx, ay + dy);
                        if (!joins && nearHere) {
                            Boolean nearThere = nearSwitch(ax + dx, ay + dy);
                            if (nearThere == null) {
                                return Turnout.AWAITED;
                            }
                            joins = nearThere;
                        }
                        if (joins) {
                            squares.add(nk);
                            next.add(nk);
                        }
                    }
                }
            }
            if (!anyNear && next.isEmpty()) {
                for (long at : squares) {
                    turnouts.put(at, Turnout.NONE);
                }
                return Turnout.NONE;
            }
            Turnout turnout = squares.size() > TURNOUT_MOST ? new Turnout(null, null, "more than " + TURNOUT_MOST + " squares") : new TurnoutReader(squares).read();
            if (turnout == Turnout.AWAITED) {
                return turnout;
            }
            turnout.squares = squares;
            HashSet<Long> round = new HashSet<>();
            for (long at : squares) {
                int ax = (int) (at >> 32), ay = (int) at;
                for (int dy = -TURNOUT_KEPT_ROUND; dy <= TURNOUT_KEPT_ROUND; dy++) {
                    for (int dx = -TURNOUT_KEPT_ROUND; dx <= TURNOUT_KEPT_ROUND; dx++) {
                        round.add(key(ax + dx, ay + dy));
                    }
                }
            }
            turnout.round = round.stream().mapToLong(Long::longValue).sorted().toArray();
            turnout.signature = signature(turnout.round);
            for (long at : squares) {
                turnouts.put(at, turnout);
                if (turnout.signature != WAITING_SIGNATURE) {
                    kept.put(at, turnout);
                }
            }
            return turnout;
        }

        private static final long WAITING_SIGNATURE = Long.MIN_VALUE;

        /** What the squares hold, summed, or WAITING_SIGNATURE while any of them is not loaded. */
        private long signature(long[] squares) {
            long sum = 17;
            for (long at : squares) {
                int n = area.pieces((int) (at >> 32), (int) at, other);
                if (n < 0) {
                    return WAITING_SIGNATURE;
                }
                sum = sum * 31 + n;
                for (int i = 0; i < n; i++) {
                    sum = sum * 31 + System.identityHashCode(other[i]);
                }
            }
            return sum == WAITING_SIGNATURE ? sum + 1 : sum;
        }

        /** Reads a turnout's squares: see Turnout. */
        private final class TurnoutReader {
            private final HashSet<Long> squares;
            /** The squares with pieces no line of track with sleepers accounts for, of those the ones with rails alone, and those under a wagon's deck. */
            private final HashSet<Long> unread = new HashSet<>(), alone = new HashSet<>(), covered = new HashSet<>();
            private final ArrayList<Line> lines = new ArrayList<>();
            private final ArrayList<Route> routes = new ArrayList<>();

            TurnoutReader(HashSet<Long> squares) {
                this.squares = squares;
            }

            Turnout read() {
                TreeMap<double[], ArrayList<Long>> found = new TreeMap<>(
                        Comparator.comparingDouble((double[] line) -> line[0]).thenComparingDouble(line -> line[1]));
                for (long at : squares) {
                    int x = (int) (at >> 32), y = (int) at;
                    Piece[] pieces = Arrays.copyOf(here, area.pieces(x, y, here));
                    for (Piece p : pieces) {
                        if (p == WAGON_FLOOR) {
                            covered.add(at);
                        }
                        if (p == NOTHING || p == WAGON_FLOOR) {
                            continue;
                        }
                        if (p.bend != null || !p.ties) {
                            unread.add(at);
                            if (!p.ties) {
                                alone.add(at);
                            }
                            continue;
                        }
                        double c = straightC(p, x, y);
                        if (Double.isNaN(c)) {
                            return Turnout.AWAITED;
                        }
                        found.computeIfAbsent(new double[] {p.way, c}, line -> new ArrayList<>()).add(at);
                    }
                }
                for (Map.Entry<double[], ArrayList<Long>> entry : found.entrySet()) {
                    Line line = new Line((int) entry.getKey()[0], entry.getKey()[1]);
                    for (long at : entry.getValue()) {
                        int x = (int) (at >> 32), y = (int) at;
                        // A straight down or across lies along the whole of each of its squares; a diagonal along those its middle crosses.
                        double[] part = line.way == NS || line.way == EW ? new double[] {Math.min(along(line, x, y), along(line, x + 1, y + 1)),
                            Math.max(along(line, x, y), along(line, x + 1, y + 1))}
                                : Builder.clip(line.footX, line.footY, line.dx, line.dy, x, y, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
                        if (part != null) {
                            line.lo = Math.min(line.lo, part[0]);
                            line.hi = Math.max(line.hi, part[1]);
                        }
                    }
                    if (line.lo >= line.hi) {
                        continue;
                    }
                    int loOn = runsOn(line, line.lo - 0.25), hiOn = runsOn(line, line.hi + 0.25);
                    if (loOn < 0 || hiOn < 0) {
                        return Turnout.AWAITED;
                    }
                    line.loOpen = loOn == 1;
                    line.hiOpen = hiOn == 1;
                    line.from = line.loOpen ? Double.NEGATIVE_INFINITY : line.lo;
                    line.to = line.hiOpen ? Double.POSITIVE_INFINITY : line.hi;
                    lines.add(line);
                }
                // A line running on through the turnout lies under those that end in it, so its sleepers are laid whole and theirs cut where they meet it.
                lines.sort(Comparator.comparingInt(line -> line.loOpen && line.hiOpen ? 0 : 1));
                for (int i = 0; i < lines.size(); i++) {
                    for (int side = -1; side <= 1; side += 2) {
                        if (!(side < 0 ? lines.get(i).loOpen : lines.get(i).hiOpen)) {
                            for (int j = 0; j < lines.size(); j++) {
                                if (j != i && lines.get(j).way != lines.get(i).way) {
                                    for (int sense = -1; sense <= 1; sense += 2) {
                                        route(i, side, j, sense);
                                    }
                                }
                            }
                        }
                    }
                }
                if (lines.size() > Square.MOST || routes.size() > Square.MOST) {
                    String why = lines.size() + " lines and " + routes.size() + " routes, more than a square can be built of";
                    full("the turnout with its piece at " + (int) (squares.iterator().next() >> 32) + "," + (int) (long) squares.iterator().next() + " has " + why);
                    return new Turnout(lines, routes, why);
                }
                if (routes.isEmpty()) {
                    return new Turnout(lines, routes, "no route turns off its lines");
                }
                for (long at : unread) {
                    if (!painted(at)) {
                        return new Turnout(lines, routes, "the piece at " + (int) (at >> 32) + "," + (int) at + " is of no line or route");
                    }
                }
                return new Turnout(lines, routes, null);
            }

            private double along(Line line, double x, double y) {
                return (x - line.footX) * line.dx + (y - line.footY) * line.dy;
            }

            /**
             * Whether a line runs on out of the turnout at along on it: 1 where a square there, by
             * its middle or either side of it, is not the turnout's and has the line, 0 where none
             * does, -1 for a square not loaded.
             */
            private int runsOn(Line line, double along) {
                int mx = (int) Math.floor(line.footX + line.dx * along), my = (int) Math.floor(line.footY + line.dy * along);
                for (int y = my - 1; y <= my + 1; y++) {
                    for (int x = mx - 1; x <= mx + 1; x++) {
                        if (squares.contains(key(x, y))) {
                            continue;
                        }
                        int n = area.pieces(x, y, other);
                        if (n < 0) {
                            return -1;
                        }
                        for (Piece p : Arrays.copyOf(other, n)) {
                            if (p.bend != null || !p.ties || p.way != line.way) {
                                continue;
                            }
                            double c = straightC(p, x, y);
                            if (Double.isNaN(c)) {
                                return -1;
                            }
                            if (c == line.c) {
                                return 1;
                            }
                        }
                    }
                }
                return 0;
            }

            /**
             * The route, if the turnout's squares hold one, that leaves the line one at its end on
             * side (towards lower along or higher) and turns onto the line two, along it towards
             * sense: one arc, the widest the squares hold, meeting both lines along them, each line
             * running on straight from where its track ends to where the arc leaves it. The arc must
             * cross squares the picture paints rails alone on. A route between two ends is read
             * from the end of the line read first.
             */
            private void route(int i, int side, int j, int sense) {
                Line one = lines.get(i), two = lines.get(j);
                double det = NX[one.way] * NY[two.way] - NY[one.way] * NX[two.way];
                double px = (one.c * NY[two.way] - NY[one.way] * two.c) / det, py = (NX[one.way] * two.c - one.c * NX[two.way]) / det;
                double h1x = side * one.dx, h1y = side * one.dy, h2x = sense * two.dx, h2y = sense * two.dy;
                double turned = Math.acos(Math.max(-1, Math.min(1, h1x * h2x + h1y * h2y)));
                if (Math.abs(turned - Math.PI / 4) > 1e-6 && Math.abs(turned - Math.PI / 2) > 1e-6) {
                    return;
                }
                double atOne = (px - one.footX) * one.dx + (py - one.footY) * one.dy, atTwo = (px - two.footX) * two.dx + (py - two.footY) * two.dy;
                double end = side < 0 ? one.lo : one.hi;
                double ahead = (atOne - end) * side;
                if (ahead <= 0) {
                    return;
                }
                // Two's track from where the route meets it on, towards sense: it must run on past there, and where it ends short of it the route joins that end.
                double near = sense > 0 ? two.lo : two.hi, far = sense > 0 ? (two.hiOpen ? Double.POSITIVE_INFINITY : two.hi) : (two.loOpen ? Double.NEGATIVE_INFINITY : two.lo);
                boolean nearOpen = sense > 0 ? two.loOpen : two.hiOpen;
                double most = ahead;
                boolean joinsEnd = !nearOpen && (near - atTwo) * sense > 0;
                if (joinsEnd) {
                    if (j < i) {
                        return;
                    }
                    most = Math.min(most, (near - atTwo) * sense);
                }
                double half = Math.tan(turned / 2);
                double turn = Math.signum(h1x * h2y - h1y * h2x);
                for (double r = most / half; r >= ROUTE_TIGHTEST; r -= ROUTE_STEP) {
                    double reach = r * half;
                    if ((far - atTwo) * sense <= reach + 1) {
                        return;
                    }
                    double t1x = px - h1x * reach, t1y = py - h1y * reach;
                    double cx = t1x - turn * h1y * r, cy = t1y + turn * h1x * r;
                    double start = Math.atan2(t1y - cy, t1x - cx), sweep = turn * turned;
                    if (holds(one, end, atOne - side * reach) && holds(two, joinsEnd ? near : atTwo + sense * reach, atTwo + sense * reach)
                            && holdsArc(cx, cy, r, start, sweep)) {
                        if (!paintsAlone(cx, cy, r, start, sweep)) {
                            return;
                        }
                        routes.add(new Route(cx, cy, r, start, sweep));
                        stretch(one, side, atOne - side * reach);
                        if (joinsEnd) {
                            stretch(two, -sense, atTwo + sense * reach);
                        }
                        return;
                    }
                }
            }

            /** Runs a line's track on, on side, to along. */
            private void stretch(Line line, int side, double along) {
                if (side < 0) {
                    line.from = Math.min(line.from, along);
                } else {
                    line.to = Math.max(line.to, along);
                }
            }

            /** Each edge and the middle of each rail's head, out from the middle of its track. */
            private static final double[] HEADS = {-RAIL_OFF - HEAD / 2, -RAIL_OFF, -RAIL_OFF + HEAD / 2, RAIL_OFF - HEAD / 2, RAIL_OFF, RAIL_OFF + HEAD / 2};

            /** Whether both rails of a line between two places along it lie on the turnout's squares. */
            private boolean holds(Line line, double a, double b) {
                double from = Math.min(a, b), to = Math.max(a, b);
                double nx = -line.dy, ny = line.dx;
                for (double at = from; at <= to + 1e-9; at += 0.02) {
                    double mx = line.footX + line.dx * at, my = line.footY + line.dy * at;
                    for (double out : HEADS) {
                        if (!on(mx + nx * out, my + ny * out)) {
                            return false;
                        }
                    }
                }
                return true;
            }

            /** Whether both rails of an arc lie on the turnout's squares. */
            private boolean holdsArc(double cx, double cy, double r, double start, double sweep) {
                int steps = (int) Math.ceil(Math.abs(sweep) * (r + RAIL_OFF) / 0.02);
                for (int k = 0; k <= steps; k++) {
                    double a = start + sweep * k / steps, cos = Math.cos(a), sin = Math.sin(a);
                    for (double out : HEADS) {
                        if (!on(cx + (r + out) * cos, cy + (r + out) * sin)) {
                            return false;
                        }
                    }
                }
                return true;
            }

            /** Whether the arc's middle crosses a square the picture paints rails alone on. */
            private boolean paintsAlone(double cx, double cy, double r, double start, double sweep) {
                int steps = (int) Math.ceil(Math.abs(sweep) * r / 0.1);
                for (int k = 1; k < steps; k++) {
                    double a = start + sweep * k / steps;
                    if (alone.contains(key((int) Math.floor(cx + r * Math.cos(a)), (int) Math.floor(cy + r * Math.sin(a))))) {
                        return true;
                    }
                }
                return false;
            }

            /** Whether a point is on a square of the turnout that is built. */
            private boolean on(double x, double y) {
                long at = key((int) Math.floor(x), (int) Math.floor(y));
                return squares.contains(at) && !covered.contains(at);
            }

            /** Whether the square at is near enough the middle of a route or a line of the turnout to be painting its rails. */
            private boolean painted(long at) {
                double x = (int) (at >> 32) + 0.5, y = (int) at + 0.5;
                for (Line line : lines) {
                    double along = Math.max(line.from, Math.min(line.to, (x - line.footX) * line.dx + (y - line.footY) * line.dy));
                    if (Math.hypot(line.footX + line.dx * along - x, line.footY + line.dy * along - y) <= PAINTED_NEAR) {
                        return true;
                    }
                }
                for (Route route : routes) {
                    double turned = Math.IEEEremainder(Math.atan2(y - route.y, x - route.x) - route.start, 2 * Math.PI) * Math.signum(route.sweep);
                    double off;
                    if (turned >= 0 && turned <= Math.abs(route.sweep)) {
                        off = Math.abs(Math.hypot(x - route.x, y - route.y) - route.r);
                    } else {
                        off = Double.MAX_VALUE;
                        for (double a : new double[] {route.start, route.start + route.sweep}) {
                            off = Math.min(off, Math.hypot(route.x + route.r * Math.cos(a) - x, route.y + route.r * Math.sin(a) - y));
                        }
                    }
                    if (off <= PAINTED_NEAR) {
                        return true;
                    }
                }
                return false;
            }
        }
    }

    /** A line of track with sleepers on a turnout's squares. */
    static final class Line {
        final int way;
        final double c, footX, footY, dx, dy;
        /** How far along it its pieces are laid, whether it runs on out of the turnout either way, and how far along it is built. */
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY, from, to;
        boolean loOpen, hiOpen;

        Line(int way, double c) {
            this.way = way;
            this.c = c;
            double length = Math.hypot(NX[way], NY[way]);
            footX = NX[way] * c / (length * length);
            footY = NY[way] * c / (length * length);
            double[] d = direction(way);
            dx = d[0];
            dy = d[1];
        }
    }

    /** One way a turnout turns: an arc on the map. */
    static final class Route {
        final double x, y, r, start, sweep;

        Route(double x, double y, double r, double start, double sweep) {
            this.x = x;
            this.y = y;
            this.r = r;
            this.start = start;
            this.sweep = sweep;
        }
    }

    /**
     * A turnout: where the map lays the pieces of a switch over other track, painting each rail
     * of the way it turns as its own piece of a bend. It is read as the lines of track with
     * sleepers on its squares, each running on through it or ending in it, and the routes from
     * where each ends onto the others, and built as they are: each route one arc meeting both its
     * lines along them, with its rails crossing the other track's where they meet. A turnout the
     * squares do not hold so, with a piece of the picture no line or route accounts for, is left
     * the picture it is, all of it.
     */
    static final class Turnout {
        static final Turnout NONE = new Turnout(null, null, null), AWAITED = new Turnout(null, null, null);
        final List<Line> lines;
        final List<Route> routes;
        /** Why it is left the picture it is, or null for one built. */
        final String unread;
        /** Its squares, the squares round them a change to which has it read again, in order, and what those hold, summed. */
        Set<Long> squares;
        long[] round;
        long signature;

        Turnout(List<Line> lines, List<Route> routes, String unread) {
            this.lines = lines;
            this.routes = routes;
            this.unread = unread;
        }

        /** Fills a square of it with all of it, and says LAID; or FLAT for one left the picture it is. */
        int fill(Square square) {
            if (unread != null) {
                return FLAT;
            }
            for (Line line : lines) {
                square.line(line.way, line.c, line.from, line.to, true);
                square.band(line.way, line.c, line.from, line.to);
            }
            for (Route route : routes) {
                square.arc(route.x, route.y, route.r, route.start, route.sweep, true);
            }
            return LAID;
        }
    }

    /**
     * What a square's pieces are built from: parts of lines and arcs, in the map's terms, so
     * every square of a track works out the same ones.
     */
    static final class Square {
        static final int MOST = 24;
        /** For each part of a line: its way, its constant on the map, from and to along it, and whether it has sleepers. */
        final int[] lineWay = new int[MOST];
        final double[] lineC = new double[MOST], lineFrom = new double[MOST], lineTo = new double[MOST];
        final boolean[] lineTies = new boolean[MOST];
        int lines;
        /** For each arc: its centre on the map, radius, angles, sleepers. */
        final double[] arcX = new double[MOST], arcY = new double[MOST], arcR = new double[MOST], arcStart = new double[MOST], arcSweep = new double[MOST];
        final boolean[] arcTies = new boolean[MOST];
        int arcs;
        /**
         * The lines of a turnout, each from and to along it, first those running on through it:
         * the sleepers of each line and arc after them are cut where they meet the sleepers of
         * those before it.
         */
        final int[] bandWay = new int[MOST];
        final double[] bandC = new double[MOST], bandFrom = new double[MOST], bandTo = new double[MOST];
        int bands;

        void clear() {
            lines = arcs = bands = 0;
        }

        /** Adds a piece on the square at (x, y), a straight with its middle moved across by shift. */
        void add(Piece piece, int x, int y, float shift) {
            if (piece == NOTHING) {
                return;
            }
            if (piece.bend == null) {
                line(piece.way, piece.c + shift + NX[piece.way] * x + NY[piece.way] * y, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, piece.ties);
                return;
            }
            Bend b = piece.bend;
            double ox = x - piece.dx, oy = y - piece.dy;
            halfLine(b.wayOne, b.cOne + NX[b.wayOne] * ox + NY[b.wayOne] * oy, ox + b.oneX, oy + b.oneY, b.alongOneX, b.alongOneY, piece.ties);
            halfLine(b.wayTwo, b.cTwo + NX[b.wayTwo] * ox + NY[b.wayTwo] * oy, ox + b.twoX, oy + b.twoY, b.alongTwoX, b.alongTwoY, piece.ties);
            double cx = ox + b.centreX, cy = oy + b.centreY;
            int known = arc(cx, cy, b.radius, b.startAngle);
            if (known >= 0) {
                arcTies[known] |= piece.ties;
                return;
            }
            arc(cx, cy, b.radius, b.startAngle, b.sweep, piece.ties);
        }

        void arc(double x, double y, double r, double start, double sweep, boolean ties) {
            if (arcs == MOST) {
                throw new IllegalStateException("more than " + MOST + " bends on one square");
            }
            arcX[arcs] = x;
            arcY[arcs] = y;
            arcR[arcs] = r;
            arcStart[arcs] = start;
            arcSweep[arcs] = sweep;
            arcTies[arcs++] = ties;
        }

        void band(int way, double c, double from, double to) {
            if (bands == MOST) {
                throw new IllegalStateException("more than " + MOST + " lines in one turnout");
            }
            bandWay[bands] = way;
            bandC[bands] = c;
            bandFrom[bands] = from;
            bandTo[bands++] = to;
        }

        /** Whether the square has any part of the line (way, c). */
        boolean has(int way, double c) {
            for (int i = 0; i < lines; i++) {
                if (lineWay[i] == way && lineC[i] == c) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Whether a sleeper of the line (way, c) at along on it is laid by the parts of the line
         * the square has: half a gap in from their ends, or in a turnout up to them, where the
         * arcs going on from them take up the gap.
         */
        boolean tieAt(int way, double c, double along) {
            double in = bands > 0 ? 0 : TIE_APART / 2;
            for (int i = 0; i < lines; i++) {
                if (lineWay[i] == way && lineC[i] == c && lineTies[i] && along >= lineFrom[i] + in && along <= lineTo[i] - in) {
                    return true;
                }
            }
            return false;
        }

        /** Which of the square's arcs is the one given, or -1. */
        int arc(double x, double y, double r, double start) {
            for (int i = 0; i < arcs; i++) {
                if (arcX[i] == x && arcY[i] == y && arcR[i] == r && arcStart[i] == start) {
                    return i;
                }
            }
            return -1;
        }

        /** The line from where a bend leaves it, outwards. */
        private void halfLine(int way, double c, double fromX, double fromY, double alongX, double alongY, boolean ties) {
            double[] d = DIRECTIONS[way];
            double at = fromX * d[0] + fromY * d[1];
            if (alongX * d[0] + alongY * d[1] > 0) {
                line(way, c, at, Double.POSITIVE_INFINITY, ties);
            } else {
                line(way, c, Double.NEGATIVE_INFINITY, at, ties);
            }
        }

        void line(int way, double c, double from, double to, boolean ties) {
            for (int i = 0; i < lines; i++) {
                if (lineWay[i] == way && lineC[i] == c && lineFrom[i] == from && lineTo[i] == to) {
                    lineTies[i] |= ties;
                    return;
                }
            }
            if (lines == MOST) {
                throw new IllegalStateException("more than " + MOST + " lengths of track on one square");
            }
            lineWay[lines] = way;
            lineC[lines] = c;
            lineFrom[lines] = from;
            lineTo[lines] = to;
            lineTies[lines++] = ties;
        }
    }

    /** The unit direction along a way's lines. */
    static double[] direction(int way) {
        double length = Math.hypot(NX[way], NY[way]);
        return new double[] {-NY[way] / length, NX[way] / length};
    }

    /** Each way's direction and the length of its normal, worked out once. */
    private static final double[][] DIRECTIONS = {direction(NS), direction(EW), direction(NE), direction(NW)};
    private static final double[] NORMAL_LENGTH = {Math.hypot(NX[NS], NY[NS]), Math.hypot(NX[EW], NY[EW]), Math.hypot(NX[NE], NY[NE]), Math.hypot(NX[NW], NY[NW])};

    /** Merges the first n intervals of from and to into as few as cover the same, in order, and says how many. */
    static int union(double[] from, double[] to, int n) {
        for (int i = 1; i < n; i++) {
            for (int j = i; j > 0 && from[j] < from[j - 1]; j--) {
                double f = from[j], t = to[j];
                from[j] = from[j - 1];
                to[j] = to[j - 1];
                from[j - 1] = f;
                to[j - 1] = t;
            }
        }
        int kept = 0;
        for (int i = 0; i < n; i++) {
            if (kept > 0 && from[i] <= to[kept - 1]) {
                to[kept - 1] = Math.max(to[kept - 1], to[i]);
            } else {
                from[kept] = from[i];
                to[kept++] = to[i];
            }
        }
        return kept;
    }

    /** Builds what of a square's track lies on the square at (x, y): rails and sleepers, each cut at its edges. */
    static final class Builder {
        private final Sink sink;
        private final float[] corners = new float[12], along = new float[8];

        Builder(Sink sink) {
            this.sink = sink;
        }

        private final boolean[] done = new boolean[Square.MOST];
        private final double[] railFrom = new double[Square.MOST], railTo = new double[Square.MOST];
        private Square square;
        private Around around;
        private int x, y;

        /**
         * Builds the square at (x, y), with the squares round it. The parts of one line are
         * built as one: rails over all of them, and sleepers. Whether a sleeper is laid is for
         * the square its middle is on to say, where that square has its line or bend, and
         * otherwise for this one: a bend's line starts its sleepers half a gap out from where
         * the bend leaves it, clear of the bend's own.
         */
        void build(Square square, int x, int y, Around around) {
            this.square = square;
            this.around = around;
            this.x = x;
            this.y = y;
            Arrays.fill(done, false);
            for (int i = 0; i < square.lines; i++) {
                if (done[i]) {
                    continue;
                }
                int rails = 0;
                for (int j = i; j < square.lines; j++) {
                    if (square.lineWay[j] != square.lineWay[i] || square.lineC[j] != square.lineC[i]) {
                        continue;
                    }
                    done[j] = true;
                    railFrom[rails] = square.lineFrom[j];
                    railTo[rails++] = square.lineTo[j];
                }
                buildLine(square.lineWay[i], square.lineC[i], union(railFrom, railTo, rails));
            }
            for (int i = 0; i < square.arcs; i++) {
                buildArc(i);
            }
        }

        /**
         * Builds the square at (x, y), which reader has just read round, and each square next to
         * it with no track of its own that it builds (see Reader.builds), and says WAITING while
         * one of those waits on the map, else LAID.
         */
        int build(Reader reader, int x, int y) {
            build(reader.square(), x, y, reader);
            int status = LAID;
            for (int i = 0; i < NEXT_X.length; i++) {
                int hx = x + NEXT_X[i], hy = y + NEXT_Y[i];
                int builds = reader.builds(x, y, hx, hy);
                int read = builds == 1 ? reader.readAround(hx, hy) : builds;
                if (read == WAITING) {
                    status = WAITING;
                } else if (builds == 1 && read == LAID) {
                    build(reader.square(), hx, hy, reader);
                }
            }
            return status;
        }

        /** How far from its middle a sleeper reaches. */
        private static final double REACH = Math.hypot(TIE_LONG / 2, TIE_WIDE / 2);

        /** Whether a sleeper with its middle at (cx, cy) may reach the square being built. */
        private boolean reaches(double cx, double cy) {
            return cx > x - REACH && cx < x + 1 + REACH && cy > y - REACH && cy < y + 1 + REACH;
        }

        /** The square a sleeper with its middle at (cx, cy) is for: the one its middle is on, or this one. */
        private Square owner(double cx, double cy) {
            int ox = (int) Math.floor(cx), oy = (int) Math.floor(cy);
            return ox == x && oy == y ? square : around.at(ox, oy);
        }

        private void buildLine(int way, double c, int rails) {
            double[] d = DIRECTIONS[way];
            double length = NORMAL_LENGTH[way];
            double nx = NX[way] / length, ny = NY[way] / length;
            // Along is measured from the point of the line nearest the map's corner, the same on every square.
            double footX = nx * c / length, footY = ny * c / length;
            for (int r = 0; r < rails; r++) {
                for (int side = -1; side <= 1; side += 2) {
                    double px = footX + nx * side * RAIL_OFF, py = footY + ny * side * RAIL_OFF;
                    double[] part = clip(px, py, d[0], d[1], x, y, railFrom[r], railTo[r]);
                    if (part == null) {
                        continue;
                    }
                    double from = part[0];
                    while (from < part[1] - 1e-6) {
                        double to = Math.min(part[1], (Math.floor(from / LINE_STEP + 1e-9) + 1) * LINE_STEP);
                        rail(px + d[0] * from, py + d[1] * from, px + d[0] * to, py + d[1] * to, nx, ny, nx, ny);
                        from = to;
                    }
                }
            }
            // A sleeper reaches the square where its middle along the line is within half its width of the square's.
            double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
            for (int corner = 0; corner < 4; corner++) {
                double at = (x + (corner & 1) - footX) * d[0] + (y + (corner >> 1) - footY) * d[1];
                low = Math.min(low, at);
                high = Math.max(high, at);
            }
            int band = 0;
            while (band < square.bands && (square.bandWay[band] != way || square.bandC[band] != c)) {
                band++;
            }
            for (long k = (long) Math.ceil((low - TIE_WIDE / 2) / TIE_APART); k * TIE_APART <= high + TIE_WIDE / 2; k++) {
                double cx = footX + d[0] * k * TIE_APART, cy = footY + d[1] * k * TIE_APART;
                if (!reaches(cx, cy)) {
                    continue;
                }
                Square owner = owner(cx, cy);
                if ((owner != null && owner.has(way, c) ? owner : square).tieAt(way, c, k * TIE_APART)) {
                    strips(cx, cy, Math.atan2(ny, nx), band == square.bands ? 0 : band);
                    tie(cx, cy, nx, ny, d[0], d[1]);
                }
            }
        }

        private void buildArc(int i) {
            double ox = square.arcX[i], oy = square.arcY[i], r = square.arcR[i], start = square.arcStart[i], sweep = square.arcSweep[i];
            int steps = (int) Math.ceil(Math.abs(sweep) * (r + RAIL_OFF) / ARC_STEP);
            double turn = Math.signum(sweep);
            for (int k = 0; k < steps; k++) {
                double a0 = start + sweep * k / steps, a1 = start + sweep * (k + 1) / steps, am = (a0 + a1) / 2;
                double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1), cm = Math.cos(am), sm = Math.sin(am);
                int mx = (int) Math.floor(ox + r * cm), my = (int) Math.floor(oy + r * sm);
                for (int side = -1; side <= 1; side += 2) {
                    double rr = r + side * RAIL_OFF;
                    int rx = (int) Math.floor(ox + rr * cm), ry = (int) Math.floor(oy + rr * sm);
                    if ((rx != x || ry != y) && (mx != x || my != y)) {
                        continue;
                    }
                    Square there = rx == x && ry == y ? square : around.at(rx, ry);
                    boolean railsOwn = there != null && there.arc(ox, oy, r, start) >= 0;
                    if (railsOwn ? rx == x && ry == y : mx == x && my == y) {
                        rail(ox + rr * c0, oy + rr * s0, ox + rr * c1, oy + rr * s1, c0 * turn, s0 * turn, c1 * turn, s1 * turn);
                    }
                }
            }
            int ties = arcTies(i);
            for (int k = 0; k < ties; k++) {
                double a = arcTie(i, k, ties), cx = ox + r * Math.cos(a), cy = oy + r * Math.sin(a);
                if (!reaches(cx, cy)) {
                    continue;
                }
                Square owner = owner(cx, cy);
                int there = owner == null ? -1 : owner.arc(ox, oy, r, start);
                if (there >= 0 ? owner.arcTies[there] : square.arcTies[i]) {
                    strips(cx, cy, a, square.bands);
                    if (square.bands > 0) {
                        // In a turnout a sleeper is cut where it meets the sleepers of the routes before it, and round a tight route the one before it.
                        for (int j = 0; j <= i; j++) {
                            int all = j < i ? arcTies(j) : ties, others = j < i ? all : k;
                            for (int m = 0; m < others; m++) {
                                double b = arcTie(j, m, all), bx = square.arcX[j] + square.arcR[j] * Math.cos(b), by = square.arcY[j] + square.arcR[j] * Math.sin(b);
                                box(cx, cy, a, bx, by, b);
                            }
                        }
                    }
                    tie(cx, cy, Math.cos(a), Math.sin(a), -Math.sin(a), Math.cos(a));
                }
            }
        }

        /**
         * How many sleepers the square's arc i has: in a turnout from a sleeper apart from the last
         * of a line ending where it starts to the same where it ends, close enough together that
         * none of its outer rail is far from one; otherwise from half a gap in from each end.
         */
        private int arcTies(int i) {
            double first = arcFirst(i), last = arcLast(i);
            int ties = last <= first ? 1 : (int) Math.round((last - first) / TIE_APART) + 1;
            if (square.bands > 0 && last > first) {
                ties = Math.max(ties, (int) Math.ceil((last - first) * (square.arcR[i] + RAIL_OFF) / square.arcR[i] / TIE_APART_MOST) + 1);
            }
            return ties;
        }

        /** The angle at which the square's arc i, of ties sleepers, has its sleeper k. */
        private double arcTie(int i, int k, int ties) {
            double first = arcFirst(i), last = arcLast(i);
            double at = ties == 1 ? (first + last) / 2 : first + (last - first) * k / (ties - 1);
            return square.arcStart[i] + Math.signum(square.arcSweep[i]) * at / square.arcR[i];
        }

        private double arcFirst(int i) {
            double ox = square.arcX[i], oy = square.arcY[i], r = square.arcR[i], start = square.arcStart[i], turn = Math.signum(square.arcSweep[i]);
            return square.bands > 0 ? endGap(ox + r * Math.cos(start), oy + r * Math.sin(start), -turn * Math.sin(start), turn * Math.cos(start)) : TIE_APART / 2;
        }

        private double arcLast(int i) {
            double ox = square.arcX[i], oy = square.arcY[i], r = square.arcR[i], end = square.arcStart[i] + square.arcSweep[i], turn = Math.signum(square.arcSweep[i]);
            return Math.abs(square.arcSweep[i]) * r - (square.bands > 0 ? endGap(ox + r * Math.cos(end), oy + r * Math.sin(end), turn * Math.sin(end), -turn * Math.cos(end)) : TIE_APART / 2);
        }

        /**
         * Adds the sleeper with its middle at (bx, by), its length along the angle b, to what the
         * next sleeper, with its middle at (cx, cy) and its length along the angle a, is cut away
         * in, where the two meet.
         */
        private void box(double cx, double cy, double a, double bx, double by, double b) {
            double hl = TIE_LONG / 2, hw = TIE_WIDE / 2;
            double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
            for (int axis = 0; axis < 4; axis++) {
                double ux = axis == 0 ? ca : axis == 1 ? -sa : axis == 2 ? cb : -sb, uy = axis == 0 ? sa : axis == 1 ? ca : axis == 2 ? sb : cb;
                double gap = Math.abs((bx - cx) * ux + (by - cy) * uy);
                double reachA = hl * Math.abs(ux * ca + uy * sa) + hw * Math.abs(-ux * sa + uy * ca);
                double reachB = hl * Math.abs(ux * cb + uy * sb) + hw * Math.abs(-ux * sb + uy * cb);
                if (gap >= reachA + reachB) {
                    return;
                }
            }
            double across = -sb * bx + cb * by, along = cb * bx + sb * by;
            strip(-sb, cb, across - hw, across + hw);
            limit(cb, sb, along - hl, along + hl);
        }

        /**
         * How far into an arc that leaves (px, py) along (tx, ty) its first sleeper is: where one
         * of the turnout's lines ends there, its sleepers' gap on from its last; otherwise half a gap.
         */
        private double endGap(double px, double py, double tx, double ty) {
            for (int b = 0; b < square.bands; b++) {
                int way = square.bandWay[b];
                double length = NORMAL_LENGTH[way];
                double[] d = DIRECTIONS[way];
                double along = px * d[0] + py * d[1], onward = tx * d[0] + ty * d[1];
                if (Math.abs((NX[way] * px + NY[way] * py - square.bandC[b]) / length) > 1e-6 || Math.abs(Math.abs(onward) - 1) > 1e-6) {
                    continue;
                }
                if (onward > 0 && Math.abs(along - square.bandTo[b]) < 1e-6) {
                    return Math.floor(along / TIE_APART + 1e-9) * TIE_APART + TIE_APART - along;
                }
                if (onward < 0 && Math.abs(along - square.bandFrom[b]) < 1e-6) {
                    return along - Math.ceil(along / TIE_APART - 1e-9) * TIE_APART + TIE_APART;
                }
            }
            return TIE_APART / 2;
        }

        /** The part of the line through (px, py) along (dx, dy), between from and to along it, that is on the square, or null. */
        static double[] clip(double px, double py, double dx, double dy, int x, int y, double from, double to) {
            double lo = from, hi = to;
            double[] range = {lo, hi};
            if (!slab(px, dx, x, range) || !slab(py, dy, y, range)) {
                return null;
            }
            return range[1] - range[0] > 1e-6 ? range : null;
        }

        private static boolean slab(double p, double d, int edge, double[] range) {
            if (Math.abs(d) < 1e-12) {
                return p >= edge && p < edge + 1;
            }
            double t0 = (edge - p) / d, t1 = (edge + 1 - p) / d;
            range[0] = Math.max(range[0], Math.min(t0, t1));
            range[1] = Math.min(range[1], Math.max(t0, t1));
            return range[0] < range[1];
        }

        /**
         * A length of rail between two points of its middle, each with the way out to one side of
         * it, which is the way along turned as y turns to x. Each face's corners go round it the
         * way that makes it face out of the rail.
         */
        private void rail(double x0, double y0, double x1, double y1, double out0X, double out0Y, double out1X, double out1Y) {
            float base0 = sink.ground((float) x0, (float) y0) + TIE_TOP, base1 = sink.ground((float) x1, (float) y1) + TIE_TOP;
            for (int side = -1; side <= 1; side += 2) {
                for (int p = 0; p < SECTION_OUT.length - 1; p++) {
                    int first = side > 0 ? p : p + 1, second = side > 0 ? p + 1 : p;
                    double inA = side * SECTION_OUT[first], inB = side * SECTION_OUT[second];
                    put(0, x0 + out0X * inA, y0 + out0Y * inA, base0 + SECTION_UP[first]);
                    put(1, x1 + out1X * inA, y1 + out1Y * inA, base1 + SECTION_UP[first]);
                    put(2, x1 + out1X * inB, y1 + out1Y * inB, base1 + SECTION_UP[second]);
                    put(3, x0 + out0X * inB, y0 + out0Y * inB, base0 + SECTION_UP[second]);
                    sink.quad(corners, null, RAIL);
                }
            }
            double head = HEAD / 2f;
            put(0, x0 + out0X * head, y0 + out0Y * head, base0 + RAIL_HIGH);
            put(1, x1 + out1X * head, y1 + out1Y * head, base1 + RAIL_HIGH);
            put(2, x1 - out1X * head, y1 - out1Y * head, base1 + RAIL_HIGH);
            put(3, x0 - out0X * head, y0 - out0Y * head, base0 + RAIL_HIGH);
            sink.quad(corners, null, RAIL);
        }

        /** How many corners a part of a sleeper can have, and how many parts cutting it can leave. */
        private static final int CORNERS = 64, PARTS = 64;
        private final double[] polyX = new double[CORNERS], polyY = new double[CORNERS], cutX = new double[CORNERS], cutY = new double[CORNERS];
        private final float[] polyS = new float[CORNERS], polyT = new float[CORNERS], polyTop = new float[CORNERS], polyFoot = new float[CORNERS];
        private double[][] partX = new double[PARTS][CORNERS], partY = new double[PARTS][CORNERS], nextX = new double[PARTS][CORNERS],
                nextY = new double[PARTS][CORNERS];
        private int[] partN = new int[PARTS], nextN = new int[PARTS];

        /**
         * The strips the next sleeper is cut away in: where NX * x + NY * y is between lo and hi,
         * for each, and where a strip is limited, only where MX * x + MY * y is between its
         * limit's lo and hi as well.
         */
        private int strips;
        private static final int STRIPS = 256;
        private final double[] stripNX = new double[STRIPS], stripNY = new double[STRIPS], stripLo = new double[STRIPS], stripHi = new double[STRIPS],
                stripMX = new double[STRIPS], stripMY = new double[STRIPS], stripMLo = new double[STRIPS], stripMHi = new double[STRIPS];
        private final boolean[] limited = new boolean[STRIPS];

        /**
         * Where a sleeper with its middle at (cx, cy) and its length along the angle a meets the
         * sleepers of the square's first bands lines: the whole of a line's band where its middle
         * is along it, otherwise the sleepers at its ends that it meets.
         */
        private void strips(double cx, double cy, double a, int bands) {
            strips = 0;
            double half = TIE_LONG / 2;
            for (int b = 0; b < bands; b++) {
                int way = square.bandWay[b];
                double length = NORMAL_LENGTH[way];
                double[] d = DIRECTIONS[way];
                double nx = NX[way] / length, ny = NY[way] / length, along = cx * d[0] + cy * d[1], middle = square.bandC[b] / length;
                if (along > square.bandFrom[b] && along < square.bandTo[b]) {
                    strip(nx, ny, middle - half, middle + half);
                    continue;
                }
                double from = Math.max(square.bandFrom[b], along - TIE_LONG), to = Math.min(square.bandTo[b], along + TIE_LONG);
                for (long k = (long) Math.ceil(from / TIE_APART - 1e-9); k * TIE_APART <= to + 1e-9; k++) {
                    box(cx, cy, a, nx * middle + d[0] * k * TIE_APART, ny * middle + d[1] * k * TIE_APART, Math.atan2(ny, nx));
                }
            }
        }

        private void strip(double nx, double ny, double lo, double hi) {
            if (strips == STRIPS) {
                throw new IllegalStateException("a sleeper meets more than " + STRIPS + " others");
            }
            stripNX[strips] = nx;
            stripNY[strips] = ny;
            stripLo[strips] = lo;
            stripHi[strips] = hi;
            limited[strips++] = false;
        }

        /** Limits the last strip added to where MX * x + MY * y is between lo and hi. */
        private void limit(double mx, double my, double lo, double hi) {
            stripMX[strips - 1] = mx;
            stripMY[strips - 1] = my;
            stripMLo[strips - 1] = lo;
            stripMHi[strips - 1] = hi;
            limited[strips - 1] = true;
        }

        /**
         * The part on the square being built of a sleeper with its middle at (cx, cy), its length
         * along (lx, ly) and the track along (wx, wy), less where it meets the strips: its top, and
         * a side on each edge, the edges of the square and of the strips it is cut at among them.
         */
        private void tie(double cx, double cy, double lx, double ly, double wx, double wy) {
            double hl = TIE_LONG / 2, hw = TIE_WIDE / 2;
            for (int corner = 0; corner < 4; corner++) {
                double l = ((corner == 1 || corner == 2) ? 1 : -1) * hl, w = (corner >= 2 ? 1 : -1) * hw;
                partX[0][corner] = cx + lx * l + wx * w;
                partY[0][corner] = cy + ly * l + wy * w;
            }
            partN[0] = 4;
            int parts = 1;
            for (int k = 0; k < strips; k++) {
                int made = 0;
                for (int p = 0; p < parts; p++) {
                    for (int side = -1; side <= 1; side += 2) {
                        made = keep(made, cut(partX[p], partY[p], partN[p], nextX[made], nextY[made], stripNX[k], stripNY[k], side < 0 ? stripLo[k] : stripHi[k], side),
                                cx, cy);
                    }
                    if (limited[k]) {
                        int n = cut(partX[p], partY[p], partN[p], cutX, cutY, stripNX[k], stripNY[k], stripLo[k], 1);
                        n = cut(cutX, cutY, n, polyX, polyY, stripNX[k], stripNY[k], stripHi[k], -1);
                        for (int side = -1; side <= 1; side += 2) {
                            made = keep(made, cut(polyX, polyY, n, nextX[made], nextY[made], stripMX[k], stripMY[k], side < 0 ? stripMLo[k] : stripMHi[k], side), cx, cy);
                        }
                    }
                }
                double[][] swapX = partX, swapY = partY;
                partX = nextX;
                partY = nextY;
                nextX = swapX;
                nextY = swapY;
                int[] swapN = partN;
                partN = nextN;
                nextN = swapN;
                parts = made;
            }
            for (int p = 0; p < parts; p++) {
                tiePart(partX[p], partY[p], partN[p], cx, cy, lx, ly, wx, wy);
            }
        }

        /** Keeps the part of a sleeper just cut into nextX and nextY, of n corners, where it is not meagre, and says how many parts are kept. */
        private int keep(int made, int n, double cx, double cy) {
            if (area(nextX[made], nextY[made], n) <= MEAGRE) {
                return made;
            }
            if (made == PARTS - 1) {
                throw new IllegalStateException("a sleeper at " + cx + "," + cy + " cut into more than " + PARTS + " parts");
            }
            nextN[made] = n;
            return made + 1;
        }

        /** The least area of a part of a sleeper that is built, in square metres: a square centimetre. */
        private static final double MEAGRE = 1e-4;

        /** The area of a polygon. */
        private static double area(double[] px, double[] py, int n) {
            double twice = 0;
            for (int v = 0; v < n; v++) {
                int w = (v + 1) % n;
                twice += px[v] * py[w] - px[w] * py[v];
            }
            return Math.abs(twice) / 2;
        }

        /** Builds the part on the square being built of one part of a sleeper: see tie. */
        private void tiePart(double[] px, double[] py, int count, double cx, double cy, double lx, double ly, double wx, double wy) {
            int n = cut(px, py, count, cutX, cutY, 1, 0, x, 1);
            n = cut(cutX, cutY, n, polyX, polyY, 1, 0, x + 1, -1);
            n = cut(polyX, polyY, n, cutX, cutY, 0, 1, y, 1);
            n = cut(cutX, cutY, n, polyX, polyY, 0, 1, y + 1, -1);
            n = corners(n);
            if (area(polyX, polyY, n) <= MEAGRE) {
                return;
            }
            for (int v = 0; v < n; v++) {
                double ex = polyX[v] - cx, ey = polyY[v] - cy;
                polyS[v] = (float) ((ex * lx + ey * ly) / TIE_LONG + 0.5);
                polyT[v] = (float) ((ex * wx + ey * wy) / TIE_WIDE + 0.5);
                float ground = sink.ground((float) polyX[v], (float) polyY[v]);
                polyTop[v] = ground + TIE_TOP;
                polyFoot[v] = ground - TIE_SUNK;
            }
            for (int v = 1; v + 1 < n; v += 2) {
                int last = Math.min(v + 2, n - 1);
                corner(0, 0, polyTop);
                corner(1, v, polyTop);
                corner(2, v + 1, polyTop);
                corner(3, last, polyTop);
                sink.quad(corners, along, TIE);
            }
            for (int a = 0; a < n; a++) {
                int b = (a + 1) % n;
                corner(0, a, polyFoot);
                corner(1, b, polyFoot);
                corner(2, b, polyTop);
                corner(3, a, polyTop);
                sink.quad(corners, along, TIE);
            }
        }

        private void corner(int corner, int v, float[] up) {
            put(corner, polyX[v], polyY[v], up[v]);
            along[corner * 2] = polyS[v];
            along[corner * 2 + 1] = polyT[v];
        }

        /** Keeps of the polygon (ax, ay) what is where sign * (nx * x + ny * y - edge) is not below 0, into (bx, by), and says how many corners. */
        private static int cut(double[] ax, double[] ay, int n, double[] bx, double[] by, double nx, double ny, double edge, int sign) {
            int kept = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                double di = sign * (nx * ax[i] + ny * ay[i] - edge), dj = sign * (nx * ax[j] + ny * ay[j] - edge);
                if (di >= 0) {
                    bx[kept] = ax[i];
                    by[kept++] = ay[i];
                }
                if (di >= 0 != dj >= 0) {
                    double f = di / (di - dj);
                    bx[kept] = ax[i] + (ax[j] - ax[i]) * f;
                    by[kept++] = ay[i] + (ay[j] - ay[i]) * f;
                }
            }
            return kept;
        }

        /** Drops the corners of the cut polygon that are not corners, being on a line between their neighbours, and says how many are left. */
        private int corners(int n) {
            boolean dropped = true;
            while (dropped && n >= 3) {
                dropped = false;
                for (int v = 0; v < n && n >= 3; v++) {
                    int before = (v + n - 1) % n, after = (v + 1) % n;
                    double ux = polyX[v] - polyX[before], uy = polyY[v] - polyY[before], wx = polyX[after] - polyX[v], wy = polyY[after] - polyY[v];
                    if (Math.abs(ux * wy - uy * wx) < 1e-10 || ux * ux + uy * uy < 1e-12) {
                        System.arraycopy(polyX, v + 1, polyX, v, n - v - 1);
                        System.arraycopy(polyY, v + 1, polyY, v, n - v - 1);
                        n--;
                        dropped = true;
                    }
                }
            }
            return n;
        }

        private void put(int corner, double x, double y, float up) {
            corners[corner * 3] = (float) x;
            corners[corner * 3 + 1] = (float) y;
            corners[corner * 3 + 2] = up;
        }
    }
}
