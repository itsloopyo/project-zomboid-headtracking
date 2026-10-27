package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.opengl.GL11;
import zombie.core.textures.Texture;

/**
 * The beds of roses the game lays outside houses: a square of the ground painted with blooms
 * among leaves, some edged with stones along a side or two. Laid flat as painted they were a
 * pink picture on the lawn. Within REACH of the eye each square grows rose bushes out of the
 * painting: a few stems, leaves held out round them and flower heads at their tops, each leaf
 * coloured from a leaf the sprite paints near its bush and each flower from a bloom. Where the
 * sprite paints stones nothing grows, and the stones stay painted on the ground with the soil.
 *
 * Roses go by the season (Seasons): a bush comes into leaf in spring and drops its leaves in
 * autumn as the trees do, each on a day of its own, and is bare canes through the winter. It
 * flowers from late spring until early autumn.
 *
 * Where its roses grow, a bed is laid as earth: its painting with the blooms painted out, the
 * leaves and stones left, so the flowers are on the bushes and not a pink carpet under them.
 *
 * The colours come from the sprite's own dots, which are read on the thread that draws (see
 * SpriteColours): a bed is laid as painted until its sprite has been read, and grows once it has.
 */
final class Flowerbeds {
    private static final String SHEET = "vegetation_ornamental_01_";
    /** Within this many tiles of the eye a bed grows its roses, and past it the painting is the bed. */
    static final float REACH = 20f;
    /** What each dot of a bed's sprite is. */
    static final byte NOTHING = 0, LEAF = 1, BLOOM = 2, STONE = 3;
    /** How many bushes a square grows near the eye, along each side of it. */
    private static final int SITES = 4;
    /** A bush looks this many dots either way round its foot for what it grows out of, and for leaves and blooms to take its colours from. */
    static final int LOOK = 5;
    /** A bush is never shorter or taller than these, in tiles. */
    private static final float LOWEST = 0.32f, TALLEST = 0.5f;
    /** How far out from its foot a bush reaches, in tiles: it stands this far back from a wall, or its canes lean through it. */
    static final float SPREAD = 0.2f;
    /** How far through spring the first and the last bush come into flower, by Seasons.stepped's spring, which goes no further than 0.7. */
    private static final float FLOWERS_EARLIEST = 0.4f, FLOWERS_LATEST = 0.7f;
    /** How far through autumn the first and the last bush have finished flowering. */
    private static final float FLOWERS_GONE_EARLIEST = 0.1f, FLOWERS_GONE_LATEST = 0.5f;
    /** A dot covered less than this is the sprite's empty corner. */
    private static final int COVERED = 153;

    /** For each sprite read: what each of its dots is, row by row from its top left. */
    private static final ConcurrentHashMap<Texture, Bed> known = new ConcurrentHashMap<>();
    /** The sprites a bed has asked for: no other sprite is measured. */
    private static final Set<Texture> wanted = ConcurrentHashMap.newKeySet();

    /** What each of a sprite's dots is, width across and height down. */
    record Bed(int width, int height, byte[] dots) {}

    /** What earth says of a bed whose picture no frame has been drawn since it was first asked for. */
    static final int PENDING = -1;
    /** The colour of the earth the blooms are painted out with, each channel 0 to 255, at a bloom's usual light. */
    static final int EARTH_RED = 78, EARTH_GREEN = 58, EARTH_BLUE = 40;

    /** One picture of a bed laid as earth: the sprite, and where it sits in its frame. */
    private record Earth(Texture texture, float originU, float originV, float scale, float left, float top, float width, float height) {}

    /** What has been asked for of each sprite, and each picture made: its OpenGL name, or 0 for a sprite that paints too little of its square for one. */
    private static final ConcurrentHashMap<Texture, Set<Earth>> earthAsked = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Earth, Integer> earthKnown = new ConcurrentHashMap<>();

    private Flowerbeds() {}

