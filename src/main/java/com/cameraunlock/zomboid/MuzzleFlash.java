package com.cameraunlock.zomboid;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

/**
 * The flash of a shot: a small, brief bloom of burning powder at the muzzle of the weapon the
 * viewer holds, opening out ahead of it along the barrel, and for as long a modest light there
 * for the picture to light the world and the figures with. The MuzzleFlash row of
 * CameraUnlock.ini switches both off.
 *
 * The game's logic thread says when a shot is taken. The render thread knows where the muzzle
 * is, having just placed the weapon, and draws the bloom.
 */
final class MuzzleFlash {

    /**
     * How long a flash lasts after the first frame that shows it. At 60 frames a second the
     * flash is whole in that frame, less than half in the next and gone in the one after.
     */
    static final float SECONDS = 0.03f;
    /** How bright the light is at its start, beside the sun at 2.1. */
    static final float LIGHT = 2f;
    /** Burning powder: a yellow white. */
    private static final float RED = 1f, GREEN = 0.78f, BLUE = 0.45f;
    /**
     * How bright the bloom is at its start. Light, in the picture's measure: the exposure
     * develops it, so the same flash comes out smaller in the open by day, when the eye lets in
     * less light, than at night, and blooms only where it is brighter than the picture shows.
     */
    private static final float BURST = 3f;
    /**
     * How far the flash reaches out ahead of the muzzle, and its half width where it is widest,
     * in tiles. The held weapon is drawn at about 0.74 tiles a metre (Characters.ARM_SCALE): a
     * tongue of fire about 12 cm long and 5 cm across, as burning powder leaves a rifle's or a
     * shotgun's muzzle.
     */
    private static final float LENGTH = 0.09f, WIDEST = 0.018f;
    /**
     * A long gun's muzzle is this far from its grip, in tiles, as the game's pump gun is held. A handgun's cartridge
     * burns a small part of the powder a long gun's does, and its flash is made smaller by how much nearer its grip
     * its muzzle is, down to SMALLEST of the long gun's.
     */
    private static final float LONG_GUN_REACH = 0.67f, SMALLEST = 0.4f;
    /** The faint glow round it reaches this many times its half width. */
    private static final float GLOW = 1.8f;
    /** The light is this far ahead of the muzzle along the barrel, in tiles, so the weapon's own end is lit from in front. */
    private static final float LIGHT_AHEAD = 0.05f;

    /** What plume gives: where the flash starts and ends in the view's axes, its widest half width, and how far its glow reaches. */
    static final int PLUME = 8;

    /**
     * For a shader the flash lights: the light's place in the world with its strength, and its
     * colour. A surface at p facing n is given flashLight(p, n) times its own colour.
     */
    static final String GLSL = """
            uniform vec4 uFlashAt;
            uniform vec3 uFlashColor;

            // What the flash of a shot gives a surface at p that faces n: a light at the
            // muzzle, thinning with the square of the distance.
            vec3 flashLight(vec3 p, vec3 n) {
                if (uFlashAt.w <= 0.0) {
                    return vec3(0.0);
                }
                vec3 to = uFlashAt.xyz - p;
                float far2 = dot(to, to);
                vec3 dir = to * inversesqrt(max(far2, 0.0001));
                return uFlashColor * (uFlashAt.w * (0.25 + 0.75 * max(dot(n, dir), 0.0)) / (1.0 + 1.6 * far2));
            }
            """;

