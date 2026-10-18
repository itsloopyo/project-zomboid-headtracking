package com.cameraunlock.zomboid.workshop;

import com.cameraunlock.zomboid.Agent;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;

/**
 * The class ZombieBuddy runs for the Workshop package: javaPkgName in its mod.info names this
 * package, and ZombieBuddy calls {@code <javaPkgName>.Main.main} once it has put the mod's jar on
 * the system class path. ZombieBuddy 2.x keeps the JVM's Instrumentation in a package-private
 * static field of its Loader and offers mods no other way to it.
 */
public final class Main {
    private Main() {}

    public static void main(String[] arguments) throws ReflectiveOperationException, IOException {
        try {
            Field field = Class.forName("me.zed_0xff.zombie_buddy.Loader").getDeclaredField("g_instrumentation");
            field.setAccessible(true);
            Agent.startLate((Instrumentation) field.get(null));
        } catch (ReflectiveOperationException | IOException | RuntimeException | Error failure) {
            // ZombieBuddy logs only the InvocationTargetException around this, without its cause,
            // and the mod's own log is not open before Agent.startLate has opened it. The game
            // writes what is printed here to console.txt.
            System.out.println("Project Zomboid Head Tracking could not start from ZombieBuddy: " + failure);
            failure.printStackTrace(System.out);
            throw failure;
        }
    }
}
