package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleConsumer;

/**
 * CameraUnlock.ini: its rows, as the mod describes them to cameraunlock-core, which reads and
 * writes the file. Nothing in the mod opens it. The mod describes the rows when it starts, and
 * its build describes the same rows to render config/CameraUnlock.ini (RenderConfig).
 */
final class Config {
    static final String FILE_NAME = "CameraUnlock.ini";
    /** The game's name as cameraunlock-core's data/games.json spells it. */
    private static final String DISPLAY_NAME = "Project Zomboid";

    /**
     * How far off a wall the eye is held, in tiles, as the file holds it. It has to exceed
     * WorldScene.NEAR_CORNER_DISTANCE, the distance to the near plane's corners.
     */
    static final String COLLISION_MARGIN = "0.12";
    static final float MIN_FIELD_OF_VIEW = 40f, MAX_FIELD_OF_VIEW = 110f;

    /**
     * The bits of pressed keys: core's three, then the mod's own. The first person key is the
     * first player's tapped, a second player's tapped twice and every player's held, and a view
     * is handed FIRST_PERSON for whichever of those is its own.
     */
    static final int CYCLE_TRACKING_MODE = CameraUnlock.HOTKEY_CYCLE_TRACKING_MODE,
            YAW_MODE = CameraUnlock.HOTKEY_YAW_MODE, AIM_MODE = CameraUnlock.HOTKEY_AIM_MODE,
            FIRST_PERSON = CameraUnlock.HOTKEY_LOCAL, CYCLE_QUALITY = CameraUnlock.HOTKEY_LOCAL << 1,
            CYCLE_STYLE = CameraUnlock.HOTKEY_LOCAL << 2, FIRST_PERSON_SECOND = CameraUnlock.HOTKEY_LOCAL << 3,
            SPLIT_SCREEN = CameraUnlock.HOTKEY_LOCAL << 4, FIRST_PERSON_ALL = CameraUnlock.HOTKEY_LOCAL << 5;

    /** Player 2's port; each player after them has the next. */
    private static final int SECOND_PLAYER_PORT = 4243;
    private static final String[] PLAYER_PORT_ROWS = {"SecondPlayerUdpPort", "ThirdPlayerUdpPort", "FourthPlayerUdpPort"};

    private static final String[] LOAD_STATUS = {"read", "imported", "created", "deferred", "legacy refused", "unreadable"};
    private static final String[] SAVE_STATUS = {"saved", "not saved", "may not be saved"};

    /**
     * What the rows read again while the game runs hold: the field of view in degrees, Own's bits,
     * the Quality row, the IsometricQuality row as a Quality mode, the Style row, the HillHeight row, the MuzzleFlash row, the SplitScreen row and the rows of [Graphics].
     */
    record Live(float fieldOfView, int own, int quality, int overheadQuality, int style, float hillHeight, boolean muzzleFlash, int splitScreen,
            Quality.Picture custom) {}

    /** The keys of [Graphics], the Custom graphics mode, in the order of Quality.Picture's parts. */
    static final String[] GRAPHICS = {"TreeQuality", "RenderScale", "SharpShadows", "ViewDistance", "ZombiesAndPropsDrawDistance",
        "FarTreeDistance", "RainOnGlass", "ShadingAndLight", "Reflections", "FullSizeTextures", "SurfaceDepth", "WholeFigureShadows",
        "FrameBlending"};
    private static final int RENDER_SCALE = 1;
    static final float MIN_RENDER_SCALE = 0.5f;
    static final int MIN_VIEW_DISTANCE = 32, MIN_DRAW_DISTANCE = 16;

    private final CameraUnlock core;
    private final int fieldOfView, quality, overheadQuality, style, hillHeight, muzzleFlash, splitScreen;
    /** The rows of the ports of players 2, 3 and 4. */
    private final int[] playerPorts = new int[PLAYER_PORT_ROWS.length];
    /** The rows of [Content], in the order of Own's bits. */
    private final int[] own;
    /** The rows of [Graphics], in the order of GRAPHICS. */
    private final int[] graphics;
    /** For each of the mod's own rows the game's options screen shows, by its key: what puts a value applied there on the running game. */
    private final Map<String, DoubleConsumer> puts = new HashMap<>();

