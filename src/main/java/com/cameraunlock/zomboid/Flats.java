package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import zombie.core.textures.Texture;

/**
 * A floor's sprite laid flat and a wall's stood square, each as a picture of its own.
 *
 * The game paints a square of floor as a diamond, and a floor of many squares is the same
 * diamond set down again and again, each meeting the next along its slanted edges. Those
 * edges are staircases of dots, half of them empty, so a face that took its texels from the
 * diamond itself had to keep clear of them, and showed the middle of the diamond drawn out
 * to the square's edges: the joints of a tiled floor stepped sideways at every square, and
 * the squares showed as patches. Here the diamond is turned into the square it is a picture
 * of, once, and a floor's face takes its texels by where on its square each corner lies, the
 * square's edge at the edge of the picture. The pattern then runs from square to square as
 * the game drew it, across a chunk's edge as across any other.
 *
 * A length of wall is painted on the slant, and stops a few dots short of its far end, where
 * the game's view has the end of the next wall. A face on it kept clear of that too, and of
 * the staircase along its top, so every length of a brick wall showed its bricks drawn out
 * sideways and a narrow brick at each join. Stood square, with the few dots its end and its
 * top lack filled in from the dots beside them, a length of wall is a picture a tile long
 * and a level high, and the courses and the bond run on from one length into the next.
 *
 * A roof tile is a square of floor tipped up: its sprite is laid flat the same way, on the
 * slope the tile reads as, and its courses meet the next tile's.
 *
 * A picture is read through the sampler every sprite is read through, which does not repeat
 * a picture. So it has a rim holding what lies past its edge, which is the far side of the
 * same picture since a floor or a wall is the one sprite set down again and again: a texel
 * blended across the edge is then blended with what is there on the map. Up a roof the next
 * tile is another sprite, and a roof's rim is its own last dots.
 *
 * It is the game's own sprite and nothing of the mod's, so it is drawn in every graphics
 * mode and has no row in [Content].
 */
final class Flats {
    /** Dots to a side of a floor's flat picture: the diamond is 128 dots across at the game's doubled scale. */
    static final int SIDE = 128;
    /** How much of the square the sprite has to paint to be a square of floor. */
    private static final float PAINTED = 0.97f;
    /** A dot is painted when the sprite's dots round it cover this much of it: what the picture takes for solid. A stain laid thinly over a whole square is no floor. */
    private static final float COVERED = 0.5f;
    /** Two heights of a roof's plane, in levels, are the same when this near: a tile's height is its own less its level's, which comes out a hair different on each level. */
    private static final float SAME_PLANE = 1e-3f;
    /** Dots of rim round the square, and dots to a side of the picture with its rim. */
    static final int RIM = 4, WHOLE = SIDE + 2 * RIM;
    /** Where in the picture the square's (0, 0) corner is, and how much of the picture the square takes, both ways alike. */
    static final float FROM = (float) RIM / WHOLE, SPAN = (float) SIDE / WHOLE;

    /** Dots along and up a wall's picture: a tile is 64 dots across the sprite and a level 192 up it, at the game's doubled scale. */
    static final int WALL_LONG = 64, WALL_HIGH = 192;
    /**
     * Dots of rim at each end of a wall's picture, and the picture's length with them. A skin
     * runs half a wall's thickness past its length round the outside of a corner, which is
     * under four dots, and a texel is blended with the dots a few either side of it.
     */
    static final int WALL_RIM = 8, WALL_WHOLE = WALL_LONG + 2 * WALL_RIM;
    /** Where along its picture a wall's length starts, and how much of the picture the length takes. */
    static final float WALL_FROM = (float) WALL_RIM / WALL_WHOLE, WALL_SPAN = (float) WALL_LONG / WALL_WHOLE;
    /** A run of empty dots no longer than this at an end of a row, or at the top of a column, is the sprite stopping short and is filled in. A longer one is an opening, or a wall that is low or short. */
    private static final int END_SHORT = 6, TOP_SHORT = 12;
    /** A dot of a wall is solid when the sprite's dots round it cover this much of it, and has a colour when they cover the second. Between the two it is as the sprite has it: the glass of a glazed wall. */
    private static final float WALL_COVERED = 0.5f, WALL_TINGED = 0.02f;

    /** What a sprite is read as: the floor of its square, the wall along its square's north edge or its west, or a tile of roof over it. */
    static final int FLOOR = 0, NORTH = 1, WEST = 2, ROOF = 3;

