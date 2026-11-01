package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import org.lwjglx.input.Keyboard;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.properties.PropertyContainer;
import zombie.entity.ComponentType;
import zombie.entity.components.spriteconfig.SpriteConfig;
import zombie.entity.components.ui.UiConfig;
import zombie.input.GameKeyboard;
import zombie.inventory.ItemContainer;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.areas.SafeHouse;
import zombie.iso.objects.IsoCurtain;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoLightSwitch;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWaveSignal;
import zombie.iso.objects.IsoWindow;
import zombie.iso.objects.IsoWorldInventoryObject;
import zombie.network.GameClient;
import zombie.ui.SpeedControls;
import zombie.ui.UIManager;
import zombie.util.list.PZArrayList;

/**
 * What the Interact key acts on in first person: the thing under the reticle.
 * The game's own view has the player click on a door, a window, a curtain, a
 * light switch, a container or something lying on the ground. Here there is no
 * cursor to click with, so the line from the eye through the reticle is traced
 * across the map to the first of those it meets, and that object is handed to the same
 * Lua the game runs for a click on it: a radio or a television's window, a workstation's,
 * the container, the half of a fridge the view is on. Whether the character is near enough,
 * and everything the action then does, is the game's.
 *
 * Logic thread only: it reads the map and calls the game's Lua.
 */
final class CentreUse {
    final View view;

    CentreUse(View view) {
        this.view = view;
    }

    /** What a press of the key came to. */
    enum Outcome {
        /** Nothing the game would take a click on is under the reticle: the key is the game's. */
        NOTHING,
        /** The game's click handler took the object. */
        USED,
        /** A container within the game's own reach: its contents are in the loot panel, which needs the pointer. */
        CONTAINER
    }

    /** How far along the view the key reaches, in tiles. The game's own handlers decide what is near enough to act on. */
    private static final float REACH = 2.5f;
    /** How far along a line through the pointer the world menu looks, in tiles: the game walks the player to what it is for. */
    private static final float MENU_REACH = WorldScene.REACH;
    /** How far from the eye to the middle of its square something standing may be for the key to open its menu (standingFor), in tiles: the key's reach and half a square's diagonal. */
    private static final float STANDING_REACH = REACH + 0.71f;
    /** How near the view's line has to pass to something lying on the ground or on a table. */
    private static final float ITEM_RADIUS = 0.3f;
    /** The view runs this far on past the floor it meets, so a thing lying where it lands is in reach of it. */
    private static final float PAST_THE_FLOOR = 0.1f;
    /**
     * How far short of its square's middle the front of a thing standing on a counter is, in tiles: the game's microwave
     * on a counter was met by a view that came down to the counter's top 0.41 into the square, and the top in front of it
     * at 0.32.
     */
    private static final float ON_TOP_FRONT = 0.13f;
    /**
     * How high over the player's floor, in tiles, the view may pass the middle of a square it goes on past and still be
     * on a radio or a television standing there: half a storey. Higher, it is looking over it at something further off,
     * or at a cabinet hung on the wall it meets.
     */
    private static final float LOW_THING = ViewCamera.LEVEL_HEIGHT / 2f;

    private static final String CLICK_HANDLER = "ISObjectClickHandler", WORLD_MENU = "ISWorldObjectContextMenu", INVENTORY_PAGE = "ISInventoryPage";
    private static final String PRESS = "doClickSpecificObject", RELEASE = "doClick", GRAB = "onGrabWItem", DOOR = "onOpenCloseDoor",
            CLIMB = "onClimbThroughWindow", GET_UP = "getUpAndThen", WINDOW = "onOpenCloseWindow";
    private static final String FREE_TILE = "AdjacentFreeTileFinder", BESIDE = "isTileOrAdjacent", FIND_FREE = "Find";
    private static final String ENTITY_UI = "ISEntityUI", CAN_OPEN = "CanOpenWindowFor";
    /** How far from a container, in squares each way, the game's click walks the character to it (ISObjectClickHandler.doClick). */
    private static final int WALK_REACH = 3;

    /** Whether the game's click would walk the character from walkFrom to the container walkTo, asked of its Lua once for each. */
    private IsoObject walkTo;
    private IsoGridSquare walkFrom;
    private boolean walks;
    /** Whether the game would open workstation's window for the character at openFrom, asked of its Lua once for each. */
    private IsoObject workstation;
    private IsoGridSquare openFrom;
    private boolean opens;

    /**
     * The key (Outlines.key) of what the key would act on this frame, for the picture to
     * outline, or Outlines.NONE when the view is on nothing the key acts on.
     */
    volatile long marked = Outlines.NONE;

    /** Notes what the key would act on with the view as it is (under), or that there is nothing. */
    void mark(IsoObject found) {
        marked = found == null ? Outlines.NONE : Outlines.key(found);
    }

    /** What the key would act on along the view, or null. */
    IsoObject under(ViewCamera.Pose view, float floorUp, IsoPlayer player, IsoCell cell) {
        return find(view, floorUp, cell, (int) Math.floor(player.getZ()), false, REACH);
    }

