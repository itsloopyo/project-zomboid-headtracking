package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * Skirting boards, rails and cornices along an inside wall, built as mouldings.
 *
 * The map lays these over a wall as a sprite of their own (walls_interior_detailing_*): a
 * white or a wooden board along the foot of the wall, a cove under the ceiling, a rail between.
 * A pace away the sprite's few dots a level are a smear with a dark outline, so each band of it
 * is built as a moulding standing out of the wall in the band's colour. Stood square (see
 * Flats.stood), a band is a run of rows painted along much of the wall; where a band stops
 * along the wall, at a doorway, the moulding stops too. A band taller than TALLEST is a
 * panel: one painted evenly is built as a board, and one with a pattern, brick, blocks or tiles, is
 * a surface and not a moulding, and the sprite keeps it all.
 */
final class Trims {
    /** The tallest band built as a moulding, in levels: past it a band is a panel. */
    static final float TALLEST = 0.14f;
    /** A panel is painted evenly where the spread of its dots' brightness is under this share of their mean: brick and tiles have courses and joints. */
    private static final float EVEN = 0.12f;
    /**
     * Cast blocks are as even as paint, and their joints are a dark row over a light one a dot
     * each: a row this much of its brightness off the rows two either side of it is a joint, and
     * a panel with one is blocks. A few rows at its top and foot are its outline and its shadow.
     */
    private static final float JOINT = 0.06f;
    private static final int OUTLINE_ROWS = 3;
    /**
     * How far a cornice falls down the wall, in tiles. The game draws a cornice as seen from
     * above, its depth along the ceiling shown as height, so its band says less of its size
     * than of how it is drawn: 0.10 to 0.12 of a level by sprite, for what is one moulding
     * round a room. Every cornice is this one.
     */
    static final float CORNICE_FALL = 0.13f;
    /** The heights a band's foot and top are rounded to, in levels: a band reads a dot higher on one edge than the other, and the two meet in a corner. */
    private static final float HEIGHTS = 32f;
    /** A row is part of a band where this much of the wall's length is painted, and a band shorter than FEWEST_ROWS is a stray line. */
    private static final float ROW_PAINTED = 0.15f;
    private static final int FEWEST_ROWS = 2;
    /** A column is part of a band where this much of the band's rows is painted, and a gap along the wall wider than GAP columns splits the band in two. */
    private static final float COLUMN_PAINTED = 0.5f;
    private static final int GAP = 3;
    /** A band starting or stopping this near a level's foot or top, or a wall's end, is taken to reach it. */
    private static final float SNAP = 0.04f;
    /** How far the skirting and a rail stand out of the wall's skin, and the round of a skirting's top edge and a rail's, in tiles. */
    static final float BOARD = 0.016f, ROUND = 0.007f;
    /**
     * How much darker the game paints a west wall's sprite than the north wall's of the same
     * finish, to light it: a white skirting reads 0.66 on a west wall and 0.77 on a north, a
     * wooden one 0.30 and 0.34. The moulding is lit by the picture, and takes the north's paint.
     */
    static final float WEST_SHADE = 0.87f;
    /** How far a cornice's fillet stands out of the skin, in tiles. */
    static final float FILLET = 0.005f;

    /** One band of a trim sprite: from foot to top in levels over its floor, from from to to along its wall's length, in its colour. */
    record Band(float foot, float top, float from, float to, float red, float green, float blue) {}

    private record Asked(Texture texture, boolean north) {}

    private record Frame(float originU, float originV, float scale, float left, float top, float width, float height) {}

    private static final Band[] NONE = new Band[0];
    private static final ConcurrentHashMap<Asked, Frame> asked = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Asked, Band[]> known = new ConcurrentHashMap<>();

    private Trims() {}

    /** Whether the sprite is laid over an inside wall as trim. */
    static boolean is(String name) {
        return name != null && name.startsWith("walls_interior_detailing");
    }

    /**
     * Whether the trim sprite is built as mouldings. The bathroom sheets are tiled wainscots,
     * whose joints are a few dots of a level apart: read as bands they come out as one even
     * panel, a plain board in place of the tiles. They are WallTiles'.
     */
    static boolean moulded(String name) {
        return is(name) && !WallTiles.tiledSheet(name);
    }

