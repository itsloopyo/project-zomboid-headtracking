package com.cameraunlock.zomboid;

/**
 * Dev only: the rooms an eye sees into, found the slow way, by following a line from the eye
 * through each of a grid of points of its picture across the map square by square and level
 * by level until something stops it, as RoomSight.Ground says what does. Every room a line
 * passes through is seen. RoomSight's walk is held to it: a room the lines find and the walk
 * does not is one the picture would leave out while the eye sees into it.
 *
 * Lines between the points of the grid are not followed, so a room seen only through a gap
 * narrower than the grid is spaced at its distance can be missed here.
 *
 * A line from the open air into a room more than RoomSight.ABOVE_FROM_OUTSIDE storeys over the
 * eye's ends there, as the walk's does: such a window shows its blind, not the room.
 */
final class RoomRays {
    private RoomRays() {}

    /**
     * Adds to seen every room a line from the eye through a point of an across by up grid over
     * its picture reaches within reach tiles, and returns how many lines were followed.
     */
    static int trace(RoomSight.Ground ground, ViewCamera.Pose eye, float tanAcross, float tanUp, int across, int up, float reach, int startLevel, RoomSight.Seen seen) {
        for (int i = 0; i < across; i++) {
            for (int j = 0; j < up; j++) {
                float a = ((i + 0.5f) / across * 2f - 1f) * tanAcross, b = ((j + 0.5f) / up * 2f - 1f) * tanUp;
                float dx = eye.forwardX + a * eye.rightX + b * eye.upX, dy = eye.forwardY + a * eye.rightY + b * eye.upY, du = eye.forwardUp + a * eye.rightUp + b * eye.upUp;
                float length = (float) Math.sqrt(dx * dx + dy * dy + du * du);
                follow(ground, eye.eyeX, eye.eyeY, eye.eyeUp, dx / length, dy / length, du / length, reach, startLevel, seen);
            }
        }
        return across * up;
    }

    /** Dev only: told each opening a line passes from one region into another, as RoomSight.Openings keeps it, with the region it comes from. */
    interface Crossed {
        void crossed(int x, int y, int z, int kind, long from, long to, float along);
    }

