package zombie.core.skinnedmodel.model;

public final class ItemModelRenderer {
    private Model model;
    private final org.joml.Matrix4f transform = new org.joml.Matrix4f();
    private float tintR;
    private float tintG;
    private float tintB;
    private String tintMask;
    public float x;
    public float y;
    public float z;
    public final org.joml.Vector3f angle = new org.joml.Vector3f();
    public RenderStatus renderMain(zombie.inventory.InventoryItem item, zombie.iso.IsoGridSquare square, zombie.iso.IsoGridSquare renderSquare, float x, float y, float z,
            float flipAngle, float forcedRotation, boolean forceNoAtlas) { throw new UnsupportedOperationException("reference stub"); }
    public void reset() { throw new UnsupportedOperationException("reference stub"); }

    public enum RenderStatus {
        NoModel, Failed, Loading, Ready
    }
}
