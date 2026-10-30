package com.cameraunlock.zomboid;

import zombie.core.Core;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

/**
 * The line the main menu shows when the mod stayed out of the game, so a player whose mod does
 * nothing is told why without reading the log. The only patch in the game's code then is the
 * one that calls draw.
 */
public final class NotRunning {
    private static volatile String said;
    private static volatile boolean drawFailed;

    private NotRunning() {}

    static void say(String why) {
        said = "Project Zomboid Head Tracking is not running: " + why + ". " + Log.FILE_NAME + " says more";
    }

    /** Called from MainScreenState.render after the game has drawn its mods' UI. */
    public static void draw() {
        String line = said;
        if (line == null || drawFailed) {
            return;
        }
        try {
            TextManager.instance.DrawStringCentre(UIFont.Medium, Core.getInstance().getScreenWidth() / 2.0,
                    Core.getInstance().getScreenHeight() * 0.04, line, 1.0, 0.75, 0.3, 1.0);
        } catch (LinkageError missing) {
            // A build the mod stayed out of can lack what the line is drawn with too: the menu goes on without it.
            drawFailed = true;
            Log.error("the main menu cannot say the mod is not running", missing);
        }
    }
}
