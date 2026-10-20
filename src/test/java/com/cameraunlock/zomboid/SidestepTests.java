package com.cameraunlock.zomboid;

/** Tests of Sidestep: a character walked sideways or backwards facing the view keeps the game's pace for the walk. */
final class SidestepTests {
    private SidestepTests() {}

    static void run() {
        eachWayTheStanceTimesItsRaiseIsTheGait();
        theRaiseChangesSmoothlyRoundFromBackToSideways();
        theGaitIsTheGamesReadingOfTheKeys();
    }

    private static void eachWayTheStanceTimesItsRaiseIsTheGait() {
        float diagonal = (float) -Math.sqrt(0.5);
        float[][] ways = {{-1f, Sidestep.BACK}, {diagonal, Sidestep.BACK_ASIDE}, {0f, Sidestep.ASIDE}};
        for (float[] way : ways) {
            check(Math.abs(Sidestep.stance(way[0]) - way[1]) < 1e-4f, "the stance at " + way[0] + " covers what was measured: " + Sidestep.stance(way[0]));
            check(Math.abs(Sidestep.stance(way[0]) * Sidestep.raised(way[0], Sidestep.Gait.WALK) - 1f) < 1e-4f
                            && Math.abs(Sidestep.stance(way[0]) * Sidestep.raised(way[0], Sidestep.Gait.JOG) - Sidestep.JOG) < 1e-4f
                            && Math.abs(Sidestep.stance(way[0]) * Sidestep.raised(way[0], Sidestep.Gait.SPRINT) - Sidestep.SPRINT) < 1e-4f
                            && Math.abs(Sidestep.stance(way[0]) * Sidestep.raised(way[0], Sidestep.Gait.SNEAK) - Sidestep.SNEAK) < 1e-4f
                            && Math.abs(Sidestep.stance(way[0]) * Sidestep.raised(way[0], Sidestep.Gait.SNEAK_JOG) - Sidestep.SNEAK_JOG) < 1e-4f,
                    "walked at " + way[0] + " the character makes the walk's, the jog's, the sprint's, the sneak's and the crouched jog's pace");
        }
        check(Sidestep.stance(0.5f) == Sidestep.ASIDE, "a controller pushed partly ahead covers the sideways share");
    }

    private static void theRaiseChangesSmoothlyRoundFromBackToSideways() {
        float was = Sidestep.raised(-1f, Sidestep.Gait.WALK);
        for (int step = 1; step <= 90; step++) {
            float now = Sidestep.raised((float) -Math.cos(Math.toRadians(step)), Sidestep.Gait.WALK);
            check(now >= was - 1e-4f && now - was < 0.1f, "the raise grows a little at each degree from straight back: " + step + " gives " + now);
            was = now;
        }
    }

    private static void theGaitIsTheGamesReadingOfTheKeys() {
        check(Sidestep.gait(false, true, false, false) == Sidestep.Gait.SPRINT, "on the keyboard the sprint key sprints on its own");
        check(Sidestep.gait(false, true, true, false) == Sidestep.Gait.WALK, "on a controller the sprint button alone does not");
        check(Sidestep.gait(true, true, true, false) == Sidestep.Gait.SPRINT, "on a controller the run and sprint buttons together sprint");
        check(Sidestep.gait(true, false, false, false) == Sidestep.Gait.JOG && Sidestep.gait(true, false, false, true) == Sidestep.Gait.SNEAK_JOG,
                "the jog key jogs, at the crouched jog's pace crouched");
        check(Sidestep.gait(false, false, false, true) == Sidestep.Gait.SNEAK && Sidestep.gait(false, false, false, false) == Sidestep.Gait.WALK,
                "with neither key down a crouched character sneaks and a standing one walks");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}
