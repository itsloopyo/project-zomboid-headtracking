package com.cameraunlock.zomboid;

import java.util.Random;

/** Which models a pass leaves out: none that the pass could show. */
public final class CrowdTests {
    private CrowdTests() {}

    private static final float TAN_HIGH = (float) Math.tan(Math.toRadians(55)), ASPECT = 16f / 9f;

    static void run() {
        aPointInThePictureIsOutsideNoEdge();
        aPointPastAnEdgeIsThatFarOutside();
        aShadowThatFallsIntoThePictureIsKept();
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static float[] multiply(float[] m, float x, float y, float z) {
        float[] out = new float[4];
        for (int row = 0; row < 4; row++) {
            out[row] = m[row] * x + m[4 + row] * y + m[8 + row] * z + m[12 + row];
        }
        return out;
    }

    private static ViewCamera.Pose pose(Random random) {
        ViewCamera.Pose pose = new ViewCamera.Pose();
        ViewCamera.clean(pose, 100f + random.nextFloat() * 50f, 200f + random.nextFloat() * 50f, 1.6f + random.nextFloat() * 5f,
                (float) (random.nextFloat() * 2 * Math.PI), (float) Math.toRadians(random.nextFloat() * 160 - 80));
        ViewCamera.turn(pose, (float) Math.toRadians(random.nextFloat() * 180 - 90), (float) Math.toRadians(random.nextFloat() * 60 - 30),
                (float) Math.toRadians(random.nextFloat() * 120 - 60), random.nextBoolean());
        return pose;
    }

    /** Against the matrices the picture is drawn with, for views rolled and turned every way: in the picture and outside no edge are the same thing. */
    private static void aPointInThePictureIsOutsideNoEdge() {
        Random random = new Random(7);
        float[] projection = ViewCamera.projection(TAN_HIGH, ASPECT, 0.05f, 400f);
        float[] past = new float[5];
        int inside = 0, outside = 0;
        for (int trial = 0; trial < 20000; trial++) {
            ViewCamera.Pose pose = pose(random);
            float[] view = ViewCamera.view(pose);
            float x = pose.eyeX + random.nextFloat() * 80f - 40f, y = pose.eyeY + random.nextFloat() * 80f - 40f, up = pose.eyeUp + random.nextFloat() * 30f - 15f;
            float[] eye = multiply(view, x, y, up);
            float[] clip = multiply(projection, eye[0], eye[1], eye[2]);
            // A point within a hair of an edge is on either side of it by rounding.
            float edge = Math.min(clip[3] - Math.abs(clip[0]), clip[3] - Math.abs(clip[1]));
            if (Math.abs(edge) < 1e-2f || Math.abs(clip[3]) < 1e-2f) {
                continue;
            }
            boolean drawn = clip[3] > 0f && edge > 0f;
            PictureEdges.outsideOf(view, TAN_HIGH * ASPECT, TAN_HIGH, x, y, up, past);
            float most = Math.max(Math.max(past[0], past[1]), Math.max(past[2], Math.max(past[3], past[4])));
            check(drawn == most < 0f, "a point is outside one of the picture's edges exactly when the projection puts it out of the picture");
            if (drawn) {
                inside++;
            } else {
                outside++;
            }
        }
        check(inside > 1000 && outside > 1000, "points of both kinds were tried");
    }

    private static void aPointPastAnEdgeIsThatFarOutside() {
        ViewCamera.Pose pose = new ViewCamera.Pose();
        ViewCamera.clean(pose, 0f, 0f, 0f, 0f, 0f);
        float[] view = ViewCamera.view(pose);
        float[] past = new float[5];
        // Facing +x with +y on the right and a picture as wide as it is deep: the right edge is the line y = x.
        PictureEdges.outsideOf(view, 1f, 1f, 10f, 10f, 0f, past);
        check(Math.abs(past[1]) < 1e-4f, "a point on the right edge is nought outside it");
        PictureEdges.outsideOf(view, 1f, 1f, 10f, 12f, 0f, past);
        check(Math.abs(past[1] - (float) Math.sqrt(2.0)) < 1e-4f && past[2] < 0f, "two tiles to the right of the edge at a slant of one in one is the square root of two outside it, and inside the left");
        PictureEdges.outsideOf(view, 1f, 1f, -3f, 0f, 0f, past);
        check(Math.abs(past[0] - 3f) < 1e-4f, "three tiles behind the eye is three outside the plane through it");
        PictureEdges.outsideOf(view, 1f, 1f, 10f, 0f, 13f, past);
        check(past[3] > 0f && past[4] < 0f, "above the top edge is outside the top and inside the bottom");
    }

    /** A sun's map by its third row alone, which is all the test reads: the way to the light, over the map's depth. */
    private static float[] mapTowards(float x, float y, float up) {
        float length = (float) Math.sqrt(x * x + y * y + up * up), range = 60f;
        float[] map = new float[16];
        map[2] = -x / length / range;
        map[6] = -y / length / range;
        map[10] = -up / length / range;
        return map;
    }

    private static void aShadowThatFallsIntoThePictureIsKept() {
        ViewCamera.Pose pose = new ViewCamera.Pose();
        ViewCamera.clean(pose, 0f, 0f, 1.6f, 0f, 0f);
        float[] view = ViewCamera.view(pose);
        float wide = 1f, high = 0.6f, round = 3f;
        // The light low in the west, behind an eye that faces east: a shadow runs east.
        float[] fromBehind = mapTowards(-1f, 0f, 0.2f);
        check(!PictureEdges.shadesNothingSeen(view, wide, high, fromBehind, -8f, 0f, 0.9f, round), "someone behind the eye, with the light behind them, shades ground the eye sees");
        check(!PictureEdges.shadesNothingSeen(view, wide, high, fromBehind, 10f, 0f, 0.9f, round), "someone in the picture is kept");
        // The light low in the east: the shadow of someone behind the eye runs further behind.
        float[] fromAhead = mapTowards(1f, 0f, 0.2f);
        check(PictureEdges.shadesNothingSeen(view, wide, high, fromAhead, -8f, 0f, 0.9f, round), "someone behind the eye, with the light ahead, shades nothing the eye sees");
        // The light low in the north (-y): shadows run south, to the eye's right.
        float[] fromLeft = mapTowards(0f, -1f, 0.2f);
        check(!PictureEdges.shadesNothingSeen(view, wide, high, fromLeft, 10f, -30f, 0.9f, round), "someone far to the left, with the light beyond them, shades into the picture");
        check(PictureEdges.shadesNothingSeen(view, wide, high, fromLeft, 10f, 30f, 0.9f, round), "someone far to the right of it shades away from the picture");
        // Overhead the shadow is at the feet.
        float[] fromAbove = mapTowards(0f, 0f, 1f);
        check(PictureEdges.shadesNothingSeen(view, wide, high, fromAbove, -8f, 0f, 0.9f, round), "under an overhead light someone behind the eye shades nothing the eye sees");
        check(!PictureEdges.shadesNothingSeen(view, wide, high, fromAbove, -2f, 0f, 0.9f, round), "but someone within their own reach of the eye is kept");
        // Looked for against the shadow itself: wherever a point of the shadow is in the picture, the caster is kept.
        Random random = new Random(11);
        float[] projection = ViewCamera.projection(high, wide / high, 0.05f, 400f);
        int kept = 0, left = 0;
        for (int trial = 0; trial < 20000; trial++) {
            ViewCamera.Pose turned = pose(random);
            float[] seen = ViewCamera.view(turned);
            float lightX = random.nextFloat() * 2f - 1f, lightY = random.nextFloat() * 2f - 1f, lightUp = 0.05f + random.nextFloat();
            float[] map = mapTowards(lightX, lightY, lightUp);
            float length = (float) Math.sqrt(lightX * lightX + lightY * lightY + lightUp * lightUp);
            float x = turned.eyeX + random.nextFloat() * 60f - 30f, y = turned.eyeY + random.nextFloat() * 60f - 30f, up = 0.9f;
            boolean out = PictureEdges.shadesNothingSeen(seen, wide, high, map, x, y, up, round);
            if (out) {
                left++;
            } else {
                kept++;
            }
            // Points down the middle of the shadow, from the person to the ground.
            for (float along = 0f; out && along <= up * length / lightUp; along += 0.25f) {
                float[] eye = multiply(seen, x - lightX / length * along, y - lightY / length * along, up - lightUp / length * along);
                float[] clip = multiply(projection, eye[0], eye[1], eye[2]);
                check(!(clip[3] > 0f && Math.abs(clip[0]) < clip[3] && Math.abs(clip[1]) < clip[3]), "no point of the shadow of someone left out is in the picture");
            }
        }
        check(kept > 1000 && left > 1000, "casters of both kinds were tried");
    }
}
