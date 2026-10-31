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
    private zombie.iso.areas.IsoRoom room;
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
        object.square = this;
        objects.add(object);
        return object;
    }

    /** The square beside it that way, if the map has it. */
    public IsoGridSquare getAdjacentSquare(IsoDirections direction) {
        return switch (direction) {
            case N -> cell.getGridSquare(x, y - 1, z);
            case S -> cell.getGridSquare(x, y + 1, z);
            case E -> cell.getGridSquare(x + 1, y, z);
            case W -> cell.getGridSquare(x - 1, y, z);
            default -> throw new IllegalArgumentException("the stand-in finds the squares north, south, east and west of it, and was asked for " + direction);
        };
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

    /** Puts the square in that room, under that room id. */
    public void room(zombie.iso.areas.IsoRoom of, long id) {
        room = of;
        roomID = id;
    }

    public zombie.iso.areas.IsoRoom getRoom() {
        return room;
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
        chunk.cell = cell;
        return chunk;
    }

    /** The type of the first thing on the square that is a stair, or null where none is. */
    public zombie.iso.SpriteDetails.IsoObjectType getStairs() {
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            zombie.iso.SpriteDetails.IsoObjectType type = sprite == null ? null : sprite.getType();
            if (type != null && type.name().startsWith("stairs")) {
                return type;
            }
        }
        return null;
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

    private boolean seen, inReach;
    /** How many times the square was asked whether its player has seen it, and its room checked for being spotted from it, and for which player last. */
    public int seenAsked, roomChecks, roomCheckedFor = -1;

    /** Whether the game has had the square in its player's sight. */
    public void seen(boolean seen) {
        this.seen = seen;
    }

    /** Whether the square is on its player's level and near enough them for the game to spot its room from it: see checkRoomSeen. */
    public void inReach(boolean inReach) {
        this.inReach = inReach;
    }

    /** What the game's light does as it finds a square has just turned seen, which the tests set: see turns. */
    public static java.util.function.Consumer<IsoGridSquare> onTurn = square -> {};
    private boolean turns;

    /** Whether the game's light finds the square has just turned seen the next time it is asked about, and hands it to onTurn. */
    public void turns(boolean turns) {
        this.turns = turns;
    }

    public boolean isSeen(int playerIndex) {
        seenAsked++;
        if (turns && seen) {
            turns = false;
            onTurn.accept(this);
        }
        return seen;
    }

    public boolean isCouldSee(int playerIndex) {
        return seen;
    }

    /** The game's spotting of the square's room, which goes by the room's level and how far it is from the player: here by inReach. */
    public void checkRoomSeen(int playerIndex) {
        roomChecks++;
        roomCheckedFor = playerIndex;
        if (inReach && room != null) {
            room.getRoomDef().explored = true;
        }
    }
}
