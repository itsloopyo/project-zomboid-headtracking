package zombie.iso.areas;

/** The tests' stand-in for a room of the map, which a test puts squares in: see zombie.iso.IsoGridSquare.room. */
public final class IsoRoom {
    private final zombie.iso.RoomDef def;

    public IsoRoom(zombie.iso.RoomDef def) {
        this.def = def;
    }

    public zombie.iso.RoomDef getRoomDef() {
        return def;
    }
}
