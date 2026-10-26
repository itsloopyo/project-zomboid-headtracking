package com.cameraunlock.zomboid;

import java.util.IdentityHashMap;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * A hood of roofing over a wall that stands on its own: the notice board's kiosk on a pier.
 * The map makes one of a roof's eave row and nothing else, the tiles a roof hangs out past its
 * walls at the top of the level below it, laid on the squares of the wall with nothing over the
 * wall, and a hipped end piece on the square past each end. Read as a roof's tiles they were
 * thin painted sheets, each its own slab with its sprite's ragged edge, and nothing under them.
 *
 * The mod hangs a model of a metre of hood on each square of the wall instead, hipped at each
 * end of the run, and draws nothing for the end pieces, which the hips stand in for. An eave of
 * a real roof has the roof behind it on the level above, a floor of a flat roof, or the storey
 * above, and is left to Roofs.
 */
final class Hoods {
    static final int NONE = -1;
    /** How high the foot of the board along a hood's front is, in levels: the lowest point of the model. tools/models/furniture.py hood(). */
    static final float FOOT = 0.824f;
    /**
     * Where on its square a hood's sprite gives the roofing's colour: how far out from the wall,
     * how far along it and how high, in levels. On every roof sheet's eave row that is on the
     * face of a shingle, clear of the dark lines between them.
     */
    static final float DYE_OUT = 0.1f, DYE_ALONG = 0.59f, DYE_HIGH = 0.968f;
    /** How many places of a roof sheet one finish of its tiles takes up. */
    private static final int FINISH_PLACES = 32;

    private static final IdentityHashMap<IsoSprite, Integer> eaves = new IdentityHashMap<>();
    private static final IdentityHashMap<IsoSprite, Boolean> ends = new IdentityHashMap<>();

    private Hoods() {}

    /** For a map loaded anew: its sprites are not the ones before's. */
    static void forget() {
        eaves.clear();
        ends.clear();
    }

    /**
     * Which wall of its square an eave tile of a roof hangs from, Outdoors.NORTH or WEST, by
     * its name and the edge the game says it is fixed to, or NONE for a sprite that is none.
     * Every roof sheet of the game holds its tiles twice over in two finishes, 32 places
     * apart, and has its eave row at the same two places of each, the one fixed to the north
     * edge and the one fixed to the west: 6 and 7, and 38 and 39, which burn to 6 and 7.
     */
    static int eaveOf(String name, boolean north, boolean west, boolean eave) {
        if (!eave || north == west) {
            return NONE;
        }
        int inFinish = placeInFinish(name);
        if (inFinish == 6 && north) {
            return Outdoors.NORTH;
        }
        if (inFinish == 7 && west) {
            return Outdoors.WEST;
        }
        return NONE;
    }

    /**
     * Whether a sprite of that name is the piece of eave a roof sheet hangs in the corner
     * between a square's north and west walls, where the eaves along the two walls meet: place
     * 20 of each finish, a V of tiles fixed to no edge.
     */
    static boolean innerCornerOf(String name) {
        return placeInFinish(name) == 20;
    }

