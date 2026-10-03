package org.lwjgl.system.windows;

import java.nio.IntBuffer;

public final class User32 {
    private static RuntimeException stub() {
        return new UnsupportedOperationException("reference stub");
    }

    public static boolean GetWindowRect(IntBuffer lastError, long window, RECT rect) { throw stub(); }
    public static long MonitorFromWindow(long window, int flags) { throw stub(); }
    public static boolean GetMonitorInfo(long monitor, MONITORINFOEX info) { throw stub(); }
    public static boolean SetWindowPos(IntBuffer lastError, long window, long insertAfter, int x, int y, int width, int height, int flags) { throw stub(); }
    public static boolean IsZoomed(long window) { throw stub(); }
}
