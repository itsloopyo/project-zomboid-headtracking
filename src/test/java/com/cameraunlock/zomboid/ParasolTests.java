package com.cameraunlock.zomboid;

import java.util.HashMap;

/** Tests of which sprites are the quarters of a parasol, and of the models that stand in their place. */
final class ParasolTests {
    private ParasolTests() {}

    static void run() {
        quarterIsWhereItsValanceFacesFromTheCorner();
        paleBlueCanopyIsOneParasolThoughItsQuartersHaveTwoKinds();
        straightCanopyIsNoParasol();
        everyPartOfAWholeParasolFindsItsSouthEastQuarterAndThatOneAloneStandsIt();
        poleIsTheCornerPostOnTheSouthEastSquareAndNoOther();
        canopyWithAQuarterMissingOrOutOfPlaceIsNoParasol();
        clothIsLookedUpInsideTheSouthEastSquareWhereTheModelHasCloth();
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

    /** A level of a made-up map: what is laid as which quarter on which square. */
    private static final class Yard implements Parasols.Squares<String> {
        private final HashMap<String, String> laid = new HashMap<>();

        Yard lay(String what, int quarter, int x, int y) {
            laid.put(x + "," + y + "," + quarter, what);
            return this;
        }

        void lift(int quarter, int x, int y) {
            laid.remove(x + "," + y + "," + quarter);
        }

        @Override
        public String holds(int x, int y, int quarter) {
            return laid.get(x + "," + y + "," + quarter);
        }
    }

    /** A parasol whose pole stands at the corner the squares (x - 1, y - 1) and (x, y) meet at: the quarter that faces north lies north-west of it, east north-east, west south-west and south south-east. */
    private static Yard parasol(Yard yard, String name, int x, int y) {
        return yard.lay(name + " north", Parasols.NORTH_WEST, x - 1, y - 1).lay(name + " east", Parasols.NORTH_EAST, x, y - 1)
                .lay(name + " west", Parasols.SOUTH_WEST, x - 1, y).lay(name + " south", Parasols.SOUTH_EAST, x, y);
    }

    private static final int[] QUARTERS = {Parasols.NORTH_WEST, Parasols.NORTH_EAST, Parasols.SOUTH_WEST, Parasols.SOUTH_EAST};
    /** Where each of QUARTERS lies from the south-east square. */
    private static final int[] WEST = {1, 0, 1, 0}, NORTH = {1, 1, 0, 0};

    private static void everyPartOfAWholeParasolFindsItsSouthEastQuarterAndThatOneAloneStandsIt() {
        Yard yard = parasol(new Yard(), "black", 5, 7);
        int stood = 0;
        for (int part = 0; part < 4; part++) {
            int x = 5 - WEST[part], y = 7 - NORTH[part];
            String front = Parasols.front(yard, QUARTERS[part], false, x, y);
            check("black south".equals(front), "quarter " + QUARTERS[part] + " on " + x + ", " + y + " is part of the parasol at 5, 7: " + front);
            // WorldScene.addParasol stands the model for the object that is the answer, and adds nothing for any other part.
            stood += yard.holds(x, y, QUARTERS[part]).equals(front) ? 1 : 0;
        }
        check(stood == 1, "one quarter of the four stands the parasol: " + stood);
    }

    private static void poleIsTheCornerPostOnTheSouthEastSquareAndNoOther() {
        Yard yard = parasol(new Yard(), "black", 5, 7);
        check("black south".equals(Parasols.front(yard, Parasols.NONE, true, 5, 7)), "a corner post on the south-east square is the pole");
        for (int x = 3; x <= 7; x++) {
            for (int y = 5; y <= 9; y++) {
                check(x == 5 && y == 7 || Parasols.front(yard, Parasols.NONE, true, x, y) == null, "a corner post on " + x + ", " + y + " is a wall's");
                check(Parasols.front(yard, Parasols.NONE, false, x, y) == null, "what is neither a quarter nor a corner post is no part of a parasol: " + x + ", " + y);
            }
        }
    }

    private static void canopyWithAQuarterMissingOrOutOfPlaceIsNoParasol() {
        for (int missing = 0; missing < 4; missing++) {
            Yard yard = parasol(new Yard(), "torn", 5, 7);
            yard.lift(QUARTERS[missing], 5 - WEST[missing], 7 - NORTH[missing]);
            for (int part = 0; part < 4; part++) {
                check(Parasols.front(yard, QUARTERS[part], false, 5 - WEST[part], 7 - NORTH[part]) == null,
                        "with quarter " + QUARTERS[missing] + " missing, quarter " + QUARTERS[part] + " is left to be drawn as it is");
            }
            check(Parasols.front(yard, Parasols.NONE, true, 5, 7) == null, "and the post is a post: quarter " + QUARTERS[missing] + " missing");
        }
        // The four quarters round a corner the other way about, each facing in: the corners of four awnings, not one parasol.
        Yard inward = new Yard().lay("a", Parasols.SOUTH_EAST, 4, 6).lay("b", Parasols.SOUTH_WEST, 5, 6).lay("c", Parasols.NORTH_EAST, 4, 7).lay("d", Parasols.NORTH_WEST, 5, 7);
        check(Parasols.front(inward, Parasols.SOUTH_EAST, false, 4, 6) == null && Parasols.front(inward, Parasols.SOUTH_WEST, false, 5, 6) == null
                && Parasols.front(inward, Parasols.NORTH_EAST, false, 4, 7) == null && Parasols.front(inward, Parasols.NORTH_WEST, false, 5, 7) == null,
                "four quarters that face in round a corner are no parasol");
    }

    /**
     * The cloth's two colours are read off the sprite of the south-east quarter, at the texels
     * that show two points of the canopy over that square. The model that is dyed with them is
     * the shape read off the same sprites, so its cloth is at those points too.
     */
    private static void clothIsLookedUpInsideTheSouthEastSquareWhereTheModelHasCloth() {
        Models.Model model = Models.get("parasol");
        float[][] points = {{Parasols.FIRST_A, Parasols.FIRST_B}, {Parasols.SECOND_A, Parasols.SECOND_B}};
        check(Math.hypot(points[0][0] - points[1][0], points[0][1] - points[1][1]) > 0.2, "the two colours are read at two places");
        // Round from east, the striped canopies' sprites have one colour's stripe over 40 to 50 degrees on all three and the
        // other's over 60 to 65: blue and red stripes run 55 to 65, green and white's white from 60.
        double first = Math.toDegrees(Math.atan2(Parasols.FIRST_B, Parasols.FIRST_A)), second = Math.toDegrees(Math.atan2(Parasols.SECOND_B, Parasols.SECOND_A));
        check(first > 42.5 && first < 47.5 && second > 61 && second < 64, "each colour is read in the middle of its stripe: " + first + ", " + second);
        for (float[] point : points) {
            check(point[0] > 0f && point[0] < 1f && point[1] > 0f && point[1] < 1f, "a colour is read over the south-east square: " + point[0] + ", " + point[1]);
            float out = (float) Math.hypot(point[0], point[1]), nearest = Float.MAX_VALUE, clothAt = 0f;
            for (int vertex = model.first[Models.CLOTH]; vertex < model.first[Models.LINEN + 1]; vertex++) {
                float[] v = model.vertices;
                int at = vertex * Models.VERTEX_FLOATS;
                float off = Math.abs((float) Math.hypot(v[at], v[at + 1]) - out);
                // The cloth has an upper and an under side a few millimetres apart, and the valance hangs from its edge: the upper side is the highest.
                if (off < nearest - 0.02f || off < nearest + 0.02f && v[at + 2] > clothAt) {
                    nearest = Math.min(nearest, off);
                    clothAt = v[at + 2];
                }
            }
            check(nearest < 0.1f && Math.abs(clothAt / ViewCamera.LEVEL_HEIGHT - Parasols.CLOTH_HIGH) < 0.04f,
                    "a colour is read at the height the canopy is at, that far from the pole: " + clothAt / ViewCamera.LEVEL_HEIGHT + " levels at " + out + " tiles");
        }
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
