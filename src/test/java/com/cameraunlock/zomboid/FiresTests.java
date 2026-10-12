package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/**
 * Tests of fire as first person draws it: how big a square's fire is through its life, where a
 * hearth's fire stands in its model however the model is turned, how its light flickers and
 * which lights are kept, the part of the picture a fire is drawn over, and that the OwnFire row
 * and the game's own look leave fire to the game's sprites.
 */
final class FiresTests {
    private FiresTests() {}

    static void run() {
        int mode = Quality.row();
        Quality.Picture custom = Quality.custom();
        int rows = Own.rows();
        Quality.set(Quality.HIGH);
        try {
            Checks.run("aFireGrowsToItsHeightAndDiesAsSmoke", FiresTests::aFireGrowsToItsHeightAndDiesAsSmoke);
            Checks.run("aHearthsFireStandsInItsModelHoweverTurned", FiresTests::aHearthsFireStandsInItsModelHoweverTurned);
            Checks.run("aStovesGlowIsOnItsDoorFacingOut", FiresTests::aStovesGlowIsOnItsDoorFacingOut);
            Checks.run("firelightFlickersAndNeverGoesOut", FiresTests::firelightFlickersAndNeverGoesOut);
            Checks.run("theStrongestLightsWhereTheEyeIsAreKept", FiresTests::theStrongestLightsWhereTheEyeIsAreKept);
            Checks.run("aFireIsDrawnOverThePartOfThePictureItIsIn", FiresTests::aFireIsDrawnOverThePartOfThePictureItIsIn);
            Checks.run("theRowAndTheGamesOwnLookLeaveFireToTheGame", FiresTests::theRowAndTheGamesOwnLookLeaveFireToTheGame);
            Checks.run("theClockStartsAgainEachHour", FiresTests::theClockStartsAgainEachHour);
        } finally {
            Own.set(rows);
            Quality.custom(custom);
            Quality.set(mode);
        }
    }

    private static float[] square(int stage, boolean smoke, boolean campfire) {
        float[] fire = new float[Fires.FLOATS];
        fire[Fires.X] = 10.5f;
        fire[Fires.Y] = 20.5f;
        Fires.square(stage, smoke, campfire, fire, 0);
        return fire;
    }

    private static void aFireGrowsToItsHeightAndDiesAsSmoke() {
        float[] small = square(0, false, false), growing = square(1, false, false), height = square(2, false, false), falling = square(3, false, false),
                dying = square(4, false, false), smoke = square(0, true, false), campfire = square(0, false, true);
        check(small[Fires.TALL] < growing[Fires.TALL] && growing[Fires.TALL] < height[Fires.TALL], "the flames grow taller from stage 0 to stage 2");
        check(falling[Fires.TALL] == growing[Fires.TALL] && falling[Fires.SMOKE] == growing[Fires.SMOKE], "stage 3 is stage 1 again on the way down");
        check(small[Fires.SMOKE] == 0f && height[Fires.SMOKE] > growing[Fires.SMOKE], "a small flame has no smoke, and the fire at its height the most");
        check(dying[Fires.FLAME] == 0f && dying[Fires.SMOKE] > 0f && dying[Fires.LIGHT] == 0f, "a fire at its last stage is smoke alone, and gives no light");
        check(smoke[Fires.FLAME] == 0f && smoke[Fires.SMOKE] > 0f && smoke[Fires.LIGHT] == 0f, "a square of smoke is smoke alone");
        check(campfire[Fires.TALL] < height[Fires.TALL] && campfire[Fires.SMOKE] < height[Fires.SMOKE] && campfire[Fires.LIGHT] > 0f,
                "a campfire is a small fire that lights round it");
        check(height[Fires.LIGHT_X] == 10.5f && height[Fires.LIGHT_Y] == 20.5f && height[Fires.LIGHT_UP] > 0f && height[Fires.LIGHT_UP] < height[Fires.TALL],
                "a square's light is over its middle, within its flames");
        check(height[Fires.RADIUS] <= 0.5f, "the flames of a square stay on it: " + height[Fires.RADIUS]);
    }

