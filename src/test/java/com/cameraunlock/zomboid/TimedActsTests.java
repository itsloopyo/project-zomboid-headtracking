package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class TimedActsTests {
    private TimedActsTests() {}

    static void run() throws IOException {
        ArmModel model;
        try (InputStream packed = TimedActsTests.class.getResourceAsStream("/models/arms.mesh.gz")) {
            model = new ArmModel(packed);
        }
        Checks.run("everyAnimationOfTheGamesIsAWayOfWorking", TimedActsTests::everyAnimationOfTheGamesIsAWayOfWorking);
        Checks.run("handsAtWorkAreInThePictureAndHandsWithNothingToDoAreNot", TimedActsTests::handsAtWorkAreInThePictureAndHandsWithNothingToDoAreNot);
        Checks.run("handsComeUpFromUnderThePicture", TimedActsTests::handsComeUpFromUnderThePicture);
        Checks.run("whatTheGamesHandHoldsIsCarriedToTheHandBeforeTheEyeAsAWeaponIs", TimedActsTests::whatTheGamesHandHoldsIsCarriedToTheHandBeforeTheEyeAsAWeaponIs);
        Checks.run("armsReachEveryHandAtEveryMomentWithWristsAsWristsGo", () -> armsReachEveryHandAtEveryMomentWithWristsAsWristsGo(model));
        Checks.run("theShaderBendsTheHeldMapAsTheHandsHoldIt", TimedActsTests::theShaderBendsTheHeldMapAsTheHandsHoldIt);
    }

    /** The hands are placed on the sheet by TimedActs.bend and the sheet is drawn bent by the shader's copy of it: they must agree everywhere. */
    private static void theShaderBendsTheHeldMapAsTheHandsHoldIt() {
        GlslFunctions shader = new GlslFunctions(TimedActs.SHEET_BENT);
        float worst = 0f;
        for (float u = -0.5f; u <= 0.5f; u += 1f / 48f) {
            for (float v = -0.5f; v <= 0.5f; v += 1f / 32f) {
                worst = Math.max(worst, Math.abs(TimedActs.bend(u, v) - shader.call("sheetStill", u, v)));
            }
        }
        check(worst < 1e-6f, "the shader's bend of the held map is TimedActs.bend's, at most " + worst + " m apart");
        float bowed = TimedActs.bend(0f, 0.2f);
        check(bowed < -0.005f, "and the sheet bows away from the eye between the hands: " + bowed);
    }

    /** The names the game gives the animations of its timed actions, as src/test/data/timed-action-anims.txt lists them. */
    static List<String> animations() throws IOException {
        List<String> names = Files.readAllLines(Path.of("src", "test", "data", "timed-action-anims.txt"), StandardCharsets.UTF_8).stream()
                .map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
        check(names.size() > 100, "the list of the game's timed action animations is read: " + names.size());
        return names;
    }

    private static void everyAnimationOfTheGamesIsAWayOfWorking() throws IOException {
        for (String anim : animations()) {
            check(TimedActs.kind(anim) != TimedActs.NONE, "the hands do something at " + anim);
        }
        for (String anim : new String[] {"Reload", "EquipItem", "UnequipItem", "DropWhileMoving", ""}) {
            check(TimedActs.kind(anim) == TimedActs.NONE, "the hands are left to the weapon, or to the game, at '" + anim + "'");
        }
        String[][] named = {{"Eat", "MOUTH"}, {"TakePills", "MOUTH"}, {"Drink", "DRINK"}, {"Read", "READ"}, {"Bandage", "WRAP"}, {"Craft", "WORK"},
            {"Making", "WORK"}, {"SewingCloth", "WORK"}, {"OpenTinCan", "CRANK"}, {"OpenTinCanWithKnife", "CRANK"}, {"Build", "HAMMER"},
            {"BuildLow", "HAMMER"}, {"SmithingHammer", "HAMMER"}, {"SawLog", "SAW"}, {"Loot", "RUMMAGE"}, {"Forage", "RUMMAGE"}, {"ScrubWall", "SCRUB"},
            {"Pour", "POUR"}, {"Chop_tree", "SWING"}, {"DigShovel", "SWING"}, {"BlowTorch", "WELD"}, {"WashFace", "FACE"},
            {"Rake", "SCRUB"}, {"Untie_Low", "WORK"}, {"AnimalLure", "RUMMAGE"}, {"RemoveCurtain", "WORK"}, {"Shave", "FACE"}, {"InsertBullets", "WORK"}};
        String[] kinds = {"NONE", "MOUTH", "DRINK", "READ", "WORK", "CRANK", "WRAP", "HAMMER", "SAW", "RUMMAGE", "SCRUB", "POUR", "SWING", "WELD", "FACE"};
        for (String[] pair : named) {
            check(kinds[TimedActs.kind(pair[0])].equals(pair[1]), pair[0] + " is " + pair[1] + ", not " + kinds[TimedActs.kind(pair[0])]);
        }
        check(TimedActs.low("BuildLow", null) && TimedActs.low("ScrubFloor_Mop", null) && TimedActs.low("Forage", null) && TimedActs.low("BlowTorchFloor", null)
                && TimedActs.low("Tying_Low", null) && TimedActs.low("Rake", null)
                && !TimedActs.low("Build", null) && !TimedActs.low("Eat", null) && !TimedActs.low("BlowTorch", null) && !TimedActs.low("BlowGlass", null),
                "work on the floor is low, and work before the body is not");
        check(TimedActs.low("Loot", "Low") && !TimedActs.low("Loot", "Mid") && !TimedActs.low("Loot", "High") && !TimedActs.low("Loot", null) && !TimedActs.low("Loot", ""),
                "a search reaches down where the game says what is searched is low, and before the body where it is higher or unsaid");
        check(!TimedActs.low("Eat", "Low") && !TimedActs.low("Build", "Low"),
                "how high a search is, as a variable the game may leave set, lowers nothing but a search");
    }

    /** The tangent of half the height of the picture, and its width against its height, as the arm tests take them. */
    private static final float PICTURE = 0.6371f, WIDE = 16f / 9f;
    /** The middle of a hand is this far inside the picture's edge at least, as a share of the half height: a hand on the edge is cut in half. */
    private static final float INSIDE = 0.9f;

    private static boolean inThePicture(float[] at) {
        float depth = -at[2];
        return depth > 0f && Math.abs(at[1]) < depth * PICTURE * INSIDE && Math.abs(at[0]) < depth * PICTURE * WIDE * INSIDE;
    }

    private static boolean away(TimedActs.Hand hand, int side) {
        return ArmPose.apart(hand.at, TimedActs.AWAY[side]) < 1e-6f;
    }

    /** Every way of working. */
    private static final int[] KINDS = {TimedActs.MOUTH, TimedActs.DRINK, TimedActs.READ, TimedActs.WORK, TimedActs.CRANK, TimedActs.WRAP, TimedActs.HAMMER,
        TimedActs.SAW, TimedActs.RUMMAGE, TimedActs.SCRUB, TimedActs.POUR, TimedActs.SWING, TimedActs.WELD, TimedActs.FACE};

    private static void handsAtWorkAreInThePictureAndHandsWithNothingToDoAreNot() {
        TimedActs.Hand right = new TimedActs.Hand(), left = new TimedActs.Hand();
        for (int kind : KINDS) {
            for (int items = 0; items < 4; items++) {
                for (int low = 0; low < 2; low++) {
                    int[] seen = new int[2], working = new int[2];
                    int samples = 0;
                    for (float t = 0f; t < 6f; t += 0.02f, samples++) {
                        TimedActs.hands(kind, low == 1, t, (items & 1) != 0, (items & 2) != 0, 1f, right, left);
                        TimedActs.Hand[] both = {right, left};
                        for (int side = 0; side < 2; side++) {
                            TimedActs.Hand hand = both[side];
                            String what = String.format(Locale.ROOT, "kind %d items %d low %d at %.2f s, %s hand at (%.2f, %.2f, %.2f)", kind, items, low, t,
                                    side == 0 ? "right" : "left", hand.at[0], hand.at[1], hand.at[2]);
                            check(Math.abs(ArmPose.dot(hand.fingers, hand.fingers) - 1f) < 1e-4f && Math.abs(ArmPose.dot(hand.out, hand.out) - 1f) < 1e-4f
                                    && Math.abs(ArmPose.dot(hand.fingers, hand.out)) < 1e-4f, "a hand's fingers and palm are square to each other: " + what);
                            if (away(hand, side)) {
                                check(!inThePicture(hand.at), "a hand with nothing to do is out of the picture: " + what);
                                continue;
                            }
                            working[side]++;
                            check(-hand.at[2] >= 0.17f, "a hand at work is no nearer the eye than what it holds can be drawn: " + what);
                            if (inThePicture(hand.at)) {
                                seen[side]++;
                            }
                        }
                    }
                    // A drink the game puts in the left hand is drunk from the left.
                    boolean leftDrinks = kind == TimedActs.DRINK && (items & 2) != 0;
                    check(leftDrinks ? working[1] > 0 && working[0] == 0 : working[0] > 0,
                            "the right hand is at work in every way of working, the left alone for a drink in the left: kind " + kind + " items " + items);
                    for (int side = 0; side < 2; side++) {
                        check(working[side] == 0 || seen[side] >= working[side] * 0.6f,
                                String.format(Locale.ROOT, "kind %d items %d low %d: the %s hand at work is in the picture most of the time: %d of %d", kind, items, low,
                                        side == 0 ? "right" : "left", seen[side], working[side]));
                    }
                }
            }
        }
    }

    private static void handsComeUpFromUnderThePicture() {
        TimedActs.Hand right = new TimedActs.Hand(), left = new TimedActs.Hand();
        for (int kind : KINDS) {
            TimedActs.hands(kind, false, 1.3f, true, true, 0f, right, left);
            check(away(right, 0) && away(left, 1), "with the hands not yet up both are below the picture: kind " + kind);
            TimedActs.hands(kind, false, 1.3f, true, true, 0.5f, right, left);
            check(right.at[1] > TimedActs.AWAY[0][1] || away(right, 0), "half way up a hand is on its way up: kind " + kind);
        }
    }

    private static void whatTheGamesHandHoldsIsCarriedToTheHandBeforeTheEyeAsAWeaponIs() {
        // A hand of the game's along +y from its wrist, the line across its wrist along +x, so the line palmFacing measures by is -z.
        float[] hand = new float[CharacterDraw.HAND_SAYS];
        float[] wrist = {0.3f, 1.2f, -0.1f}, rigid = {0.3f, 0.9f, -0.1f}, middle = {0.3f, 1.25f, -0.1f}, across = {0.35f, 1.2f, -0.1f};
        System.arraycopy(wrist, 0, hand, 0, 3);
        System.arraycopy(rigid, 0, hand, 3, 3);
        System.arraycopy(middle, 0, hand, 6, 3);
        System.arraycopy(across, 0, hand, 9, 3);
        for (float held : new float[] {-0.04f, 0.04f}) {
            float[] item = {0.31f, 1.27f, -0.1f + held};
            float facing = ViewModelMath.palmFacing(hand, item);
            check(Math.abs(Math.abs(facing) - 0.04f) < 1e-5f, "what is held is measured from the middle of the hand along the line out of it: " + facing);
            float side = facing < 0f ? -1f : 1f;
            float[] fingers = {0f, 0.6f, -0.8f}, palm = {-1f, 0f, 0f}, at = {0.1f, -0.1f, -0.4f}, out = new float[16];
            float scale = Characters.CHARACTER_SCALE, back = 0.02f;
            ViewModelMath.carry(hand, side, fingers, palm, at, scale, back, out);
            float[] seenMiddle = new float[3], seenItem = new float[3], seenWrist = new float[3];
            ViewModelMath.place(out, middle[0], middle[1], middle[2], seenMiddle);
            ViewModelMath.place(out, item[0], item[1], item[2], seenItem);
            ViewModelMath.place(out, wrist[0], wrist[1], wrist[2], seenWrist);
            check(ArmPose.apart(seenMiddle, new float[] {at[0] - palm[0] * back, at[1] - palm[1] * back, at[2] - palm[2] * back}) < 1e-5f,
                    "the middle of the game's hand goes behind the middle of what the hand before the eye holds");
            float[] item3 = {seenItem[0] - seenMiddle[0], seenItem[1] - seenMiddle[1], seenItem[2] - seenMiddle[2]};
            check(Math.abs(ArmPose.dot(item3, palm) - 0.04f * scale) < 1e-4f, "what the game's hand holds is in front of the palm before the eye, as far as it was, at the weapon's scale: " + ArmPose.dot(item3, palm));
            float[] toWrist = {seenWrist[0] - seenMiddle[0], seenWrist[1] - seenMiddle[1], seenWrist[2] - seenMiddle[2]};
            check(Math.abs(ArmPose.dot(toWrist, fingers) + 0.05f * scale) < 1e-4f, "the game's wrist is behind the middle of the hand, back along the fingers");
            float[] weapon = new float[16];
            ViewModelMath.hold(new float[] {0f, 0f, 0f}, new float[] {0f, 1f, 0f}, new float[] {0f, 0f, 1f}, null, scale, 0f, 0f, 0.3f, weapon);
            check(Math.signum(determinant(out)) == Math.signum(determinant(weapon)) && Math.abs(Math.abs(determinant(out)) - scale * scale * scale) < 1e-3f,
                    "the game's axes are carried in a mirror and at the scale a weapon is: " + determinant(out) + " against " + determinant(weapon));
        }
    }

    private static float determinant(float[] m) {
        return m[0] * (m[5] * m[10] - m[9] * m[6]) - m[4] * (m[1] * m[10] - m[9] * m[2]) + m[8] * (m[1] * m[6] - m[5] * m[2]);
    }

    private static final float SCALE = 0.74f;
    private static final float[] SAME = {1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};

    private static void armsReachEveryHandAtEveryMomentWithWristsAsWristsGo(ArmModel model) {
        TimedActs.Hand right = new TimedActs.Hand(), left = new TimedActs.Hand();
        TimedActs.Hand[] seen = {new TimedActs.Hand(), new TimedActs.Hand()};
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        float worstBent = 0f, worstTwist = 0f;
        String worstBentAt = "", worstTwistAt = "";
        for (int kind : KINDS) {
            for (int items = 0; items < 4; items++) {
                for (int low = 0; low < 2; low++) {
                    for (float t = 0f; t < 6f; t += 0.05f) {
                        TimedActs.hands(kind, low == 1, t, (items & 1) != 0, (items & 2) != 0, 1f, right, left);
                        TimedActs.Hand[] both = {right, left};
                        for (int side = 0; side < 2; side++) {
                            for (int axis = 0; axis < 3; axis++) {
                                seen[side].at[axis] = both[side].at[axis] * SCALE;
                            }
                            System.arraycopy(both[side].fingers, 0, seen[side].fingers, 0, 3);
                            System.arraycopy(both[side].out, 0, seen[side].out, 0, 3);
                            seen[side].pose = both[side].pose;
                        }
                        String at = String.format(Locale.ROOT, "kind %d items %d low %d at %.2f s", kind, items, low, t);
                        check(ArmHolds.placeTask(model, SCALE, seen, SAME, skins, said), "both arms are placed: " + at);
                        for (int side = 0; side < 2; side++) {
                            if (away(both[side], side)) {
                                continue;
                            }
                            float bent = (float) Math.toDegrees(said[side][ArmPose.BENT]), twist = (float) Math.toDegrees(Math.abs(said[side][ArmPose.ASKED]));
                            String which = at + (side == 0 ? " right" : " left");
                            if (bent > worstBent) {
                                worstBent = bent;
                                worstBentAt = which;
                            }
                            if (twist > worstTwist) {
                                worstTwist = twist;
                                worstTwistAt = which;
                            }
                            check(bent <= 45f, "a wrist is bent no further than a wrist goes: " + which + ", " + bent);
                            check(twist <= Math.toDegrees(ArmPose.TWIST_MOST), "a hand asks its forearm to turn no further than a forearm turns, and the upper arm is not rolled for it: " + which + ", " + twist);
                            check(said[side][ArmPose.ELBOW + 1] < said[side][ArmPose.WRIST + 1], "the elbow is under the wrist: the forearm comes up into the picture: " + which);
                        }
                    }
                }
            }
        }
        System.out.println(String.format(Locale.ROOT, "  timed actions: wrists bent %.0f at most (%s), forearms turned %.0f at most (%s)", worstBent, worstBentAt, worstTwist, worstTwistAt));
    }
}
