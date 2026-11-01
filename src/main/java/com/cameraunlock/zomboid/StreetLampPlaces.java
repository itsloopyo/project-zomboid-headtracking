package com.cameraunlock.zomboid;

import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * Where a street lamp the map draws in two pieces apart stands. The game stands a lamp taller than a storey as the lower
 * length of its post on a square (lighting_outdoor_01_4, 16 or 17) and its head on the square over it, which a model meets
 * (Furniture.postFoot). Its view draws a square at the same place as the square a level up and Spans.LEVEL_SQUARES on along
 * x and y, and a lower length one square back along x and y under a head as running up into it, and the map's artists use
 * both. Read over the shipped map: six heads are drawn a level up and three squares on from their post (557, 9892, 2 over
 * 554, 9889, 0; 415, 9684, 2), fourteen heads stand over a lower length one square back (391, 9805, 1 over 390, 9804, 0),
 * one lower length is drawn a level up in the air three squares on from under its head (11339, 6765, 1 under 11336, 6762, 1)
 * and one head is drawn on the ground three squares back from the post it tops (12587, 3824, 0 over 12590, 3827, 0). Each
 * stood where it was drawn, the head was a lamp in the air with no post under it, and the post a post with no head.
 */
final class StreetLampPlaces {
    private StreetLampPlaces() {}

    /** What of a street lamp a square holds, as Drawn says: a head, the lower length of a post a head stands on, both or neither. */
    static final int HEAD = 1, LOWER = 2;

    /** The map as the lamps are found on it: what of a street lamp is drawn on a square of a level, HEAD and LOWER or'd. */
    interface Drawn {
        int on(int x, int y, int z);
    }

    /** Where a lower length stands: SAME on the square it is drawn on, BACK one square on along x and y, UP a level lower and Spans.LEVEL_SQUARES back. */
    static final int SAME = 0, BACK = 1, UP = 2;

    private static boolean head(Drawn map, int x, int y, int z) {
        return (map.on(x, y, z) & HEAD) != 0;
    }

    private static boolean lower(Drawn map, int x, int y, int z) {
        return (map.on(x, y, z) & LOWER) != 0;
    }

    /** Whether a head is drawn on a square with no lower length under it on its own square or one square back. */
    private static boolean headAlone(Drawn map, int x, int y, int z) {
        return head(map, x, y, z) && !lower(map, x, y, z - 1) && !lower(map, x - 1, y - 1, z - 1);
    }

    /** Whether a lower length is drawn on a square with no head over it. */
    private static boolean lowerAlone(Drawn map, int x, int y, int z) {
        return lower(map, x, y, z) && !head(map, x, y, z + 1);
    }

    /**
     * How many levels the head drawn on a square stands off it, Spans.LEVEL_SQUARES along x and y for each: -1 for one drawn
     * a level up and three squares on from the post it tops, 1 for one drawn on the level of that post three squares back
     * from it, where the post stands on a floor, else 0.
     */
    static int headShift(Drawn map, Poles.Floored floored, int x, int y, int z) {
        if (!headAlone(map, x, y, z)) {
            return 0;
        }
        int on = Spans.LEVEL_SQUARES;
        if (lowerAlone(map, x - on, y - on, z - 2)) {
            return -1;
        }
        return lowerAlone(map, x + on, y + on, z) && floored.at(x + on, y + on, z) ? 1 : 0;
    }

    /**
     * Where the lower length drawn on a square stands: BACK under a head one square on along x and y that has no lower
     * length of its own, UP under a head on its own level Spans.LEVEL_SQUARES back where it is drawn in the air, else SAME.
     */
    static int lowerStands(Drawn map, Poles.Floored floored, int x, int y, int z) {
        if (!lowerAlone(map, x, y, z)) {
            return SAME;
        }
        if (head(map, x + 1, y + 1, z + 1) && !lower(map, x + 1, y + 1, z)) {
            return BACK;
        }
        int on = Spans.LEVEL_SQUARES;
        return !floored.at(x, y, z) && headAlone(map, x - on, y - on, z) && headShift(map, floored, x - on, y - on, z) == 0 ? UP : SAME;
    }

