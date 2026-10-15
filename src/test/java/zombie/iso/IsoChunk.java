package zombie.iso;

/** The tests' stand-in for a chunk of the loaded map, which a frame of the mod's picture keeps the ones round the chunk it reads of. No test makes one: see zombie.core.properties.PropertyContainer. */
public final class IsoChunk {
    public int wx;
    public int wy;
    /** False while the map is still filling the chunk: see IsoGridSquare.filling. */
    public boolean loaded;

    public int getMaxLevel() {
        return 0;
    }
}
