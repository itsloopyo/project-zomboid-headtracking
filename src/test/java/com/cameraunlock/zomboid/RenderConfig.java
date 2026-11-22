package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the CameraUnlock.ini a first start creates, from the rows Config describes, to the first
 * path given, and the same rows as the JSON Headstart's settings page reads to the second.
 * `pixi run render-config` rewrites config/CameraUnlock.ini and config/cameraunlock-settings.json
 * with it after a change to a row, a comment or a default, and CoreTests holds both committed
 * files to it.
 */
public final class RenderConfig {
    private RenderConfig() {}

    public static void main(String[] arguments) throws IOException {
        Config config = new Config(CameraUnlock.loadBundled());
        config.render(Path.of(arguments[0]));
        Files.writeString(Path.of(arguments[1]), config.schema());
    }
}
