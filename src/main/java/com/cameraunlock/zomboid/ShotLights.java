package com.cameraunlock.zomboid;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import org.joml.Vector3f;
import zombie.SoundManager;
import zombie.core.properties.IsoPropertyType;
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
 *
 * A street lamp is broken where the picture draws its light, which is not always over its own
 * square: a road lamp's head is out on its arm over the next one, and the light of a lamp in one
 * piece is up in the level over its foot. The game will not switch one off (IsoLightSwitch.setActive
 * leaves a light out of doors that a player cannot take down as it is), so every lamp is switched
 * off and its light put out as setActive does it for the rest (setActivated, switchLight).
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
    /** How near the middle of a street lamp's light a shot must pass to break it, in tiles. */
    static final float HEAD_NEAR = 0.35f;
    /** How many squares to each side of where a shot passes a street lamp's light can be, out on its arm. */
    private static final int HEAD_OUT = 1;
    /** The levels a street lamp is on: a lamp in one piece on the ground, or the head of a taller one on the level over it. */
    private static final int LAMP_LEVELS = 2;
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
        if (!Rules.breaksLamps()) {
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
                if (DevBuild.ON) {
                    Log.info(String.format(Locale.ROOT, "a shot broke the %s of vehicle %s, landing at (%.2f, %.2f) in its frame", id, vehicle.getScriptName(), local.x, local.z));
                }
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
     * Where the lights of a street lamp are, as the picture stands its model (Furniture.streetLamp):
     * for each light, how far east and south of the middle of the lamp's square it is and how high
     * over the floor of its level, in tiles, three numbers a light. Null for a sprite of the game's
     * sheet of street lamps (by its place on the sheet) that is no lamp the picture models.
     */
    static float[] lightsOf(int which) {
        float post = Furniture.LAMP_POST_AXIS - 0.5f;
        return switch (which) {
            // A lamp in one piece: the band of light under a cone's lid, a globe, a lantern.
            case 0, 2 -> new float[] {0f, 0f, 2.62f};
            case 1 -> new float[] {0f, 0f, 2.56f};
            case 3 -> new float[] {0f, 0f, 2.49f};
            // An acorn lantern on its post, and two of them on arms out along x, or along y.
            case 5 -> new float[] {post, post, ACORN_UP};
            case 6 -> new float[] {post - TWIN_ACORN_OUT, post, ACORN_UP, post + TWIN_ACORN_OUT, post, ACORN_UP};
            case 7 -> new float[] {post, post - TWIN_ACORN_OUT, ACORN_UP, post, post + TWIN_ACORN_OUT, ACORN_UP};
            // A road lamp's lens at the end of its arm, out north, east, south and west.
            case 8 -> new float[] {0f, -COBRA_OUT, COBRA_UP};
            case 9 -> new float[] {COBRA_OUT, 0f, COBRA_UP};
            case 10 -> new float[] {0f, COBRA_OUT, COBRA_UP};
            case 11 -> new float[] {-COBRA_OUT, 0f, COBRA_UP};
            // A car park lamp's two flat heads, side by side along y, or along x.
            case 18 -> new float[] {0f, -TWIN_OUT, TWIN_UP, 0f, TWIN_OUT, TWIN_UP};
            case 19 -> new float[] {-TWIN_OUT, 0f, TWIN_UP, TWIN_OUT, 0f, TWIN_UP};
            default -> null;
        };
    }

    /** Where the lights of the street lamp models are on them, in tiles: see tools/models/furniture.py. */
    private static final float ACORN_UP = 2.06f, TWIN_ACORN_OUT = 0.55f, COBRA_OUT = 0.8f, COBRA_UP = 2.29f, TWIN_OUT = 0.5f, TWIN_UP = 1.78f;

    private static final String STREET_LAMPS = "lighting_outdoor_01_", LIT_STREET_LAMPS = STREET_LAMPS + "on_";

    /** Which street lamp of the game's sheet a sprite is, by its place on the sheet, lit or not; -1 for anything else. */
    static int streetLampOf(String sprite, String customName) {
        if (!Knockdown.streetLamp(sprite, customName)) {
            return -1;
        }
        return Sheets.place(sprite, sprite.startsWith(LIT_STREET_LAMPS) ? LIT_STREET_LAMPS : STREET_LAMPS);
    }

    /**
     * Whether a shot from (eyeX, eyeY, eyeUp) the unit way (wayX, wayY, wayUp), as far as distance,
     * passes within HEAD_NEAR of the point (x, y, up). The shot stops on the first thing it meets,
     * which may be the outside of the lamp itself, so a light that far beyond is still reached.
     */
    static boolean passesBy(float eyeX, float eyeY, float eyeUp, float wayX, float wayY, float wayUp, float distance, float x, float y, float up) {
        float toX = x - eyeX, toY = y - eyeY, toUp = up - eyeUp;
        float along = toX * wayX + toY * wayY + toUp * wayUp;
        if (along < 0f || along > distance + HEAD_NEAR) {
            return false;
        }
        float offX = toX - wayX * along, offY = toY - wayY * along, offUp = toUp - wayUp * along;
        return offX * offX + offY * offY + offUp * offUp <= HEAD_NEAR * HEAD_NEAR;
    }

    /**
     * A shot along line, from its eye as far as distance, in the picture's measure: breaks each
     * street lamp whose light it passes, and each other lamp whose square it passes through near
     * the middle, at the lamp's own level. Logic thread.
     */
    static void along(IsoCell cell, Terrain terrain, ViewCamera.Pose line, float distance) {
        if (!Rules.breaksLamps()) {
            return;
        }
        IsoGridSquare last = null;
        int lastColumn = Integer.MIN_VALUE, lastRow = Integer.MIN_VALUE;
        for (float s = 0f; s <= distance; s += STEP) {
            float x = line.eyeX + line.forwardX * s, y = line.eyeY + line.forwardY * s, up = line.eyeUp + line.forwardUp * s;
            int column = (int) Math.floor(x), row = (int) Math.floor(y);
            if (column != lastColumn || row != lastRow) {
                lastColumn = column;
                lastRow = row;
                streetLampsRound(cell, terrain, line, distance, column, row);
            }
            int level = (int) Math.floor((up - terrain.height(x, y)) / ViewCamera.LEVEL_HEIGHT);
            IsoGridSquare square = level < 0 ? null : cell.getGridSquare(column, row, level);
            if (square == null || square == last) {
                continue;
            }
            last = square;
            if (!passesNear(line.eyeX, line.eyeY, line.forwardX, line.forwardY, square.getX(), square.getY())) {
                continue;
            }
            PZArrayList<IsoObject> objects = square.getObjects();
            for (int i = 0; i < objects.size(); i++) {
                if (objects.get(i) instanceof IsoLightSwitch lamp && !lamp.lightRoom && lamp.hasLightBulb() && streetLampOf(lamp) < 0) {
                    breakLamp(lamp, square, "");
                }
            }
        }
    }

    /** Breaks each street lamp on the squares round (column, row) whose light the shot passes. */
    private static void streetLampsRound(IsoCell cell, Terrain terrain, ViewCamera.Pose line, float distance, int column, int row) {
        for (int x = column - HEAD_OUT; x <= column + HEAD_OUT; x++) {
            for (int y = row - HEAD_OUT; y <= row + HEAD_OUT; y++) {
                for (int z = 0; z < LAMP_LEVELS; z++) {
                    IsoGridSquare square = cell.getGridSquare(x, y, z);
                    if (square == null) {
                        continue;
                    }
                    PZArrayList<IsoObject> objects = square.getObjects();
                    for (int i = 0; i < objects.size(); i++) {
                        if (!(objects.get(i) instanceof IsoLightSwitch lamp) || !lamp.hasLightBulb()) {
                            continue;
                        }
                        float[] lights = lightsOf(streetLampOf(lamp));
                        if (lights == null) {
                            continue;
                        }
                        float floor = z * ViewCamera.LEVEL_HEIGHT + terrain.height(x + 0.5f, y + 0.5f);
                        for (int k = 0; k < lights.length; k += 3) {
                            if (passesBy(line.eyeX, line.eyeY, line.eyeUp, line.forwardX, line.forwardY, line.forwardUp, distance,
                                    x + 0.5f + lights[k], y + 0.5f + lights[k + 1], floor + lights[k + 2])) {
                                breakLamp(lamp, square, " street");
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    static int streetLampOf(IsoLightSwitch lamp) {
        return lamp.getSprite() == null ? -1 : streetLampOf(lamp.getSprite().getName(), lamp.getProperties().get(IsoPropertyType.CUSTOM_NAME));
    }

    private static void breakLamp(IsoLightSwitch lamp, IsoGridSquare square, String kind) {
        lamp.setBulbItemRaw(null);
        lamp.setActivated(false);
        lamp.switchLight(false);
        SoundManager.instance.PlayWorldSound(BREAK_SOUND, square, 0f, SOUND_REACH, 1f, false);
        if (DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "a shot broke the%s lamp at (%d, %d, %d)", kind, square.getX(), square.getY(), square.getZ()));
        }
    }
}
