package com.cameraunlock.zomboid;

/** Tests of the rows of [Content]: what of the mod's own making is shown, and the world built again when a row is switched. */
final class OwnTests {
    private OwnTests() {}

    static void run() {
        Quality.set(0, Quality.HIGH);
        int before = Own.rows();
        try {
            everythingIsOnUntilARowSaysOtherwise();
            aSwitchedRowHasTheWorldBuiltAgainOnce();
            aSwitchedRowHasEachViewsWorldBuiltAgain();
            theLowGraphicsModeShowsOnlyTheFurnitureAndBuildingsOfTheModsOwn();
            theArmsOnAHeldWeaponGoByTheirRowInEveryMode();
            customsLowTreeQualityHasTheGamesPlants();
            paintedLinesGoByTheFilesRowOutsideLow();
            onlyTheFurnitureAndBuildingsAreShownBeforeThePicturesAreMade();
        } finally {
            Own.picturesMade(true);
            Quality.set(0, Quality.HIGH);
            Own.set(before);
            Own.taken(0);
            Own.taken(1);
        }
    }

    private static void everythingIsOnUntilARowSaysOtherwise() {
        Own.set(Own.ALL);
        check(Own.weapons(0) && Own.furniture() && Own.pictures(0) && Own.plants(0) && Own.textures(0) && Own.buildings() && Own.zombies(0)
                && Own.arms(), "with every row on, all of it is shown");
        Own.set(Own.ALL & ~Own.WEAPONS);
        check(!Own.weapons(0) && Own.furniture() && Own.pictures(0) && Own.plants(0) && Own.textures(0) && Own.buildings() && Own.zombies(0),
                "the weapons row off takes the mod's weapons away and nothing else");
        Own.set(Own.ALL & ~Own.ARMS);
        check(!Own.arms() && Own.weapons(0) && Own.people(0), "the arms row off takes the mod's arms away, and leaves its weapons and the skins of the living");
        Own.set(Own.PICTURES);
        check(Own.pictures(0) && !Own.weapons(0) && !Own.furniture() && !Own.plants(0) && !Own.textures(0) && !Own.buildings() && !Own.zombies(0),
                "one row on shows that and nothing else");
    }

    private static void onlyTheFurnitureAndBuildingsAreShownBeforeThePicturesAreMade() {
        Quality.set(0, Quality.HIGH);
        Own.set(Own.ALL);
        Own.taken(0);
        Own.picturesMade(false);
        check(!Own.weapons(0) && !Own.plants(0) && !Own.textures(0) && !Own.pictures(0) && !Own.people(0) && Own.rows() == Own.ALL,
                "in High with its pictures still to be made every row that needs them reads off, and the file's rows are as they were");
        check(Own.furniture() && Own.buildings(), "and the furniture and buildings, which need none of them, are shown");
        check(Own.taken(0) && !Own.taken(0), "and the world is built without the rest, once");
        Own.picturesMade(true);
        check(Own.weapons(0) && Own.plants(0) && Own.textures(0) && Own.taken(0) && !Own.taken(0), "made, they are shown and the world is built again, once");
    }

    /**
     * The game's own arms, moved with the weapon to before the eye, hang in the middle of the
     * picture, so Low draws the mod's own on a held weapon too. OwnArms off is the game's arms in
     * every mode, as Low drew them before, for a player whose other mods change the arms.
     */
    private static void theArmsOnAHeldWeaponGoByTheirRowInEveryMode() {
        Own.set(Own.ALL);
        Own.taken(0);
        Quality.set(0, Quality.LOW);
        check(Own.arms() && !Own.weapons(0) && !Own.people(0), "in Low with OwnArms on the arms on a held weapon are the mod's, and nothing else of its own is shown");
        check(!Own.taken(0), "and they are drawn without the world being built again");
        Own.set(Own.ALL & ~Own.ARMS);
        check(!Own.arms(), "in Low with OwnArms off they are the game's own, as Low drew them before");
        Own.taken(0);
        for (int mode : new int[] {Quality.MEDIUM, Quality.HIGH, Quality.AUTOMATIC}) {
            Quality.set(0, mode);
            check(!Own.arms() && Own.weapons(0), "OwnArms off is the game's arms in " + Quality.name(0) + " too, with the rest of the mod's own shown");
        }
        Own.set(Own.ALL);
        Own.picturesMade(false);
        check(Own.arms() && !Own.weapons(0), "the arms are made with the core of the picture, so they are the mod's before the rest of its own is made");
        Own.picturesMade(true);
        Quality.set(0, Quality.LOW);
        check(Quality.plain(0) && Own.arms() && !Own.weapons(0), "Low, the game's own look, draws the arms");
        Quality.set(0, Quality.HIGH);
        Own.taken(0);
    }

