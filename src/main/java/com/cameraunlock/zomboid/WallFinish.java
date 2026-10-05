package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.WeakHashMap;
import zombie.core.properties.PropertyContainer;
import zombie.core.textures.Texture;
import zombie.iso.BuildingDef;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.areas.IsoBuilding;
import zombie.iso.sprite.IsoSprite;

/**
 * What a building is finished in outside, and each of its rooms inside.
 *
 * A building has one finish outside. The walls that show it are those on its south and east
 * faces, which stand on the squares just outside it and are painted for the side out of doors:
 * the sprite most of them show is the building's. Of the plain walls among them, the one most
 * of the south face shows and the one most of the east face shows are kept as well: those are
 * what the far side of a wall on the north or the west of the building is drawn with, a
 * north face with the south face's sprite and a west face with the east's. A room's finish is the sprite most of the
 * walls on its own squares show, which are painted for the room. Both are read once for a
 * level of a building and kept, since every wall of it asks each time its chunk is built, and
 * read again every so often, since walls are built and knocked down.
 */
final class WallFinish {
    /** How many frames a level's finishes are kept for, and for how many when part of the building was not loaded. */
    private static final int KEPT = 600, RETRIED = 30;
    /** A building wider than this, in squares, is not read: nothing that size has one finish. */
    private static final int WIDEST = 200;
    private static final int LEVELS = 64;
    private static final int MOST_BUILDINGS = 256;

    private static final class Level {
        int stamp;
        boolean whole = true;
        Texture outside;
        Facing south, east;
        final HashMap<Long, Texture> rooms = new HashMap<>();
    }

    /** A plain wall of a building's outside: its sprite, for what it is made of, and the picture of it. */
    static final class Facing {
        final IsoSprite sprite;
        final Texture texture;

        Facing(IsoSprite sprite, Texture texture) {
            this.sprite = sprite;
            this.texture = texture;
        }
    }

    /** What the walls of one place show: the plain ones, and all of them for a place with no plain wall. */
    private static final class Tally {
        final WallSides.Votes plain = new WallSides.Votes(), any = new WallSides.Votes();
    }

    /**
     * Held weakly. A building holds its rooms and they hold every square of theirs, so a
     * building kept here after the game had unloaded it kept its squares, and what stands on
     * them, from being collected: across a few towns that came to more than a gigabyte, and
     * the game ran out of memory.
     */
    private static final WeakHashMap<IsoBuilding, Level[]> known = new WeakHashMap<>();
    private static final ArrayList<Texture> shown = new ArrayList<>();
    private static final ArrayList<IsoSprite> shownBy = new ArrayList<>();

    private WallFinish() {}

    /** For a map loaded anew: its buildings are not the ones before's. */
    static void forget() {
        known.clear();
    }

    /** The sprite the building's outside walls show on that level, or on the ground where that level has none. Null if none is found. */
    static Texture outside(IsoCell cell, IsoBuilding building, int z, int frame) {
        Texture finish = of(cell, building, z, frame).outside;
        return finish == null && z != 0 ? of(cell, building, 0, frame).outside : finish;
    }

    /**
     * The plain wall most of the building's south face shows on that level, for a wall on a
     * north edge, or most of its east face, for one on a west edge. Null if that face has none.
     */
    static Facing facing(IsoCell cell, IsoBuilding building, int z, boolean north, int frame) {
        Level level = of(cell, building, z, frame);
        return north ? level.south : level.east;
    }

    /** The sprite the room's walls show, or null. */
    static Texture room(IsoCell cell, IsoBuilding building, int z, long room, int frame) {
        return of(cell, building, z, frame).rooms.get(room);
    }

    private static Level of(IsoCell cell, IsoBuilding building, int z, int frame) {
        if (known.size() > MOST_BUILDINGS) {
            known.clear();
        }
        Level[] levels = known.computeIfAbsent(building, unused -> new Level[LEVELS]);
        int slot = z & (LEVELS - 1);
        Level level = levels[slot];
        if (level == null || frame - level.stamp >= (level.whole ? KEPT : RETRIED)) {
            level = read(cell, building, z);
            level.stamp = frame;
            levels[slot] = level;
        }
        return level;
    }

    private static Level read(IsoCell cell, IsoBuilding building, int z) {
        Level level = new Level();
        BuildingDef def = building.getDef();
        if (def.getW() > WIDEST || def.getH() > WIDEST) {
            return level;
        }
        shown.clear();
        shownBy.clear();
        Tally outside = new Tally(), southFace = new Tally(), eastFace = new Tally();
        HashMap<Long, Tally> rooms = new HashMap<>();
        for (int x = def.getX(); x < def.getX() + def.getW(); x++) {
            for (int y = def.getY(); y < def.getY() + def.getH(); y++) {
                IsoGridSquare square = cell.getGridSquare(x, y, z);
                if (square == null) {
                    // Above the ground a square of air is no square. On the ground it is map not loaded yet.
                    level.whole &= cell.getGridSquare(x, y, 0) != null;
                    continue;
                }
                if (square.getBuilding() != building) {
                    continue;
                }
                Tally room = rooms.computeIfAbsent(square.getRoomID(), unused -> new Tally());
                vote(room, square, true);
                vote(room, square, false);
                IsoGridSquare south = cell.getGridSquare(x, y + 1, z), east = cell.getGridSquare(x + 1, y, z);
                level.whole &= z != 0 || south != null && east != null;
                if (south != null && south.getRoomID() == -1L) {
                    vote(outside, south, true);
                    vote(southFace, south, true);
                }
                if (east != null && east.getRoomID() == -1L) {
                    vote(outside, east, false);
                    vote(eastFace, east, false);
                }
            }
        }
        level.outside = commonest(outside);
        level.south = plainest(southFace);
        level.east = plainest(eastFace);
        for (var room : rooms.entrySet()) {
            Texture finish = commonest(room.getValue());
            if (finish != null) {
                level.rooms.put(room.getKey(), finish);
            }
        }
        return level;
    }

    private static void vote(Tally tally, IsoGridSquare square, boolean north) {
        IsoObject wall = WorldScene.wallOn(square, north);
        if (wall == null) {
            return;
        }
        Texture texture = wall.getSprite().getTextureForCurrentFrame(wall.getDir());
        int index = shown.indexOf(texture);
        if (index < 0) {
            index = shown.size();
            shown.add(texture);
            shownBy.add(wall.getSprite());
        }
        tally.any.add(index);
        PropertyContainer properties = wall.getSprite().getProperties();
        // A wall with a door or a window in it is mostly frame and glass, not finish.
        if (!properties.has(IsoFlagType.DoorWallN) && !properties.has(IsoFlagType.DoorWallW)
                && !properties.has(IsoFlagType.WindowN) && !properties.has(IsoFlagType.WindowW)) {
            tally.plain.add(index);
        }
    }

    private static Facing plainest(Tally tally) {
        int index = tally.plain.commonest();
        return index < 0 ? null : new Facing(shownBy.get(index), shown.get(index));
    }

    private static Texture commonest(Tally tally) {
        int index = tally.plain.isEmpty() ? tally.any.commonest() : tally.plain.commonest();
        return index < 0 ? null : shown.get(index);
    }
}
