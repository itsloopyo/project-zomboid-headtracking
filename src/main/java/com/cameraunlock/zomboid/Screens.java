package com.cameraunlock.zomboid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLongArray;
import zombie.iso.objects.IsoTelevision;
import zombie.radio.devices.DeviceData;
import zombie.radio.media.MediaData;
import zombie.radio.scripting.RadioBroadCast;
import zombie.radio.scripting.RadioChannel;
import zombie.radio.scripting.RadioScriptManager;

/**
 * What a television that is switched on shows. The game lays a sprite of colour bars over a
 * set that is on, a dozen pixels across, and that is how a set that is on is told here. A set
 * with nothing coming in shows the bars, as the game's does. A set with a channel coming in or
 * a tape playing shows the show that is on: the game says which broadcast a channel is airing
 * and which tape is in a set, Shows says which show each is, and the jar has the shots of
 * each show made with tools/pictures, or none, when the set shows the bars. Every set on one
 * channel shows the same.
 *
 * A chunk is not built again as the picture changes. The sheet on a set's glass carries
 * where its picture comes from, a channel or a tape, where another piece carries its texture,
 * and which shot of which show that is now, and what part of the shot, is worked out when the
 * sheet is drawn.
 */
final class Screens {
    /** What a set shows: nothing, the bars, or a programme. */
    static final int DARK = -1, BARS = 0, PROGRAMME = 1;
    /** How far in front of its glass a set's picture stands, in the set's metres. */
    static final float PROUD = 0.004f;
    /** How many times as wide as it is high every picture a set shows is. */
    static final float WIDE_FOR_HIGH = 4f / 3f;
    /** What the picture's program is told of a tube beside its picture: one in colour, one with none, one showing snow. */
    static final float IN_COLOUR = 0f, IN_GREY = 1f, SNOW = 2f;
    /**
     * What a sheet showing a programme carries is this or less: this, less the number of where
     * its picture comes from. Decor's own pictures are the numbers from -1 down to the count
     * of them, nowhere near it.
     */
    private static final int LIVE = -(1 << 24);
    /** How long after a set showing it was last drawn a channel is still asked what is on it, and its show's shots are kept read. */
    private static final long WATCHED_NANOS = 10_000_000_000L;
    /** How long after the last set showing it was drawn a show's shots are given back to the graphics card. */
    private static final long KEPT_NANOS = 60_000_000_000L;
    /** How often a watched channel is asked what is on it, and how often the shots to give back are looked for. */
    private static final long ASKED_NANOS = 250_000_000L, SWEPT_NANOS = 1_000_000_000L;
    private static final String TABLE = "/pictures/shows.txt";

    /** The show each source shows and when a set showing each was last drawn, by the source's number. */
    private record Sources(AtomicIntegerArray show, AtomicLongArray drawn) {}

    /** Read by the logic thread the first time a set shows a programme. */
    private static volatile Shows shows;
    /** Each shot's picture in Decor by its show, and the bars in colour and with none. Made with shows. */
    private static int[][] shots;
    private static volatile int[] bars;
    /** How many of each show's shots the logic thread has asked Decor to read: none once the render thread has given them back. */
    private static AtomicIntegerArray asked;

    /** Logic thread: the number of each source, a channel's frequency or a tape's id, and each one's key by its number. */
    private static final Map<Object, Integer> numbers = new HashMap<>();
    private static final List<Object> keys = new ArrayList<>();
    private static volatile Sources sources = new Sources(new AtomicIntegerArray(16), new AtomicLongArray(16));
    private static long askedAt = System.nanoTime(), sweptAt = askedAt;

    /** Where each set's glass is on its model: the least and greatest x, the least and greatest z, and the y of its front. */
    private static final Known<Models.Model, float[]> glasses = new Known<>();
    /** What Shows.cut fills, one for each thread. */
    private static final float[] built = new float[Shows.CUT_FLOATS], cut = new float[Shows.CUT_FLOATS];
    /** What Shows.cut fills for glow, on the logic thread. */
    private static final float[] glowCut = new float[Shows.CUT_FLOATS];
    /**
     * The mean linear light of snow as the picture's program draws it, 0.08 + 0.8 u^2 for an
     * even u, taken to the power 2.2.
     */
    static final float SNOW_GLOW = 0.159f;

    private Screens() {}

    /** What a set shows now. */
    static int showing(IsoTelevision set) {
        if (set.getOverlaySprite() == null) {
            return DARK;
        }
        DeviceData device = set.getDeviceData();
        return device.isReceivingSignal() || device.isPlayingMedia() ? PROGRAMME : BARS;
    }

    /** Where the programme of a set showing one comes from: the id of the tape it plays, or its channel's frequency. */
    private static Object source(DeviceData device) {
        // A set a server says is playing can have no tape this machine knows of: it is on its channel then.
        MediaData tape = device.isPlayingMedia() ? device.getMediaData() : null;
        return tape != null ? tape.getId() : (Object) device.getChannel();
    }

