package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * Structures the game builds of many sprites, several squares wide and several levels high,
 * each square drawn by itself: the steel lattice of a pylon or a radio mast, a pylon's
 * cross-arms and the insulators on them, a water tower's tank, a steel storage tank, a silo, a
 * small vented tank. The game gives none of their sprites a grid of its own, and each is
 * placed by where it lies from the others, so a structure is known by its sprites lying where
 * they always lie from one of them, its anchor.
 *
 * The mod stands one model of a section of a structure on its anchor's square and draws
 * nothing for the section's other sprites, as Parasols does with a canopy's quarters. A
 * section with its anchor or one of its witnesses missing from where they lie is left to be
 * drawn as the game's sprites, every one of them.
 *
 * Where each section stands was read off the game's sprites drawn together as its view draws
 * them: a pylon's legs splay from feet 2.2 tiles either side of its axis to MAST_HALF at four
 * and a half levels, a cross-arm's tip is 3 or 5 tiles out, the insulators hang from the tips
 * where the wire sprites start.
 */
final class Towers {
    private static final float LEVEL = ViewCamera.LEVEL_HEIGHT;
    /** How far below the middle of a pylon's lower chord, and an insulator's axis, the lowest point of its model is, in tiles. */
    private static final float CHORD_HALF = 0.045f, DISC = 0.085f;
    /** How high the lower chords of a pylon's short arms are over the floor of their level, and the tip of a long arm, in levels. */
    private static final float SHORT_ARM = 0.6f, LONG_TIP = 0.03f;

    /**
     * Where a big dish's pedestal stands on its anchor's square. The game paints the pedestal's foot two thirds of a
     * metre over the floor where its axis is there, and a dish on a flat roof stands on the roof: the foot is put down
     * on the floor, under the painted axis, and the whole dish comes down with it.
     */
    private static final float DISH_A = 0.36f, DISH_B = 0.65f;

    /** One sprite's place from its section's anchor, in squares and levels. */
    record Place(String sprite, int dx, int dy, int dz) {}

    /**
     * A section of a structure: its model, turned as Models.turn takes it, with its origin at
     * (a, b) from the north-west corner of its anchor's square and its lowest point raised tiles
     * over that square's floor; the sprites that have to lie where they lie from the anchor for
     * it to be whole; and the rest of the sprites it is drawn for.
     */
    record Section(String model, int turns, float a, float b, float raised, String anchor, Place[] witnesses, Place[] members) {}

    /** A sprite's part in a section: the section, and where the sprite lies from its anchor. */
    record Part(Section section, int dx, int dy, int dz) {}

    private static final Map<String, Part[]> PARTS = new HashMap<>();
    private static final Part[] NONE = new Part[0];
    private static final IdentityHashMap<IsoSprite, Part[]> known = new IdentityHashMap<>();

    private Towers() {}

    static void forget() {
        known.clear();
    }

    /** The parts a sprite has in the sections of structures, by its name: none for a sprite of no structure. */
    static Part[] of(String name) {
        Part[] parts = PARTS.get(name);
        return parts == null ? NONE : parts;
    }

    static Part[] of(IsoSprite sprite) {
        Part[] parts = known.get(sprite);
        if (parts == null) {
            parts = sprite.getName() == null ? NONE : of(sprite.getName());
            known.put(sprite, parts);
        }
        return parts;
    }

    /** What a section finds where its sprites should lie. */
    static final int WHOLE = 0, BROKEN = 1, NOT_LOADED = 2;

    /** The squares of the map, by whether one holds a sprite of a name: null for a square not loaded. */
    interface Squares {
        Boolean holds(int x, int y, int z, String sprite);
    }

