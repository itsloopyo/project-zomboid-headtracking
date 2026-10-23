package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.GZIPInputStream;

/** Tests of where the hands go on a firearm, read from the firearm's own mesh. */
final class GripTests {
    private GripTests() {}

    static void run() throws IOException {
        ArmModel model;
        try (InputStream packed = GripTests.class.getResourceAsStream("/models/arms.mesh.gz")) {
            model = new ArmModel(packed);
        }
        Checks.run("pistolsGripIsReadFromItsMesh", GripTests::pistolsGripIsReadFromItsMesh);
        Checks.run("longGunsWristFeedAndForeEndAreReadFromItsMesh", GripTests::longGunsWristFeedAndForeEndAreReadFromItsMesh);
        Checks.run("meshWithNoTriggerGuardHasNoShape", GripTests::meshWithNoTriggerGuardHasNoShape);
        Checks.run("modsOwnModelsHaveAGripAndAForeEnd", GripTests::modsOwnModelsHaveAGripAndAForeEnd);
        Checks.run("aModelFileThatCannotBeIsNotRead", GripTests::aModelFileThatCannotBeIsNotRead);
        Checks.run("oddMeshesHaveNoShapeAndSayWhy", GripTests::oddMeshesHaveNoShapeAndSayWhy);
        Checks.run("rightHandClosesRoundAPistolsGripAlongItsRake", () -> rightHandClosesRoundAPistolsGripAlongItsRake(model));
        Checks.run("leftHandWrapsTheRightOnAPistolHeldInBoth", () -> leftHandWrapsTheRightOnAPistolHeldInBoth(model));
        Checks.run("leftHandIsUnderTheForeEndsUnderside", () -> leftHandIsUnderTheForeEndsUnderside(model));
        Checks.run("armsOnAPistolComeUpFromUnderThePictureUnturned", () -> armsOnAPistolComeUpFromUnderThePictureUnturned(model));
        Checks.run("armsOnALongGunTurnNoFurtherThanAForearmTurns", () -> armsOnALongGunTurnNoFurtherThanAForearmTurns(model));
        Checks.run("armsWorkingTheActionTurnNoFurtherThanAForearmTurns", () -> armsWorkingTheActionTurnNoFurtherThanAForearmTurns(model));
        Checks.run("rightPalmOnALongGunFacesAsItsHoldSays", () -> rightPalmOnALongGunFacesAsItsHoldSays(model));
        Checks.run("aRifleRackedByAHandleTurnsNoKnobAfter", () -> aRifleRackedByAHandleTurnsNoKnobAfter(model));
        Checks.run("heldMeshIsFilledAgainEachFrame", GripTests::heldMeshIsFilledAgainEachFrame);
        Checks.run("meshAxesComeFromTheMuzzleAttachment", GripTests::meshAxesComeFromTheMuzzleAttachment);
    }

    /** Where the pistol's front edge is at a height. */
    private static float frontAt(float u) {
        float t = (u - TestGuns.GRIP_TOP) / (TestGuns.GRIP_FOOT - TestGuns.GRIP_TOP);
        return TestGuns.GRIP_FRONT_TOP + (TestGuns.GRIP_FRONT_FOOT - TestGuns.GRIP_FRONT_TOP) * t;
    }

    private static void pistolsGripIsReadFromItsMesh() {
        WeaponShape shape = TestGuns.shape(TestGuns.pistol());
        check(near(shape.guardFront, 0.074f, 0.003f) && shape.guardBack < 0.04f && near(shape.guardFoot, -0.024f, 0.003f), "the guard is the hole under the frame: "
                + shape.guardBack + " to " + shape.guardFront + ", foot " + shape.guardFoot);
        float a = shape.grip[1], u = shape.grip[2];
        check(u < shape.guardFoot && u > TestGuns.GRIP_FOOT, "the hand's middle is on the grip, under the guard and over the grip's foot: " + u);
        check(a < frontAt(u) && a > frontAt(u) - TestGuns.GRIP_THICK, "and between the grip's front and its back: " + a + " at " + u);
        float[] rake = new float[3];
        ArmPose.unit(0f, TestGuns.GRIP_FRONT_TOP - TestGuns.GRIP_FRONT_FOOT, TestGuns.GRIP_TOP - TestGuns.GRIP_FOOT, rake);
        check(ArmPose.dot(rake, shape.gripLine) > Math.cos(Math.toRadians(3)), "the grip's line is its front edge's, leaning forward at the top as a pistol's grip does: "
                + shape.gripLine[1] + ", " + shape.gripLine[2]);
        check(near(shape.gripThick, TestGuns.GRIP_THICK * rake[2], 0.004f), "it is as thick as the grip is: " + shape.gripThick);
        check(near(shape.gripFoot[2], TestGuns.GRIP_FOOT, 0.004f), "its foot, where a magazine goes in, is the grip's: " + shape.gripFoot[2]);
        check(near(shape.slide[1], -0.02f + WeaponShape.SLIDE_HELD, 0.003f) && shape.slide[2] > TestGuns.PISTOL_BORE - 0.02f, "the slide is taken hold of near its rear and its top");
        check(!shape.magazine, "a pistol has nothing hanging ahead of its guard");
    }

