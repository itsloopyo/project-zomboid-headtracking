package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

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
        Checks.run("actionIsKnownByTheGamesNameForIt", OwnArmTests::actionIsKnownByTheGamesNameForIt);
        Checks.run("handKeepsTimeWithTheRoundsGoingIn", OwnArmTests::handKeepsTimeWithTheRoundsGoingIn);
        rightHandWorksTheBoltAndComesBackToTheGrip(model);
        leftHandComesToAPistolOnlyToLoadItAndBringsTheMagazine(model);
        leftHandCarriesATorchLowBesideAOneHandedWeapon(model);
        leftHandLeavesTheForeEndToFeedShellsAndIsBackOnItAfter(model);
        somethingSwungInBothHandsHasAFistUnderTheOther(model);
        handCrossesToAHoldThatIsNotBesideItAndDoesNotJump(model);
        armsOnALongGunAreArmsFromShouldersOutOfThePicture(model);
        handUnderAForeEndComesBackToWhereItsArmReaches(model);
        handOnAPumpIsOnItAndGoesWithIt(model);
        upperArmIsAsLongAsTheHoldNeedsWithinWhatUpperArmsAre(model);
        Checks.run("elbowIsWhereTheTwoBonesMeet", OwnArmTests::elbowIsWhereTheTwoBonesMeet);
        wristIsWhereTheArmPutsIt(model);
        Checks.run("longGunIsHeldNearerAtTheHipThanItsSightsAsk", OwnArmTests::longGunIsHeldNearerAtTheHipThanItsSightsAsk);
        armsAreTheSizeArmsAreAndStartWhereShouldersAre(model);
        everyMeleeClassKeepsItsWristsThroughEveryBlow(model);
        aSpearsLeftArmStaysUnderThePictureThroughAThrust(model);
        aTwoHandedSwingGoesThroughWithTheLeftWristEasy(model);
    }

    private static final float SCALE = 0.74f;
    /** The tangent of half the height of the picture the game is seen in, how wide it is against that, and how far under the picture the middle of an elbow is asked to be and how thick an arm is about the ball of its shoulder, in metres. */
    private static final float PICTURE = 0.6371f, WIDE = 16f / 9f, UNDER = 0.03f, BALL = 0.09f;
    /**
     * A hunting rifle and a pump gun of the mod's own, and one of the game's long guns: how far behind the grip the eye is with the sights up,
     * how far under the barrel's line the grip is, how far ahead of the grip the fore-end is held and how far over it, in metres.
     */
    private static final float[][] LONG_GUNS = {{0.13f, 0.062f, 0.41f, 0.032f}, {0.26f, 0.053f, 0.4425f, 0.019f}, {ViewModel.AIMED_AHEAD / SCALE, 0.06f, 0.38f, 0.02f}};
    private static final String[] LONG_GUN_NAMES = {"the rifle", "the pump gun", "one of the game's long guns"};
    /** How a gun is tipped about its grip: its muzzle up, its muzzle to the left, and its top over to the left, in radians. The first is filled in with the cant a gun has at the hip. */
    private static final float[][] TIPS = {{0f, 0f, 0f}, {0.3f, 0f, 0f}, {-0.3f, 0f, 0f}, {0f, 0.25f, 0f}, {0f, -0.25f, 0f}, {0f, 0f, 0.6f}, {0f, 0f, -0.6f}, {0.2f, -0.2f, -0.7f}};

    private static float[] turned(float[] turn, float[] line) {
        return new float[] {turn[0] * line[0] + turn[3] * line[1] + turn[6] * line[2], turn[1] * line[0] + turn[4] * line[1] + turn[7] * line[2],
            turn[2] * line[0] + turn[5] * line[1] + turn[8] * line[2]};
    }

    /**
     * A long gun where the mod holds one in its own hands: `raised` of the way from the hip to the sights, tipped about the line of its
     * barrel, and its fore-end drawn back by `drawn` metres from where it rests.
     */
    private static ArmHolds.Weapon longGun(float[] gun, float raised, float[] tip, float drawn) {
        float aimed = gun[0] * SCALE, hip = ViewModel.hipAhead(aimed);
        float[] line = {ViewModel.HIP_LONG_X * (1f - raised), ViewModel.HIP_LONG_Y + (-0.02f - ViewModel.HIP_LONG_Y) * raised, -(hip + (aimed - hip) * raised)};
        float[] rolled = new float[9], pitched = new float[9], yawed = new float[9];
        ArmPose.about(new float[] {0f, 0f, -1f}, tip[2], rolled);
        ArmPose.about(new float[] {1f, 0f, 0f}, tip[0], pitched);
        ArmPose.about(new float[] {0f, 1f, 0f}, tip[1], yawed);
        float[] pointing = turned(yawed, turned(pitched, turned(rolled, new float[] {0f, 0f, -1f}))), over = turned(yawed, turned(pitched, turned(rolled, new float[] {0f, 1f, 0f})));
        float[] grip = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            grip[axis] = line[axis] - over[axis] * gun[1] * SCALE;
        }
        return held(LONG, grip, pointing, over, gun[2], drawn);
    }

    /** The test long gun, its grip at a place in the view, its fore-end held `ahead` of its grip and drawn back by `drawn`, in metres. */
    private static final WeaponShape LONG = TestGuns.shape(TestGuns.longGun(false)), PISTOL = TestGuns.shape(TestGuns.pistol());

    private static ArmHolds.Weapon held(WeaponShape shape, float[] grip, float[] pointing, float[] over, float ahead, float drawn) {
        ArmHolds.Placed placed = TestGuns.placed(shape, grip, pointing, over, SCALE, drawn, shape.grip[1] + ahead);
        return new ArmHolds.Weapon(true, shape == PISTOL, placed.at(shape.grip), pointing, over, placed);
    }

    /** Where the hand under a gun's fore-end is held, drawn back as it is. */
    private static float[] foreEndOf(ArmHolds.Weapon gun) {
        ArmHolds.Placed placed = gun.placed();
        float a = placed.foreRests(ArmActs.PUMP) - placed.slid();
        return placed.at(a, placed.overUnder(a), 0f);
    }

    private static ArmHolds.Doing holding(float raised) {
        return new ArmHolds.Doing(raised, true, ArmActs.PUMP, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f, 0f);
    }

    /** Whether a place, with `clear` metres round it, is out of the picture of an eye that looks the way the aim does: behind the eye, or past an edge of the picture. */
    private static boolean outOfThePicture(float[] at, float clear) {
        float depth = -at[2];
        return depth <= 0f || Math.abs(at[1]) - clear * SCALE > depth * PICTURE || Math.abs(at[0]) - clear * SCALE > depth * PICTURE * WIDE;
    }

    /** A place as a head tipped down by so many degrees from the aim sees it. */
    private static float[] tippedDown(float[] at, float degrees) {
        float[] turn = new float[9];
        ArmPose.about(new float[] {1f, 0f, 0f}, (float) Math.toRadians(degrees), turn);
        return turned(turn, at);
    }

    /** How far ahead of a weapon's grip, along its barrel, the left hand placed by `skins` holds it, in metres. */
    private static float aheadOfTheGrip(ArmModel model, float[] skins, ArmHolds.Weapon weapon) {
        float[] on = placed(skins, model.wrist, model.holds[model.pose("fore")]);
        return ArmPose.dot(new float[] {on[0] - weapon.grip()[0], on[1] - weapon.grip()[1], on[2] - weapon.grip()[2]}, weapon.pointing()) / SCALE;
    }

    /** These are what the arms are measured by everywhere else here, so they are held to what a body is. */
    private static void armsAreTheSizeArmsAreAndStartWhereShouldersAre(ArmModel model) {
        float shortest = model.upperLength * ArmPose.UPPER_SHORTEST, longest = model.upperLength * ArmPose.UPPER_LONGEST;
        check(shortest > 0.18f && longest < 0.34f && longest > model.foreLength, "an upper arm is drawn between 0.18 m and 0.34 m long, and at its longest is longer than the forearm: " + shortest + " to " + longest);
        check(ArmPose.SHOULDER_GIVES > 0f && ArmPose.SHOULDER_GIVES <= 0.05f, "a shoulder gives no more than five centimetres");
        check(ArmPose.WRIST_MOST <= Math.toRadians(35) && ArmHolds.REACH_EASY < ArmPose.STRAIGHTEST && ArmPose.STRAIGHTEST < 1f, "a hand asks for its wrist bent no more than 35 degrees, and an arm is never quite straight");
        check(ArmHolds.NEAREST_FORE >= 0.14f, "the hand under a fore-end comes no nearer the middle of the grip than clears the hand that is on it");
        float[][] shoulders = {{ArmHolds.SHOULDER[0], ArmHolds.SHOULDER[1], ArmHolds.SHOULDER[2]}, {-ArmHolds.SHOULDER[0], ArmHolds.SHOULDER[1], ArmHolds.SHOULDER[2]},
            ArmHolds.LONG_SHOULDERS[0], ArmHolds.LONG_SHOULDERS[1], ArmHolds.LONG_LEFT_SHOULDER_AIMED};
        for (float[] shoulder : shoulders) {
            check(Math.abs(shoulder[0]) >= 0.13f && Math.abs(shoulder[0]) <= 0.22f && shoulder[1] <= -0.18f && shoulder[1] >= -0.28f && shoulder[2] >= -0.03f && shoulder[2] <= 0.15f,
                    "a shoulder is 13 to 22 cm to its side of the eye, 18 to 28 cm under it and no more than 3 cm ahead of it: " + Arrays.toString(shoulder));
        }
        check(ArmHolds.LONG_SHOULDERS[0][0] > 0f && ArmHolds.LONG_SHOULDERS[1][0] < 0f && ArmHolds.LONG_SHOULDERS[1][2] < ArmHolds.LONG_SHOULDERS[0][2],
                "behind a long gun the right one is on the right and the left one is the further forward");
    }

    private static void armsOnALongGunAreArmsFromShouldersOutOfThePicture(ArmModel model) {
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        for (int which = 0; which < LONG_GUNS.length; which++) {
            for (float raised = 0f; raised < 1.01f; raised += 0.125f) {
                for (int tipped = 0; tipped < TIPS.length; tipped++) {
                    // At the hip a gun is tipped over towards the middle, less as it comes up.
                    float[] tip = tipped == 0 ? new float[] {0f, 0f, (float) Math.toRadians(8) * (1f - raised)} : TIPS[tipped];
                    String how = LONG_GUN_NAMES[which] + " raised " + raised + ", tipped the way numbered " + tipped;
                    check(ArmHolds.place(model, SCALE, longGun(LONG_GUNS[which], raised, tip, 0f), holding(raised), SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled()) == 2,
                            "a long gun has both arms: " + how);
                    for (int side = 0; side < 2; side++) {
                        String arm = (side == 0 ? "the right arm on " : "the left arm on ") + how;
                        float[] shoulder = placed(skins[side], model.upper, model.shoulder), elbow = placed(skins[side], model.upper, model.elbow);
                        float[] wrist = placed(skins[side], model.fore, model.hand);
                        float drawn = said[side][ArmPose.DRAWN];
                        check(near(ArmPose.apart(elbow, wrist), model.foreLength * SCALE, 1e-4f), "the forearm is as long as it is: " + arm);
                        check(drawn >= ArmPose.UPPER_SHORTEST && drawn <= ArmPose.UPPER_LONGEST && near(ArmPose.apart(shoulder, elbow), model.upperLength * drawn * SCALE, 1e-4f),
                                "the upper arm is as long as it says, which is a length upper arms are: " + arm + ", " + drawn);
                        for (int bone : new int[] {model.fore, model.foreTwist}) {
                            for (int column = 0; column < 3; column++) {
                                float x = skins[side][bone * 16 + column * 4], y = skins[side][bone * 16 + column * 4 + 1], z = skins[side][bone * 16 + column * 4 + 2];
                                check(near((float) Math.sqrt(x * x + y * y + z * z), SCALE, 1e-4f), "the bone " + model.bones[bone] + " is drawn out no way: " + arm);
                            }
                        }
                        float[] rests = ArmHolds.longShoulder(side, raised);
                        float moved = ArmPose.apart(shoulder, new float[] {rests[0] * SCALE, rests[1] * SCALE, rests[2] * SCALE});
                        check(moved <= ArmPose.SHOULDER_GIVES * SCALE + 1e-4f && near(moved, said[side][ArmPose.GAVE], 1e-4f),
                                "it starts at the shoulder behind a long gun, as near as a shoulder gives, and says how far that is: " + arm + ", " + moved);
                        // One of the game's long guns has its sights so far from the eye that the arm does not reach even the back of its fore-end
                        // with them up, and the shoulder comes after the hand: it is held to less.
                        float tips = which < 2 ? 30f : tipped == 0 ? 20f : 0f;
                        for (float down = 0f; down <= tips; down += 10f) {
                            check(outOfThePicture(tippedDown(shoulder, down), BALL),
                                    "the ball of that shoulder is out of the picture, so the arm runs out of it and does not end in it, with the head tipped down " + down + " degrees: " + arm);
                        }
                        check(which == 2 || outOfThePicture(tippedDown(shoulder, 40f), 0f), "and tipped down 40 the middle of it still is: " + arm);
                        float bent = (float) Math.toDegrees(said[side][ArmPose.BENT]);
                        check(bent < (side == 1 ? 33f : tipped == 0 ? 42f : 62f), "its wrist is bent no further than a wrist goes: " + arm + ", " + bent);
                    }
                    ArmHolds.Weapon held = longGun(LONG_GUNS[which], raised, tip, 0f);
                    float[] palm = placed(skins[1], model.wrist, model.holds[model.pose("fore")]);
                    float[] into = placed(skins[1], model.wrist, new float[] {model.holds[model.pose("fore")][0] + model.out[0], model.holds[model.pose("fore")][1] + model.out[1],
                        model.holds[model.pose("fore")][2] + model.out[2]});
                    float[] faces = {(into[0] - palm[0]) / SCALE, (into[1] - palm[1]) / SCALE, (into[2] - palm[2]) / SCALE};
                    check(ArmPose.dot(faces, held.over()) > 0.4f && Math.abs(ArmPose.dot(faces, held.pointing())) < 1e-3f,
                            "the palm of the hand under the fore-end faces up into it, square to the barrel: " + how + ", " + ArmPose.dot(faces, held.over()));
                    float ahead = aheadOfTheGrip(model, skins[1], held);
                    check(ahead >= ArmHolds.NEAREST_FORE - 1e-4f && ahead <= LONG_GUNS[which][2] + 1e-4f, "that hand is between the grip and where the fore-end is held: " + how + ", " + ahead);
                    if (raised == 0f && tipped == 0) {
                        float[] elbow = {said[1][ArmPose.ELBOW], said[1][ArmPose.ELBOW + 1], said[1][ArmPose.ELBOW + 2]};
                        check(outOfThePicture(elbow, UNDER), "at the hip the middle of the left elbow is under the picture and the upper arm with it: the forearm is what comes up into it: " + how);
                    }
                }
            }
        }
    }

    private static void handUnderAForeEndComesBackToWhereItsArmReaches(ArmModel model) {
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        float[] fore = model.holds[model.pose("fore")];
        float[] rests = {ArmHolds.LONG_SHOULDERS[1][0] * SCALE, ArmHolds.LONG_SHOULDERS[1][1] * SCALE, ArmHolds.LONG_SHOULDERS[1][2] * SCALE};
        float easy = ArmPose.whole(model) * SCALE * ArmHolds.REACH_EASY;
        float nearest = 0f;
        // The same gun with its fore-end held further and further out along its barrel.
        for (float out = 0.2f; out < 0.9f; out += 0.05f) {
            float[] grip = {0.1f, -0.16f, -0.2f};
            ArmHolds.Weapon gun = held(LONG, grip, new float[] {0f, 0f, -1f}, new float[] {0f, 1f, 0f}, out, 0f);
            float[] end = foreEndOf(gun);
            check(ArmHolds.place(model, SCALE, gun, holding(0f), SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled()) == 2, "a long gun has both arms however long it is");
            float[] hand = placed(skins[1], model.wrist, fore);
            float[] wrist = {said[1][ArmPose.WRIST], said[1][ArmPose.WRIST + 1], said[1][ArmPose.WRIST + 2]};
            check(near(hand[0], grip[0], 1e-4f) && hand[1] > grip[1], "the left hand is under the barrel, in the upright through it");
            float ahead = (grip[2] - hand[2]) / SCALE;
            check(ahead <= out + 1e-4f && ahead >= ArmHolds.NEAREST_FORE - 1e-4f, "no further out than the fore-end is, and no nearer the grip than a hand comes: " + ahead);
            if (ahead < out - 1e-3f) {
                // Brought back off the bare barrel onto the fore-end under it, the hand is lower, and is held as near its easy reach as a few passes bring it.
                check(nearest == 0f || near(ahead, nearest, 5e-3f), "a fore-end out of reach has the hand as far out as the arm reaches, wherever the fore-end is: " + ahead + " against " + nearest + " out " + out);
                nearest = ahead;
                check(ArmPose.apart(rests, wrist) <= easy + 1e-3f && ArmPose.apart(rests, wrist) > easy - 5e-3f * SCALE, "and its wrist is then as far from where the shoulder rests as the arm reaches easily: " + ArmPose.apart(rests, wrist) / SCALE + " against " + easy / SCALE + " out " + out + " ahead " + ahead);
            } else {
                check(nearest == 0f, "a fore-end within reach has the hand on it");
                check(ArmPose.apart(rests, wrist) <= easy + 1e-3f, "which is no further than the arm reaches easily");
            }
            check(said[1][ArmPose.GAVE] <= ArmPose.SHOULDER_GIVES * SCALE + 1e-5f, "and the shoulder is where a shoulder is, as near as one gives");
        }
        check(nearest > 0.3f, "the arm reaches a good way along the barrel: " + nearest);
    }

    private static void handOnAPumpIsOnItAndGoesWithIt(ArmModel model) {
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        float[] fore = model.holds[model.pose("fore")], pump = LONG_GUNS[1];
        // The mod's own pump gun has its fore-end 0.195 m long about where it is held, and draws it back 0.094 m.
        float behind = pump[2] - 0.0975f, travel = 0.094f;
        for (float raised = 0f; raised < 1.01f; raised += 0.25f) {
            float[] tip = {0f, 0f, (float) Math.toRadians(8) * (1f - raised)};
            ArmHolds.Weapon rests = longGun(pump, raised, tip, 0f), back = longGun(pump, raised, tip, travel);
            check(ArmHolds.place(model, SCALE, rests, holding(raised), SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled()) == 2, "a pump gun has both arms");
            float[] on = placed(skins[1], model.wrist, fore);
            float[] from = {on[0] - rests.grip()[0], on[1] - rests.grip()[1], on[2] - rests.grip()[2]};
            float ahead = ArmPose.dot(from, rests.pointing()) / SCALE;
            check(ahead > behind + 0.01f && ahead <= pump[2] + 1e-4f, "its left hand is on its fore-end, ahead of the back of it, raised " + raised + ": " + ahead + " against " + behind);
            check(ArmHolds.place(model, SCALE, back, holding(raised), SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled()) == 2, "and has both with the fore-end drawn back");
            float[] drawn = placed(skins[1], model.wrist, fore);
            float[] moved = {on[0] - drawn[0], on[1] - drawn[1], on[2] - drawn[2]};
            check(near(ArmPose.dot(moved, rests.pointing()), travel * SCALE, 1e-3f) && near(ArmPose.apart(on, drawn), travel * SCALE, 1e-3f),
                    "the hand goes back with the fore-end, as far as it goes and along the barrel, raised " + raised + ": " + ArmPose.apart(on, drawn) / SCALE);
            // A shot kicks the whole gun back along its barrel and a step sways it. Where it is held still is where it was.
            for (float kick : new float[] {0.06f, -0.03f}) {
                float[] kickedGrip = new float[3], toView = rests.placed().toView().clone();
                for (int axis = 0; axis < 3; axis++) {
                    float by = -rests.pointing()[axis] * kick * SCALE + rests.over()[axis] * 0.01f * SCALE;
                    kickedGrip[axis] = rests.grip()[axis] + by;
                    toView[12 + axis] += by;
                }
                ArmHolds.Placed still = rests.placed();
                ArmHolds.Weapon kicked = new ArmHolds.Weapon(true, false, kickedGrip, rests.pointing(), rests.over(),
                        new ArmHolds.Placed(still.shape(), toView, still.still(), still.slid(), still.foreRest(), still.travel()));
                check(ArmHolds.place(model, SCALE, kicked, holding(raised), SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled()) == 2, "a gun that a shot has kicked has both arms");
                check(near(aheadOfTheGrip(model, skins[1], kicked), ahead, 1e-3f), "its left hand is at the same place on its fore-end as before the shot, kicked " + kick + ", raised " + raised
                        + ": " + aheadOfTheGrip(model, skins[1], kicked) + " against " + ahead);
            }
        }
        // A fore-end the arm reaches wherever it is has the hand on it wherever it is.
        float[] grip = {0.1f, -0.16f, -0.2f};
        ArmHolds.Weapon near = held(LONG, grip, new float[] {0f, 0f, -1f}, new float[] {0f, 1f, 0f}, 0.25f, 0.05f);
        check(ArmHolds.place(model, SCALE, near, holding(0f), SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled()) == 2
                && ArmPose.apart(placed(skins[1], model.wrist, fore), foreEndOf(near)) < 1e-4f, "a fore-end within reach, drawn back, has the hand on it where it is and not where it rests");
        // One of the game's pump guns with the sights up has its fore-end out of reach: the hand is as near the grip as a hand comes, and stays clear of the grip with the fore-end back.
        ArmHolds.Weapon far = longGun(LONG_GUNS[2], 1f, new float[3], 0.108f);
        check(ArmHolds.place(model, SCALE, far, holding(1f), SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled()) == 2, "one of the game's pump guns has both arms with its fore-end back");
        float clear = aheadOfTheGrip(model, skins[1], far);
        check(clear >= ArmHolds.NEAREST_FORE - 1e-4f && clear <= LONG_GUNS[2][2] - 0.108f + 1e-4f, "its left hand is no nearer the grip than a hand comes, and no further out than the fore-end is: " + clear);
    }

    private static void upperArmIsAsLongAsTheHoldNeedsWithinWhatUpperArmsAre(ArmModel model) {
        float[] shoulder = {0.14f, -0.16f, 0.05f}, pole = {0.25f, -0.42f, 0.09f}, across = {0f, 0.98f, -0.2f}, out = {-1f, 0f, 0f};
        float[] skins = new float[model.bones.length * 16], said = new float[ArmPose.SAYS];
        int exact = 0, short3 = 0, long3 = 0;
        for (float ahead = 0.02f; ahead < 1.5f; ahead += 0.04f) {
            check(ArmPose.solve(model, SCALE, only(model, "grip"), new float[] {0.13f, -0.14f, -ahead}, across, out, shoulder, pole, false, false, skins, said), "a hand has an arm however far off it is: " + ahead);
            float[] starts = placed(skins, model.upper, model.shoulder), elbow = placed(skins, model.upper, model.elbow), wrist = placed(skins, model.fore, model.hand);
            float drawn = said[ArmPose.DRAWN], upper = model.upperLength * drawn * SCALE;
            check(near(ArmPose.apart(elbow, wrist), model.foreLength * SCALE, 1e-4f) && near(ArmPose.apart(starts, elbow), upper, 1e-4f),
                    "its forearm is as long as it is and its upper arm as long as it says: " + ahead);
            check(drawn >= ArmPose.UPPER_SHORTEST && drawn <= ArmPose.UPPER_LONGEST, "which is a length upper arms are: " + drawn);
            check(near(ArmPose.apart(starts, shoulder), said[ArmPose.GAVE], 1e-4f), "it says how far its shoulder is from where it was asked for");
            if (drawn > ArmPose.UPPER_SHORTEST && drawn < ArmPose.UPPER_LONGEST) {
                exact++;
                check(said[ArmPose.GAVE] < 1e-4f && said[ArmPose.BENT] <= ArmPose.WRIST_MOST + 1e-3f,
                        "an elbow the upper arm reaches leaves the shoulder where it is, and the wrist bent no further than a hand would have it: " + ahead);
            } else if (drawn == ArmPose.UPPER_SHORTEST) {
                short3++;
            } else {
                long3++;
                float straight = (model.upperLength * ArmPose.UPPER_LONGEST + model.foreLength) * SCALE * ArmPose.STRAIGHTEST;
                if (ArmPose.apart(shoulder, wrist) > straight + ArmPose.SHOULDER_GIVES * SCALE) {
                    check(near(ArmPose.apart(starts, wrist), straight, 1e-4f), "out of reach, the arm is as straight as an arm goes: " + ahead);
                    float[] line = {wrist[0] - shoulder[0], wrist[1] - shoulder[1], wrist[2] - shoulder[2]}, moved = {starts[0] - shoulder[0], starts[1] - shoulder[1], starts[2] - shoulder[2]};
                    check(ArmPose.dot(line, moved) > 0.99f * ArmPose.apart(shoulder, wrist) * said[ArmPose.GAVE], "and the shoulder has come after the hand");
                }
            }
        }
        check(exact > 2 && short3 > 0 && long3 > 5, "the hands asked for were some within reach, some too near and some too far: " + exact + ", " + short3 + ", " + long3);
        check(shoulder[0] == 0.14f && shoulder[1] == -0.16f && shoulder[2] == 0.05f, "the shoulder asked for is the caller's own and is not written to");
    }

    private static void elbowIsWhereTheTwoBonesMeet() {
        float upper = 0.3f, fore = 0.26f;
        float[] from = {0.5f, -1f, 0f};
        float[] shoulder = {0f, 0f, 0f}, wrist = {0f, 0f, -0.4f}, elbow = {0.1f, -0.3f, -0.2f};
        check(ArmPose.jointed(shoulder, wrist, upper, fore, from, elbow) && shoulder[0] == 0f && shoulder[1] == 0f && shoulder[2] == 0f, "a wrist within reach has an elbow and leaves the shoulder be");
        check(near(ArmPose.apart(shoulder, elbow), upper, 1e-5f) && near(ArmPose.apart(elbow, wrist), fore, 1e-5f), "the upper arm's length from the shoulder and the forearm's from the wrist");
        check(elbow[0] > 0f && elbow[1] < 0f && near(elbow[1] / elbow[0], -3f, 1e-3f), "on the side of the line between them that it was asked for");
        // Asked for on the line itself, which is no one way off it, it goes the way it was to come from.
        float[] onLine = {0f, 0f, -0.2f};
        check(ArmPose.jointed(shoulder, wrist, upper, fore, from, onLine), "an elbow asked for on the line from shoulder to wrist is still an elbow");
        check(near(ArmPose.apart(shoulder, onLine), upper, 1e-5f) && onLine[0] > 0f && near(onLine[1] / onLine[0], -2f, 1e-3f), "off it towards where the forearm was to come from");
        check(!ArmPose.jointed(shoulder, wrist, upper, fore, new float[] {0f, 0f, 1f}, new float[] {0f, 0f, -0.2f}), "with that on the line too there is no saying where the elbow is");
        check(!ArmPose.jointed(shoulder, new float[] {0f, 0f, 0f}, upper, fore, from, new float[] {0.1f, -0.3f, -0.2f}), "nor for a wrist at the shoulder");
        // Out of reach, and nearer than the two bones fold to.
        float[] far = {0f, 0f, 0f}, farElbow = {0.1f, -0.3f, -0.4f};
        check(ArmPose.jointed(far, new float[] {0f, 0f, -0.9f}, upper, fore, from, farElbow), "a wrist out of reach has an arm");
        check(near(far[2], -(0.9f - (upper + fore) * ArmPose.STRAIGHTEST), 1e-5f) && far[0] == 0f && far[1] == 0f, "whose shoulder has come along the line to it by what the arm was short");
        check(near(ArmPose.apart(far, farElbow), upper, 1e-5f) && near(ArmPose.apart(farElbow, new float[] {0f, 0f, -0.9f}), fore, 1e-5f), "and both bones are still their own lengths");
        float[] close = {0f, 0f, 0f}, closeElbow = {0.1f, -0.3f, 0f};
        check(ArmPose.jointed(close, new float[] {0f, 0f, -0.01f}, upper, fore, from, closeElbow) && close[2] > 0f, "a wrist nearer than the arm folds to sends the shoulder back from it");
        check(near(ArmPose.apart(close, closeElbow), upper, 1e-5f) && near(ArmPose.apart(closeElbow, new float[] {0f, 0f, -0.01f}), fore, 1e-5f), "with both bones their own lengths");
    }

    private static void wristIsWhereTheArmPutsIt(ArmModel model) {
        float[] centre = {0.05f, -0.2f, -0.35f}, across = {0.2f, 0.1f, -1f}, out = {0.3f, 0.9f, 0.1f}, shoulder = {0.14f, -0.16f, 0.05f}, from = {0.3f, -0.45f, 0f};
        float[] skins = new float[model.bones.length * 16], said = new float[ArmPose.SAYS], wrist = new float[3];
        for (int way = 0; way < 4; way++) {
            boolean left = way % 2 == 1, round = way >= 2;
            float side = left ? -1f : 1f;
            float[] c = {centre[0] * side, centre[1], centre[2]}, a = {across[0] * side, across[1], across[2]}, o = {out[0] * side, out[1], out[2]};
            float[] s = {shoulder[0] * side, shoulder[1], shoulder[2]}, f = {from[0] * side, from[1], from[2]};
            String pose = round ? "handle" : "fore";
            check(ArmPose.solve(model, SCALE, only(model, pose), c, a, o, s, f, left, round, skins, said) && ArmPose.wrist(model, SCALE, only(model, pose), c, a, o, f, left, round, wrist),
                    "a hand is placed with an arm and without");
            check(near(wrist[0], said[ArmPose.WRIST], 1e-5f) && near(wrist[1], said[ArmPose.WRIST + 1], 1e-5f) && near(wrist[2], said[ArmPose.WRIST + 2], 1e-5f),
                    "its wrist is in the same place either way, " + (left ? "left" : "right") + (round ? " on a handle" : " on a fore-end"));
        }
    }

    private static void longGunIsHeldNearerAtTheHipThanItsSightsAsk() {
        check(ViewModel.hipAhead(ViewModel.AIMED_AHEAD) == ViewModel.HIP_AHEAD && ViewModel.HIP_AHEAD < ViewModel.AIMED_AHEAD,
                "a long gun whose sights are far from the eye is held at the hip where every weapon is");
        check(ViewModel.hipAhead(0.22f) == 0.22f, "one whose grip is nearer than that with the sights up is held as near at the hip, where the arm that reaches its fore-end with the sights up reaches it still");
        check(ViewModel.hipAhead(0.05f) == ViewModel.HIP_NEAREST && ViewModel.HIP_NEAREST < ViewModel.HIP_AHEAD, "and none is held nearer than the nearest");
    }

    private static void handCrossesToAHoldThatIsNotBesideItAndDoesNotJump(ArmModel model) {
        ArmHolds.Weapon gun = weapon(false);
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        ArmHolds.Carried carried = new ArmHolds.Carried();
        ArmHolds.Settled settled = new ArmHolds.Settled();
        settled.seconds = 0.02f;
        ArmHolds.Doing ready = new ArmHolds.Doing(0f, true, ArmActs.PUMP, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f, 0f);
        ArmHolds.Doing loading = new ArmHolds.Doing(0f, true, ArmActs.PUMP, -1f, ArmActs.LOADING, 0.5f, 0f, 1f, -1f, false, -1f, -1f, 2f, 0f);
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
        ArmHolds.Weapon bat = new ArmHolds.Weapon(false, false, grip, pointing, new float[] {0.3f, 0.6f, 0.8f}, null);
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        ArmHolds.Carried carried = new ArmHolds.Carried();
        float[] handle = model.holds[model.pose("handle")];
        check(ArmHolds.place(model, SCALE, bat, new ArmHolds.Doing(0f, false, ArmActs.NO_ACTION, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f, 0f), SAME, skins, said, carried, new ArmHolds.Settled()) == 1,
                "something swung in one hand has one arm");
        check(ArmHolds.place(model, SCALE, bat, new ArmHolds.Doing(0f, true, ArmActs.NO_ACTION, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f, 0f), SAME, skins, said, carried, new ArmHolds.Settled()) == 2,
                "and in both hands, two");
        float[] right = placed(skins[0], model.wrist, handle), left = placed(skins[1], model.wrist, handle);
        check(ArmPose.apart(right, grip) < 1e-4f, "the right fist is where it is held");
        float[] down = {left[0] - right[0], left[1] - right[1], left[2] - right[2]};
        check(near(ArmPose.apart(left, right), ArmHolds.LOWER_FIST * SCALE, 1e-4f) && ArmPose.dot(down, pointing) < 0f, "and the left one is under it on the handle");
        check(said[1][ArmPose.ELBOW] < said[0][ArmPose.ELBOW], "the left elbow is to the left of the right one");
    }

    /** A long gun held level before the eye, pointing straight ahead, its fore-end within reach, and a handgun the same. */
    private static ArmHolds.Weapon weapon(boolean handgun) {
        float[] grip = {0.1f, -0.14f, handgun ? -0.3f : -0.2f};
        return held(handgun ? PISTOL : LONG, grip, new float[] {0f, 0f, -1f}, new float[] {0f, 1f, 0f}, 0.27f, 0f);
    }

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
        ArmHolds.Doing held = new ArmHolds.Doing(0f, true, ArmActs.BOLT_SINGLE, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f, 0f);
        check(ArmHolds.place(model, SCALE, rifle, held, SAME, skins, said, carried, new ArmHolds.Settled()) == 2, "a rifle held in both hands has both arms");
        check(ArmPose.apart(placed(skins[0], model.wrist, grip), rifle.grip()) < 1e-4f, "with the right hand on its grip");
        // Half way through working the bolt it is drawn right back, with its handle turned up.
        float back = ArmActs.BOLT_AFTER + OwnRifle.WORKED_FOR / 2f;
        ArmHolds.Doing working = new ArmHolds.Doing(0f, true, ArmActs.BOLT_SINGLE, back, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f, 0f);
        check(ArmHolds.place(model, SCALE, rifle, working, SAME, skins, said, carried, new ArmHolds.Settled()) == 2, "while its bolt is worked it still has both");
        WeaponShape shape = rifle.placed().shape();
        float out = Math.abs(shape.knob[0]), up = shape.knob[2] - shape.bore, turned = ArmHolds.BOLT_LIFT;
        float[] knob = rifle.placed().at(shape.knob[1] - ArmHolds.BOLT_TRAVEL, shape.bore + out * (float) Math.sin(turned) + up * (float) Math.cos(turned),
                Math.signum(shape.knob[0]) * (out * (float) Math.cos(turned) - up * (float) Math.sin(turned)));
        float[] rests = rifle.placed().at(shape.knob);
        check(ArmPose.apart(placed(skins[0], model.wrist, pinch), knob) < 1e-4f, "and the right hand is on the knob, where the knob then is: up and back");
        check(knob[1] > rests[1] && knob[2] > rests[2], "which is higher than it rests and nearer the eye");
        ArmHolds.Doing over = new ArmHolds.Doing(0f, true, ArmActs.BOLT_SINGLE, ArmActs.BOLT_AFTER + OwnRifle.WORKED_FOR + ArmActs.BOLT_LEFT_IN + 0.01f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f, 0f);
        check(ArmHolds.place(model, SCALE, rifle, over, SAME, skins, said, carried, new ArmHolds.Settled()) == 2
                && ArmPose.apart(placed(skins[0], model.wrist, grip), rifle.grip()) < 1e-4f, "when the bolt is shut the hand is back on the grip");
        // A pump's action is the other hand's: the right stays where it is.
        ArmHolds.Doing pumped = new ArmHolds.Doing(0f, true, ArmActs.PUMP, back, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f, 0f);
        check(ArmHolds.place(model, SCALE, rifle, pumped, SAME, skins, said, carried, new ArmHolds.Settled()) == 2
                && ArmPose.apart(placed(skins[0], model.wrist, grip), rifle.grip()) < 1e-4f, "a pump gun's right hand never leaves its grip");
    }

    private static void leftHandComesToAPistolOnlyToLoadItAndBringsTheMagazine(ArmModel model) {
        ArmHolds.Weapon pistol = weapon(true);
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        ArmHolds.Carried carried = new ArmHolds.Carried();
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f, 0f), SAME, skins, said, carried, new ArmHolds.Settled()) == 1
                && carried.what == ArmHolds.NOTHING, "a pistol held in one hand has one arm, carrying nothing");
        // A pistol with nothing in it, being loaded: a magazine has been on its way for a tenth of a second, then for longer than the game was taken to take.
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.LOADING, 0.1f, 0f, 1f, -1f, false, -1f, 0.1f, 2f, 0f), SAME, skins, said,
                carried, new ArmHolds.Settled()) == 2 && carried.what == ArmHolds.MAGAZINE, "while it is loaded the left hand comes to it with a magazine");
        float[] magazine = model.holds[model.pose("magazine")], line = pistol.placed().way(PISTOL.gripLine), foot = pistol.placed().at(PISTOL.gripFoot);
        // The fist round a magazine at the well is under the grip's foot, all of it.
        float[] at = {foot[0] - line[0] * ArmHolds.HANDGUN_MAGAZINE_UNDER * SCALE, foot[1] - line[1] * ArmHolds.HANDGUN_MAGAZINE_UNDER * SCALE,
            foot[2] - line[2] * ArmHolds.HANDGUN_MAGAZINE_UNDER * SCALE};
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.LOADING, 5f, 0f, 1f, -1f, false, -1f, 5f, 2f, 0f), SAME, skins, said,
                carried, new ArmHolds.Settled()) == 2 && carried.what == ArmHolds.MAGAZINE, "which it still has however long the game takes over it");
        float[] under = {at[0] - line[0] * ArmActs.MAGAZINE_DRAWN * SCALE, at[1] - line[1] * ArmActs.MAGAZINE_DRAWN * SCALE, at[2] - line[2] * ArmActs.MAGAZINE_DRAWN * SCALE};
        check(ArmPose.apart(placed(skins[1], model.wrist, magazine), under) < 1e-3f, "held under the well, along the grip's line");
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.HOLDING, 0f, 0f, 1f, 0.15f, true, -1f, -1f, 2f, 0f), SAME, skins, said,
                carried, new ArmHolds.Settled()) == 2 && carried.what == ArmHolds.NOTHING, "once the game has loaded it the magazine is in the pistol");
        check(ArmPose.apart(placed(skins[1], model.wrist, magazine), at) < 1e-3f, "the hand then at the well it went into");
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f), SAME, skins, said,
                carried, new ArmHolds.Settled()) == 1, "and gone again when that is over");
        // A pistol with a magazine in it, being unloaded: the hand goes to the magazine, and has it once the game says it is out.
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.UNLOADING, 0.4f, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f), SAME, skins, said,
                carried, new ArmHolds.Settled()) == 2 && carried.what == ArmHolds.NOTHING, "to unload it the left hand comes with nothing");
        check(ArmPose.apart(placed(skins[1], model.wrist, magazine), at) < 1e-3f, "and takes hold of the magazine where it is, in the well");
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.UNLOADING, 0.6f, 0f, 1f, -1f, false, 0.2f, -1f, 2f, 0f), SAME, skins, said,
                carried, new ArmHolds.Settled()) == 2 && carried.what == ArmHolds.MAGAZINE, "and goes off with it when the game says it is out");
        check(placed(skins[1], model.wrist, magazine)[1] < at[1] - 0.05f, "downwards");
        check(ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.RACKING, 0.3f, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f), SAME, skins, said,
                carried, new ArmHolds.Settled()) == 2 && carried.what == ArmHolds.NOTHING, "it comes empty to draw the slide back");
        // The magazine stands in the fist the size it is.
        ArmHolds.place(model, SCALE, pistol, new ArmHolds.Doing(0f, false, ArmActs.HANDGUN, -1f, ArmActs.LOADING, 0.1f, 0f, 1f, -1f, false, -1f, 0.1f, 2f, 0f), SAME, skins, said, carried, new ArmHolds.Settled());
        float long3 = (float) Math.sqrt(carried.at[4] * carried.at[4] + carried.at[5] * carried.at[5] + carried.at[6] * carried.at[6]);
        check(near(long3, SCALE, 1e-4f), "and is drawn the size the arm is");
    }

    private static void leftHandCarriesATorchLowBesideAOneHandedWeapon(ArmModel model) {
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        TimedActs.Hand seen = new TimedActs.Hand();
        check(ArmHolds.carry(model, SCALE, SAME, new float[] {0f, 0f, -1f}, skins, said, seen), "a left arm carrying something beside a one-handed weapon is placed");
        float[] at = {ArmHolds.CARRY_AT[0] * SCALE, ArmHolds.CARRY_AT[1] * SCALE, ArmHolds.CARRY_AT[2] * SCALE};
        check(ArmPose.apart(placed(skins[1], model.wrist, model.holds[model.pose("handle")]), at) < 1e-3f, "its fist where the carry puts it");
        check(ArmPose.apart(seen.at, at) < 1e-6f, "and what it carries is put there");
        check(Math.abs(said[1][ArmPose.TWISTED]) <= ArmPose.TWIST_MOST + 1e-4f, "its forearm turned no further than a forearm turns");
        check(Math.abs(said[1][ArmPose.ASKED]) <= ArmPose.TWIST_MOST, "turned as it was asked, not turned back about its forearm");
        check(said[1][ArmPose.BENT] < Math.toRadians(36), "its wrist bent " + Math.toDegrees(said[1][ArmPose.BENT]) + " degrees, under 36");
        float[] along = new float[3], across = new float[3];
        ArmHolds.carried(seen, along, across);
        check(-along[2] > 0.99f, "what it carries points ahead, " + Arrays.toString(along));
        check(Math.abs(ArmPose.dot(along, across)) < 1e-5f && Math.abs(ArmPose.dot(across, across) - 1f) < 1e-5f, "and has a way across it, square to it");
        // A light that follows the head is pointed where it shines, by the arm swung from the shoulder.
        for (float[] turn : new float[][] {{-35f, 0f}, {35f, 0f}, {0f, 25f}, {0f, -25f}, {30f, 20f}, {-30f, -20f}}) {
            float yaw = (float) Math.toRadians(turn[0]), pitch = (float) Math.toRadians(turn[1]);
            float[] toward = {(float) (Math.sin(yaw) * Math.cos(pitch)), (float) Math.sin(pitch), (float) (-Math.cos(yaw) * Math.cos(pitch))};
            check(ArmHolds.carry(model, SCALE, SAME, toward, skins, said, seen), "carrying a light turned " + Arrays.toString(turn) + " the arm is placed");
            ArmHolds.carried(seen, along, across);
            check(ArmPose.dot(along, toward) > 0.99f, "and points it where it shines, " + Arrays.toString(along) + " for " + Arrays.toString(toward));
            check(Math.abs(said[1][ArmPose.ASKED]) <= ArmPose.TWIST_MOST && said[1][ArmPose.BENT] < Math.toRadians(36), "with the forearm and wrist as a carrying arm has them");
        }
    }

    private static void leftHandLeavesTheForeEndToFeedShellsAndIsBackOnItAfter(ArmModel model) {
        ArmHolds.Weapon gun = weapon(false);
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        ArmHolds.Carried carried = new ArmHolds.Carried();
        float[] fore = model.holds[model.pose("fore")];
        check(ArmHolds.place(model, SCALE, gun, new ArmHolds.Doing(0f, true, ArmActs.PUMP, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f, 0f), SAME, skins, said, carried, new ArmHolds.Settled()) == 2
                && ArmPose.apart(placed(skins[1], model.wrist, fore), foreEndOf(gun)) < 1e-4f, "a pump gun held ready has the left hand under its fore-end");
        check(ArmHolds.place(model, SCALE, gun, new ArmHolds.Doing(0f, true, ArmActs.PUMP, -1f, ArmActs.LOADING, 1f, 0.3f, 1f, -1f, false, -1f, -1f, 2f, 0f), SAME, skins, said, carried, new ArmHolds.Settled()) == 2
                && carried.what == ArmHolds.SHELL, "being loaded, the left hand carries a shell");
        float away = ArmPose.apart(placed(skins[1], model.wrist, fore), foreEndOf(gun));
        check(away > 0.05f, "and is off the fore-end");
        check(ArmHolds.place(model, SCALE, gun, new ArmHolds.Doing(0f, true, ArmActs.PUMP, -1f, ArmActs.LOADING, 1f, 0.8f, 1f, -1f, false, -1f, -1f, 2f, 0f), SAME, skins, said, carried, new ArmHolds.Settled()) == 2
                && carried.what == ArmHolds.NOTHING, "the shell is in the gun once it has been pushed in");
        check(ArmHolds.place(model, SCALE, gun, new ArmHolds.Doing(0f, true, ArmActs.BOLT_SINGLE, -1f, ArmActs.LOADING, 1f, 0.3f, 1f, -1f, false, -1f, -1f, 2f, 0f), SAME, skins, said, carried, new ArmHolds.Settled()) == 2
                && carried.what == ArmHolds.CARTRIDGE, "a rifle loaded round by round is fed cartridges");
        // Racking a pump is done by the hand that is on the fore-end, where it is.
        check(ArmHolds.place(model, SCALE, gun, new ArmHolds.Doing(0f, true, ArmActs.PUMP, -1f, ArmActs.RACKING, 0.2f, 0f, 1f, -1f, false, -1f, -1f, 2f, 0f), SAME, skins, said, carried, new ArmHolds.Settled()) == 2
                && ArmPose.apart(placed(skins[1], model.wrist, fore), foreEndOf(gun)) < 1e-4f, "to rack it the left hand stays under the fore-end");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
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
        check(near(said[ArmPose.GAVE], 0f, 1e-3f) && near(said[ArmPose.DRAWN], 1f, 1e-3f), "its shoulder where it was asked for, and its upper arm the length the model has it");
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
        check(near(ArmPose.apart(placed(skins, model.upper, model.shoulder), elbow), model.upperLength * scale * said[ArmPose.DRAWN], 1e-4f), "and the upper arm as long as it says it is drawn");
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

    /**
     * Something swung of each class, of the lengths the game has, held as ViewModel holds it in the mod's own hands, through
     * every way its class is hit with: no wrist bent past 45 degrees.
     */
    private static void everyMeleeClassKeepsItsWristsThroughEveryBlow(ArmModel model) {
        // Class, both hands, butt and tip in the mesh's units at scale 1: a hammer, a machete, a bat, a fire axe, a sledgehammer, a knife, a spear.
        float[][] weapons = {{MeleeHold.ONE_HANDED, 0f, -0.015f, 0.235f}, {MeleeHold.ONE_HANDED, 0f, -0.026f, 0.306f}, {MeleeHold.TWO_HANDED, 1f, -0.026f, 0.467f},
            {MeleeHold.TWO_HANDED, 1f, -0.025f, 0.388f}, {MeleeHold.HEAVY, 1f, -0.038f, 0.653f}, {MeleeHold.KNIFE, 0f, -0.028f, 0.248f}, {MeleeHold.SPEAR, 1f, -0.09f, 0.68f}};
        float perUnit = 1f / Characters.PER_METRE, tilesPerUnit = 1.5f;
        float worst = 0f;
        for (float[] w : weapons) {
            int kind = (int) w[0];
            boolean both = w[1] > 0f;
            MeleeHold.Shape shape = new MeleeHold.Shape(w[2], w[3], 0f, -1f);
            MeleeHold.Fit fit = MeleeHold.fit(kind, shape, perUnit, tilesPerUnit, true, PICTURE);
            for (int attack = ViewModelMath.SWUNG; attack <= ViewModelMath.SHOVED; attack++) {
                // Only the ways the game hits with each: a knife and a spear thrust, every class goes onto the floor and shoves, and a heavy weapon has no uppercut.
                boolean point = kind == MeleeHold.KNIFE || kind == MeleeHold.SPEAR;
                if (point ? attack != ViewModelMath.THRUST && attack != ViewModelMath.FLOORED && attack != ViewModelMath.SHOVED
                        : attack == ViewModelMath.THRUST || kind == MeleeHold.HEAVY && attack == ViewModelMath.RAISED) {
                    continue;
                }
                int way = MeleeHold.way(kind, attack);
                for (int frame = 0; frame <= 24; frame++) {
                    float t = frame / 24f, lands = 0.45f;
                    float reached = Math.min(t / lands, 1f), past = t <= lands ? 0f : (t - lands) / (1f - lands);
                    float[] pose = new float[ViewModelMath.HOLD], top = new float[3], matrix = new float[16];
                    ViewModelMath.attack(way, reached, past, false, fit.rest(), pose);
                    float share = MeleeHold.share(reached, past, false);
                    pose[1] += fit.aimed()[way] * share;
                    MeleeHold.top(shape, way, share, top);
                    float[] origin = {0f, fit.hand(), 0f};
                    ViewModelMath.hold(origin, new float[] {0f, 1f, 0f}, top, null, tilesPerUnit, pose[3], pose[4], pose[5], matrix);
                    ViewModelMath.turn(matrix, pose[0], pose[1], pose[2], pose[3], pose[4], -pose[5], 0f, 0f, 0f);
                    float[] grip = new float[3], pointing = new float[3], over = new float[3];
                    ViewModelMath.place(matrix, 0f, fit.hand(), 0f, grip);
                    ViewModelMath.turned(matrix, new float[] {0f, 1f, 0f}, pointing);
                    ViewModelMath.turned(matrix, top, over);
                    ArmPose.unit(pointing[0], pointing[1], pointing[2], pointing);
                    ArmPose.unit(over[0], over[1], over[2], over);
                    ArmHolds.Weapon held = new ArmHolds.Weapon(false, false, grip, pointing, over, null, fit.second(), kind == MeleeHold.SPEAR);
                    ArmHolds.Doing doing = new ArmHolds.Doing(0f, both, ArmActs.NO_ACTION, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f, pose[6]);
                    float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
                    int arms = ArmHolds.place(model, SCALE, held, doing, SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled());
                    check(arms == (both ? 2 : 1), "class " + kind + " way " + way + " frame " + frame + " has its arms");
                    for (int side = 0; side < arms; side++) {
                        float bent = (float) Math.toDegrees(said[side][ArmPose.BENT]);
                        worst = Math.max(worst, bent);
                        check(bent <= 45.5f, "class " + kind + " way " + way + " frame " + frame + (side == 0 ? " right" : " left") + " wrist bent " + bent);
                    }
                }
            }
        }
        check(worst > 30f, "and the wrists were measured: " + worst);
    }

    /**
     * The arms on something swung of a class, at a moment of an attack, held as ViewModel holds it in the mod's own hands:
     * the answer of ArmHolds.place, with `said` filled.
     */
    private static int swungArms(ArmModel model, int kind, boolean both, MeleeHold.Shape shape, int attack, float reached, float past, boolean struck, float[][] said) {
        MeleeHold.Fit fit = MeleeHold.fit(kind, shape, 1f / Characters.PER_METRE, 1.5f, true, PICTURE);
        int way = MeleeHold.way(kind, attack);
        float[] pose = new float[ViewModelMath.HOLD], top = new float[3], matrix = new float[16];
        ViewModelMath.attack(way, reached, past, struck, fit.rest(), pose);
        float share = MeleeHold.share(reached, past, struck);
        pose[1] += fit.aimed()[way] * share;
        MeleeHold.top(shape, way, share, top);
        ViewModelMath.hold(new float[] {0f, fit.hand(), 0f}, new float[] {0f, 1f, 0f}, top, null, 1.5f, pose[3], pose[4], pose[5], matrix);
        ViewModelMath.turn(matrix, pose[0], pose[1], pose[2], pose[3], pose[4], -pose[5], 0f, 0f, 0f);
        float[] grip = new float[3], pointing = new float[3], over = new float[3];
        ViewModelMath.place(matrix, 0f, fit.hand(), 0f, grip);
        ViewModelMath.turned(matrix, new float[] {0f, 1f, 0f}, pointing);
        ViewModelMath.turned(matrix, top, over);
        ArmPose.unit(pointing[0], pointing[1], pointing[2], pointing);
        ArmPose.unit(over[0], over[1], over[2], over);
        ArmHolds.Weapon held = new ArmHolds.Weapon(false, false, grip, pointing, over, null, fit.second(), kind == MeleeHold.SPEAR);
        ArmHolds.Doing doing = new ArmHolds.Doing(0f, both, ArmActs.NO_ACTION, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, false, -1f, -1f, 2f, pose[6]);
        float[][] skins = new float[2][model.bones.length * 16];
        return ArmHolds.place(model, SCALE, held, doing, SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled());
    }

    /**
     * A spear thrust, whether it lands on something or goes on through: the left elbow stays under the foot of the
     * picture, by at least the half thickness of an arm about it, and so does the upper arm from it to the shoulder, which
     * is lower still. Its shirt's sleeve came into the lower left of the picture as a dark flap while the shoulder was
     * beside the eye.
     */
    private static void aSpearsLeftArmStaysUnderThePictureThroughAThrust(ArmModel model) {
        MeleeHold.Shape spear = new MeleeHold.Shape(-0.09f, 0.68f, 0f, -1f);
        float highest = -Float.MAX_VALUE;
        for (boolean struck : new boolean[] {true, false}) {
            for (int frame = 0; frame <= 40; frame++) {
                float t = frame / 40f, lands = 0.45f;
                float reached = Math.min(t / lands, 1f), past = t <= lands ? 0f : (t - lands) / (1f - lands);
                float[][] said = new float[2][ArmPose.SAYS];
                check(swungArms(model, MeleeHold.SPEAR, true, spear, ViewModelMath.THRUST, reached, past, struck, said) == 2, "a spear is held in both hands, frame " + frame);
                float x = said[1][ArmPose.ELBOW], y = said[1][ArmPose.ELBOW + 1], z = said[1][ArmPose.ELBOW + 2];
                // How far above the foot of the picture the elbow is, at its distance ahead, in tiles: under it is less than nought.
                float above = y + -z * PICTURE;
                highest = Math.max(highest, above);
                check(above < -ARM_HALF * SCALE, (struck ? "struck" : "missed") + ", frame " + frame + ": the left elbow is under the picture: " + above + " (at " + x + ", " + y + ", " + z + ")");
                check(ArmHolds.SPEAR_SHOULDER[1] * SCALE < y, "and the shoulder is lower still");
            }
        }
        check(highest < 0f, "under the picture all the way: " + highest);
    }

    /** About half as thick as an arm is at the elbow, in metres. */
    private static final float ARM_HALF = 0.04f;

    /**
     * Something swung in both hands, the length of a bat, goes on through its swing with the left wrist bent no further
     * than a wrist goes: the swing's gone-through key bent it 49 degrees before it was eased.
     */
    private static void aTwoHandedSwingGoesThroughWithTheLeftWristEasy(ArmModel model) {
        MeleeHold.Shape bat = new MeleeHold.Shape(-0.026f, 0.467f, 0f, -1f);
        float worst = 0f;
        for (int frame = 0; frame <= 20; frame++) {
            float past = frame / 20f;
            float[][] said = new float[2][ArmPose.SAYS];
            check(swungArms(model, MeleeHold.TWO_HANDED, true, bat, ViewModelMath.SWUNG, 1f, past, false, said) == 2, "a bat is held in both hands");
            worst = Math.max(worst, (float) Math.toDegrees(said[1][ArmPose.BENT]));
        }
        check(worst <= 45f, "the left wrist through a bat's follow-through: " + worst);
    }
}