    /** What the game's Lua lacks for the key to work, or null. */
    static String gap() {
        String gap = gap(CLICK_HANDLER, PRESS);
        if (gap == null) {
            gap = gap(CLICK_HANDLER, RELEASE);
        }
        if (gap == null) {
            gap = gap(WORLD_MENU, GRAB);
        }
        if (gap == null) {
            gap = gap(WORLD_MENU, DOOR);
        }
        if (gap == null) {
            gap = gap(WORLD_MENU, CLIMB);
        }
        if (gap == null) {
            gap = gap(WORLD_MENU, WINDOW);
        }
        if (gap == null) {
            gap = gap(FREE_TILE, BESIDE);
        }
        if (gap == null) {
            gap = gap(FREE_TILE, FIND_FREE);
        }
        if (gap == null) {
            gap = gap(ENTITY_UI, CAN_OPEN);
        }
        return gap != null ? gap : gap(WORLD_MENU, GET_UP);
    }

    private static String gap(String table, String function) {
        return LuaManager.env.rawget(table) instanceof KahluaTable found && found.rawget(function) != null
                ? null : "the game's Lua has no " + table + "." + function;
    }

    /** How a press reaches the game. */
    enum Route {
        /** The world menu's Grab, for something lying on the ground. */
        GRAB,
        /** The world menu's Climb through, for a window the key is held on. */
        CLIMB,
        /** The world menu's Open or Close, for a window the character does not stand at. */
        WINDOW_MENU,
        /** The game's click on a door, a window, a curtain or a light switch, and for a door the world menu's Open or Close where the click is not taken. */
        CLICK,
        /**
         * The game's click on anything else: a radio's, a television's or a workstation's window
         * where the character is near enough, a walk to a container a few squares off, or
         * nothing, where its tests fail.
         */
        RELEASE,
        /** The game's click on a container within its reach, which puts it in the loot panel. */
        CONTAINER,
        /** A climb through a hole (HoleHop). */
        HOLE,
        /** The world menu for something standing within reach (standingFor), opened at the reticle. */
        MENU
    }

    /**
     * What a press of the key would do: the way it goes to the game, what it acts on, and what it
     * comes to as the label under the reticle names it, null where the game would take the press
     * and do nothing. The label names nothing a press does not do.
     *
     * @param container for a container, the one of its containers a press shows
     * @param hop       for a climb through a hole, where
     */
    record Use(Route route, IsoObject object, InteractWords.Does does, ItemContainer container, HoleHop.Hop hop) {
        static Use of(Route route, IsoObject object, InteractWords.Does does) {
            return new Use(route, object, does, null, null);
        }
    }

    /**
     * What a press would do with the reticle on found (under): what act then does.
     *
     * @param pointer whether the player has a pointer to work a container's panels with
     * @param climb   whether the key is held, which climbs through a window that can be climbed through instead of opening or shutting it
     * @return null where the key is left to the game
     */
    Use decide(IsoObject found, ViewCamera.Pose view, float floorUp, IsoPlayer player, boolean pointer, boolean climb) {
        if (found == null) {
            return null;
        }
        if (found instanceof IsoWorldInventoryObject) {
            return Use.of(Route.GRAB, found, InteractWords.Does.GRAB);
        }
        if (climb && climbable(found, player)) {
            return Use.of(Route.CLIMB, found, InteractWords.Does.CLIMB_THROUGH);
        }
        if (found instanceof IsoWindow pane && !pane.isSmashed() && !pane.isInvincible() && pane.getBarricadeForCharacter(player) == null
                && player.getCurrentSquare() != pane.getSquare() && player.getCurrentSquare() != pane.getOppositeSquare()) {
            return Use.of(Route.WINDOW_MENU, pane, pane.IsOpen() ? InteractWords.Does.CLOSE_WINDOW : InteractWords.Does.OPEN_WINDOW);
        }
        if (keyActsOn(found)) {
            return Use.of(Route.CLICK, found, clicked(found, player));
        }
        if (!pointer) {
            // The game's click on a container is the first player's, whoever asks, and its panels are a pointer's. A
            // controller's own key opens what its player stands at, so the key is left to the game.
            return null;
        }
        // The game's click (ISObjectClickHandler.doClick). Where its tests fail it takes the click and does nothing.
        IsoGridSquare from = player.getCurrentSquare();
        IsoGridSquare at = found instanceof IsoMovingObject body ? body.getCurrentSquare() : found.getSquare();
        boolean seen = at != null && at.isSeen(0);
        // A radio, a television or a workstation: the game's click opens its window, when the character is near enough.
        InteractWords.Does panel = seen ? panel(found, player, from) : null;
        boolean container = found.getContainer() != null;
        // The game's own test for a container near enough to open. One further off it may walk to.
        IsoGridSquare parent = found.getSquare();
        boolean inReach = parent != null && from != null && Math.abs(from.getX() - parent.getX()) <= 1 && Math.abs(from.getY() - parent.getY()) <= 1
                && from.getZ() == parent.getZ() && !from.isBlockedTo(parent);
        InteractWords.Does does = ClickRules.click(seen, panel, seen && GameClient.client && !SafeHouse.isSafehouseAllowLoot(at, player), container,
                found instanceof IsoThumpable thumpable && thumpable.isLockedToCharacter(player), inReach,
                seen && panel == null && container && !inReach && walks(found, from, player));
        if (does != InteractWords.Does.LOOT) {
            return new Use(Route.RELEASE, found, does, does == InteractWords.Does.WALK_TO ? found.getContainer() : null, null);
        }
        // The game's click opens a fridge's freezer for the upper half of its picture, measured in the picture the game
        // would have drawn, and the fridge for anything else, though the panel may keep the freezer it last showed. The
        // half the view is on is the one shown.
        ItemContainer shown = found.getContainerCount() >= 2
                ? found.getContainerByIndex(above(at, view, floorUp, 0f) > ViewCamera.LEVEL_HEIGHT / 2f ? 1 : 0) : found.getContainer();
        return new Use(Route.CONTAINER, found, InteractWords.Does.LOOT, shown, null);
    }

