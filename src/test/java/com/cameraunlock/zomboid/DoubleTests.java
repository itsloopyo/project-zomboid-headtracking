package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of how the double barrelled shotgun's chambers are worked from what the game says of it: OwnDouble.Barrels, and where its shells are. */
final class DoubleTests {
    private DoubleTests() {}

    static void run() throws Throwable {
        Checks.run("aShotFiresTheLowerBarrelFirst", DoubleTests::aShotFiresTheLowerBarrelFirst);
        Checks.run("takenInHandItsRoundsAreInTheUpperChamberFirst", DoubleTests::takenInHandItsRoundsAreInTheUpperChamberFirst);
        Checks.run("anotherOfTheSameKindIsNotShotsFired", DoubleTests::anotherOfTheSameKindIsNotShotsFired);
        Checks.run("openingThrowsOutTheFiredShellsAndACancelledReloadLeavesThemEmpty", DoubleTests::openingThrowsOutTheFiredShellsAndACancelledReloadLeavesThemEmpty);
        Checks.run("eachStrokeOfTheLoadingHandPutsAShellIn", DoubleTests::eachStrokeOfTheLoadingHandPutsAShellIn);
        Checks.run("unloadingTakesRoundsOutWithNoShot", DoubleTests::unloadingTakesRoundsOutWithNoShot);
        Checks.run("theHandPushesTheShellInAtTheChambersMouth", DoubleTests::theHandPushesTheShellInAtTheChambersMouth);
    }

    private static final int GUN = 11, OTHER = 12;
    /** The numbers of dbarrel.mesh.gz as tools/models/dbarrel.py writes them, from the grip: hinge y z, opens, lever y, lever turns, breech y, lower z, upper z, shell length. */
    private static final float[] MOVES = {0.243f, 0.0256f, 0.6632f, 0.156f, 0.5585f, 0.205f, 0.0306f, 0.0536f, 0.07f};

    private static String chambers(OwnDouble.Barrels barrels) {
        return "" + "-LF".charAt(barrels.holds(0)) + "-LF".charAt(barrels.holds(1));
    }

    private static void aShotFiresTheLowerBarrelFirst() {
        OwnDouble.Barrels barrels = new OwnDouble.Barrels();
        barrels.step(GUN, 2, ArmActs.HOLDING, 0f, false);
        check(chambers(barrels).equals("LL"), "loaded in both: " + chambers(barrels));
        barrels.step(GUN, 1, ArmActs.HOLDING, 0f, false);
        check(chambers(barrels).equals("FL"), "the first shot is the lower barrel's: " + chambers(barrels));
        barrels.step(GUN, 0, ArmActs.HOLDING, 0f, false);
        check(chambers(barrels).equals("FF"), "and the second the upper's: " + chambers(barrels));
        OwnDouble.Barrels both = new OwnDouble.Barrels();
        both.step(GUN, 2, ArmActs.HOLDING, 0f, false);
        both.step(GUN, 0, ArmActs.HOLDING, 0f, false);
        check(chambers(both).equals("FF"), "two shots counted in one frame are both: " + chambers(both));
    }

    private static void takenInHandItsRoundsAreInTheUpperChamberFirst() {
        OwnDouble.Barrels one = new OwnDouble.Barrels();
        one.step(GUN, 1, ArmActs.HOLDING, 0f, false);
        check(chambers(one).equals("-L"), "one round taken in hand is in the upper chamber, the one that fires second: " + chambers(one));
        check(one.ejected() == 0, "and nothing fired is in it: the game counts none");
        OwnDouble.Barrels none = new OwnDouble.Barrels();
        none.step(GUN, 0, ArmActs.HOLDING, 0f, false);
        check(chambers(none).equals("--"), "an empty gun taken in hand is empty, with no fired shells: " + chambers(none));
        none.step(GUN, 1, ArmActs.HOLDING, 0f, false);
        check(chambers(none).equals("L-"), "a round counted in goes into the lower chamber: " + chambers(none));
    }

    private static void anotherOfTheSameKindIsNotShotsFired() {
        OwnDouble.Barrels barrels = new OwnDouble.Barrels();
        barrels.step(GUN, 2, ArmActs.HOLDING, 0f, false);
        barrels.step(OTHER, 0, ArmActs.HOLDING, 0f, false);
        check(chambers(barrels).equals("--"), "an empty gun of the same kind swapped in is empty, not fired: " + chambers(barrels));
        check(barrels.ejected() == 0, "and opening it throws nothing out");
        barrels.step(GUN, 2, ArmActs.HOLDING, 0f, false);
        check(chambers(barrels).equals("LL"), "the loaded one taken back is loaded: " + chambers(barrels));
    }

