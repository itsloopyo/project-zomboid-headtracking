package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Locale;

/**
 * What the walls round the listener do to what is heard: how far a sound is muffled on its way
 * from where it is made, and how the space the listener is in rings.
 *
 * A sound reaches the listener the quieter of two ways. Straight through, losing what each
 * wall, shut door, shut window and floor it crosses takes. Or round, through the openings
 * between rooms and the open air (RoomSight.Openings): a doorway, an open door or window, the
 * well of a stair, a shut door or window losing what it takes, and a little more for each tile
 * the way round is longer than the straight line and for having to turn at all. That is the
 * rooms and portals way sound goes round a building, worked out from the openings the picture
 * already keeps.
 *
 * How a space rings is worked out from the listener's room: its floor area and the height of
 * a level, how hard its surfaces are by what the map calls the room, and its openings, which
 * let sound out. Out of doors it is how much of what is round the listener is building, and
 * how far off.
 *
 * Logic thread: the map is read there.
 */
final class Acoustics {
    /** What an edge between two squares does to sound: nothing, a shut window or a door with glass, a shut door, a wall. */
    static final int OPEN = 0, GLASS = 1, DOOR = 2, WALL = 3;
    /** Decibels each kind of edge takes from a sound that goes through it, by OPEN, GLASS, DOOR, WALL. */
    static final float[] THROUGH = {0f, 5f, 6f, 9f};
    /** Decibels a floor or a ceiling takes. */
    static final float FLOOR = 10f;
    /** Decibels for each tile a way round is longer than the straight line, and for turning through an opening at all. */
    static final float PER_TILE = 0.8f, BEND = 1.5f;
    /** Out of doors a building in the straight line is gone round or over: it takes no more than this. */
    static final float AROUND_A_BUILDING = 6f;
    /** No sound is muffled further: the game's own falling off with distance still applies. */
    static final float MOST = 24f;
    /** How high above its level's floor the listener's ears and a sound are taken to be, in tiles. */
    private static final float EARS = 1.4f, MADE = 0.5f;
    /** How far a way round is followed, in decibels: past this nothing is quieter than MOST anyway. */
    private static final float SEARCHED = MOST + PER_TILE * 48f;
    private static final int RAYS = 16;
    /** A room's ring: at WET_VOLUME cubic metres, WET_HARD dB for bare tile and concrete, WET_SOFT for soft furnishing. */
    private static final float WET_HARD = -5f, WET_SOFT = -13f, WET_PER_DECADE = 2f, WET_VOLUME = 15f;
    private static final float RAY_REACH = 30f;

    /** The map as sound reads it: RoomSight.Ground and what each edge does to a sound. */
    interface Walls extends RoomSight.Ground {
        /** What the north edge of a square does to sound, or with north false its west edge: OPEN, GLASS, DOOR or WALL. */
        int heard(int x, int y, int z, boolean north);
    }

    /** How muffled a sound is, in decibels lost, as the cut-off of the low pass it goes through. */
    static float cutoff(float muffled) {
        return Math.max(250f, Math.min(20000f, 20000f * (float) Math.pow(2.0, -muffled / 2.0)));
    }

    /** Which of a reverb's settings a space asks for. */
    static final class Ring {
        float decayMs, earlyMs, lateMs, hfDecayPercent, highCutHz, diffusion, density, wetDb;

