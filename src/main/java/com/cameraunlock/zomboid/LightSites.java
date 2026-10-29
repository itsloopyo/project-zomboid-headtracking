package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * Where the light of a chunk build's vertices comes from. A site names what lit a frame: a
 * square's own light and corners (SQUARE), the square across a wall (ALONG), one side of a
 * window or a door (SIDE), the place under a floor that juts out (CEILING) or under a canopy
 * (CANOPY), a block of squares a piece of furniture stands over (BLOCK), the middle of the
 * square a tower stands on (TOWER), or one of those with a paint's tint laid on each corner
 * (TINTED). A build keeps the light of each site its vertices used once, as a record, in its
 * table; a vertex says which record its light is (Mesh.lightSite) and how it takes it: the
 * record's flat light (FLAT), or its corners blended at the shares (a, b) of the square in the
 * weights form emitAt uses (CORNERS) or the lerp form of Trims and the litter (LERP), times a
 * multiplier the builder lays on after the blend. A vertex whose light is a constant, (1, 1, 1)
 * open ground the game has not lit, or a leaf's share of shadow, has no site (MODE_STATIC).
 *
 * A record is the light state a Frame holds, in RECORD floats: red, green, blue, the day a flat
 * vertex of the site takes, the corners' red, green and blue, their unlit shares and their day,
 * then the openness with its kind of light (WorldScene.LIT_STEP), unlitKind, and 1 where a lamp
 * burns behind the glass the site lights (see coded).
 *
 * A record can be worked out again from its site's key, its base and its place alone, from the
 * light read now (Evaluator): that is how a build's page is refreshed when the light changes. A
 * site whose builder took its light from the frame's as it was (the floor of a room not seen
 * into, a door's own side, a block of squares, a tower, a lamp, a tint) names that site, its
 * base, in its key (putOver) or beside it (lamp, tinted), and is worked out from the base's record.
 */
final class LightSites {
    /**
     * Dev and the suite: every vertex is checked as it is added against the light the picture
     * will work out for it from the floats it carries and its site's record (decode), and every
     * frame a vertex takes its site from against that site's record.
     */
    static final boolean TWIN = Boolean.getBoolean("cameraunlock.zomboid.lightTwin");
    /**
     * Dev only: while set, every vertex is built carrying the light its site gives it as a
     * vertex of no site carries its own (MODE_STATIC), so that a picture of each can be told
     * apart. A build so made keeps in its vertices the light it was built in until it is built
     * again: a refresh of its page does not reach them.
     */
    static volatile boolean baked = Boolean.getBoolean("cameraunlock.zomboid.lightBaked");

    /** The site of a frame lit by hand, or never lit: its light is its own, in no table. */
    static final int NO_SITE = -1;
    /** The site of a frame whose light is a constant: its vertices carry their light as it is. */
    static final int STATIC = -2;

    /** How a vertex takes its light: not said yet, a constant, flat, or blended over the corners. */
    static final int NONE = -1, MODE_STATIC = 0, FLAT = 1, CORNERS = 2, LERP = 3;

    static final int RECORD = 32;
    static final int RED = 0, GREEN = 1, BLUE = 2, FLAT_DAY = 3, CORNER_RED = 4, CORNER_GREEN = 8, CORNER_BLUE = 12, CORNER_UNLIT = 16,
            CORNER_DAY = 20, OPEN = 24, UNLIT_KIND = 25, LAMPLIT = 26;

    static final long SQUARE = 1, ALONG = 2, SIDE = 3, CEILING = 4, CANOPY = 5, BLOCK = 6, TOWER = 7, TINTED = 8;
    /** Which square of the window a site's lamp is read from, in a site's key: see lamplit. */
    static final long LAMP_OWN = 1, LAMP_NORTH = 2, LAMP_WEST = 3;
    /** Where a site put over another (putOver) names its base in its key's names: bits 20 to 44. */
    static final int BASE_SHIFT = 20;

    private LightSites() {}

    /** A site's key: its kind, the level it is on, then what names it within the level. */
    static long key(long kind, int level, long names) {
        return kind << 56 | (long) (level + 128 & 0xFF) << 48 | names;
    }

    /** The key's kind. */
    static long kindOf(long key) {
        return key >>> 56;
    }

