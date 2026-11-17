package com.cameraunlock.zomboid;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import zombie.core.Core;
import zombie.core.properties.PropertyContainer;
import zombie.core.textures.Texture;
import zombie.iso.IsoDirections;
import zombie.iso.IsoLot;
import zombie.iso.LotHeader;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteManager;

/**
 * What each tile a cell's lotpack names is to the far land, and the colour the game paints it:
 * the ground's floors and what is laid over them, the walls and the windows in them, and the
 * tiles of roofs (FarLot reads the lotpack with these). The sprites are the game's and are asked
 * on the logic thread, and their colours are read off the graphics card by the frames drawn
 * (SpriteColours), so a cell waits until every colour it needs has been read.
 */
final class FarTiles {
    /** A tile's traits. GROUND: it is laid flat on the ground, as a floor or over one. */
    static final int GROUND = 1, FLOOR = 1 << 1, DIAMOND = 1 << 2, SEE_THROUGH = 1 << 3, OVERLAY = 1 << 4, WATER = 1 << 5;
    /** A wall along the square's north or west edge, a window in it, and whether the tile is the wall itself, painted plain, and not a frame round a door or a window. */
    static final int WALL_N = 1 << 6, WALL_W = 1 << 7, WINDOW_N = 1 << 8, WINDOW_W = 1 << 9, PLAIN_WALL = 1 << 10;
    /** A tile of a roof, which of the ways in FarLot it lies, three bits from LIES_SHIFT, and how high its highest edge stands over its level, in 15ths of a level, four bits from TOP_SHIFT. */
    static final int ROOF = 1 << 11, LIES_SHIFT = 12, TOP_SHIFT = 15;
    /** Whether the tile's colour has been read: the low 24 bits of its colour, and for a tile laid on the ground how much of the square it covers, in 255ths, in the high 8. */
    static final int COLOURED = 1 << 20;
    /** How many steps of a level a roof's top is kept in. */
    static final float TOP_STEPS = 15f;

    /**
     * What a cell's tiles are, a place for each tile its header names: their traits and colours.
     *
     * @param pack  the cell's lotpack, read on the far land's thread
     * @param whole whether every colour is in: a tile whose colour is still being read is in without it
     */
    record Cell(Path pack, int minLevel, int maxLevel, int[] traits, int[] colours, boolean whole) {}

    /** What is not yet known of a cell: its tiles so far, the places of those whose colour is still being read, and SpriteColours.readings when they were last looked at. */
    private static final class Pending {
        final Path pack;
        final int minLevel, maxLevel;
        final int[] traits, colours;
        final ArrayList<Integer> unread = new ArrayList<>();
        long lookedAt;

        Pending(Path pack, int minLevel, int maxLevel, int tiles) {
            this.pack = pack;
            this.minLevel = minLevel;
            this.maxLevel = maxLevel;
            traits = new int[tiles];
            colours = new int[tiles];
        }

        Cell cell() {
            return new Cell(pack, minLevel, maxLevel, traits.clone(), colours.clone(), unread.isEmpty());
        }
    }

    /** Logic thread: each tile by name, its traits and colour, once its colour is known. */
    private static final HashMap<String, int[]> known = new HashMap<>();
    /** Logic thread: by name, the traits of each tile whose colour is still being read, and SpriteColours.readings when it was last looked at, so that the cells sharing it look once. */
    private static final HashMap<String, long[]> unread = new HashMap<>();

    /**
     * When a tile still waited for is next looked at: once more sprites have been read, and
     * about once a second besides, for a sheet that was not yet on the graphics card.
     */
    private static long look() {
        return (long) SpriteColours.readings() << 32 | System.nanoTime() >>> 30;
    }
    private static final HashMap<Long, Pending> pending = new HashMap<>();
    /** Set by the logic thread, read by the render thread: whether a cell waits for a colour, which frames that draw no picture then read (Pipeline.HAND_FAR). */
    static volatile boolean waiting;
    /** The frame a roof sprite is measured in, as the game's loader places a tile: at IsoObject's own offset. */
    private static final SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
    private static final float LOADED_OFFSET_X = 64f, LOADED_OFFSET_Y = 192f;

    private FarTiles() {}

    /**
     * Logic thread: the cell's tiles, or null for a cell the map has no lotpack for. Until every
     * colour its tiles take is read the cell is not whole, and asked about again, it gives what is
     * known so far: the tiles still unread are looked at again once more sprites have been read.
     */
    static Cell cell(int cellX, int cellY) {
        long key = (long) cellX << 32 | cellY & 0xffffffffL;
        Pending waited = pending.get(key);
        if (waited == null) {
            LotHeader header = IsoLot.getHeader(cellX, cellY);
            String pack = IsoLot.InfoFileNames.get("world_" + cellX + "_" + cellY + ".lotpack");
            if (header == null || pack == null) {
                return null;
            }
            List<String> names = header.tilesUsed;
            waited = new Pending(Path.of(pack), header.minLevel, header.maxLevel, names.size());
            waited.lookedAt = look();
            for (int i = 0; i < names.size(); i++) {
                if (!take(names.get(i), waited, i)) {
                    waited.unread.add(i);
                }
            }
        } else {
            lookAgain(waited, cellX, cellY);
        }
        if (waited.unread.isEmpty()) {
            pending.remove(key);
        } else {
            pending.put(key, waited);
        }
        waiting = !pending.isEmpty();
        return waited.cell();
    }

