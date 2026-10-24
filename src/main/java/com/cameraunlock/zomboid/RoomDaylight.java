package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.RoomDef;

/**
 * The daylight that comes into a room through its windows, glazed doors and doorways, as a
 * share of the open ground's light for each square of the room, before the hour: Frame.daylit
 * takes it by how much of the day there is (Evening.day), so a room has none of it at night.
 *
 * The game's light for a square in a room is what its lamps give and little or nothing of the
 * day: at noon a warehouse with its lamps off is 0 on every square, under a row of lit windows.
 * The picture draws the sun that falls through a window, and nothing else of the daylight that
 * comes in with it, which in a real room lights it from the floor and the walls the sun is on.
 * So a room square is lit at least by this share of the open ground's light: most of it beside
 * an opening, less the further in, by how many openings are near.
 *
 * An opening is an edge of the room the day passes (Edges.through): a doorway, an open door, a
 * window, by as much as its glass, curtain and boards let through. One to the open air lets the
 * day in. One to another room passes on what that room has from the open air beside the
 * opening, so a hall with no window is lit from the room whose door stands open into it. A room
 * under a room with no floor between, a warehouse whose windows are high in a wall two levels
 * tall, has the openings of that room's level as well, round the room's own walls. A room with
 * none keeps the game's light alone, and is dark.
 *
 * Shares are worked out on the logic thread, from the map as RoomSight reads it: again at once
 * when the game marks a chunk the room is on (a door, a window, a curtain, a barricade), and
 * every REFRESH_NANOS otherwise, each room at its own moment. Where a room's shares change, its
 * chunks are told to build again. Each is written whole before it is put in the table, the
 * render thread reads what is there (sharedAt) and works out nothing, and a room is let go only
 * when the loaded map no longer holds it, so the builds and the people in it go by the same
 * shares for as long as either is drawn.
 */
final class RoomDaylight {
    /** How far from an opening, in tiles, its light falls to half of what it is beside it. */
    static final float REACH = 2.5f;
    /** The most of the open ground's light a room square has from the day, however many openings are round it. */
    static final float MOST = 0.8f;
    /** How many levels over a room are looked through for openings while there is a room over it with no floor between. */
    static final int LEVELS_OPEN_OVER = 2;
    /** How often a room's shares are worked out again when the game has marked nothing on its chunks. */
    private static final long REFRESH_NANOS = 2_000_000_000L;

    /** The map as the shares read it: RoomSight's, and how much of the day each edge lets through. */
    interface Edges extends RoomSight.Ground {
        /** How much of the day passes the north edge of a square, or with north false its west edge: 0 for none, 1 for all. */
        float through(int x, int y, int z, boolean north);
    }

    /** Where a room's squares lie. */
    interface Rooms {
        /** fromX, fromY, toX, toY of the box of the room with that id, into box. */
        void box(long room, int[] box);
    }

    /** One room's shares over the box of its squares on one level. */
    static final class Shares {
        final int fromX, fromY, width, height;
        final float[] share;
        /** The marks on the room's chunks when it was worked out (ChunkMarks.structuresIn), when it is next due, and the frame they were last looked at in. */
        int marks;
        long due, looked;
        /** The openings the shares were worked out from, as Openings holds them, and the squares of the room in the box, as Openings.members has them. */
        private float[] from = new float[0];
        private long members;

        Shares(int fromX, int fromY, int width, int height) {
            this.fromX = fromX;
            this.fromY = fromY;
            this.width = width;
            this.height = height;
            share = new float[width * height];
        }

        /** The share for a square of the room, 0 for one outside its box. */
        float at(int x, int y) {
            int i = x - fromX, j = y - fromY;
            return i < 0 || j < 0 || i >= width || j >= height ? 0f : share[j * width + i];
        }

        boolean sameAs(Shares other) {
            return other != null && other.fromX == fromX && other.fromY == fromY && other.width == width && other.height == height && Arrays.equals(other.share, share);
        }
    }

