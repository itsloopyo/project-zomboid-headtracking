package com.cameraunlock.zomboid;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.LuaCallFrame;
import zombie.Lua.LuaManager;
import java.util.Set;
import java.util.Stack;
import zombie.characters.CharacterTimedActions.BaseAction;
import zombie.characters.CharacterTimedActions.LuaTimedActionNew;
import zombie.characters.IsoPlayer;
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
 * reads it again as the game does, for the game's panel, where it is panned, zoomed and marked.
 * What puts it down is MapHold's, looked at after each frame's world is drawn whatever the view
 * (settle).
 *
 * Each player's map is drawn into a picture of its own, of the sheet's shape, among the game's
 * own UI drawing, by a UI element in the lower left corner of the screen, which the picture
 * covers, so the map fills the sheet edge to edge: the renderer
 * sets its viewport from the screen's size. The sheet shows the picture on the next frame. The
 * game leaves its UI drawing out of the frames past its UI frame rate and while its Lua debugger
 * is up, and the picture is drawn again only on the frames it draws it.
 *
 * On a server where players can fight, the game's own panel is shown as it is: a map that
 * leaves the hands and the view free while it is read is more than the game gives anyone.
 */
final class HeldMap {
    private HeldMap() {}

    /** What holding the map up is called where an animation's name would be. */
    static final String HELD = "the map held up";
    /** The most pixels across the map's picture has. It is as tall as the sheet is for its width. */
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
     * key made to read it again for the game's panel from there, and a map of the game's own kind,
     * with the same data, style and paper as its panel, made for each player the first time it is
     * drawn. Handed the mod's functions, it answers the one that places a player's map and hands
     * back the map's Java element.
     */
    private static final String LUA = """
            local Mod = ...
            local maps, toPanel = {}, {}
            local new, start, stop, perform, toggle = ISReadWorldMap.new, ISReadWorldMap.start, ISReadWorldMap.stop, ISReadWorldMap.perform, ISWorldMap.ToggleWorldMap

            function ISReadWorldMap:new(character, centerX, centerY, zoom)
                local o = new(self, character, centerX, centerY, zoom)
                o.toPanel = toPanel[o.playerNum]
                return o
            end

            function ISReadWorldMap:start()
                start(self)
                if self.centerX == nil and not self.toPanel and Mod.wanted(self.playerNum) then
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
                    toPanel[playerNum] = true
                    toggle(playerNum)
                    toPanel[playerNum] = nil
                    return
                end
                toggle(playerNum)
            end

            local function make(side, tall)
                local map = ISUIElement:new(0, 0, side, tall)
                map.javaObject = UIWorldMap.new(map)
                map.mapAPI = map.javaObject:getAPIv3()
                map.mapAPI:setMapItem(MapItem.getSingleton())
                map.javaObject:setX(0)
                map.javaObject:setWidth(side)
                map.javaObject:setHeight(tall)
                ISWorldMap.initDataAndStyle(map)
                map.mapAPI:setBoolean("HideUnvisited", map.hideUnvisitedAreas)
                map.mapAPI:setBoolean("Isometric", false)
                map.mapAPI:setBoolean("Players", true)
                map.mapAPI:setBoolean("Symbols", true)
                map.side, map.tall = side, tall
                return map
            end

            return function(playerNum, side, tall, zoom, y)
                local map = maps[playerNum]
                if map == nil or map.side ~= side or map.tall ~= tall then
                    map = make(side, tall)
                    maps[playerNum] = map
                end
                map.javaObject:setY(y)
                local player = getSpecificPlayer(playerNum)
                local at = player:getVehicle() or player
                map.mapAPI:centerOn(at:getX(), at:getY())
                map.mapAPI:setZoom(zoom)
                return map.javaObject
            end
            """;

    /** How far in the held map is drawn, as the game counts its map's zoom: a few streets round the player. */
    static final double ZOOM = 17.5;

    private static final boolean[] reading = new boolean[View.COUNT], held = new boolean[View.COUNT], made = new boolean[View.COUNT];
    private static final Double[] PLAYER_NUMBERS = new Double[View.COUNT];
    private static final Double ZOOM_ASKED = ZOOM;
    /** The side and the height of the corner the maps are placed in, as they were last asked for, and as the Lua is handed them. */
    private static int placedSide, placedHeight;
    private static Double sideAsked, tallAsked, cornerAsked;
    private static Object place;
    /** Set as the game loads its Lua: the mod's goes into it on the next frame, among the game's UI drawing. */
    private static boolean pending;

