package com.cameraunlock.zomboid;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;

public final class Tests {
    public static int hookCalls;

    public static void hook() {
        hookCalls++;
    }

    public static void target() {}

    public static void main(String[] args) throws Throwable {
        cameraLooksAlongItsYaw();
        cameraPutsPlusYOnTheRightWhenFacingPlusX();
        cameraPutsHigherPointsHigher();
        cameraPitchLooksUp();
        cameraTurnsRightWithPositiveYaw();
        headYawTurnsTheViewRight();
        headPitchLooksUp();
        headRollTiltsTheViewLeft();
        worldYawKeepsTheHorizonLevel();
        projectionAgreesWithTheViewMatrix();
        reticleStaysCentredUnderRollAndMovesStraightUnderPitch();
        reticleFollowsThePointWhenTheEyeLeans();
        reticleFollowsTheFieldOfView();
        nearPlaneKeepsItsCornersInsideTheLeanSkin();
        fieldOfViewSettingIsValidated();
        lightLeadsTheHeadsYawAndIgnoresTheRest();
        lightMultiplierIsRefusedOutsideItsRange();
        packetIsParsedAndValidated();
        receiverTakesOnlyChangedPosesAndReadsLocality();
        filterKeepsThePositionInsideItsLimits();
        filterSmoothsARemoteConnectionAndTurnsTheShortWay();
        filterEasesBackWhenSamplesStop();
        leanClampTightensAtOnceAndReleasesSlowly();
        gridTraceFindsTheFirstBlockedEdge();
        shotStopsOnACharacter();
        receiverRefusesAPoseNoTrackerSendsAndABadPort();
        windowIsCentredInItsWorkArea();
        insertAfterAddsTheHookOnce();
        insertAfterRejectsAMissingCall();
        insertAfterRejectsTwoCalls();
        staticIntFieldIsRequiredAsDeclared();
        transformerHasNoGapOnceEveryPatchIsIn();
        transformerLeavesAClassItCannotPatchAloneAndNamesTheGap();
        transformerNamesAClassTheGameDoesNotHave();
        floorFaceShowsTheSpritesDiamond();
        wallFaceIsClippedToTheTrimmedSprite();
        faceOutsideTheTrimmedSpriteAddsNothing();
        uprightSpriteStandsOnItsAnchor();
        System.out.println("all tests passed");
    }

    private static final float[] PROJECTION = ViewCamera.projection((float) Math.tan(Math.toRadians(35)), 16f / 9f, 0.05f, 200f);

    /** Normalised device coordinates of a world point, or null when it is behind the eye. */
    private static float[] project(float[] view, float x, float y, float up) {
        float[] eye = multiply(view, new float[] {x, y, up, 1f});
        float[] clip = multiply(PROJECTION, eye);
        if (clip[3] <= 0f) {
            return null;
        }
        return new float[] {clip[0] / clip[3], clip[1] / clip[3], clip[2] / clip[3]};
    }

    private static float[] multiply(float[] m, float[] v) {
        float[] out = new float[4];
        for (int row = 0; row < 4; row++) {
            out[row] = m[row] * v[0] + m[4 + row] * v[1] + m[8 + row] * v[2] + m[12 + row] * v[3];
        }
        return out;
    }

    private static ViewCamera.Pose cleanPose(float x, float y, float up, float yaw, float pitch) {
        ViewCamera.Pose pose = new ViewCamera.Pose();
        ViewCamera.clean(pose, x, y, up, yaw, pitch);
        return pose;
    }

    private static float[] cleanView(float x, float y, float up, float yaw, float pitch) {
        return ViewCamera.view(cleanPose(x, y, up, yaw, pitch));
    }

