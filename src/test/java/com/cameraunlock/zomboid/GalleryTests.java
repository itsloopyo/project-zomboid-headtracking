package com.cameraunlock.zomboid;

/** Tests of which model stands for a gallery's busts and statues, and where on each the colours are taken. */
final class GalleryTests {
    private GalleryTests() {}

    static void run() {
        Checks.run("everySculptureNamesAModelTheJarHolds", GalleryTests::everySculptureNamesAModelTheJarHolds);
        Checks.run("eachColourIsTakenOverThePartItColours", GalleryTests::eachColourIsTakenOverThePartItColours);
        Checks.run("theRobedMansUpperStoreyStandsNothing", GalleryTests::theRobedMansUpperStoreyStandsNothing);
        Checks.run("whatElseIsOnTheSheetIsNone", GalleryTests::whatElseIsOnTheSheetIsNone);
    }

    private static Outdoors.Piece of(int place, String called, String group, int faces) {
        return Outdoors.of(Gallery.SHEET + place, called, group, faces, 1, 1, 0);
    }

    private static void everySculptureNamesAModelTheJarHolds() {
        Object[][] busts = {{48, "Male", Outdoors.EAST}, {49, "Female", Outdoors.EAST}, {50, "Female", Outdoors.SOUTH}, {51, "Male", Outdoors.SOUTH}};
        for (Object[] bust : busts) {
            Outdoors.Piece piece = of((int) bust[0], "Bust", (String) bust[1], (int) bust[2]);
            String model = bust[1].equals("Female") ? "bust_female" : "bust_male";
            Checks.check(piece != null && piece.model.equals(model) && piece.turns == (int) bust[2], "bust " + bust[0] + " is a " + model + " turned the way it faces");
            Models.get(model);
        }
        Outdoors.Piece woman = of(52, "Female Statue", null, Outdoors.NONE), youth = of(53, "Male Statue", null, Outdoors.NONE);
        Checks.check(woman != null && woman.model.equals("statue_female") && woman.turns == Outdoors.SOUTH, "the woman's statue faces south, as her sprite does");
        Checks.check(youth != null && youth.model.equals("statue_male") && youth.turns == Outdoors.NORTH, "the youth's faces north, as his does");
        Outdoors.Piece orator = of(54, "Statue", "Gallery", Outdoors.SOUTH);
        Checks.check(orator != null && orator.model.equals("statue_orator") && orator.turns == Outdoors.SOUTH, "the robed man faces the way his sprite says");
        for (String model : new String[] {"statue_female", "statue_male", "statue_orator"}) {
            Models.get(model);
        }
    }

    private static void eachColourIsTakenOverThePartItColours() {
        float level = ViewCamera.LEVEL_HEIGHT;
        // The tops of a bust's plinth, a statue's base and the robed man's pedestal, as furniture.py makes them.
        float plinth = 1.15f, base = 0.46f, pedestal = 1.05f;
        for (String model : new String[] {"bust_male", "bust_female"}) {
            float top = Models.get(model).height();
            Checks.check(Gallery.BUST_DYE * level > plinth && Gallery.BUST_DYE * level < top, model + "'s stone is coloured from over its plinth");
        }
        Checks.check(Gallery.BUST_PLINTH_DYE * level < plinth, "and its plinth from under the bust");
        Checks.check(Gallery.STATUE_BASE_DYE * level < base && Gallery.STATUE_DYE * level > base, "a statue's base and figure from each");
        Checks.check(Gallery.ORATOR_PEDESTAL_DYE * level < pedestal && Gallery.ORATOR_DYE * level > pedestal, "the robed man's pedestal and figure from each");
    }

    private static void theRobedMansUpperStoreyStandsNothing() {
        Outdoors.Piece top = of(Gallery.ORATOR_TOP, "Statue", "Gallery", Outdoors.SOUTH);
        Checks.check(top != null && top.model == null && top.follows, "the storey over the robed man's foot is stood by his lower sprite and hidden");
        Checks.check(Models.get("statue_orator").height() > ViewCamera.LEVEL_HEIGHT, "whose model reaches up into it");
    }

    private static void whatElseIsOnTheSheetIsNone() {
        Checks.check(of(32, "Statue", "Pat Bren", Outdoors.SOUTH) == null, "another statue of the sheet");
        Checks.check(of(48, "Bust", "Male", Outdoors.NONE) == null, "a bust with no front");
        Checks.check(Outdoors.of("sheet_01_0", "Bust", "Male", Outdoors.EAST, 1, 1, 0) == null, "a bust of another sheet");
    }
}
