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

    private static final int GLFW_CURSOR = 0x00033001, GLFW_CURSOR_NORMAL = 0x00034001, GLFW_CURSOR_DISABLED = 0x00034003,
            GLFW_CURSOR_CAPTURED = 0x00034004;

    /**
     * How many more reads of the mouse are of a cursor the window has just put somewhere
     * else. Each time the mouse is taken or let go the window moves its cursor, and the
     * distance it moved is not the player's hand: read as a turn, the view jumped to a new
     * angle whenever the game let the mouse go and it was taken back, which is on aiming and
     * on firing.
     */
    static volatile int moved;
    private static final int MOVED_FOR = 3;
    private static int takenBack;
    /** The game lets the mouse go on every aim, and the line carries the count so far. */
    private static final long SAY_EVERY_NANOS = 60_000_000_000L;
    private static long saidAt;

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
            moved = MOVED_FOR;
        }
        boolean keep = kept && !want;
        if (!ISOLATED && (want || keep || keeping)) {
            // The game puts its own pointer back when the aim is held and at other moments of
            // its own, and the window then lets the mouse go: held once and trusted to stay
            // held, the look was lost every time the sights came up. What the window has is
            // read each frame and put right.
            int should = want ? GLFW_CURSOR_DISABLED : keep ? GLFW_CURSOR_CAPTURED : GLFW_CURSOR_NORMAL;
            long window = Display.getWindow();
            if (GLFW.glfwGetInputMode(window, GLFW_CURSOR) != should) {
                GLFW.glfwSetInputMode(window, GLFW_CURSOR, should);
                moved = MOVED_FOR;
                takenBack++;
                long now = System.nanoTime();
                if (now - saidAt > SAY_EVERY_NANOS) {
                    Log.info("the window had let the mouse go: taken back " + takenBack + " times so far");
                    saidAt = now;
                }
            }
        }
        keeping = keep;
    }

    @Override
    public void render() {
        sync();
    }
}
