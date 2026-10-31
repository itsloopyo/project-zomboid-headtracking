package com.cameraunlock.zomboid;

/**
 * The parts of the mod a row of CameraUnlock.ini switches off, each on until the player unticks
 * it. A part made of patches of the game's code is read as the game starts: off then, its patches
 * are left out of the game until a start with it on (Agent.sets), so another mod that patches the
 * same methods meets the game's own, and its row says so. A part the mod does by itself is read
 * again while the game runs. A part can be both, as FirstPersonEnabled is.
 *
 * The thread that watches the file and the game's options screen set them; the logic and render
 * threads ask them.
 */
enum Part {
    FIRST_PERSON("General", "FirstPersonEnabled", true, true,
            "true: the mod's first person view is there to switch to. false: saves load in the isometric view whatever FirstPerson\n"
                    + "says, and with FirstPersonInVehicles on, first person comes on as you get into a vehicle. With both off there is no\n"
                    + "first person view, the isometric view is the game's own whatever IsometricQuality says, and nothing more is made\n"
                    + "ready for first person, though what is ready already stays until you leave the save. The first person key, or a\n"
                    + "double tap of Back on a controller, then turns this on and switches to first person, and the graphics key turns it\n"
                    + "on to go to an isometric graphics mode other than Low."),
    TORCH_FOLLOWS_HEAD("FirstPerson", "TorchFollowsHead", true, false,
            "true: in first person, a flashlight you carry points where your head turns, as far as LightMultiplier says.\n"
                    + "false: it points the way your character faces, whatever your head does."),
    HEAR_FROM_THE_EYE("FirstPerson", "HearFromTheEye", false, true,
            "true: in first person you hear from your character's eyes: sounds turn with the view, walls between you and a sound\n"
                    + "muffle it, and rooms echo. false: you hear as the isometric view does, facing north-west on the map."),
    LIGHTNING_BOLTS("FirstPerson", "LightningBolts", false, true,
            "true: in first person, lightning strikes as a bolt from the clouds to the ground and lights up the world while it\n"
                    + "flashes. false: the game's own lightning, a flash that brightens the whole map for a moment."),
    SHOT_IMPACTS("FirstPerson", "ShotImpacts", true, true,
            "true: in first person, in Medium and High, a shot kicks up dust, chips, splinters, sparks or blood where it hits\n"
                    + "and leaves a hole. false: shots leave no mark, as in the game's own view. Shots still break lamps and glass\n"
                    + "and hole doors, walls and fences when ShotsBreakLamps, ShotsBreakGlass, BlastHoles and BulletHoles are on, and\n"
                    + "while any of them is on, the mod keeps changing the game's code for this."),
    FAR_VEHICLES_AND_ZOMBIES("FirstPerson", "FarVehiclesAndZombies", true, false,
            "true: in first person, vehicles, zombies and animals in sight are drawn even past the edge of the game's own\n"
                    + "screen, and so are other players where players cannot fight. false: each is drawn only where the game's own\n"
                    + "view would show it."),
    QUICK_RAGDOLLS("FirstPerson", "QuickRagdolls", false, true,
            "true: while anyone plays in first person, a body that is hit switches to its ragdoll at once, and a first person\n"
                    + "shot pushes it along the shot from where it went in. false: bodies switch to their ragdolls the way the\n"
                    + "game blends them in."),
    HIT_FLINCH("FirstPerson", "HitFlinch", true, false,
            "true: a zombie shot in first person that stays on its feet reacts where the bullet hit: its head snaps back or\n"
                    + "its shoulder spins round. false: it plays only the game's own stagger."),
    HEAD_LED_VIEW_WHEN_DOWN("FirstPerson", "HeadLedViewWhenDown", true, false,
            "true: in first person, when you are knocked over, lie down, get up or die, the view follows your character's\n"
                    + "head. false: the mouse keeps the view while the game moves the body, and when you die the view stays where it was."),
    ITEMS_AS_MODELS("FirstPerson", "ItemsAsModels", true, true,
            "true: in first person, items on the ground are shown as the game's own 3D models of them, while the game's 3D\n"
                    + "ground items option is on. false: each lies flat as its picture."),
    FINER_SKINS("FirstPerson", "FinerSkins", false, true,
            "true: in first person, people near you wear more detailed skins than the game's: the mod's own, and any finer\n"
                    + "ones in the folder the log names. false: they wear the game's own."),
    LEAVE_OUT_SCREEN_GREY("FirstPerson", "LeaveOutScreenGrey", false, true,
            "true: the game's grey tint for the season, fog and weather is left off the first person picture, which shows those\n"
                    + "with its own lighting. false: the game tints the first person picture as it tints its own view."),
    NAME_TAGS_AT_HEADS("FirstPerson", "NameTagsAtHeads", false, true,
            "true: on a server, in first person, the names and speech the game shows over people sit over their heads in the\n"
                    + "picture, and your own are hidden. false: the game shows them where its own view puts them."),
    HIGHLIGHTS("FirstPerson", "Highlights", true, false,
            "true: in first person, a line is drawn round what the Interact key would act on, and round what the game highlights\n"
                    + "or marks on the ground for you, such as the container the loot panel shows. false: none of them, for immersion.\n"
                    + "The piece of furniture you are placing is still outlined."),
    SHOTS_FROM_VEHICLE_SEATS("Rules", "ShotsFromVehicleSeats", true, false,
            "true: in first person, sitting in a vehicle with a gun, you can fire through any part of your seat's window, and\n"
                    + "the shot breaks it. false: as in the game, the window has to be opened or broken first. On a server where\n"
                    + "players can hurt each other, the game's rule applies."),
    DRIVE_WHILE_AIMING("Rules", "DriveWhileAiming", true, false,
            "true: in first person, driving with the keyboard, you can still steer, accelerate and brake while aiming a gun.\n"
                    + "false: as in the game, aiming a gun takes over the controls. With a controller, and on a server where\n"
                    + "players can hurt each other, the game's rule applies."),
    HILLS_MOVE_VEHICLES("Rules", "HillsMoveVehicles", true, true,
            "true: offline, driving in first person, hills pull a vehicle down them and can throw it into the air over a crest\n"
                    + "at speed. false: vehicles move as on the game's level ground. On a server they always do."),
    HAND_LIGHT_OUT_AT_THE_WHEEL("Rules", "HandLightOutAtTheWheel", true, false,
            "true: in first person, a light in your hands is switched off when you take the driver's seat, since your hands\n"
                    + "are on the wheel. false: it stays as it was."),
    BURNT_BUILDING_FREEZE_FIX("Fixes", "BurntBuildingFreezeFix", false, true,
            "true: stops the game freezing for good while it sets up a burnt-down building whose door or window reaches into a\n"
                    + "chunk that has not loaded yet. false: the game's own code, which can loop on that door forever."),
    CENTRE_THE_WINDOW("Fixes", "CentreTheWindow", false, true,
            "true: the game's window is centred in the part of the monitor the taskbar leaves free. false: the game centres it\n"
                    + "on the whole main monitor, which can leave part of it under the taskbar."),
    CONTROLLER_CHOICE_FIX("Fixes", "ControllerChoiceFix", false, true,
            "true: the controllers you tick in the game's options stay ticked, a 3Dconnexion 6-axis mouse is never used as a\n"
                    + "controller, and the main menu goes to the controller whose A button was pressed. false: the game picks its\n"
                    + "controllers, which with none ticked is the first one, a SpaceMouse if one was plugged in first."),
    CACHED_GAME_LOOKUPS("Optimizations", "CachedGameLookups", false, true,
            "true: the game's lookups of items by name, of component classes and of character state machines are answered from\n"
                    + "what they found before, and two of its bone updates skip work that changes nothing, in both views. The\n"
                    + "results are the same. false: the game works each one out every time."),
    SKIP_COVERED_GAME_DRAWING("Optimizations", "SkipCoveredGameDrawing", true, true,
            "true: while the first person picture covers the screen, the game skips drawing its own view underneath it: its\n"
                    + "weather, floors, objects, shadows, people and the rest. What you see is the same. false: the game draws its\n"
                    + "whole view under the picture every frame."),
    SKINS_COMPOSED_WHILE_WAITING("Optimizations", "SkinsComposedWhileWaiting", true, true,
            "true: while the first person picture covers the game's view, what characters wear is built into their skins while\n"
                    + "the render thread waits for the next frame. The skins look the same. false: the game builds them in the\n"
                    + "middle of drawing the frame."),
    CHUNK_CHANGE_COUNTS("Optimizations", "ChunkChangeCounts", false, true,
            "true: the game's changes to each chunk are counted as it makes them, so the first person view rebuilds only the\n"
                    + "chunks that changed. What you see is the same. false: the view checks every chunk it draws for changes."),
    UNSEEN_ROOM_ZOMBIES_UNMODELLED("Optimizations", "UnseenRoomZombiesUnmodelled", true, false,
            "true: in first person, a zombie in a room you could not see into, even with every nearby door and curtain open,\n"
                    + "has no model until you come close enough to see in. false: it keeps its model wherever it stands."),
    HOLD_CHUNK_MAP_CENTRE("Optimizations", "HoldChunkMapCentre", true, true,
            "true: the game's map of loaded chunks keeps its centre until you are a tile past a chunk edge, so turning on the\n"
                    + "spot right on an edge does not load a column of chunks again every few seconds. false: as in the game, the\n"
                    + "centre moves to your chunk at once."),
    GRAPHICS_MEMORY_STEPS("Optimizations", "GraphicsMemorySteps", true, false,
            "true: on an NVIDIA graphics card without enough memory for what a graphics mode draws, first person starts the\n"
                    + "session drawing less in every mode. false: every mode starts as it is. Checked when first person first\n"
                    + "starts in a session. Either way, when the card runs out of room for something the mod makes, every mode\n"
                    + "drops a step."),
    PREPARE_WHILE_LOADING("Optimizations", "PrepareWhileLoading", true, true,
            "true: a save starts only once the mod's shaders are compiled and the distant land and the skins around you are\n"
                    + "made, so first person does not stutter making them during play. false: the save starts as soon as the game\n"
                    + "is ready, and first person makes them as you play."),
    SPOT_BY_SIGHT("Optimizations", "SpotBySight", true, true,
            "true: under the first person picture, the rooms the game fills with zombies and the squares it counts as seen\n"
                    + "follow what the view sees, a few times a second. false: they follow where the mod has the game work out\n"
                    + "light, which can fill a room you have not looked into. Needs SkipCoveredGameDrawing."),
    BUILD_ON_WORKER_THREADS("Optimizations", "BuildOnWorkerThreads", true, false,
            "true: the first person view rebuilds chunks on threads of its own, all at once, and shows each one a frame\n"
                    + "later. false: each is rebuilt on the game's own thread as the view reaches it, and shows in that frame."),
    REBUILD_FOR_OWN_SPRITES("Optimizations", "RebuildForOwnSprites", true, false,
            "true: a chunk the first person view built before some of its sprites or pictures had loaded is rebuilt once\n"
                    + "those have. false: it is rebuilt whenever any sprite or picture loads, which for the first seconds after\n"
                    + "arriving somewhere is most frames."),
    HIDDEN_WALL_READS_KEPT("Optimizations", "HiddenWallReadsKept", true, false,
            "true: as chunks load, what HiddenWallDecor reads from a building for each room it decorates is kept, and only\n"
                    + "the chunks the game has changed since are read again. The rooms come out the same. Needs ChunkChangeCounts.\n"
                    + "false: the whole building is read again for each chunk that loads, which can hitch in a tall building."),
    BUILDING_FACTS_KEPT("Optimizations", "BuildingFactsKept", true, false,
            "true: what a building's room names say, what kind of place each room is, whether the building is a home and what\n"
                    + "each picture shows are worked out once and kept. The rooms and their pictures come out the same.\n"
                    + "false: they are worked out again for each room, which can hitch in a tall building.");

