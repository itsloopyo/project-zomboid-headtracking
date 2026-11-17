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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import zombie.core.properties.PropertyContainer;
import zombie.core.textures.Texture;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteManager;

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
        Checks.run("theLandLiesUnderTheHollowsOfOpenGround", FarLandTests::theLandLiesUnderTheHollowsOfOpenGround);
        Checks.run("waterLiesUnderTheBuiltWorldsWater", FarLandTests::waterLiesUnderTheBuiltWorldsWater);
        Checks.run("aBuildingIsABlockOfItsFloors", FarLandTests::aBuildingIsABlockOfItsFloors);
        Checks.run("aFloorSmallerThanTheOneUnderItLeavesARoofBeside", FarLandTests::aFloorSmallerThanTheOneUnderItLeavesARoofBeside);
        Checks.run("aCellarAloneIsNoBuilding", FarLandTests::aCellarAloneIsNoBuilding);
        Checks.run("roomsOverNothingStandOnTheGround", FarLandTests::roomsOverNothingStandOnTheGround);
        Checks.run("aCellOfNothingIsToldFromOneWithSomething", FarLandTests::aCellOfNothingIsToldFromOneWithSomething);
        Checks.run("anIndexIsReadNoFurtherThanAnIndexRuns", FarLandTests::anIndexIsReadNoFurtherThanAnIndexRuns);
        Checks.run("aTileLargerThanACellIsRefusedBeforeItsDots", FarLandTests::aTileLargerThanACellIsRefusedBeforeItsDots);
        Checks.run("aCellsBoxHoldsEveryPointOfIt", FarLandTests::aCellsBoxHoldsEveryPointOfIt);
        Checks.run("theGroundIsItsFloorWithWhatIsLaidOverIt", FarLandTests::theGroundIsItsFloorWithWhatIsLaidOverIt);
        Checks.run("theLotsGroundTakesTheMapsPlace", FarLandTests::theLotsGroundTakesTheMapsPlace);
        Checks.run("aRoofLiesAsItsTilesSlope", FarLandTests::aRoofLiesAsItsTilesSlope);
        Checks.run("rectanglesCoverARegionAndNothingElse", FarLandTests::rectanglesCoverARegionAndNothingElse);
        Checks.run("aBuildingWearsWhatItsLotLays", FarLandTests::aBuildingWearsWhatItsLotLays);
        Checks.run("plainBlocksHaveWindowsInBays", FarLandTests::plainBlocksHaveWindowsInBays);
        Checks.run("aWallAllWindowsIsStillAWall", FarLandTests::aWallAllWindowsIsStillAWall);
        Checks.run("aChunkThatCannotBeReadIsLeftEmpty", FarLandTests::aChunkThatCannotBeReadIsLeftEmpty);
        Checks.run("aTileWaitsForItsSheet", FarLandTests::aTileWaitsForItsSheet);
        Checks.run("aLongHipIsAHipAndALongGableAGable", FarLandTests::aLongHipIsAHipAndALongGableAGable);
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
        check(to.getFloat(8) == Terrain.HILLS.farUnder(49 * 256f, 5 * 256f, Terrain.VALLEY), "at the height the land has there");
        float atOne = to.getFloat(8);
        try {
            Terrain.HILLS.heighten(1.5f);
            ByteBuffer higher = buffer(FarCells.LAND_VERTICES * FarCells.VERTEX_BYTES);
            FarCells.land(DRY, 49, 5, higher);
            check(higher.getFloat(8) == Terrain.HILLS.farUnder(49 * 256f, 5 * 256f, Terrain.VALLEY) && Math.abs(higher.getFloat(8) - 1.5f * atOne) < 1e-5f,
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

    private static void theLandLiesUnderTheHollowsOfOpenGround() {
        try {
            Terrain.HILLS.heighten(1.5f);
            ByteBuffer to = buffer(FarCells.LAND_VERTICES * FarCells.VERTEX_BYTES);
            FarCells.land(DRY, 42, 38, to);
            boolean found = false;
            for (int at = 0; at < to.capacity(); at += FarCells.VERTEX_BYTES) {
                float x = to.getFloat(at), y = to.getFloat(at + 4), up = to.getFloat(at + 8);
                check(up <= Terrain.HILLS.far(x, y, Terrain.VALLEY), "the far land lies no higher than the country at " + x + ", " + y);
                if (x == 10808f && y == 9736f) {
                    found = true;
                    // The built land there is a hollow of open ground 2.8 tiles under the country (knolls -2.48, roll -0.34).
                    check(up < Terrain.HILLS.far(x, y, Terrain.VALLEY) - 2.8f, "and in Muldraugh's gardens north east of 10766, 9752 lies down in the hollow the built land has: " + up);
                }
            }
            check(found, "the cell has the corner at 10808, 9736");
        } finally {
            Terrain.HILLS.heighten(1f);
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
                check(up == Terrain.HILLS.farUnder(x, y, FarCells.toWater(river, x - 49 * 256f, y - 5 * 256f)), "and the land well off it is the country at " + x + ", " + y);
            }
        }
        check(corners > 0, "the river has corners");
    }

    /** The area of the triangles that face up, and the highest any vertex is. */
    private static float[] roofs(ByteBuffer blocks) {
        float area = 0f, top = -Float.MAX_VALUE;
        for (int at = 0; at < blocks.limit(); at += 3 * FarCells.BLOCK_BYTES) {
            float[] x = new float[3], y = new float[3];
            for (int corner = 0; corner < 3; corner++) {
                int vertex = at + corner * FarCells.BLOCK_BYTES;
                x[corner] = blocks.getFloat(vertex);
                y[corner] = blocks.getFloat(vertex + 4);
                top = Math.max(top, blocks.getFloat(vertex + 8));
                check((blocks.get(vertex + 19) & 0xff) >= FarCells.BUILT, "a building's vertex is marked as built");
            }
            if (blocks.get(at + 14) > 63) {
                area += Math.abs((x[1] - x[0]) * (y[2] - y[0]) - (x[2] - x[0]) * (y[1] - y[0])) / 2f;
            }
        }
        return new float[] {area, top};
    }

    private static void aBuildingIsABlockOfItsFloors() {
        int[] buildings = {7, 1, 12600, 1300, 4, 3, 0};
        ByteBuffer blocks = FarCells.blocks(buildings, DRY, 49, 5);
        check(blocks.limit() == 5 * 6 * FarCells.BLOCK_BYTES, "one room on one floor is four walls and a roof, and was " + blocks.limit() / FarCells.BLOCK_BYTES + " vertices");
        float[] roofs = roofs(blocks);
        float pad = Terrain.HILLS.farUnder(12602f, 1301.5f, Terrain.VALLEY);
        check(roofs[0] == 12f, "the roof covers the room's 12 squares, and covered " + roofs[0]);
        check(Math.abs(roofs[1] - pad - ViewCamera.LEVEL_HEIGHT) < 1e-4f, "one floor above the land in the middle of it");
    }

    private static void aFloorSmallerThanTheOneUnderItLeavesARoofBeside() {
        // Two rooms side by side downstairs, one of them with a room over it.
        int[] buildings = {9, 3, 12600, 1300, 2, 4, 0, 12602, 1300, 2, 4, 0, 12600, 1300, 2, 4, 1};
        ByteBuffer blocks = FarCells.blocks(buildings, DRY, 49, 5);
        check(blocks.limit() == 10 * 6 * FarCells.BLOCK_BYTES, "two floors of four walls each and a roof each, with no wall between two rooms of a floor, and were "
                + blocks.limit() / (6 * FarCells.BLOCK_BYTES) + " faces");
        check(roofs(blocks)[0] == 16f, "the roofs together cover the building's 16 squares once");
    }

    private static void roomsOverNothingStandOnTheGround() {
        int[] buildings = {5, 1, 12600, 1300, 4, 3, 1};
        ByteBuffer blocks = FarCells.blocks(buildings, DRY, 49, 5);
        check(blocks.limit() == 5 * 6 * FarCells.BLOCK_BYTES, "a room with none under it is four walls and a roof");
        float pad = Terrain.HILLS.farUnder(12602f, 1301.5f, Terrain.VALLEY), lowest = Float.MAX_VALUE;
        for (int at = 0; at < blocks.limit(); at += FarCells.BLOCK_BYTES) {
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

    private static final int COLOURED_FLOOR = FarTiles.GROUND | FarTiles.FLOOR | FarTiles.COLOURED;

    private static void theGroundIsItsFloorWithWhatIsLaidOverIt() {
        int[] traits = {COLOURED_FLOOR, FarTiles.GROUND | FarTiles.COLOURED, COLOURED_FLOOR | FarTiles.WATER, COLOURED_FLOOR | FarTiles.DIAMOND,
                COLOURED_FLOOR | FarTiles.DIAMOND, FarTiles.GROUND | FarTiles.FLOOR, FarTiles.WALL_N | FarTiles.PLAIN_WALL | FarTiles.COLOURED};
        int[] colours = {0xff404020, 0x80c0c0c0, 0xff6c7f83, 0xff101010, 0xffa0a0a0, 0, 0xc08040};
        check(FarLot.ground(new int[] {0}, 1, traits, colours) == (FarLot.LAID | 0x404020), "a floor alone is its own colour");
        int blended = FarLot.ground(new int[] {0, 1}, 2, traits, colours);
        check(blended == (FarLot.LAID | 0x808070), "a blend over half the square is mixed in by half: " + Integer.toHexString(blended));
        check(FarLot.ground(new int[] {1}, 1, traits, colours) == 0, "a blend with no floor under it is no ground");
        check(FarLot.ground(new int[] {0, 6}, 2, traits, colours) == (FarLot.LAID | 0x404020), "a wall stood on the square leaves its ground as it was");
        check(FarLot.ground(new int[] {2}, 1, traits, colours) == (FarLot.LAID | FarLot.WET | 0x6c7f83), "a floor of water is wet");
        check(FarLot.ground(new int[] {3, 1, 4}, 3, traits, colours) == (FarLot.LAID | 0xa0a0a0), "a diamond floor laid after one takes its place and what was laid over it");
        check(FarLot.ground(new int[] {5, 1}, 2, traits, colours) == 0, "a floor whose colour is not read is no ground");
    }

    private static void theLotsGroundTakesTheMapsPlace() {
        int[] pixels = new int[FarCells.CELL * FarCells.CELL];
        Arrays.fill(pixels, FarCells.GRASS);
        pixels[1] = FarCells.WATER;
        int[] ground = new int[FarCells.CELL * FarCells.CELL];
        ground[0] = FarLot.LAID | 0x203040;
        ground[1] = FarLot.LAID | 0x203040;
        ground[2] = FarLot.LAID | FarLot.WET | 0x203040;
        ByteBuffer to = buffer(FarCells.PICTURE_BYTES);
        FarCells.picture(pixels, ground, to);
        check((to.get(0) & 0xff) == 0x20 && (to.get(1) & 0xff) == 0x30 && (to.get(2) & 0xff) == 0x40 && to.get(3) == 0, "a square of the lot is its ground's colour");
        check((to.get(4) & 0xff) == 0x20 && to.get(7) == 0, "and is not water where the lot lays a dry floor, whatever the map paints");
        check((to.get(8) & 0xff) == 0x6c && (to.get(11) & 0xff) == 255, "a wet floor is the map's water");
        check((to.get(12) & 0xff) == 0x4b && to.get(15) == 0, "and a square the lot lays no floor on is the map's picture");
    }

    private static void aRoofLiesAsItsTilesSlope() {
        check(FarCells.shape(0, 0, 12, 0, 0, 0) == FarLot.ALONG_Y, "a roof of tiles that all rise along y is a ridge along x");
        check(FarCells.shape(0, 9, 1, 0, 0, 0) == FarLot.ALONG_X, "and one of tiles nearly all rising along x is a ridge along y");
        check(FarCells.shape(0, 5, 7, 2, 2, 2) == FarLot.MEET, "a roof with a hip's corners at its ridge's ends is a hip");
        check(FarCells.shape(0, 2, 40, 4, 4, 0) == FarLot.MEET, "however long it is");
        check(FarCells.shape(0, 2, 40, 2, 0, 2) == FarLot.ALONG_Y, "and corner tiles away from the ends of its ridge, where a wing joins, make no hip");
        check(FarCells.shape(10, 1, 1, 0, 0, 0) == FarLot.FLAT, "a roof of mostly flat tiles is flat");
        check(FarCells.shape(0, 1, 0, 0, 0, 0) == FarLot.FLAT, "and one slope tile alone makes no pitched roof");
        check(FarLot.liesOf(RoofFit.ALONG_X, true, true) == FarLot.NONE, "a trim on a roof is no tile of it");
        check(FarLot.liesOf(RoofFit.LEVEL, false, false) == FarLot.NONE && FarLot.liesOf(RoofFit.LEVEL, true, false) == FarLot.FLAT,
                "a level tile is flat only where its sprite says so plainly");
        check(FarLot.liesOf(RoofFit.SOUTH_EAST_ODD, false, false) == FarLot.MEET, "a tile where two slopes meet is a hip's");
    }

    private static void rectanglesCoverARegionAndNothingElse() {
        // An L: three squares across one row and one more under its first.
        int wide = 5, deep = 4;
        boolean[] region = new boolean[wide * deep];
        for (int i : new int[] {6, 7, 8, 11}) {
            region[i] = true;
        }
        int[] found = FarCells.rectangles(region, wide, deep);
        boolean[] covered = new boolean[region.length];
        for (int r = 0; r < found.length; r += 4) {
            for (int y = found[r + 1]; y < found[r + 1] + found[r + 3]; y++) {
                for (int x = found[r]; x < found[r] + found[r + 2]; x++) {
                    check(region[y * wide + x], "a rectangle lies in the region, and " + x + ", " + y + " does not");
                    covered[y * wide + x] = true;
                }
            }
        }
        check(Arrays.equals(covered, region), "and between them they cover every square of it");
        check(found.length == 8, "an L is two rectangles, and was " + found.length / 4);
    }

    /** What the lot lays of a house of 4 by 3 squares at 12600, 1300, on cell 49, 5: a floor, walls of one colour outside with a window in the south one, and a roof sloping along y. */
    private static FarLot house() {
        return house(false);
    }

    private static FarLot house(boolean glazed) {
        int[] traits = {COLOURED_FLOOR, FarTiles.WALL_N | FarTiles.PLAIN_WALL | FarTiles.COLOURED, FarTiles.WALL_W | FarTiles.PLAIN_WALL | FarTiles.COLOURED,
                FarTiles.WALL_N | FarTiles.WINDOW_N, FarTiles.ROOF | FarTiles.COLOURED | FarLot.ALONG_Y << FarTiles.LIES_SHIFT | 15 << FarTiles.TOP_SHIFT};
        int[] colours = {0xff505050, 0xc08040, 0xc08040, 0, 0x603020};
        ArrayList<FarTreeTests.Square> squares = new ArrayList<>();
        for (int x = 56; x < 60; x++) {
            for (int y = 20; y < 23; y++) {
                squares.add(new FarTreeTests.Square(x / 8, y / 8, 0, x % 8, y % 8, 0));
                squares.add(new FarTreeTests.Square(x / 8, y / 8, 1, x % 8, y % 8, 4));
            }
            // The south wall stands on the north edge of the squares past it, with a window at 57.
            squares.add(new FarTreeTests.Square(x / 8, 23 / 8, 0, x % 8, 23 % 8, glazed || x == 57 ? 3 : 1));
        }
        for (int y = 20; y < 23; y++) {
            squares.add(new FarTreeTests.Square(60 / 8, y / 8, 0, 60 % 8, y % 8, 2));
        }
        ByteBuffer pack = FarTreeTests.pack(true, 0, 1, squares.toArray(new FarTreeTests.Square[0]));
        ArrayList<Lotpack.Malformed> skipped = new ArrayList<>();
        FarLot lot = FarLot.read(pack, traits, colours, 0, 1, skipped);
        check(skipped.isEmpty(), "the lotpack reads whole");
        return lot;
    }

    private static void aBuildingWearsWhatItsLotLays() {
        FarLot lot = house();
        check(lot.ground[20 * FarCells.CELL + 56] == (FarLot.LAID | 0x505050), "the house's floor is its ground");
        check((lot.north(57, 23, 0) & FarLot.GLAZED) != 0 && (lot.north(56, 23, 0) & FarLot.GLAZED) == 0, "the window is where the lot lays it");
        int[] buildings = {7, 1, 12600, 1300, 4, 3, 0};
        ByteBuffer blocks = FarCells.blocks(buildings, DRY, 49, 5, lot);
        float pad = Terrain.HILLS.farUnder(12602f, 1301.5f, Terrain.VALLEY);
        float eaves = pad + ViewCamera.LEVEL_HEIGHT, ridge = eaves + RoofFit.PITCH * ViewCamera.LEVEL_HEIGHT * 1.5f;
        boolean southWall = false, gable = false;
        float top = -Float.MAX_VALUE;
        for (int at = 0; at < blocks.limit(); at += FarCells.BLOCK_BYTES) {
            int kind = blocks.get(at + 19) & 0xff;
            int colour = (blocks.get(at + 16) & 0xff) << 16 | (blocks.get(at + 17) & 0xff) << 8 | blocks.get(at + 18) & 0xff;
            float z = blocks.getFloat(at + 8);
            if (kind == FarCells.ROOFED) {
                check(colour == 0x603020, "the roof is its tiles' colour, and was " + Integer.toHexString(colour));
                top = Math.max(top, z);
                continue;
            }
            check(colour == 0xc08040, "every wall is the colour of the walls the lot lays outside, and one was " + Integer.toHexString(colour));
            if (blocks.get(at + 13) == 127) {
                southWall = true;
                check(blocks.getFloat(at + 28) == 2f, "the south wall has its window in its second tile, and had " + blocks.getFloat(at + 28));
                check(kind == FarCells.BUILT + Math.round(126 / 4f), "and a quarter of it is windows, and was " + kind);
            }
            gable |= blocks.get(at + 12) != 0 && z > eaves + 0.5f;
        }
        check(southWall, "the house has a south wall");
        check(Math.abs(top - ridge) < 1e-4f, "the roof rises at the game's pitch from the long sides to a ridge over the middle: " + top + " for " + ridge);
        check(gable, "and its ends are gables of wall");
    }

    private static void plainBlocksHaveWindowsInBays() {
        int[] buildings = {7, 1, 12600, 1300, 4, 3, 0};
        ByteBuffer blocks = FarCells.blocks(buildings, DRY, 49, 5);
        boolean found = false;
        for (int at = 0; at < blocks.limit(); at += FarCells.BLOCK_BYTES) {
            if (blocks.get(at + 19) != (byte) FarCells.ROOFED) {
                found = true;
                check(blocks.getFloat(at + 28) == FarCells.BAYS && (blocks.get(at + 19) & 0xff) == FarCells.BUILT, "a wall with no lot read has its windows in bays, as the far land had before it read lots");
            }
        }
        check(found, "the block has walls");
    }

    private static void aWallAllWindowsIsStillAWall() {
        ByteBuffer blocks = FarCells.blocks(new int[] {7, 1, 12600, 1300, 4, 3, 0}, DRY, 49, 5, house(true));
        boolean found = false;
        for (int at = 0; at < blocks.limit(); at += FarCells.BLOCK_BYTES) {
            if (blocks.get(at + 13) == 127) {
                found = true;
                check((blocks.get(at + 19) & 0xff) == FarCells.ROOFED - 1, "a wall that is all windows is marked as the most a wall can be, and not as a roof: " + (blocks.get(at + 19) & 0xff));
            }
        }
        check(found, "the house has a south wall");
    }

    private static void aChunkThatCannotBeReadIsLeftEmpty() {
        int[] traits = {COLOURED_FLOOR, FarTiles.WALL_N | FarTiles.PLAIN_WALL | FarTiles.COLOURED, FarTiles.WALL_W | FarTiles.PLAIN_WALL | FarTiles.COLOURED,
                FarTiles.WALL_N | FarTiles.WINDOW_N, FarTiles.ROOF | FarTiles.COLOURED | FarLot.ALONG_Y << FarTiles.LIES_SHIFT | 15 << FarTiles.TOP_SHIFT};
        int[] colours = {0xff505050, 0xc08040, 0xc08040, 0, 0x603020};
        ArrayList<FarTreeTests.Square> squares = new ArrayList<>();
        squares.add(new FarTreeTests.Square(7, 2, 0, 0, 4, 0));
        squares.add(new FarTreeTests.Square(7, 2, 0, 0, 5, 1));
        squares.add(new FarTreeTests.Square(7, 2, 1, 0, 4, 4));
        // The last square of chunk 7, 2 in the order it is read: its count is made one no file could hold.
        squares.add(new FarTreeTests.Square(7, 2, 1, 7, 7, 0));
        squares.add(new FarTreeTests.Square(7, 3, 0, 0, 0, 0));
        ByteBuffer pack = FarTreeTests.pack(true, 0, 1, squares.toArray(new FarTreeTests.Square[0]));
        int next = pack.getInt(12 + (7 * 32 + 3) * 8);
        check(pack.getInt(next - 12) == 2, "the last square of the chunk is where it is looked for");
        pack.putInt(next - 12, 0x40000000);
        ArrayList<Lotpack.Malformed> skipped = new ArrayList<>();
        FarLot lot = FarLot.read(pack, traits, colours, 0, 1, skipped);
        check(skipped.size() == 1 && skipped.get(0).getMessage().contains("chunk 7, 2"), "a square that lays more tiles than the file holds is the chunk's to skip, and said: " + skipped);
        check(lot.ground[20 * FarCells.CELL + 56] == 0 && lot.north(56, 21, 0) == 0 && lot.roof(56, 20, 1) == 0,
                "what was read of the chunk before it is left out, as the game loads the chunk empty");
        check(lot.ground[24 * FarCells.CELL + 56] == (FarLot.LAID | 0x505050), "and the chunk after it reads as it lies");
    }

    private static void aTileWaitsForItsSheet() {
        java.util.Map<String, String> flags = new java.util.HashMap<>();
        flags.put("WallN", "");
        IsoSprite unsized = new IsoSprite("walls_exterior_house_01_0", new PropertyContainer(flags), IsoObjectType.wall, new Texture(9101, 0, 0));
        IsoSprite bare = new IsoSprite("walls_exterior_house_01_1", new PropertyContainer(flags), IsoObjectType.wall, null);
        IsoSprite roof = new IsoSprite("roofs_01_0", new PropertyContainer(new java.util.HashMap<>()), IsoObjectType.WestRoofB, new Texture(9102, 0, 0));
        IsoSpriteManager.instance.namedMap.put(unsized.getName(), unsized);
        IsoSpriteManager.instance.namedMap.put(bare.getName(), bare);
        IsoSpriteManager.instance.namedMap.put(roof.getName(), roof);
        try {
            check(FarTiles.of(unsized.getName(), null) == null, "a wall whose sheet is not yet on the graphics card is waited for, not taken as having no colour");
            check(FarTiles.of(roof.getName(), null) == null, "and so is a roof tile, whose slope is read from the sheet as well");
            int[] none = FarTiles.of(bare.getName(), null);
            check(none != null && none[1] == 0 && (none[0] & FarTiles.COLOURED) == 0, "a sprite with no sheet at all is known at once, uncoloured");
        } finally {
            IsoSpriteManager.instance.namedMap.remove(unsized.getName());
            IsoSpriteManager.instance.namedMap.remove(bare.getName());
            IsoSpriteManager.instance.namedMap.remove(roof.getName());
        }
    }

    /** A house of 20 by 6 squares at 12584, 1300 on cell 49, 5, under a roof of tiles rising along y, with a hip's corner tiles at both ends of its ridge where hip says so. */
    private static ByteBuffer longHouse(boolean hip) {
        int[] traits = {COLOURED_FLOOR, FarTiles.ROOF | FarTiles.COLOURED | FarLot.ALONG_Y << FarTiles.LIES_SHIFT | 15 << FarTiles.TOP_SHIFT,
                FarTiles.ROOF | FarTiles.COLOURED | FarLot.MEET << FarTiles.LIES_SHIFT | 15 << FarTiles.TOP_SHIFT};
        int[] colours = {0xff505050, 0x603020, 0x603020};
        ArrayList<FarTreeTests.Square> squares = new ArrayList<>();
        for (int x = 40; x < 60; x++) {
            for (int y = 20; y < 26; y++) {
                squares.add(new FarTreeTests.Square(x / 8, y / 8, 0, x % 8, y % 8, 0));
                boolean end = x - 40 < 3 || 59 - x < 3;
                squares.add(new FarTreeTests.Square(x / 8, y / 8, 1, x % 8, y % 8, hip && end ? 2 : 1));
            }
        }
        ArrayList<Lotpack.Malformed> skipped = new ArrayList<>();
        FarLot lot = FarLot.read(FarTreeTests.pack(true, 0, 1, squares.toArray(new FarTreeTests.Square[0])), traits, colours, 0, 1, skipped);
        check(skipped.isEmpty(), "the long house's lotpack reads whole");
        return FarCells.blocks(new int[] {7, 1, 12584, 1300, 20, 6, 0}, DRY, 49, 5, lot);
    }

    private static void aLongHipIsAHipAndALongGableAGable() {
        for (boolean hip : new boolean[] {true, false}) {
            ByteBuffer blocks = longHouse(hip);
            boolean endSlopes = false;
            for (int at = 0; at < blocks.limit(); at += FarCells.BLOCK_BYTES) {
                endSlopes |= (blocks.get(at + 19) & 0xff) == FarCells.ROOFED && Math.abs(blocks.get(at + 12)) > 10;
            }
            check(endSlopes == hip, hip ? "a hip 20 squares long and 6 wide slopes down at its ends" : "a gable as long has no slopes at its ends");
        }
    }
}
