package com.cameraunlock.zomboid;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.security.ProtectionDomain;

public final class Agent {
    private static final String MAIN_SCREEN_STATE = "zombie/gameStates/MainScreenState";

    private Agent() {}

    public static void premain(String arguments, Instrumentation instrumentation) throws IOException {
        Path gameDirectory = Path.of("").toAbsolutePath();
        Log.open(gameDirectory);
        Log.info("Project Zomboid Head Tracking " + Agent.class.getPackage().getImplementationVersion()
                + " loaded, Java " + Runtime.version() + ", game directory " + gameDirectory);

        String renderCheckDirectory = System.getProperty(RenderCheck.PROPERTY);
        if (renderCheckDirectory != null) {
            RenderCheck.outputDirectory = Path.of(renderCheckDirectory);
            instrumentation.addTransformer(new MenuHook());
            Log.info("render check on, captures go to " + RenderCheck.outputDirectory);
        }
    }

    private static final class MenuHook implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> redefined,
                ProtectionDomain domain, byte[] classBytes) {
            if (!MAIN_SCREEN_STATE.equals(className)) {
                return null;
            }
            // The JVM discards whatever a transformer throws and loads the class
            // unchanged, so this is the only place the failure can be reported.
            try {
                byte[] hooked = CallSite.insertAfter(classBytes,
                        new CallSite.Method(MAIN_SCREEN_STATE, "render", "()V"),
                        new CallSite.Method(MAIN_SCREEN_STATE, "renderBackground", "()V"),
                        new CallSite.Method("com/cameraunlock/zomboid/RenderCheck", "queue", "()V"));
                Log.info("hooked " + className + ".render after renderBackground");
                return hooked;
            } catch (Throwable failure) {
                Log.error("could not hook " + className + ", the class loads unchanged", failure);
                return null;
            }
        }
    }
}
