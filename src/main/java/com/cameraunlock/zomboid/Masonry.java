package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * What a wall is built of, read off its sprite: brick, boards lapped along it, cast blocks,
 * cut stone, or none of those.
 *
 * The game's picture of a brick wall is a few dots to a brick, and stood in front of it in
 * first person each brick is a blurred slab. The picture lays such a wall itself, brick by
 * brick, in the sprite's colours (see facBrick in the world's shader), and has to know which
 * walls to lay and how. A wall's sprite shows it from the side on the slant, so along a strip of the
 * sprite the courses step down a dot every two across. With the strip set straight, a wall
 * laid in courses is the same few rows over and over: every so many rows there is a row
 * lighter than the rest, the mortar, or darker, the shadow under a board. Between those rows a
 * brick wall has short upright lines of mortar where two bricks meet, and a boarded one has
 * none. Blocks are brick with courses three times as deep and faces all of one grey. Stone has
 * the upright lines and faces that differ from one stone to the next.
 *
 * A sprite's dots are held by OpenGL and are read on the thread that draws, from the sheet
 * SpriteColours copies: the build asks, and is told nothing until a frame has been drawn.
 */
final class Masonry {
    /** A dot is part of the wall from this much cover up. */
    private static final int PAINTED = 128;
    /** How many dots across the strip of a sprite that is read is. */
    private static final int STRIP = 24;
    /** How much of the strip a row has to be painted across to be read, and the fewest rows one after the other a wall is read from. */
    private static final float FULL_ROW = 0.75f;
    private static final int FEWEST_ROWS = 40;
    /** The least a sprite's rows differ in brightness, and the least its joints stand out from the rest, for it to be laid in courses at all. */
    private static final float LEAST_SPREAD = 0.012f, LEAST_JOINT = 0.02f;
    /** How like itself the wall is a whole course further up, from 0 to 1, for its courses to be evenly spaced. */
    private static final float EVEN = 0.4f;
    /** Upright joints to a course along the strip: a boarded wall has fewer than the first, a brick one at least the second, a stone one at least the third. */
    private static final float NO_UPRIGHTS = 0.1f, BRICK_UPRIGHTS = 0.3f, STONE_UPRIGHTS = 0.5f;
    private static final int LONGEST_COURSE = 48, SHORTEST_COURSE = 3;
    private static final float NEARLY = 0.7f, HALFWAY = 0.4f;
    /**
     * The deepest a course of brick is and the shallowest a course of blocks, in dots. Not a share of the sprite's height: a
     * parapet's sprite is half as high as a wall's and its bricks the same size.
     */
    private static final int DEEPEST_BRICK = 14, SHALLOWEST_BLOCK = 20;
    /** How much the faces between the joints differ among themselves, in brightness from 0 to 1: under the first they are cast blocks, over the second cut stone. */
    private static final float EVEN_FACE = 0.02f, ROUGH_FACE = 0.03f;
    private static final float RED = 0.299f, GREEN = 0.587f, BLUE = 0.114f;

    /** For each sprite read: how the picture lays it, as WallSides.laid gives it, or 0 for a wall that is shown as painted. */
    private static final ConcurrentHashMap<Texture, Integer> known = new ConcurrentHashMap<>();

    private Masonry() {}

    /**
     * How the picture lays the wall this sprite shows: a number from WallSides.laid, with the
     * wall's colour, its joints' and its way, BRICK, LAPPED, STONE or BLOCK. 0 for a wall shown as the game painted
     * it, and until a frame has been drawn since the sprite was first asked for.
     */
    static int of(Texture texture) {
        Integer laid = known.get(texture);
        if (laid == null) {
            SpriteColours.of(texture);
            return 0;
        }
        return laid;
    }

    /**
     * Whether a sprite is of a sheet of outside walls. Indoors a striped wallpaper reads as
     * boards and a tiled bathroom as brick, and both are right as the game painted them.
     */
    static boolean suits(String name) {
        return name != null && (name.startsWith("walls_exterior_house") || name.startsWith("walls_exterior_roofs") || name.startsWith("walls_house_")
                || name.startsWith("walls_commercial") || name.startsWith("walls_garage"));
    }

    /** Whether the sprite has still to be read: of() gives 0 for it, and may not once a frame has been drawn. */
    static boolean unread(Texture texture) {
        return !known.containsKey(texture);
    }

