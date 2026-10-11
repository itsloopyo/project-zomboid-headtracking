package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which made thing stands over each square of the map, so that Terrain can give the whole of
 * it one level footing. The land rolls, a road rolls with it and what grows stands on it.
 * What is built does not: a building, the row it shares walls with, the stair up its side,
 * the roof that overhangs its walls, the awning and the balcony and the canopy on posts are
 * each one body, plumb and level, and the ground is cut level under all of it.
 *
 * A square is under something built when a building's room is on it, a stair stands on it,
 * or a floor, a roof, a wall or a stair is over it on a level above. Built squares that
 * touch, by a side or by a corner, are one thing, and so is what hangs over a square that
 * touches them: a sign, a coping, a lamp on a bracket. So are built squares with no more
 * than a passage of made ground between them, PASSAGE squares across: the shops of a row
 * stand on one level, and the paving along their fronts and in the passages between them
 * with them, where shops each at a height of its own were so many steps in the pavement.
 *
 * One with a building in it stands at that building's footing, and where several buildings
 * are joined, at the footing of the one nearest the middle of them all, the same whichever
 * square asks. One with no building in it, a canopy or a flight to nowhere, stands at the
 * height the ground has under the middle of it with nothing standing there. And one with
 * made ground against its walls, building or none, stands at the height that ground has
 * with nothing standing, in the middle of all of it that meets the walls: a shop is as high
 * as the pavement at its door, and the shop across the street as high as the same street,
 * where each at the height of the land under its own middle stood a step above its pavement
 * or below it. What only hangs, with nothing built that it touches, is the head of
 * something on a pole, a street lamp or a flag, and goes by the land as its pole does.
 *
 * Logic thread.
 */
final class Footings {
    /**
     * What is over a square: nothing, something built that is no room of a building, a
     * building, a square the map has not loaded, or something on a level above that is no
     * floor, roof, wall or stair.
     */
    static final byte OPEN = 0, BUILT = 1, HOUSE = 2, UNLOADED = 3, HUNG = 4;
    /** Nothing over it, and made ground: a street, a pavement, a yard. */
    static final byte PAVED = 5;
    /**
     * With what over says of a square not loaded, read from the map's files ahead of the game
     * (MapAhead): the save may have it otherwise, so a thing found with it is not settled.
     */
    static final byte GUESSED = 16;
    /** What Site.state says of a chunk. */
    static final byte NOT_LOADED = 0, READ_AHEAD = 1, LOADED = 2;
    /** The widest passage of made ground that what is built either side of it is one thing across, counting what hangs out over it from either side. */
    static final int PASSAGE = 4;

    /**
     * The map, as far as a footing goes by it. What over says of a square goes by the map at
     * that square and the squares round it alone, and so is the same for as long as each
     * chunk they are in is as loaded as it was and nothing is built or taken down there.
     */
    interface Site {
        byte over(int x, int y);

        /**
         * Whether the chunk (wx, wy) is loaded: what over says of its squares changes with
         * this and with nothing else of the map's. By what over says of its middle square,
         * for a map whose chunks are loaded whole.
         */
        default boolean loaded(int wx, int wy) {
            return over(wx * CHUNK + CHUNK / 2, wy * CHUNK + CHUNK / 2) != UNLOADED;
        }

        /**
         * LOADED, READ_AHEAD for a chunk not loaded that over says what the map's files have of,
         * or NOT_LOADED: what over says of its squares changes with this.
         */
        default byte state(int wx, int wy) {
            return loaded(wx, wy) ? LOADED : NOT_LOADED;
        }

        /** The middle of the building over a HOUSE square, as key packs it. */
        long house(int x, int y);

        /** Whether the ground of a HUNG square is made ground: over tells that of a square only when nothing is over it. */
        boolean paved(int x, int y);
    }

    /**
     * No more squares than this are one thing. Something larger, a mall of several buildings
     * or a viaduct, is not looked over whole: each building in it keeps its own footing, what
     * is joined to a building takes the nearest one's, and what has no building follows the
     * land as a road does.
     */
    static final int MOST = 8192;

