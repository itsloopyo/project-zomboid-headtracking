package com.cameraunlock.zomboid;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import zombie.scripting.ScriptManager;
import zombie.scripting.objects.Item;

final class GameFixTests {
    private GameFixTests() {}

    static void run() {
        Checks.run("aKindIsKeptOnceItIsLearnt", GameFixTests::aKindIsKeptOnceItIsLearnt);
        Checks.run("anAnswerIsWorkedOutOnceForEachThingAsked", GameFixTests::anAnswerIsWorkedOutOnceForEachThingAsked);
        Checks.run("falseIsAnsweredAtOnceWhileTheListIsEmpty", GameFixTests::falseIsAnsweredAtOnceWhileTheListIsEmpty);
        Checks.run("anAnswerIsKeptUntilWhatItCameFromChanges", GameFixTests::anAnswerIsKeptUntilWhatItCameFromChanges);
        Checks.run("anItemIsHandedBackUntilTheScriptsChange", GameFixTests::anItemIsHandedBackUntilTheScriptsChange);
        Checks.run("whatBeginsWithAMethodEndsWithIt", GameFixTests::whatBeginsWithAMethodEndsWithIt);
        Checks.run("aPatchThatNoLongerFitsIsAGap", GameFixTests::aPatchThatNoLongerFitsIsAGap);
        Checks.run("aLocalIsFoundByWhatWasStoredInItWhereverItSits", GameFixTests::aLocalIsFoundByWhatWasStoredInItWhereverItSits);
        Checks.run("eachChangeToWhatANameFindsTellsGameFixes", GameFixTests::eachChangeToWhatANameFindsTellsGameFixes);
        Checks.run("theGuardStopsTheKeepingForCodeThatWasNotChecked", GameFixTests::theGuardStopsTheKeepingForCodeThatWasNotChecked);
        Checks.run("aDigestIsOfTheCodeNotOfHowItIsLaidOut", GameFixTests::aDigestIsOfTheCodeNotOfHowItIsLaidOut);
        // Last, since a stop is for good: nothing after it could keep an item.
        Checks.run("aLostPatchStopsTheKeepingForGood", GameFixTests::aLostPatchStopsTheKeepingForGood);
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

    private static void anItemIsHandedBackUntilTheScriptsChange() {
        ScriptManager scripts = new ScriptManager();
        Item axe = new Item(), saw = new Item();
        scripts.items.put("Base.Axe", axe);
        GameFixes.keepItemsIf("the game's code is missing a patch");
        check(GameFixes.findItem(scripts, "Base.Axe", true) == axe && GameFixes.findItem(scripts, "Base.Axe", true) == axe && scripts.lookups == 2,
                "with a patch the keeping needs missing, every name is looked up as the game does");
        GameFixes.keepItemsIf(null);
        check(GameFixes.findItem(scripts, "Base.Axe", true) == axe && scripts.lookups == 3, "with all of them in, a name is looked up the first time it is asked for");
        check(GameFixes.findItem(scripts, "Base.Axe", true) == axe && scripts.lookups == 3, "and what it found is handed back the next time");
        check(GameFixes.findItem(scripts, "Base.Saw", true) == null && GameFixes.findItem(scripts, "Base.Saw", true) == null && scripts.lookups == 5,
                "a name that finds nothing is looked up each time");
        check(GameFixes.findItem(scripts, null, true) == null && GameFixes.findItem(scripts, null, true) == null && scripts.lookups == 7,
                "and no name is the game's to look up, each time");
        GameFixes.scriptsChanging();
        scripts.items.put("Base.Axe", saw);
        check(GameFixes.findItem(scripts, "Base.Axe", true) == saw && GameFixes.findItem(scripts, "Base.Axe", true) == saw && scripts.lookups == 9,
                "while the scripts change, every name is looked up");
        GameFixes.scriptsChanging();
        GameFixes.scriptsChanged();
        check(GameFixes.findItem(scripts, "Base.Axe", true) == saw && scripts.lookups == 10, "and still once a change inside that change has ended");
        GameFixes.scriptsChanged();
        check(GameFixes.findItem(scripts, "Base.Axe", true) == saw && scripts.lookups == 11, "once the change has ended, a name is looked up again");
        check(GameFixes.findItem(scripts, "Base.Axe", true) == saw && scripts.lookups == 11, "and what it finds then is kept");
        scripts.items.put("Base.Saw", saw);
        scripts.duringLookup = () -> {
            GameFixes.scriptsChanging();
            GameFixes.scriptsChanged();
        };
        check(GameFixes.findItem(scripts, "Base.Saw", true) == saw && scripts.lookups == 12, "a name looked up while a change begins and ends");
        scripts.duringLookup = null;
        check(GameFixes.findItem(scripts, "Base.Saw", true) == saw && scripts.lookups == 13, "is not kept, as what it found may be from before the change");
        check(GameFixes.findItem(scripts, "Base.Saw", true) == saw && scripts.lookups == 13, "the lookup after it is");
        check(GameFixes.findItem(scripts, "Base.Axe", true) == saw && scripts.lookups == 14, "and a name kept before that change is looked up again");
        ScriptManager other = new ScriptManager();
        Item hatchet = new Item();
        other.items.put("Base.Axe", hatchet);
        check(GameFixes.findItem(other, "Base.Axe", true) == hatchet && other.lookups == 1, "a name kept from one ScriptManager is looked up afresh in another");
        check(GameFixes.findItem(scripts, "Base.Axe", true) == saw && scripts.lookups == 15, "and in the first again once the other has found it");
    }

    private static int begun, ended, begunAsTheMethodRan, endedAsTheMethodRan, caughtInside, endedAsItCaught, turnsTaken;

    public static void begins() {
        begun++;
    }

    public static void ends() {
        ended++;
    }

    public static void runs() {
        begunAsTheMethodRan = begun;
        endedAsTheMethodRan = ended;
    }

    public static void caught() {
        caughtInside++;
        endedAsItCaught = ended;
    }

    public static void turns() {
        turnsTaken++;
    }

    private static Throwable thrownOutOf(Method method, int argument) throws IllegalAccessException {
        try {
            method.invoke(null, argument);
            return null;
        } catch (InvocationTargetException invoked) {
            return invoked.getCause();
        }
    }

    private static void whatBeginsWithAMethodEndsWithIt() throws Throwable {
        ClassDesc thrown = ClassDesc.of("java.lang.IllegalStateException"), handled = ClassDesc.of("java.lang.IllegalArgumentException"),
                tests = ClassDesc.of("com.cameraunlock.zomboid.GameFixTests");
        // change(0) returns, change(1) throws out of the method, and change(2) throws inside a try the method catches itself.
        // turn(n) on an instance goes round a loop n times, and the loop and a try in it both start at the method's first
        // instruction; the second time round it throws inside the try, which catches it.
        byte[] subject = ClassFile.of().build(ClassDesc.of("com.cameraunlock.zomboid.SubjectChangingScripts"), type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code -> code
                        .aload(0)
                        .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                        .return_())
                .withMethodBody("turn", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int), ClassFile.ACC_PUBLIC, code -> {
                    Label top = code.newBoundLabel();
                    code.trying(inside -> inside
                                            .invokestatic(tests, "turns", ConstantDescs.MTD_void)
                                            .iload(1)
                                            .iconst_2()
                                            .ifThen(Opcode.IF_ICMPEQ, throwsInside -> throwsInside.new_(handled).dup().invokespecial(handled, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void).athrow()),
                                    catches -> catches.catching(handled, handler -> handler.pop().invokestatic(tests, "caught", ConstantDescs.MTD_void)))
                            .iinc(1, -1)
                            .iload(1)
                            .ifgt(top)
                            .return_();
                })
                .withMethodBody("change", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> code
                        .invokestatic(tests, "runs", ConstantDescs.MTD_void)
                        .trying(inside -> inside
                                        .iload(0)
                                        .iconst_2()
                                        .ifThen(Opcode.IF_ICMPEQ, throwsInside -> throwsInside.new_(handled).dup().invokespecial(handled, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void).athrow()),
                                catches -> catches.catching(handled, handler -> handler.pop().invokestatic(tests, "caught", ConstantDescs.MTD_void)))
                        .iload(0)
                        .iconst_1()
                        .ifThen(Opcode.IF_ICMPEQ, fails -> fails.new_(thrown).dup().invokespecial(thrown, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void).athrow())
                        .return_()));
        CallSite.Method begins = new CallSite.Method("com/cameraunlock/zomboid/GameFixTests", "begins", "()V"),
                ends = new CallSite.Method("com/cameraunlock/zomboid/GameFixTests", "ends", "()V");
        byte[] patched = CallSite.insertAround(subject, new CallSite.Method("com/cameraunlock/zomboid/SubjectChangingScripts", "change", "(I)V"), begins, ends);
        patched = CallSite.insertAround(patched, new CallSite.Method("com/cameraunlock/zomboid/SubjectChangingScripts", "turn", "(I)V"), begins, ends);
        check(ClassFile.of().verify(patched).isEmpty(), "the patched class verifies");
        Class<?> defined = MethodHandles.lookup().defineClass(patched);
        Method change = defined.getMethod("change", int.class);
        Throwable out = thrownOutOf(change, 0);
        check(out == null && begun == 1 && begunAsTheMethodRan == 1 && endedAsTheMethodRan == 0 && ended == 1,
                "what begins before the method's own code ends after it, as it returns, got " + out);
        out = thrownOutOf(change, 1);
        check(begun == 2 && ended == 2, "and ends as it throws");
        check(out instanceof IllegalStateException, "and what the method threw is thrown on, got " + out);
        out = thrownOutOf(change, 2);
        check(out == null && caughtInside == 1 && endedAsItCaught == 2, "what the method catches itself is still caught there, before the change ends, got " + out);
        check(begun == 3 && ended == 3, "and the change then ends once, as the method returns");
        defined.getMethod("turn", int.class).invoke(defined.getConstructor().newInstance(), 3);
        check(turnsTaken == 3 && caughtInside == 2 && endedAsItCaught == 3, "a loop and a try that start at a method's first instruction run as they did, got " + turnsTaken + " turns");
        check(begun == 4 && ended == 4, "and the change begins once as the method is called, not each time round the loop, and ends once");
    }

    private static final String TARGET = "the target";
    private static final CallSite.Method MADE = new CallSite.Method("com/cameraunlock/zomboid/GameFixTests", "made", "()Ljava/lang/Object;"),
            FOUND = new CallSite.Method("com/cameraunlock/zomboid/GameFixTests", "found", "()Z"),
            KEPT = new CallSite.Method("com/cameraunlock/zomboid/GameFixTests", "kept", "(ZLjava/lang/Object;)Z");

    static Object made() {
        return TARGET;
    }

    static boolean found() {
        return false;
    }

    static boolean kept(boolean found, Object target) {
        return found || target == TARGET;
    }

    /**
     * A method that keeps {@code before} ints in its first locals, stores what made() returns in
     * the next and an int in the one after, the way calculateHitInfoList keeps its target and loop
     * counter, then asks found(). {@code overwritten} stores null over the target before the call.
     */
    private static byte[] storingATarget(String name, int before, boolean overwritten) {
        ClassDesc owner = ClassDesc.of("com.cameraunlock.zomboid." + name);
        return ClassFile.of().build(owner, type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withMethodBody("run", MethodTypeDesc.of(ConstantDescs.CD_boolean), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                    for (int i = 0; i < before; i++) {
                        code.iconst_0().istore(i);
                    }
                    CallSite.invokeStatic(code, MADE);
                    code.astore(before).iconst_0().istore(before + 1);
                    if (overwritten) {
                        code.aconst_null().astore(before);
                    }
                    CallSite.invokeStatic(code, FOUND);
                    code.ireturn();
                }));
    }

    private static void aLocalIsFoundByWhatWasStoredInItWhereverItSits() throws Throwable {
        for (int before : new int[] {0, 3}) {
            String name = "SubjectStoringATarget" + before;
            CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/" + name, "run", "()Z");
            byte[] patched = CallSite.insertAfterEach(storingATarget(name, before, false), within, FOUND, List.of(CallSite.Stored.returnOf(MADE)),
                    (code, slots) -> {
                        code.aload(slots[0]);
                        CallSite.invokeStatic(code, KEPT);
                    }, 1);
            check(ClassFile.of().verify(patched).isEmpty(), "with the target in local " + before + " the patched class verifies");
            Class<?> defined = MethodHandles.lookup().defineClass(patched);
            check(Boolean.TRUE.equals(defined.getMethod("run").invoke(null)), "and the hook is handed the target from local " + before);
        }
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/SubjectOverwritingATarget", "run", "()Z");
        String thrown = null;
        try {
            CallSite.insertAfterEach(storingATarget("SubjectOverwritingATarget", 1, true), within, FOUND, List.of(CallSite.Stored.returnOf(MADE)),
                    (code, slots) -> code.aload(slots[0]), 1);
        } catch (IllegalStateException e) {
            thrown = e.getMessage();
        }
        check(thrown != null && thrown.contains("no local holding"), "a local stored over before the call is not handed on, got " + thrown);
    }

    private static void aPatchThatNoLongerFitsIsAGap() {
        String owner = "com/cameraunlock/zomboid/GameFixTests";
        Agent.PatchTransformer transformer = new Agent.PatchTransformer(List.of(new Agent.Patch(owner, "fitting two bytes", classBytes -> {
            if (classBytes.length != 2) {
                throw new IllegalStateException("does not fit " + classBytes.length + " bytes");
            }
            return classBytes;
        })));
        transformer.transform(null, owner, null, null, new byte[2]);
        check(transformer.gap() == null, "a patch that went in as its class loaded is no gap");
        transformer.transform(null, owner, GameFixTests.class, null, new byte[3]);
        String gap = transformer.gap();
        check(gap != null && gap.contains("fitting two bytes"), "but it is once it does not fit the class's code as the class is transformed again, got " + gap);
    }

    /**
     * Each patch of Agent.itemLookupPatches put into a method shaped as the game's, which returns
     * at once when its object says so and otherwise makes the call given. FindItem(String) and the
     * ten methods that change what a name finds on the build ItemLookupGuard was checked against
     * are every patch there is, so one dropped from the list, or one that no longer tells
     * GameFixes on a way out, fails here. The methods are read, not run.
     */
    private static void eachChangeToWhatANameFindsTellsGameFixes() {
        List<Agent.Patch> patches = Agent.itemLookupPatches();
        String scripts = "zombie/scripting/ScriptManager", module = "zombie/scripting/objects/ScriptModule", lua = "zombie/Lua/LuaManager$GlobalObject";
        String item = "Lzombie/scripting/objects/Item;", mode = "Lzombie/scripting/ScriptLoadMode;", text = "Ljava/lang/String;";
        String[][] changes = {
                {scripts, "Reset", "()V"}, {scripts, "Load", "()V"}, {scripts, "LoadFile", "(" + mode + text + "Z)V"}, {scripts, "ParseScript", "(" + mode + text + ")V"},
                {scripts, "ReloadScripts", "(Lzombie/scripting/ScriptType;)V"}, {scripts, "ReloadScripts", "(Ljava/util/EnumSet;)V"},
                {module, "ParseScriptPP", "(" + mode + text + ")V"}, {module, "Reset", "()V"},
                {lua, "createNewScriptItem", "(" + text.repeat(5) + ")" + item}, {lua, "cloneItemType", "(" + text + text + ")" + item}};
        List<String> told = List.of("GameFixes.scriptsChanging", "GameFixes.scriptsChanged", "return", "GameFixes.scriptsChanged", "return", "GameFixes.scriptsChanged", "throw");
        for (String[] change : changes) {
            byte[] patched = patchOf(patches, change[0], change[1] + change[2] + " ").rewrite().apply(shapedAsTheGames(change[1], change[2], null));
            List<String> steps = steps(patched, change[1]);
            check(steps.equals(told), change[0] + "." + change[1] + change[2] + " patched reads " + steps + ", where it should read " + told);
        }
        byte[] find = patchOf(patches, scripts, "FindItem(String) ").rewrite()
                .apply(shapedAsTheGames("FindItem", "(" + text + ")" + item, new CallSite.Method(scripts, "FindItem", "(" + text + "Z)" + item)));
        List<String> steps = steps(find, "FindItem");
        check(steps.equals(List.of("return", "GameFixes.findItem", "return")), "FindItem(String) patched reads " + steps + ", where its one lookup should be GameFixes'");
        check(patches.size() == changes.length + 1, "there is no patch besides FindItem(String)'s and the " + changes.length + " changes', got " + patches.size() + " patches");
    }

    private static Agent.Patch patchOf(List<Agent.Patch> patches, String owner, String what) {
        List<Agent.Patch> found = patches.stream().filter(patch -> patch.owner().equals(owner) && patch.what().startsWith(what)).toList();
        if (found.size() != 1) {
            throw new IllegalStateException(found.size() + " patches of " + owner + "." + what.strip() + ", where there should be one");
        }
        return found.get(0);
    }

    /**
     * A class with a method of the name and descriptor given, which returns at once when its
     * field says so and otherwise makes the call given, if there is one, on no object, and
     * returns. Whatever it returns or hands the call is null or naught.
     */
    private static byte[] shapedAsTheGames(String method, String descriptor, CallSite.Method call) {
        ClassDesc owner = ClassDesc.of("com.cameraunlock.zomboid.ShapedAsTheGames");
        MethodTypeDesc type = MethodTypeDesc.ofDescriptor(descriptor);
        return ClassFile.of().build(owner, shape -> shape
                .withFlags(ClassFile.ACC_PUBLIC)
                .withField("early", ConstantDescs.CD_boolean, ClassFile.ACC_PUBLIC)
                .withMethodBody(method, type, ClassFile.ACC_PUBLIC, code -> {
                    code.aload(0).getfield(owner, "early", ConstantDescs.CD_boolean).ifThen(Opcode.IFNE, early -> answer(early, type));
                    if (call != null) {
                        MethodTypeDesc called = MethodTypeDesc.ofDescriptor(call.descriptor());
                        code.aconst_null();
                        for (ClassDesc parameter : called.parameterList()) {
                            if (parameter.isPrimitive()) {
                                code.iconst_0();
                            } else {
                                code.aconst_null();
                            }
                        }
                        code.invokevirtual(ClassDesc.ofInternalName(call.owner()), call.name(), called).pop();
                    }
                    answer(code, type);
                }));
    }

    private static void answer(CodeBuilder code, MethodTypeDesc type) {
        if (type.returnType().isPrimitive()) {
            code.return_();
        } else {
            code.aconst_null().areturn();
        }
    }

    /** What a method does, in order: each call by its name, a call to GameFixes as "GameFixes." and its name, each return and each throw. */
    private static List<String> steps(byte[] classBytes, String method) {
        List<String> steps = new ArrayList<>();
        for (MethodModel model : ClassFile.of().parse(classBytes).methods()) {
            if (!model.methodName().equalsString(method)) {
                continue;
            }
            for (CodeElement element : model.code().orElseThrow()) {
                if (element instanceof InvokeInstruction invoke) {
                    steps.add((invoke.owner().asInternalName().equals("com/cameraunlock/zomboid/GameFixes") ? "GameFixes." : "") + invoke.name().stringValue());
                } else if (element instanceof ReturnInstruction) {
                    steps.add("return");
                } else if (element instanceof ThrowInstruction) {
                    steps.add("throw");
                }
            }
        }
        return steps;
    }

    private static ClassDesc type(String internalName) {
        return ClassDesc.ofInternalName(internalName);
    }

    /** A class of the name given, extending the class given, with one static method of the code given. */
    private static byte[] withMethod(String name, String parent, String method, String descriptor, Consumer<CodeBuilder> code) {
        return ClassFile.of().build(type(name), shape -> shape
                .withFlags(ClassFile.ACC_PUBLIC)
                .withSuperclass(type(parent))
                .withMethodBody(method, MethodTypeDesc.ofDescriptor(descriptor), ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code));
    }

    /** A mod's class whose one method runs the code given and returns. */
    private static byte[] touching(Consumer<CodeBuilder> code) {
        return withMethod("com/mod/Toucher", "java/lang/Object", "touch", "()V", touch -> {
            code.accept(touch);
            touch.return_();
        });
    }

    private static void stopsFor(byte[] classBytes, String why, String what) {
        String said = new ItemLookupGuard((reason, cause) -> {}, reason -> {}).check(classBytes);
        check(said != null && said.contains(why), what + ", got " + said);
    }

    private static void letBe(byte[] classBytes, String what) {
        String said = new ItemLookupGuard((reason, cause) -> {}, reason -> {}).check(classBytes);
        check(said == null, what + ", got " + said);
    }

    private static void theGuardStopsTheKeepingForCodeThatWasNotChecked() {
        String bucket = ItemLookupGuard.BUCKET, module = ItemLookupGuard.MODULE, manager = ItemLookupGuard.MANAGER;
        String uses = "com/mod/Toucher.touch()V uses what ScriptManager.FindItem reads";
        ClassDesc map = ClassDesc.of("java.util.Map"), hashMap = ClassDesc.of("java.util.HashMap");
        letBe(touching(code -> code.aconst_null().pop()), "a class that names nothing of the scripts is let be");
        letBe(touching(code -> code.aconst_null().getfield(type(module), "items", type(bucket)).pop()), "as is one that reads which bucket holds a module's items");
        stopsFor(touching(code -> code.aconst_null().aconst_null().putfield(type(module), "items", type(bucket))), uses, "one that puts another bucket there stops the keeping");
        stopsFor(touching(code -> code.aconst_null().getfield(type(bucket), "scriptMap", map).pop()), uses, "as does one that reads a bucket's map, which it could change");
        stopsFor(touching(code -> code.aconst_null().getfield(type("zombie/scripting/objects/ScriptModule$3"), "scriptMap", map).pop()), uses,
                "or reads it through a bucket of the game's that comes from one, before that bucket's class is defined");
        stopsFor(touching(code -> code.aconst_null().invokevirtual(type(ItemLookupGuard.COLLECTION), "getFullTypeToScriptMap", MethodTypeDesc.of(hashMap)).pop()), uses,
                "or asks for the map of full types");
        stopsFor(touching(code -> code.ldc(MethodHandleDesc.ofField(DirectMethodHandleDesc.Kind.GETTER, type(manager), "moduleMap", hashMap)).pop()), uses,
                "or holds a handle to the map of modules");
        DirectMethodHandleDesc metafactory = MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, ClassDesc.of("java.lang.invoke.LambdaMetafactory"), "metafactory",
                MethodTypeDesc.of(ConstantDescs.CD_CallSite, ConstantDescs.CD_MethodHandles_Lookup, ConstantDescs.CD_String, ConstantDescs.CD_MethodType,
                        ConstantDescs.CD_MethodType, ConstantDescs.CD_MethodHandle, ConstantDescs.CD_MethodType));
        DynamicCallSiteDesc lambda = DynamicCallSiteDesc.of(metafactory, "apply", MethodTypeDesc.of(ClassDesc.of("java.util.function.Function")),
                MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object),
                MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.VIRTUAL, type(bucket), "getScriptMap", MethodTypeDesc.of(map)), MethodTypeDesc.of(map, type(bucket)));
        stopsFor(touching(code -> code.invokedynamic(lambda).pop()), uses, "or makes a lambda of a bucket's map");
        stopsFor(withMethod("com/mod/OwnBucket", bucket, "make", "()V", CodeBuilder::return_),
                "com/mod/OwnBucket extends zombie/scripting/ScriptBucket and was not checked", "a bucket the game does not have stops it");
        stopsFor(withMethod(manager, "java/lang/Object", "FindItem", "(Ljava/lang/String;)Lzombie/scripting/objects/Item;", code -> code.aconst_null().areturn()),
                "zombie/scripting/ScriptManager.FindItem(Ljava/lang/String;)Lzombie/scripting/objects/Item; is not the code that was checked", "as does a lookup that is not the game's");
        stopsFor(withMethod("zombie/scripting/objects/ScriptModule$3", bucket, "get", "(Ljava/lang/String;)Lzombie/scripting/objects/BaseScriptObject;", code -> code.aconst_null().areturn()),
                "takes the place of", "or the item bucket answering a name its own way");
        stopsFor(withMethod("zombie/scripting/objects/ScriptModule$1", "zombie/scripting/ScriptBucket$Template", "get", "(Ljava/lang/String;)Lzombie/scripting/objects/BaseScriptObject;",
                        code -> code.aconst_null().areturn()),
                "takes the place of", "or a bucket answering it in place of ScriptBucket's own, read before the class between them is");
        String mode = "zombie/scripting/ScriptLoadMode";
        MethodTypeDesc parse = MethodTypeDesc.of(ConstantDescs.CD_void, type(mode), ConstantDescs.CD_String);
        stopsFor(withMethod(manager, "java/lang/Object", "addScript", "(Ljava/lang/String;)V", code -> code
                        .aconst_null().aconst_null().aload(0).invokevirtual(type(manager), "CreateFromToken", parse).return_()),
                "zombie/scripting/ScriptManager.addScript(Ljava/lang/String;)V uses what", "a method of the game's that adds a module without telling stops it");
        stopsFor(withMethod(manager, "java/lang/Object", "reloadItems", "()V", code -> code
                        .aconst_null().aconst_null().aconst_null().invokevirtual(type(manager), "loadScripts", MethodTypeDesc.of(ConstantDescs.CD_void, type(mode), ClassDesc.of("java.util.EnumSet"))).return_()),
                "zombie/scripting/ScriptManager.reloadItems()V uses what", "as does one that loads the scripts without telling");
        stopsFor(touching(code -> code.aconst_null().aconst_null().aconst_null().invokevirtual(type(module), "CreateFromTokenPP", parse)), uses,
                "or one that reads a module's scripts into it without telling");

        List<String> stops = new ArrayList<>();
        ItemLookupGuard guard = new ItemLookupGuard((why, cause) -> stops.add(why + (cause == null ? "" : ", " + cause.getClass().getSimpleName())), stops::add);
        check(guard.transform(null, "com/mod/Toucher", null, null, touching(code -> code.aconst_null().pop())) == null && stops.isEmpty(),
                "the guard changes no class, and is quiet about one that uses nothing of the scripts");
        guard.transform(null, "com/mod/Broken", null, null, "a file that names zombie/scripting/ and is no class".getBytes(StandardCharsets.ISO_8859_1));
        check(stops.size() == 1 && stops.get(0).startsWith("com/mod/Broken could not be read") && stops.get(0).endsWith("Exception"),
                "a class it cannot read stops the keeping, with what was thrown, got " + stops);
        guard.transform(null, "com/mod/Toucher", null, null, touching(code -> code.aconst_null().getfield(type(bucket), "scriptMap", map).pop()));
        check(stops.size() == 1, "and it says so once: nothing after is read, got " + stops);

        List<String> told = toldAfterStopsThat(new IllegalStateException("the stop could not be told"), 2, 2);
        check(told.size() == 2 && told.get(1).equals("com/mod/Toucher.touch()V uses what ScriptManager.FindItem reads and was not checked"),
                "a stop that throws is told again, as the guard reads on until a stop has been told, got " + told);
        told = toldAfterStopsThat(new AssertionError("the stop could not be told"), 1, 1);
        check(told.equals(List.of("com/mod/Toucher could not be read for what it does to the scripts, AssertionError")),
                "and so is one that throws an Error, which the guard catches as the JVM would drop it, got " + told);
        letBe(withMethod("zombie/scripting/ScriptBucket$Template", bucket, "<init>", "(Lzombie/scripting/objects/ScriptModule;Lzombie/scripting/ScriptType;)V", CodeBuilder::return_),
                "a constructor of a subclass is not taken to replace the one of its parent it calls");

        List<String> early = new ArrayList<>();
        new ItemLookupGuard((why, cause) -> early.add(why), why -> early.add("late: " + why)).loadedBefore(new Class<?>[] {String.class, Item.class, GameFixTests.class, int[].class});
        check(early.isEmpty(), "classes loaded before the mod started that use nothing of the scripts are read from their files and let be, got " + early);
        new ItemLookupGuard((why, cause) -> early.add(why), why -> early.add("late: " + why)).loadedBefore(new Class<?>[] {Item.class, ScriptManager.class});
        check(early.size() == 1 && early.get(0).startsWith("late: zombie/scripting/ScriptManager was loaded before the mod started"),
                "but one whose code a lookup or a change rests on stops the keeping, as a late start, got " + early);
    }

    /**
     * What a guard whose first {@code throwing} stops throw the failure given tells as it is
     * handed {@code classes} classes that each stop the keeping, with anything it lets out of
     * transform.
     */
    private static List<String> toldAfterStopsThat(Throwable failure, int throwing, int classes) {
        List<String> told = new ArrayList<>();
        int[] thrown = {0};
        ItemLookupGuard unsure = new ItemLookupGuard((why, cause) -> {
            if (thrown[0]++ < throwing) {
                if (failure instanceof RuntimeException unchecked) {
                    throw unchecked;
                }
                throw (Error) failure;
            }
            told.add(why + (cause == null ? "" : ", " + cause.getClass().getSimpleName()));
        }, told::add);
        for (int i = 0; i < classes; i++) {
            try {
                unsure.transform(null, "com/mod/Toucher", null, null, touching(code -> code.aconst_null().getfield(type(ItemLookupGuard.BUCKET), "scriptMap", ClassDesc.of("java.util.Map")).pop()));
            } catch (Throwable out) {
                told.add("out of transform: " + out.getClass().getSimpleName());
            }
        }
        return told;
    }

    /**
     * The same code laid out otherwise, with line numbers and its constants at other places in the
     * class's constant pool, has the same digest, and other code has another. With 300 constants
     * ahead of its own, the code loads its constant with ldc_w where it had ldc.
     */
    private static void aDigestIsOfTheCodeNotOfHowItIsLaidOut() {
        long plain = digestOf("com/mod/Plain", false, 0, "Base.Axe"), laidOut = digestOf("com/mod/LaidOut", true, 0, "Base.Axe"),
                farDown = digestOf("com/mod/FarDown", true, 300, "Base.Axe"), other = digestOf("com/mod/Plain", false, 0, "Base.Saw");
        check(plain == laidOut, "the same code has the same digest however it is laid out, got " + plain + " and " + laidOut);
        check(plain == farDown, "and with its constant far down the constant pool, got " + plain + " and " + farDown);
        check(plain != other, "and other code has another");
    }

    private static long digestOf(String name, boolean laidOut, int constantsAhead, String constant) {
        byte[] classBytes = ClassFile.of().build(type(name), shape -> {
            for (int i = 0; i < constantsAhead; i++) {
                shape.constantPool().stringEntry("ahead " + i);
            }
            if (laidOut) {
                shape.withMethodBody("first", MethodTypeDesc.of(ConstantDescs.CD_long), ClassFile.ACC_STATIC, code -> code.lineNumber(3).ldc(7L).lreturn());
            }
            shape.withMethodBody("named", MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_String), ClassFile.ACC_STATIC, code -> {
                if (laidOut) {
                    code.lineNumber(10);
                }
                Label other = code.newLabel();
                code.aload(0).ldc(constant).invokevirtual(ConstantDescs.CD_String, "equals", MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_Object)).ifeq(other);
                if (laidOut) {
                    code.lineNumber(11);
                }
                code.iconst_1().ireturn().labelBinding(other).iconst_0().ireturn();
            });
        });
        MethodModel named = ClassFile.of().parse(classBytes).methods().stream().filter(method -> method.methodName().equalsString("named")).findFirst().orElseThrow();
        boolean wide = named.code().orElseThrow().elementStream().anyMatch(element -> element instanceof Instruction instruction && instruction.opcode() == Opcode.LDC_W);
        if (wide != constantsAhead > 255) {
            throw new IllegalStateException(name + " loads its constant with " + (wide ? "ldc_w" : "ldc") + " with " + constantsAhead + " constants ahead of it");
        }
        return ItemLookupGuard.digest(named);
    }

    private static void aLostPatchStopsTheKeepingForGood() {
        String owner = "com/cameraunlock/zomboid/GameFixTests";
        List<String> lost = new ArrayList<>();
        Agent.PatchTransformer transformer = new Agent.PatchTransformer(List.of(new Agent.Patch(owner, "fitting two bytes", classBytes -> {
            if (classBytes.length != 2) {
                throw new IllegalStateException("does not fit " + classBytes.length + " bytes");
            }
            return classBytes;
        })), lost::add);
        transformer.transform(null, owner, null, null, new byte[2]);
        check(lost.isEmpty(), "a patch that goes in is not lost");
        transformer.transform(null, owner, GameFixTests.class, null, new byte[3]);
        check(lost.equals(List.of(owner + ".fitting two bytes")), "one that fails as its class is transformed again is told of before transform returns, got " + lost);

        // What Agent adds, through an Instrumentation that writes down what it is asked.
        List<String> asked = new ArrayList<>();
        List<ClassFileTransformer> added = new ArrayList<>();
        Instrumentation instrumentation = (Instrumentation) Proxy.newProxyInstance(GameFixTests.class.getClassLoader(), new Class<?>[] {Instrumentation.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "addTransformer" -> {
                        added.add((ClassFileTransformer) arguments[0]);
                        asked.add("add " + arguments[0].getClass().getSimpleName() + (arguments.length == 2 && (Boolean) arguments[1] ? ", able to retransform" : ""));
                        yield null;
                    }
                    case "getAllLoadedClasses" -> {
                        asked.add("the loaded classes");
                        yield new Class<?>[] {Item.class};
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Agent.addItemLookupTransformers(instrumentation);
        List<String> wanted = List.of("add ItemLookupGuard, able to retransform", "add PatchTransformer, able to retransform", "the loaded classes");
        check(asked.equals(wanted), "Agent adds the guard first, then the patches of the lookups, then has the guard read what was loaded before, got " + asked);
        String cost = Agent.itemLookupGuardCost();
        check(cost.startsWith("ItemLookupGuard has read 1 of the 0 classes"), "and the guard has read the one class loaded before it, got " + cost);

        ScriptManager scripts = new ScriptManager();
        Item axe = new Item();
        scripts.items.put("Base.Axe", axe);
        GameFixes.keepItemsIf(null);
        check(GameFixes.findItem(scripts, "Base.Axe", true) == axe && GameFixes.findItem(scripts, "Base.Axe", true) == axe && scripts.lookups == 1,
                "while items are kept, a name is looked up once");
        ((Agent.PatchTransformer) added.get(1)).transform(null, ItemLookupGuard.MANAGER, ScriptManager.class, null, new byte[3]);
        check(GameFixes.findItem(scripts, "Base.Axe", true) == axe && scripts.lookups == 2,
                "once a patch of the lookups Agent added fails as its class is transformed again, every name is looked up as the game does");
        GameFixes.keepItemsIf(null);
        check(GameFixes.findItem(scripts, "Base.Axe", true) == axe && GameFixes.findItem(scripts, "Base.Axe", true) == axe && scripts.lookups == 4,
                "for good: nothing turns it on again");
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
