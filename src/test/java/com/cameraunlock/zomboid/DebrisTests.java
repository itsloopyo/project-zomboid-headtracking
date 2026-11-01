package com.cameraunlock.zomboid;

import java.util.Random;

/** The pieces of what is knocked down or shot out, falling in the picture: see Debris and DebrisDraw. */
final class DebrisTests {
    private DebrisTests() {}

    static void run() {
        Checks.run("aPieceFallsBouncesOnceOrTwiceAndComesToRestOnTheGround", DebrisTests::aPieceFallsBouncesOnceOrTwiceAndComesToRestOnTheGround);
        Checks.run("aPieceLiesAWhileThenSinksAndIsLetGo", DebrisTests::aPieceLiesAWhileThenSinksAndIsLetGo);
        Checks.run("aBoardStandingOnEndTopplesOntoItsBroadestFace", DebrisTests::aBoardStandingOnEndTopplesOntoItsBroadestFace);
        Checks.run("aPieceSlidingOnTheGroundStopsAsFrictionSays", DebrisTests::aPieceSlidingOnTheGroundStopsAsFrictionSays);
        Checks.run("aHitchIsTakenAsAQuarterSecond", DebrisTests::aHitchIsTakenAsAQuarterSecond);
        Checks.run("thePoolHoldsItsMostAndANewPieceTakesTheOldestsPlace", DebrisTests::thePoolHoldsItsMostAndANewPieceTakesTheOldestsPlace);
        Checks.run("eachThingComesApartIntoItsOwnPiecesThrownTheWayTheVehicleWent", DebrisTests::eachThingComesApartIntoItsOwnPiecesThrownTheWayTheVehicleWent);
        Checks.run("everyPieceThrownComesToRestAndGoes", DebrisTests::everyPieceThrownComesToRestAndGoes);
        Checks.run("aSpriteIsMeasuredFromItsNorthCornerOrItsMiddle", DebrisTests::aSpriteIsMeasuredFromItsNorthCornerOrItsMiddle);
        Checks.run("whatAHoleCutsFreeIsWhatIsNewWithWhatTouchesAsOne", DebrisTests::whatAHoleCutsFreeIsWhatIsNewWithWhatTouchesAsOne);
        Checks.run("aPieceIsDrawnAsABoxAboutItsMiddle", DebrisTests::aPieceIsDrawnAsABoxAboutItsMiddle);
        Checks.run("aBoardShotLooseIsThrownOnceAndNotOnAReloadOrARebuild", DebrisTests::aBoardShotLooseIsThrownOnceAndNotOnAReloadOrARebuild);
        Checks.run("aPieceShotOutGoesTheWayTheShotWent", DebrisTests::aPieceShotOutGoesTheWayTheShotWent);
    }

    private static void check(boolean held, String what) {
        Checks.check(held, what);
    }

    private static final float[] FLAT = {1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};

    /** A board half 0.3 by 0.06 by 0.0125 tiles lying flat, its thin side up, at up over the ground, red. */
    private static int board(Debris.Pool pool, float up, float vx, float vy) {
        return pool.add(10f, 20f, 0f, up, FLAT, 0.3f, 0.06f, 0.0125f, vx, vy, 0f, 0f, 0f, 0f, 0.8f, 0.1f, 0.1f);
    }

    private static void aPieceFallsBouncesOnceOrTwiceAndComesToRestOnTheGround() {
        Debris.Pool pool = new Debris.Pool(8, new Random(1));
        int k = board(pool, 2f, 0f, 0f);
        int bounces = 0;
        float lowest = Float.MAX_VALUE;
        boolean wasFalling = false;
        for (int frame = 0; frame < 600 && !pool.resting[k]; frame++) {
            pool.advance(1f / 60f);
            lowest = Math.min(lowest, pool.up[k] - pool.reachDown(k));
            if (wasFalling && pool.vup[k] > 0f) {
                bounces++;
            }
            wasFalling = pool.vup[k] < -Debris.BOUNCES_FROM;
        }
        check(bounces == 1 || bounces == 2, "a board dropped two tiles flat bounces once or twice off the ground: " + bounces);
        check(pool.resting[k], "and comes to rest within ten seconds");
        check(lowest > -0.01f, "and never sinks into the ground while it falls: its foot went " + lowest + " under it");
        check(Math.abs(pool.up[k] - 0.0125f) < 1e-4f, "it lies with its middle half its thickness over the ground: " + pool.up[k]);
        check(pool.x[k] == 10f && pool.y[k] == 20f, "dropped straight, it lands where it was dropped");
    }

