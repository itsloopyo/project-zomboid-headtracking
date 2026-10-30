package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * A shop's awning as the map has one: a sprite on each square along the shop front, of a
 * canopy that hangs on the wall at the edge of its square behind the way it faces and runs
 * out over the square. The game marks a canopy as a roof tile, so that it is cut away with the
 * roofs, and laid as roof tiles an awning was a row of separate sloping slabs with the roof's
 * caps and eaves on them, each at its own depth and none meeting the next.
 *
 * The mod hangs one model of its own on each square instead, a metre of awning, so that the
 * models of a run meet at the edges of their squares. Which model, by the sprite: the game
 * has awnings that run out a whole square and slope down to a valance of round tabs, of points
 * or with a straight hem, short ones that run out under half a square, and domed ones that
 * curve down to the front. The curved quarters of a round canopy are Parasols'.
 */
final class Awnings {
    /** How a model of an awning hangs on its square, and where its sprite gives its cloth's two colours. */
    static final class Hung {
        final String model;
        /** Quarter turns from facing north, as Models.turn takes them: the way the front faces. */
        final int turns;
        /** How high the foot of the valance is, in levels: the lowest point of the model. */
        final float foot;
        /** How high the top of the cloth is where it meets the wall, in levels. */
        final float wall;
        /** Where on the square the sprite gives the first colour and the second, and how high, in levels. */
        final float firstA, firstB, secondA, secondB, dyeHigh;

        Hung(String model, int turns, float foot, float wall, float firstA, float firstB, float secondA, float secondB, float dyeHigh) {
            this.model = model;
            this.turns = turns;
            this.foot = foot;
            this.wall = wall;
            this.firstA = firstA;
            this.firstB = firstB;
            this.secondA = secondA;
            this.secondB = secondB;
            this.dyeHigh = dyeHigh;
        }

        /**
         * Where the middle of the model's footprint is on its square, along x and y: its back
         * clear of the wall on the edge behind it by BACK, depth being how far the model reaches
         * out from its back.
         */
        void centre(float depth, float[] out) {
            Models.turn(turns, 0f, -1f, out);
            float fromMiddle = BACK + depth / 2f - 0.5f;
            out[0] = 0.5f + out[0] * fromMiddle;
            out[1] = 0.5f + out[1] * fromMiddle;
        }
    }

    /** How far the back of an awning is from the edge of its square, in tiles: clear of the wall's skin. tools/models/furniture.py AWNING_BACK. */
    static final float BACK = 0.06f;

    /** What the game calls an awning, and the kind of the curved quarters a parasol is made of. */
    private static final String CANOPY = "Canopy", CURVED = "Curved";
    private static final String DECORATION = "walls_decoration_01_";

    /** How high the foot of each kind's valance is, in levels, read off the sprites: tools/models/furniture.py awnings(). */
    static final float LONG_FOOT = 0.727f, SHORT_FOOT = 0.80f, DOME_FOOT = 0.625f;
    /** How high the top of each kind's cloth is where it meets the wall, in levels: tools/models/furniture.py awnings(). */
    static final float LONG_WALL = 0.969f, SHORT_WALL = 0.969f, DOME_WALL = 0.985f;
    /**
     * Where the cloth's colours are read on the sprite of each kind, as a share of the square
     * along the wall, how far out from it, and how high the cloth is there, in levels. On every
     * striped awning of the game the two places along the wall are in stripes of the two
     * colours, an eighth of a square or more inside each: the stripes are a quarter of a square
     * wide, and the sprites of an awning that faces north or west have theirs an eighth of a
     * square on from those that face south or east.
     */
    static final float LONG_OUT = 0.5f, LONG_HIGH = 0.905f, SHORT_OUT = 0.2f, SHORT_HIGH = 0.952f, DOME_OUT = 0.3f, DOME_HIGH = 0.97f;
    static final float[] LONG_ALONG_SOUTH_EAST = {0.125f, 0.3125f}, LONG_ALONG_NORTH_WEST = {0.25f, 0.5f}, SHORT_ALONG = {0.07f, 0.32f}, DOME_ALONG = {0.5f, 0.5f};

    private static final Known<IsoSprite, Hung> known = new Known<>();

    private Awnings() {}

    /** For a map loaded anew: its sprites are not the ones before's. */
    static void forget() {
        known.clear();
    }