    private static void aHearthsFireStandsInItsModelHoweverTurned() {
        for (String model : new String[] {"fireplace", "burn_barrel", "cooking_pit", "cooking_pit_simple", "wood_stove"}) {
            check(Fires.hearth(model) != null, model + " holds a fire");
        }
        check(Fires.hearth("campfire") == null && Fires.hearth("counter") == null, "a model that holds none has no fire: a lit campfire is the square's own fire");
        float[] barrel = Fires.hearth("burn_barrel");
        float[] fire = new float[Fires.FLOATS];
        for (int turns = 0; turns < 4; turns++) {
            Fires.placeHearth(barrel, 5.5f, 7.5f, 1f, turns, 1f, 1f, 3f, fire, 0);
            check(Math.abs(fire[Fires.X] - 5.5f) < 1e-6f && Math.abs(fire[Fires.Y] - 7.5f) < 1e-6f, "a barrel's fire is over its middle at " + turns + " turns");
            check(Math.abs(fire[Fires.UP] - (1f + barrel[Fires.HEARTH_UP])) < 1e-6f && fire[Fires.FLAME] == 1f && fire[Fires.SEED] == 3f,
                    "on its ash, and burning");
        }
        float[] fireplace = Fires.hearth("fireplace");
        Fires.placeHearth(fireplace, 5.5f, 7.5f, 0f, 0, 0.8f, 0.9f, 0f, fire, 0);
        check(Math.abs(fire[Fires.RADIUS] - fireplace[Fires.HEARTH_RADIUS] * 0.8f) < 1e-6f && Math.abs(fire[Fires.TALL] - fireplace[Fires.HEARTH_TALL] * 0.9f) < 1e-6f,
                "a hearth's fire is as big as its model is scaled along the ground and up");
        check(fire[Fires.SMOKE] == 0f, "a fireplace's smoke goes up its chimney");
        // A fireplace faces north unturned: its light is out in front of it, to the map's -y.
        check(fire[Fires.LIGHT_Y] < fire[Fires.Y] && fire[Fires.LIGHT] > 0f, "a fireplace's light is out in front of its fire");
        Fires.placeHearth(fireplace, 5.5f, 7.5f, 0f, 1, 1f, 1f, 0f, fire, 0);
        check(fire[Fires.LIGHT_X] > fire[Fires.X] && Math.abs(fire[Fires.LIGHT_Y] - fire[Fires.Y]) < 1e-6f, "turned a quarter, to the east, its light is out to the east");
    }

    private static void aStovesGlowIsOnItsDoorFacingOut() {
        float[] stove = Fires.hearth("wood_stove");
        float[] fire = new float[Fires.FLOATS];
        float[] out = new float[2];
        for (int turns = 0; turns < 4; turns++) {
            Fires.placeHearth(stove, 0f, 0f, 0f, turns, 1f, 1f, 0f, fire, 0);
            Models.turn(turns, 0f, -1f, out);
            check(fire[Fires.KIND] == Fires.WINDOW && fire[Fires.OUT_X] == out[0] && fire[Fires.OUT_Y] == out[1], "the glow faces out of the stove's front at " + turns + " turns");
            float front = fire[Fires.X] * fire[Fires.OUT_X] + fire[Fires.Y] * fire[Fires.OUT_Y];
            check(front > 0.24f && front < 0.264f, "it is out past the door's face and behind the grille's bars: " + front);
            check(fire[Fires.ALONG_X] * fire[Fires.OUT_X] + fire[Fires.ALONG_Y] * fire[Fires.OUT_Y] == 0f, "along the door, across the way out");
        }
        check(fire[Fires.WIDE] > 0.1f && fire[Fires.HIGH] > 0.3f && fire[Fires.UP] > 0.25f, "as wide and high as the grille, from above the feet");
    }

    private static void firelightFlickersAndNeverGoesOut() {
        float least = 2f, most = -1f, last = Fires.flicker(0f, 1f);
        float moved = 0f;
        for (int i = 1; i < 6000; i++) {
            float now = Fires.flicker(i / 60f, 1f);
            least = Math.min(least, now);
            most = Math.max(most, now);
            moved = Math.max(moved, Math.abs(now - last));
            last = now;
        }
        check(least >= 0.6f && most <= 1f, "the light breathes between " + least + " and " + most);
        check(most - least > 0.25f, "and visibly: " + (most - least));
        check(moved < 0.2f, "from one frame to the next at 60 a second it changes by no more than " + moved);
        check(Fires.flicker(3f, 1f) != Fires.flicker(3f, 40f), "two fires do not flicker as one");
    }

