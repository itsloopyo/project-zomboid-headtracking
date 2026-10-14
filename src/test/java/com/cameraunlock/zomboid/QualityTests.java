package com.cameraunlock.zomboid;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

final class QualityTests {
    private QualityTests() {}

    private static final long SECOND = 1_000_000_000L;

    static void run() {
        Checks.run("aLevelAskedForIsKept", QualityTests::aLevelAskedForIsKept);
        Checks.run("automaticStepsDownWhileThePictureIsSlow", QualityTests::automaticStepsDownWhileThePictureIsSlow);
        Checks.run("automaticStaysWhereThePictureKeepsUp", QualityTests::automaticStaysWhereThePictureKeepsUp);
        Checks.run("everyPatternTheReviewsNamedDoesWhatItShould", QualityTests::everyPatternTheReviewsNamedDoesWhatItShould);
        Checks.run("aStepUpSettlesThoughTheCountRestsWhileTheContentIsMade", QualityTests::aStepUpSettlesThoughTheCountRestsWhileTheContentIsMade);
        Checks.run("automaticStepsBackUpWithRoomToSpare", QualityTests::automaticStepsBackUpWithRoomToSpare);
        Checks.run("aSceneSlowAtOneLevelAndFastAtTheNextSettles", QualityTests::aSceneSlowAtOneLevelAndFastAtTheNextSettles);
        Checks.run("automaticsFloorIsTheStockLookAndItComesBackUp", QualityTests::automaticsFloorIsTheStockLookAndItComesBackUp);
        Checks.run("theGamesFrameLimitIsNotASlowPicture", QualityTests::theGamesFrameLimitIsNotASlowPicture);
        Checks.run("aModeThePlayerChoseIsNeverStepped", QualityTests::aModeThePlayerChoseIsNeverStepped);
        Checks.run("twoViewsAreCountedAsOnePicture", QualityTests::twoViewsAreCountedAsOnePicture);
        Checks.run("theKeyGoesAutomaticLowMediumHighCustomAndRound", QualityTests::theKeyGoesAutomaticLowMediumHighCustomAndRound);
        Checks.run("eachViewKeepsAModeOfItsOwn", QualityTests::eachViewKeepsAModeOfItsOwn);
        Checks.run("theInstallerNamesTheModesInTheKeysOrder", QualityTests::theInstallerNamesTheModesInTheKeysOrder);
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
        check(Quality.scale() == 0.8f && Quality.sight() == 80 && !Quality.crownsToSight() && !Quality.gamesPlants() && !Quality.shadedAndLit()
                        && !Quality.relief(),
                "medium is fewer pixels, the mod's trees nearer only, and no shading of corners, lit air, mirrors or depth in surfaces");
        Quality.set(Quality.LOW);
        check(Quality.scale() < 0.7f && Quality.sight() == 48 && Quality.shadowSize() == 2048 && !Quality.shadedAndLit() && !Quality.relief(),
                "low is fewer pixels, shorter sight, smaller maps and no shading of corners");
    }

    private static void automaticStepsDownWhileThePictureIsSlow() {
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        check(Quality.scale() == 1f, "automatic begins with the whole picture");
        long now = frames(SECOND, 30f, 2.5f);
        check(Quality.scale() == 1f, "and judges nothing in under three seconds");
        now = frames(now, 30f, 1f);
        check(Quality.scale() == 0.8f, "three seconds at thirty frames a second take it down a level");
        now = frames(now, 30f, 4f);
        check(Quality.scale() == 0.8f, "the next count begins once the new level has settled");
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
        check(Quality.scale() == 0.8f, "two views drawn thirty times a second are thirty frames a second, and take it down a level");
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        for (int frame = 0; frame < 30 * 4; frame++) {
            now += step;
            Quality.rest(0);
            Quality.frame(1, now, 0f);
        }
        check(Quality.scale() == 0.8f, "with the first view off the second's frames are the ones counted, and the first's being off does not begin the count again");
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        Quality.rest(1);
        now = frames(now + 60 * SECOND, 60f, 10f);
        check(Quality.scale() == 1f, "and with both off the count begins again");
        Quality.rest(0);
    }