    static Hung of(IsoSprite sprite) {
        return known.get(sprite, each -> {
            PropertyContainer properties = each.getProperties();
            return of(each.getName(), properties.get("CustomName"), properties.get("GroupName"), properties.get("Facing"));
        });
    }

    /** The model of an awning a sprite is a square of and how it hangs, or null for a sprite that is none the mod has a model of. */
    static Hung of(String name, String called, String kind, String facing) {
        if (name == null || !CANOPY.equals(called) || kind != null && kind.startsWith(CURVED) || facing == null) {
            return null;
        }
        int faces = switch (facing) {
            case "N" -> Outdoors.NORTH;
            case "E" -> Outdoors.EAST;
            case "S" -> Outdoors.SOUTH;
            case "W" -> Outdoors.WEST;
            default -> Outdoors.NONE;
        };
        if (faces == Outdoors.NONE) {
            return null;
        }
        String model = modelOf(name);
        if (model == null) {
            return null;
        }
        return switch (model) {
            case "awning_short" -> hang(model, faces, SHORT_FOOT, SHORT_WALL, SHORT_ALONG, SHORT_OUT, SHORT_HIGH);
            case "awning_dome" -> hang(model, faces, DOME_FOOT, DOME_WALL, DOME_ALONG, DOME_OUT, DOME_HIGH);
            default -> hang(model, faces, LONG_FOOT, LONG_WALL, faces == Outdoors.SOUTH || faces == Outdoors.EAST ? LONG_ALONG_SOUTH_EAST : LONG_ALONG_NORTH_WEST,
                    LONG_OUT, LONG_HIGH);
        };
    }

    /**
     * Which model a sprite of an awning is, by its place on its sheet, or null. Every one the
     * map places was looked at: a sprite of another sheet or place is left to be drawn as the
     * game's.
     */
    static String modelOf(String name) {
        if (name.startsWith(DECORATION)) {
            String place = name.substring(DECORATION.length());
            return switch (place) {
                case "84", "85", "92", "93", "100", "101", "108", "109", "116", "117", "124", "125" -> "awning";
                case "128", "129", "136" -> "awning_hemmed";
                case "132", "133", "140", "141" -> "awning_pointed";
                case "68", "69", "70", "71", "112", "113", "114", "115", "120", "121", "122", "123" -> "awning_short";
                case "145", "148", "149", "152", "157" -> "awning_dome";
                default -> null;
            };
        }
        return switch (name) {
            case "location_restaurant_pie_01_4", "location_restaurant_pie_01_5", "location_restaurant_pileocrepe_01_4", "location_restaurant_pileocrepe_01_5",
                    "location_restaurant_bar_smutz_12", "location_restaurant_bar_smutz_13", "location_restaurant_bar_smutz_14", "location_restaurant_bar_smutz_15"
                    -> "awning_short";
            default -> null;
        };
    }

    /**
     * along is the two places along the wall, as a share of the square from its west or north
     * corner, and out how far from the wall the cloth's colours are read.
     */
    private static Hung hang(String model, int faces, float foot, float wall, float[] along, float out, float high) {
        float[] first = onSquare(faces, along[0], out), second = onSquare(faces, along[1], out);
        return new Hung(model, faces, foot, wall, first[0], first[1], second[0], second[1], high);
    }

    /**
     * How high the cloth of the awning on a square meets the wall on its north edge (north) or
     * its west edge, in levels: what hangs on the wall above it rests on it there. 0 for a
     * square with no awning hung on that wall.
     */
    static float meetsWall(IsoGridSquare square, boolean north) {
        int faces = north ? Outdoors.SOUTH : Outdoors.EAST;
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            Hung hung = sprite == null ? null : of(sprite);
            if (hung != null && hung.turns == faces) {
                return hung.wall;
            }
        }
        return 0f;
    }

    /** Where on the square a place is that far along the wall from its west or north corner and that far out from it. */
    static float[] onSquare(int faces, float along, float out) {
        return switch (faces) {
            case Outdoors.SOUTH -> new float[] {along, out};
            case Outdoors.NORTH -> new float[] {along, 1f - out};
            case Outdoors.EAST -> new float[] {out, along};
            default -> new float[] {1f - out, along};
        };
    }
}
