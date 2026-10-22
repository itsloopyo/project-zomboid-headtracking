package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import zombie.core.textures.Texture;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * The roofs of the loaded map, each read whole.
 *
 * A roof tile's sprite says how the tile lies, a little wrongly, and RoofFit puts a roof's
 * tiles on the planes they make up. That takes the whole roof: every tile joined to the one
 * asked about is read, they are fitted together, and what each came to is kept, since every
 * tile of the roof asks each time its chunk is built. Read again every so often, since roofs
 * are built and burnt.
 */
final class Roofs {
    /** Two corner heights of a roof tile nearer than this, in levels, are one height: the tile is flat. */
    static final float FLAT = 0.03f;
    /** A level sheet of roof painted less than this over its floor, in levels, lies under a slope and is no tile: see flatTop. */
    private static final float FLAT_TOP_LEAST = 0.2f;
    /** How far inside a roof tile's corner its sprite is looked at, in frame pixels at tile scale 1. */
    private static final float CORNER_INSET = 4f;
    /** No roof in the game climbs more than this over a tile, in levels: a slope takes three tiles to a floor. */
    static final float STEEPEST = 0.55f;
    /** A roof sprite narrower than this, in unscaled pixels, is a fitting on the roof and not a tile of it. */
    static final float WHOLE_TILE_WIDE = 50f;
    /** This many rows of a sprite with nothing painted on them end what is painted above them, in frame pixels at tile scale 1. */
    private static final float PAINT_GAP = 3f;
    /** The top or the foot of a sprite further than this from over its middle, in frame pixels at tile scale 1, is not the corner drawn there: see cut. A corner at the sprite's end is 32 from it. */
    private static final float CUT_ASIDE = 20f;
    /** How far along the line from the north-west corner of a square to the south-east a tile with its north-west corner cut away is looked at for the half that is gone, and for the half that is there. */
    private static final float CUT_AWAY = 0.2f, CUT_KEPT = 0.6f;
    /** How wide the sprite of half a tile is, in unscaled pixels, and how far in from its frame's left edge the east half starts at the least. */
    private static final float HALF_NARROWEST = 47f, HALF_WIDEST = 54f, HALF_INSET = 8f;
    private static final int KEPT = 600, RETRIED = 30;
    /** The most tiles read as one roof. */
    private static final int MOST = 4000;
    /** How many squares past a slope's ridge a roof with no tile between is looked for, as the same roof: see beyondRidge. */
    private static final int RIDGE_GAP = 3;

    /** One roof: what it is covered in. */
    static final class Roof {
        /** The sprite most of its tiles show, or null for a roof with no such sprite the picture can take a colour from. */
        Texture finish;
        /** A place on that sprite's texture that is painted. */
        float u, v;
        private int stamp;
        private boolean whole = true;
        /** A tile of it on the map, which a reading of it can start from, and the frame it was last read again from there for a chunk that holds only what was made of it. */
        private int x, y, z, revived;
        /**
         * How many frames it is kept before it is read again: from half of KEPT up to all of
         * it, by where it is, so that the roofs of a street read in one frame do not all fall
         * due in one frame ever after.
         */
        private int kept;
        /**
         * For tend: everything kept of the roof, the box of chunks it lies on, everything
         * it came out as in one number, and what ChunkMarks had counted for the box and
         * which chunks the map had loaded when it was read.
         */
        private final ArrayList<Laid> tiles = new ArrayList<>(), backs = new ArrayList<>();
        private final ArrayList<Capped> caps = new ArrayList<>();
        private final ArrayList<Part> parts = new ArrayList<>();
        private int minWx, minWy, maxWx, maxWy, fromZ, toZ;
        private int structures, loaded;
        /** Whether tend has it in its list. */
        private boolean listed;
    }

    /**
     * What of a roof one chunk's build goes by, summed into one number: every tile of the
     * roof on the chunk or within a tile of it, and the caps whose middles lie on it.
     */
    private static final class Part {
        final int wx, wy;
        long print;

        Part(int wx, int wy) {
            this.wx = wx;
            this.wy = wy;
        }
    }

    /** One tile of a roof as the fit left it. */
    static final class Laid {
        final RoofFit.Tile tile;
        /** The sprite the tile was read from, or null for a tile of a slope the map leaves out. */
        final Texture texture;
        final Roof roof;
        /** Whether it is a sheet behind a ridge (RoofFit.behindRidges): kept so its square is not read again, and no tile. */
        final boolean behind;

        Laid(RoofFit.Tile tile, Texture texture, Roof roof) {
            this(tile, texture, roof, false);
        }

        Laid(RoofFit.Tile tile, Texture texture, Roof roof, boolean behind) {
            this.tile = tile;
            this.texture = texture;
            this.roof = roof;
            this.behind = behind;
        }
    }

    private static final HashMap<Long, Laid> laid = new HashMap<>();
    /**
     * The tiles of the slopes the map leaves out, by square and by chunk. A chunk that holds
     * nothing else of its roof still has to find them, so they outlive laid, and each goes when
     * its roof has not been read again for MADE_KEPT frames.
     */
    private static final HashMap<Long, Laid> made = new HashMap<>();
    private static final HashMap<Long, ArrayList<Laid>> madeByChunk = new HashMap<>();

    /** One length of the cap along a ridge or down a hip, and the roof it is on. */
    static final class Capped {
        final RoofFit.Cap cap;
        final Roof roof;

        Capped(RoofFit.Cap cap, Roof roof) {
            this.cap = cap;
            this.roof = roof;
        }
    }

