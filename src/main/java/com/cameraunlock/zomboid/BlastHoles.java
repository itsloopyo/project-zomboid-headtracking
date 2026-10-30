package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import se.krka.kahlua.vm.KahluaTable;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoChunk;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * The holes blasts have blown through wooden doors and board fences, and the marks single pellets
 * have left on doors, which the first person picture cuts and lays into what it builds of them
 * (MeshCut). The game keeps no such thing, so the mod keeps them in the object's mod data, where
 * they are saved with it, and reads them back as chunks load.
 *
 * Each is six numbers: how far along the edge it is on, in tiles from its square's corner, and
 * how high, in levels over its floor (as DoorGlass measures a door's lights); its radius in tiles;
 * the seed its ragged outline is drawn from; for a mark the side of the edge it was struck from, 1
 * or -1, or 0 for a hole; and 1 where the edge is the square's north one, 0 its west: a fence's
 * corner length stands on both. A door's are measured on the leaf as it hangs
 * shut, and laid on the leaf as it stands swung open (OpeningBuilder.addBuiltDoor). A fence's
 * marks are the decals the picture lays over the world (Impacts), as a wall's are.
 */
final class BlastHoles {
    private BlastHoles() {}

    /** The key an object's mod data keeps its holes and marks under. */
    static final String KEY = "cameraunlockBlastHoles";
    static final int FLOATS = 6, ALONG = 0, UP = 1, RADIUS = 2, SEED = 3, SIDE = 4, NORTH = 5;
    /** The most holes and marks an object keeps: the oldest go first past them. */
    static final int MOST_HOLES = 8, MOST_MARKS = 48;
    /** How many points a hole's outline has, how ragged it is, and how much taller than wide: wood splits along its grain, and a door's and a fence's grain runs up. */
    static final int HOLE_POINTS = 18;
    static final float HOLE_RAGGED = 0.3f, HOLE_TALL = 1.25f;
    /** The same for a mark, and its radius in tiles. */
    static final int MARK_POINTS = 7;
    static final float MARK_RAGGED = 0.35f, MARK_RADIUS = 0.014f;
    /** How far into a hole's radius a later shot has to cross to go through it: the outline is ragged, and a round grazing its edge strikes wood. */
    static final float THROUGH = 0.8f;

    /** What each object loaded keeps, for the builds to read off the logic thread. */
    private static final Map<IsoObject, float[]> kept = Collections.synchronizedMap(new WeakHashMap<>());

    /** An object's holes and marks, FLOATS numbers each, or null for none. Any thread. */
    static float[] of(IsoObject object) {
        return kept.get(object);
    }

    /** Whether an object is one a blast blows holes in: a wooden door, or a length of board fence. */
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
        boolean onEdge = Edges.west(properties, sprite.getType()) || Edges.north(properties, sprite.getType());
        boolean picket = properties.has("StopCar") || properties.has("TieSheetRope");
        return Fences.boards(sprite.getName(), properties.get("Material"), properties.get("MaterialType"), properties.get("Material2"), onEdge, picket);
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
                        kept.put(object, parse(saved));
                    }
                }
            }
        }
    }

    static float[] parse(String saved) {
        String[] words = saved.isEmpty() ? new String[0] : saved.split(",");
        if (words.length % FLOATS != 0) {
            throw new IllegalArgumentException("blast holes saved as " + words.length + " numbers, not " + FLOATS + " a hole: " + saved);
        }
        float[] entries = new float[words.length];
        for (int i = 0; i < words.length; i++) {
            entries[i] = Float.parseFloat(words[i]);
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
     * Keeps a hole (side 0) or a mark on an object, past the most it keeps of that kind by
     * letting go of the oldest, saves it in the object's mod data and has the picture build the
     * object again. Logic thread.
     */
    static void add(IsoObject object, boolean north, float along, float up, float radius, int seed, int side) {
        float[] was = kept.get(object);
        float[] now = with(was == null ? new float[0] : was, north, along, up, radius, seed, side);
        kept.put(object, now);
        KahluaTable data = object.getModData();
        data.rawset(KEY, write(now));
        ChunkMarks.changed(object);
        if (DevBuild.ON) {
            IsoGridSquare square = object.getSquare();
            Log.info(String.format(Locale.ROOT, "%s at %.3f along and %.3f levels up, radius %.3f, on the %s on the %s edge of (%d, %d, %d)", side == 0 ? "a blast blew a hole" : "a pellet marked",
                    along, up, radius, DoorGlass.isDoor(object) ? "door" : "fence", north ? "north" : "west", square.getX(), square.getY(), square.getZ()));
        }
    }

    /** The entries with one more, the oldest of its kind let go past the most of that kind. */
    static float[] with(float[] entries, boolean north, float along, float up, float radius, int seed, int side) {
        int count = entries.length / FLOATS, same = 0, first = -1;
        for (int i = 0; i < count; i++) {
            if ((entries[i * FLOATS + SIDE] == 0f) == (side == 0)) {
                same++;
                if (first < 0) {
                    first = i;
                }
            }
        }
        float[] base = entries;
        if (same >= (side == 0 ? MOST_HOLES : MOST_MARKS)) {
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

    /** Whether (along, up) on an object's north or west edge is inside one of its holes, far enough in for a shot to go through: see THROUGH. Logic thread. */
    static boolean through(IsoObject object, boolean north, float along, float up) {
        float[] entries = kept.get(object);
        return entries != null && inAHole(entries, north, along, up);
    }

    static boolean inAHole(float[] entries, boolean north, float along, float up) {
        for (int at = 0; at < entries.length; at += FLOATS) {
            if (entries[at + SIDE] != 0f || (entries[at + NORTH] != 0f) != north) {
                continue;
            }
            float r = entries[at + RADIUS] * THROUGH;
            float da = (along - entries[at + ALONG]) / r, du = (up - entries[at + UP]) * ViewCamera.LEVEL_HEIGHT / (r * HOLE_TALL);
            if (da * da + du * du <= 1f) {
                return true;
            }
        }
        return false;
    }

    /** Whether an object has a hole: a door with one is seen through, as one with glass is (RoomMap). Logic thread. */
    static boolean holed(IsoObject object) {
        float[] entries = kept.get(object);
        if (entries == null) {
            return false;
        }
        for (int at = 0; at < entries.length; at += FLOATS) {
            if (entries[at + SIDE] == 0f) {
                return true;
            }
        }
        return false;
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
            } else {
                cut.mark(s, t, MeshCut.outline(s, t, rs, rt, MARK_RAGGED, MARK_POINTS, seed), side * sideBy);
            }
        }
    }

    /** Forgets every object's: the save is left. */
    static void forget() {
        kept.clear();
    }
}