    /**
     * The bands the sprite paints along the north or the west edge of its square. Null until a
     * frame has been drawn since it was first asked for; empty for a sprite that is panelling,
     * or paints nothing along that edge.
     */
    static Band[] of(Texture texture, SpriteGeometry.Frame frame, boolean north) {
        Asked key = new Asked(texture, north);
        Band[] bands = known.get(key);
        if (bands != null) {
            return bands;
        }
        if (asked.putIfAbsent(key, new Frame(frame.originU, frame.originV, frame.scale, frame.left, frame.top, frame.width, frame.height)) == null) {
            SpriteColours.readAgain(texture);
        }
        return null;
    }

    /** The bands of a sprite along an edge as far as they have been read, without asking for them: null if not read yet. */
    static Band[] known(Texture texture, boolean north) {
        return known.get(new Asked(texture, north));
    }

    /** Whether a band is a cornice: one under the ceiling, which falls CORNICE_FALL down the wall whatever its band. */
    static boolean cornice(Band band) {
        return band.top >= 1f && band.foot > 0f;
    }

    /** How high over its floor a band's moulding comes down to, in levels. */
    static float footOf(Band band) {
        return cornice(band) ? band.top - CORNICE_FALL / ViewCamera.LEVEL_HEIGHT : band.foot;
    }

    /** How far a band's moulding stands out of the wall's skin at most, in tiles. */
    static float depth(Band band) {
        float[] section = section(band);
        float most = 0f;
        for (int i = 0; i < section.length; i += 2) {
            most = Math.max(most, section[i]);
        }
        return most;
    }

    /**
     * How much further out of the wall's skin than on a bare wall something hung over the wall
     * from from to to along it and from bottom to top up it, in levels, has to stand to be clear
     * of the mouldings of the bands, in tiles: the depth of the deepest it is over.
     */
    static float clearOf(Band[] bands, float from, float to, float bottom, float top) {
        float most = 0f;
        for (Band band : bands) {
            if (band.from < to && band.to > from && footOf(band) < top && band.top > bottom) {
                most = Math.max(most, depth(band));
            }
        }
        return most;
    }

    /**
     * Whether two bands, one on each wall of a corner, are one moulding turning the corner and
     * meet there in a mitre: both reach the corner, and they are the same moulding, which for a
     * board is the same foot and top give or take a reading's rounding.
     */
    static boolean turnsTheCorner(Band one, Band other) {
        if (one.from > 0f || other.from > 0f || cornice(one) != cornice(other)) {
            return false;
        }
        return cornice(one) || Math.abs(one.foot - other.foot) <= 1.01f / HEIGHTS && Math.abs(one.top - other.top) <= 1.01f / HEIGHTS;
    }

    /** Reads the sprite for each edge it was asked for on: see Windows.read. On the thread that draws. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        for (int edge = 0; edge < 2; edge++) {
            Asked key = new Asked(texture, edge == 0);
            Frame wanted = asked.get(key);
            if (wanted == null || known.containsKey(key)) {
                continue;
            }
            byte[] dots = Flats.stood(sheet, width, left, top, right, bottom, key.north, wanted.originU, wanted.originV, wanted.scale,
                    wanted.left, wanted.top, wanted.width, wanted.height);
            Band[] bands = dots == null ? NONE : bands(dots, Flats.WALL_LONG, Flats.WALL_HIGH, key.north ? 1f : 1f / WEST_SHADE);
            if (FirstPerson.TRACE) {
                Log.info("trim " + texture.getName() + (key.north ? " north" : " west") + (bands.length == 0 ? " is left to its sprite" : " is " + Arrays.toString(bands)));
            }
            known.put(key, bands);
        }
    }

    /** Render thread, as Flats.letGo: what was read is kept by sprite, and asked for again by the next build. */
    static void letGo() {
        asked.clear();
        known.clear();
    }

