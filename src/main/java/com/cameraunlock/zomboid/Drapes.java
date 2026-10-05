package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * What a curtain is, read off its sprite.
 *
 * The game paints a curtain as a sheet of cloth a few dozen dots wide with its folds drawn on.
 * Stood on a wall it is a flat board with stripes. So the sprite is only measured, laid flat on
 * its wall as a window's is (see Windows): where the cloth hangs, drawn across the window in
 * one length or pulled back to its two ends, how far down it comes, and its colour. The picture
 * then hangs cloth there in folds of its own, from a rod. A sheet rolled up over a window, which
 * hangs nowhere, is measured as the roll it is.
 */
final class Drapes {
    /** The fewest dots a length of cloth is across, and the widest gap in one that is still a fold's shadow and no parting. */
    private static final int NARROWEST = 2, WIDEST_GAP = 2;
    /** How far down from its head a curtain is looked across, as a share of its height, and how many rows either side are looked at with that one. */
    private static final float ACROSS_AT = 0.5f;
    private static final int ROWS_EITHER_SIDE = 2;
    /** Cloth shorter than this share of its width is a roll. */
    private static final float ROLLED = 0.45f;

    /** One curtain: places are shares of the wall's length, heights are in levels. */
    static final class Shape {
        float originU, originV, scale;
        boolean north, far;
        /** The whole of it, rod and cloth. */
        float start, end, foot, head;
        /** Two numbers a length of cloth: where along it starts and ends. None for cloth rolled up, which fills the whole. */
        float[] hangs = new float[0];
        float red, green, blue;
    }

    private record Asked(boolean north, boolean far, float originU, float originV, float scale, float left, float top, float width, float height) {}

    private static final Shape NONE = new Shape();
    private static final ConcurrentHashMap<Texture, Asked> asked = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Texture, Shape> known = new ConcurrentHashMap<>();

    private Drapes() {}

    /**
     * The curtain the sprite paints on an edge of its square: the north or west one, or the one
     * far from those. Null until a frame has been drawn since it was first asked for, and for a
     * sprite with no cloth on that edge.
     */
    static Shape of(Texture texture, SpriteGeometry.Frame frame, boolean north, boolean far) {
        Shape shape = known.get(texture);
        if (shape != null) {
            return shape == NONE ? null : shape;
        }
        if (asked.putIfAbsent(texture, new Asked(north, far, frame.originU, frame.originV, frame.scale, frame.left, frame.top, frame.width, frame.height)) == null) {
            SpriteColours.readAgain(texture);
        }
        return null;
    }

    /** Whether the sprite has still to be read. */
    static boolean unread(Texture texture) {
        return !known.containsKey(texture);
    }

    /** Reads the sprite if it was asked for: see Windows.read. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        Asked wanted = asked.get(texture);
        if (wanted == null || known.containsKey(texture)) {
            return;
        }
        int across = Windows.across(wanted.scale), high = Windows.high(wanted.scale);
        int[] at = new int[across * high];
        byte[] dots = Windows.laid(sheet, width, left, top, right, bottom, wanted.north, wanted.far, wanted.originU, wanted.originV, wanted.scale,
                wanted.left, wanted.top, wanted.width, wanted.height, at);
        Shape shape = measure(dots, across, high);
        if (shape == null) {
            known.put(texture, NONE);
            return;
        }
        // The colour most of the cloth has: the mean of the fullest band of brightness and the one either side of it.
        int[] bands = new int[Windows.BANDS];
        for (int i = 0; i < dots.length; i++) {
            if (dots[i] == Windows.FRAME) {
                bands[Windows.band(sheet, at[i])]++;
            }
        }
        int fullest = 0;
        for (int band = 1; band < Windows.BANDS; band++) {
            if (bands[band] > bands[fullest]) {
                fullest = band;
            }
        }
        long red = 0, green = 0, blue = 0;
        int count = 0;
        for (int i = 0; i < dots.length; i++) {
            if (dots[i] == Windows.FRAME && Math.abs(Windows.band(sheet, at[i]) - fullest) <= 1) {
                red += sheet.get(at[i]) & 0xff;
                green += sheet.get(at[i] + 1) & 0xff;
                blue += sheet.get(at[i] + 2) & 0xff;
                count++;
            }
        }
        shape.red = red / (255f * count);
        shape.green = green / (255f * count);
        shape.blue = blue / (255f * count);
        shape.north = wanted.north;
        shape.far = wanted.far;
        shape.originU = wanted.originU;
        shape.originV = wanted.originV;
        shape.scale = wanted.scale;
        known.put(texture, shape);
    }

    /**
     * The curtain in a sprite laid flat on its wall: dots holds across dots a row, the top row
     * first, and what is Windows.FRAME is cloth or rod. Null for a sprite with too little on it.
     */
    static Shape measure(byte[] dots, int across, int high) {
        int c0 = across, c1 = 0, r0 = high, r1 = 0;
        for (int row = 0; row < high; row++) {
            for (int column = 0; column < across; column++) {
                if (dots[row * across + column] == Windows.FRAME) {
                    c0 = Math.min(c0, column);
                    c1 = Math.max(c1, column + 1);
                    r0 = Math.min(r0, row);
                    r1 = Math.max(r1, row + 1);
                }
            }
        }
        if (c1 - c0 < NARROWEST || r1 - r0 < NARROWEST) {
            return null;
        }
        Shape shape = new Shape();
        shape.start = (float) c0 / across;
        shape.end = (float) c1 / across;
        shape.head = 1f - (float) r0 / high;
        shape.foot = 1f - (float) r1 / high;
        // Cloth much wider than it is long hangs nowhere: it is rolled up along the window's head.
        float long1 = (r1 - r0) * ViewCamera.LEVEL_HEIGHT / high, wide = (float) (c1 - c0) / across;
        if (long1 < ROLLED * wide) {
            return shape;
        }
        int middle = r0 + Math.round((r1 - r0) * ACROSS_AT);
        int from = Math.max(r0, middle - ROWS_EITHER_SIDE), to = Math.min(r1, middle + ROWS_EITHER_SIDE + 1);
        float[] hangs = new float[0];
        int run = -1, last = -1;
        for (int column = c0; column <= c1; column++) {
            boolean cloth = false;
            for (int row = from; row < to && column < c1; row++) {
                cloth |= dots[row * across + column] == Windows.FRAME;
            }
            if (cloth) {
                run = run < 0 ? column : run;
                last = column + 1;
            } else if (run >= 0 && (column == c1 || column - last >= WIDEST_GAP)) {
                if (last - run >= NARROWEST) {
                    hangs = Arrays.copyOf(hangs, hangs.length + 2);
                    hangs[hangs.length - 2] = (float) run / across;
                    hangs[hangs.length - 1] = (float) last / across;
                }
                run = -1;
            }
        }
        shape.hangs = hangs;
        return shape;
    }
}