    final String section, key, comment;
    /** Whether the row is taken while the game runs, and whether it is read at the start to leave patches out. */
    final boolean live, start;
    private volatile boolean on = true, atStart = true, patched = true;
    /** FIRST_PERSON's alone: whether any player's FirstPersonInVehicles row is on, which needs first person whatever FirstPersonEnabled says. */
    private volatile boolean forVehicles;

    Part(String section, String key, boolean live, boolean start, String comment) {
        this.section = section;
        this.key = key;
        this.live = live;
        this.start = start;
        this.comment = start ? comment + "\n" + (live ? LIVE_AND_NEXT_START : NEXT_START) : comment;
    }

    /** The last line of the row of a part made of patches, saying when off is taken. */
    static final String NEXT_START = "Takes effect when the game next starts. When off, the mod does not change the game's own code for this.",
            LIVE_AND_NEXT_START = "Takes effect at once. From the next start, off also stops the mod changing the game's own code for this.";

    /** Whether the part is on now: as the start found it, for a part not taken while the game runs. */
    boolean on() {
        return on || forVehicles;
    }

    /** Whether the part's own row is on: for FIRST_PERSON, FirstPersonEnabled, leaving out FirstPersonInVehicles. */
    boolean row() {
        return on;
    }

    /** Whether any player's FirstPersonInVehicles row is on, which puts first person on whatever FirstPersonEnabled says. */
    static void firstPersonInVehicles(boolean any) {
        FIRST_PERSON.forVehicles = any;
    }

