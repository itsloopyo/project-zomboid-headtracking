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
        Checks.run("oddMeshesHaveNoShapeAndSayWhy", GripTests::oddMeshesHaveNoShapeAndSayWhy);
        Checks.run("rightHandClosesRoundAPistolsGripAlongItsRake", () -> rightHandClosesRoundAPistolsGripAlongItsRake(model));
        Checks.run("leftHandWrapsTheRightOnAPistolHeldInBoth", () -> leftHandWrapsTheRightOnAPistolHeldInBoth(model));
        Checks.run("leftHandIsUnderTheForeEndsUnderside", () -> leftHandIsUnderTheForeEndsUnderside(model));
        Checks.run("armsOnAPistolComeUpFromUnderThePictureUnturned", () -> armsOnAPistolComeUpFromUnderThePictureUnturned(model));
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
    }

    /** The mod's own shotgun and rifle are held as the game's firearms are: their meshes say where their grips are. */
    private static void modsOwnModelsHaveAGripAndAForeEnd() throws IOException {
        for (String name : new String[] {"shotgun", "rifle"}) {
            ByteBuffer read;
            try (InputStream packed = new GZIPInputStream(GripTests.class.getResourceAsStream("/models/" + name + ".mesh.gz"))) {
                read = ByteBuffer.wrap(packed.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            }
            read.getInt();
            read.getInt();
            float[] muzzle = {read.getFloat(), read.getFloat(), read.getFloat()};
            for (int skip = 0; skip < 9; skip++) {
                read.getFloat();
            }
            int vertices = read.getInt(), indices = 0;
            for (int part = 0; part < OwnModel.PARTS; part++) {
                indices += read.getInt();
            }
            int vertexBytes = 3 * Float.BYTES + 9 * Short.BYTES;
            float[] places = new float[vertices * 3];
            int[] corners = new int[indices];
            int at = read.position();
            for (int vertex = 0; vertex < vertices; vertex++) {
                for (int axis = 0; axis < 3; axis++) {
                    places[vertex * 3 + axis] = read.getFloat(at + vertex * vertexBytes + axis * Float.BYTES);
                }
            }
            for (int index = 0; index < indices; index++) {
                corners[index] = read.getInt(at + vertices * vertexBytes + index * Integer.BYTES);
            }
            StringBuilder why = new StringBuilder();
            WeaponShape shape = WeaponShape.read(places, corners, new float[] {0f, 1f, 0f}, new float[] {0f, 0f, 1f}, muzzle, 1f, why);
            check(shape != null, "the " + name + "'s mesh has a grip: " + why);
            if (shape != null) {
                check(ArmPose.apart(shape.grip, new float[3]) < 0.08f && shape.grip[2] < shape.bore, "the " + name + "'s grip is under its bore and within a hand of its origin: "
                        + shape.grip[1] + ", " + shape.grip[2]);
                check(shape.foreTo > shape.foreFrom && shape.levelTo > shape.levelFrom, "and it has an underside ahead of its guard");
            }
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
            float[] hold = model.holds[model.pose("grip")];
            float[] held = placed(skins[0], model.wrist, hold);
            WeaponShape shape = pistol.placed().shape();
            check(ArmPose.apart(held, pistol.grip()) < 1e-4f, "the right hand closes round the grip's middle, which is under the guard and not the frame or the slide");
            float[] thumb = placed(skins[0], model.wrist, new float[] {hold[0] + model.across[0], hold[1] + model.across[1], hold[2] + model.across[2]});
            float[] across = {(thumb[0] - held[0]) / SCALE, (thumb[1] - held[1]) / SCALE, (thumb[2] - held[2]) / SCALE};
            // The grip lies aslant across the palm: the knuckles lean back from its raked line by GRIP_ASLANT.
            float[] line = pistol.placed().way(shape.gripLine), front = new float[3], aslant = new float[3];
            float along = ArmPose.dot(pistol.pointing(), line);
            ArmPose.unit(pistol.pointing()[0] - line[0] * along, pistol.pointing()[1] - line[1] * along, pistol.pointing()[2] - line[2] * along, front);
            for (int axis = 0; axis < 3; axis++) {
                aslant[axis] = line[axis] * (float) Math.cos(ArmHolds.GRIP_ASLANT) - front[axis] * (float) Math.sin(ArmHolds.GRIP_ASLANT);
            }
            check(ArmPose.dot(across, aslant) > Math.cos(Math.toRadians(3)), "its knuckles run from the little finger to the first aslant across the grip's raked line: "
                    + Math.toDegrees(Math.acos(Math.min(1f, ArmPose.dot(across, aslant)))) + " degrees off");
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
     * Both arms on a pistol, at the hip and with the sights up, as the game's pistols are held: each
     * forearm turned along its length no further than a forearm turns, so the upper arm is not
     * rolled to make up the rest, and each elbow under its wrist, so the forearm comes up into the
     * picture from below and does not lie across it from the side.
     */
    private static void armsOnAPistolComeUpFromUnderThePictureUnturned(ArmModel model) {
        for (boolean aimed : new boolean[] {false, true}) {
            ArmHolds.Weapon pistol = pistolHeld(aimed);
            float[][] skins = new float[2][model.bones.length * 16], said = new float[2][ArmPose.SAYS];
            ArmHolds.Doing both = new ArmHolds.Doing(aimed ? 1f : 0f, true, ArmActs.HANDGUN, -1f, ArmActs.HOLDING, 0f, 0f, 1f, -1f, true, -1f, -1f, 2f, 0f);
            check(ArmHolds.place(model, SCALE, pistol, both, SAME, skins, said, new ArmHolds.Carried(), new ArmHolds.Settled()) == 2, "a pistol held in both hands has both arms");
            for (int side = 0; side < 2; side++) {
                String arm = (side == 0 ? "the right" : "the left") + (aimed ? " arm with the sights up" : " arm at the hip");
                float asked = (float) Math.toDegrees(said[side][ArmPose.ASKED]);
                check(Math.abs(asked) <= Math.toDegrees(ArmPose.TWIST_MOST), arm + " asks its forearm to turn no further than a forearm turns: " + asked + " degrees");
                float drop = (said[side][ArmPose.WRIST + 1] - said[side][ArmPose.ELBOW + 1]) / SCALE;
                check(drop > 0.08f, arm + " has its elbow well under its wrist: " + drop + " m");
            }
        }
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