    /** The level the site of the key is on. */
    static int levelOf(long key) {
        return (int) (key >>> 48 & 0xFF) - 128;
    }

    /** A square of the map as a site's place holds it: see Table.places. */
    static long place(int x, int y, int z) {
        return (long) x << 40 | (y & 0xFFFFFFL) << 16 | z & 0xFFFFL;
    }

    static int placeX(long place) {
        return (int) (place >> 40);
    }

    static int placeY(long place) {
        return (int) (place << 24 >> 40);
    }

    static int placeZ(long place) {
        return (short) place;
    }

    /**
     * Works out a site's record again from its key, as a build's page is refreshed when the light
     * changes: WorldScene.Frame.relight.
     */
    interface Evaluator {
        /**
         * The record of the site of the key, base and place, from the light as it is now, into into
         * at at, with its base's record, where it has one (base not -1), at base * RECORD in records,
         * and a TINTED site's tint at tintAt in tints. False, and into as it was, where no build
         * would put that site now, the light having changed what is built.
         */
        boolean relight(long key, int base, long place, float[] tints, int tintAt, float[] records, float[] into, int at);
    }

    /** The names of a site with a lamp behind it, which burns or not as the square of the window named by which (LAMP_OWN, LAMP_NORTH, LAMP_WEST) has it: see coded. */
    static long lamp(long names, long which) {
        return names | which << 46;
    }

    /** Whether the site of the key has a lamp behind it, burning or not: see lamp. */
    static boolean hasLamp(long key) {
        return (key >>> 46 & 3L) != 0;
    }

    /** The names of a site lit as nothing, the floor of a room the player has not seen into: its openness and kind of light are the site's, its light 0. */
    static long dark(long names) {
        return names | 1L << 45;
    }

    /** The sites of one build, each once, with the record of each. Used on one thread at a time. */
    static final class Table {
        /** The level the sites put next are on. */
        int level;
        long[] keys = new long[256];
        float[] records = new float[256 * RECORD];
        /** For a site worked out from the site its frame had before (putOver, lamp, tinted), that site: -1 for one worked out from its key alone. */
        int[] bases = new int[256];
        /** For a site the game is asked of a square to light (CEILING, CANOPY, a lamp's), that square (place): 0 for any other. */
        long[] places = new long[256];
        /** For a TINTED site, the red, green and blue its base's light was multiplied by. */
        float[] tints = new float[256 * 3];
        int count;
        /** TWIN: works out each site put for the first time again from its key, which must give the record it was put with. Null for no such check. */
        Evaluator evaluator;
        private long[] slotKey = new long[512];
        /** The site in the slot, plus 1: 0 for an empty slot. */
        private int[] slotSite = new int[512];
        private final float[] worked = new float[RECORD];

        void clear() {
            count = 0;
            Arrays.fill(slotSite, 0);
        }

        /**
         * The site of the key on the table's level, which lights the frame as it now is: the frame
         * takes it. A site put for the first time keeps the frame's light as its record, with the
         * day its flat vertices take and whether a lamp burns behind it.
         */
        int put(long kind, long names, SpriteGeometry.Frame frame, float flatDay, boolean lamplit) {
            return put(kind, names, -1, 0, frame, flatDay, lamplit);
        }

        /** put, for a site the game is asked of the square at place (LightSites.place) to light: see places. */
        int putAt(long kind, long names, long place, SpriteGeometry.Frame frame) {
            return put(kind, names, -1, place, frame, 0f, false);
        }

        /**
         * put, for a site lit from the light of another site of the same level, base, which the
         * frame had before: the site's key names its base, and its record is worked out from the base's.
         */
        int putOver(long kind, long names, int base, SpriteGeometry.Frame frame, float flatDay) {
            if (base < 0) {
                throw new IllegalStateException("a light site of kind " + kind + " is laid over the light of a frame of no site's: " + LightSites.describe(frame, flatDay, false));
            }
            if (levelOf(keys[base]) != level) {
                throw new IllegalStateException("a light site of kind " + kind + " on level " + level + " is laid over site " + base + ", " + describe(base));
            }
            return put(kind, names | (long) base << BASE_SHIFT, base, 0, frame, flatDay, false);
        }

