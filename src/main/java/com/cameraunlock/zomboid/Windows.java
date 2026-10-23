package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * What a window is built of, read off its sprite.
 *
 * The game paints a window as a few dozen dots: a frame four dots wide, bars one dot wide, and
 * a pale film for the glass with streaks of shine on it. Stood on a wall and looked at from a
 * pace away each dot is a block the size of a thumb. So the sprite is only measured: laid flat
 * on its wall, every dot is frame, glass or nothing, and the frame's four sides, the bars that
 * cross it and the panes between them are found as rectangles. The picture then builds the
 * window from those, with edges as sharp as the screen is.
 *
 * A sprite's dots are held by OpenGL and are read on the thread that draws, with the sheet
 * SpriteColours reads for it: the build asks, and is told nothing until a frame has been drawn.
 */
final class Windows {
    /** A dot is frame from this much cover up, and nothing below the lower: between them it is glass or the shine on it. */
    static final int FRAME_COVER = 180, LEAST_COVER = 38;
    static final byte EMPTY = 0, GLASS = 1, FRAME = 2;
    /** How much of a line of dots has to be frame for the line to be a bar, and for it to be the ragged edge of one. */
    private static final float BAR = 0.7f, BAR_EDGE = 0.35f;
    /** The fewest dots a window is across or high. */
    private static final int SMALLEST = 6;
    /**
     * How many dots, at the game's smallest tiles, a window's sprite is read past each end of
     * its square. The game paints a window a few dots off its square, where the sprite on the
     * next square or the wall's own covers the difference, so the frame can begin in the square
     * before and stop short of the end, the wall's opening running on to the end of the square.
     */
    static final int SPILL = 4;
    /** The fewest dots an arch rises over where it springs from. */
    private static final int LOWEST_ARCH = 3;
    /** How far in from a corner of its outline a frame with square corners is looked for, in dots. */
    private static final int CORNER = 4;
    /** The fewest dots a light between two bars is across or high. */
    private static final int THINNEST_LIGHT = 2;
    /** A pane with less glass in it than the lower has none, and with more than the upper is whole. */
    static final float NO_GLASS = 0.08f, WHOLE_GLASS = 0.85f;
    static final int BANDS = 16;

    /** One window: all places are shares of the wall's length, then heights in levels. */
    static final class Shape {
        /** The frame origin and scale the sprite was read with. */
        float originU, originV, scale;
        boolean north;
        /** The frame's outside, and how thick each of its sides is. */
        float start, end, foot, head, startWide, endWide, footHigh, headHigh;
        /** The colour the frame is painted in. */
        float red, green, blue;
        /** Four numbers a bar: where along it starts and ends, how high its foot and its head. */
        float[] bars = new float[0];
        /** Five numbers a pane: as a bar, then how much of it is glass. */
        float[] panes = new float[0];
        /**
         * For a window with an arched head, what stands over the square part of it, which then
         * ends at head: three numbers for each of a row of places evenly along it from start
         * to end, how high the frame's outside is there, how high its inside, and 1 where
         * there is glass under it. Null for a window with a level head.
         */
        float[] arch;
    }

    /** What a sprite is read with: how its frame lies on the wall, and where in the frame its texture is. */
    private record Asked(boolean north, float originU, float originV, float scale, float left, float top, float width, float height) {}

    private static final Shape NONE = new Shape();
    private static final ConcurrentHashMap<Texture, Asked> asked = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Texture, Shape> known = new ConcurrentHashMap<>();
    /** The sprites asked for on the other edge than the one they are kept for: a sprite stands on one edge, and is read for the first that asks. */
    private static final ConcurrentHashMap<Texture, Boolean> otherEdge = new ConcurrentHashMap<>();

    private Windows() {}

