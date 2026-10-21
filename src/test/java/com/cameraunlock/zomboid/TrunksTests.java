package com.cameraunlock.zomboid;

import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.HashSet;
import java.util.Set;
import zombie.iso.LosUtil.TestResults;

/**
 * A tree stops a first person shot at the bole the picture draws, not across its whole square (see
 * Trunks). Measured in the game, in the wood by the railway at (10900.5, 8500.5): the aim at a zombie
 * fifteen tiles east and one north stopped at x 10907.000, the far edge of the tree square
 * (10906, 8500), which it crossed 0.36 tiles from the middle, and the game found no target behind
 * it. Six of eight aimed shots there stopped on a tree square's far edge; in town, with no trees,
 * none did.
 */
final class TrunksTests {
    private TrunksTests() {}

    static void run() {
        Checks.run("leavingATreesSquareStopsAShotOnlyWhereTheGameStopsItForSomethingElse", TrunksTests::leavingATreesSquareStopsAShotOnlyWhereTheGameStopsItForSomethingElse);
        Checks.run("aLinePastTheBoleGoesOnAndOneIntoItStopsThere", TrunksTests::aLinePastTheBoleGoesOnAndOneIntoItStopsThere);
        Checks.run("theMeasuredShotPastATreesCornerReachesTheZombie", TrunksTests::theMeasuredShotPastATreesCornerReachesTheZombie);
        Checks.run("theAimAndTheHitListBothGoByTheBole", TrunksTests::theAimAndTheHitListBothGoByTheBole);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void leavingATreesSquareStopsAShotOnlyWhereTheGameStopsItForSomethingElse() {
        check(Trunks.past(TestResults.Blocked, TestResults.Clear, 0.2f) == TestResults.Clear,
                "out of a tree's square onto open ground the shot goes on: the bole inside is what it can meet");
        check(Trunks.past(TestResults.Blocked, TestResults.ClearThroughWindow, 0.2f) == TestResults.ClearThroughWindow,
                "and through a window beside the tree it is the window the line of fire keeps");
        check(Trunks.past(TestResults.Blocked, TestResults.Blocked, 0.2f) == TestResults.Blocked,
                "a wall on the same edge still stops it");
        check(Trunks.past(TestResults.Blocked, TestResults.Clear, Float.NaN) == TestResults.Blocked,
                "a tree the picture draws no bole for stops it across its square, as the game has it");
        check(Trunks.past(TestResults.ClearThroughClosedDoor, TestResults.Clear, 0.2f) == TestResults.ClearThroughClosedDoor,
                "and what the game does not call blocked is left as it is");
    }

    private static void aLinePastTheBoleGoesOnAndOneIntoItStopsThere() {
        // A bole of radius 0.2 in the middle of square (3, 0), at (3.5, 0.5).
        check(Trunks.meets(0.5f, 0.75f, 1f, 0f, 3, 0, 0.2f, 0f) == Float.POSITIVE_INFINITY, "a line 0.25 off its middle passes by");
        float into = Trunks.meets(0.5f, 0.6f, 1f, 0f, 3, 0, 0.2f, 0f);
        check(Math.abs(into - (3f - (float) Math.sqrt(0.2 * 0.2 - 0.1 * 0.1))) < 1e-4f, "one 0.1 off meets its bark (" + into + ")");
        check(Trunks.meets(4.5f, 0.5f, 1f, 0f, 3, 0, 0.2f, 0f) == Float.POSITIVE_INFINITY, "the bole behind the line's start is not met");
        // The direction is the ground part of a unit vector in three dimensions: distances are along that vector.
        float down = (float) Math.sqrt(1 - 0.36), slanted = Trunks.meets(0.5f, 0.5f, down, 0f, 3, 0, 0.2f, 0f);
        check(Math.abs(slanted - 2.8f / down) < 1e-4f, "a line aimed down meets it as far along itself as the ground part takes (" + slanted + ")");
        check(Trunks.meets(0.5f, 0.5f, 1f, 0f, 3, 0, 0.2f, 3.0f) == 3.0f, "a trace that starts inside the bole meets it at once");
    }

    /**
     * The game's test along the measured line, square by square, with the tree on (6, 0): blocked leaving
     * it, as the game's own test answers past a tree (rig run q2: Blocked from 10907, 8500 towards the tree
     * on 10906, 8500, Clear the other way). With the bole taken as what stops the shot, the line reaches
     * the zombie.
     */
    private static void theMeasuredShotPastATreesCornerReachesTheZombie() {
        int treeX = 6, treeY = 0;
        float bole = 0.2f;
        GridTrace.Edges game = (fromX, fromY, toX, toY) -> fromX == treeX && fromY == treeY;
        GridTrace.Edges boles = (fromX, fromY, toX, toY) -> new WorldQuery.LineOfFire().stopsAt(
                Trunks.past(fromX == treeX && fromY == treeY ? TestResults.Blocked : TestResults.Clear, TestResults.Clear,
                        fromX == treeX && fromY == treeY ? bole : Float.NaN));
        // From (0.5, 0.5) to the zombie at (15.57, -0.41), offsets of the measured run.
        float wayX = 15.07f, wayY = -0.91f, length = (float) Math.hypot(wayX, wayY);
        GridTrace trace = new GridTrace();
        float stock = trace.firstBlockedEdge(game, 0.5f, 0.5f, wayX / length, wayY / length, length);
        check(Math.abs(0.5f + wayX / length * stock - 7f) < 1e-3f, "by the game's rule the shot stops at x 7, the tree square's far edge (" + stock + ")");
        check(trace.firstBlockedEdge(boles, 0.5f, 0.5f, wayX / length, wayY / length, length) == Float.POSITIVE_INFINITY,
                "by the bole it goes on to the zombie");
        check(Trunks.meets(0.5f, 0.5f, wayX / length, wayY / length, treeX, treeY, bole, 0f) == Float.POSITIVE_INFINITY,
                "and it passes the bole by, 0.36 from its middle");
    }

    private static Set<String> calledBy(Class<?> owner, String method) throws Exception {
        byte[] bytes;
        try (InputStream in = owner.getResourceAsStream(owner.getSimpleName() + ".class")) {
            bytes = in.readAllBytes();
        }
        Set<String> called = new HashSet<>();
        for (MethodModel each : ClassFile.of().parse(bytes).methods()) {
            if (each.methodName().equalsString(method)) {
                each.code().ifPresent(code -> code.forEach(element -> {
                    if (element instanceof InvokeInstruction invoke) {
                        called.add(invoke.owner().asInternalName() + "." + invoke.name().stringValue());
                    }
                }));
            }
        }
        return called;
    }

    private static void theAimAndTheHitListBothGoByTheBole() throws Exception {
        check(calledBy(WorldQuery.class, "solidToAShot").contains("com/cameraunlock/zomboid/Trunks.past"),
                "the aim's edge test takes the tree of the square left away where its bole is drawn");
        check(calledBy(Penetration.class, "edgeCost").contains("com/cameraunlock/zomboid/Trunks.past"),
                "and so does the line the game's hit list keeps a target by, or the reticle would sit on a zombie the shot cannot reach");
        check(calledBy(Penetration.class, "spendAt").contains("com/cameraunlock/zomboid/Trunks.meets"),
                "and that line stops at a bole it meets");
    }
}
