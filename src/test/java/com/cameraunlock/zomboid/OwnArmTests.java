package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;

final class OwnArmTests {
    private OwnArmTests() {}

    static void run() throws IOException {
        ArmModel model;
        try (InputStream packed = OwnArmTests.class.getResourceAsStream("/models/arms.mesh.gz")) {
            model = new ArmModel(packed);
        }
        fileHasAnArmWithAHand(model);
        armLaidWhereItRestsIsLeftWhereItRests(model);
        handIsLaidOnWhatItHoldsAndTheBonesKeepTheirLengths(model);
        leftArmIsTheRightOneInAMirror(model);
        fingersBendAsThePoseHasThem(model);
        forearmTurnsWithTheHandAlongItsLength(model);
        actionIsKnownByTheGamesNameForIt();
        handKeepsTimeWithTheRoundsGoingIn();
        rightHandWorksTheBoltAndComesBackToTheGrip(model);
        leftHandComesToAPistolOnlyToLoadItAndBringsTheMagazine(model);
        leftHandLeavesTheForeEndToFeedShellsAndIsBackOnItAfter(model);
        somethingSwungInBothHandsHasAFistUnderTheOther(model);
        handCrossesToAHoldThatIsNotBesideItAndDoesNotJump(model);
    }

    private static void handCrossesToAHoldThatIsNotBesideItAndDoesNotJump(ArmModel model) {
        ArmHolds.Weapon gun = weapon(false);
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        ArmHolds.Carried carried = new ArmHolds.Carried();
        ArmHolds.Settled settled = new ArmHolds.Settled();
        settled.seconds = 0.02f;
        ArmHolds.Doing ready = new ArmHolds.Doing(0f, true, ArmActs.PUMP, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f);
        ArmHolds.Doing loading = new ArmHolds.Doing(0f, true, ArmActs.PUMP, -1f, ArmActs.LOADING, 0.5f, 0f, 1f, -1f, false, -1f, -1f, 2f);
        check(ArmHolds.place(model, SCALE, gun, ready, SAME, skins, said, carried, settled) == 2, "a gun held ready is placed");
        float[] on = {said[1][ArmPose.WRIST], said[1][ArmPose.WRIST + 1], said[1][ArmPose.WRIST + 2]};
        check(ArmHolds.place(model, SCALE, gun, loading, SAME, skins, said, carried, new ArmHolds.Settled()) == 2, "and the same gun being loaded");
        float[] off = {said[1][ArmPose.WRIST], said[1][ArmPose.WRIST + 1], said[1][ArmPose.WRIST + 2]};
        check(ArmPose.apart(on, off) > 0.1f, "its left hand is then somewhere else altogether");
        float last = 0f, most = 0f;
        float[] before = on;
        for (int frame = 0; frame < 20; frame++) {
            check(ArmHolds.place(model, SCALE, gun, loading, SAME, skins, said, carried, settled) == 2, "it is placed on every frame of the way there");
            float[] now = {said[1][ArmPose.WRIST], said[1][ArmPose.WRIST + 1], said[1][ArmPose.WRIST + 2]};
            most = Math.max(most, ArmPose.apart(now, before));
            last = ArmPose.apart(now, off);
            before = now;
        }
        check(most < ArmPose.apart(on, off) * 0.3f, "the hand goes there over several frames, no one of them taking it a third of the way");
        check(last < 1e-3f, "and is there after a fifth of a second and a little over");
    }