    static Squares on(IsoCell cell) {
        return (x, y, z, sprite) -> {
            IsoGridSquare square = cell.getGridSquare(x, y, z);
            if (square == null) {
                // Above the ground the map has no square where there is only air, so a missing
                // square is one not loaded only where the ground under it is missing too.
                return z != 0 && cell.getGridSquare(x, y, 0) != null ? Boolean.FALSE : null;
            }
            PZArrayList<IsoObject> objects = square.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                IsoSprite on = objects.get(i).getSprite();
                if (on != null && sprite.equals(on.getName())) {
                    return true;
                }
            }
            return false;
        };
    }

    /** Whether the section whose anchor is on (x, y, z) is whole there. */
    static int whole(Squares squares, Section section, int x, int y, int z) {
        Boolean anchor = squares.holds(x, y, z, section.anchor);
        if (anchor == null) {
            return NOT_LOADED;
        }
        if (!anchor) {
            return BROKEN;
        }
        for (Place witness : section.witnesses) {
            Boolean there = squares.holds(x + witness.dx, y + witness.dy, z + witness.dz, witness.sprite);
            if (there == null) {
                return NOT_LOADED;
            }
            if (!there) {
                return BROKEN;
            }
        }
        return WHOLE;
    }

    private static void add(Section section) {
        List<Place> all = new ArrayList<>();
        all.add(new Place(section.anchor, 0, 0, 0));
        all.addAll(List.of(section.witnesses));
        all.addAll(List.of(section.members));
        for (Place place : all) {
            Part[] before = PARTS.getOrDefault(place.sprite, NONE);
            Part[] after = Arrays.copyOf(before, before.length + 1);
            after[before.length] = new Part(section, place.dx, place.dy, place.dz);
            PARTS.put(place.sprite, after);
        }
    }

    private static Place[] places(String sheet, int... indexAndPlace) {
        Place[] places = new Place[indexAndPlace.length / 4];
        for (int i = 0; i < places.length; i++) {
            places[i] = new Place(sheet + indexAndPlace[4 * i], indexAndPlace[4 * i + 1], indexAndPlace[4 * i + 2], indexAndPlace[4 * i + 3]);
        }
        return places;
    }

    /**
     * A storey of a square lattice mast: eight sprites round an empty middle square, the first of
     * them north of it. The same eight on every sheet that has one.
     */
    private static Section storey(String sheet, int first, String model) {
        return new Section(model, 0, 0.5f, 1.5f, 0f, sheet + first, places(sheet, first + 7, 1, 2, 0),
                places(sheet, first + 1, -1, 1, 0, first + 2, -1, 0, 0, first + 3, 1, 0, 0, first + 4, 1, 1, 0, first + 5, -1, 2, 0, first + 6, 0, 2, 0));
    }

    /** An insulator that hangs at (a, b) on its own square, turned to reach the way the wire leaves, on the arm whose anchor lies at one of two places from it. */
    private static void insulator(String sprite, String model, int turns, float a, float b, float raised, String arm, int dx, int dy, int otherDx, int otherDy) {
        for (int[] at : new int[][] {{dx, dy}, {otherDx, otherDy}}) {
            add(new Section(model, turns, a, b, raised, sprite, new Place[] {new Place(arm, at[0], at[1], 0)}, new Place[0]));
        }
    }

    static {
        String pylon = "electricity_pylon_", radio = "radio_tower_";
        // The base: four splayed legs over five levels, the anchor its north-west foot. The
        // eight sprites of the top level's ring are all there on a pylon; on a water tower the
        // tank's own sprites stand in for the four at the front.
        int[] base = {1, 0, 1, 0, 2, 1, 1, 0, 3, 4, 1, 0, 4, 3, 1, 0, 5, 4, 0, 0, 6, 0, -3, 0, 7, 0, -4, 0, 8, 0, -2, 0, 9, 3, -3, 0,
            10, 4, -2, 0, 11, 4, -3, 0, 12, 4, -2, 1, 13, 4, -3, 1, 14, 4, 1, 1, 15, 4, 0, 1, 16, 0, -2, 1, 17, 0, -3, 1, 18, 1, -3, 1,
            19, 0, 0, 1, 20, 0, 1, 1, 21, 1, 1, 1, 22, 2, -3, 1, 23, 3, -3, 1, 24, 3, 1, 1, 25, 4, -1, 1, 26, 0, -1, 1, 27, 2, 1, 1,
            28, 3, 0, 2, 29, 4, -1, 2, 30, 4, 0, 2, 31, 4, -2, 2, 32, 3, -2, 2, 33, 3, 1, 2, 34, 2, -3, 2, 35, 3, -3, 2, 36, 0, -3, 2,
            37, 1, -3, 2, 38, 2, 1, 2, 39, 1, 1, 2, 40, 1, 0, 2, 41, 1, -2, 2, 42, 4, -3, 2, 43, 1, -1, 2, 44, 0, 0, 2, 45, 0, 1, 2,
            48, 1, 0, 3, 49, 2, 0, 3, 50, 3, 0, 3, 51, 3, -1, 3, 52, 3, -2, 3, 53, 1, -2, 3, 54, 1, -1, 3,
            56, 1, 0, 4, 57, 3, 0, 4, 58, 1, -2, 4, 59, 1, -1, 4, 60, 3, -2, 4, 61, 3, -1, 4, 62, 2, -2, 4, 63, 2, 0, 4};
        for (String sheet : new String[] {pylon, radio}) {
            add(new Section("lattice_base", 0, 2.5f, -0.5f, 0f, sheet + 0, places(sheet, 55, 2, -2, 3), places(sheet, base)));
        }
        add(storey(pylon, 64, "lattice_storey"));
        add(storey(radio, 64, "lattice_storey_white"));
        add(storey(radio, 72, "lattice_storey_red"));

        // A pylon's cross-arms, a pair a level: short ones on the level of the base's top and on
        // the top storey's, long ones between, along y on a pylon whose wires run along x and
        // along x on one whose wires run along y.
        float shortArm = SHORT_ARM * LEVEL - CHORD_HALF;
        add(new Section("pylon_arm_short", 0, -0.5f, 1.5f, shortArm, pylon + 72, places(pylon, 87, 0, 4, 0),
                places(pylon, 73, 0, -1, 0, 74, -2, 0, 0, 75, -2, -1, 0, 76, -2, -2, 0, 77, -1, -2, 0, 78, -1, -1, 0, 79, 0, -2, 0,
                        80, -2, 4, 0, 81, -2, 3, 0, 82, -2, 2, 0, 83, -1, 3, 0, 84, -1, 4, 0, 85, 0, 2, 0, 86, 0, 3, 0)));
        add(new Section("pylon_arm_long", 0, 0.5f, 1.5f, -CHORD_HALF, pylon + 134, places(pylon, 121, 0, 6, 0),
                places(pylon, 120, 0, 5, 0, 122, 0, 4, 0, 123, 1, 3, 0, 124, 0, 3, 0, 125, 0, 3, 0, 126, 1, 2, 0, 127, -1, 2, 0,
                        128, 0, -3, 0, 129, 0, -4, 0, 130, 0, -2, 0, 131, -1, -1, 0, 133, 1, -1, 0, 135, 1, 0, 0)));
        add(new Section("pylon_arm_short", 1, 2.5f, 0.5f, shortArm, pylon + 111, places(pylon, 114, 5, 1, 0),
                places(pylon, 104, 1, 1, 0, 105, 0, 1, 0, 106, -1, 1, 0, 107, -1, 0, 0, 108, -1, -1, 0, 109, 0, -1, 0, 110, 1, -1, 0,
                        112, 4, 1, 0, 113, 3, 1, 0, 115, 5, 0, 0, 116, 3, -1, 0, 117, 4, -1, 0, 118, 5, -1, 0, 119, 4, 0, 0)));
        add(new Section("pylon_arm_long", 1, -0.5f, -0.5f, -CHORD_HALF, pylon + 90, places(pylon, 98, -6, -1, 0),
                places(pylon, 88, 0, -2, 0, 89, 1, -1, 0, 91, 1, 0, 0, 92, 2, -1, 0, 93, 3, -1, 0, 95, 1, -1, 0, 96, -3, 0, 0,
                        97, -3, -1, 0, 99, -5, -1, 0, 100, -4, -1, 0, 101, -3, -2, 0, 102, -2, -2, 0, 103, -2, 0, 0)));

        // The insulators at the arms' tips, each reaching out along the wire it holds. On an arm
        // along x the game paints the insulator of the long arm's east tip with the arm.
        float atShort = SHORT_ARM * LEVEL - DISC, atLong = LONG_TIP * LEVEL - DISC;
        insulator(pylon + 145, "insulator_pair", 2, 0.24f, 0.5f, atShort, pylon + 72, 0, 2, 0, -4);
        insulator(pylon + 144, "insulator_pair", 0, 0.76f, 0.5f, atShort, pylon + 72, 2, 2, 2, -4);
        insulator(pylon + 147, "insulator_string", 2, -0.5f, 0.5f, atLong, pylon + 134, -1, 4, -1, -6);
        insulator(pylon + 146, "insulator_string", 0, 1.5f, 0.5f, atLong, pylon + 134, 1, 4, 1, -6);
        insulator(pylon + 136, "insulator_pair", 3, 0.5f, 0.24f, atShort, pylon + 111, 1, -1, -5, -1);
        insulator(pylon + 137, "insulator_pair", 1, 0.5f, 0.76f, atShort, pylon + 111, 1, 1, -5, 1);
        add(new Section("insulator_string", 3, 0.5f, -0.5f, atLong, pylon + 138, places(pylon, 90, 6, 0, 0), new Place[0]));
        add(new Section("insulator_string", 1, 0.5f, 1.5f, atLong, pylon + 139, places(pylon, 90, 6, 2, 0), new Place[0]));
        add(new Section("insulator_string", 3, 1.5f, 0.5f, atLong, pylon + 93, places(pylon, 90, -3, 1, 0), new Place[0]));

        // A water tank on a roof or on a pylon's base: legs on the anchor's level less one,
        // the barrel on the anchor's, its roof on the level over that.
        String water = "water_tank_";
        add(new Section("water_tank", 0, 0.5f, 0.5f, -LEVEL, water + 9, places(water, 2, 1, -1, 1, 16, -1, 1, -1),
                places(water, 0, -1, 1, 1, 1, 0, 0, 1, 3, 0, 1, 1, 4, 1, 0, 1, 8, -1, 1, 0, 10, 1, -1, 0, 17, 1, 1, -1, 18, 0, 1, -1,
                        19, 1, 0, -1, 21, 0, 1, -1, 22, 1, 0, -1, 23, -1, 1, -1, 33, 1, 1, -1, 40, 0, 1, 0, 41, 1, 0, 0, 44, 1, 1, 1)));

        // A steel storage tank 5.5 tiles across: its wall's sprites along its south and east, its
        // roof's on the level over them, its floor's on the ground. The anchor is the wall's
        // south-east sprite. The same wall and roof on a silo's top are a tank raised on it.
        String works = "industry_02_";
        int[] wall = {80, -4, 0, 0, 82, -2, 0, 0, 83, -1, 0, 0, 85, 0, -1, 0, 86, 0, -2, 0, 87, 0, -3, 0};
        int[] roof = {88, -6, -2, 1, 89, -5, -1, 1, 90, -4, 0, 1, 91, -6, -3, 1, 92, -5, -2, 1, 93, -4, -1, 1, 94, -3, 0, 1,
            96, -6, -4, 1, 97, -5, -3, 1, 98, -4, -2, 1, 99, -3, -1, 1, 100, -2, 0, 1, 101, -5, -4, 1, 102, -4, -3, 1, 103, -3, -2, 1,
            104, -2, -1, 1, 105, -1, 0, 1, 106, -5, -5, 1, 107, -4, -4, 1, 108, -3, -3, 1, 109, -2, -2, 1, 110, -1, -1, 1, 111, 0, 0, 1,
            112, -4, -5, 1, 113, -3, -4, 1, 114, -2, -3, 1, 115, -1, -2, 1, 116, 0, -1, 1, 117, -4, -6, 1, 118, -3, -5, 1, 119, -2, -4, 1,
            120, -1, -3, 1, 121, 0, -2, 1, 122, -2, -5, 1, 123, -1, -4, 1, 124, 0, -3, 1, 125, -3, -6, 1, 126, -2, -6, 1, 127, -1, -5, 1,
            128, 0, -4, 1};
        int[] floor = {304, -5, -1, 0, 305, -4, 0, 0, 306, -5, -2, 0, 307, -4, -1, 0, 308, -3, 0, 0, 309, -5, -3, 0, 310, -4, -2, 0,
            311, -3, -1, 0, 312, -2, 0, 0, 313, -4, -3, 0, 314, -3, -2, 0, 315, -2, -1, 0, 317, -4, -4, 0, 318, -3, -3, 0, 319, -2, -2, 0,
            322, -3, -4, 0, 323, -2, -3, 0, 324, -1, -2, 0, 325, 0, -1, 0, 326, -3, -5, 0, 327, -2, -4, 0, 328, -1, -3, 0, 329, 0, -2, 0,
            330, -2, -5, 0, 331, -1, -4, 0, 332, 0, -3, 0, 333, -1, -5, 0, 334, 0, -4, 0};
        int[] grounded = new int[wall.length + roof.length + floor.length];
        System.arraycopy(wall, 0, grounded, 0, wall.length);
        System.arraycopy(roof, 0, grounded, wall.length, roof.length);
        System.arraycopy(floor, 0, grounded, wall.length + roof.length, floor.length);
        int[] raised = new int[wall.length + roof.length];
        System.arraycopy(grounded, 0, raised, 0, raised.length);
        add(new Section("storage_tank", 2, -2f, -2f, 0f, works + 84, places(works, 81, -3, 0, 0, 95, 0, -4, 0, 316, -1, 0, 0, 320, -1, -1, 0, 321, 0, 0, 0),
                places(works, grounded)));
        add(new Section("storage_tank", 2, -1f, -1f, 0f, works + 84, places(works, 81, -3, 0, 0, 95, 0, -4, 0, 130, -1, -1, -1), places(works, raised)));
        add(new Section("silo_storey", 0, 0f, 0f, 0f, works + 130, places(works, 129, -1, 0, 0, 131, 0, -1, 0), new Place[0]));
        add(new Section("vent_tank", 0, 0f, 0f, 0f, works + 73, places(works, 74, -1, -1, 0), places(works, 72, -1, 0, 0, 75, 0, -1, 0)));

        // A big satellite dish on a pedestal, facing south: three sprites over three of its four squares and three on
        // the level over them, the anchor the pedestal's. The pedestal stands on the anchor's square, and the dish
        // reaches out over the squares to its south.
        String dish = "appliances_com_01_";
        add(new Section("satellite_dish_large", 2, DISH_A, DISH_B, 0f, dish + 38,
                places(dish, 36, -1, 1, 0, 37, 0, 1, 0, 30, 0, 0, 1, 28, -1, 1, 1, 29, 0, 1, 1), new Place[0]));
    }
}
