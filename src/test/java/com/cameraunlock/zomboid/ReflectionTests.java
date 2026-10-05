package com.cameraunlock.zomboid;

import java.nio.FloatBuffer;

/** Tests of where the viewer is drawn to be seen in the glass of a window. */
final class ReflectionTests {
    private static final float TAN = (float) Math.tan(Math.toRadians(32.5)), ASPECT = 16f / 9f;

    private ReflectionTests() {}

    static void run() {
        mirroredViewPutsAPointWhereTheGlassShowsIt();
        eyeBehindTheGlassSeesTheWorldAsTheGlassShowsIt();
        openingsStandBeforeTheGlassOnTheEyesSide();
    }

    private static OwnReflection.Glass wall(boolean north, float at, float side, float... along) {
        OwnReflection.Glass glass = new OwnReflection.Glass();
        glass.found = true;
        glass.north = north;
        glass.at = at;
        glass.side = side;
        glass.count = along.length;
        System.arraycopy(along, 0, glass.along, 0, along.length);
        glass.foot = 0f;
        return glass;
    }

    private static ViewCamera.Pose eye() {
        ViewCamera.Pose camera = new ViewCamera.Pose();
        ViewCamera.clean(camera, 20.4f, 31.7f, 1.6f, (float) Math.toRadians(80), (float) Math.toRadians(-6));
        ViewCamera.turn(camera, (float) Math.toRadians(12), (float) Math.toRadians(4), (float) Math.toRadians(9), true);
        return camera;
    }

    private static float[] seen(float[] view, float x, float y, float up) {
        return new float[] {
            view[0] * x + view[4] * y + view[8] * up + view[12],
            view[1] * x + view[5] * y + view[9] * up + view[13],
            view[2] * x + view[6] * y + view[10] * up + view[14]};
    }

    private static void mirroredViewPutsAPointWhereTheGlassShowsIt() {
        for (boolean north : new boolean[] {true, false}) {
            OwnReflection.Glass glass = wall(north, 34f, -1f, 20f);
            float[] plain = ViewCamera.view(eye()), mirrored = ViewCamera.view(eye());
            OwnReflection.mirror(mirrored, glass);
            float x = 20.9f, y = 32.2f, up = 1.1f;
            float[] inGlass = seen(mirrored, x, y, up);
            float[] behind = north ? seen(plain, x, 2f * 34f - y, up) : seen(plain, 2f * 34f - x, y, up);
            for (int axis = 0; axis < 3; axis++) {
                check(Math.abs(inGlass[axis] - behind[axis]) < 1e-3f, "a point drawn through the mirrored view is where its image stands, as far behind the glass as it is before it");
            }
            float[] onGlass = north ? seen(mirrored, x, 34f, up) : seen(mirrored, 34f, y, up);
            float[] there = north ? seen(plain, x, 34f, up) : seen(plain, 34f, y, up);
            check(Math.abs(onGlass[0] - there[0]) < 1e-3f && Math.abs(onGlass[2] - there[2]) < 1e-3f, "and a point on the glass stays where it is");
        }
    }

    private static void eyeBehindTheGlassSeesTheWorldAsTheGlassShowsIt() {
        for (boolean north : new boolean[] {true, false}) {
            OwnReflection.Glass glass = wall(north, north ? 34f : 23f, -1f, 20f);
            ViewCamera.Pose camera = eye();
            if (!north) {
                ViewCamera.clean(camera, 20.4f, 31.7f, 1.6f, (float) Math.toRadians(10), 0f);
            }
            ViewCamera.Pose behind = new ViewCamera.Pose();
            OwnReflection.mirror(camera, glass, behind);
            // Where the viewer stands, and where the glass shows them.
            float x = camera.eyeX + 0.1f, y = camera.eyeY - 0.05f, up = 1.2f;
            float imageX = north ? x : 2f * glass.at - x, imageY = north ? 2f * glass.at - y : y;
            float[] ofImage = new float[2], ofViewer = new float[2];
            check(ViewCamera.project(camera, TAN, ASPECT, imageX, imageY, up, ofImage), "the viewer's image is before the eye that looks at the glass");
            check(ViewCamera.project(behind, TAN, ASPECT, x, y, up, ofViewer), "and the viewer is before the eye behind it");
            check(Math.abs(ofImage[0] - ofViewer[0]) < 1e-3f && Math.abs(ofImage[1] - ofViewer[1]) < 1e-3f,
                    "at the same place in the picture: the light is worked out where the viewer stands and shown where their image is");
        }
    }

    private static void openingsStandBeforeTheGlassOnTheEyesSide() {
        ViewCamera.Pose camera = eye();
        OwnReflection.Glass glass = wall(true, 34f, -1f, 20f, 22f);
        FloatBuffer corners = FloatBuffer.allocate(OwnReflection.MOST * 18);
        check(OwnReflection.corners(glass, camera, corners) == 12, "two triangles to a window");
        float least = Float.MAX_VALUE, most = -Float.MAX_VALUE;
        for (int corner = 0; corner < 12; corner++) {
            float y = corners.get(corner * 3 + 1) + camera.eyeY;
            check(y < 34f && y > 33.9f, "every corner is a hair on the eye's side of the glass");
            least = Math.min(least, corners.get(corner * 3) + camera.eyeX);
            most = Math.max(most, corners.get(corner * 3) + camera.eyeX);
            float up = corners.get(corner * 3 + 2) + camera.eyeUp;
            check(Math.abs(up) < 1e-4f || Math.abs(up - ViewCamera.LEVEL_HEIGHT) < 1e-4f, "from the floor to the floor above");
        }
        check(Math.abs(least - 20f) < 1e-4f && Math.abs(most - 23f) < 1e-4f, "over each window's own tile of the wall");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
