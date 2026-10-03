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

    private ViewCamera() {}

    /**
     * @param eyeX   eye position, tiles
     * @param eyeY   eye position, tiles
     * @param eyeUp  eye height above level 0, tiles (a level is LEVEL_HEIGHT)
     * @param yaw    radians, 0 facing +x, positive turning towards +y (to the right)
     * @param pitch  radians, positive looking up
     */
    static float[] view(float eyeX, float eyeY, float eyeUp, float yaw, float pitch) {
        float cy = (float) Math.cos(yaw);
        float sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch);
        float sp = (float) Math.sin(pitch);

        float fx = cy * cp, fy = sy * cp, fz = sp;
        float rx = -sy, ry = cy, rz = 0f;
        float ux = -cy * sp, uy = -sy * sp, uz = cp;

        return new float[] {
            rx, ux, -fx, 0f,
            ry, uy, -fy, 0f,
            rz, uz, -fz, 0f,
            -(rx * eyeX + ry * eyeY + rz * eyeUp),
            -(ux * eyeX + uy * eyeY + uz * eyeUp),
            fx * eyeX + fy * eyeY + fz * eyeUp,
            1f,
        };
    }

    /** OpenGL perspective projection, depth -1 at the near plane to +1 at the far plane. */
    static float[] projection(float verticalFovRadians, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(verticalFovRadians / 2.0));
        return new float[] {
            f / aspect, 0f, 0f, 0f,
            0f, f, 0f, 0f,
            0f, 0f, (far + near) / (near - far), -1f,
            0f, 0f, 2f * far * near / (near - far), 0f,
        };
    }
}
