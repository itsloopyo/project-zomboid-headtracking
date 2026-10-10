package com.cameraunlock.zomboid;

/**
 * How far from the eye first person draws people, zombies, vehicles and the furniture and decor
 * the mod has models of: the row ZombiesAndPropsDrawDistance as a frame takes it. The land, the
 * buildings and what is built into a chunk with them go by ViewDistance alone, which is also as
 * far as this reaches: a row at the view distance or over it leaves nothing out that the view
 * distance does not.
 *
 * Ahead of a vehicle the viewer rides in, on the side it faces, nothing is left out but what the
 * view distance leaves out: what the car is driven at is seen as far as the land is.
 *
 * Where players can fight (Online.contested) nothing is left out: a car or a wardrobe not drawn
 * would be cover another player is seen through.
 */
final class DrawDistance {
    /** A reach that leaves nothing out. */
    static final float WHOLE = Float.POSITIVE_INFINITY;

    private float eyeX, eyeY, aheadX, aheadY, aside = WHOLE;

    /** How far a row of so many tiles reaches with the view so many tiles deep, in a game where players can fight or cannot. */
    static float reach(int asked, int viewDistance, boolean contested) {
        return contested || asked >= viewDistance ? WHOLE : asked;
    }

    /**
     * Takes what the graphics mode draws now, for an eye at (eyeX, eyeY) in a vehicle that faces
     * (aheadX, aheadY) on the map, or on foot with both 0.
     */
    void take(float eyeX, float eyeY, float aheadX, float aheadY, boolean contested) {
        Quality.Picture picture = Quality.drawn();
        this.eyeX = eyeX;
        this.eyeY = eyeY;
        this.aheadX = aheadX;
        this.aheadY = aheadY;
        aside = reach(picture.drawDistance(), picture.viewDistance(), contested);
    }

    /** Leaves nothing out: the game's own view, drawn by the mod, shows what the game's screen holds. */
    void whole() {
        aside = WHOLE;
    }

    void copy(DrawDistance from) {
        eyeX = from.eyeX;
        eyeY = from.eyeY;
        aheadX = from.aheadX;
        aheadY = from.aheadY;
        aside = from.aside;
    }

    /** Whether something whose middle stands at (x, y) on the map is drawn. */
    boolean draws(float x, float y) {
        float toX = x - eyeX, toY = y - eyeY;
        return aside == WHOLE || toX * aheadX + toY * aheadY > 0f || toX * toX + toY * toY <= aside * aside;
    }
}
