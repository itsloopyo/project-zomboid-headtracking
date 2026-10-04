package com.cameraunlock.zomboid;

import org.lwjgl.glfw.GLFW;
import org.lwjglx.input.Mouse;
import org.lwjglx.opengl.Display;
import zombie.core.textures.TextureDraw;

/**
 * Hands the mouse to the game window for looking, and back. The window's input
 * mode can only be changed on the thread that owns the window, which is the
 * render thread, so the logic thread says what it wants and this applies it.
 */
final class MouseGrab extends TextureDraw.GenericDrawer {
    static final MouseGrab DRAWER = new MouseGrab();

    /**
     * Set by a test that runs the game behind the windows of whoever is at the machine. The
     * real mouse is theirs: the window never takes it, and the view is turned by the test's
     * own commands only.
     */
    static final boolean ISOLATED = Boolean.getBoolean("cameraunlock.zomboid.isolated");

    private static final int GLFW_CURSOR = 0x00033001, GLFW_CURSOR_NORMAL = 0x00034001, GLFW_CURSOR_CAPTURED = 0x00034004;

    /** What the logic thread wants. */
    static volatile boolean wanted;
    /**
     * Whether the pointer, while it is out for the game's panels, is kept inside the window.
     * Left free it slides off the edge onto whatever is beside the game, and the next click
     * goes there and takes the keyboard with it.
     */
    static volatile boolean kept;
    /** Whether the window was last told to keep it in. */
    static volatile boolean keeping;
    /** What the window was last told. */
    static volatile boolean applied;

    private MouseGrab() {}

    static void sync() {
        boolean want = wanted;
        if (applied != want) {
            if (!ISOLATED) {
                Mouse.setGrabbed(want);
            }
            applied = want;
            keeping = false;
        }
        boolean keep = kept && !want;
        if (keeping != keep && !ISOLATED && !want) {
            GLFW.glfwSetInputMode(Display.getWindow(), GLFW_CURSOR, keep ? GLFW_CURSOR_CAPTURED : GLFW_CURSOR_NORMAL);
        }
        keeping = keep;
    }

    @Override
    public void render() {
        sync();
    }
}
