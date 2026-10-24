package com.cameraunlock.zomboid;

/** Whether Lazarus is on and which snapshot it goes back to, apart from the game's classes so the agent and the tests can ask before the game loads. */
final class LazarusRing {
    /** Lazarus is held back for a later release. */
    static final boolean ON = Boolean.getBoolean("cameraunlock.zomboid.lazarus");
    /** How far back Lazarus takes a player, and how often they are written out, in seconds of play. */
    static final float REWIND = 13f, EVERY = 1f;
    /** Snapshots kept a player: enough for one REWIND old with one a second. */
    static final int KEPT = (int) (REWIND / EVERY) + 2;

    private LazarusRing() {}

    /** Of snapshots taken at these times, oldest first, the newest at least REWIND old by now, or the oldest when none is that old yet. */
    static int rewoundTo(float[] takenAt, float now) {
        int chosen = 0;
        for (int i = 0; i < takenAt.length; i++) {
            if (now - takenAt[i] >= REWIND) {
                chosen = i;
            }
        }
        return chosen;
    }
}
