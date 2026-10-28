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
 * with everything on that square, as the game's own click would. A left click there uses what
 * the line meets, as the game's own left click does in its view (FirstPerson.useAtPointer).
 *
 * Logic thread only: the game tells its Lua of the mouse there.
 */
final class WorldMenu {
    final View view;

    WorldMenu(View view) {
        this.view = view;
    }

    private static final String RIGHT_DOWN = "OnRightMouseDown", RIGHT_UP = "OnRightMouseUp", LEFT_DOWN = "OnMouseDown", LEFT_UP = "OnMouseUp";
    /** A press held longer than this is no click: for the right button it is the aim, and it is the game's own limit for a right click on an object. */
    private static final long CLICK_NANOS = 500_000_000L;
    /** Where the game's ISContextMenu.get starts a menu sliding in from, to the right of and above where it is asked for. */
    private static final double SLIDE_X = 20, SLIDE_Y = -10;

    private static final String MANAGER = "ISContextManager";

    private boolean down, leftDown;
    private long downAt, leftDownAt;

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
        if (RIGHT_DOWN.equals(event) && view.dragsInFirstPerson() && DragAim.of(view.index) instanceof KahluaTable drag) {
            // In the game's own view a right click opens the world menu, which puts the drag away.
            DragAim.putAway(drag, view.index);
            view.controls.spendRightPress();
            down = false;
            Log.info("a right click put the drag cursor " + drag.rawget("Type") + " away");
        } else if (RIGHT_DOWN.equals(event)) {
            down = view.firstPerson.pointerOut() && !view.controls.rightPressSpent();
            downAt = System.nanoTime();
        } else if (RIGHT_UP.equals(event) && down) {
            down = false;
            if (System.nanoTime() - downAt < CLICK_NANOS && view.firstPerson.pointerOut()) {
                open(x, y);
            }
        } else if (LEFT_DOWN.equals(event)) {
            leftDown = view.firstPerson.pointerOut();
            leftDownAt = System.nanoTime();
        } else if (LEFT_UP.equals(event) && leftDown) {
            leftDown = false;
            // With a drag cursor up the click puts it down, through the game's build button, and is for nothing else.
            if (System.nanoTime() - leftDownAt < CLICK_NANOS && view.firstPerson.pointerOut() && DragAim.of(view.index) == null) {
                view.firstPerson.useAtPointer((float) x, (float) y);
            }
        }
    }

    private void open(double x, double y) {
        IsoObject found = view.firstPerson.underPointer((float) x, (float) y);
        if (FirstPerson.TRACE) {
            Log.info("world menu at (" + x + ", " + y + "): " + found);
        }
        if (found != null) {
            openFor(found, x, y);
        }
    }

    /** Opens the game's world menu for a thing at a place in the game's window, and holds the pointer out for it. Whether it opened. */
    boolean openFor(IsoObject found, double x, double y) {
        IsoGridSquare square = found instanceof IsoMovingObject body ? body.getCurrentSquare() : found.getSquare();
        if (square == null) {
            return false;
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
            return false;
        }
        KahluaTable objects = LuaManager.platform.newTable();
        objects.rawset(Double.valueOf(1), found);
        Object[] made = call(UIManager.tableget(instance, "createWorldMenu"), player, found, objects, atX[1], atY[1]);
        if (made == null || made.length < 2 || !(made[1] instanceof KahluaTable menu)
                || !(menu.rawget("javaObject") instanceof UIElementInterface shown) || !shown.isVisible()) {
            return false;
        }
        menu.rawset("requestX", x);
        menu.rawset("requestY", y);
        call(UIManager.tableget(menu, "setSlideGoalX"), menu, x + SLIDE_X, x);
        call(UIManager.tableget(menu, "setSlideGoalY"), menu, y + SLIDE_Y, y);
        view.firstPerson.holdPointerForMenu();
        return true;
    }

    /**
     * Takes down the player's context menu where one is up, the world menu and any other, the
     * way the game's own closeAll does. Whether one was up.
     */
    boolean shut() {
        Object[] found = call(LuaManager.env.rawget("getPlayerContextMenu"), Double.valueOf(view.index));
        if (found == null || found.length < 2 || !(found[1] instanceof KahluaTable menu)
                || !(menu.rawget("javaObject") instanceof UIElementInterface shown) || !shown.isVisible()) {
            return false;
        }
        // The game leaves a menu with nothing in it shown, off the screen.
        Object[] empty = call(UIManager.tableget(menu, "isEmpty"), menu);
        if (empty == null || empty.length < 2 || Boolean.TRUE.equals(empty[1])) {
            return false;
        }
        call(UIManager.tableget(menu, "closeAll"), menu);
        down = false;
        Log.info("the context menu of player " + (view.index + 1) + " was taken down");
        return true;
    }

    /** What a Lua function of the game's answered, its own success first, or null where it raised an error, which is logged. */
    private static Object[] call(Object function, Object... arguments) {
        Object[] result = LuaManager.caller.pcall(UIManager.getDefaultThread(), function, arguments);
        if (result.length == 0 || !Boolean.TRUE.equals(result[0])) {
            Log.info("a call to the game's context menu failed: " + Arrays.toString(result));
            return null;
        }
        return result;
    }
}
