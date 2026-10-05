package com.cameraunlock.zomboid;

/**
 * The camera's way between the game's own view and the first-person view, and
 * how far along it the camera is.
 *
 * The game's view is drawn by a camera a long way off with next to no field of
 * view, looking at the middle of the screen. The first-person view is drawn by
 * one at the eye. Between them the camera looks at a place that moves from the
 * one to the other, turns from the game's angle to the way the character
 * faces, opens its field of view, and comes in until it is at the eye. The
 * head's turn and lean come in with it, so it lands on the tracked view.
 *
 * Where the game's own view is the game's own picture, the first-person
 * renderer's picture of the same place from the same angle is mixed in over it
 * before the camera sets off, and mixed out again once it is back: the two are
 * drawn in different ways, and only while the camera is still do they lie on
 * each other.
 *
 * Logic thread only.
 */
final class Flight {
    /** How long the camera takes from one view to the other. */
    static final float SECONDS = Float.parseFloat(System.getProperty("cameraunlock.zomboid.flightSeconds", "0.6"));
    /**
     * The most of a frame's length the camera moves by. The frame the picture is first built
     * on is many times as long as the rest, and taken whole it would be half the way gone.
     */
    private static final float MOST_A_FRAME = 0.05f;

    /** How long one picture takes to mix into the other, before the camera sets off or after it is back. */
    static final float MIX_SECONDS = SECONDS * 2f / 3f;

    /** 0 in the game's view, 1 in the first-person view, and even in time between. */
    private static float at;
    /** How much of the game's own picture the renderer's covers: all of it whenever the camera is off its place overhead. */
    private static float shown;
    private static float tan, away;
    private static final float[] axis = new float[3];

    private Flight() {}

    /**
     * Moves the camera towards the first-person view or away from it over a frame of the given length.
     *
     * @param mixes the game's own view is the game's own picture, which the renderer's is mixed over
     */
    static void advance(boolean in, float seconds, boolean mixes) {
        float time = Math.min(seconds, MOST_A_FRAME);
        if (!mixes) {
            shown = in || at > 0f ? 1f : 0f;
        }
        if (in && shown < 1f) {
            shown = Math.min(shown + time / MIX_SECONDS, 1f);
        } else if (in) {
            at = Math.min(at + time / SECONDS, 1f);
        } else if (at > 0f) {
            at = Math.max(at - time / SECONDS, 0f);
            shown = mixes || at > 0f ? 1f : 0f;
        } else {
            shown = Math.max(shown - time / MIX_SECONDS, 0f);
        }
    }

    /** Puts the camera at one end with no way between. */
    static void cut(boolean in) {
        at = in ? 1f : 0f;
        shown = at;
    }

    /** Whether the camera is at the eye. */
    static boolean landed() {
        return at == 1f;
    }

    /** Whether the camera is back where the game's own view is drawn from, and the renderer's picture is off the game's. */
    static boolean home() {
        return at == 0f && shown == 0f;
    }

    /** How much of the game's own picture the renderer's covers. */
    static float shown() {
        return shown;
    }

    /** How much of the way the camera has come, 0 in the game's view: it sets off and arrives slowly. */
    static float share() {
        return at * at * (3f - 2f * at);
    }

    /** The tangent of half the vertical field of view the camera last had. */
    static float tanHalfFieldOfView() {
        return tan;
    }

    /** How far the camera last was from the place it looks at, in tiles. */
    static float away() {
        return away;
    }

