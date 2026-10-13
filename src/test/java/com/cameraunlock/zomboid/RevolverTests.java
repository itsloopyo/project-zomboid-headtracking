package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.ArrayList;
import java.util.List;

/** Tests of how a revolver's cylinder is worked from what the game says of it: OwnRevolver.Cylinder. */
final class RevolverTests {
    private RevolverTests() {}

    static void run() throws Throwable {
        Checks.run("eachShotSpendsTheChamberUnderTheHammerAndTurnsTheNextUp", RevolverTests::eachShotSpendsTheChamberUnderTheHammerAndTurnsTheNextUp);
        Checks.run("loadingSwingsItOutThrowsOutWhatWasFiredAndFillsItInOrder", RevolverTests::loadingSwingsItOutThrowsOutWhatWasFiredAndFillsItInOrder);
        Checks.run("unloadingThrowsOutTheRoundsAsTheGameTakesThem", RevolverTests::unloadingThrowsOutTheRoundsAsTheGameTakesThem);
        Checks.run("chambersAreTurnedAboutTheCylindersAxis", RevolverTests::chambersAreTurnedAboutTheCylindersAxis);
        Checks.run("firedRoundsTheGameStillCountsAreSpentUntilThrownOut", RevolverTests::firedRoundsTheGameStillCountsAreSpentUntilThrownOut);
    }

    private static final float FRAME = 1f / 60f;

    /** Steps the cylinder for so long at a frame a sixtieth of a second, with what the game says, and gathers what it throws out. */
    private static void hold(OwnRevolver.Cylinder cylinder, int rounds, int doing, float seconds, List<String> thrown) {
        for (float time = 0f; time < seconds; time += FRAME) {
            cylinder.step("Base.Revolver", 6, rounds, doing, FRAME, (place, live) -> thrown.add((live ? "round " : "case ") + place));
        }
    }

    private static String chambers(OwnRevolver.Cylinder cylinder) {
        StringBuilder out = new StringBuilder();
        for (int place = 0; place < 6; place++) {
            out.append("-LS".charAt(cylinder.holds(place)));
        }
        return out.toString();
    }

    private static void eachShotSpendsTheChamberUnderTheHammerAndTurnsTheNextUp() {
        OwnRevolver.Cylinder cylinder = new OwnRevolver.Cylinder();
        List<String> thrown = new ArrayList<>();
        hold(cylinder, 6, ArmActs.HOLDING, 0.5f, thrown);
        check(chambers(cylinder).equals("LLLLLL") && cylinder.hammer() == 0f && cylinder.turned() == 0f, "six live rounds, the hammer down: " + chambers(cylinder));
        cylinder.step("Base.Revolver", 6, 5, ArmActs.HOLDING, FRAME, (place, live) -> thrown.add("x"));
        check(chambers(cylinder).equals("LLLLLS"), "a shot spends the chamber, which turns round to the last place: " + chambers(cylinder));
        check(cylinder.hammer() > 0.4f && cylinder.trigger() == 1f && cylinder.turned() == -1f, "on the shot the hammer is falling, the trigger back and the fired chamber still up");
        hold(cylinder, 5, ArmActs.HOLDING, 0.3f, thrown);
        check(cylinder.hammer() == 0f && cylinder.trigger() == 0f && cylinder.turned() == 0f, "after it the hammer is down, the trigger forward and the cylinder turned on");
        hold(cylinder, 3, ArmActs.HOLDING, 0.3f, thrown);
        check(chambers(cylinder).equals("LLLSSS") && thrown.isEmpty(), "two more shots: three spent, none thrown out: " + chambers(cylinder) + " " + thrown);
    }

