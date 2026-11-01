package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Tests of the background softened while aiming: AimBlur's focus, blur and easing, and the shaders' text against them. */
final class AimBlurTests {
    private AimBlurTests() {}

    static void run() {
        Checks.run("whatTheAimIsOnStaysSharpAndWhatIsBeyondItSoftens", AimBlurTests::whatTheAimIsOnStaysSharpAndWhatIsBeyondItSoftens);
        Checks.run("aNearTargetSoftensTheBackgroundMoreThanAFarOne", AimBlurTests::aNearTargetSoftensTheBackgroundMoreThanAFarOne);
        Checks.run("theGunSoftensLessThanTheBackgroundAndItsNearestPartsMost", AimBlurTests::theGunSoftensLessThanTheBackgroundAndItsNearestPartsMost);
        Checks.run("theBlurComesAndGoesWithTheSightsAndNeverThroughAScope", AimBlurTests::theBlurComesAndGoesWithTheSightsAndNeverThroughAScope);
        Checks.run("theFocusIsPulledToTheAimAtAnyFrameRate", AimBlurTests::theFocusIsPulledToTheAimAtAnyFrameRate);
        Checks.run("theShadersBlurAsTheJavaDoes", AimBlurTests::theShadersBlurAsTheJavaDoes);
        Checks.run("theBlurIsDrawnAfterTheSteadyingAndNotInLow", AimBlurTests::theBlurIsDrawnAfterTheSteadyingAndNotInLow);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    /** The tangent of half the height's angle of a picture of the usual field of view. */
    private static final float TAN = 0.6f;

    private static float far(float focusTiles, float ahead) {
        return AimBlur.farBlur(1f / focusTiles, ahead, AimBlur.spread(AimBlur.APERTURE, TAN, 1f), AimBlur.MOST_FAR);
    }

    private static float near(float focusTiles, float ahead) {
        return AimBlur.nearBlur(1f / focusTiles, ahead, AimBlur.spread(AimBlur.NEAR_APERTURE, TAN, 1f), AimBlur.MOST_NEAR);
    }

    private static void whatTheAimIsOnStaysSharpAndWhatIsBeyondItSoftens() {
        check(far(5f, 5f) == 0f && near(5f, 5f) == 0f, "a target five tiles off is sharp");
        check(far(5f, 4f) == 0f, "what is nearer than the focus takes no blur beyond it");
        float behind = far(5f, 10f), wayBehind = far(5f, 400f);
        check(behind > 0f && wayBehind > behind, "what is beyond it softens, more the further: " + behind + ", " + wayBehind);
        check(far(1.5f, 1e6f) == AimBlur.MOST_FAR, "however far and however near the focus, never more than MOST_FAR");
        check(AimBlur.MOST_FAR * 1080f < 8f, "a gentle softening: at most " + AimBlur.MOST_FAR * 1080f + " dots of a picture 1080 high");
        check(AimBlur.farBlur(0f, 400f, AimBlur.spread(AimBlur.APERTURE, TAN, 1f), AimBlur.MOST_FAR) == 0f,
                "with nothing in reach the focus is at the horizon and nothing beyond it softens");
    }

    private static void aNearTargetSoftensTheBackgroundMoreThanAFarOne() {
        float nearTarget = far(3f, 200f), farTarget = far(30f, 200f);
        check(nearTarget > 4f * farTarget, "a target at three tiles softens the distance far more than one at thirty: " + nearTarget + " against " + farTarget);
        check(farTarget * 1080f < 1f, "behind a target thirty tiles off the distance is near enough sharp: " + farTarget * 1080f + " dots");
        check(far(3f, 6f) > 0.4f * far(3f, 200f), "and behind a near one even what stands a little past it softens");
        float zoomed = AimBlur.farBlur(1f / 5f, 200f, AimBlur.spread(AimBlur.APERTURE, TAN * 0.7f, 1f), AimBlur.MOST_FAR);
        check(zoomed > far(5f, 200f), "the sights' zoom magnifies the blur with the rest of the picture");
    }

    private static void theGunSoftensLessThanTheBackgroundAndItsNearestPartsMost() {
        float stock = near(5f, 0.12f), foresight = near(5f, 0.6f);
        check(stock > foresight && foresight > 0f, "the receiver at the eye softens more than the foresight: " + stock + ", " + foresight);
        check(foresight * 1080f < 1f, "the foresight stays all but sharp: " + foresight * 1080f + " dots");
        check(stock <= AimBlur.MOST_NEAR && AimBlur.MOST_NEAR < AimBlur.MOST_FAR / 2f, "and the gun never softens as much as the background");
        check(near(5f, 8f) == 0f, "what is beyond the focus takes no near blur");
    }

    private static void theBlurComesAndGoesWithTheSightsAndNeverThroughAScope() {
        check(AimBlur.amount(true, true, false, 0f) == 0f, "at the hip nothing softens");
        check(AimBlur.amount(true, true, false, 0.4f) == 0.4f && AimBlur.amount(true, true, false, 1f) == 1f, "it eases in as far as the sights are up");
        check(AimBlur.amount(true, true, true, 1f) == 0f, "never with a scope on the gun");
        check(AimBlur.amount(true, false, false, 1f) == 0f, "nor with something that is not a gun");
        check(AimBlur.amount(false, true, false, 1f) == 0f, "nor with the part off, or in Low");
        check(AimBlur.spread(AimBlur.APERTURE, TAN, 0.5f) == 0.5f * AimBlur.spread(AimBlur.APERTURE, TAN, 1f), "half up, half the blur");
    }

    /** The focus after seconds in steps of step, from one distance's to another's. */
    private static float pulled(float from, float to, float seconds, float step) {
        float focus = from;
        for (float done = 0f; done < seconds - step * 0.5f; done += step) {
            focus = AimBlur.pulled(focus, to, step);
        }
        return focus;
    }

    private static void theFocusIsPulledToTheAimAtAnyFrameRate() {
        float from = 1f / 30f, to = 1f / 3f;
        float once = pulled(from, to, AimBlur.FOCUS_SECONDS, 1f / 120f);
        float share = (once - from) / (to - from);
        check(Math.abs(share - (1f - (float) Math.exp(-1))) < 0.01f, "the focus comes all but one over e of the way in FOCUS_SECONDS: " + share);
        check(Math.abs(once - pulled(from, to, AimBlur.FOCUS_SECONDS, 1f / 20f)) < 1e-4f, "the same at 20 frames a second as at 120");
        check(Math.abs(pulled(from, to, 2f, 1f / 60f) - to) < 1e-5f, "and settles on the aim");
        check(AimBlur.pulled(to, to, 0.1f) == to && Math.abs(AimBlur.pulled(from, to, 0f) - from) < 1e-7f, "nothing moves a settled focus, and no time moves nothing");
        check(AimBlur.FOCUS_SECONDS > 0.05f && AimBlur.FOCUS_SECONDS < 0.5f, "quick, but not a jump");
    }

    private static void theShadersBlurAsTheJavaDoes() {
        GlslFunctions shader = new GlslFunctions(AimBlur.BLURS);
        float spread = AimBlur.spread(AimBlur.APERTURE, TAN, 1f);
        for (float focus : new float[] {0f, 1f / 40f, 0.2f, 1f / 1.5f}) {
            for (float ahead : new float[] {0.1f, 0.5f, 1.5f, 5f, 40f, 1000f}) {
                float java = AimBlur.farBlur(focus, ahead, spread, AimBlur.MOST_FAR), glsl = shader.call("farBlur", focus, ahead, spread, AimBlur.MOST_FAR);
                check(Math.abs(java - glsl) < 1e-6f, "farBlur at focus " + focus + " and " + ahead + " tiles: " + java + " in Java, " + glsl + " in the shader");
                java = AimBlur.nearBlur(focus, ahead, spread, AimBlur.MOST_NEAR);
                glsl = shader.call("nearBlur", focus, ahead, spread, AimBlur.MOST_NEAR);
                check(Math.abs(java - glsl) < 1e-6f, "nearBlur at focus " + focus + " and " + ahead + " tiles: " + java + " in Java, " + glsl + " in the shader");
            }
        }
    }

    private static void theBlurIsDrawnAfterTheSteadyingAndNotInLow() {
        String pipeline = source("Pipeline.java").replaceAll("\\s+", "");
        int steadied = pipeline.indexOf("drawSteady(camera,tanHalfFieldOfView,aspect,near,far);");
        int blurred = pipeline.indexOf("frameColor=aimBlur.draw(view,frameColor,sceneDepth,width,height,tanHalfFieldOfView,near,far,frameNanos);meter();");
        int developed = pipeline.indexOf("drawDeveloped(camera,state,tanHalfFieldOfView,aspect,exposure,near,far,fogStart,fogEnd);");
        check(steadied >= 0 && blurred > steadied && developed > blurred, "blurred after the frames are steadied, and before the meter, the glow and the develop read the picture");
        String blur = source("AimBlur.java").replaceAll("\\s+", "");
        check(blur.contains("booleanon=gather!=null&&Part.AIM_BLUR.on()&&!Quality.plain(view.index);"), "nothing in Low or with the part off");
        check(blur.contains("if(amount<=0f){focus=Float.NaN;returncolor;}"), "and nothing drawn with the sights down");
        check(blur.contains("view.scope.fitted()!=null"), "a scope fitted keeps the picture sharp");
        check(Part.AIM_BLUR.live && !Part.AIM_BLUR.start, "the switch is taken while the game runs and patches nothing");
    }

    private static String source(String file) {
        try {
            return Files.readString(Path.of("src/main/java/com/cameraunlock/zomboid", file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
