package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoDirections;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;

/**
 * What hangs flat on a wall: a picture, a mirror, a clock. The game has each as a sprite
 * painted on the slant its view sees the wall at. Here it is found where on the wall that
 * painting is, so that a model can hang there upright, and how the painting lies over the
 * model's face, so that a frame can hold the picture the sprite shows.
 *
 * A wall is one of the four edges of a square, and a place on it is how far along the edge
 * it is, 0 to 1, going the way the map's x or y grows.
 */
final class WallHung {
    static final int NONE = -1, NORTH = 0, WEST = 1, SOUTH = 2, EAST = 3;

    /** What painted leaves: where along the wall the painting starts and ends, and how high its foot and its top are, in levels. */
    static final int FROM = 0, TO = 1, BOTTOM = 2, TOP = 3;

    /** The least a painting is along its wall, in tiles, and up it, in levels, to be taken for something hung there. */
    private static final float NARROWEST = 0.08f, LOWEST = 0.04f;
    /** How far past the ends of its wall, the floor and the wall's top a painting may be found and still be on that wall. */
    private static final float SLACK = 0.12f;
    /** How far inside the sprite's edge the painting is taken to stop: the outermost texels are half see-through. */
    private static final float EDGE = 0.5f;

    private WallHung() {}

    /**
     * Which wall of its square a sprite laid over a wall was painted for: the one the game
     * says it is attached to, or failing that the one behind it, facing the way it does.
     * NONE for one that says neither.
     */
    static int wallOf(IsoSprite over) {
        PropertyContainer properties = over.getProperties();
        boolean north = properties.has(IsoFlagType.attachedN), west = properties.has(IsoFlagType.attachedW);
        if (north != west) {
            return north ? NORTH : WEST;
        }
        return behind(over.getFacing());
    }

    /** The wall of its square that is behind something facing that way. */
    static int behind(IsoDirections facing) {
        return facing == IsoDirections.S ? NORTH : facing == IsoDirections.E ? WEST
                : facing == IsoDirections.N ? SOUTH : facing == IsoDirections.W ? EAST : NONE;
    }

    /** Where across its square a place on a wall is. */
    static float a(int wall, float along) {
        return wall == NORTH || wall == SOUTH ? along : wall == EAST ? 1f : 0f;
    }

    /** Where down its square a place on a wall is. */
    static float b(int wall, float along) {
        return wall == WEST || wall == EAST ? along : wall == SOUTH ? 1f : 0f;
    }

    /**
     * Finds the upright rectangle of a wall that a sprite paints, from the rectangle of the
     * frame the sprite fills. Seen as the game sees it, a rectangle on a wall is a
     * parallelogram with upright sides: how far across the frame a dot is says how far along
     * the wall it is, and the frame's rows run downhill along every wall, so the sprite's top
     * row is the rectangle's top at its uphill end and its bottom row the rectangle's foot at
     * its downhill end.
     *
     * False for a sprite that paints no such thing: next to nothing, or something that runs
     * off the wall's ends, under the floor or over the wall's top, which a sprite drawn for
     * another wall does.
     */
    static boolean painted(SpriteGeometry.Frame frame, int wall, float[] out) {
        float start = SpriteGeometry.frameU(frame, a(wall, 0f), b(wall, 0f));
        float perTile = SpriteGeometry.frameU(frame, a(wall, 1f), b(wall, 1f)) - start;
        float one = (frame.left + EDGE - start) / perTile, other = (frame.left + frame.width - EDGE - start) / perTile;
        float from = Math.min(one, other), to = Math.max(one, other);
        float top = SpriteGeometry.heightDrawnAt(frame, a(wall, from), b(wall, from), frame.top + EDGE);
        float bottom = SpriteGeometry.heightDrawnAt(frame, a(wall, to), b(wall, to), frame.top + frame.height - EDGE);
        out[FROM] = from;
        out[TO] = to;
        out[BOTTOM] = bottom;
        out[TOP] = top;
        return to - from >= NARROWEST && top - bottom >= LOWEST && from >= -SLACK && to <= 1f + SLACK && bottom >= -SLACK && top <= 1f + SLACK;
    }

    /**
     * How much further along its wall, in tiles, something is that stands out tiles proud of
     * the wall than its painting is found by painted, which takes it for flat on the wall. The
     * game's view looks down the map's diagonal, so what stands out into the square is painted
     * to one side of where it is fixed and a little lower or higher.
     */
    static float alongProud(int wall, float out) {
        return wall == NORTH || wall == WEST ? out : -out;
    }

    /**
     * As alongProud, how much higher it is, in levels. A tile towards the view is painted a
     * sixth of a level down, and so is the tile along the wall it was found short by.
     */
    static float upProud(int wall, float out) {
        return alongProud(wall, out) / 3f;
    }

    /**
     * Which of the three frames a picture hangs in, by how many times as wide as it is high it
     * is: 0 the one half as high again as it is wide, 1 the square one, 2 the one half as wide
     * again as it is high. The nearest in shape, so the frame is stretched the least.
     */
    static int frameFor(float wideForHigh) {
        return wideForHigh < 0.82f ? 0 : wideForHigh > 1.22f ? 2 : 1;
    }

    /**
     * How a sprite lies over the face of a model hung on its wall, in the sprite's texture
     * coordinates: into out the place the sprite shows the point (a, b) of the square high
     * levels up, where the model's origin hangs, then what is added to that for a metre along
     * the model's x, which goes (acrossA, acrossB) over the square, and what for a metre up
     * the model, which is up levels.
     */
    static void shows(SpriteGeometry.Frame frame, float a, float b, float high, float acrossA, float acrossB, float up, float[] out) {
        float perU = (frame.uEnd - frame.uStart) / frame.width, perV = (frame.vEnd - frame.vStart) / frame.height;
        float u = SpriteGeometry.frameU(frame, a, b), v = SpriteGeometry.frameV(frame, a, b, high);
        out[0] = frame.uStart + (u - frame.left) * perU;
        out[1] = frame.vStart + (v - frame.top) * perV;
        out[2] = (SpriteGeometry.frameU(frame, a + acrossA, b + acrossB) - u) * perU;
        out[3] = (SpriteGeometry.frameV(frame, a + acrossA, b + acrossB, high) - v) * perV;
        out[4] = 0f;
        out[5] = (SpriteGeometry.frameV(frame, a, b, high + up) - v) * perV;
    }
}
