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
     * Ground laid level on purpose, or something stood on it that marks it (Grounds): joined
     * only to more of its own, never to what is built, and it stands at the height the ground
     * has under its middle with nothing standing.
     */
    static final byte LEVEL = 6;
    /**
     * With what over says of a square not loaded, read from the map's files ahead of the game
     * (MapAhead): the save may have it otherwise, so a thing found with it is not settled. Except
     * a thing of level ground: what marks it is the map's own, its goals look 80 squares past
     * the far goal for another, which is past the loaded map wherever the player stands, and it
     * would never settle (findLevel).
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

        /** What a LEVEL square is to level ground, as Grounds names it. */
        default byte levelKind(int x, int y) {
            return Grounds.SURFACE;
        }
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
     * Of a thing of level ground, the box round it that Terrain lays level, from its first
     * corner to its last, in whole squares, and whether there is one: every thing of level
     * ground found whole has one. Every square of it and of the land eased round it
     * (Terrain.GROUND_EASE) is within SEEN squares of a square of the thing, so that a chunk
     * whose heights it changes always has some of the thing among the squares Terrain gathers
     * round it: Grounds.reach and WIDTH_FOR_LENGTH keep it so, and findLevel throws if not.
     */
    boolean boxed;
    int boxLowX, boxLowY, boxHighX, boxHighY;
    /**
     * Of a thing of level ground, the thing whose footing it is laid at: of the things whose
     * boxes overlap its box, and those that overlap theirs in turn, the one with the largest
     * box, and of two as large the one whose middle comes first by x and then by y. Its middle
     * as key packs it, and its box. Every thing of such a chain is told the same one: told
     * from what one chunk gathers round it, two chunks could lay one box at two footings, with
     * a step at the border between them.
     */
    long ruler;
    int rulerLowX, rulerLowY, rulerHighX, rulerHighY;
    /** How near a square of a thing of level ground every square of its box and of the land eased round it must be, either way: see boxed. */
    static final int SEEN = Terrain.AROUND - 1;
    /** Whether the search in hand is findLevel's, which what is read ahead of the map settles: see GUESSED. */
    private boolean levelSearch;
    /** Whether findRuler is looking round the chain, and whether the thing it gathers touches a square found in a thing too large to look over. */
    private boolean ruling, metLarge;

    /**
     * What each square asked about in the search in hand is, by the square as square packs
     * it: the search asks about each of a square's eight neighbours, and telling what is over
     * a square is a walk up through its levels. A place holds for the search whose number it
     * has, so the table is never cleared. It is made twice as large whenever the search in
     * hand has asked about half as many squares as it has places: looking for the ruler of a
     * thing of level ground asks about every square round each box of its chain.
     */
    private long[] asked = new long[1 << 18];
    private int[] askedIn = new int[asked.length];
    private byte[] answered = new byte[asked.length];
    private int search, places;
    private static final byte QUEUED = 8;
    /**
     * The squares found in things too large to look over. A search that meets one knows what
     * it is in without looking over MOST squares again, and a building's own square among
     * them answers with no search.
     */
    private final HashSet<Long> large = new HashSet<>();

    private static final int CHUNK = Terrain.CHUNK_TILES;

    /** The answer for a thing looked over whole: see find. Of one not all on loaded map, what the search went by, and null for one that is. */
    private record Answer(long found, boolean free, boolean settled, Ground ground, boolean boxed, int lowX, int lowY, int highX, int highY,
            long ruler, int rulerLowX, int rulerLowY, int rulerHighX, int rulerHighY) {}

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

        /**
         * The chunk at last looked up and what get gave for it, while lastHeld. The squares of
         * one chunk are asked about in a row, and a get of the key the access order has newest,
         * or of a key not in the map, leaves the order as it is: so it holds until put or clear.
         */
        private int lastX, lastY;
        private boolean lastHeld;
        private transient Answer[] last;

        Answer at(int x, int y) {
            int chunkX = Math.floorDiv(x, Terrain.CHUNK_TILES), chunkY = Math.floorDiv(y, Terrain.CHUNK_TILES);
            if (!lastHeld || chunkX != lastX || chunkY != lastY) {
                last = get(spread(square(chunkX, chunkY)));
                lastX = chunkX;
                lastY = chunkY;
                lastHeld = true;
            }
            return last == null ? null : last[placeIn(x, y)];
        }

        void put(int x, int y, Answer answer) {
            lastHeld = false;
            computeIfAbsent(chunkOf(x, y), key -> new Answer[Terrain.CHUNK_TILES * Terrain.CHUNK_TILES])[placeIn(x, y)] = answer;
        }

        @Override
        public void clear() {
            lastHeld = false;
            super.clear();
        }

        private static long chunkOf(int x, int y) {
            return spread(square(Math.floorDiv(x, Terrain.CHUNK_TILES), Math.floorDiv(y, Terrain.CHUNK_TILES)));
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

    /**
     * What a HashMap is keyed by for a place packed by key or square: Long hashes x << 32 | y as
     * x ^ y, which the squares and chunks round about share by the dozen, and a map of them is
     * searched along trees. The multiplier takes each key to a different one, so no two places
     * share a map key; a map key is never read back as a place.
     */
    static long spread(long key) {
        return key * 0x9E3779B97F4A7C15L;
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
        boxed = known != null && known.boxed;
        if (boxed) {
            boxFrom(known);
        }
    }

    private void boxFrom(Answer known) {
        boxLowX = known.lowX;
        boxLowY = known.lowY;
        boxHighX = known.highX;
        boxHighY = known.highY;
        ruler = known.ruler;
        rulerLowX = known.rulerLowX;
        rulerLowY = known.rulerLowY;
        rulerHighX = known.rulerHighX;
        rulerHighY = known.rulerHighY;
    }

    /** Finds the thing that stands over (x, y) and leaves it in the fields. */
    void find(Site site, int x, int y) {
        count = 0;
        free = false;
        settled = true;
        whole = true;
        open = false;
        boxed = false;
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
            boxed = known.boxed;
            if (boxed) {
                boxFrom(known);
            }
            return;
        }
        search++;
        places = 0;
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
        if (start == LEVEL) {
            levelSearch = true;
            try {
                findLevel(site, x, y, startAt);
            } finally {
                levelSearch = false;
            }
            return;
        }
        boolean big = large.contains(spread(square(x, y)));
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
                big |= large.contains(spread(square(toX, toY)));
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
                        big |= large.contains(spread(square(toX, toY)));
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
                grew |= large.add(spread(square(squares[member * 2], squares[member * 2 + 1])));
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

    /**
     * The search for a thing of level ground from a LEVEL square: the LEVEL squares that touch
     * it by a side or a corner, and those of its own kind that what marks a pitch, a diamond,
     * a court or an arena reaches across the ground between (Grounds.reach), never across
     * something built. It stands at the height the ground has under the middle of the box
     * round it, and Terrain lays the box level: see boxed.
     */
    private void findLevel(Site site, int x, int y, int startAt) {
        if (large.contains(spread(square(x, y)))) {
            whole = false;
            return;
        }
        answered[startAt] |= QUEUED;
        push(x, y);
        int[] box = new int[4];
        boolean widened = gatherLevel(site, 0, box);
        if (!whole) {
            keepLarge(0);
            return;
        }
        free = true;
        found = key((box[0] + box[2] + 1) / 2f, (box[1] + box[3] + 1) / 2f);
        boxAround(box, widened);
        boxLowX = box[0];
        boxLowY = box[1];
        boxHighX = box[2];
        boxHighY = box[3];
        if (!seenFromAll(0, count)) {
            throw new IllegalStateException("the box of level ground from " + boxLowX + ", " + boxLowY + " to " + boxHighX + ", " + boxHighY
                    + " reaches farther than Terrain gathers round the chunks it changes: see Grounds.reach");
        }
        boxed = true;
        findRuler(site);
        remember(site, 0, count);
    }

    /** Has large take in the squares from the one at from on, of a thing found too large to look over. */
    private void keepLarge(int from) {
        boolean grew = false;
        for (int member = from; member < count; member++) {
            grew |= large.add(spread(square(squares[member * 2], squares[member * 2 + 1])));
        }
        largeThings += grew ? 1 : 0;
    }

    /**
     * Looks over the thing of level ground whose first square is the one at from in squares,
     * pushing its squares after it, and leaves the first and last corner of the squares in box.
     * Whether it is a pitch or a court, to be widened: see boxAround.
     */
    private boolean gatherLevel(Site site, int from, int[] box) {
        int lowX = squares[from * 2], highX = lowX, lowY = squares[from * 2 + 1], highY = lowY;
        boolean widened = false;
        for (int next = from; next < count && whole; next++) {
            int atX = squares[next * 2], atY = squares[next * 2 + 1];
            lowX = Math.min(lowX, atX);
            highX = Math.max(highX, atX);
            lowY = Math.min(lowY, atY);
            highY = Math.max(highY, atY);
            for (int side = 0; side < 8 && whole; side++) {
                joinLevel(site, atX + STEP_X[side], atY + STEP_Y[side], Grounds.NONE, from);
            }
            byte kind = site.levelKind(atX, atY);
            widened |= Grounds.widened(kind);
            int reach = Grounds.reach(kind);
            if (Grounds.looksAround(kind)) {
                for (int dx = -reach; dx <= reach && whole; dx++) {
                    for (int dy = -reach; dy <= reach && whole; dy++) {
                        joinLevel(site, atX + dx, atY + dy, kind, from);
                    }
                }
                continue;
            }
            for (int side = 0; side < 4 && whole; side++) {
                if (STEP_X[side] != 0 ? !Grounds.looksAlongX(kind) : !Grounds.looksAlongY(kind)) {
                    continue;
                }
                for (int across = 1; across <= reach && whole; across++) {
                    int toX = atX + STEP_X[side] * across, toY = atY + STEP_Y[side] * across;
                    int at = place(site, toX, toY);
                    byte over = (byte) (answered[at] & ~QUEUED);
                    if (over == UNLOADED) {
                        settled = false;
                        break;
                    }
                    if (over == BUILT || over == HOUSE) {
                        break;
                    }
                    if (over == LEVEL && site.levelKind(toX, toY) == kind) {
                        joinLevel(site, toX, toY, kind, from);
                        break;
                    }
                }
            }
        }
        box[0] = lowX;
        box[1] = lowY;
        box[2] = highX;
        box[3] = highY;
        return widened;
    }

    /** Turns the first and last corner of the squares of a thing of level ground into the box Terrain lays level round it. */
    private static void boxAround(int[] box, boolean widened) {
        box[0] -= Grounds.MARGIN;
        box[1] -= Grounds.MARGIN;
        box[2] += 1 + Grounds.MARGIN;
        box[3] += 1 + Grounds.MARGIN;
        if (!widened) {
            return;
        }
        // A pitch is known by its goals and a court by its hoops, at either end of it: it is as wide as such ground is for its length.
        int sizeX = box[2] - box[0], sizeY = box[3] - box[1];
        int wantX = Math.round(sizeY * Grounds.WIDTH_FOR_LENGTH), wantY = Math.round(sizeX * Grounds.WIDTH_FOR_LENGTH);
        if (sizeX < wantX) {
            box[0] -= (wantX - sizeX) / 2;
            box[2] = box[0] + wantX;
        }
        if (sizeY < wantY) {
            box[1] -= (wantY - sizeY) / 2;
            box[3] = box[1] + wantY;
        }
    }

    /**
     * Finds the ruler of the thing of level ground in hand, whose squares are the first count
     * and whose box is boxed (see ruler), and keeps the answer of every other thing of its
     * chain, which has the same ruler. A thing whose box overlaps a box of the chain has a
     * square within SEEN of the squares they share, so each box of the chain is looked round
     * that far for the squares of things not yet looked over. A thing met before the box it
     * overlaps is kept aside until the chain reaches it. Their squares are pushed after the
     * thing's own, and dropped again at the end.
     */
    private void findRuler(Site site) {
        int own = count;
        // Of each thing met: its box, its middle, where its squares end in squares, and whether it is of the chain.
        int[] boxes = {boxLowX, boxLowY, boxHighX, boxHighY};
        long[] middles = {found};
        int[] ends = {own};
        boolean[] chained = {true};
        int things = 1;
        int[] queue = {0};
        int queued = 1;
        int[] box = new int[4];
        ruling = true;
        try {
            for (int looked = 0; looked < queued; looked++) {
                int at4 = queue[looked] * 4;
                int lowX = boxes[at4], lowY = boxes[at4 + 1], highX = boxes[at4 + 2], highY = boxes[at4 + 3];
                for (int atX = lowX - SEEN; atX < highX + SEEN; atX++) {
                    for (int atY = lowY - SEEN; atY < highY + SEEN; atY++) {
                        int at = place(site, atX, atY);
                        byte over = answered[at];
                        if (over == UNLOADED) {
                            settled = false;
                        }
                        if (over != LEVEL || large.contains(spread(square(atX, atY)))) {
                            continue;
                        }
                        int from = count;
                        answered[at] |= QUEUED;
                        push(atX, atY);
                        metLarge = false;
                        boolean widened = gatherLevel(site, from, box);
                        if (!whole || metLarge) {
                            // Some of a thing too large to look over: none of it has a box. Its squares stay queued.
                            keepLarge(from);
                            count = from;
                            whole = true;
                            continue;
                        }
                        if (things == middles.length) {
                            boxes = Arrays.copyOf(boxes, things * 8);
                            middles = Arrays.copyOf(middles, things * 2);
                            ends = Arrays.copyOf(ends, things * 2);
                            chained = Arrays.copyOf(chained, things * 2);
                            queue = Arrays.copyOf(queue, things * 2);
                        }
                        middles[things] = key((box[0] + box[2] + 1) / 2f, (box[1] + box[3] + 1) / 2f);
                        boxAround(box, widened);
                        System.arraycopy(box, 0, boxes, things * 4, 4);
                        ends[things++] = count;
                        for (boolean grew = true; grew;) {
                            grew = false;
                            for (int one = 0; one < things; one++) {
                                for (int other = 0; other < things && !chained[one]; other++) {
                                    if (chained[other] && boxes[one * 4] < boxes[other * 4 + 2] && boxes[other * 4] < boxes[one * 4 + 2]
                                            && boxes[one * 4 + 1] < boxes[other * 4 + 3] && boxes[other * 4 + 1] < boxes[one * 4 + 3]) {
                                        chained[one] = true;
                                        queue[queued++] = one;
                                        grew = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            ruling = false;
        }
        int rules = 0;
        for (int one = 1; one < things; one++) {
            if (!chained[one]) {
                continue;
            }
            long area = (long) (boxes[one * 4 + 2] - boxes[one * 4]) * (boxes[one * 4 + 3] - boxes[one * 4 + 1]);
            long rulerArea = (long) (boxes[rules * 4 + 2] - boxes[rules * 4]) * (boxes[rules * 4 + 3] - boxes[rules * 4 + 1]);
            float oneX = keyX(middles[one]), rulerX = keyX(middles[rules]);
            if (area > rulerArea || area == rulerArea && (oneX < rulerX || oneX == rulerX && keyY(middles[one]) < keyY(middles[rules]))) {
                rules = one;
            }
        }
        ruler = middles[rules];
        rulerLowX = boxes[rules * 4];
        rulerLowY = boxes[rules * 4 + 1];
        rulerHighX = boxes[rules * 4 + 2];
        rulerHighY = boxes[rules * 4 + 3];
        long ownFound = found;
        for (int one = 1; one < things; one++) {
            if (!chained[one]) {
                continue;
            }
            found = middles[one];
            boxLowX = boxes[one * 4];
            boxLowY = boxes[one * 4 + 1];
            boxHighX = boxes[one * 4 + 2];
            boxHighY = boxes[one * 4 + 3];
            if (!seenFromAll(ends[one - 1], ends[one])) {
                throw new IllegalStateException("the box of level ground from " + boxLowX + ", " + boxLowY + " to " + boxHighX + ", " + boxHighY
                        + " reaches farther than Terrain gathers round the chunks it changes: see Grounds.reach");
            }
            remember(site, ends[one - 1], ends[one]);
        }
        found = ownFound;
        boxLowX = boxes[0];
        boxLowY = boxes[1];
        boxHighX = boxes[2];
        boxHighY = boxes[3];
        count = own;
    }

    /** Joins a square to the thing of level ground whose first square is the one at from if it is LEVEL and, for a kind other than NONE, of that kind. */
    private void joinLevel(Site site, int x, int y, byte kind, int from) {
        int at = place(site, x, y);
        byte over = answered[at];
        if (over == UNLOADED) {
            settled = false;
        }
        if (over != LEVEL || kind != Grounds.NONE && site.levelKind(x, y) != kind) {
            metLarge |= ruling && over == (LEVEL | QUEUED) && large.contains(spread(square(x, y)));
            return;
        }
        if (ruling && large.contains(spread(square(x, y)))) {
            metLarge = true;
            return;
        }
        whole = count - from < MOST;
        if (whole) {
            answered[at] |= QUEUED;
            push(x, y);
        }
    }

    /**
     * Whether every square of the box in hand and of the land eased round it is within SEEN
     * squares, either way, of a square of the thing, whose squares are those from from to to in
     * squares: the squares one step at a time out from those of the thing, by sides and
     * corners, which is the distance either way.
     */
    private boolean seenFromAll(int from, int to) {
        int ease = (int) Math.ceil(Terrain.GROUND_EASE);
        int fromX = boxLowX - ease - 1, fromY = boxLowY - ease - 1, sizeX = boxHighX + ease + 1 - fromX, sizeY = boxHighY + ease + 1 - fromY;
        int[] away = new int[sizeX * sizeY];
        Arrays.fill(away, -1);
        int[] queue = new int[sizeX * sizeY];
        int queued = 0;
        for (int member = from; member < to; member++) {
            int at = (squares[member * 2] - fromX) * sizeY + squares[member * 2 + 1] - fromY;
            if (away[at] < 0) {
                away[at] = 0;
                queue[queued++] = at;
            }
        }
        int farthest = 0;
        for (int next = 0; next < queued; next++) {
            int at = queue[next], i = at / sizeY, j = at % sizeY;
            farthest = away[at];
            for (int side = 0; side < 8; side++) {
                int ti = i + STEP_X[side], tj = j + STEP_Y[side];
                if (ti >= 0 && tj >= 0 && ti < sizeX && tj < sizeY && away[ti * sizeY + tj] < 0) {
                    away[ti * sizeY + tj] = away[at] + 1;
                    queue[queued++] = ti * sizeY + tj;
                }
            }
        }
        return farthest <= SEEN;
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
        remember(site, 0, count);
    }

    /** Keeps the answer in the fields for the squares from from to to in squares. */
    private void remember(Site site, int from, int to) {
        Ground ground = null;
        if (!settled) {
            long[] chunks = Arrays.copyOf(touched, touchedCount);
            byte[] states = new byte[touchedCount];
            for (int at = 0; at < touchedCount; at++) {
                states[at] = site.state((int) (chunks[at] >> 32), (int) chunks[at]);
            }
            ground = new Ground(chunks, states, largeThings, changes);
        }
        Answer answer = new Answer(found, free, settled, ground, boxed, boxLowX, boxLowY, boxHighX, boxHighY, ruler, rulerLowX, rulerLowY, rulerHighX, rulerHighY);
        Answers into = settled ? lasting : forNow;
        for (int member = from; member < to; member++) {
            into.put(squares[member * 2], squares[member * 2 + 1], answer);
        }
    }

    private static final int[] STEP_X = {1, -1, 0, 0, 1, 1, -1, -1}, STEP_Y = {0, 0, 1, -1, 1, -1, 1, -1};

    /** Where in the table the square is, asking the site about it if this search has not yet. A place is good until the next call. */
    private int place(Site site, int x, int y) {
        long square = square(x, y);
        int at = placeOf(square);
        while (askedIn[at] == search) {
            if (asked[at] == square) {
                return at;
            }
            at = at + 1 & asked.length - 1;
        }
        if (2 * ++places > asked.length) {
            grow();
            at = placeOf(square);
            while (askedIn[at] == search) {
                at = at + 1 & asked.length - 1;
            }
        }
        asked[at] = square;
        askedIn[at] = search;
        byte over = site.over(x, y);
        if ((over & GUESSED) != 0) {
            settled &= levelSearch;
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

    private int placeOf(long square) {
        return (int) (square * 0x9E3779B97F4A7C15L >>> 64 - Integer.numberOfTrailingZeros(asked.length));
    }

    /** Makes the table twice as large, with the places of the search in hand. */
    private void grow() {
        long[] wasAsked = asked;
        int[] wasIn = askedIn;
        byte[] wasAnswered = answered;
        asked = new long[wasAsked.length * 2];
        askedIn = new int[asked.length];
        answered = new byte[asked.length];
        for (int each = 0; each < wasAsked.length; each++) {
            if (wasIn[each] != search) {
                continue;
            }
            int to = placeOf(wasAsked[each]);
            while (askedIn[to] == search) {
                to = to + 1 & asked.length - 1;
            }
            asked[to] = wasAsked[each];
            askedIn[to] = search;
            answered[to] = wasAnswered[each];
        }
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
