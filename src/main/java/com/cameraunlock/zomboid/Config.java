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
     * first player's tapped, a second player's tapped twice and both players' held, and a view
     * is handed FIRST_PERSON for whichever of those is its own.
     */
    static final int CYCLE_TRACKING_MODE = CameraUnlock.HOTKEY_CYCLE_TRACKING_MODE,
            YAW_MODE = CameraUnlock.HOTKEY_YAW_MODE, AIM_MODE = CameraUnlock.HOTKEY_AIM_MODE,
            FIRST_PERSON = CameraUnlock.HOTKEY_LOCAL, CYCLE_QUALITY = CameraUnlock.HOTKEY_LOCAL << 1,
            CYCLE_STYLE = CameraUnlock.HOTKEY_LOCAL << 2, FIRST_PERSON_SECOND = CameraUnlock.HOTKEY_LOCAL << 3,
            SPLIT_SCREEN = CameraUnlock.HOTKEY_LOCAL << 4, FIRST_PERSON_BOTH = CameraUnlock.HOTKEY_LOCAL << 5;

    private static final int SECOND_PLAYER_PORT = 4243;

    private static final String[] LOAD_STATUS = {"read", "imported", "created", "deferred", "legacy refused", "unreadable"};
    private static final String[] SAVE_STATUS = {"saved", "not saved", "may not be saved"};

    /**
     * What the rows read again while the game runs hold: the field of view in degrees, Own's bits,
     * the Quality row, the Style row, the HillHeight row, the FrameBlending row, the MuzzleFlash row, the SplitScreen row and the rows of [Graphics].
     */
    record Live(float fieldOfView, int own, int quality, int style, float hillHeight, boolean frameBlending, boolean muzzleFlash, int splitScreen, Quality.Picture custom) {}

    /** The keys of [Graphics], the Custom graphics mode, in the order of Quality.Picture's parts. */
    static final String[] GRAPHICS = {"StockLook", "SpritePlants", "RenderScale", "SharpShadows", "ViewDistance", "ZombiesAndPropsDrawDistance",
        "ForwardDrivingDrawDistance", "RainOnGlass", "ShadingAndLight", "Reflections", "FullSizeTextures", "FarTrees", "SurfaceDepth"};
    private static final int RENDER_SCALE = 2;
    static final float MIN_RENDER_SCALE = 0.5f;
    static final int MIN_VIEW_DISTANCE = 32, MIN_DRAW_DISTANCE = 16;

    private final CameraUnlock core;
    private final int fieldOfView, quality, style, hillHeight, frameBlending, muzzleFlash, splitScreen, secondPlayerPort;
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
     * rows on the nav keys alone (see cameraunlock-core's per_game). FieldOfView, Quality, Style,
     * HillHeight, FrameBlending, MuzzleFlash, SplitScreen and the rows of [Content] are the mod's own, and the ones read
     * again while the game runs, and so are the rows of [Graphics], which are what the Custom
     * graphics mode draws. They are writable too, and a writable row that is not a key
     * list is a setting on the game's options screen (OptionsPage). SecondPlayerUdpPort, FirstPersonKey, CycleQualityKey,
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
        core.configConcept("EnableOnStartup", 0, "true: head tracking is on when the game starts. false: it is off. No key turns it on or off.", null);
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
        int perGame = CameraUnlock.ROW_PER_GAME;
        core.configConcept("CycleTrackingModeKey", perGame, null, "PageUp");
        core.configConcept("YawModeKey", perGame, null, "PageDown");
        core.configConcept("TrueFreeLookKey", perGame,
                "Cycles the aim mode: sights locked, free look with a marker, stock sights\n"
                        + "(TrueFreeLook, FreeLookMarker, StockSights).", "Insert");
        core.configConcept("LightMultiplier", 0, null, null);

        fieldOfView = core.configLocalFloat("General", "FieldOfView",
                "Field of view of the first person view in degrees, from the bottom of the picture to\n"
                        + "the top: 40 to 110. A wider screen shows more to the sides. Project Zomboid has no\n"
                        + "field of view setting of its own. Aiming a firearm still narrows the view by the\n"
                        + "same ratio. Read again while the game runs, so a change shows as soon as the file\n"
                        + "is saved.",
                writable | live, FieldOfView.DEFAULT_DEGREES, MIN_FIELD_OF_VIEW, MAX_FIELD_OF_VIEW);
        quality = core.configLocalEnum("General", "Quality",
                "The graphics mode: how much of the mod's own picture is drawn. CycleQualityKey goes\n"
                        + "from one to the next, Low, Medium, High, Custom, and saves it here. Low is as near\n"
                        + "the game's own look as there is: the game's own view is the game's own picture, and\n"
                        + "first person is drawn with the game's own sprites, models and textures in place of\n"
                        + "everything the mod made, at a little over three fifths of the pixels each way, sight\n"
                        + "to 48 tiles, smaller shadow maps, and no shading of corners, lit air, reflections or\n"
                        + "depth in surfaces. Medium and High draw both views with what the mod made. High: the\n"
                        + "whole picture. Medium: the game's own grass, trees, bushes and rocks whatever\n"
                        + "OwnPlants says, no shading of corners, lit air, reflections or depth in surfaces\n"
                        + "either, four fifths of the pixels each way, sight to 64 tiles and smaller shadow\n"
                        + "maps. Custom: the picture the rows of [Graphics] describe. A change to any of those\n"
                        + "rows on the game's options screen, or in this file while the game runs, goes to\n"
                        + "Custom. Automatic, which the key never goes to: begins at High and draws less, as\n"
                        + "Medium and then Low do but still with everything the mod made, each time the picture\n"
                        + "is drawn fewer than 45 times a second for three seconds, until the game is closed.\n"
                        + "Read again while the game runs.",
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
        // Not a row of [Graphics]: it changes what the land looks like and not what the picture costs, in every graphics mode alike.
        hillHeight = core.configLocalFloat("General", "HillHeight",
                "How high the hills stand in first person. The game's map is flat, and in first\n"
                        + "person the mod gives the land a lie of its own: the country rising and falling,\n"
                        + "hills, higher and more of them in farmland and woods than in town, swells that\n"
                        + "streets go over, and ground uneven underfoot. 1: as the mod makes it. 0: as level\n"
                        + "as the game's map. Up to " + HillHeight.MOST + ": that many times as high, where the\n"
                        + "steepest main road climbs about one in five and a half. A kerb, a bank down to water\n"
                        + "and how far water lies below its banks are the same at every height. The game's\n"
                        + "own view from overhead is always level, and on a\n"
                        + "server where players can fight one another the hills stay at 1. Read again\n"
                        + "while the game runs.",
                writable | live, HillHeight.DEFAULT, 0f, HillHeight.MOST);
        frameBlending = core.configLocalBool("General", "FrameBlending",
                "Whether each frame of the first person picture is mixed with the one before it.\n"
                        + "false: every frame is drawn on its own. true: fine edges, leaves and the grain of\n"
                        + "soft shadows are smoother and steadier, and the whole picture is a little softer,\n"
                        + "as much standing still as while you turn or walk. Read again while the game runs.",
                writable | live, false);
        muzzleFlash = core.configLocalBool("General", "MuzzleFlash",
                "true: a shot from a firearm in first person flashes at the muzzle: a small bloom\n"
                        + "of fire ahead of the barrel for a frame or two, and a moment's light on what is\n"
                        + "near. false: no flash and no light from it. Read again while the game runs.",
                writable | live, true);
        splitScreen = core.configLocalEnum("General", "SplitScreen",
                "How two players at one screen share it. SplitScreenKey goes from one to the other\n"
                        + "and saves it here. Vertical: side by side, each half the width and the whole\n"
                        + "height, as the game has it. Horizontal: player 1 above player 2, each the whole\n"
                        + "width and half the height. One player has the whole screen and three or four have\n"
                        + "a quarter each, as the game has it, whichever this says. Read again while the game\n"
                        + "runs.",
                writable | live, SplitScreen.words(), SplitScreen.VERTICAL);
        Quality.Picture high = Quality.HIGH_PICTURE;
        graphics = new int[] {
            core.configLocalBool("Graphics", "StockLook",
                    "The rows of this section are the Custom graphics mode: what is drawn while Quality is\n"
                            + "Custom. Until you change them they are High's. Low, Medium and High draw their own\n"
                            + "picture and leave these rows as they are. Changing any of them on the game's options\n"
                            + "screen, or here while the game runs, sets Quality to Custom. On the options screen\n"
                            + "they show the picture of the mode you are in, and what you apply there is kept here.\n"
                            + "Read again while the game runs.\n"
                            + "true: as near the game's own look as there is, as in Low: the game's own view is the\n"
                            + "game's own picture, and first person is drawn with the game's own sprites, models and\n"
                            + "textures in place of everything the mod made. false: both views are drawn with what\n"
                            + "the mod made, as the rows of [Content] say.",
                    writable | live, high.stockLook()),
            core.configLocalBool("Graphics", "SpritePlants",
                    "true: grass, trees, bushes and rocks are the game's own sprites whatever OwnPlants\n"
                            + "says, as in Medium. The mod's are the dearest thing in the picture to draw. false:\n"
                            + "as OwnPlants says.",
                    writable | live, high.spritePlants()),
            core.configLocalFloat("Graphics", "RenderScale",
                    "How many of the first person picture's pixels there are to each of the window's, each\n"
                            + "way: 0.5 to 1. Low draws 0.62, Medium 0.8 and High 1.",
                    writable | live, high.renderScale(), MIN_RENDER_SCALE, 1f),
            core.configLocalBool("Graphics", "SharpShadows",
                    "true: shadow maps 4096 texels across, as in High. false: 2048, as in Low and Medium,\n"
                            + "which take a quarter of the memory on the graphics card.",
                    writable | live, high.sharpShadows()),
            core.configLocalInt("Graphics", "ViewDistance",
                    "How far the first person view is built and drawn, in tiles, where the loaded map\n"
                            + "reaches that far: 32 to 80. It is the land, the buildings and the plants, and as far\n"
                            + "as anything is drawn. Low draws 48, Medium 64 and High 80.",
                    writable | live, high.viewDistance(), MIN_VIEW_DISTANCE, high.viewDistance()),
            core.configLocalInt("Graphics", "ZombiesAndPropsDrawDistance",
                    "How far off zombies, people, vehicles and the furniture, appliances and decor the mod\n"
                            + "has models of are drawn in first person, in tiles: " + MIN_DRAW_DISTANCE + " to " + high.viewDistance() + ". It never reaches\n"
                            + "past ViewDistance, which is how far the land and the buildings go: at ViewDistance or\n"
                            + "over it they are drawn as far as the view goes. Lower, what stands further off is\n"
                            + "left out, which costs less to draw. Low draws 48, Medium 64 and High 80, each its own\n"
                            + "view distance. On a server where players can hurt each other it leaves nothing out.",
                    writable | live, high.drawDistance(), MIN_DRAW_DISTANCE, high.viewDistance()),
            core.configLocalInt("Graphics", "ForwardDrivingDrawDistance",
                    "ZombiesAndPropsDrawDistance ahead of a vehicle you ride in: how far off on the side\n"
                            + "it faces zombies, people, vehicles and props are drawn, in tiles: " + MIN_DRAW_DISTANCE + " to " + high.viewDistance() + ". Never\n"
                            + "past ViewDistance. Behind and beside the vehicle, and on foot,\n"
                            + "ZombiesAndPropsDrawDistance is what counts. Low draws 48, Medium 64 and High 80. On a\n"
                            + "server where players can hurt each other it leaves nothing out.",
                    writable | live, high.drivingDrawDistance(), MIN_DRAW_DISTANCE, high.viewDistance()),
            core.configLocalEnum("Graphics", "RainOnGlass",
                    "How much of the rain on the windows of your vehicle is drawn. Low: drops of one size.\n"
                            + "Medium: specks among them, and trickles down the panes no wiper reaches. High: three\n"
                            + "sizes of drop, and drops that run down and leave a line of wet behind them.",
                    writable | live, "Low,Medium,High", high.rainOnGlass()),
            core.configLocalBool("Graphics", "ShadingAndLight",
                    "true: corners are shaded and lent the light of what stands near and the air is lit, as\n"
                            + "in High, and with Reflections true wet roads, polished floors, glass and water mirror\n"
                            + "the world. false: none of these, as in Low and Medium.",
                    writable | live, high.shadingAndLight()),
            core.configLocalBool("Graphics", "Reflections",
                    "true: the mirrors of a vehicle you sit in show the road behind, as in every mode, and\n"
                            + "with ShadingAndLight true wet roads, polished floors, glass and water mirror the\n"
                            + "world, as in High. false: none of that. A vehicle's mirrors are plain glass, and the\n"
                            + "road behind is not drawn for them, which costs nothing, and water shows the sky alone,\n"
                            + "as in Low and Medium.",
                    writable | live, high.reflections()),
            core.configLocalBool("Graphics", "FullSizeTextures",
                    "true: the mod's pictures of surfaces, on walls and of skin are made at their full size,\n"
                            + "as in High. false: at half their width and height, a quarter of what they take on\n"
                            + "the graphics card and in the PC's memory.",
                    writable | live, high.fullSizeTextures()),
            core.configLocalBool("Graphics", "FarTrees",
                    "true: every tree and bush is the mod's own as far as the view is built, as in High.\n"
                            + "false: one further off than " + (int) Crowns.REACH + " tiles is the game's sprite on a fan of cards,\n"
                            + "which costs less and changes to the mod's as you near it.",
                    writable | live, high.farTrees()),
            core.configLocalBool("Graphics", "SurfaceDepth",
                    "true: near surfaces are given the depth of what they show, as in High. false: they are\n"
                            + "drawn flat.",
                    writable | live, high.surfaceDepth()),
        };

        core.configLocalHotkeyTaps("FirstPersonKey",
                "Goes into the first person view and back to the game's own. Tapped, for the first\n"
                        + "player. Tapped twice, for a second player at the same screen. Held for " + CameraUnlock.HOLD_MS + " ms,\n"
                        + "for both.",
                0, "Delete", FIRST_PERSON, FIRST_PERSON_SECOND, FIRST_PERSON_BOTH);
        core.configLocalHotkey("CycleQualityKey", "Goes to the next graphics mode: Low, Medium, High, Custom (Quality).", 0, "F9",
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
                    writable | live, true),
            core.configLocalBool("Content", "OwnFurniture",
                    "true: the mod's models of furniture, appliances, street lamps, clocks and picture\n"
                            + "frames, banners hung as cloth, and bunting and flags laid on their wall. false:\n"
                            + "the game's sprites of them.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnPictures",
                    "true: the mod's photographs, paintings, posters and signs on walls, and programmes on a\n"
                            + "television that is on. false: the game's pictures, and only colour bars on a\n"
                            + "television.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnPlants",
                    "true: the mod's leaves, tree trunks, boulders, grass, ferns, flowers and fallen\n"
                            + "leaves. false: the game's sprites of trees, bushes and grass.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnTextures",
                    "true: the mod's textures of tarmac, gravel, earth, lawn, wood, boards, roofing,\n"
                            + "paint, carpet, cloth and wallpaper, and the lines painted on roads drawn as paint\n"
                            + "with clean edges. false: the colours of the game's sprites alone, and the lines\n"
                            + "as the game's sprites have them.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnBuildings",
                    "true: windows, doors, curtains and handrails built by the mod, the strings and posts\n"
                            + "of an open flight of stairs, and the outside of a wall finished as its building is.\n"
                            + "false: the game's sprites of them, on both sides of the wall.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnZombies",
                    "true: the mod's own skins for zombies near the eye, each with a lifelike face.\n"
                            + "false: the game's own painted skins.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnPeople",
                    "true: the mod's own skins for your character and the other living, in the game's\n"
                            + "five skin tones, each with a lifelike face. false: the game's own painted skins.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnArms",
                    "true: arms and hands of the mod's own on a held weapon, with fingers that close on\n"
                            + "it and go to its bolt, slide, magazine and loading port as it is worked and loaded,\n"
                            + "in the skin, sleeves and gloves your character has. false: the game's own arms.",
                    writable | live, true),
            core.configLocalBool("Content", "OwnClothes",
                    "true: what people and zombies wear is made finer near the eye: the game's picture\n"
                            + "of each garment and head of hair enlarged with its outline smoothed, and the weave,\n"
                            + "knit, grain or strands of what it is made of laid on from the mod's own photographs.\n"
                            + "false: the game's own pictures as they are.",
                    writable | live, true),
        };

        puts.put("FieldOfView", value -> {
            FieldOfView.set((float) value);
            Log.info("first person field of view set to " + (float) value + " degrees vertical");
        });
        puts.put("Quality", value -> Quality.set((int) value));
        puts.put("Style", value -> Style.set((int) value));
        puts.put("HillHeight", value -> {
            HillHeight.set((float) value);
            Log.info("hill height set to " + (float) value);
        });
        puts.put("FrameBlending", value -> FrameBlending.set(value != 0));
        puts.put("MuzzleFlash", value -> MuzzleFlash.set(value != 0));
        puts.put("SplitScreen", value -> SplitScreen.set((int) value));
        String[] ownKeys = {"OwnWeapons", "OwnFurniture", "OwnPictures", "OwnPlants", "OwnTextures", "OwnBuildings", "OwnZombies", "OwnPeople",
            "OwnArms", "OwnClothes"};
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
        return new double[] {bit(picture.stockLook()), bit(picture.spritePlants()), picture.renderScale(), bit(picture.sharpShadows()),
            picture.viewDistance(), picture.drawDistance(), picture.drivingDrawDistance(), picture.rainOnGlass(), bit(picture.shadingAndLight()),
            bit(picture.reflections()), bit(picture.fullSizeTextures()), bit(picture.farTrees()), bit(picture.surfaceDepth())};
    }

    /** The picture the options of the rows of [Graphics] hold, in the order of GRAPHICS. */
    static Quality.Picture picture(double[] values) {
        return new Quality.Picture(values[0] != 0, values[1] != 0, (float) values[2], values[3] != 0, (int) Math.rint(values[4]),
                (int) Math.rint(values[5]), (int) Math.rint(values[6]), (int) Math.rint(values[7]), values[8] != 0, values[9] != 0,
                values[10] != 0, values[11] != 0, values[12] != 0);
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
        return new Live(core.configGetFloat(fieldOfView), rows, core.configGetInt(quality), core.configGetInt(style),
                core.configGetFloat(hillHeight), core.configGetInt(frameBlending) != 0, core.configGetInt(muzzleFlash) != 0, core.configGetInt(splitScreen), picture(custom));
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
