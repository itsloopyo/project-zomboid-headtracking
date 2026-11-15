package com.cameraunlock.zomboid;

/** Tests of how high over a set its lines are written: SetCaptions.top and spriteTop against the models and the heights the game gives the sets. */
final class SetCaptionsTests {
    private SetCaptionsTests() {}

    static void run() {
        Checks.run("aSetsLinesGoOverTheTopOfItsCase", SetCaptionsTests::aSetsLinesGoOverTheTopOfItsCase);
        Checks.run("aConsolesLinesGoOverTheCabinetTheGamesSurfaceSays", SetCaptionsTests::aConsolesLinesGoOverTheCabinetTheGamesSurfaceSays);
        Checks.run("aSetWithNoModelIsToppedAsTheModelOfItsKind", SetCaptionsTests::aSetWithNoModelIsToppedAsTheModelOfItsKind);
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

    private static Furniture known(String sprite, String called, String group, int surface, boolean tableTop) {
        return Furniture.known(TileRows.sprite(sprite + "|CustomName|" + called + "|GroupName|" + group + "|Facing|S|Surface|" + surface
                + (tableTop ? "|IsTableTop||IsSurfaceOffset|" : "")));
    }

    private static Furniture set(String sprite, String called, String group, int surface, boolean tableTop) {
        Furniture piece = known(sprite, called, group, surface, tableTop);
        check(piece != null, sprite + " is one of the mod's models");
        return piece;
    }

    /** The highest point of a model's case: the vertices of the surfaces it is made of. */
    private static float caseTop(Models.Model model, int... surfaces) {
        float top = Float.NEGATIVE_INFINITY;
        for (int surface : surfaces) {
            for (int vertex = model.first[surface]; vertex < model.first[surface + 1]; vertex++) {
                top = Math.max(top, model.vertices[vertex * Models.VERTEX_FLOATS + 2]);
            }
        }
        return top;
    }

    private static void aSetsLinesGoOverTheTopOfItsCase() {
        // Sets painted for a table, 15 high for a television and 34 for the radio. A set's case is plastic or steel with rubber
        // knobs, and the radio has a metal aerial over its case, which the lines are not put over.
        Object[][] sets = {
            {set("appliances_television_01_0", "Television", "Mid-Century", 15, true), "tv", 15f, caseTop(Models.get("tv"), Models.PLASTIC, Models.RUBBER)},
            {set("appliances_television_01_4", "Television", "Premium Technologies", 15, true), "tv_wide", 15f, caseTop(Models.get("tv_wide"), Models.STEEL, Models.RUBBER)},
            {set("appliances_radio_01_0", "Radio", "Retro", 34, true), "radio", 34f, caseTop(Models.get("radio"), Models.PLASTIC, Models.RUBBER)},
        };
        check(Models.get("radio").maxZ > (Float) sets[2][3] + 0.1f, "the radio's aerial stands well over its case");
        for (Object[] each : sets) {
            Furniture piece = (Furniture) each[0];
            Models.Model model = Models.get((String) each[1]);
            check(piece.model == model, each[1] + " is the model stood for it");
            check(near(piece.scaleUp, Furniture.TILES_PER_METRE), each[1] + " is stood at its own size");
            float surface = (Float) each[2], caseTop = (Float) each[3];
            // On a low table the game lifts the set 3 more (measured in the game, 10777,9768), on a chest of drawers 19, and on the
            // floor it takes it down by its own Surface.
            for (float offset : new float[] {3f, 19f, -surface}) {
                float top = SetCaptions.top(piece, offset);
                check(near(top, metres(surface + offset) + caseTop), each[1] + " lifted " + offset + " is topped at its table and its case: " + top);
            }
        }
    }

    private static void aConsolesLinesGoOverTheCabinetTheGamesSurfaceSays() {
        float twentyFive = 0f;
        for (int surface : new int[] {25, 40}) {
            Furniture console = set("appliances_television_01_8", "Television", "Antique", surface, false);
            check(console.model == Models.get("tv_console"), "the antique console is stood as the console model");
            // Furniture makes the cabinet's top as high as the game's Surface says, and the whole model with it, unless that is
            // bigger than its square holds, as 40 is: then the model is as big as fits, as wide as it is high.
            float cabinet = console.raised + Furniture.TV_CABINET_TOP * console.scaleUp;
            check(near(console.scaleUp, console.scale), "a console is scaled alike along the ground and up");
            check(surface == 25 ? near(cabinet, metres(surface)) : cabinet <= metres(surface) + 1e-4f,
                    "a console of Surface " + surface + " has its cabinet's top at it, or as high as fits under it: " + cabinet);
            float top = SetCaptions.top(console, 0f);
            check(near(top, cabinet), "and its lines go over the cabinet, not over the aerial on it: " + top);
            if (surface == 25) {
                twentyFive = top;
            }
        }
        check(SetCaptions.top(set("appliances_television_01_8", "Television", "Antique", 40, false), 0f) > twentyFive, "a taller console's lines are higher");
    }

    private static void aSetWithNoModelIsToppedAsTheModelOfItsKind() {
        // A console painted for no cabinet height the mod has a model of, and a radio that does not go on a table, are drawn as the game's sprites.
        check(known("appliances_television_01_8", "Television", "Antique", 20, false) == null, "a console of Surface 20 has no model");
        check(near(SetCaptions.spriteTop(false, 20, 0f, true), Furniture.TV_CABINET_TOP), "and its lines go over a cabinet as high as the console model's, on the floor");
        check(known("appliances_radio_01_0", "Radio", "Retro", 34, false) == null, "a radio that goes on no table has no model");
        float radioCase = caseTop(Models.get("radio"), Models.PLASTIC, Models.RUBBER);
        check(near(SetCaptions.spriteTop(false, 34, 0f, false), radioCase), "and its lines go over a case as high as the radio model's, on the floor");
        // A set that goes on a table and has no model stands on the table its sprite was painted for, lifted as the game lifts it.
        check(near(SetCaptions.spriteTop(true, 15, 3f, true), metres(18f) + caseTop(Models.get("tv_wide"), Models.STEEL, Models.RUBBER)),
                "a table's set with no model is as tall as the larger set's model, over its table");
        check(near(SetCaptions.spriteTop(true, 34, -34f, false), radioCase), "a table's radio taken down to the floor is topped at its case");
    }
}
