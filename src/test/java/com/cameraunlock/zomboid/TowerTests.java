package com.cameraunlock.zomboid;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Tests of which sprites make up a pylon, a mast, a water tower or a tank, and of the models that stand in their place. */
final class TowerTests {
    private TowerTests() {}

    static void run() {
        Checks.run("everySpriteOfAWholePylonFindsItsSectionsAndOnlyAnchorsStandThem", TowerTests::everySpriteOfAWholePylonFindsItsSectionsAndOnlyAnchorsStandThem);
        Checks.run("sectionWithAWitnessMissingIsLeftAsTheGameDrawsIt", TowerTests::sectionWithAWitnessMissingIsLeftAsTheGameDrawsIt);
        Checks.run("sectionReachingASquareNotLoadedWaitsForTheMap", TowerTests::sectionReachingASquareNotLoadedWaitsForTheMap);
        Checks.run("everySectionsModelIsInTheFileAndStandsOnItsFoot", TowerTests::everySectionsModelIsInTheFileAndStandsOnItsFoot);
        Checks.run("insulatorsHangFromTheTipsOfTheArmsTheyAreWitnessedBy", TowerTests::insulatorsHangFromTheTipsOfTheArmsTheyAreWitnessedBy);
        Checks.run("mastStoreysStandOnTheTopOfTheBase", TowerTests::mastStoreysStandOnTheTopOfTheBase);
        Checks.run("gantryColumnsAreStoodAsOutdoorPieces", TowerTests::gantryColumnsAreStoodAsOutdoorPieces);
    }

    /** A made-up map: the names of the sprites on each square, and the squares that are loaded. */
    private static final class Map3 implements Towers.Squares {
        final Map<String, Set<String>> on = new HashMap<>();
        Set<String> notLoaded = new HashSet<>();

        void put(String sprite, int x, int y, int z) {
            on.computeIfAbsent(x + "," + y + "," + z, k -> new HashSet<>()).add(sprite);
        }

        @Override
        public Boolean holds(int x, int y, int z, String sprite) {
            String key = x + "," + y + "," + z;
            if (notLoaded.contains(key)) {
                return null;
            }
            return on.getOrDefault(key, Set.of()).contains(sprite);
        }
    }

    /** Lays every sprite of a section with its anchor on (x, y, z). */
    private static void lay(Map3 map, Towers.Section section, int x, int y, int z) {
        map.put(section.anchor(), x, y, z);
        for (Towers.Place place : section.witnesses()) {
            map.put(place.sprite(), x + place.dx(), y + place.dy(), z + place.dz());
        }
        for (Towers.Place place : section.members()) {
            map.put(place.sprite(), x + place.dx(), y + place.dy(), z + place.dz());
        }
    }

    private static Towers.Section sectionAnchoredBy(String anchor, String model) {
        for (Towers.Part part : Towers.of(anchor)) {
            if (part.dx() == 0 && part.dy() == 0 && part.dz() == 0 && part.section().anchor().equals(anchor) && part.section().model().equals(model)) {
                return part.section();
            }
        }
        throw new AssertionError("no section of " + model + " is anchored by " + anchor);
    }

    /** A pylon whose wires run along x, its base's anchor at (100, 200, 0), and the insulators of its east side. */
    private static Map3 pylon() {
        Map3 map = new Map3();
        int x = 100, y = 200;
        lay(map, sectionAnchoredBy("electricity_pylon_0", "lattice_base"), x, y, 0);
        for (int z = 5; z <= 7; z++) {
            lay(map, sectionAnchoredBy("electricity_pylon_64", "lattice_storey"), x + 2, y - 2, z);
        }
        for (int z : new int[] {4, 7}) {
            lay(map, sectionAnchoredBy("electricity_pylon_72", "pylon_arm_short"), x + 3, y - 2, z);
            map.put("electricity_pylon_145", x + 3, y - 4, z);
            map.put("electricity_pylon_145", x + 3, y + 2, z);
        }
        lay(map, sectionAnchoredBy("electricity_pylon_134", "pylon_arm_long"), x + 2, y - 2, 6);
        map.put("electricity_pylon_147", x + 3, y - 6, 6);
        map.put("electricity_pylon_147", x + 3, y + 4, 6);
        return map;
    }

    private static void everySpriteOfAWholePylonFindsItsSectionsAndOnlyAnchorsStandThem() {
        Map3 map = pylon();
        int stood = 0;
        for (Map.Entry<String, Set<String>> square : map.on.entrySet()) {
            String[] at = square.getKey().split(",");
            int x = Integer.parseInt(at[0]), y = Integer.parseInt(at[1]), z = Integer.parseInt(at[2]);
            for (String sprite : square.getValue()) {
                boolean found = false;
                for (Towers.Part part : Towers.of(sprite)) {
                    if (Towers.whole(map, part.section(), x - part.dx(), y - part.dy(), z - part.dz()) == Towers.WHOLE) {
                        found = true;
                        if (part.dx() == 0 && part.dy() == 0 && part.dz() == 0 && part.section().anchor().equals(sprite)) {
                            stood++;
                        }
                    }
                }
                check(found, sprite + " on " + square.getKey() + " is part of a whole section");
            }
        }
        // The base, three storeys, three pairs of arms and six insulators.
        check(stood == 13, "13 models stand for the pylon: " + stood);
    }

