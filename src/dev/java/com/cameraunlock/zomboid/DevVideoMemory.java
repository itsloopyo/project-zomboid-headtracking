package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

/**
 * Dev builds only: a big graphics card made to look and act like a small one, to prove the mod
 * on cards it cannot be run on here. cameraunlock.zomboid.videoMemoryMb is the size VideoMemory
 * believes the card is. cameraunlock.zomboid.ballastMb is how much of the card is filled with
 * textures of nothing before the mod makes anything, and kept full: a few are cleared every frame,
 * so the driver cannot move them off the card for being unused and the mod's own really meet a
 * full card. cameraunlock.zomboid.refuseEverySeconds asks the driver for a buffer of a terabyte that often
 * while the picture is drawn, on cameraunlock.zomboid.refuseFrames frames in a row (1 if unset), which
 * it refuses as out of memory, and leaves the error for the mod to find. cameraunlock.zomboid.refuseWorldBuffers
 * asks for a terabyte in place of the first that many world buffers, in place of the size the world wants.
 * cameraunlock.zomboid.videoMemoryStep starts the graphics modes at that step, whatever is free.
 */
final class DevVideoMemory {
    private DevVideoMemory() {}

    private static final int PRETEND_MB = Integer.getInteger("cameraunlock.zomboid.videoMemoryMb", 0);
    private static final int BALLAST_MB = Integer.getInteger("cameraunlock.zomboid.ballastMb", 0);
    /** A texture of the ballast: 8192 square of four bytes, 256 MB. */
    private static final int SIDE = 8192, PIECE_MB = SIDE * SIDE * 4 >> 20;
    private static final int TOUCHED_A_FRAME = 4;
    private static final long REFUSE_EVERY_NANOS = Integer.getInteger("cameraunlock.zomboid.refuseEverySeconds", 0) * 1_000_000_000L;
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_ARRAY_BUFFER_BINDING = 0x8894, GL_STATIC_DRAW = 0x88E4;
    private static final int REFUSE_FRAMES = Integer.getInteger("cameraunlock.zomboid.refuseFrames", 1);
    private static final int START_STEP = Integer.getInteger("cameraunlock.zomboid.videoMemoryStep", -1);
    private static int worldRefusals = Integer.getInteger("cameraunlock.zomboid.refuseWorldBuffers", 0);
    private static long refusedAt;
    private static int refusing;

    private static final int GL_TEXTURE_2D = 0x0DE1, GL_RGBA8 = 0x8058, GL_RGBA = 0x1908, GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_FRAMEBUFFER = 0x8D40, GL_COLOR_ATTACHMENT0 = 0x8CE0, GL_COLOR_BUFFER_BIT = 0x4000, GL_SCISSOR_TEST = 0x0C11;
    private static final int GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6, GL_FRAMEBUFFER_COMPLETE = 0x8CD5, GL_TEXTURE_BINDING_2D = 0x8069;

    private static final ArrayList<Integer> targets = new ArrayList<>();
    private static int next;

    static int pretendMb() {
        return PRETEND_MB;
    }

    static int startStep() {
        return START_STEP;
    }

    /** What the world's buffer is asked for at, in bytes: a terabyte for the first refuseWorldBuffers. */
    static long worldBufferBytes(long bytes) {
        if (worldRefusals <= 0) {
            return bytes;
        }
        worldRefusals--;
        Log.info("video memory: cameraunlock.zomboid.refuseWorldBuffers asks for the world's buffer of " + (bytes >> 20) + " MB as a terabyte");
        return 1L << 40;
    }

    /** Render thread, once: fills BALLAST_MB of the card, and says how much it filled. */
    static int ballast() {
        if (BALLAST_MB <= 0) {
            return 0;
        }
        int bound = GL11.glGetInteger(GL_TEXTURE_BINDING_2D), drawing = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int filled = 0;
        while (filled < BALLAST_MB) {
            int texture = GL11.glGenTextures();
            GL11.glBindTexture(GL_TEXTURE_2D, texture);
            GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SIDE, SIDE, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            int error = GL11.glGetError();
            if (error != 0) {
                GL11.glDeleteTextures(texture);
                Log.info("video memory ballast: the driver refused a texture after " + filled + " MB (OpenGL error 0x" + Integer.toHexString(error) + ")");
                break;
            }
            int target = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
            GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
            int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("video memory ballast: the target of a ballast texture is not complete (0x" + Integer.toHexString(status) + ")");
            }
            targets.add(target);
            clear(target);
            filled += PIECE_MB;
        }
        GL11.glFinish();
        GL11.glBindTexture(GL_TEXTURE_2D, bound);
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, drawing);
        Log.info("video memory ballast: " + filled + " MB held on the card in " + targets.size() + " textures of " + SIDE + " square");
        return filled;
    }

    /** Render thread, once a frame: clears a few of the ballast's textures, so the driver keeps every one of them on the card. */
    static void touch() {
        long now = System.nanoTime();
        if (REFUSE_EVERY_NANOS > 0 && now - refusedAt > REFUSE_EVERY_NANOS) {
            if (refusedAt != 0L) {
                refusing = REFUSE_FRAMES;
            }
            refusedAt = now;
        }
        if (refusing > 0) {
            refusing--;
            int bound = GL11.glGetInteger(GL_ARRAY_BUFFER_BINDING);
            int buffer = GL15.glGenBuffers();
            GL15.glBindBuffer(GL_ARRAY_BUFFER, buffer);
            GL15.glBufferData(GL_ARRAY_BUFFER, 1L << 40, GL_STATIC_DRAW);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, bound);
            GL15.glDeleteBuffers(buffer);
            Log.info("video memory: cameraunlock.zomboid.refuseEverySeconds asked the driver for a buffer of a terabyte");
        }
        if (targets.isEmpty()) {
            return;
        }
        int drawing = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        for (int i = 0; i < TOUCHED_A_FRAME; i++) {
            next = (next + 1) % targets.size();
            clear(targets.get(next));
        }
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, drawing);
    }

    private static void clear(int target) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL11.glDisable(GL_SCISSOR_TEST);
        GL11.glColorMask(true, true, true, true);
        GL11.glClearColor(0.25f, 0.5f, 0.75f, 1f);
        GL11.glClear(GL_COLOR_BUFFER_BIT);
    }
}
