package com.cameraunlock.zomboid;

import java.util.Map;
import java.util.WeakHashMap;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;

/**
 * What a piece of wall the game names no material for is made of, by the wall it stands in. The
 * game names a material (MaterialType) for the plain lengths of an outside wall, and none for the
 * pieces of the same wall round its windows and doorways, whose sheets hold nothing else
 * (walls_exterior_house_03 to 13), nor for most of those on the sheets of the plain lengths. So
 * such a piece is made of what the nearest plain length named along the same edge line is made of,
 * either way, past any other unnamed pieces and with wall on every edge between: siding round a
 * window is siding, brick round a doorway brick. Where the nearest each way are as near and differ,
 * the one that stops a round more is taken. A piece with no named wall in reach keeps no material.
 * Logic thread.
 */
final class WallRuns {
    private WallRuns() {}

    /** How many squares along its edge line a piece looks for a named wall. */
    static final int REACH = 6;

    /** What each piece was found made of, "" for nothing: a wall's run does not change while it is loaded. */
    private static final Map<IsoObject, String> found = new WeakHashMap<>();

    /** What a wall piece the game names no material for is made of by its run, or null for none. */
    static String materialOf(IsoObject piece) {
        String known = found.get(piece);
        if (known == null) {
            String looked = look(piece);
            known = looked == null ? "" : looked;
            found.put(piece, known);
        }
        return known.isEmpty() ? null : known;
    }

    private static String look(IsoObject piece) {
        IsoSprite sprite = piece.getSprite();
        IsoGridSquare square = piece.getSquare();
        if (sprite == null || square == null) {
            return null;
        }
        IsoCell cell = IsoWorld.instance.currentCell;
        if (cell == null) {
            return null;
        }
        PropertyContainer properties = sprite.getProperties();
        IsoObjectType type = sprite.getType();
        if (!WorldScene.isWall(properties, type)) {
            return null;
        }
        boolean corner = properties.has(IsoFlagType.WallNW);
        String made = null;
        if (corner || Edges.north(properties, type)) {
            made = alongLine(cell, square, true);
        }
        if (made == null && (corner || Edges.west(properties, type))) {
            made = alongLine(cell, square, false);
        }
        return made;
    }

    /** The material of the nearest named wall along a square's north or west edge line, each way. */
    private static String alongLine(IsoCell cell, IsoGridSquare square, boolean north) {
        int beforeAt = 0, afterAt = 0;
        String before = null, after = null;
        for (int way = -1; way <= 1; way += 2) {
            for (int step = 1; step <= REACH; step++) {
                int x = square.getX() + (north ? way * step : 0), y = square.getY() + (north ? 0 : way * step);
                IsoGridSquare next = cell.getGridSquare(x, y, square.getZ());
                IsoObject wall = next == null ? null : WorldScene.wallOn(next, north, false);
                if (wall == null) {
                    break;
                }
                String made = Penetration.materialOf(wall);
                if (made != null) {
                    if (way < 0) {
                        before = made;
                        beforeAt = step;
                    } else {
                        after = made;
                        afterAt = step;
                    }
                    break;
                }
            }
        }
        return nearer(before, beforeAt, after, afterAt);
    }

    /** Of the named walls found each way, so many squares off, the nearer, or at the same reach the one that stops a round more. */
    static String nearer(String before, int beforeAt, String after, int afterAt) {
        if (before == null || after == null) {
            return before != null ? before : after;
        }
        if (beforeAt != afterAt) {
            return beforeAt < afterAt ? before : after;
        }
        return Penetration.cost(before) >= Penetration.cost(after) ? before : after;
    }
}
