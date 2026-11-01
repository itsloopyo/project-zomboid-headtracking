package org.lwjgl.util.vector;

public class Matrix4f extends Matrix {
    public float m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23, m30, m31, m32, m33;
    public Matrix setIdentity() { throw new UnsupportedOperationException("reference stub"); }
    public static Matrix4f mul(Matrix4f left, Matrix4f right, Matrix4f dest) { throw new UnsupportedOperationException("reference stub"); }
    public static Matrix4f invert(Matrix4f src, Matrix4f dest) { throw new UnsupportedOperationException("reference stub"); }
}