    /**
     * Whether the game's click on a container out of reach walks the character to it, by
     * ISObjectClickHandler.doClick's own tests: the player has the game's option to walk to a
     * container on, it is within three squares each way, the character is not already on its
     * square or beside it, and the game finds a free square beside it to walk to.
     */
    private boolean walks(IsoObject container, IsoGridSquare from, IsoPlayer player) {
        IsoGridSquare parent = container.getSquare();
        if (parent == null || from == null || !Core.getInstance().getOptionAutoWalkContainer()) {
            return false;
        }
        int acrossX = parent.getX() - from.getX(), acrossY = parent.getY() - from.getY();
        if (acrossX * acrossX + acrossY * acrossY > WALK_REACH * WALK_REACH) {
            return false;
        }
        if (container != walkTo || from != walkFrom) {
            walkTo = container;
            walkFrom = from;
            Object[] beside = call(FREE_TILE, BESIDE, from, parent);
            Object[] free = beside == null || Boolean.TRUE.equals(beside.length > 1 ? beside[1] : null) ? null : call(FREE_TILE, FIND_FREE, parent, player);
            walks = free != null && free.length > 1 && free[1] != null;
        }
        return walks;
    }

    /**
     * What the game's click on a door, a window, a curtain or a light switch does, by the game's
     * own tests in ISObjectClickHandler.doClickSpecificObject, or null where it takes the click and
     * does nothing. A door the click is not for is opened or shut through the world menu.
     */
    private static InteractWords.Does clicked(IsoObject found, IsoPlayer player) {
        if (found instanceof IsoDoor door) {
            IsoGridSquare from = player.getCurrentSquare(), square = door.getSquare();
            boolean taken = from != null && !(from.isWallTo(square) && !from.isDoorTo(square)) && square.getZ() == from.getZ() && door.isAdjacentToSquare(from);
            return ClickRules.door(door.IsOpen(), GameKeyboard.isKeyDown(Keyboard.KEY_LSHIFT), player.isWalking(), door.HasCurtains() != null,
                    from != null && from == door.getSheetSquare(), taken, door.isCurtainOpen());
        }
        if (found instanceof IsoThumpable door) {
            return door.IsOpen() ? InteractWords.Does.CLOSE_DOOR : InteractWords.Does.OPEN_DOOR;
        }
        if (found instanceof IsoCurtain curtain) {
            return curtain(curtain, player);
        }
        IsoGridSquare from = player.getCurrentSquare();
        if (found instanceof IsoLightSwitch light) {
            return from == null || light.getSquare().DistToProper(player) >= 2f || from.isWallTo(light.getSquare()) ? null
                    : light.isActivated() ? InteractWords.Does.TURN_OFF : InteractWords.Does.TURN_ON;
        }
        IsoWindow pane = (IsoWindow) found;
        boolean shift = GameKeyboard.isKeyDown(Keyboard.KEY_LSHIFT);
        IsoCurtain curtain = shift ? pane.HasCurtains() : null;
        return ClickRules.window(shift, curtain == null ? null : curtain(curtain, player), pane.isInvincible(), from == pane.getSquare() || from == pane.getOppositeSquare(),
                pane.getBarricadeForCharacter(player) != null, pane.isDestroyed(), pane.isBarricaded(), pane.IsOpen());
    }

    private static InteractWords.Does curtain(IsoCurtain curtain, IsoPlayer player) {
        return !curtain.canInteractWith(player) ? null : curtain.IsOpen() ? InteractWords.Does.CLOSE_CURTAINS : InteractWords.Does.OPEN_CURTAINS;
    }

    /**
     * The window the game's click opens for a radio, a television or a workstation, by
     * ISObjectClickHandler.doClick's own tests: a radio's or a television's within 1.5 tiles of
     * its middle, a workstation's within 2.45 of its main square's, with nothing between, outside
     * the tutorial, unpaused, the character alive and not aiming, and for a workstation no
     * dismantling under way and the game's ISEntityUI.CanOpenWindowFor. Null for none.
     */
    private InteractWords.Does panel(IsoObject found, IsoPlayer player, IsoGridSquare from) {
        if (found instanceof IsoWaveSignal && near(found, 1.5f * 1.5f, player, from)) {
            return InteractWords.Does.DEVICE_OPTIONS;
        }
        SpriteConfig config = found.getSpriteConfig();
        IsoObject master = config == null ? null : config.getMultiSquareMaster();
        return master != null && near(master, 1.5f * 4f, player, from) && !dismantling() && opensWindow(master, from, player) ? InteractWords.Does.OPEN_NAMED : null;
    }

