package zombie.core.skinnedmodel;

public abstract class ModelCamera {
    public static ModelCamera instance;
    public float useAngle;
    public float x;
    public float y;
    public float z;

    public abstract void Begin();

    public abstract void End();
}
