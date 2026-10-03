package com.cameraunlock.zomboid;

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

    static float[] view(Pose pose) {
        return new float[] {
            pose.rightX, pose.upX, -pose.forwardX, 0f,
            pose.rightY, pose.upY, -pose.forwardY, 0f,
            pose.rightUp, pose.upUp, -pose.forwardUp, 0f,
            -(pose.rightX * pose.eyeX + pose.rightY * pose.eyeY + pose.rightUp * pose.eyeUp),
            -(pose.upX * pose.eyeX + pose.upY * pose.eyeY + pose.upUp * pose.eyeUp),
            pose.forwardX * pose.eyeX + pose.forwardY * pose.eyeY + pose.forwardUp * pose.eyeUp,
            1f,
        };
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
        float f = 1f / tanHalfVerticalFov;
        return new float[] {
            f / aspect, 0f, 0f, 0f,
            0f, f, 0f, 0f,
            0f, 0f, (far + near) / (near - far), -1f,
            0f, 0f, 2f * far * near / (near - far), 0f,
        };
    }
}
