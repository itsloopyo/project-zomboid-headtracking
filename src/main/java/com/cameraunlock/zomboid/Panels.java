package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.List;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
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
    private static final List<String> INVENTORY_PANELS = List.of("getPlayerInventory", "getPlayerLoot");

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
     * windows and dialogs.
     */
    static int open() {
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
            if (element.isVisible() && (element.isForceCursorVisible() || element.isWantKeyEvents()
                    || element instanceof UIElement drawn && isWindow(drawn.getTable()))) {
                open++;
            }
        }
        return open;
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
    static void showInventory(boolean visible) {
        for (String function : INVENTORY_PANELS) {
            Object[] found = LuaManager.caller.pcall(UIManager.getDefaultThread(), LuaManager.env.rawget(function), Double.valueOf(0));
            if (found.length < 2 || !Boolean.TRUE.equals(found[0]) || !(found[1] instanceof KahluaTable panel)) {
                Log.info("the game's " + function + "(0) gave no panel, which is left as it is: " + Arrays.toString(found));
                continue;
            }
            LuaManager.caller.pcallvoid(UIManager.getDefaultThread(), UIManager.tableget(panel, "setVisible"), panel, visible);
        }
    }
}
