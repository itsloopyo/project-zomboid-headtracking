package com.cameraunlock.zomboid;

import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.objects.IsoCurtain;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWindow;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * The game's map as RoomSight reads it: which room each square is in, what each edge between
 * two squares does to sight as the picture draws it, and which squares have a floor. Run on
 * the logic thread, which the game changes the map on.
 *
 * An edge stops sight only where the picture draws something that does: a wall with no
 * opening in it, a shut door with no glass in it, a drawn curtain. A doorway, a window, a
 * door with glass in it, a fence, a wall the game calls see-through and a barricade (planks
 * have gaps) let sight through. A floor or a roof over a square stops it between levels.
 *
 * The openings of each chunk are kept, and found again when the game marks anything that may
 * be part of a building on the chunk or round it, when the chunk or the chunks beside it are
 * loaded again, and while the game marks nothing, every second.
 */
final class RoomMap implements RoomSight.Ground {
    private static final int SIDE = ChunkMarks.SIDE;
    private static final long REREAD_UNTOLD_NANOS = 1_000_000_000L;
    private IsoCell cell;
    private Terrain terrain;
    private final RoomSight.Openings[] kept = new RoomSight.Openings[SIDE * SIDE];
    private final RoomSight.Blocks[] keptBlocks = new RoomSight.Blocks[SIDE * SIDE];
    /** For each chunk kept, the rooms any of its squares is in, each once: the builds of the chunk go by whether those are in sight. */
    private final RoomSight.Seen[] keptRooms = new RoomSight.Seen[SIDE * SIDE];
    private final IsoChunk[] keptChunk = new IsoChunk[SIDE * SIDE], keptNorth = new IsoChunk[SIDE * SIDE], keptWest = new IsoChunk[SIDE * SIDE];
    private final int[] keptMarks = new int[SIDE * SIDE], keptLevels = new int[SIDE * SIDE];
    private final long[] keptNanos = new long[SIDE * SIDE];
    /** How many times each chunk has been read: a frame whose chunks were all read before, and are the same chunks, has the same openings. */
    private final int[] keptReadings = new int[SIDE * SIDE];
    /** Dev only: how many chunks were read for their openings since the last asking. */
    int chunksRead;

    void begin(IsoCell cell, Terrain terrain) {
        this.cell = cell;
        this.terrain = terrain;
    }

    /**
     * Reads a chunk's openings and blocks again if what they were read from may have changed,
     * and says how many times it has been read.
     *
     * @param north the chunk at y - 1 on the loaded map, or null
     * @param west  the chunk at x - 1, or null
     * @param told  the game marks what it changes: see ChunkMarks.live
     */
    int read(IsoChunk chunk, IsoChunk north, IsoChunk west, boolean told, long now) {
        int slot = ChunkMarks.slot(chunk.wx, chunk.wy);
        int marks = ChunkMarks.structuresIn(chunk.wx - 1, chunk.wy - 1, chunk.wx + 1, chunk.wy + 1);
        int levels = chunk.getMinLevel() * 1000 + chunk.getMaxLevel();
        RoomSight.Openings openings = kept[slot];
        RoomSight.Blocks blocks = keptBlocks[slot];
        if (openings == null || keptChunk[slot] != chunk || keptNorth[slot] != north || keptWest[slot] != west || keptMarks[slot] != marks
                || keptLevels[slot] != levels || !told && now - keptNanos[slot] >= REREAD_UNTOLD_NANOS) {
            if (openings == null) {
                openings = kept[slot] = new RoomSight.Openings();
                blocks = keptBlocks[slot] = new RoomSight.Blocks();
                keptRooms[slot] = new RoomSight.Seen();
            }
            openings.clear();
            blocks.clear();
            RoomSight.Seen rooms = keptRooms[slot];
            rooms.clear();
            int fromX = chunk.wx * WorldScene.CHUNK_TILES, fromY = chunk.wy * WorldScene.CHUNK_TILES;
            for (int y = fromY; y < fromY + WorldScene.CHUNK_TILES; y++) {
                blocks.scanRow(this, fromX, fromX + WorldScene.CHUNK_TILES, y);
            }
            // The gap over the highest level is to whatever is above it, which is open air.
            for (int z = chunk.getMinLevel(); z <= chunk.getMaxLevel(); z++) {
                for (int x = fromX; x < fromX + WorldScene.CHUNK_TILES; x++) {
                    for (int y = fromY; y < fromY + WorldScene.CHUNK_TILES; y++) {
                        openings.scan(this, x, y, z);
                        long room = region(x, y, z);
                        if (room != RoomSight.OUTSIDE) {
                            rooms.add(room);
                        }
                    }
                }
            }
            keptChunk[slot] = chunk;
            keptNorth[slot] = north;
            keptWest[slot] = west;
            keptMarks[slot] = marks;
            keptLevels[slot] = levels;
            keptNanos[slot] = now;
            keptReadings[slot]++;
            chunksRead++;
        }
        return keptReadings[slot];
    }