    /** The caps of the roofs read, by where each is and by the chunk its middle is in: kept and dropped as the made tiles are. */
    private static final HashMap<Long, Capped> capped = new HashMap<>();
    private static final HashMap<Long, ArrayList<Capped>> cappedByChunk = new HashMap<>();
    private static final int MADE_KEPT = 3 * KEPT;
    /** How many levels over a place are looked through for what roofs it. */
    private static final int LOOKED_ABOVE = 3;
    private static int cleared;
    /** Every roof read while the game tells of its changes, each until it is read again or its ground is unloaded: see tend. */
    private static final ArrayList<Roof> roofs = new ArrayList<>();
    private static final ArrayList<Roof> replaced = new ArrayList<>();
    private static final ArrayList<Part> before = new ArrayList<>();
    private static int tended, loadedNow;
    private static final int TENDED_A_FRAME = 4;
    private static final SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
    private static final ArrayList<Laid> reading = new ArrayList<>();
    private static final ArrayList<RoofFit.Tile> tiles = new ArrayList<>();
    private static final ArrayList<Texture> shown = new ArrayList<>();

    private Roofs() {}

    /** For a map loaded anew. */
    static void forget() {
        laid.clear();
        made.clear();
        madeByChunk.clear();
        capped.clear();
        cappedByChunk.clear();
        roofs.clear();
    }

    /**
     * Logic thread, once a frame while the game tells which chunks it changes (ChunkMarks).
     * A roof is otherwise read again every so many frames by whichever chunk next asks for a
     * tile of it, and a chunk nothing has been said of asks for nothing. So a few roofs a
     * frame are looked at here in turn: one whose chunks the game has marked for an object
     * since it was read is read again, and so is one read while some of the ground round it
     * was not loaded, once the map has loaded more. A roof that comes out otherwise has the
     * chunks it lies on told (see read). One neither due nor waiting to be is kept as fresh
     * as if it had been read, and one whose ground the map has unloaded is let go.
     */
    static void tend(IsoCell cell, int now, float scale, int loaded) {
        loadedNow = loaded;
        for (int n = 0; n < TENDED_A_FRAME && !roofs.isEmpty(); n++) {
            if (tended >= roofs.size()) {
                tended = 0;
            }
            Roof roof = roofs.get(tended);
            if (cell.getGridSquare(roof.x, roof.y, 0) == null) {
                drop(roof);
                continue;
            }
            // One read short of loaded ground waits out its frames from when it was read, however often it comes round here.
            boolean waits = !roof.whole && roof.loaded != loaded;
            boolean due = roof.whole ? ChunkMarks.structuresIn(roof.minWx, roof.minWy, roof.maxWx, roof.maxWy, roof.fromZ, roof.toZ) != roof.structures : waits && now - roof.stamp >= RETRIED;
            if (!due) {
                if (!waits) {
                    roof.stamp = now;
                }
                tended++;
                continue;
            }
            read(cell, roof.x, roof.y, roof.z, now, scale);
            if (roof.listed) {
                // No tile is left where it was read from, so nothing took its place.
                drop(roof);
                for (Part part : roof.parts) {
                    ChunkMarks.tell(part.wx, part.wy);
                }
            }
        }
    }

    /** Lets go of everything kept of a roof. */
    private static void drop(Roof roof) {
        roofs.remove(roof);
        roof.listed = false;
        for (Laid one : roof.tiles) {
            laid.remove(RoofFit.key(one.tile.x, one.tile.y, one.tile.z), one);
        }
        for (Laid back : roof.backs) {
            made.remove(keyOf(back.tile), back);
            long chunk = RoofFit.key(Math.floorDiv(back.tile.x, WorldScene.CHUNK_TILES), Math.floorDiv(back.tile.y, WorldScene.CHUNK_TILES), 0);
            ArrayList<Laid> held = madeByChunk.get(chunk);
            if (held != null) {
                held.remove(back);
            }
        }
        for (Capped cap : roof.caps) {
            capped.remove(keyOf(cap.cap), cap);
            ArrayList<Capped> held = cappedByChunk.get(chunkOf(cap.cap));
            if (held != null) {
                held.remove(cap);
            }
        }
    }

    private static long mix(long sum, long value) {
        return (sum ^ value) * 0x100000001B3L;
    }

    private static long mix(long sum, RoofFit.Tile tile) {
        sum = mix(mix(mix(mix(sum, tile.x), tile.y), tile.z), tile.axis | tile.fitAxis << 4 | tile.crease << 8 | tile.own << 12 | tile.half << 16 | tile.peakAxis << 20
                | (tile.sure ? 1 << 24 : 0) | (tile.fitted ? 1 << 25 : 0) | (tile.fitting ? 1 << 26 : 0) | (tile.shaped ? 1 << 27 : 0) | (tile.carried ? 1 << 28 : 0));
        sum = mix(mix(mix(mix(sum, Float.floatToRawIntBits(tile.low)), Float.floatToRawIntBits(tile.high)), Float.floatToRawIntBits(tile.fitLow)), Float.floatToRawIntBits(tile.fitHigh));
        sum = mix(mix(sum, Float.floatToRawIntBits(tile.halfEdge)), Float.floatToRawIntBits(tile.peak));
        sum = mix(mix(sum, Float.floatToRawIntBits(tile.peakBefore)), Float.floatToRawIntBits(tile.peakAfter));
        for (float corner : tile.corners) {
            sum = mix(sum, Float.floatToRawIntBits(corner));
        }
        return sum;
    }

