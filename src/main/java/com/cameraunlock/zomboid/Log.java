package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

final class Log {
    static final String FILE_NAME = "ProjectZomboidHeadTracking.log";

    private static PrintWriter out;

    private Log() {}

    static synchronized void open(Path directory) throws IOException {
        out = new PrintWriter(Files.newBufferedWriter(directory.resolve(FILE_NAME), StandardCharsets.UTF_8), true);
    }

    static synchronized void info(String message) {
        out.println(LocalTime.now().truncatedTo(ChronoUnit.MILLIS) + " " + message);
    }

    static void error(String message, Throwable cause) {
        StringWriter trace = new StringWriter();
        cause.printStackTrace(new PrintWriter(trace, true));
        info("ERROR " + message + System.lineSeparator() + trace);
    }
}
