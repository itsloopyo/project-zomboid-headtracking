package com.cameraunlock.zomboid;

import java.util.ArrayList;
import zombie.characterTextures.BloodBodyPartType;
import zombie.characters.IsoPlayer;
import zombie.characters.WornItems.WornItems;
import zombie.core.ImmutableColor;
import zombie.core.skinnedmodel.population.ClothingItem;
import zombie.core.skinnedmodel.visual.HumanVisual;
import zombie.core.skinnedmodel.visual.ItemVisual;
import zombie.core.textures.Texture;
import zombie.inventory.types.Clothing;
import zombie.scripting.objects.VehicleScript;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehiclePart;

/**
 * The driver's place the picture builds in a vehicle, in tiles along the vehicle, across it
 * and up.
 */
final class Cabins {
    private Cabins() {}

    /** The place is laid out at one and a half times the size a vehicle is drawn at. */
    static final float SCALE = 2f / 3f;

    /**
     * The wheel's middle from the eye, its radius, the way its column runs, away from the
     * driver, and where the mirror on the screen hangs.
     */
    private static final float[] PLACE = {0.531f, 0f, -0.447f, 0.231f, 0.943f, 0f, -0.332f, 0.46f, 0.34f, 0.07f};

    /** Where the eye of a driver is from the middle of the wheel: behind it and above. */
    static final float EYE_BEHIND = PLACE[0] * SCALE, EYE_ABOVE = -PLACE[2] * SCALE;

    /**
     * The driver's place in the vehicle a viewer sits in or last sat in, or null where the
     * vehicle's own steering wheel has not been found: the place is built on the game's wheel,
     * and without one there is nowhere to build it.
     */
    static float[] wheel(Ride ride) {
        String script = ride.seatedIn;
        return script == null || !ride.wheels.containsKey(script) ? null : PLACE;
    }