        private int put(long kind, long names, int base, long place, SpriteGeometry.Frame frame, float flatDay, boolean lamplit) {
            long key = key(kind, level, names);
            int slot = slot(key);
            int site;
            if (slotSite[slot] != 0) {
                site = slotSite[slot] - 1;
                if (TWIN && !(matches(frame, records, site * RECORD) && records[site * RECORD + FLAT_DAY] == flatDay
                        && records[site * RECORD + LAMPLIT] == (lamplit ? 1f : 0f) && places[site] == place)) {
                    throw new IllegalStateException("site " + Long.toHexString(key) + " lit twice, otherwise: " + describe(site) + " against "
                            + LightSites.describe(frame, flatDay, lamplit));
                }
            } else {
                site = add(key, slot);
                take(frame, records, site * RECORD, flatDay, lamplit);
                bases[site] = base;
                places[site] = place;
                checkWorked(site);
            }
            frame.site = site;
            frame.sites = this;
            return site;
        }

        /** TWIN: throws where the evaluator works out the site's record otherwise than it was put. */
        private void checkWorked(int site) {
            if (!TWIN || evaluator == null) {
                return;
            }
            System.arraycopy(records, site * RECORD, worked, 0, RECORD);
            boolean put = evaluator.relight(keys[site], bases[site], places[site], tints, site * 3, records, worked, 0);
            if (!put || !Arrays.equals(records, site * RECORD, site * RECORD + RECORD, worked, 0, RECORD)) {
                throw new IllegalStateException("site " + Long.toHexString(keys[site]) + " is put as " + describe(site) + ", and worked out again from its key "
                        + (put ? "as " + Arrays.toString(Arrays.copyOf(worked, LAMPLIT + 1)) : "is no site"));
            }
        }

        /**
         * The site of a key another table holds, with the record at at in from: the frame is lit
         * as that record says and takes it. Crowns are made apart from their build (Crowns.mesh),
         * and the record of each site of theirs is the same key's in their build's page, which is
         * where it is refreshed from: neither its base nor its place is written down here.
         */
        int putRecord(long key, float[] from, int at, SpriteGeometry.Frame frame) {
            int slot = slot(key);
            int site;
            if (slotSite[slot] != 0) {
                site = slotSite[slot] - 1;
                if (TWIN && !Arrays.equals(records, site * RECORD, site * RECORD + RECORD, from, at, at + RECORD)) {
                    throw new IllegalStateException("site " + Long.toHexString(key) + " lit twice, otherwise: " + describe(site) + " against "
                            + Arrays.toString(Arrays.copyOfRange(from, at, at + LAMPLIT + 1)));
                }
            } else {
                site = add(key, slot);
                System.arraycopy(from, at, records, site * RECORD, RECORD);
                bases[site] = -1;
                places[site] = 0L;
            }
            give(records, site * RECORD, frame);
            frame.site = site;
            frame.sites = this;
            return site;
        }

        /**
         * The site of the frame's site with a lamp behind it, which burns or not (lamplit) as the
         * square at place (LightSites.place) has it: the site's own square or the one north or west
         * of it, which named by which (LAMP_OWN, LAMP_NORTH, LAMP_WEST) tells the three sites apart.
         * The frame takes it.
         */
        int lamp(SpriteGeometry.Frame frame, long which, long place, boolean lamplit) {
            int base = frame.site;
            if (base < 0) {
                throw new IllegalStateException("a lamp is read for the light of a frame of no site's: " + LightSites.describe(frame, 0f, lamplit));
            }
            long kind = kindOf(keys[base]);
            if (kind != SQUARE && kind != SIDE) {
                throw new IllegalStateException("a lamp is read from the square of site " + describe(base) + ", whose kind names no square");
            }
            long key = keys[base] | which << 46;
            int slot = slot(key);
            int site;
            if (slotSite[slot] != 0) {
                site = slotSite[slot] - 1;
                if (TWIN && !(Arrays.equals(records, site * RECORD, site * RECORD + LAMPLIT, records, base * RECORD, base * RECORD + LAMPLIT)
                        && records[site * RECORD + LAMPLIT] == (lamplit ? 1f : 0f) && places[site] == place)) {
                    throw new IllegalStateException("site " + Long.toHexString(key) + " lit twice, otherwise: " + describe(site) + " against " + describe(base)
                            + (lamplit ? " with the lamp burning" : " with no lamp"));
                }
            } else {
                site = add(key, slot);
                System.arraycopy(records, base * RECORD, records, site * RECORD, RECORD);
                records[site * RECORD + LAMPLIT] = lamplit ? 1f : 0f;
                bases[site] = base;
                places[site] = place;
                checkWorked(site);
            }
            frame.site = site;
            return site;
        }

