package com.cameraunlock.zomboid;

/**
 * Whether the world map may be up in a player's hands (HeldMap), from what holds of them this
 * frame, each a bit of one int. The first that holds, in the order of the bits, is why it is down.
 */
final class MapHold {
    private MapHold() {}

    static final int GONE = 1, OFF = 1 << 1, NO_PICTURE = 1 << 2, NOT_FIRST_PERSON = 1 << 3, NO_OWN_ARMS = 1 << 4, BODY_LEADS = 1 << 5, DEAD = 1 << 6,
            SEAT_CHANGED = 1 << 7, PLAYERS_CAN_FIGHT = 1 << 8, AIMING = 1 << 9, ATTACKING = 1 << 10, DOING_SOMETHING_ELSE = 1 << 11;

    private static final String[] WHY = {"the player is gone", "the held map or first person is switched off",
        "the graphics driver would not make the picture the map is drawn into", "the view is not first person",
        "the mod's own arms are not drawn", "the game's animation has the body", "the player is dead", "the player got into or out of a vehicle seat, or moved to another",
        "players can fight on this server", "the player is aiming", "the player is attacking", "the player is doing something else"};

    /** Why the map is down with these holding, or null where it may be up. */
    static String down(int holding) {
        if (holding == 0) {
            return null;
        }
        return WHY[Integer.numberOfTrailingZeros(holding)];
    }

    /** Whether reading the map now puts it in the hands, with these holding: the reading is itself something done. */
    static boolean wanted(int holding) {
        return down(holding & ~DOING_SOMETHING_ELSE) == null;
    }
}
