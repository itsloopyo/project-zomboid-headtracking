package com.cameraunlock.zomboid;

/**
 * What water shows of the world above it, worked out so that a point on the water shows the
 * same thing wherever the head points.
 *
 * The world is drawn a second time from the eye's image under the water. A point Q above the
 * water, drawn from that image, falls on the same place in the picture as Q's own image under
 * the water would fall seen from the eye, and that is the place where the water between the eye
 * and that image is drawn. So the water at a pixel shows what the second picture has at that
 * same pixel, and nothing of it depends on what else the first picture happens to hold.
 *
 * Water lies on the country's slow rise and fall (Terrain.waterLevel), which over the width of
 * a river is a plane to within a few hundredths of a tile: the plane that touches it under
 * the eye is the mirror.
 */
final class WaterMirror {
    /**
     * How much wider than the eye's picture the second one is drawn, each way. A ripple throws
     * the eye's line to one side of where level water would, and at the edge of the picture
     * that is outside it: the margin is what is there to be shown.
     */
    static final float MARGIN = 1.2f;

    /**
     * How far off the plane water is taken to lie, in tiles: the country's rise and fall puts
     * water eighty tiles off within two thirds of a tile of the plane under the eye.
     */
    private static final float OFF_PLANE = 0.7f;
    /** How far ahead of the eye a box round water is cut off, in tiles. */
    private static final float CLIP_AHEAD = 0.05f;
    /**
     * How far off what water shows is taken to stand, in tiles, where a ripple throws the
     * eye's line aside: the world's program looks that far along the thrown line.
     */
    static final float THROW = 24f;
    /**
     * The most a ripple tilts water, as a slope, in a light breeze: three times what it mostly
     * does. How much more an angle is of the picture at its sides than in its middle, at the
     * widest field of view the picture is drawn with. And a little more round all of it, as a
     * share of half the picture.
     */
    private static final float TILT_MOST = 0.15f, SIDE_SPREAD = 2f, SPARE = 0.03f;

    private WaterMirror() {}

    /** Readies a rectangle of the picture, its least and greatest x and y from -1 to 1, for takeIn: nothing in it yet. */
    static void nothingSeen(float[] rect) {
        rect[0] = rect[1] = Float.POSITIVE_INFINITY;
        rect[2] = rect[3] = Float.NEGATIVE_INFINITY;
    }

    /**
     * Widens the rectangle to where the water over a stretch of the map can be in a picture
     * from the eye, tan its half height as a tangent: the box over the stretch that the water's
     * plane runs through, cut off where it passes behind the eye, which a turn of the head can
     * put at any edge of the picture. Past the edge of the picture is past -1 or 1. And round
     * that, as far as a ripple on that water can throw what it shows: twice the most the wind
     * tilts it, roughness as ruffle gives it, as the angle it turns the line through, of which
     * a place THROW along the thrown line is the less the further off the water is.
     *
     * @param corners room for 32 floats
     */
    static void takeIn(ViewCamera.Pose eye, float[] plane, float minX, float minY, float maxX, float maxY, float tan, float aspect, float roughness,
            float[] corners, float[] rect) {
        float awayX = Math.max(0f, Math.max(minX - eye.eyeX, eye.eyeX - maxX)), awayY = Math.max(0f, Math.max(minY - eye.eyeY, eye.eyeY - maxY));
        float throwing = 2f * TILT_MOST * roughness * THROW / ((float) Math.hypot(awayX, awayY) + THROW);
        float reachX = throwing * SIDE_SPREAD / (tan * aspect), reachY = throwing / tan;
        for (int corner = 0; corner < 8; corner++) {
            float x = (corner & 1) == 0 ? minX : maxX, y = (corner & 2) == 0 ? minY : maxY;
            float up = (plane[3] - plane[0] * x - plane[1] * y) / plane[2] + ((corner & 4) == 0 ? -OFF_PLANE : OFF_PLANE);
            float dx = x - eye.eyeX, dy = y - eye.eyeY, du = up - eye.eyeUp;
            int at = corner * 4;
            corners[at] = dx * eye.rightX + dy * eye.rightY + du * eye.rightUp;
            corners[at + 1] = dx * eye.upX + dy * eye.upY + du * eye.upUp;
            corners[at + 2] = dx * eye.forwardX + dy * eye.forwardY + du * eye.forwardUp;
        }
        for (int a = 0; a < 8; a++) {
            for (int b = a; b < 8; b++) {
                float aheadA = corners[a * 4 + 2], aheadB = corners[b * 4 + 2];
                float across, upward, ahead;
                if (a == b) {
                    if (aheadA < CLIP_AHEAD) {
                        continue;
                    }
                    across = corners[a * 4];
                    upward = corners[a * 4 + 1];
                    ahead = aheadA;
                } else if (aheadA < CLIP_AHEAD != aheadB < CLIP_AHEAD) {
                    float t = (CLIP_AHEAD - aheadA) / (aheadB - aheadA);
                    across = corners[a * 4] + t * (corners[b * 4] - corners[a * 4]);
                    upward = corners[a * 4 + 1] + t * (corners[b * 4 + 1] - corners[a * 4 + 1]);
                    ahead = CLIP_AHEAD;
                } else {
                    continue;
                }
                float x = across / (ahead * tan * aspect), y = upward / (ahead * tan);
                rect[0] = Math.min(rect[0], x - reachX);
                rect[1] = Math.min(rect[1], y - reachY);
                rect[2] = Math.max(rect[2], x + reachX);
                rect[3] = Math.max(rect[3], y + reachY);
            }
        }
    }

