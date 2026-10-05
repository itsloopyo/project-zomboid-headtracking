package com.cameraunlock.zomboid;

import com.cameraunlock.zomboid.SpriteGeometry.Mesh;
import java.util.HashMap;

/**
 * What grows on open ground in the first-person picture and what lies on it, where the game
 * has only a flat painted square or a flat painted card: the round bole of a tree and its
 * roots, ivy up it, boulders, ferns, weeds and flowers, fallen leaves and twigs.
 *
 * Everything here is worked out from where a square is on the map, so the same plants stand in
 * the same places every time the square is built, and nothing is kept between builds.
 */
final class Flora {
    /**
     * What a vertex is made of, as SpriteGeometry.Frame.material carries it: numbers above
     * those of the mod's furniture, which end at 47. The world shader has the same numbers.
     */
    static final float LITTER = 56f, TRUNK = 57f, IVY = 58f, ROCK = 59f, FROND = 60f, LEAF = 61f, FLOWER = 62f, BUSH = 63f;

    /** Which leaf a LEAF card is. The card's texture coordinate across carries it in the twos above the 0 to 1 it runs over. */
    static final int WEED = 0, FALLEN_ORANGE = 1, FALLEN_BROWN = 2, FALLEN_YELLOW = 3, TWIG = 4;
    /** Which flower a FLOWER card is, carried the same way. */
    static final int WHITE = 0, YELLOW = 1, PURPLE = 2;
    private static final float PICK_STEP = 2f;

    /** How far from a tree its fallen leaves reach, in tiles. */
    static final float WOOD_REACH = 3.2f;
    /** How many squares past a chunk's edge trees are looked for: all that are within WOOD_REACH of a corner on the edge. */
    static final int WOOD_RING = 3;
    private static final float WOOD_GAIN = 0.9f;

    static final int FLOWERS_MOST = 4, FERNS_MOST = 2, FALLEN_MOST = 3, WEED_LEAVES = 5;
    /** Flowers come in drifts this many squares across. */
    private static final int DRIFT = 4;

    static final int TRUNK_SIDES = 12, TRUNK_SIDES_FAR = 6;
    /** How far below the ground a trunk and a boulder start, in tiles: the ground under them slopes. */
    private static final float SUNK = 0.12f;
    /** How much thinner a bole is at its top than at its foot, and how far past its top it runs on to a point, as a share of its height. */
    private static final float TAPER = 0.78f;
    static final float SPIRE = 0.45f;
    /** How much of a tile one repeat of the bark's or the ivy's picture covers. */
    private static final float BARK_SPAN = 0.7f, IVY_SPAN = 0.55f;
    private static final float IVY_STANDS_OFF = 0.02f;
    /** Where across the frond's picture its stem meets the bottom edge. The picture is packed so. */
    static final float FROND_ROOT = 0.75f;
    /** A frond's width over its length, which is the shape of its picture. */
    private static final float FROND_WIDE = 0.83f;
    private static final float FROND_SWAY = 0.25f;

    private static final int RINGS = 4;
    private final float[] ringX = new float[RINGS * (TRUNK_SIDES + 1)], ringY = new float[RINGS * (TRUNK_SIDES + 1)];
    private final float[] ringSlope = new float[RINGS * (TRUNK_SIDES + 1)];
    private final float[] ringUp = new float[RINGS];
    private final float[] rock = new float[BALL.length];

    /** A number that is the same every time for one place and one purpose, and unlike its neighbours'. */
    static int mix(int x, int y, int salt) {
        int hash = x * 73856093 ^ y * 19349663 ^ salt * 83492791;
        hash = (hash ^ hash >>> 13) * 0x5bd1e995;
        return hash ^ hash >>> 15;
    }

    /** Eight bits of a mixed number as 0 to 1. */
    static float unit(int hash, int shift) {
        return (hash >>> shift & 0xff) / 255f;
    }

