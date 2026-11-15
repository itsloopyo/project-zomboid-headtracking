package zombie.ui;

public interface UIElementInterface {
    Boolean isVisible();
    Double getY();
    boolean isWantKeyEvents();
    boolean isForceCursorVisible();
    void onMouseButtonDownOutside(int button, double x, double y);
    boolean onConsumeMouseButtonDown(int button, double x, double y);
    void onMouseButtonUpOutside(int button, double x, double y);
    boolean onConsumeMouseButtonUp(int button, double x, double y);
    boolean isOverElement(double x, double y);
    Boolean onConsumeMouseMove(double dx, double dy, double x, double y);
    void onExtendMouseMoveOutside(double dx, double dy, double x, double y);
    Boolean isPointOver(double x, double y);
}
