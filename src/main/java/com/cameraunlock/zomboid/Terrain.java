package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import zombie.core.properties.PropertyContainer;
import zombie.iso.BuildingDef;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.areas.IsoBuilding;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * The lie of the land in the first-person picture. The game's map is flat: a river is level
 * with its banks and a town is a table. Seen from above that is how a map reads. Seen from eye
 * height it is a board, so the picture gives the land a height the game does not have, by the
 * rules land keeps:
 *
 * <ul>
 * <li>The country rises and falls over hundreds of paces, and hills stand on it. A road climbs
 * a hill at a grade a car can take, because the hills are that gentle.</li>
 * <li>Over the hills the land swells and dips a tile or so every hundred paces or more, streets
 * with it, so that a long street is seen to go over a crest and down into a dip.</li>
 * <li>Water lies low. Hills only ever rise from the base of the country, and they and the
 * swells die away towards water over the width of a valley's side, so a pond or a river is
 * level in the bottom of its valley, and its banks fall to it.</li>
 * <li>A building stands on a level pad, at the height the land has in the middle of it or,
 * with made ground against its walls, at the height that ground has where it meets them
 * (Footings), and the open ground round it is graded to meet the pad.</li>
 * <li>A street is level from one side to the other. It rises and falls along its length with
 * the country and the hills, at the height the land has down the middle of it, and the ground
 * beside it is graded to meet it. It takes nothing from a building that stands back from it:
 * the grade from a building's pad is done by the time it reaches the street.</li>
 * <li>Made ground against what is built is laid level at its footing, the street with its
 * pavements from the wall to the far kerb, and comes down to the land's height as a ramp
 * past the end of the wall (Paving).</li>
 * <li>Open ground has rises and hollows and is uneven underfoot. Made ground, a road or a
 * yard, is cut level through it.</li>
 * </ul>
 *
 * Only the picture. The game's own positions, paths and collisions stay on the flat map, and
 * everything drawn, on every floor, the eye included, is lifted by the height of the ground
 * under it.
 *
 * Heights are kept at the corners of the squares, a chunk at a time. The logic thread works
 * them out and both threads read them.
 *
 * There are two lies of the land, the hills and the country left level, and each player's
 * view is drawn on one of them (WorldScene.terrain): what a square is made of is the same for
 * both and is kept once.
 *
 * How high the hills stand is the player's (HillHeight): every rise of the land, the country's,
 * the hills', the swells' and what is uneven on open ground, is that many times what it is
 * at 1, and so is every slope the rules below give at 1. At 0 the land is as level as the game's map. What is a hand high and no part of
 * the lie of the land stays as it is at every height: a kerb, a bank down to water, the water
 * lying below the land round it.
 */
final class Terrain {
    static final int CHUNK_TILES = 8;
    static final int CORNERS = CHUNK_TILES + 1;
    private static final int TABLE_SIDE = 64;
    /**
     * How many chunks a place in the tables keeps. One view's chunks are all less than a table's
     * side apart and never ask for the same place. Two views can: with the players some multiple
     * of the table's side of chunks apart, each would put the other's chunk out, every round.
     */
    private static final int WAYS = View.COUNT;
    /** How far from made ground, a building or water its hold on the height of the land reaches, in tiles. */
    static final int REACH = 6;
    /** How far below the land round it water lies, and how far from it the bank starts to fall, in tiles. */
    static final float WATER_BELOW = 0.34f;
    private static final float BANK_WIDTH = 2.6f;
    /**
     * A street is laid at the height the land has, taken over the street within this many tiles
     * either way. No less than a street is wide, so that both its sides take it over the same squares.
     */
    static final int LEVELLED = 8;
    /** How far round a chunk the squares are gathered: as far as Paving looks from a corner for what is built, in whole chunks. */
    static final int AROUND = (Paving.GATHERED + CHUNK_TILES - 1) / CHUNK_TILES * CHUNK_TILES;
    static final int SPAN = CHUNK_TILES + 2 * AROUND;
    /**
     * How far a square di, dj squares from a corner is from it, the square's nearest point, at
     * (di + LEVELLED) * 2 * LEVELLED + dj + LEVELLED, for di and dj from -LEVELLED to LEVELLED - 1.
     */
    static final float[] AWAY = new float[4 * LEVELLED * LEVELLED];

    static {
        for (int di = -LEVELLED; di < LEVELLED; di++) {
            for (int dj = -LEVELLED; dj < LEVELLED; dj++) {
                float awayI = di >= 0 ? di : -di - 1, awayJ = dj >= 0 ? dj : -dj - 1;
                AWAY[(di + LEVELLED) * 2 * LEVELLED + dj + LEVELLED] = (float) Math.sqrt(awayI * awayI + awayJ * awayJ);
            }
        }
    }

    static final byte NATURAL = 0, MADE = 1, WATER = 2, UNKNOWN = 3, STREET = 4;
    /** How far a street lies below the ground beside it: the height of a kerb, in tiles. */
    static final float KERB = 0.11f;

    private static final class Grid {
        int wx, wy;
        boolean settled;
        /** The frame it was worked out in, and when. */
        int stamp;
        long stampNanos;
        /** The frame it was last asked for in: of the chunks that share a place in the table, the one asked for longest ago makes way. */
        int used;
        /** Which chunks the map had loaded when it was worked out: see loaded. */
        int loaded;
        /** A number no other heights have had: the same as the grid's before it when nothing of it came out otherwise. */
        int version;
        final float[] heights = new float[CORNERS * CORNERS];
        /** The height of water at each corner, which where a quay meets it is not the land's. */
        final float[] water = new float[CORNERS * CORNERS];
        /** Where the kerbs of the chunk's streets run, as Kerbs keeps it. Null where it has no street. */
        float[] kerb;
        /** What the squares the kerbs were worked out from are, and the kerbs, each as one number. */
        long kerbFrom, kerbSum;
        /** What the heights were worked out from, as one number: see gathered. */
        long from;
        /** The gain they were worked out at. */
        float gain;

        Grid() {}

        Grid(Grid source) {
            wx = source.wx;
            wy = source.wy;
            settled = source.settled;
            stamp = source.stamp;
            stampNanos = source.stampNanos;
            used = source.used;
            loaded = source.loaded;
            version = source.version;
            System.arraycopy(source.heights, 0, heights, 0, heights.length);
            System.arraycopy(source.water, 0, water, 0, water.length);
            kerb = source.kerb;
            kerbFrom = source.kerbFrom;
            kerbSum = source.kerbSum;
            from = source.from;
            gain = source.gain;
        }
    }

    private static final class Kinds {
        int wx, wy;
        final byte[] kinds = new byte[CHUNK_TILES * CHUNK_TILES];
        /** The middle of the made thing that stands over each square, as Footings finds it. NaN where there is none. */
        final float[] midX = new float[CHUNK_TILES * CHUNK_TILES], midY = new float[CHUNK_TILES * CHUNK_TILES];
        /** Whether that thing stands at the height the ground has at midX, midY with nothing of its kind standing, as Footings tells: see gather. */
        final boolean[] free = new boolean[CHUNK_TILES * CHUNK_TILES];
        /** Whether a building or something built stands on the square's own ground, which a kerb's line keeps out of. */
        final boolean[] hard = new boolean[CHUNK_TILES * CHUNK_TILES];
        /** Whether the square is made ground that is no road: a yard, a path, or the pale paving the game lays along a street as its footway. */
        final boolean[] footway = new boolean[CHUNK_TILES * CHUNK_TILES];
        /** Whether every square of the chunk was loaded: then it is kept, and is the same whenever it is asked for. */
        boolean whole;
        /** Whether it is what the map's files have of a chunk not loaded (MapAhead), which the save may have otherwise. */
        boolean guessed;
        /** Whether footed has filled in midX, midY and free; whether what it found can no longer change; and the map it found it on. */
        boolean footed, footSettled;
        long footedAsOf;
        /**
         * Which squares footed found open (Footings.open), and whether the chunk and the eight
         * round it were all loaded then: what over says of a square goes by the chunks round
         * it, and while they stay loaded an open square is open still and is not asked again.
         */
        final boolean[] open = new boolean[CHUNK_TILES * CHUNK_TILES];
        boolean ringLoaded;
        boolean water;
        /** How many of the squares have a building or something built standing on them: see hard. */
        int builtOn;
        /** As Grid.used. */
        int used;
    }

    /** The land with its hills, and the land as level as the game's map is: see level. */
    static final Terrain HILLS = new Terrain(false), LEVEL = new Terrain(true);

