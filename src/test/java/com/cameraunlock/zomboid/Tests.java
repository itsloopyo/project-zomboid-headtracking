package com.cameraunlock.zomboid;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

public final class Tests {
    private static final FieldOfView fieldOfView = new FieldOfView();
    private static final Flight flight = new Flight();
    private static final MuzzleFlash muzzleFlash = new MuzzleFlash();
    private static final HeadFollowLight headFollowLight = new HeadFollowLight();

    public static int hookCalls;

    public static void hook() {
        hookCalls++;
    }

    public static void target() {}

    /** Where the log of the run is written. */
    private static Path logs;

    public static void main(String[] args) throws Throwable {
        // Under build, which each build clears: cameraunlock-core keeps the log open until the process ends.
        logs = Files.createDirectories(Path.of("build", "test-run").toAbsolutePath());
        Log.open(logs);
        // A start is in the Low graphics mode, which shows nothing the mod made. The tests of what it made are of High.
        Quality.set(Quality.HIGH);
        // And of a game in which the pictures the mod's own is drawn from have been made, which a test has no graphics card to make.
        Own.picturesMade(true);
        Checks.run("cameraLooksAlongItsYaw", Tests::cameraLooksAlongItsYaw);
        Checks.run("cameraPutsPlusYOnTheRightWhenFacingPlusX", Tests::cameraPutsPlusYOnTheRightWhenFacingPlusX);
        Checks.run("cameraPutsHigherPointsHigher", Tests::cameraPutsHigherPointsHigher);
        Checks.run("cameraPitchLooksUp", Tests::cameraPitchLooksUp);
        Checks.run("cameraTurnsRightWithPositiveYaw", Tests::cameraTurnsRightWithPositiveYaw);
        Checks.run("headYawTurnsTheViewRight", Tests::headYawTurnsTheViewRight);
        Checks.run("headPitchLooksUp", Tests::headPitchLooksUp);
        Checks.run("headRollTiltsTheViewLeft", Tests::headRollTiltsTheViewLeft);
        Checks.run("worldYawKeepsTheHorizonLevel", Tests::worldYawKeepsTheHorizonLevel);
        Checks.run("flightLeavesTheOverheadCameraAndLandsOnTheTrackedView", Tests::flightLeavesTheOverheadCameraAndLandsOnTheTrackedView);
        Checks.run("flightTakesItsTimeWhateverTheFrames", Tests::flightTakesItsTimeWhateverTheFrames);
        Checks.run("flightMixesThePicturesOnlyWhileTheCameraIsStill", Tests::flightMixesThePicturesOnlyWhileTheCameraIsStill);
        Checks.run("projectionAgreesWithTheViewMatrix", Tests::projectionAgreesWithTheViewMatrix);
        Checks.run("reticleStaysCentredUnderRollAndMovesStraightUnderPitch", Tests::reticleStaysCentredUnderRollAndMovesStraightUnderPitch);
        Checks.run("reticleFollowsThePointWhenTheEyeLeans", Tests::reticleFollowsThePointWhenTheEyeLeans);
        Checks.run("reticleFollowsTheFieldOfView", Tests::reticleFollowsTheFieldOfView);
        Checks.run("nearPlaneKeepsItsCornersInsideTheLeanSkin", Tests::nearPlaneKeepsItsCornersInsideTheLeanSkin);
        Checks.run("fieldOfViewSetIsTheOneDrawnWith", Tests::fieldOfViewSetIsTheOneDrawnWith);
        Checks.run("lightTakesTheHeadingOfItsAngles", Tests::lightTakesTheHeadingOfItsAngles);
        Checks.run("gridTraceFindsTheFirstBlockedEdge", Tests::gridTraceFindsTheFirstBlockedEdge);
        Checks.run("CabinTests", CabinTests::run);
        Checks.run("GlassPullTests", GlassPullTests::run);
        Checks.run("MoversTests", MoversTests::run);
        Checks.run("SnowPrintTests", SnowPrintTests::run);
        Checks.run("SnowCoverTests", SnowCoverTests::run);
        Checks.run("SnowOnPlantsTests", SnowOnPlantsTests::run);
        Checks.run("TorchLessTests", TorchLessTests::run);
        Checks.run("AimTests", AimTests::run);
        Checks.run("ArmTests", ArmTests::run);
        Checks.run("OwnArmTests", OwnArmTests::run);
        Checks.run("GripTests", GripTests::run);
        Checks.run("BufferTests", BufferTests::run);
        Checks.run("PictureTests", PictureTests::run);
        Checks.run("LinkTests", LinkTests::run);
        Checks.run("RoadPaintTests", RoadPaintTests::run);
        Checks.run("shotStopsOnACharacter", Tests::shotStopsOnACharacter);
        Checks.run("insertAfterAddsTheHookOnce", Tests::insertAfterAddsTheHookOnce);
        Checks.run("insertAfterRejectsAMissingCall", Tests::insertAfterRejectsAMissingCall);
        Checks.run("insertAfterRejectsTwoCalls", Tests::insertAfterRejectsTwoCalls);
        Checks.run("insertAfterEachAddsTheHookAfterEveryCall", Tests::insertAfterEachAddsTheHookAfterEveryCall);
        Checks.run("returnedBooleanGoesThroughTheFilter", Tests::returnedBooleanGoesThroughTheFilter);
        Checks.run("staticIntFieldIsRequiredAsDeclared", Tests::staticIntFieldIsRequiredAsDeclared);
        Checks.run("attackHoldsTheAimForAShortTail", Tests::attackHoldsTheAimForAShortTail);
        Checks.run("aFirearmsShotWaitsForTheAimToBear", Tests::aFirearmsShotWaitsForTheAimToBear);
        Checks.run("keysHeldThroughTheLandingKeepTheirWalk", Tests::keysHeldThroughTheLandingKeepTheirWalk);
        Checks.run("theGroundLosesItsLightWithTheSun", Tests::theGroundLosesItsLightWithTheSun);
        Checks.run("fieldOfViewEasesToTheAimedZoomAndBack", Tests::fieldOfViewEasesToTheAimedZoomAndBack);
        Checks.run("firstPersonKeysReplaceTheStockOnes", Tests::firstPersonKeysReplaceTheStockOnes);
        Checks.run("pointerComesOutForTheInventoryKeyAPanelAndAHeldButton", Tests::pointerComesOutForTheInventoryKeyAPanelAndAHeldButton);
        Checks.run("transformerHasNoGapOnceEveryPatchIsIn", Tests::transformerHasNoGapOnceEveryPatchIsIn);
        Checks.run("exceptionTheGameCaughtFromTheModIsWrittenOnce", Tests::exceptionTheGameCaughtFromTheModIsWrittenOnce);
        Checks.run("hookIsGivenAStaticMethodsFirstArgument", Tests::hookIsGivenAStaticMethodsFirstArgument);
        Checks.run("transformerLeavesAClassItCannotPatchAloneAndNamesTheGap", Tests::transformerLeavesAClassItCannotPatchAloneAndNamesTheGap);
        Checks.run("transformerNamesAClassTheGameDoesNotHave", Tests::transformerNamesAClassTheGameDoesNotHave);
        Checks.run("floorFaceShowsTheSpritesDiamond", Tests::floorFaceShowsTheSpritesDiamond);
        Checks.run("flatFloorTakesItsTexelsFromWhereItLies", Tests::flatFloorTakesItsTexelsFromWhereItLies);
        Checks.run("faceMadeTheShortWayIsTheFaceMadeTheLongWay", Tests::faceMadeTheShortWayIsTheFaceMadeTheLongWay);
        Checks.run("wallFaceIsClippedToTheTrimmedSprite", Tests::wallFaceIsClippedToTheTrimmedSprite);
        Checks.run("faceOutsideTheTrimmedSpriteAddsNothing", Tests::faceOutsideTheTrimmedSpriteAddsNothing);
        Checks.run("uprightSpriteStandsOnItsAnchor", Tests::uprightSpriteStandsOnItsAnchor);
        Checks.run("smoothNormalsAgreeAcrossASeam", Tests::smoothNormalsAgreeAcrossASeam);
        Checks.run("partsAreToldByTheirNames", Tests::partsAreToldByTheirNames);
        Checks.run("textureRunsDownAMeshTheSameEitherSideOfASeam", Tests::textureRunsDownAMeshTheSameEitherSideOfASeam);
        Checks.run("fringeThinsOutWithDistance", Tests::fringeThinsOutWithDistance);
        Checks.run("fineSkinFileIsNamedAfterTheGamesTexture", Tests::fineSkinFileIsNamedAfterTheGamesTexture);
        Checks.run("hookBeforeAReturnGetsTheResultAndTheFirstArgument", Tests::hookBeforeAReturnGetsTheResultAndTheFirstArgument);
        Checks.run("packedModelIsReadBack", Tests::packedModelIsReadBack);
        Checks.run("brokenModelFilesAreRefused", Tests::brokenModelFilesAreRefused);
        Checks.run("quarterTurnsCarryAModelsFrontRoundTheCompass", Tests::quarterTurnsCarryAModelsFrontRoundTheCompass);
        Checks.run("modelsAreLaidOutOneAfterAnotherForTheGraphicsCard", Tests::modelsAreLaidOutOneAfterAnotherForTheGraphicsCard);
        Checks.run("pieceIsPackedAsThePictureIsToldIt", Tests::pieceIsPackedAsThePictureIsToldIt);
        Checks.run("pieceStandsWhereTheModelTurnedScaledAndMovedIs", Tests::pieceStandsWhereTheModelTurnedScaledAndMovedIs);
        Checks.run("pieceCarriesTheLightOfItsSquaresCorners", Tests::pieceCarriesTheLightOfItsSquaresCorners);
        Checks.run("pieceIsHeldByItsBall", Tests::pieceIsHeldByItsBall);
        Checks.run("paintingOnAWallIsFoundWhereItHangs", Tests::paintingOnAWallIsFoundWhereItHangs);
        Checks.run("spriteDrawnForAnotherWallIsNoPainting", Tests::spriteDrawnForAnotherWallIsNoPainting);
        Checks.run("pictureLiesOverItsFrameAsTheSpritePaintsIt", Tests::pictureLiesOverItsFrameAsTheSpritePaintsIt);
        Checks.run("frameIsChosenByThePicturesShape", Tests::frameIsChosenByThePicturesShape);
        Checks.run("jarHoldsEveryModelStoodOnTheFloor", Tests::jarHoldsEveryModelStoodOnTheFloor);
        Checks.run("wallTintSurvivesItsPacking", Tests::wallTintSurvivesItsPacking);
        Checks.run("meshesAreKeptByTextureAndTint", Tests::meshesAreKeptByTextureAndTint);
        Checks.run("OutdoorTests", OutdoorTests::run);
        Checks.run("IndustryTests", IndustryTests::run);
        Checks.run("OverheadTests", OverheadTests::run);
        Checks.run("CampTests", CampTests::run);
        Checks.run("FarmTests", FarmTests::run);
        Checks.run("LeisureTests", LeisureTests::run);
        Checks.run("FurnitureTests", FurnitureTests::run);
        Checks.run("DisplayCaseTests", DisplayCaseTests::run);
        Checks.run("ClinicTests", ClinicTests::run);
        Checks.run("ShopFittingTests", ShopFittingTests::run);
        Checks.run("BuildingTests", BuildingTests::run);
        Checks.run("RoofTests", RoofTests::run);
        Checks.run("ShapeTests", ShapeTests::run);
        Checks.run("CrownTests", CrownTests::run);
        Checks.run("GrassTests", GrassTests::run);
        Checks.run("WindTests", WindTests::run);
        Checks.run("WindowTests", WindowTests::run);
        Checks.run("ReflectionTests", ReflectionTests::run);
        Checks.run("WaterMirrorTests", WaterMirrorTests::run);
        Checks.run("DecorTests", DecorTests::run);
        Checks.run("OwnTests", OwnTests::run);
        Checks.run("LowTests", LowTests::run);
        Checks.run("ScopeTests", ScopeTests::run);
        Checks.run("RifleTests", RifleTests::run);
        Checks.run("MuzzleFlashTests", MuzzleFlashTests::run);
        Checks.run("KerbTests", KerbTests::run);
        Checks.run("StairTests", StairTests::run);
        Checks.run("FlightTests", FlightTests::run);
        Checks.run("CutawayTests", CutawayTests::run);
        Checks.run("ShelterTests", ShelterTests::run);
        Checks.run("SightlineTests", SightlineTests::run);
        Checks.run("RoomSightTests", RoomSightTests::run);
        Checks.run("TerrainTests", TerrainTests::run);
        Checks.run("FootingTests", FootingTests::run);
        Checks.run("PavingTests", PavingTests::run);
        Checks.run("FacadeTests", FacadeTests::run);
        Checks.run("SpanTests", SpanTests::run);
        Checks.run("ParasolTests", ParasolTests::run);
        Checks.run("GroundItemTests", GroundItemTests::run);
        Checks.run("AwningTests", AwningTests::run);
        Checks.run("TentTests", TentTests::run);
        Checks.run("TowerTests", TowerTests::run);
        Checks.run("AerialTests", AerialTests::run);
        Checks.run("LightsTests", LightsTests::run);
        Checks.run("BannerTests", BannerTests::run);
        Checks.run("SignRunTests", SignRunTests::run);
        Checks.run("WallObjectTests", WallObjectTests::run);
        Checks.run("QualityTests", QualityTests::run);
        Checks.run("DrawDistanceTests", DrawDistanceTests::run);
        Checks.run("StyleTests", StyleTests::run);
        Checks.run("GameFixTests", GameFixTests::run);
        Checks.run("MarkTests", MarkTests::run);
        Checks.run("FarLandTests", FarLandTests::run);
        Checks.run("OnlineTests", OnlineTests::run);
        Checks.run("EyesightTests", EyesightTests::run);
        Checks.run("HearingTests", HearingTests::run);
        Checks.run("CapsuleTests", CapsuleTests::run);
        Checks.run("SplitScreenTests", SplitScreenTests::run);
        Checks.run("DoubleTapTests", DoubleTapTests::run);
        Checks.run("CrowdTests", CrowdTests::run);
        Checks.run("TailorTests", TailorTests::run);
        Checks.run("usualColourIsWhatMostOfTheSpriteIsPaintedIn", Tests::usualColourIsWhatMostOfTheSpriteIsPaintedIn);
        Checks.run("theBuildThatShipsHasNoDevelopmentKeys", Tests::theBuildThatShipsHasNoDevelopmentKeys);
        Checks.run("heldWeaponPointsAheadWithItsBarrelOnTheLineAsked", Tests::heldWeaponPointsAheadWithItsBarrelOnTheLineAsked);
        Checks.run("heldWeaponKeepsItsShapeAndTheGamesMirror", Tests::heldWeaponKeepsItsShapeAndTheGamesMirror);
        Checks.run("weaponWithoutAMuzzleIsHeldByItsGrip", Tests::weaponWithoutAMuzzleIsHeldByItsGrip);
        Checks.run("weaponWithNoFrameIsNotHeld", Tests::weaponWithNoFrameIsNotHeld);
        Checks.run("weaponTurnsAboutItsGrip", Tests::weaponTurnsAboutItsGrip);
        Checks.run("easingCoversAllButOneOverEInItsConstantAtAnyFrameRate", Tests::easingCoversAllButOneOverEInItsConstantAtAnyFrameRate);
        Checks.run("recoilIsOverInAHundredAndFiftyMilliseconds", Tests::recoilIsOverInAHundredAndFiftyMilliseconds);
        Checks.run("stepBobsTheWeaponDownAndAcross", Tests::stepBobsTheWeaponDownAndAcross);
        Checks.run("flashIsBrightestAtTheShotAndGoneAfterItsTime", Tests::flashIsBrightestAtTheShotAndGoneAfterItsTime);
        Checks.run("plantsStandWhereTheyStoodAndAreNeverTooMany", Tests::plantsStandWhereTheyStoodAndAreNeverTooMany);
        Checks.run("fallenLeavesAreAsDeepFromEitherChunkThatSharesACorner", Tests::fallenLeavesAreAsDeepFromEitherChunkThatSharesACorner);
        Checks.run("tuftCarriesHowWellItsGrassDoes", Tests::tuftCarriesHowWellItsGrassDoes);
        Checks.run("boleIsClosedAboveItsFoot", Tests::boleIsClosedAboveItsFoot);
        Checks.run("barkIsAsLargeRoundABoleAsUpIt", Tests::barkIsAsLargeRoundABoleAsUpIt);
        Checks.run("ivyKeepsToTheBoleAndSaysHowFarUpItIs", Tests::ivyKeepsToTheBoleAndSaysHowFarUpItIs);
        Checks.run("boulderIsClosed", Tests::boulderIsClosed);
        Checks.run("fernGrowsFromItsRoot", Tests::fernGrowsFromItsRoot);
        Checks.run("litterShowsAsMuchAsItsCornersSay", Tests::litterShowsAsMuchAsItsCornersSay);
        Checks.run("elbowKeepsBothBonesTheirLengthsAndDropsTheWayThePolePoints", Tests::elbowKeepsBothBonesTheirLengthsAndDropsTheWayThePolePoints);
        Checks.run("pumpGoesBackAndComesForwardAgain", Tests::pumpGoesBackAndComesForwardAgain);
        Checks.run("armTooShortToReachBringsItsShoulderWithIt", Tests::armTooShortToReachBringsItsShoulderWithIt);
        Checks.run("armWithNoWayForItsElbowToDropHasNone", Tests::armWithNoWayForItsElbowToDropHasNone);
        Checks.run("attackStartsAndEndsWhereTheWeaponIsHeld", Tests::attackStartsAndEndsWhereTheWeaponIsHeld);
        Checks.run("swingDrawsBackToTheRightAndSweepsAcrossToTheLeft", Tests::swingDrawsBackToTheRightAndSweepsAcrossToTheLeft);
        Checks.run("attackComesInFasterAndFasterUntilItLands", Tests::attackComesInFasterAndFasterUntilItLands);
        Checks.run("attackMovesWithoutAJump", Tests::attackMovesWithoutAJump);
        Checks.run("attackIsShownAFrameBehindAndLandsOnce", Tests::attackIsShownAFrameBehindAndLandsOnce);
        Checks.run("attacksHeldOneAfterAnotherEachBegin", Tests::attacksHeldOneAfterAnotherEachBegin);
        Checks.run("attackLetGoOfRunsOnAndOnePausedStandsStill", Tests::attackLetGoOfRunsOnAndOnePausedStandsStill);
        Checks.run("attackThatLandsAtItsVeryStartOrEndIsStillANumber", Tests::attackThatLandsAtItsVeryStartOrEndIsStillANumber);
        Checks.run("raiseLeavesTheStraightLineOnlyBetweenItsEnds", Tests::raiseLeavesTheStraightLineOnlyBetweenItsEnds);
        Checks.run("meshSaysWhereItsWristAndElbowAreAndWhatItsSleeveWears", Tests::meshSaysWhereItsWristAndElbowAreAndWhatItsSleeveWears);
        Checks.run("ShaderTests", ShaderTests::run);
        Checks.tally();
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

    private static final float[] HELD = {1.0f, -0.2f, -0.17f, 0.2f, -0.22f, 0.38f, 0f};

    private static void attackStartsAndEndsWhereTheWeaponIsHeld() {
        float[] at = new float[ViewModelMath.HOLD];
        for (int way = ViewModelMath.SWUNG; way <= ViewModelMath.SHOVED; way++) {
            for (boolean struck : new boolean[] {false, true}) {
                ViewModelMath.attack(way, 0f, 0f, struck, HELD, at);
                for (int part = 0; part < ViewModelMath.HOLD; part++) {
                    check(at[part] == HELD[part], "an attack starts from where the weapon is held");
                }
                ViewModelMath.attack(way, 1f, 1f, struck, HELD, at);
                for (int part = 0; part < ViewModelMath.HOLD; part++) {
                    check(Math.abs(at[part] - HELD[part]) < 1e-6f, "and ends there");
                }
                ViewModelMath.attack(way, 1f, 3f, struck, HELD, at);
                check(Math.abs(at[1] - HELD[1]) < 1e-6f, "and stays there after");
            }
        }
    }

    private static void swingDrawsBackToTheRightAndSweepsAcrossToTheLeft() {
        float[] at = new float[ViewModelMath.HOLD];
        ViewModelMath.attack(ViewModelMath.SWUNG, ViewModelMath.WINDS, 0f, false, HELD, at);
        check(at[1] > 0.6f && at[3] > HELD[3] && at[5] < HELD[5] && at[6] > 0.3f,
                "drawn back it is turned and moved to the right and towards the viewer, and the body is turned with it");
        float wasRight = at[3], woundYaw = at[1], wasTurned = at[6], trailed = at[1];
        for (float reached = ViewModelMath.WINDS + 0.02f; reached <= 1f; reached += 0.02f) {
            ViewModelMath.attack(ViewModelMath.SWUNG, reached, 0f, false, HELD, at);
            check(at[3] < wasRight && at[6] < wasTurned, "the hands and the body come round from right to left all the way to landing");
            wasRight = at[3];
            wasTurned = at[6];
            trailed = Math.max(trailed, at[1]);
        }
        float landedYaw = at[1];
        check(trailed > woundYaw + 0.2f && landedYaw < woundYaw - 1.5f, "the far end trails behind the hands as they start round, and is across to the left as it lands");
        ViewModelMath.attack(ViewModelMath.SWUNG, 1f, ViewModelMath.FOLLOWS, false, HELD, at);
        check(at[1] < landedYaw - 0.8f && at[3] < 0f && at[0] < 0f, "landed on nothing it goes on through, well to the left with its end brought down");
        ViewModelMath.attack(ViewModelMath.SWUNG, 1f, ViewModelMath.SPRINGS, true, HELD, at);
        check(at[1] > landedYaw && at[3] > 0f, "landed on something it is stopped there and springs back the way it came");
    }

    private static void attackComesInFasterAndFasterUntilItLands() {
        float[] at = new float[ViewModelMath.HOLD], before = new float[ViewModelMath.HOLD];
        for (int way = ViewModelMath.SWUNG; way <= ViewModelMath.SHOVED; way++) {
            float slow = 0f, fast = 0f;
            for (int step = 0; step <= 100; step++) {
                float reached = ViewModelMath.WINDS + (1f - ViewModelMath.WINDS) * step / 100f;
                ViewModelMath.attack(way, reached, 0f, false, HELD, at);
                float moved = 0f;
                for (int part = 0; part < ViewModelMath.HOLD; part++) {
                    moved += Math.abs(at[part] - before[part]);
                }
                if (step >= 1 && step <= 10) {
                    slow += moved;
                } else if (step > 90) {
                    fast += moved;
                }
                System.arraycopy(at, 0, before, 0, ViewModelMath.HOLD);
            }
            check(fast > slow * 3f, "the last tenth of the way in covers several times what the first does");
        }
    }

    private static void attackMovesWithoutAJump() {
        float[] at = new float[ViewModelMath.HOLD], before = new float[ViewModelMath.HOLD];
        for (int way = ViewModelMath.SWUNG; way <= ViewModelMath.SHOVED; way++) {
            for (boolean struck : new boolean[] {false, true}) {
                System.arraycopy(HELD, 0, before, 0, ViewModelMath.HOLD);
                for (int step = 1; step <= 2000; step++) {
                    float reached = Math.min(step / 1000f, 1f), past = Math.max(step / 1000f - 1f, 0f);
                    ViewModelMath.attack(way, reached, past, struck, HELD, at);
                    for (int part = 0; part < ViewModelMath.HOLD; part++) {
                        check(Math.abs(at[part] - before[part]) < 0.04f, "no part of an attack moves far in a thousandth of the way in or the way back");
                    }
                    System.arraycopy(at, 0, before, 0, ViewModelMath.HOLD);
                }
            }
        }
        ViewModelMath.attack(ViewModelMath.SWUNG, -3f, 0f, false, HELD, at);
        check(at[1] == HELD[1], "before an attack there is none");
    }

    private static void attackIsShownAFrameBehindAndLandsOnce() {
        AttackClock clock = new AttackClock();
        float frame = 1f / 60f;
        clock.step(false, 0f, 0f, frame);
        check(clock.reached() == 0f && clock.past() == 0f && !clock.began, "with no attack there is none");
        clock.step(true, 0.05f, 0.25f, frame);
        check(clock.began && clock.reached() == 0f, "an attack begins from where the weapon is held");
        int landings = 0;
        float shown = 0f;
        for (float fraction = 0.1f; fraction < 0.99f; fraction += 0.05f) {
            float before = fraction - 0.05f;
            clock.step(true, fraction, 0.25f, frame);
            check(!clock.began, "and begins once");
            check(Math.abs(clock.reached() - Math.min(before / 0.25f, 1f)) < 1e-4f, "the picture is where the animation was the frame before");
            if (clock.landed) {
                landings++;
                check(before >= 0.25f && before - 0.05f < 0.25f, "it lands on the frame after the animation passed its landing, which is the frame the game lands it");
            }
            check(clock.past() >= shown, "and goes one way only");
            shown = clock.past();
        }
        check(landings == 1, "it lands once");
        check(clock.past() == 1f, "and what was swung is held again before the animation is over");
    }

    private static void attacksHeldOneAfterAnotherEachBegin() {
        AttackClock clock = new AttackClock();
        float frame = 1f / 60f;
        for (float fraction = 0f; fraction < 0.95f; fraction += 0.1f) {
            clock.step(true, fraction, 0.25f, frame);
        }
        clock.step(true, 0.02f, 0.25f, frame);
        check(clock.began && clock.reached() == 0f && clock.past() == 0f, "an animation that starts again is a new attack, from the start");
        clock.step(true, 0.12f, 0.25f, frame);
        check(!clock.began && !clock.landed && clock.reached() > 0f, "which goes on as the first did");
        clock.reset();
        check(clock.reached() == 0f, "and a clock that is reset has no attack");
        clock.step(true, 0.5f, 0.25f, frame);
        check(clock.began, "so the next thing it sees is one beginning");
    }

    private static void attackThatLandsAtItsVeryStartOrEndIsStillANumber() {
        float frame = 1f / 60f;
        for (float lands : new float[] {0f, AttackClock.HELD_AGAIN, 1f}) {
            AttackClock clock = new AttackClock();
            clock.step(true, 0f, lands, frame);
            clock.step(true, 0.02f, lands, frame);
            check(clock.reached() >= 0f && clock.reached() <= 1f && clock.past() >= 0f && clock.past() <= 1f, "an attack said to land " + lands + " of the way through is somewhere between its start and its end");
            check(clock.past() < 1f, "and is not over as it begins");
        }
    }

    private static void attackLetGoOfRunsOnAndOnePausedStandsStill() {
        AttackClock clock = new AttackClock();
        float frame = 1f / 60f;
        clock.step(true, 0f, 0.25f, frame);
        clock.step(true, 0.1f, 0.25f, frame);
        clock.step(true, 0.2f, 0.25f, frame);
        clock.step(true, 0.3f, 0.25f, frame);
        check(!clock.landed, "the animation is past its landing and the picture is not yet");
        float before = clock.reached();
        for (int i = 0; i < 5; i++) {
            clock.step(true, 0.3f, 0.25f, frame);
        }
        check(clock.reached() == before && !clock.landed, "while the game is paused the picture stands still and nothing lands");
        clock.step(true, 0.4f, 0.25f, frame);
        check(clock.landed, "and it lands on the frame the game goes on");
        float past = clock.past();
        boolean over = false;
        for (int i = 0; i < 60 && !over; i++) {
            clock.step(false, 0f, 0f, frame);
            check(!clock.landed && !clock.began, "let go of, it neither begins nor lands again");
            over = clock.reached() == 0f;
            check(over || clock.past() > past, "and runs on at the pace it had");
            past = clock.past();
        }
        check(over, "to its end");
        clock.step(true, 0f, 0.25f, frame);
        clock.step(true, 0.1f, 0.25f, frame);
        clock.step(false, 0f, 0f, frame);
        for (int i = 0; i < 60; i++) {
            clock.step(false, 0f, 0f, frame);
            check(!clock.landed, "an attack let go of before it landed never lands");
        }
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
        muzzleFlash.light(light, System.nanoTime());
        check(light[0] == 0f && light[3] == 0f && light[4] > 0f, "with no weapon drawn there is no light, only its colour");
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
        aFlowerStandsOnAStalk();
    }

    private static void aFlowerStandsOnAStalk() {
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        SpriteGeometry.Frame frame = new SpriteGeometry.Frame();
        float ground = 0.3f, tall = 0.5f, half = 0.04f;
        Flora.addFlower(mesh, 10.5f, 20.5f, ground, tall, half, 0.0, Flora.PURPLE, 5, frame);
        int each = SpriteGeometry.VERTEX_FLOATS;
        check(mesh.triangles() == 4, "a flower is its head and its stalk, two triangles each");
        float[] v = mesh.vertices;
        boolean foot = false, top = false;
        for (int vertex = 0; vertex < 12; vertex++) {
            int at = vertex * each;
            boolean stalk = vertex >= 6;
            check(Math.floor(v[at + 3] / 2f) == (stalk ? Flora.STALK : Flora.PURPLE), "the head's card says which flower and the stalk's that it is a stalk");
            check(Math.floor((v[at + 4] + 0.5f) / 2f) == 5f, "and both say the same step of distance to be left out from");
            check(v[at + 14] == (stalk ? 1f : 0f), "the stalk's card turns to the eye and the head's does not");
            float sway = (float) Math.sqrt(v[at + 9] * v[at + 9] + v[at + 10] * v[at + 10] + v[at + 11] * v[at + 11]) - 1f;
            if (!stalk) {
                check(near(sway, 1f), "the head moves whole with the wind");
                continue;
            }
            check(near(Math.abs(v[at + 12]), half * Flora.STALK_CARD), "the stalk's card is narrower than the head");
            if (v[at + 2] > ground) {
                top = true;
                check(v[at] == 10.5f && v[at + 1] == 20.5f && near(v[at + 2], ground + tall) && near(sway, 1f),
                        "the stalk's top is at the middle of the head and moves with it");
            } else {
                foot = true;
                check(near(v[at], 10.5f + tall * Flora.STALK_LEANS) && v[at + 1] == 20.5f && near(v[at + 2], ground - Flora.STALK_SUNK) && near(sway, 0f),
                        "its foot is in the ground, a little way off the way the head nods from, and stays put");
            }
        }
        check(foot && top, "a stalk has a foot and a top");
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
        mesh.layers.lie(SpriteGeometry.LAYERS_UP, 3f);
        Flora.addLitter(mesh, 5, 6, new float[] {0f, 0.1f, 0.2f, 0.1f}, new float[] {1f, 0.25f, 0f, 0.5f}, frame);
        check(mesh.triangles() == 2, "litter over a square is two triangles");
        for (int at = 0; at < mesh.floats; at += SpriteGeometry.VERTEX_FLOATS) {
            float[] f = mesh.vertices;
            float cover = 2f - (float) Math.sqrt(f[at + 9] * f[at + 9] + f[at + 10] * f[at + 10] + f[at + 11] * f[at + 11]);
            boolean east = near(f[at], 6f), south = near(f[at + 1], 7f);
            check(near(cover, east ? south ? 0f : 0.25f : south ? 0.5f : 1f), "each corner shows as much litter as it was given");
            check(near(f[at + 2], east ? south ? 0.2f : 0.1f : south ? 0.1f : 0f), "and lies on the ground there");
            check(f[at + 12] == 3f && f[at + 14] == SpriteGeometry.LAYERS_UP, "as many layers over it as it was laid by, which the picture makes room for");
            check(near(f[at + 8], Flora.LITTER * 32f), "and is marked as litter");
        }
    }

    /** One triangle called "wedge": a wooden corner on the floor, a cloth one a metre up and a metal one to the side. */
    private static ByteBuffer packedWedge() {
        return packedWedges("wedge");
    }

    /**
     * The same three vertices under each of the names, one model after another. The whole one
     * is a triangle seen from both sides, so two that share their vertices, and the plain one
     * the one triangle. They are said to be a quarter of a metre apart.
     */
    private static ByteBuffer packedWedges(String... names) {
        ByteBuffer out = ByteBuffer.allocate(256 * names.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put(new byte[] {'C', 'U', 'F', 'M'}).putInt(3).putInt(names.length);
        for (String name : names) {
            out.put((byte) name.length()).put(name.getBytes(StandardCharsets.US_ASCII));
            out.putFloat(-1f).putFloat(-2f).putFloat(0f).putFloat(1f).putFloat(2f).putFloat(1f);
            out.putFloat(0.25f);
            for (int triangles = 2; triangles >= 1; triangles--) {
                out.putInt(3).putInt(triangles);
                out.putFloat(-1f).putFloat(-2f).putFloat(0f).putFloat(0f).putFloat(-1f).putFloat(0f).put((byte) Models.WOOD).put((byte) 255);
                out.putFloat(0f).putFloat(2f).putFloat(1f).putFloat(0f).putFloat(0f).putFloat(1f).put((byte) Models.CLOTH).put((byte) 51);
                out.putFloat(1f).putFloat(0f).putFloat(0.5f).putFloat(1f).putFloat(0f).putFloat(0f).put((byte) Models.METAL).put((byte) 0);
                if (triangles == 2) {
                    out.putShort((short) 2).putShort((short) 1).putShort((short) 0);
                }
                out.putShort((short) 0).putShort((short) 1).putShort((short) 2);
            }
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
        check(wedge.triangles() == 2 && wedge.surface.length == 3, "it has its two triangles, and the vertices they share once");
        check(Arrays.equals(wedge.corners, new int[] {2, 1, 0, 0, 1, 2}), "a triangle is three of its vertices, in the order packed");
        check(wedge.plain.triangles() == 1 && wedge.plain.plain == null && Arrays.equals(wedge.plain.corners, new int[] {0, 1, 2}),
                "its plain one is read after it");
        check(near(wedge.apart, 0.25f) && near(wedge.plain.width(), 2f), "with how far apart the two are, and the plain one is said to be as big");
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
        laterFormat.putInt(4, 4);
        refused(laterFormat, "format 4");
        ByteBuffer cutShort = packedWedge();
        cutShort.limit(cutShort.limit() - 10);
        refused(cutShort, "the file ends before that many");
        ByteBuffer unknownSurface = packedWedge();
        // The file ends with the plain one's last vertex, of metal, and its one triangle.
        int lastSurface = unknownSurface.limit() - 3 * Short.BYTES - 2;
        unknownSurface.put(lastSurface, (byte) Models.SURFACES);
        refused(unknownSurface, "surface " + Models.SURFACES);
        ByteBuffer shuffled = packedWedge();
        shuffled.put(lastSurface, (byte) Models.WOOD);
        refused(shuffled, "out of the order of their surfaces");
        ByteBuffer strayCorner = packedWedge();
        strayCorner.putShort(strayCorner.limit() - Short.BYTES, (short) 3);
        refused(strayCorner, "a triangle at vertex 3");
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
        check(first.start == 0 && first.plain.start == 3 && second.start == 6 && second.plain.start == 9,
                "each model starts where the one before it ends, and its plain one after it");
        float[] buffer = Models.buffer(models.values());
        check(buffer.length == 12 * Models.BUFFER_FLOATS, "every vertex of every model is there");
        int[] corners = Models.corners(models.values());
        check(corners.length == 18 && second.from == 9 && second.plain.from == 15, "and every triangle, a model's from where it says");
        check(Arrays.equals(Arrays.copyOfRange(corners, 9, 18), new int[] {8, 7, 6, 6, 7, 8, 9, 10, 11}),
                "a triangle names its vertices by where they are among every model's");
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
        check(pieces.count == 1 && pieces.triangles() == 2, "a piece is one record whatever its model holds");
        float[] f = pieces.floats;
        int[] whole = pieces.ints;
        check(whole[Models.Instances.FIRST] == 9 && whole[Models.Instances.COUNT] == 6, "it says which triangles of every model's are its model");
        check(whole[Models.Instances.PLAIN_FIRST] == 15 && whole[Models.Instances.PLAIN_COUNT] == 3, "and which are its plain one");
        // Stood at half size along the ground and a quarter up, so the greater of the two.
        check(near(f[Models.Instances.APART], 0.125f), "how far apart the two are is in tiles, as the piece is stood");
        float pixel = 2f / 1080f, least = 0.125f / (pixel * Models.Instances.SEEN_APART) + f[Models.Instances.BALL + 3];
        check(!pieces.plain(0, least - 0.01f, pixel), "a piece near enough for that to show is drawn whole");
        check(pieces.plain(0, least + 0.01f, pixel), "and from further off, where it does not, plain");
        check(!pieces.plain(0, -1f, pixel), "a piece the eye is inside of is drawn whole");
        check(whole[Models.Instances.TEXTURE] == 77, "and which texture dyes it");
        check(whole[Models.Instances.BUILT] == 0, "it is drawn as far as props are");
        pieces.built();
        check(whole[Models.Instances.BUILT] == 1, "and once said to be part of a building, as far as the buildings are");
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
        check(pieces.count == 22 && pieces.triangles() == 44, "pieces are kept however many there are");
        check(near(f[Models.Instances.PLACE], 10f) && whole[Models.Instances.TEXTURE] == 77, "and the first is as it was");
        check(whole[Models.Instances.INTS + Models.Instances.FIRST] == 0 && whole[Models.Instances.INTS + Models.Instances.TEXTURE] == 0,
                "a piece that takes no dye names no texture");
        check(near(f[21 * Models.Instances.FLOATS + Models.Instances.PLACE], 19f) && whole[21 * Models.Instances.INTS + Models.Instances.FIRST] == 9,
                "the last is where the last was put");
        check(whole[Models.Instances.BUILT] == 1 && whole[Models.Instances.INTS + Models.Instances.BUILT] == 0 && whole[21 * Models.Instances.INTS + Models.Instances.BUILT] == 0,
                "the first is still part of a building, and none stood after it is");
        pieces.clear();
        check(pieces.count == 0 && pieces.triangles() == 0, "a chunk built again starts with none");
    }

    /**
     * What a stood vertex is held to, bit for bit: the model's, turned, then made the size it
     * is told along the ground and up, then moved to where the model stands.
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
                "tv", "tv_wide", "tv_console", "lit_screen", "table_lamp", "toilet", "bath", "washing_machine", "wheelie_bin", "litter_bin", "mailbox", "postbox", "park_bench",
                "picnic_table", "crate", "barrel", "frame_tall", "frame_square", "frame_wide", "canvas", "wall_clock", "table_2", "table_3", "table_4",
                "narrow_table", "low_table", "low_table_2", "round_table", "office_desk", "shop_counter", "low_shelves", "wall_shelf", "wall_shelves",
                "metal_rack", "store_shelves", "plastic_chair", "folding_chair", "booth", "stool", "vending_machine", "arcade_machine", "dumpster_sloped", "dumpster_domed",
                "radio", "microwave", "dishwasher", "chest_freezer", "register", "shower", "shower_head", "kettle_barbecue", "gas_barbecue",
                "mini_fridge", "large_fridge",
                "pool_table"};
        for (String name : names) {
            Models.Model model = Models.get(name);
            // A length of canvas is a bare slab. Everything else has parts.
            check((model.triangles() > 100 || name.equals("canvas")) && model.triangles() <= 6000,
                    name + " is a model of a size to draw, at " + model.triangles() + " triangles");
            check(model.plain.triangles() < model.triangles(), name + " has a plain model of fewer triangles: " + model.plain.triangles());
            check(model.apart < 0.02f, name + "'s plain model is nowhere far from it: " + model.apart);
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
        check("/skins/body/m_zedbody01_level2.jpg".equals(FineSkins.Jar.resource("Body/M_ZedBody01_Level2.PNG")),
                "the mod's own skin for one of the game's is under the same path in the jar, in small letters");
        check("/skins/body/m_zedbody01.cut.png".equals(FineSkins.Jar.cut("/skins/body/m_zedbody01.jpg")), "and what of it is see-through is beside it");
        // A skin the jar lacks is no error to the mod, which then draws the game's own: only this says one has gone missing.
        for (String sex : new String[] {"M", "F"}) {
            for (int tone = 1; tone <= 4; tone++) {
                for (String state : new String[] {"", "_level1", "_level2", "_level3"}) {
                    String skin = FineSkins.Jar.resource("Body/" + sex + "_ZedBody0" + tone + state + ".png");
                    check(FineSkins.class.getResource(skin) != null, "the jar has the zombie skin " + skin);
                    check(FineSkins.class.getResource(FineSkins.Jar.cut(skin)) != null, "and what of it is see-through, " + FineSkins.Jar.cut(skin));
                }
            }
        }
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

        flight.cut(false);
        flight.camera(overhead, OVERHEAD_TAN, OVERHEAD_AWAY, yawFrom, pitchFrom, clean, tracked, yawTo, pitchTo, TAN_HALF_FOV, out);
        check(apart(out, overhead) < 1e-2f && turnedApart(out, overhead) < 1e-5f, "before it sets off the camera is the overhead one, whatever the head is doing");
        check(flight.tanHalfFieldOfView() == OVERHEAD_TAN && near(flight.away(), OVERHEAD_AWAY), "with its field of view and its distance");

        flight.cut(true);
        flight.camera(overhead, OVERHEAD_TAN, OVERHEAD_AWAY, yawFrom, pitchFrom, clean, tracked, yawTo, pitchTo, TAN_HALF_FOV, out);
        check(apart(out, tracked) < 1e-4f && turnedApart(out, tracked) < 1e-5f, "landed it is the tracked view, the head's turn and lean in it");
        check(flight.tanHalfFieldOfView() == TAN_HALF_FOV && flight.away() == 0f, "with the first-person field of view, at the eye");

        flight.cut(false);
        float wasAway = OVERHEAD_AWAY, wasTan = OVERHEAD_TAN;
        while (!flight.landed()) {
            flight.advance(true, Flight.SECONDS / 20f, false);
            flight.camera(overhead, OVERHEAD_TAN, OVERHEAD_AWAY, yawFrom, pitchFrom, clean, tracked, yawTo, pitchTo, TAN_HALF_FOV, out);
            check(near(out.rightX * out.rightX + out.rightY * out.rightY + out.rightUp * out.rightUp, 1f)
                    && near(out.upX * out.upX + out.upY * out.upY + out.upUp * out.upUp, 1f)
                    && near(out.forwardX * out.forwardX + out.forwardY * out.forwardY + out.forwardUp * out.forwardUp, 1f), "on the way its axes are each a unit long");
            check(near(out.rightX * out.upX + out.rightY * out.upY + out.rightUp * out.upUp, 0f)
                    && near(out.rightX * out.forwardX + out.rightY * out.forwardY + out.rightUp * out.forwardUp, 0f)
                    && near(out.upX * out.forwardX + out.upY * out.forwardY + out.upUp * out.forwardUp, 0f), "and square to each other");
            float cross = (out.rightY * out.upUp - out.rightUp * out.upY) * out.forwardX + (out.rightUp * out.upX - out.rightX * out.upUp) * out.forwardY
                    + (out.rightX * out.upY - out.rightY * out.upX) * out.forwardUp;
            check(cross > 0.99f, "and of the same hand as a clean view's, so the picture is never mirrored");
            check(flight.away() < wasAway && flight.tanHalfFieldOfView() > wasTan, "it only comes nearer, and its field of view only opens");
            wasAway = flight.away();
            wasTan = flight.tanHalfFieldOfView();
            float s = flight.share();
            float lookedX = out.eyeX + out.forwardX * flight.away(), lookedY = out.eyeY + out.forwardY * flight.away();
            check(Math.abs(lookedX - (atX + (tracked.eyeX - atX) * s)) < 1e-2f && Math.abs(lookedY - (atY + (tracked.eyeY - atY) * s)) < 1e-2f,
                    "and the place it looks at goes straight from the middle of the screen to the eye");
        }
        flight.cut(false);
    }

    private static void flightTakesItsTimeWhateverTheFrames() {
        flight.cut(false);
        flight.advance(true, 5f, false);
        check(flight.share() < 0.1f, "a frame many times too long does not carry the camera half way");
        check(flight.shown() == 1f, "over a view the renderer draws already, its picture is whole from the first frame");
        int frames = 1;
        while (!flight.landed()) {
            flight.advance(true, 1f / 60f, false);
            frames++;
        }
        check(Math.abs(frames / 60f - Flight.SECONDS) < 0.1f, "at sixty frames a second the way in takes the time it is given");
        check(flight.share() == 1f, "and ends at the eye");
        flight.advance(false, 1f / 60f, false);
        check(!flight.landed() && !flight.home(), "turned back, it is on its way out from where it was");
        while (flight.share() > 0f) {
            flight.advance(false, 1f / 60f, false);
        }
        check(flight.home(), "and is home as soon as it is back overhead");
    }

    private static void flightMixesThePicturesOnlyWhileTheCameraIsStill() {
        flight.cut(false);
        float was = 0f;
        int frames = 0;
        while (flight.shown() < 1f) {
            flight.advance(true, 1f / 60f, true);
            check(flight.shown() > was && flight.share() == 0f, "over the game's own picture the renderer's comes in with the camera still overhead");
            was = flight.shown();
            frames++;
        }
        check(Math.abs(frames / 60f - Flight.MIX_SECONDS) < 0.05f, "in the time the mix is given");
        flight.advance(true, 1f / 60f, true);
        check(flight.share() > 0f, "and only then does the camera set off");
        while (!flight.landed()) {
            flight.advance(true, 1f / 60f, true);
        }
        while (flight.share() > 0f) {
            check(flight.shown() == 1f, "on the way out the picture is whole until the camera is back overhead");
            flight.advance(false, 1f / 60f, true);
        }
        check(flight.shown() == 1f && !flight.home(), "where it is still whole, and the game's view is not back yet");
        was = 1f;
        while (!flight.home()) {
            flight.advance(false, 1f / 60f, true);
            check(flight.shown() < was && flight.share() == 0f, "and goes out over the game's own picture with the camera still");
            was = flight.shown();
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
        check(WorldScene.NEAR_CORNER_DISTANCE < Float.parseFloat(Config.COLLISION_MARGIN), "the near plane's corners are nearer than a lean is held off a wall");
    }

    private static void fieldOfViewSetIsTheOneDrawnWith() {
        float before = FieldOfView.degrees();
        check(before == FieldOfView.DEFAULT_DEGREES, "with no file read the field of view is the row's default");
        FieldOfView.set(90f);
        check(near(fieldOfView.tanHalfVertical(), 1f), "a field of view of 90 degrees has a half tangent of 1");
        check(near(fieldOfView.drawnDegrees(), 90f), "and is the angle the picture is drawn with");
        FieldOfView.set(before);
    }

    /** The angles are the light's own, as cameraunlock-core hands them over: the head's by LightMultiplier. */
    private static void lightTakesTheHeadingOfItsAngles() {
        ViewCamera.Pose level = cleanPose(0f, 0f, 1.6f, radians(200), 0f);
        ViewCamera.Pose down = cleanPose(0f, 0f, 1.6f, radians(200), radians(-60));
        ViewCamera.Pose up = cleanPose(0f, 0f, 1.6f, radians(200), radians(89));
        check(near(HeadFollowLight.headingTurn(level, radians(30), 0f, 0f, true), radians(30)), "the beam turns by the light's yaw");
        check(near(HeadFollowLight.headingTurn(level, radians(-30), 0f, 0f, false), radians(-30)),
                "to the left too, and the same about the view's own up while the view is level");
        check(near(HeadFollowLight.headingTurn(down, radians(30), 0f, 0f, true), radians(30)), "looking down changes nothing about straight up");
        check(near(HeadFollowLight.headingTurn(up, radians(90), 0f, 0f, true), radians(90)), "nor does looking almost straight up");
        check(near(HeadFollowLight.headingTurn(down, radians(30), radians(22.5f), radians(30), true), radians(30)),
                "pitch and roll turn no heading when the yaw is about straight up");
        // Yaw about a tipped up vector swings the view centre further over the ground: tan(turn) = tan(yaw) / cos(pitch).
        check(near(HeadFollowLight.headingTurn(down, radians(30), 0f, 0f, false),
                (float) Math.atan(Math.tan(Math.toRadians(30)) / 0.5)), "about the view's own up it is the heading the view centre would take");

        ViewCamera.Pose view = cleanPose(0f, 0f, 1.6f, radians(200), radians(-60));
        ViewCamera.turn(view, radians(25), radians(10), radians(15), false);
        float viewTurn = (float) Math.atan2(view.forwardY, view.forwardX) + radians(360) - radians(200);
        check(near(HeadFollowLight.headingTurn(down, radians(25), radians(10), radians(15), false), viewTurn),
                "given the view's own angles the beam has the heading of the view");

        headFollowLight.follow(level, radians(90), 0f, 0f, true);
        check(near(headFollowLight.turn(), radians(90)), "a followed head turns the beam");
        headFollowLight.release();
        check(headFollowLight.turn() == 0f, "and a released beam is the game's");
        check(HeadFollowLight.headingTurn(level, 0f, 0f, 0f, true) == 0f, "angles of nought, which a multiplier of 0 gives, leave the beam on the aim");
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

    /** These tests run on the classes the installer's jar is made of. */
    private static void theBuildThatShipsHasNoDevelopmentKeys() {
        check(!DevKeys.PRESENT, "the classes that ship have the empty DevKeys");
        check(DevKeys.class.getDeclaredFields().length == 1 && DevKeys.class.getDeclaredMethods().length == 1
                && DevKeys.class.getNestMembers().length == 1, "and it holds nothing but its flag and an empty poll");
        check(DevPatches.list().isEmpty(), "and nothing of the game's is patched for them");
        // A key put in the wrong source tree would pass all of that. What a development key does it does by calling
        // the game, and a class names every method it calls: none of the classes that ship may name one of these.
        try (Stream<Path> files = Files.walk(Path.of("build/classes"))) {
            int read = 0;
            for (Path file : files.filter(each -> each.toString().endsWith(".class")).toList()) {
                String names = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
                read++;
                for (String cheat : CHEATS) {
                    check(!names.contains(cheat), file.getFileName() + " ships and names " + cheat + ", which only a development key calls");
                }
            }
            check(read > 100, "the classes that ship were read: " + read);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * What of the game a cheat calls and nothing that ships does: the clock, the weather, health
     * and damage, the game's own cheats, making and removing zombies, moving the player, and
     * taking a vehicle. All but the three of the game's own modes are what the development keys call.
     */
    private static final String[] CHEATS = {"setTimeOfDay", "setAdminValue", "setEnableAdmin", "setForceSnow", "RestoreToFullHealth",
            "setAvoidDamage", "PlayerCheats", "isCheatAllowed", "setGodMod", "setGhostMode", "setInvisible", "createRealZombieNow",
            "removeFromWorld", "removeFromSquare", "teleportTo", "setHotwired", "setContainerContentAmount", "setCurrentAmmoCount"};

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
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

    private static void flatFloorTakesItsTexelsFromWhereItLies() {
        SpriteGeometry.Frame frame = wholeFrame();
        frame.flat = SpriteGeometry.FLAT_FLOOR;
        SpriteGeometry.Mesh mesh = new SpriteGeometry.Mesh();
        float from = Flats.FROM, to = Flats.FROM + Flats.SPAN;
        check(new SpriteGeometry().addFace(mesh, frame, 10, 20, 0, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f) == 2, "a flat floor is two triangles");
        check(hasVertex(mesh, 10f, 20f, 0f, from, from) && hasVertex(mesh, 11f, 20f, 0f, to, from) && hasVertex(mesh, 11f, 21f, 0f, to, to)
                && hasVertex(mesh, 10f, 21f, 0f, from, to), "the square's corners are the corners of the picture inside its rim, so the square beside goes on where this one stops");
        // A part of the square, as a kerb leaves it: the texels are still those of where it lies.
        SpriteGeometry.Mesh part = new SpriteGeometry.Mesh();
        new SpriteGeometry().addFace(part, frame, 10, 20, 0, 0.25f, 0.5f, 0f, 0.5f, 0f, 0f, 0f, 0.25f, 0f);
        check(hasVertex(part, 10.25f, 20.5f, 0f, from + 0.25f * Flats.SPAN, from + 0.5f * Flats.SPAN)
                && hasVertex(part, 10.75f, 20.75f, 0f, from + 0.75f * Flats.SPAN, from + 0.75f * Flats.SPAN), "a part of the square takes its own part of the picture");
        // A wall stood square: its texels go by how far along the edge and how high, whichever edge it is.
        for (int as = SpriteGeometry.FLAT_NORTH; as <= SpriteGeometry.FLAT_WEST; as++) {
            boolean north = as == SpriteGeometry.FLAT_NORTH;
            frame.flat = as;
            SpriteGeometry.Mesh wall = new SpriteGeometry.Mesh();
            new SpriteGeometry().addFace(wall, frame, 10, 20, 0, 0f, 0f, 0f, north ? 0.5f : 0f, north ? 0f : 0.5f, 0f, 0f, 0f, 0.5f);
            float half = Flats.WALL_FROM + 0.5f * Flats.WALL_SPAN;
            check(hasVertex(wall, 10f, 20f, 0f, Flats.WALL_FROM, 1f) && hasVertex(wall, north ? 10.5f : 10f, north ? 20f : 20.5f, 0.5f * ViewCamera.LEVEL_HEIGHT, half, 0.5f),
                    "a wall stood square takes its texels by how far along it and how high, " + (north ? "north" : "west"));
        }
    }

    /**
     * A face is clipped once where the sprite covers it whole and each of its corners is
     * worked out once: every float of it is what clipping it twice and working each corner
     * out for every triangle gives.
     */
    private static void faceMadeTheShortWayIsTheFaceMadeTheLongWay() {
        int cut = 0, whole = 0;
        for (int trim = 0; trim < 6; trim++) {
            SpriteGeometry.Frame frame = wholeFrame();
            // Whole, then trimmed from each side and from two: faces the sprite covers, cuts square and cuts across a corner.
            frame.left = trim == 1 || trim == 5 ? 40f : 0f;
            frame.width = (trim == 2 ? 90f : 128f) - frame.left;
            frame.top = trim == 3 || trim == 5 ? 150f : 0f;
            frame.height = (trim == 4 ? 215f : 256f) - frame.top;
            frame.uStart = frame.left / 128f;
            frame.uEnd = (frame.left + frame.width) / 128f;
            frame.vStart = frame.top / 256f;
            frame.vEnd = (frame.top + frame.height) / 256f;
            frame.light(0.7f, 0.6f, 0.5f);
            float[][] faces = {{0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f}, {0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f}, {0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f},
                    {0.2f, 0.1f, 0.3f, 0.6f, 0f, 0f, 0f, 0.7f, 0f}, {0f, 0f, 0f, 1f, 0f, 0.4f, 0f, 1f, 0f}};
            for (float[] f : faces) {
                SpriteGeometry.Mesh shortWay = new SpriteGeometry.Mesh(), longWay = new SpriteGeometry.Mesh();
                SpriteGeometry quick = new SpriteGeometry(), plain = new SpriteGeometry();
                plain.afresh = true;
                int made = quick.addFace(shortWay, frame, 3, 4, 1, f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8]);
                check(made == plain.addFace(longWay, frame, 3, 4, 1, f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8])
                        && shortWay.floats == longWay.floats, "a face is as many triangles made either way");
                for (int at = 0; at < shortWay.floats; at++) {
                    check(Float.floatToRawIntBits(shortWay.vertices[at]) == Float.floatToRawIntBits(longWay.vertices[at]),
                            "float " + at + " of a face is the same made either way, trim " + trim);
                }
                cut += made > 2 ? 1 : 0;
                whole += made == 2 ? 1 : 0;
            }
        }
        check(cut >= 4 && whole >= 4, "the faces tried are some whole and some cut to more corners, not " + whole + " and " + cut);
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
        new SpriteGeometry().addUpright(mesh, wholeFrame(), 4, 6, 0, 0.5f, 0.5f, 0f, 0f, 1f, false, false);
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

    private static void theGroundLosesItsLightWithTheSun() {
        float night = Evening.NIGHT_OPEN;
        check(Evening.open(0.9f, night) == 1f, "with the sun high the open ground has all its light");
        check(near(Evening.open(SkyState.FULL_NIGHT, night), night) && near(Evening.open(-0.8f, night), night),
                "by the time the sky is the night's the ground has the night's light");
        float last = 1f;
        for (float sunUp = 0.3f; sunUp >= SkyState.FULL_NIGHT; sunUp -= 0.01f) {
            float light = Evening.open(sunUp, night);
            check(light <= last && last - light < 0.03f, "the light goes evenly as the sun goes down");
            last = light;
        }
        check(near(Evening.kept(-0.3f, 1f, night), night), "the game still at full daylight under a night sky is taken down to the night's");
        check(Evening.kept(-0.3f, night, night) == 1f && Evening.kept(-0.3f, 0.1f, night) == 1f, "a game as dark as the night or darker is left as it is");
        check(Evening.kept(0.9f, 1f, night) == 1f, "by day nothing is taken");
        check(near(Evening.dimmed(0.5f, 0.9f, 0.2f), 0.1f), "a square with no more than the open ground's light loses its share");
        check(near(Evening.dimmed(1f, 0.6f, 0.5f), 0.7f), "what a square has over the open ground's light came from a lamp and stays");
    }

    private static void keysHeldThroughTheLandingKeepTheirWalk() {
        long start = Long.MAX_VALUE - Stride.ADJUST_NANOS / 2, late = start + Stride.ADJUST_NANOS;
        float stock = radians(-135), facing = radians(-45), turned = radians(-30);

        Stride stride = new Stride();
        stride.begin(1f, 0f, facing);
        check(stride.turn(1f, 0f, facing, stock, start - 1) == 0f, "a key held as first person is asked for goes on the way it was walking");
        check(stride.turn(0f, -1f, facing, stock, start - 1) == 0f, "and another key pressed while the camera flies in keeps that walk");
        stride.landed(start);
        check(near(stride.turn(0f, -1f, turned, stock, late - 1), turned - facing), "as it does within the time to adjust, turning with the view, across the clock's wrap");
        check(near(stride.turn(0f, -1f, turned, stock, late), turned - stock), "and goes by the view once the time is up");
        check(near(stride.turn(1f, 0f, turned, stock, late), turned - stock), "as the first key does from then on");

        stride.begin(1f, 0f, facing);
        stride.landed(start);
        check(near(stride.turn(1f, 0f, turned, stock, late + late), turned - facing), "a key held all the while keeps its walk for as long as it is held");

        stride.begin(0f, 0f, facing);
        check(near(stride.turn(0f, -1f, facing, stock, start), facing - stock), "with no key down at the press, a key pressed while the camera flies in goes by the view");

        stride.begin(1f, 0f, facing);
        stride.reset();
        check(near(stride.turn(1f, 0f, facing, stock, start), facing - stock), "a walk that stopped is not kept");
    }

    private static void attackHoldsTheAimForAShortTail() {
        long start = Long.MAX_VALUE - AimInput.TAIL_NANOS / 2, frame = 16_000_000L;
        AimInput aim = new AimInput();
        check(!aim.held(false, false, start), "nothing pressed is no aim");
        check(aim.held(true, false, start), "the game's own aim input is passed on");
        // What is in hand is not a firearm, and the game reads its attack button after its aim input.
        check(aim.held(false, true, start) && !aim.attack(true, false), "the attack button holds the aim");
        aim.frame();
        // The click is passed on over the frames after it, and the tail runs from the last of them.
        long passed = start;
        for (int i = 0; i <= AimInput.FRAMES_PRESSED; i++) {
            passed = start + (i + 1) * frame;
            check(aim.held(false, false, passed), "the aim stays held on the frames after a one frame click");
            aim.attack(false, false);
            aim.frame();
        }
        check(aim.held(false, false, passed + AimInput.TAIL_NANOS - 1), "and to the end of the tail, across the clock's wrap");
        check(!aim.held(false, false, passed + AimInput.TAIL_NANOS), "and no longer");
        aim = new AimInput();
        aim.held(false, true, start);
        aim.attack(true, false);
        aim.frame();
        aim.held(false, true, start + 10 * frame);
        aim.attack(true, false);
        aim.frame();
        aim.attack(true, false);
        aim.frame();
        aim.attack(true, false);
        check(aim.held(false, false, start + 10 * frame + AimInput.TAIL_NANOS - 1), "the tail runs from the last frame the button was down");
        aim.held(false, true, start);
        aim.reset();
        check(!aim.held(false, false, start + frame), "a tail that was reset holds nothing");
        check(aim.held(true, false, start + frame), "but the game's own aim input still does");
    }

    private static void aFirearmsShotWaitsForTheAimToBear() {
        long now = 1_000L, frame = 16_000_000L;
        AimInput aim = new AimInput();
        // A click of one frame with nothing aimed.
        check(aim.held(false, true, now) && !aim.attack(true, true), "a click holds the aim and is not yet an attack");
        aim.frame();
        for (int i = 1; i < AimInput.FRAMES_TO_BEAR; i++) {
            now += frame;
            check(aim.held(false, false, now), "the aim stays held for a click the game has not been told of, frame " + i);
            check(!aim.attack(false, true), "and the game is not told of it, frame " + i);
            aim.frame();
        }
        for (int i = 0; i < AimInput.FRAMES_PRESSED; i++) {
            now += frame;
            check(aim.held(false, false, now) && aim.attack(false, true), "then the click is passed on, held down, frame " + i);
            aim.frame();
        }
        now += frame;
        check(aim.held(false, false, now), "the tail runs from the click's last frame");
        check(!aim.attack(false, true), "and the click is over");
        check(!aim.held(false, false, now + AimInput.TAIL_NANOS + frame), "the aim goes down after the tail");

        // The button held down: fire for as long as it is.
        aim = new AimInput();
        for (int i = 0; i < AimInput.FRAMES_TO_BEAR; i++) {
            aim.held(false, true, now);
            check(!aim.attack(true, true), "a held button waits as a click does, frame " + i);
            aim.frame();
        }
        for (int i = 0; i < 10; i++) {
            aim.held(false, true, now);
            check(aim.attack(true, true), "and is then down for as long as it is held, frame " + i);
            aim.frame();
        }
        aim.held(false, false, now);
        aim.frame();
        aim.held(false, false, now);
        aim.frame();
        aim.held(false, false, now);
        check(!aim.attack(false, true), "and up soon after it is let go");

        // The sights already up: no wait.
        aim = new AimInput();
        for (int i = 0; i < AimInput.FRAMES_TO_BEAR; i++) {
            aim.held(true, false, now);
            aim.frame();
        }
        aim.held(true, true, now);
        check(aim.attack(true, true), "with the aim long held a click is an attack at once");

        // Not a firearm: the game takes the attack on the frame after the aim went up, and a click of one frame is still passed on.
        aim = new AimInput();
        check(aim.held(false, true, now) && !aim.attack(true, false), "what is swung is not an attack on the frame of the click, when the game would not take it");
        aim.frame();
        for (int i = 0; i < AimInput.FRAMES_PRESSED; i++) {
            check(aim.held(false, false, now + frame) && aim.attack(false, false), "a click of one frame is passed on the frame after, held down, frame " + i);
            aim.frame();
        }
        check(!aim.attack(false, false), "and is then over");
        aim = new AimInput();
        aim.held(true, false, now);
        aim.frame();
        aim.held(true, true, now);
        check(aim.attack(true, false), "with the aim already held a click of what is swung is an attack at once");
        aim.frame();
        aim.frame();
        aim.frame();
        aim.held(true, true, now);
        check(aim.attack(true, false), "and the button held down goes on being one");

        // A game that is not taking attacks does not have its aim held for good.
        aim = new AimInput();
        aim.held(false, true, now);
        for (int i = 0; i < 20; i++) {
            aim.frame();
            aim.held(false, false, now);
        }
        check(!aim.held(false, false, now + AimInput.TAIL_NANOS + frame), "a click the game never asked after is dropped");
    }

    private static void fieldOfViewEasesToTheAimedZoomAndBack() {
        fieldOfView.rest();
        float set = FieldOfView.setTanHalfVertical();
        check(fieldOfView.zoom() == 1f && fieldOfView.tanHalfVertical() == set, "unaimed, the picture is drawn at the field of view that was set");
        fieldOfView.ease(false, 0.016f);
        check(fieldOfView.zoom() == 1f, "and stays there");
        fieldOfView.ease(true, FieldOfView.ZOOM_SECONDS);
        float afterOne = fieldOfView.zoom();
        check(near(afterOne, FieldOfView.AIMED_ZOOM + (1f - FieldOfView.AIMED_ZOOM) / (float) Math.E),
                "one time constant covers all but 1/e of the way");
        fieldOfView.rest();
        for (int i = 0; i < 7; i++) {
            fieldOfView.ease(true, FieldOfView.ZOOM_SECONDS / 7f);
        }
        check(near(fieldOfView.zoom(), afterOne), "the easing does not depend on the frame rate");
        for (int i = 0; i < 120; i++) {
            fieldOfView.ease(true, 0.016f);
        }
        check(fieldOfView.zoom() == FieldOfView.AIMED_ZOOM, "it settles on the aimed zoom exactly");
        check(near(fieldOfView.tanHalfVertical(), set * FieldOfView.AIMED_ZOOM), "which narrows the tangent the picture is drawn with");
        check(near(FieldOfView.setTanHalfVertical(), set), "and leaves the field of view that was set");
        fieldOfView.ease(false, 0.016f);
        check(fieldOfView.zoom() > FieldOfView.AIMED_ZOOM && fieldOfView.zoom() < 1f, "lowering the sights eases back, never at once");
        for (int i = 0; i < 120; i++) {
            fieldOfView.ease(false, 0.016f);
        }
        check(fieldOfView.zoom() == 1f, "and settles on exactly 1");
        fieldOfView.ease(true, 0.016f);
        fieldOfView.rest();
        check(fieldOfView.zoom() == 1f, "with no picture drawn it is unzoomed at once");
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
        // What the tests before this wrote is no part of what is read back.
        int before = Files.readString(logs.resolve(Log.FILE_NAME)).length();
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
        String written = Files.readString(logs.resolve(Log.FILE_NAME)).substring(before);
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
