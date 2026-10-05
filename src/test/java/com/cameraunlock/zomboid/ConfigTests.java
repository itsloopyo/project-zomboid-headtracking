package com.cameraunlock.zomboid;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
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
        fieldOfViewIsReadAgainWhenTheFileChanges(config, file);
        zoomCompensationIsCores();
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

    private static void fieldOfViewIsReadAgainWhenTheFileChanges(NativeConfig config, Path file) throws IOException {
        float[] degrees = {-1f};
        check(!config.reloadFieldOfView(degrees) && degrees[0] == -1f, "a file nobody has edited is not read again");
        String text = Files.readString(file, StandardCharsets.US_ASCII);
        check(text.contains("\r\nFieldOfView=65.0\r\n"), "the file holds the field of view");
        Files.writeString(file, text.replace("FieldOfView=65.0", "FieldOfView=90"), StandardCharsets.US_ASCII);
        // The watch goes by the file's write time, and two writes can land on one tick of it.
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 2000));
        check(config.reloadFieldOfView(degrees) && degrees[0] == 90f, "an edited field of view is read while the game runs");
        check(!config.reloadFieldOfView(degrees), "and only once");
        check(config.saveAimMode(AimMode.SIGHTS_LOCKED), "a save after it still saves");
        check(Files.readString(file, StandardCharsets.US_ASCII).contains("\r\nFieldOfView=90\r\n"),
                "and leaves the edited field of view in the file");
    }

    /** Core has no Java, so ZoomCompensation is a port of its zoom_compensation.h. This holds the two together. */
    @SuppressWarnings("restricted")
    private static void zoomCompensationIsCores() throws Throwable {
        SymbolLookup lookup = SymbolLookup.libraryLookup(LIBRARY, Arena.global());
        Linker linker = Linker.nativeLinker();
        ValueLayout.OfFloat real = ValueLayout.JAVA_FLOAT;
        MethodHandle factor = linker.downcallHandle(lookup.findOrThrow("PzhtTestFovZoomFactor"),
                FunctionDescriptor.of(real, real, real));
        MethodHandle angle = linker.downcallHandle(lookup.findOrThrow("PzhtTestScaleAngleForZoom"),
                FunctionDescriptor.of(real, real, real));
        MethodHandle lean = linker.downcallHandle(lookup.findOrThrow("PzhtTestScaleLeanForZoom"),
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, real));

        float base = FieldOfView.setTanHalfVertical();
        check(ZoomCompensation.factor(base, base) == (float) factor.invokeExact(base, base)
                && ZoomCompensation.factor(base, base) == 1f, "unzoomed, the factor is core's and exactly 1");
        for (float zoom : new float[] {1f, FieldOfView.AIMED_ZOOM, 0.5f, 0.25f}) {
            float ours = ZoomCompensation.factor(base * zoom, base);
            check(ours == (float) factor.invokeExact(base * zoom, base), "the factor is core's at a zoom of " + zoom);
            for (float degrees : new float[] {-60f, -10f, 0f, 2.5f, 25f, 80f}) {
                float theirs = (float) angle.invokeExact(degrees, ours);
                check(Math.abs(Math.toDegrees(ZoomCompensation.scaleAngle((float) Math.toRadians(degrees), ours)) - theirs) < 1e-3,
                        "an angle of " + degrees + " degrees is scaled as core scales it at a zoom of " + zoom);
            }
            try (Arena arena = Arena.ofConfined()) {
                float[] ourLean = {0.3f, -0.2f, 0.1f};
                float[] axis = {0.6f, 0.64f, 0.48f};
                MemorySegment theirLean = arena.allocateFrom(real, ourLean);
                MemorySegment theirAxis = arena.allocateFrom(real, axis);
                lean.invokeExact(theirLean, theirAxis, ours);
                ZoomCompensation.scaleLean(ourLean, axis[0], axis[1], axis[2], ours);
                for (int i = 0; i < 3; i++) {
                    check(Math.abs(ourLean[i] - theirLean.getAtIndex(real, i)) < 1e-6f,
                            "a lean is split along the view and scaled across it as core does at a zoom of " + zoom);
                }
            }
        }
    }
}
