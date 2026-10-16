package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.iso.SpriteDetails.IsoObjectType;

/**
 * Chain link fencing, built as one: the wire fabric as a sheet the picture weaves its own
 * diamonds into at their real size, a round top rail along its top and a round post at each
 * corner the game's sprite has one, and at each end of a run. The game paints the wire a texel
 * wide, about ten diamonds to a tile, which stood up in front of the eye is a lattice of blobs
 * as big as a hand. A length that is torn, bent or burnt keeps its sprite's outline, its holes
 * and its posts, and the picture weaves the wire into what of it is wire: see BROKEN. With
 * the mod's buildings off the fence is the game's sprite.
 */
final class ChainLink {
    /** The wire fabric: the picture cuts the diamonds out of it, see PipelineShaders. */
    static final float FABRIC = 91f;
    /** The wire of a torn length, woven only where its sprite paints wire, the sprite's own thick strokes kept. */
    static final float BROKEN = 92f;
    /** As BROKEN, for a burnt length: the same steel, darkened by the fire by a set amount (PipelineShaders). */
    static final float BURNT = 93f;
    /** Galvanised steel, for the wire, the posts and the rail. */
    static final float RED = 0.56f, GREEN = 0.58f, BLUE = 0.59f;
    /** In tiles: a line post 48 mm across, a top rail 42 mm. */
    static final float POST_RADIUS = 0.024f, RAIL_RADIUS = 0.021f;
    static final int POST_SIDES = 10, RAIL_SIDES = 8;

    /** A length of fencing: how high, in levels, its rail and its posts stand, whether its sprite has a post, and which edges it is on. */
    record Run(float rail, float post, boolean hasPost, boolean north, boolean west) {}

    // Read off the sprites: the tall fence's posts stand 0.865 of a level (2.1 m) and its wire
    // reaches 0.83, the low one's 0.5 and 0.47. Each piece with a post has it at the north-west corner.
    private static final float TALL_RAIL = 0.83f, TALL_POST = 0.865f, LOW_RAIL = 0.47f, LOW_POST = 0.5f;
    private static final String SHEET = "fencing_01_";

    private ChainLink() {}

    /** The length a sprite is, or null for any sprite that is not a whole length of chain link. */
    static Run of(String name) {
        if (name == null) {
            return null;
        }
        return switch (Sheets.place(name, SHEET)) {
            case 24 -> new Run(LOW_RAIL, LOW_POST, true, true, false);
            case 25 -> new Run(LOW_RAIL, LOW_POST, false, true, false);
            case 26 -> new Run(LOW_RAIL, LOW_POST, false, false, true);
            case 27 -> new Run(LOW_RAIL, LOW_POST, true, false, true);
            case 28 -> new Run(LOW_RAIL, LOW_POST, true, true, true);
            case 56 -> new Run(TALL_RAIL, TALL_POST, true, true, false);
            case 57 -> new Run(TALL_RAIL, TALL_POST, false, true, false);
            case 58 -> new Run(TALL_RAIL, TALL_POST, false, false, true);
            case 59 -> new Run(TALL_RAIL, TALL_POST, true, false, true);
            case 60 -> new Run(TALL_RAIL, TALL_POST, true, true, true);
            default -> null;
        };
    }

    /** Whether a sprite is a lone post of chain link fencing, low or tall, whole or burnt: the mod stands it as a model of its own (Outdoors). */
    static boolean lonePost(String name) {
        return name != null && (name.equals("fencing_01_29") || name.equals("fencing_01_61") || name.equals("fencing_burnt_01_53")
                || name.equals("fencing_burnt_01_69"));
    }

    /** Whether a sprite is a gate in chain link fencing: one of the sheet of fence gates hung with wire. It stays its sprite, posts and all. */
    static boolean gate(String name, String secondMaterial) {
        return name != null && name.startsWith("fixtures_doors_fences_") && "MetalWire".equals(secondMaterial);
    }

    /**
     * Whether a sprite is a torn, bent or burnt length of chain link: wire on the sheets of
     * damaged and burnt fences. The welded mesh panels on those sheets are of metal bars, and
     * what lies on the ground is no wall.
     */
    static boolean broken(String name, String material, boolean onEdge) {
        return name != null && onEdge && "MetalWire".equals(material)
                && (name.startsWith("fencing_damaged_01_") || name.startsWith("fencing_damaged_02_") || name.startsWith("fencing_burnt_01_"));
    }

    /**
     * Whether a sprite is laid as one sheet of chain link on the edge or edges it stands on: a
     * whole length, or a torn or burnt one. The game marks a corner length, and some torn ones,
     * only as hopped over and not as a wall seen through, so the flags of a sheet miss them.
     */
    static boolean sheet(String name, PropertyContainer properties, IsoObjectType type) {
        return of(name) != null || broken(name, properties.get("Material"), Edges.north(properties, type) || Edges.west(properties, type));
    }

    /** The material a torn or burnt length's sprite is drawn with, BROKEN or BURNT, or 0 for a sprite that is none: see broken. */
    static float brokenMaterial(String name, String material, boolean onEdge) {
        return !broken(name, material, onEdge) ? 0f : name.startsWith("fencing_burnt_01_") ? BURNT : BROKEN;
    }
}
