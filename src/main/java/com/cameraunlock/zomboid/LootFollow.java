package com.cameraunlock.zomboid;

/**
 * When the loot panel is asked to show what the crosshair is on, while InventoryStaysOpen keeps
 * it open in first person. It is asked as the crosshair moves on to something else, and not
 * again for the same thing once it shows it, so a container the player picked in the panel
 * themselves stays picked until the look moves on. One the panel would not take, because the
 * game has not put it among the containers round the character yet, is asked for again a
 * little later. What the crosshair is on is looked for a few times a second, not every frame.
 *
 * Logic thread only.
 */
final class LootFollow {
    /** How often what the crosshair is on is looked for. */
    static final long LOOK_EVERY_NANOS = 100_000_000L;
    /** How long after the panel did not take a container it is asked again. */
    static final long ASK_AGAIN_NANOS = 250_000_000L;

    private boolean looked;
    private long lookedAt;
    /** What the panel was last asked to show, while the crosshair stays on it, and whether it took it. */
    private Object followed;
    private boolean taken;
    private long askedAt;

    /** Whether it is time to look again for what the crosshair is on. */
    boolean looking(long now) {
        if (looked && now - lookedAt < LOOK_EVERY_NANOS) {
            return false;
        }
        looked = true;
        lookedAt = now;
        return true;
    }

    /**
     * Whether the panel is to be asked to show target now. Null is the crosshair on nothing
     * that is looted, which asks for nothing and lets the next thing it is on be asked for.
     */
    boolean asks(Object target, long now) {
        if (target == null) {
            followed = null;
            return false;
        }
        if (target == followed && (taken || now - askedAt < ASK_AGAIN_NANOS)) {
            return false;
        }
        followed = target;
        taken = false;
        askedAt = now;
        return true;
    }

    /** Whether the panel took what it was last asked to show. */
    void taken(boolean took) {
        taken = took;
    }

    /** Starts again, as the panels are kept open or let go of. */
    void reset() {
        looked = false;
        followed = null;
        taken = false;
    }
}
