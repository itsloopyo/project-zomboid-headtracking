package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.objects.IsoCurtain;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoLightSwitch;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWindow;
import zombie.iso.objects.IsoWorldInventoryObject;
import zombie.ui.UIManager;
import zombie.util.list.PZArrayList;

/**
 * What the Interact key acts on in first person: the thing the middle of the
 * view is on. The game's own view has the player click on a door, a window, a
 * curtain, a light switch, a container or something lying on the ground. Here
 * there is no cursor to click with, so the clean view is traced across the
 * map to the first of those it meets, and that object is handed to the same
 * Lua the game runs for a click on it. Whether the character is near enough,
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
        /** Nothing the game would take a click on is at the middle of the view: the key is the game's. */
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
    /** How near the view's line has to pass to something lying on the ground or on a table. */
    private static final float ITEM_RADIUS = 0.3f;
    /** The view runs this far on past the floor it meets, so a thing lying where it lands is in reach of it. */
    private static final float PAST_THE_FLOOR = 0.1f;

    private static final String CLICK_HANDLER = "ISObjectClickHandler", WORLD_MENU = "ISWorldObjectContextMenu";
    private static final String PRESS = "doClickSpecificObject", RELEASE = "doClick", GRAB = "onGrabWItem";

    /**
     * The key (Outlines.key) of what the key would act on this frame, for the picture to
     * outline, or Outlines.NONE when the view is on nothing the key acts on.
     */
    volatile long marked = Outlines.NONE;

    /** Notes what the key would act on with the view as it is, or that there is nothing. */
    void mark(ViewCamera.Pose view, float floorUp, IsoPlayer player, IsoCell cell) {
        IsoObject found = find(view, floorUp, cell, (int) Math.floor(player.getZ()), false);
        marked = found == null ? Outlines.NONE : Outlines.key(found);
    }

    /** What the game's Lua lacks for the key to work, or null. */
    static String gap() {
        String gap = gap(CLICK_HANDLER, PRESS);
        if (gap == null) {
            gap = gap(CLICK_HANDLER, RELEASE);
        }
        return gap != null ? gap : gap(WORLD_MENU, GRAB);
    }

    private static String gap(String table, String function) {
        return LuaManager.env.rawget(table) instanceof KahluaTable found && found.rawget(function) != null
                ? null : "the game's Lua has no " + table + "." + function;
    }

    /**
     * Acts on what the middle of the view is on.
     *
     * @param view    the clean view
     * @param floorUp the height of the floor the player stands on, in tiles above level 0
     * @param pointer whether the player has a pointer to work a container's panels with
     */
    Outcome press(ViewCamera.Pose view, float floorUp, IsoPlayer player, IsoCell cell, boolean pointer) {
        IsoObject found = find(view, floorUp, cell, (int) Math.floor(player.getZ()), false);
        if (found == null) {
            return Outcome.NOTHING;
        }
        if (found instanceof IsoWorldInventoryObject item) {
            return call(WORLD_MENU, GRAB, null, item, Double.valueOf(player.getIndex())) == null ? Outcome.NOTHING : Outcome.USED;
        }
        if (found instanceof IsoDoor || found instanceof IsoWindow || found instanceof IsoCurtain
                || found instanceof IsoLightSwitch || found instanceof IsoThumpable thumpable && thumpable.isDoor()) {
            // False when the game will not have it: too far off, the wrong side of a wall, aiming.
            Object[] answered = call(CLICK_HANDLER, PRESS, found, Double.valueOf(player.getIndex()), player);
            return answered != null && answered.length > 1 && Boolean.TRUE.equals(answered[1]) ? Outcome.USED : Outcome.NOTHING;
        }
        if (!pointer) {
            // The game's click on a container is the first player's, whoever asks, and its panels are a pointer's. A
            // controller's own key opens what its player stands at, so the key is left to the game.
            return Outcome.NOTHING;
        }
        IsoGridSquare at = found instanceof IsoMovingObject body ? body.getCurrentSquare() : found.getSquare();
        IsoGridSquare from = player.getCurrentSquare();
        // The game's own test for a container near enough to open. One further off it walks to.
        boolean inReach = at != null && from != null && Math.abs(from.getX() - at.getX()) <= 1 && Math.abs(from.getY() - at.getY()) <= 1
                && from.getZ() == at.getZ() && !from.isBlockedTo(at);
        if (inReach) {
            // Unfolded first: the click handler then puts the container in the loot panel.
            Panels.showInventory(player.getIndex(), true);
        }
        if (call(CLICK_HANDLER, RELEASE, found, Double.valueOf(0), Double.valueOf(0)) == null) {
            // The panels are up already where it was in reach, and the pointer is wanted for them.
            return inReach ? Outcome.CONTAINER : Outcome.NOTHING;
        }
        return inReach ? Outcome.CONTAINER : Outcome.USED;
    }

    /**
     * What the game's Lua answered, the call's own success first, or null where it raised an
     * error. That Lua is the game's and any mod's that wraps it, and this runs inside the
     * game's update of the player: the error is logged and the key is left to the game.
     */
    private Object[] call(String table, String function, Object... arguments) {
        Object[] result = LuaManager.caller.pcall(UIManager.getDefaultThread(), ((KahluaTable) LuaManager.env.rawget(table)).rawget(function), arguments);
        if (result.length == 0 || !Boolean.TRUE.equals(result[0])) {
            Log.info("the game's " + table + "." + function + " failed, and Interact is left to the game: " + Arrays.toString(result));
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
        return find(line, floorUp, cell, (int) Math.floor(player.getZ()), true);
    }

    /**
     * The first thing along the view that the game takes a click on, or null. The map is
     * squares with walls on their edges: a door, a window or a curtain is met where the view
     * crosses an edge, and everything else on the square it stands on.
     *
     * @param menu whether it is for the world menu, which is for anything the view meets
     */
    private IsoObject find(ViewCamera.Pose view, float floorUp, IsoCell cell, int level, boolean menu) {
        float reach = menu ? MENU_REACH : REACH;
        float down = view.forwardUp < 0f ? (floorUp - view.eyeUp) / view.forwardUp : Float.POSITIVE_INFINITY;
        reach = Math.min(reach, down + PAST_THE_FLOOR);
        int tileX = (int) Math.floor(view.eyeX), tileY = (int) Math.floor(view.eyeY);
        int stepX = view.forwardX > 0f ? 1 : -1, stepY = view.forwardY > 0f ? 1 : -1;
        float nextX = view.forwardX == 0f ? Float.POSITIVE_INFINITY : ((view.forwardX > 0f ? tileX + 1 : tileX) - view.eyeX) / view.forwardX;
        float nextY = view.forwardY == 0f ? Float.POSITIVE_INFINITY : ((view.forwardY > 0f ? tileY + 1 : tileY) - view.eyeY) / view.forwardY;
        float perTileX = view.forwardX == 0f ? Float.POSITIVE_INFINITY : Math.abs(1f / view.forwardX);
        float perTileY = view.forwardY == 0f ? Float.POSITIVE_INFINITY : Math.abs(1f / view.forwardY);
        IsoGridSquare from = cell.getGridSquare(tileX, tileY, level);
        while (from != null) {
            IsoObject here = onSquare(from, view, reach);
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
                if (menu && from.isBlockedTo(to)) {
                    IsoObject wall = switchOn(from);
                    return wall != null ? wall : (ownerIsTo ? to : from).getWall(!crossX);
                }
            }
            if (from.isBlockedTo(to)) {
                return switchOn(from);
            }
            if (to.isSolid() || to.isSolidTrans()) {
                // A counter, a fridge, a shelf unit: the view stops on it, and it is what is looked at.
                IsoObject there = onSquare(to, view, Float.POSITIVE_INFINITY);
                if (there == null && menu) {
                    there = standing(to);
                }
                return there != null ? there : switchOn(from);
            }
            from = to;
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
            if (object instanceof IsoDoor door && door.north == north
                    || object instanceof IsoThumpable thumpable && thumpable.isDoor() && thumpable.north == north) {
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
     * What is on a square for the key: the thing lying nearest the view's line, else a body
     * or a piece of furniture that holds things.
     */
    private IsoObject onSquare(IsoGridSquare square, ViewCamera.Pose view, float reach) {
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
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            if (object.getContainer() != null && !(object instanceof IsoWorldInventoryObject)) {
                return object;
            }
        }
        return null;
    }

    /** The floor where the view comes down to it, or null where there is no square or no floor there. */
    private static IsoObject floorAt(IsoCell cell, ViewCamera.Pose view, float down, int level) {
        IsoGridSquare square = cell.getGridSquare((int) Math.floor(view.eyeX + view.forwardX * down),
                (int) Math.floor(view.eyeY + view.forwardY * down), level);
        return square == null ? null : square.getFloor();
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
