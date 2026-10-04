package com.cameraunlock.zomboid;

/**
 * The keys the first person view answers with in place of the player's own
 * bindings, so that it plays like a first person game: Left Shift sprints,
 * Left Ctrl crouches. The game's own defaults have the jog on Left Shift, the
 * sprint on Left Alt and the aim on Left Ctrl.
 *
 * Key codes are the game's keyboard's. A mouse button is 10000 and up, and 0
 * is no key.
 */
final class KeySubstitutions {
    static final int LEFT_CONTROL = 29, LEFT_SHIFT = 42, C = 46, LEFT_ALT = 56, RIGHT_MOUSE = 10001;

    /** The bindings that have a first person answer, by the names the game stores them under. */
    static final java.util.List<String> NAMES = java.util.List.of("Sprint", "Run", "Aim", "Crouch");

    /** A binding's key and its alternative. */
    record Keys(int key, int alt) {}

    private KeySubstitutions() {}

    /**
     * What a binding answers with in first person, or null where it stays as
     * the player has it.
     *
     * @param name the name the game stores the binding under
     * @param key  the player's own key for it
     * @param alt  the player's own alternative
     */
    static Keys substitute(String name, int key, int alt) {
        Keys keys = switch (name) {
            case "Sprint" -> new Keys(LEFT_SHIFT, 0);
            case "Run" -> new Keys(LEFT_ALT, 0);
            case "Aim" -> aimWithoutControl(key, alt);
            // A crouch already on Left Ctrl is left alone, and one the player moved keeps its key.
            case "Crouch" -> key == LEFT_CONTROL || alt == LEFT_CONTROL ? null : new Keys(key == 0 ? C : key, LEFT_CONTROL);
            default -> null;
        };
        return keys == null || keys.key == key && keys.alt == alt ? null : keys;
    }

    private static Keys aimWithoutControl(int key, int alt) {
        int first = key == LEFT_CONTROL ? 0 : key;
        int second = alt == LEFT_CONTROL ? 0 : alt;
        if (first == 0) {
            first = second;
            second = 0;
        }
        return new Keys(first == 0 ? RIGHT_MOUSE : first, second);
    }
}
