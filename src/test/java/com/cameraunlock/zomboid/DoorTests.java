package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tests of a door as OpeningBuilder builds it: its raised panels, hinges and knob, and the numbers its wood and metal are drawn under. */
final class DoorTests {
    private DoorTests() {}

    static void run() {
        Checks.run("raisedPanelHasNoGapsBetweenItsPieces", DoorTests::raisedPanelHasNoGapsBetweenItsPieces);
        Checks.run("raisedPanelFieldStandsOutOfEitherFace", DoorTests::raisedPanelFieldStandsOutOfEitherFace);
        Checks.run("raisedPanelBevelsFaceAwayFromItsMiddle", DoorTests::raisedPanelBevelsFaceAwayFromItsMiddle);
        Checks.run("narrowPanelKeepsAFieldAndABevel", DoorTests::narrowPanelKeepsAFieldAndABevel);
        Checks.run("grooveStandsClearOfTheLeafsFace", DoorTests::grooveStandsClearOfTheLeafsFace);
        Checks.run("doorsWoodAndMetalAreDrawnUnderNumbersOfTheirOwn", DoorTests::doorsWoodAndMetalAreDrawnUnderNumbersOfTheirOwn);
        Checks.run("hingesAreOnTheEdgeAwayFromTheKnobAndTheFaceAwayFromTheWall", DoorTests::hingesAreOnTheEdgeAwayFromTheKnobAndTheFaceAwayFromTheWall);
        Checks.run("roseKeepsClearOfThePanelBesideTheKnob", DoorTests::roseKeepsClearOfThePanelBesideTheKnob);
        Checks.run("jambIsFilledFromTheDoorwaysCutToTheLeafOnly", DoorTests::jambIsFilledFromTheDoorwaysCutToTheLeafOnly);
        Checks.run("paneWithARoomOnBothSidesHasNoStreetSide", DoorTests::paneWithARoomOnBothSidesHasNoStreetSide);
    }

    private static final float LEVEL = ViewCamera.LEVEL_HEIGHT, FACE = 0.022f;

    /** Corner k of quad q: along, up and across, with up in tiles. */
    private static float[] corner(float[] quads, int q, int k) {
        int at = q * OpeningBuilder.PANEL_QUAD + 3 * k;
        return new float[] {quads[at], quads[at + 1] * LEVEL, quads[at + 2]};
    }

    /** The way quad q faces, from its corners' turn, made to point out of the face the panel is on. */
    private static float[] facing(float[] quads, int q, float face) {
        float[] c0 = corner(quads, q, 0), c1 = corner(quads, q, 1), c3 = corner(quads, q, 3);
        float ux = c1[0] - c0[0], uy = c1[1] - c0[1], uz = c1[2] - c0[2];
        float vx = c3[0] - c0[0], vy = c3[1] - c0[1], vz = c3[2] - c0[2];
        float[] n = {uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx};
        float sign = Math.signum(face);
        return new float[] {n[0] * sign, n[1] * sign, n[2] * sign};
    }

    private static boolean same(float[] a, float[] b) {
        return Math.abs(a[0] - b[0]) < 1e-6f && Math.abs(a[1] - b[1]) < 1e-6f && Math.abs(a[2] - b[2]) < 1e-6f;
    }

    /** Whether point p is a corner of quad q. */
    private static boolean cornerOf(float[] quads, int q, float[] p) {
        for (int k = 0; k < 4; k++) {
            if (same(corner(quads, q, k), p)) {
                return true;
            }
        }
        return false;
    }

    private static void raisedPanelHasNoGapsBetweenItsPieces() {
        float[] quads = OpeningBuilder.raisedPanel(0.2f, 0.45f, 0.1f, 0.3f, FACE);
        check(quads.length == 9 * OpeningBuilder.PANEL_QUAD, "a raised panel is a groove of four, four bevels and a field");
        // Every bevel shares its two outer corners with the groove's inside and its two inner ones with the field.
        for (int bevel = 4; bevel < 8; bevel++) {
            int onField = 0, onGroove = 0;
            for (int k = 0; k < 4; k++) {
                float[] p = corner(quads, bevel, k);
                onField += cornerOf(quads, 8, p) ? 1 : 0;
                boolean grooved = false;
                for (int g = 0; g < 4; g++) {
                    grooved |= cornerOf(quads, g, p);
                }
                onGroove += grooved ? 1 : 0;
            }
            check(onField == 2 && onGroove == 2, "bevel " + bevel + " meets the field along one edge and the groove along the other");
        }
    }

    private static void raisedPanelFieldStandsOutOfEitherFace() {
        for (float face : new float[] {FACE, -FACE}) {
            float[] quads = OpeningBuilder.raisedPanel(0.2f, 0.45f, 0.1f, 0.3f, face);
            float field = corner(quads, 8, 0)[2], groove = corner(quads, 0, 0)[2];
            check(Math.abs(field) > Math.abs(groove) && Math.abs(groove) >= Math.abs(face), "the field stands further out of the face at " + face + " than the groove, which is not inside the leaf");
        }
    }

