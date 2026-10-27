package com.cameraunlock.zomboid;

/** Tests of Sight: what the game's own view keeps of each square's light, and how a closed room takes the light round it. */
final class SightTests {
    private SightTests() {}

    static void run() {
        theDarkMultiplierIsKeptAsAByte();
        aRoomTakesTheLightOfEachSideItStandsBy();
        aSquareNotReadLendsNothing();
        aRoomWithNothingRoundItIsDark();
        theBlockPutsEachSquareWhereTheShaderReadsIt();
        whatStandsOverAClosedRoomKeepsItsLight();
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
}
