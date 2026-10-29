package zombie.core.skinnedmodel.model;

import zombie.characters.IsoGameCharacter;
import zombie.core.textures.TextureDraw;

public final class ModelSlotRenderData extends TextureDraw.GenericDrawer {
    public IsoGameCharacter character;
    public zombie.iso.IsoMovingObject object;
    public final ModelInstanceRenderDataList modelData = null;
    public zombie.core.skinnedmodel.animation.AnimationPlayer animPlayer;
    public float animPlayerAngle;
    public float x;
    public float y;
    public float z;
    public boolean inVehicle;
    public float squareDepth;
    public float centerOfMassY;
    public float ambientR, ambientG, ambientB;
    public float finalScale;
    public float alpha;
    public final ModelInstance.EffectLight[] effectLights = null;
    public ModelInstanceTextureCreator textureCreator;
    public boolean canRender() { throw new UnsupportedOperationException("reference stub"); }
    public ModelInstanceRenderDataList getModelData() { throw new UnsupportedOperationException("reference stub"); }
    @Override
    public void render() { throw new UnsupportedOperationException("reference stub"); }
}
