package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * With a gun's sights up, the picture's background beyond what the aim is on softens a little,
 * as an eye fixed on the sights and the target sees it, and the nearest parts of the gun a
 * little less: eased in and out with the sights (ViewModel.Held.aim), and focused at the aim's
 * distance (ViewModel.Held.nearness), pulled over FOCUS_SECONDS as the aim crosses from
 * something near to something far. A point's blur goes by one over its distance against one over
 * the focus's, as a lens's does: a target close by softens what is behind it far more than one
 * far off does.
 *
 * Never with a scope fitted: the scope's own picture fills its eyepiece, drawn at the gun's
 * distance, and the softening of what is that near would blur what the scope shows. Nothing in
 * Low, which draws the picture as the game's look, or with AimBlur off.
 *
 * Drawn after the frames are steadied (FrameBlending), so what is kept from frame to frame stays
 * sharp: a half-size gather of the picture, each place taking the taps whose own blur reaches it,
 * and a full-size pass that puts it in where the blur is more than half a dot. What the picture
 * shows from then on, its glow and its develop, is read from that. Nothing is drawn without the
 * sights up, and its two pictures are given back once none has been drawn for a while.
 *
 * Render thread.
 */
final class AimBlur {
    /** The blur's opening in tiles: a point's blur, as an angle, is this times how far one over its distance is from one over the focus's. */
    static final float APERTURE = 0.012f;
    /** The same for what is nearer than the focus: much less, so the sights stay readable and only what is nearest the eye softens. */
    static final float NEAR_APERTURE = 0.0004f;
    /** The most of the picture's height a blur reaches beyond the focus, and nearer than it. */
    static final float MOST_FAR = 0.006f, MOST_NEAR = 0.0015f;
    /** How long the focus takes to come most of the way to a new distance, in seconds. */
    static final float FOCUS_SECONDS = 0.15f;
    /** Blur of less than this many dots of the picture is not drawn. */
    static final float LEAST_DOTS = 0.5f;
    private static final int TAPS = 12;

    /**
     * How far what is ahead blurs beyond the focus, as a share of the picture's height: focus
     * is one over the focus's distance, ahead the distance of the point, both in tiles, spread
     * the blur's opening over the picture's height (spread) and most its cap. Nothing at or
     * nearer than the focus.
     */
    static float farBlur(float focus, float ahead, float spread, float most) {
        return Math.min(most, spread * Math.max(focus - 1f / ahead, 0f));
    }

    /** How far what is nearer than the focus blurs, as farBlur says it. */
    static float nearBlur(float focus, float ahead, float spread, float most) {
        return Math.min(most, spread * Math.max(1f / ahead - focus, 0f));
    }

    /** An opening as a share of the picture's height per one over a tile, for a picture tanHalf high each way, as much as amount of it shows. */
    static float spread(float opening, float tanHalf, float amount) {
        return amount * opening / (2f * tanHalf);
    }

    /**
     * How much of the blur shows: as far as the sights are up, with a gun in hand and no scope
     * on it, with the part on and the picture not Low's.
     */
    static float amount(boolean on, boolean firearm, boolean scoped, float aim) {
        return on && firearm && !scoped ? Math.clamp(aim, 0f, 1f) : 0f;
    }

    /** The focus, one over its distance, seconds on towards one over the aim's distance (0 with nothing in reach): frame-rate independent. */
    static float pulled(float focus, float toward, float seconds) {
        return toward + (focus - toward) * (float) Math.exp(-seconds / FOCUS_SECONDS);
    }

    private static final String VERTEX = PipelineShaders.SCREEN_VERTEX;

    /** farBlur and nearBlur as the shaders work them out: the tests set them against the Java. */
    static final String BLURS = """
            float farBlur(float focus, float ahead, float spread, float most) {
                return min(most, spread * max(focus - 1.0 / ahead, 0.0));
            }

            float nearBlur(float focus, float ahead, float spread, float most) {
                return min(most, spread * max(1.0 / ahead - focus, 0.0));
            }
            """;