    /**
     * Writes the camera for where it is on its way.
     *
     * @param overhead     the camera the game's own view is drawn from, looking at the middle of the screen
     * @param overheadTan  the tangent of half its vertical field of view
     * @param overheadAway how far it is from the place it looks at
     * @param clean        the first-person view the mouse has set, built from yawTo and pitchTo
     * @param tracked      the first-person view the frame would be drawn from: clean, with the head in it
     * @param tanTo        the tangent of half the first-person view's vertical field of view
     */
    static void camera(ViewCamera.Pose overhead, float overheadTan, float overheadAway, float yawFrom, float pitchFrom,
            ViewCamera.Pose clean, ViewCamera.Pose tracked, float yawTo, float pitchTo, float tanTo, ViewCamera.Pose out) {
        float s = share();
        float turn = (float) Math.IEEEremainder(yawTo - yawFrom, 2 * Math.PI);
        ViewCamera.clean(out, 0f, 0f, 0f, yawFrom + turn * s, pitchFrom + (pitchTo - pitchFrom) * s);

        // The head has turned the tracked view from the clean one. The same turn, taken in the
        // camera's own axes and by as much as the camera has come, is given to the camera.
        headed(out, clean, tracked.rightX, tracked.rightY, tracked.rightUp, out.rightX, out.rightY, out.rightUp, s, axis);
        float rightX = axis[0], rightY = axis[1], rightUp = axis[2];
        headed(out, clean, tracked.upX, tracked.upY, tracked.upUp, out.upX, out.upY, out.upUp, s, axis);
        float upX = axis[0], upY = axis[1], upUp = axis[2];
        headed(out, clean, tracked.forwardX, tracked.forwardY, tracked.forwardUp, out.forwardX, out.forwardY, out.forwardUp, s, axis);
        float forwardX = axis[0], forwardY = axis[1], forwardUp = axis[2];
        // Three vectors mixed apart are no longer square to each other.
        float length = (float) Math.sqrt(forwardX * forwardX + forwardY * forwardY + forwardUp * forwardUp);
        forwardX /= length; forwardY /= length; forwardUp /= length;
        float along = rightX * forwardX + rightY * forwardY + rightUp * forwardUp;
        rightX -= along * forwardX; rightY -= along * forwardY; rightUp -= along * forwardUp;
        length = (float) Math.sqrt(rightX * rightX + rightY * rightY + rightUp * rightUp);
        rightX /= length; rightY /= length; rightUp /= length;
        along = upX * forwardX + upY * forwardY + upUp * forwardUp;
        float across = upX * rightX + upY * rightY + upUp * rightUp;
        upX -= along * forwardX + across * rightX; upY -= along * forwardY + across * rightY; upUp -= along * forwardUp + across * rightUp;
        length = (float) Math.sqrt(upX * upX + upY * upY + upUp * upUp);
        out.rightX = rightX; out.rightY = rightY; out.rightUp = rightUp;
        out.upX = upX / length; out.upY = upY / length; out.upUp = upUp / length;
        out.forwardX = forwardX; out.forwardY = forwardY; out.forwardUp = forwardUp;

        // What the picture takes in at the place looked at shrinks evenly to nothing while the
        // field of view opens evenly, and the distance is whatever gives the one with the other.
        tan = overheadTan + (tanTo - overheadTan) * s;
        away = overheadAway * overheadTan * (1f - s) / tan;
        float atX = overhead.eyeX + overhead.forwardX * overheadAway, atY = overhead.eyeY + overhead.forwardY * overheadAway;
        float atUp = overhead.eyeUp + overhead.forwardUp * overheadAway;
        out.eyeX = atX + (tracked.eyeX - atX) * s - forwardX * away;
        out.eyeY = atY + (tracked.eyeY - atY) * s - forwardY * away;
        out.eyeUp = atUp + (tracked.eyeUp - atUp) * s - forwardUp * away;
    }

    /**
     * One of the camera's axes with the head's turn in it: the tracked view's axis, read in
     * the clean view's axes and set out again in the camera's, mixed with the camera's own by
     * how far the camera has come.
     */
    private static void headed(ViewCamera.Pose camera, ViewCamera.Pose clean, float x, float y, float up,
            float ownX, float ownY, float ownUp, float share, float[] out) {
        float right = x * clean.rightX + y * clean.rightY + up * clean.rightUp;
        float above = x * clean.upX + y * clean.upY + up * clean.upUp;
        float ahead = x * clean.forwardX + y * clean.forwardY + up * clean.forwardUp;
        out[0] = ownX + (camera.rightX * right + camera.upX * above + camera.forwardX * ahead - ownX) * share;
        out[1] = ownY + (camera.rightY * right + camera.upY * above + camera.forwardY * ahead - ownY) * share;
        out[2] = ownUp + (camera.rightUp * right + camera.upUp * above + camera.forwardUp * ahead - ownUp) * share;
    }
}
