package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.LuaCallFrame;
import zombie.GameTime;
import zombie.Lua.LuaEventManager;
import zombie.Lua.LuaManager;
import zombie.characters.BodyDamage.BodyPart;
import zombie.characters.CharacterDiedListener;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;
import zombie.inventory.types.HandWeapon;
import zombie.inventory.types.InventoryContainer;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.objects.IsoWorldInventoryObject;
import zombie.ui.SpeedControls;
import zombie.ui.UIManager;
import zombie.vehicles.BaseVehicle;

/**
 * Rewind 30s, on only with -Dcameraunlock.zomboid.rewind=true: offline, a player at this screen
 * who dies is asked whether to die or to go back to how they were REWIND seconds before. Only the
 * player goes back.
 * The world goes on as it was, the zombie that killed them included, and every zombie stands
 * still for ZOMBIES_STILL seconds of play after, so the player can see where they are.
 *
 * Every second of play each player is written out with the game's own IsoPlayer.save, which is
 * what the game keeps of them in a save: where they stand, their body, wounds and infection
 * included, what they carry, wear and hold, and what they know. A death is held at the start of
 * IsoGameCharacter.Kill, before the game has made a body, deleted the player's file from the save
 * or told its Lua, and the game is paused under a panel of two buttons. Rewind loads the player
 * from the snapshot with IsoPlayer.load. The other button lets the held Kill run, and the death
 * goes on as the game has it. Leaving the save with the panel up rewinds, before the game saves.
 *
 * IsoPlayer.load makes every item it reads anew, so each snapshot also keeps the items the player
 * had then. An item of those that has since left the player, dropped or put away, is taken from
 * where it is now, since the player has it again. An item the player has taken up since is left
 * on the ground where they died, where the game would have left it with their body.
 *
 * All of it runs on the game's main thread: the world's tick, Kill and the panel's buttons.
 */
final class Rewind {
    private static final float EVERY = RewindRing.EVERY;
    private static final int KEPT = RewindRing.KEPT;
    private static final int FIRST_BUFFER = 256 * 1024;

    /** One player as they were, with the items they had then, every container's contents included. */
    private record Snapshot(byte[] bytes, InventoryItem[] items, float health, float takenAt, long takenNanos, BaseVehicle vehicle, int seat) {}

    /** A Kill the panel holds, with what it was called with, to let it run as it was called. */
    private record Held(IsoGameCharacter killer, HandWeapon weapon, boolean gory, CharacterDiedListener listener) {}

    /** By player: whose snapshots these are, the snapshots oldest first, the seconds played and since the last one. */
    private static final IsoPlayer[] whose = new IsoPlayer[View.COUNT];
    private static final Snapshot[][] kept = new Snapshot[View.COUNT][KEPT];
    private static final int[] count = new int[View.COUNT];
    private static final float[] played = new float[View.COUNT], sinceLast = new float[View.COUNT];
    private static final Held[] held = new Held[View.COUNT];
    /** The game's speed when the first death was held, put back once none is. */
    private static int speedBefore;
    /** Set while a held Kill is let run, so the hold does not take it again. */
    private static boolean passing;
    /** Seconds of play left that the zombies stand still for. */
    private static float stillFor;
    private static ByteBuffer buffer = ByteBuffer.allocate(FIRST_BUFFER);
    private static final List<InventoryItem> walked = new ArrayList<>();

    private static final JavaFunction ANSWER = Rewind::answered;
    private static final Field READ_BOOKS = readBooksField();

    private Rewind() {}

    private static Field readBooksField() {
        try {
            Field field = IsoGameCharacter.class.getDeclaredField("readBooks");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the game's record of the books a character has read has changed: " + e, e);
        }
    }

    private static List<?> readBooks(IsoPlayer player) {
        try {
            return (List<?>) READ_BOOKS.get(player);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("the books " + player.getPlayerNum() + " has read cannot be read: " + e, e);
        }
    }

