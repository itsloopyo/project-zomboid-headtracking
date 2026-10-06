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
            anAnswerIsKeptUntilWhatItCameFromChanges();
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

    private static void anAnswerIsKeptUntilWhatItCameFromChanges() throws Throwable {
        ClassDesc parent = ClassDesc.of("com.cameraunlock.zomboid.SubjectHolding"), child = ClassDesc.of("com.cameraunlock.zomboid.SubjectAsking");
        MethodTypeDesc getsObject = MethodTypeDesc.of(ConstantDescs.CD_Object), takesObject = MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object);
        // The parent holds one thing, hands it over when asked and counts the askings.
        byte[] holding = ClassFile.of().build(parent, type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withField("held", ConstantDescs.CD_Object, ClassFile.ACC_PUBLIC)
                .withField("asked", ConstantDescs.CD_int, ClassFile.ACC_PUBLIC)
                .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code -> code
                        .aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void).return_())
                .withMethodBody("put", takesObject, ClassFile.ACC_PUBLIC, code -> code.aload(0).aload(1).putfield(parent, "held", ConstantDescs.CD_Object).return_())
                .withMethodBody("find", getsObject, ClassFile.ACC_PUBLIC, code -> code
                        .aload(0).aload(0).getfield(parent, "asked", ConstantDescs.CD_int).iconst_1().iadd().putfield(parent, "asked", ConstantDescs.CD_int)
                        .aload(0).getfield(parent, "held", ConstantDescs.CD_Object).areturn()));
        byte[] asking = ClassFile.of().build(child, type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withSuperclass(parent)
                .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code -> code
                        .aload(0).invokespecial(parent, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void).return_())
                .withMethodBody("get", getsObject, ClassFile.ACC_PUBLIC, code -> code.aload(0).invokevirtual(child, "find", getsObject).areturn()));
        CallSite.Method getter = new CallSite.Method("com/cameraunlock/zomboid/SubjectAsking", "get", "()Ljava/lang/Object;");
        java.util.List<CallSite.Method> changers = java.util.List.of(new CallSite.Method("com/cameraunlock/zomboid/SubjectAsking", "put", "(Ljava/lang/Object;)V"));
        byte[] kept = CallSite.keepInField(asking, getter, "kept", "com/cameraunlock/zomboid/SubjectHolding", changers);
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Class<?> base = lookup.defineClass(holding);
        Class<?> defined = lookup.defineClass(kept);
        Object made = defined.getConstructor().newInstance();
        Method get = defined.getMethod("get"), put = defined.getMethod("put", Object.class);
        check(get.invoke(made) == null && get.invoke(made) == null && base.getField("asked").getInt(made) == 2, "while there is nothing to keep, it is asked for each time");
        put.invoke(made, "first");
        check("first".equals(get.invoke(made)) && "first".equals(get.invoke(made)) && "first".equals(get.invoke(made)), "what is found is handed back");
        check(base.getField("asked").getInt(made) == 3, "and found once, it is not asked for again");
        put.invoke(made, "second");
        check("second".equals(get.invoke(made)) && base.getField("asked").getInt(made) == 4, "after a change it is asked for afresh");
        base.getField("held").set(made, null);
        put.invoke(made, (Object) null);
        check(get.invoke(made) == null, "and what was taken away is not handed back");
        boolean refused = false;
        try {
            CallSite.keepInField(kept, getter, "kept", "com/cameraunlock/zomboid/SubjectHolding", changers);
        } catch (IllegalStateException expected) {
            refused = true;
        }
        check(refused, "a class that already keeps it is rejected");
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
