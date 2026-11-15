package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import zombie.characters.IsoPlayer;
import zombie.chat.ChatElement;
import zombie.core.properties.PropertyContainer;
import zombie.core.textures.Texture;
import zombie.iso.IsoCamera;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoTelevision;
import zombie.iso.objects.IsoWaveSignal;
import zombie.ui.TextDrawObject;
import zombie.ui.UIFont;
import zombie.vehicles.BaseVehicle;

/**
 * The lines a television, a radio, a car radio or a radio in the hands shows of what it is
 * playing, put where they belong in the first person picture.
 *
 * The game writes a set's lines at the set's place in its own view, worked out through the
 * isometric zoom, so under the first person picture they hang wherever that view has the set and
 * slide about as the wheel zooms it. Here they are written over the set as it is drawn, and not
 * at all where the set is behind the eye or out of the picture. What is written stays the game's.
 *
 * A radio the player holds writes its lines into the player's own speech, which NameTags leaves
 * out in first person: they are written low in the middle of the window, as are those of the
 * radio of the car the player sits in, whose middle is beside or behind the eye.
 *
 * Everything runs on the game's logic thread.
 */
final class SetCaptions {
    private final View view;

    SetCaptions(View view) {
        this.view = view;
    }

    /**
     * How tall the mod's models of the sets are: a wide screen 0.56 (a plain set 0.48), a radio's
     * case 0.185, and the top of the antique console that stands on the floor 0.78
     * (tools/models/furniture.py, Furniture.TV_CABINET_TOP).
     */
    static final float TELEVISION_HIGH = 0.56f, RADIO_HIGH = 0.185f, CONSOLE_TOP = 0.78f;
    /** How far over the top of a set the lowest line is written. */
    private static final float ABOVE = 0.12f;
    /** How far over a car's floor the lines of a car radio heard from outside are written: just over its roof. */
    private static final float OVER_A_CAR = 1.7f;
    /** The game's panel of a driver's instruments, which it stands at the bottom of the window as tall as this picture (ISVehicleDashboard). */
    private static final String DASHBOARD = "media/ui/vehicles/dashboard.png";
    /** How many pixels over the dashboard's top the lowest line of a radio written low in the window is. */
    private static final int OVER_THE_DASHBOARD = 8;

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
        PropertyContainer own = set.getProperties();
        float atX = square.getX() + 0.5f, atY = square.getY() + 0.5f;
        float top = top(own != null && own.has("IsTableTop"), own == null ? 0 : Surfaces.surface(own), set.getRenderYOffset(), set instanceof IsoTelevision);
        writeAt(lines, playerIndex, atX, atY, square.getZ() * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(atX, atY, square.getZ()) + top + ABOVE);
    }

    /**
     * How high over its floor the top of a set is drawn. A set that goes on a table is drawn
     * standing at its own sprite's Surface, the height of the table it was painted on, raised by
     * the offset the game gives it on a higher or lower piece (IsoGridSquare.FixStackableObjects),
     * as WorldScene stands its model. Any other set, the antique console, stands on the floor.
     *
     * @param ownSurface    the set's own sprite's Surface, in the game's pixels
     * @param renderYOffset the set's IsoObject.getRenderYOffset, in the same pixels
     */
    static float top(boolean tableTop, int ownSurface, float renderYOffset, boolean television) {
        float foot = ((tableTop ? ownSurface : 0) + renderYOffset) / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
        return foot + (television ? tableTop ? TELEVISION_HIGH : CONSOLE_TOP : RADIO_HIGH);
    }

    /** Called in place of the game's writing of a car radio's lines. */
    void writeOverCar(ChatElement lines, int playerIndex, int x, int y, BaseVehicle car) {
        if (!view.firstPerson.coversTheGamesView()) {
            lines.renderBatched(playerIndex, x, y);
            return;
        }
        IsoPlayer viewer = view.player();
        if (viewer != null && viewer.getVehicle() == car) {
            lines.renderBatched(playerIndex, IsoCamera.getScreenWidth(playerIndex) / 2, lowLine(playerIndex));
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
     * Where the lowest line written low in the window goes: over the game's dashboard, which it
     * shows in a seat whenever the mod does not hide it (Panels.dashboard), and which stands taller
     * than the hotbar does.
     */
    private static int lowLine(int playerIndex) {
        Texture dashboard = Texture.getSharedTexture(DASHBOARD);
        if (dashboard == null) {
            throw new IllegalStateException("the game has no " + DASHBOARD + " to keep radio lines clear of");
        }
        return IsoCamera.getScreenHeight(playerIndex) - dashboard.getHeight() - OVER_THE_DASHBOARD;
    }

    private static Field playerLines, chatLines, lineDisplayTime, historyVal;

    /**
     * Called where NameTags leaves out the viewer's own speech: writes the lines of a radio the
     * viewer holds, the ones the game tags "radio", low in the middle of the window, as the game's
     * PlayerLines.render writes them, newest first with the older fading by the history's share.
     */
    void writeOwnRadio(ChatElement speech, int playerIndex) {
        try {
            if (playerLines == null) {
                Class<?> lines = Class.forName("zombie.chat.ChatElement$PlayerLines");
                playerLines = accessible(ChatElement.class.getDeclaredField("playerLines"));
                historyVal = accessible(ChatElement.class.getDeclaredField("historyVal"));
                chatLines = accessible(lines.getDeclaredField("chatLines"));
                lineDisplayTime = accessible(lines.getDeclaredField("lineDisplayTime"));
            }
            Object[] all = (Object[]) playerLines.get(speech);
            if (playerIndex >= all.length) {
                return;
            }
            Object mine = all[playerIndex];
            TextDrawObject[] written = (TextDrawObject[]) chatLines.get(mine);
            float fadeAfter = lineDisplayTime.getInt(mine) / 4f, history = historyVal.getFloat(speech);
            int x = IsoCamera.getScreenWidth(playerIndex) / 2, y = 0;
            boolean placed = false;
            int count = 0;
            for (TextDrawObject line : written) {
                if (!line.getEnabled() || !"radio".equals(line.getCustomTag())) {
                    continue;
                }
                if (line.getWidth() <= 0 || line.getHeight() <= 0) {
                    count++;
                    continue;
                }
                float clock = line.getInternalClock();
                if (clock <= 0f) {
                    continue;
                }
                if (!placed) {
                    y = lowLine(playerIndex);
                    placed = true;
                }
                float alpha = Math.min(1f, clock / fadeAfter);
                y -= line.getHeight() + 1;
                if (count == 0) {
                    line.AddBatchedDraw(x, y, line.getDefaultFontEnum() != UIFont.Dialogue, alpha);
                } else if (history > 0f) {
                    line.AddBatchedDraw(x, y, line.getDefaultFontEnum() != UIFont.Dialogue, alpha * history);
                }
                count++;
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("the game's ChatElement has not the lines this build of the mod reads a held radio's from", failure);
        }
    }

    private static Field accessible(Field field) {
        field.setAccessible(true);
        return field;
    }
}
