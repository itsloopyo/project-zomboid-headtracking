package com.cameraunlock.zomboid;

import java.util.HashMap;

/**
 * Which of the game's sprites are lengths of handrail, and what each is a length of.
 *
 * The map gives a flight its handrail as separate sprites: one on each of the flight's three
 * squares for each side that has a rail, and two more a level up for the part of the rail
 * that is higher than a storey over the foot of the flight. Round a landing or a balcony it
 * gives a level rail a square's edge at a time. Each is a picture of a rail as the game's
 * view sees it, and stood on its edge as a sheet it is a rail with no thickness that stops at
 * every square. Flights builds each as a rail, and this says which sprite asks for which.
 */
final class Rails {
    /** Round rail on posts a pace apart. */
    static final int PIPE = 0;
    /** A thin rail on a thin baluster to each tread. */
    static final int BALUSTER = 1;
    /** A timber balustrade: a top rail, a bottom rail, square balusters between them. */
    static final int BALUSTRADE = 2;

    /** What a sprite is a length of. */
    static final int SLOPE = 0, ABOVE = 1, LEVEL_WEST = 2, LEVEL_NORTH = 3, LEVEL_BOTH = 4, CORNER_POST = 5;

    static final class Piece {
        final int style, part;
        /** For SLOPE and ABOVE: whether the flight climbs west, its rail on a north edge, and which square of the flight the length is on. */
        final boolean west;
        final int third;
        /** The colour the rail is painted in: the mean of what its sprites paint, the same for every length of one rail. */
        final float red, green, blue;

        private Piece(int style, int part, boolean west, int third, float[] paint) {
            this.style = style;
            this.part = part;
            this.west = west;
            this.third = third;
            red = paint[0];
            green = paint[1];
            blue = paint[2];
        }
    }

    private static final String SHEET = "fixtures_railings_01_";
    private static final HashMap<String, Piece> PIECES = new HashMap<>();

    private Rails() {}

    /**
     * A flight's rail: the sprites on its bottom, middle and top squares, then the one over
     * the middle square and those over the top one, a level up.
     */
    private static void slope(int style, float[] paint, boolean west, int bottom, int middle, int top, int overMiddle, int... overTop) {
        PIECES.put(SHEET + bottom, new Piece(style, SLOPE, west, 0, paint));
        PIECES.put(SHEET + middle, new Piece(style, SLOPE, west, 1, paint));
        PIECES.put(SHEET + top, new Piece(style, SLOPE, west, 2, paint));
        PIECES.put(SHEET + overMiddle, new Piece(style, ABOVE, west, 1, paint));
        for (int over : overTop) {
            PIECES.put(SHEET + over, new Piece(style, ABOVE, west, 2, paint));
        }
    }

    private static void level(int style, float[] paint, int part, int... sprites) {
        for (int sprite : sprites) {
            PIECES.put(SHEET + sprite, new Piece(style, part, false, 0, paint));
        }
    }

    private static final float[] VARNISHED = {0.30f, 0.23f, 0.14f}, IRON = {0.21f, 0.21f, 0.21f}, WHITE = {0.66f, 0.66f, 0.64f},
            RUST = {0.27f, 0.19f, 0.17f}, TIMBER = {0.28f, 0.16f, 0.10f}, OAK = {0.34f, 0.25f, 0.18f}, PAINTED = {0.74f, 0.71f, 0.68f};

    static {
        slope(BALUSTER, VARNISHED, true, 0, 1, 2, 14, 3);
        slope(BALUSTER, VARNISHED, false, 7, 6, 5, 15, 4);
        slope(BALUSTER, IRON, true, 16, 17, 18, 12, 19);
        slope(BALUSTER, IRON, false, 23, 22, 21, 13, 20);
        slope(BALUSTER, WHITE, true, 86, 87, 94, 102, 95);
        slope(BALUSTER, WHITE, false, 71, 70, 69, 103, 68);
        slope(PIPE, RUST, true, 61, 60, 59, 58, 57, 56);
        slope(PIPE, RUST, false, 40, 41, 42, 43, 44, 51);
        slope(PIPE, TIMBER, true, 101, 100, 99, 98, 97, 96);
        slope(PIPE, TIMBER, false, 80, 81, 82, 83, 84, 85);
        slope(PIPE, WHITE, true, 141, 140, 139, 138, 137, 136);
        slope(PIPE, WHITE, false, 120, 121, 122, 123, 124, 125);

        level(PIPE, RUST, LEVEL_WEST, 48, 54);
        level(PIPE, RUST, LEVEL_NORTH, 49, 53);
        level(PIPE, RUST, LEVEL_BOTH, 52);
        level(PIPE, RUST, CORNER_POST, 50);
        level(PIPE, TIMBER, LEVEL_WEST, 88, 92);
        level(PIPE, TIMBER, LEVEL_NORTH, 89, 93);
        level(PIPE, TIMBER, LEVEL_BOTH, 90);
        level(PIPE, TIMBER, CORNER_POST, 91);
        level(PIPE, WHITE, LEVEL_WEST, 128, 132);
        level(PIPE, WHITE, LEVEL_NORTH, 129, 133);
        level(PIPE, WHITE, LEVEL_BOTH, 130);
        level(PIPE, WHITE, CORNER_POST, 131);
        level(BALUSTER, VARNISHED, LEVEL_WEST, 28);
        level(BALUSTER, VARNISHED, LEVEL_NORTH, 29);
        level(BALUSTER, VARNISHED, LEVEL_BOTH, 30);
        level(BALUSTER, IRON, LEVEL_WEST, 36);
        level(BALUSTER, IRON, LEVEL_NORTH, 37);
        level(BALUSTER, IRON, LEVEL_BOTH, 38);
        level(BALUSTER, WHITE, LEVEL_WEST, 108);
        level(BALUSTER, WHITE, LEVEL_NORTH, 109);
        level(BALUSTER, WHITE, LEVEL_BOTH, 110);
        level(BALUSTRADE, OAK, LEVEL_WEST, 24);
        level(BALUSTRADE, OAK, LEVEL_NORTH, 25);
        level(BALUSTRADE, OAK, LEVEL_BOTH, 26);
        level(BALUSTRADE, OAK, CORNER_POST, 27);
        level(BALUSTRADE, PAINTED, LEVEL_WEST, 64);
        level(BALUSTRADE, PAINTED, LEVEL_NORTH, 65);
        level(BALUSTRADE, PAINTED, LEVEL_BOTH, 66);
        level(BALUSTRADE, PAINTED, CORNER_POST, 67);
    }

    /** What the sprite of this name is a length of, or null when it is no rail. */
    static Piece of(String name) {
        return name == null ? null : PIECES.get(name);
    }

    /** Whether a sprite is a square of the fire escape, the open flight of iron the map stands that post under. */
    static boolean fireEscape(String name) {
        if (name == null || !name.startsWith(FIRE_ESCAPE)) {
            return false;
        }
        String square = name.substring(FIRE_ESCAPE.length());
        return square.equals("3") || square.equals("4") || square.equals("5") || square.equals("11") || square.equals("12") || square.equals("13");
    }

    private static final String FIRE_ESCAPE = "fixtures_stairs_01_";

    /** The post the map stands under the head of a fire escape: Flights stands the flight's own there. */
    static boolean flightPost(String name) {
        return "fixtures_stairs_01_14".equals(name);
    }
}
