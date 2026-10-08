package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.nio.file.Path;

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
     * The bits of pressed keys: core's four, then the mod's own. The first person key is the
     * first player's tapped and a second player's held, and a view is handed FIRST_PERSON for
     * either.
     */
    static final int TOGGLE = CameraUnlock.HOTKEY_TOGGLE, CYCLE_TRACKING_MODE = CameraUnlock.HOTKEY_CYCLE_TRACKING_MODE,
            YAW_MODE = CameraUnlock.HOTKEY_YAW_MODE, AIM_MODE = CameraUnlock.HOTKEY_AIM_MODE,
            FIRST_PERSON = CameraUnlock.HOTKEY_LOCAL, CYCLE_QUALITY = CameraUnlock.HOTKEY_LOCAL << 1,
            CYCLE_STYLE = CameraUnlock.HOTKEY_LOCAL << 2, FIRST_PERSON_SECOND = CameraUnlock.HOTKEY_LOCAL << 3,
            SPLIT_SCREEN = CameraUnlock.HOTKEY_LOCAL << 4;

    private static final int SECOND_PLAYER_PORT = 4243;

    private static final String[] LOAD_STATUS = {"read", "imported", "created", "deferred", "legacy refused", "unreadable"};
    private static final String[] SAVE_STATUS = {"saved", "not saved", "may not be saved"};

    /** What the rows read again while the game runs hold: the field of view in degrees, Own's bits, the Quality row, the Style row, the FrameBlending row and the SplitScreen row. */
    record Live(float fieldOfView, int own, int quality, int style, boolean frameBlending, int splitScreen) {}

    private final CameraUnlock core;
    private final int fieldOfView, quality, style, frameBlending, splitScreen, secondPlayerPort;
    /** The rows of [Content], in the order of Own's bits. */
    private final int[] own;

    /**
     * Describes the rows. The tracking mode pair, WorldSpaceYaw and the three aim mode rows are
     * writable: their hotkeys save. End changes the session only. FieldOfView, Quality, Style,
     * FrameBlending, SplitScreen and the rows of [Content] are the mod's own, and the ones read
     * again while the game runs. SecondPlayerUdpPort, FirstPersonKey, CycleQualityKey,
     * CycleStyleKey and SplitScreenKey are the mod's own too, and read at the start.
     */
    Config(CameraUnlock core) {
        this.core = core;
        int writable = CameraUnlock.ROW_WRITABLE, live = CameraUnlock.ROW_LIVE;
        core.configDescribe(DISPLAY_NAME);
        core.configConcept("UdpPort", 0, null, null);
        secondPlayerPort = core.configLocalInt("Network", "SecondPlayerUdpPort",
                "UDP port the mod receives a second player's tracker data on, for two players at\n"
                        + "one screen (OpenTrack protocol). The first player's tracker is on UdpPort, and the\n"
                        + "two have to differ.",
                0, SECOND_PLAYER_PORT, 1, 65535);
        core.configConcept("EnableOnStartup", 0, null, null);
        core.configConcept("WorldSpaceYaw", writable, null, null);
        core.configConcept("RotationEnabled", writable, null, null);
        core.configConcept("DataFreshnessMs", 0, null, null);
        core.configConcept("LocalSmoothing", 0, null, null);
        core.configConcept("RemoteSmoothing", 0, null, null);
        core.configConcept("PositionEnabled", writable, null, null);
        core.configConcept("TrueFreeLook", writable,
                "false: while you aim down the sights, leaning keeps your eye on the sights.\n"
                        + "true: the weapon stays put and your head moves freely around it (free look), and\n"
                        + "the game's reticle shows where your shot will land.", null);
        core.configConcept("FreeLookMarker", writable,
                "The aim marker of free look. In Project Zomboid the game's own reticle is that\n"
                        + "marker and is drawn whenever you aim, so this row changes nothing: the mod\n"
                        + "writes it with TrueFreeLook.", null);
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
        core.configConcept("ToggleKey", 0, null, null);
        core.configConcept("CycleTrackingModeKey", 0, null, null);
        core.configConcept("YawModeKey", 0, null, null);
        core.configConcept("TrueFreeLookKey", 0,
                "Cycles the aim mode: sights locked, free look with a marker, stock sights\n"
                        + "(TrueFreeLook, FreeLookMarker, StockSights).", null);
        core.configConcept("LightMultiplier", 0, null, null);

        fieldOfView = core.configLocalFloat("General", "FieldOfView",
                "Field of view of the first person view in degrees, from the bottom of the picture to\n"
                        + "the top: 40 to 110. A wider screen shows more to the sides. Project Zomboid has no\n"
                        + "field of view setting of its own. Aiming a firearm still narrows the view by the\n"
                        + "same ratio. Read again while the game runs, so a change shows as soon as the file\n"
                        + "is saved.",
                live, FieldOfView.DEFAULT_DEGREES, MIN_FIELD_OF_VIEW, MAX_FIELD_OF_VIEW);
        quality = core.configLocalEnum("General", "Quality",
                "The graphics mode: how much of the mod's own picture is drawn. CycleQualityKey\n"
                        + "goes from one to the next, Low, Medium, High, and saves it here. Low is as near\n"
                        + "the game's own look as there is: the game's own view is the game's own picture,\n"
                        + "and first person is drawn with the game's own sprites, models and textures in\n"
                        + "place of everything the mod made, at a little over three fifths of the pixels\n"
                        + "each way, sight to 48 tiles, smaller shadow maps, and no shading of corners, lit\n"
                        + "air, reflections or depth in surfaces. Medium and High draw both views with what\n"
                        + "the mod made. High: the whole picture. Medium: the game's own grass, trees,\n"
                        + "bushes and rocks whatever OwnPlants says, no shading of corners, lit air,\n"
                        + "reflections or depth in surfaces either, four fifths of the pixels each way,\n"
                        + "sight to 64 tiles and smaller shadow maps. Automatic, which only this file can\n"
                        + "set: begins at High and draws less, as Medium and then Low do but still with\n"
                        + "everything the mod made, each time the picture is drawn fewer than 45 times a\n"
                        + "second for three seconds, until the game is closed. Read again while the game\n"
                        + "runs.",
                writable | live, Quality.words(), Quality.LOW);
        style = core.configLocalEnum("General", "Style",
                "How the mod's picture is developed, in every graphics mode. CycleStyleKey goes\n"
                        + "from one to the next and saves it here. Vivid: strong contrast and full colours,\n"
                        + "cool in the shade and warm in the sun, with dark corners, glare off the sun and a\n"
                        + "glow round bright lights. Natural: the colours as they are lit, with no tint,\n"
                        + "less contrast, and less of the dark corners, the glare and the glow. Bleak:\n"
                        + "washed out colours and low contrast under a faint drab cast, with no glare off\n"
                        + "the sun, nearer the colours of the game's own view. The light, the weather and\n"
                        + "the hour are the same in all three. Read again while the game runs.",
                writable | live, Style.words(), Style.VIVID.ordinal());
        frameBlending = core.configLocalBool("General", "FrameBlending",
                "Whether each frame of the first person picture is mixed with the one before it.\n"
                        + "false: every frame is drawn on its own, so the picture is as sharp while you turn\n"
                        + "or walk as it is standing still. true: fine edges, leaves and the grain of soft\n"
                        + "shadows are steadier in a still view, and what moves across the picture is\n"
                        + "softened and can smear. Read again while the game runs.",
                live, false);
        splitScreen = core.configLocalEnum("General", "SplitScreen",
                "How two players at one screen share it. SplitScreenKey goes from one to the other\n"
                        + "and saves it here. Vertical: side by side, each half the width and the whole\n"
                        + "height, as the game has it. Horizontal: player 1 above player 2, each the whole\n"
                        + "width and half the height. One player has the whole screen and three or four have\n"
                        + "a quarter each, as the game has it, whichever this says. Read again while the game\n"
                        + "runs.",
                writable | live, SplitScreen.words(), SplitScreen.VERTICAL);
        core.configLocalHotkeyHeld("FirstPersonKey",
                "Goes into the first person view and back to the game's own. Tapped, for the first\n"
                        + "player. Held for " + CameraUnlock.HOLD_MS + " ms, for a second player at the same screen.",
                0, "Delete", FIRST_PERSON, FIRST_PERSON_SECOND);
        core.configLocalHotkey("CycleQualityKey", "Goes to the next graphics mode: Low, Medium, High (Quality).", 0, "F9",
                CYCLE_QUALITY);
        core.configLocalHotkey("CycleStyleKey", "Goes to the next picture style: Vivid, Natural, Bleak (Style).", 0, "F8",
                CYCLE_STYLE);
        core.configLocalHotkey("SplitScreenKey",
                "With two players at the screen, goes between side by side and one above the other\n"
                        + "(SplitScreen). Does nothing with any other number of players.", 0, "F7",
                SPLIT_SCREEN);
        own = new int[] {
            core.configLocalBool("Content", "OwnWeapons",
                    "What the mod made itself and shows in place of the game's own. Set a row to false\n"
                            + "and the game's own is drawn there instead, as the game or another mod has it.\n"
                            + "These rows are read again while the game runs, so a change shows as soon as the\n"
                            + "file is saved. In Medium and High they apply to the game's own view too, which\n"
                            + "the mod draws there.\n"
                            + "true: the mod's models of the pump shotgun, the hunting rifle and rifle scopes.\n"
                            + "false: the game's own weapon and scope models, and no picture through the scope.",
                    live, true),
            core.configLocalBool("Content", "OwnFurniture",
                    "true: the mod's models of furniture, appliances, street lamps, clocks and picture\n"
                            + "frames. false: the game's sprites of them.",
                    live, true),
            core.configLocalBool("Content", "OwnPictures",
                    "true: the mod's photographs, paintings and posters on walls, and programmes on a\n"
                            + "television that is on. false: the game's pictures, and only colour bars on a\n"
                            + "television.",
                    live, true),
            core.configLocalBool("Content", "OwnPlants",
                    "true: the mod's leaves, tree trunks, boulders, grass, ferns, flowers and fallen\n"
                            + "leaves. false: the game's sprites of trees, bushes and grass.",
                    live, true),
            core.configLocalBool("Content", "OwnTextures",
                    "true: the mod's textures of tarmac, gravel, earth, lawn, wood, boards, roofing,\n"
                            + "paint, carpet, cloth and wallpaper. false: the colours of the game's sprites alone.",
                    live, true),
            core.configLocalBool("Content", "OwnBuildings",
                    "true: windows, doors and curtains built by the mod, and the outside of a wall\n"
                            + "finished as its building is. false: the game's sprites of them, on both sides of\n"
                            + "the wall.",
                    live, true),
            core.configLocalBool("Content", "OwnZombies",
                    "true: the mod's own skins for zombies near the eye, each with a lifelike face.\n"
                            + "false: the game's own painted skins.",
                    live, true),
            core.configLocalBool("Content", "OwnPeople",
                    "true: the mod's own skins for your character and the other living, in the game's\n"
                            + "five skin tones, each with a lifelike face. false: the game's own painted skins.",
                    live, true),
            core.configLocalBool("Content", "OwnArms",
                    "true: arms and hands of the mod's own on a held weapon, with fingers that close on\n"
                            + "it and go to its bolt, slide, magazine and loading port as it is worked and loaded,\n"
                            + "in the skin, sleeves and gloves your character has. false: the game's own arms.",
                    live, true),
            core.configLocalBool("Content", "OwnClothes",
                    "true: what people and zombies wear is made finer near the eye: the game's picture\n"
                            + "of each garment and head of hair enlarged with its outline smoothed, and the weave,\n"
                            + "knit, grain or strands of what it is made of laid on from the mod's own photographs.\n"
                            + "false: the game's own pictures as they are.",
                    live, true),
        };
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
        return new Live(core.configGetFloat(fieldOfView), rows, core.configGetInt(quality), core.configGetInt(style),
                core.configGetInt(frameBlending) != 0, core.configGetInt(splitScreen));
    }

    /**
     * When CameraUnlock.ini or Defaults.ini has changed since core last read or wrote it, reads
     * both again and answers what they give the rows read while the game runs. Null when nothing
     * changed or the file cannot be read, which core's own line in the log says.
     */
    Live reload() {
        return core.configReload() == CameraUnlock.RELOAD_APPLIED ? live() : null;
    }

    /** The UDP port a second player's tracker is listened for on. */
    int secondPlayerPort() {
        return core.configGetInt(secondPlayerPort);
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
        return saved("graphics mode", core.configSaveInt(quality, row));
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
