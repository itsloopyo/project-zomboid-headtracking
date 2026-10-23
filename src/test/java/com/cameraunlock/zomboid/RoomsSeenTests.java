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
}
