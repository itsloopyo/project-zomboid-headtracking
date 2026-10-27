package com.cameraunlock.zomboid;

import java.util.Map;

/** Tests of what a room's hidden walls are hung with: the room's kind by its name on the map, and the kind of mirror or clock the building has. */
final class HiddenWallsTests {
    private HiddenWallsTests() {}

    static void run() {
        Checks.run("aRoomIsKnownByItsNameOnTheMap", HiddenWallsTests::aRoomIsKnownByItsNameOnTheMap);
        Checks.run("aRoomWithNothingToSayWhatItIsIsLeftAlone", HiddenWallsTests::aRoomWithNothingToSayWhatItIsIsLeftAlone);
        Checks.run("theMirrorIsOfTheKindTheBuildingHasMostOf", HiddenWallsTests::theMirrorIsOfTheKindTheBuildingHasMostOf);
        Checks.run("aKindTheGameDrawsFacingOutOnlyIsPassedOver", HiddenWallsTests::aKindTheGameDrawsFacingOutOnlyIsPassedOver);
        Checks.run("aHuntersHouseTakesATrophy", HiddenWallsTests::aHuntersHouseTakesATrophy);
        Checks.run("aHomesPicturesAreOfTheHouse", HiddenWallsTests::aHomesPicturesAreOfTheHouse);
    }

    private static void aHomesPicturesAreOfTheHouse() {
        Checks.check(HiddenWalls.subjectOf("tennis-3").equals("tennis") && HiddenWalls.subjectOf("family-08").equals(HiddenWalls.FAMILY)
                && HiddenWalls.subjectOf("painting-lakeside").equals("painting-lakeside"), "a picture's subject is its name before the number");
        java.util.Map<String, Integer> pictures = new java.util.HashMap<>();
        for (int picture = 0; picture < Decor.count(); picture++) {
            pictures.merge(HiddenWalls.subjectOf(Decor.nameOf(picture)), 1, Integer::sum);
        }
        for (String subject : HiddenWalls.TASTES) {
            Checks.check(pictures.getOrDefault(subject, 0) >= 3, "the jar has several pictures of " + subject);
        }
        for (String name : HiddenWalls.NAMED) {
            Checks.check(HiddenWalls.TASTES.contains(HiddenWalls.subjectOf(name)) && pictures.containsKey(HiddenWalls.subjectOf(name)),
                    "the named picture " + name + " is one of a taste's");
        }
        int tennis = Decor.named("tennis-1"), shownTennis = Decor.named("tennis-2"), family = Decor.named("family-05"), other = Decor.named("family-08");
        int lakeside = Decor.named("painting-lakeside"), hockey = Decor.named("hockey-0");
        java.util.List<int[]> house = HiddenWalls.homeSources(java.util.Set.of(shownTennis, family, other, lakeside, hockey), java.util.Set.of(shownTennis, other),
                java.util.Set.of());
        java.util.Set<Integer> taste = new java.util.HashSet<>(), photos = new java.util.HashSet<>(), landscapes = new java.util.HashSet<>();
        for (int picture : house.get(0)) {
            taste.add(picture);
        }
        for (int picture : house.get(1)) {
            photos.add(picture);
        }
        for (int picture : house.get(2)) {
            landscapes.add(picture);
        }
        Checks.check(taste.contains(tennis) && !taste.contains(shownTennis) && !taste.contains(Decor.named("hockey-1")), "a room with a tennis picture takes another tennis picture, not one the building shows");
        for (int picture : taste) {
            Checks.check(HiddenWalls.subjectOf(Decor.nameOf(picture)).equals("tennis"), "and nothing of a subject the room does not show: " + Decor.nameOf(picture));
        }
        Checks.check(photos.equals(java.util.Set.of(family)), "the family photographs are those the building shows and the room does not: " + photos);
        Checks.check(!landscapes.isEmpty() && !landscapes.contains(lakeside), "the landscapes are those the building does not show");
        java.util.List<int[]> spread = HiddenWalls.homeSources(java.util.Set.of(family, other), java.util.Set.of(), java.util.Set.of(family));
        Checks.check(spread.get(1).length == 1 && spread.get(1)[0] == other, "a family photograph drawn in another room is passed over while another is left");
        java.util.List<int[]> all = HiddenWalls.homeSources(java.util.Set.of(family), java.util.Set.of(), java.util.Set.of(family));
        Checks.check(all.get(1).length == 1, "and drawn again when it is the only one");
        java.util.List<int[]> sources = java.util.List.of(new int[] {1, 2}, new int[] {5}, new int[] {7, 8, 9});
        boolean[] used = new boolean[3];
        java.util.Set<Integer> taken = new java.util.HashSet<>();
        int first = HiddenWalls.next(sources, used, taken, 0);
        taken.add(first);
        int second = HiddenWalls.next(sources, used, taken, 1);
        taken.add(second);
        int third = HiddenWalls.next(sources, used, taken, 2);
        taken.add(third);
        int fourth = HiddenWalls.next(sources, used, taken, 3);
        Checks.check((first == 1 || first == 2) && second == 5 && third >= 7, "a room gets one of the subject it shows, then the family, then a landscape");
        Checks.check(fourth == 1 || fourth == 2, "and a second of the first before a second landscape: " + fourth);
        boolean[] none = new boolean[3];
        Checks.check(HiddenWalls.next(java.util.List.of(new int[0], new int[] {5}, new int[] {7}), none, java.util.Set.of(5), 0) == 7,
                "a house with no other subject and its family shown in the room gets a landscape");
        Checks.check(HiddenWalls.next(java.util.List.of(new int[0], new int[0], new int[] {7}), new boolean[3], java.util.Set.of(7), 0) == Decor.NONE,
                "and nothing once every one is taken");
    }