    /**
     * What each family of vehicles has at the driver's place. A row is: the wheel's size
     * against the standard, its rim's thickness, its spokes (2 a bar, 3 a T, 4 two low pairs,
     * 5 a cross, 6 three thin metal ones) and their thickness; the hub's half-width, half-height
     * and roundness; the instruments (0 a strip, 1 two dials, 2 a large dial between two small,
     * 3 a row, 5 one dial and a small one, 6 a lit panel of figures and bars), their radius and number; the hood's half-width and
     * half-height, none for a bare plate; the colours of the dash and of the seats; the colour
     * the instruments light up in; the gear lever (1 on the column, 2 on the floor); the wood
     * on the dash (none, half the height of a band of it, or 1 for the whole face); its air
     * vents (1 round, 2 slatted rectangles); what the seats are covered in (0 plain, 1 ribs
     * down them, 2 pleats across them, 3 plaid, 4 two fine stripes); what stands between the
     * seats (1 a console with the lever's boot, 2 the boot alone, 3 a van's engine cover,
     * 4 the cover and a post up the middle of the screen); and what is set in the dash
     * (1 a radio, 2 a glove box, 3 both).
     * A script belongs to the family whose name it starts with, the longest such name winning:
     * the liveries of one body share its script's stem.
     */
    private static final String[] FAMILIES = {
        "Base.CarNormal", "Base.CarLights", "Base.CarTaxi", "Base.CarLuxury", "Base.CarStationWagon", "Base.ModernCar02", "Base.ModernCar", "Base.OffRoad",
        "Base.PickUpTruck", "Base.PickUpVan", "Base.RaceCar", "Base.SmallCar02", "Base.SmallCar", "Base.SportsCar", "Base.StepVan", "Base.SUV", "Base.Van",
    };
    private static final float[] SALOON = {1.05f, 0.017f, 2, 0.024f, 0.085f, 0.04f, 0.02f, 0, 0.05f, 1, 0.21f, 0.05f,
        0.23f, 0.27f, 0.33f, 0.30f, 0.35f, 0.42f, 0.6f, 1f, 0.75f, 1, 0.022f, 2, 1, 0, 3};
    private static final float[][] KITS = {
        SALOON,
        {1.05f, 0.017f, 2, 0.024f, 0.085f, 0.04f, 0.02f, 0, 0.05f, 1, 0.21f, 0.05f, 0.12f, 0.12f, 0.125f, 0.13f, 0.13f, 0.14f, 0.6f, 1f, 0.75f, 1, 0, 2, 0, 0, 3},
        {1.05f, 0.017f, 2, 0.024f, 0.085f, 0.04f, 0.02f, 0, 0.05f, 1, 0.21f, 0.05f, 0.42f, 0.34f, 0.24f, 0.44f, 0.36f, 0.26f, 0.6f, 1f, 0.75f, 1, 0, 2, 0, 0, 3},
        {1.08f, 0.022f, 4, 0.02f, 0.10f, 0.07f, 0.03f, 2, 0.06f, 3, 0.21f, 0.075f, 0.12f, 0.11f, 0.10f, 0.55f, 0.40f, 0.24f, 1f, 0.7f, 0.3f, 2, 0.03f, 1, 2, 1, 3},
        {1.05f, 0.017f, 2, 0.024f, 0.085f, 0.04f, 0.02f, 0, 0.05f, 1, 0.21f, 0.05f, 0.40f, 0.30f, 0.20f, 0.45f, 0.36f, 0.25f, 0.6f, 1f, 0.75f, 1, 1, 2, 3, 0, 3},
        {1.0f, 0.022f, 4, 0.02f, 0.10f, 0.05f, 0.02f, 6, 0.058f, 3, 0.22f, 0.072f, 0.14f, 0.17f, 0.24f, 0.18f, 0.23f, 0.34f, 0.5f, 0.9f, 1f, 1, 0, 2, 1, 0, 3},
        {0.97f, 0.024f, 5, 0.018f, 0.09f, 0.075f, 0.02f, 6, 0.052f, 2, 0.2f, 0.066f, 0.30f, 0.30f, 0.31f, 0.36f, 0.36f, 0.38f, 0.5f, 1f, 0.9f, 2, 0, 2, 0, 1, 3},
        {1.0f, 0.016f, 6, 0.011f, 0.035f, 0.035f, 0.035f, 3, 0.034f, 5, 0.22f, 0f, 0.25f, 0.27f, 0.19f, 0.12f, 0.12f, 0.12f, 1f, 1f, 0.95f, 2, 0, 0, 0, 2, 0},
        {1.10f, 0.018f, 2, 0.022f, 0.07f, 0.045f, 0.02f, 1, 0.055f, 2, 0.2f, 0.07f, 0.36f, 0.26f, 0.17f, 0.42f, 0.31f, 0.20f, 0.6f, 1f, 0.75f, 1, 0, 2, 2, 0, 3},
        {1.12f, 0.016f, 3, 0.014f, 0.04f, 0.04f, 0.04f, 3, 0.031f, 6, 0.24f, 0.05f, 0.10f, 0.10f, 0.10f, 0.10f, 0.12f, 0.20f, 1f, 0.75f, 0.4f, 2, 0, 0, 0, 2, 3},
        {0.92f, 0.022f, 6, 0.012f, 0.035f, 0.035f, 0.035f, 3, 0.036f, 5, 0.22f, 0f, 0.55f, 0.55f, 0.57f, 0.08f, 0.08f, 0.08f, 1f, 1f, 0.95f, 2, 0, 0, 0, 2, 0},
        {0.96f, 0.019f, 4, 0.02f, 0.06f, 0.06f, 0.012f, 1, 0.05f, 2, 0.18f, 0.07f, 0.10f, 0.10f, 0.10f, 0.38f, 0.38f, 0.40f, 1f, 0.6f, 0.2f, 2, 0, 2, 4, 1, 3},
        {0.95f, 0.015f, 2, 0.02f, 0.05f, 0.04f, 0.012f, 5, 0.058f, 2, 0.15f, 0.075f, 0.45f, 0.37f, 0.26f, 0.45f, 0.36f, 0.25f, 0.6f, 1f, 0.75f, 2, 0, 2, 0, 2, 1},
        {0.93f, 0.026f, 2, 0.032f, 0.08f, 0.06f, 0.03f, 0, 0.05f, 1, 0.19f, 0.06f, 0.10f, 0.10f, 0.10f, 0.45f, 0.10f, 0.10f, 0.3f, 0.9f, 1f, 2, 0, 2, 2, 1, 3},
        {1.30f, 0.016f, 6, 0.011f, 0.035f, 0.035f, 0.035f, 5, 0.055f, 2, 0.14f, 0f, 0.62f, 0.60f, 0.54f, 0.08f, 0.08f, 0.08f, 0.6f, 1f, 0.75f, 2, 0, 0, 0, 4, 0},
        {1.02f, 0.022f, 4, 0.02f, 0.095f, 0.05f, 0.02f, 1, 0.055f, 2, 0.2f, 0.072f, 0.33f, 0.29f, 0.25f, 0.45f, 0.40f, 0.33f, 0.5f, 0.95f, 0.9f, 1, 0, 2, 0, 0, 3},
        {1.12f, 0.018f, 2, 0.022f, 0.08f, 0.04f, 0.02f, 1, 0.055f, 2, 0.2f, 0.07f, 0.25f, 0.24f, 0.22f, 0.45f, 0.38f, 0.28f, 0.6f, 1f, 0.75f, 1, 0, 2, 0, 3, 3},
    };

