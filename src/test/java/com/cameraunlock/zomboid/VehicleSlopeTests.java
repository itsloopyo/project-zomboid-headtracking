package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

final class VehicleSlopeTests {
    private VehicleSlopeTests() {}

    static void run() {
        Checks.run("levelGroundTurnsNothing", VehicleSlopeTests::levelGroundTurnsNothing);
        Checks.run("aCarFacingUphillHasItsNoseUp", VehicleSlopeTests::aCarFacingUphillHasItsNoseUp);
        Checks.run("aSlopeAcrossRollsTheCarToTheLowSide", VehicleSlopeTests::aSlopeAcrossRollsTheCarToTheLowSide);
        Checks.run("theTurnIsATurnAndNoneAboutUp", VehicleSlopeTests::theTurnIsATurnAndNoneAboutUp);
    }

    private static final float CLOSE = 1e-5f;

    private static void levelGroundTurnsNothing() {
        VehicleSlope slope = new VehicleSlope();
        slope.on(2f, 2f, 2f, 2f, 0.6f, 0.8f, 4.7f, 1.8f);
        float[] up = {0f, 0f, 1f};
        slope.apply(up, 0);
        check(Math.abs(slope.groundUp - 2f) < CLOSE && slope.steep == 0f && Math.abs(up[2] - 1f) < CLOSE && Math.abs(up[0]) < CLOSE,
                "on level ground the vehicle stands at the ground's height and is not tipped");
    }

    private static void aCarFacingUphillHasItsNoseUp() {
        VehicleSlope slope = new VehicleSlope();
        // Facing along x, its front a tile higher than its back over a length of four.
        slope.on(1f, 1f, 0f, 0f, 1f, 0f, 4f, 2f);
        float[] nose = {1f, 0f, 0f}, up = {0f, 0f, 1f};
        slope.apply(nose, 0);
        slope.apply(up, 0);
        float angle = (float) Math.atan(0.25);
        check(Math.abs(nose[2] - (float) Math.sin(angle)) < CLOSE && Math.abs(nose[0] - (float) Math.cos(angle)) < CLOSE,
                "the nose rises by the slope's angle: " + nose[0] + ", " + nose[2]);
        check(Math.abs(up[0] + (float) Math.sin(angle)) < CLOSE && Math.abs(up[2] - (float) Math.cos(angle)) < CLOSE,
                "and its up leans back, square to the slope: " + up[0] + ", " + up[2]);
        check(Math.abs(slope.steep - (float) Math.sin(angle)) < CLOSE && Math.abs(slope.downX + 1f) < CLOSE && Math.abs(slope.downY) < CLOSE,
                "down the slope is behind it, as steep as the climb: " + slope.downX + ", " + slope.downY + " " + slope.steep);
        check(Math.abs(slope.groundUp - 0.5f) < CLOSE, "and it stands at the height under its middle: " + slope.groundUp);
    }

    private static void aSlopeAcrossRollsTheCarToTheLowSide() {
        VehicleSlope slope = new VehicleSlope();
        // Facing along y; its right is (1, 0), and the ground on that side is higher.
        slope.on(0f, 0.5f, 0f, 0.5f, 0f, 1f, 4f, 2f);
        float[] side = {1f, 0f, 0f};
        slope.apply(side, 0);
        check(side[2] > 0f && slope.downX < -0.99f, "the right side, on the higher ground, is lifted, and down is to the left: " + side[2] + " " + slope.downX);
    }

    private static void theTurnIsATurnAndNoneAboutUp() {
        VehicleSlope slope = new VehicleSlope();
        float fx = 0.6f, fy = 0.8f;
        slope.on(1.3f, 0.7f, 0.2f, 0.4f, fx, fy, 4.7f, 1.8f);
        float[] t = slope.turn;
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < 3; b++) {
                float dot = t[a * 3] * t[b * 3] + t[a * 3 + 1] * t[b * 3 + 1] + t[a * 3 + 2] * t[b * 3 + 2];
                check(Math.abs(dot - (a == b ? 1f : 0f)) < 1e-4f, "the turn's columns are square to each other and a unit long: " + a + " " + b + " " + dot);
            }
        }
        float det = t[0] * (t[4] * t[8] - t[7] * t[5]) - t[3] * (t[1] * t[8] - t[7] * t[2]) + t[6] * (t[1] * t[5] - t[4] * t[2]);
        check(Math.abs(det - 1f) < 1e-4f, "and it turns, never mirrors: " + det);
        float[] across = {-slope.downY, slope.downX, 0f};
        slope.apply(across, 0);
        check(Math.abs(across[2]) < 1e-5f && Math.abs(across[0] + slope.downY) < 1e-5f, "the line across the slope stays where it is: nothing is turned about up");
    }
}
