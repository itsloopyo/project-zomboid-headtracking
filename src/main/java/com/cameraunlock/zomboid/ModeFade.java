package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.Locale;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import zombie.core.SpriteRenderer;
import zombie.core.opengl.RenderThread;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureDraw;

/**
 * One view's picture from before a change of graphics mode, laid over the pictures after it
 * and faded out, so the change is seen as one picture turning into the other. The world as the
 * frame before the change finished it is copied out, before anything the game puts over the
 * world; the frames after draw the copy over their own world, less of it each frame. While the
 * new mode's view is still being made the copy is held whole, for at most MOST_HELD.
 *
 * The logic thread asks for the copy and for each frame of the fade; the render thread takes,
 * draws and gives back.
 */
final class ModeFade {
    /** How long the picture before takes to fade out. */
    static final float SECONDS = 0.6f;
    /** The most of a frame's length the fade moves by: the frame a mode is first drawn in is often many times as long as the rest. */
    private static final float MOST_A_FRAME = 0.05f;
    /** The longest the picture before is held whole while the new mode's view is made, after which it fades all the same. */
    static final float MOST_HELD = 1.5f;

    private static final String FRAGMENT = """
            #version 120
            uniform sampler2D uBefore;
            uniform vec4 uViewport;
            uniform float uShown;

            void main() {
                vec2 at = (gl_FragCoord.xy - uViewport.xy) / uViewport.zw;
                gl_FragColor = vec4(texture2D(uBefore, at).rgb, uShown);
            }
            """;
    private static final SmallProgram program = new SmallProgram("fade between graphics modes", SmallProgram.FLAT_VERTEX, FRAGMENT);

    private static final int GL_TEXTURE_2D = 0x0DE1, GL_RGBA = 0x1908, GL_RGBA8 = 0x8058, GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_LINEAR = 0x2601;
    private static final int GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803, GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_FRAMEBUFFER = 0x8D40, GL_DRAW_FRAMEBUFFER = 0x8CA9, GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0, GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6, GL_READ_FRAMEBUFFER_BINDING = 0x8CAA, GL_CURRENT_PROGRAM = 0x8B8D;
    private static final int GL_VERTEX_ARRAY_BINDING = 0x85B5, GL_ARRAY_BUFFER = 0x8892, GL_ARRAY_BUFFER_BINDING = 0x8894;
    private static final int GL_ACTIVE_TEXTURE = 0x84E0, GL_TEXTURE0 = 0x84C0;
    private static final int GL_ALL_ATTRIB_BITS = 0x000FFFFF, GL_COLOR_BUFFER_BIT = 0x4000, GL_NEAREST = 0x2600, GL_VIEWPORT = 0x0BA2;
    private static final int GL_SCISSOR_TEST = 0x0C11, GL_BLEND = 0x0BE2, GL_DEPTH_TEST = 0x0B71, GL_STENCIL_TEST = 0x0B90;
    private static final int GL_CULL_FACE = 0x0B44, GL_ALPHA_TEST = 0x0BC0, GL_SRC_ALPHA = 0x0302, GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_TRIANGLE_STRIP = 0x0005;

    private final int index;
    /** Copies asked for, and the last asked for that was then given back unfaded. Logic thread. */
    private int asked, givenBack;
    /** The last copy whose fade has ended or been given up. */
    private volatile int ended;
    /** Whether the view the logic thread last built is still waiting for what the new mode draws with. */
    private volatile boolean waiting;

    // Render thread.
    private int taken, texture, target, wide, high;
    private float faded, held;
    private long lastNanos, takenNanos;
    private final int[] viewport = new int[4];

    ModeFade(int index) {
        this.index = index;
    }

    /** Asks for the picture as it is now, at this point in the frame, to be faded out over the frames that follow. Logic thread. */
    void take() {
        int number = ++asked;
        SpriteRenderer.instance.drawGeneric(new TextureDraw.GenericDrawer() {
            @Override
            public void render() {
                took(number);
            }
        });
    }

    /**
     * Asks for the picture before, where a fade is running, to be drawn over this frame's. Logic thread.
     *
     * @param stillMaking the new mode's view is not yet drawn as that mode draws it
     */
    void frame(boolean stillMaking) {
        if (asked == ended) {
            return;
        }
        waiting = stillMaking;
        SpriteRenderer.instance.drawGeneric(DRAW);
    }

    /**
     * Gives back the picture before, faded out or not, by the render thread's own queue: no
     * frame of this view's comes again to draw it. Logic thread.
     */
    void giveBack(String why) {
        if (asked == ended || asked == givenBack) {
            return;
        }
        int upTo = givenBack = asked;
        RenderThread.queueInvokeOnRenderContext(() -> giveBack(upTo, why));
    }

