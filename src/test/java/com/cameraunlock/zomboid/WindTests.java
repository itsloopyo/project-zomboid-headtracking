package com.cameraunlock.zomboid;

/** Tests of the wind as what grows feels it: whatever the game's wind does, nothing jumps. */
final class WindTests {
    private static final float FRAME = 1f / 60f;

    private WindTests() {}

    static void run() {
        Checks.run("pushFollowsTheGamesWind", WindTests::pushFollowsTheGamesWind);
        Checks.run("windThatTurnsRoundAtOnceMovesTheGrassNoFasterThanItBlows", WindTests::windThatTurnsRoundAtOnceMovesTheGrassNoFasterThanItBlows);
        Checks.run("pushComesRoundToANewWindInAFewSeconds", WindTests::pushComesRoundToANewWindInAFewSeconds);
        Checks.run("gustsAreCarriedTheWayTheWindBlows", WindTests::gustsAreCarriedTheWayTheWindBlows);
        Checks.run("travelAndClockStayWithinWhatTheyRepeatOver", WindTests::travelAndClockStayWithinWhatTheyRepeatOver);
        Checks.run("rainAndSnowAreCarriedAtTheAirsSpeedAndNeverJump", WindTests::rainAndSnowAreCarriedAtTheAirsSpeedAndNeverJump);
        Checks.run("dropsAndFlakesHaveFallenAWholeNumberOfBoxesWhenTheirClocksStartAgain", WindTests::dropsAndFlakesHaveFallenAWholeNumberOfBoxesWhenTheirClocksStartAgain);
    }

    private static void rainAndSnowAreCarriedAtTheAirsSpeedAndNeverJump() {
        check(Wind.speed(0f) == 0f && Math.abs(Wind.speed(1f) - 33.3f) < 0.01f && Wind.speed(0.5f) > 16f && Wind.speed(0.5f) < 17f,
                "the air moves at the game's own speeds: nothing in a calm, 120 kilometres an hour at the top");
        Wind wind = new Wind();
        for (int frame = 0; frame < 60; frame++) {
            wind.blow(1f, 0f, 5f, FRAME);
        }
        check(Math.abs(wind.airX - 5f) < 1e-3f && Math.abs(wind.airY) < 1e-3f, "the air moves the way the wind blows, at its speed");
        check(wind.carriedX > 4.8f && wind.carriedX < 5.2f && Math.abs(wind.carriedY) < 1e-3f, "a second of it carries what falls five tiles: " + wind.carriedX);
        // The game's wind swings right round and goes from a calm to a gale and back, every other frame.
        for (int frame = 0; frame < 2000; frame++) {
            float carriedX = wind.carriedX, carriedY = wind.carriedY, airX = wind.airX, airY = wind.airY;
            boolean back = frame % 2 == 0;
            wind.blow(back ? -1f : 0.2f, back ? 0.5f : -1f, back ? 33f : 0f, FRAME);
            float moved = (float) Math.hypot(apart(wind.carriedX, carriedX, Wind.CARRIED_WRAP), apart(wind.carriedY, carriedY, Wind.CARRIED_WRAP));
            check(moved <= 34f * FRAME, "rain and snow were carried " + moved + " tiles in a frame: faster than any air moves");
            check(Math.hypot(wind.airX - airX, wind.airY - airY) < 40f * FRAME / Wind.SETTLES, "the air's speed eases to a new wind and does not jump to it");
            check(wind.carriedX >= 0f && wind.carriedX < Wind.CARRIED_WRAP && wind.carriedY >= 0f && wind.carriedY < Wind.CARRIED_WRAP,
                    "how far it has carried them stays within the box they are drawn in");
        }
    }

