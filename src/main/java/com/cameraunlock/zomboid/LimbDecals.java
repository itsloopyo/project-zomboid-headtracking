package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import zombie.core.textures.Texture;

/**
 * Pieces cut off zombies that have come to rest, each a picture of it seen from straight
 * above, laid on the ground as the game lays a body (WorldScene.addBody): once a piece lies
 * still it is drawn once into a square of one shared texture, and from then on it is part of
 * its chunk's build and no model of it is kept or drawn. The texture holds {@link #SLOTS}
 * pictures; the next piece laid takes the square of the one laid longest ago, which goes.
 *
 * Pictures are made on the render thread; the logic thread reads what lies where.
 */
final class LimbDecals {
    private LimbDecals() {}

    /** Texels along the side of one picture, and pictures along the side of the texture. */
    static final int SLOT = 128, ACROSS = 16, SIDE = SLOT * ACROSS, SLOTS = ACROSS * ACROSS;
    /** The smallest mipmap is a few texels a picture, so one picture's average takes in little of the next. */
    private static final int MOST_LEVEL = 4;

    /** A piece lying on the ground: the middle of its picture on the map, its level, half the picture's side in tiles, and which picture it is. */
    record Laid(float x, float y, int z, float half, int slot, long serial) {
        /** Where the picture's square starts in the texture, as a share of its side. */
        float u0() {
            return (float) (slot % ACROSS * SLOT) / SIDE;
        }

        float v0() {
            return (float) (slot / ACROSS * SLOT) / SIDE;
        }
    }

    private static final Object lock = new Object();
    private static final Laid[] bySlot = new Laid[SLOTS];
    private static final HashMap<Long, List<Laid>> bySquare = new HashMap<>();
    private static int next;
    private static long serials;
    private static volatile boolean any;
    private static volatile int atlas;
    private static int atlasTarget, scratch, scratchColor, scratchDepth;

    private static final int GL_TEXTURE_2D = 0x0DE1, GL_RGBA = 0x1908, GL_RGBA8 = 0x8058, GL_UNSIGNED_BYTE = 0x1401, GL_DEPTH_COMPONENT24 = 0x81A6, GL_DEPTH_COMPONENT = 0x1902, GL_FLOAT = 0x1406;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_LINEAR = 0x2601, GL_LINEAR_MIPMAP_LINEAR = 0x2703;
    private static final int GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803, GL_CLAMP_TO_EDGE = 0x812F, GL_TEXTURE_MAX_LEVEL = 0x813D;
    private static final int GL_FRAMEBUFFER = 0x8D40, GL_DRAW_FRAMEBUFFER = 0x8CA9, GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0, GL_DEPTH_ATTACHMENT = 0x8D00, GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6, GL_READ_FRAMEBUFFER_BINDING = 0x8CAA, GL_CURRENT_PROGRAM = 0x8B8D;
    private static final int GL_VERTEX_ARRAY_BINDING = 0x85B5, GL_ARRAY_BUFFER = 0x8892, GL_ELEMENT_ARRAY_BUFFER = 0x8893;
    private static final int GL_ARRAY_BUFFER_BINDING = 0x8894, GL_ELEMENT_ARRAY_BUFFER_BINDING = 0x8895;
    private static final int GL_ALL_ATTRIB_BITS = 0x000FFFFF, GL_COLOR_BUFFER_BIT = 0x4000, GL_DEPTH_BUFFER_BIT = 0x0100, GL_NEAREST = 0x2600;
    private static final int[] FIRST = {GL_COLOR_ATTACHMENT0};
    private static final int GL_SCISSOR_TEST = 0x0C11, GL_BLEND = 0x0BE2, GL_DEPTH_TEST = 0x0B71, GL_STENCIL_TEST = 0x0B90;

    /** The texture every picture is in, or 0 before the first is made. */
    static int texture() {
        return atlas;
    }

