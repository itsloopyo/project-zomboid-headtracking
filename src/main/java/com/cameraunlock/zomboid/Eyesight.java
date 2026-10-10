package com.cameraunlock.zomboid;

/**
 * How far and how clearly the character sees, as the first person picture shows it.
 *
 * Looking down on its map the game hides what its character cannot see: fog lies over
 * everything but a few tiles round them, a character past the game's sight for the weather and
 * the hour is not drawn, and tiredness, panic, drink and some headwear narrow the cone of what
 * the character takes in. From the eye the same things are the air between the eye and what
 * it looks at and the edge of the eye's view. The game's own swimming picture for drink is
 * drawn over the whole window, the first person picture included (seen), and is left to it;
 * its blur for short sight is drawn the same way (seen).
 *
 * Where players can fight (Online.contested) all of it holds whatever a development switch
 * says: see on.
 */
final class Eyesight {
    private Eyesight() {}

    /**
     * How much light a tile of air takes out of a line of sight, in full fog, in the heaviest
     * rain and in the heaviest snow. In the game's own view (captures at 1920 by 1080) full
     * fog leaves a zombie 3.7 tiles off plain, one at 5.6 half hidden and one at 10 gone; here
     * they are 7, 27 and 78 parts in a hundred hidden: the game's figure is for its view from
     * above, and from the eye, driving most of all, thinner air reads better. At fog 0.3 the game's view is a light
     * haze over the street, and its fog thickens far faster than the game's figure for it
     * rises: here the air is that figure to the power 3, and fog 0.3 hides 9 parts in a
     * hundred at 16 tiles, fog 0.5 61 at 30 tiles. The game's rain and snow hide nothing of the map. Rain here takes
     * three fifths of what stands 80 tiles off, where the built world of High ends, and snow
     * nine tenths.
     */
    static final float FOG_THICK = 0.30f, RAIN_THICK = 0.012f, SNOW_THICK = 0.03f;
    /** The power of the game's fog figure the air goes by. */
    static final double FOG_POWER = 3.0;
    /** How far round the eye the air thins away, as the game keeps its fog off the ground nearest the character. */
    static final float FOG_CLEAR_NEAR = 5f;
    /** How deep the air is over the eye for looking up through: the sky shows through thin fog and not through thick. */
    static final float SKY_THROUGH = 12f;

    /** For development only: the first person picture as it was without any of this. Never where players can fight. */
    static volatile boolean without = Boolean.getBoolean("cameraunlock.zomboid.withoutSight");

    /** Whether the picture shows the character's sight as the game has it. */
    static boolean on() {
        return on(without, Online.contested());
    }

    /** The same, from the development switch and whether players can fight here, where it is always on. */
    static boolean on(boolean switchedOff, boolean contested) {
        return !switchedOff || contested;
    }

    /**
     * Whether a zombie before the eye is drawn whole, whatever the game's own fade says: within
     * the game's own sight for the weather and the hour (GameTime.getViewDist), on a server as
     * alone. Past that it is left to the game.
     *
     * @param sightOn         whether the picture shows the character's sight: see on
     * @param distanceSquared how far off it is, squared, in tiles
     * @param gameSees        the game's sight, in tiles
     */
    static boolean shownWhole(boolean sightOn, float distanceSquared, float gameSees) {
        return !(sightOn && distanceSquared > gameSees * gameSees);
    }

    /** How much light one tile of air takes out of a line of sight, with the game's fog, rain and snow each from 0 to 1. */
    static float thickness(float fog, float rain, float snow) {
        return FOG_THICK * (float) Math.pow(fog, FOG_POWER) + RAIN_THICK * rain + SNOW_THICK * snow;
    }

    /**
     * How much of what stands that far off the air hides, from 0 to 1, as the shaders work
     * it out: the tiles nearer than FOG_CLEAR_NEAR hardly count, with no edge where they end.
     */
    static float veiled(float thickness, float range) {
        float past = range - FOG_CLEAR_NEAR;
        float counted = past > 20f ? past : (float) Math.log(1.0 + Math.exp(past));
        return 1f - (float) Math.exp(-thickness * counted);
    }

    /**
     * How narrow the character's view has become from what the game narrows its cone by
     * other than the dark: tiredness, drink, panic and what is worn over the eyes. 0 is the
     * game's widest cone in daylight, 1 the narrowest it allows.
     *
     * On foot the game's cone is 144 of its degrees in daylight; tiredness takes 72 at full
     * fatigue and 36 more when it is at its height, drink 0.36 a point of intoxication from the
     * second level of the moodle, the fourth level of panic 36, and what is worn multiplies
     * what is left. It never goes under 18. In a vehicle it is 324 in daylight, and tiredness
     * takes only the 36 at its height.
     *
     * @param seated       whether the character is in a vehicle
     * @param fatigue      the character's fatigue, 0 to 1
     * @param exhausted    whether fatigue is at its height
     * @param drunkLevel   the level of the drunk moodle, 0 to 4
     * @param intoxication 0 to 100
     * @param panicLevel   the level of the panic moodle, 0 to 4
     * @param worn         what the game multiplies the cone by for what is worn, 1 for nothing
     */
    static float narrowed(boolean seated, float fatigue, boolean exhausted, int drunkLevel, float intoxication, int panicLevel, float worn) {
        float widest = seated ? 324f : 144f;
        float cone = seated ? widest : widest - 72f * fatigue;
        if (exhausted) {
            cone -= 36f;
        }
        if (drunkLevel >= 2) {
            cone -= 0.36f * intoxication;
        }
        if (panicLevel == 4) {
            cone -= 36f;
        }
        cone = Math.max(cone, 18f) * worn;
        return Math.clamp((widest - cone) / (widest - 18f), 0f, 1f);
    }
}
