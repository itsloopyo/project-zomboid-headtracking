package com.cameraunlock.zomboid;

import java.util.Locale;
import zombie.GameTime;
import zombie.SandboxOptions;
import zombie.characters.CharacterStat;
import zombie.characters.IsoPlayer;
import zombie.characters.Stats;
import zombie.iso.weather.ClimateManager;
import zombie.iso.weather.ClimateMoon;
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
    /** The moon's light at the game's Normal night darkness with no moon to see or no clear sky to see it in, as the sky pass's colours are given. */
    static final float MOON_RED = 0.10f, MOON_GREEN = 0.13f, MOON_BLUE = 0.20f;
    /**
     * The stock view's night floor for each of the sandbox's Night Darkness settings, Pitch Black,
     * Dark, Normal and Bright (RenderSettings.PlayerRenderSettings): the moon's light goes with it, so
     * Pitch Black has none and Bright more than Normal.
     */
    static final float[] NIGHT_FLOOR = {0f, 0.07f, 0.15f, 0.25f};
    /**
     * What a full moon in a clear sky adds to that floor. The stock view adds 0.075 by the moon whatever
     * the sky: so much here put a wood's floor under a full moon at twice what the stock view shows in
     * its cone there. At this it is a little darker than that, at Normal.
     */
    static final float FULL_MOON_ADDS = 0.04f;
    /** The game moves the moon's phase on a quarter at a time at midnight: the light eases to it with this time constant in seconds. */
    static final float MOON_SETTLES = 3f;
    private static float moonShown = -1f;
    private final float[] light = new float[3];
    /** How much of the light crossing a tile of air is caught and sent to the eye: on a clear day, and added by full fog and by full rain. */
    private static final float MIST_CLEAR = 0.0045f, MIST_FOG = 0.022f, MIST_RAIN = 0.008f, MIST_SNOW = 0.025f;
    /**
     * Cloud drift in noise widths per second, in still air and per unit of the game's wind
     * intensity. A cloud overhead crosses a hand's width of sky in several seconds, not in one.
     */
    static final float DRIFT_STILL = 0.0006f, DRIFT_PER_WIND = 0.004f;

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
    /** How low and gold the sun is and how much of it gets through cloud and fog, 0 to 1, at dusk and at dawn alike: none at noon or at night. See Evening.LOW_SUN_BLUE. */
    float lowSun;
    /** As much of lowSun as the picture gives rooms and who is in them: none in Low, which lights a room as the game does. */
    float roomGold;
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
    /** What a room the game has not shown is lit by, without a lamp and with one: see WorldScene.LIT_STEP. The scene sets them. */
    final float[] roomLight = new float[3], lampLight = new float[3];
    /** What anything else the game has given no light is lit by, the open ground's with no lamp on it: see WorldScene.OPEN_LIT. The scene sets it. */
    final float[] openUnlit = new float[3];
    /** The open ground's light by as much of the day as there is, where rooms are lit by the day they let in, or 0: see SpriteGeometry.Mesh.day. The scene sets it. */
    final float[] daylight = new float[3];
    /** The light the character carries, as CarriedLight gathered it for this picture. The scene sets it. */
    float[] carried = new float[8];
    /** The light what the viewer holds is in, as HeldLight.light has it for this picture. The scene sets it. */
    final float[] held = new float[HeldLight.LIGHTS];
    /** What this picture draws a line round, or null for nothing: see Outlines. */
    Outlines.Frame outlined;
    /** Where on the map the camera the picture is drawn from stands, which a card turns to face, and the way a card on that very square lies. */
    float facedX, facedY, acrossX = 1f, acrossY;
    /** The eye the scene is built round, the player's own whichever camera draws the picture: how thick a layer is goes by how far off it is. See PipelineShaders.FACED. */
    float layersFromX, layersFromY, layersFromUp;
    float cover;
    /** How much of the sun the clouds over it take, eased, 0 in a gap in the dome's clouds: see shine and sunVeiledNow. */
    float sunVeiled;
    /**
     * How much of what the eye looks at lies in the clouds' shadows on the ground, 0 to 1, eased:
     * the scene sets it (see shadeLooked and cloudEased). The develop pass's grade goes by it (coverSeen).
     */
    float shadeSeen = 1f;
    /** What capture leaves for shine: how hard rain or snow falls, how grey the day's cloud makes the sky, and how white the sun is for its height. */
    float falling, overcast, sunWhite;
    /** How closed the cloud deck is that rain or snow falls from, 0 to 1: the square of the heavier fall. */
    float deck;
    /** How far the sky's clouds have drifted, under SKY_REPEATS: see skyDrifted. */
    float driftX, driftY;
    /** How far the clouds' shadows have drifted over the ground, in cells of their noise: see cloudShadow. */
    float cloudsX, cloudsY;
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
    /** The lamps hung on walls that light the picture, the first lampCount records of lamps (see Lamps), and how far each is from the eye, squared. */
    final float[] lamps = new float[Lamps.LIGHTS * Lamps.RECORD];
    final float[] lampFar = new float[Lamps.LIGHTS];
    int lampCount;
    /** The lamps in rooms and the sets that are on that light the picture, the first roomLampCount records of roomLamps, and the squares each reaches: see RoomLamps. */
    final float[] roomLamps = new float[RoomLamps.LIGHTS * RoomLamps.FLOATS];
    final float[] roomLampRows = new float[RoomLamps.LIGHTS * RoomLamps.ROW_VECTORS * 4];
    int roomLampCount;
    /** The leaves falling and lying round the eye, the first leafCount records of leaves: see LeafFall. */
    final float[] leaves = new float[LeafFall.MOST * LeafFall.FLOATS];
    int leafCount;

    private static double driftedX, driftedY;
    private static double cloudsDriftedX, cloudsDriftedY;
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
        return String.format(Locale.ROOT, "sun up %.2f night %.2f cover %.2f sun veiled %.2f shade seen %.2f mist %.4f wind (%.2f, %.2f) rain %.2f wet %.2f fog %.2f snow %.2f air %.4f narrowed %.2f lightning %.3f cloud %.2f bolts %d",
                sunUp, night, cover, sunVeiled, shadeSeen, mist, windX, windY, rain, wet, fog, snow, thick, narrowed, flashBlue, burst, seenCount);
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

    /** How white the sun's light is with the sun that high, 0 at the horizon to 1 from a sine of 0.4 up. */
    static float white(float sunUp) {
        return smooth(0f, 0.4f, sunUp);
    }

    /** How much of the sun gets through the cloud: none once it is down. */
    static float through(float sunUp, float overcast) {
        return (1f - 0.85f * overcast) * smooth(-0.02f, 0.08f, sunUp);
    }

    /** See lowSun: fog takes it as cloud does, so a room in a grey fog at sunset stays as grey as the street. */
    static float lowSun(float sunUp, float overcast, float fog) {
        return (1f - white(sunUp)) * through(sunUp, overcast) * (1f - fog);
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

        float white = white(sunUp);
        sunWhite = white;
        this.falling = falling;
        this.overcast = overcast;

        fog = Math.clamp(climate.getFogIntensity(), 0f, 1f);
        long now = System.nanoTime();
        moonShown = eased(moonShown, ClimateMoon.getInstance().getMoonFloat(), (now - lastNanos) / 1e9f, GameTime.isGamePaused());
        if (sunUp > -0.06f) {
            lightX = sunX; lightY = sunY; lightUp = sunUp;
        } else {
            lightX = moonX; lightY = moonY; lightUp = moonUp;
        }
        lightColour(sunUp, white, night, moonGives(SandboxOptions.instance.nightDarkness.getValue(), moonShown, covered(cloudy, falling, fog)), light);
        lightRed = light[0];
        lightGreen = light[1];
        lightBlue = light[2];
        // Under the cloud a heavy fall comes from, little of the sun or the moon gets through to throw a shadow.
        float beneath = 1f - FALLING_DIMS * falling;
        lightRed *= beneath;
        lightGreen *= beneath;
        lightBlue *= beneath;

        float windAngle = climate.getWindAngleRadians();
        // The game's wind as it is this moment: its intensity with the game's own lulls and
        // gusts in it, which is what the game sways its trees and slants its rain by. So the
        // grass here rises and falls with them.
        float gusting = (float) ClimateManager.getWindTickFinal();
        float windStrength = Wind.push(gusting);
        blowing = Math.clamp(climate.getWindIntensity(), 0f, 1f);
        // Falling snow catches a beam of light more than rain does: each flake is white and slow.
        mist = MIST_CLEAR + MIST_FOG * fog + MIST_RAIN * rain + MIST_SNOW * snowing;
        // Morning and evening air holds more than noon's: damp rising, dust and pollen hanging.
        mist *= 1f + 1.0f * (1f - Math.clamp(sunUp / 0.6f, 0f, 1f)) * (1f - night);

        this.rain = climate.isSnowing() ? 0f : rain;
        snow = snowing;
        snowFalling = snowing;
        float lying = Math.clamp(climate.getSnowFracNow(), 0f, 1f);
        snowShown = shown(snowShown, lying, (now - lastNanos) / 1e9f, GameTime.isGamePaused());
        snowLying = snowShown;
        if (lastNanos != 0 && !GameTime.isGamePaused()) {
            float elapsed = Math.min((now - lastNanos) / 1e9f, 0.25f);
            wetness = Math.clamp(wetness + (this.rain > 0.05f ? elapsed / SECONDS_TO_WET : -elapsed / SECONDS_TO_DRY), 0f, 1f);
            float speed = DRIFT_STILL + DRIFT_PER_WIND * Math.clamp(climate.getWindIntensity(), 0f, 1f);
            float windCos = (float) Math.cos(windAngle), windSin = (float) Math.sin(windAngle);
            driftedX = skyDrifted(driftedX, windCos * speed * elapsed);
            driftedY = skyDrifted(driftedY, windSin * speed * elapsed);
            cloudsDriftedX = cloudsDrifted(cloudsDriftedX, windCos * speed * elapsed);
            cloudsDriftedY = cloudsDrifted(cloudsDriftedY, windSin * speed * elapsed);
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
        driftX = (float) driftedX;
        driftY = (float) driftedY;
        cloudsX = (float) cloudsDriftedX;
        cloudsY = (float) cloudsDriftedY;
        seconds = (now - START_NANOS) / 1e9f % 3600f;
        if (STILL) {
            windX = 0f;
            windY = 0f;
            seconds = 0f;
        }
    }

    /**
     * The light the sun or the moon throws, red, green and blue as the sky pass's colours are given:
     * the sun's above sunUp -0.06, below it the moon's, moonGives times its light at Normal (see moonGives).
     */
    static void lightColour(float sunUp, float white, float night, float moonGives, float[] into) {
        if (sunUp > -0.06f) {
            float strength = smooth(-0.06f, 0.1f, sunUp);
            into[0] = strength;
            into[1] = strength * mix(0.55f, 0.97f, white);
            into[2] = strength * mix(0.30f, 0.92f, white);
            return;
        }
        float moon = night * moonGives;
        into[0] = MOON_RED * moon;
        into[1] = MOON_GREEN * moon;
        into[2] = MOON_BLUE * moon;
    }

    /**
     * How many times the moon's light at Normal night darkness with no moon to see it gives, at the
     * sandbox's Night Darkness setting (1 Pitch Black to 4 Bright), by how much of the moon is lit, the
     * game's 0 at a new moon to 1 at a full one, and how much of the sky is covered (see covered).
     */
    static float moonGives(int nightDarkness, float phase, float covered) {
        return (NIGHT_FLOOR[nightDarkness - 1] + FULL_MOON_ADDS * phase * (1f - covered)) / NIGHT_FLOOR[2];
    }

    /** How much of the sky is closed to the moon, 0 to 1: by cloud, by the deck rain or snow falls from, or by fog. */
    static float covered(float cloudy, float falling, float fog) {
        return Math.max(cloudy, Math.max(falling, fog));
    }

    /**
     * The moon's phase as the light shows it, elapsed seconds after it showed shown, the game having
     * phase: taken as it is on the first picture, held while the game is paused, and eased with
     * MOON_SETTLES otherwise, so a quarter's step at midnight is never a step in the light.
     */
    static float eased(float shown, float phase, float elapsed, boolean paused) {
        if (shown < 0f) {
            return phase;
        }
        return paused ? shown : shown + (phase - shown) * (1f - (float) Math.exp(-Math.min(elapsed, 0.25f) / MOON_SETTLES));
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

    /** As the game leaves a save: the next one starts dry, with its own moon and snow taken as they are. */
    static void forget() {
        wetness = 0f;
        moonShown = -1f;
        snowShown = -1f;
        lastNanos = 0;
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

    /**
     * Sets the sun's light in the sky and its shafts, and how much a low sun gilds a room, by how
     * much of the sun the clouds over it take (veiled, see sunVeiled), and by rain or snow, which
     * dims the sun even through a gap. Call after capture.
     */
    void shine(float veiled) {
        sunVeiled = veiled;
        float dimmed = Math.max(veiled, falling);
        float up = smooth(-0.06f, 0.02f, sunUp) * (1f - 0.9f * dimmed);
        sunRed = up;
        sunGreen = up * mix(0.50f, 0.96f, sunWhite);
        sunBlue = up * mix(0.20f, 0.88f, sunWhite);
        float shafts = through(sunUp, dimmed);
        rayRed = shafts * 0.55f;
        rayGreen = shafts * 0.55f * mix(0.55f, 0.95f, sunWhite);
        rayBlue = shafts * 0.55f * mix(0.25f, 0.80f, sunWhite);
        lowSun = lowSun(sunUp, dimmed, fog);
    }

    /**
     * How much of the sun the clouds over it take now, 0 to 1: the cloud the dome draws over the
     * sun (CloudDome) as far as the dome draws it, and the day's overcast where it draws none,
     * so a sun too low for the dome's clouds is as grey as the day.
     */
    float sunVeiledNow() {
        float shown = CloudDome.shown(sunUp);
        float taken = shown == 0f ? 0f : CloudDome.taken(sunX, sunY, sunUp, cover, deck, driftX, driftY);
        return shown * taken + (1f - shown) * overcast;
    }

    /**
     * How long, in seconds, what is eased by the clouds (sunVeiled, shadeSeen) takes to follow them, the
     * longest step of the clock it eases by at once, so a hitch such as an autosave is no jump,
     * and the gap after which it starts again from the clouds as they are: first person was off,
     * or another save was loaded.
     */
    static final float CLOUDS_EASE = 4f, CLOUDS_STEP_MOST = 0.25f, CLOUDS_RESTART = 10f;

    /** What is eased by the clouds, seconds after it was shown, the clouds giving now: see CLOUDS_EASE. */
    static float cloudEased(float shown, float now, float seconds) {
        return seconds > CLOUDS_RESTART ? now : shown + (now - shown) * (1f - (float) Math.exp(-Math.min(seconds, CLOUDS_STEP_MOST) / CLOUDS_EASE));
    }

    /**
     * How much cloud the develop pass grades the picture by: a clear sky's where all the eye looks
     * at is in the sun, the day's cover where all of it is in the clouds' shadows, and so the day's
     * cover on a day the clouds close over.
     */
    float coverSeen() {
        return COVER_CLEAR + (cover - COVER_CLEAR) * shadeSeen;
    }

    /** How far the clouds' shadow lies over the ground under a place of the map, 0 in a gap to 1 under a cloud, as the world's shader has it (PipelineShaders.CLOUD_SHADOWS). */
    float cloudShadow(float x, float y) {
        float lean = CLOUD_LEAD / Math.max(lightUp, 0.25f);
        float underX = (x + lightX * lean) / CLOUD_WIDTH + cloudsX, underY = (y + lightY * lean) / CLOUD_WIDTH + cloudsY;
        float body = cloudPatches(underX, underY) * 0.6f + cloudPatches(underX * 2.7f + 7f, underY * 2.7f + 7f) * 0.4f;
        return smooth(-CLOUD_EDGE, CLOUD_EDGE, body - (CLOUD_CLEARS - cover));
    }

    /**
     * How much of the ground the eye at (x, y) looks at, the flat way (forwardX, forwardY), lies in
     * the clouds' shadows: the shadow over a round set ahead of the eye, a cell of their noise from
     * its middle to its edge, weighed at its middle twice and at rings of places round it. One
     * shadow's edge crossing the eye does not regrade the whole picture, and a shaded field seen
     * from a gap is graded as more shade than the gap.
     */
    float shadeLooked(float x, float y, float forwardX, float forwardY) {
        float middleX = x + forwardX * SHADE_AHEAD, middleY = y + forwardY * SHADE_AHEAD;
        float sum = 2f * cloudShadow(middleX, middleY);
        for (int ring = 1; ring <= SHADE_RINGS; ring++) {
            float out = SHADE_REACH * ring / SHADE_RINGS;
            for (int i = 0; i < SHADE_ROUND; i++) {
                double angle = 2.0 * Math.PI * (i + 0.5 * ring) / SHADE_ROUND;
                sum += cloudShadow(middleX + out * (float) Math.cos(angle), middleY + out * (float) Math.sin(angle));
            }
        }
        return sum / (SHADE_RINGS * SHADE_ROUND + 2);
    }

    /** How far ahead of the eye, in tiles, the round shadeLooked weighs is centred, how far it reaches from its middle, and in how many rings of how many places. */
    static final float SHADE_AHEAD = 10f, SHADE_REACH = SkyState.CLOUD_WIDTH;
    static final int SHADE_RINGS = 2, SHADE_ROUND = 8;
    /** Smooth noise over cells, as the shader's cloudPatches. */
    static float cloudPatches(float x, float y) {
        float cellX = (float) Math.floor(x), cellY = (float) Math.floor(y);
        float fx = x - cellX, fy = y - cellY;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        return mix(mix(cloudGrain(cellX, cellY), cloudGrain(cellX + 1f, cellY), fx),
                mix(cloudGrain(cellX, cellY + 1f), cloudGrain(cellX + 1f, cellY + 1f), fx), fy);
    }

    /**
     * A cell's value, 0 to 1, as the shader's cloudGrain: a permutation of whole numbers under 289,
     * each step of which a float holds exactly. The shader's mod is not tested against this on a
     * graphics card; it may differ where a step lands on a multiple of 289 itself. cloudPermute
     * and mod289 are those of webgl-noise (https://github.com/stegu/webgl-noise, MIT, Copyright (C)
     * 2011 Ashima Arts and 2011-2016 Stefan Gustavson): see THIRD-PARTY-NOTICES.md.
     */
    static float cloudGrain(float cellX, float cellY) {
        return cloudPermute(cloudPermute(mod289(cellX)) + mod289(cellY)) / 289f;
    }

    private static float cloudPermute(float x) {
        return mod289((x * 34f + 1f) * x);
    }

    private static float mod289(float x) {
        return x - 289f * (float) Math.floor(x / 289f);
    }

    /** How the clouds' shadows lie, shared with the shader (PipelineShaders.CLOUD_SHADOWS): how many tiles a cell of their noise spans, how far along the sun the cloud casting one is, as tiles over the sun's height, the noise over which a place is shaded, at a cover of 0, and over how much of it the shadow's edge goes. */
    static final float CLOUD_WIDTH = 22f, CLOUD_LEAD = 30f, CLOUD_CLEARS = 1.12f, CLOUD_EDGE = 0.06f;
    /** How many cells of their noise the clouds' shadows cross for one of the sky's drift: they move with the clouds overhead. */
    static final float CLOUD_DRIFTS = 6f;
    /**
     * After how many cells the shadows' noise comes round again, both of its layers: the first
     * every 289 cells, the second, 2.7 times as fine, every 289 / 2.7, and ten of the one are
     * twenty-seven of the other. Their drift is wrapped there, where no eye can see it.
     */
    static final double CLOUD_REPEATS = 2890.0;

    /**
     * After how much drift the sky's clouds (Sky.CLOUDS) come round again: their noise repeats
     * every 1, and they read it at the drift times 0.3, 0.4, 0.6 and other tenths, all of which
     * come round at 10.
     */
    static final double SKY_REPEATS = 10.0;

    /** The sky's drift moved on by by: kept under SKY_REPEATS, in a double, which counts the smallest frame's drift. */
    static double skyDrifted(double drifted, double by) {
        double next = drifted + by;
        return next - SKY_REPEATS * Math.floor(next / SKY_REPEATS);
    }

    /**
     * The clouds' shadows' drift, in cells, moved on by the sky's drift by: kept under
     * CLOUD_REPEATS, in a double, which over that span still counts the smallest frame's drift.
     */
    static double cloudsDrifted(double drifted, double by) {
        double next = drifted + by * CLOUD_DRIFTS;
        return next - CLOUD_REPEATS * Math.floor(next / CLOUD_REPEATS);
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
