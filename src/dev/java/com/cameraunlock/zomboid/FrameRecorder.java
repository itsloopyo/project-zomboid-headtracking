package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import zombie.GameTime;
import zombie.core.Core;
import zombie.core.PerformanceSettings;
import zombie.core.SpriteRenderer;
import zombie.core.textures.TextureDraw;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

/**
 * Records the game's frames to video files, there only in a development build: for filming
 * the mod. Each frame the game draws is read back from the window and piped to ffmpeg, once
 * as the player sees it ("hud": the game's interface on top) and once before the interface is
 * drawn ("clean"), so one take gives both.
 *
 * While it records, the game's clock goes on by exactly one frame's share of a second each
 * frame (FPSTracking sets the clock's step from the time each frame really took), and the
 * game's frame limit is set to the recording's rate. The world then moves the same distance
 * every frame of the film whatever a slow frame cost; what moves by the wall clock (the
 * tracker's pose, the mod's own springs) still runs in real time, so the game has to keep up
 * with the rate for those to match, and a frame that took over one and a half frames' time is
 * counted as late.
 *
 * Frames are read into a ring of pixel buffers and taken out two frames later, so the read
 * back does not wait for the frame to finish drawing. A frame is never dropped: when ffmpeg
 * falls behind, the drawing waits for it, and the wait is counted.
 *
 * The game's own sound is recorded beside the frames by an external tool given the game's
 * process id (cameraunlock.zomboid.recorder.audiocap), into a WAV with the clock time of its
 * first sample; the frame list holds the clock time each frame was drawn, both as
 * System.nanoTime, so the two can be lined up.
 */
public final class FrameRecorder {
    /** GL_PIXEL_PACK_BUFFER, GL_STREAM_READ, GL_READ_ONLY, GL_BGRA, GL_UNSIGNED_BYTE, GL_READ_FRAMEBUFFER and its binding. */
    private static final int PACK = 0x88EB, STREAM_READ = 0x88E1, READ_ONLY = 0x88B8, BGRA = 0x80E1, UNSIGNED_BYTE = 0x1401;
    private static final int READ_FRAMEBUFFER = 0x8CA8, READ_FRAMEBUFFER_BINDING = 0x8CAA;
    private static final int RING = 3;
    private static final int QUEUED_FRAMES = 6;

    private static final String FFMPEG = System.getProperty("cameraunlock.zomboid.recorder.ffmpeg", "C:/ProgramData/chocolatey/bin/ffmpeg.exe");
    private static final String AUDIOCAP = System.getProperty("cameraunlock.zomboid.recorder.audiocap", "");
    /** The video encoder's arguments, after the input. Near lossless, on the graphics card's encoder. */
    private static final String ENCODE = System.getProperty("cameraunlock.zomboid.recorder.encode",
            "-c:v hevc_nvenc -preset p5 -tune hq -rc constqp -qp 12 -pix_fmt yuv444p");

    private static volatile Take take;
    private static int savedLockFps;
    private static boolean savedUncapped;

    private FrameRecorder() {}

    /** One recording: its streams, its sound and its frame list. */
    private static final class Take {
        final String name;
        final Path folder;
        final int fps;
        final boolean fixedStep;
        final Stream hud, clean;
        final Process audio;
        final Path audioStop;
        final StringBuilder frames = new StringBuilder("frame logicNanos\n");
        int frameCount;
        long lastLogicNanos;
        int late;
        volatile boolean stopAsked;
        final java.util.concurrent.CountDownLatch flushed = new java.util.concurrent.CountDownLatch(1);

        Take(String name, Path folder, int fps, boolean fixedStep, boolean hud, boolean clean, int width, int height) throws IOException {
            this.name = name;
            this.folder = folder;
            this.fps = fps;
            this.fixedStep = fixedStep;
            this.hud = hud ? new Stream(folder.resolve(name + "-hud.mkv"), fps, width, height) : null;
            this.clean = clean ? new Stream(folder.resolve(name + "-clean.mkv"), fps, width, height) : null;
            if (AUDIOCAP.isEmpty()) {
                audio = null;
                audioStop = null;
            } else {
                audioStop = folder.resolve(name + ".audio-stop");
                Files.deleteIfExists(audioStop);
                audio = new ProcessBuilder(AUDIOCAP, "--pid", Long.toString(ProcessHandle.current().pid()), "--out", folder.resolve(name + ".wav").toString(),
                        "--stop-file", audioStop.toString()).redirectErrorStream(true).redirectOutput(folder.resolve(name + ".audio.log").toFile()).start();
            }
        }
    }

