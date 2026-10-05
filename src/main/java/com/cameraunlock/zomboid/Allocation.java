package com.cameraunlock.zomboid;

import java.lang.management.ManagementFactory;

/** How much memory a thread has asked for, for the trace: what the picture takes a frame is what the collector has to clear. */
final class Allocation {
    private static final com.sun.management.ThreadMXBean THREADS = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    private Allocation() {}

    /** Bytes the calling thread has allocated since it started. */
    static long byThisThread() {
        return THREADS.getCurrentThreadAllocatedBytes();
    }
}
