package com.cameraunlock.zomboid;

import java.util.Arrays;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.LuaCallFrame;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.network.GameClient;

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
    /** The players' numbers as the game's Lua keys its fishing managers by them, made once: asked every frame. */
    private static final Double[] NUMBERS = {0.0, 1.0, 2.0, 3.0};
    /** Where the aim is, for the game's Lua, which asks on its own thread alone. */
    private static final float[] AIMED = new float[3];

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

    /**
     * Where a player's bobber floats, x and y into out, or false while they have none out. The
     * game keeps it in its Lua (Fishing.ManagerInstances, by the player's number, or their
     * name online) and draws it only in its own view. Logic thread.
     */
    static boolean bobber(IsoPlayer player, float[] out) {
        if (!(manager(player) instanceof KahluaTable manager) || !(manager.rawget("fishingRod") instanceof KahluaTable rod)
                || !(rod.rawget("bobber") instanceof KahluaTable bobber)) {
            return false;
        }
        out[0] = ((Double) bobber.rawget("x")).floatValue();
        out[1] = ((Double) bobber.rawget("y")).floatValue();
        return true;
    }

    /** The game's fishing manager of a player, by their number, or their name online, or null while they have none. */
    private static Object manager(IsoPlayer player) {
        if (!(LuaManager.env.rawget("Fishing") instanceof KahluaTable fishing) || !(fishing.rawget("ManagerInstances") instanceof KahluaTable managers)) {
            return null;
        }
        return managers.rawget(GameClient.client ? player.getUsername() : NUMBERS[player.getPlayerNum()]);
    }

    private static int aim(LuaCallFrame frame, int arguments) {
        Object who = frame.get(0);
        View view = who instanceof IsoPlayer player && player.getJoypadBind() == -1 ? View.of(player) : null;
        if (view != null && view.firstPerson.lookCaptured()) {
            view.controls.aimPoint(AIMED);
            return frame.push((double) AIMED[0], (double) AIMED[1]);
        }
        Object[] ran = LuaManager.caller.pcall(frame.getThread(), stock, who);
        if (!Boolean.TRUE.equals(ran[0])) {
            throw new IllegalStateException("the game's Fishing.Utils.getAimCoords failed: " + Arrays.toString(ran));
        }
        return frame.push(ran[1], ran[2]);
    }
}