    /**
     * Whether the country is left as level as the game's map is. Seen from overhead the picture
     * has to lie where the game's own does, or the pointer is not on what it seems to be on:
     * the game knows nothing of hills. It has no kerbs either: a street cut a kerb's height
     * down had everything lying on it drawn 9 pixels below where the game draws it at zoom 1,
     * and 17 at zoom 0.5, apart from the game's own highlight of it. Banks, which are a hand high, stay.
     */
    final boolean level;
    /** How many times its height at 1 every rise of the land is: 0 for the level land, and the player's for the hills. Set by the logic thread. */
    private volatile float gain;
    private final AtomicReferenceArray<Grid> grids = new AtomicReferenceArray<>(TABLE_SIDE * TABLE_SIDE * WAYS);
    private static final Kinds[] known = new Kinds[TABLE_SIDE * TABLE_SIDE * WAYS];
    private static final Kinds passing = new Kinds();
    /**
     * What the chunks that are not whole are made of, by chunk, for as long as the map has the
     * chunks loaded that it had when they were looked over. One height asks about the same
     * chunk many times, and looking one over is a search for what each thing on it is joined to.
     */
    private static final HashMap<Long, Kinds> unsure = new HashMap<>();
    private static long unsureAsOf = Long.MIN_VALUE;
    /** The footings worked out for made things with no building in them that cannot be kept yet, for as long. */
    private final HashMap<Long, Float> padsForNow = new HashMap<>();
    private long padsAsOf = Long.MIN_VALUE;

    /**
     * A number that is another when the map may have other chunks loaded: the game's own count
     * of that where it tells of it, and the frame where it does not or where a dev build
     * checks that it does.
     */
    private static long mapAsOf() {
        return ChunkMarks.live && !AUDITED ? 1L << 32 | loaded & 0xFFFFFFFFL : frame & 0xFFFFFFFFL;
    }

    static final byte[] around = new byte[SPAN * SPAN];
    static final float[] padsAround = new float[SPAN * SPAN];
    /** The height of the land in the middle of each square of street of around. */
    private static final float[] streetAround = new float[SPAN * SPAN];
    private static final float[] midXAround = new float[SPAN * SPAN], midYAround = new float[SPAN * SPAN];
    private static final boolean[] freeAround = new boolean[SPAN * SPAN];
    /** Which squares of around are footway: see Kinds. */
    static final boolean[] footwayAround = new boolean[SPAN * SPAN];
    private static final Footings footings = new Footings();
    /** The map Footings is asking about, while kinds runs. */
    private static IsoCell siteCell;
    private static final Footings.Site site = new Footings.Site() {
        @Override
        public byte over(int x, int y) {
            IsoGridSquare square = siteCell.getGridSquare(x, y, 0);
            if (square == null) {
                return guessedOver(x, y);
            }
            if (defOf(square) != null) {
                return Footings.HOUSE;
            }
            if (stands(siteCell, square, x, y)) {
                return Footings.BUILT;
            }
            byte over = above(siteCell, square, x, y);
            if (over != Footings.OPEN) {
                return over;
            }
            byte kind = kind(square);
            return !Paving.without && (kind == STREET || kind == MADE) ? Footings.PAVED : Footings.OPEN;
        }

        @Override
        public boolean loaded(int wx, int wy) {
            // As findWater and kerbs take it: the map has every square of a chunk or none of it.
            return siteCell.getGridSquare(wx * CHUNK_TILES + CHUNK_TILES / 2, wy * CHUNK_TILES + CHUNK_TILES / 2, 0) != null;
        }

        @Override
        public byte state(int wx, int wy) {
            return loaded(wx, wy) ? Footings.LOADED : MapAhead.guessed(wx, wy) != null ? Footings.READ_AHEAD : Footings.NOT_LOADED;
        }

        @Override
        public long house(int x, int y) {
            IsoGridSquare square = siteCell.getGridSquare(x, y, 0);
            if (square == null) {
                return MapAhead.guessed(Math.floorDiv(x, CHUNK_TILES), Math.floorDiv(y, CHUNK_TILES)).house[inChunk(x, y)];
            }
            BuildingDef def = defOf(square);
            return Footings.key(def.getX() + def.getW() / 2f, def.getY() + def.getH() / 2f);
        }

        @Override
        public boolean paved(int x, int y) {
            IsoGridSquare square = siteCell.getGridSquare(x, y, 0);
            byte kind = square == null ? MapAhead.guessed(Math.floorDiv(x, CHUNK_TILES), Math.floorDiv(y, CHUNK_TILES)).kind[inChunk(x, y)] : kind(square);
            return !Paving.without && (kind == STREET || kind == MADE);
        }
    };
    /** Where a square of the map is in its chunk, as Kinds and MapAhead.Guess keep squares. */
    private static int inChunk(int x, int y) {
        return Math.floorMod(x, CHUNK_TILES) * CHUNK_TILES + Math.floorMod(y, CHUNK_TILES);
    }

    /**
     * What over says of a square not loaded, as the map's files have it (MapAhead), with
     * Footings.GUESSED: in the same order as over asks it of a loaded square. UNLOADED where
     * nothing has been read of it.
     */
    private static byte guessedOver(int x, int y) {
        MapAhead.Guess guess = MapAhead.guessed(Math.floorDiv(x, CHUNK_TILES), Math.floorDiv(y, CHUNK_TILES));
        if (guess == null) {
            return Footings.UNLOADED;
        }
        int at = inChunk(x, y);
        byte kind = guess.kind[at];
        byte over = guess.house[at] != Footings.NONE ? Footings.HOUSE
                : standsNow(guess.stands[at]) ? Footings.BUILT
                : guess.above[at] != Footings.OPEN ? guess.above[at]
                : !Paving.without && (kind == STREET || kind == MADE) ? Footings.PAVED : Footings.OPEN;
        return (byte) (over | Footings.GUESSED);
    }

    /** Whether something built stands on a square not loaded, as stands tells of a loaded one, from what MapAhead read of it. */
    private static boolean standsNow(byte stands) {
        return (stands & (MapAhead.STAIR_OR_ROOF | MapAhead.WALLED)) != 0 || (stands & MapAhead.CANOPY) != 0 && !Own.furniture();
    }

    /** The height of the footing of each made thing whose footing can no longer change, by the middle of the thing. */
    private final HashMap<Long, Float> pads = new HashMap<>();
    private static final byte[] kerbKinds = new byte[Kerbs.SPAN * Kerbs.SPAN];
    private static final boolean[] kerbHard = new boolean[Kerbs.SPAN * Kerbs.SPAN];
    /** How far from water the hills take to rise to their height, in chunks and in tiles: the width of a valley's side. */
    private static final int VALLEY_CHUNKS = 4;
    static final float VALLEY = VALLEY_CHUNKS * CHUNK_TILES;
    /** How far round a chunk water is looked for, in chunks: a valley's width from anywhere within LEVELLED of it. */
    private static final int WATER_CHUNKS = VALLEY_CHUNKS + (LEVELLED + CHUNK_TILES - 1) / CHUNK_TILES;
    private static final int[] waterChunkX = new int[(2 * WATER_CHUNKS + 1) * (2 * WATER_CHUNKS + 1)];
    private static final int[] waterChunkY = new int[waterChunkX.length];
    private static int waterChunks;
    /** How many squares of each chunk findWater looked at have something built over them, x first, and the chunk the first of them is. */
    private static final int WATER_SIDE = 2 * WATER_CHUNKS + 1;
    private static final int[] builtChunks = new int[WATER_SIDE * WATER_SIDE];
    private static int builtFromX, builtFromY;
    /**
     * How far round a place what is built counts towards its being a town, in chunks. One
     * less than findWater looks: base is asked of places up to a chunk out from the chunk
     * findWater was asked for, and what counts round them has to have been looked at
     * whichever chunk that was, or two chunks would make a different town of one place.
     */
    private static final int TOWN_CHUNKS = WATER_CHUNKS - 1;
    /** The share of the ground round a place that is built on where the swells begin to be cut down, and where nothing is left of them. */
    private static final float TOWN_FROM = 0.12f, TOWN_WHOLE = 0.3f;
    /** How much of a town the corners of the chunks findWater looked at are in, worked out as each is first asked for. */
    private static final float[] townAt = new float[(WATER_SIDE + 1) * (WATER_SIDE + 1)];
    private static final boolean[] townKnown = new boolean[townAt.length];

