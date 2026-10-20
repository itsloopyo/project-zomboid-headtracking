package com.cameraunlock.zomboid;

/**
 * The pace of a character walked sideways or backwards in first person, held facing the view
 * in the game's aiming stance (Controls). The stance's animations move it about a third of the
 * ground the game's walk ahead does backwards and a fifth sideways, and the game plays them at
 * the one pace whether the jog, sprint or sneak key is down. The ground they move it is raised
 * by the share that brings it to the pace it would have turned and walked, jogged, sprinted or
 * crept that way at. The legs keep the stance's step.
 */
final class Sidestep {
    /**
     * The stance's pace as a share of the walk ahead, backwards, half back and half sideways, and
     * sideways: the shares that, divided into the walk's, brought each to it over three seconds
     * from a standstill in the game.
     */
    static final float BACK = 0.339f, BACK_ASIDE = 0.317f, ASIDE = 0.238f;
    /** The game's own pace ahead as a share of its walk: jogging, sprinting, sneaking, and jogging crouched. */
    static final float JOG = 1.56f, SPRINT = 1.56f, SNEAK = 0.93f, SNEAK_JOG = 1.77f;

    enum Gait { WALK, JOG, SPRINT, SNEAK, SNEAK_JOG }

    private Sidestep() {}

    /** The gait the game would give the walk, as it reads the keys in IsoPlayer.updateInputState: on a controller a sprint is the run button with the sprint one. */
    static Gait gait(boolean run, boolean sprint, boolean controller, boolean sneaking) {
        if (sprint && (run || !controller)) {
            return Gait.SPRINT;
        }
        if (run) {
            return sneaking ? Gait.SNEAK_JOG : Gait.JOG;
        }
        return sneaking ? Gait.SNEAK : Gait.WALK;
    }

    /**
     * The ground the stance moves the character, as a share of the walk ahead.
     *
     * @param ahead the cosine of the angle between the way walked and the view, -1 straight back
     */
    static float stance(float ahead) {
        float fromBack = (float) Math.acos(Math.max(-1f, Math.min(1f, -ahead)));
        float quarter = (float) (Math.PI / 4);
        if (fromBack <= quarter) {
            return BACK + (BACK_ASIDE - BACK) * fromBack / quarter;
        }
        return fromBack <= 2f * quarter ? BACK_ASIDE + (ASIDE - BACK_ASIDE) * (fromBack - quarter) / quarter : ASIDE;
    }

    /** What the ground the stance moves the character is multiplied by to make the game's pace for the gait. */
    static float raised(float ahead, Gait gait) {
        float pace = switch (gait) {
            case WALK -> 1f;
            case JOG -> JOG;
            case SPRINT -> SPRINT;
            case SNEAK -> SNEAK;
            case SNEAK_JOG -> SNEAK_JOG;
        };
        return pace / stance(ahead);
    }
}
