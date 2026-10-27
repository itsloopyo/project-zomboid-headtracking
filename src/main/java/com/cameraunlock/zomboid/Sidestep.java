package com.cameraunlock.zomboid;

/**
 * The pace of a character walked, jogged or sprinted sideways or backwards in first person, held
 * facing the view in the game's aiming stance (Controls). The stance's animations move it about a third of the
 * ground the game's walk ahead does backwards and a fifth sideways, and the game does not slow
 * them for what slows its walk: a load, wounds, tiredness, bare feet or trees. So the pace is
 * taken from the game's own walk ahead, measured as the character walks, creeps, jogs or sprints
 * ahead, and the ground the stance moves it is raised to that pace. The legs keep the stance's step.
 * A jog or sprint not yet measured goes at the walk's pace.
 *
 * Until the character has walked ahead long enough to measure, the pace is the stance's raised by
 * the shares below. The raise is never more than OVERREACH times those, so the stance's own start
 * from a standstill still eases in, and a walk ahead blending out as the stance begins is not
 * carried past the pace.
 *
 * One per view. Logic thread only.
 */
final class Sidestep {
    /**
     * The stance's pace as a share of the walk ahead, backwards, half back and half sideways, and
     * sideways, with a weapon held in one hand: the shares that, divided into the walk's, brought
     * each to it over three seconds from a standstill in the game.
     */
    static final float BACK = 0.339f, BACK_ASIDE = 0.317f, ASIDE = 0.238f;
    /** The ways the game moves a character ahead, each measured apart. */
    static final int WALKING = 0, CREEPING = 1, JOGGING = 2, SPRINTING = 3;
    /** The game's creep ahead as a share of its walk ahead. */
    static final float SNEAK = 0.93f;
    /** How far past the shares above the raise may go, for weapons whose stance steps shorter than a one handed one's. */
    static final float OVERREACH = 1.5f;
    /** How long a walk ahead is under way before its pace is the walk's and not its start. */
    static final long UNDER_WAY_NANOS = 500_000_000L;
    /** A gap between two frames of walking ahead longer than this is a new walk. */
    static final long GAP_NANOS = 200_000_000L;
    /** The time constant the measured pace follows the walk's with. */
    static final float FOLLOW_SECONDS = 0.5f;

    private final float[] measured = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
    private long walkingSince = -1L, lastWalked = -1L;
    private int walkedGait;

    /**
     * A frame of the game's own walk ahead, its pace the ground moved over the game's time
     * multiplier, so that the game's speed does not count.
     */
    void walkedAhead(float pace, int gait, long now) {
        if (lastWalked < 0L || now - lastWalked > GAP_NANOS || gait != walkedGait) {
            walkingSince = now;
        } else if (now - walkingSince >= UNDER_WAY_NANOS) {
            int slot = gait;
            float seconds = (now - lastWalked) / 1e9f;
            measured[slot] = Float.isNaN(measured[slot]) ? pace
                    : measured[slot] + (pace - measured[slot]) * (float) (1.0 - Math.exp(-seconds / FOLLOW_SECONDS));
        }
        lastWalked = now;
        walkedGait = gait;
    }

    /** The game's pace ahead measured for one of the gaits, NaN until one has been. */
    float measured(int gait) {
        return measured[gait];
    }

    /**
     * What the ground the stance moves the character this frame is multiplied by.
     *
     * @param pace  the stance's pace this frame, the ground over the game's time multiplier
     * @param ahead the cosine of the angle between the way walked and the view, -1 straight back
     */
    float raised(float pace, float ahead, int gait) {
        if (pace <= 0f) {
            return 1f;
        }
        boolean creeping = gait == CREEPING;
        float shares = (creeping ? SNEAK : 1f) / stance(ahead);
        float walk = measured(creeping ? CREEPING : WALKING);
        float most = pace * shares * OVERREACH;
        float wanted = walk;
        if (gait >= JOGGING && !Float.isNaN(measured(gait))) {
            wanted = measured(gait);
            // The start still eases in, by the same share of the faster gait.
            most = Float.isNaN(walk) ? wanted : most * wanted / walk;
        }
        return Math.min(Float.isNaN(wanted) ? pace * shares : wanted, most) / pace;
    }

    /**
     * The stance's pace as a share of the walk ahead, from the shares measured.
     *
     * @param ahead the cosine of the angle between the way walked and the view, -1 straight back
     */
    static float stance(float ahead) {
        float fromBack = (float) Math.acos(Math.max(-1f, Math.min(1f, -ahead)));
        float quarter = (float) (Math.PI / 4);
        if (fromBack <= quarter) {
            return BACK + (BACK_ASIDE - BACK) * fromBack / quarter;
        }
        return fromBack <= 2f * quarter ? BACK_ASIDE + (ASIDE - BACK_ASIDE) * (fromBack - quarter) / quarter : ASIDE;
    }
}
