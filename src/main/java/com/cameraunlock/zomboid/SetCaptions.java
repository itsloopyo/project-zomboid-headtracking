package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import se.krka.kahlua.vm.KahluaTable;
import zombie.IndieGL;
import zombie.Lua.LuaManager;
import zombie.characters.IsoPlayer;
import zombie.chat.ChatElement;
import zombie.core.opengl.GLState;
import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoCamera;
import zombie.iso.IsoGridSquare;
import zombie.iso.objects.IsoTelevision;
import zombie.iso.objects.IsoWaveSignal;
import zombie.iso.sprite.IsoSprite;
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
 * slide about as the wheel zooms it. Here they are written over the set as it stands in the
 * picture, and not at all where the set is behind the eye or out of the picture. What is written
 * stays the game's.
 *
 * A radio the player carries writes its lines into the player's own speech, and the radio of the
 * car the player sits in has its middle beside or behind the eye: both are written low in the
 * middle of the window, over the game's panels along the bottom and one over the other where both
 * are playing, as the game's PlayerLines.render writes a person's. The game asks for them while it
 * draws its world, under the first person picture, so they are kept until the game has written
 * its own lines over the picture (writeKept).
 *
 * Everything runs on the game's logic thread.
 */
final class SetCaptions {
    private final View view;

    SetCaptions(View view) {
        this.view = view;
    }

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
    /** The tops of the models' bodies: see bodyTop. */
    private static final Map<Models.Model, Float> BODY_TOPS = new HashMap<>();

