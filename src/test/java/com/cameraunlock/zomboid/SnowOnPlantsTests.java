package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tests of lying snow on bark and on plants drawn as cards. They read the shaders' text, since nothing here runs a shader. */
final class SnowOnPlantsTests {
    private SnowOnPlantsTests() {}

    static void run() {
        Checks.run("barkAndIvyHoldSnowOnlyWhereItWouldLie", SnowOnPlantsTests::barkAndIvyHoldSnowOnlyWhereItWouldLie);
        Checks.run("woodCarriesARidgeOfSnowAlongItsTop", SnowOnPlantsTests::woodCarriesARidgeOfSnowAlongItsTop);
        Checks.run("lowDrawsWhatGrowsBare", SnowOnPlantsTests::lowDrawsWhatGrowsBare);
        Checks.run("aCardPlantIsBuriedInBothDrawingsFromSlopesAskedBeforeAnythingIsLeftOut", SnowOnPlantsTests::aCardPlantIsBuriedInBothDrawingsFromSlopesAskedBeforeAnythingIsLeftOut);
        Checks.run("snowOnACardIsOnLeavesAndNeverSolid", SnowOnPlantsTests::snowOnACardIsOnLeavesAndNeverSolid);
        Checks.run("aStepInTheGamesSnowIsEasedAndNoSnowIsNone", SnowOnPlantsTests::aStepInTheGamesSnowIsEasedAndNoSnowIsNone);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static int count(String text, String of) {
        int found = 0;
        for (int at = text.indexOf(of); at >= 0; at = text.indexOf(of, at + 1)) {
            found++;
        }
        return found;
    }

    /**
     * Bark and ivy hold snow as snowOnBark lays it, by the way they face and where a blizzard
     * drove it, and not the ground's lying snow, which would plaster a bole white up its whole
     * side; and none in Low. The old look (snowOnWood, snowOnIvy) is not back.
     */
    private static void barkAndIvyHoldSnowOnlyWhereItWouldLie() {
        String main = PipelineShaders.WORLD_FRAGMENT_MAIN.replaceAll("\\s+", "");
        check(main.contains("floatup=bark?0.0:smoothstep(0.35,0.8,level.z);"), "a bole, a limb and ivy take none of the ground's snow");
        check(main.contains("snowed=uPlantSnow>0.0?snowOnBark(level,"), "bark and ivy take snowOnBark's, and none in Low");
        String bark = SnowShader.GLSL.replaceAll("\\s+", "");
        check(bark.contains("floattop=smoothstep(0.45,0.9,facing.z);"), "only wood that faces nearly up holds lying snow");
        check(bark.contains("(1.0-smoothstep(0.6,0.9,abs(facing.z)))"), "a blizzard sticks snow on upright windward faces, not on tops or undersides");
        check(!SnowShader.GLSL.contains("snowOnWood") && !SnowShader.GLSL.contains("snowOnIvy") && !main.contains("snowOnWood") && !main.contains("snowOnIvy"),
                "the old snow on bark and ivy is not back");
    }

    /**
     * Wood carries a ridge of snow along its top, raised in the world's vertex shader as high as
     * a share of the wood's thickness there, as far down from its top as the lying snow reaches,
     * and as much as the fragment shader whitens it where the sky is hidden; the fragment shader
     * draws that same ridge white. The shadows cast it. Wood that carries no ridge stays where it is.
     */
    private static void woodCarriesARidgeOfSnowAlongItsTop() {
        check(SnowShader.RIDGE_HIGH > 0f && SnowShader.RIDGE_OF_THICK > 1f && SnowShader.RIDGE_WIDE > 0f && SnowShader.RIDGE_WIDE < 1f,
                "a ridge stands higher than the half-round of the wood's top and spreads a little to each side, up to a limit");
        check(SnowShader.FINE > 0f && SnowShader.FINE < SnowShader.RIDGE_HIGH / SnowShader.RIDGE_OF_THICK, "only wood whose ridge is lower than the limit is frosted all round");
        String ridge = SnowShader.RIDGE.replaceAll("\\s+", "");
        check(ridge.contains("floatsnowRidgeTop(vec3facing){returnsmoothstep(0.15,0.75,facing.z);}"), "upright and downward wood has no top");
        check(ridge.contains("floatsnowRidgeLain(floattop){returnsmoothstep(0.0,0.2,top-1.0+min(uSnowLying*2.5,1.0));}"),
                "a light fall lies on the crest alone, a deep one as far as the ridge reaches");
        check(ridge.contains("vec3snowRidge(vec3facing,floatthick,floatopen){floattop=snowRidgeTop(facing);"
                + "floathigh=uSnowLying*open*min(SNOW_RIDGE_OF_THICK*max(thick,0.0),SNOW_RIDGE_HIGH)*top*snowRidgeLain(top);"
                + "returnvec3(facing.xy*(SNOW_RIDGE_WIDE*high),high);}"),
                "the ridge stands up and out, by the wood's thickness, where it lies and as much as lies");
        String vertex = PipelineShaders.WORLD_VERTEX.replaceAll("\\s+", "");
        check(vertex.contains("if(uModel<0.5&&uPlantSnow>0.0&&gl_MultiTexCoord1.z<-0.5){vec3facing=normalize(gl_Normal);"
                + "floatopen=vKind>0.5?clamp(1.0-0.8*(1.0-skyOverAt(at.xyz+facing*0.1,max(length(at.xyz-uEye)-uRangeFrom,0.0)))/0.62,0.0,1.0):0.0;"
                + "at.xyz+=snowRidge(facing,gl_MultiTexCoord1.x,open);vBlade.xy=vec2(snowRidgeTop(facing),abs(gl_MultiTexCoord1.x));}"),
                "only wood is raised, in nothing but Low, as the fragment shader is told, less under cover as it whitens less there, and none indoors");
        int raised = vertex.indexOf("at.xyz+=snowRidge(");
        check(raised > vertex.indexOf("vec4at=strip?") && raised < vertex.indexOf("vWorld=at.xyz+lies;"), "the ridge is in the place drawn, after the wind");
        check(vertex.contains("}elseif(kind>-0.5){") && SpriteGeometry.WOOD < -0.5f && SpriteGeometry.WOOD > -1.5f, "wood carries its thickness where a card is turned, and is not moved by it");
        String main = PipelineShaders.WORLD_FRAGMENT_MAIN.replaceAll("\\s+", "");
        check(main.contains("snowed=uPlantSnow>0.0?snowOnBark(level,vPlace.xy+vec2(0.61,-0.43)*vPlace.z,vBlade.x,vBlade.y):0.0;"),
                "the fragment shader is told the wood's top and thickness");
        String bark = SnowShader.GLSL.replaceAll("\\s+", "");
        check(bark.contains("floatladen=ridgeTop>0.0?snowRidgeLain(ridgeTop):0.0;"), "the ridge is white where it lies");
        check(bark.contains("floatfrost=thick>0.0&&thick<SNOW_FINE?0.85*smoothstep(0.05,0.45,uSnowLying*(0.6+0.6*ridgeTop)):0.0;"), "a twig is frosted");
        check(bark.contains("returnmin(max(max(lain,laden),max(frost,stuck))*clamp(1.0-0.8*snowShelter,0.0,1.0),0.9);"),
                "the ridge, the frost, what lies on the top and what a blizzard drove are all drawn, less under cover");
        String shadow = PipelineShaders.SHADOW_VERTEX.replaceAll("\\s+", "");
        check(shadow.contains("if(gl_MultiTexCoord1.z<-0.5&&uPlantSnow>0.0&&uFromAbove<0.5){stands.xyz+=snowRidge(normalize(gl_Normal),gl_MultiTexCoord1.x,1.0);}"),
                "the ridge casts its shadow, and keeps no sky off what is under it");
    }

    private static void lowDrawsWhatGrowsBare() {
        String main = PipelineShaders.WORLD_FRAGMENT_MAIN.replaceAll("\\s+", "");
        check(main.contains("if(uSnowLying>0.0&&open>0.5&&!water&&!model&&!body&&!blood&&(uPlantSnow>0.0||!(strip||grass||crown||core||card))){"),
                "with no snow on what grows, grass, crowns and cards take none of the snow drawn on the ground");
        check(main.contains("if(uPlantSnow>0.0&&vKind>2.5&&"), "nor is a card plant buried in it");
        String vertex = PipelineShaders.WORLD_VERTEX.replaceAll("\\s+", "");
        check(vertex.contains("deep=uLaid>2.5||uPlantSnow<=0.0?0.0:snowStood("), "nor does grass stand in it or bend under it");
        check(vertex.contains("take*lean*(1.0-0.35*uPlantSnow)") && !vertex.contains("take*lean*(1.0-0.35*uSnowLying)"), "nor does a bough swing the less for it");
        SkyState state = new SkyState();
        state.snowLying = 0.7f;
        try {
            Quality.set(Quality.LOW);
            check(state.snowOnPlants() == 0f, "in Low, the plain mode, no snow lies on what grows with snow lying on the ground");
            Quality.set(Quality.MEDIUM);
            check(state.snowOnPlants() == 0.7f, "in Medium as much as on the ground");
            Quality.set(Quality.HIGH);
            check(state.snowOnPlants() == 0.7f, "and in High");
        } finally {
            Quality.set(Quality.HIGH);
        }
        // Pipeline hands the world's program and the shadows' their uniforms and runs only with a graphics card: what it hands them is read.
        String pipeline;
        try {
            pipeline = Files.readString(Path.of("src/main/java/com/cameraunlock/zomboid/Pipeline.java"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        check(count(pipeline, "\"uPlantSnow\", state.snowOnPlants())") == 2 && count(pipeline, "\"uPlantSnow\"") == 2,
                "and both programs are told that, and nothing else, as the snow on what grows");
    }

    private static void aCardPlantIsBuriedInBothDrawingsFromSlopesAskedBeforeAnythingIsLeftOut() {
        String rest = PipelineShaders.WORLD_FRAGMENT_MAIN_REST;
        int buried = rest.indexOf("if (rise < snowAround("), stops = rest.indexOf("if (depthOnly)");
        check(buried >= 0 && stops >= 0 && buried < stops, "what of a card is under the snow is left out before the drawing of depth stops, so both drawings leave it out");
        String start = PipelineShaders.WORLD_FRAGMENT_MAIN_START.replaceAll("\\s+", "");
        int asked = start.indexOf("if(uPlantSnow>0.0){riseScreen=vec2(dFdx(vWorld.z),dFdy(vWorld.z));coverScreen=vec2(dFdx(vCover),dFdy(vCover));");
        int left = start.indexOf("discard;");
        check(asked >= 0 && asked < left, "the slopes that decide what of a card is buried are asked before any pixel is left out, and only with snow on what grows");
        check(count(start, "dFdx(vWorld.z)") == 1 && count(start, "dFdx(vCover)") == 1 && count(start, "dFdx(vUv.y)") == 1, "and not again outside that test, so Low asks none of them");
        check(rest.replaceAll("\\s+", "").contains("if(uPlantSnow>0.0&&vKind>2.5&&vKind<3.5&&vCover<0.9995&&(vMaterial<55.5||vMaterial>62.5&&vMaterial<63.5)){"),
                "only the game's own sprites and the bushes in their place are buried, under the sky, with snow lying, and none of Flora's fronds, leaves and flowers, whose sway is not their height");
    }

    private static void aStepInTheGamesSnowIsEasedAndNoSnowIsNone() {
        float shown = 0.95f, most = 0f;
        int frames = 0;
        while (shown != 0.65f && frames < 60 * 60) {
            float next = SkyState.settled(shown, 0.65f, 1f / 60f);
            most = Math.max(most, shown - next);
            shown = next;
            frames++;
        }
        check(shown == 0.65f, "the snow drawn comes to the game's in the end: " + shown);
        check(most < 0.01f, "the game's snow going from 0.95 to 0.65 on the hour moves the snow drawn by under a hundredth a frame: " + most);
        check(frames > 60 * SkyState.SNOW_SETTLES && frames < 60 * 6 * SkyState.SNOW_SETTLES, "and it gets there in a few seconds: " + frames + " frames");
        float melting = 0.3f;
        for (int frame = 0; frame < 60 * 30; frame++) {
            melting = SkyState.settled(melting, 0f, 1f / 60f);
        }
        check(melting == 0f, "snow that has gone is drawn as none at all, not as a little forever: " + melting);
        check(SkyState.shown(-1f, 0.7f, 0.016f, false) == 0.7f, "the first picture draws the game's snow as it is");
        check(SkyState.shown(0.2f, 0.7f, SkyState.SNOW_SNAPS + 0.5f, false) == 0.7f, "after a load or a long gap the game's snow is taken as it is");
        check(SkyState.shown(0.2f, 0.7f, 0.5f, true) == 0.2f, "while the game is paused the snow drawn does not move");
        float next = SkyState.shown(0.2f, 0.7f, 0.016f, false);
        check(next > 0.2f && next < 0.21f, "and otherwise it eases towards the game's: " + next);
    }

    private static void snowOnACardIsOnLeavesAndNeverSolid() {
        String card = SnowCoverTests.body(SnowShader.GLSL, "float snowOnCard(");
        check(card.contains("lain*leafy*lit*"), "snow on a card is on its lit leaves alone: none on bark, so no white outline round the wood");
        check(card.contains("*open,0.95)"), "a card under a tree carries less snow, and never a solid white");
        check(PipelineShaders.WORLD_FRAGMENT_MAIN_REST.replaceAll("\\s+", "").contains("floatleafy=twig?0.0:"), "nor on a twig of Flora's");
    }
}
