package com.cameraunlock.zomboid;

/**
 * The low fences WallBuilder stands as models. The white picket fence: fencing_01_4 on a square's west edge, 5 on its
 * north edge and 6 on both, each painting a post at the square's north-west corner and pickets on two rails along the
 * edge. The dark one with rounded tops: 121 on the west edge, 120 on the north and 122 on both, nine pickets on three
 * rails and no post. The barbed wire fence: three strands along the edge, 16 on the west edge, 17 on the north and 18 on
 * both with a sawn post near the north-west corner, and 20 on the west and 21 on the north with none. As a sheet stood on
 * the edge their pickets and posts had no thickness and leaned as the game's view paints them.
 */
final class ModelFences {
    private ModelFences() {}

    private static final String SHEET = "fencing_01_";

    /** How high, in levels, the fences' paint is read: between a picket fence's rails, and halfway up the barbed wire's post. */
    static final float DYE_HIGH = 0.5f / ViewCamera.LEVEL_HEIGHT;

    /**
     * A fence: the model each edge stands as, its centre in from the edge's line and its foot over the ground; the
     * material the sprite's paint dyes, and how far along the edge from the corner and in from its line the paint is read
     * at DYE_HIGH; and the post its sprite paints, or null, with how far along and in from the north-west corner it
     * stands, and the lone post sprite that stands in its place where a square has one.
     */
    record Fence(String model, float in, float lift, int dyes, float dyeAlong, float dyeIn, String post, float postAlong, float postIn,
            String lonePost) {
        boolean posted() {
            return post != null;
        }
    }

    // A picket fence stands on the edge's line and its post on the corner.
    static final Fence WHITE = new Fence("picket_length", 0f, 0f, 1 << Models.PAINT, 0.344f, 0f, "picket_post", 0f, 0f, SHEET + "7");
    static final Fence ROUNDED = new Fence("rounded_picket_length", 0f, 0f, 1 << Models.PAINT, 0.44f, 0f, null, 0f, 0f, null);

    // Read off fencing_01_16, 17 and 20: the post's middle stands where fencing_01_19, the lone post, paints it, and the
    // strands run the post's face toward the edge, from 0.18 m up. The model stands on its lowest barb.
    private static final float WIRE_IN = 0.055f, WIRE_FOOT = 0.18f - 0.0165f, POST_ALONG = 0.133f, POST_IN = 0.102f;
    static final Fence BARBED_POSTED = new Fence("barbed_wire_length", WIRE_IN, WIRE_FOOT, 1 << Models.WOOD, POST_ALONG, POST_IN, "rail_post",
            POST_ALONG, POST_IN, SHEET + "19");
    static final Fence BARBED = new Fence("barbed_wire_length", WIRE_IN, WIRE_FOOT, 0, 0f, 0f, null, 0f, 0f, null);

    /** The fence a sprite is a whole length of, or null. */
    static Fence of(String name) {
        return name == null ? null : switch (Sheets.place(name, SHEET)) {
            case 4, 5, 6 -> WHITE;
            case 120, 121, 122 -> ROUNDED;
            case 16, 17, 18 -> BARBED_POSTED;
            case 20, 21 -> BARBED;
            default -> null;
        };
    }
}