    /**
     * Sets a roof just read beside what was kept of the same tiles before, for tend: notes
     * what of it each chunk's build goes by, lets go of every roof it has taken a tile from,
     * and tells each chunk for which that has come out otherwise than those roofs had it.
     * A roof read for the first time tells the chunks it lies on too: one that holds only
     * its far slope or its ridge may have been built before any tile of it was asked for.
     */
    private static void settle(Roof roof) {
        long finish = mix(mix(mix(0x9E3779B97F4A7C15L, roof.finish == null ? 0 : roof.finish.getID()), Float.floatToRawIntBits(roof.u)), Float.floatToRawIntBits(roof.v));
        for (Laid one : roof.tiles) {
            note(roof, one.tile.x, one.tile.y, mix(mix(finish, one.tile), one.texture.getID()));
        }
        for (Laid back : roof.backs) {
            note(roof, back.tile.x, back.tile.y, mix(finish, back.tile));
        }
        for (Capped cap : roof.caps) {
            RoofFit.Cap line = cap.cap;
            long print = mix(mix(mix(mix(finish, Float.floatToRawIntBits(line.x0)), Float.floatToRawIntBits(line.y0)), Float.floatToRawIntBits(line.high0)), Float.floatToRawIntBits(line.x1));
            print = mix(mix(mix(print, Float.floatToRawIntBits(line.y1)), Float.floatToRawIntBits(line.high1)), Float.floatToRawIntBits(line.fall));
            int middleX = (int) Math.floor((line.x0 + line.x1) / 2f), middleY = (int) Math.floor((line.y0 + line.y1) / 2f);
            part(roof.parts, Math.floorDiv(middleX, WorldScene.CHUNK_TILES), Math.floorDiv(middleY, WorldScene.CHUNK_TILES)).print += print;
        }
        // A reading takes a tile on the level above or below one it has: a mark on those levels may change it.
        roof.fromZ = roof.z - 1;
        roof.toZ = roof.z + 1;
        for (Laid one : roof.tiles) {
            roof.fromZ = Math.min(roof.fromZ, one.tile.z - 1);
            roof.toZ = Math.max(roof.toZ, one.tile.z + 1);
        }
        roof.minWx = roof.minWy = Integer.MAX_VALUE;
        roof.maxWx = roof.maxWy = Integer.MIN_VALUE;
        for (Part part : roof.parts) {
            roof.minWx = Math.min(roof.minWx, part.wx);
            roof.minWy = Math.min(roof.minWy, part.wy);
            roof.maxWx = Math.max(roof.maxWx, part.wx);
            roof.maxWy = Math.max(roof.maxWy, part.wy);
        }
        roof.structures = ChunkMarks.structuresIn(roof.minWx, roof.minWy, roof.maxWx, roof.maxWy, roof.fromZ, roof.toZ);
        roof.loaded = loadedNow;
        before.clear();
        for (Roof old : replaced) {
            drop(old);
            for (Part part : old.parts) {
                part(before, part.wx, part.wy).print += part.print;
            }
        }
        for (Part part : roof.parts) {
            if (part(before, part.wx, part.wy).print != part.print) {
                ChunkMarks.tell(part.wx, part.wy);
            }
        }
        for (Part was : before) {
            if (was.print != 0L && part(roof.parts, was.wx, was.wy).print == 0L) {
                ChunkMarks.tell(was.wx, was.wy);
            }
        }
        roofs.add(roof);
        roof.listed = true;
    }

    /** Counts a tile at (x, y) into what each chunk within a tile of it goes by: a build asks what roofs the squares beside its own. */
    private static void note(Roof roof, int x, int y, long print) {
        int fromWx = Math.floorDiv(x - 1, WorldScene.CHUNK_TILES), toWx = Math.floorDiv(x + 1, WorldScene.CHUNK_TILES);
        int fromWy = Math.floorDiv(y - 1, WorldScene.CHUNK_TILES), toWy = Math.floorDiv(y + 1, WorldScene.CHUNK_TILES);
        for (int wx = fromWx; wx <= toWx; wx++) {
            for (int wy = fromWy; wy <= toWy; wy++) {
                part(roof.parts, wx, wy).print += print;
            }
        }
    }

    /** The part for a chunk among parts, put there with nothing counted into it if it has none. */
    private static Part part(ArrayList<Part> parts, int wx, int wy) {
        for (int i = 0; i < parts.size(); i++) {
            Part part = parts.get(i);
            if (part.wx == wx && part.wy == wy) {
                return part;
            }
        }
        Part part = new Part(wx, wy);
        parts.add(part);
        return part;
    }

    /** The tile of roof on the square as its whole roof was fitted, or null for a square with no roof tile. */
    static Laid at(IsoCell cell, int x, int y, int z, int now, float scale) {
        if (now - cleared >= KEPT) {
            cleared = now;
            forgetStale(now);
        }
        long key = RoofFit.key(x, y, z);
        Laid known = laid.get(key);
        if (known == null || due(known.roof, now)) {
            // A tile that has gone from the map is not read back in.
            laid.remove(key);
            read(cell, x, y, z, now, scale);
            known = laid.get(key);
        }
        return known == null || known.behind ? null : known;
    }

    private static boolean due(Roof roof, int now) {
        return now - roof.stamp >= (roof.whole ? roof.kept : RETRIED);
    }

    /**
     * Reads a roof again from its own tile when it is due and nothing has asked for a tile of
     * it: a chunk that holds only its far slope or its ridge asks for no tile, and with the
     * chunk its tiles are on left unbuilt, out of the view, the slope would be dropped as
     * that of a roof long gone. True when it was read, and what is held of it replaced.
     */
    private static boolean revived(IsoCell cell, Roof roof, int now, float scale) {
        // No oftener than a roof short of tiles is tried again: a reading that comes back short leaves what is held as it was, and still due.
        if (!due(roof, now) || now - roof.revived < RETRIED) {
            return false;
        }
        roof.revived = now;
        read(cell, roof.x, roof.y, roof.z, now, scale);
        return true;
    }

