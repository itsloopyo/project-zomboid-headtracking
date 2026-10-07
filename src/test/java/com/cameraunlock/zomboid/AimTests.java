package com.cameraunlock.zomboid;

final class AimTests {
    private AimTests() {}

    static void run() {
        fadeTakesItsTimeEachWayAndArrivesAtRest();
        fadeReversedPartWayCarriesOnFromWhereItIs();
        aimModeIsDecodedFromItsThreeSettings();
        aimModeCyclesInOrderAndDecodesFromItsThreeValues();
        stockSightsTakesThePoseOutOnlyWithTheSightsUp();
        stockSightsReversedPartWayDoesNotStep();
        hipLeanIsAllTheViews();
        sightsLockedHandsTheLeanAcrossTheAimToTheWeapon();
        freeLookKeepsTheLeanOnTheView();
        stockSightsHandsTheLeanOverAsSightsLockedDoes();
        sharesPutTheEyeInTheSamePlaceWhateverTheSplit();
        forwardLeanIsWholeAtTheHipAndStopsAtTheRearSightWhenAimed();
        zoomLeavesTheForwardLeanWhole();
        seatedTheWeaponHasNothingAndTheLeanAcrossEasesOut();
        stoppingPutsTheWeaponBack();
        weaponStaysOnTheAimWhileTheHeadTurnsAndLeans();
        weaponComesWithTheEyeWhenItHasTheLean();
        betweenAgreesWithTheViewMatrices();
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static void fadeTakesItsTimeEachWayAndArrivesAtRest() {
        AdsFade fade = new AdsFade();
        check(fade.update(false, 1000) == 1f, "at the hip the scale is 1");
        check(fade.update(true, 1000) == 1f, "the frame the sights start up nothing has moved yet");
        check(near(fade.update(true, 1075), 0.5f), "half way through the 150 ms it is half way");
        float early = fade.update(true, 1076);
        check(early < 0.5f && early > 0.45f, "and it moves without a step");
        check(fade.update(true, 1150) == 0f, "after 150 ms the sights are up");
        check(fade.update(true, 5000) == 0f, "and stay up");
        check(fade.update(false, 5000) == 0f, "the frame they start down nothing has moved yet");
        check(near(fade.update(false, 5125), 0.5f), "half way through the 250 ms back it is half way");
        check(fade.update(false, 5250) == 1f, "after 250 ms it is at the hip again");
        fade.update(true, 6000);
        fade.update(true, 6075);
        fade.reset();
        check(fade.update(false, 6076) == 1f, "a reset is the hip at once");
    }

    private static void fadeReversedPartWayCarriesOnFromWhereItIs() {
        AdsFade fade = new AdsFade();
        fade.update(true, 0);
        float down = fade.update(true, 75);
        float back = fade.update(false, 76);
        check(Math.abs(back - down) < 0.02f, "letting go part way does not step the scale");
        check(fade.update(false, 76 + 60) < 1f, "the way back takes its share of the 250 ms");
        check(fade.update(false, 76 + 135) == 1f, "and no longer than the distance left needs");
        // A tap: pressed for one frame.
        AdsFade tapped = new AdsFade();
        tapped.update(true, 0);
        check(tapped.update(false, 1) > 0.999f, "a tap of the aim button moves nothing");
        AdsFade backwards = new AdsFade();
        backwards.update(true, 1000);
        check(backwards.update(true, 900) == 1f, "a clock stepping back leaves the transition where it started");
    }

    private static void aimModeIsDecodedFromItsThreeSettings() {
        check(AimMode.decode(false, false, false) == AimMode.SIGHTS_LOCKED, "all three false is sights locked");
        check(AimMode.decode(true, true, false) == AimMode.FREE_LOOK_MARKER, "free look and marker is free look with a marker");
        check(AimMode.decode(true, false, false) == AimMode.TRUE_FREE_LOOK, "free look alone is true free look");
        check(AimMode.decode(false, true, false) == AimMode.SIGHTS_LOCKED, "the marker alone is sights locked");
        for (boolean free : new boolean[] {false, true}) {
            for (boolean marker : new boolean[] {false, true}) {
                check(AimMode.decode(free, marker, true) == AimMode.STOCK_SIGHTS, "stock sights wins whatever the other two hold");
            }
        }
        check(!AimMode.STOCK_SIGHTS.isFreeLook() && !AimMode.SIGHTS_LOCKED.isFreeLook()
                && AimMode.FREE_LOOK_MARKER.isFreeLook() && AimMode.TRUE_FREE_LOOK.isFreeLook(), "only the two free look modes are free look");
    }

    private static void aimModeCyclesInOrderAndDecodesFromItsThreeValues() {
        AimMode mode = AimMode.SIGHTS_LOCKED;
        AimMode[] order = {AimMode.FREE_LOOK_MARKER, AimMode.TRUE_FREE_LOOK, AimMode.STOCK_SIGHTS, AimMode.SIGHTS_LOCKED};
        String[] labels = {"Aim mode: free look with marker", "Aim mode: true free look", "Aim mode: stock sights", "Aim mode: sights locked"};
        for (int step = 0; step < order.length; step++) {
            mode = mode.next();
            check(mode == order[step], "the cycle's step " + step + " is " + order[step]);
            check(mode.label.equals(labels[step]), "and is called " + labels[step]);
            check(AimMode.decode(mode.trueFreeLook(), mode.freeLookMarker(), mode.stockSights()) == mode, "its three values decode to itself");
        }
        check(!AimMode.STOCK_SIGHTS.trueFreeLook() && !AimMode.STOCK_SIGHTS.freeLookMarker(), "stock sights is saved with the other two false");
    }

    /** The share of yaw, pitch and the lean that reaches the view, as HeadTracking works it out. */
    private static float stockShare(AdsFade fade, AimMode mode, boolean sightsUp, long now) {
        return fade.update(mode.stockSightsEngaged(sightsUp), now);
    }

    private static void stockSightsTakesThePoseOutOnlyWithTheSightsUp() {
        for (AimMode mode : new AimMode[] {AimMode.SIGHTS_LOCKED, AimMode.FREE_LOOK_MARKER, AimMode.TRUE_FREE_LOOK}) {
            AdsFade fade = new AdsFade();
            check(stockShare(fade, mode, false, 0) == 1f && stockShare(fade, mode, true, 10) == 1f && stockShare(fade, mode, true, 1000) == 1f,
                    mode + " leaves the pose whole, sights up or down");
        }
        AdsFade fade = new AdsFade();
        check(stockShare(fade, AimMode.STOCK_SIGHTS, false, 0) == 1f, "at the hip stock sights leaves the pose whole");
        stockShare(fade, AimMode.STOCK_SIGHTS, true, 100);
        float part = stockShare(fade, AimMode.STOCK_SIGHTS, true, 175);
        check(near(part, 0.5f), "part way up the pose is scaled by the fade");
        check(stockShare(fade, AimMode.STOCK_SIGHTS, true, 250) == 0f, "with the sights up yaw, pitch and the lean are nought");
        // What is scaled is yaw, pitch and the three lean axes. Roll is not multiplied by the
        // share anywhere, so a tilt of 12 degrees is 12 degrees at every stage.
        float yaw = 20f * part, roll = 12f;
        check(near(yaw, 10f) && roll == 12f, "roll is the tracker's own throughout");
    }

    private static void stockSightsReversedPartWayDoesNotStep() {
        AdsFade fade = new AdsFade();
        stockShare(fade, AimMode.STOCK_SIGHTS, true, 0);
        float down = stockShare(fade, AimMode.STOCK_SIGHTS, true, 75);
        check(Math.abs(stockShare(fade, AimMode.STOCK_SIGHTS, false, 76) - down) < 0.02f, "the aim button let go part way does not step the pose");
        // The mode key, mid-aim: out of stock sights with the sights still up.
        AdsFade keyed = new AdsFade();
        stockShare(keyed, AimMode.STOCK_SIGHTS, true, 0);
        stockShare(keyed, AimMode.STOCK_SIGHTS, true, 150);
        check(stockShare(keyed, AimMode.SIGHTS_LOCKED, true, 151) < 0.01f, "the mode key mid-aim does not step the pose either");
        check(stockShare(keyed, AimMode.SIGHTS_LOCKED, true, 151 + 250) == 1f, "and the head eases back into the view");
        // And into it.
        AdsFade into = new AdsFade();
        stockShare(into, AimMode.TRUE_FREE_LOOK, true, 0);
        check(stockShare(into, AimMode.STOCK_SIGHTS, true, 1) > 0.99f, "stepping into stock sights mid-aim eases the pose out from where it is");
    }

    /** Looking along +x, pitched up 30 degrees. */
    private static final float AIM_X = (float) Math.cos(Math.toRadians(30)), AIM_UP = 0.5f;
    private static final float[] LEAN = {0.1f, 0.25f, 0.05f};

    private static LeanHandover settled(float[] lean, boolean aiming, AimMode mode, boolean onFoot) {
        LeanHandover handover = new LeanHandover();
        handover.update(lean, AIM_X, 0f, AIM_UP, aiming, mode.isFreeLook(), onFoot, 0);
        handover.update(lean, AIM_X, 0f, AIM_UP, aiming, mode.isFreeLook(), onFoot, 1000);
        return handover;
    }

    private static boolean same(float[] a, float x, float y, float z) {
        return near(a[0], x) && near(a[1], y) && near(a[2], z);
    }

    private static void hipLeanIsAllTheViews() {
        for (AimMode mode : AimMode.values()) {
            LeanHandover hip = settled(LEAN, false, mode, true);
            check(same(hip.camera, LEAN[0], LEAN[1], LEAN[2]) && same(hip.rig, 0f, 0f, 0f), "at the hip the view has the whole lean in " + mode);
        }
    }

    private static void sightsLockedHandsTheLeanAcrossTheAimToTheWeapon() {
        LeanHandover aimed = settled(LEAN, true, AimMode.SIGHTS_LOCKED, true);
        float along = LEAN[0] * AIM_X + LEAN[2] * AIM_UP;
        check(same(aimed.camera, AIM_X * along, 0f, AIM_UP * along), "sights up, the view keeps only the lean along the aim");
        check(same(aimed.rig, LEAN[0] - AIM_X * along, LEAN[1], LEAN[2] - AIM_UP * along), "and the weapon has all of the lean across it");
        check(near(aimed.rig[0] * AIM_X + aimed.rig[2] * AIM_UP, 0f), "which has no part along the aim");
        LeanHandover part = new LeanHandover();
        part.update(LEAN, AIM_X, 0f, AIM_UP, true, false, true, 0);
        part.update(LEAN, AIM_X, 0f, AIM_UP, true, false, true, 75);
        check(near(part.rig[1], LEAN[1] * 0.5f) && near(part.camera[1], LEAN[1] * 0.5f), "part way up it is split by the fade");
    }

    private static void freeLookKeepsTheLeanOnTheView() {
        LeanHandover marker = settled(LEAN, true, AimMode.FREE_LOOK_MARKER, true), free = settled(LEAN, true, AimMode.TRUE_FREE_LOOK, true);
        check(same(free.camera, LEAN[0], LEAN[1], LEAN[2]) && same(free.rig, 0f, 0f, 0f), "in true free look the view keeps the whole lean through the aim");
        check(same(marker.camera, free.camera[0], free.camera[1], free.camera[2]) && same(marker.rig, 0f, 0f, 0f),
                "and free look with a marker has the same shares");
    }

    private static void stockSightsHandsTheLeanOverAsSightsLockedDoes() {
        LeanHandover locked = settled(LEAN, true, AimMode.SIGHTS_LOCKED, true);
        // The settings file can hold free look beside stock sights: it is the decoded mode that is asked.
        LeanHandover stock = settled(LEAN, true, AimMode.decode(true, true, true), true);
        check(same(stock.camera, locked.camera[0], locked.camera[1], locked.camera[2]) && same(stock.rig, locked.rig[0], locked.rig[1], locked.rig[2]),
                "a lean on its way out in stock sights is carried as in sights locked");
    }

    private static void sharesPutTheEyeInTheSamePlaceWhateverTheSplit() {
        LeanHandover handover = new LeanHandover();
        for (long now = 0; now <= 200; now += 10) {
            handover.update(LEAN, AIM_X, 0f, AIM_UP, true, false, true, now);
            check(same(handover.camera, LEAN[0] - handover.rig[0], LEAN[1] - handover.rig[1], LEAN[2] - handover.rig[2]),
                    "the two shares add up to the lean " + now + " ms into the raise, with the view pitched");
        }
    }

    private static void forwardLeanIsWholeAtTheHipAndStopsAtTheRearSightWhenAimed() {
        float[] forward = {AIM_X * Settings.BUILT_IN.positionLimitZ(), 0f, AIM_UP * Settings.BUILT_IN.positionLimitZ()};
        for (AimMode mode : AimMode.values()) {
            LeanHandover hip = new LeanHandover();
            hip.setForwardStop(ViewModel.FORWARD_STOP);
            hip.update(forward, AIM_X, 0f, AIM_UP, false, mode.isFreeLook(), true, 0);
            check(same(hip.camera, forward[0], forward[1], forward[2]), "at the hip the whole forward lean is applied in " + mode);
            LeanHandover aimed = new LeanHandover();
            aimed.setForwardStop(ViewModel.FORWARD_STOP);
            aimed.update(forward, AIM_X, 0f, AIM_UP, true, mode.isFreeLook(), true, 0);
            aimed.update(forward, AIM_X, 0f, AIM_UP, true, mode.isFreeLook(), true, 1000);
            check(same(aimed.camera, AIM_X * ViewModel.FORWARD_STOP, 0f, AIM_UP * ViewModel.FORWARD_STOP) && same(aimed.rig, 0f, 0f, 0f),
                    "with the sights up it stops at the rear sight in " + mode);
        }
        check(ViewModel.FORWARD_STOP < Settings.BUILT_IN.positionLimitZ() && ViewModel.FORWARD_STOP > 0.2f, "the stop is short of the limit and not a guess far short of the sight");
        LeanHandover back = settled(new float[] {-AIM_X * 0.1f, 0f, -AIM_UP * 0.1f}, true, AimMode.SIGHTS_LOCKED, true);
        check(same(back.camera, -AIM_X * 0.1f, 0f, -AIM_UP * 0.1f), "leaning back is not stopped");
    }

    private static void zoomLeavesTheForwardLeanWhole() {
        for (float zoom : new float[] {1f, FieldOfView.AIMED_ZOOM, 0.25f}) {
            float[] lean = {AIM_X * 0.4f, 0.2f, AIM_UP * 0.4f};
            ZoomCompensation.scaleLean(lean, AIM_X, 0f, AIM_UP, zoom);
            check(near(lean[0] * AIM_X + lean[2] * AIM_UP, 0.4f), "the lean along the view is 0.4 at a zoom of " + zoom);
            check(near(lean[1], 0.2f * zoom), "and the lean across it is scaled by the zoom");
        }
    }

    private static void seatedTheWeaponHasNothingAndTheLeanAcrossEasesOut() {
        LeanHandover seated = settled(LEAN, true, AimMode.SIGHTS_LOCKED, false);
        float along = LEAN[0] * AIM_X + LEAN[2] * AIM_UP;
        check(same(seated.rig, 0f, 0f, 0f), "where the weapon cannot be moved it has no share");
        check(same(seated.camera, AIM_X * along, 0f, AIM_UP * along), "and the lean across the aim eases out");
    }

    private static void stoppingPutsTheWeaponBack() {
        LeanHandover handover = settled(LEAN, true, AimMode.SIGHTS_LOCKED, true);
        handover.stop();
        check(same(handover.rig, 0f, 0f, 0f) && same(handover.camera, 0f, 0f, 0f), "a stop leaves the weapon and the view where the game has them");
        handover.update(LEAN, AIM_X, 0f, AIM_UP, false, false, true, 2000);
        check(same(handover.camera, LEAN[0], LEAN[1], LEAN[2]), "and the next lean starts at the hip");
    }

    private static ViewCamera.Pose pose(float x, float y, float up, float yaw, float pitch) {
        ViewCamera.Pose pose = new ViewCamera.Pose();
        ViewCamera.clean(pose, x, y, up, yaw, pitch);
        return pose;
    }

    private static final float TAN = (float) Math.tan(Math.toRadians(65) / 2), ASPECT = 16f / 9f;

    /** Where a point of the aim's view lands in the picture, through the matrix the weapon is drawn with. */
    private static float[] drawn(ViewCamera.Pose aim, ViewCamera.Pose camera, float x, float y, float ahead) {
        float[] fromAim = new float[16], seen = new float[3];
        ViewCamera.between(aim, camera, fromAim);
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
        // True free look: the eye leans and the weapon stays where it was in the world.
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
        float[] between = new float[16], seen = new float[3];
        ViewCamera.between(from, to, between);
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
        ViewCamera.between(from, from, between);
        ViewModelMath.place(between, x, y, z, through);
        check(near(through[0], x) && near(through[1], y) && near(through[2], z), "a view to itself changes nothing");
    }
}
