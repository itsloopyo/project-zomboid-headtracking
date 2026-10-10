package com.cameraunlock.zomboid;

/**
 * Which people the eye's picture, and a scope's, can leave out because no line of sight can
 * reach them: one standing in a room whose contents are not built (RoomSight: no walk round
 * the eye, from a step or two off and through any shut door near it as if open, has found it
 * for a few seconds), and not beside an opening into a region that is. Out of doors, and
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

    /** Whether people are left out by room at all: not where players can fight, where everyone is drawn as the game shows them. */
    static boolean applies(boolean contested) {
        return !contested;
    }

    /** Takes the rooms whose contents are built and the squares beside the openings into them. */
    void fill(RoomSight.Kept built, RoomSight.Openings openings) {
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

    int rooms() {
        return rooms.size();
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + (1 << 20)) << 36) | ((long) (y + (1 << 20)) << 12) | (z + 64);
    }
}
