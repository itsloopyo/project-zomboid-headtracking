package com.cameraunlock.zomboid;

import org.joml.Vector3f;
import zombie.iso.IsoWorld;
import zombie.vehicles.BaseVehicle;

/**
 * The headlights that are on near the eye, as beams the picture can light the world with. The
 * game's logic thread gathers them and the render thread reads the last gathering whole.
 */
final class Headlights {

    static final int BEAMS = 2;
    /** For each beam: where its lamps are (x, y, up), the way it shines (x, y, up), and 1 if it is lit. */
    static final int STRIDE = 7;
    /** How far ahead of a vehicle's middle its lamps are, in tiles. */
    private static final float NOSE = 2.3f;
    /** How far above the ground the lamps are. */
    private static final float LAMP_HEIGHT = 0.6f;
    /** Lamps are dipped: the beam meets the road some way ahead. */
    private static final float DIP = 0.045f;
    private static final float REACH = 45f;

    volatile float[] beams = new float[BEAMS * STRIDE];
    private final Vector3f facing = new Vector3f();
    private final VehicleSlope slope = new VehicleSlope();

    /** @param terrain the lie of the land the view the beams are gathered for is drawn on */
    void gather(Terrain terrain, float eyeX, float eyeY) {
        float[] found = new float[BEAMS * STRIDE];
        float nearest = Float.MAX_VALUE, second = Float.MAX_VALUE;
        for (BaseVehicle vehicle : IsoWorld.instance.currentCell.getVehicles()) {
            if (!vehicle.getHeadlightsOn()) {
                continue;
            }
            float toX = vehicle.getX() - eyeX, toY = vehicle.getY() - eyeY;
            float distance = toX * toX + toY * toY;
            if (distance > REACH * REACH) {
                continue;
            }
            vehicle.getForwardVector(facing);
            float along = (float) Math.hypot(facing.x, facing.z);
            int slot = distance < nearest ? 0 : distance < second ? 1 : -1;
            // A vehicle stood on its nose or its tail has no way along the ground to shine.
            if (slot < 0 || along < 1e-3f) {
                continue;
            }
            if (slot == 0) {
                System.arraycopy(found, 0, found, STRIDE, STRIDE);
                second = nearest;
                nearest = distance;
            } else {
                second = distance;
            }
            float wayX = facing.x / along, wayY = facing.z / along;
            Vector3f extents = vehicle.getScript().getExtents();
            slope.lie(terrain, vehicle.getX(), vehicle.getY(), vehicle.getZ(), wayX, wayY, extents.z, extents.x);
            int at = slot * STRIDE;
            // The lamps are on the vehicle's nose, and shine the way it is tipped on the land.
            found[at] = wayX * NOSE;
            found[at + 1] = wayY * NOSE;
            found[at + 2] = LAMP_HEIGHT;
            slope.apply(found, at);
            found[at] += vehicle.getX();
            found[at + 1] += vehicle.getY();
            found[at + 2] += slope.groundUp;
            float length = (float) Math.sqrt(1f + DIP * DIP);
            found[at + 3] = wayX / length;
            found[at + 4] = wayY / length;
            found[at + 5] = -DIP / length;
            slope.apply(found, at + 3);
            found[at + 6] = 1f;
        }
        beams = found;
    }
}
