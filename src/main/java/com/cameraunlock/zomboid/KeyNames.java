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
    /** The game's codes of each Windows virtual-key code core may ask about, for LinuxKeys. */
    private static final Map<Integer, int[]> BY_VIRTUAL_KEY = new HashMap<>();

    static {
        for (int digit = 1; digit <= 9; digit++) {
            name(digit + 1, "Alpha" + digit, 0x30 + digit);
        }
        name(11, "Alpha0", 0x30);
        name(14, "Backspace", 0x08);
        name(15, "Tab", 0x09);
        String rows = "QWERTYUIOP";
        for (int at = 0; at < rows.length(); at++) {
            name(16 + at, rows.substring(at, at + 1), rows.charAt(at));
        }
        name(28, "Return", 0x0D);
        String home = "ASDFGHJKL";
        for (int at = 0; at < home.length(); at++) {
            name(30 + at, home.substring(at, at + 1), home.charAt(at));
        }
        String bottom = "ZXCVBNM";
        for (int at = 0; at < bottom.length(); at++) {
            name(44 + at, bottom.substring(at, at + 1), bottom.charAt(at));
        }
        name(55, "KeypadMultiply", 0x6A);
        name(57, "Space", 0x20);
        name(58, "CapsLock", 0x14);
        for (int f = 1; f <= 10; f++) {
            name(58 + f, "F" + f, 0x6F + f);
        }
        name(69, "Numlock", 0x90);
        name(70, "ScrollLock", 0x91);
        name(71, "Keypad7", 0x67);
        name(72, "Keypad8", 0x68);
        name(73, "Keypad9", 0x69);
        name(74, "KeypadMinus", 0x6D);
        name(75, "Keypad4", 0x64);
        name(76, "Keypad5", 0x65);
        name(77, "Keypad6", 0x66);
        name(78, "KeypadPlus", 0x6B);
        name(79, "Keypad1", 0x61);
        name(80, "Keypad2", 0x62);
        name(81, "Keypad3", 0x63);
        name(82, "Keypad0", 0x60);
        name(83, "KeypadPeriod", 0x6E);
        name(87, "F11", 0x7A);
        name(88, "F12", 0x7B);
        for (int f = 13; f <= 18; f++) {
            name(87 + f, "F" + f, 0x6F + f);
        }
        name(113, "F19", 0x82);
        name(181, "KeypadDivide", 0x6F);
        // The game reads neither Print nor Menu (its KeyCodes answers -1 for both), so neither has a code for LinuxKeys.
        name(183, "Print");
        name(197, "Pause", 0x13);
        name(199, "Home", 0x24);
        name(200, "UpArrow", 0x26);
        name(201, "PageUp", 0x21);
        name(203, "LeftArrow", 0x25);
        name(205, "RightArrow", 0x27);
        name(207, "End", 0x23);
        name(208, "DownArrow", 0x28);
        name(209, "PageDown", 0x22);
        name(210, "Insert", 0x2D);
        name(211, "Delete", 0x2E);
        name(219, "LeftWindows", 0x5B);
        name(220, "RightWindows", 0x5C);
        name(221, "Menu");
        // Keys a row cannot name but a key list can, which core asks for by their codes: Esc, Enter
        // on the keypad (Windows' VK_RETURN, as the other Enter), and Shift, Ctrl and Alt, of a
        // chord as the pair and on their own as one side.
        key(0x1B, 1);
        key(0x0D, 156);
        key(0x10, 42);
        key(0x10, 54);
        key(0x11, 29);
        key(0x11, 157);
        key(0x12, 56);
        key(0x12, 184);
        key(0xA0, 42);
        key(0xA1, 54);
        key(0xA2, 29);
        key(0xA3, 157);
        key(0xA4, 56);
        key(0xA5, 184);
    }

    private KeyNames() {}

    private static void name(int code, String name, int virtualKey) {
        name(code, name);
        key(virtualKey, code);
    }

    private static void name(int code, String name) {
        if (NAMES.put(code, name) != null || CODES.put(name.toLowerCase(Locale.ROOT), code) != null) {
            throw new IllegalStateException("key " + code + " or " + name + " is named twice");
        }
    }

    private static void key(int virtualKey, int code) {
        int[] before = BY_VIRTUAL_KEY.getOrDefault(virtualKey, new int[0]);
        int[] codes = java.util.Arrays.copyOf(before, before.length + 1);
        codes[before.length] = code;
        BY_VIRTUAL_KEY.put(virtualKey, codes);
    }

    /** The game's codes of the key a Windows virtual-key code names, or null where the game has none that is the same key on every layout. */
    static int[] codes(int virtualKey) {
        return BY_VIRTUAL_KEY.get(virtualKey);
    }

    /** The Windows virtual-key code of a name a row can hold, for the test that holds this table to cameraunlock-core's keys.json. */
    static Map<String, Integer> virtualKeys() {
        Map<String, Integer> named = new HashMap<>();
        for (Map.Entry<Integer, int[]> each : BY_VIRTUAL_KEY.entrySet()) {
            for (int code : each.getValue()) {
                String name = NAMES.get(code);
                if (name != null) named.put(name, each.getKey());
            }
        }
        return named;
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