        void set(Ring from) {
            decayMs = from.decayMs;
            earlyMs = from.earlyMs;
            lateMs = from.lateMs;
            hfDecayPercent = from.hfDecayPercent;
            highCutHz = from.highCutHz;
            diffusion = from.diffusion;
            density = from.density;
            wetDb = from.wetDb;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "decay %.0f ms, early %.0f, late %.0f, hf %.0f%%, cut %.0f Hz, diffusion %.0f, density %.0f, wet %.1f dB",
                    decayMs, earlyMs, lateMs, hfDecayPercent, highCutHz, diffusion, density, wetDb);
        }
    }

    /**
     * How a room rings: Sabine's decay for its volume and the sound its surfaces and openings
     * take up, a tile taken as a metre. The level of the ringing against what is heard straight
     * goes by how hard the room is (WET_HARD, WET_SOFT, between them by absorption), up
     * WET_PER_DECADE for each tenfold of volume over WET_VOLUME, and down by the share of what
     * the room takes up that goes out of its openings; kept between -22 and -3 dB. The game's
     * own sounds carry long tails of their own, and a big room's ring under -18 dB was lost
     * under them (measured: a 1745 square garage at -18 dB added about 1 dB to a shot's tail).
     *
     * @param squares    the room's floor area
     * @param absorption how much of what reaches its surfaces they take: see absorption
     * @param open       square metres of opening that let sound out of it
     */
    static void room(int squares, float absorption, float open, Ring into) {
        float height = ViewCamera.LEVEL_HEIGHT;
        float volume = squares * height, side = (float) Math.sqrt(squares);
        float surface = 2f * squares + 4f * side * height;
        float taken = surface * absorption + open;
        float seconds = Math.max(0.15f, Math.min(4f, 0.161f * volume / taken));
        float between = 4f * volume / surface;
        into.decayMs = seconds * 1000f;
        into.earlyMs = Math.max(1f, Math.min(300f, between / 343f * 1000f));
        into.lateMs = Math.max(1f, Math.min(100f, into.earlyMs * 2f + 4f));
        into.hfDecayPercent = Math.max(30f, Math.min(90f, 100f - absorption * 250f));
        into.highCutHz = Math.max(3000f, Math.min(12000f, 12000f - absorption * 30000f));
        into.diffusion = 100f;
        into.density = 100f;
        float hardness = Math.max(0f, Math.min(1f, (absorption - 0.05f) / 0.2f));
        float kept = surface * absorption / taken;
        into.wetDb = Math.max(-22f, Math.min(-3f, WET_HARD + (WET_SOFT - WET_HARD) * hardness
                + WET_PER_DECADE * (float) Math.log10(volume / WET_VOLUME) + 10f * (float) Math.log10(kept)));
    }

    /**
     * How the open air rings: off where nothing stands round the listener, and with more of
     * what is round them building, longer, louder and coming back from as far as it stands.
     *
     * @param enclosed the share of directions round the listener with a building within reach
     * @param distance how far off those buildings stand on average, in tiles
     */
    static void outdoors(float enclosed, float distance, Ring into) {
        into.decayMs = 300f + 1200f * enclosed;
        into.earlyMs = Math.max(5f, Math.min(300f, 2f * distance / 343f * 1000f));
        into.lateMs = Math.max(5f, Math.min(100f, into.earlyMs));
        into.hfDecayPercent = 50f;
        into.highCutHz = 6000f;
        into.diffusion = 40f + 40f * enclosed;
        into.density = 40f + 40f * enclosed;
        into.wetDb = enclosed <= 0f ? -80f : -34f + 22f * enclosed;
    }

    /**
     * How much of what reaches a room's surfaces they take, by what the map calls the room:
     * tile, metal and bare concrete little, a bedroom or a sitting room of soft things much,
     * anything else between.
     */
    static float absorption(String room) {
        String name = room == null ? "" : room.toLowerCase(Locale.ROOT);
        for (String hard : HARD) {
            if (name.contains(hard)) {
                return 0.05f;
            }
        }
        for (String soft : SOFT) {
            if (name.contains(soft)) {
                return 0.25f;
            }
        }
        return 0.12f;
    }

    private static final String[] HARD = {"bathroom", "toilet", "shower", "kitchen", "laundry", "garage", "warehouse", "storage", "factory",
            "shed", "basement", "cellar", "mechanic", "gym", "pool", "church", "jail", "cell", "freezer"};
    private static final String[] SOFT = {"bedroom", "living", "motel", "hotel", "office", "library", "theatre", "cinema", "clothes", "bookstore"};

    /** What is round one listener: the ways round to the regions near them, and how their space rings. */
    static final class Surroundings {
        private Walls walls;
        private RoomSight.Openings openings;
        private float listenerX, listenerY, listenerUp;
        private int listenerZ;
        long region;
        /** Where each opening is, x, y and up, and what it does to a sound going through it. */
        private float[] at = new float[3 * 256];
        private float[] through = new float[256];
        /** Each side of each opening, 2 * opening + side (0 here, 1 there), in order of the region on that side. */
        private int[] sides = new int[512];
        private long[] sideRegion = new long[512];
        /** The least decibels to arrive in the region on a side of an opening, through it: 2 * opening + side. */
        private float[] best = new float[512];
        private int[] heap = new int[1024];
        private float[] heapKey = new float[1024];
        /** Dev only: how many ways through openings the last search followed. */
        int followed;
        /** The room the listener is in out of doors, or how enclosed the open air round them is: see listen. */
        float enclosed, enclosedDistance;
        int roomOpenings;

        /** Works out the ways round from where the listener is, through the openings given. */
        void listen(Walls walls, RoomSight.Openings openings, float x, float y, int z) {
            this.walls = walls;
            this.openings = openings;
            listenerX = x;
            listenerY = y;
            listenerZ = z;
            listenerUp = z * ViewCamera.LEVEL_HEIGHT + EARS;
            region = walls.region((int) Math.floor(x), (int) Math.floor(y), z);
            int count = openings.count;
            if (through.length < count) {
                int grown = Math.max(count, through.length * 2);
                at = new float[3 * grown];
                through = new float[grown];
                sides = new int[2 * grown];
                sideRegion = new long[2 * grown];
                best = new float[2 * grown];
                heap = new int[4 * grown];
                heapKey = new float[4 * grown];
            }
            roomOpenings = 0;
            for (int i = 0; i < count; i++) {
                int kind = openings.kind[i], ox = openings.x[i], oy = openings.y[i], oz = openings.z[i];
                at[3 * i] = kind == RoomSight.WEST ? ox : ox + 0.5f;
                at[3 * i + 1] = kind == RoomSight.NORTH ? oy : oy + 0.5f;
                at[3 * i + 2] = kind == RoomSight.UP ? (oz + 1) * ViewCamera.LEVEL_HEIGHT : oz * ViewCamera.LEVEL_HEIGHT + EARS;
                through[i] = kind == RoomSight.UP ? 0f : THROUGH[walls.heard(ox, oy, oz, kind == RoomSight.NORTH)];
                sides[2 * i] = 2 * i;
                sides[2 * i + 1] = 2 * i + 1;
                sideRegion[2 * i] = openings.here[i];
                sideRegion[2 * i + 1] = openings.there[i];
                if ((openings.here[i] == region || openings.there[i] == region) && region != RoomSight.OUTSIDE && through[i] == 0f) {
                    roomOpenings++;
                }
            }
            sortSides(0, 2 * count - 1);
            search(count);
            if (region == RoomSight.OUTSIDE) {
                lookRound();
            }
        }

        /** Orders sides by the region on them, so a region's sides are one run of the list. */
        private void sortSides(int from, int to) {
            while (from < to) {
                long pivot = sideRegion[sides[(from + to) >>> 1]];
                int i = from, j = to;
                while (i <= j) {
                    while (sideRegion[sides[i]] < pivot) {
                        i++;
                    }
                    while (sideRegion[sides[j]] > pivot) {
                        j--;
                    }
                    if (i <= j) {
                        int kept = sides[i];
                        sides[i++] = sides[j];
                        sides[j--] = kept;
                    }
                }
                if (j - from < to - i) {
                    sortSides(from, j);
                    from = i;
                } else {
                    sortSides(i, to);
                    to = j;
                }
            }
        }

        /** The first place in sides with this region on it, or the length of the run if there is none, which then is empty. */
        private int firstSide(long of, int sideCount) {
            int low = 0, high = sideCount;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (sideRegion[sides[middle]] < of) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            return low;
        }

        /**
         * Dijkstra over the openings: from the listener to every opening of their region, through
         * it into the region beyond, and on through that region's other openings, each step
         * costing PER_TILE a tile of its length and what the opening it goes through takes.
         */
        private void search(int count) {
            int sideCount = 2 * count;
            Arrays.fill(best, 0, sideCount, Float.POSITIVE_INFINITY);
            int size = 0;
            followed = 0;
            for (int k = firstSide(region, sideCount); k < sideCount && sideRegion[sides[k]] == region; k++) {
                int from = sides[k], opening = from >> 1;
                float cost = PER_TILE * distance(listenerX, listenerY, listenerUp, opening) + through[opening];
                size = relax(from ^ 1, cost, size);
            }
            while (size > 0) {
                int arrived = heap[0];
                float cost = heapKey[0];
                size = pop(size);
                if (cost > best[arrived] || cost > SEARCHED) {
                    continue;
                }
                long in = sideRegion[arrived];
                int opening = arrived >> 1;
                for (int k = firstSide(in, sideCount); k < sideCount && sideRegion[sides[k]] == in; k++) {
                    int from = sides[k], next = from >> 1;
                    if (next == opening) {
                        continue;
                    }
                    followed++;
                    float step = PER_TILE * distance(at[3 * opening], at[3 * opening + 1], at[3 * opening + 2], next) + through[next];
                    size = relax(from ^ 1, cost + step, size);
                }
            }
        }

        private int relax(int side, float cost, int size) {
            if (cost >= best[side]) {
                return size;
            }
            best[side] = cost;
            if (size == heap.length) {
                heap = Arrays.copyOf(heap, size * 2);
                heapKey = Arrays.copyOf(heapKey, size * 2);
            }
            int i = size;
            while (i > 0) {
                int parent = (i - 1) >> 1;
                if (heapKey[parent] <= cost) {
                    break;
                }
                heap[i] = heap[parent];
                heapKey[i] = heapKey[parent];
                i = parent;
            }
            heap[i] = side;
            heapKey[i] = cost;
            return size + 1;
        }

        private int pop(int size) {
            size--;
            int last = heap[size];
            float key = heapKey[size];
            int i = 0;
            while (true) {
                int child = 2 * i + 1;
                if (child >= size) {
                    break;
                }
                if (child + 1 < size && heapKey[child + 1] < heapKey[child]) {
                    child++;
                }
                if (heapKey[child] >= key) {
                    break;
                }
                heap[i] = heap[child];
                heapKey[i] = heapKey[child];
                i = child;
            }
            heap[i] = last;
            heapKey[i] = key;
            return size;
        }

        private float distance(float x, float y, float up, int opening) {
            float dx = at[3 * opening] - x, dy = at[3 * opening + 1] - y, du = at[3 * opening + 2] - up;
            return (float) Math.sqrt(dx * dx + dy * dy + du * du);
        }

        /**
         * How many decibels a sound made at a point loses on its way to the listener, by the
         * quieter of straight through and the way round.
         *
         * @param z the level the sound is on, with what is above its floor as a fraction
         */
        float muffled(float x, float y, float z) {
            int level = (int) Math.floor(z);
            long there = walls.region((int) Math.floor(x), (int) Math.floor(y), level);
            if (there == region && region == RoomSight.OUTSIDE) {
                // The open air has no floor between its levels: a balcony is heard from the street through nothing.
                return Math.min(straight(x, y, listenerZ), AROUND_A_BUILDING);
            }
            if (there == region && level == listenerZ) {
                return 0f;
            }
            float up = z * ViewCamera.LEVEL_HEIGHT + MADE;
            float round = Float.POSITIVE_INFINITY;
            int sideCount = 2 * openings.count;
            for (int k = firstSide(there, sideCount); k < sideCount && sideRegion[sides[k]] == there; k++) {
                int side = sides[k];
                if (best[side] < round) {
                    round = Math.min(round, best[side] + PER_TILE * distance(x, y, up, side >> 1));
                }
            }
            float dx = x - listenerX, dy = y - listenerY, du = up - listenerUp;
            round += BEND - PER_TILE * (float) Math.sqrt(dx * dx + dy * dy + du * du);
            return Math.min(MOST, Math.min(round, straight(x, y, level)));
        }

        /** What the walls on the straight line from the listener take, on the listener's level, and the floors between the levels. */
        private float straight(float x, float y, int level) {
            float taken = FLOOR * Math.abs(level - listenerZ);
            int cellX = (int) Math.floor(listenerX), cellY = (int) Math.floor(listenerY);
            int endX = (int) Math.floor(x), endY = (int) Math.floor(y);
            float dx = x - listenerX, dy = y - listenerY;
            int stepX = dx > 0f ? 1 : -1, stepY = dy > 0f ? 1 : -1;
            float nextX = dx == 0f ? Float.POSITIVE_INFINITY : ((dx > 0f ? cellX + 1 : cellX) - listenerX) / dx;
            float nextY = dy == 0f ? Float.POSITIVE_INFINITY : ((dy > 0f ? cellY + 1 : cellY) - listenerY) / dy;
            float perX = dx == 0f ? Float.POSITIVE_INFINITY : Math.abs(1f / dx), perY = dy == 0f ? Float.POSITIVE_INFINITY : Math.abs(1f / dy);
            // One step for each square edge between the two, so a tie at a corner cannot carry the walk past the end.
            for (int steps = Math.abs(endX - cellX) + Math.abs(endY - cellY); steps > 0 && taken < MOST; steps--) {
                if (cellY == endY || cellX != endX && nextX < nextY) {
                    // Leaving through the west edge of this square, or the west edge of the next.
                    taken += THROUGH[walls.heard(stepX > 0 ? cellX + 1 : cellX, cellY, listenerZ, false)];
                    cellX += stepX;
                    nextX += perX;
                } else {
                    taken += THROUGH[walls.heard(cellX, stepY > 0 ? cellY + 1 : cellY, listenerZ, true)];
                    cellY += stepY;
                    nextY += perY;
                }
            }
            return taken;
        }

        /** Out of doors: how much of the way round the listener a building stands within RAY_REACH, and how far off. */
        private void lookRound() {
            int hits = 0;
            float far = 0f;
            for (int i = 0; i < RAYS; i++) {
                double angle = 2.0 * Math.PI * i / RAYS;
                float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
                for (float d = 1f; d <= RAY_REACH; d += 1f) {
                    int x = (int) Math.floor(listenerX + cos * d), y = (int) Math.floor(listenerY + sin * d);
                    if (walls.building(x, y, listenerZ) != RoomSight.OUTSIDE || listenerZ > 0 && walls.building(x, y, 0) != RoomSight.OUTSIDE) {
                        hits++;
                        far += d;
                        break;
                    }
                }
            }
            enclosed = hits / (float) RAYS;
            enclosedDistance = hits == 0 ? RAY_REACH : far / hits;
        }
    }
}