    private static final String COMMON = "#version 120\n" + BLURS + """
            uniform sampler2D uScene;
            uniform sampler2D uDepth;
            uniform vec2 uFull;
            uniform float uNear;
            uniform float uFar;
            // One over the focus's distance, in tiles.
            uniform float uFocus;
            // The blur beyond the focus, its spread and its cap, and nearer than it the same, as shares of the picture's height.
            uniform vec4 uSpread;

            float aheadAt(vec2 at) {
                float z = texture2D(uDepth, at).r;
                return 2.0 * uNear * uFar / (uFar + uNear - (z * 2.0 - 1.0) * (uFar - uNear));
            }

            // How many of the picture's dots a point this far off blurs over.
            float dotsOf(float ahead) {
                return max(farBlur(uFocus, ahead, uSpread.x, uSpread.y), nearBlur(uFocus, ahead, uSpread.z, uSpread.w)) * uFull.y;
            }
            """;

    /**
     * Half size. Each place takes the taps round it, out to its own blur, that blur as far as
     * they are from it: what is in focus, the target, is not smeared over the background
     * round it, and what is nearer than the focus, the gun, takes only from what is as near.
     */
    private static final String GATHER = COMMON + "const int TAPS = " + TAPS + ";\n" + """
            uniform vec2 uSize;

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                float ahead = aheadAt(uv);
                float reach = dotsOf(ahead);
                float focusAhead = 1.0 / max(uFocus, 1e-6);
                bool nearer = ahead < focusAhead;
                vec4 sum = vec4(texture2D(uScene, uv).rgb, 1.0);
                if (reach > LEAST_DOTS) {
                    for (int i = 0; i < TAPS; i++) {
                        // Taps spread evenly over the disc, each turned by the golden angle from the last.
                        float turn = float(i) * 2.39996323;
                        vec2 off = vec2(cos(turn), sin(turn)) * (sqrt((float(i) + 0.5) / float(TAPS)) * reach);
                        vec2 at = uv + off / uFull;
                        float tapAhead = aheadAt(at);
                        float weight = clamp(dotsOf(tapAhead) - length(off) + 1.0, 0.0, 1.0);
                        if (nearer && tapAhead >= focusAhead) {
                            weight = 0.0;
                        }
                        sum += vec4(texture2D(uScene, at).rgb, 1.0) * weight;
                    }
                }
                gl_FragColor = vec4(sum.rgb / sum.a, 1.0);
            }
            """.replace("LEAST_DOTS", Float.toString(LEAST_DOTS));

    /** Full size: the gathered picture where the blur is more than half a dot, the picture's own elsewhere, its alpha kept. */
    private static final String SHOWN = COMMON + """
            uniform sampler2D uGathered;

            void main() {
                vec2 uv = gl_FragCoord.xy / uFull;
                vec4 sharp = texture2D(uScene, uv);
                vec3 soft = texture2D(uGathered, uv).rgb;
                gl_FragColor = vec4(mix(sharp.rgb, soft, smoothstep(LEAST_DOTS, LEAST_DOTS + 1.0, dotsOf(aheadAt(uv)))), sharp.a);
            }
            """.replace("LEAST_DOTS", Float.toString(LEAST_DOTS));

    private static Program gather, shown;
    private static boolean refused;

    /**
     * Links the two programs, as a piece of the core. Always null: a refusal is said once, and
     * the picture is drawn without the blur in every mode.
     */
    static String link() {
        if (gather != null || refused) {
            return null;
        }
        StringBuilder problem = new StringBuilder();
        gather = Program.link("aim blur gather", VERTEX, GATHER, problem);
        shown = Program.link("aim blur shown", VERTEX, SHOWN, problem);
        if (gather == null || shown == null) {
            refused = true;
            gather = null;
            shown = null;
            Log.info("the background is not blurred while aiming on this machine: " + problem);
        }
        return null;
    }

    private static final int GL_TEXTURE_2D = 0x0DE1, GL_RGBA = 0x1908, GL_RGBA16F = 0x881A, GL_FLOAT = 0x1406;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_LINEAR = 0x2601;
    private static final int GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803, GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_FRAMEBUFFER = 0x8D40, GL_COLOR_ATTACHMENT0 = 0x8CE0, GL_FRAMEBUFFER_COMPLETE = 0x8CD5, GL_TEXTURE0 = 0x84C0;