    /** The sheet of the game's poles and overhead wires, which are hung on squares of air and are no part of anything built there. */
    static final String OVERHEAD_LINES = "appliances_com";

    /** No footing: the land's own height. */
    static final long NONE = key(Float.NaN, Float.NaN);

    /** The thing found over the square last asked about: its middle as key packs it, or NONE. */
    long found;
    /** Whether it stands at the height the ground has where found is with nothing built standing, and not at a building's footing: it has no building in it, or it has made ground against it. */
    boolean free;
    /** Whether all of it was on loaded map: one that was not may be another thing when more is loaded. */
    boolean settled;
    /**
     * Whether all of it was looked over: then every square of it has the same answer, and the
     * first count pairs of squares are those squares, or none where the answer was kept from
     * the search that found it.
     */
    boolean whole;
    /**
     * Whether the square asked about was open ground or paving with nothing over it: then
     * there is nothing to stand at, and that holds for as long as over says the same of it.
     */
    boolean open;
    int count;
    int[] squares = new int[512];

    /**
     * What each square asked about in the search in hand is, by the square as square packs
     * it: the search asks about each of a square's eight neighbours, and telling what is over
     * a square is a walk up through its levels. A place holds for the search whose number it
     * has, so the table is never cleared. A thing of MOST squares has eight times as many
     * round it at the very most and sixteen times as many along the passages out from it,
     * 4 * (PASSAGE + 1) less the four already counted from each, and the table is never full.
     */
    private static final int PLACES = 1 << 18;
    private final long[] asked = new long[PLACES];
    private final int[] askedIn = new int[PLACES];
    private final byte[] answered = new byte[PLACES];
    private int search;
    private static final byte QUEUED = 8;
    /**
     * The squares found in things too large to look over. A search that meets one knows what
     * it is in without looking over MOST squares again, and a building's own square among
     * them answers with no search.
     */
    private final HashSet<Long> large = new HashSet<>();

    private static final int CHUNK = Terrain.CHUNK_TILES;

    /** The answer for a thing looked over whole: see find. Of one not all on loaded map, what the search went by, and null for one that is. */
    private record Answer(long found, boolean free, boolean settled, Ground ground) {}

    /**
     * The chunks a search that met ground the map had not loaded went by, each chunk with a
     * square it asked about or a square round one, and which of them were loaded or read ahead
     * (Site.state). While each is as it was, a search of the same thing asks the same of every
     * square and comes out the same, and the answer holds, unless a thing found too large to look over
     * since has taken in some of its squares: a search that meets one of those stops short.
     * What is built or taken down on loaded ground is not seen until the thing is looked over
     * again, as with a settled thing.
     */
    private static final class Ground {
        final long[] chunks;
        /** As Site.state said of each chunk. */
        final byte[] states;
        /** How many things had been found too large when it was found. */
        final int largeThings;
        /** The last changed it was found to hold in, and whether it has been found not to. */
        int heldIn;
        boolean gone;

        Ground(long[] chunks, byte[] states, int largeThings, int heldIn) {
            this.chunks = chunks;
            this.states = states;
            this.largeThings = largeThings;
            this.heldIn = heldIn;
        }
    }

    /** How many times changed has been called, and how many times large has taken in squares it did not have: see Ground. */
    private int changes, largeThings;
    /** The chunks the search in hand has gone by, by the chunk as square packs it, in the same way as asked, in a table twice as large as it needs to be or more. */
    private long[] chunkAsked = new long[1 << 12];
    private int[] chunkAskedIn = new int[chunkAsked.length];
    private long[] touched = new long[64];
    private int touchedCount;

