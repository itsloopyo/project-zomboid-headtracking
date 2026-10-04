package zombie.core.skinnedmodel.model;

public final class ModelInstanceRenderData {
    public ModelInstance modelInstance;
    public Model model;
    public java.nio.FloatBuffer matrixPalette;
    public boolean ignoreLighting;
    public float hue;

    public void RenderCharacter(ModelSlotRenderData data) { throw new UnsupportedOperationException("reference stub"); }
    public void RenderVehicle(ModelSlotRenderData data) { throw new UnsupportedOperationException("reference stub"); }
    public final org.joml.Matrix4f xfrm = null;
}