    /**
     * The bands of a length of wall stood square: long dots a row, high rows from the top of a
     * level down to its floor, four bytes a dot with the cover last, their colours multiplied
     * by lighter. Empty when a band is taller than TALLEST, or nothing is painted in bands.
     */
    static Band[] bands(byte[] dots, int long_, int high, float lighter) {
        List<Band> found = new ArrayList<>();
        int row = 0;
        while (row < high) {
            if (!rowInBand(dots, long_, row)) {
                row++;
                continue;
            }
            int first = row;
            while (row < high && rowInBand(dots, long_, row)) {
                row++;
            }
            int last = row - 1;
            if (last - first + 1 < FEWEST_ROWS) {
                continue;
            }
            float top = 1f - (float) first / high, foot = 1f - (float) (last + 1) / high;
            if (top - foot > TALLEST && !even(dots, long_, first, last)) {
                return NONE;
            }
            top = top > 1f - SNAP ? 1f : Math.round(top * HEIGHTS) / HEIGHTS;
            foot = foot < SNAP ? 0f : Math.round(foot * HEIGHTS) / HEIGHTS;
            int column = 0;
            while (column < long_) {
                if (!columnInBand(dots, long_, column, first, last)) {
                    column++;
                    continue;
                }
                int start = column, end = column, gap = 0;
                while (column < long_ && gap <= GAP) {
                    if (columnInBand(dots, long_, column, first, last)) {
                        end = column;
                        gap = 0;
                    } else {
                        gap++;
                    }
                    column++;
                }
                float from = (float) start / long_, to = (float) (end + 1) / long_;
                from = from < SNAP ? 0f : from;
                to = to > 1f - SNAP ? 1f : to;
                float[] colour = colour(dots, long_, first, last, start, end);
                found.add(new Band(foot, top, from, to, Math.min(colour[0] * lighter, 1f), Math.min(colour[1] * lighter, 1f), Math.min(colour[2] * lighter, 1f)));
                column = end + 1;
            }
        }
        return found.toArray(NONE);
    }

    private static boolean rowInBand(byte[] dots, int long_, int row) {
        int painted = 0;
        for (int i = 0; i < long_; i++) {
            painted += dots[(row * long_ + i) * 4 + 3] != 0 ? 1 : 0;
        }
        return painted >= ROW_PAINTED * long_;
    }

    private static boolean columnInBand(byte[] dots, int long_, int column, int first, int last) {
        int painted = 0;
        for (int row = first; row <= last; row++) {
            painted += dots[(row * long_ + column) * 4 + 3] != 0 ? 1 : 0;
        }
        return painted >= COLUMN_PAINTED * (last - first + 1);
    }

    /** Whether the rows first to last are painted in one colour, as a panel of painted boards is, and not in courses or tiles. */
    private static boolean even(byte[] dots, int long_, int first, int last) {
        double sum = 0.0, squares = 0.0;
        int count = 0;
        for (int row = first; row <= last; row++) {
            for (int column = 0; column < long_; column++) {
                int at = (row * long_ + column) * 4;
                if (dots[at + 3] != 0) {
                    double light = 0.299 * (dots[at] & 0xff) + 0.587 * (dots[at + 1] & 0xff) + 0.114 * (dots[at + 2] & 0xff);
                    sum += light;
                    squares += light * light;
                    count++;
                }
            }
        }
        double mean = sum / count;
        return Math.sqrt(Math.max(squares / count - mean * mean, 0.0)) < EVEN * mean && !jointed(dots, long_, first + OUTLINE_ROWS, last - OUTLINE_ROWS);
    }

    private static boolean jointed(byte[] dots, int long_, int first, int last) {
        double[] light = new double[Math.max(last - first + 1, 0)];
        for (int row = first; row <= last; row++) {
            double sum = 0.0;
            int count = 0;
            for (int column = 0; column < long_; column++) {
                int at = (row * long_ + column) * 4;
                if (dots[at + 3] != 0) {
                    sum += 0.299 * (dots[at] & 0xff) + 0.587 * (dots[at + 1] & 0xff) + 0.114 * (dots[at + 2] & 0xff);
                    count++;
                }
            }
            light[row - first] = sum / count;
        }
        for (int i = 2; i < light.length - 2; i++) {
            double around = (light[i - 2] + light[i + 2]) / 2.0;
            if (Math.abs(light[i] - around) > JOINT * around) {
                return true;
            }
        }
        return false;
    }

