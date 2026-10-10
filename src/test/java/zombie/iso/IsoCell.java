package zombie.iso;

import java.util.HashMap;

/** The tests' stand-in for the loaded map: the squares a test has put there. See zombie.core.properties.PropertyContainer. */
public final class IsoCell {
    private final HashMap<Long, IsoGridSquare> squares = new HashMap<>();

    private static long key(int x, int y, int z) {
        return ((long) x & 0xFFFFFFL) << 32 | ((long) y & 0xFFFFFFL) << 8 | (z & 0xFFL);
    }

    /** The square there, put on the map if it was not. */
    public IsoGridSquare square(int x, int y, int z) {
        return squares.computeIfAbsent(key(x, y, z), unused -> new IsoGridSquare(x, y, z));
    }

    public IsoGridSquare getGridSquare(int x, int y, int z) {
        return squares.get(key(x, y, z));
    }
}