    /**
     * How deep in fallen leaves each corner of a chunk is, 0 on open ground and 1 in a wood.
     *
     * trees says which squares hold a tree: the chunk's and WOOD_RING more on every side, x
     * first. A corner takes something from every tree within WOOD_REACH of it, the more the
     * nearer, so one tree on a lawn has a thin scatter round its foot and a wood is leaves
     * from edge to edge. Two chunks that share a corner see the same trees round it and give
     * it the same depth.
     */
    static void wooded(boolean[] trees, int tiles, float[] corners) {
        int span = tiles + 2 * WOOD_RING, across = tiles + 1;
        for (int i = 0; i < across; i++) {
            for (int j = 0; j < across; j++) {
                float sum = 0f;
                for (int di = -WOOD_RING; di < WOOD_RING; di++) {
                    for (int dj = -WOOD_RING; dj < WOOD_RING; dj++) {
                        if (!trees[(i + WOOD_RING + di) * span + j + WOOD_RING + dj]) {
                            continue;
                        }
                        float awayX = di + 0.5f, awayY = dj + 0.5f;
                        float near = 1f - (float) Math.sqrt(awayX * awayX + awayY * awayY) / WOOD_REACH;
                        if (near > 0f) {
                            sum += near * near;
                        }
                    }
                }
                corners[i * across + j] = Math.min(sum * WOOD_GAIN, 1f);
            }
        }
    }

    /** How many flowers stand on a square of grass: none on most, a few where a drift of them lies. */
    static int flowers(int x, int y) {
        int drift = mix(Math.floorDiv(x, DRIFT), Math.floorDiv(y, DRIFT), 11);
        int own = mix(x, y, 12);
        if (unit(drift, 0) < 0.3f) {
            return 1 + (own >>> 8 & 3);
        }
        return unit(own, 0) < 0.15f ? 1 : 0;
    }

    /** Which flower the square's flower number index is: mostly the one its drift is made of. */
    static int flowerKind(int x, int y, int index) {
        int drift = mix(Math.floorDiv(x, DRIFT), Math.floorDiv(y, DRIFT), 11);
        int own = mix(x, y, 20 + index);
        return Math.floorMod(unit(own, 16) < 0.75f ? drift >>> 8 : own >>> 8, 3);
    }

    /** Whether a broad-leaved weed grows on a square of grass. */
    static boolean weed(int x, int y) {
        return unit(mix(x, y, 13), 0) < 0.12f;
    }

    /** How many ferns stand on a square as deep in a wood as wooded says. */
    static int ferns(int x, int y, float wooded) {
        float chance = unit(mix(x, y, 14), 0);
        return chance < 0.22f * wooded ? 2 : chance < 0.6f * wooded ? 1 : 0;
    }

    /** How many fallen leaves and twigs stand proud of the litter on such a square. */
    static int fallen(int x, int y, float wooded) {
        return Math.min((int) (wooded * (1f + FALLEN_MOST) * unit(mix(x, y, 15), 0) + wooded), FALLEN_MOST);
    }

    /** Whether ivy grows up the tree on a square. */
    static boolean ivied(int x, int y) {
        return unit(mix(x, y, 16), 0) < 0.4f;
    }

    private static void mark(Mesh mesh, float open, float kind, float material) {
        mesh.open = open + kind + 32f * material;
    }

    /**
     * Fallen leaves over the whole of a square, as deep at each corner as cover says, in the
     * corner order of SpriteGeometry.lift: laid over the ground and mixed into it.
     */
    static void addLitter(Mesh mesh, int x, int y, float[] lift, float raised, float[] cover, SpriteGeometry.Frame frame) {
        mark(mesh, frame.open, 0f, LITTER);
        mesh.face(-(lift[1] - lift[0] + lift[2] - lift[3]) / 2f, -(lift[3] - lift[0] + lift[2] - lift[1]) / 2f, 1f);
        for (int corner : QUAD) {
            mesh.sway = 1f - cover[corner];
            mesh.add(x + (corner == 1 || corner == 2 ? 1f : 0f), y + (corner >= 2 ? 1f : 0f), lift[corner] + raised, 0f, 0f,
                    frame.cornerRed[corner], frame.cornerGreen[corner], frame.cornerBlue[corner]);
        }
        mesh.sway = 0f;
    }

