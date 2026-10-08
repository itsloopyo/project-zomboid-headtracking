package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3f;
import zombie.characters.IsoPlayer;
import zombie.scripting.objects.VehicleScript;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehiclePart;

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
    private static final float SPEEDOMETER_FULL = 160f, REV_COUNTER_FULL = 7000f;

    /** Whether the viewer is in a vehicle. */
    volatile boolean seated;
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
    /** The way the viewer's vehicle faces, on the map. */
    volatile float aheadX, aheadY;
    /**
     * The way the viewer's vehicle faces, the side to the left of that and its own up, in the
     * world, as the game's physics has it: nose up or down, on its side or on its roof. The
     * picture reads them on another thread, so a new array is set whole each time.
     */
    volatile float[] stands = {1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};
    /** How fast the viewer's vehicle is going on the map, as the game's physics has it. */
    volatile float goingX, goingY;

    /**
     * For each vehicle script the viewer has sat in: the wheel's middle from where the vehicle
     * stands on the ground, along the vehicle, across it and up it, by its own axes as it was
     * tipped when the picture last drew it for this viewer, in tiles, and then how far across
     * it is from the body's own middle, which says which side of the vehicle the wheel is on
     * however it lies. Written by the picture's thread: see VehicleMeshes.note.
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
        seated = vehicle != null;
        instrumentsLit = vehicle != null && vehicle.isEngineRunning();
        if (vehicle == null) {
            return;
        }
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
        vehicle.getForwardVector(facing);
        vehicle.getUpVector(above);
        float nose = (float) Math.sqrt(facing.x * facing.x + facing.y * facing.y + facing.z * facing.z);
        float high = (float) Math.sqrt(above.x * above.x + above.y * above.y + above.z * above.z);
        float[] axes = new float[9];
        Cabins.axes(facing.x / nose, facing.z / nose, facing.y / nose, above.x / high, above.z / high, above.y / high, axes);
        stands = axes;
        vehicle.getLinearVelocity(going);
        goingX = going.x;
        goingY = going.z;

        int seat = vehicle.getSeat(player);
        driving = seat == 0;
        doorOpen = seat >= 0 && Cabins.doorOpen(vehicle, seat);
        skin = player.getHumanVisual().getSkinTextureIndex();
        // What is worn changes seldom, and asking makes a list for every garment.
        if (driving && (wearAsked++ & 31) == 0) {
            Cabins.noteWear(player, dress);
        }
        if (seat >= 0) {
            seatTheEye(vehicle, seat, floorUp, terrain, eye);
        }
    }

    /** The way the vehicle faces on the map, as a yaw. The game gives its forward vector with y as up. */
    float heading(BaseVehicle vehicle) {
        vehicle.getForwardVector(facing);
        return (float) Math.atan2(facing.z, facing.x);
    }

    private void seatTheEye(BaseVehicle vehicle, int seat, float floorUp, Terrain terrain, float[] eye) {
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
            eye[0] = vehicle.getX() + axes[0] * back + axes[3] * across + axes[6] * raised;
            eye[1] = vehicle.getY() + axes[1] * back + axes[4] * across + axes[7] * raised;
            eye[2] = vehicle.getZ() * ViewCamera.LEVEL_HEIGHT + terrain.lift(vehicle.getX(), vehicle.getY(), vehicle.getZ()) + axes[2] * back + axes[5] * across
                    + axes[8] * raised;
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
