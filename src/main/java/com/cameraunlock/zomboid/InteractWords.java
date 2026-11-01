package com.cameraunlock.zomboid;

/**
 * The words of the label under the reticle that says what the Interact key does (InteractLabel),
 * and how the label comes and goes. Nothing here reads the game: each action is named by the key
 * of the game's own translation of it, which InteractLabel looks up.
 */
final class InteractWords {
    private InteractWords() {}

    /** What a press of the key does, by the key of the game's words for it, and whether the name of what it acts on follows. */
    enum Does {
        GRAB("ContextMenu_Grab", true),
        CLIMB_THROUGH("ContextMenu_Climb_through", false),
        OPEN_WINDOW("ContextMenu_Open_window", false),
        CLOSE_WINDOW("ContextMenu_Close_window", false),
        OPEN_DOOR("ContextMenu_Open_door", false),
        CLOSE_DOOR("ContextMenu_Close_door", false),
        OPEN_CURTAINS("ContextMenu_Open_curtains", false),
        CLOSE_CURTAINS("ContextMenu_Close_curtains", false),
        TURN_ON("ContextMenu_Turn_On", false),
        TURN_OFF("ContextMenu_Turn_Off", false),
        /** A walk to a container further off, by the title the loot panel gives it. */
        WALK_TO("ContextMenu_Walk_to", true),
        /** The loot panel, on a container by the title the panel gives it. */
        LOOT("IGUI_Controller_Loot", true),
        /** A radio's or a television's window. */
        DEVICE_OPTIONS("IGUI_DeviceOptions", false),
        /** A workstation's window, which the game's controller prompt names by the workstation alone. */
        OPEN_NAMED(null, true),
        /** The game's world menu for a thing, a sink or a table, as its menu names it. */
        MENU(null, true);

        /** The key of the game's words for it, or null where the name alone says it. */
        final String key;
        final boolean named;

        Does(String key, boolean named) {
            this.key = key;
            this.named = named;
        }
    }

    /** The word before the key when the key does something else held. */
    static final String HOLD = "Hold";
    /** What follows the name of a thing whose world menu the key opens: the menu's further choices. */
    static final String MORE = "...";
    /** The space between what a tap does and what holding the key does. */
    private static final String APART = "   ";

    /**
     * The words for one action, or null where an action that names what it acts on has no name
     * for it.
     *
     * @param said the game's words for it, or null where it has none
     * @param name the name of what it acts on, or null where the game has none
     */
    static String action(Does does, String said, String name) {
        if (does.named && name == null) {
            return null;
        }
        if (does == Does.MENU) {
            return name + MORE;
        }
        if (!does.named) {
            return said;
        }
        return said == null ? name : said + " " + name;
    }

    /**
     * The whole label: the key, then what a tap of it does, then what holding it does where that is
     * something else.
     *
     * @param key  the name of the key, or null where it has none to show
     * @param tap  what a tap does, or null for nothing
     * @param hold what holding the key does, or null where holding does nothing more
     * @return null where the key does nothing
     */
    static String line(String key, String tap, String hold) {
        if (hold != null && hold.equals(tap)) {
            hold = null;
        }
        if (tap == null && hold == null) {
            return null;
        }
        String tapped = tap == null ? null : key == null ? tap : "[" + key + "] " + tap;
        String held = hold == null ? null : key == null ? HOLD + ": " + hold : "[" + HOLD + " " + key + "] " + hold;
        return tapped == null ? held : held == null ? tapped : tapped + APART + held;
    }

    /**
     * How the label comes and goes. Crossing a gap between two things the key acts on does not
     * take it away: it stays as it was for HOLD_NANOS, then fades. It fades in as quickly, and
     * takes new words at once.
     */
    static final class Fade {
        static final long HOLD_NANOS = 150_000_000L, IN_NANOS = 80_000_000L, OUT_NANOS = 120_000_000L;
        /** The longest step one frame takes the fade, so a stall does not jump it from shown to gone. */
        private static final long MOST_STEP_NANOS = 50_000_000L;

        private String text;
        private float alpha;
        private long wantedAt, steppedAt;
        private boolean stepped;

        /** Takes this frame's words, or null for none. */
        void update(String wanted, long now) {
            float step = stepped ? Math.min(now - steppedAt, MOST_STEP_NANOS) : 0f;
            stepped = true;
            steppedAt = now;
            if (wanted != null) {
                text = wanted;
                wantedAt = now;
                alpha = Math.min(1f, alpha + step / IN_NANOS);
            } else if (text != null && now - wantedAt > HOLD_NANOS) {
                alpha = Math.max(0f, alpha - step / OUT_NANOS);
                if (alpha == 0f) {
                    text = null;
                }
            }
        }

        /** Gone at once, as when the pointer comes out. */
        void clear() {
            text = null;
            alpha = 0f;
            stepped = false;
        }

        /** The words shown, or null. */
        String text() {
            return text;
        }

        /** How much of them shows, 0 to 1. */
        float alpha() {
            return alpha;
        }
    }
}