    /**
     * A chunk within a valley's width of ground that is not loaded has heights that may yet
     * change. They are worked out again this many frames on, not at every asking: a build asks
     * up to three times, and most of the chunks in sight are that near the map's edge.
     */
    private static final int RETRIED = 30;
    /** The time the frames above stand for at a hundred frames a second: a faster PC waits it out as well, or a grid at the map's edge, which never settles, is worked out more often the faster the picture is drawn. */
    private static final long RETRIED_NANOS = 300_000_000L;
    private static int frame;
    private static long frameNanos;
    /**
     * The longest a frame spends working heights out again for chunks that have them, in
     * nanoseconds. When the map loads a row of chunks every chunk near its edge is due at
     * once, most of those in sight among them, and all in one frame they hold it: the rest
     * keep the heights they have until the next frame, and are no worse off than the frame before.
     */
    private static final long AGAIN_NANOS_A_FRAME = 1_500_000L;
    private static long againNanos;
    /**
     * A chunk worked out this many frames ago is worked out whatever the frame has spent, one
     * such chunk a frame: the chunks are asked for in one order every frame, and the last of
     * them would wait for ever behind the first coming due again.
     */
    private static final int AGAIN_AT_THE_LATEST = 8 * RETRIED;
    private static boolean overdueTaken;
    private static int loaded;
    private static int versions;
    /** Dev only: an unsettled grid is worked out again on the clock though the map is as it was, to say so if it then comes out otherwise. */
    private static final boolean AUDITED = ChunkMarks.COUNTED;

    /**
     * Logic thread, once a frame the picture is built: a number that is another whenever the
     * map has another set of chunks loaded. A grid is unsettled only for want of ground that
     * is not loaded, so with the game telling of its changes it is worked out again when
     * that number has changed and not otherwise.
     */
    static void loaded(int epoch) {
        // What has been read of the map not loaded is another, as much as another set of chunks loaded is.
        loaded = epoch * 31 + MapAhead.version();
    }

    /** The version of a chunk's heights. After corners. */
    int version(int wx, int wy) {
        return grid(wx, wy).version;
    }

    /** The same, or -1 for a chunk whose heights are not kept. */
    int versionIfKept(int wx, int wy) {
        Grid grid = grid(wx, wy);
        return grid == null ? -1 : grid.version;
    }

    /** Logic thread, once a round of the views' builds: see Rounds. */
    static void frame(int number) {
        frame = number;
        frameNanos = System.nanoTime();
        againNanos = 0L;
        overdueTaken = false;
    }

    private Terrain(boolean level) {
        this.level = level;
        gain = level ? 0f : 1f;
    }

    float gain() {
        return gain;
    }

    /** Works out a chunk's heights again at once, from what is handed it: see fromMap. */
    interface Rework<C> {
        void chunk(C from, int wx, int wy);
    }

    /** A chunk's heights worked out again from the map, whatever the frame has spent on heights. Logic thread. */
    void fromMap(IsoCell cell, int wx, int wy) {
        corners(cell, wx, wy, true);
    }

    /** How many times the gain has changed, and by view how many times it had when the heights round its eye were last worked out again (heighten). */
    private int changes;
    private final int[] roundEyeAsOf = new int[View.COUNT];

    /**
     * Logic thread, as a view is built with its eye at (eyeX, eyeY): heighten when the gain is
     * another, and whenever the gain has changed since the heights round this view's eye were
     * last worked out again, those of the chunk the eye is over and of the eight round it worked
     * out again by now, from `from` (fromMap and the map). The views share the land, and each eye has its own. The rest
     * are worked out as they are asked for, and a build asks for the nearest first: the land an
     * eye stands in and the water beside it are never at two heights. Whether the gain changed.
     */
    <C> boolean heighten(float gain, int view, float eyeX, float eyeY, C from, Rework<C> now) {
        boolean changed = gain != this.gain;
        if (changed) {
            heighten(gain);
        }
        if (roundEyeAsOf[view] == changes) {
            return changed;
        }
        roundEyeAsOf[view] = changes;
        int wx = Math.floorDiv((int) Math.floor(eyeX), CHUNK_TILES), wy = Math.floorDiv((int) Math.floor(eyeY), CHUNK_TILES);
        for (int x = wx - 1; x <= wx + 1; x++) {
            for (int y = wy - 1; y <= wy + 1; y++) {
                now.chunk(from, x, y);
            }
        }
        return changed;
    }

    /**
     * Logic thread: has the hills stand gain times their height at 1, and every chunk's
     * heights worked out again the next time they are asked for. Whatever was built on the
     * heights is built again as each chunk's come out otherwise (WorldScene.landOf), and the
     * heights kept stay for any thread to read until then, with the water at the gain they
     * were worked out at (waterLevel).
     */
    void heighten(float gain) {
        if (level) {
            throw new IllegalStateException("the level land has no hills to raise");
        }
        this.gain = gain;
        changes++;
        pads.clear();
        padsForNow.clear();
        rework();
    }

    static Terrain of(boolean level) {
        return level ? LEVEL : HILLS;
    }

    /** For a map loaded anew: lets go of every height worked out and of what every square was made of. Logic thread. */
    static void forget() {
        for (int slot = 0; slot < TABLE_SIDE * TABLE_SIDE * WAYS; slot++) {
            HILLS.grids.set(slot, null);
            LEVEL.grids.set(slot, null);
        }
        Arrays.fill(known, null);
        unsure.clear();
        footings.forget();
        HILLS.pads.clear();
        LEVEL.pads.clear();
        HILLS.padsForNow.clear();
        LEVEL.padsForNow.clear();
    }

    /**
     * For what a square is made of read otherwise with the map as it is (a parasol's canopy is
     * built ground or not by a row of [Content]): lets go of what every square was made of and
     * has every chunk's heights worked out again the next time they are asked for. The heights
     * kept stay for any thread to read until then, and keep their version where they come out
     * the same. Logic thread.
     */
    static void reread() {
        Arrays.fill(known, null);
        unsure.clear();
        footings.forget();
        for (Terrain terrain : new Terrain[] {HILLS, LEVEL}) {
            terrain.pads.clear();
            terrain.padsForNow.clear();
            terrain.rework();
        }
    }

    /** Has every chunk's heights worked out again the next time they are asked for. */
    private void rework() {
        for (int slot = 0; slot < TABLE_SIDE * TABLE_SIDE * WAYS; slot++) {
            Grid grid = grids.get(slot);
            if (grid != null) {
                grid.settled = false;
                grid.loaded = loaded - 1;
                grid.stamp = frame - RETRIED;
                grid.stampNanos = frameNanos - RETRIED_NANOS;
            }
        }
    }

    /** The first of the WAYS places a chunk may be kept in. */
    private static int slot(int wx, int wy) {
        return ((wx & (TABLE_SIDE - 1)) * TABLE_SIDE + (wy & (TABLE_SIDE - 1))) * WAYS;
    }

    /** The heights kept for a chunk, or null. Any thread. */
    private Grid grid(int wx, int wy) {
        int slot = slot(wx, wy);
        for (int way = 0; way < WAYS; way++) {
            Grid grid = grids.get(slot + way);
            if (grid != null && grid.wx == wx && grid.wy == wy) {
                return grid;
            }
        }
        return null;
    }

    /** Keeps a chunk's heights in place of those it had, or in a place free, or in place of the chunk asked for longest ago. Logic thread. */
    private void keep(Grid fresh) {
        int slot = slot(fresh.wx, fresh.wy), into = slot;
        for (int way = 0; way < WAYS; way++) {
            Grid held = grids.get(slot + way);
            if (held == null || held.wx == fresh.wx && held.wy == fresh.wy) {
                into = slot + way;
                break;
            }
            if (held.used < grids.get(into).used) {
                into = slot + way;
            }
        }
        grids.set(into, fresh);
    }

    /** What is kept of what a chunk's squares are, or null. Logic thread. */
    private static Kinds known(int wx, int wy) {
        int slot = slot(wx, wy);
        for (int way = 0; way < WAYS; way++) {
            Kinds kept = known[slot + way];
            if (kept != null && kept.wx == wx && kept.wy == wy) {
                kept.used = frame;
                return kept;
            }
        }
        return null;
    }

    /** The height of the ground at (x, y) above the flat map, in tiles. 0 where it is not known. */
    float height(float x, float y) {
        return height(x, y, true);
    }

    /**
     * The height of the land at (x, y) with no street cut into it: what hangs over the ground,
     * a wire, keeps its height where it crosses a kerb. 0 where it is not known.
     */
    float land(float x, float y) {
        return height(x, y, false);
    }