    /**
     * Drops the tiles, the made tiles and the caps of roofs not read again in MADE_KEPT frames. Each is
     * already taken for gone by whoever asks for it, but only a chunk being built asks, so
     * without this a roof the player has left behind is held for the rest of the game.
     */
    private static void forgetStale(int now) {
        laid.values().removeIf(one -> now - one.roof.stamp > MADE_KEPT);
        made.values().removeIf(back -> now - back.roof.stamp > MADE_KEPT);
        capped.values().removeIf(cap -> now - cap.roof.stamp > MADE_KEPT);
        // A chunk left with none is dropped with them, or a long drive leaves a list for every chunk passed.
        madeByChunk.values().removeIf(held -> {
            held.removeIf(back -> now - back.roof.stamp > MADE_KEPT);
            return held.isEmpty();
        });
        cappedByChunk.values().removeIf(held -> {
            held.removeIf(cap -> now - cap.roof.stamp > MADE_KEPT);
            return held.isEmpty();
        });
    }

    private static void read(IsoCell cell, int x, int y, int z, int now, float scale) {
        Roof roof = new Roof();
        roof.stamp = now;
        roof.x = x;
        roof.y = y;
        roof.z = z;
        roof.kept = KEPT / 2 + Math.floorMod(x * 31 + y * 17 + z, KEPT / 2);
        reading.clear();
        HashSet<Long> seen = new HashSet<>();
        if (!(scale > 0f)) {
            throw new IllegalArgumentException("a roof at " + x + ", " + y + " on level " + z + " was to be read at tile scale " + scale);
        }
        frame.scale = scale;
        take(cell, x, y, z, roof, seen);
        if (reading.isEmpty()) {
            return;
        }
        for (int next = 0; next < reading.size() && reading.size() < MOST; next++) {
            RoofFit.Tile tile = reading.get(next).tile;
            for (int side = 0; side < 4; side++) {
                int dx = side == 0 ? -1 : side == 1 ? 1 : 0, dy = side == 2 ? -1 : side == 3 ? 1 : 0;
                roof.whole &= cell.getGridSquare(tile.x + dx, tile.y + dy, 0) != null;
                for (int dz = -1; dz <= 1; dz++) {
                    take(cell, tile.x + dx, tile.y + dy, tile.z + dz, roof, seen);
                }
            }
            beyondRidge(cell, tile, roof, seen);
        }
        // In the order of their places and not of the reading, which starts at whichever tile asked:
        // where two tiles are as good as each other the fit takes the first, and a roof is to come
        // out the same whoever asks.
        reading.sort(Comparator.comparingLong(one -> RoofFit.key(one.tile.x, one.tile.y, one.tile.z)));
        tiles.clear();
        for (Laid one : reading) {
            tiles.add(one.tile);
        }
        // A sheet behind a ridge is no tile of what shows: the far slope is made over it, as over a square with none.
        List<RoofFit.Tile> behind = RoofFit.behindRidges(tiles);
        if (!behind.isEmpty()) {
            reading.replaceAll(one -> behind.contains(one.tile) ? new Laid(one.tile, one.texture, roof, true) : one);
        }
        tiles.clear();
        shown.clear();
        WallSides.Votes votes = new WallSides.Votes();
        for (Laid one : reading) {
            if (one.behind) {
                continue;
            }
            tiles.add(one.tile);
            if (one.tile.sure && one.tile.axis != RoofFit.LEVEL && one.texture.getWidth() > WHOLE_TILE_WIDE * scale) {
                int index = shown.indexOf(one.texture);
                if (index < 0) {
                    index = shown.size();
                    shown.add(one.texture);
                }
                votes.add(index);
            }
        }
        RoofFit.fit(tiles);
        if (!votes.isEmpty()) {
            paintedPlace(shown.get(votes.commonest()), roof);
        }
        replaced.clear();
        for (Laid one : reading) {
            Laid was = laid.put(RoofFit.key(one.tile.x, one.tile.y, one.tile.z), one);
            if (was != null && was.roof.listed && !replaced.contains(was.roof)) {
                replaced.add(was.roof);
            }
        }
        roof.tiles.addAll(reading);
        // A roof read before all of it was loaded is short of rows, and what would be carried
        // over its ridge then is not the roof's far side.
        if (roof.whole) {
            int highest = 0;
            for (RoofFit.Tile tile : tiles) {
                highest = Math.max(highest, tile.z);
            }
            HashSet<Long> behindKeys = new HashSet<>();
            for (RoofFit.Tile sheet : behind) {
                behindKeys.add(RoofFit.key(sheet.x, sheet.y, sheet.z));
            }
            List<RoofFit.Tile> backs = RoofFit.backs(tiles, site(cell, highest, behindKeys));
            RoofFit.peaks(tiles, backs);
            for (RoofFit.Tile back : backs) {
                keep(new Laid(back, null, roof));
            }
            for (RoofFit.Cap cap : RoofFit.caps(tiles, backs)) {
                keep(new Capped(cap, roof));
            }
            List<RoofFit.Tile> valleys = RoofFit.valleys(tiles, backs);
            for (RoofFit.Tile over : valleys) {
                keep(new Laid(over, null, roof));
            }
            for (RoofFit.Cap cap : RoofFit.capsOf(valleys)) {
                keep(new Capped(cap, roof));
            }
        }
        if (ChunkMarks.live) {
            settle(roof);
        }
    }

    /** The key of the chunk a cap's middle lies in. */
    private static long chunkOf(RoofFit.Cap cap) {
        int middleX = (int) Math.floor((cap.x0 + cap.x1) / 2f), middleY = (int) Math.floor((cap.y0 + cap.y1) / 2f);
        return RoofFit.key(Math.floorDiv(middleX, WorldScene.CHUNK_TILES), Math.floorDiv(middleY, WorldScene.CHUNK_TILES), 0);
    }

    /** What a cap is kept under. */
    private static long keyOf(RoofFit.Cap cap) {
        float middleX = (cap.x0 + cap.x1) / 2f, middleY = (cap.y0 + cap.y1) / 2f;
        // Twice the middle, rounded, and the way the cap runs name one: a level tile with slopes
        // up to it on all four sides has two caps that cross at its middle.
        int runs = cap.x0 == cap.x1 ? 1 : cap.y0 == cap.y1 ? 2 : 3;
        return RoofFit.key(Math.round(2f * middleX), Math.round(2f * middleY), runs);
    }

