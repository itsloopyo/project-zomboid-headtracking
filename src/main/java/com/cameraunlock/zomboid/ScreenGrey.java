package com.cameraunlock.zomboid;

/**
 * How much the game's screen shader (screen.frag) greys a player's view as it puts it on the
 * screen. The game's figure (RenderSettings.PlayerRenderSettings.getDesaturation) is its
 * climate's desaturation times the daylight: the season's, (1 - the season's day percent) x 0.4,
 * and on top of it the morning fog's and the weather's. The first person picture draws its own
 * fog and grades its own weather and overcast, and greyed by the game's as well it read as
 * overcast under a clear sky. It grades nothing by the season, so it keeps the season's grey and
 * no more.
 */
final class ScreenGrey {
    private ScreenGrey() {}

    /** The most of the game's desaturation that is the season's: ClimateValues', 0.4 at a season's shortest day. */
    static final float SEASON_MOST = 0.4f;

    /**
     * The desaturation the screen shader greys a view by, the game's being stock: the season's
     * part of it under as much of the first person picture as covers the view, its style shown,
     * and the game's own otherwise. Low's picture is the game's look and keeps the game's.
     *
     * @param dayPercent the season's day percent (ErosionSeason.getCurDayPercent)
     * @param daylight   the climate's daylight strength, which the game scales its figure by
     * @param covered    how much of the view the first person picture covers, 0 to 1
     * @param styled     how much of the picture is its own style rather than the game's view from overhead, 0 to 1
     */
    static float under(float stock, float dayPercent, float daylight, boolean plain, float covered, float styled) {
        if (plain) {
            return stock;
        }
        float season = Math.min(stock, (1f - dayPercent) * SEASON_MOST * daylight);
        return stock + (season - stock) * covered * styled;
    }
}