    /**
     * Describes the rows. The tracking mode pair, WorldSpaceYaw and the three aim mode rows are
     * writable: their hotkeys save. There is no key that switches head tracking off: the
     * owner ruled the action out for this game, and the three keys there are are the game's own
     * rows on the nav keys alone (see cameraunlock-core's per_game). FieldOfView, Quality, IsometricQuality, Style,
     * HillHeight, MuzzleFlash, SplitScreen and the rows of [Content] are the mod's own, and the ones read
     * again while the game runs, and so are the rows of [Graphics], which are what the Custom
     * graphics mode draws. They are writable too, and a writable row that is not a key
     * list is a setting on the game's options screen (OptionsPage). SecondPlayerUdpPort, ThirdPlayerUdpPort, FourthPlayerUdpPort, FirstPersonKey, CycleQualityKey,
     * CycleStyleKey and SplitScreenKey are the mod's own too, and read at the start.
     */
    Config(CameraUnlock core) {
        this.core = core;
        int writable = CameraUnlock.ROW_WRITABLE, live = CameraUnlock.ROW_LIVE;
        core.configDescribe(DISPLAY_NAME);
        core.configConcept("UdpPort", 0, null, null);
        for (int i = 0; i < playerPorts.length; i++) {
            playerPorts[i] = core.configLocalInt("Network", PLAYER_PORT_ROWS[i],
                    "UDP port for player " + (i + 2) + "'s tracker (OpenTrack protocol). Each player needs their own.",
                    0, SECOND_PLAYER_PORT + i, 1, 65535);
        }
        core.configConcept("EnableOnStartup", 0, "true: head tracking is on when the game starts. false: it is off. No key turns it on or off.", null);
        core.configConcept("WorldSpaceYaw", writable, null, null);
        core.configConcept("RotationEnabled", writable, null, null);
        core.configConcept("DataFreshnessMs", 0, null, null);
        core.configConcept("LocalSmoothing", 0, null, null);
        core.configConcept("RemoteSmoothing", 0, null, null);
        core.configConcept("PositionEnabled", writable, null, null);
        core.configConcept("TrueFreeLook", writable,
                "false: aiming down the sights keeps your eye on the sights when you lean. true: free look, the weapon stays put and your\n"
                        + "head moves around it.", null);
        core.configConcept("FreeLookMarker", writable,
                "Changes nothing in Project Zomboid: the game's own reticle is the free look marker. Written with TrueFreeLook.", null);
        core.configConcept("StockSights", writable, null, null);
        core.configConcept("PositionLimitX", 0, null, null);
        core.configConcept("PositionLimitY", 0, null, null);
        core.configConcept("PositionLimitYDown", 0, null, null);
        core.configConcept("PositionLimitZ", 0, null, null);
        core.configConcept("PositionLimitZBack", 0, null, null);
        core.configConcept("CollisionEnabled", 0, null, null);
        core.configConcept("CollisionMargin", 0, "How far the view is held off a wall when you lean into it, in map tiles.",
                COLLISION_MARGIN);
        core.configConcept("CollisionReleaseSmoothing", 0, null, null);
        int perGame = CameraUnlock.ROW_PER_GAME;
        core.configConcept("CycleTrackingModeKey", perGame, null, "PageUp");
        core.configConcept("YawModeKey", perGame, null, "PageDown");
        core.configConcept("TrueFreeLookKey", perGame,
                "Cycles the aim mode: sights locked, free look with a marker, stock sights.", "Insert");
        core.configConcept("LightMultiplier", 0, null, null);

        fieldOfView = core.configLocalFloat("General", "FieldOfView",
                "Vertical field of view of the first person view, in degrees: 40 to 110.",
                writable | live, FieldOfView.DEFAULT_DEGREES, MIN_FIELD_OF_VIEW, MAX_FIELD_OF_VIEW);
        quality = core.configLocalEnum("General", "Quality",
                "First person graphics mode: Automatic, Low, Medium, High or Custom. Automatic draws less when the frame rate drops\n"
                        + "and more again when it recovers. Custom uses the rows of [Graphics].",
                writable | live, Quality.words(), Quality.AUTOMATIC);
        overheadQuality = core.configLocalEnum("General", "IsometricQuality",
                "Graphics mode of the game's isometric view: Low (the game's own picture), Medium, High or Custom.",
                writable | live, Quality.overheadWords(), 0);
        style = core.configLocalEnum("General", "Style",
                "Picture style: Vivid (strong contrast and colour), Natural (colours as lit, less contrast) or Bleak (washed out, near\n"
                        + "the game's own look).",
                writable | live, Style.words(), Style.BLEAK.ordinal());
        // Not a row of [Graphics]: it changes what the land looks like and not what the picture costs, in every graphics mode alike.
        hillHeight = core.configLocalFloat("General", "HillHeight",
                "How high the first person hills stand: 0 is as flat as the game's map, 1 as the mod makes them, up to " + HillHeight.MOST + ".\n"
                        + "Held at 1 where players can fight.",
                writable | live, HillHeight.DEFAULT, 0f, HillHeight.MOST);
        muzzleFlash = core.configLocalBool("General", "MuzzleFlash",
                "true: a shot from a firearm flashes at the muzzle and briefly lights what is near. Never in Low.",
                writable | live, true);
        splitScreen = core.configLocalEnum("General", "SplitScreen",
                "How two players at one screen share it: Vertical (side by side) or Horizontal (one above the other).",
                writable | live, SplitScreen.words(), SplitScreen.VERTICAL);
        Quality.Picture high = Quality.HIGH_PICTURE;
        graphics = new int[] {
            core.configLocalEnum("Graphics", "TreeQuality",
                    "Low: the game's plant sprites. Medium: the mod's trees, the game's sprites past " + (int) Crowns.REACH + " tiles.\n"
                            + "High: the mod's trees as far as you see.",
                    writable | live, Quality.TREE_QUALITIES, high.treeQuality()),
            core.configLocalFloat("Graphics", "RenderScale",
                    "Share of the window's resolution the first person picture is drawn at, each way: 0.5 to 1.",
                    writable | live, high.renderScale(), MIN_RENDER_SCALE, 1f),
            core.configLocalBool("Graphics", "SharpShadows",
                    "true: shadow maps 4096 texels across. false: 2048, a quarter of the graphics memory.",
                    writable | live, high.sharpShadows()),
            core.configLocalInt("Graphics", "ViewDistance",
                    "How far the first person view is drawn, in tiles: 32 to 80, within the map the game has loaded.",
                    writable | live, high.viewDistance(), MIN_VIEW_DISTANCE, high.viewDistance()),
            core.configLocalInt("Graphics", "ZombiesAndPropsDrawDistance",
                    "How far zombies, people, vehicles and the mod's furniture are drawn, in tiles: " + MIN_DRAW_DISTANCE + " to " + high.viewDistance()
                            + ", never past\nViewDistance. Where players can fight, everything is drawn.",
                    writable | live, high.drawDistance(), MIN_DRAW_DISTANCE, high.viewDistance()),
            core.configLocalInt("Graphics", "FarTreeDistance",
                    "How far past the loaded map trees are drawn, in tiles: 0 (none) to " + Quality.MOST_FAR_TREES + ".",
                    writable | live, high.farTreeDistance(), 0, Quality.MOST_FAR_TREES),
            core.configLocalEnum("Graphics", "RainOnGlass",
                    "Rain on your vehicle's windows: Low (one size of drop), Medium (adds specks and trickles) or High (three sizes and\n"
                            + "running drops).",
                    writable | live, "Low,Medium,High", high.rainOnGlass()),
            core.configLocalBool("Graphics", "ShadingAndLight",
                    "true: shaded corners, light from what stands near and lit air, and with Reflections, mirroring wet roads, floors,\n"
                            + "glass and water.",
                    writable | live, high.shadingAndLight()),
            core.configLocalBool("Graphics", "Reflections",
                    "true: your vehicle's mirrors show the road behind, and with ShadingAndLight, wet roads, floors, glass and water\n"
                            + "mirror the world.",
                    writable | live, high.reflections()),
            core.configLocalBool("Graphics", "FullSizeTextures",
                    "true: the mod's surface, wall and skin pictures at full size. false: half size, a quarter of the memory.",
                    writable | live, high.fullSizeTextures()),
            core.configLocalBool("Graphics", "SurfaceDepth",
                    "true: near surfaces are given the depth of what they show. false: they are drawn flat.",
                    writable | live, high.surfaceDepth()),
            core.configLocalBool("Graphics", "WholeFigureShadows",
                    "true: what people and zombies wear and carry casts a shadow at any distance. false: past " + (int) CharacterDraw.SHADOW_WHOLE_WITHIN
                            + " tiles\nonly their bodies do, which costs less in a crowd.",
                    writable | live, high.wholeFigureShadows()),
            core.configLocalBool("Graphics", "FrameBlending",
                    "true: each frame is blended with the one before, for smoother edges and leaves and a slightly softer picture.",
                    writable | live, high.frameBlending()),
        };

        core.configLocalHotkeyTaps("FirstPersonKey",
                "Switches between first person and the game's own view. Tap for player 1, tap twice for player 2, hold for every player.",
                0, "Delete", FIRST_PERSON, FIRST_PERSON_SECOND, FIRST_PERSON_ALL);
        core.configLocalHotkey("CycleQualityKey",
                "Goes to the next graphics mode and sets both views to it: Automatic, Low, Medium, High, Custom (Quality and\n"
                        + "IsometricQuality). The isometric view has no Automatic: it takes Low for it, and the key skips Automatic\n"
                        + "there. The game's options set the two views apart.", 0, "F9",
                CYCLE_QUALITY);
        core.configLocalHotkey("CycleStyleKey", "Goes to the next picture style: Vivid, Natural, Bleak (Style).", 0, "F8",
                CYCLE_STYLE);
        core.configLocalHotkey("SplitScreenKey",
                "With two players at the screen, switches between side by side and one above the other.", 0, "F7",
                SPLIT_SCREEN);
        own = new int[] {
            core.configLocalBool("Content", "OwnWeapons",
                    "true: the mod's models of the pump shotgun and its sawn-off, the hunting rifle, the double barrelled shotgun and its\n"
                            + "sawn-off, the pistols, the revolvers, the toy cap pistol and rifle scopes. false: the game's own, with no picture\n"
                            + "through the scope.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnFurniture",
                    "true: the mod's models of furniture, appliances, street lamps, clocks, picture frames, banners and flags. false: the\n"
                            + "game's sprites.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnPictures",
                    "true: the mod's photographs, paintings, posters, signs and television programmes. false: the game's pictures, and\n"
                            + "only colour bars on a television.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnPlants",
                    "true: the mod's trees, bushes, boulders, grass, ferns, flowers, crops, fallen leaves and fallen sticks. false: the game's\n"
                            + "sprites.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnTextures",
                    "true: the mod's textures of ground, wood, roofing, paint, carpet, cloth and wallpaper, and clean road lines. false:\n"
                            + "the colours of the game's sprites.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnBuildings",
                    "true: windows, doors, curtains, handrails, open stairs and wall finishes built by the mod. false: the game's sprites.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnZombies",
                    "true: the mod's skins, with lifelike faces, for zombies near you. false: the game's own skins.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnPeople",
                    "true: the mod's skins, with lifelike faces, for your character and other living people. false: the game's own skins.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnArms",
                    "true: the mod's arms and hands on a held weapon, working its bolt, slide and magazine. false: the game's own, for a\n"
                            + "mod that changes the arms or the weapons.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnClothes",
                    "true: clothes and hair near you are sharpened, with the weave, knit, grain or strands of what they are made of.\n"
                            + "false: the game's own pictures.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnAnimals",
                    "true: the mod's skins for animals near you, with the hair of their coats. false: the game's own skins.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnFire",
                    "true: flames, smoke and firelight for burning buildings, Molotovs, campfires, fireplaces, wood stoves, burn barrels\n"
                            + "and cooking pits. false: the game's own flat flames and smoke, stood up where they burn.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnCabins",
                    "true: the mod's models of the inside of the vehicle you sit in, its dashboard, instruments, wheel, seats and doors.\n"
                            + "false: the game's own, with its glass cut away.",
                    writable | live, true),
        };

        puts.put("FieldOfView", value -> {
            FieldOfView.set((float) value);
            Log.info("first person field of view set to " + (float) value + " degrees vertical");
        });
        puts.put("Quality", value -> Quality.set((int) value));
        puts.put("IsometricQuality", value -> Quality.setOverhead((int) value + Quality.LOW));
        puts.put("Style", value -> Style.set((int) value));
        puts.put("HillHeight", value -> {
            HillHeight.set((float) value);
            Log.info("hill height set to " + (float) value);
        });
        puts.put("MuzzleFlash", value -> MuzzleFlash.set(value != 0));
        puts.put("SplitScreen", value -> SplitScreen.set((int) value));
        String[] ownKeys = {"OwnWeapons", "OwnFurniture", "OwnPictures", "OwnPlants", "OwnTextures", "OwnBuildings", "OwnZombies", "OwnPeople",
            "OwnArms", "OwnClothes", "OwnAnimals", "OwnFire", "OwnCabins"};
        for (int i = 0; i < ownKeys.length; i++) {
            int bit = 1 << i;
            puts.put(ownKeys[i], value -> {
                Own.set(value != 0 ? Own.rows() | bit : Own.rows() & ~bit);
                Log.info("the mod's own in first person: " + Own.described(Own.rows()));
            });
        }
    }

