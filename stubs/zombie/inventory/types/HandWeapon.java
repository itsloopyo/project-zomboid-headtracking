package zombie.inventory.types;

import zombie.inventory.InventoryItem;

public final class HandWeapon extends InventoryItem {
    public boolean isAimedFirearm() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isRackAfterShoot() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.scripting.objects.WeaponReloadType getWeaponReloadType() { throw new UnsupportedOperationException("reference stub"); }
    public int getCurrentAmmoCount() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isContainsClip() { throw new UnsupportedOperationException("reference stub"); }
    public void setRoundChambered(boolean chambered) { throw new UnsupportedOperationException("reference stub"); }
    public void setJammed(boolean jammed) { throw new UnsupportedOperationException("reference stub"); }
    public WeaponPart getWeaponPart(String partType) { throw new UnsupportedOperationException("reference stub"); }
    public void attachWeaponPart(WeaponPart part) { throw new UnsupportedOperationException("reference stub"); }
}
