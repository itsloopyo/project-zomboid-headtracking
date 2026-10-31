package com.cameraunlock.zomboid;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

final class QualityTests {
    private QualityTests() {}

    private static final long SECOND = 1_000_000_000L;

    static void run() {
        Checks.run("splitScreenLowersHighOnce", QualityTests::splitScreenLowersHighOnce);
        Checks.run("aLevelAskedForIsKept", QualityTests::aLevelAskedForIsKept);
        Checks.run("automaticStepsDownWhileThePictureIsSlow", QualityTests::automaticStepsDownWhileThePictureIsSlow);
        Checks.run("automaticPicksTheLevelThatHoldsAndNeverStepsAgain", QualityTests::automaticPicksTheLevelThatHoldsAndNeverStepsAgain);
        Checks.run("everyPatternTheReviewsNamedDoesWhatItShould", QualityTests::everyPatternTheReviewsNamedDoesWhatItShould);
        Checks.run("automaticSaysItsStepsAndLeavesItsPickToBeSaid", QualityTests::automaticSaysItsStepsAndLeavesItsPickToBeSaid);
        Checks.run("automaticsFloorIsTheStockLookAndItPicksLow", QualityTests::automaticsFloorIsTheStockLookAndItPicksLow);
        Checks.run("theGamesFrameLimitIsNotASlowPicture", QualityTests::theGamesFrameLimitIsNotASlowPicture);
        Checks.run("aModeThePlayerChoseIsNeverStepped", QualityTests::aModeThePlayerChoseIsNeverStepped);
        Checks.run("twoViewsAreCountedAsOnePicture", QualityTests::twoViewsAreCountedAsOnePicture);
        Checks.run("theKeyGoesLowMediumHighCustomAndRound", QualityTests::theKeyGoesLowMediumHighCustomAndRound);
        Checks.run("eachViewKeepsAModeOfItsOwn", QualityTests::eachViewKeepsAModeOfItsOwn);
        Checks.run("eachPlayerHasModesOfTheirOwn", QualityTests::eachPlayerHasModesOfTheirOwn);
        Checks.run("theInstallerNamesTheModesInTheKeysOrder", QualityTests::theInstallerNamesTheModesInTheKeysOrder);
        Checks.run("customDrawsWhatEachPresetDrewFromItsRows", () -> inFirstPerson(QualityTests::customDrawsWhatEachPresetDrewFromItsRows));
        Checks.run("eachRowOfCustomReachesItsOwnAnswer", () -> inFirstPerson(QualityTests::eachRowOfCustomReachesItsOwnAnswer));
        Checks.run("thePresetsLeaveCustomAsItWas", QualityTests::thePresetsLeaveCustomAsItWas);
        Checks.run("aPresetDrawsALargeViewAtTheMostPixelsAndCustomAtItsRow", QualityTests::aPresetDrawsALargeViewAtTheMostPixelsAndCustomAtItsRow);
        Quality.custom(Quality.HIGH_PICTURE);
        Quality.set(0, Quality.HIGH);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** A preset draws a view of more than MOST_PRESET_PIXELS as if it were that size, Custom draws its RenderScale row whatever the size. */
    private static void aPresetDrawsALargeViewAtTheMostPixelsAndCustomAtItsRow() {
        Quality.custom(Quality.HIGH_PICTURE);
        Quality.inFirstPerson(0, true);
        for (int mode : new int[] {Quality.LOW, Quality.MEDIUM, Quality.HIGH}) {
            Quality.set(0, mode);
            float share = Quality.scale(0);
            check(Quality.scale(0, 1920, 1080) == share && Quality.scale(0, 2560, 1440) == share,
                    Quality.name(0) + " draws a view of 1440p or less at its own render scale");
            float at4k = Quality.scale(0, 3840, 2160);
            check(Math.abs(at4k - share * 2f / 3f) < 1e-5f, Quality.name(0) + " draws a 4K view with the pixels it gives 1440p: " + at4k);
            check(Math.abs(at4k * 3840 * at4k * 2160 - share * 2560 * share * 1440) < 2f, "which is the same count of pixels");
        }
        Quality.set(0, Quality.CUSTOM);
        check(Quality.scale(0, 3840, 2160) == 1f, "Custom with RenderScale 1 draws a 4K view whole");
        Quality.setOverhead(0, Quality.CUSTOM);
        Quality.set(0, Quality.HIGH);
        Quality.inFirstPerson(0, false);
        check(Quality.scale(0, 3840, 2160) == 1f, "and so does the isometric view in Custom");
        Quality.setOverhead(0, Quality.HIGH);
        check(Quality.scale(0, 3840, 2160) == 1f, "and the isometric view in High: its picture goes with the zoom, and is never held");
        Quality.setOverhead(0, Quality.LOW);
        Quality.inFirstPerson(1, true);
        Quality.set(1, Quality.AUTOMATIC);
        float automatic = Quality.scale(1);
        check(Math.abs(Quality.scale(1, 3840, 2160) - automatic * 2f / 3f) < 1e-5f, "a second view in first person in Automatic is held as the first is");
        Quality.set(1, Quality.CUSTOM);
        check(Quality.scale(1, 3840, 2160) == 1f, "and in Custom is not");
        Quality.set(1, Quality.HIGH);
        Quality.inFirstPerson(1, false);
    }

    private static void splitScreenLowersHighOnce() {
        for (int players = 2; players <= 4; players++) {
            for (int first = Quality.AUTOMATIC; first <= Quality.CUSTOM; first++) {
                for (int overhead = Quality.LOW; overhead <= Quality.CUSTOM; overhead++) {
                    Quality.players(0);
                    Quality.set(0, first);
                    Quality.setOverhead(0, overhead);
                    check(Quality.players(1) == 0, "single player keeps the chosen quality");
                    check(Quality.row(0) == first && Quality.overheadRow(0) == overhead, "single player keeps both modes");
                    check(Quality.players(players) == (first == Quality.HIGH || overhead == Quality.HIGH ? 1 : 0),
                            "only High changes when split screen begins");
                    check(Quality.row(0) == (first == Quality.HIGH ? Quality.MEDIUM : first), "first person High becomes Medium");
                    check(Quality.overheadRow(0) == (overhead == Quality.HIGH ? Quality.MEDIUM : overhead), "isometric High becomes Medium");
                    Quality.set(0, Quality.HIGH);
                    Quality.setOverhead(0, Quality.HIGH);
                    check(Quality.players(players) == 0 && Quality.players(4) == 0, "staying in split screen does not change quality again");
                    check(Quality.row(0) == Quality.HIGH && Quality.overheadRow(0) == Quality.HIGH, "High can be selected again in split screen");
                    Quality.players(1);
                    check(Quality.players(players) == 1, "joining split screen again lowers High again");
                }
            }
        }
        Quality.players(0);
        Quality.set(0, Quality.HIGH);
        Quality.setOverhead(0, Quality.LOW);
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
        Quality.set(0, Quality.HIGH);
        check(Quality.scale(0) == 1f && Quality.sight(0) == 80 && Quality.shadowSize(0) == 4096 && Quality.shadedAndLit(0),
                "high is the whole picture");
        frames(SECOND, 10f, 20f);
        check(Quality.scale(0) == 1f, "and stays the whole picture however slowly it is drawn");
        Quality.set(0, Quality.MEDIUM);
        check(Quality.scale(0) == 0.8f && Quality.sight(0) == 60 && Quality.grassDensity(0) == Quality.SOME_GRASS && !Quality.crownsToSight(0) && !Quality.gamesPlants(0) && Quality.shadedAndLit(0)
                        && !Quality.relief(0),
                "medium is fewer pixels, shorter sight, less grass, the mod's trees nearer only, and no depth in surfaces, with shaded corners and lit air");
        Quality.set(0, Quality.LOW);
        check(Quality.scale(0) < 0.7f && Quality.sight(0) == 48 && Quality.shadowSize(0) == 2048 && !Quality.shadedAndLit(0) && !Quality.relief(0),
                "low is fewer pixels, shorter sight, smaller maps and no shading of corners");
    }

    private static void automaticStepsDownWhileThePictureIsSlow() {
        Quality.set(0, Quality.HIGH);
        Quality.set(0, Quality.AUTOMATIC);
        check(Quality.scale(0) == 1f, "automatic begins with the whole picture");
        long now = frames(SECOND, 30f, 2.5f);
        check(Quality.scale(0) == 1f, "and judges nothing in under three seconds");
        now = frames(now, 30f, 1f);
        check(Quality.scale(0) == 0.8f, "three seconds at thirty frames a second take it down a level");
        now = frames(now, 30f, 4f);
        check(Quality.scale(0) == 0.8f, "the next count begins once the new level has settled");
        now = frames(now, 30f, 1.5f);
        check(Quality.sight(0) == 48 && !Quality.plain(0) && Quality.name(0).equals("Automatic (Low)"),
                "and still slow, it goes down again, to Low's cost with what the mod made: " + Quality.name(0));
        now = frames(now, 30f, 5.5f);
        check(Quality.plain(0) && Quality.drawn(0).equals(Quality.LOW_PICTURE) && Quality.name(0).equals("Automatic (Stock)"),
                "and still slow, to the Low mode's own look: " + Quality.name(0));
        Quality.decided();
        frames(now, 5f, 30f);
        check(Quality.plain(0) && Quality.row(0) == Quality.LOW && Quality.decided() == 1,
                "below which there is nothing, and there it picks Low, the game's own look, for the player to keep: " + Quality.name(0));
    }

    private static void twoViewsAreCountedAsOnePicture() {
        Quality.set(0, Quality.HIGH);
        Quality.set(0, Quality.AUTOMATIC);
        long step = SECOND / 30, now = SECOND;
        for (int frame = 0; frame < 30 * 4; frame++) {
            now += step;
            Quality.frame(0, now, 0f);
            Quality.frame(1, now + 1000, 0f);
        }
        check(Quality.scale(0) == 0.8f, "two views drawn thirty times a second are thirty frames a second, and take it down a level");
        Quality.set(0, Quality.HIGH);
        Quality.set(0, Quality.AUTOMATIC);
        for (int frame = 0; frame < 30 * 4; frame++) {
            now += step;
            Quality.rest(0);
            Quality.frame(1, now, 0f);
        }
        check(Quality.scale(0) == 0.8f, "with the first view off the second's frames are the ones counted, and the first's being off does not begin the count again");
        Quality.set(0, Quality.HIGH);
        Quality.set(0, Quality.AUTOMATIC);
        Quality.rest(1);
        now = frames(now + 60 * SECOND, 60f, 10f);
        check(Quality.scale(0) == 1f, "and with both off the count begins again");
        Quality.rest(0);
    }

    private static void automaticPicksTheLevelThatHoldsAndNeverStepsAgain() {
        Quality.set(0, Quality.HIGH);
        Quality.set(0, Quality.AUTOMATIC);
        Quality.decided();
        long now = frames(SECOND, 60f, 11f);
        check(Quality.name(0).equals("Automatic (High)") && Quality.decided() == 0, "at sixty frames a second it is still measuring after eleven seconds");
        now = frames(now, 60f, 2f);
        check(Quality.row(0) == Quality.HIGH && Quality.decided() == 1, "and once High has held twelve seconds it picks High: " + Quality.name(0));
        now = frames(now, 20f, 30f);
        check(Quality.row(0) == Quality.HIGH && !Quality.stepToSay() && Quality.decided() == 0, "and never steps the mode it picked, however slow the frames");
        Quality.set(0, Quality.AUTOMATIC);
        now = frames(now + 60 * SECOND, 30f, 3.5f);
        check(Quality.name(0).equals("Automatic (Medium)"), "slow, it goes down to Medium");
        now = frames(now, 100f, 20f);
        check(Quality.row(0) == Quality.MEDIUM && Quality.decided() == 1, "and picks Medium there, however fast the frames then are: " + Quality.name(0));
        Quality.set(0, Quality.AUTOMATIC);
        now = frames(now + 60 * SECOND, 60f, 5f);
        Quality.rest(0);
        now = frames(now + 60 * SECOND, 60f, 2f);
        check(Quality.name(0).equals("Automatic (High)"), "a minute with the view off is not a minute held");
        Quality.set(0, Quality.AUTOMATIC);
        now = frames(now + 60 * SECOND, 100f, 2f);
        frames(now + 6 * SECOND, 100f, 2f);
        check(Quality.name(0).equals("Automatic (High)"), "one frame of six seconds among fast ones is a hitch and takes nothing down");
        Quality.set(0, Quality.HIGH);
        Quality.decided();
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
        return steps.size() == 1 && steps.get(0).endsWith("picked High");
    }

    private static boolean downToStock(List<String> steps) {
        return steps.size() == 4 && steps.get(0).endsWith("Medium") && seconds(steps.get(0)) <= 4 && steps.get(2).endsWith("Stock")
                && steps.get(3).endsWith("picked Low");
    }

    private static boolean picksMedium(List<String> steps) {
        return steps.size() == 2 && steps.get(0).endsWith("Medium") && steps.get(1).endsWith("picked Medium");
    }

    private static long seconds(String step) {
        return Long.parseLong(step.substring(0, step.indexOf(' ')));
    }

    /** Every pattern, each played from Automatic at High. */
    static List<Pattern> patterns() {
        List<Pattern> all = new ArrayList<>();
        all.add(new Pattern("steady 100, no limit", held(0f), 120f, steady(100), "picks High", QualityTests::staysAtHigh));
        all.add(new Pattern("steady 29.5, limit 30", held(30f), 120f, steady(29.5), "picks High", QualityTests::staysAtHigh));
        all.add(new Pattern("steady 59.5, limit 60", held(60f), 120f, steady(59.5), "picks High", QualityTests::staysAtHigh));
        for (double rate : new double[] {40, 20, 8, 5, 3}) {
            all.add(new Pattern("steady " + (int) rate, held(0f), 60f, steady(rate), "down a level within 4 s, on to Stock, picks Low", QualityTests::downToStock));
        }
        all.add(new Pattern("100 with a 6 s frame after every 5 s", held(0f), 120f, runs(100, 5 * SECOND, 0, 0, 6 * SECOND), "picks High", QualityTests::staysAtHigh));
        all.add(new Pattern("60 with a save every 12 s: ten 150 ms frames in a row", held(0f), 300f, runs(60, 12 * SECOND, 10, SECOND * 15 / 100, 0),
                "picks High", QualityTests::staysAtHigh));
        all.add(new Pattern("60 with a save every 12 s: ten 150 ms frames, then a 0.5 s hitch", held(0f), 300f,
                runs(60, 12 * SECOND, 10, SECOND * 15 / 100, SECOND / 2), "picks High", QualityTests::staysAtHigh));
        all.add(new Pattern("60 with one 150 ms frame in every five (23 real)", held(0f), 60f, everyNth(60, 5, SECOND * 15 / 100),
                "down a level within 4 s, on to Stock, picks Low", QualityTests::downToStock));
        all.add(new Pattern("60 with one 250 ms frame in every five (15.8 real)", held(0f), 60f, everyNth(60, 5, SECOND / 4),
                "down a level within 4 s, on to Stock, picks Low", QualityTests::downToStock));
        all.add(new Pattern("200 and 300 ms frames by turns", held(0f), 60f, everyNth(1e9 / 200e6, 2, SECOND * 3 / 10),
                "down a level within 4 s, on to Stock, picks Low", QualityTests::downToStock));
        all.add(new Pattern("4 s at 20, then 59.5, limit 60, a 110 ms frame every 10 s", held(60f), 400f,
                fromMedium(runs(59.5, 10 * SECOND, 1, SECOND * 11 / 100, 0)), "down to Medium, picks it", QualityTests::picksMedium));
        for (double rate : new double[] {46, 50, 56}) {
            all.add(new Pattern("steady " + (int) rate, held(0f), 300f, steady(rate), "picks High", QualityTests::staysAtHigh));
            all.add(new Pattern("4 s at 20, then steady " + (int) rate, held(0f), 600f, fromMedium(steady(rate)), "down to Medium, picks it",
                    QualityTests::picksMedium));
        }
        all.add(new Pattern("4 s at 20, then 100 with a 0.3 s stall every second", held(0f), 600f, fromMedium(runs(100, SECOND, 1, SECOND * 3 / 10, 0)),
                "down to Medium, picks it", QualityTests::picksMedium));
        all.add(new Pattern("29.5, limit 30, raised to 60 at 2 s, then 59.5", at -> at < 2 * SECOND ? 30f : 60f, 120f,
                (level, at) -> (long) (SECOND / (at < 2 * SECOND ? 29.5 : 59.5)), "picks High", QualityTests::staysAtHigh));
        all.add(new Pattern("100 with a 6 s freeze and a 120 ms frame after every 5 s", held(0f), 120f,
                runs(100, 5 * SECOND, 1, SECOND * 12 / 100, 6 * SECOND, true), "picks High", QualityTests::staysAtHigh));
        all.add(new Pattern("60 with a save every 12 s: ten 150 ms frames, then a 0.51 s hitch", held(0f), 300f,
                runs(60, 12 * SECOND, 10, SECOND * 15 / 100, SECOND * 51 / 100, false), "picks High", QualityTests::staysAtHigh));
        all.add(new Pattern("3.5 s at 60, then 1.9 s at 9, by turns (42 real)", held(0f), 60f,
                (level, at) -> at % (SECOND * 54 / 10) < SECOND * 35 / 10 ? SECOND / 60 : SECOND / 9, "down a level, on to Stock, picks Low",
                steps -> steps.stream().anyMatch(step -> step.endsWith("Stock")) && steps.get(steps.size() - 1).endsWith("picked Low")));
        for (float limit : new float[] {0f, 60f}) {
            all.add(new Pattern("44 at High, 60 at Medium" + (limit > 0f ? ", limit 60 (59.9 at Medium)" : "") + "", held(limit), 120f,
                    (level, at) -> (long) (SECOND / (level.endsWith("(High)") ? 44.0 : level.endsWith("(Medium)") ? (limit > 0f ? 59.9 : 60.0) : 90.0)),
                    "Medium within 10 s, picks it", steps -> picksMedium(steps) && seconds(steps.get(0)) < 10));
        }
        return all;
    }

    /** Plays a pattern from Automatic at High until it picks a mode: the steps it took, and the pick as "<seconds> picked <mode>". */
    static List<String> played(Pattern pattern) {
        Quality.set(0, Quality.HIGH);
        Quality.set(0, Quality.AUTOMATIC);
        List<String> steps = new ArrayList<>();
        String level = Quality.name(0);
        long from = SECOND, now = from;
        while (now - from < (long) (pattern.seconds() * SECOND) && step(steps, level, now - from)) {
            now += pattern.times().next(level, now - from);
            Quality.frame(0, now, pattern.limit().at(now - from));
            level = Quality.name(0);
        }
        Quality.decided();
        return steps;
    }

    /** Notes a step or the pick in steps when the mode's name has changed from the last noted: whether Automatic is still measuring. */
    private static boolean step(List<String> steps, String name, long at) {
        String was = steps.isEmpty() ? "High" : steps.get(steps.size() - 1).substring(steps.get(steps.size() - 1).indexOf(' ') + 1);
        if (!name.startsWith("Automatic (")) {
            steps.add(at / SECOND + " picked " + name);
            return false;
        }
        String level = name.substring("Automatic (".length(), name.length() - 1);
        if (!level.equals(was)) {
            steps.add(at / SECOND + " " + level);
        }
        return true;
    }

    /** What each pattern did, a line each: ok or FAIL, the pattern, what it should do, the steps it took. */
    static List<String> table() {
        List<String> lines = new ArrayList<>();
        for (Pattern pattern : patterns()) {
            List<String> steps = played(pattern);
            String got = steps.size() <= 8 ? steps.toString() : steps.subList(0, 8) + " ... " + steps.size() + " steps, the last " + steps.get(steps.size() - 1);
            lines.add((pattern.holds().test(steps) ? "ok   " : "FAIL ") + pattern.name() + " | " + pattern.expected() + " | " + got);
        }
        Quality.set(0, Quality.HIGH);
        return lines;
    }

    private static void everyPatternTheReviewsNamedDoesWhatItShould() {
        for (String line : table()) {
            check(line.startsWith("ok"), line);
        }
    }


    /** Each step Automatic took, and its pick, as played does, from the frames of a scene drawn at a rate that depends on the level alone. */
    private static List<String> playedFor(long from, float seconds, java.util.function.ToDoubleFunction<String> rateAt) {
        List<String> steps = new ArrayList<>();
        String level = Quality.name(0);
        long now = from;
        while (now - from < (long) (seconds * SECOND) && step(steps, level, now - from)) {
            now += (long) (SECOND / rateAt.applyAsDouble(level));
            Quality.frame(0, now, limit);
            level = Quality.name(0);
        }
        return steps;
    }

    private static void automaticSaysItsStepsAndLeavesItsPickToBeSaid() {
        Quality.set(0, Quality.HIGH);
        Quality.set(0, Quality.AUTOMATIC);
        Quality.decided();
        check(!Quality.stepToSay(), "nothing to say before a step");
        check(Arrays.equals(Quality.choices(0), new int[] {Quality.AUTOMATIC, Quality.LOW, Quality.MEDIUM, Quality.HIGH, Quality.CUSTOM}),
                "while it measures, the options screen lists Automatic with the other modes");
        long now = frames(SECOND, 30f, 3.5f);
        check(Quality.name(0).equals("Automatic (Medium)") && Quality.stepToSay(), "slow, it goes down to Medium and has that to say: " + Quality.name(0));
        Quality.stepSaid();
        check(!Quality.stepToSay(), "and once said, nothing more");
        check(Quality.choice(Quality.AUTOMATIC).equals("Automatic (Medium)") && Quality.choice(Quality.HIGH).equals("High"),
                "the options screen names Automatic with its level, and the other modes as they are");
        frames(now, 50f, 20f);
        check(Quality.row(0) == Quality.MEDIUM && Quality.decided() == 1 && Quality.decided() == 0 && !Quality.stepToSay(),
                "fifty frames a second hold Medium, which it picks, for Tracking to save and say once");
        check(Arrays.equals(Quality.choices(0), new int[] {Quality.LOW, Quality.MEDIUM, Quality.HIGH, Quality.CUSTOM}) && Quality.choice(Quality.AUTOMATIC).equals("Automatic"),
                "and from then on the options screen lists no Automatic to go back to");
        Quality.set(0, Quality.HIGH);
    }



    private static void automaticsFloorIsTheStockLookAndItPicksLow() {
        Quality.set(0, Quality.HIGH);
        Quality.set(0, Quality.AUTOMATIC);
        Quality.decided();
        List<String> down = playedFor(SECOND, 30f, level -> 20.0);
        check(down.equals(List.of("3 Medium", "8 Low", "13 Stock", "18 picked Low")),
                "at twenty frames a second it steps down a level every five seconds to Stock, and picks Low there: " + down);
        check(Quality.plain(0) && Quality.gamesPlants(0) && Quality.drawn(0).equals(Quality.preset(Quality.LOW)) && Quality.decided() == 1,
                "where it draws what the Low mode draws, the game's own look");
        Quality.set(0, Quality.HIGH);
    }

    private static void theGamesFrameLimitIsNotASlowPicture() {
        limit = 30f;
        Quality.set(0, Quality.HIGH);
        Quality.set(0, Quality.AUTOMATIC);
        long now = frames(SECOND, 29.5f, 15f);
        check(Quality.row(0) == Quality.HIGH, "a game held to thirty frames a second that draws 29.5 picks High: " + Quality.name(0));
        Quality.set(0, Quality.AUTOMATIC);
        now = frames(now, 20f, 3.5f);
        check(Quality.name(0).equals("Automatic (Medium)"), "and twenty under that limit is slow: " + Quality.name(0));
        limit = 60f;
        Quality.set(0, Quality.HIGH);
        Quality.set(0, Quality.AUTOMATIC);
        now = frames(now, 40f, 3.5f);
        check(Quality.name(0).equals("Automatic (Medium)"), "under a limit of sixty, forty is slow: " + Quality.name(0));
        Quality.set(0, Quality.HIGH);
        Quality.set(0, Quality.AUTOMATIC);
        frames(now, 59.5f, 15f);
        check(Quality.row(0) == Quality.HIGH, "and 59.5, as near sixty as such a game gets, holds High, which it picks: " + Quality.name(0));
        limit = 0f;
        Quality.set(0, Quality.HIGH);
        Quality.decided();
    }

    private static void aModeThePlayerChoseIsNeverStepped() {
        for (int mode : new int[] {Quality.LOW, Quality.MEDIUM, Quality.HIGH, Quality.CUSTOM}) {
            Quality.set(0, mode);
            Quality.Picture chosen = Quality.drawn(0);
            long now = frames(SECOND, 10f, 30f);
            frames(now, 200f, 300f);
            check(Quality.drawn(0).equals(chosen) && !Quality.stepToSay() && Quality.row(0) == mode,
                    Quality.name(0) + " is drawn as chosen, however slow or fast the frames");
        }
    }

    private static void theKeyGoesLowMediumHighCustomAndRound() {
        Quality.set(0, Quality.AUTOMATIC);
        check(Quality.next(0) == Quality.LOW, "the key goes from Automatic to Low");
        Quality.set(0, Quality.LOW);
        check(Quality.next(0) == Quality.MEDIUM, "from Low to Medium");
        Quality.set(0, Quality.MEDIUM);
        check(Quality.next(0) == Quality.HIGH, "from Medium to High");
        Quality.set(0, Quality.HIGH);
        check(Quality.next(0) == Quality.CUSTOM, "from High to Custom");
        Quality.set(0, Quality.CUSTOM);
        check(Quality.next(0) == Quality.LOW && Quality.name(0).equals("Custom"), "and from Custom round to Low, never to Automatic");
        check(Quality.words().equals("Automatic,Low,Medium,High,Custom"), "and the row's words are in that order, Automatic as a file not yet measured holds: " + Quality.words());
    }

    /**
     * First person's mode and the isometric view's are kept apart, the one in effect is first
     * person's while any view is in it, and the isometric view's Low, the game's own picture,
     * leaves first person's picture as it was.
     */
    private static void eachPlayerHasModesOfTheirOwn() {
        try {
            Quality.set(0, Quality.HIGH);
            Quality.inFirstPerson(0, true);
            Quality.set(1, Quality.LOW);
            Quality.setOverhead(1, Quality.MEDIUM);
            Quality.inFirstPerson(1, true);
            check(Quality.drawn(0).equals(Quality.HIGH_PICTURE) && Quality.drawn(1).equals(Quality.LOW_PICTURE) && Quality.scale(1) < Quality.scale(0)
                            && Quality.said(1).equals("First person (Low)"),
                    "the first player in High and the second in Low each draw their own: " + Quality.said(1));
            check(Quality.plain(1) && !Quality.plain(0) && !Quality.plainEverywhere() && !Quality.gamesPlantsEverywhere(),
                    "the second player's Low is the game's look for them alone, and what the mod made is still shown to the first");
            check(!Quality.smallerPictures(), "and the pictures they share are made full size for the first player's High");
            Quality.inFirstPerson(1, false);
            check(Quality.drawn(1).equals(Quality.MEDIUM_PICTURE) && Quality.drawn(0).equals(Quality.HIGH_PICTURE),
                    "the second player back in their isometric view draws its Medium, and the first player's view is as it was");
            check(Quality.next(1) == Quality.MEDIUM && Quality.nextOverhead(1) == Quality.HIGH && Quality.next(0) == Quality.CUSTOM,
                    "and each player's key goes on from their own mode");
            Quality.set(0, Quality.LOW);
            check(Quality.smallerPictures(), "with both in modes of half size pictures, the shared ones are half size");
            check(!Quality.plainEverywhere(), "and the second player's isometric Medium still shows what the mod made");
            Quality.setOverhead(1, Quality.LOW);
            check(Quality.plainEverywhere() && Quality.gamesPlantsEverywhere(), "with both in Low, nobody is shown what the mod made");
            Quality.set(1, Quality.HIGH);
            Quality.inFirstPerson(1, true);
            check(!Quality.smallerPictures() && !Quality.plainEverywhere(), "and one player's High has them made, full size, again");
            Quality.gone(1);
            check(Quality.smallerPictures() && Quality.plainEverywhere() && Quality.drawn(0).equals(Quality.LOW_PICTURE),
                    "a player gone from the screen no longer has them made for their mode");
            Quality.set(1, Quality.HIGH);
            Quality.setOverhead(1, Quality.LOW);
            Quality.inFirstPerson(1, false);
            check(!Quality.smallerPictures(), "a player in their isometric Low, which the game draws, has the shared pictures made full size for the first person High they are held ready for");
            Quality.gone(1);
            Quality.set(1, Quality.AUTOMATIC);
            Quality.set(0, Quality.AUTOMATIC);
            frames(SECOND, 20f, 10f);
            check(!Quality.name(0).equals("Automatic (High)"), "the first player's Automatic stepped down: " + Quality.name(0));
            Quality.set(0, Quality.LOW);
            Quality.inFirstPerson(1, true);
            check(Quality.name(1).equals("Automatic (High)"), "a player who comes to the screen in Automatic, with nobody there in it, begins it again at High: " + Quality.name(1));
            Quality.set(0, Quality.HIGH);
            Quality.setOverhead(0, Quality.HIGH);
            Quality.setOverhead(1, Quality.HIGH);
            Quality.setOverhead(2, Quality.HIGH);
            check(Quality.players(2) == 3 && Quality.row(0) == Quality.MEDIUM && Quality.overheadRow(0) == Quality.MEDIUM && Quality.row(1) == Quality.AUTOMATIC
                            && Quality.overheadRow(1) == Quality.MEDIUM,
                    "split screen lowers each player's High, and says whose");
            check(Quality.overheadRow(2) == Quality.HIGH, "and leaves the High of a player not at the screen");
            Style.set(1, Style.VIVID.ordinal());
            check(Style.of(0) == Style.BLEAK && Style.of(1) == Style.VIVID, "each player has a picture style of their own");
        } finally {
            Quality.players(0);
            Quality.gone(1);
            Quality.set(1, Quality.AUTOMATIC);
            Quality.setOverhead(1, Quality.LOW);
            Quality.setOverhead(2, Quality.LOW);
            Style.set(1, Style.BLEAK.ordinal());
            Quality.inFirstPerson(0, false);
            Quality.set(0, Quality.HIGH);
            Quality.setOverhead(0, Quality.LOW);
        }
    }

    private static void eachViewKeepsAModeOfItsOwn() {
        Quality.set(0, Quality.HIGH);
        Quality.setOverhead(0, Quality.LOW);
        check(!Quality.firstPersonInEffect(0) && Quality.overheadPlain(0) && Quality.drawn(0).equals(Quality.HIGH_PICTURE)
                        && Quality.said(0).equals("Isometric (Low)"),
                "in the isometric view's Low the game draws that view, and the picture stays first person's High: " + Quality.said(0));
        Quality.setOverhead(0, Quality.MEDIUM);
        check(!Quality.overheadPlain(0) && Quality.drawn(0).equals(Quality.MEDIUM_PICTURE) && Quality.name(0).equals("High"),
                "in its Medium the isometric view is drawn as Medium, and first person's mode stays High");
        Quality.inFirstPerson(0, true);
        check(Quality.firstPersonInEffect(0) && Quality.drawn(0).equals(Quality.HIGH_PICTURE) && Quality.said(0).equals("First person (High)"),
                "the view going to first person puts first person's mode in effect: " + Quality.said(0));
        Quality.set(0, Quality.LOW);
        check(Quality.plain(0) && Quality.overheadRow(0) == Quality.MEDIUM, "first person's mode set there leaves the isometric view's");
        Quality.inFirstPerson(0, false);
        check(!Quality.firstPersonInEffect(0) && Quality.drawn(0).equals(Quality.MEDIUM_PICTURE) && Quality.name(0).equals("Low"),
                "with the view back in the isometric view, its Medium is drawn again and first person's Low is kept");
        Quality.setOverhead(0, Quality.CUSTOM);
        check(Quality.drawn(0).equals(Quality.custom()) && Quality.said(0).equals("Isometric (Custom)"), "its Custom draws the rows of [Graphics]");
        check(Quality.nextOverhead(0) == Quality.LOW, "and the key goes from Custom round to Low, with no Automatic");
        Quality.setOverhead(0, Quality.LOW);
        check(Quality.nextOverhead(0) == Quality.MEDIUM && Quality.drawn(0).equals(Quality.LOW_PICTURE),
                "from Low to Medium, and in Low the picture is first person's again, its Low");
        Quality.setOverhead(0, Quality.MEDIUM);
        check(Quality.nextOverhead(0) == Quality.HIGH, "from Medium to High");
        Quality.setOverhead(0, Quality.HIGH);
        check(Quality.nextOverhead(0) == Quality.CUSTOM && Quality.drawn(0).equals(Quality.HIGH_PICTURE), "and from High to Custom");
        check(Quality.overheadWords().equals("Low,Medium,High,Custom"), "and the isometric row's words are in that order: " + Quality.overheadWords());
        boolean refused = false;
        try {
            Quality.setOverhead(0, Quality.AUTOMATIC);
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused && Quality.overheadRow(0) == Quality.HIGH, "the isometric view has no Automatic");
        Quality.setOverhead(0, Quality.LOW);
        Quality.set(0, Quality.HIGH);
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
        return Quality.plain(0) + " " + Quality.gamesPlants(0) + " " + Quality.scale(0) + " " + Quality.shadowSize(0) + " " + Quality.sight(0) + " "
                + reaches() + " " + Quality.glassRain(0) + " " + Quality.shadedAndLit(0) + " " + Quality.reflections(0) + " " + Quality.surfacesMirror(0) + " "
                + Quality.smallerPictures() + " " + Quality.crownsToSight(0) + " " + Quality.relief(0) + " " + Quality.wholeFigureShadows(0) + " " + Quality.sightBehind()
                + " " + Quality.farTreeDistance(0) + " " + Quality.frameBlending(0) + " " + Quality.grassDensity(0) + " " + Quality.grassDistance(0)
                + " " + Quality.lightDistance(0) + " " + Quality.reflectionScale(0) + " " + Quality.corpseModels(0) + " " + Quality.antialiasing(0);
    }

    /**
     * How far people and props are drawn now, beside a vehicle and ahead of it, from a viewer
     * riding one that faces along x: the furthest whole tile, or "all" where nothing within 200
     * is left out.
     */
    static String reaches() {
        DrawDistance reach = new DrawDistance();
        reach.take(0, 0f, 0f, 1f, 0f, false);
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

    /** Runs checks of what view 0 draws with it in first person, where the pictures the views share are made for it. */
    private static void inFirstPerson(Runnable checks) {
        Quality.inFirstPerson(0, true);
        try {
            checks.run();
        } finally {
            Quality.inFirstPerson(0, false);
        }
    }

    private static void customDrawsWhatEachPresetDrewFromItsRows() {
        String[] expected = {
            "true true 0.62 2048 48 all/all 0 false false false true false false false 48 300 false 40 16 20 0.5 false 1",
            "false false 0.8 2048 60 all/all 1 true true true true false false false 48 150 true 40 16 30 0.5 false 1",
            "false false 1.0 4096 80 all/all 2 true true true false true true true 48 400 true 100 16 60 0.75 true 1",
        };
        for (int mode = Quality.LOW; mode <= Quality.HIGH; mode++) {
            Quality.set(0, mode);
            String preset = answers();
            check(preset.equals(expected[mode - Quality.LOW]), Quality.name(0) + " draws as it always has: " + preset);
            Quality.custom(Config.picture(Config.values(Quality.preset(mode))));
            Quality.set(0, Quality.CUSTOM);
            // Custom always draws what the mod made: Low's rows there are Low's cost without the game's own look.
            String custom = mode == Quality.LOW ? "false" + preset.substring("true".length()) : preset;
            check(answers().equals(custom), "Custom with the rows " + Quality.preset(mode) + " written as the options screen holds them draws as "
                    + Quality.name(0) + " did, with what the mod made: " + answers());
        }
    }

    /** Each part of Custom's picture changed alone, from High's, changes the one answer that part is and no other. */
    private static void eachRowOfCustomReachesItsOwnAnswer() {
        Quality.custom(Quality.HIGH_PICTURE);
        Quality.set(0, Quality.CUSTOM);
        String high = answers();
        double[] changed = {Quality.TREES_NEAR, 40, 30, 0.7, 40, 30, 300, 0, 0, 0, 0.5, 0, 0, 0, 0, 0, Quality.ANTIALIASING_HIGH, 25, 0};
        String[] answer = {
            "false false 1.0 4096 80 all/all 2 true true true false false true true 48 400 true 100 16 60 0.75 true 1",
            "false false 1.0 4096 80 all/all 2 true true true false true true true 48 400 true 40 16 60 0.75 true 1",
            "false false 1.0 4096 80 all/all 2 true true true false true true true 48 400 true 100 30 60 0.75 true 1",
            "false false 0.7 4096 80 all/all 2 true true true false true true true 48 400 true 100 16 60 0.75 true 1",
            "false false 1.0 4096 40 all/all 2 true true true false true true true 48 400 true 100 16 60 0.75 true 1",
            "false false 1.0 4096 80 30/all 2 true true true false true true true 48 400 true 100 16 60 0.75 true 1",
            "false false 1.0 4096 80 all/all 2 true true true false true true true 48 300 true 100 16 60 0.75 true 1",
            "false false 1.0 4096 80 all/all 0 true true true false true true true 48 400 true 100 16 60 0.75 true 1",
            "false false 1.0 4096 80 all/all 2 false true false false true true true 48 400 true 100 16 60 0.75 true 1",
            "false false 1.0 4096 80 all/all 2 true false false false true true true 48 400 true 100 16 60 0.75 true 1",
            "false false 1.0 4096 80 all/all 2 true true true false true true true 48 400 true 100 16 60 0.5 true 1",
            "false false 1.0 4096 80 all/all 2 true true true true true true true 48 400 true 100 16 60 0.75 true 1",
            "false false 1.0 4096 80 all/all 2 true true true false true false true 48 400 true 100 16 60 0.75 true 1",
            "false false 1.0 2048 80 all/all 2 true true true false true true true 48 400 true 100 16 60 0.75 true 1",
            "false false 1.0 4096 80 all/all 2 true true true false true true false 48 400 true 100 16 60 0.75 true 1",
            "false false 1.0 4096 80 all/all 2 true true true false true true true 48 400 false 100 16 60 0.75 true 1",
            "false false 1.0 4096 80 all/all 2 true true true false true true true 48 400 true 100 16 60 0.75 true 3",
            "false false 1.0 4096 80 all/all 2 true true true false true true true 48 400 true 100 16 25 0.75 true 1",
            "false false 1.0 4096 80 all/all 2 true true true false true true true 48 400 true 100 16 60 0.75 false 1",
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
        Quality.set(0, Quality.CUSTOM);
        Quality.custom(mine);
        check(Quality.sight(0) == 56 && Quality.drawn(0).equals(mine), "Custom draws the rows given it");
        Quality.set(0, Quality.LOW);
        check(Quality.sight(0) == 48 && Quality.plain(0), "Low draws Low's picture");
        Quality.set(0, Quality.MEDIUM);
        Quality.set(0, Quality.HIGH);
        Quality.set(0, Quality.AUTOMATIC);
        check(Quality.custom().equals(mine), "and Low, Medium, High and Automatic leave Custom's rows as they were");
        Quality.set(0, Quality.CUSTOM);
        check(Quality.sight(0) == 56 && !Quality.crownsToSight(0) && Quality.drawn(0).equals(mine), "so back in Custom it is the player's own again");
        Quality.set(0, Quality.HIGH);
        Quality.custom(Quality.HIGH_PICTURE);
        check(Quality.sight(0) == 80, "and rows given while another mode is drawn are kept for Custom and draw nothing yet");
        Quality.custom(mine);
        check(Quality.sight(0) == 80 && Quality.custom().equals(mine), "in either direction");
    }
}