        /** A site of its own for the frame, lit as its base site times a tint at each corner (FloorBuilder.shade): the frame takes it. */
        int tinted(int base, float red, float green, float blue, SpriteGeometry.Frame frame) {
            if (base < 0) {
                throw new IllegalStateException("a tint is laid on the light of a frame of no site: " + LightSites.describe(frame, 0f, false));
            }
            if (levelOf(keys[base]) != level) {
                throw new IllegalStateException("a tint on level " + level + " is laid on site " + describe(base));
            }
            int site = add(key(TINTED, level, count), slot(key(TINTED, level, count)));
            take(frame, records, site * RECORD, 0f, false);
            bases[site] = base;
            places[site] = 0L;
            tints[site * 3] = red;
            tints[site * 3 + 1] = green;
            tints[site * 3 + 2] = blue;
            checkWorked(site);
            frame.site = site;
            frame.sites = this;
            return site;
        }

        /** The site of a key already put on the table's level, or NO_SITE. */
        int find(long kind, long names) {
            int slot = slot(key(kind, level, names));
            return slotSite[slot] - 1;
        }

        private int add(long key, int slot) {
            if (count == keys.length) {
                keys = Arrays.copyOf(keys, count * 2);
                records = Arrays.copyOf(records, count * 2 * RECORD);
                bases = Arrays.copyOf(bases, count * 2);
                places = Arrays.copyOf(places, count * 2);
                tints = Arrays.copyOf(tints, count * 2 * 3);
            }
            int site = count++;
            keys[site] = key;
            slotKey[slot] = key;
            slotSite[slot] = site + 1;
            if (count * 2 > slotKey.length) {
                long[] oldKeys = slotKey;
                int[] oldSites = slotSite;
                slotKey = new long[oldKeys.length * 2];
                slotSite = new int[oldKeys.length * 2];
                for (int i = 0; i < oldKeys.length; i++) {
                    if (oldSites[i] != 0) {
                        int to = slot(oldKeys[i]);
                        slotKey[to] = oldKeys[i];
                        slotSite[to] = oldSites[i];
                    }
                }
            }
            return site;
        }

        /** The slot of the key, or the empty slot it would go in. */
        private int slot(long key) {
            int mask = slotKey.length - 1;
            int at = (int) (key * 0x9E3779B97F4A7C15L >>> 40) & mask;
            while (slotSite[at] != 0 && slotKey[at] != key) {
                at = at + 1 & mask;
            }
            return at;
        }

        String describe(int site) {
            return Long.toHexString(keys[site]) + " " + Arrays.toString(Arrays.copyOfRange(records, site * RECORD, site * RECORD + LAMPLIT + 1));
        }
    }

    /** Keeps the frame's light as a record at at, with the day a flat vertex of its site takes and whether a lamp burns behind it. */
    static void take(SpriteGeometry.Frame frame, float[] into, int at, float flatDay, boolean lamplit) {
        into[at + RED] = frame.red;
        into[at + GREEN] = frame.green;
        into[at + BLUE] = frame.blue;
        into[at + FLAT_DAY] = flatDay;
        System.arraycopy(frame.cornerRed, 0, into, at + CORNER_RED, 4);
        System.arraycopy(frame.cornerGreen, 0, into, at + CORNER_GREEN, 4);
        System.arraycopy(frame.cornerBlue, 0, into, at + CORNER_BLUE, 4);
        System.arraycopy(frame.cornerUnlit, 0, into, at + CORNER_UNLIT, 4);
        System.arraycopy(frame.cornerDay, 0, into, at + CORNER_DAY, 4);
        into[at + OPEN] = frame.open;
        into[at + UNLIT_KIND] = frame.unlitKind;
        into[at + LAMPLIT] = lamplit ? 1f : 0f;
        Arrays.fill(into, at + LAMPLIT + 1, at + RECORD, 0f);
    }

