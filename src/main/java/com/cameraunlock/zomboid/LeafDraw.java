package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;

/**
 * The falling leaves' vertices on the graphics card, made again each frame from the frame's
 * records (LeafFall.FLOATS) into one buffer kept for it: a quad a leaf, four corners, each
 * the leaf's middle and how much shows, the way out to the corner, where the corner is on the
 * leaf with the leaf's colour and turn, and the way its face looks. Render thread.
 */
final class LeafDraw {
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_STREAM_DRAW = 0x88E0, GL_FLOAT = 0x1406, GL_QUADS = 0x0007;
    private static final int GL_VERTEX_ARRAY = 0x8074, GL_NORMAL_ARRAY = 0x8075, GL_TEXTURE_COORD_ARRAY = 0x8078, GL_TEXTURE0 = 0x84C0;
    private static final int FLOATS_A_CORNER = 14, STRIDE = FLOATS_A_CORNER * Float.BYTES;
    private static final float[] CORNER_U = {0f, 1f, 1f, 0f}, CORNER_V = {0f, 0f, 1f, 1f};

    private final FloatBuffer corners = ByteBuffer.allocateDirect(LeafFall.MOST * 4 * STRIDE).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private int buffer;

    /** Puts the leaves of the frame on the card and points the vertex, normal and first two texture coordinate arrays at them, switched on. */
    void point(float[] leaves, int count) {
        corners.clear();
        for (int leaf = 0; leaf < count; leaf++) {
            int at = leaf * LeafFall.FLOATS;
            float alongX = leaves[at + LeafFall.ALONG_X], alongY = leaves[at + LeafFall.ALONG_Y], alongUp = leaves[at + LeafFall.ALONG_UP];
            float acrossX = leaves[at + LeafFall.ACROSS_X], acrossY = leaves[at + LeafFall.ACROSS_Y], acrossUp = leaves[at + LeafFall.ACROSS_UP];
            float faceX = alongY * acrossUp - alongUp * acrossY, faceY = alongUp * acrossX - alongX * acrossUp, faceUp = alongX * acrossY - alongY * acrossX;
            for (int corner = 0; corner < 4; corner++) {
                float u = CORNER_U[corner], v = CORNER_V[corner];
                float a = 2f * u - 1f, b = 2f * v - 1f;
                corners.put(leaves[at + LeafFall.X]).put(leaves[at + LeafFall.Y]).put(leaves[at + LeafFall.UP]).put(leaves[at + LeafFall.SHOWN]);
                corners.put(acrossX * a + alongX * b).put(acrossY * a + alongY * b).put(acrossUp * a + alongUp * b);
                corners.put(u).put(v).put(leaves[at + LeafFall.HUE]).put(leaves[at + LeafFall.TURNED]);
                corners.put(faceX).put(faceY).put(faceUp);
            }
        }
        corners.flip();
        if (buffer == 0) {
            buffer = GL15.glGenBuffers();
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, buffer);
        // Orphaned and filled again: the card need not wait for last frame's draw to finish with it.
        GL15.glBufferData(GL_ARRAY_BUFFER, (long) corners.capacity() * Float.BYTES, GL_STREAM_DRAW);
        GL15.glBufferSubData(GL_ARRAY_BUFFER, 0L, corners);
        GL11.glEnableClientState(GL_VERTEX_ARRAY);
        GL11.glVertexPointer(4, GL_FLOAT, STRIDE, 0L);
        GL11.glEnableClientState(GL_NORMAL_ARRAY);
        GL11.glNormalPointer(GL_FLOAT, STRIDE, 4L * Float.BYTES);
        GL13.glClientActiveTexture(GL_TEXTURE0);
        GL11.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glTexCoordPointer(4, GL_FLOAT, STRIDE, 7L * Float.BYTES);
        GL13.glClientActiveTexture(GL_TEXTURE0 + 1);
        GL11.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glTexCoordPointer(3, GL_FLOAT, STRIDE, 11L * Float.BYTES);
        GL13.glClientActiveTexture(GL_TEXTURE0);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    void draw(int count) {
        GL11.glDrawArrays(GL_QUADS, 0, count * 4);
    }

    /** Lets go of the buffer, with the rest of the pictures. */
    void letGo() {
        if (buffer != 0) {
            GL15.glDeleteBuffers(buffer);
            buffer = 0;
        }
    }
}
