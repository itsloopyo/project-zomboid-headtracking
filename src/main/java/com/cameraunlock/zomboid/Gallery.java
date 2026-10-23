package com.cameraunlock.zomboid;

/**
 * A gallery's sculpture: busts on their plinths, a draped woman and a youth on low bases, and a robed man with a staff
 * on a pedestal the game paints over two storeys. Asked by Outdoors.of, and stood the same way.
 *
 * The heights the colours are taken at were read off the game's sprites up the middle of the square: a bust's plinth
 * is grey under a pale stone bust, a statue's base white.
 */
final class Gallery {
    private Gallery() {}

    static final String SHEET = "location_entertainment_gallery_02_";
    private static final int STONE = 1 << Models.CERAMIC, PLINTH = 1 << Models.PAINT;
    /** Where a bust's sprite paints its stone and its plinth, in levels. */
    static final float BUST_DYE = 0.65f, BUST_PLINTH_DYE = 0.2f;
    /** Where a standing statue's sprite paints the figure and its base. */
    static final float STATUE_DYE = 0.55f, STATUE_BASE_DYE = 0.08f;
    /** Where the robed man's lower sprite paints the figure and its pedestal. */
    static final float ORATOR_DYE = 0.65f, ORATOR_PEDESTAL_DYE = 0.15f;
    /** The upper storey of the robed man, which his lower sprite's model stands whole. */
    static final int ORATOR_TOP = 55;

    static Outdoors.Piece of(String name, String called, String group, int faces, int width, int height) {
        int place = Sheets.place(name, SHEET);
        if (place < 0 || called == null || width != 1 || height != 1) {
            return null;
        }
        if (called.equals("Bust") && faces != Outdoors.NONE) {
            return new Outdoors.Piece("Female".equals(group) ? "bust_female" : "bust_male", faces).dye(STONE, BUST_DYE).also(PLINTH, BUST_PLINTH_DYE);
        }
        // The game gives the two standing statues no facing: the woman's sprite faces south and the youth's north.
        if (called.equals("Female Statue")) {
            return new Outdoors.Piece("statue_female", Outdoors.SOUTH).dye(STONE, STATUE_DYE).also(PLINTH, STATUE_BASE_DYE);
        }
        if (called.equals("Male Statue")) {
            return new Outdoors.Piece("statue_male", Outdoors.NORTH).dye(STONE, STATUE_DYE).also(PLINTH, STATUE_BASE_DYE);
        }
        if (called.equals("Statue") && "Gallery".equals(group) && faces != Outdoors.NONE) {
            return place == ORATOR_TOP ? Outdoors.following(new Outdoors.Piece(null, faces))
                    : new Outdoors.Piece("statue_orator", faces).dye(STONE, ORATOR_DYE).also(PLINTH, ORATOR_PEDESTAL_DYE);
        }
        return null;
    }
}
