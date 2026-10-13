package com.cameraunlock.zomboid;

import zombie.iso.IsoDirections;

/**
 * What a vehicle driven into a fence or a lamp post does to it, played alone: faster than a
 * walk it knocks the thing down and drives on, a little slower and a little dented, as in an
 * open-world driving game. The game has the knocking down already, for anything its tiles mark
 * as hit by cars, and asks for 150 km/h before it happens to a fence or a post: no car gets
 * there, so a fence stops a car dead. See CarSmash for where this meets the game.
 */
final class Knockdown {
    private Knockdown() {}

    enum Kind { FENCE, POST }

    /** A push slower than this is a push: the thing stands, as it does in the game. */
    static final float WALKING_KMH = 10f;

    /**
     * Faster than this is the physics engine's velocity gone wrong, not a drive: the fastest
     * vehicle the game defines tops out at 120 km/h. How far ahead a vehicle is looked at grows
     * with its speed, and a vehicle flung at thousands of tiles a second had the logic thread
     * look through hundreds of millions of squares an update, which froze the game.
     */
    static final float MOST_KMH = 400f;

    /** Whether a vehicle knocks things down at all: alone, with its engine running, faster than a walk and no faster than any vehicle goes. */
    static boolean smashing(boolean online, boolean engineRunning, float kmh) {
        return !online && engineRunning && Math.abs(kmh) > WALKING_KMH && Math.abs(kmh) <= MOST_KMH;
    }

    /**
     * Whether a vehicle goes through a thing the game marks only as slowing a car (CarSlowFactor
     * and not HitByCar: bushes, shrubs and other growth) without being slowed: alone it does, as
     * in an open-world driving game. The game pushes back on a car by its speed and that factor at
     * every one it meets.
     */
    static boolean brushedThrough(boolean online, boolean slows, boolean hitByCar) {
        return !online && slows && !hitByCar;
    }

    /**
     * Whether the engine gives less off the road, as the game's own off-road check says it does:
     * there it cuts the engine's force to 0.6 of itself and less in the higher gears, times the
     * vehicle's off-road efficiency. Alone it does not; the softer grip and the bumps off the road
     * are left as the game has them.
     */
    static boolean engineHeldOffRoad(boolean online, boolean offRoad) {
        return online && offRoad;
    }

    /** Whether a vehicle is going faster than any vehicle goes, or at a speed that is no number. */
    static boolean flung(float kmh) {
        return !(Math.abs(kmh) <= MOST_KMH);
    }

    /**
     * What a thing is, or null for anything else, by its sprite, the Material its tile names,
     * whether the game knows how to knock it down (its tile marks it as hit by cars, or the game
     * breaks or bends it as zombies do) and whether it stands along a square's edge.
     * Fences are every sheet of them the game knows how to knock down, chain link, wire, wood and
     * metal bars, the pieces either side of a hole included, but stone: a stone fence is a low
     * wall. Chain link and wire (Material MetalWire) standing along an edge are fences whether
     * the game knows them or not; what is left lying of a broken one stands on no edge. Lamp
     * posts are the marked posts of the street lamp sheet.
     */
    static Kind of(String sprite, String material, boolean known, boolean onEdge) {
        if (sprite == null) {
            return null;
        }
        if (sprite.startsWith("fencing_")) {
            return "Stone".equals(material) || !known && !("MetalWire".equals(material) && onEdge) ? null : Kind.FENCE;
        }
        return known && sprite.startsWith("lighting_outdoor_01_") ? Kind.POST : null;
    }

    /** Whether a thing on the level over a post is the post's street lamp: the street lamp sheet has no CustomName, its wall lamps, signs and floodlights do. */
    static boolean streetLamp(String sprite, String customName) {
        return sprite != null && sprite.startsWith("lighting_outdoor_01_") && customName == null;
    }

    /** A point a share of the way along a square's edge: the north edge runs along x, the west edge along y. */
    static float edgeX(int x, boolean north, float share) {
        return north ? x + share : x;
    }

