package com.cameraunlock.zomboid;

import zombie.Lua.LuaEventManager;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.ui.MoodlesUI;
import zombie.ui.UIManager;

/**
 * How two players share the screen: the SplitScreen row of CameraUnlock.ini. Vertical is the
 * game's own, side by side, each half the width and the whole height. Horizontal puts player 1
 * above player 2, each the whole width and half the height. With one player, or three or four,
 * the screen is laid out as the game lays it out whatever the row says.
 *
 * The game works a player's part of the screen out in many places, and all of them from the
 * same three things: more than one player halves the width, more than two halve the height,
 * and a player's number says which column and which row is theirs (1 and 3 the right column,
 * 2 and 3 the bottom row). Horizontal for two is that sum with other numbers put in: the width
 * is halved as for one player, the height as for three, and player 2 has the place of player 3,
 * the left of the bottom row. The patches of Agent.splitScreenPatches put those numbers in
 * wherever the game does the sum, and nothing of the sum itself is done here.
 *
 * The row is read again while the game runs. The thread that watches the file sets it, the
 * logic thread turns it on its hotkey and lays the game's panels out again, and both the logic
 * and the render thread read it as they place a player's picture.
 */
public final class SplitScreen {
    static final int VERTICAL = 0, HORIZONTAL = 1;
    private static final String[] NAMES = {"Vertical", "Horizontal"};

    private static volatile int row = VERTICAL;
    /** Every patch is in the game's code: with only some of them in, parts of the screen would be laid out one way and parts the other. */
    private static volatile boolean patched;
    private static boolean asked;
    /** The row the game's panels were last laid out for. */
    private static boolean laidOutStacked;

    private SplitScreen() {}

    static void set(int wanted) {
        if (wanted == row) {
            return;
        }
        row = wanted;
        // The screen changes as frame lays it out, and says so there.
        Log.info("split screen row set to " + described());
    }

    static int row() {
        return row;
    }

    static String name() {
        return NAMES[row];
    }

    /** The row's name, and that the screen stays side by side where the patches are known not to be in. */
    static String described() {
        return asked && !patched && row != VERTICAL ? NAMES[row] + ", but the screen stays side by side: the game's code did not take the change" : NAMES[row];
    }

    /** The words CameraUnlock.ini holds for the row, in the order of their numbers, with commas between. */
    static String words() {
        return String.join(",", NAMES);
    }

    /** Whether the screen is one player above the other. */
    static boolean stacked(int layout, int players) {
        return layout == HORIZONTAL && players == 2;
    }

    /** The number of players the game is to halve the width for, where it asks whether there is more than one. */
    static int across(int layout, int players) {
        return stacked(layout, players) ? 1 : players;
    }

    /** The number of players the game is to halve the height for, where it asks whether there are more than two. */
    static int down(int layout, int players) {
        return stacked(layout, players) ? 3 : players;
    }

    /** The player whose column and row a player has. */
    static int seat(int layout, int players, int index) {
        return stacked(layout, players) && index == 1 ? 2 : index;
    }

    private static int layout() {
        return patched ? row : VERTICAL;
    }

    /** Called where the game has read its count of players to ask whether there is more than one, with the count. */
    public static int across(int players) {
        return across(layout(), players);
    }

    /** Called where the game has read its count of players to ask whether there are more than two, with the count. */
    public static int down(int players) {
        return down(layout(), players);
    }

    /** Called where the game is about to take a player's column or row from their number, with the number. */
    public static int seat(int index) {
        return seat(layout(), IsoPlayer.numPlayers, index);
    }

    /**
     * Called as UIManager.resize returns, which the game runs every frame. It puts each
     * player's moodles down the right of their column and at the top of their row with a sum
     * of its own on the player's number, so they are moved from here: player 1's to the right
     * edge, where it put player 2's, and player 2's as far under the middle as player 1's are
     * under the top.
     */
    public static void afterResize() {
        if (!stacked(layout(), IsoPlayer.numPlayers)) {
            return;
        }
        MoodlesUI first = UIManager.MoodleUI[0], second = UIManager.MoodleUI[1];
        if (first == null || second == null) {
            return;
        }
        first.setX(second.getX());
        second.setY(Core.getInstance().getScreenHeight() / 2 + first.getY());
    }

    /**
     * Once a frame on the logic thread. The first time, asks whether the patches are in. When
     * the row has changed with two players at the screen, has the game lay its panels out
     * again as it does when the window changes size: its Lua is told the size has changed, and
     * each player's view works out afresh what it shows.
     */
    static void frame() {
        if (!asked) {
            asked = true;
            String gap = Agent.splitScreenGap();
            if (gap == null) {
                patched = true;
            } else {
                Log.info("split screen stays side by side whatever the SplitScreen row says: " + gap);
            }
        }
        int now = layout();
        boolean stacked = stacked(now, IsoPlayer.numPlayers);
        if (stacked == laidOutStacked) {
            return;
        }
        laidOutStacked = stacked;
        Integer width = Core.getInstance().getScreenWidth(), height = Core.getInstance().getScreenHeight();
        LuaEventManager.triggerEvent("OnResolutionChange", width, height, width, height);
        for (IsoPlayer player : IsoPlayer.players) {
            if (player != null) {
                player.dirtyRecalcGridStackTime = 2f;
            }
        }
        Log.info("split screen laid out " + NAMES[now] + " for " + width + " by " + height);
    }
}