    /** Lights the frame as the record at at says, all the parts a frame has. Its site is the caller's to say. */
    static void give(float[] records, int at, SpriteGeometry.Frame frame) {
        frame.red = records[at + RED];
        frame.green = records[at + GREEN];
        frame.blue = records[at + BLUE];
        System.arraycopy(records, at + CORNER_RED, frame.cornerRed, 0, 4);
        System.arraycopy(records, at + CORNER_GREEN, frame.cornerGreen, 0, 4);
        System.arraycopy(records, at + CORNER_BLUE, frame.cornerBlue, 0, 4);
        System.arraycopy(records, at + CORNER_UNLIT, frame.cornerUnlit, 0, 4);
        System.arraycopy(records, at + CORNER_DAY, frame.cornerDay, 0, 4);
        frame.open = records[at + OPEN];
        frame.unlitKind = records[at + UNLIT_KIND];
    }

    /** TWIN: throws for a frame with no site, or one lit otherwise than its site's record. A frame lit by a constant (STATIC) passes. */
    static void check(SpriteGeometry.Frame frame) {
        if (frame.site == NO_SITE) {
            throw new IllegalStateException("a frame lit by hand, of no site, lights what is built: " + describe(frame, 0f, false));
        }
        if (frame.site >= 0 && !matches(frame, frame.sites.records, frame.site * RECORD)) {
            throw new IllegalStateException("a frame is lit otherwise than its site " + frame.sites.describe(frame.site) + ": " + describe(frame, 0f, false));
        }
    }

    /** Whether the frame holds the light of the record at at, the parts a frame has: all but the flat vertices' day and the lamp. */
    static boolean matches(SpriteGeometry.Frame frame, float[] records, int at) {
        if (frame.red != records[at + RED] || frame.green != records[at + GREEN] || frame.blue != records[at + BLUE] || frame.open != records[at + OPEN]
                || frame.unlitKind != records[at + UNLIT_KIND]) {
            return false;
        }
        for (int corner = 0; corner < 4; corner++) {
            if (frame.cornerRed[corner] != records[at + CORNER_RED + corner] || frame.cornerGreen[corner] != records[at + CORNER_GREEN + corner]
                    || frame.cornerBlue[corner] != records[at + CORNER_BLUE + corner] || frame.cornerUnlit[corner] != records[at + CORNER_UNLIT + corner]
                    || frame.cornerDay[corner] != records[at + CORNER_DAY + corner]) {
                return false;
            }
        }
        return true;
    }

    static String describe(SpriteGeometry.Frame frame, float flatDay, boolean lamplit) {
        float[] record = new float[RECORD];
        take(frame, record, 0, flatDay, lamplit);
        return "the frame's " + Arrays.toString(Arrays.copyOfRange(record, 0, LAMPLIT + 1));
    }

    /**
     * What the picture works out for a vertex that takes its light from the record at at in the
     * given mode, at the shares (a, b), times the multiplier: red, green, blue, then what it has
     * of a light the game has not given (Mesh.unlit), then of the day (Mesh.day), into out. In
     * the order of operations the builders bake it in, so the same floats come out.
     */
    static void twin(float[] records, int at, int mode, float a, float b, float multRed, float multGreen, float multBlue, float[] out) {
        if (mode == FLAT) {
            out[0] = records[at + RED] * multRed;
            out[1] = records[at + GREEN] * multGreen;
            out[2] = records[at + BLUE] * multBlue;
            out[3] = SpriteGeometry.AS_FACE;
            out[4] = records[at + FLAT_DAY];
            return;
        }
        float w0 = (1f - a) * (1f - b), w1 = a * (1f - b), w2 = a * b, w3 = (1f - a) * b;
        for (int part = 0; part < 3; part++) {
            int c = at + CORNER_RED + 4 * part;
            float blended = mode == LERP
                    ? (records[c] * (1f - a) + records[c + 1] * a) * (1f - b) + (records[c + 3] * (1f - a) + records[c + 2] * a) * b
                    : records[c] * w0 + records[c + 1] * w1 + records[c + 2] * w2 + records[c + 3] * w3;
            out[part] = blended * (part == 0 ? multRed : part == 1 ? multGreen : multBlue);
        }
        int u = at + CORNER_UNLIT, d = at + CORNER_DAY;
        out[3] = records[u] < 0f ? SpriteGeometry.AS_FACE
                : records[at + UNLIT_KIND] * SpriteGeometry.UNLIT_KIND_STEP + records[u] * w0 + records[u + 1] * w1 + records[u + 2] * w2 + records[u + 3] * w3;
        out[4] = records[d] * w0 + records[d + 1] * w1 + records[d + 2] * w2 + records[d + 3] * w3;
    }

