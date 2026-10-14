package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Locale;
import org.joml.Vector3f;
import zombie.core.properties.IsoPropertyType;
import zombie.core.properties.PropertyContainer;
import zombie.iso.BentFences;
import zombie.iso.BrokenFences;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;
import zombie.scripting.objects.VehicleScript;
import zombie.vehicles.BaseVehicle;

/**
 * A fence or a lamp post a vehicle runs into faster than a walk, played alone, is knocked down
 * by the game's own means and the vehicle drives on. See Knockdown for the rules.
 *
 * The game meets a thing its tiles mark as hit by cars only once the middle of the thing's
 * square is inside the vehicle (BaseVehicle.breakingObjects, IsoObject.Collision), and its
 * physics stops the vehicle at a fence on the near edge of a square well before that. So each
 * update, before the game looks, this looks a little ahead of the vehicle, the way it is going,
 * for the fences and posts it is about to touch: a fence along the edge it stands on, a post at
 * its middle. The game's own meeting is taken over as well, for a thing this did not see first.
 *
 * A fence the game breaks alone (BrokenFences) is broken as zombies and sledgehammers break
 * it, into its broken sprite and debris with the pieces either side damaged; a fence the game
 * brings down a span at a time (BentFences, the tall chain link) comes down a span; any other
 * fence piece is taken away, as the game takes away what a car flattens. A lamp post is taken
 * away with the street lamp on the level above it, whose light goes with it
 * (IsoLightSwitch.removeFromWorld). What is met at once slows the vehicle once, against the way
 * it goes, by as much as it was met square on, and is one crash of the game's (BaseVehicle.crash)
 * where the game would count that loss of speed as one. On a server the world is the server's,
 * and the game's own rules stand.
 */
final class CarSmash {
    private CarSmash() {}

    /**
     * The game's impulse from a hit plant, against the vehicle's motion, for each share of its
     * speed to be taken off. Measured in a Base.CarNormal of 1118 kg, where an impulse of mass
     * times the speed to lose took off 0.27 of it.
     */
    private static final float IMPULSE_PER_SHARE_LOST = 3.7f;

    /** Where a fence is looked for along its edge, as shares of the edge. */
    private static final float[] ALONG = {0.05f, 0.275f, 0.5f, 0.725f, 0.95f};

    /** Logic thread only. */
    private static final ArrayList<IsoObject> MET = new ArrayList<>();
    private static final ArrayList<Knockdown.Kind> KINDS = new ArrayList<>();
    private static final ArrayList<Float> KEPT = new ArrayList<>();
    private static final Vector3f LOCAL = new Vector3f(), ORIGIN = new Vector3f();

    /** What a vehicle going this fast knocks down of a thing, or null where the game's own rules stand. */
    private static Knockdown.Kind knockable(IsoObject thing) {
        IsoSprite sprite = thing.getSprite();
        PropertyContainer properties = thing.getProperties();
        if (sprite == null || thing.getSquare() == null || properties == null) {
            return null;
        }
        boolean known = properties.has(IsoPropertyType.HIT_BY_CAR) || BrokenFences.getInstance().isBreakableObject(thing)
                || BentFences.getInstance().isBendableFence(thing);
        return Knockdown.of(sprite.getName(), properties.get(IsoPropertyType.MATERIAL), known,
                properties.has(IsoFlagType.collideN) || properties.has(IsoFlagType.collideW));
    }

    private static float kmh(BaseVehicle vehicle) {
        return vehicle.getSpeed2D() * 3.6f;
    }

    /** Whether a vehicle meeting a thing goes through it unslowed: see Knockdown.brushedThrough. */
    static boolean brushedThrough(IsoObject thing) {
        PropertyContainer properties = thing.getProperties();
        return Knockdown.brushedThrough(Online.joined(), properties.has("CarSlowFactor"), properties.has("HitByCar"));
    }

    /** The game hands it each thing marked as hit by cars that a vehicle has met. True where the thing was knocked down and the game's own rule is passed over. */
    static boolean struck(IsoObject thing, BaseVehicle vehicle) {
        if (!Knockdown.smashing(Online.joined(), vehicle.shouldCollideWithObjects(), kmh(vehicle))) {
            return false;
        }
        Knockdown.Kind kind = knockable(thing);
        if (kind == null) {
            return false;
        }
        MET.add(thing);
        KINDS.add(kind);
        vehicle.getLinearVelocity(LOCAL);
        PropertyContainer properties = thing.getProperties();
        KEPT.add(Knockdown.speedKept(kind, Knockdown.squareness(properties.has(IsoFlagType.collideN), properties.has(IsoFlagType.collideW), LOCAL.x, LOCAL.z)));
        knockDown(vehicle, localGoingAlong(vehicle), "met");
        return true;
    }

