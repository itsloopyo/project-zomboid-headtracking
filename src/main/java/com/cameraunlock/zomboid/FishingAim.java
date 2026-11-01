package com.cameraunlock.zomboid;

import java.util.Arrays;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.LuaCallFrame;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;

/**
 * Where a fishing rod is aimed from the first person view. The game's
 * Fishing.Utils.getAimCoords takes a mouse player's aim from the square under
 * the pointer in its own view, which the first person view hides, so whether
 * the aim is on water, how far it is and where the cast lands came from a
 * point the player cannot see. Under the first person view it is the point the
 * aim is on.
 */
final class FishingAim {
    private static final JavaFunction AIM = FishingAim::aim;
    private static Object stock;

    private FishingAim() {}

    /** Called once the game has loaded its Lua, each time it does. */
    static void register() {
        if (!(LuaManager.env.rawget("Fishing") instanceof KahluaTable fishing) || !(fishing.rawget("Utils") instanceof KahluaTable utils)
                || utils.rawget("getAimCoords") == null) {
            throw new IllegalStateException("the game's Lua has no Fishing.Utils.getAimCoords");
        }
        stock = utils.rawget("getAimCoords");
        utils.rawset("getAimCoords", AIM);
    }

    private static int aim(LuaCallFrame frame, int arguments) {
        Object who = frame.get(0);
        View view = who instanceof IsoPlayer player && player.getJoypadBind() == -1 ? View.of(player) : null;
        if (view != null && view.firstPerson.lookCaptured()) {
            float[] at = new float[3];
            view.controls.aimPoint(at);
            return frame.push((double) at[0], (double) at[1]);
        }
        Object[] ran = LuaManager.caller.pcall(frame.getThread(), stock, who);
        if (!Boolean.TRUE.equals(ran[0])) {
            throw new IllegalStateException("the game's Fishing.Utils.getAimCoords failed: " + Arrays.toString(ran));
        }
        return frame.push(ran[1], ran[2]);
    }
}
