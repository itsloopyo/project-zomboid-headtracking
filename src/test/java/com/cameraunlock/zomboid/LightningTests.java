package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/**
 * Tests of lightning: how a strike runs in time, the shape of its bolt, what it does to the
 * sky and the light an eye sees, how that falls off with distance, and that the game's own
 * look and a rumble with lightning switched off have none of it.
 */
final class LightningTests {
    private LightningTests() {}

    private static final long SECOND = 1_000_000_000L;

    static void run() {
        int mode = Quality.row(0);
        Quality.Picture custom = Quality.custom();
        Quality.set(0, Quality.HIGH);
        try {
            Checks.run("aStrikeIsFaintUntilItsLeaderReachesTheGroundThenPeaks", LightningTests::aStrikeIsFaintUntilItsLeaderReachesTheGroundThenPeaks);
            Checks.run("onlyTheFirstStrokeGoesDownTheBranches", LightningTests::onlyTheFirstStrokeGoesDownTheBranches);
            Checks.run("aStrikeIsOutBeforeItIsLetGo", LightningTests::aStrikeIsOutBeforeItIsLetGo);
            Checks.run("theBoltRunsFromTheCloudToUnderTheGroundInOneLine", LightningTests::theBoltRunsFromTheCloudToUnderTheGroundInOneLine);
            Checks.run("branchesLeaveTheChannelGoingDownAndAreDimmer", LightningTests::branchesLeaveTheChannelGoingDownAndAreDimmer);
            Checks.run("theSameStrikeMakesTheSameBolt", LightningTests::theSameStrikeMakesTheSameBolt);
            Checks.run("lightFallsWithDistanceAndNoneIsSeenPastTheGamesReach", LightningTests::lightFallsWithDistanceAndNoneIsSeenPastTheGamesReach);
            Checks.run("aStrikeTakesTheLightOverWhileItIsBrighterThanTheMoon", LightningTests::aStrikeTakesTheLightOverWhileItIsBrighterThanTheMoon);
            Checks.run("aFaintFarStrikeLeavesTheSunsLightAndShadows", LightningTests::aFaintFarStrikeLeavesTheSunsLightAndShadows);
            Checks.run("aFlickerInTheCloudLightsTheSkyWithNoBolt", LightningTests::aFlickerInTheCloudLightsTheSkyWithNoBolt);
            Checks.run("thunderWithNoLightningAllowedShowsNothing", LightningTests::thunderWithNoLightningAllowedShowsNothing);
            Checks.run("noMoreStrikesAreKeptThanMost", LightningTests::noMoreStrikesAreKeptThanMost);
            Checks.run("theGamesOwnLookHasNoLightning", LightningTests::theGamesOwnLookHasNoLightning);
        } finally {
            Lightning.clear();
            Quality.custom(custom);
            Quality.set(0, mode);
        }
    }

    private static Lightning.Strike bolt(long seed) {
        return Lightning.make(100.5f, 200.5f, 0L, true, seed);
    }

    private static void aStrikeIsFaintUntilItsLeaderReachesTheGroundThenPeaks() {
        for (long seed = 1; seed <= 20; seed++) {
            Lightning.Strike strike = bolt(seed);
            float first = strike.strokes[0];
            check(Math.abs(first - Lightning.LEADER) < 1e-6f, "the first stroke falls when the leader is down");
            check(strike.channel(first * 0.5f) <= Lightning.LEADER_LIGHT + 1e-6f, "the leader is faint: " + strike.channel(first * 0.5f));
            check(strike.reached(first * 0.5f) > 0.4f && strike.reached(first * 0.5f) < 0.6f, "half way through, the leader is half way down");
            check(strike.reached(first) == 1f, "at the first stroke the whole channel is lit");
            float peak = strike.channel(first);
            check(peak >= 1f && peak < 1.5f, "the first stroke is the brightest light, about 1: " + peak);
            check(strike.channel(first + 0.1f) < 0.5f * peak || strike.strokes.length > 1 && strike.strokes[1] < first + 0.1f,
                    "a tenth of a second on, the stroke has faded unless another has fallen");
            check(strike.strokes.length >= 2 && strike.strokes.length <= 4, "two to four strokes: " + strike.strokes.length);
            for (int i = 1; i < strike.strokes.length; i++) {
                check(strike.strokes[i] > strike.strokes[i - 1] + 0.04f, "the strokes fall apart from each other");
                check(strike.peaks[i] < 1f, "the first stroke is the brightest");
            }
        }
    }