    private static void raisedPanelBevelsFaceAwayFromItsMiddle() {
        for (float face : new float[] {FACE, -FACE}) {
            float[] quads = OpeningBuilder.raisedPanel(0.2f, 0.45f, 0.1f, 0.3f, face);
            for (int q = 0; q < 9; q++) {
                // Turned along into up, as raisedPanel says: out of the leaf is then the face's own way.
                check(facing(quads, q, face)[2] * Math.signum(face) > 0f, "the corners of piece " + q + " of a panel on the face at " + face + " turn along into up");
            }
            check(facing(quads, 4, face)[1] < 0f, "the foot's bevel faces down, on the face at " + face);
            check(facing(quads, 5, face)[1] > 0f, "the head's bevel faces up, on the face at " + face);
            check(facing(quads, 6, face)[0] < 0f, "the bevel at the panel's start faces back along the leaf, on the face at " + face);
            check(facing(quads, 7, face)[0] > 0f, "the bevel at the panel's end faces on along the leaf, on the face at " + face);
        }
    }

    private static void narrowPanelKeepsAFieldAndABevel() {
        // The top panels of a six-panel door are a little more than a hand high.
        float[] quads = OpeningBuilder.raisedPanel(0.2f, 0.45f, 0.7f, 0.74f, FACE);
        float[] g0 = corner(quads, 4, 0), g1 = corner(quads, 5, 2), f0 = corner(quads, 8, 0), f2 = corner(quads, 8, 2);
        float innerWide = g1[0] - g0[0], innerHigh = g1[1] - g0[1];
        check(f2[0] - f0[0] >= 0.3f * innerWide && f2[1] - f0[1] >= 0.3f * innerHigh, "the field of a panel a tenth of a tile high is at least 0.3 of it each way");
        check(f0[1] - g0[1] >= 0.01f, "its bevel is at least a centimetre across, enough to catch the light");
    }

    private static void grooveStandsClearOfTheLeafsFace() {
        for (float face : new float[] {FACE, -FACE}) {
            float[] quads = OpeningBuilder.raisedPanel(0.2f, 0.45f, 0.1f, 0.3f, face);
            check(Math.abs(corner(quads, 0, 0)[2]) - Math.abs(face) >= 0.0015f - 1e-6f, "the groove on the face at " + face + " lies at least 1.5 mm out of it");
        }
    }

    /**
     * Each number of the door's own is tested by exactly the blocks of the world's program that
     * are its, and by none on the way into it: a block taken out, or another material given the
     * same number or a range over it, fails.
     */
    private static void doorsWoodAndMetalAreDrawnUnderNumbersOfTheirOwn() {
        Pattern range = Pattern.compile("vMaterial > ([0-9.]+) && vMaterial < ([0-9.]+)");
        float[] numbers = {OpeningBuilder.JOINERY, OpeningBuilder.JOINERY_LEVEL, OpeningBuilder.HARDWARE};
        // The grain, the gloss and the shade of a room drawn without its corners shaded for each wood; the colour and the gloss for the metal.
        int[] blocks = {3, 3, 2};
        for (int i = 0; i < numbers.length; i++) {
            check(ranges(range, PipelineShaders.WORLD_FRAGMENT_MAIN, numbers[i]) == blocks[i],
                    "the world's fragment program has " + blocks[i] + " blocks for " + numbers[i] + ", and no other range over it");
            check(ranges(range, PipelineShaders.WORLD_VERTEX, numbers[i]) == 0, "the world's vertex program reads " + numbers[i] + " as it is: no floorboard, no model");
        }
    }

    private static void paneWithARoomOnBothSidesHasNoStreetSide() {
        check(OpeningBuilder.paneMaterial(true, true, false) == WorldScene.INNER_PANE, "a pane between two rooms has no street side");
        check(OpeningBuilder.paneMaterial(true, true, true) == WorldScene.INNER_PANE, "and is never lit for the street");
        check(OpeningBuilder.paneMaterial(true, false, false) == WorldScene.PANE, "a pane with the street beyond it mirrors the street on that side");
        check(OpeningBuilder.paneMaterial(true, false, true) == WorldScene.LIT_PANE, "and is lit for the street by a lamp in its room");
        check(OpeningBuilder.paneSide(true) == 0 && OpeningBuilder.paneSide(false) == 1,
                "a pane faces into the square its edge is read from when that is the room, and into the one beyond when the room is there");
        // Swung open, a leaf stands in one square, which is on both its faces: addBuiltDoor asks with that square
        // both sides, so its own side always counts as the room side.
        for (boolean inRoom : new boolean[] {true, false}) {
            check(OpeningBuilder.paneMaterial(true, inRoom, false) == (inRoom ? WorldScene.INNER_PANE : WorldScene.PANE),
                    inRoom ? "the glass of a door swung open into its room has the room on both faces" : "and of one swung open out of doors has a street side");
        }
        Pattern range = Pattern.compile("vMaterial > ([0-9.]+) && vMaterial < ([0-9.]+)");
        check(ranges(range, PipelineShaders.WORLD_FRAGMENT_MAIN, WorldScene.INNER_PANE) == 1,
                "the world's fragment program reads such a pane in one place, as glass with no street side, and in no other range");
        check(ranges(range, PipelineShaders.WORLD_FRAGMENT_MAIN, WallBuilder.INNER_GLASS) == 1, "and the glass of a window's sprite between two rooms in one place too");
        check(ranges(range, PipelineShaders.WORLD_VERTEX, WorldScene.INNER_PANE) == 0 && ranges(range, PipelineShaders.WORLD_VERTEX, WallBuilder.INNER_GLASS) == 0,
                "the world's vertex program reads neither as anything of its own");
    }

