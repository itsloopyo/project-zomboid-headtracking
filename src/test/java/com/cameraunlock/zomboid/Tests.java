package com.cameraunlock.zomboid;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public final class Tests {
    public static int hookCalls;

    public static void hook() {
        hookCalls++;
    }

    public static void target() {}

    public static void main(String[] args) throws Throwable {
        cameraLooksAlongItsYaw();
        cameraPutsPlusYOnTheRightWhenFacingPlusX();
        cameraPutsHigherPointsHigher();
        cameraPitchLooksUp();
        cameraTurnsRightWithPositiveYaw();
        headYawTurnsTheViewRight();
        headPitchLooksUp();
        headRollTiltsTheViewLeft();
        worldYawKeepsTheHorizonLevel();
        flightLeavesTheOverheadCameraAndLandsOnTheTrackedView();
        flightTakesItsTimeWhateverTheFrames();
        flightMixesThePicturesOnlyWhileTheCameraIsStill();
        projectionAgreesWithTheViewMatrix();
        reticleStaysCentredUnderRollAndMovesStraightUnderPitch();
        reticleFollowsThePointWhenTheEyeLeans();
        reticleFollowsTheFieldOfView();
        nearPlaneKeepsItsCornersInsideTheLeanSkin();
        fieldOfViewSetIsTheOneDrawnWith();
        lightLeadsTheHeadsYawAndIgnoresTheRest();
        packetIsParsedAndValidated();
        receiverTakesOnlyChangedPosesAndReadsLocality();
        filterKeepsThePositionInsideItsLimits();
        filterSmoothsARemoteConnectionAndTurnsTheShortWay();
        filterEasesBackWhenSamplesStop();
        leanClampTightensAtOnceAndReleasesSlowly();
        gridTraceFindsTheFirstBlockedEdge();
        CabinTests.run();
        AimTests.run();
        ConfigTests.run();
        ArmTests.run();
        BufferTests.run();
        LinkTests.run();
        shotStopsOnACharacter();
        receiverRefusesAPoseNoTrackerSends();
        windowIsCentredInItsWorkArea();
        insertAfterAddsTheHookOnce();
        insertAfterRejectsAMissingCall();
        insertAfterRejectsTwoCalls();
        insertAfterEachAddsTheHookAfterEveryCall();
        returnedBooleanGoesThroughTheFilter();
        staticIntFieldIsRequiredAsDeclared();
        attackHoldsTheAimForAShortTail();
        zoomScalesWhatMovesAcrossThePicture();
        fieldOfViewEasesToTheAimedZoomAndBack();
        firstPersonKeysReplaceTheStockOnes();
        pointerComesOutForTheInventoryKeyAPanelAndAHeldButton();
        transformerHasNoGapOnceEveryPatchIsIn();
        exceptionTheGameCaughtFromTheModIsWrittenOnce();
        hookIsGivenAStaticMethodsFirstArgument();
        transformerLeavesAClassItCannotPatchAloneAndNamesTheGap();
        transformerNamesAClassTheGameDoesNotHave();
        floorFaceShowsTheSpritesDiamond();
        wallFaceIsClippedToTheTrimmedSprite();
        faceOutsideTheTrimmedSpriteAddsNothing();
        uprightSpriteStandsOnItsAnchor();
        smoothNormalsAgreeAcrossASeam();
        partsAreToldByTheirNames();
        textureRunsDownAMeshTheSameEitherSideOfASeam();
        fringeThinsOutWithDistance();
        fineSkinFileIsNamedAfterTheGamesTexture();
        hookBeforeAReturnGetsTheResultAndTheFirstArgument();
        packedModelIsReadBack();
        brokenModelFilesAreRefused();
        quarterTurnsCarryAModelsFrontRoundTheCompass();
        modelsAreLaidOutOneAfterAnotherForTheGraphicsCard();
        pieceIsPackedAsThePictureIsToldIt();
        pieceStandsWhereTheModelTurnedScaledAndMovedIs();
        pieceCarriesTheLightOfItsSquaresCorners();
        pieceIsHeldByItsBall();
        paintingOnAWallIsFoundWhereItHangs();
        spriteDrawnForAnotherWallIsNoPainting();
        pictureLiesOverItsFrameAsTheSpritePaintsIt();
        frameIsChosenByThePicturesShape();
        jarHoldsEveryModelStoodOnTheFloor();
        heldWeaponPointsAheadWithItsBarrelOnTheLineAsked();
        heldWeaponKeepsItsShapeAndTheGamesMirror();
        weaponWithoutAMuzzleIsHeldByItsGrip();
        weaponWithNoFrameIsNotHeld();
        weaponTurnsAboutItsGrip();
        easingCoversAllButOneOverEInItsConstantAtAnyFrameRate();
        recoilIsOverInAHundredAndFiftyMilliseconds();
        stepBobsTheWeaponDownAndAcross();
        flashIsBrightestAtTheShotAndGoneAfterItsTime();
        elbowKeepsBothBonesTheirLengthsAndDropsTheWayThePolePoints();
        pumpGoesBackAndComesForwardAgain();
        armTooShortToReachBringsItsShoulderWithIt();
        armWithNoWayForItsElbowToDropHasNone();
        swingDrawsBackSweepsAcrossAndComesBackToWhereItStarted();
        swingMovesWithoutAJump();
        raiseLeavesTheStraightLineOnlyBetweenItsEnds();
        meshSaysWhereItsWristAndElbowAreAndWhatItsSleeveWears();
        System.out.println("all tests passed");
    }

    private static float apart(float[] a, float[] b) {
        return (float) Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    private static void pumpGoesBackAndComesForwardAgain() {
        check(ViewModel.pumpAt(0f) == 0f && ViewModel.pumpAt(0.1f) == 0f, "the hand is still while the shot kicks");
        check(Math.abs(ViewModel.pumpAt(0.3f) - 1f) < 1e-4f, "the fore-end is right back at the end of the pull");
        check(ViewModel.pumpAt(0.46f) < 1e-4f && ViewModel.pumpAt(2f) == 0f, "and forward again after the shove");
        float last = 0f;
        for (float t = 0.16f; t <= 0.3f; t += 0.01f) {
            check(ViewModel.pumpAt(t) >= last - 1e-6f, "the pull only goes back");
            last = ViewModel.pumpAt(t);
        }
    }

    private static void elbowKeepsBothBonesTheirLengthsAndDropsTheWayThePolePoints() {
        float[] rightShoulder = {0.2f, -0.26f, 0.05f}, leftShoulder = {-0.2f, -0.26f, 0.05f};
        float[] rightPole = {0.45f, -1f, 0.1f}, leftPole = {-0.45f, -1f, 0.1f};
        float[] shoulder = rightShoulder.clone(), wrist = {0.17f, -0.2f, -0.3f}, elbow = new float[3];
        check(ViewModelMath.elbow(shoulder, wrist, 0.3f, 0.27f, rightPole, elbow), "an arm within reach has an elbow");
        check(apart(shoulder, rightShoulder) < 1e-5f, "its shoulder stays where it is");
        check(near(apart(shoulder, elbow), 0.3f) && near(apart(elbow, wrist), 0.27f), "both bones are their own lengths");
        float alongLine = ((elbow[2] - shoulder[2]) / (wrist[2] - shoulder[2]));
        float lineY = shoulder[1] + (wrist[1] - shoulder[1]) * alongLine, lineX = shoulder[0] + (wrist[0] - shoulder[0]) * alongLine;
        check(elbow[1] < lineY - 0.05f, "the elbow is below the line from shoulder to wrist");
        check(elbow[0] > lineX, "and the right elbow is out to the right of it");
        float[] left = leftShoulder.clone(), leftWrist = {-0.17f, -0.2f, -0.3f}, leftElbow = new float[3];
        check(ViewModelMath.elbow(left, leftWrist, 0.3f, 0.27f, leftPole, leftElbow), "the left arm has one too");
        check(near(leftElbow[0], -elbow[0]) && near(leftElbow[1], elbow[1]) && near(leftElbow[2], elbow[2]), "the mirror of the right arm's");
    }

    private static void armTooShortToReachBringsItsShoulderWithIt() {
        float[] shoulder = ViewModel.LEFT_SHOULDER.clone(), wrist = {0.1f, -0.12f, -0.8f}, elbow = new float[3];
        check(ViewModelMath.elbow(shoulder, wrist, 0.3f, 0.27f, ViewModel.LEFT_ELBOW, elbow), "a wrist out of reach still has an arm");
        check(near(apart(shoulder, elbow), 0.3f) && near(apart(elbow, wrist), 0.27f), "whose bones are their own lengths");
        check(near(apart(shoulder, wrist), 0.57f * ViewModelMath.STRAIGHTEST), "drawn out as far as an arm goes");
        float[] was = ViewModel.LEFT_SHOULDER;
        float cross = (shoulder[1] - wrist[1]) * (was[2] - wrist[2]) - (shoulder[2] - wrist[2]) * (was[1] - wrist[1]);
        check(Math.abs(cross) < 1e-4f && shoulder[2] < was[2], "the shoulder has come forward along the line to the wrist");
        float[] close = ViewModel.RIGHT_SHOULDER.clone(), touching = {0.2f, -0.26f, 0.04f};
        check(ViewModelMath.elbow(close, touching, 0.3f, 0.27f, ViewModel.RIGHT_ELBOW, elbow), "a wrist at the shoulder has an arm");
        check(near(apart(close, elbow), 0.3f) && near(apart(elbow, touching), 0.27f), "which is not folded shorter than its bones");
    }

    private static void armWithNoWayForItsElbowToDropHasNone() {
        float[] shoulder = {0f, 0f, 0f}, elbow = {7f, 7f, 7f};
        check(!ViewModelMath.elbow(shoulder, new float[] {0f, -0.4f, 0f}, 0.3f, 0.27f, new float[] {0f, -1f, 0f}, elbow), "a pole along the arm places no elbow");
        check(!ViewModelMath.elbow(shoulder, new float[] {0f, 0f, 0f}, 0.3f, 0.27f, new float[] {0f, -1f, 0f}, elbow), "nor does a wrist at the shoulder itself");
        check(elbow[0] == 7f && shoulder[1] == 0f, "and nothing is changed");
    }

    private static void swingDrawsBackSweepsAcrossAndComesBackToWhereItStarted() {
        float[] at = new float[6];
        ViewModelMath.swing(0f, at);
        for (float part : at) {
            check(part == 0f, "a swing starts from where the weapon is held");
        }
        ViewModelMath.swing(1f, at);
        for (float part : at) {
            check(part == 0f, "and ends there");
        }
        ViewModelMath.swing(ViewModelMath.SWING_WOUND, at);
        check(at[1] > 0.3f && at[3] > 0.03f && at[5] > 0.03f, "drawn back it is turned and moved to the right, and towards the viewer");
        float wasRight = at[3], wasYaw = at[1];
        for (float progress = ViewModelMath.SWING_WOUND + 0.02f; progress <= ViewModelMath.SWING_SWEPT; progress += 0.02f) {
            ViewModelMath.swing(progress, at);
            check(at[3] < wasRight && at[1] < wasYaw, "the sweep goes from right to left all the way");
            wasRight = at[3];
            wasYaw = at[1];
        }
        ViewModelMath.swing(ViewModelMath.SWING_SWEPT, at);
        check(at[1] < -1f && at[3] < -0.25f && at[5] < -0.05f, "swept it is turned and moved well to the left, and away from the viewer");
        check(at[0] < 0f, "with its end brought down");
    }

    private static void swingMovesWithoutAJump() {
        float[] at = new float[6], before = new float[6];
        for (int step = 1; step <= 500; step++) {
            ViewModelMath.swing(step / 500f, at);
            for (int part = 0; part < 6; part++) {
                check(Math.abs(at[part] - before[part]) < 0.02f, "no part of a swing moves far in a five-hundredth of it");
            }
            System.arraycopy(at, 0, before, 0, 6);
        }
        ViewModelMath.swing(-3f, at);
        check(at[1] == 0f, "before a swing there is none");
        ViewModelMath.swing(3f, at);
        check(at[1] == 0f, "nor after it");
    }

    private static void raiseLeavesTheStraightLineOnlyBetweenItsEnds() {
        check(ViewModelMath.arc(0f) == 0f && Math.abs(ViewModelMath.arc(1f)) < 1e-6f, "at the hip and at the sights the weapon is on the line");
        check(near(ViewModelMath.arc(0.5f), 1f), "and furthest off it half way");
        check(ViewModelMath.smooth(0f) == 0f && ViewModelMath.smooth(1f) == 1f && near(ViewModelMath.smooth(0.5f), 0.5f), "the raise starts and ends at rest");
        check(ViewModelMath.smooth(0.1f) < 0.1f && ViewModelMath.smooth(0.9f) > 0.9f, "slow at both ends");
    }

    /**
     * A right arm of four square rings along x: one of the upper arm at 0, two of the forearm
     * at 1 and 2, one of the hand at 3. Each ring's texture places are its place along the arm.
     */
    private static void meshSaysWhereItsWristAndElbowAreAndWhatItsSleeveWears() {
        int rings = 4;
        float[] places = new float[rings * 4 * 3], uvs = new float[rings * 4 * 2];
        int[] kinds = new int[rings * 4];
        int[] ringKind = {CharacterDraw.UPPER, CharacterDraw.FORE, CharacterDraw.FORE, CharacterDraw.HAND};
        for (int ring = 0; ring < rings; ring++) {
            for (int corner = 0; corner < 4; corner++) {
                int vertex = ring * 4 + corner;
                places[vertex * 3] = ring;
                places[vertex * 3 + 1] = corner == 0 || corner == 3 ? -0.1f : 0.1f;
                places[vertex * 3 + 2] = corner < 2 ? -0.1f : 0.1f;
                uvs[vertex * 2] = ring / 3f;
                uvs[vertex * 2 + 1] = corner / 4f;
                kinds[vertex] = ringKind[ring];
            }
        }
        int[] elements = new int[(rings - 1) * 4 * 6];
        int at = 0;
        for (int ring = 0; ring + 1 < rings; ring++) {
            for (int corner = 0; corner < 4; corner++) {
                int here = ring * 4 + corner, next = ring * 4 + (corner + 1) % 4;
                elements[at++] = here;
                elements[at++] = next;
                elements[at++] = here + 4;
                elements[at++] = next;
                elements[at++] = next + 4;
                elements[at++] = here + 4;
            }
        }
        Limb arm = Limb.read(places, uvs, kinds, elements, elements.length, 0);
        check(near(arm.wrist[0], 2.5f) && near(arm.wrist[1], 0f) && near(arm.wrist[2], 0f), "the wrist is midway between the hand's ring and the forearm's last");
        check(near(arm.elbow[0], 0.5f) && near(arm.elbow[1], 0f), "the elbow midway between the upper arm's ring and the forearm's first");
        check(near(arm.palm[0], 3f) && near(arm.palm[1], 0f) && near(arm.palm[2], 0f), "the middle of the hand is the middle of its own ring");
        check(arm.foreUv.length == Limb.SAMPLES * 2 && arm.upperUv.length == Limb.SAMPLES * 2, "each length of the arm has its samples");
        for (int sample = 0; sample < Limb.SAMPLES; sample++) {
            check(arm.foreUv[sample * 2] > 1f / 3f && arm.foreUv[sample * 2] < 2f / 3f, "the forearm is read between its own two rings");
            check(arm.upperUv[sample * 2] < 1f / 3f, "the upper arm on its own side of the elbow");
        }
        Limb other = Limb.read(places, uvs, kinds, elements, elements.length, 1);
        check(other.wrist == null && other.elbow == null && other.foreUv == null && other.upperUv == null, "a mesh with nothing of the left arm says nothing of it");
    }

    /** A weapon posed off every axis: its grip, the way it points, its top, and its muzzle a little above the grip's line. */
    private static final float[] POSED_GRIP = {0.3f, 0.9f, -0.2f}, POSED_FORWARD = {0.6f, -1.2f, 0.4f}, POSED_TOP = {0.5f, 0.5f, 1f};

    private static float[] posedMuzzle() {
        float length = (float) Math.sqrt(0.36f + 1.44f + 0.16f);
        float[] muzzle = new float[3];
        for (int i = 0; i < 3; i++) {
            muzzle[i] = POSED_GRIP[i] + POSED_FORWARD[i] / length * 0.4f + POSED_TOP[i] * 0.03f;
        }
        return muzzle;
    }

    private static float[] placed(float[] matrix, float[] point) {
        float[] out = new float[3];
        ViewModelMath.place(matrix, point[0], point[1], point[2], out);
        return out;
    }

    private static void heldWeaponPointsAheadWithItsBarrelOnTheLineAsked() {
        float[] matrix = new float[16];
        float[] muzzle = posedMuzzle();
        check(ViewModelMath.hold(POSED_GRIP, POSED_FORWARD, POSED_TOP, muzzle, 1.5f, 0.17f, -0.16f, 0.42f, matrix), "a posed weapon is held");
        float[] grip = placed(matrix, POSED_GRIP), end = placed(matrix, muzzle);
        check(near(grip[2], -0.42f), "the grip is the distance asked ahead of the eye");
        check(near(end[0], 0.17f) && near(end[1], -0.16f), "the muzzle is on the line asked");
        check(end[2] < grip[2] - 0.5f, "and further ahead than the grip, by the barrel's length at the scale drawn");
        float[] further = placed(matrix, new float[] {muzzle[0] + POSED_FORWARD[0], muzzle[1] + POSED_FORWARD[1], muzzle[2] + POSED_FORWARD[2]});
        check(near(further[0], end[0]) && near(further[1], end[1]) && further[2] < end[2], "the barrel points straight ahead");
        check(grip[1] < end[1], "the grip hangs below the barrel");
    }

    private static void heldWeaponKeepsItsShapeAndTheGamesMirror() {
        float[] matrix = new float[16];
        ViewModelMath.hold(POSED_GRIP, POSED_FORWARD, POSED_TOP, posedMuzzle(), 1.5f, 0f, -0.06f, 0.36f, matrix);
        float[] a = {0.1f, 0.2f, 0.3f}, b = {-0.4f, 0.7f, 0.1f};
        float[] heldA = placed(matrix, a), heldB = placed(matrix, b);
        float before = (float) Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
        float after = (float) Math.sqrt((heldA[0] - heldB[0]) * (heldA[0] - heldB[0]) + (heldA[1] - heldB[1]) * (heldA[1] - heldB[1])
                + (heldA[2] - heldB[2]) * (heldA[2] - heldB[2]));
        check(near(after, before * 1.5f), "two points of the model stay as far apart, at the scale drawn");
        float determinant = matrix[0] * (matrix[5] * matrix[10] - matrix[9] * matrix[6]) - matrix[4] * (matrix[1] * matrix[10] - matrix[9] * matrix[2])
                + matrix[8] * (matrix[1] * matrix[6] - matrix[5] * matrix[2]);
        check(near(determinant, -1.5f * 1.5f * 1.5f), "the model is mirrored, as the game draws it");
    }

    private static void weaponWithoutAMuzzleIsHeldByItsGrip() {
        float[] matrix = new float[16];
        check(ViewModelMath.hold(POSED_GRIP, POSED_FORWARD, POSED_TOP, null, 1.5f, 0.22f, -0.25f, 0.4f, matrix), "a weapon with no muzzle is held");
        float[] grip = placed(matrix, POSED_GRIP);
        check(near(grip[0], 0.22f) && near(grip[1], -0.25f) && near(grip[2], -0.4f), "its grip is at the place asked");
        float[] above = placed(matrix, new float[] {POSED_GRIP[0] + POSED_TOP[0], POSED_GRIP[1] + POSED_TOP[1], POSED_GRIP[2] + POSED_TOP[2]});
        check(above[1] > grip[1], "its top is up");
    }

    private static void weaponWithNoFrameIsNotHeld() {
        float[] matrix = new float[16];
        check(!ViewModelMath.hold(POSED_GRIP, new float[3], POSED_TOP, null, 1.5f, 0f, 0f, 0.4f, matrix), "a weapon that points nowhere is not held");
        check(!ViewModelMath.hold(POSED_GRIP, POSED_FORWARD, POSED_FORWARD, null, 1.5f, 0f, 0f, 0.4f, matrix), "nor one whose top is along its barrel");
    }

    private static void weaponTurnsAboutItsGrip() {
        float[] matrix = new float[16];
        ViewModelMath.hold(POSED_GRIP, POSED_FORWARD, POSED_TOP, null, 1.5f, 0.2f, -0.2f, 0.4f, matrix);
        float[] ahead = {POSED_GRIP[0] + POSED_FORWARD[0], POSED_GRIP[1] + POSED_FORWARD[1], POSED_GRIP[2] + POSED_FORWARD[2]};
        float[] raised = matrix.clone();
        ViewModelMath.turn(raised, 0.3f, 0f, 0f, 0.2f, -0.2f, -0.4f, 0f, 0f, 0f);
        float[] grip = placed(raised, POSED_GRIP);
        check(near(grip[0], 0.2f) && near(grip[1], -0.2f) && near(grip[2], -0.4f), "the grip stays where it is");
        check(placed(raised, ahead)[1] > placed(matrix, ahead)[1] + 0.1f, "positive pitch raises the muzzle");
        float[] swung = matrix.clone();
        ViewModelMath.turn(swung, 0f, 0.3f, 0f, 0.2f, -0.2f, -0.4f, 0f, 0f, 0f);
        check(placed(swung, ahead)[0] > placed(matrix, ahead)[0] + 0.1f, "positive yaw swings it to the right");
        float[] kicked = matrix.clone();
        ViewModelMath.turn(kicked, 0f, 0f, 0f, 0.2f, -0.2f, -0.4f, 0.01f, 0.02f, 0.03f);
        grip = placed(kicked, POSED_GRIP);
        check(near(grip[0], 0.21f) && near(grip[1], -0.18f) && near(grip[2], -0.37f), "a move carries the grip with it");
    }

    private static void easingCoversAllButOneOverEInItsConstantAtAnyFrameRate() {
        float once = ViewModelMath.ease(0f, 1f, 0.07f, 0.07f);
        check(near(once, 1f - 1f / (float) Math.E), "one time constant covers all but 1/e of the way");
        float stepped = 0f;
        for (int i = 0; i < 7; i++) {
            stepped = ViewModelMath.ease(stepped, 1f, 0.01f, 0.07f);
        }
        check(near(stepped, once), "in seven frames or in one");
        check(near(ViewModelMath.turned((float) Math.toRadians(170), (float) Math.toRadians(-170)), (float) Math.toRadians(20)),
                "a turn past half a circle is taken the short way");
    }

    private static void recoilIsOverInAHundredAndFiftyMilliseconds() {
        float kick = 1f;
        for (int frame = 0; frame < 9; frame++) {
            kick = ViewModelMath.ease(kick, 0f, 0.15f / 9f, ViewModel.RECOIL_SECONDS);
        }
        check(kick < 0.06f && kick > 0f, "all but a twentieth of a kick has gone 150 ms after the shot");
        check(ViewModelMath.ease(1f, 0f, 0.016f, ViewModel.RECOIL_SECONDS) > 0.7f, "and most of it is still there a frame after");
    }

    private static void stepBobsTheWeaponDownAndAcross() {
        float[] at = new float[2];
        ViewModelMath.bob(1.3f, 0f, at);
        check(at[0] == 0f && at[1] == 0f, "standing still the weapon is still");
        ViewModelMath.bob((float) (Math.PI / 2), 1f, at);
        check(near(at[0], ViewModelMath.BOB_ACROSS) && near(at[1], -ViewModelMath.BOB_DOWN), "mid step it is across and down");
        ViewModelMath.bob((float) (3 * Math.PI / 2), 1f, at);
        check(near(at[0], -ViewModelMath.BOB_ACROSS) && near(at[1], -ViewModelMath.BOB_DOWN), "the next step it is across the other way, and down again");
    }

    private static void flashIsBrightestAtTheShotAndGoneAfterItsTime() {
        check(MuzzleFlash.left(0f) == 1f, "a flash is whole at the shot");
        check(near(MuzzleFlash.left(MuzzleFlash.SECONDS / 2f), 0.5f), "half gone half way");
        check(MuzzleFlash.left(MuzzleFlash.SECONDS) == 0f && MuzzleFlash.left(1f) == 0f, "and gone after its time");
        float[] light = {9f, 9f, 9f, 9f, 9f, 9f, 9f};
        MuzzleFlash.light(light);
        check(light[0] == 0f && light[3] == 0f && light[4] > 0f, "with no weapon drawn there is no light, only its colour");
        plantsStandWhereTheyStoodAndAreNeverTooMany();
        fallenLeavesAreAsDeepFromEitherChunkThatSharesACorner();
        tuftCarriesHowWellItsGrassDoes();
        boleIsClosedAboveItsFoot();
        barkIsAsLargeRoundABoleAsUpIt();
        ivyKeepsToTheBoleAndSaysHowFarUpItIs();
        boulderIsClosed();
        fernGrowsFromItsRoot();
        litterShowsAsMuchAsItsCornersSay();
        System.out.println("all tests passed");
    }

    private static void plantsStandWhereTheyStoodAndAreNeverTooMany() {
        int flowered = 0, bare = 0, weeds = 0, ferned = 0;
        for (int x = -40; x < 40; x++) {
            for (int y = 10900; y < 10980; y++) {
                int flowers = Flora.flowers(x, y);
                check(flowers == Flora.flowers(x, y), "a square has the same flowers every time it is built");
                check(flowers >= 0 && flowers <= Flora.FLOWERS_MOST, "a square never has more flowers than the most");
                for (int flower = 0; flower < flowers; flower++) {
                    int kind = Flora.flowerKind(x, y, flower);
                    check(kind == Flora.flowerKind(x, y, flower) && kind >= Flora.WHITE && kind <= Flora.PURPLE, "a flower is one of the three, and the same one");
                }
                flowered += flowers > 0 ? 1 : 0;
                bare += flowers == 0 ? 1 : 0;
                weeds += Flora.weed(x, y) ? 1 : 0;
                check(Flora.ferns(x, y, 0f) == 0 && Flora.fallen(x, y, 0f) == 0, "open ground has no ferns and no fallen leaves");
                for (float wooded = 0.1f; wooded <= 1f; wooded += 0.3f) {
                    int ferns = Flora.ferns(x, y, wooded), fallen = Flora.fallen(x, y, wooded);
                    check(ferns == Flora.ferns(x, y, wooded) && fallen == Flora.fallen(x, y, wooded), "a wooded square has the same ferns and leaves every time");
                    check(ferns >= 0 && ferns <= Flora.FERNS_MOST && fallen >= 0 && fallen <= Flora.FALLEN_MOST, "and never more than the most");
                }
                check(Flora.ferns(x, y, 1f) >= Flora.ferns(x, y, 0.4f), "deeper in a wood there are no fewer ferns");
                ferned += Flora.ferns(x, y, 1f) > 0 ? 1 : 0;
                check(Flora.ivied(x, y) == Flora.ivied(x, y), "a tree has ivy every time or never");
            }
        }
        check(flowered > 500 && bare > 3000, "flowers are on some squares and most have none");
        check(weeds > 300 && weeds < 1500, "a weed grows on about one square in eight");
        check(ferned > 3000 && ferned < 5000, "ferns grow on over half the squares of a wood, and not on all");
    }

    private static void fallenLeavesAreAsDeepFromEitherChunkThatSharesACorner() {
        int tiles = Terrain.CHUNK_TILES, span = tiles + 2 * Flora.WOOD_RING, across = tiles + 1;
        float[] west = new float[across * across], east = new float[across * across], none = new float[across * across];
        Flora.wooded(woodAround(0, 0, span, 5), tiles, west);
        Flora.wooded(woodAround(tiles, 0, span, 5), tiles, east);
        boolean some = false, whole = false;
        for (int j = 0; j < across; j++) {
            check(near(west[tiles * across + j], east[j]), "the corners two chunks share are as deep in leaves from either");
        }
        for (float depth : west) {
            check(depth >= 0f && depth <= 1f, "leaves are no deeper than all the way");
            some |= depth > 0f && depth < 1f;
        }
        check(some, "a thin wood has corners part way deep in leaves");
        Flora.wooded(new boolean[span * span], tiles, none);
        for (float depth : none) {
            check(depth == 0f, "ground with no tree near has no fallen leaves");
        }
        Flora.wooded(woodAround(0, 0, span, 1), tiles, west);
        for (float depth : west) {
            whole |= depth == 1f;
        }
        check(whole, "a wood with a tree on every square is all leaves");
        boolean[] lone = new boolean[span * span];
        lone[(4 + Flora.WOOD_RING) * span + 4 + Flora.WOOD_RING] = true;
        Flora.wooded(lone, tiles, west);
        check(west[4 * across + 4] > 0.3f && west[4 * across + 4] < 1f, "one tree has a scatter of leaves at its foot");
        check(west[0] == 0f && west[8 * across + 4] == 0f, "and none four squares away");
    }

    /** Which squares of a chunk at (chunkX, chunkY) and of the ring round it hold a tree, in a land with one on every so many squares. */
    private static boolean[] woodAround(int chunkX, int chunkY, int span, int every) {
        boolean[] trees = new boolean[span * span];
        for (int i = 0; i < span; i++) {
            for (int j = 0; j < span; j++) {
                trees[i * span + j] = Math.floorMod(Flora.mix(chunkX + i - Flora.WOOD_RING, chunkY + j - Flora.WOOD_RING, 3), every) == 0;
            }
        }
        return trees;
    }

    private static void tuftCarriesHowWellItsGrassDoes() {
        for (float vigour : new float[] {0f, 0.35f, 1f}) {
            SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
            new SpriteGeometry().addTuft(mesh, wholeFrame(), 3, 4, 0.5f, 0.5f, 0.6f, 0.8f, 0.3f, 0.4f, SpriteGeometry.LONG_GRASS, vigour);
            check(mesh.triangles() == 2, "a tuft is two triangles");
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                float[] f = mesh.vertices;
                float length = (float) Math.sqrt(f[at + 9] * f[at + 9] + f[at + 10] * f[at + 10] + f[at + 11] * f[at + 11]);
                check(near(f[at + 11] / length, SpriteGeometry.TUFT_UP_LEAST + SpriteGeometry.TUFT_UP_SPAN * vigour),
                        "a tuft's normal leans up by how well its grass does, at its root and its top alike");
                check(near(f[at + 9] * 0.6f + f[at + 10] * 0.8f, 0f), "and still looks square across the tuft");
            }
        }
    }

    /** How many triangles of a mesh meet along each of its edges, by the two ends of the edge. */
    private static HashMap<String, Integer> edges(SpriteGeometry.Mesh mesh) {
        HashMap<String, Integer> edges = new HashMap<>();
        int triangle = 3 * SpriteGeometry.VERTEX_FLOATS;
        for (int at = 0; at < mesh.floats; at += triangle) {
            for (int corner = 0; corner < 3; corner++) {
                String from = place(mesh, at + corner * SpriteGeometry.VERTEX_FLOATS);
                String to = place(mesh, at + (corner + 1) % 3 * SpriteGeometry.VERTEX_FLOATS);
                edges.merge(from.compareTo(to) < 0 ? from + " " + to : to + " " + from, 1, Integer::sum);
            }
        }
        return edges;
    }

    private static String place(SpriteGeometry.Mesh mesh, int at) {
        return Math.round(mesh.vertices[at] * 10000f) + "," + Math.round(mesh.vertices[at + 1] * 10000f) + "," + Math.round(mesh.vertices[at + 2] * 10000f);
    }

    private static void boleIsClosedAboveItsFoot() {
        SpriteGeometry.Frame frame = wholeFrame();
        for (int seed : new int[] {7, 0x12345678, -99}) {
            SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
            int triangles = new Flora().addTrunk(mesh, 10.5f, 20.5f, 1f, 0.15f, 2f, true, Flora.BARK_PINE, seed, frame);
            check(triangles == mesh.triangles() && triangles == 3 * Flora.TRUNK_SIDES * 2 + Flora.TRUNK_SIDES, "a bole near the eye is three bands and a point");
            int open = 0;
            for (Map.Entry<String, Integer> edge : edges(mesh).entrySet()) {
                check(edge.getValue() <= 2, "no edge of a bole has three faces on it");
                if (edge.getValue() == 1) {
                    open++;
                    String[] ends = edge.getKey().split("[ ,]");
                    check(Integer.parseInt(ends[2]) < 10000 && Integer.parseInt(ends[5]) < 10000, "a bole is open only round its foot, under the ground");
                }
            }
            check(open == Flora.TRUNK_SIDES, "and its foot is one ring");
            float widest = 0f;
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                float away = (float) Math.hypot(mesh.vertices[at] - 10.5f, mesh.vertices[at + 1] - 20.5f);
                widest = Math.max(widest, away);
                if (mesh.vertices[at + 2] > 1.5f) {
                    check(away <= 0.15f + 1e-4f, "a bole is no thicker than its radius above its roots");
                }
                check(mesh.vertices[at + 2] <= 1f + 2f * (1f + Flora.SPIRE) + 1e-4f, "and ends in a point that far above its height");
            }
            check(widest > 0.28f && widest < 0.4f, "its longest root runs out to about twice its radius or a little more");
        }
        SpriteGeometry.Mesh far = new SpriteGeometry.Mesh();
        check(new Flora().addTrunk(far, 10.5f, 20.5f, 1f, 0.15f, 2f, false, Flora.BARK_OAK, 7, frame) == Flora.TRUNK_SIDES_FAR * 2 && far.triangles() == Flora.TRUNK_SIDES_FAR * 2,
                "a bole far off is one band of six sides");
    }

    private static void barkIsAsLargeRoundABoleAsUpIt() {
        SpriteGeometry.Frame frame = wholeFrame();
        for (float radius : new float[] {0.05f, 0.11f, 0.2f, 0.4f}) {
            for (int seed : new int[] {7, 0x12345678, -99}) {
                SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
                new Flora().addTrunk(mesh, 10.5f, 20.5f, 1f, radius, 2f, false, Flora.BARK_HEMLOCK, seed, frame);
                float[] f = mesh.vertices;
                float steps = (float) Math.floor(f[3] / Flora.BARK_ACROSS);
                float wraps = (steps % Flora.BARK_WRAPS_MOST + 1f) / 4f;
                float least = Float.MAX_VALUE, most = -Float.MAX_VALUE, low = Float.MAX_VALUE, high = -Float.MAX_VALUE;
                for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                    check(Math.floor(f[at + 3] / Flora.BARK_ACROSS) == steps, "every vertex of a bole says the same of how its bark is laid");
                    check(Math.floor(f[at + 4] / Flora.BARK_UP) == Flora.BARK_HEMLOCK, "and which bark it is");
                    least = Math.min(least, f[at + 3] - Flora.BARK_ACROSS * steps);
                    most = Math.max(most, f[at + 3] - Flora.BARK_ACROSS * steps);
                    low = Math.min(low, f[at + 4] - Flora.BARK_UP * Flora.BARK_HEMLOCK);
                    high = Math.max(high, f[at + 4] - Flora.BARK_UP * Flora.BARK_HEMLOCK);
                }
                check(near(least, 0f) && near(most, wraps), "the bark goes round a bole as many quarters of its picture as the bole says");
                float span = 2f * (float) Math.PI * radius / wraps;
                check(span > 0.35f && span < 0.65f, "a picture of bark covers about as much of a sapling as of a great tree");
                // A bole far off is one band, from 1.1 of its radius under the ground to 0.78 of it at its top.
                float run = (float) Math.hypot(2f + 0.12f, radius * (1.1f - 0.78f));
                check(Math.abs((high - low) * span - run) < 0.01f, "and is as large up the bole as round it");
            }
        }
        check(Flora.barkOf("e_americanholly_1_3", 7) == Flora.BARK_HOLLY && Flora.barkOf("e_riverbirch_1_0", 7) == Flora.BARK_BIRCH
                && Flora.barkOf("e_virginiapine_1_2", 7) == Flora.BARK_PINE && Flora.barkOf("e_canadianhemlock_1_2", 7) == Flora.BARK_HEMLOCK,
                "a tree has the bark of its species");
        boolean[] seen = new boolean[8];
        for (int seed = 0; seed < 4096; seed += 37) {
            seen[Flora.barkOf("e_americanlinden_1_1", Flora.mix(seed, 3, 81))] = true;
        }
        check(seen[Flora.BARK_OAK] && seen[Flora.BARK_MAPLE] && seen[Flora.BARK_LINDEN], "and where its species has none of its own, one of several");
    }

    private static void ivyKeepsToTheBoleAndSaysHowFarUpItIs() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        int triangles = new Flora().addIvy(mesh, 10.5f, 20.5f, 1f, 0.15f, 2f, 1.4f, 7, wholeFrame());
        check(triangles == mesh.triangles() && triangles == 2 * Flora.TRUNK_SIDES * 2, "ivy is two bands round the bole");
        boolean foot = false, top = false;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float[] f = mesh.vertices;
            float away = (float) Math.hypot(f[at] - 10.5f, f[at + 1] - 20.5f);
            check(f[at + 2] <= 2.4f + 1e-4f && f[at + 2] > 1f, "ivy starts above the ground and stops where it is told to");
            check(away > 0.15f * 0.78f && away < 0.3f, "and stands just off the bark, clear of the roots' ends");
            float up = (float) Math.sqrt(f[at + 9] * f[at + 9] + f[at + 10] * f[at + 10] + f[at + 11] * f[at + 11]) - 1f;
            check(up >= -1e-4f && up <= 1f + 1e-4f, "a vertex says how far up the ivy it is");
            foot |= near(up, 0f);
            top |= near(up, 1f) && near(f[at + 2], 2.4f);
        }
        check(foot && top, "from none at its foot to all at its top");
    }

    private static void boulderIsClosed() {
        for (int seed : new int[] {1, 99991, -5}) {
            SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
            int triangles = new Flora().addRock(mesh, 3.5f, 4.5f, 0.2f, 0.4f, 0.6f, true, seed, wholeFrame());
            check(triangles == 80 && mesh.triangles() == 80, "a boulder near the eye has eighty faces");
            for (int shared : edges(mesh).values()) {
                check(shared == 2, "every edge of a boulder has a face on each side");
            }
            boolean above = false;
            for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
                float[] f = mesh.vertices;
                check(Math.hypot(f[at] - 3.5f, f[at + 1] - 4.5f) < 0.4f * 1.2f, "a boulder keeps within its width, give or take its lumps");
                check(f[at + 2] < 0.2f + 0.6f * 1.2f, "and its height");
                above |= f[at + 2] > 0.2f + 0.4f;
            }
            check(above, "and stands most of that height");
        }
        SpriteGeometry.Mesh far = new SpriteGeometry.Mesh();
        check(new Flora().addRock(far, 3.5f, 4.5f, 0.2f, 0.4f, 0.6f, false, 1, wholeFrame()) == 20, "a boulder far off has twenty faces");
        for (int shared : edges(far).values()) {
            check(shared == 2, "and is closed too");
        }
    }

    private static void fernGrowsFromItsRoot() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        check(Flora.addFern(mesh, 2.5f, 3.5f, 0.1f, 0.5f, 6, 12345, wholeFrame()) == 24 && mesh.triangles() == 24, "a fern is four triangles a frond");
        boolean rooted = false;
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float[] f = mesh.vertices;
            check(Math.hypot(f[at] - 2.5f, f[at + 1] - 3.5f) < 0.5f * 1.1f * 1.4f && f[at + 2] >= 0.1f - 1e-4f && f[at + 2] < 0.1f + 0.5f * 1.1f,
                    "a frond reaches no further than its length and its width allow");
            check(f[at + 3] >= 0f && f[at + 3] <= 1f && f[at + 4] >= 0f && f[at + 4] <= 1f, "and shows its picture once");
            rooted |= near(f[at + 4], 0f) && near(f[at + 2], 0.1f);
        }
        check(rooted, "every frond starts on the ground");
    }

    private static void litterShowsAsMuchAsItsCornersSay() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        SpriteGeometry.Frame frame = wholeFrame();
        frame.light(0.5f, 0.5f, 0.5f);
        Flora.addLitter(mesh, 5, 6, new float[] {0f, 0.1f, 0.2f, 0.1f}, 0.01f, new float[] {1f, 0.25f, 0f, 0.5f}, frame);
        check(mesh.triangles() == 2, "litter over a square is two triangles");
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float[] f = mesh.vertices;
            float cover = 2f - (float) Math.sqrt(f[at + 9] * f[at + 9] + f[at + 10] * f[at + 10] + f[at + 11] * f[at + 11]);
            boolean east = near(f[at], 6f), south = near(f[at + 1], 7f);
            check(near(cover, east ? south ? 0f : 0.25f : south ? 0.5f : 1f), "each corner shows as much litter as it was given");
            check(near(f[at + 2], (east ? south ? 0.2f : 0.1f : south ? 0.1f : 0f) + 0.01f), "and lies just over the ground there");
            check(near(f[at + 8], Flora.LITTER * 32f), "and is marked as litter");
        }
    }

    /** One triangle called "wedge": a wooden corner on the floor, a cloth one a metre up and a metal one to the side. */
    private static ByteBuffer packedWedge() {
        return packedWedges("wedge");
    }

    /** The same triangle under each of the names, one model after another. */
    private static ByteBuffer packedWedges(String... names) {
        ByteBuffer out = ByteBuffer.allocate(256 * names.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put(new byte[] {'C', 'U', 'F', 'M'}).putInt(2).putInt(names.length);
        for (String name : names) {
            out.put((byte) name.length()).put(name.getBytes(StandardCharsets.US_ASCII));
            out.putFloat(-1f).putFloat(-2f).putFloat(0f).putFloat(1f).putFloat(2f).putFloat(1f);
            out.putInt(1);
            out.putFloat(-1f).putFloat(-2f).putFloat(0f).putFloat(0f).putFloat(-1f).putFloat(0f).put((byte) Models.WOOD).put((byte) 255);
            out.putFloat(0f).putFloat(2f).putFloat(1f).putFloat(0f).putFloat(0f).putFloat(1f).put((byte) Models.CLOTH).put((byte) 51);
            out.putFloat(1f).putFloat(0f).putFloat(0.5f).putFloat(1f).putFloat(0f).putFloat(0f).put((byte) Models.METAL).put((byte) 0);
        }
        return out.flip();
    }

    private static InputStream gzipped(ByteBuffer plain) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream packed = new GZIPOutputStream(bytes)) {
            packed.write(plain.array(), 0, plain.limit());
        }
        return new ByteArrayInputStream(bytes.toByteArray());
    }

    private static Models.Model wedge() throws IOException {
        return Models.read(gzipped(packedWedge())).get("wedge");
    }

    private static void packedModelIsReadBack() throws IOException {
        Map<String, Models.Model> models = Models.read(gzipped(packedWedge()));
        check(models.size() == 1 && models.containsKey("wedge"), "the file's one model is read under its name");
        Models.Model wedge = models.get("wedge");
        check(wedge.triangles() == 1, "it has the one triangle");
        check(near(wedge.width(), 2f) && near(wedge.depth(), 4f) && near(wedge.height(), 1f), "its bounds give its size");
        check(near(wedge.vertices[6], 0f) && near(wedge.vertices[7], 2f) && near(wedge.vertices[8], 1f), "the second vertex is where it was packed");
        check(near(wedge.vertices[11], 1f), "and looks up, as packed");
        check(wedge.surface[0] == Models.WOOD && wedge.surface[1] == Models.CLOTH && wedge.surface[2] == Models.METAL, "each vertex keeps its surface");
        check(wedge.first[Models.WOOD] == 0 && wedge.first[Models.DARK_WOOD] == 1 && wedge.first[Models.CLOTH] == 1 && wedge.first[Models.LINEN] == 2
                && wedge.first[Models.METAL] == 2 && wedge.first[Models.PAINT] == 3 && wedge.first[Models.SURFACES] == 3,
                "each surface's vertices are a run, an empty one for a surface the model has none of");
        check(near(wedge.reached[0], 1f) && near(wedge.reached[1], 0.2f) && near(wedge.reached[2], 0f), "and how much light reaches it, as a share");
    }

    private static void brokenModelFilesAreRefused() throws IOException {
        ByteBuffer otherFile = packedWedge();
        otherFile.put(0, (byte) 'X');
        refused(otherFile, "does not start with CUFM");
        ByteBuffer laterFormat = packedWedge();
        laterFormat.putInt(4, 3);
        refused(laterFormat, "format 3");
        ByteBuffer cutShort = packedWedge();
        cutShort.limit(cutShort.limit() - 10);
        refused(cutShort, "the file ends before that many");
        ByteBuffer unknownSurface = packedWedge();
        unknownSurface.put(unknownSurface.limit() - 2, (byte) Models.SURFACES);
        refused(unknownSurface, "surface " + Models.SURFACES);
        ByteBuffer shuffled = packedWedge();
        shuffled.put(shuffled.limit() - 2, (byte) Models.WOOD);
        refused(shuffled, "out of the order of their surfaces");
        ByteBuffer trailing = packedWedge();
        trailing.limit(trailing.limit() + 3);
        refused(trailing, "3 bytes follow the last model");
    }

    private static void refused(ByteBuffer plain, String why) throws IOException {
        try {
            Models.read(gzipped(plain));
        } catch (IOException e) {
            check(e.getMessage().contains(why), "a broken file of models is refused for what is wrong with it, got: " + e.getMessage());
            return;
        }
        throw new AssertionError("a file of models was read that should be refused: " + why);
    }

    private static void quarterTurnsCarryAModelsFrontRoundTheCompass() {
        float[] out = new float[2];
        // The map's x runs east and its y south. A model's front is its -y.
        float[][] fronts = {{0f, -1f}, {1f, 0f}, {0f, 1f}, {-1f, 0f}};
        for (int turns = 0; turns < 4; turns++) {
            Models.turn(turns, 0f, -1f, out);
            check(near(out[0], fronts[turns][0]) && near(out[1], fronts[turns][1]), "the front faces north, east, south and west in turn, at turn " + turns);
            // Seen from above the map, with north up, clockwise is from north to east. Whoever
            // stands in front of the piece looking at it has the model's +x on their right.
            float lookX = -out[0], lookY = -out[1];
            Models.turn(turns, 1f, 0f, out);
            check(near(out[0], -lookY) && near(out[1], lookX), "the model's +x is on the right of whoever faces its front, at turn " + turns);
            Models.turn(turns, 3f, 4f, out);
            check(near(out[0] * out[0] + out[1] * out[1], 25f), "a turn keeps lengths, at turn " + turns);
        }
        Models.turn(5, 0f, -1f, out);
        check(near(out[0], 1f) && near(out[1], 0f), "five quarter turns are one");
    }

    private static void modelsAreLaidOutOneAfterAnotherForTheGraphicsCard() throws IOException {
        Map<String, Models.Model> models = Models.read(gzipped(packedWedges("first", "second")));
        Models.Model first = models.get("first"), second = models.get("second");
        check(first.start == 0 && second.start == 3, "each model starts where the one before it ends");
        float[] buffer = Models.buffer(models.values());
        check(buffer.length == 6 * Models.BUFFER_FLOATS, "every vertex of every model is there");
        int cloth = (second.start + 1) * Models.BUFFER_FLOATS;
        check(near(buffer[cloth], 0f) && near(buffer[cloth + 1], 2f) && near(buffer[cloth + 2], 1f), "a vertex is where the model has it, not turned or moved");
        check(near(buffer[cloth + 3], 0f) && near(buffer[cloth + 4], 0f) && near(buffer[cloth + 5], 1f), "and looks the way the model has it look");
        check(near(buffer[cloth + 6], Models.CLOTH), "it says what it is made of");
        // The picture raises the light a vertex carries to the power 2.2, which leaves the fifth of it the vertex was packed with.
        check(near(buffer[cloth + 7], (float) Math.pow(0.2, 1.0 / 2.2)), "and how much of its square's light the model lets through to it");
        check(near(buffer[7], 1f) && near(buffer[2 * Models.BUFFER_FLOATS + 6], Models.METAL), "the first model's vertices are before it");
    }

    /** A wedge stood facing east at half size, its middle at (10.5, 20.5), on a floor 2.5 up, out of doors. Its cloth is dyed, and its metal from a second place. */
    private static Models.Instances stoodWedge(Models.Model wedge) {
        SpriteGeometry.Frame light = new SpriteGeometry.Frame();
        light.light(0.5f, 0.6f, 0.7f);
        light.open = 1f;
        Models.Instances pieces = new Models.Instances();
        pieces.add(wedge, light, 10, 20, 10.5f, 20.5f, 2.5f, 1, 0.5f, 0.25f, 77, 1 << Models.CLOTH, 0.25f, 0.75f,
                1 << Models.METAL | 1 << Models.CLOTH, 0.3f, 0.4f);
        return pieces;
    }

    private static void pieceIsPackedAsThePictureIsToldIt() throws IOException {
        Map<String, Models.Model> models = Models.read(gzipped(packedWedges("first", "second")));
        Models.Instances pieces = stoodWedge(models.get("second"));
        check(pieces.count == 1 && pieces.triangles() == 1, "a piece is one record whatever its model holds");
        float[] f = pieces.floats;
        int[] whole = pieces.ints;
        check(whole[Models.Instances.FIRST] == 3 && whole[Models.Instances.COUNT] == 3, "it says which vertices of the buffer are its model");
        check(whole[Models.Instances.TEXTURE] == 77, "and which texture dyes it");
        check(near(f[Models.Instances.PLACE], 10f) && near(f[Models.Instances.PLACE + 1], 20f), "it is lit by the square it was stood on");
        check(near(f[Models.Instances.PLACE + 2], 0.5f) && near(f[Models.Instances.PLACE + 3], 0.25f), "it keeps its size along the ground and its size up apart");
        check(near(f[Models.Instances.FOOT], 0.5f) && near(f[Models.Instances.FOOT + 1], 0.5f) && near(f[Models.Instances.FOOT + 2], 2.5f),
                "its origin is given from that square's corner, and its foot is on the floor");
        check(near(f[Models.Instances.SURFACES], 1f), "it is out of doors as its square is");
        check(f[Models.Instances.SURFACES + 1] == (1 << Models.CLOTH), "the dyed surfaces are a bit each");
        check(f[Models.Instances.SURFACES + 2] == (1 << Models.METAL), "a surface dyed from the first place is not also dyed from the second");
        check(near(f[Models.Instances.DYE], 0.25f) && near(f[Models.Instances.DYE + 1], 0.75f) && near(f[Models.Instances.DYE + 2], 0.3f)
                && near(f[Models.Instances.DYE + 3], 0.4f), "each dye looks at its own place on the sprite");
        check(Models.FIRST_DYED_MATERIAL - Models.FIRST_MATERIAL >= Models.SURFACES, "the dyed surfaces are numbered clear of the plain ones");
        check((1 << Models.SURFACES) - 1 < 1 << 24, "every set of surfaces is a whole number a float holds exactly");

        pieces.add(models.get("first"), new SpriteGeometry.Frame(), 3, 4, 3.5f, 4.5f, 0f, 0, 1f, 1f, 0, 0, 0f, 0f, 0, 0f, 0f);
        for (int i = 0; i < 20; i++) {
            pieces.add(models.get("second"), new SpriteGeometry.Frame(), i, 0, i + 0.5f, 0.5f, 0f, i, 1f, 1f, 0, 0, 0f, 0f, 0, 0f, 0f);
        }
        f = pieces.floats;
        whole = pieces.ints;
        check(pieces.count == 22 && pieces.triangles() == 22, "pieces are kept however many there are");
        check(near(f[Models.Instances.PLACE], 10f) && whole[Models.Instances.TEXTURE] == 77, "and the first is as it was");
        check(whole[Models.Instances.INTS + Models.Instances.FIRST] == 0 && whole[Models.Instances.INTS + Models.Instances.TEXTURE] == 0,
                "a piece that takes no dye names no texture");
        check(near(f[21 * Models.Instances.FLOATS + Models.Instances.PLACE], 19f) && whole[21 * Models.Instances.INTS + Models.Instances.FIRST] == 3,
                "the last is where the last was put");
        pieces.clear();
        check(pieces.count == 0 && pieces.triangles() == 0, "a chunk built again starts with none");
    }

    /**
     * Each vertex of the model as it was written into a chunk's mesh before the graphics card
     * stood the models: turned, then made the size it is told along the ground and up, then
     * moved to where the model stands.
     */
    private static void pieceStandsWhereTheModelTurnedScaledAndMovedIs() throws IOException {
        Models.Model wedge = wedge();
        SpriteGeometry.Frame light = new SpriteGeometry.Frame();
        float[] turned = new float[2], stood = new float[3], rows = new float[4];
        float[][] sizes = {{1f, 1f}, {0.5f, 0.5f}, {0.97f, 1.4f}, {2f, 0.2f}};
        for (int turns = 0; turns < 4; turns++) {
            for (float[] size : sizes) {
                int tileX = 10712, tileY = 9604;
                float centreX = tileX + 1.5f, centreY = tileY + 0.31f, baseUp = 2.5f;
                Models.Instances pieces = new Models.Instances();
                pieces.add(wedge, light, tileX, tileY, centreX, centreY, baseUp, turns, size[0], size[1], 0, 0, 0f, 0f, 0, 0f, 0f);
                String when = " at turn " + turns + " and size " + size[0] + " by " + size[1];
                for (int at = 0; at < wedge.vertices.length; at += Models.VERTEX_FLOATS) {
                    float[] v = wedge.vertices;
                    Models.turn(turns, v[at], v[at + 1], turned);
                    float overX = centreX - tileX + turned[0] * size[0], overY = centreY - tileY + turned[1] * size[0];
                    pieces.stand(0, v[at], v[at + 1], v[at + 2], stood, 0);
                    check(stood[0] == tileX + overX && stood[1] == tileY + overY && stood[2] == baseUp + v[at + 2] * size[1],
                            "a vertex stands where it was written before," + when);
                    // The way a vertex looks is turned with the model and nothing else.
                    Models.turn(turns, v[at + 3], v[at + 4], turned);
                    System.arraycopy(pieces.floats, Models.Instances.TURN, rows, 0, 4);
                    check(rows[0] * v[at + 3] + rows[1] * v[at + 4] == turned[0] && rows[2] * v[at + 3] + rows[3] * v[at + 4] == turned[1],
                            "and looks the way it did," + when);
                }
            }
        }
        Models.Instances east = stoodWedge(wedge);
        // The model's (-1, -2, 0) faces east: its -y is the map's +x, its +x the map's -y.
        east.stand(0, -1f, -2f, 0f, stood, 0);
        check(near(stood[0], 11.5f) && near(stood[1], 21f) && near(stood[2], 2.5f), "a vertex is turned, scaled and moved to where the model stands");
        east.stand(0, 0f, 2f, 1f, stood, 0);
        check(near(stood[0], 9.5f) && near(stood[1], 20.5f) && near(stood[2], 2.75f), "and is as high as the model is told to be, whatever its size along the ground");
    }

    private static void pieceCarriesTheLightOfItsSquaresCorners() throws IOException {
        SpriteGeometry.Frame light = new SpriteGeometry.Frame();
        // The square's four corners, (0, 0), (1, 0), (1, 1) then (0, 1), each a light of its own.
        float[][] corners = {{0.1f, 0.5f, 0.9f, 0.3f}, {0.2f, 0.4f, 0.6f, 0.8f}, {1f, 0f, 0.5f, 0.25f}};
        System.arraycopy(corners[0], 0, light.cornerRed, 0, 4);
        System.arraycopy(corners[1], 0, light.cornerGreen, 0, 4);
        System.arraycopy(corners[2], 0, light.cornerBlue, 0, 4);
        Models.Instances pieces = new Models.Instances();
        pieces.add(wedge(), light, 0, 0, 0.5f, 0.5f, 0f, 0, 0.2f, 0.2f, 0, 0, 0f, 0f, 0, 0f, 0f);
        float[] f = pieces.floats;
        float[][] places = {{0f, 0f}, {1f, 0f}, {1f, 1f}, {0f, 1f}, {0.7f, 0.1f}, {0.5f, 0.9f}};
        for (float[] place : places) {
            float a = place[0], b = place[1];
            float[] weights = {(1f - a) * (1f - b), a * (1f - b), a * b, (1f - a) * b};
            for (int colour = 0; colour < 3; colour++) {
                float want = 0f;
                for (int corner = 0; corner < 4; corner++) {
                    want += weights[corner] * corners[colour][corner];
                }
                int at = Models.Instances.LIGHT + colour;
                // The sum the picture does for a vertex that far along and down its square.
                float got = f[at] + a * f[at + 3] + b * f[at + 6] + a * b * f[at + 9];
                check(near(got, want), "a place over the square takes of each corner's light by how near that corner it is, at " + a + ", " + b);
            }
        }
    }

    private static void pieceIsHeldByItsBall() throws IOException {
        Models.Model wedge = wedge();
        SpriteGeometry.Frame light = new SpriteGeometry.Frame();
        float[] stood = new float[3];
        for (int turns = 0; turns < 4; turns++) {
            Models.Instances pieces = new Models.Instances();
            pieces.add(wedge, light, 5, 7, 5.2f, 7.9f, 1f, turns, 0.6f, 1.7f, 0, 0, 0f, 0f, 0, 0f, 0f);
            float[] f = pieces.floats;
            int ball = Models.Instances.BALL;
            for (int corner = 0; corner < 8; corner++) {
                pieces.stand(0, (corner & 1) == 0 ? wedge.minX : wedge.maxX, (corner & 2) == 0 ? wedge.minY : wedge.maxY,
                        (corner & 4) == 0 ? wedge.minZ : wedge.maxZ, stood, 0);
                float away = (float) Math.sqrt((stood[0] - f[ball]) * (stood[0] - f[ball]) + (stood[1] - f[ball + 1]) * (stood[1] - f[ball + 1])
                        + (stood[2] - f[ball + 2]) * (stood[2] - f[ball + 2]));
                check(away <= f[ball + 3] + 1e-4f, "every corner of the piece is inside the ball it is left out of the picture by, at turn " + turns);
                check(away >= f[ball + 3] - 1e-4f, "and the ball is no bigger than that takes");
            }
        }
    }

    /**
     * A sprite at twice the game's first size whose trimmed rectangle is what a rectangle on a
     * wall of its square paints: from 0.2 to 0.7 along the wall, 0.3 to 0.6 of a level up.
     */
    private static SpriteGeometry.Frame paintedOn(int wall) {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        float left = Float.MAX_VALUE, right = -Float.MAX_VALUE, top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        for (float along : new float[] {0.2f, 0.7f}) {
            for (float high : new float[] {0.3f, 0.6f}) {
                float u = SpriteGeometry.frameU(frame, WallHung.a(wall, along), WallHung.b(wall, along));
                float v = SpriteGeometry.frameV(frame, WallHung.a(wall, along), WallHung.b(wall, along), high);
                left = Math.min(left, u);
                right = Math.max(right, u);
                top = Math.min(top, v);
                bottom = Math.max(bottom, v);
            }
        }
        // The outermost half texel each way is the sprite's soft edge.
        frame.left = left - 0.5f;
        frame.top = top - 0.5f;
        frame.width = right - left + 1f;
        frame.height = bottom - top + 1f;
        frame.uStart = 0.25f;
        frame.uEnd = 0.5f;
        frame.vStart = 0.5f;
        frame.vEnd = 0.75f;
        return frame;
    }

    private static void paintingOnAWallIsFoundWhereItHangs() {
        float[] at = new float[4];
        for (int wall : new int[] {WallHung.NORTH, WallHung.WEST, WallHung.SOUTH, WallHung.EAST}) {
            check(WallHung.painted(paintedOn(wall), wall, at), "a rectangle painted on wall " + wall + " is something hung there");
            check(near(at[WallHung.FROM], 0.2f) && near(at[WallHung.TO], 0.7f), "it is found where along wall " + wall + " it is, from "
                    + at[WallHung.FROM] + " to " + at[WallHung.TO]);
            check(near(at[WallHung.BOTTOM], 0.3f) && near(at[WallHung.TOP], 0.6f), "and how high up wall " + wall + ", from "
                    + at[WallHung.BOTTOM] + " to " + at[WallHung.TOP]);
        }
        check(near(WallHung.a(WallHung.NORTH, 0.3f), 0.3f) && near(WallHung.b(WallHung.NORTH, 0.3f), 0f), "the north wall runs along the square's first edge");
        check(near(WallHung.a(WallHung.EAST, 0.3f), 1f) && near(WallHung.b(WallHung.EAST, 0.3f), 0.3f), "the east wall runs down its far edge");
    }

    private static void spriteDrawnForAnotherWallIsNoPainting() {
        float[] at = new float[4];
        // The same painting, taken for one on the wall across the square: it comes out off that wall's end.
        check(!WallHung.painted(paintedOn(WallHung.NORTH), WallHung.WEST, at), "a north wall's picture is not on the west wall");
        check(!WallHung.painted(paintedOn(WallHung.WEST), WallHung.NORTH, at), "nor a west wall's on the north");
        SpriteGeometry.Frame speck = paintedOn(WallHung.NORTH);
        speck.width = 3f;
        check(!WallHung.painted(speck, WallHung.NORTH, at), "a sliver of paint is nothing hung on a wall");
    }

    private static void pictureLiesOverItsFrameAsTheSpritePaintsIt() throws IOException {
        for (int wall : new int[] {WallHung.NORTH, WallHung.WEST}) {
            SpriteGeometry.Frame frame = paintedOn(wall);
            float[] at = new float[4], shows = new float[6], way = new float[2];
            check(WallHung.painted(frame, wall, at), "the picture is found");
            // A model half a metre wide and a metre high, hung to fill what was found, facing into the square.
            int turns = wall == WallHung.NORTH ? 2 : 1;
            float along = (at[WallHung.FROM] + at[WallHung.TO]) / 2f, scale = (at[WallHung.TO] - at[WallHung.FROM]) / 0.5f;
            float up = at[WallHung.TOP] - at[WallHung.BOTTOM];
            Models.turn(turns, 1f, 0f, way);
            WallHung.shows(frame, WallHung.a(wall, along), WallHung.b(wall, along), at[WallHung.BOTTOM], way[0] * scale, way[1] * scale, up, shows);
            // Each corner of the model's face shows what the sprite paints at that corner of the rectangle on the wall.
            for (float x : new float[] {-0.25f, 0.25f}) {
                for (float z : new float[] {0f, 1f}) {
                    float where = along + (way[0] + way[1]) * x * scale, high = at[WallHung.BOTTOM] + z * up;
                    float u = SpriteGeometry.frameU(frame, WallHung.a(wall, where), WallHung.b(wall, where));
                    float v = SpriteGeometry.frameV(frame, WallHung.a(wall, where), WallHung.b(wall, where), high);
                    float wantU = frame.uStart + (u - frame.left) / frame.width * (frame.uEnd - frame.uStart);
                    float wantV = frame.vStart + (v - frame.top) / frame.height * (frame.vEnd - frame.vStart);
                    check(near(shows[0] + x * shows[2] + z * shows[4], wantU) && near(shows[1] + x * shows[3] + z * shows[5], wantV),
                            "the corner " + x + ", " + z + " of a picture on wall " + wall + " shows the sprite's own corner");
                    check(wantU >= frame.uStart && wantU <= frame.uEnd && wantV >= frame.vStart && wantV <= frame.vEnd, "which is inside the sprite");
                }
            }
            // Seen from inside the square, what is on the right of the picture is on the right of the sprite, on either wall.
            check(shows[2] > 0f, "the picture is not mirrored on wall " + wall);
            check(shows[5] < 0f, "and its top is the sprite's top");
        }
        Models.Instances pieces = stoodWedge(wedge());
        pieces.picture(new float[] {0.1f, 0.2f, 0.3f, 0.4f, 0f, 0.6f});
        float[] f = pieces.floats;
        check(near(f[Models.Instances.DYE], 0.1f) && near(f[Models.Instances.DYE + 1], 0.2f), "a picture's origin is where its piece's first colour was taken");
        check(near(f[Models.Instances.PICTURE], 0.3f) && near(f[Models.Instances.PICTURE + 1], 0.4f) && near(f[Models.Instances.PICTURE + 2], 0f)
                && near(f[Models.Instances.PICTURE + 3], 0.6f), "and how it lies over the model goes with the piece");
        pieces.add(wedge(), new SpriteGeometry.Frame(), 3, 4, 3.5f, 4.5f, 0f, 0, 1f, 1f, 0, 0, 0f, 0f, 0, 0f, 0f);
        check(near(pieces.floats[Models.Instances.FLOATS + Models.Instances.PICTURE + 3], 0f), "a piece with no picture has none left over from another");
    }

    private static void frameIsChosenByThePicturesShape() {
        check(WallHung.frameFor(0.6f / 0.9f) == 0 && WallHung.frameFor(1f) == 1 && WallHung.frameFor(0.9f / 0.6f) == 2, "each frame is chosen for a picture of its own shape");
        check(WallHung.frameFor(0.3f) == 0 && WallHung.frameFor(4f) == 2, "and the nearest for one of no frame's shape");
        Models.Model tall = Models.get("frame_tall"), wide = Models.get("frame_wide");
        check(tall.height() > tall.width() && wide.width() > wide.height(), "the tall frame is the tall one");
        check(tall.first[Models.PANE + 1] > tall.first[Models.PANE], "a frame holds a pane");
    }

    private static void jarHoldsEveryModelStoodOnTheFloor() {
        String[] names = {"double_bed", "single_bed", "wardrobe", "dining_chair", "small_table", "sofa", "armchair", "bedside_cabinet", "dresser", "desk",
                "office_chair", "counter", "counter_sink", "counter_corner", "wall_cupboard", "wall_cupboard_corner", "fridge", "oven", "bookcase", "locker",
                "tv", "tv_console", "table_lamp", "toilet", "bath", "washing_machine", "wheelie_bin", "litter_bin", "mailbox", "postbox", "park_bench",
                "picnic_table", "crate", "barrel", "frame_tall", "frame_square", "frame_wide", "canvas", "wall_clock", "table_2", "table_3", "table_4",
                "narrow_table", "low_table", "low_table_2", "round_table", "office_desk", "shop_counter", "low_shelves", "wall_shelf", "wall_shelves",
                "metal_rack", "store_shelves", "plastic_chair", "folding_chair", "booth", "stool", "vending_machine", "arcade_machine", "dumpster",
                "radio", "microwave", "dishwasher", "chest_freezer", "register", "shower", "shower_head", "kettle_barbecue", "gas_barbecue",
                "mini_fridge", "large_fridge"};
        for (String name : names) {
            Models.Model model = Models.get(name);
            // A length of canvas is a bare slab. Everything else has parts.
            check((model.triangles() > 100 || name.equals("canvas")) && model.triangles() <= 6000,
                    name + " is a model of a size to draw, at " + model.triangles() + " triangles");
            check(near(model.minZ, 0f), name + " stands on the floor");
            if (name.startsWith("shower")) {
                // Built on its square, the wall at its back.
                check(model.minX >= -0.5f && model.maxX <= 0.5f && model.minY >= -0.5f && model.maxY <= 0.5f, name + " lies inside the square it stands on");
                check(model.maxY > 0.4f, name + " reaches the wall behind it");
            } else if (name.endsWith("_corner")) {
                // The corner of a run fills a square and is built round the middle of that square.
                check(model.minX >= -0.5f && model.maxX <= 0.5f && model.minY >= -0.5f && model.maxY <= 0.5f, name + " lies inside the square it fills");
                check(near(model.maxX, 0.5f) && near(model.minY, -0.5f), name + " reaches the two edges of its square that the run goes on from");
            } else {
                check(Math.abs(model.minX + model.maxX) < 1e-3f && Math.abs(model.minY + model.maxY) < 1e-3f, name + " has its origin at the middle of its footprint");
            }
            float least = 1f;
            for (int at = 0; at < model.vertices.length; at += Models.VERTEX_FLOATS) {
                float[] v = model.vertices;
                float length = (float) Math.sqrt(v[at + 3] * v[at + 3] + v[at + 4] * v[at + 4] + v[at + 5] * v[at + 5]);
                // The picture reads a longer normal as a plant that moves with the wind.
                check(Math.abs(length - 1f) < 1e-3f, name + " has unit normals, and one is " + length);
                check(v[at] >= model.minX - 1e-4f && v[at] <= model.maxX + 1e-4f && v[at + 2] <= model.maxZ + 1e-4f, name + " lies inside its bounds");
                least = Math.min(least, model.reached[at / Models.VERTEX_FLOATS]);
            }
            check(least >= 0.29f, name + " is nowhere left with less light than the darkest joint keeps, and has " + least);
        }
        wallTintSurvivesItsPacking();
        meshesAreKeptByTextureAndTint();
        BuildingTests.run();
        RoofTests.run();
        ShapeTests.run();
        CrownTests.run();
        GrassTests.run();
        WindowTests.run();
        ReflectionTests.run();
        DecorTests.run();
        ScopeTests.run();
        KerbTests.run();
        usualColourIsWhatMostOfTheSpriteIsPaintedIn();
        System.out.println("all tests passed");
    }

    private static void wallTintSurvivesItsPacking() {
        float[] tint = new float[4];
        WallSides.tint(0, tint);
        check(tint[0] == 0f && tint[1] == 0f && tint[2] == 0f && tint[3] == 0f, "no key is no tint, which the picture reads as the sprite's own colour");
        float step = 0.5f / 15f + 1e-4f;
        for (float[] colour : new float[][] {{0.8f, 0.42f, 0.13f}, {1f, 1f, 1f}, {0.03f, 0.5f, 0.97f}}) {
            for (int how : new int[] {WallSides.FLAT, WallSides.SIDING}) {
                int key = WallSides.key(colour[0], colour[1], colour[2], how);
                check(key != 0, "a colour's key is never the key that means none");
                WallSides.tint(key, tint);
                check(Math.abs(tint[0] - colour[0]) <= step && Math.abs(tint[1] - colour[1]) <= step && Math.abs(tint[2] - colour[2]) <= step,
                        "the colour comes back to within half a step of sixteen");
                check(tint[3] == how, "and so does how it is painted");
                check(WallSides.key(tint[0], tint[1], tint[2], how) == key, "what came back packs to itself");
            }
        }
        check(WallSides.key(0f, 0f, 0f, WallSides.FLAT) != 0, "black paint is still a tint");
        check(WallSides.key(0.4f, 0.4f, 0.4f, WallSides.FLAT) == WallSides.key(0.41f, 0.39f, 0.4f, WallSides.FLAT), "colours a hair apart share a key, and so a draw");
        check(WallSides.key(0.4f, 0.4f, 0.4f, WallSides.FLAT) != WallSides.key(0.4f, 0.4f, 0.4f, WallSides.SIDING), "paint and boards of one colour are two draws");
    }

    private static void meshesAreKeptByTextureAndTint() {
        SpriteGeometry.MeshSet meshes = new SpriteGeometry.MeshSet();
        SpriteGeometry.Mesh plain = meshes.get(7), tinted = meshes.get(7, 0x8421);
        check(plain != tinted && plain.tint == 0 && tinted.tint == 0x8421 && tinted.texture == 7, "one texture in two tints is two meshes");
        for (int texture = 1; texture < 200; texture++) {
            meshes.get(texture, texture % 3 == 0 ? 0x1111 * (texture % 5 + 1) : 0);
        }
        check(meshes.get(7) == plain && meshes.get(7, 0x8421) == tinted, "and each is found again after the table has grown");
    }

    private static void usualColourIsWhatMostOfTheSpriteIsPaintedIn() {
        int width = 8, height = 6;
        ByteBuffer pixels = ByteBuffer.allocate(width * height * 4);
        // A sheet of red, with the sprite on it from (2, 1) to before (7, 5): 20 pixels, of
        // which 12 are a pale blue, 4 a dark frame, 2 unpainted and 2 a little lighter blue.
        for (int i = 0; i < width * height; i++) {
            pixels.put(i * 4, (byte) 255).put(i * 4 + 3, (byte) 255);
        }
        int[][] kinds = {{100, 150, 200, 255}, {20, 20, 20, 255}, {255, 255, 255, 0}, {110, 160, 210, 255}};
        int[] counts = {12, 4, 2, 2};
        int kind = 0, used = 0;
        for (int y = 1; y < 5; y++) {
            for (int x = 2; x < 7; x++) {
                if (used == counts[kind]) {
                    kind++;
                    used = 0;
                }
                used++;
                for (int channel = 0; channel < 4; channel++) {
                    pixels.put((y * width + x) * 4 + channel, (byte) kinds[kind][channel]);
                }
            }
        }
        float[] colour = new float[4];
        check(WallSides.usualColour(pixels, width, 2, 1, 7, 5, colour), "a painted sprite has a colour");
        float red = (12 * 100 + 2 * 110) / (14 * 255f), green = (12 * 150 + 2 * 160) / (14 * 255f), blue = (12 * 200 + 2 * 210) / (14 * 255f);
        check(near(colour[0], red) && near(colour[1], green) && near(colour[2], blue),
                "it is the mean of the brightness most of it has: not the frame, the unpainted pixels or the sheet round it");
        check(near(colour[3], 0.299f * red + 0.587f * green + 0.114f * blue), "with the brightness of that colour");
        check(!WallSides.usualColour(pixels, width, 2, 1, 2, 5, colour), "a sprite of no width has none");
        for (int i = 0; i < width * height; i++) {
            pixels.put(i * 4 + 3, (byte) 0);
        }
        check(!WallSides.usualColour(pixels, width, 2, 1, 7, 5, colour), "nor has one with nothing painted");
        // Pieces that stand side by side in a run are a tile wide, so that they meet.
        for (String name : new String[] {"counter", "counter_sink", "wall_cupboard"}) {
            check(near(Models.get(name).width(), 1f), name + " is a metre wide");
        }
        check(near(Models.get("counter").depth(), Models.get("counter_sink").depth()), "a counter with a basin is as deep as one without, and stands where it would");
    }

    private static void fineSkinFileIsNamedAfterTheGamesTexture() {
        check("F_Hair_Braids.png".equals(FineSkins.Pack.relative(
                "C:\\Program Files (x86)\\Steam\\steamapps\\common\\ProjectZomboid\\media\\textures\\F_Hair_Braids.png")),
                "a texture straight under the game's folder is a file straight under the pack's");
        check("Body/MaleBody01.png".equals(FineSkins.Pack.relative("D:/Games/ProjectZomboid/media/textures/Body/MaleBody01.png")),
                "a texture in a folder is in the same folder of the pack");
        check("Body/Masks/Mask_Chest.PNG".equals(FineSkins.Pack.relative("C:\\pz\\MEDIA\\Textures\\Body/Masks\\Mask_Chest.PNG")),
                "either slash and any case of the game's folder, with the rest as the game spells it");
        check("Body/MaleBody01.png".equals(FineSkins.Pack.relative("media/textures/Body/MaleBody01.png")),
                "a name that starts at the game's folder");
        check("Clothes/Shirt.png".equals(FineSkins.Pack.relative("C:/pz/mods/Mine/media/textures/old/media/textures/Clothes/Shirt.png")),
                "the last folder of that name is the one the file is under");
        check(FineSkins.Pack.relative("SmartTexture") == null, "a composed texture has no file");
        check(FineSkins.Pack.relative(null) == null, "nor has a texture with no name");
        check(FineSkins.Pack.relative("C:/pz/multimedia/textures/Body/MaleBody01.png") == null, "a folder that only ends like the game's is not it");
        check(FineSkins.Pack.relative("C:/pz/media/textures/../../secret.png") == null, "a name that climbs out of the folder names nothing");
        check(FineSkins.Pack.relative("C:/pz/media/textures/Body//MaleBody01.png") == null, "nor does one with an empty piece");
        check(FineSkins.Pack.relative("C:/pz/media/textures/D:secret.png") == null, "nor does one that names another drive");
        check(FineSkins.Pack.relative("C:/pz/media/textures/Body/skin.png:hidden.png") == null, "nor one that names a stream of a file");
        check(FineSkins.Pack.relative("C:/pz/media/textures/Body/MaleBody01.txt") == null, "only pictures are in the pack");
        check(FineSkins.Pack.relative("C:/pz/media/textures/.png") == null, "a picture has a name");
        check(FineSkins.Pack.folder().endsWith(Path.of("Zomboid", "CameraUnlock", "skins")),
                "the pack is in the game's own folder of the player's");
    }

    public static String resultAndArgument(String result, String argument) {
        return result + " from " + argument;
    }

    private static void hookBeforeAReturnGetsTheResultAndTheFirstArgument() throws Throwable {
        ClassDesc string = ConstantDescs.CD_String;
        MethodTypeDesc make = MethodTypeDesc.of(string, string);
        byte[] subject = ClassFile.of().build(ClassDesc.of("com.cameraunlock.zomboid.SubjectWithArgument"), type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC, code ->
                        code.aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void).return_())
                .withMethodBody("make", make, ClassFile.ACC_PUBLIC, code -> code.ldc("made").areturn()));
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/SubjectWithArgument", "make", "(Ljava/lang/String;)Ljava/lang/String;");
        CallSite.Method hook = new CallSite.Method("com/cameraunlock/zomboid/Tests", "resultAndArgument",
                "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
        byte[] hooked = CallSite.insertBeforeReturns(subject, within, code -> CallSite.passFirstArgument(code, hook));
        Class<?> defined = MethodHandles.lookup().defineClass(hooked);
        Object made = defined.getMethod("make", String.class).invoke(defined.getConstructor().newInstance(), "steps");
        check("made from steps".equals(made), "the hook is given what the method was about to return and the method's first argument, and its answer is returned");
    }

    private static void partsAreToldByTheirNames() {
        String media = "C:\\Games\\Leather Goods\\ProjectZomboid\\media\\textures\\";
        check(CharacterDraw.wearOf("skinned/hair/m_hair_picard", media + "F_Hair_White.png", false) == CharacterDraw.HAIR, "a head of hair is hair by its model's name");
        check(CharacterDraw.wearOf("Bob_Beard_Full", "SmartTexture", false) == CharacterDraw.HAIR, "a beard is hair");
        check(CharacterDraw.wearOf("FemaleBody", "SmartTexture", true) == CharacterDraw.BODY, "a character's composed skin is a body");
        check(CharacterDraw.wearOf("Skinned\\MaleBody", media + "Body\\M_ZedBody01.png", false) == CharacterDraw.BODY, "a body is a body by its name");
        check(CharacterDraw.wearOf("Bob_Jacket", media + "Clothes\\Jacket\\Jacket_Denim.png", false) == CharacterDraw.TWILL,
                "denim is twill, and the folder the game is installed in says nothing of it");
        check(CharacterDraw.wearOf("Kate_JacketLeather", "SmartTexture", false) == CharacterDraw.LEATHER, "leather is told by the model when the texture is composed");
        check(CharacterDraw.wearOf("Bob_Jumper", media + "Clothes\\Jumper\\Jumper_Roundneck.png", false) == CharacterDraw.KNIT, "a jumper is knitted");
        check(CharacterDraw.wearOf("Bob_JacketPadded", "SmartTexture", false) == CharacterDraw.WEAVE, "a padded jacket is cloth, not a pad");
        check(CharacterDraw.wearOf("Bob_Kneepad_L", "SmartTexture", false) == CharacterDraw.SLEEK, "a knee pad is hard and smooth");
        check(CharacterDraw.wearOf("Bob_Poncho", null, false) == CharacterDraw.WEAVE, "what nothing is known of is plain cloth");
    }

    private static void textureRunsDownAMeshTheSameEitherSideOfASeam() {
        // A square of four triangles round its middle, which the mesh holds twice. The
        // texture is laid on it upright, twice as wide as the square and half as high.
        float[] places = {0f, 0.5f, 0f, -1f, 1f, 0f, 1f, 1f, 0f, 1f, 0f, 0f, -1f, 0f, 0f, 0f, 0.5f, 0f};
        float[] uvs = new float[12];
        for (int vertex = 0; vertex < 6; vertex++) {
            uvs[vertex * 2] = places[vertex * 3] / 2f;
            uvs[vertex * 2 + 1] = (1f - places[vertex * 3 + 1]) * 2f;
        }
        int[] elements = {0, 1, 2, 0, 2, 3, 5, 3, 4, 5, 4, 1};
        float[] along = CharacterDraw.along(places, uvs, elements, 12);
        for (int vertex = 0; vertex < 6; vertex++) {
            check(near(along[vertex * 4], 0f) && near(along[vertex * 4 + 1], -1f) && near(along[vertex * 4 + 2], 0f), "the texture runs straight down the mesh at every vertex");
            check(along[vertex * 4 + 3] == (vertex == 0 || vertex == 5 ? 0f : 1f), "the middle is inside the mesh though it is held twice, and the corners are on its open edge");
        }
        float[] lay = CharacterDraw.laid(places, uvs, elements, 12);
        check(near(lay[0], 2f) && near(lay[1], 0.5f), "a whole width of the texture goes two units over the mesh and a whole height half a unit");
        float[] nowhere = CharacterDraw.laid(places, new float[12], elements, 12);
        check(nowhere[0] == 0f && nowhere[1] == 0f, "a mesh whose texture is laid nowhere has no lie");
        float[] unsaid = CharacterDraw.along(places, new float[12], elements, 12);
        check(unsaid[0] == 0f && unsaid[1] == 0f && unsaid[2] == 0f, "and its texture runs no way");
    }

    private static void fringeThinsOutWithDistance() {
        check(CharacterDraw.fringe(CharacterDraw.HAIR, 1.3f) > CharacterDraw.fringe(CharacterDraw.HAIR, 4f), "hair near the eye has more layers of fringe than hair further off");
        check(CharacterDraw.fringe(CharacterDraw.HAIR, 4f) > 0 && CharacterDraw.fringe(CharacterDraw.HAIR, 9f) == 0, "and far off it has none");
        check(CharacterDraw.fringe(CharacterDraw.KNIT, 1f) == 0, "nothing but hair has a fringe");
    }

    private static void smoothNormalsAgreeAcrossASeam() {
        // A ridge with a wing down either side. The mesh holds the ridge's two ends twice,
        // once for each wing, and lists the wings' corners opposite ways round.
        float h = (float) Math.sqrt(0.5);
        float[] places = {0f, 1f, 0f, 0f, 1f, 1f, -1f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 5f, 5f, 5f};
        float[] normals = {-h, h, 0f, -h, h, 0f, -h, h, 0f, h, h, 0f, h, h, 0f, h, h, 0f, 0f, 0f, 1f};
        float[] smooth = CharacterDraw.smoothNormals(places, normals, new int[] {0, 1, 2, 3, 4, 5}, 6);
        for (int ridge : new int[] {0, 1, 3, 4}) {
            check(near(smooth[ridge * 3], 0f) && near(smooth[ridge * 3 + 1], 1f) && near(smooth[ridge * 3 + 2], 0f),
                    "every copy of a vertex on the seam faces the way the two wings do between them");
        }
        check(near(smooth[6], -h) && near(smooth[7], h) && near(smooth[8], 0f), "a vertex of one face faces as the face does");
        check(near(smooth[15], h) && near(smooth[16], h) && near(smooth[17], 0f), "whichever way round the face's corners are listed");
        check(smooth[18] == 0f && smooth[19] == 0f && smooth[20] == 1f, "a vertex no triangle uses keeps its own normal");
    }

    private static final float[] PROJECTION = ViewCamera.projection((float) Math.tan(Math.toRadians(35)), 16f / 9f, 0.05f, 200f);

    /** Normalised device coordinates of a world point, or null when it is behind the eye. */
    private static float[] project(float[] view, float x, float y, float up) {
        float[] eye = multiply(view, new float[] {x, y, up, 1f});
        float[] clip = multiply(PROJECTION, eye);
        if (clip[3] <= 0f) {
            return null;
        }
        return new float[] {clip[0] / clip[3], clip[1] / clip[3], clip[2] / clip[3]};
    }

    private static float[] multiply(float[] m, float[] v) {
        float[] out = new float[4];
        for (int row = 0; row < 4; row++) {
            out[row] = m[row] * v[0] + m[4 + row] * v[1] + m[8 + row] * v[2] + m[12 + row] * v[3];
        }
        return out;
    }

    private static ViewCamera.Pose cleanPose(float x, float y, float up, float yaw, float pitch) {
        ViewCamera.Pose pose = new ViewCamera.Pose();
        ViewCamera.clean(pose, x, y, up, yaw, pitch);
        return pose;
    }

    private static float[] cleanView(float x, float y, float up, float yaw, float pitch) {
        return ViewCamera.view(cleanPose(x, y, up, yaw, pitch));
    }

    private static final float OVERHEAD_AWAY = 6000f, OVERHEAD_TAN = 12f / 6000f;

    private static void flightLeavesTheOverheadCameraAndLandsOnTheTrackedView() {
        float yawFrom = radians(-135), pitchFrom = radians(-30), yawTo = radians(100), pitchTo = radians(-10);
        ViewCamera.Pose overhead = cleanPose(0f, 0f, 0f, yawFrom, pitchFrom);
        float atX = 20f, atY = 30f, atUp = 0f;
        overhead.eyeX = atX - overhead.forwardX * OVERHEAD_AWAY;
        overhead.eyeY = atY - overhead.forwardY * OVERHEAD_AWAY;
        overhead.eyeUp = atUp - overhead.forwardUp * OVERHEAD_AWAY;
        ViewCamera.Pose clean = cleanPose(21f, 31f, 1.6f, yawTo, pitchTo);
        ViewCamera.Pose tracked = cleanPose(21.1f, 31f, 1.65f, yawTo, pitchTo);
        ViewCamera.turn(tracked, radians(25), radians(-8), radians(5), true);
        ViewCamera.Pose out = new ViewCamera.Pose();

        Flight.cut(false);
        Flight.camera(overhead, OVERHEAD_TAN, OVERHEAD_AWAY, yawFrom, pitchFrom, clean, tracked, yawTo, pitchTo, TAN_HALF_FOV, out);
        check(apart(out, overhead) < 1e-2f && turnedApart(out, overhead) < 1e-5f, "before it sets off the camera is the overhead one, whatever the head is doing");
        check(Flight.tanHalfFieldOfView() == OVERHEAD_TAN && near(Flight.away(), OVERHEAD_AWAY), "with its field of view and its distance");

        Flight.cut(true);
        Flight.camera(overhead, OVERHEAD_TAN, OVERHEAD_AWAY, yawFrom, pitchFrom, clean, tracked, yawTo, pitchTo, TAN_HALF_FOV, out);
        check(apart(out, tracked) < 1e-4f && turnedApart(out, tracked) < 1e-5f, "landed it is the tracked view, the head's turn and lean in it");
        check(Flight.tanHalfFieldOfView() == TAN_HALF_FOV && Flight.away() == 0f, "with the first-person field of view, at the eye");

        Flight.cut(false);
        float wasAway = OVERHEAD_AWAY, wasTan = OVERHEAD_TAN;
        while (!Flight.landed()) {
            Flight.advance(true, Flight.SECONDS / 20f, false);
            Flight.camera(overhead, OVERHEAD_TAN, OVERHEAD_AWAY, yawFrom, pitchFrom, clean, tracked, yawTo, pitchTo, TAN_HALF_FOV, out);
            check(near(out.rightX * out.rightX + out.rightY * out.rightY + out.rightUp * out.rightUp, 1f)
                    && near(out.upX * out.upX + out.upY * out.upY + out.upUp * out.upUp, 1f)
                    && near(out.forwardX * out.forwardX + out.forwardY * out.forwardY + out.forwardUp * out.forwardUp, 1f), "on the way its axes are each a unit long");
            check(near(out.rightX * out.upX + out.rightY * out.upY + out.rightUp * out.upUp, 0f)
                    && near(out.rightX * out.forwardX + out.rightY * out.forwardY + out.rightUp * out.forwardUp, 0f)
                    && near(out.upX * out.forwardX + out.upY * out.forwardY + out.upUp * out.forwardUp, 0f), "and square to each other");
            float cross = (out.rightY * out.upUp - out.rightUp * out.upY) * out.forwardX + (out.rightUp * out.upX - out.rightX * out.upUp) * out.forwardY
                    + (out.rightX * out.upY - out.rightY * out.upX) * out.forwardUp;
            check(cross > 0.99f, "and of the same hand as a clean view's, so the picture is never mirrored");
            check(Flight.away() < wasAway && Flight.tanHalfFieldOfView() > wasTan, "it only comes nearer, and its field of view only opens");
            wasAway = Flight.away();
            wasTan = Flight.tanHalfFieldOfView();
            float s = Flight.share();
            float lookedX = out.eyeX + out.forwardX * Flight.away(), lookedY = out.eyeY + out.forwardY * Flight.away();
            check(Math.abs(lookedX - (atX + (tracked.eyeX - atX) * s)) < 1e-2f && Math.abs(lookedY - (atY + (tracked.eyeY - atY) * s)) < 1e-2f,
                    "and the place it looks at goes straight from the middle of the screen to the eye");
        }
        Flight.cut(false);
    }

    private static void flightTakesItsTimeWhateverTheFrames() {
        Flight.cut(false);
        Flight.advance(true, 5f, false);
        check(Flight.share() < 0.1f, "a frame many times too long does not carry the camera half way");
        check(Flight.shown() == 1f, "over a view the renderer draws already, its picture is whole from the first frame");
        int frames = 1;
        while (!Flight.landed()) {
            Flight.advance(true, 1f / 60f, false);
            frames++;
        }
        check(Math.abs(frames / 60f - Flight.SECONDS) < 0.1f, "at sixty frames a second the way in takes the time it is given");
        check(Flight.share() == 1f, "and ends at the eye");
        Flight.advance(false, 1f / 60f, false);
        check(!Flight.landed() && !Flight.home(), "turned back, it is on its way out from where it was");
        while (Flight.share() > 0f) {
            Flight.advance(false, 1f / 60f, false);
        }
        check(Flight.home(), "and is home as soon as it is back overhead");
    }

    private static void flightMixesThePicturesOnlyWhileTheCameraIsStill() {
        Flight.cut(false);
        float was = 0f;
        int frames = 0;
        while (Flight.shown() < 1f) {
            Flight.advance(true, 1f / 60f, true);
            check(Flight.shown() > was && Flight.share() == 0f, "over the game's own picture the renderer's comes in with the camera still overhead");
            was = Flight.shown();
            frames++;
        }
        check(Math.abs(frames / 60f - Flight.MIX_SECONDS) < 0.05f, "in the time the mix is given");
        Flight.advance(true, 1f / 60f, true);
        check(Flight.share() > 0f, "and only then does the camera set off");
        while (!Flight.landed()) {
            Flight.advance(true, 1f / 60f, true);
        }
        while (Flight.share() > 0f) {
            check(Flight.shown() == 1f, "on the way out the picture is whole until the camera is back overhead");
            Flight.advance(false, 1f / 60f, true);
        }
        check(Flight.shown() == 1f && !Flight.home(), "where it is still whole, and the game's view is not back yet");
        was = 1f;
        while (!Flight.home()) {
            Flight.advance(false, 1f / 60f, true);
            check(Flight.shown() < was && Flight.share() == 0f, "and goes out over the game's own picture with the camera still");
            was = Flight.shown();
        }
    }

    private static float apart(ViewCamera.Pose a, ViewCamera.Pose b) {
        return (float) Math.sqrt((a.eyeX - b.eyeX) * (a.eyeX - b.eyeX) + (a.eyeY - b.eyeY) * (a.eyeY - b.eyeY) + (a.eyeUp - b.eyeUp) * (a.eyeUp - b.eyeUp));
    }

    /** The most any part of one pose's axes differs from the other's. */
    private static float turnedApart(ViewCamera.Pose a, ViewCamera.Pose b) {
        float most = Math.max(Math.abs(a.rightX - b.rightX), Math.max(Math.abs(a.rightY - b.rightY), Math.abs(a.rightUp - b.rightUp)));
        most = Math.max(most, Math.max(Math.abs(a.upX - b.upX), Math.max(Math.abs(a.upY - b.upY), Math.abs(a.upUp - b.upUp))));
        return Math.max(most, Math.max(Math.abs(a.forwardX - b.forwardX), Math.max(Math.abs(a.forwardY - b.forwardY), Math.abs(a.forwardUp - b.forwardUp))));
    }

    private static float radians(double degrees) {
        return (float) Math.toRadians(degrees);
    }

    private static final float TAN_HALF_FOV = (float) Math.tan(Math.toRadians(35));
    private static final float ASPECT = 16f / 9f;

    private static void headYawTurnsTheViewRight() {
        for (boolean worldYaw : new boolean[] {true, false}) {
            ViewCamera.Pose pose = cleanPose(0f, 0f, 1.6f, 0f, 0f);
            ViewCamera.turn(pose, radians(20), 0f, 0f, worldYaw);
            float[] ahead = project(ViewCamera.view(pose), 10f, 0f, 1.6f);
            check(ahead[0] < 0f && near(ahead[1], 0f), "positive head yaw turns right: what was ahead moves left");
        }
    }

    private static void headPitchLooksUp() {
        ViewCamera.Pose pose = cleanPose(0f, 0f, 1.6f, 0f, 0f);
        ViewCamera.turn(pose, 0f, radians(20), 0f, true);
        float[] ahead = project(ViewCamera.view(pose), 10f, 0f, 1.6f);
        check(ahead[1] < 0f && near(ahead[0], 0f), "positive head pitch looks up: what was ahead moves down");
    }

    private static void headRollTiltsTheViewLeft() {
        ViewCamera.Pose pose = cleanPose(0f, 0f, 1.6f, 0f, 0f);
        ViewCamera.turn(pose, 0f, 0f, radians(20), true);
        float[] above = project(ViewCamera.view(pose), 10f, 0f, 3f);
        check(above[0] > 0f, "positive head roll tilts left: a point above the centre swings to the right");
        float[] ahead = project(ViewCamera.view(pose), 10f, 0f, 1.6f);
        check(near(ahead[0], 0f) && near(ahead[1], 0f), "roll leaves the view centre where it was");
    }

    private static void worldYawKeepsTheHorizonLevel() {
        ViewCamera.Pose world = cleanPose(0f, 0f, 1.6f, 0f, radians(-60));
        ViewCamera.turn(world, radians(30), 0f, 0f, true);
        check(near(world.rightUp, 0f), "yaw about straight up leaves the right vector level");
        check(near(world.forwardUp, (float) Math.sin(radians(-60))), "and the pitch unchanged");

        ViewCamera.Pose local = cleanPose(0f, 0f, 1.6f, 0f, radians(-60));
        ViewCamera.turn(local, radians(30), 0f, 0f, false);
        check(Math.abs(local.rightUp) > 0.1f, "yaw about the view's own up tips the horizon when looking down");
    }

    private static void projectionAgreesWithTheViewMatrix() {
        ViewCamera.Pose pose = cleanPose(3f, 7f, 1.6f, radians(40), radians(-10));
        ViewCamera.turn(pose, radians(25), radians(15), radians(-20), true);
        pose.eyeX += 0.2f;
        pose.eyeY -= 0.1f;
        pose.eyeUp += 0.15f;
        float[] matrix = ViewCamera.projection(TAN_HALF_FOV, ASPECT, 0.05f, 200f);
        float[] eye = multiply(ViewCamera.view(pose), new float[] {9f, 14f, 0.5f, 1f});
        float[] clip = multiply(matrix, eye);
        float[] ndc = new float[2];
        check(ViewCamera.project(pose, TAN_HALF_FOV, ASPECT, 9f, 14f, 0.5f, ndc), "the point is in front of the eye");
        check(near(ndc[0], clip[0] / clip[3]) && near(ndc[1], clip[1] / clip[3]),
                "the reticle's projection lands where the frame's matrices put the same point");
        check(!ViewCamera.project(pose, TAN_HALF_FOV, ASPECT, pose.eyeX - pose.forwardX, pose.eyeY - pose.forwardY,
                pose.eyeUp - pose.forwardUp, ndc), "a point behind the eye has no place on the screen");
    }

    /** Where the clean aim's direction falls in a view turned by the head, the eye not moved. */
    private static float[] aimInTurnedView(float cleanPitch, float yaw, float pitch, float roll, boolean worldYaw) {
        ViewCamera.Pose clean = cleanPose(0f, 0f, 1.6f, radians(30), cleanPitch);
        ViewCamera.Pose turned = new ViewCamera.Pose();
        turned.set(clean);
        ViewCamera.turn(turned, yaw, pitch, roll, worldYaw);
        float[] ndc = new float[2];
        check(ViewCamera.project(turned, TAN_HALF_FOV, ASPECT, clean.eyeX + clean.forwardX, clean.eyeY + clean.forwardY,
                clean.eyeUp + clean.forwardUp, ndc), "the aim is in front of the turned view");
        return ndc;
    }

    private static void reticleStaysCentredUnderRollAndMovesStraightUnderPitch() {
        float[] rolled = aimInTurnedView(0f, 0f, 0f, radians(25), true);
        check(near(rolled[0], 0f) && near(rolled[1], 0f), "roll alone leaves the reticle at the centre");

        float[] pitched = aimInTurnedView(0f, 0f, radians(15), 0f, true);
        check(near(pitched[0], 0f) && pitched[1] < 0f, "pitch alone moves the reticle straight down the screen");

        // Roll is the outermost turn, so it swings the reticle round the centre at the distance
        // pitch put it: the distance from the centre in tangent space does not change with roll.
        float[] both = aimInTurnedView(0f, 0f, radians(15), radians(25), true);
        float radius = (float) Math.hypot(both[0] * ASPECT, both[1]);
        check(near(radius, Math.abs(pitched[1])), "pitch and roll together keep the reticle on the aim");

        float[] down = aimInTurnedView(radians(-89.9f), radians(40), 0f, 0f, true);
        check(Math.abs(down[0]) < 2e-3f && Math.abs(down[1]) < 2e-3f,
                "looking straight down, yaw about straight up spins the world round a reticle that stays put");
    }

    private static void reticleFollowsThePointWhenTheEyeLeans() {
        ViewCamera.Pose clean = cleanPose(0f, 0f, 1.6f, 0f, 0f);
        ViewCamera.Pose leaned = new ViewCamera.Pose();
        leaned.set(clean);
        leaned.eyeY += 0.3f;
        float[] nearPoint = new float[2];
        float[] farPoint = new float[2];
        ViewCamera.project(leaned, TAN_HALF_FOV, ASPECT, 1f, 0f, 1.6f, nearPoint);
        ViewCamera.project(leaned, TAN_HALF_FOV, ASPECT, 20f, 0f, 1.6f, farPoint);
        check(near(nearPoint[0], -0.3f / 1f / (TAN_HALF_FOV * ASPECT)), "a lean to the right puts a near aim point left by lean over distance");
        check(near(farPoint[0], -0.3f / 20f / (TAN_HALF_FOV * ASPECT)), "and a far one left by much less");
        check(near(nearPoint[1], 0f) && near(farPoint[1], 0f), "with no vertical movement");
    }

    private static void reticleFollowsTheFieldOfView() {
        ViewCamera.Pose clean = cleanPose(3f, 7f, 1.6f, radians(40), radians(-10));
        ViewCamera.Pose tracked = new ViewCamera.Pose();
        tracked.set(clean);
        ViewCamera.turn(tracked, radians(20), radians(10), radians(15), true);
        tracked.eyeY += 0.25f;
        float aimX = clean.eyeX + clean.forwardX * 6f, aimY = clean.eyeY + clean.forwardY * 6f,
                aimUp = clean.eyeUp + clean.forwardUp * 6f;
        float referenceTanHalf = (float) Math.tan(Math.toRadians(65) / 2);
        float[] reference = new float[2];
        ViewCamera.project(tracked, referenceTanHalf, ASPECT, aimX, aimY, aimUp, reference);
        for (float degrees : new float[] {40f, 65f, 90f, 110f}) {
            for (float aspect : new float[] {4f / 3f, ASPECT, 32f / 9f}) {
                float tanHalf = (float) Math.tan(Math.toRadians(degrees) / 2);
                float[] matrix = ViewCamera.projection(tanHalf, aspect,
                        ViewCamera.nearPlane(WorldScene.NEAR_CORNER_DISTANCE, tanHalf, aspect), 150f);
                float[] clip = multiply(matrix, multiply(ViewCamera.view(tracked), new float[] {aimX, aimY, aimUp, 1f}));
                float[] ndc = new float[2];
                check(ViewCamera.project(tracked, tanHalf, aspect, aimX, aimY, aimUp, ndc), "the aim point is in front of the eye");
                check(near(ndc[0], clip[0] / clip[3]) && near(ndc[1], clip[1] / clip[3]),
                        "the reticle lands where the frame's matrices put the aim point at " + degrees + " degrees, aspect " + aspect);
                float scale = referenceTanHalf / tanHalf;
                check(near(ndc[1], reference[1] * scale) && near(ndc[0] * aspect, reference[0] * ASPECT * scale),
                        "a wider field of view draws the reticle nearer the centre by the ratio of the tangents");
            }
        }
    }

    private static void nearPlaneKeepsItsCornersInsideTheLeanSkin() {
        for (float degrees : new float[] {40f, 65f, 110f}) {
            for (float aspect : new float[] {4f / 3f, ASPECT, 32f / 9f, 48f / 9f}) {
                float tanHalf = (float) Math.tan(Math.toRadians(degrees) / 2);
                float nearPlane = ViewCamera.nearPlane(WorldScene.NEAR_CORNER_DISTANCE, tanHalf, aspect);
                float corner = (float) Math.sqrt(nearPlane * nearPlane * (1 + tanHalf * tanHalf * (1 + aspect * aspect)));
                check(near(corner, WorldScene.NEAR_CORNER_DISTANCE), "the near plane's corners are the asked distance from the eye");
            }
        }
        check(WorldScene.NEAR_CORNER_DISTANCE < Settings.BUILT_IN.collisionMargin(), "the near plane's corners are nearer than a lean is held off a wall");
    }

    private static void fieldOfViewSetIsTheOneDrawnWith() {
        float before = FieldOfView.degrees();
        check(before == FieldOfView.DEFAULT_DEGREES, "with no file read the field of view is the row's default");
        FieldOfView.set(90f);
        check(near(FieldOfView.tanHalfVertical(), 1f), "a field of view of 90 degrees has a half tangent of 1");
        check(near(FieldOfView.drawnDegrees(), 90f), "and is the angle the picture is drawn with");
        FieldOfView.set(before);
    }

    private static void lightLeadsTheHeadsYawAndIgnoresTheRest() throws Exception {
        Log.open(Files.createTempDirectory("zomboid-headtracking-test"));
        ViewCamera.Pose level = cleanPose(0f, 0f, 1.6f, radians(200), 0f);
        ViewCamera.Pose down = cleanPose(0f, 0f, 1.6f, radians(200), radians(-60));
        ViewCamera.Pose up = cleanPose(0f, 0f, 1.6f, radians(200), radians(89));
        float lead = Settings.BUILT_IN.lightMultiplier();
        check(near(HeadFollowLight.headingTurn(level, radians(20), 0f, 0f, true), radians(20) * lead),
                "the beam turns the default multiple of the head's yaw");
        check(near(HeadFollowLight.headingTurn(level, radians(-20), 0f, 0f, false), radians(-20) * lead),
                "to the left too, and the same about the view's own up while the view is level");
        check(near(HeadFollowLight.headingTurn(down, radians(20), 0f, 0f, true), radians(30)), "looking down changes nothing about straight up");
        check(near(HeadFollowLight.headingTurn(up, radians(60), 0f, 0f, true), radians(90)), "nor does looking almost straight up");
        check(near(HeadFollowLight.headingTurn(down, radians(20), radians(15), radians(20), true), radians(30)),
                "pitch and roll turn no heading when the yaw is about straight up");
        // Yaw about a tipped up vector swings the view centre further over the ground: tan(turn) = tan(yaw) / cos(pitch).
        check(near(HeadFollowLight.headingTurn(down, radians(20), 0f, 0f, false),
                (float) Math.atan(Math.tan(Math.toRadians(30)) / 0.5)), "about the view's own up it is the heading the view centre would take");

        Settings.current = lightTurning(1f);
        ViewCamera.Pose view = cleanPose(0f, 0f, 1.6f, radians(200), radians(-60));
        ViewCamera.turn(view, radians(25), radians(10), radians(15), false);
        float viewTurn = (float) Math.atan2(view.forwardY, view.forwardX) + radians(360) - radians(200);
        check(near(HeadFollowLight.headingTurn(down, radians(25), radians(10), radians(15), false), viewTurn),
                "at 1 the beam has the heading of the view");
        Settings.current = Settings.BUILT_IN;

        HeadFollowLight.follow(level, radians(60), 0f, 0f, true);
        check(near(HeadFollowLight.turn(), radians(90)), "a followed head turns the beam");
        HeadFollowLight.release();
        check(HeadFollowLight.turn() == 0f, "and a released beam is the game's");

        Settings.current = lightTurning(0f);
        check(near(HeadFollowLight.headingTurn(level, radians(20), radians(10), radians(5), true), 0f), "a multiplier of 0 leaves the beam on the aim");
        Settings.current = Settings.BUILT_IN;
    }

    private static Settings lightTurning(float multiplier) {
        Settings s = Settings.BUILT_IN;
        return new Settings(s.udpPort(), s.dataFreshnessMs(), s.enableOnStartup(), s.worldSpaceYaw(), s.rotationEnabled(),
                s.positionEnabled(), s.trueFreeLook(), s.freeLookMarker(), s.stockSights(), s.collisionEnabled(),
                s.localSmoothing(), s.remoteSmoothing(), s.positionLimitX(), s.positionLimitY(), s.positionLimitYDown(),
                s.positionLimitZ(), s.positionLimitZBack(), s.collisionMargin(), s.collisionReleaseSmoothing(), multiplier,
                s.fieldOfView());
    }

    private static byte[] packet(double x, double y, double z, double yaw, double pitch, double roll) {
        ByteBuffer buffer = ByteBuffer.allocate(48).order(ByteOrder.LITTLE_ENDIAN);
        for (double value : new double[] {x, y, z, yaw, pitch, roll}) {
            buffer.putDouble(value);
        }
        return buffer.array();
    }

    private static void packetIsParsedAndValidated() {
        OpenTrackReceiver.Sample sample = OpenTrackReceiver.parse(packet(10, -20, 30, 1, 2, 3), 48, 5L);
        check(near(sample.x(), 0.1f) && near(sample.y(), -0.2f) && near(sample.z(), 0.3f), "centimetres become metres");
        check(sample.yaw() == 1f && sample.pitch() == 2f && sample.roll() == 3f, "angles stay in degrees");
        check(OpenTrackReceiver.parse(packet(0, 0, 0, 0, 0, 0), 47, 5L) == null, "a short datagram is dropped");
        check(OpenTrackReceiver.parse(packet(0, 0, 0, Double.NaN, 0, 0), 48, 5L) == null, "a NaN is dropped");
        check(OpenTrackReceiver.parse(packet(0, Double.POSITIVE_INFINITY, 0, 0, 0, 0), 48, 5L) == null, "an infinity is dropped");
        check(OpenTrackReceiver.parse(packet(1e300, 0, 0, 0, 0, 0), 48, 5L) == null, "a value too large for a float is dropped");
    }

    private static void receiverTakesOnlyChangedPosesAndReadsLocality() throws Exception {
        Log.open(Files.createTempDirectory("zomboid-headtracking-test"));
        OpenTrackReceiver receiver = new OpenTrackReceiver(4242, 500);
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        InetAddress lan = InetAddress.getByName("192.168.1.20");
        check(receiver.latest() == null && !receiver.isFresh(), "nothing before the first packet");
        receiver.accept(packet(0, 0, 0, 5, 0, 0), 48, loopback, System.nanoTime());
        OpenTrackReceiver.Sample first = receiver.latest();
        check(first != null && !receiver.isRemote() && receiver.isFresh(), "a loopback packet is a local connection");
        receiver.accept(packet(0, 0, 0, 5, 0, 0), 48, loopback, System.nanoTime());
        check(receiver.latest() == first, "a repeat of the same pose is not a new sample");
        receiver.accept(packet(0, 0, 0, Double.NaN, 0, 0), 48, lan, System.nanoTime());
        check(!receiver.isRemote(), "a datagram that is dropped does not change the connection's locality");
        receiver.accept(packet(0, 0, 0, 6, 0, 0), 48, lan, System.nanoTime());
        check(receiver.latest() != first && receiver.isRemote(), "a packet from another address is a remote connection");
    }

    private static OpenTrackReceiver.Sample sample(double x, double y, double z, double yaw, double pitch, double roll, long nanos) {
        return OpenTrackReceiver.parse(packet(x * 100, y * 100, z * 100, yaw, pitch, roll), 48, nanos);
    }

    private static void filterKeepsThePositionInsideItsLimits() {
        PoseFilter filter = new PoseFilter();
        filter.update(sample(1, 1, -1, 10, 20, 30, 1L), false, 1f / 60f);
        check(near(filter.x(), 0.30f) && near(filter.y(), 0.20f) && near(filter.z(), -0.40f),
                "the forward lean, negative z, gets the large allowance");
        check(filter.yaw() == 10f && filter.pitch() == 20f && filter.roll() == 30f, "the first pose is taken as it is");
        filter.reset();
        filter.update(sample(-1, -1, 1, 0, 0, 0, 2L), false, 1f / 60f);
        check(near(filter.x(), -0.30f) && near(filter.y(), -0.20f) && near(filter.z(), 0.10f),
                "the backward lean, positive z, gets the small one");
    }

    private static void filterSmoothsARemoteConnectionAndTurnsTheShortWay() {
        check(PoseFilter.effectiveSmoothing(0.1f, 0.7f, false) == 0.1f && PoseFilter.effectiveSmoothing(0.1f, 0.7f, true) == 0.7f,
                "the connection selects the smoothing value");
        check(near(1f / 50f, (float) (-1.0 / 60 / Math.log(1 - PoseFilter.smoothingFactor(0f, 1f / 60f)))),
                "no smoothing is a 20 ms time constant");

        float[] yawAfter = new float[2];
        for (int remote = 0; remote < 2; remote++) {
            PoseFilter filter = new PoseFilter();
            filter.update(sample(0, 0, 0, 0, 0, 0, 1L), remote == 1, 1f / 60f);
            for (int frame = 0; frame < 3; frame++) {
                filter.update(sample(0, 0, 0, 30, 0, 0, 2L), remote == 1, 1f / 60f);
            }
            yawAfter[remote] = filter.yaw();
        }
        check(yawAfter[0] > yawAfter[1] && yawAfter[1] > 0f, "a remote connection is smoothed more than a local one");

        PoseFilter filter = new PoseFilter();
        filter.update(sample(0, 0, 0, 179, 0, 0, 1L), false, 1f / 60f);
        filter.update(sample(0, 0, 0, -179, 0, 0, 2L), false, 1f / 60f);
        check(Math.abs(filter.yaw()) > 178f, "yaw crosses the 180 degree seam the short way");
    }

    private static void filterEasesBackWhenSamplesStop() {
        PoseFilter filter = new PoseFilter();
        filter.update(sample(0, 0, 0, 0, 0, 0, 1L), false, 0.02f);
        OpenTrackReceiver.Sample last = sample(0, 0, 0, 10, 0, 0, 2L);
        for (int frame = 0; frame < 200; frame++) {
            filter.update(last, false, 0.02f);
        }
        check(near(filter.yaw(), 10f), "a feed that stops leaves the view at the last pose it sent, not past it");
    }

    private static void leanClampTightensAtOnceAndReleasesSlowly() {
        LeanClamp clamp = new LeanClamp();
        float[] room = {1f};
        LeanClamp.Room query = (x, y, up, reach) -> Math.min(room[0], reach);

        float[] offset = {0.3f, 0f, 0f};
        check(near(clamp.apply(offset, 1f / 60f, query), 0.3f) && !clamp.inContact(), "open ground gives the whole lean");

        room[0] = 0.1f;
        offset = new float[] {0.3f, 0f, 0f};
        check(near(clamp.apply(offset, 1f / 60f, query), 0.1f) && near(offset[0], 0.1f) && clamp.inContact(),
                "a wall stops the lean in the same frame");

        room[0] = 1f;
        offset = new float[] {0.3f, 0f, 0f};
        float released = clamp.apply(offset, 1f / 60f, query);
        check(released > 0.1f && released < 0.15f, "the lean opens back up gradually when the wall clears");
        for (int frame = 0; frame < 600; frame++) {
            offset = new float[] {0.3f, 0f, 0f};
            released = clamp.apply(offset, 1f / 60f, query);
        }
        check(near(released, 0.3f) && !clamp.inContact(), "and gets all the way back");
    }

    private static void gridTraceFindsTheFirstBlockedEdge() {
        GridTrace trace = new GridTrace();
        GridTrace.Edges wallAtXFive = (fromX, fromY, toX, toY) -> fromX == 4 && toX == 5 || fromX == 5 && toX == 4;
        check(near(trace.firstBlockedEdge(wallAtXFive, 4.25f, 0.5f, 1f, 0f, 2f), 0.75f) && trace.hitFacesX,
                "a wall across the ray is met at its distance");
        check(trace.firstBlockedEdge(wallAtXFive, 4.25f, 0.5f, 1f, 0f, 0.5f) == Float.POSITIVE_INFINITY,
                "a wall past the reach is not met");
        check(trace.firstBlockedEdge(wallAtXFive, 4.25f, 0.5f, 0f, 1f, 10f) == Float.POSITIVE_INFINITY,
                "a ray along the wall never crosses it");
        float diagonal = (float) Math.sqrt(0.5);
        check(near(trace.firstBlockedEdge(wallAtXFive, 4.25f, 0.5f, diagonal, diagonal, 5f), 0.75f / diagonal),
                "a ray at an angle meets it further along");
        check(near(trace.firstBlockedEdge(wallAtXFive, 5.5f, 0.5f, -1f, 0f, 2f), 0.5f), "and from the other side");
    }

    private static void shotStopsOnACharacter() {
        ViewCamera.Pose aim = cleanPose(0f, 0f, 1.6f, 0f, 0f);
        check(near(WorldQuery.cylinderDistance(aim, 5f, 0f, 0f), 5f - WorldQuery.CHARACTER_RADIUS), "a figure dead ahead is hit on its near side");
        check(WorldQuery.cylinderDistance(aim, 5f, 1f, 0f) == Float.POSITIVE_INFINITY, "a figure beside the aim is missed");
        check(WorldQuery.cylinderDistance(aim, -5f, 0f, 0f) == Float.POSITIVE_INFINITY, "a figure behind is missed");
        ViewCamera.Pose over = cleanPose(0f, 0f, 1.6f, 0f, radians(20));
        check(WorldQuery.cylinderDistance(over, 5f, 0f, 0f) == Float.POSITIVE_INFINITY, "an aim over its head is missed");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-4f;
    }

    private static void cameraLooksAlongItsYaw() {
        float[] ahead = project(cleanView(3f, 7f, 1.6f, 0f, 0f), 13f, 7f, 1.6f);
        check(near(ahead[0], 0f) && near(ahead[1], 0f), "a point straight ahead along +x is at the screen centre");
        check(project(cleanView(3f, 7f, 1.6f, 0f, 0f), -7f, 7f, 1.6f) == null, "a point behind the eye is not drawn");

        float yaw = (float) (Math.PI / 2);
        float[] aheadY = project(cleanView(3f, 7f, 1.6f, yaw, 0f), 3f, 17f, 1.6f);
        check(near(aheadY[0], 0f) && near(aheadY[1], 0f), "at a quarter turn the camera looks along +y");
    }

    private static void cameraPutsPlusYOnTheRightWhenFacingPlusX() {
        float[] view = cleanView(0f, 0f, 1.6f, 0f, 0f);
        check(project(view, 10f, 2f, 1.6f)[0] > 0f, "+y is on the right when facing +x");
        check(project(view, 10f, -2f, 1.6f)[0] < 0f, "-y is on the left when facing +x");
    }

    private static void cameraPutsHigherPointsHigher() {
        float[] view = cleanView(0f, 0f, 1.6f, 0f, 0f);
        check(project(view, 10f, 0f, 3f)[1] > 0f, "a point above eye height is in the top half");
        check(project(view, 10f, 0f, 0f)[1] < 0f, "the ground is in the bottom half");
    }

    private static void cameraPitchLooksUp() {
        float[] view = cleanView(0f, 0f, 1.6f, 0f, (float) Math.toRadians(20));
        check(project(view, 10f, 0f, 1.6f)[1] < 0f, "looking up moves the horizon down the screen");
    }

    private static void cameraTurnsRightWithPositiveYaw() {
        float[] view = cleanView(0f, 0f, 1.6f, (float) Math.toRadians(20), 0f);
        check(project(view, 10f, 0f, 1.6f)[0] < 0f, "turning right moves what was ahead to the left");
    }

    private static void receiverRefusesAPoseNoTrackerSends() throws Exception {
        Log.open(Files.createTempDirectory("zomboid-headtracking-test"));
        ByteBuffer wire = ByteBuffer.allocate(OpenTrackReceiver.PACKET_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        double limit = OpenTrackReceiver.MAX_MAGNITUDE;
        for (int channel = 0; channel < OpenTrackReceiver.PACKET_BYTES / Double.BYTES; channel++) {
            for (double sign : new double[] {1, -1}) {
                wire.putDouble(channel * Double.BYTES, sign * limit);
                check(OpenTrackReceiver.parse(wire.array(), OpenTrackReceiver.PACKET_BYTES, 1L) != null,
                        "value " + channel + " is taken at the limit");
                // Finite as floats, and two of them a float's range apart overflow the step between them.
                for (double tooFar : new double[] {limit * 1.01, 3e38}) {
                    wire.putDouble(channel * Double.BYTES, sign * tooFar);
                    check(OpenTrackReceiver.parse(wire.array(), OpenTrackReceiver.PACKET_BYTES, 1L) == null,
                            "value " + channel + " is dropped at " + sign * tooFar);
                }
            }
            wire.putDouble(channel * Double.BYTES, 0);
        }

        OpenTrackReceiver receiver = new OpenTrackReceiver(4242, 500);
        int pitch = 4 * Double.BYTES;
        wire.putDouble(pitch, 5);
        receiver.accept(wire.array(), OpenTrackReceiver.PACKET_BYTES, InetAddress.getByName("127.0.0.1"), System.nanoTime());
        OpenTrackReceiver.Sample kept = receiver.latest();
        check(kept != null && kept.pitch() == 5f, "a pose a tracker sends is taken");
        wire.putDouble(pitch, -3e38);
        receiver.accept(wire.array(), OpenTrackReceiver.PACKET_BYTES, InetAddress.getByName("192.168.1.20"), System.nanoTime());
        check(receiver.latest() == kept && !receiver.isRemote(), "a pose out of range changes neither the pose nor the connection's locality");
    }

    private static void windowIsCentredInItsWorkArea() {
        check(WindowPlacement.centredOrigin(0, 5120, 1936) == 1592, "a window is centred across a work area at the origin");
        check(WindowPlacement.centredOrigin(0, 1392, 1119) == 136, "a window is centred down a work area a taskbar shortens");
        check(WindowPlacement.centredOrigin(-1920, 1920, 1296) == -1608, "a monitor left of the primary has a negative origin");
        check(WindowPlacement.centredOrigin(40, 1040, 759) == 180, "a work area below a top taskbar starts below it");
    }

    /** A 128x256 frame at tile scale 2 held whole in a texture, as the game's doubled tiles are. */
    private static SpriteGeometry.Frame wholeFrame() {
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        frame.scale = 2f;
        frame.originU = 64f;
        frame.originV = 192f;
        frame.left = 0f;
        frame.top = 0f;
        frame.width = 128f;
        frame.height = 256f;
        frame.uStart = 0f;
        frame.vStart = 0f;
        frame.uEnd = 1f;
        frame.vEnd = 1f;
        return frame;
    }

    /** A face takes its texels from a little inside its edge, so a corner is near its texel, not on it. */
    private static final float TEXEL_SLACK = 0.03f;

    private static boolean hasVertex(SpriteGeometry.Mesh mesh, float x, float y, float up, float u, float v) {
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float[] f = mesh.vertices;
            if (near(f[at], x) && near(f[at + 1], y) && near(f[at + 2], up)
                    && Math.abs(f[at + 3] - u) < TEXEL_SLACK && Math.abs(f[at + 4] - v) < TEXEL_SLACK) {
                return true;
            }
        }
        return false;
    }

    private static void floorFaceShowsTheSpritesDiamond() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        int triangles = new SpriteGeometry().addFace(mesh, wholeFrame(), 10, 20, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f);
        check(triangles == 2, "a floor inside the frame is two triangles");
        check(hasVertex(mesh, 10f, 20f, 0f, 0.5f, 0.75f), "the tile's own corner is the top of the diamond");
        check(hasVertex(mesh, 11f, 20f, 0f, 1f, 0.875f), "the +x corner is the right of the diamond");
        check(hasVertex(mesh, 11f, 21f, 0f, 0.5f, 1f), "the far corner is the bottom of the diamond");
        check(hasVertex(mesh, 10f, 21f, 0f, 0f, 0.875f), "the +y corner is the left of the diamond");
    }

    private static void wallFaceIsClippedToTheTrimmedSprite() {
        SpriteGeometry.Frame frame = wholeFrame();
        frame.top = 128f;
        frame.height = 128f;
        frame.vStart = 0.5f;
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        new SpriteGeometry().addFace(mesh, frame, 0, 0, 1, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);
        check(mesh.triangles() > 0, "a wall that overlaps the trimmed sprite is drawn");
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            check(mesh.vertices[at + 4] >= 0.5f - 1e-4f, "no vertex reaches above the trimmed sprite");
            check(near(mesh.vertices[at], 0f), "a west wall stays on the tile's west edge");
        }
        check(hasVertex(mesh, 0f, 0f, ViewCamera.LEVEL_HEIGHT * (1f + 64f / 192f), 0.5f, 0.5f),
                "the cut is along the sprite's top row: a sprite that stops well short of the face is not stretched");
    }

    private static void faceOutsideTheTrimmedSpriteAddsNothing() {
        SpriteGeometry.Frame frame = wholeFrame();
        frame.left = 64f;
        frame.width = 64f;
        frame.uStart = 0.5f;
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        int triangles = new SpriteGeometry().addFace(mesh, frame, 0, 0, 0, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f);
        check(triangles == 0 && mesh.floats == 0, "a west wall draws nothing from a sprite that only has a right half");
    }

    private static void uprightSpriteStandsOnItsAnchor() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        new SpriteGeometry().addUpright(mesh, wholeFrame(), 4, 6, 0, 0.5f, 0.5f, 0f, 0f, 1f, false);
        check(mesh.triangles() == 2, "an upright sprite is two triangles");
        float halfWidth = (float) (Math.sqrt(2.0) / 2.0);
        check(hasVertex(mesh, 4.5f, 6.5f - halfWidth, ViewCamera.LEVEL_HEIGHT * 224f / 192f, 0f, 0f),
                "the frame's top left is half a tile diagonal to the left and 224 pixels up");
        check(hasVertex(mesh, 4.5f, 6.5f + halfWidth, -ViewCamera.LEVEL_HEIGHT * 32f / 192f, 1f, 1f),
                "the frame's bottom right is below the anchor by the rows under it");
    }

    private static final ClassDesc TESTS = ClassDesc.of("com.cameraunlock.zomboid.Tests");
    private static final CallSite.Method RUN = new CallSite.Method("com/cameraunlock/zomboid/Subject", "run", "()V");
    private static final CallSite.Method TARGET = new CallSite.Method("com/cameraunlock/zomboid/Tests", "target", "()V");
    private static final CallSite.Method HOOK = new CallSite.Method("com/cameraunlock/zomboid/Tests", "hook", "()V");

    private static byte[] subjectCallingTarget(int times) {
        return subjectCallingTarget("Subject", times);
    }

    private static byte[] subjectCallingTarget(String name, int times) {
        MethodTypeDesc noArgs = MethodTypeDesc.of(ConstantDescs.CD_void);
        return ClassFile.of().build(ClassDesc.of("com.cameraunlock.zomboid." + name), type ->
                type.withFlags(ClassFile.ACC_PUBLIC).withMethodBody("run", noArgs, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
                    for (int i = 0; i < times; i++) {
                        code.invokestatic(TESTS, "target", noArgs);
                    }
                    code.return_();
                }));
    }

    private static void insertAfterAddsTheHookOnce() throws Throwable {
        byte[] hooked = CallSite.insertAfter(subjectCallingTarget(1), RUN, TARGET, HOOK);
        Class<?> subject = MethodHandles.lookup().defineClass(hooked);
        hookCalls = 0;
        subject.getMethod("run").invoke(null);
        check(hookCalls == 1, "the hook runs once per call of the hooked method");
    }

    private static void insertAfterRejectsAMissingCall() {
        try {
            CallSite.insertAfter(subjectCallingTarget(0), RUN, TARGET, HOOK);
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("a method without the call is rejected");
    }

    private static void insertAfterRejectsTwoCalls() {
        try {
            CallSite.insertAfter(subjectCallingTarget(2), RUN, TARGET, HOOK);
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("a method with the call twice is rejected");
    }

    private static void insertAfterEachAddsTheHookAfterEveryCall() throws Throwable {
        byte[] hooked = CallSite.insertAfterEach(subjectCallingTarget("SubjectCallingTwice", 2), RUN, TARGET, HOOK, 2);
        Class<?> subject = MethodHandles.lookup().defineClass(hooked);
        hookCalls = 0;
        subject.getMethod("run").invoke(null);
        check(hookCalls == 2, "the hook runs after each of the two calls");
        boolean refused = false;
        try {
            CallSite.insertAfterEach(subjectCallingTarget(1), RUN, TARGET, HOOK, 2);
        } catch (IllegalStateException expected) {
            refused = true;
        }
        check(refused, "a method with fewer calls than expected is rejected");
    }

    public static boolean opposite(boolean value) {
        return !value;
    }

    private static void returnedBooleanGoesThroughTheFilter() throws Throwable {
        MethodTypeDesc booleanToBoolean = MethodTypeDesc.of(ConstantDescs.CD_boolean, ConstantDescs.CD_boolean);
        byte[] subject = ClassFile.of().build(ClassDesc.of("com.cameraunlock.zomboid.SubjectReturningBoolean"), type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withMethodBody("same", booleanToBoolean, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code ->
                        code.iload(0).ifThenElse(yes -> yes.iconst_1().ireturn(), no -> no.iconst_0().ireturn())));
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/SubjectReturningBoolean", "same", "(Z)Z");
        CallSite.Method filter = new CallSite.Method("com/cameraunlock/zomboid/Tests", "opposite", "(Z)Z");
        Class<?> defined = MethodHandles.lookup().defineClass(CallSite.filterReturn(subject, within, filter));
        Method same = defined.getMethod("same", boolean.class);
        check(Boolean.FALSE.equals(same.invoke(null, true)) && Boolean.TRUE.equals(same.invoke(null, false)),
                "every return of the method hands back what the filter made of its value");
        boolean refused = false;
        try {
            CallSite.filterReturn(subject, new CallSite.Method(within.owner(), "missing", "(Z)Z"), filter);
        } catch (IllegalStateException expected) {
            refused = true;
        }
        check(refused, "a class without the method is rejected");
    }

    private static void attackHoldsTheAimForAShortTail() {
        long start = Long.MAX_VALUE - AimInput.TAIL_NANOS / 2, frame = 16_000_000L;
        AimInput aim = new AimInput();
        check(!aim.held(false, false, start), "nothing pressed is no aim");
        check(aim.held(true, false, start), "the game's own aim input is passed on");
        check(aim.held(false, true, start), "the attack button holds the aim");
        check(aim.held(false, false, start + frame), "the aim stays held on the frame after a one frame click");
        check(aim.held(false, false, start + AimInput.TAIL_NANOS - 1), "and to the end of the tail, across the clock's wrap");
        check(!aim.held(false, false, start + AimInput.TAIL_NANOS), "and no longer");
        aim.held(false, true, start);
        aim.held(false, true, start + 10 * frame);
        check(aim.held(false, false, start + 10 * frame + AimInput.TAIL_NANOS - 1), "the tail runs from the last frame the button was down");
        aim.held(false, true, start);
        aim.reset();
        check(!aim.held(false, false, start + frame), "a tail that was reset holds nothing");
        check(aim.held(true, false, start + frame), "but the game's own aim input still does");
    }

    private static void zoomScalesWhatMovesAcrossThePicture() {
        float base = (float) Math.tan(Math.toRadians(65) / 2), aimed = base * FieldOfView.AIMED_ZOOM;
        check(ZoomCompensation.factor(base, base) == 1f, "unzoomed, the factor is exactly 1");
        float factor = ZoomCompensation.factor(aimed, base);
        check(near(factor, FieldOfView.AIMED_ZOOM), "zoomed, it is the ratio of the tangents");
        check(ZoomCompensation.scaleAngle(radians(10), 1f) == radians(10), "an angle is untouched unzoomed");
        for (float degrees : new float[] {-170f, -100f, 100f, 170f}) {
            check(near(ZoomCompensation.scaleAngle(radians(degrees), 1f), radians(degrees)),
                    "a head turned " + degrees + " degrees is left there unzoomed, not put a half turn away");
            float zoomed = ZoomCompensation.scaleAngle(radians(degrees), factor);
            check(Math.signum(zoomed) == Math.signum(degrees) && Math.abs(zoomed) > radians(90),
                    "and stays past the right angle on its own side when zoomed");
        }

        // A head yaw puts a point that was dead ahead off to one side. Where it lands on
        // screen has to be the same zoomed and unzoomed.
        ViewCamera.Pose clean = cleanPose(3f, 4f, 1.6f, radians(30), 0f);
        float aheadX = clean.eyeX + clean.forwardX * 8f, aheadY = clean.eyeY + clean.forwardY * 8f;
        float[] hip = new float[2], sights = new float[2];
        for (float degrees : new float[] {-40f, -10f, 5f, 25f}) {
            ViewCamera.Pose turned = new ViewCamera.Pose();
            turned.set(clean);
            ViewCamera.turn(turned, radians(degrees), 0f, 0f, true);
            ViewCamera.project(turned, base, ASPECT, aheadX, aheadY, clean.eyeUp, hip);
            turned.set(clean);
            ViewCamera.turn(turned, ZoomCompensation.scaleAngle(radians(degrees), factor), 0f, 0f, true);
            ViewCamera.project(turned, aimed, ASPECT, aheadX, aheadY, clean.eyeUp, sights);
            check(near(hip[0], sights[0]), "a head yaw of " + degrees + " degrees moves the picture as far zoomed as unzoomed");
        }

        // Looking along +x: a lean of 0.2 along the view, 0.3 across it and 0.1 up.
        float[] lean = {0.2f, 0.3f, 0.1f};
        ZoomCompensation.scaleLean(lean, 1f, 0f, 0f, 0.5f);
        check(near(lean[0], 0.2f), "the lean along the view is left whole");
        check(near(lean[1], 0.15f) && near(lean[2], 0.05f), "the lean across the view is scaled");
        float[] slanted = {0.3f, 0.3f, 0f};
        float diagonal = (float) Math.sqrt(0.5);
        ZoomCompensation.scaleLean(slanted, diagonal, diagonal, 0f, 0.5f);
        check(near(slanted[0], 0.3f) && near(slanted[1], 0.3f), "a lean straight along a slanted view is left whole");
        float[] across = {0.3f, -0.3f, 0f};
        ZoomCompensation.scaleLean(across, diagonal, diagonal, 0f, 0.5f);
        check(near(across[0], 0.15f) && near(across[1], -0.15f), "and one straight across it is scaled whole");
        float[] same = {0.2f, 0.3f, 0.1f};
        ZoomCompensation.scaleLean(same, 0.6f, 0f, 0.8f, 1f);
        check(near(same[0], 0.2f) && near(same[1], 0.3f) && near(same[2], 0.1f), "unzoomed, a lean is untouched");
    }

    private static void fieldOfViewEasesToTheAimedZoomAndBack() {
        FieldOfView.rest();
        float set = FieldOfView.setTanHalfVertical();
        check(FieldOfView.zoom() == 1f && FieldOfView.tanHalfVertical() == set, "unaimed, the picture is drawn at the field of view that was set");
        FieldOfView.ease(false, 0.016f);
        check(FieldOfView.zoom() == 1f, "and stays there");
        FieldOfView.ease(true, FieldOfView.ZOOM_SECONDS);
        float afterOne = FieldOfView.zoom();
        check(near(afterOne, FieldOfView.AIMED_ZOOM + (1f - FieldOfView.AIMED_ZOOM) / (float) Math.E),
                "one time constant covers all but 1/e of the way");
        FieldOfView.rest();
        for (int i = 0; i < 7; i++) {
            FieldOfView.ease(true, FieldOfView.ZOOM_SECONDS / 7f);
        }
        check(near(FieldOfView.zoom(), afterOne), "the easing does not depend on the frame rate");
        for (int i = 0; i < 120; i++) {
            FieldOfView.ease(true, 0.016f);
        }
        check(FieldOfView.zoom() == FieldOfView.AIMED_ZOOM, "it settles on the aimed zoom exactly");
        check(near(FieldOfView.tanHalfVertical(), set * FieldOfView.AIMED_ZOOM), "which narrows the tangent the picture is drawn with");
        check(near(FieldOfView.setTanHalfVertical(), set), "and leaves the field of view that was set");
        FieldOfView.ease(false, 0.016f);
        check(FieldOfView.zoom() > FieldOfView.AIMED_ZOOM && FieldOfView.zoom() < 1f, "lowering the sights eases back, never at once");
        for (int i = 0; i < 120; i++) {
            FieldOfView.ease(false, 0.016f);
        }
        check(FieldOfView.zoom() == 1f, "and settles on exactly 1");
        FieldOfView.ease(true, 0.016f);
        FieldOfView.rest();
        check(FieldOfView.zoom() == 1f, "with no picture drawn it is unzoomed at once");
    }

    private static void firstPersonKeysReplaceTheStockOnes() {
        int shift = KeySubstitutions.LEFT_SHIFT, alt = KeySubstitutions.LEFT_ALT, control = KeySubstitutions.LEFT_CONTROL;
        int rightMouse = KeySubstitutions.RIGHT_MOUSE, c = KeySubstitutions.C, x = 45;
        check(new KeySubstitutions.Keys(shift, 0).equals(KeySubstitutions.substitute("Sprint", alt, 0)), "Left Shift sprints");
        check(new KeySubstitutions.Keys(alt, 0).equals(KeySubstitutions.substitute("Run", shift, 0)), "the jog moves to Left Alt");
        check(KeySubstitutions.substitute("Sprint", shift, 0) == null, "a sprint already on Left Shift is the player's own binding");
        check(new KeySubstitutions.Keys(rightMouse, 0).equals(KeySubstitutions.substitute("Aim", control, rightMouse)),
                "the aim gives up Left Ctrl and keeps the right mouse button");
        check(new KeySubstitutions.Keys(x, 0).equals(KeySubstitutions.substitute("Aim", x, control)), "wherever Left Ctrl was");
        check(new KeySubstitutions.Keys(rightMouse, 0).equals(KeySubstitutions.substitute("Aim", control, 0)),
                "an aim that was on Left Ctrl alone goes to the right mouse button");
        check(KeySubstitutions.substitute("Aim", x, rightMouse) == null, "an aim that was never on Left Ctrl is left alone");
        check(new KeySubstitutions.Keys(c, control).equals(KeySubstitutions.substitute("Crouch", c, 0)), "C and Left Ctrl crouch");
        check(new KeySubstitutions.Keys(x, control).equals(KeySubstitutions.substitute("Crouch", x, 0)),
                "a crouch the player moved keeps its key and gains Left Ctrl");
        check(new KeySubstitutions.Keys(c, control).equals(KeySubstitutions.substitute("Crouch", 0, 0)), "an unbound crouch gets both");
        check(KeySubstitutions.substitute("Crouch", control, 0) == null, "a crouch already on Left Ctrl is left alone");
        for (String vanilla : new String[] {"ReloadWeapon", "Melee", "Interact", "Attack/Click", "Hotbar 1",
                "Equip/Turn On/Off Light Source", "Toggle Inventory", "Rack Firearm", "ManualFloorAtk"}) {
            check(KeySubstitutions.substitute(vanilla, 19, 0) == null, vanilla + " is left as the player has it");
        }
        for (String name : KeySubstitutions.NAMES) {
            check(KeySubstitutions.substitute(name, 0, 0) != null, name + " is named as substituted and is");
        }
    }

    private static void pointerComesOutForTheInventoryKeyAPanelAndAHeldButton() {
        PointerMode.Inventory leave = PointerMode.Inventory.LEAVE, open = PointerMode.Inventory.OPEN, close = PointerMode.Inventory.CLOSE;
        PointerMode pointer = new PointerMode();
        check(pointer.update(false, false, false, 0) == leave && !pointer.on(), "the look has the mouse to begin with");
        check("false".equals(pointer.describe()), "and the trace says so");

        check(pointer.update(true, false, false, 0) == open && pointer.on() && pointer.byKey(),
                "the inventory key opens the inventory and brings the pointer out");
        check("true key".equals(pointer.describe()), "for the key");
        check(pointer.update(false, false, false, 0) == leave && pointer.on(), "and it stays out");
        check(pointer.update(true, false, false, 0) == close && !pointer.on(), "the key again closes the inventory and goes back to the look");

        pointer.update(true, false, false, 0);
        check(pointer.update(false, true, false, 0) == close && !pointer.on(), "Escape closes it too");
        check(pointer.update(false, true, false, 0) == leave && !pointer.on(), "and Escape with the inventory shut is the game's own");

        check(pointer.update(false, false, true, 0) == leave && pointer.on(), "the held button brings the pointer out and leaves the inventory alone");
        check("true held".equals(pointer.describe()), "for being held");
        pointer.update(false, false, true, 1);
        check("true panel+held".equals(pointer.describe()), "a menu opened from it keeps the pointer");
        check(pointer.update(false, false, false, 1) == leave && pointer.on(), "after the button is let go");
        check(pointer.update(false, false, false, 0) == leave && !pointer.on(), "until the menu closes");

        pointer.update(false, false, false, 2);
        check(pointer.on(), "two panels open");
        pointer.update(false, false, false, 1);
        check(pointer.on(), "one of them closed");
        check(pointer.update(false, true, false, 1) == leave && pointer.on(), "Escape does not take the pointer from a panel");
        check(pointer.update(true, false, false, 1) == open && "true key+panel".equals(pointer.describe()), "the inventory opens beside a panel");
        check(pointer.update(true, false, false, 1) == close && !pointer.on(), "closing the inventory goes back to the look past the panel left open");
        pointer.update(false, false, false, 1);
        check(!pointer.on(), "which no longer brings the pointer out");
        pointer.update(false, false, false, 2);
        check(pointer.on(), "another panel opening does");
        pointer.update(false, false, false, 1);
        check(!pointer.on(), "until it closes");
        pointer.update(false, false, false, 0);
        pointer.update(false, false, false, 1);
        check(pointer.on(), "a panel closed and opened again brings the pointer out again");

        pointer.update(true, false, false, 1);
        pointer.reset();
        check(!pointer.on() && !pointer.byKey(), "turning the view off puts everything back");
        check(pointer.update(false, false, false, 3) == leave && !pointer.on(), "panels already open when the view comes on do not take the look");
        pointer.update(false, false, false, 4);
        check(pointer.on(), "one more does");
    }

    private static byte[] subjectWithField(String name, ClassDesc type, int flags) {
        return ClassFile.of().build(ClassDesc.of("com.cameraunlock.zomboid.Subject"), subject ->
                subject.withFlags(ClassFile.ACC_PUBLIC).withField(name, type, flags));
    }

    private static boolean staticIntAccepted(byte[] classBytes, String field) {
        try {
            CallSite.requireStaticInt(classBytes, field);
            return true;
        } catch (IllegalStateException rejected) {
            return false;
        }
    }

    private static void staticIntFieldIsRequiredAsDeclared() {
        int publicStatic = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;
        if (!staticIntAccepted(subjectWithField("x", ConstantDescs.CD_int, publicStatic), "x")) {
            throw new AssertionError("a static int field is accepted");
        }
        if (staticIntAccepted(subjectWithField("column", ConstantDescs.CD_int, publicStatic), "x")) {
            throw new AssertionError("a class without the field is rejected");
        }
        if (staticIntAccepted(subjectWithField("x", ConstantDescs.CD_float, publicStatic), "x")) {
            throw new AssertionError("a field of another type is rejected");
        }
        if (staticIntAccepted(subjectWithField("x", ConstantDescs.CD_int, ClassFile.ACC_PUBLIC), "x")) {
            throw new AssertionError("an instance field is rejected");
        }
        if (staticIntAccepted(subjectWithField("x", ConstantDescs.CD_int, publicStatic | ClassFile.ACC_FINAL), "x")) {
            throw new AssertionError("a final field is rejected");
        }
    }

    private static Agent.Patch hookAfterTarget(String owner) {
        return new Agent.Patch(owner, "run after target", classBytes -> CallSite.insertAfter(classBytes, RUN, TARGET, HOOK));
    }

    private static void transformerHasNoGapOnceEveryPatchIsIn() throws Throwable {
        Log.open(Files.createTempDirectory("zomboid-headtracking-test"));
        Agent.PatchTransformer transformer = new Agent.PatchTransformer(List.of(hookAfterTarget(RUN.owner())));
        if (transformer.transform(null, "com/cameraunlock/zomboid/Other", null, null, subjectCallingTarget(1)) != null) {
            throw new AssertionError("a class no patch names is left alone");
        }
        byte[] hooked = transformer.transform(null, RUN.owner(), null, null, subjectCallingTarget(1));
        if (hooked == null) {
            throw new AssertionError("the class a patch names is rewritten");
        }
        if (transformer.gap() != null) {
            throw new AssertionError("no gap once the one patch is in, got: " + transformer.gap());
        }
    }

    private static void exceptionTheGameCaughtFromTheModIsWrittenOnce() throws Exception {
        Path directory = Files.createTempDirectory("zomboid-headtracking-test");
        Log.open(directory);
        RuntimeException theGames = new RuntimeException("the game's own");
        theGames.setStackTrace(new StackTraceElement[] {new StackTraceElement("zombie.gameStates.IngameState", "render", "IngameState.java", 1)});
        Log.caughtByTheGame(theGames);
        for (int frame = 0; frame < 3; frame++) {
            RuntimeException fromTheMod = new RuntimeException("thrown in a hook");
            fromTheMod.setStackTrace(new StackTraceElement[] {
                    new StackTraceElement("com.cameraunlock.zomboid.FirstPerson", "afterWorldRender", "FirstPerson.java", 1),
                    new StackTraceElement("zombie.gameStates.IngameState", "renderFrameInternal", "IngameState.java", 1)});
            Log.caughtByTheGame(new IllegalStateException("wrapped by the game", fromTheMod));
        }
        String written = Files.readString(directory.resolve(Log.FILE_NAME));
        check(!written.contains("the game's own"), "an exception none of which came through the mod is left to the game's log");
        check(written.split("the game caught this from the mod", -1).length == 2, "a fault that comes every frame is written once, got: " + written);
        check(written.contains("thrown in a hook") && written.contains("FirstPerson.afterWorldRender"), "it is written with its cause and where in the mod it was thrown");
    }

    private static String takenByTheHook;

    public static void takes(String argument) {
        takenByTheHook = argument;
    }

    private static void hookIsGivenAStaticMethodsFirstArgument() throws Throwable {
        ClassDesc string = ConstantDescs.CD_String;
        MethodTypeDesc take = MethodTypeDesc.of(ConstantDescs.CD_void, string, string);
        byte[] subject = ClassFile.of().build(ClassDesc.of("com.cameraunlock.zomboid.SubjectWithStaticMethod"), type -> type
                .withFlags(ClassFile.ACC_PUBLIC)
                .withMethodBody("take", take, ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> code.return_()));
        CallSite.Method within = new CallSite.Method("com/cameraunlock/zomboid/SubjectWithStaticMethod", "take", "(Ljava/lang/String;Ljava/lang/String;)V");
        CallSite.Method hook = new CallSite.Method("com/cameraunlock/zomboid/Tests", "takes", "(Ljava/lang/String;)V");
        byte[] hooked = CallSite.insertBeforeReturns(subject, within, code -> CallSite.passFirstStaticArgument(code, hook));
        MethodHandles.lookup().defineClass(hooked).getMethod("take", String.class, String.class).invoke(null, "first", "second");
        check("first".equals(takenByTheHook), "the hook is given the first argument of a static method, got: " + takenByTheHook);
    }

    private static void transformerLeavesAClassItCannotPatchAloneAndNamesTheGap() throws Throwable {
        Log.open(Files.createTempDirectory("zomboid-headtracking-test"));
        // Two patches on a class that is really loaded, so the gap is not "no class". The first
        // fits and the second does not: nothing that went in before makes up for the one missing.
        String loaded = "com/cameraunlock/zomboid/Tests";
        Agent.Patch fits = hookAfterTarget(loaded);
        Agent.Patch doesNotFit = new Agent.Patch(loaded, "missing after target", classBytes ->
                CallSite.insertAfter(classBytes, new CallSite.Method(RUN.owner(), "missing", "()V"), TARGET, HOOK));
        Agent.PatchTransformer transformer = new Agent.PatchTransformer(List.of(fits, doesNotFit));
        byte[] hooked = transformer.transform(null, loaded, null, null, subjectCallingTarget(1));
        if (hooked == null) {
            throw new AssertionError("the patch that fits still goes in");
        }
        String gap = transformer.gap();
        if (gap == null || !gap.contains("missing after target")) {
            throw new AssertionError("the gap names the patch that is not in, got: " + gap);
        }

        Agent.PatchTransformer alone = new Agent.PatchTransformer(List.of(hookAfterTarget(loaded)));
        if (alone.transform(null, loaded, null, null, subjectCallingTarget(2)) != null) {
            throw new AssertionError("a class whose only patch does not fit is loaded unchanged");
        }
        if (alone.gap() == null) {
            throw new AssertionError("a patch that did not fit is a gap");
        }
    }

    private static void transformerNamesAClassTheGameDoesNotHave() {
        Agent.PatchTransformer transformer = new Agent.PatchTransformer(
                List.of(hookAfterTarget("com/cameraunlock/zomboid/NoSuchClass")));
        String gap = transformer.gap();
        if (gap == null || !gap.contains("no class com/cameraunlock/zomboid/NoSuchClass")) {
            throw new AssertionError("a class that cannot be found is a gap, got: " + gap);
        }
    }
}
