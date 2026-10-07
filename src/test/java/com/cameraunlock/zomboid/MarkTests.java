package com.cameraunlock.zomboid;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;

final class MarkTests {
    private MarkTests() {}

    static void run() {
        aChunkThatIsToldHasAnotherNumber();
        aBoxOfChunksIsToldWhole();
        aTallyIsCountedIntoAnother();
        try {
            aMethodOfAnObjectHandsItselfOverBeforeAnythingElse();
            aStaticMethodIsRefused();
        } catch (AssertionError failed) {
            throw failed;
        } catch (Throwable thrown) {
            throw new AssertionError("a rewritten class could not be made or called", thrown);
        }
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static void aChunkThatIsToldHasAnotherNumber() {
        int at = ChunkMarks.told(500, 700), beside = ChunkMarks.told(501, 700);
        ChunkMarks.tell(500, 700);
        check(ChunkMarks.told(500, 700) != at, "a chunk that is told has another number");
        check(ChunkMarks.told(501, 700) == beside, "the chunk beside it has the number it had");
        // The table is 64 chunks a side and the loaded map at most 19, so the chunk 64 on is never loaded with this one.
        check(ChunkMarks.slot(500, 700) == ChunkMarks.slot(500 + ChunkMarks.SIDE, 700 - ChunkMarks.SIDE), "a chunk shares its place with the one a table's width on");
        check(ChunkMarks.slot(-1, 0) != ChunkMarks.slot(0, 0) && ChunkMarks.slot(-1, 0) == ChunkMarks.slot(63, 0), "and chunks west of the map's origin have places of their own");
    }

    private static void aBoxOfChunksIsToldWhole() {
        int inside = ChunkMarks.told(900, 902), corner = ChunkMarks.told(901, 899), outside = ChunkMarks.told(902, 900);
        ChunkMarks.tell(899, 899, 901, 902);
        check(ChunkMarks.told(900, 902) == inside + 1 && ChunkMarks.told(901, 899) == corner + 1, "every chunk of a box that is told is told once");
        check(ChunkMarks.told(902, 900) == outside, "a chunk beside the box is not told");
    }

    private static void aTallyIsCountedIntoAnother() {
        OpenLight one = new OpenLight(), other = new OpenLight(), both = new OpenLight();
        float[] light = new float[3];
        for (int i = 0; i < 5; i++) {
            one.add(0.2f, 0.2f, 0.2f);
        }
        for (int i = 0; i < 7; i++) {
            other.add(0.8f, 0.7f, 0.6f);
        }
        new OpenLight().addTo(both);
        check(both.isEmpty(), "a tally with nothing counted adds nothing");
        one.addTo(both);
        both.commonest(light);
        check(Math.abs(light[0] - 0.2f) < 1e-6f, "one tally counted in gives its own light");
        other.addTo(both);
        both.commonest(light);
        check(Math.abs(light[0] - 0.8f) < 1e-6f && Math.abs(light[2] - 0.6f) < 1e-6f, "seven squares of the second outnumber five of the first");
        one.addTo(both);
        both.commonest(light);
        check(Math.abs(light[0] - 0.2f) < 1e-6f, "and the first counted in again outnumbers them");
    }

    private static Object marker;
    private static long markedWith;
    private static int markedBefore;

    public static void marked(Object levels, long flags) throws ReflectiveOperationException {
        marker = levels;
        markedWith = flags;
        // What the method itself does has not been done yet.
        markedBefore = levels.getClass().getField("done").getInt(levels);
    }

    private static byte[] subject(ClassDesc owner, int flags) {
        MethodTypeDesc invalidate = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int, ConstantDescs.CD_long);
        boolean ofAnObject = (flags & ClassFile.ACC_STATIC) == 0;
        return ClassFile.of().build(owner, type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withField("done", ConstantDescs.CD_int, ClassFile.ACC_PUBLIC)
                .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code -> code
                        .aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void).return_())
                .withMethodBody("invalidateLevel", invalidate, flags, code -> {
                    if (ofAnObject) {
                        code.aload(0).iload(1).putfield(owner, "done", ConstantDescs.CD_int);
                    }
                    code.return_();
                }));
    }

    private static void aMethodOfAnObjectHandsItselfOverBeforeAnythingElse() throws Throwable {
        ClassDesc owner = ClassDesc.of("com.cameraunlock.zomboid.SubjectMarkingALevel"), tests = ClassDesc.of("com.cameraunlock.zomboid.MarkTests");
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/SubjectMarkingALevel", "invalidateLevel", "(IJ)V");
        byte[] patched = CallSite.insertAtStartOfInstanceMethod(subject(owner, ClassFile.ACC_PUBLIC), within, code -> code.aload(0).lload(2)
                .invokestatic(tests, "marked", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object, ConstantDescs.CD_long)));
        Class<?> defined = MethodHandles.lookup().defineClass(patched);
        Object levels = defined.getConstructor().newInstance();
        defined.getMethod("invalidateLevel", int.class, long.class).invoke(levels, 3, 256L);
        check(marker == levels && markedWith == 256L, "the hook is handed the object and the flags the method was called with");
        check(markedBefore == 0 && defined.getField("done").getInt(levels) == 3, "before the method does what it did, which it still does");
    }

    private static void aStaticMethodIsRefused() {
        ClassDesc owner = ClassDesc.of("com.cameraunlock.zomboid.SubjectMarkingNothing");
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/SubjectMarkingNothing", "invalidateLevel", "(IJ)V");
        byte[] subject = subject(owner, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC);
        try {
            CallSite.insertAtStartOfInstanceMethod(subject, within, code -> code.aload(0).pop());
        } catch (IllegalStateException refused) {
            return;
        }
        throw new AssertionError("a static method by the name took a patch that loads the object it has not got");
    }
}
