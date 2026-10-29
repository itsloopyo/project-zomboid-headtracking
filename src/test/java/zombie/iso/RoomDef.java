package zombie.iso;

import java.util.ArrayList;

/** The tests' stand-in for a room's place on the map: its level, its rectangles and its building. See zombie.iso.areas.IsoRoom. */
public final class RoomDef {
    public int level;
    public boolean userDefined;
    public boolean explored;
    public final ArrayList<RoomRect> rects = new ArrayList<>();
    public BuildingDef building;

    public RoomDef(int level) {
        this.level = level;
    }

    public BuildingDef getBuilding() {
        return building;
    }

    public int getArea() {
        throw new UnsupportedOperationException("no test's room has an area");
    }

    public String getName() {
        throw new UnsupportedOperationException("no test's room has a name");
    }

    public static final class RoomRect {
        public int x;
        public int y;
        public int w;
        public int h;

        public RoomRect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
}
