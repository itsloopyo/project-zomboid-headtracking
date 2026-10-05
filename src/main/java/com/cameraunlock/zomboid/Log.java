package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class Log {
    static final String FILE_NAME = "ProjectZomboidHeadTracking.log";

    private static final String MOD_PACKAGE = Log.class.getPackageName() + ".";
    private static final Set<String> CAUGHT = ConcurrentHashMap.newKeySet();

    private static PrintWriter out;

    private Log() {}

    static synchronized void open(Path directory) throws IOException {
        if (out == null) {
            // A log that ends without this line is a game that was killed or crashed.
            Runtime.getRuntime().addShutdownHook(new Thread(() -> info("game process ending"), "CameraUnlock-Log"));
        }
        out = new PrintWriter(Files.newBufferedWriter(directory.resolve(FILE_NAME), StandardCharsets.UTF_8), true);
    }

    static synchronized void info(String message) {
        out.println(LocalTime.now().truncatedTo(ChronoUnit.MILLIS) + " " + message);
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
