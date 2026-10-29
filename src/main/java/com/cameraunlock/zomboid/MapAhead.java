package com.cameraunlock.zomboid;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import zombie.core.properties.PropertyContainer;
import zombie.iso.BuildingDef;
import zombie.iso.IsoLot;
import zombie.iso.IsoWorld;
import zombie.iso.LotHeader;
import zombie.iso.RoomDef;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteManager;

/**
 * The ground beyond the loaded map, as the game's map has it, for the heights of the land
 * (Terrain). A chunk's heights go by what lies up to Terrain.AROUND tiles round it, and the
 * game keeps the map loaded some seventy tiles each way, so the chunks in the outer half of
 * what is loaded had heights worked out with open, dry, unbuilt ground beyond the edge, and
 * stepped to their true heights as the player went on and the map loaded more. Read ahead from
 * the lotpacks the game loads its chunks from, they have their true heights when they first
 * show.
 *
 * It is the map as shipped, not the save: where a save has something built, burned or moved,
 * what is read here is not what the game loads. So what is read here is a guess: Terrain works
 * a chunk's heights out from it while the ground is not loaded, as it did from nothing, and
 * again from the ground once it is, and where the two agree they come out the same.
 *
 * The lotpacks are read on a thread of their own. The sprites and the buildings are the
 * game's, and are asked on the logic thread.
 */
final class MapAhead {
    /**
     * Chunks beyond the loaded map each way that are read: as far as a chunk at its edge looks
     * for what is built round it (Terrain.AROUND, 6 chunks), and from the middle of a thing it
     * finds there for water (5 more), with a chunk to spare.
     */
    static final int BEYOND = 12;
    private static final int CHUNK = Terrain.CHUNK_TILES;
    private static final int SQUARES = CHUNK * CHUNK;
    private static final int CELL_CHUNKS = FarCells.CELL / CHUNK;
    /** The longest a frame spends taking in what the thread has read, in nanoseconds. */
    private static final long TAKEN_NANOS_A_FRAME = 1_000_000L;

    /** What a tile is, as far as the heights go by it: see traits. */
    static final int FLOOR = 1, WATER = 1 << 1, NATURAL_NAME = 1 << 2, STREET_NAME = 1 << 3, ROAD_04 = 1 << 4, STAIR = 1 << 5,
            ROOF = 1 << 6, PARASOL = 1 << 7, WALL_N = 1 << 8, WALL_W = 1 << 9, BUILT_UP = 1 << 10, BLOCKS_RAIN = 1 << 11, OVERHEAD = 1 << 12;
    /** In Raw.above: some tile that is no overhead line stands on a level above. */
    static final int ANY = 1 << 13;
    /** Where in a tile's traits what it is to level ground is (Grounds.of), four bits from here. */
    static final int LEVEL_SHIFT = 14;
    /** Where in a tile's traits the line it paints on a road is (Lanes.mark), eight bits from here. */
    static final int MARK_SHIFT = 18;
    /**
     * How the game's loader lays a tile on a square (CellLoader.DoTileObjectCreation). A
     * solidfloor tile flagged diamondFloor and not transparentFloor, laid where the square's
     * floor (its first solidfloor object) is flagged diamondFloor, becomes that floor: it takes
     * the place of the floor's sprite and of the overlays laid on it. A FloorOverlay is laid on
     * the square's floor, and on a square with no floor is not laid at all.
     */
    static final int DIAMOND = 1 << 26, SEE_THROUGH = 1 << 27, OVERLAY = 1 << 28;
    /** Of what the overlays laid on a floor are, the lines on a road: Terrain.marks reads them off the floor. */
    private static final int MARKS = 0xFF << MARK_SHIFT;

    /** Of Guess.stands: a stair or a roof tile, a parasol's canopy, a building's own wall along its south or east side. */
    static final byte STAIR_OR_ROOF = 1, CANOPY = 2, WALLED = 4;

    /**
     * A chunk's squares as the game lays its lotpack's tiles (see DIAMOND), x first: the traits of
     * the floor on the ground (0 where there is none), of every tile laid on the ground and the
     * lines of the overlays laid on its floor, and of every tile laid on the levels above that is
     * no overhead line (with ANY), and the levels the lotpack has tiles on, a bit each from 1;
     * and what the square is to level ground (Grounds), by the tiles laid on the ground or the
     * level over it.
     */
    record Raw(int generation, int wx, int wy, int[] floor, int[] ground, int[] above, int[] levels, byte[] level) {}

