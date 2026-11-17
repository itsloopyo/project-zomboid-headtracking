package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * What a cell's lotpack lays, as the far land takes it (FarCells): the colour of the ground on
 * each square, and on each level the walls along the squares' north and west edges, the windows
 * in them and the tiles of roofs. Read from the lotpack as the game reads it when it loads a
 * chunk (Lotpack), with the tiles' traits and colours FarTiles gave. Any thread.
 */
final class FarLot {
    /** How a roof tile lies: no way the far land takes, level, its height changing along x or along y, where two slopes meet, or a sheet of a roof taken for its colour alone. */
    static final int NONE = 0, FLAT = 1, ALONG_X = 2, ALONG_Y = 3, MEET = 4, SHEET = 5;
    /** Of a square's ground: it has a floor, the low 24 bits its colour, and the floor is water. */
    static final int LAID = 1 << 24, WET = 1 << 25;
    /** Of an edge: a wall stands on it, one tile of it is the wall's own, its colour the low 24 bits, and a window is in it. */
    static final int WALLED = 1 << 24, PAINTED = 1 << 25, GLAZED = 1 << 26;
    /**
     * Of a square's roof: how its tile lies, three bits from LIES_SHIFT, its top as FarTiles
     * keeps it, four bits from TOP_SHIFT, and whether its colour is read, the low 24 bits.
     */
    static final int LIES_SHIFT = 24, TOP_SHIFT = 27, ROOF_PAINTED = 1 << 31;

    private static final int CELL = FarCells.CELL, CHUNK = Terrain.CHUNK_TILES, CHUNKS = CELL / CHUNK;

    final int lowest, highest;
    /** By square, a row of squares at a time from the least y: see LAID. */
    final int[] ground = new int[CELL * CELL];
    /** By level from lowest, each square's north and west edge and its roof, or null for a level with none. */
    private final int[][] north, west, roofs;

    private FarLot(int lowest, int highest) {
        this.lowest = lowest;
        this.highest = highest;
        int levels = Math.max(highest - lowest + 1, 0);
        north = new int[levels][];
        west = new int[levels][];
        roofs = new int[levels][];
    }

    /** How a roof tile lies by what Roofs.measure read of it: its axis, whether it is sure of it, and whether it is a trim or a half tile. */
    static int liesOf(int axis, boolean sure, boolean trim) {
        if (trim) {
            return NONE;
        }
        return switch (axis) {
            case RoofFit.LEVEL -> sure ? FLAT : NONE;
            case RoofFit.ALONG_X -> ALONG_X;
            case RoofFit.ALONG_Y -> ALONG_Y;
            case RoofFit.NORTH_WEST_ODD, RoofFit.SOUTH_EAST_ODD -> MEET;
            default -> throw new IllegalArgumentException("no roof axis " + axis);
        };
    }

    /** The edge along the north of (x, y) of the cell on level z: see WALLED. 0 off the cell or for none. */
    int north(int x, int y, int z) {
        return at(north, x, y, z);
    }

    /** The edge along the west of (x, y): as north. */
    int west(int x, int y, int z) {
        return at(west, x, y, z);
    }

    /** The roof tile on (x, y) on level z: see LIES_SHIFT. 0 off the cell or for none. */
    int roof(int x, int y, int z) {
        return at(roofs, x, y, z);
    }

    private int at(int[][] levels, int x, int y, int z) {
        if (x < 0 || y < 0 || x >= CELL || y >= CELL || z < lowest || z > highest) {
            return 0;
        }
        int[] level = levels[z - lowest];
        return level == null ? 0 : level[y * CELL + x];
    }

    private int[] level(int[][] levels, int z) {
        int[] level = levels[z - lowest];
        if (level == null) {
            level = new int[CELL * CELL];
            levels[z - lowest] = level;
        }
        return level;
    }

    /**
     * Reads a cell's lotpack. A chunk that cannot be read is skipped, as the game loads it, empty,
     * and what was wrong with it added to skipped.
     *
     * @param traits  by each tile of the header, what FarTiles made of it
     * @param colours by each tile of the header, its colour as FarTiles gave it
     */
    static FarLot read(ByteBuffer pack, int[] traits, int[] colours, int minLevel, int maxLevel, List<Lotpack.Malformed> skipped) {
        FarLot lot = new FarLot(Math.max(minLevel, Lotpack.LOWEST), Math.min(maxLevel, Lotpack.HIGHEST));
        Lotpack in = new Lotpack(pack);
        int[] tiles = new int[16];
        for (int wx = 0; wx < CHUNKS; wx++) {
            for (int wy = 0; wy < CHUNKS; wy++) {
                try {
                    tiles = lot.chunk(in, wx, wy, traits, colours, tiles);
                } catch (Lotpack.Malformed unreadable) {
                    lot.empty(wx, wy);
                    skipped.add(unreadable);
                }
            }
        }
        return lot;
    }

    /** Leaves the chunk (wx, wy) with nothing laid on it, as the game loads a chunk whose read threw, whatever was read of it before the throw. */
    private void empty(int wx, int wy) {
        for (int y = wy * CHUNK; y < (wy + 1) * CHUNK; y++) {
            int row = y * CELL + wx * CHUNK;
            Arrays.fill(ground, row, row + CHUNK, 0);
            for (int[][] levels : new int[][][] {north, west, roofs}) {
                for (int[] level : levels) {
                    if (level != null) {
                        Arrays.fill(level, row, row + CHUNK, 0);
                    }
                }
            }
        }
    }

