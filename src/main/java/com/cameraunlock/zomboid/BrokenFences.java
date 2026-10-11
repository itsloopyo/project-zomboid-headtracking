package com.cameraunlock.zomboid;

/**
 * A broken length of fence: wire, pickets, boards, iron railings or a concrete panel with a
 * piece out of it. The game marks it for no wall, so it stood in the middle of its square as a
 * picture turned to the eye. It stands on the edge of its square its picture is painted along:
 * the half of its frame west of the square's north corner is the west edge, the half east of it
 * the north edge, and a corner piece paints both.
 */
final class BrokenFences {
    private BrokenFences() {}

    private static final String SHEET = "fencing_damaged_01_";
    /** How far past the square's north corner a picture may reach and still be on one edge alone, in tiles: the post at the corner. */
    private static final float CORNER_POST = 0.15f;

    static boolean is(String name) {
        return name != null && name.startsWith(SHEET);
    }

    static boolean onWest(SpriteGeometry.Frame frame) {
        return frame.left < frame.originU - reach(frame);
    }

    static boolean onNorth(SpriteGeometry.Frame frame) {
        return frame.left + frame.width > frame.originU + reach(frame);
    }

    private static float reach(SpriteGeometry.Frame frame) {
        return CORNER_POST * SpriteGeometry.PIXELS_ACROSS_PER_TILE * frame.scale;
    }
}
