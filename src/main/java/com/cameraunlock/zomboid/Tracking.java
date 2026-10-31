package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import zombie.characters.IsoPlayer;
import zombie.input.JoypadButton;

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
    /** The port each player's tracker is listened for on, or 0 while it is not. */
    private static final int[] ports = new int[View.COUNT];
    /** Which of the players after the first have been given their port. */
    private static final boolean[] listening = new boolean[View.COUNT];
    /** A save writes a file and the watch asks for its write time, so both are kept off the game's logic thread, where the hotkeys are read. */
    static final ScheduledExecutorService saver = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "CameraUnlock-Config");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile boolean enabled = true;
    private static volatile boolean worldYaw = true;
    /** Whether the last press of the yaw mode key did nothing, every view being from a vehicle's seat. Logic thread. */
    private static boolean yawKeyIdle;
    private static volatile int trackingMode = CameraUnlock.TRACKING_ROTATION_AND_POSITION;
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
    /** Each player's controller's Back button, tapped twice to switch first person. */
    private static final DoubleTap[] backTaps = new DoubleTap[View.COUNT];

    static {
        for (int i = 0; i < View.COUNT; i++) {
            backTaps[i] = new DoubleTap();
        }
    }
    /** Whose Back button is still down from a double tap: the game's wheel it put up is taken down until it is let go. */
    private static final boolean[] backHeld = new boolean[View.COUNT];

    /**
     * The players whose graphics mode changed other than by its key, a bit each by number: to
     * Custom, or lowered for split screen. The next frame drawn says so on their screens as the key does.
     */
    private static final AtomicInteger graphicsToSay = new AtomicInteger();

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

    /** What the screen says after the yaw mode key. Logic thread. */
    static String yawModeSaid() {
        return yawKeyIdle ? "Horizon-relative head turns are always off in a vehicle seat"
                : "Horizon-relative head turns: " + (worldYaw ? "on" : "off");
    }

    static float lightMultiplier() {
        return settings.lightMultiplier;
    }

    /**
     * Reads CameraUnlock.ini from the game's folder, where the mod's jar is installed, and starts
     * listening for the first player's tracker and for the hotkeys.
     */
    static void start(Path gameDirectory) {
        String defaults = System.getProperty(Agent.DEFAULTS_PROPERTY);
        int port = open(gameDirectory, defaults == null ? null : Path.of(defaults));
        ports[0] = port;
        try {
            listen(port);
            core.hotkeysStart();
        } catch (RuntimeException failure) {
            // The mod stays out of the game, and its port is let go for whatever else listens on it.
            try {
                letGo();
            } catch (RuntimeException also) {
                failure.addSuppressed(also);
            }
            throw failure;
        }
        watch = saver.scheduleWithFixedDelay(Tracking::watchSettings, WATCH_MILLISECONDS, WATCH_MILLISECONDS, TimeUnit.MILLISECONDS);
    }

    private static ScheduledFuture<?> watch;

    /** Lets go of what start took, for a start that fails after it: the port, the microphones and the watch on CameraUnlock.ini. */
    static void stop() {
        watch.cancel(false);
        letGo();
    }

    /** Nothing reads a microphone the open left open once the mod is out of the game, and no options page is there to close it. */
    private static void letGo() {
        core.stop();
        Microphones.set(false, new String[View.COUNT]);
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
        // Core's range for the concept starts at 0, and nearer than the near plane's corners the eye would see through the wall it is held off.
        if (!(settings.collisionMargin > WorldScene.NEAR_CORNER_DISTANCE)) {
            Log.error("CollisionMargin=" + settings.collisionMargin + " in " + Config.FILE_NAME + " is not more than " + WorldScene.NEAR_CORNER_DISTANCE
                    + " tiles, the near plane's reach, so a lean would see through a wall: the row's default of " + Config.COLLISION_MARGIN + " is used");
            settings.collisionMargin = Float.parseFloat(Config.COLLISION_MARGIN);
        }
        if (settings.aimMode == CameraUnlock.AIM_TRUE_FREE_LOOK) {
            settings.aimMode = CameraUnlock.AIM_FREE_LOOK_MARKER;
        }
        core.configure(settings);
        enabled = loaded.enableOnStartup;
        worldYaw = loaded.worldSpaceYaw;
        trackingMode = settings.trackingMode;
        aimMode = settings.aimMode;

        Config.Live live = config.live();
        inVehicles(live.firstPersonInVehicles());
        Part.start(live.parts());
        Own.set(live.own());
        Rules.set(live.rules());
        Quality.custom(live.custom());
        for (int player = 0; player < View.COUNT; player++) {
            Quality.set(player, live.quality()[player]);
            Quality.setOverhead(player, live.overheadQuality()[player]);
            Style.set(player, live.style()[player]);
        }
        firstPersonAtLoad.set(live.firstPerson());
        HillHeight.set(live.hillHeight());
        MuzzleFlash.set(live.muzzleFlash());
        Crosshair.set(live.crosshair());
        Regrip.set(live.regrip());
        PendingLights.set(live.spreadLightUpdates());
        SplitScreen.set(live.splitScreen());
        FieldOfView.set(live.fieldOfView());
        talk(live.talkingMouths(), live.microphones());
        Log.info("the mod's own in first person: " + Own.described(Own.rows()));
        Log.info("the mod's rules offline: " + Rules.described(Rules.rows()));
        Log.info("the mod's parts: " + Part.described());
        Log.info("hill height " + HillHeight.asked());
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

    /**
     * The tracker of a player after the first is listened for from the first frame the game
     * draws them, on the port their row of [Network] names. Logic thread.
     */
    static void listenFor(int playerIndex) {
        if (listening[playerIndex]) {
            return;
        }
        listening[playerIndex] = true;
        int port = config.playerPort(playerIndex);
        for (int other = 0; other < View.COUNT; other++) {
            if (ports[other] == port) {
                // Core refuses the port by throwing, which here would be into the game's own frame.
                Log.info(Config.playerPortRow(playerIndex) + " is " + port + ", which player " + (other + 1) + "'s tracker is listened for on: player "
                        + (playerIndex + 1) + "'s head is not tracked until CameraUnlock.ini gives them a port of their own and the game is started again");
                return;
            }
        }
        core.viewStart(playerIndex, port);
        ports[playerIndex] = port;
    }

    /** One frame of core's pipeline for a view: once per rendered frame of that view, on the logic thread. */
    static void frame(int view, CameraUnlock.FrameInput input, CameraUnlock.Frame out) {
        core.viewFrame(view, input, out);
    }

    /** After a view's frame that asked for a lean. */
    static void lean(int view, CameraUnlock.Obstruction found, CameraUnlock.Lean out) {
        core.viewLean(view, found, out);
    }

    /** FieldOfView, Quality, IsometricQuality, Style, HillHeight, MuzzleFlash, SplitScreen and the rows of [Content] and [Graphics] are the settings read again while the game runs: every other row is read at the start. */
    static void watchSettings() {
        try {
            // Core's rows as the file last gave them or a save put them, before the reload: a row the reload changes was edited in the file.
            // A key or the options screen may have changed the session's since, with its save still queued behind this.
            Config.Live was = config.live();
            Config.Live live = config.reload();
            if (live != null) {
                take(was, live);
            }
        } catch (RuntimeException failure) {
            // Nothing is above this on the thread: left uncaught, the reason never reaches the log.
            // Thrown on, it ends the watch, so a fault that stays is logged once and not every second.
            Log.error(Config.FILE_NAME + " is no longer watched for changes", failure);
            throw failure;
        }
    }

    /** Puts the rows read again while the game runs on the mod's running settings. The config thread. */
    private static void take(Config.Live was, Config.Live live) {
        if (live.fieldOfView() != was.fieldOfView()) {
            FieldOfView.set(live.fieldOfView());
            Log.info(Config.FILE_NAME + " changed: first person field of view set to " + live.fieldOfView() + " degrees vertical");
        }
        boolean edited = !live.custom().equals(was.custom());
        if (edited) {
            double[] session = Config.values(Quality.custom()), file = Config.values(live.custom()), before = Config.values(was.custom());
            for (int row = 0; row < session.length; row++) {
                if (file[row] != before[row]) {
                    session[row] = file[row];
                }
            }
            Quality.custom(Config.picture(session));
        }
        // The rows of [Graphics] are the first player's, as the game's options screen, where they are set, is.
        if (edited && live.quality()[0] != Quality.CUSTOM) {
            Log.info(Config.FILE_NAME + " changed: a row of [Graphics] was edited, which goes to the first player's Custom graphics mode");
            Quality.set(0, Quality.CUSTOM);
            sayGraphics(1);
            config.saveQuality(0, Quality.CUSTOM);
        } else if (live.quality()[0] != was.quality()[0]) {
            Quality.set(0, live.quality()[0]);
        }
        for (int player = 0; player < View.COUNT; player++) {
            if (player > 0 && live.quality()[player] != was.quality()[player]) {
                Quality.set(player, live.quality()[player]);
            }
            if (live.overheadQuality()[player] != was.overheadQuality()[player]) {
                Quality.setOverhead(player, live.overheadQuality()[player]);
            }
            if (live.style()[player] != was.style()[player]) {
                Style.set(player, live.style()[player]);
            }
        }
        // Only the rows the file changed: a toggle made since the file was read is saved after this reload, and kept.
        int changed = live.firstPerson() ^ was.firstPerson();
        firstPersonAtLoad.updateAndGet(bits -> bits & ~changed | live.firstPerson() & changed);
        inVehicles(live.firstPersonInVehicles());
        if (live.hillHeight() != was.hillHeight()) {
            HillHeight.set(live.hillHeight());
            Log.info(Config.FILE_NAME + " changed: hill height set to " + live.hillHeight());
        }
        if (live.muzzleFlash() != was.muzzleFlash()) {
            MuzzleFlash.set(live.muzzleFlash());
        }
        if (live.crosshair() != was.crosshair()) {
            Crosshair.set(live.crosshair());
        }
        if (live.regrip() != was.regrip()) {
            Regrip.set(live.regrip());
        }
        if (live.spreadLightUpdates() != was.spreadLightUpdates()) {
            PendingLights.set(live.spreadLightUpdates());
        }
        if (live.splitScreen() != was.splitScreen()) {
            SplitScreen.set(live.splitScreen());
        }
        int own = live.own() ^ was.own();
        if (own != 0) {
            Own.update(rows -> rows & ~own | live.own() & own);
            Log.info(Config.FILE_NAME + " changed: the mod's own in first person: " + Own.described(Own.rows()));
        }
        int rules = live.rules() ^ was.rules();
        if (rules != 0) {
            Rules.update(rows -> rows & ~rules | live.rules() & rules);
            Log.info(Config.FILE_NAME + " changed: the mod's rules offline: " + Rules.described(Rules.rows()));
        }
        boolean mouthsEdited = live.talkingMouths() != was.talkingMouths(), microphonesEdited = false;
        String[] names = microphones.toArray(String[]::new);
        for (int player = 0; player < names.length; player++) {
            if (!live.microphones().get(player).equals(was.microphones().get(player))) {
                names[player] = live.microphones().get(player);
                microphonesEdited = true;
            }
        }
        if (mouthsEdited || microphonesEdited) {
            talk(mouthsEdited ? live.talkingMouths() : Mouths.on(), List.of(names));
        }
        String parts = Part.described();
        Part.take(live.parts(), live.parts() ^ was.parts());
        Agent.takeIn();
        if (!Part.described().equals(parts)) {
            Log.info(Config.FILE_NAME + " changed: the mod's parts: " + Part.described());
        }
    }

    /** Each player's microphone as last taken from the file or the game's options screen. */
    private static volatile List<String> microphones = List.of();

    /** Puts TalkingMouths and the microphones on the mouths, opening and closing microphones to match. */
    private static void talk(boolean on, List<String> names) {
        microphones = names;
        Mouths.set(on);
        Microphones.set(on, names.toArray(String[]::new));
        Log.info("talking mouths " + (on ? "on, microphones " + names : "off: no microphone is opened"));
    }

    /** The settings the game's options screen shows. Logic thread, once the file is read. */
    static CameraUnlock.Option[] options() {
        return config.options();
    }

    /** An option's place in options(), by its key. */
    static int optionPlace(String id) {
        return config.optionPlace(id);
    }

    /** The settings of the options screen the session holds itself, which applyScreen takes in place of a row's put. */
    static final String TRACKING_MODE_OPTION = "TrackingMode", AIM_MODE_OPTION = "AimMode", ENABLED_OPTION = "EnableOnStartup",
            YAW_MODE_OPTION = "WorldSpaceYaw", LOCAL_SMOOTHING_OPTION = "LocalSmoothing", REMOTE_SMOOTHING_OPTION = "RemoteSmoothing",
            LIGHT_MULTIPLIER_OPTION = "LightMultiplier";
    /** Of those, the ones core's session holds, which it is given whole again (CameraUnlock.configure) when any of them changes. */
    private static final Set<String> CORE_OPTIONS = Set.of(TRACKING_MODE_OPTION, AIM_MODE_OPTION, LOCAL_SMOOTHING_OPTION, REMOTE_SMOOTHING_OPTION,
            LIGHT_MULTIPLIER_OPTION);

    /**
     * The settings applied on the options screen whose save has still to run, by key. Core holds
     * a value applied there from when its save runs on the config thread, so a screen opened
     * before then reads it from here.
     */
    private static final Map<String, Applied> unsaved = new ConcurrentHashMap<>();

    /** A value one apply gave a setting. It keeps Object's equals, so a save takes off its own apply's values and never an equal one applied since. */
    private static final class Applied {
        final double value;

        Applied(double value) {
            this.value = value;
        }
    }

    /**
     * What an option reads as now. The settings the session holds itself read as the session has
     * them, and so do the graphics modes. A row of [Graphics] reads as the picture the first
     * player's first person mode draws (Quality.pictureOf), so in Low, Medium, High and Automatic
     * the screen shows that mode's, and in Custom the row itself. Any other reads as core holds
     * it, or as an apply left it while that apply's save has still to run.
     */
    static double optionValue(int option) {
        String id = config.optionId(option);
        int part = Config.graphicsPart(id), player = qualityPlayer(id), overheadPlayer = overheadQualityPlayer(id);
        return switch (id) {
            case TRACKING_MODE_OPTION -> trackingMode;
            case AIM_MODE_OPTION -> aimMode;
            case ENABLED_OPTION -> enabled ? 1 : 0;
            case YAW_MODE_OPTION -> worldYaw ? 1 : 0;
            case LOCAL_SMOOTHING_OPTION -> settings.localSmoothing;
            case REMOTE_SMOOTHING_OPTION -> settings.remoteSmoothing;
            case LIGHT_MULTIPLIER_OPTION -> settings.lightMultiplier;
            default -> part >= 0 ? Config.values(Quality.pictureOf(Quality.row(0)))[part]
                    : player >= 0 ? Quality.row(player)
                    : overheadPlayer >= 0 ? Quality.overheadRow(overheadPlayer) - Quality.LOW
                    : held(id, option);
        };
    }

    /** What core holds for an option, or the value an apply gave it while that apply's save has still to run. */
    private static double held(String id, int option) {
        Applied applied = unsaved.get(id);
        return applied != null ? applied.value : config.optionValue(option);
    }

    /** The player whose first person graphics mode an option is, or -1 for an option that is none's. */
    static int qualityPlayer(String id) {
        return playerOf(id, Config::qualityKey);
    }

    /** The player whose isometric graphics mode an option is, or -1 for an option that is none's. */
    static int overheadQualityPlayer(String id) {
        return playerOf(id, Config::overheadQualityKey);
    }

    private static int playerOf(String id, IntFunction<String> keyOf) {
        for (int player = 0; player < View.COUNT; player++) {
            if (id.equals(keyOf.apply(player))) {
                return player;
            }
        }
        return -1;
    }

    /** A slider's notch nearest a value, worked out as the game's ISSliderPanel.setCurrentValue works it out, so the two are the same number. */
    static double notch(double value, double step) {
        double perStep = 1 / step;
        return Math.floor(value * perStep + 0.5) / perStep;
    }

    /** Whether applyScreen has somewhere to put an option's value. */
    static boolean takes(CameraUnlock.Option described) {
        return CORE_OPTIONS.contains(described.id) || described.id.equals(ENABLED_OPTION) || described.id.equals(YAW_MODE_OPTION)
                || config.takes(described.id);
    }

    /**
     * What the player applied on the game's options screen: each setting that changed, by its key,
     * as its option's number, and the picture of the first player's Custom graphics mode where the
     * screen changed it, else null. The session takes every one at once, so a key pressed a moment
     * later is neither undone nor saved over. The patches of the parts switched on go into the
     * game's code once for the lot, and one save follows on the config thread. With a picture the
     * first player's first person graphics mode goes to Custom. The modes are saved as the session
     * has them, which is how their keys save them. Logic thread.
     *
     * A setting that cannot be taken is written to the log and left out, and the rest are still
     * taken: thrown on, it would stop the game from applying and saving its own options.
     *
     * @return the keys of the settings taken, with player 1's Quality and the rows of [Graphics]
     *     where the picture was
     */
    static Set<String> applyScreen(Map<String, Double> changed, Quality.Picture custom) {
        return apply(changed, custom, "the game's options screen");
    }

    /**
     * Settings a key needs to show what it was pressed for, taken and saved as applyScreen takes
     * and saves the options screen's, and logged as taken from the key. Logic thread.
     */
    static void applyForKey(Map<String, Double> needed, String key) {
        apply(needed, null, key);
    }

    private static Set<String> apply(Map<String, Double> changed, Quality.Picture custom, String from) {
        boolean pictured = false;
        if (custom != null) {
            // Before player 1's graphics mode, which may be among the settings: Custom comes on drawing this picture, not the one before.
            try {
                Quality.custom(custom);
                Quality.set(0, Quality.CUSTOM);
                sayGraphics(1);
                pictured = true;
            } catch (RuntimeException failure) {
                Log.error("the Custom graphics settings as applied on the game's options screen were not taken", failure);
            }
        }
        Map<String, Double> taken = new LinkedHashMap<>();
        boolean configure = false;
        for (Map.Entry<String, Double> setting : changed.entrySet()) {
            try {
                configure |= take(setting.getKey(), setting.getValue());
                taken.put(setting.getKey(), setting.getValue());
            } catch (RuntimeException failure) {
                Log.error(setting.getKey() + " as applied from " + from + " was not taken", failure);
            }
        }
        if (configure) {
            // The session has no call that sets the tracking mode or the smoothing alone.
            settings.trackingMode = trackingMode;
            settings.aimMode = aimMode;
            core.configure(settings);
        }
        if (taken.isEmpty() && !pictured) {
            return taken.keySet();
        }
        Agent.takeIn();
        StringJoiner now = new StringJoiner(", "), atStart = new StringJoiner(", ");
        taken.forEach((key, value) -> (Part.nextStart(key) ? atStart : now).add(key + " " + said(key, value)));
        if (pictured) {
            now.add("player 1's Custom graphics");
        }
        Log.info(now.length() == 0 ? "saved from " + from + " for the next start: " + atStart
                : "taken from " + from + ": " + now + (atStart.length() > 0 ? "; saved for the next start: " + atStart : ""));
        boolean savePicture = pictured;
        Map<String, Applied> applied = new LinkedHashMap<>();
        taken.forEach((key, value) -> applied.put(key, new Applied(value)));
        unsaved.putAll(applied);
        save(() -> {
            try {
                Map<String, Double> saving = new LinkedHashMap<>(taken);
                if (savePicture) {
                    // The picture the save finds, which holds any row a reload took from the file since.
                    double[] values = Config.values(Quality.custom());
                    for (int part = 0; part < values.length; part++) {
                        saving.put(Config.GRAPHICS[part], values[part]);
                    }
                    saving.put(Config.qualityKey(0), (double) Quality.CUSTOM);
                }
                config.saveOptions(saving);
            } finally {
                applied.forEach(unsaved::remove);
            }
        });
        Set<String> keys = new LinkedHashSet<>(taken.keySet());
        if (pictured) {
            keys.addAll(List.of(Config.GRAPHICS));
            keys.add(Config.qualityKey(0));
        }
        return keys;
    }

    /** Puts one setting applied on the options screen on the session. Answers whether core's session is to be given its settings again. */
    private static boolean take(String id, double value) {
        switch (id) {
            case TRACKING_MODE_OPTION -> trackingMode = (int) value;
            case AIM_MODE_OPTION -> aimMode = (int) value;
            case LOCAL_SMOOTHING_OPTION -> settings.localSmoothing = (float) value;
            case REMOTE_SMOOTHING_OPTION -> settings.remoteSmoothing = (float) value;
            case LIGHT_MULTIPLIER_OPTION -> settings.lightMultiplier = (float) value;
            case ENABLED_OPTION -> enabled = value != 0;
            case YAW_MODE_OPTION -> worldYaw = value != 0;
            default -> config.put(id, value);
        }
        return CORE_OPTIONS.contains(id);
    }

    /** A value an option was given as the log says it, "75" or "0.2", and a list's by the name of its choice. */
    private static String said(String id, double value) {
        CameraUnlock.Option option = options()[optionPlace(id)];
        if (option.kind == CameraUnlock.OPTION_ENUM) {
            return option.choices[(int) value];
        }
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    /** Has the next frame drawn say the graphics mode on the screens of the players of the bits. */
    private static void sayGraphics(int players) {
        graphicsToSay.getAndAccumulate(players, (views, bits) -> views | bits);
    }

    /**
     * The views whose screen says their graphics mode in this frame, a bit each by number, with
     * the game's menu up (OptionsPage.menuShown) or not: once after it changed other than by its
     * key, and for each player in first person in Automatic once after it stepped, on the first
     * frame the menu is not up. The options screen is opened from that menu in a save, and said
     * under it, it was gone before the menu was shut. Only a player at the screen is told. Logic
     * thread.
     */
    static int graphicsToTell(boolean menuShown) {
        boolean stepped = Quality.stepToSay();
        if (menuShown || graphicsToSay.get() == 0 && !stepped) {
            return 0;
        }
        int views = graphicsToSay.getAndSet(0);
        Quality.stepSaid();
        for (int player = 0; player < View.COUNT; player++) {
            int bit = 1 << player;
            if (stepped && Quality.firstPersonInEffect(player) && Quality.row(player) == Quality.AUTOMATIC) {
                views |= bit;
            }
            if (!Quality.present(player)) {
                views &= ~bit;
            } else if ((views & bit) != 0) {
                Log.info("said on player " + (player + 1) + "'s screen: " + Quality.said(player));
            }
        }
        return views;
    }

    /** The players whose graphics mode key was pressed in this frame, a bit each by number: their modes change at the start of the next. Logic thread. */
    private static int graphicsModeOwed;

    /** Whether a view's graphics mode changes at the start of the next frame: this frame's picture is the one before. Logic thread. */
    static boolean graphicsModeOwed(int view) {
        return (graphicsModeOwed & 1 << view) != 0;
    }

    /** The keys pressed between now and the next frame drawn are dropped, not acted on. Logic thread. */
    static void dropTheNextPresses() {
        outOfSave = true;
    }

    /**
     * Once for each player drawn in a frame of a save, before that player's view: what was
     * pressed that is theirs to act on or to say, as Config's bits. The keys are read at the
     * first player drawn in the frame, and what is every view's is done there and then.
     *
     * @param first whether this is the first player the game draws in this frame
     */
    static int pressedFor(int playerIndex, boolean first) {
        if (first) {
            boolean enteringSave = outOfSave;
            int pressed = 0, told = cycleOwedGraphicsModes();
            // The hotkeys are heard whatever the game is doing with the keyboard, so a key that goes into one of its text boxes is not an action as well.
            if (outOfSave || zombie.core.Core.getInstance().isDoingTextEntry()) {
                outOfSave = false;
                core.hotkeysDrop();
            } else {
                pressed = act(core.hotkeysTake());
                if ((pressed & Config.SPLIT_SCREEN) != 0) {
                    if (IsoPlayer.numPlayers == 2) {
                        turnSplitScreen();
                    } else {
                        Log.info("the split screen key turns the split of two players and does nothing with " + IsoPlayer.numPlayers + " at the screen");
                    }
                }
            }
            sayGraphics(saveLowered(Quality.players(IsoPlayer.numPlayers)));
            if (enteringSave) {
                graphicsToSay.set(0);
                Quality.stepSaid();
            } else {
                told |= graphicsToTell(OptionsPage.menuShown());
            }
            SplitScreen.frame();
            owe(pressed, told);
            backButtons();
        }
        if (playerIndex >= View.COUNT) {
            return 0;
        }
        int mine = owed[playerIndex];
        owed[playerIndex] = 0;
        return mine;
    }

    /** Keeps for the next start the graphics modes of the players split screen lowered, a bit each, and hands the bits back. */
    private static int saveLowered(int lowered) {
        for (int i = 0; i < View.COUNT; i++) {
            if ((lowered & 1 << i) != 0) {
                int player = i, firstQuality = Quality.row(i), overhead = Quality.overheadRow(i);
                save(() -> {
                    config.saveQuality(player, firstQuality);
                    config.saveIsometricQuality(player, overhead);
                });
            }
        }
        return lowered;
    }

    /**
     * What each player's view is owed of this frame's presses: what every view says, the
     * graphics mode said where told has their bit, and the player keys that are theirs.
     */
    private static void owe(int pressed, int told) {
        int said = pressed & (Config.AIM_MODE | Config.SPLIT_SCREEN | Config.CYCLE_TRACKING_MODE | Config.YAW_MODE);
        // Held, the key puts every player at the screen into one view: first person if any of them is out of it, else the game's own.
        boolean allFirst = false;
        if ((pressed & Config.FIRST_PERSON_ALL) != 0) {
            for (int i = 0; i < View.COUNT; i++) {
                View view = View.of(i);
                allFirst |= atScreen(i) && (view == null || !view.firstPerson.wanted());
            }
            Log.info("the first person key was held: every player to " + (allFirst ? "first person" : "the isometric view"));
        }
        for (int i = 0; i < View.COUNT; i++) {
            owed[i] = said | ((told & 1 << i) != 0 ? Config.CYCLE_QUALITY : 0);
            if ((pressed & Config.FIRST_PERSON_ALL) != 0) {
                View view = View.of(i);
                if (atScreen(i) && (view == null ? allFirst : view.firstPerson.wanted() != allFirst)) {
                    owed[i] |= Config.FIRST_PERSON;
                }
            } else if (theirs(pressed, i, Config.FIRST_PERSON_TAPS, 0, "first person")) {
                owed[i] |= Config.FIRST_PERSON;
            }
            if (theirs(pressed, i, Config.CYCLE_QUALITY_TAPS, Config.CYCLE_QUALITY_ALL, "graphics")) {
                View view = View.of(i);
                if (view != null && view.pipeline.failure() != null) {
                    // Every graphics mode but the game's own is drawn by first person: the key says why there are none.
                    owed[i] |= Config.CYCLE_QUALITY;
                    Log.info("player " + (i + 1) + "'s graphics key changes nothing: " + view.pipeline.failure());
                } else {
                    // Changed on the next frame: this one's picture is the view's picture before (ModeFade).
                    graphicsModeOwed |= 1 << i;
                }
            }
            if (theirs(pressed, i, Config.CYCLE_STYLE_TAPS, Config.CYCLE_STYLE_ALL, "style")) {
                cycleStyle(i);
                owed[i] |= Config.CYCLE_STYLE;
            }
        }
    }

    /**
     * Whether a key of the player keys was pressed for a player: tapped once for the first player,
     * twice for the second and so on, or held for every player at the screen. A key tapped for a
     * player who is not at the screen does nothing.
     */
    private static boolean theirs(int pressed, int player, int[] taps, int held, String key) {
        boolean tapped = (pressed & taps[player]) != 0, here = atScreen(player);
        if (tapped && !here) {
            Log.info("the " + key + " key was tapped " + (player + 1) + " times, which is player " + (player + 1) + "'s, and there is none at the screen");
        }
        return here && (tapped || (pressed & held) != 0);
    }

    private static boolean atScreen(int player) {
        return player == 0 || IsoPlayer.numPlayers > player && IsoPlayer.players[player] != null;
    }

    /**
     * A player on a controller switches their own first person with the Back button tapped
     * twice. The game puts its wheel up on every press of the button, the second's as well,
     * and that is taken down again for as long as the button is held.
     */
    private static void backButtons() {
        long now = System.nanoTime();
        for (int i = 0; i < View.COUNT; i++) {
            IsoPlayer player = IsoPlayer.players[i];
            // With first person switched off the button is the game's alone: its wheel is left up.
            boolean down = Part.FIRST_PERSON.running() && player != null && View.onController(player) && JoypadButton.Back.isDown(player.getJoypadBind());
            if (backTaps[i].press(down, now)) {
                owed[i] |= Config.FIRST_PERSON;
                backHeld[i] = true;
                Log.info("player " + (i + 1) + "'s Back button tapped twice, which asks for their first person to be switched");
            }
            backHeld[i] &= down;
            if (backHeld[i]) {
                Panels.shutBackWheel(i);
            }
        }
    }

    /** A save was left: what is pressed until a frame of the next is not acted on. */
    static void leftSave() {
        outOfSave = true;
        Quality.players(0);
        cycleOwedGraphicsModes();
    }

    /** Steps the graphics mode of each player whose key was pressed in the frame before, and answers which those were, a bit each. */
    private static int cycleOwedGraphicsModes() {
        int owedNow = graphicsModeOwed;
        graphicsModeOwed = 0;
        for (int i = 0; i < View.COUNT; i++) {
            if ((owedNow & 1 << i) != 0) {
                cycleGraphicsMode(i);
            }
        }
        return owedNow;
    }

    /** Acts on the head tracking actions among the bits, and hands them all back. */
    static int act(int pressed) {
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

    /** Each player's microphone, player 1's first, as the session has them. */
    static List<String> microphones() {
        return microphones;
    }

    /**
     * A microphone the player chose for a player on the game's options screen. As with a key, the
     * session takes it at once and its save follows on the config thread. Logic thread.
     */
    static void applyMicrophone(int player, String name) {
        String[] names = microphones.toArray(String[]::new);
        names[player] = name;
        microphones = List.of(names);
        Microphones.set(Mouths.on(), names);
        Log.info("player " + (player + 1) + "'s microphone set to \"" + name + "\"");
        save(() -> config.saveMicrophone(player, name));
    }

    /** The new value is already applied. A save that fails leaves the session on it, and the log says why. */
    private static void save(Runnable save) {
        saver.execute(() -> {
            try {
                save.run();
            } catch (RuntimeException failure) {
                // Nothing is above this on the thread: left uncaught, the reason never reaches the log.
                Log.error("a changed setting was not saved", failure);
                Config.trouble("A setting you changed was not saved to " + Config.FILE_NAME);
            }
        });
    }

    static void cycleMode() {
        trackingMode = core.cycleTrackingMode();
        Log.info("head tracking mode: " + TRACKING_MODE_LABEL[trackingMode]);
        save(config::saveTrackingMode);
    }

    /** A view says, each frame it has a player, whether it is from a vehicle's seat. */
    static void rides(int view, boolean inVehicle) {
        viewed[view] = true;
        seated[view] = inVehicle;
    }

    /** A view has no player. */
    static void gone(int view) {
        viewed[view] = false;
    }

    /** From a vehicle's seat head yaw turns about the view's own up whatever is saved, so the key does nothing while every view there is, is from one. */
    static void toggleWorldYaw() {
        boolean any = false, onFoot = false;
        for (int i = 0; i < View.COUNT; i++) {
            any |= viewed[i];
            onFoot |= viewed[i] && !seated[i];
        }
        yawKeyIdle = any && !onFoot;
        if (yawKeyIdle) {
            Log.info("the yaw mode key does nothing from a vehicle's seat, where head yaw turns about the view's own up");
            return;
        }
        worldYaw = !worldYaw;
        Log.info("head yaw turns about " + (worldYaw ? "straight up" : "the view's own up"));
        boolean saved = worldYaw;
        save(() -> config.saveWorldSpaceYaw(saved));
    }

    /**
     * A player's graphics mode after the one in effect, their first person's or their isometric
     * view's, kept for the next start. The other view keeps its own.
     */
    static void cycleGraphicsMode(int player) {
        boolean firstPerson = Quality.firstPersonInEffect(player);
        int mode = firstPerson ? Quality.next(player) : Quality.nextOverhead(player);
        setGraphicsMode(player, firstPerson, mode);
        if (mode == Quality.LOW) {
            return;
        }
        // Past Low a mode is the mod's picture, which shows nothing of Low's difference with every Vanilla row on or with no first person.
        Map<String, Double> needed = new LinkedHashMap<>();
        if (Own.rows() == 0) {
            for (String key : Config.VANILLA_KEYS) {
                needed.put(key, 0.0);
            }
        }
        if (!Part.FIRST_PERSON.on()) {
            needed.put(Part.FIRST_PERSON.key, 1.0);
        }
        if (!needed.isEmpty()) {
            applyForKey(needed, "the graphics key, so that " + Quality.said(player) + " looks as it does");
        }
    }

    /** A player's graphics mode of their first person or their isometric view, kept for the next start. */
    static void setGraphicsMode(int player, boolean firstPerson, int mode) {
        if (firstPerson) {
            Quality.set(player, mode);
            save(() -> config.saveQuality(player, mode));
        } else {
            Quality.setOverhead(player, mode);
            save(() -> config.saveIsometricQuality(player, mode));
        }
    }

    /** A player's picture style after the one they are in, kept for the next start. */
    static void cycleStyle(int player) {
        Style next = Style.of(player).next();
        Style.set(player, next.ordinal());
        save(() -> config.saveStyle(player, next));
    }

    /**
     * For each player, by their number's bit, whether their save is to load in first person: the file's FirstPerson rows.
     * One player's bit is changed on the logic thread while a reload of the file sets them all on the config thread.
     */
    private static final AtomicInteger firstPersonAtLoad = new AtomicInteger();

    static boolean firstPersonRemembered(int player) {
        return (firstPersonAtLoad.get() & 1 << player) != 0;
    }

    static void firstPersonAtLoad(int player, boolean on) {
        setBit(firstPersonAtLoad, player, on);
    }

    /** For each player, by their number's bit, whether getting into a vehicle and out again switches the view: the file's FirstPersonInVehicles rows. */
    private static final AtomicInteger firstPersonInVehicles = new AtomicInteger();

    static boolean firstPersonInVehicles(int player) {
        return (firstPersonInVehicles.get() & 1 << player) != 0;
    }

    static void firstPersonInVehicles(int player, boolean on) {
        setBit(firstPersonInVehicles, player, on);
        Part.firstPersonInVehicles(firstPersonInVehicles.get() != 0);
    }

    private static void inVehicles(int players) {
        firstPersonInVehicles.set(players);
        Part.firstPersonInVehicles(players != 0);
    }

    private static void setBit(AtomicInteger bits, int player, boolean on) {
        bits.updateAndGet(was -> on ? was | 1 << player : was & ~(1 << player));
    }

    /** The keys that switch first person, as CameraUnlock.ini names them. */
    static String firstPersonKeys() {
        return config.firstPersonKeys();
    }

    /** The keys that go to the next graphics mode, as CameraUnlock.ini names them. */
    static String cycleQualityKeys() {
        return config.cycleQualityKeys();
    }

    /** The keys that go to the next picture style, as CameraUnlock.ini names them. */
    static String cycleStyleKeys() {
        return config.cycleStyleKeys();
    }

    /** The keys that turn the split screen, as CameraUnlock.ini names them. */
    static String splitScreenKeys() {
        return config.splitScreenKeys();
    }

    /** The keys that go to the next tracking mode, as the game started with them. */
    static String cycleTrackingModeKeys() {
        return config.cycleTrackingModeKeys();
    }

    /** The keys that switch horizon-relative head turns, as the game started with them. */
    static String yawModeKeys() {
        return config.yawModeKeys();
    }

    /** The keys that go to the next aim mode, as the game started with them. */
    static String aimModeKeys() {
        return config.aimModeKeys();
    }

    /** The view a player switched to, kept for the next load. */
    static void rememberFirstPerson(int player, boolean on) {
        firstPersonAtLoad(player, on);
        save(() -> config.saveFirstPerson(player, on));
    }

    /** The other way for two players to share the screen, kept for the next start. */
    static void turnSplitScreen() {
        int next = SplitScreen.row() == SplitScreen.VERTICAL ? SplitScreen.HORIZONTAL : SplitScreen.VERTICAL;
        SplitScreen.set(next);
        // Left out of the game's code while the row was Vertical as the game started.
        Agent.takeIn();
        save(() -> config.saveSplitScreen(next));
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
