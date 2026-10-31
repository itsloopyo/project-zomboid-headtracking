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
     * The holes a door's casing on a square's north or west edge is cut with: those of the doorway's wall it stands on,
     * where ImpactFinder keeps the shots that strike it (WorldScene.wallOn). Null where there are none.
     */
    static float[] ofCasingOn(IsoGridSquare square, boolean north) {
        IsoObject wall = WorldScene.wallOn(square, north);
        return wall == null ? null : of(wall);
    }

    /**
     * Whether an object is one a blast blows holes in: a wooden door, a wooden fence on an edge,
     * tall or low, the map's or one a player built, whole, broken or burnt, or a wall of
     * plasterboard, which the pellets go on through (Penetration), or of boards, siding among them,
     * which stops them. Logic thread for a wall (Penetration.madeOf). A rail fence strung with
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
        boolean onEdge = Edges.west(properties, sprite.getType()) || Edges.north(properties, sprite.getType());
        // A corner piece is the wall of both its edges, as WorldScene.wallOn takes it.
        if ((onEdge || properties.has(IsoFlagType.WallNW)) && WorldScene.isWall(properties, sprite.getType())) {
            String wall = Penetration.madeOf(object);
            return PLASTER.equals(wall) || WOOD.equals(wall);
        }
        return name != null && made != null && made.startsWith("Wood") && !"MetalWire".equals(properties.get("Material2"))
                && (name.startsWith("fencing_") || name.startsWith("carpentry_02_") || name.startsWith("crafted_0")) && ModelFences.of(name) == null
                && onEdge;
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

    /** The entries with one more, past the most of its kind letting go of one the others cover, or else the oldest. */
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

    /**
     * What an object is to Breakage on its north or west edge: a door's leaf as the picture measured
     * it (null until then), a wooden fence's boards as high as the picture built them (a level until
     * it has), or a wall's sheet a level high. Any thread.
     */
    static Breakage.Panel panel(IsoObject object, boolean north) {
        if (DoorGlass.isDoor(object)) {
            Windows.Shape leaf = object.getSprite() == null ? null : DoorGlass.leafOf(object);
            return leaf == null ? null : leafPanel(leaf);
        }
        IsoGridSquare square = object.getSquare();
        if (square == null) {
            return null;
        }
        int startsAt = north ? square.getX() : square.getY();
        if (boards(object)) {
            float top = topOf(object);
            return new Breakage.Panel(Breakage.BOARDS, 0f, 1f, 0f, Float.isNaN(top) ? 1f : top, startsAt);
        }
        return new Breakage.Panel(Breakage.SHEET, 0f, 1f, 0f, 1f, startsAt);
    }

    /** A shut door's leaf as Breakage takes it: one sheet. */
    static Breakage.Panel leafPanel(Windows.Shape leaf) {
        return new Breakage.Panel(Breakage.SHEET, Math.min(leaf.start, leaf.end), Math.max(leaf.start, leaf.end),
                Math.min(leaf.foot, leaf.head), Math.max(leaf.foot, leaf.head), 0);
    }

    /** Whether an object is a fence of boards stood side by side: a wooden fence a blast holes, and not a door or a wall. Any thread. */
    static boolean boards(IsoObject object) {
        IsoSprite sprite = object.getSprite();
        return sprite != null && !DoorGlass.isDoor(object) && !WorldScene.isWall(sprite.getProperties(), sprite.getType()) && blastable(object);
    }

    /** How far, in tiles, a broken piece's corners go out past its squares, give or take PIECE_SKEW. */
    private static final float PIECE_OVER = 0.002f, PIECE_SKEW = 0.001f;

    /** A new hole's or mark's SEED. */
    static int newSeed() {
        return seeds.nextInt(1 << 20);
    }

    /** Whether (along, up) on an object's north or west edge is inside one of its holes, far enough in for a shot to go through: see THROUGH. Logic thread. */
    static boolean through(IsoObject object, boolean north, float along, float up) {
        float[] entries = of(object);
        return entries != null && (inAHole(entries, north, along, up) || Breakage.of(entries, north, panel(object, north)).inside(along, up));
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
     * sideBy; and what went with them (Breakage) of the panel the object is, null for none.
     */
    static void into(MeshCut cut, float[] entries, Breakage.Panel panel, boolean north, float sFrom, float sBy, float tFrom, float tBy, int sideBy) {
        float[][] pieces = Breakage.of(entries, north, panel).rectangles;
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
