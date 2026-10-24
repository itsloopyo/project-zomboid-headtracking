package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3f;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.network.GameClient;
import zombie.scripting.objects.VehicleScript;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehiclePart;
import zombie.vehicles.VehicleWindow;

/**
 * The vehicle the viewer sits in, as the first-person picture needs it: which seat, where the
 * head in that seat is, and what the cabin shows.
 *
 * Everything but the wheels is set on the game's logic thread and read by the picture.
 */
final class Ride {
    /** The eye of someone seated in a vehicle, above the ground the vehicle stands on, in tiles. Set by eye in Base.CarNormal. */
    private static final float EYE_HEIGHT_IN_VEHICLE = 1.1f;
    /** How far in from the door the driver's head is: in line with the middle of the wheel. */
    private static final float HEAD_INBOARD = 0.64f;
    private static final float[] DRIVER = {0f, 0f};
    /** The speedometer reads to 160 km/h, a hundred miles an hour, and the rev counter to 7000. */
    static final float SPEEDOMETER_FULL = 160f, REV_COUNTER_FULL = 7000f;

    /** Whether the viewer is in a vehicle. */
    volatile boolean seated;
    /** Where the vehicle's middle was on the map and the height of the land under it, when the eye was seated in it. */
    float atX, atY, groundUp;
    /** How the vehicle lies on the land under it (VehicleSlope.turn), which the picture tips the vehicle and all in it by. */
    final float[] turn = new float[9];
    /** How the vehicle lies on its springs, and how the ground under its wheels would have it this frame. */
    private final VehicleSlope slope = new VehicleSlope(), ground = new VehicleSlope(), held = new VehicleSlope();
    /** Whether the vehicle has left the land, flying over a crest, and how high it is: see Airtime. */
    private final Airtime air = new Airtime();
    /** How long the vehicle had been in the air, and how far over the land it has been at most, for the dev build's line on landing. */
    private float wasAloft, highest;
    /** How long the springs take to carry the vehicle most of the way to a new lie of the ground: seconds. */
    private static final float SPRUNG_FOR = 0.08f;
    /** A gap between frames longer than this, or another vehicle, puts the vehicle straight where the ground has it. */
    private static final long SPRINGS_LOST_NANOS = 500_000_000L;
    private BaseVehicle carried;
    private long followedAt;
    /** Whether the viewer is in the driver's seat of it, and how dark the viewer's skin is, as the game numbers its skins. */
    volatile boolean driving;
    volatile int skin;
    /** Whether a door of the cabin the viewer sits in stands open. */
    volatile boolean doorOpen;
    /** Whether the vehicle the viewer sits in has its engine running, and so its instruments lit. */
    volatile boolean instrumentsLit;
    /** What the instruments of the vehicle the viewer sits in show, each as a part of its gauge's full scale. */
    volatile float roadSpeed, engineSpeed, fuel;
    /** How far the vehicle the viewer sits in is steered, as the game counts it (0.9 at full lock standing still), to the right positive. */
    volatile float steering;
    /** Whether the vehicle the viewer sits in has its headlights on. */
    volatile boolean lampsOn;
    /**
     * Where the vehicle the viewer sits in seats its passengers: across, up and along for each,
     * from the middle of its model, in tiles, as its script has them. The driver is the first.
     */
    volatile float[] seats;
    /**
     * The script name of the vehicle the viewer sits in, or last sat in: null before the first.
     * The picture reads it after it has seen seated, on another thread, so it is set before
     * seated on the way in and kept on the way out.
     */
    volatile String seatedIn;
    /**
     * The vehicle the viewer sits in, or last sat in, for telling its model from another vehicle's beside it and for
     * nothing else. Null before the first, and once the view lets go of the map.
     */
    volatile BaseVehicle sat;
    /** The way the viewer's vehicle faces, on the map. */
    volatile float aheadX, aheadY;
    /**
     * The way the viewer's vehicle faces, the side to the left of that and its own up, in the
     * world, as the game's physics has it: nose up or down, on its side or on its roof. The
     * picture reads them on another thread, so a new array is set whole each time.
     */
    volatile float[] stands = {1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};
    /**
     * How the viewer's vehicle is turned from standing level on its heading, as Cabins.tilt
     * gives it, or null on foot. The view from a seat is turned by it, so the viewer tips and
     * rolls with the vehicle. Set and read on the logic thread.
     */
    float[] tilt;
    private final float[] tilted = new float[9];
    /** How fast the viewer's vehicle is going on the map, as the game's physics has it. */
    volatile float goingX, goingY;

