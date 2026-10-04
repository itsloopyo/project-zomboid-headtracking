package com.cameraunlock.zomboid;

import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.SpriteDetails.IsoFlagType;

/**
 * What the loaded map says is in the way of the eye and of a shot. Both
 * questions are answered by the game's own tests of the edge between two
 * squares, never by what a sprite is called. Logic thread only.
 */
final class WorldQuery {
    /** A figure's radius and height in tiles, the size the view draws a character at. */
    static final float CHARACTER_RADIUS = 0.3f;
    static final float CHARACTER_HEIGHT = 1.75f;

    /**
     * A wall met at a shallow angle has to be stopped short of by SKIN over
     * the cosine, which has no bound as the angle flattens. Past 75 degrees off
     * the wall's normal the eye is sliding along the wall, not into it.
     */
    private static final float MIN_COSINE = 0.25f;

    private final GridTrace trace = new GridTrace();
    private IsoCell cell;
    private int level;
    private final GridTrace.Edges solidToTheEye = (fromX, fromY, toX, toY) -> blocked(fromX, fromY, toX, toY, true);
    private final GridTrace.Edges solidToAShot = (fromX, fromY, toX, toY) -> blocked(fromX, fromY, toX, toY, false);

    void look(IsoCell cell, int level) {
        this.cell = cell;
        this.level = level;
    }

    private boolean blocked(int fromX, int fromY, int toX, int toY, boolean glassBlocks) {
        IsoGridSquare from = cell.getGridSquare(fromX, fromY, level);
        IsoGridSquare to = cell.getGridSquare(toX, toY, level);
        if (from == null) {
            return false;
        }
        if (to == null) {
            // Above the ground the map has no square where there is only air. A wall on this
            // square's own north or west edge is still a wall; one on the far side of a missing
            // square cannot exist, because the wall would have made the square.
            return toY < fromY && from.has(IsoFlagType.collideN) || toX < fromX && from.has(IsoFlagType.collideW);
        }
        return from.isBlockedTo(to) || to.isSolid() || glassBlocks && from.isWindowTo(to);
    }

    /**
     * How far the eye can move from (x, y) along a unit vector before it is
     * within LeanClamp.SKIN of a wall, capped at reach. Three rays, the middle
     * one through the eye and one each side of it, so a wall's end beside the
     * path counts as well as a wall across it.
     */
    float roomForTheEye(float x, float y, float directionX, float directionY, float reach) {
        float ground = (float) Math.sqrt(directionX * directionX + directionY * directionY);
        if (ground < 1e-4f) {
            return reach;
        }
        float sideX = -directionY / ground * LeanClamp.SKIN;
        float sideY = directionX / ground * LeanClamp.SKIN;
        float room = reach;
        for (int ray = -1; ray <= 1; ray++) {
            float hit = trace.firstBlockedEdge(solidToTheEye, x + sideX * ray, y + sideY * ray,
                    directionX, directionY, reach + LeanClamp.SKIN / MIN_COSINE);
            if (hit == Float.POSITIVE_INFINITY) {
                continue;
            }
            float cosine = Math.max(Math.abs(trace.hitFacesX ? directionX : directionY), MIN_COSINE);
            room = Math.min(room, hit - LeanClamp.SKIN / cosine);
        }
        return Math.max(room, 0f);
    }

    /**
     * The distance from the eye along its forward vector to the first thing a
     * shot stops on: a wall, a closed door, the floor, the ceiling or one of
     * the characters given. Positive infinity when there is nothing within
     * reach, which is open sky or open ground.
     */
    float shotDistance(ViewCamera.Pose aim, float floorUp, float reach,
            float[] characterX, float[] characterY, float[] characterBase, int characters) {
        float nearest = trace.firstBlockedEdge(solidToAShot, aim.eyeX, aim.eyeY, aim.forwardX, aim.forwardY, reach);
        if (aim.forwardUp < 0f) {
            nearest = Math.min(nearest, (floorUp - aim.eyeUp) / aim.forwardUp);
        } else if (aim.forwardUp > 0f) {
            float toCeiling = ((level + 1) * ViewCamera.LEVEL_HEIGHT + Terrain.height(aim.eyeX, aim.eyeY) - aim.eyeUp) / aim.forwardUp;
            if (toCeiling < nearest && toCeiling <= reach) {
                IsoGridSquare above = cell.getGridSquare((int) Math.floor(aim.eyeX + aim.forwardX * toCeiling),
                        (int) Math.floor(aim.eyeY + aim.forwardY * toCeiling), level + 1);
                if (above != null && above.getFloor() != null) {
                    nearest = toCeiling;
                }
            }
        }
        for (int i = 0; i < characters; i++) {
            nearest = Math.min(nearest, cylinderDistance(aim, characterX[i], characterY[i], characterBase[i]));
        }
        return nearest <= reach ? nearest : Float.POSITIVE_INFINITY;
    }

    /** Where a ray enters an upright cylinder of a character's size standing at (x, y, base), or infinity. */
    static float cylinderDistance(ViewCamera.Pose ray, float x, float y, float base) {
        float offsetX = ray.eyeX - x, offsetY = ray.eyeY - y;
        float a = ray.forwardX * ray.forwardX + ray.forwardY * ray.forwardY;
        float b = 2f * (offsetX * ray.forwardX + offsetY * ray.forwardY);
        float c = offsetX * offsetX + offsetY * offsetY - CHARACTER_RADIUS * CHARACTER_RADIUS;
        float discriminant = b * b - 4f * a * c;
        if (a < 1e-8f || discriminant < 0f) {
            return Float.POSITIVE_INFINITY;
        }
        float distance = (-b - (float) Math.sqrt(discriminant)) / (2f * a);
        float height = ray.eyeUp + ray.forwardUp * distance - base;
        return distance > 0f && height >= 0f && height <= CHARACTER_HEIGHT ? distance : Float.POSITIVE_INFINITY;
    }
}
