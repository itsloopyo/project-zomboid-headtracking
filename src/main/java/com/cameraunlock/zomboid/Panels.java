package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.input.Mouse;
import zombie.inventory.ItemContainer;
import zombie.network.GameClient;
import zombie.ui.UIElement;
import zombie.ui.UIElementInterface;
import zombie.ui.UIManager;

/**
 * The game's panels, which are written in its Lua: which of them are open and
 * need a pointer, and showing and hiding the inventory.
 *
 * Everything runs on the game's logic thread, which is the one its Lua runs on.
 */
final class Panels {
    /** The Lua functions that hand back a player's inventory panel and loot panel. */
    private static final String LOOT_PANEL = "getPlayerLoot", INVENTORY_PAGE = "ISInventoryPage";
    private static final List<String> INVENTORY_PANELS = List.of("getPlayerInventory", LOOT_PANEL);
    private static final String MOUSE_DRAG = "ISMouseDrag";

    /**
     * The Lua classes whose panels are windows and dialogs. The inventory and
     * loot panels are neither: they sit on the screen folded all session, so
     * the inventory key answers for them. ISPanelJoypad is what the game makes
     * its plain panels of: the oven's and the microwave's settings, the
     * exercise panel, the map.
     */
    private static final List<String> WINDOW_CLASSES = List.of("ISCollapsableWindow", "ISModalDialog", "ISModalRichText", "ISTextBox", "ISPanelJoypad");

    /** Made of ISPanelJoypad too, and on the screen through play with nothing in them to point at. */
    private static final List<String> HEADS_UP_CLASSES = List.of("ISHotbar", "ISMiniMapOuter");

    /** The Lua function that hands back a player's panel of a driver's instruments. */
    private static final String DASHBOARD = "getPlayerVehicleDashboard";
    /** For each player, whether their dashboard panel is hidden by dashboard and not by the game. */
    private static final boolean[] dashboardHidden = new boolean[4];
    /**
     * The panel each player's dashboard was last hidden through, while it stays hidden. The game makes
     * it once a player is made (ISPlayerDataObject), so while it is not shown nothing needs asking of Lua.
     */
    private static final UIElementInterface[] dashboardKept = new UIElementInterface[4];

    private static final KahluaTable[] windowClasses = new KahluaTable[WINDOW_CLASSES.size()];
    private static final KahluaTable[] headsUpClasses = new KahluaTable[HEADS_UP_CLASSES.size()];

    private Panels() {}

    /** What the game's Lua lacks for the inventory key to work, or null. */
    static String gap() {
        for (String function : INVENTORY_PANELS) {
            if (LuaManager.env.rawget(function) == null) {
                return "the game's Lua has no " + function;
            }
        }
        return null;
    }

    /**
     * How many panels are open that need a pointer: the ones the game itself
     * marks as showing the cursor (its context and radial menus) or as taking
     * the keyboard (crafting, building, the map, vehicle mechanics), and its
     * windows and dialogs. A panel that is another player's at the same screen is not counted:
     * they work it with their controller, and it is no reason for this player's pointer to come out.
     */
    static int open(int playerIndex) {
        // Looked up every time: a class held from before the game reloaded its Lua would match nothing.
        for (int i = 0; i < windowClasses.length; i++) {
            windowClasses[i] = LuaManager.env.rawget(WINDOW_CLASSES.get(i)) instanceof KahluaTable table ? table : null;
        }
        for (int i = 0; i < headsUpClasses.length; i++) {
            headsUpClasses[i] = LuaManager.env.rawget(HEADS_UP_CLASSES.get(i)) instanceof KahluaTable table ? table : null;
        }
        int open = 0;
        List<UIElementInterface> elements = UIManager.getUI();
        for (int i = 0; i < elements.size(); i++) {
            UIElementInterface element = elements.get(i);
            if (element.isVisible() && !(element instanceof UIElement owned && isAnothers(owned.getTable(), playerIndex))
                    && (element.isForceCursorVisible() || element.isWantKeyEvents()
                    || element instanceof UIElement drawn && isWindow(drawn.getTable()))) {
                open++;
            }
        }
        return open;
    }

