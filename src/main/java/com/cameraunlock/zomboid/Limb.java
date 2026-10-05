package com.cameraunlock.zomboid;

/** What a mesh has of one arm, in the mesh's own places as it was made. */
final class Limb {
    /** A wrist thinner than this against its width is a card. */
    private static final float FLAT = 1e-3f;
    /** How many places on each length of an arm its sleeve's colour is read at. */
    static final int SAMPLES = 3;

    /** The middles of the wrist and of the elbow, or null in a mesh that does not reach across one, and how far its skin stands off the elbow. */
    float[] wrist, elbow;
    float elbowWide;
    /**
     * The shape of the wrist seen along the forearm, in a mesh that has both and whose wrist is not flat: the line
     * across it, of length one, and half its width along that line and square to it. The
     * oval of those two halves has every corner of the wrist inside it.
     */
    float[] across;
    float wristAcross, wristThrough;
    /** The middle of the hand, or null in a mesh with none of it. */
    float[] palm;
    /** Texture places well inside the forearm's skin and the upper arm's, SAMPLES pairs each, or null in a mesh that covers none of it. */
    float[] foreUv, upperUv;

    /**
     * What a mesh has of one arm. Each vertex is taken to belong to the bone that has most of
     * it. The wrist is where triangles reach from the hand's vertices to the forearm's, and
     * the elbow where they reach from the forearm's to the upper arm's: the middle of each is
     * the mean of those triangles' corners. The colour of what is worn on a length of the arm
     * is read at the middles of the triangles nearest half way along it, which are inside the
     * texture's piece for that length and not on its edge.
     *
     * @param kinds what each vertex's bone is to an arm
     * @param side  0 for the right arm, 1 for the left
     */
    static Limb read(float[] places, float[] uvs, int[] kinds, int[] elements, int count, int side) {
        int hand = side * CharacterDraw.KINDS + CharacterDraw.HAND, fore = side * CharacterDraw.KINDS + CharacterDraw.FORE, upper = side * CharacterDraw.KINDS + CharacterDraw.UPPER;
        Limb limb = new Limb();
        boolean[] atWrist = new boolean[kinds.length], atElbow = new boolean[kinds.length];
        // How many of each triangle's corners are the forearm's, and the upper arm's.
        int[] ofFore = new int[count / 3], ofUpper = new int[count / 3];
        for (int corner = 0; corner + 2 < count; corner += 3) {
            int hands = 0, fores = 0, uppers = 0;
            for (int of = 0; of < 3; of++) {
                int kind = kinds[elements[corner + of]];
                hands += kind == hand ? 1 : 0;
                fores += kind == fore ? 1 : 0;
                uppers += kind == upper ? 1 : 0;
            }
            for (int of = 0; of < 3; of++) {
                atWrist[elements[corner + of]] |= hands > 0 && fores > 0;
                atElbow[elements[corner + of]] |= fores > 0 && uppers > 0;
            }
            ofFore[corner / 3] = fores;
            ofUpper[corner / 3] = uppers;
        }
        float[] wrist = middle(places, atWrist), elbow = middle(places, atElbow);
        if (wrist != null) {
            limb.wrist = new float[] {wrist[0], wrist[1], wrist[2]};
        }
        boolean[] ofHand = new boolean[kinds.length];
        for (int vertex = 0; vertex < kinds.length; vertex++) {
            ofHand[vertex] = kinds[vertex] == hand;
        }
        float[] palm = middle(places, ofHand);
        if (palm != null) {
            limb.palm = new float[] {palm[0], palm[1], palm[2]};
        }
        if (wrist != null && elbow != null) {
            section(places, atWrist, wrist, elbow, limb);
        }
        if (elbow != null) {
            limb.elbow = new float[] {elbow[0], elbow[1], elbow[2]};
            limb.elbowWide = elbow[3];
        }
        limb.foreUv = worn(places, uvs, elements, ofFore, wrist != null && elbow != null
                ? new float[] {(wrist[0] + elbow[0]) / 2f, (wrist[1] + elbow[1]) / 2f, (wrist[2] + elbow[2]) / 2f} : null);
        limb.upperUv = worn(places, uvs, elements, ofUpper, null);
        return limb;
    }

