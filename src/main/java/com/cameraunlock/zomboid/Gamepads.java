package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.lwjglx.input.Controller;
import org.lwjglx.input.Controllers;
import org.lwjglx.input.Keyboard;
import se.krka.kahlua.vm.KahluaTable;
import zombie.GameWindow;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.input.GameKeyboard;
import zombie.input.JoypadManager;
import zombie.input.Mouse;
import zombie.ui.UIManager;

/**
 * Which controllers the game plays with, and which of them has its menu.
 *
 * Of the joysticks GLFW finds the game keeps only the ones it has a gamepad mapping for, and
 * of those it lets play only the ones ticked in its Controller options. When none is ticked
 * it ticks the joystick at index 0, which is a SpaceMouse when one is plugged in before the
 * controllers are switched on: that has no mapping, so nothing is ticked and no controller
 * answers on the main menu. Here the ticks the player made stand, and while none of the
 * gamepads plugged in is ticked every one of them is ticked as it is found, in place of the
 * game's one at index 0. A 3Dconnexion 6-axis mouse never plays, mapped or not.
 *
 * The game gives its menu to a controller whose A is down, the first it comes to in its list,
 * and waits at the end of loading for the A of the first controller that ever had the menu.
 * Here it is the controller whose A was pressed this frame, and the last one to have the menu
 * on the main menu is the one loading waits for and the game starts as player 1. A click or a
 * key on the main menu gives the menu back to the keyboard and mouse, and player 1 then plays
 * on them. Controllers pressed once a game is under way join as the game has them join, as the
 * next of players 2, 3 and 4.
 */
public final class Gamepads {
    private Gamepads() {}

    /** The GUIDs ticked here, not by the player: they do not count as the player's choice while the game runs. */
    private static final Set<String> tickedHere = new HashSet<>();

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
     * ticked in the options. It is a gamepad, as the game keeps no other.
     */
    public static boolean plays(HashSet<String> ticked, Object guid) {
        List<String> connected = new ArrayList<>();
        for (int i = 0; i < Controllers.getControllerCount(); i++) {
            Controller controller = Controllers.getController(i);
            if (controller != null && ignored(controller.getGUID(), controller.isGamepad()) == null) {
                connected.add(controller.getGUID());
            }
        }
        return plays(ticked, (String) guid, connected, tickedHere);
    }

    /**
     * Whether a gamepad plays: one the player ticked does, and while the player has ticked none
     * of the gamepads plugged in, every one does and is ticked, so the options show what plays
     * and the game saves it as it saves its own tick at index 0.
     *
     * @param connected the GUIDs of the gamepads plugged in that could play
     * @param here      the GUIDs ticked by this, which it adds to
     */
    static boolean plays(Set<String> ticked, String guid, List<String> connected, Set<String> here) {
        if (threeDConnexion(guid)) {
            return false;
        }
        if (ticked.contains(guid)) {
            return true;
        }
        for (String other : connected) {
            if (ticked.contains(other) && !here.contains(other)) {
                return false;
            }
        }
        ticked.add(guid);
        here.add(guid);
        Log.info("controller " + guid + " ticked: none of the gamepads plugged in was ticked in the game's Controller options");
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

    /**
     * Called at the start of each update of the main menu. The game keeps a controller that once
     * pressed A on it as player 1's however the menu is worked after, and gives the menu back
     * only when that controller is taken out, through MainScreen.setKeyboardMouseActivated.
     * Here a click or a key does the same.
     */
    public static void menu() {
        if (GameWindow.activatedJoyPad == null || !keyboardOrMouseUsed()) {
            return;
        }
        int id = GameWindow.activatedJoyPad.getID();
        Object[] ran = LuaManager.caller.pcall(UIManager.getDefaultThread(),
                ((KahluaTable) LuaManager.env.rawget("MainScreen")).rawget("setKeyboardMouseActivated"), new Object[0]);
        if (ran.length == 0 || !Boolean.TRUE.equals(ran[0])) {
            Log.info("the game's MainScreen.setKeyboardMouseActivated failed, and controller " + id + " keeps the menu: " + Arrays.toString(ran));
            return;
        }
        Log.info("the keyboard and mouse have the menu from controller " + id + ", and player 1 plays on them");
    }

    private static boolean keyboardOrMouseUsed() {
        if (Mouse.isLeftPressed() || Mouse.isRightPressed() || Mouse.isMiddlePressed()) {
            return true;
        }
        for (int key = 1; key < Keyboard.KEYBOARD_SIZE; key++) {
            if (GameKeyboard.isKeyPressed(key)) {
                return true;
            }
        }
        return false;
    }
}
