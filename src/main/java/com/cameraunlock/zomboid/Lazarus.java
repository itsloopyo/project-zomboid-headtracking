package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Locale;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.JavaFunction;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.LuaCallFrame;
import zombie.GameTime;
import zombie.Lua.LuaEventManager;
import zombie.Lua.LuaManager;
import zombie.characters.CharacterDiedListener;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoWorld;
import zombie.ui.SpeedControls;
import zombie.ui.UIManager;
import zombie.vehicles.BaseVehicle;

/**
 * Lazarus, held back for a later release and switched on with -Dcameraunlock.zomboid.lazarus=true:
 * offline, a player at this screen who dies is asked whether to die
 * or to go back to how they were REWIND seconds before. Only the player goes back. The world
 * goes on as it was, the zombie that killed them included.
 *
 * Every second of play each player is written out with the game's own IsoPlayer.save, which is
 * what the game keeps of them in a save: where they stand, their body, wounds and infection
 * included, what they carry, wear and hold, and what they know. A death is held at the start of
 * IsoGameCharacter.Kill, before the game has made a body, deleted the player's file from the save
 * or told its Lua, and the game is paused under a panel of two buttons. Lazarus loads the
 * player from the snapshot with IsoPlayer.load. The other button lets the held Kill run, and the
 * death goes on as the game has it.
 *
 * All of it runs on the game's main thread: the world's tick, Kill and the panel's buttons.
 */
final class Lazarus {
    private static final float REWIND = LazarusRing.REWIND, EVERY = LazarusRing.EVERY;
    private static final int KEPT = LazarusRing.KEPT;
    private static final int FIRST_BUFFER = 256 * 1024;

    /** One player as they were. */
    private record Snapshot(byte[] bytes, float health, float takenAt, BaseVehicle vehicle, int seat) {}

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
    private static ByteBuffer buffer = ByteBuffer.allocate(FIRST_BUFFER);

    private static final JavaFunction ANSWER = Lazarus::answered;

    private Lazarus() {}

    /** Called as the game starts a tick of the world: writes out each player whose second has come, and keeps a held death paused. */
    static void tick() {
        if (Online.joined()) {
            // What was kept would be from before the server, should the player come back to a game alone.
            for (int i = 0; i < View.COUNT; i++) {
                if (held[i] == null && count[i] > 0) {
                    forget(i);
                }
            }
            return;
        }
        float seconds = GameTime.getInstance().getRealworldSecondsSinceLastUpdate();
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
        BaseVehicle vehicle = player.getVehicle();
        Snapshot snapshot = new Snapshot(Arrays.copyOf(buffer.array(), buffer.position()), player.getHealth(), played[index],
                vehicle, vehicle == null ? -1 : vehicle.getSeat(player));
        Snapshot[] ring = kept[index];
        if (count[index] == KEPT) {
            System.arraycopy(ring, 1, ring, 0, KEPT - 1);
            ring[KEPT - 1] = snapshot;
        } else {
            ring[count[index]++] = snapshot;
        }
    }

    private static Snapshot rewound(int index) {
        float[] takenAt = new float[count[index]];
        for (int i = 0; i < takenAt.length; i++) {
            takenAt[i] = kept[index][i].takenAt();
        }
        return kept[index][LazarusRing.rewoundTo(takenAt, played[index])];
    }

