package com.cameraunlock.zomboid;

import java.io.IOException;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class Agent {
    private static final String DISPLAY = "org/lwjglx/opengl/Display";
    private static final String GLFW = "org/lwjgl/glfw/GLFW";
    private static final String WINDOW_PLACEMENT = "com/cameraunlock/zomboid/WindowPlacement";
    private static final String FIRST_PERSON = "com/cameraunlock/zomboid/FirstPerson";
    private static final String GAME_MOUSE = "zombie/input/Mouse";
    private static final String TORCH = "zombie/characters/IsoGameCharacter$TorchInfo";
    private static final String EXCEPTION_LOGGER = "zombie/core/logger/ExceptionLogger";
    private static final String RETICLE = "zombie/input/AimingReticle";
    private static final String CHARACTER_DRAW = "zombie/core/skinnedmodel/model/ModelSlotRenderData";
    private static final String PART_DRAW = "zombie/core/skinnedmodel/model/ModelInstanceRenderData";
    private static final String COMBINER = "zombie/core/textures/TextureCombiner";
    private static final String TEXTURE = "Lzombie/core/textures/Texture;";
    private static final String FINE_SKINS = "com/cameraunlock/zomboid/FineSkins";
    private static final String CONTROLS = "com/cameraunlock/zomboid/Controls";
    private static final String PLAYER = "zombie/characters/IsoPlayer";
    private static final String UI = "zombie/ui/UIManager";
    private static final String KEY_BINDING = "Lzombie/core/Core$KeyBinding;";
    private static final String INT_FILTER = "(I)I";
    private static final String BOOLEAN_FILTER = "(Z)Z";
    private static final String VECTOR2_FILTER = "(Lzombie/iso/Vector2;)Lzombie/iso/Vector2;";

    /** A rewrite of one method of one game class. */
    record Patch(String owner, String what, UnaryOperator<byte[]> rewrite) {}

    /** The patches the first person view is made of. */
    private static PatchTransformer firstPerson;
    private static volatile boolean firstPersonPatched;

    private Agent() {}

    /**
     * Why the first person view cannot run on this build of the game, or null
     * once every patch it is made of is in the game's code. With only some of
     * them in, the view would draw while the keys, the mouse or the reticle
     * still worked the game's own way.
     */
    static String firstPersonGap() {
        if (firstPersonPatched) {
            return null;
        }
        String gap = firstPerson.gap();
        firstPersonPatched = gap == null;
        return gap;
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

    private static volatile boolean loaded;

    static boolean loaded() {
        return loaded;
    }

    /** Called where the game has just logged an exception of any kind. */
    public static void caughtByTheGame(Throwable thrown) {
        Log.caughtByTheGame(thrown);
    }

    /** Called by the JVM when Boot loads the agent into it, which is how an installed copy starts. */
    public static void agentmain(String arguments, Instrumentation instrumentation) throws IOException {
        premain(arguments, instrumentation);
    }

    /** Called by the JVM for -javaagent, which is how the development run starts. */
    public static void premain(String arguments, Instrumentation instrumentation) throws IOException {
        loaded = true;
        Path gameDirectory = Path.of("").toAbsolutePath();
        Log.open(gameDirectory);
        Log.info("Project Zomboid Head Tracking " + Agent.class.getPackage().getImplementationVersion()
                + " loaded, Java " + Runtime.version() + ", game directory " + gameDirectory);
        try {
            start(gameDirectory, instrumentation);
        } catch (IOException | RuntimeException | Error failure) {
            // Boot asks only whether the agent was loaded and starts the game either way, and
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

        HeadTracking.start(gameDirectory);

        instrumentation.addTransformer(new PatchTransformer(basePatches()));
        firstPerson = new PatchTransformer(firstPersonPatches());
        instrumentation.addTransformer(firstPerson);
        instrumentation.addTransformer(new PatchTransformer(DevPatches.list()));

        fineSkins = new PatchTransformer(fineSkinPatches());
        instrumentation.addTransformer(fineSkins);

        shot = new PatchTransformer(shotPatches());
        instrumentation.addTransformer(shot);

        modelHead = new PatchTransformer(modelHeadPatches());
        instrumentation.addTransformer(modelHead);

        nameTags = new PatchTransformer(nameTagPatches());
        instrumentation.addTransformer(nameTags);

        if (!Boolean.getBoolean("cameraunlock.zomboid.withoutGameFixes")) {
            instrumentation.addTransformer(new PatchTransformer(gameFixPatches()));
        }
        instrumentation.addTransformer(new PatchTransformer(coveredPatches()));
        instrumentation.addTransformer(new PatchTransformer(leftSavePatches()));
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
                after(new CallSite.Method(TORCH, "set", "(Lzombie/characters/IsoPlayer;Lzombie/inventory/InventoryItem;)L" + TORCH + ";"),
                        new CallSite.Method("zombie/characters/IsoPlayer", "getLookVector", VECTOR2_FILTER),
                        new CallSite.Method("com/cameraunlock/zomboid/HeadFollowLight", "direction", VECTOR2_FILTER)),
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
        CallSite.Method clickAiming = new CallSite.Method(CONTROLS, "aimingForWorldClicks", BOOLEAN_FILTER);
        CallSite.Method ballistics = new CallSite.Method("zombie/core/physics/BallisticsController", "update", "()V");
        return List.of(
                after(new CallSite.Method("zombie/gameStates/IngameState", "renderFrameInternal", "(I)V"),
                        new CallSite.Method("zombie/iso/IsoWorld", "render", "()V"),
                        new CallSite.Method(FIRST_PERSON, "afterWorldRender", "()V")),
                after(new CallSite.Method("zombie/characters/IsoPlayer", "getInputMoveVector", VECTOR2_FILTER),
                        new CallSite.Method("zombie/characters/IsoLivingCharacter", "getInputMoveVector", VECTOR2_FILTER),
                        new CallSite.Method(FIRST_PERSON, "moveVector", VECTOR2_FILTER)),
                new Patch("org/lwjglx/opengl/Display", "updateMouseCursor left alone while the look has the mouse",
                        classBytes -> CallSite.returnAtOnceIf(classBytes, new CallSite.Method("org/lwjglx/opengl/Display", "updateMouseCursor", "()V"),
                                new CallSite.Method(FIRST_PERSON, "holdsMouse", "()Z"))),
                after(new CallSite.Method("zombie/iso/LightingJNI", "updatePlayer", "(I)V"),
                        new CallSite.Method("zombie/characters/IsoPlayer", "getLookVector", VECTOR2_FILTER),
                        new CallSite.Method(FIRST_PERSON, "sightVector", VECTOR2_FILTER)),
                new Patch(CHARACTER_DRAW, "render before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(CHARACTER_DRAW, "render", "()V"),
                        code -> CallSite.passThis(code, new CallSite.Method("com/cameraunlock/zomboid/Characters", "drawn",
                                "(L" + CHARACTER_DRAW + ";)V")))),
                reticle("zombie/iso/sprite/IsoReticle", "render", "()V", "getXA", "reticleX"),
                reticle("zombie/iso/sprite/IsoReticle", "render", "()V", "getYA", "reticleY"),
                reticle("zombie/iso/sprite/IsoCursor", "render", "(I)V", "getXA", "reticleX"),
                reticle("zombie/iso/sprite/IsoCursor", "render", "(I)V", "getYA", "reticleY"),
                returning("zombie/characters/component/CharacterInputComponent", "isAimKeyDownInternal", "()Z", "aimKey", BOOLEAN_FILTER),
                returning("zombie/characters/component/CharacterInputComponent", "isInteractButtonDownInternal", "()Z", "interactKey", BOOLEAN_FILTER),
                returning(UI, "checkPicked", "()Z", "picked", BOOLEAN_FILTER),
                new Patch(UI, "update after isAiming", classBytes -> CallSite.insertAfter(classBytes,
                        new CallSite.Method(UI, "update", "()V"), aiming, clickAiming)),
                // Once where a right press reaches the object under the cursor and once where its release does.
                new Patch(UI, "updateMouseButtons after isAiming", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method(UI, "updateMouseButtons", "(II)I"), aiming, clickAiming, 2)),
                returning(PLAYER, "calculateAimVector", VECTOR2_FILTER, "aimVector", VECTOR2_FILTER),
                after(ballistics, new CallSite.Method(RETICLE, "getX", INT_FILTER), new CallSite.Method(CONTROLS, "aimPointX", INT_FILTER)),
                after(ballistics, new CallSite.Method(RETICLE, "getY", INT_FILTER), new CallSite.Method(CONTROLS, "aimPointY", INT_FILTER)),
                new Patch(ballistics.owner(), "update after calculateMuzzlePosition", classBytes -> CallSite.insertAfterEach(classBytes,
                        ballistics, new CallSite.Method(ballistics.owner(), "calculateMuzzlePosition", "(Lzombie/iso/Vector3;Lzombie/iso/Vector3;)F"),
                        code -> CallSite.passThis(code, new CallSite.Method(CONTROLS, "muzzle", "(L" + ballistics.owner() + ";)V")), 1)),
                new Patch("zombie/core/Core", "getKeyBinding before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method("zombie/core/Core", "getKeyBinding", "(Ljava/lang/String;)" + KEY_BINDING),
                        code -> CallSite.passFirstArgument(code, new CallSite.Method(CONTROLS, "binding",
                                "(" + KEY_BINDING + "Ljava/lang/String;)" + KEY_BINDING)))),
                new Patch(GAME_MOUSE, "update before its returns", classBytes -> {
                    CallSite.requireStaticInt(classBytes, "x");
                    CallSite.requireStaticInt(classBytes, "y");
                    return CallSite.insertBeforeReturns(classBytes, mouseUpdate, code -> {
                        CallSite.filterStaticInt(code, GAME_MOUSE, "x", new CallSite.Method(FIRST_PERSON, "cursorX", "(I)I"));
                        CallSite.filterStaticInt(code, GAME_MOUSE, "y", new CallSite.Method(FIRST_PERSON, "cursorY", "(I)I"));
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
        CallSite.Method covered = new CallSite.Method(FIRST_PERSON, "coversTheGamesView", "()Z");
        return List.of(
                new Patch(world, "renderWeatherFX left out under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(world, "renderWeatherFX", "()V"), covered)),
                new Patch(cell, "renderPuddles left out under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(cell, "renderPuddles", "(I)V"), covered)),
                new Patch(cell, "renderRainSplashes left out under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(cell, "renderRainSplashes", "(II)V"), covered)),
                new Patch(cell, "renderFog left out under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(cell, "renderFog", "(I)V"), covered)),
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
                // The game's own draw of each part of each person into its own view, which the
                // picture then covers: the picture draws every one of them again itself, by this
                // same method. Everything round the draw runs as it did: where the model's
                // camera is stood, which the picture reads, and what says a model was shown.
                new Patch(PART_DRAW, "RenderCharacter left out under the first person picture", classBytes -> CallSite.returnAtOnceIf(classBytes,
                        new CallSite.Method(PART_DRAW, "RenderCharacter", "(L" + CHARACTER_DRAW + ";)V"),
                        new CallSite.Method("com/cameraunlock/zomboid/Characters", "gamesOwnDrawIsCovered", "()Z"))));
    }

    /**
     * Apart again: without it first person works as it does, and what it holds on the graphics
     * card when a save is left for the menu stays held until another save is played.
     */
    private static List<Patch> leftSavePatches() {
        String state = "zombie/gameStates/IngameState";
        return List.of(new Patch(state, "exit before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                new CallSite.Method(state, "exit", "()V"),
                code -> CallSite.passThis(code, new CallSite.Method(FIRST_PERSON, "leftSave", "(Ljava/lang/Object;)V")))));
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
                new Patch(component, "getECSClass answered from what a class's kind was found to be", classBytes -> CallSite.answerAtOnceUnlessNull(classBytes,
                        new CallSite.Method(component, "getECSClass", "(Ljava/lang/Class;)Ljava/lang/Class;"),
                        new CallSite.Method("com/cameraunlock/zomboid/GameFixes", "componentKind", "(Ljava/lang/Class;)Ljava/lang/Class;"))),
                // What a character is made of is kept in a map by kind, and its state machine is
                // asked for out of that map dozens of times a frame, by each zombie of itself and
                // by every zombie of each one near it as they push apart: with 300 on the screen
                // the looking up was a ninth of the game's logic thread. A character is given
                // its state machine once, when it is made. It is kept beside the map, and let go
                // whenever anything is put in the map or taken out of it.
                new Patch(character, "getStateMachineComponent kept once found", classBytes -> CallSite.keepInField(classBytes,
                        new CallSite.Method(character, "getStateMachineComponent", "()Lzombie/characters/component/StateMachineComponent;"),
                        "cameraUnlockStateMachine", "zombie/iso/IsoMovingObject", List.of(
                                new CallSite.Method(character, "setECSComponent", "(L" + component + ";)V"),
                                new CallSite.Method(character, "removeECSComponent", "(L" + component + ";)V"),
                                new CallSite.Method(character, "removeECSComponent", "(Ljava/lang/Class;)V")))),
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

    /**
     * Apart again: without it the weapon in hand is held up all the same, with no kick and no
     * flash. The game starts its own muzzle flash at the one place a firearm's shot is taken.
     */
    private static List<Patch> shotPatches() {
        return List.of(
                after(new CallSite.Method("zombie/CombatManager", "attackCollisionCheck",
                                "(Lzombie/characters/IsoGameCharacter;Lzombie/inventory/types/HandWeapon;Lzombie/ai/states/SwipeStatePlayer;Lzombie/AttackType;)V"),
                        new CallSite.Method("zombie/EffectsManager", "startMuzzleFlash", "(Lzombie/characters/IsoGameCharacter;I)V"),
                        new CallSite.Method("com/cameraunlock/zomboid/ViewModel", "shot", "()V")));
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
                code -> CallSite.passThis(code, new CallSite.Method("com/cameraunlock/zomboid/ModelHead", "pose", "(L" + player + ";)V")), 1)),
                new Patch(player, "updateAnimation_StandardAnimation after updateModelTransforms", classBytes -> CallSite.insertAfterEach(classBytes,
                posing, new CallSite.Method(player, "updateModelTransforms", "()V"),
                code -> CallSite.passThis(code, new CallSite.Method("com/cameraunlock/zomboid/ModelHead", "posed", "(L" + player + ";)V")), 1)));
    }

    /**
     * Apart again: without these, on a server, names and speech are written where the game's
     * own view has their owners. The game works out one place on the screen for all it
     * writes over a person, and that place is what is changed.
     */
    private static List<Patch> nameTagPatches() {
        String character = "zombie/characters/IsoGameCharacter", tags = "com/cameraunlock/zomboid/NameTags";
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

    /** The game's reticle and aim cursor each read the screen position to draw at from one call. */
    private static Patch reticle(String owner, String render, String descriptor, String read, String filter) {
        return after(new CallSite.Method(owner, render, descriptor), new CallSite.Method(RETICLE, read, INT_FILTER),
                new CallSite.Method(FIRST_PERSON, filter, INT_FILTER));
    }

    /** What a method returns is passed through one of the hooks in Controls. */
    private static Patch returning(String owner, String method, String descriptor, String filter, String filterDescriptor) {
        return new Patch(owner, method + " before its returns", classBytes -> CallSite.filterReturn(classBytes,
                new CallSite.Method(owner, method, descriptor), new CallSite.Method(CONTROLS, filter, filterDescriptor)));
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
                    Log.info("patched " + className + "." + patch.what());
                } catch (Throwable failure) {
                    Log.error("could not patch " + className + "." + patch.what() + ", the method runs unchanged", failure);
                }
            }
            return patched;
        }
    }
}
