package zombie.iso;

import java.util.HashMap;

/** The tests' stand-in for the loaded map: the squares a test has put there. See zombie.core.properties.PropertyContainer. */
public final class IsoCell {
    private final HashMap<Long, IsoGridSquare> squares = new HashMap<>();
    private int lowest, highest;

    private static long key(int x, int y, int z) {
        return ((long) x & 0xFFFFFFL) << 32 | ((long) y & 0xFFFFFFL) << 8 | (z & 0xFFL);
    }

    /** The square there, put on the map if it was not. */
    public IsoGridSquare square(int x, int y, int z) {
        lowest = Math.min(lowest, z);
        highest = Math.max(highest, z);
        return squares.computeIfAbsent(key(x, y, z), unused -> new IsoGridSquare(this, x, y, z));
    }

    /** The lowest and the highest level a square has been put on, and 0: every chunk of a test has those levels. */
    int lowest() {
        return lowest;
    }

    int highest() {
        return highest;
    }

    public IsoGridSquare getGridSquare(int x, int y, int z) {
        return squares.get(key(x, y, z));
    }
}