    private static float radians(double degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static final float TAN_HALF_FOV = (float) Math.tan(Math.toRadians(35));
    private static final float ASPECT = 16f / 9f;

    private static void headYawTurnsTheViewRight() {
        for (boolean worldYaw : new boolean[] {true, false}) {
            ViewCamera.Pose pose = cleanPose(0f, 0f, 1.6f, 0f, 0f);
            ViewCamera.turn(pose, radians(20), 0f, 0f, worldYaw);
            float[] ahead = project(ViewCamera.view(pose), 10f, 0f, 1.6f);
            check(ahead[0] < 0f && near(ahead[1], 0f), "positive head yaw turns right: what was ahead moves left");
        }
    }

    private static void headPitchLooksUp() {
        ViewCamera.Pose pose = cleanPose(0f, 0f, 1.6f, 0f, 0f);
        ViewCamera.turn(pose, 0f, radians(20), 0f, true);
        float[] ahead = project(ViewCamera.view(pose), 10f, 0f, 1.6f);
        check(ahead[1] < 0f && near(ahead[0], 0f), "positive head pitch looks up: what was ahead moves down");
    }

    private static void headRollTiltsTheViewLeft() {
        ViewCamera.Pose pose = cleanPose(0f, 0f, 1.6f, 0f, 0f);
        ViewCamera.turn(pose, 0f, 0f, radians(20), true);
        float[] above = project(ViewCamera.view(pose), 10f, 0f, 3f);
        check(above[0] > 0f, "positive head roll tilts left: a point above the centre swings to the right");
        float[] ahead = project(ViewCamera.view(pose), 10f, 0f, 1.6f);
        check(near(ahead[0], 0f) && near(ahead[1], 0f), "roll leaves the view centre where it was");
    }

    private static void worldYawKeepsTheHorizonLevel() {
        ViewCamera.Pose world = cleanPose(0f, 0f, 1.6f, 0f, radians(-60));
        ViewCamera.turn(world, radians(30), 0f, 0f, true);
        check(near(world.rightUp, 0f), "yaw about straight up leaves the right vector level");
        check(near(world.forwardUp, (float) Math.sin(radians(-60))), "and the pitch unchanged");

        ViewCamera.Pose local = cleanPose(0f, 0f, 1.6f, 0f, radians(-60));
        ViewCamera.turn(local, radians(30), 0f, 0f, false);
        check(Math.abs(local.rightUp) > 0.1f, "yaw about the view's own up tips the horizon when looking down");
    }

    private static void projectionAgreesWithTheViewMatrix() {
        ViewCamera.Pose pose = cleanPose(3f, 7f, 1.6f, radians(40), radians(-10));
        ViewCamera.turn(pose, radians(25), radians(15), radians(-20), true);
        pose.eyeX += 0.2f;
        pose.eyeY -= 0.1f;
        pose.eyeUp += 0.15f;
        float[] matrix = ViewCamera.projection(TAN_HALF_FOV, ASPECT, 0.05f, 200f);
        float[] eye = multiply(ViewCamera.view(pose), new float[] {9f, 14f, 0.5f, 1f});
        float[] clip = multiply(matrix, eye);
        float[] ndc = new float[2];
        check(ViewCamera.project(pose, TAN_HALF_FOV, ASPECT, 9f, 14f, 0.5f, ndc), "the point is in front of the eye");
        check(near(ndc[0], clip[0] / clip[3]) && near(ndc[1], clip[1] / clip[3]),
                "the reticle's projection lands where the frame's matrices put the same point");
        check(!ViewCamera.project(pose, TAN_HALF_FOV, ASPECT, pose.eyeX - pose.forwardX, pose.eyeY - pose.forwardY,
                pose.eyeUp - pose.forwardUp, ndc), "a point behind the eye has no place on the screen");
    }

    /** Where the clean aim's direction falls in a view turned by the head, the eye not moved. */
    private static float[] aimInTurnedView(float cleanPitch, float yaw, float pitch, float roll, boolean worldYaw) {
        ViewCamera.Pose clean = cleanPose(0f, 0f, 1.6f, radians(30), cleanPitch);
        ViewCamera.Pose turned = new ViewCamera.Pose();
        turned.set(clean);
        ViewCamera.turn(turned, yaw, pitch, roll, worldYaw);
        float[] ndc = new float[2];
        check(ViewCamera.project(turned, TAN_HALF_FOV, ASPECT, clean.eyeX + clean.forwardX, clean.eyeY + clean.forwardY,
                clean.eyeUp + clean.forwardUp, ndc), "the aim is in front of the turned view");
        return ndc;
    }

    private static void reticleStaysCentredUnderRollAndMovesStraightUnderPitch() {
        float[] rolled = aimInTurnedView(0f, 0f, 0f, radians(25), true);
        check(near(rolled[0], 0f) && near(rolled[1], 0f), "roll alone leaves the reticle at the centre");

        float[] pitched = aimInTurnedView(0f, 0f, radians(15), 0f, true);
        check(near(pitched[0], 0f) && pitched[1] < 0f, "pitch alone moves the reticle straight down the screen");

        // Roll is the outermost turn, so it swings the reticle round the centre at the distance
        // pitch put it: the distance from the centre in tangent space does not change with roll.
        float[] both = aimInTurnedView(0f, 0f, radians(15), radians(25), true);
        float radius = (float) Math.hypot(both[0] * ASPECT, both[1]);
        check(near(radius, Math.abs(pitched[1])), "pitch and roll together keep the reticle on the aim");

        float[] down = aimInTurnedView(radians(-89.9f), radians(40), 0f, 0f, true);
        check(Math.abs(down[0]) < 2e-3f && Math.abs(down[1]) < 2e-3f,
                "looking straight down, yaw about straight up spins the world round a reticle that stays put");
    }

    private static void reticleFollowsThePointWhenTheEyeLeans() {
        ViewCamera.Pose clean = cleanPose(0f, 0f, 1.6f, 0f, 0f);
        ViewCamera.Pose leaned = new ViewCamera.Pose();
        leaned.set(clean);
        leaned.eyeY += 0.3f;
        float[] nearPoint = new float[2];
        float[] farPoint = new float[2];
        ViewCamera.project(leaned, TAN_HALF_FOV, ASPECT, 1f, 0f, 1.6f, nearPoint);
        ViewCamera.project(leaned, TAN_HALF_FOV, ASPECT, 20f, 0f, 1.6f, farPoint);
        check(near(nearPoint[0], -0.3f / 1f / (TAN_HALF_FOV * ASPECT)), "a lean to the right puts a near aim point left by lean over distance");
        check(near(farPoint[0], -0.3f / 20f / (TAN_HALF_FOV * ASPECT)), "and a far one left by much less");
        check(near(nearPoint[1], 0f) && near(farPoint[1], 0f), "with no vertical movement");
    }

    private static void reticleFollowsTheFieldOfView() {
        ViewCamera.Pose clean = cleanPose(3f, 7f, 1.6f, radians(40), radians(-10));
        ViewCamera.Pose tracked = new ViewCamera.Pose();
        tracked.set(clean);
        ViewCamera.turn(tracked, radians(20), radians(10), radians(15), true);
        tracked.eyeY += 0.25f;
        float aimX = clean.eyeX + clean.forwardX * 6f, aimY = clean.eyeY + clean.forwardY * 6f,
                aimUp = clean.eyeUp + clean.forwardUp * 6f;
        float referenceTanHalf = (float) Math.tan(Math.toRadians(65) / 2);
        float[] reference = new float[2];
        ViewCamera.project(tracked, referenceTanHalf, ASPECT, aimX, aimY, aimUp, reference);
        for (float degrees : new float[] {FieldOfView.MIN_DEGREES, 65f, 90f, FieldOfView.MAX_DEGREES}) {
            for (float aspect : new float[] {4f / 3f, ASPECT, 32f / 9f}) {
                float tanHalf = (float) Math.tan(Math.toRadians(degrees) / 2);
                float[] matrix = ViewCamera.projection(tanHalf, aspect,
                        ViewCamera.nearPlane(WorldScene.NEAR_CORNER_DISTANCE, tanHalf, aspect), 150f);
                float[] clip = multiply(matrix, multiply(ViewCamera.view(tracked), new float[] {aimX, aimY, aimUp, 1f}));
                float[] ndc = new float[2];
                check(ViewCamera.project(tracked, tanHalf, aspect, aimX, aimY, aimUp, ndc), "the aim point is in front of the eye");
                check(near(ndc[0], clip[0] / clip[3]) && near(ndc[1], clip[1] / clip[3]),
                        "the reticle lands where the frame's matrices put the aim point at " + degrees + " degrees, aspect " + aspect);
                float scale = referenceTanHalf / tanHalf;
                check(near(ndc[1], reference[1] * scale) && near(ndc[0] * aspect, reference[0] * ASPECT * scale),
                        "a wider field of view draws the reticle nearer the centre by the ratio of the tangents");
            }
        }
    }

    private static void nearPlaneKeepsItsCornersInsideTheLeanSkin() {
        for (float degrees : new float[] {FieldOfView.MIN_DEGREES, 65f, FieldOfView.MAX_DEGREES}) {
            for (float aspect : new float[] {4f / 3f, ASPECT, 32f / 9f, 48f / 9f}) {
                float tanHalf = (float) Math.tan(Math.toRadians(degrees) / 2);
                float nearPlane = ViewCamera.nearPlane(WorldScene.NEAR_CORNER_DISTANCE, tanHalf, aspect);
                float corner = (float) Math.sqrt(nearPlane * nearPlane * (1 + tanHalf * tanHalf * (1 + aspect * aspect)));
                check(near(corner, WorldScene.NEAR_CORNER_DISTANCE), "the near plane's corners are the asked distance from the eye");
            }
        }
        check(WorldScene.NEAR_CORNER_DISTANCE < LeanClamp.SKIN, "the near plane's corners are nearer than a lean is held off a wall");
    }

    private static void fieldOfViewSettingIsValidated() {
        check(FieldOfView.parse(null) == FieldOfView.DEFAULT_DEGREES, "no setting is the default field of view");
        check(FieldOfView.parse(" 90 ") == 90f, "a setting is degrees");
        for (String bad : new String[] {"wide", "", "NaN", "10", "170", "Infinity"}) {
            boolean refused = false;
            try {
                FieldOfView.parse(bad);
            } catch (IllegalArgumentException expected) {
                refused = true;
            }
            check(refused, "a field of view of \"" + bad + "\" is refused");
        }
        float before = FieldOfView.degrees();
        FieldOfView.set(90f);
        check(near(FieldOfView.tanHalfVertical(), 1f), "a field of view of 90 degrees has a half tangent of 1");
        FieldOfView.set(before);
    }

    private static void lightLeadsTheHeadsYawAndIgnoresTheRest() throws Exception {
        Log.open(java.nio.file.Files.createTempDirectory("zomboid-headtracking-test"));
        ViewCamera.Pose level = cleanPose(0f, 0f, 1.6f, radians(200), 0f);
        ViewCamera.Pose down = cleanPose(0f, 0f, 1.6f, radians(200), radians(-60));
        ViewCamera.Pose up = cleanPose(0f, 0f, 1.6f, radians(200), radians(89));
        float lead = HeadFollowLight.DEFAULT_MULTIPLIER;
        check(near(HeadFollowLight.headingTurn(level, radians(20), 0f, 0f, true), radians(20) * lead),
                "the beam turns the default multiple of the head's yaw");
        check(near(HeadFollowLight.headingTurn(level, radians(-20), 0f, 0f, false), radians(-20) * lead),
                "to the left too, and the same about the view's own up while the view is level");
        check(near(HeadFollowLight.headingTurn(down, radians(20), 0f, 0f, true), radians(30)), "looking down changes nothing about straight up");
        check(near(HeadFollowLight.headingTurn(up, radians(60), 0f, 0f, true), radians(90)), "nor does looking almost straight up");
        check(near(HeadFollowLight.headingTurn(down, radians(20), radians(15), radians(20), true), radians(30)),
                "pitch and roll turn no heading when the yaw is about straight up");
        // Yaw about a tipped up vector swings the view centre further over the ground: tan(turn) = tan(yaw) / cos(pitch).
        check(near(HeadFollowLight.headingTurn(down, radians(20), 0f, 0f, false),
                (float) Math.atan(Math.tan(Math.toRadians(30)) / 0.5)), "about the view's own up it is the heading the view centre would take");

        check(HeadFollowLight.setMultiplier("1"), "a multiplier of 1 is taken");
        ViewCamera.Pose view = cleanPose(0f, 0f, 1.6f, radians(200), radians(-60));
        ViewCamera.turn(view, radians(25), radians(10), radians(15), false);
        float viewTurn = (float) Math.atan2(view.forwardY, view.forwardX) + radians(360) - radians(200);
        check(near(HeadFollowLight.headingTurn(down, radians(25), radians(10), radians(15), false), viewTurn),
                "at 1 the beam has the heading of the view");
        check(HeadFollowLight.setMultiplier(String.valueOf(lead)), "the default is taken back");

        HeadFollowLight.follow(level, radians(60), 0f, 0f, true);
        check(near(HeadFollowLight.turn(), radians(90)), "a followed head turns the beam");
        HeadFollowLight.release();
        check(HeadFollowLight.turn() == 0f, "and a released beam is the game's");

        check(HeadFollowLight.setMultiplier("0"), "a multiplier of 0 is taken");
        check(near(HeadFollowLight.headingTurn(level, radians(20), radians(10), radians(5), true), 0f), "and leaves the beam on the aim");
        check(HeadFollowLight.setMultiplier(String.valueOf(lead)), "the default is put back");
    }

    private static void lightMultiplierIsRefusedOutsideItsRange() {
        for (String bad : new String[] {"bright", "", "NaN", "-0.1", "5.01", "8", "Infinity"}) {
            check(!HeadFollowLight.setMultiplier(bad), "a light multiplier of \"" + bad + "\" is refused");
            check(HeadFollowLight.multiplier() == HeadFollowLight.DEFAULT_MULTIPLIER, "and the one in use stands");
        }
        check(HeadFollowLight.setMultiplier(" 5 ") && HeadFollowLight.multiplier() == HeadFollowLight.MAX_MULTIPLIER,
                "the top of the range is taken");
        HeadFollowLight.setMultiplier(String.valueOf(HeadFollowLight.DEFAULT_MULTIPLIER));
    }

    private static byte[] packet(double x, double y, double z, double yaw, double pitch, double roll) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(48).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        for (double value : new double[] {x, y, z, yaw, pitch, roll}) {
            buffer.putDouble(value);
        }
        return buffer.array();
    }

