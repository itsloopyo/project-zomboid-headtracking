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
