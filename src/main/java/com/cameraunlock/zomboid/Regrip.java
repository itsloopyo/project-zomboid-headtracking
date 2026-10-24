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
 * equipped in the right alone with the left empty is left so.
 *
 * A light beside such a weapon is put away when it is switched off (the light key, F, only
 * switches it), so that the weapon goes back into both hands; and a light taken up in the left,
 * which takes the weapon out of the right, has the weapon taken back into the right after it.
 * Each is the game's own action (ISUnequipAction, ISEquipWeaponAction), queued as the inventory
 * queues it, the equip asked for the right hand alone. Not where players can fight (Online.regrips). Logic thread.
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

    static final int NOTHING = 0, BOTH = 1, STOW = 2, RETAKE = 3;

    /** What the right and the left held the frame before, whether the right's was for both hands, and whether the left's was a light that was on. */
    private Object right, left;
    private boolean rightBoth, leftLit;

    /**
     * What is to be done, by what the hands hold this frame: BOTH, the weapon in the right goes back
     * into both, the left having just put down something else it held beside that same weapon;
     * STOW, the light in the left beside a weapon for both hands has just been switched off, and is
     * put away; RETAKE, a light has just been taken up in the left and the weapon for both hands the
     * right held has gone out of it, and is taken back into the right.
     *
     * @param both  whether what the right holds is a weapon made for both hands
     * @param light whether what the left holds gives light
     * @param lit   whether it is switched on
     */
    int next(Object primary, boolean both, Object secondary, boolean light, boolean lit) {
        int next = NOTHING;
        if (primary != null && primary == right && left != null && left != primary && secondary == null) {
            next = BOTH;
        } else if (both && light && primary == right && secondary == left && secondary != primary && leftLit && !lit) {
            next = STOW;
        } else if (primary == null && rightBoth && light && secondary != left && secondary != right) {
            next = RETAKE;
        }
        retake = next == RETAKE ? right : null;
        right = primary;
        rightBoth = both;
        left = secondary;
        leftLit = light && lit;
        return next;
    }

    /** The weapon RETAKE takes back into the right. */
    private Object retake;

    void update(IsoPlayer player, int index) {
        InventoryItem primary = player.getPrimaryHandItem(), secondary = player.getSecondaryHandItem();
        boolean both = primary instanceof HandWeapon weapon && (weapon.isTwoHandWeapon() || weapon.isRequiresEquippedBothHands()) && weapon.getCondition() > 0;
        boolean light = secondary != null && secondary.canEmitLight() && secondary.canBeActivated();
        int next = next(primary, both, secondary, light, light && secondary.isActivated());
        if (next == NOTHING || !on || !Online.regrips()) {
            return;
        }
        switch (next) {
            case BOTH -> {
                if (!both) {
                    return;
                }
                call(LuaManager.env.rawget("ISInventoryPaneContextMenu"), "equipWeapon", primary, false, true, (double) index);
                Log.info("the left hand let go beside " + primary.getFullType() + ": it is taken back in both hands");
            }
            case STOW -> {
                queue("ISUnequipAction", player, secondary, 50.0);
                Log.info("the " + secondary.getFullType() + " beside " + primary.getFullType() + " is switched off: it is put away");
            }
            default -> {
                InventoryItem weapon = (InventoryItem) retake;
                if (!player.getInventory().contains(weapon)) {
                    return;
                }
                // The game's `new` asks for both hands for any weapon that requires them, which would put the light down again.
                KahluaTable equip = (KahluaTable) action(LuaManager.env.rawget("ISEquipWeaponAction"), new Object[] {player, weapon, 50.0, true, false});
                equip.rawset("twoHands", false);
                call(LuaManager.env.rawget("ISTimedActionQueue"), "add", equip);
                Log.info("the " + secondary.getFullType() + " taken up in the left took " + weapon.getFullType() + " out of the right: it is taken back into the right");
            }
        }
    }

    /** Queues one of the game's timed actions, made by its class's own `new`. */
    private static void queue(String action, Object... arguments) {
        Object made = action(LuaManager.env.rawget(action), arguments);
        call(LuaManager.env.rawget("ISTimedActionQueue"), "add", made);
    }

    private static Object action(Object type, Object[] arguments) {
        Object[] with = new Object[arguments.length + 1];
        with[0] = type;
        System.arraycopy(arguments, 0, with, 1, arguments.length);
        Object[] result = LuaManager.caller.pcall(UIManager.getDefaultThread(), ((KahluaTable) type).rawget("new"), with);
        if (result.length < 2 || !Boolean.TRUE.equals(result[0]) || result[1] == null) {
            throw new IllegalStateException("the game's timed action could not be made: " + Arrays.toString(result));
        }
        return result[1];
    }

    private static void call(Object table, String function, Object... arguments) {
        Object[] result = LuaManager.caller.pcall(UIManager.getDefaultThread(), ((KahluaTable) table).rawget(function), arguments);
        if (result.length == 0 || !Boolean.TRUE.equals(result[0])) {
            throw new IllegalStateException("the game's " + function + " failed: " + Arrays.toString(result));
        }
    }
}
