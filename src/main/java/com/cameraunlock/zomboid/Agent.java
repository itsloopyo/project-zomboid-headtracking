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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class Agent {
    private static final String DISPLAY = "org/lwjglx/opengl/Display";
    private static final String GLFW = "org/lwjgl/glfw/GLFW";
    private static final String WINDOW_PLACEMENT = "com/cameraunlock/zomboid/WindowPlacement";
    private static final String ATLAS = "zombie/core/skinnedmodel/DeadBodyAtlas", BODY_PARAMS = ATLAS + "$BodyParams", RENDER_JOB = ATLAS + "$RenderJob";
    private static final String ANIMATED_MODEL = "zombie/core/skinnedmodel/advancedanimation/AnimatedModel";
    private static final String HOOKS = "com/cameraunlock/zomboid/Hooks";
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

    /** A rewrite of one method of one game class. */
    record Patch(String owner, String what, UnaryOperator<byte[]> rewrite) {}

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
        // Asked several times a frame, and the answer once had does not change: see PatchTransformer.gap.
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

    private static volatile boolean loaded;

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
        if (loaded) {
            // The development run starts an installed copy with -javaagent, and Boot then loads the agent again.
            Log.info("the agent was started a second time: the first start stands");
            return;
        }
        // The offline harness runs the game's classes in the game's folder and keeps the mod's log
        // and CameraUnlock.ini in a folder of its own, named by this property. The game never sets it.
        Path gameDirectory = Path.of(System.getProperty(GAME_DIRECTORY_PROPERTY, "")).toAbsolutePath();
        Log.open(gameDirectory);
        // Only once there is a log: a second start after a first that could not open one has nowhere to say so.
        loaded = true;
        Log.info("Project Zomboid Head Tracking " + Agent.class.getPackage().getImplementationVersion()
                + " loaded, Java " + Runtime.version() + ", game directory " + gameDirectory);
        try {
            start(gameDirectory, instrumentation);
        } catch (IOException | RuntimeException | Error failure) {
            // Boot asks only whether the agent started and runs the game either way, and
            // nothing else puts the reason in this log: it would end here without one.
            Log.error("the mod could not start", failure);
            throw failure;
        }
    }

    private static void start(Path gameDirectory, Instrumentation instrumentation) throws IOException {
        Links.Result links = Links.check();
        if (!links.unresolved().isEmpty()) {
            links.unresolved().forEach(missing -> Log.info("not in this build of the game as the mod uses it: " + missing));
            Log.info("the mod stays out of the game: this build lacks " + links.unresolved().size() + " of the "
                    + links.references() + " things of the game's the mod uses");
            return;
        }
        Log.info("all " + links.references() + " things of the game's the mod uses are in this build");

        Tracking.start(gameDirectory);

        instrumentation.addTransformer(new PatchTransformer(basePatches()));
        firstPerson = new PatchTransformer(firstPersonPatches());
        instrumentation.addTransformer(firstPerson);
        instrumentation.addTransformer(new PatchTransformer(DevPatches.list()));

        fineSkins = new PatchTransformer(fineSkinPatches());
        instrumentation.addTransformer(fineSkins);

        shot = new PatchTransformer(shotPatches());
        instrumentation.addTransformer(shot);

        thunder = new PatchTransformer(thunderPatches());
        instrumentation.addTransformer(thunder);

        lamps = new PatchTransformer(lampPatches());
        instrumentation.addTransformer(lamps);

        impacts = new PatchTransformer(impactPatches());
        instrumentation.addTransformer(impacts);

        modelHead = new PatchTransformer(modelHeadPatches());
        instrumentation.addTransformer(modelHead);

        nameTags = new PatchTransformer(nameTagPatches());
        instrumentation.addTransformer(nameTags);

        if (!Boolean.getBoolean("cameraunlock.zomboid.withoutGameFixes")) {
            instrumentation.addTransformer(new PatchTransformer(gameFixPatches()));
        }
        instrumentation.addTransformer(new PatchTransformer(coveredPatches()));
        instrumentation.addTransformer(new PatchTransformer(farVehiclePatches()));
        instrumentation.addTransformer(new PatchTransformer(leftSavePatches()));
        instrumentation.addTransformer(new PatchTransformer(luaLoadedPatches()));
        instrumentation.addTransformer(new PatchTransformer(knockdownPatches()));
        instrumentation.addTransformer(new PatchTransformer(controllerPatches()));
        chunkMarks = new PatchTransformer(chunkMarkPatches());
        instrumentation.addTransformer(chunkMarks);
        windowMarks = new PatchTransformer(windowMarkPatches());
        instrumentation.addTransformer(windowMarks);
        hearing = new PatchTransformer(hearingPatches());
        instrumentation.addTransformer(hearing);
        splitScreen = new PatchTransformer(splitScreenPatches());
        instrumentation.addTransformer(splitScreen);
    }

    /** The patches for the window's place, the carried light and the log. */
    private static List<Patch> basePatches() {
        return List.of(
                after(new CallSite.Method(DISPLAY, "create", "()V"),
                        new CallSite.Method(GLFW, "glfwSetWindowPos", "(JII)V"),
                        new CallSite.Method(WINDOW_PLACEMENT, "centreNewWindow", "()V")),
                after(new CallSite.Method(DISPLAY, "setDisplayModeAndFullscreenInternal", "(Lorg/lwjglx/opengl/DisplayMode;Z)V"),
                        new CallSite.Method(GLFW, "glfwSetWindowMonitor", "(JJIIIII)V"),
                        new CallSite.Method(WINDOW_PLACEMENT, "centreAfterModeChange", "()V")),
                after(new CallSite.Method(DISPLAY, "setBorderlessWindow", "(Z)V"),
                        new CallSite.Method(GLFW, "glfwSetWindowAttrib", "(JII)V"),
                        new CallSite.Method(WINDOW_PLACEMENT, "centreAfterModeChange", "()V")),
                new Patch(TORCH, "set after getLookVector", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method(TORCH, "set", "(L" + PLAYER + ";Lzombie/inventory/InventoryItem;)L" + TORCH + ";"),
                        new CallSite.Method(PLAYER, "getLookVector", VECTOR2_FILTER),
                        code -> CallSite.passFirstArgument(code, new CallSite.Method(HOOKS, "lightDirection",
                                "(Lzombie/iso/Vector2;L" + PLAYER + ";)Lzombie/iso/Vector2;")), 1)),
                // The one method every exception the game catches or lets a thread end on is logged by.
                new Patch(EXCEPTION_LOGGER, "logException before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(EXCEPTION_LOGGER, "logException",
                                "(Ljava/lang/Throwable;Ljava/lang/String;Ljava/lang/String;Lzombie/debug/DebugType;Lzombie/debug/LogSeverity;)V"),
                        code -> CallSite.passFirstStaticArgument(code, new CallSite.Method("com/cameraunlock/zomboid/Agent", "caughtByTheGame",
                                "(Ljava/lang/Throwable;)V")))));
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
        CallSite.Method applyImpulse = new CallSite.Method("zombie/core/physics/Bullet", "applyImpulse", "(II[F)V");
        CallSite.Method pushAlongTheShot = new CallSite.Method("com/cameraunlock/zomboid/HitImpulse", "apply", "(II[F)V");
        return List.of(
                after(new CallSite.Method("zombie/gameStates/IngameState", "renderFrameInternal", "(I)V"),
                        new CallSite.Method("zombie/iso/IsoWorld", "render", "()V"),
                        new CallSite.Method(HOOKS, "afterWorldRender", "()V")),
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
                    code.aload(2);
                    code.aload(3);
                    CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "shotAt", "(" + character + character + ")V"));
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
                            code.fload(4);
                            code.iload(5);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "zombieHit", "(L" + zombie + ";Lzombie/inventory/types/HandWeapon;FZ)V"));
                        })),
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
                new Patch("zombie/core/textures/SmartTexture", "destroy at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                        new CallSite.Method("zombie/core/textures/SmartTexture", "destroy", "()V"), code -> {
                            code.aload(0);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "keepsTexture", "(Lzombie/core/textures/Texture;)Z"));
                            code.ifThen(java.lang.classfile.Opcode.IFNE, java.lang.classfile.CodeBuilder::return_);
                        })),
                new Patch("zombie/iso/IsoChunk", "Save to a buffer at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                        new CallSite.Method("zombie/iso/IsoChunk", "Save", "(Ljava/nio/ByteBuffer;Ljava/util/zip/CRC32;Z)Ljava/nio/ByteBuffer;"), code -> {
                            code.aload(0);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "chunkSaving", "(Lzombie/iso/IsoChunk;)V"));
                        })),
                new Patch("zombie/iso/IsoChunk", "Save to a buffer before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method("zombie/iso/IsoChunk", "Save", "(Ljava/nio/ByteBuffer;Ljava/util/zip/CRC32;Z)Ljava/nio/ByteBuffer;"), code -> {
                            code.aload(0);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "chunkSaved", "(Lzombie/iso/IsoChunk;)V"));
                        })),
                new Patch("zombie/iso/IsoWorld", "update at its start", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                        new CallSite.Method("zombie/iso/IsoWorld", "update", "()V"),
                        code -> CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "worldUpdate", "()V")))),
                new Patch("zombie/iso/IsoMetaGrid", "processZones before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method("zombie/iso/IsoMetaGrid", "processZones", "()V"), code -> {
                            code.aload(0);
                            CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "zonesProcessed", "(Lzombie/iso/IsoMetaGrid;)V"));
                        })),
                new Patch(RAGDOLL, "simulateHitReaction calling applyImpulse", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(RAGDOLL, "simulateHitReaction", "()V"), applyImpulse, pushAlongTheShot, 1)),
                new Patch(LIVE_NODE, "getBlendInTime before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(LIVE_NODE, "getBlendInTime", "()F"),
                        code -> CallSite.passThis(code, new CallSite.Method("com/cameraunlock/zomboid/Ragdolls", "blendIn", "(FL" + LIVE_NODE + ";)F")))),
                new Patch(LIVE_NODE, "getBlendOutTime before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(LIVE_NODE, "getBlendOutTime", "()F"),
                        code -> CallSite.passThis(code, new CallSite.Method("com/cameraunlock/zomboid/Ragdolls", "blendOut", "(FL" + LIVE_NODE + ";)F")))),
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
                // The outline round a highlighted object (the container the loot panel shows, in
                // the player's object highlight colour) is laid over the frame where the object
                // stands in the game's own view, and not under the picture: it was left floating
                // by the middle of the screen. The picture draws the same outlines round the
                // things themselves: see Outlines. An object taken from the world while outlined
                // is let go of when the game's view next draws.
                new Patch(outline, "object outlines left out under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(outline, "render", "(I)V"), covered)),
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
                        new CallSite.Method(HOOKS, "gamesOwnDrawIsCovered", "()Z"))),
                // The game draws an animal by a program of its own, which the picture leaves to it:
                // the picture's finer skin is handed to that draw once it has taken the model's.
                new Patch(PART_DRAW, "RenderCharacter hands its skin to the picture", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(PART_DRAW, "RenderCharacter", "(L" + CHARACTER_DRAW + ";)V"),
                        new CallSite.Method(MODEL, "DrawChar", "(L" + CHARACTER_DRAW + ";L" + PART_DRAW + ";)V"),
                        new CallSite.Method(HOOKS, "partDrawn", "(L" + MODEL + ";L" + CHARACTER_DRAW + ";L" + PART_DRAW + ";)V"), 1)));
    }

    /**
     * Apart again: without it first person works as it does, and a vehicle comes into the
     * picture where the game's own view would first show it. The game draws the model of a
     * thing that moves only while it is on the game's own screen and on ground the viewer has
     * seen: the picture sees further, and a vehicle within its sight is drawn. A zombie has a
     * model at all only near the game's screen, and from a vehicle the zombies ahead keep
     * theirs and are drawn as well. So does an animal. See FarModels.
     */
    private static List<Patch> farVehiclePatches() {
        String cell = "zombie/iso/fboRenderChunk/FBORenderCell", vehicle = "zombie/vehicles/BaseVehicle";
        String world = "zombie/iso/IsoWorld", zombie = "zombie/characters/IsoZombie";
        return List.of(
                new Patch(cell, "renderMovingObject asks the picture whether a vehicle is in view", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method(cell, "renderMovingObject", "(Lzombie/iso/IsoMovingObject;)V"),
                        new CallSite.Method("zombie/iso/IsoMovingObject", "isOnScreen", "()Z"),
                        new CallSite.Method(HOOKS, "onScreen", "(Lzombie/iso/IsoMovingObject;)Z"), 1)),
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
                new Patch(world, "sceneCullAnimals hands its choice to the picture", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(world, "sceneCullAnimals", "()V"),
                        code -> CallSite.invokeStatic(code, new CallSite.Method(HOOKS, "animalsCulled", "()V")))));
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
                        new CallSite.Method(HOOKS, "plantGivesWay", "()Z"))),
                new Patch(vehicle, "the push of a person and of a body it meets", classBytes -> CallSite.insertAfterEach(
                        CallSite.insertAfterEach(classBytes,
                                new CallSite.Method(vehicle, "applyImpulseFromHitPedestrian", "(Lzombie/characters/IsoGameCharacter;)V"),
                                new CallSite.Method("zombie/characters/IsoGameCharacter", "getMass", "()F"),
                                new CallSite.Method(HOOKS, "pedestrianMass", "(F)F"), 1),
                        new CallSite.Method(vehicle, "applyImpulseFromHitCorpse", "(Lzombie/iso/objects/IsoDeadBody;)V"),
                        new CallSite.Method(vehicle, "getFudgedMass", "()F"),
                        new CallSite.Method(HOOKS, "corpseMass", "(F)F"), 1)));
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
                new Patch(occlusion, "calculateCurrentValue before its returns", classBytes -> CallSite.filterReturn(classBytes,
                        new CallSite.Method(occlusion, "calculateCurrentValue", "()F"),
                        new CallSite.Method(hearing, "occluded", "(F)F"))),
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
     * FBORenderLevels, all for the game's own chunk textures. See ChunkMarks.
     */
    private static List<Patch> chunkMarkPatches() {
        String chunk = "zombie/iso/IsoChunk", levels = "zombie/iso/fboRenderChunk/FBORenderLevels";
        MethodTypeDesc marked = MethodTypeDesc.ofDescriptor("(L" + chunk + ";J)V"), lit = MethodTypeDesc.ofDescriptor("(L" + levels + ";IJ)V");
        ClassDesc marks = ClassDesc.ofInternalName("com/cameraunlock/zomboid/ChunkMarks");
        return List.of(
                new Patch(chunk, "invalidateRenderChunkLevel telling which chunk it marks", classBytes -> {
                    refuseChunkMarksIfAsked();
                    return CallSite.insertAtStartOfInstanceMethod(classBytes, new CallSite.Method(chunk, "invalidateRenderChunkLevel", "(IJ)V"),
                            code -> code.aload(0).lload(2).invokestatic(marks, "marked", marked));
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
                }));
    }

    /**
     * Apart again, and each from the other: a window is given its smashed, open or shut sprite
     * in these methods, none of which marks the chunk, since the game draws windows afresh on
     * every frame. Without one of them that change shows when the chunk is next read through,
     * which with the game telling is up to half a second on for a window right by the eye.
     */
    private static List<Patch> windowMarkPatches() {
        String window = "zombie/iso/objects/IsoWindow";
        CallSite.Method changed = new CallSite.Method("com/cameraunlock/zomboid/ChunkMarks", "changed", "(Lzombie/iso/IsoObject;)V");
        ArrayList<Patch> patches = new ArrayList<>();
        for (String[] method : new String[][] {{"smashWindow", "(ZZ)V"}, {"setSmashed", "(Z)V"}, {"setGlassRemoved", "(Z)V"},
                {"ToggleWindow", "(Lzombie/characters/IsoGameCharacter;)V"}, {"Thump", "(Lzombie/iso/IsoMovingObject;I)V"},
                {"syncIsoObjectReceive", "(Lzombie/core/network/ByteBufferReader;)V"}}) {
            patches.add(new Patch(window, method[0] + " telling of the window's new sprite", classBytes -> CallSite.insertBeforeReturns(classBytes,
                    new CallSite.Method(window, method[0], method[1]), code -> CallSite.passThis(code, changed))));
        }
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
                }),
                // The game asks of every bone of every character on every frame whether the bone is
                // among those hung on another skeleton, by boxing the bone's number, taking a
                // predicate out of a pool that keeps its own in a hash set, and walking the list
                // with it. For all but a few characters the list is empty.
                new Patch(player, "isBoneReparented answered at once while no bone is", classBytes -> CallSite.falseAtOnceWhileEmpty(classBytes,
                        new CallSite.Method(player, "isBoneReparented", "(I)Z"), "reparentedBoneBindings")));
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

    /** Apart: without it a vehicle's lamps are drawn as paint, and nothing else is changed. See VehicleLamps. */
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
        private final Set<Patch> applied = ConcurrentHashMap.newKeySet();

        PatchTransformer(List<Patch> patches) {
            this.patches = patches;
        }

        /**
         * What is missing from the game's code: the first patch that is not in
         * it, or null when all of them are. A class that has not loaded yet is
         * loaded here, which runs its patches, so the answer does not change
         * later.
         */
        String gap() {
            for (Patch patch : patches) {
                if (applied.contains(patch)) {
                    continue;
                }
                try {
                    Class.forName(patch.owner().replace('/', '.'), false, PatchTransformer.class.getClassLoader());
                } catch (ClassNotFoundException missing) {
                    return "the game has no class " + patch.owner();
                }
                if (!applied.contains(patch)) {
                    return patch.owner() + "." + patch.what() + " is not patched";
                }
            }
            return null;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> redefined,
                ProtectionDomain domain, byte[] classBytes) {
            byte[] patched = null;
            for (Patch patch : patches) {
                if (!patch.owner().equals(className)) {
                    continue;
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
                    Log.error("could not patch " + className + "." + patch.what() + ", the method runs unchanged", failure);
                }
            }
            return patched;
        }
    }
}
