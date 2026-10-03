package com.cameraunlock.zomboid;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

final class CallSite {
    /** A method named by internal class name, method name and descriptor. */
    record Method(String owner, String name, String descriptor) {}

    private CallSite() {}

    /**
     * Inserts a call to {@code hook}, a static method taking and returning
     * nothing, straight after the one call to {@code call} inside
     * {@code within}. Any other number of matches is a game build this was not
     * written for, and throws rather than guessing which call was meant.
     */
    static byte[] insertAfter(byte[] classBytes, Method within, Method call, Method hook) {
        ClassFile classFile = ClassFile.of();
        ClassModel model = classFile.parse(classBytes);
        int[] matches = {0};

        CodeTransform insert = (code, element) -> {
            code.with(element);
            if (element instanceof InvokeInstruction invoke && isCallTo(invoke, call)) {
                matches[0]++;
                code.invokestatic(ClassDesc.ofInternalName(hook.owner()), hook.name(),
                        MethodTypeDesc.ofDescriptor(hook.descriptor()));
            }
        };
        byte[] transformed = classFile.transformClass(model, ClassTransform.transformingMethodBodies(
                method -> method.methodName().equalsString(within.name())
                        && method.methodType().equalsString(within.descriptor()),
                insert));

        if (matches[0] != 1) {
            throw new IllegalStateException("expected one call to " + call + " in " + within + ", found " + matches[0]);
        }
        return transformed;
    }

    private static boolean isCallTo(InvokeInstruction invoke, Method call) {
        return invoke.owner().asInternalName().equals(call.owner())
                && invoke.name().equalsString(call.name())
                && invoke.type().equalsString(call.descriptor());
    }
}