    private static boolean near(IsoObject object, float within, IsoPlayer player, IsoGridSquare from) {
        IsoGridSquare square = object.getSquare();
        return !"Tutorial".equals(Core.getInstance().getGameMode()) && !paused() && player.isAlive() && !player.isAiming() && from != null && square != null
                && player.DistToSquared(square.getX() + 0.5f, square.getY() + 0.5f) < within && !from.isSomethingTo(square);
    }

    /** Whether the game is paused as its click handler reads it: its speed set to nothing. */
    private static boolean paused() {
        SpeedControls speed = UIManager.getSpeedControls();
        return speed != null && speed.getCurrentGameSpeed() == 0;
    }

    /** Whether the first player has the game's dismantling cursor up, which a click on a workstation is for. */
    private static boolean dismantling() {
        KahluaTable drag = IsoWorld.instance.currentCell.getDrag(0);
        return drag != null && "ISDestroyCursor".equals(drag.rawget("Type"));
    }

    private boolean opensWindow(IsoObject master, IsoGridSquare from, IsoPlayer player) {
        if (master != workstation || from != openFrom) {
            workstation = master;
            openFrom = from;
            Object[] answered = call(ENTITY_UI, CAN_OPEN, player, master);
            opens = answered != null && answered.length > 1 && Boolean.TRUE.equals(answered[1]);
        }
        return opens;
    }

    /**
     * Acts on what is under the reticle.
     *
     * @param view    the line from the eye through the reticle
     * @param floorUp the height of the floor the player stands on, in tiles above level 0
     * @param pointer whether the player has a pointer to work a container's panels with
     * @param climb   whether the key was held, which climbs through a window that can be climbed through instead of opening or shutting it
     */
    Outcome press(ViewCamera.Pose view, float floorUp, IsoPlayer player, IsoCell cell, boolean pointer, boolean climb) {
        Use use = decide(under(view, floorUp, player, cell), view, floorUp, player, pointer, climb);
        return use == null ? Outcome.NOTHING : act(use, player);
    }

    /** Does what decide said a press would. Not for a hole or the world menu, which FirstPerson opens. */
    Outcome act(Use use, IsoPlayer player) {
        IsoObject found = use.object();
        Double index = Double.valueOf(player.getIndex());
        switch (use.route()) {
            case GRAB -> {
                return call(WORLD_MENU, GRAB, null, found, index) == null ? Outcome.NOTHING : Outcome.USED;
            }
            case CLIMB -> {
                // The game's click shuts an open window, and climbs through a smashed one only from a square beside it, answering
                // that it took the click from further off. The key climbs through either from within its reach, where there is a
                // floor beyond to land on, the way the game's click climbs: up first, then over, walking to the window first.
                Object climbing = ((KahluaTable) LuaManager.env.rawget(WORLD_MENU)).rawget(CLIMB);
                return call(WORLD_MENU, GET_UP, player, climbing, null, (IsoWindow) found, index) == null ? Outcome.NOTHING : Outcome.USED;
            }
            case WINDOW_MENU -> {
                // The game's click on a window is for a character standing at it, and takes any other click without acting.
                // One over a counter or a dresser has no square in front of it to stand on, so the key is the world menu's
                // Open or Close, which walks the character to it first where the game can find a way.
                return call(WORLD_MENU, WINDOW, null, found, index) == null ? Outcome.NOTHING : Outcome.USED;
            }
            case CLICK -> {
                // False when the game will not have it: too far off, the wrong side of a wall, aiming.
                Object[] answered = call(CLICK_HANDLER, PRESS, found, index, player);
                if (answered != null && answered.length > 1 && Boolean.TRUE.equals(answered[1])) {
                    return Outcome.USED;
                }
                if (!isDoor(found)) {
                    return Outcome.NOTHING;
                }
                // The click is for a door beside the character and not for one aimed at. The key is for the door under the
                // reticle wherever it stands within reach, so it is the world menu's Open or Close: the character walks to the
                // door first where it is not beside it.
                return call(WORLD_MENU, DOOR, null, found, index) == null ? Outcome.NOTHING : Outcome.USED;
            }
            case RELEASE -> {
                return call(CLICK_HANDLER, RELEASE, found, Double.valueOf(0), Double.valueOf(0)) == null ? Outcome.NOTHING : Outcome.USED;
            }
            case CONTAINER -> {
                // Unfolded first: the click handler then puts the container in the loot panel.
                Panels.showInventory(player.getIndex(), true);
                if (call(CLICK_HANDLER, RELEASE, found, Double.valueOf(0), Double.valueOf(0)) != null && found.getContainerCount() >= 2) {
                    // The half the view is on is selected in the loot panel the click has just put up.
                    Object[] loot = call(null, "getPlayerLoot", Double.valueOf(0));
                    if (loot != null && loot.length > 1) {
                        call(INVENTORY_PAGE, "selectButtonForContainer", loot[1], use.container());
                    }
                }
                // The panels are up whether or not the click took, and the pointer is wanted for them.
                return Outcome.CONTAINER;
            }
            default -> throw new IllegalArgumentException("CentreUse does not act on " + use.route());
        }
    }

