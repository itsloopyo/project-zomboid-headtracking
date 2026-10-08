package com.cameraunlock.zomboid;

/**
 * What a matrix puts in a picture, as the six planes that bound it, for telling what need not
 * be drawn into the picture at all: a thing wholly on the far side of one of the planes puts
 * no pixel in it.
 */
final class MapBounds {
    /**
     * How far outside the picture a box has to be before it is said to be, in tiles. Where the
     * graphics card puts a vertex and where this works it out to be differ by the rounding of
     * numbers the size of the map, a thousandth of a tile.
     */
    private static final double OUTSIDE_BY = 0.05;

    /**
     * Each plane as (x, y, up, d) with (x, y, up) of unit length: a point is on the picture's
     * side of a plane where x times its x and so on, plus d, is above 0, and that number is how
     * far inside it is.
     */
    private final double[] planes = new double[24];
    private final float[] fromEye = new float[16];

    /** Takes the bounds of the picture a matrix makes: column-major, as OpenGL loads one, from the map's tiles to clip space. */
    void of(float[] m) {
        for (int plane = 0; plane < 6; plane++) {
            int row = plane / 2;
            double sign = (plane & 1) == 0 ? 1.0 : -1.0;
            double x = m[3] + sign * m[row], y = m[7] + sign * m[4 + row], up = m[11] + sign * m[8 + row], d = m[15] + sign * m[12 + row];
            double length = Math.sqrt(x * x + y * y + up * up);
            planes[plane * 4] = x / length;
            planes[plane * 4 + 1] = y / length;
            planes[plane * 4 + 2] = up / length;
            planes[plane * 4 + 3] = d / length;
        }
    }

    /**
     * Takes the bounds of the picture a projection makes of what an eye's turn leaves before it,
     * for boxes given by where they are from the eye and not by where they are on the map: the
     * world's vertex program takes the eye's place off a vertex before it turns it, and a box
     * is asked of the same way.
     *
     * @param turned what ViewCamera.turned made of the eye
     */
    void ofTurned(float[] projection, float[] turned) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += projection[k * 4 + row] * turned[column * 4 + k];
                }
                fromEye[column * 4 + row] = sum;
            }
        }
        of(fromEye);
    }

    /** Whether a box, given by its middle and how far it reaches each way, is wholly outside the picture. */
    boolean outside(float x, float y, float up, float halfX, float halfY, float halfUp) {
        for (int plane = 0; plane < 24; plane += 4) {
            double nx = planes[plane], ny = planes[plane + 1], nz = planes[plane + 2];
            double inside = nx * x + ny * y + nz * up + planes[plane + 3] + Math.abs(nx) * halfX + Math.abs(ny) * halfY + Math.abs(nz) * halfUp;
            if (inside < -OUTSIDE_BY) {
                return true;
            }
        }
        return false;
    }
}
