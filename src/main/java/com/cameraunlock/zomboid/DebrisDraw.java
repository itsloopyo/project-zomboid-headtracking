package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

/**
 * The falling pieces' vertices on the graphics card (Debris), made again each frame from the
 * frame's records into one buffer kept for it: a box a piece, six faces of four corners, each
 * corner where it is in the world, the way its face looks and the piece's colour. One draw for
 * every piece. Render thread.
 */
final class DebrisDraw {
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_STREAM_DRAW = 0x88E0, GL_FLOAT = 0x1406, GL_QUADS = 0x0007;
    private static final int GL_VERTEX_ARRAY = 0x8074, GL_NORMAL_ARRAY = 0x8075, GL_COLOR_ARRAY = 0x8076;
    static final int FLOATS_A_CORNER = 9, CORNERS = 24;
    private static final int STRIDE = FLOATS_A_CORNER * Float.BYTES;
    /** Each face's corners, as signs along the face's two other axes, in order round it. */
    private static final float[] ROUND_U = {-1f, 1f, 1f, -1f}, ROUND_V = {-1f, -1f, 1f, 1f};

    private final float[] filled = new float[Debris.MOST * CORNERS * FLOATS_A_CORNER];
    private final FloatBuffer corners = ByteBuffer.allocateDirect(Debris.MOST * CORNERS * STRIDE).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private int buffer;

    /** Puts the pieces of the frame on the card and points the vertex, normal and colour arrays at them, switched on. */
    void point(float[] pieces, int count) {
        corners.clear();
        corners.put(filled, 0, fill(pieces, count, filled)).flip();
        if (buffer == 0) {
            buffer = GL15.glGenBuffers();
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, buffer);
        // Orphaned at the one size it always has and filled with the frame's pieces, as the leaves' is (LeafDraw).
        GL15.glBufferData(GL_ARRAY_BUFFER, (long) corners.capacity() * Float.BYTES, GL_STREAM_DRAW);
        GL15.glBufferSubData(GL_ARRAY_BUFFER, 0L, corners);
        GL11.glEnableClientState(GL_VERTEX_ARRAY);
        GL11.glVertexPointer(3, GL_FLOAT, STRIDE, 0L);
        GL11.glEnableClientState(GL_NORMAL_ARRAY);
        GL11.glNormalPointer(GL_FLOAT, STRIDE, 3L * Float.BYTES);
        GL11.glEnableClientState(GL_COLOR_ARRAY);
        GL11.glColorPointer(3, GL_FLOAT, STRIDE, 6L * Float.BYTES);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /** Writes count pieces' corners, FLOATS_A_CORNER floats each, CORNERS a piece, into into from its start, and gives how many floats that is. */
    static int fill(float[] pieces, int count, float[] into) {
        int to = 0;
        for (int piece = 0; piece < count; piece++) {
            int at = piece * Debris.FLOATS;
            float cx = pieces[at + Debris.X], cy = pieces[at + Debris.Y], cz = pieces[at + Debris.UP];
            for (int axis = 0; axis < 3; axis++) {
                int out = at + Debris.A + 3 * axis, u = at + Debris.A + 3 * ((axis + 1) % 3), v = at + Debris.A + 3 * ((axis + 2) % 3);
                float length = (float) Math.sqrt(pieces[out] * pieces[out] + pieces[out + 1] * pieces[out + 1] + pieces[out + 2] * pieces[out + 2]);
                for (float side = -1f; side <= 1f; side += 2f) {
                    for (int corner = 0; corner < 4; corner++) {
                        float a = ROUND_U[corner], b = ROUND_V[corner];
                        for (int j = 0; j < 3; j++) {
                            into[to + j] = (j == 0 ? cx : j == 1 ? cy : cz) + side * pieces[out + j] + a * pieces[u + j] + b * pieces[v + j];
                            into[to + 3 + j] = side * pieces[out + j] / length;
                        }
                        into[to + 6] = pieces[at + Debris.RED];
                        into[to + 7] = pieces[at + Debris.GREEN];
                        into[to + 8] = pieces[at + Debris.BLUE];
                        to += FLOATS_A_CORNER;
                    }
                }
            }
        }
        return to;
    }

    void draw(int count) {
        GL11.glDrawArrays(GL_QUADS, 0, count * CORNERS);
    }

    /** Lets go of the buffer, with the rest of the pictures. */
    void letGo() {
        if (buffer != 0) {
            GL15.glDeleteBuffers(buffer);
            buffer = 0;
        }
    }
}
