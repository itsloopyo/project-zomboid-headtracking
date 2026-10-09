package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Tests of what a painted road line is read as: PaintFit's outline of a sprite laid flat, and RoadPaint's laying of it. */
final class RoadPaintTests {
    private static final int SIDE = RoadPaint.SIDE, MARGIN = RoadPaint.MARGIN, WIDE = SIDE + 2 * MARGIN;

    private RoadPaintTests() {}

    private interface Painted {
        boolean at(double x, double y);
    }

    static void run() {
        Checks.run("stripeWithAStaircaseEdgeIsOneStraightStripeOnTheSpritesDots", RoadPaintTests::stripeWithAStaircaseEdgeIsOneStraightStripeOnTheSpritesDots);
        Checks.run("stripeRunsOffItsSquareWithNoEnd", RoadPaintTests::stripeRunsOffItsSquareWithNoEnd);
        Checks.run("barThatStopsShortHasAnEdgeThere", RoadPaintTests::barThatStopsShortHasAnEdgeThere);
        Checks.run("barDrawnOffItsSquareMeetsTheNextLengthOfItself", RoadPaintTests::barDrawnOffItsSquareMeetsTheNextLengthOfItself);
        Checks.run("slantedStripeMeetsItselfInTheNextSquare", RoadPaintTests::slantedStripeMeetsItselfInTheNextSquare);
        Checks.run("triangleKeepsItsThreeCorners", RoadPaintTests::triangleKeepsItsThreeCorners);
        Checks.run("wholeSquareAndHoleAndHairlineAreNotRead", RoadPaintTests::wholeSquareAndHoleAndHairlineAreNotRead);
        Checks.run("spritesDiamondIsReadFromItsOwnMiddle", RoadPaintTests::spritesDiamondIsReadFromItsOwnMiddle);
        Checks.run("paintIsLaidWithWhatItsCornersCarry", RoadPaintTests::paintIsLaidWithWhatItsCornersCarry);
        Checks.run("layGivesThePaintItsMaterialAndTheFrameItsOwnBack", RoadPaintTests::layGivesThePaintItsMaterialAndTheFrameItsOwnBack);
        Checks.run("noPointOfThePaintIsCoatedTwice", RoadPaintTests::noPointOfThePaintIsCoatedTwice);
        Checks.run("everyCornerIsANumber", RoadPaintTests::everyCornerIsANumber);
        Checks.run("onlyMarkingsAreRead", RoadPaintTests::onlyMarkingsAreRead);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** Dots, each as solid as the share of it that is painted, x and y in dots from the square's corner. */
    private static float[] cover(Painted painted) {
        float[] cover = new float[WIDE * WIDE];
        for (int i = 0; i < WIDE; i++) {
            for (int j = 0; j < WIDE; j++) {
                int inside = 0;
                for (int part = 0; part < 64; part++) {
                    inside += painted.at(i - MARGIN + (part % 8 + 0.5) / 8.0, j - MARGIN + (part / 8 + 0.5) / 8.0) ? 1 : 0;
                }
                cover[i * WIDE + j] = inside / 64f;
            }
        }
        return cover;
    }

    /** Whether the point, in tiles, is inside the paint of triangles as PaintFit makes them. */
    private static boolean inside(float[] triangles, double x, double y) {
        for (int at = 0; at < triangles.length; at += 3 * PaintFit.CORNER) {
            double ax = triangles[at], ay = triangles[at + 1], bx = triangles[at + 4], by = triangles[at + 5], cx = triangles[at + 8], cy = triangles[at + 9];
            double twice = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
            double a = ((bx - x) * (cy - y) - (by - y) * (cx - x)) / twice, b = ((cx - x) * (ay - y) - (cy - y) * (ax - x)) / twice, c = 1.0 - a - b;
            if (a >= -1e-9 && b >= -1e-9 && c >= -1e-9 && a * triangles[at + 2] + b * triangles[at + 6] + c * triangles[at + 10] >= -1e-9) {
                return true;
            }
        }
        return false;
    }

    /** The painted stretches of the line across the square at one place along it, in dots: along x at y when alongX, else along y at x. Each as from and to. */
    private static double[] crossed(float[] triangles, boolean alongX, double at) {
        double[] found = new double[16];
        int count = 0;
        boolean was = false;
        int steps = SIDE * 64;
        for (int k = 0; k <= steps; k++) {
            double place = (double) k / steps;
            boolean is = k < steps && (alongX ? inside(triangles, place, at / SIDE) : inside(triangles, at / SIDE, place));
            if (is != was) {
                found[count++] = place * SIDE;
                was = is;
            }
        }
        return Arrays.copyOf(found, count);
    }

    private static void near(double value, double wanted, double within, String what) {
        check(Math.abs(value - wanted) <= within, what + ": " + value + " where " + wanted + " was wanted");
    }

    private static void stripeWithAStaircaseEdgeIsOneStraightStripeOnTheSpritesDots() {
        // Dots 2 to 16 across, its edges stepping a dot out and a dot in at every other dot along, as a sprite's staircase does once laid flat.
        float[] triangles = PaintFit.triangles(cover((x, y) -> y > 0 && y < SIDE && x > 2 + (Math.floor(y / 2) % 2 == 0 ? -1 : 1) && x < 16 + (Math.floor(y / 2) % 2 == 0 ? -1 : 1)), SIDE, MARGIN);
        check(triangles != null, "a stripe is read");
        for (double along : new double[] {0.01, 13.3, 64.0, 100.7, 127.99}) {
            double[] across = crossed(triangles, true, along);
            check(across.length == 2, "the stripe is one stretch of paint across, at " + along);
            near(across[0], 2.0, 0.02, "its edge is straight and on the sprite's own dots at " + along);
            near(across[1], 16.0, 0.02, "its other edge is too, at " + along);
        }
        near(triangles[3] * SIDE, 14.0, 0.02, "its thickness is its width");
    }

    private static void stripeRunsOffItsSquareWithNoEnd() {
        float[] triangles = PaintFit.triangles(cover((x, y) -> y > 0 && y < SIDE && x > 2 && x < 16), SIDE, MARGIN);
        check(triangles != null, "the stripe is read");
        // An open end has no edge: nothing of the stripe says a point at its very end is any nearer the outside than its middle is.
        for (int at = 0; at < triangles.length; at += PaintFit.CORNER) {
            double x = triangles[at] * SIDE, y = triangles[at + 1] * SIDE, inside = triangles[at + 2] * SIDE;
            if (x > 2.01 && x < 15.99 && inside >= 0f) {
                near(inside, Math.min(x - 2.0, 16.0 - x), 0.02, "a corner inside the stripe is as far inside as it is from the stripe's sides, whatever its place along, at " + x + ", " + y);
            }
            check(y > -0.01 && y < SIDE + 0.01, "nothing of a stripe that ends on the square's edge lies past it, at " + y);
        }
    }

    private static void barThatStopsShortHasAnEdgeThere() {
        float[] triangles = PaintFit.triangles(cover((x, y) -> y > 0 && y < 70 && x > 50 && x < 78), SIDE, MARGIN);
        boolean skirt = false, band = false;
        for (int at = 0; at < triangles.length; at += PaintFit.CORNER) {
            double y = triangles[at + 1] * SIDE, inside = triangles[at + 2] * SIDE;
            skirt |= y > 75 && inside < 0f;
            band |= Math.abs(y - 70) < 0.02 && inside == 0f;
        }
        check(band, "the bar's end is an edge of its outline, on the dot it stops at");
        check(skirt, "and past it the triangles go on, further outside the further out");
    }

    private static void barDrawnOffItsSquareMeetsTheNextLengthOfItself() {
        // A whole tile long, begun five dots before its square: the next square's bar starts where this one stops.
        float[] triangles = PaintFit.triangles(cover((x, y) -> y > -5 && y < SIDE - 5 && x > 50 && x < 78), SIDE, MARGIN);
        for (int at = 0; at < triangles.length; at += PaintFit.CORNER) {
            double y = triangles[at + 1] * SIDE;
            check(y > -5.01 && y < SIDE - 4.99, "the bar has no skirt past either end, where the next length of it lies: " + y);
        }
        double[] across = crossed(triangles, true, SIDE - 5.01);
        check(across.length == 2, "the bar is whole up to its end");
        near(across[0], 50.0, 0.02, "and as wide there as anywhere");
    }

    private static void slantedStripeMeetsItselfInTheNextSquare() {
        // One stripe at one square over for two along, cut into the lengths two squares hold of it.
        Painted stripe = (x, y) -> 2 * x - y > 20 && 2 * x - y < 51;
        float[] here = PaintFit.triangles(cover((x, y) -> y > 0 && y < SIDE && x > 0 && x < SIDE && stripe.at(x, y)), SIDE, MARGIN);
        float[] next = PaintFit.triangles(cover((x, y) -> y > 0 && y < SIDE && x > 0 && x < SIDE && stripe.at(x, y + SIDE)), SIDE, MARGIN);
        check(here != null && next != null, "both lengths are read");
        double[] leaves = crossed(here, true, SIDE - 0.001), enters = crossed(next, true, 0.001);
        check(leaves.length == 2 && enters.length == 2, "each is one stretch of paint on the edge the squares share");
        near(leaves[0], enters[0], 0.03, "the stripe's one side is in line across the edge");
        near(leaves[1], enters[1], 0.03, "and its other side");
        near(leaves[1] - leaves[0], 15.5, 0.03, "at the width the stripe has along the edge");
    }

    private static void triangleKeepsItsThreeCorners() {
        float[] triangles = PaintFit.triangles(cover((x, y) -> x > 0 && Math.abs(y - 64) < 30 - x), SIDE, MARGIN);
        check(triangles != null, "an arrowhead is read");
        check(inside(triangles, 28.5 / SIDE, 64.0 / SIDE), "its point is there");
        check(!inside(triangles, 31.5 / SIDE, 64.0 / SIDE), "and no further");
        check(inside(triangles, 1.0 / SIDE, 92.0 / SIDE) && !inside(triangles, 1.0 / SIDE, 95.0 / SIDE), "its foot is as wide as it was painted");
    }

    private static void wholeSquareAndHoleAndHairlineAreNotRead() {
        check(PaintFit.triangles(cover((x, y) -> false), SIDE, MARGIN) == null, "nothing painted is nothing read");
        check(PaintFit.triangles(cover((x, y) -> x > 0 && y > 0 && x < SIDE && y < SIDE), SIDE, MARGIN) == null, "a whole square of paint is a floor and no marking");
        check(PaintFit.triangles(cover((x, y) -> Math.hypot(x - 64, y - 64) < 40 && Math.hypot(x - 64, y - 64) > 20), SIDE, MARGIN) == null, "a ring has a hole and is not read");
        check(PaintFit.triangles(cover((x, y) -> y > 20 && y < 100 && x > 60 && x < 61), SIDE, MARGIN) == null, "a hair of one dot is not read");
    }

    private static void spritesDiamondIsReadFromItsOwnMiddle() {
        // A sprite at the game's doubled scale, 128 by 64 with its corner drawn at (64, 0): the diamond of dots the game paints for a
        // whole square, rows of 2 to 126 dots from x 62 and 0, and of it a stripe two dots in from the square's west edge, fourteen wide.
        int wide = 128, high = 64;
        ByteBuffer sheet = ByteBuffer.allocate(wide * high * 4);
        for (int y = 0; y < high; y++) {
            int rowFrom = y < 32 ? 62 - 2 * y : 2 * (y - 32), rowTo = y < 32 ? 64 + 2 * y : 126 - 2 * (y - 32);
            for (int x = rowFrom; x < rowTo; x++) {
                // The stripe is counted across the row from the diamond's own west edge, as the game's is.
                int fromWest = y < 32 ? x - rowFrom : x - rowFrom + 4 * (y - 32) + 2;
                if (fromWest >= 2 && fromWest < 16) {
                    int place = (y * wide + x) * 4;
                    sheet.put(place, (byte) 200).put(place + 1, (byte) 190).put(place + 2, (byte) 40).put(place + 3, (byte) 255);
                }
            }
        }
        float[] paint = new float[4];
        float[] cover = RoadPaint.laidFlat(sheet, wide, 0, 0, wide, high, 64f, 0f, 2f, 0f, 0f, wide, high, paint);
        float[] triangles = PaintFit.triangles(cover, SIDE, MARGIN);
        check(triangles != null, "the stripe is read off the sprite");
        for (double along : new double[] {0.5, 40.0, 127.5}) {
            double[] across = crossed(triangles, true, along);
            check(across.length == 2, "one stripe across at " + along);
            near(across[0], 2.0, 0.02, "two dots in from the square's edge at " + along);
            near(across[1], 16.0, 0.02, "and fourteen wide at " + along);
        }
        near(paint[0], 200f / 255f * 0.93f, 0.01, "the paint is the sprite's colour");
        near(paint[3], 1.0, 1e-6, "and a full coat");
    }

    private static void paintIsLaidWithWhatItsCornersCarry() {
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.open = 1f;
        frame.light(0.5f, 0.6f, 0.7f);
        geometry.lift[0] = 0.1f;
        geometry.lift[1] = 0.2f;
        geometry.lift[2] = 0.3f;
        geometry.lift[3] = 0.2f;
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        float[] corners = {0.25f, 0.5f, 0.02f, 0.11f, 1.1f, 0.5f, -0.15f, 0.11f, 0.25f, 0.75f, 0f, 0.11f};
        geometry.addGiven(mesh, frame, 7, 9, 0, corners, 0);
        int floats = SpriteGeometry.VERTEX_FLOATS;
        check(mesh.floats == 3 * floats, "one triangle in, one triangle laid");
        near(mesh.vertices[0], 7.25, 1e-6, "its corner is where on the square it was given");
        near(mesh.vertices[3], 0.02, 1e-6, "and carries the first number it was given with");
        near(mesh.vertices[4], 0.11, 1e-6, "and the second");
        near(mesh.vertices[2], 0.1 * 0.75 * 0.5 + 0.2 * 0.25 * 0.5 + 0.3 * 0.25 * 0.5 + 0.2 * 0.75 * 0.5, 1e-6, "it lies on the ground of the square");
        near(mesh.vertices[floats], 8.1, 1e-6, "a corner past the square's edge is laid where it is");
        near(mesh.vertices[floats + 2], 0.25, 1e-6, "as high as the edge is there");
        near(mesh.vertices[5], 0.5, 1e-6, "lit as the square is");
        near(mesh.vertices[8], 1f, 1e-6, "and out of doors as the square is, of no material the frame did not say");
    }

    private static void layGivesThePaintItsMaterialAndTheFrameItsOwnBack() {
        SpriteGeometry geometry = new SpriteGeometry();
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.open = 1f;
        frame.material = 3f;
        frame.light(1f, 1f, 1f);
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        float[] triangles = PaintFit.triangles(cover((x, y) -> y > 0 && y < SIDE && x > 2 && x < 16), SIDE, MARGIN);
        for (int at = 0; at < triangles.length; at += 3 * PaintFit.CORNER) {
            geometry.addGiven(mesh, paintFrame(frame), 0, 0, 0, triangles, at);
        }
        check(mesh.floats == triangles.length / PaintFit.CORNER * SpriteGeometry.VERTEX_FLOATS, "every triangle of the paint is laid");
        near(mesh.vertices[8], 1f + 32f * RoadPaint.MATERIAL, 1e-3, "each vertex says it is paint, out of doors");
    }

    /** The frame as RoadPaint.lay hands it on: the paint's material for the while. */
    private static SpriteGeometry.Frame paintFrame(SpriteGeometry.Frame frame) {
        frame.material = RoadPaint.MATERIAL;
        return frame;
    }

    /** How many triangles of paint a point, in dots, is inside of, clear of their edges. */
    private static int coats(float[] triangles, double x, double y) {
        int coats = 0;
        for (int at = 0; at < triangles.length; at += 3 * PaintFit.CORNER) {
            double ax = triangles[at] * SIDE, ay = triangles[at + 1] * SIDE, bx = triangles[at + 4] * SIDE, by = triangles[at + 5] * SIDE, cx = triangles[at + 8] * SIDE, cy = triangles[at + 9] * SIDE;
            double twice = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
            double a = ((bx - x) * (cy - y) - (by - y) * (cx - x)) / twice, b = ((cx - x) * (ay - y) - (cy - y) * (ax - x)) / twice, c = 1.0 - a - b;
            double edge = 0.02 / Math.sqrt(Math.abs(twice));
            if (a > edge && b > edge && c > edge && a * triangles[at + 2] + b * triangles[at + 6] + c * triangles[at + 10] > 0.0) {
                coats++;
            }
        }
        return coats;
    }

    private static void noPointOfThePaintIsCoatedTwice() {
        Painted[] shapes = {
            (x, y) -> y > 0 && y < SIDE && x > 2 && x < 16,
            (x, y) -> x > 0 && y > 0 && x < SIDE && y < SIDE && x - y > 20 && x - y < 34,
            (x, y) -> x > 0 && y > 0 && x < SIDE && y < SIDE && 2 * x - y > 20 && 2 * x - y < 51,
            (x, y) -> x > 2 && x < 16 && y > 2 && y < SIDE || y > 2 && y < 16 && x > 2 && x < SIDE,
            (x, y) -> x > 34 && x < 64 && y > 4 && y < SIDE || y > 4 && y < 18 && x > 34 && x < SIDE,
            (x, y) -> x > 0 && Math.abs(y - 64) < 30 - x,
        };
        for (int which = 0; which < shapes.length; which++) {
            float[] triangles = PaintFit.triangles(cover(shapes[which]), SIDE, MARGIN);
            check(triangles != null, "shape " + which + " is read");
            int twice = 0, never = 0, painted = 0;
            for (double x = 0.37; x < SIDE; x += 0.71) {
                for (double y = 0.41; y < SIDE; y += 0.73) {
                    int coats = coats(triangles, x, y);
                    twice += coats > 1 ? 1 : 0;
                    boolean is = shapes[which].at(x, y);
                    painted += is ? 1 : 0;
                    never += is && !inside(triangles, x / SIDE, y / SIDE) ? 1 : 0;
                }
            }
            check(twice == 0, "no point of shape " + which + " lies inside two triangles of its paint: " + twice + " do");
            check(never * 25 < painted, "and all but its staircase is painted: " + never + " of " + painted + " points are not");
        }
    }

    private static void everyCornerIsANumber() {
        Painted[] shapes = {
            (x, y) -> x > 0 && y > 0 && x < SIDE && y < SIDE && (Math.abs(x - y) < 7 || Math.abs(x + y - SIDE) < 7),
            (x, y) -> x > 0 && y > 0 && x < SIDE && y < SIDE && (Math.abs(x - 29) < 6 || Math.abs(y - 73.5) < 6),
            (x, y) -> Math.hypot(x - 64, y - 64) < 30,
            (x, y) -> x > 10 && x < 100 && y > 119.4 && y < 144,
        };
        for (int which = 0; which < shapes.length; which++) {
            float[] triangles = PaintFit.triangles(cover(shapes[which]), SIDE, MARGIN);
            for (int at = 0; triangles != null && at < triangles.length; at++) {
                check(Float.isFinite(triangles[at]), "every float of shape " + which + " is a number, at " + at);
            }
        }
    }

    private static void onlyMarkingsAreRead() {
        check(RoadPaint.marks("street_trafficlines_01_16") && RoadPaint.marks("street_trafficlines_curb_yellow_faded_108"), "the lines of a road and of its bays are markings");
        check(RoadPaint.marks("recreational_sports_01_108") && RoadPaint.marks("recreational_sports_01_119"), "so are the arcs of a court");
        check(!RoadPaint.marks("recreational_sports_01_22") && !RoadPaint.marks("recreational_sports_01_1080") && !RoadPaint.marks("recreational_sports_01_120"), "the court's floor and its hoops are not");
        check(!RoadPaint.marks("blends_street_01_80") && !RoadPaint.marks("d_streetcracks_1_3") && !RoadPaint.marks(null), "nor is a road, a crack, or a sprite with no name");
        check(RoadPaint.marks("street_curbs_01_8") && RoadPaint.marks("street_curbs_01_diag_12") && RoadPaint.marks("street_curbs_01_diag_2_40"), "the strip of concrete between a road and its pavement is laid as paint is, straight or slanted");
        check(RoadPaint.MATERIAL != WorldScene.HUNG && RoadPaint.STRIP != WorldScene.HUNG && RoadPaint.MATERIAL != Flora.CORE && RoadPaint.STRIP != Flora.CORE,
                "paint has material numbers of its own: a bulb that hangs swings in the wind, and paint taken for one did");
        check(!RoadPaint.marks("street_curbs_01_blend_dark_grass_17") && !RoadPaint.marks("street_curbs_01_blend_gravel_3"), "but not where the verge is blended into it: that is no one colour");
    }
}
