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
            Checks.run("theSmokeOfABlockRisesAsOneColumn", FiresTests::theSmokeOfABlockRisesAsOneColumn);
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
            // The world as the rows now are is what view 0 was last built with, as it was before.
            Own.taken(0);
        }
    }

    private static float[] square(int size, boolean smoke, boolean campfire) {
        float[] fire = new float[Fires.FLOATS];
        fire[Fires.X] = 10.5f;
        fire[Fires.Y] = 20.5f;
        Fires.square(size, smoke, campfire, fire, 0);
        return fire;
    }

    private static void aFireGrowsToItsHeightAndDiesAsSmoke() {
        // A fire that spreads, stage by stage as IsoFire.updateFromTimer moves it on, and a campfire as IsoFire.setLifeStage sets it.
        int[] spreads = {Fires.SMALL, Fires.SMALL, Fires.MEDIUM, Fires.HEIGHT, Fires.MEDIUM, Fires.DYING};
        for (int stage = 0; stage < spreads.length; stage++) {
            check(Fires.size(stage, false) == spreads[stage], "a spreading fire at stage " + stage + " is size " + spreads[stage]);
        }
        int[] permanent = {Fires.SMALL, Fires.MEDIUM, Fires.HEIGHT, Fires.MEDIUM, Fires.DYING};
        for (int stage = 0; stage < permanent.length; stage++) {
            check(Fires.size(stage, true) == permanent[stage], "a permanent fire at stage " + stage + " is size " + permanent[stage]);
        }
        float[] small = square(Fires.SMALL, false, false), medium = square(Fires.MEDIUM, false, false), height = square(Fires.HEIGHT, false, false),
                dying = square(Fires.DYING, false, false), smoke = square(Fires.SMALL, true, false), campfire = square(Fires.SMALL, false, true);
        check(small[Fires.TALL] < medium[Fires.TALL] && medium[Fires.TALL] < height[Fires.TALL], "the flames grow taller to the fire's height");
        check(small[Fires.SMOKE] == 0f && height[Fires.SMOKE] > medium[Fires.SMOKE], "a small flame has no smoke, and the fire at its height the most");
        check(height[Fires.LIGHT] > medium[Fires.LIGHT] && medium[Fires.LIGHT] > small[Fires.LIGHT], "and the most light");
        check(dying[Fires.FLAME] == 0f && dying[Fires.SMOKE] > 0f && dying[Fires.LIGHT] == 0f, "a dying fire is smoke alone, and gives no light");
        check(smoke[Fires.FLAME] == 0f && smoke[Fires.SMOKE] > 0f && smoke[Fires.LIGHT] == 0f, "a square of smoke is smoke alone");
        float[] dyingCampfire = square(Fires.DYING, false, true);
        check(dyingCampfire[Fires.FLAME] == 0f && dyingCampfire[Fires.LIGHT] == 0f, "a campfire burnt out is smoke alone too");
        check(campfire[Fires.TALL] < height[Fires.TALL] && campfire[Fires.SMOKE] < height[Fires.SMOKE] && campfire[Fires.LIGHT] > 0f,
                "a campfire is a small fire that lights round it");
        check(height[Fires.LIGHT_X] == 10.5f && height[Fires.LIGHT_Y] == 20.5f && height[Fires.LIGHT_UP] > 0f && height[Fires.LIGHT_UP] < height[Fires.TALL],
                "a square's light is over its middle, within its flames");
        check(height[Fires.RADIUS] <= 0.5f, "the flames of a square stay on it: " + height[Fires.RADIUS]);
    }

    private static void theSmokeOfABlockRisesAsOneColumn() {
        float[] fires = new float[5 * Fires.FLOATS];
        // A block of four burning at its height, from (10, 20), and a fire of its own two squares off.
        int[][] at = {{10, 20}, {11, 20}, {10, 21}, {11, 21}, {13, 20}};
        for (int i = 0; i < at.length; i++) {
            fires[i * Fires.FLOATS + Fires.X] = at[i][0] + 0.5f;
            fires[i * Fires.FLOATS + Fires.Y] = at[i][1] + 0.5f;
            Fires.square(Fires.HEIGHT, false, false, fires, i * Fires.FLOATS);
        }
        float one = fires[Fires.THICK];
        Fires.smokeInBlocks(fires, at.length);
        check(fires[Fires.SMOKE] > 0f && fires[Fires.FLOATS + Fires.SMOKE] == 0f && fires[2 * Fires.FLOATS + Fires.SMOKE] == 0f
                && fires[3 * Fires.FLOATS + Fires.SMOKE] == 0f, "the block's smoke rises from its first square alone");
        check(Math.abs(fires[Fires.THICK] - 2f * one) < 1e-5f, "twice as thick as one square's: " + fires[Fires.THICK]);
        check(fires[4 * Fires.FLOATS + Fires.SMOKE] > 0f, "a fire whose block's first square does not burn keeps its own");
        // A block whose first square is dying while the rest burn at their height.
        for (int i = 0; i < 4; i++) {
            Fires.square(i == 0 ? Fires.DYING : Fires.HEIGHT, false, false, fires, i * Fires.FLOATS);
        }
        Fires.smokeInBlocks(fires, 4);
        float[] height = square(Fires.HEIGHT, false, false);
        check(fires[Fires.TALL] == height[Fires.TALL] && fires[Fires.FLAME] == height[Fires.FLAME] && fires[Fires.SMOKE] == height[Fires.SMOKE],
                "the column rises off the block's biggest flames, lit by them");
        check(fires[Fires.FLOATS + Fires.FLAME] > 0f, "the flames of every square are kept");
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
        float[] fire = square(Fires.HEIGHT, false, false);
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
