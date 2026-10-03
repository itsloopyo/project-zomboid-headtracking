package com.cameraunlock.zomboid;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.function.UnaryOperator;

public final class Agent {
    private static final String DISPLAY = "org/lwjglx/opengl/Display";
    private static final String GLFW = "org/lwjgl/glfw/GLFW";
    private static final String WINDOW_PLACEMENT = "com/cameraunlock/zomboid/WindowPlacement";
    private static final String FIRST_PERSON = "com/cameraunlock/zomboid/FirstPerson";
    private static final String GAME_MOUSE = "zombie/input/Mouse";
    private static final String VECTOR2_FILTER = "(Lzombie/iso/Vector2;)Lzombie/iso/Vector2;";

    /** A rewrite of one method of one game class. */
    private record Patch(String owner, String what, UnaryOperator<byte[]> rewrite) {}

    private Agent() {}

    public static void premain(String arguments, Instrumentation instrumentation) throws IOException {
        Path gameDirectory = Path.of("").toAbsolutePath();
        Log.open(gameDirectory);
        Log.info("Project Zomboid Head Tracking " + Agent.class.getPackage().getImplementationVersion()
                + " loaded, Java " + Runtime.version() + ", game directory " + gameDirectory);

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
                after(new CallSite.Method("zombie/gameStates/IngameState", "renderFrameInternal", "(I)V"),
                        new CallSite.Method("zombie/iso/IsoWorld", "render", "()V"),
                        new CallSite.Method(FIRST_PERSON, "afterWorldRender", "()V")),
                after(new CallSite.Method("zombie/characters/IsoPlayer", "getInputMoveVector", VECTOR2_FILTER),
                        new CallSite.Method("zombie/characters/IsoLivingCharacter", "getInputMoveVector", VECTOR2_FILTER),
                        new CallSite.Method(FIRST_PERSON, "moveVector", VECTOR2_FILTER)),
                new Patch(GAME_MOUSE, "update before its returns", classBytes ->
                        CallSite.insertBeforeReturns(classBytes, mouseUpdate, code -> {
                            CallSite.filterStaticInt(code, GAME_MOUSE, "x", new CallSite.Method(FIRST_PERSON, "cursorX", "(I)I"));
                            CallSite.filterStaticInt(code, GAME_MOUSE, "y", new CallSite.Method(FIRST_PERSON, "cursorY", "(I)I"));
                        })))));
    }

    private static Patch after(CallSite.Method within, CallSite.Method call, CallSite.Method hook) {
        return new Patch(within.owner(), within.name() + " after " + call.name(),
                classBytes -> CallSite.insertAfter(classBytes, within, call, hook));
    }

    private static final class PatchTransformer implements ClassFileTransformer {
        private final List<Patch> patches;

        PatchTransformer(List<Patch> patches) {
            this.patches = patches;
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
                    Log.info("patched " + className + "." + patch.what());
                } catch (Throwable failure) {
                    Log.error("could not patch " + className + "." + patch.what() + ", the method runs unchanged", failure);
                }
            }
            return patched;
        }
    }
}
