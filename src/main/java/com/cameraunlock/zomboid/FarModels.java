package com.cameraunlock.zomboid;

import org.joml.Vector3f;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.vehicles.BaseVehicle;

/**
 * The vehicles and the zombies that the game's own view has no room for, or hides. The game
 * draws the model of a thing that moves only while it stands on the game's screen, which
 * looking down on the map is some thirty tiles ahead of the viewer, and the picture draws the
 * models the game draws. From the eye the road shows for twice that and more, so a vehicle
 * stood on it came into the picture a second ahead of a car at speed, and a zombie beside it.
 * And the game fades a zombie in as the viewer comes to see the square it stands on, which on
 * foot is a zombie made out of nothing a pace away as the viewer rounds a corner.
 *
 * Where players can fight, all of it is shown as the game shows it to everyone.
 */
public final class FarModels {
    final View view;

    FarModels(View view) {
        this.view = view;
    }


    private final Vector3f ahead = new Vector3f();
    private final float[] faces = new float[2];
    /**
     * The cosine of how far round from the way the view faces a zombie is shown whole on foot:
     * 90 degrees. The widest picture ends 69 degrees round, and a zombie is whole for the rest
     * before a turn of the view brings it in, the game giving it its model a tick after it is
     * asked for. At 120 degrees a crowd all round was a third more zombies drawn.
     */
    private static final float ROUND = 0f;
    /** A view that faces along the map by less than this is straight up or down, and whatever stands round the viewer is before it. */
    private static final float LEVEL_LEAST = 0.2f;

    /**
     * In place of the game's asking whether a thing that moves is on its screen, as it goes
     * through them to draw them, on its logic thread. Under the picture a vehicle within the
     * picture's sight is, wherever the game's own screen ends, and so is a zombie before the
     * eye, which is shown whole: see screenAcross. The game draws a model in the light of the square it stands
     * on, which it keeps to hand only for the squares on its screen, so that is fetched here.
     */
    public boolean onScreen(IsoMovingObject moving) {
        if (moving instanceof IsoZombie zombie && beforeTheEye(zombie)) {
            zombie.setAlphaAndTarget(1f);
        } else if (moving.isOnScreen()) {
            return true;
        } else if (!(moving instanceof BaseVehicle && withinSight(moving))) {
            return false;
        }
        IsoGridSquare square = moving.getCurrentSquare();
        if (square == null) {
            return moving.isOnScreen();
        }
        square.cacheLightInfo();
        return true;
    }

    /**
     * In place of the game's asking where on its screen a zombie is, across and down, as it
     * chooses which zombies have a model: one more than a hundred pixels off the screen has
     * none, and nothing to draw. A zombie before the eye is answered for as if it stood at the
     * screen's corner, and is shown whole.
     *
     * The game fades a zombie out while the viewer cannot see the square it stands on, which
     * it works out square by square across the map: a row of trees hides what stands behind
     * it, and from a car at speed the answer for a square far ahead comes and goes. Left to
     * that, zombies that showed far down the road faded out as the car came up to them (27 to
     * 78 tiles off at 100 km/h) and came back beside it. On foot the answer for the square round
     * a corner comes after the viewer has turned it, and the zombie there faded in before their
     * eyes. The picture hides a zombie before the eye by what stands between the two, and by
     * nothing else.
     */
    public int screenAcross(IsoZombie zombie, int playerIndex) {
        if (!beforeTheEye(zombie)) {
            return zombie.getScreenProperX(playerIndex);
        }
        zombie.setAlphaAndTarget(1f);
        return 0;
    }

    public int screenDown(IsoZombie zombie, int playerIndex) {
        return beforeTheEye(zombie) ? 0 : zombie.getScreenProperY(playerIndex);
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
    public boolean seen(boolean byTheGame) {
        return byTheGame || shownWhole();
    }

    /** Whether vehicles are drawn by where they are and not by what the game's own view shows of them. */
    private boolean shownWhole() {
        return view.firstPerson.coversTheGamesView() && !Online.contested();
    }

    private boolean withinSight(IsoMovingObject moving) {
        if (!shownWhole()) {
            return false;
        }
        IsoPlayer viewer = view.player();
        float toX = moving.getX() - viewer.getX(), toY = moving.getY() - viewer.getY();
        float reach = ChunkBuffers.drawnWithin(Quality.sight());
        return toX * toX + toY * toY <= reach * reach;
    }

    /**
     * Whether this stands within the picture's sight where the picture can come to show it.
     * From a vehicle that is the side the vehicle faces: what is behind shows only in a mirror,
     * which takes in less than the game's own screen does. On foot it is all but what is
     * behind the view: see ROUND.
     */
    private boolean beforeTheEye(IsoMovingObject moving) {
        if (!withinSight(moving)) {
            return false;
        }
        IsoPlayer viewer = view.player();
        float toX = moving.getX() - viewer.getX(), toY = moving.getY() - viewer.getY();
        BaseVehicle ride = viewer.getVehicle();
        if (ride != null) {
            ride.getForwardVector(ahead);
            return toX * ahead.x + toY * ahead.z > 0f;
        }
        view.firstPerson.facing(faces);
        float level = (float) Math.hypot(faces[0], faces[1]);
        return level < LEVEL_LEAST || toX * faces[0] + toY * faces[1] > ROUND * level * (float) Math.hypot(toX, toY);
    }
}
