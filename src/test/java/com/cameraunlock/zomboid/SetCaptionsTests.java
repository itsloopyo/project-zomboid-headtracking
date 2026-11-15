package com.cameraunlock.zomboid;

/** Tests of how high over a set its lines are written: SetCaptions.top against the models the mod stands the sets as. */
final class SetCaptionsTests {
    private SetCaptionsTests() {}

    static void run() {
        Checks.run("aSetsLinesGoOverTheTopOfItsModel", SetCaptionsTests::aSetsLinesGoOverTheTopOfItsModel);
        Checks.run("aConsolesLinesGoOverTheCabinetTheGamesSurfaceSays", SetCaptionsTests::aConsolesLinesGoOverTheCabinetTheGamesSurfaceSays);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    /** Pixels of the game's surfaces as metres. */
    private static float metres(float pixels) {
        return pixels / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
    }

    private static Furniture set(String sprite, String called, String group, String surface, boolean tableTop) {
        Furniture piece = Furniture.known(TileRows.sprite(sprite + "|CustomName|" + called + "|GroupName|" + group + "|Facing|S|Surface|" + surface
                + (tableTop ? "|IsTableTop||IsSurfaceOffset|" : "")));
        check(piece != null, sprite + " is one of the mod's models");
        return piece;
    }

    private static void aSetsLinesGoOverTheTopOfItsModel() {
        // Sets painted for a table, 15 high for a television and 34 for the radio, each a model no bigger than its square.
        Object[][] sets = {
            {set("appliances_television_01_0", "Television", "Mid-Century", "15", true), "tv", 15f},
            {set("appliances_television_01_4", "Television", "Premium Technologies", "15", true), "tv_wide", 15f},
            {set("appliances_radio_01_0", "Radio", "Retro", "34", true), "radio", 34f},
        };
        for (Object[] each : sets) {
            Furniture piece = (Furniture) each[0];
            Models.Model model = Models.get((String) each[1]);
            check(piece.model == model, each[1] + " is the model stood for it");
            check(near(piece.scaleUp, Furniture.TILES_PER_METRE), each[1] + " is stood at its own size");
            float surface = (Float) each[2];
            // On a low table the game lifts the set 3 more (measured in the game, 10777,9768); on the floor it takes it down by its own Surface.
            for (float offset : new float[] {3f, 19f, -surface}) {
                float top = SetCaptions.top(piece, offset);
                check(near(top, metres(surface + offset) + model.maxZ), each[1] + " lifted " + offset + " is topped at its table and its model's height: " + top);
            }
        }
        Furniture tv = (Furniture) sets[0][0];
        check(SetCaptions.top(tv, 19f) > SetCaptions.top(tv, 3f), "on a higher piece the same set's top is higher");
        check(near(SetCaptions.top((Furniture) sets[2][0], -34f), Models.get("radio").maxZ), "a radio on the floor is topped at its own height over the floor");
    }

    private static void aConsolesLinesGoOverTheCabinetTheGamesSurfaceSays() {
        float twentyFive = 0f;
        for (int surface : new int[] {25, 40}) {
            Furniture console = set("appliances_television_01_8", "Television", "Antique", Integer.toString(surface), false);
            check(console.model == Models.get("tv_console"), "the antique console is stood as the console model");
            // Furniture makes the cabinet's top as high as the game's Surface says, and the whole model with it, unless that is
            // bigger than its square holds, as 40 is: then the model is as big as fits, as wide as it is high.
            float cabinet = console.raised + 0.78f * console.scaleUp;
            check(near(console.scaleUp, console.scale), "a console is scaled alike along the ground and up");
            check(surface == 25 ? near(cabinet, metres(surface)) : cabinet <= metres(surface) + 1e-4f,
                    "a console of Surface " + surface + " has its cabinet's top at it, or as high as fits under it: " + cabinet);
            float top = SetCaptions.top(console, 0f);
            check(near(top, cabinet / 0.78f * console.model.maxZ), "and its lines go over the top of the model at that scale: " + top);
            if (surface == 25) {
                twentyFive = top;
            }
        }
        check(SetCaptions.top(set("appliances_television_01_8", "Television", "Antique", "40", false), 0f) > twentyFive,
                "a taller console's lines are higher");
    }
}
