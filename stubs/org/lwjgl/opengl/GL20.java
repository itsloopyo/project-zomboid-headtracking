package org.lwjgl.opengl;

public final class GL20 {
    public static void glUseProgram(int program) {
        throw new UnsupportedOperationException("reference stub");
    }
    public static int glCreateProgram() { throw new UnsupportedOperationException("reference stub"); }
    public static int glCreateShader(int type) { throw new UnsupportedOperationException("reference stub"); }
    public static void glAttachShader(int program, int shader) { throw new UnsupportedOperationException("reference stub"); }
    public static void glShaderSource(int shader, CharSequence source) { throw new UnsupportedOperationException("reference stub"); }
    public static void glCompileShader(int shader) { throw new UnsupportedOperationException("reference stub"); }
    public static void glLinkProgram(int program) { throw new UnsupportedOperationException("reference stub"); }
    public static int glGetShaderi(int shader, int pname) { throw new UnsupportedOperationException("reference stub"); }
    public static int glGetProgrami(int program, int pname) { throw new UnsupportedOperationException("reference stub"); }
    public static String glGetShaderInfoLog(int shader) { throw new UnsupportedOperationException("reference stub"); }
    public static String glGetProgramInfoLog(int program) { throw new UnsupportedOperationException("reference stub"); }
    public static int glGetUniformLocation(int program, CharSequence name) { throw new UnsupportedOperationException("reference stub"); }
    public static void glUniform1i(int location, int value) { throw new UnsupportedOperationException("reference stub"); }
    public static void glUniform1f(int location, float x) { throw new UnsupportedOperationException("reference stub"); }
    public static void glUniform2f(int location, float x, float y) { throw new UnsupportedOperationException("reference stub"); }
    public static void glUniform3f(int location, float x, float y, float z) { throw new UnsupportedOperationException("reference stub"); }
    public static void glUniform4f(int location, float x, float y, float z, float w) { throw new UnsupportedOperationException("reference stub"); }
    public static void glUniformMatrix4fv(int location, boolean transpose, float[] value) { throw new UnsupportedOperationException("reference stub"); }
    public static void glUniformMatrix4fv(int location, boolean transpose, java.nio.FloatBuffer value) { throw new UnsupportedOperationException("reference stub"); }
    public static void glDisableVertexAttribArray(int index) { throw new UnsupportedOperationException("reference stub"); }
    public static void glEnableVertexAttribArray(int index) { throw new UnsupportedOperationException("reference stub"); }
    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long pointer) { throw new UnsupportedOperationException("reference stub"); }
    public static void glBindAttribLocation(int program, int index, CharSequence name) { throw new UnsupportedOperationException("reference stub"); }
}
