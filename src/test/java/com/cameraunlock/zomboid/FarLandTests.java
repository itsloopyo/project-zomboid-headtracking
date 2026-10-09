package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

final class FarLandTests {
    private FarLandTests() {}

    private static final int[][] DRY = new int[9][];

    static {
        Arrays.setAll(DRY, i -> new int[FarCells.SIDE]);
    }

    static void run() {
        Checks.run("aPictureIsHalvedDownToOnePixel", FarLandTests::aPictureIsHalvedDownToOnePixel);
        Checks.run("waterIsFoundByTheChunk", FarLandTests::waterIsFoundByTheChunk);
        Checks.run("theLandLiesAsTheBuiltWorldsDoes", FarLandTests::theLandLiesAsTheBuiltWorldsDoes);
        Checks.run("waterLiesUnderTheBuiltWorldsWater", FarLandTests::waterLiesUnderTheBuiltWorldsWater);
        Checks.run("aBuildingIsABlockOfItsFloors", FarLandTests::aBuildingIsABlockOfItsFloors);
        Checks.run("aFloorSmallerThanTheOneUnderItLeavesARoofBeside", FarLandTests::aFloorSmallerThanTheOneUnderItLeavesARoofBeside);
        Checks.run("aCellarAloneIsNoBuilding", FarLandTests::aCellarAloneIsNoBuilding);
        Checks.run("roomsOverNothingStandOnTheGround", FarLandTests::roomsOverNothingStandOnTheGround);
        Checks.run("aCellOfNothingIsToldFromOneWithSomething", FarLandTests::aCellOfNothingIsToldFromOneWithSomething);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static ByteBuffer buffer(int bytes) {
        return ByteBuffer.allocate(bytes).order(ByteOrder.nativeOrder());
    }

    private static void aPictureIsHalvedDownToOnePixel() {
        int[] pixels = new int[FarCells.CELL * FarCells.CELL];
        Arrays.fill(pixels, 0xff204060);
        pixels[0] = 0xff000000 | FarCells.WATER;
        pixels[1] = 0xff8e5b56;
        ByteBuffer to = buffer(FarCells.PICTURE_BYTES);
        FarCells.picture(pixels, to);
        check(to.position() == FarCells.PICTURE_BYTES, "a picture and its halvings fill what is kept for them, and wrote " + to.position());
        check((to.get(0) & 0xff) == 0x6c && (to.get(3) & 0xff) == 255, "a tile of water keeps its colour and is marked as water");
        check((to.get(4) & 0xff) == 0x8f && (to.get(7) & 0xff) == 0, "a pavement is concrete and is not water");
        check((to.get(8) & 0xff) == 0x20 && (to.get(9) & 0xff) == 0x40 && (to.get(10) & 0xff) == 0x60, "any other tile keeps its colour, red first");
        int last = FarCells.PICTURE_BYTES - 4;
        check((to.get(last) & 0xff) == 0x20 && (to.get(last + 2) & 0xff) == 0x60, "the last halving is one pixel of what nearly all the cell is");
        int half = FarCells.CELL * FarCells.CELL * 4;
        check((to.get(half + 3) & 0xff) == 64, "a pixel of the first halving is a quarter water where one of its four tiles is, and was " + (to.get(half + 3) & 0xff));

        ByteBuffer none = buffer(FarCells.PICTURE_BYTES);
        FarCells.picture(null, none);
        check((none.get(0) & 0xff) == 0x4b && (none.get(1) & 0xff) == 0x58 && (none.get(2) & 0xff) == 0x1b, "a cell the map has no picture of is open grass");
    }

    private static void waterIsFoundByTheChunk() {
        int[] pixels = new int[FarCells.CELL * FarCells.CELL];
        pixels[250 * FarCells.CELL + 17] = 0xff000000 | FarCells.WATER;
        int[] rows = FarCells.waters(pixels);
        for (int row = 0; row < FarCells.SIDE; row++) {
            check(rows[row] == (row == 31 ? 1 << 2 : 0), "one tile of water marks its own chunk and no other, row " + row);
        }
        check(FarCells.toWater(DRY, 100f, 100f) == Terrain.VALLEY, "with no water about, it is a valley's width away");

        int[][] around = new int[9][];
        Arrays.setAll(around, i -> new int[FarCells.SIDE]);
        around[4][0] = 1;
        check(FarCells.toWater(around, 20f, 4f) == 12f, "water in the chunk at the cell's corner is 12 tiles from a place 20 along");
        check(FarCells.toWater(around, 4f, 4f) == 0f, "and no way from a place in that chunk");
        Arrays.fill(around[4], 0);
        // The cell to the west, its last chunk along the top row.
        around[3][0] = 1 << FarCells.SIDE - 1;
        check(FarCells.toWater(around, 3f, 4f) == 3f, "water in the next cell is found across the edge");
    }

    private static void theLandLiesAsTheBuiltWorldsDoes() {
        ByteBuffer to = buffer(FarCells.LAND_VERTICES * FarCells.VERTEX_BYTES);
        FarCells.land(DRY, 49, 5, to);
        check(to.position() == to.capacity(), "the land of a cell is as many vertices as it is drawn as");
        check(to.getFloat(0) == 49 * 256f && to.getFloat(4) == 5 * 256f, "the first is the cell's own corner on the map");
        check(to.getFloat(8) == Terrain.far(49 * 256f, 5 * 256f, Terrain.VALLEY), "at the height the land has there");
        int last = (FarCells.LAND_VERTICES - 1) * FarCells.VERTEX_BYTES;
        check(to.getFloat(last) == 50 * 256f && to.getFloat(last + 4) == 6 * 256f, "and the last is the corner across from it");
        for (int at = 0; at < to.capacity(); at += FarCells.VERTEX_BYTES) {
            float x = to.getFloat(at + 12), y = to.getFloat(at + 16), z = to.getFloat(at + 20);
            check(Math.abs(x * x + y * y + z * z - 1f) < 1e-4f && z > 0.9f, "the land faces up, a little off it on a slope");
            check(to.get(at + 27) == 0, "and is marked as land");
        }
    }

    private static void waterLiesUnderTheBuiltWorldsWater() {
        // A river one chunk wide across the middle of the cell, with land either side of it.
        int[][] river = new int[9][];
        Arrays.setAll(river, i -> new int[FarCells.SIDE]);
        river[4][FarCells.SIDE / 2] = -1;
        ByteBuffer to = buffer(FarCells.LAND_VERTICES * FarCells.VERTEX_BYTES);
        FarCells.land(river, 49, 5, to);
        int corners = 0;
        for (int at = 0; at < to.capacity(); at += FarCells.VERTEX_BYTES) {
            float x = to.getFloat(at), y = to.getFloat(at + 4), up = to.getFloat(at + 8);
            float chunkY = (y - 5 * 256f) / FarCells.STEP;
            if (chunkY == FarCells.SIDE / 2f || chunkY == FarCells.SIDE / 2f + 1f) {
                corners++;
                check(up < Terrain.HILLS.waterLevel(x, y) - 0.04f, "a corner of the river's chunks lies under the built world's water at " + x + ", " + y + ": " + up);
            } else if (chunkY < FarCells.SIDE / 2f - 1f || chunkY > FarCells.SIDE / 2f + 2f) {
                check(up == Terrain.far(x, y, FarCells.toWater(river, x - 49 * 256f, y - 5 * 256f)), "and the land well off it is the country at " + x + ", " + y);
            }
        }
        check(corners > 0, "the river has corners");
    }

    /** The area of the triangles that face up, and the highest any vertex is. */
    private static float[] roofs(ByteBuffer blocks) {
        float area = 0f, top = -Float.MAX_VALUE;
        for (int at = 0; at < blocks.limit(); at += 3 * FarCells.VERTEX_BYTES) {
            float[] x = new float[3], y = new float[3];
            for (int corner = 0; corner < 3; corner++) {
                int vertex = at + corner * FarCells.VERTEX_BYTES;
                x[corner] = blocks.getFloat(vertex);
                y[corner] = blocks.getFloat(vertex + 4);
                top = Math.max(top, blocks.getFloat(vertex + 8));
                check((blocks.get(vertex + 27) & 0xff) == 255, "a building's vertex is marked as built");
            }
            if (blocks.getFloat(at + 20) > 0.5f) {
                area += Math.abs((x[1] - x[0]) * (y[2] - y[0]) - (x[2] - x[0]) * (y[1] - y[0])) / 2f;
            }
        }
        return new float[] {area, top};
    }

    private static void aBuildingIsABlockOfItsFloors() {
        int[] buildings = {7, 1, 12600, 1300, 4, 3, 0};
        ByteBuffer blocks = FarCells.blocks(buildings, DRY, 49, 5);
        check(blocks.limit() == 5 * 6 * FarCells.VERTEX_BYTES, "one room on one floor is four walls and a roof, and was " + blocks.limit() / FarCells.VERTEX_BYTES + " vertices");
        float[] roofs = roofs(blocks);
        float pad = Terrain.far(12602f, 1301.5f, Terrain.VALLEY);
        check(roofs[0] == 12f, "the roof covers the room's 12 squares, and covered " + roofs[0]);
        check(Math.abs(roofs[1] - pad - ViewCamera.LEVEL_HEIGHT) < 1e-4f, "one floor above the land in the middle of it");
    }

    private static void aFloorSmallerThanTheOneUnderItLeavesARoofBeside() {
        // Two rooms side by side downstairs, one of them with a room over it.
        int[] buildings = {9, 3, 12600, 1300, 2, 4, 0, 12602, 1300, 2, 4, 0, 12600, 1300, 2, 4, 1};
        ByteBuffer blocks = FarCells.blocks(buildings, DRY, 49, 5);
        check(blocks.limit() == 10 * 6 * FarCells.VERTEX_BYTES, "two floors of four walls each and a roof each, with no wall between two rooms of a floor, and were "
                + blocks.limit() / (6 * FarCells.VERTEX_BYTES) + " faces");
        check(roofs(blocks)[0] == 16f, "the roofs together cover the building's 16 squares once");
    }

    private static void roomsOverNothingStandOnTheGround() {
        int[] buildings = {5, 1, 12600, 1300, 4, 3, 1};
        ByteBuffer blocks = FarCells.blocks(buildings, DRY, 49, 5);
        check(blocks.limit() == 5 * 6 * FarCells.VERTEX_BYTES, "a room with none under it is four walls and a roof");
        float pad = Terrain.far(12602f, 1301.5f, Terrain.VALLEY), lowest = Float.MAX_VALUE;
        for (int at = 0; at < blocks.limit(); at += FarCells.VERTEX_BYTES) {
            lowest = Math.min(lowest, blocks.getFloat(at + 8));
        }
        check(lowest < pad, "and its walls go down into the ground, where they stopped a floor up in the air");
    }

    private static void aCellOfNothingIsToldFromOneWithSomething() {
        int[] pixels = new int[FarCells.CELL * FarCells.CELL];
        Arrays.fill(pixels, 0xffa0a0a4);
        check(FarCells.nothing(pixels), "a picture that is the map's grey all over is of nothing");
        pixels[777] = 0xff000000 | FarCells.GRASS;
        check(!FarCells.nothing(pixels), "and one tile of anything else makes it a picture of somewhere");
    }

    private static void aCellarAloneIsNoBuilding() {
        int[] buildings = {3, 1, 12600, 1300, 4, 3, -1};
        check(FarCells.blocks(buildings, DRY, 49, 5).limit() == 0, "rooms under the ground are no block on it");
    }
}
