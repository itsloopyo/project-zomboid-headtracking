package com.cameraunlock.zomboid;

/**
 * Whether the viewer is glancing at the watch on a wrist, and how far the arm is up for it: the
 * view pitched well down and turned towards that wrist's side of the body, by the head, the
 * mouse or both, and held there a moment. Looking at the floor ahead while walking is pitched
 * down and not turned, and a look round to the side is turned and not pitched down: neither
 * raises the arm. The way out is wider than the way in, so a look held at the edge does not
 * make the arm flicker.
 *
 * The turn is measured from where the body faced as the look went down. Standing still, the
 * body turns with the mouse, so a player without head tracking glances by looking down and then
 * turning the view to the wrist's side; with head tracking the head turns and the body stays.
 *
 * Logic thread.
 */
final class WatchGlance {
    /** The view's pitch below the level at which a glance begins and at which it ends, radians. */
    static final float DOWN_FROM = (float) Math.toRadians(-30), DOWN_TO = (float) Math.toRadians(-22);
    /** Further down than this the eye is on the floor at its feet, not on a wrist. */
    static final float DOWN_MOST = (float) Math.toRadians(-80);
    /** How far the view is turned towards the wrist's side of the body at which a glance begins and ends, radians. */
    static final float ASIDE_FROM = (float) Math.toRadians(12), ASIDE_TO = (float) Math.toRadians(5);
    /** Further round than this the eye is looking behind, over the shoulder. */
    static final float ASIDE_MOST = (float) Math.toRadians(75);
    /** How long a look is held before the arm comes up, seconds: a sweep of the view across the place does not raise it. */
    static final float DWELL = 0.2f;
    /** The arm comes up, to within a twentieth, in three of these, and goes down as fast. */
    static final float RAISE_SECONDS = 0.1f;
    /** Nearer than this to down, an arm going down is down. */
    static final float GONE = 0.02f;

    private boolean looking, wasDown;
    private float held;
    private float up;
    private float facing;

    /**
     * How far a look is turned towards a wrist's side from where the body faced as the look went
     * down, radians: from where it faces now while the look is not down.
     *
     * @param yaw     where the look is turned, radians, right positive
     * @param bodyYaw where the body faces now
     */
    float aside(float pitch, float yaw, float bodyYaw, boolean left) {
        boolean down = pitch < DOWN_TO;
        if (!down || !wasDown) {
            facing = bodyYaw;
        }
        wasDown = down;
        float turned = turned(yaw, facing);
        return left ? -turned : turned;
    }

    /**
     * Steps the glance over a frame.
     *
     * @param pitch   the view's pitch, radians, up positive: the mouse's and the head's together
     * @param aside   how far the view is turned from where the body faces towards the wrist's side, radians
     * @param allowed whether the arm may come up at all this frame: a watch on that wrist, that hand free, nothing else under way
     * @return how far the arm is up, 0 to 1
     */
    float step(float pitch, float aside, boolean allowed, float seconds) {
        boolean inside = looking ? pitch < DOWN_TO && aside > ASIDE_TO : pitch < DOWN_FROM && aside > ASIDE_FROM;
        looking = allowed && inside && pitch > DOWN_MOST && aside < ASIDE_MOST;
        held = looking ? held + seconds : 0f;
        boolean raised = looking && held >= DWELL;
        up = ViewModelMath.ease(up, raised ? 1f : 0f, seconds, RAISE_SECONDS);
        if (!raised && up < GONE) {
            up = 0f;
        }
        return up;
    }

    /** How far the arm is up, 0 to 1, as the last step left it. */
    float up() {
        return up;
    }

    /** The arm goes down at once and the next glance waits its moment again. */
    void rest() {
        looking = false;
        wasDown = false;
        held = 0f;
        up = 0f;
    }

    /**
     * Whether a glance is at the left wrist: the only one with a watch on it, or with one on each,
     * the one the look is turned towards.
     *
     * @param turned how far the look is turned from the body, right positive
     */
    static boolean atLeft(boolean rightWorn, boolean leftWorn, float turned) {
        return leftWorn && (!rightWorn || turned < 0f);
    }

    /** How far a view turned to yaw is from a body facing bodyYaw, radians, right positive, from -pi to pi. */
    static float turned(float yaw, float bodyYaw) {
        return (float) Math.IEEEremainder(yaw - bodyYaw, 2 * Math.PI);
    }
}