    /**
     * The shape of a wrist as it is seen along the forearm. The line across it is the one its
     * corners are spread furthest along, and the oval is the least of that line's proportions
     * that has every corner inside it: a wrist of four corners is an oval round the four.
     */
    private static void section(float[] places, boolean[] atWrist, float[] wrist, float[] elbow, Limb limb) {
        float ax = elbow[0] - wrist[0], ay = elbow[1] - wrist[1], az = elbow[2] - wrist[2];
        float length = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        ax /= length;
        ay /= length;
        az /= length;
        // Two lines square to the forearm and to each other, the first from whichever axis the forearm is least along.
        float sx = Math.abs(ax) <= Math.abs(ay) && Math.abs(ax) <= Math.abs(az) ? 1f : 0f, sy = sx == 0f && Math.abs(ay) <= Math.abs(az) ? 1f : 0f;
        float sz = 1f - sx - sy;
        float along = sx * ax + sy * ay + sz * az;
        float ux = sx - ax * along, uy = sy - ay * along, uz = sz - az * along;
        length = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= length;
        uy /= length;
        uz /= length;
        float vx = ay * uz - az * uy, vy = az * ux - ax * uz, vz = ax * uy - ay * ux;
        float uu = 0f, uv = 0f, vv = 0f;
        for (int vertex = 0; vertex < atWrist.length; vertex++) {
            if (atWrist[vertex]) {
                float x = places[vertex * 3] - wrist[0], y = places[vertex * 3 + 1] - wrist[1], z = places[vertex * 3 + 2] - wrist[2];
                float u = x * ux + y * uy + z * uz, v = x * vx + y * vy + z * vz;
                uu += u * u;
                uv += u * v;
                vv += v * v;
            }
        }
        double spreadAlong = 0.5 * Math.atan2(2 * uv, uu - vv);
        float cos = (float) Math.cos(spreadAlong), sin = (float) Math.sin(spreadAlong);
        float[] across = {ux * cos + vx * sin, uy * cos + vy * sin, uz * cos + vz * sin};
        float tx = vx * cos - ux * sin, ty = vy * cos - uy * sin, tz = vz * cos - uz * sin;
        float widest = 0f, deepest = 0f;
        for (int vertex = 0; vertex < atWrist.length; vertex++) {
            if (atWrist[vertex]) {
                float x = places[vertex * 3] - wrist[0], y = places[vertex * 3 + 1] - wrist[1], z = places[vertex * 3 + 2] - wrist[2];
                widest = Math.max(widest, Math.abs(x * across[0] + y * across[1] + z * across[2]));
                deepest = Math.max(deepest, Math.abs(x * tx + y * ty + z * tz));
            }
        }
        // A wrist that is a flat card has no shape to measure: its hand is not one an arm is drawn to.
        if (!(widest > 0f && deepest > widest * FLAT)) {
            return;
        }
        float outside = 0f;
        for (int vertex = 0; vertex < atWrist.length; vertex++) {
            if (atWrist[vertex]) {
                float x = places[vertex * 3] - wrist[0], y = places[vertex * 3 + 1] - wrist[1], z = places[vertex * 3 + 2] - wrist[2];
                float u = (x * across[0] + y * across[1] + z * across[2]) / widest, v = (x * tx + y * ty + z * tz) / deepest;
                outside = Math.max(outside, (float) Math.sqrt(u * u + v * v));
            }
        }
        limb.across = across;
        limb.wristAcross = widest * outside;
        limb.wristThrough = deepest * outside;
    }

    /** The mean of the marked vertices and their mean distance from it, or null with none marked. */
    private static float[] middle(float[] places, boolean[] marked) {
        float[] middle = new float[4];
        int of = 0;
        for (int vertex = 0; vertex < marked.length; vertex++) {
            if (marked[vertex]) {
                middle[0] += places[vertex * 3];
                middle[1] += places[vertex * 3 + 1];
                middle[2] += places[vertex * 3 + 2];
                of++;
            }
        }
        if (of == 0) {
            return null;
        }
        middle[0] /= of;
        middle[1] /= of;
        middle[2] /= of;
        for (int vertex = 0; vertex < marked.length; vertex++) {
            if (marked[vertex]) {
                float x = places[vertex * 3] - middle[0], y = places[vertex * 3 + 1] - middle[1], z = places[vertex * 3 + 2] - middle[2];
                middle[3] += (float) Math.sqrt(x * x + y * y + z * z) / of;
            }
        }
        return middle;
    }

    /**
     * The texture places at the middles of the SAMPLES triangles nearest a point, among those
     * with the most corners on one length of an arm: all three where the mesh has such
     * triangles, which a mesh of few faces may not. Null if no triangle has a corner there.
     *
     * @param corners how many of each triangle's corners are on that length
     * @param near    the point, or null for the middle of all such triangles
     */
    private static float[] worn(float[] places, float[] uvs, int[] elements, int[] corners, float[] near) {
        int most = 0;
        for (int has : corners) {
            most = Math.max(most, has);
        }
        if (most == 0) {
            return null;
        }
        float[] middles = new float[corners.length * 3];
        float[] mean = new float[3];
        int of = 0;
        for (int triangle = 0; triangle < corners.length; triangle++) {
            if (corners[triangle] != most) {
                continue;
            }
            for (int axis = 0; axis < 3; axis++) {
                middles[triangle * 3 + axis] = (places[elements[triangle * 3] * 3 + axis] + places[elements[triangle * 3 + 1] * 3 + axis]
                        + places[elements[triangle * 3 + 2] * 3 + axis]) / 3f;
                mean[axis] += middles[triangle * 3 + axis];
            }
            of++;
        }
        float[] to = near != null ? near : new float[] {mean[0] / of, mean[1] / of, mean[2] / of};
        float[] found = new float[SAMPLES * 2];
        boolean[] taken = new boolean[corners.length];
        int last = -1;
        for (int sample = 0; sample < SAMPLES; sample++) {
            int nearest = -1;
            float least = Float.MAX_VALUE;
            for (int triangle = 0; triangle < corners.length; triangle++) {
                if (corners[triangle] != most || taken[triangle]) {
                    continue;
                }
                float x = middles[triangle * 3] - to[0], y = middles[triangle * 3 + 1] - to[1], z = middles[triangle * 3 + 2] - to[2];
                float away = x * x + y * y + z * z;
                if (away < least) {
                    least = away;
                    nearest = triangle;
                }
            }
            // A length of fewer triangles than samples is read at its last one again.
            if (nearest < 0) {
                nearest = last;
            }
            taken[nearest] = true;
            last = nearest;
            for (int axis = 0; axis < 2; axis++) {
                found[sample * 2 + axis] = (uvs[elements[nearest * 3] * 2 + axis] + uvs[elements[nearest * 3 + 1] * 2 + axis]
                        + uvs[elements[nearest * 3 + 2] * 2 + axis]) / 3f;
            }
        }
        return found;
    }
}
