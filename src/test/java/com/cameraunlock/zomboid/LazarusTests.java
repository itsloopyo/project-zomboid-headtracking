package com.cameraunlock.zomboid;

/** Tests of which snapshot Lazarus takes a player back to. */
final class LazarusTests {
    private LazarusTests() {}

    static void run() {
        float[] full = new float[LazarusRing.KEPT];
        for (int i = 0; i < full.length; i++) {
            full[i] = 100f + i * LazarusRing.EVERY;
        }
        float now = full[full.length - 1] + 0.5f;
        int chosen = LazarusRing.rewoundTo(full, now);
        Checks.check(now - full[chosen] >= LazarusRing.REWIND && (chosen == full.length - 1 || now - full[chosen + 1] < LazarusRing.REWIND),
                "with a full ring the snapshot gone back to is the newest at least " + LazarusRing.REWIND + " s old: " + (now - full[chosen]) + " s");
        Checks.check(chosen > 0, "and a full ring keeps one older than that, so one that is old enough is always there");
        Checks.check(LazarusRing.rewoundTo(new float[] {3f, 4f, 5f}, 6f) == 0, "with less than " + LazarusRing.REWIND + " s played it is the oldest there is");
    }
}
