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
 * Other players are drawn the same way where players cannot fight, alone in split screen and on a
 * server without PvP: the game draws one only on its screen, which in split screen is half as wide
 * again, and fades one its own sight from above does not reach. Where players can fight, other
 * players, and a vehicle with another player in it, are shown as the game shows them to everyone:
 * see shownWhole.
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
    /** How near the viewer on foot a zombie is shown whole whichever way the view faces, in tiles. */
    private static final float MADE_ALL_ROUND = 24f;
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
        culledZombie = zombie;
        culled = inARoomNotSeen(zombie);
        if (culled) {
            return OFF_THE_SCREEN;
        }
        if (!beforeTheEye(zombie)) {
            return zombie.getScreenProperX(playerIndex);
        }
        zombie.setAlphaAndTarget(1f);
        return 0;
    }

    public int screenDown(IsoZombie zombie, int playerIndex) {
        if (zombie == culledZombie ? culled : inARoomNotSeen(zombie)) {
            return OFF_THE_SCREEN;
        }
        return beforeTheEye(zombie) ? 0 : zombie.getScreenProperY(playerIndex);
    }

    /**
     * Where a zombie the picture leaves out is said to stand on the game's screen: far enough
     * off it that the game gives it no model, and so does not pose it every tick.
     */
    private static final int OFF_THE_SCREEN = -100_000;
    /** The zombie screenAcross was last asked about and whether it was left out, for screenDown, which the game asks next. */
    private IsoZombie culledZombie;
    private boolean culled;

    /**
     * Whether a zombie stands in a room no line of sight reaches, nor could if every shut door
     * and drawn curtain in reach were opened, and not beside an opening into one that could be
     * seen: see WorldScene.roomsMayShow. It is given its model again as the eye comes near
     * enough to see into its room, before it can be seen. Under the picture only: where players
     * fight, zombies are drawn as offline, so the same rule holds there.
     */
    private boolean inARoomNotSeen(IsoZombie zombie) {
        FigureRooms rooms = view.worldScene.roomsForModels;
        if (rooms == null || !shownWhole(zombie)) {
            return false;
        }
        IsoGridSquare square = zombie.getCurrentSquare();
        return square != null && rooms.leftOut(square.getRoomID(), square.getX(), square.getY(), square.getZ());
    }

    /**
     * After the game has chosen which animals have a model, by whether they stand on its
     * screen: an animal before the eye keeps its model, as the game keeps the model of one it
     * chose, except one playing dead, which the game gives none. Only under the picture: from
     * the game's own view no animal is before the eye (withinSight).
     */
    void keepAnimalModels() {
        if (!view.firstPerson.coversTheGamesView()) {
            return;
        }
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

    /**
     * In place of the game's asking whether another player is on its screen as it draws them, on
     * its logic thread. Where players cannot fight, a player before the eye is drawn as a zombie
     * there is (Online.drawnAsFarAsZombies), in full, and in the light of the square they stand
     * on, which the game keeps to hand only for the squares on its screen. The game has asked
     * already that they stand on a square.
     */
    public boolean playerOnScreen(IsoPlayer player) {
        if (!playerDrawnWhole(player)) {
            return player.isOnScreen();
        }
        player.setAlphaAndTarget(1f);
        player.getCurrentSquare().cacheLightInfo();
        return true;
    }

    /**
     * Whether another player is drawn whole however the game's own sight for players goes: the
     * fade it gives one out of its cone or behind something, and on a server the one it gives
     * one behind the viewer or sneaking, are its view from above.
     */
    public boolean playerDrawnWhole(IsoPlayer player) {
        return player != view.player() && beforeTheEye(player);
    }

    /** Whether this is drawn by where it is and not by what the game's own view shows of it. */
    private boolean shownWhole(IsoMovingObject moving) {
        if (!view.firstPerson.coversTheGamesView()) {
            return false;
        }
        if (moving instanceof BaseVehicle vehicle) {
            return !(carriesAnotherPlayer(vehicle) && Online.contested());
        }
        return !(moving instanceof IsoPlayer player) || moving instanceof IsoAnimal || Online.drawnAsFarAsZombies(player.isInvisible());
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
        // Near the viewer, behind as well: the game makes a zombie's model and composes its clothes the first time it
        // is shown, and a turn to a crowd that had none did all of it in one frame of 130 to 270 ms. Within this reach
        // that is done as each comes near instead, and a turn finds them made.
        if (toX * toX + toY * toY < MADE_ALL_ROUND * MADE_ALL_ROUND) {
            return true;
        }
        view.firstPerson.facing(faces);
        float level = (float) Math.hypot(faces[0], faces[1]);
        return level < LEVEL_LEAST || toX * faces[0] + toY * faces[1] > ROUND * level * (float) Math.hypot(toX, toY);
    }
}