    /** When a flung vehicle was last said, so a vehicle flung for many updates is said every few seconds and not every update. */
    private static long flungSaidAt;
    private static final long FLUNG_SAID_EVERY_NANOS = 5_000_000_000L;

    /** Knocks down what the vehicle is about to touch, the way it is going, before the game looks for what it has met. */
    static void sweep(BaseVehicle vehicle) {
        float kmh = kmh(vehicle);
        if (Knockdown.flung(kmh) && vehicle.shouldCollideWithObjects()) {
            long now = System.nanoTime();
            if (DevBuild.ON && (flungSaidAt == 0L || now - flungSaidAt >= FLUNG_SAID_EVERY_NANOS)) {
                flungSaidAt = now;
                Log.info(String.format(Locale.ROOT, "a vehicle (%s) at %.4g, %.4g has a speed of %.4g km/h from the game's physics: it is left to the game's own rules",
                        vehicle.getScriptName(), vehicle.getX(), vehicle.getY(), kmh));
            }
        }
        if (!Knockdown.smashing(Online.joined(), vehicle.shouldCollideWithObjects(), kmh)) {
            return;
        }
        VehicleScript script = vehicle.getScript();
        Vector3f extents = script.getExtents(), middle = script.getCenterOfMassOffset();
        float halfAcross = extents.x / 2f, halfAlong = extents.z / 2f, ahead = Knockdown.ahead(vehicle.getSpeed2D());
        vehicle.getLinearVelocity(LOCAL);
        float goingX = LOCAL.x, goingY = LOCAL.z;
        localOf(vehicle, vehicle.getX() + goingX, vehicle.getY() + goingY);
        float goingAcross = LOCAL.x, goingAlong = LOCAL.z;
        int around = (int) Math.ceil(Math.max(halfAcross, halfAlong) + Math.max(Math.abs(middle.x), Math.abs(middle.z)) + ahead + Knockdown.CLEARANCE) + 1;
        int x0 = (int) Math.floor(vehicle.getX()), y0 = (int) Math.floor(vehicle.getY()), z = (int) Math.floor(vehicle.getZ());
        for (int y = y0 - around; y <= y0 + around; y++) {
            for (int x = x0 - around; x <= x0 + around; x++) {
                IsoGridSquare square = IsoWorld.instance.currentCell.getGridSquare(x, y, z);
                if (square == null) {
                    continue;
                }
                for (int i = 0; i < square.getObjects().size(); i++) {
                    IsoObject thing = square.getObjects().get(i);
                    Knockdown.Kind kind = knockable(thing);
                    if (kind == null) {
                        continue;
                    }
                    PropertyContainer properties = thing.getProperties();
                    boolean north = properties.has(IsoFlagType.collideN), west = properties.has(IsoFlagType.collideW);
                    boolean touched = north && touches(vehicle, x, y, true, middle, goingAcross, goingAlong, halfAcross, halfAlong, ahead)
                            || west && touches(vehicle, x, y, false, middle, goingAcross, goingAlong, halfAcross, halfAlong, ahead)
                            || !north && !west && met(vehicle, x + 0.5f, y + 0.5f, middle, goingAcross, goingAlong, halfAcross, halfAlong, ahead);
                    if (touched) {
                        MET.add(thing);
                        KINDS.add(kind);
                        KEPT.add(Knockdown.speedKept(kind, Knockdown.squareness(north, west, goingX, goingY)));
                    }
                }
            }
        }
        if (!MET.isEmpty()) {
            knockDown(vehicle, goingAlong, "ahead");
        }
    }

    /** Whether any point along the edge of the square at (x, y), north or west, is met. */
    private static boolean touches(BaseVehicle vehicle, int x, int y, boolean north, Vector3f middle, float goingAcross, float goingAlong,
            float halfAcross, float halfAlong, float ahead) {
        for (float share : ALONG) {
            if (met(vehicle, Knockdown.edgeX(x, north, share), Knockdown.edgeY(y, north, share), middle, goingAcross, goingAlong, halfAcross, halfAlong, ahead)) {
                return true;
            }
        }
        return false;
    }

    private static boolean met(BaseVehicle vehicle, float x, float y, Vector3f middle, float goingAcross, float goingAlong,
            float halfAcross, float halfAlong, float ahead) {
        localOf(vehicle, x, y);
        return Knockdown.met(LOCAL.x - middle.x, LOCAL.z - middle.z, goingAcross, goingAlong, halfAcross, halfAlong, ahead);
    }