    private static void packetIsParsedAndValidated() {
        OpenTrackReceiver.Sample sample = OpenTrackReceiver.parse(packet(10, -20, 30, 1, 2, 3), 48, 5L);
        check(near(sample.x(), 0.1f) && near(sample.y(), -0.2f) && near(sample.z(), 0.3f), "centimetres become metres");
        check(sample.yaw() == 1f && sample.pitch() == 2f && sample.roll() == 3f, "angles stay in degrees");
        check(OpenTrackReceiver.parse(packet(0, 0, 0, 0, 0, 0), 47, 5L) == null, "a short datagram is dropped");
        check(OpenTrackReceiver.parse(packet(0, 0, 0, Double.NaN, 0, 0), 48, 5L) == null, "a NaN is dropped");
        check(OpenTrackReceiver.parse(packet(0, Double.POSITIVE_INFINITY, 0, 0, 0, 0), 48, 5L) == null, "an infinity is dropped");
        check(OpenTrackReceiver.parse(packet(1e300, 0, 0, 0, 0, 0), 48, 5L) == null, "a value too large for a float is dropped");
    }

    private static void receiverTakesOnlyChangedPosesAndReadsLocality() throws Exception {
        Log.open(java.nio.file.Files.createTempDirectory("zomboid-headtracking-test"));
        OpenTrackReceiver receiver = new OpenTrackReceiver(0);
        java.net.InetAddress loopback = java.net.InetAddress.getByName("127.0.0.1");
        java.net.InetAddress lan = java.net.InetAddress.getByName("192.168.1.20");
        check(receiver.latest() == null && !receiver.isFresh(), "nothing before the first packet");
        receiver.accept(packet(0, 0, 0, 5, 0, 0), 48, loopback, System.nanoTime());
        OpenTrackReceiver.Sample first = receiver.latest();
        check(first != null && !receiver.isRemote() && receiver.isFresh(), "a loopback packet is a local connection");
        receiver.accept(packet(0, 0, 0, 5, 0, 0), 48, loopback, System.nanoTime());
        check(receiver.latest() == first, "a repeat of the same pose is not a new sample");
        receiver.accept(packet(0, 0, 0, Double.NaN, 0, 0), 48, lan, System.nanoTime());
        check(!receiver.isRemote(), "a datagram that is dropped does not change the connection's locality");
        receiver.accept(packet(0, 0, 0, 6, 0, 0), 48, lan, System.nanoTime());
        check(receiver.latest() != first && receiver.isRemote(), "a packet from another address is a remote connection");
    }