    private final float[] ndc = new float[2];
    /** The game's frame lines were last kept to be written low in, and where the next one up goes, or NONE before the first. */
    private int lowFrame;
    private int lowY = NONE;
    private static final int NONE = Integer.MIN_VALUE;
    /** The lines kept to be written low, where each goes down the window, its fade, and whether it is written or only its backdrop drawn. */
    private int kept;
    private TextDrawObject[] keptLine = new TextDrawObject[8];
    private int[] keptY = new int[8];
    private float[] keptAlpha = new float[8];
    private boolean[] keptOutlined = new boolean[8], keptWritten = new boolean[8];
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
        int squareX = square.getX(), squareY = square.getY(), z = square.getZ();
        IsoSprite sprite = set.getSprite();
        // The model the mod has of the set, whether it draws it or the game's sprite (Own.furniture), which the models are made to match.
        Furniture piece = sprite == null ? null : Furniture.known(sprite);
        float atX, atY, top;
        if (piece != null) {
            atX = squareX + piece.centreX;
            atY = squareY + piece.centreY;
            top = top(piece, set.getRenderYOffset());
        } else {
            PropertyContainer own = sprite == null ? null : sprite.getProperties();
            atX = squareX + 0.5f;
            atY = squareY + 0.5f;
            top = spriteTop(own != null && own.has("IsTableTop"), own == null ? 0 : Surfaces.surface(own), set.getRenderYOffset(), set instanceof IsoTelevision);
        }
        // The floor under the square as FurnishingBuilder stands a piece on it: the mean of its corners' heights.
        float floor = (view.worldScene.terrain.lift(squareX, squareY, z) + view.worldScene.terrain.lift(squareX + 1, squareY, z)
                + view.worldScene.terrain.lift(squareX + 1, squareY + 1, z) + view.worldScene.terrain.lift(squareX, squareY + 1, z)) / 4f;
        writeAt(lines, playerIndex, atX, atY, z * ViewCamera.LEVEL_HEIGHT + floor + top + ABOVE);
    }

    /**
     * How high over its floor the top of a set's body is, standing as FurnishingBuilder stands its
     * model: at the height Furniture raises it to, moved by the offset the game gives it on a higher
     * or lower piece (IsoGridSquare.FixStackableObjects), at the scale Furniture gives it.
     *
     * @param renderYOffset the set's IsoObject.getRenderYOffset, in the game's pixels of a surface
     */
    static float top(Furniture piece, float renderYOffset) {
        return piece.raised + renderYOffset / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT + bodyTop(piece.model) * piece.scaleUp;
    }

    /**
     * As top, for a set the mod has no model of: one that goes on a table stands at its own
     * sprite's Surface, the height of the table it was painted on, moved by the same offset, and
     * any other set, a console, on the floor. Each is as tall as the mod's model of its kind:
     * the larger set, the radio, the console's cabinet.
     *
     * @param ownSurface the set's own sprite's Surface, in the game's pixels
     */
    static float spriteTop(boolean tableTop, int ownSurface, float renderYOffset, boolean television) {
        float foot = ((tableTop ? ownSurface : 0) + renderYOffset) / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
        return foot + (!television ? bodyTop(Models.get("radio")) : tableTop ? bodyTop(Models.get("tv_wide")) : Furniture.TV_CABINET_TOP);
    }

    /** The top of a model's body, in its own metres: its highest point not of metal, which on a set is an aerial or a knob on its top. */
    static float bodyTop(Models.Model model) {
        Float known = BODY_TOPS.get(model);
        if (known != null) {
            return known;
        }
        float top = Float.NEGATIVE_INFINITY;
        for (int vertex = 0; vertex < model.surface.length; vertex++) {
            if (model.surface[vertex] != Models.METAL) {
                top = Math.max(top, model.vertices[vertex * Models.VERTEX_FLOATS + 2]);
            }
        }
        BODY_TOPS.put(model, top);
        return top;
    }

    /** Called in place of the game's writing of a car radio's lines. */
    void writeOverCar(ChatElement lines, int playerIndex, int x, int y, BaseVehicle car) {
        if (!view.firstPerson.coversTheGamesView()) {
            lines.renderBatched(playerIndex, x, y);
            return;
        }
        IsoPlayer viewer = view.player();
        if (viewer != null && viewer.getVehicle() == car) {
            keepLow(lines, playerIndex, false);
            return;
        }
        float atX = car.getX(), atY = car.getY(), z = car.getZ();
        writeAt(lines, playerIndex, atX, atY, z * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(atX, atY, z) + OVER_A_CAR);
    }

    /**
     * Called where the game writes the viewer's own speech in first person, which it has not left
     * its radio lines out of: keeps those, the lines of a radio the viewer carries, to be written low.
     */
    void writeCarriedRadio(ChatElement speech, int playerIndex) {
        keepLow(speech, playerIndex, true);
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
     * Keeps lines to be written low in the middle of the window, over the panels and over what
     * has been kept already in this frame, as the game's PlayerLines.render places them: upward
     * from there, the first plainly and those after it faded by the history's share. Nothing is
     * kept of lines that throw part way.
     *
     * @param radioOnly only the lines the game tags "radio", which are what a radio said into the speech of whoever carries it
     */
    private void keepLow(ChatElement lines, int playerIndex, boolean radioOnly) {
        int frame = IsoCamera.frameState.frameCount;
        if (frame != lowFrame) {
            lowFrame = frame;
            lowY = NONE;
            kept = 0;
        }
        int keptBefore = kept, lowBefore = lowY;
        boolean done = false;
        try {
            Object[] all = (Object[]) Chat.PLAYER_LINES.get(lines);
            if (playerIndex >= all.length || !Chat.SHOWING.getBoolean(lines)) {
                done = true;
                return;
            }
            Object mine = all[playerIndex];
            TextDrawObject[] written = (TextDrawObject[]) Chat.WRITTEN.get(mine);
            float fadeAfter = Chat.SHOWN_FOR.getInt(mine) / 4f, history = Chat.HISTORY.getFloat(lines);
            int count = 0;
            for (TextDrawObject line : written) {
                if (!line.getEnabled() || radioOnly && !"radio".equals(line.getCustomTag())) {
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
                if (lowY == NONE) {
                    lowY = IsoCamera.getScreenHeight(playerIndex) - panelsHigh(playerIndex) - OVER_THE_PANELS;
                }
                float alpha = Math.min(1f, clock / fadeAfter);
                lowY -= line.getHeight() + 1;
                keep(line, lowY, count == 0 ? alpha : alpha * history, line.getDefaultFontEnum() != UIFont.Dialogue, count == 0 || history > 0f);
                count++;
            }
            done = true;
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("the game's ChatElement refused the mod a field it had made readable", failure);
        } finally {
            if (!done) {
                kept = keptBefore;
                lowY = lowBefore;
            }
        }
    }

    private void keep(TextDrawObject line, int y, float alpha, boolean outlined, boolean writes) {
        if (kept == keptLine.length) {
            int more = kept * 2;
            keptLine = Arrays.copyOf(keptLine, more);
            keptY = Arrays.copyOf(keptY, more);
            keptAlpha = Arrays.copyOf(keptAlpha, more);
            keptOutlined = Arrays.copyOf(keptOutlined, more);
            keptWritten = Arrays.copyOf(keptWritten, more);
        }
        keptLine[kept] = line;
        keptY[kept] = y;
        keptAlpha[kept] = alpha;
        keptOutlined[kept] = outlined;
        keptWritten[kept] = writes;
        kept++;
    }

    /**
     * Called once the game has written its own lines over the picture: writes the lines kept to be
     * written low, each over the game's backdrop where it has one, as PlayerLines.render does, and
     * puts back the blend the backdrop was drawn with.
     */
    void writeKept(int playerIndex) {
        if (kept == 0) {
            return;
        }
        boolean backdrop = ChatElement.doBackDrop && ChatElement.backdropTexture != null;
        int a = 0, b = 0, c = 0, d = 0;
        if (backdrop) {
            try {
                Object blending = Chat.BLEND_NOW.get(GLState.BlendFuncSeparate);
                a = Chat.BLEND_A.getInt(blending);
                b = Chat.BLEND_B.getInt(blending);
                c = Chat.BLEND_C.getInt(blending);
                d = Chat.BLEND_D.getInt(blending);
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException("the game's GLState refused the mod a field it had made readable", failure);
            }
        }
        int x = IsoCamera.getScreenWidth(playerIndex) / 2;
        for (int i = 0; i < kept; i++) {
            TextDrawObject line = keptLine[i];
            if (backdrop) {
                IndieGL.glBlendFunc(SOURCE_ALPHA, ONE_MINUS_SOURCE_ALPHA);
                ChatElement.backdropTexture.renderInnerBased(x - line.getWidth() / 2, keptY[i], line.getWidth(), line.getHeight(), 0f, 0f, 0f, BACKDROP_ALPHA);
            }
            if (keptWritten[i]) {
                line.Draw(x, keptY[i], keptOutlined[i], keptAlpha[i]);
            }
            keptLine[i] = null;
        }
        if (backdrop) {
            IndieGL.glBlendFuncSeparate(a, b, c, d);
        }
        kept = 0;
        lowY = NONE;
    }

    /** The fields a ChatElement keeps its lines in and the game's blend is read from, found together, all or none: Links has checked at the start that the game has each. */
    private static final class Chat {
        static final Field PLAYER_LINES, HISTORY, SHOWING, WRITTEN, SHOWN_FOR, BLEND_NOW, BLEND_A, BLEND_B, BLEND_C, BLEND_D;

        static {
            Field[] found = new Field[10];
            try {
                ClassLoader game = ChatElement.class.getClassLoader();
                Links.Field[] wanted = {Links.CHAT_PLAYER_LINES, Links.CHAT_HISTORY, Links.CHAT_SHOWING, Links.CHAT_WRITTEN, Links.CHAT_SHOWN_FOR,
                    Links.BLEND_NOW, Links.BLEND_A, Links.BLEND_B, Links.BLEND_C, Links.BLEND_D};
                for (int i = 0; i < wanted.length; i++) {
                    found[i] = Class.forName(wanted[i].owner().replace('/', '.'), false, game).getDeclaredField(wanted[i].name());
                    found[i].setAccessible(true);
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("the game has not the fields this build of the mod writes a radio's lines with", failure);
            }
            PLAYER_LINES = found[0];
            HISTORY = found[1];
            SHOWING = found[2];
            WRITTEN = found[3];
            SHOWN_FOR = found[4];
            BLEND_NOW = found[5];
            BLEND_A = found[6];
            BLEND_B = found[7];
            BLEND_C = found[8];
            BLEND_D = found[9];
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
