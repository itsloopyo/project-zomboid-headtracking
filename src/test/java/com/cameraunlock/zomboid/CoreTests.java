package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * What the mod does with cameraunlock-core, through the module the build puts in the classes:
 * the rows of CameraUnlock.ini, what each hotkey's action does and saves, and the tracker's pose
 * taken to this game's view. Real OpenTrack datagrams go to a loopback port, and
 * the settings file and Defaults.ini are in a scratch folder under build, so the developer's own
 * are neither read nor written.
 *
 * It is a process of its own because core reads one settings file a process, as a game does. A
 * later start of the game is this class run again with "start": see started.
 *
 * Every check runs, and the tally is printed once at the end.
 */
public final class CoreTests {
    private static final FieldOfView fieldOfView = new FieldOfView();
    private static final HeadFollowLight headFollowLight = new HeadFollowLight();
    private static final HeadTracking headTracking = new HeadTracking(0, fieldOfView, headFollowLight);

    /**
     * A port the system says nothing holds, asked for just before core listens on it. Not 4242,
     * where a tracker may be sending, and not one fixed number: another run of these tests on
     * the same machine held it, and core then waited for the port while the poses sent here
     * went to the other run.
     */
    private static int port;
    private static final float ASPECT = 16f / 9f;
    private static final float FRAME_SECONDS = 0.005f;

    private static Path scratch, game, defaults, file;

    private CoreTests() {}

    /** The game's own main class in theShippedJarStartsThroughBoot: it loads one class the mod patches. */
    public static final class Game {
        private Game() {}

        public static void main(String[] arguments) {
            System.out.println("the game ran");
        }
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length > 0 && arguments[0].equals("start")) {
            Log.open(Path.of(arguments[3]));
            Tracking.open(Path.of(arguments[1]), Path.of(arguments[2]));
            System.out.println(started());
            return;
        }
        Checks.suite = "core tests";
        scratch = Path.of("build", "core-test-run").toAbsolutePath();
        if (Files.exists(scratch)) {
            // What the run before left: its log was open until that process ended.
            try (Stream<Path> left = Files.walk(scratch)) {
                for (Path each : left.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(each);
                }
            }
        }
        game = Files.createDirectories(scratch.resolve("game"));
        defaults = Files.createDirectories(scratch.resolve("user").resolve("CameraUnlock")).resolve("Defaults.ini");
        file = game.resolve(Config.FILE_NAME);
        Log.open(game);

