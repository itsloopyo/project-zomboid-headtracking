package com.cameraunlock.zomboid;

/**
 * Who is let room on the card first when there is not enough for everyone who asks: the
 * nearest of those who found none. Nobody further off is let any until they have had theirs,
 * or have not asked for a while, so the room that frees as others let go goes to them, and
 * whoever is first in line is never kept waiting for good.
 */
final class RoomLine {
    private final long forgetNanos;
    private Object first;
    private float firstAway;
    private long firstAsked;

    /** @param forgetNanos how long whoever is first in line keeps their place without asking */
    RoomLine(long forgetNanos) {
        this.forgetNanos = forgetNanos;
    }

    /**
     * Whether whoever asks takes the room they want now.
     *
     * @param who  the asker, the same object every time they ask
     * @param away how far from the eye they stand
     * @param now  when they ask, in nanoseconds
     * @param fits whether what they want fits in the room there is
     */
    boolean ask(Object who, float away, long now, boolean fits) {
        boolean ahead = first == null || who == first || away < firstAway || now - firstAsked > forgetNanos;
        if (!ahead) {
            return false;
        }
        if (fits) {
            if (who == first) {
                first = null;
            }
            return true;
        }
        first = who;
        firstAway = away;
        firstAsked = now;
        return false;
    }
}
