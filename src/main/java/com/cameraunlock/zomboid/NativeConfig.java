package com.cameraunlock.zomboid;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * CameraUnlock.ini and the hotkeys it names, through cameraunlock-core. Core is C++ and C#, so
 * its config owner and hotkey poller live in the DLL beside the mod's jar (native/src/bridge.h),
 * and this class is the only thing that calls it. Nothing in the mod reads or writes the file.
 */
final class NativeConfig {
    static final String LIBRARY = "ProjectZomboidHeadTracking.dll";
    static final String FILE_NAME = "CameraUnlock.ini";

    /** The bits of pressed(). */
    static final int TOGGLE = 1, CYCLE_TRACKING_MODE = 2, YAW_MODE = 4, AIM_MODE = 8;

    /** Core's ConfigLoadStatus and ConfigSaveStatus, by number. */
    static final int CANONICAL = 0, CREATED = 2;
    static final int SAVED = 0;
    private static final String[] LOAD_STATUS =
            {"read", "imported", "created", "deferred", "legacy refused", "unreadable"};
    private static final String[] SAVE_STATUS = {"saved", "not saved", "may not be saved"};

    private static final int SETTINGS_BYTES = 80;
    private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
    private static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT;

    private final MethodHandle load, saveTrackingMode, saveWorldSpaceYaw, saveAimMode, hotkeysStart, hotkeysTake,
            takeLog;

    @SuppressWarnings("restricted")
    NativeConfig(Path library) {
        SymbolLookup lookup = SymbolLookup.libraryLookup(library, Arena.global());
        Linker linker = Linker.nativeLinker();
        ValueLayout pointer = ValueLayout.ADDRESS;
        load = bind(linker, lookup, "PzhtConfigLoad", FunctionDescriptor.of(INT, pointer, pointer, pointer));
        saveTrackingMode = bind(linker, lookup, "PzhtConfigSaveTrackingMode", FunctionDescriptor.of(INT, INT, INT));
        saveWorldSpaceYaw = bind(linker, lookup, "PzhtConfigSaveWorldSpaceYaw", FunctionDescriptor.of(INT, INT));
        saveAimMode = bind(linker, lookup, "PzhtConfigSaveAimMode", FunctionDescriptor.of(INT, INT, INT, INT));
        hotkeysStart = bind(linker, lookup, "PzhtHotkeysStart", FunctionDescriptor.of(INT));
        hotkeysTake = bind(linker, lookup, "PzhtHotkeysTake", FunctionDescriptor.of(INT));
        takeLog = bind(linker, lookup, "PzhtTakeLog", FunctionDescriptor.of(INT, pointer, INT));

        int bytes = call(bind(linker, lookup, "PzhtSettingsBytes", FunctionDescriptor.of(INT)));
        if (bytes != SETTINGS_BYTES) {
            throw new IllegalStateException(library + " hands back " + bytes + " bytes of settings and this jar reads "
                    + SETTINGS_BYTES + ": the two are from different builds of the mod");
        }
    }

    @SuppressWarnings("restricted")
    private static MethodHandle bind(Linker linker, SymbolLookup lookup, String name, FunctionDescriptor descriptor) {
        return linker.downcallHandle(lookup.findOrThrow(name), descriptor);
    }

    /** What a load found and the settings the session runs on. */
    record Loaded(int status, Settings settings) {}

    /**
     * Reads the file, creating it where there is none.
     *
     * @param defaults the Defaults.ini to read, or null for the player's own, which is what the game passes
     */
    Loaded load(Path file, Path defaults) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(SETTINGS_BYTES, Integer.BYTES);
            int status = call(load, wide(arena, file), defaults == null ? MemorySegment.NULL : wide(arena, defaults), out);
            writeLog();
            if (status < 0) {
                throw new IllegalStateException(file + " could not be loaded: " + Log.FILE_NAME + " has the reason");
            }
            Log.info(file + ": " + LOAD_STATUS[status]);
            return new Loaded(status, new Settings(
                    out.get(INT, 0), out.get(INT, 4), out.get(INT, 8) != 0, out.get(INT, 12) != 0,
                    out.get(INT, 16) != 0, out.get(INT, 20) != 0, out.get(INT, 24) != 0, out.get(INT, 28) != 0,
                    out.get(INT, 32) != 0, out.get(INT, 36) != 0, out.get(FLOAT, 40), out.get(FLOAT, 44),
                    out.get(FLOAT, 48), out.get(FLOAT, 52), out.get(FLOAT, 56), out.get(FLOAT, 60),
                    out.get(FLOAT, 64), out.get(FLOAT, 68), out.get(FLOAT, 72), out.get(FLOAT, 76)));
        }
    }

    /** Each save answers whether the file holds the new value. The session keeps it either way. */
    boolean saveTrackingMode(boolean rotation, boolean position) {
        return saved("tracking mode", call(saveTrackingMode, rotation ? 1 : 0, position ? 1 : 0));
    }

    boolean saveWorldSpaceYaw(boolean world) {
        return saved("yaw mode", call(saveWorldSpaceYaw, world ? 1 : 0));
    }

    boolean saveAimMode(AimMode mode) {
        return saved("aim mode", call(saveAimMode, mode.trueFreeLook() ? 1 : 0, mode.freeLookMarker() ? 1 : 0,
                mode.stockSights() ? 1 : 0));
    }

    private boolean saved(String what, int status) {
        writeLog();
        if (status < 0) {
            throw new IllegalStateException("the " + what + " could not be saved: " + Log.FILE_NAME + " has the reason");
        }
        Log.info(FILE_NAME + ": " + what + " " + SAVE_STATUS[status]);
        return status == SAVED;
    }

    /** Puts the file's four key lists on core's poller, which runs on a thread of its own. */
    void startHotkeys() {
        int status = call(hotkeysStart);
        writeLog();
        if (status != 0) {
            throw new IllegalStateException("the hotkeys could not be started: " + Log.FILE_NAME + " has the reason");
        }
    }

    /** The actions whose keys went down since the last call. */
    int pressed() {
        try {
            // Asked every frame, so it is called exactly, with nothing boxed.
            return (int) hotkeysTake.invokeExact();
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Core's owner logs nothing itself: it hands its lines back, and they go in the mod's log here. */
    private void writeLog() {
        int length = call(takeLog, MemorySegment.NULL, 0);
        if (length == 0) {
            return;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(length);
            call(takeLog, buffer, length);
            String text = new String(buffer.toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
            text.lines().forEach(Log::info);
        }
    }

    private static MemorySegment wide(Arena arena, Path path) {
        return arena.allocateFrom(path.toString(), StandardCharsets.UTF_16LE);
    }

    private static int call(MethodHandle function, Object... arguments) {
        try {
            return (int) function.invokeWithArguments(arguments);
        } catch (RuntimeException | Error failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException(failure);
        }
    }
}
