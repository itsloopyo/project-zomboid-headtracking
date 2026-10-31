package com.cameraunlock.zomboid;

import com.cameraunlock.core.agent.Boot;
import java.io.IOException;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class Agent {
    private static final String DISPLAY = "org/lwjglx/opengl/Display";
    private static final String GLFW = "org/lwjgl/glfw/GLFW";
    private static final String WINDOW_PLACEMENT = "com/cameraunlock/zomboid/WindowPlacement";
    private static final String ATLAS = "zombie/core/skinnedmodel/DeadBodyAtlas", BODY_PARAMS = ATLAS + "$BodyParams", RENDER_JOB = ATLAS + "$RenderJob";
    private static final String ANIMATED_MODEL = "zombie/core/skinnedmodel/advancedanimation/AnimatedModel";
    private static final String HOOKS = "com/cameraunlock/zomboid/Hooks";
    private static final String NOT_RUNNING = "com/cameraunlock/zomboid/NotRunning";
    private static final String HIGHLIGHT = "zombie/iso/fboRenderChunk/FBORenderObjectHighlight";
    private static final String GAME_MOUSE = "zombie/input/Mouse";
    private static final String TORCH = "zombie/characters/IsoGameCharacter$TorchInfo";
    private static final String EXCEPTION_LOGGER = "zombie/core/logger/ExceptionLogger";
    private static final String RETICLE = "zombie/input/AimingReticle";
    private static final String CHARACTER_DRAW = "zombie/core/skinnedmodel/model/ModelSlotRenderData";
    private static final String PART_DRAW = "zombie/core/skinnedmodel/model/ModelInstanceRenderData";
    private static final String MODEL = "zombie/core/skinnedmodel/model/Model";
    private static final String COMBINER = "zombie/core/textures/TextureCombiner";
    private static final String TEXTURE = "Lzombie/core/textures/Texture;";
    private static final String FINE_SKINS = "com/cameraunlock/zomboid/FineSkins";
    private static final String PLAYER = "zombie/characters/IsoPlayer";
    private static final String UI = "zombie/ui/UIManager";
    private static final String RAGDOLL = "zombie/core/physics/RagdollController";
    private static final String LIVE_NODE = "zombie/core/skinnedmodel/advancedanimation/LiveAnimNode";
    private static final String KEY_BINDING = "Lzombie/core/Core$KeyBinding;";
    private static final String INT_FILTER = "(I)I";
    private static final String BOOLEAN_FILTER = "(Z)Z";
    private static final String VECTOR2_FILTER = "(Lzombie/iso/Vector2;)Lzombie/iso/Vector2;";
    static final String GAME_DIRECTORY_PROPERTY = "cameraunlock.zomboid.gameDirectory";
    /** A Defaults.ini of a test's own in place of the player's, which the game never sets. */
    static final String DEFAULTS_PROPERTY = "cameraunlock.zomboid.defaults";

    /**
     * A rewrite of one method of one game class. One that adds fields or methods to the class
     * cannot be applied to a class the game has already loaded: see retransformLoaded.
     */
    record Patch(String owner, String what, UnaryOperator<byte[]> rewrite, boolean addsMembers) {
        Patch(String owner, String what, UnaryOperator<byte[]> rewrite) {
            this(owner, what, rewrite, false);
        }
    }

    /** The patches the first person view is made of. */
    private static PatchTransformer firstPerson;
    private static volatile boolean firstPersonAsked;
    private static volatile String firstPersonGap;

    private Agent() {}

    /**
     * Why the first person view cannot run on this build of the game, or null
     * once every patch it is made of is in the game's code. With only some of
     * them in, the view would draw while the keys, the mouse or the reticle
     * still worked the game's own way.
     */
    static String firstPersonGap() {
        // Asked several times a frame, so the first answer is kept, though it can change later: see PatchTransformer.gap.
        if (!firstPersonAsked) {
            firstPersonGap = firstPerson.gap();
            firstPersonAsked = true;
        }
        return firstPersonGap;
    }

    /** The patches that tell FineSkins what the game's texture combiner composes and takes back. */
    private static PatchTransformer fineSkins;

    /** Why finer skins cannot be made on this build of the game, or null once both of their patches are in. */
    static String fineSkinsGap() {
        return fineSkins.gap();
    }

    /** The patch that tells ViewModel a firearm has been fired. */
    private static PatchTransformer shot;

    /** Why the held weapon is not told of its shots on this build of the game, or null once the patch is in. */
    static String shotGap() {
        return shot.gap();
    }

    /** The patch that tells Lightning of a strike. */
    private static PatchTransformer thunder;

    /** Why no lightning is drawn on this build of the game, or null once the patch is in. */
    static String thunderGap() {
        return thunder.gap();
    }

    /** The patch that has the game's vehicle shaders write their lit lamps for the picture. */
    private static PatchTransformer lamps;

    /** Why vehicle lamps are not drawn lit on this build of the game, or null once the patch is in. */
    static String lampsGap() {
        return lamps.gap();
    }

    /** Whether TalkingMouths was on as the game started, which decides whether the game's own people are drawn able to open their mouths. */
    private static volatile boolean mouthsAtStart;

    static boolean mouthsAtStart() {
        return mouthsAtStart;
    }

    /** The patches that tell ImpactFinder where each shot's tracer ends. */
    private static PatchTransformer impacts;

    /** Why shots leave no mark where they strike on this build of the game, or null once both patches are in. */
    static String impactsGap() {
        return impacts.gap();
    }

    /** The patch that has ModelHead turn the character's head. */
    private static PatchTransformer modelHead;

    /** Why the character's head is not turned on this build of the game, or null once the patch is in. */
    static String modelHeadGap() {
        return modelHead.gap();
    }

    /** The patches that put the names over other players where those players are in the first person picture. */
    private static PatchTransformer nameTags;

    /** Why names are not moved into the first person picture on this build of the game, or null once the patches are in. */
    static String nameTagsGap() {
        return nameTags.gap();
    }

    /** The patches that tell ChunkMarks what the game marks as changed. */
    private static PatchTransformer chunkMarks;

    /** Why the game does not say which chunks it has changed on this build of it, or null once it does. */
    static String chunkMarksGap() {
        return chunkMarks.gap();
    }

    /** The patches that tell ChunkMarks of what the game changes without marking it. */
    private static PatchTransformer windowMarks;

    /** Why a window smashed or opened is not told of at once on this build of the game, or null once it is. */
    static String windowMarksGap() {
        return windowMarks.gap();
    }

    /** The patches that tell ChunkMarks of the rest of what the game changes without marking it. */
    private static PatchTransformer unmarkedChanges;

    /** Which of the changes the game marks nothing for are not told of on this build of it, each by its patch, or null once all are. */
    static String unmarkedChangesGap() {
        return unmarkedChanges.gaps();
    }

    /** The patches that have Hearing say which way the game's listener faces and how far out its camera is. */
    private static PatchTransformer hearing;

    /** Why first person is heard as the game's own view is on this build of the game, or null once both patches are in. */
    static String hearingGap() {
        return hearing.gap();
    }

    /** The patches that lay two players' parts of the screen out one above the other. */
    private static PatchTransformer splitScreen;

    /** Why two players stay side by side on this build of the game, or null once every patch of the other layout is in. */
    static String splitScreenGap() {
        return splitScreen.gap();
    }

    /** The patches that let GameFixes keep what the game's item lookups find. */
    private static PatchTransformer itemLookups;
    private static ItemLookupGuard itemLookupGuard;

    /**
     * Why a change to the game's scripts could go by without telling GameFixes, which leaves every
     * item looked up as the game does: a patch missing from the game's code. Null when none is.
     * A class of theirs loaded before the mod started, in which a change could have begun before
     * its patch went in, is ItemLookupGuard's to find.
     */
    static String itemLookupsGap() {
        return itemLookups.gap();
    }

    /** How much ItemLookupGuard's reading of the classes has cost so far, for the log. */
    static String itemLookupGuardCost() {
        return itemLookupGuard.cost();
    }

    /**
     * Adds ItemLookupGuard, then the patches of the item lookups, both able to retransform, ahead
     * of every other transformer of the mod's, so the guard reads the game's classes as the game
     * has them; then has the guard read the classes loaded before it was added. A patch of the
     * lookups that fails, as its class loads or is transformed again, stops the keeping for good
     * before the class is given its new code. No other patch of the mod's is in a method these
     * patch, so they go in as they would later in the order.
     */
    static void addItemLookupTransformers(Instrumentation instrumentation) {
        itemLookupGuard = new ItemLookupGuard(GameFixes::stopKeepingItems, GameFixes::keepNoItemsAfterALateStart);
        instrumentation.addTransformer(itemLookupGuard, true);
        itemLookups = new PatchTransformer(itemLookupPatches(),
                what -> GameFixes.stopKeepingItems("the patch of " + what + " failed, see the error above", null));
        add(instrumentation, itemLookups);
        itemLookupGuard.loadedBefore(instrumentation.getAllLoadedClasses());
    }

    /** The patches that have WaitingComposites make the clothes' composites while the render thread waits for the frame. */
    private static PatchTransformer waitingComposites;

    /** Why the composites are made in the draw on this build of the game, or null once every one of those patches is in. */
    static String waitingCompositesGap() {
        return waitingComposites.gap();
    }

    /** The patch that leaves the spotting of rooms and the marking of squares seen to Spotting while the first person picture covers the player's view. */
    private static PatchTransformer spotting;

    /** Why the game's light still spots rooms and marks squares under the first person picture on this build of the game, or null once it does not. */
    static String spottingGap() {
        return spotting.gap();
    }

    private static volatile boolean loaded;

    private static final List<PatchTransformer> transformers = new ArrayList<>();

    /** What the mod was started with, which takeIn puts patches into the game's code with. */
    private static Instrumentation started;

    /** The sets this start left out of the game's code, each until the parts and rules it serves are on: see takeIn. */
    private static final List<PatchSet> waiting = new ArrayList<>();

    /** Puts the main menu's line saying why the mod is not running into the game's code, and nothing else. */
    private static void sayNotRunning(Instrumentation instrumentation, String why) {
        NotRunning.say(why);
        String menu = "zombie/gameStates/MainScreenState";
        PatchTransformer transformer = new PatchTransformer(List.of(after(new CallSite.Method(menu, "render", "()V"),
                new CallSite.Method("zombie/modding/ActiveMods", "renderUI", "()V"), new CallSite.Method(NOT_RUNNING, "draw", "()V"))));
        add(instrumentation, transformer);
        retransform(instrumentation, instrumentation.getAllLoadedClasses(), transformer.owners());
    }

    private static void add(Instrumentation instrumentation, PatchTransformer transformer) {
        // Only a transformer added as able to retransform sees the classes retransformLoaded asks for again.
        instrumentation.addTransformer(transformer, true);
        transformers.add(transformer);
    }

    /**
     * Has the JVM run every loaded class a patch is for through the transformers again, one class
     * at a time so one the JVM refuses leaves the others patched. A retransformation may change
     * method bodies only, so PatchTransformer leaves out each patch that adds members.
     */
    private static void retransformLoaded(Instrumentation instrumentation, Class<?>[] before) {
        Set<String> owners = new HashSet<>();
        transformers.forEach(transformer -> owners.addAll(transformer.owners()));
        int[] done = retransform(instrumentation, before, owners);
        Log.info("patched again " + done[0] + " classes the game had loaded before the mod started" + refusedSaid(done));
    }

    /** How many classes a retransform refused, as the log says it after how many it patched: nothing where none was. */
    private static String refusedSaid(int[] done) {
        return done[1] == 0 ? "" : ", " + done[1] + " refused";
    }

    /** Has the JVM run each of the loaded classes the owners name through the transformers again, and answers how many it did and how many it refused. */
    private static int[] retransform(Instrumentation instrumentation, Class<?>[] loadedClasses, Set<String> owners) {
        int done = 0, refused = 0;
        for (Class<?> loadedClass : loadedClasses) {
            String name = loadedClass.getName().replace('.', '/');
            if (!owners.contains(name)) {
                continue;
            }
            try {
                instrumentation.retransformClasses(loadedClass);
                done++;
            } catch (java.lang.instrument.UnmodifiableClassException | UnsupportedOperationException | LinkageError refusal) {
                transformers.forEach(transformer -> transformer.forget(name));
                Log.error("the JVM would not patch " + name + " again, its methods run unchanged", refusal);
                refused++;
            }
        }
        return new int[] {done, refused};
    }

    /**
     * Puts into the game's code each set this start left out whose parts and rules are on now, so
     * a part switched on while the game runs works at once: its transformer is added, and the
     * classes it patches that the game has loaded are patched again. A patch that adds members to
     * a class already loaded stays out until the next start. Then each part taken while the game
     * runs that is on is marked as in the game's code. A part switched off keeps its patches until
     * the next start, which leaves them out. Called once the file or the options screen has set the
     * parts and rules, on the thread that set them.
     */
    static synchronized void takeIn() {
        List<PatchSet> in = new ArrayList<>();
        for (PatchSet set : waiting) {
            if (set.leftOut().get() == null) {
                in.add(set);
            }
        }
        Part.takenIn();
        if (in.isEmpty()) {
            return;
        }
        Set<String> owners = new HashSet<>();
        StringJoiner names = new StringJoiner(", ");
        for (PatchSet set : in) {
            add(started, set.transformer());
            owners.addAll(set.transformer().owners());
            names.add(set.name());
        }
        int[] done = retransform(started, started.getAllLoadedClasses(), owners);
        for (PatchSet set : in) {
            set.transformer().leaveOut(null);
        }
        waiting.removeAll(in);
        // FirstPerson asks for the first person set's gap every frame and is handed the first answer.
        firstPersonAsked = false;
        Log.info("put into the game's code now, as what they serve was switched on: " + names + "; patched again " + done[0]
                + " classes the game had loaded" + refusedSaid(done));
    }

    /** Called where the game has just logged an exception of any kind. */
    public static void caughtByTheGame(Throwable thrown) {
        Log.caughtByTheGame(thrown);
    }

    /** Called by the JVM when cameraunlock-core's Boot loads the agent into it, which is how an installed copy starts. */
    public static void agentmain(String arguments, Instrumentation instrumentation) throws IOException {
        premain(arguments, instrumentation);
    }

    /** Called by the JVM for -javaagent, which is how the development run starts. */
    public static void premain(String arguments, Instrumentation instrumentation) throws IOException {
        Boot.agentStarted();
        catchUp(instrumentation);
    }

    /**
     * Starts the mod and patches again the classes its patches are for that are already loaded.
     * Started with the game there are usually none, but another agent in the JVM can load game
     * classes first: ZombieBuddy's loads LuaManager, whose patch puts the mod's settings on the
     * options screen. Says whether the patches are registered.
     */
    private static boolean catchUp(Instrumentation instrumentation) throws IOException {
        // Listed before any transformer is added: a class that loads after this is patched as it
        // loads, so only these need patching again, and only these may not be given new members.
        Class<?>[] before = instrumentation.getAllLoadedClasses();
        if (!begin(instrumentation)) {
            return false;
        }
        retransformLoaded(instrumentation, before);
        return true;
    }

    /**
     * Called by the Workshop package's entry class, which ZombieBuddy runs while the game loads its
     * mods. The game's window and many of its classes exist by then, so the classes the patches are
     * for that have already loaded are patched again, and the new window is centred here.
     */
    public static void startLate(Instrumentation instrumentation) throws IOException {
        if (loaded) {
            // Another copy of the mod started with the JVM: this class is that copy's, which
            // has said in its log whether it is running.
            Log.info("ZombieBuddy started the Workshop copy, and a copy of the mod started with the game is already in it: the Workshop copy stays out");
            return;
        }
        if (catchUp(instrumentation)) {
            if (Part.CENTRE_THE_WINDOW.atStart()) {
                WindowPlacement.centreNewWindow();
            }
            if (Part.CONTROLLER_CHOICE_FIX.atStart() && org.lwjglx.input.Controllers.isCreated()) {
                Gamepads.found();
            }
        }
    }

    /**
     * The jar's Implementation-Version, read from the jar: ZombieBuddy's class loader defines the
     * package with no manifest, so the package has no version there. Classes run from a folder
     * have no jar to say it.
     */
    private static String version(Path source) throws IOException {
        if (java.nio.file.Files.isDirectory(source)) return "(classes in " + source + ")";
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(source.toFile())) {
            return jar.getManifest().getMainAttributes().getValue(java.util.jar.Attributes.Name.IMPLEMENTATION_VERSION);
        }
    }

    /** The jar the mod's classes were loaded from, or the folder of classes the offline harness runs. */
    private static Path source() {
        try {
            return Path.of(Agent.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (java.net.URISyntaxException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /**
     * Throws where an art jar the code jar's Class-Path names is not beside it. The JVM leaves a
     * missing one off the class path without a word, and every picture and model in it would then
     * read as absent.
     */
    private static void requireArtJars(Path jar) throws IOException {
        String classPath;
        try (java.util.jar.JarFile file = new java.util.jar.JarFile(jar.toFile())) {
            classPath = file.getManifest().getMainAttributes().getValue(java.util.jar.Attributes.Name.CLASS_PATH);
        }
        if (classPath == null) throw new IllegalStateException(jar + " has no Class-Path naming its art jars");
        List<String> missing = new ArrayList<>();
        for (String name : classPath.trim().split("\\s+")) {
            if (!java.nio.file.Files.isRegularFile(jar.resolveSibling(name))) missing.add(name);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("the art jars " + String.join(", ", missing) + " are not beside " + jar + ": install the mod again");
        }
    }

    /** Starts the mod, and says whether its patches are now registered. */
    private static boolean begin(Instrumentation instrumentation) throws IOException {
        if (loaded) {
            // The development run starts an installed copy with -javaagent, and Boot then loads the agent again.
            Log.info("the agent was started a second time: the first start stands");
            return false;
        }
        // The offline harness runs the game's classes in the game's folder and keeps the mod's log
        // and CameraUnlock.ini in a folder of its own, named by this property. The game never sets it.
        Path gameDirectory = Path.of(System.getProperty(GAME_DIRECTORY_PROPERTY, "")).toAbsolutePath();
        Log.open(gameDirectory);
        // Only once there is a log: a second start after a first that could not open one has nowhere to say so.
        loaded = true;
        Path source = source();
        Log.info("Project Zomboid Head Tracking " + version(source) + " loaded, Java " + Runtime.version() + ", game directory " + gameDirectory);
        try {
            if (!java.nio.file.Files.isDirectory(source)) requireArtJars(source);
            return start(gameDirectory, instrumentation);
        } catch (IOException | RuntimeException | Error failure) {
            // Boot asks only whether the agent started and runs the game either way, and
            // nothing else puts the reason in this log: it would end here without one.
            Log.error("the mod could not start", failure);
            sayNotRunning(instrumentation, "it hit an error as it started");
            throw failure;
        }
    }

    private static boolean start(Path gameDirectory, Instrumentation instrumentation) throws IOException {
        Links.Result links = Links.check();
        if (!links.unresolved().isEmpty()) {
            links.unresolved().forEach(missing -> Log.info("not in this build of the game as the mod uses it: " + missing));
            Log.info("the mod stays out of the game: this build lacks " + links.unresolved().size() + " of the "
                    + links.references() + " things of the game's the mod uses");
            sayNotRunning(instrumentation, "it does not work with this version of the game");
            return false;
        }
        Log.info("all " + links.references() + " things of the game's the mod uses are in this build");

        Tracking.start(gameDirectory);
        try {
            Map<String, StringJoiner> leftOut = new LinkedHashMap<>();
            String lookupsOff = off(Part.CACHED_GAME_LOOKUPS);
            if (lookupsOff == null) {
                addItemLookupTransformers(instrumentation);
            } else {
                leftOut.computeIfAbsent(lookupsOff, why -> new StringJoiner(", ")).add("item lookups");
            }
            started = instrumentation;
            for (PatchSet set : sets()) {
                String why = set.leftOut().get();
                if (why == null) {
                    add(instrumentation, set.transformer());
                } else {
                    set.transformer().leaveOut(why);
                    waiting.add(set);
                    leftOut.computeIfAbsent(why, reason -> new StringJoiner(", ")).add(set.name());
                }
            }
            Log.info("the mod's " + transformers.size() + " sets of patches are registered, each to go in as the game loads its class");
            leftOut.forEach((why, sets) -> Log.info("left out of the game's code until what they serve is switched on, as " + why + ": " + sets));
            return true;
        } catch (RuntimeException | Error failure) {
            // begin says the mod could not start: what it registered stops patching the classes still to load, and its port and watch are let go.
            if (itemLookupGuard != null) {
                instrumentation.removeTransformer(itemLookupGuard);
            }
            transformers.forEach(instrumentation::removeTransformer);
            transformers.clear();
            waiting.clear();
            Tracking.stop();
            throw failure;
        }
    }

    /** A set of patches, its name in the log, and why it is out of the game's code as the parts and rules are now, or null when it goes in. */
    record PatchSet(String name, PatchTransformer transformer, Supplier<String> leftOut) {}

    /**
     * The mod's sets of patches after the item lookups, in the order they go in, each with why it
     * is left out as the parts and rules of CameraUnlock.ini were when the game started: asked
     * once Tracking.start has read the file. Gives each set whose gap the mod asks its field.
     */
    static List<PatchSet> sets() {
        Supplier<String> firstPersonOff = offNow(Part.FIRST_PERSON);
        firstPerson = new PatchTransformer(firstPersonPatches());
        fineSkins = new PatchTransformer(fineSkinPatches());
        shot = new PatchTransformer(shotPatches());
        thunder = new PatchTransformer(thunderPatches());
        lamps = new PatchTransformer(lampPatches());
        impacts = new PatchTransformer(impactPatches());
        modelHead = new PatchTransformer(modelHeadPatches());
        nameTags = new PatchTransformer(nameTagPatches());
        chunkMarks = new PatchTransformer(chunkMarkPatches());
        windowMarks = new PatchTransformer(windowMarkPatches());
        unmarkedChanges = new PatchTransformer(unmarkedChangePatches());
        hearing = new PatchTransformer(hearingPatches());
        splitScreen = new PatchTransformer(splitScreenPatches());
        waitingComposites = new PatchTransformer(waitingCompositePatches());
        spotting = new PatchTransformer(spottingPatches());
        // A shot breaks lamps and glass, a blast holes doors and fences and a round what it goes through, where ImpactFinder finds it ends.
        Supplier<String> impactsOff = () -> (Rules.rows() & (Rules.BREAKS_LAMPS | Rules.BREAKS_GLASS | Rules.BLAST_HOLES | Rules.BULLET_HOLES)) != 0 ? null
                : also(off(Part.FIRST_PERSON, Part.SHOT_IMPACTS), "were ShotsBreakLamps, ShotsBreakGlass, BlastHoles and BulletHoles");
        // First person's aim counts a blast's pellets through the same patches (Hooks.hitThrough).
        Supplier<String> throughWallsOff = () -> (Rules.rows() & Rules.THROUGH_WALLS) != 0 ? null
                : also(off(Part.FIRST_PERSON), "was ShotsThroughWalls");
        // The shaders and the part draw serve first person's picture and the mouths of the people
        // the game draws, and the game reads its shaders as it starts, so these follow the start alone.
        mouthsAtStart = Mouths.on();
        String mouthsOffAtStart = mouthsAtStart || Part.FIRST_PERSON.atStart() ? null
                : Part.FIRST_PERSON.key + " was off as the game started, and so was TalkingMouths";
        Supplier<String> mouthsOff = () -> mouthsOffAtStart;
        // Blood trails and flying pieces come only from first person's wounds; the hidden walls and the rewind have rows of their own.
        Supplier<String> worldOff = () -> (Rules.rows() & (Rules.HIDDEN_WALLS | Rules.REWIND)) != 0 ? null
                : also(off(Part.FIRST_PERSON), DevBuild.ON ? "were HiddenWallDecor and Rewind30s" : "was HiddenWallDecor");
        Supplier<String> splitScreenOff = () -> SplitScreen.row() != SplitScreen.VERTICAL ? null : "SplitScreen is Vertical, the game's own";
        Supplier<String> in = () -> null;
        return List.of(
                new PatchSet("base", new PatchTransformer(basePatches()), in),
                new PatchSet("window", new PatchTransformer(windowPatches()), offNow(Part.CENTRE_THE_WINDOW)),
                new PatchSet("torch", new PatchTransformer(torchPatches()), firstPersonOff),
                new PatchSet("first person", firstPerson, firstPersonOff),
                new PatchSet("ragdolls", new PatchTransformer(ragdollPatches()), offNow(Part.FIRST_PERSON, Part.QUICK_RAGDOLLS)),
                new PatchSet("item models", new PatchTransformer(itemModelPatches()), offNow(Part.FIRST_PERSON, Part.ITEMS_AS_MODELS)),
                new PatchSet("frame", new PatchTransformer(framePatches()), in),
                new PatchSet("shots through walls", new PatchTransformer(throughWallPatches()), throughWallsOff),
                new PatchSet("world", new PatchTransformer(worldPatches()), worldOff),
                new PatchSet("development", new PatchTransformer(DevPatches.list()), in),
                new PatchSet("fine skins", fineSkins, offNow(Part.FIRST_PERSON, Part.FINER_SKINS)),
                new PatchSet("shot", shot, firstPersonOff),
                new PatchSet("thunder", thunder, offNow(Part.FIRST_PERSON, Part.LIGHTNING_BOLTS)),
                new PatchSet("lamps", lamps, mouthsOff),
                new PatchSet("impacts", impacts, impactsOff),
                new PatchSet("model head", modelHead, in),
                new PatchSet("name tags", nameTags, offNow(Part.FIRST_PERSON, Part.NAME_TAGS_AT_HEADS)),
                new PatchSet("cached lookups", new PatchTransformer(gameFixPatches()), offNow(Part.CACHED_GAME_LOOKUPS)),
                new PatchSet("hang fix", new PatchTransformer(hangFixPatches()), offNow(Part.BURNT_BUILDING_FREEZE_FIX)),
                new PatchSet("covered", new PatchTransformer(coveredPatches()), offNow(Part.FIRST_PERSON, Part.SKIP_COVERED_GAME_DRAWING)),
                new PatchSet("part draw", new PatchTransformer(partDrawPatches()), mouthsOff),
                new PatchSet("screen grey", new PatchTransformer(screenGreyPatches()), offNow(Part.FIRST_PERSON, Part.LEAVE_OUT_SCREEN_GREY)),
                new PatchSet("far vehicles", new PatchTransformer(farVehiclePatches()), firstPersonOff),
                new PatchSet("live sight", new PatchTransformer(liveSightPatches()), ruleOff(Rules.SEEN_AT_ONCE, "ZombiesSeenAtOnce")),
                new PatchSet("left save", new PatchTransformer(leftSavePatches()), in),
                new PatchSet("loading", new PatchTransformer(loadingPatches()), offNow(Part.FIRST_PERSON, Part.PREPARE_WHILE_LOADING)),
                new PatchSet("lua loaded", new PatchTransformer(luaLoadedPatches()), in),
                new PatchSet("knockdown", new PatchTransformer(knockdownPatches()), ruleOff(Rules.SMASHES_THROUGH, "VehiclesSmashThrough")),
                new PatchSet("car physics", new PatchTransformer(carPhysicsPatches()), offNow(Part.FIRST_PERSON, Part.HILLS_MOVE_VEHICLES)),
                new PatchSet("controller", new PatchTransformer(controllerPatches()), offNow(Part.CONTROLLER_CHOICE_FIX)),
                new PatchSet("chunk marks", chunkMarks, offNow(Part.FIRST_PERSON, Part.CHUNK_CHANGE_COUNTS)),
                new PatchSet("window marks", windowMarks, offNow(Part.FIRST_PERSON, Part.CHUNK_CHANGE_COUNTS)),
                new PatchSet("unmarked changes", unmarkedChanges, offNow(Part.FIRST_PERSON, Part.CHUNK_CHANGE_COUNTS)),
                new PatchSet("hushed marks", new PatchTransformer(hushedMarkPatches()), offNow(Part.FIRST_PERSON, Part.CHUNK_CHANGE_COUNTS)),
                new PatchSet("hearing", hearing, offNow(Part.FIRST_PERSON, Part.HEAR_FROM_THE_EYE)),
                new PatchSet("chunk centre", new PatchTransformer(chunkCentrePatches()), offNow(Part.HOLD_CHUNK_MAP_CENTRE)),
                new PatchSet("split screen", splitScreen, splitScreenOff),
                new PatchSet("waiting composites", waitingComposites, offNow(Part.FIRST_PERSON, Part.SKINS_COMPOSED_WHILE_WAITING)),
                new PatchSet("spotting", spotting, offNow(Part.FIRST_PERSON, Part.SKIP_COVERED_GAME_DRAWING, Part.SPOT_BY_SIGHT)),
                new PatchSet("viewpoint", new PatchTransformer(viewpointPatches()), () -> Tracking.enabled() ? null : "head tracking is off"));
    }

    /** Why a set that serves one row of [Rules] alone is out of the game's code: that row off, or null. */
    private static Supplier<String> ruleOff(int bit, String key) {
        return () -> (Rules.rows() & bit) != 0 ? null : key + " is off";
    }

    /**
     * Why a set that serves only these parts, each needing the ones before it, is out of the game's
     * code: the first whose patches are not in and cannot go in now, or null. A part taken while the
     * game runs can go in once it is on; any other only at a start with it on.
     */
    private static String off(Part... parts) {
        for (Part part : parts) {
            if (!part.patched() && !(part.live && part.on())) {
                return part.key + (part.live ? " is off" : " was off as the game started");
            }
        }
        return null;
    }

    private static Supplier<String> offNow(Part... parts) {
        return () -> off(parts);
    }

    /** Why a set is out that also serves a row that is off: the reason, and the row, or null. */
    private static String also(String off, String row) {
        return off == null ? null : off + ", and so " + row;
    }

    /**
     * The patch that puts the head's turn into the camera of Project Viewpoint, another mod, as
     * its ThirdPerson.capture returns: see ViewpointCamera. Its class loads only with that mod in use.
     */
    private static List<Patch> viewpointPatches() {
        return List.of(new Patch("viewpoint/camera/ThirdPerson", "capture with the head's turn added to its camera shot",
                classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method("viewpoint/camera/ThirdPerson", "capture",
                                "(Lviewpoint/core/Frame;Lzombie/iso/IsoCell;Lzombie/characters/IsoGameCharacter;F)V"),
                        code -> code.aload(0).aload(2).invokestatic(ClassDesc.ofInternalName(HOOKS), "viewpointShot",
                                MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;Lzombie/characters/IsoGameCharacter;)V")))));
    }

    /** The patches for the window's place: see WindowPlacement. */
    private static List<Patch> windowPatches() {
        return List.of(
                after(new CallSite.Method(DISPLAY, "create", "()V"),
                        new CallSite.Method(GLFW, "glfwSetWindowPos", "(JII)V"),
                        new CallSite.Method(WINDOW_PLACEMENT, "centreNewWindow", "()V")),
                after(new CallSite.Method(DISPLAY, "setDisplayModeAndFullscreenInternal", "(Lorg/lwjglx/opengl/DisplayMode;Z)V"),
                        new CallSite.Method(GLFW, "glfwSetWindowMonitor", "(JJIIIII)V"),
                        new CallSite.Method(WINDOW_PLACEMENT, "centreAfterModeChange", "()V")),
                after(new CallSite.Method(DISPLAY, "setBorderlessWindow", "(Z)V"),
                        new CallSite.Method(GLFW, "glfwSetWindowAttrib", "(JII)V"),
                        new CallSite.Method(WINDOW_PLACEMENT, "centreAfterModeChange", "()V")));
    }

    /** The patch for the log. */
    private static List<Patch> basePatches() {
        return List.of(
                // The one method every exception the game catches or lets a thread end on is logged by.
                new Patch(EXCEPTION_LOGGER, "logException before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(EXCEPTION_LOGGER, "logException",
                                "(Ljava/lang/Throwable;Ljava/lang/String;Ljava/lang/String;Lzombie/debug/DebugType;Lzombie/debug/LogSeverity;)V"),
                        code -> CallSite.passFirstStaticArgument(code, new CallSite.Method("com/cameraunlock/zomboid/Agent", "caughtByTheGame",
                                "(Ljava/lang/Throwable;)V")))));
    }

    /** The way a carried torch shines, which in first person is where the view faces and the head turns: see Hooks.lightDirection. */
    private static List<Patch> torchPatches() {
        return List.of(new Patch(TORCH, "set after getLookVector", classBytes -> CallSite.insertAfterEach(classBytes,
                new CallSite.Method(TORCH, "set", "(L" + PLAYER + ";Lzombie/inventory/InventoryItem;)L" + TORCH + ";"),
                new CallSite.Method(PLAYER, "getLookVector", VECTOR2_FILTER),
                code -> CallSite.passFirstArgument(code, new CallSite.Method(HOOKS, "lightDirection",
                        "(Lzombie/iso/Vector2;L" + PLAYER + ";)Lzombie/iso/Vector2;")), 1)));
    }

    private static List<Patch> firstPersonPatches() {
        CallSite.Method mouseUpdate = new CallSite.Method(GAME_MOUSE, "update", "()V");
        CallSite.Method aiming = new CallSite.Method(PLAYER, "isAiming", "()Z");
        CallSite.Method clickAiming = new CallSite.Method(HOOKS, "aimingForWorldClicks", BOOLEAN_FILTER);
        String input = "zombie/characters/component/CharacterInputComponent", inputFilter = "(ZL" + input + ";)Z";
        String byPlayer = "(Lzombie/iso/Vector2;L" + PLAYER + ";)Lzombie/iso/Vector2;";
        CallSite.Method ballistics = new CallSite.Method("zombie/core/physics/BallisticsController", "update", "()V");
        String character = "Lzombie/characters/IsoGameCharacter;", zombie = "zombie/characters/IsoZombie";
        CallSite.Method processHit = new CallSite.Method("zombie/CombatManager", "processHit",
                "(Lzombie/inventory/types/HandWeapon;" + character + character + ")I");
        String vector3 = "Lzombie/iso/Vector3;";
        CallSite.Method muzzleAt = new CallSite.Method(ballistics.owner(), "calculateMuzzlePosition", "(" + vector3 + vector3 + ")F");
        CallSite.Method muzzle = new CallSite.Method(HOOKS, "muzzle", "(L" + ballistics.owner() + ";" + vector3 + vector3 + ")F");
        return List.of(
                new Patch(PLAYER, "getInputMoveVector after getInputMoveVector", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method(PLAYER, "getInputMoveVector", VECTOR2_FILTER),
                        new CallSite.Method("zombie/characters/IsoLivingCharacter", "getInputMoveVector", VECTOR2_FILTER),
                        code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "moveVector", byPlayer)), 1)),
                new Patch("org/lwjglx/opengl/Display", "updateMouseCursor left alone while the look has the mouse",
                        classBytes -> CallSite.returnAtOnceIf(classBytes, new CallSite.Method("org/lwjglx/opengl/Display", "updateMouseCursor", "()V"),
                                new CallSite.Method(HOOKS, "holdsMouse", "()Z"))),
                new Patch("zombie/iso/LightingJNI", "updatePlayer after getLookVector", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method("zombie/iso/LightingJNI", "updatePlayer", "(I)V"),
                        new CallSite.Method(PLAYER, "getLookVector", VECTOR2_FILTER),
                        code -> CallSite.passFirstStaticInt(code, new CallSite.Method(HOOKS, "sightVector", "(Lzombie/iso/Vector2;I)Lzombie/iso/Vector2;")), 1)),
                new Patch("zombie/characters/IsoGameCharacter", "render after showPassenger", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method("zombie/characters/IsoGameCharacter", "render", "(FFFLzombie/core/textures/ColorInfo;ZZLzombie/core/opengl/Shader;)V"),
                        new CallSite.Method("zombie/vehicles/BaseVehicle", "showPassenger", "(Lzombie/characters/IsoGameCharacter;)Z"),
                        code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "showPassenger", "(ZLzombie/characters/IsoGameCharacter;)Z")), 1)),
                new Patch(PLAYER, "updateWhileInVehicle after isHittable", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method(PLAYER, "updateWhileInVehicle", "()V"),
                        new CallSite.Method("zombie/vehicles/VehicleWindow", "isHittable", "()Z"),
                        code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "seatGlassInTheWay", "(ZLzombie/characters/IsoPlayer;)Z")), 1)),
                new Patch(PLAYER, "updateWhileInVehicle after isAiming", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method(PLAYER, "updateWhileInVehicle", "()V"),
                        aiming,
                        code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "aimingLetsGoOfTheWheel", "(ZLzombie/characters/IsoPlayer;)Z")), 1)),
                new Patch("zombie/characters/IsoGameCharacter", "doDeferredMovement after getDeferredMovement", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method("zombie/characters/IsoGameCharacter", "doDeferredMovement", "()V"),
                        new CallSite.Method("zombie/characters/IsoGameCharacter", "getDeferredMovement", "(Lzombie/iso/Vector2;Z)Lzombie/iso/Vector2;"),
                        code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "moved",
                                "(Lzombie/iso/Vector2;Lzombie/characters/IsoGameCharacter;)Lzombie/iso/Vector2;")), 1)),
                // A jog or sprint aside held in the aiming stance tires as the jog or sprint does: see Hooks.tiresJogging.
                new Patch(PLAYER, "updateEndurance after isRunning", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method(PLAYER, "updateEndurance", "()V"),
                        new CallSite.Method(PLAYER, "isRunning", "()Z"),
                        code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "tiresJogging", "(ZLzombie/characters/IsoPlayer;)Z")), 2)),
                new Patch(PLAYER, "updateEndurance after isSprinting", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method(PLAYER, "updateEndurance", "()V"),
                        new CallSite.Method(PLAYER, "isSprinting", "()Z"),
                        code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "tiresSprinting", "(ZLzombie/characters/IsoPlayer;)Z")), 3)),
                new Patch(CHARACTER_DRAW, "render before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(CHARACTER_DRAW, "render", "()V"),
                        code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "drawn",
                                "(L" + CHARACTER_DRAW + ";)V")))),
                reticle("zombie/iso/sprite/IsoReticle", "render", "()V", "getXA", "reticleX"),
                reticle("zombie/iso/sprite/IsoReticle", "render", "()V", "getYA", "reticleY"),
                reticle("zombie/iso/sprite/IsoCursor", "render", "(I)V", "getXA", "reticleX"),
                reticle("zombie/iso/sprite/IsoCursor", "render", "(I)V", "getYA", "reticleY"),
                returningWithThis(input, "isAimKeyDownInternal", "()Z", "aimKey", inputFilter),
                returningWithThis(input, "isAttackButtonDown", "()Z", "attackKey", inputFilter),
                returningWithThis(input, "isInteractButtonDownInternal", "()Z", "interactKey", inputFilter),
                returningWithThis(input, "isMeleeButtonDownInternal", "()Z", "meleeKey", inputFilter),
                returning(UI, "checkPicked", "()Z", "picked", BOOLEAN_FILTER),
                // Where a drag cursor is put: see DragAim.
                new Patch("zombie/iso/IsoCell", "doBuildingInternal after getPickedTile", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method("zombie/iso/IsoCell", "doBuildingInternal", "(IZ)Z"),
                        new CallSite.Method(UI, "getPickedTile", "()Lzombie/iso/Vector2;"), code -> {
                            code.iload(1);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "dragTile", "(Lzombie/iso/Vector2;I)Lzombie/iso/Vector2;"));
                        }, 3)),
                screenToIso("screenToIsoX"),
                screenToIso("screenToIsoY"),
                // OnMouseDown, OnMouseUp, OnRightMouseDown and OnRightMouseUp.
                new Patch(UI, "updateMouseButtons telling the Lua of buttons", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(UI, "updateMouseButtons", "(II)I"),
                        new CallSite.Method("zombie/Lua/LuaEventManager", "triggerEvent", "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"),
                        new CallSite.Method(HOOKS, "mouseButton", "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"), 4)),
                new Patch(UI, "update after isAiming", classBytes -> CallSite.insertAfter(classBytes,
                        new CallSite.Method(UI, "update", "()V"), aiming, clickAiming)),
                // Once where a right press reaches the object under the cursor and once where its release does.
                new Patch(UI, "updateMouseButtons after isAiming", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method(UI, "updateMouseButtons", "(II)I"), aiming, clickAiming, 2)),
                returningWithThis(PLAYER, "calculateAimVector", VECTOR2_FILTER, "aimVector", byPlayer),
                instead(ballistics, new CallSite.Method(RETICLE, "getX", INT_FILTER), "aimPointX"),
                instead(ballistics, new CallSite.Method(RETICLE, "getY", INT_FILTER), "aimPointY"),
                new Patch(ballistics.owner(), "update calling calculateMuzzlePosition", classBytes -> CallSite.replaceEach(classBytes,
                        ballistics, muzzleAt, muzzle, 1)),
                // The line a target is let in along, and the line a shot that hit nothing is sent along.
                new Patch(processHit.owner(), "calculateBallistics calling calculateMuzzlePosition", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(processHit.owner(), "calculateBallistics", "(" + character + "F)V"), muzzleAt, muzzle, 1)),
                new Patch(processHit.owner(), "fireWeapon calling calculateMuzzlePosition", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(processHit.owner(), "fireWeapon", "(Lzombie/inventory/types/HandWeapon;" + character + ")V"), muzzleAt, muzzle, 1)),
                new Patch(processHit.owner(), "processHit at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes, processHit, code -> {
                    code.aload(1);
                    code.aload(2);
                    code.aload(3);
                    CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "shotAt", "(Lzombie/inventory/types/HandWeapon;" + character + character + ")V"));
                })),
                new Patch(processHit.owner(), "processHit calling isCameraTarget", classBytes -> CallSite.replaceEach(classBytes, processHit,
                        new CallSite.Method(ballistics.owner(), "isCameraTarget", "(I)Z"),
                        new CallSite.Method(HOOKS, "cameraTarget", "(L" + ballistics.owner() + ";I)Z"), 1)),
                new Patch(processHit.owner(), "processHit calling getCachedTargetedBodyPart", classBytes -> CallSite.replaceEach(classBytes, processHit,
                        new CallSite.Method(ballistics.owner(), "getCachedTargetedBodyPart", "(I)I"),
                        new CallSite.Method(HOOKS, "targetedBodyPart", "(L" + ballistics.owner() + ";I)I"), 1)),
                new Patch(zombie, "hitConsequences before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(zombie, "hitConsequences", "(Lzombie/inventory/types/HandWeapon;" + character + "ZFZ)V"), code -> {
                            code.aload(0);
                            code.aload(1);
                            code.aload(2);
                            code.fload(4);
                            code.iload(5);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "zombieHit", "(L" + zombie + ";Lzombie/inventory/types/HandWeapon;" + character + "FZ)V"));
                        })),
                // The game forgets a highlighted object it draws into a chunk's texture once it has
                // drawn it there, and keeps only the object's flag: Outlines keeps its own list.
                new Patch(HIGHLIGHT, "registerObject and unregisterObject at their starts", classBytes -> {
                    for (String[] hook : new String[][] {{"registerObject", "highlightRegistered"}, {"unregisterObject", "highlightUnregistered"}}) {
                        classBytes = CallSite.insertAtStartOfInstanceMethod(classBytes, new CallSite.Method(HIGHLIGHT, hook[0], "(Lzombie/iso/IsoObject;)V"), code -> {
                            code.aload(1);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, hook[1], "(Lzombie/iso/IsoObject;)V"));
                        });
                    }
                    return classBytes;
                }),
                new Patch(zombie, "resetForReuse at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                        new CallSite.Method(zombie, "resetForReuse", "()V"), code -> {
                            code.aload(0);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "zombieReused", "(L" + zombie + ";)V"));
                        })),
                new Patch("zombie/iso/objects/IsoDeadBody", "<init> of a character before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method("zombie/iso/objects/IsoDeadBody", "<init>", "(Lzombie/characters/IsoGameCharacter;ZZ)V"), code -> {
                            code.aload(0);
                            code.aload(1);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "bodyMade", "(Lzombie/iso/objects/IsoDeadBody;Lzombie/characters/IsoGameCharacter;)V"));
                        })),
                new Patch(BODY_PARAMS, "init of a body before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(BODY_PARAMS, "init", "(Lzombie/iso/objects/IsoDeadBody;)V"), code -> {
                            code.aload(0);
                            code.aload(1);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "bodyParams", "(L" + BODY_PARAMS + ";Lzombie/iso/objects/IsoDeadBody;)V"));
                        })),
                new Patch(ATLAS, "getBodyKey before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(ATLAS, "getBodyKey", "(L" + BODY_PARAMS + ";)Ljava/lang/String;"), code -> {
                            code.aload(1);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "bodyKey", "(Ljava/lang/String;L" + BODY_PARAMS + ";)Ljava/lang/String;"));
                        })),
                new Patch(RENDER_JOB, "init after setDeathPose", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method(RENDER_JOB, "init", "(L" + BODY_PARAMS + ";L" + ATLAS + "$AtlasEntry;)L" + RENDER_JOB + ";"),
                        new CallSite.Method(RENDER_JOB, "setDeathPose", "()V"), code -> {
                            code.aload(0);
                            code.getfield(ClassDesc.ofInternalName(RENDER_JOB), "body", ClassDesc.ofInternalName(BODY_PARAMS));
                            code.aload(0);
                            code.getfield(ClassDesc.ofInternalName(RENDER_JOB), "animatedModel", ClassDesc.ofInternalName(ANIMATED_MODEL));
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "bodyBaking", "(L" + BODY_PARAMS + ";L" + ANIMATED_MODEL + ";)V"));
                        }, 1)),
                new Patch(RENDER_JOB, "render after toBodyAtlas", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method(RENDER_JOB, "render", "()V"),
                        new CallSite.Method(ATLAS, "toBodyAtlas", "(L" + RENDER_JOB + ";)V"), code -> {
                            code.aload(0);
                            code.getfield(ClassDesc.ofInternalName(RENDER_JOB), "animatedModel", ClassDesc.ofInternalName(ANIMATED_MODEL));
                            code.aload(0);
                            code.getfield(ClassDesc.ofInternalName(RENDER_JOB), "entry", ClassDesc.ofInternalName(ATLAS + "$AtlasEntry"));
                            code.aload(0);
                            code.getfield(ClassDesc.ofInternalName(RENDER_JOB), "animPlayerAngle", java.lang.constant.ConstantDescs.CD_float);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "bodyBaked", "(L" + ANIMATED_MODEL + ";Ljava/lang/Object;F)V"));
                        }, 1)),
                new Patch("zombie/core/textures/SmartTexture", "destroy at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                        new CallSite.Method("zombie/core/textures/SmartTexture", "destroy", "()V"), code -> {
                            code.aload(0);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "keepsTexture", "(Lzombie/core/textures/Texture;)Z"));
                            code.ifThen(java.lang.classfile.Opcode.IFNE, java.lang.classfile.CodeBuilder::return_);
                        })),
                new Patch("zombie/iso/IsoMetaGrid", "processZones before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method("zombie/iso/IsoMetaGrid", "processZones", "()V"), code -> {
                            code.aload(0);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "zonesProcessed", "(Lzombie/iso/IsoMetaGrid;)V"));
                        })),
                new Patch("zombie/core/Core", "getKeyBinding before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method("zombie/core/Core", "getKeyBinding", "(Ljava/lang/String;)" + KEY_BINDING),
                        code -> CallSite.passFirstArgument(code, new CallSite.Method(HOOKS, "binding",
                                "(" + KEY_BINDING + "Ljava/lang/String;)" + KEY_BINDING)))),
                heldAim("getXA", "heldAcross"),
                heldAim("getYA", "heldDown"),
                new Patch(GAME_MOUSE, "update before its returns", classBytes -> {
                    CallSite.requireStaticInt(classBytes, "x");
                    CallSite.requireStaticInt(classBytes, "y");
                    return CallSite.insertBeforeReturns(classBytes, mouseUpdate, code -> {
                        CallSite.filterStaticInt(code, GAME_MOUSE, "x", new CallSite.Method(HOOKS, "cursorX", "(I)I"));
                        CallSite.filterStaticInt(code, GAME_MOUSE, "y", new CallSite.Method(HOOKS, "cursorY", "(I)I"));
                    });
                }));
    }

    /** Apart from first person's: the shader of a model of an item on the ground is made without a frame's wait. See ItemModels.createShader. */
    private static List<Patch> itemModelPatches() {
        String model = "zombie/core/skinnedmodel/model/Model";
        return List.of(new Patch(model, "<init> calling CreateShader", classBytes -> CallSite.replaceEach(classBytes,
                new CallSite.Method(model, "<init>", "(Lzombie/asset/AssetPath;Lzombie/asset/AssetManager;L" + model + "$ModelAssetParams;)V"),
                new CallSite.Method(model, "CreateShader", "(Ljava/lang/String;)V"),
                new CallSite.Method("com/cameraunlock/zomboid/ItemModels", "createShader", "(L" + model + ";Ljava/lang/String;)V"), 1)));
    }

    /** Apart from first person's: a body hit goes into its ragdoll at once, and a first person shot pushes it along the shot. See Ragdolls and HitImpulse. */
    private static List<Patch> ragdollPatches() {
        CallSite.Method applyImpulse = new CallSite.Method("zombie/core/physics/Bullet", "applyImpulse", "(II[F)V");
        CallSite.Method pushAlongTheShot = new CallSite.Method("com/cameraunlock/zomboid/HitImpulse", "apply", "(II[F)V");
        return List.of(
                new Patch(RAGDOLL, "simulateHitReaction calling applyImpulse", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(RAGDOLL, "simulateHitReaction", "()V"), applyImpulse, pushAlongTheShot, 1)),
                new Patch(LIVE_NODE, "getBlendInTime before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(LIVE_NODE, "getBlendInTime", "()F"),
                        code -> CallSite.passThis(code, new CallSite.Method("com/cameraunlock/zomboid/Ragdolls", "blendIn", "(FL" + LIVE_NODE + ";)F")))),
                new Patch(LIVE_NODE, "getBlendOutTime before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(LIVE_NODE, "getBlendOutTime", "()F"),
                        code -> CallSite.passThis(code, new CallSite.Method("com/cameraunlock/zomboid/Ragdolls", "blendOut", "(FL" + LIVE_NODE + ";)F")))));
    }

    /**
     * Apart from first person's, and in whichever view a player is: the hook every frame of the
     * mod runs from, straight after the game has drawn a player's world (the keys, the head
     * turned with the tracker and sent to other players, the sound's mix, first person's frame
     * and the isometric view's), and the one after it has written what people say, where the
     * mod writes what the screen tells the player. Without the first the mod does nothing in play.
     */
    private static List<Patch> framePatches() {
        return List.of(
                after(new CallSite.Method("zombie/gameStates/IngameState", "renderFrameInternal", "(I)V"),
                        new CallSite.Method("zombie/iso/IsoWorld", "render", "()V"),
                        new CallSite.Method(HOOKS, "afterWorldRender", "()V")),
                after(new CallSite.Method("zombie/gameStates/IngameState", "renderFrameTextInternal", "(I)V"),
                        new CallSite.Method("zombie/chat/ChatElement", "RenderBatch", "(I)V"),
                        new CallSite.Method(HOOKS, "afterWorldText", "()V")));
    }

    /**
     * Apart from first person's: a round goes on through plasterboard, boards and doors, from
     * either view. See Penetration. Without them every shot stops at the first wall, as the game
     * has it.
     */
    private static List<Patch> throughWallPatches() {
        String character = "Lzombie/characters/IsoGameCharacter;", testResults = "Lzombie/iso/LosUtil$TestResults;", moving = "Lzombie/iso/IsoMovingObject;";
        String combat = "zombie/CombatManager";
        CallSite.Method hitListWeapon = new CallSite.Method(combat, "calculateHitListWeapon",
                "(" + character + "Lzombie/network/fields/hit/AttackVars;Lzombie/util/list/PZArrayList;)V");
        CallSite.Method hitInfoList = new CallSite.Method(combat, "calculateHitInfoList", "(" + character + ")V");
        CallSite.Method validTarget = new CallSite.Method("zombie/core/physics/BallisticsController", "isValidTarget", "(I)Z");
        CallSite.Method keptThrough = new CallSite.Method(HOOKS, "validTarget", "(Z" + moving + ")Z");
        return List.of(
                new Patch(combat, "calculateHitInfoList at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes, hitInfoList,
                        code -> CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "hitListStarts", "()V")))),
                new Patch(combat, "calculateHitListWeapon after lineClear", classBytes -> CallSite.insertAfterEach(classBytes, hitListWeapon,
                        new CallSite.Method("zombie/iso/LosUtil", "lineClear", "(Lzombie/iso/IsoCell;IIIIIIZ)" + testResults),
                        List.of(CallSite.Stored.returnOf(new CallSite.Method("zombie/network/fields/hit/AttackVars", "getWeapon",
                                        "(Lzombie/characters/IsoLivingCharacter;)Lzombie/inventory/types/HandWeapon;")),
                                CallSite.Stored.castTo("zombie/iso/IsoMovingObject")), (code, slots) -> {
                            code.aload(1);
                            code.aload(slots[0]);
                            code.aload(slots[1]);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "lineOfFire", "(" + testResults + character + "Lzombie/inventory/types/HandWeapon;" + moving + ")" + testResults));
                        }, 1)),
                // Both drop a ranged target the ballistics did not find: calculateHitInfoList has the target as the hit's object, processBallisticsTargets as that object cast to a character.
                new Patch(combat, "calculateHitInfoList after isValidTarget", classBytes -> CallSite.insertAfterEach(classBytes, hitInfoList,
                        validTarget, List.of(CallSite.Stored.returnOf(new CallSite.Method("zombie/network/fields/hit/HitInfo", "getObject", "()" + moving))),
                        (code, slots) -> {
                            code.aload(slots[0]);
                            CallSite.invokeStatic(code, keptThrough);
                        }, 2)),
                new Patch(combat, "processBallisticsTargets after isValidTarget", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method(combat, "processBallisticsTargets", "(" + character + "Lzombie/inventory/types/HandWeapon;Lzombie/util/list/PZArrayList;)V"),
                        validTarget, List.of(CallSite.Stored.castTo("zombie/characters/IsoGameCharacter")), (code, slots) -> {
                            code.aload(slots[0]);
                            CallSite.invokeStatic(code, keptThrough);
                        }, 1)),
                new Patch(combat, "attackCollisionCheck calling Hit", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(combat, "attackCollisionCheck", "(" + character + "Lzombie/inventory/types/HandWeapon;Lzombie/ai/states/SwipeStatePlayer;Lzombie/AttackType;)V"),
                        new CallSite.Method("zombie/iso/IsoMovingObject", "Hit", "(Lzombie/inventory/types/HandWeapon;" + character + "FZF)F"),
                        new CallSite.Method(HOOKS, "hitThrough", "(" + moving + "Lzombie/inventory/types/HandWeapon;" + character + "FZF)F"), 1)));
    }

    /**
     * Apart from first person's, and in whichever view a player is: the world's tick, which ages
     * the blood trails and the flying pieces and dresses the hidden walls; the trail lifted off a
     * chunk while the game saves it (Bleeding.saving); and the hidden walls of a chunk found as it
     * loads (HiddenWalls.loaded).
     */
    private static List<Patch> worldPatches() {
        String chunk = "zombie/iso/IsoChunk";
        CallSite.Method save = new CallSite.Method(chunk, "Save", "(Ljava/nio/ByteBuffer;Ljava/util/zip/CRC32;Z)Ljava/nio/ByteBuffer;");
        return List.of(
                new Patch(chunk, "Save to a buffer at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes, save, code -> {
                    code.aload(0);
                    CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "chunkSaving", "(L" + chunk + ";)V"));
                })),
                new Patch(chunk, "Save to a buffer on its way out", classBytes -> CallSite.insertOnEveryWayOut(classBytes, save, code -> {
                    code.aload(0);
                    CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "chunkSaved", "(L" + chunk + ";)V"));
                })),
                new Patch(chunk, "doLoadGridsquare on its way out", classBytes -> CallSite.insertOnEveryWayOut(classBytes,
                        new CallSite.Method(chunk, "doLoadGridsquare", "()V"), code -> {
                            code.aload(0);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "chunkLoaded", "(L" + chunk + ";)V"));
                        })),
                new Patch("zombie/iso/IsoWorld", "update at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                        new CallSite.Method("zombie/iso/IsoWorld", "update", "()V"),
                        code -> CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "worldUpdate", "()V")))));
    }

    /** How much the game's screen shader greys a player's view, through ScreenGrey. */
    private static List<Patch> screenGreyPatches() {
        String screen = "zombie/iso/weather/WeatherShader", settings = "zombie/core/opengl/RenderSettings$PlayerRenderSettings";
        return List.of(new Patch(screen, "startMainThread reading the screen's desaturation", classBytes -> CallSite.replaceEach(classBytes,
                new CallSite.Method(screen, "startMainThread", "(Lzombie/core/textures/TextureDraw;I)V"),
                new CallSite.Method(settings, "getDesaturation", "()F"),
                new CallSite.Method(HOOKS, "screenDesaturation", "(L" + settings + ";)F"), 1)));
    }

    /**
     * Apart again, and each from the other: what the game draws of the weather for its own view,
     * which is left out while the first person picture covers that view whole. The picture has
     * rain, puddles and fog of its own, and the game's were built drop by drop on its logic
     * thread and written out corner by corner on its render thread, only to be painted over: in
     * rain they were a quarter of what the game's logic thread did and most of what its render
     * thread did outside the graphics driver. Each of these draws and keeps nothing the game
     * goes by: where the rain falls, how wet the ground is and what the weather is are worked
     * out elsewhere and are as they were.
     */
    private static List<Patch> coveredPatches() {
        String world = "zombie/iso/IsoWorld", cell = "zombie/iso/fboRenderChunk/FBORenderCell";
        String cutaways = "zombie/iso/fboRenderChunk/FBORenderCutaways", outline = "zombie/iso/fboRenderChunk/FBORenderObjectOutline";
        String shadows = "zombie/iso/fboRenderChunk/FBORenderShadows";
        CallSite.Method covered = new CallSite.Method(HOOKS, "coversTheGamesView", "()Z");
        return List.of(
                new Patch(world, "renderWeatherFX left out under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(world, "renderWeatherFX", "()V"), covered)),
                new Patch(cell, "renderPuddles left out under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(cell, "renderPuddles", "(I)V"), covered)),
                new Patch(cell, "renderRainSplashes left out under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(cell, "renderRainSplashes", "(II)V"), covered)),
                new Patch(cell, "renderFog left out under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(cell, "renderFog", "(I)V"), covered)),
                // The shadow the game lays under a character in its own view, and which squares
                // hide others from its camera overhead, where it keeps a texture of each level:
                // the picture draws its own shadows and keeps no such textures.
                new Patch("zombie/characters/IsoGameCharacter", "renderShadow left out under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method("zombie/characters/IsoGameCharacter", "renderShadow", "(FFF)V"), covered)),
                new Patch(cell, "calculateOccludingSquares left out under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(cell, "calculateOccludingSquares", "(I)V"), covered)),
                // The outline round a highlighted object (the container the loot panel shows, in
                // the player's object highlight colour) is laid over the frame where the object
                // stands in the game's own view, and not under the picture: it was left floating
                // by the middle of the screen. The picture draws the same outlines round the
                // things themselves: see Outlines. An object taken from the world while outlined
                // is let go of when the game's view next draws.
                new Patch(outline, "object outlines left out under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(outline, "render", "(I)V"), covered)),
                // The arrow to where the game's Lua points the player is drawn by where the player stands
                // in the game's own view and turned the way the place lies in it: over the picture it
                // pointed nowhere by the middle of the screen. The picture points to the place itself:
                // see Homing.
                new Patch("zombie/iso/WorldMarkers", "homing arrows left out under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method("zombie/iso/WorldMarkers", "renderHomingPoint", "()V"), covered)),
                // The rest of what the game draws of the world into its own view while the picture
                // covers it: the squares of each chunk, into the chunk's own texture; the trees,
                // plants, windows and whatever else it draws afresh over those every frame, sprite by
                // sprite; and what lies and moves on the ground. With the lawn of a garden in view the
                // second was a tenth of the game's logic thread and most of what its render thread
                // did. The people and the vehicles are drawn by other steps, which run: the picture
                // draws them from what those leave. A chunk whose texture was not drawn is still due
                // its drawing when the game's own view is next shown.
                new Patch(cell, "the game's drawing of its own covered view left out under the first person picture", classBytes -> {
                    for (String name : new String[] {"renderTranslucentObjects", "renderTranslucentFloorObjects"}) {
                        classBytes = CallSite.returnAtOnceIf(classBytes, new CallSite.Method(cell, name, "(IILzombie/core/opengl/Shader;Lzombie/core/opengl/Shader;J)V"), covered);
                    }
                    for (String name : new String[] {"renderItemsInWorld", "renderWater", "renderWaterShore", "renderFlies", "renderAnimatedAttachments"}) {
                        classBytes = CallSite.returnAtOnceIf(classBytes, new CallSite.Method(cell, name, "(I)V"), covered);
                    }
                    return CallSite.returnAtOnceIf(classBytes,
                            new CallSite.Method(cell, "renderOneChunk", "(Lzombie/iso/IsoChunk;Lzombie/iso/IsoCell$PerPlayerRender;IJLzombie/core/opengl/Shader;Lzombie/core/opengl/Shader;)V"), covered);
                }),
                // What the game works out for each chunk's texture before it draws one: which walls
                // of each level are cut away, and every square's light. It does both for every level
                // whose texture is due its drawing, and under the picture that drawing never comes,
                // so every level in view was worked out afresh every frame: on the fifteenth floor
                // of a building of 27 that was two fifths of the game's logic thread. The levels
                // stay due, and are worked out when the game's own view is next shown.
                new Patch(cell, "the chunks' textures not prepared under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(cell, "prepareChunksForUpdating", "(I)V"), covered)),
                new Patch(cutaways, "the levels' cut away walls not worked out under the first person picture", classBytes -> CallSite.falseAtOnceIf(classBytes,
                        new CallSite.Method(cutaways, "checkExteriorWalls", "(Ljava/util/ArrayList;)Z"), covered)),
                // The game's shadows under its people, bodies and mannequins, each worked out over the
                // ground's slope and depth as it is added, and drawn into its own view only.
                new Patch(shadows, "shadows not added under the first person picture", classBytes -> {
                    for (String added : new String[] {"(FFFLorg/joml/Vector3f;FFFFFFFZ)V", "(FFFFFFFFFFFFFFFLzombie/core/textures/Texture;Z)V"}) {
                        classBytes = CallSite.returnAtOnceIf(classBytes, new CallSite.Method(shadows, "addShadow", added), covered);
                    }
                    return classBytes;
                }),
                // The dark the game lays over what its player cannot see: worked out from the player's
                // cone of sight over the walls round them and drawn into its own view only. The
                // picture goes by the mod's own walk through the rooms (RoomSight), not by this.
                new Patch("zombie/vispoly/VisibilityPolygon2", "the cone of sight not drawn under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method("zombie/vispoly/VisibilityPolygon2", "renderMain", "(I)V"), covered)),
                // The game's own draw of each person into its own view, which the picture then
                // covers: the picture draws every part of them again itself, by RenderCharacter.
                // performRenderCharacter is the whole of that draw, the GL state and the model
                // camera it sets up for it included, and leaves the camera's angle as it found it.
                // What is round it in render still runs: where the model's camera is stood,
                // which the picture reads, and what says a model was shown.
                new Patch(CHARACTER_DRAW, "skip covered character render setup", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(CHARACTER_DRAW, "performRenderCharacter", "()V"),
                        new CallSite.Method(HOOKS, "gamesOwnDrawIsCovered", "()Z"))),
                new Patch(PART_DRAW, "RenderCharacter left out under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(PART_DRAW, "RenderCharacter", "(L" + CHARACTER_DRAW + ";)V"),
                        new CallSite.Method(HOOKS, "gamesOwnDrawIsCovered", "()Z"))));
    }

    /**
     * Apart from the covered skips, and in whichever view a player is: the game's draw of each part
     * of a person goes through GameMouths, which opens the mouth of one who talks. The game draws
     * an animal by a program of its own, which the picture leaves to it: the picture's finer skin
     * is handed to that draw once it has taken the model's. Without it the people the game draws
     * keep their mouths shut.
     */
    private static List<Patch> partDrawPatches() {
        return List.of(new Patch(PART_DRAW, "RenderCharacter hands its skin to the picture", classBytes -> CallSite.replaceEach(classBytes,
                new CallSite.Method(PART_DRAW, "RenderCharacter", "(L" + CHARACTER_DRAW + ";)V"),
                new CallSite.Method(MODEL, "DrawChar", "(L" + CHARACTER_DRAW + ";L" + PART_DRAW + ";)V"),
                new CallSite.Method(HOOKS, "partDrawn", "(L" + MODEL + ";L" + CHARACTER_DRAW + ";L" + PART_DRAW + ";)V"), 1)));
    }

    /**
     * The game spots a room, and marks a square seen this hour, as it works out the square's
     * light, so under the first person picture both went by where and when the mod read light.
     * With this they are left to Spotting there, and are as they were everywhere else. The
     * light is worked out by updateFBORenderChunk, and by update itself where
     * PerformanceSettings.fboRenderChunk is off, which only the game's debug key for its old
     * renderer does (IsoCell.checkToggleOldRendererKey). One patch, so that every call goes to
     * the hooks or none does.
     */
    private static List<Patch> spottingPatches() {
        String lighting = "zombie/iso/LightingJNI$JNILighting";
        return List.of(new Patch(lighting, "update and updateFBORenderChunk leaving rooms and marks to Spotting under the first person picture",
                classBytes -> lightLeavingSpotting(classBytes, lighting, HOOKS)));
    }

    /** spottingPatches' rewrite of a class of light whose player is its int field playerIndex, to the hooks roomSeenByLight and squareSeenByLight of the class given. */
    static byte[] lightLeavingSpotting(byte[] classBytes, String lighting, String hooks) {
        String square = "zombie/iso/IsoGridSquare", meta = "zombie/meta/Meta";
        CallSite.Method roomSeen = new CallSite.Method(square, "checkRoomSeen", "(I)V"), byLight = new CallSite.Method(hooks, "roomSeenByLight", "(L" + square + ";I)V");
        CallSite.Method marked = new CallSite.Method(meta, "dealWithSquareSeen", "(L" + square + ";)V");
        CallSite.Method squareSeen = new CallSite.Method(hooks, "squareSeenByLight", "(L" + meta + ";L" + square + ";I)V");
        CallSite.Method fbo = new CallSite.Method(lighting, "updateFBORenderChunk", "()V"), old = new CallSite.Method(lighting, "update", "()V");
        Consumer<CodeBuilder> squareSeenForThePlayer = code -> {
            code.aload(0).getfield(ClassDesc.ofInternalName(lighting), "playerIndex", ClassDesc.ofDescriptor("I"));
            CallSite.invokeStatic(code, squareSeen);
        };
        classBytes = CallSite.replaceEach(classBytes, fbo, roomSeen, byLight, 1);
        classBytes = CallSite.replaceEach(classBytes, fbo, marked, squareSeenForThePlayer, 1);
        // update spots a room from a square seen, and also from an unseen one beside the player.
        classBytes = CallSite.replaceEach(classBytes, old, roomSeen, byLight, 2);
        return CallSite.replaceEach(classBytes, old, marked, squareSeenForThePlayer, 1);
    }

    /**
     * Apart again: without it first person works as it does, and a vehicle comes into the
     * picture where the game's own view would first show it. The game draws the model of a
     * thing that moves only while it is on the game's own screen and on ground the viewer has
     * seen: the picture sees further, and a vehicle within its sight is drawn. A zombie has a
     * model at all only near the game's screen, and from a vehicle the zombies ahead keep
     * theirs and are drawn as well. So does an animal, and where players cannot fight, another
     * player. See FarModels.
     */
    private static List<Patch> farVehiclePatches() {
        String cell = "zombie/iso/fboRenderChunk/FBORenderCell", vehicle = "zombie/vehicles/BaseVehicle";
        String world = "zombie/iso/IsoWorld", zombie = "zombie/characters/IsoZombie", player = "L" + PLAYER + ";";
        return List.of(
                new Patch(cell, "renderMovingObject asks the picture whether a vehicle is in view", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(cell, "renderMovingObject", "(Lzombie/iso/IsoMovingObject;)V"),
                        new CallSite.Method("zombie/iso/IsoMovingObject", "isOnScreen", "()Z"),
                        new CallSite.Method(HOOKS, "onScreen", "(Lzombie/iso/IsoMovingObject;)Z"), 1)),
                new Patch(cell, "renderMovingObject draws a thing that moves above the game's cutaway under the picture", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method(cell, "renderMovingObject", "(Lzombie/iso/IsoMovingObject;)V"),
                        new CallSite.Method(cell, "shouldRenderSquare", "(Lzombie/iso/IsoGridSquare;)Z"),
                        code -> {
                            code.aload(1);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "aboveTheCut", "(ZLzombie/iso/IsoMovingObject;)Z"));
                        }, 1)),
                new Patch(cell, "renderPlayer asks the picture whether a player is in view", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(cell, "renderPlayer", "(I" + player + ")V"),
                        new CallSite.Method(PLAYER, "isOnScreen", "()Z"),
                        new CallSite.Method(HOOKS, "playerOnScreen", "(" + player + ")Z"), 1)),
                new Patch(PLAYER, "a remote player's render asks the picture whether the viewer sees them", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(PLAYER, "render", "(FFFLzombie/core/textures/ColorInfo;ZZLzombie/core/opengl/Shader;)V"),
                        new CallSite.Method(PLAYER, "checkCanSeeClient", "(" + player + ")Z"),
                        new CallSite.Method(HOOKS, "seesPlayer", "(" + player + player + ")Z"), 1)),
                new Patch(vehicle, "a vehicle's render asks the picture whether it is seen", classBytes -> {
                    CallSite.Method render = new CallSite.Method(vehicle, "render", "(FFFLzombie/core/textures/ColorInfo;ZZLzombie/core/opengl/Shader;)V");
                    CallSite.Method seen = new CallSite.Method(HOOKS, "seen", "(ZL" + vehicle + ";)Z");
                    Consumer<CodeBuilder> ask = code -> {
                        code.aload(0);
                        CallSite.invokeStatic(code, seen);
                    };
                    classBytes = CallSite.insertAfterEach(classBytes, render, new CallSite.Method("zombie/iso/IsoGridSquare$ILighting", "bSeen", "()Z"), ask, 1);
                    return CallSite.insertAfterEach(classBytes, render, new CallSite.Method(vehicle, "couldSeeIntersectedSquare", "(I)Z"), ask, 1);
                }),
                new Patch(world, "sceneCullZombies asks the picture where a zombie is on the screen", classBytes -> {
                    CallSite.Method cull = new CallSite.Method(world, "sceneCullZombies", "()V");
                    classBytes = CallSite.replaceEach(classBytes, cull, new CallSite.Method(zombie, "getScreenProperX", "(I)I"),
                            new CallSite.Method(HOOKS, "screenAcross", "(L" + zombie + ";I)I"), 1);
                    return CallSite.replaceEach(classBytes, cull, new CallSite.Method(zombie, "getScreenProperY", "(I)I"),
                            new CallSite.Method(HOOKS, "screenDown", "(L" + zombie + ";I)I"), 1);
                }),
                new Patch("zombie/characters/IsoGameCharacter", "updateAnimationTimeDelta asks the picture whether a character shows", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method("zombie/characters/IsoGameCharacter", "updateAnimationTimeDelta", "()V"),
                        new CallSite.Method("zombie/characters/IsoGameCharacter", "getAlpha", "(I)F"),
                        new CallSite.Method(HOOKS, "animationAlpha", "(Lzombie/characters/IsoGameCharacter;I)F"), 1)),
                new Patch(world, "sceneCullAnimals hands its choice to the picture", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(world, "sceneCullAnimals", "()V"),
                        code -> CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "animalsCulled", "()V")))));
    }

    /** The game's sight of zombies in its own view, with the line to each read as the map is: see LiveSight. Without them it is the game's grid alone. */
    private static List<Patch> liveSightPatches() {
        String lighting = "zombie/iso/LightingJNI", square = "zombie/iso/IsoGridSquare", character = "Lzombie/characters/IsoGameCharacter;";
        return List.of(
                new Patch(lighting, "calculateVisionCone before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(lighting, "calculateVisionCone", "(" + character + ")F"), code -> {
                            code.aload(0);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "visionCone", "(F" + character + ")F"));
                        })),
                new Patch("zombie/characters/IsoGameCharacter", "TestIfSeen reads the line to a square beside the grid's marks", classBytes -> {
                    CallSite.Method test = new CallSite.Method("zombie/characters/IsoGameCharacter", "TestIfSeen", "(IL" + PLAYER + ";)Z");
                    classBytes = CallSite.insertAfterEach(classBytes, test, new CallSite.Method(square, "isCouldSee", "(I)Z"), code -> {
                        code.aload(0);
                        code.iload(1);
                        code.aload(2);
                        CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "couldSee", "(Z" + character + "IL" + PLAYER + ";)Z"));
                    }, 1);
                    return CallSite.insertAfterEach(classBytes, test, new CallSite.Method(square, "isCanSee", "(I)Z"), code -> {
                        code.aload(0);
                        CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "canSee", "(Z" + character + ")Z"));
                    }, 1);
                }),
                new Patch(PLAYER, "updateLOS before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(PLAYER, "updateLOS", "()V"), code -> {
                            code.aload(0);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "lineOfSightDone", "(L" + PLAYER + ";)V"));
                        })));
    }

    /** Apart from everything else: without them the game takes its controllers as it does. See Gamepads. */
    private static List<Patch> controllerPatches() {
        String controllers = "org/lwjglx/input/Controllers", joypads = "zombie/input/JoypadManager", joypad = joypads + "$Joypad";
        String gamepads = "com/cameraunlock/zomboid/Gamepads";
        return List.of(
                new Patch(controllers, "create before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(controllers, "create", "()V"),
                        code -> CallSite.invokeStatic(code, new CallSite.Method(gamepads, "found", "()V")))),
                new Patch(controllers, "updateControllersCount before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(controllers, "updateControllersCount", "(II)V"),
                        code -> CallSite.passFirstStaticInt(code, new CallSite.Method(gamepads, "plugged", "(I)V")))),
                new Patch(joypads, "addJoypad calling contains", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(joypads, "addJoypad", "(ILjava/lang/String;Ljava/lang/String;)V"),
                        new CallSite.Method("java/util/HashSet", "contains", "(Ljava/lang/Object;)Z"),
                        new CallSite.Method(gamepads, "plays", "(Ljava/util/HashSet;Ljava/lang/Object;)Z"), 1)),
                new Patch("zombie/GameWindow", "logic calling isAPressed", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method("zombie/GameWindow", "logic", "()V"),
                        new CallSite.Method(joypad, "isAPressed", "()Z"),
                        new CallSite.Method(gamepads, "pressedA", "(L" + joypad + ";)Z"), 1)),
                new Patch("zombie/gameStates/MainScreenState", "update at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                        new CallSite.Method("zombie/gameStates/MainScreenState", "update", "()Lzombie/gameStates/GameStateMachine$StateAction;"),
                        code -> CallSite.invokeStatic(code, new CallSite.Method(gamepads, "menu", "()V")))));
    }

    /** Apart from everything else: without it the mod's settings are in CameraUnlock.ini and on its keys, as they are in any game. */
    private static List<Patch> luaLoadedPatches() {
        String lua = "zombie/Lua/LuaManager";
        return List.of(new Patch(lua, "LoadDirBase before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                new CallSite.Method(lua, "LoadDirBase", "()V"),
                code -> CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "luaLoaded", "()V")))));
    }

    /**
     * Apart from everything else, and each from the other: without both a vehicle meets fences
     * and lamp posts as the game has it. See CarSmash.
     */
    private static List<Patch> knockdownPatches() {
        String object = "zombie/iso/IsoObject", vehicle = "zombie/vehicles/BaseVehicle";
        CallSite.Method collision = new CallSite.Method(object, "Collision", "(Lzombie/iso/Vector2;L" + object + ";)V");
        return List.of(
                new Patch(vehicle, "breakingObjects looking ahead for fences and posts at its start", classBytes ->
                        CallSite.insertAtStartOfInstanceMethod(classBytes, new CallSite.Method(vehicle, "breakingObjects", "()V"),
                                code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "meeting", "(L" + vehicle + ";)V")))),
                new Patch(object, "Collision passed over where a vehicle knocks the thing down", classBytes ->
                        CallSite.insertAtStartOfInstanceMethod(classBytes, collision, code -> {
                            code.aload(0);
                            code.aload(2);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "knockedDown", "(L" + object + ";L" + object + ";)Z"));
                            code.ifThen(Opcode.IFNE, CodeBuilder::return_);
                        })),
                new Patch("zombie/core/physics/CarController", "update's off-road engine force", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method("zombie/core/physics/CarController", "update", "()V"),
                        new CallSite.Method(vehicle, "isDoingOffroad", "()Z"),
                        new CallSite.Method(HOOKS, "offRoadEngine", "(Z)Z"), 1)),
                new Patch(vehicle, "breakingObjects' slowing by what it touches", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method(vehicle, "breakingObjects", "()V"),
                        new CallSite.Method(object, "GetVehicleSlowFactor", "(L" + vehicle + ";)F"),
                        new CallSite.Method(HOOKS, "slowFactor", "(F)F"), 2)),
                new Patch(vehicle, "applyImpulseFromHitPlant passed over where plants give way", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(vehicle, "applyImpulseFromHitPlant", "(L" + object + ";F)V"),
                        new CallSite.Method(HOOKS, "plantGivesWay", "()Z"))));
    }

    /**
     * Apart from everything else: without it vehicles keep to the game's level ground, never
     * roll over and never burn out on their roofs, as they do in the game. See CarPhysics.
     */
    private static List<Patch> carPhysicsPatches() {
        String vehicle = "zombie/vehicles/BaseVehicle", controller = "zombie/core/physics/CarController";
        return List.of(
                new Patch(vehicle, "update at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                        new CallSite.Method(vehicle, "update", "()V"),
                        code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "vehicleUpdate", "(L" + vehicle + ";)V")))),
                new Patch(vehicle, "applyAllImpulsesFromProneCharacters at its start, before each physics step", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                        new CallSite.Method(vehicle, "applyAllImpulsesFromProneCharacters", "()V"),
                        code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "vehicleStepped", "(L" + vehicle + ";)V")))),
                new Patch(controller, "update's handing the engine, brakes and steering to the physics", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(controller, "update", "()V"),
                        new CallSite.Method("zombie/core/physics/Bullet", "controlVehicle", "(IFFF)V"),
                        new CallSite.Method(HOOKS, "controlVehicle", "(IFFF)V"), 2)));
    }

    /**
     * Apart again: without it first person works as it does, what it holds on the graphics
     * card when a save is left for the menu stays held until another save is played, and the
     * saving done on the way out, to the menu or by quitting the game, is written down as a
     * stall (Stall).
     */
    private static List<Patch> leftSavePatches() {
        String state = "zombie/gameStates/IngameState";
        return List.of(new Patch(state, "exit at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                        new CallSite.Method(state, "exit", "()V"),
                        code -> CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "leavingSave", "()V")))),
                new Patch("zombie/GameWindow", "exit at its start", classBytes -> CallSite.insertAtStartOfStaticMethod(classBytes,
                        new CallSite.Method("zombie/GameWindow", "exit", "()V"),
                        code -> CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "leavingSave", "()V")))),
                new Patch(state, "exit before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(state, "exit", "()V"),
                        code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "leftSave", "(Ljava/lang/Object;)V")))));
    }

    /**
     * Apart again: without the first, first person works as it does, and what it is drawn with is
     * made in the first frames of play instead, shaders included. GameLoadingState.update asks
     * WorldStreamer.isBusy once, and only once the world is loaded: from then until the click that
     * begins the save. Without the second the loading screen does not say that it waits for the
     * shaders, and without the third it says "Click to Start" while a click is not taken.
     * GameLoadingState.render reads its done once, in the test before it draws that line.
     */
    private static List<Patch> loadingPatches() {
        String state = "zombie/gameStates/GameLoadingState";
        return List.of(new Patch(state, "update after isBusy", classBytes -> CallSite.insertAfter(classBytes,
                        new CallSite.Method(state, "update", "()Lzombie/gameStates/GameStateMachine$StateAction;"),
                        new CallSite.Method("zombie/iso/WorldStreamer", "isBusy", "()Z"),
                        new CallSite.Method(HOOKS, "worldLoaded", BOOLEAN_FILTER))),
                after(new CallSite.Method(state, "render", "()V"), new CallSite.Method(state, "doQuickTips", "()V"),
                        new CallSite.Method(HOOKS, "loadingText", "()V")),
                new Patch(state, "render's Click to Start held back while the shaders compile", classBytes -> CallSite.filterStaticBooleanReads(classBytes,
                        new CallSite.Method(state, "render", "()V"), state, "done", new CallSite.Method(HOOKS, "clickToStartShown", "(Z)Z"), 1)));
    }

    /**
     * Apart: without it a player's chunk map follows them as the game's own does. ProcessChunkPos
     * works the map's centre out as PZMath.fastfloor of the place over 8, x and then y, from the
     * player's place or the one ahead of their vehicle, and moves the map when either differs from
     * worldX or worldY. Both pass through ChunkCentre before they are kept, so the map is moved,
     * and LightingJNI told, only to the centre it decides.
     */
    private static List<Patch> chunkCentrePatches() {
        String map = "zombie/iso/IsoChunkMap", centre = "com/cameraunlock/zomboid/ChunkCentre", held = "(IL" + map + ";F)I";
        return List.of(new Patch(map, "ProcessChunkPos keeps its centre a tile past the chunk line", classBytes -> CallSite.filterFlooredQuotients(classBytes,
                new CallSite.Method(map, "ProcessChunkPos", "(Lzombie/characters/IsoGameCharacter;)V"), 8f,
                new CallSite.Method("zombie/core/math/PZMath", "fastfloor", "(F)I"),
                List.of(new CallSite.Method(centre, "heldX", held), new CallSite.Method(centre, "heldY", held)))));
    }

    /**
     * Apart from everything else and all or none: with only some of them in, parts of the
     * screen would be laid out one way and parts the other, so SplitScreen changes nothing
     * until every one is. These are the places the game works a player's part of the screen
     * out from its count of players and the player's number. IsoCamera's eight answers are
     * what most of the game and all of its Lua ask. The rest do the sum themselves: where a
     * player's picture is drawn in the one offscreen buffer and how large (Core), the scissor
     * the two atlases put back after drawing a body or an item of their own, and the size the
     * view cone's blur is told. UIManager.resize places the moodles by a sum of another shape,
     * and SplitScreen moves them after it.
     */
    private static List<Patch> splitScreenPatches() {
        String camera = "zombie/iso/IsoCamera", core = "zombie/core/Core", split = "com/cameraunlock/zomboid/SplitScreen";
        String bodies = "zombie/core/skinnedmodel/DeadBodyAtlas", items = "zombie/core/skinnedmodel/model/WorldItemAtlas$RenderJob";
        String startFrame = "(IIFIZZZ)V", ofPlayer = "(I)I";
        CallSite.Method seat = new CallSite.Method(split, "seat", INT_FILTER);
        CallSite.Method drawing = new CallSite.Method("zombie/core/SpriteRenderer", "getRenderingPlayerIndex", "()I");
        CallSite.Method startingFrame = new CallSite.Method(core, "DoStartFrameStuffInternal", startFrame);
        CallSite.Method toBodyAtlas = new CallSite.Method(bodies, "toBodyAtlas", "(L" + bodies + "$RenderJob;)V");
        CallSite.Method restoreStencil = new CallSite.Method(items, "restoreScreenStencil", "()V");
        return List.of(
                halved(new CallSite.Method(camera, "getScreenWidth", ofPlayer), 1, 0),
                halved(new CallSite.Method(camera, "getScreenHeight", ofPlayer), 0, 1),
                seated(new CallSite.Method(camera, "getScreenLeft", ofPlayer), seat),
                seated(new CallSite.Method(camera, "getScreenTop", ofPlayer), seat),
                seated(new CallSite.Method(camera, "getOffscreenLeft", ofPlayer), seat),
                seated(new CallSite.Method(camera, "getOffscreenTop", ofPlayer), seat),
                halved(new CallSite.Method(core, "getOffscreenWidth", ofPlayer), 1, 0),
                halved(new CallSite.Method(core, "getOffscreenHeight", ofPlayer), 0, 1),
                halved(startingFrame, 2, 2),
                new Patch(core, "DoStartFrameStuffInternal taking the player's column", classBytes -> CallSite.filterFirstOperandOfEach(classBytes,
                        startingFrame, Opcode.IREM, seat, 1)),
                new Patch(core, "DoStartFrameStuffInternal taking the player's row", classBytes -> CallSite.filterFirstOperandOfEach(classBytes,
                        startingFrame, Opcode.IF_ICMPLT, seat, 1)),
                halved(new CallSite.Method(core, "DoStartFrameNoZoom", startFrame), 1, 1),
                halved(new CallSite.Method("zombie/vispoly/VisibilityPolygon2$Drawer", "renderToScreen", "()V"), 1, 1),
                halved(toBodyAtlas, 1, 1),
                after(toBodyAtlas, drawing, seat),
                halved(restoreStencil, 1, 1),
                after(restoreStencil, drawing, seat),
                new Patch(UI, "resize before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(UI, "resize", "()V"),
                        code -> code.invokestatic(ClassDesc.ofInternalName(split), "afterResize", MethodTypeDesc.ofDescriptor("()V")))));
    }

    /**
     * Where {@code within} asks whether more than one player shares the width, {@code widths}
     * times, and whether more than two share the height, {@code heights} times, SplitScreen
     * says how many to count.
     */
    private static Patch halved(CallSite.Method within, int widths, int heights) {
        String split = "com/cameraunlock/zomboid/SplitScreen";
        return new Patch(within.owner(), within.name() + " counting the players across and down", classBytes -> CallSite.filterStaticIntCompared(
                CallSite.filterStaticIntCompared(classBytes, within, PLAYER, "numPlayers", 1, new CallSite.Method(split, "across", INT_FILTER), widths),
                within, PLAYER, "numPlayers", 2, new CallSite.Method(split, "down", INT_FILTER), heights));
    }

    /** {@code within} takes a player's column or row from the number it is handed, and nothing else. */
    private static Patch seated(CallSite.Method within, CallSite.Method seat) {
        return new Patch(within.owner(), within.name() + " taking the player's place", classBytes -> CallSite.filterFirstStaticIntArgument(classBytes, within, seat));
    }

    /**
     * Apart again, and each from the other: without the first, first person is heard as the
     * game's own view is, facing north-west on the map whichever way the view faces; without
     * the second its sounds are mixed for however far out the game's own view was left. The
     * game sets its listener at one call, and hands its sounds how far out its camera is from
     * one method. See Hearing.
     *
     * The other two put the walls into what is heard from the eye: without the third no sound
     * is muffled by a wall and none rings in a room but by the game's own mix, as each event
     * an emitter starts is noted at the one method every event starts from; without the
     * fourth a sound behind the head is muffled as if a wall stood there, as the game takes
     * any square its own view of the player could not see as hidden. See SoundMix.
     *
     * The first notes each sound the game has FMOD call back on, as FMOD keeps one callback a
     * sound and the mod puts its effects on from its own: without it the mod's would take the
     * place of the game's, which says when the power goes off.
     */
    private static List<Patch> hearingPatches() {
        String listener = "fmod/fmod/SoundListener", zoom = "zombie/audio/parameters/ParameterCameraZoom", hearing = HOOKS;
        String emitter = "fmod/fmod/FMODSoundEmitter", occlusion = "zombie/audio/parameters/ParameterOcclusion";
        String set = "(IFFFFFFFFFFFF)V";
        CallSite.Method startEvent = new CallSite.Method(emitter, "startEvent", "(JLzombie/audio/GameSoundClip;Z)V");
        CallSite.Method started = new CallSite.Method(hearing, "eventStarted", "(Lfmod/fmod/FMODSoundEmitter;J)V");
        CallSite.Method setCallback = new CallSite.Method("fmod/javafmod", "FMOD_Studio_EventInstance_SetCallback", "(JLfmod/fmod/FMOD_STUDIO_EVENT_CALLBACK;I)I");
        CallSite.Method calledBack = new CallSite.Method(hearing, "gameCallsBack", "(J)V");
        return List.of(
                new Patch("fmod/javafmod", "FMOD_Studio_EventInstance_SetCallback at its start", classBytes -> CallSite.insertAtStartOfStaticMethod(classBytes, setCallback, code -> {
                    code.lload(0);
                    CallSite.invokeStatic(code, calledBack);
                })),
                new Patch(emitter, "startEvent at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes, startEvent, code -> {
                    code.aload(0);
                    code.lload(1);
                    CallSite.invokeStatic(code, started);
                })),
                new Patch(occlusion, "calculateCurrentValue before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(occlusion, "calculateCurrentValue", "()F"), code -> {
                            code.aload(0);
                            code.getfield(ClassDesc.ofInternalName(occlusion), "emitter", ClassDesc.ofInternalName(emitter));
                            CallSite.invokeStatic(code, new CallSite.Method(hearing, "occluded", "(FL" + emitter + ";)F"));
                        })),
                new Patch(listener, "tick setting the listener", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(listener, "tick", "()V"),
                        new CallSite.Method("fmod/javafmod", "FMOD_Studio_Listener3D", set),
                        new CallSite.Method(hearing, "listener", set), 1)),
                new Patch(zoom, "calculateCurrentValue before its returns", classBytes -> CallSite.filterReturn(classBytes,
                        new CallSite.Method(zoom, "calculateCurrentValue", "()F"),
                        new CallSite.Method(hearing, "zoomedOut", "(F)F"))));
    }

    /**
     * Apart again: without them the picture is the same and is worked out the long way, each
     * chunk in view read through over and over to see whether it has changed. With them the
     * game says which chunks it has changed: what changes a chunk's look goes through the
     * two methods of IsoChunk, and a square's light found changed through the one of
     * FBORenderLevels, all for the game's own chunk textures. The fourth tells of a chunk
     * as its load begins and once it has loaded, since what the game marks as it fills one is
     * not counted while the chunk is not yet loaded. See ChunkMarks.
     */
    static List<Patch> chunkMarkPatches() {
        String chunk = "zombie/iso/IsoChunk", levels = "zombie/iso/fboRenderChunk/FBORenderLevels";
        MethodTypeDesc marked = MethodTypeDesc.ofDescriptor("(L" + chunk + ";J)V"), markedOn = MethodTypeDesc.ofDescriptor("(L" + chunk + ";IJ)V"), lit = MethodTypeDesc.ofDescriptor("(L" + levels + ";IJ)V");
        ClassDesc marks = ClassDesc.ofInternalName("com/cameraunlock/zomboid/ChunkMarks");
        return List.of(
                new Patch(chunk, "invalidateRenderChunkLevel telling which chunk it marks", classBytes -> {
                    refuseChunkMarksIfAsked();
                    return CallSite.insertAtStartOfInstanceMethod(classBytes, new CallSite.Method(chunk, "invalidateRenderChunkLevel", "(IJ)V"),
                            code -> code.aload(0).iload(1).lload(2).invokestatic(marks, "marked", markedOn));
                }),
                new Patch(chunk, "invalidateRenderChunkLevels telling which chunk it marks", classBytes -> {
                    refuseChunkMarksIfAsked();
                    return CallSite.insertAtStartOfInstanceMethod(classBytes, new CallSite.Method(chunk, "invalidateRenderChunkLevels", "(J)V"),
                            code -> code.aload(0).lload(1).invokestatic(marks, "marked", marked));
                }),
                new Patch(levels, "invalidateLevel telling which chunk's light it marks and on which level", classBytes -> {
                    refuseChunkMarksIfAsked();
                    return CallSite.insertAtStartOfInstanceMethod(classBytes, new CallSite.Method(levels, "invalidateLevel", "(IJ)V"),
                            code -> code.aload(0).iload(1).lload(2).invokestatic(marks, "lit", lit));
                }),
                // IsoChunkMap.updateInternal and processAllLoadGridSquare finish each chunk they put on a map in it.
                new Patch(chunk, "doLoadGridsquare telling of the chunk it is loading and has loaded", classBytes -> {
                    refuseChunkMarksIfAsked();
                    CallSite.Method load = new CallSite.Method(chunk, "doLoadGridsquare", "()V");
                    MethodTypeDesc ofChunk = MethodTypeDesc.ofDescriptor("(L" + chunk + ";)V");
                    return CallSite.insertOnEveryWayOut(CallSite.insertAtStartOfInstanceMethod(classBytes, load, code -> code.aload(0).invokestatic(marks, "loading", ofChunk)),
                            load, code -> code.aload(0).invokestatic(marks, "loaded", ofChunk));
                }));
    }

    /**
     * Apart again: without them a set left on, or a door a crowd strikes, marks its chunk many
     * times a second for what only the game's own view draws, and the building it stands in is
     * read again for each. See ChunkMarks.hush and ScreenMarks.marked.
     */
    private static List<Patch> hushedMarkPatches() {
        String object = "zombie/iso/IsoObject", television = "zombie/iso/objects/IsoTelevision";
        CallSite.Method invalidate = new CallSite.Method(object, "invalidateRenderChunkLevel", "(J)V");
        CallSite.Method hush = new CallSite.Method("com/cameraunlock/zomboid/ChunkMarks", "hush", "(L" + object + ";J)V");
        return List.of(
                new Patch(object, "setRenderEffect marking for the game's view alone", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(object, "setRenderEffect", "(Lzombie/iso/objects/RenderEffectType;Z)V"), invalidate, hush, 1)),
                new Patch(object, "removeRenderEffect marking for the game's view alone", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(object, "removeRenderEffect", "(Lzombie/iso/objects/ObjectRenderEffects;)V"), invalidate, hush, 1)),
                new Patch(television, "updateTvScreen marking only what a build shows", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(television, "updateTvScreen", "()V"), new CallSite.Method(television, "invalidateRenderChunkLevel", "(J)V"),
                        new CallSite.Method("com/cameraunlock/zomboid/ScreenMarks", "marked", "(L" + television + ";J)V"), 1)));
    }

    /**
     * Apart again, and each from the other: a window is given its smashed, open or shut sprite
     * in these methods, none of which marks the chunk, since the game draws windows afresh on
     * every frame. Without one of them that change shows when the chunk is next read through,
     * which with the game telling is up to half a second on for a window right by the eye.
     */
    static List<Patch> windowMarkPatches() {
        String window = "zombie/iso/objects/IsoWindow";
        CallSite.Method changed = new CallSite.Method("com/cameraunlock/zomboid/ChunkMarks", "changed", "(Lzombie/iso/IsoObject;)V");
        ArrayList<Patch> patches = new ArrayList<>();
        // Each returns early having changed nothing: Thump for a blow that does not break the window, or that falls on its barricade, ToggleWindow for a
        // window locked for good, broken or barricaded against the character, smashWindow for one already broken, setGlassRemoved for one not broken.
        // Each marks after the one call that follows its new sprite.
        for (String[] method : new String[][] {
                {"Thump", "(Lzombie/iso/IsoMovingObject;I)V", window, "addBrokenGlass", "(Lzombie/iso/IsoMovingObject;)V"},
                {"ToggleWindow", "(Lzombie/characters/IsoGameCharacter;)V", "zombie/iso/IsoGridSquare", "RecalcProperties", "()V"},
                {"smashWindow", "(ZZ)V", "zombie/pathfind/PolygonalMap2", "squareChanged", "(Lzombie/iso/IsoGridSquare;)V"},
                {"setGlassRemoved", "(Z)V", window, "getObjectIndex", "()I"}}) {
            patches.add(new Patch(window, method[0] + " telling of the window's new sprite", classBytes -> CallSite.insertAfterEach(classBytes,
                    new CallSite.Method(window, method[0], method[1]), new CallSite.Method(method[2], method[3], method[4]),
                    code -> CallSite.passThis(code, changed), 1)));
        }
        for (String[] method : new String[][] {{"setSmashed", "(Z)V"}, {"syncIsoObjectReceive", "(Lzombie/core/network/ByteBufferReader;)V"}}) {
            patches.add(new Patch(window, method[0] + " telling of the window's new sprite", classBytes -> CallSite.insertBeforeReturns(classBytes,
                    new CallSite.Method(window, method[0], method[1]), code -> CallSite.passThis(code, changed))));
        }
        return patches;
    }

    /**
     * Apart again, and each from the other: where the game changes what a build of a chunk is
     * made from and marks nothing. Erosion's vines and moss hung on a wall and taken off it, the
     * dirt a story stamps on a floor and the four beside it, the graffiti it sprays, a compost
     * bin filling, a door, a built door or a curtain the game moves without a sound, a plank or
     * metal broken off a barricade, a hearth or a barbecue lit or put out, a body the corpse
     * removal takes away, and a square put on the map where there was none. Without one of
     * them the change waits for the chunk's next sweep (WorldScene.sweptEvery), half a second
     * or more by the eye and six seconds or more far off, unless the chunk is read sooner for
     * something else.
     */
    static List<Patch> unmarkedChangePatches() {
        String marks = "com/cameraunlock/zomboid/ChunkMarks", overlay = "zombie/erosion/obj/ErosionObjOverlay", square = "zombie/iso/IsoGridSquare",
                world = "zombie/randomizedWorld/RandomizedWorldBase", compost = "zombie/iso/objects/IsoCompost";
        CallSite.Method changed = new CallSite.Method(marks, "changed", "(Lzombie/iso/IsoObject;)V");
        CallSite.Method changedOn = new CallSite.Method(marks, "changedOn", "(Lzombie/iso/IsoGridSquare;)V");
        CallSite.Method burning = new CallSite.Method(marks, "burning", "(Lzombie/iso/IsoObject;ZZ)V");
        ArrayList<Patch> patches = new ArrayList<>();
        // Each tells where it changes the sprites attached to the object, its first argument, which neither assigns. setOverlay answers -1 at once for no
        // object or a stage it has not got; removeOverlay answers false for an object with none of it, and also after taking some away by their sprite.
        patches.add(new Patch(overlay, "setOverlay telling of the object's new overlay", classBytes -> CallSite.insertAfterEach(classBytes,
                new CallSite.Method(overlay, "setOverlay", "(Lzombie/iso/IsoObject;IIIF)I"), new CallSite.Method("java/util/ArrayList", "add", "(Ljava/lang/Object;)Z"),
                code -> CallSite.passFirstArgument(code, changed), 1)));
        patches.add(new Patch(overlay, "removeOverlay telling of the overlay taken off", classBytes -> CallSite.insertAfterEach(classBytes,
                new CallSite.Method(overlay, "removeOverlay", "(Lzombie/iso/IsoObject;I)Z"), new CallSite.Method("java/util/ArrayList", "remove", "(I)Ljava/lang/Object;"),
                code -> CallSite.passFirstArgument(code, changed), 2)));
        // It returns at once on sand, dirt or no floor; past that it gives the floor its dirt.
        patches.add(new Patch(square, "dirtStamp telling of the floors it stamps", classBytes -> CallSite.insertAfterEach(classBytes,
                new CallSite.Method(square, "dirtStamp", "()V"), new CallSite.Method("zombie/iso/IsoObject", "setSpriteFromName", "(Ljava/lang/String;)V"),
                code -> CallSite.passThis(code, new CallSite.Method(marks, "stamped", "(Lzombie/iso/IsoGridSquare;)V")), 1)));
        // The square argument is moved to the one south of it for two of the graffiti, before the wall is found: by the call that ends the method it is the square sprayed.
        patches.add(new Patch(world, "graffSquare telling of the graffiti", classBytes -> CallSite.insertAfterEach(classBytes,
                new CallSite.Method(world, "graffSquare", "(Lzombie/iso/IsoGridSquare;Ljava/lang/String;Z)V"),
                new CallSite.Method(square, "RecalcAllWithNeighbours", "(Z)V"), code -> {
                    code.aload(1);
                    CallSite.invokeStatic(code, changedOn);
                }, 1)));
        // A bin is asked for its sprite on every update, and sends the four it can have to a server's clients only when it is given another.
        patches.add(new Patch(compost, "updateSprite telling of the bin's new sprite", classBytes -> CallSite.insertAfterEach(classBytes,
                new CallSite.Method(compost, "updateSprite", "()V"), new CallSite.Method(compost, "transmitUpdatedSpriteToClients", "()V"),
                code -> CallSite.passThis(code, changed), 4)));
        // Each returns at once for one barricaded; past that it has the light worked out again and turns the door.
        for (String door : new String[] {"zombie/iso/objects/IsoDoor", "zombie/iso/objects/IsoThumpable", "zombie/iso/objects/IsoCurtain"}) {
            patches.add(new Patch(door, "ToggleDoorSilent telling of its new sprite", classBytes -> CallSite.insertAfterEach(classBytes,
                    new CallSite.Method(door, "ToggleDoorSilent", "()V"), new CallSite.Method(square, "setRecalcLightTime", "(F)V"),
                    code -> CallSite.passThis(code, changed), 1)));
        }
        // A blow, a zombie's or a weapon's, wears down the barricade's metal sheet, else its metal bars, else a plank, and has its sprite chosen again only as one of them breaks.
        // A server's client is sent what is left of each instead (syncIsoObjectReceive), which it writes and returns once, choosing no sprite.
        String barricade = "zombie/iso/objects/IsoBarricade";
        patches.add(new Patch(barricade, "Damage telling of what it breaks off the barricade", classBytes -> CallSite.insertAfterEach(classBytes,
                new CallSite.Method(barricade, "Damage", "(F)V"), new CallSite.Method(barricade, "chooseSprite", "()V"),
                code -> CallSite.passThis(code, changed), 3)));
        patches.add(new Patch(barricade, "syncIsoObjectReceive telling of what the server says is left of the barricade", classBytes -> CallSite.insertBeforeReturns(classBytes,
                new CallSite.Method(barricade, "syncIsoObjectReceive", "(Lzombie/core/network/ByteBufferReader;)V"), code -> CallSite.passThis(code, changed))));
        for (String fire : new String[] {"zombie/iso/objects/IsoFireplace", "zombie/iso/objects/IsoBarbecue"}) {
            patches.add(new Patch(fire, "setLit telling of a fire lit or put out", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                    new CallSite.Method(fire, "setLit", "(Z)V"), code -> {
                        code.aload(0).aload(0).invokevirtual(ClassDesc.ofInternalName(fire), "isLit", MethodTypeDesc.ofDescriptor("()Z")).iload(1);
                        CallSite.invokeStatic(code, burning);
                    })));
        }
        // Each body taken off the map is counted out here, first thing in IsoDeadBody.removeFromWorld, by where it lay. The corpse removal (IsoDeadBody.updateBodies) takes bodies away with no mark.
        String corpses = "zombie/iso/CorpseCount";
        patches.add(new Patch(corpses, "corpseRemoved telling of the body taken away", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                new CallSite.Method(corpses, "corpseRemoved", "(III)V"), code -> {
                    code.iload(1).iload(2);
                    CallSite.invokeStatic(code, new CallSite.Method(marks, "bodyGone", "(II)V"));
                })));
        // A square the game makes after a chunk's own fill is put on the map here: the build cursor makes one where its level has none (IsoCell.createNewGridSquare), a chunk loading puts them on the edges of the chunks beside it (IsoChunk.loadInMainThread), and IsoGridSquare.addFloor and AddStairs make them too. The square is the first argument, which it does not assign.
        String cell = "zombie/iso/IsoCell";
        patches.add(new Patch(cell, "ConnectNewSquare telling of the square it puts on the map", classBytes -> CallSite.insertBeforeReturns(classBytes,
                new CallSite.Method(cell, "ConnectNewSquare", "(L" + square + ";Z)L" + square + ";"),
                code -> CallSite.passFirstArgument(code, new CallSite.Method(marks, "made", "(L" + square + ";)V")))));
        return patches;
    }

    /** Dev only: has the patches fail as they would on a build of the game they do not fit. */
    private static void refuseChunkMarksIfAsked() {
        if (Boolean.getBoolean("cameraunlock.zomboid.withoutChunkMarks")) {
            throw new IllegalStateException("refused, as cameraunlock.zomboid.withoutChunkMarks asks");
        }
    }

    /**
     * Apart from everything else, and each from the other: what the game does over and over
     * for an answer that does not change. Neither changes an answer, so the game plays as it
     * did, in its own view as in first person. See GameFixes. One that does not fit a build of
     * the game leaves that method as the game has it, and the log says so.
     */
    private static List<Patch> gameFixPatches() {
        String component = "zombie/characters/ecs/ECSComponent", player = "zombie/core/skinnedmodel/animation/AnimationPlayer";
        String character = "zombie/characters/IsoGameCharacter";
        String palette = "zombie/core/skinnedmodel/advancedanimation/AnimatedModel$AnimatedModelInstanceRenderData", matrix = "org/lwjgl/util/vector/Matrix4f";
        return List.of(
                new Patch(component, "getECSClass answered from what it answered for a class before", classBytes -> CallSite.answerOnce(classBytes,
                        new CallSite.Method(component, "getECSClass", "(Ljava/lang/Class;)Ljava/lang/Class;"),
                        new CallSite.Method("com/cameraunlock/zomboid/GameFixes", "knownKind", "(Ljava/lang/Class;)Ljava/lang/Class;"),
                        new CallSite.Method("com/cameraunlock/zomboid/GameFixes", "learnKind", "(Ljava/lang/Class;Ljava/lang/Class;)V"))),
                // What a character is made of is kept in a map by kind, and its state machine is
                // asked for out of that map dozens of times a frame, by each zombie of itself and
                // by every zombie of each one near it as they push apart: with 300 on the screen
                // the looking up was a ninth of the game's logic thread. A character is given
                // its state machine once, when it is made. It is kept beside the map, and let go
                // whenever anything is put in the map or taken out of it.
                new Patch(character, "getStateMachineComponent kept once found", classBytes -> {
                    List<CallSite.Method> changers = List.of(
                            new CallSite.Method(character, "setECSComponent", "(L" + component + ";)V"),
                            new CallSite.Method(character, "removeECSComponent", "(L" + component + ";)V"),
                            new CallSite.Method(character, "removeECSComponent", "(Ljava/lang/Class;)V"));
                    // A changer the game no longer has by that name would be given a method nothing calls, and what is kept would never be let go.
                    for (CallSite.Method changer : changers) {
                        String why = Links.notInherited("zombie/iso/IsoMovingObject", changer.name(), changer.descriptor(), Links::running);
                        if (why != null) {
                            throw new IllegalStateException(changer + " is not inherited as it was: " + why);
                        }
                    }
                    return CallSite.keepInField(classBytes, new CallSite.Method(character, "getStateMachineComponent", "()Lzombie/characters/component/StateMachineComponent;"),
                            "cameraUnlockStateMachine", "zombie/iso/IsoMovingObject", changers);
                }, true),
                // The game asks of every bone of every character on every frame whether the bone is
                // among those hung on another skeleton, by boxing the bone's number, taking a
                // predicate out of a pool that keeps its own in a hash set, and walking the list
                // with it. For all but a few characters the list is empty.
                new Patch(player, "isBoneReparented answered at once while no bone is", classBytes -> CallSite.falseAtOnceWhileEmpty(classBytes,
                        new CallSite.Method(player, "isBoneReparented", "(I)Z"), "reparentedBoneBindings",
                        new CallSite.Method("zombie/util/list/PZArrayUtil", "contains", "(Ljava/util/List;Ljava/util/function/Predicate;)Z"))),
                new Patch(palette, "initMatrixPalette puts each bone in at once", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(palette, "initMatrixPalette", "()V"),
                        new CallSite.Method(matrix, "store", "(Ljava/nio/FloatBuffer;)Lorg/lwjgl/util/vector/Matrix;"),
                        new CallSite.Method("com/cameraunlock/zomboid/BoneMatrices", "store", "(L" + matrix + ";Ljava/nio/FloatBuffer;)Lorg/lwjgl/util/vector/Matrix;"), 1)));
    }

    /**
     * Apart from the other game fixes, and all or nothing: FindItem(String) answered from what a
     * name found before, and each method that changes what a name finds telling GameFixes as it
     * begins and on every way out of it. The rest of ScriptManager's code that changes its
     * modules and their items runs only inside its methods here; ScriptModule's two can be called
     * from Lua as well; and Lua makes an item, or a copy of one, straight into a module. GameFixes
     * keeps nothing unless every one of these is in the game's code, so on a build of the game
     * one of them does not fit, every item is looked up as the game always did, and the log says
     * which did not fit. A patch that fails when its class is transformed again stops the keeping
     * for good before the class is given its new code, as does a start after the game has loaded
     * one of these classes: see ItemLookupGuard.
     */
    static List<Patch> itemLookupPatches() {
        String scripts = "zombie/scripting/ScriptManager", module = "zombie/scripting/objects/ScriptModule", lua = "zombie/Lua/LuaManager$GlobalObject";
        String fixes = "com/cameraunlock/zomboid/GameFixes", item = "Lzombie/scripting/objects/Item;", mode = "Lzombie/scripting/ScriptLoadMode;";
        CallSite.Method changing = new CallSite.Method(fixes, "scriptsChanging", "()V"), changed = new CallSite.Method(fixes, "scriptsChanged", "()V");
        List<Patch> patches = new ArrayList<>();
        patches.add(new Patch(scripts, "FindItem(String) answered from what the name found before", classBytes -> CallSite.replaceEach(classBytes,
                new CallSite.Method(scripts, "FindItem", "(Ljava/lang/String;)" + item),
                new CallSite.Method(scripts, "FindItem", "(Ljava/lang/String;Z)" + item),
                new CallSite.Method(fixes, "findItem", "(L" + scripts + ";Ljava/lang/String;Z)" + item), 1)));
        for (CallSite.Method changer : List.of(
                new CallSite.Method(scripts, "Reset", "()V"),
                new CallSite.Method(scripts, "Load", "()V"),
                new CallSite.Method(scripts, "LoadFile", "(" + mode + "Ljava/lang/String;Z)V"),
                new CallSite.Method(scripts, "ParseScript", "(" + mode + "Ljava/lang/String;)V"),
                new CallSite.Method(scripts, "ReloadScripts", "(Lzombie/scripting/ScriptType;)V"),
                new CallSite.Method(scripts, "ReloadScripts", "(Ljava/util/EnumSet;)V"),
                new CallSite.Method(module, "ParseScriptPP", "(" + mode + "Ljava/lang/String;)V"),
                new CallSite.Method(module, "Reset", "()V"),
                new CallSite.Method(lua, "createNewScriptItem", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)" + item),
                new CallSite.Method(lua, "cloneItemType", "(Ljava/lang/String;Ljava/lang/String;)" + item))) {
            patches.add(new Patch(changer.owner(), changer.name() + changer.descriptor() + " telling GameFixes the scripts change",
                    classBytes -> CallSite.insertAround(classBytes, changer, changing, changed)));
        }
        return patches;
    }

    /** Where the game's own code would go round a loop for ever: see BurntBuildings. Always, in its own view as in first person. */
    private static List<Patch> hangFixPatches() {
        String square = "zombie/iso/IsoGridSquare";
        return List.of(new Patch(square, "BurnWalls takes away what the game would leave", classBytes -> CallSite.replaceEach(classBytes,
                new CallSite.Method(square, "BurnWalls", "(ZZ)V"),
                new CallSite.Method(square, "RemoveTileObject", "(Lzombie/iso/IsoObject;)I"),
                new CallSite.Method("com/cameraunlock/zomboid/BurntBuildings", "takenAway", "(L" + square + ";Lzombie/iso/IsoObject;)I"), 6)));
    }

    /**
     * Apart from the first person view's patches: without these the view still draws, with the
     * game's own skins.
     */
    private static List<Patch> fineSkinPatches() {
        return List.of(
                new Patch(COMBINER, "combine before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(COMBINER, "combine", "(Ljava/util/ArrayList;)" + TEXTURE),
                        code -> CallSite.passFirstArgument(code, new CallSite.Method(FINE_SKINS, "combined",
                                "(" + TEXTURE + "Ljava/util/ArrayList;)" + TEXTURE)))),
                new Patch(COMBINER, "releaseTexture before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(COMBINER, "releaseTexture", "(" + TEXTURE + ")V"),
                        code -> CallSite.passFirstArgument(code, new CallSite.Method(FINE_SKINS, "released", "(" + TEXTURE + ")V")))));
    }

    /**
     * Apart: without them the game makes the clothes' composites in the draw, as it always did,
     * and nothing else is changed. See WaitingComposites. The render thread waits for a frame in
     * SpriteRenderer.waitForReadyState, holding the lock on the frames, and runs the callback each
     * time it wakes without one. The logic thread hands a frame over in pushFrameDown, under the
     * same lock, and any thread wakes the render thread with notifyRenderStateQueue, under it too.
     */
    private static List<Patch> waitingCompositePatches() {
        String renderer = "zombie/core/SpriteRenderer", state = "zombie/core/sprite/SpriteRenderState", thread = "zombie/core/opengl/RenderThread",
                draw = "zombie/core/textures/TextureDraw";
        String composites = "com/cameraunlock/zomboid/WaitingComposites";
        CallSite.Method pushFrameDown = new CallSite.Method(renderer, "pushFrameDown", "()V");
        CallSite.Method notify = new CallSite.Method(renderer, "notifyRenderStateQueue", "()V");
        CallSite.Method callback = new CallSite.Method(thread, "waitForRenderStateCallback", "()Z");
        return List.of(
                new Patch(draw, "drawModel before its return", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(draw, "drawModel", "(L" + draw + ";Lzombie/core/skinnedmodel/ModelManager$ModelSlot;)V"),
                        code -> CallSite.passFirstStaticArgument(code, new CallSite.Method(composites, "readied", "(L" + draw + ";)V")))),
                new Patch(renderer, "pushFrameDown at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes, pushFrameDown,
                        code -> CallSite.invokeStatic(code, new CallSite.Method(composites, "handing", "()V")))),
                new Patch(renderer, "pushFrameDown after the frame is handed over", classBytes -> CallSite.insertAfter(classBytes, pushFrameDown,
                        new CallSite.Method("zombie/core/sprite/SpriteRendererStates", "movePopulatingToReady", "()V"),
                        new CallSite.Method(composites, "handed", "()V"))),
                new Patch(renderer, "notifyRenderStateQueue at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes, notify,
                        code -> CallSite.invokeStatic(code, new CallSite.Method(composites, "notifying", "()V")))),
                new Patch(renderer, "notifyRenderStateQueue after it wakes the render thread", classBytes -> CallSite.insertAfter(classBytes, notify,
                        new CallSite.Method("java/lang/Object", "notifyAll", "()V"),
                        new CallSite.Method(composites, "woken", "()V"))),
                new Patch(state, "clear at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                        new CallSite.Method(state, "clear", "()V"),
                        code -> CallSite.invokeStatic(code, new CallSite.Method(composites, "clearing", "()V")))),
                new Patch(thread, "waitForRenderStateCallback at its start", classBytes -> CallSite.insertAtStartOfStaticMethod(classBytes, callback,
                        code -> CallSite.invokeStatic(code, new CallSite.Method(composites, "calling", "()V")))),
                new Patch(thread, "waitForRenderStateCallback before its return", classBytes -> CallSite.filterReturn(classBytes, callback,
                        new CallSite.Method(composites, "waiting", "(Z)Z"))));
    }

    /** Apart: without it a vehicle's lamps are drawn as paint and the people the game draws itself keep their mouths shut, and nothing else is changed. See VehicleLamps and GameMouths. */
    private static List<Patch> lampPatches() {
        String unit = "zombie/core/opengl/ShaderUnit";
        return List.of(new Patch(unit, "loadShaderFile before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                new CallSite.Method(unit, "loadShaderFile", "(Ljava/lang/String;Ljava/util/ArrayList;)Ljava/lang/String;"),
                code -> CallSite.passFirstArgument(code, new CallSite.Method(HOOKS, "shaderSource", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;")))));
    }

    /** Apart: without it there is no lightning in first person, and nothing else is changed. */
    private static List<Patch> thunderPatches() {
        String storm = "zombie/iso/weather/ThunderStorm";
        return List.of(new Patch(storm, "enqueueThunderEvent telling of a strike", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                new CallSite.Method(storm, "enqueueThunderEvent", "(IIZZZ)V"),
                code -> {
                    code.iload(1);
                    code.iload(2);
                    code.iload(3);
                    code.iload(4);
                    code.iload(5);
                    CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "thunder", "(IIZZZ)V"));
                })),
                new Patch(storm, "enqueueThunderEvent before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(storm, "enqueueThunderEvent", "(IIZZZ)V"),
                        code -> {
                            code.aload(0);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "thunderTold", "(L" + storm + ";)V"));
                        })));
    }

    /**
     * Apart again: without it the weapon in hand is held up all the same, with no kick and no
     * flash. The game starts its own muzzle flash at the one place a firearm's shot is taken.
     */
    private static List<Patch> shotPatches() {
        return List.of(
                new Patch("zombie/CombatManager", "attackCollisionCheck starting the muzzle flash", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method("zombie/CombatManager", "attackCollisionCheck",
                                "(Lzombie/characters/IsoGameCharacter;Lzombie/inventory/types/HandWeapon;Lzombie/ai/states/SwipeStatePlayer;Lzombie/AttackType;)V"),
                        new CallSite.Method("zombie/EffectsManager", "startMuzzleFlash", "(Lzombie/characters/IsoGameCharacter;I)V"),
                        new CallSite.Method(HOOKS, "muzzleFlash", "(Lzombie/EffectsManager;Lzombie/characters/IsoGameCharacter;I)V"), 1)));
    }

    /**
     * Apart again: without them shots leave no mark where they strike. Every firearm's shot, each
     * of a shotgun's pellets and every other player's, is drawn by the game as a tracer to where
     * it ended, by one of two calls: to an end it worked out, or along the aim to the weapon's
     * range. The game's third call, to an end with no square, goes through the first.
     */
    private static List<Patch> impactPatches() {
        String tracers = "zombie/iso/objects/IsoBulletTracerEffects", effect = "L" + tracers + "$Effect;";
        String character = "Lzombie/characters/IsoGameCharacter;", square = "Lzombie/iso/IsoGridSquare;";
        return List.of(
                new Patch(tracers, "addEffect to an end before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(tracers, "addEffect", "(" + character + "FFFF" + square + ")" + effect), code -> {
                            code.aload(1);
                            code.fload(2);
                            code.fload(3);
                            code.fload(4);
                            code.fload(5);
                            code.aload(6);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "tracer", "(" + character + "FFFF" + square + ")V"));
                        })),
                new Patch(tracers, "addEffect to the range before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(tracers, "addEffect", "(" + character + "F)" + effect), code -> {
                            code.aload(1);
                            code.fload(2);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "tracerToRange", "(" + character + "F)V"));
                        })));
    }

    /**
     * Apart again: without it the character's head stays as the game's animations have it.
     * The game poses a skeleton from its animations, twists the spine towards the aim, and
     * only then makes the matrices the model is drawn with. The head is turned before
     * those are made and put back after, so the next frame's pose starts from the game's own.
     */
    private static List<Patch> modelHeadPatches() {
        String player = "zombie/core/skinnedmodel/animation/AnimationPlayer";
        CallSite.Method posing = new CallSite.Method(player, "updateAnimation_StandardAnimation", "(F)V");
        return List.of(new Patch(player, "updateAnimation_StandardAnimation after updateTwistBone", classBytes -> CallSite.insertAfterEach(classBytes,
                posing, new CallSite.Method(player, "updateTwistBone", "()V"),
                code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "pose", "(L" + player + ";)V")), 1)),
                new Patch(player, "updateAnimation_StandardAnimation after updateModelTransforms", classBytes -> CallSite.insertAfterEach(classBytes,
                posing, new CallSite.Method(player, "updateModelTransforms", "()V"),
                code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "posed", "(L" + player + ";)V")), 1)),
                new Patch(player, "updateAnimation_StandardAnimation after UpdateSkinTransforms", classBytes -> CallSite.insertAfterEach(classBytes,
                posing, new CallSite.Method(player, "UpdateSkinTransforms", "()V"),
                code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "skinned", "(L" + player + ";)V")), 1)),
                new Patch(player, "updateAnimation_SharedSkeleTrack after UpdateSkinTransforms", classBytes -> CallSite.insertAfterEach(classBytes,
                new CallSite.Method(player, "updateAnimation_SharedSkeleTrack", "(Lzombie/core/skinnedmodel/animation/sharedskele/SharedSkeleAnimationTrack;FF)V"),
                new CallSite.Method(player, "UpdateSkinTransforms", "()V"),
                code -> CallSite.passThis(code, new CallSite.Method(HOOKS, "skinned", "(L" + player + ";)V")), 1)));
    }

    /**
     * Apart again: without these, on a server, names and speech are written where the game's
     * own view has their owners. The game works out one place on the screen for all it
     * writes over a person, and that place is what is changed.
     */
    private static List<Patch> nameTagPatches() {
        String character = "zombie/characters/IsoGameCharacter", tags = HOOKS;
        String vector = "Lzombie/iso/Vector2;", picker = "zombie/iso/IsoObjectPicker";
        CallSite.Method writing = new CallSite.Method(character, "renderlast", "()V");
        Consumer<CodeBuilder> place = code -> {
            code.aload(0);
            code.getstatic(ClassDesc.ofInternalName(character), "tempVector2_2", ClassDesc.ofDescriptor(vector));
            code.invokestatic(ClassDesc.ofInternalName(tags), "place", MethodTypeDesc.ofDescriptor("(L" + character + ";" + vector + ")V"));
        };
        return List.of(
                new Patch(character, "renderlast after getNameCoords", classBytes -> CallSite.insertAfterEach(classBytes, writing,
                        new CallSite.Method(character, "getNameCoords", "(FFFFFF" + vector + ")V"), place, 1)),
                // In a vehicle the place comes from the vehicle, and the game's own working out is passed over.
                new Patch(character, "renderlast after getNameCoordForPlayer", classBytes -> CallSite.insertAfterEach(classBytes, writing,
                        new CallSite.Method("zombie/vehicles/BaseVehicle", "getNameCoordForPlayer", "(L" + character + ";F" + vector + ")Z"), place, 1)),
                new Patch(character, "renderlast writing the name", classBytes -> CallSite.replaceEach(classBytes, writing,
                        new CallSite.Method("zombie/ui/TextDrawObject", "AddBatchedDraw", "(DDZ)V"),
                        new CallSite.Method(tags, "writeName", "(Lzombie/ui/TextDrawObject;DDZ)V"), 1)),
                new Patch(character, "renderlast writing the speech", classBytes -> CallSite.replaceEach(classBytes, writing,
                        new CallSite.Method("zombie/chat/ChatElement", "renderBatched", "(IIIZ)V"),
                        new CallSite.Method(tags, "writeSpeech", "(Lzombie/chat/ChatElement;IIIZ)V"), 1)),
                new Patch(character, "renderlast asking what is under the pointer", classBytes -> CallSite.replaceEach(classBytes, writing,
                        new CallSite.Method(picker, "ContextPick", "(II)L" + picker + "$ClickObject;"),
                        new CallSite.Method(tags, "underPointer", "(L" + picker + ";II)L" + picker + "$ClickObject;"), 1)));
    }

    /** The game's reticle and aim cursor each read the screen position to draw at from one call, which names the player. */
    private static Patch reticle(String owner, String render, String descriptor, String read, String hook) {
        return instead(new CallSite.Method(owner, render, descriptor), new CallSite.Method(RETICLE, read, INT_FILTER), hook);
    }

    /** Where the game has a player's aim on the screen is passed through a hook with the player's number. */
    private static Patch heldAim(String read, String hook) {
        return new Patch(RETICLE, read + " before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                new CallSite.Method(RETICLE, read, INT_FILTER),
                code -> CallSite.passFirstStaticInt(code, new CallSite.Method(HOOKS, hook, "(II)I"))));
    }

    /** The place on the map the game's Lua is given for a point on a player's screen is passed through a hook with the player's number. */
    private static Patch screenToIso(String method) {
        String globals = "zombie/Lua/LuaManager$GlobalObject";
        return new Patch(globals, method + " before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                new CallSite.Method(globals, method, "(IFFF)F"),
                code -> CallSite.passFirstStaticInt(code, new CallSite.Method(HOOKS, method, "(FI)F"))));
    }

    /** The one call to a static method of the game's inside another goes to a hook that takes what it took. */
    private static Patch instead(CallSite.Method within, CallSite.Method call, String hook) {
        return new Patch(within.owner(), within.name() + " calling " + call.name(), classBytes -> CallSite.replaceEach(classBytes,
                within, call, new CallSite.Method(HOOKS, hook, call.descriptor()), 1));
    }

    /** What a method returns is passed through a hook. */
    private static Patch returning(String owner, String method, String descriptor, String filter, String filterDescriptor) {
        return new Patch(owner, method + " before its returns", classBytes -> CallSite.filterReturn(classBytes,
                new CallSite.Method(owner, method, descriptor), new CallSite.Method(HOOKS, filter, filterDescriptor)));
    }

    /** What a method of an object returns is passed through a hook with the object, which says whose it is. */
    private static Patch returningWithThis(String owner, String method, String descriptor, String filter, String filterDescriptor) {
        return new Patch(owner, method + " before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                new CallSite.Method(owner, method, descriptor),
                code -> CallSite.passThis(code, new CallSite.Method(HOOKS, filter, filterDescriptor))));
    }

    private static Patch after(CallSite.Method within, CallSite.Method call, CallSite.Method hook) {
        return new Patch(within.owner(), within.name() + " after " + call.name(),
                classBytes -> CallSite.insertAfter(classBytes, within, call, hook));
    }

    static final class PatchTransformer implements ClassFileTransformer {
        private final List<Patch> patches;
        private final Consumer<String> lost;
        private final Set<Patch> applied = ConcurrentHashMap.newKeySet();
        /** The classes of these patches that loaded with this transformer added, which a patch may give new members as they are transformed again. */
        private final Set<String> loadedHere = ConcurrentHashMap.newKeySet();
        /** Why the set was left out of the game's code at this start, or null when it went in. */
        private volatile String leftOut;

        PatchTransformer(List<Patch> patches) {
            this(patches, what -> {});
        }

        /** {@code lost} is handed the class and what each patch that fails was for, before transform returns. */
        PatchTransformer(List<Patch> patches, Consumer<String> lost) {
            this.patches = patches;
            this.lost = lost;
        }

        Set<String> owners() {
            Set<String> owners = new HashSet<>();
            patches.forEach(patch -> owners.add(patch.owner()));
            return owners;
        }

        void leaveOut(String why) {
            leftOut = why;
        }

        /** Marks the patches of a class the JVM refused to take back as not in the game's code. */
        void forget(String owner) {
            patches.stream().filter(patch -> patch.owner().equals(owner)).forEach(applied::remove);
        }

        /**
         * What is missing from the game's code: the first patch that is not in
         * it, or null when all of them are. A class that has not loaded yet is
         * loaded here, which runs its patches. The answer can still change
         * later, if another agent in the JVM has a class transformed again and
         * a patch of it fails then, and a caller that keeps its first answer
         * does not hear of that.
         */
        String gap() {
            if (leftOut != null) {
                return leftOut;
            }
            for (Patch patch : patches) {
                String missing = missing(patch);
                if (missing != null) {
                    return missing;
                }
            }
            return null;
        }

        /** The same for patches that each stand apart from the others: every one that is not in the game's code, or null when all of them are. */
        String gaps() {
            if (leftOut != null) {
                return leftOut;
            }
            StringJoiner all = new StringJoiner("; ");
            for (Patch patch : patches) {
                String missing = missing(patch);
                if (missing != null) {
                    all.add(missing);
                }
            }
            return all.length() == 0 ? null : all.toString();
        }

        /** Why a patch is not in the game's code, or null if it is. */
        private String missing(Patch patch) {
            if (applied.contains(patch)) {
                return null;
            }
            try {
                Class.forName(patch.owner().replace('/', '.'), false, PatchTransformer.class.getClassLoader());
            } catch (ClassNotFoundException missing) {
                return "the game has no class " + patch.owner();
            }
            return applied.contains(patch) ? null : patch.owner() + "." + patch.what() + " is not patched";
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> redefined,
                ProtectionDomain domain, byte[] classBytes) {
            byte[] patched = null;
            for (Patch patch : patches) {
                if (!patch.owner().equals(className)) {
                    continue;
                }
                if (redefined != null && patch.addsMembers() && !loadedHere.contains(className)) {
                    Log.info("left out " + className + "." + patch.what() + ": the game loaded the class before these patches were added,"
                            + " and a loaded class cannot be given new fields or methods");
                    continue;
                }
                if (redefined == null) {
                    loadedHere.add(className);
                }
                // The JVM discards whatever a transformer throws and loads the class
                // unchanged, so this is the only place the failure can be reported.
                try {
                    patched = patch.rewrite().apply(patched == null ? classBytes : patched);
                    applied.add(patch);
                    if (DevBuild.ON) {
                        Log.info("patched " + className + "." + patch.what());
                    }
                } catch (Throwable failure) {
                    // When another agent has the class transformed again, what is returned here
                    // replaces its code, unless the JVM refuses the retransformation, so a patch
                    // that went in the first time is out of it now.
                    applied.remove(patch);
                    Log.error("could not patch " + className + "." + patch.what() + ", the method runs unchanged", failure);
                    lost.accept(className + "." + patch.what());
                }
            }
            return patched;
        }
    }
}
