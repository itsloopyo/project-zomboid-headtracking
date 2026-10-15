package com.cameraunlock.zomboid;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;

/**
 * Whether a surface whose picture costs a pass of its own (water, a mirror) may show in the
 * eye's picture past what stands in front of it. Each frame the caller draws stand-ins for the
 * surface, a little larger than it, against the depth of the eye's own world between begin and
 * end; the graphics card's answer is taken up a frame or two later without waiting for it. So
 * that a surface coming out from behind what hid it is never shown without its picture, it
 * counts as showing on the frame it comes into view, until KEPT_FRAMES after a query last saw
 * it, and whenever it reaches the edge of the eye's picture, where a turn brings it in before
 * any query could see it. Render thread, one for each kind of surface of a view.
 */
final class SightQuery {
    private static final int GL_ANY_SAMPLES_PASSED = 0x8C2F, GL_QUERY_RESULT = 0x8866, GL_QUERY_RESULT_AVAILABLE = 0x8867;
    private static final int GL_DEPTH_TEST = 0x0B71, GL_LEQUAL = 0x0203, GL_BLEND = 0x0BE2;
    private static final int KEPT_FRAMES = 30;
    private final int[] queries = new int[4];
    private final boolean[] asked = new boolean[4];
    private int at, seenFrame;
    private boolean wasInView, asking;

    /** Takes up the answers come in, and says whether the surface may show this frame. */
    boolean mayShow(boolean inView, boolean atEdge, int frame) {
        for (int i = 0; i < queries.length; i++) {
            if (asked[i] && GL15.glGetQueryObjecti(queries[i], GL_QUERY_RESULT_AVAILABLE) != 0) {
                asked[i] = false;
                if (GL15.glGetQueryObjecti(queries[i], GL_QUERY_RESULT) != 0) {
                    seenFrame = frame;
                }
            }
        }
        if (!wasInView) {
            seenFrame = frame;
        }
        wasInView = inView;
        return atEdge || frame - seenFrame <= KEPT_FRAMES;
    }

    /** Counts the surface as seen this frame without asking, for one no stand-in can be drawn for. */
    void seen(int frame) {
        seenFrame = frame;
    }

    /**
     * Begins a query, with what is drawn until end tested against the depth and written
     * nowhere: the caller has set the matrices and a program that writes no depth of its own.
     * False while every query is still out, and then nothing is to be drawn.
     */
    boolean begin() {
        if (asked[at]) {
            return false;
        }
        if (queries[at] == 0) {
            queries[at] = GL15.glGenQueries();
        }
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glDisable(GL_BLEND);
        GL11.glColorMask(false, false, false, false);
        GL15.glBeginQuery(GL_ANY_SAMPLES_PASSED, queries[at]);
        asking = true;
        return true;
    }

    void end() {
        if (!asking) {
            throw new IllegalStateException("a sight query ended that was not begun");
        }
        GL15.glEndQuery(GL_ANY_SAMPLES_PASSED);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
        asked[at] = true;
        at = (at + 1) % queries.length;
        asking = false;
    }
}
