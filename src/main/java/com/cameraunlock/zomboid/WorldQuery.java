package com.cameraunlock.zomboid;

import java.util.function.Predicate;
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

    /** What the game's line of fire has met so far along the shot being traced: see LineOfFire. */
    private final LineOfFire lineOfFire = new LineOfFire();

    /**
     * Whether a shot stops at the edge between two squares, as near as the game's own rule can be
     * followed edge by edge. A firearm keeps a target only when the game's line of sight test, made of
     * this test square by square (LosUtil.lineClear in CombatManager.calculateHitListWeapon), finds no
     * wall and no shut door on the way; whether a character could walk across does not come into it. A
     * doorway, an open door and a window's glass stop a walker and not a shot, and the game breaks the
     * glass as the shot goes on. The game also drops a target behind a barricade the weapon cannot get
     * past, and behind a wall only a spear's attack goes through (SpearOnlyAttackThrough), neither of
     * which this test sees: there the reticle can sit on a target the shot does not reach.
     */
    private boolean solidToAShot(int fromX, int fromY, int toX, int toY) {
        IsoGridSquare from = cell.getGridSquare(fromX, fromY, level);
        IsoGridSquare to = cell.getGridSquare(toX, toY, level);
        if (from == null || to == null) {
            return stopsAShotBesideAir(from == null ? null : from::has, to == null ? null : to::has, fromX, fromY, toX, toY);
        }
        return lineOfFire.stopsAt(to.testVisionAdjacent(fromX - toX, fromY - toY, 0, true, false));
    }

    /**
     * Whether a shot stops at an edge where the square on one side is missing, as from an upper floor out
     * over the street. The game tests no edge there; it builds what its eye cannot see past from each
     * square's own walls (IsoGridSquare.CalculateVisionBlocked), and a wall it sees through (a window's
     * glass, transparentN/W) or one with a door in it is not one. A wall on the far side of the missing
     * square cannot exist, because the wall would have made the square.
     *
     * @param from what the square left has, or null where there is none
     * @param to   what the square entered has, or null where there is none
     */
    static boolean stopsAShotBesideAir(Predicate<IsoFlagType> from, Predicate<IsoFlagType> to, int fromX, int fromY, int toX, int toY) {
        if (from == null) {
            return to != null && (toY > fromY && wallTheEyeCannotSeePast(to, true) || toX > fromX && wallTheEyeCannotSeePast(to, false));
        }
        return toY < fromY && wallTheEyeCannotSeePast(from, true) || toX < fromX && wallTheEyeCannotSeePast(from, false);
    }

    private static boolean wallTheEyeCannotSeePast(Predicate<IsoFlagType> square, boolean north) {
        return north ? square.test(IsoFlagType.collideN) && !square.test(IsoFlagType.transparentN) && !square.test(IsoFlagType.doorN)
                : square.test(IsoFlagType.collideW) && !square.test(IsoFlagType.transparentW) && !square.test(IsoFlagType.doorW);
    }

    /**
     * The game's line of fire as it goes, edge by edge: LosUtil.lineClear keeps one result along the whole
     * line, and a firearm drops its target when that ends Blocked or ClearThroughClosedDoor. The result
     * is not the last edge's: once a window has been met, a shut door with glass in it further on leaves
     * it a window, and so the shot goes on through both.
     */
    static final class LineOfFire {
        private LosUtil.TestResults soFar = LosUtil.TestResults.Clear;

        void start() {
            soFar = LosUtil.TestResults.Clear;
        }

        /** Takes in the game's test of the next edge, and answers whether the shot stops there. */
        boolean stopsAt(LosUtil.TestResults seen) {
            boolean kept = seen != LosUtil.TestResults.Blocked && soFar != LosUtil.TestResults.Clear
                    && (seen != LosUtil.TestResults.ClearThroughWindow || soFar != LosUtil.TestResults.ClearThroughOpenDoor);
            if (!kept) {
                soFar = seen;
            } else if (seen == LosUtil.TestResults.ClearThroughClosedDoor && soFar == LosUtil.TestResults.ClearThroughOpenDoor) {
                soFar = seen;
            }
            return soFar == LosUtil.TestResults.Blocked || soFar == LosUtil.TestResults.ClearThroughClosedDoor;
        }
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
     * the characters given. A character behind walls a round of the cartridge
     * goes through is the first thing it stops on (see Penetration); with no
     * cartridge, walls stop it. Positive infinity when there is nothing within
     * reach, which is open sky or open ground.
     */
    float shotDistance(ViewCamera.Pose aim, Terrain terrain, float footing, float reach,
            float[] characterX, float[] characterY, float[] characterBase, int characters, Penetration.Cartridge through) {
        float surface = toSurface(aim, terrain::height, footing, reach);
        boolean wall = met == WALL;
        float nearest = surface;
        for (int i = 0; i < characters; i++) {
            float distance = cylinderDistance(aim, characterX[i], characterY[i], characterBase[i]);
            if (distance < nearest && (distance <= surface || wall && through != null
                    && Penetration.share(cell, level, through, aim.eyeX, aim.eyeY, aim.eyeX + aim.forwardX * distance, aim.eyeY + aim.forwardY * distance) > 0f)) {
                nearest = distance;
            }
        }
        return nearest <= reach ? nearest : Float.POSITIVE_INFINITY;
    }

    /** What the last line traced met first: a wall, a floor or the ceiling over the level it was traced on. */
    static final int WALL = 0, FLOOR = 1, CEILING = 2;
    int met;
    /** Of a wall met: whether its face is across x, a wall on a square's west edge, and not across y. */
    boolean metFacesX;
    /** The floor level the wall or the floor met is on. */
    int metLevel;

    /**
     * The distance along a line to the first wall, floor or ceiling a shot stops on, with what
     * it is in met, metFacesX and metLevel. Infinity, or more than reach, where it meets none
     * within reach.
     *
     * @param footing the floor the line is traced from stands on, in tiles over the flat map
     */
    float toSurface(ViewCamera.Pose aim, Ground ground, float footing, float reach) {
        lineOfFire.start();
        float nearest = toWallOrFloor(aim, ground, footing, reach);
        if (aim.forwardUp > 0f) {
            float toCeiling = ((level + 1) * ViewCamera.LEVEL_HEIGHT + ground.height(aim.eyeX, aim.eyeY) - aim.eyeUp) / aim.forwardUp;
            if (toCeiling < nearest && toCeiling <= reach) {
                IsoGridSquare above = cell.getGridSquare((int) Math.floor(aim.eyeX + aim.forwardX * toCeiling),
                        (int) Math.floor(aim.eyeY + aim.forwardY * toCeiling), level + 1);
                if (above != null && above.getFloor() != null) {
                    nearest = toCeiling;
                    met = CEILING;
                    metLevel = level + 1;
                }
            }
        }
        return nearest;
    }

    /**
     * The distance along an aim to the first wall or floor. The aim's own level has a floor only
     * where the map has one: from an upper window, a balcony or the head of a stair the shot
     * goes on past the edge of it, down through the level below and the walls that stand there,
     * to the ground. The ground and whatever is under it is floor everywhere.
     *
     * @param footing the floor the eye stands on, in tiles over the flat map
     */
    private float toWallOrFloor(ViewCamera.Pose aim, Ground ground, float footing, float reach) {
        int aimedFrom = level;
        try {
            float from = 0f;
            while (true) {
                float toFloor = toFloor(aim, ground, footing - (aimedFrom - level) * ViewCamera.LEVEL_HEIGHT, from, reach);
                float wall = from + trace.firstBlockedEdge(solidToAShot, aim.eyeX + aim.forwardX * from, aim.eyeY + aim.forwardY * from, aim.forwardX, aim.forwardY,
                        Math.min(toFloor, reach) - from);
                if (wall <= toFloor) {
                    met = WALL;
                    metFacesX = trace.hitFacesX;
                    metLevel = level;
                    return wall;
                }
                if (toFloor > reach) {
                    return Float.POSITIVE_INFINITY;
                }
                IsoGridSquare under = cell.getGridSquare((int) Math.floor(aim.eyeX + aim.forwardX * toFloor), (int) Math.floor(aim.eyeY + aim.forwardY * toFloor), level);
                if (level <= 0 || under != null && under.getFloor() != null) {
                    met = FLOOR;
                    metLevel = level;
                    return toFloor;
                }
                level--;
                from = toFloor;
            }
        } finally {
            level = aimedFrom;
        }
    }

    /** The height of the ground the picture draws over the flat map at a place, in tiles: Terrain.height. */
    interface Ground {
        float height(float x, float y);
    }

    /**
     * How far apart along the aim it is tried against a floor, in tiles, and how often the
     * crossing found is halved after. A kerb's pavement stands 0.11 over the road, and a shallow
     * aim passes over less than a step of it.
     */
    private static final float FLOOR_STEP = 0.1f;
    private static final int FLOOR_HALVINGS = 6;

    /**
     * The distance along an aim, from `from` on, at which it first comes down onto a floor `flat`
     * tiles over the flat map as the picture draws it: raised by the ground's height at each
     * place, as everything at a place is (Terrain.lift), so a floor in a dip is lower than the
     * eye's own footing. Infinity where it meets none within reach.
     */
    static float toFloor(ViewCamera.Pose aim, Ground ground, float flat, float from, float reach) {
        float along = from;
        // Lying on a slope the eye can be under the ground drawn where it is: the aim leaves that before it can come down onto any.
        while (under(aim, ground, flat, along)) {
            if (along >= reach) {
                return Float.POSITIVE_INFINITY;
            }
            along = Math.min(along + FLOOR_STEP, reach);
        }
        float before = along;
        while (!under(aim, ground, flat, along)) {
            if (along >= reach) {
                return Float.POSITIVE_INFINITY;
            }
            before = along;
            along = Math.min(along + FLOOR_STEP, reach);
        }
        for (int i = 0; i < FLOOR_HALVINGS; i++) {
            float middle = (before + along) / 2f;
            if (under(aim, ground, flat, middle)) {
                along = middle;
            } else {
                before = middle;
            }
        }
        return along;
    }

    private static boolean under(ViewCamera.Pose aim, Ground ground, float flat, float along) {
        return aim.eyeUp + aim.forwardUp * along <= flat + ground.height(aim.eyeX + aim.forwardX * along, aim.eyeY + aim.forwardY * along);
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
