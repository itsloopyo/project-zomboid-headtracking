package com.cameraunlock.zomboid;

import java.util.Map;

/** Tests of what a room's hidden walls are hung with: the room's kind by its name on the map, and the kind of mirror or clock the building has. */
final class HiddenWallsTests {
    private HiddenWallsTests() {}

    static void run() {
        aRoomIsKnownByItsNameOnTheMap();
        aRoomWithNothingToSayWhatItIsIsLeftAlone();
        theMirrorIsOfTheKindTheBuildingHasMostOf();
        aKindTheGameDrawsFacingOutOnlyIsPassedOver();
    }

    private static void aRoomIsKnownByItsNameOnTheMap() {
        Checks.check(HiddenWalls.kindOf("bathroom") == HiddenWalls.Kind.BATH && HiddenWalls.kindOf("revpeterwattsbathroom") == HiddenWalls.Kind.BATH,
                "a bathroom is a bathroom by any name the map gives it");
        Checks.check(HiddenWalls.kindOf("bedroom") == HiddenWalls.Kind.BED && HiddenWalls.kindOf("kidsbedroom4") == HiddenWalls.Kind.BED
                && HiddenWalls.kindOf("guestbedroom") == HiddenWalls.Kind.BED, "so is a bedroom");
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

    private static void aKindTheGameDrawsFacingOutOnlyIsPassedOver() {
        Checks.check(HiddenWalls.preferred(Map.of("Mirror|Small Wide", 9, "Mirror|Tall", 1), "Mirror", "Large").equals("Tall"),
                "a mirror two squares wide, which is not hung here, does not decide the kind");
    }
}
