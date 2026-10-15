package com.cameraunlock.zomboid;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.WeakHashMap;
import zombie.core.properties.PropertyContainer;
import zombie.core.textures.Texture;
import zombie.iso.BuildingDef;
import zombie.iso.IsoCell;
import zombie.iso.IsoDirections;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.areas.IsoBuilding;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteManager;

/**
 * What a building is finished in outside, and each of its rooms inside.
 *
 * The walls that show a building's outside are those on its south and east faces, which stand
 * on the squares just outside it and are painted for the side out of doors. The walls on its
 * north and west carry the wallpaper of the rooms behind them, and their far side is drawn
 * with a sprite taken from the other two faces.
 *
 * Not one sprite for the whole building. Two buildings in five are more than one thing
 * outside: a brick block with a boarded porch and a boarded garage on its side, a shop with a
 * brick front and a tin store behind. Given the sprite most of the south face shows, the back
 * of the garage was brick, and the west side of the block was boards downstairs and brick
 * upstairs. The parts of such a building are its rooms, the garage being one. So each room has
 * an outside of its own: what most of its own south and east walls show, and for a room in the
 * north-west corner, which has neither, what the nearest such wall shows looking through the
 * building from each of its north walls southward and each of its west walls eastward. A wall
 * on the north needs that finish as a sprite of a north edge and one on the west as a sprite
 * of a west edge, whichever face it was read from: the game's sheets hold a finish as a west
 * wall, then a north wall, then the corner of the two, and where the building itself does
 * not show the one wanted it is taken from the sheet.
 *
 * A room's finish inside is the sprite most of the walls on its own squares show, which are
 * painted for the room. All of it is read once for a level of a building and kept, since
 * every wall of it asks each time its chunk is built, and read again every so often, since
 * walls are built and knocked down.
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
        /** Whether tend has the level in its list. */
        boolean listed;
        Texture outside;
        Facing south, east;
        final HashMap<Long, Texture> rooms = new HashMap<>();
        /** What the outside of each room's walls shows, on a north edge and on a west edge. One of the two may be null, never both. */
        final HashMap<Long, Facing[]> outsides = new HashMap<>();
        /** For tend: what ChunkMarks had counted for the building's chunks and which chunks the map had loaded when the level was read. */
        int structures, loaded;

        /** Everything the level came out as, in one number. */
        long print() {
            long print = mix(mix(mix(mix(0x9E3779B97F4A7C15L, whole ? 1 : 0), of(outside)), of(south)), of(east));
            // Summed, each room by itself: a map hands its rooms over in no order that can be relied on.
            long each = 0;
            for (var room : rooms.entrySet()) {
                each += mix(mix(1, room.getKey()), of(room.getValue()));
            }
            for (var room : outsides.entrySet()) {
                each += mix(mix(mix(2, room.getKey()), of(room.getValue()[NORTH])), of(room.getValue()[WEST]));
            }
            return mix(print, each);
        }

        private static long mix(long sum, long value) {
            return (sum ^ value) * 0x100000001B3L;
        }

        private static long of(Texture texture) {
            return texture == null ? 0 : texture.getID() + 1L;
        }

        private static long of(Facing facing) {
            return facing == null ? 0 : mix(of(facing.texture), System.identityHashCode(facing.sprite));
        }
    }

    /** A level of a building that has been read, for tend. */
    private record Read(WeakReference<IsoBuilding> building, int z) {}

    /** Every level read while the game tells of its changes: see tend. */
    private static final ArrayList<Read> tendedLevels = new ArrayList<>();
    private static int tended, loadedNow;
    private static final int TENDED_A_FRAME = 4;

    /**
     * Logic thread, once a frame while the game tells which chunks it changes (ChunkMarks).
     * A level's finishes are otherwise read again every so many frames by whichever wall next
     * asks, and a chunk nothing has been said of asks for nothing. So a few levels a frame
     * are looked at here in turn: one whose building's chunks the game has marked for an
     * object since it was read is read again, and so is one read while part of the building
     * was not loaded, once the map has loaded more. Where it comes out otherwise the chunks
     * the building stands on are told. One neither due nor waiting to be is kept as fresh as
     * if it had been read.
     */
    static void tend(IsoCell cell, int frame, int loaded) {
        loadedNow = loaded;
        for (int n = 0; n < TENDED_A_FRAME && !tendedLevels.isEmpty(); n++) {
            if (tended >= tendedLevels.size()) {
                tended = 0;
            }
            Read one = tendedLevels.get(tended);
            IsoBuilding building = one.building.get();
            Level[] levels = building == null ? null : known.get(building);
            Level level = levels == null ? null : levels[one.z & (LEVELS - 1)];
            if (level == null) {
                tendedLevels.remove(tended);
                continue;
            }
            tended++;
            BuildingDef def = building.getDef();
            // One read while part of the building was not loaded waits out its frames from when it was read, however often it comes round here.
            boolean waits = !level.whole && level.loaded != loaded;
            boolean due = level.whole ? structuresOn(def) != level.structures : waits && frame - level.stamp >= RETRIED;
            if (!due) {
                if (!waits) {
                    level.stamp = frame;
                }
                continue;
            }
            Level fresh = read(cell, building, one.z);
            fresh.listed = true;
            fresh.stamp = frame;
            levels[one.z & (LEVELS - 1)] = fresh;
            if (fresh.print() != level.print()) {
                ChunkMarks.tell(Math.floorDiv(def.getX() - 1, WorldScene.CHUNK_TILES), Math.floorDiv(def.getY() - 1, WorldScene.CHUNK_TILES),
                        Math.floorDiv(def.getX() + def.getW(), WorldScene.CHUNK_TILES), Math.floorDiv(def.getY() + def.getH(), WorldScene.CHUNK_TILES));
            }
        }
    }

    /** The marks on the chunks a building's walls stand on: its own squares and the row of squares outside each face. */
    private static int structuresOn(BuildingDef def) {
        return ChunkMarks.structuresIn(Math.floorDiv(def.getX() - 1, WorldScene.CHUNK_TILES), Math.floorDiv(def.getY() - 1, WorldScene.CHUNK_TILES),
                Math.floorDiv(def.getX() + def.getW(), WorldScene.CHUNK_TILES), Math.floorDiv(def.getY() + def.getH(), WorldScene.CHUNK_TILES));
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
    /** The finishes met on the level being read, each named as its west wall is, */
    private static final ArrayList<String> styles = new ArrayList<>();
    /** and for each the wall of it met on a north edge, on a west edge, and as the corner of the two. */
    private static final ArrayList<Facing[]> styled = new ArrayList<>();
    /** Which of them the wall along the south and along the east of a square of the building shows. */
    private static final HashMap<Long, Integer> southOf = new HashMap<>(), eastOf = new HashMap<>();
    private static final int NORTH = 0, WEST = 1, CORNER = 2;

    private WallFinish() {}

    /** For a map loaded anew: its buildings are not the ones before's. */
    static void forget() {
        known.clear();
        tendedLevels.clear();
    }

    /**
     * The sprite the outside of that room's walls shows. For a room none could be read for, the
     * one most of the building's outside walls show on that level, or on the ground where that
     * level has none. Null if none is found.
     */
    static Texture outside(IsoCell cell, IsoBuilding building, int z, long room, int frame) {
        Level level = of(cell, building, z, frame);
        Facing[] own = level.outsides.get(room);
        if (own != null) {
            return (own[NORTH] != null ? own[NORTH] : own[WEST]).texture;
        }
        return level.outside == null && z != 0 ? of(cell, building, 0, frame).outside : level.outside;
    }

    /**
     * The plain wall the outside of that room shows, as a wall on a north edge or as one on a
     * west edge. Null where its finish has no such wall. For a room none could be read for, the
     * plain wall most of the building's south face shows on that level, for a north edge, or
     * most of its east face, and null if that face has none.
     */
    static Facing facing(IsoCell cell, IsoBuilding building, int z, boolean north, long room, int frame) {
        Level level = of(cell, building, z, frame);
        Facing[] own = level.outsides.get(room);
        if (own != null) {
            return own[north ? NORTH : WEST];
        }
        return north ? level.south : level.east;
    }

    /** The sprite the room's walls show, or null. */
    static Texture room(IsoCell cell, IsoBuilding building, int z, long room, int frame) {
        return of(cell, building, z, frame).rooms.get(room);
    }

    private static Level of(IsoCell cell, IsoBuilding building, int z, int frame) {
        if (known.size() > MOST_BUILDINGS) {
            known.clear();
            // Every level is read anew from here and lists itself again: one left listed would be tended twice.
            tendedLevels.clear();
        }
        Level[] levels = known.computeIfAbsent(building, unused -> new Level[LEVELS]);
        int slot = z & (LEVELS - 1);
        Level level = levels[slot];
        // With the game telling, a level is read again by tend alone, which tells every chunk the building stands on
        // when it comes out otherwise: a reading made here is for the one wall that asked.
        if (level == null || !ChunkMarks.live && frame - level.stamp >= (level.whole ? KEPT : RETRIED)) {
            boolean listed = level != null && level.listed;
            level = read(cell, building, z);
            level.stamp = frame;
            level.listed = listed;
            levels[slot] = level;
        }
        // Whenever the game is telling, not only at a first reading: it may begin to tell after the level was read.
        if (ChunkMarks.live && !level.listed) {
            tendedLevels.add(new Read(new WeakReference<>(building), z));
            level.listed = true;
        }
        return level;
    }

    /** The square, or null for one of a chunk the map is still filling: see read. */
    private static IsoGridSquare filled(IsoGridSquare square) {
        return square != null && square.getChunk().loaded ? square : null;
    }

    private static Level read(IsoCell cell, IsoBuilding building, int z) {
        Level level = new Level();
        BuildingDef def = building.getDef();
        level.structures = structuresOn(def);
        level.loaded = loadedNow;
        if (def.getW() > WIDEST || def.getH() > WIDEST) {
            return level;
        }
        shown.clear();
        shownBy.clear();
        styles.clear();
        styled.clear();
        southOf.clear();
        eastOf.clear();
        WallSides.Outsides outsides = new WallSides.Outsides();
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
                // A chunk the map is still filling has squares with no room yet and walls not all hung: read as it
                // stands, the inside walls of such squares are counted as the building's outside, and the level is
                // kept as whole with that finish. It is not whole until the chunk is.
                if (!square.getChunk().loaded) {
                    level.whole = false;
                    continue;
                }
                if (square.getBuilding() != building) {
                    continue;
                }
                Tally room = rooms.computeIfAbsent(square.getRoomID(), unused -> new Tally());
                vote(room, square, true);
                vote(room, square, false);
                IsoGridSquare south = filled(cell.getGridSquare(x, y + 1, z)), east = filled(cell.getGridSquare(x + 1, y, z));
                // Above the ground a missing neighbour is air or map not loaded yet, which its ground square tells apart.
                level.whole &= z == 0 ? south != null && east != null
                        : filled(cell.getGridSquare(x, y + 1, 0)) != null && filled(cell.getGridSquare(x + 1, y, 0)) != null;
                if (south != null && south.getRoomID() == -1L && !papered(south, true)) {
                    vote(outside, south, true);
                    vote(southFace, south, true);
                    int style = styleOn(south, true);
                    if (style >= 0) {
                        southOf.put(at(x, y), style);
                        outsides.shows(square.getRoomID(), style);
                    }
                }
                if (east != null && east.getRoomID() == -1L && !papered(east, false)) {
                    vote(outside, east, false);
                    vote(eastFace, east, false);
                    int style = styleOn(east, false);
                    if (style >= 0) {
                        eastOf.put(at(x, y), style);
                        outsides.shows(square.getRoomID(), style);
                    }
                }
            }
        }
        for (int x = def.getX(); x < def.getX() + def.getW(); x++) {
            for (int y = def.getY(); y < def.getY() + def.getH(); y++) {
                IsoGridSquare square = cell.getGridSquare(x, y, z);
                if (square == null || square.getBuilding() != building) {
                    continue;
                }
                if (WorldScene.wallOn(square, true) != null && isOutside(cell.getGridSquare(x, y - 1, z))) {
                    look(cell, building, outsides, square.getRoomID(), x, y, z, true);
                }
                if (WorldScene.wallOn(square, false) != null && isOutside(cell.getGridSquare(x - 1, y, z))) {
                    look(cell, building, outsides, square.getRoomID(), x, y, z, false);
                }
            }
        }
        for (int style = 0; style < styles.size(); style++) {
            Facing[] walls = styled.get(style);
            for (int edge = NORTH; edge <= WEST; edge++) {
                if (walls[edge] == null) {
                    Facing sheet = fromSheet(styles.get(style), edge == NORTH);
                    walls[edge] = sheet != null ? sheet : walls[CORNER];
                }
            }
        }
        for (long room : rooms.keySet()) {
            int style = outsides.of(room);
            if (style >= 0 && (styled.get(style)[NORTH] != null || styled.get(style)[WEST] != null)) {
                level.outsides.put(room, styled.get(style));
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

    /**
     * Whether the wall on the square's north or west edge is of a sheet of inside walls. A
     * square with no room is not always out of doors: a passage or an alcove the map gives no
     * room to has papered walls, and a wall of it is no part of the building's outside. Taken
     * for one, the room beside it was pink bathroom tiles on its far side.
     */
    private static boolean papered(IsoGridSquare square, boolean north) {
        IsoObject wall = WorldScene.wallOn(square, north);
        String name = wall == null ? null : wall.getSprite().getName();
        return name != null && name.startsWith("walls_interior");
    }

    private static long at(int x, int y) {
        return (long) x << 32 | y & 0xffffffffL;
    }

    /** Above the ground a square of open air is often no square at all. */
    private static boolean isOutside(IsoGridSquare square) {
        return square == null || square.getRoomID() == -1L;
    }

    private static boolean opens(PropertyContainer properties) {
        return properties.has(IsoFlagType.DoorWallN) || properties.has(IsoFlagType.DoorWallW)
                || properties.has(IsoFlagType.WindowN) || properties.has(IsoFlagType.WindowW);
    }

    /**
     * Which finish the plain wall on the square's north or west edge shows, as a place in
     * styles, and -1 where no plain wall stands there. The wall is kept as its finish's wall
     * of that edge if it is the first met.
     */
    private static int styleOn(IsoGridSquare square, boolean north) {
        IsoObject wall = WorldScene.wallOn(square, north);
        if (wall == null || wall.getSprite().getName() == null || opens(wall.getSprite().getProperties())) {
            return -1;
        }
        IsoSprite sprite = wall.getSprite();
        PropertyContainer properties = sprite.getProperties();
        boolean corner = properties.has(IsoFlagType.WallNW), onNorth = properties.has(IsoFlagType.WallN), onWest = properties.has(IsoFlagType.WallW);
        String name = WallSides.sheetmate(sprite.getName(), corner ? -2 : onNorth ? -1 : 0);
        int style = styles.indexOf(name);
        if (style < 0) {
            style = styles.size();
            styles.add(name);
            styled.add(new Facing[3]);
        }
        int edge = corner ? CORNER : onNorth ? NORTH : onWest ? WEST : north ? NORTH : WEST;
        if (styled.get(style)[edge] == null) {
            styled.get(style)[edge] = new Facing(sprite, sprite.getTextureForCurrentFrame(wall.getDir()));
        }
        return style;
    }

    /**
     * Tells outsides what a wall on the north or west edge of the square at (x, y) has behind
     * it: the first plain wall of the building's south or east face met going through the
     * building from there, and how many squares off.
     */
    private static void look(IsoCell cell, IsoBuilding building, WallSides.Outsides outsides, long room, int x, int y, int z, boolean north) {
        for (int far = 0;; far++) {
            int atX = north ? x : x + far, atY = north ? y + far : y;
            IsoGridSquare square = cell.getGridSquare(atX, atY, z);
            if (square == null || square.getBuilding() != building) {
                return;
            }
            Integer style = (north ? southOf : eastOf).get(at(atX, atY));
            if (style != null) {
                outsides.sees(room, far, style);
                return;
            }
        }
    }

    /** The plain wall of a finish on a north or a west edge as the game's sheet has it, or null where the sheet has no such wall there. */
    private static Facing fromSheet(String style, boolean north) {
        IsoSprite sprite = IsoSpriteManager.instance.namedMap.get(north ? WallSides.sheetmate(style, 1) : style);
        if (sprite == null) {
            return null;
        }
        PropertyContainer properties = sprite.getProperties();
        if (!properties.has(north ? IsoFlagType.WallN : IsoFlagType.WallW) || opens(properties)) {
            return null;
        }
        Texture texture = sprite.getTextureForCurrentFrame(IsoDirections.N);
        return WorldScene.usable(texture) ? new Facing(sprite, texture) : null;
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
        if (!opens(properties)) {
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
