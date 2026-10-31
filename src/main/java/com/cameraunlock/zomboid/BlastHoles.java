package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import se.krka.kahlua.vm.KahluaTable;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoChunk;
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
 * Each is six numbers: how far along the edge it is on, in tiles from its square's corner, and
 * how high, in levels over its floor (as DoorGlass measures a door's lights); its radius in tiles,
 * a round's where it went in; the seed its ragged outline is drawn from; what it is and the side
 * of the edge it was struck from, 1 or -1 (SIDE: 0 a blast's hole, the side for a mark, twice the
 * side for a round's hole); and 1 where the edge is the square's north one, 0 its west: a fence's
 * corner length stands on both. A door's are measured on the leaf as it hangs
 * shut, and laid on the leaf as it stands swung open (OpeningBuilder.addBuiltDoor). A fence's
 * marks are the decals the picture lays over the world (Impacts), as a wall's are.
 */
final class BlastHoles {
    private BlastHoles() {}

    /** The key an object's mod data keeps its holes and marks under. */
    private static final String KEY = "cameraunlockBlastHoles";
    static final int FLOATS = 6, ALONG = 0, UP = 1, RADIUS = 2, SEED = 3, SIDE = 4, NORTH = 5;
    /** Where each hole's and mark's ragged outline is drawn from: below 2^24, so a float in the save holds it whole. */
    private static final Random seeds = new Random();
    /** The most blasts' holes, marks and rounds' holes an object keeps: the oldest of each go first past them. */
    static final int MOST_HOLES = 8, MOST_MARKS = 48, MOST_ROUNDS = 24;
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
    /** How far into a hole's radius a later shot has to cross to go through it: the outline is ragged, and a round grazing its edge strikes wood. */
    private static final float THROUGH = 0.8f;

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
     * Whether an object is one a blast blows holes in: a wooden door, or a wooden fence on an edge,
     * tall or low, the map's or one a player built, whole, broken or burnt. A rail fence strung with
     * wire is mostly the wire's gaps, and is not one, nor a fence the picture builds from a model of
     * its own (ModelFences), whose pickets are not cut.
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
        return name != null && made != null && made.startsWith("Wood") && !"MetalWire".equals(properties.get("Material2"))
                && (name.startsWith("fencing_") || name.startsWith("carpentry_02_") || name.startsWith("crafted_0")) && ModelFences.of(name) == null
                && (Edges.west(properties, sprite.getType()) || Edges.north(properties, sprite.getType()));
    }

    /**
     * Whether a round that goes through an object leaves a hole the picture cuts: a door the picture
     * builds a leaf for, any but a garage door, a wooden fence (blastable), or a wall or fence of
     * something a round goes through that is not glass (Penetration.cost).
     */
    static boolean holedByRounds(IsoObject object) {
        IsoSprite sprite = object.getSprite();
        if (sprite == null) {
            return false;
        }
        if (DoorGlass.isDoor(object)) {
            return !sprite.getProperties().has("GarageDoor");
        }
        String made = sprite.getProperties().get("MaterialType");
        return blastable(object) || made != null && !made.startsWith("Glass") && Penetration.cost(made) != Penetration.STOPS;
    }

    /** The tops of the low board fences the picture has built, in levels over their ground, by their sprite's picture. */
    private static final Map<Texture, Float> tops = new java.util.concurrent.ConcurrentHashMap<>();

    /** Keeps how high the low board fence the picture built from a sprite's picture stands, in levels. Build threads. */
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

    /** How high the picture built a low fence, in levels over its ground, or NaN where it has not built its sprite. */
    static float topOf(IsoObject fence) {
        Float top = tops.get(fence.getSprite().getTextureForCurrentFrame(fence.getDir()));
        return top == null ? Float.NaN : top;
    }

    /** Called as the game has loaded a chunk's squares, on its logic thread: the holes and marks of its things, as saved. */
    static void loaded(IsoChunk chunk) {
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
                    } else if (any) {
                        // The game takes the walls and fences of a chunk it let go of for those of the next it loads.
                        kept.remove(object);
                    }
                }
            }
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

    static float[] parse(String saved) {
        String[] words = saved.isEmpty() ? new String[0] : saved.split(",");
        if (words.length % FLOATS != 0) {
            throw new IllegalArgumentException("blast holes saved as " + words.length + " numbers, not " + FLOATS + " a hole: " + saved);
        }
        if (words.length / FLOATS > MOST_HOLES + MOST_MARKS + MOST_ROUNDS) {
            throw new IllegalArgumentException("blast holes saved as " + words.length / FLOATS + " holes, more than an object keeps");
        }
        float[] entries = new float[words.length];
        for (int i = 0; i < words.length; i++) {
            entries[i] = Float.parseFloat(words[i]);
            if (!Float.isFinite(entries[i])) {
                throw new IllegalArgumentException("blast holes saved with " + words[i] + " among their numbers: " + saved);
            }
        }
        for (int at = 0; at < entries.length; at += FLOATS) {
            float side = entries[at + SIDE], north = entries[at + NORTH], radius = entries[at + RADIUS];
            if (side != (int) side || Math.abs(side) > ROUND || (north != 0f && north != 1f) || !(radius > 0f && radius <= 1f)) {
                throw new IllegalArgumentException("blast holes saved with a hole no shot makes: " + saved);
            }
        }
        return entries;
    }

    static String write(float[] entries) {
        StringBuilder out = new StringBuilder();
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
     * object's mod data and has the picture build the object again. Logic thread.
     */
    static void add(IsoObject object, boolean north, float along, float up, float radius, int seed, int side) {
        float[] was = kept.get(object);
        float[] now = with(was == null ? new float[0] : was, north, along, up, radius, seed, side);
        any = true;
        kept.put(object, now);
        KahluaTable data = object.getModData();
        data.rawset(KEY, write(now));
        ChunkMarks.changed(object);
        if (DevBuild.ON) {
            IsoGridSquare square = object.getSquare();
            Log.info(String.format(Locale.ROOT, "%s at %.3f along and %.3f levels up, radius %.3f, on the %s on the %s edge of (%d, %d, %d)",
                    side == 0 ? "a blast blew a hole" : Math.abs(side) == ROUND ? "a round went through" : "a pellet marked", along, up, radius,
                    DoorGlass.isDoor(object) ? "door" : object.getSprite().getName(), north ? "north" : "west", square.getX(), square.getY(), square.getZ()));
        }
    }

    /** The entries with one more, the oldest of its kind let go past the most of that kind. */
    static float[] with(float[] entries, boolean north, float along, float up, float radius, int seed, int side) {
        int count = entries.length / FLOATS, same = 0, first = -1;
        for (int i = 0; i < count; i++) {
            if (Math.abs((int) entries[i * FLOATS + SIDE]) == Math.abs(side)) {
                same++;
                if (first < 0) {
                    first = i;
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
        return out;
    }

    /** A new hole's or mark's SEED. */
    static int newSeed() {
        return seeds.nextInt(1 << 20);
    }

    /** Whether (along, up) on an object's north or west edge is inside one of its holes, far enough in for a shot to go through: see THROUGH. Logic thread. */
    static boolean through(IsoObject object, boolean north, float along, float up) {
        float[] entries = of(object);
        return entries != null && inAHole(entries, north, along, up);
    }

    static boolean inAHole(float[] entries, boolean north, float along, float up) {
        for (int at = 0; at < entries.length; at += FLOATS) {
            if (!isHole(entries[at + SIDE]) || (entries[at + NORTH] != 0f) != north) {
                continue;
            }
            float r = entries[at + RADIUS] * THROUGH;
            float tall = entries[at + SIDE] == 0f ? HOLE_TALL : 1f;
            float da = (along - entries[at + ALONG]) / r, du = (up - entries[at + UP]) * ViewCamera.LEVEL_HEIGHT / (r * tall);
            if (da * da + du * du <= 1f) {
                return true;
            }
        }
        return false;
    }

    /** Whether an object has a hole on its north or its west edge: a door or a wall with one is seen through, as a door with glass is (RoomMap). Logic thread. */
    static boolean holed(IsoObject object, boolean north) {
        float[] entries = of(object);
        if (entries == null) {
            return false;
        }
        for (int at = 0; at < entries.length; at += FLOATS) {
            if (isHole(entries[at + SIDE]) && (entries[at + NORTH] != 0f) == north) {
                return true;
            }
        }
        return false;
    }

    /** Whether an entry of this SIDE is a hole, a blast's or a round's, and not a mark. */
    private static boolean isHole(float side) {
        return side == 0f || Math.abs(side) == ROUND;
    }

    /**
     * Puts an object's holes and marks on its north or west edge into a cut: each at s = sFrom +
     * sBy * along on the cut's plane and t = tFrom + tBy * up, in tiles, and a mark's side times
     * sideBy.
     */
    static void into(MeshCut cut, float[] entries, boolean north, float sFrom, float sBy, float tFrom, float tBy, int sideBy) {
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
                        MeshCut.outline(s, t, rs * EXIT_WIDER, rt * EXIT_WIDER * EXIT_TALL, EXIT_RAGGED, ROUND_POINTS, seed), Integer.signum(side) * sideBy, s, t);
            } else {
                cut.mark(s, t, MeshCut.outline(s, t, rs, rt, MARK_RAGGED, MARK_POINTS, seed), side * sideBy);
            }
        }
    }

    /** Forgets every object's: the save is left. */
    static void forget() {
        kept.clear();
        any = false;
    }
}
