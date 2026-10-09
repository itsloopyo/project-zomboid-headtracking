package com.cameraunlock.zomboid;

final class QualityTests {
    private QualityTests() {}

    private static final long SECOND = 1_000_000_000L;

    static void run() {
        Checks.run("aLevelAskedForIsKept", QualityTests::aLevelAskedForIsKept);
        Checks.run("automaticStepsDownWhileThePictureIsSlow", QualityTests::automaticStepsDownWhileThePictureIsSlow);
        Checks.run("automaticStaysWhereThePictureKeepsUp", QualityTests::automaticStaysWhereThePictureKeepsUp);
        Checks.run("twoViewsAreCountedAsOnePicture", QualityTests::twoViewsAreCountedAsOnePicture);
        Checks.run("theKeyGoesLowMediumHighCustomAndRound", QualityTests::theKeyGoesLowMediumHighCustomAndRound);
        Checks.run("customDrawsWhatEachPresetDrewFromItsRows", QualityTests::customDrawsWhatEachPresetDrewFromItsRows);
        Checks.run("eachRowOfCustomReachesItsOwnAnswer", QualityTests::eachRowOfCustomReachesItsOwnAnswer);
        Checks.run("thePresetsLeaveCustomAsItWas", QualityTests::thePresetsLeaveCustomAsItWas);
        Quality.custom(Quality.HIGH_PICTURE);
        Quality.set(Quality.HIGH);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** Counts frames at a steady rate for a time, and returns the time it ended at. */
    private static long frames(long from, float rate, float seconds) {
        long step = (long) (SECOND / rate);
        long now = from;
        while (now - from < (long) (seconds * SECOND)) {
            now += step;
            Quality.frame(0, now);
        }
        return now;
    }

    private static void aLevelAskedForIsKept() {
        Quality.set(Quality.HIGH);
        check(Quality.scale() == 1f && Quality.sight() == 80 && Quality.shadowSize() == 4096 && Quality.shadedAndLit(),
                "high is the whole picture");
        frames(SECOND, 10f, 20f);
        check(Quality.scale() == 1f, "and stays the whole picture however slowly it is drawn");
        Quality.set(Quality.MEDIUM);
        check(Quality.scale() == 0.8f && Quality.sight() == 64 && !Quality.shadedAndLit() && !Quality.relief(),
                "medium is fewer pixels, shorter sight and no shading of corners, lit air, mirrors or depth in surfaces");
        Quality.set(Quality.LOW);
        check(Quality.scale() < 0.7f && Quality.sight() == 48 && Quality.shadowSize() == 2048 && !Quality.shadedAndLit() && !Quality.relief(),
                "low is fewer pixels, shorter sight, smaller maps and no shading of corners");
    }

    private static void automaticStepsDownWhileThePictureIsSlow() {
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        check(Quality.sight() == 80, "automatic begins with the whole picture");
        long now = frames(SECOND, 30f, 2.5f);
        check(Quality.sight() == 80, "and judges nothing in under three seconds");
        now = frames(now, 30f, 1f);
        check(Quality.sight() == 64, "three seconds at thirty frames a second take it down a level");
        now = frames(now, 30f, 4f);
        check(Quality.sight() == 64, "the next count begins once the new level has settled");
        now = frames(now, 30f, 1.5f);
        check(Quality.sight() == 48, "and still slow, it goes down again");
        frames(now, 5f, 30f);
        check(Quality.sight() == 48, "below which there is nothing");
    }

    private static void twoViewsAreCountedAsOnePicture() {
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        long step = SECOND / 30, now = SECOND;
        for (int frame = 0; frame < 30 * 4; frame++) {
            now += step;
            Quality.frame(0, now);
            Quality.frame(1, now + 1000);
        }
        check(Quality.sight() == 64, "two views drawn thirty times a second are thirty frames a second, and take it down a level");
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        for (int frame = 0; frame < 30 * 4; frame++) {
            now += step;
            Quality.rest(0);
            Quality.frame(1, now);
        }
        check(Quality.sight() == 64, "with the first view off the second's frames are the ones counted, and the first's being off does not begin the count again");
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        Quality.rest(1);
        now = frames(now + 60 * SECOND, 60f, 10f);
        check(Quality.sight() == 80, "and with both off the count begins again");
        Quality.rest(0);
    }

    private static void automaticStaysWhereThePictureKeepsUp() {
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        long now = frames(SECOND, 60f, 30f);
        check(Quality.sight() == 80, "at sixty frames a second the picture stays whole");
        Quality.rest(0);
        now = frames(now + 60 * SECOND, 60f, 10f);
        check(Quality.sight() == 80, "and a minute with the view off is not a minute of slow frames");
        now = frames(now, 30f, 3.2f);
        check(Quality.sight() == 64, "slow frames later on are still acted on");
        frames(now, 120f, 60f);
        check(Quality.sight() == 64, "and it does not go back up in the session");
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        now = frames(now + 60 * SECOND, 100f, 2f);
        now = frames(now + 6 * SECOND, 100f, 2f);
        check(Quality.sight() == 80, "one frame of six seconds among fast ones is a hitch and takes nothing down");
    }

    private static void theKeyGoesLowMediumHighCustomAndRound() {
        Quality.set(Quality.LOW);
        check(Quality.next() == Quality.MEDIUM, "the key goes from Low to Medium");
        Quality.set(Quality.MEDIUM);
        check(Quality.next() == Quality.HIGH, "from Medium to High");
        Quality.set(Quality.HIGH);
        check(Quality.next() == Quality.CUSTOM, "from High to Custom");
        Quality.set(Quality.CUSTOM);
        check(Quality.next() == Quality.LOW && Quality.name().equals("Custom"), "and from Custom round to Low");
        Quality.set(Quality.AUTOMATIC);
        check(Quality.next() == Quality.LOW, "from Automatic, which only the file sets, to Low");
        check(Quality.words().equals("Automatic,Low,Medium,High,Custom"), "and the row's words have Custom last: " + Quality.words());
    }

    /** Every answer a graphics mode gives, as one line: the draw distance on foot and ahead of a vehicle among them. */
    private static String answers() {
        return Quality.plain() + " " + Quality.gamesPlants() + " " + Quality.scale() + " " + Quality.shadowSize() + " " + Quality.sight() + " "
                + reaches() + " " + Quality.glassRain() + " " + Quality.shadedAndLit() + " " + Quality.reflections() + " " + Quality.surfacesMirror() + " "
                + Quality.smallerPictures() + " " + Quality.crownsToSight() + " " + Quality.relief() + " " + Quality.sightBehind();
    }

    /**
     * How far people and props are drawn now, beside a vehicle and ahead of it, from a viewer
     * riding one that faces along x: the furthest whole tile, or "all" where nothing within 200
     * is left out.
     */
    static String reaches() {
        DrawDistance reach = new DrawDistance();
        reach.take(0f, 0f, 1f, 0f, false);
        return furthest(reach, 0f, 1f) + "/" + furthest(reach, 1f, 0f);
    }

    private static String furthest(DrawDistance reach, float wayX, float wayY) {
        if (reach.draws(wayX * 200f, wayY * 200f)) {
            return "all";
        }
        int tiles = 0;
        while (reach.draws(wayX * (tiles + 1), wayY * (tiles + 1))) {
            tiles++;
        }
        return Integer.toString(tiles);
    }

    private static void customDrawsWhatEachPresetDrewFromItsRows() {
        String[] expected = {
            "true false 0.62 2048 48 all/all 0 false true false true false false 48",
            "false true 0.8 2048 64 all/all 1 false true false true false false 48",
            "false false 1.0 4096 80 all/all 2 true true true false true true 48",
        };
        for (int mode = Quality.LOW; mode <= Quality.HIGH; mode++) {
            Quality.set(mode);
            String preset = answers();
            check(preset.equals(expected[mode - Quality.LOW]), Quality.name() + " draws as it always has: " + preset);
            Quality.custom(Config.picture(Config.values(Quality.preset(mode))));
            Quality.set(Quality.CUSTOM);
            check(answers().equals(preset), "Custom with the rows " + Quality.preset(mode) + " written as the options screen holds them draws as "
                    + Quality.name() + " did: " + answers());
        }
    }

    /** Each part of Custom's picture changed alone, from High's, changes the one answer that part is and no other. */
    private static void eachRowOfCustomReachesItsOwnAnswer() {
        Quality.custom(Quality.HIGH_PICTURE);
        Quality.set(Quality.CUSTOM);
        String high = answers();
        double[] changed = {1, 1, 0.7, 0, 40, 30, 50, 0, 0, 0, 0, 0, 0};
        String[] answer = {
            "true false 1.0 4096 80 all/all 2 true true true false true true 48",
            "false true 1.0 4096 80 all/all 2 true true true false true true 48",
            "false false 0.7 4096 80 all/all 2 true true true false true true 48",
            "false false 1.0 2048 80 all/all 2 true true true false true true 48",
            "false false 1.0 4096 40 all/all 2 true true true false true true 48",
            "false false 1.0 4096 80 30/all 2 true true true false true true 48",
            "false false 1.0 4096 80 all/50 2 true true true false true true 48",
            "false false 1.0 4096 80 all/all 0 true true true false true true 48",
            "false false 1.0 4096 80 all/all 2 false true false false true true 48",
            "false false 1.0 4096 80 all/all 2 true false false false true true 48",
            "false false 1.0 4096 80 all/all 2 true true true true true true 48",
            "false false 1.0 4096 80 all/all 2 true true true false false true 48",
            "false false 1.0 4096 80 all/all 2 true true true false true false 48",
        };
        for (int part = 0; part < Config.GRAPHICS.length; part++) {
            double[] values = Config.values(Quality.HIGH_PICTURE);
            values[part] = changed[part];
            Quality.custom(Config.picture(values));
            check(answers().equals(answer[part]), Config.GRAPHICS[part] + " in Custom is its own answer and no other's: " + answers());
            Quality.custom(Quality.HIGH_PICTURE);
            check(answers().equals(high), "and back, it is High's again");
        }
    }

    private static void thePresetsLeaveCustomAsItWas() {
        double[] values = Config.values(Quality.MEDIUM_PICTURE);
        values[4] = 56;
        Quality.Picture mine = Config.picture(values);
        Quality.set(Quality.CUSTOM);
        Quality.custom(mine);
        check(Quality.sight() == 56 && Quality.drawn().equals(mine), "Custom draws the rows given it");
        Quality.set(Quality.LOW);
        check(Quality.sight() == 48 && Quality.plain(), "Low draws Low's picture");
        Quality.set(Quality.MEDIUM);
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        check(Quality.custom().equals(mine), "and Low, Medium, High and Automatic leave Custom's rows as they were");
        Quality.set(Quality.CUSTOM);
        check(Quality.sight() == 56 && Quality.gamesPlants() && Quality.drawn().equals(mine), "so back in Custom it is the player's own again");
        Quality.set(Quality.HIGH);
        Quality.custom(Quality.HIGH_PICTURE);
        check(Quality.sight() == 80, "and rows given while another mode is drawn are kept for Custom and draw nothing yet");
        Quality.custom(mine);
        check(Quality.sight() == 80 && Quality.custom().equals(mine), "in either direction");
    }
}
