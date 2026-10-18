package zombie.core.skinnedmodel.model;

public final class VertexBufferObject {
    int[] elements;
    Vbo handle;

    public static final class Vbo {
        public int vboId;
        public int numElements;
        public int vertexStride;
        public boolean faceDataOnly;
    }

    public static final class VertexFormat {
        public int getNumElements() { throw new UnsupportedOperationException("reference stub"); }
        public VertexElement getElement(int index) { throw new UnsupportedOperationException("reference stub"); }
    }

    public static final class VertexElement {
        public VertexType type;
        public int byteOffset;
    }

    public enum VertexType {
        VertexArray, NormalArray, ColorArray, IndexArray, TextureCoordArray, TangentArray, BlendWeightArray, BlendIndexArray, Depth
    }
}