    /**
     * Above this a vertex's alpha says how it takes its light: 1 where its square is aloft
     * (SpriteGeometry.MeshSet.aloft), 2 times its mode, 8 where the kind of its light is its
     * record's, and 16 times its material's lit variant: 1 for glass and 2 for a pane, which a
     * lamp burning behind them makes lit glass and a lit pane.
     */
    static final float CODED_STEP = 16384f;

    /**
     * The alpha a vertex of a site carries, from the alpha its light gives it: coded as
     * CODED_STEP says, with the kind of its light left off where the picture takes it from the
     * record, and, on a site with a lamp behind it, its material the one with no lamp.
     */
    static float coded(float alpha, float aloft, int mode, boolean kindFromSite, boolean lampSite) {
        float kind = (float) Math.floor((alpha + 0.5f) / WorldScene.LIT_STEP);
        float within = alpha - WorldScene.LIT_STEP * kind;
        float material = (float) Math.floor((within + 0.5f) / 32f);
        float base = material, variant = 0f;
        if (lampSite) {
            base = material == WallBuilder.LIT_GLASS ? WorldScene.GLASS : material == WorldScene.LIT_PANE ? WorldScene.PANE : material;
            variant = base == WorldScene.GLASS ? 1f : base == WorldScene.PANE ? 2f : 0f;
        }
        return within + 32f * (base - material) + (kindFromSite ? 0f : WorldScene.LIT_STEP * kind)
                + CODED_STEP * (aloft + 2f * mode + (kindFromSite ? 8f : 0f) + 16f * variant);
    }

    /**
     * The light the picture works out for the vertex whose floats start at at, as
     * PipelineShaders.WORLD_VERTEX does before the kinds of light the game has not given: from
     * the floats alone for a vertex of no site, and from its record in records (a build's page,
     * counted from the page's start) for one of a site. Into out: red, green, blue, what it has of
     * a light the game has not given, of the day, and its alpha with the kind of its light and its
     * material, and with no code over CODED_STEP.
     */
    static void decode(float[] vertices, int at, float[] records, float[] out) {
        float alpha = vertices[at + 8];
        int code = (int) Math.floor((alpha + 0.5f) / CODED_STEP);
        float bare = alpha - CODED_STEP * code;
        int mode = code >> 1 & 3;
        if (mode == MODE_STATIC) {
            out[0] = vertices[at + 5];
            out[1] = vertices[at + 6];
            out[2] = vertices[at + 7];
            out[3] = vertices[at + SpriteGeometry.UNLIT_AT];
            out[4] = vertices[at + SpriteGeometry.DAY_AT];
            out[5] = bare;
            return;
        }
        int record = (int) vertices[at + SpriteGeometry.RECORD_AT] * RECORD;
        twin(records, record, mode, vertices[at + SpriteGeometry.A_AT], vertices[at + SpriteGeometry.B_AT], vertices[at + 5], vertices[at + 6], vertices[at + 7], out);
        if ((code & 8) != 0) {
            bare += WorldScene.LIT_STEP * kindOf(records[record + OPEN]);
        }
        int variant = code >> 4;
        if (variant > 0 && records[record + LAMPLIT] > 0.5f) {
            bare += variant == 1 ? 32f * (WallBuilder.LIT_GLASS - WorldScene.GLASS) : 32f * (WorldScene.LIT_PANE - WorldScene.PANE);
        }
        out[5] = bare;
    }

    /** The kind of light an openness (WorldScene.openOf) or a vertex's alpha carries, as the picture reads it. */
    static int kindOf(float open) {
        return (int) Math.floor((open + 0.5f) / WorldScene.LIT_STEP);
    }
}
