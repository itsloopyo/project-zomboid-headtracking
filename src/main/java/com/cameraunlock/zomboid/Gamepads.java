package com.cameraunlock.zomboid;

import java.util.HashSet;
import org.lwjglx.input.Controller;
import org.lwjglx.input.Controllers;
import zombie.GameWindow;
import zombie.characters.IsoPlayer;
import zombie.input.JoypadManager;

/**
 * Which controllers the game plays with, and which of them has its menu.
 *
 * Of the joysticks GLFW finds the game keeps only the ones it has a gamepad mapping for, and
 * of those it lets play only the ones ticked in its Controller options. When none is ticked
 * it ticks the joystick at index 0, which is a SpaceMouse when one is plugged in before the
 * controllers are switched on: that has no mapping, so nothing is ticked and no controller
 * answers on the main menu. Here every mapped gamepad plays as soon as it is found, and a
 * 3Dconnexion 6-axis mouse never does, mapped or not.
 *
 * The game gives its menu to a controller whose A is down, the first it comes to in its list,
 * and waits at the end of loading for the A of the first controller that ever had the menu.
 * Here it is the controller whose A was pressed this frame, and the last one to have the menu
 * on the main menu is the one loading waits for and the game starts as player 1. Controllers
 * pressed once a game is under way join as the game has them join, players 2, 3 and 4.
 */
public final class Gamepads {
    private Gamepads() {}

    /** Why GLFW's joystick with this GUID plays no part, or null when it is a gamepad the game plays with. */
    static String ignored(String guid, boolean mapped) {
        if (threeDConnexion(guid)) {
            return "a 3Dconnexion 6-axis mouse";
        }
        if (!mapped) {
            return "GLFW has no gamepad mapping for it";
        }
        return null;
    }

    /**
     * GLFW names a DirectInput device 03000000, its vendor, 0000, its product and twelve
     * zeros, vendor and product each as two bytes low first. 3Dconnexion's vendor is 256f;
     * before it had one its devices were Logitech's, 046d, numbered c6xx.
     */
    static boolean threeDConnexion(String guid) {
        if (guid.length() != 32 || !guid.startsWith("03000000")) {
            return false;
        }
        String vendor = guid.substring(8, 12);
        return vendor.equals("6f25") || vendor.equals("6d04") && guid.startsWith("c6", 18);
    }

    /** Called from Controllers.create once GLFW's joysticks are found, at the game's start. */
    public static void found() {
        int found = 0;
        for (int i = 0; i < Controllers.getControllerCount(); i++) {
            Controller controller = Controllers.getController(i);
            if (controller != null) {
                describe(controller, "found");
                found++;
            }
        }
        if (found == 0) {
            Log.info("no controller found at the start");
        }
    }

    /** Called from Controllers.updateControllersCount once a joystick has been plugged in or taken out. */
    public static void plugged(int index) {
        Controller controller = Controllers.getController(index);
        if (controller == null) {
            Log.info("controller " + index + " taken out");
        } else {
            describe(controller, "plugged in");
        }
    }

    private static void describe(Controller controller, String how) {
        String why = ignored(controller.getGUID(), controller.isGamepad());
        Log.info("controller " + controller.getID() + " " + how + ": '" + controller.getJoystickName() + "', GUID " + controller.getGUID()
                + ", " + controller.getAxisCount() + " axes, " + controller.getButtonCount() + " buttons: "
                + (why == null ? "a gamepad, plays" : "ignored, " + why));
    }

    /**
     * Called from JoypadManager.addJoypad in place of its asking whether the controller is
     * ticked in the options. It is a gamepad, as the game keeps no other, and it is ticked
     * here, so the options show what plays; untick it there and it sits out until the game is
     * started again.
     */
    public static boolean plays(HashSet<String> ticked, Object guid) {
        String id = (String) guid;
        if (threeDConnexion(id)) {
            return false;
        }
        ticked.add(id);
        return true;
    }

    /** Called from GameWindow.logic in place of its asking, of each controller that plays, whether its A is down. */
    public static boolean pressedA(JoypadManager.Joypad joypad) {
        if (!joypad.isButtonStartPress(joypad.getAButton())) {
            return false;
        }
        if (IsoPlayer.getInstance() == null && GameWindow.activatedJoyPad != joypad) {
            GameWindow.activatedJoyPad = joypad;
            Log.info("controller " + joypad.getID() + " has the menu");
        }
        return true;
    }
}
