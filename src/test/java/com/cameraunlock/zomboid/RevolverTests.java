package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.ArrayList;
import java.util.List;

/** Tests of how a revolver's cylinder is worked from what the game says of it: OwnRevolver.Cylinder and Places. */
final class RevolverTests {
    private RevolverTests() {}

    static void run() throws Throwable {
        Checks.run("eachShotSpendsTheChamberUnderTheHammerAndTurnsTheNextUp", RevolverTests::eachShotSpendsTheChamberUnderTheHammerAndTurnsTheNextUp);
        Checks.run("loadingSwingsItOutThrowsOutWhatWasFiredAndFillsItInOrder", RevolverTests::loadingSwingsItOutThrowsOutWhatWasFiredAndFillsItInOrder);
        Checks.run("unloadingThrowsOutTheRoundsAsTheGameTakesThem", RevolverTests::unloadingThrowsOutTheRoundsAsTheGameTakesThem);
        Checks.run("aCountPutBackAfterEachShotStillLeavesTheCasesIn", RevolverTests::aCountPutBackAfterEachShotStillLeavesTheCasesIn);
        Checks.run("theSpentCountClearedAFrameBeforeTheReloadKeepsTheCases", RevolverTests::theSpentCountClearedAFrameBeforeTheReloadKeepsTheCases);
        Checks.run("takenInHandItIsAsTheGameHasIt", RevolverTests::takenInHandItIsAsTheGameHasIt);
        Checks.run("anotherOfTheSameKindIsNotShotsFired", RevolverTests::anotherOfTheSameKindIsNotShotsFired);
        Checks.run("aRoundTakenOutWithNoShotIsNotOne", RevolverTests::aRoundTakenOutWithNoShotIsNotOne);
        Checks.run("fiveAndEightChambersFillAndEmptyTheSameWay", RevolverTests::fiveAndEightChambersFillAndEmptyTheSameWay);
        Checks.run("theHandHasAChamberToLoadFromTheStartOfTheSwing", RevolverTests::theHandHasAChamberToLoadFromTheStartOfTheSwing);
    }

    private static final float FRAME = 1f / 60f;
    private static final int GUN = 7, OTHER = 8;

    /** A revolver's cylinder as a test holds it: its id and chamber count, what is thrown out of it, and steps of a sixtieth of a second. */
    private static final class Held {
        final OwnRevolver.Cylinder cylinder = new OwnRevolver.Cylinder();
        final List<String> thrown = new ArrayList<>();
        final int chambers;
        int id = GUN;

        Held(int chambers) {
            this.chambers = chambers;
        }

        Held hold(int rounds, int fired, int doing, float seconds) {
            for (float time = 0f; time < seconds; time += FRAME) {
                cylinder.step(id, chambers, rounds, fired, doing, FRAME, (place, live) -> thrown.add((live ? "round " : "case ") + place));
            }
            return this;
        }

        String chambers() {
            StringBuilder out = new StringBuilder();
            for (int place = 0; place < chambers; place++) {
                out.append("-LS".charAt(cylinder.holds(place)));
            }
            return out.toString();
        }
    }

    private static void eachShotSpendsTheChamberUnderTheHammerAndTurnsTheNextUp() {
        Held gun = new Held(6).hold(6, 0, ArmActs.HOLDING, 0.5f);
        check(gun.chambers().equals("LLLLLL") && gun.cylinder.hammer() == 0f && gun.cylinder.turned() == 0f, "six live rounds, the hammer down: " + gun.chambers());
        gun.hold(5, 1, ArmActs.HOLDING, FRAME);
        check(gun.chambers().equals("LLLLLS"), "a shot spends the chamber, which turns round to the last place: " + gun.chambers());
        check(gun.cylinder.hammer() > 0.4f && gun.cylinder.trigger() == 1f && gun.cylinder.turned() == -1f, "on the shot the hammer is falling, the trigger back and the fired chamber still up");
        gun.hold(5, 1, ArmActs.HOLDING, 0.3f);
        check(gun.cylinder.hammer() == 0f && gun.cylinder.trigger() == 0f && gun.cylinder.turned() == 0f, "after it the hammer is down, the trigger forward and the cylinder turned on");
        gun.hold(3, 3, ArmActs.HOLDING, 0.3f);
        check(gun.chambers().equals("LLLSSS") && gun.thrown.isEmpty(), "two more shots: three spent, none thrown out: " + gun.chambers() + " " + gun.thrown);
    }

