package com.cameraunlock.zomboid;

/** The build that ships cannot make the graphics card look smaller, fill it or refuse it: see the dev build's DevVideoMemory. */
final class DevVideoMemory {
    private DevVideoMemory() {}

    static int pretendMb() {
        return 0;
    }

    static int startStep() {
        return -1;
    }

    static int ballast() {
        return 0;
    }

    static long worldBufferBytes(long bytes) {
        return bytes;
    }

    static void touch() {}
}