    private static void automaticStaysWhereThePictureKeepsUp() {
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        long now = frames(SECOND, 60f, 30f);
        check(Quality.scale() == 1f, "at sixty frames a second the picture stays whole");
        Quality.rest(0);
        now = frames(now + 60 * SECOND, 60f, 10f);
        check(Quality.scale() == 1f, "and a minute with the view off is not a minute of slow frames");
        now = frames(now, 30f, 3.2f);
        check(Quality.scale() == 0.8f, "slow frames later on are still acted on");
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        now = frames(now + 60 * SECOND, 100f, 2f);
        now = frames(now + 6 * SECOND, 100f, 2f);
        check(Quality.scale() == 1f, "one frame of six seconds among fast ones is a hitch and takes nothing down");
    }

    /** How long the next frame takes, by the mode's name ("Automatic (High)") and the nanoseconds since the pattern began. */
    interface FrameTimes {
        long next(String level, long at);
    }

    /**
     * A pattern of frame times the reviews of Automatic named, the game's frame limit by the
     * nanoseconds since the pattern began (0 for none), how long it is played for, and what Automatic must do with it: holds takes the
     * steps it took, each "<seconds> <level>".
     */
    record Pattern(String name, FrameLimit limit, float seconds, FrameTimes times, String expected, Predicate<List<String>> holds) {}

    /** The game's frame limit by the nanoseconds since the pattern began, 0 for none. */
    interface FrameLimit {
        float at(long at);
    }

    private static FrameLimit held(float limit) {
        return at -> limit;
    }

    private static FrameTimes steady(double rate) {
        return (level, at) -> (long) (SECOND / rate);
    }

    /** At rate, with every nth frame taking slow nanoseconds. */
    private static FrameTimes everyNth(double rate, int n, long slow) {
        int[] frame = {0};
        return (level, at) -> ++frame[0] % n == 0 ? slow : (long) (SECOND / rate);
    }

    /** 4 seconds at 20 frames a second, which takes it down to Medium, then times. */
    private static FrameTimes fromMedium(FrameTimes then) {
        return (level, at) -> at < 4 * SECOND ? SECOND / 20 : then.next(level, at);
    }

    /** At rate, with a run, a period after the last ended, of so many stalled frames each so long, then a hitch of hitch nanoseconds (0 for none). */
    private static FrameTimes runs(double rate, long period, int stalled, long each, long hitch) {
        return runs(rate, period, stalled, each, hitch, false);
    }

    /** The same, with the hitch first in the run where hitchFirst. */
    private static FrameTimes runs(double rate, long period, int stalled, long each, long hitch, boolean hitchFirst) {
        long[] next = {period};
        int[] left = {-1};
        return (level, at) -> {
            if (left[0] < 0 && at >= next[0]) {
                left[0] = stalled + (hitch > 0 ? 1 : 0);
            }
            if (left[0] > 0) {
                left[0]--;
                int at0 = hitchFirst ? stalled : 0;
                return left[0] == at0 && hitch > 0 ? hitch : each;
            }
            if (left[0] == 0) {
                left[0] = -1;
                next[0] = at + period;
            }
            return (long) (SECOND / rate);
        };
    }

    private static boolean staysAtHigh(List<String> steps) {
        return steps.isEmpty();
    }

    private static boolean downToStock(List<String> steps) {
        return steps.size() == 3 && steps.get(0).endsWith("Medium") && seconds(steps.get(0)) <= 4 && steps.get(2).endsWith("Stock");
    }

    private static long seconds(String step) {
        return Long.parseLong(step.substring(0, step.indexOf(' ')));
    }

