package com.cameraunlock.zomboid;

/**
 * The transition between the hip and the sights: a scale that is 1 at the hip and 0 with the
 * sights up, 150 ms on the way down and 250 ms back, leaving and arriving at rest. A reversal
 * part way carries on from where the transition is, at the same rate, so a tap of the aim
 * button steps nothing.
 *
 * The same model as cameraunlock-core's AdsFade, which exists in C++ and C# only. It has no
 * clock of its own: the caller says what time it is.
 */
final class AdsFade {
    static final long LOWER_MILLIS = 150;
    static final long RAISE_MILLIS = 250;

    private enum State { HIP, LOWERING, AIMING, RAISING }

    /** Nearer than this the two ends of a leg are the same place. */
    private static final float SETTLED = 1e-6f;

    private State state = State.HIP;
    private long startMillis;
    private long durationMillis = LOWER_MILLIS;
    private float from = 1f, target = 1f;

    /**
     * Once a frame.
     *
     * @param aiming the state this frame, polled and not latched
     * @return the scale: 1 at the hip, 0 with the sights up
     */
    float update(boolean aiming, long nowMillis) {
        boolean turnDown = aiming && (state == State.HIP || state == State.RAISING);
        boolean turnUp = !aiming && (state == State.LOWERING || state == State.AIMING);
        if (turnDown || turnUp) {
            float at = current(nowMillis);
            float to = turnDown ? 0f : 1f;
            float distance = Math.abs(to - at);
            if (distance < SETTLED) {
                state = turnDown ? State.AIMING : State.HIP;
                return to;
            }
            state = turnDown ? State.LOWERING : State.RAISING;
            from = at;
            target = to;
            // By the distance left to travel, so a leg cut short moves at the rate of a whole one.
            durationMillis = Math.max((long) ((turnDown ? LOWER_MILLIS : RAISE_MILLIS) * distance), 1);
            startMillis = nowMillis;
        }
        float scale = current(nowMillis);
        if ((state == State.LOWERING || state == State.RAISING) && elapsed(nowMillis) >= durationMillis) {
            state = target == 0f ? State.AIMING : State.HIP;
        }
        return scale;
    }

    /** Back to the hip, for wherever tracking stops, so the next aim starts clean. */
    void reset() {
        state = State.HIP;
        from = 1f;
        target = 1f;
    }

    /** A clock that stepped backwards has no time passed, and not an age of it. */
    private long elapsed(long nowMillis) {
        return Math.max(nowMillis - startMillis, 0);
    }

    private float current(long nowMillis) {
        if (state == State.HIP) {
            return 1f;
        }
        if (state == State.AIMING) {
            return 0f;
        }
        long elapsed = elapsed(nowMillis);
        if (elapsed >= durationMillis) {
            return target;
        }
        float t = (float) elapsed / durationMillis;
        return from + (target - from) * (t * t * (3f - 2f * t));
    }
}
