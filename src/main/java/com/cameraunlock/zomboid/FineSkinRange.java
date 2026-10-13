package com.cameraunlock.zomboid;

import java.util.IdentityHashMap;
import java.util.Iterator;

/**
 * Who is near enough the eye to wear a finer skin. Composed whole, each skin is a 2048 by
 * 2048 picture, which a person further off than a few tiles is too small on the screen to
 * show. Someone wearing one keeps it a little further out than one is put on, so that a
 * person standing at the edge does not change skins every frame.
 */
final class FineSkinRange {
    /** Nearer than this, in tiles, a person is put into a finer skin, and one wearing one keeps it out to the second. */
    static final float WITHIN = 5f, KEPT_WITHIN = 6f;

    /** The frame each person last wore a finer skin in. */
    private final IdentityHashMap<Object, Integer> wornIn = new IdentityHashMap<>();
    private int frame = Integer.MIN_VALUE;

    /**
     * Whether this person wears a finer skin in this frame's pictures.
     *
     * @param who   the person, the same object every frame
     * @param away  how far from the eye they stand, in tiles
     * @param frame the game's frame
     */
    boolean wears(Object who, float away, int frame) {
        if (frame != this.frame) {
            this.frame = frame;
            for (Iterator<Integer> each = wornIn.values().iterator(); each.hasNext();) {
                if (each.next() < frame - 1) {
                    each.remove();
                }
            }
        }
        Integer last = wornIn.get(who);
        boolean wore = last != null && last >= frame - 1;
        boolean wears = away < WITHIN || wore && away < KEPT_WITHIN;
        if (wears) {
            wornIn.put(who, frame);
        }
        return wears;
    }

    /** How many people are remembered as wearing one. */
    int remembered() {
        return wornIn.size();
    }
}
