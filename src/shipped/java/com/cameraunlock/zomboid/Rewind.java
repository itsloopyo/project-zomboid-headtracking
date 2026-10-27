package com.cameraunlock.zomboid;

/** The build that ships has no Rewind 30s: a death is not yet rewound reliably enough. See DevKeys for how the two builds differ. */
final class Rewind {
    private Rewind() {}

    static void tick() {}

    static void leaving() {}

    static void forget() {}
}
