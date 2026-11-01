package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import se.krka.kahlua.vm.KahluaTable;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;

/**
 * The pickets shots have broken out of the picket fences the picture stands as models (ModelFences). A
 * picket is nailed to the rails behind it: a round or a pellet that strikes it between its lowest rail's foot
 * and its highest rail's top knocks it out whole, one that strikes it above its highest rail breaks off what
 * is above the break, and one below its lowest rail what is below, as a board of a board fence goes where
 * nothing holds it (Breakage). What breaks off falls in the picture (Debris.shotLoose), and the gap is seen
 * and shot through (ModelFences.struck).
 *
 * The fence stays one instance of its model. Each length of it carries what is broken of its pickets as a
 * mask of BITS bits a picket, counted along the model's x, two masks an object, one for each edge it stands
 * on: the low TOP bits are 0 for a picket whole at its top, GONE for one knocked out, and between them the
 * height it is broken across at over its highest rail, in CUTS steps (cut); the FOOT bit is set where it is
 * broken off below its lowest rail. The world's and the sun's vertex programs move a vertex of a broken
 * picket as collapse does, so a picket gone draws nothing and one broken is drawn to the break. The masks are
 * kept in the fence's mod data and saved with it, as BlastHoles keeps a fence's holes.
 */
final class ShotPickets {
    private ShotPickets() {}

    /** Bits of a mask a picket, the steps a picket broken over its highest rail is broken at, and the most pickets a mask holds. */
    static final int BITS = 4, CUTS = 6, MOST = 12;
    /** The low bits of a picket's: whole at its top, or knocked out, with the steps between; and the bit of one broken off below its lowest rail. */
    static final int TOP = 7, GONE = 7, FOOT = 8;
    /** The bits of a mask a float the graphics card is told holds whole: six pickets. */
    static final int WORD_BITS = 24;

    /** A length of fence whose pickets are told apart is longer than this, in the model's metres, along its x: a rail. */
    private static final float RAIL_LONGEST = 0.5f;

    /** Whether the model of that name is a length of picket fence whose pickets are numbered (numbered). */
    static boolean numbers(String model) {
        return model.equals(ModelFences.WHITE.model()) || model.equals(ModelFences.ROUNDED.model());
    }

    /**
     * Which picket of a length of picket fence each vertex of its model is on, counted from 1 in the order of
     * their places along the model's x, and 0 for a vertex of a rail. A picket or a rail is the triangles that
     * meet at their corners, the same corner of two faces being two vertices where the faces are flat; a rail
     * runs on along most of the length, and a picket is narrow.
     */
    static int[] numbered(String name, float[] vertices, int[] corners) {
        int count = vertices.length / Models.VERTEX_FLOATS;
        int[] joined = new int[count];
        for (int i = 0; i < count; i++) {
            joined[i] = i;
        }
        Map<List<Float>, Integer> at = new HashMap<>();
        for (int vertex = 0; vertex < count; vertex++) {
            int v = vertex * Models.VERTEX_FLOATS;
            Integer same = at.putIfAbsent(List.of(vertices[v], vertices[v + 1], vertices[v + 2]), vertex);
            if (same != null) {
                Models.join(joined, same, vertex);
            }
        }
        for (int corner = 0; corner < corners.length; corner += 3) {
            Models.join(joined, corners[corner], corners[corner + 1]);
            Models.join(joined, corners[corner], corners[corner + 2]);
        }
        float[] least = new float[count], most = new float[count];
        Arrays.fill(least, Float.POSITIVE_INFINITY);
        Arrays.fill(most, Float.NEGATIVE_INFINITY);
        for (int vertex = 0; vertex < count; vertex++) {
            int root = Models.root(joined, vertex);
            least[root] = Math.min(least[root], vertices[vertex * Models.VERTEX_FLOATS]);
            most[root] = Math.max(most[root], vertices[vertex * Models.VERTEX_FLOATS]);
        }
        ArrayList<Integer> pickets = new ArrayList<>();
        int rails = 0;
        for (int vertex = 0; vertex < count; vertex++) {
            if (Models.root(joined, vertex) != vertex) {
                continue;
            }
            if (most[vertex] - least[vertex] > RAIL_LONGEST) {
                rails++;
            } else {
                pickets.add(vertex);
            }
        }
        if (rails == 0 || pickets.isEmpty() || pickets.size() > MOST) {
            throw new IllegalStateException(name + " is a length of picket fence with " + pickets.size() + " pickets and " + rails
                    + " rails, and its pickets are told apart only with a rail and from 1 to " + MOST + " pickets");
        }
        pickets.sort((a, b) -> Float.compare(least[a], least[b]));
        int[] number = new int[count];
        for (int vertex = 0; vertex < count; vertex++) {
            number[vertex] = pickets.indexOf(Models.root(joined, vertex)) + 1;
        }
        return number;
    }

