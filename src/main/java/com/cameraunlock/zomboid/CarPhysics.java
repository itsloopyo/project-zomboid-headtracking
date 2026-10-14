package com.cameraunlock.zomboid;

import java.util.HashMap;
import java.util.Locale;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import zombie.characters.IsoGameCharacter;
import zombie.core.physics.Bullet;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.objects.IsoFireManager;
import zombie.scripting.ScriptManager;
import zombie.scripting.objects.VehicleScript;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehiclesDB2;

/**
 * Vehicles played alone, as in an open-world driving game: the first person hills pull a
 * vehicle down them and throw it into the air over a crest at speed, where it has no grip
 * until it lands (Airtime); a vehicle thrown hard enough across rolls over (Rollover); and one
 * left on its roof burns out (Wreck). On a server the game's level ground moves every vehicle
 * as everyone else in the game sees it move, and none of this is done.
 *
 * Logic thread only.
 */
final class CarPhysics {
    private CarPhysics() {}

    /** What is known of one vehicle, by its physics id. */
    private static final class Kept {
        BaseVehicle vehicle;
        /** When the picture last said how the land lies under it, and how it lay. */
        long landSaidAt;
        float steep, downX, downY;
        boolean flying;
        /** The steering it left the ground with, kept while it flies. */
        float steeringHeld;
        long updatedAt;
        final Wreck wreck = new Wreck();
        final Rollover rollover = new Rollover();
        /** How hard it is thrown across before it tips: see Rollover.tipsAt. */
        float tipsAt;
        long rollSaidAt, pullSaidAt;
        /** Whether it has stood off its roof since it was met. */
        boolean stood;
    }

    /**
     * How hard a vehicle of the script is thrown across before it tips, from where its wheels
     * are: their widest from its middle across, and the deepest of their bottoms below it. A
     * vehicle with no wheels never tips.
     */
    private static float tipsAt(VehicleScript script) {
        float halfTrack = 0f, height = 0f;
        for (int i = 0; i < script.getWheelCount(); i++) {
            VehicleScript.Wheel wheel = script.getWheel(i);
            halfTrack = Math.max(halfTrack, Math.abs(wheel.getOffset().x));
            height = Math.max(height, wheel.radius - wheel.getOffset().y);
        }
        return halfTrack > 0f && height > 0f ? Rollover.tipsAt(halfTrack, height) : Float.MAX_VALUE;
    }

    private static final HashMap<Integer, Kept> KEPT = new HashMap<>();
    /** How long what the picture said of the land holds: longer, first person is off or the vehicle out of its view, and the land is level. */
    private static final long LAND_SAID_FOR_NANOS = 250_000_000L;
    /** A gap between updates of a vehicle longer than this starts it again: it was out of the loaded map, or the game paused. */
    private static final long UPDATES_LOST_NANOS = 500_000_000L;
    private static final Vector3f FORWARD = new Vector3f(), UP = new Vector3f(), GOING = new Vector3f();
    private static boolean saidOnline;

    private static int made;

    private static Kept kept(BaseVehicle vehicle) {
        Kept kept = KEPT.get(Integer.valueOf(vehicle.getId()));
        if (kept == null || kept.vehicle != vehicle) {
            // A vehicle unloaded or taken out of the world is never updated again: what was kept of
            // vehicles not updated for a minute goes as new ones are met.
            if (++made % 64 == 0) {
                long now = System.nanoTime();
                KEPT.values().removeIf(old -> now - old.updatedAt > 60_000_000_000L && now - old.landSaidAt > 60_000_000_000L);
            }
            kept = new Kept();
            kept.vehicle = vehicle;
            KEPT.put(Integer.valueOf(vehicle.getId()), kept);
        }
        return kept;
    }

    /**
     * Said by the picture each update for the vehicle a viewer drives: how the land lies under its
     * wheels and whether it is flying off it.
     */
    static void land(BaseVehicle vehicle, VehicleSlope ground, boolean flying, float steering) {
        if (Online.joined()) {
            return;
        }
        Kept kept = kept(vehicle);
        if (flying && !kept.flying) {
            kept.steeringHeld = steering;
        }
        kept.flying = flying;
        kept.steep = ground.steep();
        kept.downX = ground.downX();
        kept.downY = ground.downY();
        kept.landSaidAt = System.nanoTime();
    }

