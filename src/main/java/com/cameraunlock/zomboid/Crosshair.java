package com.cameraunlock.zomboid;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * A crosshair for a weapon held at the hip. The game draws its own aiming reticle only while
 * the aim is held, and the first person view moves that one to where the shot lands. With a
 * weapon in hand and the aim not held the game draws nothing, and the middle of the picture
 * is not marked: this is the mark for that time, drawn at the same point, where the shot
 * would land.
 *
 * The logic thread says where it is and the render thread draws it over the finished picture.
 */
final class Crosshair {

    /** In pixels on a screen 1080 high: a tick's length and width, and how far from the middle it starts. */
    private static final int LENGTH = 7, WIDTH = 2, GAP = 5;
    /** How much further out the ticks stand at the most spread, moving and just after a shot. */
    private static final float SPREAD = 12f;
    private static final int DESIGN_HEIGHT = 1080;

    private static final String FRAGMENT = """
            #version 120
            uniform vec4 uColor;

            void main() {
                gl_FragColor = uColor;
            }
            """;

    private static final int GL_BLEND = 0x0BE2, GL_SRC_ALPHA = 0x0302, GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_DEPTH_TEST = 0x0B71, GL_CULL_FACE = 0x0B44, GL_TRIANGLE_STRIP = 0x0005;

    private static final SmallProgram flat = new SmallProgram("crosshair", SmallProgram.FLAT_VERTEX, FRAGMENT);

    /** Where it is, -1 to 1 across the picture and up it, and how spread, or null when it is not shown. */
    private volatile float[] shown;

    void show(float x, float y, float spread) {
        shown = new float[] {x, y, spread};
    }

    void hide() {
        shown = null;
    }

    /** Draws it into the picture bound, whose viewport is the whole of a picture this many pixels each way. */
    void draw(int width, int height) {
        float[] where = shown;
        if (where == null || !flat.use()) {
            return;
        }
        int unit = Math.max(1, Math.round((float) height / DESIGN_HEIGHT));
        int middleX = Math.round((where[0] * 0.5f + 0.5f) * width), middleY = Math.round((where[1] * 0.5f + 0.5f) * height);
        int from = Math.round((GAP + SPREAD * where[2]) * unit), to = from + LENGTH * unit, half = WIDTH * unit / 2;
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glEnable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        int color = flat.at("uColor");
        // Dark under light, a pixel wider all round: it reads against a pale wall and a dark one.
        for (int layer = 0; layer < 2; layer++) {
            int edge = layer == 0 ? unit : 0;
            if (layer == 0) {
                GL20.glUniform4f(color, 0f, 0f, 0f, 0.55f);
            } else {
                GL20.glUniform4f(color, 1f, 1f, 1f, 0.9f);
            }
            box(middleX + from - edge, middleY - half - edge, middleX + to + edge, middleY - half + WIDTH * unit + edge, width, height);
            box(middleX - to - edge, middleY - half - edge, middleX - from + edge, middleY - half + WIDTH * unit + edge, width, height);
            box(middleX - half - edge, middleY + from - edge, middleX - half + WIDTH * unit + edge, middleY + to + edge, width, height);
            box(middleX - half - edge, middleY - to - edge, middleX - half + WIDTH * unit + edge, middleY - from + edge, width, height);
            box(middleX - half - edge, middleY - half - edge, middleX - half + WIDTH * unit + edge, middleY - half + WIDTH * unit + edge, width, height);
        }
        GL11.glDisable(GL_BLEND);
    }

    /** A box between whole pixels, so its edges are sharp. */
    private static void box(int left, int bottom, int right, int top, int width, int height) {
        float x0 = left * 2f / width - 1f, x1 = right * 2f / width - 1f;
        float y0 = bottom * 2f / height - 1f, y1 = top * 2f / height - 1f;
        GL11.glBegin(GL_TRIANGLE_STRIP);
        GL11.glVertex2f(x0, y0);
        GL11.glVertex2f(x1, y0);
        GL11.glVertex2f(x0, y1);
        GL11.glVertex2f(x1, y1);
        GL11.glEnd();
    }
}