    /**
     * The answers for the squares of things looked over whole, by chunk and square: one search
     * of a thing answers for every square of it. Those of a settled thing hold until a map is
     * loaded anew, the others only while the map has the chunks loaded that it had when they
     * were found: see changed. Of chunks not asked about lately the answers are let go, past
     * KEPT_CHUNKS of them, and found again if they are asked about.
     */
    private final Answers lasting = new Answers(), forNow = new Answers();
    /** About twice the chunks a view of the farthest the game loads looks over. */
    static final int KEPT_CHUNKS = 2048;

    private static final class Answers extends LinkedHashMap<Long, Answer[]> {
        private static final long serialVersionUID = 1L;

        Answers() {
            super(64, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Answer[]> eldest) {
            return size() > KEPT_CHUNKS;
        }

        Answer at(int x, int y) {
            Answer[] chunk = get(chunkOf(x, y));
            return chunk == null ? null : chunk[placeIn(x, y)];
        }

        void put(int x, int y, Answer answer) {
            computeIfAbsent(chunkOf(x, y), key -> new Answer[Terrain.CHUNK_TILES * Terrain.CHUNK_TILES])[placeIn(x, y)] = answer;
        }

        private static long chunkOf(int x, int y) {
            return square(Math.floorDiv(x, Terrain.CHUNK_TILES), Math.floorDiv(y, Terrain.CHUNK_TILES));
        }

        private static int placeIn(int x, int y) {
            return Math.floorMod(x, Terrain.CHUNK_TILES) * Terrain.CHUNK_TILES + Math.floorMod(y, Terrain.CHUNK_TILES);
        }
    }

    /** For a map loaded anew. */
    void forget() {
        large.clear();
        lasting.clear();
        forNow.clear();
    }

    /**
     * For a map with other chunks loaded: a thing that was not all on loaded map may be another
     * thing now, and is looked over again when it is next asked about if a chunk it went by is
     * loaded or let go since.
     */
    void changed() {
        changes++;
    }

    private static long square(int x, int y) {
        return (long) x << 32 | y & 0xFFFFFFFFL;
    }

    static long key(float x, float y) {
        return (long) Float.floatToRawIntBits(x) << 32 | Float.floatToRawIntBits(y) & 0xFFFFFFFFL;
    }

    static float keyX(long key) {
        return Float.intBitsToFloat((int) (key >> 32));
    }

    static float keyY(long key) {
        return Float.intBitsToFloat((int) key);
    }

    /**
     * What find leaves in the fields for a square the map has not loaded, without asking the
     * site about it: the answer for a settled thing that was found while the square was
     * loaded, and otherwise none, not settled. A thing not all on loaded map does not hold
     * once a chunk it went by is let go, and the square's own is one of those.
     */
    void findUnloaded(int x, int y) {
        count = 0;
        whole = true;
        open = false;
        Answer known = lasting.at(x, y);
        found = known == null ? NONE : known.found;
        free = known != null && known.free;
        settled = known != null;
    }

