package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * The battens across a wall of upright boards, read off its sprite: a barn's or a shed's,
 * with two boards nailed along it a third and two thirds of the way up. The picture lays
 * such a wall board by board in one colour (see timber in the world's shader), and the
 * battens the sprite paints went with the rest of its strokes: WallBuilder lays them again.
 *
 * Set straight as Masonry sets a strip of the sprite, a wall of upright boards is the same
 * brightness all the way up, and a batten is a few rows lighter than the boards, the first
 * of them its lit top edge, much lighter, and the line of its shadow under it, dark. A wall
 * facing north paints the batten's face barely lighter than the boards, and the batten
 * nearer the foot of a wall facing west has no dark line over it: the lit edge and the
 * shadow are the two every one of them has. A wall of lapped boards is such lines all the
 * way up, and is no wall of upright boards. A gable's piece is read as nothing: its
 * battens are at heights of a wall a level high, which a gable is not.
 */
final class Battens {
    private static final int PAINTED = 128;
    private static final int STRIP = 24;
    private static final float FULL_ROW = 0.75f;
    private static final int FEWEST_ROWS = 32;
    /** A wall a level high, set straight, is this share of its sprite's height, or more: a gable's piece is less. */
    private static final float WHOLE_WALL = 0.75f;
    /** A row is the boards' own within QUIET of the usual row, and QUIETEST of the wall's rows are: lapped boards and brick are lines all the way up. */
    private static final float QUIET = 0.015f, QUIETEST = 0.6f;
    /** A batten's lit edge stands LIT over the usual row, its rows LIGHTER on average, and its shadow EDGE under it. */
    private static final float LIT = 0.04f, LIGHTER = 0.005f, EDGE = 0.025f;
    /** How deep a batten is, as a share of the wall's height, and how many a wall has at most. */
    private static final float SHALLOWEST = 0.025f, DEEPEST = 0.1f;
    private static final int MOST = 4;
    private static final float RED = 0.299f, GREEN = 0.587f, BLUE = 0.114f;
    private static final float[] NONE = new float[0];

    /** For each sprite read: the battens' foot and top in turn, each a share of the wall's height above its foot, lowest first. */
    private static final ConcurrentHashMap<Texture, float[]> known = new ConcurrentHashMap<>();

    private Battens() {}

    /**
     * The battens across the wall the sprite shows, as measure gives them: empty for a wall
     * with none, and null until a frame has been drawn since the sprite was first asked for.
     */
    static float[] of(Texture texture) {
        float[] battens = known.get(texture);
        if (battens == null) {
            SpriteColours.of(texture);
        }
        return battens;
    }

    /** Reads the sprite: the rectangle from (left, top) to before (right, bottom) of a sheet width dots wide, held as bytes of red, green, blue and cover. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        float[] battens = measure(sheet, width, left, top, right, bottom, texture.getHeightOrig());
        if (FirstPerson.TRACE && battens.length > 0) {
            Log.info("wall " + texture.getName() + " has " + battens.length / 2 + " battens, " + Arrays.toString(battens));
        }
        known.put(texture, battens);
    }

    /** As of(), for the rectangle of a sheet a sprite is, trimmed to what it paints from a picture whole high. */
    static float[] measure(ByteBuffer sheet, int width, int left, int top, int right, int bottom, int whole) {
        int across = right - left, high = bottom - top;
        if (across < STRIP || high < FEWEST_ROWS) {
            return NONE;
        }
        // The strip with the most of the wall in it: a sprite with a window in it is whole wall along one side.
        int[] painted = new int[across];
        for (int x = 0; x < across; x++) {
            for (int y = 0; y < high; y++) {
                if ((sheet.get(at(width, left + x, top + y) + 3) & 0xff) >= PAINTED) {
                    painted[x]++;
                }
            }
        }
        int from = 0, most = -1, inStrip = 0;
        for (int x = 0; x < across; x++) {
            inStrip += painted[x];
            if (x >= STRIP) {
                inStrip -= painted[x - STRIP];
            }
            if (x >= STRIP - 1 && inStrip > most) {
                most = inStrip;
                from = x - STRIP + 1;
            }
        }
        // Set straight the way that makes the rows differ most: the other smears a batten over the rows it crosses.
        float[] rows = null;
        float spread = -1f;
        for (int way = -1; way <= 1; way += 2) {
            float[] sum = new float[high + STRIP + 1];
            int[] count = new int[high + STRIP + 1];
            for (int dx = 0; dx < STRIP; dx++) {
                int shift = way > 0 ? (dx + 1) / 2 : -(dx / 2);
                for (int y = 0; y < high; y++) {
                    int at = at(width, left + from + dx, top + y);
                    if ((sheet.get(at + 3) & 0xff) >= PAINTED) {
                        int row = y - shift + STRIP / 2;
                        sum[row] += (RED * (sheet.get(at) & 0xff) + GREEN * (sheet.get(at + 1) & 0xff) + BLUE * (sheet.get(at + 2) & 0xff)) / 255f;
                        count[row]++;
                    }
                }
            }
            // Every row from the first full one to the last, a row the strip does not fill (a window in it) left out as NaN.
            int first = -1, last = -1, full = 0;
            for (int row = 0; row < count.length; row++) {
                if (count[row] >= FULL_ROW * STRIP) {
                    first = first < 0 ? row : first;
                    last = row;
                    full++;
                }
            }
            if (full < FEWEST_ROWS || last - first + 1 < WHOLE_WALL * whole) {
                continue;
            }
            float[] means = new float[last - first + 1];
            float mean = 0f;
            for (int i = 0; i < means.length; i++) {
                int row = first + i;
                means[i] = count[row] >= FULL_ROW * STRIP ? sum[row] / count[row] : Float.NaN;
                mean += Float.isNaN(means[i]) ? 0f : means[i] / full;
            }
            float differ = 0f;
            for (float value : means) {
                differ += Float.isNaN(value) ? 0f : (value - mean) * (value - mean);
            }
            if (differ > spread) {
                spread = differ;
                rows = means;
            }
        }
        return rows == null ? NONE : battens(rows);
    }

    /** The battens in a wall's rows of brightness set straight, the top row first and the foot's last, a row with no reading NaN: see measure. */
    static float[] battens(float[] rows) {
        int count = rows.length, read = 0;
        float[] sorted = new float[count];
        for (float row : rows) {
            if (!Float.isNaN(row)) {
                sorted[read++] = row;
            }
        }
        Arrays.sort(sorted, 0, read);
        float usual = sorted[read / 2];
        int quiet = 0;
        for (int i = 0; i < read; i++) {
            if (Math.abs(sorted[i] - usual) < QUIET) {
                quiet++;
            }
        }
        if (quiet < QUIETEST * read) {
            return NONE;
        }
        float[] found = new float[2 * MOST];
        int battens = 0;
        for (int row = 0; row < count; row++) {
            if (!(rows[row] - usual > LIT)) {
                continue;
            }
            // The shadow under it: the first dark row, no further down than a batten is deep and with nothing unread on the way.
            int shadow = row + 1;
            float light = rows[row] - usual;
            while (shadow < count && !Float.isNaN(rows[shadow]) && usual - rows[shadow] <= EDGE && shadow - row + 2f <= DEEPEST * count) {
                light += rows[shadow] - usual;
                shadow++;
            }
            float deep = (shadow - row + 2f) / count;
            if (shadow == count || Float.isNaN(rows[shadow]) || usual - rows[shadow] <= EDGE || light / (shadow - row) <= LIGHTER || deep < SHALLOWEST) {
                continue;
            }
            if (battens == MOST) {
                return NONE;
            }
            // The row over the lit edge is the batten's top: the dark line of its edge where it has one.
            found[2 * battens] = (count - 1 - shadow) / (float) (count - 1);
            found[2 * battens + 1] = (count - row) / (float) (count - 1);
            battens++;
            row = shadow;
            while (row + 1 < count && !Float.isNaN(rows[row + 1]) && usual - rows[row + 1] > EDGE) {
                row++;
            }
        }
        // Read top first, given lowest first.
        float[] given = new float[2 * battens];
        for (int i = 0; i < battens; i++) {
            given[2 * i] = found[2 * (battens - 1 - i)];
            given[2 * i + 1] = found[2 * (battens - 1 - i) + 1];
        }
        return given;
    }

    private static int at(int width, int x, int y) {
        return (y * width + x) * 4;
    }
}
