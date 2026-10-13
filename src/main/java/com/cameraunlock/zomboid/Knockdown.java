package com.cameraunlock.zomboid;

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
     * What a thing is, by its sprite, the Material its tile names, whether the tile marks it as
     * hit by cars and whether it stands along a square's edge, or null for anything else.
     * Fences are every sheet of them the tiles mark, chain link, wire, wood and metal bars, but
     * stone: a stone fence is a low wall. Chain link and wire (Material MetalWire) are fences
     * whether marked or not, as long as they stand along an edge: the tall chain link
     * fencing_01_80 to 92 is not marked, and what is left lying of a broken one stands on no
     * edge. Lamp posts are the marked posts of the street lamp sheet; the lamps on top of them
     * are on the level above.
     */
    static Kind of(String sprite, String material, boolean hitByCar, boolean onEdge) {
        if (sprite == null) {
            return null;
        }
        if (sprite.startsWith("fencing_")) {
            return "Stone".equals(material) || !hitByCar && !("MetalWire".equals(material) && onEdge) ? null : Kind.FENCE;
        }
        return hitByCar && sprite.startsWith("lighting_outdoor_01_") ? Kind.POST : null;
    }

    /** How near the vehicle's box a thing is met on every side, so a fence brushed along the side is met too. In tiles. */
    static final float CLEARANCE = 0.2f;

    /**
     * How far ahead of the vehicle's box, the way it is going, a thing is met: the ground it
     * covers in a tenth of a second, so the thing is gone before the game's physics meets it.
     * In tiles, from the vehicle's speed in tiles a second.
     */
    static float ahead(float tilesPerSecond) {
        return Math.abs(tilesPerSecond) * 0.1f;
    }

    /**
     * Whether a point, across and along the vehicle from the middle of its box, with along
     * counted the way the vehicle is going, is within reach of a box this wide and long.
     */
    static boolean inReach(float across, float along, float halfAcross, float halfAlong, float ahead) {
        return Math.abs(across) < halfAcross + CLEARANCE && along > -halfAlong - CLEARANCE && along < halfAlong + CLEARANCE + ahead;
    }

    /** Whether a vehicle going this fast, either way, knocks a thing down. */
    static boolean fastEnough(float kmh) {
        return Math.abs(kmh) > WALKING_KMH;
    }

    /** The share of its speed a vehicle keeps through one piece of a thing: a post is a pole set in concrete. */
    static float speedKept(Kind kind) {
        return kind == Kind.FENCE ? 0.9f : 0.65f;
    }

    /**
     * How hard the end of the vehicle that hit is damaged, in the game's own measure of a crash:
     * each part there loses that much condition, give or take five (BaseVehicle.addDamageFront).
     * A fence panel at 50 km/h is 3, a post 10.
     */
    static int damage(Kind kind, float kmh) {
        return Math.round(Math.abs(kmh) / (kind == Kind.FENCE ? 20f : 5f));
    }
}
