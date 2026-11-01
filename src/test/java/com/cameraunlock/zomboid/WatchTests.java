package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;

final class WatchTests {
    private WatchTests() {}

    static void run() throws IOException {
        ArmModel model;
        try (InputStream packed = WatchTests.class.getResourceAsStream("/models/arms.mesh.gz")) {
            model = new ArmModel(packed);
        }
        Checks.run("glanceComesOnlyForALookDownAndToTheWristsSide", WatchTests::glanceComesOnlyForALookDownAndToTheWristsSide);
        Checks.run("glanceWaitsAMomentEasesInAndOutAndHoldsAtTheEdge", WatchTests::glanceWaitsAMomentEasesInAndOutAndHoldsAtTheEdge);
        Checks.run("glanceIsTheSameAtAnyFrameRate", WatchTests::glanceIsTheSameAtAnyFrameRate);
        Checks.run("glanceIsMeasuredFromWhereTheBodyFacedAsTheLookWentDown", WatchTests::glanceIsMeasuredFromWhereTheBodyFacedAsTheLookWentDown);
        Checks.run("turnFromTheBodyIsTheShortWayRound", WatchTests::turnFromTheBodyIsTheShortWayRound);
        Checks.run("glanceIsAtTheWristTheLookTurnsTowards", WatchTests::glanceIsAtTheWristTheLookTurnsTowards);
        Checks.run("watchShowsTheTimeToTheTenMinutesAsTheGamesClockDoes", WatchTests::watchShowsTheTimeToTheTenMinutesAsTheGamesClockDoes);
        Checks.run("heldMapComesDownForEachReasonAndSaysTheFirst", WatchTests::heldMapComesDownForEachReasonAndSaysTheFirst);
        Checks.run("heldMapIsNeverWantedWherePlayersCanFight", WatchTests::heldMapIsNeverWantedWherePlayersCanFight);
        Checks.run("handsStandAtTheTimesAngles", WatchTests::handsStandAtTheTimesAngles);
        Checks.run("figuresAreTheGamesClocks", WatchTests::figuresAreTheGamesClocks);
        Checks.run("segmentsLieInsideTheirFigureAndFiguresApart", WatchTests::segmentsLieInsideTheirFigureAndFiguresApart);
        Checks.run("everyWatchOfTheGamesIsLaidOutOnAWrist", WatchTests::everyWatchOfTheGamesIsLaidOutOnAWrist);
        Checks.run("armIsMeasuredWhereTheWatchIsWorn", () -> armIsMeasuredWhereTheWatchIsWorn(model));
        Checks.run("watchFaceIsTurnedToTheEyeAndReadsTheRightWayOnEitherWrist", () -> watchFaceIsTurnedToTheEyeAndReadsTheRightWayOnEitherWrist(model));
        Checks.run("mapIsHeldUpInThePictureByBothHands", () -> mapIsHeldUpInThePictureByBothHands(model));
    }

    private static final float FRAME = 1f / 60f;

    /** Holds a look for a time, a frame at a time, and answers how far the arm is up. */
    private static float hold(WatchGlance glance, float pitchDegrees, float asideDegrees, boolean allowed, float seconds, float frame) {
        float up = 0f;
        for (float t = 0f; t < seconds; t += frame) {
            up = glance.step((float) Math.toRadians(pitchDegrees), (float) Math.toRadians(asideDegrees), allowed, frame);
        }
        return up;
    }

    private static void glanceComesOnlyForALookDownAndToTheWristsSide() {
        check(hold(new WatchGlance(), -40f, 25f, true, 0.8f, FRAME) > 0.99f, "a look down and to the wrist's side raises the arm");
        check(hold(new WatchGlance(), -34f, 15f, true, 0.8f, FRAME) > 0.99f, "so does a shallower glance");
        float[][] not = {{-50f, 0f}, {-60f, 8f}, {-20f, 30f}, {0f, 40f}, {-40f, -25f}, {-40f, 100f}, {-85f, 25f}};
        String[] why = {"the floor ahead", "the floor nearly ahead", "a look round, not down", "a look to the side", "a look to the other side", "a look over the shoulder",
            "the feet"};
        for (int look = 0; look < not.length; look++) {
            check(hold(new WatchGlance(), not[look][0], not[look][1], true, 2f, FRAME) == 0f, "the arm stays down for " + why[look]);
        }
        check(hold(new WatchGlance(), -40f, 25f, false, 2f, FRAME) == 0f, "the arm stays down when the glance is not allowed: no watch, the hand full, aiming");
    }

