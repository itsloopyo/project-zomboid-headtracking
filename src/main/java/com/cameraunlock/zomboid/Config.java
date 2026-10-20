package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
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

    private static final String[] LOAD_STATUS = {"read", "imported", "created", "deferred", "legacy refused", "unreadable"};
    private static final String[] SAVE_STATUS = {"saved", "not saved", "may not be saved"};

    /**
     * What the rows read again while the game runs hold: the field of view in degrees, Own's bits, Rules' bits,
     * by player the Quality rows, the IsometricQuality rows as Quality modes and the Style rows,
     * the HillHeight row, the MuzzleFlash row, the SplitScreen row, the rows of [Graphics], the TalkingMouths row
     * and each player's microphone.
     */
    record Live(float fieldOfView, int own, int rules, int[] quality, int[] overheadQuality, int[] style, float hillHeight, boolean muzzleFlash, int splitScreen,
            Quality.Picture custom, boolean talkingMouths, List<String> microphones) {}

    /** The keys of [Graphics], the Custom graphics mode, in the order of Quality.Picture's parts. */
    static final String[] GRAPHICS = {"TreeQuality", "GrassDensity", "RenderScale", "SharpShadows", "ViewDistance", "ZombiesAndPropsDrawDistance",
        "FarTreeDistance", "RainOnGlass", "ShadingAndLight", "Reflections", "FullSizeTextures", "SurfaceDepth", "WholeFigureShadows",
        "FrameBlending"};
    private static final int RENDER_SCALE = 2;
    static final float MIN_RENDER_SCALE = 0.5f;
    static final int MIN_VIEW_DISTANCE = 32, MIN_DRAW_DISTANCE = 16;

    private final CameraUnlock core;
    private final int fieldOfView, hillHeight, muzzleFlash, splitScreen, talkingMouths;
    /** By player, the rows of their first person graphics mode, their isometric view's and their picture style. */
    private final int[] quality = new int[View.COUNT], overheadQuality = new int[View.COUNT], style = new int[View.COUNT];
    /** The rows of each player's microphone, in the order of MICROPHONE_ROWS. */
    private final int[] microphones = new int[MICROPHONE_ROWS.length];
    /** The rows of the ports of players 2, 3 and 4. */
    private final int[] playerPorts = new int[PLAYER_PORT_ROWS.length];
    /** The rows of [Content], in the order of Own's bits. */
    private final int[] own;
    /** The rows of [Rules], in the order of Rules' bits. */
    private final int[] rules;
    /** The rows of [Graphics], in the order of GRAPHICS. */
    private final int[] graphics;
    /** For each of the mod's own rows the game's options screen shows, by its key: what puts a value applied there on the running game. */
    private final Map<String, DoubleConsumer> puts = new HashMap<>();

    /**
     * Describes the rows. The tracking mode pair, WorldSpaceYaw and the three aim mode rows are
     * writable: their hotkeys save. There is no key that switches head tracking off: the
     * owner ruled the action out for this game, and the three keys there are are the game's own
     * rows on the nav keys alone (see cameraunlock-core's per_game). FieldOfView, Quality, IsometricQuality, Style,
     * the rows of [Players], HillHeight, MuzzleFlash, SplitScreen and the rows of [Content] and [Rules] are the mod's own, and the ones read
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
        quality[0] = core.configLocalEnum("General", "Quality",
                "First person graphics mode: Automatic, Low, Medium, High or Custom. Automatic draws less when the frame rate drops\n"
                        + "and more again when it recovers. Custom uses the rows of [Graphics].",
                writable | live, Quality.words(), Quality.AUTOMATIC);
        overheadQuality[0] = core.configLocalEnum("General", "IsometricQuality",
                "Graphics mode of the game's isometric view: Low (the game's own picture), Medium, High or Custom.",
                writable | live, Quality.overheadWords(), 0);
        style[0] = core.configLocalEnum("General", "Style",
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
        for (int player = 1; player < View.COUNT; player++) {
            String whose = "Player " + (player + 1) + "'s ";
            quality[player] = core.configLocalEnum("Players", qualityKey(player), whose + "first person graphics mode, as Quality.",
                    writable | live, Quality.words(), Quality.AUTOMATIC);
            overheadQuality[player] = core.configLocalEnum("Players", overheadQualityKey(player),
                    whose + "isometric graphics mode, as IsometricQuality.", writable | live, Quality.overheadWords(), 0);
            style[player] = core.configLocalEnum("Players", PLAYER_ROW[player] + "Style", whose + "picture style, as Style.", writable | live,
                    Style.words(), Style.BLEAK.ordinal());
        }
        Quality.Picture high = Quality.HIGH_PICTURE;
        graphics = new int[] {
            core.configLocalEnum("Graphics", "TreeQuality",
                    "Low: the game's plant sprites. Medium: the mod's trees, the game's sprites past " + (int) Crowns.REACH + " tiles.\n"
                            + "High: the mod's trees as far as you see.",
                    writable | live, Quality.TREE_QUALITIES, high.treeQuality()),
            core.configLocalInt("Graphics", "GrassDensity",
                    "How much of the grass the mod grows blade by blade is grown, as a percentage: " + Quality.LEAST_GRASS + " to " + Quality.ALL_GRASS + ".\n"
                            + "Lower, fewer and broader plants cover the same lawns and long grass, which costs less to\n"
                            + "build and draw. High grows all of it, Medium and Low " + Quality.SOME_GRASS + ". On a server where\n"
                            + "players can hurt each other it grows all of it.",
                    writable | live, high.grassDensity(), Quality.LEAST_GRASS, Quality.ALL_GRASS),
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
                    "true: mirrors in rooms show the room and you, your vehicle's mirrors show the road behind, and with\n"
                            + "ShadingAndLight, wet roads, floors, glass and water mirror the world.",
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
                "Switches between first person and the game's own view. Tap once for player 1, twice for player 2, three times for\n"
                        + "player 3, four times for player 4, hold for every player.",
                0, "Delete", FIRST_PERSON_TAPS, FIRST_PERSON_ALL);
        core.configLocalHotkeyTaps("CycleQualityKey",
                "Goes to a player's next graphics mode and sets both their views to it: Automatic, Low, Medium, High, Custom (Quality\n"
                        + "and IsometricQuality, and for players 2, 3 and 4 their rows of [Players]). The isometric view has no Automatic:\n"
                        + "it takes Low for it, and the key skips Automatic there. The game's options set the two views apart. Tap once for\n"
                        + "player 1, twice for player 2, three times for player 3, four times for player 4, hold for every player.",
                0, "F9", CYCLE_QUALITY_TAPS, CYCLE_QUALITY_ALL);
        core.configLocalHotkeyTaps("CycleStyleKey",
                "Goes to a player's next picture style: Vivid, Natural, Bleak (Style, and for players 2, 3 and 4 their rows of\n"
                        + "[Players]). Tap once for player 1, twice for player 2, three times for player 3, four times for player 4, hold\n"
                        + "for every player.",
                0, "F8", CYCLE_STYLE_TAPS, CYCLE_STYLE_ALL);
        core.configLocalHotkey("SplitScreenKey",
                "With two players at the screen, switches between side by side and one above the other.", 0, "F7",
                SPLIT_SCREEN);
        own = new int[] {
            core.configLocalBool("Content", "OwnWeapons",
                    "true: the mod's models of the pump shotgun and its sawn-off, the tactical pump shotgun, the hunting, varmint and\n"
                            + "tactical rifles, the lever action rifle and carbine, the self-loading rifle and carbine, the double barrelled shotgun\n"
                            + "and its sawn-off, the pistols, the revolvers, the toy cap pistol and rifle, the assault rifle, the battle rifle and\n"
                            + "rifle scopes. false: the game's own, with no picture through the scope.",
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
                    "true: windows, doors, curtains, handrails, chain link fences, open stairs and wall finishes built by the mod. false:\n"
                            + "the game's sprites.",
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
        rules = new int[] {
            core.configLocalBool("Rules", "ShotsThroughWalls",
                    "true: offline, your shots go on through stud walls, walls of boards, shut doors and windows, losing damage to\n"
                            + "each by what it is made of. false: they stop it, as the game has it. On a server the game's rule holds.",
                    writable | live, true),
            core.configLocalBool("Rules", "ShotsBreakLamps",
                    "true: offline, a shot breaks the lamp it passes through and the vehicle lamp it lands on. false: lamps\n"
                            + "are left lit, as the game has it. On a server the game's rule holds.",
                    writable | live, true),
            core.configLocalBool("Rules", "ShotsWoundLimbs",
                    "true: offline, first person shots to a zombie's legs slow it and drop it to crawl, shots to its arms leave them\n"
                            + "hanging, enough take the limb off, and a head shot that kills can take the head off or burst it. false: hits\n"
                            + "are the game's. On a server the game's rule holds.",
                    writable | live, true),
            core.configLocalBool("Rules", "VehiclesSmashThrough",
                    "true: offline, a vehicle faster than a walk knocks down fences and street lamp posts, drives through bushes\n"
                            + "unslowed and keeps its engine's full power off the road. false: the game's rules for all of it. On a server the\n"
                            + "game's rules hold.",
                    writable | live, true),
            core.configLocalBool("Rules", "FacesTheView",
                    "true: offline, walking or sneaking sideways or backwards in first person, your character keeps facing where you\n"
                            + "look, at the pace of its walk ahead. Jogging or sprinting it turns to the way it goes. false: it always turns,\n"
                            + "as the game has it. On a server the game's rule holds.",
                    writable | live, true),
        };

        talkingMouths = core.configLocalBool("Voice", TALKING_MOUTHS,
                "true: your character's mouth moves with what you say into your microphone: the jaw drops for \"ah\", the lips\n"
                        + "spread for \"ee\" and round for \"oo\". Online, other players' mouths move while the game's voice chat plays them.\n"
                        + "The sound is measured as it comes in, and nothing is recorded or sent.\n"
                        + "false: no microphone is opened.",
                writable | live, false);
        for (int player = 0; player < microphones.length; player++) {
            microphones[player] = core.configLocalText("Voice", MICROPHONE_ROWS[player],
                    (player == 0 ? "Your microphone" : "The microphone of player " + (player + 1) + " at the screen") + ", by the name Windows gives it, "
                            + Microphones.DEFAULT + " for Windows' own default, or " + Microphones.NONE + ".",
                    writable | live, player == 0 ? Microphones.DEFAULT : Microphones.NONE);
        }

        puts.put("FieldOfView", value -> {
            FieldOfView.set((float) value);
            Log.info("first person field of view set to " + (float) value + " degrees vertical");
        });
        for (int player = 0; player < View.COUNT; player++) {
            int whose = player;
            puts.put(qualityKey(player), value -> Quality.set(whose, (int) value));
            puts.put(overheadQualityKey(player), value -> Quality.setOverhead(whose, (int) value + Quality.LOW));
            puts.put(PLAYER_ROW[player] + "Style", value -> Style.set(whose, (int) value));
        }
        puts.put("HillHeight", value -> {
            HillHeight.set((float) value);
            Log.info("hill height set to " + (float) value);
        });
        puts.put("MuzzleFlash", value -> MuzzleFlash.set(value != 0));
        puts.put("SplitScreen", value -> SplitScreen.set((int) value));
        puts.put(TALKING_MOUTHS, value -> {
            Mouths.set(value != 0);
            Microphones.set(value != 0, Tracking.microphones().toArray(String[]::new));
            Log.info("talking mouths " + (value != 0 ? "on" : "off: no microphone is opened"));
        });
        String[] ownKeys = {"OwnWeapons", "OwnFurniture", "OwnPictures", "OwnPlants", "OwnTextures", "OwnBuildings", "OwnZombies", "OwnPeople",
            "OwnArms", "OwnClothes", "OwnAnimals", "OwnFire", "OwnCabins"};
        for (int i = 0; i < ownKeys.length; i++) {
            int bit = 1 << i;
            puts.put(ownKeys[i], value -> {
                Own.set(value != 0 ? Own.rows() | bit : Own.rows() & ~bit);
                Log.info("the mod's own in first person: " + Own.described(Own.rows()));
            });
        }
        String[] ruleKeys = {"ShotsThroughWalls", "ShotsBreakLamps", "ShotsWoundLimbs", "VehiclesSmashThrough", "FacesTheView"};
        for (int i = 0; i < ruleKeys.length; i++) {
            int bit = 1 << i;
            puts.put(ruleKeys[i], value -> {
                Rules.set(value != 0 ? Rules.rows() | bit : Rules.rows() & ~bit);
                Log.info("the mod's rules offline: " + Rules.described(Rules.rows()));
            });
        }
    }

    /** The key of a player's first person graphics mode: Quality, SecondPlayerQuality and so on. */
    static String qualityKey(int player) {
        return PLAYER_ROW[player] + "Quality";
    }

    /** The key of a player's isometric graphics mode: IsometricQuality, SecondPlayerIsometricQuality and so on. */
    static String overheadQualityKey(int player) {
        return PLAYER_ROW[player] + "IsometricQuality";
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
        return new double[] {picture.treeQuality(), picture.grassDensity(), picture.renderScale(), bit(picture.sharpShadows()), picture.viewDistance(),
            picture.drawDistance(), picture.farTreeDistance(), picture.rainOnGlass(), bit(picture.shadingAndLight()), bit(picture.reflections()),
            bit(picture.fullSizeTextures()), bit(picture.surfaceDepth()), bit(picture.wholeFigureShadows()), bit(picture.frameBlending())};
    }

    /** The picture the options of the rows of [Graphics] hold, in the order of GRAPHICS: Custom always draws what the mod made. */
    static Quality.Picture picture(double[] values) {
        return new Quality.Picture(false, (int) Math.rint(values[0]), (int) Math.rint(values[1]), (float) values[2], values[3] != 0,
                (int) Math.rint(values[4]), (int) Math.rint(values[5]), (int) Math.rint(values[6]), (int) Math.rint(values[7]), values[8] != 0,
                values[9] != 0, values[10] != 0, values[11] != 0, values[12] != 0, values[13] != 0);
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
        int changed = 0;
        for (int i = 0; i < rules.length; i++) {
            changed |= core.configGetInt(rules[i]) << i;
        }
        double[] custom = new double[graphics.length];
        for (int part = 0; part < graphics.length; part++) {
            custom[part] = part == RENDER_SCALE ? core.configGetFloat(graphics[part]) : core.configGetInt(graphics[part]);
        }
        int[] qualities = new int[View.COUNT], overheadQualities = new int[View.COUNT], styles = new int[View.COUNT];
        for (int player = 0; player < View.COUNT; player++) {
            qualities[player] = core.configGetInt(quality[player]);
            overheadQualities[player] = core.configGetInt(overheadQuality[player]) + Quality.LOW;
            styles[player] = core.configGetInt(style[player]);
        }
        return new Live(core.configGetFloat(fieldOfView), rows, changed, qualities, overheadQualities, styles, core.configGetFloat(hillHeight), core.configGetInt(muzzleFlash) != 0, core.configGetInt(splitScreen), picture(custom),
                core.configGetInt(talkingMouths) != 0, List.of(microphones()));
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

    boolean saveSplitScreen(int row) {
        return saved("split screen", core.configSaveInt(splitScreen, row));
    }

    private static boolean saved(String what, int status) {
        Log.info(FILE_NAME + ": " + what + " " + SAVE_STATUS[status]);
        return status == CameraUnlock.SAVE_SAVED;
    }
}
