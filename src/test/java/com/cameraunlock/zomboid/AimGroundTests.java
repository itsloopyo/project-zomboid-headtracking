package com.cameraunlock.zomboid;

/** Tests of where the aim meets the ground the picture draws, which hills raise and lower. */
final class AimGroundTests {
    private AimGroundTests() {}

    static void run() {
        Checks.run("onLevelGroundTheAimMeetsItWhereTheLevelPlaneIs", AimGroundTests::onLevelGroundTheAimMeetsItWhereTheLevelPlaneIs);
        Checks.run("aChestInADipIsReachedBeforeTheGround", AimGroundTests::aChestInADipIsReachedBeforeTheGround);
        Checks.run("aRiseAheadStopsALevelAim", AimGroundTests::aRiseAheadStopsALevelAim);
        Checks.run("anAimThatMeetsNoGroundWithinReachIsUnstopped", AimGroundTests::anAimThatMeetsNoGroundWithinReachIsUnstopped);
        Checks.run("aFloorOverTheFlatMapIsRaisedByTheGroundUnderIt", AimGroundTests::aFloorOverTheFlatMapIsRaisedByTheGroundUnderIt);
        Checks.run("anEyeUnderTheGroundMeetsTheGroundItComesDownOnto", AimGroundTests::anEyeUnderTheGroundMeetsTheGroundItComesDownOnto);
        Checks.run("aStripOfPavementOverTheAimStopsIt", AimGroundTests::aStripOfPavementOverTheAimStopsIt);
        Checks.run("aDragCursorIsPutOnTheSquareInFrontOfAWall", AimGroundTests::aDragCursorIsPutOnTheSquareInFrontOfAWall);
        Checks.run("aDragCursorIsPutWhereTheLineMeetsTheFloor", AimGroundTests::aDragCursorIsPutWhereTheLineMeetsTheFloor);
        Checks.run("aDragCursorOnALineThatMeetsNothingIsPutAtItsReach", AimGroundTests::aDragCursorOnALineThatMeetsNothingIsPutAtItsReach);
        Checks.run("aDragCursorIsTheGamesOwnOutOfFirstPerson", AimGroundTests::aDragCursorIsTheGamesOwnOutOfFirstPerson);
        Checks.run("aDragCursorInFirstPersonIsPutWhereItIsAimed", AimGroundTests::aDragCursorInFirstPersonIsPutWhereItIsAimed);
        Checks.run("aDragCursorWithNoLineIsPutNowhere", AimGroundTests::aDragCursorWithNoLineIsPutNowhere);
        Checks.run("onlyABarricadeIsPutByAWindow", AimGroundTests::onlyABarricadeIsPutByAWindow);
        Checks.run("aDragShowsTheEdgeAPieceStandsOnBeforeTheWayItFaces", AimGroundTests::aDragShowsTheEdgeAPieceStandsOnBeforeTheWayItFaces);
        Checks.run("aDragsArrowPointsTheWayThePieceFaces", AimGroundTests::aDragsArrowPointsTheWayThePieceFaces);
        Checks.run("aDragsFrameStandsOnTheEdgeThePieceIsPutOn", AimGroundTests::aDragsFrameStandsOnTheEdgeThePieceIsPutOn);
    }

    private static void aDragShowsTheEdgeAPieceStandsOnBeforeTheWayItFaces() {
        Checks.check(DragAim.facing(true, false, "S") == DragAim.Facing.NORTH_EDGE, "a piece on the north edge shows that edge, whatever its Facing");
        Checks.check(DragAim.facing(false, true, null) == DragAim.Facing.WEST_EDGE, "and one on the west edge that one");
        Checks.check(DragAim.facing(false, false, "N") == DragAim.Facing.NORTH, "a chair facing N shows north");
        Checks.check(DragAim.facing(false, false, "E") == DragAim.Facing.EAST, "E east");
        Checks.check(DragAim.facing(false, false, "S") == DragAim.Facing.SOUTH, "S south");
        Checks.check(DragAim.facing(false, false, "W") == DragAim.Facing.WEST, "W west");
        Checks.check(DragAim.facing(false, false, null) == DragAim.Facing.NONE, "a piece with no Facing shows none");
        Checks.check(DragAim.facing(false, false, "NW") == DragAim.Facing.NONE, "nor does one turned between two ways");
    }

