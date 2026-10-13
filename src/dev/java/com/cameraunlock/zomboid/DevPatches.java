package com.cameraunlock.zomboid;

import java.util.List;

/**
 * What a development build patches in the game on top of what every build does. Apart from
 * DevKeys, which names the game's player class: the agent reads this as it starts, and a class
 * loaded before the patches are in place brings the game's classes in unpatched.
 */
final class DevPatches {
    private DevPatches() {}

    /**
     * The invincible key (see DevKeys.cheatsAllowed), the frame recorder's clock and its two places in a frame, and the
     * mod's own words on the screen, which the recorder keeps out of a take (see FrameRecorder).
     */
    static List<Agent.Patch> list() {
        return List.of(new Agent.Patch("zombie/characters/PlayerCheats", "isCheatAllowed before its returns", classBytes -> CallSite.filterReturn(classBytes,
                        new CallSite.Method("zombie/characters/PlayerCheats", "isCheatAllowed", "()Z"),
                        new CallSite.Method("com/cameraunlock/zomboid/DevKeys", "cheatsAllowed", "(Z)Z"))),
                new Agent.Patch("zombie/FPSTracking", "frameStep before its returns", classBytes -> CallSite.insertBeforeReturns(classBytes,
                        new CallSite.Method("zombie/FPSTracking", "frameStep", "()J"),
                        code -> CallSite.invokeStatic(code, new CallSite.Method("com/cameraunlock/zomboid/FrameRecorder", "fixStep", "()V")))),
                new Agent.Patch("zombie/gameStates/IngameState", "renderFrameUI after SkyBox.draw and after ActiveMods.renderUI", classBytes -> CallSite.insertAfter(
                        CallSite.insertAfter(classBytes, new CallSite.Method("zombie/gameStates/IngameState", "renderFrameUI", "()V"),
                                new CallSite.Method("zombie/iso/sprite/SkyBox", "draw", "()V"),
                                new CallSite.Method("com/cameraunlock/zomboid/FrameRecorder", "beforeInterface", "()V")),
                        new CallSite.Method("zombie/gameStates/IngameState", "renderFrameUI", "()V"),
                        new CallSite.Method("zombie/modding/ActiveMods", "renderUI", "()V"),
                        new CallSite.Method("com/cameraunlock/zomboid/FrameRecorder", "afterInterface", "()V"))),
                new Agent.Patch("com/cameraunlock/zomboid/FirstPerson", "afterWorldText's words on the screen", classBytes -> CallSite.replaceEach(classBytes,
                        new CallSite.Method("com/cameraunlock/zomboid/FirstPerson", "afterWorldText", "(I)V"),
                        new CallSite.Method("zombie/ui/TextManager", "DrawStringCentre", "(Lzombie/ui/UIFont;DDLjava/lang/String;DDDD)V"),
                        new CallSite.Method("com/cameraunlock/zomboid/FrameRecorder", "said",
                                "(Lzombie/ui/TextManager;Lzombie/ui/UIFont;DDLjava/lang/String;DDDD)V"), 1)));
    }
}