    /**
     * The flash as a tongue of fire along the barrel, looked at along each pixel's ray: the
     * point of its axis nearest the ray says how bright it is there and how deep, so the weapon
     * and anything else nearer the eye than the flash is drawn over it. Its edge is ragged in
     * four petals turned to a new angle with each shot.
     */
    private static final String BURST_FRAGMENT = """
            #version 120
            uniform vec2 uSize;
            uniform vec2 uTan;
            uniform vec3 uFrom;
            uniform vec3 uAlong;
            uniform float uWidest;
            uniform float uGlow;
            uniform vec3 uColor;
            uniform float uTurn;
            uniform float uNear;
            uniform float uFar;

            void main() {
                vec2 picture = gl_FragCoord.xy / uSize * 2.0 - 1.0;
                vec3 ray = vec3(picture * uTan, -1.0);
                float rr = dot(ray, ray), ra = dot(ray, uAlong), aa = dot(uAlong, uAlong);
                float rf = dot(ray, uFrom), af = dot(uAlong, uFrom);
                float across = rr * aa - ra * ra;
                // Looked at straight down the barrel every point of the axis is as near: the muzzle's is taken.
                float t = across > 1e-12 ? clamp((ra * rf - rr * af) / across, 0.0, 1.0) : 0.0;
                vec3 axis = uFrom + uAlong * t;
                vec3 off = ray * (dot(ray, axis) / rr) - axis;
                vec3 side = normalize(abs(uAlong.y) < 0.9 * length(uAlong) ? cross(uAlong, vec3(0.0, 1.0, 0.0)) : cross(uAlong, vec3(1.0, 0.0, 0.0)));
                vec3 over = normalize(cross(side, uAlong));
                float petals = 1.0 + 0.6 * pow(abs(cos(2.0 * atan(dot(off, over), dot(off, side) + 1e-9) + uTurn)), 6.0) * (1.0 - t);
                float wide = uWidest * (0.35 + 0.65 * smoothstep(0.0, 0.4, t)) * (1.0 - 0.4 * t) * petals;
                float far2 = dot(off, off);
                float bright = (1.0 - t * t);
                float core = exp(-3.0 * far2 / (wide * wide));
                float glow = 0.12 * exp(-2.5 * far2 / (uGlow * uGlow));
                float light = bright * (core + glow);
                if (light < 0.002) {
                    discard;
                }
                float ahead = -axis.z;
                gl_FragDepth = clamp(0.5 + 0.5 * (uFar + uNear - 2.0 * uNear * uFar / ahead) / (uFar - uNear), 0.0, 1.0);
                gl_FragColor = vec4(uColor * light, 1.0);
            }
            """;

    private static final int GL_BLEND = 0x0BE2, GL_ONE = 1, GL_ZERO = 0, GL_SRC_ALPHA = 0x0302, GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_DEPTH_TEST = 0x0B71, GL_LEQUAL = 0x0203;
    private static final int GL_TRIANGLE_STRIP = 0x0005;

    private static final SmallProgram burst = new SmallProgram("muzzle flash", SmallProgram.FLAT_VERTEX, BURST_FRAGMENT);

    /** The MuzzleFlash row of CameraUnlock.ini. The thread that watches the file sets it, the render thread reads it. */
    private static volatile boolean on = true;

    /** Shots taken. Written by the logic thread. */
    private volatile int shots;
    /** The shots the picture has shown, and when the last was first shown. Render thread. */
    private int shown;
    private long shownAt;
    /** Where the light is in the world (x, y, up), once a weapon with a muzzle has been placed. Render thread. */
    private final float[] at = new float[3];
    private boolean placed;
    private final float[] plume = new float[PLUME], box = new float[4];

    static void set(boolean asked) {
        if (asked == on) {
            return;
        }
        on = asked;
        Log.info("muzzle flash: " + (asked ? "on" : "off"));
    }

    static boolean on() {
        return on;
    }

    /** A shot has been taken. Logic thread. */
    void fire() {
        shots++;
    }

    /** How much of a flash is left this long after the frame that first showed it: 1 then, 0 from SECONDS on. */
    static float left(float seconds) {
        return seconds < 0f || seconds >= SECONDS ? 0f : 1f - seconds / SECONDS;
    }

    /**
     * How strong the flash is in a frame drawn at now, 0 to 1. A shot is shown whole in the
     * first frame drawn after it however long that frame took to come, so none goes unseen at a
     * low frame rate, and it fades from there. Render thread.
     */
    float strength(long now) {
        if (!on) {
            return 0f;
        }
        int fired = shots;
        if (fired == 0) {
            return 0f;
        }
        if (fired != shown) {
            shown = fired;
            shownAt = now;
        }
        return left((now - shownAt) / 1e9f);
    }

    /**
     * Where the flash of a shot is, into out (PLUME floats): it starts at the muzzle and
     * reaches out ahead along the barrel, then its widest half width and the reach of its glow.
     * The muzzle and the barrel's unit direction are in the view's axes: x to the right, y up
     * and z towards the viewer. Reach is how far the muzzle is from the grip, in tiles.
     */
    static void plume(float[] muzzle, float[] pointing, float reach, float[] out) {
        float size = Math.clamp(reach / LONG_GUN_REACH, SMALLEST, 1f);
        for (int axis = 0; axis < 3; axis++) {
            out[axis] = muzzle[axis];
            out[3 + axis] = muzzle[axis] + pointing[axis] * LENGTH * size;
        }
        out[6] = WIDEST * size;
        out[7] = WIDEST * GLOW * size;
    }

