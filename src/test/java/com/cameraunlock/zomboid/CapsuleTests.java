package com.cameraunlock.zomboid;

/** Tests of which part of a character a first person shot is on. */
final class CapsuleTests {
    /** A person standing at the origin facing -x: head, chest, and a right arm held out in front of the chest. */
    private static final float[] ENDS = {
            0f, 0f, 1.50f, 0f, 0f, 1.72f,
            0f, 0f, 1.00f, 0f, 0f, 1.45f,
            -0.25f, 0.10f, 1.30f, -0.25f, 0.10f, 1.05f,
    };
    private static final float[] RADII = {0.11f, 0.17f, 0.06f};
    private static final int HEAD = 0, CHEST = 1, ARM = 2;

    private CapsuleTests() {}

    static void run() {
        Checks.run("anAimAtTheHeadIsOnTheHead", CapsuleTests::anAimAtTheHeadIsOnTheHead);
        Checks.run("anAimAtTheChestIsOnTheChestNotTheHead", CapsuleTests::anAimAtTheChestIsOnTheChestNotTheHead);
        Checks.run("anAimFromAboveAtTheChestIsOnTheChest", CapsuleTests::anAimFromAboveAtTheChestIsOnTheChest);
        Checks.run("whatIsInFrontIsHitFirst", CapsuleTests::whatIsInFrontIsHitFirst);
        Checks.run("aNearMissIsOnTheNearestPart", CapsuleTests::aNearMissIsOnTheNearestPart);
        Checks.run("aWideMissIsOnNoPart", CapsuleTests::aWideMissIsOnNoPart);
        Checks.run("nothingBehindTheEyeIsHit", CapsuleTests::nothingBehindTheEyeIsHit);
        Checks.run("theClosestApproachIsWhereTheRayCrossesTheBone", CapsuleTests::theClosestApproachIsWhereTheRayCrossesTheBone);
        Checks.run("whereOnAPartTheAimLandsIsMeasuredAlongIt", CapsuleTests::whereOnAPartTheAimLandsIsMeasuredAlongIt);
    }

    private static ViewCamera.Pose ray(float x, float y, float up, float toX, float toY, float toUp) {
        ViewCamera.Pose pose = new ViewCamera.Pose();
        float dx = toX - x, dy = toY - y, du = toUp - up;
        float length = (float) Math.sqrt(dx * dx + dy * dy + du * du);
        pose.eyeX = x;
        pose.eyeY = y;
        pose.eyeUp = up;
        pose.forwardX = dx / length;
        pose.forwardY = dy / length;
        pose.forwardUp = du / length;
        return pose;
    }

    private static int on(ViewCamera.Pose ray) {
        return Capsules.first(ray, ENDS, RADII, RADII.length, HitPart.NEAR_MISS, new float[1]);
    }

    private static void anAimAtTheHeadIsOnTheHead() {
        Checks.check(on(ray(-8f, 0f, 1.6f, 0f, 0f, 1.6f)) == HEAD, "level with the head");
        Checks.check(on(ray(-4f, -3f, 1.6f, 0f, 0f, 1.62f)) == HEAD, "from the side and a little below");
    }

    private static void anAimAtTheChestIsOnTheChestNotTheHead() {
        Checks.check(on(ray(-8f, 0f, 1.6f, 0f, 0f, 1.2f)) == CHEST, "from eye height down at the chest, past the arm");
        Checks.check(on(ray(-8f, -3f, 1.6f, 0f, 0f, 1.35f)) == CHEST, "from the side away from the arm");
    }

    private static void anAimFromAboveAtTheChestIsOnTheChest() {
        Checks.check(on(ray(-3f, 0f, 4f, 0f, 0f, 1.2f)) == CHEST, "from an upper floor, steeply down past the head");
    }

    private static void whatIsInFrontIsHitFirst() {
        Checks.check(on(ray(-8f, 0.1f, 1.2f, 0f, 0.1f, 1.2f)) == ARM, "the arm held across the chest takes it");
    }

    private static void aNearMissIsOnTheNearestPart() {
        Checks.check(on(ray(-8f, 0.17f, 1.65f, 0f, 0.17f, 1.65f)) == HEAD, "just past the side of the head");
    }

    private static void aWideMissIsOnNoPart() {
        Checks.check(on(ray(-8f, 0.6f, 1.6f, 0f, 0.6f, 1.6f)) == Capsules.NONE, "a hand's breadth clear of the head");
        Checks.check(on(ray(-8f, 0f, 2.2f, 0f, 0f, 2.2f)) == Capsules.NONE, "over the top");
    }

    private static void nothingBehindTheEyeIsHit() {
        Checks.check(on(ray(2f, 0f, 1.6f, 4f, 0f, 1.6f)) == Capsules.NONE, "looking away from the person");
    }

    private static void theClosestApproachIsWhereTheRayCrossesTheBone() {
        float[] out = new float[2];
        Capsules.closest(ray(-5f, 0.05f, 1.2f, 0f, 0.05f, 1.2f), ENDS, 6, out);
        Checks.check(Math.abs(out[0] - 5f) < 1e-4f, "along the ray to the bone: " + out[0]);
        Checks.check(Math.abs(out[1] - 0.05f) < 1e-4f, "beside it: " + out[1]);
        Capsules.closest(ray(-5f, 0f, 3f, 0f, 0f, 3f), ENDS, 6, out);
        Checks.check(Math.abs(out[1] - 1.55f) < 1e-4f, "past its top end, measured from that end: " + out[1]);
    }

    private static void whereOnAPartTheAimLandsIsMeasuredAlongIt() {
        float[] along = new float[1];
        Checks.check(Capsules.first(ray(-8f, -3f, 1.1f, 0f, 0f, 1.1f), ENDS, RADII, RADII.length, HitPart.NEAR_MISS, along) == CHEST,
                "the chest, from the side away from the arm");
        Checks.check(Math.abs(along[0] - 0.2222f) < 0.01f, "a fifth of the way up from its lower end: " + along[0]);
        Capsules.first(ray(-8f, -3f, 1.4f, 0f, 0f, 1.4f), ENDS, RADII, RADII.length, HitPart.NEAR_MISS, along);
        Checks.check(Math.abs(along[0] - 0.8889f) < 0.01f, "near its top: " + along[0]);
        Checks.check(Capsules.first(ray(-8f, 0.17f, 1.65f, 0f, 0.17f, 1.65f), ENDS, RADII, RADII.length, HitPart.NEAR_MISS, along) == HEAD
                && Math.abs(along[0] - 0.6818f) < 0.01f, "and along the part it only just misses: " + along[0]);
    }
}
