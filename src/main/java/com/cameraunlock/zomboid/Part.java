package com.cameraunlock.zomboid;

/**
 * The parts of the mod a row of CameraUnlock.ini switches off, each on until the player unticks
 * it. A part made of patches of the game's code is read as the game starts: off then, its patches
 * are left out of the game until a start with it on (Agent.sets), so another mod that patches the
 * same methods meets the game's own, and its row says so. A part the mod does by itself is read
 * again while the game runs. A part can be both, as FirstPersonEnabled is.
 *
 * The thread that watches the file and the game's options screen set them; the logic and render
 * threads ask them.
 */
enum Part {
    FIRST_PERSON("General", "FirstPersonEnabled", true, true,
            "true: the mod's first person view. false: there is none, and the isometric view is the game's own whatever\n"
                    + "IsometricQuality says. The first person key, a controller's double tap of Back and FirstPerson do nothing, and\n"
                    + "nothing more is made ready for first person while you play or while a save loads; what was already made stays\n"
                    + "until you leave the save.");

    final String section, key, comment;
    /** Whether the row is taken while the game runs, and whether it is read at the start to leave patches out. */
    final boolean live, start;
    private volatile boolean on = true, atStart = true;

    Part(String section, String key, boolean live, boolean start, String comment) {
        this.section = section;
        this.key = key;
        this.live = live;
        this.start = start;
        this.comment = start ? comment + "\n" + (live ? LIVE_AND_NEXT_START : NEXT_START) : comment;
    }

    /** The last line of the row of a part made of patches, saying when off is taken. */
    static final String NEXT_START = "Taken at the next start. Off, its patches of the game's code are left out.",
            LIVE_AND_NEXT_START = "Taken at once. Off, its patches of the game's code are left out from the next start.";

    /** Whether the part is on now: as the start found it, for a part not taken while the game runs. */
    boolean on() {
        return on;
    }

    /** Whether the part was on as the game started, which decides whether its patches are in the game's code. */
    boolean atStart() {
        return atStart;
    }

    /** Whether the part works now: on, and for a part of patches, on as the game started too, so its patches are in. */
    boolean running() {
        return on && (atStart || !start);
    }

    /** Each part's bit in what Config reads: the part's ordinal. */
    long bit() {
        return 1L << ordinal();
    }

    /** Every part as the file holds it at the start, by its bit. */
    static void start(long bits) {
        for (Part part : values()) {
            part.atStart = part.on = (bits & part.bit()) != 0;
        }
    }

    /** The parts taken while the game runs as the file holds them now, by their bits. The rest stay as the start found them. */
    static void take(long bits) {
        for (Part part : values()) {
            if (part.live) {
                part.on = (bits & part.bit()) != 0;
            }
        }
    }

    /** A part taken while the game runs, set from the options screen. */
    void set(boolean to) {
        if (!live) {
            throw new IllegalStateException(key + " is read only as the game starts");
        }
        on = to;
    }

    /** The parts that are off now, in words, for the log. */
    static String described() {
        StringBuilder off = new StringBuilder();
        for (Part part : values()) {
            if (!part.on || part.start && !part.atStart) {
                off.append(off.length() == 0 ? "" : ", ").append(part.key).append(part.on ? " on from the next start" : " off");
            }
        }
        return off.length() == 0 ? "every part of the mod is on" : "switched off: " + off;
    }
}
