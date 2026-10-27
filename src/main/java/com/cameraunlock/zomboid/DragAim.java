package com.cameraunlock.zomboid;

import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.entity.components.spriteconfig.SpriteConfigManager;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.Vector2;
import zombie.ui.UIManager;

/**
 * Where the game's drag cursors are in first person: the ghost of something to build, Walk To,
 * Place Item, moving furniture, Cut down Tree, Dig Furrow. The game puts one on the square its
 * own view has under the mouse (IsoCell.doBuildingInternal reads UIManager.getPickedTile), and
 * Place Item and the furniture cursor turn the mouse into a place on the floor themselves
 * (screenToIsoX and screenToIsoY). That view is under the picture, and while the look has the
 * mouse the mouse does not move. So while a drag is up and the picture is played in, the place
 * is where the line through the reticle meets the world, or with the pointer out where the
 * line through the pointer does, and where there is no such line the drag is put nowhere.
 *
 * Logic thread.
 */
final class DragAim {
    /** How far along a line that meets nothing nearer a drag is put, in tiles. */
    static final float REACH = 20f;
    /** How far back from a wall the line meets a drag is put, in tiles, so it is on the square in front of the wall. */
    static final float SHORT_OF_A_WALL = 0.05f;

    private final Vector2 tile = new Vector2();
    final Aimed aimed = new Aimed();

    /** The drag cursor a player has up, or null. */
    static KahluaTable of(int playerIndex) {
        IsoCell cell = IsoWorld.instance == null ? null : IsoWorld.instance.currentCell;
        return cell == null ? null : cell.getDrag(playerIndex);
    }

    /** The square the game has a drag cursor on, or null where it has none yet. */
    static IsoGridSquare squareOf(KahluaTable drag) {
        return drag.rawget("square") instanceof IsoGridSquare square ? square : null;
    }

    /** Whether the game would put down what is dragged where it is. */
    static boolean fitsOf(KahluaTable drag) {
        return Boolean.TRUE.equals(drag.rawget("canBeBuild"));
    }

    /** Whether a drag is put on a tree's bole where a line meets it: only Cut down Tree is, which is put on a tree. */
    static boolean onBoles(KahluaTable drag) {
        return "ISChopTreeCursor".equals(drag.rawget("Type"));
    }

    /** Whether a drag is put by a window a line meets: only a barricade is, which the game puts on a window. */
    static boolean onWindows(KahluaTable drag) {
        return drag.rawget("objectInfo") instanceof SpriteConfigManager.ObjectInfo info && barricades(info.getScript().getOnIsValid());
    }

    /** Whether a build's test of where it can go is one of the game's barricades', by the test's name. */
    static boolean barricades(String onIsValid) {
        return onIsValid != null && onIsValid.startsWith("BuildRecipeCode.barricade.");
    }

    /**
     * Puts a player's drag cursor away as the game's Esc does (ToggleEscapeMenu): its close
     * first, where it has one, and the furniture cursor by its own exitCursor, which also takes
     * down the panel it shows beside it.
     */
    static void putAway(KahluaTable drag, int playerIndex) {
        Object exit = UIManager.tableget(drag, "exitCursor");
        if (exit != null) {
            call(exit, drag);
            return;
        }
        Object close = UIManager.tableget(drag, "close");
        if (close != null) {
            call(close, drag);
        }
        IsoWorld.instance.currentCell.setDrag(null, playerIndex);
    }

    private static void call(Object function, KahluaTable drag) {
        Object[] result = LuaManager.caller.pcall(UIManager.getDefaultThread(), function, new Object[] {drag});
        if (result.length == 0 || !Boolean.TRUE.equals(result[0])) {
            throw new IllegalStateException("putting the drag cursor " + drag.rawget("Type") + " away failed: " + java.util.Arrays.toString(result));
        }
    }

    /**
     * How far along a line a drag is put: where the line first meets a wall, a floor or a
     * ceiling, and no further than REACH.
     *
     * @param met the distance to what the line met, infinity for nothing
     */
    static float along(float met) {
        return Math.min(met, REACH);
    }

    /**
     * Where a drag is put across a wall's face that a line met, so it is on the square the line
     * came from: back off the wall's edge, which is at a whole tile, and not back along the line,
     * which at a shallow angle or far out on the map goes on through the edge.
     *
     * @param at where the line met the wall, across its face, in tiles
     * @param way the line's way across the face
     */
    static float offWall(float at, float way) {
        return Math.round(at) - Math.signum(way) * SHORT_OF_A_WALL;
    }

    /** Puts the drag at a place on the map, in tiles. */
    void aim(float atX, float atY) {
        aimed.aim(atX, atY);
        tile.x = (float) Math.floor(atX);
        tile.y = (float) Math.floor(atY);
    }

    /**
     * Called with the tile the game's own view has under the mouse.
     *
     * @param inFirstPerson whether the picture is played in by a mouse player, for as long as
     *        first person is on, so a long frame, after which the picture reads as not drawn
     *        until the next, does not hand the game the tile under the hidden mouse
     */
    Vector2 tile(Vector2 stock, boolean inFirstPerson) {
        return switch (aimed.put(inFirstPerson)) {
            case STOCK -> stock;
            case AIMED -> stock == null ? null : tile;
            case NOWHERE -> null;
        };
    }

    /** Called with the place across the map the game's own view has at a point on the screen. */
    float isoX(float stock, boolean inFirstPerson) {
        return aimed.onTheFloor(inFirstPerson) ? aimed.x : stock;
    }

    /** The same, down the map. */
    float isoY(float stock, boolean inFirstPerson) {
        return aimed.onTheFloor(inFirstPerson) ? aimed.y : stock;
    }

    /** Where a drag is handed: the game's own square, the one aimed at, or none. */
    enum Put { STOCK, AIMED, NOWHERE }

    /** Where the picture has put the drag up. */
    static final class Aimed {
        /** Whether a place has been found for the drag up, and whether this frame found none. */
        private boolean placed, lost;
        private float x, y;

        void aim(float atX, float atY) {
            x = atX;
            y = atY;
            placed = true;
            lost = false;
        }

        /** A drag is up and this frame has no line to put it on: it is put nowhere, and its place on the floor stays where it was. */
        void lose() {
            lost = true;
        }

        /** No drag is up, or the picture is not played in. */
        void forget() {
            placed = false;
            lost = false;
        }

        Put put(boolean inFirstPerson) {
            if (!inFirstPerson) {
                return Put.STOCK;
            }
            return placed && !lost ? Put.AIMED : Put.NOWHERE;
        }

        /** Whether a place on the floor the game turns the mouse into is the aimed one. */
        boolean onTheFloor(boolean inFirstPerson) {
            return inFirstPerson && placed;
        }

        float x() {
            return x;
        }

        float y() {
            return y;
        }
    }
}
