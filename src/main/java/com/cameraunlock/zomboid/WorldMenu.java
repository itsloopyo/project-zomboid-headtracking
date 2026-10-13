package com.cameraunlock.zomboid;

import java.util.Arrays;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.ui.UIElementInterface;
import zombie.ui.UIManager;

/**
 * The game's menu for things in the world, opened by a right click with the pointer out over
 * the first person picture. The game opens it for what its own view has under the pointer,
 * and under this picture that is nothing (Controls.picked). Here the line from the eye through
 * the pointer is traced across the map, and the game's own menu is opened for what it meets,
 * with everything on that square, as the game's own click would.
 *
 * Logic thread only: the game tells its Lua of the mouse there.
 */
final class WorldMenu {
    final View view;

    WorldMenu(View view) {
        this.view = view;
    }

    private static final String RIGHT_DOWN = "OnRightMouseDown", RIGHT_UP = "OnRightMouseUp";
    /** A right press held longer than this is the aim: the game's own limit for a right click on an object. */
    private static final long CLICK_NANOS = 500_000_000L;
    /** Where the game's ISContextMenu.get starts a menu sliding in from, to the right of and above where it is asked for. */
    private static final double SLIDE_X = 20, SLIDE_Y = -10;

    private static final String MANAGER = "ISContextManager";

    private boolean down;
    private long downAt;

    /** What the game's Lua lacks for the menu to open, or null. */
    static String gap() {
        if (!(LuaManager.env.rawget(MANAGER) instanceof KahluaTable manager) || manager.rawget("getInstance") == null) {
            return "the game's Lua has no " + MANAGER + ".getInstance";
        }
        for (String function : new String[] {"isoToScreenX", "isoToScreenY"}) {
            if (LuaManager.env.rawget(function) == null) {
                return "the game's Lua has no " + function;
            }
        }
        return null;
    }

    /**
     * Called after the game has told its Lua of a mouse button going down or up where none of
     * its panels took it.
     *
     * @param x the pointer's place across the game's window, in pixels
     * @param y the pointer's place down it
     */
    void mouse(String event, double x, double y) {
        if (RIGHT_DOWN.equals(event)) {
            down = view.firstPerson.pointerOut();
            downAt = System.nanoTime();
        } else if (RIGHT_UP.equals(event) && down) {
            down = false;
            if (System.nanoTime() - downAt < CLICK_NANOS && view.firstPerson.pointerOut()) {
                open(x, y);
            }
        }
    }

    private void open(double x, double y) {
        IsoObject found = view.firstPerson.underPointer((float) x, (float) y);
        if (FirstPerson.TRACE) {
            Log.info("world menu at (" + x + ", " + y + "): " + found);
        }
        if (found == null) {
            return;
        }
        IsoGridSquare square = found instanceof IsoMovingObject body ? body.getCurrentSquare() : found.getSquare();
        if (square == null) {
            return;
        }
        // The game turns the place on the screen it is handed back into a square of its own view
        // for the rest of the menu, so it is handed that square's place there, and the menu is
        // then put at the pointer, the way ISContextMenu.get puts it where it is asked for.
        Double player = Double.valueOf(view.index);
        Object[] atX = call(LuaManager.env.rawget("isoToScreenX"), player, square.getX() + 0.5, square.getY() + 0.5, (double) square.getZ());
        Object[] atY = call(LuaManager.env.rawget("isoToScreenY"), player, square.getX() + 0.5, square.getY() + 0.5, (double) square.getZ());
        Object[] manager = call(((KahluaTable) LuaManager.env.rawget(MANAGER)).rawget("getInstance"));
        if (atX == null || atX.length < 2 || atY == null || atY.length < 2 || manager == null || manager.length < 2
                || !(manager[1] instanceof KahluaTable instance)) {
            return;
        }
        KahluaTable objects = LuaManager.platform.newTable();
        objects.rawset(Double.valueOf(1), found);
        Object[] made = call(UIManager.tableget(instance, "createWorldMenu"), player, found, objects, atX[1], atY[1]);
        if (made == null || made.length < 2 || !(made[1] instanceof KahluaTable menu)
                || !(menu.rawget("javaObject") instanceof UIElementInterface shown) || !shown.isVisible()) {
            return;
        }
        menu.rawset("requestX", x);
        menu.rawset("requestY", y);
        call(UIManager.tableget(menu, "setSlideGoalX"), menu, x + SLIDE_X, x);
        call(UIManager.tableget(menu, "setSlideGoalY"), menu, y + SLIDE_Y, y);
        view.firstPerson.holdPointerForMenu();
    }

    /** What a Lua function of the game's answered, its own success first, or null where it raised an error, which is logged. */
    private static Object[] call(Object function, Object... arguments) {
        Object[] result = LuaManager.caller.pcall(UIManager.getDefaultThread(), function, arguments);
        if (result.length == 0 || !Boolean.TRUE.equals(result[0])) {
            Log.info("the game's world menu was not opened: a call failed: " + Arrays.toString(result));
            return null;
        }
        return result;
    }
}