    /** Adds the openings and blocks of a chunk read lately to a frame's. */
    void addTo(IsoChunk chunk, RoomSight.Openings into, RoomSight.Blocks blocksInto) {
        int slot = ChunkMarks.slot(chunk.wx, chunk.wy);
        into.addAll(kept[slot]);
        blocksInto.addAll(keptBlocks[slot]);
    }

    /** Whether a chunk was read as it is now loaded and any of the rooms given is on it: a chunk never read was never built by sight. */
    boolean holdsAny(IsoChunk chunk, RoomSight.Seen of) {
        int slot = ChunkMarks.slot(chunk.wx, chunk.wy);
        return keptChunk[slot] == chunk && keptRooms[slot].overlaps(of);
    }

    @Override
    public long region(int x, int y, int z) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        return square == null ? RoomSight.OUTSIDE : square.getRoomID();
    }

    @Override
    public int edge(int x, int y, int z, boolean north) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        if (square == null) {
            // Walls stand on the north and west edges of the square they belong to: with no square there is none.
            return RoomSight.SEE;
        }
        IsoGridSquare beyond = north ? cell.getGridSquare(x, y - 1, z) : cell.getGridSquare(x - 1, y, z);
        boolean door = false, doorShut = false, window = false, wall = false;
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            if (object instanceof IsoDoor each && each.getNorth() == north) {
                door = true;
                doorShut |= !each.IsOpen() && (!seeThrough(each.getSprite()) || each.HasCurtains() != null && !each.isCurtainOpen());
            } else if (object instanceof IsoThumpable thumpable) {
                if (thumpable.isDoor() && thumpable.getNorth() == north) {
                    door = true;
                    doorShut |= !thumpable.IsOpen();
                } else if (north ? thumpable.isWindowN() : thumpable.isWindowW()) {
                    window = true;
                }
            } else if (object instanceof IsoWindow each && each.getNorth() == north) {
                window = true;
            }
            IsoSprite sprite = object.getSprite();
            if (sprite == null) {
                continue;
            }
            var flags = sprite.getProperties();
            if (north ? flags.has(IsoFlagType.WindowN) : flags.has(IsoFlagType.WindowW)) {
                window = true;
            }
            if (north ? flags.has(IsoFlagType.DoorWallN) : flags.has(IsoFlagType.DoorWallW)) {
                // A door frame: what is in the doorway decides.
                continue;
            }
            boolean onEdge = north ? flags.has(IsoFlagType.WallN) || flags.has(IsoFlagType.WallNW) : flags.has(IsoFlagType.WallW) || flags.has(IsoFlagType.WallNW);
            boolean seen = north ? flags.has(IsoFlagType.WallNTrans) || flags.has(IsoFlagType.transparentN) || flags.has(IsoFlagType.HoppableN) || flags.has(IsoFlagType.TallHoppableN)
                    : flags.has(IsoFlagType.WallWTrans) || flags.has(IsoFlagType.transparentW) || flags.has(IsoFlagType.HoppableW) || flags.has(IsoFlagType.TallHoppableW);
            wall |= onEdge && !seen;
        }
        if (door) {
            return doorShut || curtained(square, beyond, north) ? RoomSight.SHUT : RoomSight.SEE;
        }
        if (window) {
            return curtained(square, beyond, north) ? RoomSight.SHUT : RoomSight.SEE;
        }
        return wall ? RoomSight.SOLID : RoomSight.SEE;
    }

    /** Whether a door's picture has glass in it, which the game says of a door it lets the light of a room through. */
    private static boolean seeThrough(IsoSprite sprite) {
        return sprite != null && sprite.getProperties().has("doorTrans");
    }

    /** Whether a curtain is drawn across the edge, hung on the square's side of it or on the side beyond. */
    private static boolean curtained(IsoGridSquare square, IsoGridSquare beyond, boolean north) {
        return drawn(square, north ? IsoObjectType.curtainN : IsoObjectType.curtainW) || beyond != null && drawn(beyond, north ? IsoObjectType.curtainS : IsoObjectType.curtainE);
    }

    private static boolean drawn(IsoGridSquare square, IsoObjectType type) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i) instanceof IsoCurtain curtain && !curtain.IsOpen() && curtain.getType() == type) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean floored(int x, int y, int z) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        if (square == null) {
            return false;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite == null) {
                continue;
            }
            var flags = sprite.getProperties();
            if (flags.has(IsoFlagType.solidfloor) && !flags.has(IsoFlagType.transparentFloor) || Roofs.isTile(sprite.getType())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public long building(int x, int y, int z) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        return square == null || square.getRoomID() == RoomSight.OUTSIDE ? RoomSight.OUTSIDE : square.getBuilding().getDef().id;
    }

    @Override
    public float floorUp(float x, float y, int z) {
        return z * ViewCamera.LEVEL_HEIGHT + terrain.lift(x, y, z);
    }
}
