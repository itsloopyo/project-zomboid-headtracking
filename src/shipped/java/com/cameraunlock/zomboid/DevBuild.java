package com.cameraunlock.zomboid;

/**
 * Whether this is a development build. The build that ships says no, so the log lines kept for
 * development (each zombie wound, each lean against a wall, the numbers a model is fitted by)
 * are compiled out of it: see DevKeys for how the two builds differ.
 */
final class DevBuild {
    static final boolean ON = false;

    private DevBuild() {}
}
