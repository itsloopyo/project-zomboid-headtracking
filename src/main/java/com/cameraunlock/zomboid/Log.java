package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mod's log, ProjectZomboidHeadTracking.log in the game's folder. It is cameraunlock-core's
 * file log, so the lines of the mod's Java and the lines of core's receiver, config owner and
 * hotkeys are in one file in the order they happened. Core keeps the run before as
 * ProjectZomboidHeadTracking.prev.log.
 */
final class Log {
    static final String FILE_NAME = "ProjectZomboidHeadTracking.log";

    private static final String MOD_PACKAGE = Log.class.getPackageName() + ".";
    private static final Set<String> CAUGHT = ConcurrentHashMap.newKeySet();

    private static CameraUnlock core;

    private Log() {}

    /**
     * Loads cameraunlock-core and opens its log. This is the first thing the mod does, so a core
     * that cannot load (its module missing from the jar, or of another build) is found here, where
     * core's log cannot be written: the reason is then written to the file from here, and it is all
     * the file holds.
     */
    static synchronized void open(Path directory) throws IOException {
        Path file = directory.resolve(FILE_NAME);
        try {
            core = Core.library();
            core.logOpen(file);
        } catch (RuntimeException | Error failure) {
            // An Error too: a jar without core's module throws NoClassDefFoundError, and the game would start with nothing in the file to say why.
            StringWriter trace = new StringWriter();
            failure.printStackTrace(new PrintWriter(trace, true));
            Files.writeString(file, LocalTime.now().truncatedTo(ChronoUnit.MILLIS) + " ERROR Project Zomboid Head Tracking could not start,"
                    + " and the game runs without it: " + failure.getMessage() + System.lineSeparator() + trace);
            throw failure;
        }
        // A log that ends without this line is a game that was killed or crashed.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> info("game process ending"), "CameraUnlock-Log"));
    }

    static void info(String message) {
        core.logWrite(message);
    }

    /**
     * Writes an exception the game has caught if any of it came through the mod. The game
     * puts it in its own console.txt, which a report with this log alone would not have.
     * A hook that throws does so every frame, so each fault is written the first time only:
     * there are no more of them than there are lines of the mod that can throw.
     */
    static void caughtByTheGame(Throwable thrown) {
        for (Throwable each = thrown; each != null; each = each.getCause()) {
            for (StackTraceElement frame : each.getStackTrace()) {
                if (frame.getClassName().startsWith(MOD_PACKAGE)) {
                    if (CAUGHT.add(each.getClass().getName() + " at " + frame)) {
                        error("the game caught this from the mod at " + frame + ". It is written here once however often it comes", thrown);
                    }
                    return;
                }
            }
        }
    }

    static void error(String message, Throwable cause) {
        StringWriter trace = new StringWriter();
        cause.printStackTrace(new PrintWriter(trace, true));
        info("ERROR " + message + System.lineSeparator() + trace);
    }
}
