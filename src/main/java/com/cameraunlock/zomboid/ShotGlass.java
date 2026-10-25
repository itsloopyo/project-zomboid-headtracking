package com.cameraunlock.zomboid;

import java.util.Locale;
import zombie.characters.IsoGameCharacter;
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
 */
final class ShotGlass {
    private ShotGlass() {}

    private static final GridTrace trace = new GridTrace();
    private static final GridTrace.Edges cross = ShotGlass::crossAt;
    private static IsoCell cell;
    private static Terrain terrain;
    private static IsoGameCharacter shooter;
    private static ViewCamera.Pose line;

    /**
     * How far along a line through (eyeX, eyeY) the way (wayX, wayY) it crosses the edge between
     * two neighbouring squares, in the measure of the way given.
     */
    static float crossedAt(float eyeX, float eyeY, float wayX, float wayY, int fromX, int fromY, int toX, int toY) {
        return fromX == toX ? (Math.max(fromY, toY) - eyeY) / wayY : (Math.max(fromX, toX) - eyeX) / wayX;
    }

    /** A shot along line, from its eye as far as distance, in the picture's measure: breaks each window it crosses. Logic thread. */
    static void along(IsoCell on, Terrain land, IsoGameCharacter by, ViewCamera.Pose shot, float distance) {
        if (!Rules.breaksGlass()) {
            return;
        }
        cell = on;
        terrain = land;
        shooter = by;
        line = shot;
        trace.firstBlockedEdge(cross, shot.eyeX, shot.eyeY, shot.forwardX, shot.forwardY, distance);
        cell = null;
        terrain = null;
        shooter = null;
        line = null;
    }

    /** Breaks the window on the edge the shot crosses, if there is one, and answers whether a barricade there stops the shot. */
    private static boolean crossAt(int fromX, int fromY, int toX, int toY) {
        float s = crossedAt(line.eyeX, line.eyeY, line.forwardX, line.forwardY, fromX, fromY, toX, toY);
        float x = line.eyeX + line.forwardX * s, y = line.eyeY + line.forwardY * s, up = line.eyeUp + line.forwardUp * s;
        int level = (int) Math.floor((up - terrain.height(x, y)) / ViewCamera.LEVEL_HEIGHT);
        // An edge is the north or west edge of the square south or east of it, and what stands on it is that square's.
        boolean north = fromX == toX;
        IsoGridSquare owner = level < 0 ? null : cell.getGridSquare(Math.max(fromX, toX), Math.max(fromY, toY), level);
        IsoWindow window = owner == null ? null : windowOn(owner, north);
        if (window == null) {
            return false;
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
