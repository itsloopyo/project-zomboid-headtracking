package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import se.krka.kahlua.vm.KahluaTable;
import zombie.IndieGL;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.chat.ChatElement;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoCamera;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoTelevision;
import zombie.iso.objects.IsoWaveSignal;
import zombie.ui.TextDrawObject;
import zombie.ui.UIElementInterface;
import zombie.ui.UIFont;
import zombie.ui.UIManager;
import zombie.vehicles.BaseVehicle;

/**
 * The lines a television, a radio, a car radio or a radio the player carries shows of what it is
 * playing, put where they belong in the first person picture.
 *
 * The game writes a set's lines at the set's place in its own view, worked out through the
 * isometric zoom, so under the first person picture they hang wherever that view has the set and
 * slide about as the wheel zooms it. Here they are written over the set as it is drawn, and not
 * at all where the set is behind the eye or out of the picture. What is written stays the game's.
 *
 * A radio the player carries writes its lines into the player's own speech, and the radio of the
 * car the player sits in has its middle beside or behind the eye: both are written low in the
 * middle of the window, over the game's panels along the bottom and one over the other where both
 * are playing, as the game's PlayerLines.render writes a person's.
 *
 * Everything runs on the game's logic thread.
 */
final class SetCaptions {
    private final View view;

    SetCaptions(View view) {
        this.view = view;
    }

    /**
     * How tall a set is that the mod draws no model of, from the foot its sprite gives it: those of
     * the mod's models, a wide screen 0.56, a radio's case 0.185 and the antique console's cabinet
     * 0.78 (tools/models/furniture.py), each at the size the game paints the set.
     */
    static final float SPRITE_TELEVISION_HIGH = 0.56f, SPRITE_RADIO_HIGH = 0.185f, SPRITE_CONSOLE_TOP = 0.78f;
    /** How far over the top of a set the lowest line is written. */
    private static final float ABOVE = 0.12f;
    /** How far over a car's floor the lines of a car radio heard from outside are written: just over its roof. */
    private static final float OVER_A_CAR = 1.7f;
    /** The game's panels along the bottom of a player's part of the screen, a driver's instruments and the hotbar, which lines written low are kept over. */
    private static final String DASHBOARD = "getPlayerVehicleDashboard", HOTBAR = "getPlayerHotbar";
    /** How many pixels over the panels the lowest line written low in the window is. */
    private static final int OVER_THE_PANELS = 8;
    /** How often the panels' heights are looked up again: they change as the player sits down, gets out or hides the interface. */
    private static final long PANELS_EVERY = 250_000_000L;
    /** GL_SRC_ALPHA and GL_ONE_MINUS_SRC_ALPHA, the blend the game draws a line's backdrop with. */
    private static final int SOURCE_ALPHA = 770, ONE_MINUS_SOURCE_ALPHA = 771;
    /** How dark the game draws the backdrop behind a line. */
    private static final float BACKDROP_ALPHA = 0.4f;

    private final float[] ndc = new float[2];
    /** The game's frame the lines written low were last written in, and where the next one up goes in it, or NONE before the first. */
    private int lowFrame;
    private int lowY = NONE;
    private static final int NONE = Integer.MIN_VALUE;
    private boolean panelsRead;
    private long panelsReadAt;
    private int panelsHigh;
    private final Set<String> panelTroubleSaid = new HashSet<>();

    /** Called in place of the game's writing of a television's or a radio's lines. */
    void writeOverSet(ChatElement lines, int playerIndex, int x, int y, IsoWaveSignal set) {
        if (!view.firstPerson.coversTheGamesView()) {
            lines.renderBatched(playerIndex, x, y);
            return;
        }
        IsoGridSquare square = set.getSquare();
        Furniture piece = Own.furniture() ? Furniture.known(set.getSprite()) : null;
        float top;
        if (piece != null) {
            top = top(piece, set.getRenderYOffset());
        } else {
            PropertyContainer own = set.getProperties();
            top = spriteTop(own.has("IsTableTop"), Surfaces.surface(own), set.getRenderYOffset(), set instanceof IsoTelevision);
        }
        float atX = square.getX() + 0.5f, atY = square.getY() + 0.5f;
        writeAt(lines, playerIndex, atX, atY, square.getZ() * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(atX, atY, square.getZ()) + top + ABOVE);
    }

