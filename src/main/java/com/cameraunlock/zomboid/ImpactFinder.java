package com.cameraunlock.zomboid;

import java.util.ArrayList;
import org.joml.Vector3f;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.physics.BallisticsController;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.Vector3;
import zombie.iso.objects.IsoDoor;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;
import zombie.vehicles.BaseVehicle;

/**
 * Where a shot struck, worked out from the line the game draws its tracer along: every shot of
 * every firearm, a shotgun's pellets each, the viewer's and anyone else's. Logic thread.
 *
 * The game decides what a shot hits and says where its line ends: on a character it hit, on
 * the square of a wall it could not pass, or at the weapon's range. The game's line ends a
 * square or so out from where the picture draws a wall, so the line is traced again against
 * the walls and floors the picture builds from the same squares, by the game's own test of
 * what stops a shot (WorldQuery), and the impact is put on the face of the wall the shooter
 * sees. Nothing here changes what the game hits.
 */
final class ImpactFinder {
    /** A character this near the end of a shot's line, in tiles across the map, is the one the game hit. */
    static final float CHARACTER_NEAR = 0.45f;
    /**
     * How far past the end of the game's line a wall is looked for, in tiles, when the game says
     * a square stopped the shot: its line stops at the square's middle or short of it.
     */
    private static final float STOPPED_PAST = 1.5f;
    /** How far past the point the reticle marks the viewer's own shot is traced, in tiles: the point is on the surface. */
    private static final float AIM_PAST = 0.3f;
    /** How far past the end of a line that the game says nothing stopped a surface still counts, in tiles. */
    private static final float OPEN_PAST = 0.05f;
    /** How high on a character the blood of a shot can be, over their feet, in tiles. */
    private static final float BODY_LOW = 0.1f, BODY_HIGH = 1.7f;
    /** How far past the game's end of a line it is followed for the side of a vehicle it went in by, and how far short of that side what flies starts, in tiles. */
    private static final float VEHICLE_PAST = 4f, VEHICLE_OFF = 0.15f;
    /** A round from a firearm reaching this far, in tiles, is a rifle's, which marks more than a handgun's. */
    private static final float RIFLE_RANGE = 20f;
    /** How big a mark a rifle's round makes and a shotgun's pellet, beside a handgun's. */
    static final float RIFLE = 1.3f, PELLET = 0.55f;
    /** How far out from a wall's face the floor that what falls from it lands on is read, in tiles. */
    private static final float FALLS_OUT = 0.3f;

    private static final WorldQuery world = new WorldQuery();
    private static final ViewCamera.Pose line = new ViewCamera.Pose();
    private static final ArrayList<IsoMovingObject> near = new ArrayList<>();
    private static final float[] point = new float[3];
    private static final Vector3f from = new Vector3f(), to = new Vector3f(), entry = new Vector3f();

    private ImpactFinder() {}

