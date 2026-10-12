package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of the eye handed to the character's head while the game's animation has the body. */
final class BodyLedTests {
    private BodyLedTests() {}

    static void run() {
        Checks.run("anUprightHeadLooksTheWayTheBodyFaces", BodyLedTests::anUprightHeadLooksTheWayTheBodyFaces);
        Checks.run("aHeadOnItsBackLooksUp", BodyLedTests::aHeadOnItsBackLooksUp);
        Checks.run("theEyeIsAheadOfAndAboveTheJointAsTheHeadHasThem", BodyLedTests::theEyeIsAheadOfAndAboveTheJointAsTheHeadHasThem);
        Checks.run("theHandOverGoesTheShortestWayAndKeepsTheViewSquare", BodyLedTests::theHandOverGoesTheShortestWayAndKeepsTheViewSquare);
        Checks.run("theHandOverSurvivesAHalfTurn", BodyLedTests::theHandOverSurvivesAHalfTurn);
        Checks.run("tippedTurnsTheLevelViewIntoTheView", BodyLedTests::tippedTurnsTheLevelViewIntoTheView);
        Checks.run("theHeadTakesTheEyeQuicklyAndGivesItBack", BodyLedTests::theHeadTakesTheEyeQuicklyAndGivesItBack);
        Checks.run("aHitLeavesTheEyeTheMouses", BodyLedTests::aHitLeavesTheEyeTheMouses);
        Checks.run("onlyALedBodyIsHeld", BodyLedTests::onlyALedBodyIsHeld);
        Checks.run("aTippedRolledHeadOnATurnedBodyLooksWhereItsFaceDoes", BodyLedTests::aTippedRolledHeadOnATurnedBodyLooksWhereItsFaceDoes);
    }

    /** The angle the game draws a body facing +x at: AnimationPlayer.getRenderedAngle, a quarter turn more than the way it faces. */
    private static final float FACING_PLUS_X = (float) (Math.PI / 2);