    private static void aRoomIsKnownByItsNameOnTheMap() {
        Checks.check(HiddenWalls.kindOf("bathroom") == HiddenWalls.Kind.BATH && HiddenWalls.kindOf("revpeterwattsbathroom") == HiddenWalls.Kind.BATH,
                "a bathroom is a bathroom by any name the map gives it");
        Checks.check(HiddenWalls.kindOf("bedroom") == HiddenWalls.Kind.BED && HiddenWalls.kindOf("kidsbedroom4") == HiddenWalls.Kind.BED
                && HiddenWalls.kindOf("guestbedroom") == HiddenWalls.Kind.BED && HiddenWalls.kindOf("motelroomoccupied") == HiddenWalls.Kind.BED,
                "so is a bedroom, a motel's room among them");
        Checks.check(HiddenWalls.kindOf("hospitalroom") == HiddenWalls.Kind.LIVING && HiddenWalls.kindOf("bar") == HiddenWalls.Kind.LIVING
                && HiddenWalls.kindOf("cafe") == HiddenWalls.Kind.LIVING, "a hospital's room, a bar and a cafe are rooms to sit in, with a clock and their kind's pictures");
        Checks.check(HiddenWalls.kindOf("kitchen") == HiddenWalls.Kind.KITCHEN && HiddenWalls.kindOf("pizzakitchen") == HiddenWalls.Kind.KITCHEN,
                "a house's kitchen and a restaurant's are kitchens");
        Checks.check(HiddenWalls.kindOf("breakroom") == HiddenWalls.Kind.KITCHEN, "and so is a works breakroom, which gets a clock and no mirror");
        Checks.check(HiddenWalls.homeOf(java.util.List.of("hall", "kitchen", "kidsbedroom", "bathroom", "garage"))
                && HiddenWalls.homeOf(java.util.List.of("livingroom", "kitchen", "bathroom"))
                && HiddenWalls.homeOf(java.util.List.of("ww_bedroom", "ww_kitchen", "ww_livingroom", "porch"))
                && HiddenWalls.homeOf(java.util.List.of("bedroom", "", "kitchen", "pantry")),
                "a hall's mirror is a house's, with a bedroom or only a living room, and a nameless room does not unmake it");
        Checks.check(!HiddenWalls.homeOf(java.util.List.of("bedroom", "kitchen", "hall", "stairwell", "elevator", "janitor"))
                && !HiddenWalls.homeOf(java.util.List.of("", "office")),
                "a block of flats' corridor is no home's hall, and nor is a building with only nameless rooms and an office");
        int[] pool = {4, 7, 9};
        Checks.check(HiddenWalls.unshown(pool, java.util.Set.of(4, 9), 5) == 7 && HiddenWalls.unshown(pool, java.util.Set.of(4, 7, 9), 0) == Decor.NONE,
                "a hidden wall's picture is one its building shows nowhere, and none where it shows them all");
        Checks.check(HiddenWalls.unshown(pool, java.util.Set.of(), 0) != HiddenWalls.unshown(pool, java.util.Set.of(), 1),
                "two pictures drawn in one room differ");
        Checks.check(HiddenWalls.middleOf(1, 1) == 0.5f && HiddenWalls.middleOf(0, 1) == 1f && HiddenWalls.middleOf(1, 2) == 1f,
                "a hidden wall's picture hangs in the middle of its stretch of wall, on a square's middle or the edge two share");
        Checks.check(HiddenWalls.key(10, 20, 0, HiddenWalls.SOUTH) != HiddenWalls.key(10, 20, 0, HiddenWalls.EAST)
                && HiddenWalls.key(10, 20, 0, HiddenWalls.SOUTH) != HiddenWalls.key(10, 20, 1, HiddenWalls.SOUTH)
                && HiddenWalls.key(10, 20, 0, HiddenWalls.SOUTH) != HiddenWalls.key(20, 10, 0, HiddenWalls.SOUTH)
                && HiddenWalls.key(10, 20, -1, HiddenWalls.SOUTH) != HiddenWalls.key(10, 20, 0, HiddenWalls.SOUTH),
                "each square's two hidden walls on each level have their own picture");
        for (String name : HiddenWalls.GENERIC) {
            Checks.check(Decor.named(name) >= 0, "the jar has the generic painting " + name);
        }
        Checks.check(!HiddenWalls.homeOf(java.util.List.of("secondaryhall", "secondaryclassroom", "bedroom", "office"))
                && !HiddenWalls.homeOf(java.util.List.of("hospitalhallway", "nursery", "hospitalroom", "waitingroom"))
                && !HiddenWalls.homeOf(java.util.List.of("restaurantdining", "restaurantkitchen", "hall", "bedroom", "kitchen"))
                && !HiddenWalls.homeOf(java.util.List.of("hall", "office", "kitchen", "bathroom")),
                "and not a school's, a hospital's, a restaurant's with a flat over it or an office suite's");
        Checks.check(HiddenWalls.kindOf("livingroom") == HiddenWalls.Kind.LIVING && HiddenWalls.kindOf("diningroom") == HiddenWalls.Kind.LIVING
                && HiddenWalls.kindOf("spiffo_dining") == HiddenWalls.Kind.LIVING, "living and dining rooms are rooms to sit in");
        Checks.check(HiddenWalls.kindOf("hall") == HiddenWalls.Kind.HALL && HiddenWalls.kindOf("foyer") == HiddenWalls.Kind.HALL, "a hall and a foyer are halls");
        Checks.check(HiddenWalls.kindOf("policeoffice") == HiddenWalls.Kind.OFFICE && HiddenWalls.kindOf("classroom_maths") == HiddenWalls.Kind.OFFICE
                && HiddenWalls.kindOf("universitylibrary") == HiddenWalls.Kind.OFFICE, "offices, classrooms and libraries take a clock");
    }