    /**
     * A shot's tracer has been started. Its line runs from the muzzle the shooter's ballistics
     * have just worked out to (endX, endY, endZ) in the game's measure, z in floor levels, or
     * along the muzzle's way to the weapon's range where there is no end.
     *
     * @param stopped the square the game says stopped the shot, or null
     */
    static void ended(IsoGameCharacter shooter, float range, float endX, float endY, float endZ, IsoGridSquare stopped, boolean hasEnd) {
        BallisticsController ballistics = shooter.getBallisticsController();
        View view = shooter instanceof IsoPlayer player && View.of(player) != null ? View.of(player) : View.first();
        IsoCell cell = IsoWorld.instance.currentCell;
        if (ballistics == null || view == null || cell == null) {
            return;
        }
        Terrain terrain = view.worldScene.terrain;
        Vector3 muzzle = ballistics.getMuzzlePosition(), aim = ballistics.getMuzzleDirection();
        float startX = muzzle.x, startY = muzzle.y, startZ = muzzle.z;
        if (!hasEnd) {
            endX = startX + aim.x * range;
            endY = startY + aim.y * range;
            endZ = startZ + aim.z * range;
        }
        float startUp = startZ * ViewCamera.LEVEL_HEIGHT + terrain.lift(startX, startY, startZ);
        float endUp = endZ * ViewCamera.LEVEL_HEIGHT + terrain.lift(endX, endY, endZ);
        float size = size(shooter.getAttackingWeapon());
        if (FirstPerson.TRACE) {
            Log.info(String.format(java.util.Locale.ROOT, "shot line: muzzle (%.3f, %.3f, %.3f) up %.3f, aim (%.3f, %.3f, %.3f), end (%.3f, %.3f, %.3f) up %.3f, stopped %s, ended %b",
                    startX, startY, startZ, startUp, aim.x, aim.y, aim.z, endX, endY, endZ, endUp, stopped == null ? "no" : stopped.getX() + "," + stopped.getY(), hasEnd));
        }

        // The picture raises the land, and by a different height at each place, so the game's straight line from the muzzle
        // comes out crooked in it: a round aimed at a wall 3.5 tiles off was traced to it 0.67 tiles over the reticle, the
        // land under the shooter standing 0.67 higher than at the wall (measured). The viewer's own shot is traced to the
        // point the reticle marks, each end raised by the land where it is; a pellet goes as far to the side of that point as
        // its own line goes of the aim. Anyone else's goes to where the game ended it, a single round along its aim.
        float length;
        boolean pellet = size == PELLET && hasEnd;
        View own = shooter instanceof IsoPlayer player ? View.of(player) : null;
        if (own != null && own.firstPerson.lookCaptured()) {
            own.controls.aimPoint(point);
            float toX = point[0], toY = point[1], toUp = point[2] * ViewCamera.LEVEL_HEIGHT + terrain.lift(point[0], point[1], point[2]);
            if (pellet) {
                float wayX = endX - startX, wayY = endY - startY, wayZ = endZ - startZ;
                float share = (float) Math.sqrt(sq(point[0] - startX) + sq(point[1] - startY) + sq(point[2] - startZ))
                        / Math.max((float) Math.sqrt(wayX * wayX + wayY * wayY + wayZ * wayZ), 1e-6f);
                toX += startX + wayX * share - point[0];
                toY += startY + wayY * share - point[1];
                toUp += (startZ + wayZ * share - point[2]) * ViewCamera.LEVEL_HEIGHT;
            }
            length = along(startX, startY, startUp, toX - startX, toY - startY, toUp - startUp) + AIM_PAST;
        } else if (pellet) {
            length = along(startX, startY, startUp, endX - startX, endY - startY, endUp - startUp);
        } else {
            along(startX, startY, startUp, aim.x, aim.y, aim.z * ViewCamera.LEVEL_HEIGHT);
            length = (endX - startX) * line.forwardX + (endY - startY) * line.forwardY + (endUp - startUp) * line.forwardUp;
        }

        IsoGameCharacter hit = characterAt(cell, shooter, endX, endY, endZ);
        if (hit != null) {
            flesh(hit, terrain, size);
            return;
        }

        if (length < 1e-3f) {
            return;
        }
        float reach = length + (stopped != null ? STOPPED_PAST : OPEN_PAST);
        world.look(cell, (int) Math.floor(shooter.getZ()));
        float distance = world.toSurface(line, terrain::height, shooter.getZ() * ViewCamera.LEVEL_HEIGHT, reach);
        // The game ended the line on a vehicle it hit. The reticle's point is past it, on what the vehicle stands in front of.
        IsoGridSquare under = cell.getGridSquare((int) Math.floor(endX), (int) Math.floor(endY), (int) Math.floor(endZ));
        BaseVehicle vehicle = under == null ? null : under.getVehicleContainer();
        float body = vehicle == null ? Float.POSITIVE_INFINITY : vehicleSide(vehicle, startX, startY, startZ, Math.max(length, (float) Math.hypot(endX - startX, endY - startY)));
        if (body < Math.min(distance, reach)) {
            float up = line.eyeUp + line.forwardUp * body;
            add(line.eyeX + line.forwardX * body, line.eyeY + line.forwardY * body, up, -line.forwardX, -line.forwardY, -line.forwardUp,
                    line.forwardX, line.forwardY, line.forwardUp, up - 1f, Impacts.METAL, size, false, 0f, 0f, 1f);
            return;
        }
        if (distance <= reach) {
            surface(cell, terrain, distance, size);
            return;
        }
        if (stopped != null) {
            // Something the picture does not build stopped it, a barricade or a solid thing on the square: its face is not known.
            int kind = Impacts.ofMaterialType(stopped.getProperties().get("MaterialType"));
            float flat = (float) Math.hypot(line.forwardX, line.forwardY);
            add(endX, endY, endUp, flat > 1e-4f ? -line.forwardX / flat : 0f, flat > 1e-4f ? -line.forwardY / flat : 0f, flat > 1e-4f ? 0f : 1f,
                    line.forwardX, line.forwardY, line.forwardUp, stopped.getZ() * ViewCamera.LEVEL_HEIGHT + terrain.height(endX, endY), kind == Impacts.NONE ? Impacts.MASONRY : kind, size, false, 0f, 0f, 1f);
        }
    }

