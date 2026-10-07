package com.cameraunlock.zomboid;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Head tracking for the first-person view: the tracker's pose, the hotkeys,
 * and the one place the pose is converted to the view's axes.
 *
 * Only the picture is turned and moved. The view direction the mouse sets,
 * which the game's aim, movement and facing follow, is never written here.
 *
 * Everything runs on the game's logic thread except the receiver's own thread and the
 * thread that saves a changed mode to CameraUnlock.ini and watches it for the rows read again
 * while the game runs.
 */
final class HeadTracking {
    private enum Mode {
        FULL("rotation and position", true, true),
        ROTATION_ONLY("rotation only", true, false),
        POSITION_ONLY("position only", false, true);

        final String label;
        final boolean rotation;
        final boolean position;

        Mode(String label, boolean rotation, boolean position) {
            this.label = label;
            this.rotation = rotation;
            this.position = position;
        }

        /** The mode a file's pair names. Core's owner never hands back both false. */
        static Mode of(boolean rotation, boolean position) {
            return rotation ? position ? FULL : ROTATION_ONLY : POSITION_ONLY;
        }
    }

    /** A frame longer than this is a hitch, and taking it whole would snap the smoothing to its target. */
    private static final float MAX_FRAME_SECONDS = 0.25f;
    /**
     * A lean held right at a wall's distance crosses in and out of contact on the tracker's
     * noise, as often as every frame, and each log line is a write to disk.
     */
    private static final long CONTACT_LOG_INTERVAL_NANOS = 1_000_000_000L;

