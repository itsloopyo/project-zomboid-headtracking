package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

public final class GL11 {
    private static RuntimeException stub() {
        return new UnsupportedOperationException("reference stub");
    }

    public static void glEnable(int cap) { throw stub(); }
    public static void glDisable(int cap) { throw stub(); }
    public static void glCullFace(int mode) { throw stub(); }
    public static void glDrawElements(int mode, int count, int type, long indices) { throw stub(); }
    public static void glBegin(int mode) { throw stub(); }
    public static void glEnd() { throw stub(); }
    public static void glBindTexture(int target, int texture) { throw stub(); }
    public static void glColor3f(float r, float g, float b) { throw stub(); }
    public static void glTexCoord2f(float u, float v) { throw stub(); }
    public static void glTexEnvi(int target, int pname, int param) { throw stub(); }
    public static void glClear(int mask) { throw stub(); }
    public static void glClearColor(float r, float g, float b, float a) { throw stub(); }
    public static void glClearDepth(double depth) { throw stub(); }
    public static void glColorMask(boolean r, boolean g, boolean b, boolean a) { throw stub(); }
    public static void glDepthMask(boolean flag) { throw stub(); }
    public static void glDepthFunc(int func) { throw stub(); }
    public static void glAlphaFunc(int func, float ref) { throw stub(); }
    public static void glVertex3f(float x, float y, float z) { throw stub(); }
    public static int glGetInteger(int pname) { throw stub(); }
    public static int glGetTexLevelParameteri(int target, int level, int pname) { throw stub(); }
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
    public static void glFogi(int pname, int param) { throw stub(); }
    public static void glFinish() { throw stub(); }
    public static void glDrawArrays(int mode, int first, int count) { throw stub(); }
    public static void glEnableClientState(int array) { throw stub(); }
    public static void glDisableClientState(int array) { throw stub(); }
    public static void glVertexPointer(int size, int type, int stride, FloatBuffer pointer) { throw stub(); }
    public static void glTexCoordPointer(int size, int type, int stride, FloatBuffer pointer) { throw stub(); }
    public static void glColorPointer(int size, int type, int stride, FloatBuffer pointer) { throw stub(); }
    public static void glNormalPointer(int type, int stride, FloatBuffer pointer) { throw stub(); }
    public static void glPolygonOffset(float factor, float units) { throw stub(); }
    public static void glDrawBuffer(int buffer) { throw stub(); }
    public static void glReadBuffer(int buffer) { throw stub(); }
    public static void glTexImage2D(int target, int level, int internalFormat, int width, int height, int border, int format,
            int type, ByteBuffer pixels) { throw stub(); }
    public static void glCopyTexImage2D(int target, int level, int internalFormat, int x, int y, int width, int height, int border) { throw stub(); }
    public static int glGenTextures() { throw stub(); }
    public static void glTexParameteri(int target, int pname, int param) { throw stub(); }
    public static void glBlendFunc(int source, int destination) { throw stub(); }
    public static void glVertex2f(float x, float y) { throw stub(); }
    public static void glPushClientAttrib(int mask) { throw stub(); }
    public static void glPopClientAttrib() { throw stub(); }
    public static void glFogf(int pname, float param) { throw stub(); }
    public static void glFogfv(int pname, float[] params) { throw stub(); }
    public static void glLoadIdentity() { throw new UnsupportedOperationException("reference stub"); }
}