    private static void aDragsArrowPointsTheWayThePieceFaces() {
        float[] lines = new float[6 * DragAim.MOST_FACING_LINES];
        Checks.check(DragAim.facingLines(DragAim.Facing.NONE, 10, 20, lines) == 0, "a piece that faces no way has no arrow");
        float[][] ways = {{0f, -1f}, {1f, 0f}, {0f, 1f}, {-1f, 0f}};
        DragAim.Facing[] facings = {DragAim.Facing.NORTH, DragAim.Facing.EAST, DragAim.Facing.SOUTH, DragAim.Facing.WEST};
        for (int f = 0; f < facings.length; f++) {
            int count = DragAim.facingLines(facings[f], 10, 20, lines);
            Checks.check(count == 2, facings[f] + " is an arrow of two lines: " + count);
            for (int line = 0; line < count; line++) {
                int at = line * 6;
                float footAlong = (lines[at] - 10.5f) * ways[f][0] + (lines[at + 1] - 20.5f) * ways[f][1];
                float tipAlong = (lines[at + 3] - 10.5f) * ways[f][0] + (lines[at + 4] - 20.5f) * ways[f][1];
                Checks.check(tipAlong > footAlong + 0.2f, facings[f] + "'s arrow runs from the middle out the way it faces: " + footAlong + " to " + tipAlong);
                Checks.check(lines[at + 2] == 0f && lines[at + 5] == 0f, "on the floor");
                for (int end = 0; end < 2; end++) {
                    float x = lines[at + end * 3], y = lines[at + end * 3 + 1];
                    Checks.check(x > 10f && x < 11f && y > 20f && y < 21f, facings[f] + "'s arrow is inside its square: " + x + ", " + y);
                }
            }
            float across0 = (lines[0] - 10.5f) * -ways[f][1] + (lines[1] - 20.5f) * ways[f][0];
            float across1 = (lines[6] - 10.5f) * -ways[f][1] + (lines[7] - 20.5f) * ways[f][0];
            Checks.check(Math.abs(across0 + across1) < 1e-5f && Math.abs(across0) > 0.2f, facings[f] + "'s wings are either side of it: " + across0 + ", " + across1);
        }
    }

    private static void aDragsFrameStandsOnTheEdgeThePieceIsPutOn() {
        float[] lines = new float[6 * DragAim.MOST_FACING_LINES];
        int count = DragAim.facingLines(DragAim.Facing.NORTH_EDGE, 10, 20, lines);
        Checks.check(count == 3, "an edge's frame is two posts and a top: " + count);
        float top = 0f;
        for (int i = 0; i < count * 6; i += 3) {
            Checks.check(lines[i + 1] == 20f && lines[i] >= 10f && lines[i] <= 11f, "a piece on the north edge stands on y 20 from x 10 to 11: " + lines[i] + ", " + lines[i + 1]);
            top = Math.max(top, lines[i + 2]);
        }
        Checks.check(top == DragAim.EDGE_FRAME_HIGH, "its frame stands up off the floor: " + top);
        count = DragAim.facingLines(DragAim.Facing.WEST_EDGE, 10, 20, lines);
        for (int i = 0; i < count * 6; i += 3) {
            Checks.check(lines[i] == 10f && lines[i + 1] >= 20f && lines[i + 1] <= 21f, "a piece on the west edge stands on x 10 from y 20 to 21: " + lines[i] + ", " + lines[i + 1]);
        }
    }

    private static void onlyABarricadeIsPutByAWindow() {
        Checks.check(DragAim.barricades("BuildRecipeCode.barricade.OnIsValidPlanks"), "planks");
        Checks.check(DragAim.barricades("BuildRecipeCode.barricade.OnIsValid"), "metal sheets and bars");
        Checks.check(!DragAim.barricades("BuildRecipeCode.windowGlass.OnIsValid"), "not glass put in a frame");
        Checks.check(!DragAim.barricades(null), "not a build with no test of its own");
    }

