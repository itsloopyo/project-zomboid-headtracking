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
        new SpriteGeometry().addUpright(mesh, wholeFrame(), 4, 6, 0, 0.5f, 0.5f, 0f, 0f, 1f);
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