    /** In place of the game's handing a vehicle's engine, brakes and steering to its physics: a vehicle flying has nothing to push on or steer by. */
    static void control(int id, float engine, float brake, float steering) {
        Kept kept = Online.joined() ? null : KEPT.get(id);
        if (kept != null && kept.flying && System.nanoTime() - kept.landSaidAt <= LAND_SAID_FOR_NANOS) {
            Bullet.controlVehicle(id, 0f, 0f, kept.steeringHeld);
            return;
        }
        Bullet.controlVehicle(id, engine, brake, steering);
    }

    /** Called as the game begins each update of each vehicle. */
    static void update(BaseVehicle vehicle) {
        if (Online.joined()) {
            if (!saidOnline) {
                saidOnline = true;
                Log.info("on a server vehicles keep to the game's level ground: no hills pull them, none roll and none burn out on their roofs");
            }
            KEPT.clear();
            return;
        }
        // Alone every vehicle's physics is this machine's (isLocalPhysicSim asks about a server's say, and is false alone).
        if (vehicle.isBurntOrSmashed()) {
            return;
        }
        Kept kept = kept(vehicle);
        long now = System.nanoTime();
        float seconds = kept.updatedAt == 0L || now - kept.updatedAt > UPDATES_LOST_NANOS ? 0f : (now - kept.updatedAt) / 1e9f;
        kept.updatedAt = now;
        vehicle.getForwardVector(FORWARD);
        vehicle.getUpVector(UP);
        vehicle.getLinearVelocity(GOING);
        float mass = vehicle.getMass();
        boolean landKnown = now - kept.landSaidAt <= LAND_SAID_FOR_NANOS;
        if (landKnown && !kept.flying && kept.steep > 0f) {
            // The pull of its weight down the slope. The game's physics has up as its y: the map's y is its z.
            float pull = mass * Airtime.GRAVITY * kept.steep;
            Bullet.applyCentralForceToVehicle(vehicle.getId(), kept.downX * pull, 0f, kept.downY * pull);
            if (DevBuild.ON && now - kept.pullSaidAt > 2_000_000_000L) {
                kept.pullSaidAt = now;
                Log.info(String.format(Locale.ROOT, "a %s at %.1f km/h on a slope of %.3f is pulled down it towards %.2f, %.2f",
                        vehicle.getScriptName(), vehicle.getCurrentSpeedKmHour(), kept.steep, kept.downX, kept.downY));
            }
        }
        if (seconds > 0f) {
            if (kept.tipsAt == 0f) {
                kept.tipsAt = tipsAt(vehicle.getScript());
            }
            float torque = kept.rollover.step(FORWARD.x, FORWARD.z, UP.y, GOING.x, GOING.z, seconds, kept.tipsAt);
            if (DevBuild.ON && torque != 0f && now - kept.rollSaidAt > 250_000_000L) {
                kept.rollSaidAt = now;
                Log.info(String.format(Locale.ROOT, "a %s at %.1f km/h thrown across harder than the %.1f m/s/s that tips it is rolled by %.1f a kilogram, its up at %.2f",
                        vehicle.getScriptName(), Math.abs(vehicle.getCurrentSpeedKmHour()), kept.tipsAt, torque, UP.y));
            }
            if (torque != 0f) {
                // About its own forward axis, by the mass it turns.
                Bullet.applyTorqueToVehicle(vehicle.getId(), FORWARD.x * torque * mass, FORWARD.y * torque * mass, FORWARD.z * torque * mass);
            }
            // The game leaves some of its wrecks on their roofs where it spawns them: only a
            // vehicle that has stood on its wheels since it was met, and has an engine to catch, burns.
            kept.stood |= UP.y > Wreck.ON_ITS_ROOF;
            switch (kept.stood && vehicle.getPartById("Engine") != null ? kept.wreck.step(UP.y, seconds) : Wreck.Change.NONE) {
                case CATCHES -> catchFire(vehicle);
                case GOES_UP -> goUp(vehicle);
                case NONE -> { }
            }
        }
    }