    private float height(float x, float y, boolean kerbed) {
        int tileX = (int) Math.floor(x), tileY = (int) Math.floor(y);
        int wx = Math.floorDiv(tileX, CHUNK_TILES), wy = Math.floorDiv(tileY, CHUNK_TILES);
        Grid grid = grid(wx, wy);
        if (grid == null) {
            return 0f;
        }
        int i = tileX - wx * CHUNK_TILES, j = tileY - wy * CHUNK_TILES;
        float a = x - tileX, b = y - tileY;
        float[] h = grid.heights;
        int at = i * CORNERS + j;
        return (h[at] * (1f - a) + h[at + CORNERS] * a) * (1f - b) + (h[at + 1] * (1f - a) + h[at + CORNERS + 1] * a) * b
                - (kerbed && grid.kerb != null && Kerbs.at(grid.kerb, x - wx * CHUNK_TILES, y - wy * CHUNK_TILES) > 0f ? KERB : 0f);
    }

    /** Where the kerbs of a chunk run, as Kerbs keeps it, and null where the chunk has no street. After corners. */
    float[] kerb(int wx, int wy) {
        return grid(wx, wy).kerb;
    }

    /** The kerbs of a chunk as one number, which is another when they run otherwise. After corners. */
    long kerbSum(int wx, int wy) {
        return grid(wx, wy).kerbSum;
    }

    /** The height of water at the corners of a chunk, laid out as corners gives the land's. After corners. */
    float[] water(int wx, int wy) {
        return grid(wx, wy).water;
    }

    /**
     * The corner heights of a chunk, CORNERS by CORNERS with x first, worked out if they are
     * not yet or if the ground round the chunk was not all loaded when they last were. Logic
     * thread.
     */
    float[] corners(IsoCell cell, int wx, int wy) {
        return corners(cell, wx, wy, false);
    }

    /** The same, and with now whatever the frame has spent on heights worked out again. */
    private float[] corners(IsoCell cell, int wx, int wy, boolean now) {
        Grid grid = grid(wx, wy);
        boolean known = grid != null;
        if (known) {
            grid.used = frame;
        }
        boolean mapAsItWas = known && ChunkMarks.live && grid.loaded == loaded;
        if (known && (grid.settled || mapAsItWas && !AUDITED || !now && (frame - grid.stamp < RETRIED || frameNanos - grid.stampNanos < RETRIED_NANOS))) {
            return grid.heights;
        }
        if (known && !now && againNanos > AGAIN_NANOS_A_FRAME) {
            if (overdueTaken || frame - grid.stamp < AGAIN_AT_THE_LATEST) {
                return grid.heights;
            }
            overdueTaken = true;
        }
        long began = known ? System.nanoTime() : 0L;
        Grid fresh = new Grid();
        fresh.wx = wx;
        fresh.wy = wy;
        fresh.stamp = frame;
        fresh.used = frame;
        fresh.stampNanos = frameNanos;
        fresh.loaded = loaded;
        fresh.gain = gain;
        // In this order: gather looks for the water round each building, and the corners want the water round the chunk.
        fresh.settled = gather(cell, wx, wy) & findWater(cell, wx, wy);
        // The gain with them: the same squares give other heights at another.
        fresh.from = gathered() * 31 + Float.floatToRawIntBits(gain);
        boolean asBefore = known && grid.from == fresh.from;
        if (asBefore && !AUDITED) {
            // Every square and every footing the heights go by is as it was when they were last worked out, and so are they.
            System.arraycopy(grid.heights, 0, fresh.heights, 0, fresh.heights.length);
            System.arraycopy(grid.water, 0, fresh.water, 0, fresh.water.length);
        } else {
            boolean wet = false;
            for (int i = 0; i < CHUNK_TILES; i++) {
                for (int j = 0; j < CHUNK_TILES; j++) {
                    wet |= around[(AROUND + i) * SPAN + AROUND + j] == WATER;
                }
            }
            streets(wx * CHUNK_TILES - AROUND, wy * CHUNK_TILES - AROUND, AROUND, AROUND + CHUNK_TILES, true);
            for (int i = 0; i < CORNERS; i++) {
                for (int j = 0; j < CORNERS; j++) {
                    float x = wx * CHUNK_TILES + i, y = wy * CHUNK_TILES + j;
                    // Tiny height changes otherwise invalidate every mesh standing on the chunk.
                    fresh.heights[i * CORNERS + j] = Math.round(corner(x, y, i + AROUND, j + AROUND) * 256f) / 256f;
                    fresh.water[i * CORNERS + j] = wet ? base(x, y) - WATER_BELOW : 0f;
                }
            }
        }
        if (asBefore && AUDITED) {
            boolean sameInputs = Arrays.equals(fresh.water, grid.water);
            // Border heights also depend on neighbouring cached grids.
            for (int x = 1; x < CHUNK_TILES; x++) {
                for (int y = 1; y < CHUNK_TILES; y++) {
                    sameInputs &= fresh.heights[x * CORNERS + y] == grid.heights[x * CORNERS + y];
                }
            }
            if (!sameInputs) {
                Log.info("terrain: cached interior heights changed with identical inputs at " + wx + ", " + wy);
            }
        }
        if (!level) {
            kerbs(cell, fresh, known ? grid : null);
        }
        joinEdges(fresh);
        boolean same = known && fresh.kerbSum == grid.kerbSum && Arrays.equals(fresh.heights, grid.heights) && Arrays.equals(fresh.water, grid.water);
        fresh.version = same ? grid.version : ++versions;
        if (mapAsItWas && !same) {
            Log.info("chunk marks: MISSED the heights at " + wx + ", " + wy + ", which came out otherwise with the map as it was");
        }
        keep(fresh);
        if (known) {
            againNanos += System.nanoTime() - began;
        }
        return fresh.heights;
    }

