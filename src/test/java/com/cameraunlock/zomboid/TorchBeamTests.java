package com.cameraunlock.zomboid;

/** Tests of how the carried torch's beam falls away from its axis: PipelineShaders' torchBeam, evaluated. */
final class TorchBeamTests {
    private TorchBeamTests() {}

    private static final float STEP_DEGREES = 0.25f;

    static void run() {
        Checks.run("theBeamIsBrightestOnItsAxisAndLessAllTheWayOut", TorchBeamTests::theBeamIsBrightestOnItsAxisAndLessAllTheWayOut);
        Checks.run("theBeamIsNothingPastItsEdge", TorchBeamTests::theBeamIsNothingPastItsEdge);
        Checks.run("theBeamGivesUnderHalfTheLightTheFlatTopDid", TorchBeamTests::theBeamGivesUnderHalfTheLightTheFlatTopDid);
        Checks.run("aWayThirtyDegreesOffTheAxisHasLessThanTheFlatTopGaveIt", TorchBeamTests::aWayThirtyDegreesOffTheAxisHasLessThanTheFlatTopGaveIt);
    }

    private static GlslFunctions shader() {
        String beams = PipelineShaders.BEAMS;
        int from = beams.indexOf("float torchBeam(float within) {");
        Checks.check(from >= 0, "the BEAMS text has torchBeam");
        return new GlslFunctions(beams.substring(from, beams.indexOf("\n}", from) + 2));
    }

    private static float beam(GlslFunctions shader, double degrees) {
        return shader.call("torchBeam", (float) Math.cos(Math.toRadians(degrees)));
    }

    /** The beam the torch had before, flat to 15 degrees, which lit nearly all of the view. */
    private static float flatTop(double degrees) {
        float within = (float) Math.cos(Math.toRadians(degrees));
        return 0.14f * smoothstep(0.55f, 0.8f, within) + 0.86f * smoothstep(0.8f, 0.965f, within);
    }

    private static float smoothstep(float from, float to, float x) {
        float t = Math.min(Math.max((x - from) / (to - from), 0f), 1f);
        return t * t * (3f - 2f * t);
    }

    /** No flat top for a wall at arm's length to show as a disc, and a middle no more than a third over the flat top's. */
    private static void theBeamIsBrightestOnItsAxisAndLessAllTheWayOut() {
        GlslFunctions shader = shader();
        float axis = beam(shader, 0), last = axis;
        Checks.check(axis > 1f && axis <= 1.3f * flatTop(0), "the middle is brighter than the flat top and no more than 1.3 times it: " + axis);
        for (float degrees = STEP_DEGREES; degrees <= 60f; degrees += STEP_DEGREES) {
            float now = beam(shader, degrees);
            Checks.check(now < last, "the beam is less at " + degrees + " degrees than nearer the axis: " + now + " after " + last);
            last = now;
        }
        Checks.check(beam(shader, 10) < 0.8f * axis, "10 degrees off the axis has under 0.8 of the middle: " + beam(shader, 10));
    }

    /** Exactly nothing past 60 degrees, so a point there asks nothing of the torch's map. */
    private static void theBeamIsNothingPastItsEdge() {
        GlslFunctions shader = shader();
        for (float degrees = 60f + STEP_DEGREES; degrees <= 180f; degrees += STEP_DEGREES) {
            Checks.check(beam(shader, degrees) == 0f, "nothing at " + degrees + " degrees: " + beam(shader, degrees));
        }
    }

    /** Over the sphere, each way weighed by the solid angle it stands for. */
    private static void theBeamGivesUnderHalfTheLightTheFlatTopDid() {
        GlslFunctions shader = shader();
        double now = 0, was = 0, step = Math.toRadians(STEP_DEGREES);
        for (float degrees = STEP_DEGREES / 2; degrees < 90f; degrees += STEP_DEGREES) {
            double band = Math.sin(Math.toRadians(degrees)) * step;
            now += beam(shader, degrees) * band;
            was += flatTop(degrees) * band;
        }
        Checks.check(now < 0.45 * was, "the beam gives " + now + " in all, the flat top " + was);
    }

    /** The flat top was still 0.44 there, and a ceiling near by took more than the far wall it was aimed at. */
    private static void aWayThirtyDegreesOffTheAxisHasLessThanTheFlatTopGaveIt() {
        GlslFunctions shader = shader();
        Checks.check(beam(shader, 30) < 0.8f * flatTop(30), "30 degrees off the axis: " + beam(shader, 30) + " against the flat top's " + flatTop(30));
        Checks.check(beam(shader, 25) < 0.6f * flatTop(25), "25 degrees off the axis: " + beam(shader, 25) + " against the flat top's " + flatTop(25));
        Checks.check(beam(shader, 30) < 0.1f * beam(shader, 0), "30 degrees off the axis has under a tenth of the middle: " + beam(shader, 30));
    }
}