    /**
     * Called at the start of IsoGameCharacter.Kill with what it was called with. Says whether to
     * leave the Kill out: it is a death Lazarus holds for the player's answer.
     */
    static boolean holds(IsoGameCharacter dying, IsoGameCharacter killer, HandWeapon weapon, boolean gory, CharacterDiedListener listener) {
        if (passing || !(dying instanceof IsoPlayer player) || !player.isLocalPlayer() || player.isDead() || Online.joined()) {
            return false;
        }
        int index = player.getPlayerNum();
        if (held[index] != null) {
            return true;
        }
        if (whose[index] != player || count[index] == 0) {
            Log.info("player " + index + " died with Lazarus on and no snapshot of them yet: the death goes on");
            return false;
        }
        show(index);
        SpeedControls speed = UIManager.getSpeedControls();
        if (!anyHeld()) {
            speedBefore = Math.max(speed.getCurrentGameSpeed(), SpeedControls.PlaySpeed);
        }
        held[index] = new Held(killer, weapon, gory, listener);
        speed.SetCurrentGameSpeed(SpeedControls.PauseSpeed);
        Log.info(String.format(Locale.ROOT, "player %d died: held for Lazarus, killed by %s, the game paused, the snapshot to go back to is %.1f s old",
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
            CameraUnlockLazarus = ISPanelJoypad:derive("CameraUnlockLazarus")
            CameraUnlockLazarus.instance = {}

            function CameraUnlockLazarus:new(playerIndex, lines)
                local o = ISPanelJoypad:new(getPlayerScreenLeft(playerIndex), getPlayerScreenTop(playerIndex),
                        getPlayerScreenWidth(playerIndex), getPlayerScreenHeight(playerIndex))
                setmetatable(o, self)
                self.__index = self
                o.background = false
                o.playerIndex = playerIndex
                o.lines = lines
                return o
            end

            function CameraUnlockLazarus:createChildren()
                local text = getTextManager()
                local high = text:getFontHeight(UIFont.Small) + 6
                local wide = 20 + math.max(text:MeasureStringX(UIFont.Small, "Lazarus"), text:MeasureStringX(UIFont.Small, "Accept death"))
                local x = (self.width - wide) / 2
                local y = self.height - 50 - high * 2
                self.rise = ISButton:new(x, y, wide, high, "Lazarus", self, CameraUnlockLazarus.onRise)
                self.rise:initialise()
                self:addChild(self.rise)
                self.die = ISButton:new(x, y + high + 10, wide, high, "Accept death", self, CameraUnlockLazarus.onDie)
                self.die:initialise()
                self:addChild(self.die)
            end

            function CameraUnlockLazarus:render()
                ISPanelJoypad.render(self)
                local high = getTextManager():getFontHeight(UIFont.Large) + 2
                local y = self.height / 2 - high * #self.lines
                for _, line in ipairs(self.lines) do
                    self:drawTextCentre(line, self.width / 2, y, 1, 1, 1, 1, UIFont.Large)
                    y = y + high
                end
            end

            function CameraUnlockLazarus:answer(rises)
                if JoypadState.players[self.playerIndex + 1] then
                    setJoypadFocus(self.playerIndex, nil)
                end
                self:removeFromUIManager()
                CameraUnlockLazarus.instance[self.playerIndex] = nil
                CameraUnlockLazarus.answered(self.playerIndex, rises)
            end

            function CameraUnlockLazarus:onRise()
                self:answer(true)
            end

            function CameraUnlockLazarus:onDie()
                self:answer(false)
            end

            function CameraUnlockLazarus:onGainJoypadFocus(joypadData)
                ISPanelJoypad.onGainJoypadFocus(self, joypadData)
                self:setISButtonForA(self.rise)
                self:setISButtonForB(self.die)
            end

            function CameraUnlockLazarus.show(playerIndex, rewind)
                local lines = { getGameTime():getDeathString(getSpecificPlayer(playerIndex)), "Lazarus takes you back " .. rewind .. " seconds." }
                local panel = CameraUnlockLazarus:new(playerIndex, lines)
                panel:initialise()
                panel:addToUIManager()
                panel:setAlwaysOnTop(true)
                panel.javaObject:setIgnoreLossControl(true)
                CameraUnlockLazarus.instance[playerIndex] = panel
                if JoypadState.players[playerIndex + 1] then
                    setJoypadFocus(playerIndex, panel)
                end
            end
            """;

    private static void show(int index) {
        if (!(LuaManager.env.rawget("CameraUnlockLazarus") instanceof KahluaTable)) {
            Object compiled;
            try {
                compiled = LuaCompiler.loadstring(PANEL, "CameraUnlockLazarus", LuaManager.env);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            ran("the Lazarus panel's Lua", LuaManager.caller.pcall(UIManager.getDefaultThread(), compiled, new Object[0]));
            ((KahluaTable) LuaManager.env.rawget("CameraUnlockLazarus")).rawset("answered", ANSWER);
        }
        KahluaTable panel = (KahluaTable) LuaManager.env.rawget("CameraUnlockLazarus");
        ran("CameraUnlockLazarus.show", LuaManager.caller.pcall(UIManager.getDefaultThread(), panel.rawget("show"),
                new Object[] {(double) index, String.format(Locale.ROOT, "%.0f", REWIND)}));
    }

    private static void ran(String what, Object[] result) {
        if (!Boolean.TRUE.equals(result[0])) {
            throw new IllegalStateException(what + " failed: " + Arrays.toString(result));
        }
    }

    /** Called by the panel's buttons, with the player's number and whether they chose Lazarus. */
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

    /** Loads a held player from their snapshot and stands them up where it was taken. */
    private static void rise(int index, IsoPlayer player) {
        Snapshot snapshot = rewound(index);
        float age = played[index] - snapshot.takenAt();
        player.StopAllActionQueue();
        player.ensureNotInVehicle();
        try {
            player.load(ByteBuffer.wrap(snapshot.bytes()), IsoWorld.WorldVersion, false);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
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
        // Played on from the snapshot, so a second death in this life goes back from here.
        played[index] = snapshot.takenAt();
        int keptUpTo = 0;
        while (keptUpTo < count[index] && kept[index][keptUpTo] != snapshot) {
            keptUpTo++;
        }
        Arrays.fill(kept[index], keptUpTo + 1, KEPT, null);
        count[index] = keptUpTo + 1;
        sinceLast[index] = 0f;
        Log.info(String.format(Locale.ROOT, "player %d rose with Lazarus: loaded as they were %.1f s before, %d bytes, at (%.2f, %.2f, %.2f), health %.1f%s",
                index, age, snapshot.bytes().length, player.getX(), player.getY(), player.getZ(), player.getHealth(), seated));
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
    }
}