    private static OpenTrackReceiver receiver;
    private static NativeConfig config;
    private static final long WATCH_MILLISECONDS = 1000;
    /** A save writes a file and the watch asks for its write time, so both are kept off the game's logic thread, where the hotkeys are read. */
    private static final ScheduledExecutorService saver = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "CameraUnlock-Config");
        thread.setDaemon(true);
        return thread;
    });
    private static final PoseFilter filter = new PoseFilter();
    private static final LeanClamp clamp = new LeanClamp();
    private static final float[] lean = new float[3];
    /** The lean between the view and the weapon, as the sights come up. */
    private static final LeanHandover handover = new LeanHandover();
    /** What of the head's yaw, pitch and lean reaches the view in stock sights: 1 at the hip, 0 with the sights up. */
    private static final AdsFade stockFade = new AdsFade();
    private static final long CLOCK_START_NANOS = System.nanoTime();

    /** Where this frame's lean starts and the map it is traced through. */
    private static WorldQuery leanWorld;
    private static float leanEyeX, leanEyeY;
    private static final LeanClamp.Room roomForTheLean = (directionX, directionY, directionUp, reach) ->
            leanWorld.roomForTheEye(leanEyeX, leanEyeY, directionX, directionY, reach);

    private static boolean enabled = true;
    private static Mode mode = Mode.FULL;
    private static boolean worldYaw = true;
    private static volatile AimMode aimMode = AimMode.SIGHTS_LOCKED;

    /** Whether the aim mode leaves the weapon where it is and the head free to move round it. */
    static boolean freeLook() {
        return aimMode.isFreeLook();
    }
    private static float stockShare = 1f;
    private static long lastFrameNanos;
    private static boolean applied;
    private static boolean contactLogged;
    private static long contactLoggedNanos = System.nanoTime() - CONTACT_LOG_INTERVAL_NANOS;
    private static float leanAsked;
    /** The head's yaw, pitch and roll this frame in radians, in the tracker's senses, while headTurned. */
    private static final float[] headTurn = new float[3];
    private static boolean headTurned;

    private HeadTracking() {}

    /**
     * How far the head is turned this frame, for the character's own head: yaw to the right,
     * pitch up and roll to the left, in radians. Null while tracking turns nothing.
     */
    static float[] headTurn() {
        return headTurned ? headTurn : null;
    }

    /**
     * The game's own view, which the mod has no camera in: the head's turn is kept up for
     * the character's head and nothing else is done with the pose.
     *
     * @param inGameplay the game is running and the character is alive
     */
    static void follow(boolean inGameplay) {
        long now = System.nanoTime();
        float delta = Math.min((now - lastFrameNanos) / 1e9f, MAX_FRAME_SECONDS);
        lastFrameNanos = now;
        OpenTrackReceiver.Sample sample = receiver.latest();
        if (!inGameplay || !enabled || !mode.rotation || sample == null) {
            rest();
            return;
        }
        filter.update(sample, receiver.isRemote(), delta);
        headTurn[0] = (float) Math.toRadians(filter.yaw());
        headTurn[1] = (float) Math.toRadians(filter.pitch());
        headTurn[2] = (float) Math.toRadians(filter.roll());
        headTurned = true;
        // No lean is applied from here: the next one starts at the hip, with no wall from before rationing it.
        clamp.reset();
        handover.stop();
        stockFade.reset();
        HeadFollowLight.release();
    }

    /**
     * Reads CameraUnlock.ini from the game's folder, where the mod's jar is installed, and starts
     * listening for the tracker and for the hotkeys.
     */
    static void start(Path gameDirectory) {
        config = new NativeConfig(jarDirectory().resolve(NativeConfig.LIBRARY));
        NativeConfig.Loaded loaded = config.load(gameDirectory.resolve(NativeConfig.FILE_NAME));
        Settings settings = loaded.settings();
        Settings.current = settings;
        Own.set(loaded.own());
        Quality.set(loaded.quality());
        Style.set(loaded.style());
        FrameBlending.set(loaded.frameBlending());
        Log.info("the mod's own in first person: " + Own.described(Own.rows()));
        enabled = settings.enableOnStartup();
        mode = Mode.of(settings.rotationEnabled(), settings.positionEnabled());
        worldYaw = settings.worldSpaceYaw();
        aimMode = AimMode.decode(settings.trueFreeLook(), settings.freeLookMarker(), settings.stockSights());
        FieldOfView.set(settings.fieldOfView());
        Log.info("first person field of view set to " + FieldOfView.degrees() + " degrees vertical");
        Log.info("head tracking " + (enabled ? "on" : "off") + ", " + mode.label + ", yaw about "
                + (worldYaw ? "straight up" : "the view's own up") + ", " + aimMode.label);
        receiver = new OpenTrackReceiver(settings.udpPort(), settings.dataFreshnessMs());
        receiver.start();
        config.startHotkeys();
        saver.scheduleWithFixedDelay(HeadTracking::watchSettings, WATCH_MILLISECONDS, WATCH_MILLISECONDS,
                TimeUnit.MILLISECONDS);
    }

    /** FieldOfView, Quality, Style, FrameBlending and the rows of [Content] are the settings read again while the game runs: every other row is read at the start. */
    private static void watchSettings() {
        try {
            NativeConfig.Live live = config.reloadLive();
            if (live != null && live.fieldOfView() != FieldOfView.degrees()) {
                FieldOfView.set(live.fieldOfView());
                Log.info(NativeConfig.FILE_NAME + " changed: first person field of view set to " + live.fieldOfView()
                        + " degrees vertical");
            }
            if (live != null) {
                Quality.set(live.quality());
                Style.set(live.style());
                FrameBlending.set(live.frameBlending());
            }
            if (live != null && live.own() != Own.rows()) {
                Own.set(live.own());
                Log.info(NativeConfig.FILE_NAME + " changed: the mod's own in first person: " + Own.described(live.own()));
            }
        } catch (RuntimeException failure) {
            // Nothing is above this on the thread: left uncaught, the reason never reaches the log.
            // Thrown on, it ends the watch, so a fault that stays is logged once and not every second.
            Log.error(NativeConfig.FILE_NAME + " is no longer watched for changes", failure);
            throw failure;
        }
    }

    private static Path jarDirectory() {
        try {
            return Path.of(HeadTracking.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        } catch (URISyntaxException failure) {
            throw new IllegalStateException(failure);
        }
    }

    static boolean applied() {
        return applied;
    }

    /**
     * How far the weapon and the shot are moved with the eye this frame, in world axes: the
     * part of the lean across the aim, while the sights are up in a mode that keeps the eye
     * on them. Nought at the hip, in free look and whenever no lean is applied.
     */
    static float[] carried() {
        return handover.rig;
    }

    /** What was pressed since the hotkeys were last asked after is forgotten: it was pressed in the game's menus, or as a save loaded. */
    static void dropHotkeys() {
        config.pressed();
    }

    static void pollHotkeys() {
        int pressed = config.pressed();
        if ((pressed & NativeConfig.TOGGLE) != 0) {
            toggleEnabled();
        }
        if ((pressed & NativeConfig.CYCLE_TRACKING_MODE) != 0) {
            cycleMode();
        }
        if ((pressed & NativeConfig.YAW_MODE) != 0) {
            toggleWorldYaw();
        }
        if ((pressed & NativeConfig.AIM_MODE) != 0) {
            cycleAimMode();
        }
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

    static void toggleEnabled() {
        enabled = !enabled;
        Log.info("head tracking " + (enabled ? "on" : "off"));
    }

    static void cycleMode() {
        mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
        Log.info("head tracking mode: " + mode.label);
        Mode saved = mode;
        save(() -> config.saveTrackingMode(saved.rotation, saved.position));
    }

    static void toggleWorldYaw() {
        worldYaw = !worldYaw;
        Log.info("head yaw turns about " + (worldYaw ? "straight up" : "the view's own up"));
        boolean saved = worldYaw;
        save(() -> config.saveWorldSpaceYaw(saved));
    }

    /** F9: the graphics mode after the one the session is in, kept for the next start. */
    static void cycleGraphicsMode() {
        int next = Quality.next();
        Quality.set(next);
        save(() -> config.saveQuality(next));
    }

    /** F10: the picture style after the one the session is in, kept for the next start. */
    static void cycleStyle() {
        Style next = Style.current().next();
        Style.set(next.ordinal());
        save(() -> config.saveStyle(next));
    }

    static void cycleAimMode() {
        aimMode = aimMode.next();
        Log.info(aimMode.label);
        AimMode saved = aimMode;
        save(() -> config.saveAimMode(saved));
    }

    /**
     * Writes the view the frame is drawn from: the clean view, turned and
     * moved by the head when tracking applies.
     *
     * @param inGameplay the first-person view is on, the game is running and the character is alive
     * @param cleanYaw   the yaw the clean view was built from, which the lean follows
     * @param sightsUp   whether a firearm's sights are up this frame
     * @param onFoot     whether the weapon can be moved with the eye: not from a vehicle's seat
     */
    static void view(boolean inGameplay, ViewCamera.Pose clean, float cleanYaw, WorldQuery world, boolean sightsUp,
            boolean onFoot, ViewCamera.Pose out) {
        long now = System.nanoTime();
        float delta = Math.min((now - lastFrameNanos) / 1e9f, MAX_FRAME_SECONDS);
        lastFrameNanos = now;

        out.set(clean);
        lean[0] = 0f;
        lean[1] = 0f;
        lean[2] = 0f;
        leanAsked = 0f;
        OpenTrackReceiver.Sample sample = receiver.latest();
        if (!inGameplay || !enabled || sample == null) {
            rest();
            return;
        }
        applied = true;
        float zoom = FieldOfView.zoom();
        // A tracker that has gone quiet leaves the last pose in place: the filter is fed the
        // same sample and holds it.
        filter.update(sample, receiver.isRemote(), delta);
        // Stock sights takes the head out of the view while the sights are up, all but its
        // roll. It is the pose itself that is scaled, here, before anything reads it: the
        // zoom, the walls, the weapon and the carried light all work from what is left.
        long nowMillis = (now - CLOCK_START_NANOS) / 1_000_000L;
        stockShare = stockFade.update(aimMode.stockSightsEngaged(sightsUp), nowMillis);

        if (mode.rotation) {
            // The tracker's senses, as its apps send them: yaw positive to the right, pitch
            // positive up, roll positive tilting the head to the left. They are this camera's too.
            // A narrowed field of view magnifies the head's turn with everything else, so yaw
            // and pitch are scaled to move the picture as far as they do unzoomed. Roll turns
            // the picture by its own angle at any field of view.
            float yaw = ZoomCompensation.scaleAngle((float) Math.toRadians(filter.yaw() * stockShare), zoom);
            float pitch = ZoomCompensation.scaleAngle((float) Math.toRadians(filter.pitch() * stockShare), zoom);
            float roll = (float) Math.toRadians(filter.roll());
            ViewCamera.turn(out, yaw, pitch, roll, worldYaw);
            HeadFollowLight.follow(clean, yaw, pitch, roll, worldYaw);
            headTurn[0] = yaw;
            headTurn[1] = pitch;
            headTurn[2] = roll;
        } else {
            HeadFollowLight.release();
        }
        headTurned = mode.rotation;
        if (!mode.position) {
            clamp.reset();
            handover.stop();
            return;
        }
        // The tracker sends x positive to the head's left and z positive away from the screen.
        // Both are turned round here, after the limits, so the larger forward allowance stays
        // on the forward lean. The lean follows where the body faces and stays level: looking
        // down does not turn a lean forward into a lean into the floor.
        float right = -filter.x() * stockShare;
        float up = filter.y() * stockShare;
        float forward = -filter.z() * stockShare;
        float cos = (float) Math.cos(cleanYaw);
        float sin = (float) Math.sin(cleanYaw);
        lean[0] = -sin * right + cos * forward;
        lean[1] = cos * right + sin * forward;
        lean[2] = up;
        leanAsked = (float) Math.sqrt(right * right + up * up + forward * forward);
        // Only the lean across the view is magnified by a zoom. The lean along it is left
        // whole, so leaning in reaches as far through the sights as at the hip.
        ZoomCompensation.scaleLean(lean, clean.forwardX, clean.forwardY, clean.forwardUp, zoom);
        leanWorld = world;
        leanEyeX = clean.eyeX;
        leanEyeY = clean.eyeY;
        if (Settings.current.collisionEnabled()) {
            clamp.apply(lean, delta, roomForTheLean);
        } else {
            clamp.reset();
        }
        if (clamp.inContact() != contactLogged && now - contactLoggedNanos >= CONTACT_LOG_INTERVAL_NANOS) {
            contactLogged = clamp.inContact();
            contactLoggedNanos = now;
            Log.info(String.format(Locale.ROOT, contactLogged
                    ? "lean held off a wall: asked for %.3f, room for %.3f, eye at (%.2f, %.2f)"
                    : "lean clear of the wall again: asked for %.3f, given %.3f, eye at (%.2f, %.2f)",
                    leanAsked, length(lean), clean.eyeX, clean.eyeY));
        }
        // The eye goes where the lean puts it whichever of the two has it. What the weapon
        // has moves the weapon and the shot as well, so only the share of the view parts the
        // eye from the shot.
        handover.setForwardStop(ViewModel.forwardStop());
        handover.update(lean, clean.forwardX, clean.forwardY, clean.forwardUp, sightsUp, aimMode.isFreeLook(), onFoot && Online.leanCarriesTheShot(), nowMillis);
        out.eyeX += handover.camera[0] + handover.rig[0];
        out.eyeY += handover.camera[1] + handover.rig[1];
        out.eyeUp += handover.camera[2] + handover.rig[2];
    }

    /**
     * Tracking does not apply this frame. The next time it does it starts from the tracker's
     * pose then, with no wall from before the break rationing the lean, and a sample interval
     * measured across the break is not taken for the tracker's rate.
     */
    static void rest() {
        applied = false;
        headTurned = false;
        filter.reset();
        clamp.reset();
        handover.stop();
        stockFade.reset();
        stockShare = 1f;
        contactLogged = false;
        HeadFollowLight.release();
    }

    private static float length(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    /** One line with every term of the frame's pose, for the trace. */
    static String describe() {
        return String.format(Locale.ROOT,
                "tracking %s mode %s yaw about %s fresh %b remote %b head (yaw %.2f pitch %.2f roll %.2f)"
                        + " tracker position (%.3f, %.3f, %.3f) lean asked %.3f given world (%.3f, %.3f, %.3f) wall %b"
                        + " %s stock share %.3f view has (%.3f, %.3f, %.3f) weapon has (%.3f, %.3f, %.3f) %s",
                applied ? "applied" : enabled ? "idle" : "off", mode.label, worldYaw ? "world up" : "view up",
                receiver.isFresh(), receiver.isRemote(), filter.yaw(), filter.pitch(), filter.roll(),
                filter.x(), filter.y(), filter.z(), leanAsked, lean[0], lean[1], lean[2], clamp.inContact(),
                aimMode.label, stockShare, handover.camera[0], handover.camera[1], handover.camera[2],
                handover.rig[0], handover.rig[1], handover.rig[2], HeadFollowLight.describe());
    }
}