    private static void keep(Capped cap) {
        cap.roof.caps.add(cap);
        long key = keyOf(cap.cap);
        long chunk = chunkOf(cap.cap);
        Capped was = capped.put(key, cap);
        if (was != null) {
            // The cap it takes the place of has its middle within half a tile, which may be over a chunk's edge.
            ArrayList<Capped> held = cappedByChunk.get(chunkOf(was.cap));
            if (held != null) {
                held.remove(was);
            }
        }
        cappedByChunk.computeIfAbsent(chunk, unused -> new ArrayList<>()).add(cap);
    }

    /** The caps of ridges and hips whose middles lie in a chunk, or null for a chunk with none. */
    static ArrayList<Capped> cappedIn(IsoCell cell, int chunkX, int chunkY, int now, float scale) {
        ArrayList<Capped> held = cappedByChunk.get(RoofFit.key(chunkX, chunkY, 0));
        if (held == null) {
            return null;
        }
        for (int i = 0; i < held.size(); i++) {
            if (revived(cell, held.get(i).roof, now, scale)) {
                i = -1;
            }
        }
        for (int i = held.size() - 1; i >= 0; i--) {
            Capped cap = held.get(i);
            if (now - cap.roof.stamp > MADE_KEPT) {
                held.remove(i);
                capped.remove(keyOf(cap.cap), cap);
            }
        }
        return held;
    }

    /**
     * What a made tile is kept under. A slope carried over the square of another is kept apart
     * from the tile that is the roof of the square, by the way it runs: both may lie on one square.
     */
    private static long keyOf(RoofFit.Tile tile) {
        int kind = !tile.carried ? 0 : tile.peak > 0f ? 3 : tile.fitAxis;
        return RoofFit.key(tile.x, tile.y, kind << 6 | tile.z);
    }

    private static void keep(Laid back) {
        back.roof.backs.add(back);
        long key = keyOf(back.tile);
        long chunk = RoofFit.key(Math.floorDiv(back.tile.x, WorldScene.CHUNK_TILES), Math.floorDiv(back.tile.y, WorldScene.CHUNK_TILES), 0);
        ArrayList<Laid> held = madeByChunk.computeIfAbsent(chunk, unused -> new ArrayList<>());
        Laid was = made.put(key, back);
        if (was != null) {
            held.remove(was);
        }
        held.add(back);
    }

    /** The tile of a slope the map leaves out that lies on the square, or null. */
    static Laid madeAt(int x, int y, int z, int now) {
        Laid back = made.get(RoofFit.key(x, y, z));
        return back == null || now - back.roof.stamp > MADE_KEPT ? null : back;
    }

    /** The tiles of the slopes the map leaves out that lie in a chunk, or null for a chunk with none. */
    static ArrayList<Laid> madeIn(IsoCell cell, int chunkX, int chunkY, int now, float scale) {
        ArrayList<Laid> held = madeByChunk.get(RoofFit.key(chunkX, chunkY, 0));
        if (held == null) {
            return null;
        }
        for (int i = 0; i < held.size(); i++) {
            if (revived(cell, held.get(i).roof, now, scale)) {
                i = -1;
            }
        }
        for (int i = held.size() - 1; i >= 0; i--) {
            Laid back = held.get(i);
            if (now - back.roof.stamp > MADE_KEPT) {
                held.remove(i);
                made.remove(keyOf(back.tile), back);
            }
        }
        return held;
    }

    /**
     * The crown a slope's far side comes down onto, a square or two past its ridge in the same
     * building, is of the same roof though the map leaves the squares between with no tile: a
     * cross gable before the crown of a hip cut short (10402..10411 x 12658..12669) was two roofs,
     * and the gable's far side, made with no crown in its roof, stood a third of a level over the
     * crown and ran on at the ridge's height to the north wall where the crown ended short of it.
     * Only a sheet lower than the ridge is taken, and only across squares with no roofing.
     */
    private static void beyondRidge(IsoCell cell, RoofFit.Tile tile, Roof roof, HashSet<Long> seen) {
        if (!tile.sure || tile.fitting || tile.axis != RoofFit.ALONG_X && tile.axis != RoofFit.ALONG_Y || Math.abs(tile.high - tile.low) < RoofFit.SAME) {
            return;
        }
        int up = tile.low > tile.high ? -1 : 1;
        int dx = tile.axis == RoofFit.ALONG_X ? up : 0, dy = tile.axis == RoofFit.ALONG_Y ? up : 0;
        float ridge = Math.max(tile.low, tile.high);
        IsoGridSquare under = cell.getGridSquare(tile.x, tile.y, 0);
        if (under == null || under.getBuilding() == null) {
            return;
        }
        for (int step = 1; step <= RIDGE_GAP; step++) {
            int x = tile.x + step * dx, y = tile.y + step * dy;
            IsoGridSquare ground = cell.getGridSquare(x, y, 0), square = cell.getGridSquare(x, y, tile.z);
            if (ground == null || ground.getBuilding() != under.getBuilding() || square == null) {
                return;
            }
            if (!roofing(cell, square)) {
                continue;
            }
            if (step == 1) {
                return;
            }
            long key = RoofFit.key(x, y, tile.z);
            int before = reading.size();
            take(cell, x, y, tile.z, roof, seen);
            if (reading.size() > before) {
                RoofFit.Tile found = reading.get(before).tile;
                if (!found.sheet || found.low >= ridge - RoofFit.SAME) {
                    reading.remove(before);
                    seen.remove(key);
                }
            }
            return;
        }
    }