    private static void longGunsWristFeedAndForeEndAreReadFromItsMesh() {
        WeaponShape shape = TestGuns.shape(TestGuns.longGun(true));
        check(near(shape.guardBack, TestGuns.LONG_GUARD_BACK, 0.004f) && near(shape.guardFront, TestGuns.LONG_GUARD_FRONT, 0.004f), "the guard: " + shape.guardBack + " to " + shape.guardFront);
        float[] rake = new float[3];
        ArmPose.unit(0f, 0.04f, 0.05f, rake);
        check(ArmPose.dot(rake, shape.gripLine) > Math.cos(Math.toRadians(4)), "the hand lies along the wrist's raked front: " + shape.gripLine[1] + ", " + shape.gripLine[2]);
        check(shape.grip[2] < -0.035f && shape.grip[1] < TestGuns.LONG_GUARD_BACK, "on the wrist behind and under the guard: " + shape.grip[1] + ", " + shape.grip[2]);
        check(shape.magazine && near(shape.magazineBack, TestGuns.MAGAZINE_BACK, 0.004f) && near(shape.magazineFront, TestGuns.MAGAZINE_FRONT, 0.004f)
                && near(shape.magazineFoot, TestGuns.MAGAZINE_FOOT, 0.004f), "the magazine hangs ahead of the guard: " + shape.magazineBack + " to " + shape.magazineFront);
        check(shape.levelFrom >= TestGuns.FORE_FROM - 0.004f && shape.levelTo <= TestGuns.FORE_TO + 0.004f && shape.levelTo - shape.levelFrom > 0.2f,
                "the fore-end is the low level run under the barrel, not the bare barrel ahead of it: " + shape.levelFrom + " to " + shape.levelTo);
        check(near(shape.under(0.35f), TestGuns.FORE_FOOT, 0.004f) && near(shape.under(0.7f), 0.02f, 0.004f), "and the underside is the fore-end's under it and the barrel's ahead of it");
        WeaponShape without = TestGuns.shape(TestGuns.longGun(false));
        check(!without.magazine, "a long gun with nothing under it ahead of the guard has no magazine");
    }

    private static void meshWithNoTriggerGuardHasNoShape() {
        TestGuns.Builder b = new TestGuns.Builder();
        b.box(-0.02f, 0.17f, -0.004f, 0.036f, 0.012f);
        b.box(-0.01f, 0.03f, -0.08f, -0.004f, 0.012f);
        TestGuns.Mesh mesh = b.mesh(new float[] {0f, 0.17f, 0.026f});
        StringBuilder why = new StringBuilder();
        check(WeaponShape.read(mesh.places(), mesh.corners(), new float[] {0f, 1f, 0f}, new float[] {0f, 0f, 1f}, mesh.muzzle(), 1f, why) == null && why.indexOf("trigger guard") >= 0,
                "a mesh with no hole a finger goes through has no grip to be held by, and says so: " + why);
    }

    /** A mesh another mod supplies may be anything: one with a place that is no number, or one drawn far beyond its muzzle, has no shape and says why, and nothing throws. */
    private static void oddMeshesHaveNoShapeAndSayWhy() {
        TestGuns.Mesh pistol = TestGuns.pistol();
        float[] broken = pistol.places().clone();
        broken[4] = Float.NaN;
        StringBuilder why = new StringBuilder();
        check(WeaponShape.read(broken, pistol.corners(), new float[] {0f, 1f, 0f}, new float[] {0f, 0f, 1f}, pistol.muzzle(), 1f, why) == null && why.indexOf("not a number") >= 0,
                "a mesh with a place that is not a number has no shape: " + why);
        float[] far = pistol.places().clone();
        far[4] = 1000f;
        why.setLength(0);
        check(WeaponShape.read(far, pistol.corners(), new float[] {0f, 1f, 0f}, new float[] {0f, 0f, 1f}, pistol.muzzle(), 1f, why) == null && why.indexOf("no firearm") >= 0,
                "nor one with a vertex a thousand times its length off: " + why);
        // Over three times its muzzle's way from the grip ahead and up: two million cells, read on the render thread as it is first drawn.
        float[] big = pistol.places().clone();
        big[3] = 0f;
        big[4] = 0.55f;
        big[5] = 0.55f;
        why.setLength(0);
        check(WeaponShape.read(big, pistol.corners(), new float[] {0f, 1f, 0f}, new float[] {0f, 0f, 1f}, pistol.muzzle(), 1f, why) == null && why.indexOf("no firearm") >= 0,
                "nor one three times as long and as tall as its muzzle is from its grip: " + why);
    }

