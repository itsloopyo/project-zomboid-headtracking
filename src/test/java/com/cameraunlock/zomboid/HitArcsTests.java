package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of where the first person view marks a hit or a close zombie, and how strongly. */
final class HitArcsTests {
    private HitArcsTests() {}

    static void run() {
        Checks.run("anAttackerIsMarkedOnTheEdgeOnItsSide", HitArcsTests::anAttackerIsMarkedOnTheEdgeOnItsSide);
        Checks.run("theBearingTurnsWithTheView", HitArcsTests::theBearingTurnsWithTheView);
        Checks.run("aPitchedAndRolledViewKeepsItsBearings", HitArcsTests::aPitchedAndRolledViewKeepsItsBearings);
        Checks.run("aViewStraightUpOrDownFacesTheWayItWouldTippedLevel", HitArcsTests::aViewStraightUpOrDownFacesTheWayItWouldTippedLevel);
        Checks.run("aBearingMovesSmoothlyAsTheHeadPitchesAndRolls", HitArcsTests::aBearingMovesSmoothlyAsTheHeadPitchesAndRolls);
        Checks.run("lyingDownTheTopOfThePictureIsAhead", HitArcsTests::lyingDownTheTopOfThePictureIsAhead);
        Checks.run("lyingDownTheTopAndBottomChangePlacesOnceAndOtherwiseMoveSmoothly", HitArcsTests::lyingDownTheTopAndBottomChangePlacesOnceAndOtherwiseMoveSmoothly);
        Checks.run("aMarkIsNamedForTheEdgeItIsDrawnNearest", HitArcsTests::aMarkIsNamedForTheEdgeItIsDrawnNearest);
        Checks.run("aHitFadesOutOverASecondAndAHalf", HitArcsTests::aHitFadesOutOverASecondAndAHalf);
        Checks.run("aCloseZombieGlowsOnlyOutOfViewAndNear", HitArcsTests::aCloseZombieGlowsOnlyOutOfViewAndNear);
        Checks.run("aCloseZombieGlowsMostBehindAndNear", HitArcsTests::aCloseZombieGlowsMostBehindAndNear);
    }

