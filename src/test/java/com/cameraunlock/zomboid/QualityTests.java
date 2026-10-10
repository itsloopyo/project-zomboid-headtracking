package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.List;

final class QualityTests {
    private QualityTests() {}

    private static final long SECOND = 1_000_000_000L;

    static void run() {
        Checks.run("aLevelAskedForIsKept", QualityTests::aLevelAskedForIsKept);
        Checks.run("automaticStepsDownWhileThePictureIsSlow", QualityTests::automaticStepsDownWhileThePictureIsSlow);
        Checks.run("automaticStaysWhereThePictureKeepsUp", QualityTests::automaticStaysWhereThePictureKeepsUp);
        Checks.run("automaticStepsBackUpWithRoomToSpare", QualityTests::automaticStepsBackUpWithRoomToSpare);
        Checks.run("aSceneSlowAtOneLevelAndFastAtTheNextSettles", QualityTests::aSceneSlowAtOneLevelAndFastAtTheNextSettles);
        Checks.run("automaticsFloorIsTheStockLookAndItComesBackUp", QualityTests::automaticsFloorIsTheStockLookAndItComesBackUp);
        Checks.run("theGamesFrameLimitIsNotASlowPicture", QualityTests::theGamesFrameLimitIsNotASlowPicture);
        Checks.run("aModeThePlayerChoseIsNeverStepped", QualityTests::aModeThePlayerChoseIsNeverStepped);
        Checks.run("twoViewsAreCountedAsOnePicture", QualityTests::twoViewsAreCountedAsOnePicture);
        Checks.run("theKeyGoesAutomaticLowMediumHighCustomAndRound", QualityTests::theKeyGoesAutomaticLowMediumHighCustomAndRound);
        Checks.run("customDrawsWhatEachPresetDrewFromItsRows", QualityTests::customDrawsWhatEachPresetDrewFromItsRows);
        Checks.run("eachRowOfCustomReachesItsOwnAnswer", QualityTests::eachRowOfCustomReachesItsOwnAnswer);
        Checks.run("thePresetsLeaveCustomAsItWas", QualityTests::thePresetsLeaveCustomAsItWas);
        Quality.custom(Quality.HIGH_PICTURE);
        Quality.set(Quality.HIGH);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** The frame rate the game is held to in the frames counted, 0 for none. */
    private static float limit;

    /** Counts frames at a steady rate for a time, and returns the time it ended at. */
    private static long frames(long from, float rate, float seconds) {
        long step = (long) (SECOND / rate);
        long now = from;
        while (now - from < (long) (seconds * SECOND)) {
            now += step;
            Quality.frame(0, now, limit);
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
        check(Quality.sight() == 48 && !Quality.plain() && Quality.name().equals("Automatic (Low)"),
                "and still slow, it goes down again, to Low's cost with what the mod made: " + Quality.name());
        now = frames(now, 30f, 5.5f);
        check(Quality.plain() && Quality.drawn().equals(Quality.LOW_PICTURE) && Quality.name().equals("Automatic (Stock)"),
                "and still slow, to the Low mode's own look: " + Quality.name());
        frames(now, 5f, 30f);
        check(Quality.plain() && Quality.name().equals("Automatic (Stock)"), "below which there is nothing");
    }

    private static void twoViewsAreCountedAsOnePicture() {
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        long step = SECOND / 30, now = SECOND;
        for (int frame = 0; frame < 30 * 4; frame++) {
            now += step;
            Quality.frame(0, now, 0f);
            Quality.frame(1, now + 1000, 0f);
        }
        check(Quality.sight() == 64, "two views drawn thirty times a second are thirty frames a second, and take it down a level");
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        for (int frame = 0; frame < 30 * 4; frame++) {
            now += step;
            Quality.rest(0);
            Quality.frame(1, now, 0f);
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
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        now = frames(now + 60 * SECOND, 100f, 2f);
        now = frames(now + 6 * SECOND, 100f, 2f);
        check(Quality.sight() == 80, "one frame of six seconds among fast ones is a hitch and takes nothing down");
    }

    /** Each step Automatic took, as "<seconds> <level>", from the frames of a scene drawn at a rate that depends on the level alone. */
    private static List<String> playedFor(long from, float seconds, java.util.function.ToDoubleFunction<String> rateAt) {
        List<String> steps = new ArrayList<>();
        String level = Quality.name();
        long now = from;
        while (now - from < (long) (seconds * SECOND)) {
            now += (long) (SECOND / rateAt.applyAsDouble(level));
            Quality.frame(0, now, limit);
            String after = Quality.name();
            if (!after.equals(level)) {
                level = after;
                steps.add((now - from) / SECOND + " " + after.substring("Automatic (".length(), after.length() - 1));
            }
        }
        return steps;
    }

    private static void automaticStepsBackUpWithRoomToSpare() {
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        check(!Quality.stepToSay(), "nothing to say before a step");
        long now = frames(SECOND, 30f, 3.5f);
        check(Quality.name().equals("Automatic (Medium)") && Quality.stepToSay(), "slow, it goes down to Medium and has that to say: " + Quality.name());
        Quality.stepSaid();
        check(!Quality.stepToSay(), "and once said, nothing more");
        check(Quality.choice(Quality.AUTOMATIC).equals("Automatic (Medium)") && Quality.choice(Quality.HIGH).equals("High"),
                "the options screen names Automatic with its level, and the other modes as they are");
        now = frames(now, 50f, 300f);
        check(Quality.name().equals("Automatic (Medium)"), "fifty frames a second, between the two rates, keep it where it is for five minutes");
        now = frames(now, 60f, 10f);
        now = frames(now + SECOND / 2, 60f, 10f);
        check(Quality.name().equals("Automatic (Medium)"), "sixty frames a second broken by a hitch are not fifteen seconds of them");
        now = frames(now, 60f, 18f);
        check(Quality.name().equals("Automatic (High)") && Quality.stepToSay(), "fifteen seconds of sixty take it back up to High, which it says: " + Quality.name());
        Quality.stepSaid();
        frames(now, 60f, 600f);
        check(Quality.name().equals("Automatic (High)") && !Quality.stepToSay(), "where it stays while the picture keeps up");
        Quality.set(Quality.HIGH);
        check(Quality.choice(Quality.AUTOMATIC).equals("Automatic"), "out of Automatic the screen names it without a level");
    }

    private static void automaticStaysBarredForTheFirstTwoMinutes(long now) {
        // Stepped down at `now`: High is not tried again for two minutes, however fast Medium is.
        now = frames(now, 120f, 110f);
        check(Quality.name().equals("Automatic (Medium)"), "a level stepped down from is not stepped up into again within two minutes: " + Quality.name());
        frames(now, 120f, 20f);
        check(Quality.name().equals("Automatic (High)"), "after which it is: " + Quality.name());
    }

    /**
     * The scene the owner named: 44 frames a second at High and 60 at Medium. It must settle at
     * Medium, trying High again ever more seldom, and never be thrown further down by the tries.
     */
    private static void aSceneSlowAtOneLevelAndFastAtTheNextSettles() {
        for (float held : new float[] {0f, 60f}) {
            limit = held;
            Quality.set(Quality.HIGH);
            Quality.set(Quality.AUTOMATIC);
            List<String> steps = playedFor(SECOND, 2 * 3600f,
                    level -> level.endsWith("(High)") ? 44.0 : level.endsWith("(Medium)") ? Math.min(60.0, held > 0f ? held - 0.1 : 60.0) : 90.0);
            String limited = held > 0f ? ", the game held to 60 frames a second: " : ": ";
            List<Long> tries = new ArrayList<>();
            boolean belowMedium = false;
            for (String step : steps) {
                belowMedium |= step.endsWith("Low") || step.endsWith("Stock");
                if (step.endsWith("High")) {
                    tries.add(Long.parseLong(step.substring(0, step.indexOf(' '))));
                }
            }
            check(!steps.isEmpty() && steps.get(0).endsWith("Medium") && Long.parseLong(steps.get(0).substring(0, steps.get(0).indexOf(' '))) < 10,
                    "it goes down to Medium in its first seconds" + limited + steps);
            check(!belowMedium, "a try at High is never what takes it below Medium" + limited + steps);
            check(tries.size() <= 7, "in two hours High is tried at most seven times" + limited + steps);
            boolean wider = true;
            long gap = 0;
            for (int i = 1; i < tries.size(); i++) {
                long next = tries.get(i) - tries.get(i - 1);
                wider &= next + 10 >= gap && next >= 120;
                gap = next;
            }
            check(wider && gap >= 32 * 60, "each try is at least as long after the last as that one was after the one before, and the last ones half an hour apart"
                    + limited + tries);
            check(Quality.name().equals("Automatic (Medium)"), "and two hours on it is at Medium" + limited + Quality.name());
            limit = 0f;
            Quality.set(Quality.HIGH);
            Quality.set(Quality.AUTOMATIC);
            steps = playedFor(SECOND, 3600f, level -> level.endsWith("(High)") ? 44.0 : 52.0);
            check(steps.size() == 1 && steps.get(0).endsWith("Medium"), "a scene at 52 at Medium never tries High at all: " + steps);
        }
        limit = 0f;
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        long now = frames(SECOND, 30f, 3.5f);
        check(Quality.name().equals("Automatic (Medium)"), "slow, to Medium");
        automaticStaysBarredForTheFirstTwoMinutes(now);
    }

    private static void automaticsFloorIsTheStockLookAndItComesBackUp() {
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        List<String> down = playedFor(SECOND, 30f, level -> 20.0);
        check(down.equals(List.of("3 Medium", "8 Low", "13 Stock")), "at twenty frames a second it steps down a level every five seconds to Stock: " + down);
        check(Quality.plain() && !Quality.gamesPlants() && Quality.drawn().equals(Quality.preset(Quality.LOW)),
                "where it draws what the Low mode draws, the game's own look");
        List<String> up = playedFor(31 * SECOND, 600f, level -> 100.0);
        check(up.size() == 3 && up.get(0).endsWith("Low") && up.get(1).endsWith("Medium") && up.get(2).endsWith("High"),
                "with room to spare it comes back up, a level at a time, to High: " + up);
        check(Quality.drawn().equals(Quality.HIGH_PICTURE), "and draws High's picture");
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        playedFor(SECOND, 15f, level -> 20.0);
        check(Quality.name().equals("Automatic (Stock)"), "down again");
        long from = 16 * SECOND;
        List<String> fromStock = playedFor(from, 130f, level -> 100.0);
        check(fromStock.size() == 1 && fromStock.get(0).endsWith("Low") && !Quality.plain() && !Quality.gamesPlants(),
                "the first step up from Stock is to Low's cost with the mod's own look, its plants among it: " + fromStock);
    }

    private static void theGamesFrameLimitIsNotASlowPicture() {
        limit = 30f;
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        long now = frames(SECOND, 29.5f, 120f);
        check(Quality.name().equals("Automatic (High)"), "a game held to thirty frames a second that draws 29.5 stays at High: " + Quality.name());
        now = frames(now, 20f, 7f);
        check(Quality.name().equals("Automatic (Medium)"), "and twenty under that limit is slow: " + Quality.name());
        limit = 60f;
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        now = frames(now, 40f, 3.5f);
        check(Quality.name().equals("Automatic (Medium)"), "under a limit of sixty, forty is slow: " + Quality.name());
        frames(now, 59.5f, 140f);
        check(Quality.name().equals("Automatic (High)"), "and 59.5, as near sixty as such a game gets, is room to spare: " + Quality.name());
        limit = 0f;
    }

    private static void aModeThePlayerChoseIsNeverStepped() {
        for (int mode : new int[] {Quality.LOW, Quality.MEDIUM, Quality.HIGH, Quality.CUSTOM}) {
            Quality.set(mode);
            Quality.Picture chosen = Quality.drawn();
            long now = frames(SECOND, 10f, 30f);
            frames(now, 200f, 300f);
            check(Quality.drawn().equals(chosen) && !Quality.stepToSay() && Quality.row() == mode,
                    Quality.name() + " is drawn as chosen, however slow or fast the frames");
        }
    }

    private static void theKeyGoesAutomaticLowMediumHighCustomAndRound() {
        Quality.set(Quality.AUTOMATIC);
        check(Quality.next() == Quality.LOW, "the key goes from Automatic to Low");
        Quality.set(Quality.LOW);
        check(Quality.next() == Quality.MEDIUM, "from Low to Medium");
        Quality.set(Quality.MEDIUM);
        check(Quality.next() == Quality.HIGH, "from Medium to High");
        Quality.set(Quality.HIGH);
        check(Quality.next() == Quality.CUSTOM, "from High to Custom");
        Quality.set(Quality.CUSTOM);
        check(Quality.next() == Quality.AUTOMATIC && Quality.name().equals("Custom"), "and from Custom round to Automatic");
        check(Quality.words().equals("Automatic,Low,Medium,High,Custom"), "and the row's words are in that order: " + Quality.words());
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