    private static final int[] QUAD = {0, 1, 2, 0, 2, 3};

    /**
     * A flower's head over (x, y) at height up: a square card tipped to face half upward and
     * half the way turn points, since it is looked down on from one side or another. It has no
     * stem: that is in among the grass. Two triangles.
     */
    static void addFlower(Mesh mesh, float x, float y, float up, float half, double turn, int kind, SpriteGeometry.Frame frame) {
        float wayX = (float) Math.cos(turn), wayY = (float) Math.sin(turn);
        float acrossX = -wayY * half, acrossY = wayX * half;
        float tipX = wayX * half * TIPPED_ALONG, tipY = wayY * half * TIPPED_ALONG, tipUp = half * TIPPED_UP;
        mark(mesh, frame.open, SpriteGeometry.CARD, FLOWER);
        mesh.face(-wayX * TIPPED_UP, -wayY * TIPPED_UP, TIPPED_ALONG);
        mesh.sway = 1f;
        float pick = PICK_STEP * kind;
        for (int corner : QUAD) {
            float a = corner == 1 || corner == 2 ? 1f : -1f, b = corner >= 2 ? 1f : -1f;
            mesh.add(x + acrossX * a + tipX * b, y + acrossY * a + tipY * b, up + tipUp * b, pick + (a + 1f) / 2f, (b + 1f) / 2f,
                    frame.red, frame.green, frame.blue);
        }
        mesh.sway = 0f;
    }

    private static final float TIPPED_ALONG = 0.766f, TIPPED_UP = 0.643f;

    /**
     * One leaf growing out from (x, y) at height up the way turn points, rising at the angle
     * rise: a weed's leaf, or a fallen one lying nearly flat, or a twig. Two triangles.
     */
    static void addLeaf(Mesh mesh, float x, float y, float up, float length, float width, double turn, double rise, int kind,
            float sway, SpriteGeometry.Frame frame) {
        float wayX = (float) Math.cos(turn), wayY = (float) Math.sin(turn);
        float along = length * (float) Math.cos(rise), climb = length * (float) Math.sin(rise);
        float acrossX = -wayY * width / 2f, acrossY = wayX * width / 2f;
        mark(mesh, frame.open, SpriteGeometry.CARD, LEAF);
        mesh.face(-wayX * climb, -wayY * climb, along);
        float pick = PICK_STEP * kind;
        for (int corner : QUAD) {
            float a = corner == 1 || corner == 2 ? 1f : -1f, b = corner >= 2 ? 1f : 0f;
            mesh.sway = sway * b;
            mesh.add(x + acrossX * a + wayX * along * b, y + acrossY * a + wayY * along * b, up + climb * b, pick + (a + 1f) / 2f, b,
                    frame.red, frame.green, frame.blue);
        }
        mesh.sway = 0f;
    }

    /**
     * A fern at (x, y) on ground at height up: fronds growing out from one root all round,
     * each climbing steeply and then arching over level, so that looked down on it is a crown
     * of fronds seen flat and from the side a low mound. Four triangles a frond.
     */
    static int addFern(Mesh mesh, float x, float y, float up, float size, int fronds, int seed, SpriteGeometry.Frame frame) {
        mark(mesh, frame.open, SpriteGeometry.CARD, FROND);
        for (int frond = 0; frond < fronds; frond++) {
            int own = mix(seed, frond, 17);
            double turn = (frond + unit(own, 0) * 0.7) / fronds * 2.0 * Math.PI + unit(seed, 8) * Math.PI;
            float length = size * (0.75f + 0.35f * unit(own, 8));
            float wayX = (float) Math.cos(turn), wayY = (float) Math.sin(turn);
            boolean mirrored = (own >>> 16 & 1) == 1;
            float side = mirrored ? -1f : 1f;
            float acrossX = -wayY * side * length * FROND_WIDE, acrossY = wayX * side * length * FROND_WIDE;
            double steep = Math.toRadians(50.0 + 20.0 * unit(own, 17)), level = Math.toRadians(-8.0 + 20.0 * unit(own, 24));
            float midOut = length / 2f * (float) Math.cos(steep), midUp = length / 2f * (float) Math.sin(steep);
            float tipOut = midOut + length / 2f * (float) Math.cos(level), tipUp = midUp + length / 2f * (float) Math.sin(level);
            mesh.face(-wayX * 0.5f, -wayY * 0.5f, 1f);
            frondBand(mesh, x, y, up, wayX, wayY, acrossX, acrossY, 0f, 0f, midOut, midUp, 0f, 0.5f, frame);
            mesh.face(0f, 0f, 1f);
            frondBand(mesh, x, y, up, wayX, wayY, acrossX, acrossY, midOut, midUp, tipOut, tipUp, 0.5f, 1f, frame);
        }
        mesh.sway = 0f;
        return fronds * 4;
    }