    /**
     * What the game's Lua answered, the call's own success first, or null where it raised an
     * error. That Lua is the game's and any mod's that wraps it, and this runs inside the
     * game's update of the player: the error is logged and the key is left to the game.
     */
    private Object[] call(String table, String function, Object... arguments) {
        Object callee = table == null ? LuaManager.env.rawget(function) : ((KahluaTable) LuaManager.env.rawget(table)).rawget(function);
        Object[] result = LuaManager.caller.pcall(UIManager.getDefaultThread(), callee, arguments);
        if (result.length == 0 || !Boolean.TRUE.equals(result[0])) {
            Log.info("the game's " + (table == null ? "" : table + ".") + function + " failed, and Interact is left to the game: " + Arrays.toString(result));
            return null;
        }
        return result;
    }

    /**
     * What the game's world menu is for along a line from the eye, or null. It is what the
     * key would act on, else the wall, the counter or the fridge the line stops at, else the
     * floor where the line comes down. The menu is for the whole square that is on, as the
     * game's own is for what its view has under the pointer.
     */
    IsoObject forMenu(ViewCamera.Pose line, float floorUp, IsoPlayer player, IsoCell cell) {
        return find(line, floorUp, cell, (int) Math.floor(player.getZ()), true, MENU_REACH);
    }

    /**
     * What the world menu is for where the key acts on nothing: something standing within the
     * key's reach, a sink, a table or a chair, whose menu (Drink, Wash, Sit) is what the game
     * offers for it. Null for anything else, which leaves the key to the game.
     */
    IsoObject standingFor(ViewCamera.Pose line, float floorUp, IsoPlayer player, IsoCell cell) {
        // The line enters a square at most half its diagonal nearer than the square's middle, so nothing past this is near enough.
        float across = (float) Math.hypot(line.forwardX, line.forwardY);
        float reach = across == 0f ? MENU_REACH : Math.min(MENU_REACH, (STANDING_REACH + 0.71f) / across);
        IsoObject found = find(line, floorUp, cell, (int) Math.floor(player.getZ()), true, reach);
        IsoGridSquare square = found == null ? null : found.getSquare();
        if (square == null || found == square.getFloor() || keyActsOn(found) || found.getType() == IsoObjectType.wall) {
            return null;
        }
        float toX = square.getX() + 0.5f - line.eyeX, toY = square.getY() + 0.5f - line.eyeY;
        return toX * toX + toY * toY <= STANDING_REACH * STANDING_REACH ? found : null;
    }

    /** Whether found (under) is a window the player can climb through, which a tap of the key opens or shuts and holding it climbs. */
    static boolean climbable(IsoObject found, IsoPlayer player) {
        return found instanceof IsoWindow pane && (pane.IsOpen() || pane.isSmashed()) && !player.isAiming() && pane.canClimbThrough(player)
                && landsOnAFloor(pane, player);
    }

    /** Whether the square on the other side of a window from the player has a floor, so climbing out is no fall. */
    private static boolean landsOnAFloor(IsoWindow window, IsoPlayer player) {
        IsoGridSquare square = window.getSquare();
        boolean playerBefore = window.getNorth() ? player.getY() < square.getY() : player.getX() < square.getX();
        IsoGridSquare far = playerBefore ? square : window.getOppositeSquare();
        return far != null && far.getFloor() != null;
    }

    /** Whether the key hands a thing to the game's click handler: a door, a window, a curtain, a light switch. */
    private static boolean keyActsOn(IsoObject object) {
        return isDoor(object) || object instanceof IsoWindow || object instanceof IsoCurtain || object instanceof IsoLightSwitch;
    }

    private static boolean isDoor(IsoObject object) {
        return object instanceof IsoDoor || object instanceof IsoThumpable thumpable && thumpable.isDoor();
    }

    /**
     * Whether the game's click acts on a thing that holds nothing: a radio or a television, or a
     * piece of a workstation, whose window the game opens for a click on any of its squares.
     */
    static boolean clickedThoughEmpty(IsoObject object) {
        if (object instanceof IsoWaveSignal) {
            return true;
        }
        SpriteConfig config = object.getSpriteConfig();
        IsoObject master = config == null ? null : config.getMultiSquareMaster();
        return master != null && master.hasComponent(ComponentType.UiConfig) && master.<UiConfig>getComponent(ComponentType.UiConfig).isUiEnabled();
    }

