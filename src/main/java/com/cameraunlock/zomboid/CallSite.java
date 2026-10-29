package com.cameraunlock.zomboid;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.List;
import java.util.function.Consumer;

final class CallSite {
    /** A method named by internal class name, method name and descriptor. */
    record Method(String owner, String name, String descriptor) {}

    private CallSite() {}

    /**
     * Inserts a call to the static method {@code hook} straight after the one
     * call to {@code call} inside {@code within}. The hook either takes and
     * returns nothing, or takes the value the call returned and returns its
     * replacement. Any other number of matches is a game build this was not
     * written for, and throws rather than guessing which call was meant.
     */
    static byte[] insertAfter(byte[] classBytes, Method within, Method call, Method hook) {
        return insertAfterEach(classBytes, within, call, hook, 1);
    }

    /** As insertAfter, for a method that makes the call {@code expected} times and is to have the hook after each. */
    static byte[] insertAfterEach(byte[] classBytes, Method within, Method call, Method hook, int expected) {
        return insertAfterEach(classBytes, within, call, code -> invokeStatic(code, hook), expected);
    }

    /** Inserts {@code code} straight after each of the {@code expected} calls to {@code call} inside {@code within}. */
    static byte[] insertAfterEach(byte[] classBytes, Method within, Method call, Consumer<CodeBuilder> inserted, int expected) {
        int[] matches = {0};
        CodeTransform insert = (code, element) -> {
            code.with(element);
            if (element instanceof InvokeInstruction invoke && isCallTo(invoke, call)) {
                matches[0]++;
                inserted.accept(code);
            }
        };
        byte[] transformed = transform(classBytes, within, insert);
        if (matches[0] != expected) {
            throw new IllegalStateException("expected " + expected + " of the call to " + call + " in " + within + ", found " + matches[0]);
        }
        return transformed;
    }

    /**
     * Has each of the {@code expected} calls to {@code call} inside {@code within}, which is
     * made on an object, go to the static method {@code hook} instead. The hook takes that
     * object and then what the call took, and returns what the call returned. A call made on
     * no object goes to a hook that takes what it took.
     */
    static byte[] replaceEach(byte[] classBytes, Method within, Method call, Method hook, int expected) {
        int[] matches = {0};
        CodeTransform replace = (code, element) -> {
            if (element instanceof InvokeInstruction invoke && isCallTo(invoke, call)) {
                matches[0]++;
                invokeStatic(code, hook);
            } else {
                code.with(element);
            }
        };
        byte[] transformed = transform(classBytes, within, replace);
        if (matches[0] != expected) {
            throw new IllegalStateException("expected " + expected + " of the call to " + call + " in " + within + ", found " + matches[0]);
        }
        return transformed;
    }

    /**
     * Passes what {@code within} returns through the static method {@code filter},
     * which takes the value about to be returned and returns its replacement.
     */
    static byte[] filterReturn(byte[] classBytes, Method within, Method filter) {
        return insertBeforeReturns(classBytes, within, code -> invokeStatic(code, filter));
    }

    /**
     * Makes {@code within}, which returns nothing, return at once whenever the static method
     * {@code condition} answers true.
     */
    static byte[] returnAtOnceIf(byte[] classBytes, Method within, Method condition) {
        boolean[] begun = {false};
        CodeTransform insert = (code, element) -> {
            if (!begun[0]) {
                begun[0] = true;
                invokeStatic(code, condition);
                code.ifThen(Opcode.IFNE, CodeBuilder::return_);
            }
            code.with(element);
        };
        return transform(classBytes, within, insert);
    }

    /** As returnAtOnceIf, for a method that answers yes or no: where the condition holds it answers no. */
    static byte[] falseAtOnceIf(byte[] classBytes, Method within, Method condition) {
        boolean[] begun = {false};
        CodeTransform insert = (code, element) -> {
            if (!begun[0]) {
                begun[0] = true;
                invokeStatic(code, condition);
                code.ifThen(Opcode.IFNE, held -> {
                    held.iconst_0();
                    held.ireturn();
                });
            }
            code.with(element);
        };
        return transform(classBytes, within, insert);
    }

