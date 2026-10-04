package zombie.core;

public final class Core {
    public static int tileScale;
    public final zombie.core.opengl.MatrixStack projectionMatrixStack = null;
    public final zombie.core.opengl.MatrixStack modelViewMatrixStack = null;

    public static Core getInstance() { throw new UnsupportedOperationException("reference stub"); }
    public float getZoom(int playerIndex) { throw new UnsupportedOperationException("reference stub"); }
    public KeyBinding getKeyBinding(String name) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isToggleToAim() { throw new UnsupportedOperationException("reference stub"); }

    public record KeyBinding(String name, int keyValue, int altKey, boolean shift, boolean ctrl, boolean alt) {}
}
