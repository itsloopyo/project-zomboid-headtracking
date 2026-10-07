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
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * What the mod does with cameraunlock-core, through the CameraUnlockCore.dll the build puts beside
 * the classes: the rows of CameraUnlock.ini, what each hotkey's action does and saves, and the
 * tracker's pose taken to this game's view. Real OpenTrack datagrams go to a loopback port, and
 * the settings file and Defaults.ini are in a scratch folder under build, so the developer's own
 * are neither read nor written.
 *
 * It is a process of its own because core reads one settings file a process, as a game does. A
 * later start of the game is this class run again with "start": see started.
 *
 * Every check runs, and the tally is printed once at the end.
 */
public final class CoreTests {
    /** Not 4242: a tracker may be running on the machine the tests run on. */
    private static final int PORT = 14323;
    private static final float ASPECT = 16f / 9f;
    private static final float FRAME_SECONDS = 0.005f;

    private static int checks;
    private static int failures;
    private static Path scratch, game, defaults, file;

    private CoreTests() {}

    /** The game's own main class in gameRunsUnmoddedBesideALibraryOfAnotherBuild. */
    public static final class Game {
        private Game() {}

        public static void main(String[] arguments) {
            System.out.println("the game ran");
        }
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length > 0 && arguments[0].equals("start")) {
            Log.open(Path.of(arguments[3]));
            HeadTracking.open(Path.of(arguments[1]), Path.of(arguments[2]));
            System.out.println(started());
            return;
        }
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

        longStackTraceIsLoggedWhole();
        committedFileIsTheRowsRendered();
        firstStartCreatesTheCommittedFileAndRunsOnItsValues();
        trackerAxesBecomeThisViews();
        mapsAnswerIsHandedToCoreAsAWall();
        poseTurnsAndMovesTheView();
        repeatedJumpIsNotFollowed();
        zoomLeavesTheHeadsTurnTheSameSizeOnScreen();
        hotkeyActionsActAndSave();
        stockSightsLeavesTheViewTheHeadsRollAlone();
        toggleSwitchesTrackingOffForTheSessionAndWritesNothing();
        nextStartRunsOnWhatWasSaved();
        freeLookWithNoMarkerIsReadAsFreeLookWithOne();
        rowsReadAgainWhileTheGameRuns();
        gameRunsUnmoddedBesideALibraryOfAnotherBuild();