    /**
     * What Terrain asks of a square of a chunk that is not loaded, x first. Whether something
     * stands on the ground with Own.furniture as it is set, and what is over the square, are
     * Terrain's to make of these.
     */
    static final class Guess {
        final int wx, wy;
        final byte[] kind = new byte[SQUARES];
        final boolean[] footway = new boolean[SQUARES];
        /** The lines painted on a road's square, as Lanes.mark gives them, as Terrain.Kinds keeps them. */
        final byte[] marks = new byte[SQUARES];
        /** The middle of the building whose pad the square is part of (Terrain.footingOf), as Footings.key packs it, or Footings.NONE. */
        final long[] house = new long[SQUARES];
        final byte[] stands = new byte[SQUARES];
        /** Footings.BUILT, HUNG or OPEN, as Terrain.above has it. */
        final byte[] above = new byte[SQUARES];
        /** What the square is to level ground, as Terrain.groundOf has it. */
        final byte[] level = new byte[SQUARES];

        Guess(int wx, int wy) {
            this.wx = wx;
            this.wy = wy;
        }
    }

    /** The buildings of the map: the middle of the one with a room at (x, y, z), as Footings.key packs it, or Footings.NONE. */
    interface Rooms {
        long house(int x, int y, int z);
    }

    private MapAhead() {}

    /** What a tile is, as Terrain.kind, Terrain.stands and Terrain.above take it. Logic thread. */
    static int traits(IsoSprite sprite) {
        PropertyContainer flags = sprite.getProperties();
        int traits = 0;
        if (flags.has(IsoFlagType.solidfloor)) {
            traits |= FLOOR | BLOCKS_RAIN;
        }
        if (flags.has(IsoFlagType.BlockRain)) {
            traits |= BLOCKS_RAIN;
        }
        if (flags.has(IsoFlagType.water)) {
            traits |= WATER;
        }
        traits |= flags.has(IsoFlagType.diamondFloor) ? DIAMOND : 0;
        traits |= flags.has(IsoFlagType.transparentFloor) ? SEE_THROUGH : 0;
        traits |= flags.has(IsoFlagType.FloorOverlay) ? OVERLAY : 0;
        String name = sprite.getName();
        if (name != null) {
            traits |= name.startsWith("blends_natural") ? NATURAL_NAME : name.startsWith("blends_street") ? STREET_NAME : 0;
            traits |= name.startsWith(Footings.OVERHEAD_LINES) ? OVERHEAD : 0;
            traits |= Grounds.of(name) << LEVEL_SHIFT;
            traits |= Lanes.mark(name) << MARK_SHIFT;
        }
        if ("Road_04".equals(flags.get("FloorMaterial"))) {
            traits |= ROAD_04;
        }
        IsoObjectType type = sprite.getType();
        boolean stair = Terrain.isStair(type), roof = Terrain.isRoof(type);
        traits |= stair ? STAIR : 0;
        traits |= roof ? Parasols.quarter(sprite) != Parasols.NONE ? PARASOL : ROOF : 0;
        boolean wallN = flags.has(IsoFlagType.WallN), wallW = flags.has(IsoFlagType.WallW), wallNW = flags.has(IsoFlagType.WallNW);
        boolean doorN = flags.has(IsoFlagType.DoorWallN), doorW = flags.has(IsoFlagType.DoorWallW);
        boolean windowN = flags.has(IsoFlagType.WindowN), windowW = flags.has(IsoFlagType.WindowW);
        traits |= wallN || wallNW || doorN || windowN ? WALL_N : 0;
        traits |= wallW || wallNW || doorW || windowW ? WALL_W : 0;
        if (stair || roof || wallN || wallW || wallNW || flags.has(IsoFlagType.WallSE) || doorN || doorW || windowN || windowW) {
            traits |= BUILT_UP;
        }
        return traits;
    }

