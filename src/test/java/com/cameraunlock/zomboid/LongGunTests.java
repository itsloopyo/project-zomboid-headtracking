package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;

/**
 * Tests of the mod's own long guns: how the self-loading and lever guns work their actions, how far the eye leans in
 * to them with the sights up, and where what the game fits to them goes.
 */
final class LongGunTests {
    private LongGunTests() {}

    private static final float FALLS = (float) Math.toRadians(34), OVER = (float) Math.toRadians(38);

    static void run() {
        Checks.run("leverIsShutUntilTheShotIsWorkedAndAfter", LongGunTests::leverIsShutUntilTheShotIsWorkedAndAfter);
        Checks.run("leverSwingsRightDownOnce", LongGunTests::leverSwingsRightDownOnce);
        Checks.run("modelAndHandTurnTheLeverTheSameWay", LongGunTests::modelAndHandTurnTheLeverTheSameWay);
        Checks.run("leverGunsHammerFallsAtTheShotAndIsLeftCocked", LongGunTests::leverGunsHammerFallsAtTheShotAndIsLeftCocked);
        Checks.run("aShotWorkedThrowsOneCase", LongGunTests::aShotWorkedThrowsOneCase);
        Checks.run("rackingALoadedLeverGunKeepsItsHammerCockedAndThrowsNothing", LongGunTests::rackingALoadedLeverGunKeepsItsHammerCockedAndThrowsNothing);
        Checks.run("shotsTakenWhileTheLeverGunWasNotDrawnAreNotWorked", LongGunTests::shotsTakenWhileTheLeverGunWasNotDrawnAreNotWorked);
        Checks.run("onlyABoltActionsKnobIsTurnedUp", LongGunTests::onlyABoltActionsKnobIsTurnedUp);
        Checks.run("theEyeIsStoppedShortOfWhatStandsInTheLineOfSight", LongGunTests::theEyeIsStoppedShortOfWhatStandsInTheLineOfSight);
        Checks.run("aRedDotOrLaserGoesWhereTheModelHasAPlaceForIt", LongGunTests::aRedDotOrLaserGoesWhereTheModelHasAPlaceForIt);
        Checks.run("aVersionSixFileSaysWhereItsRedDotAndLaserGo", LongGunTests::aVersionSixFileSaysWhereItsRedDotAndLaserGo);
    }

    private static void leverIsShutUntilTheShotIsWorkedAndAfter() {
        check(OwnLever.swung(-1f) == 0f, "with nothing being worked the lever is shut");
        check(OwnLever.swung(0f) == 0f && OwnLever.swung(0.04f) == 0f, "it stays shut a moment after the shot");
        check(Math.abs(OwnLever.swung(ArmActs.CYCLE_LASTS)) < 1e-6f, "and is shut again by the time working the action is over");
    }

    private static void leverSwingsRightDownOnce() {
        float most = 0f, before = 0f;
        int falls = 0;
        boolean opening = false;
        for (float seconds = 0f; seconds < ArmActs.CYCLE_LASTS; seconds += 0.002f) {
            float swung = OwnLever.swung(seconds);
            check(swung >= -1e-6f && swung <= 1f + 1e-6f, "the swing stays within its range at " + seconds);
            most = Math.max(most, swung);
            if (swung > before + 1e-6f && !opening) {
                falls++;
                opening = true;
            } else if (swung < before - 1e-6f) {
                opening = false;
            }
            before = swung;
        }
        check(most == 1f, "the lever goes right down, not " + most);
        check(falls == 1, "and once, not " + falls + " times");
    }