    /**
     * The window the sprite paints on the north or west edge of its square. Null until a frame
     * has been drawn since it was first asked for, and for a sprite that is no window in a
     * frame with four straight sides, nor such a frame under an arched head: a round window, a
     * sheet of stained glass.
     */
    static Shape of(Texture texture, SpriteGeometry.Frame frame, boolean north) {
        Shape shape = known.get(texture);
        if (shape != null && (shape == NONE || shape.north == north && shape.originU == frame.originU && shape.originV == frame.originV && shape.scale == frame.scale)) {
            return shape == NONE ? null : shape;
        }
        Asked before = asked.get(texture);
        if (before != null && before.north != north) {
            otherEdge.put(texture, Boolean.TRUE);
            return null;
        }
        Asked wanted = new Asked(north, frame.originU, frame.originV, frame.scale, frame.left, frame.top, frame.width, frame.height);
        if (!wanted.equals(asked.put(texture, wanted))) {
            known.remove(texture);
            SpriteColours.readAgain(texture);
        }
        return null;
    }

    /** Whether the sprite has still to be read: of() gives null for it, and will not once a frame has been drawn. */
    static boolean unread(Texture texture) {
        return !known.containsKey(texture) && !otherEdge.containsKey(texture);
    }

    /**
     * Reads the sprite if it was asked for: it is the rectangle from (left, top) to before
     * (right, bottom) of a sheet width dots wide, held as bytes of red, green, blue and cover.
     */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        Asked wanted = asked.get(texture);
        if (wanted == null || known.containsKey(texture)) {
            return;
        }
        int across = across(wanted.scale), high = high(wanted.scale), margin = Math.round(SPILL * wanted.scale);
        int[] at = new int[(across + 2 * margin) * high];
        byte[] dots = laid(sheet, width, left, top, right, bottom, wanted.north, false, wanted.originU, wanted.originV, wanted.scale,
                wanted.left, wanted.top, wanted.width, wanted.height, margin, at);
        Shape shape = measure(dots, across + 2 * margin, high);
        if (shape == null || shape.arch != null) {
            // An arch is read evenly from start to end, and one cut at the square's end would be read wrong: read the square alone.
            int wide = across + 2 * margin;
            byte[] square = new byte[across * high];
            int[] squareAt = new int[across * high];
            for (int row = 0; row < high; row++) {
                System.arraycopy(dots, row * wide + margin, square, row * across, across);
                System.arraycopy(at, row * wide + margin, squareAt, row * across, across);
            }
            dots = square;
            at = squareAt;
            shape = measure(dots, across, high);
        } else {
            shape = onSquare(shape, across, margin);
        }
        if (shape == null) {
            known.put(texture, NONE);
            return;
        }
        colour(sheet, dots, at, shape);
        shape.north = wanted.north;
        shape.originU = wanted.originU;
        shape.originV = wanted.originV;
        shape.scale = wanted.scale;
        known.put(texture, shape);
    }

    /** How many dots a sprite laid flat on a wall is across, and how many high. */
    static int across(float scale) {
        return Math.round(SpriteGeometry.PIXELS_ACROSS_PER_TILE * scale);
    }

    static int high(float scale) {
        return Math.round(SpriteGeometry.PIXELS_PER_LEVEL * scale);
    }

    /**
     * A sprite laid flat on the wall it is painted for: across(scale) dots a row, the top row
     * first, each EMPTY, GLASS or FRAME by how solid it is, and into at where in the sheet each
     * was read. The wall is the north or west edge of the sprite's square, or the edge far
     * from those. The sprite is the rectangle from (left, top) to before (right, bottom) of a
     * sheet width dots wide, held as bytes of red, green, blue and cover, and (frameLeft,
     * frameTop) and its size say where in its frame that rectangle is.
     */
    static byte[] laid(ByteBuffer sheet, int width, int left, int top, int right, int bottom, boolean north, boolean far,
            float originU, float originV, float scale, float frameLeft, float frameTop, float frameWidth, float frameHeight, int[] at) {
        return laid(sheet, width, left, top, right, bottom, north, far, originU, originV, scale, frameLeft, frameTop, frameWidth, frameHeight, 0, at);
    }

    /** As laid, with margin more dots read past each end of the edge, on the wall's line: a row is across(scale) + 2 * margin dots, its first margin before the square. */
    static byte[] laid(ByteBuffer sheet, int width, int left, int top, int right, int bottom, boolean north, boolean far,
            float originU, float originV, float scale, float frameLeft, float frameTop, float frameWidth, float frameHeight, int margin, int[] at) {
        int across = across(scale), high = high(scale), wide = across + 2 * margin;
        byte[] dots = new byte[wide * high];
        float other = far ? 1f : 0f;
        for (int row = 0; row < high; row++) {
            for (int column = 0; column < wide; column++) {
                float along = (column - margin + 0.5f) / across, up = 1f - (row + 0.5f) / high;
                float a = north ? along : other, b = north ? other : along;
                float u = (a - b) * SpriteGeometry.PIXELS_ACROSS_PER_TILE * scale + originU;
                float v = ((a + b) * SpriteGeometry.PIXELS_DOWN_PER_TILE - up * SpriteGeometry.PIXELS_PER_LEVEL) * scale + originV;
                int x = left + (int) Math.floor((u - frameLeft) / frameWidth * (right - left));
                int y = top + (int) Math.floor((v - frameTop) / frameHeight * (bottom - top));
                if (u < frameLeft || v < frameTop || x >= right || y >= bottom) {
                    continue;
                }
                int place = (y * width + x) * 4;
                int cover = sheet.get(place + 3) & 0xff;
                dots[row * wide + column] = cover >= FRAME_COVER ? FRAME : cover >= LEAST_COVER ? GLASS : EMPTY;
                at[row * wide + column] = place;
            }
        }
        return dots;
    }

    /** The colour most of the frame's dots have: the mean of the fullest band of brightness and the one either side of it. */
    static void colour(ByteBuffer sheet, byte[] dots, int[] at, Shape shape) {
        int[] bands = new int[BANDS];
        for (int i = 0; i < dots.length; i++) {
            if (dots[i] == FRAME) {
                bands[band(sheet, at[i])]++;
            }
        }
        int fullest = 0;
        for (int band = 1; band < BANDS; band++) {
            if (bands[band] > bands[fullest]) {
                fullest = band;
            }
        }
        long red = 0, green = 0, blue = 0;
        int count = 0;
        for (int i = 0; i < dots.length; i++) {
            if (dots[i] == FRAME && Math.abs(band(sheet, at[i]) - fullest) <= 1) {
                red += sheet.get(at[i]) & 0xff;
                green += sheet.get(at[i] + 1) & 0xff;
                blue += sheet.get(at[i] + 2) & 0xff;
                count++;
            }
        }
        shape.red = red / (255f * count);
        shape.green = green / (255f * count);
        shape.blue = blue / (255f * count);
    }

    static int band(ByteBuffer sheet, int at) {
        int bright = (299 * (sheet.get(at) & 0xff) + 587 * (sheet.get(at + 1) & 0xff) + 114 * (sheet.get(at + 2) & 0xff)) / 1000;
        return bright * BANDS / 256;
    }

    /**
     * The window in a sprite laid flat on its wall: dots holds across dots a row, the top row
     * first, each EMPTY, GLASS or FRAME. Null for what is no frame with four straight sides,
     * nor such a frame under an arched head.
     */
    static Shape measure(byte[] dots, int across, int high) {
        return measure(dots, across, high, false);
    }

    /** As measure, and with solidToo a rectangle that is frame all over is a shape too, one bar and no light: a door with no glass in it. */
    static Shape measure(byte[] dots, int across, int high, boolean solidToo) {
        int c0 = across, c1 = 0, r0 = high, r1 = 0;
        for (int row = 0; row < high; row++) {
            for (int column = 0; column < across; column++) {
                if (dots[row * across + column] == FRAME) {
                    c0 = Math.min(c0, column);
                    c1 = Math.max(c1, column + 1);
                    r0 = Math.min(r0, row);
                    r1 = Math.max(r1, row + 1);
                }
            }
        }
        // A few stray dots past the frame's edge, the soft end of a line, are no part of it.
        for (boolean trimmed = true; trimmed && c1 - c0 >= SMALLEST && r1 - r0 >= SMALLEST; ) {
            trimmed = false;
            if (framed(dots, across, r0, c0, c1, false) < BAR_EDGE && !joined(dots, across, r0, c0, c1)) {
                r0++;
                trimmed = true;
            } else if (framed(dots, across, r1 - 1, c0, c1, false) < BAR_EDGE && !joined(dots, across, r1 - 1, c0, c1)) {
                r1--;
                trimmed = true;
            } else if (framed(dots, across, c0, r0, r1, true) < BAR_EDGE && framed(dots, across, c0 + 1, r0, r1, true) >= BAR_EDGE) {
                c0++;
                trimmed = true;
            } else if (framed(dots, across, c1 - 1, r0, r1, true) < BAR_EDGE && framed(dots, across, c1 - 2, r0, r1, true) >= BAR_EDGE) {
                c1--;
                trimmed = true;
            }
        }
        return shaped(dots, across, high, c0, c1, r0, r1, solidToo, false);
    }

    /** The window whose outline is from column c0 to before c1 and row r0 to before r1. With openHead it has no head of its own: it is what stands under an arch. */
    private static Shape shaped(byte[] dots, int across, int high, int c0, int c1, int r0, int r1, boolean solidToo, boolean openHead) {
        if (c1 - c0 < SMALLEST || r1 - r0 < SMALLEST) {
            return null;
        }
        // The outermost line of a sprite's frame is every other dot of it, where the slant of
        // the game's view cuts its edge, and that slant cuts the corner of a deep sill short. So
        // a frame's corner is square when there is frame within a few dots of it, and an arch's
        // or a round window's is bare much further in than that.
        for (int corner = openHead ? 2 : 0; corner < 4; corner++) {
            boolean found = false;
            for (int in = 0; in < CORNER && !found; in++) {
                for (int down = 0; down < CORNER && !found; down++) {
                    int column = (corner & 1) == 0 ? c0 + in : c1 - 1 - in, row = corner < 2 ? r0 + down : r1 - 1 - down;
                    found = dots[row * across + column] == FRAME;
                }
            }
            if (!found) {
                return corner < 2 && !solidToo ? arched(dots, across, high, c0, c1, r0, r1) : null;
            }
        }
        float headHigh = openHead ? 0f : side(dots, across, c0, c1, r0, r1, false, false), footHigh = side(dots, across, c0, c1, r0, r1, false, true);
        // The stiles are read across the band between head and foot, where the lights are: over
        // the whole height a door with one small light high in it is frame in every column.
        int bandTop = r0 + (int) Math.ceil(headHigh), bandBottom = r1 - (int) Math.ceil(footHigh);
        if (bandBottom - bandTop < 2) {
            bandTop = r0;
            bandBottom = r1;
        }
        float startWide = side(dots, across, c0, c1, bandTop, bandBottom, true, false), endWide = side(dots, across, c0, c1, bandTop, bandBottom, true, true);
        Shape shape = new Shape();
        shape.start = (float) c0 / across;
        shape.end = (float) c1 / across;
        shape.head = 1f - (float) r0 / high;
        shape.foot = 1f - (float) r1 / high;
        if (solidToo && headHigh >= r1 - r0 - 1f) {
            shape.bars = with(shape.bars, shape.start, shape.end, shape.foot, shape.head);
            return shape;
        }
        // A length of a glazed wall has a head and a foot and may have no stile at either end: it runs on both ways.
        if (!openHead && headHigh < 1f || footHigh < 1f) {
            return null;
        }
        shape.startWide = startWide / across;
        shape.endWide = endWide / across;
        shape.headHigh = headHigh / high;
        shape.footHigh = footHigh / high;
        int left = c0 + (int) Math.ceil(startWide), right = c1 - (int) Math.ceil(endWide);
        int top = r0 + (int) Math.ceil(headHigh), bottom = r1 - (int) Math.ceil(footHigh);
        if (right - left < 2 || bottom - top < 2) {
            return null;
        }
        float[] rails = runs(dots, across, left, right, top, bottom, false);
        float[] bars = new float[0], panes = new float[0];
        for (int band = 0; band <= rails.length / 2; band++) {
            float from = band == 0 ? top : rails[band * 2 - 1], to = band == rails.length / 2 ? bottom : rails[band * 2];
            if (band > 0) {
                bars = with(bars, shape.start + shape.startWide, shape.end - shape.endWide, 1f - from / high, 1f - rails[band * 2 - 2] / high);
            }
            int first = (int) Math.ceil(from), last = (int) Math.floor(to);
            // Two bars with under two dots between them are one bar: the gap is the ragged line of each.
            if (last - first < THINNEST_LIGHT) {
                if (to > from) {
                    bars = with(bars, shape.start + shape.startWide, shape.end - shape.endWide, 1f - to / high, 1f - from / high);
                }
                continue;
            }
            float[] uprights = runs(dots, across, left, right, first, last, true);
            for (int cell = 0; cell <= uprights.length / 2; cell++) {
                float begin = cell == 0 ? left : uprights[cell * 2 - 1], finish = cell == uprights.length / 2 ? right : uprights[cell * 2];
                if (cell > 0) {
                    bars = with(bars, uprights[cell * 2 - 2] / across, begin / across, 1f - to / high, 1f - from / high);
                }
                if (Math.floor(finish) - Math.ceil(begin) < THINNEST_LIGHT) {
                    if (finish > begin) {
                        bars = with(bars, begin / across, finish / across, 1f - to / high, 1f - from / high);
                    }
                    continue;
                }
                int glass = 0, all = 0;
                for (int row = first; row < last; row++) {
                    for (int column = (int) Math.ceil(begin); column < (int) Math.floor(finish); column++) {
                        glass += dots[row * across + column] == EMPTY ? 0 : 1;
                        all++;
                    }
                }
                if (all == 0 || glass < NO_GLASS * all) {
                    continue;
                }
                panes = with(panes, begin / across, finish / across, 1f - to / high, 1f - from / high);
                panes = Arrays.copyOf(panes, panes.length + 1);
                panes[panes.length - 1] = glass >= WHOLE_GLASS * all ? 1f : (float) glass / all;
            }
        }
        shape.bars = bars;
        shape.panes = panes;
        return shape;
    }

    /**
     * A window with an arched head: the square part of it from where the arch springs down,
     * measured as any window is, and over that, at each column of dots, where the frame's
     * outside and inside are and whether there is glass under it. A bar that runs up into the
     * arch is frame all the way down its column. The bars that fan out across an arch are lost:
     * the glass under it is one light.
     */
    private static Shape arched(byte[] dots, int across, int high, int c0, int c1, int r0, int r1) {
        int spring = -1;
        for (int row = r0; row < r1 && spring < 0; row++) {
            if ((dots[row * across + c0] == FRAME || dots[row * across + c0 + 1] == FRAME) && (dots[row * across + c1 - 1] == FRAME || dots[row * across + c1 - 2] == FRAME)) {
                spring = row;
            }
        }
        if (spring - r0 < LOWEST_ARCH || r1 - spring < SMALLEST) {
            return null;
        }
        Shape shape = shaped(dots, across, high, c0, c1, spring, r1, false, true);
        if (shape == null) {
            return null;
        }
        float[] arch = new float[(c1 - c0) * 3];
        for (int column = c0; column < c1; column++) {
            int outer = r0;
            while (outer < spring && dots[outer * across + column] != FRAME) {
                outer++;
            }
            int inner = outer;
            while (inner < spring && dots[inner * across + column] == FRAME) {
                inner++;
            }
            int glass = 0;
            for (int row = inner; row < spring; row++) {
                glass += dots[row * across + column] == EMPTY ? 0 : 1;
            }
            int at = (column - c0) * 3;
            arch[at] = 1f - (float) outer / high;
            arch[at + 1] = 1f - (float) inner / high;
            arch[at + 2] = spring > inner && 2 * glass >= spring - inner ? 1f : 0f;
        }
        shape.arch = arch;
        return shape;
    }

    /**
     * The window measured over a row of across + 2 * margin dots, the first margin of them
     * before the square, put on its square: its places made shares of the square's length.
     * A frame that has a stile past one end and stops short of the other is moved back along
     * the edge by as much as it can, so its stile closes the end of the opening it stopped short
     * of, when it is as long as its square less the margin: a window of one square painted off
     * it, and not half of one two squares long. Then what is still past an end is cut off there,
     * where the next square's window or wall stands. An end with no stile, which runs on into
     * the window on the next square, is carried to the square's end when it stops within the
     * margin of it. Not for a window under an arch.
     */
    static Shape onSquare(Shape shape, int across, int margin) {
        int wide = across + 2 * margin;
        float before = (float) margin / across, scale = (float) wide / across;
        shape.start = shape.start * scale - before;
        shape.end = shape.end * scale - before;
        shape.startWide *= scale;
        shape.endWide *= scale;
        for (int at = 0; at < shape.bars.length; at += 4) {
            shape.bars[at] = shape.bars[at] * scale - before;
            shape.bars[at + 1] = shape.bars[at + 1] * scale - before;
        }
        for (int at = 0; at < shape.panes.length; at += 5) {
            shape.panes[at] = shape.panes[at] * scale - before;
            shape.panes[at + 1] = shape.panes[at + 1] * scale - before;
        }
        float move = 0f;
        boolean wholeSquare = shape.end - shape.start >= 1f - before;
        if (wholeSquare && shape.start < 0f && shape.startWide > 0f && shape.end < 1f) {
            move = Math.min(-shape.start, 1f - shape.end);
        } else if (wholeSquare && shape.end > 1f && shape.endWide > 0f && shape.start > 0f) {
            move = -Math.min(shape.end - 1f, shape.start);
        }
        shape.start += move;
        shape.end += move;
        for (int at = 0; at < shape.bars.length; at += 4) {
            shape.bars[at] += move;
            shape.bars[at + 1] += move;
        }
        for (int at = 0; at < shape.panes.length; at += 5) {
            shape.panes[at] += move;
            shape.panes[at + 1] += move;
        }
        if (shape.start < 0f) {
            shape.startWide = Math.max(shape.start + shape.startWide, 0f);
            shape.start = 0f;
        }
        if (shape.end > 1f) {
            shape.endWide = Math.max(shape.endWide - (shape.end - 1f), 0f);
            shape.end = 1f;
        }
        if (shape.startWide == 0f && shape.start > 0f && shape.start <= before) {
            float was = shape.start;
            shape.start = 0f;
            for (int at = 0; at < shape.bars.length; at += 4) {
                shape.bars[at] = Math.abs(shape.bars[at] - was) < RUNS_ON ? 0f : shape.bars[at];
            }
            for (int at = 0; at < shape.panes.length; at += 5) {
                shape.panes[at] = Math.abs(shape.panes[at] - was) < RUNS_ON ? 0f : shape.panes[at];
            }
        }
        if (shape.endWide == 0f && shape.end < 1f && shape.end >= 1f - before) {
            float was = shape.end;
            shape.end = 1f;
            for (int at = 0; at < shape.bars.length; at += 4) {
                shape.bars[at + 1] = Math.abs(shape.bars[at + 1] - was) < RUNS_ON ? 1f : shape.bars[at + 1];
            }
            for (int at = 0; at < shape.panes.length; at += 5) {
                shape.panes[at + 1] = Math.abs(shape.panes[at + 1] - was) < RUNS_ON ? 1f : shape.panes[at + 1];
            }
        }
        shape.bars = cut(shape.bars, 4);
        shape.panes = cut(shape.panes, 5);
        return shape;
    }

    /** A place along this near the end of a window that runs on is at that end. */
    private static final float RUNS_ON = 1e-4f;

    /** The bars or panes, each so many numbers, cut to the square: one that is wholly past an end is dropped. */
    private static float[] cut(float[] list, int each) {
        float[] kept = new float[list.length];
        int count = 0;
        for (int at = 0; at < list.length; at += each) {
            float from = Math.max(list[at], 0f), to = Math.min(list[at + 1], 1f);
            if (to <= from) {
                continue;
            }
            System.arraycopy(list, at, kept, count, each);
            kept[count] = from;
            kept[count + 1] = to;
            count += each;
        }
        return Arrays.copyOf(kept, count);
    }

    private static float[] with(float[] list, float a, float b, float c, float d) {
        list = Arrays.copyOf(list, list.length + 4);
        list[list.length - 4] = a;
        list[list.length - 3] = b;
        list[list.length - 2] = c;
        list[list.length - 1] = d;
        return list;
    }

    /** Whether a row holds three dots of frame side by side: the crown of an arch does, and a scatter of stray dots does not. */
    private static boolean joined(byte[] dots, int across, int row, int c0, int c1) {
        for (int column = c0, run = 0; column < c1; column++) {
            run = dots[row * across + column] == FRAME ? run + 1 : 0;
            if (run >= 3) {
                return true;
            }
        }
        return false;
    }

    /** How much of a line of dots is frame: a column from row r0 to before r1, or a row from column c0 to before c1. */
    private static float framed(byte[] dots, int across, int line, int from, int to, boolean upright) {
        int frame = 0;
        for (int i = from; i < to; i++) {
            frame += dots[upright ? i * across + line : line * across + i] == FRAME ? 1 : 0;
        }
        return (float) frame / (to - from);
    }

    /**
     * How thick one side of the frame is, in dots: the lines of it that are frame nearly all
     * the way, counted in from the side, and a half for a ragged line before them. A line
     * after them that is part frame is the bars that meet the side, and no part of it.
     */
    private static float side(byte[] dots, int across, int c0, int c1, int r0, int r1, boolean upright, boolean fromEnd) {
        int first = upright ? c0 : r0, last = upright ? c1 : r1;
        float thick = 0f;
        boolean solid = false;
        for (int step = 0; step < last - first; step++) {
            int line = fromEnd ? last - 1 - step : first + step;
            float share = upright ? framed(dots, across, line, r0, r1, true) : framed(dots, across, line, c0, c1, false);
            if (share >= BAR) {
                thick += 1f;
                solid = true;
            } else if (share >= BAR_EDGE && step == 0) {
                thick = 0.5f;
            } else {
                break;
            }
        }
        return solid ? thick : 0f;
    }

    /**
     * The bars across a part of the window, upright ones or level ones: for each, the line it
     * starts at and the line it ends before, a ragged line on either side of it counting as
     * half. A bar one dot thick that the slant of the game's view has painted across two lines,
     * half on each, is one line thick between them.
     */
    private static float[] runs(byte[] dots, int across, int c0, int c1, int r0, int r1, boolean upright) {
        int first = upright ? c0 : r0, last = upright ? c1 : r1;
        float[] shares = new float[last - first + 2];
        for (int line = first; line < last; line++) {
            shares[line - first + 1] = upright ? framed(dots, across, line, r0, r1, true) : framed(dots, across, line, c0, c1, false);
        }
        float[] found = new float[0];
        for (int line = first; line < last; line++) {
            int at = line - first + 1;
            if (shares[at] >= BAR) {
                int end = line;
                while (end < last && shares[end - first + 1] >= BAR) {
                    end++;
                }
                found = Arrays.copyOf(found, found.length + 2);
                found[found.length - 2] = line - (shares[at - 1] >= BAR_EDGE ? 0.5f : 0f);
                found[found.length - 1] = end + (shares[end - first + 1] >= BAR_EDGE ? 0.5f : 0f);
                line = end;
            } else if (shares[at] >= BAR_EDGE && shares[at + 1] >= BAR_EDGE && shares[at + 1] < BAR && shares[at + 2] < BAR_EDGE && shares[at - 1] < BAR_EDGE) {
                found = Arrays.copyOf(found, found.length + 2);
                found[found.length - 2] = line + 0.5f;
                found[found.length - 1] = line + 1.5f;
                line++;
            }
        }
        return found;
    }
}
