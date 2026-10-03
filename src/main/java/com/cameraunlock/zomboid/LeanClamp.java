package com.cameraunlock.zomboid;

/**
 * Keeps a lean from putting the eye inside a wall: scales the offset down to
 * what the world leaves room for. Tightening is instant, because easing into a
 * smaller allowance would leave the eye in the wall for the length of the
 * ease. Releasing is eased, so the view does not pop when the wall clears.
 *
 * The policy of cameraunlock-core's LeanClamp, which exists in C++ and C# only.
 */
final class LeanClamp {
    /** How far off a wall the eye is held, in tiles. It has to exceed the distance to the near plane's corners. */
    static final float SKIN = 0.12f;
    /** The release's smoothing: a 200 ms time constant. */
    private static final float RELEASE_SMOOTHING = 0.9f;
    private static final float MINIMUM_LEAN = 1e-4f;
    private static final float SETTLE_FRACTION = 1e-3f;

    /** How far along a direction the eye can travel before it is within SKIN of something. */
    interface Room {
        /** The clear travel along the unit vector, capped at reach, measured to the eye and not to the wall. */
        float along(float directionX, float directionY, float directionUp, float reach);
    }

    private float allowed;
    private boolean hasAllowance;
    private boolean contact;

    boolean inContact() {
        return contact;
    }

    void reset() {
        hasAllowance = false;
        contact = false;
    }

    /** Scales the offset in place and returns the length it was left with. */
    float apply(float[] offset, float deltaSeconds, Room room) {
        contact = false;
        float desired = (float) Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1] + offset[2] * offset[2]);
        if (desired <= MINIMUM_LEAN) {
            // No lean, so nothing was blocking it. A stale allowance would ration the next lean
            // off a wall the player has already left.
            hasAllowance = false;
            return desired;
        }
        float target = Math.clamp(room.along(offset[0] / desired, offset[1] / desired, offset[2] / desired, desired),
                0f, desired);

        float now = hasAllowance ? Math.min(allowed, desired) : desired;
        if (target < now) {
            now = target;
            hasAllowance = true;
        } else if (hasAllowance) {
            now += (target - now) * PoseFilter.smoothingFactor(RELEASE_SMOOTHING, deltaSeconds);
            if (desired - now <= desired * SETTLE_FRACTION) {
                now = desired;
                hasAllowance = false;
            }
        }
        allowed = now;
        contact = now < desired;
        float scale = now / desired;
        offset[0] *= scale;
        offset[1] *= scale;
        offset[2] *= scale;
        return now;
    }
}
