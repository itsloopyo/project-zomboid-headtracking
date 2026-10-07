package com.cameraunlock.zomboid;

import org.joml.Vector3f;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.iso.IsoMovingObject;
import zombie.vehicles.BaseVehicle;

/**
 * The vehicles, and from a vehicle the zombies, that the game's own view has no room for. The
 * game draws the model of a thing that moves only while it stands on the game's screen, which
 * looking down on the map is some thirty tiles ahead of the viewer, and the picture draws the
 * models the game draws. From the eye the road shows for twice that and more, so a vehicle
 * stood on it came into the picture a second ahead of a car at speed, and a zombie beside it.
 *
 * Where players can fight, all of it is shown as the game shows it to everyone.
 */
public final class FarModels {
    private FarModels() {}

    private static final Vector3f ahead = new Vector3f();

    /**
     * In place of the game's asking whether a thing that moves is on its screen, as it goes
     * through them to draw them, on its logic thread. Under the picture a vehicle within the
     * picture's sight is, wherever the game's own screen ends, and so is a zombie ahead of the
     * vehicle the viewer rides in, which is shown whole: see screenAcross. The game draws a model in the light of the square it stands
     * on, which it keeps to hand only for the squares on its screen, so that is fetched here.
     */
    public static boolean onScreen(IsoMovingObject moving) {
        if (moving instanceof IsoZombie zombie && aheadOfTheRide(zombie)) {
            zombie.setAlphaAndTarget(1f);
        } else if (moving.isOnScreen()) {
            return true;
        } else if (!(moving instanceof BaseVehicle && withinSight(moving))) {
            return false;
        }
        moving.getCurrentSquare().cacheLightInfo();
        return true;
    }

    /**
     * In place of the game's asking where on its screen a zombie is, across and down, as it
     * chooses which zombies have a model: one more than a hundred pixels off the screen has
     * none, and nothing to draw. A zombie ahead of the vehicle the viewer rides in is answered
     * for as if it stood at the screen's corner, and is shown whole.
     *
     * The game fades a zombie out while the viewer cannot see the square it stands on, which
     * it works out square by square across the map: a row of trees hides what stands behind
     * it, and from a car at speed the answer for a square far ahead comes and goes. Left to
     * that, zombies that showed far down the road faded out as the car came up to them (27 to
     * 78 tiles off at 100 km/h) and came back beside it. From a vehicle the picture hides a
     * zombie ahead by what stands between it and the eye, and by nothing else.
     */
    public static int screenAcross(IsoZombie zombie, int playerIndex) {
        if (!aheadOfTheRide(zombie)) {
            return zombie.getScreenProperX(playerIndex);
        }
        zombie.setAlphaAndTarget(1f);
        return 0;
    }

    public static int screenDown(IsoZombie zombie, int playerIndex) {
        return aheadOfTheRide(zombie) ? 0 : zombie.getScreenProperY(playerIndex);
    }

    /**
     * What the game goes by as it draws a vehicle, each passed through here: whether the viewer
     * has ever seen the square it stands on, without which it draws none, and whether the
     * viewer could see any square it stands on, by which it fades it in and out. Looking down
     * on the map, ground not seen yet is dark. From the eye it is the roadside ahead, there in
     * the picture with everything on it but the vehicle, which came in when the game's own
     * sight reached it, 36 to 56 tiles off at 107 km/h. Under the picture what stands between
     * the eye and a vehicle hides it, and nothing else does.
     */
    public static boolean seen(boolean byTheGame) {
        return byTheGame || shownWhole();
    }

    /** Whether vehicles are drawn by where they are and not by what the game's own view shows of them. */
    private static boolean shownWhole() {
        return FirstPerson.coversTheGamesView() && !Online.contested();
    }

    private static boolean withinSight(IsoMovingObject moving) {
        if (!shownWhole()) {
            return false;
        }
        IsoPlayer viewer = IsoPlayer.getInstance();
        float toX = moving.getX() - viewer.getX(), toY = moving.getY() - viewer.getY();
        float reach = ChunkBuffers.drawnWithin(Quality.sight());
        return toX * toX + toY * toY <= reach * reach;
    }

    /**
     * Whether the viewer rides in a vehicle and this stands within the picture's sight on the
     * side the vehicle faces. What is behind shows only in a mirror, which takes in less than
     * the game's own screen does.
     */
    private static boolean aheadOfTheRide(IsoMovingObject moving) {
        if (!withinSight(moving)) {
            return false;
        }
        IsoPlayer viewer = IsoPlayer.getInstance();
        BaseVehicle ride = viewer.getVehicle();
        if (ride == null) {
            return false;
        }
        ride.getForwardVector(ahead);
        return (moving.getX() - viewer.getX()) * ahead.x + (moving.getY() - viewer.getY()) * ahead.z > 0f;
    }
}
