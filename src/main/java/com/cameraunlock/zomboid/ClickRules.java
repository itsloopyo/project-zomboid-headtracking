package com.cameraunlock.zomboid;

import com.cameraunlock.zomboid.InteractWords.Does;

/**
 * What the game's click does (ISObjectClickHandler), by its own tests in its own order, on what
 * CentreUse reads of the game. Nothing here reads the game, so the tests check each rule.
 */
final class ClickRules {
    private ClickRules() {}

    /**
     * A door (doClickDoor): its curtains with Shift held, the character not walking, from the
     * square they hang over, where the click is taken at all; else the door, which the world menu
     * opens or shuts where the click is not taken.
     *
     * @param onSheetSide whether the character stands on the square the door's curtains hang over
     * @param taken       whether the click is taken: no wall between, the same floor, the character beside the door
     */
    static Does door(boolean open, boolean shift, boolean walking, boolean curtains, boolean onSheetSide, boolean taken, boolean curtainsOpen) {
        if (shift && !walking && curtains && onSheetSide && taken) {
            return curtainsOpen ? Does.CLOSE_CURTAINS : Does.OPEN_CURTAINS;
        }
        return open ? Does.CLOSE_DOOR : Does.OPEN_DOOR;
    }

    /**
     * A window (doClickWindow): with Shift held, its curtains; nothing for a window that cannot
     * be broken, one the character does not stand at or one barricaded against them; through a
     * smashed one that is not barricaded; else it is opened or shut. The key clicks only a window
     * the character stands at, or one that is smashed, cannot be broken or is barricaded against
     * them: any other goes to the world menu's Open or Close first, Shift or not.
     *
     * @param curtains   what the click does to its curtains, or null for nothing
     * @param atIt       whether the character stands on its square or the one opposite
     * @param barricaded whether it is barricaded on either side
     */
    static Does window(boolean shift, Does curtains, boolean invincible, boolean atIt, boolean barricadedAgainst, boolean destroyed, boolean barricaded, boolean open) {
        if (shift) {
            return curtains;
        }
        if (invincible || !atIt || barricadedAgainst) {
            return null;
        }
        if (destroyed) {
            return barricaded ? null : Does.CLIMB_THROUGH;
        }
        return open ? Does.CLOSE_WINDOW : Does.OPEN_WINDOW;
    }

    /**
     * Anything else (doClick), in its order: nothing on a square the first player has not seen;
     * a radio's, a television's or a workstation's window where the click opens one; nothing where
     * a safehouse bars looting, there is no container, the character aims or a crate is locked to
     * the character; the
     * loot panel within reach; further off, a walk to it where the game would walk; else nothing.
     *
     * @param panel the window the click opens for a radio, a television or a workstation, or null
     * @param walks whether the game would walk the character to the container (CentreUse.walks)
     */
    static Does click(boolean seen, Does panel, boolean lootBarred, boolean container, boolean aiming, boolean locked, boolean inReach, boolean walks) {
        if (!seen) {
            return null;
        }
        if (panel != null) {
            return panel;
        }
        if (lootBarred || !container || aiming || locked) {
            return null;
        }
        return inReach ? Does.LOOT : walks ? Does.WALK_TO : null;
    }
}