    /**
     * Inserts {@code code} before the first instruction of {@code within}, which has to be a
     * method of an object and not a static one: what is inserted loads the object. A static
     * method by that name would take the patch and then fail the JVM's check of the class.
     */
    static byte[] insertAtStartOfInstanceMethod(byte[] classBytes, Method within, Consumer<CodeBuilder> inserted) {
        for (MethodModel method : ClassFile.of().parse(classBytes).methods()) {
            if (method.methodName().equalsString(within.name()) && method.methodType().equalsString(within.descriptor())
                    && (method.flags().has(AccessFlag.STATIC) || method.flags().has(AccessFlag.ABSTRACT))) {
                throw new IllegalStateException(within + " is static or has no body");
            }
        }
        boolean[] begun = {false};
        CodeTransform insert = (code, element) -> {
            if (!begun[0]) {
                begun[0] = true;
                inserted.accept(code);
            }
            code.with(element);
        };
        return transform(classBytes, within, insert);
    }

    /** Inserts {@code code} before the first instruction of {@code within}, which has to be a static method with a body. */
    static byte[] insertAtStartOfStaticMethod(byte[] classBytes, Method within, Consumer<CodeBuilder> inserted) {
        for (MethodModel method : ClassFile.of().parse(classBytes).methods()) {
            if (method.methodName().equalsString(within.name()) && method.methodType().equalsString(within.descriptor())
                    && (!method.flags().has(AccessFlag.STATIC) || method.flags().has(AccessFlag.NATIVE))) {
                throw new IllegalStateException(within + " is not static or has no body");
            }
        }
        boolean[] begun = {false};
        CodeTransform insert = (code, element) -> {
            if (!begun[0]) {
                begun[0] = true;
                inserted.accept(code);
            }
            code.with(element);
        };
        return transform(classBytes, within, insert);
    }

    /**
     * Makes {@code within}, a static method that takes one object and returns one, work an
     * answer out once for each thing asked about. At its start it returns what the static
     * method {@code known} has for its argument, unless that is null, and then goes on as it
     * did. Before each of its own returns it hands what it was asked and what it is about to
     * answer to the static method {@code learn}. What it was asked is put by at the start, so
     * a method that writes over its argument as it works still says what it was asked.
     */
    static byte[] answerOnce(byte[] classBytes, Method within, Method known, Method learn) {
        for (MethodModel method : ClassFile.of().parse(classBytes).methods()) {
            if (method.methodName().equalsString(within.name()) && method.methodType().equalsString(within.descriptor()) && !method.flags().has(AccessFlag.STATIC)) {
                throw new IllegalStateException(within + " is not static");
            }
        }
        boolean[] begun = {false};
        int[] asked = {-1}, returns = {0};
        CodeTransform insert = (code, element) -> {
            if (!begun[0]) {
                begun[0] = true;
                asked[0] = code.allocateLocal(TypeKind.REFERENCE);
                code.aload(0);
                code.astore(asked[0]);
                code.aload(0);
                invokeStatic(code, known);
                code.dup();
                code.ifThenElse(Opcode.IFNONNULL, CodeBuilder::areturn, CodeBuilder::pop);
            }
            if (element instanceof ReturnInstruction) {
                returns[0]++;
                code.dup();
                code.aload(asked[0]);
                code.swap();
                invokeStatic(code, learn);
            }
            code.with(element);
        };
        byte[] transformed = transform(classBytes, within, insert);
        if (returns[0] == 0) {
            throw new IllegalStateException("found no return in " + within);
        }
        return transformed;
    }