    /** The loaded map, the tiles fromX..toX and fromY..toY: past it a square is not there, which the map reads as in no room, and an edge of a room would be taken for an opening. */
    record Loaded(int fromX, int fromY, int toX, int toY) {
        boolean holds(int x, int y) {
            return x >= fromX && y >= fromY && x <= toX && y <= toY;
        }
    }

    /** Openings found: x, y, height over the room's floor in tiles, and how much of the day each lets through, four floats each. */
    private static final class Openings {
        float[] at = new float[32];
        int count;
        /** The squares of the room the openings were looked for round, each folded in as it was found. */
        long members;

        void add(float x, float y, float up, float weight) {
            if (count * 4 == at.length) {
                at = Arrays.copyOf(at, at.length * 2);
            }
            at[count * 4] = x;
            at[count * 4 + 1] = y;
            at[count * 4 + 2] = up;
            at[count * 4 + 3] = weight;
            count++;
        }

        /** How much of the day reaches a point of the room's floor: 0 with none, towards 1 with many near. */
        float reached(float x, float y) {
            float sum = 0f;
            for (int o = 0; o < count; o++) {
                float dx = (x - at[o * 4]) / REACH, dy = (y - at[o * 4 + 1]) / REACH, dz = at[o * 4 + 2] / REACH;
                sum += at[o * 4 + 3] / (1f + dx * dx + dy * dy + dz * dz);
            }
            return 1f - (float) Math.exp(-sum);
        }
    }

    /** What working out a room's shares writes into as it goes, kept from one room to the next. */
    static final class Scratch {
        private final Openings own = new Openings();
        private final ArrayList<Openings> spare = new ArrayList<>();
        private final Map<Long, Openings> beside = new HashMap<>();
        private final int[] box = new int[4], besideBox = new int[4];

        private Openings fresh() {
            Openings openings = spare.isEmpty() ? new Openings() : spare.remove(spare.size() - 1);
            openings.count = 0;
            openings.members = 0L;
            return openings;
        }

        private void reset() {
            own.count = 0;
            own.members = 0L;
            spare.addAll(beside.values());
            beside.clear();
        }
    }

    /** The shares for the squares of the room with that id on level z. */
    static Shares of(Edges map, Rooms rooms, long room, int z, Loaded loaded, Scratch scratch) {
        return of(map, rooms, room, z, loaded, scratch, null);
    }

    /**
     * The same, or was itself when the room's squares and openings are what was was worked out
     * from: the shares are then the same, and a tower floor of three thousand squares and a
     * hundred windows is not summed again square by square.
     */
    static Shares of(Edges map, Rooms rooms, long room, int z, Loaded loaded, Scratch scratch, Shares was) {
        scratch.reset();
        rooms.box(room, scratch.box);
        int[] box = clip(scratch.box, loaded);
        Openings openings = openings(map, rooms, room, z, box, loaded, scratch, scratch.own, true);
        int width = Math.max(box[2] - box[0] + 1, 0), height = Math.max(box[3] - box[1] + 1, 0);
        int floats = openings.count * 4;
        if (was != null && was.fromX == box[0] && was.fromY == box[1] && was.width == width && was.height == height && was.members == openings.members
                && Arrays.equals(was.from, 0, was.from.length, openings.at, 0, floats)) {
            return was;
        }
        Shares shares = new Shares(box[0], box[1], width, height);
        shares.from = Arrays.copyOf(openings.at, floats);
        shares.members = openings.members;
        if (openings.count == 0) {
            return shares;
        }
        for (int j = box[1]; j <= box[3]; j++) {
            for (int i = box[0]; i <= box[2]; i++) {
                if (map.region(i, j, z) == room) {
                    shares.share[(j - box[1]) * shares.width + (i - box[0])] = MOST * openings.reached(i + 0.5f, j + 0.5f);
                }
            }
        }
        return shares;
    }

    private static int[] clip(int[] box, Loaded loaded) {
        box[0] = Math.max(box[0], loaded.fromX);
        box[1] = Math.max(box[1], loaded.fromY);
        box[2] = Math.min(box[2], loaded.toX);
        box[3] = Math.min(box[3], loaded.toY);
        return box;
    }