        // In this order: most carry on from the session or the saved file the one before left.
        Checks.run("longStackTraceIsLoggedWhole", CoreTests::longStackTraceIsLoggedWhole);
        Checks.run("committedFileIsTheRowsRendered", CoreTests::committedFileIsTheRowsRendered);
        Checks.run("firstStartCreatesTheCommittedFileAndRunsOnItsValues", CoreTests::firstStartCreatesTheCommittedFileAndRunsOnItsValues);
        Checks.run("trackerAxesBecomeThisViews", CoreTests::trackerAxesBecomeThisViews);
        Checks.run("mapsAnswerIsHandedToCoreAsAWall", CoreTests::mapsAnswerIsHandedToCoreAsAWall);
        Checks.run("poseTurnsAndMovesTheView", CoreTests::poseTurnsAndMovesTheView);
        Checks.run("repeatedJumpIsNotFollowed", CoreTests::repeatedJumpIsNotFollowed);
        Checks.run("zoomLeavesTheHeadsTurnTheSameSizeOnScreen", CoreTests::zoomLeavesTheHeadsTurnTheSameSizeOnScreen);
        Checks.run("hotkeyActionsActAndSave", CoreTests::hotkeyActionsActAndSave);
        Checks.run("stockSightsLeavesTheViewTheHeadsRollAlone", CoreTests::stockSightsLeavesTheViewTheHeadsRollAlone);
        Checks.run("aTrackerThatGoesQuietLeavesTheViewAndTheHead", CoreTests::aTrackerThatGoesQuietLeavesTheViewAndTheHead);
        Checks.run("nextStartRunsOnWhatWasSaved", CoreTests::nextStartRunsOnWhatWasSaved);
        Checks.run("freeLookWithNoMarkerIsReadAsFreeLookWithOne", CoreTests::freeLookWithNoMarkerIsReadAsFreeLookWithOne);
        Checks.run("aFileFromBeforeTheKeysMovedStartsOnTheNavKeys", CoreTests::aFileFromBeforeTheKeysMovedStartsOnTheNavKeys);
        Checks.run("rowsReadAgainWhileTheGameRuns", CoreTests::rowsReadAgainWhileTheGameRuns);
        Checks.run("settingsOfTheGamesOptionsScreenAreSavedAndTaken", CoreTests::settingsOfTheGamesOptionsScreenAreSavedAndTaken);
        Checks.run("eachPlayersMicrophoneIsChosenAndSavedByItsName", CoreTests::eachPlayersMicrophoneIsChosenAndSavedByItsName);
        Checks.run("aGraphicsRowAppliedOnTheOptionsScreenGoesToCustom", CoreTests::aGraphicsRowAppliedOnTheOptionsScreenGoesToCustom);
        Checks.run("customKeepsItsRowsThroughTheOtherModes", CoreTests::customKeepsItsRowsThroughTheOtherModes);
        Checks.run("theHeightOfTheHillsIsASliderThatLeavesTheGraphicsModeAsItIs", CoreTests::theHeightOfTheHillsIsASliderThatLeavesTheGraphicsModeAsItIs);
        Checks.run("aGraphicsRowEditedInTheFileGoesToCustom", CoreTests::aGraphicsRowEditedInTheFileGoesToCustom);
        Checks.run("theDrawDistanceFarTreesAndReflectionsGoToCustomAsTheOtherRowsDo", CoreTests::theDrawDistanceFarTreesAndReflectionsGoToCustomAsTheOtherRowsDo);
        Checks.run("theGrassDensityGoesToCustomAsTheOtherRowsDo", CoreTests::theGrassDensityGoesToCustomAsTheOtherRowsDo);
        Checks.run("aRowChangedInAutomaticStartsCustomFromItsLevelAndNeverFromStock", CoreTests::aRowChangedInAutomaticStartsCustomFromItsLevelAndNeverFromStock);
        Checks.run("theShippedJarStartsThroughBoot", CoreTests::theShippedJarStartsThroughBoot);
        Checks.tally();
    }

    private static void check(boolean held, String what) {
        Checks.check(held, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 2e-3f;
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    /** What a start left the session and the mod's own settings on, as one line. */
    private static String started() {
        return headTracking.describe() + " | field of view " + FieldOfView.degrees() + " | own " + Own.described(Own.rows()) + " | graphics "
                + Quality.name(0) + " | style " + Style.of(0).label + " | frame blending " + Quality.frameBlending(0);
    }

    private static String java() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private record Ran(int exit, String output) {}

    private static Ran run(Path directory, List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("did not finish: " + command);
        }
        return new Ran(process.exitValue(), output);
    }

    /** The game started again on the scratch folder's file: another process, as a start is. */
    private static String nextStart(String name) throws IOException, InterruptedException {
        Path log = Files.createDirectories(scratch.resolve(name));
        Ran ran = run(Path.of("").toAbsolutePath(), List.of(java(), "--enable-native-access=ALL-UNNAMED", "-cp",
                System.getProperty("java.class.path"), CoreTests.class.getName(), "start", game.toString(), defaults.toString(), log.toString()));
        check(ran.exit() == 0, "the next start runs: exit " + ran.exit() + ", " + ran.output());
        return ran.output() + Files.readString(log.resolve(Log.FILE_NAME));
    }

    /**
     * A save is made on the mod's own thread, a moment after the action, one save after another.
     * This returns once every save asked for so far has been made or has failed, so nothing here
     * reads or writes the file while a save is replacing it.
     */
    private static void savesMade() throws InterruptedException {
        CountDownLatch made = new CountDownLatch(1);
        Tracking.saver.execute(made::countDown);
        made.await();
    }

    private static byte[] bytes() throws IOException, InterruptedException {
        savesMade();
        return Files.readAllBytes(file);
    }

    private static String text() throws IOException, InterruptedException {
        return new String(bytes(), StandardCharsets.US_ASCII);
    }

    private static boolean holds(String row) throws IOException, InterruptedException {
        return text().contains("\r\n" + row + "\r\n");
    }

    private static boolean saved(String... rows) throws IOException, InterruptedException {
        String text = text();
        boolean all = true;
        for (String row : rows) {
            all &= text.contains("\r\n" + row + "\r\n");
        }
        return all;
    }

    private static long firstEdit;
    private static int edits;

    /** The player saving the file from an editor. */
    private static void edit(String text) throws IOException, InterruptedException {
        savesMade();
        Files.writeString(file, text, StandardCharsets.US_ASCII);
        if (edits == 0) {
            firstEdit = Files.getLastModifiedTime(file).toMillis();
        }
        // Core goes by the file's write time, and two writes can land on one tick of it. Each edit is two seconds after the one before.
        Files.setLastModifiedTime(file, FileTime.fromMillis(firstEdit + 2000L * ++edits));
    }

    private static void longStackTraceIsLoggedWhole() throws IOException {
        RuntimeException deep = new RuntimeException("thrown far down");
        StackTraceElement[] frames = new StackTraceElement[80];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new StackTraceElement("zombie.some.LongNamedClassOfTheGames", "aMethodWithALongName" + i, "LongNamedClassOfTheGames.java", i);
        }
        deep.setStackTrace(frames);
        Log.error("a failure with a long way down", new IllegalStateException("wrapped", deep));
        String written = Files.readString(game.resolve(Log.FILE_NAME));
        check(written.contains("ERROR a failure with a long way down") && written.contains("Caused by: java.lang.RuntimeException: thrown far down")
                        && written.contains("aMethodWithALongName79"),
                "an error is in the log with its whole stack trace and its cause, however long");
    }

    private static void committedFileIsTheRowsRendered() throws IOException, InterruptedException {
        Path rendered = scratch.resolve("rendered.ini");
        Ran ran = run(Path.of("").toAbsolutePath(), List.of(java(), "--enable-native-access=ALL-UNNAMED", "-cp",
                System.getProperty("java.class.path"), RenderConfig.class.getName(), rendered.toString()));
        check(ran.exit() == 0 && Arrays.equals(Files.readAllBytes(rendered), Files.readAllBytes(Path.of("config", Config.FILE_NAME))),
                "config/CameraUnlock.ini is the rows' fresh render, byte for byte (pixi run render-config): " + ran.output());
    }

    private static void firstStartCreatesTheCommittedFileAndRunsOnItsValues() throws IOException, InterruptedException {
        int port = Tracking.open(game, defaults);
        check(Files.readString(game.resolve(Log.FILE_NAME)).contains(Config.FILE_NAME + ": created"), "a first start with no file creates it");
        check(Arrays.equals(Files.readAllBytes(file), Files.readAllBytes(Path.of("config", Config.FILE_NAME))),
                "the file a first start creates is config/CameraUnlock.ini byte for byte");
        check(Files.exists(defaults), "and the Defaults.ini it was pointed at is created");
        check(port == 4242, "a first start listens on 4242");
        String start = started();
        check(start.contains("tracking idle mode rotation and position yaw about world up") && start.contains("Aim mode: sights locked"),
                "with tracking on, rotation and position, yaw about straight up, sights locked: " + start);
        check(start.endsWith("field of view 65.0 | own " + Own.described(Own.ALL) + " | graphics Automatic (High) | style Bleak | frame blending true"),
                "65 degrees, everything of the mod's own on, the Automatic graphics mode in first person beginning at High, the Bleak style, frame blending: "
                        + start);
        check(Quality.overheadRow(0) == Quality.LOW && Quality.overheadPlain(0) && holds("IsometricQuality=Low"),
                "and the isometric view in Low, the game's own picture");
        check(Tracking.lightMultiplier() == 1.5f, "and the light turned one and a half times the head");
        check(!Mouths.on() && holds("TalkingMouths=false") && Tracking.microphones().equals(List.of("Default", "None", "None", "None"))
                        && holds("Microphone=Default") && holds("SecondPlayerMicrophone=None") && holds("FourthPlayerMicrophone=None"),
                "and talking mouths off, so no microphone is opened, with player 1 on Windows' default microphone and the others on none: "
                        + Tracking.microphones());
        int firstPersonTaps = 0;
        for (int bit : Config.FIRST_PERSON_TAPS) {
            firstPersonTaps |= bit;
        }
        check(Config.FIRST_PERSON_TAPS.length == View.COUNT && Config.FIRST_PERSON_TAPS[0] == Config.FIRST_PERSON
                        && Integer.bitCount(Config.CYCLE_TRACKING_MODE | Config.YAW_MODE | Config.AIM_MODE | Config.CYCLE_QUALITY
                                | Config.CYCLE_STYLE | Config.SPLIT_SCREEN | firstPersonTaps | Config.FIRST_PERSON_ALL) == 11,
                "the six actions have a bit each, and the first person key one for each player tapped and one held");
    }

    private static float[] lean(float cleanYaw, float x, float y, float z) {
        float[] m = new float[9];
        HeadTracking.trackerToWorld(cleanYaw, null, m);
        return new float[] {m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z};
    }

    private static boolean is(float[] v, float x, float y, float up) {
        return near(v[0], x) && near(v[1], y) && near(v[2], up);
    }

    /** The boundary: the tracker's x is positive to the head's left, its y up, and its negative z the lean in. */
    private static void trackerAxesBecomeThisViews() {
        ViewCamera.Pose ahead = new ViewCamera.Pose();
        ViewCamera.clean(ahead, 0f, 0f, 1.6f, 0f, 0f);
        check(is(lean(0f, 0.1f, 0f, 0f), -0.1f * ahead.rightX, -0.1f * ahead.rightY, 0f) && is(lean(0f, 0.1f, 0f, 0f), 0f, -0.1f, 0f),
                "facing +x, a head 10 cm along the tracker's +x moves the eye 10 cm against the view's right, to (0, -0.1, 0)");
        check(is(lean(0f, 0f, 0f, -0.4f), 0.4f, 0f, 0f), "a lean in of 40 cm, the tracker's negative z, moves it 40 cm the way the view faces");
        check(is(lean(0f, 0f, 0f, 0.1f), -0.1f, 0f, 0f), "and a lean back moves it back");
        check(is(lean(radians(90), 0f, 0f, -0.4f), 0f, 0.4f, 0f), "facing +y the same lean in is along +y");
        check(is(lean(radians(90), 0.1f, 0f, 0f), 0.1f, 0f, 0f), "and the tracker's +x is along +x, still against the view's right");
        check(is(lean(radians(35), 0f, 0.05f, 0f), 0f, 0f, 0.05f), "the tracker's y is up whichever way the view faces");
        float[] m = new float[9];
        HeadTracking.trackerToWorld(radians(200), null, m);
        check(m[6] == 0f && m[8] == 0f && m[1] == 0f && m[4] == 0f, "and no lean across or along the view goes up or down: the lean stays level");

        ViewCamera.Pose clean = new ViewCamera.Pose(), turned = new ViewCamera.Pose(), expected = new ViewCamera.Pose();
        ViewCamera.clean(clean, 3f, 4f, 1.6f, radians(40), radians(-10));
        turned.set(clean);
        HeadTracking.turn(turned, 0f, 0f, 12f, true);
        check(near(turned.forwardX, clean.forwardX) && near(turned.forwardY, clean.forwardY) && near(turned.forwardUp, clean.forwardUp),
                "angles of 0, 0 and 12 degrees leave the view looking where it did");
        float tilt = turned.upX * clean.rightX + turned.upY * clean.rightY + turned.upUp * clean.rightUp;
        check(near(tilt, (float) -Math.sin(Math.toRadians(12))), "and tilt its top 12 degrees to the left");
        turned.set(clean);
        HeadTracking.turn(turned, 20f, 10f, 12f, true);
        expected.set(clean);
        ViewCamera.turn(expected, radians(20), radians(10), radians(12), true);
        check(near(turned.forwardX, expected.forwardX) && near(turned.forwardY, expected.forwardY) && near(turned.forwardUp, expected.forwardUp)
                        && near(turned.upUp, expected.upUp),
                "core's degrees are the camera's radians, in the camera's own senses");
        turned.set(clean);
        HeadTracking.turn(turned, 20f, 0f, 0f, true);
        check(turned.forwardX * clean.rightX + turned.forwardY * clean.rightY > 0.3f, "a positive yaw turns the view to its right");
    }

    private static void mapsAnswerIsHandedToCoreAsAWall() {
        CameraUnlock.Obstruction found = new CameraUnlock.Obstruction();
        HeadTracking.report(0.07f, 0.30f, 0.12f, found);
        check(found.queried && found.blocked && near(found.distance, 0.19f),
                "room for 7 cm of a 30 cm lean is a wall the margin beyond it, which core holds the eye the margin short of");
        HeadTracking.report(0.30f, 0.30f, 0.12f, found);
        check(found.queried && !found.blocked, "room for the whole lean is a clear path");
        HeadTracking.report(0f, 0.30f, 0.12f, found);
        check(found.blocked && near(found.distance, 0.12f), "no room at all is a wall at the margin");
    }

    private static DatagramSocket socket;

    /** x, y and z in metres, sent as the wire's centimetres, and the angles in degrees. */
    private static void send(double yaw, double pitch, double roll, double x, double y, double z) throws IOException {
        if (socket == null) {
            socket = new DatagramSocket();
        }
        ByteBuffer wire = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        for (double value : new double[] {x * 100, y * 100, z * 100, yaw, pitch, roll}) {
            wire.putDouble(value);
        }
        socket.send(new DatagramPacket(wire.array(), 48, InetAddress.getLoopbackAddress(), port));
    }

    /**
     * A tracker moving to a pose and stopping there. The first step of a turn waits for the packet
     * after it, so there are two. Then nothing more comes, and core carries the turn on past the
     * pose for a moment, as it does between any two packets, before it settles on what the tracker
     * last said: the frames here are that moment.
     */
    private static void moveTo(double yaw, double pitch, double roll, double x, double y, double z) throws IOException, InterruptedException {
        send(yaw - 0.1, pitch, roll, x, y, z);
        frames(30);
        send(yaw, pitch, roll, x, y, z);
        frames(900);
    }

    private static final ViewCamera.Pose clean = new ViewCamera.Pose(), out = new ViewCamera.Pose();
    private static final WorldQuery noMap = new WorldQuery();
    private static float cleanYaw;
    private static boolean sightsUp;
    private static boolean inVehicle;

    private static void frame() throws InterruptedException {
        Thread.sleep((long) (FRAME_SECONDS * 1000));
        // No weapon is in hand, and the lean is not handed to one.
        headTracking.view(true, clean, cleanYaw, null, noMap, sightsUp, false, ViewModel.FORWARD_STOP, inVehicle, false, out);
    }

    private static boolean within(int milliseconds, BooleanSupplier reached) throws InterruptedException {
        long until = System.nanoTime() + milliseconds * 1_000_000L;
        while (System.nanoTime() < until) {
            frame();
            if (reached.getAsBoolean()) {
                return true;
            }
        }
        return false;
    }

    private static void frames(int milliseconds) throws InterruptedException {
        within(milliseconds, () -> false);
    }

    private static boolean looks(float yawDegrees, float pitchDegrees, float rollDegrees) {
        ViewCamera.Pose expected = new ViewCamera.Pose();
        expected.set(clean);
        ViewCamera.turn(expected, radians(yawDegrees), radians(pitchDegrees), radians(rollDegrees), true);
        return near(out.forwardX, expected.forwardX) && near(out.forwardY, expected.forwardY) && near(out.forwardUp, expected.forwardUp)
                && near(out.upX, expected.upX) && near(out.upY, expected.upY) && near(out.upUp, expected.upUp);
    }

    private static boolean eyeMoved(float x, float y, float up) {
        return near(out.eyeX - clean.eyeX, x) && near(out.eyeY - clean.eyeY, y) && near(out.eyeUp - clean.eyeUp, up);
    }

    private static String eye() {
        return "(" + (out.eyeX - clean.eyeX) + ", " + (out.eyeY - clean.eyeY) + ", " + (out.eyeUp - clean.eyeUp) + ") " + headTracking.describe();
    }

    private static void poseTurnsAndMovesTheView() throws IOException, InterruptedException {
        try (DatagramSocket free = new DatagramSocket(0)) {
            port = free.getLocalPort();
        }
        Tracking.listen(port);
        // The checks from here hold a pose the tracker sent once for seconds, where a tracker gone quiet would ease out of the view.
        freshnessInTheFile = Tracking.settings().dataFreshnessMs;
        freshFor(600_000);
        cleanYaw = 0f;
        ViewCamera.clean(clean, 3f, 4f, 1.6f, cleanYaw, 0f);
        frame();
        check(looks(0f, 0f, 0f) && eyeMoved(0f, 0f, 0f) && headTracking.headTurn() == null && headTracking.describe().startsWith("tracking idle"),
                "before a tracker sends anything the view is the clean one");

        send(20, 10, 12, 0.10, 0.05, -0.20);
        check(within(3000, () -> looks(20f, 10f, 12f)), "a pose of yaw 20, pitch 10 and roll 12 turns the view by them: " + headTracking.describe());
        check(within(1000, () -> eyeMoved(0.20f, -0.10f, 0.05f)),
                "and a head 10 cm to its left, 5 cm up and 20 cm in moves the eye 20 cm ahead, 10 cm against the view's right and 5 cm up: " + eye());
        float[] head = headTracking.headTurn();
        check(head != null && near(head[0], radians(20)) && near(head[1], radians(10)) && near(head[2], radians(12)),
                "the character's head is given the same turn, in radians");
        float[] carried = headTracking.carried();
        check(carried[0] == 0f && carried[1] == 0f && carried[2] == 0f, "at the hip the weapon is moved by none of the lean");
        check(headTracking.describe().startsWith("tracking applied") && headTracking.describe().contains("remote false"),
                "tracking says it is applied, from a tracker on this machine");
        check(near(headFollowLight.turn(), HeadFollowLight.headingTurn(clean, radians(30), radians(15), radians(18), true)),
                "and the carried light is turned by core's angles for it, one and a half times the head's");
        ViewCamera.Pose lit = new ViewCamera.Pose();
        lit.set(clean);
        ViewCamera.turn(lit, radians(30), radians(15), radians(18), true);
        check(near(headFollowLight.up(clean), lit.forwardUp) && Math.abs(lit.forwardUp - clean.forwardUp) > 0.05f,
                "and the beam's height is that same pose's, not the view's at one times the head: " + headFollowLight.up(clean) + " against " + lit.forwardUp);
        check(Files.readString(game.resolve(Log.FILE_NAME)).contains("the lean is not held off walls: there is no map to measure it against"),
                "with no map to measure the lean against, the log says the lean is not held off walls");

        cleanYaw = radians(90);
        ViewCamera.clean(clean, 3f, 4f, 1.6f, cleanYaw, 0f);
        frame();
        check(eyeMoved(0.10f, 0.20f, 0.05f), "the lean follows the body: facing +y the same head moves the eye 20 cm along +y: " + eye());
        cleanYaw = 0f;
        ViewCamera.clean(clean, 3f, 4f, 1.6f, cleanYaw, 0f);

        headTracking.follow(true);
        head = headTracking.headTurn();
        check(head != null && near(head[0], radians(20)) && headFollowLight.turn() == 0f,
                "in the game's own view the head's turn is kept up for the character's head, and the light is the game's");
        headTracking.follow(false);
        check(headTracking.headTurn() == null, "and is not while the game is paused");
        frame();
        check(looks(20f, 10f, 12f) && eyeMoved(0.20f, -0.10f, 0.05f), "the first frame tracking applies again is at the tracker's pose: " + eye());
        headTracking.rest();
        check(headTracking.headTurn() == null && headTracking.describe().startsWith("tracking idle"), "a save that is left puts tracking at rest");
        frame();
    }

    /** The bug core's receiver was given its gate for: a tracker that loses the head jumps to a pose and repeats it bit for bit. */
    private static void repeatedJumpIsNotFollowed() throws IOException, InterruptedException {
        for (int repeat = 0; repeat < 8; repeat++) {
            send(0, 0, 0, 0, 0, 0);
            frames(40);
        }
        check(looks(20f, 10f, 12f) && eyeMoved(0.20f, -0.10f, 0.05f), "a jump to a pose that then repeats bit for bit does not swing the view: " + eye());
        moveTo(20.5, 10, 12, 0.10, 0.05, -0.20);
        check(looks(20.5f, 10f, 12f), "and the head found again is followed: " + headTracking.describe());
        moveTo(20, 10, 12, 0.10, 0.05, -0.20);
    }

    private static void zoomLeavesTheHeadsTurnTheSameSizeOnScreen() throws IOException, InterruptedException {
        moveTo(25, 0, 0, 0.10, 0, -0.20);
        check(looks(25f, 0f, 0f), "a yaw of 25 degrees alone: " + headTracking.describe());
        float base = FieldOfView.setTanHalfVertical();
        float aheadX = clean.eyeX + clean.forwardX * 8f, aheadY = clean.eyeY + clean.forwardY * 8f;
        float[] hip = new float[2], aimed = new float[2];
        ViewCamera.Pose turned = new ViewCamera.Pose();
        turned.set(out);
        turned.eyeX = clean.eyeX;
        turned.eyeY = clean.eyeY;
        turned.eyeUp = clean.eyeUp;
        ViewCamera.project(turned, base, ASPECT, aheadX, aheadY, clean.eyeUp, hip);
        check(headTracking.zoomFactor() == 1f, "unzoomed, core's zoom factor is exactly 1");

        fieldOfView.ease(true, 10f);
        frame();
        check(near(headTracking.zoomFactor(), FieldOfView.AIMED_ZOOM), "with a firearm aimed it is the ratio of the tangents");
        turned.set(out);
        turned.eyeX = clean.eyeX;
        turned.eyeY = clean.eyeY;
        turned.eyeUp = clean.eyeUp;
        ViewCamera.project(turned, fieldOfView.tanHalfVertical(), ASPECT, aheadX, aheadY, clean.eyeUp, aimed);
        check(near(hip[0], aimed[0]) && Math.abs(hip[0]) > 0.3f, "and the head's yaw moves the picture as far as it did unzoomed: " + hip[0] + " and " + aimed[0]);
        check(eyeMoved(0.20f, -0.10f * FieldOfView.AIMED_ZOOM, 0f), "the lean in is whole and the lean across the view is scaled with the picture: " + eye());
        moveTo(0, 0, 15, 0.10, 0, -0.20);
        check(looks(0f, 0f, 15f), "and the head's roll is whole, unscaled by the zoom: " + headTracking.describe());
        fieldOfView.rest();
        moveTo(20, 10, 12, 0.10, 0.05, -0.20);
        check(looks(20f, 10f, 12f), "back to the first pose: " + headTracking.describe());
    }

    private static void hotkeyActionsActAndSave() throws IOException, InterruptedException {
        int others = Config.FIRST_PERSON | Config.CYCLE_QUALITY | Config.CYCLE_STYLE | Config.SPLIT_SCREEN;
        byte[] before = bytes();
        int handedBack = Tracking.act(others);
        String modes = headTracking.describe();
        check(handedBack == others && Arrays.equals(bytes(), before) && modes.contains("mode rotation and position yaw about world up")
                        && modes.contains("Aim mode: sights locked"),
                "the first person view's four actions are handed back for it to act on, and change and save nothing here");

        check(Tracking.act(Config.CYCLE_TRACKING_MODE) == Config.CYCLE_TRACKING_MODE && headTracking.describe().contains("mode rotation only"),
                "the tracking mode key goes to rotation only");
        check(saved("RotationEnabled=true", "PositionEnabled=false"), "and saves it");
        frame();
        check(looks(20f, 10f, 12f) && eyeMoved(0f, 0f, 0f), "rotation only turns the view and moves nothing: " + eye());
        Tracking.act(Config.CYCLE_TRACKING_MODE);
        check(saved("RotationEnabled=false", "PositionEnabled=true"), "then position only, saved");
        frame();
        check(looks(0f, 0f, 0f) && eyeMoved(0.20f, -0.10f, 0.05f) && headTracking.headTurn() == null, "which moves the eye and turns nothing: " + eye());
        Tracking.act(Config.CYCLE_TRACKING_MODE);
        check(saved("RotationEnabled=true", "PositionEnabled=true") && headTracking.describe().contains("mode rotation and position"), "then both again, saved");

        Tracking.act(Config.YAW_MODE);
        check(saved("WorldSpaceYaw=false") && headTracking.describe().contains("yaw about view up"), "the yaw mode key goes to the view's own up and saves it");
        ViewCamera.clean(clean, 3f, 4f, 1.6f, cleanYaw, radians(-50));
        frame();
        ViewCamera.Pose aboutItsOwnUp = new ViewCamera.Pose();
        aboutItsOwnUp.set(clean);
        ViewCamera.turn(aboutItsOwnUp, radians(20), radians(10), radians(12), false);
        check(near(out.forwardX, aboutItsOwnUp.forwardX) && near(out.forwardY, aboutItsOwnUp.forwardY) && !looks(20f, 10f, 12f),
                "and a view looking down is turned about its own up");
        ViewCamera.clean(clean, 3f, 4f, 1.6f, cleanYaw, 0f);
        Tracking.act(Config.YAW_MODE);
        check(saved("WorldSpaceYaw=true") && headTracking.describe().contains("yaw about world up"), "and back, saved");

        ViewCamera.clean(clean, 3f, 4f, 1.6f, cleanYaw, radians(-50));
        inVehicle = true;
        frame();
        check(near(out.forwardX, aboutItsOwnUp.forwardX) && near(out.forwardY, aboutItsOwnUp.forwardY)
                        && headTracking.describe().contains("yaw about view up"),
                "from a vehicle's seat the view is turned about its own up, with yaw about straight up saved");
        byte[] seated = bytes();
        Tracking.act(Config.YAW_MODE);
        frame();
        check(Arrays.equals(bytes(), seated) && near(out.forwardX, aboutItsOwnUp.forwardX) && near(out.forwardY, aboutItsOwnUp.forwardY),
                "where the yaw mode key changes and saves nothing");
        inVehicle = false;
        frame();
        check(headTracking.describe().contains("yaw about world up") && !near(out.forwardX, aboutItsOwnUp.forwardX),
                "and out of the vehicle the yaw is about straight up again");
        ViewCamera.clean(clean, 3f, 4f, 1.6f, cleanYaw, 0f);

        Tracking.act(Config.AIM_MODE);
        check(Tracking.aimModeLabel().equals("Aim mode: free look with marker") && Tracking.freeLook(), "the aim mode key goes to free look with a marker");
        check(saved("TrueFreeLook=true", "FreeLookMarker=true"), "saved with its marker");
        Tracking.act(Config.AIM_MODE);
        check(Tracking.aimModeLabel().equals("Aim mode: stock sights") && !Tracking.freeLook(), "then to stock sights, with no free look without a marker between");
        check(saved("TrueFreeLook=false", "FreeLookMarker=false", "StockSights=true"), "saved");

        Tracking.cycleGraphicsMode(0);
        check(Quality.overheadRow(0) == Quality.MEDIUM && Quality.name(0).equals("Automatic (High)") && saved("IsometricQuality=Medium") && holds("Quality=Automatic")
                        && Quality.said(0).equals("Isometric (Medium)"),
                "in the isometric view the graphics mode's action goes from Low to Medium there, saves it, and leaves first person's: " + Quality.said(0));
        Tracking.cycleGraphicsMode(0);
        Tracking.cycleGraphicsMode(0);
        Tracking.cycleGraphicsMode(0);
        check(Quality.overheadRow(0) == Quality.LOW && saved("IsometricQuality=Low") && holds("Quality=Automatic"),
                "on through High and Custom, with no Automatic, round to Low");
        Quality.inFirstPerson(0, true);
        check(Quality.said(0).equals("First Person (Automatic, High)"), "first person names its mode, and in Automatic the level it is at: " + Quality.said(0));
        Tracking.cycleGraphicsMode(0);
        check(Quality.name(0).equals("Low") && Quality.overheadRow(0) == Quality.LOW && saved("Quality=Low") && holds("IsometricQuality=Low")
                        && Quality.said(0).equals("First Person (Low)"),
                "in first person it goes from Automatic to Low, saves it, and leaves the isometric view's: " + Quality.said(0));
        Tracking.cycleGraphicsMode(0);
        check(Quality.name(0).equals("Medium") && Quality.overheadRow(0) == Quality.LOW && saved("Quality=Medium") && holds("IsometricQuality=Low"),
                "and on to Medium, the isometric view still in Low");
        Tracking.cycleStyle(0);
        check(Style.of(0) == Style.VIVID && saved("Style=Vivid"), "the picture style's action goes round to Vivid and saves it");
        Tracking.cycleGraphicsMode(2);
        check(Quality.overheadRow(2) == Quality.MEDIUM && Quality.row(2) == Quality.AUTOMATIC && Quality.row(0) == Quality.MEDIUM
                        && saved("ThirdPlayerIsometricQuality=Medium") && holds("ThirdPlayerQuality=Automatic") && holds("Quality=Medium")
                        && holds("SecondPlayerIsometricQuality=Low"),
                "player 3's graphics action steps the mode of the view they are in and saves it to their own row");
        Tracking.cycleStyle(1);
        check(Style.of(1) == Style.VIVID && Style.of(0) == Style.VIVID && saved("SecondPlayerStyle=Vivid", "ThirdPlayerStyle=Bleak"),
                "and player 2's style action steps theirs alone");
        Tracking.cycleStyle(1);
        Tracking.cycleStyle(1);
        Tracking.cycleGraphicsMode(2);
        Tracking.cycleGraphicsMode(2);
        Tracking.cycleGraphicsMode(2);
        check(Style.of(1) == Style.BLEAK && Quality.overheadRow(2) == Quality.LOW && saved("SecondPlayerStyle=Bleak", "ThirdPlayerIsometricQuality=Low"),
                "and each goes round as player 1's does");
        check(SplitScreen.row() == SplitScreen.VERTICAL && holds("SplitScreen=Vertical") && holds("SplitScreenKey=F7"), "two players start side by side, with F7 to turn them");
        Tracking.turnSplitScreen();
        check(SplitScreen.row() == SplitScreen.HORIZONTAL && saved("SplitScreen=Horizontal"), "the split screen's action goes to one above the other and saves it");
        check(holds("UdpPort=default") && holds("CycleTrackingModeKey=PageUp") && holds("YawModeKey=PageDown") && holds("TrueFreeLookKey=Insert") && !text().contains("ToggleKey") && holds("FirstPersonKey=Delete"), "and rows no action saves are as they were");
    }

    /** The session is in stock sights here. */
    private static void stockSightsLeavesTheViewTheHeadsRollAlone() throws IOException, InterruptedException {
        frame();
        check(looks(20f, 10f, 12f) && eyeMoved(0.20f, -0.10f, 0.05f), "at the hip stock sights leaves the pose whole: " + eye());
        sightsUp = true;
        check(within(2000, () -> looks(0f, 0f, 12f) && eyeMoved(0f, 0f, 0f)),
                "with the sights up, a pose of yaw 20, pitch 10 and roll 12 turns the view 0, 0 and 12 and moves the eye nowhere: " + eye());
        float[] head = headTracking.headTurn();
        check(head != null && near(head[0], 0f) && near(head[1], 0f) && near(head[2], radians(12)), "and the character's head with it");
        sightsUp = false;
        check(within(2000, () -> looks(20f, 10f, 12f) && eyeMoved(0.20f, -0.10f, 0.05f)), "the sights down, the pose eases back whole: " + eye());
        Tracking.act(Config.AIM_MODE);
        check(Tracking.aimModeLabel().equals("Aim mode: sights locked") && saved("StockSights=false"), "and the key comes round to sights locked, saved");
        Tracking.act(Config.AIM_MODE);
        Tracking.act(Config.AIM_MODE);
        check(saved("StockSights=true"), "stock sights again, for the next start");
    }

    /** The tracker closed: what it sent last leaves the picture, and a save loaded then has the head straight. */
    private static void aTrackerThatGoesQuietLeavesTheViewAndTheHead() throws IOException, InterruptedException {
        freshFor(200);
        send(20, 10, 12, 0.10, 0.05, -0.20);
        check(within(1000, () -> looks(20f, 10f, 12f) && eyeMoved(0.20f, -0.10f, 0.05f)), "while the tracker sends the view has its pose: " + eye());
        frames(1500);
        float[] head = headTracking.headTurn();
        check(looks(0f, 0f, 0f) && eyeMoved(0f, 0f, 0f) && head != null && near(head[0], 0f) && near(head[1], 0f) && near(head[2], 0f),
                "once it stops sending its pose leaves the view and the character's head: " + eye());
        headTracking.rest();
        frame();
        check(looks(0f, 0f, 0f) && eyeMoved(0f, 0f, 0f), "a save loaded with the tracker still closed starts from the clean view: " + eye());
        send(20, 10, 12, 0.10, 0.05, -0.20);
        frame();
        check(!looks(20f, 10f, 12f), "the tracker sending again does not snap the view to its pose: " + eye());
        for (long until = System.nanoTime() + 1_000_000_000L; System.nanoTime() < until; ) {
            send(20, 10, 12, 0.10, 0.05, -0.20);
            frame();
        }
        check(looks(20f, 10f, 12f) && eyeMoved(0.20f, -0.10f, 0.05f), "and as it goes on sending its pose eases back in: " + eye());
        freshFor(freshnessInTheFile);
    }

    private static int freshnessInTheFile;

    /** How long core takes a packet as current, put on the session as the file would, in the modes the session runs in now. */
    private static void freshFor(int milliseconds) {
        CameraUnlock.Settings settings = Tracking.settings();
        settings.dataFreshnessMs = milliseconds;
        settings.trackingMode = CameraUnlock.TRACKING_ROTATION_AND_POSITION;
        settings.aimMode = Tracking.aimMode();
        Core.library().configure(settings);
    }

    private static void nextStartRunsOnWhatWasSaved() throws IOException, InterruptedException {
        String next = nextStart("second-start");
        check(next.contains(Config.FILE_NAME + ": read"), "the saved file is read at the next start");
        check(next.contains("tracking idle mode rotation and position yaw about world up") && next.contains("Aim mode: stock sights"),
                "which has tracking on again, and the saved modes: " + next);
        check(next.contains("graphics Medium | style Vivid"), "and the saved graphics mode and picture style");
    }

    private static void freeLookWithNoMarkerIsReadAsFreeLookWithOne() throws IOException, InterruptedException {
        String saved = text();
        edit(saved.replace("TrueFreeLook=false", "TrueFreeLook=true").replace("StockSights=true", "StockSights=false"));
        check(holds("TrueFreeLook=true") && holds("FreeLookMarker=false") && holds("StockSights=false"), "a file that holds free look with no marker");
        check(nextStart("third-start").contains("Aim mode: free look with marker"), "starts in free look with a marker, the game's own reticle");
        edit(saved);
    }

    /** A file written before the tracking keys were the game's own: a toggle row, and the three following Defaults.ini. */
    private static void aFileFromBeforeTheKeysMovedStartsOnTheNavKeys() throws IOException, InterruptedException {
        String saved = text();
        edit(saved.replace("CycleTrackingModeKey=PageUp", "ToggleKey=End, Ctrl+Shift+Y\r\nCycleTrackingModeKey=default")
                .replace("YawModeKey=PageDown", "YawModeKey=default").replace("TrueFreeLookKey=Insert", "TrueFreeLookKey=default"));
        String next = nextStart("keys-from-before");
        check(next.contains(Config.FILE_NAME + ": read"), "a file with the old toggle row and the keys at default is read: " + next);
        check(next.contains("ToggleKey is not a setting this mod reads, so it is ignored"), "and the toggle row is said to be left alone, not acted on");
        edit(saved);
    }

    private static void rowsReadAgainWhileTheGameRuns() throws IOException, InterruptedException {
        Tracking.watchSettings();
        String saved = text();
        edit(saved.replace("FieldOfView=65.0", "FieldOfView=90").replace("VanillaWeapons=false", "VanillaWeapons=true")
                .replace("VanillaPlants=false", "VanillaPlants=true").replace("Quality=Medium", "Quality=High").replace("Style=Vivid", "Style=Natural")
                .replace("MuzzleFlash=true", "MuzzleFlash=false").replace("Crosshair=true", "Crosshair=false").replace("RegripTwoHandedWeapons=true", "RegripTwoHandedWeapons=false").replace("SplitScreen=Horizontal", "SplitScreen=Vertical").replace("UdpPort=default", "UdpPort=5555")
                .replace("HillHeight=1.5", "HillHeight=0.25"));
        Tracking.watchSettings();
        check(FieldOfView.degrees() == 90f, "an edited field of view is read while the game runs");
        check(HillHeight.asked() == 0.25f, "and the height of the hills: " + HillHeight.asked());
        check(Own.rows() == (Own.ALL & ~Own.WEAPONS & ~Own.PLANTS), "rows of [Content] switched on are the game's, and the rest stay the mod's: " + Own.described(Own.rows()));
        check(Quality.name(0).equals("High") && Style.of(0) == Style.NATURAL, "and the graphics mode and the picture style");
        check(SplitScreen.row() == SplitScreen.VERTICAL, "and how two players share the screen");
        check(!MuzzleFlash.on(), "and the muzzle flash switched off");
        check(!Crosshair.on(), "and the mod's own crosshair switched off");
        check(!Regrip.on(), "and two-handed weapons left in the right hand");
        edit(saved.replace("VanillaArms=false", "VanillaArms=true"));
        Tracking.watchSettings();
        check(FieldOfView.degrees() == 65f && Own.rows() == (Own.ALL & ~Own.ARMS) && Quality.name(0).equals("Medium") && Style.of(0) == Style.VIVID
                        && MuzzleFlash.on() && Crosshair.on() && Regrip.on() && SplitScreen.row() == SplitScreen.HORIZONTAL && HillHeight.asked() == 1.5f,
                "set back, they are as they were, and the arms row switched on is that row and no other: " + Own.described(Own.rows()));
        Tracking.cycleStyle(0);
        check(Style.of(0) == Style.NATURAL, "a save after an edit still saves");
        check(saved("Style=Natural", "VanillaArms=true"), "and leaves the edited row in the file");

        edit(text().replace("FieldOfView=65.0", "FieldOfView=80"));
        Tracking.cycleStyle(0);
        check(saved("Style=Bleak", "FieldOfView=80", "VanillaArms=true"), "a save made before the watch has looked at an edit leaves the edited row in the file too");
        Tracking.watchSettings();
        check(FieldOfView.degrees() == 80f && Style.of(0) == Style.BLEAK, "and the watch still reads the edit: " + FieldOfView.degrees());
    }

    private static int optionNamed(CameraUnlock.Option[] options, String id) {
        for (int option = 0; option < options.length; option++) {
            if (options[option].id.equals(id)) {
                return option;
            }
        }
        throw new IllegalArgumentException(id);
    }

    private static void settingsOfTheGamesOptionsScreenAreSavedAndTaken() throws IOException, InterruptedException {
        CameraUnlock.Option[] options = Tracking.options();
        StringBuilder ids = new StringBuilder();
        for (CameraUnlock.Option option : options) {
            ids.append(option.id).append(' ');
        }
        check(ids.toString().equals("WorldSpaceYaw TrackingMode AimMode FieldOfView Quality IsometricQuality Style HillHeight MuzzleFlash Crosshair RegripTwoHandedWeapons SplitScreen "
                        + "SecondPlayerQuality SecondPlayerIsometricQuality SecondPlayerStyle ThirdPlayerQuality ThirdPlayerIsometricQuality ThirdPlayerStyle "
                        + "FourthPlayerQuality FourthPlayerIsometricQuality FourthPlayerStyle TreeQuality "
                        + "GrassDensity RenderScale ViewDistance ZombiesAndPropsDrawDistance FarTreeDistance RainOnGlass ShadingAndLight Reflections "
                        + "FullSizeTextures SurfaceDepth SharpShadows WholeFigureShadows FrameBlending LightDistance VanillaWeapons VanillaFurniture "
                        + "VanillaPictures VanillaPlants VanillaTextures VanillaBuildings VanillaZombies VanillaPeople VanillaArms VanillaClothes VanillaAnimals VanillaFire "
                        + "VanillaCabins "
                        + "ShotsThroughWalls ShotsBreakLamps ShotsWoundLimbs VehiclesSmashThrough FacesTheView ShotsPastTrees ZombiesSeenAtOnce TalkingMouths "),
                "the game's options screen is given the modes, the yaw mode and the mod's own rows, and no key list, port or microphone: " + ids);
        int fov = optionNamed(options, "FieldOfView"), yaw = optionNamed(options, "WorldSpaceYaw"), tracking = optionNamed(options, "TrackingMode"),
                aim = optionNamed(options, "AimMode"), arms = optionNamed(options, "VanillaArms"), flash = optionNamed(options, "MuzzleFlash");
        boolean taken = true;
        for (CameraUnlock.Option option : options) {
            taken &= Tracking.takes(option);
        }
        check(taken, "the session has somewhere to put each of them");
        check(options[fov].kind == CameraUnlock.OPTION_FLOAT && options[fov].min == 40 && options[fov].max == 110 && options[fov].step == 1
                        && options[fov].label.equals("Field of view") && options[aim].source == CameraUnlock.OPTION_AIM_MODE,
                "the field of view is a slider from 40 to 110 in whole degrees");
        check(Tracking.optionValue(fov) == FieldOfView.degrees() && Tracking.optionValue(aim) == Tracking.aimMode()
                        && (Tracking.optionValue(yaw) != 0) == Tracking.worldYaw()
                        && (Tracking.optionValue(flash) != 0) == MuzzleFlash.on() && options[flash].label.equals("Muzzle flash"),
                "each reads as the session has it");
        check(options[arms].kind == CameraUnlock.OPTION_BOOL && options[arms].label.equals("Vanilla arms") && options[arms].comment.contains("held weapon")
                        && options[arms].comment.contains("mod that changes the arms or the weapons"),
                "the arms are a tick box whose tooltip says what they are and what they are on for: " + options[arms].label);

        int overhead = optionNamed(options, "IsometricQuality");
        check(Tracking.optionValue(overhead) == Quality.overheadRow(0) - Quality.LOW && options[overhead].label.equals("Isometric quality"),
                "the isometric view's graphics mode reads as the session has it, Low as 0: " + options[overhead].label);
        Tracking.applyOption(overhead, options[overhead], 2);
        check(Quality.overheadRow(0) == Quality.HIGH && Tracking.optionValue(overhead) == 2 && saved("IsometricQuality=High"),
                "set to High there it is taken, saved and read back");
        Tracking.applyOption(overhead, options[overhead], 0);
        check(Quality.overheadRow(0) == Quality.LOW && saved("IsometricQuality=Low"), "and set back to Low");

        boolean world = !Tracking.worldYaw(), flashed = !MuzzleFlash.on(), vanillaArms = (Own.rows() & Own.ARMS) != 0;
        int[] applied = {fov, yaw, tracking, aim, arms, flash};
        double[] values = {72, world ? 1 : 0, CameraUnlock.TRACKING_POSITION_ONLY, CameraUnlock.AIM_STOCK_SIGHTS, vanillaArms ? 1 : 0, flashed ? 1 : 0};
        for (int i = 0; i < applied.length; i++) {
            Tracking.applyOption(applied[i], options[applied[i]], values[i]);
        }
        check(FieldOfView.degrees() == 72f && Tracking.worldYaw() == world && Tracking.aimMode() == CameraUnlock.AIM_STOCK_SIGHTS
                        && Tracking.trackingModeLabel().equals("position only") && ((Own.rows() & Own.ARMS) == 0) == vanillaArms
                        && MuzzleFlash.on() == flashed,
                "what is applied there the session takes at once, before any of it is saved: " + started());
        Tracking.toggleWorldYaw();
        Tracking.cycleGraphicsMode(0);
        check(Tracking.worldYaw() == !world, "a key pressed straight after is not undone by the saves that follow");
        Tracking.toggleWorldYaw();
        check(saved("FieldOfView=72.0", "WorldSpaceYaw=" + world, "RotationEnabled=false", "PositionEnabled=true", "StockSights=true",
                        "VanillaArms=" + vanillaArms, "MuzzleFlash=" + flashed),
                "what is applied there is saved as its row, or the rows of its mode");
        check(FieldOfView.degrees() == 72f && Tracking.worldYaw() == world && Tracking.aimMode() == CameraUnlock.AIM_STOCK_SIGHTS
                        && Tracking.trackingModeLabel().equals("position only") && ((Own.rows() & Own.ARMS) == 0) == vanillaArms
                        && MuzzleFlash.on() == flashed,
                "and the session still runs on it once the saves are made: " + started());
        check(Tracking.optionValue(fov) == 72 && Tracking.optionValue(tracking) == CameraUnlock.TRACKING_POSITION_ONLY,
                "and the screen reads it back when it next opens");
        Tracking.cycleMode();
        check(Tracking.trackingModeLabel().equals("rotation and position") && Tracking.optionValue(tracking) == CameraUnlock.TRACKING_ROTATION_AND_POSITION,
                "a mode the key cycles after that goes on from it, and is what the screen then reads");
    }

    /** The options screen as it opens: each setting's place, what it is, what it shows, and what it holds, which is what it shows until the player changes it. */
    private record Screen(int[] options, CameraUnlock.Option[] described, double[] shown, double[] held) {
        static Screen opened() {
            CameraUnlock.Option[] described = Tracking.options();
            int[] options = new int[described.length];
            double[] shown = new double[described.length];
            for (int option = 0; option < described.length; option++) {
                options[option] = option;
                shown[option] = Tracking.optionValue(option);
            }
            return new Screen(options, described, shown, shown.clone());
        }

        void set(String id, double value) {
            held[optionNamed(described, id)] = value;
        }

        double shows(String id) {
            return shown[optionNamed(described, id)];
        }

        void apply() {
            Tracking.applyScreen(options, described, shown, held);
        }
    }

    /**
     * No microphone is opened here: each player's is set to none before talking mouths are turned
     * on, so the run reads no sound on a machine that has a microphone.
     */
    private static void eachPlayersMicrophoneIsChosenAndSavedByItsName() throws IOException, InterruptedException {
        String spanish = "Micrófono (USB Audio Device)";
        Tracking.applyMicrophone(1, spanish);
        check(Tracking.microphones().get(1).equals(spanish) && !Mouths.on(), "a microphone chosen for player 2 is the session's at once, with the mouths still off");
        savesMade();
        byte[] written = (Config.MICROPHONE_ROWS[1] + "=" + spanish + "\r\n").getBytes(StandardCharsets.UTF_8);
        check(indexOf(Files.readAllBytes(file), written) >= 0, "and saved by its name, as UTF-8");
        for (int player = 0; player < Config.MICROPHONE_ROWS.length; player++) {
            Tracking.applyMicrophone(player, Microphones.NONE);
        }
        CameraUnlock.Option[] options = Tracking.options();
        int talking = optionNamed(options, Config.TALKING_MOUTHS);
        check(options[talking].kind == CameraUnlock.OPTION_BOOL && options[talking].label.equals("Talking mouths")
                        && options[talking].comment.contains("nothing is recorded or sent"),
                "talking mouths are a tick box whose tooltip says nothing is recorded or sent: " + options[talking].label);
        Tracking.applyOption(talking, options[talking], 1);
        check(Mouths.on() && saved("TalkingMouths=true", "Microphone=None", "SecondPlayerMicrophone=None"),
                "turned on there they are on and saved, with every microphone on none");
        check(Microphones.mouth(0) == Mouths.Shape.SHUT && Microphones.mouth(1) == Mouths.Shape.SHUT, "and no mouth opens with no microphone to read");
        Tracking.applyOption(talking, options[talking], 0);
        Tracking.applyMicrophone(0, Microphones.DEFAULT);
        check(!Mouths.on() && saved("TalkingMouths=false", "Microphone=Default"), "and off again, with player 1 back on the default");
    }

    private static int indexOf(byte[] in, byte[] part) {
        for (int at = 0; at + part.length <= in.length; at++) {
            if (Arrays.equals(in, at, at + part.length, part, 0, part.length)) {
                return at;
            }
        }
        return -1;
    }

    private static void theHeightOfTheHillsIsASliderThatLeavesTheGraphicsModeAsItIs() throws IOException, InterruptedException {
        String mode = Quality.name(0);
        said();
        Screen screen = Screen.opened();
        CameraUnlock.Option hills = screen.described()[optionNamed(screen.described(), "HillHeight")];
        check(hills.kind == CameraUnlock.OPTION_FLOAT && hills.min == 0 && hills.max == 2 && hills.step == 0.1
                        && hills.label.equals("Hill height") && hills.comment.contains("0 is as flat"),
                "the height of the hills is a slider from level to " + HillHeight.MOST + " with its row's comment: " + hills.min + " " + hills.max + " " + hills.step + " " + hills.label);
        check(screen.shows("HillHeight") == HillHeight.asked(), "it shows the height the session has");
        check(Tracking.notch(0.3f, hills.step) == 0.3 && Tracking.notch(1.7f, hills.step) == 1.7 && Tracking.notch(0.62f, 0.01) == 0.62,
                "a value put on a slider is the notch the game's slider holds, worked out as it works it out: " + Tracking.notch(0.3f, hills.step));
        check(HillHeight.DEFAULT == 1.5f && HillHeight.CONTESTED == 1f,
                "hill height defaults to 1.5, and is held at 1 where players can fight");
        screen.set("HillHeight", 2);
        screen.apply();
        savesMade();
        check(HillHeight.asked() == 2f && saved("HillHeight=2.0"), "the slider saves its upper endpoint, 2");
        screen.set("HillHeight", 0.4);
        screen.apply();
        savesMade();
        check(HillHeight.asked() == 0.4f && HillHeight.drawn(false) == 0.4f && Quality.name(0).equals(mode) && !said() && saved("HillHeight=0.4", "Quality=" + mode),
                "applied there it is taken and saved, and the graphics mode stays as it was: it is not one of the rows of [Graphics]");
        screen = Screen.opened();
        screen.set("HillHeight", 1);
        screen.apply();
        savesMade();
        check(HillHeight.asked() == 1f && saved("HillHeight=1.0"), "and set back, it is back");
    }

    /** Whether the next frame says the graphics mode, with the game's menu shut. */
    private static boolean said() {
        return Tracking.graphicsToTell(false) == 1;
    }

    private static void inMode(String mode) {
        while (!Quality.name(0).equals(mode)) {
            Tracking.cycleGraphicsMode(0);
        }
    }

    private static void aGraphicsRowAppliedOnTheOptionsScreenGoesToCustom() throws IOException, InterruptedException {
        inMode("High");
        said();
        check(saved("Quality=High", "ViewDistance=80", "RenderScale=1.0") && !saved("StockLook=false"), "High, with the rows of [Graphics] as a first start writes them");

        Screen screen = Screen.opened();
        check(screen.shows("ViewDistance") == 80 && screen.shows("RenderScale") == 1 && screen.shows("SharpShadows") == 1
                        && screen.shows("Quality") == Quality.HIGH,
                "the screen shows High's picture in the rows of [Graphics]");
        screen.set("FieldOfView", 70);
        screen.apply();
        check(FieldOfView.degrees() == 70f && Quality.name(0).equals("High") && saved("FieldOfView=70.0", "Quality=High") && !said(),
                "a setting that is not a graphics setting applied there leaves the graphics mode as it was");

        screen.set("ViewDistance", 56);
        screen.set("Quality", Quality.LOW);
        // The config thread held, as it is when the screen reads the mode back straight after Apply: the saves are still to come.
        CountDownLatch held = new CountDownLatch(1);
        Tracking.saver.execute(() -> {
            try {
                held.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        screen.apply();
        boolean readsCustom = Tracking.optionValue(optionNamed(screen.described(), "Quality")) == Quality.CUSTOM;
        held.countDown();
        check(readsCustom, "the screen's mode list reads Custom as soon as it is applied, before the save is made");
        check(Quality.name(0).equals("Custom") && Quality.sight(0) == 56 && Quality.scale(0) == 1f && Quality.shadowSize(0) == 4096 && Quality.crownsToSight(0),
                "a graphics row changed there goes straight to Custom, drawn as High was but for that row, whatever the mode list was set to: "
                        + Quality.name(0));
        check(Tracking.graphicsToTell(true) == 0 && said() && !said(), "the first frame the game's menu is not up says so, once, as the key does");
        check(saved("Quality=Custom", "ViewDistance=56", "RenderScale=1.0", "SharpShadows=true"), "Custom is saved, with the rows as applied");
        check(Tracking.optionValue(optionNamed(screen.described(), "Quality")) == Quality.CUSTOM && screen.shows("ViewDistance") == 56,
                "and the screen's mode list now reads Custom");

        inMode("Low");
        Screen fromLow = Screen.opened();
        check(fromLow.shows("ViewDistance") == 48 && saved("Quality=Low", "ViewDistance=56"),
                "in Low the screen shows Low's picture, and the file still holds Custom's rows");
        fromLow.set("RenderScale", 0.7);
        fromLow.apply();
        check(Quality.name(0).equals("Custom") && !Quality.plain(0) && Quality.sight(0) == 48 && Quality.scale(0) == 0.7f,
                "a row changed in Low is Low's picture with that row, drawn with what the mod made: " + Quality.described(Quality.drawn(0)));
        check(saved("Quality=Custom", "RenderScale=0.7", "ViewDistance=48", "SharpShadows=false", "RainOnGlass=Low"),
                "and that is what Custom's rows then hold");
    }

    private static void customKeepsItsRowsThroughTheOtherModes() throws IOException, InterruptedException {
        Quality.Picture mine = Quality.custom();
        Tracking.cycleGraphicsMode(0);
        check(Quality.name(0).equals("Automatic (High)") && Quality.sight(0) == 80 && saved("Quality=Automatic", "RenderScale=0.7"),
                "the key goes from Custom to Automatic and saves it, and Custom's rows stay");
        Tracking.cycleGraphicsMode(0);
        check(Quality.name(0).equals("Low") && Quality.sight(0) == 48 && saved("Quality=Low", "RenderScale=0.7"), "then to Low");
        Tracking.cycleGraphicsMode(0);
        Tracking.cycleGraphicsMode(0);
        check(Quality.name(0).equals("High") && Quality.scale(0) == 1f && saved("Quality=High", "RenderScale=0.7"),
                "Medium and High draw their own and leave Custom's rows in the file");
        Tracking.cycleGraphicsMode(0);
        check(Quality.name(0).equals("Custom") && Quality.drawn(0).equals(mine) && saved("Quality=Custom"), "and the key comes round to Custom as the player left it");
        check(nextStart("custom-start").contains("graphics Custom"), "which the next start begins in");
    }

    private static void aGraphicsRowEditedInTheFileGoesToCustom() throws IOException, InterruptedException {
        Tracking.cycleGraphicsMode(0);
        check(Quality.name(0).equals("Automatic (High)"), "the key goes round to Automatic");
        Tracking.cycleGraphicsMode(0);
        check(Quality.name(0).equals("Low"), "and to Low");
        said();
        edit(text().replace("ViewDistance=48", "ViewDistance=72"));
        Tracking.watchSettings();
        check(Quality.name(0).equals("Custom") && Quality.sight(0) == 72 && Quality.scale(0) == 0.7f, "a row of [Graphics] edited in the file goes to Custom with it");
        check(saved("Quality=Custom", "ViewDistance=72") && said(), "saved, and said");
        edit(text().replace("Quality=Custom", "Quality=Medium"));
        Tracking.watchSettings();
        check(Quality.name(0).equals("Medium") && Quality.sight(0) == 60 && Quality.custom().viewDistance() == 72 && !said(),
                "the mode edited alone goes to that mode, and leaves Custom's rows");
        String style = Style.of(0) == Style.NATURAL ? "Bleak" : "Natural";
        edit(text().replace("Style=" + Style.of(0).label, "Style=" + style));
        Tracking.watchSettings();
        check(Quality.name(0).equals("Medium") && Style.of(0).label.equals(style), "and another row edited leaves the mode");
        edit(text().replace("Quality=Medium", "Quality=Custom").replace("ViewDistance=72", "ViewDistance=60"));
        Tracking.watchSettings();
        check(Quality.name(0).equals("Custom") && Quality.sight(0) == 60 && saved("Quality=Custom", "ViewDistance=60"),
                "and the mode and a row edited together is Custom with the edit");
    }

    private static void theGrassDensityGoesToCustomAsTheOtherRowsDo() throws IOException, InterruptedException {
        inMode("High");
        said();
        Screen screen = Screen.opened();
        CameraUnlock.Option[] options = screen.described();
        CameraUnlock.Option grass = options[optionNamed(options, "GrassDensity")];
        check(grass.kind == CameraUnlock.OPTION_INT && grass.min == Quality.LEAST_GRASS && grass.max == Quality.ALL_GRASS && grass.label.equals("Grass density"),
                "the grass density is a slider of percentages from " + Quality.LEAST_GRASS + " to " + Quality.ALL_GRASS);
        check(screen.shows("GrassDensity") == 100, "High grows all of the grass");

        screen.set("GrassDensity", 40);
        screen.apply();
        check(Quality.name(0).equals("Custom") && Quality.grassDensity(0) == 40 && Quality.sight(0) == 80,
                "a grass density applied there goes straight to Custom: " + Quality.described(Quality.drawn(0)));
        check(said() && saved("Quality=Custom", "GrassDensity=40", "ViewDistance=80"), "said, and saved with the rest of Custom's rows");
        check(Grass.density(Quality.grassDensity(0), false) == 0.4f && Grass.density(Quality.grassDensity(0), true) == 1f,
                "grass grows that share of its plants, and all of them where players can fight");

        inMode("Low");
        check(Quality.grassDensity(0) == Quality.SOME_GRASS, "Low grows Medium's share of the grass, for Automatic's level at Low's cost");
        said();
        edit(text().replace("GrassDensity=40", "GrassDensity=70"));
        Tracking.watchSettings();
        check(Quality.name(0).equals("Custom") && Quality.grassDensity(0) == 70, "the grass density edited in the file goes to Custom with it");
        check(saved("Quality=Custom", "GrassDensity=70") && said(), "saved, and said");
    }

    private static void theDrawDistanceFarTreesAndReflectionsGoToCustomAsTheOtherRowsDo() throws IOException, InterruptedException {
        inMode("High");
        said();
        Screen screen = Screen.opened();
        CameraUnlock.Option[] options = screen.described();
        CameraUnlock.Option props = options[optionNamed(options, "ZombiesAndPropsDrawDistance")];
        CameraUnlock.Option farTrees = options[optionNamed(options, "FarTreeDistance")];
        CameraUnlock.Option reflections = options[optionNamed(options, "Reflections")];
        check(props.kind == CameraUnlock.OPTION_INT && props.min == 16 && props.max == 80 && props.step == 1 && props.label.equals("Zombies and props draw distance")
                        && props.comment.contains("ViewDistance") && farTrees.kind == CameraUnlock.OPTION_INT && farTrees.min == 0 && farTrees.max == 768
                        && farTrees.label.equals("Far tree distance") && reflections.kind == CameraUnlock.OPTION_BOOL
                        && options[optionNamed(options, "ShadingAndLight")].label.equals("Shading and light"),
                "the draw distance is a slider of whole tiles from 16 to 80, the far trees one from 0 to 768, and reflections a tick box of its own");
        check(screen.shows("ZombiesAndPropsDrawDistance") == 80 && screen.shows("FarTreeDistance") == 400 && screen.shows("Reflections") == 1
                        && screen.shows("ShadingAndLight") == 1,
                "in High the screen shows people and props drawn as far as the view goes, far trees to High's 400 tiles, and reflections on");

        screen.set("ZombiesAndPropsDrawDistance", 30);
        screen.apply();
        DrawDistance reach = new DrawDistance();
        reach.take(0, 0f, 0f, 0f, 0f, false);
        check(Quality.name(0).equals("Custom") && Quality.drawn(0).drawDistance() == 30 && Quality.sight(0) == 80 && !reach.draws(31f, 0f) && reach.draws(30f, 0f),
                "a draw distance applied there goes straight to Custom and draws people and props no further: " + Quality.described(Quality.drawn(0)));
        check(said() && saved("Quality=Custom", "ZombiesAndPropsDrawDistance=30", "FarTreeDistance=400", "ViewDistance=80"),
                "said, and saved with the rest of Custom's rows");

        Screen again = Screen.opened();
        again.set("Reflections", 0);
        again.apply();
        check(Quality.name(0).equals("Custom") && !Quality.reflections(0) && !Quality.surfacesMirror(0) && Quality.shadedAndLit(0)
                        && Quality.drawn(0).drawDistance() == 30 && saved("Reflections=false", "ShadingAndLight=true", "ZombiesAndPropsDrawDistance=30"),
                "reflections ticked off there are off, apart from the shading and light, and saved");

        inMode("Medium");
        check(Quality.reflections(0) && Quality.drawn(0).drawDistance() == 60 && Quality.farTreeDistance(0) == 150,
                "Medium draws its own: its vehicle mirrors, people and props to its view's end, and far trees to 150 tiles");
        said();
        edit(text().replace("FarTreeDistance=400", "FarTreeDistance=200"));
        Tracking.watchSettings();
        check(Quality.name(0).equals("Custom") && Quality.farTreeDistance(0) == 200 && !Quality.reflections(0),
                "the far tree distance edited in the file goes to Custom with it, the rest as Custom left them");
        check(saved("Quality=Custom", "FarTreeDistance=200") && said(), "saved, and said");
    }

    /** Frames of first person at ten a second until Automatic is at a level. */
    private static void slowUntil(String level) {
        long now = 1_000_000_000L;
        for (int frame = 0; frame < 600 && !Quality.name(0).equals(level); frame++) {
            now += 100_000_000L;
            Quality.frame(0, now, 0f);
        }
        Quality.rest(0);
    }

    private static void aRowChangedInAutomaticStartsCustomFromItsLevelAndNeverFromStock() throws IOException, InterruptedException {
        inMode("Automatic (High)");
        slowUntil("Automatic (Stock)");
        said();
        Screen screen = Screen.opened();
        check(Quality.name(0).equals("Automatic (Stock)"), "Automatic stepped down to Stock: " + Quality.name(0));
        screen.set("ViewDistance", 40);
        screen.apply();
        savesMade();
        check(Quality.name(0).equals("Custom") && !Quality.plain(0) && !Quality.gamesPlants(0) && Quality.scale(0) == Quality.LOW_PICTURE.renderScale()
                        && Quality.sight(0) == 40 && Quality.shadowSize(0) == 2048 && !Quality.shadedAndLit(0),
                "a row changed at Stock starts Custom from the level above it, Low's cost with what the mod made, and the change: " + Quality.described(Quality.drawn(0)));
        check(saved("Quality=Custom", "TreeQuality=Medium", "ViewDistance=40"), "and that is what Custom's rows hold");

        inMode("Automatic (High)");
        slowUntil("Automatic (Medium)");
        said();
        screen = Screen.opened();
        screen.set("SharpShadows", 1);
        screen.apply();
        savesMade();
        check(Quality.name(0).equals("Custom") && !Quality.plain(0) && !Quality.gamesPlants(0) && Quality.scale(0) == 0.8f && Quality.sight(0) == 60
                        && Quality.shadowSize(0) == 4096,
                "a row changed at Medium starts Custom from Automatic's Medium, with the mod's plants, and the change: " + Quality.described(Quality.drawn(0)));
        check(saved("Quality=Custom", "TreeQuality=Medium", "ViewDistance=60", "SharpShadows=true"), "and that is what Custom's rows hold");
    }

    /**
     * The shipped jar started as an installed copy starts it, by cameraunlock-core's boot class, in
     * a folder of its own, with the stubs of the game's classes for the game. It starts and the game
     * runs; and from a jar that lacks cameraunlock-core's module, or with the art jars its Class-Path
     * names not beside it, the game runs without it and the log says why.
     */
    private static void theShippedJarStartsThroughBoot() throws IOException, InterruptedException {
        Path jar = Path.of("build", "ProjectZomboidHeadTracking.jar").toAbsolutePath();
        // The stubs first: the tests have game classes of their own, which are not the game's shapes.
        String game = Path.of("build", "stubs").toAbsolutePath() + ";" + Path.of("build", "test-classes").toAbsolutePath();
        List<Path> art;
        try (Stream<Path> built = Files.list(jar.getParent())) {
            art = built.filter(path -> path.getFileName().toString().startsWith("ProjectZomboidHeadTracking-")).toList();
        }
        check(!art.isEmpty(), "the build put the art jars beside the jar: " + jar.getParent());
        for (String kind : new String[] {"Boot", "Boot Refused", "Boot Without Art"}) {
            boolean starts = kind.equals("Boot");
            Path folder = Files.createDirectories(scratch.resolve(kind));
            if (kind.equals("Boot Refused")) {
                withoutTheModule(jar, folder.resolve(jar.getFileName()));
            } else {
                Files.copy(jar, folder.resolve(jar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            if (!kind.equals("Boot Without Art")) {
                for (Path piece : art) {
                    Files.deleteIfExists(folder.resolve(piece.getFileName()));
                    Files.createLink(folder.resolve(piece.getFileName()), piece);
                }
            }
            List<String> command = List.of(java(), "--enable-native-access=ALL-UNNAMED", "-XX:+EnableDynamicAgentLoading",
                    "-D" + Agent.DEFAULTS_PROPERTY + "=" + defaults, "-Dcameraunlock.mainClass=" + Game.class.getName().replace('.', '/'),
                    "-cp", jar.getFileName() + ";" + game, "com.cameraunlock.core.agent.Boot");
            Ran ran = run(folder, command);
            String written = Files.readString(folder.resolve(Log.FILE_NAME));
            check(ran.exit() == 0 && ran.output().contains("the game ran"), kind + ": the game runs: exit " + ran.exit() + ", " + ran.output());
            if (starts) {
                check(written.matches("(?s).*Project Zomboid Head Tracking [0-9]+[.][0-9]+[.][0-9]+ loaded.*")
                                && written.contains("things of the game's the mod uses are in this build") && written.contains("head tracking on")
                                && written.contains("sets of patches are registered") && !written.contains("ERROR"),
                        "and the mod starts from the jar alone, its core, its settings and its patches: " + written);
            } else if (kind.equals("Boot Refused")) {
                check(written.contains("ERROR Project Zomboid Head Tracking could not start, and the game runs without it")
                                && written.contains("com/cameraunlock/core/wasm/CoreModule") && !written.contains("sets of patches are registered"),
                        "and the mod stays out, and its log says why: " + written);
            } else {
                check(written.contains("ProjectZomboidHeadTracking-models.jar") && written.contains("are not beside")
                                && !written.contains("sets of patches are registered"),
                        "without the art jars the mod stays out, and its log names them: " + written);
            }
        }
    }

    /** The mod's jar with every entry of cameraunlock-core's module left out, as a jar put together wrongly would be. */
    private static void withoutTheModule(Path jar, Path to) throws IOException {
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(Files.newInputStream(jar));
                java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(Files.newOutputStream(to))) {
            for (java.util.zip.ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (entry.getName().startsWith("com/cameraunlock/core/wasm/")) continue;
                out.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                in.transferTo(out);
                out.closeEntry();
            }
        }
    }
}
