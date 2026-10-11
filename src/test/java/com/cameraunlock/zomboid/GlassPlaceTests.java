package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/**
 * Tests of where a line of sight from a seat meets the glass of the vehicle, as the cabin's
 * shader counts a place on a pane for the dust and the rain on it and for the wipers' arcs: a
 * lean of the head, along the seat, across it or up, leaves a place on the glass where it is,
 * for the driver and for passengers.
 *
 * Nothing here can run a shader, so the shader's arithmetic is mirrored in Java and the lines
 * the mirror follows are checked to be in the shader word for word.
 */
final class GlassPlaceTests {
    private GlassPlaceTests() {}

    /** The standard car's driver's place in the shader's units (Pipeline.setCabin): uDash.x, uScreen.w, uPad.y and uWheel.y. */
    private static final float DASH = Cabins.STANDARD_MEASURES[4] / Cabins.SCALE, GLASS_OUT = Cabins.STANDARD_MEASURES[3] / Cabins.SCALE,
            PAD = Cabins.PILLAR_BEHIND, WHEEL_ACROSS = 0f;
    private static final float CABIN = 0.6667f;

    static void run() {
        Checks.run("thePlacesMirroredAreTheShaders", GlassPlaceTests::thePlacesMirroredAreTheShaders);
        Checks.run("aLeanLeavesAPlaceOnABuiltCabinsGlassWhereItIs", GlassPlaceTests::aLeanLeavesAPlaceOnABuiltCabinsGlassWhereItIs);
    }

    private static void thePlacesMirroredAreTheShaders() {
        String shader = String.join("\n", PipelineShaders.FIGURE_LIGHT_PARTS, PipelineShaders.FIGURE_LIGHT_PANES, CabinShader.SOLIDS, CabinShader.GLASS, PipelineShaders.FIGURE_LIGHT_MAIN);
        for (String line : new String[] {
            "float wide = abs(uDash.x) + uScreen.w;",
            "float out2 = d.y > 0.0 ? mid + widths().x + 0.03 - o.y : o.y - mid + widths().x + 0.03;",
            "float side = out2 / max(abs(d.y), 0.001);",
            "float end = d.x < 0.0 ? (o.x - uPad.y + 1.9) / max(-d.x, 0.001) : 9.0;",
            "return vec2(min(side, end), side <= end ? 1.0 : 0.0);",
            "vec2 leaves = paneOut(vec3(uSeat.x, (uSeat.y - uWheel.y) * toward, uSeat.z), line * vec3(1.0, toward, 1.0));",
            "ends = leaves.y < 0.5;",
            "at = (uSeat + line * leaves.x) * CABIN;",
            "return pane(vec2(ends ? at.y : at.x + 7.0, at.z), faces, -out3, ends && along > 0.0 ? 1.0 : 0.0);",
            "vec3 on3 = to + uSeat;",
            "gl_FragData[0] = pane(vec2(ends ? on3.y : on3.x + 7.0, on3.z), n, view, ends && to.x > 0.0 ? 1.0 : 0.0);",
        }) {
            int at = shader.indexOf(line);
            check(at >= 0 && shader.indexOf(line, at + 1) < 0, "the cabin's shader has the line \"" + line + "\" once, as the place on the glass is mirrored here");
        }
    }

    /** paneOut: how far a line from o going d, across counted towards the middle, goes to the glass it leaves by, and 1 for a side pane. */
    private static float[] paneOut(float[] o, float[] d) {
        float mid = Math.abs(DASH), wide = Math.abs(DASH) + GLASS_OUT;
        float out2 = d[1] > 0f ? mid + wide + 0.03f - o[1] : o[1] - mid + wide + 0.03f;
        float side = out2 / Math.max(Math.abs(d[1]), 0.001f);
        float end = d[0] < 0f ? (o[0] - PAD + 1.9f) / Math.max(-d[0], 0.001f) : 9f;
        return new float[] {Math.min(side, end), side <= end ? 1f : 0f};
    }

    /** paneSeen with the driver's place laid out, for an eye at seat looking along line: the place on the pane, 1 for a screen at an end, 1 for the front one. */
    private static float[] seen(float[] seat, float[] line) {
        float toward = DASH < 0f ? -1f : 1f;
        float[] leaves = paneOut(new float[] {seat[0], (seat[1] - WHEEL_ACROSS) * toward, seat[2]}, new float[] {line[0], line[1] * toward, line[2]});
        boolean ends = leaves[1] < 0.5f;
        float[] at = new float[3];
        for (int k = 0; k < 3; k++) {
            at[k] = (seat[k] + line[k] * leaves[0]) * CABIN;
        }
        return new float[] {ends ? at[1] : at[0] + 7f, at[2], ends ? 1f : 0f, ends && line[0] > 0f ? 1f : 0f};
    }

    private static float[] unit(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[] {x / length, y / length, z / length};
    }

    /** Where each seat's eye is with no lean, in the shader's uSeat: the driver at nought, a passenger beside and one behind across the middle line. */
    private static final float[][] SEATS = {{0f, 0f, 0f}, {0f, 2f * DASH, 0f}, {-1.1f, 2f * DASH, 0f}};
    private static final String[] SEAT_NAMES = {"the driver", "the front passenger", "the passenger behind"};

    /** Lines of sight from an eye with no lean to the driver's side window, the far side window and the rear screen. */
    private static final float[][] LOOKS = {unit(0.3f, -1f, 0.1f), unit(0.2f, 1f, 0.05f), unit(-1f, 0.12f, 0.08f)};
    private static final String[] LOOK_NAMES = {"the driver's side window", "the far side window", "the rear screen"};

    /** Leans of the head along the seat, across it and up, a tenth of a tile each way. */
    private static final float[][] LEANS = {{0.15f, 0f, 0f}, {-0.15f, 0f, 0f}, {0f, 0.15f, 0f}, {0f, -0.15f, 0f}, {0f, 0f, 0.15f}, {0f, 0f, -0.15f}};

    private static void aLeanLeavesAPlaceOnABuiltCabinsGlassWhereItIs() {
        for (int s = 0; s < SEATS.length; s++) {
            float[] seat = SEATS[s];
            for (int l = 0; l < LOOKS.length; l++) {
                float[] still = seen(seat, LOOKS[l]);
                String what = SEAT_NAMES[s] + " looking at " + LOOK_NAMES[l];
                check(still[2] == (l == 2 ? 1f : 0f), what + ": the line leaves by that pane");
                float[] reach = paneOut(new float[] {seat[0], seat[1], seat[2]}, LOOKS[l]);
                float[] glass = new float[3];
                for (int k = 0; k < 3; k++) {
                    glass[k] = seat[k] + LOOKS[l][k] * reach[0];
                }
                for (float[] lean : LEANS) {
                    float[] eye = {seat[0] + lean[0], seat[1] + lean[1], seat[2] + lean[2]};
                    float[] moved = seen(eye, unit(glass[0] - eye[0], glass[1] - eye[1], glass[2] - eye[2]));
                    check(moved[2] == still[2] && Math.abs(moved[0] - still[0]) < 1e-4f && Math.abs(moved[1] - still[1]) < 1e-4f,
                            what + ", the head leant (" + lean[0] + ", " + lean[1] + ", " + lean[2] + "): the same place on the glass is at (" + moved[0] + ", " + moved[1]
                                    + ") where it was at (" + still[0] + ", " + still[1] + ")");
                }
            }
        }
    }
}
