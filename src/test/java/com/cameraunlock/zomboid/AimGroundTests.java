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
