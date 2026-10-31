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
 * The shut windows and glazed doors near the viewer. Glass throws back some of what is on the
 * eye's side of it, the viewer among the rest: the room seen from the room, plain with the
 * street dark behind the glass, and the street seen from the street, plain against a room
 * darker than the day. LookingGlass draws that side as a pane shows it, as it draws a
 * mirror's picture, and lays it over the panes by as much as glass throws back.
 *
 * Each window is noted as a glass a tile wide and a level high, in the plane of its panes.
 * The opening is smaller: the picture is laid a hair before the panes and behind the face
 * of the wall, which hides it everywhere but in the opening. A door's glass is noted as the
 * lights in its leaf take up and no more: the leaf's faces stand before the picture between
 * them, and nothing hides the doorway round a leaf that does not fill it. Where a light is
 * broken nothing stands before the picture there either, so each whole light is noted alone.
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
    static final float CLEAR = WorldScene.WALL_HALF + 0.003f;

    private WindowGlass() {}

    /** The windows found, as a frame carries them from the thread that found them to the one that draws. */
    static final class Panes {
        /** Their glass, LookingGlass.FLOATS each, as LookingGlass.consider takes it. */
        final float[] glass = new float[MOST * LookingGlass.FLOATS];
        int count;
        /** Whether the eye is out of doors, so that the side of them it sees is the street's: see throwsBack. */
        boolean street;
    }

    /**
     * Logic thread: the shut, whole windows and the shut doors the mod built with glass in them
     * (Doors) within REACH that the eye sees from a side it stands on (see throwsBack), each
     * facing the eye. From a room only its own floor's, which is all a room shows; from the
     * street the floors over and under it too, whose windows look onto the same street.
     */
    static void find(IsoCell cell, IsoPlayer player, Terrain terrain, ViewCamera.Pose view, Panes into) {
        into.count = 0;
        int level = (int) Math.floor(player.getZ());
        int fromX = (int) Math.floor(view.eyeX), fromY = (int) Math.floor(view.eyeY);
        IsoGridSquare eyes = cell.getGridSquare(fromX, fromY, level);
        boolean eyeOutside = eyes == null || eyes.isOutside();
        into.street = eyeOutside;
        int lowest = eyeOutside ? level - 1 : level, highest = eyeOutside ? level + 1 : level;
        for (int z = lowest; z <= highest; z++) {
            for (int x = fromX - REACH; x <= fromX + REACH; x++) {
                for (int y = fromY - REACH; y <= fromY + REACH && into.count < MOST; y++) {
                    IsoGridSquare square = cell.getGridSquare(x, y, z);
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
                        IsoGridSquare across = north ? cell.getGridSquare(x, y - 1, z) : cell.getGridSquare(x - 1, y, z);
                        IsoGridSquare eyesSide = off < 0f ? across : square;
                        if (eyesSide == null || !throwsBack(eyeOutside, eyesSide.isOutside())) {
                            continue;
                        }
                        // The window stands on the floor of the room it is in, which on a hill is not the land under the eye.
                        IsoGridSquare room = !square.isOutside() || across == null ? square : across;
                        float foot = z * ViewCamera.LEVEL_HEIGHT + terrain.lift(room.getX() + 0.5f, room.getY() + 0.5f, z);
                        if (leaf == null) {
                            note(north, edge, north ? x : y, foot, Math.signum(off), into.glass, into.count++ * LookingGlass.FLOATS);
                            continue;
                        }
                        // A broken light has no glass to throw anything back: each whole one is a glass of its own.
                        float[] panes = DoorGlass.withBroken(leaf.panes, DoorGlass.broken(object));
                        boolean whole = true;
                        for (int pane = 0; pane < panes.length; pane += 5) {
                            whole &= panes[pane + 4] >= 1f;
                        }
                        for (int pane = 0; pane < panes.length && into.count < MOST; pane += 5) {
                            if (whole || panes[pane + 4] >= 1f) {
                                int at = into.count++ * LookingGlass.FLOATS;
                                note(north, edge, north ? x : y, foot, Math.signum(off), into.glass, at);
                                lights(panes, whole ? 0 : pane, whole ? panes.length : pane + 5, north, into.glass, at);
                            }
                            if (whole) {
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Whether a window throws back to the eye what is on the eye's side of it, from whether the
     * eye's own square and the square on the eye's side of the window's edge are in the open
     * air: both or neither, the room from the room and the street from the street. The edge
     * alone does not tell which side the eye is on: from the street beyond a house, a window in
     * the far wall has its room on the eye's side and is seen through the house's other glass,
     * and a picture of the room laid over it from out there hid the room the pane shows.
     */
    static boolean throwsBack(boolean eyeOutside, boolean eyesSideOutside) {
        return eyeOutside == eyesSideOutside;
    }

    /** The leaf of a door the mod builds from its sprite, where it has glass in it: see OpeningBuilder.addBuiltDoor. */
    private static Windows.Shape glazedLeaf(IsoObject door, boolean north) {
        if (!Own.buildings()) {
            return null;
        }
        Doors.Shape shape = Doors.known(door.getSprite().getTextureForCurrentFrame(door.getDir()), north);
        return shape == null || shape.leaf.panes.length == 0 ? null : shape.leaf;
    }

    /** Narrows a glass noted a tile wide and a level high at at to the lights of a door's leaf from `from` to `to` in its panes, five numbers a light. */
    static void lights(float[] panes, int from, int to, boolean north, float[] out, int at) {
        float start = 1f, end = 0f, foot = 1f, head = 0f;
        for (int pane = from; pane < to; pane += 5) {
            start = Math.min(start, panes[pane]);
            end = Math.max(end, panes[pane + 1]);
            foot = Math.min(foot, panes[pane + 2]);
            head = Math.max(head, panes[pane + 3]);
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
