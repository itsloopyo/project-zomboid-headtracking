package com.cameraunlock.zomboid;

/** How far and how clearly the character sees in the first person picture: see Eyesight. */
final class EyesightTests {
    private EyesightTests() {}

    static void run() {
        Checks.run("clearAirHidesNothing", EyesightTests::clearAirHidesNothing);
        Checks.run("fullFogIsAWhiteOutBeyondAFewTiles", EyesightTests::fullFogIsAWhiteOutBeyondAFewTiles);
        Checks.run("lightFogIsAHaze", EyesightTests::lightFogIsAHaze);
        Checks.run("wherePlayersCanFightFogIsAsThickAsInTheGamesOwnView", EyesightTests::wherePlayersCanFightFogIsAsThickAsInTheGamesOwnView);
        Checks.run("airHidesMoreTheFurtherAndTheThicker", EyesightTests::airHidesMoreTheFurtherAndTheThicker);
        Checks.run("rainAndSnowLeaveTheNearStreetPlain", EyesightTests::rainAndSnowLeaveTheNearStreetPlain);
        Checks.run("aViewNarrowsAsTheGameNarrowsItsCone", EyesightTests::aViewNarrowsAsTheGameNarrowsItsCone);
        Checks.run("whereNobodyCanBeFoughtZombiesAreWholeWithinTheGamesSight", EyesightTests::whereNobodyCanBeFoughtZombiesAreWholeWithinTheGamesSight);
        Checks.run("wherePlayersCanFightZombiesAreTheGamesAndTheSightStaysOn", EyesightTests::wherePlayersCanFightZombiesAreTheGamesAndTheSightStaysOn);
        Checks.run("theSwitchTakesTheSightOutOnlyWhereNobodyCanBeFought", EyesightTests::theSwitchTakesTheSightOutOnlyWhereNobodyCanBeFought);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void clearAirHidesNothing() {
        check(Eyesight.thickness(0f, 0f, 0f, 0f, false) == 0f, "no fog, rain or snow is no air to hide anything");
        check(Eyesight.veiled(0f, 80f) == 0f, "and hides nothing at the end of sight");
    }

    private static void fullFogIsAWhiteOutBeyondAFewTiles() {
        float fog = Eyesight.thickness(1f, 0f, 0f, 0f, false);
        check(Eyesight.veiled(fog, 3.7f) < 0.2f, "a zombie 3.7 tiles off is plain in full fog, as in the game's own view");
        float half = Eyesight.veiled(fog, 5.6f);
        check(half > 0.2f && half < 0.35f, "one at 5.6 is a quarter hidden, thinner than the game's view from above: " + half);
        check(Eyesight.veiled(fog, 10f) > 0.7f, "one at ten is mostly gone");
        check(Eyesight.veiled(fog, 16f) > 0.95f, "and next to nothing is left by sixteen");
    }

    private static void lightFogIsAHaze() {
        float fog = Eyesight.thickness(0.3f, 0f, 0f, 0f, false);
        check(Eyesight.veiled(fog, 10f) < 0.2f, "fog 0.3 leaves the street at ten tiles");
        float far = Eyesight.veiled(fog, 16f);
        check(far > 0.05f && far < 0.15f, "and hides a tenth or so at sixteen: " + far);
    }

    /** The owner, 2026-10-10: "pvp should be fair". Nobody where players can fight sees further through fog than the game's own view lets everyone. */
    private static void wherePlayersCanFightFogIsAsThickAsInTheGamesOwnView() {
        float fog = Eyesight.thickness(1f, 0f, 0f, 0f, true);
        check(Eyesight.veiled(fog, 3.7f) < 0.2f, "a zombie 3.7 tiles off is plain in full fog, as in the game's own view");
        float half = Eyesight.veiled(fog, 5.6f);
        check(half > 0.35f && half < 0.6f, "one at 5.6 is half hidden, as in the game's own view: " + half);
        check(Eyesight.veiled(fog, 10f) > 0.94f, "one at ten is gone");
        float light = Eyesight.veiled(Eyesight.thickness(0.3f, 0f, 0f, 0f, true), 16f);
        check(light > 0.2f && light < 0.35f, "fog 0.3 hides a quarter or so at sixteen: " + light);
        for (float amount = 0.1f; amount <= 1f; amount += 0.1f) {
            for (float range = 6f; range <= 80f; range += 2f) {
                check(Eyesight.veiled(Eyesight.thickness(amount, 0f, 0f, 0f, true), range) >= Eyesight.veiled(Eyesight.thickness(amount, 0f, 0f, 0f, false), range),
                        "and fog " + amount + " hides no less there at " + range + " tiles than alone");
            }
        }
        check(Eyesight.thickness(0f, 1f, 1f, 0.5f, true) == Eyesight.thickness(0f, 1f, 1f, 0.5f, false), "rain and snow are the same either way");
    }

    private static void airHidesMoreTheFurtherAndTheThicker() {
        float before = -1f;
        for (float range = 0f; range <= 80f; range += 0.5f) {
            float veiled = Eyesight.veiled(Eyesight.thickness(0.5f, 0f, 0f, 0f, false), range);
            check(veiled >= before && veiled >= 0f && veiled <= 1f, "fog hides more the further off at " + range);
            before = veiled;
        }
        check(Eyesight.veiled(Eyesight.thickness(0.3f, 0f, 0f, 0f, false), 10f) < Eyesight.veiled(Eyesight.thickness(0.6f, 0f, 0f, 0f, false), 10f), "and more the thicker it is");
    }

    private static void rainAndSnowLeaveTheNearStreetPlain() {
        check(Eyesight.veiled(Eyesight.thickness(0f, 1f, 0f, 0f, false), 20f) < 0.25f, "the heaviest rain leaves most of the street at twenty tiles");
        check(Eyesight.veiled(Eyesight.thickness(0f, 0f, 1f, 0f, false), 20f) < 0.4f, "and so does the heaviest snow");
        float rainFar = Eyesight.veiled(Eyesight.thickness(0f, 1f, 0f, 0f, false), 80f);
        check(rainFar > 0.5f && rainFar < 0.7f, "rain hides three fifths of what stands at the end of sight: " + rainFar);
        float still = Eyesight.veiled(Eyesight.thickness(0f, 0f, 1f, 0f, false), 80f);
        check(still > 0.3f && still < 0.4f, "the heaviest snow in still air hides a third of what stands at the end of sight: " + still);
        float blizzard = Eyesight.veiled(Eyesight.thickness(0f, 0f, 1f, 0.9f, false), 80f);
        check(blizzard > 0.48f && blizzard < 0.6f, "a blizzard about half: " + blizzard);
        float moderate = Eyesight.veiled(Eyesight.thickness(0f, 0f, 0.5f, 0f, false), 80f);
        check(moderate < 0.15f, "a fall half as heavy hides little, an eighth: " + moderate);
        check(Eyesight.veiled(Eyesight.thickness(0f, 0f, 0.25f, 0.3f, false), 80f) < 0.05f, "light snow next to nothing");
        check(Eyesight.thickness(0f, 0f, 0f, 1f, false) == 0f, "wind alone hides nothing");
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
        check(Eyesight.shownWhole(true, 40f * 40f, 43.3f), "a zombie 40 tiles off in full fog by day is drawn whole");
        check(!Eyesight.shownWhole(true, 50f * 50f, 43.3f), "one at 50, past the game's sight, is left to the game");
        check(Eyesight.shownWhole(false, 50f * 50f, 43.3f), "with the development switch taking the sight out, it is drawn whole as before");
        check(!Eyesight.on(true, false), "the switch takes the sight out where nobody can be fought");
        check(Eyesight.on(false, false), "and without it the sight is in");
    }

    private static void wherePlayersCanFightZombiesAreTheGamesAndTheSightStaysOn() {
        check(Eyesight.on(true, true), "where players can fight the development switch cannot take the sight out");
    }

    /** As the frame asks it, of the server the game is joined to. */
    private static void theSwitchTakesTheSightOutOnlyWhereNobodyCanBeFought() {
        boolean was = Eyesight.without;
        try {
            Eyesight.without = false;
            for (Servers.Where where : Servers.Where.values()) {
                Servers.in(where, () -> check(Eyesight.on(), "the character's sight is on " + where + " with the switch left alone"));
            }
            Eyesight.without = true;
            Servers.in(Servers.Where.ALONE, () -> check(!Eyesight.on(), "alone the development switch takes it out"));
            Servers.in(Servers.Where.FRIENDLY, () -> check(!Eyesight.on(), "and on a server where players cannot hurt each other"));
            Servers.in(Servers.Where.CONTESTED, () -> check(Eyesight.on(), "and not where they can"));
        } finally {
            Eyesight.without = was;
        }
    }
}
