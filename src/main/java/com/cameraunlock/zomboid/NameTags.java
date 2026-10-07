package com.cameraunlock.zomboid;

import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.chat.ChatElement;
import zombie.iso.IsoCamera;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.IsoObjectPicker;
import zombie.iso.Vector2;
import zombie.ui.TextDrawObject;

/**
 * The names and the speech the game writes over people's heads, which on a server are the
 * other players', put where those heads are in the first person picture.
 *
 * The game works out a place on the screen for each from where the person stands in its own
 * view, and writes the name, the speech and the notes there. Only that place is changed, so
 * whose name is written, and whether the viewer can see them at all, stay the game's to
 * decide. The viewer's own name and speech would be written across the middle of the
 * picture, where the eye is, and are left out: the chat panel has what was said.
 *
 * A server can ask that a name shows only with the pointer on its owner. In first person
 * there is no pointer, and the game is told the pointer is on whoever the middle of the view
 * is on. Whether that player is one the viewer can see is still the game's to say.
 *
 * Everything runs on the game's logic thread.
 */
public final class NameTags {
    /** How far above the feet a name is written: just clear of the head, which stands 1.45 high in the picture (measured against a player 2.6 and 4.4 tiles off). */
    private static final float ABOVE = 1.5f;
    /** Where the game is given for a name that is not in the picture. */
    private static final int NOWHERE = -100_000;
    /** How far to either side of the middle of a person the view may pass and still be on them, how tall they are, and how far off they may be. */
    private static final float BODY_HALF_WIDE = 0.6f, BODY_HIGH = 2f, LOOK_REACH = 40f;

    private static final float[] ndc = new float[2];
    /** The person the game is writing over now is the viewer, seen from their own eye. */
    private static boolean own;
    /** The person the game is writing over now has been given a place in the picture, and how far down the window it is. */
    private static boolean placed;
    private static float placedY;
    /** How far above that place the top of a name is written, in pixels: the height of its one line. */
    private static final float NAME_HIGH = 22f;
    /** The player the middle of the clean view is on, or null. */
    private static IsoPlayer lookedAt;
    private static boolean gapSaid;

    private NameTags() {}

    /**
     * Called where the game has worked out where on the screen to write over a person.
     *
     * @param at the place, in pixels of the window
     */
    public static void place(IsoGameCharacter character, Vector2 at) {
        own = false;
        placed = false;
        if (!FirstPerson.coversTheGamesView()) {
            return;
        }
        IsoPlayer viewer = IsoPlayer.getInstance();
        if (character == viewer) {
            own = true;
            return;
        }
        float x = character.getX(), y = character.getY();
        float up = character.getZ() * ViewCamera.LEVEL_HEIGHT + Terrain.lift(x, y, character.getZ()) + ABOVE;
        if (!FirstPerson.inPicture(x, y, up, ndc)) {
            at.set(NOWHERE, NOWHERE);
            return;
        }
        int index = viewer.getIndex();
        placedY = (0.5f - ndc[1] * 0.5f) * IsoCamera.getScreenHeight(index);
        placed = true;
        at.set((ndc[0] * 0.5f + 0.5f) * IsoCamera.getScreenWidth(index), placedY);
    }

    /**
     * Called in place of the game's writing of a person's name. The game writes it a fixed
     * number of pixels above the place it worked out, which over a person seen small and far
     * off in the picture is a long way over their head: there it is written just above.
     */
    public static void writeName(TextDrawObject name, double x, double y, boolean outlined) {
        if (own) {
            return;
        }
        name.AddBatchedDraw(x, placed ? placedY - NAME_HIGH : y, outlined);
    }

    /** Called in place of the game's writing of what a person has said. */
    public static void writeSpeech(ChatElement speech, int playerIndex, int x, int y, boolean ignoreRadioLines) {
        if (!own) {
            speech.renderBatched(playerIndex, x, y, ignoreRadioLines);
        }
    }

    /**
     * Called in place of the game's asking what its own view has under the pointer, which it
     * does to find whose name to write. The game looks on the squares round the one that
     * answer stands on. Under the first person picture its own view is not drawn and has
     * nothing under the pointer, which is held ahead of the viewer and points at nothing.
     */
    public static IsoObjectPicker.ClickObject underPointer(IsoObjectPicker picker, int x, int y) {
        if (!FirstPerson.coversTheGamesView()) {
            return picker.ContextPick(x, y);
        }
        IsoPlayer on = lookedAt;
        IsoGridSquare square = on == null ? null : on.getCurrentSquare();
        IsoObject floor = square == null ? null : square.getFloor();
        if (floor == null) {
            return null;
        }
        if (looked == null) {
            looked = new IsoObjectPicker.ClickObject();
        }
        looked.tile = floor;
        return looked;
    }

    /** The answer handed back, made when first needed: the class is the game's, and there is none where the mod is tested. */
    private static IsoObjectPicker.ClickObject looked;

    /**
     * Once a frame of the first person view on a server: finds the nearest other player the
     * clean view passes through, feet to head.
     */
    static void look(ViewCamera.Pose clean, IsoCell cell, IsoPlayer viewer) {
        IsoPlayer found = null;
        float nearest = LOOK_REACH;
        for (IsoMovingObject mover : cell.getObjectList()) {
            if (mover instanceof IsoPlayer other && other != viewer) {
                float x = other.getX(), y = other.getY();
                float along = passes(clean, x, y, other.getZ() * ViewCamera.LEVEL_HEIGHT + Terrain.lift(x, y, other.getZ()));
                if (along < nearest) {
                    nearest = along;
                    found = other;
                }
            }
        }
        lookedAt = found;
    }

    /**
     * How far along a view it passes through a person standing at a place, feet to head, or
     * not a number if it does not: it passes to one side, over or under, or they are behind.
     *
     * @param base how high their feet are
     */
    static float passes(ViewCamera.Pose view, float x, float y, float base) {
        float flat = view.forwardX * view.forwardX + view.forwardY * view.forwardY;
        if (flat < 1e-6f) {
            return Float.NaN;
        }
        // Where along the view it is nearest the upright line the person stands on.
        float along = ((x - view.eyeX) * view.forwardX + (y - view.eyeY) * view.forwardY) / flat;
        if (along <= 0f) {
            return Float.NaN;
        }
        float missX = view.eyeX + view.forwardX * along - x, missY = view.eyeY + view.forwardY * along - y;
        float height = view.eyeUp + view.forwardUp * along - base;
        return missX * missX + missY * missY <= BODY_HALF_WIDE * BODY_HALF_WIDE && height >= 0f && height <= BODY_HIGH ? along : Float.NaN;
    }

    /** The view is on nobody: first person is off, or the game is not joined to a server. */
    static void lookAway() {
        lookedAt = null;
        if (looked != null) {
            looked.tile = null;
        }
    }

    /** Says once why names are where the game's own view has them on this build of the game, if they are. */
    static void sayGap() {
        if (!gapSaid) {
            gapSaid = true;
            String gap = Agent.nameTagsGap();
            if (gap != null) {
                Log.info("names over other players are not moved into the first person picture: " + gap);
            }
        }
    }
}
