package com.cameraunlock.zomboid;

import org.lwjglx.input.Mouse;
import zombie.core.textures.TextureDraw;

/**
 * Hands the mouse to the game window for looking, and back. The window's input
 * mode can only be changed on the thread that owns the window, which is the
 * render thread, so the logic thread says what it wants and this applies it.
 */
final class MouseGrab extends TextureDraw.GenericDrawer {
    static final MouseGrab DRAWER = new MouseGrab();

    /** What the logic thread wants. */
    static volatile boolean wanted;
    /** What the window was last told. */
    static volatile boolean applied;

    private MouseGrab() {}

    static void sync() {
        boolean want = wanted;
        if (applied != want) {
            Mouse.setGrabbed(want);
            applied = want;
        }
    }

    @Override
    public void render() {
        sync();
    }
}
