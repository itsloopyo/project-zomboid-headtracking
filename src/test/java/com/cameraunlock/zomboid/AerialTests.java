package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tests of the dishes and aerials the game paints on roofs and walls: which sprites stand which model, where, and how
 * a thing painted over a roof's level is stood on the roof. The rows are the tiles' own, sounds left out.
 */
final class AerialTests {
    private AerialTests() {}

    static void run() {
        Checks.run("aBigDishIsOneModelStoodByItsPedestalsSprite", AerialTests::aBigDishIsOneModelStoodByItsPedestalsSprite);
        Checks.run("aBigDishWithASpriteMissingIsLeftAsTheGameDrawsIt", AerialTests::aBigDishWithASpriteMissingIsLeftAsTheGameDrawsIt);
        Checks.run("aHousesDishIsAModelWithItsBackToTheWallBehindIt", AerialTests::aHousesDishIsAModelWithItsBackToTheWallBehindIt);
        Checks.run("aTelevisionAerialIsStoodByOneOfItsTwoSpritesOnTheRoof", AerialTests::aTelevisionAerialIsStoodByOneOfItsTwoSpritesOnTheRoof);
        Checks.run("whatIsPaintedOverARoofsLevelStandsOnTheRoofWhereTheViewDrawsIt", AerialTests::whatIsPaintedOverARoofsLevelStandsOnTheRoofWhereTheViewDrawsIt);
        Checks.run("theModelsStandOnTheirFeet", AerialTests::theModelsStandOnTheirFeet);
    }

    private static final String SHEET = "appliances_com_01_";
    private static final String DISH = "|BlocksPlacement||CanScrap||CustomName|Dish|GroupName|Satellite|IsMoveAble||Material|AluminumScrap|Material2|Electric|"
            + "Material3|Transmission|MaterialType|Metal_Light|MoveType|WallObject|PickUpLevel|2|PickUpTool|Electrician|PickUpWeight|200|PlaceTool|Electrician|Facing|";
    private static final String AERIAL = "|BlocksPlacement||CanScrap||CustomName|Antenna|ForceSingleItem||GroupName|Old TV|IsMoveAble||Material|AluminumScrap|"
            + "Material2|MetalBars|PickUpTool|Electrician|PickUpWeight|20|PlaceTool|Electrician|";

    /** A made-up map: the names of the sprites on each square. */
    private static final class Squares implements Towers.Squares {
        final Map<String, Set<String>> on = new HashMap<>();

        void put(String sprite, int x, int y, int z) {
            on.computeIfAbsent(x + "," + y + "," + z, k -> new HashSet<>()).add(sprite);
        }

        void remove(String sprite, int x, int y, int z) {
            on.get(x + "," + y + "," + z).remove(sprite);
        }

        @Override
        public Boolean holds(int x, int y, int z, String sprite) {
            return on.getOrDefault(x + "," + y + "," + z, Set.of()).contains(sprite);
        }
    }

    /** The big dish on the skyscraper's roof in Louisville, where the map lays it. */
    private static Squares towerDish() {
        Squares map = new Squares();
        map.put(SHEET + 38, 12785, 1536, 26);
        map.put(SHEET + 36, 12784, 1537, 26);
        map.put(SHEET + 37, 12785, 1537, 26);
        map.put(SHEET + 30, 12785, 1536, 27);
        map.put(SHEET + 28, 12784, 1537, 27);
        map.put(SHEET + 29, 12785, 1537, 27);
        return map;
    }

