package com.cameraunlock.zomboid;

/** Tests of which sprites are the quarters of a parasol, and of the models that stand in their place. */
final class ParasolTests {
    private ParasolTests() {}

    static void run() {
        quarterIsWhereItsValanceFacesFromTheCorner();
        paleBlueCanopyIsOneParasolThoughItsQuartersHaveTwoKinds();
        straightCanopyIsNoParasol();
        clothIsLookedUpInsideTheSouthEastSquare();
        modelsShadeTheFourSquaresAndStandOnTheGround();
    }

    private static void quarterIsWhereItsValanceFacesFromTheCorner() {
        String[] facing = {"N", "E", "W", "S"};
        for (int quarter = Parasols.NORTH_WEST; quarter <= Parasols.SOUTH_EAST; quarter++) {
            check(Parasols.quarterOf("Canopy", "Curved black", facing[quarter]) == quarter, "the quarter that faces " + facing[quarter]);
        }
        check(Parasols.ALONG_X[Parasols.SOUTH_EAST] == 1 && Parasols.ALONG_Y[Parasols.SOUTH_EAST] == 1
                && Parasols.ALONG_X[Parasols.NORTH_EAST] == 1 && Parasols.ALONG_Y[Parasols.NORTH_EAST] == 0
                && Parasols.ALONG_X[Parasols.SOUTH_WEST] == 0 && Parasols.ALONG_Y[Parasols.SOUTH_WEST] == 1
                && Parasols.ALONG_X[Parasols.NORTH_WEST] == 0 && Parasols.ALONG_Y[Parasols.NORTH_WEST] == 0, "the four squares round the corner");
    }

    private static void paleBlueCanopyIsOneParasolThoughItsQuartersHaveTwoKinds() {
        check(Parasols.quarterOf("Canopy", "Curved light", "E") == Parasols.NORTH_EAST, "the quarter the game calls Curved light");
        check(Parasols.quarterOf("Canopy", "Curved light blue", "S") == Parasols.SOUTH_EAST, "the quarter the game calls Curved light blue");
        check(Parasols.modelOf("Curved light").equals("parasol_dome") && Parasols.modelOf("Curved light blue").equals("parasol_dome"), "the pale blue one is the dome");
        check(Parasols.modelOf("Curved red and white striped").equals("parasol"), "the rest are the cone");
    }

    private static void straightCanopyIsNoParasol() {
        check(Parasols.quarterOf("Canopy", "Black", "S") == Parasols.NONE, "a straight awning");
        check(Parasols.quarterOf("Canopy", null, "S") == Parasols.NONE, "a canopy of no kind");
        check(Parasols.quarterOf("Lights", "Curved black", "S") == Parasols.NONE, "something else of that kind");
        check(Parasols.quarterOf(null, null, null) == Parasols.NONE, "a sprite with no name");
        check(Parasols.quarterOf("Canopy", "Curved black", null) == Parasols.NONE, "a canopy that faces no way");
    }

    private static void clothIsLookedUpInsideTheSouthEastSquare() {
        float first = (float) Math.hypot(Parasols.FIRST_A, Parasols.FIRST_B), second = (float) Math.hypot(Parasols.SECOND_A, Parasols.SECOND_B);
        check(Math.abs(first - 0.8f) < 0.005f && Math.abs(second - 0.8f) < 0.005f, "both colours are taken four fifths out: " + first + ", " + second);
        double between = Math.toDegrees(Math.atan2(Parasols.SECOND_B, Parasols.SECOND_A) - Math.atan2(Parasols.FIRST_B, Parasols.FIRST_A));
        check(Math.abs(between - 20.0) < 0.5, "and 20 degrees apart: " + between);
    }

    private static void modelsShadeTheFourSquaresAndStandOnTheGround() {
        for (String name : new String[] {"parasol", "parasol_dome"}) {
            Models.Model model = Models.get(name);
            check(model.minZ == 0f && model.maxZ > 2.3f && model.maxZ < 2.6f, name + " stands on the ground and is a storey high: " + model.minZ + " to " + model.maxZ);
            check(model.maxX > 0.9f && model.maxX <= 1f && Math.abs(model.minX + model.maxX) < 0.001f && Math.abs(model.minY + model.maxY) < 0.001f,
                    name + " reaches no further than its four squares, its pole in the middle: " + model.minX + " to " + model.maxX);
            for (int surface : new int[] {Models.CLOTH, Models.LINEN}) {
                check(model.first[surface + 1] > model.first[surface], name + " has cloth of both colours");
            }
            float lowestCloth = Float.MAX_VALUE;
            for (int vertex = model.first[Models.CLOTH]; vertex < model.first[Models.LINEN + 1]; vertex++) {
                lowestCloth = Math.min(lowestCloth, model.vertices[vertex * Models.VERTEX_FLOATS + 2]);
            }
            check(lowestCloth > 1.4f, name + "'s valance is over a seated head: " + lowestCloth);
        }
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
