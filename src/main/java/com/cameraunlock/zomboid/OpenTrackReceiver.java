package com.cameraunlock.zomboid;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * Listens for OpenTrack UDP datagrams on a thread of its own: six little-endian
 * doubles, x, y, z in centimetres then yaw, pitch, roll in degrees. Anything a
 * tracker appends past those 48 bytes is ignored.
 */
final class OpenTrackReceiver {
    static final String PORT_PROPERTY = "cameraunlock.zomboid.port";
    static final int DEFAULT_PORT = 4242;
    static final int PACKET_BYTES = 48;
    /**
     * The largest value a pose may carry, in the wire's degrees or centimetres. No tracker
     * measures one near it, and two poses of a float's full range apart overflow the
     * interpolation between them, which leaves the view on a NaN that no later pose clears.
     */
    static final float MAX_MAGNITUDE = 1e6f;
    private static final int MAX_PORT = 65535;

    /** A tracker is quiet once this long has passed without a packet. */
    private static final int FRESH_MILLIS = 500;
    private static final long FRESH_NANOS = FRESH_MILLIS * 1_000_000L;
    /** Room for the pose and for whatever a tracker appends to it. */
    private static final int RECEIVE_BUFFER_BYTES = 256;
    private static final int POSE_VALUES = PACKET_BYTES / Double.BYTES;
    private static final int BIND_RETRY_MILLIS = 500;
    private static final float METRES_PER_CENTIMETRE = 0.01f;

    /** One pose off the wire, position in metres, angles in degrees, in the tracker's own axes. */
    record Sample(float x, float y, float z, float yaw, float pitch, float roll, long nanos) {
        boolean samePose(Sample other) {
            return x == other.x && y == other.y && z == other.z
                    && yaw == other.yaw && pitch == other.pitch && roll == other.roll;
        }
    }

    private final int port;
    private volatile Sample latest;
    private volatile boolean remote;
    private volatile long lastPacketNanos;
    private boolean receiving;
    private boolean rejectionLogged;

    OpenTrackReceiver(int port) {
        this.port = port;
    }

    /** @param text the port to listen on, or null for the default */
    static int parsePort(String text) {
        if (text == null) {
            return DEFAULT_PORT;
        }
        int value;
        try {
            value = Integer.parseInt(text.strip());
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException(PORT_PROPERTY + " is \"" + text + "\", which is not a port number");
        }
        if (value < 1 || value > MAX_PORT) {
            throw new IllegalArgumentException(PORT_PROPERTY + " is " + value + ", outside 1 to " + MAX_PORT);
        }
        return value;
    }

    void start() {
        Thread thread = new Thread(this::run, "CameraUnlock-OpenTrack");
        thread.setDaemon(true);
        thread.start();
    }

    /** The newest pose that differs from the one before it, or null before the first packet. */
    Sample latest() {
        return latest;
    }

    /** True when the last accepted packet came from anywhere but loopback. */
    boolean isRemote() {
        return remote;
    }

    boolean isFresh() {
        long last = lastPacketNanos;
        return last != 0L && System.nanoTime() - last < FRESH_NANOS;
    }

    private void run() {
        try (DatagramSocket socket = bind()) {
            socket.setSoTimeout(FRESH_MILLIS);
            byte[] buffer = new byte[RECEIVE_BUFFER_BYTES];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (true) {
                packet.setLength(buffer.length);
                try {
                    socket.receive(packet);
                } catch (SocketTimeoutException quiet) {
                    noteSilence(System.nanoTime());
                    continue;
                }
                accept(buffer, packet.getLength(), packet.getAddress(), System.nanoTime());
            }
        } catch (IOException | InterruptedException | RuntimeException failure) {
            // Nothing is above this on the thread: left uncaught, the reason goes to the
            // game's console and this log never says why tracking stopped.
            Log.error("head tracking stopped listening on UDP port " + port, failure);
        }
    }

    /** Another program holding the port is the usual reason for a failure, and it can let go later. */
    private DatagramSocket bind() throws SocketException, InterruptedException {
        String lastMessage = null;
        while (true) {
            DatagramSocket socket = new DatagramSocket(null);
            try {
                socket.bind(new InetSocketAddress(port));
                Log.info("head tracking listening on UDP port " + port);
                return socket;
            } catch (SocketException failure) {
                socket.close();
                String message = String.valueOf(failure.getMessage());
                if (!message.equals(lastMessage)) {
                    lastMessage = message;
                    Log.info("head tracking cannot open UDP port " + port + " (" + lastMessage
                            + "), trying again every " + BIND_RETRY_MILLIS + " ms");
                }
                Thread.sleep(BIND_RETRY_MILLIS);
            }
        }
    }

    /** Takes one datagram. Package-private so a test can feed it without a socket. */
    void accept(byte[] data, int length, InetAddress sender, long nanos) {
        Sample sample = parse(data, length, nanos);
        if (sample == null) {
            if (!rejectionLogged) {
                rejectionLogged = true;
                Log.info("ignored a " + length + " byte datagram from " + sender.getHostAddress()
                        + ": an OpenTrack pose is " + PACKET_BYTES + " bytes of six doubles, none past "
                        + MAX_MAGNITUDE + " either way."
                        + " Later ones are ignored without a line here");
            }
            noteSilence(nanos);
            return;
        }
        remote = !sender.isLoopbackAddress();
        Sample previous = latest;
        noteReceiving(sample, previous == null, sender);
        // A tracker's relay repeats the last pose faster than the tracker measures one. Only a
        // pose that differs is a new sample, or the interpolator would take the relay's rate for
        // the tracker's and have nothing to interpolate between.
        if (previous == null || !previous.samePose(sample)) {
            latest = sample;
        }
        lastPacketNanos = nanos;
    }

    /** Logs the tracker going quiet, once each time it does. Package-private for the same reason. */
    void noteSilence(long nanos) {
        if (receiving && nanos - lastPacketNanos >= FRESH_NANOS) {
            receiving = false;
            Log.info("no tracking packet for " + FRESH_MILLIS + " ms, the view keeps the last pose");
        }
    }

    /** Logs the tracker's first packet, and its first after going quiet. */
    private void noteReceiving(Sample sample, boolean first, InetAddress sender) {
        if (receiving) {
            return;
        }
        receiving = true;
        Log.info(first
                ? String.format(Locale.ROOT,
                        "first tracking packet from %s: x %.3f y %.3f z %.3f m, yaw %.2f pitch %.2f roll %.2f",
                        sender.getHostAddress(), sample.x, sample.y, sample.z, sample.yaw, sample.pitch, sample.roll)
                : String.format(Locale.ROOT, "tracking packets again from %s after %.1f s without one",
                        sender.getHostAddress(), (sample.nanos - lastPacketNanos) / 1e9));
    }

    /** Null for a datagram that is too short or carries a value that is not a number within MAX_MAGNITUDE. */
    static Sample parse(byte[] data, int length, long nanos) {
        if (length < PACKET_BYTES) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN);
        float[] values = new float[POSE_VALUES];
        for (int i = 0; i < values.length; i++) {
            values[i] = (float) buffer.getDouble(i * Double.BYTES);
            if (!(Math.abs(values[i]) <= MAX_MAGNITUDE)) {
                return null;
            }
        }
        return new Sample(values[0] * METRES_PER_CENTIMETRE, values[1] * METRES_PER_CENTIMETRE,
                values[2] * METRES_PER_CENTIMETRE, values[3], values[4], values[5], nanos);
    }
}
