package com.cameraunlock.zomboid;

import java.util.ArrayList;

/** Tests of what Flights builds round a flight's treads, and of which sprites Rails takes for lengths of rail. */
final class FlightTests {
    private FlightTests() {}

    private static final int[] SLOPED = {Rails.PIPE, Rails.BALUSTER};
    private static final int[] STYLES = {Rails.PIPE, Rails.BALUSTER, Rails.BALUSTRADE};
    /** Half the thickness of the thickest upright under a flight's rail whose head is cut square, in tiles. */
    private static final float THICKEST_HALF = 0.0225f;

    static void run() {
        Checks.run("aRailRunsUnbrokenFromTheFootOfAFlightToItsHead", FlightTests::aRailRunsUnbrokenFromTheFootOfAFlightToItsHead);
        Checks.run("aFlightsRailArrivesAtTheHeightOfTheLandingsRail", FlightTests::aFlightsRailArrivesAtTheHeightOfTheLandingsRail);
        Checks.run("everyUprightOfARailStandsOnATreadAndReachesTheRail", FlightTests::everyUprightOfARailStandsOnATreadAndReachesTheRail);
        Checks.run("aBalusterStandsOnEveryTread", FlightTests::aBalusterStandsOnEveryTread);
        Checks.run("anOpenFlightsStringsStopAtTheFloorAndCarryEveryTread", FlightTests::anOpenFlightsStringsStopAtTheFloorAndCarryEveryTread);
        Checks.run("postsStandUnderTheMiddleAndTheHeadOfAnOpenFlightThatHasAFloor", FlightTests::postsStandUnderTheMiddleAndTheHeadOfAnOpenFlightThatHasAFloor);
        Checks.run("aLevelRailSpansItsWholeEdge", FlightTests::aLevelRailSpansItsWholeEdge);
        Checks.run("everyFlightsRailHasALengthForEachOfItsSquares", FlightTests::everyFlightsRailHasALengthForEachOfItsSquares);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static final int ALONG = 0, ACROSS = 1, UP = 2;

    /** The faces a build hands over, each four corners of along, across, up. */
    private static ArrayList<float[]> faces(java.util.function.Consumer<Flights> build) {
        ArrayList<float[]> faces = new ArrayList<>();
        build.accept(new Flights(q -> faces.add(q.clone())));
        return faces;
    }

    private static float least(float[] face, int axis) {
        return Math.min(Math.min(face[axis], face[3 + axis]), Math.min(face[6 + axis], face[9 + axis]));
    }

    private static float most(float[] face, int axis) {
        return Math.max(Math.max(face[axis], face[3 + axis]), Math.max(face[6 + axis], face[9 + axis]));
    }

    private static boolean level(float[] face) {
        return near(least(face, UP), most(face, UP));
    }

    /** The highest any face reaches at the given place along. */
    private static float topAt(ArrayList<float[]> faces, float along) {
        float top = -Float.MAX_VALUE;
        for (float[] face : faces) {
            for (int corner = 0; corner < 4; corner++) {
                if (near(face[corner * 3], along)) {
                    top = Math.max(top, face[corner * 3 + UP]);
                }
            }
        }
        return top;
    }

    private static void aRailRunsUnbrokenFromTheFootOfAFlightToItsHead() {
        for (int style : SLOPED) {
            for (int third = 0; third < 2; third++) {
                int square = third;
                float joint = third + 1f;
                float below = topAt(faces(f -> f.slope(square, style, 0f)), joint), above = topAt(faces(f -> f.slope(square + 1, style, 0f)), joint);
                check(near(below, above), "the rail over square " + third + " ends at " + below + " where the next begins at " + above + ", style " + style);
                check(below > Flights.nosings(joint), "the rail is over the nosings at " + joint);
            }
        }
    }

    private static void aFlightsRailArrivesAtTheHeightOfTheLandingsRail() {
        for (int style : SLOPED) {
            float arrives = topAt(faces(f -> f.slope(2, style, 0f)), 3f);
            check(near(arrives, 1f + Flights.levelTop(style)), "the rail arrives at " + arrives + " and the landing's is at " + (1f + Flights.levelTop(style)) + ", style " + style);
            float along = topAt(faces(f -> f.level(style, 0f)), 0f);
            check(near(along, Flights.levelTop(style)), "a level rail's top at its end is " + along + ", style " + style);
        }
    }

    private static void everyUprightOfARailStandsOnATreadAndReachesTheRail() {
        for (int style : SLOPED) {
            for (int third = 0; third < 3; third++) {
                int square = third;
                ArrayList<float[]> faces = faces(f -> f.slope(square, style, 0f));
                int feet = 0;
                for (float[] face : faces) {
                    float middle = (least(face, ALONG) + most(face, ALONG)) / 2f;
                    // The level faces under the line of the nosings are the feet of the uprights. The rail has none: it slopes.
                    if (!level(face) || face[UP] > Flights.nosings(middle) + 1e-4f) {
                        continue;
                    }
                    feet++;
                    float stoodOn = middle < Stairs.GOING / 2f ? 0f : Flights.treadUnder(middle);
                    check(face[UP] < stoodOn && face[UP] > stoodOn - 0.006f,
                            "an upright at " + middle + " has its foot at " + face[UP] + ", a little into the tread there, which is at " + stoodOn + ", style " + style);
                }
                check(feet >= 2, "square " + third + " of a rail stands on " + feet + " uprights, style " + style);
                for (float[] face : faces) {
                    float middle = (least(face, ALONG) + most(face, ALONG)) / 2f;
                    boolean head = level(face) && face[UP] > Flights.nosings(middle) + 1e-4f;
                    if (head) {
                        float rail = topAt(faces, third + 1f) - (third + 1f - middle) / 3f;
                        check(face[UP] < rail && face[UP] > rail - 0.06f, "an upright's head at " + middle + " is at " + face[UP] + " under a rail whose top is at " + rail + ", style " + style);
                        // The rail falls towards the foot of the flight, and the head must be under its top at the upright's downhill face too.
                        check(face[UP] <= rail - THICKEST_HALF / 3f, "an upright's head at " + middle + " is under the rail's top at its downhill face, style " + style);
                    }
                }
            }
        }
    }

    private static void aBalusterStandsOnEveryTread() {
        int feet = 0;
        for (int third = 0; third < 3; third++) {
            int square = third;
            for (float[] face : faces(f -> f.slope(square, Rails.BALUSTER, 0f))) {
                float middle = (least(face, ALONG) + most(face, ALONG)) / 2f;
                feet += level(face) && face[UP] <= Flights.nosings(middle) + 1e-4f ? 1 : 0;
            }
        }
        check(feet == 3 * Stairs.STEPS + 2, "a flight's rail of balusters stands on " + feet + " uprights: one a tread and the two newels");
    }

    private static void anOpenFlightsStringsStopAtTheFloorAndCarryEveryTread() {
        for (int third = 0; third < 3; third++) {
            int square = third;
            for (int stands = 0; stands <= 1; stands++) {
                boolean stood = stands == 1;
                for (float[] face : faces(f -> f.open(square, stood))) {
                    check(least(face, UP) >= -1e-5f, "nothing of an open flight is under its floor, square " + third);
                    check(least(face, ACROSS) >= -1e-5f && most(face, ACROSS) <= 1f + 1e-5f, "nothing of an open flight is outside its width");
                    check(least(face, ALONG) >= third - 1e-5f && most(face, ALONG) <= third + 1f + 1e-5f, "a square of an open flight builds within itself");
                }
            }
            for (int step = 0; step < Stairs.STEPS; step++) {
                float front = third + step * Stairs.GOING, back = front + Stairs.GOING, tread = Stairs.tread(third, step);
                check(Flights.stringTop(front) <= tread && Flights.stringUnder(back) <= tread - Flights.TREAD,
                        "the string is beside the tread at " + front + " from its front to under its back");
                check(Flights.stringTop(back) >= tread, "the string covers the end of the tread at " + front + " all the way to its back");
            }
        }
        check(Flights.stringUnder(0f) == 0f, "the string stands on the floor at the foot of the flight");
    }

    private static void postsStandUnderTheMiddleAndTheHeadOfAnOpenFlightThatHasAFloor() {
        for (int third = 0; third < 3; third++) {
            int square = third;
            int bare = faces(f -> f.open(square, false)).size(), stood = faces(f -> f.open(square, true)).size();
            // Two posts and a beam, six faces each, and under the middle square two braces.
            int more = third == 0 ? 0 : third == 1 ? 30 : 18;
            check(stood - bare == more, "square " + third + " of an open flight on a floor has " + (stood - bare) + " faces of posts, beam and braces");
            if (third == 0) {
                continue;
            }
            ArrayList<float[]> all = faces(f -> f.open(square, true));
            float head = third + 1f, carried = Flights.stringUnder(head);
            boolean onFloor = false, underString = false;
            for (float[] face : all.subList(bare, all.size())) {
                boolean post = level(face) && most(face, ALONG) - least(face, ALONG) < 0.1f && most(face, ACROSS) - least(face, ACROSS) < 0.1f;
                onFloor |= post && near(face[UP], 0f) && near(most(face, ALONG), head);
                underString |= post && near(face[UP], carried);
            }
            check(onFloor && underString, "the posts under the head of square " + third + " stand on the floor and reach the string at " + carried);
        }
    }

    private static void aLevelRailSpansItsWholeEdge() {
        for (int style : STYLES) {
            ArrayList<float[]> faces = faces(f -> f.level(style, 0f));
            float from = Float.MAX_VALUE, to = -Float.MAX_VALUE, top = -Float.MAX_VALUE, foot = Float.MAX_VALUE;
            for (float[] face : faces) {
                from = Math.min(from, least(face, ALONG));
                to = Math.max(to, most(face, ALONG));
                top = Math.max(top, most(face, UP));
                foot = Math.min(foot, least(face, UP));
            }
            check(near(from, 0f) && near(to, 1f), "a level rail runs from " + from + " to " + to + " of its edge, style " + style);
            check(near(top, Flights.levelTop(style)) && foot < 0f && foot > -0.006f, "a level rail stands from " + foot + ", a little into its floor, to " + top + ", style " + style);
        }
    }

    private static void everyFlightsRailHasALengthForEachOfItsSquares() {
        int[][] flights = {{0, 1, 2}, {7, 6, 5}, {16, 17, 18}, {23, 22, 21}, {86, 87, 94}, {71, 70, 69}, {61, 60, 59}, {40, 41, 42},
                {101, 100, 99}, {80, 81, 82}, {141, 140, 139}, {120, 121, 122}};
        for (int[] flight : flights) {
            Rails.Piece bottom = Rails.of("fixtures_railings_01_" + flight[0]);
            for (int third = 0; third < 3; third++) {
                Rails.Piece piece = Rails.of("fixtures_railings_01_" + flight[third]);
                check(piece != null && piece.part == Rails.SLOPE && piece.third == third && piece.west == bottom.west && piece.style == bottom.style,
                        "fixtures_railings_01_" + flight[third] + " is the rail of square " + third + " of its flight");
            }
        }
        Rails.Piece pipe = Rails.of("fixtures_railings_01_61"), over = Rails.of("fixtures_railings_01_57"), north = Rails.of("fixtures_railings_01_40");
        check(pipe.west && pipe.style == Rails.PIPE && over.part == Rails.ABOVE && over.third == 2 && over.west && !north.west,
                "the fire escape's rail: 61 at the foot of a flight that climbs west, 57 over its head, 40 at the foot of one that climbs north");
        check(Rails.of("fixtures_railings_01_49").part == Rails.LEVEL_NORTH && Rails.of("fixtures_railings_01_48").part == Rails.LEVEL_WEST
                && Rails.of("fixtures_railings_01_52").part == Rails.LEVEL_BOTH && Rails.of("fixtures_railings_01_50").part == Rails.CORNER_POST,
                "the fire escape's landing rail: 48 on a west edge, 49 on a north one, 52 on both, 50 the post between");
        check(Rails.of("fixtures_railings_01_112") == null && Rails.of("walls_exterior_house_01_4") == null && Rails.of(null) == null,
                "a panelled rail, a wall and a sprite with no name are no rail Flights builds");
        check(Rails.flightPost("fixtures_stairs_01_14") && !Rails.flightPost("fixtures_stairs_01_22"), "the post under a fire escape is fixtures_stairs_01_14");
        check(Rails.fireEscape("fixtures_stairs_01_5") && Rails.fireEscape("fixtures_stairs_01_11") && !Rails.fireEscape("fixtures_stairs_01_1")
                && !Rails.fireEscape("fixtures_stairs_01_14") && !Rails.fireEscape("fixtures_stairs_01_50") && !Rails.fireEscape(null),
                "the fire escape is squares 3 to 5 and 11 to 13 of fixtures_stairs_01");
    }
}
