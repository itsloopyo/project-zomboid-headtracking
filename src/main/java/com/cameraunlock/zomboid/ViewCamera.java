package com.cameraunlock.zomboid;

import java.util.Arrays;

/**
 * The first-person camera: an eye position and an orientation in the game's
 * world, and the one place they become view and projection matrices.
 *
 * World axes are the game's own: x and y lie on the ground in tiles, and z is
 * the floor level. The game's isometric projection puts a world point at screen
 * ((x - y) * 32, (x + y) * 16 - z * 96), y down the screen, so seen from above
 * +y is a quarter turn clockwise from +x: someone facing +x has +y on their
 * right. That makes (x, y, up) a left-handed frame, and the view matrix here is
 * built from explicit right, up and forward vectors rather than a right-handed
 * look-at, which would mirror the picture.
 *
 * Matrices are column-major float[16], the layout OpenGL loads.
 */
final class ViewCamera {
    /**
     * Height of one floor level in tiles. A tile's 64 pixel diagonal makes its
     * side 64 / sqrt(2) pixels on screen and the 2:1 projection looks down at
     * 30 degrees, so a level's 96 pixels are 96 / (cos(30) * 64 / sqrt(2))
     * tiles. The game uses the same figure to place its 3D models.
     */
    static final float LEVEL_HEIGHT = 2.44949f;

    /** Nearer the eye plane than this, a point projects towards infinity. */
    private static final float MIN_DEPTH_AHEAD = 1e-3f;

    private ViewCamera() {}

    /** An eye position and the three unit vectors of the view from it, in world axes. */
    static final class Pose {
        float eyeX, eyeY, eyeUp;
        float rightX, rightY, rightUp;
        float upX, upY, upUp;
        float forwardX, forwardY, forwardUp;

        void set(Pose other) {
            eyeX = other.eyeX; eyeY = other.eyeY; eyeUp = other.eyeUp;
            rightX = other.rightX; rightY = other.rightY; rightUp = other.rightUp;
            upX = other.upX; upY = other.upY; upUp = other.upUp;
            forwardX = other.forwardX; forwardY = other.forwardY; forwardUp = other.forwardUp;
        }
    }

