package com.cameraunlock.zomboid;

/** The cone of sight as LiveSight reads it back from what LightingJNI hands on. */
final class SightRule {
    private SightRule() {}

    /** LightingJNI.coneToDegrees: the cone as the game hands it on, back to degrees across. */
    static float coneToDegrees(float cone) {
        return 180f + cone * 180f;
    }
}