    /**
     * The glasshouse the picture builds over each family's driver, in the families' order: how
     * steeply the windscreen stands, in degrees from level; how far above the driver's eye
     * the rail over the screen is; and how thick the pillars are. The game's bodies run their
     * screens back over the driver's head to a roof that begins behind it, which from the
     * seat is no cabin at all, so only the foot of the screen is taken from the body.
     */
    private static final float[][] HOUSES = {
        {42f, 0.20f, 0.034f},
        {42f, 0.20f, 0.034f},
        {42f, 0.20f, 0.034f},
        {40f, 0.20f, 0.037f},
        {44f, 0.21f, 0.034f},
        {34f, 0.19f, 0.038f},
        {33f, 0.19f, 0.038f},
        {62f, 0.25f, 0.028f},
        {52f, 0.24f, 0.032f},
        {52f, 0.24f, 0.032f},
        {34f, 0.16f, 0.028f},
        {44f, 0.20f, 0.032f},
        {46f, 0.20f, 0.03f},
        {30f, 0.14f, 0.034f},
        {80f, 0.48f, 0.03f},
        {50f, 0.27f, 0.036f},
        {58f, 0.33f, 0.034f},
    };

    private static int family(String script) {
        int best = -1;
        for (int i = 0; i < FAMILIES.length; i++) {
            if (script.startsWith(FAMILIES[i]) && (best < 0 || FAMILIES[i].length() > FAMILIES[best].length())) {
                best = i;
            }
        }
        return best;
    }

    /** The driver's place of the family that script belongs to. A script of no family here has the saloon's. */
    static float[] kit(String script) {
        int best = family(script);
        return best < 0 ? SALOON : KITS[best];
    }

    /** The pillar behind the driver's door stands this far along from the eye. */
    static final float PILLAR_BEHIND = -0.2f;

