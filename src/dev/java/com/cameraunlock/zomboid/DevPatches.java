package com.cameraunlock.zomboid;

import java.util.List;

/**
 * What a development build patches in the game on top of what every build does. Apart from
 * DevKeys, which names the game's player class: the agent reads this as it starts, and a class
 * loaded before the patches are in place brings the game's classes in unpatched.
 */
final class DevPatches {
    private DevPatches() {}

    /** The invincible key: see DevKeys.cheatsAllowed. */
    static List<Agent.Patch> list() {
        return List.of(new Agent.Patch("zombie/characters/PlayerCheats", "isCheatAllowed before its returns", classBytes -> CallSite.filterReturn(classBytes,
                new CallSite.Method("zombie/characters/PlayerCheats", "isCheatAllowed", "()Z"),
                new CallSite.Method("com/cameraunlock/zomboid/DevKeys", "cheatsAllowed", "(Z)Z"))));
    }
}
