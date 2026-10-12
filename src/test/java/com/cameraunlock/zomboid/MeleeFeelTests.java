package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of how a blow that lands stops in the picture. */
final class MeleeFeelTests {
    private MeleeFeelTests() {}

    static void run() {
        Checks.run("aHeavierBlowStopsLonger", MeleeFeelTests::aHeavierBlowStopsLonger);
        Checks.run("aBlowThatLandsStandsForItsTimeThenCatchesUp", MeleeFeelTests::aBlowThatLandsStandsForItsTimeThenCatchesUp);
        Checks.run("aMissDoesNotStop", MeleeFeelTests::aMissDoesNotStop);
        Checks.run("aStopPausedStaysAndGoesOnAfter", MeleeFeelTests::aStopPausedStaysAndGoesOnAfter);
        Checks.run("aStopInAFastAttackStillRejoinsBeforeItIsHeldAgain", MeleeFeelTests::aStopInAFastAttackStillRejoinsBeforeItIsHeldAgain);
        Checks.run("aStopEndsWithItsAttack", MeleeFeelTests::aStopEndsWithItsAttack);
    }

    private static final float FRAME = 1f / 60f, LANDS = 0.25f, PACE = 1f;

    /** Steps a clock through an attack at PACE a second, `stop` seconds stopped if it lands with one, and gives what it showed past landing each frame, and what it would have shown without the stop. */
    private static float[][] play(AttackClock clock, float pace, float stop) {
        int frames = (int) (1f / (pace * FRAME)) + 1;
        float[][] shown = new float[2][frames];
        for (int frame = 0; frame < frames; frame++) {
            float fraction = frame * pace * FRAME;
            clock.step(true, fraction, LANDS, FRAME);
            if (clock.landed && stop > 0f) {
                clock.stop(stop);
            }
            float before = fraction - pace * FRAME;
            shown[0][frame] = clock.past();
            shown[1][frame] = Math.clamp((before - LANDS) / (AttackClock.HELD_AGAIN - LANDS), 0f, 1f);
        }
        return shown;
    }

    /** A baseball bat's weight in the game. */
    private static final float BAT_WEIGHT = 2f;

    private static void aHeavierBlowStopsLonger() {
        check(HitStop.seconds(0.2f) == HitStop.LIGHTEST_STOPS && HitStop.seconds(9f) == HitStop.HEAVIEST_STOPS, "a stop is never shorter or longer than its bounds");
        check(HitStop.seconds(BAT_WEIGHT) > HitStop.seconds(0.5f) && HitStop.seconds(5f) > HitStop.seconds(BAT_WEIGHT),
                "a bat stops longer than a knife and a sledgehammer longer than a bat: " + HitStop.seconds(0.5f) + " " + HitStop.seconds(BAT_WEIGHT) + " " + HitStop.seconds(5f));
    }

    private static void aBlowThatLandsStandsForItsTimeThenCatchesUp() {
        float stop = HitStop.seconds(BAT_WEIGHT);
        float[][] shown = play(new AttackClock(), PACE, stop);
        float held = 0f;
        boolean moved = false, rejoined = false;
        int frames = shown[0].length;
        for (int frame = 1; frame < frames; frame++) {
            float beyond = shown[1][frame] * (AttackClock.HELD_AGAIN - LANDS) / PACE;
            if (beyond > 0f && beyond < stop - 1e-4f) {
                check(shown[0][frame] == 0f, "while the stop lasts what struck stands where it landed: " + beyond + " s past it, shown " + shown[0][frame]);
                held = beyond;
            }
            check(shown[0][frame] >= shown[0][frame - 1], "it goes one way only");
            check(shown[0][frame] <= shown[1][frame] + 1e-6f, "and is never ahead of the game's animation");
            moved |= shown[0][frame] > 0f;
            if (beyond > stop + HitStop.CATCHES_UP + 1e-4f) {
                check(Math.abs(shown[0][frame] - shown[1][frame]) < 1e-5f, "once it has caught up it is with the animation: " + shown[0][frame] + " against " + shown[1][frame]);
                rejoined = true;
            }
        }
        check(held > stop - 2f * FRAME, "it stood for the stop's length, to a frame: " + held + " of " + stop);
        check(moved && rejoined, "and moved on after, and caught up");
        check(shown[0][frames - 1] == 1f, "and is held again when the attack would have been without it");
    }

    private static void aMissDoesNotStop() {
        float[][] shown = play(new AttackClock(), PACE, 0f);
        for (int frame = 0; frame < shown[0].length; frame++) {
            check(Math.abs(shown[0][frame] - shown[1][frame]) < 1e-5f, "a blow that lands on nothing goes on with the animation");
        }
    }

    private static void aStopPausedStaysAndGoesOnAfter() {
        AttackClock clock = new AttackClock();
        float fraction = 0f;
        float stop = HitStop.seconds(5f);
        while (!clock.landed) {
            clock.step(true, fraction, LANDS, FRAME);
            fraction += PACE * FRAME;
        }
        clock.stop(stop);
        for (int frame = 0; frame < 600; frame++) {
            clock.step(true, fraction, LANDS, FRAME);
            check(clock.past() == 0f, "paused in the stop, it stands where it struck however long the pause");
        }
        int frames = 0;
        while (clock.past() == 0f && frames < 100) {
            fraction += PACE * FRAME;
            clock.step(true, fraction, LANDS, FRAME);
            frames++;
        }
        check(frames > 1 && frames <= Math.ceil(stop / FRAME) + 1, "and once the game goes on it stands only for what was left of the stop: " + frames + " frames of " + stop + " s");
    }

    private static void aStopInAFastAttackStillRejoinsBeforeItIsHeldAgain() {
        float pace = 4f;
        float[][] shown = play(new AttackClock(), pace, HitStop.HEAVIEST_STOPS);
        boolean stood = false;
        for (int frame = 0; frame < shown[0].length; frame++) {
            float share = shown[1][frame];
            stood |= share > 0f && shown[0][frame] == 0f;
            if (share >= HitStop.ROOM + 1e-3f) {
                check(Math.abs(shown[0][frame] - share) < 1e-5f, "it is back with the animation within " + HitStop.ROOM + " of the way to being held again: at " + share + " shown " + shown[0][frame]);
            }
        }
        check(stood, "and still stood a while");
    }

    private static void aStopEndsWithItsAttack() {
        AttackClock clock = new AttackClock();
        float fraction = 0f;
        while (!clock.landed) {
            clock.step(true, fraction, LANDS, FRAME);
            fraction += PACE * FRAME;
        }
        clock.stop(HitStop.HEAVIEST_STOPS);
        clock.step(false, 0f, 0f, FRAME);
        boolean over = false;
        for (int frame = 0; frame < 120 && !over; frame++) {
            clock.step(false, 0f, 0f, FRAME);
            over = !clock.under();
        }
        check(over, "an attack let go of in its stop runs on to its end and is over");
        clock.step(true, 0f, LANDS, FRAME);
        clock.step(true, 0.3f, LANDS, FRAME);
        clock.step(true, 0.35f, LANDS, FRAME);
        check(clock.past() > 0f, "and the next attack, landing on nothing, has no stop of the last one's");
    }
}
