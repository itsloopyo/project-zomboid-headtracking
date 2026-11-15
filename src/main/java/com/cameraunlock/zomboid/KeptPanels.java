package com.cameraunlock.zomboid;

/**
 * What InventoryStaysOpen asks of the inventory and loot panels from one frame of first person
 * to the next, worked out from the option, whether the look has the mouse and whether the
 * character aims. The panels are shown unfolded as the option comes on and each time the look
 * takes the mouse back, since with the pointer out the game folds them as it always does. They
 * fold while the character aims, as the game's own do, and unfold after. Nothing here pins them:
 * the player's own pin is the game's and stays as they set it.
 *
 * Logic thread only.
 */
final class KeptPanels {
    enum Act {
        NONE,
        /** Show both panels and unfold them. */
        SHOW,
        /** Fold them to their title bars, as the game does while aiming. */
        FOLD,
        /** The option went off: the panels are the game's again. */
        LET_GO
    }

    private boolean kept, folded, looked, owed;

    /**
     * @param option  whether InventoryStaysOpen is on
     * @param looking whether the look has the mouse, the pointer not out
     * @param aiming  whether the character aims
     */
    Act frame(boolean option, boolean looking, boolean aiming) {
        if (!option) {
            if (!kept) {
                return Act.NONE;
            }
            reset();
            return Act.LET_GO;
        }
        if (!kept) {
            kept = true;
            folded = false;
            looked = looking;
            owed = false;
            return Act.SHOW;
        }
        if (aiming && !folded) {
            folded = true;
            owed = false;
            return Act.FOLD;
        }
        if (aiming) {
            looked = looking;
            return Act.NONE;
        }
        boolean show = folded || owed || looking && !looked;
        folded = false;
        owed = false;
        looked = looking;
        return show ? Act.SHOW : Act.NONE;
    }

    /** The SHOW just asked for could not be done, as the game has not made both panels yet: it is asked for again on the next frame. */
    void notShown() {
        owed = true;
    }

    /** Whether the panels are kept open: from the option coming on until it goes off or kept is reset, shown or not yet. */
    boolean kept() {
        return kept;
    }

    /** Nothing kept: first person was left, or the save. */
    void reset() {
        kept = false;
        folded = false;
        looked = false;
        owed = false;
    }
}
