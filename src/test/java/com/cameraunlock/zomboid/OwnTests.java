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
            aSwitchedRowHasEachViewsWorldBuiltAgain();
            theLowGraphicsModeShowsNothingOfTheModsOwn();
            theArmsOnAHeldWeaponGoByTheirRowInEveryMode();
            customsSpritePlantsAreMediums();
            paintedLinesGoByTheFilesRowOutsideLow();
            nothingOfTheModsOwnIsShownBeforeItsPicturesAreMade();
        } finally {
            Own.picturesMade(true);
            Quality.set(Quality.HIGH);
            Own.set(before);
            Own.taken(0);
            Own.taken(1);
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
        Own.taken(0);
        Own.picturesMade(false);
        check(!Own.weapons() && !Own.plants() && !Own.textures() && !Own.buildings() && !Own.people() && Own.rows() == Own.ALL,
                "in High with its pictures still to be made every row reads off, and the file's rows are as they were");
        check(Own.taken(0) && !Own.taken(0), "and the world is built without them, once");
        Own.picturesMade(true);
        check(Own.weapons() && Own.plants() && Own.textures() && Own.taken(0) && !Own.taken(0), "made, they are shown and the world is built again, once");
    }

    /**
     * The game's own arms, moved with the weapon to before the eye, hang in the middle of the
     * picture, so Low draws the mod's own on a held weapon too. OwnArms off is the game's arms in
     * every mode, as Low drew them before, for a player whose other mods change the arms.
     */
    private static void theArmsOnAHeldWeaponGoByTheirRowInEveryMode() {
        Own.set(Own.ALL);
        Own.taken(0);
        Quality.set(Quality.LOW);
        check(Own.arms() && !Own.weapons() && !Own.people(), "in Low with OwnArms on the arms on a held weapon are the mod's, and nothing else of its own is shown");
        check(!Own.taken(0), "and they are drawn without the world being built again");
        Own.set(Own.ALL & ~Own.ARMS);
        check(!Own.arms(), "in Low with OwnArms off they are the game's own, as Low drew them before");
        Own.taken(0);
        for (int mode : new int[] {Quality.MEDIUM, Quality.HIGH, Quality.AUTOMATIC}) {
            Quality.set(mode);
            check(!Own.arms() && Own.weapons(), "OwnArms off is the game's arms in " + Quality.name() + " too, with the rest of the mod's own shown");
        }
        Own.set(Own.ALL);
        Own.picturesMade(false);
        check(Own.arms() && !Own.weapons(), "the arms are made with the core of the picture, so they are the mod's before the rest of its own is made");
        Own.picturesMade(true);
        Quality.set(Quality.LOW);
        check(Quality.plain() && Own.arms() && !Own.weapons(), "Low, the game's own look, draws the arms");
        Quality.set(Quality.HIGH);
        Own.taken(0);
    }

    private static void theLowGraphicsModeShowsNothingOfTheModsOwn() {
        Own.set(Own.ALL);
        Own.taken(0);
        Quality.set(Quality.LOW);
        check(!Own.weapons() && !Own.furniture() && !Own.pictures() && !Own.plants() && !Own.textures() && !Own.buildings() && !Own.zombies()
                && !Own.people(), "in Low every row reads off, whatever the file says");
        check(Own.taken(0) && !Own.taken(0), "and going to it has the world built again, once");
        Quality.set(Quality.AUTOMATIC);
        check(Own.plants() && Own.people() && Own.taken(0), "Automatic shows all of it again");
        Quality.set(Quality.MEDIUM);
        check(!Own.plants() && Own.furniture() && Own.textures() && Own.buildings() && Own.people() && Own.taken(0),
                "Medium shows all of it but the plants, which are the game's, and has the world built again");
        check(Quality.next() == Quality.HIGH, "the hotkey goes from Medium to High");
        Quality.set(Quality.HIGH);
        check(Own.plants() && Own.taken(0), "High has the mod's plants back");
        check(Quality.next() == Quality.CUSTOM, "from High to Custom");
        Quality.set(Quality.LOW);
        check(Quality.next() == Quality.MEDIUM, "and from Low to Medium");
    }

    private static void customsSpritePlantsAreMediums() {
        Own.set(Own.ALL);
        Quality.custom(Quality.HIGH_PICTURE);
        Quality.set(Quality.CUSTOM);
        Own.taken(0);
        check(Own.plants() && Own.textures() && Own.paint(), "Custom with High's rows shows all of the mod's own");
        double[] values = Config.values(Quality.HIGH_PICTURE);
        values[0] = 1;
        Quality.custom(Config.picture(values));
        check(!Own.plants() && Own.furniture() && Own.taken(0) && !Own.taken(0), "SpritePlants has the plants the game's, as Medium does, and the world built again once");
        Quality.custom(Quality.HIGH_PICTURE);
        Quality.set(Quality.HIGH);
        Own.taken(0);
    }

    private static void paintedLinesGoByTheFilesRowOutsideLow() {
        Own.set(Own.ALL);
        Quality.set(Quality.MEDIUM);
        Own.taken(0);
        Own.picturesMade(false);
        check(Own.paint() && !Own.textures(), "in Medium the lines of a road are laid as paint before the mod's pictures are made");
        Own.picturesMade(true);
        Own.taken(0);
        Own.set(Own.ALL & ~Own.TEXTURES);
        check(!Own.paint(), "the textures row off in the file has them drawn from the game's sprites");
        check(Own.taken(0) && !Own.taken(0), "and the world is built again for it, once");
        Own.set(Own.ALL);
        check(Own.taken(0) && Own.paint(), "the row back on has the paint back");
        Quality.set(Quality.LOW);
        check(!Own.paint(), "in Low they are the game's sprites whatever the file says");
        check(Own.taken(0) && !Own.taken(0), "and going to Low has the world built again without them, once");
        Own.set(Own.ALL & ~Own.TEXTURES);
        check(!Own.taken(0), "the textures row switched in Low changes nothing that is built");
        Own.set(Own.ALL);
        check(!Own.taken(0), "nor does it switched back");
        Quality.set(Quality.HIGH);
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
