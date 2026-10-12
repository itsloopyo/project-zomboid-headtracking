package zombie.core.skinnedmodel.model;

public final class ModelMesh {
    public final org.joml.Vector3f minXyz = new org.joml.Vector3f();
    public final org.joml.Vector3f maxXyz = new org.joml.Vector3f();
    public VertexBufferObject vb;
    public int modificationCount;
    public boolean isReady() { throw new UnsupportedOperationException("reference stub"); }
}
