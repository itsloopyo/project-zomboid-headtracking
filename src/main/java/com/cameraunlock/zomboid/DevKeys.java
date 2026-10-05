package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import zombie.GameTime;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.input.GameKeyboard;
import zombie.inventory.InventoryItem;
import zombie.inventory.InventoryItemFactory;
import zombie.inventory.types.HandWeapon;
import zombie.inventory.types.WeaponPart;
import zombie.iso.IsoWorld;
import zombie.iso.weather.ClimateManager;
import zombie.network.GameClient;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehiclePart;

/**
 * Keys for whoever is working on the mod, there only in a development install: the deploy
 * script leaves a marker file beside the jar, and the installer a player runs does not.
 * Each is on a key the game's default bindings leave alone. The game acts on a key whatever
 * modifier is held with it, so F2 to F6, its pause and speed keys, cannot be shared with it.
 *
 * F7 takes the nearest vehicle within a few paces: mends it, fills its tank, hot-wires it and
 * sits the player in the driver's seat, whatever state it was found in. For looking at
 * vehicles and driving without first finding one that runs.
 *
 * The apostrophe makes the player invincible, and vulnerable again on the next press.
 *
 * 0 clears the loaded map of zombies and keeps it clear, and lets them back on the next
 * press: for looking at the picture in peace.
 *
 * The full stop moves the player to the next of the places worth looking at, round and round,
 * and the comma to the one before.
 *
 * 9 sets the clock to early evening, with the sun starting down: the hour the picture looks
 * its best.
 *
 * The semicolon puts a pump action shotgun in the player's hands, and from then on whatever firearm is
 * held is kept loaded, sound and unjammed: for trying the view as a shooter without first
 * finding a gun and shells.
 *
 * The left square bracket does the same with a hunting rifle that has a scope on it: the
 * weakest of the game's three on the first press, the next on each press after, and round
 * again. For looking through each scope without finding one and a screwdriver.
 *
 * The slash puts a flashlight in the player's left hand, charged and lit: for
 * the beam that follows the head, and for night, without finding one and a battery. A weapon
 * in the right hand stays there.
 */
final class DevKeys {
    static final String MARKER = "ProjectZomboidHeadTracking.dev";
    /** F7 in the key codes the game's keyboard uses. */
    private static final int TAKE_VEHICLE_KEY = 65;
    private static final float TAKE_WITHIN = 5f;
    /** The apostrophe. */
    private static final int INVINCIBLE_KEY = 40;

    /** 0 on the number row. */
    private static final int CLEAR_KEY = 11;
    private static boolean cleared;
    /** The full stop and the comma. */
    private static final int NEXT_PLACE_KEY = 52, LAST_PLACE_KEY = 51;

    /** A tile on the ground. */
    private record Place(String name, float x, float y) {}

    /**
     * The first four and the West Point and Louisville ones are spots the picture has been
     * judged from. The rest are the game's own spawn points for each town, so there is a
     * floor to stand on at every one.
     */
    private static final Place[] PLACES = {
            new Place("Riverside, the lake shore", 6442.5f, 5187.5f),
            new Place("a wood by the railway", 10900.5f, 8500.5f),
            new Place("open farmland", 9050.5f, 8700.5f),
            new Place("Muldraugh, a street", 10792.5f, 9776.5f),
            new Place("West Point, the church and the park", 11930.5f, 6880.5f),
            new Place("West Point, by the river", 11700.5f, 6730.5f),
            new Place("the railway east of West Point", 12177.5f, 6838.5f),
            new Place("Louisville", 12700.5f, 2300.5f),
            new Place("Valley Station, a house", 12595.5f, 5330.5f),
            new Place("Rosewood, a house", 7976.5f, 11402.5f),
            new Place("March Ridge, a house", 9883.5f, 12812.5f),
            new Place("Fallas Lake, a house", 7213.5f, 8288.5f),
            new Place("Echo Creek, the gas station shop", 3573.5f, 10899.5f),
            new Place("Brandenburg, a house", 2152.5f, 6089.5f),
            new Place("Ekron, a house", 411.5f, 9761.5f),
            new Place("Irvington, a house", 1912.5f, 14381.5f),
    };
    /** The place the player was last sent to, and none before the first press. */
    private static int place = -1;
    /** 9 on the number row. */
    private static final int EVENING_KEY = 10;
    /** How long before dusk the clock is set to, in hours. */
    private static final float BEFORE_DUSK = 1.75f;
    /** The semicolon. */
    private static final int ARM_KEY = 39;
    private static final String SHOTGUN = "Base.Shotgun";
    /** The left square bracket, which is beside the semicolon and the apostrophe. */
    private static final int SCOPED_KEY = 26;
    /** The slash, beside the full stop. The right square bracket is the game's own, for its furniture panel. */
    private static final int TORCH_KEY = 53;
    private static final String TORCH = "Base.HandTorch";
    private static final String RIFLE = "Base.HuntingRifle";
    private static int scope = -1;

