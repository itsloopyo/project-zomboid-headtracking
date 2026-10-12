package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.ArrayList;
import java.util.List;

/** Tests of how something swung is held and hit with, by what its mesh says of it. */
final class MeleeTests {
    private MeleeTests() {}

    static void run() {
        Checks.run("anAxeStrikesWithTheSideItsHeadStandsOutTo", MeleeTests::anAxeStrikesWithTheSideItsHeadStandsOutTo);
        Checks.run("aBladeStrikesWithItsEdgeNotItsFlat", MeleeTests::aBladeStrikesWithItsEdgeNotItsFlat);
        Checks.run("aPanStrikesWithItsFaceWhicheverWayItIsTurned", MeleeTests::aPanStrikesWithItsFaceWhicheverWayItIsTurned);
        Checks.run("aRoundClubStrikesWithTheMeshesMinusZ", MeleeTests::aRoundClubStrikesWithTheMeshesMinusZ);
        Checks.run("everyBlowLandsWithTheStrikingSideLeading", MeleeTests::everyBlowLandsWithTheStrikingSideLeading);
        Checks.run("theTurnAboutTheHandleComesAndGoesWithTheBlow", MeleeTests::theTurnAboutTheHandleComesAndGoesWithTheBlow);
        Checks.run("atRestTheFarEndIsInThePicture", MeleeTests::atRestTheFarEndIsInThePicture);
        Checks.run("whatFitsUprightStandsAsItAlwaysDid", MeleeTests::whatFitsUprightStandsAsItAlwaysDid);
        Checks.run("aSpearIsHeldLevelWithTheLeftHandAhead", MeleeTests::aSpearIsHeldLevelWithTheLeftHandAhead);
        Checks.run("handsGoWhereTheGamesHandIsOnEveryHandle", MeleeTests::handsGoWhereTheGamesHandIsOnEveryHandle);
        Checks.run("aLongWeaponLandsOnWhatItHits", MeleeTests::aLongWeaponLandsOnWhatItHits);
        Checks.run("aScaledModelIsHeldInMetres", MeleeTests::aScaledModelIsHeldInMetres);
        Checks.run("toolsAndBladesStrikeAsTheirKindDoes", MeleeTests::toolsAndBladesStrikeAsTheirKindDoes);
    }

    /** A box's eight corners, as a mesh's places. */
    private static void box(List<Float> places, float x0, float x1, float y0, float y1, float z0, float z1) {
        for (int corner = 0; corner < 8; corner++) {
            places.add((corner & 1) == 0 ? x0 : x1);
            places.add((corner & 2) == 0 ? y0 : y1);
            places.add((corner & 4) == 0 ? z0 : z1);
        }
    }