    /** One video file: its ffmpeg, its pixel buffers on the render thread and the frames waiting to be written. */
    private static final class Stream {
        final Path file;
        final int width, height, bytes;
        final Process ffmpeg;
        final BlockingQueue<byte[]> full = new ArrayBlockingQueue<>(QUEUED_FRAMES);
        final BlockingQueue<byte[]> empty = new ArrayBlockingQueue<>(QUEUED_FRAMES + 2);
        final Thread writer;
        final int[] buffers = new int[RING];
        int read;
        long waitedNanos;
        volatile Throwable failed;
        static final byte[] END = new byte[0];

        Stream(Path file, int fps, int width, int height) throws IOException {
            this.file = file;
            this.width = width;
            this.height = height;
            this.bytes = width * height * 4;
            for (int i = 0; i < QUEUED_FRAMES + 2; i++) {
                empty.add(new byte[bytes]);
            }
            List<String> command = new ArrayList<>(List.of(FFMPEG, "-hide_banner", "-loglevel", "warning", "-y", "-f", "rawvideo", "-pix_fmt", "bgra",
                    "-s", width + "x" + height, "-framerate", Integer.toString(fps), "-i", "-", "-vf", "vflip"));
            command.addAll(List.of(ENCODE.split(" ")));
            command.add(file.toString());
            ffmpeg = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(file.resolveSibling(file.getFileName() + ".ffmpeg.log").toFile()).start();
            OutputStream into = ffmpeg.getOutputStream();
            writer = new Thread(() -> {
                try (into) {
                    while (true) {
                        byte[] frame = full.take();
                        if (frame == END) {
                            return;
                        }
                        into.write(frame);
                        empty.add(frame);
                    }
                } catch (Throwable failure) {
                    failed = failure;
                    Log.error("recorder: writing " + file.getFileName() + " to ffmpeg failed", failure);
                }
            }, "recorder " + file.getFileName());
            writer.setDaemon(true);
            writer.start();
        }

        /** Render thread: reads this frame into the next pixel buffer and hands on the one read two frames ago. */
        void readFrame() throws InterruptedException {
            if (read == 0) {
                for (int i = 0; i < RING; i++) {
                    buffers[i] = GL15.glGenBuffers();
                    GL15.glBindBuffer(PACK, buffers[i]);
                    GL15.glBufferData(PACK, (long) bytes, STREAM_READ);
                }
            }
            int previousRead = GL11.glGetInteger(READ_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(READ_FRAMEBUFFER, 0);
            GL11.glPixelStorei(0x0D05, 1);
            GL15.glBindBuffer(PACK, buffers[read % RING]);
            GL11.glReadPixels(0, 0, width, height, BGRA, UNSIGNED_BYTE, 0L);
            GL30.glBindFramebuffer(READ_FRAMEBUFFER, previousRead);
            read++;
            if (read >= RING) {
                handOn(buffers[read % RING]);
            }
            GL15.glBindBuffer(PACK, 0);
        }

        private void handOn(int buffer) throws InterruptedException {
            if (failed != null) {
                throw new IllegalStateException("recorder: " + file.getFileName() + " stopped taking frames", failed);
            }
            GL15.glBindBuffer(PACK, buffer);
            ByteBuffer pixels = GL15.glMapBuffer(PACK, READ_ONLY, bytes, null);
            if (pixels == null) {
                throw new IllegalStateException("recorder: glMapBuffer gave no pixels for " + file.getFileName() + ", GL error " + GL11.glGetError());
            }
            long before = System.nanoTime();
            byte[] frame = empty.take();
            pixels.get(frame);
            GL15.glUnmapBuffer(PACK);
            full.put(frame);
            waitedNanos += System.nanoTime() - before;
        }

        /** Render thread: hands on the frames still in the ring and frees it. */
        void flush() throws InterruptedException {
            for (int k = Math.max(read - RING + 1, 0); k < read; k++) {
                handOn(buffers[k % RING]);
            }
            GL15.glBindBuffer(PACK, 0);
            for (int buffer : buffers) {
                if (buffer != 0) {
                    GL15.glDeleteBuffers(buffer);
                }
            }
            full.put(END);
        }

        String finish() throws InterruptedException {
            writer.join();
            if (!ffmpeg.waitFor(120, TimeUnit.SECONDS)) {
                ffmpeg.destroyForcibly();
                throw new IllegalStateException("recorder: ffmpeg did not finish " + file.getFileName() + " within two minutes");
            }
            if (ffmpeg.exitValue() != 0 || failed != null) {
                throw new IllegalStateException("recorder: ffmpeg exited " + ffmpeg.exitValue() + " on " + file.getFileName() + ", see its .ffmpeg.log");
            }
            return String.format(Locale.ROOT, "%s %d frames, waited %.0f ms on the writer", file.getFileName(), read, waitedNanos / 1e6);
        }
    }