    private static void loadingSwingsItOutThrowsOutWhatWasFiredAndFillsItInOrder() {
        Held gun = new Held(6).hold(6, 0, ArmActs.HOLDING, 0.1f).hold(2, 4, ArmActs.HOLDING, 0.1f);
        check(gun.chambers().equals("LLSSSS"), "four shots: " + gun.chambers());
        // ISReloadWeaponAction.start clears the spent count as it starts.
        gun.hold(2, 0, ArmActs.LOADING, OwnRevolver.Cylinder.PUSH_FROM);
        check(gun.cylinder.swing() == 1f && gun.thrown.isEmpty() && gun.chambers().equals("LLSSSS"), "loading swings the cylinder out with the cases still in: " + gun.chambers() + " " + gun.thrown);
        gun.hold(2, 0, ArmActs.LOADING, 0.2f);
        check(gun.thrown.equals(List.of("case 2", "case 3", "case 4", "case 5")), "the push throws out the four fired cases: " + gun.thrown);
        check(gun.chambers().equals("LL----") && gun.cylinder.loading() == 2, "and leaves those chambers empty, the next round to go into the third: " + gun.chambers());
        gun.hold(3, 0, ArmActs.LOADING, 0.5f);
        check(gun.chambers().equals("LLL---") && gun.cylinder.loading() == 3, "a round the game puts in is in the next chamber: " + gun.chambers());
        gun.hold(6, 0, ArmActs.RACKING, 0.3f);
        check(gun.cylinder.swing() == 0f && gun.cylinder.pushed() == 0f && gun.chambers().equals("LLLLLL") && gun.thrown.size() == 4, "closed again, full, nothing more thrown: " + gun.chambers());
    }

    private static void unloadingThrowsOutTheRoundsAsTheGameTakesThem() {
        Held gun = new Held(6).hold(6, 0, ArmActs.HOLDING, 0.1f).hold(5, 1, ArmActs.HOLDING, 0.1f);
        gun.hold(5, 0, ArmActs.UNLOADING, 0.5f).hold(0, 0, ArmActs.UNLOADING, 0.2f);
        check(gun.thrown.equals(List.of("case 5", "round 0", "round 1", "round 2", "round 3", "round 4")), "the fired case at the push, the rounds as the game takes them: " + gun.thrown);
        check(gun.chambers().equals("------"), "and it is empty: " + gun.chambers());
    }

    /** As the rig had it on 42.21: something put the count back after each shot, so three shots left six rounds and three spent. */
    private static void aCountPutBackAfterEachShotStillLeavesTheCasesIn() {
        Held gun = new Held(6).hold(6, 0, ArmActs.HOLDING, 0.1f).hold(6, 3, ArmActs.HOLDING, 0.3f);
        check(gun.chambers().equals("LLLSSS"), "three shots: three spent: " + gun.chambers());
        gun.hold(6, 0, ArmActs.LOADING, 0.2f);
        check(gun.chambers().equals("LLLSSS") && gun.thrown.isEmpty(), "the reload clears the game's spent count, and the cases stay until the push: " + gun.chambers());
        gun.hold(6, 0, ArmActs.LOADING, 0.4f);
        check(gun.thrown.size() == 3 && gun.chambers().equals("LLLLLL"), "then they are thrown out and the six the game counts are in: " + gun.thrown + " " + gun.chambers());
    }

    /** The two changes a frame apart, in the other order: the spent count cleared while the game still says holding. */
    private static void theSpentCountClearedAFrameBeforeTheReloadKeepsTheCases() {
        Held gun = new Held(6).hold(6, 0, ArmActs.HOLDING, 0.1f).hold(3, 3, ArmActs.HOLDING, 0.3f);
        gun.hold(3, 0, ArmActs.HOLDING, FRAME);
        check(gun.chambers().equals("LLLSSS") && gun.cylinder.hammer() == 0f, "the cleared count is neither a shot nor the cases gone: " + gun.chambers());
        gun.hold(3, 0, ArmActs.LOADING, 0.6f);
        check(gun.thrown.equals(List.of("case 3", "case 4", "case 5")), "and the push throws them out: " + gun.thrown);
    }

    private static void takenInHandItIsAsTheGameHasIt() {
        Held gun = new Held(6).hold(3, 3, ArmActs.HOLDING, FRAME);
        check(gun.chambers().equals("LLLSSS") && gun.cylinder.hammer() == 0f, "a revolver taken up with three fired holds their cases, and was not just fired: " + gun.chambers());
        gun.hold(3, 0, ArmActs.LOADING, 0.6f);
        check(gun.thrown.equals(List.of("case 3", "case 4", "case 5")), "its first reload throws them out: " + gun.thrown);
    }

    private static void anotherOfTheSameKindIsNotShotsFired() {
        Held gun = new Held(6).hold(6, 0, ArmActs.HOLDING, 0.2f);
        gun.id = OTHER;
        gun.hold(2, 0, ArmActs.HOLDING, FRAME);
        check(gun.chambers().equals("LL----") && gun.cylinder.hammer() == 0f && gun.cylinder.turned() == 0f, "another revolver of the kind, with two in it, is read afresh: " + gun.chambers());
        gun.id = GUN;
        gun.hold(6, 0, ArmActs.HOLDING, FRAME);
        check(gun.chambers().equals("LLLLLL") && gun.cylinder.hammer() == 0f, "and so is the first again: " + gun.chambers());
    }

