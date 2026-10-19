package com.cameraunlock.zomboid;

import zombie.characters.IsoPlayer;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWindow;
import zombie.util.list.PZArrayList;

/**
 * The shut windows and glazed doors near the viewer, seen from inside. Glass throws back some of the room it
 * is in, and with the street dark behind it the room is plain in it, the viewer among the
 * rest. LookingGlass draws the room as a pane shows it, as it draws a mirror's picture, and
 * lays it over the panes by as much as glass throws back.
 *
 * Each window is noted as a glass a tile wide and a level high, in the plane of its panes.
 * The opening is smaller: the picture is laid a hair before the panes and behind the face
 * of the wall, which hides it everywhere but in the opening. A door's glass is noted as the
 * lights in its leaf take up and no more: the leaf's faces stand before the picture between
 * them, and nothing hides the doorway round a leaf that does not fill it.
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
     * Logic thread: the shut, whole windows and the shut doors the mod built with glass in them
     * (Doors) on the player's floor within REACH whose room side the eye is on, each facing the eye.
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
                    IsoObject object = objects.get(i);
                    Windows.Shape leaf = null;
                    boolean north;
                    if (object instanceof IsoWindow window && !window.isSmashed() && !window.isGlassRemoved() && !window.IsOpen()) {
                        north = window.getNorth();
                    } else if (object instanceof IsoDoor door && !door.IsOpen()) {
                        north = door.north;
                        leaf = glazedLeaf(object, north);
                    } else if (object instanceof IsoThumpable thumpable && thumpable.isDoor() && !thumpable.IsOpen()) {
                        north = thumpable.north;
                        leaf = glazedLeaf(object, north);
                    } else {
                        continue;
                    }
                    if (!(object instanceof IsoWindow) && leaf == null) {
                        continue;
                    }
                    int edge = north ? y : x;
                    float off = (north ? view.eyeY : view.eyeX) - edge;
                    // The square on the eye's side of the window's edge: a window looked at from the street throws back the street, which the pane does itself.
                    IsoGridSquare eyesSide = off < 0f ? (north ? cell.getGridSquare(x, y - 1, level) : cell.getGridSquare(x - 1, y, level)) : square;
                    if (eyesSide == null || eyesSide.isOutside()) {
                        continue;
                    }
                    int at = into.count++ * LookingGlass.FLOATS;
                    note(north, edge, north ? x : y, foot, Math.signum(off), into.glass, at);
                    if (leaf != null) {
                        lights(leaf, north, into.glass, at);
                    }
                }
            }
        }
    }

    /** The leaf of a door the mod builds from its sprite, where it has glass in it: see OpeningBuilder.addBuiltDoor. */
    private static Windows.Shape glazedLeaf(IsoObject door, boolean north) {
        if (!Own.buildings()) {
            return null;
        }
        Doors.Shape shape = Doors.known(door.getSprite().getTextureForCurrentFrame(door.getDir()));
        return shape == null || shape.leaf.north != north || shape.leaf.panes.length == 0 ? null : shape.leaf;
    }

    /** Narrows a glass noted a tile wide and a level high at at to the lights of a door's leaf. */
    static void lights(Windows.Shape leaf, boolean north, float[] out, int at) {
        float start = 1f, end = 0f, foot = 1f, head = 0f;
        for (int pane = 0; pane < leaf.panes.length; pane += 5) {
            start = Math.min(start, leaf.panes[pane]);
            end = Math.max(end, leaf.panes[pane + 1]);
            foot = Math.min(foot, leaf.panes[pane + 2]);
            head = Math.max(head, leaf.panes[pane + 3]);
        }
        out[at + LookingGlass.CORNER + (north ? 0 : 1)] += start;
        out[at + LookingGlass.CORNER + 2] += foot * ViewCamera.LEVEL_HEIGHT;
        out[at + LookingGlass.ACROSS + (north ? 0 : 1)] = end - start;
        out[at + LookingGlass.UP + 2] = (head - foot) * ViewCamera.LEVEL_HEIGHT;
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