    /**
     * Starts a take: files {@code name}-hud.mkv and {@code name}-clean.mkv (as asked) in
     * {@code folder}, at {@code fps} frames a second, the window's size.
     */
    public static synchronized void start(Path folder, String name, int fps, boolean fixedStep, boolean hud, boolean clean) {
        if (take != null) {
            throw new IllegalStateException("recorder: " + take.name + " is still recording");
        }
        if (!hud && !clean) {
            throw new IllegalArgumentException("recorder: a take records the hud stream, the clean stream or both");
        }
        try {
            Files.createDirectories(folder);
            int width = Core.getInstance().getScreenWidth(), height = Core.getInstance().getScreenHeight();
            Take started = new Take(name, folder, fps, fixedStep, hud, clean, width, height);
            savedLockFps = PerformanceSettings.getLockFPS();
            savedUncapped = PerformanceSettings.instance.isFramerateUncapped();
            PerformanceSettings.setLockFPS(fps);
            PerformanceSettings.instance.setFramerateUncapped(false);
            take = started;
            Log.info(String.format(Locale.ROOT, "recorder: %s started, %dx%d at %d fps, fixed step %b, hud %b, clean %b, sound %s", name, width, height, fps,
                    fixedStep, hud, clean, started.audio == null ? "not recorded" : "recorded"));
        } catch (IOException failure) {
            throw new UncheckedIOException("recorder: " + name + " could not start", failure);
        }
    }

    /**
     * Stops the take after the frame being drawn: its last frames are written, ffmpeg and the
     * sound are let finish, and the frame list is written. Logic thread.
     */
    public static synchronized void stop() {
        if (take == null) {
            throw new IllegalStateException("recorder: nothing is recording");
        }
        take.stopAsked = true;
    }

    /** Logic thread, at the end of the frame stop was asked in: the flush goes into the frame's drawing after its last read. */
    private static synchronized void end(Take ending) {
        take = null;
        // Counted here, not in the finishing thread, so busy() never reads false between the two.
        finishing++;
        PerformanceSettings.setLockFPS(savedLockFps);
        PerformanceSettings.instance.setFramerateUncapped(savedUncapped);
        SpriteRenderer.instance.drawGeneric(new Flush(ending));
        Thread finisher = new Thread(() -> finish(ending), "recorder finish " + ending.name);
        finisher.setDaemon(true);
        finisher.start();
    }

    /**
     * The mod's own words on the screen (the graphics mode said at a switch, "getting ready"): drawn as ever, except
     * while a take records or is being written, or a frame dump is asked and not yet read, so neither has any of them. Logic thread.
     */
    public static void said(TextManager text, UIFont font, double x, double y, String words, double r, double g, double b, double a) {
        if (!busy() && !FrameDump.pending()) {
            text.DrawStringCentre(font, x, y, words, r, g, b, a);
        }
    }