    /** A number that is another when a set showing a programme takes it from somewhere else, for a chunk's sum. */
    static int from(IsoTelevision set) {
        return source(set.getDeviceData()).hashCode();
    }

    private static Shows shows() {
        Shows read = shows;
        if (read == null) {
            try (InputStream in = Screens.class.getResourceAsStream(TABLE)) {
                if (in == null) {
                    throw new IOException("the jar has no " + TABLE);
                }
                read = Shows.read(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)), TABLE);
            } catch (IOException e) {
                throw new UncheckedIOException("the jar's table of television shows could not be read", e);
            }
            int[][] pictures = new int[read.count()][];
            for (int show = 0; show < pictures.length; show++) {
                pictures[show] = new int[read.shots(show)];
                for (int shot = 0; shot < pictures[show].length; shot++) {
                    pictures[show][shot] = Decor.named(read.picture(show, shot));
                }
            }
            shots = pictures;
            asked = new AtomicIntegerArray(read.count());
            shows = read;
        }
        return read;
    }

    private static int[] bars() {
        int[] made = bars;
        if (made == null) {
            bars = made = new int[] {Decor.named("screen-bars"), Decor.named("screen-grey-bars")};
        }
        return made;
    }

    /** The show a source is showing now, by what the game says is on. */
    private static int showOf(Object source) {
        if (source instanceof String tape) {
            return shows().taped(tape);
        }
        int frequency = (Integer) source;
        RadioChannel channel = RadioScriptManager.getInstance().getChannels().get(frequency);
        if (channel == null) {
            // A channel the game keeps no script of on this machine: one a server airs, or another mod's.
            return shows().aired(frequency, null);
        }
        // Between a broadcast's last line and the set's losing the signal the channel has nothing airing: the set still shows what was.
        RadioBroadCast airing = channel.getAiringBroadcast();
        return shows().aired(frequency, airing != null ? airing.getID() : channel.getLastBroadcastID());
    }

    /**
     * Logic thread: what the sheet on the glass of a set that is on carries where another piece
     * carries its texture, for the bars or for a programme, which the picture's program turns
     * into a texture with now. The shot a set showing a programme shows first is asked of
     * Decor here, and tick asks for the rest of its show's.
     */
    static int held(IsoTelevision set, int showing, boolean inGrey) {
        if (showing == BARS) {
            return Decor.held(bars()[inGrey ? 1 : 0]);
        }
        shows();
        Object source = source(set.getDeviceData());
        Integer number = numbers.get(source);
        long now = System.nanoTime();
        if (number == null) {
            number = keys.size();
            int show = showOf(source);
            Sources known = sources;
            Sources grown = number < known.show.length() ? known : new Sources(new AtomicIntegerArray(number * 2), new AtomicLongArray(number * 2));
            for (int i = 0; grown != known && i < number; i++) {
                grown.show.set(i, known.show.get(i));
                grown.drawn.set(i, known.drawn.get(i));
            }
            // The render thread is shown the source with its show and its time on it, so that it never gives back a show it is about to draw.
            grown.show.set(number, show);
            grown.drawn.set(number, now);
            sources = grown;
            said(source, show);
            numbers.put(source, number);
            keys.add(source);
        }
        // A set in a build is about to be drawn: its source is watched from now.
        sources.drawn.set(number, now);
        read(sources.show.get(number), now);
        return LIVE - number;
    }

    /** Logic thread: reads the picture a show shows at a time, for the render thread to find read. */
    private static void read(int show, long now) {
        if (shots[show].length == 0) {
            Decor.held(bars()[0]);
            return;
        }
        shows.cut(show, now, built);
        Decor.held(shots[show][(int) built[Shows.SHOT]]);
    }

    /**
     * Logic thread, once a frame: asks each channel a set in view is on what it is airing, and
     * asks for one more shot of a show in view, so that each is most often read by the time
     * its cut comes and the render thread has only to hand it to the graphics card.
     */
    static void tick() {
        if (shows == null) {
            return;
        }
        long now = System.nanoTime();
        Sources known = sources;
        boolean ask = now - askedAt >= ASKED_NANOS;
        if (ask) {
            askedAt = now;
        }
        boolean read = false;
        for (int number = 0; number < keys.size(); number++) {
            if (now - known.drawn.get(number) > WATCHED_NANOS) {
                continue;
            }
            if (ask) {
                int was = known.show.get(number), is = showOf(keys.get(number));
                if (is != was) {
                    known.show.set(number, is);
                    said(keys.get(number), is);
                }
            }
            int show = known.show.get(number), done = asked.get(show);
            // The shot showing now first, whichever it is: a show that has just come on is drawn this frame.
            read(show, now);
            if (!read && done < shots[show].length) {
                Decor.held(shots[show][done]);
                // Left as it is where the render thread gave the show's shots back meanwhile: they are asked for again from the first.
                asked.compareAndSet(show, done, done + 1);
                read = true;
            }
        }
    }

    private static void said(Object source, int show) {
        if (DevBuild.ON) {
            Log.info("a television " + (source instanceof String ? "playing the tape " : "on channel ") + source + " shows " + shows.name(show));
        }
    }

    /**
     * Logic thread: into out the mean linear colour of what the glass of a set that is on shows
     * now in the picture, as FurnishingBuilder has it drawn: the bars, a programme's shot and the
     * part of it the glass shows, or snow, with no colour on a set that has none. False where
     * that picture has not been read yet, or the set's programme has not been drawn yet.
     */
    static boolean glow(IsoTelevision set, int playerIndex, float[] out) {
        Furniture piece = set.getSprite() == null ? null : Furniture.known(set.getSprite());
        boolean inGrey = piece != null && piece.blackAndWhite;
        boolean shown;
        if (piece == null || showing(set) != PROGRAMME || !Own.pictures(playerIndex)) {
            shown = Decor.glow(bars()[inGrey ? 1 : 0], 0.5f, 0.5f, 1f, out);
        } else {
            shows();
            Integer number = numbers.get(source(set.getDeviceData()));
            if (number == null) {
                return false;
            }
            int show = sources.show.get(number);
            if (show == Shows.SNOW) {
                out[0] = out[1] = out[2] = SNOW_GLOW;
                return true;
            }
            if (shows.shots(show) == 0) {
                return Decor.glow(bars()[inGrey ? 1 : 0], 0.5f, 0.5f, 1f, out);
            }
            shows.cut(show, System.nanoTime(), glowCut);
            shown = Decor.glow(shots[show][(int) glowCut[Shows.SHOT]], glowCut[Shows.ACROSS], glowCut[Shows.DOWN], glowCut[Shows.OF], out);
        }
        if (shown && inGrey) {
            out[0] = out[1] = out[2] = 0.2126f * out[0] + 0.7152f * out[1] + 0.0722f * out[2];
        }
        return shown;
    }

    /** Whether what a piece carries is where a programme comes from, and not one picture. */
    static boolean live(int held) {
        return held <= LIVE;
    }

    /**
     * Render thread: what a sheet carrying where its programme comes from shows now, as Decor
     * holds a picture, and into tube the part of that picture the glass shows: where across
     * and down the picture its middle is, how much of the picture's width and height it is of,
     * and SNOW for a show that is interference, whose picture is not looked at.
     */
    static int now(int held, float[] tube) {
        int number = LIVE - held;
        long now = System.nanoTime();
        Sources known = sources;
        known.drawn.set(number, now);
        int show = known.show.get(number);
        if (shows.shots(show) == 0) {
            tube[0] = tube[1] = 0.5f;
            tube[2] = 1f;
            tube[3] = show == Shows.SNOW ? SNOW : IN_COLOUR;
            return -1 - bars[0];
        }
        shows.cut(show, now, cut);
        int picture = shots[show][(int) cut[Shows.SHOT]];
        if (shownLast.length <= number * SHOWN) {
            int was = shownLast.length;
            shownLast = java.util.Arrays.copyOf(shownLast, Math.max(16, number * 2 + 2) * SHOWN);
            java.util.Arrays.fill(shownLast, was, shownLast.length, -1f);
        }
        int at = number * SHOWN;
        if (!Decor.ready(picture) && shownLast[at] >= 0f && Decor.ready((int) shownLast[at])) {
            // The cut's shot is not read yet, and drawn now it would be nothing: the set holds the shot it showed last.
            System.arraycopy(shownLast, at + 1, tube, 0, 4);
            return -1 - (int) shownLast[at];
        }
        tube[0] = cut[Shows.ACROSS];
        tube[1] = cut[Shows.DOWN];
        tube[2] = cut[Shows.OF];
        tube[3] = IN_COLOUR;
        shownLast[at] = picture;
        System.arraycopy(tube, 0, shownLast, at + 1, 4);
        return -1 - picture;
    }

    /** Render thread: for each source, the shot it was last shown at and the part of it, -1 for none yet. */
    private static final int SHOWN = 5;
    private static float[] shownLast = new float[0];

    /**
     * Render thread, once a frame before any set is drawn: gives back to the graphics card the
     * shots of every show no set drawn in the last while shows.
     */
    static void giveBack() {
        long now = System.nanoTime();
        if (shows == null || now - sweptAt < SWEPT_NANOS) {
            return;
        }
        sweptAt = now;
        Sources known = sources;
        for (int show = 0; show < asked.length(); show++) {
            if (asked.get(show) == 0) {
                continue;
            }
            boolean kept = false;
            for (int number = 0; number < known.show.length() && !kept; number++) {
                kept = known.show.get(number) == show && known.drawn.get(number) != 0 && now - known.drawn.get(number) < KEPT_NANOS;
            }
            if (!kept) {
                asked.set(show, 0);
                for (int picture : shots[show]) {
                    Decor.forget(picture);
                }
            }
        }
    }

    /** Render thread, when Decor gives every picture back: no show's shots are read any more. */
    static void letGo() {
        if (shows != null) {
            for (int show = 0; show < asked.length(); show++) {
                asked.set(show, 0);
            }
        }
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
            glass = glasses.keep(model, glass);
        }
        return glass;
    }
}