    /**
     * A room's openings into found. With throughRooms, an opening to another room lets through
     * what that room has from the open air at the square beyond, its own openings found without;
     * without, only openings to the open air are found.
     */
    private static Openings openings(Edges map, Rooms rooms, long room, int z, int[] box, Loaded loaded, Scratch scratch, Openings found, boolean throughRooms) {
        for (int y = box[1]; y <= box[3]; y++) {
            for (int x = box[0]; x <= box[2]; x++) {
                if (map.region(x, y, z) != room) {
                    continue;
                }
                found.members = found.members * 31L + ((long) y << 32 ^ x);
                // Walls stand on a square's north and west edges: the south edge is the north edge of the square below, the east edge the west edge of the one beside.
                for (int side = 0; side < 4; side++) {
                    int edgeX = side == 3 ? x + 1 : x, edgeY = side == 2 ? y + 1 : y;
                    boolean north = side == 0 || side == 2;
                    int beyondX = side == 1 ? x - 1 : side == 3 ? x + 1 : x, beyondY = side == 0 ? y - 1 : side == 2 ? y + 1 : y;
                    if (!loaded.holds(beyondX, beyondY)) {
                        continue;
                    }
                    long beyond = map.region(beyondX, beyondY, z);
                    if (beyond == room) {
                        continue;
                    }
                    float openX = north ? x + 0.5f : edgeX, openY = north ? edgeY : y + 0.5f;
                    float through = map.through(edgeX, edgeY, z, north);
                    if (through > 0f) {
                        if (beyond == RoomSight.OUTSIDE) {
                            found.add(openX, openY, 0f, through);
                        } else if (throughRooms) {
                            Openings next = scratch.beside.get(beyond);
                            if (next == null) {
                                rooms.box(beyond, scratch.besideBox);
                                next = openings(map, rooms, beyond, z, clip(scratch.besideBox, loaded), loaded, scratch, scratch.fresh(), false);
                                scratch.beside.put(beyond, next);
                            }
                            float passed = through * next.reached(beyondX + 0.5f, beyondY + 0.5f);
                            if (passed > 0f) {
                                found.add(openX, openY, 0f, passed);
                            }
                        }
                    }
                    // Over the room's own walls, the levels over it while a room is over it with no floor between.
                    for (int over = 1; over <= LEVELS_OPEN_OVER && map.region(x, y, z + over) != RoomSight.OUTSIDE && !map.floored(x, y, z + over); over++) {
                        if (map.region(beyondX, beyondY, z + over) == RoomSight.OUTSIDE) {
                            float high = map.through(edgeX, edgeY, z + over, north);
                            if (high > 0f) {
                                found.add(openX, openY, over * ViewCamera.LEVEL_HEIGHT, high);
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    private final Map<Long, Shares> rooms = new ConcurrentHashMap<>();
    private final Scratch scratch = new Scratch();
    /** The room's box from its rectangles in the map, which hold for a square's room id whether or not the game has made the room yet. */
    private static final Rooms MAP_ROOMS = (room, box) -> {
        RoomDef def = IsoWorld.instance.getMetaGrid().getRoomDefByID(room);
        box[0] = box[1] = Integer.MAX_VALUE;
        box[2] = box[3] = Integer.MIN_VALUE;
        for (RoomDef.RoomRect rect : def.rects) {
            box[0] = Math.min(box[0], rect.x);
            box[1] = Math.min(box[1], rect.y);
            box[2] = Math.max(box[2], rect.x + rect.w - 1);
            box[3] = Math.max(box[3], rect.y + rect.h - 1);
        }
    };
    /** Dev only: rooms worked out since the last asking, the time it took, and the longest one with its box's squares. */
    private long tollRooms, tollNanos, tollLongestNanos, tollLongestSquares;

    /**
     * Logic thread: the share of the open ground's light for a square in a room, worked out
     * again when the game has marked the room's chunks since, or it is due. The loaded map is
     * the square of tiles from (loadedFromX, loadedFromY), side tiles wide.
     */
    float share(Edges map, IsoGridSquare square, int loadedFromX, int loadedFromY, int side, long now) {
        long id = square.getRoomID();
        if (id == RoomSight.OUTSIDE) {
            return 0f;
        }
        Shares shares = rooms.get(id);
        if (shares != null && shares.looked == now) {
            return shares.at(square.getX(), square.getY());
        }
        if (shares == null || shares.marks != marksOn(shares) || now >= shares.due) {
            long began = System.nanoTime();
            Shares made = of(map, MAP_ROOMS, id, square.getZ(), new Loaded(loadedFromX, loadedFromY, loadedFromX + side - 1, loadedFromY + side - 1), scratch, shares);
            made.marks = marksOn(made);
            // Each room at its own moment of the period, so that the rooms in sight are not all worked out on one frame.
            made.due = now + REFRESH_NANOS + (shares == null ? Math.floorMod(id * 0x9E3779B97F4A7C15L, REFRESH_NANOS) : 0L);
            if (shares != null && !made.sameAs(shares)) {
                tellChunks(made);
            }
            rooms.put(id, made);
            shares = made;
            long took = System.nanoTime() - began;
            tollRooms++;
            tollNanos += took;
            if (took > tollLongestNanos) {
                tollLongestNanos = took;
                tollLongestSquares = (long) made.width * made.height;
            }
        }
        shares.looked = now;
        return shares.at(square.getX(), square.getY());
    }

    /** The marks on the chunks a room's box is on and the ring round it, which hold the walls of its south and east edges. */
    private static int marksOn(Shares shares) {
        return ChunkMarks.structuresIn(Math.floorDiv(shares.fromX - 1, WorldScene.CHUNK_TILES), Math.floorDiv(shares.fromY - 1, WorldScene.CHUNK_TILES),
                Math.floorDiv(shares.fromX + shares.width, WorldScene.CHUNK_TILES), Math.floorDiv(shares.fromY + shares.height, WorldScene.CHUNK_TILES));
    }

    private static void tellChunks(Shares shares) {
        ChunkMarks.tell(Math.floorDiv(shares.fromX, WorldScene.CHUNK_TILES), Math.floorDiv(shares.fromY, WorldScene.CHUNK_TILES),
                Math.floorDiv(shares.fromX + shares.width - 1, WorldScene.CHUNK_TILES), Math.floorDiv(shares.fromY + shares.height - 1, WorldScene.CHUNK_TILES));
    }

    /** Any thread: the share last worked out for a square's room, or 0 where none has been. */
    float sharedAt(IsoGridSquare square) {
        long id = square.getRoomID();
        if (id == RoomSight.OUTSIDE) {
            return 0f;
        }
        Shares shares = rooms.get(id);
        return shares == null ? 0f : shares.at(square.getX(), square.getY());
    }

    /** Logic thread: lets go of every room with no square on the loaded map, whose west and north chunk and width in chunks are given. */
    void forgetOutside(int mapWx, int mapWy, int mapSide) {
        int fromX = mapWx * WorldScene.CHUNK_TILES, fromY = mapWy * WorldScene.CHUNK_TILES, to = mapSide * WorldScene.CHUNK_TILES;
        for (Iterator<Shares> each = rooms.values().iterator(); each.hasNext(); ) {
            Shares shares = each.next();
            if (shares.fromX + shares.width <= fromX || shares.fromY + shares.height <= fromY || shares.fromX >= fromX + to || shares.fromY >= fromY + to) {
                each.remove();
            }
        }
    }

    /** Lets go of every room: the map they are of is gone. */
    void forget() {
        rooms.clear();
    }

    /** Dev only: the rooms worked out since the last asking and what they cost. */
    String tolls() {
        String told = String.format(java.util.Locale.ROOT, "room daylight worked out %d times in %.2f ms, longest %.3f ms (%d squares), %d rooms kept",
                tollRooms, tollNanos / 1e6, tollLongestNanos / 1e6, tollLongestSquares, rooms.size());
        tollRooms = tollNanos = tollLongestNanos = tollLongestSquares = 0;
        return told;
    }
}
