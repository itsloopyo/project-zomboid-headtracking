package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tests of how wet the viewer's hands and what they hold get in the rain: Soaked's sums, and the shaders' text, since nothing here runs a shader. */
final class SoakedTests {
    private SoakedTests() {}

    static void run() {
        Checks.run("heavyRainWetsTheViewerThroughInAboutAMinute", SoakedTests::heavyRainWetsTheViewerThroughInAboutAMinute);
        Checks.run("aDrizzleWetsMoreSlowlyAndNoRainWetsNothing", SoakedTests::aDrizzleWetsMoreSlowlyAndNoRainWetsNothing);
        Checks.run("outOfTheRainTheWaterDriesOverAFewMinutes", SoakedTests::outOfTheRainTheWaterDriesOverAFewMinutes);
        Checks.run("wettingAndDryingAreTheSameAtAnyFrameRate", SoakedTests::wettingAndDryingAreTheSameAtAnyFrameRate);
        Checks.run("snowThatMeltsOnTheViewerLeavesItsWater", SoakedTests::snowThatMeltsOnTheViewerLeavesItsWater);
        Checks.run("clothesTheGameSoakedStayDarkAfterTheWaterHasDried", SoakedTests::clothesTheGameSoakedStayDarkAfterTheWaterHasDried);
        Checks.run("theViewersArmsAndGunTakeTheWet", SoakedTests::theViewersArmsAndGunTakeTheWet);
        Checks.run("lowAndTheSwitchLeaveTheViewerDry", SoakedTests::lowAndTheSwitchLeaveTheViewerDry);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** The water after seconds in steps of step, from water, in rain that heavy, sheltered or not. */
    private static float after(float water, float rain, boolean sheltered, float seconds, float step) {
        for (float done = 0f; done < seconds - step * 0.5f; done += step) {
            water = Soaked.water(water, rain, sheltered, 0f, step);
        }
        return water;
    }

    private static void heavyRainWetsTheViewerThroughInAboutAMinute() {
        check(after(0f, 1f, false, 1f, 1f / 60f) > 0f, "a second of rain starts to wet them");
        float half = after(0f, 1f, false, 30f, 1f / 60f);
        check(Math.abs(half - 0.5f) < 0.01f, "half a minute of the heaviest rain wets them half way: " + half);
        check(after(0f, 1f, false, Soaked.SECONDS_TO_WET, 1f / 60f) > 0.99f, "a minute wets them through");
        check(after(0f, 1f, false, 600f, 1f / 60f) == 1f, "and they never get wetter than wet through");
        check(Soaked.SECONDS_TO_WET >= 45f && Soaked.SECONDS_TO_WET <= 90f, "about a minute");
    }

    private static void aDrizzleWetsMoreSlowlyAndNoRainWetsNothing() {
        float heavy = after(0f, 1f, false, 20f, 0.05f), drizzle = after(0f, 0.1f, false, 20f, 0.05f);
        check(drizzle > 0f && drizzle < heavy, "a drizzle wets, more slowly than a downpour: " + drizzle + " against " + heavy);
        check(after(0f, Soaked.NO_RAIN, false, 60f, 0.05f) == 0f, "rain too light to wet the ground wets nobody");
        check(after(0.5f, Soaked.NO_RAIN, false, 10f, 0.05f) < 0.5f, "and in it they dry");
        check(after(0f, 1f, true, 60f, 0.05f) == 0f, "under a roof, indoors or in a vehicle the rain does not reach them");
    }

    private static void outOfTheRainTheWaterDriesOverAFewMinutes() {
        float minute = after(1f, 0f, true, 60f, 1f / 30f);
        check(minute > 0.6f && minute < 0.9f, "a minute out of it they are still wet: " + minute);
        check(after(1f, 0f, true, Soaked.SECONDS_TO_DRY + 1f, 1f / 30f) == 0f, "and dry after SECONDS_TO_DRY");
        check(Soaked.SECONDS_TO_DRY >= 120f && Soaked.SECONDS_TO_DRY <= 360f, "a few minutes");
        check(Soaked.SECONDS_TO_DRY > Soaked.SECONDS_TO_WET, "they dry more slowly than they wet");
        check(after(1f, 1f, false, 0f, 0.1f) == 1f && Soaked.water(0.4f, 1f, false, 0f, 0f) == 0.4f, "no time, no change");
    }

    private static void wettingAndDryingAreTheSameAtAnyFrameRate() {
        float fast = after(0f, 0.7f, false, 25f, 1f / 144f), slow = after(0f, 0.7f, false, 25f, 1f / 20f);
        check(Math.abs(fast - slow) < 1e-3f, "wetting at 144 and at 20 frames a second: " + fast + " and " + slow);
        float dryFast = after(0.9f, 0f, true, 50f, 1f / 144f), drySlow = after(0.9f, 0f, true, 50f, 1f / 20f);
        check(Math.abs(dryFast - drySlow) < 1e-3f, "drying the same: " + dryFast + " and " + drySlow);
    }

    private static void snowThatMeltsOnTheViewerLeavesItsWater() {
        check(Math.abs(Soaked.water(0f, 0f, true, 0.5f, 0f) - 0.5f * Soaked.MELT_WETS) < 1e-6f, "the snow that melts is water on them at once");
        check(Soaked.water(0.9f, 0f, true, 1f, 0f) == 1f, "never more than wet through");
        check(Soaked.water(0.3f, 0f, true, -0.2f, 0f) == 0.3f, "snow gathering on them is no water");
        String soaked = source("Soaked.java").replaceAll("\\s+", "");
        check(soaked.contains("floatmelted=sheltered?dustBefore[view]-dust:0f;"), "what is lost of the snow on them indoors is what melted");
    }

    private static void clothesTheGameSoakedStayDarkAfterTheWaterHasDried() {
        check(Soaked.soak(0f, 80f) == 0.8f, "the game's wetness of the arms soaks them though no water stands on them");
        check(Soaked.soak(0.9f, 10f) == 0.9f, "the water on them soaks them where the game has them drier");
        check(Soaked.soak(0f, 250f) == 1f && Soaked.soak(0f, -5f) == 0f, "between dry and wet through");
        String soaked = source("Soaked.java").replaceAll("\\s+", "");
        check(soaked.contains("type==BodyPartType.Hand_L||type==BodyPartType.Hand_R||type==BodyPartType.ForeArm_L||type==BodyPartType.ForeArm_R"),
                "the game's wetness read is the hands' and the forearms'");
    }

    private static void theViewersArmsAndGunTakeTheWet() {
        check(!Soaked.GLSL.contains("BEAD_CELLS") && !Soaked.GLSL.contains("RUN_PACE") && !Soaked.GLSL.contains("BEAD_HIGH"),
                "the shader text has the numbers in it, not their names");
        String glsl = Soaked.GLSL.replaceAll("\\s+", "");
        check(glsl.contains("if(uWet.x<=0.0){return0.0;}"), "no water where none stands");
        check(glsl.contains("step(wetChance(cell),uWet.x*smoothstep(-0.3,0.6,up))"), "beads sit where the surface faces up, more the wetter it is");
        check(glsl.contains("smoothstep(0.3,0.8,1.0-abs(up))"), "drops run only down steep faces");
        String arms = source("OwnArms.java").replaceAll("\\s+", "");
        check(arms.contains("+SnowShader.ON_PEOPLE+Soaked.GLSL+"), "the mod's arms have it");
        check(count(arms, "facing=wetFacing(facing,vSeen,vRest,water);") == 2, "on sleeve and on skin");
        String gun = source("OwnGun.java").replaceAll("\\s+", "");
        check(gun.contains("+PipelineShaders.BEAMS+Soaked.GLSL+") && gun.contains("n=wetFacing(n,at,vOwn,water);"), "and the mod's guns");
        check(gun.contains("vec3dull=tint.rgb*(1.0-metal)*(1.0-0.35*uWet.y);"), "wood and polymer darken, metal does not");
        check(gun.contains("float[]wet=Soaked.shown(view.index);"), "a gun is as wet as the viewer holding it");
        String people = source("CharacterDraw.java").replaceAll("\\s+", "");
        check(people.contains("if(uWet.y>0.0&&!hair&&snowLies<=0.5){"), "the game's hands and the viewer's body darken and shine, hair and snow aside");
        check(people.contains("wetOn=NOT_WET;"), "and nobody else does: each person begun is dry until said otherwise");
        String drawing = source("Characters.java").replaceAll("\\s+", "");
        check(drawing.contains("float[]wet=Soaked.on(view.index,data.character,dust);if(ownArms){OwnArms.snowed(dust);OwnArms.wetted(wet);")
                && drawing.contains("OwnArms.snowed(dust);OwnArms.wetted(Soaked.on(view.index,data.character,dust));"),
                "the viewer's arms with a weapon and at a task are wet");
        check(drawing.contains("CharacterDraw.snowed(dust);if(own){CharacterDraw.wetted(Soaked.on(view.index,data.character,dust));}}"),
                "the viewer's body, and no one else's");
    }

    private static void lowAndTheSwitchLeaveTheViewerDry() {
        String soaked = source("Soaked.java").replaceAll("\\s+", "");
        check(soaked.contains("if(!Part.WET_ON_THE_PLAYER.on()||Quality.plain(view)){returnDRY;}"), "nothing in Low or with the part off");
        check(soaked.contains("return!Part.WET_ON_THE_PLAYER.on()||Quality.plain(view)||takenIn[view]!=Pipeline.frame()?DRY:shown[view];"),
                "nor on the gun, nor from a frame before");
        check(Part.WET_ON_THE_PLAYER.live && !Part.WET_ON_THE_PLAYER.start, "the switch is taken while the game runs and patches nothing");
    }

    private static int count(String text, String of) {
        int found = 0;
        for (int at = text.indexOf(of); at >= 0; at = text.indexOf(of, at + 1)) {
            found++;
        }
        return found;
    }

    private static String source(String file) {
        try {
            return Files.readString(Path.of("src/main/java/com/cameraunlock/zomboid", file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
