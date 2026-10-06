package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * The picture's colour with a depth that belongs to what the mod itself draws in the
 * viewer's hands: a rifle of its own, a scope. The hands and the game's weapons have a
 * depth already, but it starts at the plane they are cut off at, and a scope's eyepiece or
 * the back of a receiver is nearer the eye than that: measured in it, every part of them is
 * as near as every other, and whichever triangle is drawn last shows. What is drawn here is
 * sorted against itself, and each of its shaders leaves out what the hands' depth says is
 * behind a hand.
 *
 * Render thread only.
 */
final class HeldDepth {
    private HeldDepth() {}

    private static final int GL_TEXTURE_2D = 0x0DE1, GL_FLOAT = 0x1406, GL_DEPTH_COMPONENT = 0x1902, GL_DEPTH_COMPONENT24 = 0x81A6;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_NEAREST = 0x2600;
    private static final int GL_FRAMEBUFFER = 0x8D40, GL_COLOR_ATTACHMENT0 = 0x8CE0, GL_DEPTH_ATTACHMENT = 0x8D00, GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_DEPTH_BUFFER_BIT = 0x0100;

    private static int target, color, depth, wide, high;

    /** The picture's colour was given back, and the target is to take the next one. */
    static void forgetTarget() {
        color = 0;
    }

    /**
     * Makes the target ready for a frame's held things, its depth cleared.
     *
     * @param sceneColor the picture's colour, sceneWide by sceneHigh
     */
    static void begin(int sceneColor, int sceneWide, int sceneHigh) {
        if (sceneColor != color || sceneWide != wide || sceneHigh != high) {
            if (target == 0) {
                target = GL30.glGenFramebuffers();
            }
            if (depth != 0) {
                GL11.glDeleteTextures(depth);
            }
            depth = GL11.glGenTextures();
            GL11.glBindTexture(GL_TEXTURE_2D, depth);
            GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, sceneWide, sceneHigh, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
            GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sceneColor, 0);
            GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth, 0);
            int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("the driver would not make the target for what is held (status 0x" + Integer.toHexString(status) + ")");
            }
            color = sceneColor;
            wide = sceneWide;
            high = sceneHigh;
        }
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL11.glDepthMask(true);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL_DEPTH_BUFFER_BIT);
    }

    /** Draws into the target from here on. */
    static void bind() {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
    }
}
