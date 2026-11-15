package zombie.ui;

public class UIElement implements UIElementInterface {
    public Double getX() { throw new UnsupportedOperationException("reference stub"); }
    public void setX(double x) { throw new UnsupportedOperationException("reference stub"); }
    public Double getY() { throw new UnsupportedOperationException("reference stub"); }
    public void setY(double y) { throw new UnsupportedOperationException("reference stub"); }
    public Boolean isVisible() { throw new UnsupportedOperationException("reference stub"); }
    public void setVisible(boolean visible) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isWantKeyEvents() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isForceCursorVisible() { throw new UnsupportedOperationException("reference stub"); }
    public se.krka.kahlua.vm.KahluaTable getTable() { throw new UnsupportedOperationException("reference stub"); }
    public void render() { throw new UnsupportedOperationException("reference stub"); }
    public void onMouseButtonDownOutside(int button, double x, double y) { throw new UnsupportedOperationException("reference stub"); }
    public boolean onConsumeMouseButtonDown(int button, double x, double y) { throw new UnsupportedOperationException("reference stub"); }
    public void onMouseButtonUpOutside(int button, double x, double y) { throw new UnsupportedOperationException("reference stub"); }
    public boolean onConsumeMouseButtonUp(int button, double x, double y) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isOverElement(double x, double y) { throw new UnsupportedOperationException("reference stub"); }
    public Boolean onConsumeMouseMove(double dx, double dy, double x, double y) { throw new UnsupportedOperationException("reference stub"); }
    public void onExtendMouseMoveOutside(double dx, double dy, double x, double y) { throw new UnsupportedOperationException("reference stub"); }
    public Boolean isPointOver(double x, double y) { throw new UnsupportedOperationException("reference stub"); }
    public void DrawItemIcon(zombie.inventory.InventoryItem item, double x, double y, double alpha, double width, double height) { throw new UnsupportedOperationException("reference stub"); }
    public void DrawText(UIFont font, String text, double x, double y, double r, double g, double b, double alpha) { throw new UnsupportedOperationException("reference stub"); }
    public void DrawTextRight(UIFont font, String text, double x, double y, double r, double g, double b, double alpha) { throw new UnsupportedOperationException("reference stub"); }
    public void DrawTextureScaledCol(zombie.core.textures.Texture texture, double x, double y, double width, double height, double r, double g, double b, double alpha) { throw new UnsupportedOperationException("reference stub"); }
}
