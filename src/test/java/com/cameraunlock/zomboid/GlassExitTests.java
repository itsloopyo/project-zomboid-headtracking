package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of which side of a vehicle a shot from a seat leaves through. */
final class GlassExitTests {
    private static final float HALF_WIDE = 0.9f, HALF_LONG = 2.2f;

    private GlassExitTests() {}

    static void run() {
        Checks.run("aShotOutOfTheSideLeavesByThatSideBesideTheSeat", GlassExitTests::aShotOutOfTheSideLeavesByThatSideBesideTheSeat);
        Checks.run("aShotAheadOrBehindLeavesByTheEnds", GlassExitTests::aShotAheadOrBehindLeavesByTheEnds);
        Checks.run("aSeatAHairOutsideTheBoxIsTakenFromInsideIt", GlassExitTests::aSeatAHairOutsideTheBoxIsTakenFromInsideIt);
        Checks.run("aShotStraightUpOrDownLeavesByNoSide", GlassExitTests::aShotStraightUpOrDownLeavesByNoSide);
    }

    private static void aShotOutOfTheSideLeavesByThatSideBesideTheSeat() {
        float[] along = new float[1];
        check(GlassExit.leaves(-0.4f, 0.3f, -1f, 0f, HALF_WIDE, HALF_LONG, along) == GlassExit.MINUS_X, "straight out to -x leaves by the -x side");
        check(Math.abs(along[0] - 0.3f) < 1e-6f, "beside the seat, " + along[0]);
        check(GlassExit.leaves(-0.4f, 0.3f, 1f, 0.2f, HALF_WIDE, HALF_LONG, along) == GlassExit.PLUS_X, "across the vehicle and a little ahead leaves by the far side");
        check(Math.abs(along[0] - (0.3f + 0.2f * 1.3f)) < 1e-5f, "where the line meets it, " + along[0]);
    }

    private static void aShotAheadOrBehindLeavesByTheEnds() {
        float[] along = new float[1];
        check(GlassExit.leaves(-0.4f, 0.3f, 0.1f, 1f, HALF_WIDE, HALF_LONG, along) == GlassExit.PLUS_Z, "nearly straight along +z leaves by that end");
        check(Math.abs(along[0] - (-0.4f + 0.1f * 1.9f)) < 1e-5f, "where the line meets it, " + along[0]);
        check(GlassExit.leaves(-0.4f, 0.3f, 0f, -1f, HALF_WIDE, HALF_LONG, along) == GlassExit.MINUS_Z, "straight along -z leaves by the other end");
        check(GlassExit.leaves(0f, 0f, 1f, 1f, 1f, 1f, along) == GlassExit.PLUS_X, "through a corner the side across is taken");
    }

    private static void aSeatAHairOutsideTheBoxIsTakenFromInsideIt() {
        float[] along = new float[1];
        check(GlassExit.leaves(-0.95f, 0.3f, -1f, 0.1f, HALF_WIDE, HALF_LONG, along) == GlassExit.MINUS_X, "a seat at the door, out past the box, still shoots out of its own side");
        check(Math.abs(along[0] - 0.3f) < 1e-6f, "at once, beside the seat");
        check(GlassExit.leaves(-0.95f, 0.3f, 1f, 0f, HALF_WIDE, HALF_LONG, along) == GlassExit.PLUS_X, "and across the vehicle out of the far side");
    }

    private static void aShotStraightUpOrDownLeavesByNoSide() {
        check(GlassExit.leaves(0.2f, 0.1f, 0f, 0f, HALF_WIDE, HALF_LONG, new float[1]) == GlassExit.NONE, "a shot with no way along the ground breaks no glass");
    }
}
