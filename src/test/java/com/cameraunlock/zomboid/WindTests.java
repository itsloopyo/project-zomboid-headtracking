package com.cameraunlock.zomboid;

/** Tests of the wind as what grows feels it: whatever the game's wind does, nothing jumps. */
final class WindTests {
    private static final float FRAME = 1f / 60f;

    private WindTests() {}

    static void run() {
        pushFollowsTheGamesWind();
        windThatTurnsRoundAtOnceMovesTheGrassNoFasterThanItBlows();
        pushComesRoundToANewWindInAFewSeconds();
        gustsAreCarriedTheWayTheWindBlows();
        travelAndClockStayWithinWhatTheyRepeatOver();
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
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
            wind.blow(1.2f, 0f, FRAME);
        }
        // The game's wind swings right round and trebles, then swings back every other frame.
        for (int frame = 0; frame < 600; frame++) {
            float pushX = wind.pushX, pushY = wind.pushY, travelX = wind.travelX, travelY = wind.travelY;
            boolean back = frame % 2 == 0;
            wind.blow(back ? -1.75f : 0.4f, back ? 0.3f : -1.1f, FRAME);
            float turned = (float) Math.hypot(wind.pushX - pushX, wind.pushY - pushY);
            check(turned < 3.5f * FRAME / Wind.SETTLES * 1.01f, "the push changed by " + turned + " in a frame of a wind that had swung round");
            float moved = (float) Math.hypot(apart(wind.travelX, travelX, Wind.TRAVEL_WRAP), apart(wind.travelY, travelY, Wind.TRAVEL_WRAP));
            check(moved < 4f * FRAME, "the gusts were carried " + moved + " tiles in a frame: faster than any wind blows them");
        }
    }

    private static void pushComesRoundToANewWindInAFewSeconds() {
        Wind wind = new Wind();
        wind.blow(1f, 0f, FRAME);
        check(wind.pushX == 1f && wind.pushY == 0f, "the first wind there is, is the push at once: there is nothing to ease from");
        for (int frame = 0; frame < 60 * 12; frame++) {
            wind.blow(0f, 1f, FRAME);
        }
        check(Math.abs(wind.pushX) < 0.03f && Math.abs(wind.pushY - 1f) < 0.03f, "twelve seconds on the push is the new wind: " + wind.pushX + ", " + wind.pushY);
    }

    private static void gustsAreCarriedTheWayTheWindBlows() {
        Wind wind = new Wind();
        for (int frame = 0; frame < 60; frame++) {
            wind.blow(0f, 1f, FRAME);
        }
        check(Math.abs(wind.travelX) < 1e-3f && wind.travelY > 1.5f && wind.travelY < 2.5f,
                "a second of wind of 1 along y carries the gusts about two tiles along y: " + wind.travelX + ", " + wind.travelY);
        Wind still = new Wind();
        still.blow(0f, 0f, 1f);
        check(still.travelX == 0f && still.travelY == 0f, "with no wind the gusts stay where they are");
    }

    private static void travelAndClockStayWithinWhatTheyRepeatOver() {
        Wind wind = new Wind();
        for (int frame = 0; frame < 60 * 600; frame++) {
            wind.blow(-1.2f, -0.9f, FRAME);
            if (wind.travelX < 0f || wind.travelX >= Wind.TRAVEL_WRAP || wind.travelY < 0f || wind.travelY >= Wind.TRAVEL_WRAP || wind.flutter < 0f
                    || wind.flutter >= Wind.FLUTTER_WRAP) {
                throw new AssertionError("after " + frame + " frames the wind's travel or clock is out of bounds: " + wind.travelX + ", " + wind.travelY + ", " + wind.flutter);
            }
        }
    }
}