    private static boolean armed;
    private static boolean invincible, wasInvincible;
    private static Field cannotDie;
    private static Field godModes;
    private static Object godMode;
    private static boolean looked;
    private static boolean enabled;

    private DevKeys() {}

    /**
     * Called with whether the game allows its own cheats, which outside its debug mode and off
     * a server it does not: its god mode then reads as off whatever is set. While the key has the
     * player invincible in a development install they are allowed. A game joined to a server
     * allows them by its own rules already, so this never adds anything there.
     */
    static boolean cheatsAllowed(boolean stock) {
        return stock || invincible;
    }

    /**
     * Logic thread, once a frame. The marker is looked for here and not as the agent starts:
     * this class names the game's player class, and a class the agent loads before its patches
     * are in place brings the game's classes in unpatched.
     */
    static void poll() {
        if (!looked) {
            looked = true;
            enabled = Files.exists(Path.of("").toAbsolutePath().resolve(MARKER));
            if (enabled) {
                Log.info("development install: 0 clears out the zombies, . goes to the next place and , to the one before, 9 sets early evening, ; gives a shotgun that never runs dry, [ gives a hunting rifle with the next of the three scopes on it, ' makes you invincible, F7 takes the nearest vehicle");
            }
        }
        // On a server every one of these would be a cheat against the other players.
        if (!enabled || GameClient.client) {
            invincible = false;
            cleared = false;
            armed = false;
            return;
        }
        IsoPlayer safe = IsoPlayer.getInstance();
        if (GameKeyboard.isKeyPressed(INVINCIBLE_KEY)) {
            invincible = !invincible;
            Log.info("': invincible " + invincible);
        }
        // The game's own god mode is refused outside its debug mode, so the player is made
        // whole again every frame instead: wounds, bites and infection are gone as they land.
        if (safe != null && (invincible || wasInvincible)) {
            keepWhole(safe);
        }
        if (GameKeyboard.isKeyPressed(CLEAR_KEY)) {
            cleared = !cleared;
            Log.info("0: zombies cleared " + cleared);
        }
        if (cleared && IsoWorld.instance.currentCell != null) {
            ArrayList<IsoZombie> zombies = IsoWorld.instance.currentCell.getZombieList();
            for (int i = zombies.size() - 1; i >= 0; i--) {
                IsoZombie zombie = zombies.get(i);
                zombie.removeFromWorld();
                zombie.removeFromSquare();
            }
        }
        boolean back = GameKeyboard.isKeyPressed(LAST_PLACE_KEY);
        if (safe != null && safe.getVehicle() == null && (back || GameKeyboard.isKeyPressed(NEXT_PLACE_KEY))) {
            place = back ? Math.floorMod(Math.max(place, 0) - 1, PLACES.length) : (place + 1) % PLACES.length;
            Place to = PLACES[place];
            safe.teleportTo(to.x(), to.y(), 0);
            Log.info((back ? "," : ".") + ": moved to " + to.name() + " (" + to.x() + ", " + to.y() + "), place " + (place + 1) + " of " + PLACES.length);
        }
        if (GameKeyboard.isKeyPressed(EVENING_KEY)) {
            float hour = ClimateManager.getInstance().getSeason().getDusk() - BEFORE_DUSK;
            GameTime.getInstance().setTimeOfDay(hour);
            Log.info("9: the clock set to " + hour);
        }
        if (safe != null && GameKeyboard.isKeyPressed(ARM_KEY)) {
            InventoryItem shotgun = safe.getInventory().AddItem(SHOTGUN);
            safe.setPrimaryHandItem(shotgun);
            safe.setSecondaryHandItem(shotgun);
            armed = true;
            Log.info(";: a shotgun in hand, kept loaded");
        }
        if (safe != null && GameKeyboard.isKeyPressed(SCOPED_KEY)) {
            scope = (scope + 1) % ScopeOptics.KNOWN.length;
            String part = ScopeOptics.KNOWN[scope].part();
            HandWeapon rifle = (HandWeapon) safe.getInventory().AddItem(RIFLE);
            rifle.attachWeaponPart(InventoryItemFactory.<WeaponPart>CreateItem(part));
            safe.setPrimaryHandItem(rifle);
            safe.setSecondaryHandItem(rifle);
            armed = true;
            Log.info("[: a hunting rifle in hand with " + part + " on it, kept loaded");
        }
        if (safe != null && GameKeyboard.isKeyPressed(TORCH_KEY)) {
            InventoryItem torch = safe.getInventory().AddItem(TORCH);
            torch.setCurrentUsesFloat(1f);
            safe.setSecondaryHandItem(torch);
            torch.setActivated(true);
            Log.info("/: a flashlight in the left hand, charged and lit");
        }
        if (armed && safe != null && safe.getPrimaryHandItem() instanceof HandWeapon held && held.isAimedFirearm()) {
            held.setCurrentAmmoCount(held.getMaxAmmo());
            held.setRoundChambered(true);
            held.setJammed(false);
            held.setCondition(held.getConditionMax());
        }
        if (GameKeyboard.isKeyPressed(TAKE_VEHICLE_KEY)) {
            takeNearestVehicle();
        }
    }