    private void joinEdges(Grid fresh) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                Grid held = grid(fresh.wx + dx, fresh.wy + dy);
                if (held == null || held.gain != fresh.gain) {
                    continue;
                }
                Grid changed = null;
                int fromX = dx <= 0 ? 0 : CHUNK_TILES, toX = dx >= 0 ? CHUNK_TILES : 0;
                int fromY = dy <= 0 ? 0 : CHUNK_TILES, toY = dy >= 0 ? CHUNK_TILES : 0;
                for (int x = fromX; x <= toX; x++) {
                    for (int y = fromY; y <= toY; y++) {
                        int source = x * CORNERS + y;
                        int target = (x - dx * CHUNK_TILES) * CORNERS + y - dy * CHUNK_TILES;
                        if (fresh.heights[source] != held.heights[target]) {
                            if (changed == null) {
                                // Published arrays may still be read by the render thread.
                                changed = new Grid(held);
                            }
                            changed.heights[target] = fresh.heights[source];
                        }
                    }
                }
                if (changed != null) {
                    changed.version = ++versions;
                    keep(changed);
                }
            }
        }
    }

    /**
     * Works out where the kerbs of a chunk run. After gather. The line of a kerb is shaped by
     * the squares a long way along it, so it is only worked out anew when one of those has
     * changed since it last was.
     */
    private static void kerbs(IsoCell cell, Grid fresh, Grid last) {
        boolean street = false;
        for (int i = AROUND - 1; i <= AROUND + CHUNK_TILES && !street; i++) {
            for (int j = AROUND - 1; j <= AROUND + CHUNK_TILES && !street; j++) {
                street = around[i * SPAN + j] == STREET;
            }
        }
        if (!street) {
            return;
        }
        int reach = Kerbs.MARGIN / CHUNK_TILES;
        long from = 0x9E3779B97F4A7C15L;
        for (int cx = -reach; cx <= reach; cx++) {
            for (int cy = -reach; cy <= reach; cy++) {
                Kinds kinds = known(fresh.wx + cx, fresh.wy + cy);
                if (kinds == null) {
                    kinds = onMap(cell, fresh.wx + cx, fresh.wy + cy) ? kinds(cell, fresh.wx + cx, fresh.wy + cy) : null;
                }
                for (int i = 0; i < CHUNK_TILES; i++) {
                    for (int j = 0; j < CHUNK_TILES; j++) {
                        int at = ((cx + reach) * CHUNK_TILES + i) * Kerbs.SPAN + (cy + reach) * CHUNK_TILES + j;
                        kerbKinds[at] = kinds == null ? UNKNOWN : kinds.kinds[i * CHUNK_TILES + j];
                        kerbHard[at] = kinds != null && kinds.hard[i * CHUNK_TILES + j];
                        from = (from ^ kerbKinds[at] + (kerbHard[at] ? 8 : 0)) * 0x100000001B3L;
                    }
                }
            }
        }
        fresh.kerbFrom = from;
        if (last != null && last.kerbFrom == from) {
            fresh.kerb = last.kerb;
            fresh.kerbSum = last.kerbSum;
            return;
        }
        fresh.kerb = Kerbs.field(kerbKinds, kerbHard, fresh.wx * CHUNK_TILES, fresh.wy * CHUNK_TILES);
        if (fresh.kerb != null) {
            long sum = 0x9E3779B97F4A7C15L;
            for (float value : fresh.kerb) {
                sum = (sum ^ Float.floatToRawIntBits(value)) * 0x100000001B3L;
            }
            fresh.kerbSum = sum;
        }
    }

    /** Fills streetAround, for an around whose first square is (x, y) of the map, and has Paving look around over, for any corner of it to be asked for. After findWater. */
    void streets(int x, int y) {
        streets(x, y, 0, SPAN - 1, true);
    }

    /**
     * The same for the corners numbered from to to, either way, and no others. Without
     * paving, the heights asked for after are the land's and the streets' own, with no made
     * ground laid to anything built: what a thing stands at that takes its height from the
     * ground it stands on (gather).
     */
    void streets(int x, int y, int from, int to, boolean paving) {
        for (int i = Math.max(0, from - LEVELLED); i < Math.min(SPAN, to + LEVELLED); i++) {
            for (int j = Math.max(0, from - LEVELLED); j < Math.min(SPAN, to + LEVELLED); j++) {
                if (around[i * SPAN + j] == STREET) {
                    streetAround[i * SPAN + j] = base(x + i + 0.5f, y + j + 0.5f);
                }
            }
        }
        if (paving) {
            Paving.lay(around, padsAround, footwayAround, from, to);
        } else {
            Paving.rest();
        }
    }

    /** The height of the corner at (x, y) of the map, which is the corner (cornerI, cornerJ) of around. After streets. */
    float corner(float x, float y, int cornerI, int cornerJ) {
        float toMade = REACH, toWater = REACH, toPad = REACH, toBuilt = REACH, toStreet = REACH;
        float touchingPads = 0f, nearPads = 0f, nearWeight = 0f;
        int touching = 0;
        for (int di = -REACH; di < REACH; di++) {
            for (int dj = -REACH; dj < REACH; dj++) {
                int at = (cornerI + di) * SPAN + cornerJ + dj;
                byte kind = around[at];
                float pad = padsAround[at];
                // Open ground with something built over it, an eave over a lawn, is open ground still, and stands at that thing's footing.
                if (kind == NATURAL && Float.isNaN(pad)) {
                    continue;
                }
                float away = AWAY[(di + LEVELLED) * 2 * LEVELLED + dj + LEVELLED];
                if (kind != NATURAL) {
                    toMade = Math.min(toMade, away);
                }
                if (kind == WATER) {
                    toWater = Math.min(toWater, away);
                } else if (kind != NATURAL) {
                    toBuilt = Math.min(toBuilt, away);
                }
                if (kind == STREET) {
                    toStreet = Math.min(toStreet, away);
                }
                if (!Float.isNaN(pad) && away < REACH) {
                    toPad = Math.min(toPad, away);
                    if (away == 0f) {
                        touchingPads += pad;
                        touching++;
                    }
                    float weight = 1f - away / REACH;
                    weight *= weight * weight * weight;
                    nearPads += pad * weight;
                    nearWeight += weight;
                }
            }
        }
        if (touching > 0) {
            return touchingPads / touching;
        }
        float land = base(x, y);
        if (toStreet < REACH) {
            // In the same order from whichever chunk asks, so that two chunks give the corner they share one height.
            float streets = 0f;
            int count = 0;
            for (int di = -LEVELLED; di < LEVELLED; di++) {
                for (int dj = -LEVELLED; dj < LEVELLED; dj++) {
                    int at = (cornerI + di) * SPAN + cornerJ + dj;
                    if (around[at] == STREET) {
                        streets += streetAround[at];
                        count++;
                    }
                }
            }
            land += (streets / count - land) * (1f - smooth(toStreet / REACH));
        }
        land += (knolls(x, y) + roll(x, y)) * smooth(toMade / REACH)
                // A bank falls to the water. A quay or a boardwalk does not: it runs level to its
                // edge and the water lies below it.
                - WATER_BELOW * (1f - smooth(toWater / BANK_WIDTH)) * smooth(toBuilt / 2f);
        if (nearWeight == 0f) {
            return Paving.onto(land, cornerI, cornerJ);
        }
        float pad = nearPads / nearWeight;
        return Paving.onto(pad + (land - pad) * smooth(toPad / Math.min(REACH, toPad + toStreet)), cornerI, cornerJ);
    }

    /**
     * Fills around and padsAround with what each square within AROUND of the chunk is. False if
     * some of it is not loaded, which counts as made ground, or if a pad may yet change.
     *
     * A made thing that stands at the height of the ground under it (Footings.free) has that
     * height from the land and the streets alone, with nothing standing and no made ground
     * laid to anything: so what stands round the chunk is first looked over, each such
     * thing's footing is read off the chunks round its own middle, and then the ground is
     * laid out again with them all standing.
     */
    private boolean gather(IsoCell cell, int wx, int wy) {
        if (padsAsOf != mapAsOf()) {
            padsForNow.clear();
            padsAsOf = mapAsOf();
        }
        // First for what stands round the chunk alone: the footings are laid under it last, when every one of them is known.
        boolean all = lay(cell, wx, wy, true, AROUND / CHUNK_TILES, false);
        freeAsked = 0;
        long last = Footings.NONE;
        for (int at = 0; at < midXAround.length; at++) {
            if (!freeAround[at]) {
                continue;
            }
            long key = Footings.key(midXAround[at], midYAround[at]);
            // A thing's squares lie side by side: the one before was most often the same thing's.
            if (key == last) {
                continue;
            }
            last = key;
            boolean asked = pads.containsKey(Footings.spread(key));
            if (!asked && padsForNow.containsKey(Footings.spread(key))) {
                asked = true;
                all = false;
            }
            for (int earlier = 0; earlier < freeAsked && !asked; earlier++) {
                asked = freeKeys[earlier] == key;
            }
            if (!asked) {
                if (freeAsked == freeKeys.length) {
                    freeKeys = Arrays.copyOf(freeKeys, freeAsked * 2);
                }
                freeKeys[freeAsked++] = key;
            }
        }
        for (int asked = 0; asked < freeAsked; asked++) {
            // The corner of the map nearest the thing's middle, in the chunk that has it for one of its own.
            int x = Math.round(Footings.keyX(freeKeys[asked])), y = Math.round(Footings.keyY(freeKeys[asked]));
            int midWx = Math.floorDiv(x, CHUNK_TILES), midWy = Math.floorDiv(y, CHUNK_TILES);
            // Only the chunks round the middle: the height asked for there is the land's and the street's own, which reach no further.
            boolean lasting = lay(cell, midWx, midWy, true, 1, true) & findWater(cell, midWx, midWy);
            // The streets round that one corner, which is all that is read.
            int cornerI = x - midWx * CHUNK_TILES + AROUND, cornerJ = y - midWy * CHUNK_TILES + AROUND;
            streets(midWx * CHUNK_TILES - AROUND, midWy * CHUNK_TILES - AROUND, Math.min(cornerI, cornerJ), Math.max(cornerI, cornerJ), false);
            (lasting ? pads : padsForNow).put(Footings.spread(freeKeys[asked]), corner(x, y, cornerI, cornerJ));
            all &= lasting;
        }
        return all & lay(cell, wx, wy, false, AROUND / CHUNK_TILES, true);
    }

    /**
     * All that a chunk's heights are worked out from, as one number, after gather and
     * findWater for the chunk: what every square gathered is, the footing each stands on,
     * where the water lies, and how much is built in the chunks round it. Nothing else goes
     * into a height: base reads water and the built chunks findWater gathered, and those reach
     * WATER_CHUNKS, within what is gathered. A chunk near the edge of the loaded map is looked at again
     * whenever the map loads more, and most often none of this has changed.
     */
    private static long gathered() {
        long sum = Paving.without ? 0x9E3779B97F4A7C15L : 0x2545F4914F6CDD1DL;
        for (int at = 0; at < SPAN * SPAN; at++) {
            sum = (sum ^ around[at] + (footwayAround[at] ? 16 : 0)) * 0x100000001B3L;
            sum = (sum ^ Float.floatToRawIntBits(padsAround[at])) * 0x100000001B3L;
        }
        for (int at = 0; at < waterChunks; at++) {
            sum = (sum ^ waterChunkX[at]) * 0x100000001B3L;
            sum = (sum ^ waterChunkY[at]) * 0x100000001B3L;
        }
        // How much is built round about, which the town is: a chunk is counted once every square of it is seen, which may be after all else here is.
        for (int built : builtChunks) {
            sum = (sum ^ built) * 0x100000001B3L;
        }
        return sum;
    }

    /** The things that stand at the height of the ground under them whose footings the gathering in hand has still to work out. */
    private long[] freeKeys = new long[8];
    private int freeAsked;

    /**
     * Fills around for gather, out to reach chunks from the chunk and no further, and with
     * footings padsAround as far: bare, with nothing standing that takes its height from the
     * ground under it.
     */
    private boolean lay(IsoCell cell, int wx, int wy, boolean bare, int reach, boolean footings) {
        boolean all = true;
        for (int cx = -reach; cx <= reach; cx++) {
            for (int cy = -reach; cy <= reach; cy++) {
                Kinds kinds = kinds(cell, wx + cx, wy + cy);
                footed(cell, kinds);
                // What stands on a chunk may be joined to something on ground that is not loaded, and is then another thing with another footing.
                all &= kinds.whole && kinds.footSettled;
                for (int i = 0; i < CHUNK_TILES; i++) {
                    int row = cx * CHUNK_TILES + i + AROUND;
                    if (row < 0 || row >= SPAN) {
                        continue;
                    }
                    for (int j = 0; j < CHUNK_TILES; j++) {
                        int column = cy * CHUNK_TILES + j + AROUND;
                        if (column < 0 || column >= SPAN) {
                            continue;
                        }
                        byte kind = kinds.kinds[i * CHUNK_TILES + j];
                        all &= kind != UNKNOWN;
                        around[row * SPAN + column] = kind;
                        midXAround[row * SPAN + column] = kinds.midX[i * CHUNK_TILES + j];
                        midYAround[row * SPAN + column] = kinds.midY[i * CHUNK_TILES + j];
                        freeAround[row * SPAN + column] = kinds.free[i * CHUNK_TILES + j];
                        footwayAround[row * SPAN + column] = kinds.footway[i * CHUNK_TILES + j];
                    }
                }
            }
        }
        if (!footings) {
            return all;
        }
        // Only now: finding a pad looks at other chunks, and lays their water over this one's.
        float lastX = Float.NaN, lastY = Float.NaN, lastPad = Float.NaN;
        // The two last footings that could not be kept yet: rows of squares cross two buildings in turn, and each would look for its water again at every crossing.
        long unkeptKey = Footings.NONE, otherUnkeptKey = Footings.NONE;
        float unkeptPad = Float.NaN, otherUnkeptPad = Float.NaN;
        int first = AROUND - reach * CHUNK_TILES, past = AROUND + (reach + 1) * CHUNK_TILES;
        for (int row = first; row < past; row++) {
            for (int at = row * SPAN + first; at < row * SPAN + past; at++) {
                float x = midXAround[at], y = midYAround[at];
                if (Float.isNaN(x)) {
                    padsAround[at] = Float.NaN;
                    continue;
                }
                if (x != lastX || y != lastY) {
                    long key = Footings.key(x, y);
                    Float kept = pads.get(Footings.spread(key));
                    if (freeAround[at]) {
                        kept = bare ? Float.valueOf(Float.NaN) : kept != null ? kept : padsForNow.get(Footings.spread(key));
                    } else if (kept == null && key == unkeptKey) {
                        kept = unkeptPad;
                    } else if (kept == null && key == otherUnkeptKey) {
                        kept = otherUnkeptPad;
                    } else if (kept == null) {
                        boolean lasting = findWater(cell, Math.floorDiv((int) Math.floor(x), CHUNK_TILES), Math.floorDiv((int) Math.floor(y), CHUNK_TILES));
                        kept = base(x, y);
                        if (lasting) {
                            pads.put(Footings.spread(key), kept);
                        } else {
                            otherUnkeptKey = unkeptKey;
                            otherUnkeptPad = unkeptPad;
                            unkeptKey = key;
                            unkeptPad = kept;
                        }
                        all &= lasting;
                    }
                    lastX = x;
                    lastY = y;
                    lastPad = kept;
                }
                padsAround[at] = lastPad;
            }
        }
        return all;
    }

    /**
     * Notes which chunks within WATER_CHUNKS of the chunk hold water. False if some of them are
     * not loaded, and so not known.
     */
    private static boolean findWater(IsoCell cell, int wx, int wy) {
        boolean all = true;
        waterChunks = 0;
        builtFromX = wx - WATER_CHUNKS;
        builtFromY = wy - WATER_CHUNKS;
        Arrays.fill(townKnown, false);
        Arrays.fill(builtChunks, 0);
        for (int cx = -WATER_CHUNKS; cx <= WATER_CHUNKS; cx++) {
            for (int cy = -WATER_CHUNKS; cy <= WATER_CHUNKS; cy++) {
                Kinds kept = known(wx + cx, wy + cy);
                if (kept == null) {
                    kept = onMap(cell, wx + cx, wy + cy) ? kinds(cell, wx + cx, wy + cy) : null;
                    if (kept == null || !kept.whole) {
                        all = false;
                        if (kept == null || !kept.guessed) {
                            continue;
                        }
                    }
                }
                builtChunks[(cx + WATER_CHUNKS) * WATER_SIDE + cy + WATER_CHUNKS] = kept.builtOn;
                if (kept.water) {
                    waterChunkX[waterChunks] = (wx + cx) * CHUNK_TILES;
                    waterChunkY[waterChunks++] = (wy + cy) * CHUNK_TILES;
                }
            }
        }
        return all;
    }

    /** How far (x, y) is from the nearest chunk findWater found water in, in tiles, and VALLEY if from none. */
    private static float toWaterChunk(float x, float y) {
        float nearest = VALLEY;
        for (int i = 0; i < waterChunks; i++) {
            float awayX = Math.max(0f, Math.max(waterChunkX[i] - x, x - waterChunkX[i] - CHUNK_TILES));
            float awayY = Math.max(0f, Math.max(waterChunkY[i] - y, y - waterChunkY[i] - CHUNK_TILES));
            nearest = Math.min(nearest, (float) Math.sqrt(awayX * awayX + awayY * awayY));
        }
        return nearest;
    }

    /**
     * How much of a town (x, y) is in: nothing where a house stands here and there on its
     * lawn, all where the ground round about is built over, shops wall to wall. The swells
     * die away in one, as they do towards water: a row of shops is level from end to end,
     * and on land that falls a tile in twenty along its front one end of it stands a step
     * above its pavement and the other a step below, whatever rule the paving is laid by. A
     * town is built where the ground is easy, or the ground is made easy for it. The hills
     * stay: they are broad, and a town can stand on the side of one. Worked out at the
     * corners of the chunks, from the chunks findWater looked at, and read off between them:
     * the same from whichever chunk asks once the map round both is loaded.
     */
    private static float town(float x, float y) {
        float inX = x / CHUNK_TILES, inY = y / CHUNK_TILES;
        int cornerX = (int) Math.floor(inX), cornerY = (int) Math.floor(inY);
        float a = inX - cornerX, b = inY - cornerY;
        return (townAtCorner(cornerX, cornerY) * (1f - a) + townAtCorner(cornerX + 1, cornerY) * a) * (1f - b)
                + (townAtCorner(cornerX, cornerY + 1) * (1f - a) + townAtCorner(cornerX + 1, cornerY + 1) * a) * b;
    }

    /** The same at the corner the chunk (wx, wy) has first. */
    private static float townAtCorner(int wx, int wy) {
        int i = wx - builtFromX, j = wy - builtFromY;
        boolean kept = i >= 0 && j >= 0 && i <= WATER_SIDE && j <= WATER_SIDE;
        if (kept && townKnown[i * (WATER_SIDE + 1) + j]) {
            return townAt[i * (WATER_SIDE + 1) + j];
        }
        float built = 0f, ground = 0f;
        for (int cx = -TOWN_CHUNKS; cx < TOWN_CHUNKS; cx++) {
            for (int cy = -TOWN_CHUNKS; cy < TOWN_CHUNKS; cy++) {
                float awayX = cx + 0.5f, awayY = cy + 0.5f;
                float near = 1f - (awayX * awayX + awayY * awayY) / (TOWN_CHUNKS * TOWN_CHUNKS);
                if (near <= 0f) {
                    continue;
                }
                near *= near;
                ground += CHUNK_TILES * CHUNK_TILES * near;
                int atX = i + cx, atY = j + cy;
                // A chunk findWater did not look at counts as open ground: it is further off than base is asked for.
                if (atX >= 0 && atY >= 0 && atX < WATER_SIDE && atY < WATER_SIDE) {
                    built += builtChunks[atX * WATER_SIDE + atY] * near;
                }
            }
        }
        float town = smooth((built / ground - TOWN_FROM) / (TOWN_WHOLE - TOWN_FROM));
        if (kept) {
            townAt[i * (WATER_SIDE + 1) + j] = town;
            townKnown[i * (WATER_SIDE + 1) + j] = true;
        }
        return town;
    }

    /**
     * Whether the chunk is loaded or MapAhead has read it: whether kinds has anything to say of
     * it. A chunk with its middle square missing is not loaded, and looking over the rest of it
     * would find nothing.
     */
    private static boolean onMap(IsoCell cell, int wx, int wy) {
        return cell.getGridSquare(wx * CHUNK_TILES + CHUNK_TILES / 2, wy * CHUNK_TILES + CHUNK_TILES / 2, 0) != null || MapAhead.guessed(wx, wy) != null;
    }

    /** What the squares of a chunk not loaded are, as MapAhead read them. */
    private static Kinds guessed(MapAhead.Guess guess) {
        Kinds fresh = new Kinds();
        fresh.wx = guess.wx;
        fresh.wy = guess.wy;
        fresh.guessed = true;
        for (int at = 0; at < CHUNK_TILES * CHUNK_TILES; at++) {
            fresh.kinds[at] = guess.kind[at];
            fresh.hard[at] = guess.house[at] != Footings.NONE || standsNow(guess.stands[at]);
            fresh.footway[at] = guess.footway[at];
            fresh.water |= guess.kind[at] == WATER;
            fresh.builtOn += fresh.hard[at] ? 1 : 0;
        }
        return fresh;
    }

    /** What each square of a chunk is. Kept once every square of the chunk has been seen loaded. */
    private static Kinds kinds(IsoCell cell, int wx, int wy) {
        Kinds kept = known(wx, wy);
        if (kept != null) {
            return kept;
        }
        if (unsureAsOf != mapAsOf()) {
            unsure.clear();
            unsureAsOf = mapAsOf();
        }
        long chunk = Footings.spread((long) wx << 32 | wy & 0xFFFFFFFFL);
        kept = unsure.get(chunk);
        if (kept != null) {
            return kept;
        }
        MapAhead.Guess guess = cell.getGridSquare(wx * CHUNK_TILES + CHUNK_TILES / 2, wy * CHUNK_TILES + CHUNK_TILES / 2, 0) == null ? MapAhead.guessed(wx, wy) : null;
        if (guess != null) {
            kept = guessed(guess);
            unsure.put(chunk, kept);
            return kept;
        }
        boolean whole = true, water = false;
        for (int i = 0; i < CHUNK_TILES; i++) {
            for (int j = 0; j < CHUNK_TILES; j++) {
                int x = wx * CHUNK_TILES + i, y = wy * CHUNK_TILES + j;
                IsoGridSquare square = cell.getGridSquare(x, y, 0);
                byte kind = kind(square);
                whole &= kind != UNKNOWN;
                passing.kinds[i * CHUNK_TILES + j] = kind;
                passing.hard[i * CHUNK_TILES + j] = square != null && (defOf(square) != null || stands(cell, square, x, y));
                passing.footway[i * CHUNK_TILES + j] = kind == MADE || kind == STREET && footway(square);
                water |= kind == WATER;
            }
        }
        Kinds fresh = new Kinds();
        fresh.wx = wx;
        fresh.wy = wy;
        fresh.whole = whole;
        fresh.water = water;
        for (boolean built : passing.hard) {
            fresh.builtOn += built ? 1 : 0;
        }
        System.arraycopy(passing.kinds, 0, fresh.kinds, 0, fresh.kinds.length);
        System.arraycopy(passing.hard, 0, fresh.hard, 0, fresh.hard.length);
        System.arraycopy(passing.footway, 0, fresh.footway, 0, fresh.footway.length);
        if (!whole) {
            unsure.put(chunk, fresh);
            return fresh;
        }
        fresh.used = frame;
        int slot = slot(wx, wy), into = slot;
        for (int way = 0; way < WAYS; way++) {
            Kinds held = known[slot + way];
            if (held == null) {
                into = slot + way;
                break;
            }
            if (held.used < known[into].used) {
                into = slot + way;
            }
        }
        known[into] = fresh;
        return fresh;
    }

    /**
     * Fills in which made thing stands over each square of a chunk, and what it stands at:
     * the search of what a square is joined to looks over chunks round it, so it is asked
     * only of the chunks a height is worked out from (lay) and not of every chunk looked at
     * for water. Kept while what it found is settled, and otherwise for as long as the map
     * has the same chunks loaded.
     */
    private static void footed(IsoCell cell, Kinds kinds) {
        long asOf = mapAsOf();
        if (footingsAsOf != asOf) {
            footings.changed();
            footingsAsOf = asOf;
        }
        if (kinds.footed && (kinds.footSettled || kinds.footedAsOf == asOf)) {
            return;
        }
        boolean settled = true, onMap = false;
        for (byte kind : kinds.kinds) {
            onMap |= kind != UNKNOWN;
        }
        siteCell = cell;
        boolean ringLoaded = onMap;
        for (int cx = -1; cx <= 1 && ringLoaded; cx++) {
            for (int cy = -1; cy <= 1 && ringLoaded; cy++) {
                ringLoaded = site.loaded(kinds.wx + cx, kinds.wy + cy);
            }
        }
        boolean openHolds = kinds.footed && kinds.ringLoaded && ringLoaded;
        for (int i = 0; i < CHUNK_TILES; i++) {
            for (int j = 0; j < CHUNK_TILES; j++) {
                int at = i * CHUNK_TILES + j;
                if (openHolds && kinds.open[at]) {
                    continue;
                }
                // A chunk that is not whole is looked over again whenever the map has other chunks loaded (kinds): one with no square known has none loaded now.
                if (onMap) {
                    footings.find(site, kinds.wx * CHUNK_TILES + i, kinds.wy * CHUNK_TILES + j);
                } else {
                    footings.findUnloaded(kinds.wx * CHUNK_TILES + i, kinds.wy * CHUNK_TILES + j);
                }
                settled &= footings.settled;
                kinds.open[at] = footings.open;
                kinds.midX[at] = Footings.keyX(footings.found);
                kinds.midY[at] = Footings.keyY(footings.found);
                kinds.free[at] = footings.free;
            }
        }
        siteCell = null;
        kinds.ringLoaded = ringLoaded;
        kinds.footed = true;
        kinds.footSettled = settled;
        kinds.footedAsOf = asOf;
    }

    private static long footingsAsOf = Long.MIN_VALUE;

    private static BuildingDef defOf(IsoGridSquare square) {
        IsoBuilding building = square.getBuilding();
        return building == null ? null : building.getDef();
    }

    /**
     * Whether something built stands on the ground of a square that is in no room of a
     * building: a stair, a roof tile, which there is an awning or a canopy, or a building's
     * own wall, which the game stands on the square outside the building along its south and
     * east sides. A fence or a garden wall does not: it runs with the ground it stands on.
     */
    private static boolean stands(IsoCell cell, IsoGridSquare square, int x, int y) {
        PZArrayList<IsoObject> objects = square.getObjects();
        boolean north = false, west = false;
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite == null) {
                continue;
            }
            // A parasol's canopy is marked as a roof tile, and where the mod stands it as a parasol it stands on the ground as furniture does: see Parasols.
            if (isStair(sprite.getType()) || isRoof(sprite.getType()) && !(Own.furniture() && Parasols.partOfOne(cell, sprite, x, y, 0))) {
                return true;
            }
            PropertyContainer flags = sprite.getProperties();
            north |= flags.has(IsoFlagType.WallN) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.DoorWallN) || flags.has(IsoFlagType.WindowN);
            west |= flags.has(IsoFlagType.WallW) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.DoorWallW) || flags.has(IsoFlagType.WindowW);
        }
        return north && inBuilding(cell.getGridSquare(x, y - 1, 0)) || west && inBuilding(cell.getGridSquare(x - 1, y, 0));
    }

    /**
     * What is over a square on the levels above it, as Footings names it: BUILT under a room,
     * a floor, a roof, a wall, a rail or a stair, HUNG under anything else, a sign, a coping
     * or the head of a street lamp, and OPEN under nothing. The wires the game hangs over a
     * road on squares of air, and the upper lengths of the poles that carry them, are
     * nothing: they go by the land (WorldScene.addPole, addWires).
     */
    private static byte above(IsoCell cell, IsoGridSquare square, int x, int y) {
        boolean hung = false;
        for (int z = 1; z <= square.getChunk().getMaxLevel(); z++) {
            IsoGridSquare over = cell.getGridSquare(x, y, z);
            if (over == null) {
                continue;
            }
            if (over.getBuilding() != null || over.getFloor() != null) {
                return Footings.BUILT;
            }
            PZArrayList<IsoObject> objects = over.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                IsoSprite sprite = objects.get(i).getSprite();
                if (sprite == null || sprite.getName() != null && sprite.getName().startsWith(Footings.OVERHEAD_LINES)) {
                    continue;
                }
                PropertyContainer flags = sprite.getProperties();
                if (isStair(sprite.getType()) || isRoof(sprite.getType()) || flags.has(IsoFlagType.WallN) || flags.has(IsoFlagType.WallW) || flags.has(IsoFlagType.WallNW)
                        || flags.has(IsoFlagType.WallSE) || flags.has(IsoFlagType.DoorWallN) || flags.has(IsoFlagType.DoorWallW) || flags.has(IsoFlagType.WindowN) || flags.has(IsoFlagType.WindowW)) {
                    return Footings.BUILT;
                }
                hung = true;
            }
        }
        return hung ? Footings.HUNG : Footings.OPEN;
    }

    static boolean isRoof(IsoObjectType type) {
        return type == IsoObjectType.WestRoofB || type == IsoObjectType.WestRoofM || type == IsoObjectType.WestRoofT;
    }

    private static boolean inBuilding(IsoGridSquare square) {
        return square != null && defOf(square) != null;
    }

    static boolean isStair(IsoObjectType type) {
        return type == IsoObjectType.stairsBW || type == IsoObjectType.stairsMW || type == IsoObjectType.stairsTW
                || type == IsoObjectType.stairsBN || type == IsoObjectType.stairsMN || type == IsoObjectType.stairsTN;
    }

    static byte kind(IsoGridSquare square) {
        return square == null ? UNKNOWN : kind(square, square.getFloor());
    }

    /** Whether a square of street is footway: the game calls the floor of its pavements Road_04, as Ground tells. */
    private static boolean footway(IsoGridSquare square) {
        return "Road_04".equals(square.getFloor().getSprite().getProperties().get("FloorMaterial"));
    }

    /** The same of a square whose floor the caller has already asked the game for: the asking is a walk through the square's objects. */
    static byte kind(IsoGridSquare square, IsoObject floor) {
        IsoSprite sprite = floor == null ? null : floor.getSprite();
        if (sprite == null) {
            return MADE;
        }
        if (sprite.getProperties().has(IsoFlagType.water)) {
            return WATER;
        }
        String name = sprite.getName();
        if (name == null || !square.isOutside()) {
            return MADE;
        }
        return name.startsWith("blends_natural") ? NATURAL : name.startsWith("blends_street") ? STREET : MADE;
    }

    /** What is drawn at (x, y), on any floor, stands this much above where the flat map has it. */
    float lift(float x, float y, float z) {
        return height(x, y);
    }

    static float smooth(float t) {
        t = Math.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * The height water's surface has at (x, y), wherever water lies there: what water() gives
     * a corner of a chunk with water in it, where no hill stands on the country, at the gain the
     * chunk's heights were worked out at (gainAt). Any thread.
     */
    float waterLevel(float x, float y) {
        return waterLevel(gainAt(x, y), x, y);
    }

    /** The same at a gain. */
    static float waterLevel(float gain, float x, float y) {
        return (gain == 0f ? 0f : gain * rise(x, y)) - WATER_BELOW;
    }

    /**
     * The gain the heights of the chunk at (x, y) were worked out at, which is what its land and
     * its water are drawn at: after a change of the height of the hills until the chunk's heights
     * are worked out again, the gain before. Where they never were, the gain they will be. Any
     * thread.
     */
    float gainAt(float x, float y) {
        Grid grid = grid(Math.floorDiv((int) Math.floor(x), CHUNK_TILES), Math.floorDiv((int) Math.floor(y), CHUNK_TILES));
        return grid == null ? gain : grid.gain;
    }

    /** Whether a chunk whose squares have all been seen loaded has water on it. Logic thread. */
    static boolean holdsWater(int wx, int wy) {
        Kinds kept = known(wx, wy);
        return kept != null && kept.water;
    }

    /** The height of the land at (x, y) before what is uneven on open ground and what is built is on it. After findWater. */
    float base(float x, float y) {
        if (gain == 0f) {
            return 0f;
        }
        float toWater = toWaterChunk(x, y), heave = heave(x, y);
        return gain * (lie(x, y, toWater, heave) - (Paving.without ? 0f : heave * town(x, y) * smooth(toWater / VALLEY)));
    }

    /**
     * The height of the land at (x, y) as it can be told with nothing of the map loaded there:
     * the country, and the hills and swells on it where the nearest chunk with water in it is
     * toWater tiles off, VALLEY and more counting as none. What a building, a street and open
     * ground's own unevenness do to it is left out, and so is the dying away of the swells in
     * a town (base): land seen from far off is up to a swell's height off what it is when the
     * map has loaded there. Any thread.
     */
    float far(float x, float y, float toWater) {
        return gain == 0f ? 0f : gain * lie(x, y, toWater, heave(x, y));
    }

    /** The same at a gain of 1, where heave is heave's at (x, y). */
    private static float lie(float x, float y, float toWater, float heave) {
        return rise(x, y) + (stand(x, y) + heave + upland(x, y)) * smooth(toWater / VALLEY);
    }

    /** The rise and fall of the country over the better part of a mile. Never steeper than a rise of one in forty. */
    private static float rise(float x, float y) {
        return 2.2f * (float) (Math.sin(x * 0.0061 + 0.7) * Math.sin(y * 0.0053 + 2.2))
                + 1.4f * (float) Math.sin((x * 0.8 - y * 0.6) * 0.0087 + 4.0);
    }

    /**
     * The hills that stand on the country: broad, a few tiles high, and never below it. A road
     * over one climbs less than one in fifteen.
     */
    private static float stand(float x, float y) {
        float broad = (float) (Math.sin(x * 0.015 + 1.1) * Math.sin(y * 0.018 + 0.3) + 0.6 * Math.sin((x + y) * 0.01 + 5.0));
        float ridge = (float) (Math.sin(x * 0.033 - y * 0.022 + 2.0) * Math.sin(y * 0.03 + x * 0.009 + 0.9));
        float up = Math.max(0f, 0.5f + 0.42f * broad + 0.16f * ridge);
        return 2.4f * up * up;
    }

    /**
     * The hills of farmland and woods (Country), which stand on the country as stand's do, more
     * often and higher: round tops some two hundred and fifty tiles across and up to eight tiles
     * high. None in a town, where the map zones none of the land round about farmland or woods,
     * and like stand's they die away towards water.
     */
    static float upland(float x, float y) {
        float country = Country.at(x, y);
        if (country == 0f) {
            return 0f;
        }
        float lumps = (float) (0.55 * Math.sin(x * 0.012 + 0.4) * Math.sin(y * 0.01044 + 2.9)
                + 0.45 * Math.sin((x * 0.6 - y * 0.8) * 0.01356 + 1.2) * Math.sin((x * 0.8 + y * 0.6) * 0.00852 + 0.3));
        float up = Math.max(0f, lumps + 0.35f);
        return country * 4.5f * up * up;
    }

    /**
     * The swells and dips of the land, streets and all: a tile or so from top to bottom and
     * some hundred and thirty tiles from one crest to the next, which is near enough to see
     * both from a street. Never steeper than one in twenty.
     */
    float swells(float x, float y) {
        return gain == 0f ? 0f : gain * heave(x, y);
    }

    private static float heave(float x, float y) {
        return 0.65f * (float) (Math.sin(x * 0.047 + 2.6) * Math.sin(y * 0.041 + 0.5))
                + 0.37f * (float) Math.sin((x * 0.6 + y * 0.8) * 0.052 + 1.9);
    }

    /**
     * The rises and hollows of open country, a stone's throw across and a man's height or so
     * from top to bottom. Made ground is cut level through them: a road runs between its banks.
     */
    private float knolls(float x, float y) {
        if (gain == 0f) {
            return 0f;
        }
        return gain * (1.1f * (float) (Math.sin(x * 0.067 + 0.3) * Math.sin(y * 0.079 + 1.7))
                + 0.7f * (float) (Math.sin((x + y * 0.6) * 0.045 + 3.1) * Math.sin((y - x * 0.4) * 0.057 + 0.8)));
    }

    /** What is uneven underfoot on open ground, the same wherever and whenever it is asked. */
    private float roll(float x, float y) {
        if (gain == 0f) {
            return 0f;
        }
        return gain * (0.2f * (float) (Math.sin(x * 0.071 + 1.3) * Math.sin(y * 0.063 + 0.4))
                + 0.07f * (float) (Math.sin(x * 0.29 + y * 0.11) * Math.sin(y * 0.27 - x * 0.13)));
    }
}
