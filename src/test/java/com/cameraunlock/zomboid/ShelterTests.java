package com.cameraunlock.zomboid;

/** Tests of the block of squares rain and snow are kept out of with no roof drawn: see Shelter. */
final class ShelterTests {
    private ShelterTests() {}

    static void run() {
        Checks.run("aRoomsSquaresAreCoveredAndTheSquaresBesideThemAreNot", ShelterTests::aRoomsSquaresAreCoveredAndTheSquaresBesideThemAreNot);
        Checks.run("aRoomThatRunsOffTheBlockCoversWhatOfItIsOnIt", ShelterTests::aRoomThatRunsOffTheBlockCoversWhatOfItIsOnIt);
        Checks.run("aBuildingWithNothingOverTheGroundCoversNothing", ShelterTests::aBuildingWithNothingOverTheGroundCoversNothing);
        Checks.run("anotherBlockIsMadeOnceTheViewerHasStrayed", ShelterTests::anotherBlockIsMadeOnceTheViewerHasStrayed);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void aRoomsSquaresAreCoveredAndTheSquaresBesideThemAreNot() {
        Shelter shelter = new Shelter(10700, 9960);
        shelter.cover(10690, 9955, 6, 4, 2);
        for (int y = 9954; y <= 9959; y++) {
            for (int x = 10689; x <= 10696; x++) {
                boolean inside = x >= 10690 && x < 10696 && y >= 9955 && y < 9959;
                check(shelter.at(x, y) == (inside ? 2 : 0), "square " + x + ", " + y + " is " + (inside ? "under the room's two storeys" : "in the open"));
            }
        }
    }

    private static void aRoomThatRunsOffTheBlockCoversWhatOfItIsOnIt() {
        Shelter shelter = new Shelter(100, 100);
        shelter.cover(shelter.x - 5, shelter.y + Shelter.SIDE - 2, 10, 10, 1);
        check(shelter.at(shelter.x, shelter.y + Shelter.SIDE - 1) == 1, "the corner of the room that is on the block is covered");
        check(shelter.at(shelter.x + 5, shelter.y + Shelter.SIDE - 1) == 0, "the square past the room's side is not");
        check(shelter.at(shelter.x - 1, shelter.y + Shelter.SIDE - 1) == 0 && shelter.at(shelter.x, shelter.y + Shelter.SIDE) == 0,
                "and a square off the block is read as open");
        shelter.cover(shelter.x + 20, shelter.y + 20, 1, 1, 400);
        check(shelter.at(shelter.x + 20, shelter.y + 20) == 255, "more storeys than a byte counts are as many as it does count");
    }

    private static void aBuildingWithNothingOverTheGroundCoversNothing() {
        Shelter shelter = new Shelter(0, 0);
        shelter.cover(-3, -3, 6, 6, 0);
        check(shelter.at(0, 0) == 0, "a cellar with no storey over it keeps nothing off the ground above");
    }

    private static void anotherBlockIsMadeOnceTheViewerHasStrayed() {
        Shelter shelter = new Shelter(500, 700);
        check(!shelter.strayedFrom(500, 700) && !shelter.strayedFrom(500 + Shelter.STRAY, 700 - Shelter.STRAY), "near the block's middle it is kept");
        check(shelter.strayedFrom(500 + Shelter.STRAY + 1, 700) && shelter.strayedFrom(500, 700 - Shelter.STRAY - 1), "a square further and another is made");
        check(Shelter.STRAY + 60 <= Shelter.SIDE / 2, "a viewer who has not strayed still has sixty squares of block every way");
    }
}
