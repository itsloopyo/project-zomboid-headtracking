package com.cameraunlock.zomboid;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * CameraUnlock.ini through the test build of the mod's DLL, which is the shipped one reading a
 * Defaults.ini the test names, from a scratch folder, so the developer's is neither read nor
 * created.
 */
final class ConfigTests {
    private static final Path LIBRARY =
            Path.of("build-native", "Release", "ProjectZomboidHeadTrackingTest.dll").toAbsolutePath();

    private ConfigTests() {}

    static void run() throws Throwable {
        Path scratch = Files.createTempDirectory("zomboid-headtracking-config");
        Log.open(scratch);
        Path file = scratch.resolve("game").resolve(NativeConfig.FILE_NAME);
        Files.createDirectories(file.getParent());
        Files.createDirectories(scratch.resolve("user"));
        Path defaults = scratch.resolve("user").resolve("CameraUnlock").resolve("Defaults.ini");
        NativeConfig config = new NativeConfig(LIBRARY);
        setDefaultsPath(defaults);

        firstStartCreatesTheCommittedFileAndRunsOnTheBuiltInValues(config, file, defaults);
        changedModesAreSavedAndReadAtTheNextStart(config, file);
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    @SuppressWarnings("restricted")
    private static void setDefaultsPath(Path defaults) throws Throwable {
        try (Arena arena = Arena.ofConfined()) {
            Linker.nativeLinker()
                    .downcallHandle(SymbolLookup.libraryLookup(LIBRARY, Arena.global()).findOrThrow("PzhtTestSetDefaultsPath"),
                            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))
                    .invoke(arena.allocateFrom(defaults.toString(), StandardCharsets.UTF_16LE));
        }
    }

    private static void firstStartCreatesTheCommittedFileAndRunsOnTheBuiltInValues(NativeConfig config, Path file,
            Path defaults) throws IOException {
        NativeConfig.Loaded loaded = config.load(file);
        check(loaded.status() == NativeConfig.CREATED, "a first start with no file creates it");
        check(loaded.settings().equals(Settings.BUILT_IN), "Settings.BUILT_IN is what core gives a first start: " + loaded.settings());
        check(Arrays.equals(Files.readAllBytes(file), Files.readAllBytes(Path.of("config", NativeConfig.FILE_NAME))),
                "the file a first start creates is config/CameraUnlock.ini byte for byte");
        check(Files.exists(defaults), "and the Defaults.ini it was pointed at is created");
    }

    private static void changedModesAreSavedAndReadAtTheNextStart(NativeConfig config, Path file) throws IOException {
        check(config.saveTrackingMode(true, false), "the tracking mode saves");
        check(config.saveWorldSpaceYaw(false), "the yaw mode saves");
        check(config.saveAimMode(AimMode.STOCK_SIGHTS), "the aim mode saves");
        String text = Files.readString(file, StandardCharsets.US_ASCII);
        for (String line : new String[] {"RotationEnabled=true", "PositionEnabled=false", "WorldSpaceYaw=false",
                "StockSights=true", "UdpPort=default"}) {
            check(text.contains("\r\n" + line + "\r\n"), "after the saves the file holds " + line);
        }

        NativeConfig.Loaded loaded = config.load(file);
        Settings settings = loaded.settings();
        check(loaded.status() == NativeConfig.CANONICAL, "the saved file is read at the next start");
        check(settings.rotationEnabled() && !settings.positionEnabled() && !settings.worldSpaceYaw(),
                "which runs on the saved tracking and yaw modes");
        check(AimMode.decode(settings.trueFreeLook(), settings.freeLookMarker(), settings.stockSights()) == AimMode.STOCK_SIGHTS,
                "and the saved aim mode");
        check(settings.udpPort() == Settings.BUILT_IN.udpPort(), "a row no save touched keeps its value");
    }
}
