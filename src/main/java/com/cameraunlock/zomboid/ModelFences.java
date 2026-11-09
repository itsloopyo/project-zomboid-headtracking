package com.cameraunlock.zomboid;

import java.util.List;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;

/**
 * The low fences WallBuilder stands as models. The white picket fence: fencing_01_4 on a square's west edge, 5 on its
 * north edge and 6 on both, each painting a post at the square's north-west corner and pickets on two rails along the
 * edge. The dark one with rounded tops: 121 on the west edge, 120 on the north and 122 on both, nine pickets on three
 * rails and no post. The barbed wire fence: three strands along the edge, 16 on the west edge, 17 on the north and 18 on
 * both with a sawn post near the north-west corner, and 20 on the west and 21 on the north with none. As a sheet stood on
 * the edge their pickets and posts had no thickness and leaned as the game's view paints them.
 */
final class ModelFences {
    private ModelFences() {}

    private static final String SHEET = "fencing_01_";

    /** How high, in levels, the fences' paint is read: between a picket fence's rails, and halfway up the barbed wire's post. */
    static final float DYE_HIGH = 0.5f / ViewCamera.LEVEL_HEIGHT;

    /**
     * A fence: the model each edge stands as, its centre in from the edge's line and its foot over the ground; the
     * material the sprite's paint dyes, and how far along the edge from the corner and in from its line the paint is read
     * at DYE_HIGH; and the post its sprite paints, or null, with how far along and in from the north-west corner it
     * stands, and the lone post sprite that stands in its place where a square has one.
     */
    record Fence(String model, float in, float lift, int dyes, float dyeAlong, float dyeIn, String post, float postAlong, float postIn,
            String lonePost) {
        boolean posted() {
            return post != null;
        }
    }

    // A picket fence stands on the edge's line and its post on the corner.
    static final Fence WHITE = new Fence("picket_length", 0f, 0f, 1 << Models.PAINT, 0.344f, 0f, "picket_post", 0f, 0f, SHEET + "7");
    static final Fence ROUNDED = new Fence("rounded_picket_length", 0f, 0f, 1 << Models.PAINT, 0.44f, 0f, null, 0f, 0f, null);

    // Read off fencing_01_16, 17 and 20: the post's middle stands where fencing_01_19, the lone post, paints it, and the
    // strands run the post's face toward the edge, from 0.18 m up. The model stands on its lowest barb.
    private static final float WIRE_IN = 0.055f, WIRE_FOOT = 0.18f - 0.0165f, POST_ALONG = 0.133f, POST_IN = 0.102f;
    static final Fence BARBED_POSTED = new Fence("barbed_wire_length", WIRE_IN, WIRE_FOOT, 1 << Models.WOOD, POST_ALONG, POST_IN, "rail_post",
            POST_ALONG, POST_IN, SHEET + "19");
    static final Fence BARBED = new Fence("barbed_wire_length", WIRE_IN, WIRE_FOOT, 0, 0f, 0f, null, 0f, 0f, null);

