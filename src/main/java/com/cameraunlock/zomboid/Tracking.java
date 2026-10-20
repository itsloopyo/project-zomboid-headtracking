package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
     * The rows of [Graphics] as the file holds them, as far as the config thread knows: what the
     * watch tells an edit of them by. Only the config thread has it once the file is read.
     */
    private static Quality.Picture customInFile;
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
        listen(port);
        try {
            core.hotkeysStart();
        } catch (RuntimeException failure) {
            // The mod stays out of the game, and its port is let go for whatever else listens on it.
            core.stop();
            throw failure;
        }
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
        Rules.set(live.rules());
        customInFile = live.custom();
        Quality.custom(live.custom());
        for (int player = 0; player < View.COUNT; player++) {
            Quality.set(player, live.quality()[player]);
            Quality.setOverhead(player, live.overheadQuality()[player]);
            Style.set(player, live.style()[player]);
        }
        HillHeight.set(live.hillHeight());
        MuzzleFlash.set(live.muzzleFlash());
        SplitScreen.set(live.splitScreen());
        FieldOfView.set(live.fieldOfView());
        talk(live);
        Log.info("the mod's own in first person: " + Own.described(Own.rows()));
        Log.info("the mod's rules offline: " + Rules.described(Rules.rows()));
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
            Config.Live live = config.reload();
            if (live != null) {
                take(live);
            }
        } catch (RuntimeException failure) {
            // Nothing is above this on the thread: left uncaught, the reason never reaches the log.
            // Thrown on, it ends the watch, so a fault that stays is logged once and not every second.
            Log.error(Config.FILE_NAME + " is no longer watched for changes", failure);
            throw failure;
        }
    }

    /** Puts the rows read again while the game runs on the mod's running settings. The config thread. */
    private static void take(Config.Live live) {
        if (live.fieldOfView() != FieldOfView.degrees()) {
            FieldOfView.set(live.fieldOfView());
            Log.info(Config.FILE_NAME + " changed: first person field of view set to " + live.fieldOfView() + " degrees vertical");
        }
        boolean edited = !live.custom().equals(customInFile);
        if (edited) {
            customInFile = live.custom();
            Quality.custom(live.custom());
        }
        // The rows of [Graphics] are the first player's, as the game's options screen, where they are set, is.
        if (edited && live.quality()[0] != Quality.CUSTOM) {
            Log.info(Config.FILE_NAME + " changed: a row of [Graphics] was edited, which goes to the first player's Custom graphics mode");
            Quality.set(0, Quality.CUSTOM);
            sayGraphics(1);
            config.saveQuality(0, Quality.CUSTOM);
        } else {
            Quality.set(0, live.quality()[0]);
        }
        for (int player = 0; player < View.COUNT; player++) {
            if (player > 0) {
                Quality.set(player, live.quality()[player]);
            }
            Quality.setOverhead(player, live.overheadQuality()[player]);
            Style.set(player, live.style()[player]);
        }
        if (live.hillHeight() != HillHeight.asked()) {
            HillHeight.set(live.hillHeight());
            Log.info(Config.FILE_NAME + " changed: hill height set to " + live.hillHeight());
        }
        MuzzleFlash.set(live.muzzleFlash());
        SplitScreen.set(live.splitScreen());
        if (live.own() != Own.rows()) {
            Own.set(live.own());
            Log.info(Config.FILE_NAME + " changed: the mod's own in first person: " + Own.described(live.own()));
        }
        if (live.rules() != Rules.rows()) {
            Rules.set(live.rules());
            Log.info(Config.FILE_NAME + " changed: the mod's rules offline: " + Rules.described(live.rules()));
        }
        if (live.talkingMouths() != Mouths.on() || !live.microphones().equals(microphones)) {
            talk(live);
        }
    }

    /** Each player's microphone as last taken from the file or the game's options screen. */
    private static volatile List<String> microphones = List.of();

    /** Puts TalkingMouths and the microphones on the mouths, opening and closing microphones to match. */
    private static void talk(Config.Live live) {
        microphones = live.microphones();
        Mouths.set(live.talkingMouths());
        Microphones.set(live.talkingMouths(), microphones.toArray(String[]::new));
        Log.info("talking mouths " + (live.talkingMouths() ? "on, microphones " + microphones : "off: no microphone is opened"));
    }

    /** The settings the game's options screen shows. Logic thread, once the file is read. */
    static CameraUnlock.Option[] options() {
        return config.options();
    }

    /**
     * What an option reads as now. A row of [Graphics] reads as the picture the first player's
     * first person mode draws, so in Low, Medium, High and Automatic the screen shows that mode's,
     * and in Custom the row itself. The graphics modes read as the session's: core has the mode an
     * apply went to only once its save, which follows on the config thread, is made.
     */
    static double optionValue(int option) {
        String id = config.optionId(option);
        int part = Config.graphicsPart(id), player = qualityPlayer(id), overheadPlayer = overheadQualityPlayer(id);
        return part >= 0 ? Config.values(Quality.firstPersonPicture(0))[part]
                : player >= 0 ? Quality.row(player)
                : overheadPlayer >= 0 ? Quality.overheadRow(overheadPlayer) - Quality.LOW
                : config.optionValue(option);
    }

    /** The player whose first person graphics mode an option is, or -1 for an option that is none's. */
    static int qualityPlayer(String id) {
        for (int player = 0; player < View.COUNT; player++) {
            if (id.equals(Config.qualityKey(player))) {
                return player;
            }
        }
        return -1;
    }

    /** The player whose isometric graphics mode an option is, or -1 for an option that is none's. */
    static int overheadQualityPlayer(String id) {
        for (int player = 0; player < View.COUNT; player++) {
            if (id.equals(Config.overheadQualityKey(player))) {
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

    private static final String YAW_MODE_OPTION = "WorldSpaceYaw";

    /** Whether applyOption has somewhere to put an option's value. */
    static boolean takes(CameraUnlock.Option described) {
        return described.source != CameraUnlock.OPTION_ROW || described.id.equals(YAW_MODE_OPTION) || config.takes(described.id);
    }

    /**
     * A setting the player applied on the game's options screen, by its place in options(). As
     * with a key, the session takes it at once and its save follows on the config thread, each
     * setting's by itself, so a key pressed a moment later is neither undone nor saved over.
     * The modes are saved as the session's, which is how their keys save them. Logic thread.
     */
    static void applyOption(int option, CameraUnlock.Option described, double value) {
        if (described.source == CameraUnlock.OPTION_TRACKING_MODE) {
            trackingMode = (int) value;
            // The session has no call that sets this mode alone.
            settings.trackingMode = trackingMode;
            settings.aimMode = aimMode;
            core.configure(settings);
            Log.info("head tracking mode: " + TRACKING_MODE_LABEL[trackingMode]);
            save(config::saveTrackingMode);
        } else if (described.source == CameraUnlock.OPTION_AIM_MODE) {
            aimMode = (int) value;
            core.setAimMode(aimMode);
            Log.info(aimModeLabel());
            save(config::saveAimMode);
        } else {
            if (described.id.equals(YAW_MODE_OPTION)) {
                worldYaw = value != 0;
                Log.info("head yaw turns about " + (worldYaw ? "straight up" : "the view's own up"));
            } else {
                config.put(described.id, value);
            }
            save(() -> config.saveOption(option, value));
        }
    }

    /**
     * What the player applied on the game's options screen: for each of its settings by its place
     * in options(), what the screen showed as it opened or was last applied and what it holds now.
     * Each setting that changed is taken as applyOption takes it. If any of the rows of [Graphics]
     * changed, the first player's first person graphics mode goes to Custom, whatever the Quality setting holds: each row the
     * player changed takes what the screen holds, and the rest take the picture Custom starts
     * from (Quality.customFrom). Each setting taken has its shown value set to what it holds.
     * Logic thread.
     *
     * One setting that cannot be taken is written to the log and the rest are still taken: thrown
     * on, it would stop the game from applying and saving its own options.
     */
    static void applyScreen(int[] options, CameraUnlock.Option[] described, double[] shown, double[] held) {
        boolean custom = false;
        for (int i = 0; i < options.length; i++) {
            custom |= Config.graphicsPart(described[i].id) >= 0 && held[i] != shown[i];
        }
        for (int i = 0; i < options.length; i++) {
            if (held[i] == shown[i] || custom && (Config.graphicsPart(described[i].id) >= 0 || qualityPlayer(described[i].id) == 0)) {
                continue;
            }
            try {
                applyOption(options[i], described[i], held[i]);
                shown[i] = held[i];
            } catch (RuntimeException failure) {
                Log.error(described[i].id + " as applied on the game's options screen was not taken", failure);
            }
        }
        if (!custom) {
            return;
        }
        try {
            double[] values = Config.values(Quality.customFrom());
            int[] rows = new int[Config.GRAPHICS.length];
            int found = 0;
            for (int i = 0; i < options.length; i++) {
                int part = Config.graphicsPart(described[i].id);
                if (part >= 0) {
                    if (held[i] != shown[i]) {
                        values[part] = held[i];
                    }
                    rows[part] = options[i];
                    found++;
                }
            }
            if (found != Config.GRAPHICS.length) {
                throw new IllegalStateException("the options screen has " + found + " of the " + Config.GRAPHICS.length + " rows of [Graphics]");
            }
            Quality.Picture picture = Config.picture(values);
            Quality.custom(picture);
            Quality.set(0, Quality.CUSTOM);
            sayGraphics(1);
            for (int part = 0; part < rows.length; part++) {
                int option = rows[part];
                double value = values[part];
                if (config.optionValue(option) != value) {
                    save(() -> config.saveOption(option, value));
                }
            }
            save(() -> {
                customInFile = picture;
                config.saveQuality(0, Quality.CUSTOM);
            });
            for (int i = 0; i < options.length; i++) {
                if (Config.graphicsPart(described[i].id) >= 0) {
                    shown[i] = held[i];
                }
            }
        } catch (RuntimeException failure) {
            Log.error("the rows of [Graphics] as applied on the game's options screen were not taken", failure);
        }
    }

    /** Has the next frame drawn say the graphics mode on the screens of the players of the bits. */
    private static void sayGraphics(int players) {
        graphicsToSay.getAndUpdate(views -> views | players);
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
            int pressed = 0, told = graphicsModeOwed;
            boolean enteringSave = outOfSave;
            graphicsModeOwed = 0;
            for (int i = 0; i < View.COUNT; i++) {
                if ((told & 1 << i) != 0) {
                    cycleGraphicsMode(i);
                }
            }
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
                        pressed &= ~Config.SPLIT_SCREEN;
                        Log.info("the split screen key turns the split of two players and does nothing with " + IsoPlayer.numPlayers + " at the screen");
                    }
                }
            }
            int lowered = Quality.players(IsoPlayer.numPlayers);
            for (int i = 0; i < View.COUNT; i++) {
                if ((lowered & 1 << i) != 0) {
                    int player = i, firstQuality = Quality.row(i), overhead = Quality.overheadRow(i);
                    save(() -> {
                        config.saveQuality(player, firstQuality);
                        config.saveIsometricQuality(player, overhead);
                    });
                }
            }
            sayGraphics(lowered);
            if (enteringSave) {
                graphicsToSay.set(0);
                Quality.stepSaid();
            } else {
                told |= graphicsToTell(OptionsPage.menuShown());
            }
            SplitScreen.frame();
            int said = pressed & (Config.AIM_MODE | Config.SPLIT_SCREEN);
            for (int i = 0; i < View.COUNT; i++) {
                owed[i] = said | ((told & 1 << i) != 0 ? Config.CYCLE_QUALITY : 0);
                if (theirs(pressed, i, Config.FIRST_PERSON_TAPS, Config.FIRST_PERSON_ALL, "first person")) {
                    owed[i] |= Config.FIRST_PERSON;
                }
                if (theirs(pressed, i, Config.CYCLE_QUALITY_TAPS, Config.CYCLE_QUALITY_ALL, "graphics")) {
                    // Changed on the next frame: this one's picture is the view's picture before (ModeFade).
                    graphicsModeOwed |= 1 << i;
                }
                if (theirs(pressed, i, Config.CYCLE_STYLE_TAPS, Config.CYCLE_STYLE_ALL, "style")) {
                    cycleStyle(i);
                    owed[i] |= Config.CYCLE_STYLE;
                }
            }
            backButtons();
        }
        if (playerIndex >= View.COUNT) {
            return 0;
        }
        int mine = owed[playerIndex];
        owed[playerIndex] = 0;
        return mine;
    }

    /**
     * Whether a key of the player keys was pressed for a player: tapped once for the first player,
     * twice for the second and so on, or held for every player at the screen. A key tapped for a
     * player who is not at the screen does nothing.
     */
    private static boolean theirs(int pressed, int player, int[] taps, int held, String key) {
        boolean tapped = (pressed & taps[player]) != 0, here = player == 0 || IsoPlayer.numPlayers > player && IsoPlayer.players[player] != null;
        if (tapped && !here) {
            Log.info("the " + key + " key was tapped " + (player + 1) + " times, which is player " + (player + 1) + "'s, and there is none at the screen");
        }
        return here && (tapped || (pressed & held) != 0);
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
            boolean down = player != null && View.onController(player) && JoypadButton.Back.isDown(player.getJoypadBind());
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
        int owedNow = graphicsModeOwed;
        graphicsModeOwed = 0;
        for (int i = 0; i < View.COUNT; i++) {
            if ((owedNow & 1 << i) != 0) {
                cycleGraphicsMode(i);
            }
        }
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
        if (any && !onFoot) {
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
     * view's, set for both their views and kept for the next start. The isometric view has no
     * Automatic and takes Low for it. The options screen sets the two apart.
     */
    static void cycleGraphicsMode(int player) {
        int next = Quality.firstPersonInEffect(player) ? Quality.next(player) : Quality.nextOverhead(player);
        int overhead = next == Quality.AUTOMATIC ? Quality.LOW : next;
        Quality.set(player, next);
        Quality.setOverhead(player, overhead);
        save(() -> {
            config.saveQuality(player, next);
            config.saveIsometricQuality(player, overhead);
        });
    }

    /** A player's picture style after the one they are in, kept for the next start. */
    static void cycleStyle(int player) {
        Style next = Style.of(player).next();
        Style.set(player, next.ordinal());
        save(() -> config.saveStyle(player, next));
    }

    /** The other way for two players to share the screen, kept for the next start. */
    static void turnSplitScreen() {
        int next = SplitScreen.row() == SplitScreen.VERTICAL ? SplitScreen.HORIZONTAL : SplitScreen.VERTICAL;
        SplitScreen.set(next);
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
