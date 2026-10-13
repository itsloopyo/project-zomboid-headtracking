package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of who is let room on the card first: see RoomLine. */
final class RoomLineTests {
    private RoomLineTests() {}

    private static final long FORGET = 1_000L;

    static void run() {
        Checks.run("roomThatFitsIsTakenWithNobodyWaiting", RoomLineTests::roomThatFitsIsTakenWithNobodyWaiting);
        Checks.run("theNearestWaitingHasTheRoomFirst", RoomLineTests::theNearestWaitingHasTheRoomFirst);
        Checks.run("someoneNearerGoesAheadOfTheFirstInLine", RoomLineTests::someoneNearerGoesAheadOfTheFirstInLine);
        Checks.run("aPlaceInLineIsLostByNotAsking", RoomLineTests::aPlaceInLineIsLostByNotAsking);
        Checks.run("theLineMovesOnOnceServed", RoomLineTests::theLineMovesOnOnceServed);
        Checks.run("theFirstInLineIsServedHoweverTheyMove", RoomLineTests::theFirstInLineIsServedHoweverTheyMove);
    }

    private static void roomThatFitsIsTakenWithNobodyWaiting() {
        RoomLine line = new RoomLine(FORGET);
        check(line.ask(new Object(), 4f, 0L, true), "with nobody waiting, room that fits is taken");
        check(!line.ask(new Object(), 4f, 0L, false), "and room that does not fit is not");
    }

    private static void theNearestWaitingHasTheRoomFirst() {
        RoomLine line = new RoomLine(FORGET);
        Object near = new Object(), far = new Object();
        line.ask(near, 1f, 0L, false);
        check(!line.ask(far, 3f, 10L, true), "someone further off is not let room that frees while someone nearer waits");
        check(line.ask(near, 1f, 20L, true), "the nearest waiting takes it");
    }

    private static void someoneNearerGoesAheadOfTheFirstInLine() {
        RoomLine line = new RoomLine(FORGET);
        Object far = new Object(), near = new Object();
        line.ask(far, 3f, 0L, false);
        line.ask(near, 1f, 10L, false);
        check(!line.ask(far, 3f, 20L, true), "once someone nearer waits, the one further off waits behind them");
        check(line.ask(near, 1f, 30L, true), "and the nearer is served");
    }

    private static void aPlaceInLineIsLostByNotAsking() {
        RoomLine line = new RoomLine(FORGET);
        line.ask(new Object(), 1f, 0L, false);
        check(line.ask(new Object(), 3f, FORGET + 1L, true), "someone who stopped asking holds nobody back for good");
    }

    private static void theLineMovesOnOnceServed() {
        RoomLine line = new RoomLine(FORGET);
        Object near = new Object();
        line.ask(near, 1f, 0L, false);
        line.ask(near, 1f, 10L, true);
        check(line.ask(new Object(), 3f, 20L, true), "after the first in line is served, anyone may take room that fits");
    }

    private static void theFirstInLineIsServedHoweverTheyMove() {
        RoomLine line = new RoomLine(FORGET);
        Object walker = new Object();
        long now = 0L;
        for (int frame = 0; frame < 50; frame++) {
            now += 10L;
            line.ask(walker, 1f + frame * 0.05f, now, false);
        }
        check(line.ask(walker, 4f, now + 10L, true), "walking away while waiting keeps their place");
    }
}