    private static void aBigDishIsOneModelStoodByItsPedestalsSprite() {
        Squares map = towerDish();
        int stood = 0, dealt = 0;
        for (Map.Entry<String, Set<String>> square : map.on.entrySet()) {
            String[] at = square.getKey().split(",");
            int x = Integer.parseInt(at[0]), y = Integer.parseInt(at[1]), z = Integer.parseInt(at[2]);
            for (String sprite : square.getValue()) {
                Towers.Part[] parts = Towers.of(sprite);
                check(parts.length == 1 && parts[0].section().model().equals("satellite_dish_large"), sprite + " is a square of the big dish");
                Towers.Part part = parts[0];
                if (Towers.whole(map, part.section(), x - part.dx(), y - part.dy(), z - part.dz()) == Towers.WHOLE) {
                    dealt++;
                    if (part.dx() == 0 && part.dy() == 0 && part.dz() == 0 && part.section().anchor().equals(sprite)) {
                        stood++;
                    }
                }
            }
        }
        check(dealt == 6 && stood == 1, "all six sprites are the one dish, stood once: " + dealt + " dealt with, " + stood + " stood");
        Towers.Section dish = Towers.of(SHEET + 38)[0].section();
        check(dish.turns() == Outdoors.SOUTH, "it looks south, as the game says it faces");
        check(dish.a() > 0f && dish.a() < 1f && dish.b() > 0f && dish.b() < 1f, "its pedestal stands on its anchor's square");
    }

    private static void aBigDishWithASpriteMissingIsLeftAsTheGameDrawsIt() {
        Squares map = towerDish();
        map.remove(SHEET + 29, 12785, 1537, 27);
        Towers.Section dish = Towers.of(SHEET + 38)[0].section();
        check(Towers.whole(map, dish, 12785, 1536, 26) == Towers.BROKEN, "a dish with a square of it gone is not whole, and its sprites stay");
    }

    private static void aHousesDishIsAModelWithItsBackToTheWallBehindIt() {
        for (String[] dish : new String[][] {{"20", "S", "2"}, {"21", "E", "1"}}) {
            Furniture piece = Furniture.known(TileRows.sprite(SHEET + dish[0] + DISH + dish[1]));
            check(piece != null && piece.model != null && piece.model.name.equals("satellite_dish"), SHEET + dish[0] + " is the house's dish");
            if (piece == null) {
                continue;
            }
            check(piece.wall == WallHung.NONE && piece.turns == Integer.parseInt(dish[2]), "it stands looking the way it faces, " + dish[1]);
            float[] front = new float[2];
            Models.turn(piece.turns, 0f, -1f, front);
            float back = 0.5f + (front[0] != 0f ? front[0] * (piece.centreX - 0.5f) : front[1] * (piece.centreY - 0.5f)) - piece.model.depth() * piece.scale / 2f;
            check(back > 0.03f && back < 0.1f, "its back is against the wall behind it: " + back);
            check(piece.raised > 0.85f && piece.raised < 1f, "hung as high as its sprite paints it: " + piece.raised);
            check(piece.onRoof && piece.posted != null && piece.posted.name.equals("satellite_dish_post"), "on a roof it is the dish on a post, stood on the roof");
            check(piece.posted != null && Math.abs(piece.posted.height() - (piece.raised + piece.model.height())) < 0.02f,
                    "whose post reaches from the roof to where the dish hangs over it: " + (piece.posted == null ? 0f : piece.posted.height()));
        }
    }

