package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * How loudly each player at the screen is speaking into their own microphone, as how far their
 * mouth is open (Mouths). A microphone is opened only while TalkingMouths is on, one for each
 * player whose row of [Voice] names one, and closed as soon as either changes. What comes in is
 * measured a block at a time into one buffer that the next block overwrites: nothing of the sound
 * is kept, written or sent, only how loud it was.
 *
 * Each open microphone is read on a thread of its own. The render thread reads how far each mouth
 * is open.
 */
final class Microphones {
    /** The words a row of [Voice] holds for the microphone Windows uses by default, and for none. */
    static final String DEFAULT = "Default", NONE = "None";
    /** Java Sound's name for the capture device Windows uses by default. */
    private static final String DEFAULT_DEVICE = "Primary Sound Capture Driver";
    private static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    private static final DataLine.Info CAPTURE = new DataLine.Info(TargetDataLine.class, FORMAT);
    /** Samples measured at once: 20 ms, as quick as a mouth opens on a syllable. */
    private static final int BLOCK = 320;
    /** How long a microphone that is not connected, or that failed, is left before it is looked for again. */
    private static final long RETRY_MILLISECONDS = 5000;

    /** The microphone each player at the screen is read from, or null: replaced whole, so the render thread reads one that is whole. */
    private static volatile Capture[] running = new Capture[View.COUNT];

    private Microphones() {}

    /**
     * Opens the microphone each player's row names and closes every other, or closes them all
     * when the mouths are off. A player whose microphone is the one already open keeps it.
     *
     * @param names each player's row of [Voice], in order: DEFAULT, NONE or a device's name
     */
    static synchronized void set(boolean on, String[] names) {
        Capture[] now = running.clone();
        for (int player = 0; player < now.length; player++) {
            String wanted = on && !NONE.equals(names[player]) ? names[player] : null;
            Capture had = now[player];
            if (had != null && had.asked.equals(wanted)) {
                continue;
            }
            if (had != null) {
                had.stop();
                now[player] = null;
            }
            if (wanted != null) {
                now[player] = new Capture(player, wanted);
            }
        }
        running = now;
    }

    /** How far a player's mouth is open, 0 to 1. Any thread. */
    static float open(int player) {
        Capture capture = running[player];
        return capture == null ? 0f : capture.open;
    }

