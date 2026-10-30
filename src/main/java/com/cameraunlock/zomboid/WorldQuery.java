package com.cameraunlock.zomboid;

import java.util.function.Predicate;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.LosUtil;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.util.list.PZArrayList;

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
    private final GridTrace.Edges solidToAClimber = this::solidToAClimber;
    private final GridTrace.Edges solidToAShot = this::solidToAShot;
    private final GridTrace.Edges solidToADrag = this::solidToADrag;

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

    /**
     * Whether the eye could not pass the edge between two squares side by side on the level looked at:
     * what the game's isBlockedTo says is in the way (a wall, a shut door), any window the game's
     * isWindowTo finds between the two, or a square that is solid.
     */
    boolean solidToTheEye(int fromX, int fromY, int toX, int toY) {
        IsoGridSquare from = cell.getGridSquare(fromX, fromY, level);
        IsoGridSquare to = cell.getGridSquare(toX, toY, level);
        if (from == null || to == null) {
            return wallBesideAir(from, to, fromX, fromY, toX, toY);
        }
        return from.isBlockedTo(to) || to.isSolid() || from.isWindowTo(to);
    }

    /**
     * As solidToTheEye, but for an eye the game is carrying over a fence or through a window: the head
     * goes over or through ahead of the feet, and stopping it there leaves the eye behind the body it
     * climbs with.
     */
    private boolean solidToAClimber(int fromX, int fromY, int toX, int toY) {
        IsoGridSquare from = cell.getGridSquare(fromX, fromY, level);
        IsoGridSquare to = cell.getGridSquare(toX, toY, level);
        if (from != null && to != null && (windowBetween(from, to) || fenceBetween(fromX, fromY, toX, toY,
                fenceOnly(from, true), fenceOnly(from, false), fenceOnly(to, true), fenceOnly(to, false)))) {
            return to.isSolid();
        }
        return solidToTheEye(fromX, fromY, toX, toY);
    }

    /**
     * Whether a square's north or west edge has a fence on it and no wall, window or door: a square's
     * flags are those of everything on it, and a window or a wall beside the fence still stops the eye.
     */
    private static boolean fenceOnly(IsoGridSquare square, boolean north) {
        return north ? (square.has(IsoFlagType.HoppableN) || square.has(IsoFlagType.TallHoppableN)) && !square.has(IsoFlagType.WallN)
                        && !square.has(IsoFlagType.WindowN) && !square.has(IsoFlagType.windowN) && !square.has(IsoFlagType.DoorWallN) && !square.has(IsoFlagType.doorN)
                : (square.has(IsoFlagType.HoppableW) || square.has(IsoFlagType.TallHoppableW)) && !square.has(IsoFlagType.WallW)
                        && !square.has(IsoFlagType.WindowW) && !square.has(IsoFlagType.windowW) && !square.has(IsoFlagType.DoorWallW) && !square.has(IsoFlagType.doorW);
    }

    /**
     * Whether the edge between two squares side by side has a fence on it. A square carries the fences on
     * its own north and west edges, so the one between it and the square south or east of it is that
     * square's.
     */
    static boolean fenceBetween(int fromX, int fromY, int toX, int toY, boolean fromNorth, boolean fromWest, boolean toNorth, boolean toWest) {
        if (toY != fromY) {
            return toY < fromY ? fromNorth : toNorth;
        }
        return toX < fromX ? fromWest : toX > fromX && toWest;
    }

    /** What the game's line of fire has met so far along the shot being traced: see LineOfFire. */
    private final LineOfFire lineOfFire = new LineOfFire();

    /** Whether a tree stops the shots traced only at its bole: see Trunks. Set for each shot by its shooter. */
    boolean boles;
    /**
     * Whether a window stops the line as a wall does. A barricade aimed at a window is put on the
     * square beside it, where the game puts one (BuildRecipeCode.barricade), and not out through the
     * glass. Set for each line by its drag cursor: see DragAim.onWindows.
     */
    boolean windows;
    /**
     * Whether a shut door's lights of glass let the shots traced by, as a window's glass does,
     * where Penetration lets a round through them. Set for each shot by its shooter.
     */
    boolean lights;
    /** The line being traced, from where its trace began, and the nearest it has met a bole along it. */
    private float lineX, lineY, lineWayX, lineWayY, lineFrom, boleMet;
    /** The height of the line being traced at its eye and the way it climbs, and the ground it is measured over. */
    private float lineUp, lineWayUp;
    private Ground lineGround;

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
        LosUtil.TestResults seen = to.testVisionAdjacent(fromX - toX, fromY - toY, 0, true, false);
        if (boles) {
            if (seen == LosUtil.TestResults.Blocked) {
                seen = Trunks.past(seen, from.testVisionAdjacent(toX - fromX, toY - fromY, 0, true, false), Trunks.on(from));
            }
            boleMetIn(to, toX, toY);
        }
        if (lights && seen == LosUtil.TestResults.ClearThroughClosedDoor && throughALight(from, to, fromX, fromY, toX, toY)) {
            seen = LosUtil.TestResults.ClearThroughWindow;
        }
        if (lights && (seen == LosUtil.TestResults.Blocked || seen == LosUtil.TestResults.ClearThroughClosedDoor) && throughAHole(from, to, fromX, fromY, toX, toY)) {
            seen = LosUtil.TestResults.Clear;
        }
        return lineOfFire.stopsAt(seen);
    }

    /** Whether the line being traced crosses the shut door or the fence on the edge between two squares in a hole a blast blew through it: see BlastHoles. */
    private boolean throughAHole(IsoGridSquare from, IsoGridSquare to, int fromX, int fromY, int toX, int toY) {
        boolean north = fromX == toX;
        IsoGridSquare owner = north ? (toY < fromY ? from : to) : (toX < fromX ? from : to);
        float crossed = ShotGlass.crossedAt(lineX, lineY, lineWayX, lineWayY, fromX, fromY, toX, toY);
        float x = lineX + lineWayX * crossed, y = lineY + lineWayY * crossed;
        float along = north ? x - owner.getX() : y - owner.getY();
        // Over the middle of the square the hole is on, as ImpactFinder measured it when it made it.
        float up = (lineUp + lineWayUp * crossed - lineGround.height(owner.getX() + 0.5f, owner.getY() + 0.5f)) / ViewCamera.LEVEL_HEIGHT - level;
        PZArrayList<IsoObject> objects = owner.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            if (DoorGlass.shutOn(object, north)) {
                return !DoorGlass.barricaded(object) && BlastHoles.through(object, north, along, up);
            }
        }
        IsoObject wall = WorldScene.wallOn(owner, north);
        return wall != null && BlastHoles.through(wall, north, along, up);
    }

    /** Whether the line being traced crosses the shut door on the edge between two squares in one of its lights: see DoorGlass. */
    private boolean throughALight(IsoGridSquare from, IsoGridSquare to, int fromX, int fromY, int toX, int toY) {
        // An edge is the north or west edge of the square south or east of it, and what stands on it is that square's.
        boolean north = fromX == toX;
        IsoGridSquare owner = north ? (toY < fromY ? from : to) : (toX < fromX ? from : to);
        float crossed = ShotGlass.crossedAt(lineX, lineY, lineWayX, lineWayY, fromX, fromY, toX, toY);
        float x = lineX + lineWayX * crossed, y = lineY + lineWayY * crossed;
        float up = (lineUp + lineWayUp * crossed - lineGround.height(x, y)) / ViewCamera.LEVEL_HEIGHT - level;
        PZArrayList<IsoObject> objects = owner.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            if (DoorGlass.shutOn(object, north)) {
                return !DoorGlass.barricaded(object) && DoorGlass.lightAt(object, north, north ? x - owner.getX() : y - owner.getY(), up) >= 0;
            }
        }
        return false;
    }

    /** As solidToAShot, with a window stopping the line too: see windows. */
    private boolean solidToADrag(int fromX, int fromY, int toX, int toY) {
        IsoGridSquare from = cell.getGridSquare(fromX, fromY, level);
        IsoGridSquare to = cell.getGridSquare(toX, toY, level);
        return from != null && to != null && windowBetween(from, to) || solidToAShot(fromX, fromY, toX, toY);
    }

    /** A window on the edge between two squares, or an empty frame, which the game keeps as an object of its own. */
    private static boolean windowBetween(IsoGridSquare from, IsoGridSquare to) {
        return from.isWindowTo(to) || from.getWindowFrameTo(to) != null;
    }

    /** Keeps the nearest the line being traced meets the bole drawn on a square, if it meets one. */
    private void boleMetIn(IsoGridSquare square, int x, int y) {
        float bole = Trunks.on(square);
        if (!Float.isNaN(bole)) {
            boleMet = Math.min(boleMet, Trunks.meets(lineX, lineY, lineWayX, lineWayY, x, y, bole, lineFrom));
        }
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
        return roomForTheEye(x, y, directionX, directionY, reach, margin, false);
    }

    /** As roomForTheEye, with fences and windows passed while the game climbs the character over or through one. */
    float roomForTheEye(float x, float y, float directionX, float directionY, float reach, float margin, boolean climbing) {
        float ground = (float) Math.sqrt(directionX * directionX + directionY * directionY);
        if (ground < 1e-4f) {
            return reach;
        }
        float sideX = -directionY / ground * margin;
        float sideY = directionX / ground * margin;
        float room = reach;
        for (int ray = -1; ray <= 1; ray++) {
            float hit = trace.firstBlockedEdge(climbing ? solidToAClimber : solidToTheEye, x + sideX * ray, y + sideY * ray,
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
     * Moves an eye at (at[0], at[1]) back off each wall closer to it than the margin, along x and
     * along y, to the side of the wall the body is on. The game lets the body stand a few
     * hundredths of a tile off a wall, and roomForTheEye only shortens the way from the body to the
     * eye, so an eye left there has the wall inside the near plane. The picture then shows the
     * building through the wall while RoomSight, walking from the eye's square outside, finds none
     * of its rooms and leaves out every wall in them. The side is the body's, not the eye's square,
     * as an eye on the edge itself is in the square beyond it. The margin is held under half a
     * square, so a push never takes the eye past the next edge.
     */
    static void offWalls(GridTrace trace, GridTrace.Edges solid, double[] at, double bodyX, double bodyY, float margin) {
        float held = Math.min(margin, MOST_HELD_OFF);
        // In double: a whole tile number less a float is a float, a thousandth of a tile apart at these coordinates.
        double off = held;
        for (int way = 0; way < 4; way++) {
            float alongX = way == 0 ? 1f : way == 1 ? -1f : 0f, alongY = way == 2 ? 1f : way == 3 ? -1f : 0f;
            float hit = trace.firstBlockedEdge(solid, (float) at[0], (float) at[1], alongX, alongY, held);
            if (hit >= held) {
                continue;
            }
            if (alongX != 0f) {
                long edge = Math.round(at[0] + alongX * hit);
                at[0] = bodyX < edge ? Math.min(at[0], edge - off) : Math.max(at[0], edge + off);
            } else {
                long edge = Math.round(at[1] + alongY * hit);
                at[1] = bodyY < edge ? Math.min(at[1], edge - off) : Math.max(at[1], edge + off);
            }
        }
    }

    /** The furthest offWalls holds an eye off a wall, in tiles: under half a square. */
    static final float MOST_HELD_OFF = 0.45f;

    /** As offWalls, on the level looked at. */
    void holdOffWalls(double[] at, double bodyX, double bodyY, float margin) {
        offWalls(trace, solidToTheEye, at, bodyX, bodyY, margin);
    }

    /**
     * The distance from the eye along its forward vector to the first thing a
     * shot stops on: a wall, a closed door, the floor, the ceiling or one of
     * the characters given. A character behind walls a round of the cartridge
     * goes through is the first thing it stops on (see Penetration), the walls
     * judged on the line from (shooterX, shooterY) as the game judges them, not
     * from the eye; with no cartridge, walls stop it. Positive infinity when
     * there is nothing within reach, which is open sky or open ground.
     */
    float shotDistance(ViewCamera.Pose aim, Terrain terrain, float footing, float reach,
            float[] characterX, float[] characterY, float[] characterBase, int characters, Penetration.Cartridge through, float shooterX, float shooterY) {
        float surface = toSurface(aim, terrain::height, footing, reach);
        boolean wall = met == WALL;
        float nearest = surface;
        for (int i = 0; i < characters; i++) {
            float distance = cylinderDistance(aim, characterX[i], characterY[i], characterBase[i]);
            if (distance < nearest && (distance <= surface || wall && through != null
                    && Penetration.share(cell, level, through, boles, shooterX, shooterY, characterX[i], characterY[i], aim, terrain) > 0f)) {
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
    /** Of a wall met: whether it is a tree's bole (boles), and not a wall on a square's edge. */
    boolean metBole;
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
                lineX = aim.eyeX;
                lineY = aim.eyeY;
                lineWayX = aim.forwardX;
                lineWayY = aim.forwardY;
                lineFrom = from;
                lineUp = aim.eyeUp;
                lineWayUp = aim.forwardUp;
                lineGround = ground;
                boleMet = Float.POSITIVE_INFINITY;
                float startX = aim.eyeX + aim.forwardX * from, startY = aim.eyeY + aim.forwardY * from;
                if (boles) {
                    int x = (int) Math.floor(startX), y = (int) Math.floor(startY);
                    IsoGridSquare start = cell.getGridSquare(x, y, level);
                    if (start != null) {
                        boleMetIn(start, x, y);
                    }
                }
                float wall = from + trace.firstBlockedEdge(windows ? solidToADrag : solidToAShot, startX, startY, aim.forwardX, aim.forwardY, Math.min(toFloor, reach) - from);
                boolean facesX = trace.hitFacesX;
                boolean bole = boleMet < wall;
                if (bole) {
                    wall = boleMet;
                    facesX = Math.abs(aim.eyeX + aim.forwardX * wall - (Math.floor(aim.eyeX + aim.forwardX * wall) + 0.5))
                            >= Math.abs(aim.eyeY + aim.forwardY * wall - (Math.floor(aim.eyeY + aim.forwardY * wall) + 0.5));
                }
                if (wall <= toFloor) {
                    met = WALL;
                    metFacesX = facesX;
                    metBole = bole;
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