    /**
     * For each vehicle script the viewer has sat in: the wheel's middle from the vehicle's
     * centre of mass, along the vehicle, across it and up it, by its own axes as it was tipped
     * when the picture first drew it for this viewer, in tiles; how far across it is from the
     * body's own middle, which says which side of the vehicle the wheel is on however it lies;
     * and how far the centre of mass is above the ground under the vehicle's middle, along the
     * land's up. Written by the picture's thread: see VehicleMeshes.note.
     */
    final ConcurrentHashMap<String, float[]> wheels = new ConcurrentHashMap<>();
    /** What the viewer wears on the arms and hands, asked from the driver's seat and with a weapon in hand. */
    final Cabins.Dress dress = new Cabins.Dress();

    private String seatsOf;
    private final Vector3f seatAt = new Vector3f();
    private final Vector3f facing = new Vector3f();
    private final Vector3f above = new Vector3f();
    private final Vector3f going = new Vector3f();
    private int seatSaid;
    private int wearAsked;
    private int nearAsked;
    private boolean wasDriving;


    /**
     * Notes the vehicle the player sits in, or that there is none, and moves the eye to the
     * head of whoever sits in the player's seat: in a vehicle the eye is at the seat, not
     * where the game keeps the character on its map.
     *
     * @param vehicle the vehicle the player is in, or null
     * @param floorUp how high the floor under the player is
     * @param terrain the lie of the land the player's view is drawn on
     * @param eye     the eye's x, y and height, which a seat replaces
     */
    void note(IsoPlayer player, BaseVehicle vehicle, float floorUp, Terrain terrain, float[] eye) {
        // The picture reads the seats as soon as it sees the viewer seated: they are set first.
        if (vehicle != null && !vehicle.getScriptName().equals(seatsOf)) {
            seatsOf = vehicle.getScriptName();
            seats = seatsOf(vehicle.getScript());
        }
        if (vehicle != null) {
            seatedIn = vehicle.getScriptName();
        }
        // Kept on the way out, as seatedIn is: the picture of the frame before is still drawn seated.
        if (vehicle != null) {
            sat = vehicle;
        }
        seated = vehicle != null;
        if (vehicle == null && Own.cabins() && ++nearAsked % 20 == 0) {
            BaseVehicle near = player.getNearVehicle();
            if (near != null) {
                CabinModel.readAhead(near.getScriptName());
            }
        }
        instrumentsLit = vehicle != null && vehicle.isEngineRunning();
        if (vehicle == null) {
            tilt = null;
            wasDriving = false;
            return;
        }
        vehicle.getForwardVector(facing);
        Vector3f extents = vehicle.getScript().getExtents();
        ground.lie(terrain, vehicle.getX(), vehicle.getY(), vehicle.getZ(), facing.x, facing.z, extents.z, extents.x);
        long now = System.nanoTime();
        boolean jumped = vehicle != carried || now - followedAt > SPRINGS_LOST_NANOS;
        float seconds = (now - followedAt) / 1e9f;
        held.copy(slope);
        slope.follow(ground, 1f - (float) Math.exp(-seconds / SPRUNG_FOR), jumped);
        // On a server the vehicle keeps to the land: there the game's level ground is what moves it.
        // On the ground it goes where its springs carry it; in the air it comes down on the land itself.
        air.step(air.airborne ? ground.groundUp : slope.groundUp, seconds, jumped || Online.joined());
        slope.groundUp = air.height;
        if (air.airborne) {
            // In the air nothing holds it to the land: it keeps the lie it left the ground with.
            slope.copy(held);
            slope.groundUp = air.height;
            highest = Math.max(highest, air.height - ground.groundUp);
        }
        if (DevBuild.ON && air.landedAt > 0f && wasAloft >= Airtime.FLYING_AFTER) {
            Log.info(String.format(Locale.ROOT, "a %s at %.1f km/h landed at %.1f, %.1f after %.2f s in the air, up to %.2f over the land, falling at %.1f m/s",
                    vehicle.getScriptName(), Math.abs(vehicle.getCurrentSpeedKmHour()), vehicle.getX(), vehicle.getY(), wasAloft, highest, air.landedAt));
        }
        if (!air.airborne) {
            highest = 0f;
        }
        wasAloft = air.aloft;
        carried = vehicle;
        followedAt = now;
        atX = vehicle.getX();
        atY = vehicle.getY();
        groundUp = slope.groundUp;
        System.arraycopy(slope.turn, 0, turn, 0, turn.length);
        roadSpeed = Math.abs(vehicle.getCurrentSpeedKmHour()) / SPEEDOMETER_FULL;
        engineSpeed = vehicle.isEngineRunning() ? (float) vehicle.getEngineSpeed() / REV_COUNTER_FULL : 0f;
        VehiclePart tank = vehicle.getPartById("GasTank");
        fuel = tank == null || tank.getContainerCapacity() <= 0 ? 0f : tank.getContainerContentAmount() / tank.getContainerCapacity();
        // The game counts a turn to the left as positive: with the right key held it read -0.9 at full lock.
        steering = -vehicle.getCurrentSteering();
        lampsOn = vehicle.getHeadlightsOn();
        // The seats are fixed in the vehicle, which the game's physics tips and rolls and
        // in a crash throws on its side or its roof. The game gives the way a vehicle
        // faces, its up and its speed with y as up.
        vehicle.getUpVector(above);
        float nose = (float) Math.sqrt(facing.x * facing.x + facing.y * facing.y + facing.z * facing.z);
        float high = (float) Math.sqrt(above.x * above.x + above.y * above.y + above.z * above.z);
        float[] axes = new float[9];
        Cabins.axes(facing.x / nose, facing.z / nose, facing.y / nose, above.x / high, above.z / high, above.y / high, axes);
        for (int axis = 0; axis < 9; axis += 3) {
            slope.apply(axes, axis);
        }
        stands = axes;
        float flat = Math.max((float) Math.hypot(facing.x, facing.z), 1e-3f);
        Cabins.tilt(axes, facing.x / flat, facing.z / flat, tilted);
        tilt = tilted;
        vehicle.getLinearVelocity(going);
        goingX = going.x;
        goingY = going.z;

        int seat = vehicle.getSeat(player);
        driving = seat == 0;
        if (driving) {
            CarPhysics.land(vehicle, ground, air.flying(), vehicle.getCurrentSteering());
        }
        if (driving && !wasDriving) {
            putOutHandLights(player);
        }
        wasDriving = driving;
        doorOpen = seat >= 0 && Cabins.doorOpen(vehicle, seat);
        skin = player.getHumanVisual().getSkinTextureIndex();
        // What is worn changes seldom, and asking makes a list for every garment.
        if (driving && (wearAsked++ & 31) == 0) {
            Cabins.noteWear(player, dress);
        }
        if (seat >= 0) {
            seatTheEye(vehicle, seat, floorUp, eye);
        }
    }

