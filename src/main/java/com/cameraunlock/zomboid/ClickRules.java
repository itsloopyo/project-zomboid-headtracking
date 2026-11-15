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

    /** What holding the key does where a tap does something else. */
    enum Held {
        /** Climbs through the window. */
        CLIMB,
        /** The loot panel's Take All on the container. */
        TAKE_ALL,
        /** Climbs through the hole. */
        HOLE,
        /** What a tap does. */
        AS_TAP
    }

    /**
     * What holding the key does with the reticle where it is, the one decision the key and the label
     * under the reticle take: a window that can be climbed through is climbed through; a container
     * a tap would show in the loot panel is emptied with its Take All, where there is something in
     * it; and otherwise a hole a body fits through is climbed through.
     *
     * @param climbable a window the character can climb through
     * @param loot      a tap shows a container within reach in the loot panel
     * @param empty     that container has nothing in it as the game knows it now
     * @param hole      there is a hole a body fits through at the reticle, which matters only where
     *                  this answers HOLE or AS_TAP with it false (holeMatters)
     */
    static Held held(boolean climbable, boolean loot, boolean empty, boolean hole) {
        if (climbable) {
            return Held.CLIMB;
        }
        if (loot && !empty) {
            return Held.TAKE_ALL;
        }
        return hole ? Held.HOLE : Held.AS_TAP;
    }

    /**
     * Whether a tap of the key takes the highlighted row of the quick loot list (QuickLoot) rather
     * than doing what it did before the list: the list takes the input, it is up for the container
     * the tap is on, and it has a row. On an empty container a tap still shows it in the loot panel.
     */
    static boolean tapTakesRow(boolean listTakes, boolean sameContainer, boolean row) {
        return listTakes && sameContainer && row;
    }

    /** Whether a hole at the reticle would change what held answers, so it is worth looking for one. */
    static boolean holeMatters(boolean climbable, boolean loot, boolean empty) {
        return held(climbable, loot, empty, false) == Held.AS_TAP;
    }
}
