package com.cameraunlock.zomboid;

/** Tests of which way the player's ears face. */
final class HearingTests {
    private static final Hearing hearing = new Hearing();

    private static final float OVERHEAD_YAW = (float) (-0.75 * Math.PI), OVERHEAD_PITCH = (float) Math.toRadians(-30);
    private static final float ROOT_HALF = (float) Math.sqrt(0.5);

    private HearingTests() {}

    static void run() {
        theOverheadCameraIsHeardAsTheGameHearsIt();
        atTheEyeTheRightEarIsTheViewsRight();
        theEarsStaySquareAllTheWayIn();
        theCameraIsNoWayOutFromTheEye();
    }

    /** The side the game's sound engine takes for the right ear: its up crossed with the way it faces. */
    private static float[] rightEar() {
        return new float[] {
                hearing.upY * hearing.forwardUp - hearing.upUp * hearing.forwardY,
                hearing.upUp * hearing.forwardX - hearing.upX * hearing.forwardUp,
                hearing.upX * hearing.forwardY - hearing.upY * hearing.forwardX};
    }

    private static void theOverheadCameraIsHeardAsTheGameHearsIt() {
        ViewCamera.Pose overhead = new ViewCamera.Pose();
        ViewCamera.clean(overhead, 0f, 0f, 0f, OVERHEAD_YAW, OVERHEAD_PITCH);
        hearing.face(overhead, -OVERHEAD_PITCH, 0f);
        check(near(hearing.forwardX, -ROOT_HALF) && near(hearing.forwardY, -ROOT_HALF) && near(hearing.forwardUp, 0f),
                "the camera's place overhead is heard facing north-west and level, as the game sets it: "
                        + hearing.forwardX + ", " + hearing.forwardY + ", " + hearing.forwardUp);
        check(near(hearing.upX, 0f) && near(hearing.upY, 0f) && near(hearing.upUp, 1f), "with straight up as its up");
        // The game's view puts a place at (x - y) across the screen: its right is more x and less y.
        float[] right = rightEar();
        check(near(right[0], ROOT_HALF) && near(right[1], -ROOT_HALF) && near(right[2], 0f), "and the right of the screen in its right ear");
        hearing.rest();
    }

    private static void atTheEyeTheRightEarIsTheViewsRight() {
        ViewCamera.Pose view = new ViewCamera.Pose();
        ViewCamera.clean(view, 10f, 20f, 1.6f, 1.1f, 0.3f);
        ViewCamera.turn(view, 0.7f, -0.4f, 0.25f, true);
        hearing.face(view, 0f, 1f);
        float[] right = rightEar();
        check(near(right[0], view.rightX) && near(right[1], view.rightY) && near(right[2], view.rightUp),
                "what is to the right in the picture is in the right ear: " + right[0] + ", " + right[1] + ", " + right[2]);
        check(near(hearing.forwardX, view.forwardX) && near(hearing.forwardY, view.forwardY) && near(hearing.forwardUp, view.forwardUp),
                "and the ears face the way the head has turned the view");
        hearing.rest();
    }

    private static void theEarsStaySquareAllTheWayIn() {
        ViewCamera.Pose view = new ViewCamera.Pose();
        for (int step = 0; step <= 10; step++) {
            float in = step / 10f;
            ViewCamera.clean(view, 0f, 0f, 0f, OVERHEAD_YAW + 2.5f * in, OVERHEAD_PITCH * (1f - in));
            hearing.face(view, -OVERHEAD_PITCH * (1f - in), in);
            float along = hearing.forwardX * hearing.upX + hearing.forwardY * hearing.upY + hearing.forwardUp * hearing.upUp;
            float length = (float) Math.sqrt(hearing.forwardX * hearing.forwardX + hearing.forwardY * hearing.forwardY + hearing.forwardUp * hearing.forwardUp);
            float upLength = (float) Math.sqrt(hearing.upX * hearing.upX + hearing.upY * hearing.upY + hearing.upUp * hearing.upUp);
            check(near(along, 0f) && near(length, 1f) && near(upLength, 1f), "the way the ears face and their up are each one long and square to each other at " + in);
            check(near(hearing.forwardUp, 0f), "and a camera that has not been looked up or down with is heard level at " + in + ": " + hearing.forwardUp);
        }
        hearing.rest();
    }

    private static void theCameraIsNoWayOutFromTheEye() {
        ViewCamera.Pose view = new ViewCamera.Pose();
        ViewCamera.clean(view, 0f, 0f, 0f, 0f, 0f);
        check(hearing.zoomedOut(0.7f) == 0.7f, "in the game's own view its sounds are mixed for how far out its camera is");
        hearing.face(view, 0f, 0.5f);
        check(near(hearing.zoomedOut(0.7f), 0.35f), "half way in, for half as far out");
        hearing.face(view, 0f, 1f);
        check(hearing.zoomedOut(0.7f) == 0f, "and from the eye for no way out at all");
        hearing.rest();
        check(hearing.zoomedOut(0.7f) == 0.7f, "back in the game's own view they are mixed its way again");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }
}
