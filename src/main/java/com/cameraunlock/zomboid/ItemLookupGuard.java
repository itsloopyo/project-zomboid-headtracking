package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.ConstantDynamicEntry;
import java.lang.classfile.constantpool.DoubleEntry;
import java.lang.classfile.constantpool.FloatEntry;
import java.lang.classfile.constantpool.IntegerEntry;
import java.lang.classfile.constantpool.LoadableConstantEntry;
import java.lang.classfile.constantpool.LongEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodHandleEntry;
import java.lang.classfile.constantpool.MethodTypeEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.StringEntry;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.IncrementInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LabelTarget;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDesc;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Reads every class as the JVM defines it, before any of its code can run, for code that could
 * change what ScriptManager.FindItem finds without telling GameFixes, or make the lookup depend on
 * something else. What a name finds rests on a few maps and fields of five classes; the methods
 * that use them, and the methods a lookup runs, were read on one build of the game and are listed
 * in {@link #CHECKED} with a digest of their code. A method not on that list that uses those
 * maps or fields, a listed method whose code is not the code that was read, or a class that
 * extends one of the five and was not read, stops GameFixes keeping items for good, with the
 * reason in the log. So a new build of the game is held to the list, and so is a Java mod's code
 * where it names those members in its instructions or its constant pool.
 *
 * <p>What it does not see: a member reached by its name as a string, through reflection
 * (getDeclaredField, Field.set), MethodHandles.Lookup's find methods or a VarHandle; Unsafe and
 * JNI; a hidden class, which Lookup.defineHiddenClass defines without any transformer seeing it;
 * a class that names the five only as "zombie.scripting." strings for Class.forName; and a
 * transformer added after the mod's, which changes classes after they are read here.
 */
final class ItemLookupGuard implements ClassFileTransformer {
    static final String MANAGER = "zombie/scripting/ScriptManager";
    static final String MODULE = "zombie/scripting/objects/ScriptModule";
    static final String BUCKET = "zombie/scripting/ScriptBucket";
    static final String COLLECTION = "zombie/scripting/ScriptBucketCollection";
    static final String TYPE = "zombie/scripting/ScriptType";

    /** The fields whose every use counts, by the class they are declared in. */
    private static final Map<String, Set<String>> USED = Map.of(
            MANAGER, Set.of("moduleMap", "moduleList", "cachedModules", "moduleAliases"),
            MODULE, Set.of("imports", "disabled"),
            BUCKET, Set.of("scriptMap", "dotInName"),
            COLLECTION, Set.of("fullTypeToScriptMap", "map"),
            TYPE, Set.of("flags"));
    /** The fields only whose writes count: the item buckets a lookup starts from, which the game reads everywhere. */
    private static final Map<String, Set<String>> WRITTEN = Map.of(MANAGER, Set.of("items"), MODULE, Set.of("items"));
    /**
     * The methods that hand out a bucket's maps, and those that change what a name finds without
     * telling GameFixes: they fill or empty buckets and collections, add modules or read a
     * module's imports. On the build that was read the last three are private and called only from
     * methods that tell GameFixes, which can be called from anywhere.
     */
    private static final Map<String, Set<String>> CALLED = Map.of(
            BUCKET, Set.of("getScriptMap", "LoadScripts", "reset", "CreateFromTokenPP"),
            COLLECTION, Set.of("getFullTypeToScriptMap", "LoadScripts", "reset", "registerModule"),
            MANAGER, Set.of("CreateFromToken", "loadScripts"),
            MODULE, Set.of("CreateFromTokenPP"));

    private static final byte[] SCRIPTING = "zombie/scripting/".getBytes(StandardCharsets.ISO_8859_1);

    /**
     * The methods of the game's Steam build 25485521 that use the maps and fields above or call
     * the methods above, every method a lookup runs from FindItem(String), every method of a
     * subclass that overrides one of those, and the constructors of the item buckets with those
     * they call, which choose the maps a lookup asks: by owner, name and descriptor, each with the
     * digest of its code that {@link #digest} gives. A lookup's calls on a bucket reach only the
     * item buckets, as only the constructors put a bucket in the items fields, and they are listed.
     */
    static final Map<String, Long> CHECKED = Map.ofEntries(
            Map.entry("zombie/Lua/LuaManager$GlobalObject.cloneItemType(Ljava/lang/String;Ljava/lang/String;)Lzombie/scripting/objects/Item;", 0x6671cab1a8f67536L),
            Map.entry("zombie/Lua/LuaManager$GlobalObject.createNewScriptItem(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Lzombie/scripting/objects/Item;", 0xab0ba7dc2c124f31L),
            Map.entry("zombie/SoundManager.debugScriptSounds()V", 0x2cb52285c73e3097L),
            Map.entry("zombie/inventory/RecipeManager.resolveItemModuleDotType(Lzombie/scripting/objects/Recipe;Ljava/lang/String;Ljava/util/Set;Ljava/lang/String;)Lzombie/scripting/objects/Item;", 0x8aaab506abd77aa9L),
            Map.entry("zombie/scripting/ScriptBucket$Template.CreateFromTokenPP(Lzombie/scripting/ScriptLoadMode;Ljava/lang/String;Ljava/lang/String;)Z", 0x315ab6704bab799fL),
            Map.entry("zombie/scripting/ScriptBucket$Template.LoadScripts(Lzombie/scripting/ScriptLoadMode;)V", 0x08b1a523a3928df0L),
            Map.entry("zombie/scripting/ScriptBucket.<init>(Lzombie/scripting/objects/ScriptModule;Lzombie/scripting/ScriptType;)V", 0x544475030c1f1dbbL),
            Map.entry("zombie/scripting/ScriptBucket.<init>(Lzombie/scripting/objects/ScriptModule;Lzombie/scripting/ScriptType;Ljava/util/Map;)V", 0xa52bde931841fbb7L),
            Map.entry("zombie/scripting/ScriptBucket.LoadScripts(Lzombie/scripting/ScriptLoadMode;)V", 0xa7f3689c7272beb3L),
            Map.entry("zombie/scripting/ScriptBucket.get(Ljava/lang/String;)Lzombie/scripting/objects/BaseScriptObject;", 0xdf0fe7f81907810eL),
            Map.entry("zombie/scripting/ScriptBucket.getScriptMap()Ljava/util/Map;", 0x28e97ad6d8c2131fL),
            Map.entry("zombie/scripting/ScriptBucket.reset()V", 0xdaf812fe27011fc3L),
            Map.entry("zombie/scripting/ScriptBucketCollection.<init>(Lzombie/scripting/ScriptManager;Lzombie/scripting/ScriptType;)V", 0x8c8a663b0f75edc6L),
            Map.entry("zombie/scripting/ScriptBucketCollection.LoadScripts(Lzombie/scripting/ScriptLoadMode;)V", 0x60dfdeaa4dcd46f4L),
            Map.entry("zombie/scripting/ScriptBucketCollection.getAllScripts()Ljava/util/ArrayList;", 0x523ecc1a8658c524L),
            Map.entry("zombie/scripting/ScriptBucketCollection.getFullType(Ljava/lang/String;)Lzombie/scripting/objects/BaseScriptObject;", 0x4d3a6e4ad130ccc5L),
            Map.entry("zombie/scripting/ScriptBucketCollection.getFullTypeToScriptMap()Ljava/util/HashMap;", 0x48fceaf04e9a87cbL),
            Map.entry("zombie/scripting/ScriptBucketCollection.getScript(Ljava/lang/String;)Lzombie/scripting/objects/BaseScriptObject;", 0x716b4981f882a3b7L),
            Map.entry("zombie/scripting/ScriptBucketCollection.hasFullType(Ljava/lang/String;)Z", 0x24d591814d9cb080L),
            Map.entry("zombie/scripting/ScriptBucketCollection.registerModule(Lzombie/scripting/objects/ScriptModule;)V", 0x569dbf1286fbc188L),
            Map.entry("zombie/scripting/ScriptBucketCollection.reset()V", 0x5cdb9105e24c3290L),
            Map.entry("zombie/scripting/ScriptManager$3.<init>(Lzombie/scripting/ScriptManager;Lzombie/scripting/ScriptManager;Lzombie/scripting/ScriptType;)V", 0x5fc6dd7816a1ee1bL),
            Map.entry("zombie/scripting/ScriptManager$3.LoadScripts(Lzombie/scripting/ScriptLoadMode;)V", 0xeee075d2b29acf3aL),
            Map.entry("zombie/scripting/ScriptManager.<init>()V", 0x339c8fa1f443cd0fL),
            Map.entry("zombie/scripting/ScriptManager.CheckExitPoints()V", 0xe100f0cc30e525fbL),
            Map.entry("zombie/scripting/ScriptManager.CreateFromToken(Lzombie/scripting/ScriptLoadMode;Ljava/lang/String;)V", 0x339bd344a6b770c2L),
            Map.entry("zombie/scripting/ScriptManager.FindItem(Ljava/lang/String;)Lzombie/scripting/objects/Item;", 0x74c3af6d0454a8e9L),
            Map.entry("zombie/scripting/ScriptManager.FindItem(Ljava/lang/String;Z)Lzombie/scripting/objects/Item;", 0xd2369cf7b3370779L),
            Map.entry("zombie/scripting/ScriptManager.Load()V", 0xb990600ee37ead94L),
            Map.entry("zombie/scripting/ScriptManager.ParseScript(Lzombie/scripting/ScriptLoadMode;Ljava/lang/String;)V", 0x57c08245f35303e3L),
            Map.entry("zombie/scripting/ScriptManager.ReloadScripts(Ljava/util/EnumSet;)V", 0xa950055036d92136L),
            Map.entry("zombie/scripting/ScriptManager.Reset()V", 0x1b83a1e3c10eb309L),
            Map.entry("zombie/scripting/ScriptManager.addModelScript(Lzombie/scripting/objects/ModelScript;)V", 0x50d6f293bdbfb6eeL),
            Map.entry("zombie/scripting/ScriptManager.addSpriteModel(Lzombie/iso/SpriteModel;)V", 0xc2f84d2c75a35a91L),
            Map.entry("zombie/scripting/ScriptManager.createZedDmgMap()V", 0x7be32b678b40a193L),
            Map.entry("zombie/scripting/ScriptManager.getItem(Ljava/lang/String;)Lzombie/scripting/objects/Item;", 0x0088fa43fa874881L),
            Map.entry("zombie/scripting/ScriptManager.getItemName(Ljava/lang/String;)Ljava/lang/String;", 0xd5f4c341925a0c4fL),
            Map.entry("zombie/scripting/ScriptManager.getItemsByType(Ljava/lang/String;)Ljava/util/ArrayList;", 0xc085b52fc07bd10eL),
            Map.entry("zombie/scripting/ScriptManager.getModule(Ljava/lang/String;)Lzombie/scripting/objects/ScriptModule;", 0xc46055ccdfa5b1aeL),
            Map.entry("zombie/scripting/ScriptManager.getModule(Ljava/lang/String;Z)Lzombie/scripting/objects/ScriptModule;", 0xed8ec36052fe69beL),
            Map.entry("zombie/scripting/ScriptManager.getModuleNoDisableCheck(Ljava/lang/String;)Lzombie/scripting/objects/ScriptModule;", 0x8f73a7a5dce8f757L),
            Map.entry("zombie/scripting/ScriptManager.loadScripts(Lzombie/scripting/ScriptLoadMode;Ljava/util/EnumSet;)V", 0x548527bcfcec801cL),
            Map.entry("zombie/scripting/ScriptManager.resolveItemType(Lzombie/scripting/objects/ScriptModule;Ljava/lang/String;)Ljava/lang/String;", 0x311862d6c98bb93bL),
            Map.entry("zombie/scripting/ScriptManager.resolveModelScript(Lzombie/scripting/objects/ScriptModule;Ljava/lang/String;)Ljava/lang/String;", 0xb269abbadb0746a6L),
            Map.entry("zombie/scripting/ScriptType.<clinit>()V", 0x4965768372730054L),
            Map.entry("zombie/scripting/ScriptType.hasFlag(Lzombie/scripting/ScriptType$Flags;)Z", 0xd7ec85b39f531ca8L),
            Map.entry("zombie/scripting/ScriptType.hasFlags(Ljava/util/EnumSet;)Z", 0xcaf1bc538b15332fL),
            Map.entry("zombie/scripting/objects/ScriptModule$3.<init>(Lzombie/scripting/objects/ScriptModule;Lzombie/scripting/objects/ScriptModule;Lzombie/scripting/ScriptType;)V", 0x404a7e79f1611558L),
            Map.entry("zombie/scripting/objects/ScriptModule$3.getFromManager(Ljava/lang/String;)Lzombie/scripting/objects/BaseScriptObject;", 0x0eaded791074c384L),
            Map.entry("zombie/scripting/objects/ScriptModule$3.getFromManager(Ljava/lang/String;)Lzombie/scripting/objects/Item;", 0x06a08d5716c60840L),
            Map.entry("zombie/scripting/objects/ScriptModule$3.getFromModule(Ljava/lang/String;Lzombie/scripting/objects/ScriptModule;)Lzombie/scripting/objects/BaseScriptObject;", 0x34c1081b8613a78cL),
            Map.entry("zombie/scripting/objects/ScriptModule$3.getFromModule(Ljava/lang/String;Lzombie/scripting/objects/ScriptModule;)Lzombie/scripting/objects/Item;", 0xf1806a7371790f0fL),
            Map.entry("zombie/scripting/objects/ScriptModule.<init>()V", 0xdd8dce0f17995e45L),
            Map.entry("zombie/scripting/objects/ScriptModule.CreateFromTokenPP(Lzombie/scripting/ScriptLoadMode;Ljava/lang/String;)V", 0x3bfbfd4d6bf7d476L),
            Map.entry("zombie/scripting/objects/ScriptModule.ParseScriptPP(Lzombie/scripting/ScriptLoadMode;Ljava/lang/String;)V", 0xce99363ad3e52164L),
            Map.entry("zombie/scripting/objects/ScriptModule.Reset()V", 0xb63fe37ab435a910L),
            Map.entry("zombie/scripting/objects/ScriptModule.getItem(Ljava/lang/String;)Lzombie/scripting/objects/Item;", 0xa55cc75e664b90d1L)
    );

    /** The classes of that build that come from one of the five, each with the class it extends. */
    static final Map<String, String> SUBCLASSES = Map.ofEntries(
            Map.entry("zombie/scripting/ScriptBucket$Template", BUCKET),
            Map.entry("zombie/scripting/ScriptManager$1", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$10", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$11", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$12", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$13", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$14", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$15", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$16", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$17", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$18", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$19", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$2", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$20", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$21", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$22", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$23", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$24", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$25", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$26", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$27", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$28", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$29", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$3", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$30", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$31", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$32", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$33", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$34", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$35", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$36", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$37", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$4", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$5", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$6", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$7", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$8", COLLECTION),
            Map.entry("zombie/scripting/ScriptManager$9", COLLECTION),
            Map.entry("zombie/scripting/objects/ScriptModule$1", "zombie/scripting/ScriptBucket$Template"),
            Map.entry("zombie/scripting/objects/ScriptModule$10", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$11", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$12", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$13", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$14", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$15", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$16", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$17", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$18", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$19", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$2", "zombie/scripting/ScriptBucket$Template"),
            Map.entry("zombie/scripting/objects/ScriptModule$20", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$21", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$22", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$23", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$24", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$25", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$26", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$27", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$28", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$29", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$3", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$30", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$31", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$32", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$33", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$34", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$35", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$36", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$37", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$4", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$5", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$6", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$7", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$8", BUCKET),
            Map.entry("zombie/scripting/objects/ScriptModule$9", BUCKET)
    );

    /** The name and descriptor of each listed method, by its class. */
    private static final Map<String, Set<String>> CHECKED_BY_CLASS = CHECKED.keySet().stream().collect(Collectors.groupingBy(
            key -> key.substring(0, key.indexOf('.')), Collectors.mapping(key -> key.substring(key.indexOf('.') + 1), Collectors.toUnmodifiableSet())));

    /**
     * Each class known to come from one of the five, with the one it comes from. The listed
     * subclasses are in it from the start, since code naming one can be defined before it is.
     */
    private final Map<String, String> family = new ConcurrentHashMap<>();
    /**
     * The class each class of a family extends. The listed subclasses are in it from the start
     * too, as the JVM hands a class to its transformers before the class it extends.
     */
    private final Map<String, String> parents = new ConcurrentHashMap<>(SUBCLASSES);
    private final BiConsumer<String, Throwable> stop;
    private final Consumer<String> startedLate;
    private volatile boolean stopped;
    private final AtomicLong defined = new AtomicLong(), read = new AtomicLong(), nanos = new AtomicLong();

    /**
     * {@code stop} is handed why GameFixes must stop keeping items, with what was thrown where
     * something was; {@code startedLate} why it must when the mod started after the game had
     * loaded a class it could not read as the game runs it. Either may be called more than once,
     * by threads defining classes at the same time.
     */
    ItemLookupGuard(BiConsumer<String, Throwable> stop, Consumer<String> startedLate) {
        this.stop = stop;
        this.startedLate = startedLate;
        USED.keySet().forEach(each -> family.put(each, each));
        for (String name : SUBCLASSES.keySet()) {
            String base = name;
            while (!USED.containsKey(base)) {
                base = SUBCLASSES.get(base);
            }
            family.put(name, base);
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> redefined, ProtectionDomain domain, byte[] classBytes) {
        if (stopped) {
            return null;
        }
        long began = System.nanoTime();
        defined.incrementAndGet();
        // The JVM discards whatever a transformer throws, Errors as well, so a class that cannot
        // be read is a reason to stop here, or the class would go unread.
        try {
            String why = check(classBytes);
            if (why != null) {
                stopFor(why, null);
            }
        } catch (Throwable failure) {
            stopFor(className + " could not be read for what it does to the scripts", failure);
        } finally {
            nanos.addAndGet(System.nanoTime() - began);
        }
        return null;
    }

    /**
     * Reads, the way transform would have, the classes the JVM had loaded before the guard was
     * there: through the loader that defined each, from the file it was defined from. A class
     * whose methods are listed, or that comes from one of the five, cannot be vouched for that
     * way: a change could have begun in it before its patch went in, or another agent may have
     * changed it as it loaded. What stops the keeping here is told to {@code startedLate}.
     */
    void loadedBefore(Class<?>[] classes) {
        List<Class<?>> toRead = new ArrayList<>();
        for (Class<?> loaded : classes) {
            ClassLoader loader = loaded.getClassLoader();
            if (loader == null || loader == ClassLoader.getPlatformClassLoader() || loaded.isArray() || loaded.isHidden()
                    || loaded.getName().startsWith("com.cameraunlock.")) {
                continue;
            }
            String name = loaded.getName().replace('.', '/');
            if (CHECKED_BY_CLASS.containsKey(name) || family.containsKey(name)) {
                startedLateFor(name + " was loaded before the mod started, so its code as the game runs it could not be read");
                return;
            }
            toRead.add(loaded);
        }
        for (Class<?> loaded : toRead) {
            String name = loaded.getName().replace('.', '/');
            try (InputStream in = loaded.getClassLoader().getResourceAsStream(name + ".class")) {
                if (in == null) {
                    startedLateFor(name + " was loaded before the mod started, from no class file its loader hands out, so what it does to the scripts could not be read");
                    return;
                }
                String why = check(in.readAllBytes());
                if (why != null) {
                    startedLateFor(why);
                    return;
                }
            } catch (IOException | RuntimeException failure) {
                stopFor(name + " was loaded before the mod started and its class file could not be read", failure);
                return;
            }
        }
    }

    /** How much reading the classes has cost so far, for the log. */
    String cost() {
        return "ItemLookupGuard has read " + read.get() + " of the " + defined.get() + " classes defined since the mod started, in " + nanos.get() / 1_000_000 + " ms";
    }

    /**
     * Nothing more is read once the stop has been told, and not before, so a stop that throws
     * leaves the guard reading. No lock: a class loaded while the stop is told, on this thread or
     * another, is read and may tell it again.
     */
    private void stopFor(String why, Throwable cause) {
        stop.accept(why, cause);
        stopped = true;
    }

    private void startedLateFor(String why) {
        startedLate.accept(why);
        stopped = true;
    }

    /** Why the class makes kept items unsafe, or null if it does not. */
    String check(byte[] classBytes) {
        if (!contains(classBytes, SCRIPTING)) {
            return null;
        }
        read.incrementAndGet();
        ClassModel model = ClassFile.of().parse(classBytes);
        String name = model.thisClass().asInternalName();
        String parent = model.superclass().map(ClassEntry::asInternalName).orElse(null);
        String base = parent == null ? null : family.get(parent);
        if (base != null) {
            family.put(name, base);
            parents.put(name, parent);
            if (!parent.equals(SUBCLASSES.get(name))) {
                return name + " extends " + parent + " and was not checked";
            }
        }
        if (!CHECKED_BY_CLASS.containsKey(name) && base == null && !mentions(model)) {
            return null;
        }
        Set<String> overridden = new HashSet<>();
        for (String up = base == null ? null : parent; up != null; up = parents.get(up)) {
            overridden.addAll(CHECKED_BY_CLASS.getOrDefault(up, Set.of()));
        }
        for (MethodModel method : model.methods()) {
            String signature = method.methodName().stringValue() + method.methodType().stringValue();
            String key = name + "." + signature;
            Long checked = CHECKED.get(key);
            if (checked != null) {
                if (digest(method) != checked) {
                    return key + " is not the code that was checked";
                }
            } else if (overridden.contains(signature) && method.code().isPresent() && !method.methodName().equalsString("<init>")) {
                return key + " takes the place of a method ScriptManager.FindItem's answer rests on and was not checked";
            } else if (uses(method, family)) {
                return key + " uses what ScriptManager.FindItem reads and was not checked";
            }
        }
        return null;
    }

    /** Whether the class's constant pool names a member {@link #uses} would count. */
    private boolean mentions(ClassModel model) {
        for (PoolEntry entry : model.constantPool()) {
            MemberRefEntry ref = entry instanceof MethodHandleEntry handle ? handle.reference() : entry instanceof MemberRefEntry member ? member : null;
            if (ref != null) {
                String base = family.get(ref.owner().asInternalName());
                String member = ref.name().stringValue();
                if (base != null && (named(USED, base, member) || named(WRITTEN, base, member) || named(CALLED, base, member))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether the method uses a map or field FindItem's answer rests on, by the classes {@code family} knows. */
    static boolean uses(MethodModel method, Map<String, String> family) {
        if (method.code().isEmpty()) {
            return false;
        }
        for (CodeElement element : method.code().get()) {
            switch (element) {
                case FieldInstruction field -> {
                    String base = family.get(field.owner().asInternalName());
                    String member = field.name().stringValue();
                    boolean writes = field.opcode() == Opcode.PUTFIELD || field.opcode() == Opcode.PUTSTATIC;
                    if (base != null && (named(USED, base, member) || writes && named(WRITTEN, base, member))) {
                        return true;
                    }
                }
                case InvokeInstruction call -> {
                    String base = family.get(call.owner().asInternalName());
                    if (base != null && named(CALLED, base, call.name().stringValue())) {
                        return true;
                    }
                }
                case InvokeDynamicInstruction dynamic -> {
                    if (handles(dynamic.bootstrapMethod(), family) || dynamic.bootstrapArgs().stream().anyMatch(arg -> handles(arg, family))) {
                        return true;
                    }
                }
                case ConstantInstruction constant -> {
                    if (handles(constant.constantValue(), family)) {
                        return true;
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    /** Whether a constant is a handle to, or a dynamic constant made from, a member {@link #uses} would count. */
    private static boolean handles(ConstantDesc constant, Map<String, String> family) {
        if (constant instanceof DirectMethodHandleDesc handle) {
            String base = family.get(internalName(handle.owner()));
            String member = handle.methodName();
            return base != null && switch (handle.kind()) {
                case GETTER, STATIC_GETTER -> named(USED, base, member);
                case SETTER, STATIC_SETTER -> named(USED, base, member) || named(WRITTEN, base, member);
                default -> named(CALLED, base, member);
            };
        }
        if (constant instanceof DynamicConstantDesc<?> dynamic) {
            return handles(dynamic.bootstrapMethod(), family) || dynamic.bootstrapArgsList().stream().anyMatch(arg -> handles(arg, family));
        }
        return false;
    }

    private static boolean named(Map<String, Set<String>> members, String base, String member) {
        Set<String> names = members.get(base);
        return names != null && names.contains(member);
    }

    private static String internalName(ClassDesc type) {
        String descriptor = type.descriptorString();
        return descriptor.startsWith("L") ? descriptor.substring(1, descriptor.length() - 1) : descriptor;
    }

    /**
     * A digest of what a method's code does: each instruction with its operands by name and value
     * rather than by constant pool index, each jump by the instruction it goes to, and each
     * handler of its exception table. Line numbers, local variable names and stack maps are left
     * out, and ldc_w, goto_w and jsr_w are read as ldc, goto and jsr, which a compiler writes in
     * their place for a constant far down the pool or a jump far off, so the same code in another
     * build of the class, laid out otherwise, has the same digest. Zero for a method with no code.
     */
    static long digest(MethodModel method) {
        if (method.code().isEmpty()) {
            return 0;
        }
        CodeModel code = method.code().get();
        Map<Label, Integer> at = new IdentityHashMap<>();
        int instructions = 0;
        for (CodeElement element : code) {
            if (element instanceof LabelTarget target) {
                at.put(target.label(), instructions);
            } else if (element instanceof Instruction) {
                instructions++;
            }
        }
        StringBuilder text = new StringBuilder();
        for (CodeElement element : code) {
            if (element instanceof Instruction instruction) {
                Opcode opcode = switch (instruction.opcode()) {
                    case LDC_W -> Opcode.LDC;
                    case GOTO_W -> Opcode.GOTO;
                    case JSR_W -> Opcode.JSR;
                    default -> instruction.opcode();
                };
                text.append(opcode.name());
                operands(text, instruction, at);
                text.append('\n');
            }
        }
        for (ExceptionCatch handler : code.exceptionHandlers()) {
            text.append("catch ").append(at.get(handler.tryStart())).append(' ').append(at.get(handler.tryEnd())).append(' ')
                    .append(at.get(handler.handler())).append(' ').append(handler.catchType().map(ClassEntry::asInternalName).orElse("*")).append('\n');
        }
        try {
            byte[] sha = MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8));
            long digest = 0;
            for (int i = 0; i < 8; i++) {
                digest = digest << 8 | (sha[i] & 0xff);
            }
            return digest;
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("every JVM has SHA-256", missing);
        }
    }

    private static void operands(StringBuilder text, Instruction instruction, Map<Label, Integer> at) {
        switch (instruction) {
            case LoadInstruction load -> text.append(' ').append(load.slot());
            case StoreInstruction store -> text.append(' ').append(store.slot());
            case IncrementInstruction increment -> text.append(' ').append(increment.slot()).append(' ').append(increment.constant());
            case BranchInstruction branch -> text.append(' ').append(at.get(branch.target()));
            case LookupSwitchInstruction lookup -> {
                text.append(' ').append(at.get(lookup.defaultTarget()));
                for (SwitchCase each : lookup.cases()) {
                    text.append(' ').append(each.caseValue()).append(':').append(at.get(each.target()));
                }
            }
            case TableSwitchInstruction table -> {
                text.append(' ').append(table.lowValue()).append(' ').append(table.highValue()).append(' ').append(at.get(table.defaultTarget()));
                for (SwitchCase each : table.cases()) {
                    text.append(' ').append(each.caseValue()).append(':').append(at.get(each.target()));
                }
            }
            case FieldInstruction field -> member(text, field.field());
            case InvokeInstruction call -> {
                member(text, call.method());
                text.append(call.isInterface() ? " interface" : "");
            }
            case InvokeDynamicInstruction dynamic -> {
                text.append(' ').append(dynamic.name().stringValue()).append(' ').append(dynamic.type().stringValue());
                constant(text, dynamic.bootstrapMethod());
                dynamic.bootstrapArgs().forEach(arg -> constant(text, arg));
            }
            case NewObjectInstruction made -> text.append(' ').append(made.className().asInternalName());
            case NewPrimitiveArrayInstruction array -> text.append(' ').append(array.typeKind().name());
            case NewReferenceArrayInstruction array -> text.append(' ').append(array.componentType().asInternalName());
            case NewMultiArrayInstruction array -> text.append(' ').append(array.arrayType().asInternalName()).append(' ').append(array.dimensions());
            case TypeCheckInstruction check -> text.append(' ').append(check.type().asInternalName());
            case ConstantInstruction.ArgumentConstantInstruction argument -> text.append(' ').append(argument.constantValue());
            case ConstantInstruction.LoadConstantInstruction loaded -> entry(text, loaded.constantEntry());
            case DiscontinuedInstruction.JsrInstruction jsr -> text.append(' ').append(at.get(jsr.target()));
            case DiscontinuedInstruction.RetInstruction ret -> text.append(' ').append(ret.slot());
            default -> {
            }
        }
    }

    private static void member(StringBuilder text, MemberRefEntry member) {
        text.append(' ').append(member.owner().asInternalName()).append('.').append(member.name().stringValue()).append(member.type().stringValue());
    }

    private static void entry(StringBuilder text, LoadableConstantEntry entry) {
        switch (entry) {
            case IntegerEntry value -> text.append(" int ").append(value.intValue());
            case FloatEntry value -> text.append(" float ").append(Float.floatToRawIntBits(value.floatValue()));
            case LongEntry value -> text.append(" long ").append(value.longValue());
            case DoubleEntry value -> text.append(" double ").append(Double.doubleToRawLongBits(value.doubleValue()));
            case StringEntry value -> text.append(" string ").append(value.stringValue().length()).append(':').append(value.stringValue());
            case ClassEntry value -> text.append(" class ").append(value.asInternalName());
            case MethodTypeEntry value -> text.append(" type ").append(value.descriptor().stringValue());
            case MethodHandleEntry value -> constant(text, value.asSymbol());
            case ConstantDynamicEntry value -> constant(text, value.asSymbol());
        }
    }

    private static void constant(StringBuilder text, ConstantDesc constant) {
        switch (constant) {
            case DirectMethodHandleDesc handle -> text.append(" handle ").append(handle.kind().name()).append(' ').append(internalName(handle.owner()))
                    .append('.').append(handle.methodName()).append(handle.lookupDescriptor());
            case DynamicConstantDesc<?> dynamic -> {
                text.append(" dynamic ").append(dynamic.constantName()).append(' ').append(dynamic.constantType().descriptorString());
                constant(text, dynamic.bootstrapMethod());
                dynamic.bootstrapArgsList().forEach(arg -> constant(text, arg));
            }
            case ClassDesc type -> text.append(" class ").append(type.descriptorString());
            case MethodTypeDesc type -> text.append(" type ").append(type.descriptorString());
            case String value -> text.append(" string ").append(value.length()).append(':').append(value);
            case Float value -> text.append(" float ").append(Float.floatToRawIntBits(value));
            case Double value -> text.append(" double ").append(Double.doubleToRawLongBits(value));
            default -> text.append(' ').append(constant.getClass().getSimpleName()).append(' ').append(constant);
        }
    }

    private static boolean contains(byte[] bytes, byte[] wanted) {
        byte first = wanted[0];
        for (int i = 0, last = bytes.length - wanted.length; i <= last; i++) {
            if (bytes[i] != first) {
                continue;
            }
            int j = 1;
            while (j < wanted.length && bytes[i + j] == wanted[j]) {
                j++;
            }
            if (j == wanted.length) {
                return true;
            }
        }
        return false;
    }
}
