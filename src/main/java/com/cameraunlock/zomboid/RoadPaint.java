package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import zombie.core.textures.Texture;

/**
 * Lines painted on a road, drawn as paint, and the strip of pale concrete the game lays
 * between a road and its pavement, which is drawn as paint is.
 *
 * The game lays a road's lines as sprites over the road's floor: a stripe along one edge of a
 * square, a corner, a bar, a slanted bay line. A sprite's edge is a staircase of dots, and a
 * face that showed the sprite took its dots from a little inside itself, a different stretch
 * for each square, so a line down a road was saw-edged and stepped sideways at every square.
 *
 * Here the sprite says three things and is not drawn: what shape it paints, which PaintFit
 * reads off it laid flat, what colour the paint is, and how thick a coat. The shape is laid on
 * the road as triangles whose corners carry how far inside the paint's edge they are, and the
 * picture works out from that how much of each pixel the paint covers: an edge is as sharp as
 * the screen from a hand away, and a line too far off to be a pixel wide is as faint as it is
 * thin and does not crawl. The triangles lie on the road as its other overlays do, and the
 * picture lights, wets, shades and snows them as it does the road.
 *
 * It is the game's own marking and the game's colour. The row OwnTextures of [Content] says
 * whether the mod draws it: off, the sprite is drawn as the game has it.
 */
final class RoadPaint {
    /** Dots to a side of a sprite laid flat to be read: the game's tile is this many dots across at its doubled scale. And how many dots past the square each way are read with it: a sprite may paint a little past its square. */
    static final int SIDE = 128, MARGIN = 16;
    /**
     * What the picture is told a triangle of paint is made of, and a triangle of the strip of
     * concrete along a road's edge, which wears as concrete does and not as paint. No other
     * material has either number: 82 is a bulb that hangs (WorldScene.HUNG).
     */
    static final float MATERIAL = 83f, STRIP = 84f;
    /** A sprite's dots count as paint from this share of its most solid dot up, when its colour is taken. */
    private static final float PAINTED = 0.5f;
    /** The paint's colour is that of its lightest dots, this share of them: the darker ones are wear. And how much darker than those the paint is taken to be on the whole. */
    private static final float LIGHTEST = 0.25f, WORN = 0.93f;
    /**
     * How far left of the frame pixel the game draws at a square's far corner the middle of
     * the square's diamond of dots is, in frame pixels at tile scale 1. A whole floor's dots
     * are a diamond two short of the tile's width at its widest row, set against the frame's
     * left edge: measured on the 2x sheets, rows of 2 to 126 dots from x 0, their middle at
     * 63 where the corner is drawn at 64. Read from the corner's own pixel, every edge of a
     * line would lie half a dot of the sprite off where it is painted, and a stripe drawn to
     * the end of its square would stop that far short of it.
     */
    private static final float MIDDLE_LEFT = 0.5f;
    /** A reading is used when it and the sprite's own dots paint this share of the same dots: see PaintFit.agreement. A straight edge for a staircase costs four hundredths, a wrong shape far more. */
    private static final float AGREES = 0.7f;
    /** A sprite whose most solid dot covers less than this is no paint. */
    private static final int FAINTEST = 24;

    /** Dev only, for the rig: no sprite is read as paint while set, so every marking is drawn from its sprite as it was, for a comparison within one run. */
    static volatile boolean without;

    /** One sprite's paint. */
    static final class Shape {
        /** The OpenGL name of a picture of one dot: the paint's colour, and in its cover how thick the coat is. */
        final int picture;
        /** Its triangles, PaintFit.CORNER floats a corner. */
        final float[] corners;
        /** MATERIAL or STRIP. */
        final float material;
        private final Asked as;

        private Shape(int picture, float[] corners, float material, Asked as) {
            this.picture = picture;
            this.corners = corners;
            this.material = material;
            this.as = as;
        }
    }

    /** Where the sprite sits in its frame, as the build that first asked for it had it. */
    private record Asked(float originU, float originV, float scale, float left, float top, float width, float height) {}

    private static final Shape NONE = new Shape(0, null, 0f, null);
    private static final ConcurrentHashMap<Texture, Asked> asked = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Texture, Shape> known = new ConcurrentHashMap<>();
    /** The pictures of one dot made so far, by colour and coat. Known to the thread that draws alone. */
    private static final HashMap<Integer, Integer> pictures = new HashMap<>();

    private RoadPaint() {}

    /** The sheet a court's lines are on, with its hoops, its nets and its floors, and the first and last of the sprites on it that are lines: the arcs of a circle. */
    private static final String COURT = "recreational_sports_01_";
    private static final int COURT_FIRST = 108, COURT_LAST = 119;

    /** The sheets of the strip of concrete between a road and its pavement, straight and slanted. Its other sheets have the verge's grass or gravel blended into the strip and are no one colour. */
    private static final String KERB_STRIP = "street_curbs_01_", KERB_STRIP_BLENDED = "street_curbs_01_blend";