    /**
     * The view the mouse has set, with no head tracking in it.
     *
     * @param eyeX   eye position, tiles
     * @param eyeY   eye position, tiles
     * @param eyeUp  eye height above level 0, tiles (a level is LEVEL_HEIGHT)
     * @param yaw    radians, 0 facing +x, positive turning towards +y (to the right)
     * @param pitch  radians, positive looking up
     */
    static void clean(Pose out, float eyeX, float eyeY, float eyeUp, float yaw, float pitch) {
        float cy = (float) Math.cos(yaw);
        float sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch);
        float sp = (float) Math.sin(pitch);
        out.eyeX = eyeX; out.eyeY = eyeY; out.eyeUp = eyeUp;
        out.forwardX = cy * cp; out.forwardY = sy * cp; out.forwardUp = sp;
        out.rightX = -sy; out.rightY = cy; out.rightUp = 0f;
        out.upX = -cy * sp; out.upY = -sy * sp; out.upUp = cp;
    }

    /**
     * Turns a view by the head: yaw, then pitch about the turned right vector,
     * then roll about the resulting forward vector. Angles are radians in this
     * camera's senses: positive yaw turns right, positive pitch looks up,
     * positive roll tilts the head to the left. World yaw turns about straight
     * up, which keeps the horizon level when the view is pitched; otherwise the
     * yaw is about the view's own up vector.
     */
    static void turn(Pose pose, float yaw, float pitch, float roll, boolean worldYaw) {
        float c = (float) Math.cos(yaw);
        float s = (float) Math.sin(yaw);
        if (worldYaw) {
            float fx = pose.forwardX * c - pose.forwardY * s, fy = pose.forwardX * s + pose.forwardY * c;
            float rx = pose.rightX * c - pose.rightY * s, ry = pose.rightX * s + pose.rightY * c;
            float ux = pose.upX * c - pose.upY * s, uy = pose.upX * s + pose.upY * c;
            pose.forwardX = fx; pose.forwardY = fy;
            pose.rightX = rx; pose.rightY = ry;
            pose.upX = ux; pose.upY = uy;
        } else {
            float fx = pose.forwardX * c + pose.rightX * s, fy = pose.forwardY * c + pose.rightY * s,
                    fu = pose.forwardUp * c + pose.rightUp * s;
            pose.rightX = pose.rightX * c - pose.forwardX * s;
            pose.rightY = pose.rightY * c - pose.forwardY * s;
            pose.rightUp = pose.rightUp * c - pose.forwardUp * s;
            pose.forwardX = fx; pose.forwardY = fy; pose.forwardUp = fu;
        }

        c = (float) Math.cos(pitch);
        s = (float) Math.sin(pitch);
        float fx = pose.forwardX * c + pose.upX * s, fy = pose.forwardY * c + pose.upY * s,
                fu = pose.forwardUp * c + pose.upUp * s;
        pose.upX = pose.upX * c - pose.forwardX * s;
        pose.upY = pose.upY * c - pose.forwardY * s;
        pose.upUp = pose.upUp * c - pose.forwardUp * s;
        pose.forwardX = fx; pose.forwardY = fy; pose.forwardUp = fu;

        c = (float) Math.cos(roll);
        s = (float) Math.sin(roll);
        float ux = pose.upX * c - pose.rightX * s, uy = pose.upY * c - pose.rightY * s,
                uu = pose.upUp * c - pose.rightUp * s;
        pose.rightX = pose.rightX * c + pose.upX * s;
        pose.rightY = pose.rightY * c + pose.upY * s;
        pose.rightUp = pose.rightUp * c + pose.upUp * s;
        pose.upX = ux; pose.upY = uy; pose.upUp = uu;
    }

    /**
     * Turns a view about its eye by three rows of three: what the viewer sits in has tipped
     * or rolled, and the view goes with it.
     */
    static void tilt(Pose pose, float[] by) {
        float x = pose.rightX, y = pose.rightY, up = pose.rightUp;
        pose.rightX = by[0] * x + by[1] * y + by[2] * up;
        pose.rightY = by[3] * x + by[4] * y + by[5] * up;
        pose.rightUp = by[6] * x + by[7] * y + by[8] * up;
        x = pose.upX; y = pose.upY; up = pose.upUp;
        pose.upX = by[0] * x + by[1] * y + by[2] * up;
        pose.upY = by[3] * x + by[4] * y + by[5] * up;
        pose.upUp = by[6] * x + by[7] * y + by[8] * up;
        x = pose.forwardX; y = pose.forwardY; up = pose.forwardUp;
        pose.forwardX = by[0] * x + by[1] * y + by[2] * up;
        pose.forwardY = by[3] * x + by[4] * y + by[5] * up;
        pose.forwardUp = by[6] * x + by[7] * y + by[8] * up;
    }

    static float[] view(Pose pose) {
        float[] out = new float[16];
        view(pose, out);
        return out;
    }

    /** As view, into a matrix the caller keeps: a frame that makes one each time it draws makes garbage. */
    static void view(Pose pose, float[] out) {
        turned(pose, out);
        out[12] = -(pose.rightX * pose.eyeX + pose.rightY * pose.eyeY + pose.rightUp * pose.eyeUp);
        out[13] = -(pose.upX * pose.eyeX + pose.upY * pose.eyeY + pose.upUp * pose.eyeUp);
        out[14] = pose.forwardX * pose.eyeX + pose.forwardY * pose.eyeY + pose.forwardUp * pose.eyeUp;
    }

    /**
     * The matrix that takes a point from one view's axes to another's: x to the right, y up
     * and z towards the viewer, each from its own eye. It is the second view's matrix times
     * the inverse of the first's.
     */
    static void between(Pose from, Pose to, float[] out) {
        out[0] = to.rightX * from.rightX + to.rightY * from.rightY + to.rightUp * from.rightUp;
        out[1] = to.upX * from.rightX + to.upY * from.rightY + to.upUp * from.rightUp;
        out[2] = -(to.forwardX * from.rightX + to.forwardY * from.rightY + to.forwardUp * from.rightUp);
        out[3] = 0f;
        out[4] = to.rightX * from.upX + to.rightY * from.upY + to.rightUp * from.upUp;
        out[5] = to.upX * from.upX + to.upY * from.upY + to.upUp * from.upUp;
        out[6] = -(to.forwardX * from.upX + to.forwardY * from.upY + to.forwardUp * from.upUp);
        out[7] = 0f;
        out[8] = -(to.rightX * from.forwardX + to.rightY * from.forwardY + to.rightUp * from.forwardUp);
        out[9] = -(to.upX * from.forwardX + to.upY * from.forwardY + to.upUp * from.forwardUp);
        out[10] = to.forwardX * from.forwardX + to.forwardY * from.forwardY + to.forwardUp * from.forwardUp;
        out[11] = 0f;
        float x = from.eyeX - to.eyeX, y = from.eyeY - to.eyeY, up = from.eyeUp - to.eyeUp;
        out[12] = to.rightX * x + to.rightY * y + to.rightUp * up;
        out[13] = to.upX * x + to.upY * y + to.upUp * up;
        out[14] = -(to.forwardX * x + to.forwardY * y + to.forwardUp * up);
        out[15] = 1f;
    }

    /**
     * The view with the eye at the origin: what view does to a point that has had the eye's
     * place taken off it already. Taken off by the matrix, after the point has been multiplied
     * through, the last digits of both are lost: see the world's vertex program.
     */
    static float[] turned(Pose pose) {
        float[] out = new float[16];
        turned(pose, out);
        return out;
    }

    /** As turned, into a matrix the caller keeps. */
    static void turned(Pose pose, float[] out) {
        out[0] = pose.rightX;
        out[1] = pose.upX;
        out[2] = -pose.forwardX;
        out[3] = 0f;
        out[4] = pose.rightY;
        out[5] = pose.upY;
        out[6] = -pose.forwardY;
        out[7] = 0f;
        out[8] = pose.rightUp;
        out[9] = pose.upUp;
        out[10] = -pose.forwardUp;
        out[11] = 0f;
        out[12] = 0f;
        out[13] = 0f;
        out[14] = 0f;
        out[15] = 1f;
    }

    /**
     * Where a world point falls in the view, in normalised device coordinates
     * (x to the right, y up, the screen edges at -1 and 1). Returns false for a
     * point that is not in front of the eye, where the divide has no meaning.
     */
    static boolean project(Pose pose, float tanHalfVerticalFov, float aspect, float x, float y, float up, float[] ndc) {
        float dx = x - pose.eyeX, dy = y - pose.eyeY, du = up - pose.eyeUp;
        float ahead = dx * pose.forwardX + dy * pose.forwardY + du * pose.forwardUp;
        if (ahead <= MIN_DEPTH_AHEAD) {
            return false;
        }
        ndc[0] = (dx * pose.rightX + dy * pose.rightY + du * pose.rightUp) / ahead / (tanHalfVerticalFov * aspect);
        ndc[1] = (dx * pose.upX + dy * pose.upY + du * pose.upUp) / ahead / tanHalfVerticalFov;
        return true;
    }

    /**
     * The near plane distance that puts the near plane's corners a given distance from the eye.
     * The corners are the part of it furthest from the eye, and they move out as the field of
     * view or the screen widens.
     */
    static float nearPlane(float cornerDistance, float tanHalfVerticalFov, float aspect) {
        return cornerDistance / (float) Math.sqrt(1f + tanHalfVerticalFov * tanHalfVerticalFov * (1f + aspect * aspect));
    }

    /** OpenGL perspective projection, depth -1 at the near plane to +1 at the far plane. */
    static float[] projection(float tanHalfVerticalFov, float aspect, float near, float far) {
        float[] out = new float[16];
        projection(tanHalfVerticalFov, aspect, near, far, out);
        return out;
    }

    /** As projection, into a matrix the caller keeps. */
    static void projection(float tanHalfVerticalFov, float aspect, float near, float far, float[] out) {
        float f = 1f / tanHalfVerticalFov;
        Arrays.fill(out, 0f);
        out[0] = f / aspect;
        out[5] = f;
        out[10] = (far + near) / (near - far);
        out[11] = -1f;
        out[14] = 2f * far * near / (near - far);
    }

    /**
     * The place on the floor of the game's own view that is drawn at a point of the screen,
     * into out: (x, y) on that floor is drawn at (atX, atY), and a step east or south on the
     * map moves a point by (eastX, eastY) or (southX, southY) on the screen. The view has no
     * perspective, so the two steps take the screen to the floor and back.
     */
    static void underScreen(float x, float y, float atX, float atY, float eastX, float eastY, float southX, float southY, float screenX, float screenY,
            float[] out) {
        float toX = screenX - atX, toY = screenY - atY;
        float det = eastX * southY - southX * eastY;
        out[0] = x + (toX * southY - southX * toY) / det;
        out[1] = y + (eastX * toY - toX * eastY) / det;
    }
}