    /**
     * The glasshouse over the driver of that script's vehicle, as the driver's place is laid
     * out: how far along from the eye the rail over the screen is and how far above it; how
     * thick the pillars are; and where the mirror hangs on the screen below the rail, along,
     * across towards the middle of the vehicle and up.
     *
     * @param measures where the body's cabin is from its wheel, as VehicleMeshes.Parts has it
     */
    static float[] house(String script, float[] measures) {
        int best = family(script);
        float[] house = HOUSES[Math.max(best, 0)];
        float footAlong = PLACE[0] + measures[0] / SCALE, footUp = PLACE[2] + measures[1] / SCALE;
        float run = (house[1] - footUp) / (float) Math.tan(Math.toRadians(house[0]));
        float railAlong = footAlong - run;
        // The mirror hangs a hand's breadth down the glass from the rail, clear of it.
        float down = 0.16f / (float) Math.hypot(run, house[1] - footUp);
        return new float[] {railAlong, house[1], house[2], railAlong + run * down - 0.05f, Math.abs(measures[4]) / SCALE * Math.signum(measures[4]),
            house[1] - (house[1] - footUp) * down - 0.01f};
    }

    /** A door mirror's housing stands with its middle this far over the foot of the door's glass, this far out from the glass, and this far behind the screen's pillar at that height. */
    private static final float DOOR_MIRROR_UP = 0.1f, DOOR_MIRROR_OUT = 0.18f, DOOR_MIRROR_BEHIND = 0.04f;

    /**
     * Where the middle of the housing of each door mirror is, as the driver's place is laid
     * out: along from the eye, out from the vehicle's middle line, and up from the eye. It
     * stands on its door at the foot of the screen's pillar the picture builds, on both sides,
     * and not where the body has its own: a body may carry its mirrors on a panel ahead of its
     * door's glass, which the picture's glasshouse has as glass.
     *
     * @param house    the glasshouse, as house gives it
     * @param measures where the body's cabin is from its wheel, as VehicleMeshes.Parts has it
     */
    static float[] doorMirror(float[] house, float[] measures) {
        float belt = PLACE[2] + Math.min(measures[1], measures[2]) / SCALE;
        // The pillar runs as CabinShader stands it: from a little below and behind the foot of
        // the screen, at the glass, up to the rail.
        float footAlong = PLACE[0] + measures[0] / SCALE - 0.02f, footUp = belt - 0.03f;
        float up = belt + DOOR_MIRROR_UP;
        float pillarAlong = footAlong + (house[0] - footAlong) * (up - footUp) / (house[1] - footUp);
        float glass = (Math.abs(measures[4]) + measures[3]) / SCALE;
        return new float[] {pillarAlong - DOOR_MIRROR_BEHIND, glass + DOOR_MIRROR_OUT, up};
    }

    /**
     * Where the head of whoever sits in a seat is from the driver's, in tiles along the
     * vehicle and across it, across counted the way the wheel is from the vehicle's middle.
     * The script counts across one way and the picture the other or the same: the driver,
     * its first seat, is on the side the wheel is. Null for a seat nobody sits inside, on a
     * trailer or a roof.
     *
     * @param wheelAcross how far across from the vehicle's middle its steering wheel is
     */
    static float[] fromDriver(VehicleScript script, int seat, float wheelAcross) {
        VehicleScript.Position driver = script.getPassenger(0).getPositionById("inside");
        VehicleScript.Position place = script.getPassenger(seat).getPositionById("inside");
        if (driver == null || place == null) {
            return null;
        }
        return fromDriver(driver.getOffset().x, driver.getOffset().z, place.getOffset().x, place.getOffset().z, wheelAcross);
    }

    static float[] fromDriver(float driverAcross, float driverAlong, float seatAcross, float seatAlong, float wheelAcross) {
        float way = Math.signum(wheelAcross) * Math.signum(driverAcross);
        return new float[] {seatAlong - driverAlong, (way == 0f ? 1f : way) * (seatAcross - driverAcross)};
    }

    /**
     * How nearly a body's length has to lie along the way its vehicle faces for the body's axes
     * to be taken as the vehicle's. The two are read a frame apart, and in a crash a vehicle
     * turns a long way in a frame: only a body laid across its vehicle is under this.
     */
    private static final float LENGTHWISE = 0.5f;

