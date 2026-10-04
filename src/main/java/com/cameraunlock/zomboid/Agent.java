package com.cameraunlock.zomboid;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public final class Agent {
    private static final String DISPLAY = "org/lwjglx/opengl/Display";
    private static final String GLFW = "org/lwjgl/glfw/GLFW";
    private static final String WINDOW_PLACEMENT = "com/cameraunlock/zomboid/WindowPlacement";
    private static final String FIRST_PERSON = "com/cameraunlock/zomboid/FirstPerson";
    private static final String GAME_MOUSE = "zombie/input/Mouse";
    private static final String TORCH = "zombie/characters/IsoGameCharacter$TorchInfo";
    private static final String RETICLE = "zombie/input/AimingReticle";
    private static final String CHARACTER_DRAW = "zombie/core/skinnedmodel/model/ModelSlotRenderData";
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

    private static volatile boolean loaded;

    static boolean loaded() {
        return loaded;
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

        Log.info("first person field of view set to " + FieldOfView.degrees() + " degrees vertical");
        HeadTracking.start();

        CallSite.Method mouseUpdate = new CallSite.Method(GAME_MOUSE, "update", "()V");
        instrumentation.addTransformer(new PatchTransformer(List.of(
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
                        new CallSite.Method("com/cameraunlock/zomboid/HeadFollowLight", "direction", VECTOR2_FILTER)))));
        CallSite.Method aiming = new CallSite.Method(PLAYER, "isAiming", "()Z");
        CallSite.Method clickAiming = new CallSite.Method(CONTROLS, "aimingForWorldClicks", BOOLEAN_FILTER);
        CallSite.Method ballistics = new CallSite.Method("zombie/core/physics/BallisticsController", "update", "()V");
        firstPerson = new PatchTransformer(List.of(
                after(new CallSite.Method("zombie/gameStates/IngameState", "renderFrameInternal", "(I)V"),
                        new CallSite.Method("zombie/iso/IsoWorld", "render", "()V"),
                        new CallSite.Method(FIRST_PERSON, "afterWorldRender", "()V")),
                after(new CallSite.Method("zombie/characters/IsoPlayer", "getInputMoveVector", VECTOR2_FILTER),
                        new CallSite.Method("zombie/characters/IsoLivingCharacter", "getInputMoveVector", VECTOR2_FILTER),
                        new CallSite.Method(FIRST_PERSON, "moveVector", VECTOR2_FILTER)),
                new Patch(CHARACTER_DRAW, "render before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(CHARACTER_DRAW, "render", "()V"),
                        code -> CallSite.passThis(code, new CallSite.Method("com/cameraunlock/zomboid/Characters", "drawn",
                                "(L" + CHARACTER_DRAW + ";)V")))),
                reticle("zombie/iso/sprite/IsoReticle", "render", "()V", "getXA", "reticleX"),
                reticle("zombie/iso/sprite/IsoReticle", "render", "()V", "getYA", "reticleY"),
                reticle("zombie/iso/sprite/IsoCursor", "render", "(I)V", "getXA", "reticleX"),
                reticle("zombie/iso/sprite/IsoCursor", "render", "(I)V", "getYA", "reticleY"),
                returning("zombie/characters/component/CharacterInputComponent", "isAimKeyDownInternal", "()Z", "aimKey", BOOLEAN_FILTER),
                returning(UI, "checkPicked", "()Z", "picked", BOOLEAN_FILTER),
                new Patch(UI, "update after isAiming", classBytes -> CallSite.insertAfter(classBytes,
                        new CallSite.Method(UI, "update", "()V"), aiming, clickAiming)),
                // Once where a right press reaches the object under the cursor and once where its release does.
                new Patch(UI, "updateMouseButtons after isAiming", classBytes -> CallSite.insertAfterEach(classBytes,
                        new CallSite.Method(UI, "updateMouseButtons", "(II)I"), aiming, clickAiming, 2)),
                returning(PLAYER, "calculateAimVector", VECTOR2_FILTER, "aimVector", VECTOR2_FILTER),
                after(ballistics, new CallSite.Method(RETICLE, "getX", INT_FILTER), new CallSite.Method(CONTROLS, "aimPointX", INT_FILTER)),
                after(ballistics, new CallSite.Method(RETICLE, "getY", INT_FILTER), new CallSite.Method(CONTROLS, "aimPointY", INT_FILTER)),
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
                })));
        instrumentation.addTransformer(firstPerson);

        // Apart from the first person view's patches: without these the view still draws,
        // with the game's own skins.
        fineSkins = new PatchTransformer(List.of(
                new Patch(COMBINER, "combine before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(COMBINER, "combine", "(Ljava/util/ArrayList;)" + TEXTURE),
                        code -> CallSite.passFirstArgument(code, new CallSite.Method(FINE_SKINS, "combined",
                                "(" + TEXTURE + "Ljava/util/ArrayList;)" + TEXTURE)))),
                new Patch(COMBINER, "releaseTexture before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method(COMBINER, "releaseTexture", "(" + TEXTURE + ")V"),
                        code -> CallSite.passFirstArgument(code, new CallSite.Method(FINE_SKINS, "released", "(" + TEXTURE + ")V"))))));
        instrumentation.addTransformer(fineSkins);

        // Apart again: without it the weapon in hand is held up all the same, with no kick
        // and no flash. The game starts its own muzzle flash at the one place a firearm's
        // shot is taken.
        shot = new PatchTransformer(List.of(
                after(new CallSite.Method("zombie/CombatManager", "attackCollisionCheck",
                                "(Lzombie/characters/IsoGameCharacter;Lzombie/inventory/types/HandWeapon;Lzombie/ai/states/SwipeStatePlayer;Lzombie/AttackType;)V"),
                        new CallSite.Method("zombie/EffectsManager", "startMuzzleFlash", "(Lzombie/characters/IsoGameCharacter;I)V"),
                        new CallSite.Method("com/cameraunlock/zomboid/ViewModel", "shot", "()V"))));
        instrumentation.addTransformer(shot);
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
