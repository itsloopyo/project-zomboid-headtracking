package zombie.iso;

import java.util.ArrayList;

public final class RoomDef {
    public int level;
    public boolean userDefined;
    public boolean explored;
    public final ArrayList<RoomRect> rects = null;

    public BuildingDef getBuilding() { throw new UnsupportedOperationException("reference stub"); }
    public int getArea() { throw new UnsupportedOperationException("reference stub"); }
    public String getName() { throw new UnsupportedOperationException("reference stub"); }

    public static final class RoomRect {
        public int x;
        public int y;
        public int w;
        public int h;
    }
}
