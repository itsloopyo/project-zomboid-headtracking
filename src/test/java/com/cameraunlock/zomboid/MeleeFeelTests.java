package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.io.IOException;
import java.io.InputStream;

/** Tests of how a blow that lands stops in the picture, and of the arms in a shove or a stamp with nothing in hand. */
final class MeleeFeelTests {
    private MeleeFeelTests() {}

    static void run() throws IOException {
        ArmModel model;
        try (InputStream packed = MeleeFeelTests.class.getResourceAsStream("/models/arms.mesh.gz")) {
            model = new ArmModel(packed);
        }
        Checks.run("aHeavierBlowStopsLonger", MeleeFeelTests::aHeavierBlowStopsLonger);
        Checks.run("aBlowThatLandsStandsForItsTimeThenCatchesUp", MeleeFeelTests::aBlowThatLandsStandsForItsTimeThenCatchesUp);
        Checks.run("aMissDoesNotStop", MeleeFeelTests::aMissDoesNotStop);
        Checks.run("aStopPausedStaysAndGoesOnAfter", MeleeFeelTests::aStopPausedStaysAndGoesOnAfter);
        Checks.run("aStopInAFastAttackStillRejoinsBeforeItIsHeldAgain", MeleeFeelTests::aStopInAFastAttackStillRejoinsBeforeItIsHeldAgain);
        Checks.run("aStopEndsWithItsAttack", MeleeFeelTests::aStopEndsWithItsAttack);
        Checks.run("aHitchAroundTheLandingDoesNotChangeTheStop", MeleeFeelTests::aHitchAroundTheLandingDoesNotChangeTheStop);
        Checks.run("aShoveLeadsWithThePalmsIntoThePicture", MeleeFeelTests::aShoveLeadsWithThePalmsIntoThePicture);
        Checks.run("bareHandsMoveNoFarInATinyStep", MeleeFeelTests::bareHandsMoveNoFarInATinyStep);
        Checks.run("bothArmsArePlacedThroughAShoveAndAStamp", () -> bothArmsArePlacedThroughAShoveAndAStamp(model));
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

    /**
     * The game's animation moves on by the length of each frame. Frames are a 60th of a second
     * but for one of a tenth, on the frame the attack lands or the one before it: the stop is as
     * long in the animation's own time as with even frames.
     */
    private static void aHitchAroundTheLandingDoesNotChangeTheStop() {
        float stop = HitStop.seconds(5f);
        boolean onLanding = false, before = false;
        for (int hitch = 5; hitch < 25; hitch++) {
            AttackClock clock = new AttackClock();
            float fraction = 0f, read = 0f;
            int landedOn = -1;
            for (int frame = 0; fraction < 1f; frame++) {
                float seconds = frame == hitch ? 0.1f : FRAME;
                read = fraction;
                fraction += frame == 0 ? 0f : PACE * seconds;
                clock.step(true, fraction, LANDS, seconds);
                if (clock.landed) {
                    clock.stop(stop);
                    landedOn = frame;
                    onLanding |= hitch == frame;
                    before |= hitch == frame - 1;
                }
                if (landedOn >= 0) {
                    float expected = Math.clamp(HitStop.shown(read - LANDS, stop * PACE, HitStop.CATCHES_UP * PACE) / (AttackClock.HELD_AGAIN - LANDS), 0f, 1f);
                    check(Math.abs(clock.past() - expected) < 1e-4f, "with a hitch on frame " + hitch + " and the landing on " + landedOn + ", frame " + frame + " shows "
                            + clock.past() + " where even frames show " + expected);
                }
            }
        }
        check(onLanding && before, "a hitch fell on a landing's frame and on the frame before one");
    }

    /** A place in the aim's axes in the picture of a view this tall, as tangents: how far down and across it is seen. */
    private static float down(float[] hand) {
        return -hand[1] / -hand[2];
    }

    private static void aShoveLeadsWithThePalmsIntoThePicture() {
        float[] hand = new float[BareHands.SAYS];
        float tanHalf = (float) Math.tan(Math.toRadians(35));
        BareHands.pose(BareHands.SHOVED, 0f, 0f, false, hand);
        check(-hand[2] < 0.3f && down(hand) > tanHalf, "before a shove the hands are under the picture: " + down(hand));
        BareHands.pose(BareHands.SHOVED, 1f, 0f, false, hand);
        float palmAhead = -hand[5] / (float) Math.sqrt(hand[3] * hand[3] + hand[4] * hand[4] + hand[5] * hand[5]);
        check(palmAhead > 0.8f, "as it lands the palms face ahead: " + palmAhead);
        check(hand[7] > 1.5f * Math.abs(hand[8]) && hand[7] > 4f * Math.abs(hand[6]), "with the fingers up");
        check(-hand[2] > 0.4f && down(hand) < tanHalf && hand[0] / -hand[2] < tanHalf, "out ahead and in the picture: " + hand[0] + ", " + hand[1] + ", " + hand[2]);
        float[] through = new float[BareHands.SAYS], sprung = new float[BareHands.SAYS];
        BareHands.pose(BareHands.SHOVED, 1f, ViewModelMath.SPRINGS, true, sprung);
        BareHands.pose(BareHands.SHOVED, 1f, ViewModelMath.FOLLOWS, false, through);
        check(-sprung[2] < -hand[2] && -through[2] > -hand[2], "landed on something they spring back off it, and on nothing they go on past");
        BareHands.pose(BareHands.SHOVED, 1f, 1f, true, hand);
        check(down(hand) > tanHalf, "and after it they are down again");
        BareHands.pose(BareHands.STAMPED, 1f, 0f, false, hand);
        check(hand[4] < -0.8f && hand[0] > 0.2f, "in a stamp the hands brace out to the sides, palms down");
    }

    private static void bareHandsMoveNoFarInATinyStep() {
        float[] before = new float[BareHands.SAYS], at = new float[BareHands.SAYS];
        for (int way : new int[] {BareHands.SHOVED, BareHands.STAMPED}) {
            for (boolean struck : new boolean[] {false, true}) {
                BareHands.pose(way, 0f, 0f, struck, before);
                for (int step = 1; step <= 2000; step++) {
                    BareHands.pose(way, Math.min(step / 1000f, 1f), Math.max(step / 1000f - 1f, 0f), struck, at);
                    for (int part = 0; part < BareHands.SAYS; part++) {
                        check(Math.abs(at[part] - before[part]) < 0.01f, "no part of a hand moves far in a thousandth of the way");
                    }
                    System.arraycopy(at, 0, before, 0, BareHands.SAYS);
                }
            }
        }
    }

    private static final float SCALE = 0.74f;
    private static final float[] SAME = {1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};

    private static void bothArmsArePlacedThroughAShoveAndAStamp(ArmModel model) {
        float[][] skins = new float[2][24 * 16], said = new float[2][ArmPose.SAYS];
        float[] hand = new float[BareHands.SAYS];
        float worst = 0f;
        for (int way : new int[] {BareHands.SHOVED, BareHands.STAMPED}) {
            for (int step = 0; step <= 40; step++) {
                BareHands.pose(way, Math.min(step / 20f, 1f), Math.max(step / 20f - 1f, 0f), step % 2 == 0, hand);
                check(ArmHolds.bare(model, SCALE, hand, model.pose(BareHands.POSES[way]), SAME, skins, said) == 2, "both arms are placed, way " + way + " step " + step);
                for (int side = 0; side < 2; side++) {
                    worst = Math.max(worst, said[side][ArmPose.BENT]);
                }
            }
        }
        check(Math.toDegrees(worst) < 50.0, "no wrist is bent past 50 degrees: " + Math.toDegrees(worst));
    }
}
