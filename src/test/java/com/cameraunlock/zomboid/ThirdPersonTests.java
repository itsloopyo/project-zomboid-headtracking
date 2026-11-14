package com.cameraunlock.zomboid;

/** Tests of where ThirdPerson stands its cameras, and of what the head and the walls do to them. */
final class ThirdPersonTests {
    private ThirdPersonTests() {}

    static void run() {
        Checks.run("inTheOpenTheCameraStandsBehindAndOverThePivotLookingTheWayTheMouseDoes", ThirdPersonTests::inTheOpenTheCameraStandsBehindAndOverThePivotLookingTheWayTheMouseDoes);
        Checks.run("aWallBehindShortensTheBoomAtOnceAndItIsLetOutSlowly", ThirdPersonTests::aWallBehindShortensTheBoomAtOnceAndItIsLetOutSlowly);
        Checks.run("theCameraIsKeptUnderTheCeilingAndOverTheFloor", ThirdPersonTests::theCameraIsKeptUnderTheCeilingAndOverTheFloor);
        Checks.run("theHeadTurnsAndMovesThePictureCameraAndNotTheCleanOne", ThirdPersonTests::theHeadTurnsAndMovesThePictureCameraAndNotTheCleanOne);
        Checks.run("theCameraSwingsOutFromTheEyeAndBack", ThirdPersonTests::theCameraSwingsOutFromTheEyeAndBack);
        Checks.run("aTurnCarriedOverKeepsTheAxesSquare", ThirdPersonTests::aTurnCarriedOverKeepsTheAxesSquare);
        Checks.run("thePivotTrailsTheCharacterALittleAndJumpsWithThem", ThirdPersonTests::thePivotTrailsTheCharacterALittleAndJumpsWithThem);
        Checks.run("behindAVehicleTheCameraSwingsBackTheShorterWayAndLooksDown", ThirdPersonTests::behindAVehicleTheCameraSwingsBackTheShorterWayAndLooksDown);
        Checks.run("aSprintTakesTheCameraBackAndStoppingBringsItIn", ThirdPersonTests::aSprintTakesTheCameraBackAndStoppingBringsItIn);
        Checks.run("theHeadTurnIsCarriedOntoACameraLookingAnotherWay", ThirdPersonTests::theHeadTurnIsCarriedOntoACameraLookingAnotherWay);
        Checks.run("overTheShoulderIsToTheRight", ThirdPersonTests::overTheShoulderIsToTheRight);
        Checks.run("aWallKeepsTheCameraWhereItIsAsTheBoomShortens", ThirdPersonTests::aWallKeepsTheCameraWhereItIsAsTheBoomShortens);
        Checks.run("inTheOpenTheBoomKeepsUpWithTheView", ThirdPersonTests::inTheOpenTheBoomKeepsUpWithTheView);
        Checks.run("sittingDownTheCameraGoesOverToTheVehicleOverTime", ThirdPersonTests::sittingDownTheCameraGoesOverToTheVehicleOverTime);
        Checks.run("theWallsAreToldHowHighTheBoomIs", ThirdPersonTests::theWallsAreToldHowHighTheBoomIs);
        Checks.run("underACeilingTheWallsAreToldTheRiseOfTheLineTheCameraIsPutOn", ThirdPersonTests::underACeilingTheWallsAreToldTheRiseOfTheLineTheCameraIsPutOn);
        Checks.run("aCameraAboutTheTopOfAWallIsHeldOutPastTheEave", ThirdPersonTests::aCameraAboutTheTopOfAWallIsHeldOutPastTheEave);
        Checks.run("aBoomPassesADoorwayOnlyUnderTheDoorsHead", ThirdPersonTests::aBoomPassesADoorwayOnlyUnderTheDoorsHead);
        Checks.run("aBoomAsHighAsAnEaveDoesNotGoInUnderTheRoofFromTheOpenAir", ThirdPersonTests::aBoomAsHighAsAnEaveDoesNotGoInUnderTheRoofFromTheOpenAir);
        Checks.run("aBoomOverTheTopOfAWallMeetsTheWallOfTheLevelAbove", ThirdPersonTests::aBoomOverTheTopOfAWallMeetsTheWallOfTheLevelAbove);
        Checks.run("aCameraOutFromUnderAPorchIsNotPushedBackUnderIt", ThirdPersonTests::aCameraOutFromUnderAPorchIsNotPushedBackUnderIt);
        Checks.run("inACrampedPlaceTheCameraComesInOverTheShoulderAndGoesBackOutInTheOpen", ThirdPersonTests::inACrampedPlaceTheCameraComesInOverTheShoulderAndGoesBackOutInTheOpen);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(double a, double b, double within) {
        return Math.abs(a - b) <= within;
    }

    private static final ThirdPerson.Walls OPEN = (x, y, dx, dy, reach, margin, up, rise) -> reach;
    private static final float PIVOT_X = 100.5f, PIVOT_Y = 200.5f, PIVOT_UP = 1.6f;

    /** A rig all the way out, as the key leaves it once the camera has swung out. */
    private static ThirdPerson out() {
        ThirdPerson rig = new ThirdPerson();
        rig.cut(true);
        return rig;
    }

    private static ViewCamera.Pose looking(float yawDegrees, float pitchDegrees) {
        ViewCamera.Pose look = new ViewCamera.Pose();
        ViewCamera.clean(look, PIVOT_X, PIVOT_Y, PIVOT_UP, (float) Math.toRadians(yawDegrees), (float) Math.toRadians(pitchDegrees));
        return look;
    }

    private static void place(ThirdPerson rig, ViewCamera.Pose look, ViewCamera.Pose tracked, ThirdPerson.Walls walls, float floor, float ceiling,
            float seconds, ViewCamera.Pose clean, ViewCamera.Pose camera) {
        rig.camera(look, look, tracked, PIVOT_X, PIVOT_Y, PIVOT_UP, ThirdPerson.BEHIND, ThirdPerson.ABOVE, ThirdPerson.RIGHT, walls, floor, floor, ceiling,
                seconds, clean, camera);
    }

    private static void inTheOpenTheCameraStandsBehindAndOverThePivotLookingTheWayTheMouseDoes() {
        ViewCamera.Pose look = looking(30f, 0f), clean = new ViewCamera.Pose(), camera = new ViewCamera.Pose();
        place(out(), look, look, OPEN, 0f, Float.POSITIVE_INFINITY, 1f / 60f, clean, camera);
        double backX = PIVOT_X - Math.cos(Math.toRadians(30)) * ThirdPerson.BEHIND, backY = PIVOT_Y - Math.sin(Math.toRadians(30)) * ThirdPerson.BEHIND;
        check(near(camera.exactX(), backX, 1e-3) && near(camera.exactY(), backY, 1e-3), "the camera is BEHIND tiles back along the view: ("
                + camera.exactX() + ", " + camera.exactY() + ") against (" + backX + ", " + backY + ")");
        check(near(camera.exactUp(), PIVOT_UP + ThirdPerson.ABOVE, 1e-4), "the camera is ABOVE over the pivot: " + camera.exactUp());
        check(near(camera.forwardX, look.forwardX, 1e-5) && near(camera.forwardY, look.forwardY, 1e-5) && near(camera.forwardUp, look.forwardUp, 1e-5),
                "with no head turn the camera looks the way the mouse does");
        check(near(clean.exactX(), camera.exactX(), 1e-4) && near(clean.exactY(), camera.exactY(), 1e-4) && near(clean.exactUp(), camera.exactUp(), 1e-4),
                "with no head turn or lean the clean camera and the picture's are one");

        // Looking down swings the camera up over the pivot, along the line it looks down.
        ViewCamera.Pose down = looking(0f, -40f);
        place(out(), down, down, OPEN, 0f, Float.POSITIVE_INFINITY, 1f / 60f, clean, camera);
        double up = PIVOT_UP + Math.sin(Math.toRadians(40)) * ThirdPerson.BEHIND + ThirdPerson.ABOVE;
        check(near(camera.exactUp(), up, 1e-3), "looking 40 degrees down the camera is that far up the boom: " + camera.exactUp() + " against " + up);
    }

    private static void inACrampedPlaceTheCameraComesInOverTheShoulderAndGoesBackOutInTheOpen() {
        ThirdPerson rig = out();
        ViewCamera.Pose look = looking(0f, 0f);
        float[] wall = {0.8f};
        float[] asked = new float[1];
        ThirdPerson.Walls walled = (x, y, dx, dy, reach, margin, up, rise) -> {
            asked[0] = reach;
            return Math.max(Math.min(reach, wall[0] - margin), 0f);
        };
        rig.cramp(look, PIVOT_X, PIVOT_Y, PIVOT_UP, walled, 0f, Float.POSITIVE_INFINITY, 1f / 60f);
        check(rig.crampShare() == 1f, "the first measure after a cut is taken at once: a wall 0.8 tiles behind leaves too little boom: " + rig.crampShare());
        check(near(asked[0], ThirdPerson.BEHIND, 1e-4), "the walls are asked about the whole boom on foot: " + asked[0]);
        wall[0] = 1.4f;
        rig.cramp(look, PIVOT_X, PIVOT_Y, PIVOT_UP, walled, 0f, Float.POSITIVE_INFINITY, 1f / 60f);
        float share = (1.4f - ThirdPerson.STANDOFF) / ThirdPerson.BEHIND;
        check(share > ThirdPerson.CRAMPED_FULL && share < ThirdPerson.CRAMPED_FROM && rig.crampShare() < 1f && rig.crampShare() > 0.9f,
                "with half the boom left it starts back out, eased: " + rig.crampShare());
        for (int frame = 0; frame < 360; frame++) {
            rig.cramp(look, PIVOT_X, PIVOT_Y, PIVOT_UP, OPEN, 0f, Float.POSITIVE_INFINITY, 1f / 60f);
        }
        check(rig.crampShare() < 1e-3f, "six seconds in the open it is all the way back out: " + rig.crampShare());
        wall[0] = 0.8f;
        rig.cramp(look, PIVOT_X, PIVOT_Y, PIVOT_UP, walled, 0f, Float.POSITIVE_INFINITY, 1f / 60f);
        float in = rig.crampShare();
        check(in > 0f && in < 0.05f, "into a cramped place again it sets off slowly: " + in);
        rig.seat(false);
        rig.seat(true);
        rig.cramp(look, PIVOT_X, PIVOT_Y, PIVOT_UP, walled, 0f, Float.POSITIVE_INFINITY, 1f / 60f);
        check(rig.crampShare() > in && rig.crampShare() < 0.2f, "a seat change does not jump it: " + rig.crampShare());

        // Out over the shoulder, sitting down glides the camera across to behind the vehicle, and getting out back.
        ThirdPerson seats = out();
        seats.seat(false);
        check(seats.seatedRight(ThirdPerson.AIM_RIGHT) == ThirdPerson.AIM_RIGHT, "on foot it stands where it is wanted");
        seats.seat(true);
        check(seats.seatedRight(0f) == ThirdPerson.AIM_RIGHT, "on the frame they sit down it is still over the shoulder");
        seats.advance(true, false, false, ThirdPerson.SEAT_SECONDS / 2f);
        float halfway = seats.seatedRight(0f);
        check(halfway > 0f && halfway < ThirdPerson.AIM_RIGHT, "half way through the glide it is between: " + halfway);
        seats.advance(true, false, false, ThirdPerson.SEAT_SECONDS);
        check(seats.seatedRight(0f) == 0f, "and then behind the vehicle");
    }

    private static void aWallBehindShortensTheBoomAtOnceAndItIsLetOutSlowly() {
        ThirdPerson rig = out();
        ViewCamera.Pose look = looking(0f, 0f), clean = new ViewCamera.Pose(), camera = new ViewCamera.Pose();
        float wall = 1.0f;
        ThirdPerson.Walls walled = (x, y, dx, dy, reach, margin, up, rise) -> Math.max(Math.min(reach, wall - margin), 0f);
        place(rig, look, look, OPEN, 0f, Float.POSITIVE_INFINITY, 1f / 60f, clean, camera);
        place(rig, look, look, walled, 0f, Float.POSITIVE_INFINITY, 1f / 60f, clean, camera);
        double back = PIVOT_X - camera.exactX();
        check(near(back, wall - ThirdPerson.STANDOFF, 1e-3), "on the frame the wall comes the camera stops STANDOFF short of it: " + back);
        place(rig, look, look, OPEN, 0f, Float.POSITIVE_INFINITY, 1f / 60f, clean, camera);
        double next = PIVOT_X - camera.exactX();
        check(next > back && next < ThirdPerson.BEHIND - 0.5, "the frame after the wall goes the boom is let out a little, not all the way: " + next);
        for (int frame = 0; frame < 120; frame++) {
            place(rig, look, look, OPEN, 0f, Float.POSITIVE_INFINITY, 1f / 60f, clean, camera);
        }
        check(near(PIVOT_X - camera.exactX(), ThirdPerson.BEHIND, 1e-3), "two seconds later it is all the way out: " + (PIVOT_X - camera.exactX()));
    }

    private static void theCameraIsKeptUnderTheCeilingAndOverTheFloor() {
        ViewCamera.Pose down = looking(0f, -60f), clean = new ViewCamera.Pose(), camera = new ViewCamera.Pose();
        float ceiling = 2.44949f;
        place(out(), down, down, OPEN, 0f, ceiling, 1f / 60f, clean, camera);
        check(near(camera.exactUp(), ceiling - ThirdPerson.STANDOFF, 1e-4), "looking down in a room the camera stays STANDOFF under the ceiling: " + camera.exactUp());
        ViewCamera.Pose up = looking(0f, 70f);
        place(out(), up, up, OPEN, 0.5f, Float.POSITIVE_INFINITY, 1f / 60f, clean, camera);
        check(near(camera.exactUp(), 0.5 + ThirdPerson.STANDOFF, 1e-4), "looking up the camera stays STANDOFF over the floor: " + camera.exactUp());
    }

    private static void theHeadTurnsAndMovesThePictureCameraAndNotTheCleanOne() {
        ViewCamera.Pose look = looking(0f, 0f), tracked = new ViewCamera.Pose(), clean = new ViewCamera.Pose(), camera = new ViewCamera.Pose();
        ViewCamera.Pose still = new ViewCamera.Pose(), stillCamera = new ViewCamera.Pose();
        place(out(), look, look, OPEN, 0f, Float.POSITIVE_INFINITY, 1f / 60f, still, stillCamera);
        tracked.set(look);
        ViewCamera.turn(tracked, (float) Math.toRadians(20), (float) Math.toRadians(10), 0f, true);
        tracked.moveEye(0.0, 0.2, 0.05);
        place(out(), look, tracked, OPEN, 0f, Float.POSITIVE_INFINITY, 1f / 60f, clean, camera);
        check(near(clean.exactX(), still.exactX(), 1e-5) && near(clean.exactY(), still.exactY(), 1e-5) && near(clean.exactUp(), still.exactUp(), 1e-5)
                && near(clean.forwardX, still.forwardX, 1e-6) && near(clean.forwardY, still.forwardY, 1e-6) && near(clean.forwardUp, still.forwardUp, 1e-6),
                "the clean camera, which the aim is made from, has nothing of the head in it");
        check(near(camera.forwardX, tracked.forwardX, 1e-5) && near(camera.forwardY, tracked.forwardY, 1e-5) && near(camera.forwardUp, tracked.forwardUp, 1e-5),
                "the picture's camera is turned as far as the head turned the eye: (" + camera.forwardX + ", " + camera.forwardY + ", " + camera.forwardUp
                        + ") against (" + tracked.forwardX + ", " + tracked.forwardY + ", " + tracked.forwardUp + ")");
        check(near(camera.exactY() - stillCamera.exactY(), 0.2, 1e-4) && near(camera.exactUp() - stillCamera.exactUp(), 0.05, 1e-4),
                "the picture's camera is moved by the lean: " + (camera.exactY() - stillCamera.exactY()) + ", " + (camera.exactUp() - stillCamera.exactUp()));
    }

    private static void theCameraSwingsOutFromTheEyeAndBack() {
        ThirdPerson rig = new ThirdPerson();
        check(rig.atTheEye() && rig.share() == 0f, "a new rig is at the eye");
        ViewCamera.Pose look = looking(0f, 0f), clean = new ViewCamera.Pose(), camera = new ViewCamera.Pose();
        place(rig, look, look, OPEN, 0f, Float.POSITIVE_INFINITY, 1f / 60f, clean, camera);
        check(near(camera.exactX(), PIVOT_X, 1e-5) && near(camera.exactUp(), PIVOT_UP, 1e-5), "at the eye the camera is at the pivot");
        rig.advance(true, false, false, ThirdPerson.SWING_SECONDS / 2f);
        check(near(rig.share(), 0.5f, 1e-4) && !rig.atTheEye() && !rig.behind(), "half the swing's time is half the way out: " + rig.share());
        rig.advance(true, false, false, ThirdPerson.SWING_SECONDS);
        check(rig.behind(), "the whole swing's time is all the way out");
        rig.advance(false, false, false, ThirdPerson.SWING_SECONDS * 2f);
        check(rig.atTheEye(), "and back in again");
        rig.advance(false, true, false, ThirdPerson.AIM_SECONDS);
        check(near(rig.aimShare(), 1f, 1e-5), "the sights bring it over the shoulder in AIM_SECONDS");
    }

    private static void aTurnCarriedOverKeepsTheAxesSquare() {
        ViewCamera.Pose clean = looking(10f, -20f), tracked = new ViewCamera.Pose(), camera = looking(-70f, 35f);
        tracked.set(clean);
        ViewCamera.turn(tracked, (float) Math.toRadians(-35), (float) Math.toRadians(25), (float) Math.toRadians(15), false);
        ThirdPerson.turnedLike(camera, clean, tracked);
        double f = Math.sqrt(camera.forwardX * camera.forwardX + camera.forwardY * camera.forwardY + camera.forwardUp * camera.forwardUp);
        double u = Math.sqrt(camera.upX * camera.upX + camera.upY * camera.upY + camera.upUp * camera.upUp);
        double r = Math.sqrt(camera.rightX * camera.rightX + camera.rightY * camera.rightY + camera.rightUp * camera.rightUp);
        double fu = camera.forwardX * camera.upX + camera.forwardY * camera.upY + camera.forwardUp * camera.upUp;
        double fr = camera.forwardX * camera.rightX + camera.forwardY * camera.rightY + camera.forwardUp * camera.rightUp;
        double ur = camera.upX * camera.rightX + camera.upY * camera.rightY + camera.upUp * camera.rightUp;
        check(near(f, 1, 1e-5) && near(u, 1, 1e-5) && near(r, 1, 1e-5) && near(fu, 0, 1e-5) && near(fr, 0, 1e-5) && near(ur, 0, 1e-5),
                "the turned axes are of length one and square to each other");
        // Right is where ViewCamera.clean puts it: up across forward.
        ViewCamera.Pose level = looking(0f, 0f);
        ThirdPerson.turnedLike(level, level, level);
        check(near(level.rightX, 0, 1e-6) && near(level.rightY, 1, 1e-6), "no turn leaves right where it was: (" + level.rightX + ", " + level.rightY + ")");
    }

    private static void thePivotTrailsTheCharacterALittleAndJumpsWithThem() {
        ThirdPerson rig = out();
        rig.follow(10.0, 20.0, 1.6, 1f / 60f);
        check(rig.followX() == 10.0 && rig.followY() == 20.0 && rig.followUp() == 1.6, "the first follow puts the pivot on the character");
        // Running at 5 tiles a second along x for two seconds.
        double x = 10.0;
        double mostBehind = 0.0;
        for (int frame = 0; frame < 120; frame++) {
            x += 5.0 / 60.0;
            rig.follow(x, 20.0, 1.6, 1f / 60f);
            mostBehind = Math.max(mostBehind, x - rig.followX());
            check(near(rig.followY(), 20.0, 1e-9), "nothing moves the pivot across the way the character runs");
        }
        double settled = x - rig.followX();
        check(settled > 0.1 && settled < 0.5, "at a run the pivot trails a little, steadily: " + settled + " tiles");
        check(mostBehind <= ThirdPerson.MOST_TRAIL + 1e-9, "and never by more than MOST_TRAIL: " + mostBehind);
        for (int frame = 0; frame < 60; frame++) {
            rig.follow(x, 20.0, 1.6, 1f / 60f);
        }
        check(near(rig.followX(), x, 1e-4), "stopped, it catches up in a second: " + (x - rig.followX()));
        rig.follow(x, 20.0, 2.0, 1f / 60f);
        check(rig.followUp() > 1.6 && rig.followUp() < 1.8, "a step up is followed over a few frames, not at once: " + rig.followUp());
        rig.follow(x + 50.0, 20.0, 1.6, 1f / 60f);
        check(rig.followX() == x + 50.0, "a jump of many tiles puts the pivot there at once");
        rig.unplaced();
        rig.follow(0.0, 0.0, 0.0, 1f / 60f);
        check(rig.followX() == 0.0 && rig.followUp() == 0.0, "after the camera has been at the eye it starts on the character again");
    }

    private static void behindAVehicleTheCameraSwingsBackTheShorterWayAndLooksDown() {
        float heading = (float) Math.toRadians(170), yaw = (float) Math.toRadians(-170);
        float next = ThirdPerson.swungBehind(yaw, heading, 1f / 60f);
        check(next < yaw, "from -170 to 170 degrees it swings through 180, the 20 degree way: " + Math.toDegrees(next));
        float pitch = 0.3f;
        for (int frame = 0; frame < 180; frame++) {
            yaw = ThirdPerson.swungBehind(yaw, heading, 1f / 60f);
            pitch = ThirdPerson.tippedBehind(pitch, 1f / 60f);
        }
        check(near(Math.IEEEremainder(yaw - heading, 2 * Math.PI), 0.0, 1e-3), "three seconds on it is behind: " + Math.toDegrees(yaw));
        check(near(pitch, ThirdPerson.VEHICLE_PITCH, 1e-3), "and looking down on the vehicle: " + pitch);
    }

    private static void aSprintTakesTheCameraBackAndStoppingBringsItIn() {
        ThirdPerson rig = out();
        check(rig.sprintShare() == 0f, "a rig starts at a walk");
        rig.advance(true, false, true, ThirdPerson.SPRINT_SECONDS / 2f);
        check(near(rig.sprintShare(), 0.5f, 1e-4), "half the time is half the way back: " + rig.sprintShare());
        rig.advance(true, false, true, ThirdPerson.SPRINT_SECONDS);
        check(rig.sprintShare() == 1f, "and all the way back after SPRINT_SECONDS");
        rig.advance(true, false, false, ThirdPerson.SPRINT_SECONDS);
        check(rig.sprintShare() == 0f, "and in again as the sprint ends");
    }

    private static void theHeadTurnIsCarriedOntoACameraLookingAnotherWay() {
        // The clean view faces +x, the camera's look faces +y: a head turned 20 degrees right turns the camera to 110.
        ViewCamera.Pose clean = looking(0f, 0f), tracked = new ViewCamera.Pose(), look = looking(90f, 0f), clean2 = new ViewCamera.Pose(), camera = new ViewCamera.Pose();
        tracked.set(clean);
        ViewCamera.turn(tracked, (float) Math.toRadians(20), (float) Math.toRadians(-15), 0f, true);
        out().camera(look, clean, tracked, PIVOT_X, PIVOT_Y, PIVOT_UP, ThirdPerson.BEHIND, ThirdPerson.ABOVE, ThirdPerson.RIGHT, OPEN, 0f, 0f,
                Float.POSITIVE_INFINITY, 1f / 60f, clean2, camera);
        double yaw = Math.toDegrees(Math.atan2(camera.forwardY, camera.forwardX)), pitch = Math.toDegrees(Math.asin(camera.forwardUp));
        check(near(yaw, 110.0, 1e-3) && near(pitch, -15.0, 1e-3), "the camera looks 110 degrees round and 15 down: " + yaw + ", " + pitch);
        check(near(clean2.forwardX, look.forwardX, 1e-6) && near(clean2.forwardY, look.forwardY, 1e-6), "and the clean camera looks the way the look does");
    }

    private static void overTheShoulderIsToTheRight() {
        // Facing +x, the right is +y.
        ViewCamera.Pose look = looking(0f, 0f), clean = new ViewCamera.Pose(), camera = new ViewCamera.Pose();
        out().camera(look, look, look, PIVOT_X, PIVOT_Y, PIVOT_UP, ThirdPerson.AIM_BEHIND, ThirdPerson.AIM_ABOVE, ThirdPerson.AIM_RIGHT, OPEN, 0f, 0f,
                Float.POSITIVE_INFINITY, 1f / 60f, clean, camera);
        check(near(camera.exactY() - PIVOT_Y, ThirdPerson.AIM_RIGHT, 1e-4) && near(PIVOT_X - camera.exactX(), ThirdPerson.AIM_BEHIND, 1e-4),
                "with the sights up the camera is AIM_RIGHT to the right and AIM_BEHIND back: (" + (PIVOT_X - camera.exactX()) + " back, "
                        + (camera.exactY() - PIVOT_Y) + " right)");
    }

    private static void aWallKeepsTheCameraWhereItIsAsTheBoomShortens() {
        ThirdPerson rig = out();
        ViewCamera.Pose look = looking(0f, 0f), clean = new ViewCamera.Pose(), camera = new ViewCamera.Pose();
        float wall = 1.0f;
        ThirdPerson.Walls walled = (x, y, dx, dy, reach, margin, up, rise) -> Math.max(Math.min(reach, wall - margin), 0f);
        place(rig, look, look, walled, 0f, Float.POSITIVE_INFINITY, 1f / 60f, clean, camera);
        double before = PIVOT_X - camera.exactX();
        // The sights come up: the boom is 1.4 tiles, still longer than the room the wall leaves.
        rig.camera(look, look, look, PIVOT_X, PIVOT_Y, PIVOT_UP, 1.4f, ThirdPerson.ABOVE, 0f, walled, 0f, 0f, Float.POSITIVE_INFINITY, 1f / 60f, clean, camera);
        double after = PIVOT_X - camera.exactX();
        check(near(before, wall - ThirdPerson.STANDOFF, 1e-4) && near(after, before, 1e-4),
                "the camera stays STANDOFF off the wall as the boom shortens: " + before + " then " + after);
    }

    private static void inTheOpenTheBoomKeepsUpWithTheView() {
        // Looking well down the boom is short across the ground; looking level again it is long at once, with no wall to let it out from.
        ThirdPerson rig = out();
        ViewCamera.Pose down = looking(0f, -60f), level = looking(0f, 0f), clean = new ViewCamera.Pose(), camera = new ViewCamera.Pose();
        place(rig, down, down, OPEN, -100f, Float.POSITIVE_INFINITY, 1f / 60f, clean, camera);
        place(rig, level, level, OPEN, -100f, Float.POSITIVE_INFINITY, 1f / 60f, clean, camera);
        check(near(PIVOT_X - camera.exactX(), ThirdPerson.BEHIND, 1e-4), "the frame the view comes level the camera is the whole boom back: " + (PIVOT_X - camera.exactX()));
    }

    private static void sittingDownTheCameraGoesOverToTheVehicleOverTime() {
        // As FirstPerson.placeBehind calls it each frame: the seat, then the pivot, then how far back and up.
        ThirdPerson rig = out();
        float wanted = 5.6f;
        rig.seat(false);
        rig.follow(0.0, 0.0, 1.6, 1f / 60f);
        rig.seatedBehind(ThirdPerson.BEHIND);
        rig.seatedAbove(ThirdPerson.ABOVE);
        // Sat down in a bus whose middle is 4 tiles off: further than a teleport, glided to all the same.
        rig.seat(true);
        rig.follow(4.0, 0.0, 2.5, 1f / 60f);
        check(rig.followX() < 0.5, "the frame the character sits the pivot has hardly moved: " + rig.followX());
        check(rig.seatedBehind(wanted) == ThirdPerson.BEHIND && rig.seatedAbove(0.9f) == ThirdPerson.ABOVE, "and the camera stands as far back and up as it stood");
        rig.advance(true, false, false, ThirdPerson.SEAT_SECONDS / 2f);
        rig.seat(true);
        rig.follow(4.0, 0.0, 2.5, 1f / 60f);
        float halfway = rig.seatedBehind(wanted);
        check(near(halfway, (ThirdPerson.BEHIND + wanted) / 2f, 1e-4), "half the time is half the way back: " + halfway);
        rig.advance(true, false, false, ThirdPerson.SEAT_SECONDS);
        for (int frame = 0; frame < 120; frame++) {
            rig.seat(true);
            rig.follow(4.0, 0.0, 2.5, 1f / 60f);
        }
        check(rig.seatedBehind(wanted) == wanted && rig.seatedAbove(0.9f) == 0.9f, "the whole time is all the way");
        check(near(rig.followX(), 4.0, 1e-4) && near(rig.followUp(), 2.5, 1e-4), "with the pivot over the vehicle: " + rig.followX() + ", " + rig.followUp());
        // A cut forgets the seat: the next placing on foot is not a getting up.
        rig.cut(true);
        rig.seat(false);
        check(rig.seatedBehind(ThirdPerson.BEHIND) == ThirdPerson.BEHIND, "after a cut the camera starts where it is wanted");
    }

    private static void theWallsAreToldHowHighTheBoomIs() {
        // Looking 40 degrees down the boom climbs back from the pivot: the doorways it crosses are met higher the further back they are.
        ThirdPerson rig = out();
        ViewCamera.Pose down = looking(0f, -40f), clean = new ViewCamera.Pose(), camera = new ViewCamera.Pose();
        float floor = 0.5f;
        float[] told = new float[2];
        ThirdPerson.Walls walls = (x, y, dx, dy, reach, margin, up, rise) -> {
            told[0] = up;
            told[1] = rise;
            return reach;
        };
        place(rig, down, down, walls, floor, Float.POSITIVE_INFINITY, 1f / 60f, clean, camera);
        double flat = Math.hypot(camera.exactX() - PIVOT_X, camera.exactY() - PIVOT_Y);
        check(near(told[0], PIVOT_UP - floor, 1e-5), "the boom starts the pivot's height over the floor: " + told[0]);
        check(near(told[1], (camera.exactUp() - PIVOT_UP) / flat, 1e-4), "and rises as far a tile as the camera is up over its way back: " + told[1]);
    }

    private static void underACeilingTheWallsAreToldTheRiseOfTheLineTheCameraIsPutOn() {
        ThirdPerson rig = out();
        ViewCamera.Pose down = looking(0f, -40f), clean = new ViewCamera.Pose(), camera = new ViewCamera.Pose();
        float ceiling = 2.2f;
        float[] told = new float[1];
        ThirdPerson.Walls walls = (x, y, dx, dy, reach, margin, up, rise) -> {
            told[0] = rise;
            return reach;
        };
        place(rig, down, down, walls, 0f, ceiling, 1f / 60f, clean, camera);
        double flat = Math.hypot(camera.exactX() - PIVOT_X, camera.exactY() - PIVOT_Y);
        check(near(camera.exactUp(), ceiling - ThirdPerson.STANDOFF, 1e-4), "the camera is held under the ceiling: " + camera.exactUp());
        check(near(told[0], (camera.exactUp() - PIVOT_UP) / flat, 1e-4), "and the rise the walls are told is the line to it: " + told[0]);
    }

    private static void aCameraAboutTheTopOfAWallIsHeldOutPastTheEave() {
        float top = ViewCamera.LEVEL_HEIGHT, most = WorldQuery.MOST_HELD_OFF - ThirdPerson.STANDOFF;
        check(ThirdPerson.offTheEave(PIVOT_UP + ThirdPerson.ABOVE) == 0f, "a camera behind a standing character is under the eave and held off the wall as before");
        check(ThirdPerson.offTheEave(top) == most && ThirdPerson.offTheEave(top - 0.3f) == most, "about the wall's top it is held as far out as anything");
        check(ThirdPerson.offTheEave(top + 1f) == most, "and over it, beside a gable's rake");
        check(ThirdPerson.STANDOFF + most > RoofBuilder.EAVE + RoofBuilder.GUTTER, "which is out past the gutter's edge");
        check(ThirdPerson.offTheEave(top - RoofBuilder.EAVE - RoofBuilder.FASCIA - RoofBuilder.GUTTER - ThirdPerson.EAVE_FADE) == 0f,
                "and under the lowest an eave hangs it is held off as before");
        float step = 0f;
        for (float up = 1.5f; up < 3.2f; up += 0.01f) {
            step = Math.max(step, Math.abs(ThirdPerson.offTheEave(up + 0.01f) - ThirdPerson.offTheEave(up)));
        }
        check(step <= most * 0.01f / ThirdPerson.EAVE_FADE + 1e-4f, "and a camera sinking past it is moved out and in over EAVE_FADE: at most " + step + " a hundredth of a tile");
    }

    /** A plot from x -2 to 2 and y -2 to 4, on the ground and a level up, looked at on the ground: the house is north of y 1 and the yard south of it. */
    private static zombie.iso.IsoCell plot() {
        zombie.iso.IsoCell cell = new zombie.iso.IsoCell();
        for (int z = 0; z <= 1; z++) {
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 4; y++) {
                    cell.square(x, y, z);
                }
            }
        }
        return cell;
    }

    private static zombie.iso.sprite.IsoSprite sprite(String name, zombie.iso.SpriteDetails.IsoObjectType type, String... flags) {
        java.util.Map<String, String> properties = new java.util.HashMap<>();
        for (String flag : flags) {
            properties.put(flag, "");
        }
        return new zombie.iso.sprite.IsoSprite(name, new zombie.core.properties.PropertyContainer(properties), type, null);
    }

    private static WorldQuery looking(zombie.iso.IsoCell cell) {
        WorldQuery world = new WorldQuery();
        world.look(cell, 0);
        return world;
    }

    /** From the yard at y 2.5, straight north at the house, the boom's room as high as that and rising so much a tile. */
    private static float north(WorldQuery world, float up, float rise, float reach) {
        return world.roomForTheCamera(0.5f, 2.5f, 0f, -1f, reach, ThirdPerson.STANDOFF, up, rise);
    }

    private static void aBoomPassesADoorwayOnlyUnderTheDoorsHead() {
        zombie.iso.IsoCell cell = plot();
        cell.square(0, 1, 0).add(sprite("fixtures_doors_frames_01_1", zombie.iso.SpriteDetails.IsoObjectType.doorFrN, "DoorWallN"));
        WorldQuery world = looking(cell);
        float door = WorldQuery.DOOR_HEAD * ViewCamera.LEVEL_HEIGHT;
        check(north(world, door - 0.1f, 0f, 3f) == 3f, "under the door's head the boom goes through the doorway");
        float met = north(world, door + 0.1f, 0f, 3f);
        check(near(met, 1.5f - ThirdPerson.STANDOFF, 1e-4), "over it the wall above the door stops it a standoff short: " + met);
        float rising = north(world, door - 0.4f, 0.5f, 3f);
        check(rising < 3f, "a boom rising past the door's head before it reaches the doorway is stopped: " + rising);
        // The trace runs on past the camera by the standoff, and a boom stood short of the doorway is no higher beyond it.
        float shortOf = 1.5f - 0.1f;
        check(north(world, door - 0.01f - 0.4f * shortOf, 0.4f, shortOf) == shortOf, "and one stood short of it under the head is not met beyond the camera");
    }

    private static void aBoomAsHighAsAnEaveDoesNotGoInUnderTheRoofFromTheOpenAir() {
        zombie.iso.IsoCell cell = plot();
        // A storey's roof is on the level over it, as the map has one.
        for (int x = -2; x <= 2; x++) {
            cell.square(x, 0, 1).add(sprite("roofs_01_36", zombie.iso.SpriteDetails.IsoObjectType.WestRoofB));
        }
        WorldQuery world = looking(cell);
        check(north(world, PIVOT_UP + ThirdPerson.ABOVE, 0f, 3f) == 3f, "a camera behind a standing character goes on under the eave, as low as it is");
        float met = north(world, WorldQuery.ROOFS_FROM + 0.1f, 0f, 3f);
        check(near(met, 1.5f - ThirdPerson.STANDOFF, 1e-4), "one as high as the eave hangs is stopped where the roof starts: " + met);
        for (int y = 1; y <= 4; y++) {
            cell.square(0, y, 0).setRoomID(7L);
        }
        check(north(world, WorldQuery.ROOFS_FROM + 0.1f, 0f, 3f) == 3f, "from a room, under a roof of a storey or one of its own, it is not");
    }

    private static void aBoomOverTheTopOfAWallMeetsTheWallOfTheLevelAbove() {
        zombie.iso.IsoCell cell = plot();
        cell.square(0, 1, 1).add(sprite("walls_exterior_roofs_01_1", zombie.iso.SpriteDetails.IsoObjectType.wall, "WallN"));
        WorldQuery world = looking(cell);
        check(north(world, ViewCamera.LEVEL_HEIGHT - ThirdPerson.STANDOFF - 0.05f, 0f, 3f) == 3f, "under the top of the wall below the gable is not met");
        float met = north(world, ViewCamera.LEVEL_HEIGHT - ThirdPerson.STANDOFF + 0.05f, 0f, 3f);
        check(near(met, 1.5f - ThirdPerson.STANDOFF, 1e-4), "about the top of it the gable's wall stops the boom: " + met);
    }

    private static void aCameraOutFromUnderAPorchIsNotPushedBackUnderIt() {
        zombie.iso.IsoCell cell = plot();
        cell.square(0, 0, 1).add(sprite("roofs_01_36", zombie.iso.SpriteDetails.IsoObjectType.WestRoofB));
        WorldQuery world = looking(cell);
        float up = WorldQuery.ROOFS_FROM + 0.3f;
        double[] at = {0.5, 1.1};
        world.holdCameraOffWalls(at, 0.5, 0.5, ThirdPerson.STANDOFF, up, up, 0f);
        check(at[0] == 0.5 && at[1] == 1.1, "a camera just out from under a porch the character stands under stays out: " + at[0] + ", " + at[1]);
        cell.square(0, 1, 0).add(sprite("walls_exterior_house_01_1", zombie.iso.SpriteDetails.IsoObjectType.wall, "WallN"));
        double[] walled = {0.5, 1.1};
        world.holdCameraOffWalls(walled, 0.5, 1.5, ThirdPerson.STANDOFF, up, up, 0f);
        check(near(walled[1], 1.0 + ThirdPerson.STANDOFF, 1e-6), "and one by a wall is pushed off it to the character's side: " + walled[1]);
    }
}
