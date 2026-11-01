package com.cameraunlock.zomboid;

/**
 * Whether the world map may be up in a player's hands (HeldMap), from what holds of them this
 * frame, each a bit of one int. The first that holds, in the order of the bits, is why it is down.
 */
final class MapHold {
    private MapHold() {}

    static final int GONE = 1, OFF = 1 << 1, NOT_FIRST_PERSON = 1 << 2, NO_OWN_ARMS = 1 << 3, BODY_LEADS = 1 << 4, DEAD = 1 << 5,
            IN_A_VEHICLE = 1 << 6, PLAYERS_CAN_FIGHT = 1 << 7, AIMING = 1 << 8, ATTACKING = 1 << 9, DOING_SOMETHING_ELSE = 1 << 10;

    private static final String[] WHY = {"the player is gone", "the held map or first person is switched off", "the view is not first person",
        "the mod's own arms are not drawn", "the game's animation has the body", "the player is dead", "the player is in a vehicle",
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