    private static void glanceWaitsAMomentEasesInAndOutAndHoldsAtTheEdge() {
        WatchGlance glance = new WatchGlance();
        check(hold(glance, -40f, 25f, true, WatchGlance.DWELL - 2 * FRAME, FRAME) == 0f, "a look passing over the wrist does not raise the arm");
        float last = 0f;
        boolean rising = true;
        for (int frame = 0; frame < 40; frame++) {
            float up = glance.step((float) Math.toRadians(-40), (float) Math.toRadians(25), true, FRAME);
            rising &= up >= last;
            last = up;
        }
        check(rising && last > 0.99f, "held, it comes up and never back: " + last);
        check(hold(glance, -26f, 8f, true, 1f, FRAME) > 0.99f, "between where a glance begins and where it ends the arm stays up");
        float down = hold(glance, -10f, 8f, true, 0.12f, FRAME);
        check(down > 0f && down < 0.6f, "looking up it goes down, eased: " + down);
        check(hold(glance, -10f, 8f, true, 0.5f, FRAME) == 0f, "and is down in half a second");
        WatchGlance stopped = new WatchGlance();
        hold(stopped, -40f, 25f, true, 1f, FRAME);
        stopped.rest();
        check(hold(stopped, -40f, 25f, true, FRAME, FRAME) == 0f, "rested, it waits its moment again");
    }

    private static void glanceIsTheSameAtAnyFrameRate() {
        for (float seconds : new float[] {0.45f, 0.6f, 0.8f}) {
            float slow = hold(new WatchGlance(), -40f, 25f, true, seconds, 1f / 30f), fast = hold(new WatchGlance(), -40f, 25f, true, seconds, 1f / 144f);
            check(Math.abs(slow - fast) < 0.05f, String.format(Locale.ROOT, "%.2f s in, the arm is as far up at 30 frames a second as at 144: %.3f and %.3f", seconds, slow, fast));
        }
    }

    /**
     * Steps a look a frame at a time, the body turned as it is: with the view when it follows the
     * mouse, or left where it was when the head turns. Answers how far the arm is up.
     */
    private static float look(WatchGlance glance, boolean left, float pitchDegrees, float yawDegrees, float bodyDegrees, float seconds) {
        float up = 0f;
        for (float t = 0f; t < seconds; t += FRAME) {
            float pitch = (float) Math.toRadians(pitchDegrees);
            up = glance.step(pitch, glance.aside(pitch, (float) Math.toRadians(yawDegrees), (float) Math.toRadians(bodyDegrees), left), true, FRAME);
        }
        return up;
    }

    private static void glanceIsMeasuredFromWhereTheBodyFacedAsTheLookWentDown() {
        WatchGlance mouse = new WatchGlance();
        look(mouse, true, 0f, 90f, 90f, 0.5f);
        check(look(mouse, true, -40f, 90f, 90f, 0.5f) == 0f, "with the mouse, a look down ahead does not raise the arm");
        check(look(mouse, true, -40f, 65f, 65f, 0.8f) > 0.99f, "and then a turn to the left, the body turning with the view, raises the left arm");
        check(look(mouse, true, -10f, 65f, 65f, 0.6f) == 0f, "looking up puts it down");
        check(look(mouse, true, -40f, 65f, 65f, 0.8f) == 0f, "and looking down again where the body now faces does not raise it");
        WatchGlance right = new WatchGlance();
        look(right, false, -40f, 0f, 0f, 0.5f);
        check(look(right, false, -40f, 25f, 25f, 0.8f) > 0.99f, "a turn to the right raises the right arm");
        WatchGlance wrong = new WatchGlance();
        look(wrong, true, -40f, 0f, 0f, 0.5f);
        check(look(wrong, true, -40f, 25f, 25f, 0.8f) == 0f, "and not the left");
        WatchGlance head = new WatchGlance();
        look(head, true, 0f, 10f, 30f, 0.5f);
        check(look(head, true, -40f, 10f, 30f, 0.8f) > 0.99f, "with the head turned left and then down, the body still, the left arm comes up");
        check(look(new WatchGlance(), true, -40f, 30f, 30f, 2f) == 0f, "looking down ahead from the start does nothing");
    }

