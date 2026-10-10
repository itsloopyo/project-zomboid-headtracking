package com.cameraunlock.zomboid;

/** Tests of who the eye's picture leaves out by the room they stand in: see FigureRooms. */
final class FigureRoomsTests {
    private FigureRoomsTests() {}

    static void run() {
        Checks.run("aPersonInARoomNoLineReachesIsLeftOut", FigureRoomsTests::aPersonInARoomNoLineReachesIsLeftOut);
        Checks.run("aPersonSeenThroughAnOpenDoorIsDrawn", FigureRoomsTests::aPersonSeenThroughAnOpenDoorIsDrawn);
        Checks.run("aPersonBesideADoorOrOutOfDoorsIsAlwaysDrawn", FigureRoomsTests::aPersonBesideADoorOrOutOfDoorsIsAlwaysDrawn);
        Checks.run("whereEveryoneCanFightNobodyIsLeftOutByRoom", FigureRoomsTests::whereEveryoneCanFightNobodyIsLeftOutByRoom);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static final long HALL = 1, BACK = 2;

    /** A hall, squares 1 to 4 each way, and a long back room east of it, 5 to 10 across and 1 to 2 down, through a door at the west edge of 5, 2. */
    private static RoomSightTests.Plan house(int door) {
        return new RoomSightTests.Plan().room(HALL, 0, 1, 1, 4, 4).room(BACK, 0, 5, 1, 10, 2).opening(5, 2, 0, false, door);
    }

    /** Who is left out for an eye at (x, y) on level 0, as the frame works it out: the rooms a walk round the eye finds, and those the picture sees into. */
    private static FigureRooms seenFrom(RoomSightTests.Plan plan, float x, float y, float yaw) {
        RoomSight.Openings openings = plan.openings();
        RoomSight.Blocks blocks = plan.blocks();
        int squareX = (int) Math.floor(x), squareY = (int) Math.floor(y);
        long start = plan.region(squareX, squareY, 0), building = plan.building(squareX, squareY, 0);
        RoomSight walker = new RoomSight();
        RoomSight.Seen round = new RoomSight.Seen(), picture = new RoomSight.Seen(), entered = new RoomSight.Seen();
        walker.walkAllRound(plan, openings, blocks, x, y, 1.6f, start, building, 1.5f, 10f, round);
        ViewCamera.Pose eye = new ViewCamera.Pose();
        ViewCamera.clean(eye, x, y, 1.6f, (float) Math.toRadians(yaw), 0f);
        float tan = (float) Math.tan(Math.toRadians(55) / 2);
        walker.walk(plan, openings, blocks, eye, tan * 16f / 9f, tan, start, building, picture);
        RoomSight.Kept built = new RoomSight.Kept();
        built.found(round, 0L, entered);
        built.found(picture, 0L, entered);
        FigureRooms figures = new FigureRooms();
        figures.fill(built, openings);
        return figures;
    }

    private static void aPersonInARoomNoLineReachesIsLeftOut() {
        // The eye in the street west of the house, 14 squares from the shut door: no wall of the house has an opening to the street.
        FigureRooms figures = seenFrom(house(RoomSight.SHUT), -12.5f, 2.5f, 0f);
        check(figures.leftOut(BACK, 9, 1, 0), "a person at the far end of a room behind a wall and a shut door is left out");
        check(figures.leftOut(HALL, 2, 2, 0), "and so is one in the hall, whose walls the street has no line through");
    }

    private static void aPersonSeenThroughAnOpenDoorIsDrawn() {
        FigureRooms figures = seenFrom(house(RoomSight.SEE), 2.5f, 2.5f, 0f);
        check(!figures.leftOut(BACK, 9, 1, 0), "a person in the back room, seen through the open door, is drawn");
        FigureRooms behind = seenFrom(house(RoomSight.SEE), 2.5f, 2.5f, 180f);
        check(!behind.leftOut(BACK, 9, 1, 0), "and is with the eye turned away, so that turning back finds them there");
        FigureRooms shut = seenFrom(house(RoomSight.SHUT), 2.5f, 2.5f, 0f);
        check(!shut.leftOut(BACK, 9, 1, 0), "and is behind a shut door near the eye, which may be opened at any moment");
    }

    private static void aPersonBesideADoorOrOutOfDoorsIsAlwaysDrawn() {
        // The eye in the hall with the door shut and far enough off to count as shut: the hall is seen, the back room is not.
        RoomSightTests.Plan plan = new RoomSightTests.Plan().room(HALL, 0, 1, 1, 20, 4).room(BACK, 0, 21, 1, 26, 2).opening(21, 2, 0, false, RoomSight.SHUT);
        FigureRooms figures = seenFrom(plan, 2.5f, 2.5f, 0f);
        check(figures.leftOut(BACK, 25, 1, 0), "a person deep in the back room is left out");
        check(!figures.leftOut(BACK, 21, 2, 0) && !figures.leftOut(BACK, 22, 1, 0), "one just inside the door is drawn: a body reaches through an opening further than its square");
        check(!figures.leftOut(RoomSight.OUTSIDE, 30, 30, 0), "and one out of doors always is");
    }

    private static void whereEveryoneCanFightNobodyIsLeftOutByRoom() {
        check(!FigureRooms.applies(true), "where players can fight, no one is left out by room: the frame hands the picture no room test");
        check(FigureRooms.applies(false), "and elsewhere they are");
    }
}