    /** Logic thread: whether every colour of a cell cell gave before it was whole is now in, so that cell gives it whole. */
    static boolean whole(int cellX, int cellY) {
        Pending waited = pending.get((long) cellX << 32 | cellY & 0xffffffffL);
        if (waited == null) {
            return true;
        }
        lookAgain(waited, cellX, cellY);
        return waited.unread.isEmpty();
    }

    /** Looks again at the tiles of the cell still unread, once more sprites have been read since it last did. */
    private static void lookAgain(Pending waited, int cellX, int cellY) {
        long look = look();
        if (waited.lookedAt == look) {
            return;
        }
        waited.lookedAt = look;
        List<String> names = IsoLot.getHeader(cellX, cellY).tilesUsed;
        waited.unread.removeIf(i -> take(names.get(i), waited, i));
    }

    /** Logic thread: the cell each view's eye was last in, as keepRound was told, NONE for a view that has not told it. */
    private static final long[] eyes = new long[View.COUNT];
    private static final long NONE = Long.MIN_VALUE;

    static {
        Arrays.fill(eyes, NONE);
    }

    /** Logic thread: lets go of the cells waited for that lie further than around cells, on either axis, from the cell of every view's eye. */
    static void keepRound(int view, int eyeCellX, int eyeCellY, int around) {
        eyes[view] = (long) eyeCellX << 32 | eyeCellY & 0xffffffffL;
        pending.keySet().removeIf(key -> {
            for (long eye : eyes) {
                if (eye != NONE && Math.abs((int) (key >> 32) - (int) (eye >> 32)) <= around && Math.abs(key.intValue() - (int) eye) <= around) {
                    return false;
                }
            }
            return true;
        });
        waiting = !pending.isEmpty();
    }

    /** Logic thread: lets go of every cell waited for, as nothing will ask for their colours (Part.FAR_LAND_DETAIL off). */
    static void stopWaiting() {
        pending.clear();
        waiting = false;
    }

    /** Logic thread, as the map is let go of. */
    static void forget() {
        known.clear();
        unread.clear();
        pending.clear();
        Arrays.fill(eyes, NONE);
        waiting = false;
    }

    /** Puts what the tile is at place i of the cell: false while its colour is still being read, with its traits put and no colour. */
    private static boolean take(String name, Pending cell, int i) {
        int[] tile = known.get(name);
        if (tile == null) {
            long[] waited = unread.get(name);
            long look = look();
            tile = waited != null && waited[1] == look ? null : of(name, waited == null ? null : (int) waited[0]);
            if (tile == null) {
                if (waited == null) {
                    waited = new long[] {traits(name), look};
                    unread.put(name, waited);
                } else if (sloping((int) waited[0])) {
                    // A roof tile's slope is known once its sheet is on the graphics card, before its colour is.
                    waited[0] = traits(name);
                }
                waited[1] = look;
                cell.traits[i] = (int) waited[0];
                return false;
            }
            unread.remove(name);
            known.put(name, tile);
        }
        cell.traits[i] = tile[0];
        cell.colours[i] = tile[1];
        return true;
    }

    /** What the tile is and its colour, or null while its colour is still being read or its sheet is not yet on the graphics card. */
    static int[] of(String name, Integer known) {
        IsoSprite sprite = IsoSpriteManager.instance.namedMap.get(name);
        // A name no sprite has: the game lays nothing for it (IsoCell.PlaceLot).
        if (sprite == null) {
            return new int[2];
        }
        int traits = known != null ? known : traits(sprite, name);
        if ((traits & (GROUND | PLAIN_WALL | ROOF)) == 0) {
            return new int[] {traits, 0};
        }
        Texture texture = sprite.getTextureForCurrentFrame(IsoDirections.N);
        if (texture == null) {
            return new int[] {traits, 0};
        }
        // A sheet not yet on the graphics card is waited for, its colour and a roof tile's slope with it.
        if (!sized(texture)) {
            return null;
        }
        if (sloping(traits) && Roofs.isTile(sprite.getType())) {
            traits |= lies(texture);
        }
        int painted;
        if ((traits & GROUND) != 0) {
            float[] colour = SpriteColours.coverOf(texture);
            if (colour == null) {
                return null;
            }
            if (colour.length == 0) {
                return new int[] {traits, 0};
            }
            float cover = colour[WallSides.COVER + 3];
            if (cover <= 0f) {
                return new int[] {traits, 0};
            }
            painted = rgb(colour[WallSides.COVER] / cover, colour[WallSides.COVER + 1] / cover, colour[WallSides.COVER + 2] / cover) | Math.round(Math.min(cover, 1f) * 255f) << 24;
        } else {
            float[] colour = SpriteColours.of(texture);
            if (colour == null) {
                return SpriteColours.unread(texture) ? null : new int[] {traits, 0};
            }
            painted = rgb(colour[WallSides.MEAN], colour[WallSides.MEAN + 1], colour[WallSides.MEAN + 2]);
        }
        return new int[] {traits | COLOURED, painted};
    }

