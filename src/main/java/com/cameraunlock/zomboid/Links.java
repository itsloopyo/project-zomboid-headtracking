package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.reflect.AccessFlag;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Every class, method and field of the game that the mod's compiled code names, looked up
 * in the game's class files before a patch goes in.
 *
 * The JVM looks a member up only when the instruction that names it first runs, and for
 * this mod that is inside the game's own frame: on a build of the game that renamed or
 * retyped one, the hook would throw from there on every frame. Looked up here first, such
 * a build is one the mod stays out of. Nothing is loaded to do it: the class files are read
 * as bytes, so no class of the game is in the JVM before its patches are registered.
 */
final class Links {
    /** A field, by internal class name, name and descriptor. */
    record Field(String owner, String name, String descriptor) {}

    /** How many distinct things of the game were looked up, and what was said of each that was not found as used. */
    record Result(int references, List<String> unresolved) {}

    private static final String VERTEX_BUFFER = "zombie/core/skinnedmodel/model/VertexBufferObject";
    private static final String VERTEX_BUFFER_HANDLE = VERTEX_BUFFER + "$Vbo";
    /**
     * The fields read by name, which no instruction of the mod's names: the vertex buffer's, the body's picture and its entry,
     * the parts of a posed body, the outlined things, the layer an animation node is in the item renderer's and a door's shut sprite.
     */
    static final Field BUFFER_HANDLE = new Field(VERTEX_BUFFER, "handle", "L" + VERTEX_BUFFER_HANDLE + ";");
    static final Field BUFFER_ELEMENTS = new Field(VERTEX_BUFFER, "elements", "[I");
    static final Field HANDLE_BUFFER = new Field(VERTEX_BUFFER_HANDLE, "vboId", "I");
    static final Field HANDLE_STRIDE = new Field(VERTEX_BUFFER_HANDLE, "vertexStride", "I");
    /** Where a body keeps the game's picture of it, which WorldScene asks the game for. */
    static final Field BODY_PICTURE = new Field("zombie/iso/objects/IsoDeadBody", "atlasTex", "Lzombie/core/skinnedmodel/DeadBodyAtlas$BodyTexture;");
    /** Which of the game's pictures of bodies a body's picture is, which Corpses keeps a body's model by. */
    static final Field BODY_PICTURE_ENTRY = new Field("zombie/core/skinnedmodel/DeadBodyAtlas$BodyTexture", "entry",
            "Lzombie/core/skinnedmodel/DeadBodyAtlas$AtlasEntry;");
    private static final String ANIMATED_MODEL = "zombie/core/skinnedmodel/advancedanimation/AnimatedModel";
    /** The parts a model posed apart from any character holds for each of the game's frames, which Corpses keeps. */
    static final Field POSED_STATES = new Field(ANIMATED_MODEL, "stateInfos", "[L" + ANIMATED_MODEL + "$StateInfo;");
    static final Field POSED_PARTS = new Field(ANIMATED_MODEL + "$StateInfo", "instData", "Ljava/util/ArrayList;");
    /** The things the game outlines, which Outlines reads. */
    static final Field OUTLINED = new Field("zombie/iso/fboRenderChunk/FBORenderObjectOutline", "objectSet", "Ljava/util/HashSet;");
    /** The layer an animation node is in, which Ragdolls reads. */
    static final Field NODE_LAYER = new Field("zombie/core/skinnedmodel/advancedanimation/LiveAnimNode", "animLayer",
            "Lzombie/core/skinnedmodel/advancedanimation/AnimLayer;");
    static final Field BUFFER_FORMAT = new Field(VERTEX_BUFFER, "vertexFormat", "L" + VERTEX_BUFFER + "$VertexFormat;");
    private static final String ITEM_RENDERER = "zombie/core/skinnedmodel/model/ItemModelRenderer";
    /** What the game's renderer of an item on the ground keeps of it, which ItemModels reads. */
    static final Field ITEM_MODEL = new Field(ITEM_RENDERER, "model", "Lzombie/core/skinnedmodel/model/Model;");
    static final Field ITEM_TRANSFORM = new Field(ITEM_RENDERER, "transform", "Lorg/joml/Matrix4f;");
    static final Field ITEM_TINT_R = new Field(ITEM_RENDERER, "tintR", "F");
    static final Field ITEM_TINT_G = new Field(ITEM_RENDERER, "tintG", "F");
    static final Field ITEM_TINT_B = new Field(ITEM_RENDERER, "tintB", "F");
    static final Field ITEM_TINT_MASK = new Field(ITEM_RENDERER, "tintMask", "Ljava/lang/String;");
    /** The sprite a door shows shut, which an open door is read from: see WorldScene.shutSprite. */
    static final Field DOOR_SHUT = new Field("zombie/iso/objects/IsoDoor", "closedSprite", "Lzombie/iso/sprite/IsoSprite;");
    static final Field THUMPABLE_SHUT = new Field("zombie/iso/objects/IsoThumpable", "closedSprite", "Lzombie/iso/sprite/IsoSprite;");
    static final List<Field> REFLECTED = List.of(BUFFER_HANDLE, BUFFER_ELEMENTS, BUFFER_FORMAT, HANDLE_BUFFER, HANDLE_STRIDE, BODY_PICTURE, OUTLINED, NODE_LAYER,
            ITEM_MODEL, ITEM_TRANSFORM, ITEM_TINT_R, ITEM_TINT_G, ITEM_TINT_B, ITEM_TINT_MASK,
            DOOR_SHUT, THUMPABLE_SHUT, BODY_PICTURE_ENTRY, POSED_STATES, POSED_PARTS);