    /**
     * The part of the picture a flash can light, in the picture's own measure (-1 to 1 each
     * way), into out as least x, least y, most x, most y. False when the flash is not wholly
     * before the eye, and nothing is drawn.
     */
    static boolean box(float[] plume, float tanHalfFieldOfView, float aspect, float[] out) {
        float nearer = Math.min(-plume[2], -plume[5]);
        if (nearer <= plume[7]) {
            return false;
        }
        float reachX = plume[7] / (nearer - plume[7]) / (tanHalfFieldOfView * aspect), reachY = plume[7] / (nearer - plume[7]) / tanHalfFieldOfView;
        out[0] = out[1] = Float.MAX_VALUE;
        out[2] = out[3] = -Float.MAX_VALUE;
        for (int end = 0; end < 6; end += 3) {
            float x = plume[end] / -plume[end + 2] / (tanHalfFieldOfView * aspect), y = plume[end + 1] / -plume[end + 2] / tanHalfFieldOfView;
            out[0] = Math.min(out[0], x - reachX);
            out[1] = Math.min(out[1], y - reachY);
            out[2] = Math.max(out[2], x + reachX);
            out[3] = Math.max(out[3], y + reachY);
        }
        return true;
    }

    /**
     * The light of the flash as the picture's shaders take it, into out: x, y, up and strength
     * for uFlashAt, then red, green and blue for uFlashColor. The strength is 0 with no flash.
     */
    void light(float[] out, long now) {
        float strength = placed ? strength(now) : 0f;
        out[0] = placed ? at[0] : 0f;
        out[1] = placed ? at[1] : 0f;
        out[2] = placed ? at[2] : 0f;
        out[3] = strength * LIGHT;
        out[4] = RED;
        out[5] = GREEN;
        out[6] = BLUE;
    }

    /** The weapon placed this frame has no muzzle: nothing flashes. */
    void none() {
        placed = false;
    }

    /**
     * Notes where the light of a flash is: just ahead of the muzzle along the barrel, both in
     * the view's axes, x to the right, y up and z towards the viewer.
     */
    void place(ViewCamera.Pose camera, float[] muzzle, float[] pointing) {
        float x = muzzle[0] + pointing[0] * LIGHT_AHEAD, y = muzzle[1] + pointing[1] * LIGHT_AHEAD, ahead = -(muzzle[2] + pointing[2] * LIGHT_AHEAD);
        at[0] = camera.eyeX + camera.rightX * x + camera.upX * y + camera.forwardX * ahead;
        at[1] = camera.eyeY + camera.rightY * x + camera.upY * y + camera.forwardY * ahead;
        at[2] = camera.eyeUp + camera.rightUp * x + camera.upUp * y + camera.forwardUp * ahead;
        placed = true;
    }

    /**
     * Draws the bloom into the picture bound, which holds light and the depth of what is in it
     * in the picture's own measure (near and far), behind whatever is nearer the eye.
     */
    void draw(float[] muzzle, float[] pointing, float reach, long now, float tanHalfFieldOfView, float aspect, int width, int height, float near, float far) {
        float strength = strength(now);
        if (strength <= 0f) {
            return;
        }
        plume(muzzle, pointing, reach, plume);
        if (!box(plume, tanHalfFieldOfView, aspect, box) || !burst.use()) {
            return;
        }
        GL20.glUniform2f(burst.at("uSize"), width, height);
        GL20.glUniform2f(burst.at("uTan"), tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        GL20.glUniform3f(burst.at("uFrom"), plume[0], plume[1], plume[2]);
        GL20.glUniform3f(burst.at("uAlong"), plume[3] - plume[0], plume[4] - plume[1], plume[5] - plume[2]);
        GL20.glUniform1f(burst.at("uWidest"), plume[6]);
        GL20.glUniform1f(burst.at("uGlow"), plume[7]);
        GL20.glUniform3f(burst.at("uColor"), RED * BURST * strength, GREEN * BURST * strength, BLUE * BURST * strength);
        // Each shot's petals stand at their own angle.
        GL20.glUniform1f(burst.at("uTurn"), shown * 2.399f);
        GL20.glUniform1f(burst.at("uNear"), near);
        GL20.glUniform1f(burst.at("uFar"), far);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glEnable(GL_BLEND);
        // The picture's alpha is left alone: it says what mirrors.
        GL14.glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ZERO, GL_ONE);
        GL11.glBegin(GL_TRIANGLE_STRIP);
        GL11.glVertex2f(box[0], box[1]);
        GL11.glVertex2f(box[2], box[1]);
        GL11.glVertex2f(box[0], box[3]);
        GL11.glVertex2f(box[2], box[3]);
        GL11.glEnd();
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_DEPTH_TEST);
    }
}
