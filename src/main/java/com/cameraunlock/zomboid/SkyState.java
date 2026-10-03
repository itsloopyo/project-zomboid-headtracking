package com.cameraunlock.zomboid;

import zombie.GameTime;
import zombie.iso.weather.ClimateManager;

/**
 * What the sky looks like at one moment of the game's day and weather: where
 * the sun and moon are and the colours the sky pass mixes. Filled on the
 * game's logic thread from its clock and climate, read on the render thread.
 *
 * Every colour is worked out here and handed to the shader, so the colour the
 * far world fades into is the same number the sky draws at its horizon.
 */
final class SkyState {
    /** How far the sun's arc leans towards the south, +y, at noon. */
    private static final float SOUTH_LEAN = 0.45f;
    /** The sun's height, as the sine of its elevation, at which the day's colours are fully in. */
    private static final float FULL_DAY = 0.28f;
    /** Below this height the sky is the night's. */
    private static final float FULL_NIGHT = -0.22f;
    /** How much of the sky the clouds fill with the game's cloud intensity at 0 and at 1. */
    private static final float COVER_CLEAR = 0.29f, COVER_OVERCAST = 0.9f;
    /** How much of the light crossing a tile of air is caught and sent to the eye: on a clear day, and added by full fog and by full rain. */
    private static final float MIST_CLEAR = 0.0045f, MIST_FOG = 0.022f, MIST_RAIN = 0.008f;
    /** Cloud drift in noise widths per second, in still air and per unit of the game's wind intensity. */
    private static final float DRIFT_STILL = 0.0025f, DRIFT_PER_WIND = 0.02f;

    /** Unit vectors towards the sun, the moon and whichever of them lights the clouds, in world axes. */
    float sunX, sunY, sunUp;
    float moonX, moonY, moonUp;
    float lightX, lightY, lightUp;
    float lightRed, lightGreen, lightBlue;
    float sunRed, sunGreen, sunBlue;
    float zenithRed, zenithGreen, zenithBlue;
    float horizonRed, horizonGreen, horizonBlue;
    /** The warm band along the horizon on the sun's side at dawn and dusk. */
    float glowRed, glowGreen, glowBlue;
    /** The light shafts' colour, already scaled by how much sun gets through the clouds. */
    float rayRed, rayGreen, rayBlue;
    float night;
    float cover;
    float driftX, driftY;
    float seconds;
    /** The way the wind blows, scaled by how hard: what growing things lean and shake along. */
    float windX, windY;
    /** How much there is in the air for light to catch: a little on a clear day, a lot in fog. */
    float mist;

    private static float driftedX, driftedY;
    private static long lastNanos;
    private static final long START_NANOS = System.nanoTime();

    String describe() {
        return String.format(java.util.Locale.ROOT, "sun up %.2f night %.2f cover %.2f mist %.4f wind (%.2f, %.2f)", sunUp, night, cover, mist, windX, windY);
    }

