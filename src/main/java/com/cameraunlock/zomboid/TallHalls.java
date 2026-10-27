package com.cameraunlock.zomboid;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

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

    /**
     * Whether a square beside this one on level z, the eight round it, is in a room and has no floor. Not one the stairs
     * come up through: an upper room has no floor there either, and the squares beside its stairwell are no hall.
     */
    static boolean besideHallOver(IsoCell cell, int x, int y, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                IsoGridSquare beside = cell.getGridSquare(x + dx, y + dy, z);
                if ((dx != 0 || dy != 0) && beside != null && beside.getRoomID() != -1L && beside.getFloor() == null
                        && !holdsStairs(cell.getGridSquare(x + dx, y + dy, z - 1))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean holdsStairs(IsoGridSquare square) {
        if (square == null) {
            return false;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && Terrain.isStair(sprite.getType())) {
                return true;
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
     * The room a square is drawn and seen as: for the upper part of a tall hall and a row left
     * out of it, the room of the hall's floor (floorOf), so that its walls, its ceiling and
     * what hangs in it are drawn whenever the hall is. RoomSight.OUTSIDE for no square.
     */
    static long roomOf(IsoCell cell, int x, int y, int z) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        if (square == null) {
            return RoomSight.OUTSIDE;
        }
        IsoGridSquare hall = floorOf(cell, x, y, z);
        return (hall != null ? hall : square).getRoomID();
    }

    /**
     * The floored square of the hall a square is in the upper part of: one of the upper room,
     * with no floor and a room under it, or a row left out of it, down through every such level
     * to the hall's floor. For a square the stairs come up through, which is no hall, a floored
     * square of the room beside it when the map left it in no room (besideStairwell). Null for
     * any other square.
     */
    static IsoGridSquare floorOf(IsoCell cell, int x, int y, int z) {
        IsoGridSquare square = cell.getGridSquare(x, y, z);
        if (square == null || square.getFloor() != null) {
            return null;
        }
        if (holdsStairs(cell.getGridSquare(x, y, z - 1)) && !besideHallOver(cell, x, y, z)) {
            return square.getRoomID() != -1L ? null : besideStairwell(cell, x, y, z);
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

    /**
     * A floored square in a room beside the stairwell a square is in: across the squares the
     * stairs come up through, nearest first. The map leaves some of a stairwell in no room, and
     * read as it has it, that part is open air with no ceiling over it, and the boards of the
     * roof show at the top of the stairs (4671,8600, level 1). The rest of a stairwell is in the
     * room upstairs and is that room's, not the hall's under it, as the squares beside it are no
     * hall (besideHallOver). Null for a stairwell with no such square beside it.
     */
    private static IsoGridSquare besideStairwell(IsoCell cell, int x, int y, int z) {
        ArrayDeque<int[]> open = new ArrayDeque<>();
        Set<Long> walked = new HashSet<>();
        open.add(new int[] {x, y});
        walked.add(((long) x << 32) ^ (y & 0xffffffffL));
        while (!open.isEmpty() && walked.size() <= STAIRWELL_MOST) {
            int[] at = open.poll();
            for (int[] step : STEPS) {
                int nx = at[0] + step[0], ny = at[1] + step[1];
                if (!walked.add(((long) nx << 32) ^ (ny & 0xffffffffL))) {
                    continue;
                }
                IsoGridSquare beside = cell.getGridSquare(nx, ny, z);
                if (beside == null) {
                    continue;
                }
                if (beside.getFloor() != null) {
                    if (beside.getRoomID() != -1L) {
                        return beside;
                    }
                } else if (holdsStairs(cell.getGridSquare(nx, ny, z - 1))) {
                    open.add(new int[] {nx, ny});
                }
            }
        }
        return null;
    }

    /** The most squares a stairwell is walked across for a room beside it, and the four ways across. */
    private static final int STAIRWELL_MOST = 32;
    private static final int[][] STEPS = {{0, -1}, {-1, 0}, {1, 0}, {0, 1}};
}
