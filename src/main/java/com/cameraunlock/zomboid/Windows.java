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
    private static final int FRAME_COVER = 180, LEAST_COVER = 38;
    private static final byte EMPTY = 0, GLASS = 1, FRAME = 2;
    /** How much of a line of dots has to be frame for the line to be a bar, and for it to be the ragged edge of one. */
    private static final float BAR = 0.7f, BAR_EDGE = 0.35f;
    /** The fewest dots a window is across or high. */
    private static final int SMALLEST = 6;
    /** How far in from a corner of its outline a frame with square corners is looked for, in dots. */
    private static final int CORNER = 4;
    /** The fewest dots a light between two bars is across or high. */
    private static final int THINNEST_LIGHT = 2;
    /** A pane with less glass in it than the lower has none, and with more than the upper is whole. */
    static final float NO_GLASS = 0.08f, WHOLE_GLASS = 0.85f;
    private static final int BANDS = 16;

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
     * frame with four straight sides: an arch, a fanlight, a sheet of stained glass.
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
        int across = Math.round(SpriteGeometry.PIXELS_ACROSS_PER_TILE * wanted.scale), high = Math.round(SpriteGeometry.PIXELS_PER_LEVEL * wanted.scale);
        byte[] dots = new byte[across * high];
        int[] at = new int[across * high];
        for (int row = 0; row < high; row++) {
            for (int column = 0; column < across; column++) {
                float along = (column + 0.5f) / across, up = 1f - (row + 0.5f) / high;
                float u = (wanted.north ? along : -along) * SpriteGeometry.PIXELS_ACROSS_PER_TILE * wanted.scale + wanted.originU;
                float v = (along * SpriteGeometry.PIXELS_DOWN_PER_TILE - up * SpriteGeometry.PIXELS_PER_LEVEL) * wanted.scale + wanted.originV;
                int x = left + (int) Math.floor((u - wanted.left) / wanted.width * (right - left));
                int y = top + (int) Math.floor((v - wanted.top) / wanted.height * (bottom - top));
                if (u < wanted.left || v < wanted.top || x >= right || y >= bottom) {
                    continue;
                }
                int place = (y * width + x) * 4;
                int cover = sheet.get(place + 3) & 0xff;
                dots[row * across + column] = cover >= FRAME_COVER ? FRAME : cover >= LEAST_COVER ? GLASS : EMPTY;
                at[row * across + column] = place;
            }
        }
        Shape shape = measure(dots, across, high);
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

    /** The colour most of the frame's dots have: the mean of the fullest band of brightness and the one either side of it. */
    private static void colour(ByteBuffer sheet, byte[] dots, int[] at, Shape shape) {
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

    private static int band(ByteBuffer sheet, int at) {
        int bright = (299 * (sheet.get(at) & 0xff) + 587 * (sheet.get(at + 1) & 0xff) + 114 * (sheet.get(at + 2) & 0xff)) / 1000;
        return bright * BANDS / 256;
    }

    /**
     * The window in a sprite laid flat on its wall: dots holds across dots a row, the top row
     * first, each EMPTY, GLASS or FRAME. Null for what is no frame with four straight sides.
     */
    static Shape measure(byte[] dots, int across, int high) {
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
            if (framed(dots, across, r0, c0, c1, false) < BAR_EDGE) {
                r0++;
                trimmed = true;
            } else if (framed(dots, across, r1 - 1, c0, c1, false) < BAR_EDGE) {
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
        if (c1 - c0 < SMALLEST || r1 - r0 < SMALLEST) {
            return null;
        }
        // The outermost line of a sprite's frame is every other dot of it, where the slant of
        // the game's view cuts its edge, and that slant cuts the corner of a deep sill short. So
        // a frame's corner is square when there is frame within a few dots of it, and an arch's
        // or a round window's is bare much further in than that.
        for (int corner = 0; corner < 4; corner++) {
            boolean found = false;
            for (int in = 0; in < CORNER && !found; in++) {
                for (int down = 0; down < CORNER && !found; down++) {
                    int column = (corner & 1) == 0 ? c0 + in : c1 - 1 - in, row = corner < 2 ? r0 + down : r1 - 1 - down;
                    found = dots[row * across + column] == FRAME;
                }
            }
            if (!found) {
                return null;
            }
        }
        float headHigh = side(dots, across, c0, c1, r0, r1, false, false), footHigh = side(dots, across, c0, c1, r0, r1, false, true);
        float startWide = side(dots, across, c0, c1, r0, r1, true, false), endWide = side(dots, across, c0, c1, r0, r1, true, true);
        if (headHigh < 1f || footHigh < 1f || startWide + endWide < 1f) {
            return null;
        }
        Shape shape = new Shape();
        shape.start = (float) c0 / across;
        shape.end = (float) c1 / across;
        shape.head = 1f - (float) r0 / high;
        shape.foot = 1f - (float) r1 / high;
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

    private static float[] with(float[] list, float a, float b, float c, float d) {
        list = Arrays.copyOf(list, list.length + 4);
        list[list.length - 4] = a;
        list[list.length - 3] = b;
        list[list.length - 2] = c;
        list[list.length - 1] = d;
        return list;
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
