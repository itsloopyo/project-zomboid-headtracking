package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import zombie.ai.State;
import zombie.ai.states.ClimbOverFenceState;
import zombie.ai.states.ClimbOverWallState;
import zombie.ai.states.ClimbThroughWindowState;
import zombie.characters.IsoGameCharacter;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * The solid edges each figure a frame draws stands against, which its model is cut at. The game
 * poses a zombie pressed to a wall, thumping a door or lunging at a fence with its hands, head and
 * shoulders reaching over the edge between the squares, and its own view hides that by the order
 * it draws in. The first person picture draws the model where it is, so a viewer on the other side
 * sees an arm or a face come through the wall. So the picture leaves out what of a figure is past a
 * solid edge of the square it stands on, seen from that square, along the edge and as far again
 * past each end as REACH.
 *
 * Solid is a wall, a shut door and a fence that cannot be seen through. A window, an open door, a
 * doorway, a fence of wire, rails or pickets and one low enough to see over are not, and neither is
 * an edge with a hole a hand goes through. A figure climbing over a fence or a wall or through a
 * window has nothing cut while it crosses.
 *
 * Each frame has its own, filled on the logic thread as it is built, where the game's squares are
 * read, and read on the render thread as it is drawn (FigureLights).
 */
final class FigureWalls {

    /** How far past a cut edge's end, along it, a figure is still cut where the next square's edge is solid too, in tiles. */
    static final float REACH = 0.5f;
    /** A figure further than this from the eye, in tiles, is left whole: a hand through a wall is then a few dots. */
    static final float WITHIN = 30f;
    /** A hole narrower than this, in tiles, takes no hand: a round's goes through and the edge stays solid. */
    static final float HAND = 0.05f;
    /** The numbers of one cut: which way its line runs, where it is, which side of it the figure keeps, and its two ends along it. */
    static final int FLOATS = 5, ACROSS_Y = 0, LINE = 1, KEEPS = 2, FROM = 3, TO = 4;
    /** How many cuts a figure has at most: two runs on each of its square's four edges. */
    static final int MOST = 8;
    /** What the draw takes of one cut, in the figure's own place (planes): two vec4. */
    static final int PLANE_FLOATS = 8;

    /** Whether the edge of a square is solid: its north edge, or its west one. */
    interface EdgeSolid {
        boolean solid(int x, int y, boolean north);
    }

    /** The cuts of one figure: count of them, FLOATS numbers each. */
    static final class Cuts {
        final float[] cuts = new float[MOST * FLOATS];
        int count;
    }

    private final IdentityHashMap<IsoGameCharacter, Cuts> people = new IdentityHashMap<>();
    private final List<Cuts> taken = new ArrayList<>();
    private int used;
    private final LongTable<Boolean> north = new LongTable<>(), west = new LongTable<>();
    private IsoCell cell;
    private int level;
    private final EdgeSolid edges = (x, y, isNorth) -> {
        LongTable<Boolean> known = isNorth ? north : west;
        long key = FigureLights.key(x, y, level);
        Boolean solid = known.get(key);
        if (solid == null) {
            solid = solid(cell.getGridSquare(x, y, level), isNorth);
            known.put(key, solid);
        }
        return solid;
    };

    /**
     * Logic thread, as a frame is built: the cuts of every figure within WITHIN of the eye.
     *
     * @return how many figures have a cut
     */
    int gather(IsoCell cell, float eyeX, float eyeY) {
        this.cell = cell;
        people.clear();
        north.clear();
        west.clear();
        used = 0;
        for (IsoMovingObject mover : cell.getObjectList()) {
            if (!(mover instanceof IsoGameCharacter character)) {
                continue;
            }
            float x = character.getX(), y = character.getY();
            float toX = x - eyeX, toY = y - eyeY;
            if (toX * toX + toY * toY > WITHIN * WITHIN || crossing(character.getCurrentState())) {
                continue;
            }
            level = (int) Math.floor(character.getZ());
            if (used == taken.size()) {
                taken.add(new Cuts());
            }
            Cuts cuts = taken.get(used);
            cuts.count = cuts(x, y, edges, cuts.cuts);
            if (cuts.count > 0) {
                people.put(character, cuts);
                used++;
            }
        }
        this.cell = null;
        return people.size();
    }

