package com.cameraunlock.zomboid;

/**
 * The build that ships has no development keys. A development build is compiled with the
 * class of this name in src/dev in this one's place, and nothing in a shipped jar can switch
 * those keys on.
 */
final class DevKeys {
    static final boolean PRESENT = false;

    private DevKeys() {}

    static void poll() {}
}
