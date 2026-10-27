package zombie.core.skinnedmodel.advancedanimation;

public final class AnimatedModel {
    public zombie.core.skinnedmodel.animation.AnimationPlayer getAnimationPlayer() { throw new UnsupportedOperationException("reference stub"); }
    public float getScale() { throw new UnsupportedOperationException("reference stub"); }

    private final StateInfo[] stateInfos = null;

    public static final class StateInfo {
        public final java.util.ArrayList<AnimatedModelInstanceRenderData> instData = null;
    }

    public static class AnimatedModelInstanceRenderData {
        public zombie.core.skinnedmodel.model.ModelInstance modelInstance;
        public java.nio.FloatBuffer matrixPalette;
        public final org.joml.Matrix4f xfrm = null;
        public boolean ignoreLighting;
    }
}
