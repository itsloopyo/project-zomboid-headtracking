package com.cameraunlock.zomboid;

import org.joml.Vector3f;
import zombie.GameTime;
import zombie.characters.IsoPlayer;
import zombie.ai.states.FakeDeadZombieState;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoZombie;
import zombie.characters.animals.IsoAnimal;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoWorld;
import zombie.vehicles.BaseVehicle;

/**
 * The vehicles, zombies and animals that the game's own view has no room for, or hides. The game
 * draws the model of a thing that moves only while it stands on the game's screen, which
 * looking down on the map is some thirty tiles ahead of the viewer, and the picture draws the
 * models the game draws. From the eye the road shows for twice that and more, so a vehicle
 * stood on it came into the picture a second ahead of a car at speed, and a zombie beside it.
 * An animal has a model only while it stands on the game's screen, so a cow in a field a few
 * paces ahead was not drawn at all when the game's screen ended short of it.
 * And the game fades a zombie in as the viewer comes to see the square it stands on, which on
 * foot is a zombie made out of nothing a pace away as the viewer rounds a corner.
 *
 * Neither is shown further off than the draw distance (DrawDistance), past which the game's own
 * rules stand, and the picture draws none of them anyway.
 *
 * Where players can fight, a vehicle with another player in it is shown as the game shows it to
 * everyone, since drawing it would show them: see shownWhole.
 */
public final class FarModels {
    final View view;

    FarModels(View view) {
        this.view = view;
    }


    private final Vector3f ahead = new Vector3f();
    private final DrawDistance drawDistance = new DrawDistance();
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
     * eye, which is shown whole: see screenAcross. An animal before the eye is shown whole too. The game draws a model in the light of the square it stands
     * on, which it keeps to hand only for the squares on its screen, so that is fetched here.
     */
    public boolean onScreen(IsoMovingObject moving) {
        if ((moving instanceof IsoZombie || moving instanceof IsoAnimal) && beforeTheEye(moving)) {
            ((IsoGameCharacter) moving).setAlphaAndTarget(1f);
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
     * After the game has chosen which animals have a model, by whether they stand on its
     * screen: an animal before the eye keeps its model, as the game keeps the model of one it
     * chose, except one playing dead, which the game gives none.
     */
    void keepAnimalModels() {
        for (IsoMovingObject moving : IsoWorld.instance.currentCell.getObjectList()) {
            if (moving instanceof IsoAnimal animal && animal.getCurrentSquare() != null && !animal.isCurrentState(FakeDeadZombieState.instance())
                    && beforeTheEye(animal)) {
                animal.setSceneCulled(false);
                if (animal.hasAnimationPlayer()) {
                    animal.getAnimationPlayer().doBlending = true;
                }
            }
        }
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
    public boolean seen(boolean byTheGame, BaseVehicle vehicle) {
        return byTheGame || shownWhole(vehicle);
    }

    /** Whether this is drawn by where it is and not by what the game's own view shows of it. */
    private boolean shownWhole(IsoMovingObject moving) {
        return view.firstPerson.coversTheGamesView() && !(moving instanceof BaseVehicle vehicle && carriesAnotherPlayer(vehicle) && Online.contested());
    }

    private boolean carriesAnotherPlayer(BaseVehicle vehicle) {
        IsoPlayer viewer = view.player();
        for (int seat = 0; seat < vehicle.getMaxPassengers(); seat++) {
            if (vehicle.getCharacter(seat) instanceof IsoPlayer rider && rider != viewer) {
                return true;
            }
        }
        return false;
    }

    private boolean withinSight(IsoMovingObject moving) {
        if (!shownWhole(moving)) {
            return false;
        }
        IsoPlayer viewer = view.player();
        float toX = moving.getX() - viewer.getX(), toY = moving.getY() - viewer.getY();
        float reach = ChunkBuffers.drawnWithin(Quality.sight());
        if (toX * toX + toY * toY > reach * reach) {
            return false;
        }
        BaseVehicle ride = viewer.getVehicle();
        if (ride != null) {
            ride.getForwardVector(ahead);
        }
        drawDistance.take(viewer.getX(), viewer.getY(), ride != null ? ahead.x : 0f, ride != null ? ahead.z : 0f, Online.contested());
        return drawDistance.draws(moving.getX(), moving.getY());
    }

    /**
     * Whether this stands within the picture's sight and its draw distance where the picture can come to show it.
     * From a vehicle that is the side the vehicle faces: what is behind shows only in a mirror,
     * which takes in less than the game's own screen does. On foot it is all but what is
     * behind the view: see ROUND.
     *
     * Not past the game's own sight for the weather and the hour (GameTime.getViewDist), past
     * which the game's IsoGameCharacter.TestIfSeen counts nobody seen and the game fades a zombie
     * out: there it is left to the game, and off the game's screen it is not drawn at all. See
     * Eyesight.shownWhole.
     */
    private boolean beforeTheEye(IsoMovingObject moving) {
        if (!withinSight(moving)) {
            return false;
        }
        IsoPlayer viewer = view.player();
        float toX = moving.getX() - viewer.getX(), toY = moving.getY() - viewer.getY();
        if (!Eyesight.shownWhole(Eyesight.on(), toX * toX + toY * toY, GameTime.getInstance().getViewDist())) {
            return false;
        }
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