    private static void keepWhole(IsoPlayer safe) {
        // Health put back each frame is not enough: a crowd pulls a player down and kills
        // outright. The game's own switch for a character that cannot die is behind a
        // role check, so the field is set directly.
        try {
            if (cannotDie == null) {
                cannotDie = IsoGameCharacter.class.getDeclaredField("invincible");
                cannotDie.setAccessible(true);
            }
            cannotDie.setBoolean(safe, invincible);
            // The game's own god mode is one of a set of cheats, and every way the game
            // has of setting one asks first whether cheats are allowed, which they are not
            // outside its debug mode. The set itself is written to.
            if (godModes == null) {
                Object cheats = IsoGameCharacter.class.getMethod("getCheats").invoke(safe);
                Field held = cheats.getClass().getDeclaredField("cheats");
                held.setAccessible(true);
                godModes = held;
                godMode = Class.forName("zombie.characters.CheatType").getField("GOD_MODE").get(null);
            }
            @SuppressWarnings("unchecked")
            Set<Object> set = (Set<Object>) godModes.get(
                    IsoGameCharacter.class.getMethod("getCheats").invoke(safe));
            if (invincible) {
                set.add(godMode);
            } else {
                set.remove(godMode);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the game's character cannot be made invincible this way: " + e, e);
        }
        safe.setAvoidDamage(invincible);
        if (invincible && !safe.isDead()) {
            safe.getBodyDamage().RestoreToFullHealth();
        }
        wasInvincible = invincible;
    }

    private static void takeNearestVehicle() {
        IsoPlayer player = IsoPlayer.getInstance();
        if (player == null || player.getVehicle() != null) {
            return;
        }
        BaseVehicle nearest = null;
        float nearestSquared = TAKE_WITHIN * TAKE_WITHIN;
        for (BaseVehicle vehicle : IsoWorld.instance.currentCell.getVehicles()) {
            float toX = vehicle.getX() - player.getX(), toY = vehicle.getY() - player.getY();
            if (toX * toX + toY * toY < nearestSquared) {
                nearestSquared = toX * toX + toY * toY;
                nearest = vehicle;
            }
        }
        if (nearest == null) {
            Log.info("F7: no vehicle within " + TAKE_WITHIN + " tiles");
            return;
        }
        nearest.repair();
        VehiclePart tank = nearest.getParts().getPartById("GasTank");
        if (tank != null) {
            tank.setContainerContentAmount(tank.getContainerCapacity());
        }
        nearest.setHotwired(true);
        boolean seated = nearest.enter(0, player);
        Log.info("F7: took the vehicle at (" + nearest.getX() + ", " + nearest.getY() + "), seated " + seated);
    }
}