    /** Called as the game starts a tick of the world: writes out each player whose second has come, and keeps a held death paused. */
    static void tick() {
        if (!Rules.rewind()) {
            // What was kept would be from before the row was off or the server, should either change back.
            for (int i = 0; i < View.COUNT; i++) {
                if (held[i] == null && count[i] > 0) {
                    forget(i);
                }
            }
            stillFor = 0f;
            return;
        }
        float seconds = GameTime.getInstance().getRealworldSecondsSinceLastUpdate();
        if (stillFor > 0f) {
            stillFor -= seconds;
            if (stillFor <= 0f) {
                Log.info("the zombies move again after a rewind");
            }
        }
        boolean anyHeld = false;
        for (int i = 0; i < View.COUNT; i++) {
            IsoPlayer player = IsoPlayer.players[i];
            if (player != whose[i]) {
                forget(i);
                whose[i] = player;
            }
            if (held[i] != null) {
                anyHeld = true;
                continue;
            }
            if (player == null || !player.isLocalPlayer() || player.isDead()) {
                continue;
            }
            played[i] += seconds;
            sinceLast[i] += seconds;
            if (count[i] == 0 || sinceLast[i] >= EVERY) {
                keep(i, player);
                sinceLast[i] = 0f;
            }
        }
        if (anyHeld && UIManager.getSpeedControls().getCurrentGameSpeed() != SpeedControls.PauseSpeed) {
            // The player unpaused with the panel up: the world waits for the answer.
            UIManager.getSpeedControls().SetCurrentGameSpeed(SpeedControls.PauseSpeed);
        }
    }

    /** Whether the zombies stand still: for ZOMBIES_STILL seconds of play after a rewind, while IsoZombie's updates are passed over. */
    static boolean zombiesStill() {
        return stillFor > 0f;
    }

