package com.cameraunlock.zomboid;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * The mark of where the body faces while the game draws none. The game draws its own aiming
 * reticle only while the aim is held, and the first person view moves that one to where the
 * shot lands. Otherwise this marks the same point, which is also what the interact key acts
 * on: a crosshair with a weapon in hand, and a dot without one.
 *
 * It also draws the square a drag cursor of the game's is on (DragAim), and the pointers to the
 * places the game's Lua points the player to (Homing), whatever the Crosshair row says: the
 * game draws both by its own view, which is under the picture.
 *
 * The logic thread says where they are and the render thread draws them over the finished picture.
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

    /**
     * A pointer to a place (Homing): from over it at x, y, -1 to 1 across the picture and up
     * it, where it is in view; otherwise from the edge of the screen, the way wayX, wayY points
     * across and up the picture. In the colour the game gave it.
     */
    record Pointer(float x, float y, boolean inView, float wayX, float wayY, float red, float green, float blue, float alpha) {
    }

    /** In pixels on a screen 1080 high: a pointer's length and its width at the base, and the dark edge round it. */
    private static final float POINTER_LONG = 26f, POINTER_WIDE = 22f, POINTER_EDGE = 2f;
    /** How far in from the edges of the screen, as a share of half of it across and up, a pointer to a place out of view stands. */
    private static final float EDGE_ACROSS = 0.88f, EDGE_UP = 0.82f;

    /** Null when the game points the player nowhere. */
    private volatile Pointer[] pointers;

    /** The Crosshair row of CameraUnlock.ini. The thread that watches the file sets it, the render thread reads it. */
    private static volatile boolean on = true;

    /** Null when it is not shown. */
    private volatile Mark shown;

    /**
     * The sides of a drag cursor's square and the lines of which way what is dragged is put, as
     * pieces from x, y to x, y in -1 to 1, and whether what is dragged can be put there.
     */
    static final class Square {
        final float[] pieces = new float[4 * (4 * DragAim.Ghost.MOST + DragAim.MOST_FACING_LINES) * FirstPerson.SQUARE_PIECES];
        int floats;
        boolean fits;
    }

    /** Three, so the logic thread fills one while the render thread may still be drawing either of the last two it was handed. */
    private final Square[] squares = {new Square(), new Square(), new Square()};
    private int filling;
    /** Null when no drag cursor is up. */
    private volatile Square square;

    /** In pixels on a screen 1080 high: the width of a drag cursor's square's line, and of the dark edge round it. */
    private static final float SQUARE_LINE = 3f, SQUARE_EDGE = 1f;

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

    /** The square to fill next, and then hand to showSquare. Logic thread. */
    Square squareToFill() {
        return squares[filling];
    }

    void showSquare(Square filled) {
        square = filled;
        filling = (filling + 1) % squares.length;
    }

    void hideSquare() {
        square = null;
    }

    /** The pointers to show, or null for none. Logic thread. */
    void point(Pointer[] toShow) {
        pointers = toShow;
    }

    /** Draws it into the picture bound, whose viewport is the whole of a picture this many pixels each way. */
    void draw(int width, int height) {
        Mark where = on ? shown : null;
        Square outlined = square;
        Pointer[] pointing = pointers;
        if (where == null && outlined == null && pointing == null || !flat.use()) {
            return;
        }
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glEnable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        if (outlined != null) {
            drawSquare(outlined, width, height);
        }
        if (pointing != null) {
            for (Pointer pointer : pointing) {
                drawPointer(pointer, width, height);
            }
        }
        if (where != null) {
            drawMark(where, width, height);
        }
        GL11.glDisable(GL_BLEND);
    }

    /** A pointer as a wedge whose tip is on the place, pointing down at it, or at the edge of the screen, pointing out the way to turn. */
    private static void drawPointer(Pointer pointer, int width, int height) {
        float unit = (float) height / DESIGN_HEIGHT;
        float tipX, tipY, wayX, wayY;
        if (pointer.inView) {
            tipX = (pointer.x * 0.5f + 0.5f) * width;
            tipY = (pointer.y * 0.5f + 0.5f) * height;
            wayX = 0f;
            wayY = -1f;
        } else {
            // Turned to pixels, so the wedge points the true way on a wide screen.
            wayX = pointer.wayX * width;
            wayY = pointer.wayY * height;
            float length = (float) Math.hypot(wayX, wayY);
            wayX /= length;
            wayY /= length;
            float reach = Math.min(EDGE_ACROSS * width / 2f / Math.max(Math.abs(wayX), 1e-6f), EDGE_UP * height / 2f / Math.max(Math.abs(wayY), 1e-6f));
            tipX = width / 2f + wayX * reach;
            tipY = height / 2f + wayY * reach;
        }
        int color = flat.at("uColor");
        for (int layer = 0; layer < 2; layer++) {
            float edge = layer == 0 ? POINTER_EDGE * unit : 0f;
            if (layer == 0) {
                GL20.glUniform4f(color, 0f, 0f, 0f, 0.55f * pointer.alpha);
            } else {
                GL20.glUniform4f(color, pointer.red, pointer.green, pointer.blue, 0.95f * pointer.alpha);
            }
            float baseX = tipX - wayX * (POINTER_LONG * unit + edge), baseY = tipY - wayY * (POINTER_LONG * unit + edge);
            float half = POINTER_WIDE * unit / 2f + edge;
            GL11.glBegin(GL_TRIANGLE_STRIP);
            vertex(tipX + wayX * edge, tipY + wayY * edge, width, height);
            vertex(baseX - wayY * half, baseY + wayX * half, width, height);
            vertex(baseX + wayY * half, baseY - wayX * half, width, height);
            GL11.glEnd();
        }
    }

    private static void drawSquare(Square outlined, int width, int height) {
        int color = flat.at("uColor");
        float unit = (float) height / DESIGN_HEIGHT;
        GL20.glUniform4f(color, 0f, 0f, 0f, 0.5f);
        pieces(outlined.pieces, outlined.floats, (SQUARE_LINE / 2f + SQUARE_EDGE) * unit, width, height);
        if (outlined.fits) {
            GL20.glUniform4f(color, 0.35f, 0.95f, 0.4f, 0.9f);
        } else {
            GL20.glUniform4f(color, 1f, 0.3f, 0.25f, 0.9f);
        }
        pieces(outlined.pieces, outlined.floats, SQUARE_LINE / 2f * unit, width, height);
    }

    /** Each piece as a band this many pixels each side of its line. */
    private static void pieces(float[] pieces, int floats, float half, int width, int height) {
        for (int i = 0; i + 3 < floats; i += 4) {
            float fromX = (pieces[i] * 0.5f + 0.5f) * width, fromY = (pieces[i + 1] * 0.5f + 0.5f) * height;
            float toX = (pieces[i + 2] * 0.5f + 0.5f) * width, toY = (pieces[i + 3] * 0.5f + 0.5f) * height;
            float length = (float) Math.hypot(toX - fromX, toY - fromY);
            if (length == 0f) {
                continue;
            }
            // Out a little at each end as well, so the pieces of a side and the sides at a corner meet.
            float alongX = (toX - fromX) / length * half, alongY = (toY - fromY) / length * half;
            float acrossX = -alongY, acrossY = alongX;
            GL11.glBegin(GL_TRIANGLE_STRIP);
            vertex(fromX - alongX + acrossX, fromY - alongY + acrossY, width, height);
            vertex(fromX - alongX - acrossX, fromY - alongY - acrossY, width, height);
            vertex(toX + alongX + acrossX, toY + alongY + acrossY, width, height);
            vertex(toX + alongX - acrossX, toY + alongY - acrossY, width, height);
            GL11.glEnd();
        }
    }

    private static void vertex(float x, float y, int width, int height) {
        GL11.glVertex2f(x * 2f / width - 1f, y * 2f / height - 1f);
    }

    private static void drawMark(Mark where, int width, int height) {
        int unit = Math.max(1, Math.round((float) height / DESIGN_HEIGHT));
        int middleX = Math.round((where.x * 0.5f + 0.5f) * width), middleY = Math.round((where.y * 0.5f + 0.5f) * height);
        int from = Math.round((GAP + SPREAD * where.spread) * unit), to = from + LENGTH * unit, half = WIDTH * unit / 2;
        int color = flat.at("uColor");
        if (where.dot) {
            float radius = DOT_RADIUS * height / DESIGN_HEIGHT;
            GL20.glUniform4f(color, 0f, 0f, 0f, DOT_RIM);
            disc(middleX, middleY, radius + Math.max(1f, radius / 4f), width, height);
            GL20.glUniform4f(color, 1f, 1f, 1f, DOT_WHITE);
            disc(middleX, middleY, radius, width, height);
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