    /** Whether a sprite, by its name, is a bed of roses. The same sheet's flamingos (24..27, 40..43) are none. */
    static boolean is(String name) {
        int place = name == null ? -1 : Sheets.place(name, SHEET);
        return place >= 16 && place <= 23 || place >= 28 && place <= 39 || place >= 44 && place <= 46;
    }

    /** The sprite's dots as read. Null until a frame has been drawn since the sprite was first asked for. */
    static Bed of(Texture texture) {
        Bed bed = known.get(texture);
        if (bed == null && wanted.add(texture)) {
            // A sprite whose colours were read before a bed wanted it is read again.
            SpriteColours.of(texture);
            if (!SpriteColours.unread(texture)) {
                SpriteColours.readAgain(texture);
            }
        }
        return bed;
    }

    /**
     * The OpenGL name of the bed's picture laid as earth (see Flats.FLOOR), as the frame holds the
     * sprite: PENDING until a frame has been drawn since it was first asked for, and 0 for a sprite
     * that paints too little of its square for one.
     */
    static int earth(Texture texture, SpriteGeometry.Frame frame) {
        Earth want = new Earth(texture, frame.originU, frame.originV, frame.scale, frame.left, frame.top, frame.width, frame.height);
        Integer name = earthKnown.get(want);
        if (name != null) {
            return name;
        }
        if (earthAsked.computeIfAbsent(texture, sprite -> ConcurrentHashMap.newKeySet()).add(want)) {
            SpriteColours.of(texture);
            if (!SpriteColours.unread(texture)) {
                SpriteColours.readAgain(texture);
            }
        }
        return PENDING;
    }

    /** Reads the sprite, if a bed asked for it, and makes its pictures laid as earth: see Masonry.read. On the thread that draws. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        if (wanted.remove(texture)) {
            known.put(texture, measure(sheet, width, left, top, right, bottom));
        }
        Set<Earth> wants = earthAsked.get(texture);
        if (wants == null) {
            return;
        }
        int across = Math.max(right - left, 0), down = Math.max(bottom - top, 0);
        ByteBuffer earthen = null;
        for (Earth want : wants) {
            if (earthKnown.containsKey(want)) {
                continue;
            }
            if (earthen == null) {
                earthen = earthen(sheet, width, left, top, right, bottom);
            }
            byte[] dots = Flats.laid(earthen, across, 0, 0, across, down, want.originU, want.originV, want.scale, want.left, want.top, want.width, want.height,
                    0f, 0f, 0f, false);
            earthKnown.put(want, dots == null ? 0 : Flats.picture(Flats.rimmed(dots, false), Flats.WHOLE, Flats.WHOLE));
        }
    }

    /**
     * The rectangle from (left, top) to before (right, bottom) of a sheet width dots wide, held as
     * bytes of red, green, blue and cover, copied with each bloom painted out as earth: EARTH's
     * colour at the bloom's own light against a bloom's usual, so the painting keeps its grain.
     */
    static ByteBuffer earthen(ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        int across = Math.max(right - left, 0), down = Math.max(bottom - top, 0);
        ByteBuffer copy = ByteBuffer.allocate(across * down * 4);
        for (int y = 0; y < down; y++) {
            for (int x = 0; x < across; x++) {
                int from = ((top + y) * width + left + x) * 4, to = (y * across + x) * 4;
                int red = sheet.get(from) & 255, green = sheet.get(from + 1) & 255, blue = sheet.get(from + 2) & 255;
                if (dot(red, green, blue, sheet.get(from + 3) & 255) == BLOOM) {
                    float light = Math.clamp((0.3f * red + 0.55f * green + 0.15f * blue) / 120f, 0.6f, 1.15f);
                    red = Math.min(Math.round(EARTH_RED * light), 255);
                    green = Math.min(Math.round(EARTH_GREEN * light), 255);
                    blue = Math.min(Math.round(EARTH_BLUE * light), 255);
                }
                copy.put(to, (byte) red).put(to + 1, (byte) green).put(to + 2, (byte) blue).put(to + 3, sheet.get(from + 3));
            }
        }
        return copy;
    }

