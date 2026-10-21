package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/**
 * Tests of the light lamps in rooms and sets that are on throw (RoomLamps): which of the game's
 * lights are lamps of a room, the colour a lamp gives, how bright a set's picture is, the way a
 * set's glass faces, and that the squares a light reaches are read back by the shader as built.
 */
final class RoomLampsTests {
    private RoomLampsTests() {}

    static void run() {
        Checks.run("aLampHungOnAHouseIsNoRoomsLamp", RoomLampsTests::aLampHungOnAHouseIsNoRoomsLamp);
        Checks.run("aWhiteLampGivesAWarmWhite", RoomLampsTests::aWhiteLampGivesAWarmWhite);
        Checks.run("aColouredShadeColoursPartOfTheLight", RoomLampsTests::aColouredShadeColoursPartOfTheLight);
        Checks.run("aSetIsAsBrightAsItsPicture", RoomLampsTests::aSetIsAsBrightAsItsPicture);
        Checks.run("aSetsGlassFacesTheWayItsSpriteSays", RoomLampsTests::aSetsGlassFacesTheWayItsSpriteSays);
        Checks.run("theShaderReadsBackTheSquaresALightReaches", RoomLampsTests::theShaderReadsBackTheSquaresALightReaches);
        Checks.run("theShaderIsWrittenWithPoints", RoomLampsTests::theShaderIsWrittenWithPoints);
    }

    private static void aLampHungOnAHouseIsNoRoomsLamp() {
        // Read in the game at 10821, 9758: a porch lamp of the outdoor sheet, and table lamps of the indoor ones.
        check(RoomLamps.hungOutside("lighting_outdoor_01_25"), "a porch lamp is hung outside");
        check(!RoomLamps.hungOutside("lighting_indoor_01_41"), "a table lamp is not");
        check(!RoomLamps.hungOutside("lighting_indoor_02_8"), "nor one of the second indoor sheet");
        check(!RoomLamps.hungOutside(null), "nor a lamp with no sprite");
    }

    private static void aWhiteLampGivesAWarmWhite() {
        float[] hue = new float[3];
        // The game lists a white table lamp as (0.94, 0.94, 0.78), which the engine doubles and stops at 1.
        float bright = RoomLamps.hue(0.94f, 0.94f, 0.78f, hue, 0);
        check(bright == 1f, "a white lamp is at full brightness: " + bright);
        check(hue[0] == 1f, "its red is its greatest part: " + hue[0]);
        check(hue[1] < 1f && hue[1] > 0.85f && hue[2] < hue[1] && hue[2] > 0.7f, "and it is a warm white: " + hue[1] + " " + hue[2]);
    }

    private static void aColouredShadeColoursPartOfTheLight() {
        float[] hue = new float[3];
        // A red shade, as the game lists one at 10823, 9753.
        float bright = RoomLamps.hue(0.71f, 0.18f, 0.27f, hue, 0);
        check(bright == 1f, "a red lamp is at full brightness: " + bright);
        check(hue[0] == 1f && hue[1] > 0.5f && hue[1] < 0.8f && hue[2] > 0.5f && hue[2] < 0.8f, "its light is pink, not red: " + hue[1] + " " + hue[2]);
        float dim = RoomLamps.hue(0.2f, 0.31f, 0.59f, hue, 0);
        check(dim == 1f && hue[2] == 1f && hue[0] < hue[2], "a blue lamp is blue at the most: " + hue[0] + " " + hue[2]);
        float faint = RoomLamps.hue(0.2f, 0.2f, 0.2f, hue, 0);
        check(Math.abs(faint - 0.4f) < 1e-6f, "a faint lamp is as bright as the engine doubles it: " + faint);
    }

    private static void aSetIsAsBrightAsItsPicture() {
        // IsoTelevision.updateLightSource lists red 0.58 + 0.25 i for a brightness i from 0 to 1.
        check(RoomLamps.setBrightness(0.58f) == 0.4f, "a dark picture gives the least");
        check(Math.abs(RoomLamps.setBrightness(0.83f) - 1f) < 1e-6f, "a bright one the most");
        check(RoomLamps.setBrightness(0.705f) > 0.69f && RoomLamps.setBrightness(0.705f) < 0.71f, "and one between, between");
        check(RoomLamps.setBrightness(2f) == 1f && RoomLamps.setBrightness(0f) == 0.4f, "and nothing past them");
    }

    private static void aSetsGlassFacesTheWayItsSpriteSays() {
        check(RoomLamps.facingOf("E") == 0f, "east is +x");
        check(Math.abs(RoomLamps.facingOf("S") - Math.PI / 2) < 1e-6, "south is +y");
        check(Math.abs(RoomLamps.facingOf("N") + Math.PI / 2) < 1e-6, "north is -y");
        check(Math.abs(RoomLamps.facingOf("W") - Math.PI) < 1e-6, "west is -x");
        check(RoomLamps.facingOf(null) == RoomLamps.ALL_ROUND, "a sprite that does not say lights all round");
    }

    /** The shader's roomLampReaches, in Java, with exp2 off by a part in a million either way. */
    private static boolean reads(float row, int column, double off) {
        return (Math.floor((row + 0.5) / (Math.pow(2.0, column) * (1.0 + off))) % 2.0) == 1.0;
    }

    private static void theShaderReadsBackTheSquaresALightReaches() {
        // 0b111111000000 is a row of the lamp at 10817, 9759 as the game listed it.
        int[] patterns = {0, (1 << RoomLamps.SIDE) - 1, 0b10101010101010101, 1 << (RoomLamps.SIDE - 1), 1, 0b111111000000};
        for (int bits : patterns) {
            float row = bits;
            check(row == bits, "a row of " + RoomLamps.SIDE + " squares is held exactly in a float: " + bits);
            for (int column = 0; column < RoomLamps.SIDE; column++) {
                for (double off : new double[] {-1e-6, 0.0, 1e-6}) {
                    check(reads(row, column, off) == ((bits >> column & 1) == 1), "square " + column + " of row " + Integer.toBinaryString(bits) + " reads back with exp2 off by " + off);
                }
            }
        }
        check(RoomLamps.ROW_VECTORS * 4 >= RoomLamps.SIDE, "every row has a float");
    }

    private static void theShaderIsWrittenWithPoints() {
        check(RoomLamps.GLSL.contains(" + " + RoomLamps.REACH + ".0;"), "the reach is in the shader as a float");
        check(!RoomLamps.GLSL.matches("(?s).*\\d,\\d.*"), "no number in the shader is written with a comma");
        check(RoomLamps.GLSL.contains("uRoomLampRows[" + RoomLamps.LIGHTS * RoomLamps.ROW_VECTORS + "]"), "the rows are sized for every light");
    }
}