    /**
     * The band's colour: the mean of its brighter dots. The sprite draws a band's lower edge
     * and its shadow in a dark line a dot or two thick, which is the moulding's shade and not
     * its paint.
     */
    private static float[] colour(byte[] dots, int long_, int first, int last, int start, int end) {
        float[] light = new float[(last - first + 1) * (end - start + 1)];
        int count = 0;
        for (int row = first; row <= last; row++) {
            for (int column = start; column <= end; column++) {
                int at = (row * long_ + column) * 4;
                if (dots[at + 3] != 0) {
                    light[count++] = 0.299f * (dots[at] & 0xff) + 0.587f * (dots[at + 1] & 0xff) + 0.114f * (dots[at + 2] & 0xff);
                }
            }
        }
        float[] sorted = Arrays.copyOf(light, count);
        Arrays.sort(sorted);
        float floor = sorted[count / 2] * 0.85f;
        float red = 0f, green = 0f, blue = 0f;
        int taken = 0;
        for (int row = first; row <= last; row++) {
            for (int column = start; column <= end; column++) {
                int at = (row * long_ + column) * 4;
                if (dots[at + 3] != 0 && 0.299f * (dots[at] & 0xff) + 0.587f * (dots[at + 1] & 0xff) + 0.114f * (dots[at + 2] & 0xff) >= floor) {
                    red += dots[at] & 0xff;
                    green += dots[at + 1] & 0xff;
                    blue += dots[at + 2] & 0xff;
                    taken++;
                }
            }
        }
        return new float[] {red / (255f * taken), green / (255f * taken), blue / (255f * taken)};
    }

    /** How much more an end of a moulding faces into the room than along its wall: see add. */
    static final float END_TOWARDS_ROOM = 1.01f;

    /**
     * The section of a band's moulding: points (out of the wall's skin, height over the
     * level's floor), in tiles, from one end of the section to the other, each segment facing
     * away from the wall on its left as it is walked. Every point is at a height at or below
     * the one before, so the section and the wall behind it close as a strip per segment.
     * A skirting or a panel is a board with a round top and no underside, a cornice a fillet
     * and a cove up into the ceiling, and a rail a board rounded above. The cove's faces are
     * steeper than half a right angle, so the cut of a wall in the overhead view takes them with
     * the wall they face; the fillet's level underside is cut as it lies within the wall's
     * thickness of the line.
     * A cornice falls CORNICE_FALL down the wall whatever its band.
     */
    static float[] section(Band band) {
        float level = ViewCamera.LEVEL_HEIGHT, foot = band.foot * level, top = band.top * level, tall = top - foot;
        if (cornice(band)) {
            tall = CORNICE_FALL;
            foot = top - tall;
            float fillet = foot + 0.25f * tall, middle = foot + 0.6f * tall;
            float steep = 0.35f * tall / (float) Math.tan(Math.toRadians(72)), shallow = 0.4f * tall / (float) Math.tan(Math.toRadians(52));
            return new float[] {FILLET + steep + shallow, top, FILLET + steep, middle, FILLET, fillet, FILLET, foot, 0f, foot};
        }
        float round = Math.min(ROUND, tall / 3f);
        float bend = round * (float) (1.0 - Math.sqrt(0.5));
        float[] board = {0f, top, BOARD - round, top, BOARD - bend, top - bend, BOARD, top - round, BOARD, foot, 0f, foot};
        // A skirting stands on the floor, and an underside there would lie in the floor's own plane.
        return band.foot > 0f ? board : Arrays.copyOf(board, board.length - 2);
    }