    private static final int GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE_BINDING_2D = 0x8069, GL_RGBA = 0x1908, GL_RGBA8 = 0x8058, GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_LINEAR = 0x2601, GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_UNPACK_ALIGNMENT = 0x0CF5, GL_UNPACK_ROW_LENGTH = 0x0CF2, GL_UNPACK_SKIP_ROWS = 0x0CF3, GL_UNPACK_SKIP_PIXELS = 0x0CF4;
    private static final int GL_PIXEL_UNPACK_BUFFER = 0x88EC, GL_PIXEL_UNPACK_BUFFER_BINDING = 0x88EF;

    /**
     * Where the sprite sits in its frame, as the build that first asked for it had it, and for
     * a roof the plane it lies on: its height in levels over the square's (0, 0) corner, and
     * how much it climbs along the square each way.
     */
    private record Asked(float originU, float originV, float scale, float left, float top, float width, float height, float high, float climbA, float climbB) {}

    /** A sprite read: its picture's OpenGL name, or 0 for a sprite that paints no whole square of floor or nothing along that edge. */
    private record Read(int name, Asked as) {}

    /** By what the sprite is read as. */
    private static final java.util.List<ConcurrentHashMap<Texture, Asked>> asked = java.util.List.of(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>());
    private static final java.util.List<ConcurrentHashMap<Texture, Read>> known = java.util.List.of(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(),
            new ConcurrentHashMap<>());

    /** Dev only, for the rig: no sprite has a picture while set, so every floor, wall and roof is drawn from its sprite as it was, for a comparison within one run. */
    static volatile boolean without;

    private Flats() {}

    /**
     * The OpenGL name of the sprite's picture as a floor or as a wall. 0 until a frame has
     * been drawn since it was first asked for, for a sprite that does not paint its whole
     * square or anything along that edge, and for one that sits elsewhere in its frame, or is
     * shown from another part of its sheet, than when it was read.
     */
    static int of(Texture texture, SpriteGeometry.Frame frame, int as) {
        return of(texture, frame, as, 0f, 0f, 0f);
    }

    /** As of, for a tile of roof on the plane given as Asked holds one: 0 too for a sprite read on another plane. */
    static int of(Texture texture, SpriteGeometry.Frame frame, int as, float high, float climbA, float climbB) {
        if (without) {
            return 0;
        }
        Read read = known.get(as).get(texture);
        if (read != null) {
            Asked was = read.as;
            return was.originU == frame.originU && was.originV == frame.originV && was.scale == frame.scale && was.left == frame.left && was.top == frame.top
                    && was.width == frame.width && was.height == frame.height && Math.abs(was.high - high) < SAME_PLANE && Math.abs(was.climbA - climbA) < SAME_PLANE
                    && Math.abs(was.climbB - climbB) < SAME_PLANE ? read.name : 0;
        }
        if (asked.get(as).putIfAbsent(texture, new Asked(frame.originU, frame.originV, frame.scale, frame.left, frame.top, frame.width, frame.height, high, climbA, climbB)) == null) {
            SpriteColours.readAgain(texture);
        }
        return 0;
    }

    /** Whether the sprite has still to be read as that. */
    static boolean unread(Texture texture, int as) {
        return !without && !known.get(as).containsKey(texture);
    }

