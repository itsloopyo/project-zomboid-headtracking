package com.cameraunlock.zomboid;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Listens for OpenTrack UDP datagrams on a thread of its own: six little-endian
 * doubles, x, y, z in centimetres then yaw, pitch, roll in degrees. Anything a
 * tracker appends past those 48 bytes is ignored.
 */
final class OpenTrackReceiver {
    static final int DEFAULT_PORT = 4242;
    static final int PACKET_BYTES = 48;

    private static final long FRESH_NANOS = 500_000_000L;
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

    OpenTrackReceiver(int port) {
        this.port = port;
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
            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            while (true) {
                packet.setLength(buffer.length);
                socket.receive(packet);
                accept(buffer, packet.getLength(), packet.getAddress(), System.nanoTime());
            }
        } catch (IOException | InterruptedException failure) {
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
                if (!String.valueOf(failure.getMessage()).equals(lastMessage)) {
                    lastMessage = String.valueOf(failure.getMessage());
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
            return;
        }
        remote = !sender.isLoopbackAddress();
        Sample previous = latest;
        if (previous == null) {
            Log.info(String.format(java.util.Locale.ROOT,
                    "first tracking packet from %s: x %.3f y %.3f z %.3f m, yaw %.2f pitch %.2f roll %.2f",
                    sender.getHostAddress(), sample.x, sample.y, sample.z, sample.yaw, sample.pitch, sample.roll));
        }
        // A tracker's relay repeats the last pose faster than the tracker measures one. Only a
        // pose that differs is a new sample, or the interpolator would take the relay's rate for
        // the tracker's and have nothing to interpolate between.
        if (previous == null || !previous.samePose(sample)) {
            latest = sample;
        }
        lastPacketNanos = nanos;
    }

    /** Null for a datagram that is too short or carries a value that is not a finite float. */
    static Sample parse(byte[] data, int length, long nanos) {
        if (length < PACKET_BYTES) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN);
        float[] values = new float[6];
        for (int i = 0; i < values.length; i++) {
            values[i] = (float) buffer.getDouble(i * Double.BYTES);
            if (!Float.isFinite(values[i])) {
                return null;
            }
        }
        return new Sample(values[0] * METRES_PER_CENTIMETRE, values[1] * METRES_PER_CENTIMETRE,
                values[2] * METRES_PER_CENTIMETRE, values[3], values[4], values[5], nanos);
    }
}