    /** How long the fire under a vehicle burns, in the game's fire life: long enough to outlast its going up. */
    private static final int FIRE_LIFE = 3000;
    private static final int FIRE_ENERGY = 300;

    /** Fire takes hold under a vehicle on its roof, and whoever is inside is thrown clear of it. */
    private static void catchFire(BaseVehicle vehicle) {
        IsoGridSquare square = vehicle.getSquare();
        if (square != null) {
            IsoFireManager.StartFire(IsoWorld.instance.currentCell, square, true, FIRE_ENERGY, FIRE_LIFE);
        }
        int thrown = 0;
        for (int seat = 0; seat < vehicle.getMaxPassengers(); seat++) {
            IsoGameCharacter inside = vehicle.getCharacter(seat);
            if (inside != null) {
                vehicle.exit(inside);
                vehicle.setCharacterPosition(inside, seat, "outside");
                inside.setZ(vehicle.getZ());
                thrown++;
            }
        }
        Log.info(String.format(Locale.ROOT, "a %s on its roof at %.1f, %.1f caught fire, %d thrown clear of it", vehicle.getScriptName(), vehicle.getX(), vehicle.getY(), thrown));
    }

    /**
     * The vehicle goes up and is left as the game's burnt shell of its kind, lying as it lay. A
     * vehicle of another module than the game's own is left as the shell nearest its size.
     */
    private static void goUp(BaseVehicle vehicle) {
        IsoCell cell = IsoWorld.instance.currentCell;
        IsoGridSquare square = vehicle.getSquare();
        String shell = Wreck.shellOf(vehicle.getScriptName());
        if (shell == null) {
            shell = nearestShell(vehicle.getScript().getExtents());
        }
        vehicle.playSound("PipeBombExplode");
        Quaternionf lying = vehicle.jniTransform.getRotation(new Quaternionf());
        float x = vehicle.getX(), y = vehicle.getY(), z = vehicle.getZ(), physicsZ = vehicle.savedPhysicsZ;
        int skin = vehicle.getSkinIndex();
        zombie.iso.IsoDirections facing = vehicle.getDir();
        // Out of its seats, the world, its square and the game's store of vehicles.
        vehicle.permanentlyRemove();
        // As the game makes a smashed vehicle of a whole one (BaseVehicle.setSmashed).
        BaseVehicle burnt = new BaseVehicle(cell);
        burnt.setScriptName(shell);
        burnt.setScript();
        burnt.setSkinIndex(skin);
        burnt.setX(x);
        burnt.setY(y);
        burnt.setZ(z);
        burnt.setDir(facing);
        burnt.savedRot.set(lying);
        burnt.savedPhysicsZ = physicsZ;
        burnt.jniTransform.setRotation(lying);
        if (square != null && zombie.iso.IsoChunk.doSpawnedVehiclesInInvalidPosition(burnt)) {
            burnt.setSquare(square);
            square.chunk.vehicles.add(burnt);
            burnt.chunk = square.chunk;
            burnt.addToWorld();
            VehiclesDB2.instance.addVehicle(burnt);
        }
        if (square != null) {
            IsoFireManager.StartFire(cell, square, true, FIRE_ENERGY, FIRE_LIFE);
        }
        Log.info(String.format(Locale.ROOT, "the burning %s at %.1f, %.1f went up and is left as %s", vehicle.getScriptName(), x, y, shell));
    }

    /** The game's burnt shell whose size is nearest the given extents. */
    private static String nearestShell(Vector3f extents) {
        String nearest = null;
        float best = Float.MAX_VALUE;
        for (VehicleScript script : ScriptManager.instance.getAllVehicleScripts()) {
            String name = script.getFullName();
            if (!name.startsWith("Base.") || !name.contains("Burnt")) {
                continue;
            }
            Vector3f size = script.getExtents();
            float dx = size.x - extents.x, dy = size.y - extents.y, dz = size.z - extents.z, off = dx * dx + dy * dy + dz * dz;
            if (off < best) {
                best = off;
                nearest = name;
            }
        }
        if (nearest == null) {
            throw new IllegalStateException("the game defines no burnt vehicle shells in its own module");
        }
        return nearest;
    }
}
