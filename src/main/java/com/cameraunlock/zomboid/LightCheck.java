package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Locale;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

/**
 * Dev only, asked from the probe: has the graphics card work out the light of every vertex the
 * world's buffer holds with the world's own vertex program, caught by transform feedback where
 * that program goes on to the kinds of light the game has not given, and compares it with what
 * LightSites.decode works out from the same floats and the same records. The alpha, which
 * carries the kind of light and the material, must be the same to the bit. Red, green, blue,
 * unlit and day may differ where the card fuses a multiply and an add into one rounding, by a
 * few units in the last place: such vertices are counted, with the most any differs by, and a
 * difference of more than FUSED_ULPS is a fault. Render thread.
 */
final class LightCheck {
    /** Set from the probe. The picture runs the check on its next frame and clears it. */
    static volatile boolean asked;
    /** The most units in the last place a sum fused on the card may move a light by. */
    private static final int FUSED_ULPS = 8;
    private static final int GL_POINTS = 0x0000, GL_ARRAY_BUFFER = 0x8892, GL_STREAM_READ = 0x88E1, GL_TRANSFORM_FEEDBACK_BUFFER = 0x8C8E,
            GL_RASTERIZER_DISCARD = 0x8C89;
    /** Where the world's vertex program has worked out a vertex's light and goes on to the kinds of light the game has not given. */
    private static final String CAUGHT_AT = "    float lit = floor((vColor.a + 0.5) / 4096.0);\n";
    static final String VERTEX = once(once(PipelineShaders.WORLD_VERTEX, "varying vec2 vUv;\n", "varying vec4 tfLight;\nvarying vec2 tfShade;\nvarying vec2 vUv;\n"),
            CAUGHT_AT, "    tfLight = vColor;\n    tfShade = vec2(unlit, day);\n" + CAUGHT_AT);
    static final String FRAGMENT = """
            #version 120
            void main() {
                gl_FragColor = vec4(0.0);
            }
            """;
    /** What is caught of each vertex, one after the other: its colour and alpha, then unlit and day. */
    private static final String[] CAUGHT = {"tfLight", "tfShade"};
    private static final int CAUGHT_FLOATS = 6;
    private static Program program;
    private static int caught;
    private static FloatBuffer caughtFloats = floats(0), heldFloats = floats(0);
    private static float[] held = new float[0];
    private static final float[] decoded = new float[6];
    private static long vertices, fused, faults;
    private static int mostUlps;
    private static String firstFault;

    private LightCheck() {}

    private static String once(String source, String was, String now) {
        int at = source.indexOf(was);
        if (at < 0 || source.indexOf(was, at + 1) >= 0) {
            throw new IllegalStateException("the world's vertex program has " + (at < 0 ? "no" : "more than one") + " \"" + was.strip() + "\"");
        }
        return source.substring(0, at) + now + source.substring(at + was.length());
    }

    private static FloatBuffer floats(int count) {
        return ByteBuffer.allocateDirect(count * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    /**
     * Checks every block ChunkBuffers holds, with the world's arrays switched on and the vertex
     * attributes bound where the world's programs bind them, and logs what it found.
     */
    static void run(int[] places, String[] names) {
        asked = false;
        if (program == null) {
            StringBuilder problem = new StringBuilder();
            program = Program.caught("light check", VERTEX, FRAGMENT, problem, CAUGHT, places, names);
            if (program == null) {
                throw new IllegalStateException("the light check's program was refused: " + problem);
            }
        }
        program.use();
        program.set("uModel", 0f);
        program.set("uLaid", 0f);
        LightPages.bind(program);
        vertices = 0;
        fused = 0;
        faults = 0;
        mostUlps = 0;
        firstFault = null;
        caught = GL15.glGenBuffers();
        GL11.glEnable(GL_RASTERIZER_DISCARD);
        try {
            ChunkBuffers.checkLight();
        } finally {
            GL11.glDisable(GL_RASTERIZER_DISCARD);
            GL30.glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0);
            GL15.glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER, 0);
            GL15.glDeleteBuffers(caught);
        }
        Log.info(String.format(Locale.ROOT, "light check: %d vertices worked out on the graphics card, %d of them by a fused sum, by at most %d units in the last place, %d FAULTS%s",
                vertices, fused, mostUlps, faults, firstFault == null ? "" : ", the first " + firstFault));
    }

    /** Checks a build's block: floats floats of the buffer from at. */
    static void block(int buffer, int at, int floats) {
        int count = floats / SpriteGeometry.VERTEX_FLOATS;
        if (caughtFloats.capacity() < count * CAUGHT_FLOATS) {
            caughtFloats = floats(count * CAUGHT_FLOATS);
        }
        if (heldFloats.capacity() < floats) {
            heldFloats = floats(floats);
            held = new float[floats];
        }
        GL15.glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER, caught);
        GL15.glBufferData(GL_TRANSFORM_FEEDBACK_BUFFER, (long) count * CAUGHT_FLOATS * Float.BYTES, GL_STREAM_READ);
        GL30.glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, caught);
        GL30.glBeginTransformFeedback(GL_POINTS);
        GL11.glDrawArrays(GL_POINTS, at / SpriteGeometry.VERTEX_FLOATS, count);
        GL30.glEndTransformFeedback();
        caughtFloats.clear().limit(count * CAUGHT_FLOATS);
        GL15.glGetBufferSubData(GL_TRANSFORM_FEEDBACK_BUFFER, 0L, caughtFloats);
        heldFloats.clear().limit(floats);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, buffer);
        GL15.glGetBufferSubData(GL_ARRAY_BUFFER, (long) at * Float.BYTES, heldFloats);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        heldFloats.get(held, 0, floats);
        float[] records = LightPages.records();
        for (int i = 0; i < count; i++) {
            LightSites.decode(held, i * SpriteGeometry.VERTEX_FLOATS, records, decoded);
            int c = i * CAUGHT_FLOATS;
            float red = caughtFloats.get(c), green = caughtFloats.get(c + 1), blue = caughtFloats.get(c + 2), alpha = caughtFloats.get(c + 3);
            float unlit = caughtFloats.get(c + 4), day = caughtFloats.get(c + 5);
            int ulps = Math.max(Math.max(ulps(red, decoded[0]), Math.max(ulps(green, decoded[1]), ulps(blue, decoded[2]))),
                    Math.max(ulps(unlit, decoded[3]), ulps(day, decoded[4])));
            vertices++;
            mostUlps = Math.max(mostUlps, ulps);
            if (Float.floatToRawIntBits(alpha) != Float.floatToRawIntBits(decoded[5]) || ulps > FUSED_ULPS) {
                faults++;
                if (firstFault == null) {
                    firstFault = "worked out " + Arrays.toString(new float[] {red, green, blue, unlit, day, alpha}) + " where the twin gives " + Arrays.toString(decoded)
                            + " from the vertex " + Arrays.toString(Arrays.copyOfRange(held, i * SpriteGeometry.VERTEX_FLOATS, (i + 1) * SpriteGeometry.VERTEX_FLOATS));
                }
            } else if (ulps > 0) {
                fused++;
            }
        }
    }

    /** How many floats apart the card's answer and the twin's are, or the most there are where they differ in sign or one is not a number. */
    private static int ulps(float card, float twin) {
        if (card == twin) {
            return 0;
        }
        if (Float.isNaN(card) || Float.isNaN(twin) || card < 0f != twin < 0f) {
            return Integer.MAX_VALUE;
        }
        return Math.abs(Float.floatToIntBits(card) - Float.floatToIntBits(twin));
    }
}
