package com.cameraunlock.zomboid;

import java.io.IOException;
import java.util.Arrays;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import zombie.Lua.LuaManager;
import zombie.core.Core;
import zombie.ui.UIManager;

/**
 * The Modern FPS controller layout, one of the game's own controller presets (Options, Controller):
 * the game's Default preset with sprint on the left stick's click, as a click that keeps the
 * character sprinting (Controls.sprintKey), and crouch on the right stick's click. Everything
 * else is Default's, because the game gives B and X several jobs each (B: cancel, climb through,
 * smash a window, brake; X: loot, interact options, cruise control).
 *
 * The game's stick click wheel (toggle crouch, sit on the ground, auto walk) opens on a held
 * left stick click whatever the preset. With crouch on the right stick's click it opens on that
 * click held instead, the click that is the wheel's first job, and leaves the sprint click alone.
 *
 * The game builds its list of presets in its Lua each time it loads it, and a saved choice the
 * list does not have yet is put back to Original before the mod can add this one. So the choice
 * is read before the Lua loads and given back once the preset is in the list.
 */
final class PadPreset {
    static final String KEY = "ModernFPS";
    private static final String LABEL = "Modern FPS";
    private static final String DESCRIPTION = "Sprint on the left stick click: click it while jogging and you sprint until you ease off the stick or aim. "
            + "Crouch on the right stick click. Otherwise as Default.";

    private static final String LUA = """
            local key, label, description = ...
            gamepadBinding.presets[key] = {
                key = key,
                labelText = label,
                descriptionText = description,
                perform = function()
                    CharacterInputBindingSet:resetAllToDefault()
                    CharacterJoypadButtonBinding.Sprint:setBinding(JoypadButton.LeftStick)
                    CharacterJoypadButtonBinding.Crouch:setBinding(JoypadButton.RightStick)
                end
            }
            """;

    private static final String WHEEL = """
            local Menu, Prompt = ISJoystickButtonRadialMenu, ISButtonPrompt
            local down, released, held, update = Menu.onJoypadDown, Menu.onJoypadButtonReleased, Menu.onRepeatLeftStickButton, Prompt.update

            local function onRight()
                return CharacterJoypadButtonBinding.Crouch:getJoypadButton() == JoypadButton.RightStick
            end

            -- The game's wheel code asks for the left stick's click by Joypad.LStickButton alone.
            local function swapped(f, ...)
                local left, right = Joypad.LStickButton, Joypad.RStickButton
                Joypad.LStickButton, Joypad.RStickButton = right, left
                local ran = {pcall(f, ...)}
                Joypad.LStickButton, Joypad.RStickButton = left, right
                if not ran[1] then error(tostring(ran[2]) .. (ran[3] and "\\n" .. tostring(ran[3]) or ""), 0) end
            end

            Menu.onJoypadDown = function(button, joypadData)
                if onRight() then swapped(down, button, joypadData) else down(button, joypadData) end
            end
            Menu.onJoypadButtonReleased = function(button, joypadData)
                if onRight() then swapped(released, button, joypadData) else released(button, joypadData) end
            end
            -- The game calls this only while the left stick's click is held.
            Menu.onRepeatLeftStickButton = function(joypadData)
                if not onRight() then held(joypadData) end
            end
            Prompt.update = function(self)
                update(self)
                if not onRight() or not self.player then return end
                local joypadData = JoypadState.players[self.player + 1]
                if joypadData and joypadData.isActive and isJoypadRightStickButtonPressed(joypadData.id) then
                    swapped(held, joypadData)
                end
            end
            """;

    private static final String CHOOSE = """
            local key = ...
            gamepadBinding:set(key)
            return gamepadBinding:getCurrentActivePresetName()
            """;

    /** The preset the game had chosen as it began loading its Lua. */
    private static String chosenBeforeLoad;

    private PadPreset() {}

    /** Called as the game begins loading its Lua, each time it does. */
    static void luaLoading() {
        chosenBeforeLoad = Core.getInstance().getOptionGamepadBindingPreset();
    }

    /** Called once the game has loaded its Lua, each time it does. */
    static void luaLoaded() {
        run(LUA, "the mod's controller preset", KEY, LABEL, DESCRIPTION);
        run(WHEEL, "the stick click wheel on the crouch click");
        if (KEY.equals(chosenBeforeLoad) && !KEY.equals(Core.getInstance().getOptionGamepadBindingPreset())) {
            Object[] ran = run(CHOOSE, "choosing the mod's controller preset", KEY);
            if (!KEY.equals(ran[1])) {
                throw new IllegalStateException("the game kept the " + ran[1] + " controller preset when the mod chose " + KEY + " again");
            }
            Log.info("the " + LABEL + " controller preset is chosen again after the game's Lua loaded");
        }
    }

    private static Object[] run(String lua, String name, Object... arguments) {
        Object chunk;
        try {
            chunk = LuaCompiler.loadstring(lua, name, LuaManager.env);
        } catch (IOException refused) {
            throw new IllegalStateException(name + ": the game's Lua would not compile it", refused);
        }
        Object[] ran = LuaManager.caller.pcall(UIManager.getDefaultThread(), chunk, arguments);
        if (!Boolean.TRUE.equals(ran[0])) {
            throw new IllegalStateException(name + " failed in the game's Lua: " + Arrays.toString(ran));
        }
        return ran;
    }
}
