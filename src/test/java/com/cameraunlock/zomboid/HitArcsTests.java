package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of where the first person view marks a hit or a close zombie, and how strongly. */
final class HitArcsTests {
    private HitArcsTests() {}

    static void run() {
        Checks.run("anAttackerIsMarkedOnTheEdgeOnItsSide", HitArcsTests::anAttackerIsMarkedOnTheEdgeOnItsSide);
        Checks.run("theBearingTurnsWithTheView", HitArcsTests::theBearingTurnsWithTheView);
        Checks.run("aViewLookingDownStillHasABearing", HitArcsTests::aViewLookingDownStillHasABearing);
        Checks.run("aCornerOfAWidePictureIsOnItsSide", HitArcsTests::aCornerOfAWidePictureIsOnItsSide);
        Checks.run("aHitFadesOutOverASecondAndAHalf", HitArcsTests::aHitFadesOutOverASecondAndAHalf);
        Checks.run("aCloseZombieGlowsOnlyOutOfViewAndNear", HitArcsTests::aCloseZombieGlowsOnlyOutOfViewAndNear);
        Checks.run("aCloseZombieGlowsMostBehindAndNear", HitArcsTests::aCloseZombieGlowsMostBehindAndNear);
    }

    /** A view facing +x on the map, its right +y. */
    private static float facingX(float toX, float toY) {
        return HitArcs.bearing(1f, 0f, 0f, 1f, toX, toY);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static void anAttackerIsMarkedOnTheEdgeOnItsSide() {
        float aspect = 16f / 9f;
        check(near(facingX(3f, 0f), 0f) && HitArcs.edge(facingX(3f, 0f), aspect) == HitArcs.TOP, "ahead is the top edge: " + facingX(3f, 0f));
        check(near(facingX(0f, 2f), (float) Math.PI / 2f) && HitArcs.edge(facingX(0f, 2f), aspect) == HitArcs.RIGHT, "to the right is the right edge");
        check(near(facingX(0f, -2f), -(float) Math.PI / 2f) && HitArcs.edge(facingX(0f, -2f), aspect) == HitArcs.LEFT, "to the left is the left edge");
        check(near(Math.abs(facingX(-1f, 0f)), (float) Math.PI) && HitArcs.edge(facingX(-1f, 0f), aspect) == HitArcs.BOTTOM, "behind is the bottom edge");
        check(HitArcs.up(facingX(-1f, 0f)) < -0.999f && Math.abs(HitArcs.across(facingX(-1f, 0f))) < 1e-3f, "and is drawn at the middle of the bottom");
        check(HitArcs.across(facingX(0f, -2f)) < -0.999f, "the left is drawn at the left");
    }

    private static void theBearingTurnsWithTheView() {
        // The attacker stands still to the view's left; the view turns a quarter left to face it, then on to have it on the right.
        float toX = 0f, toY = -2f;
        check(near(HitArcs.bearing(1f, 0f, 0f, 1f, toX, toY), -(float) Math.PI / 2f), "a quarter turn left before the view turns");
        check(near(HitArcs.bearing(0f, -1f, 1f, 0f, toX, toY), 0f), "ahead once the view faces it");
        check(near(HitArcs.bearing(-1f, 0f, 0f, -1f, toX, toY), (float) Math.PI / 2f), "to the right once the view has turned on past it");
    }

    private static void aViewLookingDownStillHasABearing() {
        float down = (float) Math.toRadians(70);
        float bearing = HitArcs.bearing((float) Math.cos(down), 0f, 0f, 1f, 0f, -1f);
        check(near(bearing, -(float) Math.PI / 2f), "tipped down, the left is still the left: " + bearing);
        check(Float.isNaN(HitArcs.bearing(0f, 0f, 0f, 1f, 1f, 0f)), "a view straight down faces no way on the map");
    }

    private static void aCornerOfAWidePictureIsOnItsSide() {
        float bearing = (float) Math.toRadians(135);
        check(HitArcs.edge(bearing, 16f / 9f) == HitArcs.BOTTOM, "behind and right meets the bottom of a wide picture short of its corner");
        check(HitArcs.edge(bearing, 0.5f) == HitArcs.RIGHT, "and the right side of a tall one");
    }

    private static void aHitFadesOutOverASecondAndAHalf() {
        check(HitArcs.fade(0f) == 1f, "all of it at the hit");
        check(HitArcs.fade(-0.01f) == 0f && HitArcs.fade(HitArcs.FADE_SECONDS) == 0f && HitArcs.fade(5f) == 0f, "none before the hit or once it has faded");
        check(HitArcs.FADE_SECONDS == 1.5f, "it fades over a second and a half");
        float was = 1f;
        for (float t = 0.01f; t < HitArcs.FADE_SECONDS; t += 0.01f) {
            float now = HitArcs.fade(t);
            check(now <= was, "it only fades: " + now + " after " + was + " at " + t);
            was = now;
        }
        check(HitArcs.fade(0.75f) > 0.4f && HitArcs.fade(0.75f) < 0.6f, "half of it half way: " + HitArcs.fade(0.75f));
    }

    private static void aCloseZombieGlowsOnlyOutOfViewAndNear() {
        // A picture 90 degrees wide.
        float tanHalfWide = 1f;
        check(!HitArcs.outOfView((float) Math.toRadians(40), tanHalfWide) && HitArcs.outOfView((float) Math.toRadians(50), tanHalfWide), "the cone is 45 degrees each side");
        check(HitArcs.near(1f, (float) Math.toRadians(30), tanHalfWide) == 0f, "a zombie in view does not glow");
        check(HitArcs.near(1f, (float) Math.toRadians(-60), tanHalfWide) > 0f, "one just out of view to the left does");
        check(HitArcs.near(2.1f, (float) Math.PI, tanHalfWide) == 0f, "one behind past two tiles does not");
        check(HitArcs.near(HitArcs.NEAR_TILES, (float) Math.PI, tanHalfWide) == 0f, "and one at two tiles fades to nothing");
    }

    private static void aCloseZombieGlowsMostBehindAndNear() {
        float tanHalfWide = 1f;
        float behind = HitArcs.near(0.5f, (float) Math.PI, tanHalfWide), side = HitArcs.near(0.5f, (float) Math.toRadians(60), tanHalfWide);
        check(behind > side, "right behind glows more than at the side: " + behind + " against " + side);
        check(near(behind, 0.9375f), "right behind at half a tile, nearly all of it: " + behind);
        check(HitArcs.near(0.5f, (float) Math.PI, tanHalfWide) > HitArcs.near(1.5f, (float) Math.PI, tanHalfWide), "nearer glows more");
        check(near(HitArcs.near(0f, (float) Math.toRadians(45.0001), tanHalfWide), HitArcs.NEAR_AT_THE_SIDE), "just out at the side, the side's share");
    }
}