    /**
     * Reads one chunk of a lotpack into what Raw keeps, as the game reads a chunk when it loads
     * it (IsoLot.load). Any thread.
     *
     * @param pack     the whole lotpack
     * @param traits   for each tile of the cell's header, its traits
     * @param minLevel the header's lowest level
     * @param maxLevel the header's highest level
     * @param cx       the chunk within the cell, x
     * @param cy       the chunk within the cell, y
     */
    static Raw read(int generation, ByteBuffer pack, int[] traits, int minLevel, int maxLevel, int wx, int wy, int cx, int cy) {
        Lotpack in = new Lotpack(pack);
        int[] floor = new int[SQUARES], ground = new int[SQUARES], above = new int[SQUARES], levels = new int[SQUARES];
        byte[] level = new byte[SQUARES];
        int from = Math.max(minLevel, Lotpack.LOWEST), to = Math.min(maxLevel, Lotpack.HIGHEST);
        in.chunk(cx, cy);
        for (int z = from; z <= to; z++) {
            for (int x = 0; x < CHUNK; x++) {
                for (int y = 0; y < CHUNK; y++) {
                    int tiles = in.square();
                    if (tiles == 0) {
                        continue;
                    }
                    int at = x * CHUNK + y;
                    // As the game lays them (see DIAMOND): the floor, the overlays laid on it, the other tiles, those that are no overhead line, and the most the others are to level ground.
                    int laid = 0, overlaid = 0, others = 0, standing = 0, grounds = 0;
                    for (int i = 0; i < tiles; i++) {
                        int of = traits[in.tile(traits.length)];
                        if ((of & (FLOOR | DIAMOND | SEE_THROUGH)) == (FLOOR | DIAMOND) && (laid & DIAMOND) != 0) {
                            laid = of;
                            overlaid = 0;
                        } else if ((of & OVERLAY) != 0) {
                            overlaid |= laid != 0 ? of : 0;
                        } else if ((of & FLOOR) != 0 && laid == 0) {
                            laid = of;
                        } else {
                            others |= of;
                            standing |= (of & OVERHEAD) == 0 ? of | ANY : 0;
                            grounds = Math.max(grounds, of >>> LEVEL_SHIFT & 15);
                        }
                    }
                    if (z == 0 || z == 1) {
                        level[at] = (byte) Math.max(level[at], Math.max(grounds, laid >>> LEVEL_SHIFT & 15));
                    }
                    if (z == 0) {
                        floor[at] = laid;
                        ground[at] = laid | others | overlaid & MARKS;
                    } else if (z > 0) {
                        levels[at] |= 1 << z;
                        above[at] |= standing | (laid != 0 ? laid | ANY : 0);
                    }
                }
            }
        }
        return new Raw(generation, wx, wy, floor, ground, above, levels, level);
    }

    /** What the heights take of a chunk the lotpack has, with the buildings of the map. Logic thread. */
    static Guess guess(Raw raw, Rooms rooms) {
        Guess guess = new Guess(raw.wx, raw.wy);
        for (int i = 0; i < CHUNK; i++) {
            for (int j = 0; j < CHUNK; j++) {
                int at = i * CHUNK + j, x = raw.wx * CHUNK + i, y = raw.wy * CHUNK + j;
                long house = rooms.house(x, y, 0);
                // A basement's pad reaches out under the yard over it, as Terrain.footingOf has it.
                guess.house[at] = house != Footings.NONE ? house : rooms.house(x, y, -1);
                // The game's own rule for outside (IsoGridSquare.RecalcProperties): no room, and nothing over it that keeps the rain off.
                boolean outside = house == Footings.NONE && (raw.above[at] & BLOCKS_RAIN) == 0;
                int floor = raw.floor[at];
                byte kind = floor == 0 ? Terrain.MADE
                        : (floor & WATER) != 0 ? Terrain.WATER
                        : !outside ? Terrain.MADE
                        : (floor & NATURAL_NAME) != 0 ? Terrain.NATURAL : (floor & STREET_NAME) != 0 ? Terrain.STREET : Terrain.MADE;
                guess.kind[at] = kind;
                guess.footway[at] = kind == Terrain.MADE || kind == Terrain.STREET && (floor & ROAD_04) != 0;
                int ground = raw.ground[at];
                guess.marks[at] = kind == Terrain.STREET ? (byte) (ground >>> MARK_SHIFT) : 0;
                byte stands = (ground & (STAIR | ROOF)) != 0 ? STAIR_OR_ROOF : 0;
                stands |= (ground & PARASOL) != 0 ? CANOPY : 0;
                if ((ground & WALL_N) != 0 && rooms.house(x, y - 1, 0) != Footings.NONE || (ground & WALL_W) != 0 && rooms.house(x - 1, y, 0) != Footings.NONE) {
                    stands |= WALLED;
                }
                guess.stands[at] = stands;
                int over = raw.above[at];
                boolean built = (over & (FLOOR | BUILT_UP)) != 0;
                for (int z = 1; z <= Lotpack.HIGHEST && !built; z++) {
                    built = (raw.levels[at] & 1 << z) != 0 && rooms.house(x, y, z) != Footings.NONE;
                }
                guess.above[at] = built ? Footings.BUILT : (over & ANY) != 0 ? Footings.HUNG : Footings.OPEN;
                guess.level[at] = raw.level[at];
            }
        }
        return guess;
    }