    private static void frondBand(Mesh mesh, float x, float y, float up, float wayX, float wayY, float acrossX, float acrossY,
            float out0, float up0, float out1, float up1, float v0, float v1, SpriteGeometry.Frame frame) {
        for (int corner : QUAD) {
            boolean far = corner >= 2, right = corner == 1 || corner == 2;
            float out = far ? out1 : out0, climb = far ? up1 : up0, v = far ? v1 : v0;
            float across = right ? 1f - FROND_ROOT : -FROND_ROOT;
            mesh.sway = FROND_SWAY * v;
            mesh.add(x + wayX * out + acrossX * across, y + wayY * out + acrossY * across, up + climb, right ? 1f : 0f, v,
                    frame.red, frame.green, frame.blue);
        }
    }

    /**
     * The bole of a tree standing on (x, y), its foot on ground at height base: round, radius
     * thick a little way up and thinner at its top, height tall. Near the eye it spreads into
     * roots at the ground and runs on above its height to a point; further off it is a plain
     * six-sided post. The bark's
     * picture is wrapped round it a whole number of times. Returns the triangles added.
     */
    int addTrunk(Mesh mesh, float x, float y, float base, float radius, float height, boolean near, int seed, SpriteGeometry.Frame frame) {
        int sides = near ? TRUNK_SIDES : TRUNK_SIDES_FAR;
        int rings = rings(x, y, radius, height, near, seed, 0f);
        int repeats = Math.max(1, Math.round(2f * (float) Math.PI * radius / BARK_SPAN));
        mark(mesh, frame.open, 0f, TRUNK);
        mesh.sway = 0f;
        for (int ring = 0; ring + 1 < rings; ring++) {
            for (int side = 0; side < sides; side++) {
                for (int corner : QUAD) {
                    int at = (ring + (corner >= 2 ? 1 : 0)) * (TRUNK_SIDES + 1) + side + (corner == 1 || corner == 2 ? 1 : 0);
                    int around = side + (corner == 1 || corner == 2 ? 1 : 0);
                    float climb = ringUp[ring + (corner >= 2 ? 1 : 0)];
                    double angle = around * 2.0 * Math.PI / sides;
                    mesh.face((float) Math.cos(angle), (float) Math.sin(angle), ringSlope[at]);
                    mesh.add(ringX[at], ringY[at], base + climb, (float) around * repeats / sides, climb / BARK_SPAN, frame.red, frame.green, frame.blue);
                }
            }
        }
        if (!near) {
            return (rings - 1) * sides * 2;
        }
        // A bole does not end in a flat top like a stump. It runs on up into the crown, thinning
        // to nothing, and the lowest boughs hide where: many trees stand lower than the eye, and
        // their tops are looked down on.
        int top = (rings - 1) * (TRUNK_SIDES + 1);
        float topUp = ringUp[rings - 1], tip = topUp + height * SPIRE;
        float lean = radius * TAPER / (height * SPIRE);
        for (int side = 0; side < sides; side++) {
            mesh.face((float) Math.cos((side + 0.5) * 2.0 * Math.PI / sides), (float) Math.sin((side + 0.5) * 2.0 * Math.PI / sides), lean);
            mesh.add(x, y, base + tip, (side + 0.5f) * repeats / sides, tip / BARK_SPAN, frame.red, frame.green, frame.blue);
            for (int next = 0; next <= 1; next++) {
                double angle = (side + next) * 2.0 * Math.PI / sides;
                mesh.face((float) Math.cos(angle), (float) Math.sin(angle), lean);
                mesh.add(ringX[top + side + next], ringY[top + side + next], base + topUp, (float) (side + next) * repeats / sides, topUp / BARK_SPAN,
                        frame.red, frame.green, frame.blue);
            }
        }
        return (rings - 1) * sides * 2 + sides;
    }

