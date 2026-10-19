package com.cameraunlock.zomboid;

import java.nio.FloatBuffer;
import org.lwjgl.util.vector.Matrix;
import org.lwjgl.util.vector.Matrix4f;

/** The game's copy of a character's bones for the graphics card, made in fewer steps. Apart from GameFixes, whose tests run without the game's matrices. */
public final class BoneMatrices {
    private BoneMatrices() {}

    private static final ThreadLocal<float[]> MATRIX = ThreadLocal.withInitial(() -> new float[16]);

    /**
     * In place of Matrix4f.store(FloatBuffer) as the game hands a character's bones to the
     * graphics card: the game's copy puts a matrix in a number at a time, and in memory outside
     * the Java heap every number put is checked against that memory's lifetime, which with a
     * crowd drawn was a twentieth of the main thread. The same sixteen numbers go in at once, in
     * the game's order. Called from the game's code.
     */
    public static Matrix store(Matrix4f m, FloatBuffer buffer) {
        float[] numbers = MATRIX.get();
        numbers[0] = m.m00;
        numbers[1] = m.m01;
        numbers[2] = m.m02;
        numbers[3] = m.m03;
        numbers[4] = m.m10;
        numbers[5] = m.m11;
        numbers[6] = m.m12;
        numbers[7] = m.m13;
        numbers[8] = m.m20;
        numbers[9] = m.m21;
        numbers[10] = m.m22;
        numbers[11] = m.m23;
        numbers[12] = m.m30;
        numbers[13] = m.m31;
        numbers[14] = m.m32;
        numbers[15] = m.m33;
        buffer.put(numbers);
        return m;
    }
}
