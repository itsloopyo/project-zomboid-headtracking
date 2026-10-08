package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Head tracking for the first-person view. cameraunlock-core has the tracker's pose and
 * everything done to it: receiving, interpolation, smoothing, the limits, the zoom, the aim
 * modes, the lean's hold off a wall and its handover to the weapon. What is here is this game's:
 * the conversion of the pose to this view's axes, the map the lean is measured against, whether
 * tracking is on, the yaw mode, and what the hotkeys do.
 *
 * Only the picture is turned and moved. The view direction the mouse sets, which the game's aim,
 * movement and facing follow, is never written here.
 *
 * Everything runs on the game's logic thread except core's own threads and the thread that
 * saves a changed mode to CameraUnlock.ini and watches it for the rows read again while the
 * game runs.
 */
final class HeadTracking {
    private static final String[] TRACKING_MODE_LABEL = {"rotation and position", "rotation only", "position only"};

    /**
     * The aim modes in the order the key cycles them. Core has a fourth, free look with no
     * marker. The marker here is the game's own reticle, which is always drawn where the shot
     * lands, so that mode would be free look with a marker again and is left out. A file that
     * holds it is read as free look with a marker.
     */
    static final int[] AIM_MODES = {CameraUnlock.AIM_SIGHTS_LOCKED, CameraUnlock.AIM_FREE_LOOK_MARKER, CameraUnlock.AIM_STOCK_SIGHTS};

    /** A frame longer than this is a hitch, and core takes it as this long: whole, it would snap the smoothing to its target. */
    private static final float MAX_FRAME_SECONDS = 0.25f;
    /**
     * A lean held right at a wall's distance crosses in and out of contact on the tracker's
     * noise, as often as every frame, and each log line is a write to disk.
     */
    private static final long CONTACT_LOG_INTERVAL_NANOS = 1_000_000_000L;
    private static final long WATCH_MILLISECONDS = 1000;

