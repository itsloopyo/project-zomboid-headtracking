package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * What one cell of the far land is made of, worked out from what the game ships for its own
 * map: the picture of the cell, a pixel a tile, and the rooms of its buildings. Nothing here
 * asks the game or the graphics card anything, so it runs on any thread. See FarLand.
 */
final class FarCells {
    /** Tiles a side of a cell: one picture of the game's map, and one cell of its buildings. */
    static final int CELL = 256;
    /** The land has a vertex at every corner of a chunk, as the built world's heights do. */
    static final int STEP = Terrain.CHUNK_TILES;
    static final int SIDE = CELL / STEP;
    private static final int CORNERS = SIDE + 1;
    /** A picture and each halving of it, down to one pixel: no pixel of any of them is of more than its own cell. */
    static final int LEVELS = 9;
    static final int PICTURE_BYTES = 4 * (CELL * CELL * 4 - 1) / 3;
    /** A place, the way its surface faces, a colour, and how far up its building it is. */
    static final int VERTEX_BYTES = 32;
    /** One strip a row of chunks, each joined to the next by its last corner and the next's first given twice. */
    static final int LAND_VERTICES = SIDE * (2 * CORNERS + 2) - 2;
    /** What the game's map paints water, and open grass, which is also what lies where it has no picture. */
    static final int WATER = 0x6c7f83, GRASS = 0x4b581b;
    /**
     * What the game's map paints a pavement, which is a brick pink there so that a street reads on a map, and the
     * concrete it is in the built world.
     */
    private static final int PAVEMENT = 0x8e5b56, CONCRETE = 0x8f8e8a;
    /** How far below its pad the foot of a building's wall goes: the land between two of its corners is not level. */
    private static final float FOOTING = 3f;
    /** In a vertex's colour, what says a building's and not the land's. */
    private static final int BUILT = 0xff;

    /** Display colours of walls and of flat roofs, for a house and for what stands four floors and more. */
    private static final int[] WALLS = {0x8e4a3a, 0xb09670, 0x969692, 0xcdcbc4, 0x785c46, 0xc4b682, 0x9a6a52, 0xa9a59b};
    private static final int[] TALL_WALLS = {0x8f8d88, 0xa8a49a, 0x77736c, 0x9a8672, 0x6f7377, 0xb5b0a4};
    private static final int[] ROOFS = {0x48484a, 0x584234, 0x40464e, 0x683c32, 0x5a5a56};
    private static final int TALL_FROM = 4;

    private FarCells() {}

    /**
     * The picture of a cell and its halvings as the graphics card takes them, the largest first:
     * red, green, blue, and in alpha whether the tile is water.
     *
     * @param pixels the cell's picture, a row of tiles at a time from the least y, or null for a cell the map has no picture of
     */
    static void picture(int[] pixels, ByteBuffer to) {
        int from = to.position();
        for (int i = 0; i < CELL * CELL; i++) {
            int pixel = pixels == null ? GRASS : pixels[i] & 0xffffff;
            int shown = pixel == PAVEMENT ? CONCRETE : pixel;
            to.put((byte) (shown >> 16)).put((byte) (shown >> 8)).put((byte) shown).put((byte) (pixel == WATER ? 255 : 0));
        }
        for (int side = CELL / 2; side >= 1; side /= 2) {
            int whole = side * 2;
            for (int y = 0; y < side; y++) {
                for (int x = 0; x < side; x++) {
                    int upper = from + (y * 2 * whole + x * 2) * 4, lower = upper + whole * 4;
                    for (int part = 0; part < 4; part++) {
                        int sum = (to.get(upper + part) & 0xff) + (to.get(upper + 4 + part) & 0xff) + (to.get(lower + part) & 0xff) + (to.get(lower + 4 + part) & 0xff);
                        to.put((byte) (sum + 2 >> 2));
                    }
                }
            }
            from += whole * whole * 4;
        }
    }

    /** Which chunks of a cell have water in them, a bit a chunk, a row of chunks a number. */
    static int[] waters(int[] pixels) {
        int[] rows = new int[SIDE];
        if (pixels == null) {
            return rows;
        }
        for (int y = 0; y < CELL; y++) {
            for (int x = 0; x < CELL; x++) {
                if ((pixels[y * CELL + x] & 0xffffff) == WATER) {
                    rows[y / STEP] |= 1 << x / STEP;
                }
            }
        }
        return rows;
    }

