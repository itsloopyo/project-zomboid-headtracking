package com.cameraunlock.zomboid;

/**
 * Takes the tracker's samples to the frame rate: interpolates between samples,
 * smooths by the value the connection selects, and keeps the position inside
 * its limits. The pose stays in the tracker's own axes and units throughout
 * (metres and degrees); the camera converts it.
 *
 * The same model as cameraunlock-core's PoseInterpolator, PositionInterpolator,
 * TrackingProcessor and PositionProcessor, which exist in C++ and C# only.
 */
final class PoseFilter {
    static final float LOCAL_SMOOTHING = 0f;
    static final float REMOTE_SMOOTHING = 0.15f;

    static final float LIMIT_X = 0.30f;
    static final float LIMIT_Y_UP = 0.20f;
    static final float LIMIT_Y_DOWN = 0.20f;
    /** Negative z is the forward lean in the tracker's axes, and it gets the larger allowance. */
    static final float LIMIT_Z_FORWARD = 0.40f;
    static final float LIMIT_Z_BACK = 0.10f;

    private static final int X = 0, Y = 1, Z = 2, YAW = 3, PITCH = 4, ROLL = 5;
    private static final int CHANNELS = 6;

    private static final float MAX_EXTRAPOLATION_FRACTION = 0.5f;
    private static final float INTERVAL_BLEND = 0.3f;
    private static final float SEED_SAMPLE_INTERVAL = 1f / 30f;
    private static final float MIN_SAMPLE_INTERVAL = 0.001f;
    private static final float MAX_SAMPLE_INTERVAL = 0.2f;
    /** A sample may be this late before the extrapolation past the last one starts to expire. */
    private static final float EXTRAPOLATION_HOLD_SECONDS = 0.25f;
    private static final float EXTRAPOLATION_DECAY_SECONDS = 0.35f;

    private static final float SPEED_AT_NO_SMOOTHING = 50f;
    private static final float SPEED_AT_FULL_SMOOTHING = 0.1f;

    private final float[] from = new float[CHANNELS];
    private final float[] to = new float[CHANNELS];
    private final float[] smoothed = new float[CHANNELS];
    private long lastSampleNanos;
    private float progress;
    private float sampleInterval = SEED_SAMPLE_INTERVAL;
    private float sinceNewSample;
    private boolean hasFirstSample, hasSecondSample, hasSmoothed;

    float x() { return smoothed[X]; }
    float y() { return smoothed[Y]; }
    float z() { return smoothed[Z]; }
    float yaw() { return smoothed[YAW]; }
    float pitch() { return smoothed[PITCH]; }
    float roll() { return smoothed[ROLL]; }

    void reset() {
        lastSampleNanos = 0L;
        progress = 0f;
        sampleInterval = SEED_SAMPLE_INTERVAL;
        sinceNewSample = 0f;
        hasFirstSample = false;
        hasSecondSample = false;
        hasSmoothed = false;
        java.util.Arrays.fill(smoothed, 0f);
    }

    static float effectiveSmoothing(float local, float remote, boolean remoteConnection) {
        return remoteConnection ? remote : local;
    }

    void update(OpenTrackReceiver.Sample sample, boolean remoteConnection, float deltaSeconds) {
        sinceNewSample += deltaSeconds;
        if (sample.nanos() != lastSampleNanos) {
            takeSample(sample);
        }
        progress += deltaSeconds / sampleInterval;
        float t = segmentPosition(progress, sinceNewSample);

        float factor = smoothingFactor(
                effectiveSmoothing(LOCAL_SMOOTHING, REMOTE_SMOOTHING, remoteConnection), deltaSeconds);
        for (int i = 0; i < CHANNELS; i++) {
            float target = clampToLimit(i, along(i, t));
            if (!hasSmoothed) {
                smoothed[i] = target;
            } else if (isAngleThatWraps(i)) {
                smoothed[i] = normaliseDegrees(smoothed[i] + shortestDelta(smoothed[i], target) * factor);
            } else {
                smoothed[i] += (target - smoothed[i]) * factor;
            }
        }
        hasSmoothed = true;
    }

