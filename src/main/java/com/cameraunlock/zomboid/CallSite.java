package com.cameraunlock.zomboid;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
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
        int[] matches = {0};
        CodeTransform insert = (code, element) -> {
            code.with(element);
            if (element instanceof InvokeInstruction invoke && isCallTo(invoke, call)) {
                matches[0]++;
                invokeStatic(code, hook);
            }
        };
        byte[] transformed = transform(classBytes, within, insert);
        if (matches[0] != 1) {
            throw new IllegalStateException("expected one call to " + call + " in " + within + ", found " + matches[0]);
        }
        return transformed;
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
