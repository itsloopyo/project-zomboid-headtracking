package com.cameraunlock.zomboid;

/**
 * Which people the eye's picture, and a scope's, can leave out because no line of sight can
 * reach them: one standing in a room the picture has not seen into for half a second
 * (WorldScene.roomsShown), and not beside an opening into a region it has. Who stands in a room
 * the picture will soon see into is still given a model: see WorldScene.roomsMayShow. Out of doors, and
 * beside a doorway or a window, a person is always drawn: a body leans out through an opening
 * further than the square it stands on, and the walls between the eye and someone in the
 * street are not known exactly enough to hide them by.
 *
 * Filled on the logic thread with each frame and read on the render thread as that frame is
 * drawn.
 */
final class FigureRooms {
    private final RoomSight.Seen rooms = new RoomSight.Seen();
    /** The squares on the far side of an opening from a region whose contents are built: see fill. */
    private final RoomSight.Seen beside = new RoomSight.Seen();

    /** What the last fill was from: the rooms' changes and the openings' filling, the same of which make the same sets. */
    private RoomSight.Kept filledFrom;
    private RoomSight.Openings filledThrough;
    private int filledChanges, filledFilling;

    /**
     * Takes the rooms whose contents are built and the squares beside the openings into them.
     * Reading every opening in reach was most of the walk's time on every frame, a tower's
     * eleven thousand of them: from the same rooms and openings it is not read again.
     */
    void fill(RoomSight.Kept built, RoomSight.Openings openings) {
        if (built == filledFrom && openings == filledThrough && built.changes == filledChanges && openings.filling == filledFilling) {
            return;
        }
        filledFrom = built;
        filledThrough = openings;
        filledChanges = built.changes;
        filledFilling = openings.filling;
        rooms.clear();
        beside.clear();
        built.copyTo(rooms);
        for (int i = 0; i < openings.count; i++) {
            boolean here = reached(openings.here[i]), there = reached(openings.there[i]);
            if (here == there) {
                continue;
            }
            int x = openings.x[i], y = openings.y[i], z = openings.z[i], kind = openings.kind[i];
            if (here) {
                beside.add(key(kind == RoomSight.WEST ? x - 1 : x, kind == RoomSight.NORTH ? y - 1 : y, kind == RoomSight.UP ? z + 1 : z));
            } else {
                beside.add(key(x, y, z));
            }
        }
    }

    private boolean reached(long region) {
        return region == RoomSight.OUTSIDE || rooms.contains(region);
    }

    /**
     * Whether a person standing on a square in a room is left out: the room is not built and no
     * square round theirs is beside an opening into a region that is.
     */
    boolean leftOut(long room, int x, int y, int z) {
        if (reached(room)) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (beside.contains(key(x + dx, y + dy, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Dev only: whether a room's contents are built. */
    boolean built(long room) {
        return rooms.contains(room);
    }

    int rooms() {
        return rooms.size();
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + (1 << 20)) << 36) | ((long) (y + (1 << 20)) << 12) | (z + 64);
    }
}
