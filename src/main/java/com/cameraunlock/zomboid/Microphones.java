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
 * What each player at the screen is saying into their own microphone, as the shape of their
 * mouth (Mouths, Voice). A microphone is opened only while TalkingMouths is on, one for each
 * player whose row of [Voice] names one, and closed as soon as either changes. What comes in is
 * measured a block at a time, and the last 32 ms of it are kept to be measured again with the
 * next block: nothing of the sound is written or sent, only the mouth's shape.
 *
 * Each open microphone is read on a thread of its own. The render thread reads each mouth's shape.
 */
final class Microphones {
    /** The words a row of [Voice] holds for the microphone Windows uses by default, and for none. */
    static final String DEFAULT = "Default", NONE = "None";
    private static final AudioFormat FORMAT = new AudioFormat(Voice.RATE, 16, 1, true, false);
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

    /** The shape of a player's mouth. Any thread. */
    static Mouths.Shape mouth(int player) {
        Capture capture = running[player];
        return capture == null ? Mouths.Shape.SHUT : capture.mouth;
    }

    /**
     * The names of the microphones connected now, as a row of [Voice] names them, the default one
     * aside. Java Sound lists DirectSound's capture devices in the order DirectSound gives them,
     * and DirectSound gives the default first, under a name Windows words in its own language.
     */
    static List<String> present() {
        List<String> names = new ArrayList<>();
        boolean first = true;
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (AudioSystem.getMixer(info).isLineSupported(CAPTURE)) {
                if (!first) {
                    names.add(info.getName());
                }
                first = false;
            }
        }
        return names;
    }

    /** The line of a microphone by the name a row gives it, or null when no microphone of that name is connected. */
    private static TargetDataLine line(String asked) throws LineUnavailableException {
        if (DEFAULT.equals(asked)) {
            return AudioSystem.isLineSupported(CAPTURE) ? (TargetDataLine) AudioSystem.getLine(CAPTURE) : null;
        }
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (info.getName().equals(asked)) {
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
        volatile Mouths.Shape mouth = Mouths.Shape.SHUT;

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
                    Log.info("player " + (player + 1) + "'s microphone \"" + asked + "\" opened for their mouth: only the shape it gives their mouth is kept");
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
                        voice.step(block, read, (now - last) / 1e9f);
                        mouth = new Mouths.Shape(voice.open(), voice.lips());
                        last = now;
                    }
                } finally {
                    mouth = Mouths.Shape.SHUT;
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
}
