package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Whether a room is seen, taken from the room as a whole: RoomsSeen. */
final class RoomsSeenTests {
    private RoomsSeenTests() {}

    static void run() {
        Checks.run("aRoomMostlySeenIsSeen", RoomsSeenTests::aRoomMostlySeenIsSeen);
        Checks.run("aRoomMostlyUnseenIsNot", RoomsSeenTests::aRoomMostlyUnseenIsNot);
        Checks.run("squaresNotLoadedAreNotCounted", RoomsSeenTests::squaresNotLoadedAreNotCounted);
        Checks.run("everyRectangleIsCounted", RoomsSeenTests::everyRectangleIsCounted);
        Checks.run("stoppingEarlyGivesTheWholeCount", RoomsSeenTests::stoppingEarlyGivesTheWholeCount);
    }

    /** A hall 10 by 6 at (100, 200), seen but for an unseen patch of x by y at its north-east corner. */
    private static RoomsSeen.Squares patched(int patchX, int patchY) {
        return (x, y) -> x < 100 || x >= 110 || y < 200 || y >= 206 ? RoomsSeen.NONE
                : x >= 110 - patchX && y < 200 + patchY ? RoomsSeen.UNSEEN : RoomsSeen.SEEN;
    }

    private static void aRoomMostlySeenIsSeen() {
        check(RoomsSeen.most(new int[] {100, 200, 10, 6}, patched(5, 3)), "a hall seen but for a patch of 15 of its 60 squares is seen, patch and all");
    }

    private static void aRoomMostlyUnseenIsNot() {
        check(!RoomsSeen.most(new int[] {100, 200, 10, 6}, patched(10, 4)), "a hall with 40 of its 60 squares unseen is not seen");
    }

    private static void squaresNotLoadedAreNotCounted() {
        check(RoomsSeen.most(new int[] {96, 200, 14, 6}, patched(5, 3)), "squares of the room not loaded count neither way");
        check(!RoomsSeen.most(new int[] {0, 0, 4, 4}, patched(5, 3)), "and a room with none loaded is not seen");
    }

    private static void everyRectangleIsCounted() {
        RoomsSeen.Squares squares = (x, y) -> x < 10 ? RoomsSeen.SEEN : RoomsSeen.UNSEEN;
        check(!RoomsSeen.most(new int[] {0, 0, 10, 1, 10, 0, 5, 3}, squares), "10 seen in one rectangle against 15 unseen in another is not seen");
    }

    private static void stoppingEarlyGivesTheWholeCount() {
        java.util.Random random = new java.util.Random(7);
        for (int trial = 0; trial < 20000; trial++) {
            int[] rects = new int[4 * (1 + random.nextInt(3))];
            for (int r = 0; r < rects.length; r += 4) {
                rects[r] = random.nextInt(6);
                rects[r + 1] = random.nextInt(6);
                rects[r + 2] = 1 + random.nextInt(5);
                rects[r + 3] = 1 + random.nextInt(5);
            }
            int[] states = new int[144];
            for (int i = 0; i < states.length; i++) {
                states[i] = random.nextInt(3) - 1;
            }
            RoomsSeen.Squares squares = (x, y) -> states[y * 12 + x];
            int seen = 0, unseen = 0;
            for (int r = 0; r < rects.length; r += 4) {
                for (int y = rects[r + 1]; y < rects[r + 1] + rects[r + 3]; y++) {
                    for (int x = rects[r]; x < rects[r] + rects[r + 2]; x++) {
                        seen += squares.at(x, y) == RoomsSeen.SEEN ? 1 : 0;
                        unseen += squares.at(x, y) == RoomsSeen.UNSEEN ? 1 : 0;
                    }
                }
            }
            check(RoomsSeen.most(rects, squares) == seen > unseen, "trial " + trial + " answers as the whole count does");
        }
    }
}
