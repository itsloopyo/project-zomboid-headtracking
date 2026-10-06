package com.cameraunlock.zomboid;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
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

    /**
     * Makes {@code within}, a static method that takes one object and returns one, return what
     * the static method {@code answer} makes of its argument whenever the argument is not null.
     */
    static byte[] answerAtOnceUnlessNull(byte[] classBytes, Method within, Method answer) {
        boolean[] begun = {false};
        CodeTransform insert = (code, element) -> {
            if (!begun[0]) {
                begun[0] = true;
                code.aload(0);
                code.ifThen(Opcode.IFNONNULL, asked -> {
                    asked.aload(0);
                    invokeStatic(asked, answer);
                    asked.areturn();
                });
            }
            code.with(element);
        };
        return transform(classBytes, within, insert);
    }

    /**
     * Makes {@code within}, which is not static and returns a boolean, return false at once
     * whenever the ArrayList in the class's own {@code field} holds nothing. Throws unless the
     * class declares the field so: see requireStaticInt.
     */
    static byte[] falseAtOnceWhileEmpty(byte[] classBytes, Method within, String field) {
        ClassModel model = ClassFile.of().parse(classBytes);
        boolean declared = false;
        for (FieldModel candidate : model.fields()) {
            declared |= candidate.fieldName().equalsString(field) && candidate.fieldType().equalsString("Ljava/util/ArrayList;")
                    && !candidate.flags().has(AccessFlag.STATIC);
        }
        if (!declared) {
            throw new IllegalStateException("no ArrayList " + field + " of its own in " + model.thisClass().asInternalName());
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
    static byte[] keepInField(byte[] classBytes, Method getter, String field, String parent, java.util.List<Method> changers) {
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
        for (java.lang.classfile.MethodModel method : model.methods()) {
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

    private static void invokeStatic(CodeBuilder code, Method method) {
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