    /**
     * Whether a sprite of that name is a marking on the ground: a line of a road or of a
     * parking bay, an arc of a court, or the strip of concrete along a road's edge.
     */
    static boolean marks(String name) {
        if (name == null) {
            return false;
        }
        if (name.startsWith("street_trafficlines_") || name.startsWith(KERB_STRIP) && !name.startsWith(KERB_STRIP_BLENDED)) {
            return true;
        }
        if (!name.startsWith(COURT) || name.length() != COURT.length() + 3) {
            return false;
        }
        int number = 0;
        for (int at = COURT.length(); at < name.length(); at++) {
            char digit = name.charAt(at);
            if (digit < '0' || digit > '9') {
                return false;
            }
            number = number * 10 + digit - '0';
        }
        return number >= COURT_FIRST && number <= COURT_LAST;
    }

    /**
     * The paint the sprite is. Null until a frame has been drawn since it was first asked for,
     * for a sprite whose shape cannot be read, and for one that sits elsewhere in its frame
     * than when it was read: the sprite is then drawn as it is.
     */
    static Shape of(Texture texture, SpriteGeometry.Frame frame) {
        if (without) {
            return null;
        }
        Shape shape = known.get(texture);
        if (shape != null) {
            Asked was = shape.as;
            return shape != NONE && was.originU == frame.originU && was.originV == frame.originV && was.scale == frame.scale && was.left == frame.left && was.top == frame.top
                    && was.width == frame.width && was.height == frame.height ? shape : null;
        }
        if (asked.putIfAbsent(texture, new Asked(frame.originU, frame.originV, frame.scale, frame.left, frame.top, frame.width, frame.height)) == null) {
            SpriteColours.readAgain(texture);
        }
        return null;
    }

    /** Whether the sprite has still to be read. */
    static boolean unread(Texture texture) {
        return !without && !known.containsKey(texture);
    }