    /** Render thread, as Flats.letGo: gives every picture laid as earth back to the graphics card. A build made after this asks for its pictures again. */
    static void letGo() {
        int count = 0;
        for (Iterator<Integer> names = earthKnown.values().iterator(); names.hasNext();) {
            int name = names.next();
            names.remove();
            if (name != 0) {
                GL11.glDeleteTextures(name);
                count++;
            }
        }
        earthAsked.clear();
        Log.info(count + " pictures of rose beds laid as earth are given back to the graphics card");
    }

    /** The bed of the rectangle from (left, top) to before (right, bottom) of a sheet width dots wide, held as bytes of red, green, blue and cover. */
    static Bed measure(ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        int across = Math.max(right - left, 0), down = Math.max(bottom - top, 0);
        byte[] dots = new byte[across * down];
        for (int y = 0; y < down; y++) {
            for (int x = 0; x < across; x++) {
                int at = ((top + y) * width + left + x) * 4;
                dots[y * across + x] = dot(sheet.get(at) & 255, sheet.get(at + 1) & 255, sheet.get(at + 2) & 255, sheet.get(at + 3) & 255);
            }
        }
        return new Bed(across, down, dots);
    }

    /**
     * What a dot of a bed is by its colour, each channel 0 to 255. A bloom is pink, red or blue:
     * redder than green and no greener than blue, or bright, which the reddish brown of the soil
     * round the bed is not; or bluer than either. A leaf is greener than red. A stone is grey and
     * not dark. Anything else, the soil and the shadows between leaves, is nothing.
     */
    static byte dot(int red, int green, int blue, int cover) {
        if (cover < COVERED) {
            return NOTHING;
        }
        float r = red / 255f, g = green / 255f, b = blue / 255f;
        float light = 0.3f * r + 0.55f * g + 0.15f * b;
        if (r > g + 0.06f && r >= b && (b >= g || light > 0.3f) || b > g + 0.06f && b > r) {
            return BLOOM;
        }
        if (g > r + 0.01f && g >= b) {
            return LEAF;
        }
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 0.07f && light > 0.28f ? STONE : NOTHING;
    }

    /**
     * Whether a bush whose own number is own (0 to 1) is in flower at the time of year: from a
     * day of its own in late spring to one in early autumn, and all summer.
     */
    static boolean flowering(int season, float progression, float own) {
        return switch (season) {
            case Seasons.SPRING -> progression >= FLOWERS_EARLIEST + (FLOWERS_LATEST - FLOWERS_EARLIEST) * own;
            case Seasons.AUTUMN -> progression < FLOWERS_GONE_EARLIEST + (FLOWERS_GONE_LATEST - FLOWERS_GONE_EARLIEST) * own;
            case Seasons.WINTER -> false;
            default -> true;
        };
    }

    /**
     * Grows the bed of the square (x, y) as from away tiles off, from what its sprite's dots are,
     * between lowA..highA and lowB..highB of the square, at the time of year season and progression
     * (Seasons.stepped). A bush stands where more of the dots round its foot are leaves and blooms
     * than stones, and needs a leaf and a bloom among them to take its colours from.
     */
    static void grow(SpriteGeometry geometry, SpriteGeometry.Mesh mesh, SpriteGeometry.Frame frame, Bed bed, int x, int y, float away, float lowA,
            float highA, float lowB, float highB, int season, float progression) {
        float kept = Grass.kept(away * away * 0.8f);
        float broadens = Math.min(1f / (float) Math.sqrt(kept), 2.8f);
        int steps = away < 6f ? 3 : away < 12f ? 2 : 1;
        for (int i = 0; i < SITES; i++) {
            for (int j = 0; j < SITES; j++) {
                int hash = Flora.mix(x * SITES + i, y * SITES + j, 317);
                if (Flora.unit(hash, 0) >= kept) {
                    continue;
                }
                float a = lowA + (highA - lowA) * (i + 0.15f + 0.7f * Flora.unit(hash, 8)) / SITES;
                float b = lowB + (highB - lowB) * (j + 0.15f + 0.7f * Flora.unit(hash, 16)) / SITES;
                int dotX = dotX(bed, frame, SpriteGeometry.frameU(frame, a, b)), dotY = dotY(bed, frame, SpriteGeometry.frameV(frame, a, b, 0f));
                if (!grows(bed, dotX, dotY)) {
                    continue;
                }
                int leaf = near(bed, dotX, dotY, LEAF, hash), bloom = near(bed, dotX, dotY, BLOOM, hash >>> 7);
                if (leaf < 0 || bloom < 0) {
                    continue;
                }
                int more = Flora.mix(hash, i, 11);
                float leafOn = Seasons.leafOn(season, progression, more);
                bush(geometry, mesh, frame, bed, x, y, a, b, leaf, bloom, more, broadens, steps, leafOn, flowering(season, progression, Flora.unit(more, 4)));
            }
        }
        mesh.face(0f, 0f, 1f);
    }