    /**
     * Bip01_Head's turn in the model, column after column, that has the face look along a model
     * direction and the crown point along another, whatever the head's own axes are.
     */
    private static float[] headTurn(float[] faceInModel, float[] crownInModel) {
        float[] f = BodyLed.FORWARD_IN_HEAD, u = BodyLed.UP_IN_HEAD;
        float[] side = cross(f, u), sideInModel = cross(faceInModel, crownInModel);
        float[] turn = new float[9];
        // turn = [face crown side in the model] times the transpose of [face crown side in the head].
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                turn[column * 3 + row] = faceInModel[row] * f[column] + crownInModel[row] * u[column] + sideInModel[row] * side[column];
            }
        }
        return turn;
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    /** Model -Z is the way the body faces and +Y is up. */
    private static final float[] MODEL_FORWARD = {0f, 0f, -1f}, MODEL_UP = {0f, 1f, 0f}, MODEL_BACK = {0f, 0f, 1f};

    private static void anUprightHeadLooksTheWayTheBodyFaces() {
        ViewCamera.Pose head = new ViewCamera.Pose();
        BodyLed.look(headTurn(MODEL_FORWARD, MODEL_UP), FACING_PLUS_X, 0f, 0f, 0f, 0f, 0f, 1.3f, head);
        check(near(head.forwardX, 1f) && near(head.forwardY, 0f) && near(head.forwardUp, 0f), "the face looks along +x");
        check(near(head.upUp, 1f), "the crown points straight up");
        check(near(head.rightY, 1f), "+y is on the right, as the mouse's view facing +x has it");
        ViewCamera.Pose mouse = new ViewCamera.Pose();
        ViewCamera.clean(mouse, 0f, 0f, 0f, 0f, 0f);
        check(near(head.rightX, mouse.rightX) && near(head.rightY, mouse.rightY) && near(head.rightUp, mouse.rightUp),
                "with the same right vector as the mouse's level view");

        BodyLed.look(headTurn(MODEL_FORWARD, MODEL_UP), FACING_PLUS_X + (float) (Math.PI / 2), 0f, 0f, 0f, 0f, 0f, 1.3f, head);
        check(near(head.forwardX, 0f) && near(head.forwardY, 1f), "the body turned a quarter to the right has the face along +y");
    }

    private static void aHeadOnItsBackLooksUp() {
        ViewCamera.Pose head = new ViewCamera.Pose();
        BodyLed.look(headTurn(MODEL_UP, MODEL_BACK), FACING_PLUS_X, 0f, 0f, 0f, 0f, 0f, 0.2f, head);
        check(near(head.forwardUp, 1f), "the face looks straight up");
        check(near(head.upX, -1f), "with the crown towards where the body faced from");
    }

    private static void theEyeIsAheadOfAndAboveTheJointAsTheHeadHasThem() {
        ViewCamera.Pose head = new ViewCamera.Pose();
        BodyLed.look(headTurn(MODEL_FORWARD, MODEL_UP), FACING_PLUS_X, 10f, 20f, 2.5f, 0.1f, -0.05f, 1.3f, head);
        check(near(head.eyeX, 10.1f + ViewCamera.EYES_AHEAD) && near(head.eyeY, 19.95f) && near(head.eyeUp, 3.8f + ViewCamera.EYES_ABOVE_JOINT),
                "standing, the eye is where the mouse's view puts it from the joint: " + head.eyeX + ", " + head.eyeY + ", " + head.eyeUp);
        BodyLed.look(headTurn(MODEL_UP, MODEL_BACK), FACING_PLUS_X, 10f, 20f, 0f, 0f, 0f, 0.2f, head);
        check(near(head.eyeUp, 0.2f + ViewCamera.EYES_AHEAD) && near(head.eyeX, 10f - ViewCamera.EYES_ABOVE_JOINT),
                "on its back, ahead is up and above is back: " + head.eyeX + ", " + head.eyeUp);
    }

    private static boolean square(ViewCamera.Pose pose) {
        float ff = pose.forwardX * pose.forwardX + pose.forwardY * pose.forwardY + pose.forwardUp * pose.forwardUp;
        float uu = pose.upX * pose.upX + pose.upY * pose.upY + pose.upUp * pose.upUp;
        float rr = pose.rightX * pose.rightX + pose.rightY * pose.rightY + pose.rightUp * pose.rightUp;
        float fu = pose.forwardX * pose.upX + pose.forwardY * pose.upY + pose.forwardUp * pose.upUp;
        float fr = pose.forwardX * pose.rightX + pose.forwardY * pose.rightY + pose.forwardUp * pose.rightUp;
        // right = up x forward, as ViewCamera.clean has it.
        float rx = pose.upY * pose.forwardUp - pose.upUp * pose.forwardY;
        return Math.abs(ff - 1f) < 1e-4f && Math.abs(uu - 1f) < 1e-4f && Math.abs(rr - 1f) < 1e-4f && Math.abs(fu) < 1e-4f
                && Math.abs(fr) < 1e-4f && Math.abs(rx - pose.rightX) < 1e-4f;
    }

    private static void theHandOverGoesTheShortestWayAndKeepsTheViewSquare() {
        BodyLed led = new BodyLed();
        ViewCamera.Pose from = new ViewCamera.Pose(), to = new ViewCamera.Pose(), out = new ViewCamera.Pose();
        ViewCamera.clean(from, 0f, 0f, 1f, 0f, 0f);
        ViewCamera.clean(to, 2f, 4f, 3f, (float) (Math.PI / 2), 0f);
        led.between(from, to, 0f, out);
        check(near(out.forwardX, 1f) && near(out.eyeX, 0f), "none of the way is where it starts");
        led.between(from, to, 1f, out);
        check(near(out.forwardY, 1f) && near(out.eyeX, 2f) && near(out.eyeY, 4f) && near(out.eyeUp, 3f), "all of the way is where it ends");
        led.between(from, to, 0.5f, out);
        float half = (float) Math.sqrt(0.5);
        check(near(out.forwardX, half) && near(out.forwardY, half) && near(out.upUp, 1f), "half way is half the turn, the horizon level: "
                + out.forwardX + ", " + out.forwardY);
        check(near(out.eyeX, 1f) && near(out.eyeY, 2f) && near(out.eyeUp, 2f), "and half way along the line");
        ViewCamera.clean(to, 0f, 0f, 1f, -0.3f, 1.2f);
        ViewCamera.turn(to, 0f, 0f, 0.7f, false);
        for (int i = 0; i <= 10; i++) {
            led.between(from, to, i / 10f, out);
            check(square(out), "each step of the way is a view square to itself, at " + i / 10f);
        }
    }

    private static void theHandOverSurvivesAHalfTurn() {
        BodyLed led = new BodyLed();
        ViewCamera.Pose from = new ViewCamera.Pose(), to = new ViewCamera.Pose(), out = new ViewCamera.Pose();
        ViewCamera.clean(from, 0f, 0f, 1f, 0f, 0f);
        ViewCamera.clean(to, 0f, 0f, 1f, (float) Math.PI, 0f);
        for (int i = 0; i <= 10; i++) {
            led.between(from, to, i / 10f, out);
            check(square(out), "a view turned right round is reached through square views, at " + i / 10f);
        }
    }

    private static void tippedTurnsTheLevelViewIntoTheView() {
        float yaw = 0.8f;
        ViewCamera.Pose view = new ViewCamera.Pose(), level = new ViewCamera.Pose();
        ViewCamera.clean(view, 0f, 0f, 0f, yaw, 0f);
        float[] tipped = new float[9];
        BodyLed.tipped(yaw, view, tipped);
        boolean identity = true;
        for (int i = 0; i < 9; i++) {
            identity &= near(tipped[i], i % 4 == 0 ? 1f : 0f);
        }
        check(identity, "the level view at the same yaw is not turned at all");

        ViewCamera.clean(view, 0f, 0f, 0f, yaw + 0.4f, -0.9f);
        ViewCamera.turn(view, 0f, 0f, 0.5f, false);
        BodyLed.tipped(yaw, view, tipped);
        ViewCamera.clean(level, 0f, 0f, 0f, yaw, 0f);
        ViewCamera.tilt(level, tipped);
        check(near(level.forwardX, view.forwardX) && near(level.forwardY, view.forwardY) && near(level.forwardUp, view.forwardUp)
                && near(level.upX, view.upX) && near(level.upUp, view.upUp) && near(level.rightY, view.rightY),
                "turned by it, the level view is the view, as a vehicle's tilt turns it");
    }

    private static void theHeadTakesTheEyeQuicklyAndGivesItBack() {
        BodyLed led = new BodyLed();
        for (int frame = 0; frame < 30; frame++) {
            led.frame(true, true, false, 1f / 60f);
        }
        check(led.share() == 1f && led.leads(), "half a second in, the eye is wholly the head's");
        led.frame(false, false, false, 1f / 60f);
        check(!led.leads() && led.share() > 0.5f && led.share() < 1f, "let go, the first frame gives back a little of it: " + led.share());
        for (int frame = 0; frame < 60; frame++) {
            led.frame(false, false, false, 1f / 60f);
        }
        check(led.share() == 0f, "a second later it is wholly the mouse's again");
        led.frame(true, true, false, 1f / 60f);
        check(led.share() > 0f && led.share() < 0.5f, "taken again, the first frame is only part of the way: " + led.share());
        led.reset();
        check(led.share() == 0f && !led.leads() && !led.holding(), "a reset gives it straight back");
    }

    private static void aHitLeavesTheEyeTheMouses() {
        BodyLed led = new BodyLed();
        for (int frame = 0; frame < 30; frame++) {
            led.frame(true, false, false, 1f / 60f);
        }
        check(led.leads() && !led.whole() && led.share() == 0f, "half a second into a hit, the body is the game's and the eye the mouse's: " + led.share());
        for (int frame = 0; frame < 30; frame++) {
            led.frame(true, true, false, 1f / 60f);
        }
        check(led.whole() && led.share() == 1f, "knocked down from it, the eye is wholly the head's");
        led.frame(true, false, false, 1f / 60f);
        check(!led.whole() && led.share() < 1f && led.share() > 0f, "back on its feet in a hit, it eases back, not cut: " + led.share());
        led.frame(true, false, true, 1f / 60f);
        check(!led.holding(), "a body held is held only while the head has the eye");
    }

    private static void onlyALedBodyIsHeld() {
        BodyLed led = new BodyLed();
        led.frame(false, false, true, 1f / 60f);
        check(!led.holding(), "nothing is held while the eye is the mouse's");
        led.frame(true, true, true, 1f / 60f);
        check(led.holding(), "a dead body taken off the map holds the head's last view");
        led.frame(false, false, true, 1f / 60f);
        check(!led.holding(), "and lets it go once the body no longer leads");
    }

    /** A direction in the model placed in the world as the game places a body drawn at that angle. */
    private static float[] toWorld(float[] m, float bodyAngle) {
        double c = Math.cos(bodyAngle + Math.PI), s = Math.sin(bodyAngle + Math.PI);
        return new float[] {(float) (c * m[0] + s * m[2]), (float) (s * m[0] - c * m[2]), m[1]};
    }

    private static float[] unit(float x, float y, float z) {
        float n = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[] {x / n, y / n, z / n};
    }

    private static void aTippedRolledHeadOnATurnedBodyLooksWhereItsFaceDoes() {
        float bodyAngle = 2.3f;
        float[] face = unit(0.3f, -0.4f, -0.8f);
        // A crown square to the face, leaning to one side: the head is tipped and rolled at once.
        float[] side = unit(0.9f, 0.2f, 0.25f);
        float[] crown = cross(side, face);
        crown = unit(crown[0], crown[1], crown[2]);
        ViewCamera.Pose head = new ViewCamera.Pose();
        BodyLed.look(headTurn(face, crown), bodyAngle, 0f, 0f, 0f, 0f, 0f, 0f, head);
        float[] f = toWorld(face, bodyAngle), u = toWorld(crown, bodyAngle);
        check(near(head.forwardX, f[0]) && near(head.forwardY, f[1]) && near(head.forwardUp, f[2]),
                "the view looks where the face does in the world: " + head.forwardX + ", " + head.forwardY + ", " + head.forwardUp);
        check(near(head.upX, u[0]) && near(head.upY, u[1]) && near(head.upUp, u[2]), "with its up where the crown points");
        check(square(head), "and is a view square to itself");
    }
}