    /**
     * How a length of picket fence's model is laid out, in its own metres: its first corner in the list of every
     * model's (Models.Model.from); how many pickets it has; the foot of its lowest rail, the top of its highest
     * and the pickets' feet and tops; how far either side of its line its pickets reach; and where each picket
     * starts and ends along its x.
     */
    record Layout(int first, int count, float foot, float railTop, float bottom, float top, float half, float[] from, float[] to) {}

    private static final Map<Models.Model, Layout> layouts = new ConcurrentHashMap<>();

    /** How the model of a length of picket fence, the whole one or its plain one, is laid out. Any thread. */
    static Layout layout(Models.Model model) {
        return layouts.computeIfAbsent(model, ShotPickets::measure);
    }

    private static Layout measure(Models.Model model) {
        int count = 0;
        for (int number : model.piece) {
            count = Math.max(count, number);
        }
        if (count == 0) {
            throw new IllegalStateException(model.name + " has no pickets told apart: it is not a length of picket fence");
        }
        float foot = Float.POSITIVE_INFINITY, railTop = Float.NEGATIVE_INFINITY, bottom = Float.POSITIVE_INFINITY, top = Float.NEGATIVE_INFINITY, half = 0f;
        float[] from = new float[count], to = new float[count];
        Arrays.fill(from, Float.POSITIVE_INFINITY);
        Arrays.fill(to, Float.NEGATIVE_INFINITY);
        for (int vertex = 0; vertex < model.piece.length; vertex++) {
            int v = vertex * Models.VERTEX_FLOATS, number = model.piece[vertex];
            float x = model.vertices[v], y = model.vertices[v + 1], z = model.vertices[v + 2];
            if (number == 0) {
                foot = Math.min(foot, z);
                railTop = Math.max(railTop, z);
                continue;
            }
            bottom = Math.min(bottom, z);
            top = Math.max(top, z);
            half = Math.max(half, Math.abs(y));
            from[number - 1] = Math.min(from[number - 1], x);
            to[number - 1] = Math.max(to[number - 1], x);
        }
        if (!(bottom < foot && foot < railTop && railTop < top)) {
            throw new IllegalStateException(model.name + " has its pickets from " + bottom + " to " + top + " m and its rails from " + foot + " to " + railTop
                    + " m: a picket's ends are past its rails");
        }
        return new Layout(model.from, count, foot, railTop, bottom, top, half, from, to);
    }

    /** The layouts of the picket fences' models, whole and plain, for the render thread to look up by first corner. */
    private static volatile Layout[] drawn;

    /** The layout of the picket fence's model, whole or plain, whose first corner is that, or null for any other model. Render thread. */
    static Layout layoutAt(int first) {
        Layout[] known = drawn;
        if (known == null) {
            Models.Model white = Models.get(ModelFences.WHITE.model()), rounded = Models.get(ModelFences.ROUNDED.model());
            drawn = known = new Layout[] {layout(white), layout(white.plain), layout(rounded), layout(rounded.plain)};
        }
        for (Layout layout : known) {
            if (layout.first() == first) {
                return layout;
            }
        }
        return null;
    }

