package com.cameraunlock.zomboid;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

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
        Checks.run("anIndexIsReadNoFurtherThanAnIndexRuns", FarLandTests::anIndexIsReadNoFurtherThanAnIndexRuns);
        Checks.run("aTileLargerThanACellIsRefusedBeforeItsDots", FarLandTests::aTileLargerThanACellIsRefusedBeforeItsDots);
        Checks.run("aCellsBoxHoldsEveryPointOfIt", FarLandTests::aCellsBoxHoldsEveryPointOfIt);
    }

    private static void anIndexIsReadNoFurtherThanAnIndexRuns() throws IOException {
        Path zip = Path.of("pyramid.zip");
        String game = "VERSION=1\nbounds=0 0 19968 16128\nimageSize=19968 16128";
        check(FarLand.indexText(new ByteArrayInputStream(game.getBytes(StandardCharsets.UTF_8)), zip).equals(game), "the game's own index is read as it is");
        // Endless digits and no line end, as a tiny zip entry can unpack to.
        InputStream endless = new InputStream() {
            @Override
            public int read() {
                return '7';
            }
        };
        boolean refused = false;
        try {
            FarLand.indexText(endless, zip);
        } catch (IOException expected) {
            refused = true;
        }
        check(refused, "an index larger than " + FarLand.MOST_INDEX_BYTES + " bytes is refused");
    }

    private static void aTileLargerThanACellIsRefusedBeforeItsDots() throws IOException {
        BufferedImage cell = new BufferedImage(FarCells.CELL, FarCells.CELL, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream written = new ByteArrayOutputStream();
        ImageIO.write(cell, "png", written);
        byte[] png = written.toByteArray();
        BufferedImage read = FarLand.tile(new ByteArrayInputStream(png), "a cell");
        check(read.getWidth() == FarCells.CELL && read.getHeight() == FarCells.CELL, "a tile a cell across is read");
        // The header claims 20000 a side and no dots follow: reading it whole would take 1.6 GB first.
        byte[] huge = Arrays.copyOf(png, 33);
        ByteBuffer.wrap(huge, 16, 8).putInt(20000).putInt(20000);
        CRC32 crc = new CRC32();
        crc.update(huge, 12, 17);
        ByteBuffer.wrap(huge, 29, 4).putInt((int) crc.getValue());
        boolean refused = false;
        try {
            FarLand.tile(new ByteArrayInputStream(huge), "a claimed 20000 a side");
        } catch (IOException expected) {
            refused = expected.getMessage().contains("20000 by 20000");
        }
        check(refused, "a tile whose header claims 20000 a side is refused by its size");
        boolean unreadable = false;
        try {
            FarLand.tile(new ByteArrayInputStream(new byte[] {1, 2, 3}), "three bytes");
        } catch (IOException expected) {
            unreadable = true;
        }
        check(unreadable, "a tile that is no picture is refused");
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
        check(to.getFloat(8) == Terrain.HILLS.far(49 * 256f, 5 * 256f, Terrain.VALLEY), "at the height the land has there");
        float atOne = to.getFloat(8);
        try {
            Terrain.HILLS.heighten(1.5f);
            ByteBuffer higher = buffer(FarCells.LAND_VERTICES * FarCells.VERTEX_BYTES);
            FarCells.land(DRY, 49, 5, higher);
            check(higher.getFloat(8) == Terrain.HILLS.far(49 * 256f, 5 * 256f, Terrain.VALLEY) && Math.abs(higher.getFloat(8) - 1.5f * atOne) < 1e-5f,
                    "and with the hills half as high again, at the height the built land then has there: " + higher.getFloat(8) + " from " + atOne);
        } finally {
            Terrain.HILLS.heighten(1f);
        }
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
                check(up == Terrain.HILLS.far(x, y, FarCells.toWater(river, x - 49 * 256f, y - 5 * 256f)), "and the land well off it is the country at " + x + ", " + y);
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
        float pad = Terrain.HILLS.far(12602f, 1301.5f, Terrain.VALLEY);
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
        float pad = Terrain.HILLS.far(12602f, 1301.5f, Terrain.VALLEY), lowest = Float.MAX_VALUE;
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

    private static void aCellsBoxHoldsEveryPointOfIt() {
        ByteBuffer bytes = ByteBuffer.allocate(8 + 3 * FarCells.VERTEX_BYTES).order(ByteOrder.nativeOrder());
        float[][] points = {{512f, 300f, 2f}, {700f, 260f, -1f}, {600f, 511f, 14f}};
        for (int i = 0; i < points.length; i++) {
            int at = 8 + i * FarCells.VERTEX_BYTES;
            bytes.putFloat(at, points[i][0]).putFloat(at + 4, points[i][1]).putFloat(at + 8, points[i][2]);
            bytes.putFloat(at + 12, 99f);
        }
        float[] box = new float[12];
        FarLand.box(bytes, 8, points.length, FarCells.VERTEX_BYTES, box, 6);
        Checks.check(Arrays.equals(Arrays.copyOfRange(box, 6, 12), new float[] {512f, 260f, -1f, 700f, 511f, 14f}),
                "the box is from the least of each to the greatest, read where the points are: " + Arrays.toString(box));
    }
}
