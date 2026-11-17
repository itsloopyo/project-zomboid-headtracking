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
    /** How much further under the country a corner of a chunk with water lies than the built world's water does, in tiles. */
    private static final float WATER_UNDER = 0.05f;
    /**
     * What the game's map paints a pavement, which is a brick pink there so that a street reads on a map, and the
     * concrete it is in the built world.
     */
    private static final int PAVEMENT = 0x8e5b56, CONCRETE = 0x8f8e8a;
    /** How far below the lowest land at its corners the foot of a building's wall goes: the land under it is not level, and the block stands at the height of its middle. */
    private static final float FOOTING = 1f;
    /** What the game's map paints a cell it has no map of, from edge to edge. */
    private static final int NOTHING = 0xa0a0a4;

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
        picture(pixels, null, to);
    }

    /**
     * As picture, with the ground the cell's lotpack lays (FarLot.ground) in place of the map's
     * picture wherever it has a floor: each square the colour of its floor and what is laid over
     * it, as the built world paints it. Water stays the map's colour for water, which the light
     * makes into water (PipelineShaders.FAR_LIGHT).
     *
     * @param ground the lot's ground, or null to take the map's picture alone
     */
    static void picture(int[] pixels, int[] ground, ByteBuffer to) {
        int from = to.position();
        for (int i = 0; i < CELL * CELL; i++) {
            int pixel = pixels == null ? GRASS : pixels[i] & 0xffffff;
            int laid = ground == null ? 0 : ground[i];
            boolean water = (laid & FarLot.LAID) != 0 ? (laid & FarLot.WET) != 0 : pixel == WATER;
            int shown = water ? WATER : (laid & FarLot.LAID) != 0 ? laid & 0xffffff : pixel == PAVEMENT ? CONCRETE : pixel;
            to.put((byte) (shown >> 16)).put((byte) (shown >> 8)).put((byte) shown).put((byte) (water ? 255 : 0));
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

    /** Whether a cell's picture is of nothing: the grey of where the map has none, all over. Such a cell is open grass, as one with no picture is. */
    static boolean nothing(int[] pixels) {
        for (int pixel : pixels) {
            if ((pixel & 0xffffff) != NOTHING) {
                return false;
            }
        }
        return true;
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
                if (!watered(around, cx, cy)) {
                    continue;
                }
                float awayX = Math.max(0f, Math.max(cx * STEP - x, x - cx * STEP - STEP));
                float awayY = Math.max(0f, Math.max(cy * STEP - y, y - cy * STEP - STEP));
                nearest = Math.min(nearest, (float) Math.sqrt(awayX * awayX + awayY * awayY));
            }
        }
        return nearest;
    }

    /** Whether the chunk (cx, cy), counted as toWater counts places, has water in it. */
    private static boolean watered(int[][] around, int cx, int cy) {
        int[] rows = around[(Math.floorDiv(cy, SIDE) + 1) * 3 + Math.floorDiv(cx, SIDE) + 1];
        return (rows[Math.floorMod(cy, SIDE)] >>> Math.floorMod(cx, SIDE) & 1) != 0;
    }

    /** The land of a cell: LAND_VERTICES vertices, drawn as one strip. */
    static void land(int[][] around, int cellX, int cellY, ByteBuffer to) {
        // One corner more all round, for the slope at the cell's own edge.
        int ring = CORNERS + 2;
        float[] heights = new float[ring * ring];
        for (int j = 0; j < ring; j++) {
            for (int i = 0; i < ring; i++) {
                float x = (i - 1) * STEP, y = (j - 1) * STEP;
                heights[j * ring + i] = Terrain.HILLS.farUnder(cellX * CELL + x, cellY * CELL + y, toWater(around, x, y));
                // A corner of a chunk with water in it lies under the country as the built
                // world's water does, and a little more: the built world thins out into the far
                // land pixel by pixel, each as far off as the eye's line meets it, and the far
                // land's water lying higher met it nearer, where it had not come in yet. Lying
                // lower it has come in, and is behind the built world while that is there; the
                // land of such a chunk with it, which is no further than that behind the built land.
                // Any of the four chunks that meet at the corner, (i - 2, j - 2) to (i - 1, j - 1).
                if (watered(around, i - 2, j - 2) || watered(around, i - 1, j - 2) || watered(around, i - 2, j - 1) || watered(around, i - 1, j - 1)) {
                    heights[j * ring + i] -= Terrain.WATER_BELOW + WATER_UNDER;
                }
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

    /** The buildings of a cell as blocks in a few colours, as blocks(buildings, around, cellX, cellY, null) makes them. */
    static ByteBuffer blocks(int[] buildings, int[][] around, int cellX, int cellY) {
        return blocks(buildings, around, cellX, cellY, null);
    }

    /**
     * The buildings of a cell: each floor a wall round the squares its rooms cover, and a roof
     * over whatever of it the floor above does not cover. Triangles, BLOCK_BYTES a vertex.
     *
     * With the cell's lot, each building wears its own. A floor's walls are the colour of the
     * walls the game lays along its south and east sides, the sides its sprites show the outside
     * of (WallSides), with a window wherever the game lays one, and its roof is the colour of the
     * roof's tiles, pitched as they slope (shape, pitched). Without it, a building is a block in
     * one of a few colours, with its windows in BAYS and a flat roof.
     *
     * @param buildings each building as a number of its own, how many pieces its rooms are in, and each piece as x, y, tiles across, tiles down and floor
     * @param around    as toWater takes it, about the cell whose corner the tiles of (cellX, cellY) are counted from
     * @param lot       what the cell's lotpack lays, or null
     * @return the vertices, in a buffer of the machine's byte order that ends at the last of them
     */
    static ByteBuffer blocks(int[] buildings, int[][] around, int cellX, int cellY, FarLot lot) {
        Out out = new Out();
        int baseX = cellX * CELL, baseY = cellY * CELL;
        for (int at = 0; at < buildings.length; ) {
            int number = buildings[at], pieces = buildings[at + 1], first = at + 2;
            at = first + pieces * 5;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, top = -1, lowest = Integer.MAX_VALUE;
            for (int piece = first; piece < at; piece += 5) {
                if (buildings[piece + 4] < 0) {
                    continue;
                }
                minX = Math.min(minX, buildings[piece]);
                minY = Math.min(minY, buildings[piece + 1]);
                maxX = Math.max(maxX, buildings[piece] + buildings[piece + 2]);
                maxY = Math.max(maxY, buildings[piece + 1] + buildings[piece + 3]);
                top = Math.max(top, buildings[piece + 4]);
                lowest = Math.min(lowest, buildings[piece + 4]);
            }
            if (top < 0) {
                continue;
            }
            // One square more all round, so every square of the building has a neighbour to be asked about.
            int wide = maxX - minX + 2, deep = maxY - minY + 2;
            float midX = (minX + maxX) / 2f, midY = (minY + maxY) / 2f;
            float pad = Terrain.HILLS.farUnder(midX, midY, toWater(around, midX - cellX * CELL, midY - cellY * CELL));
            float foot = 0f;
            for (int corner = 0; corner < 4; corner++) {
                float x = corner % 2 == 0 ? minX : maxX, y = corner < 2 ? minY : maxY;
                foot = Math.min(foot, Terrain.HILLS.farUnder(x, y, toWater(around, x - cellX * CELL, y - cellY * CELL)) - pad);
            }
            foot -= FOOTING;
            int mixed = number * 0x9e3779b1;
            int[] walls = top + 1 >= TALL_FROM ? TALL_WALLS : WALLS;
            int plainWall = walls[(mixed >>> 8) % walls.length], plainRoof = ROOFS[(mixed >>> 20) % ROOFS.length];
            int floors = top - lowest + 1;
            // What has no rooms on the ground stands on the ground all the same: a block in the air has no underside to show.
            boolean[][] covered = new boolean[floors + 1][];
            for (int floor = lowest; floor <= top + 1; floor++) {
                covered[floor - lowest] = new boolean[wide * deep];
                cover(buildings, first, at, floor, minX, minY, wide, covered[floor - lowest]);
            }
            int fromX = minX - 1, fromY = minY - 1;
            Paint paint = new Paint(lot, fromX - baseX, fromY - baseY, wide, plainWall);
            int[] finishes = new int[floors];
            Mean all = new Mean();
            for (int floor = lowest; floor <= top; floor++) {
                Mean seen = new Mean();
                paint.finishOf(covered[floor - lowest], deep, floor, seen);
                all.add(seen);
                finishes[floor - lowest] = seen.colour(-1);
            }
            int finish = all.colour(plainWall);
            Mean roofed = new Mean();
            paint.roofOf(covered, deep, Math.max(lowest, 0) + 1, top + Roof.ABOVE, roofed);
            int roof = roofed.colour(plainRoof);
            for (int floor = lowest; floor <= top; floor++) {
                boolean[] here = covered[floor - lowest], above = covered[floor + 1 - lowest];
                float from = floor == lowest ? foot : floor * ViewCamera.LEVEL_HEIGHT, head = (floor + 1) * ViewCamera.LEVEL_HEIGHT;
                paint.floor(floor, finishes[floor - lowest] < 0 ? finish : finishes[floor - lowest]);
                walls(here, wide, deep, fromX, fromY, pad, from, head, paint, out);
                if (lot == null) {
                    roofs(here, above, wide, deep, fromX, fromY, pad + head, plainRoof, out);
                } else {
                    roofs(here, above, wide, deep, fromX, fromY, pad, floor, roof, paint, out);
                }
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

    /** The most tiles one length of wall is made of: a window's place is a bit of a float the picture reads back whole (PipelineShaders.FAR_FRAGMENT). */
    static final int LONGEST_WALL = 16;
    /** The faces of a square a wall can stand along. */
    static final int NORTH = 0, SOUTH = 1, WEST = 2, EAST = 3;

    /**
     * A wall along every run of squares that are covered with the square beside them not, on
     * each of the four sides, in lengths of one colour of LONGEST_WALL tiles at most.
     */
    private static void walls(boolean[] here, int wide, int deep, int fromX, int fromY, float pad, float foot, float head, Paint paint, Out out) {
        for (int j = 1; j < deep - 1; j++) {
            for (int side = -1; side <= 1; side += 2) {
                int face = side < 0 ? NORTH : SOUTH;
                for (int i = 1; i < wide - 1; ) {
                    if (!here[j * wide + i] || here[(j + side) * wide + i]) {
                        i++;
                        continue;
                    }
                    int start = i, colour = paint.wall(i, j, face), windows = 0;
                    while (i < wide - 1 && here[j * wide + i] && !here[(j + side) * wide + i] && i - start < LONGEST_WALL && paint.wall(i, j, face) == colour) {
                        windows |= paint.window(i, j, face) ? 1 << i - start : 0;
                        i++;
                    }
                    float y = fromY + j + (side > 0 ? 1 : 0);
                    out.wall(fromX + start, y, fromX + i, y, 0f, side, pad, foot, head, colour, paint.lot == null ? BAYS : windows, i - start);
                }
            }
        }
        for (int i = 1; i < wide - 1; i++) {
            for (int side = -1; side <= 1; side += 2) {
                int face = side < 0 ? WEST : EAST;
                for (int j = 1; j < deep - 1; ) {
                    if (!here[j * wide + i] || here[j * wide + i + side]) {
                        j++;
                        continue;
                    }
                    int start = j, colour = paint.wall(i, j, face), windows = 0;
                    while (j < deep - 1 && here[j * wide + i] && !here[j * wide + i + side] && j - start < LONGEST_WALL && paint.wall(i, j, face) == colour) {
                        windows |= paint.window(i, j, face) ? 1 << j - start : 0;
                        j++;
                    }
                    float x = fromX + i + (side > 0 ? 1 : 0);
                    out.wall(x, fromY + start, x, fromY + j, side, 0f, pad, foot, head, colour, paint.lot == null ? BAYS : windows, j - start);
                }
            }
        }
    }

    /** A roof over every run of squares that are covered with nothing over them, runs that lie one under the other the same way joined into one. */
    private static void roofs(boolean[] here, boolean[] above, int wide, int deep, int fromX, int fromY, float z, int colour, Out out) {
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
                    out.roofRun(start, i, j, open, fromX, fromY, z, colour);
                }
            }
            out.roofRowDone(j, open, wide, fromX, fromY, z, colour);
        }
    }

    /**
     * The roof of a floor as the lot has it: over the squares the floor above does not cover,
     * a rectangle at a time (rectangles), each pitched as the roof tiles over it slope (shape)
     * or flat, the flat ones laid together as the plain roofs are.
     */
    private static void roofs(boolean[] here, boolean[] above, int wide, int deep, int fromX, int fromY, float pad, int floor, int colour, Paint paint, Out out) {
        boolean[] open = new boolean[wide * deep];
        for (int i = 0; i < open.length; i++) {
            open[i] = here[i] && !above[i];
        }
        boolean[] flat = open.clone();
        int[] rectangles = rectangles(open, wide, deep);
        float base = pad + (floor + 1) * ViewCamera.LEVEL_HEIGHT;
        for (int r = 0; r < rectangles.length; r += 4) {
            Roof over = paint.roofOver(rectangles[r], rectangles[r + 1], rectangles[r + 2], rectangles[r + 3], floor);
            if (over.lies == FarLot.FLAT) {
                continue;
            }
            for (int j = rectangles[r + 1]; j < rectangles[r + 1] + rectangles[r + 3]; j++) {
                Arrays.fill(flat, j * wide + rectangles[r], j * wide + rectangles[r] + rectangles[r + 2], false);
            }
            float x0 = fromX + rectangles[r], y0 = fromY + rectangles[r + 1];
            out.pitched(x0, y0, x0 + rectangles[r + 2], y0 + rectangles[r + 3], base, over.cap * ViewCamera.LEVEL_HEIGHT, over.lies, colour, paint.floorFinish, pad);
        }
        roofs(flat, new boolean[flat.length], wide, deep, fromX, fromY, base, colour, out);
    }

    /**
     * Rectangles that between them cover every square marked in region, each grown from the
     * first square no rectangle before it covers as far as the region lets it, so they may
     * overlap: x, y, tiles across and tiles down for each, in squares of the grid.
     */
    static int[] rectangles(boolean[] region, int wide, int deep) {
        boolean[] left = region.clone();
        int[] found = new int[16];
        int count = 0;
        for (int start = 0; start < left.length; start++) {
            if (!left[start]) {
                continue;
            }
            int x0 = start % wide, y0 = start / wide, x1 = x0 + 1, y1 = y0 + 1;
            for (boolean grew = true; grew; ) {
                grew = false;
                while (x1 < wide && filled(region, wide, x1, y0, x1 + 1, y1)) {
                    x1++;
                    grew = true;
                }
                while (y1 < deep && filled(region, wide, x0, y1, x1, y1 + 1)) {
                    y1++;
                    grew = true;
                }
                while (x0 > 0 && filled(region, wide, x0 - 1, y0, x0, y1)) {
                    x0--;
                    grew = true;
                }
                while (y0 > 0 && filled(region, wide, x0, y0 - 1, x1, y0)) {
                    y0--;
                    grew = true;
                }
            }
            for (int y = y0; y < y1; y++) {
                Arrays.fill(left, y * wide + x0, y * wide + x1, false);
            }
            if (count + 4 > found.length) {
                found = Arrays.copyOf(found, found.length * 2);
            }
            found[count++] = x0;
            found[count++] = y0;
            found[count++] = x1 - x0;
            found[count++] = y1 - y0;
        }
        return Arrays.copyOf(found, count);
    }

    private static boolean filled(boolean[] region, int wide, int x0, int y0, int x1, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                if (!region[y * wide + x]) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * How a roof lies by how many of its tiles lie each way: FarLot.FLAT, ALONG_Y for a ridge
     * along x with the slopes rising from the north and south, ALONG_X the other way, or MEET
     * for a hip. The ridge runs the way most of its slopes say, and the roof is a hip where tiles
     * of a hip's corner (FarLot.MEET) lie at the ends of that ridge, a gable where none do,
     * however long the roof is.
     *
     * @param meetEndsX how many corner tiles lie at the ends of a ridge along x: within half the roof's depth of its east or west end
     * @param meetEndsY the same for a ridge along y
     */
    static int shape(int flat, int alongX, int alongY, int meet, int meetEndsX, int meetEndsY) {
        int slopes = alongX + alongY + meet;
        if (slopes < 2 || slopes < flat) {
            return FarLot.FLAT;
        }
        if (alongY >= alongX) {
            return meetEndsX > 0 ? FarLot.MEET : FarLot.ALONG_Y;
        }
        return meetEndsY > 0 ? FarLot.MEET : FarLot.ALONG_X;
    }

    /** How the roof over a rectangle lies, and how far its highest tile stands over its eaves, in levels. */
    record Roof(int lies, float cap) {
        /** How many levels over the floor under it a roof's tiles are looked for: a roof of the steepest pitch over a building 18 tiles across reaches into the third. */
        static final int ABOVE = 3;
    }

    /** A sum of colours. */
    private static final class Mean {
        long red, green, blue;
        int count;

        void add(int colour) {
            red += colour >> 16 & 0xff;
            green += colour >> 8 & 0xff;
            blue += colour & 0xff;
            count++;
        }

        void add(Mean other) {
            red += other.red;
            green += other.green;
            blue += other.blue;
            count += other.count;
        }

        /** The mean of what was added, or otherwise for nothing. */
        int colour(int otherwise) {
            if (count == 0) {
                return otherwise;
            }
            return (int) ((red + count / 2) / count) << 16 | (int) ((green + count / 2) / count) << 8 | (int) ((blue + count / 2) / count);
        }
    }

    /** Two colours further apart than this in any of red, green and blue are two paints on a wall. */
    private static final int OTHER_PAINT = 24;

    /**
     * What one building's walls are painted and where their windows are, for each floor, read
     * off the lot by squares of the building's grid. Without a lot, its plain colour, and a
     * window every other tile.
     */
    private static final class Paint {
        final FarLot lot;
        /** The grid's corner within the cell, and its width. */
        final int cornerX, cornerY, wide;
        final int plain;
        int floor, floorFinish;

        Paint(FarLot lot, int cornerX, int cornerY, int wide, int plain) {
            this.lot = lot;
            this.cornerX = cornerX;
            this.cornerY = cornerY;
            this.wide = wide;
            this.plain = plain;
            floorFinish = plain;
        }

        void floor(int floor, int finish) {
            this.floor = floor;
            floorFinish = lot == null ? plain : finish;
        }

        /** The edge of the face of the grid's square (i, j) on the floor, as the lot has it: a wall on a square's south or east edge stands on the square past it. */
        private int edge(int i, int j, int face) {
            int x = cornerX + i, y = cornerY + j;
            return switch (face) {
                case NORTH -> lot.north(x, y, floor);
                case SOUTH -> lot.north(x, y + 1, floor);
                case WEST -> lot.west(x, y, floor);
                default -> lot.west(x + 1, y, floor);
            };
        }

        /** The colour of the wall along a face of (i, j): the floor's, or a south or east wall's own where it is plainly another paint. */
        int wall(int i, int j, int face) {
            if (lot == null || face == NORTH || face == WEST) {
                return floorFinish;
            }
            int edge = edge(i, j, face);
            if ((edge & FarLot.PAINTED) == 0) {
                return floorFinish;
            }
            int own = edge & 0xffffff;
            boolean other = Math.abs((own >> 16 & 0xff) - (floorFinish >> 16 & 0xff)) > OTHER_PAINT || Math.abs((own >> 8 & 0xff) - (floorFinish >> 8 & 0xff)) > OTHER_PAINT
                    || Math.abs((own & 0xff) - (floorFinish & 0xff)) > OTHER_PAINT;
            return other ? own : floorFinish;
        }

        boolean window(int i, int j, int face) {
            return lot != null && (edge(i, j, face) & FarLot.GLAZED) != 0;
        }

        /** Adds the colours of the walls the lot lays on the south and east sides of a floor's squares, the sides whose sprites are painted outside. */
        void finishOf(boolean[] here, int deep, int floor, Mean into) {
            if (lot == null) {
                return;
            }
            this.floor = floor;
            for (int j = 1; j < deep - 1; j++) {
                for (int i = 1; i < wide - 1; i++) {
                    if (!here[j * wide + i]) {
                        continue;
                    }
                    if (!here[(j + 1) * wide + i]) {
                        paintOf(edge(i, j, SOUTH), into);
                    }
                    if (!here[j * wide + i + 1]) {
                        paintOf(edge(i, j, EAST), into);
                    }
                }
            }
        }

        private static void paintOf(int edge, Mean into) {
            if ((edge & FarLot.PAINTED) != 0) {
                into.add(edge & 0xffffff);
            }
        }

        /** Adds the colours of the roof tiles the lot lays over the squares any floor of the building covers, on the levels from first to last. */
        void roofOf(boolean[][] covered, int deep, int first, int last, Mean into) {
            if (lot == null) {
                return;
            }
            for (int z = first; z <= last; z++) {
                for (int j = 0; j < deep; j++) {
                    for (int i = 0; i < wide; i++) {
                        boolean building = false;
                        for (boolean[] floor : covered) {
                            building |= floor[j * wide + i];
                        }
                        if (!building) {
                            continue;
                        }
                        int roof = lot.roof(cornerX + i, cornerY + j, z);
                        if ((roof & FarLot.ROOF_PAINTED) != 0) {
                            into.add(roof & 0xffffff);
                        }
                    }
                }
            }
        }

        /** How the roof over the rectangle of the grid from (i, j), tiles across and down, on the floor lies, by the tiles the lot lays over it. */
        Roof roofOver(int i0, int j0, int across, int down, int floor) {
            int flat = 0, alongX = 0, alongY = 0, meet = 0, meetEndsX = 0, meetEndsY = 0;
            float cap = 0f;
            for (int z = floor + 1; z <= floor + Roof.ABOVE; z++) {
                for (int j = j0; j < j0 + down; j++) {
                    for (int i = i0; i < i0 + across; i++) {
                        int roof = lot.roof(cornerX + i, cornerY + j, z);
                        int lies = roof >>> FarLot.LIES_SHIFT & 7;
                        if (roof == 0 || lies == FarLot.NONE || lies == FarLot.SHEET) {
                            continue;
                        }
                        flat += lies == FarLot.FLAT ? 1 : 0;
                        alongX += lies == FarLot.ALONG_X ? 1 : 0;
                        alongY += lies == FarLot.ALONG_Y ? 1 : 0;
                        if (lies == FarLot.MEET) {
                            meet++;
                            // A hip's end slopes in from the end of its ridge by half the roof's breadth.
                            int fromEndX = Math.min(i - i0, i0 + across - 1 - i), fromEndY = Math.min(j - j0, j0 + down - 1 - j);
                            meetEndsX += fromEndX <= down / 2 ? 1 : 0;
                            meetEndsY += fromEndY <= across / 2 ? 1 : 0;
                        }
                        cap = Math.max(cap, z - floor - 1 + (roof >>> FarLot.TOP_SHIFT & 15) / FarTiles.TOP_STEPS);
                    }
                }
            }
            return new Roof(cap <= 0f ? FarLot.FLAT : shape(flat, alongX, alongY, meet, meetEndsX, meetEndsY), cap);
        }
    }

    /** Bytes a vertex of a building takes: a place, the way its surface faces, a colour, and the place on its wall (Out.vertex). */
    static final int BLOCK_BYTES = 32;

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

        /**
         * A vertex: its place, the way it faces as three signed bytes and one spare, its colour with
         * what kind of face it is in alpha (BUILT and over: a wall, alpha less BUILT the share of its
         * length that is windows in 127ths; ROOFED: a roof), and three floats: how far up its
         * building it is, how far along its length of wall from the length's start in tiles, and
         * which of the length's tiles have a window, a bit a tile from the start, or BAYS.
         */
        private void vertex(float x, float y, float z, float normalX, float normalY, float normalZ, int colour, int kind, float up, float along, int windows) {
            bytes.putFloat(x).putFloat(y).putFloat(z);
            bytes.put(normal(normalX)).put(normal(normalY)).put(normal(normalZ)).put((byte) 0);
            bytes.put((byte) (colour >> 16)).put((byte) (colour >> 8)).put((byte) colour).put((byte) kind);
            bytes.putFloat(up).putFloat(along).putFloat(windows);
        }

        private static byte normal(float value) {
            return (byte) Math.round(value * 127f);
        }

        void wall(float x0, float y0, float x1, float y1, float normalX, float normalY, float pad, float foot, float head, int colour, int windows, int tiles) {
            room(6 * BLOCK_BYTES);
            int kind = windows == BAYS ? BUILT : BUILT + Math.round((ROOFED - 1 - BUILT) * Integer.bitCount(windows) / (float) tiles);
            float length = tiles;
            vertex(x0, y0, pad + foot, normalX, normalY, 0f, colour, kind, foot, 0f, windows);
            vertex(x1, y1, pad + foot, normalX, normalY, 0f, colour, kind, foot, length, windows);
            vertex(x1, y1, pad + head, normalX, normalY, 0f, colour, kind, head, length, windows);
            vertex(x0, y0, pad + foot, normalX, normalY, 0f, colour, kind, foot, 0f, windows);
            vertex(x1, y1, pad + head, normalX, normalY, 0f, colour, kind, head, length, windows);
            vertex(x0, y0, pad + head, normalX, normalY, 0f, colour, kind, head, 0f, windows);
        }

        private void roof(float x0, float y0, float x1, float y1, float z, int colour) {
            room(6 * BLOCK_BYTES);
            vertex(x0, y0, z, 0f, 0f, 1f, colour, ROOFED, 0f, 0f, 0);
            vertex(x1, y0, z, 0f, 0f, 1f, colour, ROOFED, 0f, 0f, 0);
            vertex(x1, y1, z, 0f, 0f, 1f, colour, ROOFED, 0f, 0f, 0);
            vertex(x0, y0, z, 0f, 0f, 1f, colour, ROOFED, 0f, 0f, 0);
            vertex(x1, y1, z, 0f, 0f, 1f, colour, ROOFED, 0f, 0f, 0);
            vertex(x0, y1, z, 0f, 0f, 1f, colour, ROOFED, 0f, 0f, 0);
        }

        /** Two triangles, a to b to c and a to c to d, all facing (normalX, normalY, normalZ), unnormalised. */
        private void quad(float[] a, float[] b, float[] c, float[] d, float normalX, float normalY, float normalZ, int colour, int kind, float pad) {
            float length = (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
            float nx = normalX / length, ny = normalY / length, nz = normalZ / length;
            room(6 * BLOCK_BYTES);
            for (float[] corner : new float[][] {a, b, c, a, c, d}) {
                vertex(corner[0], corner[1], corner[2], nx, ny, nz, colour, kind, corner[2] - pad, 0f, 0);
            }
        }

        /**
         * A pitched roof over the rectangle from (x0, y0) to (x1, y1) with its eaves at base,
         * rising at the game's pitch (RoofFit.PITCH) from the two sides lies says, or from all
         * four for MEET, to its ridge, or to cap over its eaves where that is lower and level
         * past it. A ridge's ends are gables in the floor's wall colour.
         */
        void pitched(float x0, float y0, float x1, float y1, float base, float cap, int lies, int colour, int wall, float pad) {
            float pitch = RoofFit.PITCH * ViewCamera.LEVEL_HEIGHT;
            if (lies == FarLot.MEET) {
                float rise = Math.min(pitch * Math.min(x1 - x0, y1 - y0) / 2f, cap), run = rise / pitch, top = base + rise;
                float[] nw = {x0, y0, base}, ne = {x1, y0, base}, se = {x1, y1, base}, sw = {x0, y1, base};
                float[] inNw = {x0 + run, y0 + run, top}, inNe = {x1 - run, y0 + run, top}, inSe = {x1 - run, y1 - run, top}, inSw = {x0 + run, y1 - run, top};
                quad(nw, ne, inNe, inNw, 0f, -pitch, 1f, colour, ROOFED, pad);
                quad(se, sw, inSw, inSe, 0f, pitch, 1f, colour, ROOFED, pad);
                quad(sw, nw, inNw, inSw, -pitch, 0f, 1f, colour, ROOFED, pad);
                quad(ne, se, inSe, inNe, pitch, 0f, 1f, colour, ROOFED, pad);
                if (inNe[0] > inNw[0] + 1e-3f && inSw[1] > inNw[1] + 1e-3f) {
                    quad(inNw, inNe, inSe, inSw, 0f, 0f, 1f, colour, ROOFED, pad);
                }
                return;
            }
            // Turned so that the slopes rise along v, from v0 and v1, and the ridge runs along u.
            boolean alongY = lies == FarLot.ALONG_Y;
            float u0 = alongY ? x0 : y0, u1 = alongY ? x1 : y1, v0 = alongY ? y0 : x0, v1 = alongY ? y1 : x1;
            float rise = Math.min(pitch * (v1 - v0) / 2f, cap), run = rise / pitch, top = base + rise;
            float[] a0 = place(u0, v0, base, alongY), a1 = place(u1, v0, base, alongY), b0 = place(u0, v1, base, alongY), b1 = place(u1, v1, base, alongY);
            float[] ra0 = place(u0, v0 + run, top, alongY), ra1 = place(u1, v0 + run, top, alongY), rb0 = place(u0, v1 - run, top, alongY), rb1 = place(u1, v1 - run, top, alongY);
            quad(a0, a1, ra1, ra0, alongY ? 0f : -pitch, alongY ? -pitch : 0f, 1f, colour, ROOFED, pad);
            quad(b1, b0, rb0, rb1, alongY ? 0f : pitch, alongY ? pitch : 0f, 1f, colour, ROOFED, pad);
            if (v1 - run > v0 + run + 1e-3f) {
                quad(ra0, ra1, rb1, rb0, 0f, 0f, 1f, colour, ROOFED, pad);
            }
            quad(a0, ra0, rb0, b0, alongY ? -1f : 0f, alongY ? 0f : -1f, 0f, wall, BUILT, pad);
            quad(a1, ra1, rb1, b1, alongY ? 1f : 0f, alongY ? 0f : 1f, 0f, wall, BUILT, pad);
        }

        /** A place on the map from u along a ridge and v across it, for a ridge along x (alongY) or along y. */
        private static float[] place(float u, float v, float z, boolean alongY) {
            return alongY ? new float[] {u, v, z} : new float[] {v, u, z};
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

    /** In a building's vertex's alpha: a wall with no windows, to one all windows at ROOFED - 1, and a roof. The land's is 0. */
    static final int BUILT = 128, ROOFED = 255;
    /** The windows of a wall built with nothing of its lot read: one to each bay of two tiles, wherever the bay falls on it. */
    static final int BAYS = -1;
}