    /** Finds the thing that stands over (x, y) and leaves it in the fields. */
    void find(Site site, int x, int y) {
        count = 0;
        free = false;
        settled = true;
        whole = true;
        open = false;
        found = NONE;
        Answer known = lasting.at(x, y);
        if (known == null) {
            known = forNow.at(x, y);
            if (known != null && !holds(site, known.ground)) {
                known = null;
            }
        }
        if (known != null) {
            found = known.found;
            free = known.free;
            settled = known.settled;
            return;
        }
        search++;
        touchedCount = 0;
        int startAt = place(site, x, y);
        byte start = answered[startAt];
        if (start == UNLOADED) {
            settled = false;
            return;
        }
        if (start == OPEN || start == PAVED) {
            open = true;
            return;
        }
        boolean big = large.contains(square(x, y));
        if (big && start == HOUSE) {
            whole = false;
            found = site.house(x, y);
            return;
        }
        answered[startAt] |= QUEUED;
        push(x, y);
        long nearest = NONE;
        boolean structured = false;
        int lowX = x, highX = x, lowY = y, highY = y;
        long pavedX = 0, pavedY = 0;
        int paved = 0;
        // In a thing known to be too large, only as far as the nearest building.
        for (int next = 0; next < count && whole && !(big && nearest != NONE); next++) {
            int atX = squares[next * 2], atY = squares[next * 2 + 1];
            int here = answered[place(site, atX, atY)] & ~QUEUED;
            if (here == HOUSE && nearest == NONE) {
                nearest = site.house(atX, atY);
            }
            structured |= here != HUNG;
            lowX = Math.min(lowX, atX);
            highX = Math.max(highX, atX);
            lowY = Math.min(lowY, atY);
            highY = Math.max(highY, atY);
            for (int side = 0; side < 8 && whole; side++) {
                int toX = atX + STEP_X[side], toY = atY + STEP_Y[side];
                int at = place(site, toX, toY);
                byte over = answered[at];
                if (over == UNLOADED) {
                    settled = false;
                }
                if (over != BUILT && over != HOUSE && over != HUNG) {
                    continue;
                }
                big |= large.contains(square(toX, toY));
                whole = count < MOST;
                if (whole) {
                    answered[at] |= QUEUED;
                    push(toX, toY);
                }
            }
            if (here == HUNG) {
                // A sign or a coping out over a pavement: the pavement under it is against the wall it hangs from.
                if (site.paved(atX, atY)) {
                    pavedX += 2 * atX + 1;
                    pavedY += 2 * atY + 1;
                    paved++;
                }
                continue;
            }
            for (int side = 0; side < 4 && whole; side++) {
                for (int across = 1; across <= PASSAGE + 1 && whole; across++) {
                    int toX = atX + STEP_X[side] * across, toY = atY + STEP_Y[side] * across;
                    int at = place(site, toX, toY);
                    byte over = answered[at];
                    if (over == UNLOADED) {
                        settled = false;
                    }
                    if (over == PAVED) {
                        if (across == 1) {
                            // Twice the middle of the square, to keep to whole numbers: the sum is then the same in whatever order the squares are met.
                            pavedX += 2 * toX + 1;
                            pavedY += 2 * toY + 1;
                            paved++;
                        }
                        continue;
                    }
                    if ((over & ~QUEUED) == HUNG) {
                        continue;
                    }
                    if (across > 1 && (over == BUILT || over == HOUSE)) {
                        big |= large.contains(square(toX, toY));
                        whole = count < MOST;
                        if (whole) {
                            answered[at] |= QUEUED;
                            push(toX, toY);
                        }
                    }
                    break;
                }
            }
        }
        if (big || !whole) {
            whole = false;
            boolean grew = false;
            for (int member = 0; member < count; member++) {
                grew |= large.add(square(squares[member * 2], squares[member * 2 + 1]));
            }
            largeThings += grew ? 1 : 0;
            found = start == HOUSE ? site.house(x, y) : nearest;
            return;
        }
        if (structured && paved > 0) {
            free = true;
            found = key(pavedX / (2f * paved), pavedY / (2f * paved));
            remember(site);
            return;
        }
        if (nearest == NONE) {
            free = structured;
            found = structured ? key((lowX + highX + 1) / 2f, (lowY + highY + 1) / 2f) : NONE;
            remember(site);
            return;
        }
        // Of buildings joined, the one nearest the middle of them all: a row down a hill stands at the height of its middle and not of one end.
        float midX = (lowX + highX + 1) / 2f, midY = (lowY + highY + 1) / 2f, least = Float.POSITIVE_INFINITY;
        for (int member = 0; member < count; member++) {
            int atX = squares[member * 2], atY = squares[member * 2 + 1];
            if ((answered[place(site, atX, atY)] & ~QUEUED) != HOUSE) {
                continue;
            }
            long house = site.house(atX, atY);
            float awayX = keyX(house) - midX, awayY = keyY(house) - midY, away = awayX * awayX + awayY * awayY;
            if (away < least || away == least && house < found) {
                least = away;
                found = house;
            }
        }
        remember(site);
    }

