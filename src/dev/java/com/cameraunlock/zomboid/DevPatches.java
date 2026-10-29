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
     * which the recorder keeps out of a take (see FrameRecorder), the check of the thread a
     * square's light is read on (see LightingThread), and when asked for, the check of the
     * composites made while the render thread waits for the frame (see CompositeCheck).
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
        if (Boolean.getBoolean("cameraunlock.zomboid.checkComposites")) {
            patches.addAll(compositeCheck());
        }
        // By the property's name, a constant, so that asking loads no class of the mod's that names the game's before their patches are in.
        if (Boolean.getBoolean(Spotting.CHECKED_BY)) {
            patches.addAll(spotCheck());
        }
        return patches;
    }

    /** The game's spotting of a room, marking of a square seen and marking of a chunk level to be built again for its light, each told to SpotCheck as it begins. */
    private static List<Agent.Patch> spotCheck() {
        String cell = "zombie/iso/IsoCell", room = "zombie/iso/areas/IsoRoom", square = "zombie/iso/IsoGridSquare", meta = "zombie/meta/Meta", levels = "zombie/iso/fboRenderChunk/FBORenderLevels",
                check = "com/cameraunlock/zomboid/SpotCheck";
        return List.of(
                new Agent.Patch(cell, "roomSpotted told to SpotCheck", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                        new CallSite.Method(cell, "roomSpotted", "(L" + room + ";)V"),
                        code -> CallSite.passFirstArgument(code, new CallSite.Method(check, "roomSpotted", "(L" + room + ";)V")))),
                new Agent.Patch(meta, "dealWithSquareSeen told to SpotCheck", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                        new CallSite.Method(meta, "dealWithSquareSeen", "(L" + square + ";)V"),
                        code -> CallSite.passFirstArgument(code, new CallSite.Method(check, "squareSeen", "(L" + square + ";)V")))),
                new Agent.Patch(levels, "invalidateLevel told to SpotCheck", classBytes -> CallSite.insertAtStartOfInstanceMethod(classBytes,
                        new CallSite.Method(levels, "invalidateLevel", "(IJ)V"),
                        code -> CallSite.invokeStatic(code, new CallSite.Method(check, "levelMarked", "()V")))));
    }

    /**
     * With cameraunlock.zomboid.checkComposites set, each composite made while the render thread
     * waits for the frame is made again where the game makes it, and the two compared: see CompositeCheck.
     */
    private static List<Agent.Patch> compositeCheck() {
        String creator = "zombie/core/skinnedmodel/model/ModelInstanceTextureCreator", slot = "zombie/core/skinnedmodel/model/ModelSlotRenderData",
                check = "com/cameraunlock/zomboid/CompositeCheck";
        CallSite.Method render = new CallSite.Method(creator, "render", "()V"), checkReady = new CallSite.Method(slot, "checkReady", "()Z");
        return List.of(new Agent.Patch(creator, "render at its start and on every way out, and postRender at its start, for the composite check",
                        classBytes -> CallSite.insertAtStartOfInstanceMethod(CallSite.insertOnEveryWayOut(CallSite.insertAtStartOfInstanceMethod(classBytes, render,
                                        code -> CallSite.passThis(code, new CallSite.Method(check, "making", "(L" + creator + ";)V"))),
                                        render, code -> CallSite.passThis(code, new CallSite.Method(check, "made", "(L" + creator + ";)V"))),
                                new CallSite.Method(creator, "postRender", "()V"),
                                code -> CallSite.passThis(code, new CallSite.Method(check, "released", "(L" + creator + ";)V")))),
                new Agent.Patch(slot, "checkReady at its start and on every way out, for the composite check",
                        classBytes -> CallSite.insertOnEveryWayOut(CallSite.insertAtStartOfInstanceMethod(classBytes, checkReady,
                                        code -> CallSite.passThis(code, new CallSite.Method(check, "checking", "(L" + slot + ";)V"))),
                                checkReady, code -> CallSite.passThis(code, new CallSite.Method(check, "checked", "(L" + slot + ";)V")))));
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