    private static void aRoundTakenOutWithNoShotIsNotOne() {
        Held gun = new Held(6).hold(6, 0, ArmActs.HOLDING, 0.2f).hold(4, 0, ArmActs.HOLDING, FRAME);
        check(gun.chambers().equals("LLLL--") && gun.cylinder.hammer() == 0f && gun.cylinder.turned() == 0f, "two rounds taken out leave empty chambers and no shot: " + gun.chambers());
    }

    private static void fiveAndEightChambersFillAndEmptyTheSameWay() {
        Held snub = new Held(5).hold(5, 0, ArmActs.HOLDING, 0.1f).hold(3, 2, ArmActs.HOLDING, 0.3f);
        check(snub.chambers().equals("LLLSS"), "five chambers, two shots: " + snub.chambers());
        snub.hold(3, 0, ArmActs.LOADING, 0.6f).hold(5, 0, ArmActs.LOADING, 0.3f);
        check(snub.thrown.equals(List.of("case 3", "case 4")) && snub.chambers().equals("LLLLL"), "two cases out, two rounds in: " + snub.thrown + " " + snub.chambers());
        Held caps = new Held(8).hold(8, 0, ArmActs.HOLDING, 0.1f).hold(1, 7, ArmActs.HOLDING, 0.3f);
        check(caps.chambers().equals("LSSSSSSS"), "eight seats, seven fired: " + caps.chambers());
        caps.hold(1, 0, ArmActs.LOADING, 0.6f).hold(8, 0, ArmActs.LOADING, 0.3f);
        check(caps.thrown.size() == 7 && caps.chambers().equals("LLLLLLLL"), "seven burnt caps out, the ring full again: " + caps.thrown.size() + " " + caps.chambers());
    }

    /** The revolver's own model's numbers for how its parts move, as its file has them. */
    private static float[] moves() throws java.io.IOException {
        try (java.io.InputStream packed = new java.util.zip.GZIPInputStream(RevolverTests.class.getResourceAsStream("/models/revolver.mesh.gz"))) {
            return OwnModel.layout(java.nio.ByteBuffer.wrap(packed.readAllBytes()).order(java.nio.ByteOrder.LITTLE_ENDIAN), "revolver").moves();
        }
    }

    private static void theHandHasAChamberToLoadFromTheStartOfTheSwing() throws java.io.IOException {
        OwnRevolver.Places places = new OwnRevolver.Places();
        float[] moves = moves(), port = new float[3], first = new float[3];
        Held gun = new Held(6).hold(2, 4, ArmActs.HOLDING, 0.1f);
        check(!places.port(moves, gun.cylinder, port), "shut, no round goes in");
        gun.hold(2, 0, ArmActs.LOADING, FRAME);
        check(places.port(moves, gun.cylinder, first) && gun.cylinder.swing() < 0.1f, "the first frame of the swing already has a chamber for the hand");
        gun.hold(2, 0, ArmActs.LOADING, 0.25f);
        check(places.port(moves, gun.cylinder, port), "and it is there all through the swing");
        float moved = (float) Math.sqrt((port[0] - first[0]) * (port[0] - first[0]) + (port[2] - first[2]) * (port[2] - first[2]));
        // The file's x is the gun's right as made, and the cylinder swings out to its left.
        check(moved > 0.01f && port[0] < first[0] && Math.abs(port[1] - first[1]) < 1e-6f, "the chamber goes out to the left with the cylinder: x " + port[0] + " from " + first[0]);
        float[] axis = new float[3], crane = new float[16];
        places.crane(moves, gun.cylinder, crane);
        ViewModelMath.place(crane, moves[OwnRevolver.AXIS_X], 0f, moves[OwnRevolver.AXIS_Z], axis);
        float[] top = {moves[OwnRevolver.CHAMBER_X] - moves[OwnRevolver.AXIS_X], moves[OwnRevolver.CHAMBER_Z] - moves[OwnRevolver.AXIS_Z]};
        float pitch = (float) Math.hypot(top[0], top[1]), off = (float) Math.hypot(port[0] - axis[0], port[2] - axis[2]);
        check(Math.abs(off - pitch) < 1e-5f, "on the circle of the chambers round the swung cylinder's axis: " + off + " against " + pitch);
        gun.hold(6, 0, ArmActs.LOADING, 0.3f);
        check(!places.port(moves, gun.cylinder, port), "full, no round goes in");
    }
}
