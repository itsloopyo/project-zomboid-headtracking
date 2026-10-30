package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.properties.PropertyContainer;
import zombie.core.textures.Texture;
import zombie.iso.IsoDirections;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteManager;

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
    /** How many dots fewer than the fullest row a row may hold and still be wall all along, where the sprite's ends step on the slant. */
    private static final int RAGGED = 2;

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
    /** Each west wall sprite's north sheetmate, or the sprite itself where it has none: see mateOf. */
    private static final ConcurrentHashMap<Texture, Texture> mates = new ConcurrentHashMap<>();
    /** Each west wall's paper in its north sheetmate's paint: see of. */
    private static final ConcurrentHashMap<Texture, Paper> toned = new ConcurrentHashMap<>();

    private Papers() {}

    /**
     * What the wall sprite on the north or west edge of its square is papered in. Null until a
     * frame has been drawn since it was first asked for, for a sprite with too little wall
     * on that edge to tell, and for one with a figure on it. A sprite is read for the first edge it is asked for on.
     *
     * The game paints a west wall's sprite darker than the north wall's of the same paper, to
     * light it, by 0.70 to 0.90 as the sheet goes (walls_interior_house_03_16 is 154 to its
     * north mate's 198). The picture lights the wall itself, so a west paper keeps its stripes
     * in its north sheetmate's paint: read raw, a room's west walls, and the far skins taken
     * from them, were a shade darker than its north walls, one paper pale green beside white.
     */
    static Paper of(Texture texture, SpriteGeometry.Frame frame, boolean north) {
        Paper paper = own(texture, frame.originU, frame.originV, frame.scale, frame.left, frame.top, frame.width, frame.height, north);
        Texture mate = paper == null || north ? texture : mateOf(texture);
        if (mate == texture) {
            return paper;
        }
        Paper shaded = toned.get(texture);
        if (shaded != null) {
            return shaded;
        }
        Paper lit = own(mate, frame.originU, frame.originV, frame.scale, mate.getOffsetX(), mate.getOffsetY(), mate.getWidth(), mate.getHeight(), true);
        if (lit == null) {
            return known.containsKey(mate) ? paper : null;
        }
        return toned.computeIfAbsent(texture, unused -> toned(paper, lit));
    }

    private static Paper own(Texture texture, float originU, float originV, float scale, float left, float top, float width, float height, boolean north) {
        Paper paper = known.get(texture);
        if (paper != null) {
            return paper == NONE || paper.north != north || paper.figured ? null : paper;
        }
        if (asked.putIfAbsent(texture, new Asked(north, originU, originV, scale, left, top, width, height)) == null) {
            SpriteColours.readAgain(texture);
        }
        return null;
    }

    /**
     * The sprite still to be read before the paper is known: the sprite itself, or a west paper's north sheetmate, whose
     * reading is answered under its own name. Null where neither waits.
     */
    static Texture awaited(Texture texture) {
        Paper paper = known.get(texture);
        if (paper == null) {
            return texture;
        }
        Texture mate = paper == NONE || paper.north || paper.figured ? texture : mateOf(texture);
        return mate != texture && !known.containsKey(mate) ? mate : null;
    }

    /** The plain north wall that follows a west wall on its sheet (walls_interior_house_03_16, 03_17), or the texture itself for any other sprite. */
    private static Texture mateOf(Texture texture) {
        return mates.computeIfAbsent(texture, Papers::findMate);
    }

    private static Texture findMate(Texture texture) {
        IsoSprite sprite = texture.getName() == null ? null : IsoSpriteManager.instance.namedMap.get(texture.getName());
        if (sprite == null || !west(sprite.getProperties())) {
            return texture;
        }
        IsoSprite mate = IsoSpriteManager.instance.namedMap.get(WallSides.sheetmate(sprite.getName(), 1));
        Texture painted = mate == null || !north(mate.getProperties()) ? null : mate.getTextureForCurrentFrame(IsoDirections.N);
        return painted != null && WorldScene.usable(painted) ? painted : texture;
    }

    private static boolean west(PropertyContainer properties) {
        return !properties.has(IsoFlagType.WallNW) && (properties.has(IsoFlagType.WallW) || properties.has(IsoFlagType.DoorWallW)
                || properties.has(IsoFlagType.WindowW) || properties.has(IsoFlagType.windowW));
    }

    private static boolean north(PropertyContainer properties) {
        return !properties.has(IsoFlagType.WallNW) && (properties.has(IsoFlagType.WallN) || properties.has(IsoFlagType.DoorWallN)
                || properties.has(IsoFlagType.WindowN) || properties.has(IsoFlagType.windowN));
    }

    /** A west paper's stripes, each shaded as far from its north sheetmate's paint as the west sprite's whole is. */
    static Paper toned(Paper west, Paper north) {
        Paper paper = new Paper();
        paper.originU = west.originU;
        paper.originV = west.originV;
        paper.scale = west.scale;
        paper.north = west.north;
        paper.figured = west.figured;
        paper.ends = west.ends;
        float[] by = {north.red / Math.max(west.red, 0.01f), north.green / Math.max(west.green, 0.01f), north.blue / Math.max(west.blue, 0.01f)};
        paper.colours = new float[west.colours.length];
        for (int i = 0; i < west.colours.length; i++) {
            paper.colours[i] = Math.min(1f, west.colours[i] * by[i % 3]);
        }
        paper.red = north.red;
        paper.green = north.green;
        paper.blue = north.blue;
        return paper;
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
        boolean[] rows = wallRows(dots, across, high, Math.round(high * BELOW_TOP));
        for (int column = 0; column < across; column++) {
            // The colour most of the column has. The flowers of a figured paper are in it too, and are no part of the stripe.
            Arrays.fill(bands, 0);
            int painted = 0;
            for (int row = Math.round(high * BELOW_TOP); row < high; row++) {
                if (rows[row] && dots[row * across + column] == Windows.FRAME) {
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
                if (rows[row] && dots[row * across + column] == Windows.FRAME && Math.abs(Windows.band(sheet, at[row * across + column]) - fullest) <= 1) {
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
     * The rows of a sprite laid flat, across dots each, that a paper is read from: those from
     * first down that are wall all along, as full of it as the fullest row, above a doorway's head
     * and above and below a window. Beside an opening the sprite paints the shadow in its reveal
     * down most of a column, and the column read as a dark stripe up the wall to the ceiling. A
     * sprite with fewer than FEWEST such rows is read from all of them.
     */
    static boolean[] wallRows(byte[] dots, int across, int high, int first) {
        int[] wall = new int[high];
        int fullest = 0;
        for (int row = first; row < high; row++) {
            for (int column = 0; column < across; column++) {
                wall[row] += dots[row * across + column] == Windows.FRAME ? 1 : 0;
            }
            fullest = Math.max(fullest, wall[row]);
        }
        boolean[] rows = new boolean[high];
        int whole = 0;
        for (int row = first; row < high; row++) {
            rows[row] = wall[row] >= fullest - RAGGED;
            whole += rows[row] ? 1 : 0;
        }
        if (whole < FEWEST) {
            Arrays.fill(rows, first, high, true);
        }
        return rows;
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