    /**
     * The way a vehicle faces, the side to the left of that and its own up, in the world, each
     * of length one, from the matrix that stands its body there. A body's own axes are x to
     * its side, y up and z along its length, and the matrix has in it the whole of how the
     * vehicle is turned, tipped and rolled, on its springs or on its side or its roof. Which
     * end of the body is the front is told by faces, the same three as the game's physics
     * has them for the vehicle, nose up or down with it.
     *
     * False, with the physics' own three, for a body whose length does not lie along its
     * vehicle: one its script has turned on it.
     */
    static boolean axes(float[] stands, float[] faces, float[] out) {
        float alongX = stands[8], alongY = stands[9], alongUp = stands[10];
        float length = (float) Math.sqrt(alongX * alongX + alongY * alongY + alongUp * alongUp);
        float agree = (alongX * faces[0] + alongY * faces[1] + alongUp * faces[2]) / length;
        if (Math.abs(agree) < LENGTHWISE) {
            System.arraycopy(faces, 0, out, 0, 9);
            return false;
        }
        float long1 = Math.copySign(1f / length, agree);
        float high1 = 1f / (float) Math.sqrt(stands[4] * stands[4] + stands[5] * stands[5] + stands[6] * stands[6]);
        out[0] = alongX * long1;
        out[1] = alongY * long1;
        out[2] = alongUp * long1;
        out[6] = stands[4] * high1;
        out[7] = stands[5] * high1;
        out[8] = stands[6] * high1;
        out[3] = out[7] * out[2] - out[8] * out[1];
        out[4] = out[8] * out[0] - out[6] * out[2];
        out[5] = out[6] * out[1] - out[7] * out[0];
        return true;
    }

    /**
     * The same three from what the game's physics says of a vehicle: the way it faces and its
     * own up, each of length one, in the world.
     */
    static void axes(float noseX, float noseY, float noseUp, float highX, float highY, float highUp, float[] out) {
        out[0] = noseX;
        out[1] = noseY;
        out[2] = noseUp;
        out[3] = highY * noseUp - highUp * noseY;
        out[4] = highUp * noseX - highX * noseUp;
        out[5] = highX * noseY - highY * noseX;
        out[6] = highX;
        out[7] = highY;
        out[8] = highUp;
    }

    /**
     * How a vehicle with these three axes is turned from standing level on its heading: three
     * rows of three that take a way in the world as it would be with the vehicle level to the
     * way it is with the vehicle as it lies. Nothing is changed for a level vehicle, whichever
     * way it heads.
     *
     * @param aheadX the way the vehicle heads on the map, of length one
     */
    static void tilt(float[] axes, float aheadX, float aheadY, float[] out) {
        for (int row = 0; row < 3; row++) {
            out[row * 3] = axes[row] * aheadX - axes[3 + row] * aheadY;
            out[row * 3 + 1] = axes[row] * aheadY + axes[3 + row] * aheadX;
            out[row * 3 + 2] = axes[6 + row];
        }
    }

    /** The same three for a vehicle standing level, heading this way on the map. */
    static void level(float aheadX, float aheadY, float[] out) {
        out[0] = aheadX;
        out[1] = aheadY;
        out[2] = 0f;
        out[3] = -aheadY;
        out[4] = aheadX;
        out[5] = 0f;
        out[6] = 0f;
        out[7] = 0f;
        out[8] = 1f;
    }

    /**
     * Where the mirror on the screen hangs in the world, into out as x, y and up, for the
     * picture of the road behind to be taken from. It is fixed to the vehicle: it does not
     * move with the head, nor with the seat the viewer is in.
     *
     * @param wheelAt where the middle of the steering wheel is in the world
     * @param house   the glasshouse, as house gives it
     */
    static void mirrorAt(float[] wheelAt, float[] house, float aheadX, float aheadY, float[] out) {
        float along = (house[3] - PLACE[0]) * SCALE, across = (house[4] - PLACE[1]) * SCALE;
        out[0] = wheelAt[0] + aheadX * along - aheadY * across;
        out[1] = wheelAt[1] + aheadY * along + aheadX * across;
        out[2] = wheelAt[2] + (house[5] - PLACE[2]) * SCALE;
    }

