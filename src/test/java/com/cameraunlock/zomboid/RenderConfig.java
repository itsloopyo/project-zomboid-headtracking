package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.nio.file.Path;

/**
 * Writes the CameraUnlock.ini a first start creates, from the rows Config describes, to the path
 * given. `pixi run render-config` rewrites config/CameraUnlock.ini with it after a change to a
 * row, a comment or a default, and CoreTests holds the committed file to it.
 */
public final class RenderConfig {
    private RenderConfig() {}

    public static void main(String[] arguments) {
        new Config(CameraUnlock.load()).render(Path.of(arguments[0]));
    }
}