    /**
     * How high over its floor the top of a set's model is, standing as FurnishingBuilder stands it:
     * at the height Furniture raises it to, moved by the offset the game gives it on a higher or
     * lower piece (IsoGridSquare.FixStackableObjects), and as tall as its model at the scale
     * Furniture gives it.
     *
     * @param renderYOffset the set's IsoObject.getRenderYOffset, in the game's pixels of a surface
     */
    static float top(Furniture piece, float renderYOffset) {
        return piece.raised + renderYOffset / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT + piece.model.maxZ * piece.scaleUp;
    }

    /**
     * As top, for a set the mod draws no model of: one that goes on a table stands at its own
     * sprite's Surface, the height of the table it was painted on, moved by the same offset, and
     * any other set, the antique console, on the floor.
     *
     * @param ownSurface the set's own sprite's Surface, in the game's pixels
     */
    static float spriteTop(boolean tableTop, int ownSurface, float renderYOffset, boolean television) {
        float foot = ((tableTop ? ownSurface : 0) + renderYOffset) / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
        return foot + (television ? tableTop ? SPRITE_TELEVISION_HIGH : SPRITE_CONSOLE_TOP : SPRITE_RADIO_HIGH);
    }

    /** Called in place of the game's writing of a car radio's lines. */
    void writeOverCar(ChatElement lines, int playerIndex, int x, int y, BaseVehicle car) {
        if (!view.firstPerson.coversTheGamesView()) {
            lines.renderBatched(playerIndex, x, y);
            return;
        }
        IsoPlayer viewer = view.player();
        if (viewer != null && viewer.getVehicle() == car) {
            writeLow(lines, playerIndex, false);
            return;
        }
        float atX = car.getX(), atY = car.getY(), z = car.getZ();
        writeAt(lines, playerIndex, atX, atY, z * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(atX, atY, z) + OVER_A_CAR);
    }

