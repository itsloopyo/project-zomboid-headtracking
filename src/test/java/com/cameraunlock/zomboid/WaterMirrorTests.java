package com.cameraunlock.zomboid;

/** Tests of what water shows: that a point on it shows the same place whatever way the head is turned. */
final class WaterMirrorTests {
    private static final float TAN = (float) Math.tan(Math.toRadians(32.5)), ASPECT = 16f / 9f;

    private WaterMirrorTests() {}

    static void run() {
        Checks.run("pointOnTheWaterShowsTheSamePlaceWhateverWayTheHeadIsTurned", WaterMirrorTests::pointOnTheWaterShowsTheSamePlaceWhateverWayTheHeadIsTurned);
        Checks.run("eyesImageIsAsFarUnderTheWaterAsTheEyeIsOverIt", WaterMirrorTests::eyesImageIsAsFarUnderTheWaterAsTheEyeIsOverIt);
        Checks.run("planeTouchesTheWaterUnderTheEyeAndLiesInItAcrossARiver", WaterMirrorTests::planeTouchesTheWaterUnderTheEyeAndLiesInItAcrossARiver);
        Checks.run("windRoughensWaterAndNeverSmoothsItToGlass", WaterMirrorTests::windRoughensWaterAndNeverSmoothsItToGlass);
    }

    private static void pointOnTheWaterShowsTheSamePlaceWhateverWayTheHeadIsTurned() {
        float eyeX = 6450.5f, eyeY = 5180.5f;
        float[] plane = new float[4];
        WaterMirror.plane(Terrain.HILLS, eyeX, eyeY, plane);
        float eyeUp = Terrain.HILLS.waterLevel(eyeX, eyeY) + 0.34f + 1.6f;
        // A point of the water twelve tiles off, and what a mirror lying in the plane shows there: a place
        // thirty tiles along the eye's line thrown back by it. Neither goes by the way the head is turned.
        float waterX = eyeX + 3f, waterY = eyeY - 12f;
        float waterUp = (plane[3] - plane[0] * waterX - plane[1] * waterY) / plane[2];
        float lineX = waterX - eyeX, lineY = waterY - eyeY, lineUp = waterUp - eyeUp;
        float into = 2f * (plane[0] * lineX + plane[1] * lineY + plane[2] * lineUp);
        float length = (float) Math.sqrt(lineX * lineX + lineY * lineY + lineUp * lineUp);
        float shownX = waterX + (lineX - into * plane[0]) / length * 30f, shownY = waterY + (lineY - into * plane[1]) / length * 30f,
                shownUp = waterUp + (lineUp - into * plane[2]) / length * 30f;
        ViewCamera.Pose eye = new ViewCamera.Pose(), under = new ViewCamera.Pose();
        float[] onWater = new float[2], inPicture = new float[2], read = new float[2];
        int seen = 0;
        for (int yaw = 230; yaw <= 310; yaw += 20) {
            for (int pitch = -80; pitch <= 20; pitch += 10) {
                for (int roll = -30; roll <= 30; roll += 15) {
                    ViewCamera.clean(eye, eyeX, eyeY, eyeUp, (float) Math.toRadians(yaw), (float) Math.toRadians(pitch));
                    ViewCamera.turn(eye, 0f, 0f, (float) Math.toRadians(roll), true);
                    WaterMirror.mirror(eye, plane, under);
                    if (!ViewCamera.project(eye, TAN, ASPECT, waterX, waterY, waterUp, onWater) || Math.abs(onWater[0]) > 1f || Math.abs(onWater[1]) > 1f) {
                        continue;
                    }
                    seen++;
                    check(ViewCamera.project(under, TAN, ASPECT, shownX, shownY, shownUp, inPicture), "what the water shows is before the eye's image under the water");
                    check(Math.abs(inPicture[0] - onWater[0]) < 2e-3f && Math.abs(inPicture[1] - onWater[1]) < 2e-3f,
                            "the picture drawn from under the water has what a point of the water shows at the place the eye's picture draws that point, at yaw "
                                    + yaw + " pitch " + pitch + " roll " + roll);
                    // The sum the world's program does for a pixel of water (overWater): the place shown, counted from
                    // the eye, is put through the plane as the pipeline hands it over, drawn as the eye's own picture
                    // would draw it and scaled by the margin the wider picture was drawn with.
                    float fromX = shownX - eyeX, fromY = shownY - eyeY, fromUp = shownUp - eyeUp;
                    float through = 2f * (plane[0] * fromX + plane[1] * fromY + plane[2] * fromUp + WaterMirror.above(plane, eyeX, eyeY, eyeUp));
                    check(ViewCamera.project(eye, TAN, ASPECT, eyeX + fromX - through * plane[0], eyeY + fromY - through * plane[1],
                            eyeUp + fromUp - through * plane[2], read), "the image of what the water shows is before the eye");
                    check(ViewCamera.project(under, TAN * WaterMirror.MARGIN, ASPECT, shownX, shownY, shownUp, inPicture), "and the place itself before the eye's image");
                    check(Math.abs(read[0] / WaterMirror.MARGIN - inPicture[0]) < 2e-3f && Math.abs(read[1] / WaterMirror.MARGIN - inPicture[1]) < 2e-3f,
                            "the wider picture is read where it holds the place the water shows, at yaw " + yaw + " pitch " + pitch + " roll " + roll);
                }
            }
        }
        check(seen > 60, "the point is in the picture at most of the ways the head was turned: " + seen);
    }