    /** How the vehicle the viewer sits in lies, on its springs or in the air, as the last note had it. Logic thread. */
    VehicleSlope lie() {
        return slope;
    }

    /** The way the vehicle faces on the map, as a yaw. The game gives its forward vector with y as up. */
    float heading(BaseVehicle vehicle) {
        vehicle.getForwardVector(facing);
        return (float) Math.atan2(facing.z, facing.x);
    }

    /**
     * Whether the game lets someone seated attack from where they sit: the window in the door of
     * their seat is open, broken or gone (IsoPlayer.updateWhileInVehicle), or whole and the
     * player shoots through it (shootsThroughGlass). Then the aim input raises the weapon and
     * the attack button uses it, out of the window or any other way. Otherwise the aim input
     * only turns the head (isLookingWhileInVehicle).
     */
    static boolean armedFromSeat(IsoPlayer player) {
        BaseVehicle vehicle = player.getVehicle();
        if (vehicle == null) {
            return false;
        }
        VehiclePart door = vehicle.getPassengerDoor(vehicle.getSeat(player));
        VehicleWindow window = door == null ? null : door.findWindow();
        return window != null && (!window.isHittable() || shootsThroughGlass(player));
    }

    /**
     * Whether a player of this machine in first person, seated with a firearm in hand, may fire
     * with the window of their seat whole: the shot breaks the glass it goes through
     * (shotFromSeat). Where players can fight the game's own rule stands, and the window has to
     * be opened or broken first.
     */
    static boolean shootsThroughGlass(IsoPlayer player) {
        View view = View.of(player);
        return player.isLocalPlayer() && view != null && view.firstPerson.active() && player.isAimingFirearmEquipped() && !Online.contested();
    }

