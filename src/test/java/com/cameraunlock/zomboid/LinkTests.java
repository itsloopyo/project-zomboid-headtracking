package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicCallSiteDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Links against a game made up here: a few classes with the shapes the mod leans on, each
 * then changed the way a game update could change it.
 */
final class LinkTests {
    private static final int PUBLIC = ClassFile.ACC_PUBLIC, STATIC = ClassFile.ACC_STATIC, FINAL = ClassFile.ACC_FINAL,
            ABSTRACT = ClassFile.ACC_ABSTRACT, INTERFACE = ClassFile.ACC_INTERFACE;
    private static final ClassDesc BASE = ClassDesc.of("game.Base"), THING = ClassDesc.of("game.Thing"),
            NAMED = ClassDesc.of("game.Named"), DRAWER = ClassDesc.of("game.Drawer"), OBJECT = ConstantDescs.CD_Object,
            INT = ConstantDescs.CD_int, LONG = ConstantDescs.CD_long;
    private static final MethodTypeDesc TO_INT = MethodTypeDesc.of(INT), TO_LONG = MethodTypeDesc.of(LONG),
            TO_NOTHING = MethodTypeDesc.of(ConstantDescs.CD_void);

    private LinkTests() {}

    static void run() {
        Checks.run("everythingUsedAsItIsResolves", LinkTests::everythingUsedAsItIsResolves);
        Checks.run("aMethodIsFoundAsItIsCalled", LinkTests::aMethodIsFoundAsItIsCalled);
        Checks.run("aFieldIsFoundAsItIsUsed", LinkTests::aFieldIsFoundAsItIsUsed);
        Checks.run("aClassOnlyNamedIsLookedUp", LinkTests::aClassOnlyNamedIsLookedUp);
        Checks.run("aMethodReferenceIsLookedUp", LinkTests::aMethodReferenceIsLookedUp);
        Checks.run("aFieldReadByNameIsDeclaredAsRead", LinkTests::aFieldReadByNameIsDeclaredAsRead);
        Checks.run("aClassMadeOfOneOfTheGamesFitsIt", LinkTests::aClassMadeOfOneOfTheGamesFitsIt);
        Checks.run("aGameThatIsNotThereResolvesNothing", LinkTests::aGameThatIsNotThereResolvesNothing);
        Checks.run("aMethodIsHandedOnOnlyWhereItIsInheritedWithABody", LinkTests::aMethodIsHandedOnOnlyWhereItIsInheritedWithABody);
        Checks.run("whatTheJarCarriesIsNotLookedForInTheGame", LinkTests::whatTheJarCarriesIsNotLookedForInTheGame);
    }

    /**
     * A patch that gives a class a method of its own, to hand on to the one it inherits, goes
     * in only where that one is inherited and can be called on: a game update that made it
     * final, static, private or abstract, or took it away, is named and the patch stays out.
     */
    private static void aMethodIsHandedOnOnlyWhereItIsInheritedWithABody() {
        String base = "game/Base", thing = "game/Thing", shape = "game/Shape";
        Map<String, byte[]> game = new HashMap<>();
        game.put(thing, thing(type -> { }));
        game.put(base, base());
        Checks.check(Links.notInherited(thing, "inherited", "()I", game::get) == null, "a public method of the class above, with a body, is handed on to");
        Checks.check(Links.notInherited(base, "inherited", "()I", game::get) == null, "and so is one asked of the class that has it");
        String missing = Links.notInherited(thing, "gone", "()I", game::get);
        Checks.check(missing != null && missing.contains("neither " + thing), "a method nothing above has is named as missing: " + missing);
        Checks.check(Links.notInherited(thing, "inherited", "()J", game::get) != null, "and so is one of another type");
        Map<Integer, String> flags = Map.of(FINAL, "final", STATIC, "static", ClassFile.ACC_PRIVATE, "private", ABSTRACT, "abstract");
        for (int flag : flags.keySet()) {
            game.put(base, type(BASE, PUBLIC | (flag == ABSTRACT ? ABSTRACT : 0), OBJECT, method("inherited", TO_INT, PUBLIC & ~(flag == ClassFile.ACC_PRIVATE ? PUBLIC : 0) | flag)));
            String why = Links.notInherited(thing, "inherited", "()I", game::get);
            Checks.check((base + " has it " + flags.get(flag)).equals(why), "a method the class above has made " + flags.get(flag) + " is refused, and where and why are said: " + why);
        }
        game.put(base, base());
        game.put(shape, type(ClassDesc.of("game.Shape"), PUBLIC | INTERFACE | ABSTRACT, OBJECT, method("sides", TO_INT, PUBLIC)));
        game.put(thing, ClassFile.of().build(THING, type -> type.withFlags(PUBLIC).withSuperclass(BASE).withInterfaceSymbols(ClassDesc.of("game.Shape"))));
        Checks.check(Links.notInherited(thing, "sides", "()I", game::get) == null, "a default method of an interface the class has is handed on to");
        game.put(shape, type(ClassDesc.of("game.Shape"), PUBLIC | INTERFACE | ABSTRACT, OBJECT, method("sides", TO_INT, PUBLIC | ABSTRACT)));
        Checks.check(Links.notInherited(thing, "sides", "()I", game::get) != null, "and an interface's method with no body is refused");
    }

