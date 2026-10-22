package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * What an inside wall is papered or painted in, read off its sprite.
 *
 * The game's inside walls are one colour, or upright stripes of two or three, painted sixty
 * dots to a wall's length. Magnified to a wall a pace away, the edge of a stripe is a soft
 * smear a finger wide. But a stripe is the same from the skirting to the ceiling, so all a
 * wall's sprite says is which colour it is at each place along it: laid flat on its wall (see
 * Windows), each column of dots has a colour, and columns of one colour side by side are a
 * stripe. The picture builds the wall as those stripes, each a face of its own with a straight
 * edge. A paper with a figure on it, roses or sprigs, is no stripes: its wall shows the
 * sprite's own dots, as a figured tile does (see WallTiles).
 */
final class Papers {
    /** The fewest dots of wall a column has to hold for its colour to be taken, and how much of the wall's height below its top is left out, where the sprite shades it. */
    private static final int FEWEST = 8;
    private static final float BELOW_TOP = 0.06f;
    /** Two columns further apart in any of red, green and blue than this are two stripes, and the most stripes a wall's length is cut into. */
    private static final float SAME = 0.03f;
    private static final int MOST = 16;
    /** A paper with more than this share of its dots off the colour of their column has a figure on it. */
    private static final float FIGURED = 0.22f;

    /** One sprite's wall: n stripes, the first starting at 0 and the last ending at 1. */
    static final class Paper {
        float originU, originV, scale;
        boolean north;
        /** Where each stripe ends, as a share of the wall's length. */
        float[] ends;
        /** Each stripe's red, green and blue. */
        float[] colours;
        /** Whether the paper has a figure on it, flowers or sprigs, over its stripes. */
        boolean figured;
        /** The colour of the whole, which the stripes are told as shades of. */
        float red, green, blue;
    }

    private record Asked(boolean north, float originU, float originV, float scale, float left, float top, float width, float height) {}

    private static final Paper NONE = new Paper();
    private static final ConcurrentHashMap<Texture, Asked> asked = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Texture, Paper> known = new ConcurrentHashMap<>();

    private Papers() {}