    private static final JavaFunction WANTED = (frame, arguments) -> frame.push(MapHold.wanted(holding(player(frame), false))),
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
                lower(player(frame), "the map key was pressed again");
                return 0;
            };

    private static int player(LuaCallFrame frame) {
        return (int) (double) (Double) frame.get(0);
    }

    /** What of MapHold's holds of a player now. With `held`, the map is up already, and any action under way is something else. */
    private static int holding(int index, boolean held) {
        IsoPlayer player = IsoPlayer.players[index];
        View view = View.of(index);
        if (player == null || view == null) {
            return MapHold.GONE;
        }
        int holding = 0;
        if (!Part.FIRST_PERSON.running() || !Part.HELD_MAP.running()) {
            holding |= MapHold.OFF;
        }
        if (refused) {
            holding |= MapHold.NO_PICTURE;
        }
        if (!view.firstPerson.active()) {
            holding |= MapHold.NOT_FIRST_PERSON;
        }
        if (!Own.arms() || !CharacterDraw.cutsSeen()) {
            holding |= MapHold.NO_OWN_ARMS;
        }
        if (view.firstPerson.ledByBody()) {
            holding |= MapHold.BODY_LEADS;
        }
        if (player.isDead()) {
            holding |= MapHold.DEAD;
        }
        if (player.getVehicle() != null) {
            holding |= MapHold.IN_A_VEHICLE;
        }
        if (Online.contested()) {
            holding |= MapHold.PLAYERS_CAN_FIGHT;
        }
        // Walked any way but straight ahead the character is held in the game's aiming stance (Controls.turnedToTheView): that is no aim.
        if (player.isAiming() && !view.controls.turnedOnly()) {
            holding |= MapHold.AIMING;
        }
        if (GameAttack.attacking(player)) {
            holding |= MapHold.ATTACKING;
        }
        if (held && (doing = actionTaken(player)) != null) {
            holding |= MapHold.DOING_SOMETHING_ELSE;
        }
        return holding;
    }

    /** The action under way that last put a map down, for its line in the log. */
    private static String doing;

    /** Timed actions the game queues for a walk to somewhere: walking does not put the map down. */
    private static final Set<String> WALKS = Set.of("ISWalkToTimedAction", "ISPathFindAction");

    /** The name of the timed action the player has under way that puts a held map down, or null for none. */
    private static String actionTaken(IsoPlayer player) {
        Stack<BaseAction> actions = player.getCharacterActions();
        if (actions.isEmpty()) {
            return null;
        }
        BaseAction action = actions.get(0);
        if (action.pathfinding) {
            return null;
        }
        if (action instanceof LuaTimedActionNew lua) {
            String type = lua.getMetaType();
            return WALKS.contains(type) || "ISReadWorldMap".equals(type) ? null : type;
        }
        return action.getClass().getSimpleName();
    }

    /**
     * Puts a player's map down if it is up, for a press that is for nothing else then: Esc or a
     * middle click. Main thread.
     *
     * @return whether a map was up
     */
    static boolean putDown(int player, String why) {
        if (!held[player]) {
            return false;
        }
        lower(player, why);
        return true;
    }

    /** As the game loads its Lua: nobody holds a map, and the mod's Lua goes into the game's on the next frame among its UI drawing. */
    static void luaLoaded() {
        Arrays.fill(reading, false);
        Arrays.fill(held, false);
        pending = true;
    }

    /**
     * Puts the game's reading of its map in the hands, in the game's Lua as it was last loaded. A
     * failure is said, and leaves the map key as the game has it.
     */
    private static void register() {
        if (!(LuaManager.env.rawget("ISReadWorldMap") instanceof KahluaTable read) || !(LuaManager.env.rawget("ISWorldMap") instanceof KahluaTable)) {
            place = null;
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
            Log.error("the map is not held up in first person: the game's Lua would not compile the mod's", refused);
            return;
        }
        Object[] ran = LuaManager.caller.pcall(UIManager.getDefaultThread(), chunk, mod);
        if (!Boolean.TRUE.equals(ran[0]) || ran.length < 2 || ran[1] == null) {
            Log.error("the map is not held up in first person: the mod's Lua failed in the game's: " + Arrays.toString(ran));
            return;
        }
        place = ran[1];
        Log.info("the world map read in first person is held up in the hands");
    }

    /** Whether a player is reading the map, with the game's animation of it, to hold it up. */
    static boolean reading(int player) {
        return reading[player];
    }

    /** Whether a player holds the map up. */
    static boolean held(int player) {
        return held[player];
    }

    private static void lower(int player, String why) {
        if (held[player]) {
            held[player] = false;
            Log.info("player " + (player + 1) + " has put the map down: " + why);
        }
    }

    /** After each frame's world is drawn, whatever the view: puts down each map MapHold says is down. Main thread. */
    static void settle() {
        for (int player = 0; player < View.COUNT; player++) {
            if (reading[player] || held[player]) {
                int holding = holding(player, held[player]);
                String why = MapHold.down(holding);
                if (why != null) {
                    if (Integer.lowestOneBit(holding) == MapHold.DOING_SOMETHING_ELSE) {
                        why += ": " + doing;
                    }
                    if (reading[player]) {
                        reading[player] = false;
                        Log.info("player " + (player + 1) + "'s map is read without holding it up: " + why);
                    }
                    lower(player, why);
                }
            }
        }
    }

    /**
     * Among the game's UI drawing, before any of it, on the frames the game draws its UI: draws
     * each map still up into its player's picture, and gives back the picture of a map once the
     * hands that held it are down.
     */
    static void beforeUi() {
        if (pending) {
            pending = false;
            register();
        }
        int side = Math.min(MOST_SIDE, Math.min(Core.width, Math.round(Core.height * TimedActs.SHEET_WIDE / TimedActs.SHEET_TALL)));
        int tall = Math.round(side * TimedActs.SHEET_TALL / TimedActs.SHEET_WIDE);
        if (side != placedSide || Core.height != placedHeight) {
            placedSide = side;
            placedHeight = Core.height;
            sideAsked = (double) side;
            tallAsked = (double) tall;
            cornerAsked = (double) (Core.height - tall);
        }
        for (int player = 0; player < View.COUNT; player++) {
            if (!reading[player] && !held[player]) {
                if (made[player] && handsDown(player)) {
                    made[player] = false;
                    SpriteRenderer.instance.drawGeneric(FREE[player]);
                }
                continue;
            }
            if (PLAYER_NUMBERS[player] == null) {
                PLAYER_NUMBERS[player] = (double) player;
            }
            Object[] ran = LuaManager.caller.pcall(UIManager.getDefaultThread(), place, PLAYER_NUMBERS[player], sideAsked, tallAsked, ZOOM_ASKED, cornerAsked);
            if (!Boolean.TRUE.equals(ran[0]) || ran.length < 2 || !(ran[1] instanceof UIElement map)) {
                Log.error("player " + (player + 1) + "'s map is put down: the game's Lua would not place it: " + Arrays.toString(ran));
                reading[player] = false;
                lower(player, "it could not be drawn");
                continue;
            }
            made[player] = true;
            drawnSide = side;
            drawnTall = tall;
            drawnWidth = Core.width;
            drawnHeight = Core.height;
            SpriteRenderer.instance.drawGeneric(BIND[player]);
            try {
                map.render();
            } finally {
                SpriteRenderer.instance.drawGeneric(UNBIND[player]);
            }
        }
    }

    /** Whether the hands that held a player's map are down: until then the sheet in them still shows its picture. */
    private static boolean handsDown(int player) {
        View view = View.of(player);
        ViewModel.Task task = view == null ? null : view.viewModel.task;
        return task == null || task.kind() != TimedActs.MAP;
    }

    /** The width and height of the maps drawn and the screen's size they are drawn at, for the render thread. */
    private static volatile int drawnSide, drawnTall, drawnWidth, drawnHeight;

    private static final int[] texture = new int[View.COUNT], target = new int[View.COUNT], sides = new int[View.COUNT], talls = new int[View.COUNT];
    private static int savedDraw, savedRead;
    /** Set once the driver refuses a picture: from then on the map is read as the game reads it (MapHold.NO_PICTURE). */
    private static volatile boolean refused;
    /** Whether the map being drawn goes into its picture: else, with pictures refused, it goes nowhere. */
    private static boolean bound;
    /** The player whose map's drawing is begun and not yet ended, or -1: see bind. */
    private static int drawing = -1;

    private static final TextureDraw.GenericDrawer[] BIND = new TextureDraw.GenericDrawer[View.COUNT], UNBIND = new TextureDraw.GenericDrawer[View.COUNT],
            FREE = new TextureDraw.GenericDrawer[View.COUNT];

    static {
        for (int player = 0; player < View.COUNT; player++) {
            int index = player;
            BIND[player] = new TextureDraw.GenericDrawer() {
                @Override
                public void render() {
                    bind(index, drawnSide, drawnTall, drawnWidth, drawnHeight);
                }
            };
            UNBIND[player] = new TextureDraw.GenericDrawer() {
                @Override
                public void render() {
                    unbind(index);
                }
            };
            FREE[player] = new TextureDraw.GenericDrawer() {
                @Override
                public void render() {
                    free(index);
                }
            };
        }
    }

    /**
     * Turns what the game's UI draws next into a player's picture, or, where the driver refused
     * pictures, into nothing, until unbind: the viewport, the scissor and the colour mask are kept
     * to be put back then. The picture covers the screen's lower left corner, and the scissor keeps
     * what is drawn inside it.
     */
    private static void bind(int player, int side, int tall, int width, int height) {
        // Between this and unbind the game's own renderer draws the map, and a throw in it leaves unbind undone.
        if (drawing >= 0) {
            Log.error("player " + (drawing + 1) + "'s held map was not finished drawing: what it was drawn on is put back now");
            unbind(drawing);
        }
        GL11.glPushAttrib(GL_COLOR_BUFFER_BIT | GL_VIEWPORT_BIT | GL_SCISSOR_BIT);
        drawing = player;
        bound = !refused;
        if (bound && (texture[player] == 0 || sides[player] != side || talls[player] != tall)) {
            free(player);
            String why = make(player, side, tall);
            if (why != null) {
                refused = true;
                bound = false;
                free(player);
                Log.error("the map is read as the game reads it from now on, not held up: " + why);
            }
        }
        if (!bound) {
            GL11.glColorMask(false, false, false, false);
            return;
        }
        savedDraw = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        savedRead = GL11.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target[player]);
        GL11.glViewport(0, 0, width, height);
        GL11.glColorMask(true, true, true, true);
        GL11.glDisable(GL_SCISSOR_TEST);
        GL11.glClearColor(0.86f, 0.82f, 0.71f, 1f);
        GL11.glClear(GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL_SCISSOR_TEST);
        GL11.glScissor(0, 0, side, tall);
    }

    private static void unbind(int player) {
        drawing = -1;
        if (!bound) {
            GL11.glPopAttrib();
            return;
        }
        bound = false;
        // What was pushed holds the draw buffers of the framebuffer bound then: popped onto the picture's, they are GL_INVALID_OPERATION.
        GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, savedDraw);
        GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, savedRead);
        GL11.glPopAttrib();
        int remembered = Texture.lastTextureID;
        GL11.glBindTexture(GL_TEXTURE_2D, texture[player]);
        GL30.glGenerateMipmap(GL_TEXTURE_2D);
        GL11.glBindTexture(GL_TEXTURE_2D, remembered);
    }

    private static String make(int player, int side, int tall) {
        texture[player] = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, texture[player]);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, side, tall, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        GL30.glGenerateMipmap(GL_TEXTURE_2D);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL11.glBindTexture(GL_TEXTURE_2D, Texture.lastTextureID);
        int drawTo = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFrom = GL11.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        target[player] = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target[player]);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture[player], 0);
        int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawTo);
        GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, readFrom);
        sides[player] = side;
        talls[player] = tall;
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            return "the driver would not make the picture it is drawn into (status 0x" + Integer.toHexString(status) + ")";
        }
        Log.info("player " + (player + 1) + "'s held map picture is made, " + side + "x" + tall);
        return null;
    }

    private static void free(int player) {
        if (texture[player] == 0) {
            return;
        }
        GL30.glDeleteFramebuffers(target[player]);
        GL11.glDeleteTextures(texture[player]);
        texture[player] = target[player] = sides[player] = talls[player] = 0;
        Log.info("player " + (player + 1) + "'s held map picture is given back to the graphics card");
    }

    /** Gives every player's map picture back to the graphics card. Render thread. */
    static void letGo() {
        for (int player = 0; player < View.COUNT; player++) {
            free(player);
        }
    }

    /** The texture a player's map is drawn in, or nought for none. Render thread. */
    static int picture(int player) {
        return texture[player];
    }
}