    /**
     * The building under a roof, as RoofFit.backs asks of it. behind holds the squares of the
     * sheets behind a ridge (RoofFit.behindRidges): the far slope is made over them, so they are
     * no roofing of the game's that a made slope would stand over.
     */
    private static RoofFit.Site site(IsoCell cell, int highest, HashSet<Long> behind) {
        return new RoofFit.Site() {
            private boolean roofed(IsoGridSquare square, int x, int y, int z) {
                return roofing(cell, square) && !behind.contains(RoofFit.key(x, y, z));
            }

            @Override
            public boolean stands(int x, int y, int z) {
                boolean built = false;
                for (int level = 0; level <= highest; level++) {
                    IsoGridSquare square = cell.getGridSquare(x, y, level);
                    if (square == null) {
                        continue;
                    }
                    built |= square.getBuilding() != null;
                    // A floor in the open air is a flat roof, and what is under it stops there.
                    if (level > 0 && level < z && square.getRoomID() == -1L && (square.getFloor() != null || roofed(square, x, y, level))) {
                        return false;
                    }
                }
                return built;
            }

            @Override
            public boolean floored(int x, int y, int z) {
                IsoGridSquare square = cell.getGridSquare(x, y, z);
                return square != null && (square.getFloor() != null || roofed(square, x, y, z));
            }

            @Override
            public boolean taken(int x, int y, int z, float roof) {
                IsoGridSquare square = cell.getGridSquare(x, y, z);
                if (square != null) {
                    // The map's ceiling over the rooms below lies on the level the roof's eaves stand on, under every
                    // slope: it is no floor the made roof would stand over. A floor of any other sheet is a terrace.
                    IsoObject floor = square.getFloor();
                    boolean terrace = floor != null && !(floor.getSprite() != null && ceiling(floor.getSprite().getName()));
                    if (square.getRoomID() != -1L || terrace || roofed(square, x, y, z)) {
                        return true;
                    }
                }
                for (int above = 1; above <= LOOKED_ABOVE; above++) {
                    int level = z + above;
                    if (floored(x, y, level) && !(level <= roof + RoofFit.SAME && loftCeiling(cell, cell.getGridSquare(x, y, level)))) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    /** Whether a floor's sprite is one of the map's ceilings, which it lays over rooms on the level above them. */
    static boolean ceiling(String spriteName) {
        return spriteName != null && spriteName.startsWith("ceilings_");
    }

    /** Whether a square that is floored is only floored: no room, and none of the game's roofing on it. */
    private static boolean loftCeiling(IsoCell cell, IsoGridSquare square) {
        return square.getRoomID() == -1L && !roofing(cell, square);
    }

    /**
     * Whether any of the game's roofing lies on the square: a tile of a roof, or one of its caps
     * and edgings. A building's own sheet can hold its roof (a trailer's), and its tiles are
     * known by the game marking them roof.
     */
    static boolean roofing(IsoCell cell, IsoGridSquare square) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && (sprite.getName() != null && sprite.getName().startsWith("roofs_")
                    || isTile(sprite.getType()) && !noRoof(cell, sprite, square.getX(), square.getY(), square.getZ()))) {
                return true;
            }
        }
        return false;
    }

    /** Whether a sprite the game marks as a tile of a roof is no roof. */
    private static boolean noRoof(IsoCell cell, IsoSprite sprite, int x, int y, int z) {
        // A parasol's canopy: see Parasols. A shop's awning, or a hood over a wall that stands on its own: see Awnings and Hoods.
        return Own.furniture() && (Parasols.partOfOne(cell, sprite, x, y, z) || Awnings.of(sprite) != null
                || Hoods.eave(sprite) != Hoods.NONE && Hoods.hood(Hoods.on(cell, z), x, y) || Hoods.endPiece(sprite) && Hoods.endOfAHood(Hoods.on(cell, z), x, y))
                // A tent's or a market stall's canopy: see Tents.
                || Tents.of(sprite) != null;
    }

    private static void take(IsoCell cell, int x, int y, int z, Roof roof, HashSet<Long> seen) {
        if (!seen.add(RoofFit.key(x, y, z))) {
            return;
        }
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        if (square == null) {
            return;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object.getSprite();
            if (sprite == null || !isTile(sprite.getType()) || noRoof(cell, sprite, x, y, z)) {
                continue;
            }
            Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
            if (!WorldScene.usable(texture)) {
                continue;
            }
            WorldScene.frameOf(frame, object, texture);
            reading.add(new Laid(measure(frame, texture, x, y, z), texture, roof));
            return;
        }
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object.getSprite();
            // A tile of a roof passed over above (a parasol's, an awning's) is no flat top either.
            if (sprite == null || sprite.getName() == null || !sprite.getName().startsWith("roofs_") || isTile(sprite.getType())) {
                continue;
            }
            Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
            if (!WorldScene.usable(texture)) {
                continue;
            }
            WorldScene.frameOf(frame, object, texture);
            RoofFit.Tile tile = measure(frame, texture, x, y, z);
            if (flatTop(tile)) {
                tile.sheet = true;
                reading.add(new Laid(tile, texture, roof));
                return;
            }
        }
    }

    /**
     * Whether a sprite of the roofs' sheets that the game marks as no tile of a roof is a level
     * sheet painted over its floor: the flat crown of a hip cut short (roofs_01_90 a third of a
     * level up, roofs_01_92 two thirds), a flat roof of corrugated sheet. It is a tile of its roof
     * all the same: the fit carries the far slopes across it, and left out it showed the loft
     * ceiling a step down inside the slopes. A sheet painted on its floor is the one under a
     * slope, and no tile.
     */
    static boolean flatTop(RoofFit.Tile tile) {
        return tile.axis == RoofFit.LEVEL && tile.sure && !tile.fitting && tile.half == 0 && tile.low - tile.z >= FLAT_TOP_LEAST;
    }

