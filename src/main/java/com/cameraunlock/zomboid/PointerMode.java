package com.cameraunlock.zomboid;

/**
 * Whether the first person view has let go of the mouse so that the player can
 * point at the game's panels. Three things let go of it: the inventory key,
 * which opens the inventory and holds the pointer until it is pressed again or
 * Escape is; a panel of the game's that opens; and a button held down.
 */
final class PointerMode {
    /** What the inventory and loot panels are to do after an update. */
    enum Inventory { LEAVE, OPEN, CLOSE }

    /** Before the first update there is no count of panels to compare with. */
    private static final int UNCOUNTED = -1;

    private boolean byKey, byPanel, held;
    /**
     * How many panels are open that the pointer is not out for: the ones
     * already there when the view was turned on or the player went back to the
     * look past them. Only a panel opening beyond these brings the pointer out,
     * so one the player leaves open all session does not take the look away.
     */
    private int ignoredPanels = UNCOUNTED;

    /**
     * @param inventoryKey whether the inventory key went down this frame
     * @param escape       whether Escape went down this frame
     * @param holdDown     whether the button that points only while it is held is down
     * @param panels       how many of the game's panels that need the pointer are open
     */
    Inventory update(boolean inventoryKey, boolean escape, boolean holdDown, int panels) {
        Inventory inventory = Inventory.LEAVE;
        if (ignoredPanels == UNCOUNTED) {
            ignoredPanels = panels;
        }
        if (byPanel) {
            byPanel = panels > ignoredPanels;
        } else if (panels > ignoredPanels) {
            byPanel = true;
        } else {
            ignoredPanels = panels;
        }
        if (inventoryKey) {
            byKey = !byKey;
            inventory = byKey ? Inventory.OPEN : Inventory.CLOSE;
            if (!byKey) {
                // Closing the inventory is the way back to the look, past whatever else is open.
                byPanel = false;
                ignoredPanels = panels;
            }
        } else if (escape && byKey) {
            byKey = false;
            inventory = Inventory.CLOSE;
        }
        held = holdDown;
        return inventory;
    }

    /** Back to the look, for when the first person view is turned off. */
    void reset() {
        byKey = false;
        byPanel = false;
        held = false;
        ignoredPanels = UNCOUNTED;
    }

    boolean on() {
        return byKey || byPanel || held;
    }

    /** Whether the inventory key has the pointer out, which is when Escape puts it back. */
    boolean byKey() {
        return byKey;
    }

    /** Whether the pointer is out and what for, for the trace. */
    String describe() {
        if (!on()) {
            return "false";
        }
        StringBuilder why = new StringBuilder("true");
        char separator = ' ';
        if (byKey) {
            why.append(separator).append("key");
            separator = '+';
        }
        if (byPanel) {
            why.append(separator).append("panel");
            separator = '+';
        }
        if (held) {
            why.append(separator).append("held");
        }
        return why.toString();
    }
}