    /** The buildings of the game's map. */
    private static final Rooms ROOMS = (x, y, z) -> {
        RoomDef room = IsoWorld.instance.getMetaGrid().getRoomAt(x, y, z);
        BuildingDef def = room == null ? null : room.getBuilding();
        return def == null ? Footings.NONE : Footings.key(def.getX() + def.getW() / 2f, def.getY() + def.getH() / 2f);
    };

    /** A cell of the map as the thread reads it: its lotpack and its header's tiles' traits. */
    private record Cell(Path pack, int[] traits, int minLevel, int maxLevel) {}

    /**
     * The logic thread's: the guesses by chunk, at ((wx, wy) mod TABLE_SIDE) * View.COUNT and the
     * ways after it. A view's window is no wider than the table (side() refuses one that is), so
     * its chunks never share a place, and each view's take one way of a place at most.
     */
    private static final int TABLE_SIDE = 128;
    private static final Guess[] guesses = new Guess[TABLE_SIDE * TABLE_SIDE * View.COUNT];
    /** The chunks asked to be read and not since let go of, as key packs them, in places as guesses has them: NONE in a way that holds none. */
    private static final long[] asked = new long[TABLE_SIDE * TABLE_SIDE * View.COUNT];
    private static final long NONE = Long.MIN_VALUE;

    static {
        Arrays.fill(asked, NONE);
    }

    /** Each cell's lotpack and traits, or null for a cell the map has no lotpack for. */
    private static final HashMap<Long, Cell> cells = new HashMap<>();
    private static final HashMap<String, Integer> traitsByName = new HashMap<>();
    /** Each view's chunk the window was last read round, and the windows to keep. */
    private static final long[] roundOf = new long[View.COUNT];
    private static final int[] windowX = new int[View.COUNT], windowY = new int[View.COUNT], windowSide = new int[View.COUNT];
    private static final boolean[] windowed = new boolean[View.COUNT];
    private static int version;
    /** Another for each map loaded, so that what the thread read of one is not taken for another's. */
    private static volatile int generation;
    private static final ConcurrentLinkedQueue<Raw> read = new ConcurrentLinkedQueue<>();
    private static volatile String failure;
    private static volatile Throwable failureCause;
    private static boolean failureTold;