    private static void aTelevisionAerialIsStoodByOneOfItsTwoSpritesOnTheRoof() {
        Furniture south = Furniture.known(TileRows.sprite(SHEET + 81 + AERIAL + "Facing|S|SpriteGridPos|0,0"));
        Furniture southStay = Furniture.known(TileRows.sprite(SHEET + 80 + AERIAL + "Facing|S|SpriteGridPos|0,1"));
        Furniture east = Furniture.known(TileRows.sprite(SHEET + 82 + AERIAL + "Facing|E|SpriteGridPos|0,1"));
        Furniture eastStay = Furniture.known(TileRows.sprite(SHEET + 83 + AERIAL + "Facing|E|SpriteGridPos|0,0"));
        check(south != null && south.model != null && south.model.name.equals("tv_aerial") && south.turns == Outdoors.SOUTH && south.onRoof,
                "81 stands the aerial facing south, on the roof");
        check(east != null && east.model != null && east.model.name.equals("tv_aerial") && east.turns == Outdoors.EAST && east.onRoof,
                "82 stands the aerial facing east, on the roof");
        check(southStay != null && southStay.follows && eastStay != null && eastStay.follows, "80 and 83 stand nothing of their own");
        check(south != null && (SHEET + 80).equals(south.partner) && south.partnerX == 0 && south.partnerY == 1 && southStay != null
                && (SHEET + 81).equals(southStay.partner) && southStay.partnerY == -1, "facing south the other sprite is on the square to the south");
        check(east != null && (SHEET + 83).equals(east.partner) && east.partnerX == 1 && east.partnerY == 0 && eastStay != null
                && (SHEET + 82).equals(eastStay.partner) && eastStay.partnerX == -1, "facing east it is on the square to the east");
        check(south != null && east != null && south.centreX == east.centreY && south.centreY == east.centreX,
                "the one facing east is the one facing south mirrored across the diagonal, as the game's view is");
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    /** Where the game's view draws a point (a, b) on a square, h levels over its floor: across, then down, in tiles. */
    private static float[] drawn(float a, float b, float h) {
        return new float[] {a - b, a + b - 6f * h};
    }

    private static void whatIsPaintedOverARoofsLevelStandsOnTheRoofWhereTheViewDrawsIt() {
        float[] out = new float[3];
        RoofFit.standOn(new float[] {0f, 0f, 0f, 0f}, 0.3f, 0.7f, out);
        check(near(out[0], 0.3f) && near(out[1], 0.7f) && near(out[2], 0f), "with the roof on the floor nothing moves");
        // Flat, a fifth of a level up; a slope facing the view, high at the north-west; one along y facing it.
        float third = RoofFit.PITCH;
        float[][] roofs = {{0.2f, 0.2f, 0.2f, 0.2f}, {0.6f, 0.6f - third, 0.6f - third, 0.6f}, {0.5f, 0.5f, 0.5f - third, 0.5f - third}};
        for (float[] roof : roofs) {
            RoofFit.standOn(roof, 0.4f, 0.5f, out);
            float alongX = (roof[1] + roof[2] - roof[0] - roof[3]) / 2f, alongY = (roof[2] + roof[3] - roof[0] - roof[1]) / 2f;
            float plane = (roof[0] + roof[1] + roof[2] + roof[3]) / 4f + alongX * (out[0] - 0.5f) + alongY * (out[1] - 0.5f);
            float[] painted = drawn(0.4f, 0.5f, 0f), stood = drawn(out[0], out[1], out[2]);
            check(near(out[2], plane), "it stands on the roof: " + out[2] + " against " + plane);
            check(near(painted[0], stood[0]) && near(painted[1], stood[1]), "where the game's view drew it");
        }
        // A slope that climbs away from the view as fast as the line of sight: it is raised where it is.
        RoofFit.standOn(new float[] {0.1f, 0.1f + third, 0.1f + third, 0.1f}, 0.4f, 0.5f, out);
        check(near(out[0], 0.4f) && near(out[1], 0.5f) && near(out[2], 0.1f + third * 0.4f), "on a slope the view runs along it is raised to the roof over its place");
    }

    private static void theModelsStandOnTheirFeet() {
        for (String name : new String[] {"satellite_dish_large", "satellite_dish", "satellite_dish_post", "tv_aerial"}) {
            check(Models.get(name).minZ == 0f, name + " has its lowest point at its foot");
        }
        Models.Model big = Models.get("satellite_dish_large");
        check(big.height() > 3.5f && big.height() < 4.5f, "the big dish reaches over a level and a half, as its sprites do: " + big.height());
        Models.Model aerial = Models.get("tv_aerial");
        check(aerial.height() > 1.5f && aerial.height() < 1.8f, "an aerial is as high as its mast is painted: " + aerial.height());
    }
}
