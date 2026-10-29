package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

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
        Checks.run("everyKindOfMarkingIsLaidAsTheShapeItsSpritePaints", RoadPaintTests::everyKindOfMarkingIsLaidAsTheShapeItsSpritePaints);
        Checks.run("aReadingAgreesWithItsOwnDotsAndNotWithAnothers", RoadPaintTests::aReadingAgreesWithItsOwnDotsAndNotWithAnothers);
        Checks.run("aCurvedLineIsPaintedRightAcrossItsMiddle", RoadPaintTests::aCurvedLineIsPaintedRightAcrossItsMiddle);
        Checks.run("aCurvedLineIsLaidAsItCurves", RoadPaintTests::aCurvedLineIsLaidAsItCurves);
        Checks.run("aLineDrawnALittleOffAWayIsLaidExactlyInIt", RoadPaintTests::aLineDrawnALittleOffAWayIsLaidExactlyInIt);
        Checks.run("linesDrawnInTheSpritesDiamondAreLaidWhereTheyPaint", RoadPaintTests::linesDrawnInTheSpritesDiamondAreLaidWhereTheyPaint);
        Checks.run("whatIsDrawnPastTheStaircaseIsKept", RoadPaintTests::whatIsDrawnPastTheStaircaseIsKept);
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

    /**
     * The shapes the map's markings come in beside the straight lines and bars above: a corner
     * where an edge line turns or an arrow's shaft elbows, a tee of a shaft, a broad crossing
     * rung and an arrowhead. Each is laid where its sprite paints and nowhere else, but within a
     * dot of its outline, where the fit straightens a stair.
     */
    private static void everyKindOfMarkingIsLaidAsTheShapeItsSpritePaints() {
        Map<String, Painted> kinds = new LinkedHashMap<>();
        kinds.put("a corner of an edge line", (x, y) -> y > 0 && y < 60 && x > 40 && x < 54 || y > 46 && y < 60 && x > 40 && x < SIDE);
        kinds.put("a tee of an arrow's shaft", (x, y) -> y > 0 && y < SIDE && x > 57 && x < 71 || y > 57 && y < 71 && x > 57 && x < SIDE);
        kinds.put("a broad crossing rung", (x, y) -> y > 30 && y < 98 && x > 0 && x < SIDE);
        kinds.put("an arrowhead", (x, y) -> y > 20 && y < 100 && Math.abs(x - 64) < (y - 20) * 0.5);
        laidAsPainted(kinds, 1.0, 300);
    }

    /**
     * Each shape is read and is laid where its sprite paints and nowhere else, at every dot at
     * least clear dots from its outline (nearer, the fit straightens a stair), and has more
     * than least such dots of paint to look at.
     */
    private static void laidAsPainted(Map<String, Painted> shapes, double clear, int least) {
        for (Map.Entry<String, Painted> shape : shapes.entrySet()) {
            laidAsPainted(shape.getKey(), shape.getValue(), PaintFit.triangles(cover(shape.getValue()), SIDE, MARGIN), clear, least);
        }
    }

    private static void laidAsPainted(String what, Painted painted, float[] triangles, double clear, int least) {
        check(triangles != null, what + " is read");
        if (triangles == null) {
            return;
        }
        int wrong = 0, inPaint = 0;
        for (int i = 0; i < SIDE; i++) {
            for (int j = 0; j < SIDE; j++) {
                double x = i + 0.5, y = j + 0.5;
                boolean paint = painted.at(x, y);
                boolean same = true;
                for (int k = 0; k < 8 && same; k++) {
                    same = painted.at(x + clear * Math.cos(k * Math.PI / 4), y + clear * Math.sin(k * Math.PI / 4)) == paint;
                }
                if (!same) {
                    continue;
                }
                inPaint += paint ? 1 : 0;
                wrong += inside(triangles, x / SIDE, y / SIDE) != paint ? 1 : 0;
            }
        }
        check(inPaint > least, what + " has paint to look at: " + inPaint + " dots");
        check(wrong == 0, what + " is laid where its sprite paints and not where it does not, " + clear + " dots or more from its outline: " + wrong + " dots wrong");
    }

    /**
     * The arc of a court's line, and a curved line on a diagonal road: every dot a dot and a
     * half or more inside the paint is under the paint laid, with no slit of road down the middle.
     */
    private static void aCurvedLineIsPaintedRightAcrossItsMiddle() {
        double[][] arcs = {{0, 0, 96, 104}, {-150, -150, 246, 254}};
        for (double[] arc : arcs) {
            Painted painted = (x, y) -> {
                double r = Math.hypot(x - arc[0], y - arc[1]);
                return r > arc[2] && r < arc[3] && x > 0 && y > 0 && x < SIDE && y < SIDE;
            };
            float[] triangles = PaintFit.triangles(cover(painted), SIDE, MARGIN);
            check(triangles != null, "a line curved round " + arc[2] + " dots is read");
            if (triangles == null) {
                continue;
            }
            int bare = 0, deep = 0;
            for (int i = 1; i < SIDE; i++) {
                for (int j = 1; j < SIDE; j++) {
                    double x = i + 0.5, y = j + 0.5, r = Math.hypot(x - arc[0], y - arc[1]);
                    if (r > arc[2] + 1.5 && r < arc[3] - 1.5) {
                        deep++;
                        bare += inside(triangles, x / SIDE, y / SIDE) ? 0 : 1;
                    }
                }
            }
            check(deep > 300 && bare == 0, "every dot of a line curved round " + arc[2] + " dots that is well inside its paint is painted: " + bare + " of " + deep + " are bare");
        }
    }

    /**
     * Court arcs and curved lines of other radii, centred on the square's corner, past its side
     * and past its corner: each laid where it paints, so a curve is neither cut short at its
     * ends nor left with corners where it is rounded.
     */
    private static void aCurvedLineIsLaidAsItCurves() {
        double[][] arcs = {{0, 0, 30, 36}, {0, 0, 60, 68}, {0, 0, 96, 104}, {SIDE, 0, 50, 62}, {64, -40, 70, 78}, {-150, -150, 246, 254}};
        Map<String, Painted> curves = new LinkedHashMap<>();
        for (double[] arc : arcs) {
            curves.put("a line curved round " + arc[2] + " dots from " + arc[0] + ", " + arc[1], (x, y) -> {
                double r = Math.hypot(x - arc[0], y - arc[1]);
                return r > arc[2] && r < arc[3] && x > 0 && y > 0 && x < SIDE && y < SIDE;
            });
        }
        laidAsPainted(curves, 1.0, 150);
    }

    /**
     * A line drawn half a degree off one of the directions lines are laid in, each way round:
     * along, across, the diagonals and one square over for two along. Its sides are laid
     * exactly in that direction, each a whole number of dots from the square's corner as that
     * direction counts them, so that every square holding a length of the line lays it on the
     * same two lines.
     */
    private static void aLineDrawnALittleOffAWayIsLaidExactlyInIt() {
        int[][] ways = {{1, 0}, {0, 1}, {1, 1}, {1, -1}, {2, 1}, {1, 2}, {2, -1}, {1, -2}};
        for (int[] way : ways) {
            double drawn = Math.atan2(way[1], way[0]) + 0.008, along = Math.cos(drawn), across = Math.sin(drawn);
            float[] triangles = PaintFit.triangles(cover((x, y) -> x > 0 && y > 0 && x < SIDE && y < SIDE && Math.abs((y - 64) * along - (x - 64) * across) < 7), SIDE, MARGIN);
            String what = "a line drawn half a degree off " + way[0] + " along for " + way[1] + " across";
            check(triangles != null, what + " is read");
            if (triangles == null) {
                continue;
            }
            double length = Math.hypot(way[0], way[1]), nx = way[1] / length, ny = -way[0] / length;
            double low = Double.MAX_VALUE, high = -Double.MAX_VALUE;
            for (int at = 0; at < triangles.length; at += PaintFit.CORNER) {
                if (triangles[at + 2] == 0f) {
                    double out = (nx * triangles[at] + ny * triangles[at + 1]) * SIDE;
                    low = Math.min(low, out);
                    high = Math.max(high, out);
                }
            }
            for (int at = 0; at < triangles.length; at += PaintFit.CORNER) {
                if (triangles[at + 2] == 0f) {
                    double out = (nx * triangles[at] + ny * triangles[at + 1]) * SIDE;
                    check(Math.min(out - low, high - out) < 0.01, what + ": every corner of its outline is on one of its two sides, laid in that direction: " + out + " is between " + low + " and " + high);
                    near(out * length, Math.rint(out * length), 0.01, what + ": a side is a whole number of dots from the square's corner as the direction counts them");
                }
            }
            near(high - low, 14.0, 0.5, what + ": as wide as it was drawn");
        }
    }

    /**
     * The game draws a line in the dots of its sprite's diamond, and laid flat the line's
     * edges are a staircase. Along, across and one square over for two along, a line that stops
     * in its square, and a line at a slant no line is laid at, are each laid where they paint
     * with the staircase straightened.
     */
    private static void linesDrawnInTheSpritesDiamondAreLaidWhereTheyPaint() {
        Map<String, Painted> lines = new LinkedHashMap<>();
        lines.put("a line along the map sixteen dots wide", line(1, 0, 16, 0));
        lines.put("a line one over for two along sixteen dots wide, off the middle", line(2, 1, 16, 23.7));
        lines.put("a line across the map twelve dots wide", line(0, 1, 12, 0));
        lines.put("a line along the map eight dots wide, off the middle", line(1, 0, 8, 5.3));
        for (Map.Entry<String, Painted> one : lines.entrySet()) {
            laidAsPainted(one.getKey(), one.getValue(), PaintFit.triangles(drawnInTheDiamond(one.getValue()), SIDE, MARGIN), 1.0, 300);
        }
        Painted narrow = line(2, 1, 12, 0), wide = line(2, 1, 16, 5.3);
        Painted stops = (x, y) -> narrow.at(x, y) && (x - 64) * 2 + (y - 64) < 40 * Math.sqrt(5), stopsSooner = (x, y) -> wide.at(x, y) && (x - 64) * 2 + (y - 64) < 15 * Math.sqrt(5);
        laidAsPainted("a line one over for two along that stops in its square", stops, PaintFit.triangles(drawnInTheDiamond(stops), SIDE, MARGIN), 1.0, 300);
        laidAsPainted("a wider one, off the middle, that stops sooner", stopsSooner, PaintFit.triangles(drawnInTheDiamond(stopsSooner), SIDE, MARGIN), 1.0, 300);
        double slant = Math.toRadians(35), along = Math.cos(slant), across = Math.sin(slant);
        Painted slanted = (x, y) -> x > 0 && y > 0 && x < SIDE && y < SIDE && Math.abs((y - 64) * along - (x - 64) * across) < 6;
        float[] triangles = PaintFit.triangles(drawnInTheDiamond(slanted), SIDE, MARGIN);
        laidAsPainted("a line at a slant of 35 degrees", slanted, triangles, 1.0, 300);
        int bends = 0;
        for (int at = 0; triangles != null && at < triangles.length; at += PaintFit.CORNER) {
            double x = triangles[at] * SIDE, y = triangles[at + 1] * SIDE;
            bends += triangles[at + 2] == 0f && x > 3 && y > 3 && x < SIDE - 3 && y < SIDE - 3 ? 1 : 0;
        }
        check(bends == 0, "and its sides are straight, with no corner but where it leaves its square: " + bends + " corners");
    }

    /** A straight line in the direction whole steps along and across give, wide dots wide, its middle off dots to one side of the square's. */
    private static Painted line(int along, int across, double wide, double off) {
        double length = Math.hypot(along, across), ux = along / length, uy = across / length;
        return (x, y) -> x > 0 && y > 0 && x < SIDE && y < SIDE && Math.abs((y - 64) * ux - (x - 64) * uy - off) < wide / 2;
    }

    /**
     * What is painted, drawn as the game draws a square's sprite at its doubled scale: 128 by
     * 64 dots, each of paint or of none, the square's corner at the top middle, so that the
     * middle of the dot at x and y lies (x + 0.5 - 63) / 64 tiles further along the square's x
     * than its y, and (y + 0.5) / 32 along the two together, as RoadPaint.laidFlat reads it.
     * Read back laid flat.
     */
    private static float[] drawnInTheDiamond(Painted painted) {
        int wide = 128, high = 64;
        ByteBuffer sheet = ByteBuffer.allocate(wide * high * 4);
        for (int y = 0; y < high; y++) {
            for (int x = 0; x < wide; x++) {
                double apart = (x + 0.5 - 63) / 64.0, together = (y + 0.5) / 32.0;
                if (painted.at((together + apart) / 2 * SIDE, (together - apart) / 2 * SIDE)) {
                    int place = (y * wide + x) * 4;
                    sheet.put(place, (byte) 220).put(place + 1, (byte) 220).put(place + 2, (byte) 220).put(place + 3, (byte) 255);
                }
            }
        }
        return RoadPaint.laidFlat(sheet, wide, 0, 0, wide, high, 64f, 0f, 2f, 0f, 0f, wide, high, new float[4]);
    }

    /**
     * What the fit takes out is the staircase of the sprite's diamond and the rounding the
     * softened dots give a corner, and no more: a tab standing proud of a bar's end, a corner
     * cut off a bar, the end of a line where it runs out across its square's corner, and a
     * dent in a slanted line's edge are each laid as they are painted.
     */
    private static void whatIsDrawnPastTheStaircaseIsKept() {
        Map<String, Painted> shapes = new LinkedHashMap<>();
        shapes.put("a bar with a tab three dots high on its end", (x, y) -> y > 0 && x > 50 && x < 78 && (y < 70 || x > 70 && y < 73));
        shapes.put("a bar with a tab two dots high on its end", (x, y) -> y > 0 && x > 50 && x < 78 && (y < 70 || x > 70 && y < 72));
        shapes.put("a bar with a corner cut off four dots back", (x, y) -> y > 0 && x > 50 && x < 78 && y < 70 && x + y < 144);
        shapes.put("a diagonal line running out across its square's corner", (x, y) -> x > 0 && y > 0 && x < SIDE && y < SIDE && x - y > 108 && x - y < 122);
        double slant = Math.toRadians(13), along = Math.cos(slant), across = Math.sin(slant);
        shapes.put("a slanted line with a dent two and a half dots deep in its edge", (x, y) -> {
            double u = (x - 64) * along + (y - 64) * across, v = (y - 64) * along - (x - 64) * across;
            return x > 0 && y > 0 && x < SIDE && y < SIDE && Math.abs(v) < 7 && !(v > 4.5 && Math.abs(u - 10) < 5);
        });
        laidAsPainted(shapes, 1.0, 100);
    }

    /** What decides whether a reading is used or the sprite drawn as it is: how many of the dots either paints both paint. */
    private static void aReadingAgreesWithItsOwnDotsAndNotWithAnothers() {
        float[] stair = cover((x, y) -> y > 0 && y < SIDE && x > 2 + (Math.floor(y / 2) % 2 == 0 ? -1 : 1) && x < 16 + (Math.floor(y / 2) % 2 == 0 ? -1 : 1));
        float[] read = PaintFit.triangles(stair, SIDE, MARGIN);
        check(read != null, "a stripe with a staircase edge is read");
        if (read == null) {
            return;
        }
        float own = PaintFit.agreement(stair, SIDE, MARGIN, read);
        check(own > 0.85f && own <= 1f, "and its reading agrees with its own dots but for the stair straightened: " + own);
        float[] bar = cover((x, y) -> y > 0 && y < 70 && x > 50 && x < 78);
        float other = PaintFit.agreement(bar, SIDE, MARGIN, read);
        check(other < 0.1f, "a reading laid over another sprite's dots agrees with them hardly at all: " + other);
        float[] wider = cover((x, y) -> y > 0 && y < SIDE && x > 2 && x < 30);
        float half = PaintFit.agreement(wider, SIDE, MARGIN, read);
        check(half > 0.4f && half < 0.6f, "and with a stripe twice as wide by about half: " + half);
        check(PaintFit.agreement(cover((x, y) -> false), SIDE, MARGIN, new float[0]) == 0f, "nothing laid over nothing painted agrees with nothing");
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
        LightSiteTests.sited(frame);
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
        LightSiteTests.sited(frame);
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
            (x, y) -> x > 0 && y > 0 && x < SIDE && y < SIDE && Math.hypot(x, y) > 60 && Math.hypot(x, y) < 68,
            (x, y) -> x > 0 && y > 0 && x < SIDE && y < SIDE && Math.hypot(x + 150, y + 150) > 246 && Math.hypot(x + 150, y + 150) < 254,
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
