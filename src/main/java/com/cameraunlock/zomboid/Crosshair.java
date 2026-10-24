package com.cameraunlock.zomboid;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * The mark of where the body faces while the game draws none. The game draws its own aiming
 * reticle only while the aim is held, and the first person view moves that one to where the
 * shot lands. Otherwise this marks the same point, which is also what the interact key acts
 * on: a crosshair with a weapon in hand, and a dot without one.
 *
 * The logic thread says where it is and the render thread draws it over the finished picture.
 */
final class Crosshair {

    /** In pixels on a screen 1080 high: a tick's length and width, and how far from the middle it starts. */
    private static final int LENGTH = 7, WIDTH = 2, GAP = 5;
    /** How much further out the ticks stand at the most spread, moving and just after a shot. */
    private static final float SPREAD = 12f;
    private static final int DESIGN_HEIGHT = 1080;
    /** The dot's radius in pixels on a screen 1080 high, and how much of what is behind it shows through. */
    private static final float DOT_RADIUS = 4f, DOT_WHITE = 0.45f, DOT_RIM = 0.3f;
    private static final int DOT_SIDES = 24;

    private static final String FRAGMENT = """
            #version 120
            uniform vec4 uColor;

            void main() {
                gl_FragColor = uColor;
            }
            """;

    private static final int GL_BLEND = 0x0BE2, GL_SRC_ALPHA = 0x0302, GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_DEPTH_TEST = 0x0B71, GL_CULL_FACE = 0x0B44, GL_TRIANGLE_STRIP = 0x0005, GL_TRIANGLE_FAN = 0x0006;

    static final SmallProgram flat = new SmallProgram("crosshair", SmallProgram.FLAT_VERTEX, FRAGMENT);

    /** Where it is, -1 to 1 across the picture and up it, how spread, and whether it is the dot alone. */
    private record Mark(float x, float y, float spread, boolean dot) {
    }

    /** The Crosshair row of CameraUnlock.ini. The thread that watches the file sets it, the render thread reads it. */
    private static volatile boolean on = true;

    /** Null when it is not shown. */
    private volatile Mark shown;

    static void set(boolean asked) {
        if (asked == on) {
            return;
        }
        on = asked;
        Log.info("the mod's own crosshair: " + (asked ? "on" : "off"));
    }

    static boolean on() {
        return on;
    }

    void show(float x, float y, float spread) {
        shown = new Mark(x, y, spread, false);
    }

    void showDot(float x, float y) {
        shown = new Mark(x, y, 0f, true);
    }

    void hide() {
        shown = null;
    }

    /** Draws it into the picture bound, whose viewport is the whole of a picture this many pixels each way. */
    void draw(int width, int height) {
        Mark where = shown;
        if (where == null || !on || !flat.use()) {
            return;
        }
        int unit = Math.max(1, Math.round((float) height / DESIGN_HEIGHT));
        int middleX = Math.round((where.x * 0.5f + 0.5f) * width), middleY = Math.round((where.y * 0.5f + 0.5f) * height);
        int from = Math.round((GAP + SPREAD * where.spread) * unit), to = from + LENGTH * unit, half = WIDTH * unit / 2;
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glEnable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        int color = flat.at("uColor");
        if (where.dot) {
            float radius = DOT_RADIUS * height / DESIGN_HEIGHT;
            GL20.glUniform4f(color, 0f, 0f, 0f, DOT_RIM);
            disc(middleX, middleY, radius + Math.max(1f, radius / 4f), width, height);
            GL20.glUniform4f(color, 1f, 1f, 1f, DOT_WHITE);
            disc(middleX, middleY, radius, width, height);
            GL11.glDisable(GL_BLEND);
            return;
        }
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

    /** A disc round a pixel's corner, its radius in pixels. */
    private static void disc(int middleX, int middleY, float radius, int width, int height) {
        GL11.glBegin(GL_TRIANGLE_FAN);
        GL11.glVertex2f(middleX * 2f / width - 1f, middleY * 2f / height - 1f);
        for (int i = 0; i <= DOT_SIDES; i++) {
            double way = 2 * Math.PI * i / DOT_SIDES;
            GL11.glVertex2f((middleX + radius * (float) Math.cos(way)) * 2f / width - 1f, (middleY + radius * (float) Math.sin(way)) * 2f / height - 1f);
        }
        GL11.glEnd();
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
