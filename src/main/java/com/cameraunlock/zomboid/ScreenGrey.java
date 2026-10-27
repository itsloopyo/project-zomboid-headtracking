package com.cameraunlock.zomboid;

/**
 * How much the game's screen shader (screen.frag) greys a player's view as it puts it on the
 * screen. The game's figure (RenderSettings.PlayerRenderSettings.getDesaturation) is its
 * climate's desaturation times the daylight: the season's, (1 - the season's day percent) x 0.4,
 * and on top of it the morning fog's and the weather's. The first person picture draws its own
 * fog, grades its own weather and overcast, and shows the season by the sun's height and the
 * day's length, so it keeps none of the game's grey: greyed by it, a clear winter day read as
 * overcast.
 */
final class ScreenGrey {
    private ScreenGrey() {}

    /**
     * The desaturation the screen shader greys a view by, the game's being stock: none under as
     * much of the first person picture as covers the view, its style shown, and the game's own
     * otherwise. Low's picture is the game's look and keeps the game's.
     *
     * @param covered how much of the view the first person picture covers, 0 to 1
     * @param styled  how much of the picture is its own style rather than the game's view from overhead, 0 to 1
     */
    static float under(float stock, boolean plain, float covered, float styled) {
        if (plain) {
            return stock;
        }
        return stock * (1f - covered * styled);
    }
}
