package com.cameraunlock.zomboid;

/**
 * The game's stained glass windows. Its sprite paints the coloured glass as dots part covered,
 * as it paints a pane, so the picture took it for clear glass with the street behind: by day
 * every light of a church window came out a blown white sheet, with only its opaque red left.
 * Drawn as WorldScene.STAINED its dots are the glass itself, lit by the daylight behind them,
 * and its leading and stone or wooden frame are lit as the room is (see PipelineShaders).
 */
final class StainedGlass {
    private static final String CHURCH = "location_community_church_small_01_", WINDOWS = "fixtures_windows_church_";

    private StainedGlass() {}

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
