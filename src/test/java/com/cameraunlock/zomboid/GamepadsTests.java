package com.cameraunlock.zomboid;

import java.util.HashSet;

/** Tests of which of GLFW's joysticks the game plays with, by the GUIDs GLFW gives the devices on the owner's machine. */
final class GamepadsTests {
    private GamepadsTests() {}

    private static final String XINPUT = "78696e70757401000000000000000000";
    private static final String DUALSENSE = "030000004c050000e60c000000000000";
    private static final String SPACEMOUSE_RECEIVER = "030000006f25000052c6000000000000";
    private static final String SPACE_NAVIGATOR = "030000006d04000026c6000000000000";
    private static final String LOGITECH_F310 = "030000006d04000016c2000000000000";

    static void run() {
        check(Gamepads.ignored(XINPUT, true) == null, "an Xbox controller through XInput plays");
        check(Gamepads.ignored(DUALSENSE, true) == null, "a DualSense plays");
        check(Gamepads.ignored(LOGITECH_F310, true) == null, "a Logitech gamepad plays");
        check("GLFW has no gamepad mapping for it".equals(Gamepads.ignored(LOGITECH_F310, false)), "a joystick with no gamepad mapping is ignored");
        check("a 3Dconnexion 6-axis mouse".equals(Gamepads.ignored(SPACEMOUSE_RECEIVER, false)), "the SpaceMouse receiver is ignored as one");
        check("a 3Dconnexion 6-axis mouse".equals(Gamepads.ignored(SPACEMOUSE_RECEIVER, true)), "and so it is with a gamepad mapping");
        check("a 3Dconnexion 6-axis mouse".equals(Gamepads.ignored(SPACE_NAVIGATOR, true)), "a 3Dconnexion device under Logitech's vendor is ignored");

        HashSet<String> ticked = new HashSet<>();
        check(Gamepads.plays(ticked, DUALSENSE) && ticked.contains(DUALSENSE), "a gamepad never ticked in the options plays, and is ticked");
        check(Gamepads.plays(ticked, XINPUT) && ticked.size() == 2, "and so does a second");
        check(!Gamepads.plays(ticked, SPACEMOUSE_RECEIVER) && !ticked.contains(SPACEMOUSE_RECEIVER), "a SpaceMouse never plays and is never ticked");
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}
