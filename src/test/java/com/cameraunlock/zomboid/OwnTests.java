package com.cameraunlock.zomboid;

/** Tests of the rows of [Content]: what of the mod's own making is shown, and the world built again when a row is switched. */
final class OwnTests {
    private OwnTests() {}

    static void run() {
        Quality.set(Quality.HIGH);
        int before = Own.rows();
        try {
            everythingIsOnUntilARowSaysOtherwise();
            aSwitchedRowHasTheWorldBuiltAgainOnce();
            theLowGraphicsModeShowsNothingOfTheModsOwn();
            nothingOfTheModsOwnIsShownBeforeItsPicturesAreMade();
        } finally {
            Own.picturesMade(true);
            Quality.set(Quality.HIGH);
            Own.set(before);
            Own.taken();
        }
    }

    private static void everythingIsOnUntilARowSaysOtherwise() {
        Own.set(Own.ALL);
        check(Own.weapons() && Own.furniture() && Own.pictures() && Own.plants() && Own.textures() && Own.buildings() && Own.zombies()
                && Own.arms(), "with every row on, all of it is shown");
        Own.set(Own.ALL & ~Own.WEAPONS);
        check(!Own.weapons() && Own.furniture() && Own.pictures() && Own.plants() && Own.textures() && Own.buildings() && Own.zombies(),
                "the weapons row off takes the mod's weapons away and nothing else");
        Own.set(Own.ALL & ~Own.ARMS);
        check(!Own.arms() && Own.weapons() && Own.people(), "the arms row off takes the mod's arms away, and leaves its weapons and the skins of the living");
        Own.set(Own.PICTURES);
        check(Own.pictures() && !Own.weapons() && !Own.furniture() && !Own.plants() && !Own.textures() && !Own.buildings() && !Own.zombies(),
                "one row on shows that and nothing else");
    }

    private static void nothingOfTheModsOwnIsShownBeforeItsPicturesAreMade() {
        Quality.set(Quality.HIGH);
        Own.set(Own.ALL);
        Own.taken();
        Own.picturesMade(false);
        check(!Own.weapons() && !Own.plants() && !Own.textures() && !Own.buildings() && !Own.people() && Own.rows() == Own.ALL,
                "in High with its pictures still to be made every row reads off, and the file's rows are as they were");
        check(Own.taken() && !Own.taken(), "and the world is built without them, once");
        Own.picturesMade(true);
        check(Own.weapons() && Own.plants() && Own.textures() && Own.taken() && !Own.taken(), "made, they are shown and the world is built again, once");
    }

    private static void theLowGraphicsModeShowsNothingOfTheModsOwn() {
        Own.set(Own.ALL);
        Own.taken();
        Quality.set(Quality.LOW);
        check(!Own.weapons() && !Own.furniture() && !Own.pictures() && !Own.plants() && !Own.textures() && !Own.buildings() && !Own.zombies()
                && !Own.people(), "in Low every row reads off, whatever the file says");
        check(Own.taken() && !Own.taken(), "and going to it has the world built again, once");
        Quality.set(Quality.AUTOMATIC);
        check(Own.plants() && Own.people() && Own.taken(), "Automatic shows all of it again");
        Quality.set(Quality.MEDIUM);
        check(!Own.plants() && Own.furniture() && Own.textures() && Own.buildings() && Own.people() && Own.taken(),
                "Medium shows all of it but the plants, which are the game's, and has the world built again");
        check(Quality.next() == Quality.HIGH, "the hotkey goes from Medium to High");
        Quality.set(Quality.HIGH);
        check(Own.plants() && Own.taken(), "High has the mod's plants back");
        check(Quality.next() == Quality.LOW, "from High round to Low");
        Quality.set(Quality.LOW);
        check(Quality.next() == Quality.MEDIUM, "and from Low to Medium");
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