    /** A level view facing +x on the map, its right +y. */
    private static float facingX(float toX, float toY) {
        return HitArcs.bearing(1f, 0f, 0f, 0f, 0f, toX, toY);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static void anAttackerIsMarkedOnTheEdgeOnItsSide() {
        check(near(facingX(3f, 0f), 0f) && HitArcs.edge(facingX(3f, 0f)) == HitArcs.TOP, "ahead is the top edge: " + facingX(3f, 0f));
        check(near(facingX(0f, 2f), (float) Math.PI / 2f) && HitArcs.edge(facingX(0f, 2f)) == HitArcs.RIGHT, "to the right is the right edge");
        check(near(facingX(0f, -2f), -(float) Math.PI / 2f) && HitArcs.edge(facingX(0f, -2f)) == HitArcs.LEFT, "to the left is the left edge");
        check(near(Math.abs(facingX(-1f, 0f)), (float) Math.PI) && HitArcs.edge(facingX(-1f, 0f)) == HitArcs.BOTTOM, "behind is the bottom edge");
        check(HitArcs.up(facingX(-1f, 0f)) < -0.999f && Math.abs(HitArcs.across(facingX(-1f, 0f))) < 1e-3f, "and is drawn at the middle of the bottom");
        check(HitArcs.across(facingX(0f, -2f)) < -0.999f, "the left is drawn at the left");
    }

    private static void theBearingTurnsWithTheView() {
        // The attacker stands still to the view's left; the view turns a quarter left to face it, then on to have it on the right.
        float toX = 0f, toY = -2f;
        check(near(HitArcs.bearing(1f, 0f, 0f, 0f, 0f, toX, toY), -(float) Math.PI / 2f), "a quarter turn left before the view turns");
        check(near(HitArcs.bearing(0f, -1f, 0f, 0f, 0f, toX, toY), 0f), "ahead once the view faces it");
        check(near(HitArcs.bearing(-1f, 0f, 0f, 0f, 0f, toX, toY), (float) Math.PI / 2f), "to the right once the view has turned on past it");
    }

    /** The facing on the map and upward, and the top of the picture on the map, of a view facing +x on the map with its right +y, tipped by a pitch (down negative) and then rolled about its facing. */
    private static float[] tipped(float pitchDegrees, float rollDegrees) {
        double pitch = Math.toRadians(pitchDegrees), roll = Math.toRadians(rollDegrees);
        float[] forward = {(float) Math.cos(pitch), 0f, (float) Math.sin(pitch)};
        float[] level = {(float) -Math.sin(pitch), 0f, (float) Math.cos(pitch)}, right = {0f, 1f, 0f};
        float[] up = new float[3];
        for (int i = 0; i < 3; i++) {
            up[i] = (float) (level[i] * Math.cos(roll) + right[i] * Math.sin(roll));
        }
        return new float[] {forward[0], forward[1], forward[2], up[0], up[1]};
    }

    private static float bearingTipped(float[] view, float toX, float toY) {
        return HitArcs.bearing(view[0], view[1], view[2], view[3], view[4], toX, toY);
    }

    private static void aPitchedAndRolledViewKeepsItsBearings() {
        float[] view = tipped(-60f, 20f);
        check(near(Math.abs(bearingTipped(view, -1f, 0f)), (float) Math.PI), "looking 60 degrees down rolled 20, behind reads 180: " + Math.toDegrees(bearingTipped(view, -1f, 0f)));
        check(near(bearingTipped(view, 0f, -1f), -(float) Math.PI / 2f), "and the left reads the left: " + Math.toDegrees(bearingTipped(view, 0f, -1f)));
        check(near(bearingTipped(view, 1f, 0f), 0f), "and ahead reads ahead");
        float[] up = tipped(50f, -30f);
        check(near(Math.abs(bearingTipped(up, -1f, 0f)), (float) Math.PI) && near(bearingTipped(up, 0f, 1f), (float) Math.PI / 2f),
                "looking up and rolled, behind and the right are as when level");
    }

    private static void aViewStraightUpOrDownFacesTheWayItWouldTippedLevel() {
        // On the back, looking straight up with the top of the picture toward -x: tipped level about its right, the view faces the feet, +x.
        float[] onTheBack = tipped(90f, 0f);
        check(near(bearingTipped(onTheBack, 1f, 0f), 0f), "the feet read ahead: " + Math.toDegrees(bearingTipped(onTheBack, 1f, 0f)));
        check(near(Math.abs(bearingTipped(onTheBack, -1f, 0f)), (float) Math.PI), "past the head reads behind");
        check(near(bearingTipped(onTheBack, 0f, 1f), (float) Math.PI / 2f), "and what is on the picture's right reads the right: "
                + Math.toDegrees(bearingTipped(onTheBack, 0f, 1f)));
        float[] nearlyUp = tipped(85f, 90f), onTheSide = tipped(0f, 90f);
        check(!Float.isNaN(bearingTipped(nearlyUp, 1f, 1f)), "nearly straight up and rolled on the side still has a bearing");
        check(near(Math.abs(bearingTipped(onTheSide, -1f, 0f)), (float) Math.PI), "lying on the side looking level, behind reads 180");
        check(near(bearingTipped(tipped(-90f, 0f), 1f, 0f), 0f) && near(bearingTipped(tipped(-90f, 0f), 0f, 1f), (float) Math.PI / 2f),
                "face down, the top of the picture is ahead and the right the right");
    }

    /** The bearing lying down, with the top and bottom changed places as they are. */
    private static float lying(float[] view, boolean topBack, float toX, float toY) {
        float bearing = bearingTipped(view, toX, toY);
        return topBack ? HitArcs.turnedOver(bearing) : bearing;
    }

    private static boolean topBack(boolean did, float[] view) {
        return HitArcs.topPointsBack(did, HitArcs.topAlong(view[0], view[1], view[2], view[3], view[4]));
    }

    private static void lyingDownTheTopOfThePictureIsAhead() {
        // On the back, looking straight up with the top of the picture toward -x, past the head, and its right +y.
        float[] onTheBack = tipped(90f, 0f);
        boolean back = topBack(false, onTheBack);
        check(back, "on the back the top of the picture points back");
        check(near(lying(onTheBack, back, -1f, 0f), 0f) && HitArcs.edge(lying(onTheBack, back, -1f, 0f)) == HitArcs.TOP,
                "past the head is at the top: " + Math.toDegrees(lying(onTheBack, back, -1f, 0f)));
        check(near(Math.abs(lying(onTheBack, back, 1f, 0f)), (float) Math.PI), "the feet at the bottom");
        check(near(lying(onTheBack, back, 0f, 1f), (float) Math.PI / 2f), "what is on the picture's right at the right");
        check(near(lying(onTheBack, back, 0f, -1f), -(float) Math.PI / 2f), "and its left at the left");
        float[] faceDown = tipped(-90f, 0f);
        check(!topBack(true, faceDown) && near(lying(faceDown, false, 1f, 0f), 0f) && near(lying(faceDown, false, 0f, 1f), (float) Math.PI / 2f),
                "face down, the top of the picture points ahead, past the head is at the top and the right at the right");
        check(topBack(true, tipped(0f, 90f)) && !topBack(false, tipped(0f, 90f)) && topBack(true, tipped(0f, 0f)) && !topBack(false, tipped(0f, 0f)),
                "a level view, on the side or not, keeps whichever way it had");
    }

    private static void lyingDownTheTopAndBottomChangePlacesOnceAndOtherwiseMoveSmoothly() {
        float most = 0f;
        String where = "";
        for (int roll = -60; roll <= 60; roll += 5) {
            for (int sweep = 0; sweep < 2; sweep++) {
                boolean back = sweep == 1;
                int changes = 0;
                for (int step = 0; step < 179; step++) {
                    int pitch = sweep == 0 ? -89 + step : 89 - step, next = sweep == 0 ? pitch + 1 : pitch - 1;
                    boolean then = topBack(back, tipped(next, roll));
                    if (then != back) {
                        changes++;
                    } else {
                        for (int place = 0; place < 8; place++) {
                            float toX = (float) Math.cos(place * Math.PI / 4), toY = (float) Math.sin(place * Math.PI / 4);
                            float by = turned(lying(tipped(pitch, roll), back, toX, toY), lying(tipped(next, roll), back, toX, toY));
                            if (by > most) {
                                most = by;
                                where = "place " + place + ", pitch " + pitch + ", roll " + roll;
                            }
                        }
                    }
                    back = then;
                }
                check(changes == 1, "the top and bottom change places once from face down to on the back and once back again, roll " + roll + ": " + changes);
            }
        }
        check(Math.toDegrees(most) < 4.0, "between the changes a degree of pitch moves a bearing by at most " + Math.toDegrees(most) + " degrees, at " + where);
        boolean back = true;
        for (int wobble = 0; wobble < 20; wobble++) {
            back = topBack(back, tipped(wobble % 2 == 0 ? 18f : 22f, 0f));
        }
        check(back, "a head nodding a few degrees either side of where they changed places does not change them back");
    }

    private static float turned(float from, float to) {
        float by = Math.abs(to - from);
        return by > Math.PI ? (float) (2 * Math.PI) - by : by;
    }

    private static void aBearingMovesSmoothlyAsTheHeadPitchesAndRolls() {
        float most = 0f;
        String where = "";
        for (int place = 0; place < 8; place++) {
            float toX = (float) Math.cos(place * Math.PI / 4), toY = (float) Math.sin(place * Math.PI / 4);
            for (int roll = -60; roll <= 60; roll++) {
                for (int pitch = -89; pitch <= 89; pitch++) {
                    float here = bearingTipped(tipped(pitch, roll), toX, toY);
                    check(!Float.isNaN(here), "a bearing at pitch " + pitch + " roll " + roll);
                    float[] steps = {pitch < 89 ? turned(here, bearingTipped(tipped(pitch + 1, roll), toX, toY)) : 0f,
                            roll < 60 ? turned(here, bearingTipped(tipped(pitch, roll + 1), toX, toY)) : 0f};
                    for (float step : steps) {
                        if (step > most) {
                            most = step;
                            where = "place " + place + ", pitch " + pitch + ", roll " + roll;
                        }
                    }
                }
            }
        }
        check(Math.toDegrees(most) < 4.0, "a degree of pitch or roll turns a bearing by at most " + Math.toDegrees(most) + " degrees, at " + where);
    }

    private static void aMarkIsNamedForTheEdgeItIsDrawnNearest() {
        for (int degrees = -180; degrees < 180; degrees += 5) {
            float bearing = (float) Math.toRadians(degrees), x = HitArcs.across(bearing), y = HitArcs.up(bearing);
            // How far in from each edge the mark is drawn, as a share of half the picture: the edge named is the nearest.
            float[] inFrom = {1f - y, 1f - x, 1f + y, 1f + x};
            int nearest = 0;
            for (int edge = 1; edge < 4; edge++) {
                if (inFrom[edge] < inFrom[nearest]) {
                    nearest = edge;
                }
            }
            check(inFrom[HitArcs.edge(bearing)] <= inFrom[nearest] + 1e-6f, "at " + degrees + " degrees the edge named is " + HitArcs.EDGE_NAMES[HitArcs.edge(bearing)]
                    + ", drawn nearest the " + HitArcs.EDGE_NAMES[nearest]);
        }
        check(HitArcs.edge((float) Math.toRadians(150)) == HitArcs.BOTTOM && HitArcs.edge((float) Math.toRadians(120)) == HitArcs.RIGHT,
                "behind and to the right is drawn at the bottom, more to the right than behind at the right");
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
