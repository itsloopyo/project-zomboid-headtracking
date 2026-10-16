package com.cameraunlock.zomboid;

/**
 * What a mirror in a room shows: the world drawn a second time, from the eye's image behind
 * the mirror's glass, as WaterMirror draws it from under water. A point Q before the glass,
 * drawn from that image, falls on the same place in the picture as Q's own image behind the
 * glass falls seen from the eye, which is where the glass between the eye and that image is
 * drawn. So the glass at a pixel shows what the second picture has at that same pixel.
 *
 * Traced in the eye's own picture, a mirror that faces the viewer shows nothing: what it
 * throws back is what stands behind the viewer, which is never in the picture, and the trace
 * found grey.
 *
 * A window's panes, seen from the room, throw it back too, thinly: see WindowGlass.
 *
 * One plane a frame: the plane of whichever glass, mirrors or windows, takes up the most of
 * the picture, a window's share counted by WINDOW_WEIGHT, and every glass of that kind in it. A mirror in another plane is left to the
 * trace, a window to what its pane throws back by itself.
 */
final class LookingGlass {
    /**
     * A mirror's glass, as Models.Instances.glasses keeps it: one corner of it, the glass's
     * edge across from there and its edge up, and the way its face looks, all on the map in
     * tiles. Its face is the front of the model's pane, which looks along the model's -Y.
     */
    static final int CORNER = 0, ACROSS = 3, UP = 6, FACING = 9, FLOATS = 12;
    /** Nearer its plane than this, in tiles, the eye is as good as in the glass and sees nothing in it. */
    static final float NEAREST = 0.05f;
    /** A mirror further from the eye than this, in tiles (about 20 feet), is drawn as glass with no picture of its own. */
    static final float REACH = 6f;
    /**
     * Over this many tiles at the end of its reach a mirror's own picture fades in over the
     * trace: all of it this far within the reach, none of it at the reach.
     */
    static final float FADE = 1f;
    /** Two mirrors are in one plane when they face the same way to this cosine and stand this far apart along it at most, in tiles. */
    static final float SAME_WAY = 0.9995f, SAME_PLANE = 0.01f;
    /**
     * How far before its plane what the second picture draws begins, in tiles: the glass
     * itself lies in the plane, and from behind it is a sheet that would hide all the rest.
     * The world shader tests a place on the map against it, and a float holds such a place to
     * a millimetre past 8192 tiles and two past 16384, so the glass is cleared by more than
     * that. The nearest any frame comes before its glass is the medicine cabinet's bevel, 3 mm.
     */
    static final float CLEAR = 0.003f;
    /**
     * A window's glass throws back about a fifth of what a mirror's silver does, seen squarely,
     * so in choosing the frame's plane its share of the picture counts for that much.
     */
    static final float WINDOW_WEIGHT = 0.2f;
    /** The most mirrors in the plane whose glass is shown. */
    static final int MOST = 16;
    /** Floats a corner of the glass is drawn with: its place from the eye, how far across and up the glass it is, 0 to 1, and the glass's width and height in tiles. */
    static final int CORNER_FLOATS = 7;

    /**
     * The glass of the frame's mirrors, laid over the eye's picture: each corner from the eye,
     * then how far across and up its glass it is and how wide and high that glass is.
     */
    static final String VERTEX = """
            #version 120
            varying vec3 vPlace;
            varying vec4 vOn;
            void main() {
                vPlace = gl_Vertex.xyz;
                vOn = gl_MultiTexCoord0;
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;

    /**
     * What a mirror shows, as glass silvered at the back shows it. The picture drawn from
     * behind the glass is what the silver throws back, a little short of all of it and tinted
     * by the glass it passes through twice. The front of the glass throws back a little more
     * the flatter it is seen, a faint second image as far before the silver as the glass is
     * thick. Smudges on the glass scatter the light round them, and at the very edge the
     * silver has worn thin.
     */
    static final String SHOWN = "#version 120\n" + CabinGlass.NOISE + """
            uniform sampler2D uPicture;
            uniform vec2 uSize;
            // The part of uPicture drawn this frame, its least and greatest texel centres from 0 to 1: the rest holds other frames' pictures.
            uniform vec4 uDrawn;
            // The way the glass faces, on the map: the corners are the map's from the eye.
            uniform vec3 uFacing;
            // How much of what the glass shows is this picture, the rest left to the trace: see LookingGlass.FADE.
            uniform float uShown;
            varying vec3 vPlace;
            varying vec4 vOn;
            const float SILVER = 0.9;
            const vec3 TINT = vec3(0.93, 0.97, 0.94);
            // How thick the glass is, in tiles.
            const float THICK = 0.005;

