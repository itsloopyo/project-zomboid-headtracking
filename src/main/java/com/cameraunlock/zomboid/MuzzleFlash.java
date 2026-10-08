package com.cameraunlock.zomboid;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

/**
 * The flash of a shot: a brief bright burst at the muzzle of the weapon the viewer holds, and
 * for as long a light there for the picture to light the world and the figures with.
 *
 * The game's logic thread says when a shot is taken. The render thread knows where the muzzle
 * is, having just drawn the weapon, and draws the burst.
 */
final class MuzzleFlash {

    /** How long a flash lasts. */
    static final float SECONDS = 0.05f;
    /** How bright the light is at its start, beside the sun at 2.1. */
    private static final float LIGHT = 14f;
    /** Burning powder: a yellow white. */
    private static final float RED = 1f, GREEN = 0.78f, BLUE = 0.45f;
    /** How bright the burst itself is at its start: enough to bloom. */
    private static final float BURST = 9f;
    /** Half the width of the burst, in tiles. */
    private static final float RADIUS = 0.17f;
    /** The light is a little ahead of the muzzle, so the weapon's own end is lit from in front. */
    private static final float LIGHT_AHEAD = 0.1f;

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

    private static final String BURST_FRAGMENT = """
            #version 120
            uniform vec2 uAt;
            uniform float uRadius;
            uniform vec3 uColor;
            uniform float uTurn;

            void main() {
                vec2 d = (gl_FragCoord.xy - uAt) / uRadius;
                float r = length(d);
                if (r >= 1.0) {
                    discard;
                }
                float core = exp(-r * r * 9.0);
                float streaks = pow(abs(sin(atan(d.y, d.x) * 2.5 + uTurn)), 12.0) * (1.0 - r) * exp(-r * 1.5);
                float glow = (1.0 - r) * (1.0 - r) * 0.25;
                gl_FragColor = vec4(uColor * (core * 2.2 + streaks * 1.4 + glow), 1.0);
            }
            """;

    private static final int GL_BLEND = 0x0BE2, GL_ONE = 1, GL_ZERO = 0, GL_SRC_ALPHA = 0x0302, GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_TRIANGLE_STRIP = 0x0005;

    private static final SmallProgram burst = new SmallProgram("muzzle flash", SmallProgram.FLAT_VERTEX, BURST_FRAGMENT);

    private volatile long firedAt;
    private volatile int shots;
    /** Where the light is in the world (x, y, up), once a weapon with a muzzle has been drawn. Render thread. */
    private final float[] at = new float[3];
    private boolean placed;

    /** A shot has been taken. Logic thread. */
    void fire() {
        firedAt = System.nanoTime();
        shots++;
    }

    /** How much of a flash is left this long after its shot: 1 at the shot, 0 from SECONDS on. */
    float left(float seconds) {
        return seconds < 0f || seconds >= SECONDS ? 0f : 1f - seconds / SECONDS;
    }

    private float left() {
        return shots == 0 ? 0f : left((System.nanoTime() - firedAt) / 1e9f);
    }

    /**
     * The light of the flash as the picture's shaders take it, into out: x, y, up and strength
     * for uFlashAt, then red, green and blue for uFlashColor. The strength is 0 with no flash.
     */
    void light(float[] out) {
        out[0] = placed ? at[0] : 0f;
        out[1] = placed ? at[1] : 0f;
        out[2] = placed ? at[2] : 0f;
        out[3] = placed ? left() * LIGHT : 0f;
        out[4] = RED;
        out[5] = GREEN;
        out[6] = BLUE;
    }

    /** The weapon drawn this frame has no muzzle: nothing flashes. */
    void none() {
        placed = false;
    }

    /**
     * Draws the burst over the picture bound, which holds light, and notes where its light
     * is for the frame's shaders. The muzzle is given in the view's axes: x to the right, y up
     * and z towards the viewer.
     */
    void draw(ViewCamera.Pose camera, float x, float y, float z, float tanHalfFieldOfView, float aspect, int width, int height) {
        float ahead = -z + LIGHT_AHEAD;
        at[0] = camera.eyeX + camera.rightX * x + camera.upX * y + camera.forwardX * ahead;
        at[1] = camera.eyeY + camera.rightY * x + camera.upY * y + camera.forwardY * ahead;
        at[2] = camera.eyeUp + camera.rightUp * x + camera.upUp * y + camera.forwardUp * ahead;
        placed = true;
        float left = left();
        if (left <= 0f || z >= 0f || !burst.use()) {
            return;
        }
        float middleX = x / -z / (tanHalfFieldOfView * aspect), middleY = y / -z / tanHalfFieldOfView;
        float halfY = RADIUS / -z / tanHalfFieldOfView, halfX = halfY / aspect;
        GL20.glUniform2f(burst.at("uAt"), (middleX * 0.5f + 0.5f) * width, (middleY * 0.5f + 0.5f) * height);
        GL20.glUniform1f(burst.at("uRadius"), halfY * 0.5f * height);
        GL20.glUniform3f(burst.at("uColor"), RED * BURST * left, GREEN * BURST * left, BLUE * BURST * left);
        // Each shot's streaks stand at their own angle.
        GL20.glUniform1f(burst.at("uTurn"), shots * 2.399f);
        GL11.glEnable(GL_BLEND);
        // The picture's alpha is left alone: it says what mirrors.
        GL14.glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ZERO, GL_ONE);
        GL11.glBegin(GL_TRIANGLE_STRIP);
        GL11.glVertex2f(middleX - halfX, middleY - halfY);
        GL11.glVertex2f(middleX + halfX, middleY - halfY);
        GL11.glVertex2f(middleX - halfX, middleY + halfY);
        GL11.glVertex2f(middleX + halfX, middleY + halfY);
        GL11.glEnd();
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }
}
