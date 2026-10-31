package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoObjectType;

/**
 * What goes with the holes shot through a thing (Breakage): what is left with nothing to hold it
 * falls out, a board shot most of the way through breaks across, and what is still held stays.
 */
final class BreakageTests {
    private BreakageTests() {}

    static void run() {
        Checks.run("aPieceRingedByHolesFallsOutOfASheet", BreakageTests::aPieceRingedByHolesFallsOutOfASheet);
        Checks.run("oneHoleInASheetTakesNothingFarFromIt", BreakageTests::oneHoleInASheetTakesNothingFarFromIt);
        Checks.run("aBoardShotMostOfTheWayThroughBreaksAcross", BreakageTests::aBoardShotMostOfTheWayThroughBreaksAcross);
        Checks.run("aLengthOfBoardBrokenAtBothEndsFallsOut", BreakageTests::aLengthOfBoardBrokenAtBothEndsFallsOut);
        Checks.run("whatGoesIsTheSameEveryTime", BreakageTests::whatGoesIsTheSameEveryTime);
        Checks.run("pastTheMostHolesOneTheOthersCoverGoesFirst", BreakageTests::pastTheMostHolesOneTheOthersCoverGoesFirst);
        Checks.run("whatWentIsCutOutOfAFenceThatHasBeenShotToPieces", BreakageTests::whatWentIsCutOutOfAFenceThatHasBeenShotToPieces);
        Checks.run("aPieceShotFreeAcrossTheJoinOfTwoWallsFallsOutOfBoth", BreakageTests::aPieceShotFreeAcrossTheJoinOfTwoWallsFallsOutOfBoth);
        Checks.run("aLeafCutFromItsHingesFallsAndWhatHangsFromThemStays", BreakageTests::aLeafCutFromItsHingesFallsAndWhatHangsFromThemStays);
        Checks.run("theFrameOfAWindowHoldsTheWallRoundIt", BreakageTests::theFrameOfAWindowHoldsTheWallRoundIt);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static final Breakage.Panel SHEET = sheet(Breakage.NO_FRAMES);
    /** A fence 0.6 of a level high whose edge starts at a whole number of boards along the map: its fourth board runs from 0.45 to 0.6. */
    private static final Breakage.Panel FENCE = fence(0.6f, 0);

    /** A wall a square long and a level high, held all round and by the frames of the openings through it. */
    private static Breakage.Panel sheet(float[] frames) {
        return new Breakage.Panel(Breakage.SHEET, 0f, 1f, 0f, 1f, 10816, Breakage.HELD_ALL, frames);
    }

    private static Breakage.Panel fence(float top, int startsAt) {
        return new Breakage.Panel(Breakage.BOARDS, 0f, 1f, 0f, top, startsAt, Breakage.HELD_ALL, Breakage.NO_FRAMES);
    }

    /** What goes of a thing alone, worked out afresh. */
    private static Breakage.Pieces work(float[] entries, boolean north, Breakage.Panel panel) {
        return Breakage.work(new float[][] {entries}, north, panel)[0];
    }

    /** Twelve holes of that radius in a ring round (along, up) on a north edge, ringRadius tiles out, with seeds from seed. */
    private static float[] ring(float along, float up, float ringRadius, float radius, int seed) {
        return ring(along, up, ringRadius, radius, seed, 0f);
    }

    /** The same, turned on by that share of the way from one hole to the next. */
    private static float[] ring(float along, float up, float ringRadius, float radius, int seed, float turned) {
        float[] entries = new float[0];
        for (int i = 0; i < 12; i++) {
            double angle = 2 * Math.PI * (i + turned) / 12;
            entries = BlastHoles.with(entries, true, along + ringRadius * (float) Math.cos(angle), up + levels(ringRadius * (float) Math.sin(angle)), radius, seed + i, 0);
        }
        return entries;
    }

    private static float levels(float tiles) {
        return tiles / ViewCamera.LEVEL_HEIGHT;
    }

    private static void aPieceRingedByHolesFallsOutOfASheet() {
        float middleUp = levels(1.2f);
        float[] entries = ring(0.5f, middleUp, 0.25f, 0.08f, 100);
        Breakage.Pieces pieces = work(entries, true, SHEET);
        check(pieces.inside(0.5f, middleUp), "the piece inside the ring of holes falls out");
        check(pieces.inside(0.42f, middleUp + levels(0.05f)), "all of it");
        check(!pieces.inside(0.5f, levels(0.3f)) && !pieces.inside(0.08f, middleUp) && !pieces.inside(0.92f, levels(2f)),
                "what is outside the ring stays");
        check(work(entries, false, SHEET).rectangles.length == 0, "the holes of one edge leave the other edge whole");
    }

    private static void oneHoleInASheetTakesNothingFarFromIt() {
        float[] entries = BlastHoles.with(new float[0], true, 0.5f, levels(1.2f), 0.1f, 7, 0);
        Breakage.Pieces pieces = work(entries, true, SHEET);
        float near = 0.1f * 1.25f * 1.3f + Breakage.BRIDGE * Breakage.CELL;
        for (float[] r : pieces.rectangles) {
            check(r[0] >= 0.5f - near && r[2] <= 0.5f + near && r[1] * ViewCamera.LEVEL_HEIGHT >= 1.2f - near && r[5] * ViewCamera.LEVEL_HEIGHT <= 1.2f + near,
                    "only crumbs at the hole's ragged rim go with one hole, at " + Arrays.toString(r));
        }
    }

    private static void aBoardShotMostOfTheWayThroughBreaksAcross() {
        float up = levels(0.7f);
        float[] entries = BlastHoles.with(new float[0], true, 0.525f, up, 0.07f, 11, 0);
        Breakage.Pieces pieces = work(entries, true, FENCE);
        boolean across = true;
        for (float along = 0.451f; along < 0.6f; along += 0.005f) {
            across &= pieces.inside(along, up) || BlastHoles.inAHole(entries, true, along, up);
        }
        check(across && pieces.inside(0.452f, up), "the board the hole took most of breaks right across beside it");
        check(!pieces.inside(0.40f, up) && !pieces.inside(0.65f, up), "the boards either side, which the hole did not reach, stand");
        check(!pieces.inside(0.525f, levels(0.2f)) && !pieces.inside(0.525f, levels(1.3f)), "and the broken board stands on below and above the break, held at its ends");
    }

    private static void aLengthOfBoardBrokenAtBothEndsFallsOut() {
        float low = levels(0.45f), high = levels(1.0f);
        float[] entries = BlastHoles.with(new float[0], true, 0.525f, low, 0.07f, 21, 0);
        entries = BlastHoles.with(entries, true, 0.525f, high, 0.07f, 22, 0);
        Breakage.Pieces pieces = work(entries, true, FENCE);
        float between = (low + high) / 2f;
        check(pieces.inside(0.525f, between) && pieces.inside(0.46f, between), "the length between the two breaks falls out");
        check(!pieces.inside(0.375f, between) && !pieces.inside(0.675f, between), "the boards beside it stand");
    }

    private static void whatGoesIsTheSameEveryTime() {
        float[] entries = BlastHoles.with(new float[0], true, 0.525f, levels(0.45f), 0.06f, 21, 0);
        entries = BlastHoles.with(entries, true, 0.33f, levels(1.0f), 0.07f, 23, 0);
        float[][] once = work(entries, true, FENCE).rectangles, again = work(entries.clone(), true, FENCE).rectangles;
        check(Arrays.deepEquals(once, again), "the same holes break a fence the same way on every load and on every thread");
        check(Breakage.of(entries, true, FENCE) == Breakage.of(entries, true, FENCE), "and it is worked out once for a list of holes");
    }

    private static void pastTheMostHolesOneTheOthersCoverGoesFirst() {
        float[] entries = BlastHoles.with(new float[0], true, 5f, 0.4f, 0.05f, 1, 0);
        entries = BlastHoles.with(entries, true, 0.5f, 0.4f, 0.03f, 2, 0);
        entries = BlastHoles.with(entries, true, 0.5f, 0.4f, 0.14f, 3, 0);
        for (int i = 3; i < BlastHoles.MOST_HOLES; i++) {
            entries = BlastHoles.with(entries, true, 10f + i, 0.4f, 0.05f, 100 + i, 0);
        }
        entries = BlastHoles.with(entries, true, 50f, 0.4f, 0.05f, 999, 0);
        boolean oldest = false, covered = false;
        for (int at = 0; at < entries.length; at += BlastHoles.FLOATS) {
            oldest |= entries[at + BlastHoles.SEED] == 1f;
            covered |= entries[at + BlastHoles.SEED] == 2f;
        }
        check(entries.length == BlastHoles.MOST_HOLES * BlastHoles.FLOATS, "an object keeps the most holes");
        check(oldest && !covered, "the hole inside a bigger one went, and the oldest stayed");
    }

    /**
     * Fences shot to pieces are cut, both skins, without a throw: the ring and the spiral the rig
     * shot at 10793 and 10795, 9779 (the first threw where two broken pieces met on one line), and
     * blasts laid at random over a fence of boards and a sheet.
     */
    private static void whatWentIsCutOutOfAFenceThatHasBeenShotToPieces() {
        float[] ring = new float[0];
        for (int i = 0; i < 12; i++) {
            double angle = 2 * Math.PI * i / 12;
            ring = BlastHoles.with(ring, true, 0.5f + 0.25f * (float) Math.cos(angle), 0.4f + 0.25f * (float) Math.sin(angle) / ViewCamera.LEVEL_HEIGHT, 0.08f, 100 + i, 0);
        }
        float[] spiral = new float[0];
        double turn = 0;
        for (int k = 0; 0.035 * turn <= 0.32; k++) {
            double r = 0.035 * turn;
            spiral = BlastHoles.with(spiral, true, 0.5f + (float) (r * Math.cos(turn)), 0.4f + (float) (r * Math.sin(turn)) / ViewCamera.LEVEL_HEIGHT, 0.09f, 300 + k, 0);
            turn += 0.11 / Math.max(r, 0.11);
        }
        check(cutThrough(ring, fence(1f, 10793)) == null, "the ring of holes is cut");
        check(cutThrough(spiral, fence(1f, 10795)) == null, "the spiral of holes is cut");
        java.util.Random random = new java.util.Random(77);
        int threw = 0;
        String first = null;
        for (int run = 0; run < 400; run++) {
            float[] entries = new float[0];
            int count = 3 + random.nextInt(20);
            for (int h = 0; h < count; h++) {
                entries = BlastHoles.with(entries, true, random.nextFloat(), random.nextFloat() * 0.8f, 0.04f + random.nextFloat() * 0.1f, random.nextInt(1 << 20), 0);
            }
            Breakage.Panel panel = run % 2 == 0 ? fence(0.6f + random.nextFloat() * 0.4f, random.nextInt(20000)) : SHEET;
            String failed = cutThrough(entries, panel);
            if (failed != null) {
                threw++;
                first = first == null ? "run " + run + ": " + failed : first;
            }
        }
        check(threw == 0, threw + " of 400 shot up fences threw, the first " + first);
    }

    /** Cuts the holes and what went with them out of the panel built as two skins of a north edge, as WallBuilder lays a fence: what threw, or null. */
    private static String cutThrough(float[] entries, Breakage.Panel panel) {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        float top = panel.upTo() * ViewCamera.LEVEL_HEIGHT;
        for (float across : new float[] {-0.02f, 0.02f}) {
            float[][] corners = {{0f, 0f}, {1f, 0f}, {1f, top}, {0f, 0f}, {1f, top}, {0f, top}};
            for (float[] c : corners) {
                float[] v = new float[SpriteGeometry.VERTEX_FLOATS];
                v[0] = c[0];
                v[1] = across;
                v[2] = c[1];
                v[5] = 1f;
                v[8] = 3f;
                mesh.addVertex(v);
            }
        }
        MeshCut cut = new MeshCut(0f, 0f, 1f, 0f, 0.08f);
        BlastHoles.into(cut, entries, Breakage.of(entries, true, panel), true, 0f, 1f, 0f, ViewCamera.LEVEL_HEIGHT, 1);
        try {
            cut.cut(mesh, 0);
            cut.finish();
            return null;
        } catch (IllegalStateException e) {
            return e.getMessage() + (e.getCause() == null ? "" : " / " + e.getCause().getMessage());
        }
    }

    /** A run's holes, along it from the first square's corner, kept by the square of the run each struck alone, along its own square. */
    private static float[][] keptAlone(float[] run, int squares) {
        float[][] each = new float[squares][0];
        for (int at = 0; at < run.length; at += BlastHoles.FLOATS) {
            int square = (int) Math.floor(run[at + BlastHoles.ALONG]);
            each[square] = BlastHoles.with(each[square], true, run[at + BlastHoles.ALONG] - square, run[at + BlastHoles.UP], run[at + BlastHoles.RADIUS],
                    (int) run[at + BlastHoles.SEED], 0);
        }
        return each;
    }

    /**
     * A run's blasts, along it from the first square's corner, kept by the walls of the run as BlastHoles.add keeps them on
     * plasterboard: by the square of the run each struck, along its own square, and by the next wall along where it reaches
     * over the join into it (BlastHoles.reachedOver), moved along by the square.
     */
    private static float[][] struck(IsoObject[] walls, float[] run) {
        float[][] each = new float[walls.length][0];
        for (int at = 0; at < run.length; at += BlastHoles.FLOATS) {
            int square = (int) Math.floor(run[at + BlastHoles.ALONG]);
            float along = run[at + BlastHoles.ALONG] - square, radius = run[at + BlastHoles.RADIUS];
            each[square] = BlastHoles.with(each[square], true, along, run[at + BlastHoles.UP], radius, (int) run[at + BlastHoles.SEED], 0);
            for (int by = -1; by <= 1; by += 2) {
                IsoObject next = BlastHoles.reachedOver(walls[square], true, along, BlastHoles.reachOf(radius, 0, 0f), by);
                if (next != null) {
                    int to = Arrays.asList(walls).indexOf(next);
                    check(to == square + by, "a blast reaches over into the next wall along, not into wall " + to);
                    each[to] = BlastHoles.with(each[to], true, along - by, run[at + BlastHoles.UP], radius, (int) run[at + BlastHoles.SEED], 0);
                }
            }
        }
        return each;
    }

    /**
     * A ring of blasts shot round the join of two squares of plasterboard wall, as on the motel's wall at (7253, 8280)
     * and (7254, 8280): each square keeps the holes that struck it, and what the ring cut free is held by neither
     * square's floor, ceiling nor the ends of the wall, so it falls out of both. Each square alone, held all round,
     * had it held at the join. A blast that reaches over the join is cut out of both squares, which both keep it, so the
     * ring's piece falls whichever square its holes at the join struck; kept by the square it struck alone, a ring whose
     * holes at the join all struck the one square left a strip of the other's wall standing there, which held it.
     */
    private static void aPieceShotFreeAcrossTheJoinOfTwoWallsFallsOutOfBoth() throws Throwable {
        TestMap map = new TestMap();
        IsoObject[] walls = new IsoObject[3];
        for (int x = 0; x < 3; x++) {
            walls[x] = map.cell.square(x, 1, 0).add(map.sprite("walls_interior_house_01_1", IsoObjectType.wall, "WallN"));
        }
        float middleUp = levels(1.2f);
        float[][] struck = struck(walls, ring(1f, middleUp, 0.25f, 0.08f, 200, 0.5f));
        float[] first = struck[0], second = struck[1];
        check(first.length > 0 && second.length > 0 && struck[2].length == 0, "each square keeps the holes that struck it, and the square beyond none");
        Breakage.Panel two = new Breakage.Panel(Breakage.SHEET, 0f, 2f, 0f, 1f, 0, Breakage.HELD_ALL, Breakage.NO_FRAMES);
        float[] oneSided = ring(1f, middleUp, 0.25f, 0.08f, 200);
        check(!Breakage.work(keptAlone(oneSided, 2), true, two)[0].inside(0.95f, middleUp),
                "a ring whose holes at the join all struck the one square, each kept by the square it struck alone, is held by the other's strip");
        Breakage.Pieces[] keptByBoth = Breakage.work(struck(walls, oneSided), true, two);
        check(keptByBoth[0].inside(0.95f, middleUp) && keptByBoth[1].inside(0.05f, middleUp),
                "a ring whose holes at the join all struck the one square falls out of both, those holes being cut out of both");
        check(BlastHoles.reachedOver(walls[0], true, 0.9f, 0.08f, 1) == null && BlastHoles.reachedOver(walls[1], true, 0.03f, 0.05f, -1) == walls[0]
                && BlastHoles.reachedOver(walls[1], true, 0.97f, 0.05f, 1) == walls[2] && BlastHoles.reachedOver(walls[0], true, 0.03f, 0.05f, -1) == null,
                "a hole reaches into the next wall along only past its own square's end, and only where there is one");
        check(BlastHoles.reachedOver(walls[1], false, 0.03f, 0.05f, -1) == null, "nor along an edge the wall does not stand on");
        check(!work(first, true, SHEET).inside(0.95f, middleUp) && !work(second, true, SHEET).inside(0.05f, middleUp),
                "worked out a square alone, the piece is held at the join");
        Map<IsoObject, float[]> kept = TestMap.read(null, BlastHoles.class, "kept");
        Field any = BlastHoles.class.getDeclaredField("any");
        any.setAccessible(true);
        try {
            kept.put(walls[0], first);
            kept.put(walls[1], second);
            any.setBoolean(null, true);
            Breakage.Pieces left = BlastHoles.fallen(walls[0], true), right = BlastHoles.fallen(walls[1], true);
            check(left.inside(0.95f, middleUp) && left.inside(0.85f, middleUp + levels(0.08f)), "the piece falls out of the first square");
            check(right.inside(0.05f, middleUp) && right.inside(0.15f, middleUp - levels(0.08f)), "and out of the second");
            check(BlastHoles.through(walls[0], true, 0.97f, middleUp) && BlastHoles.through(walls[1], true, 0.03f, middleUp), "a shot goes through where it was");
            check(!left.inside(0.4f, middleUp) && !right.inside(0.6f, middleUp) && !left.inside(0.95f, levels(0.3f)), "what is outside the ring stays");
            check(BlastHoles.fallen(walls[2], true) == Breakage.Pieces.NONE, "the square beyond, with no hole, stays whole");
            check(BlastHoles.fallen(walls[0], false) == Breakage.Pieces.NONE, "and the holes of the north edges leave nothing of a west edge to fall");
        } finally {
            BlastHoles.forget();
        }
    }

    /**
     * A door's leaf hangs from its hinges at the end away from its handle: a line of blasts down the leaf from its head to its foot cuts
     * what is on the handle's side of it from them, which falls, and what is on the hinges' side stays hung.
     */
    private static void aLeafCutFromItsHingesFallsAndWhatHangsFromThemStays() {
        Doors.Shape door = new Doors.Shape();
        door.leaf = new Windows.Shape();
        door.leaf.north = true;
        door.leaf.start = 0.1f;
        door.leaf.end = 0.9f;
        door.leaf.foot = 0f;
        door.leaf.head = 0.85f;
        float[] line = new float[0];
        for (int i = 0; i <= 17; i++) {
            line = BlastHoles.with(line, true, 0.35f, i * 0.05f, 0.08f, 400 + i, 0);
        }
        float middle = 0.4f;
        door.handleAtEnd = true;
        Breakage.Pieces hungAtStart = work(line, true, BlastHoles.leafPanel(door));
        check(hungAtStart.inside(0.6f, middle) && hungAtStart.inside(0.88f, 0.8f), "with the hinges at its start, all the leaf past the line falls");
        check(!hungAtStart.inside(0.2f, middle), "and what hangs from them stays");
        door.handleAtEnd = false;
        Breakage.Pieces hungAtEnd = work(line, true, BlastHoles.leafPanel(door));
        check(hungAtEnd.inside(0.2f, middle) && !hungAtEnd.inside(0.6f, middle), "hung at its end, the leaf keeps what is past the line and drops the rest");
        float[] across = new float[0];
        for (int i = 0; i <= 10; i++) {
            across = BlastHoles.with(across, true, 0.1f + i * 0.08f, 0.4f, 0.07f, 500 + i, 0);
        }
        Breakage.Pieces split = work(across, true, BlastHoles.leafPanel(door));
        check(!split.inside(0.5f, 0.7f) && !split.inside(0.5f, 0.1f), "a line across the leaf leaves both halves on the hinges");
    }

    /**
     * A window's frame is fixed in the wall's frame, and holds the plasterboard round it: a ring of blasts round a window
     * drops nothing, where round a stretch of plain wall it drops what it rings. The frame is the opening WallBuilder read
     * off the wall's sprite (openingIn).
     */
    private static void theFrameOfAWindowHoldsTheWallRoundIt() {
        float middleUp = levels(1.3f);
        float[] round = ring(0.5f, middleUp, 0.3f, 0.08f, 600);
        check(work(round, true, SHEET).inside(0.5f, middleUp), "round plain wall the ring drops what it rings");
        float[] window = {0.38f, 0.62f, middleUp - levels(0.12f), middleUp + levels(0.12f)};
        Breakage.Pieces framed = work(round, true, sheet(window));
        check(!framed.inside(0.5f, middleUp) && !framed.inside(0.3f, middleUp), "round a window it drops nothing: the frame holds the wall it meets");
        float[] offToOneSide = {0.85f, 0.95f, middleUp - levels(0.12f), middleUp + levels(0.12f)};
        check(work(round, true, sheet(offToOneSide)).inside(0.5f, middleUp), "a window outside the ring holds nothing inside it");
    }
}