            float smudged(vec2 on) {
                float n = noiseAt(on * 9.0) * 0.5 + noiseAt(on * 23.0 + 7.1) * 0.3 + noiseAt(on * 61.0 + 3.7) * 0.2;
                return smoothstep(0.58, 0.85, n);
            }

            vec3 pictureAt(vec2 uv) {
                return texture2D(uPicture, clamp(uv, uDrawn.xy, uDrawn.zw)).rgb;
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                vec3 view = normalize(-vPlace);
                float square = clamp(dot(view, uFacing), 0.0, 1.0);
                float front = 0.04 + 0.96 * pow(1.0 - square, 5.0);
                // The front's image of a place stands as far before the silver's as twice the
                // glass is thick. Taken for a place as far behind the glass as the eye is before it.
                vec4 at = gl_ModelViewProjectionMatrix * vec4(vPlace * 2.0, 1.0);
                vec4 near = gl_ModelViewProjectionMatrix * vec4(vPlace * 2.0 + uFacing * (2.0 * THICK), 1.0);
                vec2 shift = (near.xy / near.w - at.xy / at.w) * 0.5;
                vec3 color = pictureAt(uv) * (SILVER * (1.0 - front) * (1.0 - front)) * TINT + pictureAt(uv + shift) * front;
                vec2 spread = 24.0 / uSize;
                vec3 round = (pictureAt(uv + vec2(spread.x, 0.0)) + pictureAt(uv - vec2(spread.x, 0.0))
                        + pictureAt(uv + vec2(0.0, spread.y)) + pictureAt(uv - vec2(0.0, spread.y))) * 0.25;
                float smudge = smudged(vOn.xy * vOn.zw);
                color = mix(color, round, smudge * 0.3) + round * (smudge * 0.06);
                vec2 edge = min(vOn.xy, 1.0 - vOn.xy) * vOn.zw;
                color *= mix(0.75, 1.0, smoothstep(0.0, 0.02, min(edge.x, edge.y)));
                gl_FragColor = vec4(color, uShown);
            }
            """;

    /**
     * What a window's glass shows of the room, added to what is seen through it: as much as
     * the pane throws back, the share the world's own drawing of the pane took from what is
     * behind it and gave to the street it throws back instead (PipelineShaders, "thrown").
     */
    static final String THROWN = """
            #version 120
            uniform sampler2D uPicture;
            uniform vec2 uSize;
            uniform vec4 uDrawn;
            uniform vec3 uFacing;
            uniform float uShown;
            varying vec3 vPlace;
            void main() {
                vec2 uv = clamp(gl_FragCoord.xy / uSize, uDrawn.xy, uDrawn.zw);
                float square = abs(dot(normalize(-vPlace), uFacing));
                float thrown = min(1.0, 1.6 * (0.13 + 0.87 * pow(1.0 - square, 5.0)));
                gl_FragColor = vec4(texture2D(uPicture, uv).rgb * (thrown * uShown), 0.0);
            }
            """;

    /**
     * The plane the glass of this frame's mirrors lies in: the way it faces and how far along
     * that it is from the map's corner. Glass that counts as in the plane may stand a little
     * before or behind it, and the plane is the foremost of them, so that no glass is left
     * standing before it in the picture drawn from behind.
     */
    final float[] plane = new float[4];
    /** The part of the picture their glass takes up, its least and greatest x and y from -1 to 1. */
    final float[] rect = new float[4];
    /** The glass of each of them, FLOATS each. */
    final float[] glass = new float[MOST * FLOATS];
    /** How much of what each of them shows is its own picture: see FADE. */
    final float[] shown = new float[MOST];
    int count;
    /** Whether the glass is a window's and not a mirror's: see WindowGlass. */
    boolean window;
    /** How far before the plane what the glass shows begins, and how far before it its picture is laid over the eye's, in tiles. */
    float clear, laid;
    /** Whether their glass reaches past the edge of the picture, where a turn of the head brings more of it in. */
    boolean pastEdge;

    /** The most mirrors a frame looks at: more than any room holds. */
    private static final int SEEN_MOST = 64;
    private final float[][] seenGlass = new float[SEEN_MOST][FLOATS], seenRect = new float[SEEN_MOST][4];
    private final float[] seenArea = new float[SEEN_MOST], seenShown = new float[SEEN_MOST];
    private final boolean[] seenPast = new boolean[SEEN_MOST], seenWindow = new boolean[SEEN_MOST];
    private int seen;
    /** Whether the glass the last seen() was asked of reaches past the edge of the picture. */
    private boolean reachesPast;
    /** How much of what the glass the last seen() was asked of shows is its own picture. */
    private float shownSeen;
    private final float[] testPlane = new float[4];
    private final float[] corners = new float[5 * 3], ndc = new float[2];

    /**
     * Notes the glass of a mirror in the piece of furniture just stood, its model's pane, into
     * out from at.
     */
    static void place(Models.Instances placed, int piece, Models.Model model, float[] out, int at) {
        int from = model.first[Models.PANE], to = model.first[Models.PANE + 1];
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE, face = Float.MAX_VALUE;
        for (int vertex = from; vertex < to; vertex++) {
            int v = vertex * Models.VERTEX_FLOATS;
            // Only the face of the pane, which looks along -Y: its edges and back are hidden by its frame and its wall.
            if (model.vertices[v + 4] > -0.9f) {
                continue;
            }
            minX = Math.min(minX, model.vertices[v]);
            maxX = Math.max(maxX, model.vertices[v]);
            minZ = Math.min(minZ, model.vertices[v + 2]);
            maxZ = Math.max(maxZ, model.vertices[v + 2]);
            face = Math.min(face, model.vertices[v + 1]);
        }
        if (minX > maxX) {
            throw new IllegalStateException("the model " + model.name + " is stood as a mirror and has no pane that looks out of its front");
        }
        float[] a = new float[3], b = new float[3], c = new float[3];
        // Model +x is the right of whoever faces the model, and its own left as it faces them.
        placed.stand(piece, maxX, face, minZ, a, 0);
        placed.stand(piece, minX, face, minZ, b, 0);
        placed.stand(piece, maxX, face, maxZ, c, 0);
        int turn = piece * Models.Instances.FLOATS + Models.Instances.TURN;
        out[at + CORNER] = a[0];
        out[at + CORNER + 1] = a[1];
        out[at + CORNER + 2] = a[2];
        for (int axis = 0; axis < 3; axis++) {
            out[at + ACROSS + axis] = b[axis] - a[axis];
            out[at + UP + axis] = c[axis] - a[axis];
        }
        out[at + FACING] = -placed.floats[turn + 1];
        out[at + FACING + 1] = -placed.floats[turn + 3];
        out[at + FACING + 2] = 0f;
    }

    /** The corners of a glass in turn round it, as the share of its edge across and of its edge up. */
    private static final float[] ROUND = {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f};

    /** The plane of a glass, into out: the way it faces and how far along that it is from the map's corner. */
    static void plane(float[] glass, int at, float[] out) {
        out[0] = glass[at + FACING];
        out[1] = glass[at + FACING + 1];
        out[2] = glass[at + FACING + 2];
        out[3] = out[0] * glass[at + CORNER] + out[1] * glass[at + CORNER + 1] + out[2] * glass[at + CORNER + 2];
    }

    /**
     * Whether the eye sees a glass from before it, near enough, and in its picture: into rect,
     * the part of the picture the glass takes up, its least and greatest x and y from -1 to 1,
     * kept to the picture. The glass is cut off where it passes nearer the eye than near.
     * Whether it reaches past the picture is noted before it is kept to it.
     */
    boolean seen(float[] glass, int at, ViewCamera.Pose eye, float tan, float aspect, float near, float[] out) {
        plane(glass, at, testPlane);
        float before = testPlane[0] * eye.eyeX + testPlane[1] * eye.eyeY + testPlane[2] * eye.eyeUp - testPlane[3];
        if (before < NEAREST) {
            return false;
        }
        float nearest = nearest(glass, at, eye);
        if (nearest > REACH) {
            return false;
        }
        shownSeen = shown(nearest);
        // The four corners round, as distances along the eye's axes, then the cut where they pass behind near.
        int count = 0;
        float lastAhead = 0f, lastX = 0f, lastY = 0f;
        for (int k = 0; k <= 4; k++) {
            float s = ROUND[(k % 4) * 2], t = ROUND[(k % 4) * 2 + 1];
            float dx = glass[at + CORNER] + s * glass[at + ACROSS] + t * glass[at + UP] - eye.eyeX;
            float dy = glass[at + CORNER + 1] + s * glass[at + ACROSS + 1] + t * glass[at + UP + 1] - eye.eyeY;
            float du = glass[at + CORNER + 2] + s * glass[at + ACROSS + 2] + t * glass[at + UP + 2] - eye.eyeUp;
            float x = dx * eye.rightX + dy * eye.rightY + du * eye.rightUp;
            float y = dx * eye.upX + dy * eye.upY + du * eye.upUp;
            float ahead = dx * eye.forwardX + dy * eye.forwardY + du * eye.forwardUp;
            if (k > 0 && (lastAhead < near) != (ahead < near)) {
                float f = (near - lastAhead) / (ahead - lastAhead);
                corners[count * 3] = lastX + f * (x - lastX);
                corners[count * 3 + 1] = lastY + f * (y - lastY);
                corners[count * 3 + 2] = near;
                count++;
            }
            if (k < 4 && ahead >= near) {
                corners[count * 3] = x;
                corners[count * 3 + 1] = y;
                corners[count * 3 + 2] = ahead;
                count++;
            }
            lastAhead = ahead;
            lastX = x;
            lastY = y;
        }
        if (count == 0) {
            return false;
        }
        out[0] = out[1] = Float.POSITIVE_INFINITY;
        out[2] = out[3] = Float.NEGATIVE_INFINITY;
        for (int k = 0; k < count; k++) {
            ndc[0] = corners[k * 3] / (corners[k * 3 + 2] * tan * aspect);
            ndc[1] = corners[k * 3 + 1] / (corners[k * 3 + 2] * tan);
            out[0] = Math.min(out[0], ndc[0]);
            out[1] = Math.min(out[1], ndc[1]);
            out[2] = Math.max(out[2], ndc[0]);
            out[3] = Math.max(out[3], ndc[1]);
        }
        reachesPast = out[0] < -1f || out[1] < -1f || out[2] > 1f || out[3] > 1f;
        out[0] = Math.max(out[0], -1f);
        out[1] = Math.max(out[1], -1f);
        out[2] = Math.min(out[2], 1f);
        out[3] = Math.min(out[3], 1f);
        return out[0] < out[2] && out[1] < out[3];
    }

    /** How much of what a glass the eye is this far from shows is its own picture, 0 to 1. */
    static float shown(float nearest) {
        return Math.clamp((REACH - nearest) / FADE, 0f, 1f);
    }

    /** How far the eye is from the nearest point of a glass, in tiles. */
    static float nearest(float[] glass, int at, ViewCamera.Pose eye) {
        float dx = eye.eyeX - glass[at + CORNER], dy = eye.eyeY - glass[at + CORNER + 1], du = eye.eyeUp - glass[at + CORNER + 2];
        float s = along(glass, at + ACROSS, dx, dy, du), t = along(glass, at + UP, dx, dy, du);
        float px = s * glass[at + ACROSS] + t * glass[at + UP] - dx;
        float py = s * glass[at + ACROSS + 1] + t * glass[at + UP + 1] - dy;
        float pu = s * glass[at + ACROSS + 2] + t * glass[at + UP + 2] - du;
        return (float) Math.sqrt(px * px + py * py + pu * pu);
    }

    /** How far along an edge of the glass, 0 to 1, a point at (dx, dy, du) from its corner lies, kept to the edge. */
    private static float along(float[] glass, int edge, float dx, float dy, float du) {
        float length = glass[edge] * glass[edge] + glass[edge + 1] * glass[edge + 1] + glass[edge + 2] * glass[edge + 2];
        return Math.clamp((dx * glass[edge] + dy * glass[edge + 1] + du * glass[edge + 2]) / length, 0f, 1f);
    }

    /** Begins the frame's search: no glass seen yet. */
    void begin() {
        seen = 0;
        count = 0;
    }

    /**
     * Looks at a glass and notes it if the eye sees it.
     *
     * @param glasses as Models.Instances.glasses holds a mirror's, or WindowGlass.Panes a window's
     * @param which  which of them
     */
    void consider(float[] glasses, int which, boolean window, ViewCamera.Pose eye, float tan, float aspect, float near) {
        if (seen == SEEN_MOST) {
            return;
        }
        int offset = which * FLOATS;
        if (!seen(glasses, offset, eye, tan, aspect, near, seenRect[seen])) {
            return;
        }
        System.arraycopy(glasses, offset, seenGlass[seen], 0, FLOATS);
        seenPast[seen] = reachesPast;
        seenWindow[seen] = window;
        seenShown[seen] = shownSeen;
        float[] r = seenRect[seen];
        seenArea[seen] = (r[2] - r[0]) * (r[3] - r[1]);
        seen++;
    }

    /**
     * Ends the search: chooses the plane whose mirrors take up the most of the picture, and
     * keeps those mirrors and the part of the picture they take. False if the eye sees none.
     */
    boolean end() {
        count = 0;
        pastEdge = false;
        float best = 0f;
        int chosen = -1;
        for (int i = 0; i < seen; i++) {
            plane(seenGlass[i], 0, testPlane);
            float area = 0f;
            for (int j = 0; j < seen; j++) {
                if (seenWindow[j] == seenWindow[i] && samePlane(testPlane, seenGlass[j])) {
                    area += seenWindow[j] ? seenArea[j] * WINDOW_WEIGHT : seenArea[j];
                }
            }
            if (area > best) {
                best = area;
                chosen = i;
            }
        }
        if (chosen < 0) {
            return false;
        }
        plane(seenGlass[chosen], 0, plane);
        window = seenWindow[chosen];
        clear = window ? WindowGlass.CLEAR : CLEAR;
        laid = window ? WindowGlass.LAID : CLEAR;
        float foremost = plane[3];
        rect[0] = rect[1] = Float.POSITIVE_INFINITY;
        rect[2] = rect[3] = Float.NEGATIVE_INFINITY;
        for (int j = 0; j < seen && count < MOST; j++) {
            if (seenWindow[j] != window || !samePlane(plane, seenGlass[j])) {
                continue;
            }
            System.arraycopy(seenGlass[j], 0, glass, count * FLOATS, FLOATS);
            shown[count] = seenShown[j];
            count++;
            pastEdge |= seenPast[j];
            foremost = Math.max(foremost, plane[0] * seenGlass[j][CORNER] + plane[1] * seenGlass[j][CORNER + 1] + plane[2] * seenGlass[j][CORNER + 2]);
            float[] r = seenRect[j];
            rect[0] = Math.min(rect[0], r[0]);
            rect[1] = Math.min(rect[1], r[1]);
            rect[2] = Math.max(rect[2], r[2]);
            rect[3] = Math.max(rect[3], r[3]);
        }
        plane[3] = foremost;
        return true;
    }

    private static boolean samePlane(float[] plane, float[] glass) {
        float way = plane[0] * glass[FACING] + plane[1] * glass[FACING + 1] + plane[2] * glass[FACING + 2];
        float off = plane[0] * glass[CORNER] + plane[1] * glass[CORNER + 1] + plane[2] * glass[CORNER + 2] - plane[3];
        return way > SAME_WAY && Math.abs(off) < SAME_PLANE;
    }
}
