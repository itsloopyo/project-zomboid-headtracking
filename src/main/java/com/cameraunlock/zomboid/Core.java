package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;

/**
 * cameraunlock-core, carried in the mod's jar as JVM classes (cameraunlock-core/docs/core-jar.md):
 * the tracker's receiver, the pose pipeline, CameraUnlock.ini, the hotkeys and the log. The mod's
 * Java holds none of them.
 */
final class Core {
    /** Where core reads the keys and reaches the window itself. Elsewhere (Linux) the mod gives it the keys (LinuxKeys), and the window is left alone. */
    static final boolean WINDOWS = System.getProperty("os.name").startsWith("Windows");

    private static CameraUnlock library;

    private Core() {}

    /** Started at the first call, which is Log.open's. */
    static synchronized CameraUnlock library() {
        if (library == null) {
            library = CameraUnlock.loadBundled();
        }
        return library;
    }
}
