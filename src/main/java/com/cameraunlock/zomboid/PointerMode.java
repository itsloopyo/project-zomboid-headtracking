package com.cameraunlock.zomboid;

/**
 * Whether the first person view has let go of the mouse so that the player can
 * point at the game's panels. Three things let go of it: the inventory key,
 * which opens the inventory and holds the pointer until it is pressed again or
 * Escape is, as does a container opened from the look; a panel of the game's
 * that opens; and a click of a button, which holds it until the button is
 * clicked again or Escape is pressed.
 */
final class PointerMode {
    /** What the inventory and loot panels are to do after an update. */
    enum Inventory { LEAVE, OPEN, CLOSE }

    /** Before the first update there is no count of panels to compare with. */
    private static final int UNCOUNTED = -1;

    private boolean byKey, byPanel, clicked;
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
     * @param click        whether the button that brings the pointer out and puts it back went down this frame
     * @param panels       how many of the game's panels that need the pointer are open
     */
    Inventory update(boolean inventoryKey, boolean escape, boolean click, int panels) {
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
                clicked = false;
                ignoredPanels = panels;
            }
        } else if (escape && byKey) {
            byKey = false;
            inventory = Inventory.CLOSE;
        }
        if (escape) {
            clicked = false;
        } else if (click) {
            clicked = !clicked;
        }
        return inventory;
    }

    /**
     * Takes the panels open now as ones the pointer is not out for. The aim button is for
     * the sights and nothing else: whatever the game opens on it, or on letting it go, does
     * not take the look away.
     */
    void passOver(int panels) {
        byPanel = false;
        ignoredPanels = panels;
    }

    /** Has the pointer out for the newest of the panels open now, until it closes. */
    void forNewest(int panels) {
        byPanel = true;
        ignoredPanels = panels - 1;
    }

    /** Takes back a click's hold on the pointer, for a menu taken down to go back to the look. */
    void menuShut() {
        clicked = false;
    }

    /** Brings the pointer out as the inventory key does, for a container opened from the look. */
    void open() {
        byKey = true;
    }

    /** Back to the look, for when the first person view is turned off. */
    void reset() {
        byKey = false;
        byPanel = false;
        clicked = false;
        ignoredPanels = UNCOUNTED;
    }

    boolean on() {
        return byKey || byPanel || clicked;
    }

    /** Whether the inventory key has the pointer out. */
    boolean byKey() {
        return byKey;
    }

    /** Whether Escape puts the pointer back: the inventory key or a click has it out. */
    boolean escapeTakes() {
        return byKey || clicked;
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
        if (clicked) {
            why.append(separator).append("clicked");
        }
        return why.toString();
    }
}
