package com.cameraunlock.zomboid;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;

/**
 * What is on a square, as the running build of the game hands it out.
 *
 * {@code IsoGridSquare.getObjects()} has returned the game's own PZArrayList (42.21.0) and a
 * java.util.List (42.22.2), and the mod only ever reads it as a List. A call compiled against
 * either return type fails on the other build, so the method is found on the running build by
 * what it is: the one method of the square by that name that takes nothing and returns a List.
 */
final class SquareObjects {
    private static final String SQUARE = "zombie/iso/IsoGridSquare";
    private static final String NAME = "getObjects";
    private static final String LIST = "java/util/List";

    private SquareObjects() {}

    /** Apart, so that missing, which runs before the game's classes may load, does not load the square's. */
    private static final class Found {
        static final MethodHandle OBJECTS = find();
    }

    @SuppressWarnings("unchecked")
    static List<IsoObject> of(IsoGridSquare square) {
        try {
            return (List<IsoObject>) Found.OBJECTS.invokeExact(square);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable checked) {
            // A getter declares nothing checked: invokeExact only makes Java ask.
            throw new IllegalStateException(checked);
        }
    }

    private static MethodHandle find() {
        Method found = null;
        for (Method method : IsoGridSquare.class.getMethods()) {
            if (method.getName().equals(NAME) && method.getParameterCount() == 0 && !Modifier.isStatic(method.getModifiers()) && !method.isBridge()
                    && List.class.isAssignableFrom(method.getReturnType())) {
                if (found != null) {
                    throw new IllegalStateException("IsoGridSquare has two methods " + NAME + " that return a list: " + found + " and " + method);
                }
                found = method;
            }
        }
        if (found == null) {
            throw new IllegalStateException("IsoGridSquare has no method " + NAME + " that takes nothing and returns a list");
        }
        try {
            return MethodHandles.publicLookup().unreflect(found).asType(MethodType.methodType(List.class, IsoGridSquare.class));
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /**
     * Why {@link #of} would not find the method on the game whose class files {@code classBytes}
     * hands out, or null when it would. Read from the class files, so nothing of the game is
     * loaded before its patches are registered: see Links.
     */
    static String missing(Function<String, byte[]> classBytes) {
        byte[] square = classBytes.apply(SQUARE);
        if (square == null) {
            return "class " + SQUARE + ": no such class";
        }
        int found = 0;
        for (MethodModel method : ClassFile.of().parse(square).methods()) {
            if (!method.methodName().equalsString(NAME) || method.flags().has(AccessFlag.STATIC) || !method.flags().has(AccessFlag.PUBLIC)
                    || method.flags().has(AccessFlag.BRIDGE)) {
                continue;
            }
            MethodTypeDesc type = method.methodTypeSymbol();
            if (type.parameterCount() == 0 && !type.returnType().isPrimitive() && !type.returnType().isArray()
                    && isList(internalName(type.returnType().descriptorString()), classBytes)) {
                found++;
            }
        }
        return found == 1 ? null
                : SQUARE + "." + NAME + ": " + (found == 0 ? "no such method that takes nothing and returns a list" : found + " such methods");
    }

    private static String internalName(String descriptor) {
        return descriptor.substring(1, descriptor.length() - 1);
    }

    private static boolean isList(String type, Function<String, byte[]> classBytes) {
        ArrayDeque<String> queue = new ArrayDeque<>(List.of(type));
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(LIST)) {
                return true;
            }
            byte[] bytes = visited.add(current) ? classBytes.apply(current) : null;
            if (bytes == null) {
                continue;
            }
            ClassModel model = ClassFile.of().parse(bytes);
            model.superclass().ifPresent(above -> queue.add(above.asInternalName()));
            model.interfaces().forEach(above -> queue.add(above.asInternalName()));
        }
        return false;
    }
}
