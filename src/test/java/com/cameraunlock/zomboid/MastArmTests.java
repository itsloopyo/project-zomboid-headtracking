package com.cameraunlock.zomboid;

/** Tests of the traffic signals' arms over a crossing, built a piece a square from the post they leave. */
final class MastArmTests {
    private MastArmTests() {}

    static void run() {
        Checks.run("eachArmIsFivePiecesThatMeetEndToEnd", MastArmTests::eachArmIsFivePiecesThatMeetEndToEnd);
        Checks.run("theArmClimbsFromThePostAndRunsLevelToItsTip", MastArmTests::theArmClimbsFromThePostAndRunsLevelToItsTip);
        Checks.run("theSignalsFaceTheCrossing", MastArmTests::theSignalsFaceTheCrossing);
    }

    /** The post sprite of each arm on the sheet, and the sprites of its pieces from the post out. */
    private static final int[][] ARMS = {{63, 62, 61, 60, 59, 58}, {64, 65, 66, 67, 68, 69}, {72, 73, 74, 75, 76, 77}, {87, 86, 85, 84, 83, 82}};

    private static MastArms.Piece piece(int place) {
        return MastArms.of("lighting_outdoor_01_" + place);
    }

    private static void eachArmIsFivePiecesThatMeetEndToEnd() {
        for (int[] arm : ARMS) {
            Checks.check(piece(arm[0]) == null, "lighting_outdoor_01_" + arm[0] + ", the post with its lamp, is no piece of the arm");
            MastArms.Piece first = piece(arm[1]);
            Checks.check(first != null && first.from() == 0f, "lighting_outdoor_01_" + arm[1] + " runs from the post's axis");
            for (int out = 1; out <= 5; out++) {
                MastArms.Piece each = piece(arm[out]);
                Checks.check(each != null && each.out() == out && each.alongX() == first.alongX() && each.alongY() == first.alongY(),
                        "lighting_outdoor_01_" + arm[out] + " is the piece " + out + " squares out along the same arm");
                if (out > 1 && each != null) {
                    Checks.check(piece(arm[out - 1]).to() == each.from(), "lighting_outdoor_01_" + arm[out] + " starts where the piece before it ends");
                }
                if (each != null) {
                    Checks.check(Float.isNaN(each.signal()) == (out % 2 == 1), "lighting_outdoor_01_" + arm[out] + " carries a signal only on the second and fourth square");
                    float signal = each.signal();
                    Checks.check(Float.isNaN(signal) || signal > each.from() && signal < each.to(), "a signal hangs over its own piece's square");
                }
            }
        }
        Checks.check(piece(57) == null && piece(70) == null && piece(71) == null && piece(78) == null && piece(81) == null && piece(88) == null,
                "the sprites round the arms are none");
        Checks.check(MastArms.of(null) == null && MastArms.of("lighting_outdoor_01_on_60") == null, "nor a sprite with no name or of the lit sheet");
    }

    private static void theArmClimbsFromThePostAndRunsLevelToItsTip() {
        float last = MastArms.high(0f);
        for (float out = 0.1f; out <= 5.5f; out += 0.1f) {
            float high = MastArms.high(out);
            Checks.check(high >= last - 1e-6f, "the arm never comes down on its way out, at " + out);
            last = high;
        }
        Checks.check(MastArms.high(0f) < 0.6f && Math.abs(MastArms.high(3f) - 0.842f) < 1e-4f && MastArms.high(5.5f) == MastArms.high(3f),
                "it leaves the post low and runs level over the road");
        Checks.check(Math.abs(MastArms.high(0.75f) - (MastArms.high(0.5f) + MastArms.high(1f)) / 2f) < 1e-5f, "between two knots it runs straight");
        Checks.check(MastArms.HOUSING_FOOT < MastArms.high(5f) && MastArms.HOUSING_TOP > MastArms.high(5f), "a signal's housing hangs round the arm");
    }

    private static void theSignalsFaceTheCrossing() {
        // The four posts stand round one crossing: north-east, south-east, south-west and north-west. Each signal faces the
        // traffic coming at the crossing from the far side, which is away from its own post's side of the crossing.
        int[][] faces = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
        for (int arm = 0; arm < ARMS.length; arm++) {
            MastArms.Piece signal = piece(ARMS[arm][2]);
            Checks.check(signal.facesX() == faces[arm][0] && signal.facesY() == faces[arm][1],
                    "the signals on the arm of lighting_outdoor_01_" + ARMS[arm][0] + " face " + faces[arm][0] + ", " + faces[arm][1]);
            Checks.check(signal.facesX() * signal.alongX() + signal.facesY() * signal.alongY() == 0, "a signal faces square to its arm");
        }
    }
}