    /**
     * A player of this machine fired from their seat the way (wayX, wayY) on the map: breaks the
     * vehicle's glass the shot leaves through, the windscreen, the rear screen or the window of
     * the door of the seat nearest where it leaves the side, as the game breaks a window
     * (VehicleWindow.damage, which a client asks the server for). Only on the terms a shot may go
     * through whole glass at all (shootsThroughGlass). Logic thread.
     */
    static void shotFromSeat(IsoPlayer player, float wayX, float wayY) {
        BaseVehicle vehicle = player.getVehicle();
        if (vehicle == null || !shootsThroughGlass(player)) {
            return;
        }
        Vector3f from = vehicle.getLocalPos(player.getX(), player.getY(), vehicle.getZ(), new Vector3f());
        Vector3f to = vehicle.getLocalPos(player.getX() + wayX, player.getY() + wayY, vehicle.getZ(), new Vector3f());
        Vector3f extents = vehicle.getScript().getExtents();
        float[] along = new float[1];
        int side = GlassExit.leaves(from.x, from.z, to.x - from.x, to.z - from.z, extents.x / 2f, extents.z / 2f, along);
        if (side == GlassExit.NONE) {
            return;
        }
        VehicleWindow window;
        if (side == GlassExit.PLUS_Z || side == GlassExit.MINUS_Z) {
            Vector3f forward = vehicle.getForwardVector(new Vector3f());
            Vector3f ahead = vehicle.getLocalPos(vehicle.getX() + forward.x, vehicle.getY() + forward.z, vehicle.getZ(), new Vector3f());
            VehiclePart screen = vehicle.getPartById((side == GlassExit.PLUS_Z) == (ahead.z > 0f) ? "Windshield" : "WindshieldRear");
            window = screen == null ? null : screen.getWindow();
        } else {
            window = null;
            float nearest = Float.MAX_VALUE;
            Vector3f seat = new Vector3f();
            for (int s = 0; s < vehicle.getMaxPassengers(); s++) {
                VehiclePart door = vehicle.getPassengerDoor(s);
                VehicleWindow glass = door == null ? null : door.findWindow();
                if (glass == null) {
                    continue;
                }
                vehicle.getPassengerWorldPos(s, seat);
                Vector3f local = vehicle.getLocalPos(seat.x, seat.y, vehicle.getZ(), new Vector3f());
                if ((local.x > 0f) == (side == GlassExit.PLUS_X) && Math.abs(local.z - along[0]) < nearest) {
                    nearest = Math.abs(local.z - along[0]);
                    window = glass;
                }
            }
        }
        if (window == null || !window.isHittable()) {
            return;
        }
        window.damage(window.getHealth());
        // A client asks the server, which breaks the glass and sends it back.
        Log.info(String.format(Locale.ROOT, GameClient.client ? "a shot from the seat through the %s of %s: the server asked to break it"
                : "a shot from the seat broke the %s of %s", window.getPart().getId(), vehicle.getScriptName()));
    }

    /**
     * A driver's hands are on the wheel, so a light carried in one is switched off as they take
     * the seat, as the game's own toggle does it. They can switch it on again.
     */
    private static void putOutHandLights(IsoPlayer player) {
        for (InventoryItem held : new InventoryItem[] {player.getPrimaryHandItem(), player.getSecondaryHandItem()}) {
            if (held != null && !(held instanceof HandWeapon) && held.canBeActivated() && held.isActivated() && held.getLightDistance() > 0) {
                held.setActivated(false);
                LuaManager.GlobalObject.syncItemActivated(player, held);
                held.playActivateDeactivateSound();
                Log.info("a " + held.getFullType() + " in hand switched off as the player took the driver's seat");
            }
        }
    }