    private void takeSample(OpenTrackReceiver.Sample sample) {
        lastSampleNanos = sample.nanos();
        if (!hasFirstSample) {
            setTarget(sample);
            System.arraycopy(to, 0, from, 0, CHANNELS);
            progress = 1f;
            sinceNewSample = 0f;
            hasFirstSample = true;
            return;
        }
        if (sinceNewSample > MIN_SAMPLE_INTERVAL) {
            if (!hasSecondSample) {
                sampleInterval = sinceNewSample;
                hasSecondSample = true;
            } else {
                sampleInterval += (sinceNewSample - sampleInterval) * INTERVAL_BLEND;
            }
            sampleInterval = Math.clamp(sampleInterval, MIN_SAMPLE_INTERVAL, MAX_SAMPLE_INTERVAL);
        }
        // The next segment starts from wherever this one reached, extrapolation included, so the
        // output never steps back.
        float t = segmentPosition(progress, sinceNewSample);
        for (int i = 0; i < CHANNELS; i++) {
            from[i] = along(i, t);
        }
        setTarget(sample);
        progress = 0f;
        sinceNewSample = 0f;
    }

    private void setTarget(OpenTrackReceiver.Sample sample) {
        to[X] = sample.x();
        to[Y] = sample.y();
        to[Z] = sample.z();
        to[YAW] = sample.yaw();
        to[PITCH] = sample.pitch();
        to[ROLL] = sample.roll();
    }

    private float along(int channel, float t) {
        if (isAngleThatWraps(channel)) {
            return normaliseDegrees(from[channel] + shortestDelta(from[channel], to[channel]) * t);
        }
        return from[channel] + (to[channel] - from[channel]) * t;
    }

    /** Yaw and roll arrive in (-180, 180] and can step across the seam. Pitch is bounded and cannot. */
    private static boolean isAngleThatWraps(int channel) {
        return channel == YAW || channel == ROLL;
    }

    private static float clampToLimit(int channel, float value) {
        return switch (channel) {
            case X -> Math.clamp(value, -LIMIT_X, LIMIT_X);
            case Y -> Math.clamp(value, -LIMIT_Y_DOWN, LIMIT_Y_UP);
            case Z -> Math.clamp(value, -LIMIT_Z_FORWARD, LIMIT_Z_BACK);
            default -> value;
        };
    }

    /**
     * Where along the current segment to sample. Past 1 is extrapolation, up
     * to half a sample period, which keeps the motion continuous between
     * samples. When samples stop arriving it eases back to the last pose the
     * tracker reported, on the wall clock, so a stalled feed does not park the
     * view past where the head is.
     */
    static float segmentPosition(float progress, float sinceNewSample) {
        if (progress < 0f) {
            return 0f;
        }
        float t = Math.min(progress, 1f + MAX_EXTRAPOLATION_FRACTION);
        if (sinceNewSample <= EXTRAPOLATION_HOLD_SECONDS) {
            return t;
        }
        float u = Math.min((sinceNewSample - EXTRAPOLATION_HOLD_SECONDS) / EXTRAPOLATION_DECAY_SECONDS, 1f);
        return t + (1f - t) * (u * u * (3f - 2f * u));
    }

    /** Frame-rate independent: a time constant of 20 ms at smoothing 0 and 10 s at smoothing 1. */
    static float smoothingFactor(float smoothing, float deltaSeconds) {
        float speed = SPEED_AT_NO_SMOOTHING + (SPEED_AT_FULL_SMOOTHING - SPEED_AT_NO_SMOOTHING) * smoothing;
        return 1f - (float) Math.exp(-speed * deltaSeconds);
    }

    static float normaliseDegrees(float degrees) {
        float wrapped = degrees % 360f;
        if (wrapped > 180f) {
            return wrapped - 360f;
        }
        return wrapped <= -180f ? wrapped + 360f : wrapped;
    }

    static float shortestDelta(float from, float to) {
        return normaliseDegrees(to - from);
    }
}
