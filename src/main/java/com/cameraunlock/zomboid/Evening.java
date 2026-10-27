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

    /**
     * What a room's daylight keeps of its blue and its green with the sun low and clear
     * (SkyState.lowSun 1), as light. The game lights a room the same white from noon to dusk; a
     * room the low sun comes into is gold. The green is halfway between red's and blue's in
     * stops: the indoor balance (Develop.settle) takes most of a green cast out and little of a
     * warm one, and this it reads as warm alone.
     */
    static final float LOW_SUN_BLUE = 0.4f, LOW_SUN_GREEN = (float) Math.sqrt(LOW_SUN_BLUE);

    private Evening() {}

    /** What the green of a room's daylight is multiplied by with the sun this low: see LOW_SUN_BLUE. */
    static float lowSunGreen(float lowSun) {
        return 1f - (1f - LOW_SUN_GREEN) * lowSun;
    }

    /** What the blue of a room's daylight is multiplied by with the sun this low: see LOW_SUN_BLUE. */
    static float lowSunBlue(float lowSun) {
        return 1f - (1f - LOW_SUN_BLUE) * lowSun;
    }

    /**
     * How much of a light given indoors, with that much of the daylight kept (dimmed), is the
     * day's, as light: as the world shader reads a room's, up to the open ground's light it is
     * the day's and over that a lamp's.
     */
    static float dayShare(float dimmedLight, float gameOpen, float kept) {
        float given = dimmedLight + gameOpen * (1f - kept);
        return given <= 0f ? 0f : (float) Math.pow(Math.min(gameOpen / given, 1f), 2.2);
    }

    /**
     * What a light given indoors, dimmed and in the game's count, is as red, green and blue with a
     * low sun that gold (SkyState.roomGold) on the share of it that is the day's, into stood from at.
     */
    static void stood(float dimmedLight, float gameOpen, float kept, float gold, float[] stood, int at) {
        float share = dayShare(dimmedLight, gameOpen, kept);
        stood[at] = dimmedLight;
        stood[at + 1] = dimmedLight * (float) Math.pow(1f - share * (1f - lowSunGreen(gold)), 1.0 / 2.2);
        stood[at + 2] = dimmedLight * (float) Math.pow(1f - share * (1f - lowSunBlue(gold)), 1.0 / 2.2);
    }

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
     * A square's light with that much of the daylight kept. What a square has over the open
     * ground's light came from a lamp, and stays.
     */
    static float dimmed(float light, float gameOpen, float kept) {
        float daylight = Math.min(light, gameOpen);
        return light - daylight + daylight * kept;
    }
}
