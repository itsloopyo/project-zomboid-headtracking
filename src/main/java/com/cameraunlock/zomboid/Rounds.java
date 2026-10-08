package com.cameraunlock.zomboid;

/**
 * The count that what every view shares of the world is aged and paced by.
 *
 * What a roof came to, a wall's finish and the lie of the land are the same whoever looks, so
 * they are kept once and every view's builds read them, and each is kept for so many frames
 * and read again after so many. A view counts its own builds, and by one view's count what
 * the other alone still reads would be dropped as unread. Counted by every build, two
 * players at one screen would age it twice as fast as one. So a round is counted: one ends
 * when a view that has had its turn in it comes for another. One view alone ends a round at
 * each turn, which is the count it kept for itself.
 *
 * Each is one thread's.
 */
final class Rounds {
    /** The rounds of the views' builds, on the logic thread, and of the drawing of their pictures, on the render thread. */
    static final Rounds BUILDS = new Rounds(), DRAWS = new Rounds();

    private int now;
    /** The views that have had their turn in this round, a bit each. Every bit from the start, so the first turn begins the first round. */
    private int taken = -1;
    private static final int[] loaded = new int[View.COUNT];

    private Rounds() {}

    /** A view comes for its turn: true when that begins a round. */
    boolean begin(int view) {
        boolean begins = (taken & 1 << view) != 0;
        if (begins) {
            now++;
            taken = 0;
        }
        taken |= 1 << view;
        return begins;
    }

    int now() {
        return now;
    }

    /**
     * Logic thread. Takes which chunks a view's map has loaded, as one number, and gives the
     * same for the maps of every view together: another whenever either has another set of
     * chunks loaded.
     */
    static int loaded(int view, int sum) {
        loaded[view] = sum;
        int all = 17;
        for (int each : loaded) {
            all = all * 31 + each;
        }
        return all;
    }
}
