package com.cameraunlock.zomboid;

import java.util.List;
import zombie.characters.IsoPlayer;
import zombie.chat.ChatElement;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoCamera;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoTelevision;
import zombie.iso.objects.IsoWaveSignal;
import zombie.vehicles.BaseVehicle;

/**
 * The lines a television, a radio or a car radio shows of what it is playing, put over the set
 * in the first person picture.
 *
 * The game writes them at the set's place in its own view, worked out through the isometric
 * zoom, so under the first person picture they hang wherever that view has the set and slide
 * about as the wheel zooms it. Only that place is changed: what is written, and whether it is
 * written at all, stay the game's.
 *
 * Everything runs on the game's logic thread.
 */
final class SetCaptions {
    private final View view;

    SetCaptions(View view) {
        this.view = view;
    }

    /** How tall a set standing on something is: a television's 0.48 or 0.56, a radio less. */
    private static final float TELEVISION_HIGH = 0.56f, RADIO_HIGH = 0.25f;
    /** How far over the top of a set the lowest line is written. */
    private static final float ABOVE = 0.12f;
    /** How far over a car's floor the lines of a car radio heard from outside are written: just over its roof. */
    private static final float OVER_A_CAR = 1.7f;
    /** How far down the window the lowest line of the radio of the car the viewer sits in is written. */
    private static final float OWN_CAR_DOWN = 0.82f;

    private final float[] ndc = new float[2];

    /** Called in place of the game's writing of a television's or a radio's lines. */
    void writeOverSet(ChatElement lines, int playerIndex, int x, int y, IsoWaveSignal set) {
        if (!view.firstPerson.coversTheGamesView()) {
            lines.renderBatched(playerIndex, x, y);
            return;
        }
        IsoGridSquare square = set.getSquare();
        if (square == null) {
            return;
        }
        float atX = square.getX() + 0.5f, atY = square.getY() + 0.5f;
        writeAt(lines, playerIndex, atX, atY, square.getZ() * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(atX, atY, square.getZ()) + top(set, square) + ABOVE);
    }

    /** Called in place of the game's writing of a car radio's lines. */
    void writeOverCar(ChatElement lines, int playerIndex, int x, int y, BaseVehicle car) {
        if (!view.firstPerson.coversTheGamesView()) {
            lines.renderBatched(playerIndex, x, y);
            return;
        }
        IsoPlayer viewer = view.player();
        if (viewer != null && viewer.getVehicle() == car) {
            // From the seat the car's middle is beside or behind the eye: the lines go low in the middle of the window.
            lines.renderBatched(playerIndex, IsoCamera.getScreenWidth(playerIndex) / 2, Math.round(OWN_CAR_DOWN * IsoCamera.getScreenHeight(playerIndex)));
            return;
        }
        float atX = car.getX(), atY = car.getY(), z = car.getZ();
        writeAt(lines, playerIndex, atX, atY, z * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(atX, atY, z) + OVER_A_CAR);
    }

    /** Writes the lines over a point, or not at all where the point is behind the eye or out of the picture. */
    private void writeAt(ChatElement lines, int playerIndex, float x, float y, float up) {
        if (!view.firstPerson.inPicture(x, y, up, ndc) || Math.abs(ndc[0]) > 1f || Math.abs(ndc[1]) > 1f) {
            return;
        }
        lines.renderBatched(playerIndex, Math.round((ndc[0] * 0.5f + 0.5f) * IsoCamera.getScreenWidth(playerIndex)),
                Math.round((0.5f - ndc[1] * 0.5f) * IsoCamera.getScreenHeight(playerIndex)));
    }

    /**
     * How high over its floor the top of a set is. A set on a table or a chest of drawers stands
     * on the highest surface of the rest of its square, as the game lifts it there
     * (IsoGridSquare.FixStackableObjects); a set that stands on the floor, the antique console,
     * has its own top as its surface.
     */
    private static float top(IsoWaveSignal set, IsoGridSquare square) {
        PropertyContainer own = set.getProperties();
        if (own != null && !own.has("IsTableTop") && set.getRenderYOffset() <= 0f) {
            int surface = Surfaces.surface(own);
            if (surface > 0) {
                return surface / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
            }
        }
        int under = 0;
        List<IsoObject> objects = SquareObjects.of(square);
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            PropertyContainer properties = object.getProperties();
            if (object != set && !(object instanceof IsoWaveSignal) && properties != null && !properties.has("IsTableTop")) {
                under = Math.max(under, Surfaces.surface(properties));
            }
        }
        return under / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT
                + (set instanceof IsoTelevision ? TELEVISION_HIGH : RADIO_HIGH);
    }
}