    /** Reads the sprite as whatever it was asked for as, and makes its pictures: see Windows.read. On the thread that draws. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        for (int as = FLOOR; as <= ROOF; as++) {
            Asked wanted = asked.get(as).get(texture);
            if (wanted == null || known.get(as).containsKey(texture)) {
                continue;
            }
            boolean lies = as == FLOOR || as == ROOF;
            byte[] dots = lies ? laid(sheet, width, left, top, right, bottom, wanted.originU, wanted.originV, wanted.scale, wanted.left, wanted.top, wanted.width, wanted.height,
                    wanted.high, wanted.climbA, wanted.climbB)
                    : stood(sheet, width, left, top, right, bottom, as == NORTH, wanted.originU, wanted.originV, wanted.scale, wanted.left, wanted.top, wanted.width, wanted.height);
            if (FirstPerson.TRACE) {
                Log.info((as == FLOOR ? "floor " : as == ROOF ? "roof " : as == NORTH ? "north wall " : "west wall ") + texture.getName()
                        + (dots == null ? " paints too little to be one and is drawn from its sprite" : lies ? " is laid flat" : " is stood square"));
            }
            int name = dots == null ? 0 : lies ? picture(rimmed(dots, as == FLOOR), WHOLE, WHOLE) : picture(wallRimmed(dots), WALL_WHOLE, WALL_HIGH);
            known.get(as).put(texture, new Read(name, wanted));
        }
    }

    /**
     * The length of wall a sprite paints along one edge of its square, stood square: WALL_LONG
     * dots a row from the wall's start, the (0, 0) corner of the square, to its end, and
     * WALL_HIGH rows from a level above the floor down to it, each dot a red, green, blue and
     * its cover as the sprite has it. Where most rows are empty for the same few dots at an end, or
     * most columns at the top, those dots are filled with the first dot painted: the sprite
     * stops short there and the wall does not. Null for a sprite with nothing painted along
     * the edge.
     */
    static byte[] stood(ByteBuffer sheet, int width, int left, int top, int right, int bottom, boolean north, float originU, float originV, float scale,
            float frameLeft, float frameTop, float frameWidth, float frameHeight) {
        int wide = right - left, high = bottom - top;
        if (wide <= 0 || high <= 0) {
            return null;
        }
        byte[] dots = new byte[WALL_LONG * WALL_HIGH * 4];
        float perU = wide / frameWidth, perV = high / frameHeight;
        int count = 0;
        for (int j = 0; j < WALL_HIGH; j++) {
            for (int i = 0; i < WALL_LONG; i++) {
                float along = (i + 0.5f) / WALL_LONG, up = 1f - (j + 0.5f) / WALL_HIGH;
                float u = ((north ? along : -along) * SpriteGeometry.PIXELS_ACROSS_PER_TILE * scale + originU - frameLeft) * perU - 0.5f;
                float v = ((along * SpriteGeometry.PIXELS_DOWN_PER_TILE - up * SpriteGeometry.PIXELS_PER_LEVEL) * scale + originV - frameTop) * perV - 0.5f;
                int u0 = (int) Math.floor(u), v0 = (int) Math.floor(v);
                float partU = u - u0, partV = v - v0, red = 0f, green = 0f, blue = 0f, cover = 0f;
                for (int corner = 0; corner < 4; corner++) {
                    int x = u0 + (corner & 1), y = v0 + (corner >> 1);
                    if (x < 0 || y < 0 || x >= wide || y >= high) {
                        continue;
                    }
                    int place = ((top + y) * width + left + x) * 4;
                    float weight = ((corner & 1) == 0 ? 1f - partU : partU) * ((corner >> 1) == 0 ? 1f - partV : partV) * (sheet.get(place + 3) & 0xff) / 255f;
                    red += weight * (sheet.get(place) & 0xff);
                    green += weight * (sheet.get(place + 1) & 0xff);
                    blue += weight * (sheet.get(place + 2) & 0xff);
                    cover += weight;
                }
                if (cover < WALL_TINGED) {
                    continue;
                }
                int at = (j * WALL_LONG + i) * 4;
                dots[at] = (byte) Math.round(red / cover);
                dots[at + 1] = (byte) Math.round(green / cover);
                dots[at + 2] = (byte) Math.round(blue / cover);
                dots[at + 3] = (byte) Math.round(Math.min(cover, 1f) * 255f);
                count += cover >= WALL_COVERED ? 1 : 0;
            }
        }
        if (count == 0) {
            return null;
        }
        int[] runs = new int[Math.max(WALL_HIGH, WALL_LONG)];
        for (int way = 1; way >= -1; way -= 2) {
            int first = way == 1 ? 0 : WALL_LONG - 1;
            for (int j = 0; j < WALL_HIGH; j++) {
                runs[j] = empty(dots, j * WALL_LONG + first, way, WALL_LONG, END_SHORT);
            }
            int shorts = usual(runs, WALL_HIGH, END_SHORT);
            for (int j = 0; j < WALL_HIGH; j++) {
                int from = (j * WALL_LONG + first + way * runs[j]) * 4;
                for (int k = 1; k <= runs[j] && runs[j] <= Math.min(shorts + 1, END_SHORT); k++) {
                    System.arraycopy(dots, from, dots, from - way * k * 4, 4);
                }
            }
        }
        for (int i = 0; i < WALL_LONG; i++) {
            runs[i] = empty(dots, i, WALL_LONG, WALL_HIGH, TOP_SHORT);
        }
        int shorts = usual(runs, WALL_LONG, TOP_SHORT);
        for (int i = 0; i < WALL_LONG; i++) {
            for (int k = 0; k < runs[i] && runs[i] <= Math.min(shorts + 1, TOP_SHORT); k++) {
                System.arraycopy(dots, (runs[i] * WALL_LONG + i) * 4, dots, (k * WALL_LONG + i) * 4, 4);
            }
        }
        return dots;
    }

