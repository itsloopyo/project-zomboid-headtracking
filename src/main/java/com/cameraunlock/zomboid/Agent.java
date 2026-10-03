package com.cameraunlock.zomboid;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

public final class Agent {
    private static final String MAIN_SCREEN_STATE = "zombie/gameStates/MainScreenState";
    private static final String DISPLAY = "org/lwjglx/opengl/Display";
    private static final String GLFW = "org/lwjgl/glfw/GLFW";
    private static final String WINDOW_PLACEMENT = "com/cameraunlock/zomboid/WindowPlacement";

    /** A call to {@code hook} to insert after the one call to {@code call} inside {@code within}. */
    private record Hook(CallSite.Method within, CallSite.Method call, CallSite.Method hook) {}

    private Agent() {}

    public static void premain(String arguments, Instrumentation instrumentation) throws IOException {
        Path gameDirectory = Path.of("").toAbsolutePath();
        Log.open(gameDirectory);
        Log.info("Project Zomboid Head Tracking " + Agent.class.getPackage().getImplementationVersion()
                + " loaded, Java " + Runtime.version() + ", game directory " + gameDirectory);

        List<Hook> hooks = new ArrayList<>(List.of(
                new Hook(new CallSite.Method(DISPLAY, "create", "()V"),
                        new CallSite.Method(GLFW, "glfwSetWindowPos", "(JII)V"),
                        new CallSite.Method(WINDOW_PLACEMENT, "centreNewWindow", "()V")),
                new Hook(new CallSite.Method(DISPLAY, "setDisplayModeAndFullscreenInternal", "(Lorg/lwjglx/opengl/DisplayMode;Z)V"),
                        new CallSite.Method(GLFW, "glfwSetWindowMonitor", "(JJIIIII)V"),
                        new CallSite.Method(WINDOW_PLACEMENT, "centreAfterModeChange", "()V")),
                new Hook(new CallSite.Method(DISPLAY, "setBorderlessWindow", "(Z)V"),
                        new CallSite.Method(GLFW, "glfwSetWindowAttrib", "(JII)V"),
                        new CallSite.Method(WINDOW_PLACEMENT, "centreAfterModeChange", "()V"))));

        String renderCheckDirectory = System.getProperty(RenderCheck.PROPERTY);
        if (renderCheckDirectory != null) {
            RenderCheck.outputDirectory = Path.of(renderCheckDirectory);
            hooks.add(new Hook(new CallSite.Method(MAIN_SCREEN_STATE, "render", "()V"),
                    new CallSite.Method(MAIN_SCREEN_STATE, "renderBackground", "()V"),
                    new CallSite.Method("com/cameraunlock/zomboid/RenderCheck", "queue", "()V")));
            Log.info("render check on, captures go to " + RenderCheck.outputDirectory);
        }
        instrumentation.addTransformer(new HookTransformer(hooks));
    }

    private static final class HookTransformer implements ClassFileTransformer {
        private final List<Hook> hooks;

        HookTransformer(List<Hook> hooks) {
            this.hooks = hooks;
        }

        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> redefined,
                ProtectionDomain domain, byte[] classBytes) {
            byte[] hooked = null;
            for (Hook hook : hooks) {
                if (!hook.within().owner().equals(className)) {
                    continue;
                }
                // The JVM discards whatever a transformer throws and loads the class
                // unchanged, so this is the only place the failure can be reported.
                try {
                    hooked = CallSite.insertAfter(hooked == null ? classBytes : hooked, hook.within(), hook.call(), hook.hook());
                    Log.info("hooked " + className + "." + hook.within().name() + " after " + hook.call().name());
                } catch (Throwable failure) {
                    Log.error("could not hook " + className + "." + hook.within().name()
                            + ", the method runs unchanged", failure);
                }
            }
            return hooked;
        }
    }
}
