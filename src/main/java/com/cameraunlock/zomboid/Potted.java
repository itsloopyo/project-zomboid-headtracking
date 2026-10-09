package com.cameraunlock.zomboid;

/**
 * A plant in a pot that stands on the floor. The game has five kinds on one sheet, each four times over: grown, gone
 * dry, dead, and the pot left with only its earth. The dry and the dead plant are one model, its leaves let down,
 * which the sprite colours yellow or brown. Which model a sprite is goes by its place on the sheet, and the name the
 * game gives it has to agree.
 */
final class Potted {
    /** How high on its sprite, in levels, over the middle of the square, the pot gives its colour. */
    static final float POT_DYE = 0.06f;
    private static final String SHEET = "vegetation_indoor_01_";

    final String model;
    /** How high on the sprite the leaves give their colour, in levels, or 0 for a pot with nothing growing in it. */
    final float leaves;

    private Potted(String model, float leaves) {
        this.model = model;
        this.leaves = leaves;
    }

    /** The plant a sprite is, or null for a sprite that is none of them. */
    static Potted of(String name, String called) {
        int place = Sheets.place(name, SHEET);
        // The sheet's first row holds the grown plants, and each row under it the five again, a step on.
        String kind;
        String named;
        float leaves;
        switch (place) {
            case 7, 21, 29, 37 -> {
                kind = "evergreen";
                named = "Chinese Evergreen";
                leaves = 0.38f;
            }
            case 9, 17, 25, 33 -> {
                kind = "dragon_tree";
                named = "Dragon Tree";
                leaves = 0.36f;
            }
            case 10, 18, 26, 34 -> {
                kind = "snake_plant";
                named = "Snake Plant";
                leaves = 0.24f;
            }
            case 11, 19, 27, 35 -> {
                kind = "cast_iron";
                named = "Cast Iron Plant";
                leaves = 0.26f;
            }
            case 12, 20, 28, 36 -> {
                kind = "ficus";
                named = "Ficus";
                leaves = 0.28f;
            }
            default -> {
                return null;
            }
        }
        if (!named.equals(called)) {
            return null;
        }
        return place >= 32 ? new Potted("pot_" + kind, 0f) : new Potted(place >= 16 ? kind + "_dry" : kind, leaves);
    }
}
