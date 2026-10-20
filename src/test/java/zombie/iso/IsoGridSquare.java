package zombie.iso;

import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/** The tests' stand-in for a square of the map: what is on it, its room and its floor. See zombie.core.properties.PropertyContainer. */
public final class IsoGridSquare {
    private final int x, y, z;
    private final PZArrayList<IsoObject> objects = new PZArrayList<>();
    private long roomID = -1L;
    private IsoObject floor;
    private boolean filling;
    private zombie.iso.areas.IsoBuilding building;
    private final IsoCell cell;

    IsoGridSquare(IsoCell cell, int x, int y, int z) {
        this.cell = cell;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Puts a thing with that sprite on the square, after what is on it already. */
    public IsoObject add(IsoSprite sprite) {
        IsoObject object = new IsoObject(sprite);
        objects.add(object);
        return object;
    }

    /** Lays a floor of that sprite on the square, under what is on it. */
    public void floor(IsoSprite sprite) {
        floor = add(sprite);
    }

    /** Takes up the square's floor, as over a stairwell or a hall's well. */
    public void unfloor() {
        objects.remove(floor);
        floor = null;
    }

    /** Whether the map is still filling the square's chunk, which the game says on the chunk. */
    public void filling(boolean filling) {
        this.filling = filling;
    }

    public void setRoomID(long roomID) {
        this.roomID = roomID;
    }

    public PZArrayList<IsoObject> getObjects() {
        return objects;
    }

    public long getRoomID() {
        return roomID;
    }

    /** Puts the square in that building. */
    public void building(zombie.iso.areas.IsoBuilding of) {
        building = of;
    }

    public zombie.iso.areas.IsoBuilding getBuilding() {
        return building;
    }

    /** No square of a test is in a room the game has made. */
    public zombie.iso.areas.IsoRoom getRoom() {
        return null;
    }

    public IsoObject getFloor() {
        return floor;
    }

    public boolean isOutside() {
        return roomID == -1L;
    }

    public IsoChunk getChunk() {
        IsoChunk chunk = new IsoChunk();
        chunk.wx = Math.floorDiv(x, 8);
        chunk.wy = Math.floorDiv(y, 8);
        chunk.loaded = !filling;
        chunk.minLevel = cell.lowest();
        chunk.maxLevel = cell.highest();
        return chunk;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }
}