    static boolean isTile(IsoObjectType type) {
        return type == IsoObjectType.WestRoofB || type == IsoObjectType.WestRoofM || type == IsoObjectType.WestRoofT;
    }

    /**
     * How a roof tile lies, by its sprite. A roof tile is a plane over its square, level along
     * one axis and sloping along the other. Nothing the game holds says which way a tile
     * slopes, but its sprite does. The sprite's top is where the square's north-west corner is
     * drawn and its bottom where the south-east corner is, which gives the roof's height at
     * both. The other two corners each share a height with one of those, and which one is read
     * off the sprite: a slope along y has its south-west corner drawn at the south-east
     * corner's height, a slope along x at the north-west corner's, and only one of the two
     * places has the sprite's pixels in it.
     */
    static RoofFit.Tile measure(SpriteGeometry.Frame frame, Texture texture, int x, int y, int z) {
        float northWest = SpriteGeometry.heightDrawnAt(frame, 0f, 0f, frame.top);
        float southEast = SpriteGeometry.heightDrawnAt(frame, 1f, 1f, frame.top + frame.height);
        // Half a tile, the top of a slope under a ridge along the middle of a square: its
        // sprite is three quarters of a tile's wide, and its foot is the square's south-east
        // corner. Its top row is not read: some carry a board above the slope.
        float wide = frame.width / frame.scale;
        if (wide >= HALF_NARROWEST && wide <= HALF_WIDEST) {
            RoofFit.Tile half = tile(x, y, z, RoofFit.LEVEL, true, z + southEast, z + southEast, true);
            half.half = frame.left > HALF_INSET * frame.scale ? RoofFit.EAST_HALF : RoofFit.SOUTH_HALF;
            half.halfEdge = z + southEast;
            return half;
        }
        boolean fitting = frame.width <= WHOLE_TILE_WIDE * frame.scale;
        if (!fitting) {
            RoofFit.Tile cut = cut(frame, texture, x, y, z, northWest, southEast);
            if (cut != null) {
                return cut;
            }
        }
        // A few sprites have a stray pixel or two far from the tile, at the foot of the
        // frame, and their outline then runs from the tile to the stray. The tile is the
        // longest run of rows with paint on them.
        if (Math.abs(northWest - southEast) > STEEPEST && !fitting) {
            int first = (int) frame.top, last = first + (int) frame.height - 1;
            int[] rows = RoofFit.longestRun(last - first + 1, Math.round(PAINT_GAP * frame.scale), row -> paintedRow(frame, texture, first + row));
            northWest = SpriteGeometry.heightDrawnAt(frame, 0f, 0f, first + rows[0]);
            southEast = SpriteGeometry.heightDrawnAt(frame, 1f, 1f, first + rows[1] + 1);
        }
        // Some roof sprites have more painted on them than the slope, the end of the roof or
        // a board up its edge, and their outline then says the tile climbs a whole floor. Such
        // a tile is taken to rise a third of a floor from its lowest drawn point, which is the
        // roof's own.
        if (Math.abs(northWest - southEast) > STEEPEST) {
            if (northWest > southEast) {
                northWest = southEast + RoofFit.PITCH;
            } else {
                southEast = northWest + RoofFit.PITCH;
            }
        }
        if (Math.abs(northWest - southEast) < FLAT) {
            return tile(x, y, z, RoofFit.LEVEL, true, z + northWest, z + southEast, fitting);
        }
        int alongY = 0, alongX = 0;
        boolean southWestAlongY = false, northEastAlongX = false;
        // South-west corner, then north-east: the two the slope's direction decides.
        for (int i = 0; i < 2; i++) {
            float a = i, b = 1 - i;
            float inset = (i == 0 ? CORNER_INSET : -CORNER_INSET) * frame.scale;
            int u = Math.round(SpriteGeometry.frameU(frame, a, b) + inset);
            // Along y the height follows b, so this corner is at the height of the corner with its b.
            boolean atY = texture.isMaskSet(u, Math.round(SpriteGeometry.frameV(frame, a, b, i == 0 ? southEast : northWest)));
            boolean atX = texture.isMaskSet(u, Math.round(SpriteGeometry.frameV(frame, a, b, i == 0 ? northWest : southEast)));
            if (atY && !atX) {
                alongY++;
                southWestAlongY |= i == 0;
            } else if (atX && !atY) {
                alongX++;
                northEastAlongX |= i == 1;
            }
        }
        // Each of the two corners says a different way: both are at the height of the same
        // one of the other two, and the tile is where two slopes meet.
        if (alongX == 1 && alongY == 1 && !fitting) {
            return tile(x, y, z, southWestAlongY && northEastAlongX ? RoofFit.NORTH_WEST_ODD : RoofFit.SOUTH_EAST_ODD, true, z + northWest, z + southEast, false);
        }
        if (alongX == alongY && frame.width > WHOLE_TILE_WIDE * frame.scale) {
            // The corners did not say. The sprite's two ends do: its left end is the tile's
            // south-west corner and its right end the north-east one, and whichever end is
            // painted higher up is the higher corner.
            float left = paintedAt(frame, texture, Math.round(frame.left + CORNER_INSET * frame.scale));
            float right = paintedAt(frame, texture, Math.round(frame.left + frame.width - 1 - CORNER_INSET * frame.scale));
            if (!Float.isNaN(left) && !Float.isNaN(right) && Math.abs(left - right) > 2f * frame.scale) {
                boolean southWestHigher = left < right;
                if (southWestHigher == southEast > northWest) {
                    alongY++;
                } else {
                    alongX++;
                }
            }
        }
        return tile(x, y, z, alongX > alongY ? RoofFit.ALONG_X : RoofFit.ALONG_Y, alongX != alongY, z + northWest, z + southEast, fitting);
    }