    private static OpenTrackReceiver.Sample sample(double x, double y, double z, double yaw, double pitch, double roll, long nanos) {
        return OpenTrackReceiver.parse(packet(x * 100, y * 100, z * 100, yaw, pitch, roll), 48, nanos);
    }

    private static void filterKeepsThePositionInsideItsLimits() {
        PoseFilter filter = new PoseFilter();
        filter.update(sample(1, 1, -1, 10, 20, 30, 1L), false, 1f / 60f);
        check(near(filter.x(), 0.30f) && near(filter.y(), 0.20f) && near(filter.z(), -0.40f),
                "the forward lean, negative z, gets the large allowance");
        check(filter.yaw() == 10f && filter.pitch() == 20f && filter.roll() == 30f, "the first pose is taken as it is");
        filter.reset();
        filter.update(sample(-1, -1, 1, 0, 0, 0, 2L), false, 1f / 60f);
        check(near(filter.x(), -0.30f) && near(filter.y(), -0.20f) && near(filter.z(), 0.10f),
                "the backward lean, positive z, gets the small one");
    }

    private static void filterSmoothsARemoteConnectionAndTurnsTheShortWay() {
        check(PoseFilter.effectiveSmoothing(0.1f, 0.7f, false) == 0.1f && PoseFilter.effectiveSmoothing(0.1f, 0.7f, true) == 0.7f,
                "the connection selects the smoothing value");
        check(near(1f / 50f, (float) (-1.0 / 60 / Math.log(1 - PoseFilter.smoothingFactor(0f, 1f / 60f)))),
                "no smoothing is a 20 ms time constant");

        float[] yawAfter = new float[2];
        for (int remote = 0; remote < 2; remote++) {
            PoseFilter filter = new PoseFilter();
            filter.update(sample(0, 0, 0, 0, 0, 0, 1L), remote == 1, 1f / 60f);
            for (int frame = 0; frame < 3; frame++) {
                filter.update(sample(0, 0, 0, 30, 0, 0, 2L), remote == 1, 1f / 60f);
            }
            yawAfter[remote] = filter.yaw();
        }
        check(yawAfter[0] > yawAfter[1] && yawAfter[1] > 0f, "a remote connection is smoothed more than a local one");

        PoseFilter filter = new PoseFilter();
        filter.update(sample(0, 0, 0, 179, 0, 0, 1L), false, 1f / 60f);
        filter.update(sample(0, 0, 0, -179, 0, 0, 2L), false, 1f / 60f);
        check(Math.abs(filter.yaw()) > 178f, "yaw crosses the 180 degree seam the short way");
    }

