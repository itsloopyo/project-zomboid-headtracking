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
        windowIsCentredInItsWorkArea();
        insertAfterAddsTheHookOnce();
        insertAfterRejectsAMissingCall();
        insertAfterRejectsTwoCalls();
        System.out.println("all tests passed");
    }

    private static final float[] PROJECTION = ViewCamera.projection((float) Math.toRadians(70), 16f / 9f, 0.05f, 200f);

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

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static void cameraLooksAlongItsYaw() {
        float[] ahead = project(ViewCamera.view(3f, 7f, 1.6f, 0f, 0f), 13f, 7f, 1.6f);
        check(near(ahead[0], 0f) && near(ahead[1], 0f), "a point straight ahead along +x is at the screen centre");
        check(project(ViewCamera.view(3f, 7f, 1.6f, 0f, 0f), -7f, 7f, 1.6f) == null, "a point behind the eye is not drawn");

        float yaw = (float) (Math.PI / 2);
        float[] aheadY = project(ViewCamera.view(3f, 7f, 1.6f, yaw, 0f), 3f, 17f, 1.6f);
        check(near(aheadY[0], 0f) && near(aheadY[1], 0f), "at a quarter turn the camera looks along +y");
    }

    private static void cameraPutsPlusYOnTheRightWhenFacingPlusX() {
        float[] view = ViewCamera.view(0f, 0f, 1.6f, 0f, 0f);
        check(project(view, 10f, 2f, 1.6f)[0] > 0f, "+y is on the right when facing +x");
        check(project(view, 10f, -2f, 1.6f)[0] < 0f, "-y is on the left when facing +x");
    }

    private static void cameraPutsHigherPointsHigher() {
        float[] view = ViewCamera.view(0f, 0f, 1.6f, 0f, 0f);
        check(project(view, 10f, 0f, 3f)[1] > 0f, "a point above eye height is in the top half");
        check(project(view, 10f, 0f, 0f)[1] < 0f, "the ground is in the bottom half");
    }

    private static void cameraPitchLooksUp() {
        float[] view = ViewCamera.view(0f, 0f, 1.6f, 0f, (float) Math.toRadians(20));
        check(project(view, 10f, 0f, 1.6f)[1] < 0f, "looking up moves the horizon down the screen");
    }

    private static void cameraTurnsRightWithPositiveYaw() {
        float[] view = ViewCamera.view(0f, 0f, 1.6f, (float) Math.toRadians(20), 0f);
        check(project(view, 10f, 0f, 1.6f)[0] < 0f, "turning right moves what was ahead to the left");
    }

    private static void windowIsCentredInItsWorkArea() {
        check(WindowPlacement.centredOrigin(0, 5120, 1936) == 1592, "a window is centred across a work area at the origin");
        check(WindowPlacement.centredOrigin(0, 1392, 1119) == 136, "a window is centred down a work area a taskbar shortens");
        check(WindowPlacement.centredOrigin(-1920, 1920, 1296) == -1608, "a monitor left of the primary has a negative origin");
        check(WindowPlacement.centredOrigin(40, 1040, 759) == 180, "a work area below a top taskbar starts below it");
    }

    private static final ClassDesc TESTS =ClassDesc.of("com.cameraunlock.zomboid.Tests");
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
}