    /**
     * A tile cut to a triangle, where a hip crosses its square and the map has only this side of
     * it: or null for a tile that is not. One corner of the square is cut away, the north-west or
     * the south-east, and the top of the sprite, or its foot, is then one of the other two
     * corners: it lies at an end of the sprite and not over its middle. Read as the corner cut
     * away, it put the tile a sixth of a floor out, and a hip's end was a fan of steps. The
     * corner cut away was at the height of the one found, the square's other side being the
     * lower or the higher, and which of the two was found says which way the tile slopes.
     */
    private static RoofFit.Tile cut(SpriteGeometry.Frame frame, Texture texture, int x, int y, int z, float northWest, float southEast) {
        float middle = SpriteGeometry.frameU(frame, 0f, 0f), aside = CUT_ASIDE * frame.scale;
        // The tile is the longest run of rows with paint on them: a stray pixel under it is no corner of it.
        int from = (int) frame.top;
        int[] rows = RoofFit.longestRun((int) frame.height, Math.round(PAINT_GAP * frame.scale), row -> paintedRow(frame, texture, from + row));
        int first = from + rows[0], last = from + rows[1];
        float top = paintedAlong(frame, texture, first), foot = paintedAlong(frame, texture, last);
        boolean topAside = Math.abs(top - middle) > aside, footAside = Math.abs(foot - middle) > aside;
        if (topAside == footAside) {
            return null;
        }
        // The north-east corner is drawn at the sprite's right end, the south-west at its left.
        boolean northEast = topAside ? top > middle : foot > middle;
        float a = northEast ? 1f : 0f, b = 1f - a;
        if (topAside) {
            northWest = SpriteGeometry.heightDrawnAt(frame, a, b, first);
        } else {
            southEast = SpriteGeometry.heightDrawnAt(frame, a, b, last + 1);
        }
        float climb = Math.abs(northWest - southEast);
        if (climb < FLAT || climb > STEEPEST) {
            return null;
        }
        // Half of the square is painted and half is not: a place well inside each, both over the
        // sprite's middle. A trim that runs round a corner of a roof has that corner and no middle.
        float away = topAside ? CUT_AWAY : 1f - CUT_AWAY, kept = topAside ? CUT_KEPT : 1f - CUT_KEPT;
        if (texture.isMaskSet(Math.round(middle), Math.round(SpriteGeometry.frameV(frame, away, away, northWest + (southEast - northWest) * away)))
                || !texture.isMaskSet(Math.round(middle), Math.round(SpriteGeometry.frameV(frame, kept, kept, northWest + (southEast - northWest) * kept)))) {
            return null;
        }
        // The north-east corner is as high as the north-west and the south-west as the south-east
        // on a slope along y, and the other way about on one along x.
        RoofFit.Tile cut = tile(x, y, z, northEast == topAside ? RoofFit.ALONG_Y : RoofFit.ALONG_X, true, z + northWest, z + southEast, false);
        cut.cutCorner = topAside ? 0 : 2;
        return cut;
    }

    /** The middle of what is painted in row v of the sprite, as a frame column. NaN if nothing is. */
    private static float paintedAlong(SpriteGeometry.Frame frame, Texture texture, int v) {
        int count = 0;
        long sum = 0;
        for (int u = (int) frame.left; u < frame.left + frame.width; u++) {
            if (texture.isMaskSet(u, v)) {
                sum += u;
                count++;
            }
        }
        return count == 0 ? Float.NaN : (float) sum / count;
    }

    private static RoofFit.Tile tile(int x, int y, int z, int axis, boolean sure, float low, float high, boolean fitting) {
        RoofFit.Tile tile = new RoofFit.Tile(x, y, z, axis, sure, low, high);
        tile.fitting = fitting;
        return tile;
    }

    private static boolean paintedRow(SpriteGeometry.Frame frame, Texture texture, int v) {
        for (int u = (int) frame.left; u < frame.left + frame.width; u++) {
            if (texture.isMaskSet(u, v)) {
                return true;
            }
        }
        return false;
    }

    /** The middle of what is painted in column u of the sprite, as a frame row. NaN if nothing is. */
    private static float paintedAt(SpriteGeometry.Frame frame, Texture texture, int u) {
        int first = -1, last = -1;
        for (int v = (int) frame.top; v < frame.top + frame.height; v++) {
            if (texture.isMaskSet(u, v)) {
                if (first < 0) {
                    first = v;
                }
                last = v;
            }
        }
        return first < 0 ? Float.NaN : (first + last) / 2f;
    }

    /** A roof of no tiles, covered in what a sprite shows: for a piece of roof the map lays with no roof of its own over it. */
    static Roof coveredIn(Texture texture) {
        Roof roof = new Roof();
        paintedPlace(texture, roof);
        return roof;
    }

    /**
     * Finds a place on the sprite that is painted, with paint all round it, and makes the
     * sprite the roof's finish. Looked for from the sprite's middle outwards.
     */
    private static void paintedPlace(Texture texture, Roof roof) {
        int left = (int) texture.getOffsetX(), top = (int) texture.getOffsetY(), width = texture.getWidth(), height = texture.getHeight();
        for (int ring = 0; ring < 4; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
                        continue;
                    }
                    int u = left + width / 2 + i * width / 10, v = top + height / 2 + j * height / 10;
                    if (paintedRound(texture, u, v)) {
                        roof.finish = texture;
                        roof.u = texture.getXStart() + (u + 0.5f - left) / width * (texture.getXEnd() - texture.getXStart());
                        roof.v = texture.getYStart() + (v + 0.5f - top) / height * (texture.getYEnd() - texture.getYStart());
                        return;
                    }
                }
            }
        }
    }

    private static boolean paintedRound(Texture texture, int u, int v) {
        for (int du = -1; du <= 1; du++) {
            for (int dv = -1; dv <= 1; dv++) {
                if (!texture.isMaskSet(u + du, v + dv)) {
                    return false;
                }
            }
        }
        return true;
    }
}
