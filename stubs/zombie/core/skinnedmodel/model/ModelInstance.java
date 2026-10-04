package zombie.core.skinnedmodel.model;

public class ModelInstance {
    public float targetDepth;
    public Model model;
    public zombie.core.textures.Texture tex;
    public float tintR, tintG, tintB;
    public zombie.scripting.objects.ModelScript modelScript;

    public static final class EffectLight {
        public float x, y, z;
        public float r, g, b;
        public int radius;
    }
}
