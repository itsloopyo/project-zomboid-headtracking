package com.cameraunlock.zomboid;

import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.LosUtil;
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
     * A wall met at a shallow angle has to be stopped short of by the margin over
     * the cosine, which has no bound as the angle flattens. Past 75 degrees off
     * the wall's normal the eye is sliding along the wall, not into it.
     */
    private static final float MIN_COSINE = 0.25f;

    private final GridTrace trace = new GridTrace();
    private IsoCell cell;
    private int level;
    private final GridTrace.Edges solidToTheEye = this::solidToTheEye;
    private final GridTrace.Edges solidToAShot = this::solidToAShot;

    void look(IsoCell cell, int level) {
        this.cell = cell;
        this.level = level;
    }

    /** Whether there is a map to ask. There is none while no save is being looked at. */
    boolean mapped() {
        return cell != null;
    }

    /**
     * A wall at the edge of the map's squares where one of the two is missing. Out of the air into a
     * square, the wall that made the square is on its north or west edge. Above the ground the map has
     * no square where there is only air: a wall on the square's own north or west edge is still a wall,
     * and one on the far side of a missing square cannot exist, because the wall would have made it.
     */
    private static boolean wallBesideAir(IsoGridSquare from, IsoGridSquare to, int fromX, int fromY, int toX, int toY) {
        if (from == null) {
            return to != null && (toY > fromY && to.has(IsoFlagType.collideN) || toX > fromX && to.has(IsoFlagType.collideW) || to.isSolid());
        }
        return toY < fromY && from.has(IsoFlagType.collideN) || toX < fromX && from.has(IsoFlagType.collideW);
    }

    private boolean solidToTheEye(int fromX, int fromY, int toX, int toY) {
        IsoGridSquare from = cell.getGridSquare(fromX, fromY, level);
        IsoGridSquare to = cell.getGridSquare(toX, toY, level);
        if (from == null || to == null) {
            return wallBesideAir(from, to, fromX, fromY, toX, toY);
        }
        return from.isBlockedTo(to) || to.isSolid() || from.isWindowTo(to);
    }

    /**
     * Whether a shot stops at the edge between two squares, by the game's own rule. A firearm keeps a
     * target only when the game's line of sight test, made of this test square by square
     * (LosUtil.lineClear in CombatManager.calculateHitListWeapon), finds no wall and no shut door on the
     * way; whether a character could walk across does not come into it. A doorway, an open door and a
     * window's glass stop a walker and not a shot, and the game breaks the glass as the shot goes on.
     */
    private boolean solidToAShot(int fromX, int fromY, int toX, int toY) {
        IsoGridSquare from = cell.getGridSquare(fromX, fromY, level);
        IsoGridSquare to = cell.getGridSquare(toX, toY, level);
        if (from == null || to == null) {
            return wallBesideAir(from, to, fromX, fromY, toX, toY);
        }
        return stopsAShot(to.testVisionAdjacent(fromX - toX, fromY - toY, 0, true, false));
    }

    /** Whether the game's line of sight test across an edge, as a firearm's line of fire goes by it, stops a shot there. */
    static boolean stopsAShot(LosUtil.TestResults seen) {
        return seen == LosUtil.TestResults.Blocked || seen == LosUtil.TestResults.ClearThroughClosedDoor;
    }

    /**
     * How far the eye can move from (x, y) along a unit vector before it is
     * within the margin of a wall, capped at reach. Three rays, the middle
     * one through the eye and one each side of it, so a wall's end beside the
     * path counts as well as a wall across it.
     */
    float roomForTheEye(float x, float y, float directionX, float directionY, float reach, float margin) {
        float ground = (float) Math.sqrt(directionX * directionX + directionY * directionY);
        if (ground < 1e-4f) {
            return reach;
        }
        float sideX = -directionY / ground * margin;
        float sideY = directionX / ground * margin;
        float room = reach;
        for (int ray = -1; ray <= 1; ray++) {
            float hit = trace.firstBlockedEdge(solidToTheEye, x + sideX * ray, y + sideY * ray,
                    directionX, directionY, reach + margin / MIN_COSINE);
            if (hit == Float.POSITIVE_INFINITY) {
                continue;
            }
            float cosine = Math.max(Math.abs(trace.hitFacesX ? directionX : directionY), MIN_COSINE);
            room = Math.min(room, hit - margin / cosine);
        }
        return Math.max(room, 0f);
    }

    /**
     * The distance from the eye along its forward vector to the first thing a
     * shot stops on: a wall, a closed door, the floor, the ceiling or one of
     * the characters given. Positive infinity when there is nothing within
     * reach, which is open sky or open ground.
     */
    float shotDistance(ViewCamera.Pose aim, Terrain terrain, float floorUp, float reach,
            float[] characterX, float[] characterY, float[] characterBase, int characters) {
        float nearest;
        if (aim.forwardUp < 0f) {
            nearest = downward(aim, floorUp, reach);
        } else {
            nearest = trace.firstBlockedEdge(solidToAShot, aim.eyeX, aim.eyeY, aim.forwardX, aim.forwardY, reach);
        }
        if (aim.forwardUp > 0f) {
            float toCeiling = ((level + 1) * ViewCamera.LEVEL_HEIGHT + terrain.height(aim.eyeX, aim.eyeY) - aim.eyeUp) / aim.forwardUp;
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

    /**
     * The distance along an aim below level to the first wall or floor. The aim's own level has
     * a floor only where the map has one: from an upper window, a balcony or the head of a
     * stair the shot goes on past the edge of it, down through the level below and the walls
     * that stand there, to the ground. The ground and whatever is under it is floor everywhere.
     */
    private float downward(ViewCamera.Pose aim, float floorUp, float reach) {
        int aimedFrom = level;
        try {
            float from = 0f;
            while (true) {
                float toFloor = (floorUp - (aimedFrom - level) * ViewCamera.LEVEL_HEIGHT - aim.eyeUp) / aim.forwardUp;
                float wall = from + trace.firstBlockedEdge(solidToAShot, aim.eyeX + aim.forwardX * from, aim.eyeY + aim.forwardY * from, aim.forwardX, aim.forwardY,
                        Math.min(toFloor, reach) - from);
                if (wall <= toFloor) {
                    return wall;
                }
                if (toFloor > reach) {
                    return Float.POSITIVE_INFINITY;
                }
                IsoGridSquare under = cell.getGridSquare((int) Math.floor(aim.eyeX + aim.forwardX * toFloor), (int) Math.floor(aim.eyeY + aim.forwardY * toFloor), level);
                if (level <= 0 || under != null && under.getFloor() != null) {
                    return toFloor;
                }
                level--;
                from = toFloor;
            }
        } finally {
            level = aimedFrom;
        }
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
