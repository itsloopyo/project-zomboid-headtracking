package org.lwjgl.system.windows;

public final class MONITORINFOEX {
    // Assigned in a static block as LWJGL's class does, so the compiler
    // reads the field at run time and does not bake this value into the agent.
    public static final int SIZEOF;

    static {
        SIZEOF = 0;
    }

    private static RuntimeException stub() {
        return new UnsupportedOperationException("reference stub");
    }

    public static MONITORINFOEX create() { throw stub(); }
    public MONITORINFOEX cbSize(int value) { throw stub(); }
    public RECT rcWork() { throw stub(); }
}
