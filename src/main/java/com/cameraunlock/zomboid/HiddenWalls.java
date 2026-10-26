package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import se.krka.kahlua.vm.KahluaTable;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.RoomDef;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteInstance;

/**
 * The south and east walls of a room, which the game's camera, looking in from the south east,
 * never shows from inside: the map's makers hung what a room has on its north and west walls, and
 * in first person the player turns round to two bare walls. Here a room's hidden walls are hung
 * with the game's own things that are drawn facing north or west: a mirror, a clock, a coat rack.
 * The game has no picture, photograph, poster or calendar that faces that way.
 *
 * What a room gets goes by its name on the map and by what it already has: a bathroom with no
 * mirror gets one over a sink against a hidden wall, a bedroom one, a kitchen or an office with no
 * clock a clock, a ground floor hall with a door out a coat rack, a living room a clock and a
 * mirror where its other walls carry that much. The mirror and the clock are of a kind the
 * building already has where it has one. A room with nothing to say what it is, a garage, a store
 * or a cellar, is left as the map has it.
 *
 * What is hung is the game's own object, put on its square as the map's loader puts one, so it is
 * saved with the chunk and can be looked at, taken down and broken as the map's own can. A room is
 * dressed once, the first time it and the squares round it are loaded, and its first square
 * carries a mark in its mod data that says so, in the save. Offline only: on a server the server
 * decides what is in the world.
 */
final class HiddenWalls {
    private HiddenWalls() {}

    /** The mod data key of the first square of a room that says the room's hidden walls have been dressed. */
    static final String DRESSED = "CameraUnlockHiddenWalls";
    private static final int CHUNK = Terrain.CHUNK_TILES;
    /**
     * The things hung, by the name and group the game gives them: the sprite that faces north
     * (hung on a south wall) and the one that faces west (on an east wall), or null where the game
     * has none. The game's small mirror that faces west is marked as standing on the floor.
     */
    private static final Map<String, String[]> FACING_IN = new HashMap<>();
    static {
        FACING_IN.put("Mirror|Small", new String[] {"walls_decoration_01_24", null});
        FACING_IN.put("Mirror|Tall", new String[] {"walls_decoration_01_27", "walls_decoration_01_26"});
        FACING_IN.put("Mirror|Wooden Framed", new String[] {"walls_decoration_01_29", "walls_decoration_01_28"});
        FACING_IN.put("Mirror|Large", new String[] {"walls_decoration_01_31", "walls_decoration_01_30"});
        FACING_IN.put("Clock|Wall", new String[] {"location_community_school_01_34", "location_community_school_01_35"});
        FACING_IN.put("Clock|Square Wall", new String[] {"walls_decoration_01_106", "walls_decoration_01_107"});
        FACING_IN.put("Rack|Coat", new String[] {"location_community_school_01_38", "location_community_school_01_39"});
    }

    /** What a room is to its walls. */
    enum Kind { BATH, BED, KITCHEN, LIVING, HALL, OFFICE, NONE }

    /** Which wall of its square a spot is on. */
    static final int SOUTH = 0, EAST = 1;

    /** The highest a thing under a clock may stand, as the game gives a surface: a kitchen counter's top is 34 to 38. */
    private static final int UNDER_A_CLOCK = 40;

    /** What a spot loses for being on a wall something has been hung on already: more than any stretch of wall gives. */
    private static final int WALL_HUNG_ON = 1000;

    private static final ArrayList<RoomDef> ROOMS = new ArrayList<>();
    private static final ArrayList<IsoSprite> SPRITES = new ArrayList<>();

    /**
     * A place on a hidden wall: the square in the room and which of its far walls, whether anything
     * stands on the square, whether all that stands there is low enough to hang a clock over, and
     * whether it is a sink against that wall and nothing else.
     */
    record Spot(IsoGridSquare square, int wall, boolean stands, boolean low, boolean sink) {
        boolean bare() {
            return !stands;
        }
    }

    /**
     * Called as the game has loaded a chunk's squares, on its logic thread: every room the chunk
     * or the squares round it touch that is now loaded whole, and not yet dressed, is dressed, and
     * the pictures drawn on its hidden walls are chosen once a session (plan).
     */
    static void loaded(IsoChunk chunk) {
        boolean dresses = Rules.dressesHiddenWalls(), draws = Rules.drawsHiddenWallPictures();
        if (!dresses && !draws) {
            return;
        }
        IsoCell cell = IsoWorld.instance.currentCell;
        ROOMS.clear();
        IsoWorld.instance.getMetaGrid().getRoomsIntersecting(chunk.wx * CHUNK - 1, chunk.wy * CHUNK - 1, CHUNK + 2, CHUNK + 2, ROOMS);
        for (int i = 0; i < ROOMS.size(); i++) {
            RoomDef room = ROOMS.get(i);
            boolean plans = draws && !PLANNED.contains(room);
            if (room.userDefined || kindOf(room.getName()) == Kind.NONE || !dresses && !plans || !whole(cell, room)) {
                continue;
            }
            IsoGridSquare first = firstSquare(cell, room);
            if (dresses && !dressed(first)) {
                dress(cell, room, first);
            }
            if (plans) {
                PLANNED.add(room);
                plan(cell, room);
            }
        }
        ROOMS.clear();
    }