    /**
     * How far (x, y) is from the nearest chunk with water in it, in tiles, and Terrain.VALLEY if
     * from none that near. The place is counted from the corner of the middle one of nine cells.
     *
     * @param around waters of the nine cells, a row of cells at a time from the least y
     */
    static float toWater(int[][] around, float x, float y) {
        int reach = (int) Math.ceil(Terrain.VALLEY / STEP);
        int chunkX = (int) Math.floor(x / STEP), chunkY = (int) Math.floor(y / STEP);
        float nearest = Terrain.VALLEY;
        for (int cy = chunkY - reach; cy <= chunkY + reach; cy++) {
            for (int cx = chunkX - reach; cx <= chunkX + reach; cx++) {
                int[] rows = around[(Math.floorDiv(cy, SIDE) + 1) * 3 + Math.floorDiv(cx, SIDE) + 1];
                if ((rows[Math.floorMod(cy, SIDE)] >>> Math.floorMod(cx, SIDE) & 1) == 0) {
                    continue;
                }
                float awayX = Math.max(0f, Math.max(cx * STEP - x, x - cx * STEP - STEP));
                float awayY = Math.max(0f, Math.max(cy * STEP - y, y - cy * STEP - STEP));
                nearest = Math.min(nearest, (float) Math.sqrt(awayX * awayX + awayY * awayY));
            }
        }
        return nearest;
    }

    /** The land of a cell: LAND_VERTICES vertices, drawn as one strip. */
    static void land(int[][] around, int cellX, int cellY, ByteBuffer to) {
        // One corner more all round, for the slope at the cell's own edge.
        int ring = CORNERS + 2;
        float[] heights = new float[ring * ring];
        for (int j = 0; j < ring; j++) {
            for (int i = 0; i < ring; i++) {
                float x = (i - 1) * STEP, y = (j - 1) * STEP;
                heights[j * ring + i] = Terrain.far(cellX * CELL + x, cellY * CELL + y, toWater(around, x, y));
            }
        }
        for (int j = 0; j < SIDE; j++) {
            if (j > 0) {
                corner(heights, ring, cellX, cellY, 0, j, to);
            }
            for (int i = 0; i < CORNERS; i++) {
                corner(heights, ring, cellX, cellY, i, j, to);
                corner(heights, ring, cellX, cellY, i, j + 1, to);
            }
            if (j < SIDE - 1) {
                corner(heights, ring, cellX, cellY, SIDE, j + 1, to);
            }
        }
    }

    private static void corner(float[] heights, int ring, int cellX, int cellY, int i, int j, ByteBuffer to) {
        int at = (j + 1) * ring + i + 1;
        float slopeX = (heights[at + 1] - heights[at - 1]) / (2f * STEP), slopeY = (heights[at + ring] - heights[at - ring]) / (2f * STEP);
        float length = (float) Math.sqrt(slopeX * slopeX + slopeY * slopeY + 1f);
        to.putFloat(cellX * CELL + i * STEP).putFloat(cellY * CELL + j * STEP).putFloat(heights[at]);
        to.putFloat(-slopeX / length).putFloat(-slopeY / length).putFloat(1f / length);
        to.putInt(0).putFloat(0f);
    }