    /**
     * Dev only: the first line of the grid trace follows that reaches a room, as the point of the
     * picture it goes through and each opening it passes from one region into another, each
     * also told to crossed, or null where none does.
     */
    static String pathTo(RoomSight.Ground ground, ViewCamera.Pose eye, float tanAcross, float tanUp, int across, int up, float reach, int startLevel, long room, Crossed crossed) {
        RoomSight.Seen seen = new RoomSight.Seen();
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < across; i++) {
            for (int j = 0; j < up; j++) {
                float a = ((i + 0.5f) / across * 2f - 1f) * tanAcross, b = ((j + 0.5f) / up * 2f - 1f) * tanUp;
                float dx = eye.forwardX + a * eye.rightX + b * eye.upX, dy = eye.forwardY + a * eye.rightY + b * eye.upY, du = eye.forwardUp + a * eye.rightUp + b * eye.upUp;
                float length = (float) Math.sqrt(dx * dx + dy * dy + du * du);
                seen.clear();
                follow(ground, eye.eyeX, eye.eyeY, eye.eyeUp, dx / length, dy / length, du / length, reach, startLevel, seen, null);
                if (seen.contains(room)) {
                    path.append(String.format(java.util.Locale.ROOT, "through (%.2f, %.2f) of the picture:", a / tanAcross, b / tanUp));
                    follow(ground, eye.eyeX, eye.eyeY, eye.eyeUp, dx / length, dy / length, du / length, reach, startLevel, seen, (x, y, z, kind, from, to, along) -> {
                        path.append(String.format(java.util.Locale.ROOT, " | %.1f tiles on, %d,%d,%d %s, %d to %d", along, x, y, z, kind == RoomSight.NORTH ? "north" : kind == RoomSight.WEST ? "west" : "up", from, to));
                        crossed.crossed(x, y, z, kind, from, to, along);
                    });
                    return path.toString();
                }
            }
        }
        return null;
    }

    /** Follows one line from a point on a level, a unit direction, and adds the rooms it passes through. */
    static void follow(RoomSight.Ground ground, float x, float y, float up, float dx, float dy, float du, float reach, int level, RoomSight.Seen seen) {
        follow(ground, x, y, up, dx, dy, du, reach, level, seen, null);
    }

    private static void follow(RoomSight.Ground ground, float x, float y, float up, float dx, float dy, float du, float reach, int level, RoomSight.Seen seen, Crossed path) {
        int cellX = (int) Math.floor(x), cellY = (int) Math.floor(y), z = level;
        int stepX = dx > 0f ? 1 : -1, stepY = dy > 0f ? 1 : -1;
        float nextX = dx == 0f ? Float.POSITIVE_INFINITY : ((dx > 0f ? cellX + 1 : cellX) - x) / dx;
        float nextY = dy == 0f ? Float.POSITIVE_INFINITY : ((dy > 0f ? cellY + 1 : cellY) - y) / dy;
        float perX = dx == 0f ? Float.POSITIVE_INFINITY : Math.abs(1f / dx), perY = dy == 0f ? Float.POSITIVE_INFINITY : Math.abs(1f / dy);
        float at = 0f;
        note(ground.region(cellX, cellY, z), seen);
        while (at < reach) {
            float leave = Math.min(Math.min(nextX, nextY), reach);
            // Within this square the line may pass through its ceiling or its floor before it leaves.
            float ceiling = ground.floorUp(cellX + 0.5f, cellY + 0.5f, z + 1), floor = ground.floorUp(cellX + 0.5f, cellY + 0.5f, z);
            float upAtLeave = up + du * leave;
            if (du > 0f && upAtLeave > ceiling) {
                at = (ceiling - up) / du;
                if (ground.floored(cellX, cellY, z + 1)) {
                    return;
                }
                z++;
                if (blinded(ground, cellX, cellY, z - 1, z, level)) {
                    return;
                }
                note(ground.region(cellX, cellY, z), seen);
                if (path != null && ground.region(cellX, cellY, z) != ground.region(cellX, cellY, z - 1)) {
                    path.crossed(cellX, cellY, z - 1, RoomSight.UP, ground.region(cellX, cellY, z - 1), ground.region(cellX, cellY, z), at);
                }
                continue;
            }
            if (du < 0f && upAtLeave < floor) {
                at = (floor - up) / du;
                if (ground.floored(cellX, cellY, z)) {
                    return;
                }
                z--;
                if (blinded(ground, cellX, cellY, z + 1, z, level)) {
                    return;
                }
                note(ground.region(cellX, cellY, z), seen);
                if (path != null && ground.region(cellX, cellY, z) != ground.region(cellX, cellY, z + 1)) {
                    path.crossed(cellX, cellY, z, RoomSight.UP, ground.region(cellX, cellY, z + 1), ground.region(cellX, cellY, z), at);
                }
                continue;
            }
            if (leave >= reach) {
                return;
            }
            boolean crossX = nextX <= nextY;
            int toX = crossX ? cellX + stepX : cellX, toY = crossX ? cellY : cellY + stepY;
            // The edge crossed is the north or west edge of whichever square is further along +x or +y.
            int edgeX = Math.max(cellX, toX), edgeY = Math.max(cellY, toY);
            if (ground.edge(edgeX, edgeY, z, !crossX) != RoomSight.SEE || ground.region(cellX, cellY, z) == RoomSight.OUTSIDE && ground.region(toX, toY, z) != RoomSight.OUTSIDE
                    && z > level + RoomSight.ABOVE_FROM_OUTSIDE) {
                return;
            }
            at = leave;
            if (path != null && ground.region(toX, toY, z) != ground.region(cellX, cellY, z)) {
                path.crossed(edgeX, edgeY, z, crossX ? RoomSight.WEST : RoomSight.NORTH, ground.region(cellX, cellY, z), ground.region(toX, toY, z), at);
            }
            cellX = toX;
            cellY = toY;
            if (crossX) {
                nextX += perX;
            } else {
                nextY += perY;
            }
            note(ground.region(cellX, cellY, z), seen);
        }
    }

    /** Whether a line from the open air on level from into a room on level to, the level over or under, goes in higher than the walk sees from the open air. */
    private static boolean blinded(RoomSight.Ground ground, int x, int y, int from, int to, int eyeLevel) {
        return ground.region(x, y, from) == RoomSight.OUTSIDE && ground.region(x, y, to) != RoomSight.OUTSIDE && Math.min(from, to) > eyeLevel + RoomSight.ABOVE_FROM_OUTSIDE;
    }

    private static void note(long region, RoomSight.Seen seen) {
        if (region != RoomSight.OUTSIDE) {
            seen.add(region);
        }
    }
}