    void capture() {
        GameTime time = GameTime.getInstance();
        ClimateManager climate = ClimateManager.getInstance();
        float hour = time.getTimeOfDay();
        // The season's sunrise and sunset, in hours. GameTime's own getDawn and getDusk are not hours.
        float dawn = climate.getSeason().getDawn(), dusk = climate.getSeason().getDusk();

        // One turn a day: across the sky from east, +x, to west between dawn and dusk, and
        // round underneath for the rest of it.
        float angle;
        if (hour >= dawn && hour <= dusk) {
            angle = (float) Math.PI * (hour - dawn) / (dusk - dawn);
        } else {
            float sinceDusk = hour > dusk ? hour - dusk : hour + 24f - dusk;
            angle = (float) Math.PI * (1f + sinceDusk / (24f - (dusk - dawn)));
        }
        float height = (float) Math.sin(angle);
        float length = (float) Math.sqrt(1f + SOUTH_LEAN * SOUTH_LEAN * height * height);
        sunX = (float) Math.cos(angle) / length;
        sunY = SOUTH_LEAN * Math.abs(height) / length;
        sunUp = height / length;
        float moonLength = (float) Math.sqrt(sunX * sunX + 0.09f + sunUp * sunUp);
        moonX = -sunX / moonLength;
        moonY = 0.3f / moonLength;
        moonUp = -sunUp / moonLength;

        float day = smooth(0f, FULL_DAY, sunUp);
        night = 1f - smooth(FULL_NIGHT, 0f, sunUp);
        float twilight = Math.max(0f, 1f - Math.abs(sunUp) / FULL_DAY);
        float clouds = Math.clamp(climate.getCloudIntensity(), 0f, 1f);
        float rain = Math.clamp(climate.getRainIntensity(), 0f, 1f);
        float overcast = Math.max((float) Math.pow(clouds, 1.6) * 0.7f, rain);
        // The game's figure is half way up for a day of broken cloud, which still shows a
        // good deal of blue: the sky only closes over near the top of it, or in rain.
        cover = COVER_CLEAR + (COVER_OVERCAST - COVER_CLEAR) * Math.max((float) Math.pow(clouds, 1.6) * 0.75f, rain);

        zenithRed = mix(0.010f, 0.110f, day) + 0.10f * twilight;
        zenithGreen = mix(0.016f, 0.300f, day) + 0.04f * twilight;
        zenithBlue = mix(0.045f, 0.740f, day) + 0.08f * twilight;
        horizonRed = mix(0.030f, 0.560f, day) + 0.34f * twilight;
        horizonGreen = mix(0.040f, 0.730f, day) + 0.05f * twilight;
        horizonBlue = mix(0.085f, 0.900f, day) - 0.10f * twilight;
        glowRed = 1.00f * twilight;
        glowGreen = 0.42f * twilight;
        glowBlue = 0.12f * twilight;

        // Overcast takes the colour out of the sky and most of the sun out of it.
        float zenithGrey = 0.6f * grey(zenithRed, zenithGreen, zenithBlue) + 0.25f * day;
        float horizonGrey = 0.85f * grey(horizonRed, horizonGreen, horizonBlue);
        zenithRed = mix(zenithRed, zenithGrey, overcast);
        zenithGreen = mix(zenithGreen, zenithGrey, overcast);
        zenithBlue = mix(zenithBlue, zenithGrey * 1.05f, overcast);
        horizonRed = mix(horizonRed, horizonGrey, overcast);
        horizonGreen = mix(horizonGreen, horizonGrey, overcast);
        horizonBlue = mix(horizonBlue, horizonGrey * 1.03f, overcast);
        glowRed *= 1f - overcast;
        glowGreen *= 1f - overcast;
        glowBlue *= 1f - overcast;

        float up = smooth(-0.06f, 0.02f, sunUp) * (1f - 0.9f * overcast);
        float white = smooth(0f, 0.4f, sunUp);
        sunRed = up;
        sunGreen = up * mix(0.50f, 0.96f, white);
        sunBlue = up * mix(0.20f, 0.88f, white);

        if (sunUp > -0.06f) {
            lightX = sunX; lightY = sunY; lightUp = sunUp;
            float strength = smooth(-0.06f, 0.1f, sunUp);
            lightRed = strength; lightGreen = strength * mix(0.55f, 0.97f, white); lightBlue = strength * mix(0.30f, 0.92f, white);
        } else {
            lightX = moonX; lightY = moonY; lightUp = moonUp;
            lightRed = 0.10f * night; lightGreen = 0.13f * night; lightBlue = 0.20f * night;
        }
        float through = (1f - 0.85f * overcast) * smooth(-0.02f, 0.08f, sunUp);
        rayRed = through * 0.55f;
        rayGreen = through * 0.55f * mix(0.55f, 0.95f, white);
        rayBlue = through * 0.55f * mix(0.25f, 0.80f, white);

        float windAngle = climate.getWindAngleRadians();
        float windStrength = 0.35f + 1.4f * Math.clamp(climate.getWindIntensity(), 0f, 1f);
        windX = (float) Math.cos(windAngle) * windStrength;
        windY = (float) Math.sin(windAngle) * windStrength;
        mist = MIST_CLEAR + MIST_FOG * Math.clamp(climate.getFogIntensity(), 0f, 1f) + MIST_RAIN * rain;

        long now = System.nanoTime();
        if (lastNanos != 0 && !GameTime.isGamePaused()) {
            float elapsed = Math.min((now - lastNanos) / 1e9f, 0.25f);
            float speed = DRIFT_STILL + DRIFT_PER_WIND * Math.clamp(climate.getWindIntensity(), 0f, 1f);
            float wind = climate.getWindAngleRadians();
            driftedX += (float) Math.cos(wind) * speed * elapsed;
            driftedY += (float) Math.sin(wind) * speed * elapsed;
            // The noise repeats every 1, and a float that only ever grew would lose the fraction.
            driftedX -= (float) Math.floor(driftedX);
            driftedY -= (float) Math.floor(driftedY);
        }
        lastNanos = now;
        driftX = driftedX;
        driftY = driftedY;
        seconds = (now - START_NANOS) / 1e9f % 3600f;
    }

    private static float mix(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float smooth(float from, float to, float value) {
        float t = Math.clamp((value - from) / (to - from), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float grey(float red, float green, float blue) {
        return 0.3f * red + 0.59f * green + 0.11f * blue;
    }
}
