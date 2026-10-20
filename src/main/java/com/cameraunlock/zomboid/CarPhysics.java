package com.cameraunlock.zomboid;

import java.util.Locale;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import zombie.characters.IsoGameCharacter;
import zombie.core.physics.Bullet;
import zombie.core.physics.Transform;
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
 * vehicle down them and throw it into the air over a crest at speed, where it has no drive or
 * brakes and its steering stays where it was until it lands (Airtime); a vehicle thrown hard
 * enough across rolls over (Rollover, shelved: only with -Dcameraunlock.zomboid.rollovers=true);
 * and one left on its roof burns out (Wreck). On a server
 * the game's level ground moves every vehicle as everyone else in the game sees it move, and
 * none of this is done.
 *
 * What is worked out from how a vehicle moved is worked out once an update (update), and the
 * pushes are given once a physics step (step): the game steps its physics every 0.01 s, as
 * many times an update as the time since the last calls for, and a push lasts one step.
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
        /** How hard it is thrown across before it tips (see Rollover.tipsAt), and whether it has an engine to catch fire. */
        float tipsAt;
        boolean hasEngine;
        /** The roll to give it each physics step until the next update, per kilogram, about rollAbout, its forward axis. */
        float roll;
        final Vector3f rollAbout = new Vector3f();
        long rollSaidAt, pullSaidAt, fireSaidAt;
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

    /** By physics id, which the game gives as a short. */
    private static final Kept[] KEPT = new Kept[1 << 16];
    /** Rollovers are held back for a later release. */
    private static final boolean ROLLOVERS = Boolean.getBoolean("cameraunlock.zomboid.rollovers");
    /** How long what the picture said of the land holds: longer, first person is off or the vehicle out of its view, and the land is level. */
    private static final long LAND_SAID_FOR_NANOS = 250_000_000L;
    /** A gap between updates of a vehicle longer than this starts it again: it was out of the loaded map, or the game paused. */
    private static final long UPDATES_LOST_NANOS = 500_000_000L;
    /** How often vehicles gone from the world are let go of. */
    private static final long SWEPT_EVERY_NANOS = 10_000_000_000L;
    private static final Vector3f FORWARD = new Vector3f(), UP = new Vector3f(), GOING = new Vector3f();
    private static boolean saidOnline, joined;
    private static long sweptAt;

    private static Kept kept(BaseVehicle vehicle) {
        int id = Short.toUnsignedInt(vehicle.getId());
        Kept kept = KEPT[id];
        if (kept == null || kept.vehicle != vehicle) {
            kept = new Kept();
            kept.vehicle = vehicle;
            kept.tipsAt = tipsAt(vehicle.getScript());
            kept.hasEngine = vehicle.getPartById("Engine") != null;
            KEPT[id] = kept;
        }
        return kept;
    }

    /** Lets go of every vehicle gone from the world, which the game never updates again. */
    private static void sweep(long now) {
        if (now - sweptAt < SWEPT_EVERY_NANOS) {
            return;
        }
        sweptAt = now;
        for (int id = 0; id < KEPT.length; id++) {
            if (KEPT[id] != null && KEPT[id].vehicle.isRemovedFromWorld()) {
                KEPT[id] = null;
            }
        }
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

    /** In place of the game's handing a vehicle's engine, brakes and steering to its physics: a vehicle flying has nothing to push on or brake against. */
    static void control(int id, float engine, float brake, float steering) {
        Kept kept = Online.joined() ? null : KEPT[Short.toUnsignedInt((short) id)];
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
            if (!joined) {
                joined = true;
                java.util.Arrays.fill(KEPT, null);
            }
            return;
        }
        joined = false;
        // Alone every vehicle's physics is this machine's: isLocalPhysicSim asks whether a server
        // has given it to this machine, and is false alone.
        if (vehicle.isRemovedFromWorld() || vehicle.isBurntOrSmashed()) {
            return;
        }
        long now = System.nanoTime();
        sweep(now);
        Kept kept = kept(vehicle);
        float seconds = kept.updatedAt == 0L || now - kept.updatedAt > UPDATES_LOST_NANOS ? 0f : (now - kept.updatedAt) / 1e9f;
        kept.updatedAt = now;
        kept.roll = 0f;
        if (seconds <= 0f) {
            return;
        }
        vehicle.getForwardVector(FORWARD);
        vehicle.getUpVector(UP);
        vehicle.getLinearVelocity(GOING);
        kept.roll = ROLLOVERS ? kept.rollover.step(FORWARD.x, FORWARD.z, UP.y, GOING.x, GOING.z, seconds, kept.tipsAt) : 0f;
        kept.rollAbout.set(FORWARD.x, FORWARD.y, FORWARD.z);
        if (DevBuild.ON && kept.roll != 0f && now - kept.rollSaidAt > 250_000_000L) {
            kept.rollSaidAt = now;
            Log.info(String.format(Locale.ROOT, "a %s at %.1f km/h thrown across harder than the %.1f m/s/s that tips it is rolled by %.1f a kilogram, its up at %.2f",
                    vehicle.getScriptName(), Math.abs(vehicle.getCurrentSpeedKmHour()), kept.tipsAt, kept.roll, UP.y));
        }
        // The game leaves some of its wrecks on their roofs where it spawns them: only a
        // vehicle that has stood on its wheels since it was met, and has an engine to catch, burns.
        kept.stood |= UP.y > Wreck.ON_ITS_ROOF;
        if (DevBuild.ON && kept.wreck.stage == Wreck.Stage.ALIGHT && now - kept.fireSaidAt > 1_000_000_000L) {
            kept.fireSaidAt = now;
            zombie.iso.objects.IsoFire fire = vehicle.getSquare() == null ? null : vehicle.getSquare().getFire();
            Log.info("the burning " + vehicle.getScriptName() + "'s fire " + (fire == null ? "is out" : "is at stage " + fire.lifeStage + ", " + fire.lifeStageTimer + " left of it"));
        }
        // With its row off no vehicle catches; one already alight burns out.
        boolean burns = kept.stood && kept.hasEngine && (kept.wreck.stage != Wreck.Stage.WHOLE || Rules.rolledBurn());
        switch (burns ? kept.wreck.step(UP.y, seconds) : Wreck.Change.NONE) {
            case CATCHES -> catchFire(vehicle);
            case GOES_UP -> goUp(vehicle);
            case NONE -> { }
        }
    }

    /** Called before each physics step for each vehicle (BaseVehicle.applyAllImpulsesFromProneCharacters): the pull down the slope and the roll. */
    static void step(BaseVehicle vehicle) {
        if (Online.joined()) {
            return;
        }
        Kept kept = KEPT[Short.toUnsignedInt(vehicle.getId())];
        if (kept == null || kept.vehicle != vehicle) {
            return;
        }
        long now = System.nanoTime();
        float mass = vehicle.getMass();
        if (!kept.flying && kept.steep > 0f && now - kept.landSaidAt <= LAND_SAID_FOR_NANOS) {
            // The pull of its weight down the slope. The game's physics has up as its y: the map's y is its z.
            float pull = mass * Airtime.GRAVITY * kept.steep;
            Bullet.applyCentralForceToVehicle(vehicle.getId(), kept.downX * pull, 0f, kept.downY * pull);
            if (DevBuild.ON && now - kept.pullSaidAt > 2_000_000_000L) {
                kept.pullSaidAt = now;
                Log.info(String.format(Locale.ROOT, "a %s at %.1f km/h on a slope of %.3f is pulled down it towards %.2f, %.2f",
                        vehicle.getScriptName(), vehicle.getCurrentSpeedKmHour(), kept.steep, kept.downX, kept.downY));
            }
        }
        if (kept.roll != 0f) {
            float torque = kept.roll * mass;
            Bullet.applyTorqueToVehicle(vehicle.getId(), kept.rollAbout.x * torque, kept.rollAbout.y * torque, kept.rollAbout.z * torque);
        }
    }

    /** How long the fire under a vehicle burns, in the game's fire life: long enough to outlast its going up. */
    private static final int FIRE_LIFE = 3000;
    private static final int FIRE_ENERGY = 300;

    /**
     * Fire takes hold under a vehicle on its roof and at its engine end, at its height at once,
     * and whoever is inside is thrown clear of it, as the game's own getting out puts them.
     */
    private static void catchFire(BaseVehicle vehicle) {
        IsoGridSquare square = vehicle.getSquare();
        light(square);
        vehicle.getForwardVector(FORWARD);
        float half = vehicle.getScript().getExtents().z * 0.4f;
        light(IsoWorld.instance.currentCell.getGridSquare((int) Math.floor(vehicle.getX() + FORWARD.x * half), (int) Math.floor(vehicle.getY() + FORWARD.z * half),
                (int) Math.floor(vehicle.getZ())));
        int thrown = 0;
        for (int seat = 0; seat < vehicle.getMaxPassengers(); seat++) {
            IsoGameCharacter inside = vehicle.getCharacter(seat);
            if (inside != null) {
                // Out of the seat, with OnExitVehicle for the dashboard and whatever else listens, as ISExitVehicle.perform does it.
                inside.ensureNotInVehicle();
                vehicle.setCharacterPosition(inside, seat, "outside");
                inside.setZ(vehicle.getZ());
                inside.PlayAnim("Idle");
                thrown++;
            }
        }
        Log.info(String.format(Locale.ROOT, "a %s on its roof at %.1f, %.1f %s, %d thrown clear of it", vehicle.getScriptName(), vehicle.getX(), vehicle.getY(),
                square.getFire() != null ? "caught fire" : "would not catch fire on its square", thrown));
    }

    /**
     * Starts a fire on the square, where there is one, and brings it to its height at once: a fresh
     * fire is a small flame for its first stages, and a burning vehicle is not. Its stage is set to
     * the one before its height with a moment left in it, and the game's own next step of it
     * (IsoFire.updateFromTimer) gives it its height's flames, smoke and light.
     */
    private static void light(IsoGridSquare square) {
        if (square == null) {
            return;
        }
        IsoFireManager.StartFire(IsoWorld.instance.currentCell, square, true, FIRE_ENERGY, FIRE_LIFE);
        zombie.iso.objects.IsoFire fire = square.getFire();
        if (fire != null && !fire.perm && fire.lifeStage < FIRE_HEIGHT - 1) {
            fire.lifeStage = FIRE_HEIGHT - 1;
            // A timer the game counts down from above nothing: at nothing it moves the fire on a stage.
            fire.lifeStageTimer = 1;
        }
    }

    /** The stage of a spreading fire at its height (Fires.size). */
    private static final int FIRE_HEIGHT = 3;

    /**
     * The vehicle goes up and is left as the game's burnt shell of its kind, lying as it lay, and
     * what was in it burns with it. A vehicle of another module than the game's own is left as
     * the shell nearest its size. Where the game would not have the shell stand (indoors with no
     * vehicle zone, or over another vehicle) the vehicle burns on as it is.
     */
    private static void goUp(BaseVehicle vehicle) {
        IsoCell cell = IsoWorld.instance.currentCell;
        IsoGridSquare square = vehicle.getSquare();
        String shell = Wreck.shellOf(vehicle.getScriptName());
        if (shell == null) {
            shell = nearestShell(vehicle.getScript().getExtents());
        }
        Quaternionf lying = vehicle.jniTransform.getRotation(new Quaternionf());
        Transform lay = vehicle.getWorldTransform(new Transform());
        // As the game makes a smashed vehicle of a whole one (BaseVehicle.setSmashed).
        BaseVehicle burnt = new BaseVehicle(cell);
        burnt.setScriptName(shell);
        burnt.setScript();
        burnt.setSkinIndex(vehicle.getSkinIndex());
        burnt.setX(vehicle.getX());
        burnt.setY(vehicle.getY());
        burnt.setZ(vehicle.getZ());
        burnt.setDir(vehicle.getDir());
        burnt.savedRot.set(lying);
        burnt.savedPhysicsZ = vehicle.savedPhysicsZ;
        burnt.jniTransform.setRotation(lying);
        vehicle.playSound("PipeBombExplode");
        light(square);
        if (!zombie.iso.IsoChunk.doSpawnedVehiclesInInvalidPosition(burnt)) {
            Log.info(String.format(Locale.ROOT, "the burning %s at %.1f, %.1f went up and is left as it is: the game would not stand a %s there",
                    vehicle.getScriptName(), vehicle.getX(), vehicle.getY(), shell));
            return;
        }
        KEPT[Short.toUnsignedInt(vehicle.getId())] = null;
        // Out of its seats, the world, its square and the game's store of vehicles, with what was in it.
        vehicle.permanentlyRemove();
        burnt.setSquare(square);
        square.chunk.vehicles.add(burnt);
        burnt.chunk = square.chunk;
        burnt.addToWorld();
        // Its physics made, it lies where the burning vehicle lay, as high as that lay on its roof.
        burnt.setWorldTransform(lay);
        VehiclesDB2.instance.addVehicle(burnt);
        Log.info(String.format(Locale.ROOT, "the burning %s at %.1f, %.1f went up and is left as %s", vehicle.getScriptName(), burnt.getX(), burnt.getY(), shell));
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
