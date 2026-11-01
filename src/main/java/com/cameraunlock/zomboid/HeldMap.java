package com.cameraunlock.zomboid;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.LuaCallFrame;
import zombie.Lua.LuaManager;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureDraw;
import zombie.ui.UIElement;
import zombie.ui.UIManager;

/**
 * The world map held up in both hands in first person. The map key reads the map as the game
 * has it, with its animation of reading, and then, in place of the game's panel over the whole
 * screen, the map stays up in the hands: the game's own map of the place round the player,
 * drawn by its own renderer with the player's marker and the player's own marks on it, onto a
 * sheet before the eye. The player can walk with it up. The map key again puts it down and
 * opens the game's panel, where it is panned, zoomed and marked; aiming, attacking, any other
 * action, a vehicle or death puts it down.
 *
 * The game's map is drawn into a picture of the screen's size, in the corner a UI element at
 * nought draws into, among the game's own UI drawing: the renderer sets its viewport from the
 * screen's size. The sheet reads that corner on the next frame.
 *
 * On a server where players can fight, the game's own panel is shown as it is: a map that
 * leaves the hands and the view free while it is read is more than the game gives anyone.
 */
final class HeldMap {
    private HeldMap() {}

    /** What holding the map up is called where an animation's name would be. */
    static final String HELD = "the map held up";
    /** The most pixels a side of the map's picture has. */
    static final int MOST_SIDE = 1024;

    private static final int GL_TEXTURE_2D = 0x0DE1, GL_RGBA = 0x1908, GL_RGBA8 = 0x8058, GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_LINEAR = 0x2601, GL_LINEAR_MIPMAP_LINEAR = 0x2703;
    private static final int GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803, GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_FRAMEBUFFER = 0x8D40, GL_DRAW_FRAMEBUFFER = 0x8CA9, GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0, GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6, GL_READ_FRAMEBUFFER_BINDING = 0x8CAA;
    private static final int GL_SCISSOR_TEST = 0x0C11, GL_COLOR_BUFFER_BIT = 0x4000, GL_VIEWPORT_BIT = 0x0800, GL_SCISSOR_BIT = 0x00080000;

    /**
     * The Lua that holds the map: the game's reading of it made to hold it in the hands, the map
     * key made to open the panel from there, and a map of the game's own kind, with the same
     * data, style and paper as its panel, made for each player the first time it is drawn.
     * Handed the mod's functions, it answers the one that places a player's map and hands back
     * the map's Java element.
     */
    private static final String LUA = """
            local Mod = ...
            local maps = {}
            local start, stop, perform, toggle = ISReadWorldMap.start, ISReadWorldMap.stop, ISReadWorldMap.perform, ISWorldMap.ToggleWorldMap

            function ISReadWorldMap:start()
                start(self)
                if self.centerX == nil and Mod.wanted(self.playerNum) then
                    self.heldInHands = true
                    Mod.reading(self.playerNum, true)
                end
            end

            function ISReadWorldMap:stop()
                if self.heldInHands then
                    Mod.reading(self.playerNum, false)
                end
                stop(self)
            end

            function ISReadWorldMap:perform()
                if self.heldInHands then
                    Mod.reading(self.playerNum, false)
                    if Mod.wanted(self.playerNum) then
                        Mod.hold(self.playerNum)
                        ISBaseTimedAction.perform(self)
                        return
                    end
                end
                perform(self)
            end

            ISWorldMap.ToggleWorldMap = function(playerNum)
                if Mod.held(playerNum) then
                    Mod.lower(playerNum)
                    ISWorldMap.ShowWorldMap(playerNum)
                    return
                end
                toggle(playerNum)
            end

            local function make(side)
                local map = ISUIElement:new(0, 0, side, side)
                map.javaObject = UIWorldMap.new(map)
                map.mapAPI = map.javaObject:getAPIv3()
                map.mapAPI:setMapItem(MapItem.getSingleton())
                map.javaObject:setX(0)
                map.javaObject:setY(0)
                map.javaObject:setWidth(side)
                map.javaObject:setHeight(side)
                ISWorldMap.initDataAndStyle(map)
                map.mapAPI:setBoolean("HideUnvisited", map.hideUnvisitedAreas)
                map.mapAPI:setBoolean("Isometric", false)
                map.mapAPI:setBoolean("Players", true)
                map.mapAPI:setBoolean("Symbols", true)
                map.side = side
                return map
            end

            return function(playerNum, side, zoom)
                local map = maps[playerNum]
                if map == nil or map.side ~= side then
                    map = make(side)
                    maps[playerNum] = map
                end
                local player = getSpecificPlayer(playerNum)
                local vehicle = player:getVehicle()
                local at = vehicle or player
                map.mapAPI:centerOn(at:getX(), at:getY())
                map.mapAPI:setZoom(zoom)
                return map.javaObject
            end
            """;

