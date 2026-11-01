package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import se.krka.kahlua.vm.KahluaTable;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoCell;
import zombie.iso.IsoChunk;
import zombie.iso.IsoDirections;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.core.textures.Texture;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * The holes blasts have blown through wooden doors and board fences, the marks single pellets
 * have left on doors, and the holes single rounds have gone through doors, walls and fences by,
 * which the first person picture cuts and lays into what it builds of them (MeshCut). The game
 * keeps no such thing, so the mod keeps them in the object's mod data, where they are saved with
 * it, and reads them back as chunks load.
 *
 * Each is eight numbers: how far along the edge it is on, in tiles from its square's corner, and
 * how high, in levels over its floor (as DoorGlass measures a door's lights); its radius in tiles,
 * a round's where it went in; the seed its ragged outline is drawn from; what it is and the side
 * of the edge it was struck from, 1 or -1 (SIDE: 0 a blast's hole, the side for a mark, twice the
 * side for a round's hole); 1 where the edge is the square's north one, 0 its west: a fence's
 * corner length stands on both; and, for a round's hole, how far along and up, in tiles, the
 * round's line goes for each tile it goes across the edge, north to south or west to east, so
 * that the hole is cut along that line and is seen through from where it was shot (0 for the rest,
 * and for a round's hole saved before these were kept). A door's are measured on the leaf as it hangs
 * shut, and laid on the leaf as it stands swung open (OpeningBuilder.addBuiltDoor). A fence's
 * marks are the decals the picture lays over the world (Impacts), as a wall's are.
 */
final class BlastHoles {
    private BlastHoles() {}

    /** The key an object's mod data keeps its holes and marks under. */
    private static final String KEY = "cameraunlockBlastHoles";
    static final int FLOATS = 8, ALONG = 0, UP = 1, RADIUS = 2, SEED = 3, SIDE = 4, NORTH = 5, SLANT_ALONG = 6, SLANT_UP = 7;
    /** How many numbers a hole was saved as before a round's slant was kept, and what a save that keeps it starts with. */
    private static final int OLD_FLOATS = 6;
    private static final String SLANTED = "8:";
    /** The least share of its way a round's line is taken to go across an edge, as ImpactFinder throws out what flies from the far face. */
    static final float LEAST_ACROSS = 0.1f;
    /** Where each hole's and mark's ragged outline is drawn from: below 2^24, so a float in the save holds it whole. */
    private static final Random seeds = new Random();
    /**
     * The most blasts' holes, marks and rounds' holes an object keeps. Past them a hole that the
     * others of its kind cover goes first, so a thing shot to pieces stays in pieces, and only where
     * none is covered the oldest of the kind.
     */
    static final int MOST_HOLES = 32, MOST_MARKS = 48, MOST_ROUNDS = 24;
    /** SIDE over the side a round came from, for a round's hole. */
    static final int ROUND = 2;
    /** How many points a hole's outline has, how ragged it is, and how much taller than wide: wood splits along its grain, and a door's and a fence's grain runs up. */
    private static final int HOLE_POINTS = 18;
    private static final float HOLE_RAGGED = 0.3f, HOLE_TALL = 1.25f;
    /** The same for a mark, and its radius in tiles. */
    private static final int MARK_POINTS = 7;
    static final float MARK_RAGGED = 0.35f, MARK_RADIUS = 0.014f;
    /**
     * A round's hole: how many points each outline has, how ragged the way in is, and how much
     * wider, more ragged and taller the way out is, where the wood splinters and the plaster
     * blows out the back.
     */
    static final int ROUND_POINTS = 9;
    static final float ROUND_RAGGED = 0.2f, EXIT_WIDER = 2.4f, EXIT_RAGGED = 0.45f, EXIT_TALL = 1.2f;
    /**
     * A round's hole where it goes in, in tiles, for a handgun's: larger than a round leaves, so
     * that one a few tiles off is a few pixels across (ImpactDraw draws its mark larger again).
     */
    static final float ROUND_RADIUS = 0.011f;
    /** How far in from the edge of the opening its holes cut a later shot has to cross to go through, as a share of the hole's radius: a round grazing the edge strikes wood. */
    static final float GRAZE = 0.2f;

    /** The key an object's mod data keeps the rims round its holes under, and what the list starts with: the numbers a rim is kept as. */
    private static final String RIMS_KEY = "cameraunlockShotRims", RIMS = "11:";
    /**
     * A rim is where Impacts draws it, the way its surface faces there, what the surface is made of,
     * how big the round was, and the way the surface's grain runs: all of a hole the picture draws.
     */
    static final int RIM_FLOATS = 11;
    /** The most rims an object keeps, the oldest given up past them: a fence shot to pieces by a few blasts of a shotgun's nine pellets. */
    static final int MOST_RIMS = 128;

    /** What each object loaded keeps, for the builds to read off the logic thread. */
    private static final Map<IsoObject, float[]> kept = Collections.synchronizedMap(new WeakHashMap<>());
    /**
     * Whether anything has been kept since the save was looked at. In most saves nothing is, and
     * the builds and the sums, which ask of every object they meet, take no lock to be told so.
     */
    private static volatile boolean any;

    /** An object's holes and marks, FLOATS numbers each, or null for none. Any thread. */
    static float[] of(IsoObject object) {
        return any ? kept.get(object) : null;
    }

    /**
     * The holes a door's casing on the north or west edge of square (x, y, z) is cut with: those of the doorway's wall it
     * stands on, where ImpactFinder keeps the shots that strike it (WorldScene.wallOn), and those of the walls on the same
     * edge of the squares before and after it within OVERHANG, which its posts stand over past the square's ends, moved
     * along by a square, but for those the doorway's wall keeps too, reaching over the join (add). Null where there are none.
     */
    static float[] ofCasingOn(IsoCell cell, int x, int y, int z, boolean north) {
        float[] own = ofWallOn(cell.getGridSquare(x, y, z), north);
        float[] before = ofWallOn(cell.getGridSquare(north ? x - 1 : x, north ? y : y - 1, z), north);
        float[] after = ofWallOn(cell.getGridSquare(north ? x + 1 : x, north ? y : y + 1, z), north);
        if (before == null && after == null) {
            return own;
        }
        float[] all = new float[(own == null ? 0 : own.length) + (before == null ? 0 : before.length) + (after == null ? 0 : after.length)];
        int count = 0;
        if (own != null) {
            System.arraycopy(own, 0, all, 0, own.length);
            count = own.length;
        }
        count = overhung(before, -1f, own, all, count);
        count = overhung(after, 1f, own, all, count);
        return count == 0 ? null : Arrays.copyOf(all, count);
    }

    /** How far past either end of its square a casing's post may stand, as a share of the square: a post's width and more. */
    static final float OVERHANG = 0.25f;

    private static float[] ofWallOn(IsoGridSquare square, boolean north) {
        IsoObject wall = square == null ? null : WorldScene.wallOn(square, north);
        return wall == null ? null : of(wall);
    }

    /**
     * Copies into all from count the entries of a wall a square before (-1) or after (1) whose middles are within OVERHANG of the
     * near end, moved along by that square, but for those own already keeps there; the count after.
     */
    private static int overhung(float[] entries, float by, float[] own, float[] all, int count) {
        if (entries == null) {
            return count;
        }
        for (int at = 0; at < entries.length; at += FLOATS) {
            float along = entries[at + ALONG] + by;
            if (along > -OVERHANG && along < 1f + OVERHANG && !holds(own, entries, at, along)) {
                System.arraycopy(entries, at, all, count, FLOATS);
                all[count + ALONG] = along;
                count += FLOATS;
            }
        }
        return count;
    }

    /** How near two places along an edge are to be the one: a place moved along by a square and back is off by its rounding. */
    private static final float SAME_ALONG = 1e-4f;

    /** Whether entries keep the entry of others at place at, at along: the one hole two walls keep where it reaches over the join of the two (add). */
    private static boolean holds(float[] entries, float[] others, int at, float along) {
        if (entries == null) {
            return false;
        }
        for (int o = 0; o < entries.length; o += FLOATS) {
            if (entries[o + SEED] == others[at + SEED] && entries[o + SIDE] == others[at + SIDE] && entries[o + NORTH] == others[at + NORTH]
                    && Math.abs(entries[o + ALONG] - along) < SAME_ALONG && entries[o + UP] == others[at + UP]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether an object is one a blast blows holes in: a wooden door, a wooden fence on an edge,
     * tall or low, the map's or one a player built, whole, broken or burnt, or a wall of
     * plasterboard, which the pellets go on through (Penetration), or of boards, siding among them,
     * which stops them. A rail fence strung with wire is mostly the wire's gaps, and is not one,
     * nor a fence the picture builds from a model of its own (ModelFences), whose pickets are not
     * cut. Any thread.
     */
    static boolean blastable(IsoObject object) {
        IsoSprite sprite = object.getSprite();
        if (sprite == null) {
            return false;
        }
        PropertyContainer properties = sprite.getProperties();
        if (DoorGlass.isDoor(object)) {
            if (properties.has("GarageDoor")) {
                return false;
            }
            int made = Impacts.ofMaterialType(properties.get("MaterialType"));
            return made == Impacts.WOOD || made == Impacts.NONE && "Wood".equals(Penetration.doorMaterialBySound(properties.get("DoorSound")));
        }
        String name = sprite.getName(), material = properties.get("Material");
        String made = material != null ? material : properties.get("MaterialType");
        boolean onEdge = Edges.west(properties, sprite.getType()) || Edges.north(properties, sprite.getType());
        // A corner piece is the wall of both its edges, as WorldScene.wallOn takes it.
        if ((onEdge || properties.has(IsoFlagType.WallNW)) && WorldScene.isWall(properties, sprite.getType())) {
            String wall = Penetration.madeOf(object);
            if (PLASTER.equals(wall) || WOOD.equals(wall)) {
                return true;
            }
        }
        // The tall board fence (fencing_01_8) is flagged a wall by the game and names its wood only in Material.
        return fence(name) && made != null && made.startsWith("Wood") && !"MetalWire".equals(properties.get("Material2"))
                && ModelFences.of(name) == null && onEdge;
    }

    private static boolean fence(String name) {
        return name != null && (name.startsWith("fencing_") || name.startsWith("carpentry_02_") || name.startsWith("crafted_0"));
    }

    /** What the game names plasterboard, and a wall of boards (not Wood_Solid, logs). */
    private static final String PLASTER = "Plaster", WOOD = "Wood";

    /**
     * Whether a round that goes through an object leaves a hole the picture cuts: a door the picture
     * builds a leaf for, any but a garage door or a gate of bars, which a round goes on through
     * between them, a wooden fence (blastable), or a wall or fence of something a round goes
     * through that is not glass (Penetration.cost). Logic thread.
     */
    static boolean holedByRounds(IsoObject object) {
        IsoSprite sprite = object.getSprite();
        if (sprite == null) {
            return false;
        }
        if (DoorGlass.isDoor(object)) {
            return !sprite.getProperties().has("GarageDoor") && !Penetration.BARS.equals(Penetration.madeOf(object));
        }
        String made = Penetration.madeOf(object);
        return blastable(object) || made != null && !made.startsWith("Glass") && Penetration.cost(made) != Penetration.STOPS;
    }

    /** The tops of the board fences the picture has built, low ones and those with holes, in levels over their ground, by their sprite's picture. */
    private static final Map<Texture, Float> tops = new java.util.concurrent.ConcurrentHashMap<>();

    /** Keeps how high the board fence the picture built from a sprite's picture stands, in levels. Build threads. */
    static void measured(Texture texture, float top) {
        tops.put(texture, top);
    }

    /**
     * The low board fence on a square's north or west edge, the game's see-through kind a shot goes on
     * over, which a shot crossing it under its top holes as it goes through; null where there is none.
     */
    static IsoObject lowFenceOn(IsoGridSquare square, boolean north) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object.getSprite();
            if (sprite == null || DoorGlass.isDoor(object)) {
                continue;
            }
            PropertyContainer properties = sprite.getProperties();
            boolean sheet = north ? properties.has(IsoFlagType.WallNTrans) && !properties.has(IsoFlagType.WallN)
                    : properties.has(IsoFlagType.WallWTrans) && !properties.has(IsoFlagType.WallW);
            if (sheet && blastable(object)) {
                return object;
            }
        }
        return null;
    }

    /** How high the picture built a board fence, in levels over its ground, or NaN where it has not built its sprite. */
    static float topOf(IsoObject fence) {
        Float top = tops.get(fence.getSprite().getTextureForCurrentFrame(fence.getDir()));
        return top == null ? Float.NaN : top;
    }

    /**
     * Called as the game has loaded a chunk's squares, on its logic thread: the holes and marks of its things, as saved.
     * A chunk beside it that a holed wall on its edge may run on into is built again: what falls of a run of wall is
     * worked out along all of it (fallen), and was worked out without this chunk's part.
     */
    static void loaded(IsoChunk chunk) {
        int last = WorldScene.CHUNK_TILES - 1;
        boolean west = false, east = false, north = false, south = false;
        for (int z = chunk.getMinLevel(); z <= chunk.getMaxLevel(); z++) {
            for (int i = 0; i < WorldScene.CHUNK_TILES * WorldScene.CHUNK_TILES; i++) {
                IsoGridSquare square = chunk.getGridSquare(i % WorldScene.CHUNK_TILES, i / WorldScene.CHUNK_TILES, z);
                if (square == null) {
                    continue;
                }
                PZArrayList<IsoObject> objects = square.getObjects();
                for (int k = 0; k < objects.size(); k++) {
                    IsoObject object = objects.get(k);
                    if (object.hasModData() && object.getModData().rawget(KEY) instanceof String saved) {
                        keepSaved(object, saved, square);
                        west |= i % WorldScene.CHUNK_TILES == 0;
                        east |= i % WorldScene.CHUNK_TILES == last;
                        north |= i / WorldScene.CHUNK_TILES == 0;
                        south |= i / WorldScene.CHUNK_TILES == last;
                    } else if (any) {
                        // The game takes the walls and fences of a chunk it let go of for those of the next it loads.
                        kept.remove(object);
                    }
                }
            }
        }
        if (west) {
            ChunkMarks.tell(chunk.wx - 1, chunk.wy);
        }
        if (east) {
            ChunkMarks.tell(chunk.wx + 1, chunk.wy);
        }
        if (north) {
            ChunkMarks.tell(chunk.wx, chunk.wy - 1);
        }
        if (south) {
            ChunkMarks.tell(chunk.wx, chunk.wy + 1);
        }
    }

    /**
     * Keeps what a save holds on an object again. A list that cannot be read is said once in the
     * log and the object keeps nothing, so one bad entry does not stop its chunk loading.
     */
    private static void keepSaved(IsoObject object, String saved, IsoGridSquare square) {
        float[] entries;
        try {
            entries = parse(saved);
        } catch (IllegalArgumentException malformed) {
            if (!malformedSaid) {
                malformedSaid = true;
                Log.error(String.format(Locale.ROOT, "the holes saved on %s at (%d, %d, %d) are left undrawn, and so are any others saved the same way",
                        object.getSprite() == null ? "a thing" : object.getSprite().getName(), square.getX(), square.getY(), square.getZ()), malformed);
            }
            kept.remove(object);
            return;
        }
        any = true;
        kept.put(object, entries);
    }

    /** Whether a save's list of holes that could not be read has been said this session. */
    private static boolean malformedSaid;

    /** What a save holds, FLOATS numbers a hole; one saved before the slant was kept, OLD_FLOATS a hole, is read as cut straight across. */
    static float[] parse(String saved) {
        boolean slanted = saved.startsWith(SLANTED);
        int each = slanted ? FLOATS : OLD_FLOATS;
        String list = slanted ? saved.substring(SLANTED.length()) : saved;
        String[] words = list.isEmpty() ? new String[0] : list.split(",");
        if (words.length % each != 0) {
            throw new IllegalArgumentException("blast holes saved as " + words.length + " numbers, not " + each + " a hole: " + saved);
        }
        if (words.length / each > MOST_HOLES + MOST_MARKS + MOST_ROUNDS) {
            throw new IllegalArgumentException("blast holes saved as " + words.length / each + " holes, more than an object keeps");
        }
        float[] entries = new float[words.length / each * FLOATS];
        for (int i = 0; i < words.length; i++) {
            float value = Float.parseFloat(words[i]);
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("blast holes saved with " + words[i] + " among their numbers: " + saved);
            }
            entries[i / each * FLOATS + i % each] = value;
        }
        for (int at = 0; at < entries.length; at += FLOATS) {
            float side = entries[at + SIDE], north = entries[at + NORTH], radius = entries[at + RADIUS];
            boolean straight = entries[at + SLANT_ALONG] == 0f && entries[at + SLANT_UP] == 0f;
            if (side != (int) side || Math.abs(side) > ROUND || (north != 0f && north != 1f) || !(radius > 0f && radius <= 1f)
                    || !straight && (Math.abs(side) != ROUND || Math.abs(entries[at + SLANT_ALONG]) > 1f / LEAST_ACROSS
                    || Math.abs(entries[at + SLANT_UP]) > 1f / LEAST_ACROSS)) {
                throw new IllegalArgumentException("blast holes saved with a hole no shot makes: " + saved);
            }
        }
        return entries;
    }

    static String write(float[] entries) {
        StringBuilder out = new StringBuilder(SLANTED);
        for (int i = 0; i < entries.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(entries[i]);
        }
        return out.toString();
    }

    /**
     * Keeps a blast's hole (side 0), a mark (1 or -1) or a round's hole (twice that) on an
     * object, past the most it keeps of that kind by letting go of the oldest, saves it in the
     * object's mod data and has the picture build the object again. A hole that reaches over
     * the end of its square into the next wall or fence along (reachedOver), one a hole of its
     * kind goes through, is kept by that one too, moved along by the square, so that it is cut
     * out of both and what falls of each is worked out with it (fallen). Logic thread.
     */
    static void add(IsoObject object, boolean north, float along, float up, float radius, int seed, int side) {
        add(object, north, along, up, radius, seed, side, 0f, 0f);
    }

    /** The same, for a round's hole cut along the round's line: slantAlong and slantUp as SLANT_ALONG and SLANT_UP keep them (slant). */
    static void add(IsoObject object, boolean north, float along, float up, float radius, int seed, int side, float slantAlong, float slantUp) {
        keep(object, north, along, up, radius, seed, side, slantAlong, slantUp);
        if (DevBuild.ON) {
            IsoGridSquare square = object.getSquare();
            Log.info(String.format(Locale.ROOT, "%s at %.3f along and %.3f levels up, radius %.3f, on the %s on the %s edge of (%d, %d, %d)",
                    side == 0 ? "a blast blew a hole" : Math.abs(side) == ROUND ? "a round went through" : "a pellet marked", along, up, radius,
                    DoorGlass.isDoor(object) ? "door" : object.getSprite().getName(), north ? "north" : "west", square.getX(), square.getY(), square.getZ()));
        }
        if (isHole(side)) {
            float reach = reachOf(radius, side, slantAlong);
            for (int by = -1; by <= 1; by += 2) {
                IsoObject next = reachedOver(object, north, along, reach, by);
                if (next != null && (side == 0 ? blastable(next) : holedByRounds(next))) {
                    keep(next, north, along - by, up, radius, seed, side, slantAlong, slantUp);
                    if (DevBuild.ON) {
                        IsoGridSquare on = next.getSquare();
                        Log.info(String.format(Locale.ROOT, "the hole reaches over the join into the %s on the %s edge of (%d, %d, %d), which keeps it too at %.3f along",
                                next.getSprite().getName(), north ? "north" : "west", on.getX(), on.getY(), on.getZ(), along - by));
                    }
                }
            }
        }
        tellRun(object, north);
    }

    private static void keep(IsoObject object, boolean north, float along, float up, float radius, int seed, int side, float slantAlong, float slantUp) {
        float[] was = kept.get(object);
        float[] now = with(was == null ? new float[0] : was, north, along, up, radius, seed, side, slantAlong, slantUp);
        any = true;
        kept.put(object, now);
        KahluaTable data = object.getModData();
        data.rawset(KEY, write(now));
        ChunkMarks.changed(object);
    }

    /**
     * How far along its edge from its middle a hole's outline may reach, in tiles: a blast's at its most ragged; a round's
     * way out, wider and more ragged than its way in, and moved along by the round's slant across as far from the edge as
     * the picture cuts a wall or fence (WallBuilder.FENCE_REACH).
     */
    static float reachOf(float radius, int side, float slantAlong) {
        return side == 0 ? radius * (1f + HOLE_RAGGED) : radius * EXIT_WIDER * (1f + EXIT_RAGGED) + Math.abs(slantAlong) * WallBuilder.FENCE_REACH;
    }

    /**
     * The next wall or low fence along an object's north or west edge, before it (by -1) or after it (1), where a hole on
     * the object at along that reaches as far as reach either way goes over the end of the object's square into it: the
     * next square's wall of that edge after the edge's wall (WorldScene.wallOn), its low fence after a low fence
     * (lowFenceOn). Null where the hole does not reach so far, the object is a door, or there is no such thing there.
     */
    static IsoObject reachedOver(IsoObject object, boolean north, float along, float reach, int by) {
        IsoGridSquare square = object.getSquare();
        if ((by < 0 ? along - reach >= 0f : along + reach <= 1f) || square == null || DoorGlass.isDoor(object)) {
            return null;
        }
        IsoGridSquare next = square.getAdjacentSquare(north ? by < 0 ? IsoDirections.W : IsoDirections.E : by < 0 ? IsoDirections.N : IsoDirections.S);
        if (next == null) {
            return null;
        }
        if (WorldScene.wallOn(square, north) == object) {
            return WorldScene.wallOn(next, north);
        }
        return lowFenceOn(square, north) == object ? lowFenceOn(next, north) : null;
    }

    /**
     * Has the chunks a run of holed wall the object is in reaches into besides its own built again: what falls of each
     * of the run is worked out along all of it (fallen), and its own chunk the object's new hole has built again. Logic thread.
     */
    private static void tellRun(IsoObject object, boolean north) {
        IsoGridSquare square = object.getSquare();
        if (square == null || DoorGlass.isDoor(object) || boards(object) || WorldScene.wallOn(square, north) != object) {
            return;
        }
        int wx = Math.floorDiv(square.getX(), WorldScene.CHUNK_TILES), wy = Math.floorDiv(square.getY(), WorldScene.CHUNK_TILES);
        for (IsoObject piece : runOf(object, square, north)) {
            IsoGridSquare on = piece.getSquare();
            int pieceWx = Math.floorDiv(on.getX(), WorldScene.CHUNK_TILES), pieceWy = Math.floorDiv(on.getY(), WorldScene.CHUNK_TILES);
            if (pieceWx != wx || pieceWy != wy) {
                ChunkMarks.tell(pieceWx, pieceWy);
            }
        }
    }

    /** The entries with one more, past the most of its kind letting go of one the others cover, or else the oldest. */
    static float[] with(float[] entries, boolean north, float along, float up, float radius, int seed, int side) {
        return with(entries, north, along, up, radius, seed, side, 0f, 0f);
    }

    static float[] with(float[] entries, boolean north, float along, float up, float radius, int seed, int side, float slantAlong, float slantUp) {
        int count = entries.length / FLOATS, same = 0, first = -1;
        for (int i = 0; i < count; i++) {
            if (Math.abs((int) entries[i * FLOATS + SIDE]) == Math.abs(side)) {
                same++;
                if (first < 0) {
                    first = i;
                }
            }
        }
        if (isHole(side) && same >= (side == 0 ? MOST_HOLES : MOST_ROUNDS)) {
            for (int i = 0; i < count; i++) {
                if (Math.abs((int) entries[i * FLOATS + SIDE]) == Math.abs(side) && coveredByOthers(entries, i)) {
                    first = i;
                    break;
                }
            }
        }
        float[] base = entries;
        if (same >= (side == 0 ? MOST_HOLES : Math.abs(side) == ROUND ? MOST_ROUNDS : MOST_MARKS)) {
            base = new float[entries.length - FLOATS];
            System.arraycopy(entries, 0, base, 0, first * FLOATS);
            System.arraycopy(entries, (first + 1) * FLOATS, base, first * FLOATS, entries.length - (first + 1) * FLOATS);
        }
        float[] out = Arrays.copyOf(base, base.length + FLOATS);
        int at = base.length;
        out[at + ALONG] = along;
        out[at + UP] = up;
        out[at + RADIUS] = radius;
        out[at + SEED] = seed;
        out[at + SIDE] = side;
        out[at + NORTH] = north ? 1f : 0f;
        out[at + SLANT_ALONG] = slantAlong;
        out[at + SLANT_UP] = slantUp;
        return out;
    }

    /** How far in from its outline a hole is looked at to tell whether the others cover it. */
    private static final float COVER_IN = 0.8f;

    /** Whether the other holes of its edge together cover the hole at place i: its middle and its outline drawn in by COVER_IN. */
    static boolean coveredByOthers(float[] entries, int i) {
        float[] own = outlineOf(entries, i * FLOATS);
        float middleA = entries[i * FLOATS + ALONG], middleU = entries[i * FLOATS + UP] * ViewCamera.LEVEL_HEIGHT;
        float north = entries[i * FLOATS + NORTH];
        for (int k = -1; k < own.length / 2; k++) {
            float a = k < 0 ? middleA : middleA + (own[2 * k] - middleA) * COVER_IN;
            float u = k < 0 ? middleU : middleU + (own[2 * k + 1] - middleU) * COVER_IN;
            boolean in = false;
            for (int other = 0; other < entries.length / FLOATS && !in; other++) {
                if (other == i || entries[other * FLOATS + NORTH] != north) {
                    continue;
                }
                float[] outline = outlineOf(entries, other * FLOATS);
                in = outline != null && MeshCut.inside(outline, a, u);
            }
            if (!in) {
                return false;
            }
        }
        return true;
    }

    /**
     * The outline a hole is cut by, along and up in tiles, a round's where it goes in; null for a
     * mark. It is the outline into cuts where the plane is a fence's or a wall's own.
     */
    static float[] outlineOf(float[] entries, int at) {
        float side = entries[at + SIDE];
        if (!isHole(side)) {
            return null;
        }
        float along = entries[at + ALONG], up = entries[at + UP] * ViewCamera.LEVEL_HEIGHT, r = entries[at + RADIUS];
        int seed = (int) entries[at + SEED];
        return side == 0f ? MeshCut.outline(along, up, r, r * HOLE_TALL, HOLE_RAGGED, HOLE_POINTS, seed)
                : MeshCut.outline(along, up, r, r, ROUND_RAGGED, ROUND_POINTS, seed);
    }

    /** How many squares along its line either way a run of holed wall is followed (fallen). */
    private static final int MOST_RUN = 32;

    /**
     * What goes of an object besides the holes on its north or west edge (Breakage): of a door's leaf, what its
     * holes cut from the hinges it hangs from (as the picture measured the leaf: nothing until then); of a fence of
     * boards, what they leave held at neither end of its boards (by its top as the picture built it, a level until
     * it has); and of a wall, what they cut from its floor, its ceiling and the frames of the doors and windows
     * through it, worked out along the run of holed wall it stands in (runOf), so that a piece shot free across the
     * join of two squares falls out of both. Nothing for an object with no hole on that edge. Any thread.
     */
    static Breakage.Pieces fallen(IsoObject object, boolean north) {
        float[] own = of(object);
        if (own == null || !holedOn(own, north)) {
            return Breakage.Pieces.NONE;
        }
        if (DoorGlass.isDoor(object)) {
            Doors.Shape shape = object.getSprite() == null ? null : DoorGlass.shapeOf(object);
            return shape == null ? Breakage.Pieces.NONE : Breakage.of(own, north, leafPanel(shape));
        }
        IsoGridSquare square = object.getSquare();
        if (square == null) {
            return Breakage.Pieces.NONE;
        }
        int startsAt = north ? square.getX() : square.getY();
        if (boards(object)) {
            float top = topOf(object);
            return Breakage.of(own, north, new Breakage.Panel(Breakage.BOARDS, 0f, 1f, 0f, Float.isNaN(top) ? 1f : top, startsAt,
                    Breakage.HELD_ALL, Breakage.NO_FRAMES));
        }
        if (WorldScene.wallOn(square, north) != object) {
            // A sheet that is not its edge's wall, a fence of something a round goes through, is held all round on its own.
            return Breakage.of(own, north, new Breakage.Panel(Breakage.SHEET, 0f, 1f, 0f, 1f, startsAt, Breakage.HELD_ALL, Breakage.NO_FRAMES));
        }
        ArrayList<IsoObject> run = runOf(object, square, north);
        int index = run.indexOf(object);
        float[][] entries = new float[run.size()][];
        float[] frames = Breakage.NO_FRAMES;
        for (int k = 0; k < run.size(); k++) {
            IsoObject piece = run.get(k);
            entries[k] = piece == object ? own : of(piece);
            Texture texture = piece.getSprite().getTextureForCurrentFrame(piece.getDir());
            float[] opening = WallBuilder.openingIn(texture, north);
            if (opening == null) {
                awaitOpening(texture, north, square);
            } else if (opening.length > 0) {
                frames = Arrays.copyOf(frames, frames.length + 4);
                frames[frames.length - 4] = opening[0] + k;
                frames[frames.length - 3] = opening[1] + k;
                frames[frames.length - 2] = opening[2];
                frames[frames.length - 1] = opening[3];
            }
        }
        Breakage.Panel panel = new Breakage.Panel(Breakage.SHEET, 0f, run.size(), 0f, 1f, startsAt - index, Breakage.HELD_ALL, frames);
        return Breakage.of(entries, north, panel)[index];
    }

    /**
     * The run of holed wall a wall with holes on its square's north or west edge stands in: the walls of that edge on
     * the squares along its line either way (WorldScene.wallOn, which keeps their holes), up to the first with no hole
     * there, at most MOST_RUN each way, in order along the edge with the wall itself among them. What it meets at
     * either end, the next wall whole, a corner or the open end of the wall, holds it there.
     */
    private static ArrayList<IsoObject> runOf(IsoObject wall, IsoGridSquare square, boolean north) {
        ArrayList<IsoObject> run = new ArrayList<>();
        IsoGridSquare at = square;
        for (int step = 0; step < MOST_RUN; step++) {
            at = at.getAdjacentSquare(north ? IsoDirections.W : IsoDirections.N);
            IsoObject piece = holedWallOn(at, north);
            if (piece == null) {
                break;
            }
            run.add(0, piece);
        }
        run.add(wall);
        at = square;
        for (int step = 0; step < MOST_RUN; step++) {
            at = at.getAdjacentSquare(north ? IsoDirections.E : IsoDirections.S);
            IsoObject piece = holedWallOn(at, north);
            if (piece == null) {
                break;
            }
            run.add(piece);
        }
        return run;
    }

    /** The wall on a square's north or west edge with a hole there, which a run of holed wall goes on through; null for none. */
    private static IsoObject holedWallOn(IsoGridSquare square, boolean north) {
        IsoObject wall = square == null ? null : WorldScene.wallOn(square, north);
        float[] entries = wall == null ? null : of(wall);
        return entries != null && holedOn(entries, north) && !DoorGlass.isDoor(wall) && !boards(wall) ? wall : null;
    }

    /**
     * Of each wall's sprite whose opening no build had read for an edge when what goes of a run of wall it stands in
     * was worked out, the chunks the run was built in, by key: built again once a build has read it (openingRead), when
     * the frame round the opening holds what it holds. Only builds wait: a shot asks again.
     */
    private static final ConcurrentHashMap<Texture, Set<Long>> awaiting = new ConcurrentHashMap<>();

    private static void awaitOpening(Texture texture, boolean north, IsoGridSquare square) {
        if (!BuildThreads.here()) {
            return;
        }
        long chunk = (long) Math.floorDiv(square.getX(), WorldScene.CHUNK_TILES) << 32 | Math.floorDiv(square.getY(), WorldScene.CHUNK_TILES) & 0xffffffffL;
        awaiting.computeIfAbsent(texture, any -> ConcurrentHashMap.newKeySet()).add(chunk);
        // A build on another thread may have read it since this one looked, and found nothing waiting.
        if (WallBuilder.openingIn(texture, north) != null) {
            openingRead(texture);
        }
    }

    /** Called as a build has read the opening through a wall's sprite: the chunks built while it was not are built again. Any thread. */
    static void openingRead(Texture texture) {
        Set<Long> chunks = awaiting.remove(texture);
        if (chunks != null) {
            for (long chunk : chunks) {
                BuildThreads.tell((int) (chunk >> 32), (int) chunk);
            }
        }
    }

    /** A door's leaf as Breakage takes it: one sheet, measured hanging shut, held where it hangs from its hinges, at the end away from its handle. */
    static Breakage.Panel leafPanel(Doors.Shape door) {
        Windows.Shape leaf = door.leaf;
        float from = Math.min(leaf.start, leaf.end), hinged = door.handleAtEnd ? leaf.start : leaf.end;
        return new Breakage.Panel(Breakage.SHEET, from, Math.max(leaf.start, leaf.end), Math.min(leaf.foot, leaf.head), Math.max(leaf.foot, leaf.head), 0,
                hinged == from ? Breakage.HELD_START : Breakage.HELD_END, Breakage.NO_FRAMES);
    }

    /** Whether an object is a fence of boards stood side by side: a wooden fence a blast holes, and not a door or a wall. Any thread. */
    static boolean boards(IsoObject object) {
        IsoSprite sprite = object.getSprite();
        return sprite != null && !DoorGlass.isDoor(object) && (fence(sprite.getName()) || !WorldScene.isWall(sprite.getProperties(), sprite.getType()))
                && blastable(object);
    }

    /** How far, in tiles, a broken piece's corners go out past its squares, give or take PIECE_SKEW. */
    private static final float PIECE_OVER = 0.002f, PIECE_SKEW = 0.001f;

    /** A new hole's or mark's SEED. */
    static int newSeed() {
        return seeds.nextInt(1 << 20);
    }

    /** Whether (along, up) on an object's north or west edge is far enough into the opening its holes cut for a shot to go through (opening). Logic thread. */
    static boolean through(IsoObject object, boolean north, float along, float up) {
        float[] entries = of(object);
        return entries != null && opening(entries, north, along, up, fallen(object, north));
    }

    /**
     * Whether (along, up), up in levels, is in the opening the holes on the north or west edge and the pieces fallen from
     * between them cut in the picture (HoleHop.cut), far enough in for a shot to go through: in a fallen piece, or inside a
     * hole's outline with the ring GRAZE of the widest such hole's radius round it in the opening too. A round grazing the
     * opening's edge strikes wood, and inside an opening blown by many holes the rim of each, which the others cut away, stops nothing.
     */
    static boolean opening(float[] entries, boolean north, float along, float up, Breakage.Pieces fallen) {
        if (fallen.inside(along, up)) {
            return true;
        }
        float tiles = up * ViewCamera.LEVEL_HEIGHT, widest = 0f;
        for (int at = 0; at < entries.length; at += FLOATS) {
            if (inOutline(entries, at, north, along, tiles)) {
                widest = Math.max(widest, entries[at + RADIUS]);
            }
        }
        if (widest == 0f) {
            return false;
        }
        float graze = widest * GRAZE;
        for (int k = 0; k < RING_COS.length; k++) {
            float a = along + graze * RING_COS[k], t = tiles + graze * RING_SIN[k];
            if (!fallen.inside(a, t / ViewCamera.LEVEL_HEIGHT) && !inAnOutline(entries, north, a, t)) {
                return false;
            }
        }
        return true;
    }

    /** The ways round a point opening looks GRAZE out from it. */
    private static final float[] RING_COS = new float[8], RING_SIN = new float[8];

    static {
        for (int k = 0; k < RING_COS.length; k++) {
            RING_COS[k] = (float) Math.cos(2 * Math.PI * k / RING_COS.length);
            RING_SIN[k] = (float) Math.sin(2 * Math.PI * k / RING_COS.length);
        }
    }

    private static boolean inAnOutline(float[] entries, boolean north, float along, float tiles) {
        for (int at = 0; at < entries.length; at += FLOATS) {
            if (inOutline(entries, at, north, along, tiles)) {
                return true;
            }
        }
        return false;
    }

    /** Whether (along, tiles up) is inside the outline the hole at place at is cut by (outlineOf), where that hole is on the north or west edge. */
    private static boolean inOutline(float[] entries, int at, boolean north, float along, float tiles) {
        if (!isHole(entries[at + SIDE]) || (entries[at + NORTH] != 0f) != north) {
            return false;
        }
        // No outline reaches further from its middle than a blast's at its most ragged, and as tall.
        float reach = entries[at + RADIUS] * (1f + HOLE_RAGGED) * HOLE_TALL;
        if (Math.abs(along - entries[at + ALONG]) > reach || Math.abs(tiles - entries[at + UP] * ViewCamera.LEVEL_HEIGHT) > reach) {
            return false;
        }
        return MeshCut.inside(outlineOf(entries, at), along, tiles);
    }

    /** The outlines the holes on the north or west edge are cut by in the picture (outlineOf), along and up in tiles. */
    static ArrayList<float[]> outlinesOn(float[] entries, boolean north) {
        ArrayList<float[]> outlines = new ArrayList<>();
        for (int at = 0; at < entries.length; at += FLOATS) {
            if ((entries[at + NORTH] != 0f) == north) {
                float[] outline = outlineOf(entries, at);
                if (outline != null) {
                    outlines.add(outline);
                }
            }
        }
        return outlines;
    }

    /** Whether an object has a hole on its north or its west edge: a door or a wall with one is seen through, as a door with glass is (RoomMap). Logic thread. */
    static boolean holed(IsoObject object, boolean north) {
        float[] entries = of(object);
        return entries != null && holedOn(entries, north);
    }

    private static boolean holedOn(float[] entries, boolean north) {
        for (int at = 0; at < entries.length; at += FLOATS) {
            if (isHole(entries[at + SIDE]) && (entries[at + NORTH] != 0f) == north) {
                return true;
            }
        }
        return false;
    }

    /**
     * How far a round's line goes along or up an edge for each tile it goes across it, north to south
     * or west to east (SLANT_ALONG, SLANT_UP): forward the share of its way along or up, in tiles, and
     * forwardAcross the share across. A line that crosses at less than LEAST_ACROSS is taken as crossing at that.
     */
    static float slant(float forward, float forwardAcross) {
        return forward / Math.copySign(Math.max(Math.abs(forwardAcross), LEAST_ACROSS), forwardAcross);
    }

    /** Whether an entry of this SIDE is a hole, a blast's or a round's, and not a mark. */
    private static boolean isHole(float side) {
        return side == 0f || Math.abs(side) == ROUND;
    }

    /**
     * Puts an object's holes and marks on its north or west edge into a cut: each at s = sFrom +
     * sBy * along on the cut's plane and t = tFrom + tBy * up, in tiles, and a mark's side times
     * sideBy, as is the way across the edge a round's hole slants along; and what went with them,
     * laid the same way (fallen).
     */
    static void into(MeshCut cut, float[] entries, Breakage.Pieces fallen, boolean north, float sFrom, float sBy, float tFrom, float tBy, int sideBy) {
        float[][] pieces = fallen.rectangles;
        for (int p = 0; p < pieces.length; p++) {
            float[] piece = pieces[p], outline = new float[8];
            float middleS = 0f, middleT = 0f;
            for (int k = 0; k < 4; k++) {
                // Laid mirrored along, the corners go round the other way: put back anticlockwise.
                int from = sBy < 0f ? 3 - k : k;
                outline[2 * k] = sFrom + sBy * piece[2 * from];
                outline[2 * k + 1] = tFrom + tBy * piece[2 * from + 1];
                middleS += outline[2 * k] / 4f;
                middleT += outline[2 * k + 1] / 4f;
            }
            // Pieces meet edge to edge, and stand on the panel's edges: an outline along a line a face's
            // edge lies on cannot be cut. Each corner goes out a little, by its own amount, so no two lines meet exactly.
            for (int k = 0; k < 4; k++) {
                outline[2 * k] += Math.signum(outline[2 * k] - middleS) * (PIECE_OVER + PIECE_SKEW * MeshCut.noise(p, 2 * k));
                outline[2 * k + 1] += Math.signum(outline[2 * k + 1] - middleT) * (PIECE_OVER + PIECE_SKEW * MeshCut.noise(p, 2 * k + 1));
            }
            cut.hole(outline, middleS, middleT);
        }
        for (int at = 0; at < entries.length; at += FLOATS) {
            if ((entries[at + NORTH] != 0f) != north) {
                continue;
            }
            float s = sFrom + sBy * entries[at + ALONG], t = tFrom + tBy * entries[at + UP];
            float r = entries[at + RADIUS];
            int seed = (int) entries[at + SEED];
            int side = (int) entries[at + SIDE];
            float rs = r * Math.abs(sBy), rt = r * tBy / ViewCamera.LEVEL_HEIGHT;
            if (side == 0) {
                cut.hole(MeshCut.outline(s, t, rs, rt * HOLE_TALL, HOLE_RAGGED, HOLE_POINTS, seed), s, t);
            } else if (Math.abs(side) == ROUND) {
                cut.hole(MeshCut.outline(s, t, rs, rt, ROUND_RAGGED, ROUND_POINTS, seed),
                        MeshCut.outline(s, t, rs * EXIT_WIDER, rt * EXIT_WIDER * EXIT_TALL, EXIT_RAGGED, ROUND_POINTS, seed), Integer.signum(side) * sideBy, s, t,
                        sBy * entries[at + SLANT_ALONG] * sideBy, tBy / ViewCamera.LEVEL_HEIGHT * entries[at + SLANT_UP] * sideBy);
            } else {
                cut.mark(s, t, MeshCut.outline(s, t, rs, rt, MARK_RAGGED, MARK_POINTS, seed), side * sideBy);
            }
        }
    }

    /** Forgets every object's: the save is left. */
    static void forget() {
        kept.clear();
        awaiting.clear();
        any = false;
    }
}