    private static void aRoomWithNothingToSayWhatItIsIsLeftAlone() {
        for (String name : new String[] {"garage", "storage", "basement", "officestorage", "kitchenstorage", "emptykitchen", "closet", "shed", "", "pantry"}) {
            Checks.check(HiddenWalls.kindOf(name) == HiddenWalls.Kind.NONE, "a room called '" + name + "' is left as the map has it");
        }
        Checks.check(HiddenWalls.kindOf(null) == HiddenWalls.Kind.NONE, "and so is a room with no name");
    }

    private static void theMirrorIsOfTheKindTheBuildingHasMostOf() {
        Checks.check(HiddenWalls.preferred(Map.of("Mirror|Tall", 1, "Mirror|Wooden Framed", 3, "Clock|Wall", 5), "Mirror", "Large").equals("Wooden Framed"),
                "the building's most common mirror is hung");
        Checks.check(HiddenWalls.preferred(Map.of("Mirror|Tall", 2, "Mirror|Large", 2), "Mirror", "Small").equals("Large"),
                "a tie goes the same way every time");
        Checks.check(HiddenWalls.preferred(Map.of(), "Clock", "Wall").equals("Wall"), "a building with none gets the room's own default");
    }

    private static void aHuntersHouseTakesATrophy() {
        Checks.check(HiddenWalls.wantsATrophy(Map.of(HiddenWalls.HUNTING + "|", 1, "Clock|Wall", 2)) && HiddenWalls.TASTES.contains(HiddenWalls.HUNTING),
                "a house with a hunting picture and no trophy takes a deer's head");
        Checks.check(!HiddenWalls.wantsATrophy(Map.of(HiddenWalls.HUNTING + "|", 1, "Deer Trophy|Mounted", 1))
                && !HiddenWalls.wantsATrophy(Map.of(HiddenWalls.HUNTING + "|", 2, "Antlers|Stag", 1)), "and one that has a trophy on a wall already takes no other");
        Checks.check(!HiddenWalls.wantsATrophy(Map.of("Mirror|Tall", 3, "Clock|Wall", 1)) && !HiddenWalls.wantsATrophy(Map.of()), "and one with only mirrors and clocks takes none");
        Checks.check(HiddenWalls.trophy("Deer Trophy") && !HiddenWalls.trophy("Mirror") && !HiddenWalls.trophy(null),
                "a thing the game gives no name is no trophy, and asking does not throw");
    }

    private static void aKindTheGameDrawsFacingOutOnlyIsPassedOver() {
        Checks.check(HiddenWalls.preferred(Map.of("Mirror|Small Wide", 9, "Mirror|Tall", 1), "Mirror", "Large").equals("Tall"),
                "a mirror two squares wide, which is not hung here, does not decide the kind");
    }
}
