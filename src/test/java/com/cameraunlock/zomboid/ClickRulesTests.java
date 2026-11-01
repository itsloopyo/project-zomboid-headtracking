package com.cameraunlock.zomboid;

import com.cameraunlock.zomboid.InteractWords.Does;

/** Tests of the game's click rules the Interact key and its label follow (ClickRules). */
final class ClickRulesTests {
    private ClickRulesTests() {}

    static void run() {
        Checks.run("shiftOnACurtainedDoorIsForItsCurtainsFromTheirSideOnly", ClickRulesTests::shiftOnACurtainedDoorIsForItsCurtainsFromTheirSideOnly);
        Checks.run("aWindowTakesTheClickOnlyFromBesideIt", ClickRulesTests::aWindowTakesTheClickOnlyFromBesideIt);
        Checks.run("aContainerClickFollowsTheGamesOrder", ClickRulesTests::aContainerClickFollowsTheGamesOrder);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void shiftOnACurtainedDoorIsForItsCurtainsFromTheirSideOnly() {
        check(ClickRules.door(false, true, false, true, true, true, false) == Does.OPEN_CURTAINS, "Shift from the curtains' side opens them");
        check(ClickRules.door(false, true, false, true, true, true, true) == Does.CLOSE_CURTAINS, "and shuts them when open");
        check(ClickRules.door(false, true, false, true, false, true, false) == Does.OPEN_DOOR, "from the other side it is the door");
        check(ClickRules.door(true, true, true, true, true, true, false) == Does.CLOSE_DOOR, "walking, it is the door");
        check(ClickRules.door(false, true, false, false, true, true, false) == Does.OPEN_DOOR, "with no curtains, it is the door");
        check(ClickRules.door(false, true, false, true, true, false, false) == Does.OPEN_DOOR, "where the click is not taken, the world menu opens the door");
        check(ClickRules.door(true, false, false, true, true, true, false) == Does.CLOSE_DOOR, "without Shift, it is the door");
    }

    private static void aWindowTakesTheClickOnlyFromBesideIt() {
        check(ClickRules.window(false, null, false, true, false, false, false, false) == Does.OPEN_WINDOW, "a shut window beside the character opens");
        check(ClickRules.window(false, null, false, true, false, false, false, true) == Does.CLOSE_WINDOW, "an open one shuts");
        check(ClickRules.window(false, null, false, false, false, false, false, false) == null, "from further off the click does nothing");
        check(ClickRules.window(false, null, false, true, true, false, false, false) == null, "barricaded against the character, nothing");
        check(ClickRules.window(false, null, true, true, false, false, false, false) == null, "one that cannot be broken, nothing");
        check(ClickRules.window(false, null, false, true, false, true, false, false) == Does.CLIMB_THROUGH, "a smashed one is climbed through");
        check(ClickRules.window(false, null, false, true, false, true, true, false) == null, "unless it is barricaded on the far side");
        check(ClickRules.window(true, Does.CLOSE_CURTAINS, false, true, false, false, false, false) == Does.CLOSE_CURTAINS, "with Shift it is the curtains");
        check(ClickRules.window(true, Does.CLOSE_CURTAINS, false, false, false, true, false, false) == Does.CLOSE_CURTAINS,
                "from afar too, on a smashed one the world menu does not open");
        check(ClickRules.window(true, null, false, true, false, false, false, false) == null, "and with no curtains to work, nothing");
    }

    private static void aContainerClickFollowsTheGamesOrder() {
        check(ClickRules.click(true, null, false, true, false, false, true, false) == Does.LOOT, "a container within reach is looted");
        check(ClickRules.click(false, Does.DEVICE_OPTIONS, false, true, false, false, true, false) == null, "on a square the player has not seen, nothing at all");
        check(ClickRules.click(true, Does.OPEN_NAMED, false, true, false, false, true, false) == Does.OPEN_NAMED, "a workstation's window comes before its container");
        check(ClickRules.click(true, null, true, true, false, false, true, false) == null, "a safehouse that bars looting, nothing");
        check(ClickRules.click(true, null, false, true, false, true, true, false) == null, "a crate locked to the character, nothing");
        check(ClickRules.click(true, null, false, true, true, false, true, false) == null, "a container while the character aims, nothing");
        check(ClickRules.click(true, null, false, false, false, false, true, false) == null, "no container, nothing");
        check(ClickRules.click(true, null, false, true, false, false, false, true) == Does.WALK_TO, "further off, a walk to it where the game would walk");
        check(ClickRules.click(true, null, false, true, false, false, false, false) == null, "and nothing where it would not");
    }
}