    private static void turnFromTheBodyIsTheShortWayRound() {
        check(Math.abs(WatchGlance.turned(3f, -3f) - (float) (6 - 2 * Math.PI)) < 1e-5f, "a view just past the half turn is a little the other way");
        check(Math.abs(WatchGlance.turned(0.5f, 0.2f) - 0.3f) < 1e-6f, "a view turned right of the body is turned right");
        check(Math.abs(WatchGlance.turned(-0.2f, 0.2f) + 0.4f) < 1e-6f, "and left is left");
    }

    private static void glanceIsAtTheWristTheLookTurnsTowards() {
        check(WatchGlance.atLeft(false, true, 0.3f), "a watch on the left wrist alone is glanced at however the look turns");
        check(!WatchGlance.atLeft(true, false, -0.3f), "and one on the right alone");
        check(WatchGlance.atLeft(true, true, -0.3f) && !WatchGlance.atLeft(true, true, 0.3f), "with one on each, the one the look turns towards");
    }

    private static void watchShowsTheTimeToTheTenMinutesAsTheGamesClockDoes() {
        float[][] times = {{10.1f, 10f}, {15.75f, 15f + 40f / 60f}, {23.99f, 23f + 50f / 60f}, {0.05f, 0f}, {7.5f, 7.5f}};
        for (float[] time : times) {
            check(Math.abs(WatchFace.shown(time[0]) - time[1]) < 1e-4f, "at " + time[0] + " hours a watch shows " + time[1] + ", not " + WatchFace.shown(time[0]));
        }
        check(Arrays.equals(WatchFace.figures(WatchFace.shown(10.1f), true), new int[] {1, 0, 0, 0}), "10:06 reads 10:00, as the game's clock reads it");
    }

    private static void heldMapComesDownForEachReasonAndSaysTheFirst() {
        check(MapHold.down(0) == null, "with nothing in the way the map stays up");
        int[] each = {MapHold.GONE, MapHold.OFF, MapHold.NOT_FIRST_PERSON, MapHold.NO_OWN_ARMS, MapHold.BODY_LEADS, MapHold.DEAD, MapHold.IN_A_VEHICLE,
            MapHold.PLAYERS_CAN_FIGHT, MapHold.AIMING, MapHold.ATTACKING, MapHold.DOING_SOMETHING_ELSE};
        java.util.Set<String> said = new java.util.HashSet<>();
        for (int reason : each) {
            String why = MapHold.down(reason);
            check(why != null && said.add(why), "each reason puts the map down and says why in its own words: " + why);
            check(why.equals(MapHold.down(reason | MapHold.DOING_SOMETHING_ELSE)), "the first reason is the one said: " + why);
        }
        check(MapHold.down(MapHold.NOT_FIRST_PERSON | MapHold.DEAD).equals(MapHold.down(MapHold.NOT_FIRST_PERSON)), "out of first person and dead, it says the view");
        check(!MapHold.wanted(MapHold.BODY_LEADS) && !MapHold.wanted(MapHold.NOT_FIRST_PERSON) && !MapHold.wanted(MapHold.OFF),
                "a map read with the body led, out of first person or switched off is not held up");
        check(MapHold.wanted(MapHold.DOING_SOMETHING_ELSE), "the reading of the map is not something else in the way of holding it up");
    }

    private static void heldMapIsNeverWantedWherePlayersCanFight() {
        check(MapHold.down(MapHold.PLAYERS_CAN_FIGHT) != null, "where players can fight a map up comes down");
        for (int others = 0; others < MapHold.DOING_SOMETHING_ELSE << 1; others++) {
            if (MapHold.wanted(others | MapHold.PLAYERS_CAN_FIGHT)) {
                check(false, "where players can fight the map is read as the game reads it, whatever else holds: " + others);
                return;
            }
        }
    }

    private static void handsStandAtTheTimesAngles() {
        float[][] times = {{0f, 0f, 0f}, {3f, 90f, 0f}, {15.5f, 105f, 180f}, {10.25f, 307.5f, 90f}, {23.75f, 352.5f, 270f}, {12f, 0f, 0f}};
        for (float[] time : times) {
            check(Math.abs(Math.toDegrees(WatchFace.hourAngle(time[0])) - time[1]) < 1e-3f, "at " + time[0] + " the hour hand stands at " + time[1] + " degrees from twelve");
            check(Math.abs(Math.toDegrees(WatchFace.minuteAngle(time[0])) - time[2]) < 1e-2f, "at " + time[0] + " the minute hand stands at " + time[2] + " degrees from twelve");
        }
    }