    /** Reads the sprite: the rectangle from (left, top) to before (right, bottom) of a sheet width dots wide, held as bytes of red, green, blue and cover. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        known.put(texture, measure(sheet, width, left, top, right, bottom));
    }

    /** As of(), for the rectangle of a sheet a sprite is. */
    static int measure(ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        int across = right - left, high = bottom - top;
        int strip = Math.min(STRIP, across);
        if (strip < STRIP / 3 || high < FEWEST_ROWS) {
            return 0;
        }
        // The strip with the most of the wall in it: a sprite with a window or a doorway in it
        // is whole wall along one side.
        int[] painted = new int[across];
        for (int x = 0; x < across; x++) {
            for (int y = 0; y < high; y++) {
                if (cover(sheet, width, left + x, top + y) >= PAINTED) {
                    painted[x]++;
                }
            }
        }
        int from = 0, most = -1, inStrip = 0;
        for (int x = 0; x < across; x++) {
            inStrip += painted[x];
            if (x >= strip) {
                inStrip -= painted[x - strip];
            }
            if (x >= strip - 1 && inStrip > most) {
                most = inStrip;
                from = x - strip + 1;
            }
        }
        // Which way the wall slants in the sprite is not known: both are tried, and the one
        // that sets its courses straight is the one whose rows differ most from each other.
        float[] rows = null;
        int firstRow = 0, slant = 0;
        float spread = -1f;
        for (int way = -1; way <= 1; way += 2) {
            float[] sum = new float[high + strip + 1];
            int[] count = new int[high + strip + 1];
            for (int dx = 0; dx < strip; dx++) {
                int shift = shift(way, dx);
                for (int y = 0; y < high; y++) {
                    int at = at(width, left + from + dx, top + y);
                    if ((sheet.get(at + 3) & 0xff) >= PAINTED) {
                        int row = y - shift + strip / 2;
                        sum[row] += bright(sheet, at);
                        count[row]++;
                    }
                }
            }
            int best = 0, bestLength = 0, start = 0;
            for (int row = 0; row <= count.length; row++) {
                if (row == count.length || count[row] < FULL_ROW * strip) {
                    if (row - start > bestLength) {
                        bestLength = row - start;
                        best = start;
                    }
                    start = row + 1;
                }
            }
            if (bestLength < FEWEST_ROWS) {
                continue;
            }
            float[] means = new float[bestLength];
            for (int i = 0; i < bestLength; i++) {
                means[i] = sum[best + i] / count[best + i];
            }
            float differ = deviation(means);
            if (differ > spread) {
                spread = differ;
                rows = means;
                firstRow = best - strip / 2;
                slant = way;
            }
        }
        if (rows == null || spread < LEAST_SPREAD) {
            return 0;
        }
        int count = rows.length;
        float[] sorted = rows.clone();
        Arrays.sort(sorted);
        float usual = sorted[count / 2];
        // The joints are the few rows far from the usual to one side: lighter for mortar,
        // darker for the shadow under a board.
        double lopsided = 0;
        float mean = 0f;
        for (float row : rows) {
            lopsided += Math.pow(row - usual, 3);
            mean += row / count;
        }
        float sign = lopsided > 0 ? 1f : -1f;
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = (rows[i] - usual) * sign;
        }
        float[] ranked = out.clone();
        Arrays.sort(ranked);
        float stands = ranked[Math.min(count - 1, (int) (count * 0.95f))];
        if (stands < LEAST_JOINT) {
            return 0;
        }
        // How like itself the wall is some rows further up, for the number of rows that suits best.
        float all = 0f;
        for (float row : rows) {
            all += (row - mean) * (row - mean);
        }
        float[] alike = new float[Math.min(LONGEST_COURSE, count / 2)];
        float even = 0f;
        for (int lag = SHORTEST_COURSE; lag < alike.length; lag++) {
            float both = 0f;
            for (int i = 0; i + lag < count; i++) {
                both += (rows[i] - mean) * (rows[i + lag] - mean);
            }
            alike[lag] = both / all * count / (count - lag);
            even = Math.max(even, alike[lag]);
        }
        // A course is the fewest rows that suit nearly as well as the best: twice and three
        // times a course suit as well as one.
        int course = SHORTEST_COURSE;
        // And the first that suits better than the one after it: on the way up to a peak a
        // few rows short of the course suit nearly as well as the course.
        while (course < alike.length - 1 && (alike[course] < NEARLY * even || alike[course] < alike[course + 1])) {
            course++;
        }
        // Brick laid half a brick along from course to course is most like itself two courses
        // up, where the upright joints come round again: half of that is the course, if the
        // wall is like itself there too.
        if (course % 2 == 0 && course / 2 >= SHORTEST_COURSE && course / 2 <= DEEPEST_BRICK && alike[course / 2] >= HALFWAY * even) {
            course /= 2;
        }
        // Between two joints is a course. Along it, an upright joint is a dot as far from
        // the usual as a joint is, with such dots above and below it for the course's height.
        int courses = 0, uprights = 0, faceDots = 0;
        double faceSquares = 0;
        long red = 0, green = 0, blue = 0;
        int jointDots = 0;
        for (int i = 0; i < count; ) {
            boolean joint = out[i] > 0.5f * stands;
            int end = i;
            while (end < count && out[end] > 0.5f * stands == joint) {
                end++;
            }
            for (int dx = 0; dx < strip; dx++) {
                int run = 0, longest = 0;
                for (int row = i; row < end; row++) {
                    int y = firstRow + row + shift(slant, dx);
                    if (y < 0 || y >= high) {
                        run = 0;
                        continue;
                    }
                    int at = at(width, left + from + dx, top + y);
                    if ((sheet.get(at + 3) & 0xff) < PAINTED) {
                        run = 0;
                        continue;
                    }
                    if (joint) {
                        red += sheet.get(at) & 0xff;
                        green += sheet.get(at + 1) & 0xff;
                        blue += sheet.get(at + 2) & 0xff;
                        jointDots++;
                    } else {
                        float off = bright(sheet, at) - usual;
                        run = off * sign > 0.5f * stands ? run + 1 : 0;
                        if (off * sign <= 0.5f * stands) {
                            faceSquares += off * off;
                            faceDots++;
                        }
                        longest = Math.max(longest, run);
                    }
                }
                if (!joint && end - i >= SHORTEST_COURSE && longest >= Math.max(SHORTEST_COURSE, end - i - 2)) {
                    uprights++;
                }
            }
            if (!joint && end - i >= SHORTEST_COURSE) {
                courses++;
            }
            i = end;
        }
        if (courses == 0 || jointDots == 0) {
            return 0;
        }
        float toACourse = uprights / (float) courses;
        float jointRed = red / (255f * jointDots), jointGreen = green / (255f * jointDots), jointBlue = blue / (255f * jointDots);
        // How much the dots of the faces between the joints differ among themselves: cast
        // blocks are one grey, and no two stones of a stone wall are the same.
        float mottle = (float) Math.sqrt(faceSquares / Math.max(1, faceDots));
        int how = even > EVEN && toACourse < NO_UPRIGHTS && sign < 0f ? WallSides.LAPPED
                : even > EVEN && toACourse >= BRICK_UPRIGHTS && course <= DEEPEST_BRICK ? WallSides.BRICK
                : even > EVEN && toACourse >= BRICK_UPRIGHTS && course >= SHALLOWEST_BLOCK && mottle < EVEN_FACE ? WallSides.BLOCK
                : toACourse >= STONE_UPRIGHTS && mottle >= ROUGH_FACE && course > DEEPEST_BRICK ? WallSides.STONE : 0;
        float[] body = new float[4];
        if (how == 0 || !WallSides.usualColour(sheet, width, left, top, right, bottom, body)) {
            return 0;
        }
        return WallSides.laid(body[0], body[1], body[2], jointRed, jointGreen, jointBlue, how);
    }

    /** How many dots down the wall's courses have gone dx dots along the strip, slanting one way or the other. */
    private static int shift(int way, int dx) {
        return way > 0 ? (dx + 1) / 2 : -(dx / 2);
    }

    private static int at(int width, int x, int y) {
        return (y * width + x) * 4;
    }

    private static int cover(ByteBuffer sheet, int width, int x, int y) {
        return sheet.get(at(width, x, y) + 3) & 0xff;
    }

    private static float bright(ByteBuffer sheet, int at) {
        return (RED * (sheet.get(at) & 0xff) + GREEN * (sheet.get(at + 1) & 0xff) + BLUE * (sheet.get(at + 2) & 0xff)) / 255f;
    }

    private static float deviation(float[] values) {
        float mean = 0f;
        for (float value : values) {
            mean += value / values.length;
        }
        float squares = 0f;
        for (float value : values) {
            squares += (value - mean) * (value - mean) / values.length;
        }
        return (float) Math.sqrt(squares);
    }
}