    private static void aPieceLiesAWhileThenSinksAndIsLetGo() {
        Debris.Pool pool = new Debris.Pool(8, new Random(2));
        int k = board(pool, 0.0125f, 0f, 0f);
        for (int frame = 0; frame < 60 && !pool.resting[k]; frame++) {
            pool.advance(1f / 60f);
        }
        check(pool.resting[k], "a board laid on the ground is soon at rest");
        float laid = pool.up[k];
        for (float seconds = 0f; seconds < Debris.LIES_LEAST - 0.5f; seconds += 0.25f) {
            pool.advance(0.25f);
        }
        check(pool.count == 1 && pool.up[k] == laid, "it lies where it came to rest at least LIES_LEAST seconds");
        for (float seconds = 0f; seconds < Debris.LIES_MOST + Debris.SINKS_FOR + 1f && pool.count > 0; seconds += 0.25f) {
            pool.advance(0.25f);
        }
        check(pool.count == 0, "then sinks into the ground within SINKS_FOR seconds and is let go");
        pool.advance(1f);
        check(pool.count == 0, "and an empty pool stays empty");
    }

    private static void aBoardStandingOnEndTopplesOntoItsBroadestFace() {
        Debris.Pool pool = new Debris.Pool(8, new Random(3));
        // Its length up, a little over towards +x, its thin side across x.
        float lean = 0.1f;
        float[] axes = new float[9];
        Debris.columns(axes, (float) Math.sin(lean), 0f, (float) Math.cos(lean), 0f, 1f, 0f);
        int k = pool.add(0f, 0f, 0f, 0.3f, axes, 0.3f, 0.06f, 0.0125f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f);
        for (int frame = 0; frame < 600 && !pool.resting[k]; frame++) {
            pool.advance(1f / 60f);
        }
        check(pool.resting[k], "a board stood on its end comes to rest");
        float thinUp = Math.abs(pool.axes[9 * k + 8]);
        check(thinUp > 0.999f, "lying on its broadest face, its thin side upright: " + thinUp);
        check(pool.axes[9 * k] > 0.9f, "having fallen the way it leaned: its length runs along +x " + pool.axes[9 * k]);
        check(Math.abs(pool.up[k] - 0.0125f) < 1e-3f, "and lies on the ground: " + pool.up[k]);
    }

    private static void aPieceSlidingOnTheGroundStopsAsFrictionSays() {
        Debris.Pool pool = new Debris.Pool(8, new Random(4));
        int k = board(pool, 0.0125f, 4f, 0f);
        for (int frame = 0; frame < 600 && !pool.resting[k]; frame++) {
            pool.advance(1f / 60f);
        }
        float slid = pool.x[k] - 10f, most = 16f / (2f * Debris.GRIP * Debris.GRAVITY);
        check(pool.resting[k], "a board sliding at 4 tiles a second comes to rest");
        check(slid > 0.5f * most && slid <= most, "having slid about as far as friction lets it: " + slid + " of at most " + most);
        check(pool.y[k] == 20f, "straight on");
    }

    private static void aHitchIsTakenAsAQuarterSecond() {
        Debris.Pool pool = new Debris.Pool(8, new Random(5));
        int k = board(pool, 10f, 0f, 0f);
        pool.advance(10f);
        float fell = 10f - pool.up[k], most = 0.5f * Debris.GRAVITY * Debris.STEP_MOST * Debris.STEP_MOST;
        check(fell > 0.8f * most && fell < 1.05f * most, "a step of ten seconds moves a piece by a quarter of one: it fell " + fell + " of " + most);
    }

    private static void thePoolHoldsItsMostAndANewPieceTakesTheOldestsPlace() {
        Debris.Pool pool = new Debris.Pool(4, new Random(6));
        for (int i = 0; i < 4; i++) {
            board(pool, 5f + i, 0f, 0f);
            pool.advance(0.1f);
        }
        check(pool.count == 4, "four pieces fill a pool of four");
        int slot = board(pool, 9f, 0f, 0f);
        check(pool.count == 4, "a fifth leaves it at four: " + pool.count);
        check(slot == 0 && pool.age[slot] == 0f && pool.up[slot] == 9f, "and takes the place of the first, the oldest: slot " + slot);
        for (int i = 0; i < 100; i++) {
            board(pool, 1f, 0f, 0f);
        }
        check(pool.count == 4, "however many more are thrown");
    }