    /**
     * Where a point of the map is across (x) and along (z) the vehicle, in the frame the game
     * finds a point in its box by (testCollisionWithObject), as a difference from where the
     * vehicle itself is in that frame.
     */
    private static void localOf(BaseVehicle vehicle, float x, float y) {
        vehicle.getLocalPos(vehicle.getX(), vehicle.getY(), 0f, ORIGIN);
        vehicle.getLocalPos(x, y, 0f, LOCAL);
        LOCAL.x -= ORIGIN.x;
        LOCAL.y -= ORIGIN.y;
        LOCAL.z -= ORIGIN.z;
    }

    private static float localGoingAlong(BaseVehicle vehicle) {
        vehicle.getLinearVelocity(LOCAL);
        float goingX = LOCAL.x, goingY = LOCAL.z;
        localOf(vehicle, vehicle.getX() + goingX, vehicle.getY() + goingY);
        return LOCAL.z;
    }

    /** Knocks down everything in MET at once: one slowing against the way the vehicle goes, then each thing, then one crash where the game would have one. */
    private static void knockDown(BaseVehicle vehicle, float goingAlong, String how) {
        float kmh = kmh(vehicle);
        float kept = Knockdown.speedKept(KEPT), crash = Knockdown.crash(vehicle.getSpeed2D() * (1f - kept));
        // Taken while the first thing still stands where it was hit: the game takes its place from its square.
        vehicle.applyImpulseFromHitPlant(MET.getFirst(), IMPULSE_PER_SHARE_LOST * (1f - kept));
        BrokenFences broken = BrokenFences.getInstance();
        BentFences bent = BentFences.getInstance();
        for (int i = 0; i < MET.size(); i++) {
            IsoObject thing = MET.get(i);
            Knockdown.Kind kind = KINDS.get(i);
            IsoGridSquare square = thing.getSquare();
            // Breaking one fence piece changes the pieces either side of it, which may have been met too.
            if (square == null || !square.getObjects().contains(thing)) {
                continue;
            }
            String what = thing.getSprite().getName();
            PropertyContainer properties = thing.getProperties();
            // The game's own sound of a vehicle hitting a thing, and the noise of it zombies hear. Nothing of the thing is changed by a hit of no strength.
            thing.HitByVehicle(vehicle, 0f);
            int lamps = 0;
            if (kind == Knockdown.Kind.FENCE && broken.isBreakableObject(thing)) {
                broken.destroyFence(thing, Knockdown.breakTowards(properties.has(IsoFlagType.collideN), vehicle.getX(), vehicle.getY(), thing.getX(), thing.getY()));
            } else if (kind == Knockdown.Kind.FENCE && bent.isBendableFence(thing)) {
                bent.smashFence(thing, Knockdown.breakTowards(properties.has(IsoFlagType.collideN), vehicle.getX(), vehicle.getY(), thing.getX(), thing.getY()));
            } else {
                square.transmitRemoveItemFromSquare(thing);
                if (kind == Knockdown.Kind.POST) {
                    lamps = takeLampAbove(square);
                }
            }
            if (DevBuild.ON) {
                Log.info(String.format(Locale.ROOT, "a vehicle at %.1f km/h knocked down %s at %d,%d,%d, %s%s", kmh, what, square.getX(), square.getY(), square.getZ(),
                        how, lamps > 0 ? ", and the street lamp over it" : ""));
            }
        }
        if (crash > 0f) {
            vehicle.crash(crash, Knockdown.front(goingAlong));
        }
        if (DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "the vehicle keeps %.2f of its speed through %d thing(s), crash %.1f at the %s", kept, MET.size(), crash,
                    Knockdown.front(goingAlong) ? "front" : "back"));
        }
        MET.clear();
        KINDS.clear();
        KEPT.clear();
    }

    /** Takes away the street lamp on the level over a post, and says how many things that was. */
    private static int takeLampAbove(IsoGridSquare post) {
        IsoGridSquare above = IsoWorld.instance.currentCell.getGridSquare(post.getX(), post.getY(), post.getZ() + 1);
        if (above == null) {
            return 0;
        }
        int taken = 0;
        for (int i = above.getObjects().size() - 1; i >= 0; i--) {
            IsoObject each = above.getObjects().get(i);
            IsoSprite sprite = each.getSprite();
            if (sprite != null && Knockdown.streetLamp(sprite.getName(), each.getProperties().get(IsoPropertyType.CUSTOM_NAME))) {
                above.transmitRemoveItemFromSquare(each);
                taken++;
            }
        }
        return taken;
    }
}
