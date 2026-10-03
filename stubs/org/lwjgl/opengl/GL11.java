package org.lwjgl.opengl;

import java.nio.ByteBuffer;

public final class GL11 {
    private static RuntimeException stub() {
        return new UnsupportedOperationException("reference stub");
    }

    public static void glEnable(int cap) { throw stub(); }
    public static void glDisable(int cap) { throw stub(); }
    public static void glBegin(int mode) { throw stub(); }
    public static void glEnd() { throw stub(); }
    public static void glBindTexture(int target, int texture) { throw stub(); }
    public static void glColor3f(float r, float g, float b) { throw stub(); }
    public static void glVertex3f(float x, float y, float z) { throw stub(); }
    public static int glGetInteger(int pname) { throw stub(); }
    public static void glGetIntegerv(int pname, int[] params) { throw stub(); }
    public static int glGetError() { throw stub(); }
    public static String glGetString(int name) { throw stub(); }
    public static void glLoadMatrixf(float[] m) { throw stub(); }
    public static void glMatrixMode(int mode) { throw stub(); }
    public static void glPushMatrix() { throw stub(); }
    public static void glPopMatrix() { throw stub(); }
    public static void glPushAttrib(int mask) { throw stub(); }
    public static void glPopAttrib() { throw stub(); }
    public static void glPixelStorei(int pname, int param) { throw stub(); }
    public static void glReadPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels) { throw stub(); }
    public static void glViewport(int x, int y, int w, int h) { throw stub(); }
}