    /** How far in the held map is drawn, as the game counts its map's zoom: a few streets round the player. */
    static final double ZOOM = 17.5;

    // Logic thread.
    private static final boolean[] reading = new boolean[View.COUNT], held = new boolean[View.COUNT];
    private static Object place;
    private static boolean placeRefused;

    private static final JavaFunction WANTED = (frame, arguments) -> frame.push(wanted(player(frame))),
            READING = (frame, arguments) -> {
                reading[player(frame)] = Boolean.TRUE.equals(frame.get(1));
                return 0;
            },
            HOLD = (frame, arguments) -> {
                int player = player(frame);
                held[player] = true;
                Log.info("player " + (player + 1) + " holds the map up in their hands: the map key again opens the game's map");
                return 0;
            },
            IS_HELD = (frame, arguments) -> frame.push(held[player(frame)]),
            LOWER = (frame, arguments) -> {
                lower(player(frame));
                return 0;
            };

    private static int player(LuaCallFrame frame) {
        return (int) (double) (Double) frame.get(0);
    }

    /** Whether the map read by a player now goes up in their hands. */
    private static boolean wanted(int player) {
        View view = View.of(player);
        return Part.FIRST_PERSON.running() && Part.HELD_MAP.running() && view != null && view.firstPerson.active() && Own.arms() && !Online.contested();
    }

    /** Puts the game's reading of its map in the hands, each time the game loads its Lua. */
    static void register() {
        for (int i = 0; i < View.COUNT; i++) {
            reading[i] = held[i] = false;
        }
        place = null;
        if (!(LuaManager.env.rawget("ISReadWorldMap") instanceof KahluaTable read) || !(LuaManager.env.rawget("ISWorldMap") instanceof KahluaTable)) {
            Log.info("the map is not held up in first person: the game's Lua has no ISReadWorldMap or ISWorldMap");
            return;
        }
        if (read.rawget("heldInHands") != null) {
            return;
        }
        read.rawset("heldInHands", false);
        KahluaTable mod = LuaManager.platform.newTable();
        mod.rawset("wanted", WANTED);
        mod.rawset("reading", READING);
        mod.rawset("hold", HOLD);
        mod.rawset("held", IS_HELD);
        mod.rawset("lower", LOWER);
        Object chunk;
        try {
            chunk = LuaCompiler.loadstring(LUA, "the mod's held map", LuaManager.env);
        } catch (IOException refused) {
            throw new IllegalStateException("the game's Lua would not compile the mod's held map: " + refused.getMessage(), refused);
        }
        Object[] ran = LuaManager.caller.pcall(UIManager.getDefaultThread(), chunk, mod);
        if (!Boolean.TRUE.equals(ran[0]) || ran.length < 2 || ran[1] == null) {
            throw new IllegalStateException("the mod's held map failed in the game's Lua: " + java.util.Arrays.toString(ran));
        }
        place = ran[1];
        placeRefused = false;
        Log.info("the world map read in first person is held up in the hands");
    }

    /** Whether a player is reading the map, with the game's animation of it, to hold it up. Logic thread. */
    static boolean reading(int player) {
        return reading[player];
    }

    /** Whether a player holds the map up. Logic thread. */
    static boolean held(int player) {
        return held[player];
    }

    /** Puts the map down. Logic thread. */
    static void lower(int player) {
        if (held[player]) {
            held[player] = false;
            Log.info("player " + (player + 1) + " has put the map down");
        }
    }

