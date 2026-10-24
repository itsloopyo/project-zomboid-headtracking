package com.cameraunlock.zomboid;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

final class GameFixTests {
    private GameFixTests() {}

    static void run() {
        Checks.run("aKindIsKeptOnceItIsLearnt", GameFixTests::aKindIsKeptOnceItIsLearnt);
        Checks.run("anAnswerIsWorkedOutOnceForEachThingAsked", GameFixTests::anAnswerIsWorkedOutOnceForEachThingAsked);
        Checks.run("falseIsAnsweredAtOnceWhileTheListIsEmpty", GameFixTests::falseIsAnsweredAtOnceWhileTheListIsEmpty);
        Checks.run("anAnswerIsKeptUntilWhatItCameFromChanges", GameFixTests::anAnswerIsKeptUntilWhatItCameFromChanges);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void aKindIsKeptOnceItIsLearnt() {
        check(GameFixes.knownKind(ArrayList.class) == null && GameFixes.knownKind(null) == null, "nothing is known of a class not yet asked about, nor of no class");
        GameFixes.learnKind(ArrayList.class, AbstractList.class);
        GameFixes.learnKind(null, Object.class);
        check(GameFixes.knownKind(ArrayList.class) == AbstractList.class, "what was answered for a class is known of it afterwards");
        check(GameFixes.knownKind(LinkedList.class) == null && GameFixes.knownKind(null) == null, "and of no other");
    }

    private static final Map<Class<?>, Class<?>> learnt = new HashMap<>();
    private static int worked;

    public static Class<?> known(Class<?> asked) {
        return learnt.get(asked);
    }

    public static void learn(Class<?> asked, Class<?> answer) {
        learnt.put(asked, answer);
    }

    public static Class<?> parentOf(Class<?> asked) {
        worked++;
        return asked.getSuperclass();
    }

    private static void anAnswerIsWorkedOutOnceForEachThingAsked() throws Throwable {
        ClassDesc classDesc = ConstantDescs.CD_Class, tests = ClassDesc.of("com.cameraunlock.zomboid.GameFixTests");
        // The subject writes over its argument before it answers, as a method that climbs from what it was asked does.
        byte[] subject = ClassFile.of().build(ClassDesc.of("com.cameraunlock.zomboid.SubjectReturningAClass"), type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withMethodBody("kind", MethodTypeDesc.of(classDesc, classDesc), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code ->
                        code.aload(0).invokestatic(tests, "parentOf", MethodTypeDesc.of(classDesc, classDesc)).astore(0).aload(0).areturn()));
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/SubjectReturningAClass", "kind", "(Ljava/lang/Class;)Ljava/lang/Class;");
        CallSite.Method known = new CallSite.Method("com/cameraunlock/zomboid/GameFixTests", "known", "(Ljava/lang/Class;)Ljava/lang/Class;");
        CallSite.Method learn = new CallSite.Method("com/cameraunlock/zomboid/GameFixTests", "learn", "(Ljava/lang/Class;Ljava/lang/Class;)V");
        Class<?> defined = MethodHandles.lookup().defineClass(CallSite.answerOnce(subject, within, known, learn));
        Method kind = defined.getMethod("kind", Class.class);
        check(kind.invoke(null, Integer.class) == Number.class && worked == 1, "asked about a class the first time, the method works its answer out");
        check(learnt.get(Integer.class) == Number.class && learnt.size() == 1, "and it is kept by what was asked, not by what the method made of it on the way");
        check(kind.invoke(null, Integer.class) == Number.class && worked == 1, "asked again, it hands back what was kept and works nothing out");
        check(kind.invoke(null, Object.class) == null && kind.invoke(null, Object.class) == null && worked == 3, "an answer of nothing is worked out each time");
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
        List<CallSite.Method> changers = List.of(new CallSite.Method("com/cameraunlock/zomboid/SubjectAsking", "put", "(Ljava/lang/Object;)V"));
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

    private static final CallSite.Method CONTAINS = new CallSite.Method("zombie/util/list/PZArrayUtil", "contains", "(Ljava/util/List;Ljava/util/function/Predicate;)Z");

    /**
     * A class with a list and a method that asks PZArrayUtil.contains of it, as the game's
     * AnimationPlayer.isBoneReparented does. {@code alsoAsks} has the method ask something else
     * too, as a later build of the game's might.
     */
    private static byte[] holdingAList(String name, boolean alsoAsks) {
        ClassDesc owner = ClassDesc.of("com.cameraunlock.zomboid." + name), list = ClassDesc.of("java.util.ArrayList");
        return ClassFile.of().build(owner, type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withField("held", list, ClassFile.ACC_PUBLIC)
                .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code -> code
                        .aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                        .aload(0).new_(list).dup().invokespecial(list, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void).putfield(owner, "held", list)
                        .return_())
                .withMethodBody("has", MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_int), ClassFile.ACC_PUBLIC, code -> {
                    code.aload(0).getfield(owner, "held", list)
                            .iload(1).invokestatic(ConstantDescs.CD_Integer, "valueOf", MethodTypeDesc.of(ConstantDescs.CD_Integer, ConstantDescs.CD_int)).pop()
                            .aconst_null()
                            .invokestatic(ClassDesc.ofInternalName(CONTAINS.owner()), CONTAINS.name(), MethodTypeDesc.ofDescriptor(CONTAINS.descriptor()));
                    if (alsoAsks) {
                        code.aload(0).invokevirtual(ConstantDescs.CD_Object, "hashCode", MethodTypeDesc.of(ConstantDescs.CD_int)).pop();
                    }
                    code.ireturn();
                }));
    }

    private static void falseIsAnsweredAtOnceWhileTheListIsEmpty() throws Throwable {
        byte[] subject = holdingAList("SubjectHoldingAList", false);
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/SubjectHoldingAList", "has", "(I)Z");
        byte[] patched = CallSite.falseAtOnceWhileEmpty(subject, within, "held", CONTAINS);
        check(ClassFile.of().verify(patched).isEmpty(), "the patched class verifies");
        Class<?> defined = MethodHandles.lookup().defineClass(patched);
        Object made = defined.getConstructor().newInstance();
        Method has = defined.getMethod("has", int.class);
        check(Boolean.FALSE.equals(has.invoke(made, 3)), "with nothing in the list the answer is no");
        @SuppressWarnings("unchecked")
        ArrayList<Object> held = (ArrayList<Object>) defined.getField("held").get(made);
        held.add("a bone");
        check(Boolean.TRUE.equals(has.invoke(made, 3)), "with something in it the method does what it did");
        check(refusesFalseAtOnce(subject, within, "missing"), "a class without the list is rejected");
        CallSite.Method asksMore = new CallSite.Method("com/cameraunlock/zomboid/SubjectAskingMore", "has", "(I)Z");
        check(refusesFalseAtOnce(holdingAList("SubjectAskingMore", true), asksMore, "held"), "a method that asks something besides the list is rejected");
    }

    private static boolean refusesFalseAtOnce(byte[] subject, CallSite.Method within, String field) {
        try {
            CallSite.falseAtOnceWhileEmpty(subject, within, field, CONTAINS);
            return false;
        } catch (IllegalStateException expected) {
            return true;
        }
    }
}