    private static void filterEasesBackWhenSamplesStop() {
        PoseFilter filter = new PoseFilter();
        filter.update(sample(0, 0, 0, 0, 0, 0, 1L), false, 0.02f);
        OpenTrackReceiver.Sample last = sample(0, 0, 0, 10, 0, 0, 2L);
        for (int frame = 0; frame < 200; frame++) {
            filter.update(last, false, 0.02f);
        }
        check(near(filter.yaw(), 10f), "a feed that stops leaves the view at the last pose it sent, not past it");
    }

    private static void leanClampTightensAtOnceAndReleasesSlowly() {
        LeanClamp clamp = new LeanClamp();
        float[] room = {1f};
        LeanClamp.Room query = (x, y, up, reach) -> Math.min(room[0], reach);

        float[] offset = {0.3f, 0f, 0f};
        check(near(clamp.apply(offset, 1f / 60f, query), 0.3f) && !clamp.inContact(), "open ground gives the whole lean");

        room[0] = 0.1f;
        offset = new float[] {0.3f, 0f, 0f};
        check(near(clamp.apply(offset, 1f / 60f, query), 0.1f) && near(offset[0], 0.1f) && clamp.inContact(),
                "a wall stops the lean in the same frame");

        room[0] = 1f;
        offset = new float[] {0.3f, 0f, 0f};
        float released = clamp.apply(offset, 1f / 60f, query);
        check(released > 0.1f && released < 0.15f, "the lean opens back up gradually when the wall clears");
        for (int frame = 0; frame < 600; frame++) {
            offset = new float[] {0.3f, 0f, 0f};
            released = clamp.apply(offset, 1f / 60f, query);
        }
        check(near(released, 0.3f) && !clamp.inContact(), "and gets all the way back");
    }

    private static void gridTraceFindsTheFirstBlockedEdge() {
        GridTrace trace = new GridTrace();
        GridTrace.Edges wallAtXFive = (fromX, fromY, toX, toY) -> fromX == 4 && toX == 5 || fromX == 5 && toX == 4;
        check(near(trace.firstBlockedEdge(wallAtXFive, 4.25f, 0.5f, 1f, 0f, 2f), 0.75f) && trace.hitFacesX,
                "a wall across the ray is met at its distance");
        check(trace.firstBlockedEdge(wallAtXFive, 4.25f, 0.5f, 1f, 0f, 0.5f) == Float.POSITIVE_INFINITY,
                "a wall past the reach is not met");
        check(trace.firstBlockedEdge(wallAtXFive, 4.25f, 0.5f, 0f, 1f, 10f) == Float.POSITIVE_INFINITY,
                "a ray along the wall never crosses it");
        float diagonal = (float) Math.sqrt(0.5);
        check(near(trace.firstBlockedEdge(wallAtXFive, 4.25f, 0.5f, diagonal, diagonal, 5f), 0.75f / diagonal),
                "a ray at an angle meets it further along");
        check(near(trace.firstBlockedEdge(wallAtXFive, 5.5f, 0.5f, -1f, 0f, 2f), 0.5f), "and from the other side");
    }