    /** Whether put has somewhere to put a row's value. The rows of [Graphics] are put by Tracking.applyScreen. */
    boolean takes(String key) {
        return puts.containsKey(key) || graphicsPart(key) >= 0;
    }

    /** A key's place in GRAPHICS, or -1 for a key that is not one of the rows of [Graphics]. */
    static int graphicsPart(String key) {
        for (int part = 0; part < GRAPHICS.length; part++) {
            if (GRAPHICS[part].equals(key)) {
                return part;
            }
        }
        return -1;
    }

    /** A picture as the options of the rows of [Graphics] hold it, in the order of GRAPHICS. */
    static double[] values(Quality.Picture picture) {
        return new double[] {picture.treeQuality(), picture.renderScale(), bit(picture.sharpShadows()), picture.viewDistance(),
            picture.drawDistance(), picture.farTreeDistance(), picture.rainOnGlass(), bit(picture.shadingAndLight()), bit(picture.reflections()),
            bit(picture.fullSizeTextures()), bit(picture.surfaceDepth()), bit(picture.wholeFigureShadows()), bit(picture.frameBlending())};
    }

    /** The picture the options of the rows of [Graphics] hold, in the order of GRAPHICS: Custom always draws what the mod made. */
    static Quality.Picture picture(double[] values) {
        return new Quality.Picture(false, (int) Math.rint(values[0]), (float) values[1], values[2] != 0, (int) Math.rint(values[3]),
                (int) Math.rint(values[4]), (int) Math.rint(values[5]), (int) Math.rint(values[6]), values[7] != 0, values[8] != 0,
                values[9] != 0, values[10] != 0, values[11] != 0, values[12] != 0);
    }