    /**
     * The jar carries cameraunlock-core and the WebAssembly runtime beside the mod's own classes, and
     * what they name of each other is not the game's: the built classes, linked against the stubs of
     * the game, name nothing missing that the jar has.
     */
    private static void whatTheJarCarriesIsNotLookedForInTheGame() {
        try {
            List<byte[]> built = Links.classesIn(Path.of("build/classes"));
            Set<String> shipped = new HashSet<>();
            built.forEach(bytes -> shipped.add(ClassFile.of().parse(bytes).thisClass().asInternalName()));
            Checks.check(shipped.contains("run/endive/runtime/Instance") && shipped.contains("com/cameraunlock/core/wasm/CoreModule"),
                    "the built classes carry the runtime and core's module");
            Links.Result result = Links.check(built, Links.REFLECTED, name -> {
                Path stub = Path.of("build/stubs", name + ".class");
                try (InputStream in = Files.exists(stub) ? Files.newInputStream(stub) : ClassLoader.getSystemResourceAsStream(name + ".class")) {
                    return in == null ? null : in.readAllBytes();
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            });
            List<String> ours = result.unresolved().stream()
                    .filter(missing -> shipped.stream().anyMatch(name -> missing.startsWith(name + ".") || missing.startsWith("class " + name + ":")
                            || missing.startsWith(name + " extends")))
                    .toList();
            Checks.check(ours.isEmpty(), "nothing the jar carries is looked for in the game, got: " + ours);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static byte[] type(ClassDesc name, int flags, ClassDesc parent, Consumer<ClassBuilder> members) {
        return ClassFile.of().build(name, type -> {
            type.withFlags(flags).withSuperclass(parent);
            members.accept(type);
        });
    }

    private static Consumer<ClassBuilder> method(String name, MethodTypeDesc descriptor, int flags) {
        return type -> type.withMethod(name, descriptor, flags, method -> { });
    }

    private static Consumer<ClassBuilder> field(String name, ClassDesc descriptor, int flags) {
        return type -> type.withField(name, descriptor, flags);
    }

    private static byte[] base() {
        return type(BASE, PUBLIC, OBJECT, method("inherited", TO_INT, PUBLIC).andThen(field("count", INT, PUBLIC)));
    }

    private static byte[] thing(Consumer<ClassBuilder> members) {
        return type(THING, PUBLIC, BASE, members);
    }

    private static byte[] named(int flags) {
        return type(NAMED, flags, OBJECT, method("number", TO_INT, PUBLIC | ABSTRACT));
    }

    private static byte[] ours(ClassDesc parent, Consumer<ClassBuilder> members, Consumer<CodeBuilder> use) {
        return type(ClassDesc.of("com.cameraunlock.zomboid.LinkSubject"), PUBLIC, parent, members.andThen(type ->
                type.withMethodBody("use", TO_NOTHING, PUBLIC | STATIC, code -> {
                    use.accept(code);
                    code.return_();
                })));
    }

    private static Links.Result check(byte[] ours, List<Links.Field> reflected, byte[]... game) {
        Map<String, byte[]> classes = new HashMap<>();
        for (byte[] one : game) {
            classes.put(ClassFile.of().parse(one).thisClass().asInternalName(), one);
        }
        return Links.check(List.of(ours), reflected, name -> {
            if (!name.startsWith("java/")) {
                return classes.get(name);
            }
            try (InputStream in = ClassLoader.getSystemResourceAsStream(name + ".class")) {
                return in.readAllBytes();
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        });
    }

    private static Links.Result check(Consumer<CodeBuilder> use, byte[]... game) {
        return check(ours(OBJECT, type -> { }, use), List.of(), game);
    }

    private static void accepted(String what, Links.Result result) {
        Checks.check(result.unresolved().isEmpty(), what + ", got: " + result.unresolved());
    }

    private static void refused(String what, String why, Links.Result result) {
        Checks.check(result.unresolved().size() == 1 && result.unresolved().getFirst().endsWith(": " + why),
                what + ": expected one refusal ending \"" + why + "\", got: " + result.unresolved());
    }

    private static final Consumer<CodeBuilder> MAKE = code -> code.invokestatic(THING, "make", TO_INT).pop();
    private static final Consumer<CodeBuilder> INHERITED = code -> code.aconst_null().invokevirtual(THING, "inherited", TO_INT).pop();
    private static final Consumer<CodeBuilder> NUMBER = code -> code.aconst_null().invokeinterface(NAMED, "number", TO_INT).pop();
    private static final Consumer<CodeBuilder> COUNT = code -> code.aconst_null().getfield(THING, "count", INT).pop();
    private static final Consumer<CodeBuilder> SET_TOTAL = code -> code.iconst_0().putstatic(THING, "total", INT);
    private static final Consumer<CodeBuilder> IS_THING = code -> code.aconst_null().instanceOf(THING).pop();
    private static final Consumer<CodeBuilder> MAKE_BY_REFERENCE = code -> code.invokedynamic(DynamicCallSiteDesc.of(
            ConstantDescs.ofCallsiteBootstrap(ClassDesc.of("java.lang.invoke.LambdaMetafactory"), "metafactory",
                    ConstantDescs.CD_CallSite, ConstantDescs.CD_MethodType, ConstantDescs.CD_MethodHandle, ConstantDescs.CD_MethodType),
            "getAsInt", MethodTypeDesc.of(ClassDesc.of("java.util.function.IntSupplier")),
            TO_INT, MethodHandleDesc.ofMethod(DirectMethodHandleDesc.Kind.STATIC, THING, "make", TO_INT), TO_INT)).pop();

    private static final Consumer<ClassBuilder> THING_AS_USED = method("make", TO_INT, PUBLIC | STATIC).andThen(field("total", INT, PUBLIC | STATIC));

    private static void everythingUsedAsItIsResolves() {
        Links.Result result = check(MAKE.andThen(INHERITED).andThen(NUMBER).andThen(COUNT).andThen(SET_TOTAL).andThen(IS_THING)
                .andThen(MAKE_BY_REFERENCE).andThen(MAKE), base(), thing(THING_AS_USED), named(PUBLIC | INTERFACE | ABSTRACT));
        accepted("a game with every member as it is used resolves", result);
        // make is called twice and referred to once more, and is one thing.
        if (result.references() != 6) {
            throw new AssertionError("six distinct things of the game's are counted, got " + result.references());
        }
    }

    private static void aMethodIsFoundAsItIsCalled() {
        accepted("a method the class inherits is found above it", check(INHERITED, base(), thing(THING_AS_USED)));
        refused("a method that is gone", "no such method", check(MAKE, base(), thing(method("build", TO_INT, PUBLIC | STATIC))));
        refused("a method that returns another type", "no such method", check(MAKE, base(), thing(method("make", TO_LONG, PUBLIC | STATIC))));
        refused("a static method made an instance one", "the method is no longer static", check(MAKE, base(), thing(method("make", TO_INT, PUBLIC))));
        refused("an instance method made static", "the method is now static",
                check(INHERITED, base(), thing(method("inherited", TO_INT, PUBLIC | STATIC))));
        refused("a method made private", "the method is not public", check(MAKE, base(), thing(method("make", TO_INT, ClassFile.ACC_PRIVATE | STATIC))));
        refused("a class that is gone", "no such class", check(MAKE, base()));
        refused("a class no longer public", "the class is not public", check(MAKE, base(), type(THING, 0, BASE, THING_AS_USED)));
        refused("a class whose parent is gone", "no class game/Base above it", check(INHERITED, thing(THING_AS_USED)));
        refused("an interface made a class", "the class is no longer an interface", check(NUMBER, named(PUBLIC | ABSTRACT)));
    }

    private static void aFieldIsFoundAsItIsUsed() {
        accepted("a field the class inherits is found above it", check(COUNT, base(), thing(THING_AS_USED)));
        refused("a field that is gone", "no such field", check(SET_TOTAL, base(), thing(field("sum", INT, PUBLIC | STATIC))));
        refused("a field of another type", "no such field", check(SET_TOTAL, base(), thing(field("total", LONG, PUBLIC | STATIC))));
        refused("a static field made an instance one", "the field is no longer static", check(SET_TOTAL, base(), thing(field("total", INT, PUBLIC))));
        refused("a field made private", "the field is not public", check(SET_TOTAL, base(), thing(field("total", INT, ClassFile.ACC_PRIVATE | STATIC))));
        refused("a written field made final", "the field is written and is now final",
                check(SET_TOTAL, base(), thing(field("total", INT, PUBLIC | STATIC | FINAL))));
        accepted("a field made final that is only read", check(code -> code.getstatic(THING, "total", INT).pop(),
                base(), thing(field("total", INT, PUBLIC | STATIC | FINAL))));
    }

    private static void aClassOnlyNamedIsLookedUp() {
        accepted("a class that is tested for is found", check(IS_THING, base(), thing(THING_AS_USED)));
        refused("a class that is tested for and gone", "no such class", check(IS_THING, base()));
        refused("a class that is made and gone", "no such class", check(code -> code.new_(THING).pop(), base()));
    }

    private static void aMethodReferenceIsLookedUp() {
        accepted("a method referred to is found", check(MAKE_BY_REFERENCE, base(), thing(THING_AS_USED)));
        refused("a method referred to and gone", "no such method", check(MAKE_BY_REFERENCE, base(), thing(method("build", TO_INT, PUBLIC | STATIC))));
    }

    private static void aFieldReadByNameIsDeclaredAsRead() {
        byte[] nothingUsed = ours(OBJECT, type -> { }, code -> { });
        List<Links.Field> hidden = List.of(new Links.Field("game/Thing", "hidden", "I"));
        accepted("a private field read by name is found", check(nothingUsed, hidden, base(), thing(field("hidden", INT, ClassFile.ACC_PRIVATE))));
        refused("a field read by name and gone", "no such field", check(nothingUsed, hidden, base(), thing(THING_AS_USED)));
        refused("a field read by name of another type", "the field is now J", check(nothingUsed, hidden, base(), thing(field("hidden", LONG, 0))));
        refused("a field read by name made static", "the field is now static", check(nothingUsed, hidden, base(), thing(field("hidden", INT, STATIC))));
        // getDeclaredField does not look above the class it is asked of.
        refused("a field read by name moved to the parent", "no such field",
                check(nothingUsed, List.of(new Links.Field("game/Thing", "count", "I")), base(), thing(THING_AS_USED)));
        refused("a field read by name of a class that is gone", "no such class", check(nothingUsed, hidden, base()));
    }

    private static void aClassMadeOfOneOfTheGamesFitsIt() {
        byte[] drawer = ours(DRAWER, method("render", TO_NOTHING, PUBLIC), code -> { });
        Consumer<ClassBuilder> render = method("render", TO_NOTHING, PUBLIC | ABSTRACT);
        accepted("a class that writes what its parent leaves to it fits", check(drawer, List.of(), type(DRAWER, PUBLIC | ABSTRACT, OBJECT, render)));
        refused("a parent with one more method to write", "abstract postRender()V is not written", check(drawer, List.of(),
                type(DRAWER, PUBLIC | ABSTRACT, OBJECT, render.andThen(method("postRender", TO_NOTHING, PUBLIC | ABSTRACT)))));
        refused("a parent that no longer lets render be replaced", "final render()V is replaced", check(drawer, List.of(),
                type(DRAWER, PUBLIC | ABSTRACT, OBJECT, method("render", TO_NOTHING, PUBLIC | FINAL))));
        refused("a parent made final", "the class is final", check(drawer, List.of(), type(DRAWER, PUBLIC | FINAL, OBJECT, type -> { })));
        refused("a parent that is gone", "no such class", check(drawer, List.of()));
    }

    private static void aGameThatIsNotThereResolvesNothing() {
        Links.Result result = check(MAKE.andThen(INHERITED).andThen(NUMBER).andThen(COUNT).andThen(SET_TOTAL));
        if (result.unresolved().size() != result.references() || result.references() != 5) {
            throw new AssertionError("with no game every one of the five references is refused, got: " + result.unresolved());
        }
    }
}
