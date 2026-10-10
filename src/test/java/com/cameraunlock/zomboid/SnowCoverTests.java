package com.cameraunlock.zomboid;

/**
 * Tests of how grass stands in lying snow. They read the shaders' text, since nothing here
 * runs a shader: they hold the vertex shader to the things the fragment shader draws snow by.
 */
final class SnowCoverTests {
    private SnowCoverTests() {}

    static void run() {
        Checks.run("withNoSnowLyingNoGrassIsMoved", SnowCoverTests::withNoSnowLyingNoGrassIsMoved);
        Checks.run("grassIsLetDownOnlyUnderTheSkyAndLessWhereTheSkyIsHidden", SnowCoverTests::grassIsLetDownOnlyUnderTheSkyAndLessWhereTheSkyIsHidden);
        Checks.run("theSnowGrassStandsInEndsWhereTheSnowDrawnEnds", SnowCoverTests::theSnowGrassStandsInEndsWhereTheSnowDrawnEnds);
        Checks.run("aStripAsksOnceAtItsRootAndNotWhereGrassIsLeftOut", SnowCoverTests::aStripAsksOnceAtItsRootAndNotWhereGrassIsLeftOut);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** A function's text, from its name to the brace that closes it, with the space taken out of it. */
    static String body(String text, String start) {
        int at = text.indexOf(start);
        if (at < 0) {
            throw new AssertionError("the shader has no " + start);
        }
        int from = text.indexOf('{', at), depth = 0, i = from;
        do {
            depth += text.charAt(i) == '{' ? 1 : text.charAt(i) == '}' ? -1 : 0;
            i++;
        } while (depth > 0);
        return text.substring(from, i).replaceAll("\\s+", "");
    }

    private static int count(String text, String part) {
        int found = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            found++;
        }
        return found;
    }

    private static void withNoSnowLyingNoGrassIsMoved() {
        String stood = body(SnowShader.STOOD, "float snowStood(vec3 root, bool open)");
        check(stood.startsWith("{if(uSnowLying<=0.0||!open){return0.0;}"), "with no snow lying a plant stands no deep in it, before anything is worked out");
        check(body(SnowShader.STOOD, "vec3 snowedIn(vec3 at, float deep)").equals("{at.z-=deep;returnat;}"), "a plant no deep in snow is let down by nothing");
        String laden = body(SnowShader.STOOD, "vec3 snowLaden(vec3 at, float high, float deep, float seed)");
        check(laden.contains("floatbent=0.45*high*deep/SNOW_DEPTH;") && laden.endsWith("returnvec3(at.xy+vec2(cos(turn),sin(turn))*bent,at.z-bent);}"),
                "a blade no deep in snow is bent by nothing");
        check(!body(PipelineShaders.WORLD_VERTEX, "vec3 swayed(vec3 at, float high, float seed)").contains("Snow"), "the wind on a blade of grass asks nothing of the snow");
    }

    private static void grassIsLetDownOnlyUnderTheSkyAndLessWhereTheSkyIsHidden() {
        String stood = body(SnowShader.STOOD, "float snowStood(vec3 root, bool open)");
        check(stood.contains("floatskyOver=skyOverAt(root+vec3(0.0,0.0,0.1),max(away-uRangeFrom,0.0));"),
                "how much of the sky a plant's root sees is asked as the ground's pixel asks it, within the distance it counts there");
        check(stood.contains("SNOW_SHELTERED*(1.0-skyOver)/0.62"), "less snow lies at a root under a crown or the eaves, by the share less of it lies on the ground there");
        String main = PipelineShaders.WORLD_FRAGMENT_MAIN.replaceAll("\\s+", "");
        check(main.contains("floatrange=max(away-uRangeFrom,0.0);") && main.contains("floatskyOver=open>0.5&&!crown?skyOverAt(vWorld+n*0.1,range):1.0;"),
                "the ground's pixel asks the sky as snowStood has it");
        check(main.contains("if(uSnowLying>0.0&&open>0.5&&!water&&!model&&(uPlantSnow>0.0||!(strip||grass||crown||core||card))){") && main.contains("snowShelter=(1.0-skyOver)/0.62;"),
                "snow is drawn only under the sky, thinned by what hides it, as snowStood has it");
        check(body(SnowShader.COVER, "vec3 snowThick(vec2 q, float foot)").contains("-SNOW_SHELTERED*snowShelter"), "the snow drawn is thinned by the same share");
        check(PipelineShaders.WORLD_VERTEX.replaceAll("\\s+", "").contains(",mod(gl_Color.a,32.0)>24.5);"), "a strip's vertex says whether its square is under the sky, and snowStood is told");
    }

    private static void theSnowGrassStandsInEndsWhereTheSnowDrawnEnds() {
        String stood = body(SnowShader.STOOD, "float snowStood(vec3 root, bool open)");
        check(body(SnowShader.COVER, "float snowCover(vec2 q, float foot)").equals("{returnsnowCovers(snowThick(q,foot).x+snowRagged(q,foot).x);}"),
                "the snow drawn ends where its thickness with its ragged edge says");
        check(stood.contains("floatthick=snowThick(q,foot).x+snowRagged(q,foot).x;") && stood.contains("snowCovers(thick-"),
                "the snow a plant stands in ends by the same thickness and the same ragged edge");
        check(stood.contains("floatfoot=uPixel*away*away/max(abs(toEye.z),0.1);"),
                "what is too fine to draw at the root's distance counts as its mean there too: by the ground a pixel of the view covers");
        check(stood.contains("vec2q=mod(root.xy,60.0);"), "the snow is asked for at the plant's root, the one place for every vertex of the plant");
        check(count(SnowShader.STOOD, "snowThick(") == 1, "nothing else there works out where snow lies");
    }

    private static void aStripAsksOnceAtItsRootAndNotWhereGrassIsLeftOut() {
        String world = PipelineShaders.WORLD_VERTEX.replaceAll("\\s+", "");
        check(world.contains("deep=uLaid>2.5||uPlantSnow<=0.0?0.0:snowStood(vec3(gl_Vertex.xy+gl_MultiTexCoord1.xy,gl_Vertex.z-(gl_Normal.z-level)),"),
                "a strip's depth in the snow is asked at its root, and not in the draw its vertices are left out of");
        check(world.contains("if(uModel<0.5&&vMaterial>63.5&&vMaterial<64.5&&gl_MultiTexCoord0.y<0.5){floatbanked=snowStood(at.xyz,vKind>0.5);"),
                "a kerb asks how deep the snow banked against it is at the foot of its face only");
        check(count(world, "snowStood(") == 3, "and nowhere else in the vertex program");
        check(!PipelineShaders.SHADOW_VERTEX.contains("snowStood"), "grass is in no shadow map, and the shadow program asks nothing of where snow lies");
    }
}