    private static void somethingSwungInBothHandsHasAFistUnderTheOther(ArmModel model) {
        float[] grip = {0.14f, -0.2f, -0.36f}, pointing = new float[3];
        ArmPose.unit(0f, 0.8f, -0.6f, pointing);
        ArmHolds.Weapon bat = new ArmHolds.Weapon(false, false, grip, grip, pointing, new float[] {0.3f, 0.6f, 0.8f}, grip, ArmHolds.LONG);
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        ArmHolds.Carried carried = new ArmHolds.Carried();
        float[] handle = model.holds[model.pose("handle")];
        check(ArmHolds.place(model, SCALE, bat, new ArmHolds.Doing(0f, false, ArmActs.NO_ACTION, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f), SAME, skins, said, carried, new ArmHolds.Settled()) == 1,
                "something swung in one hand has one arm");
        check(ArmHolds.place(model, SCALE, bat, new ArmHolds.Doing(0f, true, ArmActs.NO_ACTION, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f), SAME, skins, said, carried, new ArmHolds.Settled()) == 2,
                "and in both hands, two");
        float[] right = placed(skins[0], model.wrist, handle), left = placed(skins[1], model.wrist, handle);
        check(ArmPose.apart(right, grip) < 1e-4f, "the right fist is where it is held");
        float[] down = {left[0] - right[0], left[1] - right[1], left[2] - right[2]};
        check(near(ArmPose.apart(left, right), ArmHolds.LOWER_FIST * SCALE, 1e-4f) && ArmPose.dot(down, pointing) < 0f, "and the left one is under it on the handle");
        check(said[1][ArmPose.ELBOW] < said[0][ArmPose.ELBOW], "the left elbow is to the left of the right one");
    }

    /** A long gun held level before the eye, pointing straight ahead, and a handgun the same. */
    private static ArmHolds.Weapon weapon(boolean handgun) {
        float[] grip = {0.1f, -0.14f, -0.3f};
        return new ArmHolds.Weapon(true, handgun, grip, grip, new float[] {0f, 0f, -1f}, new float[] {0f, 1f, 0f}, new float[] {0.1f, -0.13f, -0.55f},
                handgun ? ArmHolds.HAND : ArmHolds.LONG);
    }

    private static final float SCALE = 0.74f;
    private static final float[] SAME = {1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};

    private static void actionIsKnownByTheGamesNameForIt() {
        check(ArmActs.kind("boltaction") == ArmActs.BOLT && ArmActs.kind("BOLT_ACTION_NO_MAG") == ArmActs.BOLT_SINGLE && ArmActs.kind("shotgun") == ArmActs.PUMP
                && ArmActs.kind("handgun") == ArmActs.HANDGUN && ArmActs.kind("revolver") == ArmActs.REVOLVER && ArmActs.kind("leveraction") == ArmActs.LEVER
                && ArmActs.kind("doublebarrelshotgun") == ArmActs.DOUBLE && ArmActs.kind("DOUBLE_BARREL_SHOTGUN_SAWN") == ArmActs.DOUBLE_SAWN,
                "each of the game's ways of reloading is a kind of action, by either of its names");
        check(ArmActs.kind("NONE") == ArmActs.NO_ACTION && ArmActs.kind("something new") == ArmActs.NO_ACTION, "and a name it does not know is no action at all");
    }

    private static void handKeepsTimeWithTheRoundsGoingIn() {
        check(ArmActs.round(0.4f, false, false, 0.1f, ArmActs.ROUND_TAKES) == 0f, "with nothing being loaded the hand is at the start of a round");
        float round = 0f;
        for (int frame = 0; frame < 10; frame++) {
            float before = round;
            round = ArmActs.round(round, true, false, ArmActs.ROUND_TAKES / 20f, ArmActs.ROUND_TAKES);
            check(round > before, "while loading the hand goes on through the round");
        }
        check(near(round, 0.5f, 1e-4f), "half way through it after half the time a round takes");
        check(ArmActs.round(0.2f, true, true, 0.01f, 1f) == ArmActs.ROUND_IN && ArmActs.round(0.9f, true, true, 0.01f, 1f) == ArmActs.ROUND_IN,
                "and when the game says a round went in, the hand is where it pushes one in");
        check(ArmActs.round(0.99f, true, false, ArmActs.ROUND_TAKES * 0.02f, ArmActs.ROUND_TAKES) < 0.05f, "after one round it begins the next");
    }

