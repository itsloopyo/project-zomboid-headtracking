package com.cameraunlock.zomboid;

/**
 * The daylight the first-person picture gives the ground as the sun goes down and comes up.
 *
 * The game holds its daylight at full until the season's dusk and only then lets it fall, over
 * the two and a half hours to midnight. The picture's sun has set by dusk and its sky is the
 * night's a little after, so for over an hour the ground would be lit as at noon under a black
 * sky full of stars. Here the open ground's light follows the sun: it begins to go as the sun gets
 * low and is the night's by the time the sky is. It is never more than the game gives, so once
 * the game's own light has fallen the picture is the game's again.
 *
 * Lights are as the game counts them, 0 to 1, before the picture's curve.
 */
final class Evening {
    /** The light of open ground at full night as measured in the game: it is learnt again each night, for a game set darker or lighter. */
    static final float NIGHT_OPEN = 0.18f;
    /**
     * How much of the open ground's daylight a room has, as the game counts light. The game lights
     * a room by day nearly as the street outside, so the sun on its floor and the day through its
     * windows were hardly brighter than its walls. The eye opens up by as much as the room's
     * daylight is taken down, so the room itself looks as it did, and the sun on its floor, the day
     * through its windows and the lit air are that much brighter than it. 0.45 is about a sixth of the light.
     */
    static final float ROOM_DAYLIGHT = 0.45f;
    /** The sun's height, as the sine of its elevation, below which the ground begins to lose its light. */
    private static final float SUN_LOW = 0.30f;

    private Evening() {}

    /** The light open ground has with the sun that high. */
    static float open(float sunUp, float nightOpen) {
        return nightOpen + (1f - nightOpen) * day(sunUp);
    }

    /** How much of the day the sun that high gives, the night's light left out: 0 at full night, 1 from SUN_LOW up. */
    static float day(float sunUp) {
        float t = Math.clamp((sunUp - SkyState.FULL_NIGHT) / (SUN_LOW - SkyState.FULL_NIGHT), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** How much of the daylight the game gives the open ground the picture keeps, 1 for all of it. */
    static float kept(float sunUp, float gameOpen, float nightOpen) {
        return gameOpen <= 0f ? 1f : Math.min(open(sunUp, nightOpen) / gameOpen, 1f);
    }

    /**
     * How much of the daylight a picture keeps whose camera is the game's own, over the map, or on
     * its way from there down to the eye: all of it from the game's camera, whose view the game
     * draws by its own light at every hour, and the eye's share once the camera is at the eye.
     *
     * @param eyeKept what the eye keeps: see kept
     * @param landed  how much of the way from the game's camera to the eye the camera has come, 0 to 1
     */
    static float seenFrom(float eyeKept, float landed) {
        return eyeKept + (1f - eyeKept) * (1f - landed);
    }

    /**
     * A square's light with that much of the daylight kept. What a square has over the open
     * ground's light came from a lamp, and stays.
     */
    static float dimmed(float light, float gameOpen, float kept) {
        float daylight = Math.min(light, gameOpen);
        return light - daylight + daylight * kept;
    }
}
