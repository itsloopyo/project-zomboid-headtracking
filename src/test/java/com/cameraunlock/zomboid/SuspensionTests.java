package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

final class SuspensionTests {
    private SuspensionTests() {}

    static void run() {
        Checks.run("theFrontLiftsOntoAHillBeforeTheBackAndBobsOnItsSprings", SuspensionTests::theFrontLiftsOntoAHillBeforeTheBackAndBobsOnItsSprings);
        Checks.run("aKerbUnderOneSideRollsTheBodyAndItSettles", SuspensionTests::aKerbUnderOneSideRollsTheBodyAndItSettles);
        Checks.run("aCornerStopsAtItsTravel", SuspensionTests::aCornerStopsAtItsTravel);
    }

    private static final float STEP = 1f / 60f, LENGTH = 4f, WIDTH = 2f, GRADE = 0.2f;

    /** The ground on a road facing along x that is level to 0 and climbs GRADE beyond it. */
    private static float road(float x) {
        return Math.max(0f, x) * GRADE;
    }

    private static void theFrontLiftsOntoAHillBeforeTheBackAndBobsOnItsSprings() {
        Suspension springs = new Suspension();
        VehicleSlope ground = new VehicleSlope(), body = new VehicleSlope();
        float speed = 5f, lowestFront = 0f, highestFront = 0f;
        boolean setDown = true, nosedUp = false;
        for (float x = -10f; x < 40f; x += speed * STEP) {
            float front = road(x + LENGTH / 2f), back = road(x - LENGTH / 2f);
            ground.on(front, front, back, back, 1f, 0f, LENGTH, WIDTH);
            springs.carry(ground, STEP, setDown, body);
            setDown = false;
            if (x + LENGTH / 2f > 0f && x - LENGTH / 2f < 0f) {
                check(body.corners[2] == 0f && body.corners[3] == 0f, "the back stays on the level while only the front is on the hill: " + body.corners[2]);
                lowestFront = Math.min(lowestFront, body.corners[0] - front);
                highestFront = Math.max(highestFront, body.corners[0] - front);
                float[] nose = {1f, 0f, 0f};
                body.apply(nose, 0);
                nosedUp |= nose[2] > 0.1f;
            }
        }
        check(nosedUp, "the nose is up while the front is on the hill and the back is not");
        check(lowestFront < -0.03f && highestFront > 0.01f, "the front sinks onto its springs as it meets the hill and swings back past rest: " + lowestFront + " " + highestFront);
        for (int i = 0; i < 4; i++) {
            check(Math.abs(body.corners[i] - ground.corners[i]) < 1e-3f, "on a steady climb every corner rests over its wheel: " + i + " " + (body.corners[i] - ground.corners[i]));
        }
    }

    private static void aKerbUnderOneSideRollsTheBodyAndItSettles() {
        Suspension springs = new Suspension();
        VehicleSlope ground = new VehicleSlope(), body = new VehicleSlope();
        ground.on(0f, 0f, 0f, 0f, 0f, 1f, LENGTH, WIDTH);
        springs.carry(ground, STEP, true, body);
        // Facing along y, the right is (1, 0): its wheels go up a 5 cm kerb.
        ground.on(0f, 0.05f, 0f, 0.05f, 0f, 1f, LENGTH, WIDTH);
        boolean rolled = false;
        for (int step = 0; step < 300; step++) {
            springs.carry(ground, STEP, false, body);
            float[] up = {0f, 0f, 1f};
            body.apply(up, 0);
            rolled |= up[0] < -0.005f;
            check(Float.isFinite(body.corners[1]), "the corners stay numbers");
        }
        check(rolled, "the body rolls to the left as the right side is lifted");
        check(Math.abs(body.corners[1] - 0.05f) < 1e-3f && Math.abs(body.corners[0]) < 1e-3f, "and settles on the kerb: " + body.corners[0] + " " + body.corners[1]);
    }

    private static void aCornerStopsAtItsTravel() {
        Suspension springs = new Suspension();
        VehicleSlope ground = new VehicleSlope(), body = new VehicleSlope();
        ground.on(0f, 0f, 0f, 0f, 1f, 0f, LENGTH, WIDTH);
        springs.carry(ground, STEP, true, body);
        springs.carry(ground, STEP, false, body);
        springs.land(20f);
        for (int step = 0; step < 120; step++) {
            springs.carry(ground, STEP, false, body);
            for (int i = 0; i < 4; i++) {
                check(body.corners[i] >= -Suspension.TRAVEL - 1e-6f, "a hard landing takes a corner no further down than its stop: " + body.corners[i]);
            }
        }
    }
}