    /**
     * The buildings of a cell as plain blocks: each floor a wall round the squares its rooms
     * cover, and a flat roof over whatever of it the floor above does not cover. Triangles.
     *
     * @param buildings each building as a number of its own, how many pieces its rooms are in, and each piece as x, y, tiles across, tiles down and floor
     * @param around    as toWater takes it, about the cell whose corner the tiles of (cellX, cellY) are counted from
     * @return the vertices, in a buffer of the machine's byte order that ends at the last of them
     */
    static ByteBuffer blocks(int[] buildings, int[][] around, int cellX, int cellY) {
        Out out = new Out();
        for (int at = 0; at < buildings.length; ) {
            int number = buildings[at], pieces = buildings[at + 1], first = at + 2;
            at = first + pieces * 5;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, top = -1;
            for (int piece = first; piece < at; piece += 5) {
                if (buildings[piece + 4] < 0) {
                    continue;
                }
                minX = Math.min(minX, buildings[piece]);
                minY = Math.min(minY, buildings[piece + 1]);
                maxX = Math.max(maxX, buildings[piece] + buildings[piece + 2]);
                maxY = Math.max(maxY, buildings[piece + 1] + buildings[piece + 3]);
                top = Math.max(top, buildings[piece + 4]);
            }
            if (top < 0) {
                continue;
            }
            // One square more all round, so every square of the building has a neighbour to be asked about.
            int wide = maxX - minX + 2, deep = maxY - minY + 2;
            float midX = (minX + maxX) / 2f, midY = (minY + maxY) / 2f;
            float pad = Terrain.far(midX, midY, toWater(around, midX - cellX * CELL, midY - cellY * CELL));
            int mixed = number * 0x9e3779b1;
            int[] walls = top + 1 >= TALL_FROM ? TALL_WALLS : WALLS;
            int wall = walls[(mixed >>> 8) % walls.length], roof = ROOFS[(mixed >>> 20) % ROOFS.length];
            boolean[] here = new boolean[wide * deep], above = new boolean[wide * deep];
            cover(buildings, first, at, 0, minX, minY, wide, here);
            for (int floor = 0; floor <= top; floor++) {
                Arrays.fill(above, false);
                cover(buildings, first, at, floor + 1, minX, minY, wide, above);
                float foot = floor == 0 ? -FOOTING : floor * ViewCamera.LEVEL_HEIGHT, head = (floor + 1) * ViewCamera.LEVEL_HEIGHT;
                walls(here, wide, deep, minX - 1, minY - 1, pad, foot, head, wall, out);
                roofs(here, above, wide, deep, minX - 1, minY - 1, pad, head, roof, out);
                boolean[] next = here;
                here = above;
                above = next;
            }
        }
        return out.done();
    }

    private static void cover(int[] buildings, int first, int end, int floor, int minX, int minY, int wide, boolean[] covered) {
        for (int piece = first; piece < end; piece += 5) {
            if (buildings[piece + 4] != floor) {
                continue;
            }
            for (int y = buildings[piece + 1]; y < buildings[piece + 1] + buildings[piece + 3]; y++) {
                int row = (y - minY + 1) * wide + buildings[piece] - minX + 1;
                Arrays.fill(covered, row, row + buildings[piece + 2], true);
            }
        }
    }