    private static void onlyTheFirstStrokeGoesDownTheBranches() {
        for (long seed = 1; seed <= 20; seed++) {
            Lightning.Strike strike = bolt(seed);
            float second = strike.strokes[1];
            float main = strike.channel(second), branches = strike.branches(second);
            check(branches < 0.6f * main, "at the second stroke the branches stay dim beside the channel: " + branches + " against " + main);
            check(strike.branches(strike.strokes[0]) >= 1f, "the first stroke lights the branches whole");
        }
    }

    private static void aStrikeIsOutBeforeItIsLetGo() {
        for (long seed = 1; seed <= 40; seed++) {
            for (boolean hasBolt : new boolean[] {true, false}) {
                Lightning.Strike strike = Lightning.make(0f, 0f, 0L, hasBolt, seed);
                check(strike.light(Lightning.LASTS) < 0.01f, "nothing is left of a strike when it is let go: " + strike.light(Lightning.LASTS));
                check(strike.light(-0.01f) == 0f, "nothing before it");
            }
        }
    }

    private static void theBoltRunsFromTheCloudToUnderTheGroundInOneLine() {
        for (long seed = 1; seed <= 10; seed++) {
            Lightning.Strike strike = bolt(seed);
            Lightning.Bolt shape = strike.bolt;
            check(shape.topUp == Lightning.CLOUD_BASE, "the bolt leaves the cloud's base");
            check(shape.footUp == -Lightning.UNDER_GROUND && shape.footX == strike.x && shape.footY == strike.y, "its foot is under where it strikes");
            float lean = (float) Math.hypot(shape.topX - strike.x, shape.topY - strike.y);
            check(lean < Lightning.CLOUD_BASE * 0.4f, "it leans no more than a bolt does: " + lean);
            float[] ends = new float[shape.vertices * Lightning.FLOATS], others = new float[ends.length], kinds = new float[ends.length];
            shape.ends.get(0, ends);
            shape.others.get(0, others);
            shape.kinds.get(0, kinds);
            float lastX = shape.topX, lastY = shape.topY, lastUp = shape.topUp;
            int mainStretches = 0;
            for (int v = 0; v < shape.vertices; v += 4) {
                int at = v * Lightning.FLOATS;
                if (kinds[at + 2] != 0f) {
                    continue;
                }
                check(ends[at] == lastX && ends[at + 1] == lastY && ends[at + 2] == lastUp, "each stretch of the channel starts where the one above ended");
                lastX = others[at];
                lastY = others[at + 1];
                lastUp = others[at + 2];
                mainStretches++;
            }
            check(lastX == shape.footX && lastY == shape.footY && lastUp == shape.footUp, "the channel ends at its foot");
            check(mainStretches == 512, "the channel is 512 stretches: " + mainStretches);
            for (int v = 0; v < shape.vertices; v++) {
                int at = v * Lightning.FLOATS;
                check(Math.abs(ends[at + 3]) == 1f, "each vertex is on one side of its ribbon");
                check(ends[at + 2] < Lightning.CLOUD_BASE * 1.25f && ends[at + 2] > -Lightning.UNDER_GROUND - Lightning.CLOUD_BASE * 0.25f,
                        "the bolt stays between the cloud and the ground: " + ends[at + 2]);
                check(kinds[at] >= 0f, "nothing is lit before the leader sets off");
            }
        }
    }