    /**
     * Ivy up the bole addTrunk makes from the same numbers: a sleeve just clear of the bark
     * from above the roots to tall, cut into leaves by the picture, which also frays its ends.
     * Each vertex says how far up the sleeve it is where a plant says how far it sways.
     */
    int addIvy(Mesh mesh, float x, float y, float base, float radius, float height, float tall, int seed, SpriteGeometry.Frame frame) {
        int rings = rings(x, y, radius, height, true, seed, IVY_STANDS_OFF);
        int repeats = Math.max(1, Math.round(2f * (float) Math.PI * radius / IVY_SPAN));
        mark(mesh, frame.open, 0f, IVY);
        float foot = ringUp[1];
        for (int ring = 1; ring + 1 < rings; ring++) {
            for (int side = 0; side < TRUNK_SIDES; side++) {
                for (int corner : QUAD) {
                    int upper = corner >= 2 ? 1 : 0, around = side + (corner == 1 || corner == 2 ? 1 : 0);
                    int at = (ring + upper) * (TRUNK_SIDES + 1) + around;
                    // The top ring is brought down to where the ivy ends, along the bole's own line.
                    float climb = Math.min(ringUp[ring + upper], tall);
                    float share = ring + upper == rings - 1 ? (climb - ringUp[ring]) / (ringUp[ring + 1] - ringUp[ring]) : 1f;
                    int below = ring * (TRUNK_SIDES + 1) + around;
                    float px = upper == 1 ? ringX[below] + (ringX[at] - ringX[below]) * share : ringX[at];
                    float py = upper == 1 ? ringY[below] + (ringY[at] - ringY[below]) * share : ringY[at];
                    double angle = around * 2.0 * Math.PI / TRUNK_SIDES;
                    mesh.face((float) Math.cos(angle), (float) Math.sin(angle), ringSlope[at]);
                    mesh.sway = Math.clamp((climb - foot) / (tall - foot), 0f, 1f);
                    mesh.add(px, py, base + climb, (float) around * repeats / TRUNK_SIDES, climb / IVY_SPAN, frame.red, frame.green, frame.blue);
                }
            }
        }
        mesh.sway = 0f;
        return (rings - 2) * TRUNK_SIDES * 2;
    }

