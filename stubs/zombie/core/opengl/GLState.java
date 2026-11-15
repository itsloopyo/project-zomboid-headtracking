package zombie.core.opengl;

public final class GLState {
    public static final CBlendFuncSeparate BlendFuncSeparate = null;

    public abstract static class Base4Ints extends IOpenGLState<C4IntsValue> {}

    public static final class CBlendFuncSeparate extends Base4Ints {}

    public static final class C4IntsValue implements IOpenGLState.Value {
        int a;
        int b;
        int c;
        int d;
    }
}
