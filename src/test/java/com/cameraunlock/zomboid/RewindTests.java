package com.cameraunlock.zomboid;

/** Tests of which snapshot Rewind 30s takes a player back to. */
final class RewindTests {
    private RewindTests() {}

    static void run() {
        float[] full = new float[RewindRing.KEPT];
        for (int i = 0; i < full.length; i++) {
            full[i] = 100f + i * RewindRing.EVERY;
        }
        float now = full[full.length - 1] + 0.5f;
        int chosen = RewindRing.rewoundTo(full, now);
        Checks.check(now - full[chosen] >= RewindRing.REWIND && (chosen == full.length - 1 || now - full[chosen + 1] < RewindRing.REWIND),
                "with a full ring the snapshot gone back to is the newest at least " + RewindRing.REWIND + " s old: " + (now - full[chosen]) + " s");
        Checks.check(chosen > 0, "and a full ring keeps one older than that, so one that is old enough is always there");
        Checks.check(RewindRing.rewoundTo(new float[] {3f, 4f, 5f}, 6f) == 0, "with less than " + RewindRing.REWIND + " s played it is the oldest there is");
    }
}
