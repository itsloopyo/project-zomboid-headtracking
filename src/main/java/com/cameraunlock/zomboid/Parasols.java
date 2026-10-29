package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * A parasol as the map has one. The game has no sprite of a parasol: the map puts four quarters
 * of a round shop canopy on the four squares round a corner, and most often a wall's corner post
 * on the south-east one for the pole. The game marks a canopy as a roof tile, so that it is cut
 * away with the roofs, and laid as four roof tiles a parasol is four slabs leaning on a post.
 *
 * The mod stands one model at the corner in their place. The quarter the game says faces north
 * lies north-west of the corner, east north-east, west south-west and south south-east: a
 * quarter faces the way its own valance does from where the game's view is.
 */
final class Parasols {
    static final int NONE = -1, NORTH_WEST = 0, NORTH_EAST = 1, SOUTH_WEST = 2, SOUTH_EAST = 3;
    /** Where each quarter's square is from the north-west one's. */
    static final int[] ALONG_X = {0, 1, 0, 1}, ALONG_Y = {0, 0, 1, 1};

    /**
     * Where on the south-east quarter's square its sprite shows the cloth's two colours, and
     * how high, in levels. Both are four fifths of the way out from the pole, 45 and 62.5 degrees
     * round from east: on every striped canopy the game has, those two are on stripes of the
     * two colours. The blue and the red striped ones have a stripe from 55 to 65 degrees and the
     * green one a stripe from 60 on, so 62.5 is 2.5 degrees inside each; 45 is 5 degrees inside
     * the other colour's stripe on all three.
     */
    static final float FIRST_A = 0.566f, FIRST_B = 0.566f, SECOND_A = 0.369f, SECOND_B = 0.710f, CLOTH_HIGH = 0.82f;

    private static final String CANOPY = "Canopy", ROUND = "Curved", DOMED = "Curved light";

    private static final Known<IsoSprite, Integer> quarters = new Known<>();

    private Parasols() {}

    /** For a map loaded anew: its sprites are not the ones before's. */
    static void forget() {
        quarters.clear();
    }

    /**
     * Which quarter of a round canopy a sprite is, by the name the game gives the piece, the
     * kind of piece and the way it faces, or NONE. The four quarters of one canopy do not
     * always have one kind: the game calls three of the pale blue one "Curved light blue" and
     * the fourth "Curved light".
     */
    static int quarterOf(String called, String kind, String facing) {
        if (!CANOPY.equals(called) || kind == null || !kind.startsWith(ROUND) || facing == null) {
            return NONE;
        }
        return switch (facing) {
            case "N" -> NORTH_WEST;
            case "E" -> NORTH_EAST;
            case "W" -> SOUTH_WEST;
            case "S" -> SOUTH_EAST;
            default -> NONE;
        };
    }

    /** The model of a canopy of that kind: the pale blue one is a dome, the rest shallow cones. */
    static String modelOf(String kind) {
        return kind.startsWith(DOMED) ? "parasol_dome" : "parasol";
    }

    static int quarter(IsoSprite sprite) {
        Integer known = quarters.get(sprite);
        if (known == null) {
            PropertyContainer properties = sprite.getProperties();
            known = quarterOf(properties.get("CustomName"), properties.get("GroupName"), properties.get("Facing"));
            known = quarters.keep(sprite, known);
        }
        return known;
    }

    /** The object on a square that is that quarter of a round canopy, or null. */
    static IsoObject holding(IsoGridSquare square, int quarter) {
        if (square == null) {
            return null;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && quarter(sprite) == quarter) {
                return objects.get(i);
            }
        }
        return null;
    }

    /** The squares of one level of a map, by what each holds that is a given quarter of a round canopy, or null. */
    interface Squares<T> {
        T holds(int x, int y, int quarter);
    }

    static Squares<IsoObject> on(IsoCell cell, int z) {
        return (x, y, quarter) -> holding(cell.getGridSquare(x, y, z), quarter);
    }

    /**
     * The south-east quarter of the parasol whose north-west quarter is on a square, when all
     * four of its quarters are on the map, or null.
     */
    static <T> T whole(Squares<T> squares, int northWestX, int northWestY) {
        T front = null;
        for (int quarter = NORTH_WEST; quarter <= SOUTH_EAST; quarter++) {
            front = squares.holds(northWestX + ALONG_X[quarter], northWestY + ALONG_Y[quarter], quarter);
            if (front == null) {
                return null;
            }
        }
        return front;
    }

    /**
     * The south-east quarter of the whole parasol that what is on a square is part of, or null:
     * a quarter by where it lies from the corner, and what is no quarter but may be the pole
     * as the south-east one, on whose square the map stands the pole. Every part of one
     * parasol reaches the same answer.
     */
    static <T> T front(Squares<T> squares, int quarter, boolean pole, int x, int y) {
        if (quarter == NONE && !pole) {
            return null;
        }
        int lies = quarter == NONE ? SOUTH_EAST : quarter;
        return whole(squares, x - ALONG_X[lies], y - ALONG_Y[lies]);
    }

    /** Whether a sprite on a square is a quarter of a parasol that is whole. */
    static boolean partOfOne(IsoCell cell, IsoSprite sprite, int x, int y, int z) {
        int quarter = quarter(sprite);
        return quarter != NONE && front(on(cell, z), quarter, false, x, y) != null;
    }
}