    private static void figuresAreTheGamesClocks() {
        Object[][] times = {{0f, false, new int[] {1, 2, 0, 0}}, {0f, true, new int[] {0, 0, 0, 0}}, {9.5f, false, new int[] {-1, 9, 3, 0}},
            {13.75f, true, new int[] {1, 3, 4, 5}}, {13.75f, false, new int[] {-1, 1, 4, 5}}, {12.02f, false, new int[] {1, 2, 0, 1}},
            {23.999f, true, new int[] {2, 3, 5, 9}}, {24.5f, true, new int[] {0, 0, 3, 0}}};
        for (Object[] time : times) {
            int[] shown = WatchFace.figures((Float) time[0], (Boolean) time[1]);
            check(Arrays.equals(shown, (int[]) time[2]), time[0] + " h on a " + ((Boolean) time[1] ? "24" : "12") + " hour clock reads " + Arrays.toString((int[]) time[2]) + ", not "
                    + Arrays.toString(shown));
        }
    }

    private static void segmentsLieInsideTheirFigureAndFiguresApart() {
        int lit = 0;
        for (int segment = 0; segment < 7; segment++) {
            float[] lies = WatchFace.segment(segment);
            boolean across = lies[3] > 0.5f;
            float halfX = (across ? lies[2] : WatchFace.SEGMENT_THICK) / 2f, halfY = (across ? WatchFace.SEGMENT_THICK : lies[2]) / 2f;
            check(Math.abs(lies[0]) + halfX <= WatchFace.DIGIT_WIDE / 2f + 1e-6f && Math.abs(lies[1]) + halfY <= WatchFace.DIGIT_TALL / 2f + 1e-6f,
                    "segment " + segment + " lies inside its figure");
            lit |= WatchFace.FIGURES[8] & 1 << segment;
        }
        check(lit == 0b1111111, "an eight lights all seven segments");
        check(Integer.bitCount(WatchFace.FIGURES[1]) == 2 && Integer.bitCount(WatchFace.FIGURES[7]) == 3, "a one is two segments and a seven three");
        for (int digit = 1; digit < 4; digit++) {
            check(WatchFace.DIGIT_AT[digit] - WatchFace.DIGIT_AT[digit - 1] > WatchFace.DIGIT_WIDE, "figure " + digit + " does not run into the one before it");
        }
        check(WatchFace.DIGIT_AT[3] + WatchFace.DIGIT_WIDE / 2f < WatchFace.DISPLAY_ACROSS / 2f && WatchFace.DIGIT_TALL < WatchFace.DISPLAY_TALL,
                "the figures are on the display");
    }

    private static final String[] WATCHES = {"Base.WristWatch_Left_ClassicBlack", "Base.WristWatch_Right_ClassicBrown", "Base.WristWatch_Left_ClassicMilitary",
        "Base.WristWatch_Left_ClassicGold", "Base.WristWatch_Left_DigitalBlack", "Base.WristWatch_Right_DigitalRed", "Base.WristWatch_Left_DigitalDress",
        "Base.WristWatch_Left_Expensive"};

    private static void everyWatchOfTheGamesIsLaidOutOnAWrist() {
        WatchFace.Pieces pieces = new WatchFace.Pieces();
        for (String type : WATCHES) {
            boolean digital = type.contains("Digital");
            for (int left = 0; left < 2; left++) {
                WatchFace.lay(WatchFace.look(type, digital), digital, left == 1, 18.13f, false, 0.03f, 0.035f, pieces);
                check(pieces.count > WatchFace.STRAP_PIECES + 4, type + " has a strap, a case and a face");
                float lowest = Float.MAX_VALUE;
                for (int piece = WatchFace.STRAP_PIECES; piece < pieces.count; piece++) {
                    lowest = Math.min(lowest, pieces.matrices[piece * 16 + 13] - Math.abs(pieces.matrices[piece * 16 + 5]) / 2f);
                }
                check(lowest >= 0.03f - 1e-5f, type + "'s case sits on the back of the wrist, not in it: " + lowest);
            }
        }
    }

    private static void armIsMeasuredWhereTheWatchIsWorn(ArmModel model) {
        for (int sex = 0; sex < 2; sex++) {
            float up = model.watchRadii[ArmModel.ARM * 4 + sex * 2], around = model.watchRadii[ArmModel.ARM * 4 + sex * 2 + 1];
            check(up > 0.012f && up < 0.04f && around > 0.015f && around < 0.05f,
                    String.format(Locale.ROOT, "a %s wrist is as thick as a wrist is where the watch sits: %.3f up, %.3f across", sex == 0 ? "man's" : "woman's", up, around));
        }
        check(model.watchAlong > 1.5f && model.watchAlong < 2f, "the watch sits on the forearm before the hand, as the mesh counts along the arm: " + model.watchAlong);
    }

