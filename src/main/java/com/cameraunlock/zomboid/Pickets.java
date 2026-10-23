package com.cameraunlock.zomboid;

/**
 * The white picket fence: fencing_01_4 on a square's west edge, 5 on its north edge and 6 on both, each painting a post
 * at the square's north-west corner and pickets on two rails along the edge. As a sheet stood on the edge its pickets
 * had no thickness and leaned as the game's view paints them. WallBuilder stands each edge as the model picket_length,
 * and a picket_post at the corner.
 */
final class Pickets {
    private Pickets() {}

    private static final String SHEET = "fencing_01_";
    /** The lone post of the same fence, which stands at the corner where a length has its own: see Outdoors.fencePost. */
    static final String POST = SHEET + "7";

    /**
     * Where the sprites paint a picket, which the fence's paint takes its colour from: how far along the edge from the
     * corner, and how high, in levels, between the rails.
     */
    static final float DYE_ALONG = 0.344f, DYE_HIGH = 0.5f / ViewCamera.LEVEL_HEIGHT;

    /** Whether a sprite is a whole length of the white picket fence. */
    static boolean is(String name) {
        return name != null && switch (Sheets.place(name, SHEET)) {
            case 4, 5, 6 -> true;
            default -> false;
        };
    }
}