    /**
     * A picture the mod draws on a hidden wall in first person and nowhere else: nothing of it is
     * in the world or the save, and it cannot be taken down (owner ruling of 2026-10-26: "purely
     * decorative, we just don't want first person users to see blank walls in literally every
     * house/room/floor"). The picture's number in Decor's index, where along the square's wall
     * it hangs from and to as the map's x or y grows, and its foot and head in levels.
     */
    record Drawn(int picture, float from, float to, float bottom, float top) {}

    /** The pictures drawn on hidden walls, by square and wall (key). Written on the logic thread, read by the first person builds. */
    private static final ConcurrentHashMap<Long, Drawn> DRAWN = new ConcurrentHashMap<>();
    /** The rooms whose pictures have been chosen since the save was loaded. Logic thread. */
    private static final Set<RoomDef> PLANNED = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * How long the longer side of a painting or photograph drawn on a hidden wall is, and of a
     * certificate, in tiles, as big as the game's own; and how high up the wall its middle is, in levels.
     */
    private static final float DRAWN_LONG_SIDE = 0.85f, DRAWN_CERTIFICATE = 0.45f, DRAWN_MIDDLE = 0.62f;
    /** The most a room's hidden walls carry, the game's things hung there by dress and the pictures drawn there together. */
    private static final int MOST_ON_HIDDEN_WALLS = 3;
    /**
     * The game's paintings of a landscape or of flowers: pictures of nobody and of no taste in
     * particular, which hang in any home.
     */
    static final List<String> GENERIC = List.of("painting-desert-landscape", "painting-flowers-on-table", "painting-helianthus", "painting-island-beach",
            "painting-lakeside", "painting-lighthouse", "painting-lillys-waterpond", "painting-mount-never-rest", "painting-night-with-stars",
            "painting-riverside", "painting-waterfall");

    static long key(int x, int y, int z, int wall) {
        return (((long) z & 0xff) << 56 | ((long) y & 0xffffffL) << 32 | ((long) x & 0xffffffL) << 8) | wall;
    }

    /** Whether a picture is drawn on either hidden wall of a square. Any thread. */
    static boolean drawnOn(int x, int y, int z) {
        return DRAWN.containsKey(key(x, y, z, SOUTH)) || DRAWN.containsKey(key(x, y, z, EAST));
    }

    /**
     * The picture drawn on a square's south (or east) wall, or null: none where the rule has
     * been turned off since, or where the wall is no longer a place for one, the player having
     * cut a door in it, hung something on it or stood something tall against it. A build's thread.
     */
    static Drawn drawnAt(IsoCell cell, int x, int y, int z, int wall, ArrayList<IsoSprite> sprites) {
        Drawn drawn = DRAWN.get(key(x, y, z, wall));
        if (drawn == null || !Rules.drawsHiddenWallPictures()) {
            return null;
        }
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        RoomDef room = IsoWorld.instance.getMetaGrid().getRoomAt(x, y, z);
        if (square == null || room == null) {
            return null;
        }
        Spot spot = spot(cell, room, square, wall, sprites);
        return spot != null && (spot.bare() || spot.low()) && !nearAnOpening(cell, spot) ? drawn : null;
    }

    /** Called as the game leaves a save: the next save's rooms choose their pictures again. */
    static void forget() {
        DRAWN.clear();
        PLANNED.clear();
    }