    private final TextureDraw.GenericDrawer DRAW = new TextureDraw.GenericDrawer() {
        @Override
        public void render() {
            draw();
        }
    };

    private void took(int number) {
        if (number <= ended) {
            // Given back before it was taken: the save it was asked for in was left.
            return;
        }
        GL11.glGetIntegerv(GL_VIEWPORT, viewport);
        if (viewport[2] <= 0 || viewport[3] <= 0) {
            giveBack(number, "there was no picture to take");
            return;
        }
        int drawTo = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFrom = GL11.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int remembered = Texture.lastTextureID;
        GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
        try {
            if (texture == 0 || wide != viewport[2] || high != viewport[3]) {
                letGo();
                String refused = make(viewport[2], viewport[3]);
                if (refused != null) {
                    giveBack(number, refused);
                    return;
                }
            }
            GL11.glDisable(GL_SCISSOR_TEST);
            GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, drawTo);
            GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target);
            GL30.glBlitFramebuffer(viewport[0], viewport[1], viewport[0] + wide, viewport[1] + high, 0, 0, wide, high, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        } finally {
            GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawTo);
            GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, readFrom);
            GL11.glPopAttrib();
            Texture.lastTextureID = remembered;
        }
        taken = number;
        faded = 0f;
        held = 0f;
        lastNanos = takenNanos = System.nanoTime();
        Log.info(who() + "the picture before the graphics mode changed is taken, " + wide + "x" + high + ", to fade out over " + SECONDS + " s");
    }

    private void draw() {
        if (texture == 0 || ended == taken) {
            return;
        }
        long now = System.nanoTime();
        float time = Math.min((now - lastNanos) / 1e9f, MOST_A_FRAME);
        lastNanos = now;
        if (waiting && held < MOST_HELD) {
            held += time;
        } else {
            faded += time / SECONDS;
        }
        if (faded >= 1f) {
            giveBack(taken, String.format(Locale.ROOT, "faded out %.2f s after it was taken, %.2f s of that held while the new mode was made",
                    (now - takenNanos) / 1e9f, held));
            return;
        }
        GL11.glGetIntegerv(GL_VIEWPORT, viewport);
        if (viewport[2] <= 0 || viewport[3] <= 0) {
            return;
        }
        int previous = GL11.glGetInteger(GL_CURRENT_PROGRAM);
        int arrays = GL11.glGetInteger(GL_VERTEX_ARRAY_BINDING), vertices = GL11.glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int unit = GL11.glGetInteger(GL_ACTIVE_TEXTURE);
        int remembered = Texture.lastTextureID;
        GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
        try {
            if (!program.use()) {
                giveBack(taken, "its shader was refused");
                return;
            }
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
            GL11.glDisable(GL_DEPTH_TEST);
            GL11.glDisable(GL_STENCIL_TEST);
            GL11.glDisable(GL_CULL_FACE);
            GL11.glDisable(GL_ALPHA_TEST);
            GL11.glEnable(GL_BLEND);
            GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            // What the game keeps in the picture's alpha is its own.
            GL11.glColorMask(true, true, true, false);
            GL13.glActiveTexture(GL_TEXTURE0);
            GL11.glBindTexture(GL_TEXTURE_2D, texture);
            GL20.glUniform1i(program.at("uBefore"), 0);
            GL20.glUniform4f(program.at("uViewport"), viewport[0], viewport[1], viewport[2], viewport[3]);
            float s = faded;
            GL20.glUniform1f(program.at("uShown"), 1f - s * s * (3f - 2f * s));
            GL11.glBegin(GL_TRIANGLE_STRIP);
            GL11.glVertex2f(-1f, -1f);
            GL11.glVertex2f(1f, -1f);
            GL11.glVertex2f(-1f, 1f);
            GL11.glVertex2f(1f, 1f);
            GL11.glEnd();
        } finally {
            GL11.glPopAttrib();
            GL13.glActiveTexture(unit);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, vertices);
            GL30.glBindVertexArray(arrays);
            Program.useOther(previous);
            Texture.lastTextureID = remembered;
        }
    }

    /** Null once the copy's texture and target are made, else why the driver would not make them. */
    private String make(int width, int height) {
        texture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        target = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        wide = width;
        high = height;
        return status == GL_FRAMEBUFFER_COMPLETE ? null : "the driver would not make the target it is copied into (status 0x" + Integer.toHexString(status) + ")";
    }

    private void letGo() {
        if (texture == 0) {
            return;
        }
        GL30.glDeleteFramebuffers(target);
        GL11.glDeleteTextures(texture);
        texture = target = wide = high = 0;
    }

    private void giveBack(int upTo, String why) {
        letGo();
        ended = upTo;
        Log.info(who() + "the picture before the graphics mode changed is given back to the graphics card: " + why);
    }

    private String who() {
        return index == 0 ? "" : "player " + (index + 1) + ": ";
    }
}