    private static void eachThingComesApartIntoItsOwnPiecesThrownTheWayTheVehicleWent() {
        int[] expected = {Debris.SPAN_PIECES, Debris.FRAGMENT_PIECES, 1, 6};
        Debris.Shape[] shapes = {Debris.Shape.SPAN, Debris.Shape.FRAGMENTS, Debris.Shape.POLE, Debris.Shape.FRAME};
        for (int s = 0; s < shapes.length; s++) {
            Debris.Pool pool = new Debris.Pool(Debris.MOST, new Random(7 + s));
            float speed = shapes[s] == Debris.Shape.POLE ? Debris.POLE_BREAKS * 0.5f : 12f;
            pool.knockedDown(shapes[s], 100.5f, 200f, 0f, 1f, 0f, 1f, shapes[s] == Debris.Shape.POLE ? 5f : 1.2f, false, 0f, speed, 0.5f, 0.4f, 0.3f);
            check(pool.count == expected[s], shapes[s] + " comes apart into " + expected[s] + " pieces: " + pool.count);
            for (int k = 0; k < pool.count; k++) {
                check(pool.up[k] - pool.reachDown(k) > -0.02f, shapes[s] + "'s pieces start above the ground: piece " + k + " reaches " + (pool.up[k] - pool.reachDown(k)));
                check(pool.vy[k] > 0f && Math.abs(pool.vx[k]) < pool.vy[k], shapes[s] + "'s pieces go on the way the vehicle went: " + pool.vx[k] + ", " + pool.vy[k]);
                check(pool.colour[3 * k] == 0.5f && pool.colour[3 * k + 2] == 0.3f, "in the thing's colour");
            }
        }
        Debris.Pool fast = new Debris.Pool(Debris.MOST, new Random(11));
        fast.knockedDown(Debris.Shape.POLE, 0.5f, 0.5f, 0f, 1f, 0f, 1f, 5f, true, Debris.POLE_BREAKS * 2f, 0f, 0.4f, 0.4f, 0.4f);
        check(fast.count == 2, "a lamp post hit fast breaks in two: " + fast.count);
        check(Math.abs(2f * (fast.half[0] + fast.half[3]) - 5f) < 1e-4f, "whose lengths make the post's: " + 2f * (fast.half[0] + fast.half[3]));
        for (int frame = 0; frame < 240; frame++) {
            fast.advance(1f / 60f);
        }
        for (int k = 0; k < fast.count; k++) {
            check(fast.x[k] > 0.5f, "and goes over the way it was pushed: piece " + k + " at " + fast.x[k]);
            check(Math.abs(fast.axes[9 * k + 2]) < 0.2f, "and lies along the ground: piece " + k + " stands " + fast.axes[9 * k + 2]);
        }
    }

    private static void everyPieceThrownComesToRestAndGoes() {
        Debris.Pool pool = new Debris.Pool(Debris.MOST, new Random(12));
        for (int i = 0; i < 6; i++) {
            pool.knockedDown(Debris.Shape.SPAN, i, 0f, 0f, 1f, 0f, 1f, 1.2f, i % 2 == 0, 3f * i, 10f, 0.5f, 0.5f, 0.5f);
            pool.knockedDown(Debris.Shape.FRAME, i, 3f, 0f, 1f, 0f, 1f, 1.2f, false, -8f, 2f * i, 0.5f, 0.5f, 0.5f);
        }
        int thrown = pool.count;
        boolean allRest = false;
        float deepest = 0f, took = 0f;
        for (; took < 0.8f * Debris.LONGEST && !allRest; took += 1f / 60f) {
            pool.advance(1f / 60f);
            allRest = true;
            for (int k = 0; k < pool.count; k++) {
                allRest &= pool.resting[k];
                float foot = pool.up[k] - pool.reachDown(k);
                deepest = Float.isFinite(foot) ? Math.min(deepest, foot) : Float.NEGATIVE_INFINITY;
            }
        }
        check(deepest > -0.05f, "no piece goes into the ground as it lands: the deepest went " + deepest);
        check(thrown == 6 * (Debris.SPAN_PIECES + 6), "every piece was thrown: " + thrown);
        check(allRest, "every piece thrown comes to rest well before LONGEST: " + took + " s");
        for (float seconds = 0f; seconds < Debris.LIES_MOST + Debris.SINKS_FOR + 1f; seconds += 0.25f) {
            pool.advance(0.25f);
        }
        check(pool.count == 0, "and every one has gone soon after: " + pool.count + " left");
    }

