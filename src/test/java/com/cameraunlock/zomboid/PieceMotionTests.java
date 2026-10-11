package com.cameraunlock.zomboid;

import java.util.Random;

/** Tests of a cut-off piece falling as a rigid body. */
final class PieceMotionTests {
    private PieceMotionTests() {}

    static void run() {
        Checks.run("aThrownPieceComesToLieAlongTheGround", PieceMotionTests::aThrownPieceComesToLieAlongTheGround);
        Checks.run("aPieceLyingOnTheGroundStaysLying", PieceMotionTests::aPieceLyingOnTheGroundStaysLying);
    }

    private static final float GRAVITY = 9.8f / Pieces.METRES_PER_UNIT;

    private static void aThrownPieceComesToLieAlongTheGround() {
        Random random = new Random(7);
        for (int throwIt = 0; throwIt < 200; throwIt++) {
            // Cut hanging, lying or anything between, from a standing zombie's knee or elbow, or from one on the ground.
            float ax = random.nextFloat() - 0.5f, ay = random.nextFloat() - 0.5f, az = random.nextFloat() - 0.5f, a = (float) Math.sqrt(ax * ax + ay * ay + az * az);
            float length = 0.1f + 0.3f * random.nextFloat(), radius = 0.02f + 0.03f * random.nextFloat();
            float height = random.nextBoolean() ? 0.05f + length : 0.3f + 0.4f * random.nextFloat();
            PieceMotion motion = new PieceMotion(0f, height, 0f, ax / a, ay / a, az / a, length, radius, GRAVITY);
            motion.throwIt(random.nextFloat() - 0.5f, random.nextFloat() * 2f, random.nextFloat() - 0.5f,
                    (random.nextFloat() - 0.5f) * 20f, (random.nextFloat() - 0.5f) * 20f, (random.nextFloat() - 0.5f) * 20f);
            for (int second = 0; second < 20 && !motion.resting; second++) {
                motion.advance(1f);
            }
            float[] pivot = new float[3];
            motion.pivot(pivot);
            Checks.check(motion.resting, "throw " + throwIt + " comes to rest within 20 seconds: " + motion.said());
            Checks.check(motion.tilt() < Math.toRadians(12), "throw " + throwIt + " rests lying along the ground, not on an end: tilted " + Math.toDegrees(motion.tilt()));
            Checks.check(Math.abs(pivot[1] - radius) < 0.02f, "throw " + throwIt + "'s cut end rests on the ground: at " + pivot[1] + " for a radius of " + radius);
        }
    }

    private static void aPieceLyingOnTheGroundStaysLying() {
        PieceMotion motion = new PieceMotion(0f, 0.03f, 0f, 1f, 0f, 0f, 0.3f, 0.03f, GRAVITY);
        motion.advance(3f);
        float[] pivot = new float[3];
        motion.pivot(pivot);
        Checks.check(motion.resting && motion.tilt() < 0.01f && Math.abs(pivot[0]) < 0.01f, "a piece laid on the ground lies still where it is");
    }
}