    /** Whether the part was on as the game started, which decides whether its patches are in the game's code. */
    boolean atStart() {
        return atStart;
    }

    /** Whether the part's patches are in the game's code: on as the game started, or switched on since, for a part taken while the game runs. */
    boolean patched() {
        return patched;
    }

    /** Whether the part works now: on, and for a part of patches, with its patches in. */
    boolean running() {
        return on() && (patched || !start);
    }

    /** Whether a row is a part's that is taken only when the game next starts. */
    static boolean nextStart(String key) {
        for (Part part : values()) {
            if (part.key.equals(key)) {
                return !part.live;
            }
        }
        return false;
    }

    /** Marks each part taken while the game runs that is on as in the game's code, once Agent.takeIn has put its patches in. */
    static void takenIn() {
        for (Part part : values()) {
            if (part.live && part.on()) {
                part.patched = true;
            }
        }
    }

    /** Each part's bit in what Config reads: the part's ordinal. */
    long bit() {
        return 1L << ordinal();
    }

    /** Every part as the file holds it at the start, by its bit, once firstPersonInVehicles has been told the file. */
    static void start(long bits) {
        for (Part part : values()) {
            part.on = (bits & part.bit()) != 0;
            part.atStart = part.patched = part.on();
        }
    }

    /** The parts taken while the game runs as the file holds them now, by their bits. The rest stay as the start found them. */
    static void take(long bits, long changed) {
        for (Part part : values()) {
            if (part.live && (changed & part.bit()) != 0) {
                part.on = (bits & part.bit()) != 0;
            }
        }
    }

    /** A part taken while the game runs, set from the options screen. */
    void set(boolean to) {
        if (!live) {
            throw new IllegalStateException(key + " is read only as the game starts");
        }
        on = to;
    }

    /** The parts that are off now, in words, for the log. */
    static String described() {
        StringBuilder off = new StringBuilder();
        for (Part part : values()) {
            if (!part.on || part.start && !part.patched) {
                off.append(off.length() == 0 ? "" : ", ").append(part.key)
                        .append(part.on ? " on from the next start" : part.on() ? " off, but on for FirstPersonInVehicles" : " off");
            }
        }
        return off.length() == 0 ? "every part of the mod is on" : "switched off: " + off;
    }
}