    /**
     * Whether something is wholly out of the picture a mirror takes of the road behind: nearer
     * than the picture begins, beyond where it has faded into the sky, or to one side of it.
     *
     * @param ahead  how far its middle is from the mirror along the way the mirror looks, in tiles
     * @param across how far to one side of that line
     * @param reach  how far from its middle it reaches along the ground
     * @param tan    half of what the picture takes in from side to side, as a tangent
     */
    static boolean outOfMirror(float ahead, float across, float reach, float tan, float near, float sight) {
        return ahead < near - reach || ahead > sight + reach || Math.abs(across) - tan * ahead > reach * (float) Math.sqrt(1f + tan * tan);
    }

    /**
     * Whether a door of the cabin stands open: the driver's, the passenger's beside the
     * driver, or the viewer's own. The lamp in the roof is lit by them.
     */
    static boolean doorOpen(BaseVehicle vehicle, int seat) {
        return open(vehicle.getPassengerDoor(0)) || open(vehicle.getPassengerDoor(1)) || seat > 1 && open(vehicle.getPassengerDoor(seat));
    }

    private static boolean open(VehiclePart part) {
        return part != null && part.getDoor() != null && part.getDoor().isOpen();
    }

    /** The game's skins, palest first, as the colour of the back of a hand. */
    private static final float[][] SKINS = {{0.74f, 0.57f, 0.47f}, {0.68f, 0.50f, 0.39f}, {0.58f, 0.41f, 0.31f}, {0.44f, 0.30f, 0.22f}, {0.31f, 0.21f, 0.16f}};
    /** What a garment is taken to be dyed with where the picture of it cannot be read: a middling grey cloth. */
    private static final float[] CLOTH = {0.42f, 0.42f, 0.44f};

    static float[] skin(int index) {
        return SKINS[Math.clamp(index, 0, SKINS.length - 1)];
    }

    /**
     * What covers the viewer's arm or hand: the picture the game shows the garment by in the
     * inventory, whose colours are the garment's own, and the dye the game lays over it.
     */
    record Garment(Texture picture, float red, float green, float blue) {}

    /**
     * What the viewer wears on the arms and the hands. The sleeve is null on a bare arm and
     * the glove null on a bare hand; a short sleeve ends at the elbow.
     */
    record Wear(Garment sleeve, boolean toTheWrist, Garment glove) {}

    /**
     * The places of an arm the game keeps blood and dirt for, the right arm's and then the
     * left's, each from the hand up. A viewer's stains are two numbers for each, 0 to 1: how
     * bloody what shows there is, and how dirty.
     */
    static final int STAINS = 12;

    /** Apart, so that the game's names for them are asked for only once the game is there to give them. */
    private static final class Places {
        static final BloodBodyPartType[] OF_ARMS = {BloodBodyPartType.Hand_R, BloodBodyPartType.ForeArm_R, BloodBodyPartType.UpperArm_R,
            BloodBodyPartType.Hand_L, BloodBodyPartType.ForeArm_L, BloodBodyPartType.UpperArm_L};
    }

    private static final Wear BARE = new Wear(null, false, null);

    /** One viewer's arms and hands: how stained they are and what is worn on them. Set on the logic thread, read by the picture. */
    static final class Dress {
        volatile float[] stains = new float[STAINS];
        volatile Wear worn = BARE;
    }