    private static final float SCALE = 0.74f, PICTURE = 0.6371f, WIDE = 16f / 9f;
    private static final float[] SAME = {1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};

    /** Places both arms where the hands are, at the weapon's scale. */
    private static final float[][] SAID = new float[2][ArmPose.SAYS];

    private static boolean place(ArmModel model, TimedActs.Hand right, TimedActs.Hand left, float[][] skins) {
        TimedActs.Hand[] seen = {new TimedActs.Hand(), new TimedActs.Hand()}, both = {right, left};
        for (int side = 0; side < 2; side++) {
            for (int axis = 0; axis < 3; axis++) {
                seen[side].at[axis] = both[side].at[axis] * SCALE;
            }
            System.arraycopy(both[side].fingers, 0, seen[side].fingers, 0, 3);
            System.arraycopy(both[side].out, 0, seen[side].out, 0, 3);
            seen[side].pose = both[side].pose;
            seen[side].comesFrom = both[side].comesFrom;
            System.arraycopy(both[side].from, 0, seen[side].from, 0, 3);
        }
        return ArmHolds.placeTask(model, SCALE, seen, SAME, skins, SAID);
    }

    private static float[] column(float[] m, int column) {
        return new float[] {m[column * 4], m[column * 4 + 1], m[column * 4 + 2]};
    }

    private static void watchFaceIsTurnedToTheEyeAndReadsTheRightWayOnEitherWrist(ArmModel model) {
        TimedActs.Hand right = new TimedActs.Hand(), left = new TimedActs.Hand();
        float[][] skins = new float[2][model.bones.length * 16];
        WatchFace.Pieces pieces = new WatchFace.Pieces();
        float[] watchAt = new float[16], faceAt = new float[16];
        for (int wrist = 0; wrist < 2; wrist++) {
            for (float pitch : new float[] {0f, -0.4f, -0.8f}) {
                for (int digital = 0; digital < 2; digital++) {
                    String at = String.format(Locale.ROOT, "%s wrist, %s, the view pitched %.0f degrees", wrist == 1 ? "left" : "right", digital == 1 ? "digital" : "analogue",
                            Math.toDegrees(pitch));
                    TimedActs.watch(wrist == 1, pitch, 1f, right, left);
                    check(place(model, right, left, skins), "both arms are placed: " + at);
                    float twist = (float) Math.toDegrees(Math.abs(SAID[wrist][ArmPose.ASKED]));
                    check(twist <= Math.toDegrees(ArmPose.TWIST_MOST) - 20f,
                            "the forearm is not turned near as far as it goes to show the watch: " + at + ", turned " + twist);
                    WatchFace.lay(WatchFace.look(digital == 1 ? WATCHES[4] : WATCHES[0], digital == 1), digital == 1, wrist == 1, 10.1f, false, model.watchRadii[0],
                            model.watchRadii[1], pieces);
                    ArmPose.multiply(skins[wrist], model.foreTwist * 16, model.watchRest, 0, watchAt, 0);
                    // The display of a digital watch, the dial of the other.
                    ArmPose.multiply(watchAt, 0, pieces.matrices, (WatchFace.STRAP_PIECES + 1) * 16, faceAt, 0);
                    // In the body's axes, and seen by an eye looking at the watch, by the head, the mouse or both.
                    float[] three = TimedActs.fromBody(column(faceAt, 0), -pitch, new float[3]), normal = TimedActs.fromBody(column(faceAt, 1), -pitch, new float[3]);
                    float[] twelve = TimedActs.fromBody(column(faceAt, 2), -pitch, new float[3]), middle = TimedActs.fromBody(column(faceAt, 3), -pitch, new float[3]);
                    float[] look = new float[3], up = new float[3], across = new float[3];
                    ArmPose.unit(TimedActs.WATCH_AT[0] * (wrist == 1 ? 1f : -1f), TimedActs.WATCH_AT[1], TimedActs.WATCH_AT[2], look);
                    ArmPose.unit(-look[0] * look[1], 1f - look[1] * look[1], -look[2] * look[1], up);
                    ArmPose.cross(look, up, across);
                    float[] seenFrom = new float[3], facing = new float[3], upright = new float[3], twelveWay = new float[3];
                    ArmPose.unit(-middle[0], -middle[1], -middle[2], seenFrom);
                    ArmPose.unit(normal[0], normal[1], normal[2], facing);
                    float turned = (float) Math.toDegrees(Math.acos(ArmPose.dot(seenFrom, facing)));
                    check(turned < 30f, "the face is turned to the eye: " + at + ", " + turned + " degrees off");
                    ArmPose.cross(three, twelve, upright);
                    check(ArmPose.dot(upright, normal) > 0f, "three is to the right of twelve as the face is seen, not mirrored: " + at);
                    ArmPose.unit(twelve[0], twelve[1], twelve[2], twelveWay);
                    check(ArmPose.dot(twelveWay, up) > 0.85f, "twelve is up the picture: " + at + ", " + ArmPose.dot(twelveWay, up));
                    float ahead = ArmPose.dot(middle, look), x = ArmPose.dot(middle, across) / ahead, y = ArmPose.dot(middle, up) / ahead;
                    check(ahead > 0f && Math.abs(x) < PICTURE * WIDE * 0.5f && Math.abs(y) < PICTURE * 0.5f,
                            String.format(Locale.ROOT, "the face is well inside the picture of a glance at it: %s, at %.2f, %.2f", at, x, y));
                    float far = (float) Math.sqrt(ArmPose.dot(middle, middle)) / SCALE;
                    float[] said = SAID[wrist], elbow = TimedActs.fromBody(new float[] {said[ArmPose.ELBOW], said[ArmPose.ELBOW + 1], said[ArmPose.ELBOW + 2]}, -pitch, new float[3]);
                    float[] wristAt = TimedActs.fromBody(new float[] {said[ArmPose.WRIST], said[ArmPose.WRIST + 1], said[ArmPose.WRIST + 2]}, -pitch, new float[3]);
                    float fx = ArmPose.dot(wristAt, across) / ArmPose.dot(wristAt, look) - ArmPose.dot(elbow, across) / ArmPose.dot(elbow, look);
                    float fy = ArmPose.dot(wristAt, up) / ArmPose.dot(wristAt, look) - ArmPose.dot(elbow, up) / ArmPose.dot(elbow, look);
                    float ex = ArmPose.dot(elbow, across) / ArmPose.dot(elbow, look), ey = ArmPose.dot(elbow, up) / ArmPose.dot(elbow, look);
                    float slope = (float) Math.toDegrees(Math.atan2(Math.abs(fy), Math.abs(fx)));
                    check(slope < 25f && ex * (wrist == 1 ? -1f : 1f) > 0.6f && Math.abs(ey) < PICTURE * 0.5f,
                            String.format(Locale.ROOT, "the elbow is bent out to the wrist's side and the forearm lies across the picture: %s, the forearm %.0f degrees off level, the elbow at %.2f, %.2f", at, slope, ex, ey));
                    check(far > 0.25f && far < 0.35f, "the watch is held as near as a watch is read: " + at + ", " + far + " m");
                }
            }
        }
    }

