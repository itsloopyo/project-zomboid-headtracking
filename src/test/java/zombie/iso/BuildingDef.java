package zombie.iso;

/** The tests' stand-in for a building's place on the map: its box. See zombie.core.properties.PropertyContainer. */
public final class BuildingDef {
    public long id;
    private final int x, y, w, h;

    public BuildingDef(long id, int x, int y, int w, int h) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getW() {
        return w;
    }

    public int getH() {
        return h;
    }
}
