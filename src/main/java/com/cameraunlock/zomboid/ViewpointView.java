package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;

/**
 * The view of Project Viewpoint, another mod, which first person takes turns with: both turn the
 * movement keys by their own camera, so with both on the character walks a way neither shows.
 * Viewpoint draws for the first player only, and its view is on while its View.enabled is. Its
 * view is switched off through its own FP.switchView, the way its key does, so it lets go of what
 * it holds as it does then. Logic thread, but for mouseHeld.
 */
final class ViewpointView {
    private static volatile Field enabled, captured;
    private static volatile Method switchView;

    private ViewpointView() {}

    /** Finds Viewpoint's view through the loader of its Main class, once that has started. */
    static void found(Class<?> main) {
        try {
            ClassLoader loader = main.getClassLoader();
            Field on = Class.forName("viewpoint.core.View", false, loader).getField("enabled");
            if (on.getType() != boolean.class || !Modifier.isStatic(on.getModifiers())) {
                throw new IllegalStateException("viewpoint.core.View.enabled is not a static boolean");
            }
            Method off = Class.forName("viewpoint.FP", false, loader).getDeclaredMethod("switchView", IsoGameCharacter.class, boolean.class);
            if (!Modifier.isStatic(off.getModifiers())) {
                throw new IllegalStateException("viewpoint.FP.switchView is not static");
            }
            off.setAccessible(true);
            Field held = Class.forName("viewpoint.input.Look", false, loader).getDeclaredField("captured");
            if (held.getType() != boolean.class || !Modifier.isStatic(held.getModifiers())) {
                throw new IllegalStateException("viewpoint.input.Look.captured is not a static boolean");
            }
            held.setAccessible(true);
            captured = held;
            switchView = off;
            enabled = on;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Log.error("first person and Project Viewpoint's view are not kept from running together: Viewpoint is not as this was made for", failure);
        }
    }

    /** Whether Viewpoint's view is on for this player. */
    static boolean on(IsoPlayer player) {
        Field on = enabled;
        if (on == null || player == null || player != IsoPlayer.players[0]) {
            return false;
        }
        try {
            return on.getBoolean(null);
        } catch (IllegalAccessException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * Whether Viewpoint's look still holds the mouse. It lets go only from its own code at the
     * start of the game's Display.updateMouseCursor, once its view is no longer drawn, and until
     * then it pins the game's cursor to the middle of the screen on every Mouse.update, so the
     * first-person look, which reads that cursor, does not turn. Render thread.
     */
    static boolean mouseHeld() {
        Field held = captured;
        if (held == null) {
            return false;
        }
        try {
            return held.getBoolean(null);
        } catch (IllegalAccessException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Switches Viewpoint's view off, as its own key does. */
    static void leave(IsoPlayer player) {
        try {
            switchView.invoke(null, player, false);
        } catch (IllegalAccessException impossible) {
            throw new IllegalStateException(impossible);
        } catch (InvocationTargetException failure) {
            throw new IllegalStateException("Project Viewpoint's view could not be switched off", failure.getCause());
        }
        Log.info("Project Viewpoint's view switched off, as first person now covers it");
    }
}
