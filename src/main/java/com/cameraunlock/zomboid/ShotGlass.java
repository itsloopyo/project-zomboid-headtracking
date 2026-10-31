package com.cameraunlock.zomboid;

import java.util.Locale;
import zombie.characters.IsoGameCharacter;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoWindow;
import zombie.util.list.PZArrayList;

/**
 * The window glass a shot breaks, played alone: the pane of every shut window a shot crosses,
 * whether or not anything stands behind it. The game breaks a window only on the way to a
 * character the shot hits (CombatManager.smashWindowBetween), so a shot at the glass itself
 * leaves it whole. Each is broken as the game breaks one there (IsoWindow.smashWindow), with its
 * sound, the noise zombies hear and the house alarm.
 *
 * The game lets a shot through a window's edge at any height on its level, so the glass breaks
 * wherever on that level the shot crosses the edge. A barricade on the shooter's side stops the
 * shot before the glass, and one on the far side stops it after.
 *
 * The light of a shut door's leaf the shot crosses breaks too, where Penetration lets the round
 * through it: the game keeps no glass in a door, so the mod keeps it (DoorGlass). Enough of a
 * shotgun blast's pellets through one door's lights smash its whole sash.
 */
final class ShotGlass {
    private ShotGlass() {}

    private static final GridTrace trace = new GridTrace();
    private static final GridTrace.Edges cross = ShotGlass::crossAt;
    private static IsoCell cell;
    private static Terrain terrain;
    private static IsoGameCharacter shooter;
    private static ViewCamera.Pose line;
    /** Whether the shot goes through a door's lights, as Penetration lets it, and so breaks them, and whether it is a pellet of a blast. */
    private static boolean lightsGive, pellet;

    /**
     * How far along a line through (eyeX, eyeY) the way (wayX, wayY) it crosses the edge between
     * two neighbouring squares, in the measure of the way given.
     */
    static float crossedAt(float eyeX, float eyeY, float wayX, float wayY, int fromX, int fromY, int toX, int toY) {
        return fromX == toX ? (Math.max(fromY, toY) - eyeY) / wayY : (Math.max(fromX, toX) - eyeX) / wayX;
    }

    /** A shot along line, from its eye as far as distance, in the picture's measure: breaks each window and door light it crosses. Logic thread. */
    static void along(IsoCell on, Terrain land, IsoGameCharacter by, ViewCamera.Pose shot, float distance) {
        if (!Rules.breaksGlass()) {
            return;
        }
        cell = on;
        terrain = land;
        shooter = by;
        line = shot;
        HandWeapon weapon = by.getPrimaryHandItem() instanceof HandWeapon held ? held : null;
        lightsGive = Penetration.applies(by, weapon);
        pellet = ImpactFinder.size(weapon) == ImpactFinder.PELLET;
        trace.firstBlockedEdge(cross, shot.eyeX, shot.eyeY, shot.forwardX, shot.forwardY, distance);
        cell = null;
        terrain = null;
        shooter = null;
        line = null;
    }

    /** Breaks the window or door light on the edge the shot crosses, if there is one, and answers whether a barricade there stops the shot. */
    private static boolean crossAt(int fromX, int fromY, int toX, int toY) {
        float s = crossedAt(line.eyeX, line.eyeY, line.forwardX, line.forwardY, fromX, fromY, toX, toY);
        float x = line.eyeX + line.forwardX * s, y = line.eyeY + line.forwardY * s, up = line.eyeUp + line.forwardUp * s;
        int level = (int) Math.floor((up - terrain.height(x, y)) / ViewCamera.LEVEL_HEIGHT);
        // An edge is the north or west edge of the square south or east of it, and what stands on it is that square's.
        boolean north = fromX == toX;
        IsoGridSquare owner = level < 0 ? null : cell.getGridSquare(Math.max(fromX, toX), Math.max(fromY, toY), level);
        if (owner == null) {
            return false;
        }
        IsoWindow window = windowOn(owner, north);
        if (window == null) {
            // Over the middle of the square the door is on, where the picture stands its leaf, as Penetration measures it.
            float overLeaf = (up - terrain.height(owner.getX() + 0.5f, owner.getY() + 0.5f)) / ViewCamera.LEVEL_HEIGHT - level;
            return throughADoor(owner, north, north ? x - owner.getX() : y - owner.getY(), overLeaf);
        }
        if (window.getBarricadeForCharacter(shooter) != null) {
            return true;
        }
        if (!window.IsOpen() && !window.isSmashed()) {
            window.smashWindow();
            if (DevBuild.ON) {
                Log.info(String.format(Locale.ROOT, "a shot broke the window at (%d, %d, %d), %s edge", owner.getX(), owner.getY(), level, north ? "north" : "west"));
            }
        }
        return window.getBarricadeOppositeCharacter(shooter) != null;
    }

    /**
     * Breaks the light of the shut door on a square's north or west edge that the shot crosses at
     * `along` the edge and `up` levels over the floor, and answers whether the door stops the
     * shot: where it is barricaded. A round that goes on through the leaf's wood is Penetration's.
     */
    private static boolean throughADoor(IsoGridSquare square, boolean north, float along, float up) {
        IsoObject door = DoorGlass.shutDoorOn(square, north);
        // Beside the leaf the shot meets the doorway's frame, which Penetration costs as a wall.
        if (door == null || !DoorGlass.onLeaf(door, north, along)) {
            return false;
        }
        if (DoorGlass.barricaded(door)) {
            return true;
        }
        int light = lightsGive ? DoorGlass.lightAt(door, north, along, up) : -1;
        if (DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "a shot crossed the door at (%d, %d), %s edge, %.3f along and %.3f levels up: %s", square.getX(), square.getY(),
                    north ? "north" : "west", along, up, !lightsGive ? "the round does not go through door glass" : light >= 0 ? "light " + light : DoorGlass.describe(door, north)));
        }
        if (light >= 0 && pellet) {
            DoorGlass.pellet(door, light, along, up);
        } else if (light >= 0) {
            DoorGlass.breakLight(door, light);
        }
        return false;
    }

    private static IsoWindow windowOn(IsoGridSquare square, boolean north) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i) instanceof IsoWindow window && window.getNorth() == north) {
                return window;
            }
        }
        return null;
    }
}