    /**
     * Works out the rings of a bole, each clear further out than the bark, into ringX, ringY,
     * ringUp and ringSlope, the last being how far each ring's surface leans back from upright,
     * as the up part of its normal. A ring has a vertex more than it has sides: the first
     * again, to close it. Returns how many rings.
     */
    private int rings(float x, float y, float radius, float height, boolean near, int seed, float clear) {
        int sides = near ? TRUNK_SIDES : TRUNK_SIDES_FAR;
        int rings = near ? RINGS : 2;
        int roots = 3 + (seed >>> 4 & 1);
        // The first root runs out along one of the bole's corners, and so then do the rest: between corners a root has no ridge to it.
        double first = Math.floor(unit(seed, 8) * TRUNK_SIDES) * 2.0 * Math.PI / TRUNK_SIDES;
        if (near) {
            ringUp[0] = -SUNK;
            ringUp[1] = Math.min(0.14f, height * 0.2f);
            ringUp[2] = Math.min(0.42f, height * 0.45f);
            ringUp[3] = height;
        } else {
            ringUp[0] = -SUNK;
            ringUp[1] = height;
        }
        for (int ring = 0; ring < rings; ring++) {
            for (int side = 0; side <= sides; side++) {
                double angle = (side % sides) * 2.0 * Math.PI / sides;
                float wide;
                if (!near) {
                    wide = ring == 0 ? 1.1f : TAPER;
                } else if (ring >= 2) {
                    wide = ring == 2 ? 1f : TAPER;
                } else {
                    // A root is where the bole runs out along the ground: every tree has three or
                    // four, each its own length.
                    double turned = (angle - first) * roots / (2.0 * Math.PI);
                    int root = Math.floorMod((int) Math.round(turned), roots);
                    float lobe = (float) Math.pow(0.5 + 0.5 * Math.cos((angle - first) * roots), 2.0) * (0.55f + 0.45f * unit(mix(seed, root, 18), 0));
                    wide = ring == 0 ? 1.25f + 1.3f * lobe : 1.08f + 0.42f * lobe;
                }
                int at = ring * (TRUNK_SIDES + 1) + side;
                float out = radius * wide + clear;
                ringX[at] = x + (float) Math.cos(angle) * out;
                ringY[at] = y + (float) Math.sin(angle) * out;
                ringSlope[at] = out;
            }
        }
        // Until here ringSlope holds each vertex's distance out. A ring leans as the band above
        // it does, and the top ring as the band below.
        for (int ring = 0; ring < rings; ring++) {
            int lower = Math.min(ring, rings - 2);
            float rise = ringUp[lower + 1] - ringUp[lower];
            for (int side = 0; side <= sides; side++) {
                int at = lower * (TRUNK_SIDES + 1) + side;
                float lean = (ringSlope[at] - ringSlope[at + TRUNK_SIDES + 1]) / rise;
                slopes[ring * (TRUNK_SIDES + 1) + side] = lean;
            }
        }
        System.arraycopy(slopes, 0, ringSlope, 0, rings * (TRUNK_SIDES + 1));
        return rings;
    }

    private final float[] slopes = new float[RINGS * (TRUNK_SIDES + 1)];

