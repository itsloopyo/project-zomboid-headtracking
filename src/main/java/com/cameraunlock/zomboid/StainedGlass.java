package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The game's stained glass windows. Its sprite paints the coloured glass as dots part covered,
 * as it paints a pane, so the picture took it for clear glass with the street behind: by day
 * every light of a church window came out a blown white sheet, with only its opaque red left.
 * Drawn as WorldScene.STAINED its dots are the glass itself, lit by the daylight behind them,
 * and its leading and stone or wooden frame are lit as the room is (see PipelineShaders).
 *
 * Where the mod's own surfaces are shown, each window is drawn from the mod's own picture of it
 * (tools/glass/stained.py): the same window, its glass and leading made again four times as fine,
 * held in one atlas with the cover the shader reads glass and frame by.
 */
final class StainedGlass {
    private static final String CHURCH = "location_community_church_small_01_", WINDOWS = "fixtures_windows_church_";

    static final String ATLAS = "/materials/stained.bc", LAYOUT = "/materials/stained.txt";
    /** Where in the atlas each window is, by sprite name: its texels' left, top, right and bottom as shares of the atlas. */
    private static final Map<String, float[]> PLACES = places();
    /** The atlas on the graphics card, 0 while it is not: set by the render thread with the rest of the mod's own pictures. */
    private static volatile int texture;

    private StainedGlass() {}

    static int texture() {
        return texture;
    }

    static void texture(int made) {
        texture = made;
    }

    /** Points the frame's texels at the window's place in the atlas. Thrown for a window the atlas has no picture of. */
    static void texels(String name, SpriteGeometry.Frame frame) {
        float[] place = PLACES.get(name);
        if (place == null) {
            throw new IllegalStateException("the stained glass atlas has no picture of " + name);
        }
        frame.uStart = place[0];
        frame.vStart = place[1];
        frame.uEnd = place[2];
        frame.vEnd = place[3];
    }

    static Map<String, float[]> places() {
        String text;
        try (InputStream in = StainedGlass.class.getResourceAsStream(LAYOUT)) {
            if (in == null) {
                throw new IllegalStateException("the jar has no " + LAYOUT);
            }
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(LAYOUT + " could not be read", e);
        }
        String[] lines = text.split("\n");
        String[] size = lines[0].trim().split(" ");
        float wide = Float.parseFloat(size[0]), high = Float.parseFloat(size[1]);
        Map<String, float[]> places = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String[] row = lines[i].trim().split(" ");
            float left = Float.parseFloat(row[1]), top = Float.parseFloat(row[2]);
            places.put(row[0], new float[] {left / wide, top / high, (left + Float.parseFloat(row[3])) / wide, (top + Float.parseFloat(row[4])) / high});
        }
        return places;
    }

    /**
     * Whether the sprite is a whole stained glass window, or the upper part of one. Places 112, 113, 120 and 121 of
     * the church sheet are its plain arched windows of clear glass, and the diamond-leaded lights of the window
     * sheet (8 to 13, 20 to 29) are clear glass too; a broken or emptied window is another place, past these.
     */
    static boolean of(String name) {
        if (name == null) {
            return false;
        }
        int church = Sheets.place(name, CHURCH);
        if (church >= 0) {
            return church >= 114 && church <= 119 || church >= 122 && church <= 127;
        }
        int window = Sheets.place(name, WINDOWS);
        return window >= 0 && window <= 7 || window >= 14 && window <= 19 || window == 30 || window == 31;
    }
}
