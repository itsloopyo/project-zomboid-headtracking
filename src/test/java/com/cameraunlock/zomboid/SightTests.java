package com.cameraunlock.zomboid;

/** Tests of Sight: what the game's own view keeps of each square's light, and how a closed room takes the light round it. */
final class SightTests {
    private SightTests() {}

    static void run() {
        Checks.run("theDarkMultiplierIsKeptAsAByte", SightTests::theDarkMultiplierIsKeptAsAByte);
        Checks.run("aRoomTakesTheLightOfEachSideItStandsBy", SightTests::aRoomTakesTheLightOfEachSideItStandsBy);
        Checks.run("aSquareNotReadLendsNothing", SightTests::aSquareNotReadLendsNothing);
        Checks.run("aRoomWithNothingRoundItIsDark", SightTests::aRoomWithNothingRoundItIsDark);
        Checks.run("theBlockPutsEachSquareWhereTheShaderReadsIt", SightTests::theBlockPutsEachSquareWhereTheShaderReadsIt);
        Checks.run("whatStandsOverAClosedRoomKeepsItsLight", SightTests::whatStandsOverAClosedRoomKeepsItsLight);
        Checks.run("eachRingIsTheWholeBlockLookedAtAgain", SightTests::eachRingIsTheWholeBlockLookedAtAgain);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void theDarkMultiplierIsKeptAsAByte() {
        check(Sight.kept(1f) == 255, "a square in sight keeps all of its light");
        check(Sight.kept(0f) == 0, "a square never seen keeps none");
        check(Sight.kept(0.5f) == 128, "the edge between them keeps half");
        check(Sight.kept(2.5f) == 255, "a torch lifts the game's figure past 1, and the picture keeps no more than all its light");
    }

    private static void aRoomTakesTheLightOfEachSideItStandsBy() {
        // A row of five: in sight, three of a room, never seen.
        int[] figures = {255, Sight.CLOSED, Sight.CLOSED, Sight.CLOSED, 0};
        Sight.fill(figures, new int[5], new int[5], 5, 1);
        check(figures[1] == 255 && figures[3] == 0, "the room's square by the seen ground is lit, the one by ground never seen is dark");
        check(figures[2] == 128, "and the middle takes from both, reached from both in the same ring");
    }

    private static void aSquareNotReadLendsNothing() {
        int[] figures = {Sight.UNREAD, Sight.CLOSED, 200};
        Sight.fill(figures, new int[3], new int[3], 3, 1);
        check(figures[1] == 200, "a room at the edge of what was read takes only the side that was read");
    }

    private static void aRoomWithNothingRoundItIsDark() {
        int[] figures = {Sight.UNREAD, Sight.CLOSED, Sight.UNREAD};
        Sight.fill(figures, new int[3], new int[3], 3, 1);
        check(figures[1] == 0, "a room with nothing read round it keeps none of its light");
    }

    private static void theBlockPutsEachSquareWhereTheShaderReadsIt() {
        Sight sight = new Sight();
        sight.begin(1000, 2000);
        check(sight.x == 1000 - Sight.SIDE / 2 && sight.y == 2000 - Sight.SIDE / 2, "the block is centred on the viewer's square");
        sight.put(1001, 2003, 77);
        sight.settle();
        check(sight.at(1001, 2003) == 77, "a square's figure is at its own place");
        check(sight.shown.get((2003 - sight.y) * Sight.SIDE + 1001 - sight.x) == 77, "rows run along y and columns along x, as the shader reads them");
        check(sight.at(1002, 2003) == 0, "a square not read keeps none of its light");
        for (int row = 0; row < Sight.SIDE; row++) {
            for (int column = 0; column < Sight.SIDE; column++) {
                boolean room = row > 20 && row < 100 && column > 20 && column < 100;
                sight.put(sight.x + column, sight.y + row, room ? Sight.CLOSED : 255);
            }
        }
        sight.settle();
        check(sight.at(sight.x + 60, sight.y + 60) == 255, "the middle of a large building lit all round is lit");
    }

    private static void whatStandsOverAClosedRoomKeepsItsLight() {
        Sight sight = new Sight();
        sight.begin(1000, 2000);
        sight.closed(1001, 2003, 2.5f);
        sight.put(1002, 2003, 0);
        sight.settle();
        int at = (2003 - sight.y) * Sight.SIDE;
        check(sight.lit.get(at + 1001 - sight.x) == 2.5f, "a closed room's square keeps the height its viewer's level ends at");
        check(sight.lit.get(at + 1002 - sight.x) == Sight.NEVER_LIT, "an open square keeps no height, so the shader darkens all of it");
        check(sight.at(1001, 2003) == 0, "the room itself still takes the dark of the squares round it");
        sight.begin(1000, 2000);
        check(sight.lit.get(at + 1001 - sight.x) == Sight.NEVER_LIT, "a new frame forgets the last one's rooms");
        check(Sight.NEVER_LIT < 65504f, "the height a half float holds");
    }

    private static void eachRingIsTheWholeBlockLookedAtAgain() {
        java.util.Random random = new java.util.Random(11);
        for (int trial = 0; trial < 3000; trial++) {
            int columns = 1 + random.nextInt(24), rows = 1 + random.nextInt(24), size = columns * rows;
            int closedOdds = random.nextInt(10), unreadOdds = random.nextInt(4);
            int[] figures = new int[size];
            for (int at = 0; at < size; at++) {
                int roll = random.nextInt(10);
                figures[at] = roll < closedOdds ? Sight.CLOSED : roll < closedOdds + unreadOdds ? Sight.UNREAD : random.nextInt(256);
            }
            int unreached = random.nextBoolean() ? 0 : Sight.UNREAD;
            int[] expected = figures.clone();
            byRings(expected, columns, rows, unreached);
            Sight.fill(figures, new int[size], new int[size], columns, rows, unreached);
            check(java.util.Arrays.equals(figures, expected), "trial " + trial + ": every square takes what a ring over the whole block gives it");
        }
    }

    /** Each ring every closed square still closed takes the mean of its sides' figures as they were at the start of the ring. */
    private static void byRings(int[] figures, int columns, int rows, int unreached) {
        int[] taken = new int[figures.length];
        while (true) {
            int reached = 0;
            for (int at = 0; at < figures.length; at++) {
                taken[at] = Sight.CLOSED;
                if (figures[at] != Sight.CLOSED) {
                    continue;
                }
                int column = at % columns, row = at / columns, sum = 0, count = 0;
                for (int beside : new int[] {column > 0 ? at - 1 : -1, column < columns - 1 ? at + 1 : -1, row > 0 ? at - columns : -1,
                        row < rows - 1 ? at + columns : -1}) {
                    if (beside >= 0 && figures[beside] >= 0) {
                        sum += figures[beside];
                        count++;
                    }
                }
                if (count > 0) {
                    taken[at] = Math.round(sum / (float) count);
                    reached++;
                }
            }
            if (reached == 0) {
                break;
            }
            for (int at = 0; at < figures.length; at++) {
                if (taken[at] != Sight.CLOSED) {
                    figures[at] = taken[at];
                }
            }
        }
        for (int at = 0; at < figures.length; at++) {
            if (figures[at] == Sight.CLOSED) {
                figures[at] = unreached;
            }
        }
    }
}