    /** The thread's own. */
    private static final LinkedHashMap<Path, MappedByteBuffer> open = new LinkedHashMap<>(16, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<Path, MappedByteBuffer> eldest) {
            return size() > 16;
        }
    };

    private static final ThreadPoolExecutor READER = reader();

    private static ThreadPoolExecutor reader() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), work -> {
            Thread thread = new Thread(work, "CameraUnlock map ahead");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    /** A number that is another whenever what has been read ahead is. Logic thread. */
    static int version() {
        return version;
    }

    /** What has been read of a chunk not loaded, or null. Logic thread. */
    static Guess guessed(int wx, int wy) {
        int slot = slot(wx, wy);
        for (int way = 0; way < View.COUNT; way++) {
            Guess guess = guesses[slot + way];
            if (guess != null && guess.wx == wx && guess.wy == wy) {
                return guess;
            }
        }
        return null;
    }

    private static int slot(int wx, int wy) {
        return ((wx & TABLE_SIDE - 1) * TABLE_SIDE + (wy & TABLE_SIDE - 1)) * View.COUNT;
    }

    /** Keeps a guess in place of its chunk's, or of a chunk no view's window holds. */
    private static void keep(Guess guess) {
        int slot = slot(guess.wx, guess.wy);
        for (int way = 0; way < View.COUNT; way++) {
            Guess held = guesses[slot + way];
            if (held == null || held.wx == guess.wx && held.wy == guess.wy || !inAnyWindow(held.wx, held.wy)) {
                guesses[slot + way] = guess;
                return;
            }
        }
        throw new IllegalStateException("no place for the guess of chunk " + guess.wx + ", " + guess.wy + ": every way holds a chunk some view's window has");
    }

    /** For tests: has the guess be what is read of its chunk, with no view's window kept. */
    static void put(Guess guess) {
        Arrays.fill(windowed, false);
        keep(guess);
        version++;
    }

    /** For a map loaded anew. Logic thread. */
    static void forget() {
        generation++;
        Arrays.fill(guesses, null);
        Arrays.fill(asked, NONE);
        cells.clear();
        traitsByName.clear();
        read.clear();
        Arrays.fill(windowed, false);
        version++;
    }

    /**
     * Logic thread, as a view is built with its player over the tile (x, y): has the chunks
     * within the loaded map and BEYOND chunks past it each way read, and takes in what has
     * been read since.
     */
    static void tend(int view, int x, int y) {
        if (failure != null) {
            if (!failureTold) {
                failureTold = true;
                Log.error("map ahead: " + failure + ". Nothing more is read ahead in this session: the land beyond the loaded map takes its heights as the map loads", failureCause);
            }
            return;
        }
        int wx = Math.floorDiv(x, CHUNK), wy = Math.floorDiv(y, CHUNK);
        long round = key(wx, wy);
        if (!windowed[view] || roundOf[view] != round || windowSide[view] != side()) {
            boolean had = windowed[view];
            int wasX = windowX[view], wasY = windowY[view], wasSide = windowSide[view];
            windowed[view] = true;
            roundOf[view] = round;
            windowSide[view] = side();
            windowX[view] = wx - side() / 2;
            windowY[view] = wy - side() / 2;
            ask(view);
            if (had) {
                forgetOutside(wasX, wasY, wasSide);
            }
        }
        take();
    }

    /** The chunks a side of a view's window, which must be no wider than the table (see TABLE_SIDE). */
    private static int side() {
        int side = zombie.iso.IsoChunkMap.chunkGridWidth + 2 * BEYOND;
        if (side > TABLE_SIDE) {
            throw new IllegalStateException("map ahead: a window " + side + " chunks a side, the game's loaded map of " + zombie.iso.IsoChunkMap.chunkGridWidth
                    + " and " + BEYOND + " beyond it each way, is wider than the table of " + TABLE_SIDE + ": two of its chunks would share a place");
        }
        return side;
    }

    /** A chunk to read, and the cell of the map it is in. */
    private record Job(int wx, int wy, Cell cell) {}

    private static void ask(int view) {
        ArrayList<Job> jobs = new ArrayList<>();
        int side = windowSide[view];
        int midX = windowX[view] + side / 2, midY = windowY[view] + side / 2;
        ringsOut(midX, midY, side / 2, (wx, wy) -> {
            Cell cell = noteAsked(wx, wy) ? cell(Math.floorDiv(wx, CELL_CHUNKS), Math.floorDiv(wy, CELL_CHUNKS)) : null;
            if (cell != null) {
                jobs.add(new Job(wx, wy, cell));
            }
        });
        if (jobs.isEmpty()) {
            return;
        }
        int asOf = generation;
        READER.execute(() -> {
            try {
                for (Job job : jobs) {
                    if (generation != asOf) {
                        return;
                    }
                    Cell cell = job.cell();
                    int cx = job.wx() - Math.floorDiv(job.wx(), CELL_CHUNKS) * CELL_CHUNKS, cy = job.wy() - Math.floorDiv(job.wy(), CELL_CHUNKS) * CELL_CHUNKS;
                    read.add(read(asOf, mapped(cell.pack()), cell.traits(), cell.minLevel(), cell.maxLevel(), job.wx(), job.wy(), cx, cy));
                }
            } catch (Throwable thrown) {
                failureCause = thrown;
                failure = "the map could not be read ahead: " + thrown;
            }
        });
    }

    /** A chunk, by where it is on the map. */
    interface ChunkAt {
        void at(int wx, int wy);
    }

    /** Gives each the chunks round (midX, midY) out to rings chunks from it, nearest first: ring by ring, each walked along its edge alone, x before y. */
    static void ringsOut(int midX, int midY, int rings, ChunkAt each) {
        for (int ring = 0; ring <= rings; ring++) {
            for (int wx = midX - ring; wx <= midX + ring; wx++) {
                boolean end = wx == midX - ring || wx == midX + ring;
                for (int wy = midY - ring; wy <= midY + ring; wy += end || ring == 0 ? 1 : 2 * ring) {
                    each.at(wx, wy);
                }
            }
        }
    }

    /** The thread's: the lotpack, mapped once for as long as it is among the last few read. */
    private static MappedByteBuffer mapped(Path pack) throws IOException {
        MappedByteBuffer buffer = open.get(pack);
        if (buffer == null) {
            try (FileChannel channel = FileChannel.open(pack, StandardOpenOption.READ)) {
                buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
            }
            open.put(pack, buffer);
        }
        return buffer;
    }

    /** The cell's lotpack and traits, or null where the map has none. Logic thread. */
    private static Cell cell(int cellX, int cellY) {
        long key = key(cellX, cellY);
        if (cells.containsKey(key)) {
            return cells.get(key);
        }
        // As FarTrees.lot reads them: the header the game keeps for the cell, and the lotpack its loader opens.
        LotHeader header = IsoLot.getHeader(cellX, cellY);
        String pack = IsoLot.InfoFileNames.get("world_" + cellX + "_" + cellY + ".lotpack");
        Cell cell = null;
        if (header != null && pack != null) {
            int[] traits = new int[header.tilesUsed.size()];
            for (int i = 0; i < traits.length; i++) {
                traits[i] = traitsOf(header.tilesUsed.get(i));
            }
            cell = new Cell(Path.of(pack), traits, header.minLevel, header.maxLevel);
        }
        cells.put(key, cell);
        return cell;
    }

    private static int traitsOf(String tile) {
        Integer known = traitsByName.get(tile);
        if (known == null) {
            IsoSprite sprite = IsoSpriteManager.instance.namedMap.get(tile);
            // A tile no sprite of the game's is named for is put on no square when the chunk loads.
            known = sprite == null ? 0 : traits(sprite);
            traitsByName.put(tile, known);
        }
        return known;
    }

    /** Takes in what the thread has read, for no longer than TAKEN_NANOS_A_FRAME. */
    private static void take() {
        long began = System.nanoTime();
        boolean took = false;
        Raw raw;
        while (System.nanoTime() - began < TAKEN_NANOS_A_FRAME && (raw = read.poll()) != null) {
            if (raw.generation() != generation || !asked(raw.wx(), raw.wy())) {
                continue;
            }
            keep(guess(raw, ROOMS));
            took = true;
        }
        if (took) {
            version++;
        }
    }

    /** Whether the chunk has been asked to be read and not since let go of. */
    private static boolean asked(int wx, int wy) {
        int slot = slot(wx, wy);
        long chunk = key(wx, wy);
        for (int way = 0; way < View.COUNT; way++) {
            if (asked[slot + way] == chunk) {
                return true;
            }
        }
        return false;
    }

    /** Notes the chunk as asked to be read, in place of one no view's window holds, unless it is already: whether it was not. */
    private static boolean noteAsked(int wx, int wy) {
        if (asked(wx, wy)) {
            return false;
        }
        int slot = slot(wx, wy);
        for (int way = 0; way < View.COUNT; way++) {
            long held = asked[slot + way];
            if (held == NONE || !inAnyWindow((int) (held >> 32), (int) held)) {
                asked[slot + way] = key(wx, wy);
                return true;
            }
        }
        throw new IllegalStateException("no place to note chunk " + wx + ", " + wy + " asked: every way holds a chunk some view's window has");
    }

    /** Lets go of what was asked in a view's window from (fromX, fromY), side chunks a side, and lies outside every view's window now. */
    private static void forgetOutside(int fromX, int fromY, int side) {
        for (int wx = fromX; wx < fromX + side; wx++) {
            for (int wy = fromY; wy < fromY + side; wy++) {
                if (inAnyWindow(wx, wy)) {
                    continue;
                }
                int slot = slot(wx, wy);
                long chunk = key(wx, wy);
                for (int way = 0; way < View.COUNT; way++) {
                    if (asked[slot + way] == chunk) {
                        asked[slot + way] = NONE;
                    }
                }
            }
        }
    }

    private static boolean inAnyWindow(int wx, int wy) {
        for (int view = 0; view < View.COUNT; view++) {
            if (windowed[view] && wx >= windowX[view] && wy >= windowY[view] && wx < windowX[view] + windowSide[view] && wy < windowY[view] + windowSide[view]) {
                return true;
            }
        }
        return false;
    }

    private static long key(int x, int y) {
        return (long) x << 32 | y & 0xFFFFFFFFL;
    }
}