    private static void branchesLeaveTheChannelGoingDownAndAreDimmer() {
        for (long seed = 1; seed <= 10; seed++) {
            Lightning.Bolt shape = bolt(seed).bolt;
            float[] ends = new float[shape.vertices * Lightning.FLOATS], others = new float[ends.length], kinds = new float[ends.length];
            shape.ends.get(0, ends);
            shape.others.get(0, others);
            shape.kinds.get(0, kinds);
            java.util.HashSet<String> channel = new java.util.HashSet<>();
            int branches = 0;
            for (int v = 0; v < shape.vertices; v += 4) {
                int at = v * Lightning.FLOATS;
                float rank = kinds[at + 2];
                if (rank == 0f) {
                    channel.add(ends[at] + "," + ends[at + 1] + "," + ends[at + 2]);
                    continue;
                }
                check(kinds[at + 1] < 1f, "a branch is dimmer than the channel");
                check(kinds[at] >= 0f, "the leader reaches a branch no sooner than the channel's top");
                int before = at - 4 * Lightning.FLOATS;
                boolean starts = others[before] != ends[at] || others[before + 1] != ends[at + 1] || others[before + 2] != ends[at + 2];
                if (starts && rank == 1f) {
                    check(channel.contains(ends[at] + "," + ends[at + 1] + "," + ends[at + 2]), "a branch leaves from a point of the channel");
                    branches++;
                }
                if (starts) {
                    int last = at;
                    while (last + 4 * Lightning.FLOATS < kinds.length && kinds[last + 4 * Lightning.FLOATS + 2] == rank
                            && others[last] == ends[last + 4 * Lightning.FLOATS]) {
                        last += 4 * Lightning.FLOATS;
                    }
                    check(others[last + 2] < ends[at + 2], "a branch goes down");
                }
            }
            check(branches >= 5 && branches <= 10, "five to ten branches: " + branches);
        }
    }

    private static void theSameStrikeMakesTheSameBolt() {
        Lightning.Bolt one = bolt(7).bolt, two = bolt(7).bolt, other = bolt(8).bolt;
        check(one.vertices == two.vertices && one.ends.equals(two.ends), "one seed, one bolt");
        check(!one.ends.equals(other.ends), "another seed, another bolt");
    }

    private static void lightFallsWithDistanceAndNoneIsSeenPastTheGamesReach() {
        float before = Float.MAX_VALUE;
        for (float distance = 0f; distance < 8000f; distance += 50f) {
            float near = Lightning.nearness(distance);
            check(near < before || near == 0f && before == 0f, "nearer is brighter");
            check(near >= 0f && near <= 1f, "between nought and whole");
            before = near;
        }
        check(Lightning.nearness(0f) == 1f, "whole under the bolt");
        check(Math.abs(Lightning.nearness(Lightning.HALF_AT) - 0.5f * (1f - Lightning.HALF_AT / Lightning.SEEN_WITHIN)) < 1e-5f, "about half at HALF_AT");
        check(Lightning.nearness(Lightning.SEEN_WITHIN) == 0f, "none at the game's reach");
    }

    /** A night sky's state, the moon's light as SkyState.capture leaves it. */
    private static SkyState night() {
        SkyState sky = new SkyState();
        sky.lightX = 0f;
        sky.lightY = 0.3f;
        sky.lightUp = 0.95f;
        sky.lightRed = 0.1f;
        sky.lightGreen = 0.13f;
        sky.lightBlue = 0.2f;
        return sky;
    }

    private static void aStrikeTakesTheLightOverWhileItIsBrighterThanTheMoon() {
        Lightning.clear();
        long now = 50 * SECOND;
        Lightning.struck(400, 0, true, false, now);
        Lightning.Strike strike = Lightning.showing()[0];
        long peak = now + (long) (strike.strokes[0] * SECOND);
        SkyState sky = night();
        Lightning.see(0, sky, 0f, 0f, 1.6f, peak);
        check(sky.seenCount == 1 && sky.seen[0].bolt() == strike.bolt, "the bolt is drawn");
        check(sky.seen[0].reached() == 1f && sky.seen[0].channel() > 0.9f, "at its peak, whole and bright");
        check(sky.lightUp >= 0.22f - 1e-5f && sky.lightUp < 0.95f, "the light comes from the bolt's side, not from overhead: " + sky.lightUp);
        float litX = strike.topX + (strike.x - strike.topX) * 0.7f, litY = strike.topY + (strike.y - strike.topY) * 0.7f;
        float flat = (float) Math.hypot(sky.lightX, sky.lightY);
        check((sky.lightX * litX + sky.lightY * litY) / (flat * Math.hypot(litX, litY)) > 0.999f,
                "the light comes from the lower part of the bolt: " + sky.lightX + ", " + sky.lightY);
        check(sky.lightRed > 1f, "far brighter than the moon: " + sky.lightRed);
        check(sky.flashBlue > 0f && sky.flashBlue >= sky.flashRed, "the sky is lit, a cold white");
        check(sky.burst > 0f && sky.burstUp > 0f, "the cloud glows above the eye's horizon");

        SkyState before = night();
        Lightning.see(0, before, 0f, 0f, 1.6f, now + (long) (strike.strokes[0] * 0.5f * SECOND));
        check(before.lightRed == 0.1f && before.lightUp == 0.95f, "while the leader comes down, the moon still lights the world");
        check(before.seenCount == 1 && before.seen[0].reached() < 1f, "and the leader is drawn part way");

        SkyState after = night();
        Lightning.see(0, after, 0f, 0f, 1.6f, now + (long) (Lightning.LASTS * SECOND) + SECOND / 100);
        check(after.lightRed == 0.1f && after.seenCount == 0 && after.flashBlue == 0f && after.burst == 0f, "once it is over, the night is as it was");
    }

