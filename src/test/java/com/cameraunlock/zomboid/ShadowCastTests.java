package com.cameraunlock.zomboid;

/** Tests of where a shadow map holds what it casts: PipelineShaders.SHADOW_VERTEX and its castAt. */
final class ShadowCastTests {
    private ShadowCastTests() {}

    /** The torch's map as Pipeline makes it: its tangent, near and far (reach 15 and 3.5 past it), and how far back the beam asks it. */
    private static final float TAN = 1.6f, NEAR = 0.08f, FAR = 18.5f, ASKED_BACK = 0.06f;

    static void run() {
        Checks.run("everyPlaceAMapCastsIsCountedFromNearTheEye", ShadowCastTests::everyPlaceAMapCastsIsCountedFromNearTheEye);
        Checks.run("aWallTheTorchLightsIsHeldInItsMapWellWithinTheBeamsBias", ShadowCastTests::aWallTheTorchLightsIsHeldInItsMapWellWithinTheBeamsBias);
    }

    private static void everyPlaceAMapCastsIsCountedFromNearTheEye() {
        String vertex = PipelineShaders.SHADOW_VERTEX.replaceAll("\\s+", "");
        int positions = count(vertex, "gl_Position="), cast = count(vertex, "gl_Position=castAt("), dropped = count(vertex, "gl_Position=vec4(2.0,2.0,2.0,1.0);");
        Checks.check(positions == cast + dropped, positions + " places a map is given, " + cast + " of them counted from the eye and " + dropped + " left out of it");
        Checks.check(count(vertex, "uMatrix*") == 1 && vertex.contains("returnuMatrix*vec4(from,0.0)+uCastOrigin;"), "the matrix turns only what is left once the eye's place is taken off");
    }

    /**
     * The world near the owner's report, 11932 tiles out on the map, with the torch's map multiplied in floats as the
     * graphics card does. Whole, a vertex of a wall twelve tiles off came out up to centimetres nearer or further than it
     * is, as much as the beam asks back, and the wall shaded itself in a wedge from each vertex that came out near.
     */
    private static void aWallTheTorchLightsIsHeldInItsMapWellWithinTheBeamsBias() {
        float torchX = 11932.015f, torchY = 6881.002f, torchUp = 1.937f;
        ViewCamera.Pose lamp = new ViewCamera.Pose();
        ViewCamera.clean(lamp, torchX, torchY, torchUp, (float) Math.atan2(-0.056, 0.997), (float) Math.asin(0.049));
        float[] seen = new float[16], cast = new float[16], matrix = new float[16];
        ViewCamera.view(lamp, seen);
        ViewCamera.projection(TAN, 1f, NEAR, FAR, cast);
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += cast[k * 4 + row] * seen[column * 4 + k];
                }
                matrix[column * 4 + row] = sum;
            }
        }
        float[] origin = new float[4];
        for (int row = 0; row < 4; row++) {
            origin[row] = (float) ((double) matrix[row] * torchX + (double) matrix[4 + row] * torchY + (double) matrix[8 + row] * torchUp + matrix[12 + row]);
        }
        double whole = 0, fromEye = 0;
        for (float y = 6876.13f; y < 6887f; y += 0.37f) {
            for (float up = 0.21f; up < 5.5f; up += 0.29f) {
                float x = 11944.03f;
                double dx = x - (double) torchX, dy = y - (double) torchY, dUp = up - (double) torchUp;
                double truth = distance(matrix[2] * dx + matrix[6] * dy + matrix[10] * dUp + origin[2], matrix[3] * dx + matrix[7] * dy + matrix[11] * dUp + origin[3]);
                float[] absolute = clip(matrix, x, y, up, 1f, new float[4]), relative = clip(matrix, x - torchX, y - torchY, up - torchUp, 0f, origin);
                whole = Math.max(whole, Math.abs(distance(absolute[2], absolute[3]) - truth));
                fromEye = Math.max(fromEye, Math.abs(distance(relative[2], relative[3]) - truth));
            }
        }
        Checks.check(whole > ASKED_BACK / 2f && fromEye < ASKED_BACK / 10f, "counted from the eye, a vertex is held at most " + fromEye + " tiles from where it is (whole: " + whole + ")");
    }

    /** A place through the matrix in floats, each row summed in the order the graphics card is given it. */
    private static float[] clip(float[] m, float x, float y, float up, float w, float[] plus) {
        float[] out = new float[4];
        for (int row = 0; row < 4; row++) {
            out[row] = m[row] * x + m[4 + row] * y + m[8 + row] * up + m[12 + row] * w + plus[row];
        }
        return out;
    }

    /** How far along the torch's axis the map holds a clip place, as its depth says. */
    private static double distance(double z, double w) {
        double depth = z / w;
        return 2.0 * FAR * NEAR / ((FAR + NEAR) - depth * (FAR - NEAR));
    }

    private static int count(String text, String part) {
        int found = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + 1)) {
            found++;
        }
        return found;
    }
}