    /**
     * The model turns its lever about its own x by OwnGun.turnAbout, and the hand in the loop is turned about the
     * gun's right in the view by ArmPose.about, which held up is the model's x mirrored. This checks only that the
     * two turn a point of the loop about the same pin the same way, down: not where ArmHolds puts the hand.
     */
    private static void modelAndHandTurnTheLeverTheSameWay() {
        float angle = ArmActs.LEVER_SWINGS;
        // From the model to the view: mirrored in x as the mod holds a long gun, turned a little about every axis, and moved.
        float[] turnAbout = new float[9], toView = new float[16];
        ArmPose.about(new float[] {0.36f, 0.48f, 0.80f}, 0.7f, turnAbout);
        for (int column = 0; column < 3; column++) {
            float flip = column == 0 ? -1f : 1f;
            for (int row = 0; row < 3; row++) {
                toView[column * 4 + row] = turnAbout[column * 3 + row] * flip * 0.5f;
            }
        }
        toView[12] = 0.1f;
        toView[13] = -0.2f;
        toView[14] = -0.5f;
        toView[15] = 1f;
        float[] pin = {0f, 0.17f, 0.03f}, loop = {0f, 0.04f, -0.02f}, up = {0f, 0f, 1f};
        float[] part = new float[16];
        OwnGun.turnAbout(part, pin[0], pin[1], pin[2], 1f, 0f, 0f, angle);
        float[] turnedLoop = new float[3];
        ViewModelMath.place(part, loop[0], loop[1], loop[2], turnedLoop);
        float[] byModel = new float[3];
        ViewModelMath.place(toView, turnedLoop[0], turnedLoop[1], turnedLoop[2], byModel);

        float[] ahead = new float[3], over = new float[3], right = new float[3], pinView = new float[3], loopView = new float[3];
        ViewModelMath.place(toView, 0f, 1f, 0f, ahead);
        ViewModelMath.place(toView, 0f, 0f, 0f, over);
        float[] upView = new float[3];
        ViewModelMath.place(toView, 0f, 0f, 1f, upView);
        for (int axis = 0; axis < 3; axis++) {
            ahead[axis] -= over[axis];
            upView[axis] -= over[axis];
        }
        ArmPose.cross(ahead, upView, right);
        float length = (float) Math.sqrt(ArmPose.dot(right, right));
        for (int axis = 0; axis < 3; axis++) {
            right[axis] /= length;
        }
        ViewModelMath.place(toView, pin[0], pin[1], pin[2], pinView);
        ViewModelMath.place(toView, loop[0], loop[1], loop[2], loopView);
        float[] turn = new float[9], arm = new float[3], turnedArm = new float[3], byHand = new float[3];
        ArmPose.about(right, angle, turn);
        for (int axis = 0; axis < 3; axis++) {
            arm[axis] = loopView[axis] - pinView[axis];
        }
        ArmPose.turned(turn, arm, turnedArm);
        for (int axis = 0; axis < 3; axis++) {
            byHand[axis] = pinView[axis] + turnedArm[axis];
        }
        float apart = 0f;
        for (int axis = 0; axis < 3; axis++) {
            apart = Math.max(apart, Math.abs(byHand[axis] - byModel[axis]));
        }
        check(apart < 1e-5f, "the loop the model turns is where the hand in it is turned to, " + apart + " apart");
        check(turnedLoop[2] < loop[2] - 0.05f, "and it swings down, to " + turnedLoop[2] + " from " + loop[2]);
    }

    private static void leverGunsHammerFallsAtTheShotAndIsLeftCocked() {
        check(OwnLever.hammer(-1f, true, FALLS, OVER) == 0f, "the hammer is cocked with no shot being worked");
        check(OwnLever.hammer(0f, true, FALLS, OVER) == -FALLS, "it falls forward at the shot");
        float back = 0f;
        for (float seconds = 0f; seconds < ArmActs.CYCLE_LASTS; seconds += 0.002f) {
            back = Math.max(back, OwnLever.hammer(seconds, true, FALLS, OVER));
        }
        check(Math.abs(back - OVER) < 1e-4f, "the bolt pushes it back past cocked as the lever opens, by " + back);
        check(Math.abs(OwnLever.hammer(ArmActs.CYCLE_LASTS, true, FALLS, OVER)) < 1e-5f, "and it is left cocked when the lever is shut");
        float last = OwnLever.hammer(0f, true, FALLS, OVER);
        for (float seconds = 0.002f; seconds < 0.6f; seconds += 0.002f) {
            float now = OwnLever.hammer(seconds, true, FALLS, OVER);
            check(Math.abs(now - last) < 0.12f, "the hammer does not jump between " + (seconds - 0.002f) + " and " + seconds + ": " + last + " to " + now);
            last = now;
        }
    }

    private static final int GUN = 101;
    private static final float FRAME = 1f / 60f;

    private static void aShotWorkedThrowsOneCase() {
        OwnLever.Cycle cycle = new OwnLever.Cycle();
        cycle.step(GUN, true, -1f, 4);
        cycle.step(GUN, true, 0f, 5);
        check(cycle.fired, "the shot began the working");
        int thrown = cycle.thrown ? 1 : 0;
        for (float cycled = FRAME; cycled <= ArmActs.CYCLE_LASTS; cycled += FRAME) {
            cycle.step(GUN, true, cycled, 5);
            thrown += cycle.thrown ? 1 : 0;
        }
        check(thrown == 1, "and the bolt threw the fired case out once, not " + thrown + " times");
    }

    private static void rackingALoadedLeverGunKeepsItsHammerCockedAndThrowsNothing() {
        OwnLever.Cycle cycle = new OwnLever.Cycle();
        cycle.step(GUN, true, -1f, 5);
        int thrown = 0;
        for (float cycled = 0f; cycled <= ArmActs.CYCLE_LASTS; cycled += FRAME) {
            cycle.step(GUN, true, cycled, 5);
            thrown += cycle.thrown ? 1 : 0;
            check(!cycle.fired, "racking with no shot taken is not taken for a shot, at " + cycled);
        }
        check(thrown == 0, "and throws no case, not " + thrown);
        for (float seconds = 0f; seconds < ArmActs.CYCLE_LASTS; seconds += 0.002f) {
            check(OwnLever.hammer(seconds, false, FALLS, OVER) >= 0f, "the hammer never falls as it is racked, at " + seconds);
        }
        cycle.step(GUN, true, -1f, 5);
        cycle.step(GUN, true, 0f, 6);
        check(cycle.fired, "and the next shot is worked as one");
    }

