package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Keeps the aim mode between runs of the game: the three lines TrueFreeLook, FreeLookMarker
 * and StockSights under [Position] in CameraUnlock.ini, beside the mod's jar. They are the
 * names and the section cameraunlock-core's settings file gives them, so the file reads the
 * same to anything else that follows that format. Every other line of the file is left as it
 * was found.
 */
final class AimModeStore {
    static final String FILE_NAME = "CameraUnlock.ini";
    private static final String SECTION = "Position";
    private static final String[] KEYS = {"TrueFreeLook", "FreeLookMarker", "StockSights"};

    private AimModeStore() {}

    static AimMode load(Path directory) {
        Path file = directory.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return AimMode.SIGHTS_LOCKED;
        }
        try {
            return read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new UncheckedIOException("could not read " + file, failure);
        }
    }

    static void save(Path directory, AimMode mode) {
        Path file = directory.resolve(FILE_NAME);
        try {
            String before = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
            Files.writeString(file, write(before, mode), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("could not write " + file, failure);
        }
    }

    /** The mode a file's text holds. A line that is missing is false, and so is one that is neither true nor false. */
    static AimMode read(String text) {
        boolean[] values = new boolean[KEYS.length];
        boolean inSection = false;
        for (String line : text.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("[")) {
                inSection = sectionOf(trimmed).equalsIgnoreCase(SECTION);
                continue;
            }
            int key = inSection ? keyOf(trimmed) : -1;
            if (key >= 0) {
                String value = trimmed.substring(trimmed.indexOf('=') + 1).strip();
                if (value.equalsIgnoreCase("true")) {
                    values[key] = true;
                } else if (value.equalsIgnoreCase("false")) {
                    values[key] = false;
                } else {
                    Log.info(FILE_NAME + ": " + KEYS[key] + " is \"" + value + "\", which is neither true nor false, and is read as false");
                }
            }
        }
        return AimMode.decode(values[0], values[1], values[2]);
    }

    /** The file's text with the three lines holding the mode, and nothing else changed. */
    static String write(String text, AimMode mode) {
        String ending = text.contains("\r\n") || text.isEmpty() ? "\r\n" : "\n";
        boolean[] values = {mode.trueFreeLook(), mode.freeLookMarker(), mode.stockSights()};
        boolean[] written = new boolean[KEYS.length];
        List<String> lines = new ArrayList<>(text.isEmpty() ? List.of() : List.of(text.split("\\R", -1)));
        if (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        boolean inSection = false;
        int sectionEnds = -1;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).strip();
            if (trimmed.startsWith("[")) {
                inSection = sectionOf(trimmed).equalsIgnoreCase(SECTION);
            } else if (inSection) {
                int key = keyOf(trimmed);
                if (key >= 0) {
                    lines.set(i, KEYS[key] + "=" + values[key]);
                    written[key] = true;
                }
            }
            if (inSection && !trimmed.isEmpty()) {
                sectionEnds = i + 1;
            }
        }
        if (sectionEnds < 0) {
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.add("[" + SECTION + "]");
            sectionEnds = lines.size();
        }
        for (int key = 0; key < KEYS.length; key++) {
            if (!written[key]) {
                lines.add(sectionEnds++, KEYS[key] + "=" + values[key]);
            }
        }
        return String.join(ending, lines) + ending;
    }

    private static String sectionOf(String header) {
        int close = header.indexOf(']');
        return (close < 0 ? header.substring(1) : header.substring(1, close)).strip();
    }

    /** Which of the three a line sets, or -1. */
    private static int keyOf(String trimmed) {
        int equals = trimmed.indexOf('=');
        if (equals < 0 || trimmed.startsWith(";") || trimmed.startsWith("#")) {
            return -1;
        }
        String name = trimmed.substring(0, equals).strip();
        for (int key = 0; key < KEYS.length; key++) {
            if (KEYS[key].equalsIgnoreCase(name)) {
                return key;
            }
        }
        return -1;
    }
}