    /**
     * The first thing along the view that the game takes a click on, or null. The map is
     * squares with walls on their edges: a door, a window or a curtain is met where the view
     * crosses an edge, and everything else on the square it stands on.
     *
     * @param menu whether it is for the world menu, which is for anything the view meets
     * @param reach how far along the view to look, in tiles
     */
    private IsoObject find(ViewCamera.Pose view, float floorUp, IsoCell cell, int level, boolean menu, float reach) {
        float down = view.forwardUp < 0f ? (floorUp - view.eyeUp) / view.forwardUp : Float.POSITIVE_INFINITY;
        reach = Math.min(reach, down + PAST_THE_FLOOR);
        int tileX = (int) Math.floor(view.eyeX), tileY = (int) Math.floor(view.eyeY);
        int stepX = view.forwardX > 0f ? 1 : -1, stepY = view.forwardY > 0f ? 1 : -1;
        float nextX = view.forwardX == 0f ? Float.POSITIVE_INFINITY : ((view.forwardX > 0f ? tileX + 1 : tileX) - view.eyeX) / view.forwardX;
        float nextY = view.forwardY == 0f ? Float.POSITIVE_INFINITY : ((view.forwardY > 0f ? tileY + 1 : tileY) - view.eyeY) / view.forwardY;
        float perTileX = view.forwardX == 0f ? Float.POSITIVE_INFINITY : Math.abs(1f / view.forwardX);
        float perTileY = view.forwardY == 0f ? Float.POSITIVE_INFINITY : Math.abs(1f / view.forwardY);
        IsoGridSquare from = cell.getGridSquare(tileX, tileY, level);
        float entered = 0f;
        while (from != null) {
            IsoObject here = onSquare(from, view, reach, floorUp, false);
            if (here != null && !(here instanceof IsoWorldInventoryObject) && !(here instanceof IsoMovingObject)) {
                IsoObject beyond = overTheTop(from, view, floorUp, cell, level, entered, reach);
                here = beyond != null ? beyond : here;
            }
            if (here == null && menu) {
                here = lowOn(from, view, floorUp);
            }
            if (here != null) {
                return here;
            }
            boolean crossX = nextX <= nextY;
            float distance = crossX ? nextX : nextY;
            if (distance > reach) {
                return menu && down <= MENU_REACH ? floorAt(cell, view, down, level) : null;
            }
            int toX = crossX ? tileX + stepX : tileX, toY = crossX ? tileY : tileY + stepY;
            IsoGridSquare to = cell.getGridSquare(toX, toY, level);
            if (to == null) {
                return null;
            }
            float above = view.eyeUp + view.forwardUp * distance - floorUp;
            if (above >= 0f && above <= ViewCamera.LEVEL_HEIGHT) {
                // What stands on an edge belongs to the square it is the north or the west edge of.
                boolean ownerIsTo = crossX ? toX > tileX : toY > tileY;
                IsoObject edge = onEdge(ownerIsTo ? to : from, ownerIsTo ? from : to, !crossX);
                if (edge != null) {
                    return edge;
                }
                IsoObject hung = above > LOW_THING ? hungOn(from, crossX ? (stepX > 0 ? "W" : "E") : (stepY > 0 ? "N" : "S")) : null;
                if (hung != null) {
                    return hung;
                }
                if (menu && from.isBlockedTo(to)) {
                    IsoObject wall = switchOn(from);
                    return wall != null ? wall : (ownerIsTo ? to : from).getWall(!crossX);
                }
            }
            if (from.isBlockedTo(to)) {
                return switchOn(from);
            }
            if (to.isSolid() || to.isSolidTrans()) {
                IsoObject beyond = overTheTop(to, view, floorUp, cell, level, distance, reach);
                if (beyond != null) {
                    return beyond;
                }
                // A counter, a fridge, a shelf unit: the view stops on it, and it is what is looked at.
                IsoObject there = onSquare(to, view, Float.POSITIVE_INFINITY, floorUp, true);
                if (there == null && menu) {
                    there = standing(to);
                }
                return there != null ? there : switchOn(from);
            }
            from = to;
            entered = distance;
            tileX = toX;
            tileY = toY;
            if (crossX) {
                nextX += perTileX;
            } else {
                nextY += perTileY;
            }
        }
        return null;
    }