    private int gathered, gatheredTarget, result, resultTarget, wide, high;
    private long usedNanos;
    /** One over the focus's distance as it has been pulled, and when it last was; NaN before the first frame drawn with the sights up. */
    private float focus = Float.NaN;
    private long pulledAt;

    /**
     * Draws the blur over the picture if the sights are up, and returns the picture the passes
     * after read: color itself when nothing is blurred.
     *
     * @param color   the picture as the passes after the steadying read it, wide by high
     * @param depth   its depth, measured between near and far
     * @param tanHalf the tangent of half the picture's height's angle
     */
    int draw(View view, int color, int depth, int wide, int high, float tanHalf, float near, float far, long now) {
        ViewModel.Held held = view.viewModel.held;
        boolean on = gather != null && Part.AIM_BLUR.on() && !Quality.plain(view.index);
        float amount = held == null ? 0f : amount(on, held.firearm(), view.scope.fitted() != null, held.aim());
        if (amount <= 0f) {
            focus = Float.NaN;
            return color;
        }
        float seconds = Float.isNaN(focus) ? 0f : Math.min((now - pulledAt) / 1e9f, 0.25f);
        focus = Float.isNaN(focus) ? held.nearness() : pulled(focus, held.nearness(), seconds);
        pulledAt = now;
        usedNanos = now;
        if (wide != this.wide || high != this.high || result == 0) {
            make(wide, high);
        }
        int halfWide = Math.max(1, wide / 2), halfHigh = Math.max(1, high / 2);
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, gatheredTarget);
        GL11.glViewport(0, 0, halfWide, halfHigh);
        gather.use();
        tell(gather, wide, high, tanHalf, near, far, amount);
        gather.set("uSize", (float) halfWide, (float) halfHigh);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, depth);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, color);
        Pipeline.quad();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, resultTarget);
        GL11.glViewport(0, 0, wide, high);
        shown.use();
        tell(shown, wide, high, tanHalf, near, far, amount);
        shown.set("uGathered", 2);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, gathered);
        GL13.glActiveTexture(GL_TEXTURE0);
        Pipeline.quad();
        return result;
    }

    private void tell(Program program, int wide, int high, float tanHalf, float near, float far, float amount) {
        program.set("uScene", 0);
        program.set("uDepth", 1);
        program.set("uFull", (float) wide, (float) high);
        program.set("uNear", near);
        program.set("uFar", far);
        program.set("uFocus", focus);
        program.set("uSpread", spread(APERTURE, tanHalf, amount), amount * MOST_FAR, spread(NEAR_APERTURE, tanHalf, amount), amount * MOST_NEAR);
    }

    private void make(int newWide, int newHigh) {
        takeBack(Long.MAX_VALUE);
        wide = newWide;
        high = newHigh;
        gathered = texture(Math.max(1, wide / 2), Math.max(1, high / 2));
        gatheredTarget = target(gathered, "the half-size picture blurred while aiming");
        result = texture(wide, high);
        resultTarget = target(result, "the picture blurred while aiming");
    }

    private static int texture(int wide, int high) {
        int made = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, made);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, wide, high, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL_TEXTURE_2D, 0);
        return made;
    }

    private static int target(int texture, String what) {
        int made = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, made);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            GL30.glDeleteFramebuffers(made);
            throw new IllegalStateException("the driver would not make " + what + " (status 0x" + Integer.toHexString(status) + ")");
        }
        return made;
    }

    /** Gives the two pictures back if nothing has been blurred since before. */
    void takeBack(long before) {
        // By the first texture: a making the driver refused part way has that one and not the rest.
        if (gathered != 0 && usedNanos < before) {
            GL30.glDeleteFramebuffers(gatheredTarget);
            GL30.glDeleteFramebuffers(resultTarget);
            GL11.glDeleteTextures(gathered);
            GL11.glDeleteTextures(result);
            gathered = gatheredTarget = result = resultTarget = 0;
            wide = high = 0;
        }
    }

    /** What the two pictures take on the graphics card. */
    long bytes() {
        return result == 0 ? 0L : 8L * ((long) wide * high + (long) Math.max(1, wide / 2) * Math.max(1, high / 2));
    }
}