    /** What a room is, by its name on the map. */
    static Kind kindOf(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("bathroom") || n.equals("showers")) {
            return Kind.BATH;
        }
        if (n.contains("bedroom") || n.equals("agriworkerdorm") || n.equals("nursery")) {
            return Kind.BED;
        }
        // A works breakroom is a kitchenette with a table: a clock, not a mirror.
        if (n.endsWith("kitchen") && !n.equals("emptykitchen") || n.equals("kitchen_crepe") || n.equals("breakroom") || n.equals("breakoom")) {
            return Kind.KITCHEN;
        }
        if (n.contains("livingroom") || n.equals("liviingroom") || n.contains("dining") || n.equals("diningr") || n.equals("sunroom")
                || n.equals("gameroom") || n.equals("homecinema") || n.equals("sewingroom")
                || n.equals("waitingroom") || n.equals("viplounge")) {
            return Kind.LIVING;
        }
        if (n.equals("hall") || n.equals("hallway") || n.equals("hallway2") || n.equals("ww_hall") || n.equals("foyer") || n.equals("lobby")
                || n.equals("mainhall") || n.equals("elementaryhall") || n.equals("secondaryhall") || n.equals("hospitalhallway")) {
            return Kind.HALL;
        }
        if (n.endsWith("office") || n.equals("meeting") || n.equals("meetingroom") || n.contains("classroom") || n.endsWith("library")
                || n.equals("motelreception")) {
            return Kind.OFFICE;
        }
        return Kind.NONE;
    }

    /**
     * Whether every square of the room is loaded, and the squares round it: those whose walls are
     * the room's south and east walls, and those a door beside a corner of the room is in.
     */
    private static boolean whole(IsoCell cell, RoomDef room) {
        for (int i = 0; i < room.rects.size(); i++) {
            RoomDef.RoomRect rect = room.rects.get(i);
            for (int cy = Math.floorDiv(rect.y - 1, CHUNK); cy <= Math.floorDiv(rect.y + rect.h, CHUNK); cy++) {
                for (int cx = Math.floorDiv(rect.x - 1, CHUNK); cx <= Math.floorDiv(rect.x + rect.w, CHUNK); cx++) {
                    if (cell.getChunkForGridSquare(cx * CHUNK, cy * CHUNK, room.level) == null) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static IsoGridSquare firstSquare(IsoCell cell, RoomDef room) {
        RoomDef.RoomRect rect = room.rects.get(0);
        IsoGridSquare first = cell.getGridSquare(rect.x, rect.y, room.level);
        if (first == null) {
            throw new IllegalStateException("the " + room.getName() + " at " + rect.x + "," + rect.y + "," + room.level + " is loaded and has no first square");
        }
        return first;
    }

    /**
     * The sprites of a thing on a square: its own, and those the map's loader laid over it. The
     * loader hangs a mirror or a picture flagged as an overlay of a wall on that wall, not as a
     * thing of its own.
     */
    private static ArrayList<IsoSprite> spritesOf(IsoObject object) {
        return spritesOf(object, SPRITES);
    }

    private static ArrayList<IsoSprite> spritesOf(IsoObject object, ArrayList<IsoSprite> sprites) {
        sprites.clear();
        if (object.getSprite() != null) {
            sprites.add(object.getSprite());
        }
        ArrayList<IsoSpriteInstance> over = object.getAttachedAnimSprite();
        if (over != null) {
            for (int i = 0; i < over.size(); i++) {
                sprites.add(over.get(i).parentSprite);
            }
        }
        return sprites;
    }

    static boolean dressed(IsoGridSquare first) {
        return first.hasModData() && first.getModData().rawget(DRESSED) != null;
    }

    private static boolean inRoom(RoomDef room, int x, int y) {
        for (int i = 0; i < room.rects.size(); i++) {
            RoomDef.RoomRect rect = room.rects.get(i);
            if (x >= rect.x && x < rect.x + rect.w && y >= rect.y && y < rect.y + rect.h) {
                return true;
            }
        }
        return false;
    }

    private static ArrayList<IsoGridSquare> squaresOf(IsoCell cell, RoomDef room) {
        ArrayList<IsoGridSquare> squares = new ArrayList<>();
        for (int i = 0; i < room.rects.size(); i++) {
            RoomDef.RoomRect rect = room.rects.get(i);
            for (int y = rect.y; y < rect.y + rect.h; y++) {
                for (int x = rect.x; x < rect.x + rect.w; x++) {
                    IsoGridSquare square = cell.getGridSquare(x, y, room.level);
                    if (square != null) {
                        squares.add(square);
                    }
                }
            }
        }
        return squares;
    }

    /**
     * Chooses the pictures drawn on a room's hidden walls (Drawn). A bedroom, a living room, a
     * hall or an office gets a picture on each of its hidden walls that nothing hangs on yet,
     * clear of doors and windows and over nothing taller than a counter, until its hidden walls
     * carry as many things as its shown walls do, at least one and at most MOST_ON_HIDDEN_WALLS.
     * A home's picture is a landscape or flowers; a business's is one of the pictures its kind
     * shows (Decor's pools), a certificate in its office. Neither is one the building shows already.
     */
    private static void plan(IsoCell cell, RoomDef room) {
        Kind kind = kindOf(room.getName());
        if (kind != Kind.BED && kind != Kind.LIVING && kind != Kind.HALL && kind != Kind.OFFICE) {
            return;
        }
        ArrayList<IsoGridSquare> squares = squaresOf(cell, room);
        int shown = 0;
        ArrayList<Spot> placed = new ArrayList<>();
        ArrayList<Spot> spots = new ArrayList<>();
        for (int i = 0; i < squares.size(); i++) {
            IsoGridSquare square = squares.get(i);
            for (int j = 0; j < square.getObjects().size(); j++) {
                IsoObject object = square.getObjects().get(j);
                ArrayList<IsoSprite> sprites = spritesOf(object);
                boolean south = false, east = false;
                for (int k = 0; k < sprites.size(); k++) {
                    PropertyContainer properties = sprites.get(k).getProperties();
                    if (hungOnAShownWall(object, properties)) {
                        shown++;
                    }
                    // Only what is hung to be looked at, as hungOnAShownWall counts it: a curtain or a lamp leaves the wall bare.
                    boolean looked = "WallObject".equals(properties.get("MoveType")) && object.getType() != IsoObjectType.lightswitch
                            && !properties.has("HasLightOnSprite") && !properties.has("LightRadius") && !properties.has("BlocksPlacement");
                    south |= looked && hungOn(properties, SOUTH);
                    east |= looked && hungOn(properties, EAST);
                }
                if (south) {
                    placed.add(new Spot(square, SOUTH, false, false, false));
                }
                if (east) {
                    placed.add(new Spot(square, EAST, false, false, false));
                }
            }
            for (int wall = SOUTH; wall <= EAST; wall++) {
                Spot spot = spot(cell, room, square, wall);
                if (spot != null && (spot.bare() || spot.low())) {
                    spots.add(spot);
                }
            }
        }
        spots.removeIf(spot -> nearAnOpening(cell, spot));
        String decorKind = Decor.kindOf(room);
        boolean certificate = !Decor.HOME.equals(decorKind) && kind == Kind.OFFICE && Decor.pool("certificate", decorKind) != null;
        int[] pool = Decor.HOME.equals(decorKind) ? generic() : Decor.pool(certificate ? "certificate" : "family", decorKind);
        if (pool == null) {
            return;
        }
        Set<Integer> taken = null;
        int want = Math.min(MOST_ON_HIDDEN_WALLS, Math.max(1, shown)) - placed.size();
        int mixed = room.getBuilding().getX() * 31 + room.getBuilding().getY() * 17 + room.rects.get(0).x * 7 + room.rects.get(0).y;
        ArrayList<String> drawn = new ArrayList<>();
        while (want-- > 0) {
            Spot best = null;
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < spots.size(); i++) {
                Spot spot = spots.get(i);
                if (onAWallHungOn(placed, spot)) {
                    continue;
                }
                int score = stretch(spots, spot);
                if (score > bestScore || score == bestScore && before(spot, best)) {
                    best = spot;
                    bestScore = score;
                }
            }
            if (best == null) {
                break;
            }
            if (taken == null) {
                taken = picturesInBuilding(cell, room);
            }
            int picture = unshown(pool, taken, mixed + drawn.size());
            if (picture == Decor.NONE) {
                break;
            }
            taken.add(picture);
            float wideForHigh = Decor.wideForHigh(picture);
            float longSide = certificate ? DRAWN_CERTIFICATE : DRAWN_LONG_SIDE;
            float wide = wideForHigh >= 1f ? longSide : longSide * wideForHigh;
            float high = wide / wideForHigh / ViewCamera.LEVEL_HEIGHT;
            IsoGridSquare square = best.square();
            DRAWN.put(key(square.getX(), square.getY(), room.level, best.wall()),
                    new Drawn(picture, 0.5f - wide / 2f, 0.5f + wide / 2f, DRAWN_MIDDLE - high / 2f, DRAWN_MIDDLE + high / 2f));
            ChunkMarks.tell(Math.floorDiv(square.getX(), CHUNK), Math.floorDiv(square.getY(), CHUNK));
            drawn.add(String.format(Locale.ROOT, "%s on the %s wall at %d,%d", Decor.nameOf(picture), best.wall() == SOUTH ? "south" : "east",
                    square.getX(), square.getY()));
            placed.add(best);
            Spot hung = best;
            spots.removeIf(spot -> spot.wall() == hung.wall() && Math.abs(spot.square().getX() - hung.square().getX())
                    + Math.abs(spot.square().getY() - hung.square().getY()) <= 1);
        }
        if (!drawn.isEmpty()) {
            Log.info(String.format(Locale.ROOT, "drew on the hidden walls of the %s at %d,%d,%d (%d hung on its other walls): %s", room.getName(),
                    room.rects.get(0).x, room.rects.get(0).y, room.level, shown, String.join(", ", drawn)));
        }
    }

    private static int[] generic;

    private static int[] generic() {
        if (generic == null) {
            int[] made = new int[GENERIC.size()];
            for (int i = 0; i < made.length; i++) {
                made[i] = Decor.named(GENERIC.get(i));
            }
            generic = made;
        }
        return generic;
    }

    /** One of a pool's pictures not in taken, picked by a number the room gives, or Decor.NONE where taken has every one. */
    static int unshown(int[] pool, Set<Integer> taken, int pick) {
        ArrayList<Integer> left = new ArrayList<>();
        for (int picture : pool) {
            if (!taken.contains(picture)) {
                left.add(picture);
            }
        }
        return left.isEmpty() ? Decor.NONE : left.get(Math.floorMod(pick, left.size()));
    }

    /** The pictures the first person view shows on the walls of the room's building where it is loaded, and those drawn on its hidden walls. */
    private static Set<Integer> picturesInBuilding(IsoCell cell, RoomDef room) {
        Set<Integer> pictures = new HashSet<>();
        ArrayList<RoomDef> rooms = room.getBuilding().rooms;
        for (int i = 0; i < rooms.size(); i++) {
            RoomDef each = rooms.get(i);
            for (int r = 0; r < each.rects.size(); r++) {
                RoomDef.RoomRect rect = each.rects.get(r);
                for (int y = rect.y; y < rect.y + rect.h; y++) {
                    for (int x = rect.x; x < rect.x + rect.w; x++) {
                        for (int wall = SOUTH; wall <= EAST; wall++) {
                            Drawn drawn = DRAWN.get(key(x, y, each.level, wall));
                            if (drawn != null) {
                                pictures.add(drawn.picture());
                            }
                        }
                        IsoGridSquare square = cell.getGridSquare(x, y, each.level);
                        if (square == null) {
                            continue;
                        }
                        for (int j = 0; j < square.getObjects().size(); j++) {
                            ArrayList<IsoSprite> sprites = spritesOf(square.getObjects().get(j));
                            for (int k = 0; k < sprites.size(); k++) {
                                IsoSprite sprite = sprites.get(k);
                                PropertyContainer properties = sprite.getProperties();
                                int picture = Decor.of(sprite.getName(), properties.get("CustomName"), properties.get("GroupName") == null ? "" : properties.get("GroupName"),
                                        sprite.getSpriteGrid() != null);
                                if (picture != Decor.NONE) {
                                    pictures.add(Decor.inRoom(picture, x, y, each.level));
                                }
                            }
                        }
                    }
                }
            }
        }
        return pictures;
    }

    private static void dress(IsoCell cell, RoomDef room, IsoGridSquare first) {
        Kind kind = kindOf(room.getName());
        ArrayList<IsoGridSquare> squares = squaresOf(cell, room);
        int shown = 0;
        boolean mirror = false, clock = false, rack = false;
        ArrayList<Spot> spots = new ArrayList<>();
        for (int i = 0; i < squares.size(); i++) {
            IsoGridSquare square = squares.get(i);
            for (int j = 0; j < square.getObjects().size(); j++) {
                IsoObject object = square.getObjects().get(j);
                ArrayList<IsoSprite> sprites = spritesOf(object);
                for (int k = 0; k < sprites.size(); k++) {
                    PropertyContainer properties = sprites.get(k).getProperties();
                    String called = properties.get("CustomName");
                    mirror |= "Mirror".equals(called) || properties.has("IsMirror");
                    clock |= "Clock".equals(called);
                    rack |= "Rack".equals(called);
                    if (hungOnAShownWall(object, properties)) {
                        shown++;
                    }
                }
            }
            for (int wall = SOUTH; wall <= EAST; wall++) {
                Spot spot = spot(cell, room, square, wall);
                if (spot != null) {
                    spots.add(spot);
                }
            }
        }
        spots.removeIf(spot -> nearAnOpening(cell, spot));
        ArrayList<Spot> placed = new ArrayList<>();
        ArrayList<String> hung = new ArrayList<>();
        Map<String, Integer> kinds = kindsInBuilding(cell, room);
        switch (kind) {
            case BATH -> {
                if (!mirror) {
                    hang(spots, placed, hung, Spot::sink, "Mirror", preferred(kinds, "Mirror", "Large"));
                }
            }
            case BED -> {
                if (!mirror) {
                    hang(spots, placed, hung, Spot::bare, "Mirror", preferred(kinds, "Mirror", "Tall"));
                }
            }
            case KITCHEN, OFFICE -> {
                if (!clock) {
                    hang(spots, placed, hung, spot -> spot.bare() || spot.low(), "Clock", preferred(kinds, "Clock", "Wall"));
                }
            }
            case LIVING -> {
                if (!clock) {
                    hang(spots, placed, hung, spot -> spot.bare() || spot.low(), "Clock", preferred(kinds, "Clock", "Wall"));
                }
                if (!mirror && shown >= 2 && home(room)) {
                    hang(spots, placed, hung, Spot::bare, "Mirror", preferred(kinds, "Mirror", "Wooden Framed"));
                }
            }
            case HALL -> {
                if (!rack && room.level == 0 && doorOut(cell, room, squares)) {
                    hang(spots, placed, hung, Spot::bare, "Rack", "Coat");
                }
                if (!mirror && shown >= 1 && home(room)) {
                    hang(spots, placed, hung, Spot::bare, "Mirror", preferred(kinds, "Mirror", "Wooden Framed"));
                }
            }
            case NONE -> { }
        }
        KahluaTable data = first.getModData();
        data.rawset(DRESSED, Boolean.TRUE);
        if (!hung.isEmpty()) {
            Log.info(String.format(Locale.ROOT, "hung on the hidden walls of the %s at %d,%d,%d (%d hung on its other walls): %s", room.getName(),
                    first.getX(), first.getY(), first.getZ(), shown, String.join(", ", hung)));
        }
    }

    /** Whether a thing on a square of a room hangs on one of the walls the game shows, its north or west, and is something hung to be looked at. */
    private static boolean hungOnAShownWall(IsoObject object, PropertyContainer properties) {
        if (!"WallObject".equals(properties.get("MoveType")) || object.getType() == IsoObjectType.lightswitch || properties.has("HasLightOnSprite")
                || properties.has("LightRadius") || properties.has("BlocksPlacement")) {
            return false;
        }
        if (properties.has(IsoFlagType.attachedN) || properties.has(IsoFlagType.attachedW)) {
            return true;
        }
        String facing = properties.get("Facing");
        return !properties.has(IsoFlagType.attachedS) && !properties.has(IsoFlagType.attachedE) && ("S".equals(facing) || "E".equals(facing));
    }

    /**
     * The place on the far wall of a square of the room, or null: the square beyond is no part of
     * the room and has a whole wall on the edge the two share, with no door or window in it, and
     * nothing stands on the square that a thing hung there would go through.
     */
    private static Spot spot(IsoCell cell, RoomDef room, IsoGridSquare square, int wall) {
        return spot(cell, room, square, wall, SPRITES);
    }

    private static Spot spot(IsoCell cell, RoomDef room, IsoGridSquare square, int wall, ArrayList<IsoSprite> spriteList) {
        int x = square.getX(), y = square.getY();
        int bx = wall == EAST ? x + 1 : x, by = wall == SOUTH ? y + 1 : y;
        if (inRoom(room, bx, by)) {
            return null;
        }
        IsoGridSquare beyond = cell.getGridSquare(bx, by, square.getZ());
        if (beyond == null || !wholeWall(beyond, wall == SOUTH)) {
            return null;
        }
        boolean stands = false, low = true, sink = true;
        for (int i = 0; i < square.getObjects().size(); i++) {
            IsoObject object = square.getObjects().get(i);
            ArrayList<IsoSprite> sprites = spritesOf(object, spriteList);
            for (int k = object.getSprite() == null ? 0 : 1; k < sprites.size(); k++) {
                if (hungOn(sprites.get(k).getProperties(), wall)) {
                    return null;
                }
            }
            IsoSprite sprite = object.getSprite();
            if (sprite == null || object == square.getFloor()) {
                continue;
            }
            PropertyContainer properties = sprite.getProperties();
            if (onTheFloorOrAShownWall(object, properties)) {
                continue;
            }
            if (hungOn(properties, wall)) {
                return null;
            }
            stands = true;
            sink &= "Sink".equals(properties.get("CustomName")) && (wall == SOUTH ? "N" : "W").equals(properties.get("Facing"));
            int surface = Math.max(properties.getSurface(), properties.getItemHeight());
            low &= surface > 0 && surface <= UNDER_A_CLOCK && !properties.has("IsHigh");
        }
        return new Spot(square, wall, stands, stands && low, stands && sink);
    }

    /** Whether a thing on a square lies on its floor, or is on or in one of its own walls, the north and the west. */
    private static boolean onTheFloorOrAShownWall(IsoObject object, PropertyContainer properties) {
        if (properties.has(IsoFlagType.solidfloor) || properties.has(IsoFlagType.FloorOverlay) || properties.has(IsoFlagType.attachedFloor)) {
            return true;
        }
        if (properties.has(IsoFlagType.WallN) || properties.has(IsoFlagType.WallW) || properties.has(IsoFlagType.WallNW) || properties.has(IsoFlagType.WallNTrans)
                || properties.has(IsoFlagType.WallWTrans) || properties.has(IsoFlagType.DoorWallN) || properties.has(IsoFlagType.DoorWallW)
                || properties.has(IsoFlagType.WindowN) || properties.has(IsoFlagType.WindowW) || properties.has(IsoFlagType.windowN)
                || properties.has(IsoFlagType.windowW) || properties.has(IsoFlagType.doorN) || properties.has(IsoFlagType.doorW)
                || properties.has(IsoFlagType.attachedN) || properties.has(IsoFlagType.attachedW) || properties.has(IsoFlagType.attachedNW)) {
            return true;
        }
        IsoObjectType type = object.getType();
        if (type == IsoObjectType.doorFrN || type == IsoObjectType.doorFrW || type == IsoObjectType.windowFN || type == IsoObjectType.windowFW
                || type == IsoObjectType.curtainN || type == IsoObjectType.curtainW) {
            return true;
        }
        if (properties.has(IsoFlagType.WallOverlay) && !properties.has(IsoFlagType.attachedS) && !properties.has(IsoFlagType.attachedE)) {
            return true;
        }
        String facing = properties.get("Facing");
        return "WallObject".equals(properties.get("MoveType")) && ("S".equals(facing) || "E".equals(facing));
    }

    /** Whether a thing hangs on the square's south (or east) wall already: a switch, a mirror, a curtain, anything. */
    private static boolean hungOn(PropertyContainer properties, int wall) {
        if (properties.has(wall == SOUTH ? IsoFlagType.attachedS : IsoFlagType.attachedE)) {
            return true;
        }
        return "WallObject".equals(properties.get("MoveType")) && (wall == SOUTH ? "N" : "W").equals(properties.get("Facing"));
    }

    /** Whether a square has a whole wall on its north (or west) edge: no door, doorway or window in it. */
    private static boolean wholeWall(IsoGridSquare square, boolean north) {
        boolean wall = false;
        for (int i = 0; i < square.getObjects().size(); i++) {
            IsoObject object = square.getObjects().get(i);
            IsoSprite sprite = object.getSprite();
            if (sprite == null) {
                continue;
            }
            PropertyContainer properties = sprite.getProperties();
            IsoObjectType type = object.getType();
            if (north ? properties.has(IsoFlagType.DoorWallN) || properties.has(IsoFlagType.WindowN) || properties.has(IsoFlagType.windowN)
                    || properties.has(IsoFlagType.doorN) || properties.has(IsoFlagType.WallNTrans) || type == IsoObjectType.doorFrN
                    || type == IsoObjectType.windowFN
                    : properties.has(IsoFlagType.DoorWallW) || properties.has(IsoFlagType.WindowW) || properties.has(IsoFlagType.windowW)
                    || properties.has(IsoFlagType.doorW) || properties.has(IsoFlagType.WallWTrans) || type == IsoObjectType.doorFrW
                    || type == IsoObjectType.windowFW) {
                return false;
            }
            wall |= properties.has(north ? IsoFlagType.WallN : IsoFlagType.WallW) || properties.has(IsoFlagType.WallNW);
        }
        return wall;
    }

    /** Whether a door in a wall of the room's squares opens to the outside: a hall the household comes in by. */
    private static boolean doorOut(IsoCell cell, RoomDef room, ArrayList<IsoGridSquare> squares) {
        for (int i = 0; i < squares.size(); i++) {
            IsoGridSquare square = squares.get(i);
            int x = square.getX(), y = square.getY(), z = square.getZ();
            if (door(square, true) && outside(cell.getGridSquare(x, y - 1, z)) || door(square, false) && outside(cell.getGridSquare(x - 1, y, z))) {
                return true;
            }
            IsoGridSquare south = cell.getGridSquare(x, y + 1, z), east = cell.getGridSquare(x + 1, y, z);
            if (south != null && !inRoom(room, x, y + 1) && door(south, true) && outside(south)
                    || east != null && !inRoom(room, x + 1, y) && door(east, false) && outside(east)) {
                return true;
            }
        }
        return false;
    }

    private static boolean outside(IsoGridSquare square) {
        return square != null && square.isOutside();
    }

    /** Whether a square has a door or a doorway on its north (or west) edge. */
    private static boolean door(IsoGridSquare square, boolean north) {
        for (int i = 0; i < square.getObjects().size(); i++) {
            IsoObject object = square.getObjects().get(i);
            IsoSprite sprite = object.getSprite();
            if (sprite == null) {
                continue;
            }
            PropertyContainer properties = sprite.getProperties();
            IsoObjectType type = object.getType();
            if (north ? properties.has(IsoFlagType.DoorWallN) || properties.has(IsoFlagType.doorN) || type == IsoObjectType.doorFrN
                    : properties.has(IsoFlagType.DoorWallW) || properties.has(IsoFlagType.doorW) || type == IsoObjectType.doorFrW) {
                return true;
            }
        }
        return false;
    }

    /** Whether there is a door, a doorway or a window in the same wall within a square of the spot, either side. */
    private static boolean nearAnOpening(IsoCell cell, Spot spot) {
        IsoGridSquare square = spot.square();
        int x = square.getX(), y = square.getY(), z = square.getZ();
        for (int along = -1; along <= 1; along += 2) {
            int bx = spot.wall() == EAST ? x + 1 : x + along, by = spot.wall() == SOUTH ? y + 1 : y + along;
            IsoGridSquare beyond = cell.getGridSquare(bx, by, z);
            if (beyond != null && opening(beyond, spot.wall() == SOUTH)) {
                return true;
            }
        }
        return false;
    }

    private static boolean opening(IsoGridSquare square, boolean north) {
        for (int i = 0; i < square.getObjects().size(); i++) {
            IsoObject object = square.getObjects().get(i);
            IsoSprite sprite = object.getSprite();
            if (sprite == null) {
                continue;
            }
            PropertyContainer properties = sprite.getProperties();
            IsoObjectType type = object.getType();
            if (north ? properties.has(IsoFlagType.DoorWallN) || properties.has(IsoFlagType.WindowN) || properties.has(IsoFlagType.windowN)
                    || properties.has(IsoFlagType.doorN) || type == IsoObjectType.doorFrN || type == IsoObjectType.windowFN
                    : properties.has(IsoFlagType.DoorWallW) || properties.has(IsoFlagType.WindowW) || properties.has(IsoFlagType.windowW)
                    || properties.has(IsoFlagType.doorW) || type == IsoObjectType.doorFrW || type == IsoObjectType.windowFW) {
                return true;
            }
        }
        return false;
    }

    /** Beside bedrooms, bathrooms and living rooms. A block of flats with a janitor's or bin room stays out: its corridors are not a home's. */
    private static final Set<String> HOUSE_ROOMS = Set.of("kitchen", "ww_kitchen", "bandkitchen", "emptykitchen", "pantry", "closet",
            "emptyoutside", "empty", "hall", "hallway", "laundry", "office", "garage", "garagestorage", "storage", "miscstorage", "lobby",
            "attic", "shed", "basement", "basementaccess", "balcony", "porch", "corridor", "diningroom", "sunroom", "sewingroom", "workshop",
            "gym", "recreational", "musicroom");

    /**
     * Whether the room is in a house: every named room of the building one a house has, and one
     * of them a bedroom or a living room. A school corridor, a hospital's waiting room or a
     * restaurant with a flat over it is no home's hall with a mirror. A room the map left
     * nameless says nothing either way.
     */
    private static boolean home(RoomDef room) {
        ArrayList<RoomDef> rooms = room.getBuilding().rooms;
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < rooms.size(); i++) {
            names.add(rooms.get(i).getName());
        }
        return homeOf(names);
    }

    static boolean homeOf(List<String> rooms) {
        boolean lived = false;
        for (int i = 0; i < rooms.size(); i++) {
            String n = rooms.get(i) == null ? "" : rooms.get(i).toLowerCase(Locale.ROOT);
            if (n.isEmpty()) {
                continue;
            }
            boolean bed = n.contains("bedroom"), living = n.contains("livingroom") || n.equals("liviingroom");
            if (!bed && !living && !n.contains("bathroom") && !HOUSE_ROOMS.contains(n)) {
                return false;
            }
            lived |= bed || living;
        }
        return lived;
    }

    /** How many of each mirror and clock the rooms of the room's building that are loaded have, by "Mirror|Large" and the like. */
    private static Map<String, Integer> kindsInBuilding(IsoCell cell, RoomDef room) {
        Map<String, Integer> kinds = new HashMap<>();
        ArrayList<RoomDef> rooms = room.getBuilding().rooms;
        for (int r = 0; r < rooms.size(); r++) {
            RoomDef each = rooms.get(r);
            for (int i = 0; i < each.rects.size(); i++) {
                RoomDef.RoomRect rect = each.rects.get(i);
                for (int y = rect.y; y < rect.y + rect.h; y++) {
                    for (int x = rect.x; x < rect.x + rect.w; x++) {
                        IsoGridSquare square = cell.getGridSquare(x, y, each.level);
                        if (square == null) {
                            continue;
                        }
                        for (int j = 0; j < square.getObjects().size(); j++) {
                            ArrayList<IsoSprite> sprites = spritesOf(square.getObjects().get(j));
                            for (int k = 0; k < sprites.size(); k++) {
                                String called = sprites.get(k).getProperties().get("CustomName");
                                if ("Mirror".equals(called) || "Clock".equals(called)) {
                                    kinds.merge(called + "|" + sprites.get(k).getProperties().get("GroupName"), 1, Integer::sum);
                                }
                            }
                        }
                    }
                }
            }
        }
        return kinds;
    }

    /** The group of mirror or clock the building has most of among those the game draws facing in, or the given one. */
    static String preferred(Map<String, Integer> kinds, String called, String otherwise) {
        String best = otherwise;
        int most = 0;
        for (Map.Entry<String, Integer> each : kinds.entrySet()) {
            String key = each.getKey();
            if (key.startsWith(called + "|") && FACING_IN.containsKey(key) && (each.getValue() > most
                    || each.getValue() == most && key.compareTo(called + "|" + best) < 0)) {
                most = each.getValue();
                best = key.substring(called.length() + 1);
            }
        }
        return best;
    }

    /**
     * Hangs one thing on the best of the spots that will take it, the one in the middle of the
     * longest stretch of wall still clear, on a wall nothing has been hung on yet where there is one,
     * and takes the spots beside it.
     */
    private static void hang(ArrayList<Spot> spots, ArrayList<Spot> placed, ArrayList<String> hung, Predicate<Spot> takes, String called, String group) {
        String[] sprites = FACING_IN.get(called + "|" + group);
        Spot best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < spots.size(); i++) {
            Spot spot = spots.get(i);
            String sprite = sprites[spot.wall()];
            if (sprite == null || !takes.test(spot)) {
                continue;
            }
            int score = stretch(spots, spot) - (onAWallHungOn(placed, spot) ? WALL_HUNG_ON : 0);
            if (score > bestScore || score == bestScore && before(spot, best)) {
                best = spot;
                bestScore = score;
            }
        }
        if (best == null) {
            return;
        }
        String name = sprites[best.wall()];
        IsoGridSquare square = best.square();
        IsoObject thing = IsoObject.getNew(square, name, null, false);
        if (thing.getSprite() == null) {
            throw new IllegalStateException("the game has no sprite " + name + " to hang");
        }
        square.AddTileObject(thing);
        hung.add(String.format(Locale.ROOT, "%s %s on the %s wall at %d,%d (%s)", group, called.toLowerCase(Locale.ROOT), best.wall() == SOUTH ? "south" : "east",
                square.getX(), square.getY(), name));
        placed.add(best);
        Spot taken = best;
        spots.removeIf(spot -> spot.wall() == taken.wall() && Math.abs(spot.square().getX() - taken.square().getX()) + Math.abs(spot.square().getY() - taken.square().getY()) <= 1);
    }

    /** How many spots of the same wall run on unbroken either side of a spot, the fewer of the two: the middle of a long wall scores most. */
    private static int stretch(ArrayList<Spot> spots, Spot spot) {
        int dx = spot.wall() == SOUTH ? 1 : 0, dy = spot.wall() == EAST ? 1 : 0;
        int least = Integer.MAX_VALUE;
        for (int way = -1; way <= 1; way += 2) {
            int run = 0;
            while (has(spots, spot.wall(), spot.square().getX() + way * dx * (run + 1), spot.square().getY() + way * dy * (run + 1))) {
                run++;
            }
            least = Math.min(least, run);
        }
        return least;
    }

    private static boolean has(ArrayList<Spot> spots, int wall, int x, int y) {
        for (int i = 0; i < spots.size(); i++) {
            Spot spot = spots.get(i);
            if (spot.wall() == wall && spot.square().getX() == x && spot.square().getY() == y) {
                return true;
            }
        }
        return false;
    }

    /** Whether something has been hung on the same wall as the spot, the same line of squares. */
    private static boolean onAWallHungOn(ArrayList<Spot> placed, Spot spot) {
        for (int i = 0; i < placed.size(); i++) {
            Spot each = placed.get(i);
            if (each.wall() == spot.wall() && (spot.wall() == SOUTH ? each.square().getY() == spot.square().getY() : each.square().getX() == spot.square().getX())) {
                return true;
            }
        }
        return false;
    }

    /** Whether a spot comes before another in the map's order, so that the choice between two equal spots is always the same. */
    private static boolean before(Spot spot, Spot other) {
        if (other == null) {
            return true;
        }
        IsoGridSquare a = spot.square(), b = other.square();
        return a.getY() != b.getY() ? a.getY() < b.getY() : a.getX() != b.getX() ? a.getX() < b.getX() : spot.wall() < other.wall();
    }
}