    /**
     * Notes what a viewer wears on the arms and hands. The game keeps what is worn from the
     * skin outwards, so of the garments over a part the last is the one that shows.
     */
    static void noteWear(IsoPlayer player, Dress dress) {
        WornItems items = player.getWornItems();
        Garment upper = null, fore = null, glove = null;
        // The blood and the dirt on what shows of each arm: on the skin, or on whatever is worn over it there.
        float[] soil = new float[STAINS];
        HumanVisual body = player.getHumanVisual();
        for (int place = 0; place < Places.OF_ARMS.length; place++) {
            soil[place * 2] = body.getBlood(Places.OF_ARMS[place]);
            soil[place * 2 + 1] = body.getDirt(Places.OF_ARMS[place]);
        }
        for (int i = 0; i < items.size(); i++) {
            if (!(items.getItemByIndex(i) instanceof Clothing clothing)) {
                continue;
            }
            ArrayList<BloodBodyPartType> covers = clothing.getCoveredParts();
            boolean hand = covers.contains(BloodBodyPartType.Hand_L);
            boolean lower = covers.contains(BloodBodyPartType.ForeArm_L);
            boolean higher = covers.contains(BloodBodyPartType.UpperArm_L);
            if (!hand && !lower && !higher) {
                continue;
            }
            ItemVisual visual = clothing.getVisual();
            for (int place = 0; visual != null && place < Places.OF_ARMS.length; place++) {
                if (covers.contains(Places.OF_ARMS[place])) {
                    soil[place * 2] = visual.getBlood(Places.OF_ARMS[place]);
                    soil[place * 2 + 1] = visual.getDirt(Places.OF_ARMS[place]);
                }
            }
            // The game's getTint reads the garment's model without asking whether it has one, and a mod's may not.
            ClothingItem model = visual == null ? null : visual.getClothingItem();
            ImmutableColor dye = model == null ? null : visual.getTint(model);
            Garment garment = dye == null ? new Garment(clothing.getTex(), 1f, 1f, 1f) : new Garment(clothing.getTex(), dye.r, dye.g, dye.b);
            glove = hand ? garment : glove;
            fore = lower ? garment : fore;
            upper = higher ? garment : upper;
        }
        dress.stains = soil;
        Wear now = new Wear(fore != null ? fore : upper, fore != null, glove);
        if (!now.equals(dress.worn)) {
            dress.worn = now;
        }
    }

    /**
     * The colour of a garment, into out: the mean of its picture under its dye, or grey cloth
     * under the dye where the picture cannot be read. Render thread.
     */
    static void colour(Garment garment, float[] out) {
        float[] mean = garment.picture() == null ? null : VehicleMeshes.meanOf(garment.picture());
        float[] cloth = mean == null ? CLOTH : mean;
        out[0] = cloth[0] * garment.red();
        out[1] = cloth[1] * garment.green();
        out[2] = cloth[2] * garment.blue();
    }

    /** What a vehicle carries for its trade: nothing, a patrol's radio and light switches, a taxi's meter, an ambulance's console, a racing car's cage. */
    static final float PLAIN = 0f, PATROL = 1f, TAXI = 2f, AMBULANCE = 3f, RACER = 4f;

    static float trade(String script) {
        if (script.startsWith("Base.CarTaxi")) {
            return TAXI;
        }
        if (script.startsWith("Base.VanAmbulance")) {
            return AMBULANCE;
        }
        if (script.startsWith("Base.RaceCar")) {
            return RACER;
        }
        return script.contains("Lights") ? PATROL : PLAIN;
    }

    /**
     * The game's full lock of 0.9 is a sixth of a turn of the steering wheel, 62 degrees. A key
     * winds the lock on in two thirds of a second, so a wheel geared like a real one spins, and
     * the driver's hands hold the rim from straight to lock: at lock the hand on the turn's side
     * has come down from two to four o'clock and the other is at the top.
     */
    private static final float STEERING_RATIO = 1.2f;

    /** How far the steering wheel is turned, in radians, with the vehicle steered that far as the game counts it. */
    static float wheelTurn(float steering) {
        return steering * STEERING_RATIO;
    }

    /**
     * Where a body's cabin is from the middle of its wheel, as VehicleMeshes.Parts has it, for
     * a body not yet measured: the standard car's.
     */
    static final float[] STANDARD_MEASURES = {0.37f, 0.07f, 0.07f, 0.41f, 0.323f, 0.12f, 0.2f};
}