    private static void loadingSwingsItOutThrowsOutWhatWasFiredAndFillsItInOrder() {
        OwnRevolver.Cylinder cylinder = new OwnRevolver.Cylinder();
        List<String> thrown = new ArrayList<>();
        hold(cylinder, 6, ArmActs.HOLDING, 0.1f, thrown);
        hold(cylinder, 2, ArmActs.HOLDING, 0.1f, thrown);
        check(chambers(cylinder).equals("LLSSSS"), "four shots: " + chambers(cylinder));
        hold(cylinder, 2, ArmActs.LOADING, OwnRevolver.Cylinder.PUSH_FROM, thrown);
        check(cylinder.swing() == 1f && thrown.isEmpty(), "loading swings the cylinder out before the ejector is pushed: " + cylinder.swing() + " " + thrown);
        hold(cylinder, 2, ArmActs.LOADING, 0.2f, thrown);
        check(thrown.equals(List.of("case 2", "case 3", "case 4", "case 5")), "the push throws out the four fired cases: " + thrown);
        check(chambers(cylinder).equals("LL----"), "and leaves those chambers empty: " + chambers(cylinder));
        check(cylinder.loading() == 2, "the next round goes into the chamber after the live ones");
        hold(cylinder, 3, ArmActs.LOADING, 0.5f, thrown);
        check(chambers(cylinder).equals("LLL---") && cylinder.loading() == 3, "a round the game puts in is in the next chamber: " + chambers(cylinder));
        hold(cylinder, 6, ArmActs.RACKING, 0.3f, thrown);
        check(cylinder.swing() == 0f && cylinder.pushed() == 0f && chambers(cylinder).equals("LLLLLL"), "closed again, full: " + chambers(cylinder));
        check(thrown.size() == 4, "and nothing more was thrown out");
    }

    private static void unloadingThrowsOutTheRoundsAsTheGameTakesThem() {
        OwnRevolver.Cylinder cylinder = new OwnRevolver.Cylinder();
        List<String> thrown = new ArrayList<>();
        hold(cylinder, 6, ArmActs.HOLDING, 0.1f, thrown);
        hold(cylinder, 5, ArmActs.HOLDING, 0.1f, thrown);
        hold(cylinder, 5, ArmActs.UNLOADING, 0.5f, thrown);
        hold(cylinder, 0, ArmActs.UNLOADING, 0.2f, thrown);
        check(thrown.equals(List.of("case 5", "round 0", "round 1", "round 2", "round 3", "round 4")), "the fired case at the push, the rounds as the game takes them: " + thrown);
        check(chambers(cylinder).equals("------"), "and it is empty: " + chambers(cylinder));
    }

    /** As the game has it: three shots leave six rounds with three spent; a reload clears the spent at once and leaves six. */
    private static void firedRoundsTheGameStillCountsAreSpentUntilThrownOut() {
        OwnRevolver.Cylinder cylinder = new OwnRevolver.Cylinder();
        List<String> thrown = new ArrayList<>();
        hold(cylinder, OwnRevolver.Cylinder.live(6, 0), ArmActs.HOLDING, 0.1f, thrown);
        hold(cylinder, OwnRevolver.Cylinder.live(6, 3), ArmActs.HOLDING, 0.3f, thrown);
        check(chambers(cylinder).equals("LLLSSS"), "three shots: three spent: " + chambers(cylinder));
        hold(cylinder, OwnRevolver.Cylinder.live(6, 0), ArmActs.LOADING, 0.2f, thrown);
        check(chambers(cylinder).equals("LLLSSS") && thrown.isEmpty(), "the reload clears the game's spent count, and the cases stay until the push: " + chambers(cylinder));
        hold(cylinder, OwnRevolver.Cylinder.live(6, 0), ArmActs.LOADING, 0.4f, thrown);
        check(thrown.size() == 3 && chambers(cylinder).equals("LLLLLL"), "then they are thrown out and the six the game counts are in: " + thrown + " " + chambers(cylinder));
    }

    private static void chambersAreTurnedAboutTheCylindersAxis() {
        // A turn of a sixth about a line along y through (0, -0.012) carries the top chamber at (0, 0) to where the next is.
        float[] turn = new float[16], at = new float[3];
        OwnGun.turnAbout(turn, 0f, 0f, -0.012f, 0f, 1f, 0f, (float) Math.toRadians(60));
        ViewModelMath.place(turn, 0f, 0.5f, 0f, at);
        check(Math.abs(at[1] - 0.5f) < 1e-6f && Math.abs(Math.hypot(at[0], at[2] + 0.012f) - 0.012f) < 1e-6f, "it stays on the circle round the axis and at its place along it");
        float[] moved = new float[16], both = new float[16];
        OwnRevolver.moved(0f, -0.02f, 0f, moved);
        OwnRevolver.times(turn, moved, both);
        ViewModelMath.place(both, 0f, 0.5f, 0f, at);
        check(Math.abs(at[1] - 0.48f) < 1e-6f, "and the ejector's push moves it back along the axis");
    }
}
