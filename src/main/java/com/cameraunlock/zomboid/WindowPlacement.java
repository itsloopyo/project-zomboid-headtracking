package com.cameraunlock.zomboid;

import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjglx.opengl.Display;

/**
 * Centres the game's window in the work area of the monitor it is on. The game
 * centres by its client size against the primary monitor's full size, which
 * leaves a framed window off centre and partly under the taskbar. The hooks
 * run straight after each place the game positions or reframes its window,
 * while the window is still hidden where the game hides it. The centring
 * itself is cameraunlock-core's, which leaves a maximised, minimised or
 * frameless window, and one larger than the work area, where it is and logs
 * what it did.
 */
public final class WindowPlacement {
    // The game sets this from its borderless option just before it creates the
    // window, and takes the frame off only afterwards, so at creation it is the
    // one place that says whether the window will keep its frame.
    private static final String UNDECORATED_PROPERTY = "org.lwjgl.opengl.Window.undecorated";

    private WindowPlacement() {}

    /** Called from Display.create, after the game has positioned the new window. */
    public static void centreNewWindow() {
        if (Boolean.getBoolean(UNDECORATED_PROPERTY)) {
            Log.info("window left where the game put it: it is borderless");
            return;
        }
        centre();
    }

    /** Called after the game changes the display mode or the borderless setting. */
    public static void centreAfterModeChange() {
        centre();
    }

    private static void centre() {
        Core.library().windowCenter(GLFWNativeWin32.glfwGetWin32Window(Display.getWindow()));
    }
}
