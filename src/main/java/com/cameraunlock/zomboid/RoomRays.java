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

    /**
     * Dev only: the first line of the grid trace follows that reaches a room, as the point of the
     * picture it goes through and each square and level it passes, or null where none does.
     */
    static String pathTo(RoomSight.Ground ground, ViewCamera.Pose eye, float tanAcross, float tanUp, int across, int up, float reach, int startLevel, long room) {
        RoomSight.Seen seen = new RoomSight.Seen();
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < across; i++) {
            for (int j = 0; j < up; j++) {
                float a = ((i + 0.5f) / across * 2f - 1f) * tanAcross, b = ((j + 0.5f) / up * 2f - 1f) * tanUp;
                float dx = eye.forwardX + a * eye.rightX + b * eye.upX, dy = eye.forwardY + a * eye.rightY + b * eye.upY, du = eye.forwardUp + a * eye.rightUp + b * eye.upUp;
                float length = (float) Math.sqrt(dx * dx + dy * dy + du * du);
                seen.clear();
                path.setLength(0);
                path.append(String.format(java.util.Locale.ROOT, "through (%.2f, %.2f) of the picture:", a / tanAcross, b / tanUp));
                follow(ground, eye.eyeX, eye.eyeY, eye.eyeUp, dx / length, dy / length, du / length, reach, startLevel, seen, path);
                if (seen.contains(room)) {
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

    private static void follow(RoomSight.Ground ground, float x, float y, float up, float dx, float dy, float du, float reach, int level, RoomSight.Seen seen, StringBuilder path) {
        int cellX = (int) Math.floor(x), cellY = (int) Math.floor(y), z = level;
        int stepX = dx > 0f ? 1 : -1, stepY = dy > 0f ? 1 : -1;
        float nextX = dx == 0f ? Float.POSITIVE_INFINITY : ((dx > 0f ? cellX + 1 : cellX) - x) / dx;
        float nextY = dy == 0f ? Float.POSITIVE_INFINITY : ((dy > 0f ? cellY + 1 : cellY) - y) / dy;
        float perX = dx == 0f ? Float.POSITIVE_INFINITY : Math.abs(1f / dx), perY = dy == 0f ? Float.POSITIVE_INFINITY : Math.abs(1f / dy);
        float at = 0f;
        note(ground.region(cellX, cellY, z), seen);
        if (path != null) {
            path.append(' ').append(cellX).append(',').append(cellY).append(',').append(z);
        }
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
                note(ground.region(cellX, cellY, z), seen);
                if (path != null) {
                    path.append(String.format(java.util.Locale.ROOT, " up at %.2f to %d,%d,%d", at, cellX, cellY, z));
                }
                continue;
            }
            if (du < 0f && upAtLeave < floor) {
                at = (floor - up) / du;
                if (ground.floored(cellX, cellY, z)) {
                    return;
                }
                z--;
                note(ground.region(cellX, cellY, z), seen);
                if (path != null) {
                    path.append(String.format(java.util.Locale.ROOT, " down at %.2f to %d,%d,%d", at, cellX, cellY, z));
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
            if (ground.edge(edgeX, edgeY, z, !crossX) != RoomSight.SEE) {
                return;
            }
            at = leave;
            cellX = toX;
            cellY = toY;
            if (crossX) {
                nextX += perX;
            } else {
                nextY += perY;
            }
            note(ground.region(cellX, cellY, z), seen);
            if (path != null) {
                path.append(' ').append(cellX).append(',').append(cellY).append(',').append(z);
            }
        }
    }

    private static void note(long region, RoomSight.Seen seen) {
        if (region != RoomSight.OUTSIDE) {
            seen.add(region);
        }
    }
}