    private static void theLowGraphicsModeShowsOnlyTheFurnitureAndBuildingsOfTheModsOwn() {
        Own.set(Own.ALL);
        Own.taken(0);
        Quality.set(0, Quality.LOW);
        check(!Own.weapons(0) && !Own.pictures(0) && !Own.plants(0) && !Own.textures(0) && !Own.zombies(0) && !Own.people(0),
                "in Low every row that needs the mod's pictures reads off, whatever the file says");
        check(Own.furniture() && Own.buildings(), "and the furniture and the windows and doors are the mod's");
        check(Own.taken(0) && !Own.taken(0), "and going to it has the world built again, once");
        Own.set(Own.ALL & ~Own.FURNITURE & ~Own.BUILDINGS);
        check(!Own.furniture() && !Own.buildings() && Own.taken(0) && !Own.taken(0),
                "in Low the furniture and buildings rows off are the game's sprites, and the world is built again once");
        Own.set(Own.ALL);
        check(Own.taken(0) && Own.furniture(), "and back on they are the mod's again");
        Own.set(Own.ALL & ~Own.TEXTURES);
        check(!Own.taken(0), "a row Low does not show switched in Low changes nothing that is built");
        Own.set(Own.ALL);
        Own.taken(0);
        Quality.set(0, Quality.AUTOMATIC);
        check(Own.plants(0) && Own.people(0) && Own.taken(0), "Automatic shows all of it again");
        Quality.set(0, Quality.MEDIUM);
        check(Own.plants(0) && Own.furniture() && Own.textures(0) && Own.buildings() && Own.people(0) && !Own.taken(0),
                "Medium shows all of it, its trees cheaper, and leaves the world as Automatic built it");
        check(Quality.next(0) == Quality.HIGH, "the hotkey goes from Medium to High");
        Quality.set(0, Quality.HIGH);
        check(Own.plants(0) && !Own.taken(0), "High shows the same of it, with no build again");
        check(Quality.next(0) == Quality.CUSTOM, "from High to Custom");
        Quality.set(0, Quality.LOW);
        check(Quality.next(0) == Quality.MEDIUM, "and from Low to Medium");
    }

    private static void customsLowTreeQualityHasTheGamesPlants() {
        Own.set(Own.ALL);
        Quality.custom(Quality.HIGH_PICTURE);
        Quality.set(0, Quality.CUSTOM);
        Own.taken(0);
        check(Own.plants(0) && Own.textures(0) && Own.paint(0), "Custom with High's rows shows all of the mod's own");
        double[] values = Config.values(Quality.HIGH_PICTURE);
        values[Config.graphicsPart("TreeQuality")] = Quality.TREES_SPRITES;
        Quality.custom(Config.picture(values));
        check(!Own.plants(0) && Own.furniture() && Own.taken(0) && !Own.taken(0), "TreeQuality at Low has the plants the game's, and the world built again once");
        Quality.custom(Quality.HIGH_PICTURE);
        Quality.set(0, Quality.HIGH);
        Own.taken(0);
    }

    private static void paintedLinesGoByTheFilesRowOutsideLow() {
        Own.set(Own.ALL);
        Quality.set(0, Quality.MEDIUM);
        Own.taken(0);
        Own.picturesMade(false);
        check(Own.paint(0) && !Own.textures(0), "in Medium the lines of a road are laid as paint before the mod's pictures are made");
        Own.picturesMade(true);
        Own.taken(0);
        Own.set(Own.ALL & ~Own.TEXTURES);
        check(!Own.paint(0), "the textures row off in the file has them drawn from the game's sprites");
        check(Own.taken(0) && !Own.taken(0), "and the world is built again for it, once");
        Own.set(Own.ALL);
        check(Own.taken(0) && Own.paint(0), "the row back on has the paint back");
        Quality.set(0, Quality.LOW);
        check(!Own.paint(0), "in Low they are the game's sprites whatever the file says");
        check(Own.taken(0) && !Own.taken(0), "and going to Low has the world built again without them, once");
        Own.set(Own.ALL & ~Own.TEXTURES);
        check(!Own.taken(0), "the textures row switched in Low changes nothing that is built");
        Own.set(Own.ALL);
        check(!Own.taken(0), "nor does it switched back");
        Quality.set(0, Quality.HIGH);
        Own.taken(0);
    }

    private static void aSwitchedRowHasTheWorldBuiltAgainOnce() {
        Own.set(Own.ALL);
        Own.taken(0);
        check(!Own.taken(0), "rows that have not changed leave the world as it is built");
        Own.set(Own.ALL & ~Own.PLANTS);
        check(Own.taken(0), "a row switched off has it built again");
        check(!Own.taken(0), "once");
        Own.set(Own.ALL);
        check(Own.taken(0) && !Own.taken(0), "and switched back on, once more");
    }

    private static void aSwitchedRowHasEachViewsWorldBuiltAgain() {
        Own.set(Own.ALL);
        Own.taken(0);
        Own.taken(1);
        Own.set(Own.ALL & ~Own.FURNITURE);
        check(Own.taken(0) && Own.taken(1), "a row switched has the world of each view built again");
        check(!Own.taken(0) && !Own.taken(1), "each of them once");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}