    private static float[] floats(List<Float> list) {
        float[] out = new float[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    private static void aBladeStrikesWithItsEdgeNotItsFlat() {
        // A head 0.016 thick in x, 0.09 deep in z and 0.06 along: the blade of the game's axes, a plate whose face is x.
        List<Float> axe = new ArrayList<>();
        box(axe, -0.01f, 0.01f, -0.02f, 0.25f, -0.01f, 0.01f);
        for (float y = 0.2f; y <= 0.256f; y += 0.004f) {
            for (float z = -0.063f; z <= 0.027f; z += 0.005f) {
                for (float x = -0.008f; x <= 0.0081f; x += 0.016f) {
                    axe.add(x);
                    axe.add(y);
                    axe.add(z);
                }
            }
        }
        MeleeHold.Shape shape = MeleeHold.of(floats(axe));
        check(shape.strikeX() == 0f && shape.strikeZ() == -1f, "an axe blade strikes with its edge, -z, and not its flat: " + shape.strikeX() + ", " + shape.strikeZ());
    }

    private static void anAxeStrikesWithTheSideItsHeadStandsOutTo() {
        // The game's hand axe: a handle 0.25 long, its head a wedge out to -0.063 in z and back to 0.027.
        List<Float> axe = new ArrayList<>();
        box(axe, -0.01f, 0.01f, -0.02f, 0.25f, -0.01f, 0.01f);
        box(axe, -0.013f, 0.013f, 0.2f, 0.256f, -0.063f, 0.027f);
        MeleeHold.Shape shape = MeleeHold.of(floats(axe));
        check(shape.strikeZ() < -0.99f, "an axe's edge is its -z: " + shape.strikeX() + ", " + shape.strikeZ());
        check(Math.abs(shape.butt() + 0.02f) < 1e-6f && Math.abs(shape.tip() - 0.256f) < 1e-6f, "and it runs from its butt to its head along y");
    }

    private static void aPanStrikesWithItsFaceWhicheverWayItIsTurned() {
        for (int turn = 0; turn < 8; turn++) {
            double angle = Math.toRadians(turn * 45 + 10);
            float nx = (float) Math.cos(angle), nz = (float) Math.sin(angle);
            List<Float> pan = new ArrayList<>();
            box(pan, -0.008f, 0.008f, -0.02f, 0.12f, -0.008f, 0.008f);
            // A plate 0.13 across and 0.012 thick, square to (nx, 0, nz), from 0.13 to 0.26 along.
            for (int i = 0; i < 64; i++) {
                double around = Math.PI * 2 * i / 64;
                for (int side = -1; side <= 1; side += 2) {
                    float across = (float) Math.cos(around) * 0.065f, along = 0.195f + (float) Math.sin(around) * 0.065f;
                    pan.add(-nz * across + nx * side * 0.006f);
                    pan.add(along);
                    pan.add(nx * across + nz * side * 0.006f);
                }
            }
            MeleeHold.Shape shape = MeleeHold.of(floats(pan));
            if (Math.abs(nz) >= MeleeHold.FACING + 0.05f) {
                float facing = Math.abs(shape.strikeX() * nx + shape.strikeZ() * nz);
                check(facing > 0.99f && shape.strikeZ() <= 0f, "a pan turned " + (turn * 45 + 10) + " degrees strikes with its face, the one more to -z: (" + shape.strikeX() + ", " + shape.strikeZ() + ")");
            } else if (Math.abs(nz) < MeleeHold.FACING - 0.05f) {
                check(shape.strikeX() == 0f && shape.strikeZ() == -1f, "a plate edge on to -z, turned " + (turn * 45 + 10) + " degrees, strikes with its edge");
            }
        }
    }

    private static void aRoundClubStrikesWithTheMeshesMinusZ() {
        List<Float> bat = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            double around = Math.PI * 2 * i / 48;
            for (float along = -0.026f; along <= 0.47f; along += 0.05f) {
                float r = 0.012f + 0.016f * Math.max(along, 0f) / 0.47f;
                bat.add((float) Math.cos(around) * r);
                bat.add(along);
                bat.add((float) Math.sin(around) * r);
            }
        }
        MeleeHold.Shape shape = MeleeHold.of(floats(bat));
        check(shape.strikeX() == 0f && shape.strikeZ() == -1f, "a round club has no plate: it strikes with -z, as the game's art does");
    }

    /** Where the far end is with what is swung held as `pose` has it, `share` of the way into a blow `way`, and the matrix. */
    private static float[] placed(float[] pose, int way, float share, MeleeHold.Shape shape, float[] matrix) {
        float[] top = new float[3], end = new float[3];
        MeleeHold.top(shape, way, share, top);
        ViewModelMath.hold(new float[3], new float[] {0f, 1f, 0f}, top, null, 1.5f, pose[3], pose[4], pose[5], matrix);
        ViewModelMath.turn(matrix, pose[0], pose[1], pose[2], pose[3], pose[4], -pose[5], 0f, 0f, 0f);
        ViewModelMath.place(matrix, 0f, 0.4f, 0f, end);
        return end;
    }

    private static void everyBlowLandsWithTheStrikingSideLeading() {
        float[] rest = new float[ViewModelMath.HOLD];
        MeleeHold.rest(MeleeHold.ONE_HANDED, 0.4f, true, rest);
        MeleeHold.Shape[] shapes = {new MeleeHold.Shape(0f, 0.47f, 0f, -1f), new MeleeHold.Shape(0f, 0.26f, 0.7071f, -0.7071f), new MeleeHold.Shape(0f, 0.26f, -1f, 0f)};
        for (MeleeHold.Shape shape : shapes) {
            for (int way = ViewModelMath.SWUNG; way <= ViewModelMath.FLOORED; way++) {
                float[] pose = new float[ViewModelMath.HOLD], matrix = new float[16], side = new float[3];
                ViewModelMath.attack(way, MeleeHold.LANDING_FROM, 0f, false, rest, pose);
                float[] before = placed(pose, way, MeleeHold.share(MeleeHold.LANDING_FROM, 0f, false), shape, matrix);
                ViewModelMath.attack(way, 1f, 0f, false, rest, pose);
                float[] after = placed(pose, way, MeleeHold.share(1f, 0f, false), shape, matrix);
                float[] going = {after[0] - before[0], after[1] - before[1], after[2] - before[2]}, along = new float[3];
                ViewModelMath.turned(matrix, new float[] {0f, 1f, 0f}, along);
                ArmPose.unit(along[0], along[1], along[2], along);
                float on = ArmPose.dot(going, along);
                for (int axis = 0; axis < 3; axis++) {
                    going[axis] -= along[axis] * on;
                }
                ArmPose.unit(going[0], going[1], going[2], going);
                ViewModelMath.turned(matrix, new float[] {shape.strikeX(), 0f, shape.strikeZ()}, side);
                ArmPose.unit(side[0], side[1], side[2], side);
                double off = Math.toDegrees(Math.acos(Math.clamp(ArmPose.dot(going, side), -1f, 1f)));
                check(off < 10, "way " + way + " lands with the striking side (" + shape.strikeX() + ", " + shape.strikeZ() + ") leading, " + Math.round(off) + " degrees off the way the head goes");
            }
        }
    }

    private static void theTurnAboutTheHandleComesAndGoesWithTheBlow() {
        MeleeHold.Shape pan = new MeleeHold.Shape(0f, 0.26f, 0.7071f, -0.7071f);
        float[] top = new float[3];
        for (int way = ViewModelMath.SWUNG; way <= ViewModelMath.STABBED; way++) {
            MeleeHold.top(pan, way, MeleeHold.share(0f, 0f, false), top);
            check(top[0] == 0f && top[2] == 1f, "at rest it stands in the hand as the game's art has it, its top the mesh's z, way " + way);
            check(MeleeHold.share(1f, 0f, false) == 1f, "as it lands it is turned all the way, way " + way);
            check(MeleeHold.share(1f, 1f, true) == 0f && MeleeHold.share(1f, 1f, false) == 0f, "and held again it is back, way " + way);
            float last = 0f;
            for (int step = 1; step <= 50; step++) {
                float now = MeleeHold.share(step / 50f, 0f, false);
                check(Math.abs(now - last) <= 0.1f, "without a jump on the way, way " + way + " step " + step);
                last = now;
            }
        }
        check(MeleeHold.LEADS[ViewModelMath.THRUST] == 0f && MeleeHold.LEADS[ViewModelMath.SHOVED] == 0f && MeleeHold.LEADS[ViewModelMath.STABBED] == 0f,
                "a thrust, a shove and a stab are not turned to lead");
        MeleeHold.top(new MeleeHold.Shape(0f, 0.26f, 0f, -1f), ViewModelMath.THRUST, 1f, top);
        check(top[0] == 0f && top[2] == 1f, "and something striking with -z is not turned for them at all");
    }

    /** One of the meshes' units in metres, at the scale the game draws most melee models. */
    private static final float M = 1f / Characters.PER_METRE;

    private static final float TAN = (float) Math.tan(Math.toRadians(FieldOfView.DEFAULT_DEGREES) / 2);

    private static void atRestTheFarEndIsInThePicture() {
        for (int kind = MeleeHold.ONE_HANDED; kind <= MeleeHold.KNIFE; kind++) {
            for (float reach = 0.1f; reach <= 1.4f; reach += 0.05f) {
                for (boolean trueHands : new boolean[] {true, false}) {
                    float[] rest = new float[ViewModelMath.HOLD];
                    MeleeHold.rest(kind, reach, trueHands, rest);
                    float up = MeleeHold.farEndUp(rest, reach);
                    check(up <= MeleeHold.REST_TOP * TAN + 1e-3f, "class " + kind + " reaching " + reach + " has its far end in the picture: " + up);
                }
            }
        }
    }

    private static void whatFitsUprightStandsAsItAlwaysDid() {
        // What main held everything swung at, in the mod's hand and in the game's, and how far a bat reaches.
        float[] before = {(float) Math.toRadians(60), (float) Math.toRadians(-12), (float) Math.toRadians(-10), 0.2f, -0.22f, 0.38f, 0f};
        float[] beforeGame = before.clone();
        beforeGame[0] = (float) Math.toRadians(35);
        for (int kind : new int[] {MeleeHold.ONE_HANDED, MeleeHold.TWO_HANDED, MeleeHold.HEAVY, MeleeHold.KNIFE}) {
            for (float reach = 0.1f; reach <= MeleeHold.BAT_REACH; reach += 0.05f) {
                for (boolean trueHands : new boolean[] {true, false}) {
                    float[] rest = new float[ViewModelMath.HOLD];
                    MeleeHold.rest(kind, reach, trueHands, rest);
                    for (int i = 0; i < ViewModelMath.HOLD; i++) {
                        check(Math.abs(rest[i] - (trueHands ? before : beforeGame)[i]) < 1e-6f, "class " + kind + " reaching " + reach + " stands where it stood, part " + i);
                    }
                }
            }
        }
    }

    private static void aScaledModelIsHeldInMetres() {
        // The game draws the large bone club at 1.4 and the bone hatchet at 0.6 of its mesh.
        for (float scale : new float[] {0.6f, 1f, 1.4f}) {
            MeleeHold.Shape club = new MeleeHold.Shape(-0.035f, 0.151f, 0f, -1f);
            float perUnit = scale / Characters.PER_METRE;
            float hand = MeleeHold.hand(MeleeHold.ONE_HANDED, club, perUnit);
            check(Math.abs((hand - club.butt()) * perUnit - MeleeHold.HAND_IN) < 1e-5f, "at " + scale + " the hand is a tenth of a metre in from the end: " + (hand - club.butt()) * perUnit);
            MeleeHold.Fit fit = MeleeHold.fit(MeleeHold.ONE_HANDED, club, perUnit, scale * 1.5f, true);
            check(Math.abs(fit.reach() - (club.tip() - hand) * scale * 1.5f) < 1e-5f, "and its reach is in the tiles it is drawn at");
            MeleeHold.Shape spear = new MeleeHold.Shape(-0.09f, 0.68f, 0f, -1f);
            float spearHand = MeleeHold.hand(MeleeHold.SPEAR, spear, perUnit);
            check(Math.abs((spearHand - spear.butt()) * perUnit - MeleeHold.SPEAR_BUTT) < 1e-5f
                    && MeleeHold.second(MeleeHold.SPEAR, spear, perUnit) <= (spear.tip() - spearHand) * perUnit / 2f + 1e-5f,
                    "a spear's hands are in metres at " + scale);
        }
    }

    /** A flat plate along y from `from` to `to`, `across` wide one way and `thick` the other, its face along (nx, 0, nz). */
    private static void plate(List<Float> places, float from, float to, float across, float thick, float nx, float nz) {
        for (float y = from; y <= to; y += 0.005f) {
            for (float a = -across / 2f; a <= across / 2f + 1e-6f; a += across / 8f) {
                for (float t = -thick / 2f; t <= thick / 2f + 1e-6f; t += thick) {
                    places.add(-nz * a + nx * t);
                    places.add(y);
                    places.add(nx * a + nz * t);
                }
            }
        }
    }

    private static void toolsAndBladesStrikeAsTheirKindDoes() {
        // A shovel's and a paddle's blades are plates facing z: they strike with the face nearer -z.
        for (float[] flat : new float[][] {{0.33f, 0.61f, 0.15f}, {0.45f, 0.64f, 0.12f}}) {
            List<Float> tool = new ArrayList<>();
            box(tool, -0.01f, 0.01f, -0.04f, flat[0], -0.01f, 0.01f);
            plate(tool, flat[0], flat[1], flat[2], 0.006f, 0f, 1f);
            MeleeHold.Shape shape = MeleeHold.of(floats(tool));
            check(shape.strikeX() == 0f && shape.strikeZ() == -1f, "a blade facing z strikes with its face: " + shape.strikeX() + ", " + shape.strikeZ());
        }
        // A cleaver's blade and a sickle's hook lie edge on to -z, in the plane of y and z: they strike with the edge, not the flat.
        List<Float> cleaver = new ArrayList<>();
        box(cleaver, -0.006f, 0.006f, -0.02f, 0.07f, -0.006f, 0.006f);
        plate(cleaver, 0.07f, 0.21f, 0.09f, 0.004f, 1f, 0f);
        MeleeHold.Shape shape = MeleeHold.of(floats(cleaver));
        check(shape.strikeX() == 0f && shape.strikeZ() == -1f, "a cleaver strikes with its edge: " + shape.strikeX() + ", " + shape.strikeZ());
        List<Float> sickle = new ArrayList<>();
        box(sickle, -0.008f, 0.008f, -0.026f, 0.07f, -0.008f, 0.008f);
        for (int i = 0; i <= 40; i++) {
            double around = Math.PI * i / 40;
            float y = 0.15f + (float) Math.sin(around) * 0.08f, z = (float) Math.cos(around) * 0.08f - 0.03f;
            for (float x = -0.002f; x <= 0.0021f; x += 0.004f) {
                for (float w = -0.01f; w <= 0.0101f; w += 0.02f) {
                    sickle.add(x);
                    sickle.add(y + w);
                    sickle.add(z);
                }
            }
        }
        shape = MeleeHold.of(floats(sickle));
        check(shape.strikeX() == 0f && shape.strikeZ() == -1f, "a sickle's hook, flat in y and z, strikes with -z and not its flat: " + shape.strikeX() + ", " + shape.strikeZ());
    }

    private static void aSpearIsHeldLevelWithTheLeftHandAhead() {
        float[] rest = new float[ViewModelMath.HOLD];
        MeleeHold.rest(MeleeHold.SPEAR, 1.0f, true, rest);
        check(Math.abs(rest[0]) < Math.toRadians(15), "a spear points ahead, not up: " + Math.toDegrees(rest[0]));
        // The game's crafted spear, and its short forged one, whose mesh is made from its middle.
        MeleeHold.Shape crafted = new MeleeHold.Shape(-0.09f, 0.68f, 0f, -1f), forged = new MeleeHold.Shape(-0.373f, 0.373f, 0f, -1f);
        check(Math.abs(MeleeHold.hand(MeleeHold.SPEAR, crafted, M) - 0.029f) < 0.01f && Math.abs(MeleeHold.hand(MeleeHold.SPEAR, forged, M) + 0.254f) < 0.01f,
                "its rear hand is where the game's is, a quarter of a metre in from the butt: " + MeleeHold.hand(MeleeHold.SPEAR, crafted, M) + ", " + MeleeHold.hand(MeleeHold.SPEAR, forged, M));
        check(MeleeHold.second(MeleeHold.SPEAR, crafted, M) == MeleeHold.SPEAR_APART, "its left hand is out along the shaft ahead of the right");
        MeleeHold.Shape stub = new MeleeHold.Shape(-0.09f, 0.2f, 0f, -1f);
        check(Math.abs(MeleeHold.second(MeleeHold.SPEAR, stub, M) - (0.2f - MeleeHold.hand(MeleeHold.SPEAR, stub, M)) * M / 2f) < 1e-5f,
                "and never past half way to the point");
    }

    private static void handsGoWhereTheGamesHandIsOnEveryHandle() {
        // Where the game's own right hand was on each, in its idle and its swings: the mesh's units from its origin, and the butt.
        float[][] seen = {{-0.015f, 0.016f, 0.047f}, {-0.018f, 0.027f, 0.047f}, {-0.028f, 0.021f, 0.052f}, {-0.022f, 0.027f, 0.051f}, {-0.018f, 0.028f, 0.047f}};
        for (float[] one : seen) {
            float hand = MeleeHold.hand(MeleeHold.ONE_HANDED, new MeleeHold.Shape(one[0], 0.3f, 0f, -1f), M);
            check(hand > one[1] - 0.01f && hand < one[2] + 0.01f, "a hand alone is where the game's is: " + hand + " in " + one[1] + ".." + one[2]);
        }
        float bat = MeleeHold.hand(MeleeHold.TWO_HANDED, new MeleeHold.Shape(-0.026f, 0.467f, 0f, -1f), M);
        check(Math.abs(bat - 0.062f) < 0.015f, "a bat's right hand is where the game's is: " + bat);
        MeleeHold.Shape sledge = new MeleeHold.Shape(-0.038f, 0.653f, 0f, -1f);
        check(MeleeHold.hand(MeleeHold.HEAVY, sledge, M) < 0.07f, "a sledgehammer is held at its end, not half way up as the game's animation slides it to: " + MeleeHold.hand(MeleeHold.HEAVY, sledge, M));
        check(MeleeHold.second(MeleeHold.HEAVY, sledge, M) == -ArmHolds.LOWER_FIST && MeleeHold.second(MeleeHold.TWO_HANDED, sledge, M) == -ArmHolds.LOWER_FIST,
                "with the left fist under the right at the end");
    }

    private static void aLongWeaponLandsOnWhatItHits() {
        float[] rest = new float[ViewModelMath.HOLD];
        MeleeHold.rest(MeleeHold.TWO_HANDED, MeleeHold.BAT_REACH, true, rest);
        for (int way = ViewModelMath.SWUNG; way <= ViewModelMath.STABBED; way++) {
            for (float reach = 0.1f; reach <= MeleeHold.BAT_REACH; reach += 0.05f) {
                check(MeleeHold.aimed(way, reach) == 0f, "nothing as long as a bat or shorter is turned, way " + way + " reaching " + reach);
            }
            float[] pose = new float[ViewModelMath.HOLD];
            ViewModelMath.attack(way, 1f, 0f, false, rest, pose);
            float bat = MeleeHold.across(pose, MeleeHold.BAT_REACH);
            for (float reach = 0.65f; reach <= 1.4f; reach += 0.05f) {
                float[] turned = pose.clone();
                turned[1] += MeleeHold.aimed(way, reach);
                boolean straight = way == ViewModelMath.THRUST || way == ViewModelMath.SHOVED || way == ViewModelMath.STABBED;
                if (straight) {
                    check(MeleeHold.aimed(way, reach) == 0f, "a thrust, a shove or a stab is not turned, way " + way);
                } else {
                    float share = Math.clamp((MeleeHold.FULL_REACH - reach) / (MeleeHold.FULL_REACH - MeleeHold.BAT_REACH), 0f, 1f);
                    float off = (float) Math.toDegrees(Math.atan(MeleeHold.across(turned, reach)) - Math.atan(bat * share));
                    check(Math.abs(off) < 0.5f, "way " + way + " reaching " + reach + " lands its head between a bat's line and the line ahead: " + off + " degrees off");
                }
            }
        }
        // The game's sledgehammer held at the end of its handle: without the turn its head lands well left of a bat's.
        float[] pose = new float[ViewModelMath.HOLD];
        ViewModelMath.attack(ViewModelMath.SWUNG, 1f, 0f, false, rest, pose);
        float sledge = 0.9f, unturned = (float) Math.toDegrees(Math.atan(MeleeHold.across(pose, sledge)) - Math.atan(MeleeHold.across(pose, MeleeHold.BAT_REACH)));
        check(unturned < -5f && MeleeHold.aimed(ViewModelMath.SWUNG, sledge) > 0f, "a sledgehammer's swing is turned to the right to land on what it hits: unturned " + unturned + " degrees");
    }
}
