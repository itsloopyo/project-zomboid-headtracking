package com.cameraunlock.zomboid;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.function.IntPredicate;
import zombie.input.GameKeyboard;
import zombie.input.Mouse;

/**
 * The hotkeys' keys on Linux, where cameraunlock-core cannot read them itself (its key source).
 * Core asks by Windows virtual-key code on a thread of its own, inside a call into core, so it is
 * answered from a reading the logic thread takes once a frame of a save, before the hotkeys are
 * taken, of the keys and the mouse buttons the game has read for that frame (GameKeyboard, Mouse). A
 * key core asks about that the game has no code for is said in the log once and is never down.
 */
final class LinuxKeys {
    /** Core's kMaxHoldPollGapMs: a longer gap between readings could make a tap look like a hold. */
    static final long LONGEST_GAP_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

    /**
     * The virtual keys core has asked about, a bit each. Shift, Ctrl and Alt, generic and by side,
     * are read from the first reading on: core asks for them only as a chord's key goes down, and
     * answered from a reading that left them out, the first press of a chord would be taken without them.
     */
    private static final AtomicLongArray asked = new AtomicLongArray(new long[] {1L << 0x10 | 1L << 0x11 | 1L << 0x12, 0,
        0x3FL << (0xA0 - 128), 0});
    /** The last reading, a bit each, swapped whole so core never sees half of one. */
    private static volatile long[] down = new long[4];
    /** When the last reading was taken, or 0 where none has been since the keys were let go. Logic thread. */
    private static long lastRead;
    /** The keys said in the log to have no code in the game. Logic thread. */
    private static final boolean[] said = new boolean[256];

    /** Windows' virtual-key codes of the mouse's buttons, by the game's number for each: left, right, middle and the two side buttons. */
    private static final int[] MOUSE_BUTTONS = {0x01, 0x02, 0x04, 0x05, 0x06};

    private LinuxKeys() {}

    /** Core's key source. Core's tick thread, inside a call into core, so it does nothing but look. */
    static boolean down(int virtualKey) {
        if (virtualKey < 0 || virtualKey > 255) {
            return false;
        }
        long bit = 1L << (virtualKey & 63);
        int word = virtualKey >>> 6;
        if ((asked.get(word) & bit) == 0) {
            asked.getAndUpdate(word, bits -> bits | bit);
        }
        return (down[word] & bit) != 0;
    }

    /** Every key up, where the keys stop being read (a save left, a night slept through), and the next reading does not follow on. Logic thread. */
    static void letGo() {
        down = new long[4];
        lastRead = 0;
    }

    /**
     * Reads the keys core has asked about, and answers whether this reading follows on from the
     * last within LONGEST_GAP_NANOS. Where it does not, what core heard across the gap is not a
     * press to act on, and the caller drops it. Logic thread, in a save.
     */
    static boolean read() {
        return read(GameKeyboard::isKeyDownRaw, button -> button < Mouse.getButtonCount() && Mouse.isButtonDown(button), System.nanoTime());
    }

    static boolean read(IntPredicate gameKeyDown, IntPredicate buttonDown, long now) {
        long[] next = new long[4];
        for (int word = 0; word < next.length; word++) {
            for (long left = asked.get(word); left != 0; left &= left - 1) {
                int virtualKey = word << 6 | Long.numberOfTrailingZeros(left);
                int button = mouseButton(virtualKey);
                if (button >= 0) {
                    if (buttonDown.test(button)) {
                        next[word] |= Long.lowestOneBit(left);
                    }
                    continue;
                }
                int[] codes = KeyNames.codes(virtualKey);
                if (codes == null) {
                    if (!said[virtualKey]) {
                        said[virtualKey] = true;
                        Log.info("hotkey key 0x" + Integer.toHexString(virtualKey).toUpperCase(Locale.ROOT) + " has no key in the game on "
                                + System.getProperty("os.name") + ", so it never fires there");
                    }
                    continue;
                }
                for (int code : codes) {
                    if (gameKeyDown.test(code)) {
                        next[word] |= Long.lowestOneBit(left);
                    }
                }
            }
        }
        down = next;
        boolean followsOn = lastRead != 0 && now - lastRead <= LONGEST_GAP_NANOS;
        lastRead = now;
        return followsOn;
    }

    /** The game's number for the mouse button with a virtual-key code, or -1 where the code is not one. */
    private static int mouseButton(int virtualKey) {
        for (int button = 0; button < MOUSE_BUTTONS.length; button++) {
            if (MOUSE_BUTTONS[button] == virtualKey) {
                return button;
            }
        }
        return -1;
    }
}