    /** Whether what a search went by is as it was, so that its answer holds. */
    private boolean holds(Site site, Ground ground) {
        if (ground.gone || ground.largeThings != largeThings) {
            ground.gone = true;
            return false;
        }
        if (ground.heldIn == changes) {
            return true;
        }
        for (int at = 0; at < ground.chunks.length; at++) {
            if (site.state((int) (ground.chunks[at] >> 32), (int) ground.chunks[at]) != ground.states[at]) {
                ground.gone = true;
                return false;
            }
        }
        ground.heldIn = changes;
        return true;
    }

    /** Keeps the answer of a search that looked its thing over whole for every square of the thing. */
    private void remember(Site site) {
        Ground ground = null;
        if (!settled) {
            long[] chunks = Arrays.copyOf(touched, touchedCount);
            byte[] states = new byte[touchedCount];
            for (int at = 0; at < touchedCount; at++) {
                states[at] = site.state((int) (chunks[at] >> 32), (int) chunks[at]);
            }
            ground = new Ground(chunks, states, largeThings, changes);
        }
        Answer answer = new Answer(found, free, settled, ground);
        Answers into = settled ? lasting : forNow;
        for (int member = 0; member < count; member++) {
            into.put(squares[member * 2], squares[member * 2 + 1], answer);
        }
    }

    private static final int[] STEP_X = {1, -1, 0, 0, 1, 1, -1, -1}, STEP_Y = {0, 0, 1, -1, 1, -1, 1, -1};

    /** Where in the table the square is, asking the site about it if this search has not yet. */
    private int place(Site site, int x, int y) {
        long square = square(x, y);
        int at = (int) (square * 0x9E3779B97F4A7C15L >>> 64 - Integer.numberOfTrailingZeros(PLACES));
        while (askedIn[at] == search) {
            if (asked[at] == square) {
                return at;
            }
            at = at + 1 & PLACES - 1;
        }
        asked[at] = square;
        askedIn[at] = search;
        byte over = site.over(x, y);
        if ((over & GUESSED) != 0) {
            settled = false;
            over &= ~GUESSED;
        }
        answered[at] = over;
        // What over said of the square went by the squares round it as well.
        for (int chunkX = Math.floorDiv(x - 1, CHUNK); chunkX <= Math.floorDiv(x + 1, CHUNK); chunkX++) {
            for (int chunkY = Math.floorDiv(y - 1, CHUNK); chunkY <= Math.floorDiv(y + 1, CHUNK); chunkY++) {
                touch(square(chunkX, chunkY));
            }
        }
        return at;
    }

    /** Notes that the search in hand went by a chunk. */
    private void touch(long chunk) {
        int at = chunkPlace(chunk);
        while (chunkAskedIn[at] == search) {
            if (chunkAsked[at] == chunk) {
                return;
            }
            at = at + 1 & chunkAsked.length - 1;
        }
        chunkAsked[at] = chunk;
        chunkAskedIn[at] = search;
        if (touchedCount == touched.length) {
            touched = Arrays.copyOf(touched, touchedCount * 2);
        }
        touched[touchedCount++] = chunk;
        if (2 * touchedCount > chunkAsked.length) {
            chunkAsked = new long[chunkAsked.length * 2];
            chunkAskedIn = new int[chunkAsked.length];
            for (int each = 0; each < touchedCount; each++) {
                int to = chunkPlace(touched[each]);
                while (chunkAskedIn[to] == search) {
                    to = to + 1 & chunkAsked.length - 1;
                }
                chunkAsked[to] = touched[each];
                chunkAskedIn[to] = search;
            }
        }
    }

    private int chunkPlace(long chunk) {
        return (int) (chunk * 0x9E3779B97F4A7C15L >>> 64 - Integer.numberOfTrailingZeros(chunkAsked.length));
    }

    private void push(int x, int y) {
        if (count * 2 == squares.length) {
            squares = Arrays.copyOf(squares, squares.length * 2);
        }
        squares[count * 2] = x;
        squares[count * 2 + 1] = y;
        count++;
    }
}