    /** Every pattern, each played from Automatic at High. */
    static List<Pattern> patterns() {
        List<Pattern> all = new ArrayList<>();
        all.add(new Pattern("steady 100, no limit", held(0f), 120f, steady(100), "stays at High", QualityTests::staysAtHigh));
        all.add(new Pattern("steady 29.5, limit 30", held(30f), 120f, steady(29.5), "stays at High", QualityTests::staysAtHigh));
        all.add(new Pattern("steady 59.5, limit 60", held(60f), 120f, steady(59.5), "stays at High", QualityTests::staysAtHigh));
        for (double rate : new double[] {40, 20, 8, 5, 3}) {
            all.add(new Pattern("steady " + (int) rate, held(0f), 60f, steady(rate), "down a level within 4 s, on to Stock", QualityTests::downToStock));
        }
        all.add(new Pattern("100 with a 6 s frame after every 5 s", held(0f), 120f, runs(100, 5 * SECOND, 0, 0, 6 * SECOND), "stays at High", QualityTests::staysAtHigh));
        all.add(new Pattern("60 with a save every 12 s: ten 150 ms frames in a row", held(0f), 300f, runs(60, 12 * SECOND, 10, SECOND * 15 / 100, 0),
                "stays at High", QualityTests::staysAtHigh));
        all.add(new Pattern("60 with a save every 12 s: ten 150 ms frames, then a 0.5 s hitch", held(0f), 300f,
                runs(60, 12 * SECOND, 10, SECOND * 15 / 100, SECOND / 2), "stays at High", QualityTests::staysAtHigh));
        all.add(new Pattern("60 with one 150 ms frame in every five (23 real)", held(0f), 60f, everyNth(60, 5, SECOND * 15 / 100),
                "down a level within 4 s, on to Stock", QualityTests::downToStock));
        all.add(new Pattern("60 with one 250 ms frame in every five (15.8 real)", held(0f), 60f, everyNth(60, 5, SECOND / 4),
                "down a level within 4 s, on to Stock", QualityTests::downToStock));
        all.add(new Pattern("200 and 300 ms frames by turns", held(0f), 60f, everyNth(1e9 / 200e6, 2, SECOND * 3 / 10),
                "down a level within 4 s, on to Stock", QualityTests::downToStock));
        all.add(new Pattern("4 s at 20, then 59.5, limit 60, a 110 ms frame every 10 s", held(60f), 400f,
                fromMedium(runs(59.5, 10 * SECOND, 1, SECOND * 11 / 100, 0)), "down to Medium, back up to High by 200 s, and stays",
                steps -> steps.size() == 2 && steps.get(0).endsWith("Medium") && steps.get(1).endsWith("High") && seconds(steps.get(1)) <= 200));
        for (double rate : new double[] {46, 50, 56}) {
            all.add(new Pattern("steady " + (int) rate, held(0f), 300f, steady(rate), "stays at High", QualityTests::staysAtHigh));
            all.add(new Pattern("4 s at 20, then steady " + (int) rate, held(0f), 600f, fromMedium(steady(rate)), "down to Medium and stays",
                    steps -> steps.size() == 1 && steps.get(0).endsWith("Medium")));
        }
        all.add(new Pattern("4 s at 20, then 100 with a 0.3 s stall every second", held(0f), 600f, fromMedium(runs(100, SECOND, 1, SECOND * 3 / 10, 0)),
                "down to Medium and stays: a hitch in every count is no room to spare", steps -> steps.size() == 1 && steps.get(0).endsWith("Medium")));
        all.add(new Pattern("29.5, limit 30, raised to 60 at 2 s, then 59.5", at -> at < 2 * SECOND ? 30f : 60f, 120f,
                (level, at) -> (long) (SECOND / (at < 2 * SECOND ? 29.5 : 59.5)), "stays at High", QualityTests::staysAtHigh));
        all.add(new Pattern("100 with a 6 s freeze and a 120 ms frame after every 5 s", held(0f), 120f,
                runs(100, 5 * SECOND, 1, SECOND * 12 / 100, 6 * SECOND, true), "stays at High", QualityTests::staysAtHigh));
        all.add(new Pattern("60 with a save every 12 s: ten 150 ms frames, then a 0.51 s hitch", held(0f), 300f,
                runs(60, 12 * SECOND, 10, SECOND * 15 / 100, SECOND * 51 / 100, false), "stays at High", QualityTests::staysAtHigh));
        all.add(new Pattern("3.5 s at 60, then 1.9 s at 9, by turns (42 real)", held(0f), 60f,
                (level, at) -> at % (SECOND * 54 / 10) < SECOND * 35 / 10 ? SECOND / 60 : SECOND / 9, "down a level, on to Stock",
                steps -> steps.size() == 3 && steps.get(2).endsWith("Stock")));
        for (float limit : new float[] {0f, 60f}) {
            all.add(new Pattern("44 at High, 60 at Medium" + (limit > 0f ? ", limit 60 (59.9 at Medium)" : "") + ", 2 hours", held(limit), 7200f,
                    (level, at) -> (long) (SECOND / (level.endsWith("(High)") ? 44.0 : level.endsWith("(Medium)") ? (limit > 0f ? 59.9 : 60.0) : 90.0)),
                    "Medium within 10 s, never below it, High tried 7 times at most, ends at Medium", steps -> {
                        long tries = steps.stream().filter(step -> step.endsWith("High")).count();
                        boolean below = steps.stream().anyMatch(step -> step.endsWith("Low") || step.endsWith("Stock"));
                        return !steps.isEmpty() && steps.get(0).endsWith("Medium") && seconds(steps.get(0)) < 10 && !below && tries <= 7
                                && steps.get(steps.size() - 1).endsWith("Medium");
                    }));
        }
        return all;
    }