    /** What a mask holds of a picket, counted from 0. */
    static int of(long mask, int picket) {
        return (int) (mask >>> BITS * picket) & (1 << BITS) - 1;
    }

    /** How high a picket broken over its highest rail at step code (1 to CUTS) is broken across, in the model's metres. */
    static float cut(Layout layout, int code) {
        return layout.railTop() + (code - 1) / (float) CUTS * (layout.top() - layout.railTop());
    }

    /**
     * The mask with the picket, counted from 0, struck up metres up the model: knocked out between its lowest
     * rail's foot and its highest rail's top, broken across over its highest rail at the step at or below the
     * strike, so the place struck goes, or broken off under its lowest rail. A break over a lower one is not
     * moved, and a picket knocked out stays so.
     */
    static long broken(Layout layout, long mask, int picket, float up) {
        int was = of(mask, picket), top = was & TOP, now;
        if (top == GONE) {
            return mask;
        }
        if (up > layout.railTop()) {
            int code = 1 + Math.min(CUTS - 1, (int) ((up - layout.railTop()) / (layout.top() - layout.railTop()) * CUTS));
            now = top != 0 && top <= code ? was : was & FOOT | code;
        } else if (up < layout.foot()) {
            now = was | FOOT;
        } else {
            now = GONE;
        }
        int shift = BITS * picket;
        return mask & ~((long) ((1 << BITS) - 1) << shift) | (long) now << shift;
    }

    /** What is left standing of a picket held as bits are (of), from and to in the model's metres up, or null for one knocked out. */
    static float[] standing(Layout layout, int bits) {
        int top = bits & TOP;
        if (top == GONE) {
            return null;
        }
        return new float[] {(bits & FOOT) != 0 ? layout.foot() : layout.bottom(), top == 0 ? layout.top() : cut(layout, top)};
    }

    /** What of the picket, counted from 0, broke off between two masks, from and to in the model's metres up, or null for nothing. */
    static float[] loosened(Layout layout, long before, long after, int picket) {
        float[] was = standing(layout, of(before, picket)), now = standing(layout, of(after, picket));
        if (was == null) {
            return null;
        }
        if (now == null) {
            return was;
        }
        if (now[1] < was[1]) {
            return new float[] {now[1], was[1]};
        }
        return now[0] > was[0] ? new float[] {was[0], now[0]} : null;
    }

    /**
     * Moves the place at (x, y and up in the model's metres) of a vertex on the picket numbered (from 1, 0 for a
     * rail) as the world's and the sun's vertex programs do (GLSL): every vertex of a picket knocked out to the
     * model's origin, so its triangles have no size, and one of a picket broken to the break.
     */
    static void collapse(Layout layout, long mask, int number, float[] at) {
        if (number == 0 || mask == 0L) {
            return;
        }
        int bits = of(mask, number - 1), top = bits & TOP;
        if (top == GONE) {
            at[0] = at[1] = at[2] = 0f;
            return;
        }
        if ((bits & FOOT) != 0) {
            at[2] = Math.max(at[2], layout.foot());
        }
        if (top != 0) {
            at[2] = Math.min(at[2], cut(layout, top));
        }
    }

    /**
     * collapse, for the world's and the sun's vertex programs. uPickets is the run's layout: its lowest rail's
     * foot, its highest rail's top and its pickets' tops (layoutAt); broken a mask as low and high give it.
     * Every number here is a whole number under 2^24, which a float holds exactly, and is taken apart by
     * powers of two, which leave it exact.
     */
    static final String GLSL = """
            uniform vec3 uPickets;

            vec3 shotPickets(vec3 at, float picket, vec2 broken) {
                if (picket < 0.5 || broken.x + broken.y < 0.5) {
                    return at;
                }
                float k = picket - 1.0;
                float word = k < 5.5 ? broken.x : broken.y;
                float bits = mod(floor(word * exp2(-4.0 * mod(k, 6.0))), 16.0);
                float top = mod(bits, 8.0);
                if (top > 6.5) {
                    return vec3(0.0);
                }
                if (bits > 7.5) {
                    at.z = max(at.z, uPickets.x);
                }
                if (top > 0.5) {
                    at.z = min(at.z, uPickets.y + (top - 1.0) / 6.0 * (uPickets.z - uPickets.y));
                }
                return at;
            }
            """;

