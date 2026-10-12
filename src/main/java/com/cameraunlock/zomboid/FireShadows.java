package com.cameraunlock.zomboid;

/**
 * Which of a picture's firelights throw shadows, and when their shadow maps are drawn again.
 * Each shadowed fire has a slot in one depth texture: six square faces, three across and two
 * down, the world as seen from the fire along +x, +y, -x, -y, up and down (ViewCamera.clean at
 * yaw 0, 90, 180, 270 and pitch up and down), slots one above another. The world's program
 * looks a point up in the face its direction from the fire falls in (FIRE_SHADOW_GLSL), so the
 * fire's light stops at walls and furniture as the sun's does and needs no other gate.
 *
 * What a face holds changes only with the world near the fire, not with the flames' flicker:
 * a slot is drawn again when its fire has moved or it is the oldest, a couple a frame.
 *
 * Render thread only.
 */
final class FireShadows {
    /** The most slots, the most fires that throw shadows, and how many faces a slot has. */
    static final int MOST = 4, FACES = 6;
    /** How many slots are drawn again a frame at most, and after how many frames a slot is drawn again anyway: a door opened by a fire. */
    static final int DRAWN_A_FRAME = 2, STALE_AFTER = 8;
    /** The near plane of a face, in tiles, and how far past a fire's reach its faces see. */
    static final float NEAR = 0.05f, PAST_REACH = 0.5f;
    /** A fire nearer than this to where a slot's maps were drawn from, in tiles, is the same fire. */
    private static final float SAME = 0.05f;

    /** How many slots there are, and how many texels a face is across, for the picture's mode: sharper shadows, more and finer. */
    static int slots() {
        return Quality.sharpShadows() ? MOST : 2;
    }

    static int faceSize() {
        return Quality.sharpShadows() ? 512 : 256;
    }

    /** Where each slot's maps were drawn from (x, y, up), how far they see, and the frame they were drawn in; drawnIn 0 is never. */
    final float[] from = new float[MOST * 4];
    final int[] drawnIn = new int[MOST];
    /** For each of the picture's lights, its slot, or -1 for a light that throws no shadow. */
    final float[] slotOf = new float[Fires.LIGHTS];
    /** The slots to draw this frame, toDraw of them. */
    final int[] draw = new int[MOST];
    int toDraw;

    /**
     * Gives the strongest of the lights (Fires.lights' out, reach and weight) slots, keeping each
     * fire in the slot it had, and says which slots are to be drawn this frame. frame counts up
     * from 1.
     */
    void assign(float[] lights, float[] reach, float[] weight, int frame, int slots) {
        java.util.Arrays.fill(slotOf, -1f);
        java.util.Arrays.fill(kept, false);
        int strong = strongest(lights, weight, slots);
        // First the fires that still have their slot.
        for (int k = 0; k < strong; k++) {
            int light = strongest[k];
            for (int slot = 0; slot < slots; slot++) {
                if (!kept[slot] && drawnIn[slot] != 0 && near(slot, lights, light)) {
                    kept[slot] = true;
                    slotOf[light] = slot;
                    break;
                }
            }
        }
        // Then new ones, into slots no kept fire holds.
        for (int k = 0; k < strong; k++) {
            int light = strongest[k];
            if (slotOf[light] >= 0f) {
                continue;
            }
            for (int slot = 0; slot < slots; slot++) {
                if (!kept[slot]) {
                    kept[slot] = true;
                    slotOf[light] = slot;
                    from[slot * 4] = lights[light * 4];
                    from[slot * 4 + 1] = lights[light * 4 + 1];
                    from[slot * 4 + 2] = lights[light * 4 + 2];
                    from[slot * 4 + 3] = reach[light] + PAST_REACH;
                    drawnIn[slot] = 0;
                    break;
                }
            }
        }
        // A slot not yet drawn is drawn now; then the oldest, if stale, up to DRAWN_A_FRAME. A light whose slot is not drawn yet throws no shadow until it is.
        toDraw = 0;
        for (int slot = 0; slot < slots && toDraw < DRAWN_A_FRAME; slot++) {
            if (kept[slot] && drawnIn[slot] == 0) {
                draw[toDraw++] = slot;
            }
        }
        while (toDraw < DRAWN_A_FRAME) {
            int oldest = -1;
            for (int slot = 0; slot < slots; slot++) {
                if (kept[slot] && drawnIn[slot] != 0 && frame - drawnIn[slot] >= STALE_AFTER && !listed(slot) && (oldest < 0 || drawnIn[slot] < drawnIn[oldest])) {
                    oldest = slot;
                }
            }
            if (oldest < 0) {
                break;
            }
            draw[toDraw++] = oldest;
        }
        for (int k = 0; k < toDraw; k++) {
            drawnIn[draw[k]] = frame;
        }
        for (int light = 0; light < Fires.LIGHTS; light++) {
            int slot = (int) slotOf[light];
            if (slot >= 0 && drawnIn[slot] == 0) {
                slotOf[light] = -1f;
            }
        }
    }

    private boolean listed(int slot) {
        for (int k = 0; k < toDraw; k++) {
            if (draw[k] == slot) {
                return true;
            }
        }
        return false;
    }

