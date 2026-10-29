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
            "true: the mod's first person view. false: there is none, and the isometric view is the game's own whatever\n"
                    + "IsometricQuality says. The first person key, a controller's double tap of Back and FirstPerson do nothing, and\n"
                    + "nothing more is made ready for first person while you play or while a save loads; what was already made stays\n"
                    + "until you leave the save."),
    TORCH_FOLLOWS_HEAD("FirstPerson", "TorchFollowsHead", true, false,
            "true: in first person a torch you carry lights where your head is turned, as far as LightMultiplier in [Light]\n"
                    + "says. false: it lights the way the view faces, whatever your head does."),
    HEAR_FROM_THE_EYE("FirstPerson", "HearFromTheEye", false, true,
            "true: in first person you hear from the eye: sounds turn with the view, walls between you and a sound muffle it\n"
                    + "and rooms ring. false: first person is heard as the game's own view hears, facing north-west on the map."),
    LIGHTNING_BOLTS("FirstPerson", "LightningBolts", false, true,
            "true: in first person lightning strikes as a bolt from the cloud to the ground, which lights the world while it\n"
                    + "is bright. false: lightning is the game's own flash, the whole map brightening for a moment."),
    SHOT_IMPACTS("FirstPerson", "ShotImpacts", true, true,
            "true: in first person, in Medium and High, a shot throws up dust, chips, splinters, sparks or blood where it\n"
                    + "strikes and leaves a hole. false: shots leave no mark, as in the game's own view. A shot still breaks lamps\n"
                    + "and glass as [Rules] says, and while either does the impacts' patches stay in."),
    FAR_VEHICLES_AND_ZOMBIES("FirstPerson", "FarVehiclesAndZombies", true, false,
            "true: in first person a vehicle, zombie or animal within sight is drawn wherever the game's own screen ends,\n"
                    + "and so is another player where players cannot fight. false: each is drawn only where the game's own view\n"
                    + "would show it."),
    QUICK_RAGDOLLS("FirstPerson", "QuickRagdolls", false, true,
            "true: while anyone plays in first person a body that is hit goes into its ragdoll at once, and a first person\n"
                    + "shot pushes it along the shot, at the place it went in. false: bodies go into their ragdolls as the game's own\n"
                    + "blends have them."),
    HIT_FLINCH("FirstPerson", "HitFlinch", true, false,
            "true: a zombie shot in first person that stays up is knocked by the round where it went in: a head snaps back, a\n"
                    + "shoulder is spun round. false: it plays the game's own stagger alone."),
    HEAD_LED_VIEW_WHEN_DOWN("FirstPerson", "HeadLedViewWhenDown", true, false,
            "true: in first person, knocked over, lying down, getting up and dying, the view is your character's head's.\n"
                    + "false: the view stays the mouse's while the game's animation has the body, and dead, it is held where it was."),
    ITEMS_AS_MODELS("FirstPerson", "ItemsAsModels", true, true,
            "true: in first person, while the game's 3D ground items option is on, items on the ground are built as the\n"
                    + "game's own models of them. false: each lies flat as its picture."),
    FINER_SKINS("FirstPerson", "FinerSkins", false, true,
            "true: in first person the people near the eye wear skins finer than the game's, the mod's own and any finer\n"
                    + "ones in the folder the log names. false: they wear the game's own."),
    LEAVE_OUT_SCREEN_GREY("FirstPerson", "LeaveOutScreenGrey", false, true,
            "true: the game's greying of the screen by the season, fog and weather is left off the first person picture,\n"
                    + "which shows those by its own light. false: the game greys first person's picture as it greys its own view."),
    NAME_TAGS_AT_HEADS("FirstPerson", "NameTagsAtHeads", false, true,
            "true: on a server, in first person, the names and speech the game writes over people are put over their heads\n"
                    + "in the picture, and your own are left out. false: the game writes them where its own view has their owners."),
    SHOTS_FROM_VEHICLE_SEATS("Rules", "ShotsFromVehicleSeats", true, false,
            "true: in first person, seated in a vehicle with a firearm, you can fire through the whole window of your seat,\n"
                    + "which the shot breaks. false: the window has to be opened or broken first, as the game has it. On a server\n"
                    + "where players can hurt each other the game's rule holds."),
    DRIVE_WHILE_AIMING("Rules", "DriveWhileAiming", true, false,
            "true: in first person, driving by keyboard, you keep steering, accelerating and braking with a firearm raised.\n"
                    + "false: raising a firearm takes the controls, as the game has it. On a controller, and on a server where players\n"
                    + "can hurt each other, the game's rule holds."),
    HILLS_MOVE_VEHICLES("Rules", "HillsMoveVehicles", true, true,
            "true: offline, driving in first person, the hills pull a vehicle down them and throw it into the air over a crest\n"
                    + "at speed. false: vehicles move as on the game's level ground. On a server they always do."),
    HAND_LIGHT_OUT_AT_THE_WHEEL("Rules", "HandLightOutAtTheWheel", true, false,
            "true: in first person, a light you carry in your hands is switched off as you take the driver's seat, since\n"
                    + "your hands are on the wheel. false: it stays as it was."),
    BURNT_BUILDING_FREEZE_FIX("Fixes", "BurntBuildingFreezeFix", false, true,
            "true: the game no longer stops for good as it lays out a burnt-down building whose door or window stands across\n"
                    + "into a chunk that is not loaded yet. false: the game's own code, which can go round that door for ever."),
    CENTRE_THE_WINDOW("Fixes", "CentreTheWindow", false, true,
            "true: the game's window is centred in the part of its monitor the taskbar leaves free. false: the game places\n"
                    + "it, centred by its inside against the whole of the main monitor, which can leave it partly under the taskbar."),
    CONTROLLER_CHOICE_FIX("Fixes", "ControllerChoiceFix", false, true,
            "true: the controllers you tick in the game's options stay ticked, a 3Dconnexion 6-axis mouse never plays, and\n"
                    + "the main menu goes to the controller whose A was pressed. false: the game picks its controllers, which with\n"
                    + "none ticked is the one at index 0, a SpaceMouse when one is plugged in first."),
    CACHED_GAME_LOOKUPS("Optimizations", "CachedGameLookups", false, true,
            "true: the game's lookups of items by name, of a component's class and of a character's state machine are\n"
                    + "answered from what they found before, and two of its bone updates skip work that changes nothing, in either\n"
                    + "view. The answers are the same. false: the game works each of them out every time."),
    SKIP_COVERED_GAME_DRAWING("Optimizations", "SkipCoveredGameDrawing", true, true,
            "true: under the first person picture the game leaves out drawing its own view, which the picture covers: its\n"
                    + "weather, floors, objects, shadows, people and the rest. The picture is the same. false: the game draws its whole\n"
                    + "view under the picture every frame, as it does with no picture over it.");

    final String section, key, comment;
    /** Whether the row is taken while the game runs, and whether it is read at the start to leave patches out. */
    final boolean live, start;
    private volatile boolean on = true, atStart = true;

    Part(String section, String key, boolean live, boolean start, String comment) {
        this.section = section;
        this.key = key;
        this.live = live;
        this.start = start;
        this.comment = start ? comment + "\n" + (live ? LIVE_AND_NEXT_START : NEXT_START) : comment;
    }

    /** The last line of the row of a part made of patches, saying when off is taken. */
    static final String NEXT_START = "Taken at the next start. Off, its patches of the game's code are left out.",
            LIVE_AND_NEXT_START = "Taken at once. Off, its patches of the game's code are left out from the next start.";

    /** Whether the part is on now: as the start found it, for a part not taken while the game runs. */
    boolean on() {
        return on;
    }

    /** Whether the part was on as the game started, which decides whether its patches are in the game's code. */
    boolean atStart() {
        return atStart;
    }

    /** Whether the part works now: on, and for a part of patches, on as the game started too, so its patches are in. */
    boolean running() {
        return on && (atStart || !start);
    }

    /** Each part's bit in what Config reads: the part's ordinal. */
    long bit() {
        return 1L << ordinal();
    }

    /** Every part as the file holds it at the start, by its bit. */
    static void start(long bits) {
        for (Part part : values()) {
            part.atStart = part.on = (bits & part.bit()) != 0;
        }
    }

    /** The parts taken while the game runs as the file holds them now, by their bits. The rest stay as the start found them. */
    static void take(long bits) {
        for (Part part : values()) {
            if (part.live) {
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
            if (!part.on || part.start && !part.atStart) {
                off.append(off.length() == 0 ? "" : ", ").append(part.key).append(part.on ? " on from the next start" : " off");
            }
        }
        return off.length() == 0 ? "every part of the mod is on" : "switched off: " + off;
    }
}