    /**
     * A boulder on (x, y), its foot on ground at height base: a ball of flat faces, longer one
     * way than the other and each corner pushed in or out its own amount, halfWide across at
     * the ground and tall high. Its middle is near the ground, so it is broadest where it
     * meets it and what is under the ground is not seen. Each face is lit partly as the flat it
     * is and partly as the round the whole is: all flat, a boulder is a cut gem. Returns the
     * triangles added: eighty near the eye and twenty further off.
     */
    int addRock(Mesh mesh, float x, float y, float base, float halfWide, float tall, boolean near, int seed, SpriteGeometry.Frame frame) {
        double turn = unit(seed, 0) * 2.0 * Math.PI;
        float cos = (float) Math.cos(turn), sin = (float) Math.sin(turn);
        float midUp = base + tall * ROCK_MIDDLE;
        // One broad lump to one side, the same for every corner, and a little of each corner's own.
        double lumpTurn = unit(seed, 8) * 2.0 * Math.PI;
        float lumpX = (float) Math.cos(lumpTurn), lumpY = (float) Math.sin(lumpTurn);
        for (int vertex = 0; vertex < BALL.length / 3; vertex++) {
            float ballX = BALL[vertex * 3], ballY = BALL[vertex * 3 + 1], ballUp = BALL[vertex * 3 + 2];
            float out = 0.82f + 0.14f * unit(mix(seed, vertex, 19), 0) + 0.12f * (ballX * lumpX + ballY * lumpY + 0.5f * ballUp);
            float bx = ballX * out * halfWide, by = ballY * out * halfWide * (0.7f + 0.3f * unit(seed, 16));
            rock[vertex * 3] = x + bx * cos - by * sin;
            rock[vertex * 3 + 1] = y + bx * sin + by * cos;
            rock[vertex * 3 + 2] = midUp + ballUp * out * tall * (1f - ROCK_MIDDLE);
        }
        int[] faces = near ? FINE_FACES : COARSE_FACES;
        mark(mesh, frame.open, 0f, ROCK);
        mesh.sway = 0f;
        for (int face = 0; face < faces.length; face += 3) {
            int a = faces[face] * 3, b = faces[face + 1] * 3, c = faces[face + 2] * 3;
            float ux = rock[b] - rock[a], uy = rock[b + 1] - rock[a + 1], uz = rock[b + 2] - rock[a + 2];
            float vx = rock[c] - rock[a], vy = rock[c + 1] - rock[a + 1], vz = rock[c + 2] - rock[a + 2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            // Outward, whichever way round the face's corners were listed.
            if (nx * (rock[a] - x) + ny * (rock[a + 1] - y) + nz * (rock[a + 2] - midUp) < 0f) {
                nx = -nx;
                ny = -ny;
                nz = -nz;
            }
            float flat = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            for (int corner = 0; corner < 3; corner++) {
                int at = faces[face + corner] * 3;
                float rx = rock[at] - x, ry = rock[at + 1] - y, rz = rock[at + 2] - midUp;
                float round = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
                mesh.face(nx / flat + ROCK_ROUNDED * rx / round, ny / flat + ROCK_ROUNDED * ry / round, nz / flat + ROCK_ROUNDED * rz / round);
                mesh.add(rock[at], rock[at + 1], rock[at + 2], 0f, 0f, frame.red, frame.green, frame.blue);
            }
        }
        return faces.length / 3;
    }

    /** How far up a boulder its middle is, as a share of its height, and how much of the round goes into the light on each flat face. */
    private static final float ROCK_MIDDLE = 0.12f, ROCK_ROUNDED = 0.8f;

    /** The corners of a ball of eighty faces, of which the first twelve are the corners of the twenty-faced one it was cut from. */
    private static final float[] BALL;
    private static final int[] COARSE_FACES, FINE_FACES;

    static {
        float golden = (1f + (float) Math.sqrt(5.0)) / 2f;
        float[][] twelve = {
            {-1, golden, 0}, {1, golden, 0}, {-1, -golden, 0}, {1, -golden, 0}, {0, -1, golden}, {0, 1, golden},
            {0, -1, -golden}, {0, 1, -golden}, {golden, 0, -1}, {golden, 0, 1}, {-golden, 0, -1}, {-golden, 0, 1}};
        COARSE_FACES = new int[] {
            0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1};
        float[] ball = new float[(12 + 30) * 3];
        for (int vertex = 0; vertex < 12; vertex++) {
            put(ball, vertex, twelve[vertex][0], twelve[vertex][1], twelve[vertex][2]);
        }
        HashMap<Integer, Integer> middles = new HashMap<>();
        int[] fine = new int[COARSE_FACES.length * 4];
        int count = 12, at = 0;
        for (int face = 0; face < COARSE_FACES.length; face += 3) {
            int[] corner = {COARSE_FACES[face], COARSE_FACES[face + 1], COARSE_FACES[face + 2]};
            int[] middle = new int[3];
            for (int edge = 0; edge < 3; edge++) {
                int from = corner[edge], to = corner[(edge + 1) % 3];
                int key = Math.min(from, to) * 64 + Math.max(from, to);
                Integer known = middles.get(key);
                if (known == null) {
                    put(ball, count, ball[from * 3] + ball[to * 3], ball[from * 3 + 1] + ball[to * 3 + 1], ball[from * 3 + 2] + ball[to * 3 + 2]);
                    known = count++;
                    middles.put(key, known);
                }
                middle[edge] = known;
            }
            int[] four = {corner[0], middle[0], middle[2], corner[1], middle[1], middle[0], corner[2], middle[2], middle[1],
                middle[0], middle[1], middle[2]};
            System.arraycopy(four, 0, fine, at, four.length);
            at += four.length;
        }
        BALL = ball;
        FINE_FACES = fine;
    }

    /** Puts a corner on the ball: the point's direction from the middle, one unit out. */
    private static void put(float[] ball, int vertex, float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        ball[vertex * 3] = x / length;
        ball[vertex * 3 + 1] = y / length;
        ball[vertex * 3 + 2] = z / length;
    }
}
