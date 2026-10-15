package com.cameraunlock.zomboid;

import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;

/**
 * The game's own burning of a square's walls (IsoGridSquare.BurnWalls), as it lays out a building
 * the map has as burnt down when the building's chunk loads. It goes through the square's objects
 * and takes each burnt one away, and after taking one away it looks at the same place in the list
 * again. RemoveTileObject leaves a door, a window or anything else that stands on several squares
 * where it is when the part of it on a neighbouring chunk is not loaded yet, so the loop looked at
 * the same object for ever and the game stopped on the spot: three times on the rig, once while
 * driving past such a building and twice after a teleport, each for as long as the game was left.
 */
public final class BurntBuildings {
    private static boolean said;

    private BurntBuildings() {}

    /**
     * In place of the RemoveTileObject calls in BurnWalls. Called from the game's code. What the
     * game would not take away is taken away from this square alone, the way the game takes away
     * each part of such an object, and as it takes anything away from a chunk still loading: the
     * part of it on the chunk not loaded yet burns when that chunk loads.
     */
    public static int takenAway(IsoGridSquare square, IsoObject object) {
        int index = square.RemoveTileObject(object);
        if (square.getObjects().contains(object)) {
            index = square.RemoveTileObject(object, false);
            if (square.getObjects().contains(object)) {
                throw new IllegalStateException("a burnt building's " + object.getClass().getSimpleName() + " at " + square.getX() + ", " + square.getY() + ", "
                        + square.getZ() + " is still on its square after the game's own removal of it from that square alone");
            }
            if (!said) {
                said = true;
                Log.info("a burnt building's " + object.getClass().getSimpleName() + " at " + square.getX() + ", " + square.getY() + ", " + square.getZ()
                        + " stands on squares not all loaded: the game would not take it away and would have looked at it for ever, so it was taken away from that square alone (said once)");
            }
        }
        return index;
    }
}