    private static void aFaintFarStrikeLeavesTheSunsLightAndShadows() {
        Lightning.clear();
        long now = 70 * SECOND;
        Lightning.struck(6500, 0, true, false, now);
        Lightning.Strike strike = Lightning.showing()[0];
        SkyState sky = new SkyState();
        sky.lightX = 0.3f;
        sky.lightY = 0.2f;
        sky.lightUp = 0.93f;
        sky.lightRed = sky.lightGreen = sky.lightBlue = 1f;
        Lightning.see(0, sky, 0f, 0f, 1.6f, now + (long) (strike.strokes[0] * SECOND));
        check(sky.lightUp == 0.93f && sky.lightRed == 1f, "the sun stays the light");
        check(sky.seenCount == 1, "the bolt is still seen");
        check(sky.flashBlue > 0f && sky.flashBlue < 0.2f, "and lights the sky a little: " + sky.flashBlue);
    }

    private static void aFlickerInTheCloudLightsTheSkyWithNoBolt() {
        Lightning.clear();
        long now = 90 * SECOND;
        Lightning.struck(300, 300, false, true, now);
        Lightning.Strike strike = Lightning.showing()[0];
        check(strike.bolt == null, "no bolt");
        SkyState sky = night();
        Lightning.see(0, sky, 0f, 0f, 1.6f, now + (long) (strike.strokes[0] * SECOND));
        check(sky.seenCount == 0, "nothing drawn");
        check(sky.burst > 0f && sky.flashBlue > 0f, "the cloud glows and the sky lightens");
        check(sky.lightRed == 0.1f, "no shadows from it");
    }

    private static void thunderWithNoLightningAllowedShowsNothing() {
        Lightning.clear();
        Lightning.struck(10, 10, false, false, 100 * SECOND);
        check(Lightning.showing().length == 0, "thunder alone shows nothing");
    }

    private static void noMoreStrikesAreKeptThanMost() {
        Lightning.clear();
        long now = 200 * SECOND;
        for (int i = 0; i < Lightning.MOST + 3; i++) {
            Lightning.struck(i * 10, 0, true, false, now + i);
        }
        Lightning.Strike[] kept = Lightning.showing();
        check(kept.length == Lightning.MOST, "no more than MOST");
        check(kept[kept.length - 1].x == (Lightning.MOST + 2) * 10 + 0.5f, "the newest are kept");
        Lightning.struck(5, 5, true, false, now + (long) (Lightning.LASTS * SECOND) + 10);
        check(Lightning.showing().length == 1, "those that are over are let go");
    }

    private static void theGamesOwnLookHasNoLightning() {
        Lightning.clear();
        long now = 300 * SECOND;
        Lightning.struck(200, 0, true, false, now);
        Lightning.Strike strike = Lightning.showing()[0];
        Quality.set(0, Quality.LOW);
        try {
            check(Quality.plain(0), "Low is the game's own look");
            SkyState sky = night();
            Lightning.see(0, sky, 0f, 0f, 1.6f, now + (long) (strike.strokes[0] * SECOND));
            check(sky.seenCount == 0 && sky.flashBlue == 0f && sky.burst == 0f && sky.lightRed == 0.1f, "no bolt, no flash, no glow, no light");
        } finally {
            Quality.set(0, Quality.HIGH);
        }
    }
}