    private static int ranges(Pattern range, String program, float number) {
        int found = 0;
        for (Matcher m = range.matcher(program); m.find(); ) {
            if (Float.parseFloat(m.group(1)) < number && number < Float.parseFloat(m.group(2))) {
                found++;
            }
        }
        return found;
    }

    private static void hingesAreOnTheEdgeAwayFromTheKnobAndTheFaceAwayFromTheWall() {
        for (boolean handleAtEnd : new boolean[] {false, true}) {
            float[] hinges = OpeningBuilder.hinges(0.1f, 0.85f, 0f, 0.79f, handleAtEnd);
            float edge = handleAtEnd ? 0.1f : 0.85f;
            check(hinges.length == 12, "three hinges");
            for (int at = 0; at < hinges.length; at += 4) {
                check(hinges[at] < edge && edge < hinges[at + 1], "a hinge straddles the edge at " + edge + " with the knob at the " + (handleAtEnd ? "end" : "start"));
                check(at == 0 || hinges[at + 2] > hinges[at - 1], "the hinges go up the edge one above another");
            }
        }
        // Swung open the leaf stands into its square, out from the wall it is back against, and
        // its knuckles stand out of the face further from that wall.
        float faceWay = OpeningBuilder.HINGED_SIDE == 0 ? 1f : -1f;
        float knuckle = OpeningBuilder.SWUNG_OUT + faceWay * (OpeningBuilder.DOOR_HALF + OpeningBuilder.HINGE_OUT);
        check(OpeningBuilder.SWUNG_OUT > 0f && knuckle > OpeningBuilder.SWUNG_OUT, "a swung door's knuckles are on the face away from the wall it is back against");
    }

    private static void roseKeepsClearOfThePanelBesideTheKnob() {
        for (float wide : new float[] {0.55f, 0.65f, 0.78f, 0.9f}) {
            float rose = OpeningBuilder.rose(true, wide);
            check(rose > 0f && OpeningBuilder.KNOB_IN * wide + rose < OpeningBuilder.PANELS_ACROSS[0] * wide,
                    "on a panelled leaf " + wide + " wide the rose (" + rose + ") ends short of the panel");
        }
    }

    private static boolean pair(float[] out, int at, float from, float to) {
        return Math.abs(out[2 * at] - from) < 1e-6f && Math.abs(out[2 * at + 1] - to) < 1e-6f;
    }

    private static void jambIsFilledFromTheDoorwaysCutToTheLeafOnly() {
        float[] out = new float[4];
        float over = OpeningBuilder.JAMB_OVER;
        // The church's double door, as measured: each leaf's square has half the doorway, which runs on into the other square.
        check(OpeningBuilder.jambs(0.1875f, 1f, 0.234375f, 1f, out) == 0, "a leaf reaching well into the wall past the cut is not filled beside, where the wall is");
        check(OpeningBuilder.jambs(0f, 0.75f, 0f, 0.75f, out) == 1 && pair(out, 0, 0.75f, 0.75f + over),
                "a leaf ending at the cut is closed a little way on into the wall, and where the leaves meet the gap stays open");
        check(OpeningBuilder.jambs(0.25f, 1f, 0.0625f, 1f, out) == 1 && pair(out, 0, 0.0625f - over, 0.25f), "a gap between the cut and a leaf is filled across");
        check(OpeningBuilder.jambs(0.25f, 1f, 0f, 1f, out) == 0, "a leaf in a doorway that runs on at both ends is not filled");
        check(OpeningBuilder.jambs(0.5f, 0.6f, 0.1f, 0.9f, out) == 2 && pair(out, 0, 0.1f - over, 0.5f) && pair(out, 1, 0.6f, 0.9f + over),
                "a leaf set far in is filled all the way to the cut on both sides");
        check(OpeningBuilder.jambs(0.2f, 0.8f, -1f, 0f, out) == 0, "with no doorway measured in a wall, as in a fence, nothing is filled");
    }
}
