package com.cameraunlock.zomboid;

import java.util.Locale;
import zombie.GameTime;
import zombie.characters.CharacterStat;
import zombie.characters.IsoPlayer;
import zombie.characters.Stats;
import zombie.iso.weather.ClimateManager;
import zombie.scripting.objects.MoodleType;

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
    static final float FULL_NIGHT = -0.22f;
    /** How much of the sky the clouds fill with the game's cloud intensity at 0 and at 1. */
    private static final float COVER_CLEAR = 0.29f, COVER_OVERCAST = 0.9f;
    /** How much more of the sky the deck covers in the heaviest rain or snow: enough that no moon shows through a storm. */
    private static final float COVER_CLOSED = 0.2f;
    /** How much of the sun's or the moon's light the heaviest rain or snow takes out. */
    private static final float FALLING_DIMS = 0.75f;
    /** How much of the light crossing a tile of air is caught and sent to the eye: on a clear day, and added by full fog and by full rain. */
    private static final float MIST_CLEAR = 0.0045f, MIST_FOG = 0.022f, MIST_RAIN = 0.008f, MIST_SNOW = 0.025f;
    /**
     * Cloud drift in noise widths per second, in still air and per unit of the game's wind
     * intensity. A cloud overhead crosses a hand's width of sky in several seconds, not in one.
     */
    private static final float DRIFT_STILL = 0.0006f, DRIFT_PER_WIND = 0.004f;

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
    /**
     * Set by a test that measures how much a picture of a still world changes from frame to
     * frame: with it the wind does not blow and nothing moves by the clock, so whatever still
     * changes is the picture's own unsteadiness.
     */
    private static final boolean STILL = Boolean.getBoolean("cameraunlock.zomboid.still");
    float night;
    /** The game's light for a square of open ground that no lamp shines on. The scene sets it. */
    float openRed, openGreen, openBlue;
    /** How much of the daylight the game gives the open ground the picture keeps: see Evening. */
    float daylightKept = 1f;
    /** How much of what daylightKept keeps a room keeps: see Evening.ROOM_DAYLIGHT. The scene sets it. */
    float roomKept = 1f;
    /** How many of the burning lamps nearest the eye the picture is told of: see WorldScene.lampsSeen. */
    static final int LAMPS_SEEN = 16;
    /**
     * The lamps the picture is told of: where each burns in the world's axes and how far it
     * reaches, four to a lamp, a reach of 0 where there is none, and the colour it lights a square
     * in, linear, three to a lamp. The scene sets them.
     */
    final float[] lampAt = new float[LAMPS_SEEN * 4], lampHue = new float[LAMPS_SEEN * 3];
    /** What a room the game has not shown is lit by, without a lamp and with one: see WorldScene.LIT_STEP. The scene sets them. */
    final float[] roomLight = new float[3], lampLight = new float[3];
    /** What anything else the game has given no light is lit by, the open ground in sight: see WorldScene.OPEN_LIT. The scene sets it. */
    final float[] openUnlit = new float[3];
    /** The light the character carries, as CarriedLight gathered it for this picture. The scene sets it. */
    float[] carried = new float[8];
    /** The light what the viewer holds is in, as HeldLight.light has it for this picture. The scene sets it. */
    final float[] held = new float[5];
    /** What this picture draws a line round, or null for nothing: see Outlines. */
    Outlines.Frame outlined;
    /** Where on the map the camera the picture is drawn from stands, which a card turns to face, and the way a card on that very square lies. */
    float facedX, facedY, acrossX = 1f, acrossY;
    /** The eye the scene is built round, the player's own whichever camera draws the picture: how thick a layer is goes by how far off it is. See PipelineShaders.FACED. */
    float layersFromX, layersFromY, layersFromUp;
    float cover;
    /** How closed the cloud deck is that rain or snow falls from, 0 to 1: the square of the heavier fall. */
    float deck;
    float driftX, driftY;
    float seconds;
    /** The way the wind pushes what grows, scaled by how hard, eased after the game's wind: see Wind. */
    float windX, windY;
    /** How far the wind has carried its gusts over the ground, in tiles, and the seconds on the clock grass shivers by: see Wind. */
    float gustX, gustY, flutter;
    /** How much there is in the air for light to catch: a little on a clear day, a lot in fog. */
    float mist;
    /** How thick the game has its fog, 0 to 1. */
    float fog;
    /** How hard the game has the wind blow, 0 to 1. */
    float blowing;
    /** How hard it is raining, 0 to 1. Snow is not rain. */
    float rain;
    /** How hard it is snowing, 0 to 1, and how much of the ground snow lies on, 0 to 1. */
    float snow, snowLying;
    /**
     * Snow a strong wind has driven onto the windward side of what stands up out of it: the
     * way such a side faces, as long as how much is stuck there, 0 to 1. See plastered.
     */
    float plasterX, plasterY;

    /** How much snow lies on what grows in a view: none in Low, which draws what grows as the game does. */
    float snowOnPlants(int view) {
        return Quality.plain(view) ? 0f : snowLying;
    }
    /** How fast the air moves, in tiles a second, how far it has carried the rain and the snow, in tiles, and the clocks each falls by: see Wind. */
    float airX, airY, carriedX, carriedY, rainClock, snowClock;
    /** How wet the ground is, 0 to 1: it takes a while to wet in rain and longer to dry after it. */
    float wet;
    /** How far out of doors the eye is, 0 in a room to 1 in the open. Set by the scene, and by the rigs that draw a scene of their own. */
    float outdoors;
    /**
     * How far the eye is out from under any roof, 0 under a roof, a porch's included, to 1 under
     * open sky: from there a room's dusty air is seen only faintly. Set by the scene, and 0 in the
     * rigs that draw a scene of their own.
     */
    float openOver;
    /** How much light a tile of air takes out of a line of sight: see Eyesight. 0 while the development switch takes it out. */
    float thick;
    /** How narrow the character's view has become, 0 to 1: see Eyesight.narrowed. */
    float narrowed;
    /** The light lightning adds to the sky and to what the sky lights, already as light (not squared up), and the way to it: see Lightning.see. */
    float flashRed, flashGreen, flashBlue;
    float flashX, flashY, flashUp = 1f;
    /** How bright the cloud a bolt came out of glows inside, and the way from the eye to where it glows. */
    float burst;
    float burstX, burstY, burstUp = 1f;
    /** The bolts to draw, the first seenCount of seen. */
    final Lightning.Seen[] seen = {new Lightning.Seen(), new Lightning.Seen(), new Lightning.Seen(), new Lightning.Seen()};
    int seenCount;
    /** The squares that burn, the first fireCount records of fires (see Fires), and how far each is from the eye, squared. */
    final float[] fires = new float[Fires.MOST * Fires.FLOATS];
    final float[] fireFar = new float[Fires.MOST];
    /** The game's own animations (IsoAnim) of each square's flames and of its smoke, or null: see Fires.look. Objects, for the tests. */
    final Object[] flameLook = new Object[Fires.MOST], smokeLook = new Object[Fires.MOST];
    int fireCount;
    /** The leaves falling and lying round the eye, the first leafCount records of leaves: see LeafFall. */
    final float[] leaves = new float[LeafFall.MOST * LeafFall.FLOATS];
    int leafCount;

    private static float driftedX, driftedY;
    private static final Wind wind = new Wind();
    private static float wetness;
    private static final float SECONDS_TO_WET = 12f, SECONDS_TO_DRY = 180f;
    private static long lastNanos;
    /**
     * The game works out how much snow lies only on the hour, so it comes in steps, and each
     * step would raise or let down every plant standing in it at once: what is drawn eases to
     * it with a time constant of SNOW_SETTLES seconds, a step of 0.3 landing in about fifteen.
     * After a pause in the pictures longer than SNOW_SNAPS seconds, a load or a jump to another
     * place, it is taken as it is: so is a frame that hitches that long, which shows a step whole.
     */
    static final float SNOW_SETTLES = 3f, SNOW_SNAPS = 1f;
    private static volatile float snowShown = -1f;
    /**
     * Snow driven onto a trunk builds up while it falls in a strong wind, over about
     * PLASTER_BUILDS seconds of a blizzard, from a wind of PLASTER_FROM of the game's intensity
     * at the least and fully from PLASTER_FULL, and stays until the snow lying thaws: there is
     * never more stuck than lies. Kept from frame to frame, on the logic thread.
     */
    private static final float PLASTER_BUILDS = 40f, PLASTER_FROM = 0.4f, PLASTER_FULL = 0.8f;
    private static float plasteredX, plasteredY;

    /** How deep the snow the picture shows lying is, 0 to 1. Any thread. */
    static float snowLyingNow() {
        return Math.max(snowShown, 0f);
    }

    private static volatile float snowFalling;

    /** How hard snow falls, 0 to 1, as the pictures last had it. Any thread. */
    static float snowFallingNow() {
        return snowFalling;
    }
    private static final long START_NANOS = System.nanoTime();

    String describe() {
        return String.format(Locale.ROOT, "sun up %.2f night %.2f cover %.2f mist %.4f wind (%.2f, %.2f) rain %.2f wet %.2f fog %.2f snow %.2f air %.4f narrowed %.2f lightning %.3f cloud %.2f bolts %d",
                sunUp, night, cover, mist, windX, windY, rain, wet, fog, snow, thick, narrowed, flashBlue, burst, seenCount);
    }

    /** How much brighter a drop of rain is than the air it falls through: over the sky in rain it is a faint pale line. */
    static final float RAIN_OVER_AIR = 1.1f;
    /** How much of lightning's light over the sky a drop of rain shows: a lit drop is a bead of the flash. */
    static final float RAIN_FLASHED = 1.2f;

    /**
     * The light of a drop of rain, linear, into a red, green and blue. A drop is a lens that shows the air
     * round it, which is lit as the open ground is (the veil the rain makes of the air, Sky.veilColor), so
     * it is never brighter than that light, however the horizon glows at dusk, and has the moon's at night.
     * A flash of lightning lights every drop in the air.
     */
    void dropLight(float[] into) {
        into[0] = RAIN_OVER_AIR * (float) Math.pow(openRed * daylightKept, 2.2) + RAIN_FLASHED * flashRed;
        into[1] = RAIN_OVER_AIR * (float) Math.pow(openGreen * daylightKept, 2.2) + RAIN_FLASHED * flashGreen;
        into[2] = RAIN_OVER_AIR * (float) Math.pow(openBlue * daylightKept, 2.2) + RAIN_FLASHED * flashBlue;
    }

    void capture() {
        GameTime time = GameTime.getInstance();
        ClimateManager climate = ClimateManager.getInstance();
        float hour = time.getTimeOfDay();
        // The season's sunrise and sunset, in hours. GameTime's own getDawn and getDusk are not hours.
        float dawn = climate.getSeason().getDawn(), dusk = climate.getSeason().getDusk();
        Seasons.capture(climate.getSeason());

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
        float snowing = Math.clamp(climate.getSnowIntensity(), 0f, 1f);
        float cloudy = (float) Math.pow(clouds, 1.6);
        float overcast = Math.max(cloudy * 0.7f, Math.max(rain, snowing));
        // The game's figure is half way up for a day of broken cloud, which still shows a
        // good deal of blue: the sky only closes over near the top of it, or in rain.
        // Rain and snow fall from a deck of cloud, and a heavy fall from one with no gap in it.
        float falling = Math.max(rain, snowing);
        cover = COVER_CLEAR + (COVER_OVERCAST - COVER_CLEAR) * Math.max(cloudy * 0.75f, falling) + COVER_CLOSED * falling * falling;
        deck = falling * falling;

        zenithRed = mix(0.010f, 0.240f, day) + 0.10f * twilight;
        zenithGreen = mix(0.016f, 0.430f, day) + 0.04f * twilight;
        zenithBlue = mix(0.045f, 0.780f, day) + 0.08f * twilight;
        horizonRed = mix(0.030f, 0.700f, day) + 0.34f * twilight;
        horizonGreen = mix(0.040f, 0.810f, day) + 0.05f * twilight;
        horizonBlue = mix(0.085f, 0.920f, day) - 0.10f * twilight;
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
        // Under the cloud a heavy fall comes from, little of the sun or the moon gets through to throw a shadow.
        float beneath = 1f - FALLING_DIMS * falling;
        lightRed *= beneath;
        lightGreen *= beneath;
        lightBlue *= beneath;
        float through = (1f - 0.85f * overcast) * smooth(-0.02f, 0.08f, sunUp);
        rayRed = through * 0.55f;
        rayGreen = through * 0.55f * mix(0.55f, 0.95f, white);
        rayBlue = through * 0.55f * mix(0.25f, 0.80f, white);

        float windAngle = climate.getWindAngleRadians();
        // The game's wind as it is this moment: its intensity with the game's own lulls and
        // gusts in it, which is what the game sways its trees and slants its rain by. So the
        // grass here rises and falls with them.
        float gusting = (float) ClimateManager.getWindTickFinal();
        float windStrength = Wind.push(gusting);
        fog = Math.clamp(climate.getFogIntensity(), 0f, 1f);
        blowing = Math.clamp(climate.getWindIntensity(), 0f, 1f);
        // Falling snow catches a beam of light more than rain does: each flake is white and slow.
        mist = MIST_CLEAR + MIST_FOG * fog + MIST_RAIN * rain + MIST_SNOW * snowing;
        // Morning and evening air holds more than noon's: damp rising, dust and pollen hanging.
        mist *= 1f + 1.0f * (1f - Math.clamp(sunUp / 0.6f, 0f, 1f)) * (1f - night);

        this.rain = climate.isSnowing() ? 0f : rain;
        snow = snowing;
        snowFalling = snowing;
        long now = System.nanoTime();
        float lying = Math.clamp(climate.getSnowFracNow(), 0f, 1f);
        snowShown = shown(snowShown, lying, (now - lastNanos) / 1e9f, GameTime.isGamePaused());
        snowLying = snowShown;
        if (lastNanos != 0 && !GameTime.isGamePaused()) {
            float elapsed = Math.min((now - lastNanos) / 1e9f, 0.25f);
            wetness = Math.clamp(wetness + (this.rain > 0.05f ? elapsed / SECONDS_TO_WET : -elapsed / SECONDS_TO_DRY), 0f, 1f);
            float speed = DRIFT_STILL + DRIFT_PER_WIND * Math.clamp(climate.getWindIntensity(), 0f, 1f);
            float windCos = (float) Math.cos(windAngle), windSin = (float) Math.sin(windAngle);
            driftedX += windCos * speed * elapsed;
            driftedY += windSin * speed * elapsed;
            // The noise repeats every 1, and a float that only ever grew would lose the fraction.
            driftedX -= (float) Math.floor(driftedX);
            driftedY -= (float) Math.floor(driftedY);
            wind.blow(windCos * windStrength, windSin * windStrength, Wind.speed(gusting), elapsed);
            float driving = snowing * Math.clamp((blowing - PLASTER_FROM) / (PLASTER_FULL - PLASTER_FROM), 0f, 1f);
            float[] plaster = plastered(plasteredX, plasteredY, -windCos, -windSin, driving, snowShown, elapsed);
            plasteredX = plaster[0];
            plasteredY = plaster[1];
        }
        plasterX = plasteredX;
        plasterY = plasteredY;
        windX = wind.pushX;
        windY = wind.pushY;
        gustX = wind.travelX;
        gustY = wind.travelY;
        flutter = wind.flutter;
        airX = wind.airX;
        airY = wind.airY;
        carriedX = wind.carriedX;
        carriedY = wind.carriedY;
        rainClock = wind.rainClock;
        snowClock = wind.snowClock;
        lastNanos = now;
        wet = wetness;
        driftX = driftedX;
        driftY = driftedY;
        seconds = (now - START_NANOS) / 1e9f % 3600f;
        if (STILL) {
            windX = 0f;
            windY = 0f;
            seconds = 0f;
        }
    }

    /** What the character's eyes make of the air and of what they look at, from the climate this captured and the viewer. */
    void sight(IsoPlayer viewer) {
        if (!Eyesight.on()) {
            thick = narrowed = 0f;
            return;
        }
        thick = Eyesight.thickness(fog, rain, snow, blowing, Online.contested());
        Stats stats = viewer.getStats();
        float intoxication = stats.get(CharacterStat.INTOXICATION);
        narrowed = seen(Eyesight.narrowed(viewer.getVehicle() != null, stats.get(CharacterStat.FATIGUE), stats.isAtMaximum(CharacterStat.FATIGUE),
                viewer.getMoodles().getMoodleLevel(MoodleType.DRUNK), intoxication, viewer.getMoodles().getMoodleLevel(MoodleType.PANIC),
                viewer.getWornItemsVisionMultiplier()));
    }

    /** Under this a narrowing is too slight to see, and is not drawn. */
    private static final float SLIGHTEST = 0.02f;

    private static float seen(float amount) {
        float clamped = Math.clamp(amount, 0f, 1f);
        return clamped < SLIGHTEST ? 0f : clamped;
    }

    /**
     * The snow stuck to the windward side of trunks, as plasterX and plasterY say, elapsed
     * seconds after it was (x, y): driven towards (facingX, facingY), the way a side the wind
     * comes at faces, as hard as driving says, 0 to 1, and no more than lying, the snow drawn
     * as lying. It thaws as that does.
     */
    static float[] plastered(float x, float y, float facingX, float facingY, float driving, float lying, float elapsed) {
        float take = Math.min(driving * elapsed / PLASTER_BUILDS, 1f);
        float toX = x + (facingX * Math.max(lying, 0f) - x) * take, toY = y + (facingY * Math.max(lying, 0f) - y) * take;
        float stuck = (float) Math.hypot(toX, toY), most = Math.max(lying, 0f);
        if (stuck > most) {
            toX *= most / stuck;
            toY *= most / stuck;
        }
        return new float[] {toX, toY};
    }

    /** How much snow the pictures last drew as lying: none before the first. */
    static float snowDrawn() {
        return Math.max(snowShown, 0f);
    }

    /**
     * How much snow is drawn as lying, seconds after shown was (negative for nothing drawn yet),
     * the game having lying: the game's own after a gap longer than SNOW_SNAPS, held while the
     * game is paused, and eased towards it otherwise.
     */
    static float shown(float shown, float lying, float seconds, boolean paused) {
        return shown < 0f || seconds > SNOW_SNAPS ? lying : paused ? shown : settled(shown, lying, seconds);
    }

    /** How much snow is drawn as lying elapsed seconds on from shown, the game having lying: none left over once it is within a hair of it, so no snow means none drawn. */
    static float settled(float shown, float lying, float elapsed) {
        float next = shown + (lying - shown) * (1f - (float) Math.exp(-elapsed / SNOW_SETTLES));
        return Math.abs(lying - next) < 0.002f ? lying : next;
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
