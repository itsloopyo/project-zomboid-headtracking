package com.cameraunlock.zomboid;

/** The arithmetic of the game's sight that LiveSight asks again: LightingJNI's cone and IsoGameCharacter.TestIfSeen. */
final class SightRule {
    private SightRule() {}

    /** LightingJNI.coneToDegrees: the cone as the game hands it on, back to degrees across. */
    static float coneToDegrees(float cone) {
        return 180f + cone * 180f;
    }

    /**
     * The game's IsoGameCharacter.TestIfSeen for a zombie the character has a clear line to:
     * within its sight, in its cone or within its detection range, and in light enough at that
     * distance and that far round from where the character faces. Inside the detection range the
     * light the character stands in counts for the zombie's too.
     *
     * @param distance  how far the zombie is, in tiles
     * @param facing    the cosine of how far round from where the character looks it is
     * @param coneCos   the cosine of half the cone the character takes in
     * @param sight     the game's sight for the weather and the hour, in tiles (GameTime.getViewDist)
     * @param detection the character's detection range, in tiles
     * @param light     the mean light of the zombie's square, -1 for none worked out
     * @param eyeLight  the mean light of the character's square, -1 for none worked out
     */
    static boolean seen(float distance, float facing, float coneCos, float sight, float detection, float light, float eyeLight) {
        if (distance > sight || facing < coneCos && distance >= detection || light < 0f) {
            return false;
        }
        if (distance < detection && eyeLight >= 0f) {
            light = Math.max(light, eyeLight);
        }
        if (light > 0.6f) {
            light = 1f;
        }
        float near = 1f - distance / sight;
        if (light == 1f && near > 0.3f) {
            near = 1f;
        }
        light *= Math.max(facing, 0.5f);
        if (distance <= 1f) {
            near = 1f;
            light *= 2f;
        }
        return light * near * 100f > 0.025f;
    }
}