    /**
     * Hides the game's panel of a driver's instruments while the player is seated in first person,
     * where the vehicle's own dials, wheel and lamps show it all, and shows it again when the view
     * leaves first person in the seat. The game shows the panel as the player sits down and takes
     * it off the screen as they get out, so it is hidden again on any frame it is up.
     */
    static void dashboard(int playerIndex, boolean hidden) {
        if (!hidden && !dashboardHidden[playerIndex]) {
            return;
        }
        if (hidden && dashboardKept[playerIndex] != null && !dashboardKept[playerIndex].isVisible()) {
            return;
        }
        Object[] found = LuaManager.caller.pcall(UIManager.getDefaultThread(), LuaManager.env.rawget(DASHBOARD), Double.valueOf(playerIndex));
        if (found.length < 2 || !Boolean.TRUE.equals(found[0]) || !(found[1] instanceof KahluaTable panel)
                || !(panel.rawget("javaObject") instanceof UIElementInterface shown)) {
            if (!dashboardTroubleSaid[playerIndex]) {
                sayOnce(dashboardTroubleSaid, playerIndex, "the game's " + DASHBOARD + "(" + playerIndex + ") gave no panel to hide or show: " + Arrays.toString(found));
            }
            return;
        }
        dashboardTroubleSaid[playerIndex] = false;
        if (hidden && shown.isVisible()) {
            LuaManager.caller.pcallvoid(UIManager.getDefaultThread(), UIManager.tableget(panel, "setVisible"), panel, false);
            dashboardHidden[playerIndex] = true;
            dashboardKept[playerIndex] = shown;
            if (DevBuild.ON) {
                Log.info("the game's dashboard panel of player " + (playerIndex + 1) + " hidden: the vehicle's own dials show it in first person");
            }
        } else if (!hidden) {
            // Shown again only for a vehicle still sat in: out of one, the game has taken it off the screen itself.
            if (panel.rawget("vehicle") != null) {
                LuaManager.caller.pcallvoid(UIManager.getDefaultThread(), UIManager.tableget(panel, "setVisible"), panel, true);
            }
            dashboardHidden[playerIndex] = false;
            dashboardKept[playerIndex] = null;
            if (DevBuild.ON) {
                Log.info("the game's dashboard panel of player " + (playerIndex + 1) + " given back");
            }
        }
    }

    /** The fields the game's panels keep their player in: a number in the first two, the player itself in the rest. */
    private static final String[] OWNER_FIELDS = {"playerNum", "player", "character", "chr", "playerObj"};

    /** Whether a panel says it is a player's and that player is not the one asked about. One that says nothing is everyone's. */
    private static boolean isAnothers(KahluaTable panel, int playerIndex) {
        if (panel == null) {
            return false;
        }
        for (String field : OWNER_FIELDS) {
            Object owner = panel.rawget(field);
            if (owner instanceof Double number) {
                return number.intValue() != playerIndex;
            }
            if (owner instanceof IsoPlayer player) {
                return player.getIndex() != playerIndex;
            }
        }
        return false;
    }