    /**
     * The length of picket fence, white or rounded, standing on a square's north or west edge, or null: what a shot
     * crossing that edge may strike (ImpactFinder). Logic thread.
     */
    static IsoObject picketsOn(IsoGridSquare square, boolean north) {
        List<IsoObject> objects = SquareObjects.of(square);
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            IsoSprite sprite = object.getSprite();
            Fence fence = sprite == null ? null : of(sprite.getName());
            if (fence != WHITE && fence != ROUNDED) {
                continue;
            }
            PropertyContainer properties = sprite.getProperties();
            if (north ? Edges.north(properties, sprite.getType()) : Edges.west(properties, sprite.getType())) {
                return object;
            }
        }
        return null;
    }

    /**
     * How high, in tiles over the flat map, WallBuilder.addModelFence stands the length of fence on the north or west
     * edge of square (x, y) on a level: on the lower of the edge's two ends, raised as the game draws the object.
     * The ground at the ends is the land's height there, where the picture takes the square's corners.
     */
    static float foot(Terrain terrain, int x, int y, int level, float raised, boolean north) {
        float start = terrain.height(x, y), end = north ? terrain.height(x + 1, y) : terrain.height(x, y + 1);
        return (level + raised) * ViewCamera.LEVEL_HEIGHT + Math.min(start, end);
    }

    /**
     * What struck writes: each face met, where (x, y, up) and the way it faces (x, y, up), the face gone in by first;
     * then the way the grain runs; then the picket gone into, counted from 1, or 0 for a rail, and how high in the
     * model, in its metres, it was gone into.
     */
    static final int FACE_FLOATS = 6, GRAIN = 2 * FACE_FLOATS, PICKET = GRAIN + 3, MODEL_UP = PICKET + 1, HIT_FLOATS = MODEL_UP + 1;
    /** How far past where it goes into a picket or a rail a line is taken to come out of the same one, in tiles: a picket's thickness and more. */
    private static final float THROUGH_WITHIN = 0.1f;

    /**
     * Whether the line from the eye of a pose along its forward way meets, within reach tiles of the eye, the model of a
     * length of fence as WallBuilder.addModelFence stands it on the north or west edge of square (x, y) with its foot at
     * foot (in tiles over the flat map): its pickets and its rails, which the model has, and not the gaps between them,
     * nor what of its pickets the mask broken says shots broke out (ShotPickets: 0 for none, and for a fence with no pickets).
     * Where it does, into hit as HIT_FLOATS says: where it goes in, facing back along the line, and where it comes out of
     * the same piece, facing on along it; the way the grain of the face gone in by runs, its longest side; and the picket gone into.
     */
    static boolean struck(Fence fence, int x, int y, boolean north, float foot, ViewCamera.Pose line, float reach, long broken, float[] hit) {
        Models.Model model = Models.get(fence.model());
        ShotPickets.Layout layout = broken == 0L ? null : ShotPickets.layout(model);
        float da = north ? 1f : 0f, db = north ? 0f : 1f;
        // Worked from the square's corner and the fence's foot, where a float holds a place finely out on the map too.
        float centreA = da * 0.5f + db * fence.in(), centreB = db * 0.5f + da * fence.in(), base = fence.lift();
        float ox = line.eyeX - x, oy = line.eyeY - y, ou = line.eyeUp - foot;
        float fx = line.forwardX, fy = line.forwardY, fu = line.forwardUp;
        int turns = north ? 2 : 1;
        float[] place = new float[3], turned = new float[2], corner = new float[9];
        float nearest = Float.POSITIVE_INFINITY;
        int nearestAt = -1, triangles = model.corners.length;
        for (int t = 0; t < triangles; t += 3) {
            cornersOf(model, t, turns, centreA, centreB, base, layout, broken, place, turned, corner);
            float along = alongTo(corner, ox, oy, ou, fx, fy, fu);
            if (along >= 0f && along <= reach && along < nearest) {
                nearest = along;
                nearestAt = t;
            }
        }
        if (nearestAt < 0) {
            return false;
        }
        // The way out is the furthest face of the piece gone into: past it, what the line meets is another picket or rail.
        float furthest = nearest;
        int furthestAt = nearestAt;
        for (int t = 0; t < triangles; t += 3) {
            cornersOf(model, t, turns, centreA, centreB, base, layout, broken, place, turned, corner);
            float along = alongTo(corner, ox, oy, ou, fx, fy, fu);
            if (along > furthest && along <= nearest + THROUGH_WITHIN) {
                furthest = along;
                furthestAt = t;
            }
        }
        for (int face = 0; face < 2; face++) {
            float along = face == 0 ? nearest : furthest;
            cornersOf(model, face == 0 ? nearestAt : furthestAt, turns, centreA, centreB, base, layout, broken, place, turned, corner);
            int at = face * FACE_FLOATS;
            hit[at] = x + ox + fx * along;
            hit[at + 1] = y + oy + fy * along;
            hit[at + 2] = foot + ou + fu * along;
            float e1x = corner[3] - corner[0], e1y = corner[4] - corner[1], e1u = corner[5] - corner[2];
            float e2x = corner[6] - corner[0], e2y = corner[7] - corner[1], e2u = corner[8] - corner[2];
            float nx = e1y * e2u - e1u * e2y, ny = e1u * e2x - e1x * e2u, nu = e1x * e2y - e1y * e2x;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nu * nu);
            // In by the face that looks back along the line, out by the one that looks on along it.
            float sign = (nx * fx + ny * fy + nu * fu > 0f) == (face == 0) ? -1f / length : 1f / length;
            hit[at + 3] = nx * sign;
            hit[at + 4] = ny * sign;
            hit[at + 5] = nu * sign;
            if (face == 0) {
                grain(corner, hit);
            }
        }
        hit[PICKET] = model.piece[model.corners[nearestAt]];
        hit[MODEL_UP] = ou + fu * nearest - base;
        return true;
    }

    /** The corners of the triangle from place t in the model's list, stood as struck stands them and broken as broken says (layout null for nothing broken), into corner. */
    private static void cornersOf(Models.Model model, int t, int turns, float centreA, float centreB, float base, ShotPickets.Layout layout, long broken,
            float[] place, float[] turned, float[] corner) {
        for (int k = 0; k < 3; k++) {
            int vertex = model.corners[t + k], at = vertex * Models.VERTEX_FLOATS;
            place[0] = model.vertices[at];
            place[1] = model.vertices[at + 1];
            place[2] = model.vertices[at + 2];
            if (layout != null) {
                ShotPickets.collapse(layout, broken, model.piece[vertex], place);
            }
            Models.turn(turns, place[0], place[1], turned);
            corner[3 * k] = centreA + turned[0];
            corner[3 * k + 1] = centreB + turned[1];
            corner[3 * k + 2] = base + place[2];
        }
    }

    /** How far along the line from (ox, oy, ou) the way (fx, fy, fu) it meets the triangle of corner, or NaN where it misses it (Moller and Trumbore). */
    static float alongTo(float[] corner, float ox, float oy, float ou, float fx, float fy, float fu) {
        float e1x = corner[3] - corner[0], e1y = corner[4] - corner[1], e1u = corner[5] - corner[2];
        float e2x = corner[6] - corner[0], e2y = corner[7] - corner[1], e2u = corner[8] - corner[2];
        float px = fy * e2u - fu * e2y, py = fu * e2x - fx * e2u, pu = fx * e2y - fy * e2x;
        float det = e1x * px + e1y * py + e1u * pu;
        if (Math.abs(det) < 1e-12f) {
            return Float.NaN;
        }
        float inv = 1f / det;
        float tx = ox - corner[0], ty = oy - corner[1], tu = ou - corner[2];
        float a = (tx * px + ty * py + tu * pu) * inv;
        if (a < 0f || a > 1f) {
            return Float.NaN;
        }
        float qx = ty * e1u - tu * e1y, qy = tu * e1x - tx * e1u, qu = tx * e1y - ty * e1x;
        float b = (fx * qx + fy * qy + fu * qu) * inv;
        if (b < 0f || a + b > 1f) {
            return Float.NaN;
        }
        return (e2x * qx + e2y * qy + e2u * qu) * inv;
    }

    /** The way the longest side of the triangle of corner runs, made a unit, into hit at GRAIN: a picket's grain runs up it, a rail's along it. */
    private static void grain(float[] corner, float[] hit) {
        float best = -1f;
        for (int k = 0; k < 3; k++) {
            int from = 3 * k, to = 3 * ((k + 1) % 3);
            float gx = corner[to] - corner[from], gy = corner[to + 1] - corner[from + 1], gu = corner[to + 2] - corner[from + 2];
            float length = gx * gx + gy * gy + gu * gu;
            if (length > best) {
                best = length;
                float unit = 1f / (float) Math.sqrt(length);
                hit[GRAIN] = gx * unit;
                hit[GRAIN + 1] = gy * unit;
                hit[GRAIN + 2] = gu * unit;
            }
        }
    }

    /** The fence a sprite is a whole length of, or null. */
    static Fence of(String name) {
        return name == null ? null : switch (Sheets.place(name, SHEET)) {
            case 4, 5, 6 -> WHITE;
            case 120, 121, 122 -> ROUNDED;
            case 16, 17, 18 -> BARBED_POSTED;
            case 20, 21 -> BARBED;
            default -> null;
        };
    }
}