    private static void keep(int index, IsoPlayer player) {
        while (true) {
            buffer.clear();
            try {
                player.save(buffer, false);
                break;
            } catch (BufferOverflowException full) {
                buffer = ByteBuffer.allocate(buffer.capacity() * 2);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
        walked.clear();
        walk(player.getInventory(), walked);
        BaseVehicle vehicle = player.getVehicle();
        Snapshot snapshot = new Snapshot(Arrays.copyOf(buffer.array(), buffer.position()), walked.toArray(new InventoryItem[0]),
                player.getHealth(), played[index], System.nanoTime(), vehicle, vehicle == null ? -1 : vehicle.getSeat(player));
        Snapshot[] ring = kept[index];
        if (count[index] == KEPT) {
            System.arraycopy(ring, 1, ring, 0, KEPT - 1);
            ring[KEPT - 1] = snapshot;
        } else {
            ring[count[index]++] = snapshot;
        }
    }

    /** Adds every item in a container to a list, the contents of each container in it included. */
    private static void walk(ItemContainer container, List<InventoryItem> into) {
        for (InventoryItem item : container.getItems()) {
            into.add(item);
            if (item instanceof InventoryContainer bag) {
                walk(bag.getInventory(), into);
            }
        }
    }

    private static Snapshot rewound(int index) {
        float[] takenAt = new float[count[index]];
        for (int i = 0; i < takenAt.length; i++) {
            takenAt[i] = kept[index][i].takenAt();
        }
        return kept[index][RewindRing.rewoundTo(takenAt, played[index])];
    }

    /**
     * Called at the start of IsoGameCharacter.Kill with what it was called with. Says whether to
     * leave the Kill out: it is a death Rewind holds for the player's answer.
     */
    static boolean holds(IsoGameCharacter dying, IsoGameCharacter killer, HandWeapon weapon, boolean gory, CharacterDiedListener listener) {
        // Kill sets the health to nought before it runs, and a death from a zombie's bite comes to it with the health nought already, so
        // the game's isDead is true of every death held here: a player already killed is told by onKillDone.
        if (passing || !(dying instanceof IsoPlayer player) || !Rules.rewind() || !player.isLocalPlayer() || player.isOnKillDone()) {
            return false;
        }
        int index = player.getPlayerNum();
        if (held[index] != null) {
            return true;
        }
        if (whose[index] != player || count[index] == 0) {
            Log.info("player " + index + " died with Rewind 30s on and no snapshot of them yet: the death goes on");
            return false;
        }
        show(index);
        SpeedControls speed = UIManager.getSpeedControls();
        if (!anyHeld()) {
            speedBefore = Math.max(speed.getCurrentGameSpeed(), SpeedControls.PlaySpeed);
        }
        held[index] = new Held(killer, weapon, gory, listener);
        speed.SetCurrentGameSpeed(SpeedControls.PauseSpeed);
        Log.info(String.format(Locale.ROOT, "player %d died: held for a rewind, killed by %s, the game paused, the snapshot to go back to is %.1f s old",
                index, killer == null ? "nothing" : killer.getClass().getSimpleName(), played[index] - rewound(index).takenAt()));
        return true;
    }

    private static boolean anyHeld() {
        for (Held each : held) {
            if (each != null) {
                return true;
            }
        }
        return false;
    }

    /** The panel, in the game's Lua: its own source, compiled into the game's Lua again whenever the game has loaded its Lua anew. */
    private static final String PANEL = """
            CameraUnlockRewind = ISPanelJoypad:derive("CameraUnlockRewind")
            CameraUnlockRewind.instance = {}

            function CameraUnlockRewind:new(playerIndex, lines)
                local o = ISPanelJoypad:new(getPlayerScreenLeft(playerIndex), getPlayerScreenTop(playerIndex),
                        getPlayerScreenWidth(playerIndex), getPlayerScreenHeight(playerIndex))
                setmetatable(o, self)
                self.__index = self
                o.background = false
                o.playerIndex = playerIndex
                o.lines = lines
                return o
            end

            function CameraUnlockRewind:createChildren()
                local text = getTextManager()
                local high = text:getFontHeight(UIFont.Small) + 6
                local wide = 20 + math.max(text:MeasureStringX(UIFont.Small, "Rewind 30s"), text:MeasureStringX(UIFont.Small, "Accept death"))
                local x = (self.width - wide) / 2
                local y = self.height - 50 - high * 2
                self.rise = ISButton:new(x, y, wide, high, "Rewind 30s", self, CameraUnlockRewind.onRise)
                self.rise:initialise()
                self:addChild(self.rise)
                self.die = ISButton:new(x, y + high + 10, wide, high, "Accept death", self, CameraUnlockRewind.onDie)
                self.die:initialise()
                self:addChild(self.die)
            end

            function CameraUnlockRewind:render()
                ISPanelJoypad.render(self)
                local high = getTextManager():getFontHeight(UIFont.Large) + 2
                local y = self.height / 2 - high * #self.lines
                for _, line in ipairs(self.lines) do
                    self:drawTextCentre(line, self.width / 2, y, 1, 1, 1, 1, UIFont.Large)
                    y = y + high
                end
            end

            function CameraUnlockRewind:close()
                if JoypadState.players[self.playerIndex + 1] then
                    setJoypadFocus(self.playerIndex, nil)
                end
                self:removeFromUIManager()
                CameraUnlockRewind.instance[self.playerIndex] = nil
            end

            function CameraUnlockRewind:answer(rises)
                self:close()
                CameraUnlockRewind.answered(self.playerIndex, rises)
                if rises then
                    CameraUnlockRewind.rearm(self.playerIndex)
                end
            end

            function CameraUnlockRewind.rearm(playerIndex)
                local hotbar = getPlayerHotbar(playerIndex)
                if hotbar then
                    hotbar:reloadIcons()
                end
            end

            function CameraUnlockRewind:onRise()
                self:answer(true)
            end

            function CameraUnlockRewind:onDie()
                self:answer(false)
            end

            function CameraUnlockRewind:onGainJoypadFocus(joypadData)
                ISPanelJoypad.onGainJoypadFocus(self, joypadData)
                self:setISButtonForA(self.rise)
                self:setISButtonForB(self.die)
            end

            function CameraUnlockRewind.show(playerIndex, rewind)
                local lines = { getGameTime():getDeathString(getSpecificPlayer(playerIndex)), "Go back to where you were " .. rewind .. (rewind == 1 and " second" or " seconds") .. " ago?" }
                local panel = CameraUnlockRewind:new(playerIndex, lines)
                panel:initialise()
                panel:addToUIManager()
                panel:setAlwaysOnTop(true)
                panel.javaObject:setIgnoreLossControl(true)
                CameraUnlockRewind.instance[playerIndex] = panel
                if JoypadState.players[playerIndex + 1] then
                    setJoypadFocus(playerIndex, panel)
                end
            end

            function CameraUnlockRewind.leave(playerIndex)
                local panel = CameraUnlockRewind.instance[playerIndex]
                if panel then
                    panel:close()
                end
            end
            """;

    private static KahluaTable panel() {
        if (!(LuaManager.env.rawget("CameraUnlockRewind") instanceof KahluaTable)) {
            Object compiled;
            try {
                compiled = LuaCompiler.loadstring(PANEL, "CameraUnlockRewind", LuaManager.env);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            ran("the Rewind 30s panel's Lua", LuaManager.caller.pcall(UIManager.getDefaultThread(), compiled, new Object[0]));
            ((KahluaTable) LuaManager.env.rawget("CameraUnlockRewind")).rawset("answered", ANSWER);
        }
        return (KahluaTable) LuaManager.env.rawget("CameraUnlockRewind");
    }

    private static void show(int index) {
        ran("CameraUnlockRewind.show", LuaManager.caller.pcall(UIManager.getDefaultThread(), panel().rawget("show"),
                new Object[] {(double) index, (double) Math.max(1, Math.round(played[index] - rewound(index).takenAt()))}));
    }

    private static void ran(String what, Object[] result) {
        if (!Boolean.TRUE.equals(result[0])) {
            throw new IllegalStateException(what + " failed: " + Arrays.toString(result));
        }
    }

    /** Called by the panel's buttons, with the player's number and whether they chose to rewind. */
    private static int answered(LuaCallFrame frame, int arguments) {
        int index = ((Double) frame.get(0)).intValue();
        boolean rises = Boolean.TRUE.equals(frame.get(1));
        IsoPlayer player = IsoPlayer.players[index];
        Held death = held[index];
        held[index] = null;
        if (!anyHeld()) {
            UIManager.getSpeedControls().SetCurrentGameSpeed(speedBefore);
        }
        if (rises) {
            rise(index, player);
        } else {
            Log.info("player " + index + " chose to die: the held death goes on");
            passing = true;
            try {
                player.Kill(death.killer(), death.weapon(), death.gory(), death.listener());
            } finally {
                passing = false;
            }
        }
        return 0;
    }

    /** Called as the game begins to leave a save, before it saves: a player whose death is held rewinds, so no save holds a held death. */
    static void leaving() {
        for (int i = 0; i < View.COUNT; i++) {
            if (held[i] != null) {
                held[i] = null;
                ran("CameraUnlockRewind.leave", LuaManager.caller.pcall(UIManager.getDefaultThread(), panel().rawget("leave"), new Object[] {(double) i}));
                Log.info("player " + i + " left the save with their death held: rewound before the game saves");
                rise(i, IsoPlayer.players[i]);
                ran("CameraUnlockRewind.rearm", LuaManager.caller.pcall(UIManager.getDefaultThread(), panel().rawget("rearm"), new Object[] {(double) i}));
            }
        }
    }

    /** Loads a held player from their snapshot, stands them up where it was taken and holds the zombies still. */
    private static void rise(int index, IsoPlayer player) {
        Snapshot snapshot = rewound(index);
        float age = played[index] - snapshot.takenAt();
        player.StopAllActionQueue();
        player.ensureNotInVehicle();
        String items = settleItems(player, snapshot);
        int woundsBefore = wounds(player);
        // IsoPlayer.load sets the worn items it read over what is worn, leaves the attachments alone and adds the recipes
        // and books it reads to those known: all are emptied for it.
        player.clearWornItems();
        player.getAttachedItems().clear();
        // BodyDamage.load reads a part's bandage before its wound times, and BodyPart.setBandaged(false, ...) wounds the part
        // again where a time it has now is above nought: those of a wound taken since the snapshot. They are noughted for it.
        for (BodyPart part : player.getBodyDamage().getBodyParts()) {
            part.setScratchTime(0f);
            part.setCutTime(0f);
            part.setBleedingTime(0f);
            part.setBiteTime(0f);
            part.setStitchTime(0f);
            part.setDeepWoundTime(0f);
        }
        player.getKnownRecipes().clear();
        player.getAlreadyReadBook().clear();
        readBooks(player).clear();
        try {
            player.load(ByteBuffer.wrap(snapshot.bytes()), IsoWorld.WorldVersion, false);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        int woundsLoaded = wounds(player);
        player.setHealth(snapshot.health());
        player.setOnKillDone(false);
        player.setOnDeathDone(false);
        player.setDeathDragDown(false);
        player.setAttackedBy(null);
        player.setKnockedDown(false);
        player.setOnFloor(false);
        player.setHitReaction("");
        player.setBumpType("");
        player.setPlayingDeathSound(false);
        player.setDefaultState();
        player.ensureOnTile();
        player.resetModelNextFrame();
        String seated = "";
        BaseVehicle vehicle = snapshot.vehicle();
        if (vehicle != null) {
            if (!vehicle.isRemovedFromWorld() && !vehicle.isSeatOccupied(snapshot.seat()) && vehicle.enter(snapshot.seat(), player)) {
                vehicle.setCharacterPosition(player, snapshot.seat(), "inside");
                vehicle.playPassengerAnim(snapshot.seat(), "idle");
                LuaEventManager.triggerEvent("OnEnterVehicle", player);
                seated = ", back in seat " + snapshot.seat() + " of the vehicle";
            } else {
                seated = ", standing where seat " + snapshot.seat() + " of a vehicle was: it is gone or the seat is taken";
            }
        }
        // IsoPlayer.load made every item anew, so the items each snapshot kept are no longer the player's, and the ring starts
        // again from here: a second death in this life goes back no further than this rewind.
        played[index] = snapshot.takenAt();
        Arrays.fill(kept[index], null);
        count[index] = 0;
        keep(index, player);
        sinceLast[index] = 0f;
        stillFor = RewindRing.ZOMBIES_STILL;
        Log.info(String.format(Locale.ROOT, "player %d rewound: loaded as they were %.1f s of play before (%.1f s on the clock), %d bytes, at (%.2f, %.2f, %.2f), health %.1f%s; "
                        + "wounded body parts %d before the load, %d after it, %d now; %s; the zombies stand still for %.0f s",
                index, age, (System.nanoTime() - snapshot.takenNanos()) / 1e9, snapshot.bytes().length, player.getX(), player.getY(), player.getZ(), player.getHealth(), seated,
                woundsBefore, woundsLoaded, wounds(player), items, RewindRing.ZOMBIES_STILL));
    }

    /**
     * Before a player is loaded from a snapshot: takes each item they had then and no longer
     * have from where it is now, and leaves each item they have taken up since on the ground
     * where they are. Says what it did, for the log.
     */
    private static String settleItems(IsoPlayer player, Snapshot snapshot) {
        Set<InventoryItem> had = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(had, snapshot.items());
        List<InventoryItem> now = new ArrayList<>();
        walk(player.getInventory(), now);
        Set<InventoryItem> has = Collections.newSetFromMap(new IdentityHashMap<>());
        has.addAll(now);

        int takenBack = 0;
        for (InventoryItem item : snapshot.items()) {
            if (has.contains(item)) {
                continue;
            }
            IsoWorldInventoryObject onGround = item.getWorldItem();
            if (onGround != null) {
                IsoGridSquare square = onGround.getSquare();
                square.transmitRemoveItemFromSquare(onGround);
                square.removeWorldObject(onGround);
                item.setWorldItem(null);
                takenBack++;
            } else if (item.getContainer() != null) {
                item.getContainer().Remove(item);
                takenBack++;
            }
        }

        IsoGridSquare where = player.getCurrentSquare();
        if (where == null) {
            throw new IllegalStateException("player " + player.getPlayerNum() + " rewinds standing on no square, so what they took up has nowhere to go");
        }
        int left = 0;
        for (InventoryItem item : now) {
            if (had.contains(item)) {
                continue;
            }
            InventoryItem bag = item.getContainer().getContainingItem();
            // What is in a bag taken up since goes down with the bag.
            if (bag == null || had.contains(bag)) {
                item.getContainer().Remove(item);
                where.AddWorldInventoryItem(item, player.getX() - where.getX(), player.getY() - where.getY(), player.getZ() - where.getZ());
                left++;
            }
        }
        return takenBack + " items they had then taken back from where they were, " + left + " taken up since left where they died";
    }

    /** How many of a player's body parts are cut, bitten, scratched, deeply wounded or bleeding. */
    private static int wounds(IsoPlayer player) {
        int wounded = 0;
        for (BodyPart part : player.getBodyDamage().getBodyParts()) {
            if (part.isCut() || part.bitten() || part.scratched() || part.deepWounded() || part.bleeding()) {
                wounded++;
            }
        }
        return wounded;
    }

    private static void forget(int index) {
        Arrays.fill(kept[index], null);
        count[index] = 0;
        played[index] = 0f;
        sinceLast[index] = 0f;
        held[index] = null;
        whose[index] = null;
    }

    /** Called as the game leaves a save for its menu: nothing of a save's players is kept into another. */
    static void forget() {
        for (int i = 0; i < View.COUNT; i++) {
            forget(i);
        }
        passing = false;
        stillFor = 0f;
    }
}
