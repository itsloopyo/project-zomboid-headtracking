package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.zip.GZIPInputStream;

/**
 * One of the mod's own vehicle interiors, read out of the jar: its triangles in the parts the mod
 * moves, and what it is told beside them. Made by tools/models/cabins.py.
 *
 * Places are in tiles in the interior's own frame: from the driver's eye as the mod seats it, along
 * the vehicle, across it to the left and up. An interior is built on one body; fitted to another,
 * its places move with where that body has its windscreen, sills and glass (see fit).
 *
 * Read on any thread; nothing here touches the graphics card.
 */
final class CabinModel {
    /** What each part is: the shell, the wheel, the four needles, the glass, the mirrors. */
    static final int SHELL = 0, WHEEL = 1, NEEDLE_SPEED = 2, NEEDLE_REVS = 3, NEEDLE_FUEL = 4, NEEDLE_HEAT = 5, GLASS = 6, MIRROR = 7, GROUPS = 8;
    /**
     * Of each vertex: place, normal, tangent, handedness, texture place, the sky it sees, the shade of what stands
     * near it, a byte of its part's finish (how much it takes the trim's tint in its low four bits, how metal it is
     * in its high four), a byte of what it is drawn in (128 and the repeating surface its fine relief comes from,
     * 127 for none, or below 128 which of the interior's repeating materials alone), and two spare bytes.
     */
    static final int VERTEX_BYTES = 40;
    static final int NORMAL_AT = 12, TANGENT_AT = 18, WAY_AT = 24, UV_AT = 26, SKY_AT = 34, NEAR_AT = 35, FINISH_AT = 36, KIND_AT = 37;
    /** How many repeating surfaces there are, and the most materials an interior draws in one alone. */
    static final int LAYERS = 8, MOST_TILED = 8;
    private static final int MAGIC = 'C' | 'U' << 8 | 'C' << 16 | 'M' << 24;
    private static final int VERSION = 2;

    final String name;
    final int vertices, indices;
    final int[] first = new int[GROUPS], count = new int[GROUPS];
    /** The body measures the interior was built on, as VehicleMeshes.Parts has them: see fit. */
    final float[] measures = new float[5];
    /** The wheel's middle, the way its column runs away from the driver, and its radius. */
    final float[] wheel = new float[7];
    /** For speed, revs, fuel and heat: the needle's pivot, the axis it turns about, where it points at nought and at full scale, and what full scale is. */
    final float[] needles = new float[36];
    /** The mirror on the screen and those on the doors: each its middle, the way it faces, the way across it, and its half width and half height. */
    final float[] mirrors;
    /** The lamp in the roof, and where the instruments' light comes from. */
    final float[] lamps = new float[6];
    /** Half the windscreen's width, which the wipers' arcs are laid out on. */
    float screenHalf;
    /**
     * How far the wipers' blades reach from their feet, in tiles: WIPER_SHARE of the way up the windscreen, no more
     * than WIPER_WIDE of its half width, so the two arcs overlap only a little in the middle.
     */
    final float wiperReach;
    private static final float WIPER_SHARE = 0.85f, WIPER_WIDE = 0.9f;
    /** A windscreen's texture place is counted up from this far under its foot, where the wipers stand: see CabinGlass. */
    private static final float WIPERS_BELOW = 0.3f;
    /** Whose pictures the interior is drawn with: one style's are shared by the bodies built in it. */
    final String pictures;
    /** Each repeating surface's tiles to a repeat. */
    final float[] repeats = new float[LAYERS];
    /** For each material drawn in a repeating surface alone: which surface, its colour, linear, and its roughness. */
    final float[] tiledLayer, tiledColour;
    /** The vertices as the file has them, and the triangles: the first `indices` ints of the file's tail. */
    final ByteBuffer vertexBytes, indexBytes;

