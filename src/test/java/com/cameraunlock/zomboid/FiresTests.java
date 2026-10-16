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
            Checks.run("aFiresFlamesAreTheLoopScaledToIt", FiresTests::aFiresFlamesAreTheLoopScaledToIt);
            Checks.run("theGasGlowsAsABlackBody", FiresTests::theGasGlowsAsABlackBody);
            Checks.run("embersRiseOffTheFlamesAndCool", FiresTests::embersRiseOffTheFlamesAndCool);
            Checks.run("theJarHoldsTheFlamesLoop", FiresTests::theJarHoldsTheFlamesLoop);
            Checks.run("theGamesFlamesAndSmokeAreToldByName", FiresTests::theGamesFlamesAndSmokeAreToldByName);
            Checks.run("aHearthWithNoModelBurnsAsItsSpriteShows", FiresTests::aHearthWithNoModelBurnsAsItsSpriteShows);
            Checks.run("theShaderLooksAFireShadowUpInTheFaceItWasDrawnIn", FiresTests::theShaderLooksAFireShadowUpInTheFaceItWasDrawnIn);
            Checks.run("aFireKeepsItsShadowSlot", FiresTests::aFireKeepsItsShadowSlot);
            Checks.run("aFiresShadowIsDrawnAgainWhenABuildInItsReachIs", FiresTests::aFiresShadowIsDrawnAgainWhenABuildInItsReachIs);
            Checks.run("theEyeStopsDownForFlamesAtNight", FiresTests::theEyeStopsDownForFlamesAtNight);
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
        Fires.lights(fires, n, 0f, 0f, 0f, 1f, out, new float[Fires.LIGHTS], new boolean[Fires.LIGHTS], weight);
        float furthest = 0f;
        for (int slot = 0; slot < Fires.LIGHTS; slot++) {
            check(out[slot * 4 + 3] > 0f, "every slot has a light");
            furthest = Math.max(furthest, out[slot * 4]);
        }
        check(furthest == Fires.LIGHTS * 3f, "the nearest that give light are kept, the far ones left: " + furthest);
        Fires.lights(fires, 2, 0f, 0f, 0f, 1f, out, new float[Fires.LIGHTS], new boolean[Fires.LIGHTS], weight);
        check(out[3] > 0f && out[7] == 0f, "one fire with light, then the end of the lights");
        Fires.lights(fires, 0, 0f, 0f, 0f, 1f, out, new float[Fires.LIGHTS], new boolean[Fires.LIGHTS], weight);
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
        check(FireDraw.box(fire, 0, 0f, 0f, 0f, 0f, eye, tanX, tan, 0f, 0f, 0.05f, box), "a fire ten tiles ahead is drawn");
        check(box[0] < 0f && box[2] > 0f && box[0] > -1f && box[2] < 1f, "over the middle of the picture, and not all of it across: " + box[0] + " to " + box[2]);
        check(box[1] > -1f && box[1] < 0f, "from the ground below the eye: " + box[1]);
        float[] drifted = new float[4];
        FireDraw.box(fire, 0, 0f, 1f, 0f, 0f, eye, tanX, tan, 0f, 0f, 0.05f, drifted);
        check(drifted[2] > box[2] && drifted[0] == box[0], "smoke carried to the right widens the part on the right alone");
        float[] small = square(Fires.SMALL, false, false), bare = new float[4], flamed = new float[4];
        small[Fires.X] = 10f;
        small[Fires.Y] = 0f;
        FireDraw.box(small, 0, 0f, 0f, 0f, 0f, eye, tanX, tan, 0f, 0f, 0.05f, bare);
        FireDraw.box(small, 0, 0f, 0f, 1f, 2f, eye, tanX, tan, 0f, 0f, 0.05f, flamed);
        check(flamed[3] > bare[3] && flamed[0] < bare[0], "flames reaching higher and wider than a fire's own box widen the part they are in: " + flamed[3] + " over " + bare[3]);
        fire[Fires.X] = -10f;
        check(!FireDraw.box(fire, 0, 0f, 0f, 0f, 0f, eye, tanX, tan, 0f, 0f, 0.05f, box), "one behind the eye is not drawn");
        fire[Fires.X] = 0f;
        check(FireDraw.box(fire, 0, 0f, 0f, 0f, 0f, eye, tanX, tan, 0f, 0f, 0.05f, box) && box[0] == -1f && box[3] == 1f, "the eye in the fire is drawn over the whole picture");
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

    private static void aFiresFlamesAreTheLoopScaledToIt() {
        FlameVolume.Loop loop = new FlameVolume.Loop(64, 64, 160, 32, 8, 4, 3f, 0.6f, 0.4f, 24f, 0.0125f, 30f, 140f, null, null);
        float[] fire = square(Fires.HEIGHT, false, false), cell = new float[2];
        FireDraw.cellSize(fire, 0, loop, cell);
        check(Math.abs(cell[0] * loop.reach() * loop.high() - fire[Fires.TALL]) < 1e-4f, "the loop's flames reach as high as the fire's: " + cell[0]);
        check(cell[1] >= cell[0] * FireDraw.NARROWEST && cell[1] <= cell[0] * FireDraw.WIDEST, "and are stretched across no more than allowed: " + cell[1] / cell[0]);
        fire[Fires.RADIUS] = 0.001f;
        FireDraw.cellSize(fire, 0, loop, cell);
        check(cell[1] == cell[0] * FireDraw.NARROWEST, "a fire with next to no width is squeezed only so far");
        fire = square(Fires.HEIGHT, false, false);
        float[] small = square(Fires.SMALL, false, false), smallCell = new float[2];
        FireDraw.cellSize(fire, 0, loop, cell);
        FireDraw.cellSize(small, 0, loop, smallCell);
        float bigGoes = FireDraw.played(loop, cell[0], 1f, 0f) - FireDraw.played(loop, cell[0], 0f, 0f);
        float smallGoes = FireDraw.played(loop, smallCell[0], 1f, 0f) - FireDraw.played(loop, smallCell[0], 0f, 0f);
        check(bigGoes > 0f && smallGoes > bigGoes, "a big fire's flames go round slower than a small one's: " + bigGoes + " and " + smallGoes + " frames a second");
        for (float seconds = 0f; seconds < 600f; seconds += 0.37f) {
            float at = (FireDraw.played(loop, cell[0], seconds, 4.4f) % loop.frames());
            check(at >= 0f && at < loop.frames(), "a fire is always somewhere in the loop: " + at);
        }
        check((FireDraw.played(loop, cell[0], 5f, 1f) % loop.frames()) != (FireDraw.played(loop, cell[0], 5f, 2f) % loop.frames()), "two fires are at places in the loop of their own");
    }

    private static void theGasGlowsAsABlackBody() {
        float[] colour = new float[3];
        FireDraw.blackbody(1000f, colour, 0);
        check(colour[0] == 1f && colour[1] < 0.2f && colour[2] < 0.01f, "at 1000 K deep red: " + colour[0] + " " + colour[1] + " " + colour[2]);
        FireDraw.blackbody(2800f, colour, 0);
        check(colour[0] == 1f && colour[1] > 0.4f && colour[2] > 0.1f && colour[2] < colour[1], "at 2800 K a warm yellow white: " + colour[1] + " " + colour[2]);
        float[] ramp = FireDraw.glowRamp();
        check(ramp[0] == 0f && ramp[1] == 0f && ramp[2] == 0f, "cold gas gives no light");
        for (int i = 1; i < 16; i++) {
            float before = ramp[i * 3 - 3] + ramp[i * 3 - 2] + ramp[i * 3 - 1], now = ramp[i * 3] + ramp[i * 3 + 1] + ramp[i * 3 + 2];
            check(now > before, "hotter gas is brighter, step " + i);
            // The coldest step gives no light, so has no colour to be yellower than.
            check(i < 2 || ramp[i * 3 + 1] / ramp[i * 3] >= ramp[i * 3 - 2] / ramp[i * 3 - 3] - 1e-6f, "and yellower, step " + i);
        }
        check(Math.abs(ramp[45] - FireDraw.GLOW) < 1e-3f, "the hottest at the flames' whole glow in its brightest channel");
    }

    private static void embersRiseOffTheFlamesAndCool() {
        float[] at = new float[4], later = new float[4];
        float radius = 0.5f, tall = 2.2f;
        // An ember wanders by up to 0.18 t squared each way, across both axes at once, in a flight of at most 1.4 lives.
        float flight = FireCards.EMBER_LIFE * 1.4f, farthest = radius * 0.8f + 0.18f * flight * flight * (float) Math.sqrt(2.0);
        int flying = 0, rose = 0, pairs = 0;
        for (int i = 0; i < FireCards.EMBERS; i++) {
            for (float seconds = 0f; seconds < 10f; seconds += 0.05f) {
                FireCards.ember(i, 3.3f, seconds, radius, tall, at);
                check(at[3] >= 0f && at[3] <= 1f, "an ember's heat is 0 to 1: " + at[3]);
                check(at[0] >= tall * 0.25f - 1e-5f, "it is thrown up from among the flames: " + at[0]);
                check((float) Math.hypot(at[1], at[2]) <= farthest, "it wanders no further than the hot air takes it: " + Math.hypot(at[1], at[2]));
                flying += at[3] > 0.05f ? 1 : 0;
                FireCards.ember(i, 3.3f, seconds + 0.02f, radius, tall, later);
                // A moment later in the same flight it is higher and cooler.
                if (at[3] > 0.1f && later[3] > 0.1f && later[3] < at[3]) {
                    pairs++;
                    rose += later[0] > at[0] ? 1 : 0;
                }
            }
        }
        check(flying > FireCards.EMBERS * 100, "most of the time most embers are flying: " + flying);
        check(pairs > 1000 && rose == pairs, "an ember cooling in flight always rises: " + rose + " of " + pairs);
    }

    private static void theJarHoldsTheFlamesLoop() {
        // FlameVolume.read throws for a loop that is missing, of another version, laid out of its frames, or short of its cells.
        FlameVolume.Loop loop = FlameVolume.read();
        check(loop.flame().capacity() == loop.wide() * loop.deep() * loop.high() * loop.frames() && loop.heat().capacity() * 8 == loop.flame().capacity(),
                "the flames' loop is in the jar, its heat at half its cells each way");
        check(loop.filled() > loop.wide() * 0.25f && loop.filled() < loop.wide() * 0.75f && loop.filledHigh() > loop.high() * loop.reach()
                && loop.filledHigh() <= loop.high(), "the flames fill part of the loop's box, which the march keeps to: " + loop.filled() + " " + loop.filledHigh());
        check(loop.reach() > 0.2f && loop.reach() < 1f && loop.root() > 0.1f && loop.root() < 1f && loop.rise() > 0f && loop.framesASecond() > 0f,
                "with its flames' reach and root, rise and pace: " + loop.reach() + " " + loop.root() + " " + loop.rise() + " " + loop.framesASecond());
    }

    private static void theGamesFlamesAndSmokeAreToldByName() {
        check(Fires.isLook("Fire01", "Fire") && Fires.isLook("Fire03", "Fire") && Fires.isLook("Smoke02", "Smoke"), "the game's animations of flames and smoke");
        check(!Fires.isLook("Fireplace", "Fire") && !Fires.isLook("Fire_01_3", "Fire") && !Fires.isLook("fixtures_fireplaces_01_0", "Fire")
                && !Fires.isLook("Fire1", "Fire") && !Fires.isLook(null, "Fire"), "and nothing else, a fireplace's sprite or a frame's name among it");
    }

    private static void aHearthWithNoModelBurnsAsItsSpriteShows() {
        float[] fire = new float[Fires.FLOATS];
        Fires.placeHearth(Fires.unmodelled("crafted_02_42"), 3.5f, 4.5f, 0f, 0, 1f, 1f, 0f, fire, 0);
        check(fire[Fires.FLAME] == 1f && fire[Fires.UP] > 0.3f && fire[Fires.TALL] > 0f, "a brazier burns in its basket, up off the floor");
        Fires.placeHearth(Fires.unmodelled("crafted_05_6"), 3.5f, 4.5f, 0f, 0, 1f, 1f, 0f, fire, 0);
        check(fire[Fires.FLAME] == 0f && fire[Fires.SMOKE] > 0f && fire[Fires.UP] > 2f, "a forge is shut: it smokes at its chimney's top and shows no flame");
        check(fire[Fires.LIGHT] > 0f && fire[Fires.LIGHT_UP] > 0.2f && fire[Fires.LIGHT_UP] < 0.8f, "and glows low on its body: " + fire[Fires.LIGHT_UP]);
        Fires.placeHearth(Fires.unmodelled("some_other_hearth"), 3.5f, 4.5f, 0f, 0, 1f, 1f, 0f, fire, 0);
        check(fire[Fires.FLAME] == 1f && fire[Fires.X] == 3.5f && fire[Fires.Y] == 4.5f && fire[Fires.CEILING] == 0f, "any other burns on the middle of its square");
        check(Fires.unmodelled("crafted_02_43") == Fires.unmodelled("crafted_05_12") && Fires.unmodelled("crafted_05_4") != Fires.unmodelled("crafted_05_6"),
                "only the brazier the game lights and the crafted ovens are told apart, each oven by its chimney");
        check(fire[Fires.LOOK] == 0.5f && square(Fires.HEIGHT, false, false)[Fires.LOOK] == 1f, "the game draws a hearth's flames at half the scale of a square's");
        Fires.placeHearth(Fires.BARBECUE, 3.5f, 4.5f, 0f, 0, 1f, 1f, 0f, fire, 0);
        check(fire[Fires.FLAME] == 0f && fire[Fires.SMOKE] > 0f && fire[Fires.LIGHT_UP] < fire[Fires.UP], "a lit barbecue smokes off its grill and glows under it");
    }

    /**
     * FireShadows.GLSL picks a face by the way a point lies from the fire and projects it with
     * axes of its own; the faces are drawn with FireShadows.face, ViewCamera.view and
     * ViewCamera.projection. For points all round, the two agree on the face's picture and depth.
     */
    private static void theShaderLooksAFireShadowUpInTheFaceItWasDrawnIn() {
        float far = 8f;
        float[] view = new float[16], projection = new float[16];
        ViewCamera.projection(1f, 1f, FireShadows.NEAR, far, projection);
        ViewCamera.Pose pose = new ViewCamera.Pose();
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < 2000; i++) {
            float dx = random.nextFloat() * 2f - 1f, dy = random.nextFloat() * 2f - 1f, du = random.nextFloat() * 2f - 1f;
            float ax = Math.abs(dx), ay = Math.abs(dy), au = Math.abs(du);
            int face;
            float[] forward, right, up;
            // FireShadows.GLSL's choice of face and its axes.
            if (au >= ax && au >= ay) {
                face = du > 0f ? 4 : 5;
                forward = new float[] {0f, 0f, Math.signum(du)};
                right = new float[] {0f, 1f, 0f};
                up = new float[] {-Math.signum(du), 0f, 0f};
            } else if (ax >= ay) {
                face = dx > 0f ? 0 : 2;
                forward = new float[] {Math.signum(dx), 0f, 0f};
                right = new float[] {0f, Math.signum(dx), 0f};
                up = new float[] {0f, 0f, 1f};
            } else {
                face = dy > 0f ? 1 : 3;
                forward = new float[] {0f, Math.signum(dy), 0f};
                right = new float[] {-Math.signum(dy), 0f, 0f};
                up = new float[] {0f, 0f, 1f};
            }
            float ahead = dx * forward[0] + dy * forward[1] + du * forward[2];
            float sx = (dx * right[0] + dy * right[1] + du * right[2]) / ahead, sy = (dx * up[0] + dy * up[1] + du * up[2]) / ahead;
            float near = FireShadows.NEAR, sz = (far + near) / (far - near) - 2f * far * near / ((far - near) * ahead);
            // The face as it was drawn.
            FireShadows.face(face, 0f, 0f, 0f, pose);
            ViewCamera.view(pose, view);
            float[] eye = new float[4], clip = new float[4];
            for (int row = 0; row < 4; row++) {
                eye[row] = view[row] * dx + view[4 + row] * dy + view[8 + row] * du + view[12 + row];
            }
            for (int row = 0; row < 4; row++) {
                clip[row] = projection[row] * eye[0] + projection[4 + row] * eye[1] + projection[8 + row] * eye[2] + projection[12 + row] * eye[3];
            }
            float nx = clip[0] / clip[3], ny = clip[1] / clip[3], nz = clip[2] / clip[3];
            check(Math.abs(nx - sx) < 1e-4f && Math.abs(ny - sy) < 1e-4f && Math.abs(nz - sz) < 1e-4f,
                    "face " + face + ": drawn at " + nx + ", " + ny + ", " + nz + ", looked up at " + sx + ", " + sy + ", " + sz);
            check(Math.abs(sx) <= 1.0001f && Math.abs(sy) <= 1.0001f, "inside the face it is looked up in");
        }
    }

    private static final boolean[] FLAMING = {true, true, true, true, true, true, true, true};
    private static final java.util.ArrayList<WorldScene.ChunkMeshes> NONE_BUILT = new java.util.ArrayList<>();
    /** A frame at 60 a second, in nanoseconds. */
    private static final long FRAME = 16_666_667L;

    private static void aFireKeepsItsShadowSlot() {
        FireShadows shadows = new FireShadows();
        float[] lights = new float[Fires.LIGHTS * 4], reach = new float[Fires.LIGHTS], weight = new float[Fires.LIGHTS];
        // Two fires, the second the stronger.
        lights[0] = 1f; lights[1] = 2f; lights[2] = 0.5f; lights[3] = 1f; reach[0] = 8f; weight[0] = 1f;
        lights[4] = 5f; lights[5] = 6f; lights[6] = 0.5f; lights[7] = 1f; reach[1] = 12f; weight[1] = 2f;
        shadows.assign(lights, reach, weight, FLAMING, NONE_BUILT, 1, FRAME, 2, 256);
        check(shadows.toDraw == 2 && shadows.slotOf[0] >= 0f && shadows.slotOf[1] >= 0f && shadows.slotOf[0] != shadows.slotOf[1],
                "two new fires get a slot each, drawn at once");
        float first = shadows.slotOf[0], second = shadows.slotOf[1];
        check(shadows.from[(int) second * 4 + 3] == 12f + FireShadows.PAST_REACH, "a slot sees as far as its fire's light reaches and a little more");
        // The two swap places in the list and the first loses some light: each keeps its slot.
        float[] swapped = new float[Fires.LIGHTS * 4], swappedReach = new float[Fires.LIGHTS], swappedWeight = new float[Fires.LIGHTS];
        System.arraycopy(lights, 4, swapped, 0, 4);
        System.arraycopy(lights, 0, swapped, 4, 4);
        swappedReach[0] = 12f; swappedReach[1] = 8f; swappedWeight[0] = 2f; swappedWeight[1] = 1.5f;
        shadows.assign(swapped, swappedReach, swappedWeight, FLAMING, NONE_BUILT, 2, 2 * FRAME, 2, 256);
        check(shadows.slotOf[0] == second && shadows.slotOf[1] == first && shadows.toDraw == 0, "each keeps its slot and nothing is drawn again: " + shadows.toDraw);
        shadows.assign(swapped, swappedReach, swappedWeight, FLAMING, NONE_BUILT, 3, 2 * FRAME + FireShadows.STALE_NANOS, 2, 256);
        check(shadows.toDraw >= 1, "a slot left long enough is drawn again");
        // With one slot, only the stronger fire has a shadow.
        FireShadows one = new FireShadows();
        one.assign(lights, reach, weight, FLAMING, NONE_BUILT, 1, FRAME, 1, 256);
        check(one.slotOf[1] == 0f && one.slotOf[0] == -1f, "with one slot the strongest fire has it, and the other none");
        one.assign(lights, reach, weight, FLAMING, NONE_BUILT, 2, 2 * FRAME, 0, 256);
        check(one.slotOf[0] == -1f && one.slotOf[1] == -1f && one.toDraw == 0, "with none, no fire throws a shadow");
        // One slot, held by the stronger fire: the other must outweigh it by HELD to take it.
        FireShadows hold = new FireShadows();
        hold.assign(lights, reach, weight, FLAMING, NONE_BUILT, 1, FRAME, 1, 256);
        float[] closer = weight.clone();
        closer[0] = 2.5f;
        hold.assign(lights, reach, closer, FLAMING, NONE_BUILT, 2, 2 * FRAME, 1, 256);
        check(hold.slotOf[1] == 0f && hold.slotOf[0] == -1f, "a fire a little stronger than the one holding the slot does not take it");
        closer[0] = 3.5f;
        hold.assign(lights, reach, closer, FLAMING, NONE_BUILT, 3, 3 * FRAME, 1, 256);
        check(hold.slotOf[0] == 0f && hold.slotOf[1] == -1f && hold.toDraw == 1, "one far stronger does, and its faces are drawn");
        hold.assign(lights, reach, closer, FLAMING, NONE_BUILT, 4, 4 * FRAME, 1, 512);
        check(hold.toDraw == 1 && hold.face == 512, "at another face size every slot is drawn again");
        boolean[] shut = {false, true, true, true, true, true, true, true};
        FireShadows ovens = new FireShadows();
        ovens.assign(lights, reach, closer, shut, NONE_BUILT, 1, FRAME, 2, 256);
        check(ovens.slotOf[0] == -1f && ovens.slotOf[1] >= 0f, "a light with no flames to see, a shut oven's, has no slot");
    }

    /** A build with runs whose box is from (x, y, 0) to a tile and a storey past it. */
    private static WorldScene.ChunkMeshes builtAt(float x, float y) {
        WorldScene.ChunkMeshes build = new WorldScene.ChunkMeshes(0);
        build.runs = 1;
        build.minX = x;
        build.minY = y;
        build.minUp = 0f;
        build.maxX = x + 1f;
        build.maxY = y + 1f;
        build.maxUp = 1f;
        return build;
    }

    private static void aFiresShadowIsDrawnAgainWhenABuildInItsReachIs() {
        float[] lights = new float[Fires.LIGHTS * 4], reach = new float[Fires.LIGHTS], weight = new float[Fires.LIGHTS];
        lights[0] = 10f; lights[1] = 10f; lights[2] = 0.5f; lights[3] = 1f; reach[0] = 6f; weight[0] = 1f;
        WorldScene.ChunkMeshes near = builtAt(12f, 10f), far = builtAt(40f, 10f);
        java.util.ArrayList<WorldScene.ChunkMeshes> drawn = new java.util.ArrayList<>(java.util.List.of(near, far));
        FireShadows shadows = new FireShadows();
        shadows.assign(lights, reach, weight, FLAMING, drawn, 1, FRAME, 1, 256);
        check(shadows.toDraw == 1, "a new fire's faces are drawn");
        shadows.assign(lights, reach, weight, FLAMING, drawn, 2, 2 * FRAME, 1, 256);
        check(shadows.toDraw == 0, "nothing changed, nothing is drawn again");
        far.version++;
        shadows.assign(lights, reach, weight, FLAMING, drawn, 3, 3 * FRAME, 1, 256);
        check(shadows.toDraw == 0, "a build out of the fire's reach made again draws nothing again");
        near.version++;
        shadows.assign(lights, reach, weight, FLAMING, drawn, 4, 4 * FRAME, 1, 256);
        check(shadows.toDraw == 1, "a build within it made again draws its faces again");
        shadows.assign(lights, reach, weight, FLAMING, drawn, 5, 5 * FRAME, 1, 256);
        check(shadows.toDraw == 0, "once");
        drawn.remove(near);
        shadows.assign(lights, reach, weight, FLAMING, drawn, 6, 6 * FRAME, 1, 256);
        check(shadows.toDraw == 1, "and a build within it gone does too");
        shadows.assign(lights, reach, weight, FLAMING, drawn, 7, 6 * FRAME + FireShadows.STALE_NANOS, 1, 256);
        check(shadows.toDraw == 1, "the faces are drawn again after a while whatever: the wind moves the plants");
    }

    private static void theEyeStopsDownForFlamesAtNight() {
        check(FireCards.stoppedDown(0.3f) == 1f && FireCards.stoppedDown(FireCards.DAY_EXPOSURE) == 1f, "by day the flames are drawn with all their light");
        float night = 1.5f, shown = night * FireCards.stoppedDown(night), day = FireCards.DAY_EXPOSURE;
        check(shown > day && shown < 2f * day, "at night they show a little brighter than by day, not three times as bright: " + shown / day);
        check(FireCards.stoppedDown(1f) > FireCards.stoppedDown(1.5f), "the more the eye has opened, the more it stops down");
    }
}
