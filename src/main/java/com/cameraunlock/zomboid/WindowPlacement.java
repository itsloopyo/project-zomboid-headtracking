package com.cameraunlock.zomboid;

import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.windows.MONITORINFOEX;
import org.lwjgl.system.windows.RECT;
import org.lwjgl.system.windows.User32;
import org.lwjglx.opengl.Display;

/**
 * Centres the game's window in the work area of the monitor it is on. The game
 * centres by its client size against the primary monitor's full size, which
 * leaves a framed window off centre and partly under the taskbar. The hooks
 * run straight after each place the game positions or reframes its window,
 * while the window is still hidden where the game hides it.
 */
public final class WindowPlacement {
    private static final int MONITOR_DEFAULTTONEAREST = 2;
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;

    // The game sets this from its borderless option just before it creates the
    // window, and takes the frame off only afterwards, so at creation it is the
    // one place that says whether the window will keep its frame.
    private static final String UNDECORATED_PROPERTY = "org.lwjgl.opengl.Window.undecorated";

    private WindowPlacement() {}

    /** Called from Display.create, after the game has positioned the new window. */
    public static void centreNewWindow() {
        centre(Boolean.getBoolean(UNDECORATED_PROPERTY));
    }

    /** Called after the game changes the display mode or the borderless setting. */
    public static void centreAfterModeChange() {
        centre(Display.isBorderlessWindow());
    }

    private static void centre(boolean borderless) {
        long window = GLFWNativeWin32.glfwGetWin32Window(Display.getWindow());
        if (borderless || Display.isFullscreen() || User32.IsZoomed(window)) {
            Log.info("window left where the game put it: borderless " + borderless + ", fullscreen "
                    + Display.isFullscreen() + ", maximised " + User32.IsZoomed(window));
            return;
        }

        RECT rect = RECT.create();
        if (!User32.GetWindowRect(null, window, rect)) {
            throw new IllegalStateException("GetWindowRect failed for the game window");
        }
        MONITORINFOEX monitor = MONITORINFOEX.create();
        monitor.cbSize(MONITORINFOEX.SIZEOF);
        if (!User32.GetMonitorInfo(User32.MonitorFromWindow(window, MONITOR_DEFAULTTONEAREST), monitor)) {
            throw new IllegalStateException("GetMonitorInfo failed for the game window's monitor");
        }
        RECT work = monitor.rcWork();

        int width = rect.right() - rect.left();
        int height = rect.bottom() - rect.top();
        int workWidth = work.right() - work.left();
        int workHeight = work.bottom() - work.top();
        String measured = "window " + width + "x" + height + " at (" + rect.left() + ", " + rect.top()
                + "), work area " + workWidth + "x" + workHeight + " at (" + work.left() + ", " + work.top() + ")";
        if (width > workWidth || height > workHeight) {
            Log.info(measured + ", left where the game put it: it does not fit the work area");
            return;
        }

        int x = centredOrigin(work.left(), workWidth, width);
        int y = centredOrigin(work.top(), workHeight, height);
        if (x == rect.left() && y == rect.top()) {
            return;
        }
        if (!User32.SetWindowPos(null, window, 0L, x, y, 0, 0, SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE)) {
            throw new IllegalStateException("SetWindowPos failed for the game window");
        }
        Log.info(measured + ", centred at (" + x + ", " + y + ")");
    }

    static int centredOrigin(int workStart, int workLength, int windowLength) {
        return workStart + (workLength - windowLength) / 2;
    }
}
