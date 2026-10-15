package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL44;
import org.lwjgl.opengl.GLCapabilities;

/**
 * Pictures put on the graphics card from a buffer the card reads them out of, written by the
 * render thread through a mapping kept for good. Handed a picture in the PC's memory instead,
 * the driver copied it there and then, and with its own thread to keep up with, waited for that
 * thread to have done everything handed it so far: 14 to 26 ms for a rug's 2 MB in the middle
 * of a frame. Render thread only.
 *
 * The buffer is a ring. What a frame writes into it is fenced at the frame's end, and a stretch
 * is written again only once the card has passed the fence of what was there: a picture that
 * would have to wait for it is not taken that frame, and its caller asks again the next.
 */
final class Uploads {
    private static final int GL_PIXEL_UNPACK_BUFFER = 0x88EC, GL_SYNC_GPU_COMMANDS_COMPLETE = 0x9117;
    private static final int GL_ALREADY_SIGNALED = 0x911A, GL_CONDITION_SATISFIED = 0x911C;
    private static final int GL_MAP_WRITE_BIT = 0x0002, GL_MAP_PERSISTENT_BIT = 0x0040, GL_MAP_COHERENT_BIT = 0x0080;
    /** Bigger than the most any one frame puts on the card: two sharp rugs and eight as read. */
    static final int SIZE = 32 << 20;
    private static final int ALIGN = 256;

    private record Fenced(long start, long end, long fence) {}

    private static boolean asked, ringed;
    private static int buffer;
    private static ByteBuffer mapped;
    private static long head;
    /** Where this frame's writes began, while it has written any. */
    private static long frameStart = -1;
    private static final ArrayDeque<Fenced> fenced = new ArrayDeque<>();

    private Uploads() {
    }

    /** Whether pictures go through the ring: OpenGL 4.4 or ARB_buffer_storage. Asked once, and said in the log. */
    static boolean ringed() {
        if (!asked) {
            asked = true;
            GLCapabilities card = GL.getCapabilities();
            ringed = card.OpenGL44 || card.GL_ARB_buffer_storage;
            if (ringed) {
                buffer = GL15.glGenBuffers();
                GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, buffer);
                int flags = GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT;
                GL44.glBufferStorage(GL_PIXEL_UNPACK_BUFFER, SIZE, flags);
                mapped = GL30.glMapBufferRange(GL_PIXEL_UNPACK_BUFFER, 0, SIZE, flags);
                GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                if (mapped == null) {
                    throw new IllegalStateException("the graphics card would not map the " + (SIZE >> 20) + " MB buffer pictures are put on it from");
                }
            }
            Log.info(ringed ? "pictures go on the graphics card through a " + (SIZE >> 20) + " MB ring it reads them from (OpenGL 4.4 or ARB_buffer_storage)"
                    : "this graphics card has neither OpenGL 4.4 nor ARB_buffer_storage: pictures go on it straight from the PC's memory, which can hold a frame up");
        }
        return ringed;
    }

    /**
     * Room for bytes in the ring this frame, as the offset they go at, or -1 when the card is
     * still reading what is there. Write them into {@link #at} and hand the offset to the
     * upload with the ring bound ({@link #bind}).
     */
    static long take(int bytes) {
        int size = bytes + ALIGN - 1 & -ALIGN;
        if (size > SIZE) {
            throw new IllegalArgumentException("a picture of " + bytes + " bytes is larger than the ring of " + SIZE);
        }
        long start = head;
        if (start % SIZE + size > SIZE) {
            start += SIZE - start % SIZE;
        }
        long end = start + size;
        if (frameStart >= 0 && end - SIZE > frameStart || !passed(end - SIZE)) {
            return -1;
        }
        if (frameStart < 0) {
            frameStart = head;
        }
        head = end;
        return start;
    }

    /** The ring's memory from offset on: write a picture taken with take there. */
    static ByteBuffer at(long offset, int bytes) {
        int from = (int) (offset % SIZE);
        return mapped.slice(from, bytes);
    }

    /** The ring's place in the buffer the card reads from, for an offset take gave. */
    static long place(long offset) {
        return offset % SIZE;
    }

    static void bind() {
        GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, buffer);
    }

    static void unbind() {
        GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    }

    /** Fences what this frame wrote, once its uploads have been handed to the card. */
    static void endFrame() {
        if (frameStart < 0) {
            return;
        }
        fenced.add(new Fenced(frameStart, head, GL32.glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0)));
        frameStart = -1;
    }

    /** Whether the card has read everything written in the ring before upTo. */
    private static boolean passed(long upTo) {
        while (!fenced.isEmpty() && fenced.peek().start() < upTo) {
            Fenced oldest = fenced.peek();
            int waited = GL32.glClientWaitSync(oldest.fence(), 0, 0L);
            if (waited != GL_ALREADY_SIGNALED && waited != GL_CONDITION_SATISFIED) {
                return false;
            }
            GL32.glDeleteSync(oldest.fence());
            fenced.poll();
        }
        return true;
    }
}