    static float edgeY(int y, boolean north, float share) {
        return north ? y : y + share;
    }

    /** How near the front of the vehicle's box, the way it is going, a thing is met before the box reaches it. In tiles. */
    static final float CLEARANCE = 0.2f;

    /**
     * How far ahead of the vehicle's box, the way it is going, a thing is met beyond the
     * clearance: the ground it covers in a tenth of a second, so the thing is gone before the
     * game's physics meets it. In tiles, from the vehicle's speed in tiles a second.
     */
    static float ahead(float tilesPerSecond) {
        return Math.abs(tilesPerSecond) * 0.1f;
    }

    /**
     * Whether a point is met: it is in front of the middle of the vehicle's box the way the
     * vehicle is going, and the box, carried on that way by up to the clearance and ahead, takes
     * it in. The point is across and along the vehicle from the middle of its box; the way it
     * goes is a direction across and along, of any length. Nothing beside or behind the box is
     * met, and nothing at all while it stands.
     */
    static boolean met(float across, float along, float goingAcross, float goingAlong, float halfAcross, float halfAlong, float ahead) {
        float length = (float) Math.hypot(goingAcross, goingAlong);
        if (length == 0f) {
            return false;
        }
        float wayAcross = goingAcross / length, wayAlong = goingAlong / length;
        if (across * wayAcross + along * wayAlong <= 0f) {
            return false;
        }
        float reach = ahead + CLEARANCE;
        for (int step = 0; step <= 4; step++) {
            float carried = reach * step / 4f;
            if (Math.abs(across - wayAcross * carried) <= halfAcross && Math.abs(along - wayAlong * carried) <= halfAlong) {
                return true;
            }
        }
        return false;
    }

    /** The share of its speed a vehicle keeps through one piece of a thing met square on: a post is a pole set in concrete. */
    static float speedKept(Kind kind) {
        return kind == Kind.FENCE ? 0.9f : 0.65f;
    }

    /**
     * How square on a thing is met, 0 to 1: of a fence, the share of the way the vehicle goes
     * that runs across its edge, so a fence grazed along its length takes almost nothing; of
     * anything standing on no edge, a post, the whole. A north edge runs along x, a west edge
     * along y; a corner piece on both is met as squarely as either.
     */
    static float squareness(boolean north, boolean west, float goingX, float goingY) {
        float length = (float) Math.hypot(goingX, goingY);
        if (!north && !west || length == 0f) {
            return 1f;
        }
        return Math.max(north ? Math.abs(goingY) / length : 0f, west ? Math.abs(goingX) / length : 0f);
    }

    /** The share of its speed a vehicle keeps through one piece met this squarely. */
    static float speedKept(Kind kind, float squareness) {
        return 1f - (1f - speedKept(kind)) * squareness;
    }

    /** The share kept through several pieces met at once: each takes its share of what the last left. */
    static float speedKept(Iterable<Float> eachKept) {
        float kept = 1f;
        for (float each : eachKept) {
            kept *= each;
        }
        return kept;
    }

    /**
     * How hard a crash a loss of this much speed is, in metres a second, or 0 for none: the
     * game's own rule for a vehicle that meets something (BaseVehicle.update), a crash of three
     * times the change of speed where that change is over 1.
     */
    static float crash(float speedLost) {
        return speedLost > 1f ? speedLost * 3f : 0f;
    }

    /** Whether the front of the vehicle took a knock, by which way along it the vehicle was going. */
    static boolean front(float goingAlong) {
        return goingAlong >= 0f;
    }

    /** The way the game itself breaks a fence a vehicle hits (IsoObject.Collision): away from the side the vehicle is on. */
    static IsoDirections breakTowards(boolean north, float vehicleX, float vehicleY, float fenceX, float fenceY) {
        if (north) {
            return vehicleY >= fenceY ? IsoDirections.N : IsoDirections.S;
        }
        return vehicleX >= fenceX ? IsoDirections.W : IsoDirections.E;
    }
}