    /**
     * Whether a player of this machine in first person, driving by keyboard, keeps steering,
     * accelerating and braking with a firearm raised. The game takes the controls from a driver
     * aiming one (IsoPlayer.updateWhileInVehicle). On a pad the triggers that aim and fire also
     * brake and accelerate (CarController.updateControls reads the pad wherever getJoypad is
     * not -1, player 1 included), so a pad keeps the game's rule, and so does a session where
     * players can fight. Reloading takes the controls whatever this says.
     */
    static boolean drivesWhileAiming(IsoPlayer player) {
        BaseVehicle vehicle = player.getVehicle();
        View view = View.of(player);
        return vehicle != null && vehicle.getJoypad() == -1 && player.isLocalPlayer() && view != null && view.firstPerson.active()
                && !Online.contested();
    }

    /** Whether the player sits in the driver's seat of a vehicle. */
    static boolean inDriversSeat(IsoPlayer player) {
        BaseVehicle vehicle = player.getVehicle();
        return vehicle != null && vehicle.getSeat(player) == 0;
    }

    private void seatTheEye(BaseVehicle vehicle, int seat, float floorUp, float[] eye) {
        vehicle.getPassengerWorldPos(seat, seatAt);
        // The game's seat is at the door. The head is further in, straight across
        // the vehicle. Which way the vehicle faces the game gives with y as up.
        vehicle.getForwardVector(facing);
        float along = Math.max((float) Math.hypot(facing.x, facing.z), 1e-3f);
        aheadX = facing.x / along;
        aheadY = facing.z / along;
        float outX = seatAt.x - vehicle.getX(), outY = seatAt.y - vehicle.getY();
        float ahead = outX * aheadX + outY * aheadY;
        float sideX = outX - ahead * aheadX, sideY = outY - ahead * aheadY;
        float side = Math.max((float) Math.hypot(sideX, sideY), 1e-3f);
        eye[0] = seatAt.x - sideX / side * Math.min(HEAD_INBOARD, side);
        eye[1] = seatAt.y - sideY / side * Math.min(HEAD_INBOARD, side);
        eye[2] = floorUp + EYE_HEIGHT_IN_VEHICLE;
        // Where the vehicle's own wheel has been found the driver sits behind it: a
        // pickup's seat is higher than a saloon's and a van's further forward.
        // A passenger sits where the script seats them from the driver. The eye goes
        // with the vehicle however it is thrown.
        float[] axes = stands;
        float[] wheel = wheels.get(vehicle.getScriptName());
        float[] fromDriver = wheel == null ? null : seat == 0 ? DRIVER : Cabins.fromDriver(vehicle.getScript(), seat, wheel[3]);
        if (fromDriver != null) {
            float back = wheel[0] - Cabins.EYE_BEHIND + fromDriver[0], across = wheel[1] + fromDriver[1], raised = wheel[2] + Cabins.EYE_ABOVE;
            // Turned about the centre of mass, as the game's physics turns the body.
            float centre = wheel[4];
            eye[0] = vehicle.getX() + turn[6] * centre + axes[0] * back + axes[3] * across + axes[6] * raised;
            eye[1] = vehicle.getY() + turn[7] * centre + axes[1] * back + axes[4] * across + axes[7] * raised;
            eye[2] = groundUp + turn[8] * centre + axes[2] * back + axes[5] * across + axes[8] * raised;
        }
        if (FirstPerson.TRACE && (seatSaid++ & 127) == 0) {
            Log.info(String.format(Locale.ROOT, "seat %d at (%.2f, %.2f, %.2f) vehicle (%.2f, %.2f) forward (%.2f, %.2f, %.2f) eye (%.2f, %.2f)", seat,
                    seatAt.x, seatAt.y, seatAt.z, vehicle.getX(), vehicle.getY(), facing.x, facing.y, facing.z, eye[0], eye[1]));
        }
    }

    /** A seat a passenger is never inside, on a trailer or a roof, has no place in the cabin and is left out. */
    private float[] seatsOf(VehicleScript script) {
        float[] found = new float[script.getPassengerCount() * 3];
        int seats = 0;
        for (int i = 0; i < script.getPassengerCount(); i++) {
            VehicleScript.Position inside = script.getPassenger(i).getPositionById("inside");
            if (inside != null) {
                found[seats * 3] = inside.getOffset().x;
                found[seats * 3 + 1] = inside.getOffset().y;
                found[seats * 3 + 2] = inside.getOffset().z;
                seats++;
            }
        }
        return Arrays.copyOf(found, seats * 3);
    }
}
