package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;

/**
 * cameraunlock-core, carried in the mod's jar as JVM classes (cameraunlock-core/docs/core-jar.md):
 * the tracker's receiver, the pose pipeline, CameraUnlock.ini, the hotkeys and the log. The mod's
 * Java holds none of them.
 */
final class Core {
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
