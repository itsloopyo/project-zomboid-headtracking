package com.cameraunlock.zomboid;

/** Where a throw from the first person view is aimed, so that it lands on the aim point: see Controls.throwing. */
final class Throws {
    private Throws() {}

    /**
     * How far past the corner of its square a throw flies, for each tile from
     * the thrower to that corner. IsoGameCharacter.Throw starts it at 0.4 of
     * that distance a step, and IsoPhysicsObject.update takes away a tenth of
     * the speed a step at 30 steps a second and moves it 0.3 of the speed, both
     * scaled by 30 over the frame rate the game is locked to. The flight is all
     * but over long before the throw comes down, so it is the whole sum.
     */
    static float carry(int lockFps) {
        float step = 30f / lockFps;
        float keep = 1f - 0.1f * step;
        return 0.4f * 0.3f * step * keep / (1f - keep);
    }

    /** The square, along one axis, whose corner a throw from from is aimed at to land at aimed: see Controls.throwing. Within range of from, as the game holds it. */
    static int square(float from, float aimed, float range, float carry) {
        float along = Math.max(-range, Math.min(range, (aimed - from) / carry));
        return Math.round(from + along);
    }
}