    private static ViewCamera.Pose aim(float up, float toX, float toUp) {
        ViewCamera.Pose pose = new ViewCamera.Pose();
        float length = (float) Math.sqrt(toX * toX + (toUp - up) * (toUp - up));
        pose.eyeUp = up;
        pose.forwardX = toX / length;
        pose.forwardUp = (toUp - up) / length;
        return pose;
    }

    /** Along the aim to the place at x, for an aim that runs along x. */
    private static float along(ViewCamera.Pose aim, float x) {
        return x / aim.forwardX;
    }

    private static void onLevelGroundTheAimMeetsItWhereTheLevelPlaneIs() {
        ViewCamera.Pose aim = aim(0.7f + 1.6f, 10f, 0.7f);
        float met = WorldQuery.toFloor(aim, (x, y) -> 0.7f, 0f, 0f, 30f);
        Checks.check(Math.abs(met - along(aim, 10f)) < 0.01f, "on ground 0.7 up everywhere the aim at its foot 10 tiles off meets it there: " + met);
    }

    /**
     * The ground falls 1.5 tiles over 15. A chest a tile over the ground 12 tiles off is under
     * the eye's own footing: a level plane through the footing is met 10.7 tiles off.
     */
    private static void aChestInADipIsReachedBeforeTheGround() {
        WorldQuery.Ground dip = (x, y) -> -0.1f * Math.max(x, 0f);
        ViewCamera.Pose aim = aim(1.6f, 12f, -1.2f + 1f);
        float met = WorldQuery.toFloor(aim, dip, 0f, 0f, 60f);
        // The aim falls 0.15 a tile and the ground 0.1: they meet 32 tiles off.
        Checks.check(Math.abs(met - along(aim, 32f)) < 0.01f, "the aim at a chest 12 tiles off in a dip meets the ground 32 tiles off: " + met + " against " + along(aim, 32f));
    }

    private static void aRiseAheadStopsALevelAim() {
        WorldQuery.Ground rise = (x, y) -> Math.clamp((x - 5f) * 0.5f, 0f, 3f);
        ViewCamera.Pose aim = aim(1.6f, 1f, 1.6f);
        float met = WorldQuery.toFloor(aim, rise, 0f, 0f, 30f);
        Checks.check(Math.abs(met - 8.2f) < 0.01f, "a level aim 1.6 up meets ground rising from 5 tiles off at half a tile a tile where it is 1.6 up: " + met);
    }

    private static void anAimThatMeetsNoGroundWithinReachIsUnstopped() {
        ViewCamera.Pose aim = aim(1.6f, 40f, 0.5f);
        float met = WorldQuery.toFloor(aim, (x, y) -> 0f, 0f, 0f, 30f);
        Checks.check(met == Float.POSITIVE_INFINITY, "an aim that reaches the ground 40 tiles off is not stopped within 30: " + met);
    }

    private static void aFloorOverTheFlatMapIsRaisedByTheGroundUnderIt() {
        float floor = ViewCamera.LEVEL_HEIGHT;
        ViewCamera.Pose aim = aim(floor + 0.4f + 1.6f, 10f, floor + 0.4f);
        float met = WorldQuery.toFloor(aim, (x, y) -> 0.4f, floor, 0f, 30f);
        Checks.check(Math.abs(met - along(aim, 10f)) < 0.01f, "a floor a level up on ground 0.4 up is met 0.4 over the level: " + met);
    }

    /** The ground rises to 2 tiles round the eye and falls to 0 a tile off; an upward aim from 1.6 leaves it and meets nothing. */
    private static void anEyeUnderTheGroundMeetsTheGroundItComesDownOnto() {
        WorldQuery.Ground mound = (x, y) -> x < 1f ? 2f : 0f;
        Checks.check(WorldQuery.toFloor(aim(1.6f, 10f, 3f), mound, 0f, 0f, 30f) == Float.POSITIVE_INFINITY,
                "an upward aim from under the ground drawn where the eye is meets no floor");
        ViewCamera.Pose down = aim(1.6f, 10f, 0f);
        float met = WorldQuery.toFloor(down, mound, 0f, 0f, 30f);
        Checks.check(Math.abs(met - along(down, 10f)) < 0.01f, "a downward aim from under it meets the ground it comes down onto, 10 tiles off: " + met);
    }