    /** The column of the bed's dot at frame pixel u. */
    static int dotX(Bed bed, SpriteGeometry.Frame frame, float u) {
        return Math.clamp((int) ((u - frame.left) / frame.width * bed.width), 0, bed.width - 1);
    }

    /** The row of the bed's dot at frame pixel v. */
    static int dotY(Bed bed, SpriteGeometry.Frame frame, float v) {
        return Math.clamp((int) ((v - frame.top) / frame.height * bed.height), 0, bed.height - 1);
    }

    /** Whether a bush grows at the dot: more of the dots within LOOK of it are leaves and blooms than are stones, and some are. */
    static boolean grows(Bed bed, int dotX, int dotY) {
        int plants = 0, stones = 0;
        for (int y = Math.max(dotY - LOOK, 0); y <= Math.min(dotY + LOOK, bed.height - 1); y++) {
            for (int x = Math.max(dotX - LOOK, 0); x <= Math.min(dotX + LOOK, bed.width - 1); x++) {
                byte dot = bed.dots[y * bed.width + x];
                if (dot == STONE) {
                    stones++;
                } else if (dot != NOTHING) {
                    plants++;
                }
            }
        }
        return plants > stones;
    }

    /**
     * A dot of the kind within LOOK of the dot, as its place in the bed's dots, or -1 for none: the
     * first of the kind found going round the square of dots from a place the hash picks, so bushes
     * side by side take their colours from different dots.
     */
    static int near(Bed bed, int dotX, int dotY, byte kind, int hash) {
        int side = 2 * LOOK + 1, count = side * side;
        int start = Math.floorMod(hash, count);
        for (int n = 0; n < count; n++) {
            int at = (start + n * 37) % count;
            int x = dotX - LOOK + at % side, y = dotY - LOOK + at / side;
            if (x >= 0 && y >= 0 && x < bed.width && y < bed.height && bed.dots[y * bed.width + x] == kind) {
                return y * bed.width + x;
            }
        }
        return -1;
    }

    /** Where across the texture the bed's dot is. */
    private static float dotU(Bed bed, SpriteGeometry.Frame frame, int dot) {
        return Crops.colourU(frame, frame.left + (dot % bed.width + 0.5f) / bed.width * frame.width);
    }

    /** Where down the texture the bed's dot is. */
    private static float dotV(Bed bed, SpriteGeometry.Frame frame, int dot) {
        return Crops.colourV(frame, frame.top + (dot / bed.width + 0.5f) / bed.height * frame.height);
    }

