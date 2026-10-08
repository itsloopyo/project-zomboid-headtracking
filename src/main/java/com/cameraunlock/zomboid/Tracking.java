package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import zombie.characters.IsoPlayer;

/**
 * What every view at this screen has in common: cameraunlock-core, CameraUnlock.ini, whether
 * tracking is on, its modes, and the hotkeys, which are one keyboard's. Each view's own head
 * pose is its HeadTracking's, from a tracker of its own.
 *
 * The modes are changed on the game's logic thread, where the hotkeys are read. A thread of
 * its own saves a changed mode to the file and watches it for the rows read again while the
 * game runs.
 */
final class Tracking {
    private static final String[] TRACKING_MODE_LABEL = {"rotation and position", "rotation only", "position only"};

    /**
     * The aim modes in the order the key cycles them. Core has a fourth, free look with no
     * marker. The marker here is the game's own reticle, which is always drawn where the shot
     * lands, so that mode would be free look with a marker again and is left out. A file that
     * holds it is read as free look with a marker.
     */
    static final int[] AIM_MODES = {CameraUnlock.AIM_SIGHTS_LOCKED, CameraUnlock.AIM_FREE_LOOK_MARKER, CameraUnlock.AIM_STOCK_SIGHTS};

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

    private static volatile boolean enabled = true;
    private static volatile boolean worldYaw = true;
    private static int trackingMode = CameraUnlock.TRACKING_ROTATION_AND_POSITION;
    private static volatile int aimMode = CameraUnlock.AIM_SIGHTS_LOCKED;

    /**
     * No frame of a save has run since the game started or the last save was left. The hotkeys
     * are listened for all the while, in the menus too, and what was pressed there is not acted on.
     */
    private static boolean outOfSave = true;
    /** Which views are from a vehicle's seat, of those there are. */
    private static final boolean[] viewed = new boolean[View.COUNT], seated = new boolean[View.COUNT];
    /** What was pressed this frame that a view drawn later in it has still to be given. */
    private static final int[] owed = new int[View.COUNT];

    private Tracking() {}

    static CameraUnlock.Settings settings() {
        return settings;
    }

    static boolean enabled() {
        return enabled;
    }

    static boolean worldYaw() {
        return worldYaw;
    }

    static int aimMode() {
        return aimMode;
    }

    /** Whether the aim mode leaves the weapon where it is and the head free to move round it. */
    static boolean freeLook() {
        return aimMode == CameraUnlock.AIM_FREE_LOOK_MARKER;
    }

    static String aimModeLabel() {
        return core.aimModeLabel(aimMode);
    }

    static String trackingModeLabel() {
        return TRACKING_MODE_LABEL[trackingMode];
    }

    static float lightMultiplier() {
        return settings.lightMultiplier;
    }

    /**
     * Reads CameraUnlock.ini from the game's folder, where the mod's jar is installed, and starts
     * listening for the first player's tracker and for the hotkeys.
     */
    static void start(Path gameDirectory) {
        int port = open(gameDirectory, null);
        listen(port);
        core.hotkeysStart();
        saver.scheduleWithFixedDelay(Tracking::watchSettings, WATCH_MILLISECONDS, WATCH_MILLISECONDS, TimeUnit.MILLISECONDS);
    }

    /**
     * Describes and reads CameraUnlock.ini and puts what it holds on core's session and on the
     * mod's own settings.
     *
     * @param defaults null for the player's own Defaults.ini, a scratch file in a test
     * @return the UDP port the file names for the first player's tracker
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

    /** One frame of core's pipeline for a view: once per rendered frame of that view, on the logic thread. */
    static void frame(int view, CameraUnlock.FrameInput input, CameraUnlock.Frame out) {
        core.frame(input, out);
    }

    /** After a view's frame that asked for a lean. */
    static void lean(int view, CameraUnlock.Obstruction found, CameraUnlock.Lean out) {
        core.lean(found, out);
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
     * Once for each player drawn in a frame of a save, before that player's view: what was
     * pressed that is theirs to act on or to say, as Config's bits. The keys are read at the
     * first player drawn, and what is every view's is done there and then.
     */
    static int pressedFor(int playerIndex) {
        if (playerIndex == firstDrawn()) {
            int pressed = 0;
            // The hotkeys are heard whatever the game is doing with the keyboard, so a key that goes into one of its text boxes is not an action as well.
            if (outOfSave || zombie.core.Core.getInstance().isDoingTextEntry()) {
                outOfSave = false;
                core.hotkeysDrop();
            } else {
                pressed = act(core.hotkeysTake());
                if ((pressed & Config.CYCLE_QUALITY) != 0) {
                    cycleGraphicsMode();
                }
                if ((pressed & Config.CYCLE_STYLE) != 0) {
                    cycleStyle();
                }
            }
            int said = pressed & (Config.AIM_MODE | Config.CYCLE_QUALITY | Config.CYCLE_STYLE);
            owed[0] = said | pressed & Config.FIRST_PERSON;
            owed[1] = said;
        }
        if (playerIndex >= View.COUNT) {
            return 0;
        }
        int mine = owed[playerIndex];
        owed[playerIndex] = 0;
        return mine;
    }

    private static int firstDrawn() {
        for (int i = 0; i < IsoPlayer.numPlayers; i++) {
            if (IsoPlayer.players[i] != null) {
                return i;
            }
        }
        return 0;
    }

    /** A save was left: what is pressed until a frame of the next is not acted on. */
    static void leftSave() {
        outOfSave = true;
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

    /** A view says whether it is from a vehicle's seat. */
    static void rides(int view, boolean inVehicle) {
        viewed[view] = true;
        seated[view] = inVehicle;
    }

    /** From a vehicle's seat head yaw turns about the view's own up whatever is saved, so the key does nothing while every view there is, is from one. */
    static void toggleWorldYaw() {
        boolean any = false, onFoot = false;
        for (int i = 0; i < View.COUNT; i++) {
            any |= viewed[i];
            onFoot |= viewed[i] && !seated[i];
        }
        if (any && !onFoot) {
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
}