    private static double bit(boolean on) {
        return on ? 1 : 0;
    }

    /** Puts a value of one of the mod's own rows, as an option's number, on the running game. */
    void put(String key, double value) {
        puts.get(key).accept(value);
    }

    /** Writes the file a first start creates, which the build keeps as config/CameraUnlock.ini. */
    void render(Path file) {
        core.configRender(file);
    }

    /**
     * Reads the file, creating it where there is none.
     *
     * @param defaults null for the player's own Defaults.ini, a scratch file in a test
     */
    CameraUnlock.Config load(Path file, Path defaults) {
        CameraUnlock.Config loaded = core.configLoad(file, defaults);
        Log.info(file + ": " + LOAD_STATUS[loaded.status]);
        return loaded;
    }

    /** What the rows read again while the game runs hold now. */
    Live live() {
        int rows = 0;
        for (int i = 0; i < own.length; i++) {
            rows |= core.configGetInt(own[i]) << i;
        }
        double[] custom = new double[graphics.length];
        for (int part = 0; part < graphics.length; part++) {
            custom[part] = part == RENDER_SCALE ? core.configGetFloat(graphics[part]) : core.configGetInt(graphics[part]);
        }
        return new Live(core.configGetFloat(fieldOfView), rows, core.configGetInt(quality), core.configGetInt(overheadQuality) + Quality.LOW,
                core.configGetInt(style),
                core.configGetFloat(hillHeight), core.configGetInt(muzzleFlash) != 0, core.configGetInt(splitScreen), picture(custom));
    }

