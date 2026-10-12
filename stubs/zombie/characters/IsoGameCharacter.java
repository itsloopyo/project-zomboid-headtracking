package zombie.characters;

import zombie.iso.IsoMovingObject;
import zombie.iso.Vector2;
import zombie.vehicles.BaseVehicle;

public abstract class IsoGameCharacter extends IsoMovingObject {
    public CharacterInputMode getInputMode() { throw new UnsupportedOperationException("reference stub"); }
    public int getJoypadBind() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.characters.component.CharacterInputComponent getCharacterInputComponent() { throw new UnsupportedOperationException("reference stub"); }
    public void setAttackTargetSquare(zombie.iso.IsoGridSquare square) { throw new UnsupportedOperationException("reference stub"); }
    public void addBlood(zombie.characterTextures.BloodBodyPartType part, boolean scratched, boolean bitten, boolean allLayers) { throw new UnsupportedOperationException("reference stub"); }
    public Vector2 getForwardDirection() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.iso.Vector2 getAnimForwardDirection(zombie.iso.Vector2 out) { throw new UnsupportedOperationException("reference stub"); }
    public void setForwardDirection(Vector2 direction) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isAiming() { throw new UnsupportedOperationException("reference stub"); }
    public float getAimOriginPosX() { throw new UnsupportedOperationException("reference stub"); }
    public float getAimOriginPosY() { throw new UnsupportedOperationException("reference stub"); }
    public float getAimOriginPosZ() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isSneaking() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isSitOnGround() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isSittingOnFurniture() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.core.skinnedmodel.animation.AnimationPlayer getAnimationPlayer() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isAsleep() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isDead() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.characters.WornItems.WornItems getWornItems() { throw new UnsupportedOperationException("reference stub"); }
    public BaseVehicle getVehicle() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isAimingFirearmEquipped() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isKnockedDown() { throw new UnsupportedOperationException("reference stub"); }
    public float getHealth() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.inventory.InventoryItem getPrimaryHandItem() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.inventory.ItemContainer getInventory() { throw new UnsupportedOperationException("reference stub"); }
    public void teleportTo(float x, float y, int level) { throw new UnsupportedOperationException("reference stub"); }
    public void setPrimaryHandItem(zombie.inventory.InventoryItem item) { throw new UnsupportedOperationException("reference stub"); }
    public void setSecondaryHandItem(zombie.inventory.InventoryItem item) { throw new UnsupportedOperationException("reference stub"); }
    public zombie.inventory.InventoryItem getSecondaryHandItem() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isSprinting() { throw new UnsupportedOperationException("reference stub"); }
    public final boolean isFemale() { throw new UnsupportedOperationException("reference stub"); }
    public String GetVariable(String name) { throw new UnsupportedOperationException("reference stub"); }
    public final boolean isFalling() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isCurrentState(zombie.ai.State state) { throw new UnsupportedOperationException("reference stub"); }
    public zombie.ai.State getCurrentState() { throw new UnsupportedOperationException("reference stub"); }
    public String getHitReaction() { throw new UnsupportedOperationException("reference stub"); }
    public boolean hasAnimationPlayer() { throw new UnsupportedOperationException("reference stub"); }
    public void setSceneCulled(boolean culled) { throw new UnsupportedOperationException("reference stub"); }
    public zombie.core.skinnedmodel.advancedanimation.AdvancedAnimator getAdvancedAnimator() { throw new UnsupportedOperationException("reference stub"); }
    public int getLastHitCount() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isInvisible() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.characters.BodyDamage.BodyDamage getBodyDamage() { throw new UnsupportedOperationException("reference stub"); }
    public void setAvoidDamage(boolean on) { throw new UnsupportedOperationException("reference stub"); }
    public zombie.core.physics.BallisticsController getBallisticsController() { throw new UnsupportedOperationException("reference stub"); }
    public java.util.ArrayList<zombie.inventory.InventoryItem> getActiveLightItems(java.util.ArrayList<zombie.inventory.InventoryItem> into) { throw new UnsupportedOperationException("reference stub"); }
    public zombie.characters.Stats getStats() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.characters.Moodles.Moodles getMoodles() { throw new UnsupportedOperationException("reference stub"); }
    public float getWornItemsVisionMultiplier() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.inventory.types.HandWeapon getAttackingWeapon() { throw new UnsupportedOperationException("reference stub"); }
}