    /** How far into the take the frame being made is, in milliseconds of the film, or -1 with none recording. */
    public static long filmMillis() {
        Take recording = take;
        return recording == null ? -1 : recording.frameCount * 1000L / recording.fps;
    }

    /** Whether a take is recording or still being written. */
    public static boolean busy() {
        return take != null || finishing > 0;
    }

    private static volatile int finishing;

    private static void finish(Take ending) {
        try {
            if (!ending.flushed.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("recorder: the render thread did not run the flush within 30 s: was the draw queued outside a frame?");
            }
            StringBuilder said = new StringBuilder();
            for (Stream each : new Stream[] {ending.hud, ending.clean}) {
                if (each != null) {
                    said.append(", ").append(each.finish());
                }
            }
            if (ending.audio != null) {
                Files.writeString(ending.audioStop, "stop");
                if (!ending.audio.waitFor(30, TimeUnit.SECONDS)) {
                    ending.audio.destroyForcibly();
                    throw new IllegalStateException("recorder: the sound recorder did not stop within 30 s");
                }
                said.append(", sound recorder exited ").append(ending.audio.exitValue());
            }
            Files.writeString(ending.folder.resolve(ending.name + ".frames.txt"), ending.frames, StandardCharsets.US_ASCII);
            Log.info(String.format(Locale.ROOT, "recorder: %s written, %d frames, %d late%s", ending.name, ending.frameCount, ending.late, said));
        } catch (Throwable failure) {
            Log.error("recorder: " + ending.name + " was not written whole", failure);
        } finally {
            synchronized (FrameRecorder.class) {
                finishing--;
            }
        }
    }

    /**
     * Called from FPSTracking.frameStep, after it has set the clock's step from the time the
     * frame took. While a fixed step take records, the step is one frame of the recording.
     */
    public static void fixStep() {
        Take recording = take;
        if (recording != null && recording.fixedStep) {
            GameTime.getInstance().fpsMultiplier = 60f / recording.fps;
        }
    }

    /**
     * Logic thread, from IngameState.renderFrameUI once the world and its text are drawn and before the interface. Both
     * reads go inside the frame's interface pass: a draw queued outside StartFrameUI and EndFrameUI is never run.
     */
    public static void beforeInterface() {
        FrameDump.beforeInterface();
        Take recording = take;
        if (recording == null) {
            return;
        }
        long now = System.nanoTime();
        recording.frames.append(recording.frameCount).append(' ').append(now).append('\n');
        if (recording.frameCount > 0 && now - recording.lastLogicNanos > 1_500_000_000L / recording.fps) {
            recording.late++;
        }
        recording.lastLogicNanos = now;
        recording.frameCount++;
        if (recording.clean != null) {
            SpriteRenderer.instance.drawGeneric(new Read(recording.clean));
        }
    }

    /** Logic thread, from IngameState.renderFrameUI once the interface is drawn, still inside its pass. */
    public static void afterInterface() {
        FrameDump.afterInterface();
        Take recording = take;
        if (recording == null) {
            return;
        }
        if (recording.hud != null) {
            SpriteRenderer.instance.drawGeneric(new Read(recording.hud));
        }
        if (recording.stopAsked) {
            end(recording);
        }
    }

    private static final class Read extends TextureDraw.GenericDrawer {
        private final Stream stream;

        Read(Stream stream) {
            this.stream = stream;
        }

        @Override
        public void render() {
            try {
                stream.readFrame();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("recorder: interrupted reading a frame", interrupted);
            }
        }
    }

    private static final class Flush extends TextureDraw.GenericDrawer {
        private final Take ending;

        Flush(Take ending) {
            this.ending = ending;
        }

        @Override
        public void render() {
            try {
                for (Stream each : new Stream[] {ending.hud, ending.clean}) {
                    if (each != null) {
                        each.flush();
                    }
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("recorder: interrupted flushing " + ending.name, interrupted);
            } finally {
                ending.flushed.countDown();
            }
        }
    }
}
