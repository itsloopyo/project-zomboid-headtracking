package com.cameraunlock.zomboid;

import java.util.Locale;

/**
 * What the hands do to a firearm besides hold it: work its action after a shot, rack it, and
 * load it, each the way that kind of firearm is worked.
 */
final class ArmActs {
    private ArmActs() {}

    /** How a firearm's action is worked and how it is loaded, as the game sorts its firearms. */
    static final int NO_ACTION = 0, BOLT = 1, BOLT_SINGLE = 2, DOUBLE = 3, DOUBLE_SAWN = 4, HANDGUN = 5, LEVER = 6, REVOLVER = 7, PUMP = 8;
    /** What the holder is doing to the weapon, as the game's own animation of them says. */
    static final int HOLDING = 0, RACKING = 1, LOADING = 2, UNLOADING = 3;
    /** How long after a shot the working of an action is over, in seconds. */
    static final float CYCLE_LASTS = 1.1f;
    /**
     * A bolt is worked this long after the shot, which is when the game racks the rifle; the
     * hand leaves the grip for the knob this long after the shot, and is back on the grip this
     * long after the bolt is shut.
     */
    static final float BOLT_AFTER = 0.16f, BOLT_REACHED_FROM = 0.03f, BOLT_LEFT_IN = 0.16f;
    /** How far a lever is swung, in radians. */
    static final float LEVER_SWINGS = (float) Math.toRadians(48);
    /** How far under its well a magazine is drawn before it is carried off, and how far a round is pushed into its port, in metres. */
    static final float MAGAZINE_DRAWN = 0.1f, ROUND_PUSHED = 0.05f;
    /** How long loading one round takes until the game has shown how long it does, in seconds, and how far through it the round goes in. */
    static final float ROUND_TAKES = 1f, ROUND_IN = 0.6f;
    /**
     * How long the game takes over racking, loading and unloading, in seconds, until it has
     * shown how long it does: the hands pace what they do by it. It depends on the weapon and
     * on how practised the character is, and the game does not say it ahead of time.
     */
    static final float[] TAKES = {0f, 1.4f, 2.2f, 1.2f};
    /** How long the game takes to bring a magazine to the weapon, in seconds, until it has shown how long it does. */
    static final float FETCH_TAKES = 2.2f;
    /** A magazine out of the weapon is drawn clear of the well in the first of these, in seconds, and out of sight below by the second. */
    static final float DRAWN_CLEAR_IN = 0.15f, TAKEN_IN = 0.5f;
    /** A magazine pushed home is in the weapon this long after the game has loaded it, in seconds. */
    static final float HOME_IN = 0.12f;
    /** After the game has done loading a magazine, the hand pushes it home and lets go: over this long, in seconds. */
    static final float SEATED_FOR = 0.6f;
    /** For this much of that the weapon is still held up, in seconds: it goes down before the hand goes back to it. */
    static final float SEATED_UP_FOR = 0.3f;
    /** A hand that has to go from one hold to another that is not next to it takes this long over it, in seconds. */
    static final float CROSSES_IN = 0.2f;
    /** Further apart than this from one frame to the next, in metres, or turned further than this, two holds are not next to each other. */
    static final float JUMPS = 0.08f, JUMPS_TURNING = (float) Math.cos(Math.toRadians(35));
    /** Never seated. */
    static final float NOT_SEATING = -1f;
    /** A time the game took that is shorter or longer than these is not kept as how long it takes: something cut it short, or held it up. */
    static final float SHORTEST = 0.35f, LONGEST = 8f;

    /**
     * How far through loading one round the hand is, a frame on. The hand keeps its own time,
     * and is put right whenever the game says a round has gone in.
     *
     * @param round   how far through it was, 0 to 1
     * @param loading whether rounds are being loaded
     * @param went    whether the weapon has a round more than it had last frame
     */
    static float round(float round, boolean loading, boolean went, float seconds, float takes) {
        if (!loading) {
            return 0f;
        }
        return went ? ROUND_IN : (round + seconds / takes) % 1f;
    }

    /** The kind of action, from the game's name for how a weapon is reloaded. */
    static int kind(String reloadType) {
        return switch (reloadType.toLowerCase(Locale.ROOT)) {
            case "boltaction", "bolt_action" -> BOLT;
            case "boltactionnomag", "bolt_action_no_mag" -> BOLT_SINGLE;
            case "doublebarrelshotgun", "double_barrel_shotgun" -> DOUBLE;
            case "doublebarrelshotgunsawn", "double_barrel_shotgun_sawn" -> DOUBLE_SAWN;
            case "handgun" -> HANDGUN;
            case "leveraction", "lever_action" -> LEVER;
            case "revolver" -> REVOLVER;
            case "shotgun" -> PUMP;
            default -> NO_ACTION;
        };
    }
}