    /** Lets go of everything gathered, as the save is left. */
    void forget() {
        people.clear();
        north.clear();
        west.clear();
    }

    /** Render thread: the cuts of a figure the frame draws, or null for none. */
    Cuts of(IsoGameCharacter character) {
        return people.get(character);
    }

    /** Whether a figure is crossing an edge as the game moves it over or through one. */
    private static boolean crossing(State state) {
        return state instanceof ClimbOverFenceState || state instanceof ClimbOverWallState || state instanceof ClimbThroughWindowState;
    }

    /**
     * Into out, the cuts of a figure standing at (x, y): for each edge of its square, the runs of
     * solid edge on that line across it and the squares either side of it, from REACH before its
     * square to REACH after. Returns how many.
     */
    static int cuts(float x, float y, EdgeSolid edges, float[] out) {
        int squareX = (int) Math.floor(x), squareY = (int) Math.floor(y);
        int count = 0;
        count = line(edges, true, squareY, squareX, 1f, count, out);
        count = line(edges, true, squareY + 1, squareX, -1f, count, out);
        count = line(edges, false, squareX, squareY, 1f, count, out);
        return line(edges, false, squareX + 1, squareY, -1f, count, out);
    }

    /** The runs of solid edge on the line at `at`, along it from the square at `along` and one either side, into out from count. */
    private static int line(EdgeSolid edges, boolean acrossY, int at, int along, float keeps, int count, float[] out) {
        float from = Float.NaN;
        for (int step = -1; step <= 2; step++) {
            int square = along + step;
            boolean solid = step <= 1 && (acrossY ? edges.solid(square, at, true) : edges.solid(at, square, false));
            if (solid && Float.isNaN(from)) {
                from = Math.max(square, along - REACH);
            } else if (!solid && !Float.isNaN(from)) {
                int put = count++ * FLOATS;
                out[put + ACROSS_Y] = acrossY ? 1f : 0f;
                out[put + LINE] = at;
                out[put + KEEPS] = keeps;
                out[put + FROM] = from;
                out[put + TO] = Math.min(square, along + 1 + REACH);
                from = Float.NaN;
            }
        }
        return count;
    }

    /**
     * Into out, each cut as the draw takes it, in places counted from where the figure stands (x, y):
     * the side it keeps as (a, b, c) with a x + b y + c below nought past the edge, then the way along
     * the edge as (a, b) with the run's two ends.
     */
    static void planes(Cuts cuts, float x, float y, float[] out) {
        for (int i = 0; i < cuts.count; i++) {
            int at = i * FLOATS, put = i * PLANE_FLOATS;
            boolean acrossY = cuts.cuts[at + ACROSS_Y] != 0f;
            float keeps = cuts.cuts[at + KEEPS], line = cuts.cuts[at + LINE];
            float stands = acrossY ? y : x, runs = acrossY ? x : y;
            out[put] = acrossY ? 0f : keeps;
            out[put + 1] = acrossY ? keeps : 0f;
            out[put + 2] = keeps * (stands - line);
            out[put + 3] = 0f;
            out[put + 4] = acrossY ? 1f : 0f;
            out[put + 5] = acrossY ? 0f : 1f;
            out[put + 6] = cuts.cuts[at + FROM] - runs;
            out[put + 7] = cuts.cuts[at + TO] - runs;
        }
    }