    /** The mod's own guns are held as the game's firearms are: their meshes say where their grips are. Every one in OwnModel.ALL. */
    private static void modsOwnModelsHaveAGripAndAForeEnd() throws IOException {
        for (OwnModel model : OwnModel.ALL) {
            ByteBuffer read;
            try (InputStream packed = new GZIPInputStream(GripTests.class.getResourceAsStream(model.mesh))) {
                read = ByteBuffer.wrap(packed.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            }
            OwnModel.Layout layout = OwnModel.layout(read, model.mesh);
            String name = model.what;
            StringBuilder why = new StringBuilder();
            WeaponShape shape = WeaponShape.read(layout.places(read), layout.corners(read), new float[] {0f, 1f, 0f}, model.top(), layout.muzzle(), 1f,
                    why);
            check(shape != null, name + " has a grip: " + why);
            if (shape == null) {
                continue;
            }
            check(ArmPose.apart(shape.grip, new float[3]) < 0.08f && ArmPose.dot(shape.grip, shape.up) < shape.bore, name + "'s grip is under its bore and within a hand of its origin: "
                    + shape.grip[1] + ", " + shape.grip[2]);
            check(model.handgun == layout.muzzle()[2] < 0f, name + " is made with its top towards -z if and only if it is a handgun");
            if (model.handgun) {
                // A revolver's grip leans back from its bore, and its guard is ahead of the hand, under the cylinder.
                float lean = (float) Math.toDegrees(Math.atan2(ArmPose.dot(shape.gripLine, shape.along), ArmPose.dot(shape.gripLine, shape.up)));
                check(lean > 10f && lean < 35f, name + "'s grip leans back 10 to 35 degrees from upright: " + lean);
                check(shape.guardFront > ArmPose.dot(shape.grip, shape.along) && shape.guardFront - shape.guardBack > 0.015f,
                        "and its guard is ahead of the grip and wide enough for a finger: " + shape.guardBack + " to " + shape.guardFront);
            } else {
                check(shape.foreTo > shape.foreFrom && shape.levelTo > shape.levelFrom, "and it has an underside ahead of its guard");
            }
        }
    }

    /** A model's file that is cut short, or says it has what cannot be, is refused with why, and nothing is made of it. */
    private static void aModelFileThatCannotBeIsNotRead() {
        // magic, version 5, twelve floats, 0 vertices, then what is under test.
        check(refused(head(5), "cut short"), "a version 5 head that ends before its part count");
        check(refused(head(4), "cut short"), "a version 4 head too short to hold its five counts");
        check(refused(head(5, -1), "has -1 parts"), "a negative part count");
        check(refused(head(5, 2_000_000_000), "parts"), "a part count no model has");
        check(refused(head(5, 0), "no parts"), "no parts");
        check(refused(head(5, 2, -3, 6, 0), "not whole triangles"), "a part with a negative count");
        check(refused(head(5, 1, 6, -5), "parts move"), "a negative count of moves");
        check(refused(head(5, 1, 3, 0), "bytes after its head"), "a part with indices and no body");
        check(refused(head(4, 0, 0, 0, 0, 0), null), "and a head with nothing wrong is read");
    }

    /** A model's head, all of it nought but the magic and the version, then the ints given. */
    private static ByteBuffer head(int version, int... then) {
        ByteBuffer out = ByteBuffer.allocate(8 + 12 * Float.BYTES + Integer.BYTES + then.length * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt('C' | 'U' << 8 | 'W' << 16 | 'M' << 24).putInt(version);
        for (int at = 0; at < 12; at++) {
            out.putFloat(0f);
        }
        out.putInt(0);
        for (int each : then) {
            out.putInt(each);
        }
        return out.flip();
    }

    /** Whether reading the head throws an IOException that says said, or with said null, reads it without one. */
    private static boolean refused(ByteBuffer read, String said) {
        try {
            OwnModel.layout(read, "a test model");
            return said == null;
        } catch (IOException e) {
            return said != null && e.getMessage().contains(said);
        }
    }

    private static final float SCALE = 0.74f;
    private static final float[] SAME = {1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};

    private static ArmHolds.Weapon pistolHeld(boolean aimed) {
        WeaponShape shape = TestGuns.shape(TestGuns.pistol());
        float[] grip = aimed ? new float[] {0f, -0.1f, -0.36f} : new float[] {0.12f, -0.16f, -0.32f}, pointing = {0f, 0f, -1f}, over = {0f, 1f, 0f};
        ArmHolds.Placed placed = TestGuns.placed(shape, grip, pointing, over, SCALE, 0f, Float.NaN);
        return new ArmHolds.Weapon(true, true, placed.at(shape.grip), pointing, over, placed);
    }

    private static void rightHandClosesRoundAPistolsGripAlongItsRake(ArmModel model) {
        for (boolean aimed : new boolean[] {false, true}) {
            ArmHolds.Weapon pistol = pistolHeld(aimed);
            float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
            ArmHolds.Doing holding = new ArmHolds.Doing(aimed ? 1f : 0f, false, ArmActs.HANDGUN, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f);
            check(ArmHolds.place(model, SCALE, pistol, holding, SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled()) == 1, "a pistol in one hand has one arm");
            // The pose's own hold: the grip's front edge on its right side, where the hand wraps it from outside.
            float[] own = model.holds[model.pose("pistol")];
            float[] outside = placed(skins[0], model.wrist, own), line = pistol.placed().way(pistol.placed().shape().gripLine), front = new float[3];
            float along = ArmPose.dot(pistol.pointing(), line);
            ArmPose.unit(pistol.pointing()[0] - line[0] * along, pistol.pointing()[1] - line[1] * along, pistol.pointing()[2] - line[2] * along, front);
            WeaponShape shape = pistol.placed().shape();
            float unit = pistol.placed().unit();
            float[] off = {outside[0] - pistol.grip()[0], outside[1] - pistol.grip()[1], outside[2] - pistol.grip()[2]};
            check(near(ArmPose.dot(off, front), shape.gripThick / 2f * unit, 1e-4f) && near(off[0], shape.gripWide * unit, 1e-4f),
                    "the right hand holds the grip from outside, at its front edge on its right side, under the guard and not the frame or the slide");
            float[] hold = model.holds[model.pose("grip")];
            float[] held = placed(skins[0], model.wrist, hold);
            float[] thumb = placed(skins[0], model.wrist, new float[] {hold[0] + model.across[0], hold[1] + model.across[1], hold[2] + model.across[2]});
            float[] across = {(thumb[0] - held[0]) / SCALE, (thumb[1] - held[1]) / SCALE, (thumb[2] - held[2]) / SCALE};
            // The grip lies aslant across the palm: the knuckles lean back from its raked line, which is the pistol's front edge, by 25 to 35 degrees.
            float[] edge = {0f, TestGuns.GRIP_TOP - TestGuns.GRIP_FOOT, -(TestGuns.GRIP_FRONT_TOP - TestGuns.GRIP_FRONT_FOOT)};
            ArmPose.unit(edge[0], edge[1], edge[2], edge);
            float[] knuckles = new float[3];
            ArmPose.unit(across[0], across[1], across[2], knuckles);
            float lean = (float) Math.toDegrees(Math.acos(Math.min(1f, ArmPose.dot(knuckles, edge))));
            check(lean > 25f && lean < 35f && ArmPose.dot(knuckles, pistol.pointing()) < ArmPose.dot(edge, pistol.pointing()),
                    "its knuckles run from the little finger to the first leaning back across the grip's raked line: " + lean + " degrees from it");
            float[] palm = placed(skins[0], model.wrist, new float[] {hold[0] + model.out[0], hold[1] + model.out[1], hold[2] + model.out[2]});
            check(palm[0] - held[0] < -0.5f * SCALE, "and its palm is on the grip's right side, facing it");
        }
    }

    private static void leftHandWrapsTheRightOnAPistolHeldInBoth(ArmModel model) throws java.io.IOException {
        ArmHolds.Weapon pistol = pistolHeld(true);
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        ArmHolds.Doing both = new ArmHolds.Doing(1f, true, ArmActs.HANDGUN, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f);
        check(ArmHolds.place(model, SCALE, pistol, both, SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled()) == 2, "a pistol held in both hands has both arms");
        float[] hold = model.holds[model.pose("grip")];
        float[] right = placed(skins[0], model.wrist, hold), left = placed(skins[1], model.wrist, hold);
        check(left[0] < right[0] - 0.01f * SCALE && ArmPose.apart(left, right) < 0.08f * SCALE, "the left hand is round the right, on the grip's left side: "
                + (left[0] - right[0]) / SCALE + " m to the side, " + ArmPose.apart(left, right) / SCALE + " m off");
        check(left[2] < right[2], "a little ahead of it, its fingers over the right hand's under the guard");
        float[] palm = placed(skins[1], model.wrist, new float[] {hold[0] + model.out[0], hold[1] + model.out[1], hold[2] + model.out[2]});
        check(palm[0] - left[0] > 0.5f * SCALE, "its palm faces the grip from the left");
        check(said[1][ArmPose.WRIST] < said[0][ArmPose.WRIST], "and its wrist is to the left of the right one");
        // Its thumb lies forward along the frame's left side: from the wrist it runs ahead more than across, and does not cross the back of the grip.
        int thumb = model.bone("finger1-3");
        float[] tip = placed(skins[1], thumb, new float[] {model.rests[thumb * 16 + 12], model.rests[thumb * 16 + 13], model.rests[thumb * 16 + 14]});
        float ahead = said[1][ArmPose.WRIST + 2] - tip[2], across = tip[0] - said[1][ArmPose.WRIST];
        check(ahead > 2f * across, "the left thumb runs ahead from the wrist, not across the grip: " + ahead / SCALE + " m ahead, " + across / SCALE + " m across");
        // The left hand is round the grip only when it is free.
        ArmHolds.Doing one = new ArmHolds.Doing(1f, false, ArmActs.HANDGUN, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f);
        check(ArmHolds.place(model, SCALE, pistol, one, SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled()) == 1, "with the left hand not free the pistol is held in the right alone");
    }

    /**
     * A pistol held part way between the hip and the sights, turned as the view holds it: canted
     * over at the hip, swung to the aim point, lowered.
     *
     * @param turns about the view's up, its right and the barrel, in radians
     */
    private static ArmHolds.Weapon pistolTurned(float aim, float[] turns) {
        WeaponShape shape = TestGuns.shape(TestGuns.pistol());
        float[] grip = {0.12f - 0.12f * aim, -0.16f + 0.06f * aim, -0.32f - 0.04f * aim};
        float[] pointing = {0f, 0f, -1f}, over = {0f, 1f, 0f}, turn = new float[9];
        float[][] about = {{0f, 1f, 0f}, {1f, 0f, 0f}, null};
        for (int k = 0; k < 3; k++) {
            ArmPose.about(about[k] != null ? about[k] : pointing.clone(), turns[k], turn);
            pointing = turnedBy(turn, pointing);
            over = turnedBy(turn, over);
        }
        ArmHolds.Placed placed = TestGuns.placed(shape, grip, pointing, over, SCALE, 0f, Float.NaN);
        return new ArmHolds.Weapon(true, true, placed.at(shape.grip), pointing, over, placed);
    }

    private static float[] turnedBy(float[] turn, float[] line) {
        float[] out = new float[3];
        ArmPose.turned(turn, line, out);
        return out;
    }

    /** How the view turns a pistol: as it is, canted over at the hip, swung left or right to the aim point, lowered, and raised. */
    private static final float[][] PISTOL_TURNS = {{0f, 0f, 0f}, {0f, 0f, (float) Math.toRadians(8)}, {(float) Math.toRadians(12), 0f, 0f}, {(float) Math.toRadians(-12), 0f, 0f},
        {0f, (float) Math.toRadians(-15), 0f}, {0f, (float) Math.toRadians(10), 0f}, {(float) Math.toRadians(-10), (float) Math.toRadians(-10), (float) Math.toRadians(8)}};

    /**
     * Both arms on a pistol, at the hip, half way up and with the sights up, held as the view
     * turns it: each forearm turned along its length no further than a forearm turns, so the
     * upper arm is not rolled to make up the rest, and each elbow under its wrist, so the forearm
     * comes up into the picture from below and does not lie across it from the side.
     */
    private static void armsOnAPistolComeUpFromUnderThePictureUnturned(ArmModel model) {
        for (float aim : new float[] {0f, 0.5f, 1f}) {
            for (int way = 0; way < PISTOL_TURNS.length; way++) {
                ArmHolds.Weapon pistol = pistolTurned(aim, PISTOL_TURNS[way]);
                float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
                ArmHolds.Doing both = new ArmHolds.Doing(aim, true, ArmActs.HANDGUN, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f);
                check(ArmHolds.place(model, SCALE, pistol, both, SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled()) == 2, "a pistol held in both hands has both arms");
                for (int side = 0; side < 2; side++) {
                    String arm = (side == 0 ? "the right" : "the left") + " arm on a pistol raised " + aim + ", turned the way numbered " + way;
                    float asked = (float) Math.toDegrees(said[side][ArmPose.ASKED]);
                    check(Math.abs(asked) <= Math.toDegrees(ArmPose.TWIST_MOST), arm + " asks its forearm to turn no further than a forearm turns: " + asked + " degrees");
                    // A pistol pointed down brings the wrists down towards the elbows, which stay under them.
                    float drop = (said[side][ArmPose.WRIST + 1] - said[side][ArmPose.ELBOW + 1]) / SCALE;
                    check(drop > (way == 0 ? 0.08f : 0.05f), arm + " has its elbow well under its wrist: " + drop + " m");
                }
            }
        }
    }

    /**
     * A firearm held as the view holds it: its grip `at`, in metres from the eye, turned up by
     * `pitch`, to the left by `yaw` and its top over to the left by `roll`, as ViewModelMath.turn
     * turns it, and with its fore-end slid back by `slid` of its mesh.
     */
    private static ArmHolds.Weapon heldTurned(WeaponShape shape, boolean handgun, float[] at, float pitch, float yaw, float roll) {
        float[] turn = {1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};
        ViewModelMath.turn(turn, pitch, yaw, roll, 0f, 0f, 0f, 0f, 0f, 0f);
        float[] pointing = {-turn[8], -turn[9], -turn[10]}, over = {turn[4], turn[5], turn[6]};
        float[] grip = {at[0] * SCALE, at[1] * SCALE, at[2] * SCALE};
        ArmHolds.Placed placed = TestGuns.placed(shape, grip, pointing, over, SCALE, 0f, Float.NaN);
        return new ArmHolds.Weapon(true, handgun, placed.at(shape.grip), pointing, over, placed);
    }

    /**
     * A rifle charged by a handle drawn straight back has no bolt knob to turn: once the game
     * has racked it, however short the rack was, the right hand is on the grip while the time
     * since the rack runs on, as it is with no rack at all.
     */
    private static void aRifleRackedByAHandleTurnsNoKnobAfter(ArmModel model) {
        WeaponShape rifleShape = TestGuns.shape(TestGuns.longGun(true));
        float[] handle = {0f, -0.02f, 0.03f};
        float[][] still = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        ArmHolds.Weapon gun = worked(rifleShape, false, 0f, 0f);
        gun.placed().charged(handle);
        ArmHolds.place(model, SCALE, gun, new ArmHolds.Doing(0f, true, ArmActs.BOLT, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f), SAME, still, said,
                new ArmHolds.Carried(), new ArmHolds.Settled());
        for (float since : new float[] {0.3f, 0.6f, 0.9f}) {
            float[][] skins = new float[2][model.bones.length * 16];
            gun.placed().charged(handle);
            ArmHolds.place(model, SCALE, gun, new ArmHolds.Doing(0f, true, ArmActs.BOLT, since, ArmActs.HOLDING, 0f, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f), SAME, skins, said,
                    new ArmHolds.Carried(), new ArmHolds.Settled());
            check(java.util.Arrays.equals(skins[0], still[0]), "the right hand is on the grip " + since + " s after a short rack, as with none");
        }
    }

    /**
     * How a firearm is held while it is worked: at the hip or with the sights up, and brought up
     * before the eye and tipped over by `brought` of the way, as ViewModel moves it for every
     * load, unload, fetch and rack: the grip moved by as much as ViewModel moves the weapon there,
     * the muzzle raised and swung and the top tipped by HANDLED_LONG's or HANDLED_PISTOL's turns.
     */
    private static ArmHolds.Weapon worked(WeaponShape shape, boolean handgun, float aim, float brought) {
        float[] hip = handgun ? new float[] {0.12f, -0.16f, -0.32f} : new float[] {0.22f, -0.25f, -0.23f};
        float[] aimed = handgun ? new float[] {0f, -0.1f, -0.36f} : new float[] {0.09f, -0.18f, -0.35f};
        float[] hipAt = handgun ? new float[] {0.13f, -0.14f, 0.27f} : new float[] {0.09f, -0.12f, 0.27f};
        float[] to = handgun ? ViewModel.HANDLED_PISTOL : ViewModel.HANDLED_LONG;
        float[] at = new float[3];
        for (int axis = 0; axis < 3; axis++) {
            at[axis] = hip[axis] + (aimed[axis] - hip[axis]) * aim;
        }
        // The view's places are in tiles, ahead positive; the hand's in metres, ahead negative.
        at[0] += (to[0] - hipAt[0]) / SCALE * brought;
        at[1] += (to[1] - hipAt[1]) / SCALE * brought;
        at[2] -= (to[2] - hipAt[2]) / SCALE * brought;
        float cant = (float) Math.toRadians(8) * (1f - aim);
        return heldTurned(shape, handgun, at, to[3] * brought, to[4] * brought, cant + (to[5] - cant) * brought);
    }

    /**
     * While a bolt is worked or a lever swung, a slide racked, a magazine taken out, fetched and
     * seated, or a round pushed into a pump, a bolt, a double gun or a revolver, at the hip and
     * with the sights up, with the weapon brought up and tipped over as it is for each of these,
     * each arm asks its forearm to turn no further than a forearm turns: frame after frame, so a
     * hand crossing from one hold to the next is asked too.
     */
    private static void armsWorkingTheActionTurnNoFurtherThanAForearmTurns(ArmModel model) {
        WeaponShape rifleShape = TestGuns.shape(TestGuns.longGun(true)), leverShape = TestGuns.shape(TestGuns.longGun(true, 0.06f));
        WeaponShape pistolShape = TestGuns.shape(TestGuns.pistol());
        float frame = 1f / 60f;
        String[] names = {"a bolt worked", "a lever worked", "a slide racked", "a pistol magazine out", "a pistol magazine fetched", "a pistol magazine seated",
            "a rifle magazine taken hold of", "a rifle magazine fetched", "a round pushed into a pump", "a round pushed into a bolt", "a round pushed into a double gun",
            "a round pushed into a revolver"};
        for (float aim : new float[] {0f, 1f}) {
            for (float brought : new float[] {0f, 0.5f, 1f}) {
                for (int which = 0; which < names.length; which++) {
                    if (which < 2 && brought > 0f) {
                        // A bolt or a lever is worked after a shot, with the weapon held as it was fired, not brought up to be loaded.
                        continue;
                    }
                    boolean handgun = which == 2 || which == 3 || which == 4 || which == 5 || which == 11;
                    ArmHolds.Weapon gun = worked(which == 1 ? leverShape : handgun ? pistolShape : rifleShape, handgun, aim, brought);
                    ArmHolds.Settled settled = new ArmHolds.Settled();
                    settled.seconds = frame;
                    float most = 0f;
                    String where = "";
                    for (float t = 0f; t <= (which == 1 ? 0.45f : 2.2f); t += frame) {
                        ArmHolds.Doing doing = switch (which) {
                            case 0 -> new ArmHolds.Doing(aim, true, ArmActs.BOLT, t, ArmActs.HOLDING, 0f, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f);
                            case 1 -> new ArmHolds.Doing(aim, true, ArmActs.LEVER, t, ArmActs.HOLDING, 0f, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f);
                            case 2 -> new ArmHolds.Doing(aim, true, ArmActs.HANDGUN, -1f, ArmActs.RACKING, t, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f);
                            case 3 -> new ArmHolds.Doing(aim, true, ArmActs.HANDGUN, -1f, ArmActs.LOADING, t, 0f, 2f, -1f, false, t, -1f, 2f, 0f);
                            case 4 -> new ArmHolds.Doing(aim, true, ArmActs.HANDGUN, -1f, ArmActs.LOADING, t, 0f, 2f, -1f, false, -1f, t, 2.2f, 0f);
                            case 5 -> new ArmHolds.Doing(aim, true, ArmActs.HANDGUN, -1f, ArmActs.HOLDING, 0f, 0f, 1f, t, true, -1f, -1f, 2f, 0f);
                            case 6 -> new ArmHolds.Doing(aim, true, ArmActs.BOLT, -1f, ArmActs.UNLOADING, t, 0f, 2f, -1f, true, -1f, -1f, 2f, 0f);
                            case 7 -> new ArmHolds.Doing(aim, true, ArmActs.BOLT, -1f, ArmActs.LOADING, t, 0f, 2f, -1f, false, -1f, t, 2.2f, 0f);
                            case 8 -> new ArmHolds.Doing(aim, true, ArmActs.PUMP, -1f, ArmActs.LOADING, t, (t % 1f), 1f, -1f, true, -1f, -1f, 2f, 0f);
                            case 9 -> new ArmHolds.Doing(aim, true, ArmActs.BOLT_SINGLE, -1f, ArmActs.LOADING, t, (t % 1f), 1f, -1f, true, -1f, -1f, 2f, 0f);
                            case 10 -> new ArmHolds.Doing(aim, true, ArmActs.DOUBLE, -1f, ArmActs.LOADING, t, (t % 1f), 1f, -1f, true, -1f, -1f, 2f, 0f);
                            default -> new ArmHolds.Doing(aim, true, ArmActs.REVOLVER, -1f, ArmActs.LOADING, t, (t % 1f), 1f, -1f, true, -1f, -1f, 2f, 0f);
                        };
                        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
                        int arms = ArmHolds.place(model, SCALE, gun, doing, SAME, skins, said, new ArmHolds.Carried(), settled);
                        check(arms > 0, "the arms are placed with " + names[which] + " " + t + " s in");
                        for (int side = 0; side < arms; side++) {
                            float asked = Math.abs((float) Math.toDegrees(said[side][ArmPose.ASKED]));
                            if (asked > most) {
                                most = asked;
                                where = (side == 0 ? "the right arm " : "the left arm ") + String.format(java.util.Locale.ROOT, "%.2f", t) + " s in";
                            }
                        }
                    }
                    check(most <= Math.toDegrees(ArmPose.TWIST_MOST), "with " + names[which] + (aim == 0f ? " at the hip" : " with the sights up") + ", brought up " + brought
                            + ", each arm asks its forearm to turn no further than a forearm turns: " + where + " asks " + most + " degrees");
                }
            }
        }
    }

    /**
     * Both arms on a long gun at the hip, half way up and with the sights up, where the game's
     * long guns are held: the hand under the fore-end asks its forearm to turn no further than a
     * forearm turns, which with its palm squarely up and its fingers across the barrel it did by
     * 50 to 75 degrees, and its elbow hangs under its wrist.
     */
    private static void armsOnALongGunTurnNoFurtherThanAForearmTurns(ArmModel model) {
        WeaponShape shape = TestGuns.shape(TestGuns.longGun(true));
        float[][] grips = {{0.22f, -0.25f, -0.23f}, {0.155f, -0.215f, -0.29f}, {0.09f, -0.18f, -0.35f}};
        float[] pointing = {0f, 0f, -1f}, over = {0f, 1f, 0f};
        for (int at = 0; at < grips.length; at++) {
            float aim = at / 2f;
            float[] grip = {grips[at][0] * SCALE, grips[at][1] * SCALE, grips[at][2] * SCALE};
            ArmHolds.Placed placed = TestGuns.placed(shape, grip, pointing, over, SCALE, 0f, Float.NaN);
            ArmHolds.Weapon gun = new ArmHolds.Weapon(true, false, placed.at(shape.grip), pointing, over, placed);
            float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
            ArmHolds.Doing holding = new ArmHolds.Doing(aim, true, ArmActs.BOLT, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f);
            check(ArmHolds.place(model, SCALE, gun, holding, SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled()) == 2, "a long gun has both arms");
            for (int side = 0; side < 2; side++) {
                String arm = (side == 0 ? "the right arm" : "the left arm") + " on a long gun raised " + aim;
                float asked = (float) Math.toDegrees(said[side][ArmPose.ASKED]);
                check(Math.abs(asked) <= Math.toDegrees(ArmPose.TWIST_MOST), arm + " asks its forearm to turn no further than a forearm turns: " + asked + " degrees");
            }
            float drop = (said[1][ArmPose.WRIST + 1] - said[1][ArmPose.ELBOW + 1]) / SCALE;
            check(drop > 0.03f, "the left elbow on a long gun raised " + aim + " hangs under its wrist: " + drop + " m");
        }
    }

    /**
     * The right palm on a long gun faces the grip as LONG_PALM_BACK and LONG_PALM_DOWN say, and
     * not as a hand laid along knuckles leant across the palm turns it: that turned it back 28
     * degrees where it said 35 and tipped it 14 down the grip. On a lever gun's wrist, raked 50
     * degrees, the arm is asked to turn no further than a forearm turns.
     */
    private static void rightPalmOnALongGunFacesAsItsHoldSays(ArmModel model) {
        float[] pointing = {0f, 0f, -1f}, over = {0f, 1f, 0f}, right = {1f, 0f, 0f};
        for (float rake : new float[] {0.04f, 0.06f}) {
            WeaponShape shape = TestGuns.shape(TestGuns.longGun(true, rake));
            for (float aim : new float[] {0f, 1f}) {
                float[] grip = aim == 0f ? new float[] {0.22f * SCALE, -0.25f * SCALE, -0.23f * SCALE} : new float[] {0.09f * SCALE, -0.18f * SCALE, -0.35f * SCALE};
                ArmHolds.Placed placed = TestGuns.placed(shape, grip, pointing, over, SCALE, 0f, Float.NaN);
                ArmHolds.Weapon gun = new ArmHolds.Weapon(true, false, placed.at(shape.grip), pointing, over, placed);
                float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
                ArmHolds.Doing holding = new ArmHolds.Doing(aim, true, ArmActs.BOLT, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f);
                check(ArmHolds.place(model, SCALE, gun, holding, SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled()) == 2, "a long gun has both arms");
                String how = "a long gun whose wrist is raked " + rake + (aim == 0f ? " at the hip" : " with the sights up");
                // The grip's line in the view, and the way ahead square to it: the mesh's y along and z up are the view's -z and y.
                float[] line = {0f, shape.gripLine[2], -shape.gripLine[1]}, front = new float[3];
                ArmPose.unit(line[0], line[1], line[2], line);
                float along = ArmPose.dot(pointing, line);
                ArmPose.unit(pointing[0] - line[0] * along, pointing[1] - line[1] * along, pointing[2] - line[2] * along, front);
                float back = ArmHolds.LONG_PALM_BACK, down = ArmHolds.LONG_PALM_DOWN;
                float[] want = new float[3];
                for (int axis = 0; axis < 3; axis++) {
                    want[axis] = -(right[axis] * (float) Math.cos(back) + front[axis] * (float) Math.sin(back)) * (float) Math.cos(down) - line[axis] * (float) Math.sin(down);
                }
                float[] hold = model.holds[model.pose("grip")];
                float[] held = placed(skins[0], model.wrist, hold), out = placed(skins[0], model.wrist, new float[] {hold[0] + model.out[0], hold[1] + model.out[1], hold[2] + model.out[2]});
                float[] palm = new float[3];
                ArmPose.unit(out[0] - held[0], out[1] - held[1], out[2] - held[2], palm);
                float off = (float) Math.toDegrees(Math.acos(Math.min(1f, ArmPose.dot(palm, want))));
                check(off < 1f, "the right palm on " + how + " faces as its hold says: " + off + " degrees off");
                float asked = (float) Math.toDegrees(said[0][ArmPose.ASKED]);
                check(Math.abs(asked) <= Math.toDegrees(ArmPose.TWIST_MOST), "the right arm on " + how + " asks its forearm to turn no further than a forearm turns: " + asked + " degrees");
            }
        }
    }

    /**
     * A mesh's barrel and top come from its muzzle attachment, as the game turns it; one whose
     * barrel runs along its top has no way up square to it, and says so rather than reading the
     * mesh along a top of no length.
     */
    private static void meshAxesComeFromTheMuzzleAttachment() {
        float[] along = new float[3], up = new float[3];
        check(WeaponShape.axes(-90f, 0f, 0.4f, along, up) && near(along[1], 1f, 1e-5f) && near(up[2], 1f, 1e-5f), "a long gun's barrel along its y has its top on its +z");
        check(WeaponShape.axes(-90f, 0f, -0.1f, along, up) && near(along[1], 1f, 1e-5f) && near(up[2], -1f, 1e-5f), "a handgun's, whose muzzle is under its origin, on its -z");
        check(WeaponShape.axes(-70f, 0f, 0.4f, along, up) && near(ArmPose.dot(along, up), 0f, 1e-5f) && near(ArmPose.dot(up, up), 1f, 1e-5f) && up[2] > 0f,
                "a barrel tipped up has its top square to it, still on the +z side");
        check(!WeaponShape.axes(0f, 0f, 0.4f, along, up), "an attachment not turned has its barrel along the top, and no top to read the mesh by");
    }

    /** One Placed is filled again each frame from matrices its caller keeps and overwrites: it copies them, and places the mesh by the last it was given. */
    private static void heldMeshIsFilledAgainEachFrame() {
        WeaponShape shape = TestGuns.shape(TestGuns.pistol());
        float[] matrix = {1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};
        ArmHolds.Placed placed = new ArmHolds.Placed();
        placed.set(shape, matrix, matrix, 0f, Float.NaN, ArmHolds.BOLT_TRAVEL);
        matrix[12] = 1f;
        check(placed.at(new float[3])[0] == 0f, "a matrix changed after it was given does not move the mesh");
        placed.set(shape, matrix, matrix, 0f, Float.NaN, ArmHolds.BOLT_TRAVEL);
        check(placed.at(new float[3])[0] == 1f && placed.stillAt(0f, 0f, 0f)[0] == 1f, "filled again, it places the mesh by the matrix it was given last");
    }

    private static void leftHandIsUnderTheForeEndsUnderside(ArmModel model) {
        WeaponShape shape = TestGuns.shape(TestGuns.longGun(true));
        float[] grip = {0f, -0.06f, -0.12f}, pointing = {0f, 0f, -1f}, over = {0f, 1f, 0f};
        // The hand rests within the arm's easy reach.
        ArmHolds.Placed placed = TestGuns.placed(shape, grip, pointing, over, SCALE, 0f, 0.3f);
        ArmHolds.Weapon gun = new ArmHolds.Weapon(true, false, placed.at(shape.grip), pointing, over, placed);
        float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
        ArmHolds.Doing holding = new ArmHolds.Doing(1f, true, ArmActs.BOLT, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f);
        check(ArmHolds.place(model, SCALE, gun, holding, SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled()) == 2, "a long gun has both arms");
        float[] on = placed(skins[1], model.wrist, model.holds[model.pose("fore")]);
        float a = -(on[2] - gun.grip()[2]) / SCALE + shape.grip[1], u = (on[1] - gun.grip()[1]) / SCALE + shape.grip[2];
        check(near(a, 0.3f, 0.002f), "the left hand is where it rests along the fore-end: " + a);
        check(u > TestGuns.FORE_FOOT && u <= TestGuns.FORE_FOOT + WeaponShape.FORE_HALF + 1e-3f, "and the middle of what it holds is just over the fore-end's underside, not in the air under it: " + u);
    }

    private static float[] placed(float[] skins, int bone, float[] point) {
        float[] out = new float[3];
        for (int row = 0; row < 3; row++) {
            out[row] = skins[bone * 16 + row] * point[0] + skins[bone * 16 + 4 + row] * point[1] + skins[bone * 16 + 8 + row] * point[2] + skins[bone * 16 + 12 + row];
        }
        return out;
    }

    private static boolean near(float a, float b, float within) {
        return Math.abs(a - b) < within;
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}
