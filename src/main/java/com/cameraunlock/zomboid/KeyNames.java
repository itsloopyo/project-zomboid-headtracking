package com.cameraunlock.zomboid;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The game's keyboard codes (org.lwjglx.input.Keyboard's KEY_ constants) of the keys a row of
 * [Hotkeys] can name, and the name CameraUnlock.ini spells each with (cameraunlock-core's
 * data/keys.json). Core listens by Windows' virtual-key code, so a key is here only where its name
 * has one that is the same key on every keyboard layout: the punctuation keys, Backslash (43) among
 * them, are not, and nor are the Shift, Ctrl and Alt keys, which are modifiers and never a key of
 * their own, Enter on the keypad, which Windows reports as Enter, and the mouse buttons.
 */
final class KeyNames {
    static final int ESCAPE = 1;

    /** A binding the game's key control can show: one key, and at most one modifier, as the game's own key bindings have. */
    record Binding(int code, boolean shift, boolean ctrl, boolean alt) {}

    private static final Map<Integer, String> NAMES = new HashMap<>();
    private static final Map<String, Integer> CODES = new HashMap<>();

    static {
        for (int digit = 1; digit <= 9; digit++) {
            name(digit + 1, "Alpha" + digit);
        }
        name(11, "Alpha0");
        name(14, "Backspace");
        name(15, "Tab");
        String rows = "QWERTYUIOP";
        for (int at = 0; at < rows.length(); at++) {
            name(16 + at, rows.substring(at, at + 1));
        }
        name(28, "Return");
        String home = "ASDFGHJKL";
        for (int at = 0; at < home.length(); at++) {
            name(30 + at, home.substring(at, at + 1));
        }
        String bottom = "ZXCVBNM";
        for (int at = 0; at < bottom.length(); at++) {
            name(44 + at, bottom.substring(at, at + 1));
        }
        name(55, "KeypadMultiply");
        name(57, "Space");
        name(58, "CapsLock");
        for (int f = 1; f <= 10; f++) {
            name(58 + f, "F" + f);
        }
        name(69, "Numlock");
        name(70, "ScrollLock");
        name(71, "Keypad7");
        name(72, "Keypad8");
        name(73, "Keypad9");
        name(74, "KeypadMinus");
        name(75, "Keypad4");
        name(76, "Keypad5");
        name(77, "Keypad6");
        name(78, "KeypadPlus");
        name(79, "Keypad1");
        name(80, "Keypad2");
        name(81, "Keypad3");
        name(82, "Keypad0");
        name(83, "KeypadPeriod");
        name(87, "F11");
        name(88, "F12");
        for (int f = 13; f <= 18; f++) {
            name(87 + f, "F" + f);
        }
        name(113, "F19");
        name(181, "KeypadDivide");
        name(183, "Print");
        name(197, "Pause");
        name(199, "Home");
        name(200, "UpArrow");
        name(201, "PageUp");
        name(203, "LeftArrow");
        name(205, "RightArrow");
        name(207, "End");
        name(208, "DownArrow");
        name(209, "PageDown");
        name(210, "Insert");
        name(211, "Delete");
        name(219, "LeftWindows");
        name(220, "RightWindows");
        name(221, "Menu");
    }

    private KeyNames() {}

    private static void name(int code, String name) {
        if (NAMES.put(code, name) != null || CODES.put(name.toLowerCase(Locale.ROOT), code) != null) {
            throw new IllegalStateException("key " + code + " or " + name + " is named twice");
        }
    }

    /** Whether a row of [Hotkeys] can name the key of a game's code. */
    static boolean usable(int code) {
        return NAMES.containsKey(code);
    }

    /** A binding as CameraUnlock.ini writes it, "Ctrl+PageUp", or "" for no key (code 0), which leaves the row unbound. */
    static String text(Binding binding) {
        if (binding.code == 0) {
            return "";
        }
        String name = NAMES.get(binding.code);
        if (name == null) {
            throw new IllegalArgumentException("the game's key " + binding.code + " has no name a row of [Hotkeys] can hold");
        }
        return (binding.ctrl ? "Ctrl+" : "") + (binding.shift ? "Shift+" : "") + (binding.alt ? "Alt+" : "") + name;
    }

    /**
     * The first binding of a key list that the game's key control can show, or no key (code 0)
     * where none can be: a list of several keys shows its first, and a chord of two modifiers or a
     * key named by its code (0xBA) is passed over.
     */
    static Binding shown(String list) {
        for (String item : list.split(",")) {
            String[] parts = item.trim().split("\\+");
            Integer code = CODES.get(parts[parts.length - 1].trim().toLowerCase(Locale.ROOT));
            if (code == null || parts.length > 2) {
                continue;
            }
            String modifier = parts.length == 2 ? parts[0].trim().toLowerCase(Locale.ROOT) : "";
            return new Binding(code, modifier.equals("shift"), modifier.equals("ctrl"), modifier.equals("alt"));
        }
        return new Binding(0, false, false, false);
    }

    /** Whether a key list is exactly the one binding the control shows for it, so that the control shows all of it. */
    static boolean shownWhole(String list) {
        return text(shown(list)).equals(list);
    }
}