    /**
     * Draws the game's map of the place round the first player who has one up into the map's
     * picture, among the game's UI drawing and before any of it. Logic thread.
     */
    static void beforeUi() {
        if (place == null || placeRefused) {
            return;
        }
        for (int player = 0; player < View.COUNT; player++) {
            if (!reading[player] && !held[player]) {
                continue;
            }
            int side = Math.min(MOST_SIDE, Math.min(Core.width, Core.height));
            if (side <= 0) {
                return;
            }
            Object[] ran = LuaManager.caller.pcall(UIManager.getDefaultThread(), place, (double) player, (double) side, ZOOM);
            if (!Boolean.TRUE.equals(ran[0]) || ran.length < 2 || !(ran[1] instanceof UIElement map)) {
                placeRefused = true;
                Log.info("the held map is drawn blank from now on: the game's Lua would not place it: " + java.util.Arrays.toString(ran));
                return;
            }
            drawnSide = side;
            drawnWidth = Core.width;
            drawnHeight = Core.height;
            SpriteRenderer.instance.drawGeneric(BIND);
            map.render();
            SpriteRenderer.instance.drawGeneric(UNBIND);
            return;
        }
    }

    /** The side of the map drawn and the screen's size it is drawn at, for the render thread. */
    private static volatile int drawnSide, drawnWidth, drawnHeight;

    // Render thread.
    private static int texture, target, wide, high, savedDraw, savedRead;
    /** Where the map lies in the picture, as u and v from its corner and across: nought across while none is drawn. */
    private static final float[] region = new float[4];
    private static boolean refused;

    private static final TextureDraw.GenericDrawer BIND = new TextureDraw.GenericDrawer() {
        @Override
        public void render() {
            bind(drawnSide, drawnWidth, drawnHeight);
        }
    };

    private static final TextureDraw.GenericDrawer UNBIND = new TextureDraw.GenericDrawer() {
        @Override
        public void render() {
            unbind();
        }
    };

    /** Whether the map's drawing goes into its picture: else, with the picture refused, it goes nowhere. */
    private static boolean bound;

    /**
     * Turns what the game's UI draws next into the map's picture, or, where the driver refused
     * the picture, into nothing, until unbind: the viewport, the scissor and the colour mask are
     * kept to be put back then.
     */
    private static void bind(int side, int width, int height) {
        GL11.glPushAttrib(GL_COLOR_BUFFER_BIT | GL_VIEWPORT_BIT | GL_SCISSOR_BIT);
        bound = !refused;
        if (bound && (texture == 0 || wide != width || high != height)) {
            letGo();
            String why = make(width, height);
            if (why != null) {
                refused = true;
                bound = false;
                letGo();
                Log.info("the held map is drawn blank: " + why);
            }
        }
        if (!bound) {
            GL11.glColorMask(false, false, false, false);
            return;
        }
        savedDraw = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        savedRead = GL11.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL11.glViewport(0, 0, width, height);
        GL11.glEnable(GL_SCISSOR_TEST);
        GL11.glScissor(0, height - side, side, side);
        GL11.glColorMask(true, true, true, true);
        GL11.glClearColor(0.86f, 0.82f, 0.71f, 1f);
        GL11.glClear(GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL_SCISSOR_TEST);
        region[0] = 0f;
        region[1] = (height - side) / (float) height;
        region[2] = side / (float) width;
        region[3] = side / (float) height;
        bound = true;
    }

    private static void unbind() {
        GL11.glPopAttrib();
        if (!bound) {
            return;
        }
        bound = false;
        GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, savedDraw);
        GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, savedRead);
        int remembered = Texture.lastTextureID;
        GL11.glBindTexture(GL_TEXTURE_2D, texture);
        GL30.glGenerateMipmap(GL_TEXTURE_2D);
        GL11.glBindTexture(GL_TEXTURE_2D, remembered);
    }

    private static String make(int width, int height) {
        texture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL30.glGenerateMipmap(GL_TEXTURE_2D);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL_TEXTURE_2D, Texture.lastTextureID);
        int drawTo = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFrom = GL11.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        target = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawTo);
        GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, readFrom);
        wide = width;
        high = height;
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            return "the driver would not make the picture it is drawn into (status 0x" + Integer.toHexString(status) + ")";
        }
        Log.info("the held map's picture is made, " + width + "x" + height);
        return null;
    }

    /** Gives the map's picture back to the graphics card. Render thread. */
    static void letGo() {
        region[2] = 0f;
        if (texture == 0) {
            return;
        }
        GL30.glDeleteFramebuffers(target);
        GL11.glDeleteTextures(texture);
        texture = target = wide = high = 0;
        Log.info("the held map's picture is given back to the graphics card");
    }

    /**
     * Draws the sheet the map is on, as the arms are drawn.
     *
     * @param at the matrix from a square of side one, across x and up y, facing +z, to the view
     */
    static void drawSheet(float[] projection, float[] at, float light, boolean outside) {
        OwnArms.sheet(projection, at, texture, region, light, outside);
    }
}