    private boolean near(int slot, float[] lights, int light) {
        float dx = from[slot * 4] - lights[light * 4], dy = from[slot * 4 + 1] - lights[light * 4 + 1], du = from[slot * 4 + 2] - lights[light * 4 + 2];
        return dx * dx + dy * dy + du * du < SAME * SAME;
    }

    private final boolean[] kept = new boolean[MOST], taken = new boolean[Fires.LIGHTS];
    private final int[] strongest = new int[MOST];

    /** Puts into strongest the up to `slots` lights of the most weight that give light at all, strongest first, and says how many. */
    private int strongest(float[] lights, float[] weight, int slots) {
        int lit = 0;
        for (int light = 0; light < Fires.LIGHTS; light++) {
            lit += lights[light * 4 + 3] > 0f ? 1 : 0;
        }
        int count = Math.min(lit, slots);
        java.util.Arrays.fill(taken, false);
        for (int k = 0; k < count; k++) {
            int best = -1;
            for (int light = 0; light < Fires.LIGHTS; light++) {
                if (!taken[light] && lights[light * 4 + 3] > 0f && (best < 0 || weight[light] > weight[best])) {
                    best = light;
                }
            }
            taken[best] = true;
            strongest[k] = best;
        }
        return count;
    }

    /**
     * The pose of a slot's face: from the fire, along +x, +y, -x, -y, up or down, into pose.
     * FIRE_SHADOW_GLSL picks the same axes.
     */
    static void face(int face, float x, float y, float up, ViewCamera.Pose pose) {
        double half = Math.PI / 2;
        if (face < 4) {
            ViewCamera.clean(pose, x, y, up, (float) (face * half), 0f);
        } else {
            ViewCamera.clean(pose, x, y, up, 0f, (float) (face == 4 ? half : -half));
        }
    }

    /**
     * For the world's program: how much of a fire's light reaches a point p facing n, from slot
     * of the texture the faces are in. The face is picked by the way p lies from the fire, its
     * axes are FireShadows.face's, and each face sits one texel in from its square's edges so
     * the four texels a sample blends never reach the next face.
     */
    static final String GLSL = """
            uniform sampler2DShadow uFireShadow;
            uniform vec4 uFireFrom[4];
            uniform float uFireSlot[8];
            // Texels a face is across, and how many slots the texture holds.
            uniform vec2 uFireFace;

            float fireShadow(float slot, vec3 p, vec3 n) {
                vec4 from = uFireFrom[int(slot)];
                vec3 d = p + n * 0.04 - from.xyz;
                vec3 a = abs(d);
                vec3 forward;
                vec3 right;
                vec3 up;
                float face;
                if (a.z >= a.x && a.z >= a.y) {
                    face = d.z > 0.0 ? 4.0 : 5.0;
                    forward = vec3(0.0, 0.0, sign(d.z));
                    right = vec3(0.0, 1.0, 0.0);
                    up = vec3(-sign(d.z), 0.0, 0.0);
                } else if (a.x >= a.y) {
                    face = d.x > 0.0 ? 0.0 : 2.0;
                    forward = vec3(sign(d.x), 0.0, 0.0);
                    right = vec3(0.0, sign(d.x), 0.0);
                    up = vec3(0.0, 0.0, 1.0);
                } else {
                    face = d.y > 0.0 ? 1.0 : 3.0;
                    forward = vec3(0.0, sign(d.y), 0.0);
                    right = vec3(-sign(d.y), 0.0, 0.0);
                    up = vec3(0.0, 0.0, 1.0);
                }
                float ahead = dot(d, forward);
                vec2 ndc = vec2(dot(d, right), dot(d, up)) / ahead;
                float far = from.w;
                float z = (far + 0.05) / (far - 0.05) - 2.0 * far * 0.05 / ((far - 0.05) * ahead);
                vec2 tile = vec2(mod(face, 3.0), floor(face / 3.0) + slot * 2.0);
                vec2 uv = (tile * uFireFace.x + 1.0 + (ndc * 0.5 + 0.5) * (uFireFace.x - 2.0)) / vec2(3.0 * uFireFace.x, 2.0 * uFireFace.y * uFireFace.x);
                return shadow2D(uFireShadow, vec3(uv, z * 0.5 + 0.5)).r;
            }

            // The fires' light at p facing n, shadowed where a fire has a slot; a fire with none
            // lights only as far as unlit, the gate the game's own light of the square gives.
            vec3 fireWorldLight(vec3 p, vec3 n, float unlit) {
                vec3 sum = vec3(0.0);
                for (int i = 0; i < 8; i++) {
                    vec4 at = uFireAt[i];
                    if (at.w <= 0.0) {
                        break;
                    }
                    vec3 to = at.xyz - p;
                    float far2 = dot(to, to);
                    float reach = uFireReach[i];
                    if (far2 >= reach * reach) {
                        continue;
                    }
                    vec3 dir = to * inversesqrt(max(far2, 0.0001));
                    float facing = 0.15 + 0.85 * max(dot(n, dir), 0.0);
                    float seen = uFireSlot[i] >= 0.0 ? fireShadow(uFireSlot[i], p, n) : unlit;
                    sum += vec3(at.w * facing * seen * fireFalloff(sqrt(far2), reach));
                }
                return sum * uFireColor;
            }
            """;
}
