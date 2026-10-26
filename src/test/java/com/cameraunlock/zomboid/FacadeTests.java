package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Tests of what a wall's sprite is read as built of, of the wires a sprite shows, and of a ridge along the middle of a square. */
final class FacadeTests {
    private static final int WIDE = 64, HIGH = 200;
    private static final float THIRD = RoofFit.PITCH;

    private FacadeTests() {}

    static void run() {
        Checks.run("brickIsToldByItsUprightJoints", FacadeTests::brickIsToldByItsUprightJoints);
        Checks.run("lappedBoardsHaveShadowsAndNoUprightJoints", FacadeTests::lappedBoardsHaveShadowsAndNoUprightJoints);
        Checks.run("aWallTheGameCallsMasonryIsLaidAsIt", FacadeTests::aWallTheGameCallsMasonryIsLaidAsIt);
        Checks.run("lappedBoardsUnderAnOutlineAreLapped", FacadeTests::lappedBoardsUnderAnOutlineAreLapped);
        Checks.run("lowWallOfBrickIsBrick", FacadeTests::lowWallOfBrickIsBrick);
        Checks.run("gardenWallOfBrickIsBrick", FacadeTests::gardenWallOfBrickIsBrick);
        Checks.run("blocksAreDeepCoursesOfOneGrey", FacadeTests::blocksAreDeepCoursesOfOneGrey);
        Checks.run("stoneHasDeepCoursesAndNoTwoStonesAlike", FacadeTests::stoneHasDeepCoursesAndNoTwoStonesAlike);
        Checks.run("boardedWallCarriesItsColour", FacadeTests::boardedWallCarriesItsColour);
        Checks.run("wallInAnotherLightIsTheSamePaint", FacadeTests::wallInAnotherLightIsTheSamePaint);
        Checks.run("paintedWallIsLeftAsPainted", FacadeTests::paintedWallIsLeftAsPainted);
        Checks.run("thinPaintOverAWallIsAStain", FacadeTests::thinPaintOverAWallIsAStain);
        Checks.run("onlyOutsideWallsAreLaid", FacadeTests::onlyOutsideWallsAreLaid);
        Checks.run("wireIsReadAsItsTwoEndsOverTheSquare", FacadeTests::wireIsReadAsItsTwoEndsOverTheSquare);
        Checks.run("brokenLineIsOneWire", FacadeTests::brokenLineIsOneWire);
        Checks.run("wireLevelAcrossTheViewFillsItsSpriteAndIsStillAWire", FacadeTests::wireLevelAcrossTheViewFillsItsSpriteAndIsStillAWire);
        Checks.run("onlyTheSheetsWiresAreWires", FacadeTests::onlyTheSheetsWiresAreWires);
        Checks.run("wireRunsTheWayTheMapsWiresRun", FacadeTests::wireRunsTheWayTheMapsWiresRun);
        Checks.run("poleIsItsLengthsAndItsArm", FacadeTests::poleIsItsLengthsAndItsArm);
        Checks.run("halfTilesHeldALevelUpPeakTheRidgeTheyBelongTo", FacadeTests::halfTilesHeldALevelUpPeakTheRidgeTheyBelongTo);
        Checks.run("halfTileOnItsOwnSquareMakesItsRidge", FacadeTests::halfTileOnItsOwnSquareMakesItsRidge);
        Checks.run("halfTileThatJoinsNoSlopeMakesNothing", FacadeTests::halfTileThatJoinsNoSlopeMakesNothing);
    }

    /** What is painted at a place on a wall set straight: how far along it in dots, and how many rows down. */
    private interface Wall {
        int at(int along, int row);
    }