    /**
     * Called where the game writes the viewer's own speech in first person, which it has not left
     * its radio lines out of: writes those, the lines of a radio the viewer carries.
     */
    void writeCarriedRadio(ChatElement speech, int playerIndex) {
        writeLow(speech, playerIndex, true);
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
     * Writes lines low in the middle of the window, over the panels and over what has been written
     * low already in this frame, as the game's PlayerLines.render writes them: upward from there,
     * the first plainly and those after it faded by the history's share, each over the game's
     * backdrop where it has one.
     *
     * @param radioOnly only the lines the game tags "radio", which are what a radio said into the speech of whoever carries it
     */
    private void writeLow(ChatElement lines, int playerIndex, boolean radioOnly) {
        int frame = IsoCamera.frameState.frameCount;
        if (frame != lowFrame) {
            lowFrame = frame;
            lowY = NONE;
        }
        try {
            Object mine = ((Object[]) Chat.PLAYER_LINES.get(lines))[playerIndex];
            TextDrawObject[] written = (TextDrawObject[]) Chat.WRITTEN.get(mine);
            float fadeAfter = Chat.SHOWN_FOR.getInt(mine) / 4f, history = Chat.HISTORY.getFloat(lines);
            int x = IsoCamera.getScreenWidth(playerIndex) / 2, count = 0;
            for (TextDrawObject line : written) {
                if (!line.getEnabled() || radioOnly && !"radio".equals(line.getCustomTag())) {
                    continue;
                }
                int wide = line.getWidth(), high = line.getHeight();
                if (wide <= 0 || high <= 0) {
                    count++;
                    continue;
                }
                float clock = line.getInternalClock();
                if (clock <= 0f) {
                    continue;
                }
                if (lowY == NONE) {
                    lowY = IsoCamera.getScreenHeight(playerIndex) - panelsHigh(playerIndex) - OVER_THE_PANELS;
                }
                float alpha = Math.min(1f, clock / fadeAfter);
                lowY -= high + 1;
                if (ChatElement.doBackDrop && ChatElement.backdropTexture != null) {
                    IndieGL.glBlendFunc(SOURCE_ALPHA, ONE_MINUS_SOURCE_ALPHA);
                    ChatElement.backdropTexture.renderInnerBased(x - wide / 2, lowY, wide, high, 0f, 0f, 0f, BACKDROP_ALPHA);
                }
                boolean outlined = line.getDefaultFontEnum() != UIFont.Dialogue;
                if (count == 0) {
                    line.AddBatchedDraw(x, lowY, outlined, alpha);
                } else if (history > 0f) {
                    line.AddBatchedDraw(x, lowY, outlined, alpha * history);
                }
                count++;
            }
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("the game's ChatElement refused the mod a field it had made readable", failure);
        }
    }

    /** The fields a ChatElement keeps its lines in, found together, all or none: Links has checked at the start that the game has each. */
    private static final class Chat {
        static final Field PLAYER_LINES, HISTORY, WRITTEN, SHOWN_FOR;

        static {
            Field playerLines, history, written, shownFor;
            try {
                Class<?> lines = Class.forName(Links.CHAT_WRITTEN.owner().replace('/', '.'), false, ChatElement.class.getClassLoader());
                playerLines = readable(ChatElement.class, Links.CHAT_PLAYER_LINES);
                history = readable(ChatElement.class, Links.CHAT_HISTORY);
                written = readable(lines, Links.CHAT_WRITTEN);
                shownFor = readable(lines, Links.CHAT_SHOWN_FOR);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("the game's ChatElement has not the fields this build of the mod writes its lines from", failure);
            }
            PLAYER_LINES = playerLines;
            HISTORY = history;
            WRITTEN = written;
            SHOWN_FOR = shownFor;
        }

        private static Field readable(Class<?> owner, Links.Field field) throws NoSuchFieldException {
            Field found = owner.getDeclaredField(field.name());
            found.setAccessible(true);
            return found;
        }
    }

    /**
     * How many pixels along the bottom of the player's part of the screen the game's panels cover
     * that are shown, the dashboard and the hotbar, as last looked up: nothing while the game has
     * its whole interface hidden. The mod hides the dashboard in a seat in first person (Panels).
     */
    private int panelsHigh(int playerIndex) {
        long now = System.nanoTime();
        if (!panelsRead || now - panelsReadAt >= PANELS_EVERY) {
            panelsRead = true;
            panelsReadAt = now;
            panelsHigh = UIManager.visibleAllUi ? Math.max(covered(DASHBOARD, playerIndex), covered(HOTBAR, playerIndex)) : 0;
        }
        return panelsHigh;
    }

    private int covered(String getter, int playerIndex) {
        Object[] found = LuaManager.caller.pcall(UIManager.getDefaultThread(), LuaManager.env.rawget(getter), Double.valueOf(playerIndex));
        Object panel = found.length > 1 ? found[1] : null;
        UIElementInterface shown = panel instanceof KahluaTable table && table.rawget("javaObject") instanceof UIElementInterface element ? element : null;
        if (found.length == 0 || !Boolean.TRUE.equals(found[0]) || panel != null && shown == null) {
            if (panelTroubleSaid.add(getter)) {
                Log.info("the game's " + getter + "(" + playerIndex + ") gave no panel, so lines written low in player " + (playerIndex + 1)
                        + "'s window are not kept above it: " + Arrays.toString(found));
            }
            return 0;
        }
        if (shown == null || !shown.isVisible()) {
            return 0;
        }
        return Math.max(0, (int) Math.ceil(IsoCamera.getScreenTop(playerIndex) + IsoCamera.getScreenHeight(playerIndex) - shown.getY()));
    }
}