    /** Whether a square holds that piece of eave: a piece the game calls an eave, as innerCornerOf names it. */
    static boolean holdsInnerCorner(IsoGridSquare square) {
        if (square == null) {
            return false;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && sprite.getProperties().has(IsoFlagType.isEave) && innerCornerOf(sprite.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Where in its finish a sprite of a roof sheet is, or -1 for a sprite of no roof sheet. */
    private static int placeInFinish(String name) {
        if (name == null || !name.startsWith("roofs_")) {
            return -1;
        }
        int under = name.lastIndexOf('_');
        String sheet = name.substring(0, under), place = name.substring(under + 1);
        boolean roofSheet = sheet.equals("roofs_burnt_01") || sheet.length() == "roofs_00".length() && Character.isDigit(sheet.charAt(6)) && Character.isDigit(sheet.charAt(7));
        if (!roofSheet || place.isEmpty() || !place.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        return Integer.parseInt(place) % FINISH_PLACES;
    }

    static int eave(IsoSprite sprite) {
        Integer known = eaves.get(sprite);
        if (known == null) {
            PropertyContainer properties = sprite.getProperties();
            known = Roofs.isTile(sprite.getType()) ? eaveOf(sprite.getName(), properties.has(IsoFlagType.attachedN), properties.has(IsoFlagType.attachedW),
                    properties.has(IsoFlagType.isEave)) : NONE;
            eaves.put(sprite, known);
        }
        return known;
    }

    /** Whether a sprite is a piece of a roof's eave that is not its row: a hip's end, a corner. */
    static boolean endPiece(IsoSprite sprite) {
        Boolean known = ends.get(sprite);
        if (known == null) {
            known = Roofs.isTile(sprite.getType()) && sprite.getName() != null && sprite.getName().startsWith("roofs_")
                    && sprite.getProperties().has(IsoFlagType.isEave) && eave(sprite) == NONE;
            ends.put(sprite, known);
        }
        return known;
    }

    /** One level of a map, as a hood is found on it. */
    interface Squares {
        /** The wall the eave tile on a square hangs from, NONE where there is none. */
        int eave(int x, int y);

        /** Whether a square has a wall on its north or its west edge, as wall says. */
        boolean walled(int x, int y, int wall);

        /** Whether the square one level up holds nothing at all. */
        boolean emptyAbove(int x, int y);

        /** Whether a square holds a piece of a roof's eave that is not its row. */
        boolean endPiece(int x, int y);
    }

    /** The way the front of a hood on that wall faces, as quarter turns for Models.turn. */
    static int turns(int wall) {
        return wall == Outdoors.WEST ? Outdoors.EAST : Outdoors.SOUTH;
    }

    /** Whether a square holds a hood: an eave tile on the square of its wall, with nothing above it on the square the roof behind would be on. */
    static boolean hood(Squares squares, int x, int y) {
        int wall = squares.eave(x, y);
        return wall != NONE && squares.walled(x, y, wall) && squares.emptyAbove(wall == Outdoors.WEST ? x - 1 : x, wall == Outdoors.NORTH ? y - 1 : y);
    }

    /**
     * The model of the hood on a square: whether it ends at the end of the model that is at its
     * +x, and at its -x, is whether the square past it along the wall has no hood.
     */
    static String modelOf(Squares squares, int x, int y) {
        float[] plus = new float[2];
        Models.turn(turns(squares.eave(x, y)), 1f, 0f, plus);
        int stepX = Math.round(plus[0]), stepY = Math.round(plus[1]);
        boolean endsPlus = !hood(squares, x + stepX, y + stepY), endsMinus = !hood(squares, x - stepX, y - stepY);
        return endsPlus ? endsMinus ? "hood_alone" : "hood_end_plus" : endsMinus ? "hood_end_minus" : "hood";
    }

    /** The four steps to a square's neighbours along the map's x and y. */
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Whether the piece of eave on a square is the end of a hood, which the hood's model has: past the end of the hood's run, along its wall. */
    static boolean endOfAHood(Squares squares, int x, int y) {
        return endOf(squares, x, y, (nx, ny) -> hood(squares, nx, ny));
    }

    /** Squares of a run of eave: which ones the run holds. */
    interface Run {
        boolean holds(int x, int y);
    }

    /** Whether the piece of eave on a square is the end of a run of eave tiles: past the end of the run, along its wall. */
    static boolean endOf(Squares squares, int x, int y, Run run) {
        if (!squares.endPiece(x, y)) {
            return false;
        }
        for (int[] step : STEPS) {
            int nx = x + step[0], ny = y + step[1];
            if (run.holds(nx, ny) && (squares.eave(nx, ny) == Outdoors.WEST) == (step[0] == 0)) {
                return true;
            }
        }
        return false;
    }

    /** Where the middle of the edge of its square the hood hangs from is, along x and y from the square's corner: the model's origin. */
    static void origin(int wall, float[] out) {
        out[0] = wall == Outdoors.WEST ? 0f : 0.5f;
        out[1] = wall == Outdoors.NORTH ? 0f : 0.5f;
    }

    private static final float[] DYED_WEST = {DYE_OUT, DYE_ALONG}, DYED_NORTH = {DYE_ALONG, DYE_OUT};

    /** Where on its square a hood's sprite gives the colour of its roofing, as a and b: not to be written. */
    static float[] dyedAt(int wall) {
        return wall == Outdoors.WEST ? DYED_WEST : DYED_NORTH;
    }

    /** Whether what has these flags is a wall on the north edge of its square: a plain one, or one a window or a doorway is in. */
    static boolean walledNorth(PropertyContainer flags) {
        return flags.has(IsoFlagType.WallN) || flags.has(IsoFlagType.WallNTrans) || flags.has(IsoFlagType.WindowN) || flags.has(IsoFlagType.DoorWallN);
    }

    /** Whether what has these flags is a wall on the west edge of its square: a plain one, or one a window or a doorway is in. */
    static boolean walledWest(PropertyContainer flags) {
        return flags.has(IsoFlagType.WallW) || flags.has(IsoFlagType.WallWTrans) || flags.has(IsoFlagType.WindowW) || flags.has(IsoFlagType.DoorWallW);
    }

    /** Whether a wall stands on the north edge of a square, or the west one: false for no square. */
    static boolean walled(IsoGridSquare square, boolean north) {
        if (square == null) {
            return false;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite == null) {
                continue;
            }
            PropertyContainer flags = sprite.getProperties();
            if (flags.has(IsoFlagType.WallNW) || (north ? walledNorth(flags) : walledWest(flags))) {
                return true;
            }
        }
        return false;
    }

    static Squares on(IsoCell cell, int z) {
        return new Squares() {
            @Override
            public int eave(int x, int y) {
                IsoGridSquare square = cell.getGridSquare(x, y, z);
                if (square == null) {
                    return NONE;
                }
                PZArrayList<IsoObject> objects = square.getObjects();
                for (int i = 0; i < objects.size(); i++) {
                    IsoSprite sprite = objects.get(i).getSprite();
                    int wall = sprite == null ? NONE : Hoods.eave(sprite);
                    if (wall != NONE) {
                        return wall;
                    }
                }
                return NONE;
            }

            @Override
            public boolean walled(int x, int y, int wall) {
                return Hoods.walled(cell.getGridSquare(x, y, z), wall != Outdoors.WEST);
            }

            @Override
            public boolean emptyAbove(int x, int y) {
                IsoGridSquare square = cell.getGridSquare(x, y, z + 1);
                return square == null || square.getObjects().isEmpty();
            }

            @Override
            public boolean endPiece(int x, int y) {
                IsoGridSquare square = cell.getGridSquare(x, y, z);
                if (square == null) {
                    return false;
                }
                PZArrayList<IsoObject> objects = square.getObjects();
                for (int i = 0; i < objects.size(); i++) {
                    IsoSprite sprite = objects.get(i).getSprite();
                    if (sprite != null && Hoods.endPiece(sprite)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }
}
