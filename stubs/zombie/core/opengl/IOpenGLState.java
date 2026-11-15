package zombie.core.opengl;

public abstract class IOpenGLState<T extends IOpenGLState.Value> {
    protected final T currentValue = null;

    public interface Value {}
}
