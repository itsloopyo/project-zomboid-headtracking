package com.cameraunlock.zomboid;

/** Which snapshot Rewind 30s goes back to, apart from the game's classes so the tests can ask. */
final class RewindRing {
    /** How far back a player is taken, and how often they are written out, in seconds of play. */
    static final float REWIND = 30f, EVERY = 1f;
    /** Snapshots kept a player: enough for one REWIND old with one a second. */
    static final int KEPT = (int) (REWIND / EVERY) + 2;
    /** Seconds of play the zombies stand still for once a player has gone back. */
    static final float ZOMBIES_STILL = 2f;

    private RewindRing() {}

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