    private static void shotStopsOnACharacter() {
        ViewCamera.Pose aim = cleanPose(0f, 0f, 1.6f, 0f, 0f);
        check(near(WorldQuery.cylinderDistance(aim, 5f, 0f, 0f), 5f - WorldQuery.CHARACTER_RADIUS), "a figure dead ahead is hit on its near side");
        check(WorldQuery.cylinderDistance(aim, 5f, 1f, 0f) == Float.POSITIVE_INFINITY, "a figure beside the aim is missed");
        check(WorldQuery.cylinderDistance(aim, -5f, 0f, 0f) == Float.POSITIVE_INFINITY, "a figure behind is missed");
        ViewCamera.Pose over = cleanPose(0f, 0f, 1.6f, 0f, radians(20));
        check(WorldQuery.cylinderDistance(over, 5f, 0f, 0f) == Float.POSITIVE_INFINITY, "an aim over its head is missed");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static void cameraLooksAlongItsYaw() {
        float[] ahead = project(cleanView(3f, 7f, 1.6f, 0f, 0f), 13f, 7f, 1.6f);
        check(near(ahead[0], 0f) && near(ahead[1], 0f), "a point straight ahead along +x is at the screen centre");
        check(project(cleanView(3f, 7f, 1.6f, 0f, 0f), -7f, 7f, 1.6f) == null, "a point behind the eye is not drawn");

        float yaw = (float) (Math.PI / 2);
        float[] aheadY = project(cleanView(3f, 7f, 1.6f, yaw, 0f), 3f, 17f, 1.6f);
        check(near(aheadY[0], 0f) && near(aheadY[1], 0f), "at a quarter turn the camera looks along +y");
    }

    private static void cameraPutsPlusYOnTheRightWhenFacingPlusX() {
        float[] view = cleanView(0f, 0f, 1.6f, 0f, 0f);
        check(project(view, 10f, 2f, 1.6f)[0] > 0f, "+y is on the right when facing +x");
        check(project(view, 10f, -2f, 1.6f)[0] < 0f, "-y is on the left when facing +x");
    }

    private static void cameraPutsHigherPointsHigher() {
        float[] view = cleanView(0f, 0f, 1.6f, 0f, 0f);
        check(project(view, 10f, 0f, 3f)[1] > 0f, "a point above eye height is in the top half");
        check(project(view, 10f, 0f, 0f)[1] < 0f, "the ground is in the bottom half");
    }

    private static void cameraPitchLooksUp() {
        float[] view = cleanView(0f, 0f, 1.6f, 0f, (float) Math.toRadians(20));
        check(project(view, 10f, 0f, 1.6f)[1] < 0f, "looking up moves the horizon down the screen");
    }

    private static void cameraTurnsRightWithPositiveYaw() {
        float[] view = cleanView(0f, 0f, 1.6f, (float) Math.toRadians(20), 0f);
        check(project(view, 10f, 0f, 1.6f)[0] < 0f, "turning right moves what was ahead to the left");
    }

    private static void receiverRefusesAPoseNoTrackerSendsAndABadPort() throws Exception {
        Log.open(java.nio.file.Files.createTempDirectory("zomboid-headtracking-test"));
        java.nio.ByteBuffer wire = java.nio.ByteBuffer.allocate(OpenTrackReceiver.PACKET_BYTES)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);
        double limit = OpenTrackReceiver.MAX_MAGNITUDE;
        for (int channel = 0; channel < OpenTrackReceiver.PACKET_BYTES / Double.BYTES; channel++) {
            for (double sign : new double[] {1, -1}) {
                wire.putDouble(channel * Double.BYTES, sign * limit);
                check(OpenTrackReceiver.parse(wire.array(), OpenTrackReceiver.PACKET_BYTES, 1L) != null,
                        "value " + channel + " is taken at the limit");
                // Finite as floats, and two of them a float's range apart overflow the step between them.
                for (double tooFar : new double[] {limit * 1.01, 3e38}) {
                    wire.putDouble(channel * Double.BYTES, sign * tooFar);
                    check(OpenTrackReceiver.parse(wire.array(), OpenTrackReceiver.PACKET_BYTES, 1L) == null,
                            "value " + channel + " is dropped at " + sign * tooFar);
                }
            }
            wire.putDouble(channel * Double.BYTES, 0);
        }

        OpenTrackReceiver receiver = new OpenTrackReceiver(0);
        int pitch = 4 * Double.BYTES;
        wire.putDouble(pitch, 5);
        receiver.accept(wire.array(), OpenTrackReceiver.PACKET_BYTES, java.net.InetAddress.getByName("127.0.0.1"), System.nanoTime());
        OpenTrackReceiver.Sample kept = receiver.latest();
        check(kept != null && kept.pitch() == 5f, "a pose a tracker sends is taken");
        wire.putDouble(pitch, -3e38);
        receiver.accept(wire.array(), OpenTrackReceiver.PACKET_BYTES, java.net.InetAddress.getByName("192.168.1.20"), System.nanoTime());
        check(receiver.latest() == kept && !receiver.isRemote(), "a pose out of range changes neither the pose nor the connection's locality");

        check(OpenTrackReceiver.parsePort(null) == OpenTrackReceiver.DEFAULT_PORT, "no setting is the default port");
        check(OpenTrackReceiver.parsePort(" 5555 ") == 5555, "a setting is the port");
        for (String bad : new String[] {"tracker", "", "4242.5", "0", "-1", "65536", "99999999999"}) {
            boolean refused = false;
            try {
                OpenTrackReceiver.parsePort(bad);
            } catch (IllegalArgumentException expected) {
                refused = true;
            }
            check(refused, "a port of \"" + bad + "\" is refused");
        }
    }