    /**
     * What the wall sprite on the north or west edge of its square is papered in. Null until a
     * frame has been drawn since it was first asked for, for a sprite with too little wall
     * on that edge to tell, and for one with a figure on it. A sprite is read for the first edge it is asked for on.
     */
    static Paper of(Texture texture, SpriteGeometry.Frame frame, boolean north) {
        Paper paper = known.get(texture);
        if (paper != null) {
            return paper == NONE || paper.north != north || paper.figured ? null : paper;
        }
        if (asked.putIfAbsent(texture, new Asked(north, frame.originU, frame.originV, frame.scale, frame.left, frame.top, frame.width, frame.height)) == null) {
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
        byte[] dots = Windows.laid(sheet, width, left, top, right, bottom, wanted.north, false, wanted.originU, wanted.originV, wanted.scale,
                wanted.left, wanted.top, wanted.width, wanted.height, at);
        float[] columns = new float[across * 3];
        boolean[] told = new boolean[across];
        int odd = 0, all = 0;
        int[] bands = new int[Windows.BANDS];
        for (int column = 0; column < across; column++) {
            // The colour most of the column has. The shadow in the reveal of a window and the
            // flowers of a figured paper are in it too, and are no part of the stripe.
            Arrays.fill(bands, 0);
            int painted = 0;
            for (int row = Math.round(high * BELOW_TOP); row < high; row++) {
                if (dots[row * across + column] == Windows.FRAME) {
                    bands[Windows.band(sheet, at[row * across + column])]++;
                    painted++;
                }
            }
            int fullest = 0;
            for (int band = 1; band < Windows.BANDS; band++) {
                fullest = bands[band] > bands[fullest] ? band : fullest;
            }
            long red = 0, green = 0, blue = 0;
            int count = 0;
            for (int row = Math.round(high * BELOW_TOP); row < high; row++) {
                if (dots[row * across + column] == Windows.FRAME && Math.abs(Windows.band(sheet, at[row * across + column]) - fullest) <= 1) {
                    int place = at[row * across + column];
                    red += sheet.get(place) & 0xff;
                    green += sheet.get(place + 1) & 0xff;
                    blue += sheet.get(place + 2) & 0xff;
                    count++;
                }
            }
            told[column] = painted >= FEWEST;
            if (told[column]) {
                odd += painted - count;
                all += painted;
                columns[column * 3] = red / (255f * count);
                columns[column * 3 + 1] = green / (255f * count);
                columns[column * 3 + 2] = blue / (255f * count);
            }
        }
        Paper paper = striped(columns, told);
        if (FirstPerson.TRACE) {
            Log.info("paper " + texture.getName() + (wanted.north ? " north" : " west") + " off colour " + odd + " of " + all + (paper == null ? " is too little wall" : " stripes end at " + Arrays.toString(paper.ends)
                    + " in " + Arrays.toString(paper.colours)));
        }
        if (paper == null) {
            known.put(texture, NONE);
            return;
        }
        paper.figured = odd > FIGURED * all;
        paper.north = wanted.north;
        paper.originU = wanted.originU;
        paper.originV = wanted.originV;
        paper.scale = wanted.scale;
        known.put(texture, paper);
    }

    /**
     * The stripes in a row of columns: columns holds a red, green and blue for each, and told
     * says which were read. A column not read, where a door or the end of a short wall is,
     * takes the colour of the nearest that was. Null when under a quarter were read.
     */
    static Paper striped(float[] columns, boolean[] told) {
        int across = told.length, read = 0;
        for (boolean one : told) {
            read += one ? 1 : 0;
        }
        if (read * 4 < across) {
            return null;
        }
        float[] colours = columns.clone();
        for (int column = 0; column < across; column++) {
            if (told[column]) {
                continue;
            }
            int nearest = -1;
            for (int away = 1; away < across && nearest < 0; away++) {
                nearest = column - away >= 0 && told[column - away] ? column - away : column + away < across && told[column + away] ? column + away : -1;
            }
            System.arraycopy(columns, nearest * 3, colours, column * 3, 3);
        }
        Paper paper = new Paper();
        for (int column = 0; column < across; column++) {
            paper.red += colours[column * 3] / across;
            paper.green += colours[column * 3 + 1] / across;
            paper.blue += colours[column * 3 + 2] / across;
        }
        // A paper of many soft shades, a pattern of flowers, is cut more coarsely until it is a few stripes.
        int[] starts = new int[across + 1];
        int stripes = 0;
        for (float same = SAME; ; same *= 1.5f) {
            stripes = 0;
            for (int column = 0; column < across; column++) {
                int first = stripes == 0 ? -1 : starts[stripes - 1];
                if (first < 0 || apart(colours, first, column) > same) {
                    starts[stripes++] = column;
                }
            }
            if (stripes <= MOST) {
                break;
            }
        }
        starts[stripes] = across;
        // The one column between two stripes that is a mix of both is no stripe: each takes half of it.
        float[] ends = new float[stripes], shades = new float[stripes * 3];
        int kept = 0;
        for (int stripe = 0; stripe < stripes; stripe++) {
            int from = starts[stripe], to = starts[stripe + 1];
            if (to - from == 1 && stripe > 0 && stripe + 1 < stripes && starts[stripe + 2] - to > 1 && kept > 0) {
                ends[kept - 1] = (from + 0.5f) / across;
                continue;
            }
            for (int column = from; column < to; column++) {
                for (int part = 0; part < 3; part++) {
                    shades[kept * 3 + part] += colours[column * 3 + part] / (to - from);
                }
            }
            ends[kept++] = (float) to / across;
        }
        ends[kept - 1] = 1f;
        // A sliver at either end is the next wall showing at the corner, and is no stripe of this one.
        if (kept > 1 && ends[0] * across < 1.5f) {
            System.arraycopy(ends, 1, ends, 0, kept - 1);
            System.arraycopy(shades, 3, shades, 0, (kept - 1) * 3);
            kept--;
        }
        if (kept > 1 && (1f - ends[kept - 2]) * across < 1.5f) {
            kept--;
            ends[kept - 1] = 1f;
        }
        paper.ends = Arrays.copyOf(ends, kept);
        paper.colours = Arrays.copyOf(shades, kept * 3);
        return paper;
    }

    private static float apart(float[] colours, int one, int other) {
        return Math.max(Math.abs(colours[one * 3] - colours[other * 3]),
                Math.max(Math.abs(colours[one * 3 + 1] - colours[other * 3 + 1]), Math.abs(colours[one * 3 + 2] - colours[other * 3 + 2])));
    }
}