    /**
     * Into to, the square whose head stands over the lower length that stands on (x, y, z), wherever the head is drawn: its
     * own square on the level over, or one a head is drawn on and shifted from (headShift). False for none.
     */
    static boolean headOver(Drawn map, Poles.Floored floored, int x, int y, int z, int[] to) {
        int on = Spans.LEVEL_SQUARES;
        if (head(map, x, y, z + 1) && headShift(map, floored, x, y, z + 1) == 0) {
            return set(to, x, y, z + 1);
        }
        if (headShift(map, floored, x + on, y + on, z + 2) == -1) {
            return set(to, x + on, y + on, z + 2);
        }
        if (headShift(map, floored, x - on, y - on, z) == 1) {
            return set(to, x - on, y - on, z);
        }
        return false;
    }

    private static boolean set(int[] to, int x, int y, int z) {
        to[0] = x;
        to[1] = y;
        to[2] = z;
        return true;
    }

    /**
     * Whether what headShift or lowerStands says of a lamp's piece on a square may change once more of the map is loaded:
     * a head or a lower length alone, with a square it may be shifted to or set back from on map not loaded.
     */
    static boolean unsettled(Drawn map, Poles.Loaded loaded, int x, int y, int z) {
        int on = Spans.LEVEL_SQUARES;
        boolean far = !loaded.at(x - on, y - on) || !loaded.at(x + on, y + on);
        return headAlone(map, x, y, z) && (far || !loaded.at(x - 1, y - 1))
                || lowerAlone(map, x, y, z) && (far || !loaded.at(x + 1, y + 1) || !loaded.at(x - on - 1, y - on - 1));
    }

    /** The places on the game's sheet of street lamps of the lower lengths a head stands on: under the acorns, the road lamps and signals, the car park lamps. */
    private static final int ACORN_LOWER = 4, ROAD_LOWER = 16, CAR_PARK_LOWER = 17;
    private static final String LIT = Furniture.STREET_LAMPS + "on_";

    /** What of a street lamp a sprite is, HEAD, LOWER or 0. A head is one whose model meets the post under it (Furniture.postFoot). */
    static int kindOf(IsoSprite sprite) {
        String name = sprite.getName();
        if (name == null || !name.startsWith(Furniture.STREET_LAMPS)) {
            return 0;
        }
        int which = Sheets.place(name, name.startsWith(LIT) ? LIT : Furniture.STREET_LAMPS);
        if (which == ACORN_LOWER || which == ROAD_LOWER || which == CAR_PARK_LOWER) {
            return LOWER;
        }
        Furniture piece = Furniture.known(sprite);
        return piece != null && piece.postFoot > 0f ? HEAD : 0;
    }

    /** What of a street lamp a square of the cell holds, as Drawn says. Nothing where the mod stands no models (OwnFurniture). */
    static int on(IsoCell cell, int x, int y, int z) {
        IsoGridSquare square = Own.furniture() ? cell.getGridSquare(x, y, z) : null;
        if (square == null) {
            return 0;
        }
        int kinds = 0;
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null) {
                kinds |= kindOf(sprite);
            }
        }
        return kinds;
    }

    /** Whether a square of the cell has a floor. */
    static boolean floored(IsoCell cell, int x, int y, int z) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        return square != null && square.getFloor() != null;
    }

    /**
     * headShift for the head drawn on a square of the cell. A lamp whose head stands over its post, nearly every one, is
     * told from the square under it alone.
     */
    static int headShift(IsoCell cell, int x, int y, int z) {
        if ((on(cell, x, y, z - 1) & LOWER) != 0) {
            return 0;
        }
        return headShift((ax, ay, az) -> on(cell, ax, ay, az), (ax, ay, az) -> floored(cell, ax, ay, az), x, y, z);
    }

    /** lowerStands for the lower length drawn on a square of the cell, told from the square over it where a head is there. */
    static int lowerStands(IsoCell cell, int x, int y, int z) {
        if ((on(cell, x, y, z + 1) & HEAD) != 0) {
            return SAME;
        }
        return lowerStands((ax, ay, az) -> on(cell, ax, ay, az), (ax, ay, az) -> floored(cell, ax, ay, az), x, y, z);
    }

    /** headOver on the cell. */
    static boolean headOver(IsoCell cell, int x, int y, int z, int[] to) {
        return headOver((ax, ay, az) -> on(cell, ax, ay, az), (ax, ay, az) -> floored(cell, ax, ay, az), x, y, z, to);
    }

    /** unsettled on the cell, where a square is on map not loaded when the cell has no square on the ground there. */
    static boolean unsettled(IsoCell cell, int x, int y, int z) {
        return unsettled((ax, ay, az) -> on(cell, ax, ay, az), (ax, ay) -> cell.getGridSquare(ax, ay, 0) != null, x, y, z);
    }
}
