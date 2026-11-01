package com.cameraunlock.zomboid;

/**
 * When each face of the picture of the surroundings (Surroundings) is drawn, and from where.
 * There are two pictures: the one shown, and the one being drawn a face a frame, which takes
 * the shown one's place once its sixth face is in. So what is shown is always six faces taken
 * from one place a few frames apart, and never half of one place and half of another.
 *
 * A picture is begun from where the eye is then. While the eye stays within RETAKE of where the
 * shown picture was taken, a face is drawn every SETTLED_EVERY frames, which keeps up with the
 * light and the weather. Once it goes further, or while nothing is shown, a face is drawn every
 * frame. The shown picture fades out of the reflections between RETAKE and GONE from where it
 * was taken.
 *
 * Render thread only.
 */
final class SurroundingsSchedule {
    static final int FACES = 6;
    /** How far the eye goes from where the shown picture was taken before it is taken again a face a frame, and before it is no longer shown at all, in tiles. */
    static final float RETAKE = 3f, GONE = 6f;
    /** How many frames apart the faces are drawn while the eye stays near where the shown picture was taken. */
    static final int SETTLED_EVERY = 8;

    /**
     * Each face's way across, way up and way ahead, in the world's axes, in the order of the
     * cube map's faces (+x, -x, +y, -y, +z, -z). The picture is looked into with a way in the
     * world turned to (x, y, -up). The eye's across, up and ahead are of the other hand from a
     * cube map face's, and with the turn each face is drawn with axes of the eye's own hand.
     */
    static final float[][] BASES = {
            {0f, 0f, 1f, 0f, -1f, 0f, 1f, 0f, 0f},
            {0f, 0f, -1f, 0f, -1f, 0f, -1f, 0f, 0f},
            {1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f},
            {1f, 0f, 0f, 0f, 0f, 1f, 0f, -1f, 0f},
            {1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, -1f},
            {-1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f}};

    private boolean shown, taking, begun;
    private double shownX, shownY, shownUp, takingX, takingY, takingUp;
    private int faces, lastFrame, showing;

    /** What next said to draw: the face, which of the two pictures it goes into, and where it is drawn from. */
    int face, into;
    double atX, atY, atUp;

    /**
     * Whether a face is drawn this frame for an eye at (x, y, up), and if so which, into which
     * picture and from where (face, into, atX, atY, atUp). The caller draws it this frame:
     * the face counts as drawn, and with the sixth in, its picture is the one shown.
     */
    boolean next(double x, double y, double up, int frame) {
        boolean moved = !shown || apart(x, y, up) > RETAKE;
        if (begun && !moved && frame - lastFrame < SETTLED_EVERY) {
            return false;
        }
        if (!taking) {
            taking = true;
            faces = 0;
            takingX = x;
            takingY = y;
            takingUp = up;
        }
        face = faces++;
        into = 1 - showing;
        atX = takingX;
        atY = takingY;
        atUp = takingUp;
        begun = true;
        lastFrame = frame;
        if (faces == FACES) {
            taking = false;
            shown = true;
            showing = into;
            shownX = takingX;
            shownY = takingY;
            shownUp = takingUp;
        }
        return true;
    }

    /** Whether a whole picture is there to be shown. */
    boolean shown() {
        return shown;
    }

    /** Which of the two pictures is shown. */
    int showing() {
        return showing;
    }

    double shownX() {
        return shownX;
    }

    double shownY() {
        return shownY;
    }

    double shownUp() {
        return shownUp;
    }

    /** How much of the shown picture the reflections take for an eye at (x, y, up): 1 near where it was taken, none from GONE off. */
    float weight(double x, double y, double up) {
        if (!shown) {
            return 0f;
        }
        float t = (float) Math.clamp((apart(x, y, up) - RETAKE) / (GONE - RETAKE), 0.0, 1.0);
        return 1f - t * t * (3f - 2f * t);
    }

    /** Starts again from nothing: the pictures were given back to the graphics card. */
    void forget() {
        shown = taking = begun = false;
        faces = 0;
        showing = 0;
    }

    private double apart(double x, double y, double up) {
        double dx = x - shownX, dy = y - shownY, du = up - shownUp;
        return Math.sqrt(dx * dx + dy * dy + du * du);
    }
}
