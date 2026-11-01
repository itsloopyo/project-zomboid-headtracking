package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/**
 * Tests of the sun's colour past stained glass (SunGlass): when it runs, the size of its picture,
 * that the sun maps let through what the world's program shows as the atlas's glass and not its
 * frame, and that the shaders read the picture where the sun lights the world and its air.
 */
final class SunGlassTests {
    private SunGlassTests() {}

    static void run() {
        Checks.run("itRunsOnlyWithTheRowOnInAShadedModeWithTheAtlasMade", SunGlassTests::itRunsOnlyWithTheRowOnInAShadedModeWithTheAtlasMade);
        Checks.run("thePictureIsHalfTheSunMapEachWay", SunGlassTests::thePictureIsHalfTheSunMapEachWay);
        Checks.run("theCutLetsTheGlassThroughAndKeepsTheFrame", SunGlassTests::theCutLetsTheGlassThroughAndKeepsTheFrame);
        Checks.run("theGlassPassesWhatTheWorldShowsOfIt", SunGlassTests::theGlassPassesWhatTheWorldShowsOfIt);
        Checks.run("theWorldAndTheAirAreLitThroughTheGlass", SunGlassTests::theWorldAndTheAirAreLitThroughTheGlass);
    }

    private static void itRunsOnlyWithTheRowOnInAShadedModeWithTheAtlasMade() {
        check(SunGlass.runs(true, true, 7), "with the row on, in Medium or High, with the atlas made, it runs");
        check(!SunGlass.runs(false, true, 7), "not with the row off");
        check(!SunGlass.runs(true, false, 7), "not in Low");
        check(!SunGlass.runs(true, true, 0), "not before the atlas is on the graphics card");
    }

    private static void thePictureIsHalfTheSunMapEachWay() {
        check(SunGlass.size(4096) == 2048, "High's 4096 map has a picture of 2048, " + SunGlass.size(4096));
        check(SunGlass.size(2048) == 1024, "Medium's 2048 map has a picture of 1024, " + SunGlass.size(2048));
    }

    private static void theCutLetsTheGlassThroughAndKeepsTheFrame() {
        float glass = 128f / 255f, frame = 240f / 255f, through = 0.5f + SunGlass.CUT;
        check(glass < through, "tools/glass/stained.py's glass at 128 is let through, below " + through);
        check(frame >= through, "and its frame at 240 still stops the sun");
        check(through == 0.75f, "the cut ends where the world's program starts to read the atlas as frame, " + through);
        check(PipelineShaders.WORLD_FRAGMENT_MAIN.contains("smoothstep(0.75, 0.9, texel.a)"), "the world's program reads the atlas's glass below 0.75");
        check(PipelineShaders.SHADOW_FRAGMENT.contains("uniform float uGlassCut;") && PipelineShaders.SHADOW_FRAGMENT.contains("a < 0.5 + uGlassCut"),
                "the sun maps' program lets through what is covered less than a half and the cut");
    }

    private static void theGlassPassesWhatTheWorldShowsOfIt() {
        String world = PipelineShaders.WORLD_FRAGMENT_MAIN;
        check(world.contains("STAINED_OWN_GAIN = " + SunGlass.GAIN) && world.contains("STAINED_OWN_THICK = " + SunGlass.THICK),
                "the sun past the glass takes the gain and depth of colour the world shows the glass with");
        check(SunGlass.CAST_FRAGMENT.contains("texel.a >= 0.5 + CUT"), "the picture takes only what the sun maps let through");
        check(SunGlass.GLSL.contains("GLASS_PAST = " + SunGlass.PAST), "the shaders are told how far past the glass a place has to be");
    }

    private static void theWorldAndTheAirAreLitThroughTheGlass() {
        String world = PipelineShaders.WORLD_FRAGMENT_MAIN;
        check(world.contains("sunTint = sunThroughGlass(c);"), "the world reads the sun's colour where the near map has it in the sun");
        check(world.contains("uLightLinear * sunTint * (2.1 * sun)") && world.contains("uLightLinear * sunTint * (2.1 * reached"),
                "and colours the sun's light and its gloss with it");
        check(PipelineShaders.VOLUME.contains("uniform float uGlassLit;") && PipelineShaders.VOLUME.contains("tint = sunThroughGlass(c);"),
                "the air reads it while there is glass in the near map");
    }
}
