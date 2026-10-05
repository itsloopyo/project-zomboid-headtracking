package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.HashMap;
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
    /** How far inside a roof tile's corner its sprite is looked at, in frame pixels at tile scale 1. */
    private static final float CORNER_INSET = 4f;
    /** No roof in the game climbs more than this over a tile, in levels: a slope takes three tiles to a floor. */
    private static final float STEEPEST = 0.55f;
    /** A roof sprite narrower than this, in unscaled pixels, is a fitting on the roof and not a tile of it. */
    static final float WHOLE_TILE_WIDE = 50f;
    /** This many rows of a sprite with nothing painted on them end what is painted above them, in frame pixels at tile scale 1. */
    private static final float PAINT_GAP = 3f;
    /** How wide the sprite of half a tile is, in unscaled pixels, and how far in from its frame's left edge the east half starts at the least. */
    private static final float HALF_NARROWEST = 47f, HALF_WIDEST = 54f, HALF_INSET = 8f;
    private static final int KEPT = 600, RETRIED = 30;
    /** The most tiles read as one roof. */
    private static final int MOST = 4000;

    /** One roof: what it is covered in. */
    static final class Roof {
        /** The sprite most of its tiles show, or null for a roof with no such sprite the picture can take a colour from. */
        Texture finish;
        /** A place on that sprite's texture that is painted. */
        float u, v;
        private int stamp;
        private boolean whole = true;
    }

    /** One tile of a roof as the fit left it. */
    static final class Laid {
        final RoofFit.Tile tile;
        /** The sprite the tile was read from, or null for a tile of a slope the map leaves out. */
        final Texture texture;
        final Roof roof;

        Laid(RoofFit.Tile tile, Texture texture, Roof roof) {
            this.tile = tile;
            this.texture = texture;
            this.roof = roof;
        }
    }

    private static final HashMap<Long, Laid> laid = new HashMap<>();
    /**
     * The tiles of the slopes the map leaves out, by square and by chunk. A chunk that holds
     * nothing else of its roof still has to find them, so they outlive laid, and each goes when
     * its roof has not been read again for this many frames.
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
    }

    /** The tile of roof on the square as its whole roof was fitted, or null for a square with no roof tile. */
    static Laid at(IsoCell cell, int x, int y, int z, int now, float scale) {
        if (now - cleared >= KEPT) {
            laid.clear();
            cleared = now;
            forgetStale(now);
        }
        long key = RoofFit.key(x, y, z);
        Laid known = laid.get(key);
        if (known == null || !known.roof.whole && now - known.roof.stamp >= RETRIED) {
            read(cell, x, y, z, now, scale);
            known = laid.get(key);
        }
        return known;
    }

    /**
     * Drops the made tiles and the caps of roofs not read again in MADE_KEPT frames. Each is
     * already taken for gone by whoever asks for it, but only a chunk being built asks, so a
     * roof the player has left behind was otherwise held for the rest of the game.
     */
    private static void forgetStale(int now) {
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
        reading.clear();
        HashMap<Long, Boolean> seen = new HashMap<>();
        frame.scale = scale;
        take(cell, x, y, z, roof, seen);
        for (int next = 0; next < reading.size() && reading.size() < MOST; next++) {
            RoofFit.Tile tile = reading.get(next).tile;
            for (int side = 0; side < 4; side++) {
                int dx = side == 0 ? -1 : side == 1 ? 1 : 0, dy = side == 2 ? -1 : side == 3 ? 1 : 0;
                roof.whole &= cell.getGridSquare(tile.x + dx, tile.y + dy, 0) != null;
                for (int dz = -1; dz <= 1; dz++) {
                    take(cell, tile.x + dx, tile.y + dy, tile.z + dz, roof, seen);
                }
            }
        }
        tiles.clear();
        shown.clear();
        WallSides.Votes votes = new WallSides.Votes();
        for (Laid one : reading) {
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
        for (Laid one : reading) {
            laid.put(RoofFit.key(one.tile.x, one.tile.y, one.tile.z), one);
        }
        // A roof read before all of it was loaded is short of rows, and what would be carried
        // over its ridge then is not the roof's far side.
        if (roof.whole) {
            int highest = 0;
            for (RoofFit.Tile tile : tiles) {
                highest = Math.max(highest, tile.z);
            }
            List<RoofFit.Tile> backs = RoofFit.backs(tiles, site(cell, highest));
            RoofFit.peaks(tiles, backs);
            for (RoofFit.Tile back : backs) {
                keep(new Laid(back, null, roof));
            }
            for (RoofFit.Cap cap : RoofFit.caps(tiles, backs)) {
                keep(new Capped(cap, roof));
            }
        }
    }

    /** The key of the chunk a cap's middle lies in. */
    private static long chunkOf(RoofFit.Cap cap) {
        int middleX = (int) Math.floor((cap.x0 + cap.x1) / 2f), middleY = (int) Math.floor((cap.y0 + cap.y1) / 2f);
        return RoofFit.key(Math.floorDiv(middleX, WorldScene.CHUNK_TILES), Math.floorDiv(middleY, WorldScene.CHUNK_TILES), 0);
    }

    private static void keep(Capped cap) {
        float middleX = (cap.cap.x0 + cap.cap.x1) / 2f, middleY = (cap.cap.y0 + cap.cap.y1) / 2f;
        // Twice the middle, rounded, and the way the cap runs name one: a level tile with slopes
        // up to it on all four sides has two caps that cross at its middle.
        int runs = cap.cap.x0 == cap.cap.x1 ? 1 : cap.cap.y0 == cap.cap.y1 ? 2 : 3;
        long key = RoofFit.key(Math.round(2f * middleX), Math.round(2f * middleY), runs);
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
    static ArrayList<Capped> cappedIn(int chunkX, int chunkY, int now) {
        ArrayList<Capped> held = cappedByChunk.get(RoofFit.key(chunkX, chunkY, 0));
        if (held == null) {
            return null;
        }
        for (int i = held.size() - 1; i >= 0; i--) {
            Capped cap = held.get(i);
            if (now - cap.roof.stamp > MADE_KEPT) {
                held.remove(i);
                capped.values().remove(cap);
            }
        }
        return held;
    }

    private static void keep(Laid back) {
        long key = RoofFit.key(back.tile.x, back.tile.y, back.tile.z);
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
    static ArrayList<Laid> madeIn(int chunkX, int chunkY, int now) {
        ArrayList<Laid> held = madeByChunk.get(RoofFit.key(chunkX, chunkY, 0));
        if (held == null) {
            return null;
        }
        for (int i = held.size() - 1; i >= 0; i--) {
            Laid back = held.get(i);
            if (now - back.roof.stamp > MADE_KEPT) {
                held.remove(i);
                made.remove(RoofFit.key(back.tile.x, back.tile.y, back.tile.z), back);
            }
        }
        return held;
    }

    private static RoofFit.Site site(IsoCell cell, int highest) {
        return new RoofFit.Site() {
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
                    if (level > 0 && level < z && square.getRoomID() == -1L && (square.getFloor() != null || roofing(square))) {
                        return false;
                    }
                }
                return built;
            }

            @Override
            public boolean floored(int x, int y, int z) {
                IsoGridSquare square = cell.getGridSquare(x, y, z);
                return square != null && (square.getFloor() != null || roofing(square));
            }

            @Override
            public boolean taken(int x, int y, int z) {
                IsoGridSquare square = cell.getGridSquare(x, y, z);
                if (square != null && (square.getRoomID() != -1L || square.getFloor() != null || roofing(square))) {
                    return true;
                }
                for (int above = 1; above <= LOOKED_ABOVE; above++) {
                    if (floored(x, y, z + above)) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    /** Whether any of the game's roofing lies on the square: a tile of a roof, or one of its caps and edgings. */
    static boolean roofing(IsoGridSquare square) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && sprite.getName() != null && sprite.getName().startsWith("roofs_")) {
                return true;
            }
        }
        return false;
    }

    private static void take(IsoCell cell, int x, int y, int z, Roof roof, HashMap<Long, Boolean> seen) {
        if (seen.put(RoofFit.key(x, y, z), Boolean.TRUE) != null) {
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
            if (sprite == null || !isTile(sprite.getType())) {
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