    private static void aSpriteIsMeasuredFromItsNorthCornerOrItsMiddle() {
        float level = ViewCamera.LEVEL_HEIGHT;
        check(Math.abs(Debris.paintedHigh(64f, 128f, 0f, true) - level) < 1e-4f, "a sprite painted to its top on an edge is a level high");
        check(Math.abs(Debris.paintedHigh(64f, 128f, 48f, true) - 0.5f * level) < 1e-4f, "half way down, half a level");
        check(Math.abs(Debris.paintedHigh(128f, 256f, 96f, true) - 0.5f * level) < 1e-4f, "and the same at twice the size");
        check(Math.abs(Debris.paintedHigh(64f, 128f, 48f, false) - 64f / 96f * level) < 1e-4f, "a thing in the middle of its square is measured from the middle");
        check(Debris.paintedHigh(64f, 128f, 95f, true) == 0.1f * level, "nothing is lower than a tenth of a level");
    }

    private static void whatAHoleCutsFreeIsWhatIsNewWithWhatTouchesAsOne() {
        float[] old = rectangle(0.1f, 0.1f, 0.2f, 0.2f);
        Breakage.Pieces before = new Breakage.Pieces(new float[][] {old});
        Breakage.Pieces after = new Breakage.Pieces(new float[][] {old, rectangle(0.5f, 0.1f, 0.6f, 0.3f), rectangle(0.6f, 0.1f, 0.65f, 0.2f),
                rectangle(0.9f, 0.05f, 0.92f, 0.07f)});
        float[] boxes = Debris.loosened(before, after, 12);
        check(boxes.length == 8, "two new pieces: what went before is not thrown again, and two rectangles side by side are one: " + boxes.length / 4);
        check(boxes[0] == 0.5f && boxes[1] == 0.1f && boxes[2] == 0.65f && boxes[3] == 0.3f, "the two that touch as one box, the largest first");
        check(boxes[4] == 0.9f && boxes[6] == 0.92f, "then the small one apart");
        check(Debris.loosened(before, after, 1).length == 4, "and no more than asked for");
        check(Debris.loosened(after, after, 12).length == 0, "nothing new, nothing thrown");
    }

    /** A fence 0.6 of a level high whose fourth board runs from 0.45 to 0.6 along it. */
    private static final Breakage.Panel FENCE = new Breakage.Panel(Breakage.BOARDS, 0f, 1f, 0f, 0.6f, 0, Breakage.HELD_ALL, Breakage.NO_FRAMES);

    private static void aBoardShotLooseIsThrownOnceAndNotOnAReloadOrARebuild() {
        float low = 0.45f / ViewCamera.LEVEL_HEIGHT, high = 1.0f / ViewCamera.LEVEL_HEIGHT, between = (low + high) / 2f;
        float[] one = BlastHoles.with(new float[0], true, 0.525f, low, 0.07f, 21, 0);
        Breakage.Pieces first = Breakage.of(one, true, FENCE);
        check(!holds(Debris.loosened(Breakage.Pieces.NONE, first, Debris.SHOT_PIECES), 0.525f, between),
                "a board broken at one end only still stands, so its length is not thrown");
        float[] two = BlastHoles.with(one, true, 0.525f, high, 0.07f, 22, 0);
        Breakage.Pieces second = Breakage.of(two, true, FENCE);
        check(holds(Debris.loosened(first, second, Debris.SHOT_PIECES), 0.525f, between), "broken at its other end, the length between falls and is thrown");
        Breakage.Pieces reloaded = Breakage.of(two.clone(), true, FENCE);
        check(Debris.loosened(second, reloaded, Debris.SHOT_PIECES).length == 0, "the same holes read back from a save throw nothing again");
        check(Debris.loosened(second, Breakage.of(two, true, FENCE), Debris.SHOT_PIECES).length == 0, "nor does the fence built again from them");
        float[] four = BlastHoles.with(BlastHoles.with(two, true, 0.225f, low, 0.07f, 23, 0), true, 0.225f, high, 0.07f, 24, 0);
        float[] next = Debris.loosened(second, Breakage.of(four, true, FENCE), Debris.SHOT_PIECES);
        check(holds(next, 0.225f, between) && !holds(next, 0.525f, between), "a second board shot loose throws that board, not the one that fell before it");
    }

    /** Whether one of the boxes loosened gives holds the point along and up, in levels. */
    private static boolean holds(float[] boxes, float along, float up) {
        for (int at = 0; at < boxes.length; at += 4) {
            if (boxes[at] <= along && along <= boxes[at + 2] && boxes[at + 1] <= up && up <= boxes[at + 3]) {
                return true;
            }
        }
        return false;
    }