    private static void mapIsHeldUpInThePictureByBothHands(ArmModel model) {
        TimedActs.Hand right = new TimedActs.Hand(), left = new TimedActs.Hand();
        float[][] skins = new float[2][model.bones.length * 16];
        float[] sheet = new float[16];
        for (float up = 0f; up <= 1f; up += 0.125f) {
            TimedActs.map(up, right, left);
            check(place(model, right, left, skins), "both arms are placed on the map, " + up + " of the way up");
        }
        TimedActs.sheet(1f, sheet);
        for (float[] corner : new float[][] {{-0.5f, -0.5f}, {0.5f, -0.5f}, {-0.5f, 0.5f}, {0.5f, 0.5f}}) {
            float x = sheet[0] * corner[0] + sheet[4] * corner[1] + sheet[12], y = sheet[1] * corner[0] + sheet[5] * corner[1] + sheet[13],
                    z = sheet[2] * corner[0] + sheet[6] * corner[1] + sheet[14];
            check(z < 0f && Math.abs(x / -z) < PICTURE * WIDE && Math.abs(y / -z) < PICTURE,
                    String.format(Locale.ROOT, "the map's corner %s is in the picture: %.2f, %.2f", Arrays.toString(corner), x / -z, y / -z));
        }
        float[] middle = column(sheet, 3), face = column(sheet, 2);
        check(-ArmPose.dot(middle, face) > 0f, "the map's face is turned to the eye");
        TimedActs.sheet(0f, sheet);
        check(sheet[13] / -sheet[14] < -PICTURE, "down, the map is under the picture");
    }
}