    private static void sectionWithAWitnessMissingIsLeftAsTheGameDrawsIt() {
        Map3 map = pylon();
        map.on.get("102,198,3").remove("electricity_pylon_55");
        Towers.Section base = sectionAnchoredBy("electricity_pylon_0", "lattice_base");
        check(Towers.whole(map, base, 100, 200, 0) == Towers.BROKEN, "a base with its witness gone is not whole");
        check(Towers.whole(map, base, 101, 200, 0) == Towers.BROKEN, "nor is one looked for a square off");
        Map3 bare = pylon();
        bare.on.get("103,198,4").remove("electricity_pylon_72");
        boolean hangs = false;
        for (Towers.Part part : Towers.of("electricity_pylon_145")) {
            hangs |= Towers.whole(bare, part.section(), 103, 196, 4) == Towers.WHOLE;
        }
        check(!hangs, "an insulator whose arm is gone hangs from nothing and is left as it is");
    }

    private static void sectionReachingASquareNotLoadedWaitsForTheMap() {
        Map3 map = pylon();
        map.notLoaded.add("102,198,3");
        check(Towers.whole(map, sectionAnchoredBy("electricity_pylon_0", "lattice_base"), 100, 200, 0) == Towers.NOT_LOADED,
                "a base whose witness is on a square not loaded waits");
    }

    private static void everySectionsModelIsInTheFileAndStandsOnItsFoot() {
        Set<String> names = new HashSet<>();
        for (String sheet : new String[] {"electricity_pylon_", "radio_tower_", "water_tank_", "industry_02_"}) {
            for (int i = 0; i < 400; i++) {
                for (Towers.Part part : Towers.of(sheet + i)) {
                    names.add(part.section().model());
                }
            }
        }
        check(names.size() == 12, "twelve models stand in for the structures: " + names);
        for (String name : names) {
            Models.Model model = Models.get(name);
            check(model.minZ == 0f, name + " has its lowest point at its foot: " + model.minZ);
        }
    }

    private static void insulatorsHangFromTheTipsOfTheArmsTheyAreWitnessedBy() {
        Models.Model pair = Models.get("insulator_pair"), string = Models.get("insulator_string");
        for (int i : new int[] {93, 136, 137, 138, 139, 144, 145, 146, 147}) {
            for (Towers.Part part : Towers.of("electricity_pylon_" + i)) {
                Towers.Section insulator = part.section();
                if (!insulator.model().startsWith("insulator") || !insulator.anchor().equals("electricity_pylon_" + i)) {
                    continue;
                }
                Towers.Place witness = insulator.witnesses()[0];
                Towers.Section arm = sectionAnchoredBy(witness.sprite(), witness.sprite().endsWith("_72") || witness.sprite().endsWith("_111")
                        ? "pylon_arm_short" : "pylon_arm_long");
                Models.Model armModel = Models.get(arm.model());
                boolean square = arm.model().equals("pylon_arm_short");
                float reach = armModel.maxY - 0.045f, across = square ? armModel.maxX - 0.045f : 0f;
                // Where the insulator hangs, from the arm's origin, in the arm's own frame.
                float x = insulator.a() - witness.dx() - arm.a(), y = insulator.b() - witness.dy() - arm.b();
                float[] tip = new float[2];
                boolean onATip = false;
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sy = -1; sy <= 1; sy += 2) {
                        Models.turn(arm.turns(), sx * across, sy * reach, tip);
                        onATip |= Math.abs(tip[0] - x) < 0.03f && Math.abs(tip[1] - y) < 0.03f;
                    }
                }
                check(onATip, "electricity_pylon_" + i + " hangs from a tip of its arm: " + x + ", " + y + " from its axis");
                Models.Model model = insulator.model().equals("insulator_pair") ? pair : string;
                float axis = insulator.raised() + (model.maxZ - model.minZ) / 2f;
                float chord = arm.raised() + 0.045f + (square ? 0f : 0.03f * ViewCamera.LEVEL_HEIGHT);
                check(Math.abs(axis - chord) < 0.02f, "electricity_pylon_" + i + " is as high as its arm's tip: " + axis + " and " + chord);
            }
        }
    }

    private static void mastStoreysStandOnTheTopOfTheBase() {
        Models.Model base = Models.get("lattice_base"), storey = Models.get("lattice_storey");
        check(Math.abs(base.maxZ - 5f * ViewCamera.LEVEL_HEIGHT) < 0.08f, "the base is five levels high: " + base.maxZ);
        check(Math.abs(storey.maxZ - ViewCamera.LEVEL_HEIGHT) < 0.06f, "a storey is a level high: " + storey.maxZ);
        check(Math.abs(storey.maxX - 0.79f) < 0.01f, "a storey's legs are 0.74 from its axis, as the base's are at its top: " + storey.maxX);
    }

    private static void gantryColumnsAreStoodAsOutdoorPieces() {
        check("lattice_post_foot".equals(Outdoors.of("electricity_pylon_181", null, null, Outdoors.NONE, 1, 1, 0).model), "the column's foot on its pad");
        check("lattice_post".equals(Outdoors.of("electricity_pylon_176", null, null, Outdoors.NONE, 1, 1, 0).model), "a storey of the column");
        check("lattice_post_white".equals(Outdoors.of("radio_tower_81", null, null, Outdoors.NONE, 1, 1, 0).model), "a radio mast's narrow column");
        check(Outdoors.of("electricity_pylon_177", null, null, Outdoors.NONE, 1, 1, 0) == null, "the sprite after it is none");
    }

    private static void check(boolean ok, String what) {
        Checks.check(ok, what);
    }
}