    /**
     * A rose bush at (a, b) of the square: two or three stems leaning out from its foot, each with
     * leaves held out on alternate sides up it, leafOn of them (Seasons.leafOn), and a flower at
     * its top while it is flowering. Its leaves and stems take the colour of the dot leaf, its
     * flowers that of bloom and of other blooms near it.
     */
    private static void bush(SpriteGeometry geometry, SpriteGeometry.Mesh mesh, SpriteGeometry.Frame frame, Bed bed, int x, int y, float a, float b,
            int leaf, int bloom, int more, float broadens, int steps, float leafOn, boolean flowering) {
        float leafU = dotU(bed, frame, leaf), leafV = dotV(bed, frame, leaf);
        // Cut back for the winter, a bush is short brown canes until it is half in leaf again.
        float high = (LOWEST + (TALLEST - LOWEST) * Flora.unit(more, 0)) * (0.6f + 0.4f * leafOn);
        int cane = leafOn < 0.5f ? Grass.STRAW + Grass.CROP : Grass.BLADE + Grass.CROP;
        float turn = Flora.unit(more, 24) * 6.2831853f;
        int stems = 3 + (more >>> 5 & 1);
        for (int stem = 0; stem < stems; stem++) {
            int own = Flora.mix(more, stem, 43);
            float way = turn + stem * 6.2831853f / stems + 0.6f * Flora.unit(own, 0);
            float dirX = Grass.cos(way), dirY = Grass.sin(way);
            float tall = high * (0.75f + 0.25f * Flora.unit(own, 4));
            geometry.plant(frame, x, y, a, b, leafU, leafV);
            geometry.addStrip(mesh, frame, dirX, dirY, 0f, 0f, 0.15f + 0.35f * Flora.unit(own, 8), 0.1f * Flora.unit(own, 12), tall, 0.014f * broadens, 0f,
                    Math.min(steps, 2), SpriteGeometry.EVEN, cane, Flora.unit(own, 20), 0.8f);
            float topAlong = geometry.endAlong, topUp = geometry.endUp, topLean = geometry.endLean;
            int leaves = steps > 1 ? 8 : 3;
            for (int each = 0; each < leaves; each++) {
                int one = Flora.mix(own, each, 47);
                if (Flora.unit(one, 24) >= leafOn) {
                    continue;
                }
                float share = 0.2f + 0.75f * (each + Flora.unit(one, 0)) / leaves;
                float side = way + each * 2.39996f + 0.5f * (Flora.unit(one, 4) - 0.5f);
                // A leaf grows from the stem where the stem has got to by then: it leans little, so that is its share of the way out.
                float out = topAlong * share;
                geometry.plant(frame, x, y, a + dirX * out, b + dirY * out, leafU, leafV);
                geometry.addStrip(mesh, frame, Grass.cos(side), Grass.sin(side), 0f, topUp * share, 0.9f + 0.5f * Flora.unit(one, 8), 0.5f,
                        0.1f + 0.06f * Flora.unit(one, 12), 0.07f * Math.min(broadens, 2f), 0.5f * (Flora.unit(one, 16) - 0.5f), Math.min(steps, 2),
                        SpriteGeometry.POINTED, Grass.BROAD + Grass.CROP, Flora.unit(one, 20), 0.8f);
            }
            if (!flowering) {
                continue;
            }
            // The flower: one of the blooms near the bush, on the stem's top, faced up and out the way the stem leans.
            int flower = Flora.unit(own, 28) < 0.5f ? bloom : nearOr(bed, bloom, own);
            geometry.plant(frame, x, y, a + dirX * topAlong, b + dirY * topAlong, dotU(bed, frame, flower), dotV(bed, frame, flower));
            float size = (0.1f + 0.05f * Flora.unit(own, 16)) * Math.min(broadens, 1.6f);
            geometry.addStrip(mesh, frame, dirX, dirY, -0.4f * size, topUp - 0.25f * size, Math.max(topLean, 0f) + 0.9f + 0.45f * Flora.unit(own, 24), 0f,
                    size, size, 0f, 1, SpriteGeometry.EVEN, Grass.BLOOM, Flora.unit(own, 14), 0.8f);
        }
    }

    /** Another bloom within LOOK of the dot bloom, picked by the hash, or bloom itself where there is none. */
    private static int nearOr(Bed bed, int bloom, int hash) {
        int other = near(bed, bloom % bed.width, bloom / bed.width, BLOOM, hash);
        return other < 0 ? bloom : other;
    }
}