    private static void openingThrowsOutTheFiredShellsAndACancelledReloadLeavesThemEmpty() {
        OwnDouble.Barrels barrels = new OwnDouble.Barrels();
        barrels.step(GUN, 2, ArmActs.HOLDING, 0f, false);
        barrels.step(GUN, 1, ArmActs.HOLDING, 0f, false);
        check(barrels.ejected() == 1, "opened after one shot, the lower chamber's fired shell is thrown out");
        check(chambers(barrels).equals("-L"), "and the round in the upper stays: " + chambers(barrels));
        barrels.step(GUN, 0, ArmActs.HOLDING, 0f, false);
        barrels.step(GUN, 0, ArmActs.LOADING, 0.1f, true);
        check(barrels.ejected() == 2, "opened after the second, the upper's is thrown out");
        // The reload is cancelled before the game counts any round in.
        barrels.step(GUN, 0, ArmActs.HOLDING, 0f, false);
        check(chambers(barrels).equals("--"), "a cancelled reload leaves both empty: " + chambers(barrels));
        check(barrels.ejected() == 0, "and nothing more to throw out");
        barrels.step(GUN, 2, ArmActs.LOADING, 0f, true);
        check(chambers(barrels).equals("LL"), "the next reload's rounds go into both: " + chambers(barrels));
    }

    private static void eachStrokeOfTheLoadingHandPutsAShellIn() {
        OwnDouble.Barrels barrels = new OwnDouble.Barrels();
        barrels.step(GUN, 2, ArmActs.HOLDING, 0f, false);
        barrels.step(GUN, 0, ArmActs.HOLDING, 0f, false);
        barrels.ejected();
        barrels.step(GUN, 0, ArmActs.LOADING, 0.1f, true);
        check(barrels.going() == -1 && barrels.next() == 0, "at the start of the stroke no shell is at a chamber, and the lower is next");
        barrels.step(GUN, 0, ArmActs.LOADING, 0.5f, true);
        check(barrels.going() == 0 && barrels.pushed() > 0f && barrels.pushed() < 1f, "part way through the stroke a shell is going into the lower: " + barrels.pushed());
        barrels.step(GUN, 0, ArmActs.LOADING, 0.9f, true);
        check(chambers(barrels).equals("L-"), "pushed home it is in the chamber, where the hand let go of it: " + chambers(barrels));
        barrels.step(GUN, 0, ArmActs.LOADING, 0.05f, true);
        check(chambers(barrels).equals("L-"), "with the stroke over it is home before the game counts it: " + chambers(barrels));
        check(barrels.next() == 1, "and the upper is next");
        barrels.step(GUN, 0, ArmActs.LOADING, 0.5f, true);
        check(barrels.going() == 1, "the second stroke pushes a shell into the upper");
        barrels.step(GUN, 0, ArmActs.LOADING, 0.95f, true);
        barrels.step(GUN, 0, ArmActs.LOADING, 0.02f, true);
        check(chambers(barrels).equals("LL") && barrels.going() == -1 && barrels.next() == -1, "after two strokes both are loaded and none is going in: " + chambers(barrels));
        barrels.step(GUN, 2, ArmActs.RACKING, 0f, true);
        check(chambers(barrels).equals("LL"), "and when the game counts them in, they are the same two: " + chambers(barrels));
    }

    private static void unloadingTakesRoundsOutWithNoShot() {
        OwnDouble.Barrels barrels = new OwnDouble.Barrels();
        barrels.step(GUN, 2, ArmActs.HOLDING, 0f, false);
        barrels.step(GUN, 1, ArmActs.UNLOADING, 0f, true);
        check(chambers(barrels).equals("L-"), "a round taken out is the upper's, not fired: " + chambers(barrels));
        barrels.step(GUN, 0, ArmActs.UNLOADING, 0f, true);
        check(chambers(barrels).equals("--") && barrels.ejected() == 0, "and nothing fired is left to throw out: " + chambers(barrels));
    }

    private static void theHandPushesTheShellInAtTheChambersMouth() {
        float[] at = new float[16], rim = new float[3];
        OwnDouble.rim(MOVES, 0, 0f, 1f, at, rim);
        check(near(rim, 0f, MOVES[OwnDouble.BREECH_Y] - OwnDouble.EXTRACTED, MOVES[OwnDouble.LOWER_Z]), "shut, a shell home has its rim at the lower chamber's mouth, drawn back by the extractors: " + java.util.Arrays.toString(rim));
        OwnDouble.rim(MOVES, 1, 0f, 1f, at, rim);
        check(near(rim, 0f, MOVES[OwnDouble.BREECH_Y] - OwnDouble.EXTRACTED, MOVES[OwnDouble.UPPER_Z]), "and the upper's at the upper's: " + java.util.Arrays.toString(rim));
        OwnDouble.rim(MOVES, 0, 1f, 1f, at, rim);
        check(rim[2] > MOVES[OwnDouble.LOWER_Z] + 0.01f && rim[1] > MOVES[OwnDouble.BREECH_Y], "open, the breech behind the hinge has come up and forward: " + java.util.Arrays.toString(rim));
        float[] home = rim.clone();
        OwnDouble.rim(MOVES, 0, 1f, 0f, at, rim);
        float apart = (float) Math.sqrt((rim[0] - home[0]) * (rim[0] - home[0]) + (rim[1] - home[1]) * (rim[1] - home[1]) + (rim[2] - home[2]) * (rim[2] - home[2]));
        check(Math.abs(apart - ArmActs.ROUND_PUSHED) < 1e-5f, "put to the chamber, it is as far out of it as the hand that has it pushes: " + apart);
    }

    private static boolean near(float[] p, float x, float y, float z) {
        return Math.abs(p[0] - x) < 1e-5f && Math.abs(p[1] - y) < 1e-5f && Math.abs(p[2] - z) < 1e-5f;
    }
}