    /** Plays a pattern from Automatic at High: the steps it took. */
    static List<String> played(Pattern pattern) {
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        List<String> steps = new ArrayList<>();
        String level = Quality.name();
        long from = SECOND, now = from;
        while (now - from < (long) (pattern.seconds() * SECOND)) {
            now += pattern.times().next(level, now - from);
            Quality.frame(0, now, pattern.limit().at(now - from));
            String after = Quality.name();
            if (!after.equals(level)) {
                level = after;
                steps.add((now - from) / SECOND + " " + after.substring("Automatic (".length(), after.length() - 1));
            }
        }
        return steps;
    }

    /** What each pattern did, a line each: ok or FAIL, the pattern, what it should do, the steps it took. */
    static List<String> table() {
        List<String> lines = new ArrayList<>();
        for (Pattern pattern : patterns()) {
            List<String> steps = played(pattern);
            String got = steps.size() <= 8 ? steps.toString() : steps.subList(0, 8) + " ... " + steps.size() + " steps, the last " + steps.get(steps.size() - 1);
            lines.add((pattern.holds().test(steps) ? "ok   " : "FAIL ") + pattern.name() + " | " + pattern.expected() + " | " + got);
        }
        Quality.set(Quality.HIGH);
        return lines;
    }

    private static void everyPatternTheReviewsNamedDoesWhatItShould() {
        for (String line : table()) {
            check(line.startsWith("ok"), line);
        }
    }