    private static void shotsTakenWhileTheLeverGunWasNotDrawnAreNotWorked() {
        OwnLever.Cycle cycle = new OwnLever.Cycle();
        cycle.step(GUN, true, -1f, 2);
        cycle.step(GUN, false, 0f, 7);
        check(!cycle.fired, "shots counted while it was out of sight did not begin this working");
        cycle.step(GUN, true, -1f, 7);
        cycle.step(102, true, 0f, 9);
        check(!cycle.fired, "nor shots counted before another gun was taken in hand");
    }

    private static void onlyABoltActionsKnobIsTurnedUp() {
        check(ArmHolds.knobTurn(ArmActs.BOLT_SINGLE, 0f) == 0f, "a bolt action's knob is down before it is turned");
        check(ArmHolds.knobTurn(ArmActs.BOLT_SINGLE, 1f) == ArmHolds.BOLT_LIFT, "and turned right up to unlock it");
        check(ArmHolds.knobTurn(ArmActs.BOLT, 0.5f) == 0f && ArmHolds.knobTurn(ArmActs.BOLT, 1f) == 0f,
                "a self-loader's handle is drawn straight back, never turned");
    }

    private static ByteBuffer file(String name) throws IOException {
        try (InputStream packed = new GZIPInputStream(LongGunTests.class.getResourceAsStream("/models/" + name + ".mesh.gz"))) {
            return ByteBuffer.wrap(packed.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
        }
    }

    /**
     * What stops the eye on each own long gun with the sights up is its rear sight, or where the
     * eye looks along a rib or a rail to a front one, that one, this far ahead of the grip in its
     * metres: NaN where nothing stands in the line. On a lever gun it is the hammer's spur, which
     * the bolt pushes back past cocked into the line as the lever is worked. Every own long gun is
     * here.
     */
    private static final java.util.Map<String, Float> SIGHTS = java.util.Map.ofEntries(java.util.Map.entry("rifle", 0.450f),
            java.util.Map.entry("varmint", 0.469f), java.util.Map.entry("leverrifle", 0.015f), java.util.Map.entry("levercarbine", 0.015f),
            java.util.Map.entry("js14", 0.000f), java.util.Map.entry("trapper", 0.274f), java.util.Map.entry("js3t", 0.039f),
            java.util.Map.entry("toy", 0.018f), java.util.Map.entry("shotgun", Float.NaN), java.util.Map.entry("sawnoff", Float.NaN),
            java.util.Map.entry("dbarrel", Float.NaN), java.util.Map.entry("dbarrelsawn", Float.NaN), java.util.Map.entry("tactical", Float.NaN),
            java.util.Map.entry("assault", -0.029f), java.util.Map.entry("battle", 0.011f));

    private static void theEyeIsStoppedShortOfWhatStandsInTheLineOfSight() throws IOException {
        for (OwnModel model : OwnModel.ALL) {
            if (model.handgun) {
                continue;
            }
            String name = model.mesh.substring("/models/".length(), model.mesh.length() - ".mesh.gz".length());
            check(SIGHTS.containsKey(name), name + " says where its rear sight is");
            if (!SIGHTS.containsKey(name)) {
                continue;
            }
            ByteBuffer read = file(name);
            OwnModel.Layout layout = OwnModel.layout(read, name);
            float found = OwnModel.inTheWay(model.reached(layout, layout.places(read), layout.corners(read)), layout.muzzle(), layout.sightLine(),
                    layout.eyeBehind(), model.top());
            float expected = SIGHTS.get(name);
            check(Float.isNaN(expected) ? Float.isNaN(found) : Math.abs(found - expected) < 0.005f,
                    name + "'s eye is stopped at its rear sight, " + expected + " ahead of the grip: found " + found);
        }
    }

    /**
     * The game fits a red dot sight to the self-loading rifle and carbine and the tactical shotgun, a laser to the carbine,
     * the tactical rifle and the three pistols, and a light to the pistols: each model says where, and of a gun the game
     * fits none to, nothing. A red dot sight stands on the gun's top with its tube's axis over the open sights' line,
     * which is where the eye is raised to; a laser and a light hang under the bore, ahead of the hand on the grip.
     */
    private static void aRedDotOrLaserGoesWhereTheModelHasAPlaceForIt() throws IOException {
        String[][] fitted = {{"shotgun", "", "", ""}, {"sawnoff", "", "", ""}, {"rifle", "", "", ""}, {"dbarrel", "", "", ""}, {"dbarrelsawn", "", "", ""},
                {"varmint", "", "", ""}, {"leverrifle", "", "", ""}, {"levercarbine", "", "", ""}, {"toy", "", "", ""}, {"js14", "reddot", "", ""},
                {"trapper", "reddot", "laser", ""}, {"js3t", "reddot", "", ""}, {"tactical", "", "laser", ""}, {"revolver", "", "", ""},
                {"magnum", "", "", ""}, {"snubnose", "", "", ""}, {"capgun", "", "", ""}, {"pistol", "", "laser", "gunlight"},
                {"pistol2", "", "laser", "gunlight"}, {"pistol3", "", "laser", "gunlight"},
                {"assault", "reddot", "laser", ""}, {"battle", "reddot", "laser", ""}};
        for (OwnModel model : OwnModel.ALL) {
            String name = model.mesh.substring("/models/".length(), model.mesh.length() - ".mesh.gz".length());
            String[] gun = null;
            for (String[] row : fitted) {
                gun = row[0].equals(name) ? row : gun;
            }
            check(gun != null, name + " says what the game fits to it");
            if (gun == null) {
                continue;
            }
            ByteBuffer read = file(name);
            OwnModel.Layout layout = OwnModel.layout(read, name);
            float[] mounts = layout.mounts(), top = model.top();
            for (int kind = 1; kind <= 3; kind++) {
                int at = kind == 1 ? OwnModel.RED_DOT : kind == 2 ? OwnModel.LASER : OwnModel.GUN_LIGHT;
                boolean has = mounts != null && !Float.isNaN(mounts[at]);
                check(has == !gun[kind].isEmpty(), name + (has ? " has" : " has no") + " place for the game's " + new String[] {"", "red dot sight", "laser", "light"}[kind]);
                if (!has) {
                    continue;
                }
                float over = (mounts[at] - layout.muzzle()[0]) * top[0] + (mounts[at + 1] - layout.muzzle()[1]) * top[1] + (mounts[at + 2] - layout.muzzle()[2]) * top[2];
                if (at == OwnModel.RED_DOT) {
                    float axis = over + OwnModel.RED_DOT_AXIS / Characters.PER_METRE;
                    check(over > 0f && axis > layout.sightLine(), name + "'s red dot sight stands on its top, " + over + " over the bore, its axis "
                            + axis + " over the open sights' " + layout.sightLine());
                    check(mounts[at + 1] > -0.1f && mounts[at + 1] < layout.muzzle()[1] / 2f, "and over its receiver, " + mounts[at + 1] + " ahead of the grip");
                } else {
                    check(over < 0f, name + "'s " + gun[kind] + " hangs under its bore, " + over);
                    check(mounts[at + 1] > 0f && mounts[at + 1] < layout.muzzle()[1], "ahead of the grip and short of the muzzle: " + mounts[at + 1]);
                }
            }
        }
    }

    /** A file of version 6 with no triangles, read: its three places after the numbers that say how its parts move. */
    private static void aVersionSixFileSaysWhereItsRedDotAndLaserGo() throws IOException {
        ByteBuffer file = ByteBuffer.allocate(4 + 4 + 12 * 4 + 4 + 4 + 4 + 4 + 4 * 2 + 9 * 4).order(ByteOrder.LITTLE_ENDIAN);
        file.putInt('C' | 'U' << 8 | 'W' << 16 | 'M' << 24).putInt(6);
        for (float number : new float[] {0f, 0.8f, 0.05f, 0f, 0.3f, -0.03f, 0f, 0f, 0f, 0f, 0.03f, 0.13f}) {
            file.putFloat(number);
        }
        file.putInt(0).putInt(1).putInt(0).putInt(2).putFloat(0.5f).putFloat(0.25f);
        for (float number : new float[] {0f, -0.02f, 0.071f, Float.NaN, Float.NaN, Float.NaN, 0f, 0.1f, -0.03f}) {
            file.putFloat(number);
        }
        file.flip();
        OwnModel.Layout layout = OwnModel.layout(file, "a version 6 file");
        float[] mounts = layout.mounts();
        check(mounts != null && mounts.length == OwnModel.MOUNT_NUMBERS, "it has its places");
        check(mounts[OwnModel.RED_DOT + 1] == -0.02f && mounts[OwnModel.RED_DOT + 2] == 0.071f, "the red dot sight's, as written");
        check(Float.isNaN(mounts[OwnModel.LASER]), "no laser's");
        check(mounts[OwnModel.GUN_LIGHT + 2] == -0.03f, "and the light's");
        check(layout.moves().length == 2 && layout.moves()[1] == 0.25f, "after its moves, which are as they were");
        check(file.remaining() == 0, "and nothing is left over");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}