    /**
     * Makes the picture of a piece lying at (x, y) on level z, half its side across, and lays
     * it: {@code draw} draws the piece looking straight down on that square, x to the right of
     * the picture and y towards its foot, into a target already cleared. What else is bound and
     * set is put back as it was. Render thread.
     */
    static void lay(float x, float y, int z, float half, Runnable draw) {
        int drawTo = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFrom = GL11.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int program = GL11.glGetInteger(GL_CURRENT_PROGRAM);
        int arrays = GL11.glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int vertices = GL11.glGetInteger(GL_ARRAY_BUFFER_BINDING), elements = GL11.glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        int remembered = Texture.lastTextureID;
        GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
        int slot;
        try {
            if (atlas == 0) {
                make();
            }
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, scratch);
            GL20.glDrawBuffers(FIRST);
            GL11.glViewport(0, 0, SLOT, SLOT);
            GL11.glDisable(GL_SCISSOR_TEST);
            GL11.glDisable(GL_STENCIL_TEST);
            GL11.glDisable(GL_BLEND);
            GL11.glEnable(GL_DEPTH_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            draw.run();
            slot = take();
            int left = slot % ACROSS * SLOT, bottom = slot / ACROSS * SLOT;
            GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, scratch);
            GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, atlasTarget);
            GL30.glBlitFramebuffer(0, 0, SLOT, SLOT, left, bottom, left + SLOT, bottom + SLOT, GL_COLOR_BUFFER_BIT, GL_NEAREST);
            GL11.glBindTexture(GL_TEXTURE_2D, atlas);
            GL30.glGenerateMipmap(GL_TEXTURE_2D);
        } finally {
            // What was pushed has the draw buffers of the target that was bound, so it is bound again before they are popped onto it.
            GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawTo);
            GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, readFrom);
            GL11.glPopAttrib();
            GL30.glBindVertexArray(arrays);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, vertices);
            GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, elements);
            GL20.glUseProgram(program);
            Texture.lastTextureID = remembered;
        }
        place(slot, x, y, z, half);
    }

    private static void make() {
        atlas = 0;
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SIDE, SIDE, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, MOST_LEVEL);
        atlasTarget = target(texture, 0);
        // Cleared once, so no picture's mipmaps take in whatever the card's memory held beside it.
        GL11.glViewport(0, 0, SIDE, SIDE);
        GL11.glDisable(GL_SCISSOR_TEST);
        GL11.glColorMask(true, true, true, true);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL_COLOR_BUFFER_BIT);
        GL30.glGenerateMipmap(GL_TEXTURE_2D);

        scratchColor = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, scratchColor);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SLOT, SLOT, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        scratchDepth = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, scratchDepth);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, SLOT, SLOT, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        scratch = target(scratchColor, scratchDepth);
        atlas = texture;
        Log.info("pieces lying on the ground are pictured in one texture of " + SIDE + "x" + SIDE + ", " + SLOTS + " pictures of " + SLOT + "x" + SLOT
                + ", " + (4L * SIDE * SIDE * 4 / 3 >> 20) + " MB on the graphics card");
    }

    private static int target(int color, int depth) {
        int target = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
        if (depth != 0) {
            GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth, 0);
        }
        int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("the driver would not make the target pieces on the ground are pictured in (status 0x" + Integer.toHexString(status) + ")");
        }
        return target;
    }

    /** The picture the next piece is drawn into: the one laid longest ago goes from the ground. */
    static int take() {
        synchronized (lock) {
            int slot = next;
            next = (next + 1) % SLOTS;
            Laid old = bySlot[slot];
            if (old != null) {
                List<Laid> there = bySquare.get(key((int) Math.floor(old.x), (int) Math.floor(old.y), old.z));
                there.remove(old);
                if (there.isEmpty()) {
                    bySquare.remove(key((int) Math.floor(old.x), (int) Math.floor(old.y), old.z));
                }
                bySlot[slot] = null;
            }
            return slot;
        }
    }

    /** Lays the picture in a slot taken for it on the ground. */
    static void place(int slot, float x, float y, int z, float half) {
        synchronized (lock) {
            Laid laid = new Laid(x, y, z, half, slot, ++serials);
            bySlot[slot] = laid;
            bySquare.computeIfAbsent(key((int) Math.floor(x), (int) Math.floor(y), z), k -> new ArrayList<>()).add(laid);
            any = true;
        }
    }

    /** The pieces lying with their middle on a square. Logic thread. */
    static List<Laid> at(int x, int y, int z) {
        if (!any) {
            return List.of();
        }
        synchronized (lock) {
            List<Laid> there = bySquare.get(key(x, y, z));
            return there == null ? List.of() : List.copyOf(there);
        }
    }

    /** What lies on a square, as one number for a chunk's signature: 0 for nothing. */
    static long sumAt(int x, int y, int z) {
        if (!any) {
            return 0L;
        }
        synchronized (lock) {
            List<Laid> there = bySquare.get(key(x, y, z));
            if (there == null) {
                return 0L;
            }
            long sum = 0L;
            for (Laid laid : there) {
                sum = sum * 31 + laid.serial;
            }
            return sum;
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) x << 36) ^ ((long) (y & 0xFFFFFFF) << 8) ^ (z & 0xFF);
    }

    /**
     * Gives the graphics card back the pictures and the target they are made in, and forgets
     * everything lying: no build is left to draw them. Render thread.
     */
    static void letGo() {
        if (atlas == 0) {
            forget();
            return;
        }
        GL30.glDeleteFramebuffers(atlasTarget);
        GL30.glDeleteFramebuffers(scratch);
        GL11.glDeleteTextures(atlas);
        GL11.glDeleteTextures(scratchColor);
        GL11.glDeleteTextures(scratchDepth);
        atlas = atlasTarget = scratch = scratchColor = scratchDepth = 0;
        forget();
        Log.info("the pictures of pieces lying on the ground are given back to the graphics card");
    }

    /** Everything lying is forgotten: the save it lay in was left. Any thread. */
    static void forget() {
        synchronized (lock) {
            java.util.Arrays.fill(bySlot, null);
            bySquare.clear();
            next = 0;
            any = false;
        }
    }
}
