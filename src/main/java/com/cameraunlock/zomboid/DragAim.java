package com.cameraunlock.zomboid;

import se.krka.kahlua.vm.KahluaTable;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.Vector2;

/**
 * Where the game's drag cursors are in first person: the ghost of something to build, Walk To,
 * Place Item and moving furniture. The game puts one on the square its own view has under the
 * mouse (IsoCell.doBuildingInternal reads UIManager.getPickedTile), and Place Item and the
 * furniture cursor turn the mouse into a place on the floor themselves (screenToIsoX and
 * screenToIsoY). That view is under the picture, and while the look has the mouse the mouse
 * does not move. So while a drag is up and the picture is played in, the place is where the
 * line through the reticle meets the world, or with the pointer out where the line through the
 * pointer does.
 *
 * Logic thread.
 */
final class DragAim {
    /** How far along a line that meets nothing nearer a drag is put, in tiles. */
    static final float REACH = 20f;
    /** How far short of a wall the line meets a drag is put, in tiles, so it is on the square in front of the wall. */
    static final float SHORT_OF_A_WALL = 0.05f;

    private final View view;
    private final Vector2 tile = new Vector2();
    private boolean aimed;
    private float x, y;

    DragAim(View view) {
        this.view = view;
    }

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

    /**
     * How far along a line a drag is put: where the line first meets a wall, a floor or a
     * ceiling, short of a wall, and no further than REACH.
     *
     * @param met the distance to what the line met, infinity for nothing
     * @param wall whether what it met is a wall
     */
    static float along(float met, boolean wall) {
        if (met > REACH) {
            return REACH;
        }
        return wall ? Math.max(met - SHORT_OF_A_WALL, 0f) : met;
    }

    /** Puts the drag at a place on the map, in tiles. */
    void aim(float atX, float atY) {
        x = atX;
        y = atY;
        tile.x = (float) Math.floor(atX);
        tile.y = (float) Math.floor(atY);
        aimed = true;
    }

    /** There is no drag to put, or no line to put it on. */
    void lose() {
        aimed = false;
    }

    /**
     * Whether the drag is put by the picture: for as long as first person is on, so a long frame,
     * after which active() reads false until the next, does not hand the game the tile under the
     * held cursor for a frame.
     */
    private boolean holds() {
        return aimed && view.firstPerson.isOn();
    }

    /** Called with the tile the game's own view has under the mouse. */
    Vector2 tile(Vector2 stock) {
        return stock != null && holds() ? tile : stock;
    }

    /** Called with the place across the map the game's own view has at a point on the screen. */
    float isoX(float stock) {
        return holds() ? x : stock;
    }

    /** The same, down the map. */
    float isoY(float stock) {
        return holds() ? y : stock;
    }
}