    private static void windowIsCentredInItsWorkArea() {
        check(WindowPlacement.centredOrigin(0, 5120, 1936) == 1592, "a window is centred across a work area at the origin");
        check(WindowPlacement.centredOrigin(0, 1392, 1119) == 136, "a window is centred down a work area a taskbar shortens");
        check(WindowPlacement.centredOrigin(-1920, 1920, 1296) == -1608, "a monitor left of the primary has a negative origin");
        check(WindowPlacement.centredOrigin(40, 1040, 759) == 180, "a work area below a top taskbar starts below it");
    }

    /** A 128x256 frame at tile scale 2 held whole in a texture, as the game's doubled tiles are. */
    private static SpriteGeometry.Frame wholeFrame() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.left = 0f;
        frame.top = 0f;
        frame.width = 128f;
        frame.height = 256f;
        frame.uStart = 0f;
        frame.vStart = 0f;
        frame.uEnd = 1f;
        frame.vEnd = 1f;
        return frame;
    }

    /** A face takes its texels from a little inside its edge, so a corner is near its texel, not on it. */
    private static final float TEXEL_SLACK = 0.03f;

    private static boolean hasVertex(SpriteGeometry.Mesh mesh, float x, float y, float up, float u, float v) {
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float[] f = mesh.vertices;
            if (near(f[at], x) && near(f[at + 1], y) && near(f[at + 2], up)
                    && Math.abs(f[at + 3] - u) < TEXEL_SLACK && Math.abs(f[at + 4] - v) < TEXEL_SLACK) {
                return true;
            }
        }
        return false;
    }

    private static void floorFaceShowsTheSpritesDiamond() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        int triangles = new SpriteGeometry().addFace(mesh, wholeFrame(), 10, 20, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        check(triangles == 2, "a floor inside the frame is two triangles");
        check(hasVertex(mesh, 10f, 20f, 0f, 0.5f, 0.75f), "the tile's own corner is the top of the diamond");
        check(hasVertex(mesh, 11f, 20f, 0f, 1f, 0.875f), "the +x corner is the right of the diamond");
        check(hasVertex(mesh, 11f, 21f, 0f, 0.5f, 1f), "the far corner is the bottom of the diamond");
        check(hasVertex(mesh, 10f, 21f, 0f, 0f, 0.875f), "the +y corner is the left of the diamond");
    }

    private static void wallFaceIsClippedToTheTrimmedSprite() {
        SpriteGeometry.Frame frame = wholeFrame();
        frame.top = 128f;
        frame.height = 128f;
        frame.vStart = 0.5f;
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        new SpriteGeometry().addFace(mesh, frame, 0, 0, 1, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);
        check(mesh.triangles() > 0, "a wall that overlaps the trimmed sprite is drawn");
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            check(mesh.vertices[at + 4] >= 0.5f - 1e-4f, "no vertex reaches above the trimmed sprite");
            check(near(mesh.vertices[at], 0f), "a west wall stays on the tile's west edge");
        }
        check(hasVertex(mesh, 0f, 0f, ViewCamera.LEVEL_HEIGHT * (1f + 68f / 192f), 0.5f, 0.5f),
                "the cut runs four pixels past the sprite's top row, which is stretched over them");
    }

    private static void faceOutsideTheTrimmedSpriteAddsNothing() {
        SpriteGeometry.Frame frame = wholeFrame();
        frame.left = 64f;
        frame.width = 64f;
        frame.uStart = 0.5f;
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        int triangles = new SpriteGeometry().addFace(mesh, frame, 0, 0, 0, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);
        check(triangles == 0 && mesh.floats == 0, "a west wall draws nothing from a sprite that only has a right half");
    }

    private static void uprightSpriteStandsOnItsAnchor() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        new SpriteGeometry().addUpright(mesh, wholeFrame(), 4, 6, 0, 0.5f, 0.5f, 0f, 0f, 1f, false);
        check(mesh.triangles() == 2, "an upright sprite is two triangles");
        float halfWidth = (float) (Math.sqrt(2.0) / 2.0);
        check(hasVertex(mesh, 4.5f, 6.5f - halfWidth, ViewCamera.LEVEL_HEIGHT * 224f / 192f, 0f, 0f),
                "the frame's top left is half a tile diagonal to the left and 224 pixels up");
        check(hasVertex(mesh, 4.5f, 6.5f + halfWidth, -ViewCamera.LEVEL_HEIGHT * 32f / 192f, 1f, 1f),
                "the frame's bottom right is below the anchor by the rows under it");
    }

    private static final ClassDesc TESTS = ClassDesc.of("com.cameraunlock.zomboid.Tests");
    private static final CallSite.Method RUN = new CallSite.Method("com/cameraunlock/zomboid/Subject", "run", "()V");
    private static final CallSite.Method TARGET = new CallSite.Method("com/cameraunlock/zomboid/Tests", "target", "()V");
    private static final CallSite.Method HOOK = new CallSite.Method("com/cameraunlock/zomboid/Tests", "hook", "()V");

    private static byte[] subjectCallingTarget(int times) {
        MethodTypeDesc noArgs = MethodTypeDesc.of(ConstantDescs.CD_void);
        return ClassFile.of().build(ClassDesc.of("com.cameraunlock.zomboid.Subject"), type ->
                type.withFlags(ClassFile.ACC_PUBLIC).withMethodBody("run", noArgs, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                    for (int i = 0; i < times; i++) {
                        code.invokestatic(TESTS, "target", noArgs);
                    }
                    code.return_();
                }));
    }

    private static void insertAfterAddsTheHookOnce() throws Throwable {
        byte[] hooked = CallSite.insertAfter(subjectCallingTarget(1), RUN, TARGET, HOOK);
        Class<?> subject = MethodHandles.lookup().defineClass(hooked);
        hookCalls = 0;
        subject.getMethod("run").invoke(null);
        check(hookCalls == 1, "the hook runs once per call of the hooked method");
    }

    private static void insertAfterRejectsAMissingCall() {
        try {
            CallSite.insertAfter(subjectCallingTarget(0), RUN, TARGET, HOOK);
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("a method without the call is rejected");
    }

    private static void insertAfterRejectsTwoCalls() {
        try {
            CallSite.insertAfter(subjectCallingTarget(2), RUN, TARGET, HOOK);
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("a method with the call twice is rejected");
    }

    private static byte[] subjectWithField(String name, ClassDesc type, int flags) {
        return ClassFile.of().build(ClassDesc.of("com.cameraunlock.zomboid.Subject"), subject ->
                subject.withFlags(ClassFile.ACC_PUBLIC).withField(name, type, flags));
    }

    private static boolean staticIntAccepted(byte[] classBytes, String field) {
        try {
            CallSite.requireStaticInt(classBytes, field);
            return true;
        } catch (IllegalStateException rejected) {
            return false;
        }
    }

    private static void staticIntFieldIsRequiredAsDeclared() {
        int publicStatic = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;
        if (!staticIntAccepted(subjectWithField("x", ConstantDescs.CD_int, publicStatic), "x")) {
            throw new AssertionError("a static int field is accepted");
        }
        if (staticIntAccepted(subjectWithField("column", ConstantDescs.CD_int, publicStatic), "x")) {
            throw new AssertionError("a class without the field is rejected");
        }
        if (staticIntAccepted(subjectWithField("x", ConstantDescs.CD_float, publicStatic), "x")) {
            throw new AssertionError("a field of another type is rejected");
        }
        if (staticIntAccepted(subjectWithField("x", ConstantDescs.CD_int, ClassFile.ACC_PUBLIC), "x")) {
            throw new AssertionError("an instance field is rejected");
        }
        if (staticIntAccepted(subjectWithField("x", ConstantDescs.CD_int, publicStatic | ClassFile.ACC_FINAL), "x")) {
            throw new AssertionError("a final field is rejected");
        }
    }

    private static Agent.Patch hookAfterTarget(String owner) {
        return new Agent.Patch(owner, "run after target", classBytes -> CallSite.insertAfter(classBytes, RUN, TARGET, HOOK));
    }

    private static void transformerHasNoGapOnceEveryPatchIsIn() throws Throwable {
        Log.open(java.nio.file.Files.createTempDirectory("zomboid-headtracking-test"));
        Agent.PatchTransformer transformer = new Agent.PatchTransformer(java.util.List.of(hookAfterTarget(RUN.owner())));
        if (transformer.transform(null, "com/cameraunlock/zomboid/Other", null, null, subjectCallingTarget(1)) != null) {
            throw new AssertionError("a class no patch names is left alone");
        }
        byte[] hooked = transformer.transform(null, RUN.owner(), null, null, subjectCallingTarget(1));
        if (hooked == null) {
            throw new AssertionError("the class a patch names is rewritten");
        }
        if (transformer.gap() != null) {
            throw new AssertionError("no gap once the one patch is in, got: " + transformer.gap());
        }
    }

    private static void transformerLeavesAClassItCannotPatchAloneAndNamesTheGap() throws Throwable {
        Log.open(java.nio.file.Files.createTempDirectory("zomboid-headtracking-test"));
        // Two patches on a class that is really loaded, so the gap is not "no class". The first
        // fits and the second does not: nothing that went in before makes up for the one missing.
        String loaded = "com/cameraunlock/zomboid/Tests";
        Agent.Patch fits = hookAfterTarget(loaded);
        Agent.Patch doesNotFit = new Agent.Patch(loaded, "missing after target", classBytes ->
                CallSite.insertAfter(classBytes, new CallSite.Method(RUN.owner(), "missing", "()V"), TARGET, HOOK));
        Agent.PatchTransformer transformer = new Agent.PatchTransformer(java.util.List.of(fits, doesNotFit));
        byte[] hooked = transformer.transform(null, loaded, null, null, subjectCallingTarget(1));
        if (hooked == null) {
            throw new AssertionError("the patch that fits still goes in");
        }
        String gap = transformer.gap();
        if (gap == null || !gap.contains("missing after target")) {
            throw new AssertionError("the gap names the patch that is not in, got: " + gap);
        }

        Agent.PatchTransformer alone = new Agent.PatchTransformer(java.util.List.of(hookAfterTarget(loaded)));
        if (alone.transform(null, loaded, null, null, subjectCallingTarget(2)) != null) {
            throw new AssertionError("a class whose only patch does not fit is loaded unchanged");
        }
        if (alone.gap() == null) {
            throw new AssertionError("a patch that did not fit is a gap");
        }
    }

    private static void transformerNamesAClassTheGameDoesNotHave() {
        Agent.PatchTransformer transformer = new Agent.PatchTransformer(
                java.util.List.of(hookAfterTarget("com/cameraunlock/zomboid/NoSuchClass")));
        String gap = transformer.gap();
        if (gap == null || !gap.contains("no class com/cameraunlock/zomboid/NoSuchClass")) {
            throw new AssertionError("a class that cannot be found is a gap, got: " + gap);
        }
    }
}
