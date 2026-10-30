package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
     * The bits of pressed keys: core's three, then the mod's own. The first person, graphics and
     * style keys are the first player's tapped once, the second player's tapped twice and so on
     * to the fourth, and every player's held. A view is handed FIRST_PERSON, CYCLE_QUALITY or
     * CYCLE_STYLE for whichever of those is its own.
     */
    static final int CYCLE_TRACKING_MODE = CameraUnlock.HOTKEY_CYCLE_TRACKING_MODE,
            YAW_MODE = CameraUnlock.HOTKEY_YAW_MODE, AIM_MODE = CameraUnlock.HOTKEY_AIM_MODE,
            FIRST_PERSON = CameraUnlock.HOTKEY_LOCAL, CYCLE_QUALITY = CameraUnlock.HOTKEY_LOCAL << 1,
            CYCLE_STYLE = CameraUnlock.HOTKEY_LOCAL << 2, SPLIT_SCREEN = CameraUnlock.HOTKEY_LOCAL << 4,
            FIRST_PERSON_ALL = CameraUnlock.HOTKEY_LOCAL << 5, CYCLE_QUALITY_ALL = CameraUnlock.HOTKEY_LOCAL << 11,
            CYCLE_STYLE_ALL = CameraUnlock.HOTKEY_LOCAL << 15;
    /** Each of those keys' bits by how many times it was tapped: one tap is the first player's, and each is a player's, by number. */
    static final int[] FIRST_PERSON_TAPS = {FIRST_PERSON, CameraUnlock.HOTKEY_LOCAL << 3, CameraUnlock.HOTKEY_LOCAL << 6,
        CameraUnlock.HOTKEY_LOCAL << 7};
    static final int[] CYCLE_QUALITY_TAPS = {CYCLE_QUALITY, CameraUnlock.HOTKEY_LOCAL << 8, CameraUnlock.HOTKEY_LOCAL << 9,
        CameraUnlock.HOTKEY_LOCAL << 10};
    static final int[] CYCLE_STYLE_TAPS = {CYCLE_STYLE, CameraUnlock.HOTKEY_LOCAL << 12, CameraUnlock.HOTKEY_LOCAL << 13,
        CameraUnlock.HOTKEY_LOCAL << 14};
    /** What the keys of a player's own rows begin with, by player: the first player's rows are Quality, IsometricQuality and Style. */
    private static final String[] PLAYER_ROW = {"", "SecondPlayer", "ThirdPlayer", "FourthPlayer"};

    /** Player 2's port; each player after them has the next. */
    private static final int SECOND_PLAYER_PORT = 4243;
    private static final String[] PLAYER_PORT_ROWS = {"SecondPlayerUdpPort", "ThirdPlayerUdpPort", "FourthPlayerUdpPort"};
    /** Each player's microphone, player 1's first. */
    static final String[] MICROPHONE_ROWS = {"Microphone", "SecondPlayerMicrophone", "ThirdPlayerMicrophone", "FourthPlayerMicrophone"};
    static final String TALKING_MOUTHS = "TalkingMouths";

    /** The last line of a row whose patches FirstPersonEnabled also needs, saying when off is taken. */
    private static final String BOTH_OFF = "Takes effect at once. With this and FirstPersonEnabled both off, the mod stops changing the game's own code for\n"
            + "this from the next start.";

    private static final String[] LOAD_STATUS = {"read", "imported", "created", "deferred", "legacy refused", "unreadable"};
    private static final String[] SAVE_STATUS = {"saved", "not saved", "may not be saved"};

    /**
     * What the rows read again while the game runs hold: the field of view in degrees, Own's bits, Rules' bits,
     * by player the Quality rows, the IsometricQuality rows as Quality modes and the Style rows, the SpreadLightUpdates row, the FirstPerson rows and the FirstPersonInVehicles
     * rows as a bit a player, the HillHeight row, the MuzzleFlash row, the Crosshair row, the RegripTwoHandedWeapons row, the SplitScreen row, the rows of [Graphics], the TalkingMouths row
     * and each player's microphone.
     */
    record Live(float fieldOfView, int own, int rules, int[] quality, int[] overheadQuality, int[] style, boolean spreadLightUpdates, int firstPerson, int firstPersonInVehicles, float hillHeight, boolean muzzleFlash, boolean crosshair, boolean regrip, int splitScreen,
            Quality.Picture custom, boolean talkingMouths, List<String> microphones, long parts) {}

    /** The keys of [Content], in the order of Own's bits. */
    static final String[] VANILLA_KEYS = {"VanillaWeapons", "VanillaFurniture", "VanillaPictures", "VanillaPlants", "VanillaTextures", "VanillaBuildings",
        "VanillaZombies", "VanillaPeople", "VanillaArms", "VanillaClothes", "VanillaAnimals", "VanillaFire", "VanillaCabins"};
    /** The keys of the rows of [Rules] that are not parts, in the order of Rules' bits. */
    static final String[] RULE_KEYS = {"ShotsThroughWalls", "ShotsBreakLamps", "ShotsBreakGlass", "ShotsWoundLimbs", "VehiclesSmashThrough", "FacesTheView",
        "ShotsPastTrees", "ZombiesSeenAtOnce", "Rewind30s", "HiddenWallDecor", "HiddenWallDecorSaved", "BlastHoles", "BulletHoles"};

    /** The keys of [Graphics], the Custom graphics mode, in the order the options screen shows them. */
    static final String[] GRAPHICS = {"TreeQuality", "GrassDensity", "GrassDistance", "RenderScale", "ViewDistance", "ZombiesAndPropsDrawDistance",
        "FarTreeDistance", "RainOnGlass", "ShadingAndLight", "Reflections", "ReflectionScale", "FullSizeTextures", "SurfaceDepth", "SharpShadows",
        "WholeFigureShadows", "FrameBlending", "Antialiasing", "LightDistance", "CorpseModels"};
    private static final int RENDER_SCALE = 3, REFLECTION_SCALE = 10;
    static final float MIN_RENDER_SCALE = 0.5f, MIN_REFLECTION_SCALE = 0.25f;
    static final int MIN_VIEW_DISTANCE = 32, MIN_DRAW_DISTANCE = 16;

    private final CameraUnlock core;
    private final int fieldOfView, spreadLightUpdates, hillHeight, muzzleFlash, crosshair, regrip, splitScreen, talkingMouths;
    /** By player, the rows of their first person graphics mode, their isometric view's and their picture style. */
    private final int[] quality = new int[View.COUNT], overheadQuality = new int[View.COUNT], style = new int[View.COUNT];
    private final int[] firstPerson = new int[View.COUNT], firstPersonInVehicles = new int[View.COUNT];
    private final int firstPersonKey, cycleQualityKey, cycleStyleKey, splitScreenKey;
    /** Each of the mod's own rows the options screen can show, by its key: what saveOptions writes in one go. */
    private final Map<String, Integer> localRows = new HashMap<>();
    /** The options as core lists them once the file is read, and each one's place by its key. */
    private CameraUnlock.Option[] options;
    private final Map<String, Integer> optionPlaces = new HashMap<>();
    /** The rows of each player's microphone, in the order of MICROPHONE_ROWS. */
    private final int[] microphones = new int[MICROPHONE_ROWS.length];
    /** The rows of the ports of players 2, 3 and 4. */
    private final int[] playerPorts = new int[PLAYER_PORT_ROWS.length];
    /** The rows of [Content], in the order of Own's bits. Each is on for the game's own, so its bit is set where the row is off. */
    private final int[] own;
    /** The rows of [Rules], in the order of Rules' bits, NO_ROW for Rewind30s outside a development build: see Rewind. */
    private final int[] rules;
    private static final int NO_ROW = -1;
    /** The rows of [Graphics], in the order of GRAPHICS. */
    private final int[] graphics;
    /** The row of each Part, by its ordinal. */
    private final int[] parts = new int[Part.values().length];
    /** For each of the mod's own rows the game's options screen shows, by its key: what puts a value applied there on the running game. */
    private final Map<String, DoubleConsumer> puts = new HashMap<>();

    /**
     * Describes the rows. The tracking mode pair, WorldSpaceYaw and the three aim mode rows are
     * writable: their hotkeys save. EnableOnStartup, the smoothing pair and LightMultiplier are
     * writable for the options screen, where a save of one writes its value in place of default,
     * as a hotkey's save does. There is no key that switches head tracking off: the
     * owner ruled the action out for this game, and the three keys there are are the game's own
     * rows on the nav keys alone (see cameraunlock-core's per_game). FieldOfView, FirstPerson, Quality, IsometricQuality, Style,
     * the rows of [Players], HillHeight, MuzzleFlash, Crosshair, RegripTwoHandedWeapons, SplitScreen and the rows of [Content] and [Rules] are the mod's own, and the ones read
     * again while the game runs, and so are the rows of [Graphics], which are what the Custom
     * graphics mode draws, and the rows of [Voice]. They are writable too, and a writable row that is not a key
     * list or a microphone's name is a setting on the game's options screen (OptionsPage), which shows the
     * microphones as lists of its own. SecondPlayerUdpPort, ThirdPlayerUdpPort, FourthPlayerUdpPort, FirstPersonKey, CycleQualityKey,
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
        core.configConcept("EnableOnStartup", writable,
                "true: head tracking is on. Your tracker moves the first person view and turns your character's head in the isometric\n"
                        + "view. false: it is off. No key turns it on or off. The options screen changes it when you apply, and an edit here\n"
                        + "takes effect at the next start.", null);
        core.configConcept("WorldSpaceYaw", writable,
                "true: turning your head left or right turns the view around the vertical, which keeps the horizon level while you look\n"
                        + "up or down. false: it turns around the view's own up, which tilts the horizon while you look up or down. In a\n"
                        + "vehicle seat it always turns around the view's own up.", null);
        core.configConcept("RotationEnabled", writable, null, null);
        core.configConcept("DataFreshnessMs", 0, null, null);
        // A tracker's packets from 127.0.0.0/8 or ::1 are local, from any other address remote: cameraunlock-core's rule.
        core.configConcept("LocalSmoothing", writable,
                "Smoothing for a tracker on this PC that sends to 127.0.0.1. 0 is the least, 1 the most.", null);
        core.configConcept("RemoteSmoothing", writable,
                "Smoothing for every other tracker: one on another device such as a phone, or one on this PC that sends to this PC's\n"
                        + "network address instead of 127.0.0.1. 0 is the least, 1 the most.", null);
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
        core.configConcept("CollisionEnabled", 0,
                "true: leaning your head stops at walls instead of moving the view through them. false: the lean is not held off\n"
                        + "walls, so leaning into one can put the view through it.", null);
        core.configConcept("CollisionMargin", 0, "How far the view is held off a wall when you lean into it, in map tiles.",
                COLLISION_MARGIN);
        core.configConcept("CollisionReleaseSmoothing", 0, null, null);
        int perGame = CameraUnlock.ROW_PER_GAME;
        core.configConcept("CycleTrackingModeKey", perGame, null, "PageUp");
        core.configConcept("YawModeKey", perGame, null, "PageDown");
        core.configConcept("TrueFreeLookKey", perGame,
                "Cycles the aim mode: sights locked, free look with a marker, stock sights.", "Insert");
        core.configConcept("LightMultiplier", writable,
                "How far the flashlight turns for each degree your head turns, while TorchFollowsHead is on. 1 turns it as far as\n"
                        + "the view, more turns it further, and 0 leaves it where your character faces.", null);

        describe(Part.FIRST_PERSON);
        fieldOfView = localFloat("General", "FieldOfView",
                "Vertical field of view of the first person view, in degrees: 40 to 110.",
                writable | live, FieldOfView.DEFAULT_DEGREES, MIN_FIELD_OF_VIEW, MAX_FIELD_OF_VIEW);
        quality[0] = localEnum("General", "Quality",
                "First person graphics mode: Automatic, Low, Medium, High or Custom. Automatic draws less when the frame rate drops\n"
                        + "and more again when it recovers. Custom draws the [Graphics] rows. Every mode but Custom draws at most 2560x1440\n"
                        + "pixels, however large the window.",
                writable | live, Quality.words(), Quality.AUTOMATIC);
        overheadQuality[0] = localEnum("General", "IsometricQuality",
                "Graphics mode of the game's isometric view: Low (the game's own picture), Medium, High or Custom, which draws the\n"
                        + "[Graphics] rows.",
                writable | live, Quality.overheadWords(), 0);
        style[0] = localEnum("General", "Style",
                "Picture style: Vivid (strong contrast and colour), Natural (colours as lit, less contrast), Bleak (washed out, close\n"
                        + "to the game's own look), Warm (golden light and amber shade) or Cold (steel blue shade and pale light). A new\n"
                        + "style fades in over most of a second.",
                writable | live, Style.words(), Style.BLEAK.ordinal());
        firstPerson[0] = localBool("General", "FirstPerson",
                "true: saves load in first person. Switching the view with the first person key, or a double tap of Back on a\n"
                        + "controller, changes this to match.", writable | live, false);
        firstPersonInVehicles[0] = localBool("General", "FirstPersonInVehicles",
                "true: getting into a vehicle switches to first person, and getting out switches back to the isometric view. The first\n"
                        + "person key still switches the view at any time, and a view it chose is kept when you get out.",
                writable | live, false);
        // Not a row of [Graphics]: it changes what the land looks like and not what the picture costs, in every graphics mode alike.
        hillHeight = localFloat("General", "HillHeight",
                "How tall the first person hills are: 0 is as flat as the game's map, 1 is as the mod makes them, up to " + HillHeight.MOST
                        + ".\nAlways 1 where players can fight.",
                writable | live, HillHeight.DEFAULT, 0f, HillHeight.MOST);
        muzzleFlash = localBool("General", "MuzzleFlash",
                "true: firing a gun flashes at the muzzle and briefly lights up what is nearby, in every graphics mode but Low.",
                writable | live, true);
        crosshair = localBool("General", "Crosshair",
                "true: in first person, while the game draws no reticle of its own, a small crosshair with a weapon in hand, or a dot\n"
                        + "without one, shows where the shot lands and what the interact key uses. false: neither is drawn.",
                writable | live, true);
        regrip = localBool("General", "RegripTwoHandedWeapons",
                "true: a two-handed weapon goes back into both hands when your left hand puts down what it held beside it, as the\n"
                        + "inventory's Equip Two Hands does. A light in your left hand is put away when you switch it off, so the weapon goes\n"
                        + "back into both hands, and taking up a light leaves the weapon in your right hand. false: as in the game, the weapon\n"
                        + "stays in your right hand alone, and taking up a light takes it out of your hands. Never on a server where players\n"
                        + "can fight.",
                writable | live, true);
        splitScreen = localEnum("General", "SplitScreen",
                "How two players share one screen: Vertical (side by side) or Horizontal (above and below). Takes effect at once.\n"
                        + "Vertical is the game's own split screen, and from the next start the mod leaves that part of the game alone.",
                writable | live, SplitScreen.words(), SplitScreen.VERTICAL);
        describeParts("FirstPerson");
        for (int player = 1; player < View.COUNT; player++) {
            String whose = "Player " + (player + 1) + "'s ";
            quality[player] = localEnum("Players", qualityKey(player), whose + "first person graphics mode, as Quality.",
                    writable | live, Quality.words(), Quality.AUTOMATIC);
            overheadQuality[player] = localEnum("Players", overheadQualityKey(player),
                    whose + "isometric graphics mode, as IsometricQuality.", writable | live, Quality.overheadWords(), 0);
            style[player] = localEnum("Players", styleKey(player), whose + "picture style, as Style.", writable | live,
                    Style.words(), Style.BLEAK.ordinal());
            firstPerson[player] = localBool("Players", PLAYER_ROW[player] + "FirstPerson", whose + "view at load, as FirstPerson.",
                    writable | live, false);
            firstPersonInVehicles[player] = localBool("Players", PLAYER_ROW[player] + "FirstPersonInVehicles",
                    whose + "view in vehicles, as FirstPersonInVehicles.", writable | live, false);
        }
        Quality.Picture high = Quality.HIGH_PICTURE;
        graphics = new int[] {
            localEnum("Graphics", "TreeQuality",
                    "Low: the game's plant sprites. Medium: the mod's trees, with the game's sprites past " + (int) Crowns.REACH + " tiles.\n"
                            + "High: the mod's trees as far as you can see.",
                    writable | live, Quality.TREE_QUALITIES, high.treeQuality()),
            localInt("Graphics", "GrassDensity",
                    "How much of the grass the mod grows blade by blade is grown, as a percentage: " + Quality.LEAST_GRASS + " to " + Quality.ALL_GRASS + ".\n"
                            + "Lower, fewer and broader plants cover the same lawns and long grass, which costs less to build and draw. High\n"
                            + "grows all of it and Medium " + Quality.SOME_GRASS + ". On a server where players can hurt each other it grows all of it.\n"
                            + "With TreeQuality Low the mod grows no grass of its own.",
                    writable | live, high.grassDensity(), Quality.LEAST_GRASS, Quality.ALL_GRASS),
            localInt("Graphics", "GrassDistance",
                    "How far from you lawns are grown blade by blade in first person, in tiles: " + Quality.LEAST_GRASS_DISTANCE + " to "
                            + Quality.MOST_GRASS_DISTANCE + ". Past that, lawns are flat. Higher\n"
                            + "costs more to build and draw. Medium and High grow lawns to " + high.grassDistance() + " tiles. Long grass is not affected.\n"
                            + "With TreeQuality Low the mod grows no grass of its own.",
                    writable | live, high.grassDistance(), Quality.LEAST_GRASS_DISTANCE, Quality.MOST_GRASS_DISTANCE),
            localFloat("Graphics", "RenderScale",
                    "How much of the window's resolution the first person picture is drawn at, each way: 0.5 to 1.",
                    writable | live, high.renderScale(), MIN_RENDER_SCALE, 1f),
            localInt("Graphics", "ViewDistance",
                    "How far the first person view is drawn, in tiles: 32 to 80, within the map the game has loaded.",
                    writable | live, high.viewDistance(), MIN_VIEW_DISTANCE, high.viewDistance()),
            localInt("Graphics", "ZombiesAndPropsDrawDistance",
                    "How far zombies, people, vehicles and the mod's furniture are drawn, in tiles: " + MIN_DRAW_DISTANCE + " to " + high.viewDistance()
                            + ", never past\nViewDistance. Where players can fight, everything is drawn.",
                    writable | live, high.drawDistance(), MIN_DRAW_DISTANCE, high.viewDistance()),
            localInt("Graphics", "FarTreeDistance",
                    "How far past the loaded map trees are drawn, in tiles: 0 (none) to " + Quality.MOST_FAR_TREES + ".",
                    writable | live, high.farTreeDistance(), 0, Quality.MOST_FAR_TREES),
            localEnum("Graphics", "RainOnGlass",
                    "Rain on your vehicle's windows: Low (one size of drop), Medium (adds specks and trickles) or High (three sizes and\n"
                            + "running drops).",
                    writable | live, "Low,Medium,High", high.rainOnGlass()),
            localBool("Graphics", "ShadingAndLight",
                    "true: shaded corners, light from things nearby and lit air, and with Reflections, wet roads, floors, glass and water\n"
                            + "that mirror the world.",
                    writable | live, high.shadingAndLight()),
            localBool("Graphics", "Reflections",
                    "true: mirrors in rooms show the room and you, your vehicle's mirrors show the road behind, and with\n"
                            + "ShadingAndLight, wet roads, floors, glass and water mirror the world.",
                    writable | live, high.reflections()),
            localFloat("Graphics", "ReflectionScale",
                    "How much of the first person picture's resolution wall mirrors and window glass draw their reflections at, each\n"
                            + "way: " + MIN_REFLECTION_SCALE + " to 1. High draws " + high.reflectionScale() + ", Medium "
                            + Quality.MEDIUM_PICTURE.reflectionScale() + ". Lower costs less and looks softer.",
                    writable | live, high.reflectionScale(), MIN_REFLECTION_SCALE, 1f),
            localBool("Graphics", "FullSizeTextures",
                    "true: the mod's surface, wall and skin pictures at full size. false: half size, using a quarter of the memory.",
                    writable | live, high.fullSizeTextures()),
            localBool("Graphics", "SurfaceDepth",
                    "true: near surfaces get the depth of what they show. false: they are drawn flat.",
                    writable | live, high.surfaceDepth()),
            localBool("Graphics", "SharpShadows",
                    "true: shadow maps 4096 pixels across. false: 2048, using a quarter of the graphics memory.",
                    writable | live, high.sharpShadows()),
            localBool("Graphics", "WholeFigureShadows",
                    "true: what people and zombies wear and carry casts a shadow at any distance. false: past " + (int) CharacterDraw.SHADOW_WHOLE_WITHIN
                            + " tiles only\ntheir bodies do, which costs less in a crowd.",
                    writable | live, high.wholeFigureShadows()),
            localBool("Graphics", "FrameBlending",
                    "true: each frame is blended with the ones before it (temporal anti-aliasing), which smooths grass, leaves and fine\n"
                            + "edges that Antialiasing alone cannot, for a slightly softer picture. Medium and High use it.",
                    writable | live, high.frameBlending()),
            localEnum("Graphics", "Antialiasing",
                    "How jagged edges are smoothed, from each frame alone: Off; Low, a quick blend across each edge; Medium, which also\n"
                            + "follows each edge to its ends so long shallow edges come out even; High, which follows them further and catches\n"
                            + "fainter ones too. The Low, Medium and High graphics modes use Low, whose slight softening also calms the shimmer\n"
                            + "of FrameBlending; Medium and High only touch the edges, for a sharper picture.",
                    writable | live, Quality.ANTIALIASINGS, high.antialiasing()),
            localInt("Graphics", "LightDistance",
                    "How far away lamps light what is around them with light of their own, like the pool of light under a street lamp,\n"
                            + "in tiles: 0 (the game's light only) to " + Quality.MOST_LIGHT_DISTANCE + ". Past that, lamps light the ground as the game does.",
                    writable | live, high.lightDistance(), 0, Quality.MOST_LIGHT_DISTANCE),
            localBool("Graphics", "CorpseModels",
                    "true: corpses are drawn as models lying where they fell, as zombies are. false: as the game's flat picture of each,\n"
                            + "laid on the ground, which costs far less where many lie. Low, Medium and High use the flat picture.",
                    writable | live, high.corpseModels()),
        };
        spreadLightUpdates = localBool("Optimizations", "SpreadLightUpdates",
                "true: when the game relights many places at once, the first person view takes in their new light over the next few\n"
                        + "frames, nearest first, so driving past lights stutters less. false: all of it in the frame the game relights it,\n"
                        + "so a far light change can show about a tenth of a second sooner, in longer frames.",
                writable | live, true);
        describeParts("Optimizations");

        firstPersonKey = core.configLocalHotkeyTaps("FirstPersonKey",
                "Switches between first person and the isometric view. Tap once for player 1, twice for player 2, three times for\n"
                        + "player 3, four times for player 4, or hold for every player.",
                0, "Delete", FIRST_PERSON_TAPS, FIRST_PERSON_ALL);
        cycleQualityKey = core.configLocalHotkeyTaps("CycleQualityKey",
                "Goes to the next graphics mode of the view a player is in: in first person Automatic, Low, Medium, High, Custom\n"
                        + "(Quality), in the isometric view Low, Medium, High, Custom (IsometricQuality), and for players 2, 3 and 4 their\n"
                        + "rows of [Players]. Tap once for player 1, twice for player 2, three times for player 3, four times for player 4,\n"
                        + "or hold for every player.",
                0, "F9", CYCLE_QUALITY_TAPS, CYCLE_QUALITY_ALL);
        cycleStyleKey = core.configLocalHotkeyTaps("CycleStyleKey",
                "Goes to a player's next picture style: Vivid, Natural, Bleak, Warm, Cold (Style, and for players 2, 3 and 4\n"
                        + "their rows of [Players]). Tap once for player 1, twice for player 2, three times for player 3, four times for\n"
                        + "player 4, or hold for every player.",
                0, "F8", CYCLE_STYLE_TAPS, CYCLE_STYLE_ALL);
        splitScreenKey = core.configLocalHotkey("SplitScreenKey",
                "With two players at the screen, switches between side by side and above and below.", 0, "F7",
                SPLIT_SCREEN);
        own = new int[] {
            localBool("Content", "VanillaWeapons",
                    "true: the game's own weapons, with no picture through the scope. false: the mod's models of the pump shotgun and\n"
                            + "its sawn-off, the tactical pump shotgun, the hunting, varmint and tactical rifles, the lever action rifle and\n"
                            + "carbine, the self-loading rifle and carbine, the double barrelled shotgun and its sawn-off, the pistols, the\n"
                            + "revolvers, the toy cap pistol and rifle, the assault rifle, the battle rifle and rifle scopes.",
                    writable | live, false),
            localBool("Content", "VanillaFurniture",
                    "true: the game's sprites. false: the mod's models of furniture, appliances, street lamps, clocks, picture frames,\n"
                            + "banners and flags.",
                    writable | live, false),
            localBool("Content", "VanillaPictures",
                    "true: the game's pictures on walls, and only colour bars on a television. false: the mod's own pictures on walls\n"
                            + "and televisions, where it has one.",
                    writable | live, false),
            localBool("Content", "VanillaPlants",
                    "true: the game's sprites. false: the mod's trees, bushes, boulders, grass, ferns, flowers, crops, fallen leaves and\n"
                            + "fallen sticks.",
                    writable | live, false),
            localBool("Content", "VanillaTextures",
                    "true: the colours of the game's sprites, and its own stained glass. false: the mod's textures of ground, wood,\n"
                            + "roofing, paint, carpet, cloth, wallpaper and stained glass, and clean road lines.",
                    writable | live, false),
            localBool("Content", "VanillaBuildings",
                    "true: the game's sprites. false: windows, doors, curtains, handrails, chain link fences, open stairs, railway\n"
                            + "track and wall finishes built by the mod.",
                    writable | live, false),
            localBool("Content", "VanillaZombies",
                    "true: the game's own skins for zombies. false: the mod's skins, with lifelike faces, for zombies near you.",
                    writable | live, false),
            localBool("Content", "VanillaPeople",
                    "true: the game's own skins for people. false: the mod's skins, with lifelike faces, for your character and other\n"
                            + "living people.",
                    writable | live, false),
            localBool("Content", "VanillaArms",
                    "true: the game's own arms and hands on a held weapon, for a mod that changes the arms or the weapons. false: the\n"
                            + "mod's arms and hands, which work the bolt, slide and magazine.",
                    writable | live, false),
            localBool("Content", "VanillaClothes",
                    "true: the game's own pictures of clothes and hair. false: clothes and hair near you are sharpened, with the weave,\n"
                            + "knit, grain or strands of what they are made of.",
                    writable | live, false),
            localBool("Content", "VanillaAnimals",
                    "true: the game's own skins for animals. false: the mod's skins for animals near you, with the hair of their coats.",
                    writable | live, false),
            localBool("Content", "VanillaFire",
                    "true: the game's own flames and smoke, standing upright where they burn. false: the game's flames with the mod's\n"
                            + "smoke, embers and firelight for burning buildings, Molotovs, campfires, fireplaces, wood stoves, burn barrels\n"
                            + "and cooking pits.",
                    writable | live, false),
            localBool("Content", "VanillaCabins",
                    "true: the game's own inside of the vehicle you sit in, with its glass cut away. false: the mod's model of the inside,\n"
                            + "with its dashboard, instruments, wheel, seats and doors.",
                    writable | live, false),
        };
        rules = new int[] {
            localBool("Rules", "ShotsThroughWalls",
                    "true: your shots go on through stud walls, plank walls, shut doors and windows, losing damage to each by what it\n"
                            + "is made of. false: they stop at them, as in the game. On a server where players can hurt each other the game's\n"
                            + "rule applies.\n" + BOTH_OFF,
                    writable | live, true),
            localBool("Rules", "ShotsBreakLamps",
                    "true: offline, a shot breaks a lamp it passes through and a vehicle light it hits. false: lamps stay lit, as in the\n"
                            + "game. On a server the game's rule applies.",
                    writable | live, true),
            localBool("Rules", "ShotsBreakGlass",
                    "true: offline, a shot breaks the glass of every shut window it crosses, whether or not it hits anything behind it.\n"
                            + "false: only a shot that hits someone through a window breaks it, as in the game. On a server the game's rule\n"
                            + "applies.",
                    writable | live, true),
            localBool("Rules", "ShotsWoundLimbs",
                    "true: offline, first person shots to a zombie's legs slow it and drop it to a crawl, shots to its arms leave them\n"
                            + "hanging, enough take the limb off, and a killing head shot can take the head off or burst it. false: hits are\n"
                            + "the game's. On a server the game's rule applies.",
                    writable | live, true),
            localBool("Rules", "VehiclesSmashThrough",
                    "true: offline, a vehicle going faster than walking pace knocks down fences, street lamp posts and road barricades,\n"
                            + "drives through bushes without slowing and keeps its engine's full power off the road. false: the game's rules\n"
                            + "for all of it. On a server the game's rules apply.\n" + Part.LIVE_AND_NEXT_START,
                    writable | live, true),
            localBool("Rules", "FacesTheView",
                    "true: walking or sneaking sideways or backwards in first person, your character keeps facing where you look, and\n"
                            + "you jog or sprint only straight ahead or on a forward diagonal. false: it always turns, as in the game. On a\n"
                            + "server where players can hurt each other it keeps facing where you look when going sideways and backwards, on\n"
                            + "or off, at the slower pace the game gives its aiming stance.",
                    writable | live, true),
            localBool("Rules", "ShotsPastTrees",
                    "true: a tree stops your shots only where its trunk is drawn, and a shot past the trunk goes on. false: a tree stops\n"
                            + "every shot across its square, as in the game. On a server where players can hurt each other the game's rule\n"
                            + "applies.",
                    writable | live, true),
            localBool("Rules", "ZombiesSeenAtOnce",
                    "true: in the isometric view a zombie shows as soon as nothing stands between your character and it, within the\n"
                            + "reach of your character's sight by the game's rules. false: the game's own sight, which can show a zombie late\n"
                            + "or only once it is close. On a server where players can hurt each other the game's rule applies.\n"
                            + Part.LIVE_AND_NEXT_START,
                    writable | live, true),
            !DevBuild.ON ? NO_ROW : localBool("Rules", "Rewind30s",
                    "true: offline, when you die the game pauses and asks whether to go back to where you were 30 seconds before, with\n"
                            + "what you had then and the wounds you had then. The world goes on as it was, the zombie that killed you\n"
                            + "included, but every zombie within 4 tiles of you is moved out to 4 tiles and every zombie stands still for 2\n"
                            + "seconds after. false: death is final, as in the game. On a server the game's rule applies.\n"
                            + Part.LIVE_AND_NEXT_START,
                    writable | live, false),
            localBool("Rules", "HiddenWallDecor",
                    "true: in first person, the south and east walls of a room, which the isometric view never shows from inside, get\n"
                            + "pictures drawn by the mod and never saved. false: nothing is drawn on them, and nothing is added to them\n"
                            + "whatever HiddenWallDecorSaved says. On a server the pictures are drawn too.\n" + BOTH_OFF,
                    writable | live, true),
            localBool("Rules", "HiddenWallDecorSaved",
                    "true: offline, while HiddenWallDecor is on, those walls also get the game's own mirrors, clocks and coat racks where\n"
                            + "the room's other walls say they belong, the first time the room is loaded, and those stay in your save.\n"
                            + "false: nothing is added to the save, and what was added before stays. On a server nothing is added.",
                    writable | live, true),
            localBool("Rules", "BlastHoles",
                    "true: offline, a shotgun blast in first person whose pellets land close together on a wooden door or a wooden\n"
                            + "fence blows a ragged hole through it that you can see and shoot through, and the pellets that miss the hole\n"
                            + "leave marks on a door. The holes stay in your save. false: shots leave doors and fences whole. On a server\n"
                            + "doors and fences stay whole.",
                    writable | live, true),
            localBool("Rules", "BulletHoles",
                    "true: offline, while ShotsThroughWalls is on, a round you fire in first person that goes through a door, a wall\n"
                            + "or a fence leaves a small hole where it went in and a wider, splintered one where it came out, which you can\n"
                            + "see through. The holes stay in your save. false: rounds still go through, and leave only a mark. On a server\n"
                            + "nothing is holed.",
                    writable | live, true),
        };
        describeParts("Rules");
        describeParts("Fixes");

        talkingMouths = localBool("Voice", TALKING_MOUTHS,
                "true: your character's mouth moves with what you say into your microphone: the jaw drops for \"ah\", the lips spread\n"
                        + "for \"ee\" and round for \"oo\". Online, other players' mouths move while the game's voice chat plays them, and\n"
                        + "other players with this mod see your mouth move. The sound is never recorded or sent. false: no microphone is\n"
                        + "opened. Takes effect at once, but at the next start for the people the game draws itself, as in the isometric view\n"
                        + "at Low. With this and FirstPersonEnabled both off, the mod stops changing the game's own code for this from the\n"
                        + "next start.",
                writable | live, false);
        for (int player = 0; player < microphones.length; player++) {
            microphones[player] = core.configLocalText("Voice", MICROPHONE_ROWS[player],
                    (player == 0 ? "Your microphone" : "The microphone of player " + (player + 1) + " at the screen") + ", by the name Windows gives it, "
                            + Microphones.DEFAULT + " for Windows' own default, or " + Microphones.NONE + ".",
                    writable | live, player == 0 ? Microphones.DEFAULT : Microphones.NONE);
        }

        // Each put changes the running game alone: Tracking.applyScreen takes the patches in and logs what changed once for the lot.
        puts.put("FieldOfView", value -> FieldOfView.set((float) value));
        for (int player = 0; player < View.COUNT; player++) {
            int whose = player;
            puts.put(qualityKey(player), value -> Quality.set(whose, (int) value));
            puts.put(overheadQualityKey(player), value -> Quality.setOverhead(whose, (int) value + Quality.LOW));
            puts.put(styleKey(player), value -> Style.set(whose, (int) value));
            puts.put(PLAYER_ROW[player] + "FirstPerson", value -> Tracking.firstPersonAtLoad(whose, value != 0));
            puts.put(PLAYER_ROW[player] + "FirstPersonInVehicles", value -> Tracking.firstPersonInVehicles(whose, value != 0));
        }
        puts.put("HillHeight", value -> HillHeight.set((float) value));
        puts.put("MuzzleFlash", value -> MuzzleFlash.set(value != 0));
        puts.put("Crosshair", value -> Crosshair.set(value != 0));
        puts.put("RegripTwoHandedWeapons", value -> Regrip.set(value != 0));
        puts.put("SpreadLightUpdates", value -> PendingLights.set(value != 0));
        puts.put("SplitScreen", value -> SplitScreen.set((int) value));
        puts.put(TALKING_MOUTHS, value -> {
            Mouths.set(value != 0);
            Microphones.set(value != 0, Tracking.microphones().toArray(String[]::new));
        });
        for (int i = 0; i < VANILLA_KEYS.length; i++) {
            int bit = 1 << i;
            puts.put(VANILLA_KEYS[i], value -> Own.update(rows -> value != 0 ? rows & ~bit : rows | bit));
        }
        for (Part part : Part.values()) {
            // A part read only as the game starts is saved, and taken then.
            puts.put(part.key, value -> {
                if (part.live) {
                    part.set(value != 0);
                }
            });
        }
        for (int i = 0; i < RULE_KEYS.length; i++) {
            int bit = 1 << i;
            puts.put(RULE_KEYS[i], value -> Rules.update(rows -> value != 0 ? rows | bit : rows & ~bit));
        }
    }

    /** A part's row, true unless the player says otherwise: read again while the game runs only where the part is taken then. */
    private void describe(Part part) {
        parts[part.ordinal()] = localBool(part.section, part.key, part.comment,
                CameraUnlock.ROW_WRITABLE | (part.live ? CameraUnlock.ROW_LIVE : 0), true);
    }

    /** The rows of every part of a section of its own, in the order Part lists them. */
    private void describeParts(String section) {
        for (Part part : Part.values()) {
            if (part.section.equals(section)) {
                describe(part);
            }
        }
    }

    private int localBool(String section, String key, String comment, int flags, boolean value) {
        return kept(key, core.configLocalBool(section, key, comment, flags, value));
    }

    private int localInt(String section, String key, String comment, int flags, int value, int min, int max) {
        return kept(key, core.configLocalInt(section, key, comment, flags, value, min, max));
    }

    private int localFloat(String section, String key, String comment, int flags, float value, float min, float max) {
        return kept(key, core.configLocalFloat(section, key, comment, flags, value, min, max));
    }

    private int localEnum(String section, String key, String comment, int flags, String tokens, int value) {
        return kept(key, core.configLocalEnum(section, key, comment, flags, tokens, value));
    }

    private int kept(String key, int row) {
        localRows.put(key, row);
        return row;
    }

    /** The key of a player's row of whether their saves load in first person: FirstPerson, SecondPlayerFirstPerson and so on. */
    static String firstPersonRow(int player) {
        return PLAYER_ROW[player] + "FirstPerson";
    }

    static String firstPersonInVehiclesRow(int player) {
        return PLAYER_ROW[player] + "FirstPersonInVehicles";
    }

    /** The key of a player's first person graphics mode: Quality, SecondPlayerQuality and so on. */
    static String qualityKey(int player) {
        return PLAYER_ROW[player] + "Quality";
    }

    /** The key of a player's isometric graphics mode: IsometricQuality, SecondPlayerIsometricQuality and so on. */
    static String overheadQualityKey(int player) {
        return PLAYER_ROW[player] + "IsometricQuality";
    }

    static String styleKey(int player) {
        return PLAYER_ROW[player] + "Style";
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
        return new double[] {picture.treeQuality(), picture.grassDensity(), picture.grassDistance(), picture.renderScale(), picture.viewDistance(),
            picture.drawDistance(), picture.farTreeDistance(), picture.rainOnGlass(), bit(picture.shadingAndLight()), bit(picture.reflections()), picture.reflectionScale(),
            bit(picture.fullSizeTextures()),
            bit(picture.surfaceDepth()), bit(picture.sharpShadows()), bit(picture.wholeFigureShadows()), bit(picture.frameBlending()),
            picture.antialiasing(), picture.lightDistance(), bit(picture.corpseModels())};
    }

    /** The picture the options of the rows of [Graphics] hold, in the order of GRAPHICS: Custom always draws what the mod made. */
    static Quality.Picture picture(double[] values) {
        return new Quality.Picture(false, (int) Math.rint(values[0]), (int) Math.rint(values[1]), (int) Math.rint(values[2]), (float) values[3],
                values[13] != 0, (int) Math.rint(values[4]), (int) Math.rint(values[5]), (int) Math.rint(values[6]), (int) Math.rint(values[7]),
                values[8] != 0, values[9] != 0, values[11] != 0, values[12] != 0, values[14] != 0, values[15] != 0, (int) Math.rint(values[17]),
                (float) values[10], values[18] != 0, (int) Math.rint(values[16]));
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
        if (loaded.status == CameraUnlock.LOAD_DEFERRED || loaded.status == CameraUnlock.LOAD_LEGACY_REFUSED
                || loaded.status == CameraUnlock.LOAD_UNREADABLE) {
            // Core saves nothing after such a load until a reload reads the file.
            trouble(FILE_NAME + " could not be read, so settings you change are not saved");
        }
        return loaded;
    }

    /** What the screen last said about the file, and how many times it has had something to say. */
    private static volatile String trouble;
    private static final AtomicInteger troubles = new AtomicInteger();

    /** The file let a change of the player's go unsaved: the screen says so, and the log says why. Any thread. */
    static void trouble(String what) {
        trouble = what + ". " + Log.FILE_NAME + " says why";
        troubles.incrementAndGet();
    }

    static int troubles() {
        return troubles.get();
    }

    static String troubleSaid() {
        return trouble;
    }

    /** What the rows read again while the game runs hold now. */
    Live live() {
        int rows = 0;
        for (int i = 0; i < own.length; i++) {
            rows |= (1 - core.configGetInt(own[i])) << i;
        }
        int changed = 0;
        for (int i = 0; i < rules.length; i++) {
            if (rules[i] != NO_ROW) {
                changed |= core.configGetInt(rules[i]) << i;
            }
        }
        double[] custom = new double[graphics.length];
        for (int part = 0; part < graphics.length; part++) {
            custom[part] = part == RENDER_SCALE || part == REFLECTION_SCALE ? core.configGetFloat(graphics[part]) : core.configGetInt(graphics[part]);
        }
        int[] qualities = new int[View.COUNT], overheadQualities = new int[View.COUNT], styles = new int[View.COUNT];
        int firstPersons = 0, inVehicles = 0;
        for (int player = 0; player < View.COUNT; player++) {
            qualities[player] = core.configGetInt(quality[player]);
            overheadQualities[player] = core.configGetInt(overheadQuality[player]) + Quality.LOW;
            styles[player] = core.configGetInt(style[player]);
            firstPersons |= core.configGetInt(firstPerson[player]) << player;
            inVehicles |= core.configGetInt(firstPersonInVehicles[player]) << player;
        }
        long partsOn = 0;
        for (Part part : Part.values()) {
            partsOn |= core.configGetInt(parts[part.ordinal()]) != 0 ? part.bit() : 0;
        }
        return new Live(core.configGetFloat(fieldOfView), rows, changed, qualities, overheadQualities, styles, core.configGetInt(spreadLightUpdates) != 0, firstPersons, inVehicles, core.configGetFloat(hillHeight), core.configGetInt(muzzleFlash) != 0, core.configGetInt(crosshair) != 0, core.configGetInt(regrip) != 0, core.configGetInt(splitScreen), picture(custom),
                core.configGetInt(talkingMouths) != 0, List.of(microphones()), partsOn);
    }

    /** Each player's microphone as the file names it now, player 1's first. */
    private String[] microphones() {
        String[] names = new String[microphones.length];
        for (int player = 0; player < names.length; player++) {
            names[player] = core.configGetText(microphones[player]);
        }
        return names;
    }

    boolean saveMicrophone(int player, String name) {
        return saved(MICROPHONE_ROWS[player] + " \"" + name + "\" from the game's options screen", core.configSaveText(microphones[player], name));
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

    /**
     * The settings the game's options screen shows, in core's order, which is the file's:
     * OptionsLayout sets the order the screen shows them in. Asked of core once, after the file is
     * read. Nothing changes them.
     */
    synchronized CameraUnlock.Option[] options() {
        if (options == null) {
            CameraUnlock.Option[] listed = new CameraUnlock.Option[core.configOptionCount()];
            for (int option = 0; option < listed.length; option++) {
                listed[option] = core.configOption(option);
                optionPlaces.put(listed[option].id, option);
            }
            options = listed;
        }
        return options;
    }

    /** An option's key. */
    String optionId(int option) {
        return options()[option].id;
    }

    /** An option's place in options(), by its key. */
    synchronized int optionPlace(String key) {
        options();
        Integer place = optionPlaces.get(key);
        if (place == null) {
            throw new IllegalArgumentException(key + " is no setting of the options screen");
        }
        return place;
    }

    /** What core holds for an option now, as one number: the last value the file gave it or a save put there. */
    double optionValue(int option) {
        return core.configOptionGet(option);
    }

    /**
     * Settings the game's options screen applied, each as its option's number by key, in as few
     * writes as core allows: the mod's own rows in one, each of core's rows in one of its own, and
     * the modes as the session has them. Answers whether the file holds every one.
     */
    boolean saveOptions(Map<String, Double> values) {
        boolean all = true;
        List<String> mine = new ArrayList<>();
        for (Map.Entry<String, Double> value : values.entrySet()) {
            String key = value.getKey();
            if (localRows.containsKey(key)) {
                mine.add(key);
            } else if (key.equals("TrackingMode")) {
                all &= saveTrackingMode();
            } else if (key.equals("AimMode")) {
                all &= saveAimMode();
            } else if (key.equals("WorldSpaceYaw")) {
                all &= saveWorldSpaceYaw(value.getValue() != 0);
            } else {
                all &= saved(key + " " + value.getValue() + " from the game's options screen", core.configOptionSave(optionPlace(key), value.getValue()));
            }
        }
        if (mine.isEmpty()) {
            return all;
        }
        int[] rows = new int[mine.size()];
        double[] numbers = new double[mine.size()];
        StringBuilder what = new StringBuilder();
        for (int i = 0; i < rows.length; i++) {
            String key = mine.get(i);
            rows[i] = localRows.get(key);
            numbers[i] = values.get(key);
            what.append(i == 0 ? "" : ", ").append(key).append(' ').append(numbers[i]);
        }
        return saved(what + " from the game's options screen", core.configSaveRows(rows, numbers)) && all;
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

    boolean saveQuality(int player, int row) {
        return saved("player " + (player + 1) + "'s first person graphics mode", core.configSaveInt(quality[player], row));
    }

    /** @param row a Quality mode, LOW or after */
    boolean saveIsometricQuality(int player, int row) {
        return saved("player " + (player + 1) + "'s isometric graphics mode", core.configSaveInt(overheadQuality[player], row - Quality.LOW));
    }

    boolean saveStyle(int player, Style row) {
        return saved("player " + (player + 1) + "'s picture style", core.configSaveInt(style[player], row.ordinal()));
    }

    /** The keys that switch first person, as the file names them. */
    String firstPersonKeys() {
        return core.configGetText(firstPersonKey);
    }

    String cycleQualityKeys() {
        return core.configGetText(cycleQualityKey);
    }

    String cycleStyleKeys() {
        return core.configGetText(cycleStyleKey);
    }

    String splitScreenKeys() {
        return core.configGetText(splitScreenKey);
    }

    /** The keys of core's own rows, as the load read them: core listens for these. */
    String cycleTrackingModeKeys() {
        return core.configGetConceptKeys("CycleTrackingModeKey");
    }

    String yawModeKeys() {
        return core.configGetConceptKeys("YawModeKey");
    }

    String aimModeKeys() {
        return core.configGetConceptKeys("TrueFreeLookKey");
    }

    boolean saveFirstPerson(int player, boolean on) {
        return saved("player " + (player + 1) + "'s view at load", core.configSaveInt(firstPerson[player], on ? 1 : 0));
    }

    boolean saveSplitScreen(int row) {
        return saved("split screen", core.configSaveInt(splitScreen, row));
    }

    private static boolean saved(String what, int status) {
        Log.info(FILE_NAME + ": " + what + " " + SAVE_STATUS[status]);
        if (status != CameraUnlock.SAVE_SAVED) {
            trouble(status == CameraUnlock.SAVE_UNCERTAIN ? "A setting you changed may not have been saved to " + FILE_NAME
                    : "A setting you changed was not saved to " + FILE_NAME);
        }
        return status == CameraUnlock.SAVE_SAVED;
    }
}
