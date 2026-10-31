package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

final class GlFloorTests {
    private GlFloorTests() {}

    static void run() {
        Checks.run("aContextWithAllOfTheFloorIsNotRefused", GlFloorTests::aContextWithAllOfTheFloorIsNotRefused);
        Checks.run("eachThingMissingIsNamed", GlFloorTests::eachThingMissingIsNamed);
    }

    private static void aContextWithAllOfTheFloorIsNotRefused() {
        check(GlFloor.shortOf(true, true) == null, "OpenGL 3.3 with EXT_texture_array is the floor, and first person is drawn on it");
    }

    private static void eachThingMissingIsNamed() {
        String old = GlFloor.shortOf(false, true);
        check(old != null && old.contains("older than 3.3") && !old.contains("has no"), "below 3.3 alone is said as that: " + old);
        String array = GlFloor.shortOf(true, false);
        check(array != null && array.contains("has no EXT_texture_array") && !array.contains("3.3"), "a missing texture array is named: " + array);
        String all = GlFloor.shortOf(false, false);
        check(all != null && all.contains("older than 3.3 and has no EXT_texture_array"), "everything missing is said at once: " + all);
        check(all.contains("drawn as it is without the mod"), "and the line says the game goes on without it: " + all);
    }
}
