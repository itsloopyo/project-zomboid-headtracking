package com.cameraunlock.zomboid;

import java.util.IdentityHashMap;
import zombie.iso.objects.IsoTelevision;
import zombie.radio.devices.DeviceData;

/**
 * What a television that is switched on shows. The game lays a sprite of colour bars over a
 * set that is on, a dozen pixels across, and that is how a set that is on is told here. A set
 * with a channel coming in or a tape playing shows a programme, one of the pictures in the
 * jar made with tools/pictures, and every set on one channel shows the same one, which
 * changes every few seconds. A set with nothing coming in shows the bars, as the game's does.
 *
 * A chunk is not built again for a programme's next picture. The sheet on a set's glass
 * carries its channel where another piece carries its texture, and which picture that is
 * now is worked out when the sheet is drawn.
 */
final class Screens {
    /** What a set shows: nothing, the bars, or a programme. */
    static final int DARK = -1, BARS = 0, PROGRAMME = 1;
    /** How many programmes the jar has: screen-00 and on, and each again in black and white as screen-grey-00. */
    static final int PROGRAMMES = 12;
    /** How long a set shows one picture of a programme. */
    static final long PICTURE_NANOS = 2_000_000_000L;
    /** How far in front of its glass a set's picture stands, in the set's metres. */
    static final float PROUD = 0.004f;
    /** How many times as wide as it is high every picture a set shows is. */
    static final float WIDE_FOR_HIGH = 4f / 3f;
    /**
     * What a sheet showing a programme carries is this or less: this, less twice the channel,
     * less one for a set with no colour. Decor's own pictures are the numbers from -1 down to
     * the count of them, nowhere near it.
     */
    private static final int LIVE = -(1 << 24);
    private static final int COLOUR = 0, GREY = 1;

    /**
     * Each picture's number in Decor, in colour and in black and white: the bars, then the
     * programmes. Made by the logic thread the first time a set is on, read by the render
     * thread after.
     */
    private static volatile int[][] pictures;
    /** How many of the programmes, in colour and in black and white, the logic thread has still to ask Decor to read. */
    private static final int[] toRead = new int[2];
    /** Where each set's glass is on its model: the least and greatest x, the least and greatest z, and the y of its front. */
    private static final IdentityHashMap<Models.Model, float[]> glasses = new IdentityHashMap<>();

    private Screens() {}

    /** What a set shows now. */
    static int showing(IsoTelevision set) {
        if (set.getOverlaySprite() == null) {
            return DARK;
        }
        DeviceData device = set.getDeviceData();
        return device.isReceivingSignal() || device.isPlayingMedia() ? PROGRAMME : BARS;
    }

    /** Which programme a channel shows at a time: the same for every set tuned to it, and another every PICTURE_NANOS. */
    static int programme(int channel, long nanos) {
        long mixed = (channel * 0x9E3779B97F4A7C15L ^ Math.floorDiv(nanos, PICTURE_NANOS)) * 0xBF58476D1CE4E5B9L;
        return (int) Math.floorMod(mixed ^ mixed >>> 31, (long) PROGRAMMES);
    }

    /** The name in the jar of the bars, or of a programme, in colour or in black and white. */
    static String named(int showing, int programme, boolean inGrey) {
        String tint = inGrey ? "screen-grey-" : "screen-";
        return showing == BARS ? tint + "bars" : tint + String.format("%02d", programme);
    }

    private static int[][] pictures() {
        int[][] made = pictures;
        if (made == null) {
            made = new int[2][1 + PROGRAMMES];
            for (int tint = COLOUR; tint <= GREY; tint++) {
                made[tint][0] = Decor.named(named(BARS, 0, tint == GREY));
                for (int i = 0; i < PROGRAMMES; i++) {
                    made[tint][1 + i] = Decor.named(named(PROGRAMME, i, tint == GREY));
                }
            }
            pictures = made;
        }
        return made;
    }

    /**
     * Logic thread: what the sheet on the glass of a set that is on carries where another piece
     * carries its texture, for the bars or for a programme, which Decor.texture turns into the
     * picture's texture. The picture the set shows now is read from the jar here, and
     * readAhead reads the rest of a channel's.
     */
    static int held(IsoTelevision set, int showing, boolean inGrey) {
        int tint = inGrey ? GREY : COLOUR;
        if (showing == BARS) {
            return Decor.held(pictures()[tint][0]);
        }
        int channel = set.getDeviceData().getChannel();
        Decor.held(pictures()[tint][1 + programme(channel, System.nanoTime())]);
        toRead[tint] = PROGRAMMES;
        return LIVE - (channel * 2 + tint);
    }

    /**
     * Logic thread, once a frame: reads one more of the programmes a set in a build shows, so
     * that the render thread finds each one read when its time comes and has only to hand it
     * to the graphics card.
     */
    static void readAhead() {
        for (int tint = COLOUR; tint <= GREY; tint++) {
            if (toRead[tint] > 0) {
                Decor.held(pictures[tint][toRead[tint]--]);
            }
        }
    }

    /** Whether what a piece carries is a channel, and not one picture. */
    static boolean live(int held) {
        return held <= LIVE;
    }

    /** Render thread: the picture in Decor that a sheet carrying a channel shows now. */
    static int now(int held) {
        int coded = LIVE - held;
        return pictures[coded & 1][1 + programme(coded >> 1, System.nanoTime())];
    }

    /** Where a set's glass is on its model: see glasses. Thrown for a model that has none. */
    static float[] glass(Models.Model model) {
        float[] glass = glasses.get(model);
        if (glass == null) {
            int from = model.first[Models.SCREEN], to = model.first[Models.SCREEN + 1];
            if (from == to) {
                throw new IllegalStateException("the model " + model.name + " has no screen to show a picture on");
            }
            glass = new float[] {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE};
            for (int vertex = from; vertex < to; vertex++) {
                float x = model.vertices[vertex * Models.VERTEX_FLOATS], y = model.vertices[vertex * Models.VERTEX_FLOATS + 1];
                float z = model.vertices[vertex * Models.VERTEX_FLOATS + 2];
                glass[0] = Math.min(glass[0], x);
                glass[1] = Math.max(glass[1], x);
                glass[2] = Math.min(glass[2], z);
                glass[3] = Math.max(glass[3], z);
                glass[4] = Math.min(glass[4], y);
            }
            glasses.put(model, glass);
        }
        return glass;
    }
}