        System.out.println(failures == 0 ? "core tests: all " + checks + " passed" : "core tests: " + failures + " of " + checks + " FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(boolean held, String what) {
        checks++;
        if (!held) {
            failures++;
            System.out.println("  [FAIL] " + what);
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 2e-3f;
    }

    private static float radians(float degrees) {
        return (float) Math.toRadians(degrees);
    }

    /** What a start left the session and the mod's own settings on, as one line. */
    private static String started() {
        return HeadTracking.describe() + " | field of view " + FieldOfView.degrees() + " | own " + Own.described(Own.rows()) + " | graphics "
                + Quality.name() + " | style " + Style.current().label + " | frame blending " + FrameBlending.on();
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

    private static String text() throws IOException {
        return Files.readString(file, StandardCharsets.US_ASCII);
    }

    private static boolean holds(String row) throws IOException {
        return text().contains("\r\n" + row + "\r\n");
    }

    /** A save is made on the mod's own thread, a moment after the action. */
    private static boolean saved(String... rows) throws IOException, InterruptedException {
        for (int waited = 0; waited < 5000; waited += 20) {
            boolean all = true;
            for (String row : rows) {
                all &= holds(row);
            }
            if (all) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    private static long firstEdit;
    private static int edits;

    /** The player saving the file from an editor. */
    private static void edit(String text) throws IOException {
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

    private static void firstStartCreatesTheCommittedFileAndRunsOnItsValues() throws IOException {
        int port = HeadTracking.open(game, defaults);
        check(Files.readString(game.resolve(Log.FILE_NAME)).contains(Config.FILE_NAME + ": created"), "a first start with no file creates it");
        check(Arrays.equals(Files.readAllBytes(file), Files.readAllBytes(Path.of("config", Config.FILE_NAME))),
                "the file a first start creates is config/CameraUnlock.ini byte for byte");
        check(Files.exists(defaults), "and the Defaults.ini it was pointed at is created");
        check(port == 4242, "a first start listens on 4242");
        String start = started();
        check(start.contains("tracking idle mode rotation and position yaw about world up") && start.contains("Aim mode: sights locked"),
                "with tracking on, rotation and position, yaw about straight up, sights locked: " + start);
        check(start.endsWith("field of view 65.0 | own " + Own.described(Own.ALL) + " | graphics Low | style Vivid | frame blending false"),
                "65 degrees, everything of the mod's own on, the Low graphics mode, the Vivid style, no frame blending: " + start);
        check(HeadTracking.lightMultiplier() == 1.5f, "and the light turned one and a half times the head");
        check(Integer.bitCount(Config.TOGGLE | Config.CYCLE_TRACKING_MODE | Config.YAW_MODE | Config.AIM_MODE | Config.FIRST_PERSON
                        | Config.CYCLE_QUALITY | Config.CYCLE_STYLE) == 7,
                "the seven actions have a bit each");
    }

    private static float[] lean(float cleanYaw, float x, float y, float z) {
        float[] m = new float[9];
        HeadTracking.trackerToWorld(cleanYaw, m);
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
        HeadTracking.trackerToWorld(radians(200), m);
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
        socket.send(new DatagramPacket(wire.array(), 48, InetAddress.getLoopbackAddress(), PORT));
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

    private static void frame() throws InterruptedException {
        Thread.sleep((long) (FRAME_SECONDS * 1000));
        // Not on foot: whether a lean may carry the weapon there is asked of the game, which no test has.
        HeadTracking.view(true, clean, cleanYaw, noMap, sightsUp, false, out);
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
        return "(" + (out.eyeX - clean.eyeX) + ", " + (out.eyeY - clean.eyeY) + ", " + (out.eyeUp - clean.eyeUp) + ") " + HeadTracking.describe();
    }

    private static void poseTurnsAndMovesTheView() throws IOException, InterruptedException {
        HeadTracking.listen(PORT);
        cleanYaw = 0f;
        ViewCamera.clean(clean, 3f, 4f, 1.6f, cleanYaw, 0f);
        frame();
        check(looks(0f, 0f, 0f) && eyeMoved(0f, 0f, 0f) && HeadTracking.headTurn() == null && HeadTracking.describe().startsWith("tracking idle"),
                "before a tracker sends anything the view is the clean one");

        send(20, 10, 12, 0.10, 0.05, -0.20);
        check(within(3000, () -> looks(20f, 10f, 12f)), "a pose of yaw 20, pitch 10 and roll 12 turns the view by them: " + HeadTracking.describe());
        check(within(1000, () -> eyeMoved(0.20f, -0.10f, 0.05f)),
                "and a head 10 cm to its left, 5 cm up and 20 cm in moves the eye 20 cm ahead, 10 cm against the view's right and 5 cm up: " + eye());
        float[] head = HeadTracking.headTurn();
        check(head != null && near(head[0], radians(20)) && near(head[1], radians(10)) && near(head[2], radians(12)),
                "the character's head is given the same turn, in radians");
        float[] carried = HeadTracking.carried();
        check(carried[0] == 0f && carried[1] == 0f && carried[2] == 0f, "at the hip the weapon is moved by none of the lean");
        check(HeadTracking.describe().startsWith("tracking applied") && HeadTracking.describe().contains("remote false"),
                "tracking says it is applied, from a tracker on this machine");
        check(near(HeadFollowLight.turn(), HeadFollowLight.headingTurn(clean, radians(30), radians(15), radians(18), true)),
                "and the carried light is turned by core's angles for it, one and a half times the head's");
        check(Files.readString(game.resolve(Log.FILE_NAME)).contains("the lean is not held off walls: there is no map to measure it against"),
                "with no map to measure the lean against, the log says the lean is not held off walls");

        cleanYaw = radians(90);
        ViewCamera.clean(clean, 3f, 4f, 1.6f, cleanYaw, 0f);
        frame();
        check(eyeMoved(0.10f, 0.20f, 0.05f), "the lean follows the body: facing +y the same head moves the eye 20 cm along +y: " + eye());
        cleanYaw = 0f;
        ViewCamera.clean(clean, 3f, 4f, 1.6f, cleanYaw, 0f);

        HeadTracking.follow(true);
        head = HeadTracking.headTurn();
        check(head != null && near(head[0], radians(20)) && HeadFollowLight.turn() == 0f,
                "in the game's own view the head's turn is kept up for the character's head, and the light is the game's");
        HeadTracking.follow(false);
        check(HeadTracking.headTurn() == null, "and is not while the game is paused");
        frame();
        check(looks(20f, 10f, 12f) && eyeMoved(0.20f, -0.10f, 0.05f), "the first frame tracking applies again is at the tracker's pose: " + eye());
        HeadTracking.rest();
        check(HeadTracking.headTurn() == null && HeadTracking.describe().startsWith("tracking idle"), "a save that is left puts tracking at rest");
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
        check(looks(20.5f, 10f, 12f), "and the head found again is followed: " + HeadTracking.describe());
        moveTo(20, 10, 12, 0.10, 0.05, -0.20);
    }

    private static void zoomLeavesTheHeadsTurnTheSameSizeOnScreen() throws IOException, InterruptedException {
        moveTo(25, 0, 0, 0.10, 0, -0.20);
        check(looks(25f, 0f, 0f), "a yaw of 25 degrees alone: " + HeadTracking.describe());
        float base = FieldOfView.setTanHalfVertical();
        float aheadX = clean.eyeX + clean.forwardX * 8f, aheadY = clean.eyeY + clean.forwardY * 8f;
        float[] hip = new float[2], aimed = new float[2];
        ViewCamera.Pose turned = new ViewCamera.Pose();
        turned.set(out);
        turned.eyeX = clean.eyeX;
        turned.eyeY = clean.eyeY;
        turned.eyeUp = clean.eyeUp;
        ViewCamera.project(turned, base, ASPECT, aheadX, aheadY, clean.eyeUp, hip);
        check(HeadTracking.zoomFactor() == 1f, "unzoomed, core's zoom factor is exactly 1");

        FieldOfView.ease(true, 10f);
        frame();
        check(near(HeadTracking.zoomFactor(), FieldOfView.AIMED_ZOOM), "with a firearm aimed it is the ratio of the tangents");
        turned.set(out);
        turned.eyeX = clean.eyeX;
        turned.eyeY = clean.eyeY;
        turned.eyeUp = clean.eyeUp;
        ViewCamera.project(turned, FieldOfView.tanHalfVertical(), ASPECT, aheadX, aheadY, clean.eyeUp, aimed);
        check(near(hip[0], aimed[0]) && Math.abs(hip[0]) > 0.3f, "and the head's yaw moves the picture as far as it did unzoomed: " + hip[0] + " and " + aimed[0]);
        check(eyeMoved(0.20f, -0.10f * FieldOfView.AIMED_ZOOM, 0f), "the lean in is whole and the lean across the view is scaled with the picture: " + eye());
        FieldOfView.rest();
        moveTo(20, 10, 12, 0.10, 0.05, -0.20);
        check(looks(20f, 10f, 12f), "back to the first pose: " + HeadTracking.describe());
    }

    private static void hotkeyActionsActAndSave() throws IOException, InterruptedException {
        int others = Config.FIRST_PERSON | Config.CYCLE_QUALITY | Config.CYCLE_STYLE;
        byte[] before = Files.readAllBytes(file);
        int handedBack = HeadTracking.act(others);
        String modes = HeadTracking.describe();
        Thread.sleep(300);
        check(handedBack == others && Arrays.equals(Files.readAllBytes(file), before) && modes.contains("mode rotation and position yaw about world up")
                        && modes.contains("Aim mode: sights locked"),
                "the first person view's three actions are handed back for it to act on, and change and save nothing here");

        check(HeadTracking.act(Config.CYCLE_TRACKING_MODE) == Config.CYCLE_TRACKING_MODE && HeadTracking.describe().contains("mode rotation only"),
                "the tracking mode key goes to rotation only");
        check(saved("RotationEnabled=true", "PositionEnabled=false"), "and saves it");
        frame();
        check(looks(20f, 10f, 12f) && eyeMoved(0f, 0f, 0f), "rotation only turns the view and moves nothing: " + eye());
        HeadTracking.act(Config.CYCLE_TRACKING_MODE);
        check(saved("RotationEnabled=false", "PositionEnabled=true"), "then position only, saved");
        frame();
        check(looks(0f, 0f, 0f) && eyeMoved(0.20f, -0.10f, 0.05f) && HeadTracking.headTurn() == null, "which moves the eye and turns nothing: " + eye());
        HeadTracking.act(Config.CYCLE_TRACKING_MODE);
        check(saved("RotationEnabled=true", "PositionEnabled=true") && HeadTracking.describe().contains("mode rotation and position"), "then both again, saved");

        HeadTracking.act(Config.YAW_MODE);
        check(saved("WorldSpaceYaw=false") && HeadTracking.describe().contains("yaw about view up"), "the yaw mode key goes to the view's own up and saves it");
        ViewCamera.clean(clean, 3f, 4f, 1.6f, cleanYaw, radians(-50));
        frame();
        ViewCamera.Pose aboutItsOwnUp = new ViewCamera.Pose();
        aboutItsOwnUp.set(clean);
        ViewCamera.turn(aboutItsOwnUp, radians(20), radians(10), radians(12), false);
        check(near(out.forwardX, aboutItsOwnUp.forwardX) && near(out.forwardY, aboutItsOwnUp.forwardY) && !looks(20f, 10f, 12f),
                "and a view looking down is turned about its own up");
        ViewCamera.clean(clean, 3f, 4f, 1.6f, cleanYaw, 0f);
        HeadTracking.act(Config.YAW_MODE);
        check(saved("WorldSpaceYaw=true") && HeadTracking.describe().contains("yaw about world up"), "and back, saved");

        HeadTracking.act(Config.AIM_MODE);
        check(HeadTracking.aimModeLabel().equals("Aim mode: free look with marker") && HeadTracking.freeLook(), "the aim mode key goes to free look with a marker");
        check(saved("TrueFreeLook=true", "FreeLookMarker=true"), "saved with its marker");
        HeadTracking.act(Config.AIM_MODE);
        check(HeadTracking.aimModeLabel().equals("Aim mode: stock sights") && !HeadTracking.freeLook(), "then to stock sights, with no free look without a marker between");
        check(saved("TrueFreeLook=false", "FreeLookMarker=false", "StockSights=true"), "saved");

        HeadTracking.cycleGraphicsMode();
        check(Quality.name().equals("Medium") && saved("Quality=Medium"), "the graphics mode's action goes to Medium and saves it");
        HeadTracking.cycleStyle();
        check(Style.current() == Style.NATURAL && saved("Style=Natural"), "the picture style's action goes to Natural and saves it");
        check(holds("UdpPort=default") && holds("ToggleKey=default") && holds("FirstPersonKey=Delete"), "and rows no action saves are as they were");
    }

    /** The session is in stock sights here. */
    private static void stockSightsLeavesTheViewTheHeadsRollAlone() throws IOException, InterruptedException {
        frame();
        check(looks(20f, 10f, 12f) && eyeMoved(0.20f, -0.10f, 0.05f), "at the hip stock sights leaves the pose whole: " + eye());
        sightsUp = true;
        check(within(2000, () -> looks(0f, 0f, 12f) && eyeMoved(0f, 0f, 0f)),
                "with the sights up, a pose of yaw 20, pitch 10 and roll 12 turns the view 0, 0 and 12 and moves the eye nowhere: " + eye());
        float[] head = HeadTracking.headTurn();
        check(head != null && near(head[0], 0f) && near(head[1], 0f) && near(head[2], radians(12)), "and the character's head with it");
        sightsUp = false;
        check(within(2000, () -> looks(20f, 10f, 12f) && eyeMoved(0.20f, -0.10f, 0.05f)), "the sights down, the pose eases back whole: " + eye());
        HeadTracking.act(Config.AIM_MODE);
        check(HeadTracking.aimModeLabel().equals("Aim mode: sights locked") && saved("StockSights=false"), "and the key comes round to sights locked, saved");
        HeadTracking.act(Config.AIM_MODE);
        HeadTracking.act(Config.AIM_MODE);
        check(saved("StockSights=true"), "stock sights again, for the next start");
    }

    private static void toggleSwitchesTrackingOffForTheSessionAndWritesNothing() throws IOException, InterruptedException {
        byte[] before = Files.readAllBytes(file);
        HeadTracking.act(Config.TOGGLE);
        frame();
        check(looks(0f, 0f, 0f) && eyeMoved(0f, 0f, 0f) && HeadTracking.headTurn() == null && HeadTracking.describe().startsWith("tracking off"),
                "the toggle key leaves the view the clean one");
        HeadTracking.act(Config.TOGGLE);
        frame();
        check(looks(20f, 10f, 12f) && eyeMoved(0.20f, -0.10f, 0.05f), "and pressed again puts it at the tracker's pose at once: " + eye());
        HeadTracking.act(Config.TOGGLE);
        Thread.sleep(300);
        check(Arrays.equals(Files.readAllBytes(file), before), "neither press writes the file");
    }

    /** Tracking is off in this session here, by the toggle key, which the file knows nothing of. */
    private static void nextStartRunsOnWhatWasSaved() throws IOException, InterruptedException {
        String next = nextStart("second-start");
        check(next.contains(Config.FILE_NAME + ": read"), "the saved file is read at the next start");
        check(next.contains("tracking idle mode rotation and position yaw about world up") && next.contains("Aim mode: stock sights"),
                "which has tracking on again, and the saved modes: " + next);
        check(next.contains("graphics Medium | style Natural"), "and the saved graphics mode and picture style");
    }

    private static void freeLookWithNoMarkerIsReadAsFreeLookWithOne() throws IOException, InterruptedException {
        String saved = text();
        edit(saved.replace("TrueFreeLook=false", "TrueFreeLook=true").replace("StockSights=true", "StockSights=false"));
        check(holds("TrueFreeLook=true") && holds("FreeLookMarker=false") && holds("StockSights=false"), "a file that holds free look with no marker");
        check(nextStart("third-start").contains("Aim mode: free look with marker"), "starts in free look with a marker, the game's own reticle");
        edit(saved);
    }

    private static void rowsReadAgainWhileTheGameRuns() throws IOException, InterruptedException {
        HeadTracking.watchSettings();
        String saved = text();
        edit(saved.replace("FieldOfView=65.0", "FieldOfView=90").replace("OwnWeapons=true", "OwnWeapons=false")
                .replace("OwnPlants=true", "OwnPlants=false").replace("Quality=Medium", "Quality=High").replace("Style=Natural", "Style=Bleak")
                .replace("FrameBlending=false", "FrameBlending=true").replace("UdpPort=default", "UdpPort=5555"));
        HeadTracking.watchSettings();
        check(FieldOfView.degrees() == 90f, "an edited field of view is read while the game runs");
        check(Own.rows() == (Own.ALL & ~Own.WEAPONS & ~Own.PLANTS), "rows of [Content] switched off are, and the rest stay on: " + Own.described(Own.rows()));
        check(Quality.name().equals("High") && Style.current() == Style.BLEAK && FrameBlending.on(), "and the graphics mode, the picture style and frame blending");
        edit(saved.replace("OwnArms=true", "OwnArms=false"));
        HeadTracking.watchSettings();
        check(FieldOfView.degrees() == 65f && Own.rows() == (Own.ALL & ~Own.ARMS) && Quality.name().equals("Medium") && Style.current() == Style.NATURAL
                        && !FrameBlending.on(),
                "set back, they are as they were, and the arms row switched off is that row and no other: " + Own.described(Own.rows()));
        HeadTracking.cycleStyle();
        check(Style.current() == Style.BLEAK, "a save after an edit still saves");
        check(saved("Style=Bleak", "OwnArms=false"), "and leaves the edited row in the file");
    }

    /**
     * The mod's jar started as an installed copy starts it, by cameraunlock-core's boot class, in a
     * folder whose CameraUnlockCore.dll is from a release before the jar's, and in one with none.
     */
    private static void gameRunsUnmoddedBesideALibraryOfAnotherBuild() throws IOException, InterruptedException {
        Path jar = Path.of("build", "ProjectZomboidHeadTracking.jar").toAbsolutePath();
        Path older = Path.of("build-core", "Release", "CameraUnlockCoreOtherAbi.dll").toAbsolutePath();
        String testClasses = Path.of("build", "test-classes").toAbsolutePath().toString();
        for (boolean withLibrary : new boolean[] {true, false}) {
            Path folder = Files.createDirectories(scratch.resolve(withLibrary ? "Older Library" : "No Library"));
            Files.copy(jar, folder.resolve(jar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            if (withLibrary) {
                Files.copy(older, folder.resolve(CameraUnlock.LIBRARY), StandardCopyOption.REPLACE_EXISTING);
            }
            List<String> command = new ArrayList<>(List.of(java(), "--enable-native-access=ALL-UNNAMED", "-XX:+EnableDynamicAgentLoading",
                    "-Dcameraunlock.mainClass=" + Game.class.getName().replace('.', '/'), "-cp",
                    jar.getFileName() + ";" + testClasses, "com.cameraunlock.core.agent.Boot"));
            Ran ran = run(folder, command);
            check(ran.exit() == 0 && ran.output().contains("the game ran"),
                    (withLibrary ? "beside a library of another build" : "with no library beside the jar") + " the game still runs: exit " + ran.exit() + ", " + ran.output());
            Path log = folder.resolve(Log.FILE_NAME);
            String written = Files.exists(log) ? Files.readString(log) : "";
            check(written.contains("ERROR Project Zomboid Head Tracking could not start, and the game runs without it"),
                    "and the mod's log says the mod could not start: " + written);
            if (withLibrary) {
                check(written.contains("is version " + (CameraUnlock.ABI - 1) + " of cameraunlock-core's C interface")
                                && written.contains("written for version " + CameraUnlock.ABI) && written.contains("the DLL is the older of the two"),
                        "in words: which version each side is, and that the DLL is the older");
            } else {
                check(written.contains(CameraUnlock.LIBRARY), "naming the file that is not there");
            }
        }
    }
}