    /** The first WORD_BITS bits of a mask, as the graphics card is told them, and the rest. */
    static float low(long mask) {
        return mask & (1L << WORD_BITS) - 1;
    }

    static float high(long mask) {
        return mask >>> WORD_BITS;
    }

    /** The key an object's mod data keeps its pickets broken under, and what the saved masks start with. */
    private static final String KEY = "cameraunlockShotPickets", SAVED = "1:";

    /** The masks of a fence as they are saved: north's then west's. */
    static String write(long north, long west) {
        return SAVED + north + "," + west;
    }

    /** The masks saved of a fence with that many pickets, north's then west's. */
    static long[] parse(String saved, int pickets) {
        if (!saved.startsWith(SAVED)) {
            throw new IllegalArgumentException("broken pickets saved without the " + SAVED + " they start with: " + saved);
        }
        String[] words = saved.substring(SAVED.length()).split(",", -1);
        if (words.length != 2) {
            throw new IllegalArgumentException("broken pickets saved as " + words.length + " masks, not one for each edge: " + saved);
        }
        long[] masks = new long[2];
        for (int i = 0; i < 2; i++) {
            masks[i] = Long.parseLong(words[i]);
            if (masks[i] < 0L || masks[i] >>> BITS * pickets != 0L) {
                throw new IllegalArgumentException("broken pickets saved of more than the " + pickets + " pickets the fence has: " + saved);
            }
        }
        return masks;
    }

    /** What each object loaded keeps, north's and west's, for the builds to read off the logic thread. */
    private static final Map<IsoObject, long[]> kept = Collections.synchronizedMap(new WeakHashMap<>());
    /** Whether anything has been kept since the save was looked at, so the builds take no lock in a save with nothing broken. */
    private static volatile boolean any;

    /** A fence's masks, north's then west's, or null for none broken. Any thread. */
    static long[] of(IsoObject object) {
        return any ? kept.get(object) : null;
    }

    /** The mask of the fence's length on its north or west edge, 0 for none broken. Any thread. */
    static long on(IsoObject object, boolean north) {
        long[] masks = of(object);
        return masks == null ? 0L : masks[north ? 0 : 1];
    }

    /** The layout of the whole model of the picket fence an object is, or null for anything else. */
    private static Layout layoutOf(IsoObject object) {
        IsoSprite sprite = object.getSprite();
        ModelFences.Fence fence = sprite == null ? null : ModelFences.of(sprite.getName());
        return fence == ModelFences.WHITE || fence == ModelFences.ROUNDED ? layout(Models.get(fence.model())) : null;
    }