    private static void eyesImageIsAsFarUnderTheWaterAsTheEyeIsOverIt() {
        float[] plane = new float[4];
        WaterMirror.plane(Terrain.HILLS, 11700.5f, 6560.5f, plane);
        ViewCamera.Pose eye = new ViewCamera.Pose(), under = new ViewCamera.Pose();
        ViewCamera.clean(eye, 11700.5f, 6560.5f, Terrain.HILLS.waterLevel(11700.5f, 6560.5f) + 2f, (float) Math.toRadians(40), (float) Math.toRadians(-25));
        ViewCamera.turn(eye, 0f, 0f, (float) Math.toRadians(20), true);
        WaterMirror.mirror(eye, plane, under);
        float over = WaterMirror.above(plane, eye.eyeX, eye.eyeY, eye.eyeUp);
        check(over > 1.9f, "the eye is over the water");
        check(Math.abs(WaterMirror.above(plane, under.eyeX, under.eyeY, under.eyeUp) + over) < 2e-3f, "and its image as far under it");
        float along = plane[0] * eye.forwardX + plane[1] * eye.forwardY + plane[2] * eye.forwardUp;
        check(Math.abs(plane[0] * under.forwardX + plane[1] * under.forwardY + plane[2] * under.forwardUp + along) < 1e-5f, "looking up by as much as the eye looks down");
        check(Math.abs(under.forwardX * under.forwardX + under.forwardY * under.forwardY + under.forwardUp * under.forwardUp - 1f) < 1e-5f
                && Math.abs(under.rightX * under.upX + under.rightY * under.upY + under.rightUp * under.upUp) < 1e-5f, "with its axes still square and of length one");
    }

    private static void planeTouchesTheWaterUnderTheEyeAndLiesInItAcrossARiver() {
        float[] plane = new float[4];
        float[][] places = {{6450.5f, 5180.5f}, {11700.5f, 6560.5f}, {12300.5f, 6600.5f}, {10600.5f, 9300.5f}};
        for (float[] place : places) {
            WaterMirror.plane(Terrain.HILLS, place[0], place[1], plane);
            check(Math.abs(WaterMirror.above(plane, place[0], place[1], Terrain.HILLS.waterLevel(place[0], place[1]))) < 2e-3f, "the plane touches the water under the eye");
            for (int way = 0; way < 8; way++) {
                float x = place[0] + 30f * (float) Math.cos(way * Math.PI / 4), y = place[1] + 30f * (float) Math.sin(way * Math.PI / 4);
                check(Math.abs(WaterMirror.above(plane, x, y, Terrain.HILLS.waterLevel(x, y))) < 0.12f,
                        "thirty tiles off the water is within an eighth of a tile of the plane, at " + place[0] + ", " + place[1] + " way " + way);
                // As far as High sees. The eye's line thrown back by water that far off the plane is read from
                // the picture under a degree from where it should be, and nowhere does that change at a step.
                x = place[0] + 80f * (float) Math.cos(way * Math.PI / 4);
                y = place[1] + 80f * (float) Math.sin(way * Math.PI / 4);
                check(Math.abs(WaterMirror.above(plane, x, y, Terrain.HILLS.waterLevel(x, y))) < 0.65f,
                        "eighty tiles off the water is within two thirds of a tile of the plane, at " + place[0] + ", " + place[1] + " way " + way);
            }
        }
        float[] flat = new float[4];
        WaterMirror.plane(Terrain.of(true), 6450.5f, 5180.5f, flat);
        check(flat[0] == 0f && flat[1] == 0f && flat[2] == 1f && Math.abs(flat[3] - Terrain.of(true).waterLevel(0f, 0f)) < 1e-6f, "on level land the plane is level, at the water's height");
    }

    private static void windRoughensWaterAndNeverSmoothsItToGlass() {
        check(WaterMirror.ruffle(0f) > 0.5f, "in a dead calm water still has ripples");
        check(WaterMirror.ruffle(0.8f) > 1.5f * WaterMirror.ruffle(0.1f), "a strong wind roughens it well past what a calm does");
        check(WaterMirror.ruffle(2f) == WaterMirror.ruffle(5f), "and a gale no more than a gale");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}