    /** The names of the microphones connected now, as a row of [Voice] names them, the default one aside. */
    static List<String> present() {
        List<String> names = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (!info.getName().equals(DEFAULT_DEVICE) && AudioSystem.getMixer(info).isLineSupported(CAPTURE)) {
                names.add(info.getName());
            }
        }
        return names;
    }

    /** The line of a microphone by the name a row gives it, or null when no microphone of that name is connected. */
    private static TargetDataLine line(String asked) throws LineUnavailableException {
        String device = DEFAULT.equals(asked) ? DEFAULT_DEVICE : asked;
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (info.getName().equals(device)) {
                Mixer mixer = AudioSystem.getMixer(info);
                if (mixer.isLineSupported(CAPTURE)) {
                    return (TargetDataLine) mixer.getLine(CAPTURE);
                }
            }
        }
        return null;
    }

    /** One player's microphone, read on a thread of its own until stopped. */
    private static final class Capture implements Runnable {
        final int player;
        final String asked;
        final Thread thread;
        volatile boolean stopped;
        volatile float open;

        Capture(int player, String asked) {
            this.player = player;
            this.asked = Objects.requireNonNull(asked);
            thread = new Thread(this, "CameraUnlock microphone " + (player + 1));
            thread.setDaemon(true);
            thread.start();
        }

        void stop() {
            stopped = true;
            thread.interrupt();
        }

        @Override
        public void run() {
            try {
                listen();
            } catch (RuntimeException failure) {
                // Nothing is above this on the thread: left uncaught, the reason never reaches the log.
                Log.error("player " + (player + 1) + "'s microphone is no longer read, and their mouth stays shut", failure);
                throw failure;
            }
        }

        private void listen() {
            Voice voice = new Voice();
            byte[] block = new byte[BLOCK * 2];
            boolean saidMissing = false;
            while (!stopped) {
                TargetDataLine line;
                try {
                    line = line(asked);
                    if (line == null) {
                        if (!saidMissing) {
                            Log.info("player " + (player + 1) + "'s microphone \"" + asked + "\" is not connected: their mouth stays shut until it is");
                            saidMissing = true;
                        }
                        rest();
                        continue;
                    }
                    line.open(FORMAT, block.length * 4);
                } catch (LineUnavailableException | IllegalArgumentException | SecurityException failure) {
                    Log.error("player " + (player + 1) + "'s microphone \"" + asked + "\" did not open, and is tried again in " + RETRY_MILLISECONDS / 1000
                            + " s", failure);
                    rest();
                    continue;
                }
                saidMissing = false;
                try {
                    line.start();
                    Log.info("player " + (player + 1) + "'s microphone \"" + asked + "\" opened for their mouth: only its loudness is read");
                    long last = System.nanoTime();
                    while (!stopped) {
                        int read = line.read(block, 0, block.length);
                        if (read < block.length) {
                            // A line closed under the thread, as Windows closes one whose device is unplugged.
                            if (!stopped) {
                                Log.info("player " + (player + 1) + "'s microphone \"" + asked + "\" stopped giving sound, and is looked for again");
                            }
                            break;
                        }
                        long now = System.nanoTime();
                        open = voice.step(Voice.loudness(block, read), (now - last) / 1e9f);
                        last = now;
                    }
                } finally {
                    open = 0f;
                    line.close();
                    Log.info("player " + (player + 1) + "'s microphone \"" + asked + "\" closed");
                }
                if (!stopped) {
                    rest();
                }
            }
        }

        private void rest() {
            try {
                Thread.sleep(RETRY_MILLISECONDS);
            } catch (InterruptedException stoppedWhileResting) {
                // stop() interrupts: the loop sees stopped and ends.
            }
        }
    }

    /**
     * How far a mouth is open from how loud the voice into the microphone is. The quietest the
     * microphone has been lately is its room's own noise, and the mouth opens only on sound well
     * above it, so a fan or a hum keeps it shut. It opens quickly on a syllable and closes a
     * little more slowly, as a jaw does.
     */
    static final class Voice {
        /** Below this, in decibels of full scale, nothing is a voice however quiet the room is. */
        static final float QUIETEST = -55f;
        /** How far over the room's noise a sound has to be to open the mouth at all, and how much louder again opens it wide, in decibels. */
        static final float ABOVE_ROOM = 10f, WIDE_OVER = 25f;
        /**
         * How fast the room's noise is taken to be rising while nothing quieter is heard, in
         * decibels a second. Speech falls quiet between words far more often than the time this
         * takes to reach it.
         */
        static final float ROOM_RISES = 6f;
        /** How long the mouth takes to open and to close, as time constants in seconds. */
        static final float OPENS = 0.02f, CLOSES = 0.07f;

        /** The room's noise in decibels, which the first block sets. */
        private float room = Float.NaN, open;

        /** The loudness of a block of 16 bit samples, little end first, as their root mean square against full scale. */
        static float loudness(byte[] block, int bytes) {
            double sum = 0;
            int samples = bytes / 2;
            for (int i = 0; i < samples; i++) {
                int sample = (short) ((block[2 * i] & 0xff) | (block[2 * i + 1] << 8));
                sum += (double) sample * sample;
            }
            return samples == 0 ? 0f : (float) (Math.sqrt(sum / samples) / 32768.0);
        }

        /** The mouth after a block of that loudness, which took that many seconds. */
        float step(float loudness, float seconds) {
            float decibels = 20f * (float) Math.log10(Math.max(loudness, 1e-6f));
            room = Float.isNaN(room) || decibels < room ? decibels : room + ROOM_RISES * seconds;
            float gate = Math.max(QUIETEST, room + ABOVE_ROOM);
            float wanted = Math.clamp((decibels - gate) / WIDE_OVER, 0f, 1f);
            float time = wanted > open ? OPENS : CLOSES;
            open += (wanted - open) * (1f - (float) Math.exp(-seconds / time));
            return open;
        }
    }
}
