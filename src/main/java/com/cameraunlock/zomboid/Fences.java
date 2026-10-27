package com.cameraunlock.zomboid;

/** What the game's fences are made of, by their sheet and the properties the game gives them. */
final class Fences {
    private Fences() {}

    /**
     * Whether a sprite is a length of board fence, whole, broken or burnt: wood standing on an
     * edge, on a sheet of fences. Pickets are wood too: the game lets a car stop at a broken
     * one and a sheet be tied to a whole one, and a rail fence carries wire as its second
     * material. Its boards are made board by board, as a wall of boards is. The material is
     * the sprite's Material, or its MaterialType where it has none, as the burnt tall fence has.
     */
    static boolean boards(String name, String material, String materialType, String secondMaterial, boolean onEdge, boolean picket) {
        return name != null && onEdge && !picket && "Wood".equals(material != null ? material : materialType) && !"MetalWire".equals(secondMaterial)
                && (name.startsWith("fencing_01_") || name.startsWith("fencing_damaged_01_") || name.startsWith("fencing_damaged_03_")
                        || name.startsWith("fencing_burnt_01_"));
    }

    /**
     * Whether a sprite is a whole length of low board fence with a level top, which the game
     * marks as a sheet because it can be seen over and which is built with a board fence's
     * thickness and a top. A broken, burnt, notched or pointed one stays a sheet: a top laid at
     * its highest board would float over its gaps.
     */
    static boolean thick(String name) {
        return name != null && (numbered(name, "fencing_01_", 32, 35) || numbered(name, "carpentry_02_", 44, 49));
    }

    private static boolean numbered(String name, String sheet, int first, int last) {
        if (!name.startsWith(sheet)) {
            return false;
        }
        int index = Integer.parseInt(name.substring(sheet.length()));
        return index >= first && index <= last;
    }

    /** Whether a sprite is a fence of iron bars: the iron the game paints, which its many bars would otherwise have taken for wire. */
    static boolean ironBars(String name, String material) {
        return name != null && name.startsWith("fencing_") && "MetalBars".equals(material);
    }

    /** How far before its square's corner the security fence's sprites paint, in tiles along the edge: a length with a post and one without. */
    private static final float POSTED_OVERHANG = 0.55f, KERB_OVERHANG = 0.35f;

    /** How many layers off the edge a length of the security fence with a post lies: see overhangLayers. */
    static final float POSTED_LAYERS = 2f;

    /** How far before its square's corner the security fence's post stands, in tiles: 12 to 16 dots of 64 on fencing_01_48. */
    static final float POST_BEFORE = 0.22f;

    /** How high up the security fence's sprite paints the face of its footing, in levels: the middle of the 0.28 m block. */
    static final float KERB_DYE_HIGH = 0.06f;

    /**
     * How far before its square's north-west corner a length of the security fence (welded mesh
     * over a kerb, barbed wire on arms) paints itself, in tiles along its edge, or 0 for any other
     * sprite. Its post, the arm on the post and the end of its kerb stand there, over the end of
     * the length before: the sheet cut at the corner lost every post and left a gap in its place.
     */
    static float overhang(String name) {
        return switch (name == null ? -1 : Sheets.place(name, "fencing_01_")) {
            case 48, 51 -> POSTED_OVERHANG;
            case 49, 50 -> KERB_OVERHANG;
            default -> 0f;
        };
    }

    /**
     * Where along its edge, in tiles, a panel of the temporary mesh fence stands the concrete foot under its post, or NaN for
     * any other sprite: fencing_01_88 and 89 on a north edge, 90 and 91 on a west one. It is where the panel's sheet, laid on
     * the edge's line, shows the post: the sprite paints the foot off that line, where the sheet has nothing to show it on.
     */
    static float footAlong(String name) {
        return switch (name == null ? -1 : Sheets.place(name, "fencing_01_")) {
            case 88 -> -0.062f;
            case 89 -> 0.766f;
            case 90 -> 0.82f;
            case 91 -> 0.031f;
            default -> Float.NaN;
        };
    }

    /** Where on its tile a mesh panel's sprite paints the middle of the foot under its post, (a, b) for fencing_01_88 to 91: its colour is taken there. */
    private static final float[][] FOOT_PAINTED = {{0.055f, 0.117f}, {0.892f, 0.126f}, {0.13f, 0.95f}, {0.1f, 0.13f}};

    /** How high up the mesh panel's sprite paints the middle of its foot, in levels: the middle of the 0.11 m block. */
    static final float FOOT_DYE_HIGH = 0.055f / ViewCamera.LEVEL_HEIGHT;

    /** Where across (0) or down (1) its tile a mesh panel's sprite paints the middle of its foot: see FOOT_PAINTED. */
    static float footPainted(String name, int axis) {
        return FOOT_PAINTED[Sheets.place(name, "fencing_01_") - 88][axis];
    }

    /** How many layers off the edge a length of the security fence lies, so that one with a post is drawn over the end of the one before. */
    static float overhangLayers(String name) {
        return overhang(name) == POSTED_OVERHANG ? POSTED_LAYERS : 1f;
    }
}