    /**
     * How many dots of a line are not solid before its first solid one, going from the dot at
     * from by step dots at a time: -1 for a line of count dots with none solid, and one more
     * than most for a line empty further than most.
     */
    private static int empty(byte[] dots, int from, int step, int count, int most) {
        int run = 0;
        while (run < count && (dots[(from + run * step) * 4 + 3] & 0xff) < WALL_COVERED * 255f) {
            run++;
        }
        return run == count ? -1 : Math.min(run, most + 1);
    }

    /**
     * How many dots short the sprite stops along one edge, from how many each of its lines is
     * short by there: the length, of 1 to most, that at least six in ten of the lines with
     * anything painted are short by, give or take a dot. -1 where there is none: a fence of
     * pickets or of wire is empty between them by all sorts of lengths, and is left as painted.
     */
    private static int usual(int[] runs, int lines, int most) {
        int painted = 0, best = -1, share = 0;
        for (int line = 0; line < lines; line++) {
            painted += runs[line] >= 0 ? 1 : 0;
        }
        for (int length = 1; length <= most; length++) {
            int near = 0;
            for (int line = 0; line < lines; line++) {
                near += runs[line] > 0 && Math.abs(runs[line] - length) <= 1 ? 1 : 0;
            }
            if (near > share) {
                share = near;
                best = length;
            }
        }
        return share * 10 >= painted * 6 && share > 0 ? best : -1;
    }

    /** A wall's dots with the rim at each end: each dot of the rim is the dot of the wall a whole length away from it. */
    static byte[] wallRimmed(byte[] dots) {
        byte[] whole = new byte[WALL_WHOLE * WALL_HIGH * 4];
        for (int y = 0; y < WALL_HIGH; y++) {
            for (int x = 0; x < WALL_WHOLE; x++) {
                System.arraycopy(dots, (y * WALL_LONG + Math.floorMod(x - WALL_RIM, WALL_LONG)) * 4, whole, (y * WALL_WHOLE + x) * 4, 4);
            }
        }
        return whole;
    }