    /** A wall along every run of squares that are covered with the square beside them not, on each of the four sides. */
    private static void walls(boolean[] here, int wide, int deep, int fromX, int fromY, float pad, float foot, float head, int colour, Out out) {
        for (int j = 1; j < deep - 1; j++) {
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 1; i < wide - 1; i++) {
                    int start = i;
                    while (i < wide - 1 && here[j * wide + i] && !here[(j + side) * wide + i]) {
                        i++;
                    }
                    if (i > start) {
                        float y = fromY + j + (side > 0 ? 1 : 0);
                        out.wall(fromX + start, y, fromX + i, y, 0f, side, pad, foot, head, colour);
                    }
                }
            }
        }
        for (int i = 1; i < wide - 1; i++) {
            for (int side = -1; side <= 1; side += 2) {
                for (int j = 1; j < deep - 1; j++) {
                    int start = j;
                    while (j < deep - 1 && here[j * wide + i] && !here[j * wide + i + side]) {
                        j++;
                    }
                    if (j > start) {
                        float x = fromX + i + (side > 0 ? 1 : 0);
                        out.wall(x, fromY + start, x, fromY + j, side, 0f, pad, foot, head, colour);
                    }
                }
            }
        }
    }

    /** A roof over every run of squares that are covered with nothing over them, runs that lie one under the other the same way joined into one. */
    private static void roofs(boolean[] here, boolean[] above, int wide, int deep, int fromX, int fromY, float pad, float head, int colour, Out out) {
        // By the square a run starts at: the row it began in, and -1 where no run is growing.
        int[] open = new int[wide];
        Arrays.fill(open, -1);
        // The last row is of the squares round the building, where nothing is covered: every run ends by it.
        for (int j = 1; j < deep; j++) {
            for (int i = 1; i < wide; i++) {
                int start = i;
                while (i < wide - 1 && here[j * wide + i] && !above[j * wide + i]) {
                    i++;
                }
                if (i > start) {
                    out.roofRun(start, i, j, open, fromX, fromY, pad + head, colour);
                }
            }
            out.roofRowDone(j, open, wide, fromX, fromY, pad + head, colour);
        }
    }

    /** Vertices as they are made, in a buffer that grows. */
    private static final class Out {
        private ByteBuffer bytes = ByteBuffer.allocate(1 << 14).order(ByteOrder.nativeOrder());
        /** The runs of the row before that are still growing down: where each ends, by where it starts, and the row each began in. */
        private int[] ends = new int[0], began = new int[0];
        private boolean[] met = new boolean[0];

        private void room(int more) {
            if (bytes.remaining() < more) {
                bytes = ByteBuffer.allocate(Math.max(bytes.capacity() * 2, bytes.position() + more)).order(ByteOrder.nativeOrder()).put(bytes.flip());
            }
        }

        private void vertex(float x, float y, float z, float normalX, float normalY, float normalZ, int colour, float up) {
            bytes.putFloat(x).putFloat(y).putFloat(z).putFloat(normalX).putFloat(normalY).putFloat(normalZ);
            bytes.put((byte) (colour >> 16)).put((byte) (colour >> 8)).put((byte) colour).put((byte) BUILT).putFloat(up);
        }

        void wall(float x0, float y0, float x1, float y1, float normalX, float normalY, float pad, float foot, float head, int colour) {
            room(6 * VERTEX_BYTES);
            vertex(x0, y0, pad + foot, normalX, normalY, 0f, colour, foot);
            vertex(x1, y1, pad + foot, normalX, normalY, 0f, colour, foot);
            vertex(x1, y1, pad + head, normalX, normalY, 0f, colour, head);
            vertex(x0, y0, pad + foot, normalX, normalY, 0f, colour, foot);
            vertex(x1, y1, pad + head, normalX, normalY, 0f, colour, head);
            vertex(x0, y0, pad + head, normalX, normalY, 0f, colour, head);
        }

        private void roof(float x0, float y0, float x1, float y1, float z, int colour) {
            room(6 * VERTEX_BYTES);
            vertex(x0, y0, z, 0f, 0f, 1f, colour, 0f);
            vertex(x1, y0, z, 0f, 0f, 1f, colour, 0f);
            vertex(x1, y1, z, 0f, 0f, 1f, colour, 0f);
            vertex(x0, y0, z, 0f, 0f, 1f, colour, 0f);
            vertex(x1, y1, z, 0f, 0f, 1f, colour, 0f);
            vertex(x0, y1, z, 0f, 0f, 1f, colour, 0f);
        }

        /** A run of roof from start up to end in row j: it carries on the run over it if that one starts and ends where it does. */
        void roofRun(int start, int end, int j, int[] open, int fromX, int fromY, float z, int colour) {
            if (ends.length < open.length) {
                ends = new int[open.length];
                began = new int[open.length];
                met = new boolean[open.length];
            }
            if (open[start] >= 0 && ends[start] == end) {
                met[start] = true;
                return;
            }
            if (open[start] >= 0) {
                roof(fromX + start, fromY + began[start], fromX + ends[start], fromY + j, z, colour);
            }
            open[start] = j;
            began[start] = j;
            ends[start] = end;
            met[start] = true;
        }

        /** The end of row j: every run the row did not carry on is closed. */
        void roofRowDone(int j, int[] open, int wide, int fromX, int fromY, float z, int colour) {
            for (int start = 0; start < Math.min(wide, met.length); start++) {
                if (open[start] >= 0 && !met[start]) {
                    roof(fromX + start, fromY + began[start], fromX + ends[start], fromY + j, z, colour);
                    open[start] = -1;
                }
                met[start] = false;
            }
        }

        ByteBuffer done() {
            return bytes.flip();
        }
    }
}