    /**
     * The count rests while the content is made after a step (FirstPerson.notePace), which is
     * on every step up from Medium to High: the five seconds a step up settles in still run.
     */
    private static void aStepUpSettlesThoughTheCountRestsWhileTheContentIsMade() {
        Quality.set(Quality.HIGH);
        Quality.set(Quality.AUTOMATIC);
        long now = frames(SECOND, 30f, 3.5f);
        check(Quality.name().equals("Automatic (Medium)"), "slow, to Medium");
        while (!Quality.name().equals("Automatic (High)") && now < 400 * SECOND) {
            now += SECOND / 100;
            Quality.frame(0, now, 0f);
        }
        check(Quality.name().equals("Automatic (High)"), "fast, back up to High once it may be: " + Quality.name());
        Quality.rest(0);
        now = frames(now + SECOND / 2, 40f, 4f);
        check(Quality.name().equals("Automatic (High)"), "forty frames a second in the five seconds after the step are not judged, the count rested or not: " + Quality.name());
        frames(now, 40f, 4.5f);
        check(Quality.name().equals("Automatic (Medium)"), "after them forty is slow: " + Quality.name());
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
        check(Quality.plain() && Quality.gamesPlants() && Quality.drawn().equals(Quality.preset(Quality.LOW)),
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

    /**
     * First person's mode and the isometric view's are kept apart, the one in effect is first
     * person's while any view is in it, and the isometric view's Low, the game's own picture,
     * leaves first person's picture as it was.
     */
    private static void eachViewKeepsAModeOfItsOwn() {
        Quality.set(Quality.HIGH);
        Quality.setOverhead(Quality.LOW);
        check(!Quality.firstPersonInEffect() && Quality.overheadPlain() && Quality.drawn().equals(Quality.HIGH_PICTURE)
                        && Quality.said().equals("Isometric graphics: Low"),
                "in the isometric view's Low the game draws that view, and the picture stays first person's High: " + Quality.said());
        Quality.setOverhead(Quality.MEDIUM);
        check(!Quality.overheadPlain() && Quality.drawn().equals(Quality.MEDIUM_PICTURE) && Quality.name().equals("High"),
                "in its Medium the isometric view is drawn as Medium, and first person's mode stays High");
        Quality.inFirstPerson(1, true);
        check(Quality.firstPersonInEffect() && Quality.drawn().equals(Quality.HIGH_PICTURE) && Quality.said().equals("First person graphics: High"),
                "a view going to first person puts first person's mode in effect: " + Quality.said());
        Quality.inFirstPerson(0, true);
        Quality.inFirstPerson(1, false);
        check(Quality.firstPersonInEffect() && Quality.drawn().equals(Quality.HIGH_PICTURE), "and it stays while any view is in first person");
        Quality.set(Quality.LOW);
        check(Quality.plain() && Quality.overheadRow() == Quality.MEDIUM, "first person's mode set there leaves the isometric view's");
        Quality.inFirstPerson(0, false);
        check(!Quality.firstPersonInEffect() && Quality.drawn().equals(Quality.MEDIUM_PICTURE) && Quality.name().equals("Low"),
                "with every view back in the isometric view, its Medium is drawn again and first person's Low is kept");
        Quality.setOverhead(Quality.CUSTOM);
        check(Quality.drawn().equals(Quality.custom()) && Quality.said().equals("Isometric graphics: Custom"), "its Custom draws the rows of [Graphics]");
        check(Quality.nextOverhead() == Quality.LOW, "and the key goes from Custom round to Low, with no Automatic");
        Quality.setOverhead(Quality.LOW);
        check(Quality.nextOverhead() == Quality.MEDIUM && Quality.drawn().equals(Quality.LOW_PICTURE),
                "from Low to Medium, and in Low the picture is first person's again, its Low");
        Quality.setOverhead(Quality.MEDIUM);
        check(Quality.nextOverhead() == Quality.HIGH, "from Medium to High");
        Quality.setOverhead(Quality.HIGH);
        check(Quality.nextOverhead() == Quality.CUSTOM && Quality.drawn().equals(Quality.HIGH_PICTURE), "and from High to Custom");
        check(Quality.overheadWords().equals("Low,Medium,High,Custom"), "and the isometric row's words are in that order: " + Quality.overheadWords());
        boolean refused = false;
        try {
            Quality.setOverhead(Quality.AUTOMATIC);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused && Quality.overheadRow() == Quality.HIGH, "the isometric view has no Automatic");
        Quality.setOverhead(Quality.LOW);
        Quality.set(Quality.HIGH);
    }

    /** What the installer says F9 does is the order the key goes in, and the file keeps the line ends cmd.exe reads. */
    private static void theInstallerNamesTheModesInTheKeysOrder() throws IOException {
        String text = new String(Files.readAllBytes(Path.of("scripts", "install.cmd")), StandardCharsets.US_ASCII);
        String modes = String.join(", ", Quality.words().split(",")) + " in first person; " + String.join(", ", Quality.overheadWords().split(","))
                + " in the isometric view";
        check(text.contains("F9        Cycle graphics mode: " + modes + "&echo"), "install.cmd's controls name F9's modes as " + modes);
        check(text.replace("\r\n", "").indexOf('\n') < 0, "every line of install.cmd ends in CR LF");
    }

    /** Every answer a graphics mode gives, as one line: the draw distance on foot and ahead of a vehicle among them, then the far trees and frame blending. */
    private static String answers() {
        return Quality.plain() + " " + Quality.gamesPlants() + " " + Quality.scale() + " " + Quality.shadowSize() + " " + Quality.sight() + " "
                + reaches() + " " + Quality.glassRain() + " " + Quality.shadedAndLit() + " " + Quality.reflections() + " " + Quality.surfacesMirror() + " "
                + Quality.smallerPictures() + " " + Quality.crownsToSight() + " " + Quality.relief() + " " + Quality.wholeFigureShadows() + " " + Quality.sightBehind()
                + " " + Quality.farTreeDistance() + " " + Quality.frameBlending() + " " + Quality.grassDensity();
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
            "true true 0.62 2048 48 all/all 0 false true false true false false false 48 300 false 100",
            "false false 0.8 2048 80 all/all 1 false true false true false false false 48 300 true 100",
            "false false 1.0 4096 80 all/all 2 true true true false true true true 48 768 true 100",
        };
        for (int mode = Quality.LOW; mode <= Quality.HIGH; mode++) {
            Quality.set(mode);
            String preset = answers();
            check(preset.equals(expected[mode - Quality.LOW]), Quality.name() + " draws as it always has: " + preset);
            Quality.custom(Config.picture(Config.values(Quality.preset(mode))));
            Quality.set(Quality.CUSTOM);
            // Custom always draws what the mod made: Low's rows there are Low's cost without the game's own look.
            String custom = mode == Quality.LOW ? "false" + preset.substring("true".length()) : preset;
            check(answers().equals(custom), "Custom with the rows " + Quality.preset(mode) + " written as the options screen holds them draws as "
                    + Quality.name() + " did, with what the mod made: " + answers());
        }
    }

    /** Each part of Custom's picture changed alone, from High's, changes the one answer that part is and no other. */
    private static void eachRowOfCustomReachesItsOwnAnswer() {
        Quality.custom(Quality.HIGH_PICTURE);
        Quality.set(Quality.CUSTOM);
        String high = answers();
        double[] changed = {Quality.TREES_NEAR, 40, 0.7, 0, 40, 30, 300, 0, 0, 0, 0, 0, 0, 0};
        String[] answer = {
            "false false 1.0 4096 80 all/all 2 true true true false false true true 48 768 true 100",
            "false false 1.0 4096 80 all/all 2 true true true false true true true 48 768 true 40",
            "false false 0.7 4096 80 all/all 2 true true true false true true true 48 768 true 100",
            "false false 1.0 2048 80 all/all 2 true true true false true true true 48 768 true 100",
            "false false 1.0 4096 40 all/all 2 true true true false true true true 48 768 true 100",
            "false false 1.0 4096 80 30/all 2 true true true false true true true 48 768 true 100",
            "false false 1.0 4096 80 all/all 2 true true true false true true true 48 300 true 100",
            "false false 1.0 4096 80 all/all 0 true true true false true true true 48 768 true 100",
            "false false 1.0 4096 80 all/all 2 false true false false true true true 48 768 true 100",
            "false false 1.0 4096 80 all/all 2 true false false false true true true 48 768 true 100",
            "false false 1.0 4096 80 all/all 2 true true true true true true true 48 768 true 100",
            "false false 1.0 4096 80 all/all 2 true true true false true false true 48 768 true 100",
            "false false 1.0 4096 80 all/all 2 true true true false true true false 48 768 true 100",
            "false false 1.0 4096 80 all/all 2 true true true false true true true 48 768 false 100",
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
        values[Config.graphicsPart("ViewDistance")] = 56;
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
        check(Quality.sight() == 56 && !Quality.crownsToSight() && Quality.drawn().equals(mine), "so back in Custom it is the player's own again");
        Quality.set(Quality.HIGH);
        Quality.custom(Quality.HIGH_PICTURE);
        check(Quality.sight() == 80, "and rows given while another mode is drawn are kept for Custom and draw nothing yet");
        Quality.custom(mine);
        check(Quality.sight() == 80 && Quality.custom().equals(mine), "in either direction");
    }
}