    /**
     * How far from the eye the nearest water over a stretch of the map can be, in tiles: as
     * far as the eye is from the stretch along the map, and from the water's plane less how
     * far off it water may lie.
     */
    static float nearest(ViewCamera.Pose eye, float[] plane, float minX, float minY, float maxX, float maxY) {
        float awayX = Math.max(0f, Math.max(minX - eye.eyeX, eye.eyeX - maxX)), awayY = Math.max(0f, Math.max(minY - eye.eyeY, eye.eyeY - maxY));
        float over = Math.max(0f, Math.abs(above(plane, eye.eyeX, eye.eyeY, eye.eyeUp)) - OFF_PLANE);
        return (float) Math.sqrt(awayX * awayX + awayY * awayY + over * over);
    }

    /**
     * Keeps the rectangle to the picture, as keepToPicture does, and says whether the water
     * taken into it puts any pixel in the picture. Water behind the eye or off to a side takes
     * in nothing of it, and the picture of what water shows is then not drawn at all.
     */
    static boolean showsAny(float[] rect) {
        keepToPicture(rect);
        return rect[0] < rect[2] && rect[1] < rect[3];
    }

    /** Widens the rectangle by a little more and keeps it to the picture. */
    static void keepToPicture(float[] rect) {
        rect[0] = Math.max(-1f, rect[0] - SPARE);
        rect[1] = Math.max(-1f, rect[1] - SPARE);
        rect[2] = Math.min(1f, rect[2] + SPARE);
        rect[3] = Math.min(1f, rect[3] + SPARE);
    }

    /** How rough a view's picture draws water: in Low as a light breeze leaves it in any wind, elsewhere as ruffle has it. */
    static float ruffleDrawn(int view, float push) {
        return Quality.plain(view) ? 1f : ruffle(push);
    }

    /**
     * How rough the wind makes water, as a multiple of what a light breeze does: push is how
     * hard the wind pushes on what it blows (Wind.push), a tenth in a calm and two in a gale.
     * Water is never glass, and a gale does not stand it on end.
     */
    static float ruffle(float push) {
        return Math.clamp(0.55f + 1.6f * push, 0.55f, 2.4f);
    }

    /**
     * The plane that touches the water's surface under (x, y): the unit vector square to it,
     * pointing up, and how far along that vector the plane is from the map's corner. At the gain
     * the heights under (x, y) were worked out at, which the water drawn there was built from.
     */
    static void plane(Terrain terrain, float x, float y, float[] out) {
        float gain = terrain.gainAt(x, y);
        float slopeX = (Terrain.waterLevel(gain, x + 1f, y) - Terrain.waterLevel(gain, x - 1f, y)) / 2f;
        float slopeY = (Terrain.waterLevel(gain, x, y + 1f) - Terrain.waterLevel(gain, x, y - 1f)) / 2f;
        float length = (float) Math.sqrt(slopeX * slopeX + slopeY * slopeY + 1f);
        out[0] = -slopeX / length;
        out[1] = -slopeY / length;
        out[2] = 1f / length;
        out[3] = out[0] * x + out[1] * y + out[2] * Terrain.waterLevel(gain, x, y);
    }

    /** How far above the plane a point is, in tiles. */
    static float above(float[] plane, float x, float y, float up) {
        return plane[0] * x + plane[1] * y + plane[2] * up - plane[3];
    }

    /**
     * The eye's image in the plane: as far under it as the eye is over it, with its three axes
     * turned over with it. Its axes are a mirror image of the eye's, so the picture drawn from
     * it is not turned over a second time when it is shown.
     */
    static void mirror(ViewCamera.Pose eye, float[] plane, ViewCamera.Pose out) {
        float over = 2f * above(plane, eye.eyeX, eye.eyeY, eye.eyeUp);
        out.eyeX = eye.eyeX - over * plane[0];
        out.eyeY = eye.eyeY - over * plane[1];
        out.eyeUp = eye.eyeUp - over * plane[2];
        float right = 2f * (plane[0] * eye.rightX + plane[1] * eye.rightY + plane[2] * eye.rightUp);
        out.rightX = eye.rightX - right * plane[0];
        out.rightY = eye.rightY - right * plane[1];
        out.rightUp = eye.rightUp - right * plane[2];
        float up = 2f * (plane[0] * eye.upX + plane[1] * eye.upY + plane[2] * eye.upUp);
        out.upX = eye.upX - up * plane[0];
        out.upY = eye.upY - up * plane[1];
        out.upUp = eye.upUp - up * plane[2];
        float forward = 2f * (plane[0] * eye.forwardX + plane[1] * eye.forwardY + plane[2] * eye.forwardUp);
        out.forwardX = eye.forwardX - forward * plane[0];
        out.forwardY = eye.forwardY - forward * plane[1];
        out.forwardUp = eye.forwardUp - forward * plane[2];
    }
}
