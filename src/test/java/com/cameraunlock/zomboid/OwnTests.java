package com.cameraunlock.zomboid;

/** Tests of the rows of [Content]: what of the mod's own making is shown, and the world built again when a row is switched. */
final class OwnTests {
    private OwnTests() {}

    static void run() {
        int before = Own.on();
        try {
            everythingIsOnUntilARowSaysOtherwise();
            aSwitchedRowHasTheWorldBuiltAgainOnce();
        } finally {
            Own.set(before);
            Own.taken();
        }
    }

    private static void everythingIsOnUntilARowSaysOtherwise() {
        Own.set(Own.ALL);
        check(Own.weapons() && Own.furniture() && Own.pictures() && Own.plants() && Own.textures() && Own.buildings(), "with every row on, all of it is shown");
        Own.set(Own.ALL & ~Own.WEAPONS);
        check(!Own.weapons() && Own.furniture() && Own.pictures() && Own.plants() && Own.textures() && Own.buildings(),
                "the weapons row off takes the mod's weapons away and nothing else");
        Own.set(Own.PICTURES);
        check(Own.pictures() && !Own.weapons() && !Own.furniture() && !Own.plants() && !Own.textures() && !Own.buildings(),
                "one row on shows that and nothing else");
    }

    private static void aSwitchedRowHasTheWorldBuiltAgainOnce() {
        Own.set(Own.ALL);
        Own.taken();
        check(!Own.taken(), "rows that have not changed leave the world as it is built");
        Own.set(Own.ALL & ~Own.PLANTS);
        check(Own.taken(), "a row switched off has it built again");
        check(!Own.taken(), "once");
        Own.set(Own.ALL);
        check(Own.taken() && !Own.taken(), "and switched back on, once more");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
