package com.cameraunlock.zomboid;

import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;

/**
 * The rows the map leaves out of a tall hall's upper room. A hall two floors high is marked by
 * a room with no floor on the level over it, and that room is often laid in rectangles with
 * rows left out between them, under its columns and along its walls. A row left out has no
 * floor and is in no room, so read as the map has it, it is open air in the middle of the
 * hall: no ceiling is hung over it, and the room beyond it, a balcony over the hall, is not
 * seen from the hall through it. Such a square is the hall's.
 */
final class TallHalls {
    private TallHalls() {}

    /** Whether a square beside this one on level z, the eight round it, is in a room and has no floor. */
    static boolean besideHallOver(IsoCell cell, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                IsoGridSquare beside = cell.getGridSquare(x + dx, y + dy, z);
                if ((dx != 0 || dy != 0) && beside != null && beside.getRoomID() != -1L && beside.getFloor() == null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The square of the room under a square that is a row left out of a tall hall's upper room:
     * one with no floor, in no room, beside a square of that room, and over a square of a room
     * or over another such row. Null for any other square.
     */
    static IsoGridSquare under(IsoCell cell, int x, int y, int z) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        if (square == null || square.getRoomID() != -1L || square.getFloor() != null || !besideHallOver(cell, x, y, z)) {
            return null;
        }
        IsoGridSquare below = cell.getGridSquare(x, y, z - 1);
        if (below == null) {
            return null;
        }
        return below.getRoomID() != -1L ? below : under(cell, x, y, z - 1);
    }

    /**
     * The floored square of the hall a square is in the upper part of: one of the upper room,
     * with no floor and a room under it, or a row left out of it, down through every such level
     * to the hall's floor. Null for any other square.
     */
    static IsoGridSquare floorOf(IsoCell cell, int x, int y, int z) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        if (square == null || square.getFloor() != null) {
            return null;
        }
        IsoGridSquare below = square.getRoomID() != -1L ? cell.getGridSquare(x, y, z - 1) : under(cell, x, y, z);
        if (below == null || below.getRoomID() == -1L) {
            return null;
        }
        if (below.getFloor() != null) {
            return below;
        }
        return floorOf(cell, x, y, below.getZ());
    }
}