    /** Whether a place counted from where a figure stands is cut away by planes: the rule the figures' fragment program draws by. */
    static boolean cut(float[] planes, int count, float x, float y) {
        for (int i = 0; i < count; i++) {
            int at = i * PLANE_FLOATS;
            float along = planes[at + 4] * x + planes[at + 5] * y;
            if (planes[at] * x + planes[at + 1] * y + planes[at + 2] < 0f && along >= planes[at + 6] && along <= planes[at + 7]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Into out, by columns, the matrix that takes a place seen through view, which counts from
     * (fromX, fromY, fromUp), to the same place counted from where the figure stands (x, y, up).
     * The view only turns and moves: its inverse is the transpose's turn of what it moved by.
     */
    static void fromEye(float[] view, float fromX, float fromY, float fromUp, float x, float y, float up, float[] out) {
        double a = view[0], b = view[4], c = view[8], d = view[1], e = view[5], f = view[9], g = view[2], h = view[6], k = view[10];
        double det = a * (e * k - f * h) - b * (d * k - f * g) + c * (d * h - e * g);
        double[] inverse = {
            (e * k - f * h) / det, (c * h - b * k) / det, (b * f - c * e) / det,
            (f * g - d * k) / det, (a * k - c * g) / det, (c * d - a * f) / det,
            (d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det};
        double tx = view[12], ty = view[13], tz = view[14];
        double[] off = {fromX - x, fromY - y, fromUp - up};
        for (int row = 0; row < 3; row++) {
            double r0 = inverse[row * 3], r1 = inverse[row * 3 + 1], r2 = inverse[row * 3 + 2];
            out[row] = (float) r0;
            out[4 + row] = (float) r1;
            out[8 + row] = (float) r2;
            out[12 + row] = (float) (off[row] - (r0 * tx + r1 * ty + r2 * tz));
        }
        out[3] = 0f;
        out[7] = 0f;
        out[11] = 0f;
        out[15] = 1f;
    }

    /**
     * Whether the north or west edge of a square is solid: a wall, a corner piece or a fence that
     * cannot be seen through, with no window or doorway in it and no hole a hand goes through, or a
     * door shut on it. Logic thread.
     */
    static boolean solid(IsoGridSquare square, boolean north) {
        if (square == null) {
            return false;
        }
        boolean wall = false, doorway = false, shut = false;
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            if (DoorGlass.isDoor(object)) {
                if (DoorGlass.shutOn(object, north)) {
                    if (handThrough(object, north)) {
                        return false;
                    }
                    shut = true;
                }
                continue;
            }
            IsoSprite sprite = object.getSprite();
            if (sprite == null) {
                continue;
            }
            PropertyContainer properties = sprite.getProperties();
            IsoObjectType type = sprite.getType();
            if (!properties.has(IsoFlagType.WallNW) && !(north ? Edges.north(properties, type) : Edges.west(properties, type))) {
                continue;
            }
            if (north ? properties.has(IsoFlagType.WindowN) || properties.has(IsoFlagType.windowN) || type == IsoObjectType.windowFN
                    : properties.has(IsoFlagType.WindowW) || properties.has(IsoFlagType.windowW) || type == IsoObjectType.windowFW) {
                return false;
            }
            if (north ? properties.has(IsoFlagType.DoorWallN) || type == IsoObjectType.doorFrN : properties.has(IsoFlagType.DoorWallW) || type == IsoObjectType.doorFrW) {
                doorway = true;
                continue;
            }
            if (blocks(properties, north)) {
                if (handThrough(object, north)) {
                    return false;
                }
                wall = true;
            }
        }
        return shut || wall && !doorway;
    }

    /** Whether a thing on an edge is one nothing is seen through: a wall, a corner piece, or a fence the game draws as one. */
    static boolean blocks(PropertyContainer properties, boolean north) {
        return properties.has(IsoFlagType.WallNW) || properties.has(north ? IsoFlagType.WallN : IsoFlagType.WallW);
    }

    /** Whether a thing on an edge has a hole there a hand goes through: a blast's, a round's as wide as HAND, or what a hole ringed and let fall. */
    private static boolean handThrough(IsoObject object, boolean north) {
        float[] entries = BlastHoles.of(object);
        if (entries == null) {
            return false;
        }
        for (int at = 0; at < entries.length; at += BlastHoles.FLOATS) {
            float side = entries[at + BlastHoles.SIDE];
            boolean hole = side == 0f || Math.abs(side) == BlastHoles.ROUND;
            if (hole && (entries[at + BlastHoles.NORTH] != 0f) == north && entries[at + BlastHoles.RADIUS] >= HAND) {
                return true;
            }
        }
        return BlastHoles.fallen(object, north).rectangles.length > 0;
    }
}
