package com.cameraunlock.zomboid;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;
import org.joml.Vector3f;
import zombie.core.properties.IsoPropertyType;
import zombie.core.properties.PropertyContainer;
import zombie.iso.BrokenFences;
import zombie.iso.IsoDirections;
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
 * update, before the game looks, this looks a little ahead of the vehicle for the fences and
 * posts it is about to touch: a fence along the edge it stands on, a post at its middle. The
 * game's own meeting is taken over as well, for a thing this did not see first.
 *
 * A fence the game knows how to break (BrokenFences) is broken as zombies and sledgehammers
 * break it, into its broken sprite and debris with the pieces either side damaged; any other
 * fence piece is taken away, as the game takes away what a car flattens. A lamp post is taken
 * away with the lamp on the level above it, whose light goes with it
 * (IsoLightSwitch.removeFromWorld). On a server the world is the server's, and the game's own
 * rules stand.
 */
final class CarSmash {
    private CarSmash() {}

    /** How hard each end of a vehicle is hit, private to BaseVehicle and called by name: see Links.REFLECTED_METHODS. */
    private static final Method FRONT = damager(Links.FRONT_DAMAGE), REAR = damager(Links.REAR_DAMAGE);

    /** Where a fence is looked for along its edge, as shares of the edge. */
    private static final float[] ALONG = {0.05f, 0.275f, 0.5f, 0.725f, 0.95f};

    /** Logic thread only. */
    private static final ArrayList<IsoObject> MET = new ArrayList<>();
    private static final Vector3f LOCAL = new Vector3f();

