package com.cameraunlock.zomboid;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
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
 * Any thread: the builds ask it of a fence's piece too (BlastHoles.boards).
 */
final class WallRuns {
    private WallRuns() {}

    /** How many squares along its edge line a piece looks for a named wall. */
    static final int REACH = 6;

    /**
     * What each piece was found made of, "" for nothing, with the sprite and the place it was found
     * for: a wall's run does not change while it is loaded. The game hands a piece of a chunk it
     * lets go back out for another thing of another chunk (IsoChunk.doReuseGridsquares,
     * IsoObject.getNew), so an answer for another sprite or place is looked for again. An answer
     * read while the map along the run was not loaded yet is not kept, as the wall that names the
     * piece may be in the chunk still to load.
     */
    private static final Map<IsoObject, Found> found = Collections.synchronizedMap(new WeakHashMap<>());

    private record Found(String sprite, int x, int y, int z, String material) {}

    /** What a wall piece the game names no material for is made of by its run, or null for none. */
    static String materialOf(IsoObject piece) {
        IsoSprite sprite = piece.getSprite();
        String name = sprite == null ? null : sprite.getName();
        IsoGridSquare square = piece.getSquare();
        Found known = found.get(piece);
        if (known == null || square == null || !java.util.Objects.equals(known.sprite, name) || known.x != square.getX() || known.y != square.getY()
                || known.z != square.getZ()) {
            boolean[] unloaded = new boolean[1];
            String looked = look(piece, unloaded);
            if (unloaded[0] || square == null) {
                return looked;
            }
            known = new Found(name, square.getX(), square.getY(), square.getZ(), looked == null ? "" : looked);
            found.put(piece, known);
        }
        return known.material.isEmpty() ? null : known.material;
    }

    /** Forgets what every piece was found made of: the save is left, and the next may be another map. */
    static void forget() {
        found.clear();
    }

    private static String look(IsoObject piece, boolean[] unloaded) {
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
            made = alongLine(cell, square, true, unloaded);
        }
        if (made == null && (corner || Edges.west(properties, type))) {
            made = alongLine(cell, square, false, unloaded);
        }
        return made;
    }

    /**
     * The material of the nearest named wall along a square's north or west edge line, each way.
     * A way that comes to a square of a chunk not loaded before it finds a named wall sets unloaded.
     */
    private static String alongLine(IsoCell cell, IsoGridSquare square, boolean north, boolean[] unloaded) {
        int beforeAt = 0, afterAt = 0;
        String before = null, after = null;
        for (int way = -1; way <= 1; way += 2) {
            for (int step = 1; step <= REACH; step++) {
                int x = square.getX() + (north ? way * step : 0), y = square.getY() + (north ? 0 : way * step);
                IsoGridSquare next = cell.getGridSquare(x, y, square.getZ());
                IsoChunk chunk = next != null ? next.getChunk() : cell.getChunkForGridSquare(x, y, square.getZ());
                if (chunk == null || !chunk.loaded) {
                    unloaded[0] = true;
                    break;
                }
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
