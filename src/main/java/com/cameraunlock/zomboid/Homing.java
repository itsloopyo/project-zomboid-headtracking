package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import zombie.iso.WorldMarkers;

/**
 * The places the game's Lua points a player to (WorldMarkers' homing points: the tutorial's
 * arrows to the next thing to do, a found item while foraging), as the first person picture
 * points to them. The game draws its arrow by where the player stands in its own view from
 * overhead, and turns it the way the place lies in that view: over the picture it was a
 * yellow arrow by the middle of the screen pointing nowhere in particular. The picture
 * points from over the place itself where it is in view, and from the edge of the screen
 * the way to turn where it is not.
 */
final class Homing {
    /** How far over the floor of a place the tip of its pointer is, in metres: over a counter or a crate, and under a door's head. */
    static final float POINTER_HIGH = 1.4f;
    /** How far out across and up the picture, as a share of half of it, a place is still pointed to from over it. */
    private static final float IN_VIEW_ACROSS = 0.9f, IN_VIEW_UP = 0.85f;

    private static Field points;

    private Homing() {}

    /**
     * Where each of a player's homing points is pointed to from, as the eye sees it, or null
     * for none. Logic thread, where the game's Lua adds and removes them.
     *
     * @param floorUp the height of the floor a place on the player's level, by its x and y, has
     */
    static Crosshair.Pointer[] pointers(int playerIndex, ViewCamera.Pose eye, float tanHalf, float aspect, FloorUp floorUp) {
        ArrayList<?> list = pointsOf(playerIndex);
        int count = 0;
        Crosshair.Pointer[] pointers = null;
        for (Object listed : list) {
            WorldMarkers.PlayerHomingPoint point = (WorldMarkers.PlayerHomingPoint) listed;
            if (!point.isActive() || point.isRemoved()) {
                continue;
            }
            float x = point.getX() + 0.5f, y = point.getY() + 0.5f, up = floorUp.at(x, y) + POINTER_HIGH;
            float dx = x - eye.eyeX, dy = y - eye.eyeY, du = up - eye.eyeUp;
            float ahead = dx * eye.forwardX + dy * eye.forwardY + du * eye.forwardUp;
            float across = (dx * eye.rightX + dy * eye.rightY + du * eye.rightUp) / (tanHalf * aspect);
            float over = (dx * eye.upX + dy * eye.upY + du * eye.upUp) / tanHalf;
            boolean inView = ahead > 0f && Math.abs(across) <= IN_VIEW_ACROSS * ahead && Math.abs(over) <= IN_VIEW_UP * ahead;
            if (pointers == null) {
                pointers = new Crosshair.Pointer[list.size()];
            }
            if (inView) {
                pointers[count++] = new Crosshair.Pointer(across / ahead, over / ahead, true, 0f, 0f, point.getR(), point.getG(), point.getB(), point.getA());
            } else if (ahead > 0f) {
                pointers[count++] = new Crosshair.Pointer(0f, 0f, false, across, over, point.getR(), point.getG(), point.getB(), point.getA());
            } else {
                // Behind the eye, up or down the screen says nothing of the way to turn: it points to the side.
                pointers[count++] = new Crosshair.Pointer(0f, 0f, false, across < 0f ? -1f : 1f, 0f, point.getR(), point.getG(), point.getB(), point.getA());
            }
        }
        return count == 0 ? null : Arrays.copyOf(pointers, count);
    }

    /** The height of a floor on the player's level at a place. */
    interface FloorUp {
        float at(float x, float y);
    }

    private static ArrayList<?> pointsOf(int playerIndex) {
        try {
            if (points == null) {
                points = WorldMarkers.class.getDeclaredField(Links.HOMING_POINTS.name());
                points.setAccessible(true);
            }
            return (ArrayList<?>) ((Object[]) points.get(WorldMarkers.instance))[playerIndex];
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("the game's homing points could not be read", failure);
        }
    }
}