    /** Reads the chunk (wx, wy) of the cell, with tiles as room for a square's tiles: the room, grown if a square needed more. */
    private int[] chunk(Lotpack in, int wx, int wy, int[] traits, int[] colours, int[] tiles) {
        in.chunk(wx, wy);
        for (int z = lowest; z <= highest; z++) {
            for (int x = 0; x < CHUNK; x++) {
                for (int y = 0; y < CHUNK; y++) {
                    int count = in.square();
                    if (count == 0) {
                        continue;
                    }
                    if (count > tiles.length) {
                        tiles = new int[Math.max(count, tiles.length * 2)];
                    }
                    for (int i = 0; i < count; i++) {
                        tiles[i] = in.tile(traits.length);
                    }
                    int square = (wy * CHUNK + y) * CELL + wx * CHUNK + x;
                    if (z == 0) {
                        ground[square] = ground(tiles, count, traits, colours);
                    }
                    edges(tiles, count, traits, colours, z, square);
                }
            }
        }
        return tiles;
    }

    /** Notes the walls, windows and roof the tiles of a square lay. */
    private void edges(int[] tiles, int count, int[] traits, int[] colours, int z, int square) {
        for (int i = 0; i < count; i++) {
            int of = traits[tiles[i]], colour = colours[tiles[i]] & 0xffffff;
            if ((of & (FarTiles.WALL_N | FarTiles.WINDOW_N)) != 0) {
                int[] level = level(north, z);
                level[square] = edge(level[square], of, colour, FarTiles.WALL_N, FarTiles.WINDOW_N);
            }
            if ((of & (FarTiles.WALL_W | FarTiles.WINDOW_W)) != 0) {
                int[] level = level(west, z);
                level[square] = edge(level[square], of, colour, FarTiles.WALL_W, FarTiles.WINDOW_W);
            }
            if ((of & FarTiles.ROOF) != 0) {
                int lies = of >>> FarTiles.LIES_SHIFT & 7, top = of >>> FarTiles.TOP_SHIFT & 15;
                int roof = ((of & FarTiles.COLOURED) != 0 ? ROOF_PAINTED | colour : 0) | lies << LIES_SHIFT | top << TOP_SHIFT;
                int[] level = level(roofs, z);
                int held = level[square], heldLies = held >>> LIES_SHIFT & 7;
                // A tile of the roof's own wins over a sheet taken for its colour, and the first of either over any after it.
                if (held == 0 || (heldLies == SHEET || heldLies == NONE) && lies != SHEET && lies != NONE) {
                    level[square] = roof;
                }
            }
        }
    }

    /** An edge with a tile of the traits laid on it: walled by wall, glazed by window, and painted by the first plain wall's colour. */
    private static int edge(int held, int of, int colour, int wall, int window) {
        held |= (of & wall) != 0 ? WALLED : 0;
        held |= (of & window) != 0 ? GLAZED : 0;
        if ((of & wall) != 0 && (of & (FarTiles.PLAIN_WALL | FarTiles.COLOURED)) == (FarTiles.PLAIN_WALL | FarTiles.COLOURED) && (held & PAINTED) == 0) {
            held |= PAINTED | colour;
        }
        return held;
    }

    /**
     * The ground of a square, of its tiles as the game lays them (MapAhead.DIAMOND): its floor,
     * and over it each tile laid flat on the ground after it in the share of the square it
     * covers. 0 for a square with no floor, or one whose floor paints nothing.
     *
     * @param tiles   the square's tiles, the first count of them, as places in traits and colours
     * @param traits  by tile, its traits as FarTiles has them
     * @param colours by tile, its colour and how much of the square it covers, as FarTiles has them
     */
    static int ground(int[] tiles, int count, int[] traits, int[] colours) {
        int laid = 0;
        boolean coloured = false;
        float red = 0f, green = 0f, blue = 0f;
        for (int i = 0; i < count; i++) {
            int of = traits[tiles[i]], colour = colours[tiles[i]];
            if ((of & FarTiles.GROUND) == 0) {
                continue;
            }
            boolean paints = (of & FarTiles.COLOURED) != 0;
            boolean replaces = (of & (FarTiles.FLOOR | FarTiles.DIAMOND | FarTiles.SEE_THROUGH)) == (FarTiles.FLOOR | FarTiles.DIAMOND) && (laid & FarTiles.DIAMOND) != 0;
            if (replaces || (of & FarTiles.OVERLAY) == 0 && (of & FarTiles.FLOOR) != 0 && laid == 0) {
                // The floor, or a floor that takes its place and the place of what was laid over it.
                laid = of;
                coloured = paints;
                red = (colour >> 16 & 0xff) / 255f;
                green = (colour >> 8 & 0xff) / 255f;
                blue = (colour & 0xff) / 255f;
            } else if (laid != 0 && coloured && paints) {
                // An overlay, a blend or another floor laid over the floor, each only where there is one.
                float over = (colour >>> 24) / 255f;
                red += ((colour >> 16 & 0xff) / 255f - red) * over;
                green += ((colour >> 8 & 0xff) / 255f - green) * over;
                blue += ((colour & 0xff) / 255f - blue) * over;
            }
        }
        if (laid == 0 || !coloured) {
            return 0;
        }
        return LAID | ((laid & FarTiles.WATER) != 0 ? WET : 0) | Math.round(red * 255f) << 16 | Math.round(green * 255f) << 8 | Math.round(blue * 255f);
    }
}
