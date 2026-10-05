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
