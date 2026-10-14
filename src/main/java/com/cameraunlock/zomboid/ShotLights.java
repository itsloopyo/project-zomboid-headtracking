package com.cameraunlock.zomboid;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import org.joml.Vector3f;
import zombie.SoundManager;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoLightSwitch;
import zombie.util.list.PZArrayList;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehiclePart;

/**
 * The lights a shot breaks, played alone: the one of a vehicle's four lamps a shot lands on, and
 * any lamp that is a thing of its own (a table or wall lamp, a street lamp) a shot passes through.
 * Each is broken as the game breaks one. A vehicle's lamp is damaged to nothing and its bulb taken
 * out, as a crash leaves it (BaseVehicle.damageHeadlight). A lamp's bulb is gone, as when it is
 * taken out by hand, so it stays dark until another is fitted. A room's own lights, which the game
 * shows no fitting for, have nothing to aim at and are left alone.
 */
final class ShotLights {
    private ShotLights() {}

    static final String[] VEHICLE_LAMPS = {"HeadlightLeft", "HeadlightRight", "HeadlightRearLeft", "HeadlightRearRight"};
    /**
     * How far across from a vehicle's middle its lamps are, as a share of half its width. The game
     * keeps only which side and which end each lamp is on (VehicleLight.offset, the same 0.5 and 2.0
     * for nearly every body), and places its light from that and the body's size.
     */
    static final float LAMP_ACROSS = 0.75f;
    /** How near a lamp's place a shot must land on the body to break it, in tiles. */
    static final float LAMP_REACH = 0.3f;
    /** How near the middle of a lamp's square a shot must pass to break it, in tiles. */
    static final float LAMP_NEAR = 0.35f;
    /** How far apart along a shot its squares are looked at, in tiles. */
    private static final float STEP = 0.2f;
    private static final String BREAK_SOUND = "BreakGlassItem";
    private static final float SOUND_REACH = 20f;
    /** The vehicle lamps a shot has broken since the game started. A vehicle unloaded takes its own with it. Logic thread. */
    private static final Set<VehiclePart> shotOut = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * Whether a shot that lands at (hitX, hitZ) in a vehicle's own frame, across and along it, is on
     * the lamp on the side and at the end the signs say, at a body half as wide and half as long as
     * given.
     */
    static boolean onLamp(float hitX, float hitZ, float halfWide, float halfLong, float side, float end) {
        return Math.hypot(hitX - side * LAMP_ACROSS * halfWide, hitZ - end * halfLong) <= LAMP_REACH;
    }

    /** Whether a shot along a line through (fromX, fromY) the way (wayX, wayY) passes near enough the middle of square (x, y). */
    static boolean passesNear(float fromX, float fromY, float wayX, float wayY, int x, int y) {
        float flat = (float) Math.hypot(wayX, wayY);
        if (flat < 1e-4f) {
            return true;
        }
        float toX = x + 0.5f - fromX, toY = y + 0.5f - fromY;
        return Math.abs(toX * wayY - toY * wayX) / flat <= LAMP_NEAR;
    }

    /** A shot landed on vehicle at (x, y) on the map. Logic thread. */
    static void vehicleHit(BaseVehicle vehicle, float x, float y) {
        if (Online.joined()) {
            return;
        }
        Vector3f local = vehicle.getLocalPos(x, y, vehicle.getZ(), new Vector3f());
        Vector3f extents = vehicle.getScript().getExtents();
        for (String id : VEHICLE_LAMPS) {
            VehiclePart part = vehicle.getPartById(id);
            if (part == null || part.getLight() == null || part.getInventoryItem() == null) {
                continue;
            }
            Vector3f offset = part.getLight().offset;
            if (onLamp(local.x, local.z, extents.x / 2f, extents.z / 2f, Math.signum(offset.x), Math.signum(offset.y))) {
                part.damage(part.getCondition());
                part.setInventoryItem(null);
                vehicle.transmitPartItem(part);
                shotOut.add(part);
                vehicle.playSound(BREAK_SOUND);
                Log.info(String.format(Locale.ROOT, "a shot broke the %s of vehicle %s, landing at (%.2f, %.2f) in its frame", id, vehicle.getScriptName(), local.x, local.z));
                return;
            }
        }
    }

    /** Whether a vehicle lamp is out because a shot broke it, and has not had a bulb fitted since. Logic thread. */
    static boolean isShotOut(VehiclePart lamp) {
        if (lamp.getInventoryItem() != null) {
            shotOut.remove(lamp);
            return false;
        }
        return shotOut.contains(lamp);
    }

    /**
     * A shot along line, from its eye as far as distance, in the picture's measure: breaks each lamp
     * whose square it passes through near the middle, at the lamp's own level. Logic thread.
     */
    static void along(IsoCell cell, Terrain terrain, ViewCamera.Pose line, float distance) {
        if (Online.joined()) {
            return;
        }
        IsoGridSquare last = null;
        for (float s = 0f; s <= distance; s += STEP) {
            float x = line.eyeX + line.forwardX * s, y = line.eyeY + line.forwardY * s, up = line.eyeUp + line.forwardUp * s;
            int level = (int) Math.floor((up - terrain.height(x, y)) / ViewCamera.LEVEL_HEIGHT);
            IsoGridSquare square = level < 0 ? null : cell.getGridSquare((int) Math.floor(x), (int) Math.floor(y), level);
            if (square == null || square == last) {
                continue;
            }
            last = square;
            if (!passesNear(line.eyeX, line.eyeY, line.forwardX, line.forwardY, square.getX(), square.getY())) {
                continue;
            }
            PZArrayList<IsoObject> objects = square.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                if (objects.get(i) instanceof IsoLightSwitch lamp && !lamp.lightRoom && lamp.hasLightBulb()) {
                    lamp.setBulbItemRaw(null);
                    lamp.setActive(false, false, true);
                    SoundManager.instance.PlayWorldSound(BREAK_SOUND, square, 0f, SOUND_REACH, 1f, false);
                    Log.info(String.format(Locale.ROOT, "a shot broke the lamp at (%d, %d, %d)", square.getX(), square.getY(), level));
                }
            }
        }
    }
}