    private static Method damager(Links.Method named) {
        try {
            Method method = BaseVehicle.class.getDeclaredMethod(named.name(), int.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException missing) {
            throw new IllegalStateException(named + " is not in this build of the game, which Links should have found", missing);
        }
    }

    /** What a vehicle going this fast knocks down of a thing, or null where the game's own rules stand. */
    private static Knockdown.Kind knockable(IsoObject thing, float kmh) {
        IsoSprite sprite = thing.getSprite();
        PropertyContainer properties = thing.getProperties();
        if (Online.joined() || !Knockdown.fastEnough(kmh) || sprite == null || thing.getSquare() == null || properties == null
                || !properties.has(IsoPropertyType.HIT_BY_CAR)) {
            return null;
        }
        return Knockdown.of(sprite.getName(), properties.get(IsoPropertyType.MATERIAL));
    }

    /** Called from IsoObject.Collision as it starts. True where the vehicle knocked the thing down and the game's own rule is passed over. */
    static boolean struck(IsoObject thing, BaseVehicle vehicle) {
        Knockdown.Kind kind = knockable(thing, vehicle.getCurrentSpeedKmHour());
        if (kind == null) {
            return false;
        }
        knockDown(thing, vehicle, kind, "met");
        return true;
    }

    /** Called from BaseVehicle.breakingObjects as it starts: knocks down what the vehicle is about to touch. */
    static void sweep(BaseVehicle vehicle) {
        float kmh = vehicle.getCurrentSpeedKmHour();
        if (Online.joined() || !Knockdown.fastEnough(kmh) || !vehicle.shouldCollideWithObjects()) {
            return;
        }
        VehicleScript script = vehicle.getScript();
        Vector3f extents = script.getExtents(), middle = script.getCenterOfMassOffset();
        float halfAcross = extents.x / 2f, halfAlong = extents.z / 2f, ahead = Knockdown.ahead(vehicle.getSpeed2D());
        // Which way along the vehicle's own length it is going: the game's forward is (x, z) of its forward vector on the map.
        vehicle.getForwardVector(LOCAL);
        float forwardX = LOCAL.x, forwardY = LOCAL.z;
        vehicle.getLocalPos(vehicle.getX() + forwardX, vehicle.getY() + forwardY, 0f, LOCAL);
        if (Math.abs(LOCAL.z) < 0.5f) {
            throw new IllegalStateException("a vehicle's length is not along its own z as the game's testCollisionWithObject has it: a tile ahead is " + LOCAL);
        }
        float going = Math.signum(LOCAL.z) * Math.signum(kmh);
        int around = (int) Math.ceil(Math.max(halfAcross, halfAlong) + Math.max(Math.abs(middle.x), Math.abs(middle.z)) + ahead + Knockdown.CLEARANCE) + 1;
        int x0 = (int) Math.floor(vehicle.getX()), y0 = (int) Math.floor(vehicle.getY()), z = (int) Math.floor(vehicle.getZ());
        MET.clear();
        for (int y = y0 - around; y <= y0 + around; y++) {
            for (int x = x0 - around; x <= x0 + around; x++) {
                IsoGridSquare square = IsoWorld.instance.currentCell.getGridSquare(x, y, z);
                if (square == null) {
                    continue;
                }
                for (int i = 0; i < square.getObjects().size(); i++) {
                    IsoObject thing = square.getObjects().get(i);
                    if (knockable(thing, kmh) == null) {
                        continue;
                    }
                    PropertyContainer properties = thing.getProperties();
                    boolean north = properties.has(IsoFlagType.collideN), west = properties.has(IsoFlagType.collideW);
                    boolean touched = north && touches(vehicle, x, y, 1f, 0f, middle, halfAcross, halfAlong, ahead, going)
                            || west && touches(vehicle, x, y, 0f, 1f, middle, halfAcross, halfAlong, ahead, going)
                            || !north && !west && inReach(vehicle, x + 0.5f, y + 0.5f, middle, halfAcross, halfAlong, ahead, going);
                    if (touched) {
                        MET.add(thing);
                    }
                }
            }
        }
        for (IsoObject thing : MET) {
            Knockdown.Kind kind = knockable(thing, kmh);
            // Breaking one fence piece changes the pieces either side of it, which may have been met too.
            if (kind != null && thing.getSquare() != null && thing.getSquare().getObjects().contains(thing)) {
                knockDown(thing, vehicle, kind, "ahead");
            }
        }
        MET.clear();
    }

    /** Whether any point along the edge from (x, y) the given way is within reach of the vehicle. */
    private static boolean touches(BaseVehicle vehicle, int x, int y, float wayX, float wayY, Vector3f middle, float halfAcross, float halfAlong,
            float ahead, float going) {
        for (float share : ALONG) {
            if (inReach(vehicle, x + wayX * share, y + wayY * share, middle, halfAcross, halfAlong, ahead, going)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a point of the map is within reach of the vehicle's box, which the game finds a point in as this does (testCollisionWithObject). */
    private static boolean inReach(BaseVehicle vehicle, float x, float y, Vector3f middle, float halfAcross, float halfAlong, float ahead, float going) {
        vehicle.getLocalPos(x, y, 0f, LOCAL);
        return Knockdown.inReach(LOCAL.x - middle.x, (LOCAL.z - middle.z) * going, halfAcross, halfAlong, ahead);
    }

    private static void knockDown(IsoObject thing, BaseVehicle vehicle, Knockdown.Kind kind, String how) {
        IsoGridSquare square = thing.getSquare();
        PropertyContainer properties = thing.getProperties();
        String what = thing.getSprite().getName();
        float kmh = vehicle.getCurrentSpeedKmHour(), speed = vehicle.getSpeed2D();
        // Pushed back from the thing while it still stands where it was hit: the game takes its place from its square.
        vehicle.applyImpulseFromHitObject(thing, vehicle.getFudgedMass() * speed * (1f - Knockdown.speedKept(kind)));
        // The game's own sound of a vehicle hitting a thing, and the noise of it zombies hear. Nothing of the thing is changed by a hit of no strength.
        thing.HitByVehicle(vehicle, 0f);
        int lamps = 0;
        BrokenFences fences = BrokenFences.getInstance();
        if (kind == Knockdown.Kind.FENCE && fences.isBreakableObject(thing)) {
            fences.destroyFence(thing, awayFrom(vehicle, thing, properties));
        } else {
            square.transmitRemoveItemFromSquare(thing);
            if (kind == Knockdown.Kind.POST) {
                lamps = takeLampsAbove(square);
            }
        }
        damage(kmh > 0f ? FRONT : REAR, vehicle, Knockdown.damage(kind, kmh));
        Log.info(String.format(Locale.ROOT, "a vehicle at %.1f km/h (%.2f) knocked down %s at %d,%d,%d, %s%s", kmh, speed, what, square.getX(), square.getY(), square.getZ(),
                how, lamps > 0 ? ", and " + lamps + " lamp thing(s) over it" : ""));
    }

    /** The way the game itself breaks a fence a vehicle hits: away from the side the vehicle is on. */
    private static IsoDirections awayFrom(BaseVehicle vehicle, IsoObject fence, PropertyContainer properties) {
        if (properties.has(IsoFlagType.collideN)) {
            return vehicle.getY() >= fence.getY() ? IsoDirections.N : IsoDirections.S;
        }
        return vehicle.getX() >= fence.getX() ? IsoDirections.W : IsoDirections.E;
    }

    /** Takes away the street lamp on the level over a post, and says how many things that was. */
    private static int takeLampsAbove(IsoGridSquare post) {
        IsoGridSquare above = IsoWorld.instance.currentCell.getGridSquare(post.getX(), post.getY(), post.getZ() + 1);
        if (above == null) {
            return 0;
        }
        int taken = 0;
        for (int i = above.getObjects().size() - 1; i >= 0; i--) {
            IsoObject each = above.getObjects().get(i);
            IsoSprite sprite = each.getSprite();
            if (sprite != null && sprite.getName() != null && sprite.getName().startsWith("lighting_outdoor_01_")) {
                above.transmitRemoveItemFromSquare(each);
                taken++;
            }
        }
        return taken;
    }

    private static void damage(Method end, BaseVehicle vehicle, int amount) {
        try {
            end.invoke(vehicle, amount);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        } catch (InvocationTargetException thrown) {
            throw new IllegalStateException("the game's " + end.getName() + " threw", thrown.getCause());
        }
    }
}