    private static CameraUnlock core;
    private static Config config;
    private static CameraUnlock.Settings settings;
    /** A save writes a file and the watch asks for its write time, so both are kept off the game's logic thread, where the hotkeys are read. */
    static final ScheduledExecutorService saver = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "CameraUnlock-Config");
        thread.setDaemon(true);
        return thread;
    });

    private static final CameraUnlock.FrameInput input = new CameraUnlock.FrameInput();
    private static final CameraUnlock.Frame frame = new CameraUnlock.Frame();
    private static final CameraUnlock.Obstruction found = new CameraUnlock.Obstruction();
    private static final CameraUnlock.Lean lean = new CameraUnlock.Lean();

    private static boolean enabled = true;
    private static boolean worldYaw = true;
    /** The view rides a vehicle, which turns and tilts under it: the head's yaw is about the view's own up there, whatever is saved. */
    private static boolean seated;
    private static int trackingMode = CameraUnlock.TRACKING_ROTATION_AND_POSITION;
    private static volatile int aimMode = CameraUnlock.AIM_SIGHTS_LOCKED;

    private static boolean applied;
    private static boolean fresh;
    private static boolean contactLogged;
    private static long contactLoggedNanos = System.nanoTime() - CONTACT_LOG_INTERVAL_NANOS;
    private static boolean unmeasuredLogged;
    /** The head's yaw, pitch and roll this frame in radians, in the tracker's senses, while headTurned. */
    private static final float[] headTurn = new float[3];
    private static boolean headTurned;
    private static volatile float zoomFactor = 1f;

    private HeadTracking() {}

    /** Whether the aim mode leaves the weapon where it is and the head free to move round it. */
    static boolean freeLook() {
        return aimMode == CameraUnlock.AIM_FREE_LOOK_MARKER;
    }

    static String aimModeLabel() {
        return core.aimModeLabel(aimMode);
    }

    /** The factor core scaled the head's turn by on the newest frame: 1 while nothing is zoomed. */
    static float zoomFactor() {
        return zoomFactor;
    }

    static float lightMultiplier() {
        return settings.lightMultiplier;
    }

    /**
     * How far the head is turned this frame, for the character's own head: yaw to the right,
     * pitch up and roll to the left, in radians. Null while tracking turns nothing.
     */
    static float[] headTurn() {
        return headTurned ? headTurn : null;
    }

    /**
     * Reads CameraUnlock.ini from the game's folder, where the mod's jar is installed, and starts
     * listening for the tracker and for the hotkeys.
     */
    static void start(Path gameDirectory) {
        int port = open(gameDirectory, null);
        listen(port);
        core.hotkeysStart();
        saver.scheduleWithFixedDelay(HeadTracking::watchSettings, WATCH_MILLISECONDS, WATCH_MILLISECONDS, TimeUnit.MILLISECONDS);
    }

    /**
     * Describes and reads CameraUnlock.ini and puts what it holds on core's session and on the
     * mod's own settings.
     *
     * @param defaults null for the player's own Defaults.ini, a scratch file in a test
     * @return the UDP port the file names
     */
    static int open(Path gameDirectory, Path defaults) {
        core = Core.library();
        config = new Config(core);
        CameraUnlock.Config loaded = config.load(gameDirectory.resolve(Config.FILE_NAME), defaults);
        settings = loaded.settings;
        if (settings.aimMode == CameraUnlock.AIM_TRUE_FREE_LOOK) {
            settings.aimMode = CameraUnlock.AIM_FREE_LOOK_MARKER;
        }
        core.configure(settings);
        enabled = loaded.enableOnStartup;
        worldYaw = loaded.worldSpaceYaw;
        trackingMode = settings.trackingMode;
        aimMode = settings.aimMode;

        Config.Live live = config.live();
        Own.set(live.own());
        Quality.set(live.quality());
        Style.set(live.style());
        FrameBlending.set(live.frameBlending());
        FieldOfView.set(live.fieldOfView());
        Log.info("the mod's own in first person: " + Own.described(Own.rows()));
        Log.info("first person field of view set to " + FieldOfView.degrees() + " degrees vertical");
        Log.info("head tracking " + (enabled ? "on" : "off") + ", " + TRACKING_MODE_LABEL[trackingMode] + ", yaw about "
                + (worldYaw ? "straight up" : "the view's own up") + ", " + aimModeLabel());
        return loaded.udpPort;
    }

    /**
     * Core listens from here, and its own line in the log says whether it has the port. One
     * another program holds is tried again until it is free.
     */
    static void listen(int port) {
        core.start(port);
    }

    /** FieldOfView, Quality, Style, FrameBlending and the rows of [Content] are the settings read again while the game runs: every other row is read at the start. */
    static void watchSettings() {
        try {
            Config.Live live = config.reload();
            if (live == null) {
                return;
            }
            if (live.fieldOfView() != FieldOfView.degrees()) {
                FieldOfView.set(live.fieldOfView());
                Log.info(Config.FILE_NAME + " changed: first person field of view set to " + live.fieldOfView() + " degrees vertical");
            }
            Quality.set(live.quality());
            Style.set(live.style());
            FrameBlending.set(live.frameBlending());
            if (live.own() != Own.rows()) {
                Own.set(live.own());
                Log.info(Config.FILE_NAME + " changed: the mod's own in first person: " + Own.described(live.own()));
            }
        } catch (RuntimeException failure) {
            // Nothing is above this on the thread: left uncaught, the reason never reaches the log.
            // Thrown on, it ends the watch, so a fault that stays is logged once and not every second.
            Log.error(Config.FILE_NAME + " is no longer watched for changes", failure);
            throw failure;
        }
    }

    /**
     * How far the weapon and the shot are moved with the eye this frame, in world axes: the
     * part of the lean across the aim, while the sights are up in a mode that keeps the eye
     * on them. Nought at the hip, in free look and whenever no lean is applied.
     */
    static float[] carried() {
        return lean.rig;
    }

    /** What was pressed since the hotkeys were last asked after is forgotten: it was pressed in the game's menus, or as a save loaded. */
    static void dropHotkeys() {
        core.hotkeysDrop();
    }

    /**
     * Acts on the head tracking keys pressed since the hotkeys were last asked after.
     *
     * @return every action pressed, as Config's bits: the first person view's own are its to act on
     */
    static int pollHotkeys() {
        return act(core.hotkeysTake());
    }

    /** Acts on the head tracking actions among the bits, and hands them all back. */
    static int act(int pressed) {
        if ((pressed & Config.TOGGLE) != 0) {
            toggleEnabled();
        }
        if ((pressed & Config.CYCLE_TRACKING_MODE) != 0) {
            cycleMode();
        }
        if ((pressed & Config.YAW_MODE) != 0) {
            toggleWorldYaw();
        }
        if ((pressed & Config.AIM_MODE) != 0) {
            cycleAimMode();
        }
        return pressed;
    }

    /** The new value is already applied. A save that fails leaves the session on it, and the log says why. */
    private static void save(Runnable save) {
        saver.execute(() -> {
            try {
                save.run();
            } catch (RuntimeException failure) {
                // Nothing is above this on the thread: left uncaught, the reason never reaches the log.
                Log.error("a changed setting was not saved", failure);
            }
        });
    }

    /** The session only: End never writes the file. */
    static void toggleEnabled() {
        enabled = !enabled;
        Log.info("head tracking " + (enabled ? "on" : "off"));
    }

    static void cycleMode() {
        trackingMode = core.cycleTrackingMode();
        Log.info("head tracking mode: " + TRACKING_MODE_LABEL[trackingMode]);
        save(config::saveTrackingMode);
    }

    static void toggleWorldYaw() {
        if (seated) {
            Log.info("the yaw mode key does nothing from a vehicle's seat, where head yaw turns about the view's own up");
            return;
        }
        worldYaw = !worldYaw;
        Log.info("head yaw turns about " + (worldYaw ? "straight up" : "the view's own up"));
        boolean saved = worldYaw;
        save(() -> config.saveWorldSpaceYaw(saved));
    }

    /** The graphics mode after the one the session is in, kept for the next start. */
    static void cycleGraphicsMode() {
        int next = Quality.next();
        Quality.set(next);
        save(() -> config.saveQuality(next));
    }

    /** The picture style after the one the session is in, kept for the next start. */
    static void cycleStyle() {
        Style next = Style.current().next();
        Style.set(next.ordinal());
        save(() -> config.saveStyle(next));
    }

    static void cycleAimMode() {
        aimMode = aimModeAfter(aimMode);
        core.setAimMode(aimMode);
        Log.info(aimModeLabel());
        save(config::saveAimMode);
    }

    static int aimModeAfter(int mode) {
        for (int i = 0; i < AIM_MODES.length; i++) {
            if (AIM_MODES[i] == mode) {
                return AIM_MODES[(i + 1) % AIM_MODES.length];
            }
        }
        throw new IllegalArgumentException("aim mode " + mode + " is not one this game cycles");
    }

    /**
     * The matrix that takes the tracker's position to a lean in the world's axes, three rows of
     * three. The tracker sends x positive to the head's left and z positive away from the
     * screen, and both are turned round here, after core's limits, so the larger forward
     * allowance stays on the forward lean. The lean follows where the body faces and stays
     * level: looking down does not turn a lean forward into a lean into the floor.
     *
     * @param cleanYaw the yaw the clean view was built from
     */
    static void trackerToWorld(float cleanYaw, float[] matrix) {
        float cos = (float) Math.cos(cleanYaw);
        float sin = (float) Math.sin(cleanYaw);
        // right = -x and forward = -z, then world x = -sin * right + cos * forward and world y = cos * right + sin * forward.
        matrix[0] = sin;
        matrix[1] = 0f;
        matrix[2] = -cos;
        matrix[3] = -cos;
        matrix[4] = 0f;
        matrix[5] = -sin;
        matrix[6] = 0f;
        matrix[7] = 1f;
        matrix[8] = 0f;
    }

    /**
     * Turns a view by the head's angles as core hands them over, in degrees. The tracker's
     * senses, as its apps send them, are this camera's too: yaw positive to the right, pitch
     * positive up, roll positive tilting the head to the left.
     */
    static void turn(ViewCamera.Pose view, float yawDegrees, float pitchDegrees, float rollDegrees, boolean worldYaw) {
        ViewCamera.turn(view, (float) Math.toRadians(yawDegrees), (float) Math.toRadians(pitchDegrees),
                (float) Math.toRadians(rollDegrees), worldYaw);
    }

    /**
     * The map's answer in core's terms. Core asks where the surface is and holds the eye the
     * margin short of it. The map answers how far the eye itself can go, having swept three rays
     * and allowed for a wall met at a slant, so the surface core is told of is the margin beyond
     * that.
     *
     * @param room  how far the eye can go along the lean, capped at reach
     * @param reach the lean's own length
     */
    static void report(float room, float reach, float margin, CameraUnlock.Obstruction found) {
        found.queried = true;
        found.blocked = room < reach;
        found.distance = room + margin;
    }

    private static void ask(boolean active, int more) {
        input.flags = CameraUnlock.FRAME_CLOCK | (active ? CameraUnlock.FRAME_ACTIVE : 0) | more;
        input.deltaSeconds = MAX_FRAME_SECONDS;
        input.tanHalfFov = FieldOfView.tanHalfVertical();
        input.tanHalfFovBase = FieldOfView.setTanHalfVertical();
        core.frame(input, frame);
        zoomFactor = frame.zoomFactor;
        boolean nowFresh = (frame.flags & CameraUnlock.STATE_FRESH) != 0;
        if (active && nowFresh != fresh && (frame.flags & CameraUnlock.STATE_POSE) != 0) {
            fresh = nowFresh;
            Log.info(fresh ? "the tracker's poses are arriving"
                    : "no pose taken from the tracker for " + settings.dataFreshnessMs + " ms, the view keeps the last one");
        }
    }

    /** The head's turn as the character's head and the carried light take it, from the frame core answered. */
    private static void takeTheTurn(ViewCamera.Pose clean) {
        headTurned = (frame.flags & CameraUnlock.STATE_ROTATION) != 0;
        if (!headTurned) {
            HeadFollowLight.release();
            return;
        }
        headTurn[0] = (float) Math.toRadians(frame.yaw);
        headTurn[1] = (float) Math.toRadians(frame.pitch);
        headTurn[2] = (float) Math.toRadians(frame.roll);
        if (clean == null) {
            HeadFollowLight.release();
        } else {
            HeadFollowLight.follow(clean, (float) Math.toRadians(frame.lightYaw), (float) Math.toRadians(frame.lightPitch),
                    (float) Math.toRadians(frame.lightRoll), yawAboutWorldUp());
        }
    }

    /**
     * The game's own view, which the mod has no camera in: the head's turn is kept up for
     * the character's head and nothing else is done with the pose.
     *
     * @param inGameplay the game is running and the character is alive
     */
    static void follow(boolean inGameplay) {
        ride(false);
        ask(inGameplay && enabled, 0);
        if ((frame.flags & CameraUnlock.STATE_POSE) == 0) {
            atRest();
            return;
        }
        applied = false;
        noLean();
        takeTheTurn(null);
    }

    /**
     * Writes the view the frame is drawn from: the clean view, turned and
     * moved by the head when tracking applies.
     *
     * @param inGameplay the first-person view is on, the game is running and the character is alive
     * @param cleanYaw   the yaw the clean view was built from, which the lean follows
     * @param sightsUp   whether a firearm's sights are up this frame
     * @param onFoot     whether the weapon can be moved with the eye: not from a vehicle's seat
     * @param inVehicle  whether the view is from a vehicle's seat
     */
    static void view(boolean inGameplay, ViewCamera.Pose clean, float cleanYaw, WorldQuery world, boolean sightsUp,
            boolean onFoot, boolean inVehicle, ViewCamera.Pose out) {
        ride(inVehicle);
        out.set(clean);
        input.forwardStop = ViewModel.forwardStop();
        input.aimForward[0] = clean.forwardX;
        input.aimForward[1] = clean.forwardY;
        input.aimForward[2] = clean.forwardUp;
        trackerToWorld(cleanYaw, input.trackerToWorld);
        ask(inGameplay && enabled, CameraUnlock.FRAME_LEAN | (sightsUp ? CameraUnlock.FRAME_AIMING : 0)
                | (onFoot && Online.leanCarriesTheShot() ? CameraUnlock.FRAME_RIG_AVAILABLE : 0));
        if ((frame.flags & CameraUnlock.STATE_POSE) == 0) {
            atRest();
            return;
        }
        applied = true;
        if ((frame.flags & CameraUnlock.STATE_ROTATION) != 0) {
            turn(out, frame.yaw, frame.pitch, frame.roll, yawAboutWorldUp());
        }
        takeTheTurn(clean);
        if ((frame.flags & CameraUnlock.STATE_LEAN) == 0) {
            noLean();
            return;
        }

        // With no map to ask, the query could not run, and core is told so: it passes the lean
        // unheld and says it did.
        found.queried = false;
        found.blocked = false;
        if ((frame.flags & CameraUnlock.STATE_LEAN_QUERY) != 0 && world.mapped()) {
            float margin = settings.collisionMargin;
            float reach = frame.queryReach - margin;
            report(world.roomForTheEye(clean.eyeX, clean.eyeY, frame.queryDirection[0], frame.queryDirection[1], reach, margin),
                    reach, margin, found);
        }
        core.lean(found, lean);
        out.eyeX += lean.camera[0] + lean.rig[0];
        out.eyeY += lean.camera[1] + lean.rig[1];
        out.eyeUp += lean.camera[2] + lean.rig[2];

        boolean unmeasured = (lean.flags & CameraUnlock.LEAN_QUERY_FAILED) != 0;
        if (unmeasured != unmeasuredLogged) {
            unmeasuredLogged = unmeasured;
            Log.info(unmeasured ? "the lean is not held off walls: there is no map to measure it against"
                    : "the lean is held off walls again");
        }
        boolean contact = (lean.flags & CameraUnlock.LEAN_CONTACT) != 0;
        long now = System.nanoTime();
        if (contact != contactLogged && now - contactLoggedNanos >= CONTACT_LOG_INTERVAL_NANOS) {
            contactLogged = contact;
            contactLoggedNanos = now;
            Log.info(String.format(Locale.ROOT, contact
                    ? "lean held off a wall: asked for %.3f, room for %.3f, eye at (%.2f, %.2f)"
                    : "lean clear of the wall again: asked for %.3f, given %.3f, eye at (%.2f, %.2f)",
                    lean.asked, lean.given, clean.eyeX, clean.eyeY));
        }
    }

    /**
     * Tracking does not apply from here: a save was left. Core drops its state, so the next time
     * tracking applies it starts from the tracker's pose then, with no wall from before the break
     * rationing the lean.
     */
    static void rest() {
        ride(false);
        ask(false, 0);
        atRest();
    }

    private static void atRest() {
        applied = false;
        headTurned = false;
        contactLogged = false;
        HeadFollowLight.release();
        noLean();
    }

    private static void noLean() {
        lean.flags = 0;
        lean.asked = 0f;
        lean.given = 0f;
        for (int i = 0; i < 3; i++) {
            lean.camera[i] = 0f;
            lean.rig[i] = 0f;
        }
    }

    private static boolean yawAboutWorldUp() {
        return worldYaw && !seated;
    }

    private static void ride(boolean inVehicle) {
        if (inVehicle == seated) {
            return;
        }
        seated = inVehicle;
        if (worldYaw) {
            Log.info(seated ? "in a vehicle's seat: head yaw turns about the view's own up" : "out of the vehicle: head yaw turns about straight up again");
        }
    }

    /** One line with every term of the frame's pose, for the trace. */
    static String describe() {
        return String.format(Locale.ROOT,
                "tracking %s mode %s yaw about %s fresh %b remote %b head (yaw %.2f pitch %.2f roll %.2f)"
                        + " tracker position (%.3f, %.3f, %.3f) lean asked %.3f given %.3f wall %b"
                        + " %s stock share %.3f zoom %.4f view has (%.3f, %.3f, %.3f) weapon has (%.3f, %.3f, %.3f) %s",
                applied ? "applied" : enabled ? "idle" : "off", TRACKING_MODE_LABEL[trackingMode], yawAboutWorldUp() ? "world up" : "view up",
                (frame.flags & CameraUnlock.STATE_FRESH) != 0, (frame.flags & CameraUnlock.STATE_REMOTE) != 0,
                frame.headYaw, frame.headPitch, frame.headRoll, frame.headX, frame.headY, frame.headZ, lean.asked, lean.given,
                (lean.flags & CameraUnlock.LEAN_CONTACT) != 0, aimModeLabel(), frame.poseShare, frame.zoomFactor,
                lean.camera[0], lean.camera[1], lean.camera[2], lean.rig[0], lean.rig[1], lean.rig[2], HeadFollowLight.describe());
    }
}
