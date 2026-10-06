package com.cameraunlock.zomboid;

import java.util.List;

/** The build that ships patches nothing for development: see DevKeys. */
final class DevPatches {
    private DevPatches() {}

    static List<Agent.Patch> list() {
        return List.of();
    }
}
