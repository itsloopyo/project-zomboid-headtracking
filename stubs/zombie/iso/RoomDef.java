package zombie.iso;

import java.util.ArrayList;

public final class RoomDef {
    public int level;
    public final ArrayList<RoomRect> rects = null;

    public static final class RoomRect {
        public int x;
        public int y;
        public int w;
        public int h;
    }
}