    /**
     * A door, a drawn curtain, a window or an open curtain on the edge between two squares, in
     * that order: a drawn curtain is what is seen of its window.
     *
     * @param owner the square the edge is the north or west edge of
     * @param other the square on the far side of it
     * @param north whether it is the owner's north edge
     */
    private IsoObject onEdge(IsoGridSquare owner, IsoGridSquare other, boolean north) {
        IsoObject window = null;
        IsoCurtain curtain = null;
        PZArrayList<IsoObject> objects = owner.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            if (object instanceof IsoDoor door && drawnNorth(door, door.north, door.IsOpen()) == north
                    || object instanceof IsoThumpable thumpable && thumpable.isDoor() && drawnNorth(thumpable, thumpable.north, thumpable.IsOpen()) == north) {
                return object;
            }
            if (object instanceof IsoWindow pane && pane.getNorth() == north) {
                window = object;
            } else if (object instanceof IsoCurtain hung && hung.getType() == (north ? IsoObjectType.curtainN : IsoObjectType.curtainW)) {
                curtain = hung;
            }
        }
        // A curtain on the far side of the edge hangs on that square's south or east.
        objects = other.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i) instanceof IsoCurtain hung && hung.getType() == (north ? IsoObjectType.curtainS : IsoObjectType.curtainE)) {
                curtain = hung;
            }
        }
        if (curtain != null && !curtain.IsOpen()) {
            return curtain;
        }
        return window != null ? window : curtain;
    }

    /**
     * A door, a window or a curtain on the edge the view leaves a square by, where the view crosses the whole square
     * over the top of what stands on it: the window over a counter or a dresser under it. Null where the view comes
     * down onto what stands there first, or there is nothing on that edge. A window on a room's south or east wall
     * belongs to the square outside, so it is met past the square in front of it, never on it.
     *
     * @param entered how far along the view it comes onto the square, in tiles
     */
    private IsoObject overTheTop(IsoGridSquare square, ViewCamera.Pose view, float floorUp, IsoCell cell, int level, float entered, float reach) {
        int x = square.getX(), y = square.getY();
        float outX = view.forwardX == 0f ? Float.POSITIVE_INFINITY : ((view.forwardX > 0f ? x + 1 : x) - view.eyeX) / view.forwardX;
        float outY = view.forwardY == 0f ? Float.POSITIVE_INFINITY : ((view.forwardY > 0f ? y + 1 : y) - view.eyeY) / view.forwardY;
        boolean crossX = outX <= outY;
        float left = crossX ? outX : outY;
        if (left > reach) {
            return null;
        }
        float top = topOf(square);
        float atEntry = view.eyeUp + view.forwardUp * entered - floorUp, atExit = view.eyeUp + view.forwardUp * left - floorUp;
        if (Math.min(atEntry, atExit) <= top || atExit > ViewCamera.LEVEL_HEIGHT) {
            return null;
        }
        int toX = crossX ? x + (view.forwardX > 0f ? 1 : -1) : x, toY = crossX ? y : y + (view.forwardY > 0f ? 1 : -1);
        IsoGridSquare to = cell.getGridSquare(toX, toY, level);
        if (to == null) {
            return null;
        }
        boolean ownerIsTo = crossX ? toX > x : toY > y;
        return onEdge(ownerIsTo ? to : square, ownerIsTo ? square : to, !crossX);
    }

    /**
     * How high over its floor, in tiles, what stands on a square reaches: the highest of their tops (Surface), or
     * no height at all where something there that stops the view or holds things has no top to put things on.
     */
    private static float topOf(IsoGridSquare square) {
        float top = 0f;
        boolean appliance = false;
        IsoObject floor = square.getFloor();
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            PropertyContainer properties = object.getProperties();
            if (object == floor || object.getType() == IsoObjectType.wall || keyActsOn(object) || object instanceof IsoWorldInventoryObject) {
                continue;
            }
            boolean stands = object.getContainer() != null || clickedThoughEmpty(object) || properties != null
                    && (properties.has(IsoFlagType.solid) || properties.has(IsoFlagType.solidtrans) || properties.has("IsTableTop") || hung(properties))
                    || object.getRenderYOffset() > 0f;
            if (!stands) {
                continue;
            }
            // A microwave, a lamp or a plant on a counter is taller than anything put down on it, and a sink let into a
            // counter tops out with the counter, at the game's own top for it.
            int surface = properties == null ? 0 : Surfaces.surface(properties);
            boolean onAnother = properties != null && properties.has("IsTableTop") || object.getRenderYOffset() > 0f;
            if (onAnother && !hung(properties) && (surface <= 0 || object.getContainer() != null || clickedThoughEmpty(object))) {
                appliance = true;
                continue;
            }
            if (surface <= 0 || hung(properties)) {
                return Float.POSITIVE_INFINITY;
            }
            float pixels = Surfaces.topNoTable(object);
            top = Math.max(top, pixels / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT);
        }
        return appliance ? top + APPLIANCE_TALL : top;
    }

    /** How far a microwave, a lamp or anything else standing on a counter reaches over it, in tiles: a window over it is seen above it. */
    private static final float APPLIANCE_TALL = 0.4f;

    /**
     * Whether a door is drawn on its square's north edge. Open on hinges it stands against the
     * square's other edge, where WorldScene draws it and the game's own view shows it; a garage
     * door rolls up in its doorway, and a sliding glass door slides in it.
     */
    private static boolean drawnNorth(IsoObject door, boolean north, boolean open) {
        PropertyContainer properties = door.getSprite().getProperties();
        return north != (open && !properties.has("GarageDoor") && !Doors.sliding(properties));
    }

    /**
     * What is on a square for the key: the thing lying nearest the view's line, else a body
     * or a piece of furniture that holds things or that the game's click opens a window for. A
     * microwave or a radio on a counter is what the view is on where it passes over the front of
     * the thing higher than the counter's top.
     *
     * @param floorUp   the height of the floor the player stands on, in tiles above level 0
     * @param stoppedOn whether the view stops on the square, a solid one, and does not go on past it
     */
    private IsoObject onSquare(IsoGridSquare square, ViewCamera.Pose view, float reach, float floorUp, boolean stoppedOn) {
        IsoObject nearest = null;
        float nearestOff = ITEM_RADIUS;
        ArrayList<IsoWorldInventoryObject> items = square.getWorldObjects();
        for (int i = 0; i < items.size(); i++) {
            IsoWorldInventoryObject item = items.get(i);
            float x = item.getWorldPosX(), y = item.getWorldPosY();
            float up = item.getWorldPosZ() * ViewCamera.LEVEL_HEIGHT + this.view.worldScene.terrain.lift(x, y, square.getZ());
            float toX = x - view.eyeX, toY = y - view.eyeY, toUp = up - view.eyeUp;
            float along = toX * view.forwardX + toY * view.forwardY + toUp * view.forwardUp;
            if (along <= 0f || along > reach) {
                continue;
            }
            float offX = toX - along * view.forwardX, offY = toY - along * view.forwardY, offUp = toUp - along * view.forwardUp;
            float off = (float) Math.sqrt(offX * offX + offY * offY + offUp * offUp);
            if (off < nearestOff) {
                nearestOff = off;
                nearest = item;
            }
        }
        if (nearest != null) {
            return nearest;
        }
        ArrayList<IsoMovingObject> bodies = square.getStaticMovingObjects();
        for (int i = 0; i < bodies.size(); i++) {
            if (bodies.get(i).getContainer() != null) {
                return bodies.get(i);
            }
        }
        IsoObject under = null, onTop = null;
        int top = 0;
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            PropertyContainer properties = object.getProperties();
            // A radio or a television that shares its square with a container stands on it: a counter, a cabinet.
            boolean standsOn = properties != null && properties.has("IsTableTop") || object.getRenderYOffset() > 0f || object instanceof IsoWaveSignal;
            if (!standsOn && properties != null) {
                top = Math.max(top, Surfaces.surface(properties));
            }
            if (object instanceof IsoWorldInventoryObject || object.getContainer() == null && (!clickedThoughEmpty(object)
                    || !stoppedOn && above(square, view, floorUp, 0f) > LOW_THING) || !stoppedOn && hung(properties)) {
                continue;
            }
            if (!standsOn && under == null) {
                under = object;
            } else if (standsOn && onTop == null) {
                onTop = object;
            }
        }
        if (under == null || onTop == null) {
            return under == null ? onTop : under;
        }
        return above(square, view, floorUp, ON_TOP_FRONT) > top / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT ? onTop : under;
    }

    /**
     * How high over the player's floor, in tiles, the view is where it passes a point short of the middle of a square by
     * so much, in tiles along the view.
     */
    private static float above(IsoGridSquare square, ViewCamera.Pose view, float floorUp, float shortOfMiddle) {
        float flat = (float) Math.sqrt(view.forwardX * view.forwardX + view.forwardY * view.forwardY);
        float along = flat == 0f ? 0f : ((square.getX() + 0.5f - view.eyeX) * view.forwardX + (square.getY() + 0.5f - view.eyeY) * view.forwardY) / (flat * flat)
                - shortOfMiddle / flat;
        return view.eyeUp + view.forwardUp * along - floorUp;
    }

    /** The floor where the view comes down to it, or null where there is no square or no floor there. */
    private static IsoObject floorAt(IsoCell cell, ViewCamera.Pose view, float down, int level) {
        IsoGridSquare square = cell.getGridSquare((int) Math.floor(view.eyeX + view.forwardX * down),
                (int) Math.floor(view.eyeY + view.forwardY * down), level);
        return square == null ? null : square.getFloor();
    }

    /**
     * Something standing on a square the view goes on over, which it passes lower than the top of: a sink on its
     * stand, a chair. Its top is its Surface, the height things are put down on it at. What hangs high on a wall is
     * met at its wall.
     */
    private static IsoObject lowOn(IsoGridSquare square, ViewCamera.Pose view, float floorUp) {
        float passes = above(square, view, floorUp, 0f);
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            PropertyContainer properties = object.getProperties();
            if (object != square.getFloor() && object.getType() != IsoObjectType.wall && properties != null && properties.has("CustomName") && !hung(properties)
                    && passes < Surfaces.surface(properties) / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT) {
                return object;
            }
        }
        return null;
    }

    /** Something standing on a square, which is not its floor: a sink, a counter, a stove. */
    private static IsoObject standing(IsoGridSquare square) {
        IsoObject floor = square.getFloor();
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i) != floor) {
                return objects.get(i);
            }
        }
        return floor;
    }

    /**
     * A cabinet or shelves hung high on a wall of the square, facing that way into it: met where the view
     * crosses that wall, not anywhere over the square, which reaches the one on the wall behind the player.
     */
    private static IsoObject hungOn(IsoGridSquare square, String facing) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            PropertyContainer properties = object.getProperties();
            if (object.getContainer() != null && hung(properties) && facing.equals(properties.get("Facing"))) {
                return object;
            }
        }
        return null;
    }

    private static boolean hung(PropertyContainer properties) {
        return properties != null && properties.has("IsHigh") && properties.get("Facing") != null;
    }

    private IsoObject switchOn(IsoGridSquare square) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i) instanceof IsoLightSwitch) {
                return objects.get(i);
            }
        }
        return null;
    }
}