    /**
     * Adds a band's moulding along a wall from (x0, y0) by (da, db), a tile long, on the side
     * (inX, inY) of it, its skin WorldScene.WALL_HALF out from the line, at heights over base0
     * at its start and base1 at its end, into a mesh keyed by paint(band). Each vertex is the light
     * light(x, y) gives where it is. Closed at both ends, but for a start that is mitred: there
     * the same moulding on the other wall of the corner meets it on the corner's diagonal, each
     * point of its section starting as far along as it stands out of the other wall's skin, and
     * the two close each other.
     */
    static void add(SpriteGeometry.Mesh mesh, Band band, float x0, float y0, float da, float db, float inX, float inY, float base0, float base1,
            boolean mitred, Light light) {
        float[] section = section(band);
        float skinX = inX * WorldScene.WALL_HALF, skinY = inY * WorldScene.WALL_HALF;
        float to = band.to;
        float[] lit = new float[5];
        for (int i = 0; i + 3 < section.length; i += 2) {
            float out0 = section[i], up0 = section[i + 1], out1 = section[i + 2], up1 = section[i + 3];
            float dOut = out1 - out0, dUp = up1 - up0;
            if (dOut == 0f && dUp == 0f) {
                continue;
            }
            // The segment faces away from the wall on its left: (-dUp, dOut) in (out, up).
            mesh.face(inX * -dUp, inY * -dUp, dOut);
            float from0 = startOf(band, mitred, out0), from1 = startOf(band, mitred, out1);
            float bx = x0 + da * to + skinX, by = y0 + db * to + skinY, baseB = base0 + (base1 - base0) * to;
            float a0x = x0 + da * from0 + skinX, a0y = y0 + db * from0 + skinY, a1x = x0 + da * from1 + skinX, a1y = y0 + db * from1 + skinY;
            float baseA0 = base0 + (base1 - base0) * from0, baseA1 = base0 + (base1 - base0) * from1;
            corner(mesh, light, lit, a0x + inX * out0, a0y + inY * out0, baseA0 + up0);
            corner(mesh, light, lit, bx + inX * out0, by + inY * out0, baseB + up0);
            corner(mesh, light, lit, bx + inX * out1, by + inY * out1, baseB + up1);
            corner(mesh, light, lit, a0x + inX * out0, a0y + inY * out0, baseA0 + up0);
            corner(mesh, light, lit, bx + inX * out1, by + inY * out1, baseB + up1);
            corner(mesh, light, lit, a1x + inX * out1, a1y + inY * out1, baseA1 + up1);
        }
        float from = band.from;
        for (int end = mitred ? 1 : 0; end < 2; end++) {
            float along = end == 0 ? from : to, way = end == 0 ? -1f : 1f;
            float ex = x0 + da * along + skinX, ey = y0 + db * along + skinY, base = base0 + (base1 - base0) * along;
            // Turned a little more towards the room than along the wall: the overhead view's cut
            // takes an upright face with the wall it faces most, and an end faced only along its
            // wall would go with the wall it meets, or with none, and hang in the air.
            mesh.face(da * way + inX * END_TOWARDS_ROOM, db * way + inY * END_TOWARDS_ROOM, 0f);
            for (int i = 0; i + 3 < section.length; i += 2) {
                float out0 = section[i], up0 = section[i + 1], out1 = section[i + 2], up1 = section[i + 3];
                if (up0 == up1) {
                    continue;
                }
                corner(mesh, light, lit, ex, ey, base + up0);
                corner(mesh, light, lit, ex + inX * out0, ey + inY * out0, base + up0);
                corner(mesh, light, lit, ex + inX * out1, ey + inY * out1, base + up1);
                corner(mesh, light, lit, ex, ey, base + up0);
                corner(mesh, light, lit, ex + inX * out1, ey + inY * out1, base + up1);
                corner(mesh, light, lit, ex, ey, base + up1);
            }
        }
    }

    /** How far along its wall a point of a band's section standing out of the skin starts: at the band's start, or on the corner's diagonal. */
    private static float startOf(Band band, boolean mitred, float out) {
        return mitred ? WorldScene.WALL_HALF + out : band.from;
    }

    /** The mesh key of a band's paint. */
    static int paint(Band band) {
        return WallSides.laid(band.red, band.green, band.blue, 0f, 0f, 0f, WallSides.FINE);
    }

    private static void corner(SpriteGeometry.Mesh mesh, Light light, float[] lit, float x, float y, float up) {
        light.at(x, y, lit);
        mesh.unlit = lit[3];
        mesh.day = lit[4];
        mesh.add(x, y, up, 0f, 0f, lit[0], lit[1], lit[2]);
        mesh.litAsFace();
    }

    /** The light at a place on the square, as red, green and blue into, then what it has of a light the game has not given and of the day (SpriteGeometry.Mesh.unlit and day). */
    interface Light {
        void at(float x, float y, float[] into);
    }
}