    private static void rightHandWorksTheBoltAndComesBackToTheGrip(ArmModel model) {
        ArmHolds.Weapon rifle = weapon(false);
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        ArmHolds.Carried carried = new ArmHolds.Carried();
        float[] grip = model.holds[model.pose("grip")], pinch = model.holds[model.pose("knob")];
        ArmHolds.Doing held = new ArmHolds.Doing(0f, true, ArmActs.BOLT_SINGLE, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f);
        check(ArmHolds.place(model, SCALE, rifle, held, SAME, skins, said, carried, new ArmHolds.Settled()) == 2, "a rifle held in both hands has both arms");
        check(ArmPose.apart(placed(skins[0], model.wrist, grip), rifle.grip()) < 1e-4f, "with the right hand on its grip");
        // Half way through working the bolt it is drawn right back, with its handle turned up.
        float back = ArmActs.BOLT_AFTER + OwnRifle.WORKED_FOR / 2f;
        ArmHolds.Doing working = new ArmHolds.Doing(0f, true, ArmActs.BOLT_SINGLE, back, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f);
        check(ArmHolds.place(model, SCALE, rifle, working, SAME, skins, said, carried, new ArmHolds.Settled()) == 2, "while its bolt is worked it still has both");
        ArmHolds.Parts parts = rifle.parts();
        float turned = parts.lift(), right = parts.knob()[2], up = parts.knob()[1] - parts.bore();
        float[] knob = {rifle.grip()[0] + (right * (float) Math.cos(turned) - up * (float) Math.sin(turned)) * SCALE,
            rifle.grip()[1] + (parts.bore() + right * (float) Math.sin(turned) + up * (float) Math.cos(turned)) * SCALE,
            rifle.grip()[2] - (parts.knob()[0] - parts.travel()) * SCALE};
        check(ArmPose.apart(placed(skins[0], model.wrist, pinch), knob) < 1e-4f, "and the right hand is on the knob, where the knob then is: up and back");
        check(knob[1] > rifle.grip()[1] + parts.knob()[1] * SCALE && knob[2] > rifle.grip()[2], "which is higher than it rests and nearer the eye");
        ArmHolds.Doing over = new ArmHolds.Doing(0f, true, ArmActs.BOLT_SINGLE, ArmActs.BOLT_AFTER + OwnRifle.WORKED_FOR + ArmActs.BOLT_LEFT_IN + 0.01f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f);
        check(ArmHolds.place(model, SCALE, rifle, over, SAME, skins, said, carried, new ArmHolds.Settled()) == 2
                && ArmPose.apart(placed(skins[0], model.wrist, grip), rifle.grip()) < 1e-4f, "when the bolt is shut the hand is back on the grip");
        // A pump's action is the other hand's: the right stays where it is.
        ArmHolds.Doing pumped = new ArmHolds.Doing(0f, true, ArmActs.PUMP, back, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f);
        check(ArmHolds.place(model, SCALE, rifle, pumped, SAME, skins, said, carried, new ArmHolds.Settled()) == 2
                && ArmPose.apart(placed(skins[0], model.wrist, grip), rifle.grip()) < 1e-4f, "a pump gun's right hand never leaves its grip");
    }

