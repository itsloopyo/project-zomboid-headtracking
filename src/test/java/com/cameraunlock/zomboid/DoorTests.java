package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import zombie.core.textures.Texture;

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
        Checks.run("leafIsCutBackToTheDoorwaysCut", DoorTests::leafIsCutBackToTheDoorwaysCut);
        Checks.run("casingTakesItsTexelsWhereTheWallsCutIsMeasured", DoorTests::casingTakesItsTexelsWhereTheWallsCutIsMeasured);
        Checks.run("casingIsReadAsPostsUnderAHead", DoorTests::casingIsReadAsPostsUnderAHead);
        Checks.run("woodIsToldFromPaintByColourNotDarkness", DoorTests::woodIsToldFromPaintByColourNotDarkness);
        Checks.run("casingsPostsMeetTheDoorwaysCutUnderTheHead", DoorTests::casingsPostsMeetTheDoorwaysCutUnderTheHead);
        Checks.run("casingSharesNoPlaneWithTheRevealOrTheSkirting", DoorTests::casingSharesNoPlaneWithTheRevealOrTheSkirting);
        Checks.run("whatIsDrawnBetweenOwningNoThingIsNoPartOfTheThing", DoorTests::whatIsDrawnBetweenOwningNoThingIsNoPartOfTheThing);
        Checks.run("paneWithARoomOnBothSidesHasNoStreetSide", DoorTests::paneWithARoomOnBothSidesHasNoStreetSide);
        Checks.run("openGarageDoorLiesInTheGarageWithItsTracksAtTheJambs", DoorTests::openGarageDoorLiesInTheGarageWithItsTracksAtTheJambs);
        Checks.run("capUnderAWallsFootLeavesTheDoorwayFloorBare", DoorTests::capUnderAWallsFootLeavesTheDoorwayFloorBare);
        Checks.run("doorwaysSidesArePaintedAsItsWallNotInConcrete", DoorTests::doorwaysSidesArePaintedAsItsWallNotInConcrete);
        Checks.run("eachHalfOfAnOpeningsSidesIsPaintedAsTheSkinOnItsSide", DoorTests::eachHalfOfAnOpeningsSidesIsPaintedAsTheSkinOnItsSide);
        Checks.run("anOpenDoorIsItsShutLeafOnItsOpenOutline", DoorTests::anOpenDoorIsItsShutLeafOnItsOpenOutline);
        Checks.run("aShutLeafReadWithItsHandleAtItsStartIsTurnedEndForEnd", DoorTests::aShutLeafReadWithItsHandleAtItsStartIsTurnedEndForEnd);
        Checks.run("aLongHandleIsALeverAndARoundOneAKnob", DoorTests::aLongHandleIsALeverAndARoundOneAKnob);
        Checks.run("aLeversArmRunsFromItsNeckTowardsTheHinges", DoorTests::aLeversArmRunsFromItsNeckTowardsTheHinges);
        Checks.run("aBeadRunsRoundALightHalfOverItsGlass", DoorTests::aBeadRunsRoundALightHalfOverItsGlass);
        Checks.run("aBarBetweenLightsRunsOnToTheRails", DoorTests::aBarBetweenLightsRunsOnToTheRails);
        Checks.run("aDoorAskedForOnAnEdgeItDoesNotPaintIsStillReadOnItsOwn", DoorTests::aDoorAskedForOnAnEdgeItDoesNotPaintIsStillReadOnItsOwn);
        Checks.run("anOpenLeafStandingIntoItsSquareIsStoodOnItsFloor", DoorTests::anOpenLeafStandingIntoItsSquareIsStoodOnItsFloor);
        Checks.run("aToiletStallsLeafRaggedTopAndBottomIsALeaf", DoorTests::aToiletStallsLeafRaggedTopAndBottomIsALeaf);
        Checks.run("aShacksDoorWithABareCornerIsALeaf", DoorTests::aShacksDoorWithABareCornerIsALeaf);
        Checks.run("aDoorDrawnAsItsSpriteIsToldOnceForEachReason", DoorTests::aDoorDrawnAsItsSpriteIsToldOnceForEachReason);
    }

    private static void capUnderAWallsFootLeavesTheDoorwayFloorBare() {
        check(java.util.Arrays.equals(WallBuilder.underFoot(-1f, -1f, 0f), new float[] {0f, 1f}), "a wall with no opening is capped along all its length");
        check(java.util.Arrays.equals(WallBuilder.underFoot(0.2f, 0.7f, 0.35f), new float[] {0f, 1f}), "a window's sill stands over wall, so the cap runs under it");
        check(java.util.Arrays.equals(WallBuilder.underFoot(0.2f, 0.7f, 0f), new float[] {0f, 0.2f, 0.7f, 1f}), "a doorway down to the floor has no cap across it");
        check(java.util.Arrays.equals(WallBuilder.underFoot(0f, 0.7f, 0f), new float[] {0.7f, 1f}), "a doorway from the wall's start leaves only the far end capped");
        check(java.util.Arrays.equals(WallBuilder.underFoot(0.3f, 1f, 0f), new float[] {0f, 0.3f}), "and one to its end only the near end");
        check(WallBuilder.underFoot(0f, 1f, 0f).length == 0, "and one across the whole wall none");
    }

    /**
     * Louisville, Spiffo's, 12722,1613 (spiffos_01_11, a doorway with no casing): its sides, from skin to skin, were bare
     * concrete, white under the room's light, a pale strip down the doorway beside a cream wall. A north wall's sprite at
     * the game's doubled scale with a doorway from 0.3 to 0.7 along and up to 0.8 of a level is read, and the jambs and the
     * lintel's underside are painted in the colour most of the sprite is, the paint of the wall, both halves of the wall's
     * thickness alike with the same place on both sides. A sprite not read yet still has its sides closed, in concrete.
     */
    private static void doorwaysSidesArePaintedAsItsWallNotInConcrete() {
        java.util.Map<Texture, float[]> colours = TestMap.read(null, SpriteColours.class, "known");
        Texture read = holedWall(41), unread = holedWall(42);
        float[] cream = {0.8f, 0.75f, 0.55f};
        colours.put(read, cream);
        try {
            TestMap map = new TestMap();
            SpriteGeometry.Mesh paint = WorldScene.sharedSolid.get(WorldScene.PLAIN, WallSides.laid(cream[0], cream[1], cream[2], 0f, 0f, 0f, WallSides.FINE));
            paint.clear();
            addWall(map, read);
            check(TestMap.plain().triangles() == 0, "nothing of a doorway in a wall whose sprite is read is bare concrete: " + TestMap.plain().triangles());
            check(paint.triangles() == 2 * 3 * 2, "both halves of its jambs and its lintel's underside are in the wall's paint: " + paint.triangles());
            check((float) Math.floor((paint.open + 0.5f) / 32f) == WorldScene.PAINT, "drawn as paint");
            float least = Float.MAX_VALUE, most = -Float.MAX_VALUE;
            for (int at = 0; at < paint.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                least = Math.min(least, paint.vertices[at + 1]);
                most = Math.max(most, paint.vertices[at + 1]);
            }
            check(Math.abs(least - (1f - WorldScene.WALL_HALF)) < 1e-5f && Math.abs(most - (1f + WorldScene.WALL_HALF)) < 1e-5f,
                    "from one skin to the other: " + least + " to " + most);
            paint.clear();
            addWall(map, unread);
            check(TestMap.plain().triangles() == 2 * 3 * 2 && paint.triangles() == 0, "a wall whose sprite is not read yet has its doorway's sides in concrete");
        } finally {
            colours.remove(read);
            colours.remove(unread);
            WorldScene.sharedSolid.clear();
            WorldScene.sharedLaid.clear();
        }
    }

    /** A north wall's sprite at the game's doubled scale, painted but for a doorway from 0.3 to 0.7 along and from the floor to 0.8 of a level. */
    private static Texture holedWall(int id) {
        Texture texture = new Texture(id, 128, 256);
        for (int u = 64; u < 128; u++) {
            for (int v = 0; v < 256; v++) {
                float along = (u + 0.5f - 64f) / 64f, up = (192f + 32f * along - (v + 0.5f)) / 192f;
                if (up >= 0f && up <= 1f && !(along > 0.3f && along < 0.7f && up < 0.8f)) {
                    texture.paint(u, v, u + 1, v + 1);
                }
            }
        }
        return texture;
    }

    /** Adds the wall with that sprite on the north edge of square (0, 1), with no other place beyond it. */
    private static void addWall(TestMap map, Texture texture) {
        WorldScene.Frame build = map.build;
        TestMap.plain().clear();
        zombie.iso.IsoObject wall = map.cell.square(0, 1, 0).add(map.spriteWith("walls_exterior_house_01_1", zombie.iso.SpriteDetails.IsoObjectType.wall, texture,
                "WallN", "", "wall", ""));
        wall.offsetX = 64f;
        wall.offsetY = 192f;
        build.here = map.cell.getGridSquare(0, 1, 0);
        java.util.Arrays.fill(build.geometry.lift, 0f);
        build.geometry.kerb = null;
        WorldScene.frameOf(build.frame, wall, texture);
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        mesh.texture = texture.getID();
        build.wallBuilder.addEdge(mesh, texture, true, false, 0, 1, 0, 0f, 1f, 0f);
    }

    /**
     * A window between a room and another place (the room's sprite and the far skin's colour, as farTint reads it): the
     * half of its sides inside the square, from the wall's middle to the skin there, is in the near paint, and the half
     * beyond in the far paint, sill, jambs and lintel alike. Each corner is lit as the wall's skin is where the opening cuts
     * it, from the light at the square's corners at its place along the wall, and not with the square's own light.
     */
    private static void eachHalfOfAnOpeningsSidesIsPaintedAsTheSkinOnItsSide() {
        TestMap map = new TestMap();
        WorldScene.Frame build = map.build;
        TestMap.write(build.wallBuilder, "farLight", -1);
        int floats = TestMap.<Integer>read(null, WallBuilder.class, "OPENING_FLOATS");
        float[] opening = new float[2 * floats];
        java.util.Arrays.fill(opening, -1f);
        opening[TestMap.<Integer>read(null, WallBuilder.class, "OPENING_START")] = 0.3f;
        opening[TestMap.<Integer>read(null, WallBuilder.class, "OPENING_END")] = 0.7f;
        opening[TestMap.<Integer>read(null, WallBuilder.class, "OPENING_FOOT")] = 0.35f;
        opening[TestMap.<Integer>read(null, WallBuilder.class, "OPENING_HEAD")] = 0.8f;
        java.util.Arrays.fill(build.geometry.lift, 0f);
        float[] cream = {0.8f, 0.75f, 0.55f}, blue = {0.3f, 0.4f, 0.6f};
        SpriteGeometry.Mesh near = WorldScene.sharedSolid.get(WorldScene.PLAIN, WallSides.laid(cream[0], cream[1], cream[2], 0f, 0f, 0f, WallSides.FINE));
        SpriteGeometry.Mesh far = WorldScene.sharedSolid.get(WorldScene.PLAIN, WallSides.laid(blue[0], blue[1], blue[2], 0f, 0f, 0f, WallSides.FINE));
        near.clear();
        far.clear();
        try {
            build.wallBuilder.addReveals(opening, 0, 0, 1, 0, 0f, 0f, 1f, 0f, 0f, WorldScene.WALL_HALF, cream, blue);
            check(near.triangles() == 4 * 2 && far.triangles() == 4 * 2 && TestMap.plain().triangles() == 0,
                    "each half's jambs, sill and lintel are in its own paint: " + near.triangles() + ", " + far.triangles() + ", " + TestMap.plain().triangles());
            for (int at = 0; at < near.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                check(near.vertices[at + 1] >= 1f - 1e-6f && near.vertices[at + 1] <= 1f + WorldScene.WALL_HALF + 1e-6f, "the near paint is inside the square: " + near.vertices[at + 1]);
            }
            for (int at = 0; at < far.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                check(far.vertices[at + 1] >= 1f - WorldScene.WALL_HALF - 1e-6f && far.vertices[at + 1] <= 1f + 1e-6f, "the far paint is beyond it: " + far.vertices[at + 1]);
            }
            for (SpriteGeometry.Mesh half : new SpriteGeometry.Mesh[] {near, far}) {
                for (int at = 0; at < half.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                    float x = half.vertices[at], a = half.vertices[at + SpriteGeometry.A_AT], b = half.vertices[at + SpriteGeometry.B_AT];
                    check(Math.abs(a - x) < 1e-5f && (Math.abs(a - 0.3f) < 1e-5f || Math.abs(a - 0.7f) < 1e-5f) && b == 0f,
                            "a corner at " + x + " along is lit from the corners' light at the cut it stands at, " + x + " along the wall's line: " + a + ", " + b);
                }
            }
        } finally {
            WorldScene.sharedSolid.clear();
            WorldScene.sharedLaid.clear();
        }
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

    private static void woodIsToldFromPaintByColourNotDarkness() {
        // fixtures_doors_01_13 and _12, one mahogany door, as its north and its darker west sprite read.
        check(OpeningBuilder.wooden(0.312f, 0.175f, 0.149f) && OpeningBuilder.wooden(0.227f, 0.132f, 0.116f), "a brown door is wood from either face");
        // fixtures_windows_01_64 and _48: black frames, one a hair warmer.
        check(!OpeningBuilder.wooden(0.15f, 0.13f, 0.12f) && !OpeningBuilder.wooden(0.16f, 0.14f, 0.13f), "a black frame is paint, warm or not");
        check(!OpeningBuilder.wooden(0f, 0f, 0f) && !OpeningBuilder.wooden(0.95f, 0.9f, 0.78f) && !OpeningBuilder.wooden(0.5f, 0.5f, 0.5f), "and so are black, cream and grey");
        check(OpeningBuilder.wooden(0.85f, 0.72f, 0.55f) && OpeningBuilder.wooden(0.55f, 0.4f, 0.28f), "pale and mid oak are wood");
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
        check(OpeningBuilder.paneMaterial(false, true, true) == WorldScene.LIT_PANE, "whichever side of the edge the room is");
        check(OpeningBuilder.paneMaterial(false, false, false) == WorldScene.OUTER_PANE && OpeningBuilder.paneMaterial(false, false, true) == WorldScene.OUTER_PANE,
                "a pane with the street on both sides mirrors the street to both, and has no room to be lit from");
        check(OpeningBuilder.roomSide(true, true, false) && !OpeningBuilder.roomSide(false, false, true) && !OpeningBuilder.roomSide(false, true, true),
                "a room's square is the room side of its pane, and beside a room nothing else is");
        check(OpeningBuilder.roomSide(false, true, false) && !OpeningBuilder.roomSide(false, false, false),
                "with no room either side, the roofed square is the room side, as WindowGlass takes an eye under a roof for indoors");
        boolean shed = OpeningBuilder.roomSide(false, true, false), yard = OpeningBuilder.roomSide(false, false, false);
        check(OpeningBuilder.paneMaterial(shed, yard, false) == WorldScene.PANE && OpeningBuilder.paneMaterial(yard, shed, false) == WorldScene.PANE,
                "so a window in a building the player has put up has the street on one face only, whichever side it is read from");
        check(OpeningBuilder.paneMaterial(shed, shed, false) == WorldScene.INNER_PANE, "and one between two of its roofed squares none");
        check(!OpeningBuilder.roofed(null), "past the loaded map is under no roof");
        check(OpeningBuilder.paneSide(true) == 0 && OpeningBuilder.paneSide(false) == 1,
                "a pane faces into the square its edge is read from when that is the room, and into the one beyond when the room is there");
        // Swung open, a leaf stands in one square, which is on both its faces: addBuiltDoor asks with that square both sides.
        for (boolean inRoom : new boolean[] {true, false}) {
            check(OpeningBuilder.paneMaterial(inRoom, inRoom, false) == (inRoom ? WorldScene.INNER_PANE : WorldScene.OUTER_PANE),
                    inRoom ? "the glass of a door swung open into its room has the room on both faces" : "and of one swung open out of doors the street on both");
        }
        Pattern range = Pattern.compile("vMaterial > ([0-9.]+) && vMaterial < ([0-9.]+)");
        check(ranges(range, PipelineShaders.WORLD_FRAGMENT_MAIN, WorldScene.INNER_PANE) == 1,
                "the world's fragment program reads such a pane in one place, as glass with no street side, and in no other range");
        check(ranges(range, PipelineShaders.WORLD_FRAGMENT_MAIN, WallBuilder.INNER_GLASS) == 1, "and the glass of a window's sprite between two rooms in one place too");
        check(ranges(range, PipelineShaders.WORLD_FRAGMENT_MAIN, WorldScene.OUTER_PANE) == 1, "and a pane with the street on both sides in one place");
        check(ranges(range, PipelineShaders.WORLD_VERTEX, WorldScene.INNER_PANE) == 0 && ranges(range, PipelineShaders.WORLD_VERTEX, WallBuilder.INNER_GLASS) == 0
                && ranges(range, PipelineShaders.WORLD_VERTEX, WorldScene.OUTER_PANE) == 0,
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
        check(OpeningBuilder.jambs(0.1875f, 1f, 0.234375f, 1f, false, out) == 0, "a leaf reaching well into the wall past the cut is not filled beside, where the wall is");
        check(OpeningBuilder.jambs(0f, 0.75f, 0f, 0.75f, false, out) == 1 && pair(out, 0, 0.75f, 0.75f + over),
                "a leaf ending at a cut with no reveal is closed a little way on into the wall, and where the leaves meet the gap stays open");
        check(OpeningBuilder.jambs(0.25f, 1f, 0.0625f, 1f, false, out) == 1 && pair(out, 0, 0.0625f - over, 0.25f), "a gap between the cut and a leaf is filled across");
        check(OpeningBuilder.jambs(0.25f, 1f, 0f, 1f, false, out) == 0, "a leaf in a doorway that runs on at both ends is not filled");
        check(OpeningBuilder.jambs(0.5f, 0.6f, 0.1f, 0.9f, true, out) == 2 && pair(out, 0, 0.1f - over, 0.5f) && pair(out, 1, 0.6f, 0.9f + over),
                "a leaf set far in is filled all the way to the cut on both sides, reveal or none");
        check(OpeningBuilder.jambs(0.2f, 0.8f, -1f, 0f, false, out) == 0, "with no doorway measured in a wall, as in a fence, nothing is filled");
        // Louisville, 12785,1664: the leaf cut back to the doorway from 0.09375 to 0.84375, whose ends the wall's reveals close.
        check(OpeningBuilder.jambs(0.09375f, 0.84375f, 0.09375f, 0.84375f, true, out) == 0,
                "a leaf meeting a cut the wall's reveal closes is not filled: the fill's edge lay in the reveal's plane");
        check(OpeningBuilder.jambs(0.09375f, 0.84375f, 0.09375f, 0.84375f, false, out) == 2, "and with no reveal it is");
    }

    private static Windows.Shape leaf(float start, float end) {
        Windows.Shape shape = new Windows.Shape();
        shape.start = start;
        shape.end = end;
        shape.head = 0.79f;
        return shape;
    }

    /**
     * Louisville, 12785,1664: the post of fixtures_doors_frames_01_1 is painted to pixel 69, as walls_exterior_house_01_15
     * is, and the doorway is cut at 0.09375 where the wall's sprite is next unpainted, pixel 70. The casing's face on the
     * square's north edge takes pixel 70 at 0.09375 too, so its post ends at the cut. Drawn in as a wall is, it took
     * pixel 70 at 0.052 and the wall's skin showed between the post and the leaf.
     */
    private static void casingTakesItsTexelsWhereTheWallsCutIsMeasured() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.width = 192f;
        frame.height = 256f;
        frame.uEnd = 1f;
        frame.vEnd = 1f;
        LightSiteTests.sited(frame);
        SpriteGeometry geometry = new SpriteGeometry();
        geometry.exactAcross = true;
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        geometry.addFace(mesh, frame, 0, 0, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
        check(mesh.floats > 0, "the casing's face is added");
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float along = mesh.vertices[at], pixel = mesh.vertices[at + 3] * frame.width;
            check(Math.abs(pixel - (64f + 64f * along)) < 1e-3f, "a corner at " + along + " along takes pixel " + (64f + 64f * along) + ", not " + pixel);
        }
        SpriteGeometry.Mesh wall = new SpriteGeometry.Mesh();
        new SpriteGeometry().addFace(wall, frame, 0, 0, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f);
        check(Math.abs(wall.vertices[3] * frame.width - (64f + 64f * wall.vertices[0]) - (wall.vertices[0] < 0.5f ? 3f : -3f)) < 1e-3f,
                "a wall is still drawn in 3 pixels at its ends");
    }

    /** A casing laid flat at the game's doubled scale: 64 dots along, 8 more past each end, 192 up; FRAME over the given columns from row top down to the floor, and over the head's rows from column from to before to. */
    private static byte[] casingDots(int[] posts, int top, int headRows, int from, int to) {
        int across = 64, margin = 8, wide = across + 2 * margin, high = 192;
        byte[] dots = new byte[wide * high];
        for (int post = 0; post < posts.length; post += 2) {
            for (int row = top; row < high; row++) {
                for (int column = posts[post]; column < posts[post + 1]; column++) {
                    dots[row * wide + margin + column] = Windows.FRAME;
                }
            }
        }
        for (int row = top; row < top + headRows; row++) {
            for (int column = from; column < to; column++) {
                dots[row * wide + margin + column] = Windows.FRAME;
            }
        }
        return dots;
    }

    private static void casingIsReadAsPostsUnderAHead() {
        Windows.Shape both = Casings.measure(casingDots(new int[] {4, 10, 54, 60}, 40, 12, 4, 60), 64, 8, 192);
        check(both != null, "two posts under a head are a casing");
        check(both.start == 4f / 64f && both.startWide == 6f / 64f && both.end == 60f / 64f && both.endWide == 6f / 64f,
                "its posts are where they are painted: " + both.start + "+" + both.startWide + " .. " + both.end + "-" + both.endWide);
        check(both.head == 1f - 40f / 192f && both.headHigh == 12f / 192f && both.foot == 0f, "its head is its top rows, and its posts stand on the floor");
        Windows.Shape lone = Casings.measure(casingDots(new int[] {4, 10}, 40, 12, 4, 72), 64, 8, 192);
        check(lone != null && lone.startWide > 0f && lone.endWide == 0f && lone.end == 1f, "one post under a head running on to the square's end is half of a pair's casing");
        check(Casings.measure(casingDots(new int[] {4, 10, 54, 60}, 40, 0, 4, 60), 64, 8, 192) == null, "posts with no head are no casing");
        check(Casings.measure(casingDots(new int[] {0, 64}, 40, 12, 0, 64), 64, 8, 192) == null, "a wall painted across is no casing");
        check(Casings.measure(casingDots(new int[] {4, 10, 30, 34, 54, 60}, 40, 12, 4, 60), 64, 8, 192) == null, "three uprights are no casing");
        check(Casings.measure(casingDots(new int[] {4, 10, 54, 60}, 140, 12, 4, 60), 64, 8, 192) == null, "a head under half a level is no doorway's");
    }

    private static void casingsPostsMeetTheDoorwaysCutUnderTheHead() {
        // Louisville, 12785,1664: fixtures_doors_frames_01_1 read in the game, its posts -0.046875 + 0.140625 and 0.9375 - 0.09375,
        // in walls_exterior_house_01_15 cut from 0.09375 to 0.84375.
        Windows.Shape casing = new Windows.Shape();
        casing.start = -0.046875f;
        casing.startWide = 0.140625f;
        casing.end = 0.9375f;
        casing.endWide = 0.09375f;
        casing.head = 0.8489583f;
        casing.headHigh = 0.067708336f;
        float[] members = OpeningBuilder.casingMembers(casing, 0.09375f, 0.84375f);
        int each = OpeningBuilder.CASING_MEMBER;
        check(members.length == 3 * each, "two posts and a head: " + members.length / each);
        check(members[1] == 0.09375f && members[each] == 0.84375f, "each post's inner edge is at the cut: " + members[1] + ", " + members[each]);
        check(Math.abs(members[1] - members[0] - 0.09375f) < 1e-6f && Math.abs(members[each + 1] - members[each] - 0.09375f) < 1e-6f,
                "both posts are as wide as the narrower: the wider is painted with its side");
        float headFoot = casing.head - casing.headHigh;
        check(members[3] == headFoot && members[each + 3] == headFoot, "the posts stand under the head");
        check(members[2 * each] == members[0] && members[2 * each + 1] == members[each + 1] && members[2 * each + 2] == headFoot && members[2 * each + 3] == casing.head,
                "the head runs over both posts, out to their outer edges");
        check(members[4] == OpeningBuilder.AT_FROM && members[each + 4] == OpeningBuilder.AT_TO && members[2 * each + 4] == OpeningBuilder.AT_HEAD,
                "each board is bevelled along its outer edge");
        check(OpeningBuilder.casingMaterial(true, (int) members[4]) == OpeningBuilder.JOINERY && OpeningBuilder.casingMaterial(true, (int) members[each + 4]) == OpeningBuilder.JOINERY,
                "a wooden post's grain runs up it");
        check(OpeningBuilder.casingMaterial(true, (int) members[2 * each + 4]) == OpeningBuilder.JOINERY_LEVEL, "and the head's along it");
        for (int at = 0; at < members.length; at += each) {
            check(OpeningBuilder.casingMaterial(false, (int) members[at + 4]) == WorldScene.PAINT, "a painted casing is paint, every board of it");
        }
        float[] far = OpeningBuilder.casingMembers(casing, 0.2f, 0.6f);
        check(far[1] == casing.start + casing.startWide && far[each] == casing.end - casing.endWide, "a cut too far from the posts leaves them where they are painted");
        float[] none = OpeningBuilder.casingMembers(casing, -1f, -1f);
        check(none[1] == casing.start + casing.startWide, "and so does no cut measured");
        Windows.Shape lone = new Windows.Shape();
        lone.start = 0.05f;
        lone.startWide = 0.09f;
        lone.end = 1f;
        lone.head = 0.85f;
        lone.headHigh = 0.07f;
        float[] half = OpeningBuilder.casingMembers(lone, 0.14f, 1f);
        check(half.length == 2 * each && half[each + 1] == 1f, "half of a pair's casing is one post and a head running on to the square's end");
    }

    /**
     * 11560,7029 west (fixtures_doors_frames_01_8 in walls_interior_house_04_42): the post's side at the doorway's cut ran
     * back into the wall, into the reveal's plane, and the skirting's front stood as far out as the post's. Both fought
     * from any distance, a line down the post's side and a block at its foot.
     */
    private static void casingSharesNoPlaneWithTheRevealOrTheSkirting() {
        Windows.Shape casing = new Windows.Shape();
        casing.start = -0.046875f;
        casing.startWide = 0.140625f;
        casing.end = 0.9375f;
        casing.endWide = 0.09375f;
        casing.head = 0.8489583f;
        casing.headHigh = 0.067708336f;
        int each = OpeningBuilder.CASING_MEMBER, atCut = OpeningBuilder.CASING_MEMBER - 1;
        float[] cut = OpeningBuilder.casingMembers(casing, 0.09375f, 0.84375f);
        check(cut[atCut] == OpeningBuilder.AT_TO && cut[each + atCut] == OpeningBuilder.AT_FROM,
                "each post's side at the cut is the one that stands on the skin, clear of the reveal: " + cut[atCut] + ", " + cut[each + atCut]);
        check(cut[2 * each + atCut] == OpeningBuilder.NO_EDGE, "the head has no side at the cut");
        float[] far = OpeningBuilder.casingMembers(casing, 0.2f, 0.6f);
        check(far[atCut] == OpeningBuilder.NO_EDGE && far[each + atCut] == OpeningBuilder.NO_EDGE, "posts left where they are painted have no side at the cut");
        check(DepthFights.nearestFight(OpeningBuilder.CASING_PROUD - Trims.BOARD, 0, 0) > 48f,
                "a skirting run on behind a post's foot is told from the post's front from within 48 tiles");
        check(DepthFights.nearestFight(OpeningBuilder.JAMB_PROUD, 0, 0) > 48f, "the lining at a post is told from the wall's reveal behind it from within 48 tiles");
    }

    private static void leafIsCutBackToTheDoorwaysCut() {
        // Louisville, 12785,1664: fixtures_doors_01_5 measured from 0.0625 to 0.84375 in walls_exterior_house_01_15, cut from 0.09375 to 0.84375.
        Windows.Shape measured = leaf(0.0625f, 0.84375f);
        Windows.Shape cut = OpeningBuilder.withinCut(measured, 0.09375f, 0.84375f);
        check(cut.start == 0.09375f && cut.end == 0.84375f, "a leaf running a pixel on into the wall stops at the cut");
        check(measured.start == 0.0625f, "the measured leaf, every square's that shows its sprite, is left as it was");
        Windows.Shape inside = leaf(0.125f, 0.8f);
        check(OpeningBuilder.withinCut(inside, 0.09375f, 0.84375f) == inside, "a leaf inside its cut is left as it is: the jambs fill beside it");
        Windows.Shape pair = leaf(0.1875f, 1f);
        Windows.Shape half = OpeningBuilder.withinCut(pair, 0.234375f, 1f);
        check(half.start == 0.234375f && half.end == 1f, "one leaf of a pair stops at the cut at its own end and runs on to the other leaf");
        check(OpeningBuilder.withinCut(leaf(-0.05f, 0.9f), 0f, 0.9f).start == -0.05f, "where the cut runs to the square's edge the doorway goes on, and the leaf with it");
        check(OpeningBuilder.withinCut(inside, -1f, 0f) == inside, "with no doorway measured nothing is cut");
        Windows.Shape stiled = leaf(0.05f, 0.9f);
        stiled.startWide = 0.1f;
        stiled.endWide = 0.02f;
        stiled.panes = new float[] {0.05f, 0.5f, 0.4f, 0.7f, 1f, 0.86f, 0.9f, 0.4f, 0.7f, 1f};
        Windows.Shape trimmed = OpeningBuilder.withinCut(stiled, 0.1f, 0.85f);
        check(Math.abs(trimmed.startWide - 0.05f) < 1e-6f && trimmed.endWide == 0f, "a stile loses what of it ran into the wall");
        check(trimmed.panes.length == 5 && trimmed.panes[0] == 0.1f && trimmed.panes[1] == 0.5f, "a pane is kept within the cut, and one left with no width goes");
    }

    /** A door's jamb is drawn owning no thing between its leaf's pieces (OpeningBuilder.addBuiltDoor), so the line round the door goes round the leaf. */
    private static void whatIsDrawnBetweenOwningNoThingIsNoPartOfTheThing() {
        SpriteGeometry.MeshSet set = new SpriteGeometry.MeshSet();
        SpriteGeometry.Mesh wood = LightSiteTests.constant(set.get(5));
        int vertex = SpriteGeometry.VERTEX_FLOATS;
        set.own(7L);
        wood.add(0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);
        long thing = set.owner();
        set.own(Outlines.NONE);
        wood.add(1f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);
        set.own(thing);
        wood.add(2f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);
        set.own(Outlines.NONE);
        check(thing == 7L, "the set says which thing is being drawn");
        check(wood.spans == 2 && wood.spanKey[0] == 7L && wood.spanFrom[0] == 0 && wood.spanTo[0] == vertex
                && wood.spanKey[1] == 7L && wood.spanFrom[1] == 2 * vertex && wood.spanTo[1] == 3 * vertex, "the thing owns what was drawn before and after, and not what was drawn between");
    }

    private static void openGarageDoorLiesInTheGarageWithItsTracksAtTheJambs() {
        float[] way = new float[2];
        for (float[] edge : new float[][] {{1f, 0f}, {0f, 1f}}) {
            float da = edge[0], db = edge[1];
            for (boolean own : new boolean[] {true, false}) {
                int turns = WallBuilder.raisedTurns(da, db, own);
                Models.turn(turns, 0f, -1f, way);
                float into = own ? 1f : -1f;
                check(way[0] == into * db && way[1] == into * da,
                        "a garage door raised from the edge along " + da + ", " + db + " runs " + (own ? "into its own square" : "across the edge"));
                check(WallBuilder.raisedLeaf(turns, da, db, false, false).equals("garage_leaf")
                        && WallBuilder.raisedLeaf(turns, da, db, true, true).equals("garage_leaf"), "a piece with a jamb at neither end, or both, has no track");
                for (boolean atEnd : new boolean[] {true, false}) {
                    String leaf = WallBuilder.raisedLeaf(turns, da, db, !atEnd, atEnd);
                    Models.turn(turns, leaf.equals("garage_leaf_track") ? 1f : -1f, 0f, way);
                    float along = way[0] * da + way[1] * db;
                    check(!leaf.equals("garage_leaf") && along == (atEnd ? 1f : -1f),
                            "the track of a piece with its jamb at the " + (atEnd ? "end" : "start") + " of the edge along " + da + ", " + db + " is on the jamb's side");
                }
            }
        }
    }

    private static Doors.Shape door(float start, float end, float foot, float head, int kind, boolean handleAtEnd, float red, float[] panes) {
        Windows.Shape leaf = new Windows.Shape();
        leaf.start = start;
        leaf.end = end;
        leaf.foot = foot;
        leaf.head = head;
        leaf.startWide = 0.05f;
        leaf.endWide = 0.1f;
        leaf.footHigh = 0.1f;
        leaf.headHigh = 0.05f;
        leaf.red = red;
        leaf.green = red;
        leaf.blue = red;
        leaf.panes = panes;
        Doors.Shape shape = new Doors.Shape();
        shape.leaf = leaf;
        shape.kind = kind;
        shape.handleAtEnd = handleAtEnd;
        return shape;
    }

    private static void anOpenDoorIsItsShutLeafOnItsOpenOutline() {
        // The open sprite reads as a flush leaf, pale, from 0.05 to 0.45 along the other edge;
        // the shut one as a dark panelled leaf from 0.1 to 0.9 with a light in its top half.
        Doors.Shape open = door(0.05f, 0.45f, 0f, 0.8f, Doors.FLUSH, false, 0.9f, new float[0]);
        Doors.Shape shut = door(0.1f, 0.9f, 0f, 0.8f, Doors.PANELLED, true, 0.3f, new float[] {0.3f, 0.7f, 0.4f, 0.7f, 1f});
        shut.lever = true;
        Doors.Shape swung = Doors.swung(open, shut);
        check(swung.kind == Doors.PANELLED, "an open door keeps its panels");
        check(swung.leaf.red == 0.3f, "and its colour");
        check(swung.lever, "and its kind of handle");
        check(swung.leaf.start == 0.05f && swung.leaf.end == 0.45f && swung.leaf.foot == 0f && swung.leaf.head == 0.8f, "on the outline it stands at open");
        check(swung.handleAtEnd, "hinged at the doorway's corner, the start of the leaf open, so its handle is at its end");
        float t0 = swung.leaf.panes[0], t1 = swung.leaf.panes[1];
        check(Math.abs(t0 - 0.15f) < 1e-5f && Math.abs(t1 - 0.35f) < 1e-5f, "its light is where it is on the leaf shut, scaled to the leaf open: " + t0 + ".." + t1);
        check(swung.leaf.panes[4] == 1f, "and keeps its glass");
        check(Math.abs(swung.leaf.startWide - 0.025f) < 1e-5f && Math.abs(swung.leaf.endWide - 0.05f) < 1e-5f, "its stiles are scaled as the leaf is");
    }

    private static void aShutLeafReadWithItsHandleAtItsStartIsTurnedEndForEnd() {
        Doors.Shape open = door(0.05f, 0.45f, 0f, 0.8f, Doors.FLUSH, true, 0.9f, new float[0]);
        Doors.Shape shut = door(0.1f, 0.9f, 0f, 0.8f, Doors.PANELLED, false, 0.3f, new float[] {0.2f, 0.4f, 0.4f, 0.7f, 1f});
        Doors.Shape swung = Doors.swung(open, shut);
        // The light was 0.1..0.3 of the leaf from its start, beside its handle: open, the handle is at the end.
        float t0 = swung.leaf.panes[0], t1 = swung.leaf.panes[1];
        check(Math.abs(t0 - 0.3f) < 1e-5f && Math.abs(t1 - 0.4f) < 1e-5f, "a light beside the handle stays beside it: " + t0 + ".." + t1);
        check(Math.abs(swung.leaf.startWide - 0.05f) < 1e-5f && Math.abs(swung.leaf.endWide - 0.025f) < 1e-5f, "and the stiles change ends with it");
    }

    /** A leaf 40 dots across and 60 high, all of brightness 0.8, with a dark handle drawn in it and a faint panel line. */
    private static float[] leafWith(int[] handle) {
        int across = 40, high = 60;
        float[] bright = new float[across * high];
        java.util.Arrays.fill(bright, 0.8f);
        for (int row = 0; row < high; row++) {
            bright[row * across + 30] = 0.74f;
        }
        for (int at = 0; at < handle.length; at += 4) {
            for (int row = handle[at + 2]; row < handle[at + 3]; row++) {
                for (int column = handle[at]; column < handle[at + 1]; column++) {
                    bright[row * across + column] = 0.3f;
                }
            }
        }
        return bright;
    }

    /** Whether the handle drawn in leafWith(drawn) reads as a lever, looked for in columns 28..40 of rows 20..40 and reaching to column 20. */
    private static boolean leverIn(int[] drawn) {
        return Doors.isLever(Doors.handle(leafWith(drawn), 40, 20, 40, 28, 40, 20, 40, 0.8f));
    }

    private static void aLongHandleIsALeverAndARoundOneAKnob() {
        check(leverIn(new int[] {29, 37, 30, 33}), "an arm 8 dots long and 3 high is a lever");
        check(leverIn(new int[] {35, 38, 24, 36, 29, 37, 30, 32}), "an arm on a plate standing up the leaf is a lever");
        check(leverIn(new int[] {22, 31, 30, 33}), "an arm reaching on past where the handle is looked for is a lever, and not cut short there");
        check(!leverIn(new int[] {33, 37, 28, 33}), "a handle 4 across and 5 high is a knob");
        check(!leverIn(new int[] {34, 36, 29, 32}), "and so is a small one");
        check(!Doors.isLever(Doors.handle(leafWith(new int[0]), 40, 20, 40, 28, 40, 20, 40, 0.8f)), "a leaf with no handle drawn has no lever");
    }

    private static void aLeversArmRunsFromItsNeckTowardsTheHinges() {
        for (boolean handleAtEnd : new boolean[] {false, true}) {
            float knob = handleAtEnd ? 0.8f : 0.2f;
            float[] arm = OpeningBuilder.lever(knob, handleAtEnd);
            float hinge = handleAtEnd ? 0.1f : 0.9f;
            check(arm[0] <= knob && knob <= arm[1], "the arm starts at its neck");
            check(Math.abs((handleAtEnd ? arm[0] : arm[1]) - knob) > Math.abs((handleAtEnd ? arm[1] : arm[0]) - knob), "and runs towards the hinges at " + hinge);
            check(arm[2] < OpeningBuilder.KNOB_HIGH && OpeningBuilder.KNOB_HIGH < arm[3], "at the knob's height");
        }
    }

    private static void aBarBetweenLightsRunsOnToTheRails() {
        // Two lights side by side read from the first whole dot under a head rail 0.05 high and
        // over a foot rail 0.4 high, with an upright bar between them and a rail across under.
        Doors.Shape door = door(0.1f, 0.9f, 0f, 0.8f, Doors.PANELLED, true, 0.5f, new float[] {0.2f, 0.48f, 0.405f, 0.746f, 1f, 0.52f, 0.8f, 0.405f, 0.746f, 1f});
        Windows.Shape leaf = door.leaf;
        leaf.headHigh = 0.05f;
        leaf.footHigh = 0.4f;
        leaf.bars = new float[] {0.48f, 0.52f, 0.405f, 0.746f, 0.2f, 0.8f, 0.6f, 0.62f};
        float[] bars = OpeningBuilder.barsToRails(leaf);
        check(Math.abs(bars[3] - 0.75f) < 1e-6f, "the bar between the lights meets the head rail: " + bars[3]);
        check(Math.abs(bars[2] - 0.4f) < 1e-6f, "and the foot rail: " + bars[2]);
        check(bars[4] == 0.2f && bars[5] == 0.8f && bars[6] == 0.6f && bars[7] == 0.62f, "a bar across the lights stays where it is");
        check(leaf.bars[3] == 0.746f, "and the leaf's own bars are left as read");
    }

    private static void aBeadRunsRoundALightHalfOverItsGlass() {
        float t0 = 0.3f, t1 = 0.6f, h0 = 0.5f, h1 = 0.75f;
        float[] bead = OpeningBuilder.bead(t0, t1, h0, h1, 0.1f, 0.9f, 0f, 0.8f);
        check(bead.length == 16, "four strips");
        float[][] edges = {{h0, 0}, {h1, 0}, {t0, 1}, {t1, 1}};
        for (int strip = 0; strip < 4; strip++) {
            int at = strip * 4;
            float lineAt = edges[strip][0];
            boolean upright = edges[strip][1] == 1;
            float from = upright ? bead[at] : bead[at + 2], to = upright ? bead[at + 1] : bead[at + 3];
            check(from < lineAt && lineAt < to && Math.abs((lineAt - from) - (to - lineAt)) < 1e-5f, "strip " + strip + " lies half each side of the light's edge");
        }
        check(bead[0] < t0 && bead[1] > t1, "the head runs the whole light");
        check(bead[10] > h0 && bead[11] < h1, "the sides stand between head and foot");
        float[] edge = OpeningBuilder.bead(0.1f, 0.9f, 0.5f, 0.8f, 0.1f, 0.9f, 0f, 0.8f);
        for (int at = 0; at < edge.length; at += 4) {
            check(edge[at] >= 0.1f && edge[at + 1] <= 0.9f && edge[at + 3] <= 0.8f, "a light with no stile or rail beside it keeps its bead on the leaf: strip " + at / 4);
            check(edge[at] < edge[at + 1] && edge[at + 2] < edge[at + 3], "and every strip of it some length and height: strip " + at / 4);
        }
    }

    /** A frame at the game's doubled scale, 128 by 256 dots, its square's (0, 0) corner at (64, 192), the whole frame the sprite. */
    private static SpriteGeometry.Frame doubledFrame() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.width = 128f;
        frame.height = 256f;
        return frame;
    }

    /**
     * Paints into a doubled frame's sheet (red, green, blue and cover) a brown leaf on the north or west edge of its
     * square, or with far the south or east, standing depth of a square into the square from it, from along0 to along1
     * of the edge and from foot to head of a level.
     */
    private static void paintLeaf(java.nio.ByteBuffer sheet, boolean north, boolean far, float depth, float along0, float along1, float foot, float head) {
        float other = far ? 1f - depth : depth;
        for (float along = along0; along < along1; along += 1f / 512f) {
            for (float up = foot; up < head; up += 1f / 1024f) {
                float a = north ? along : other, b = north ? other : along;
                int u = (int) Math.floor((a - b) * SpriteGeometry.PIXELS_ACROSS_PER_TILE * 2f + 64f);
                int v = (int) Math.floor(((a + b) * SpriteGeometry.PIXELS_DOWN_PER_TILE - up * SpriteGeometry.PIXELS_PER_LEVEL) * 2f + 192f);
                if (u >= 0 && u < 128 && v >= 0 && v < 256) {
                    int at = (v * 128 + u) * 4;
                    sheet.put(at, (byte) 120).put(at + 1, (byte) 80).put(at + 2, (byte) 40).put(at + 3, (byte) 255);
                }
            }
        }
    }

    /**
     * The owner found the odd door drawn flat as its sprite, a wooden panelled door in an ordinary house among them. A
     * sprite was read once, on the first edge it was asked for: asked for on an edge it does not paint, as a build that
     * reads a door open and its sprite still shut does, it was no leaf there and stayed no leaf on its own edge. Each
     * edge is read apart now.
     */
    private static void aDoorAskedForOnAnEdgeItDoesNotPaintIsStillReadOnItsOwn() {
        Texture texture = new Texture(5101, 128, 256).named("fixtures_doors_01_test");
        java.nio.ByteBuffer sheet = java.nio.ByteBuffer.allocate(128 * 256 * 4);
        paintLeaf(sheet, true, false, 0f, 0.1f, 0.9f, 0f, 0.8f);
        SpriteGeometry.Frame frame = doubledFrame();
        check(Doors.of(texture, frame, false) == null && Doors.of(texture, frame, true) == null, "nothing is known of a door before a frame of it is read");
        Doors.read(texture, sheet, 128, 0, 0, 128, 256);
        check(Doors.of(texture, frame, false) == null && !Doors.unread(texture, false, false), "the door is read as no leaf on the west edge, which it does not paint");
        Doors.Shape shape = Doors.of(texture, frame, true);
        check(shape != null, "and is a leaf on its own north edge all the same");
        check(shape != null && Math.abs(shape.leaf.start - 0.1f) < 0.03f && Math.abs(shape.leaf.end - 0.9f) < 0.03f && shape.leaf.foot < 0.02f && Math.abs(shape.leaf.head - 0.8f) < 0.02f,
                "where it is painted: " + (shape == null ? "none" : shape.leaf.start + ".." + shape.leaf.end + " up " + shape.leaf.foot + ".." + shape.leaf.head));
        check(Doors.known(texture, true) == shape && Doors.known(texture, false) == null, "and known on its own edge only");
        check(Doors.leafEdges(texture).equals("north"), "a log line says where it is a leaf: " + Doors.leafEdges(texture));
    }

    /**
     * The game paints some leaves open standing clear of the wall they are back against: into the square from the
     * north edge (fixtures_doors_02_42), whose picture is the leaf on the edge slid back along it and sunk under the
     * floor, and from the south edge (fixtures_doors_02_55), slid on and raised. Each is stood on its floor where it is
     * painted along its edge, and the one on the south edge is no leaf on the north, where all that is read of it is
     * past the square's end.
     */
    private static void anOpenLeafStandingIntoItsSquareIsStoodOnItsFloor() {
        SpriteGeometry.Frame frame = doubledFrame();
        for (boolean far : new boolean[] {false, true}) {
            Texture texture = new Texture(far ? 5103 : 5102, 128, 256).named(far ? "fixtures_doors_02_test_far" : "fixtures_doors_02_test_near");
            java.nio.ByteBuffer sheet = java.nio.ByteBuffer.allocate(128 * 256 * 4);
            paintLeaf(sheet, true, far, 0.1f, 0.15f, 0.85f, 0f, 0.8f);
            Doors.of(texture, frame, true, false, true);
            Doors.of(texture, frame, true, true, true);
            Doors.read(texture, sheet, 128, 0, 0, 128, 256);
            Doors.Shape shape = Doors.of(texture, frame, true, far, true), other = Doors.of(texture, frame, true, !far, true);
            String edge = far ? "south" : "north";
            check(shape != null, "a leaf standing into its square from the " + edge + " edge is read on that edge");
            check(shape != null && Math.abs(shape.leaf.start - 0.15f) < 0.04f && Math.abs(shape.leaf.end - 0.85f) < 0.04f,
                    "from where along the edge it is painted: " + (shape == null ? "none" : shape.leaf.start + ".." + shape.leaf.end));
            check(shape != null && Math.abs(shape.leaf.foot) < 0.03f && Math.abs(shape.leaf.head - 0.8f) < 0.03f,
                    "on its floor and as high as it is painted: " + (shape == null ? "none" : shape.leaf.foot + ".." + shape.leaf.head));
            if (far) {
                check(other == null, "and the leaf on the south edge is no leaf on the north");
            }
        }
    }

    /** A leaf frame all over from column c0 to before c1 and row r0 to before r1 of a doubled frame laid flat, its first and last rows every other dot. */
    private static byte[] raggedLeaf(int c0, int c1, int r0, int r1) {
        int across = Windows.across(2f);
        byte[] dots = new byte[across * Windows.high(2f)];
        for (int row = r0; row < r1; row++) {
            for (int column = c0; column < c1; column++) {
                boolean ragged = row == r0 || row == r1 - 1;
                dots[row * across + column] = !ragged || (column - c0) % 2 == 0 ? Windows.FRAME : Windows.EMPTY;
            }
        }
        return dots;
    }

    /**
     * A toilet stall's door (fixtures_bathroom_01_48) is wood from rows 54 to 167 of 192, half lines at its top and
     * bottom: read as head down to the line before its foot's last, it was no leaf and stayed its sprite, open or shut.
     * A band as ragged and under half a level high, across the head of a garage's opening, is still no leaf.
     */
    private static void aToiletStallsLeafRaggedTopAndBottomIsALeaf() {
        int across = Windows.across(2f), high = Windows.high(2f);
        Windows.Shape stall = Windows.measure(raggedLeaf(2, 48, 54, 168), across, high, true);
        check(stall != null, "a stall's door with ragged lines at its top and bottom is a leaf");
        check(stall != null && stall.panes.length == 0 && stall.bars.length == 4, "of wood all over, with no light");
        check(stall != null && Math.abs(stall.start - 2f / across) < 1e-5f && Math.abs(stall.end - 48f / across) < 1e-5f, "from its first column to its last");
        check(Windows.measure(raggedLeaf(2, 62, 0, 40), across, high, true) == null, "a band across a doorway's head, under half a level high, is no leaf");
    }

    /** A shack's door (carpentry_01_48) is boards with ragged ends, a corner of it bare, and wood all over all the same: it was no leaf and stayed its sprite. */
    private static void aShacksDoorWithABareCornerIsALeaf() {
        int across = Windows.across(2f), high = Windows.high(2f);
        byte[] dots = new byte[across * high];
        for (int row = 40; row < high; row++) {
            for (int column = 8; column < 56; column++) {
                boolean bare = row < 50 && column - 8 < 2 * (50 - row);
                dots[row * across + column] = bare ? Windows.EMPTY : Windows.FRAME;
            }
        }
        check(Windows.measure(dots, across, high, false) == null, "a frame with a corner bare that far in is no window");
        Windows.Shape shack = Windows.measure(dots, across, high, true);
        check(shack != null && shack.panes.length == 0, "and is a leaf of wood all over as a door");
        check(shack != null && Math.abs(shack.start - 8f / across) < 1e-5f && Math.abs(shack.end - 56f / across) < 1e-5f && Math.abs(shack.foot) < 1e-5f,
                "across its boards, from the floor: " + (shack == null ? "none" : shack.start + ".." + shack.end + " up " + shack.foot));
    }

    private static void aDoorDrawnAsItsSpriteIsToldOnceForEachReason() {
        java.util.Set<String> told = TestMap.read(null, Doors.class, "drawnAsSpriteTold");
        Texture texture = new Texture(5104, 128, 256).named("fixtures_doors_01_told");
        int before = told.size();
        Doors.drawnAsSprite(texture, 10, 20, 0, true, "its sprite is no leaf on that edge");
        Doors.drawnAsSprite(texture, 11, 20, 0, true, "its sprite is no leaf on that edge");
        check(told.size() == before + 1, "a door drawn as its sprite is told once for a sprite and reason, wherever it stands");
        Doors.drawnAsSprite(texture, 10, 20, 0, false, "open, its sprite is no leaf on that edge nor on the east edge");
        check(told.size() == before + 2, "and again for another reason");
    }
}
