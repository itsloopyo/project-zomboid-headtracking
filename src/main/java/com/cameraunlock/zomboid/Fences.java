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

    /** Whether a sprite is a fence of iron bars: the iron the game paints, which its many bars would otherwise have taken for wire. */
    static boolean ironBars(String name, String material) {
        return name != null && name.startsWith("fencing_") && "MetalBars".equals(material);
    }
}
