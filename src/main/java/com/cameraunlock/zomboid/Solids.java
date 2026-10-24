package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * What of a sprite is painted solid.
 *
 * The game's own outline of a sprite is every pixel with any paint on it, and the artists
 * paint the shadow a thing casts under it into its sprite, a few parts in a hundred of
 * black. To the outline that shadow is as much of the thing as the thing is: the coping
 * along a parapet, a slab two fingers thick with its shadow down the wall under it, reads as
 * a box a third of a storey deep. So what is built from its outline asks here for the
 * outline of its solid paint, which is read with the sprite's colours: see SpriteColours.
 */
final class Solids {
    /** A pixel with less paint on it than this, of 255, is shadow or the soft edge of something, and no part of its body. */
    static final int SOLID = 64;

    /** The solid paint of a sprite: its box in the sprite's frame, and each pixel of the box. */
    static final class Outline implements SpriteShape.Painted {
        final int left, top, width, height;
        private final long[] bits;

        Outline(int left, int top, int width, int height, long[] bits) {
            this.left = left;
            this.top = top;
            this.width = width;
            this.height = height;
            this.bits = bits;
        }

        @Override
        public boolean at(int u, int v) {
            int column = u - left, row = v - top;
            if (column < 0 || row < 0 || column >= width || row >= height) {
                return false;
            }
            int bit = row * width + column;
            return (bits[bit >> 6] >> (bit & 63) & 1L) != 0L;
        }
    }

    private record Asked(int left, int top, int width, int height) {}

    private static final ConcurrentHashMap<Texture, Asked> asked = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Texture, Outline> known = new ConcurrentHashMap<>();

    private Solids() {}

    /**
     * The solid paint of the sprite whose frame is given, with a width of 0 for a sprite that
     * has none. Null until a frame has been drawn since it was first asked for.
     */
    static Outline of(Texture texture, SpriteGeometry.Frame frame) {
        Outline outline = known.get(texture);
        if (outline == null && !asked.containsKey(texture) && asked.putIfAbsent(texture, new Asked(Math.round(frame.left), Math.round(frame.top), Math.round(frame.width), Math.round(frame.height))) == null) {
            SpriteColours.readAgain(texture);
        }
        return outline;
    }

    /** Reads the sprite if it was asked for: see Windows.read. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        Asked wanted = asked.get(texture);
        if (wanted == null || known.containsKey(texture)) {
            return;
        }
        known.put(texture, outline(solid(sheet, width, left, top, right, bottom, wanted.width, wanted.height), wanted.left, wanted.top, wanted.width, wanted.height));
    }

    /**
     * Which pixels of a sprite across by down are painted solid, row by row, read off its
     * rectangle of a sheet width wide, which is drawn at another size than the sprite where
     * the sheet is. None of them where the rectangle has no size: a sheet that never came to
     * the graphics card is read so, and its sprites paint nothing, as SpriteColours keeps them.
     */
    static boolean[] solid(ByteBuffer sheet, int width, int left, int top, int right, int bottom, int across, int down) {
        boolean[] solid = new boolean[across * down];
        if (right == left || bottom == top) {
            return solid;
        }
        for (int row = 0; row < down; row++) {
            int y = top + row * (bottom - top) / down;
            for (int column = 0; column < across; column++) {
                int x = left + column * (right - left) / across;
                solid[row * across + column] = (sheet.get((y * width + x) * 4 + 3) & 0xff) >= SOLID;
            }
        }
        return solid;
    }

    /** The outline of the pixels given as solid, across by down from (left, top) of a sprite's frame, row by row. */
    static Outline outline(boolean[] solid, int left, int top, int across, int down) {
        int firstRow = down, lastRow = -1, firstColumn = across, lastColumn = -1;
        for (int row = 0; row < down; row++) {
            for (int column = 0; column < across; column++) {
                if (solid[row * across + column]) {
                    firstRow = Math.min(firstRow, row);
                    lastRow = row;
                    firstColumn = Math.min(firstColumn, column);
                    lastColumn = Math.max(lastColumn, column);
                }
            }
        }
        if (lastRow < 0) {
            return new Outline(left, top, 0, 0, new long[0]);
        }
        int width = lastColumn - firstColumn + 1, height = lastRow - firstRow + 1;
        long[] bits = new long[(width * height + 63) / 64];
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                if (solid[(firstRow + row) * across + firstColumn + column]) {
                    int bit = row * width + column;
                    bits[bit >> 6] |= 1L << (bit & 63);
                }
            }
        }
        return new Outline(left + firstColumn, top + firstRow, width, height, bits);
    }
}
