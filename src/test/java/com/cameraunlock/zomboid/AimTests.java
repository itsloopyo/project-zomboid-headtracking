package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;

/**
 * What of aiming is this game's: which of cameraunlock-core's aim modes its key cycles, where the
 * rear sight stops a lean, and the matrices the held weapon is drawn with. The modes' own
 * behaviour, the fades and the lean's handover to the weapon, is core's, and its tests hold it.
 */
final class AimTests {
    private AimTests() {}

    static void run() {
        Checks.run("aimModeCyclesThroughThreeInOrder", AimTests::aimModeCyclesThroughThreeInOrder);
        Checks.run("forwardStopIsShortOfTheLimit", AimTests::forwardStopIsShortOfTheLimit);
        Checks.run("weaponStaysOnTheAimWhileTheHeadTurnsAndLeans", AimTests::weaponStaysOnTheAimWhileTheHeadTurnsAndLeans);
        Checks.run("weaponComesWithTheEyeWhenItHasTheLean", AimTests::weaponComesWithTheEyeWhenItHasTheLean);
        Checks.run("betweenAgreesWithTheViewMatrices", AimTests::betweenAgreesWithTheViewMatrices);
        Checks.run("weaponHoldsStillOutOnTheMap", AimTests::weaponHoldsStillOutOnTheMap);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static void aimModeCyclesThroughThreeInOrder() {
        CameraUnlock core = Core.library();
        int mode = CameraUnlock.AIM_SIGHTS_LOCKED;
        int[] order = {CameraUnlock.AIM_FREE_LOOK_MARKER, CameraUnlock.AIM_STOCK_SIGHTS, CameraUnlock.AIM_SIGHTS_LOCKED};
        String[] labels = {"Aim mode: free look with marker", "Aim mode: stock sights", "Aim mode: sights locked"};
        for (int step = 0; step < order.length; step++) {
            mode = Tracking.aimModeAfter(mode);
            check(mode == order[step], "the cycle's step " + step + " is core's mode " + order[step]);
            check(core.aimModeLabel(mode).equals(labels[step]), "which core calls " + labels[step]);
        }
        check(Tracking.AIM_MODES.length == 3, "and there is no fourth");
        boolean refused = false;
        try {
            Tracking.aimModeAfter(CameraUnlock.AIM_TRUE_FREE_LOOK);
        } catch (IllegalArgumentException notCycled) {
            refused = true;
        }
        check(refused, "free look with no marker is not a mode the session is ever in, so nothing comes after it");
    }

    private static void forwardStopIsShortOfTheLimit() {
        check(ViewModel.FORWARD_STOP < Core.library().settingsDefaults().limitZ && ViewModel.FORWARD_STOP > 0.2f,
                "the rear sight's stop is short of the lean's limit and not a guess far short of the sight");
    }

    private static ViewCamera.Pose pose(float x, float y, float up, float yaw, float pitch) {
        ViewCamera.Pose pose = new ViewCamera.Pose();
        ViewCamera.clean(pose, x, y, up, yaw, pitch);
        return pose;
    }

    private static final float TAN = (float) Math.tan(Math.toRadians(65) / 2), ASPECT = 16f / 9f;

    /** Where a point of the aim's view lands in the picture, through the matrix the weapon is drawn with. */
    private static float[] drawn(ViewCamera.Pose aim, ViewCamera.Pose camera, float x, float y, float ahead) {
        float[] eyeFromAim = new float[3];
        ViewCamera.eyeFrom(aim, camera, eyeFromAim);
        return drawn(aim, camera, eyeFromAim, x, y, ahead);
    }

    private static float[] drawn(ViewCamera.Pose aim, ViewCamera.Pose camera, float[] eyeFromAim, float x, float y, float ahead) {
        float[] fromAim = new float[16], seen = new float[3];
        ViewCamera.between(aim, camera, eyeFromAim, fromAim);
        ViewModelMath.place(fromAim, x, y, -ahead, seen);
        return new float[] {seen[0] / -seen[2] / (TAN * ASPECT), seen[1] / -seen[2] / TAN};
    }

    private static void weaponStaysOnTheAimWhileTheHeadTurnsAndLeans() {
        ViewCamera.Pose aim = pose(3f, 4f, 1.6f, radians(40), radians(-10));
        float[] ndc = new float[2];
        for (float yaw : new float[] {-20f, 15f}) {
            ViewCamera.Pose camera = new ViewCamera.Pose();
            camera.set(aim);
            ViewCamera.turn(camera, radians(yaw), radians(8), radians(5), true);
            // A point on the aim, far off, is where the reticle is drawn. The weapon's own
            // line of sight, drawn through the weapon's matrix, has to land on it.
            ViewCamera.project(camera, TAN, ASPECT, aim.eyeX + aim.forwardX * 500f, aim.eyeY + aim.forwardY * 500f, aim.eyeUp + aim.forwardUp * 500f, ndc);
            float[] sightLine = drawn(aim, camera, 0f, 0f, 500f);
            check(near(sightLine[0], ndc[0]) && near(sightLine[1], ndc[1]), "with the head turned " + yaw + " degrees the weapon's line is on the reticle");
            check(Math.abs(ndc[0]) > 0.2f, "which is off the middle of the picture, where the aim is");
        }
        // Free look: the eye leans and the weapon stays where it was in the world.
        ViewCamera.Pose leaned = new ViewCamera.Pose();
        leaned.set(aim);
        leaned.eyeX += aim.rightX * 0.25f;
        leaned.eyeY += aim.rightY * 0.25f;
        float[] grip = drawn(aim, leaned, 0f, -0.06f, 0.36f);
        ViewCamera.project(leaned, TAN, ASPECT, aim.eyeX + aim.forwardX * 0.36f + aim.upX * -0.06f, aim.eyeY + aim.forwardY * 0.36f + aim.upY * -0.06f,
                aim.eyeUp + aim.forwardUp * 0.36f + aim.upUp * -0.06f, ndc);
        check(near(grip[0], ndc[0]) && near(grip[1], ndc[1]), "a leaned eye sees the weapon where it is in the world");
        check(grip[0] < -0.3f, "which is well across the picture, the other way");
    }

    private static void weaponComesWithTheEyeWhenItHasTheLean() {
        ViewCamera.Pose clean = pose(3f, 4f, 1.6f, radians(40), radians(-10));
        // Sights locked, sights up: the aim's eye is the clean eye moved by the weapon's share,
        // which is the whole lean across the aim, and the picture's eye is the same place.
        ViewCamera.Pose aim = new ViewCamera.Pose(), camera = new ViewCamera.Pose();
        aim.set(clean);
        aim.eyeX += clean.rightX * 0.25f;
        aim.eyeY += clean.rightY * 0.25f;
        camera.set(aim);
        float[] grip = drawn(aim, camera, 0f, -0.06f, 0.36f), unleaned = drawn(clean, clean, 0f, -0.06f, 0.36f);
        check(near(grip[0], unleaned[0]) && near(grip[1], unleaned[1]), "the weapon keeps its place in the frame under a lean it has");
        float[] ndc = new float[2];
        ViewCamera.project(camera, TAN, ASPECT, clean.eyeX + clean.forwardX * 6f, clean.eyeY + clean.forwardY * 6f, clean.eyeUp + clean.forwardUp * 6f, ndc);
        check(ndc[0] < -0.01f, "while the world goes past behind it");
    }

    private static void betweenAgreesWithTheViewMatrices() {
        ViewCamera.Pose from = pose(3f, 4f, 1.6f, radians(40), radians(-10)), to = pose(3.2f, 3.9f, 1.7f, radians(55), radians(5));
        ViewCamera.turn(to, 0f, 0f, radians(10), true);
        float[] between = new float[16], seen = new float[3], toFromFrom = new float[3];
        ViewCamera.eyeFrom(from, to, toFromFrom);
        ViewCamera.between(from, to, toFromFrom, between);
        // A point of the first view, taken to the world by hand and through the second view's own matrix.
        float x = 0.13f, y = -0.14f, z = -0.27f;
        float worldX = from.eyeX + from.rightX * x + from.upX * y - from.forwardX * z;
        float worldY = from.eyeY + from.rightY * x + from.upY * y - from.forwardY * z;
        float worldUp = from.eyeUp + from.rightUp * x + from.upUp * y - from.forwardUp * z;
        float[] view = ViewCamera.view(to);
        ViewModelMath.place(view, worldX, worldY, worldUp, seen);
        float[] through = new float[3];
        ViewModelMath.place(between, x, y, z, through);
        check(near(seen[0], through[0]) && near(seen[1], through[1]) && near(seen[2], through[2]), "between two views is the second's matrix on the first's inverse");
        ViewCamera.between(from, from, ViewCamera.SAME_EYE, between);
        ViewModelMath.place(between, x, y, z, through);
        check(near(through[0], x) && near(through[1], y) && near(through[2], z), "a view to itself changes nothing");
    }

    /**
     * Out on the map a float holds a place to about a millimetre. With the lean on the weapon,
     * the aim's eye and the picture's are each the clean eye plus the lean, rounded on their
     * own: their difference wanders by a millimetre as the lean moves, and the eyepiece of a
     * scope 9 cm from the eye jumped about the picture with it. Held by the lean's own share
     * the eye has, the weapon does not move in the frame.
     */
    private static void weaponHoldsStillOutOnTheMap() {
        ViewCamera.Pose clean = pose(10792.4f, 9776.73f, 2.85f, radians(101), radians(0));
        // The eye's own share of the lean is the part along the aim, which the weapon is not moved with.
        float[] eyeAlone = {clean.forwardX * 0.012f, clean.forwardY * 0.012f, clean.forwardUp * 0.012f}, first = null;
        float most = 0f, apart = 0f;
        for (int i = 0; i < 200; i++) {
            float lean = 0.01f * (float) Math.sin(i * 0.37);
            ViewCamera.Pose aim = new ViewCamera.Pose(), camera = new ViewCamera.Pose();
            aim.set(clean);
            aim.eyeX += clean.rightX * lean;
            aim.eyeY += clean.rightY * lean;
            camera.set(clean);
            camera.eyeX += eyeAlone[0] + clean.rightX * lean;
            camera.eyeY += eyeAlone[1] + clean.rightY * lean;
            camera.eyeUp += eyeAlone[2];
            float[] eyepiece = drawn(aim, camera, eyeAlone, 0f, 0f, 0.09f);
            if (first == null) {
                first = eyepiece;
            }
            most = Math.max(most, Math.max(Math.abs(eyepiece[0] - first[0]), Math.abs(eyepiece[1] - first[1])));
            float[] byPlaces = drawn(aim, camera, 0f, 0f, 0.09f);
            apart = Math.max(apart, Math.abs(byPlaces[0] - first[0]));
        }
        check(most < 1e-5f, "the eyepiece stays put in the picture as the lean on the weapon moves, out on the map");
        check(apart > 0.005f, "where the difference of the two eyes would move it");
    }
}