    /**
     * The square a sprite paints, laid flat: SIDE dots a row, the row at y after the row
     * before it, each dot a red, green, blue and a cover of 255. A dot is the blend of the
     * four dots of the sprite round the place the game draws that point of the square at, each
     * counted by how solid it is. The few dots along the square's edges that the sprite's
     * staircase leaves empty take the colour of the dots beside them, the far edge counting as
     * beside the near one, since that is what lies there on the map. Null for a sprite that
     * leaves more than that empty: it is not a square of floor.
     *
     * @param sheet the sheet the sprite is on, four bytes a dot, width dots a row
     * @param left the sprite's trimmed rectangle on the sheet, with top, right and bottom
     * @param originU the frame dot of the square's (0, 0) corner, with originV
     * @param frameLeft the trimmed rectangle in frame dots, with frameTop, frameWidth and frameHeight
     * @param lifted how many levels over its floor the square lies at its (0, 0) corner, with how much it climbs along the square each way: 0 for a floor
     */
    static byte[] laid(ByteBuffer sheet, int width, int left, int top, int right, int bottom, float originU, float originV, float scale,
            float frameLeft, float frameTop, float frameWidth, float frameHeight, float lifted, float climbA, float climbB) {
        int wide = right - left, high = bottom - top;
        if (wide <= 0 || high <= 0) {
            return null;
        }
        byte[] dots = new byte[SIDE * SIDE * 4];
        boolean[] painted = new boolean[SIDE * SIDE];
        int count = 0;
        float perU = wide / frameWidth, perV = high / frameHeight;
        for (int j = 0; j < SIDE; j++) {
            for (int i = 0; i < SIDE; i++) {
                float a = (i + 0.5f) / SIDE, b = (j + 0.5f) / SIDE;
                float u = ((a - b) * SpriteGeometry.PIXELS_ACROSS_PER_TILE * scale + originU - frameLeft) * perU - 0.5f;
                float up = lifted + a * climbA + b * climbB;
                float v = (((a + b) * SpriteGeometry.PIXELS_DOWN_PER_TILE - up * SpriteGeometry.PIXELS_PER_LEVEL) * scale + originV - frameTop) * perV - 0.5f;
                int u0 = (int) Math.floor(u), v0 = (int) Math.floor(v);
                float partU = u - u0, partV = v - v0, red = 0f, green = 0f, blue = 0f, cover = 0f;
                for (int corner = 0; corner < 4; corner++) {
                    int x = u0 + (corner & 1), y = v0 + (corner >> 1);
                    if (x < 0 || y < 0 || x >= wide || y >= high) {
                        continue;
                    }
                    int place = ((top + y) * width + left + x) * 4;
                    float weight = ((corner & 1) == 0 ? 1f - partU : partU) * ((corner >> 1) == 0 ? 1f - partV : partV) * (sheet.get(place + 3) & 0xff) / 255f;
                    red += weight * (sheet.get(place) & 0xff);
                    green += weight * (sheet.get(place + 1) & 0xff);
                    blue += weight * (sheet.get(place + 2) & 0xff);
                    cover += weight;
                }
                if (cover < COVERED) {
                    continue;
                }
                int at = j * SIDE + i;
                dots[at * 4] = (byte) Math.round(red / cover);
                dots[at * 4 + 1] = (byte) Math.round(green / cover);
                dots[at * 4 + 2] = (byte) Math.round(blue / cover);
                dots[at * 4 + 3] = (byte) 255;
                painted[at] = true;
                count++;
            }
        }
        if (count < PAINTED * SIDE * SIDE) {
            return null;
        }
        boolean[] next = new boolean[SIDE * SIDE];
        while (count < SIDE * SIDE) {
            System.arraycopy(painted, 0, next, 0, painted.length);
            for (int at = 0; at < SIDE * SIDE; at++) {
                if (painted[at]) {
                    continue;
                }
                int i = at % SIDE, j = at / SIDE, red = 0, green = 0, blue = 0, beside = 0;
                for (int side = 0; side < 4; side++) {
                    int ni = Math.floorMod(i + (side == 0 ? -1 : side == 1 ? 1 : 0), SIDE), nj = Math.floorMod(j + (side == 2 ? -1 : side == 3 ? 1 : 0), SIDE);
                    int from = nj * SIDE + ni;
                    if (painted[from]) {
                        red += dots[from * 4] & 0xff;
                        green += dots[from * 4 + 1] & 0xff;
                        blue += dots[from * 4 + 2] & 0xff;
                        beside++;
                    }
                }
                if (beside == 0) {
                    continue;
                }
                dots[at * 4] = (byte) (red / beside);
                dots[at * 4 + 1] = (byte) (green / beside);
                dots[at * 4 + 2] = (byte) (blue / beside);
                dots[at * 4 + 3] = (byte) 255;
                next[at] = true;
                count++;
            }
            boolean[] swap = painted;
            painted = next;
            next = swap;
        }
        return dots;
    }

    /** A square's dots with the rim round them: each dot of the rim is the dot of the square a whole square away from it, or where the picture does not go round, the square's last dot that way. */
    static byte[] rimmed(byte[] dots, boolean goesRound) {
        byte[] whole = new byte[WHOLE * WHOLE * 4];
        for (int y = 0; y < WHOLE; y++) {
            for (int x = 0; x < WHOLE; x++) {
                int fromX = goesRound ? Math.floorMod(x - RIM, SIDE) : Math.clamp(x - RIM, 0, SIDE - 1), fromY = goesRound ? Math.floorMod(y - RIM, SIDE) : Math.clamp(y - RIM, 0, SIDE - 1);
                System.arraycopy(dots, (fromY * SIDE + fromX) * 4, whole, (y * WHOLE + x) * 4, 4);
            }
        }
        return whole;
    }

    /** Puts a picture with its rim on the graphics card, and gives its name. */
    static int picture(byte[] whole, int wide, int high) {
        ByteBuffer bytes = ByteBuffer.allocateDirect(whole.length).order(ByteOrder.nativeOrder());
        bytes.put(whole).flip();
        int was = GL11.glGetInteger(GL_TEXTURE_BINDING_2D);
        // With a buffer bound to unpack from, OpenGL would read the picture out of that buffer.
        int unpackBuffer = GL11.glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        GL11.glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        int name = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, name);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, wide, high, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL_TEXTURE_2D, was);
        GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        return name;
    }
}