    private static void theStrongestLightsWhereTheEyeIsAreKept() {
        int n = Fires.LIGHTS + 4;
        float[] fires = new float[n * Fires.FLOATS];
        for (int i = 0; i < n; i++) {
            int at = i * Fires.FLOATS;
            fires[at + Fires.LIGHT] = 1f;
            fires[at + Fires.LIGHT_X] = i * 3f;
        }
        // One that gives no light is passed over wherever it is.
        fires[Fires.LIGHT] = 0f;
        float[] out = new float[Fires.LIGHTS * 4], weight = new float[Fires.LIGHTS];
        Fires.lights(fires, n, 0f, 0f, 0f, 1f, out, weight);
        float furthest = 0f;
        for (int slot = 0; slot < Fires.LIGHTS; slot++) {
            check(out[slot * 4 + 3] > 0f, "every slot has a light");
            furthest = Math.max(furthest, out[slot * 4]);
        }
        check(furthest == Fires.LIGHTS * 3f, "the nearest that give light are kept, the far ones left: " + furthest);
        Fires.lights(fires, 2, 0f, 0f, 0f, 1f, out, weight);
        check(out[3] > 0f && out[7] == 0f, "one fire with light, then the end of the lights");
        Fires.lights(fires, 0, 0f, 0f, 0f, 1f, out, weight);
        check(out[3] == 0f, "no fire, no light");
    }

    private static void aFireIsDrawnOverThePartOfThePictureItIsIn() {
        ViewCamera.Pose eye = new ViewCamera.Pose();
        // Looking along +x from 1.6 tiles up.
        ViewCamera.clean(eye, 0f, 0f, 1.6f, 0f, 0f);
        float tan = (float) Math.tan(Math.toRadians(32.5)), tanX = tan * 16f / 9f;
        float[] fire = square(2, false, false);
        fire[Fires.X] = 10f;
        fire[Fires.Y] = 0f;
        float[] box = new float[4];
        check(FireDraw.box(fire, 0, 0f, 0f, eye, tanX, tan, 0f, 0f, 0.05f, box), "a fire ten tiles ahead is drawn");
        check(box[0] < 0f && box[2] > 0f && box[0] > -1f && box[2] < 1f, "over the middle of the picture, and not all of it across: " + box[0] + " to " + box[2]);
        check(box[1] > -1f && box[1] < 0f, "from the ground below the eye: " + box[1]);
        float[] drifted = new float[4];
        FireDraw.box(fire, 0, 0f, 1f, eye, tanX, tan, 0f, 0f, 0.05f, drifted);
        check(drifted[2] > box[2] && drifted[0] == box[0], "smoke carried to the right widens the part on the right alone");
        fire[Fires.X] = -10f;
        check(!FireDraw.box(fire, 0, 0f, 0f, eye, tanX, tan, 0f, 0f, 0.05f, box), "one behind the eye is not drawn");
        fire[Fires.X] = 0f;
        check(FireDraw.box(fire, 0, 0f, 0f, eye, tanX, tan, 0f, 0f, 0.05f, box) && box[0] == -1f && box[3] == 1f, "the eye in the fire is drawn over the whole picture");
    }

    private static void theRowAndTheGamesOwnLookLeaveFireToTheGame() {
        Own.set(Own.ALL);
        check(Fires.own(), "on, the picture draws its own fire");
        Own.taken(0);
        Own.set(Own.ALL & ~Own.FIRE);
        check(!Fires.own(), "the row off leaves fire to the game's sprites");
        check(Own.taken(0) && !Own.taken(0), "and switching it has the world built again, once");
        Own.set(Own.ALL);
        Quality.set(Quality.LOW);
        check(!Fires.own(), "Low is the game's own look, with the game's flames");
        Quality.set(Quality.HIGH);
    }

    private static void theClockStartsAgainEachHour() {
        long hour = 3_600_000_000_000L;
        check(FireDraw.seconds(hour + 2_000_000_000L) == 2f && FireDraw.seconds(hour - 1_000_000_000L) == 3599f, "the flames' clock counts the seconds within the hour");
    }
}
