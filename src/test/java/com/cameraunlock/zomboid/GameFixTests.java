package com.cameraunlock.zomboid;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;

final class GameFixTests {
    private GameFixTests() {}

    static void run() {
        aKindIsTheClassUnderTheRoot();
        try {
            anAnswerIsGivenAtOnceUnlessNothingWasAsked();
            falseIsAnsweredAtOnceWhileTheListIsEmpty();
        } catch (AssertionError failed) {
            throw failed;
        } catch (Throwable thrown) {
            throw new AssertionError("a rewritten class could not be made or called", thrown);
        }
        System.out.println("all tests passed");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static void aKindIsTheClassUnderTheRoot() {
        // Nothing here descends from the game's root of components, so the climb ends where the game's own would: at the top.
        check(GameFixes.componentKind(ArrayList.class) == Object.class, "a class that is no component climbs to the top, as the game's own climb does");
        check(GameFixes.componentKind(ArrayList.class) == Object.class, "and is answered the same the second time");
        check(GameFixes.componentKind(Object.class) == Object.class, "the top is its own kind");
    }

    public static Class<?> answered(Class<?> asked) {
        return String.class;
    }

    private static void anAnswerIsGivenAtOnceUnlessNothingWasAsked() throws Throwable {
        ClassDesc classDesc = ConstantDescs.CD_Class;
        byte[] subject = ClassFile.of().build(ClassDesc.of("com.cameraunlock.zomboid.SubjectReturningAClass"), type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withMethodBody("kind", MethodTypeDesc.of(classDesc, classDesc), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code ->
                        code.aload(0).areturn()));
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/SubjectReturningAClass", "kind", "(Ljava/lang/Class;)Ljava/lang/Class;");
        CallSite.Method answer = new CallSite.Method("com/cameraunlock/zomboid/GameFixTests", "answered", "(Ljava/lang/Class;)Ljava/lang/Class;");
        Class<?> defined = MethodHandles.lookup().defineClass(CallSite.answerAtOnceUnlessNull(subject, within, answer));
        Method kind = defined.getMethod("kind", Class.class);
        check(kind.invoke(null, Integer.class) == String.class, "asked about a class, the method hands back the answer it was given");
        check(kind.invoke(null, (Object) null) == null, "asked about nothing, it does what it did");
    }

    private static void falseIsAnsweredAtOnceWhileTheListIsEmpty() throws Throwable {
        ClassDesc owner = ClassDesc.of("com.cameraunlock.zomboid.SubjectHoldingAList"), list = ClassDesc.of("java.util.ArrayList");
        byte[] subject = ClassFile.of().build(owner, type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withField("held", list, ClassFile.ACC_PUBLIC)
                .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code -> code
                        .aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                        .aload(0).new_(list).dup().invokespecial(list, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void).putfield(owner, "held", list)
                        .return_())
                .withMethodBody("has", MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_int), ClassFile.ACC_PUBLIC, code ->
                        code.iconst_1().ireturn()));
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/SubjectHoldingAList", "has", "(I)Z");
        Class<?> defined = MethodHandles.lookup().defineClass(CallSite.falseAtOnceWhileEmpty(subject, within, "held"));
        Object made = defined.getConstructor().newInstance();
        Method has = defined.getMethod("has", int.class);
        check(Boolean.FALSE.equals(has.invoke(made, 3)), "with nothing in the list the answer is no");
        @SuppressWarnings("unchecked")
        ArrayList<Object> held = (ArrayList<Object>) defined.getField("held").get(made);
        held.add("a bone");
        check(Boolean.TRUE.equals(has.invoke(made, 3)), "with something in it the method does what it did");
        boolean refused = false;
        try {
            CallSite.falseAtOnceWhileEmpty(subject, within, "missing");
        } catch (IllegalStateException expected) {
            refused = true;
        }
        check(refused, "a class without the list is rejected");
    }
}
