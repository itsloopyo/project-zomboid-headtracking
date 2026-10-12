package com.cameraunlock.zomboid;

import zombie.characters.CharacterInputMode;
import zombie.characters.IsoPlayer;

/**
 * One player's first person view: everything that is that player's and not the world's. Where
 * the camera is and how the mouse or the stick has turned it, the head tracking, the weapon in
 * hand, the vehicle sat in, what the picture holds for that player's eye.
 *
 * There is one for the first player from the start. A player who joins the same screen gets
 * one the first time the game draws them, so a game with one player holds one view's worth of
 * everything. What is the same whoever looks is not here: the settings and the hotkeys
 * (Tracking), and what the picture is drawn with for every eye.
 */
final class View {
    /** How many players at one screen have a view: as many as the game lets share it. */
    static final int COUNT = 4;

    /** Made on the game's logic thread and asked for on the thread that draws as well. */
    private static final View[] views = new View[COUNT];

    static {
        views[0] = new View(0);
    }

    /** The player's number at this machine. */
    final int index;

    final Flight flight = new Flight();
    final FieldOfView fieldOfView = new FieldOfView();
    final Hearing hearing = new Hearing();
    final HeadFollowLight headFollowLight = new HeadFollowLight();
    final Headlights headlights = new Headlights();
    final TorchLess torchLess = new TorchLess();
    final Crosshair crosshair = new Crosshair();
    final Ride ride = new Ride();
    final KerbSteps kerbSteps = new KerbSteps();
    final GlassPull glassPull = new GlassPull();
    final MuzzleFlash muzzleFlash = new MuzzleFlash();
    final HeldDepth heldDepth = new HeldDepth();
    final HeadTracking headTracking;
    final FirstPerson firstPerson = new FirstPerson(this);
    final Controls controls = new Controls(this);
    final CarriedLight carriedLight = new CarriedLight(this);
    final CentreUse centreUse = new CentreUse(this);
    final NameTags nameTags = new NameTags(this);
    final ModelHead modelHead = new ModelHead(this);
    final FarModels farModels = new FarModels(this);
    final ViewModel viewModel = new ViewModel(this);
    final Scope scope = new Scope(this);
    final OwnShotgun ownShotgun = new OwnShotgun(this);
    final OwnRifle ownRifle = new OwnRifle(this);
    final WorldScene worldScene = new WorldScene(this);
    final Pipeline pipeline = new Pipeline(this);
    final Characters characters = new Characters(this);

    private View(int index) {
        this.index = index;
        headTracking = new HeadTracking(index, fieldOfView, headFollowLight);
    }

    /** The first player's view. */
    static View first() {
        return views[0];
    }

    /** The view of the player at a number, or null: the number is past COUNT, or no player there has been drawn yet. */
    static View of(int playerIndex) {
        return playerIndex >= 0 && playerIndex < COUNT ? views[playerIndex] : null;
    }

    /** The view of the player at a number, made the first time the game draws one there. Logic thread. */
    static View join(int playerIndex) {
        if (views[playerIndex] == null) {
            views[playerIndex] = new View(playerIndex);
            Log.info("player " + (playerIndex + 1) + " is at the screen and has a first person view of their own");
        }
        return views[playerIndex];
    }

    /** The view of a player of this machine, or null: see of(int). */
    static View of(IsoPlayer player) {
        for (int i = 0; i < COUNT; i++) {
            if (views[i] != null && IsoPlayer.players[i] == player) {
                return views[i];
            }
        }
        return null;
    }

    /** The player this view is for, or null while there is none at its number. */
    IsoPlayer player() {
        return IsoPlayer.players[index];
    }

    /**
     * Whether the mouse and the keyboard are this view's to hold and let go of. The first
     * player's are, whether they play with them or with a controller. A player who joined the
     * screen has a controller alone.
     */
    boolean hasMouse() {
        return index == 0;
    }

    /** Whether this view's player plays with the mouse and the keyboard now, and not a controller. */
    boolean onMouse() {
        IsoPlayer player = player();
        return hasMouse() && (player == null || !onController(player));
    }

    /** Whether a player's aim, attack and look are a controller's. */
    static boolean onController(IsoPlayer player) {
        return player.getInputMode() == CharacterInputMode.GAMEPAD;
    }
}
