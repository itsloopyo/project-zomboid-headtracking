package zombie.iso;

import zombie.iso.sprite.IsoSprite;

public class IsoObject {
    public float offsetX;
    public float offsetY;
    public java.util.ArrayList<IsoWallBloodSplat> wallBloodSplats;

    public java.util.ArrayList<zombie.iso.sprite.IsoSpriteInstance> getAttachedAnimSprite() { throw new UnsupportedOperationException("reference stub"); }
    public IsoSprite getOverlaySprite() { throw new UnsupportedOperationException("reference stub"); }
    public IsoSprite getSprite() { throw new UnsupportedOperationException("reference stub"); }
    public IsoDirections getDir() { throw new UnsupportedOperationException("reference stub"); }
    public float getRenderYOffset() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.inventory.ItemContainer getContainer() { throw new UnsupportedOperationException("reference stub"); }
    public int getContainerCount() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.inventory.ItemContainer getContainerByIndex(int index) { throw new UnsupportedOperationException("reference stub"); }
    public final zombie.entity.components.spriteconfig.SpriteConfig getSpriteConfig() { throw new UnsupportedOperationException("reference stub"); }
    public final boolean hasComponent(zombie.entity.ComponentType type) { throw new UnsupportedOperationException("reference stub"); }
    public final <T extends zombie.entity.Component> T getComponent(zombie.entity.ComponentType type) { throw new UnsupportedOperationException("reference stub"); }
    public IsoGridSquare getSquare() { throw new UnsupportedOperationException("reference stub"); }
    public int getObjectIndex() { throw new UnsupportedOperationException("reference stub"); }
    public float getX() { throw new UnsupportedOperationException("reference stub"); }
    public float getY() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.core.properties.PropertyContainer getProperties() { throw new UnsupportedOperationException("reference stub"); }
    public void HitByVehicle(zombie.vehicles.BaseVehicle vehicle, float amount) { throw new UnsupportedOperationException("reference stub"); }
    public zombie.iso.SpriteDetails.IsoObjectType getType() { throw new UnsupportedOperationException("reference stub"); }
    public void setAlphaAndTarget(float alpha) { throw new UnsupportedOperationException("reference stub"); }
    public void invalidateRenderChunkLevel(long flags) { throw new UnsupportedOperationException("reference stub"); }
    public float getAlpha(int playerIndex) { throw new UnsupportedOperationException("reference stub"); }
    public float getTargetAlpha(int playerIndex) { throw new UnsupportedOperationException("reference stub"); }
    public void setTargetAlpha(int playerIndex, float alpha) { throw new UnsupportedOperationException("reference stub"); }
    public final boolean isOutlineHighlight(int playerIndex) { throw new UnsupportedOperationException("reference stub"); }
    public final int getOutlineHighlightCol(int playerIndex) { throw new UnsupportedOperationException("reference stub"); }
}