    private static void dropsAndFlakesHaveFallenAWholeNumberOfBoxesWhenTheirClocksStartAgain() {
        // The rain and snow shaders: a drop falls 0.625 * (18..25) tiles a second and a flake 0.15 * (6..12), through a box 12 high.
        for (int step = 18; step <= 25; step++) {
            double boxes = 0.625 * step * Wind.RAIN_CLOCK / 12.0;
            check(Math.abs(boxes - Math.rint(boxes)) < 1e-4, "a drop at speed " + step + " has fallen " + boxes + " boxes when the rain's clock starts again");
        }
        for (int step = 6; step <= 12; step++) {
            double boxes = 0.15 * step * Wind.SNOW_CLOCK / 12.0;
            check(Math.abs(boxes - Math.rint(boxes)) < 1e-4, "a flake at speed " + step + " has fallen " + boxes + " boxes when the snow's clock starts again");
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void pushFollowsTheGamesWind() {
        check(Wind.push(0f) < 0.05f, "in still air next to nothing pushes the grass: " + Wind.push(0f));
        check(Wind.push(0.08f) < 0.15f, "light air of 10 kilometres an hour only stirs it: " + Wind.push(0.08f));
        check(Wind.push(0.33f) > 0.4f && Wind.push(0.33f) < 0.7f, "a fresh breeze of 40 leans it part of the way: " + Wind.push(0.33f));
        check(Wind.push(0.7f) > 1.2f, "a gale lays it over: " + Wind.push(0.7f));
        check(Wind.push(-1f) == Wind.push(0f) && Wind.push(3f) == Wind.push(1f), "an intensity outside the game's range is its nearest end");
    }

    /** How far apart two places on a ring of that length are. */
    private static float apart(float a, float b, float ring) {
        float d = Math.abs(a - b) % ring;
        return Math.min(d, ring - d);
    }

    private static void windThatTurnsRoundAtOnceMovesTheGrassNoFasterThanItBlows() {
        Wind wind = new Wind();
        for (int frame = 0; frame < 600; frame++) {
            wind.blow(1.2f, 0f, 6f, FRAME);
        }
        // The game's wind swings right round and trebles, then swings back every other frame.
        for (int frame = 0; frame < 600; frame++) {
            float pushX = wind.pushX, pushY = wind.pushY, travelX = wind.travelX, travelY = wind.travelY;
            boolean back = frame % 2 == 0;
            wind.blow(back ? -1.75f : 0.4f, back ? 0.3f : -1.1f, 6f, FRAME);
            float turned = (float) Math.hypot(wind.pushX - pushX, wind.pushY - pushY);
            check(turned < 3.5f * FRAME / Wind.SETTLES * 1.01f, "the push changed by " + turned + " in a frame of a wind that had swung round");
            float moved = (float) Math.hypot(apart(wind.travelX, travelX, Wind.TRAVEL_WRAP), apart(wind.travelY, travelY, Wind.TRAVEL_WRAP));
            check(moved < 0.4f * FRAME, "the gusts were carried " + moved + " tiles in a frame: faster than any wind blows them");
        }
    }

    private static void pushComesRoundToANewWindInAFewSeconds() {
        Wind wind = new Wind();
        wind.blow(1f, 0f, 6f, FRAME);
        check(wind.pushX == 1f && wind.pushY == 0f, "the first wind there is, is the push at once: there is nothing to ease from");
        for (int frame = 0; frame < 60 * 12; frame++) {
            wind.blow(0f, 1f, 6f, FRAME);
        }
        check(Math.abs(wind.pushX) < 0.03f && Math.abs(wind.pushY - 1f) < 0.03f, "twelve seconds on the push is the new wind: " + wind.pushX + ", " + wind.pushY);
    }

    private static void gustsAreCarriedTheWayTheWindBlows() {
        Wind wind = new Wind();
        for (int frame = 0; frame < 60; frame++) {
            wind.blow(0f, 1f, 6f, FRAME);
        }
        check(Math.abs(wind.travelX) < 1e-3f && wind.travelY > 0.15f && wind.travelY < 0.25f,
                "a second of wind of 1 along y carries the gusts about a fifth of a tile along y: " + wind.travelX + ", " + wind.travelY);
        Wind still = new Wind();
        still.blow(0f, 0f, 6f, 1f);
        check(still.travelX == 0f && still.travelY == 0f, "with no wind the gusts stay where they are");
    }

    private static void travelAndClockStayWithinWhatTheyRepeatOver() {
        Wind wind = new Wind();
        for (int frame = 0; frame < 60 * 600; frame++) {
            wind.blow(-1.2f, -0.9f, 6f, FRAME);
            if (wind.travelX < 0f || wind.travelX >= Wind.TRAVEL_WRAP || wind.travelY < 0f || wind.travelY >= Wind.TRAVEL_WRAP || wind.flutter < 0f
                    || wind.flutter >= Wind.FLUTTER_WRAP) {
                throw new AssertionError("after " + frame + " frames the wind's travel or clock is out of bounds: " + wind.travelX + ", " + wind.travelY + ", " + wind.flutter);
            }
        }
    }
}