    /** A Lua object's class is its metatable, and that class's parent is the class's own. */
    private static boolean isWindow(KahluaTable panel) {
        if (panel == null) {
            return false;
        }
        for (KahluaTable type = panel.getMetatable(); type != null; type = type.getMetatable()) {
            for (KahluaTable headsUp : headsUpClasses) {
                if (type == headsUp) {
                    return false;
                }
            }
            for (KahluaTable window : windowClasses) {
                if (type == window) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the game's menu is up over the game: the one the Escape key brings, with the
     * options and the key page in it. Alone the game is paused under it. On a server it is not.
     */
    static boolean menuOpen() {
        return LuaManager.env.rawget("MainScreen") instanceof KahluaTable screen && screen.rawget("instance") instanceof KahluaTable menu
                && menu.rawget("javaObject") instanceof UIElementInterface shown && shown.isVisible();
    }

    /**
     * Shows or hides the inventory and loot panels, through the calls the game's own inventory
     * key makes. Those are the game's Lua and any mod's that wraps it, and one of the callers is
     * inside the game's update of the player: a panel that is not handed over is logged and left
     * as it is.
     */
    static void showInventory(int playerIndex, boolean visible) {
        for (String function : INVENTORY_PANELS) {
            KahluaTable panel = inventoryPanel(function, playerIndex);
            if (panel != null) {
                LuaManager.caller.pcallvoid(UIManager.getDefaultThread(), UIManager.tableget(panel, "setVisible"), panel, visible);
            }
        }
    }

    /**
     * A player's inventory or loot panel, by the Lua function that hands it back, or null. That
     * it gave none is logged once until it gives one again: the loot panel is asked after
     * several times a second while it follows the look.
     */
    private static KahluaTable inventoryPanel(String function, int playerIndex) {
        int which = INVENTORY_PANELS.indexOf(function);
        Object[] found = LuaManager.caller.pcall(UIManager.getDefaultThread(), LuaManager.env.rawget(function), Double.valueOf(playerIndex));
        if (found.length < 2 || !Boolean.TRUE.equals(found[0]) || !(found[1] instanceof KahluaTable panel)) {
            if (!panelTroubleSaid[playerIndex][which]) {
                panelTroubleSaid[playerIndex][which] = true;
                Log.info("the game's " + function + "(" + playerIndex + ") gave no panel, which is left as it is: " + Arrays.toString(found));
            }
            return null;
        }
        panelTroubleSaid[playerIndex][which] = false;
        return panel;
    }

    private static final boolean[][] panelTroubleSaid = new boolean[View.COUNT][INVENTORY_PANELS.size()];

    /**
     * Shows a player's inventory and loot panels unfolded (InventoryStaysOpen), and says whether
     * the game gave both. The game folds a panel to its title bar a moment after the pointer
     * leaves it, on a click outside it and while aiming, and unfolds it as the pointer comes
     * over its title bar (ISInventoryPage.onMouseMove). While the look has the mouse the game's
     * panels are told nothing of it (Hooks.uiBlind), so nothing folds them then, and they are
     * unfolded here the way the pointer would. The player's own pin is never touched.
     */
    static boolean showUnfolded(int playerIndex) {
        boolean both = true;
        for (String function : INVENTORY_PANELS) {
            KahluaTable panel = inventoryPanel(function, playerIndex);
            if (panel == null) {
                both = false;
                continue;
            }
            LuaManager.caller.pcallvoid(UIManager.getDefaultThread(), UIManager.tableget(panel, "setVisible"), panel, true);
            if (Boolean.TRUE.equals(panel.rawget("isCollapsed"))) {
                panel.rawset("isCollapsed", Boolean.FALSE);
                panel.rawset("collapseCounter", Double.valueOf(0));
                // The call ISInventoryPage.onMouseMove makes as it unfolds one on a server. In this build of the game it returns without doing anything.
                if (GameClient.client && !Boolean.TRUE.equals(panel.rawget("onCharacter")) && panel.rawget("inventoryPane") instanceof KahluaTable pane
                        && pane.rawget("inventory") instanceof ItemContainer shown) {
                    shown.requestSync();
                }
                callOn(panel, "clearMaxDrawHeight", function, playerIndex);
            }
        }
        return both;
    }

    /** Folds a player's inventory and loot panels to their title bars as the game does while aiming, where they are not pinned. */
    static void fold(int playerIndex) {
        for (String function : INVENTORY_PANELS) {
            KahluaTable panel = inventoryPanel(function, playerIndex);
            if (panel != null && !Boolean.TRUE.equals(panel.rawget("pin"))) {
                callOn(panel, "collapseNow", function, playerIndex);
            }
        }
    }

    /** Calls a method of a player's panel that takes nothing, and logs where the game's Lua raised an error in it. */
    private static void callOn(KahluaTable panel, String method, String function, int playerIndex) {
        Object[] done = LuaManager.caller.pcall(UIManager.getDefaultThread(), UIManager.tableget(panel, method), panel);
        if (done.length == 0 || !Boolean.TRUE.equals(done[0])) {
            Log.info("the game's " + method + " on " + function + "(" + playerIndex + ") failed: " + Arrays.toString(done));
        }
    }

    private static ItemContainer shown(KahluaTable loot) {
        return loot.rawget("inventoryPane") instanceof KahluaTable pane && pane.rawget("inventory") instanceof ItemContainer shown ? shown : null;
    }

    /**
     * Whether the game has a container among the buttons of a loot panel: the containers round
     * the character it can reach, as its refreshBackpacks last found them.
     */
    private static boolean listed(KahluaTable loot, ItemContainer container) {
        if (!(loot.rawget("backpacks") instanceof KahluaTable buttons)) {
            return false;
        }
        for (int i = 1; i <= buttons.len(); i++) {
            if (buttons.rawget(i) instanceof KahluaTable button && button.rawget("inventory") == container) {
                return true;
            }
        }
        return false;
    }

    /** The kinds of trouble with the loot panel, each logged once per kind for each player. */
    private enum Trouble { PANEL, SELECT, FLOOR, REFRESH, TAKE_ALL, EXPLORE, CLOSE_SOUND }

    /** Whether each kind of trouble has been logged for each player: the loot panel is asked after several times a second. */
    private static final boolean[][] troubleSaid = new boolean[View.COUNT][Trouble.values().length];

    private static void sayOnce(Trouble trouble, int playerIndex, String words) {
        if (!troubleSaid[playerIndex][trouble.ordinal()]) {
            troubleSaid[playerIndex][trouble.ordinal()] = true;
            Log.info(words);
        }
    }

    /**
     * Has a player's loot panel show a container, through the call its own buttons make, where
     * the game has put it among the panel's buttons. Says whether the panel now shows it.
     */
    static boolean showInLoot(int playerIndex, ItemContainer container) {
        KahluaTable loot = inventoryPanel(LOOT_PANEL, playerIndex);
        if (loot == null) {
            return false;
        }
        if (shown(loot) == container) {
            return true;
        }
        if (!listed(loot, container) || !callPage(Trouble.SELECT, playerIndex, "selectButtonForContainer", loot, container)) {
            return false;
        }
        return shown(loot) == container;
    }

    /**
     * The game's Take All on a container, as its loot panel's button does it (ISInventoryPage.lootAll):
     * the container is shown in the loot panel, made up to date first where the panel was not on the
     * screen, and the game then walks the character to it where it has to and queues its own timed
     * transfers, by its own weight rules. A panel that was not on the screen is taken off it again.
     * Says whether the game was asked.
     */
    static boolean takeAll(int playerIndex, ItemContainer container) {
        KahluaTable loot = inventoryPanel(LOOT_PANEL, playerIndex);
        if (loot == null) {
            return false;
        }
        if (!(loot.rawget("javaObject") instanceof UIElementInterface element)) {
            sayOnce(Trouble.PANEL, playerIndex, "the game's loot panel of player " + (playerIndex + 1) + " has no javaObject, so Take All was not asked of it");
            return false;
        }
        boolean wasShown = element.isVisible();
        Object soundedBefore = loot.rawget("selectedContainerForSound");
        try {
            if (!wasShown) {
                LuaManager.caller.pcallvoid(UIManager.getDefaultThread(), UIManager.tableget(loot, "setVisible"), loot, true);
            }
            // refreshBackpacks lists the containers round the character, and fills one never looked in (checkExplored).
            if ((!wasShown || !listed(loot, container) || !container.isExplored()) && !callPage(Trouble.REFRESH, playerIndex, "refreshBackpacks", loot)) {
                return false;
            }
            if (!showInLoot(playerIndex, container)) {
                Log.info("Take All of player " + (playerIndex + 1) + " was not asked of the game: its loot panel does not list the " + container.getType());
                return false;
            }
            return callPage(Trouble.TAKE_ALL, playerIndex, "lootAll", loot);
        } finally {
            if (!wasShown) {
                LuaManager.caller.pcallvoid(UIManager.getDefaultThread(), UIManager.tableget(loot, "setVisible"), loot, false);
                // Where showing it played the container's opening sound (playContainerOpenCloseSounds noted the container), it is
                // closed as the panel's close() closes one: its closing sound, and the note let go of where that played.
                if (soundedBefore != container && loot.rawget("selectedContainerForSound") == container) {
                    Object close = inventoryPage("playContainerCloseSound");
                    Object[] played = close == null ? new Object[0] : LuaManager.caller.pcall(UIManager.getDefaultThread(), close, loot, container);
                    if (played.length == 0 || !Boolean.TRUE.equals(played[0])) {
                        sayOnce(Trouble.CLOSE_SOUND, playerIndex, "the game's ISInventoryPage.playContainerCloseSound failed for player " + (playerIndex + 1) + ": " + Arrays.toString(played));
                    } else if (played.length > 1 && Boolean.TRUE.equals(played[1])) {
                        loot.rawset("selectedContainerForSound", null);
                    }
                }
            }
        }
    }

    /**
     * Ends whatever the mouse was doing in the game's panels as the look takes it: a drag of items
     * is put down where it started, and every panel is told the pointer left it and, for each
     * button still held, that it was let go outside it, which stops a window being moved or
     * resized and lets go of the capture (ISInventoryPage.onMouseUpOutside). While the look holds
     * the mouse the panels are told nothing of it (Hooks.uiBlind), so the release itself would
     * never reach them.
     */
    static void letGoOfMouse() {
        if (LuaManager.env.rawget(MOUSE_DRAG) instanceof KahluaTable drag && drag.rawget("dragging") != null) {
            if (drag.rawget("draggingFocus") instanceof KahluaTable pane) {
                pane.rawset("dragging", null);
                if (pane.rawget("draggedItems") instanceof KahluaTable dragged) {
                    callOn(dragged, "reset", MOUSE_DRAG + ".draggingFocus.draggedItems", 0);
                }
            }
            drag.rawset("dragging", null);
            drag.rawset("draggingFocus", null);
            Log.info("a drag of items in the game's panels was put down as the look took the mouse");
        }
        boolean left = Mouse.isButtonDown(0), right = Mouse.isButtonDown(1);
        for (UIElementInterface element : new ArrayList<>(UIManager.getUI())) {
            if (!element.isVisible()) {
                continue;
            }
            element.onExtendMouseMoveOutside(0, 0, -1, -1);
            if (left) {
                element.onMouseButtonUpOutside(0, -1, -1);
            }
            if (right) {
                element.onMouseButtonUpOutside(1, -1, -1);
            }
        }
    }

    /**
     * Has the game look in a container never looked in before, as its loot panel does for each
     * container it lists round the character (ISInventoryPage.checkExplored): alone, it fills it
     * by the game's own loot tables; on a server, it asks the server for what it holds, which
     * comes some frames later.
     */
    static void explore(int playerIndex, ItemContainer container, IsoPlayer player) {
        callPage(Trouble.EXPLORE, playerIndex, "checkExplored", LuaManager.env.rawget(INVENTORY_PAGE), container, player);
    }

    /** The container the game's loot panel gathers what lies on the floor round a player into, or null where its Lua has none to give. */
    static ItemContainer floor(int playerIndex) {
        Object gather = inventoryPage("GetFloorContainer");
        Object[] found = gather == null ? new Object[0] : LuaManager.caller.pcall(UIManager.getDefaultThread(), gather, Double.valueOf(playerIndex));
        if (found.length > 1 && Boolean.TRUE.equals(found[0]) && found[1] instanceof ItemContainer floor) {
            return floor;
        }
        sayOnce(Trouble.FLOOR, playerIndex, "the game's ISInventoryPage.GetFloorContainer(" + playerIndex + ") gave no container, so the loot panel does not follow the look to the floor: "
                + Arrays.toString(found));
        return null;
    }

    /** Calls a function of the game's ISInventoryPage, and logs once per kind of trouble for each player where it is missing or raised an error. */
    private static boolean callPage(Trouble trouble, int playerIndex, String function, Object... arguments) {
        Object callee = inventoryPage(function);
        Object[] done = callee == null ? new Object[0] : LuaManager.caller.pcall(UIManager.getDefaultThread(), callee, arguments);
        if (done.length == 0 || !Boolean.TRUE.equals(done[0])) {
            sayOnce(trouble, playerIndex, "the game's ISInventoryPage." + function + " failed for player " + (playerIndex + 1) + ": " + Arrays.toString(done));
            return false;
        }
        return true;
    }

    /** A function of the game's ISInventoryPage, or null where its Lua has none. */
    private static Object inventoryPage(String function) {
        return LuaManager.env.rawget(INVENTORY_PAGE) instanceof KahluaTable page ? page.rawget(function) : null;
    }

    /**
     * Takes down a player's Back button wheel where it is up, the way letting go of the button
     * does but with nothing on it chosen. A wheel that is not up is left alone: taking it down
     * hands the player's controller back to the game, and it may be on another panel.
     */
    static void shutBackWheel(int playerIndex) {
        Object[] found = LuaManager.caller.pcall(UIManager.getDefaultThread(), LuaManager.env.rawget(BACK_WHEEL), Double.valueOf(playerIndex));
        if (found.length < 2 || !Boolean.TRUE.equals(found[0]) || !(found[1] instanceof KahluaTable wheel)) {
            if (!wheelTroubleSaid[playerIndex]) {
                sayOnce(wheelTroubleSaid, playerIndex, "the game's " + BACK_WHEEL + "(" + playerIndex + ") gave no wheel to take down: " + Arrays.toString(found));
            }
            return;
        }
        if (wheel.rawget("javaObject") instanceof UIElementInterface shown && shown.isVisible() && UIManager.getUI().contains(shown)) {
            Object[] shut = LuaManager.caller.pcall(UIManager.getDefaultThread(), UIManager.tableget(wheel, "undisplay"), wheel);
            if (shut.length == 0 || !Boolean.TRUE.equals(shut[0])) {
                if (!wheelTroubleSaid[playerIndex]) {
                    sayOnce(wheelTroubleSaid, playerIndex, "the game's Back button wheel of player " + (playerIndex + 1) + " could not be taken down: " + Arrays.toString(shut));
                }
                return;
            }
        }
        wheelTroubleSaid[playerIndex] = false;
    }

    /**
     * Whether what went wrong with each player's wheel, and with their dashboard panel, has been said since it last
     * went right: the wheel is asked after on every frame the button is held, the panel on every frame it is up.
     */
    private static final boolean[] wheelTroubleSaid = new boolean[View.COUNT], dashboardTroubleSaid = new boolean[View.COUNT];

    private static void sayOnce(boolean[] said, int playerIndex, String trouble) {
        if (!said[playerIndex]) {
            said[playerIndex] = true;
            Log.info(trouble);
        }
    }

    private static final String BACK_WHEEL = "getPlayerBackButtonWheel";
}