    /**
     * Makes {@code within}, which is not static and returns a boolean, return false at once
     * whenever the ArrayList in the class's own {@code field} holds nothing. Throws unless the
     * class declares the field so: see requireStaticInt. The early answer is only right while
     * the method's answer is whether the list holds something it asks of it, so it also throws
     * unless the method reads the field once, calls {@code contains} once and calls nothing else
     * but java/lang/Integer, which boxes the number asked about, and zombie/util/Lambda, which
     * hands out the predicate.
     */
    static byte[] falseAtOnceWhileEmpty(byte[] classBytes, Method within, String field, Method contains) {
        ClassModel model = ClassFile.of().parse(classBytes);
        boolean declared = false;
        for (FieldModel candidate : model.fields()) {
            declared |= candidate.fieldName().equalsString(field) && candidate.fieldType().equalsString("Ljava/util/ArrayList;")
                    && !candidate.flags().has(AccessFlag.STATIC);
        }
        if (!declared) {
            throw new IllegalStateException("no ArrayList " + field + " of its own in " + model.thisClass().asInternalName());
        }
        MethodModel method = model.methods().stream()
                .filter(candidate -> candidate.methodName().equalsString(within.name()) && candidate.methodType().equalsString(within.descriptor()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no method " + within));
        int listReads = 0, containsCalls = 0, otherCalls = 0;
        for (CodeElement element : method.code().orElseThrow(() -> new IllegalStateException(within + " has no body")).elementList()) {
            if (element instanceof FieldInstruction read && read.name().equalsString(field)) {
                listReads++;
            } else if (element instanceof InvokeInstruction call) {
                if (isCallTo(call, contains)) {
                    containsCalls++;
                } else if (!call.owner().asInternalName().equals("java/lang/Integer") && !call.owner().asInternalName().equals("zombie/util/Lambda")) {
                    otherCalls++;
                }
            }
        }
        if (listReads != 1 || containsCalls != 1 || otherCalls != 0) {
            throw new IllegalStateException(within + " no longer only asks " + contains + " of " + field
                    + ": reads it " + listReads + " times, calls contains " + containsCalls + " times, makes " + otherCalls + " other calls");
        }
        ClassDesc list = ClassDesc.ofDescriptor("Ljava/util/ArrayList;");
        boolean[] begun = {false};
        CodeTransform insert = (code, element) -> {
            if (!begun[0]) {
                begun[0] = true;
                code.aload(0);
                code.getfield(ClassDesc.ofInternalName(within.owner()), field, list);
                code.invoke(Opcode.INVOKEVIRTUAL, list, "isEmpty", MethodTypeDesc.ofDescriptor("()Z"), false);
                code.ifThen(Opcode.IFNE, empty -> {
                    empty.iconst_0();
                    empty.ireturn();
                });
            }
            code.with(element);
        };
        return transform(classBytes, within, insert);
    }

    /**
     * Has {@code getter}, which is not static, takes nothing and returns an object, keep what
     * it returns in a field added to the class under the name {@code field}, and return that
     * from then on without working it out again. Nothing is kept while the answer is null. The
     * class is also given a method of its own for each of {@code changers}, which it inherits
     * from {@code parent} and which each take one object and return nothing: each forgets what
     * is kept and then does what the inherited one does, so an answer that could have changed
     * is worked out afresh. Throws where the class already has the field or any of the methods.
     */
    static byte[] keepInField(byte[] classBytes, Method getter, String field, String parent, List<Method> changers) {
        ClassFile classFile = ClassFile.of();
        ClassModel model = classFile.parse(classBytes);
        ClassDesc owner = model.thisClass().asSymbol();
        MethodTypeDesc got = MethodTypeDesc.ofDescriptor(getter.descriptor());
        ClassDesc kept = got.returnType();
        if (got.parameterCount() != 0 || kept.isPrimitive()) {
            throw new IllegalStateException(getter + " does not take nothing and return an object");
        }
        for (FieldModel candidate : model.fields()) {
            if (candidate.fieldName().equalsString(field)) {
                throw new IllegalStateException(model.thisClass().asInternalName() + " already has a field " + field);
            }
        }
        boolean[] found = {false};
        for (MethodModel method : model.methods()) {
            boolean isGetter = method.methodName().equalsString(getter.name()) && method.methodType().equalsString(getter.descriptor());
            if (isGetter && (method.flags().has(AccessFlag.STATIC) || method.flags().has(AccessFlag.ABSTRACT))) {
                throw new IllegalStateException(getter + " is static or has no body");
            }
            found[0] |= isGetter;
            for (Method changer : changers) {
                if (method.methodName().equalsString(changer.name()) && method.methodType().equalsString(changer.descriptor())) {
                    throw new IllegalStateException(model.thisClass().asInternalName() + " already has its own " + changer.name() + changer.descriptor());
                }
            }
        }
        if (!found[0]) {
            throw new IllegalStateException("no method " + getter);
        }
        boolean[] begun = {false};
        CodeTransform keep = (code, element) -> {
            if (!begun[0]) {
                begun[0] = true;
                code.aload(0).getfield(owner, field, kept);
                code.ifThen(Opcode.IFNONNULL, has -> has.aload(0).getfield(owner, field, kept).areturn());
            }
            if (element instanceof ReturnInstruction) {
                code.dup().aload(0).swap().putfield(owner, field, kept);
            }
            code.with(element);
        };
        ClassTransform keeping = ClassTransform.transformingMethodBodies(
                method -> method.methodName().equalsString(getter.name()) && method.methodType().equalsString(getter.descriptor()), keep);
        ClassTransform adding = ClassTransform.endHandler(made -> {
            made.withField(field, kept, ClassFile.ACC_PRIVATE | ClassFile.ACC_TRANSIENT | ClassFile.ACC_SYNTHETIC);
            for (Method changer : changers) {
                MethodTypeDesc type = MethodTypeDesc.ofDescriptor(changer.descriptor());
                made.withMethodBody(changer.name(), type, ClassFile.ACC_PUBLIC | ClassFile.ACC_SYNTHETIC, code -> code
                        .aload(0).aconst_null().putfield(owner, field, kept)
                        .aload(0).aload(1).invokespecial(ClassDesc.ofInternalName(parent), changer.name(), type)
                        .return_());
            }
        });
        return classFile.transformClass(model, keeping.andThen(adding));
    }

    /** Inserts {@code code} before every return of {@code within}, which has to have at least one. */
    static byte[] insertBeforeReturns(byte[] classBytes, Method within, Consumer<CodeBuilder> inserted) {
        int[] matches = {0};
        CodeTransform insert = (code, element) -> {
            if (element instanceof ReturnInstruction) {
                matches[0]++;
                inserted.accept(code);
            }
            code.with(element);
        };
        byte[] transformed = transform(classBytes, within, insert);
        if (matches[0] == 0) {
            throw new IllegalStateException("found no return in " + within);
        }
        return transformed;
    }

    /**
     * Inserts {@code code} before every return of {@code within} and on every throw out of it,
     * which is then thrown on. On a throw the thrown object is on the stack under what the code
     * pushes, so the code has to leave the stack as it found it.
     */
    static byte[] insertOnEveryWayOut(byte[] classBytes, Method within, Consumer<CodeBuilder> inserted) {
        int[] matches = {0};
        CodeTransform insert = new CodeTransform() {
            private Label start;

            @Override
            public void accept(CodeBuilder code, CodeElement element) {
                if (start == null) {
                    start = code.newBoundLabel();
                }
                if (element instanceof ReturnInstruction) {
                    matches[0]++;
                    inserted.accept(code);
                }
                code.with(element);
            }

            @Override
            public void atEnd(CodeBuilder code) {
                Label handler = code.newBoundLabel();
                code.exceptionCatchAll(start, handler, handler);
                inserted.accept(code);
                code.athrow();
            }
        };
        byte[] transformed = transform(classBytes, within, insert);
        if (matches[0] == 0) {
            throw new IllegalStateException("found no return in " + within);
        }
        return transformed;
    }

    /**
     * Calls the static method {@code begins} before anything else in {@code within}, and the
     * static method {@code ends} before each of its returns and on every throw out of it, which
     * is then thrown on, so whatever begins with the method ends with it. Both take and return
     * nothing. The method has to have at least one return.
     */
    static byte[] insertAround(byte[] classBytes, Method within, Method begins, Method ends) {
        int[] returns = {0};
        CodeTransform around = new CodeTransform() {
            private Label start;

            @Override
            public void accept(CodeBuilder code, CodeElement element) {
                if (start == null) {
                    invokeStatic(code, begins);
                    start = code.newBoundLabel();
                }
                if (element instanceof ReturnInstruction) {
                    returns[0]++;
                    invokeStatic(code, ends);
                }
                code.with(element);
            }

            @Override
            public void atEnd(CodeBuilder code) {
                Label handler = code.newBoundLabel();
                code.exceptionCatchAll(start, handler, handler);
                invokeStatic(code, ends);
                code.athrow();
            }
        };
        byte[] transformed = transform(classBytes, within, around);
        if (returns[0] == 0) {
            throw new IllegalStateException("found no return in " + within);
        }
        return transformed;
    }

    /**
     * Throws unless the class declares {@code field} as a static int that can
     * be written, which is what filterStaticInt reads and writes. The JVM only
     * looks a field up when the instruction first runs, so without this a build
     * of the game that renamed or retyped the field would take the patch and
     * then throw from inside the game's own method on every call.
     */
    static void requireStaticInt(byte[] classBytes, String field) {
        ClassModel model = ClassFile.of().parse(classBytes);
        for (FieldModel candidate : model.fields()) {
            if (candidate.fieldName().equalsString(field)) {
                if (!candidate.fieldType().equalsString("I") || !candidate.flags().has(AccessFlag.STATIC)
                        || candidate.flags().has(AccessFlag.FINAL)) {
                    throw new IllegalStateException(model.thisClass().asInternalName() + "." + field
                            + " is " + candidate.flags().flags() + " " + candidate.fieldType().stringValue()
                            + ", expected a static int that is not final");
                }
                return;
            }
        }
        throw new IllegalStateException("no field " + field + " in " + model.thisClass().asInternalName());
    }

    /**
     * Passes a static int field of the class being rewritten through the
     * static method {@code filter}, which takes the value and returns its
     * replacement.
     */
    static void filterStaticInt(CodeBuilder code, String owner, String field, Method filter) {
        ClassDesc ownerDesc = ClassDesc.ofInternalName(owner);
        ClassDesc intDesc = ClassDesc.ofDescriptor("I");
        code.getstatic(ownerDesc, field, intDesc);
        invokeStatic(code, filter);
        code.putstatic(ownerDesc, field, intDesc);
    }

    /**
     * Passes each read of the static int {@code field} of the class {@code owner} inside
     * {@code within} that is compared with the constant {@code against} through the static
     * method {@code filter}, which takes the value read and returns its replacement. Such a
     * read is the field, then the constant, then a branch on the two. A read used any other
     * way is left alone, and one followed by the constant and anything but that branch throws.
     */
    static byte[] filterStaticIntCompared(byte[] classBytes, Method within, String owner, String field, int against, Method filter, int expected) {
        int[] matches = {0};
        // 0 between reads, 1 with the field read and nothing after it yet, 2 with the constant after it.
        int[] stage = {0};
        CodeTransform insert = (code, element) -> {
            if (!(element instanceof Instruction instruction)) {
                code.with(element);
                return;
            }
            if (stage[0] == 2) {
                if (!(instruction instanceof BranchInstruction branch) || !comparesTwoInts(branch.opcode())) {
                    throw new IllegalStateException(owner + "." + field + " and " + against + " are followed by " + instruction.opcode()
                            + " in " + within + ", not by a branch on the two");
                }
                matches[0]++;
                stage[0] = 0;
            } else if (stage[0] == 1) {
                stage[0] = 0;
                if (instruction instanceof ConstantInstruction constant && constant.constantValue() instanceof Integer value && value == against) {
                    invokeStatic(code, filter);
                    stage[0] = 2;
                }
            }
            if (instruction instanceof FieldInstruction read && read.opcode() == Opcode.GETSTATIC && read.owner().asInternalName().equals(owner)
                    && read.name().equalsString(field) && read.type().equalsString("I")) {
                stage[0] = 1;
            }
            code.with(element);
        };
        byte[] transformed = transform(classBytes, within, insert);
        if (matches[0] != expected) {
            throw new IllegalStateException("expected " + expected + " of " + owner + "." + field + " compared with " + against + " in " + within
                    + ", found " + matches[0]);
        }
        return transformed;
    }

    /**
     * Passes each of the {@code expected} reads of the static boolean {@code field} of the class
     * {@code owner} inside {@code within} through the static method {@code filter}, which takes
     * the value read and returns its replacement.
     */
    static byte[] filterStaticBooleanReads(byte[] classBytes, Method within, String owner, String field, Method filter, int expected) {
        int[] matches = {0};
        CodeTransform insert = (code, element) -> {
            code.with(element);
            if (element instanceof FieldInstruction read && read.opcode() == Opcode.GETSTATIC && read.owner().asInternalName().equals(owner)
                    && read.name().equalsString(field) && read.type().equalsString("Z")) {
                matches[0]++;
                invokeStatic(code, filter);
            }
        };
        byte[] transformed = transform(classBytes, within, insert);
        if (matches[0] != expected) {
            throw new IllegalStateException("expected " + expected + " of the read of " + owner + "." + field + " in " + within + ", found " + matches[0]);
        }
        return transformed;
    }

    private static boolean comparesTwoInts(Opcode opcode) {
        return switch (opcode) {
            case IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE -> true;
            default -> false;
        };
    }

    /**
     * Passes the first of the two ints each {@code operation} inside {@code within} takes
     * through the static method {@code filter}, which takes the value and returns its
     * replacement. The operation is one that takes two ints off the stack: a remainder, a
     * branch on two ints.
     */
    static byte[] filterFirstOperandOfEach(byte[] classBytes, Method within, Opcode operation, Method filter, int expected) {
        if (operation != Opcode.IREM && !comparesTwoInts(operation)) {
            throw new IllegalArgumentException(operation + " does not take two ints");
        }
        int[] matches = {0};
        CodeTransform insert = (code, element) -> {
            if (element instanceof Instruction instruction && instruction.opcode() == operation) {
                matches[0]++;
                code.swap();
                invokeStatic(code, filter);
                code.swap();
            }
            code.with(element);
        };
        byte[] transformed = transform(classBytes, within, insert);
        if (matches[0] != expected) {
            throw new IllegalStateException("expected " + expected + " of " + operation + " in " + within + ", found " + matches[0]);
        }
        return transformed;
    }

    /**
     * Passes the first argument of {@code within}, a static method whose first argument is an
     * int, through the static method {@code filter} before anything else in it runs.
     */
    static byte[] filterFirstStaticIntArgument(byte[] classBytes, Method within, Method filter) {
        if (!within.descriptor().startsWith("(I")) {
            throw new IllegalStateException(within + " does not take an int first");
        }
        for (MethodModel method : ClassFile.of().parse(classBytes).methods()) {
            if (method.methodName().equalsString(within.name()) && method.methodType().equalsString(within.descriptor())
                    && (!method.flags().has(AccessFlag.STATIC) || method.flags().has(AccessFlag.ABSTRACT))) {
                throw new IllegalStateException(within + " is not static or has no body");
            }
        }
        boolean[] begun = {false};
        CodeTransform insert = (code, element) -> {
            if (!begun[0]) {
                begun[0] = true;
                code.iload(0);
                invokeStatic(code, filter);
                code.istore(0);
            }
            code.with(element);
        };
        return transform(classBytes, within, insert);
    }

    /** Calls the static method {@code hook} with the object the method being rewritten belongs to. */
    static void passThis(CodeBuilder code, Method hook) {
        code.aload(0);
        invokeStatic(code, hook);
    }

    /**
     * Calls the static method {@code hook} with whatever the method being rewritten has on
     * its stack and then its first argument. The method has to be an instance method that
     * does not assign to that argument.
     */
    static void passFirstArgument(CodeBuilder code, Method hook) {
        code.aload(1);
        invokeStatic(code, hook);
    }

    /** The same for a static method, whose first argument is where an instance method has itself. */
    static void passFirstStaticArgument(CodeBuilder code, Method hook) {
        code.aload(0);
        invokeStatic(code, hook);
    }

    /** Calls the static method {@code hook} with whatever the static method being rewritten has on its stack and then its first argument, an int. */
    static void passFirstStaticInt(CodeBuilder code, Method hook) {
        code.iload(0);
        invokeStatic(code, hook);
    }

    static void invokeStatic(CodeBuilder code, Method method) {
        code.invoke(Opcode.INVOKESTATIC, ClassDesc.ofInternalName(method.owner()), method.name(),
                MethodTypeDesc.ofDescriptor(method.descriptor()), false);
    }

    private static byte[] transform(byte[] classBytes, Method within, CodeTransform transform) {
        ClassFile classFile = ClassFile.of();
        ClassModel model = classFile.parse(classBytes);
        boolean[] found = {false};
        byte[] transformed = classFile.transformClass(model, ClassTransform.transformingMethodBodies(
                method -> {
                    boolean match = method.methodName().equalsString(within.name())
                            && method.methodType().equalsString(within.descriptor());
                    found[0] |= match;
                    return match;
                },
                transform));
        if (!found[0]) {
            throw new IllegalStateException("no method " + within);
        }
        return transformed;
    }

    private static boolean isCallTo(InvokeInstruction invoke, Method call) {
        return invoke.owner().asInternalName().equals(call.owner())
                && invoke.name().equalsString(call.name())
                && invoke.type().equalsString(call.descriptor());
    }
}