    /**
     * Called for each object of a chunk the game has loaded, on its logic thread (BlastHoles.loaded): its masks,
     * as saved. A list that cannot be read is said once in the log and the fence keeps nothing broken.
     */
    static void loaded(IsoObject object, IsoGridSquare square) {
        if (!(object.hasModData() && object.getModData().rawget(KEY) instanceof String saved)) {
            if (any) {
                // The game takes the things of a chunk it let go of for those of the next it loads.
                kept.remove(object);
            }
            return;
        }
        Layout layout = layoutOf(object);
        long[] masks;
        try {
            if (layout == null) {
                throw new IllegalArgumentException("broken pickets saved on what is no picket fence: " + saved);
            }
            masks = parse(saved, layout.count());
        } catch (IllegalArgumentException malformed) {
            if (!malformedSaid) {
                malformedSaid = true;
                Log.error(String.format(Locale.ROOT, "the pickets saved broken on %s at (%d, %d, %d) are drawn whole, and so are any others saved the same way",
                        object.getSprite() == null ? "a thing" : object.getSprite().getName(), square.getX(), square.getY(), square.getZ()), malformed);
            }
            kept.remove(object);
            return;
        }
        any = true;
        kept.put(object, masks);
        if (DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "read the pickets saved broken on %s at (%d, %d, %d): north %s, west %s", object.getSprite().getName(),
                    square.getX(), square.getY(), square.getZ(), Long.toHexString(masks[0]), Long.toHexString(masks[1])));
        }
    }

    private static boolean malformedSaid;

    /** How far past a picket's sides a mark on it is taken to be on it, in tiles. */
    private static final float ON_PICKET = 0.01f;

    /**
     * A round or a pellet of the viewer's struck the picket, counted from 0, of the length of picket fence on
     * the north or west edge of the fence's square, up metres up its model: what breaks of the picket (broken)
     * is kept and saved with the fence and the chunk built again, the marks shots left on what broke go with it,
     * and it falls in the picture away from the side the shot came from (Debris.shotLoose). foot is the length's
     * foot, in tiles over the flat map, and raised how far the game draws the fence over its floor, in levels.
     * Whether anything broke. Logic thread.
     */
    static boolean shoot(IsoObject fence, boolean north, int picket, float up, float foot, float raised, boolean blast, ViewCamera.Pose line) {
        Layout layout = layoutOf(fence);
        long[] was = of(fence);
        long before = was == null ? 0L : was[north ? 0 : 1], after = broken(layout, before, picket, up);
        float[] fell = loosened(layout, before, after, picket);
        if (fell == null) {
            return false;
        }
        long[] now = was == null ? new long[2] : was.clone();
        now[north ? 0 : 1] = after;
        any = true;
        kept.put(fence, now);
        KahluaTable data = fence.getModData();
        data.rawset(KEY, write(now[0], now[1]));
        ChunkMarks.changed(fence);
        IsoGridSquare square = fence.getSquare();
        // The model's middle is the edge's, and its x runs east along a north edge and north along a west one (Models.turn).
        float alongFrom = north ? 0.5f + layout.from()[picket] : 0.5f - layout.to()[picket];
        float alongTo = north ? 0.5f + layout.to()[picket] : 0.5f - layout.from()[picket];
        float edge = north ? square.getY() : square.getX(), start = north ? square.getX() : square.getY();
        float acrossReach = layout.half() + ON_PICKET;
        float[] box = {
                north ? start + alongFrom - ON_PICKET : edge - acrossReach, north ? edge - acrossReach : start + alongFrom - ON_PICKET, foot + fell[0],
                north ? start + alongTo + ON_PICKET : edge + acrossReach, north ? edge + acrossReach : start + alongTo + ON_PICKET, foot + fell[1]};
        Impacts.withdraw(box);
        BlastHoles.rimsGone(fence, box);
        float across = north ? line.forwardY : line.forwardX;
        float levelsFrom = raised + fell[0] / ViewCamera.LEVEL_HEIGHT, levelsTo = raised + fell[1] / ViewCamera.LEVEL_HEIGHT;
        Breakage.Pieces piece = new Breakage.Pieces(new float[][] {{alongFrom, levelsFrom, alongTo, levelsFrom, alongTo, levelsTo, alongFrom, levelsTo}});
        Debris.shotLoose(fence, north, Breakage.Pieces.NONE, piece, blast, across > 0f ? -1 : 1, BlastHoles.slant(north ? line.forwardX : line.forwardY, across),
                BlastHoles.slant(line.forwardUp, across));
        if (DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "a %s broke picket %d of the %s on the %s edge of (%d, %d, %d) %.3f m up: %.3f to %.3f m of it falls, the mask %s now",
                    blast ? "pellet" : "round", picket + 1, fence.getSprite().getName(), north ? "north" : "west", square.getX(), square.getY(), square.getZ(), up,
                    fell[0], fell[1], Long.toHexString(after)));
        }
        return true;
    }

    /** Forgets every fence's: the save is left. */
    static void forget() {
        kept.clear();
        any = false;
    }
}
