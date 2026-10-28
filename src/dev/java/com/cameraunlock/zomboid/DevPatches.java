package com.cameraunlock.zomboid;

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Opcode;
import java.util.ArrayList;
import java.util.List;

/**
 * What a development build patches in the game on top of what every build does. Apart from
 * DevKeys, which names the game's player class: the agent reads this as it starts, and a class
 * loaded before the patches are in place brings the game's classes in unpatched.
 */
final class DevPatches {
    private DevPatches() {}

    /**
     * Rewind 30s, which is not ready to ship (see Rewind), the invincible key (see DevKeys.cheatsAllowed),
     * the frame recorder's clock and its two places in a frame, the mod's own words on the screen,
     * which the recorder keeps out of a take (see FrameRecorder), and the check of the thread a
     * square's light is read on (see LightingThread).
     */
    static List<Agent.Patch> list() {
        List<Agent.Patch> patches = new ArrayList<>(List.of(new Agent.Patch("zombie/characters/PlayerCheats", "isCheatAllowed before its returns", classBytes -> CallSite.filterReturn(classBytes,
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
                                "(Lzombie/ui/TextManager;Lzombie/ui/UIFont;DDLjava/lang/String;DDDD)V"), 1))));
        patches.addAll(rewind());
        String lighting = "zombie/iso/LightingJNI$JNILighting";
        patches.add(new Agent.Patch(lighting, "update checks the thread a square's light is read on", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                new CallSite.Method(lighting, "update", "()V"),
                code -> CallSite.invokeStatic(code, new CallSite.Method("com/cameraunlock/zomboid/LightingThread", "update", "()V")))));
        return patches;
    }

    /**
     * Every death of a character goes through this Kill, before the game makes a body or tells
     * its Lua, and every zombie through its three updates, where its animation runs too. See Rewind.
     */
    private static List<Agent.Patch> rewind() {
        String character = "zombie/characters/IsoGameCharacter", zombie = "zombie/characters/IsoZombie", rewind = "com/cameraunlock/zomboid/Rewind";
        CallSite.Method still = new CallSite.Method(rewind, "zombiesStill", "()Z");
        List<Agent.Patch> patches = new ArrayList<>();
        patches.add(new Agent.Patch(character, "Kill passed over while a rewind holds the death", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                new CallSite.Method(character, "Kill", "(L" + character + ";Lzombie/inventory/types/HandWeapon;ZLzombie/characters/CharacterDiedListener;)V"),
                code -> {
                    code.aload(0);
                    code.aload(1);
                    code.aload(2);
                    code.iload(3);
                    code.aload(4);
                    CallSite.invokeStatic(code, new CallSite.Method(rewind, "holds",
                            "(L" + character + ";L" + character + ";Lzombie/inventory/types/HandWeapon;ZLzombie/characters/CharacterDiedListener;)Z"));
                    code.ifThen(Opcode.IFNE, CodeBuilder::return_);
                })));
        String state = "zombie/gameStates/IngameState";
        patches.add(new Agent.Patch(state, "updateInternal at its start, before it saves to leave for the menu", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                new CallSite.Method(state, "updateInternal", "()Lzombie/gameStates/GameStateMachine$StateAction;"),
                code -> CallSite.invokeStatic(code, new CallSite.Method(rewind, "ingameUpdate", "()V")))));
        for (String update : new String[] {"preupdate", "update", "postupdate"}) {
            patches.add(new Agent.Patch(zombie, update + " passed over while the zombies stand still after a rewind", classBytes -> CallSite.returnAtOnceIf(classBytes,
                    new CallSite.Method(zombie, update, "()V"), still)));
        }
        return patches;
    }
}
