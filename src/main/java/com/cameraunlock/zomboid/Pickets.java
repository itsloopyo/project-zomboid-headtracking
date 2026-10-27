package com.cameraunlock.zomboid;

/**
 * The picket fences: the white one, fencing_01_4 on a square's west edge, 5 on its north edge and 6 on both, each painting
 * a post at the square's north-west corner and pickets on two rails along the edge; and the dark one with rounded tops,
 * 121 on the west edge, 120 on the north and 122 on both, nine pickets on three rails and no post. As a sheet stood on
 * the edge their pickets had no thickness and leaned as the game's view paints them. WallBuilder stands each edge as
 * the fence's model, and the white fence's picket_post at the corner.
 */
final class Pickets {
    private Pickets() {}

    private static final String SHEET = "fencing_01_";
    /** The lone post of the white fence, which stands at the corner where a length has its own: see Outdoors.fencePost. */
    static final String POST = SHEET + "7";

    /** How high, in levels, the sprites paint a picket between the rails, which the fence's paint takes its colour from. */
    static final float DYE_HIGH = 0.5f / ViewCamera.LEVEL_HEIGHT;

    /**
     * A picket fence: the model each edge stands as, how far along the edge from the corner its sprite paints a picket
     * at DYE_HIGH, and whether its sprites paint a post at the north-west corner.
     */
    record Fence(String model, float dyeAlong, boolean posted) {}

    static final Fence WHITE = new Fence("picket_length", 0.344f, true);
    static final Fence ROUNDED = new Fence("rounded_picket_length", 0.44f, false);

    /** The picket fence a sprite is a whole length of, or null. */
    static Fence of(String name) {
        return name == null ? null : switch (Sheets.place(name, SHEET)) {
            case 4, 5, 6 -> WHITE;
            case 120, 121, 122 -> ROUNDED;
            default -> null;
        };
    }
}