    private final Function<String, byte[]> classBytes;
    private final Map<String, Optional<ClassModel>> models = new HashMap<>();
    private final Set<String> seen = new HashSet<>();
    private final List<String> unresolved = new ArrayList<>();

    /** The classes the mod ships, its own and the libraries in its jar, by internal name: nothing of the game's. */
    private final Set<String> shipped = new HashSet<>();

    private Links(Function<String, byte[]> classBytes) {
        this.classBytes = classBytes;
    }

    /** Looks up what the classes of the jar or folder this class is in name, in the class files the game runs from. */
    static Result check() throws IOException {
        Path source;
        try {
            source = Path.of(Links.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException failure) {
            throw new IllegalStateException(failure);
        }
        return check(classesIn(source), REFLECTED, Links::running);
    }

    /** The class files of a jar, or of a folder and the folders in it. */
    static List<byte[]> classesIn(Path source) throws IOException {
        List<byte[]> ours = new ArrayList<>();
        if (Files.isDirectory(source)) {
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".class"))::iterator) {
                    ours.add(Files.readAllBytes(file));
                }
            }
        } else {
            try (JarFile jar = new JarFile(source.toFile())) {
                for (JarEntry entry : (Iterable<JarEntry>) jar.stream().filter(e -> e.getName().endsWith(".class"))::iterator) {
                    try (InputStream in = jar.getInputStream(entry)) {
                        ours.add(in.readAllBytes());
                    }
                }
            }
        }
        return ours;
    }

    /** The class file of a class the game runs from, by internal name, or null where there is none. */
    static byte[] running(String name) {
        try (InputStream in = Links.class.getClassLoader().getResourceAsStream(name + ".class")) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    /**
     * Why a class given a method of its own could not hand on to the one it inherits by way of
     * {@code parent}, or null where it can: {@code parent} or a class or interface above it has
     * the method with a body, and it is not static, private or final.
     */
    static String notInherited(String parent, String name, String descriptor, Function<String, byte[]> classBytes) {
        ArrayDeque<String> queue = new ArrayDeque<>(List.of(parent));
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            byte[] bytes = visited.add(current) ? classBytes.apply(current) : null;
            if (bytes == null) {
                continue;
            }
            ClassModel model = ClassFile.of().parse(bytes);
            for (MethodModel method : model.methods()) {
                if (method.methodName().equalsString(name) && method.methodType().equalsString(descriptor)) {
                    for (AccessFlag refused : List.of(AccessFlag.STATIC, AccessFlag.PRIVATE, AccessFlag.FINAL, AccessFlag.ABSTRACT)) {
                        if (method.flags().has(refused)) {
                            return current + " has it " + refused.name().toLowerCase(Locale.ROOT);
                        }
                    }
                    return null;
                }
            }
            model.superclass().ifPresent(above -> queue.add(above.asInternalName()));
            model.interfaces().forEach(above -> queue.add(above.asInternalName()));
        }
        return "neither " + parent + " nor anything above it has it";
    }

    /**
     * @param ours       the mod's class files
     * @param reflected  fields the mod reads by name, each declared by the class named
     * @param classBytes the class file of a class by internal name, or null where there is none
     */
    static Result check(Collection<byte[]> ours, List<Field> reflected, Function<String, byte[]> classBytes) {
        Links links = new Links(classBytes);
        List<ClassModel> models = ours.stream().map(ClassFile.of()::parse).toList();
        models.forEach(model -> links.shipped.add(model.thisClass().asInternalName()));
        models.forEach(links::classOfOurs);
        for (Field field : reflected) {
            links.declared(field);
        }
        return new Result(links.seen.size(), List.copyOf(links.unresolved));
    }

    private boolean isTheGames(String owner) {
        return !owner.startsWith("java/") && !owner.startsWith("[") && !shipped.contains(owner);
    }

    private Optional<ClassModel> model(String name) {
        return models.computeIfAbsent(name, wanted -> Optional.ofNullable(classBytes.apply(wanted)).map(ClassFile.of()::parse));
    }

    private void classOfOurs(ClassModel ours) {
        ours.superclass().map(ClassEntry::asInternalName).filter(this::isTheGames).ifPresent(parent -> overrides(ours, parent));
        ours.interfaces().forEach(implemented -> type(implemented.asInternalName()));
        for (MethodModel method : ours.methods()) {
            if (method.code().isEmpty()) {
                continue;
            }
            for (CodeElement element : method.code().get()) {
                switch (element) {
                    case InvokeInstruction invoke -> method(invoke.owner().asInternalName(), invoke.name().stringValue(),
                            invoke.type().stringValue(), invoke.opcode() == Opcode.INVOKESTATIC, invoke.isInterface());
                    case FieldInstruction field -> field(field.owner().asInternalName(), field.name().stringValue(), field.type().stringValue(),
                            field.opcode() == Opcode.GETSTATIC || field.opcode() == Opcode.PUTSTATIC,
                            field.opcode() == Opcode.PUTSTATIC || field.opcode() == Opcode.PUTFIELD);
                    case TypeCheckInstruction cast -> type(cast.type().asInternalName());
                    case NewObjectInstruction made -> type(made.className().asInternalName());
                    case NewReferenceArrayInstruction made -> type(made.componentType().asInternalName());
                    case ConstantInstruction.LoadConstantInstruction constant
                            when constant.constantEntry() instanceof ClassEntry named -> type(named.asInternalName());
                    case InvokeDynamicInstruction dynamic -> {
                        // A lambda or a method reference: the method it stands for is an argument of the call site's.
                        for (ConstantDesc argument : dynamic.bootstrapArgs()) {
                            if (argument instanceof DirectMethodHandleDesc handle) {
                                String descriptor = handle.owner().descriptorString();
                                method(descriptor.substring(1, descriptor.length() - 1), handle.methodName(), handle.lookupDescriptor(),
                                        handle.kind() == DirectMethodHandleDesc.Kind.STATIC
                                                || handle.kind() == DirectMethodHandleDesc.Kind.INTERFACE_STATIC,
                                        handle.isOwnerInterface());
                            }
                        }
                    }
                    default -> { }
                }
            }
        }
    }

    private void fail(String what, String why) {
        unresolved.add(what + ": " + why);
    }

    private void type(String name) {
        if (isTheGames(name) && seen.add("class " + name) && model(name).isEmpty()) {
            fail("class " + name, "no such class");
        }
    }

    /** A class of ours made of one of the game's has every method the game left for it to write, and replaces none it may not. */
    private void overrides(ClassModel ours, String parentName) {
        String what = ours.thisClass().asInternalName() + " extends " + parentName;
        if (!seen.add(what)) {
            return;
        }
        Optional<ClassModel> parent = model(parentName);
        if (parent.isEmpty()) {
            fail(what, "no such class");
            return;
        }
        if (parent.get().flags().has(AccessFlag.FINAL)) {
            fail(what, "the class is final");
        }
        Set<String> written = new HashSet<>();
        ours.methods().forEach(method -> written.add(method.methodName().stringValue() + method.methodType().stringValue()));
        for (MethodModel method : parent.get().methods()) {
            String signature = method.methodName().stringValue() + method.methodType().stringValue();
            if (method.flags().has(AccessFlag.ABSTRACT) && !written.contains(signature)) {
                fail(what, "abstract " + signature + " is not written");
            }
            if (method.flags().has(AccessFlag.FINAL) && written.contains(signature)) {
                fail(what, "final " + signature + " is replaced");
            }
        }
    }

    private void method(String owner, String name, String descriptor, boolean isStatic, boolean ownerIsInterface) {
        String what = owner + "." + name + descriptor;
        if (!isTheGames(owner) || !seen.add((isStatic ? "static " : "") + what)) {
            return;
        }
        Optional<ClassModel> start = model(owner);
        if (start.isEmpty()) {
            fail(what, "no such class");
            return;
        }
        if (start.get().flags().has(AccessFlag.INTERFACE) != ownerIsInterface) {
            fail(what, ownerIsInterface ? "the class is no longer an interface" : "the class is now an interface");
            return;
        }
        if (!start.get().flags().has(AccessFlag.PUBLIC)) {
            fail(what, "the class is not public");
            return;
        }
        ArrayDeque<String> queue = new ArrayDeque<>(List.of(owner));
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            Optional<ClassModel> model = model(current);
            if (model.isEmpty()) {
                fail(what, "no class " + current + " above it");
                return;
            }
            for (MethodModel candidate : model.get().methods()) {
                if (candidate.methodName().equalsString(name) && candidate.methodType().equalsString(descriptor)) {
                    if (candidate.flags().has(AccessFlag.STATIC) != isStatic) {
                        fail(what, isStatic ? "the method is no longer static" : "the method is now static");
                    } else if (!candidate.flags().has(AccessFlag.PUBLIC) && !candidate.flags().has(AccessFlag.PROTECTED)) {
                        fail(what, "the method is not public");
                    }
                    return;
                }
            }
            // A constructor is the named class's own or it is nothing.
            if (name.equals("<init>")) {
                break;
            }
            model.get().superclass().ifPresent(parent -> queue.add(parent.asInternalName()));
            model.get().interfaces().forEach(parent -> queue.add(parent.asInternalName()));
        }
        fail(what, "no such method");
    }

    private void field(String owner, String name, String descriptor, boolean isStatic, boolean written) {
        String what = owner + "." + name + ":" + descriptor;
        if (!isTheGames(owner) || !seen.add((isStatic ? "static " : "") + (written ? "written " : "") + what)) {
            return;
        }
        ArrayDeque<String> queue = new ArrayDeque<>(List.of(owner));
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!visited.add(current)) {
                continue;
            }
            Optional<ClassModel> model = model(current);
            if (model.isEmpty()) {
                fail(what, current.equals(owner) ? "no such class" : "no class " + current + " above it");
                return;
            }
            for (FieldModel candidate : model.get().fields()) {
                if (candidate.fieldName().equalsString(name) && candidate.fieldType().equalsString(descriptor)) {
                    if (candidate.flags().has(AccessFlag.STATIC) != isStatic) {
                        fail(what, isStatic ? "the field is no longer static" : "the field is now static");
                    } else if (!candidate.flags().has(AccessFlag.PUBLIC) && !candidate.flags().has(AccessFlag.PROTECTED)) {
                        fail(what, "the field is not public");
                    } else if (written && candidate.flags().has(AccessFlag.FINAL)) {
                        fail(what, "the field is written and is now final");
                    }
                    return;
                }
            }
            model.get().interfaces().forEach(parent -> queue.add(parent.asInternalName()));
            model.get().superclass().ifPresent(parent -> queue.add(parent.asInternalName()));
        }
        fail(what, "no such field");
    }

    /** A field read by name off one object: the class named declares it, of that type, and it is not static. */
    private void declared(Field field) {
        String what = field.owner() + "." + field.name() + ":" + field.descriptor();
        if (!seen.add("declared " + what)) {
            return;
        }
        Optional<ClassModel> model = model(field.owner());
        if (model.isEmpty()) {
            fail(what, "no such class");
            return;
        }
        for (FieldModel candidate : model.get().fields()) {
            if (candidate.fieldName().equalsString(field.name())) {
                if (!candidate.fieldType().equalsString(field.descriptor())) {
                    fail(what, "the field is now " + candidate.fieldType().stringValue());
                } else if (candidate.flags().has(AccessFlag.STATIC)) {
                    fail(what, "the field is now static");
                }
                return;
            }
        }
        fail(what, "no such field");
    }
}
