package com.cameraunlock.zomboid;

/** How far and how clearly the character sees in the first person picture: see Eyesight. */
final class EyesightTests {
    private EyesightTests() {}

    static void run() {
        Checks.run("clearAirHidesNothing", EyesightTests::clearAirHidesNothing);
        Checks.run("fullFogIsAWhiteOutBeyondAFewTiles", EyesightTests::fullFogIsAWhiteOutBeyondAFewTiles);
        Checks.run("lightFogIsAHaze", EyesightTests::lightFogIsAHaze);
        Checks.run("airHidesMoreTheFurtherAndTheThicker", EyesightTests::airHidesMoreTheFurtherAndTheThicker);
        Checks.run("rainAndSnowLeaveTheNearStreetPlain", EyesightTests::rainAndSnowLeaveTheNearStreetPlain);
        Checks.run("aViewNarrowsAsTheGameNarrowsItsCone", EyesightTests::aViewNarrowsAsTheGameNarrowsItsCone);
        Checks.run("whereNobodyCanBeFoughtZombiesAreWholeWithinTheGamesSight", EyesightTests::whereNobodyCanBeFoughtZombiesAreWholeWithinTheGamesSight);
        Checks.run("wherePlayersCanFightZombiesAreTheGamesAndTheSightStaysOn", EyesightTests::wherePlayersCanFightZombiesAreTheGamesAndTheSightStaysOn);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void clearAirHidesNothing() {
        check(Eyesight.thickness(0f, 0f, 0f) == 0f, "no fog, rain or snow is no air to hide anything");
        check(Eyesight.veiled(0f, 80f) == 0f, "and hides nothing at the end of sight");
    }

    private static void fullFogIsAWhiteOutBeyondAFewTiles() {
        float fog = Eyesight.thickness(1f, 0f, 0f);
        check(Eyesight.veiled(fog, 3.7f) < 0.2f, "a zombie 3.7 tiles off is plain in full fog, as in the game's own view");
        float half = Eyesight.veiled(fog, 5.6f);
        check(half > 0.35f && half < 0.6f, "one at 5.6 is half hidden: " + half);
        check(Eyesight.veiled(fog, 10f) > 0.94f, "one at ten is gone");
        check(Eyesight.veiled(fog, 16f) > 0.99f, "and nothing is left by sixteen");
    }

    private static void lightFogIsAHaze() {
        float fog = Eyesight.thickness(0.3f, 0f, 0f);
        check(Eyesight.veiled(fog, 10f) < 0.2f, "fog 0.3 leaves the street at ten tiles");
        float far = Eyesight.veiled(fog, 16f);
        check(far > 0.2f && far < 0.35f, "and hides a quarter or so at sixteen: " + far);
    }

    private static void airHidesMoreTheFurtherAndTheThicker() {
        float before = -1f;
        for (float range = 0f; range <= 80f; range += 0.5f) {
            float veiled = Eyesight.veiled(Eyesight.thickness(0.5f, 0f, 0f), range);
            check(veiled >= before && veiled >= 0f && veiled <= 1f, "fog hides more the further off at " + range);
            before = veiled;
        }
        check(Eyesight.veiled(Eyesight.thickness(0.3f, 0f, 0f), 10f) < Eyesight.veiled(Eyesight.thickness(0.6f, 0f, 0f), 10f), "and more the thicker it is");
    }

    private static void rainAndSnowLeaveTheNearStreetPlain() {
        check(Eyesight.veiled(Eyesight.thickness(0f, 1f, 0f), 20f) < 0.25f, "the heaviest rain leaves most of the street at twenty tiles");
        check(Eyesight.veiled(Eyesight.thickness(0f, 0f, 1f), 20f) < 0.4f, "and so does the heaviest snow");
        float rainFar = Eyesight.veiled(Eyesight.thickness(0f, 1f, 0f), 80f);
        check(rainFar > 0.5f && rainFar < 0.7f, "rain hides three fifths of what stands at the end of sight: " + rainFar);
        check(Eyesight.veiled(Eyesight.thickness(0f, 0f, 1f), 80f) > 0.85f, "snow nine tenths");
    }

    private static void aViewNarrowsAsTheGameNarrowsItsCone() {
        check(Eyesight.narrowed(false, 0f, false, 0, 0f, 0, 1f) == 0f, "a rested, sober, calm character with nothing over the eyes sees the widest");
        check(Math.abs(Eyesight.narrowed(false, 1f, false, 0, 0f, 0, 1f) - 72f / 126f) < 1e-5f, "full fatigue takes 72 of the cone's 126");
        check(Math.abs(Eyesight.narrowed(false, 1f, true, 0, 0f, 0, 1f) - 108f / 126f) < 1e-5f, "and 36 more at its height");
        check(Eyesight.narrowed(false, 0f, false, 1, 50f, 0, 1f) == 0f, "drink narrows nothing below the moodle's second level");
        check(Math.abs(Eyesight.narrowed(false, 0f, false, 2, 50f, 0, 1f) - 18f / 126f) < 1e-5f, "and 0.36 a point of intoxication from it");
        check(Eyesight.narrowed(false, 0f, false, 0, 0f, 3, 1f) == 0f, "panic narrows nothing below its fourth level");
        check(Math.abs(Eyesight.narrowed(false, 0f, false, 0, 0f, 4, 1f) - 36f / 126f) < 1e-5f, "and 36 at it");
        check(Eyesight.narrowed(false, 1f, true, 4, 100f, 4, 1f) == 1f, "everything at once is the narrowest the game allows");
        check(Math.abs(Eyesight.narrowed(false, 0f, false, 0, 0f, 0, 0.75f) - 36f / 126f) < 1e-5f, "a mask that takes a quarter of the cone narrows it by 36");
        check(Eyesight.narrowed(false, 0f, false, 0, 0f, 0, 1.5f) == 0f, "what widens the cone does not widen the picture");
        check(Eyesight.narrowed(true, 1f, false, 0, 0f, 0, 1f) == 0f, "at the wheel tiredness narrows nothing until it is at its height");
        check(Math.abs(Eyesight.narrowed(true, 1f, true, 0, 0f, 4, 1f) - 72f / 306f) < 1e-5f, "then 36 of the 306 a vehicle's cone has to lose, and panic 36");
    }

    private static void whereNobodyCanBeFoughtZombiesAreWholeWithinTheGamesSight() {
        check(Eyesight.shownWhole(false, true, 40f * 40f, 43.3f), "alone or on a server without PvP, a zombie 40 tiles off in full fog by day is drawn whole");
        check(!Eyesight.shownWhole(false, true, 50f * 50f, 43.3f), "one at 50, past the game's sight, is left to the game");
        check(Eyesight.shownWhole(false, false, 50f * 50f, 43.3f), "with the development switch taking the sight out, it is drawn whole as before");
        check(!Eyesight.on(true, false), "the switch takes the sight out where nobody can be fought");
        check(Eyesight.on(false, false), "and without it the sight is in");
    }

    private static void wherePlayersCanFightZombiesAreTheGamesAndTheSightStaysOn() {
        check(!Eyesight.shownWhole(true, true, 4f, 43.3f), "where players can fight, even a zombie two tiles off is drawn only as the game shows it");
        check(Eyesight.on(true, true), "and the development switch cannot take the sight out");
    }
}
