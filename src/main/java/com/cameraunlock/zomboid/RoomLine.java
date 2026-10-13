package com.cameraunlock.zomboid;

import java.util.IdentityHashMap;
import java.util.Iterator;

/**
 * Who is let room on the card first when there is not enough for everyone who asks: the
 * nearest of those who found none. Nobody further off is let any until they have had theirs,
 * or have not asked for a while, so the room that frees as others let go goes to them. One who
 * has been first for long and still found no room, whose room may never come, is put aside
 * for as long again: they then take room only where it fits with nobody first, and the rest
 * are served meanwhile.
 */
final class RoomLine {
    private final long forgetNanos, patienceNanos;
    private Object first;
    private float firstAway;
    private long firstAsked, firstSince;
    /** Those put aside, and when. */
    private final IdentityHashMap<Object, Long> aside = new IdentityHashMap<>();

    /**
     * @param forgetNanos   how long whoever is first in line keeps their place without asking
     * @param patienceNanos how long whoever is first in line keeps their place without being served, and is then put aside for
     */
    RoomLine(long forgetNanos, long patienceNanos) {
        this.forgetNanos = forgetNanos;
        this.patienceNanos = patienceNanos;
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
        if (first != null && now - firstSince > patienceNanos) {
            aside.put(first, now);
            first = null;
        }
        for (Iterator<Long> each = aside.values().iterator(); each.hasNext();) {
            if (now - each.next() > patienceNanos) {
                each.remove();
            }
        }
        boolean firstGone = first == null || now - firstAsked > forgetNanos;
        if (aside.containsKey(who)) {
            return fits && firstGone;
        }
        boolean ahead = firstGone || who == first || away < firstAway;
        if (!ahead) {
            return false;
        }
        if (fits) {
            if (who == first) {
                first = null;
            }
            return true;
        }
        if (who != first) {
            first = who;
            firstSince = now;
        }
        firstAway = away;
        firstAsked = now;
        return false;
    }
}