    /** A wall's sprite: the wall as the game's view shows it from the side, a dot down for every two along. */
    private static ByteBuffer sprite(Wall wall, int high) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(WIDE * high * 4);
        for (int y = 0; y < high; y++) {
            for (int x = 0; x < WIDE; x++) {
                int row = y - (x + 1) / 2;
                int colour = row < 0 || row >= high - WIDE / 2 ? 0 : wall.at(x, row);
                pixels.put((byte) (colour >> 16)).put((byte) (colour >> 8)).put((byte) colour).put((byte) (colour == 0 ? 0 : 255));
            }
        }
        pixels.flip();
        return pixels;
    }

    /** Grime is mostly dots a half covered, a poster's paint is whole, and a wall's sprite is whole with a soft rim. */
    private static void thinPaintOverAWallIsAStain() {
        check(Stains.measure(covered(new int[] {120, 90, 150, 0}), 2, 0, 0, 2, 2), "grime in thin dots is a stain");
        check(!Stains.measure(covered(new int[] {255, 255, 255, 0}), 2, 0, 0, 2, 2), "solid paint is no stain");
        check(!Stains.measure(covered(new int[] {255, 255, 255, 100}), 2, 0, 0, 2, 2), "a solid sprite with a soft edge is no stain");
        check(!Stains.measure(covered(new int[] {0, 0, 0, 0}), 2, 0, 0, 2, 2), "an empty sprite is no stain");
    }

    /** Dots of grey, each covered as given. */
    private static ByteBuffer covered(int[] cover) {
        ByteBuffer pixels = ByteBuffer.allocateDirect(cover.length * 4);
        for (int c : cover) {
            pixels.put((byte) 0x80).put((byte) 0x80).put((byte) 0x80).put((byte) c);
        }
        pixels.flip();
        return pixels;
    }

    private static int laid(Wall wall) {
        return laid(wall, HIGH);
    }

    private static int laid(Wall wall, int high) {
        return Masonry.measure(sprite(wall, high), WIDE, 0, 0, WIDE, high);
    }

    /** A colour a little lighter or darker than the one given, by a number from 0 to 1. */
    private static int shade(int colour, float by) {
        float much = 0.8f + 0.4f * by;
        return Math.min(255, (int) ((colour >> 16 & 255) * much)) << 16 | Math.min(255, (int) ((colour >> 8 & 255) * much)) << 8 | Math.min(255, (int) ((colour & 255) * much));
    }

    /** A number from 0 to 1 for each pair of whole numbers, with no pattern from one pair to the next. */
    private static float chance(int a, int b) {
        int mixed = (a * 73856093 ^ b * 19349663) * 0x9E3779B1;
        return (mixed >>> 8) / (float) (1 << 24);
    }

    private static final int BRICK = 0xa04030, MORTAR = 0xd8c8b8, BOARD = 0xe0dcc8, SHADOW = 0x6a6658, STONE = 0xc0a870, PAINT = 0x8090a0;
    private static final int BLOCK = 0xd0d0c4, BLOCK_JOINT = 0xb4b4a8;

    private static void aWallTheGameCallsMasonryIsLaidAsIt() {
        Checks.check(WallSides.madeOf("Cinderblock") == WallSides.BLOCK && WallSides.madeOf("Brick") == WallSides.BRICK && WallSides.madeOf("Stone") == WallSides.STONE,
                "cinder block, brick and stone walls are laid as blocks, bricks and stones");
        Checks.check(WallSides.madeOf("Wood") == 0 && WallSides.madeOf("Plaster") == 0 && WallSides.madeOf((String) null) == 0, "and any other wall as its sprite reads");
        int lapped = WallSides.laid(0.6f, 0.6f, 0.58f, 0.3f, 0.3f, 0.3f, WallSides.LAPPED);
        int block = WallSides.asMade(lapped, WallSides.BLOCK);
        Checks.check((block & 15) == WallSides.BLOCK && (block & ~15) == (lapped & ~15), "a block wall read as lapped boards is laid as blocks in the colours read: " + Integer.toHexString(block));
        int brick = WallSides.laid(0.6f, 0.3f, 0.2f, 0.8f, 0.8f, 0.8f, WallSides.BRICK);
        Checks.check(WallSides.asMade(brick, WallSides.BLOCK) == brick && WallSides.asMade(lapped, 0) == lapped, "a wall read as anything else, or of no masonry, is laid as read");
    }

    private static void brickIsToldByItsUprightJoints() {
        // Courses eight rows deep, bricks twenty dots long, each course half a brick along from the last.
        int how = laid((along, row) -> row % 8 == 0 || (along + row / 8 % 2 * 10) % 20 == 0 ? MORTAR : BRICK);
        float[] tint = new float[4];
        WallSides.tint(how, tint);
        check(tint[3] == WallSides.BRICK, "a wall in even courses with upright joints is brick: " + Integer.toHexString(how));
        check(Math.abs(tint[0] - 0xa0 / 255f) < 0.02f && Math.abs(tint[2] - 0x30 / 255f) < 0.02f, "its colour is the bricks': " + tint[0] + ", " + tint[2]);
        float[] joint = new float[3];
        WallSides.joint(how, joint);
        check(Math.abs(joint[0] - 0xd8 / 255f) < 0.08f && Math.abs(joint[2] - 0xb8 / 255f) < 0.08f, "and its joints are the mortar's: " + joint[0] + ", " + joint[2]);
        float[] over = new float[4];
        WallSides.tint(WallSides.over(how), over);
        check(over[3] == WallSides.BRICK + 8 && over[0] == tint[0] && over[2] == tint[2], "laid over another wall's sprite it is the same brick: " + over[3]);
        WallSides.joint(WallSides.key(0.5f, 0.5f, 0.5f, WallSides.SIDING), joint);
        check(joint[0] == 0f && joint[1] == 0f && joint[2] == 0f, "a painted skin has no joints");
    }

    private static void lappedBoardsHaveShadowsAndNoUprightJoints() {
        int how = laid((along, row) -> row % 10 == 0 ? SHADOW : BOARD);
        check((how & 15) == WallSides.LAPPED, "a wall with a dark line every board and no upright joints is lapped boards: " + Integer.toHexString(how));
    }

    private static void lappedBoardsUnderAnOutlineAreLapped() {
        // A trailer's siding: boards eighteen rows deep with a faint shadow two rows deep at each one's foot, under three black rows along the top.
        int how = laid((along, row) -> row < 3 ? 0x010101 : (row - 3) % 18 < 2 ? 0xa8a498 : 0xc4c0b0);
        check((how & 15) == WallSides.LAPPED, "lapped boards with a dark outline along their top are lapped boards: " + Integer.toHexString(how));
    }

    private static void lowWallOfBrickIsBrick() {
        // A parapet: the same brick in a sprite half as high.
        int how = laid((along, row) -> row % 8 == 0 || (along + row / 8 % 2 * 10) % 20 == 0 ? MORTAR : BRICK, 106);
        check((how & 15) == WallSides.BRICK, "a low wall's bricks are the size a whole wall's are: " + Integer.toHexString(how));
    }

    private static void gardenWallOfBrickIsBrick() {
        // A garden wall: the sprite paints 37 rows of wall, four and a half courses.
        int how = laid((along, row) -> row % 8 == 0 || (along + row / 8 % 2 * 10) % 20 == 0 ? MORTAR : BRICK, 37 + WIDE / 2);
        check((how & 15) == WallSides.BRICK, "a garden wall's few courses are brick: " + Integer.toHexString(how));
    }

    private static void blocksAreDeepCoursesOfOneGrey() {
        // Courses twenty-eight rows deep, blocks thirty dots long, every block the same grey with a darker joint two dots wide.
        int how = laid((along, row) -> row % 28 < 2 || (along + row / 28 % 2 * 15) % 30 < 2 ? BLOCK_JOINT : BLOCK);
        check((how & 15) == WallSides.BLOCK, "a wall in deep even courses of one grey is cast blocks: " + Integer.toHexString(how));
    }

    private static void stoneHasDeepCoursesAndNoTwoStonesAlike() {
        // Courses twenty rows deep, the stones of each a different length and each its own shade, mottled.
        int how = laid((along, row) -> {
            int course = row / 20, stone = (along + course * 7) / (course % 2 == 0 ? 17 : 23);
            boolean joint = row % 20 == 0 || (along + course * 7) % (course % 2 == 0 ? 17 : 23) == 0;
            return joint ? SHADOW : shade(STONE, 0.6f * chance(stone, course) + 0.4f * chance(along, row));
        });
        check((how & 15) == WallSides.STONE, "a wall in deep courses of stones each its own shade is stone: " + Integer.toHexString(how));
    }

    private static void wallInAnotherLightIsTheSamePaint() {
        float[] west = {0.42f, 0.52f, 0.55f}, north = {0.57f, 0.63f, 0.66f};
        check(WallSides.oneColourInTwoLights(north, west) && WallSides.oneColourInTwoLights(west, north), "a blue wall and the same blue a third lighter are one paint");
        check(!WallSides.oneColourInTwoLights(new float[] {0.45f, 0.26f, 0.1f}, west), "brick red is not that blue in any light");
        check(!WallSides.oneColourInTwoLights(new float[] {0.9f, 0.9f, 0.9f}, new float[] {0.3f, 0.3f, 0.3f}), "nor is white a dark grey: no light makes a wall three times as bright");
        check(!WallSides.oneColourInTwoLights(west, new float[] {0f, 0f, 0f}), "and black is nothing made lighter");
    }

    private static void boardedWallCarriesItsColour() {
        int key = WallSides.laid(0.5f, 0.3f, 0.2f, 0f, 0f, 0f, WallSides.BOARDED);
        float[] tint = new float[4];
        WallSides.tint(key, tint);
        check(tint[3] == WallSides.BOARDED && Math.abs(tint[0] - 0.5f) < 0.02f && Math.abs(tint[2] - 0.2f) < 0.02f, "a boarded wall is told its sprite's colour: " + tint[0]);
        check(tint[3] < WallSides.over(WallSides.BRICK) && tint[3] > WallSides.BLOCK, "and is neither a laid wall nor one laid over another's sprite");
    }

    private static void paintedWallIsLeftAsPainted() {
        check(laid((along, row) -> PAINT) == 0, "a wall of one colour is shown as painted");
        // Wallpaper striped upright: lines that are no courses.
        check(laid((along, row) -> along % 6 == 0 ? SHADOW : BOARD) == 0, "and so is one with upright stripes");
    }

    private static void onlyOutsideWallsAreLaid() {
        check(Masonry.suits("walls_exterior_house_01_4") && Masonry.suits("walls_commercial_01_0"), "an outside wall is read");
        check(!Masonry.suits("walls_interior_bathroom_01_3") && !Masonry.suits(null), "a tiled bathroom is not");
    }

    private static SpriteGeometry.Frame frame(int left, int top, int width, int height) {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.left = left;
        frame.top = top;
        frame.width = width;
        frame.height = height;
        return frame;
    }

    private static void wireIsReadAsItsTwoEndsOverTheSquare() {
        SpriteGeometry.Frame frame = frame(0, 0, 128, 256);
        // A wire along y, a fifth of the way across its square, from one edge of it to the other.
        float u0 = SpriteGeometry.frameU(frame, 0.2f, 0f), v0 = SpriteGeometry.frameV(frame, 0.2f, 0f, Wires.UP);
        float u1 = SpriteGeometry.frameU(frame, 0.2f, 1f), v1 = SpriteGeometry.frameV(frame, 0.2f, 1f, Wires.UP);
        float[] lines = Wires.read((u, v) -> {
            float along = (u + 0.5f - u1) / (u0 - u1);
            return along >= 0f && along <= 1f && Math.abs(v + 0.5f - (v1 + along * (v0 - v1))) <= 0.75f;
        }, frame);
        check(lines.length == 4, "one line is one wire: " + lines.length / 4);
        boolean firstIsSouth = lines[1] > lines[3];
        float southA = lines[firstIsSouth ? 0 : 2], southB = lines[firstIsSouth ? 1 : 3], northA = lines[firstIsSouth ? 2 : 0], northB = lines[firstIsSouth ? 3 : 1];
        check(Math.abs(southA - 0.2f) < 0.03f && Math.abs(northA - 0.2f) < 0.03f, "it hangs where it is drawn across the square: " + southA + ", " + northA);
        check(Math.abs(southB - 1f) < 0.03f && Math.abs(northB) < 0.03f, "from one edge of the square to the other: " + northB + " to " + southB);
    }

    private static void brokenLineIsOneWire() {
        SpriteGeometry.Frame frame = frame(0, 0, 128, 256);
        // A line across the frame with every sixth dot of it missing, and a second line well below it, whole.
        float[] lines = Wires.read((u, v) -> u >= 10 && u < 110 && (v == 60 && u % 6 != 0 || v == 100), frame);
        check(lines.length == 8, "the pieces of a broken line are one wire, and the line below it another: " + lines.length / 4);
        float[] whole = Wires.read((u, v) -> u >= 10 && u < 110 && v == 60, frame);
        boolean firstIsBroken = Math.abs(lines[1] - whole[1]) < Math.abs(lines[5] - whole[1]);
        int at = firstIsBroken ? 0 : 4;
        float span = Math.abs(lines[at] - lines[at + 2]), wholeSpan = Math.abs(whole[0] - whole[2]);
        check(Math.abs(span - wholeSpan) < 0.05f, "as long as the line unbroken: " + span + " against " + wholeSpan);
    }

    private static void wireLevelAcrossTheViewFillsItsSpriteAndIsStillAWire() {
        // Two rows of dots and nothing else in the sprite's rectangle: a wire along the map's diagonal.
        SpriteGeometry.Frame frame = frame(20, 100, 76, 2);
        float[] lines = Wires.read((u, v) -> u >= 20 && u < 96 && v >= 100 && v < 102, frame);
        check(lines.length == 4, "a sprite that is all wire is one wire: " + lines.length / 4);
        check(Math.abs(Math.abs(lines[2] - lines[0]) - Math.abs(lines[3] - lines[1])) < 0.03f && Math.abs(lines[2] - lines[0]) > 0.5f,
                "running as far along x as along y: " + (lines[2] - lines[0]) + ", " + (lines[3] - lines[1]));
        check(Wires.read((u, v) -> false, frame).length == 0, "a sprite with nothing on it has none");
    }

    private static void wireRunsTheWayTheMapsWiresRun() {
        SpriteGeometry.Frame frame = frame(0, 0, 128, 256);
        // A wire along x drawn two dots down for every five across where it should be two for four: its ends are off its line.
        float[] lines = Wires.read((u, v) -> u >= 20 && u < 100 && v == 80 + Math.round((u - 20) * 0.45f), frame);
        check(lines.length == 4, "a line drawn a little off is one wire: " + lines.length / 4);
        check(Math.abs(lines[1] - lines[3]) < 1e-4f && Math.abs(lines[0] - lines[2]) > 1f, "and runs along x: " + lines[1] + " to " + lines[3]);
        // One that runs no way a wire does keeps the way its ends say.
        float[] odd = Wires.read((u, v) -> u >= 20 && u < 100 && v == 80 + Math.round((u - 20) * 0.25f), frame);
        check(odd.length == 4 && Math.abs(odd[1] - odd[3]) > 0.1f && Math.abs(odd[0] - odd[2]) > 0.1f, "a line between two ways is left as it is drawn");
    }

    private static void poleIsItsLengthsAndItsArm() {
        check(Poles.length("appliances_com_01_88") && Poles.length("appliances_com_01_95"), "a length of shaft is a length of a pole");
        check(!Poles.length("appliances_com_01_87") && !Poles.length("appliances_com_01_96") && !Poles.length("street_decoration_01_88") && !Poles.length(null),
                "a projector, a wire and a sprite of another sheet are not");
        check(Poles.arm("appliances_com_01_88") == null && Poles.arm("appliances_com_01_94") == null, "a bare length has no arm");
        for (int index = 90; index <= 93; index++) {
            Poles.Arm arm = Poles.arm("appliances_com_01_" + index);
            check(arm != null && Math.abs(arm.alongX() * arm.alongX() + arm.alongY() * arm.alongY() - 1f) < 1e-5f, "the top of a pole has an arm that lies one way: " + index);
            check(arm.outer() + Poles.INSULATOR < arm.half() && Math.abs(arm.middle()) < arm.outer(), "its wires cross it between its ends: " + index);
            // The game looks down the map's diagonal from where x and y are greatest: the arm is on that side of its shaft.
            check(-arm.alongY() + arm.alongX() > -1e-5f, "it is on the side of the shaft the game's view is on: " + index);
        }
        // The arm that lies along x, on a shaft 0.08 thick at its top in the middle of its square.
        Poles.Arm alongX = Poles.arm("appliances_com_01_93");
        float[] at = new float[2];
        check(!Poles.insulator(alongX, 1, 0.5f, 0.5f, 0.08f, at) && Math.abs(at[0] - 1.3f) < 1e-5f && Math.abs(at[1] - 0.635f) < 1e-5f,
                "an outer wire's insulator is on the arm, which is bolted to the near side of the shaft: " + at[0] + ", " + at[1]);
        check(!Poles.insulator(alongX, 0, 0.5f, 0.5f, 0.08f, at) && Math.abs(at[0] - 0.34f) < 1e-5f, "the third wire's is beside the shaft: " + at[0]);
        check(Poles.insulator(Poles.arm("appliances_com_01_90"), 0, 0.5f, 0.5f, 0.08f, at) && at[0] == 0.5f && at[1] == 0.5f,
                "and on the shaft's top where that wire passes over it");
    }

    private static void onlyTheSheetsWiresAreWires() {
        check(Wires.hangs("appliances_com_01_96") && Wires.hangs("appliances_com_01_121") && Wires.hangs("appliances_com_01_135"), "the sheet's wires are wires");
        check(!Wires.hangs("appliances_com_01_136") && !Wires.hangs("appliances_com_01_167"), "a street lamp's arm on a pole and a band round one are not");
        check(!Wires.hangs("appliances_com_01_80") && !Wires.hangs("appliances_com_01_93") && !Wires.hangs("appliances_com_01_on_3") && !Wires.hangs(null),
                "an aerial, a pole and a sprite of another sheet are not");
    }

    /** The map's half of a gable roof in rows 0 and 1: deep tiles falling east from a ridge at 2 along x. */
    private static List<RoofFit.Tile> eastSlope(int x, int deep) {
        List<RoofFit.Tile> roof = new ArrayList<>();
        for (int y = 0; y < 2; y++) {
            for (int i = 0; i < deep; i++) {
                float high = 2f - i * THIRD;
                roof.add(new RoofFit.Tile(x + i, y, 1, RoofFit.ALONG_X, true, high, high - THIRD));
            }
        }
        RoofFit.fit(roof);
        return roof;
    }

    private static RoofFit.Tile half(int x, int y, int z, int which, float edge) {
        RoofFit.Tile half = new RoofFit.Tile(x, y, z, RoofFit.LEVEL, true, edge, edge);
        half.fitting = true;
        half.half = which;
        half.halfEdge = edge;
        return half;
    }

    private static final RoofFit.Site SEVEN_WIDE = new RoofFit.Site() {
        @Override
        public boolean stands(int x, int y, int z) {
            return x >= 0 && x <= 6 && y >= 0 && y <= 1;
        }

        @Override
        public boolean floored(int x, int y, int z) {
            return false;
        }

        @Override
        public boolean taken(int x, int y, int z, float roof) {
            return false;
        }
    };

    private static RoofFit.Tile on(List<RoofFit.Tile> tiles, int x, int y) {
        for (RoofFit.Tile tile : tiles) {
            if (tile.x == x && tile.y == y && tile.half == 0) {
                return tile;
            }
        }
        return null;
    }

    private static void halfTilesHeldALevelUpPeakTheRidgeTheyBelongTo() {
        // Seven squares wide: three of slope either side and the ridge over the middle of square 3.
        // The map holds the east half of that square a level up and a square further off each way.
        List<RoofFit.Tile> tiles = eastSlope(4, 3);
        tiles.add(half(4, 1, 2, RoofFit.EAST_HALF, 2f + THIRD));
        tiles.add(half(4, 2, 2, RoofFit.EAST_HALF, 2f + THIRD));
        List<RoofFit.Tile> made = RoofFit.backs(tiles, SEVEN_WIDE);
        RoofFit.peaks(tiles, made);
        for (int y = 0; y < 2; y++) {
            RoofFit.Tile ridge = on(made, 3, y);
            check(ridge != null && ridge.fitAxis == RoofFit.LEVEL && near(ridge.fitLow, 2f), "the ridge's square is level at its edges, row " + y);
            check(near(ridge.peak, THIRD / 2f) && ridge.peakAxis == RoofFit.ALONG_X, "and peaks along its middle half a tile's climb higher: " + ridge.peak);
        }
        check(on(made, 2, 0).peak == 0f, "the slope beside it is a slope still");
        List<RoofFit.Cap> caps = RoofFit.caps(tiles, made);
        check(caps.size() == 2 && caps.get(0).x0 == 3.5f && near(caps.get(0).high0, 2f + THIRD / 2f) && near(caps.get(0).fall, THIRD),
                "the cap is on the peak and the roof falls away from it both sides: " + caps.size());
    }

    private static void halfTileOnItsOwnSquareMakesItsRidge() {
        // A slope two tiles deep falling south from 1 2/3, and the south half of the square above it on its own square.
        List<RoofFit.Tile> tiles = new ArrayList<>();
        tiles.add(new RoofFit.Tile(0, 2, 1, RoofFit.ALONG_Y, true, 1f + 2f * THIRD, 1f + THIRD));
        tiles.add(new RoofFit.Tile(0, 3, 1, RoofFit.ALONG_Y, true, 1f + THIRD, 1f));
        RoofFit.fit(tiles);
        tiles.add(half(0, 1, 1, RoofFit.SOUTH_HALF, 1f + 2f * THIRD));
        List<RoofFit.Tile> made = new ArrayList<>();
        RoofFit.peaks(tiles, made);
        check(made.size() == 1, "the ridge's square is made: " + made.size());
        RoofFit.Tile ridge = made.get(0);
        check(ridge.x == 0 && ridge.y == 1 && ridge.z == 1 && near(ridge.fitLow, 1f + 2f * THIRD), "where the half tile is, at the height the slope reaches");
        check(near(ridge.peak, THIRD / 2f) && ridge.peakAxis == RoofFit.ALONG_Y, "peaked across the way the slope falls");
    }

    private static void halfTileThatJoinsNoSlopeMakesNothing() {
        List<RoofFit.Tile> tiles = eastSlope(4, 3);
        tiles.add(half(4, 1, 2, RoofFit.EAST_HALF, 2.9f));
        List<RoofFit.Tile> made = RoofFit.backs(tiles, SEVEN_WIDE);
        RoofFit.peaks(tiles, made);
        check(on(made, 3, 0).peak == 0f && on(made, 3, 1).peak == 0f, "a half tile at no slope's height peaks nothing");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-3f;
    }
}
