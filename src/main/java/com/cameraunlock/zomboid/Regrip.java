package com.cameraunlock.zomboid;

import java.util.Arrays;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.ui.UIManager;

/**
 * A weapon made for both hands goes back into both when the left hand lets go of what it held
 * beside it, a torch say. The game leaves it in the right hand alone, where it hits for less
 * and tires and wears faster (CombatManager.applyOneHandedDamagePenalty, processWeaponEndurance),
 * until the player asks for both hands from the inventory's menu. What is asked here is that
 * menu's own action (ISInventoryPaneContextMenu.equipWeapon with two hands), so it takes the
 * time it takes there and the server hears of it as it does from the menu.
 *
 * The weapon need not have been in both hands first. The game's Equip Secondary takes a weapon
 * held in both hands out of the right as well (ISEquipWeaponAction.complete), so a torch and a
 * two-handed weapon are only held together when the weapon was taken up after the torch. One
 * equipped in the right alone with the left empty is left so. Not where players can fight
 * (Online.regrips). Logic thread.
 */
final class Regrip {
    private static volatile boolean on = true;

    static void set(boolean asked) {
        if (asked == on) {
            return;
        }
        on = asked;
        Log.info("two-handed weapons taken back in both hands: " + (asked ? "on" : "off"));
    }

    static boolean on() {
        return on;
    }

    /** What the right and the left held the frame before. */
    private Object right, left;

    /**
     * Whether, by what the hands hold this frame, the weapon in the right is to go back into both:
     * the left has just put down something else it held beside that same weapon.
     */
    boolean emptied(Object primary, Object secondary) {
        boolean put = primary != null && primary == right && left != null && left != primary && secondary == null;
        right = primary;
        left = secondary;
        return put;
    }

    void update(IsoPlayer player, int index) {
        InventoryItem primary = player.getPrimaryHandItem();
        if (!emptied(primary, player.getSecondaryHandItem()) || !on || !Online.regrips()
                || !(primary instanceof HandWeapon weapon) || !weapon.isTwoHandWeapon() && !weapon.isRequiresEquippedBothHands() || weapon.getCondition() <= 0) {
            return;
        }
        Object equip = ((KahluaTable) LuaManager.env.rawget("ISInventoryPaneContextMenu")).rawget("equipWeapon");
        Object[] result = LuaManager.caller.pcall(UIManager.getDefaultThread(), equip, new Object[] {weapon, false, true, (double) index});
        if (result.length == 0 || !Boolean.TRUE.equals(result[0])) {
            throw new IllegalStateException("the game's ISInventoryPaneContextMenu.equipWeapon failed for " + weapon.getFullType() + ": " + Arrays.toString(result));
        }
        Log.info("the left hand let go beside " + weapon.getFullType() + ": it is taken back in both hands");
    }
}