    /** Reads the sprite if it was asked for, and makes the picture of its colour: see Windows.read. On the thread that draws. */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        Asked wanted = asked.get(texture);
        if (wanted == null || known.containsKey(texture)) {
            return;
        }
        float[] paint = new float[4];
        float[] cover = laidFlat(sheet, width, left, top, right, bottom, wanted.originU, wanted.originV, wanted.scale, wanted.left, wanted.top, wanted.width, wanted.height, paint);
        float[] corners = cover == null ? null : PaintFit.triangles(cover, SIDE, MARGIN);
        float agrees = corners == null ? 0f : PaintFit.agreement(cover, SIDE, MARGIN, corners);
        if (FirstPerson.TRACE) {
            Log.info("marking " + texture.getName() + (corners == null ? " has no shape that can be read and is drawn from its sprite"
                    : " is read as " + corners.length / (3 * PaintFit.CORNER) + " triangles that agree with it by " + agrees + (agrees < AGREES ? ", too little: it is drawn from its sprite" : "")));
        }
        if (agrees < AGREES) {
            corners = null;
        }
        if (corners == null) {
            known.put(texture, NONE);
            return;
        }
        int key = Math.round(paint[0] * 255f) << 24 | Math.round(paint[1] * 255f) << 16 | Math.round(paint[2] * 255f) << 8 | Math.round(paint[3] * 255f);
        Integer picture = pictures.get(key);
        if (picture == null) {
            picture = Flats.picture(new byte[] {(byte) (key >>> 24), (byte) (key >>> 16), (byte) (key >>> 8), (byte) key}, 1, 1);
            pictures.put(key, picture);
        }
        known.put(texture, new Shape(picture, corners, texture.getName().startsWith(KERB_STRIP) ? STRIP : MATERIAL, wanted));
    }

    /**
     * A sprite laid flat, with MARGIN dots of what lies past its square each way: SIDE + 2 *
     * MARGIN dots a row, the dot at x and y at x * that + y, the square's own corner at
     * (MARGIN, MARGIN), each how solid the sprite is there as a share of its most solid dot.
     * Into paint go the colour of the paint, red, green and blue, and how thick the coat is,
     * each from 0 to 1.
     * The sprite is the rectangle from (left, top) to before (right, bottom) of a picture width
     * pixels wide, held as bytes of red, green, blue and cover. Null for a sprite that paints
     * nothing.
     */
    static float[] laidFlat(ByteBuffer sheet, int width, int left, int top, int right, int bottom, float originU, float originV, float scale, float frameLeft, float frameTop,
            float frameWidth, float frameHeight, float[] paint) {
        int wide = right - left, high = bottom - top;
        if (wide <= 0 || high <= 0) {
            return null;
        }
        int most = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                most = Math.max(most, sheet.get((y * width + x) * 4 + 3) & 0xff);
            }
        }
        if (most < FAINTEST) {
            return null;
        }
        // The paint's colour: the mean of the lightest of its dots.
        int[] byLight = new int[256];
        int painted = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int place = (y * width + x) * 4;
                if ((sheet.get(place + 3) & 0xff) >= PAINTED * most) {
                    byLight[light(sheet, place)]++;
                    painted++;
                }
            }
        }
        int from = 255;
        for (int counted = byLight[255]; counted < LIGHTEST * painted && from > 0;) {
            counted += byLight[--from];
        }
        double red = 0.0, green = 0.0, blue = 0.0;
        int lightest = 0;
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int place = (y * width + x) * 4;
                if ((sheet.get(place + 3) & 0xff) >= PAINTED * most && light(sheet, place) >= from) {
                    red += sheet.get(place) & 0xff;
                    green += sheet.get(place + 1) & 0xff;
                    blue += sheet.get(place + 2) & 0xff;
                    lightest++;
                }
            }
        }
        paint[0] = (float) (red / lightest / 255.0) * WORN;
        paint[1] = (float) (green / lightest / 255.0) * WORN;
        paint[2] = (float) (blue / lightest / 255.0) * WORN;
        paint[3] = most / 255f;
        int dots = SIDE + 2 * MARGIN;
        float[] cover = new float[dots * dots];
        float perU = wide / frameWidth, perV = high / frameHeight;
        for (int i = 0; i < dots; i++) {
            for (int j = 0; j < dots; j++) {
                float a = (i - MARGIN + 0.5f) / SIDE, b = (j - MARGIN + 0.5f) / SIDE;
                float u = ((a - b) * SpriteGeometry.PIXELS_ACROSS_PER_TILE * scale + originU - MIDDLE_LEFT * scale - frameLeft) * perU - 0.5f;
                float v = ((a + b) * SpriteGeometry.PIXELS_DOWN_PER_TILE * scale + originV - frameTop) * perV - 0.5f;
                int u0 = (int) Math.floor(u), v0 = (int) Math.floor(v);
                float partU = u - u0, partV = v - v0, solid = 0f;
                for (int corner = 0; corner < 4; corner++) {
                    int x = u0 + (corner & 1), y = v0 + (corner >> 1);
                    if (x < 0 || y < 0 || x >= wide || y >= high) {
                        continue;
                    }
                    solid += ((corner & 1) == 0 ? 1f - partU : partU) * ((corner >> 1) == 0 ? 1f - partV : partV) * (sheet.get(((top + y) * width + left + x) * 4 + 3) & 0xff);
                }
                cover[i * dots + j] = Math.min(solid / most, 1f);
            }
        }
        return cover;
    }

    private static int light(ByteBuffer sheet, int place) {
        return Math.round(0.299f * (sheet.get(place) & 0xff) + 0.587f * (sheet.get(place + 1) & 0xff) + 0.114f * (sheet.get(place + 2) & 0xff));
    }

    /**
     * Lays a sprite's paint on its square: every triangle as a face lying on the ground, its
     * corners carrying what PaintFit gave them where a face of a sprite carries a texel's
     * place. The frame is the square's, lit and placed, and is given the paint's material for
     * the while.
     */
    static void lay(SpriteGeometry geometry, SpriteGeometry.Mesh mesh, SpriteGeometry.Frame frame, Shape shape, int x, int y, int z) {
        float made = frame.material;
        frame.material = shape.material;
        float[] corners = shape.corners;
        for (int at = 0; at < corners.length; at += 3 * PaintFit.CORNER) {
            geometry.addGiven(mesh, frame, x, y, z, corners, at);
        }
        frame.material = made;
    }

    /**
     * For the world's fragment shader. A triangle of paint carries, where a texel's place
     * would be, how far inside the paint's edge the pixel is and how thick the paint is there,
     * both in tiles. A pixel is a little square of road: so much of it lies inside the paint's
     * near edge, so much inside the far one, and the paint covers what lies inside both.
     */
    static final String GLSL = """
            float paintCover(vec2 carried) {
                float pixel = max(length(vec2(dFdx(carried.x), dFdy(carried.x))), 1e-6);
                return clamp(clamp(carried.x / pixel + 0.5, 0.0, 1.0) + clamp((carried.y - carried.x) / pixel + 0.5, 0.0, 1.0) - 1.0, 0.0, 1.0);
            }

            // Paint on a road wears where tyres run and where the stone under it stands
            // proud: thin in patches a pace or two across, and pitted finely. Finer than a
            // pixel the pits are left out, where they would only crawl.
            float paintWorn(vec2 place) {
                float wide = patches(place * 0.7 + 23.0) * 0.6 + patches(place * 2.9 + 5.0) * 0.4;
                float foot = max(length(dFdx(place)), length(dFdy(place)));
                float pits = smoothstep(0.52, 0.8, patches(place * 37.0 + 11.0)) * (1.0 - smoothstep(0.004, 0.012, foot));
                return 1.0 - 0.32 * smoothstep(0.45, 0.8, wide) - 0.3 * pits;
            }
            """;

    /** For the world's fragment main, after the texel is read: a pixel of paint is the paint's colour, as much of it as the paint covers. Concrete is not worn through to the road under it. */
    static final String GLSL_MAIN = """
                if (vMaterial > 82.5 && vMaterial < 84.5) {
                    texel.a *= paintCover(vUv) * (uOwnTextures > 0.5 && vMaterial < 83.5 ? paintWorn(vWorld.xy) : 1.0);
                }
            """;
}