    private static void aPieceShotOutGoesTheWayTheShotWent() {
        float[] way = new float[3];
        Debris.shotWay(true, -1, 0f, 0f, way);
        check(way[0] == 0f && way[1] == 1f && way[2] == 0f, "shot through a north edge from the lower y side, it goes to higher y");
        Debris.shotWay(true, 1, 0f, 0f, way);
        check(way[1] == -1f, "and from the higher y side, to lower y");
        Debris.shotWay(false, -1, 0f, 0f, way);
        check(way[0] == 1f && way[1] == 0f, "through a west edge from the lower x side, it goes to higher x");
        Debris.shotWay(false, 1, 0.5f, -0.25f, way);
        float length = (float) Math.sqrt(1f + 0.25f + 0.0625f);
        check(Math.abs(way[0] + 1f / length) < 1e-6f && Math.abs(way[1] + 0.5f / length) < 1e-6f && Math.abs(way[2] - 0.25f / length) < 1e-6f,
                "a round's slant along and up is kept for every tile across it went: " + way[0] + "," + way[1] + "," + way[2]);

        Debris.Pool pool = new Debris.Pool(8, new Random(5));
        Debris.shotWay(true, -1, 0f, 0f, way);
        pool.shotLoose(10.5f, 20f, 0f, 0.6f, 1f, 0f, 0.075f, 0.3f, Debris.BOARD_HALF, way[0], way[1], way[2], false, 0.5f, 0.4f, 0.3f);
        check(pool.count == 1 && pool.vy[0] >= Debris.ROUND_PUSH_LEAST && Math.abs(pool.vx[0]) <= Debris.SHOT_ASIDE * pool.vy[0] + 1e-6f,
                "a board a round shot out is pushed off the way the round went, a little along the fence besides: " + pool.vx[0] + "," + pool.vy[0]);
        pool.advance(0.1f);
        check(pool.axes[4] > 0.05f, "and its top tips over that way first: its up axis leans " + pool.axes[4] + " to higher y");
        for (int frame = 0; frame < 120; frame++) {
            pool.advance(1f / 60f);
        }
        check(pool.y[0] > 20.1f, "so it lands on the far side of the fence from the shooter: " + pool.y[0]);
    }

    private static float[] rectangle(float a0, float u0, float a1, float u1) {
        return new float[] {a0, u0, a1, u0, a1, u1, a0, u1};
    }

    private static void aPieceIsDrawnAsABoxAboutItsMiddle() {
        Debris.Pool pool = new Debris.Pool(2, new Random(13));
        float[] axes = new float[9];
        float c = (float) Math.cos(0.4), s = (float) Math.sin(0.4);
        Debris.columns(axes, c, s, 0f, -s * 0.6f, c * 0.6f, 0.8f);
        int k = pool.add(3f, 4f, 2f, 0.5f, axes, 0.3f, 0.05f, 0.01f, 0f, 0f, 0f, 0f, 0f, 0f, 0.2f, 0.4f, 0.6f);
        float[] record = new float[Debris.FLOATS];
        pool.write(k, record, 0, 2f);
        check(record[Debris.X] == 3f && record[Debris.Y] == 4f && record[Debris.UP] == 2.5f, "a piece is drawn at its middle over its ground");
        float[] corners = new float[DebrisDraw.CORNERS * DebrisDraw.FLOATS_A_CORNER];
        int floats = DebrisDraw.fill(record, 1, corners);
        check(floats == corners.length, "six faces of four corners: " + floats);
        float[] halves = {0.3f, 0.05f, 0.01f};
        for (int corner = 0; corner < DebrisDraw.CORNERS; corner++) {
            int at = corner * DebrisDraw.FLOATS_A_CORNER;
            float nx = corners[at + 3], ny = corners[at + 4], nz = corners[at + 5];
            check(Math.abs(nx * nx + ny * ny + nz * nz - 1f) < 1e-4f, "each corner's face looks one way, a unit way");
            float dx = corners[at] - 3f, dy = corners[at + 1] - 4f, dz = corners[at + 2] - 2.5f;
            int face = corner / 8;
            check(Math.abs(dx * nx + dy * ny + dz * nz - halves[face]) < 1e-4f, "and its face is half the box out from the middle that way: corner " + corner);
            for (int axis = 0; axis < 3; axis++) {
                float along = dx * axes[3 * axis] + dy * axes[3 * axis + 1] + dz * axes[3 * axis + 2];
                check(Math.abs(Math.abs(along) - halves[axis]) < 1e-4f, "every corner is a corner of the box: corner " + corner + " axis " + axis);
            }
            check(corners[at + 6] == 0.2f && corners[at + 7] == 0.4f && corners[at + 8] == 0.6f, "in the piece's colour");
        }
    }
}