    /** Pavement 0.11 over the road from 5.03 to 5.2 tiles off, under an aim falling 0.02 a tile that is 0.1 over the road there. */
    private static void aStripOfPavementOverTheAimStopsIt() {
        WorldQuery.Ground kerb = (x, y) -> x >= 5.03f && x < 5.2f ? 0.11f : 0f;
        ViewCamera.Pose aim = aim(0.2f, 10f, 0f);
        float met = WorldQuery.toFloor(aim, kerb, 0f, 0f, 30f);
        Checks.check(met < along(aim, 5.2f), "the aim stops on the strip of pavement: " + met);
    }

    /** A level line along x meets a wall on the west edge of square 5, 4.5 tiles from an eye at 0.5. */
    private static void aDragCursorIsPutOnTheSquareInFrontOfAWall() {
        // Far out on the map a float is good to about a thousandth of a tile, and a shallow line
        // that meets a wall on the west edge of square 10005 can come out either side of it.
        for (float at : new float[] {10004.9995f, 10005f, 10005.001f}) {
            float x = DragAim.offWall(at, 0.2f);
            Checks.check((int) Math.floor(x) == 10004, "a drag on a line going east that meets the wall at " + at + " is on square 10004: " + x);
            x = DragAim.offWall(at, -0.2f);
            Checks.check((int) Math.floor(x) == 10005, "and one going west that meets it is on square 10005: " + x);
        }
    }

    private static void aDragCursorIsPutWhereTheLineMeetsTheFloor() {
        Checks.check(DragAim.along(3.2f) == 3.2f, "a drag on a line that meets the floor 3.2 tiles off is put there: " + DragAim.along(3.2f));
    }

    private static void aDragCursorOnALineThatMeetsNothingIsPutAtItsReach() {
        Checks.check(DragAim.along(Float.POSITIVE_INFINITY) == DragAim.REACH, "a drag on a line that meets nothing is put at its reach");
        Checks.check(DragAim.along(DragAim.REACH + 5f) == DragAim.REACH, "and so is one that meets a wall past its reach");
    }

    private static void aDragCursorIsTheGamesOwnOutOfFirstPerson() {
        DragAim.Aimed aimed = new DragAim.Aimed();
        Checks.check(aimed.put(false) == DragAim.Put.STOCK && !aimed.onTheFloor(false), "with no drag aimed and first person off the game's own place is kept");
        aimed.aim(3.25f, 4.75f);
        Checks.check(aimed.put(false) == DragAim.Put.STOCK && !aimed.onTheFloor(false), "and with first person off it is kept though a place was aimed at");
        aimed.forget();
        Checks.check(!aimed.onTheFloor(true), "with no drag up the game's own place on the floor is kept in first person");
    }

    private static void aDragCursorInFirstPersonIsPutWhereItIsAimed() {
        DragAim.Aimed aimed = new DragAim.Aimed();
        aimed.aim(3.25f, 4.75f);
        Checks.check(aimed.put(true) == DragAim.Put.AIMED, "a drag aimed in first person is on the square aimed at: " + aimed.put(true));
        Checks.check(aimed.onTheFloor(true) && aimed.x() == 3.25f && aimed.y() == 4.75f, "and its place on the floor is where it was aimed");
    }

    private static void aDragCursorWithNoLineIsPutNowhere() {
        DragAim.Aimed aimed = new DragAim.Aimed();
        Checks.check(aimed.put(true) == DragAim.Put.NOWHERE, "a drag in first person not yet aimed is on no square, not the one under the hidden mouse");
        aimed.aim(3.25f, 4.75f);
        aimed.lose();
        Checks.check(aimed.put(true) == DragAim.Put.NOWHERE, "a drag whose line is lost is on no square");
        Checks.check(aimed.onTheFloor(true) && aimed.x() == 3.25f && aimed.y() == 4.75f, "and its place on the floor stays where it was last aimed");
        aimed.aim(5.5f, 6.5f);
        Checks.check(aimed.put(true) == DragAim.Put.AIMED, "aimed again it is on a square again");
        aimed.forget();
        Checks.check(aimed.put(false) == DragAim.Put.STOCK, "and once first person is given back the game's own is kept");
    }
}