    private CabinModel(String name, ByteBuffer read) throws IOException {
        this.name = name;
        if (read.getInt() != MAGIC || read.getInt() != VERSION) {
            throw new IOException(name + " is not an interior of version " + VERSION);
        }
        vertices = read.getInt();
        indices = read.getInt();
        int groups = read.getInt();
        if (groups != GROUPS) {
            throw new IOException(name + " has " + groups + " parts, where " + GROUPS + " are read");
        }
        for (int g = 0; g < GROUPS; g++) {
            first[g] = read.getInt();
            count[g] = read.getInt();
        }
        byte[] word = new byte[read.get() & 0xff];
        read.get(word);
        pictures = new String(word, java.nio.charset.StandardCharsets.US_ASCII);
        int floats = read.getInt(), start = read.position();
        for (float[] into : new float[][] {measures, wheel, needles, lamps}) {
            for (int i = 0; i < into.length; i++) {
                into[i] = read.getFloat();
            }
        }
        screenHalf = read.getFloat();
        for (int i = 0; i < LAYERS; i++) {
            repeats[i] = read.getFloat();
        }
        int tiled = (int) read.getFloat();
        if (tiled < 0 || tiled > MOST_TILED) {
            throw new IOException(name + " has " + tiled + " materials drawn in a repeating surface alone, where " + MOST_TILED + " is the most");
        }
        tiledLayer = new float[MOST_TILED];
        tiledColour = new float[MOST_TILED * 4];
        for (int t = 0; t < tiled; t++) {
            tiledLayer[t] = read.getFloat();
            for (int i = 0; i < 4; i++) {
                tiledColour[t * 4 + i] = read.getFloat();
            }
        }
        int mirrorCount = (int) read.getFloat();
        mirrors = new float[mirrorCount * 11];
        for (int i = 0; i < mirrors.length; i++) {
            mirrors[i] = read.getFloat();
        }
        if (read.position() - start != floats * Float.BYTES) {
            throw new IOException(name + " says " + floats + " numbers of itself, which are not laid out as they are read");
        }
        if (read.remaining() != vertices * VERTEX_BYTES + indices * Integer.BYTES) {
            throw new IOException(name + " holds " + read.remaining() + " bytes after its head, not the " + vertices + " vertices and " + indices + " indices it says");
        }
        vertexBytes = read.slice(read.position(), vertices * VERTEX_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        indexBytes = read.slice(read.position() + vertices * VERTEX_BYTES, indices * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        // The wiped panes are told by their sky; how high up the windscreen a place is, by its texture place.
        float slant = 0f;
        for (int i = first[GLASS]; i < first[GLASS] + count[GLASS]; i++) {
            int at = indexBytes.getInt(i * Integer.BYTES) * VERTEX_BYTES;
            if ((vertexBytes.get(at + SKY_AT) & 0xff) > 127) {
                slant = Math.max(slant, vertexBytes.getFloat(at + UV_AT + Float.BYTES) + WIPERS_BELOW);
            }
        }
        if (slant <= 0f) {
            throw new IOException(name + " has no windscreen the wipers sweep");
        }
        wiperReach = Math.min(WIPER_SHARE * slant, WIPER_WIDE * screenHalf);
    }

    /**
     * Which interior a vehicle's script has. A script belongs to the family whose name it starts
     * with, the longest such name winning: the liveries of one body share its script's stem.
     */
    static final String[][] FAMILIES = {
        {"Base.CarNormal", "carnormal"},
        {"Base.CarLights", "carnormal"},
        {"Base.CarTaxi", "carnormal"},
        {"Base.CarStationWagon", "carstationwagon"},
        {"Base.SmallCar", "smallcar"},
        {"Base.SmallCar02", "smallcar02"},
        // The scripts of their wrecks are named the other way about.
        {"Base.CarSmall", "smallcar"},
        {"Base.CarSmall02", "smallcar02"},
        {"Base.CarLuxury", "carluxury"},
        {"Base.ModernCar", "moderncar"},
        {"Base.ModernCar02", "moderncar02"},
        {"Base.SportsCar", "sportscar"},
        {"Base.RaceCar", "racecar"},
        {"Base.OffRoad", "offroad"},
        {"Base.SUV", "suv"},
        {"Base.PickUpTruck", "pickuptruck"},
        // The same cab as the pickup's.
        {"Base.PickUpVan", "pickuptruck"},
        {"Base.Van", "van"},
        {"Base.VanSeats", "vanseats"},
        {"Base.StepVan", "stepvan"},
    };

    /** The interior's name for a vehicle's script, or null where the mod has none for it. */
    static String nameFor(String script) {
        String best = null;
        int longest = -1;
        for (String[] family : FAMILIES) {
            if (script.startsWith(family[0]) && family[0].length() > longest) {
                best = family[1];
                longest = family[0].length();
            }
        }
        return best;
    }

    private static final HashMap<String, CabinModel> read = new HashMap<>();
    /** The interiors and their pictures begun reading ahead, under read's lock. A style's pictures are shared by its interiors and read once. */
    private static final HashSet<String> askedAhead = new HashSet<>(), picturesAhead = new HashSet<>();

    /**
     * Begins reading the interior of a vehicle of that script, and its pictures, on another thread: read when it is
     * first sat in, it held that frame for over a second.
     */
    static void readAhead(String script) {
        String name = nameFor(script);
        if (name == null) {
            return;
        }
        synchronized (read) {
            if (read.containsKey(name) || !askedAhead.add(name)) {
                return;
            }
        }
        Log.info("the interior " + name + " is read ahead for " + script + ", the player being near one");
        Ahead.begin("cabin " + name, () -> {
            CabinModel model = load(name);
            boolean first;
            synchronized (read) {
                first = picturesAhead.add(model.pictures);
            }
            if (first) {
                Pictures.readAhead("/cabins/" + model.pictures + ".colour.bc");
                Pictures.readAhead("/cabins/" + model.pictures + ".surface.bc");
            }
            return model;
        });
    }

    /** The interior of that name: read ahead, or read here the first time it is asked for. Thrown where the jar has none or it cannot be read. */
    static CabinModel named(String name) throws IOException {
        synchronized (read) {
            CabinModel model = read.get(name);
            if (model == null) {
                model = Ahead.take("cabin " + name, () -> load(name));
                read.put(name, model);
            }
            return model;
        }
    }

    private static CabinModel load(String name) throws IOException {
        String path = "/cabins/" + name + ".mesh.gz";
        try (InputStream packed = CabinModel.class.getResourceAsStream(path)) {
            if (packed == null) {
                throw new IOException("the jar has no " + path);
            }
            return new CabinModel(name, ByteBuffer.wrap(new GZIPInputStream(packed).readAllBytes()).order(ByteOrder.LITTLE_ENDIAN));
        }
    }

    /** Where the wheel's middle is from the driver's eye in every interior: Cabins.EYE_BEHIND and EYE_ABOVE. */
    static final float WHEEL_ALONG = Cabins.EYE_BEHIND, WHEEL_UP = -Cabins.EYE_ABOVE;

    /**
     * A place in the interior moved to where it would be on a body measured so: along, from where
     * the wheel is to where the windscreen's foot is, by the measures' difference there and not
     * at all behind the wheel; up, from the wheel's middle to the belt, which is the sill at the
     * sides and the windscreen's foot ahead, and from the belt to the roof, which stays where it
     * is over the eye; across, from the driver to the middle line to the glass on each side.
     *
     * @param from the measures the interior was built on
     * @param to   the measures of the body it is fitted to
     * @param p    along, across and up, moved in place
     */
    static void fit(float[] from, float[] to, float[] p) {
        float wheelX = WHEEL_ALONG, wheelZ = WHEEL_UP;
        float footFrom = wheelX + from[0], footTo = wheelX + to[0];
        float x = p[0], y = p[1], z = p[2];
        float nx;
        if (x <= wheelX) {
            nx = x;
        } else if (x <= footFrom) {
            nx = wheelX + (x - wheelX) * (footTo - wheelX) / (footFrom - wheelX);
        } else {
            nx = x + footTo - footFrom;
        }
        // How far the belt moves here: the sill's change at the sides, the foot's ahead of the wheel.
        float ahead = smoothstep(wheelX + 0.1f, footFrom, x);
        float beltFrom = wheelZ + lerp(from[2], from[1], ahead), beltTo = wheelZ + lerp(to[2], to[1], ahead);
        float roof = ROOF_STAYS;
        float nz;
        if (z <= wheelZ) {
            nz = z;
        } else if (z <= beltFrom) {
            nz = wheelZ + (z - wheelZ) * (beltTo - wheelZ) / (beltFrom - wheelZ);
        } else if (z <= roof) {
            nz = beltTo + (z - beltFrom) * (roof - beltTo) / (roof - beltFrom);
        } else {
            nz = z;
        }
        // Across: the driver stays over the wheel, the middle line and the glass on both sides move.
        float midFrom = from[4], midTo = to[4];
        float glassFrom = from[3], glassTo = to[3];
        float farFrom = 2f * midFrom - glassFrom, farTo = 2f * midTo - glassTo;
        float ny;
        if (y >= 0f) {
            ny = y * glassTo / glassFrom;
        } else if (y >= midFrom) {
            ny = y * midTo / midFrom;
        } else {
            ny = midTo + (y - midFrom) * (farTo - midTo) / (farFrom - midFrom);
        }
        p[0] = nx;
        p[1] = ny;
        p[2] = nz;
    }

    /** Above this, over the eye, the roof and what hangs from it stay where they are. */
    static final float ROOF_STAYS = 0.30f;

    private static float smoothstep(float from, float to, float x) {
        float t = Math.clamp((x - from) / (to - from), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * The interior's vertices fitted to a body, laid out as the file has them: places moved by
     * fit, normals and tangents turned as the move bends the surface. A part that moves as one
     * (the wheel, the needles, the mirrors) keeps its shape and goes where its middle goes; so
     * does what is told of it beside the triangles, into wheelTo, needlesTo and mirrorsTo.
     */
    ByteBuffer fitted(float[] body, float[] wheelTo, float[] needlesTo, float[] mirrorsTo, float[] lampsTo) {
        ByteBuffer out = ByteBuffer.allocateDirect(vertices * VERTEX_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        byte[] copy = new byte[vertices * VERTEX_BYTES];
        vertexBytes.get(0, copy);
        out.put(0, copy);
        int[] groupOf = new int[vertices];
        for (int g = 0; g < GROUPS; g++) {
            for (int i = first[g]; i < first[g] + count[g]; i++) {
                groupOf[indexBytes.getInt(i * Integer.BYTES)] = g;
            }
        }
        float[] p = new float[3], q = new float[3];
        float[] shift = new float[GROUPS * 3];
        System.arraycopy(wheel, 0, wheelTo, 0, wheel.length);
        System.arraycopy(needles, 0, needlesTo, 0, needles.length);
        System.arraycopy(mirrors, 0, mirrorsTo, 0, mirrors.length);
        System.arraycopy(lamps, 0, lampsTo, 0, lamps.length);
        moved(body, wheelTo, 0, shift, WHEEL);
        for (int n = 0; n < 4; n++) {
            moved(body, needlesTo, n * 9, shift, NEEDLE_SPEED + n);
        }
        for (int m = 0; m < mirrors.length / 11; m++) {
            moved(body, mirrorsTo, m * 11, null, -1);
        }
        moved(body, lampsTo, 0, null, -1);
        moved(body, lampsTo, 3, null, -1);
        float e = 0.005f;
        for (int v = 0; v < vertices; v++) {
            int at = v * VERTEX_BYTES, g = groupOf[v];
            for (int axis = 0; axis < 3; axis++) {
                p[axis] = vertexBytes.getFloat(at + axis * Float.BYTES);
            }
            if (g == WHEEL || g >= NEEDLE_SPEED && g <= NEEDLE_HEAT || g == MIRROR) {
                if (g == MIRROR) {
                    // A mirror goes with the middle of the mirror it is the glass of.
                    int which = Math.clamp((int) vertexBytes.getFloat(at + UV_AT), 0, mirrors.length / 11 - 1);
                    for (int axis = 0; axis < 3; axis++) {
                        out.putFloat(at + axis * Float.BYTES, p[axis] + mirrorsTo[which * 11 + axis] - mirrors[which * 11 + axis]);
                    }
                } else {
                    for (int axis = 0; axis < 3; axis++) {
                        out.putFloat(at + axis * Float.BYTES, p[axis] + shift[g * 3 + axis]);
                    }
                }
                continue;
            }
            System.arraycopy(p, 0, q, 0, 3);
            fit(measures, body, q);
            for (int axis = 0; axis < 3; axis++) {
                out.putFloat(at + axis * Float.BYTES, q[axis]);
            }
            // How the move stretches the surface here, from places a little way off along each axis.
            float[][] j = new float[3][3];
            for (int axis = 0; axis < 3; axis++) {
                float[] r = {p[0], p[1], p[2]};
                r[axis] += e;
                fit(measures, body, r);
                for (int row = 0; row < 3; row++) {
                    j[row][axis] = (r[row] - q[row]) / e;
                }
            }
            turn(out, at + NORMAL_AT, j, true);
            turn(out, at + TANGENT_AT, j, false);
        }
        return out;
    }

    /** Moves the place at off in floats by fit, noting how far it went as the shift of a part that moves as one. */
    private void moved(float[] body, float[] floats, int off, float[] shift, int group) {
        float[] p = {floats[off], floats[off + 1], floats[off + 2]};
        fit(measures, body, p);
        if (shift != null) {
            for (int axis = 0; axis < 3; axis++) {
                shift[group * 3 + axis] = p[axis] - floats[off + axis];
            }
        }
        System.arraycopy(p, 0, floats, off, 3);
    }

    /** A normal turned by the inverse transpose of j, a tangent by j itself, each brought back to a length of one. */
    private static void turn(ByteBuffer out, int at, float[][] j, boolean normal) {
        float x = out.getShort(at) / 32767f, y = out.getShort(at + 2) / 32767f, z = out.getShort(at + 4) / 32767f;
        float nx, ny, nz;
        if (normal) {
            // The inverse transpose: the cofactors of j, which differ from it by its determinant only.
            float c00 = j[1][1] * j[2][2] - j[1][2] * j[2][1], c01 = j[1][2] * j[2][0] - j[1][0] * j[2][2], c02 = j[1][0] * j[2][1] - j[1][1] * j[2][0];
            float c10 = j[0][2] * j[2][1] - j[0][1] * j[2][2], c11 = j[0][0] * j[2][2] - j[0][2] * j[2][0], c12 = j[0][1] * j[2][0] - j[0][0] * j[2][1];
            float c20 = j[0][1] * j[1][2] - j[0][2] * j[1][1], c21 = j[0][2] * j[1][0] - j[0][0] * j[1][2], c22 = j[0][0] * j[1][1] - j[0][1] * j[1][0];
            nx = c00 * x + c01 * y + c02 * z;
            ny = c10 * x + c11 * y + c12 * z;
            nz = c20 * x + c21 * y + c22 * z;
        } else {
            nx = j[0][0] * x + j[0][1] * y + j[0][2] * z;
            ny = j[1][0] * x + j[1][1] * y + j[1][2] * z;
            nz = j[2][0] * x + j[2][1] * y + j[2][2] * z;
        }
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1e-6f) {
            return;
        }
        out.putShort(at, (short) Math.round(nx / length * 32767f));
        out.putShort(at + 2, (short) Math.round(ny / length * 32767f));
        out.putShort(at + 4, (short) Math.round(nz / length * 32767f));
    }
}