    /**
     * When CameraUnlock.ini or Defaults.ini has changed since core last read or wrote it, reads
     * both again and answers what they give the rows read while the game runs. Null when nothing
     * changed or the file cannot be read, which core's own line in the log says.
     */
    Live reload() {
        return core.configReload() == CameraUnlock.RELOAD_APPLIED ? live() : null;
    }

    /** The UDP port the tracker of the player at a number, 1 to 3, is listened for on. */
    int playerPort(int playerIndex) {
        return core.configGetInt(playerPorts[playerIndex - 1]);
    }

    /** The row that names that port. */
    static String playerPortRow(int playerIndex) {
        return PLAYER_PORT_ROWS[playerIndex - 1];
    }

    /** The settings the game's options screen shows, in core's order, which is the file's. */
    CameraUnlock.Option[] options() {
        CameraUnlock.Option[] options = new CameraUnlock.Option[core.configOptionCount()];
        for (int option = 0; option < options.length; option++) {
            options[option] = core.configOption(option);
        }
        return options;
    }

    /** An option's key. */
    String optionId(int option) {
        return core.configOption(option).id;
    }

    /** What core holds for an option now, as one number: the last value the file gave it or a save put there. */
    double optionValue(int option) {
        return core.configOptionGet(option);
    }

    boolean saveOption(int option, double value) {
        return saved(core.configOption(option).id + " " + value + " from the game's options screen", core.configOptionSave(option, value));
    }

    /** Each save answers whether the file holds the new value. The session keeps it either way. */
    boolean saveTrackingMode() {
        return saved("tracking mode", core.configSaveTrackingMode());
    }

    boolean saveAimMode() {
        return saved("aim mode", core.configSaveAimMode());
    }

    boolean saveWorldSpaceYaw(boolean world) {
        return saved("yaw mode", core.configSaveWorldSpaceYaw(world));
    }

    boolean saveQuality(int row) {
        return saved("first person graphics mode", core.configSaveInt(quality, row));
    }

    /** @param row a Quality mode, LOW or after */
    boolean saveIsometricQuality(int row) {
        return saved("isometric graphics mode", core.configSaveInt(overheadQuality, row - Quality.LOW));
    }

    boolean saveStyle(Style row) {
        return saved("picture style", core.configSaveInt(style, row.ordinal()));
    }

    boolean saveSplitScreen(int row) {
        return saved("split screen", core.configSaveInt(splitScreen, row));
    }

    private static boolean saved(String what, int status) {
        Log.info(FILE_NAME + ": " + what + " " + SAVE_STATUS[status]);
        return status == CameraUnlock.SAVE_SAVED;
    }
}
