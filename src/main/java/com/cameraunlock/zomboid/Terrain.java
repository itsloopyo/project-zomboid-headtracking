package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReferenceArray;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteInstance;
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
 * <li>Ground laid level on purpose, a court, a track, a pitch, a diamond, is level all over
 * at the height the land has under its middle, and the land eases back in round it over
 * GROUND_EASE (Grounds).</li>
 * </ul>
 *
 * Only the picture. The game's own positions, paths and collisions stay on the flat map, and
 * everything drawn, on every floor, the eye included, is lifted by the height of the ground
 * under it.
 *
 * Heights are kept at the corners of the squares, a chunk at a time. The logic thread works
 * them out and both threads read them.
 *
 * They go by the map and by nothing that was loaded or asked for before. What each chunk's
 * squares are is read into a record of the chunk (Kinds): from its squares where it is loaded,
 * from what MapAhead read of the map's files where it is not and some view's window holds it,
 * and as UNKNOWN where neither has it. A loaded chunk's record goes by its own squares, and
 * above the ground only by those the game makes on every load of the chunk (above): a square
 * it makes on a first load only, or on the chunk's edge for a chunk that loads beside it, adds
 * nothing. Whatever is worked out from records, a footing, a pad, a chunk's heights, is kept
 * with the chunks it went by (Reads), and holds while none of them has come out otherwise. A
 * record is read again when its chunk loads or is let go, and when what MapAhead holds of the
 * chunk is another (scan). Only where it then comes out otherwise is what went by it worked
 * out again: where the save has a chunk as the map's files do, nothing is. What the game
 * changes in a loaded chunk later, a wall built or burnt down, is read when the chunk loads
 * again.
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
    /** Over how far round ground laid level on purpose the land eases back to its own height, in tiles: see Grounds. */
    static final float GROUND_EASE = 16f;

    private static final class Grid {
        int wx, wy;
        /** The frame it was worked out in. */
        int stamp;
        /** The frame it was last asked for in: of the chunks that share a place in the table, the one asked for longest ago makes way. */
        int used;
        /** A number no other heights have had: the same as the grid's before it when nothing of it came out otherwise. */
        int version;
        final float[] heights = new float[CORNERS * CORNERS];
        /** The height of water at each corner, which where a quay meets it is not the land's. */
        final float[] water = new float[CORNERS * CORNERS];
        /** Where the kerbs of the chunk's streets run, as Kerbs keeps it. Null where it has no street. */
        float[] kerb;
        /** The lanes of the chunk's roads, as Lanes keeps them. Null where none of its squares is in one. */
        float[] lanes;
        /** What the squares the kerbs and the lanes were worked out from are, and the kerbs with the lanes, each as one number. */
        long kerbFrom, kerbSum;
        /** What the heights were worked out from, as one number: see gathered. */
        long from;
        /** All that the heights and the kerbs go by, as one number: see fold. */
        long folded;
        /** The gain they were worked out at. */
        float gain;
        /** The chunks they were worked out from: they hold while none of those has come out otherwise. */
        Reads reads;
        /** Whether one has since: they are worked out again as the frame has the time (corners). */
        boolean stale;

        Grid() {}

        Grid(Grid source) {
            wx = source.wx;
            wy = source.wy;
            stamp = source.stamp;
            used = source.used;
            version = source.version;
            System.arraycopy(source.heights, 0, heights, 0, heights.length);
            System.arraycopy(source.water, 0, water, 0, water.length);
            kerb = source.kerb;
            lanes = source.lanes;
            kerbFrom = source.kerbFrom;
            kerbSum = source.kerbSum;
            from = source.from;
            folded = source.folded;
            gain = source.gain;
            reads = source.reads;
            stale = source.stale;
        }
    }

    /** Where a record was read from: the chunk's squares, what MapAhead read of the map's files, or neither, with every square UNKNOWN. */
    private static final byte LOADED = 0, READ_AHEAD = 1, UNREAD = 2;

    /**
     * What stands on the ground of a square, as standsOf reads it, a bit each: a stair, or a roof
     * tile that is no quarter of a round canopy; a building's wall along the square's north side,
     * and along its west; a room of a building on the square; and from ROOF_QUARTERS on the
     * quarters of a round canopy (Parasols.quarter) that are roof tiles, and from HELD_QUARTERS on
     * those of any kind, a bit for each quarter.
     */
    static final int STAIR_OR_ROOF = 1, NORTH_WALL = 1 << 1, WEST_WALL = 1 << 2, ROOMED = 1 << 3;
    static final int ROOF_QUARTERS = 4, HELD_QUARTERS = 8;

    /**
     * The record of a chunk: what each of its squares is, read of the square alone (read), what
     * is over each square, which goes by the squares round it as well (deriveFrom), and what made
     * thing stands over each (footed).
     */
    private static final class Kinds {
        int wx, wy;
        /** Where it was read from: LOADED, READ_AHEAD or UNREAD. */
        byte source;
        /** What MapAhead had read of the chunk where it was read from that, and otherwise null. */
        MapAhead.Guess guess;
        final byte[] kinds = new byte[CHUNK_TILES * CHUNK_TILES];
        /** Whether the square is made ground that is no road: a yard, a path, or the pale paving the game lays along a street as its footway. */
        final boolean[] footway = new boolean[CHUNK_TILES * CHUNK_TILES];
        /** The straight lines the game paints on the square, as Lanes.mark gives them. */
        final byte[] marks = new byte[CHUNK_TILES * CHUNK_TILES];
        /**
         * The middle of the building whose pad the square is part of, as Footings.key packs it, or
         * Footings.NONE: the one with a room on the square, or the one with a room under it. A
         * basement runs out under the yard beside its house, and with the yard graded off the pad
         * over it, the basement's ceiling and floor sank with the yard where its walls stood at
         * the pad (2319..2321, 13997..14001).
         */
        final long[] house = new long[CHUNK_TILES * CHUNK_TILES];
        /** What the square is to level ground (groundOf), as Grounds names it. */
        final byte[] level = new byte[CHUNK_TILES * CHUNK_TILES];
        /** What is over the square on the levels above it (above). */
        final byte[] above = new byte[CHUNK_TILES * CHUNK_TILES];
        /** What stands on the ground of the square (standsOf). */
        final short[] stands = new short[CHUNK_TILES * CHUNK_TILES];
        /** Whether no square is UNKNOWN, and whether then some square is water. */
        boolean whole, water;
        /**
         * Whether over, hard, builtOn and sum are worked out from what the squares are and what
         * those of the eight chunks round it are: while it is, those eight have records.
         */
        boolean derived;
        /** What is over each square, as Footings.Site.over tells it. */
        final byte[] over = new byte[CHUNK_TILES * CHUNK_TILES];
        /** Whether a building or something built stands on the square's own ground, which a kerb's line keeps out of. */
        final boolean[] hard = new boolean[CHUNK_TILES * CHUNK_TILES];
        /** How many of the squares are hard where the chunk is whole, and 0 where it is not. */
        int builtOn;
        /** What the squares are, kinds, hard, footway and marks, as one number. */
        long sum;
        /** The middle of the made thing that stands over each square, as Footings finds it. NaN where there is none. */
        final float[] midX = new float[CHUNK_TILES * CHUNK_TILES], midY = new float[CHUNK_TILES * CHUNK_TILES];
        /** Of a thing of level ground, the box of its ruler (Footings.ruler), whose middle midX and midY are and whose footing it is laid at, as box has them. */
        final float[] rulerBox = new float[4 * CHUNK_TILES * CHUNK_TILES];
        /** Whether that thing stands at the height the ground has at midX, midY with nothing of its kind standing, as Footings tells: see lookOver. */
        final boolean[] free = new boolean[CHUNK_TILES * CHUNK_TILES];
        /** Of a thing of level ground, the box Terrain lays level (Footings.boxed): its first corner and its last, four to a square. NaN where there is none. */
        final float[] box = new float[4 * CHUNK_TILES * CHUNK_TILES];
        /** Whether footed has filled in midX and the rest, and the chunks its searches went by. */
        boolean footed;
        Reads footReads;
        /** What footed last found, midX, midY and free, as one number; whether some square has a made thing over it, and whether one stands free. */
        long footSum;
        boolean anyThing, anyFree;
        /** As Grid.used. */
        int used;
        /** Where it is in kept. */
        int index;
        /** Whether the chunk is loaded, as the last read or scan found it. */
        boolean chunkLoaded;

        Kinds(int wx, int wy) {
            this.wx = wx;
            this.wy = wy;
            Arrays.fill(box, Float.NaN);
            Arrays.fill(rulerBox, Float.NaN);
        }
    }

    /**
     * The chunks something was worked out from, from the first chunk to the last, and how many
     * chunks the log had had when it was last found to hold: while none of them has been logged
     * since, what was worked out from them is what working it out again would give.
     */
    private static final class Reads {
        int lowX = Integer.MAX_VALUE, lowY = Integer.MAX_VALUE, highX = Integer.MIN_VALUE, highY = Integer.MIN_VALUE;
        long heldTo;

        Reads(long heldTo) {
            this.heldTo = heldTo;
        }

        void add(int fromX, int fromY, int toX, int toY) {
            lowX = Math.min(lowX, fromX);
            lowY = Math.min(lowY, fromY);
            highX = Math.max(highX, toX);
            highY = Math.max(highY, toY);
        }

        void add(Reads other) {
            add(other.lowX, other.lowY, other.highX, other.highY);
        }

        /** Whether none of the chunks has been logged since they last held, and if so that they hold now. */
        boolean hold() {
            if (!unchanged(lowX, lowY, highX, highY, heldTo)) {
                return false;
            }
            heldTo = logged;
            return true;
        }
    }

    /** How a footing was worked out: at the middle of a thing (plainPad), over the box of a thing of level ground, or under the middle of a thing with the ground bare (freePad). */
    private static final byte PLAIN = 0, BOXED = 1, BARE = 2;

    /** A footing, how it was worked out, of a BOXED one the box it was worked out over as Kinds.rulerBox has it, and the chunks it went by. */
    private static final class Pad {
        final float pad;
        final byte how;
        final float lowX, lowY, highX, highY;
        final Reads reads;

        Pad(float pad, byte how, float[] boxes, int from, Reads reads) {
            this.pad = pad;
            this.how = how;
            lowX = how == BOXED ? boxes[from] : Float.NaN;
            lowY = how == BOXED ? boxes[from + 1] : Float.NaN;
            highX = how == BOXED ? boxes[from + 2] : Float.NaN;
            highY = how == BOXED ? boxes[from + 3] : Float.NaN;
            this.reads = reads;
        }

        /** Whether it was worked out as the footing asked for now would be. */
        boolean fits(byte how, float[] boxes, int from) {
            return this.how == how && (how != BOXED || lowX == boxes[from] && lowY == boxes[from + 1] && highX == boxes[from + 2] && highY == boxes[from + 3]);
        }
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
    /** How many records a place in records keeps: see KEPT_AROUND. */
    private static final int RECORD_WAYS = 2 * View.COUNT;
    private static final Kinds[] records = new Kinds[TABLE_SIDE * TABLE_SIDE * RECORD_WAYS];
    /** Every record in records, each at its index, for scan to go over. */
    private static final ArrayList<Kinds> kept = new ArrayList<>();
    /**
     * Records of chunks further than this from every eye are let go (scan), in chunks. The map
     * the game loads reaches some 76 tiles from the player, and what is worked out for a chunk
     * of it goes by chunks round it to AROUND and, by footing searches and the water round a
     * footing, some way past that. A record let go while what went by it is kept has that
     * worked out again. One eye's records share a place in records with no more than three
     * others, and two eyes' fit RECORD_WAYS.
     */
    private static final int KEPT_AROUND = 40;
    /** The chunk each view's eye was last over, where it has told of one (heighten). */
    private static final int[] eyeChunkX = new int[View.COUNT], eyeChunkY = new int[View.COUNT];
    private static final boolean[] eyed = new boolean[View.COUNT];
    /** What scan reads a chunk into again, to set beside its record, and the records it found otherwise. */
    private static final Kinds passing = new Kinds(0, 0);
    private static final ArrayList<Kinds> otherwise = new ArrayList<>();
    /** The records of a chunk and of the eight round it, x first, that deriveFrom works from. */
    private static final Kinds[] ring = new Kinds[9];
    /**
     * The chunks whose records came out otherwise or were let go, one after another, the last
     * LOG of them, and how many there have been: see Reads.
     */
    private static final int LOG = 1 << 14;
    private static final long[] changeLog = new long[LOG];
    private static long logged;
    /**
     * The chunks whose records drop let go of or set back since the log was last added to.
     * They go in the log only when nothing is being worked out (flush): what is being worked
     * out went by them as they were, and holds to the log as it was when it began.
     */
    private static long[] pending = new long[64];
    private static int pendingCount;
    /** What mapAsOf and MapAhead.version were at the last scan. */
    private static long scannedAsOf = Long.MIN_VALUE, scannedAhead = Long.MIN_VALUE;

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
    /** Which squares of around are of a thing of level ground with a box: see fill. */
    private static final boolean[] levelAround = new boolean[SPAN * SPAN];
    private static final Footings footings = new Footings();
    /** The map Footings is asking about while footed runs, and the record of the chunk it last asked about: it asks about the squares of one chunk in a row. */
    private static IsoCell siteCell;
    private static Kinds siteKinds;
    private static final Footings.Site site = new Footings.Site() {
        @Override
        public byte over(int x, int y) {
            return siteKinds(x, y).over[inChunk(x, y)];
        }

        @Override
        public long house(int x, int y) {
            return siteKinds(x, y).house[inChunk(x, y)];
        }

        @Override
        public byte levelKind(int x, int y) {
            return siteKinds(x, y).level[inChunk(x, y)];
        }

        @Override
        public boolean paved(int x, int y) {
            byte kind = siteKinds(x, y).kinds[inChunk(x, y)];
            return !Paving.without && (kind == STREET || kind == MADE);
        }

        @Override
        public long changes() {
            return logged;
        }

        @Override
        public boolean asItWas(int lowX, int lowY, int highX, int highY, long since) {
            return unchanged(Math.floorDiv(lowX, CHUNK_TILES), Math.floorDiv(lowY, CHUNK_TILES), Math.floorDiv(highX, CHUNK_TILES), Math.floorDiv(highY, CHUNK_TILES), since);
        }
    };

    /** The record of the chunk a square is in, for site. */
    private static Kinds siteKinds(int x, int y) {
        int wx = Math.floorDiv(x, CHUNK_TILES), wy = Math.floorDiv(y, CHUNK_TILES);
        if (siteKinds == null || siteKinds.wx != wx || siteKinds.wy != wy) {
            siteKinds = kinds(siteCell, wx, wy);
        }
        return siteKinds;
    }

    /**
     * A square of the ground, or null while the map is still filling its chunk: such a square
     * may have no floor or room yet, and what it was read as would be kept as what it is. The
     * chunk counts as loaded, and so is read again, once the game has finished it.
     */
    private static IsoGridSquare ground(IsoCell cell, int x, int y) {
        IsoGridSquare square = cell.getGridSquare(x, y, 0);
        return square != null && square.getChunk().loaded ? square : null;
    }

    /** Where a square of the map is in its chunk, as Kinds and MapAhead.Guess keep squares. */
    private static int inChunk(int x, int y) {
        return Math.floorMod(x, CHUNK_TILES) * CHUNK_TILES + Math.floorMod(y, CHUNK_TILES);
    }

    /** The footing of each made thing worked out, by the middle of the thing: see plainPad and freePad. */
    private final LongTable<Pad> pads = new LongTable<>();
    /**
     * How many things pads has, and past how many it lets them all go, to be worked out again as
     * they are asked for: a drive meets things for as long as it goes on, and a pad of a thing
     * left behind is never asked for again.
     */
    private int padsKept;
    private static final int PADS_KEPT = 1 << 14;
    private static final byte[] kerbKinds = new byte[Kerbs.SPAN * Kerbs.SPAN];
    private static final boolean[] kerbHard = new boolean[Kerbs.SPAN * Kerbs.SPAN];
    private static final boolean[] kerbFootway = new boolean[Kerbs.SPAN * Kerbs.SPAN];
    private static final byte[] kerbMarks = new byte[Kerbs.SPAN * Kerbs.SPAN];
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

    /** Dev only: heights that hold are worked out again this many frames after they last were, to say so if they come out otherwise (AUDITED). */
    private static final int RETRIED = 30;
    private static int frame;
    /**
     * The longest a frame spends working heights out again for chunks that have them, in
     * nanoseconds. When the map loads a row of chunks every chunk near its edge is due at
     * once, most of those in sight among them, and all in one frame they hold it: the rest
     * keep the heights they have until the next frame, and are no worse off than the frame before.
     */
    private static final long AGAIN_NANOS_A_FRAME = 1_500_000L;
    private static long againNanos;
    /** Dev only, for the rig: heights worked out for chunks that had none and again for chunks that had, how long each took, and how many came from what they had before, and how many of those the fold told without working them out. */
    private static long tollFirst, tollFirstNanos, tollAgain, tollAgainNanos, tollAsBefore, tollFolded;
    /**
     * Dev only, for the rig: chunks read into new records, read again by scan and found
     * otherwise, records let go, records whose footings were found again, how long scan took,
     * and how many times the map was asked whether a chunk is loaded, by scan and by each read.
     */
    private static long tollRead, tollReadAgain, tollOtherwise, tollDropped, tollFooted, tollScanNanos, tollAsked;
    /**
     * A chunk worked out this many frames ago is worked out whatever the frame has spent, one
     * such chunk a frame: the chunks are asked for in one order every frame, and the last of
     * them would wait for ever behind the first coming due again.
     */
    private static final int AGAIN_AT_THE_LATEST = 8 * RETRIED;
    private static boolean overdueTaken;
    private static int loaded;
    private static int versions;
    /** Dev only: heights that hold are worked out again on the clock, and the map is read again every frame, to say so if they then come out otherwise. */
    private static final boolean AUDITED = ChunkMarks.COUNTED;

    /**
     * Logic thread, once a frame the picture is built: a number that is another whenever the
     * map has another set of chunks loaded. The records are asked whether they are as read
     * (scan) when it has changed, with the game telling of its changes, and those not loaded
     * when MapAhead's version has.
     */
    static void loaded(int epoch) {
        loaded = epoch;
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
     * Logic thread, as a view is built with its eye at (eyeX, eyeY): keeps the records round
     * the eye (scan), heightens when the gain is another, and whenever the gain has changed
     * since the heights round this view's eye were last worked out again, has those of the
     * chunk the eye is over and of the eight round it worked out again by now, from `from`
     * (fromMap and the map). The views share the land, and each eye has its own. The rest are
     * worked out as they are asked for, and a build asks for the nearest first: the land an eye
     * stands in and the water beside it are never at two heights. Whether the gain changed.
     */
    <C> boolean heighten(float gain, int view, float eyeX, float eyeY, C from, Rework<C> now) {
        int wx = Math.floorDiv((int) Math.floor(eyeX), CHUNK_TILES), wy = Math.floorDiv((int) Math.floor(eyeY), CHUNK_TILES);
        eyeChunkX[view] = wx;
        eyeChunkY[view] = wy;
        eyed[view] = true;
        boolean changed = gain != this.gain;
        if (changed) {
            heighten(gain);
        }
        if (roundEyeAsOf[view] == changes) {
            return changed;
        }
        roundEyeAsOf[view] = changes;
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
        padsKept = 0;
    }

    static Terrain of(boolean level) {
        return level ? LEVEL : HILLS;
    }

    /** For a map loaded anew: lets go of every height worked out and of every record. Logic thread. */
    static void forget() {
        for (int slot = 0; slot < TABLE_SIDE * TABLE_SIDE * WAYS; slot++) {
            HILLS.grids.set(slot, null);
            LEVEL.grids.set(slot, null);
        }
        Arrays.fill(records, null);
        kept.clear();
        pendingCount = 0;
        siteKinds = null;
        scannedAsOf = scannedAhead = Long.MIN_VALUE;
        Arrays.fill(eyed, false);
        footings.forget();
        for (Terrain terrain : new Terrain[] {HILLS, LEVEL}) {
            terrain.pads.clear();
            terrain.padsKept = 0;
        }
    }

    /**
     * For what is over a square told otherwise with the map as it is (a parasol's canopy is
     * built ground or not by a row of [Content]): works out again what is over each square of
     * every record, and logs each that comes out otherwise, so that what went by it is worked
     * out again as it is asked for. The heights kept stay for any thread to read until then,
     * and keep their version where they come out the same. Logic thread.
     */
    static void reread() {
        for (Kinds kinds : kept) {
            if (kinds.derived && derive(kinds)) {
                log(kinds.wx, kinds.wy);
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

    /** The first of the RECORD_WAYS places a chunk's record may be kept in. */
    private static int recordSlot(int wx, int wy) {
        return ((wx & (TABLE_SIDE - 1)) * TABLE_SIDE + (wy & (TABLE_SIDE - 1))) * RECORD_WAYS;
    }

    /** The record of a chunk, or null. Logic thread. */
    private static Kinds recordOf(int wx, int wy) {
        int slot = recordSlot(wx, wy);
        for (int way = 0; way < RECORD_WAYS; way++) {
            Kinds kinds = records[slot + way];
            if (kinds != null && kinds.wx == wx && kinds.wy == wy) {
                return kinds;
            }
        }
        return null;
    }

    /**
     * The record of a chunk, read now where there is none, in a place free or in place of the
     * record asked for longest ago. Logic thread.
     */
    private static Kinds record(IsoCell cell, int wx, int wy) {
        Kinds kinds = recordOf(wx, wy);
        if (kinds == null) {
            kinds = new Kinds(wx, wy);
            read(cell, kinds);
            tollRead++;
            int slot = recordSlot(wx, wy), into = -1;
            for (int way = 0; way < RECORD_WAYS && into < 0; way++) {
                into = records[slot + way] == null ? slot + way : -1;
            }
            if (into < 0) {
                into = slot;
                for (int way = 1; way < RECORD_WAYS; way++) {
                    into = records[slot + way].used < records[into].used ? slot + way : into;
                }
                drop(records[into]);
            }
            records[into] = kinds;
            kinds.index = kept.size();
            kept.add(kinds);
        }
        kinds.used = frame;
        return kinds;
    }

    /**
     * Lets go of a record. Its chunk goes in the log, and so does each derived record round it,
     * which is set back to be derived again when it is next asked for: when they are, the chunk
     * let go of is read anew. See pending.
     */
    private static void drop(Kinds kinds) {
        int slot = recordSlot(kinds.wx, kinds.wy);
        for (int way = 0; way < RECORD_WAYS; way++) {
            if (records[slot + way] == kinds) {
                records[slot + way] = null;
            }
        }
        Kinds last = kept.remove(kept.size() - 1);
        if (last != kinds) {
            kept.set(kinds.index, last);
            last.index = kinds.index;
        }
        siteKinds = null;
        pend(kinds.wx, kinds.wy);
        for (int cx = -1; cx <= 1; cx++) {
            for (int cy = -1; cy <= 1; cy++) {
                Kinds near = recordOf(kinds.wx + cx, kinds.wy + cy);
                if (near != null && near.derived) {
                    near.derived = false;
                    pend(near.wx, near.wy);
                }
            }
        }
        tollDropped++;
    }

    private static void pend(int wx, int wy) {
        if (pendingCount == pending.length) {
            pending = Arrays.copyOf(pending, pendingCount * 2);
        }
        pending[pendingCount++] = chunkKey(wx, wy);
    }

    /** Puts the chunks pending in the log. Logic thread, with nothing being worked out. */
    private static void flush() {
        for (int at = 0; at < pendingCount; at++) {
            log((int) (pending[at] >> 32), (int) pending[at]);
        }
        pendingCount = 0;
    }

    private static void log(int wx, int wy) {
        changeLog[(int) (logged & (LOG - 1))] = chunkKey(wx, wy);
        logged++;
    }

    /**
     * Whether no chunk from (lowX, lowY) to (highX, highY) has been logged since the log had
     * had since chunks. False where the log no longer reaches back that far.
     */
    private static boolean unchanged(int lowX, int lowY, int highX, int highY, long since) {
        if (logged - since > LOG) {
            return false;
        }
        for (long at = since; at < logged; at++) {
            long chunk = changeLog[(int) (at & (LOG - 1))];
            int wx = (int) (chunk >> 32), wy = (int) chunk;
            if (wx >= lowX && wx <= highX && wy >= lowY && wy <= highY) {
                return false;
            }
        }
        return true;
    }

    private static long chunkKey(int wx, int wy) {
        return (long) wx << 32 | wy & 0xFFFFFFFFL;
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

    /** The lanes of a chunk's roads, as Lanes keeps them, and null where none of its squares is in one. After corners. */
    float[] lanes(int wx, int wy) {
        return grid(wx, wy).lanes;
    }

    /** The kerbs and the lanes of a chunk as one number, which is another when they run otherwise. After corners. */
    long kerbSum(int wx, int wy) {
        return grid(wx, wy).kerbSum;
    }

    /** The height of water at the corners of a chunk, laid out as corners gives the land's. After corners. */
    float[] water(int wx, int wy) {
        return grid(wx, wy).water;
    }

    /**
     * The corner heights of a chunk, CORNERS by CORNERS with x first, worked out if they are
     * not yet or if a chunk they went by has come out otherwise since they last were. Logic
     * thread.
     */
    float[] corners(IsoCell cell, int wx, int wy) {
        return corners(cell, wx, wy, false);
    }

    /** The same, and with now whatever the frame has spent on heights worked out again. */
    private float[] corners(IsoCell cell, int wx, int wy, boolean now) {
        scan(cell);
        if (padsKept > PADS_KEPT) {
            pads.clear();
            padsKept = 0;
        }
        Grid grid = grid(wx, wy);
        boolean known = grid != null;
        if (known) {
            grid.used = frame;
            if (!grid.stale && (grid.gain != gain || !grid.reads.hold())) {
                grid.stale = true;
            }
        }
        boolean audit = known && !grid.stale && AUDITED && frame - grid.stamp >= RETRIED;
        if (known && !grid.stale && !audit) {
            return grid.heights;
        }
        if (known && !now && againNanos > AGAIN_NANOS_A_FRAME) {
            if (overdueTaken || frame - grid.stamp < AGAIN_AT_THE_LATEST) {
                return grid.heights;
            }
            overdueTaken = true;
        }
        long began = System.nanoTime();
        Reads reads = new Reads(logged);
        // In this order: lookOver looks for the water round each building, and the corners want the water round the chunk.
        lookOver(cell, wx, wy, reads);
        findWater(cell, wx, wy, reads);
        long folded = fold();
        boolean foldAsBefore = known && grid.folded == folded;
        if (foldAsBefore && !AUDITED) {
            // All the heights, the water and the kerbs go by is as it was when they were last worked out, and so are they.
            grid.reads = reads;
            grid.stale = false;
            grid.stamp = frame;
            joinEdges(grid);
            long took = System.nanoTime() - began;
            againNanos += took;
            tollAgain++;
            tollAgainNanos += took;
            tollAsBefore++;
            tollFolded++;
            return grid.heights;
        }
        Grid fresh = new Grid();
        fresh.wx = wx;
        fresh.wy = wy;
        fresh.stamp = frame;
        fresh.used = frame;
        fresh.gain = gain;
        fresh.reads = reads;
        fresh.folded = folded;
        fill();
        // The gain with them: the same squares give other heights at another.
        fresh.from = gathered() * 31 + Float.floatToRawIntBits(gain);
        boolean asBefore = known && grid.from == fresh.from;
        if (asBefore) {
            tollAsBefore++;
        }
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
        if (audit && !same) {
            Log.info("terrain: MISSED the heights at " + wx + ", " + wy + ", which came out otherwise with nothing they went by changed");
        }
        if (foldAsBefore && (fresh.from != grid.from || fresh.kerbFrom != grid.kerbFrom)) {
            Log.info("terrain: MISSED the ground at " + wx + ", " + wy + ", which came out otherwise with the fold as it was");
        }
        keep(fresh);
        long took = System.nanoTime() - began;
        if (known) {
            againNanos += took;
            tollAgain++;
            tollAgainNanos += took;
        } else {
            tollFirst++;
            tollFirstNanos += took;
        }
        return fresh.heights;
    }

    /** Dev only: what tollFirst and the rest have come to since this was last asked. */
    static String tolls() {
        String told = String.format(java.util.Locale.ROOT,
                "heights first %d in %.1f ms, again %d in %.1f ms (from what they had %d, by the fold %d), chunks read %d, read again and otherwise %d of %d in %.1f ms, let go %d, footings found for %d, asked whether loaded %d",
                tollFirst, tollFirstNanos / 1e6, tollAgain, tollAgainNanos / 1e6, tollAsBefore, tollFolded, tollRead, tollOtherwise, tollReadAgain, tollScanNanos / 1e6, tollDropped,
                tollFooted, tollAsked);
        tollFirst = tollFirstNanos = tollAgain = tollAgainNanos = tollAsBefore = tollFolded = 0;
        tollRead = tollReadAgain = tollOtherwise = tollDropped = tollFooted = tollScanNanos = tollAsked = 0;
        return told;
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
     * Works out where the kerbs of a chunk run. After fill. The line of a kerb is shaped by
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
        for (int cx = -reach; cx <= reach; cx++) {
            for (int cy = -reach; cy <= reach; cy++) {
                Kinds kinds = kinds(cell, fresh.wx + cx, fresh.wy + cy);
                for (int i = 0; i < CHUNK_TILES; i++) {
                    for (int j = 0; j < CHUNK_TILES; j++) {
                        int at = ((cx + reach) * CHUNK_TILES + i) * Kerbs.SPAN + (cy + reach) * CHUNK_TILES + j;
                        kerbKinds[at] = kinds.kinds[i * CHUNK_TILES + j];
                        kerbHard[at] = kinds.hard[i * CHUNK_TILES + j];
                        kerbFootway[at] = kinds.footway[i * CHUNK_TILES + j];
                        kerbMarks[at] = kinds.marks[i * CHUNK_TILES + j];
                    }
                }
            }
        }
        long from = kerbFrom(kerbKinds, kerbHard, kerbFootway, kerbMarks);
        fresh.kerbFrom = from;
        if (last != null && last.kerbFrom == from) {
            fresh.kerb = last.kerb;
            fresh.lanes = last.lanes;
            fresh.kerbSum = last.kerbSum;
            return;
        }
        fresh.kerb = Kerbs.field(kerbKinds, kerbHard, fresh.wx * CHUNK_TILES, fresh.wy * CHUNK_TILES);
        fresh.lanes = Lanes.of(kerbKinds, kerbFootway, kerbMarks, fresh.wx * CHUNK_TILES, fresh.wy * CHUNK_TILES);
        fresh.kerbSum = kerbSum(fresh.kerb, fresh.lanes);
    }

    /** What kerbs and Lanes work a chunk's kerbs and lanes out from, as one number. */
    static long kerbFrom(byte[] kinds, boolean[] hard, boolean[] footway, byte[] marks) {
        long from = 0x9E3779B97F4A7C15L;
        for (int at = 0; at < kinds.length; at++) {
            from = (from ^ kinds[at] + (hard[at] ? 8 : 0) + (footway[at] ? 16 : 0) + ((marks[at] & 0xFF) << 5)) * 0x100000001B3L;
        }
        return from;
    }

    /** A chunk's kerbs and lanes, either of them null for none, as one number: 0 for neither. */
    static long kerbSum(float[] kerb, float[] lanes) {
        if (kerb == null && lanes == null) {
            return 0L;
        }
        long sum = 0x9E3779B97F4A7C15L;
        for (float[] values : new float[][] {kerb, lanes}) {
            sum = (sum ^ (values == null ? 1 : values.length)) * 0x100000001B3L;
            if (values != null) {
                for (float value : values) {
                    sum = (sum ^ Float.floatToRawIntBits(value)) * 0x100000001B3L;
                }
            }
        }
        return sum;
    }

    /** Fills streetAround, for an around whose first square is (x, y) of the map, and has Paving look around over, for any corner of it to be asked for. After findWater. */
    void streets(int x, int y) {
        streets(x, y, 0, SPAN - 1, true);
    }

    /**
     * The same for the corners numbered from to to, either way, and no others. Without
     * paving, the heights asked for after are the land's and the streets' own, with no made
     * ground laid to anything built: what a thing stands at that takes its height from the
     * ground it stands on (lookOver).
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
        float toMade = REACH, toWater = REACH, toPad = REACH, toBuilt = REACH, toStreet = REACH, toStanding = REACH;
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
                    if (!levelAround[at]) {
                        toStanding = Math.min(toStanding, away);
                    }
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
        float laid = land;
        if (nearWeight > 0f) {
            float pad = nearPads / nearWeight;
            laid = pad + (land - pad) * smooth(toPad / Math.min(REACH, toPad + toStreet));
        }
        laid = Paving.onto(laid, cornerI, cornerJ);
        // Ground laid level on purpose is at its footing, and the land eases back in round it, but what is built near it keeps
        // its own footing and the ground up to its walls comes to it over REACH, as to any footing.
        float level = levelWeight(x, y), levelPad = this.levelPad[0];
        return levelPad + (laid - levelPad) * (1f - (1f - level) * toStanding / REACH);
    }

    /** The chunks within AROUND of the chunk in hand, a side of them, x first: see lookOver. */
    private static final int REGION = 2 * (AROUND / CHUNK_TILES) + 1;
    private final Kinds[] region = new Kinds[REGION * REGION];
    /** The footing of each run of squares over one made thing, in the order lookOver met them, and how many: see fill. */
    private float[] runPads = new float[64];
    private int runs;

    /**
     * Looks over what each square within AROUND of the chunk is and the footing each stands
     * on, for fill to lay out in around and padsAround, and adds to reads the chunks it went by.
     *
     * A made thing that stands at the height of the ground under it (Footings.free) has that
     * height from the land and the streets alone, with nothing standing and no made ground
     * laid to anything: so what stands round the chunk is first looked over, each such thing's
     * footing is read off the chunks round its own middle (freePad), and then the footing of
     * each run of squares over one thing is taken in turn.
     */
    private void lookOver(IsoCell cell, int wx, int wy, Reads reads) {
        // The footings of things that stand at the height of the ground under them are the land's and the streets' alone.
        groundCount = 0;
        region(cell, wx, wy, reads);
        freeAsked = 0;
        long last = Footings.NONE;
        for (int row = 0; row < SPAN; row++) {
            for (int across = 0; across < REGION; across++) {
                Kinds kinds = region[row / CHUNK_TILES * REGION + across];
                if (!kinds.anyFree) {
                    continue;
                }
                for (int at = row % CHUNK_TILES * CHUNK_TILES; at < (row % CHUNK_TILES + 1) * CHUNK_TILES; at++) {
                    if (!kinds.free[at]) {
                        continue;
                    }
                    long key = Footings.key(kinds.midX[at], kinds.midY[at]);
                    // A thing's squares lie side by side: the one before was most often the same thing's.
                    if (key == last) {
                        continue;
                    }
                    last = key;
                    boolean asked = false;
                    for (int earlier = 0; earlier < freeAsked && !asked; earlier++) {
                        asked = freeKeys[earlier] == key;
                    }
                    if (!asked) {
                        if (freeAsked == freeKeys.length) {
                            freeKeys = Arrays.copyOf(freeKeys, freeAsked * 2);
                            freeBoxes = Arrays.copyOf(freeBoxes, freeAsked * 2 * 4);
                            freePads = Arrays.copyOf(freePads, freeAsked * 2);
                        }
                        System.arraycopy(kinds.rulerBox, at * 4, freeBoxes, freeAsked * 4, 4);
                        freeKeys[freeAsked++] = key;
                    }
                }
            }
        }
        // All of them before any run: working one out lays the ground round its middle in around.
        for (int asked = 0; asked < freeAsked; asked++) {
            freePads[asked] = freePad(cell, freeKeys[asked], freeBoxes, asked * 4);
            reads.add(freePads[asked].reads);
        }
        // The footings as fill lays them, one run of squares over the same thing at a time.
        runs = 0;
        float lastX = Float.NaN, lastY = Float.NaN;
        for (int row = 0; row < SPAN; row++) {
            for (int across = 0; across < REGION; across++) {
                Kinds kinds = region[row / CHUNK_TILES * REGION + across];
                if (!kinds.anyThing) {
                    continue;
                }
                for (int at = row % CHUNK_TILES * CHUNK_TILES; at < (row % CHUNK_TILES + 1) * CHUNK_TILES; at++) {
                    float x = kinds.midX[at], y = kinds.midY[at];
                    if (Float.isNaN(x) || x == lastX && y == lastY) {
                        continue;
                    }
                    long key = Footings.key(x, y);
                    Pad pad = kinds.free[at] ? freePadOf(key) : plainPad(cell, x, y, key);
                    reads.add(pad.reads);
                    lastX = x;
                    lastY = y;
                    if (runs == runPads.length) {
                        runPads = Arrays.copyOf(runPads, runs * 2);
                    }
                    runPads[runs++] = pad.pad;
                }
            }
        }
    }

    /** Has region hold the records of the chunks within AROUND of the chunk, each with its footings found, and adds to reads the chunks they went by. */
    private void region(IsoCell cell, int wx, int wy, Reads reads) {
        int reach = AROUND / CHUNK_TILES;
        reads.add(wx - reach, wy - reach, wx + reach, wy + reach);
        for (int cx = -reach; cx <= reach; cx++) {
            for (int cy = -reach; cy <= reach; cy++) {
                Kinds kinds = kinds(cell, wx + cx, wy + cy);
                footed(cell, kinds);
                reads.add(kinds.footReads);
                region[(cx + reach) * REGION + cy + reach] = kinds;
            }
        }
    }

    /**
     * The things of level ground with a square among those gathered round the chunk in hand,
     * each as its box's first corner and last and its footing, five to a thing, and how many:
     * see corner. Footings gives a box only to a thing some of which every chunk whose heights
     * the box changes gathers, so each such chunk has the same of them.
     */
    private float[] grounds = new float[Grounds.STRIDE * 8];
    private int groundCount;

    private void addGround(float[] box, int from, float pad) {
        for (int at = 0; at < groundCount * 5; at += 5) {
            if (grounds[at] == box[from] && grounds[at + 1] == box[from + 1] && grounds[at + 2] == box[from + 2] && grounds[at + 3] == box[from + 3] && grounds[at + 4] == pad) {
                return;
            }
        }
        if (groundCount * 5 == grounds.length) {
            grounds = Arrays.copyOf(grounds, grounds.length * 2);
        }
        System.arraycopy(box, from, grounds, groundCount * 5, 4);
        grounds[groundCount * 5 + 4] = pad;
        groundCount++;
    }

    /** The footing levelWeight found. */
    private final float[] levelPad = new float[1];

    /** How far the land at (x, y) keeps its own height by the ground laid level round it, its footing in levelPad: see Grounds.weight. */
    private float levelWeight(float x, float y) {
        return Grounds.weight(grounds, groundCount, x, y, GROUND_EASE, levelPad);
    }

    /** How far apart the land is read over a box laid level, in tiles: see meanLand. */
    private static final float MEAN_STEP = 2f;

    /**
     * The footing of a box of ground laid level: the land's height over it on the whole, with
     * what is uneven on open ground left out, so that what is cut away on one side of it and
     * built up on the other come out alike. After findWater for the chunk the box's middle is
     * in, whichever chunk asks.
     */
    private float meanLand(float[] boxes, int from) {
        float lowX = boxes[from], lowY = boxes[from + 1], highX = boxes[from + 2], highY = boxes[from + 3];
        int stepsX = Math.max(1, Math.round((highX - lowX) / MEAN_STEP)), stepsY = Math.max(1, Math.round((highY - lowY) / MEAN_STEP));
        double sum = 0.0;
        for (int i = 0; i <= stepsX; i++) {
            for (int j = 0; j <= stepsY; j++) {
                sum += base(lowX + (highX - lowX) * i / stepsX, lowY + (highY - lowY) * j / stepsY);
            }
        }
        return (float) (sum / ((stepsX + 1) * (stepsY + 1)));
    }

    /** Fills around, footwayAround and padsAround with what lookOver found. */
    private void fill() {
        groundCount = 0;
        int run = 0;
        float lastX = Float.NaN, lastY = Float.NaN, lastPad = Float.NaN;
        float lastLowX = Float.NaN, lastLowY = Float.NaN, lastHighX = Float.NaN, lastHighY = Float.NaN;
        for (int row = 0; row < SPAN; row++) {
            for (int across = 0; across < REGION; across++) {
                Kinds kinds = region[row / CHUNK_TILES * REGION + across];
                int at = row % CHUNK_TILES * CHUNK_TILES;
                for (int square = row * SPAN + across * CHUNK_TILES; square < row * SPAN + (across + 1) * CHUNK_TILES; square++, at++) {
                    around[square] = kinds.kinds[at];
                    footwayAround[square] = kinds.footway[at];
                    levelAround[square] = !Float.isNaN(kinds.box[at * 4]);
                    float x = kinds.midX[at], y = kinds.midY[at];
                    if (Float.isNaN(x)) {
                        padsAround[square] = Float.NaN;
                        continue;
                    }
                    if (x != lastX || y != lastY) {
                        lastX = x;
                        lastY = y;
                        lastPad = runPads[run++];
                    }
                    // The things of a chain share a middle, their ruler's, and one run of squares may go over two of them.
                    float lowX = kinds.box[at * 4];
                    if (!Float.isNaN(lowX) && (lowX != lastLowX || kinds.box[at * 4 + 1] != lastLowY || kinds.box[at * 4 + 2] != lastHighX || kinds.box[at * 4 + 3] != lastHighY)) {
                        lastLowX = lowX;
                        lastLowY = kinds.box[at * 4 + 1];
                        lastHighX = kinds.box[at * 4 + 2];
                        lastHighY = kinds.box[at * 4 + 3];
                        addGround(kinds.box, at * 4, lastPad);
                    }
                    padsAround[square] = lastPad;
                }
            }
        }
        Grounds.order(grounds, groundCount);
    }

    /**
     * All that a chunk's heights and its kerbs are worked out from, as one number, after
     * lookOver and findWater for the chunk: what every chunk within AROUND is and what footed
     * found standing over it, the footing of each run of squares over one thing, where the
     * water lies, how much is built round about, and the gain. What fill lays out and gathered
     * sums goes by nothing else, so the heights do not either, and the kerbs go by the
     * squares of the chunks within Kerbs.MARGIN, which are among them. A pad that a thing
     * far off gives a square here is in it as the pad, wherever it was found from.
     */
    private long fold() {
        long sum = mix(Paving.without ? 1L : 0L, Float.floatToRawIntBits(gain));
        for (Kinds kinds : region) {
            sum = mix(mix(sum, kinds.sum), kinds.footSum);
        }
        for (int run = 0; run < runs; run++) {
            sum = mix(sum, Float.floatToRawIntBits(runPads[run]));
        }
        sum = mix(sum, waterChunks);
        for (int at = 0; at < waterChunks; at++) {
            sum = mix(mix(sum, waterChunkX[at]), waterChunkY[at]);
        }
        for (int built : builtChunks) {
            sum = mix(sum, built);
        }
        return sum;
    }

    /**
     * All that a chunk's heights are worked out from, as one number, after fill and
     * findWater for the chunk: what every square gathered is, the footing each stands on,
     * where the water lies, and how much is built in the chunks round it. Nothing else goes
     * into a height: base reads water and the built chunks findWater gathered, and those reach
     * WATER_CHUNKS, within what is gathered. A chunk near the edge of the loaded map is looked at again
     * whenever the map loads more, and most often none of this has changed.
     */
    private long gathered() {
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
        for (int at = 0; at < groundCount * 5; at++) {
            sum = (sum ^ Float.floatToRawIntBits(grounds[at])) * 0x100000001B3L;
        }
        return sum;
    }

    /** The things that stand at the height of the ground under them that lookOver has met, the box each lays level, four to a thing, NaN where it has none, the footing of each, and how many. */
    private long[] freeKeys = new long[8];
    private float[] freeBoxes = new float[8 * 4];
    private Pad[] freePads = new Pad[8];
    private int freeAsked;

    /**
     * Fills around, footwayAround and padsAround for freePad, for the chunk and the eight round
     * it and no further: bare, with nothing standing that takes its height from the ground under
     * it. Adds to reads the chunks it went by.
     */
    private void lay(IsoCell cell, int wx, int wy, Reads reads) {
        reads.add(wx - 1, wy - 1, wx + 1, wy + 1);
        for (int cx = -1; cx <= 1; cx++) {
            for (int cy = -1; cy <= 1; cy++) {
                Kinds kinds = kinds(cell, wx + cx, wy + cy);
                footed(cell, kinds);
                reads.add(kinds.footReads);
                for (int i = 0; i < CHUNK_TILES; i++) {
                    for (int j = 0; j < CHUNK_TILES; j++) {
                        int at = (cx * CHUNK_TILES + i + AROUND) * SPAN + cy * CHUNK_TILES + j + AROUND;
                        around[at] = kinds.kinds[i * CHUNK_TILES + j];
                        midXAround[at] = kinds.midX[i * CHUNK_TILES + j];
                        midYAround[at] = kinds.midY[i * CHUNK_TILES + j];
                        freeAround[at] = kinds.free[i * CHUNK_TILES + j];
                        footwayAround[at] = kinds.footway[i * CHUNK_TILES + j];
                    }
                }
            }
        }
        // Only now: working out a pad looks at other chunks, and lays their water over this one's.
        float lastX = Float.NaN, lastY = Float.NaN, lastPad = Float.NaN;
        int first = AROUND - CHUNK_TILES, past = AROUND + 2 * CHUNK_TILES;
        for (int row = first; row < past; row++) {
            for (int at = row * SPAN + first; at < row * SPAN + past; at++) {
                float x = midXAround[at], y = midYAround[at];
                if (Float.isNaN(x)) {
                    padsAround[at] = Float.NaN;
                    continue;
                }
                if (x != lastX || y != lastY) {
                    lastX = x;
                    lastY = y;
                    lastPad = Float.NaN;
                    if (!freeAround[at]) {
                        Pad pad = plainPad(cell, x, y, Footings.key(x, y));
                        reads.add(pad.reads);
                        lastPad = pad.pad;
                    }
                }
                padsAround[at] = lastPad;
            }
        }
    }

    /** The footing of a made thing that does not stand free, its middle at (x, y) as key packs it: the height of the land there. */
    private Pad plainPad(IsoCell cell, float x, float y, long key) {
        Pad pad = pads.get(key);
        if (pad != null && pad.fits(PLAIN, null, 0) && pad.reads.hold()) {
            return pad;
        }
        padsKept += pad == null ? 1 : 0;
        Reads reads = new Reads(logged);
        findWater(cell, Math.floorDiv((int) Math.floor(x), CHUNK_TILES), Math.floorDiv((int) Math.floor(y), CHUNK_TILES), reads);
        pad = new Pad(base(x, y), PLAIN, null, 0, reads);
        pads.put(key, pad);
        return pad;
    }

    /**
     * The footing of a made thing that stands at the height the ground has under its middle
     * with nothing standing (Footings.free), the middle as key packs it, and the box it lays
     * level from from in boxes, NaN where it lays none: the land over the box (meanLand), or
     * the land and the streets at the corner of the map nearest the middle, read off the chunks
     * round it alone (lay).
     */
    private Pad freePad(IsoCell cell, long key, float[] boxes, int from) {
        byte how = Float.isNaN(boxes[from]) ? BARE : BOXED;
        Pad pad = pads.get(key);
        if (pad != null && pad.fits(how, boxes, from) && pad.reads.hold()) {
            return pad;
        }
        padsKept += pad == null ? 1 : 0;
        // The corner of the map nearest the thing's middle, in the chunk that has it for one of its own.
        int x = Math.round(Footings.keyX(key)), y = Math.round(Footings.keyY(key));
        int midWx = Math.floorDiv(x, CHUNK_TILES), midWy = Math.floorDiv(y, CHUNK_TILES);
        Reads reads = new Reads(logged);
        float footing;
        if (how == BOXED) {
            findWater(cell, midWx, midWy, reads);
            footing = meanLand(boxes, from);
        } else {
            // Only the chunks round the middle: the height asked for there is the land's and the street's own, which reach no further.
            lay(cell, midWx, midWy, reads);
            findWater(cell, midWx, midWy, reads);
            // The streets round that one corner, which is all that is read.
            int cornerI = x - midWx * CHUNK_TILES + AROUND, cornerJ = y - midWy * CHUNK_TILES + AROUND;
            streets(midWx * CHUNK_TILES - AROUND, midWy * CHUNK_TILES - AROUND, Math.min(cornerI, cornerJ), Math.max(cornerI, cornerJ), false);
            footing = corner(x, y, cornerI, cornerJ);
        }
        pad = new Pad(footing, how, boxes, from, reads);
        pads.put(key, pad);
        return pad;
    }

    /** The footing lookOver worked out for the free thing whose middle key packs. */
    private Pad freePadOf(long key) {
        for (int asked = 0; asked < freeAsked; asked++) {
            if (freeKeys[asked] == key) {
                return freePads[asked];
            }
        }
        throw new IllegalStateException("terrain: no footing was worked out for the thing at " + Footings.keyX(key) + ", " + Footings.keyY(key) + ", which stands free");
    }

    /**
     * Notes which chunks within WATER_CHUNKS of the chunk hold water and how much is built on
     * each, and adds them to reads. A chunk with a square not known counts as dry and unbuilt.
     */
    private static void findWater(IsoCell cell, int wx, int wy, Reads reads) {
        reads.add(wx - WATER_CHUNKS, wy - WATER_CHUNKS, wx + WATER_CHUNKS, wy + WATER_CHUNKS);
        waterChunks = 0;
        builtFromX = wx - WATER_CHUNKS;
        builtFromY = wy - WATER_CHUNKS;
        Arrays.fill(townKnown, false);
        for (int cx = -WATER_CHUNKS; cx <= WATER_CHUNKS; cx++) {
            for (int cy = -WATER_CHUNKS; cy <= WATER_CHUNKS; cy++) {
                Kinds kinds = kinds(cell, wx + cx, wy + cy);
                builtChunks[(cx + WATER_CHUNKS) * WATER_SIDE + cy + WATER_CHUNKS] = kinds.builtOn;
                if (kinds.water) {
                    waterChunkX[waterChunks] = (wx + cx) * CHUNK_TILES;
                    waterChunkY[waterChunks++] = (wy + cy) * CHUNK_TILES;
                }
            }
        }
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
     * The map's buildings, as MapAhead.Rooms has them, asked with the loaded map they are asked
     * for: the game's map (MapAhead.ROOMS), whatever the loaded map, where a test hands its own.
     */
    interface Rooms {
        long house(IsoCell cell, int x, int y, int z);
    }

    static Rooms rooms = (cell, x, y, z) -> MapAhead.ROOMS.house(x, y, z);

    /**
     * Reads what each square of the chunk of a record is, as the map has it now, of the square
     * and what is over it, into the record: where the chunk is loaded off its squares, where it
     * is not off what MapAhead read of it, and where it has read nothing every square as UNKNOWN.
     * A square of a loaded chunk's ground that the game has not made is UNKNOWN (isLoaded). The
     * rooms of a loaded chunk are the ones the map has (rooms), as the game gives them when the
     * chunk loads from the save (IsoChunk.LoadFromDiskOrBufferInternal): on a first load it gives
     * none to the squares the lotpack has no tile on (IsoCell.PlaceLot).
     */
    private static void read(IsoCell cell, Kinds into) {
        int wx = into.wx, wy = into.wy;
        boolean loaded = isLoaded(cell, wx, wy);
        tollAsked++;
        MapAhead.Guess guess = loaded ? null : MapAhead.guessed(wx, wy);
        into.chunkLoaded = loaded;
        into.guess = guess;
        if (guess != null) {
            into.source = READ_AHEAD;
            System.arraycopy(guess.kind, 0, into.kinds, 0, into.kinds.length);
            System.arraycopy(guess.footway, 0, into.footway, 0, into.footway.length);
            System.arraycopy(guess.marks, 0, into.marks, 0, into.marks.length);
            System.arraycopy(guess.house, 0, into.house, 0, into.house.length);
            System.arraycopy(guess.level, 0, into.level, 0, into.level.length);
            System.arraycopy(guess.above, 0, into.above, 0, into.above.length);
            System.arraycopy(guess.stands, 0, into.stands, 0, into.stands.length);
        } else if (!loaded) {
            into.source = UNREAD;
            Arrays.fill(into.kinds, UNKNOWN);
            Arrays.fill(into.footway, false);
            Arrays.fill(into.marks, (byte) 0);
            Arrays.fill(into.house, Footings.NONE);
            Arrays.fill(into.level, Grounds.NONE);
            Arrays.fill(into.above, Footings.OPEN);
            Arrays.fill(into.stands, (short) 0);
        } else {
            into.source = LOADED;
            above(cell, into, ground(cell, wx * CHUNK_TILES + CHUNK_TILES / 2, wy * CHUNK_TILES + CHUNK_TILES / 2).getChunk().getMaxLevel());
            for (int i = 0; i < CHUNK_TILES; i++) {
                for (int j = 0; j < CHUNK_TILES; j++) {
                    int x = wx * CHUNK_TILES + i, y = wy * CHUNK_TILES + j, at = i * CHUNK_TILES + j;
                    IsoGridSquare square = ground(cell, x, y);
                    byte kind = kind(square);
                    into.kinds[at] = kind;
                    into.footway[at] = kind == MADE || kind == STREET && footway(square);
                    into.marks[at] = kind == STREET ? marks(square) : 0;
                    if (square == null) {
                        into.house[at] = Footings.NONE;
                        into.level[at] = Grounds.NONE;
                        into.above[at] = Footings.OPEN;
                        into.stands[at] = 0;
                        continue;
                    }
                    long room = rooms.house(cell, x, y, 0);
                    into.house[at] = room != Footings.NONE ? room : rooms.house(cell, x, y, -1);
                    into.level[at] = groundOf(cell, square, x, y);
                    into.stands[at] = standsOf(square, room);
                }
            }
        }
        boolean whole = true, water = false;
        for (byte kind : into.kinds) {
            whole &= kind != UNKNOWN;
            water |= kind == WATER;
        }
        into.whole = whole;
        into.water = whole && water;
    }

    /**
     * Whether a chunk is loaded, by the middle square of its ground: the game makes every square
     * of a chunk's ground before it counts the chunk loaded (IsoChunk.loadInWorldStreamerThread).
     * Where that pass throws partway, the game logs what it threw and counts the chunk loaded
     * all the same (WorldStreamer.DoChunkAlways), with some squares of its ground not made.
     */
    private static boolean isLoaded(IsoCell cell, int wx, int wy) {
        return ground(cell, wx * CHUNK_TILES + CHUNK_TILES / 2, wy * CHUNK_TILES + CHUNK_TILES / 2) != null;
    }

    /**
     * Whether the chunk of a record has what it had when the record was read, as far as can be
     * told without reading its squares again, with chunkLoaded as the map is: loaded, or not
     * loaded and with what MapAhead holds of it the same, read ahead from the same read or not
     * read. A loaded chunk's squares are taken to be as they were read for as long as it stays
     * loaded.
     */
    private static boolean asRead(Kinds kinds) {
        if (kinds.source == LOADED) {
            return kinds.chunkLoaded;
        }
        return !kinds.chunkLoaded && MapAhead.guessed(kinds.wx, kinds.wy) == kinds.guess;
    }

    /**
     * Logic thread, at each corners before anything is worked out. When the map has other
     * chunks loaded since the last scan: lets go of the records far from every eye, asks the
     * map which of the rest are loaded, and reads again each whose chunk is not as it was read
     * (asRead). When only MapAhead has read more or let go: reads again each record not loaded
     * whose chunk MapAhead holds otherwise. Each that comes out otherwise goes in the log, and
     * so does each derived record round it whose over comes out otherwise with it.
     */
    private static void scan(IsoCell cell) {
        flush();
        long asOf = mapAsOf(), ahead = MapAhead.version();
        boolean moved = asOf != scannedAsOf;
        if (!moved && ahead == scannedAhead) {
            return;
        }
        scannedAsOf = asOf;
        scannedAhead = ahead;
        long began = System.nanoTime();
        if (moved) {
            // From the last, as drop moves the last record into the place it frees.
            for (int at = kept.size() - 1; at >= 0; at--) {
                if (farFromEveryEye(kept.get(at))) {
                    drop(kept.get(at));
                }
            }
            flush();
            for (Kinds kinds : kept) {
                kinds.chunkLoaded = isLoaded(cell, kinds.wx, kinds.wy);
            }
            tollAsked += kept.size();
        }
        otherwise.clear();
        for (Kinds kinds : kept) {
            if (!moved && kinds.source == LOADED || asRead(kinds)) {
                continue;
            }
            passing.wx = kinds.wx;
            passing.wy = kinds.wy;
            read(cell, passing);
            tollReadAgain++;
            kinds.source = passing.source;
            kinds.guess = passing.guess;
            kinds.chunkLoaded = passing.chunkLoaded;
            if (!sameSquares(kinds, passing)) {
                copySquares(passing, kinds);
                otherwise.add(kinds);
            }
        }
        for (Kinds kinds : otherwise) {
            kinds.derived = false;
            log(kinds.wx, kinds.wy);
        }
        for (Kinds kinds : otherwise) {
            for (int cx = -1; cx <= 1; cx++) {
                for (int cy = -1; cy <= 1; cy++) {
                    Kinds near = recordOf(kinds.wx + cx, kinds.wy + cy);
                    if (near != null && near.derived && derive(near)) {
                        log(near.wx, near.wy);
                    }
                }
            }
        }
        tollOtherwise += otherwise.size();
        tollScanNanos += System.nanoTime() - began;
    }

    /** Whether a record's chunk is more than KEPT_AROUND chunks from the eye of every view that has told of one. False while none has. */
    private static boolean farFromEveryEye(Kinds kinds) {
        boolean any = false;
        for (int view = 0; view < View.COUNT; view++) {
            if (eyed[view]) {
                any = true;
                if (Math.max(Math.abs(kinds.wx - eyeChunkX[view]), Math.abs(kinds.wy - eyeChunkY[view])) <= KEPT_AROUND) {
                    return false;
                }
            }
        }
        return any;
    }

    /** Whether two records have every square read the same. */
    private static boolean sameSquares(Kinds one, Kinds other) {
        return Arrays.equals(one.kinds, other.kinds) && Arrays.equals(one.footway, other.footway) && Arrays.equals(one.marks, other.marks)
                && Arrays.equals(one.house, other.house) && Arrays.equals(one.level, other.level) && Arrays.equals(one.above, other.above)
                && Arrays.equals(one.stands, other.stands);
    }

    private static void copySquares(Kinds from, Kinds into) {
        System.arraycopy(from.kinds, 0, into.kinds, 0, into.kinds.length);
        System.arraycopy(from.footway, 0, into.footway, 0, into.footway.length);
        System.arraycopy(from.marks, 0, into.marks, 0, into.marks.length);
        System.arraycopy(from.house, 0, into.house, 0, into.house.length);
        System.arraycopy(from.level, 0, into.level, 0, into.level.length);
        System.arraycopy(from.above, 0, into.above, 0, into.above.length);
        System.arraycopy(from.stands, 0, into.stands, 0, into.stands.length);
        into.whole = from.whole;
        into.water = from.water;
    }

    /** The record of a chunk, read now where there is none, with what is over each square worked out. Logic thread. */
    private static Kinds kinds(IsoCell cell, int wx, int wy) {
        Kinds kinds = record(cell, wx, wy);
        if (!kinds.derived) {
            for (int cx = -1; cx <= 1; cx++) {
                for (int cy = -1; cy <= 1; cy++) {
                    ring[(cx + 1) * 3 + cy + 1] = cx == 0 && cy == 0 ? kinds : record(cell, wx + cx, wy + cy);
                }
            }
            deriveFrom(ring);
        }
        return kinds;
    }

    /** Works out again what is over each square of a derived record, from the records it was derived from. Whether over came out otherwise. */
    private static boolean derive(Kinds kinds) {
        for (int cx = -1; cx <= 1; cx++) {
            for (int cy = -1; cy <= 1; cy++) {
                ring[(cx + 1) * 3 + cy + 1] = cx == 0 && cy == 0 ? kinds : recordOf(kinds.wx + cx, kinds.wy + cy);
            }
        }
        return deriveFrom(ring);
    }

    /**
     * Works out what is over each square of the chunk in the middle of ring, as Footings.Site.over
     * tells it, and with it which squares are hard, builtOn and sum, from what the squares are and
     * what stands on those of the eight chunks round it (standsOn). A square built over only on
     * the levels above it is not hard: nothing stands on its ground. Whether over came out
     * otherwise than it was.
     */
    private static boolean deriveFrom(Kinds[] ring) {
        Kinds kinds = ring[4];
        boolean changed = false;
        int builtOn = 0;
        for (int i = 0; i < CHUNK_TILES; i++) {
            for (int j = 0; j < CHUNK_TILES; j++) {
                int at = i * CHUNK_TILES + j;
                byte kind = kinds.kinds[at];
                boolean hard = kind != UNKNOWN && (kinds.house[at] != Footings.NONE || standsOn(ring, i, j));
                byte over = kind == UNKNOWN ? Footings.UNLOADED
                        : kinds.house[at] != Footings.NONE ? Footings.HOUSE
                        : hard ? Footings.BUILT
                        : kinds.level[at] != Grounds.NONE ? Footings.LEVEL
                        : kinds.above[at] != Footings.OPEN ? kinds.above[at]
                        : !Paving.without && (kind == STREET || kind == MADE) ? Footings.PAVED : Footings.OPEN;
                changed |= over != kinds.over[at];
                kinds.over[at] = over;
                kinds.hard[at] = hard;
                builtOn += hard ? 1 : 0;
            }
        }
        kinds.builtOn = kinds.whole ? builtOn : 0;
        kinds.sum = sum(kinds);
        kinds.derived = true;
        return changed;
    }

    /**
     * Whether something built stands on the ground of the square (i, j) of the chunk in the
     * middle of ring that is in no room of a building: a stair, a roof tile, which there is an
     * awning or a canopy, or a building's own wall, which the game stands on the square outside
     * the building along its south and east sides. A fence or a garden wall does not: it runs
     * with the ground it stands on.
     */
    private static boolean standsOn(Kinds[] ring, int i, int j) {
        int stands = standsAt(ring, i, j);
        if ((stands & STAIR_OR_ROOF) != 0) {
            return true;
        }
        for (int quarter = Parasols.NORTH_WEST; quarter <= Parasols.SOUTH_EAST; quarter++) {
            // A parasol's canopy is marked as a roof tile, and where the mod stands it as a parasol it stands on the ground as furniture does: see Parasols.
            if ((stands & 1 << ROOF_QUARTERS + quarter) != 0 && !(Own.furniture() && wholeParasol(ring, i - Parasols.ALONG_X[quarter], j - Parasols.ALONG_Y[quarter]))) {
                return true;
            }
        }
        return (stands & NORTH_WALL) != 0 && (standsAt(ring, i, j - 1) & ROOMED) != 0 || (stands & WEST_WALL) != 0 && (standsAt(ring, i - 1, j) & ROOMED) != 0;
    }

    /** Whether all four quarters of a round canopy stand on the squares from (i, j), the north-west one's, as Parasols.whole tells: of the chunk in the middle of ring, each from -1 to CHUNK_TILES. */
    private static boolean wholeParasol(Kinds[] ring, int i, int j) {
        for (int quarter = Parasols.NORTH_WEST; quarter <= Parasols.SOUTH_EAST; quarter++) {
            if ((standsAt(ring, i + Parasols.ALONG_X[quarter], j + Parasols.ALONG_Y[quarter]) & 1 << HELD_QUARTERS + quarter) == 0) {
                return false;
            }
        }
        return true;
    }

    /** What stands on the ground of the square (i, j) of the chunk in the middle of ring, each from -1 to CHUNK_TILES. */
    private static int standsAt(Kinds[] ring, int i, int j) {
        int cx = Math.floorDiv(i, CHUNK_TILES), cy = Math.floorDiv(j, CHUNK_TILES);
        return ring[(cx + 1) * 3 + cy + 1].stands[(i - cx * CHUNK_TILES) * CHUNK_TILES + j - cy * CHUNK_TILES];
    }

    /**
     * Fills in which made thing stands over each square of a chunk, and what it stands at:
     * the search of what a square is joined to looks over chunks round it, so it is asked
     * only of the chunks a height is worked out from (region, lay) and not of every chunk
     * looked at for water. Kept while none of the chunks its searches went by has come out
     * otherwise.
     */
    private static void footed(IsoCell cell, Kinds kinds) {
        if (kinds.footed && kinds.footReads.hold()) {
            return;
        }
        Reads reads = new Reads(logged);
        siteCell = cell;
        for (int i = 0; i < CHUNK_TILES; i++) {
            for (int j = 0; j < CHUNK_TILES; j++) {
                int at = i * CHUNK_TILES + j;
                footings.find(site, kinds.wx * CHUNK_TILES + i, kinds.wy * CHUNK_TILES + j);
                reads.add(Math.floorDiv(footings.readLowX, CHUNK_TILES), Math.floorDiv(footings.readLowY, CHUNK_TILES),
                        Math.floorDiv(footings.readHighX, CHUNK_TILES), Math.floorDiv(footings.readHighY, CHUNK_TILES));
                long middle = footings.boxed ? footings.ruler : footings.found;
                kinds.midX[at] = Footings.keyX(middle);
                kinds.midY[at] = Footings.keyY(middle);
                kinds.free[at] = footings.free;
                kinds.box[at * 4] = footings.boxed ? footings.boxLowX : Float.NaN;
                kinds.box[at * 4 + 1] = footings.boxed ? footings.boxLowY : Float.NaN;
                kinds.box[at * 4 + 2] = footings.boxed ? footings.boxHighX : Float.NaN;
                kinds.box[at * 4 + 3] = footings.boxed ? footings.boxHighY : Float.NaN;
                kinds.rulerBox[at * 4] = footings.boxed ? footings.rulerLowX : Float.NaN;
                kinds.rulerBox[at * 4 + 1] = footings.boxed ? footings.rulerLowY : Float.NaN;
                kinds.rulerBox[at * 4 + 2] = footings.boxed ? footings.rulerHighX : Float.NaN;
                kinds.rulerBox[at * 4 + 3] = footings.boxed ? footings.rulerHighY : Float.NaN;
            }
        }
        siteCell = null;
        siteKinds = null;
        long sum = 0L;
        boolean anyThing = false, anyFree = false;
        for (int at = 0; at < CHUNK_TILES * CHUNK_TILES; at++) {
            sum = mix(mix(sum, Footings.key(kinds.midX[at], kinds.midY[at])), kinds.free[at] ? 1 : 0);
            sum = mix(mix(sum, Footings.key(kinds.box[at * 4], kinds.box[at * 4 + 1])), Footings.key(kinds.box[at * 4 + 2], kinds.box[at * 4 + 3]));
            sum = mix(mix(sum, Footings.key(kinds.rulerBox[at * 4], kinds.rulerBox[at * 4 + 1])), Footings.key(kinds.rulerBox[at * 4 + 2], kinds.rulerBox[at * 4 + 3]));
            anyThing |= !Float.isNaN(kinds.midX[at]);
            anyFree |= kinds.free[at];
        }
        kinds.footSum = sum;
        kinds.anyThing = anyThing;
        kinds.anyFree = anyFree;
        kinds.footReads = reads;
        kinds.footed = true;
        tollFooted++;
    }

    /** What a chunk's squares are, as Kinds.sum keeps it. */
    private static long sum(Kinds kinds) {
        long sum = 0L;
        for (int at = 0; at < CHUNK_TILES * CHUNK_TILES; at++) {
            sum = mix(sum, (kinds.marks[at] & 0xFF) << 8 | kinds.kinds[at] << 2 | (kinds.hard[at] ? 2 : 0) | (kinds.footway[at] ? 1 : 0));
        }
        return sum;
    }

    /** A value folded into a sum: for any one sum, two values give two sums. */
    private static long mix(long sum, long value) {
        long mixed = (sum ^ value) * 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ mixed >>> 32) * 0xBF58476D1CE4E5B9L;
        return mixed ^ mixed >>> 29;
    }

    /**
     * What stands on the ground of a square, as STAIR_OR_ROOF and the rest keep it, of the
     * square alone and the room the map has on it: whether a canopy's quarter or a wall stands
     * on the ground goes by the squares round it as well (standsOn).
     */
    private static short standsOf(IsoGridSquare square, long room) {
        int stands = room != Footings.NONE ? ROOMED : 0;
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite == null) {
                continue;
            }
            int quarter = Parasols.quarter(sprite);
            if (quarter != Parasols.NONE) {
                stands |= 1 << HELD_QUARTERS + quarter;
            }
            if (isStair(sprite.getType()) || isRoof(sprite.getType()) && quarter == Parasols.NONE) {
                stands |= STAIR_OR_ROOF;
            } else if (isRoof(sprite.getType())) {
                stands |= 1 << ROOF_QUARTERS + quarter;
            }
            PropertyContainer flags = sprite.getProperties();
            if (flags.has(IsoFlagType.WallN) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.DoorWallN) || flags.has(IsoFlagType.WindowN)) {
                stands |= NORTH_WALL;
            }
            if (flags.has(IsoFlagType.WallW) || flags.has(IsoFlagType.WallNW) || flags.has(IsoFlagType.DoorWallW) || flags.has(IsoFlagType.WindowW)) {
                stands |= WEST_WALL;
            }
        }
        return (short) stands;
    }

    /**
     * What a square of the ground is to level ground (Grounds), by what lies on it and what
     * stands on it and on the level over it, where the crossbar of a goal is: the kind that
     * reaches farthest for its partners where there are several, a goal on a court's surface.
     */
    private static byte groundOf(IsoCell cell, IsoGridSquare square, int x, int y) {
        byte kind = groundOn(square);
        IsoGridSquare over = square.getChunk().getMaxLevel() < 1 ? null : cell.getGridSquare(x, y, 1);
        return over == null ? kind : (byte) Math.max(kind, groundOn(over));
    }

    private static byte groundOn(IsoGridSquare square) {
        byte kind = Grounds.NONE;
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && sprite.getName() != null) {
                kind = (byte) Math.max(kind, Grounds.of(sprite.getName()));
            }
        }
        return kind;
    }

    /**
     * What is over each square of a loaded chunk on the levels above it, into its record, as
     * Footings names it: BUILT under a floor, a roof, a wall, a rail or a stair, or under a room
     * on a square the game makes on every load of the chunk; HUNG under anything else, a sign, a
     * coping or the head of a street lamp; and OPEN under nothing. The wires the game hangs over
     * a road on squares of air, and the upper lengths of the poles that carry them, are nothing:
     * they go by the land (WorldScene.addPole, addWires).
     *
     * On every load the game makes a square above the ground where something is on it, and the
     * squares round that in the 3 x 3 in its chunk, on its level and each level under it
     * (IsoChunk.loadInWorldStreamerThread, ensureNotNull3x3). Their room is the one the map has
     * (rooms), as a load from the save gives it. Every other square above the ground the game
     * makes on some loads only, and the save keeps none (IsoChunk.Save): on a first load, those
     * the lotpack has tiles on that the game lays nothing for, and those round the lotpack's
     * (IsoCell.PlaceLot); and on the chunk's edge, those next to something in a chunk that loads
     * beside it after it (IsoChunk.loadInMainThread). A room on one of those is nothing over the
     * ground. The 3 x 3 stops at the chunk's edge, so a square on the edge whose only neighbour
     * with something on it is in the next chunk is one of them.
     */
    private static void above(IsoCell cell, Kinds into, int highest) {
        int x0 = into.wx * CHUNK_TILES, y0 = into.wy * CHUNK_TILES;
        long built = 0L, hung = 0L, laid = 0L;
        for (int z = highest; z >= 1; z--) {
            for (int at = 0; at < CHUNK_TILES * CHUNK_TILES; at++) {
                IsoGridSquare on = cell.getGridSquare(x0 + at / CHUNK_TILES, y0 + at % CHUNK_TILES, z);
                if (on == null || on.getObjects().isEmpty()) {
                    continue;
                }
                laid |= 1L << at;
                byte over = overOf(on);
                built |= over == Footings.BUILT ? 1L << at : 0L;
                hung |= over == Footings.HUNG ? 1L << at : 0L;
            }
            for (long made = withRound(laid) & ~built; made != 0L; made &= made - 1) {
                int at = Long.numberOfTrailingZeros(made);
                built |= rooms.house(cell, x0 + at / CHUNK_TILES, y0 + at % CHUNK_TILES, z) != Footings.NONE ? 1L << at : 0L;
            }
        }
        for (int at = 0; at < CHUNK_TILES * CHUNK_TILES; at++) {
            into.above[at] = (built & 1L << at) != 0L ? Footings.BUILT : (hung & 1L << at) != 0L ? Footings.HUNG : Footings.OPEN;
        }
    }

    /** What the things on a square above the ground are over the ground under it, as above names them, its room left out. */
    private static byte overOf(IsoGridSquare on) {
        if (on.getFloor() != null) {
            return Footings.BUILT;
        }
        boolean hung = false;
        PZArrayList<IsoObject> objects = on.getObjects();
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
        return hung ? Footings.HUNG : Footings.OPEN;
    }

    /** Squares of a chunk, a bit each at i * CHUNK_TILES + j as Kinds keeps them, with the squares in the 3 x 3 round each in the chunk. */
    private static long withRound(long squares) {
        long rows = squares | squares << CHUNK_TILES | squares >>> CHUNK_TILES;
        return rows | (rows << 1 & ~0x0101010101010101L) | (rows >>> 1 & ~0x8080808080808080L);
    }

    static boolean isRoof(IsoObjectType type) {
        return type == IsoObjectType.WestRoofB || type == IsoObjectType.WestRoofM || type == IsoObjectType.WestRoofT;
    }

    static boolean isStair(IsoObjectType type) {
        return type == IsoObjectType.stairsBW || type == IsoObjectType.stairsMW || type == IsoObjectType.stairsTW
                || type == IsoObjectType.stairsBN || type == IsoObjectType.stairsMN || type == IsoObjectType.stairsTN;
    }

    static byte kind(IsoGridSquare square) {
        return square == null ? UNKNOWN : kind(square, square.getFloor());
    }

    /** The straight lines painted on a square, as Lanes.mark gives them: the map hangs them on the square's floor. */
    private static byte marks(IsoGridSquare square) {
        int marks = 0;
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int k = 0; k < objects.size(); k++) {
            IsoObject object = objects.get(k);
            IsoSprite sprite = object.getSprite();
            marks |= Lanes.mark(sprite == null ? null : sprite.getName());
            ArrayList<IsoSpriteInstance> attached = object.getAttachedAnimSprite();
            if (attached != null) {
                for (int over = 0; over < attached.size(); over++) {
                    IsoSprite on = attached.get(over).parentSprite;
                    marks |= Lanes.mark(on == null ? null : on.getName());
                }
            }
        }
        return (byte) marks;
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

    /** Whether a chunk loaded whole has water on it, as its record has it. Logic thread. */
    static boolean holdsWater(int wx, int wy) {
        Kinds kinds = recordOf(wx, wy);
        return kinds != null && kinds.source == LOADED && kinds.water;
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

    /**
     * The height the far land is drawn at: far, with the hollows of open ground (knolls and
     * roll) and without the swells where they stand up, which a town levels. The built world
     * thins out into the far land pixel by pixel, each as far off as the eye's line meets it,
     * and seen from a man's height a tile of land over the built land 40 tiles off is met 15
     * tiles off. Lying over a hollow of the built land, the far land was met where it had not
     * come in, and the sky showed between the two in a band round the eye. Lying under it, it
     * has come in, and is behind the built world while that is there.
     */
    float farUnder(float x, float y, float toWater) {
        float heave = heave(x, y), far = gain == 0f ? 0f : gain * lie(x, y, toWater, heave);
        return far - gain * Math.max(heave, 0f) * smooth(toWater / VALLEY) + Math.min(knolls(x, y) + roll(x, y), 0f);
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
