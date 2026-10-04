package zombie.characters;

import zombie.iso.IsoMovingObject;
import zombie.iso.Vector2;
import zombie.vehicles.BaseVehicle;

public abstract class IsoGameCharacter extends IsoMovingObject {
    public Vector2 getForwardDirection() { throw new UnsupportedOperationException("reference stub"); }
    public void setForwardDirection(Vector2 direction) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isAiming() { throw new UnsupportedOperationException("reference stub"); }
    public float getAimOriginPosX() { throw new UnsupportedOperationException("reference stub"); }
    public float getAimOriginPosY() { throw new UnsupportedOperationException("reference stub"); }
    public float getAimOriginPosZ() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isSneaking() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isSitOnGround() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isSittingOnFurniture() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isDead() { throw new UnsupportedOperationException("reference stub"); }
    public BaseVehicle getVehicle() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isAimingFirearmEquipped() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isKnockedDown() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.inventory.InventoryItem getPrimaryHandItem() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.inventory.ItemContainer getInventory() { throw new UnsupportedOperationException("reference stub"); }
    public void teleportTo(float x, float y, int level) { throw new UnsupportedOperationException("reference stub"); }
    public void setPrimaryHandItem(zombie.inventory.InventoryItem item) { throw new UnsupportedOperationException("reference stub"); }
    public void setSecondaryHandItem(zombie.inventory.InventoryItem item) { throw new UnsupportedOperationException("reference stub"); }
    public zombie.inventory.InventoryItem getSecondaryHandItem() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isSprinting() { throw new UnsupportedOperationException("reference stub"); }
    public final boolean isFalling() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isPerformingAttackAnimation() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isShoving() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isInvisible() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.characters.BodyDamage.BodyDamage getBodyDamage() { throw new UnsupportedOperationException("reference stub"); }
    public void setAvoidDamage(boolean on) { throw new UnsupportedOperationException("reference stub"); }
    public zombie.core.physics.BallisticsController getBallisticsController() { throw new UnsupportedOperationException("reference stub"); }
}