    /** The traits of a tile whose sprite there is, without its colour: see traits(IsoSprite, String). */
    private static int traits(String name) {
        return traits(IsoSpriteManager.instance.namedMap.get(name), name);
    }

    /** Whether the traits are a roof tile's whose slope is not yet read from its sheet, or reads as none. */
    private static boolean sloping(int traits) {
        return (traits & ROOF) != 0 && (traits >>> LIES_SHIFT & 7) == FarLot.NONE;
    }

    private static boolean sized(Texture texture) {
        return texture != null && texture.getWidth() > 0 && texture.getHeight() > 0;
    }

    private static int rgb(float red, float green, float blue) {
        return channel(red) << 16 | channel(green) << 8 | channel(blue);
    }

    private static int channel(float value) {
        return Math.clamp(Math.round(value * 255f), 0, 255);
    }

    /** What a sprite is to the far land, but for the tile's colour, and how a roof tile slopes where its sheet is not yet on the graphics card. */
    private static int traits(IsoSprite sprite, String name) {
        PropertyContainer flags = sprite.getProperties();
        int traits = 0;
        boolean floor = flags.has(IsoFlagType.solidfloor), overlay = flags.has(IsoFlagType.FloorOverlay);
        if (floor || overlay || name.startsWith("blends_")) {
            traits |= GROUND;
            traits |= floor ? FLOOR : 0;
            traits |= overlay ? OVERLAY : 0;
            traits |= flags.has(IsoFlagType.diamondFloor) ? DIAMOND : 0;
            traits |= flags.has(IsoFlagType.transparentFloor) ? SEE_THROUGH : 0;
            traits |= flags.has(IsoFlagType.water) ? WATER : 0;
        }
        boolean wallN = flags.has(IsoFlagType.WallN), wallW = flags.has(IsoFlagType.WallW), wallNW = flags.has(IsoFlagType.WallNW);
        IsoObjectType type = sprite.getType();
        boolean doorN = flags.has(IsoFlagType.DoorWallN) || type == IsoObjectType.doorFrN, doorW = flags.has(IsoFlagType.DoorWallW) || type == IsoObjectType.doorFrW;
        boolean frameN = flags.has(IsoFlagType.WindowN) || type == IsoObjectType.windowFN, frameW = flags.has(IsoFlagType.WindowW) || type == IsoObjectType.windowFW;
        // A wall of glass, a shop's front, is a window the height of its wall.
        boolean glassN = flags.has(IsoFlagType.WallNTrans), glassW = flags.has(IsoFlagType.WallWTrans);
        traits |= wallN || wallNW || doorN || frameN || glassN ? WALL_N : 0;
        traits |= wallW || wallNW || doorW || frameW || glassW ? WALL_W : 0;
        traits |= frameN || glassN || flags.has(IsoFlagType.windowN) ? WINDOW_N : 0;
        traits |= frameW || glassW || flags.has(IsoFlagType.windowW) ? WINDOW_W : 0;
        // A fence or a railing stands on an edge as a wall does, and is no wall of a building: only the walls' own sheets are, less the trims laid over them.
        if ((wallN || wallW || wallNW) && !doorN && !doorW && !frameN && !frameW && name.startsWith("walls_") && !name.startsWith("walls_exterior_roofs")
                && !name.contains("detailing") && !name.contains("decoration")) {
            traits |= PLAIN_WALL;
        }
        if (name.startsWith("roofs_") && !Roofs.accent(name)) {
            // A sheet of a roof the game marks as no tile (Roofs.flatTop) is taken for its colour alone.
            traits |= ROOF;
            Texture texture = sprite.getTextureForCurrentFrame(IsoDirections.N);
            if (!Roofs.isTile(sprite.getType())) {
                traits |= FarLot.SHEET << LIES_SHIFT;
            } else if (sized(texture)) {
                traits |= lies(texture);
            }
        }
        return traits;
    }

    /** How a roof tile's sprite lies, as Roofs.measure reads it at level 0, packed as traits keep it: see LIES_SHIFT and TOP_SHIFT. */
    private static int lies(Texture texture) {
        frame.scale = Core.tileScale;
        frame.originU = LOADED_OFFSET_X;
        frame.originV = LOADED_OFFSET_Y;
        frame.takeTexels(texture);
        RoofFit.Tile tile = Roofs.measure(frame, texture, 0, 0, 0);
        int top = Math.clamp(Math.round(Math.max(tile.low, tile.high) * TOP_STEPS), 0, (int) TOP_STEPS) << TOP_SHIFT;
        return FarLot.liesOf(tile.axis, tile.sure, tile.fitting || tile.half != 0) << LIES_SHIFT | top;
    }
}