    /** How big a mark a weapon's round makes: a pellet of a shotgun's, a rifle's, or a handgun's. */
    static float size(HandWeapon weapon) {
        if (weapon == null) {
            return 1f;
        }
        return weapon.getProjectileCount() > 1 ? PELLET : weapon.getMaxRange() >= RIFLE_RANGE ? RIFLE : 1f;
    }

    private static float sq(float x) {
        return x * x;
    }

    /** Sets the line traced to start at a place in the picture's measure and go the way given, made a unit. Its length. */
    private static float along(float x, float y, float up, float wayX, float wayY, float wayUp) {
        float length = (float) Math.sqrt(wayX * wayX + wayY * wayY + wayUp * wayUp);
        line.eyeX = x;
        line.eyeY = y;
        line.eyeUp = up;
        float unit = length > 1e-6f ? 1f / length : 0f;
        line.forwardX = wayX * unit;
        line.forwardY = wayY * unit;
        line.forwardUp = length > 1e-6f ? wayUp * unit : 1f;
        return length;
    }

    /** The character other than the shooter standing nearest the end of the line, within CHARACTER_NEAR, or null. */
    static IsoGameCharacter characterAt(IsoCell cell, IsoGameCharacter shooter, float x, float y, float z) {
        IsoGameCharacter nearest = null;
        float best = CHARACTER_NEAR * CHARACTER_NEAR;
        int level = (int) Math.floor(z);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                IsoGridSquare square = cell.getGridSquare((int) Math.floor(x) + dx, (int) Math.floor(y) + dy, level);
                if (square == null) {
                    continue;
                }
                near.clear();
                near.addAll(square.getMovingObjects());
                for (IsoMovingObject object : near) {
                    if (!(object instanceof IsoGameCharacter character) || character == shooter || Math.abs(character.getZ() - z) > 1f) {
                        continue;
                    }
                    float across = (character.getX() - x) * (character.getX() - x) + (character.getY() - y) * (character.getY() - y);
                    if (across < best) {
                        best = across;
                        nearest = character;
                    }
                }
            }
        }
        near.clear();
        return nearest;
    }

    /** A shot went into a character, along the line: blood, unless the game's own blood is switched off. */
    private static void flesh(IsoGameCharacter hit, Terrain terrain, float size) {
        if (Core.getInstance().getOptionBloodDecals() == 0) {
            return;
        }
        float x = hit.getX(), y = hit.getY();
        float base = hit.getZ() * ViewCamera.LEVEL_HEIGHT + terrain.lift(x, y, hit.getZ());
        float into = WorldQuery.cylinderDistance(line, x, y, base);
        if (into == Float.POSITIVE_INFINITY) {
            // The line passes beside the body the picture's sum gives it: it went in where it passes nearest.
            float flat = line.forwardX * line.forwardX + line.forwardY * line.forwardY;
            into = flat > 1e-6f ? Math.max(((x - line.eyeX) * line.forwardX + (y - line.eyeY) * line.forwardY) / flat, 0f) : 0f;
        }
        float up = Math.clamp(line.eyeUp + line.forwardUp * into, base + BODY_LOW, base + BODY_HIGH);
        add(line.eyeX + line.forwardX * into, line.eyeY + line.forwardY * into, up, -line.forwardX, -line.forwardY, -line.forwardUp,
                line.forwardX, line.forwardY, line.forwardUp, base, Impacts.FLESH, size, false, 0f, 0f, 1f);
    }

    /**
     * How far along the line it goes into the box the game gives a vehicle, which the game works out
     * across the map alone, less VEHICLE_OFF so that what flies starts outside it; infinity where it misses.
     */
    private static float vehicleSide(BaseVehicle vehicle, float startX, float startY, float startZ, float length) {
        float flat = line.forwardX * line.forwardX + line.forwardY * line.forwardY;
        if (flat < 1e-6f) {
            return Float.POSITIVE_INFINITY;
        }
        float past = length + VEHICLE_PAST;
        from.x = startX;
        from.y = startY;
        from.z = startZ;
        to.x = startX + line.forwardX * past;
        to.y = startY + line.forwardY * past;
        to.z = startZ;
        Vector3f into = vehicle.getIntersectPoint(from, to, entry);
        return into == null ? Float.POSITIVE_INFINITY : Math.max(((into.x - startX) * line.forwardX + (into.y - startY) * line.forwardY) / flat - VEHICLE_OFF, 0f);
    }

    /** A shot struck what WorldQuery met along the line, distance along it. */
    private static void surface(IsoCell cell, Terrain terrain, float distance, float size) {
        float x = line.eyeX + line.forwardX * distance, y = line.eyeY + line.forwardY * distance, up = line.eyeUp + line.forwardUp * distance;
        int level = world.metLevel;
        if (world.met == WorldQuery.WALL) {
            boolean north = !world.metFacesX;
            float across = north ? line.forwardY : line.forwardX;
            float normal = -Math.signum(across);
            float skin = Impacts.toSkin(distance, across);
            x = line.eyeX + line.forwardX * skin;
            y = line.eyeY + line.forwardY * skin;
            up = line.eyeUp + line.forwardUp * skin;
            float normalX = north ? 0f : normal, normalY = north ? normal : 0f;
            int edgeX = north ? (int) Math.floor(x) : Math.round(x), edgeY = north ? Math.round(y) : (int) Math.floor(y);
            IsoGridSquare square = cell.getGridSquare(edgeX, edgeY, level);
            float floor = level * ViewCamera.LEVEL_HEIGHT + terrain.height(x + normalX * FALLS_OUT, y + normalY * FALLS_OUT);
            int kind = Impacts.MASONRY;
            boolean hole = false;
            IsoObject door = square == null ? null : doorOn(square, north);
            if (door != null) {
                // A door swings: what it is made of flies from it, and it keeps no hole.
                int made = Impacts.ofMaterialType(door.getSprite() == null ? null : door.getSprite().getProperties().get("MaterialType"));
                kind = made == Impacts.NONE ? Impacts.WOOD : made;
            } else {
                IsoObject wall = square == null ? null : WorldScene.wallOn(square, north);
                if (wall != null) {
                    kind = Impacts.of(wall.getSprite(), false);
                    hole = Impacts.holed(kind);
                }
            }
            add(x, y, up, normalX, normalY, 0f, line.forwardX, line.forwardY, line.forwardUp, Math.min(floor, up), kind, size, hole,
                    north ? 1f : 0f, north ? 0f : 1f, 0f);
            return;
        }
        if (world.met == WorldQuery.CEILING) {
            float floor = (level - 1) * ViewCamera.LEVEL_HEIGHT + terrain.height(x, y);
            add(x, y, up, 0f, 0f, -1f, line.forwardX, line.forwardY, line.forwardUp, floor, Impacts.MASONRY, size, true, 1f, 0f, 0f);
            return;
        }
        IsoGridSquare square = cell.getGridSquare((int) Math.floor(x), (int) Math.floor(y), level);
        IsoObject floorObject = square == null ? null : square.getFloor();
        IsoSprite sprite = floorObject == null ? null : floorObject.getSprite();
        boolean outside = square == null || square.isOutside();
        int kind = sprite == null ? (outside ? Impacts.SOIL : Impacts.MASONRY) : Impacts.of(sprite, true);
        if (outside) {
            kind = Impacts.underSnow(kind, SkyState.snowLyingNow());
        }
        if (kind == Impacts.WATER && line.forwardUp < -1e-4f) {
            // The shot is traced to the bed of the water; it strikes the surface over it.
            float surfaceUp = terrain.waterLevel(x, y);
            if (surfaceUp > up) {
                float back = (surfaceUp - up) / -line.forwardUp;
                x -= line.forwardX * back;
                y -= line.forwardY * back;
                up = surfaceUp;
            }
        }
        add(x, y, up, 0f, 0f, 1f, line.forwardX, line.forwardY, line.forwardUp, up, kind, size, Impacts.holed(kind), 1f, 0f, 0f);
    }

    /** The door hung on a square's north or west edge, shut, or null. */
    private static IsoObject doorOn(IsoGridSquare square, boolean north) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i) instanceof IsoDoor door && door.getNorth() == north && !door.IsOpen()) {
                return door;
            }
        }
        return null;
    }

    private static void add(float x, float y, float up, float normalX, float normalY, float normalUp, float wayX, float wayY, float wayUp,
            float floor, int kind, float size, boolean hole, float grainX, float grainY, float grainUp) {
        Impacts.add(new Impacts.Struck(x, y, up, normalX, normalY, normalUp, wayX, wayY, wayUp, floor, kind, size, hole, grainX, grainY, grainUp));
        if (FirstPerson.TRACE) {
            Log.info(String.format(java.util.Locale.ROOT, "impact: kind %d at (%.2f, %.2f, %.2f) facing (%.2f, %.2f, %.2f) floor %.2f size %.2f hole %b",
                    kind, x, y, up, normalX, normalY, normalUp, floor, size, hole));
        }
    }
}