    private static void leftHandComesToAPistolOnlyToLoadItAndBringsTheMagazine(ArmModel model) {
        ArmHolds.Weapon pistol = weapon(true);
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        ArmHolds.Carried carried = new ArmHolds.Carried();
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f), SAME, skins, said, carried, new ArmHolds.Settled()) == 1
                && carried.what == ArmHolds.NOTHING, "a pistol held in one hand has one arm, carrying nothing");
        // A pistol with nothing in it, being loaded: a magazine has been on its way for a tenth of a second, then for longer than the game was taken to take.
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.LOADING, 0.1f, 0f, 1f, -1f, false, -1f, 0.1f, 2f), SAME, skins, said,
                carried, new ArmHolds.Settled()) == 2 && carried.what == ArmHolds.MAGAZINE, "while it is loaded the left hand comes to it with a magazine");
        float[] well = pistol.parts().well(), magazine = model.holds[model.pose("magazine")];
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.LOADING, 5f, 0f, 1f, -1f, false, -1f, 5f, 2f), SAME, skins, said,
                carried, new ArmHolds.Settled()) == 2 && carried.what == ArmHolds.MAGAZINE, "which it still has however long the game takes over it");
        float[] under = {pistol.grip()[0] + well[2] * SCALE, pistol.grip()[1] + (well[1] - ArmActs.MAGAZINE_DRAWN) * SCALE, pistol.grip()[2] - well[0] * SCALE};
        check(ArmPose.apart(placed(skins[1], model.wrist, magazine), under) < 1e-3f, "held under the well");
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.HOLDING, 0f, 0f, 1f, 0.15f, true, -1f, -1f, 2f), SAME, skins, said,
                carried, new ArmHolds.Settled()) == 2 && carried.what == ArmHolds.NOTHING, "once the game has loaded it the magazine is in the pistol");
        float[] at = {pistol.grip()[0] + well[2] * SCALE, pistol.grip()[1] + well[1] * SCALE, pistol.grip()[2] - well[0] * SCALE};
        check(ArmPose.apart(placed(skins[1], model.wrist, magazine), at) < 1e-3f, "the hand then at the well it went into");
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, true, -1f, -1f, 2f), SAME, skins, said,
                carried, new ArmHolds.Settled()) == 1, "and gone again when that is over");
        // A pistol with a magazine in it, being unloaded: the hand goes to the magazine, and has it once the game says it is out.
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.UNLOADING, 0.4f, 0f, 1f, -1f, true, -1f, -1f, 2f), SAME, skins, said,
                carried, new ArmHolds.Settled()) == 2 && carried.what == ArmHolds.NOTHING, "to unload it the left hand comes with nothing");
        check(ArmPose.apart(placed(skins[1], model.wrist, magazine), at) < 1e-3f, "and takes hold of the magazine where it is, in the well");
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.UNLOADING, 0.6f, 0f, 1f, -1f, false, 0.2f, -1f, 2f), SAME, skins, said,
                carried, new ArmHolds.Settled()) == 2 && carried.what == ArmHolds.MAGAZINE, "and goes off with it when the game says it is out");
        check(placed(skins[1], model.wrist, magazine)[1] < at[1] - 0.05f, "downwards");
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.RACKING, 0.3f, 0f, 1f, -1f, true, -1f, -1f, 2f), SAME, skins, said,
                carried, new ArmHolds.Settled()) == 2 && carried.what == ArmHolds.NOTHING, "it comes empty to draw the slide back");
        // The magazine stands in the fist the size it is.
        ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.LOADING, 0.1f, 0f, 1f, -1f, false, -1f, 0.1f, 2f), SAME, skins, said, carried, new ArmHolds.Settled());
        float long3 = (float) Math.sqrt(carried.at[4] * carried.at[4] + carried.at[5] * carried.at[5] + carried.at[6] * carried.at[6]);
        check(near(long3, SCALE, 1e-4f), "and is drawn the size the arm is");
    }

    private static void leftHandLeavesTheForeEndToFeedShellsAndIsBackOnItAfter(ArmModel model) {
        ArmHolds.Weapon gun = weapon(false);
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        ArmHolds.Carried carried = new ArmHolds.Carried();
        float[] fore = model.holds[model.pose("fore")];
        check(ArmHolds.place(model, SCALE, gun, new ArmHolds.Doing(0f, true, ArmActs.PUMP, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f), SAME, skins, said, carried, new ArmHolds.Settled()) == 2
                && ArmPose.apart(placed(skins[1], model.wrist, fore), gun.foreEnd()) < 1e-4f, "a pump gun held ready has the left hand under its fore-end");
        check(ArmHolds.place(model, SCALE, gun, new ArmHolds.Doing(0f, true, ArmActs.PUMP, -1f, ArmActs.LOADING, 1f, 0.3f, 1f, -1f, false, -1f, -1f, 2f), SAME, skins, said, carried, new ArmHolds.Settled()) == 2
                && carried.what == ArmHolds.SHELL, "being loaded, the left hand carries a shell");
        float away = ArmPose.apart(placed(skins[1], model.wrist, fore), gun.foreEnd());
        check(away > 0.05f, "and is off the fore-end");
        check(ArmHolds.place(model, SCALE, gun, new ArmHolds.Doing(0f, true, ArmActs.PUMP, -1f, ArmActs.LOADING, 1f, 0.8f, 1f, -1f, false, -1f, -1f, 2f), SAME, skins, said, carried, new ArmHolds.Settled()) == 2
                && carried.what == ArmHolds.NOTHING, "the shell is in the gun once it has been pushed in");
        check(ArmHolds.place(model, SCALE, gun, new ArmHolds.Doing(0f, true, ArmActs.BOLT_SINGLE, -1f, ArmActs.LOADING, 1f, 0.3f, 1f, -1f, false, -1f, -1f, 2f), SAME, skins, said, carried, new ArmHolds.Settled()) == 2
                && carried.what == ArmHolds.CARTRIDGE, "a rifle loaded round by round is fed cartridges");
        // Racking a pump is done by the hand that is on the fore-end, where it is.
        check(ArmHolds.place(model, SCALE, gun, new ArmHolds.Doing(0f, true, ArmActs.PUMP, -1f, ArmActs.RACKING, 0.2f, 0f, 1f, -1f, false, -1f, -1f, 2f), SAME, skins, said, carried, new ArmHolds.Settled()) == 2
                && ArmPose.apart(placed(skins[1], model.wrist, fore), gun.foreEnd()) < 1e-4f, "to rack it the left hand stays under the fore-end");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(float a, float b, float within) {
        return Math.abs(a - b) < within;
    }

    private static float[] only(ArmModel model, String pose) {
        float[] weights = new float[model.poses.length];
        weights[model.pose(pose)] = 1f;
        return weights;
    }

    private static float[] placed(float[] skins, int bone, float[] point) {
        float[] out = new float[3];
        for (int row = 0; row < 3; row++) {
            out[row] = skins[bone * 16 + row] * point[0] + skins[bone * 16 + 4 + row] * point[1] + skins[bone * 16 + 8 + row] * point[2] + skins[bone * 16 + 12 + row];
        }
        return out;
    }

    private static void fileHasAnArmWithAHand(ArmModel model) {
        check(model.bones.length == 24 && model.vertexCount > 5000 && model.indexCount % 3 == 0, "the file has the arm's bones and a mesh of triangles");
        check(model.upperLength > 0.2f && model.upperLength < 0.35f && model.foreLength > 0.2f && model.foreLength < 0.3f, "its bones are the lengths an arm's are, in metres");
        float[] cross = new float[3];
        ArmPose.cross(model.along, model.across, cross);
        check(near(Math.abs(ArmPose.dot(cross, model.out)), 1f, 1e-3f), "the hand's three lines are square to each other");
        for (String pose : new String[] {"open", "grip", "trigger", "fore", "handle", "pinch", "slide", "magazine"}) {
            model.pose(pose);
        }
    }

    /** What is asked for is exactly how the arm rests, so nothing but the fingers should move. */
    private static void armLaidWhereItRestsIsLeftWhereItRests(ArmModel model) {
        int pose = model.pose("grip");
        // The forearm is asked to come from where the elbow rests.
        float[] shoulder = model.shoulder.clone(), pole = model.elbow.clone();
        float[] skins = new float[model.bones.length * 16], said = new float[ArmPose.SAYS];
        check(ArmPose.solve(model, 1f, only(model, "grip"), model.holds[pose], model.across, model.out, shoulder, pole, false, false, skins, said), "the arm at rest can be placed");
        for (int bone : new int[] {model.upper, model.upperTwist, model.fore, model.foreTwist, model.wrist}) {
            for (int i = 0; i < 16; i++) {
                check(near(skins[bone * 16 + i], i % 5 == 0 ? 1f : 0f, 2e-3f), "the bone " + model.bones[bone] + " is left where it rests");
            }
        }
        check(said[ArmPose.BENT] < 0.02f && Math.abs(said[ArmPose.TWISTED]) < 0.02f, "with its wrist neither bent nor turned further than it rests");
        check(near(said[ArmPose.STRETCHED], 1f, 1e-3f), "and its upper arm its own length");
    }

    private static void handIsLaidOnWhatItHoldsAndTheBonesKeepTheirLengths(ArmModel model) {
        float scale = 0.74f;
        float[] centre = {0.13f, -0.14f, -0.27f}, across = {0f, 0.98f, -0.2f}, out = {-1f, 0f, 0f};
        float[] shoulder = {0.14f, -0.16f, 0.05f}, pole = {0.25f, -0.42f, 0.09f};
        float[] skins = new float[model.bones.length * 16], said = new float[ArmPose.SAYS];
        check(ArmPose.solve(model, scale, only(model, "grip"), centre, across, out, shoulder, pole, false, false, skins, said), "a hand on a grip before the eye has an arm");
        float[] held = placed(skins, model.wrist, model.holds[model.pose("grip")]);
        check(ArmPose.apart(held, centre) < 1e-4f, "what the hand holds is where it was asked for");
        float[] thumbSide = placed(skins, model.wrist, new float[] {model.holds[model.pose("grip")][0] + model.across[0], model.holds[model.pose("grip")][1] + model.across[1],
            model.holds[model.pose("grip")][2] + model.across[2]});
        float[] unit = new float[3];
        ArmPose.unit(across[0], across[1], across[2], unit);
        check(near((thumbSide[0] - held[0]) / scale, unit[0], 1e-3f) && near((thumbSide[1] - held[1]) / scale, unit[1], 1e-3f) && near((thumbSide[2] - held[2]) / scale, unit[2], 1e-3f),
                "the thumb's side of the hand is the way it was asked to be");
        float[] elbow = {said[ArmPose.ELBOW], said[ArmPose.ELBOW + 1], said[ArmPose.ELBOW + 2]}, wrist = {said[ArmPose.WRIST], said[ArmPose.WRIST + 1], said[ArmPose.WRIST + 2]};
        check(near(ArmPose.apart(elbow, wrist), model.foreLength * scale, 1e-4f), "the forearm is its own length");
        check(near(ArmPose.apart(shoulder, elbow), model.upperLength * scale * said[ArmPose.STRETCHED], 1e-4f), "and the upper arm as long as it says it is drawn");
        check(said[ArmPose.BENT] <= ArmPose.WRIST_MOST + 1e-3f, "the wrist is bent no further than a wrist bends");
        check(ArmPose.apart(placed(skins, model.fore, model.elbow), elbow) < 1e-4f && ArmPose.apart(placed(skins, model.fore, model.hand), wrist) < 1e-4f,
                "the forearm's bone runs from the elbow to the wrist");
        check(ArmPose.apart(placed(skins, model.foreTwist, model.hand), wrist) < 1e-4f && ArmPose.apart(placed(skins, model.wrist, model.hand), wrist) < 1e-4f,
                "and the half of it by the wrist and the hand both meet at the wrist");
        check(ArmPose.apart(placed(skins, model.upper, model.elbow), elbow) < 1e-4f, "the upper arm's bone ends at the elbow");
        check(elbow[1] < wrist[1] && elbow[0] > wrist[0] && elbow[2] > wrist[2], "the elbow is below the hand, out to its side and nearer the viewer");
    }

    private static void leftArmIsTheRightOneInAMirror(ArmModel model) {
        float[] skins = new float[model.bones.length * 16], mirrored = new float[model.bones.length * 16], said = new float[ArmPose.SAYS], other = new float[ArmPose.SAYS];
        check(ArmPose.solve(model, 0.74f, only(model, "fore"), new float[] {0.05f, -0.1f, -0.5f}, new float[] {0f, 0f, -1f}, new float[] {-0.5f, 0.8f, 0f},
                new float[] {0.14f, -0.16f, 0.05f}, new float[] {0.3f, -0.4f, 0f}, false, false, skins, said), "a right arm is placed");
        check(ArmPose.solve(model, 0.74f, only(model, "fore"), new float[] {-0.05f, -0.1f, -0.5f}, new float[] {0f, 0f, -1f}, new float[] {0.5f, 0.8f, 0f},
                new float[] {-0.14f, -0.16f, 0.05f}, new float[] {-0.3f, -0.4f, 0f}, true, false, mirrored, other), "and a left one asked for in its mirror");
        for (int i = 0; i < skins.length; i++) {
            // The first row of a matrix is what it makes of x.
            check(near(mirrored[i], i % 4 == 0 ? -skins[i] : skins[i], 1e-5f), "every bone of the left arm is the right one's with x the other way");
        }
        check(near(other[ArmPose.ELBOW], -said[ArmPose.ELBOW], 1e-5f) && near(other[ArmPose.ELBOW + 1], said[ArmPose.ELBOW + 1], 1e-5f), "and so is its elbow");
    }

    private static void fingersBendAsThePoseHasThem(ArmModel model) throws IOException {
        float[] centre = {0.13f, -0.14f, -0.27f}, across = {0f, 1f, 0f}, out = {-1f, 0f, 0f}, pole = {0.25f, -0.42f, 0.09f};
        float[] open = new float[model.bones.length * 16], fist = new float[model.bones.length * 16], half = new float[model.bones.length * 16], said = new float[ArmPose.SAYS];
        float[] weights = new float[model.poses.length];
        weights[model.pose("open")] = 0.5f;
        weights[model.pose("handle")] = 0.5f;
        check(ArmPose.solve(model, 1f, only(model, "open"), centre, across, out, new float[] {0.14f, -0.16f, 0.05f}, pole, false, false, open, said)
                && ArmPose.solve(model, 1f, only(model, "handle"), centre, across, out, new float[] {0.14f, -0.16f, 0.05f}, pole, false, false, fist, said)
                && ArmPose.solve(model, 1f, weights, centre, across, out, new float[] {0.14f, -0.16f, 0.05f}, pole, false, false, half, said), "an arm is placed with its hand open, shut and between");
        int tip = model.bone("finger3-3"), knuckle = model.bone("finger3-1");
        float[] tipRests = {model.rests[tip * 16 + 12], model.rests[tip * 16 + 13], model.rests[tip * 16 + 14]};
        float[] knuckleRests = {model.rests[knuckle * 16 + 12], model.rests[knuckle * 16 + 13], model.rests[knuckle * 16 + 14]};
        float openReach = ArmPose.apart(placed(open, tip, tipRests), placed(open, knuckle, knuckleRests));
        float fistReach = ArmPose.apart(placed(fist, tip, tipRests), placed(fist, knuckle, knuckleRests));
        float halfReach = ArmPose.apart(placed(half, tip, tipRests), placed(half, knuckle, knuckleRests));
        check(fistReach < openReach * 0.9f, "a shut hand's finger tip is nearer its knuckle than an open one's");
        check(halfReach < openReach && halfReach > fistReach, "and half way between the two it is between");
        // A bone's matrix turns and moves and does nothing else: a finger keeps its size as it bends.
        for (int bone = model.wrist; bone < model.bones.length; bone++) {
            float x = fist[bone * 16], y = fist[bone * 16 + 1], z = fist[bone * 16 + 2];
            check(near((float) Math.sqrt(x * x + y * y + z * z), 1f, 1e-3f), "the bone " + model.bones[bone] + " is drawn at its own size");
        }
    }

    private static void forearmTurnsWithTheHandAlongItsLength(ArmModel model) {
        float[] centre = {0.1f, -0.14f, -0.35f}, pole = {0.25f, -0.42f, 0.09f}, skins = new float[model.bones.length * 16];
        float[] palmDown = new float[ArmPose.SAYS], palmIn = new float[ArmPose.SAYS];
        check(ArmPose.solve(model, 0.74f, only(model, "open"), centre, new float[] {-1f, 0f, 0f}, new float[] {0f, -1f, 0f}, new float[] {0.14f, -0.16f, 0.05f}, pole, false, false, skins, palmDown)
                && ArmPose.solve(model, 0.74f, only(model, "open"), centre, new float[] {0f, 1f, 0f}, new float[] {-1f, 0f, 0f}, new float[] {0.14f, -0.16f, 0.05f}, pole, false, false, skins, palmIn),
                "a hand is placed palm down and palm inwards");
        float turned = Math.abs(palmDown[ArmPose.TWISTED] - palmIn[ArmPose.TWISTED]);
        check(turned > Math.toRadians(50) && turned < Math.toRadians(130), "between the two the forearm turns about a quarter turn along its length, and said " + Math.toDegrees(turned));
    }
}
