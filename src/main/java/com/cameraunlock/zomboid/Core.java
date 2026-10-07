package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;

/**
 * cameraunlock-core, which is CameraUnlockCore.dll beside the mod's jar: the tracker's receiver,
 * the pose pipeline, CameraUnlock.ini, the hotkeys and the log. The mod's Java holds none of them.
 */
final class Core {
    private static CameraUnlock library;

    private Core() {}

    /**
     * Loaded at the first call, which is Log.open's. A DLL that is missing or is not the one
     * this jar was built with throws here, with the reason in words.
     */
    static synchronized CameraUnlock library() {
        if (library == null) {
            library = CameraUnlock.load();
        }
        return library;
    }
}
