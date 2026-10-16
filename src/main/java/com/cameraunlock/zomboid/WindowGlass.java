package com.cameraunlock.zomboid;

import zombie.characters.IsoPlayer;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoWindow;
import zombie.util.list.PZArrayList;

/**
 * The shut windows near the viewer, seen from inside. Glass throws back some of the room it
 * is in, and with the street dark behind it the room is plain in it, the viewer among the
 * rest. LookingGlass draws the room as a pane shows it, as it draws a mirror's picture, and
 * lays it over the panes by as much as glass throws back.
 *
 * Each window is noted as a glass a tile wide and a level high, in the plane of its panes.
 * The opening is smaller: the picture is laid a hair before the panes and behind the face
 * of the wall, which hides it everywhere but in the opening.
 */
final class WindowGlass {
    /** The most windows noted. */
    static final int MOST = 32;
    /** How far along the map from the viewer a window is looked for, in tiles: a glass beyond LookingGlass.REACH has no picture of its own. */
    private static final int REACH = (int) Math.ceil(LookingGlass.REACH) + 1;
    /**
     * How far before the panes' plane the picture is laid, in tiles: nearer the eye than the
     * pane and its bars, which would hide it, and further than the face of the wall the window
     * is set in, which hides it everywhere but in the opening.
     */
    static final float LAID = 0.02f;
    /**
     * How far before the panes' plane the picture of what they show begins, in tiles: past the
     * face of the wall they are set in, which seen from behind is a sheet over all of it.
     */
    static final float CLEAR = WorldScene.WALL_HALF + LookingGlass.CLEAR;

    private WindowGlass() {}

    /** The windows found, as a frame carries them from the thread that found them to the one that draws. */
    static final class Panes {
        /** Their glass, LookingGlass.FLOATS each, as LookingGlass.consider takes it. */
        final float[] glass = new float[MOST * LookingGlass.FLOATS];
        int count;
    }

    /**
     * Logic thread: the shut, whole windows on the player's floor within REACH whose room side
     * the eye is on, each facing the eye.
     */
    static void find(IsoCell cell, IsoPlayer player, Terrain terrain, ViewCamera.Pose view, Panes into) {
        into.count = 0;
        int level = (int) Math.floor(player.getZ());
        float foot = level * ViewCamera.LEVEL_HEIGHT + terrain.lift(player.getX(), player.getY(), level);
        int fromX = (int) Math.floor(view.eyeX), fromY = (int) Math.floor(view.eyeY);
        for (int x = fromX - REACH; x <= fromX + REACH; x++) {
            for (int y = fromY - REACH; y <= fromY + REACH && into.count < MOST; y++) {
                IsoGridSquare square = cell.getGridSquare(x, y, level);
                if (square == null) {
                    continue;
                }
                PZArrayList<IsoObject> objects = square.getObjects();
                for (int i = 0; i < objects.size() && into.count < MOST; i++) {
                    if (!(objects.get(i) instanceof IsoWindow window) || window.isSmashed() || window.isGlassRemoved() || window.IsOpen()) {
                        continue;
                    }
                    boolean north = window.getNorth();
                    int edge = north ? y : x;
                    float off = (north ? view.eyeY : view.eyeX) - edge;
                    // The square on the eye's side of the window's edge: a window looked at from the street throws back the street, which the pane does itself.
                    IsoGridSquare eyesSide = off < 0f ? (north ? cell.getGridSquare(x, y - 1, level) : cell.getGridSquare(x - 1, y, level)) : square;
                    if (eyesSide == null || eyesSide.isOutside()) {
                        continue;
                    }
                    note(north, edge, north ? x : y, foot, Math.signum(off), into.glass, into.count++ * LookingGlass.FLOATS);
                }
            }
        }
    }

    /** A window's glass, on the edge of constant y (north) or x, a tile from along, facing the side given, into out from at. */
    static void note(boolean north, int edge, int along, float foot, float side, float[] out, int at) {
        out[at + LookingGlass.CORNER] = north ? along : edge;
        out[at + LookingGlass.CORNER + 1] = north ? edge : along;
        out[at + LookingGlass.CORNER + 2] = foot;
        out[at + LookingGlass.ACROSS] = north ? 1f : 0f;
        out[at + LookingGlass.ACROSS + 1] = north ? 0f : 1f;
        out[at + LookingGlass.ACROSS + 2] = 0f;
        out[at + LookingGlass.UP] = 0f;
        out[at + LookingGlass.UP + 1] = 0f;
        out[at + LookingGlass.UP + 2] = ViewCamera.LEVEL_HEIGHT;
        out[at + LookingGlass.FACING] = north ? 0f : side;
        out[at + LookingGlass.FACING + 1] = north ? side : 0f;
        out[at + LookingGlass.FACING + 2] = 0f;
    }
}
