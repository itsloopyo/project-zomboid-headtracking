package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Low draws first person from the game's own sprites, with nothing the mod adds of its own
 * whatever the rows of [Content] say: see Own.on, and OwnTests for the rows and the road's
 * lines. What is decided where the world is built and drawn needs the game's map or a graphics
 * card to run, so it is read where it is decided: each thing below goes by a row of [Content]
 * or by Quality.plain, and so is the game's own in Low.
 */
final class LowTests {
    private static final Path SOURCE = Path.of("src/main/java/com/cameraunlock/zomboid");

    private LowTests() {}

    static void run() {
        bannersHangAsClothOnlyWithTheModsFurniture();
        aWoodenFloorIsWoodWhereNoPictureOfTheModsIsLaid();
        lowsWaterIsAsRoughInAnyWindAndItsRipplesFadeWithDistance();
        aRoomIsShownAsTheGameShowsItAndNothingIsBuiltAheadInLow();
        anOpenFlightIsItsSpritesStepsWithoutTheModsBuildings();
    }

    private static void aRoomIsShownAsTheGameShowsItAndNothingIsBuiltAheadInLow() {
        String scene = scene();
        check(count(scene, "boolean showing = Online.contested() || withoutSightline || Quality.plain();") == 1,
                "in Low what stands in a room is built only once the game has shown the room");
        check(count(scene, "if (ahead && (aheadStep < 0 || withoutSightline || Quality.plain() ||") == 1,
                "and no chunk the view does not face is built ahead of a turn");
    }

    private static void anOpenFlightIsItsSpritesStepsWithoutTheModsBuildings() {
        String scene = scene();
        check(count(scene, "float riser = closed || !Own.buildings() ? Stairs.RISE : Flights.TREAD;") == 1,
                "an open flight's risers are whole and show the sprite without the mod's buildings");
        int gate = scene.indexOf("if (!closed && !Own.buildings()) { return true; }"), strings = scene.indexOf("flights.open(");
        check(gate >= 0 && strings > gate && count(scene, "flights.open(") == 1, "and it has no strings or underside built under it");
    }

    private static void bannersHangAsClothOnlyWithTheModsFurniture() {
        String scene = scene();
        String strung = "if ((kind.banner == Banners.STRUNG || kind.banner == Banners.POSTED) && Own.furniture()) { addBanner(mesh, kind.banner == Banners.STRUNG,";
        String onWall = "boolean cloth = kind.banner == Banners.ON_WALL && Own.furniture();";
        check(count(scene, strung) == 1, "a banner across a street or on a lamp post is hung as a sheet only with the mod's furniture on");
        check(count(scene, onWall) == 1, "bunting and a flag are laid on the edge their picture shows only with the mod's furniture on");
        check(count(scene, "kind.banner") == count(strung, "kind.banner") + count(onWall, "kind.banner"),
                "and those two are the only places a square's build asks its kind which cloth it is");
    }

    private static void aWoodenFloorIsWoodWhereNoPictureOfTheModsIsLaid() {
        String scene = scene();
        Matcher asked = Pattern.compile("== WorldScene\\.WOOD( && Own\\.textures\\(\\))?").matcher(scene);
        int asks = 0, gated = 0;
        while (asked.find()) {
            asks++;
            if (asked.group(1) != null) {
                gated++;
            }
        }
        check(asks > 0 && gated == asks,
                "a wooden floor is made another material, so the picture's wood is not laid on it, only where the picture lays its own textures: "
                        + gated + " of " + asks + " places ask");
    }

    private static void lowsWaterIsAsRoughInAnyWindAndItsRipplesFadeWithDistance() {
        String pipeline = source("Pipeline.java");
        check(count(pipeline, "boolean plainWater = Quality.plain();") == 1
                && count(pipeline, "to.set(\"uRuffle\", plainWater ? 1f : WaterMirror.ruffle(") == 1
                && count(pipeline, "to.set(\"uPlainWater\", plainWater ? 1f : 0f);") == 1,
                "the world's program is told the water is plain, and as rough as in a light breeze, in Low");
        String shaders = source("PipelineShaders.java");
        check(count(shaders, "bool sized = water && uPlainWater < 0.5;") == 1
                && count(shaders, "vec3 kept = sized ? ") == 1 && count(shaders, "float near = sized ? 1.0 : 1.0 - smoothstep(14.0, 55.0, range);") == 1,
                "and plain water keeps every size of ripple and lets them go from 14 to 55 tiles off, as a wet road does");
    }

    /** A source file with its comments out and every run of white space one space. */
    private static String source(String file) {
        try {
            return Files.readString(SOURCE.resolve(file)).replaceAll("//[^\n]*", "").replaceAll("\\s+", " ");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** WorldScene and the builders its frame hands each kind of thing in a chunk to, read as one. */
    static String scene() {
        try (Stream<Path> files = Files.list(SOURCE)) {
            StringBuilder scene = new StringBuilder(source("WorldScene.java"));
            files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith("Builder.java")).sorted()
                    .forEach(name -> scene.append(' ').append(source(name)));
            return scene.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int count(String in, String what) {
        int found = 0;
        for (int at = in.indexOf(what); at >= 0; at = in.indexOf(what, at + what.length())) {
            found++;
        }
        return found;
    }
}
