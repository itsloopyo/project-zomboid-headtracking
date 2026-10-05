package com.cameraunlock.zomboid;

import java.nio.file.Files;
import java.nio.file.Path;
import zombie.GameTime;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.input.GameKeyboard;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoWorld;
import zombie.network.GameClient;
import zombie.iso.weather.ClimateManager;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehiclePart;

/**
 * Keys for whoever is working on the mod, there only in a development install: the deploy
 * script leaves a marker file beside the jar, and the installer a player runs does not.
 *
 * F7 takes the nearest vehicle within a few paces: mends it, fills its tank, hot-wires it and
 * sits the player in the driver's seat, whatever state it was found in. For looking at
 * vehicles and driving without first finding one that runs.
 *
 * F6 makes the player invincible, and vulnerable again on the next press.
 *
 * F2 clears the loaded map of zombies and keeps it clear, and lets them back on the next
 * press: for looking at the picture in peace.
 *
 * F3 moves the player to the next of a few places worth looking at: a lake shore, a wood, open
 * farmland, a town street.
 *
 * F4 sets the clock to early evening, with the sun starting down: the hour the picture looks
 * its best.
 *
 * F5 puts a pump action shotgun in the player's hands, and from then on whatever firearm is
 * held is kept loaded, sound and unjammed: for trying the view as a shooter without first
 * finding a gun and shells.
 */
final class DevKeys {
    static final String MARKER = "ProjectZomboidHeadTracking.dev";
    /** F7 in the key codes the game's keyboard uses. */
    private static final int TAKE_VEHICLE_KEY = 65;
    private static final float TAKE_WITHIN = 5f;
    /** F6. */
    private static final int INVINCIBLE_KEY = 64;

    /** F2. */
    private static final int CLEAR_KEY = 60;
    private static boolean cleared;
    /** F3. */
    private static final int PLACE_KEY = 61;
    /** Tile x and y of each place, on the ground. */
    private static final float[][] PLACES = {{6442.5f, 5187.5f}, {10900.5f, 8500.5f}, {9050.5f, 8700.5f}, {10792.5f, 9776.5f}};
    private static int place;
    /** F4. */
    private static final int EVENING_KEY = 62;
    /** How long before dusk the clock is set to, in hours. */
    private static final float BEFORE_DUSK = 1.75f;
    /** F5. */
    private static final int ARM_KEY = 63;
    private static final String SHOTGUN = "Base.Shotgun";

    private static boolean armed;
    private static boolean invincible, wasInvincible;
    private static java.lang.reflect.Field cannotDie;
    private static java.lang.reflect.Field godModes;
    private static Object godMode;
    private static boolean looked;
    private static boolean enabled;

    private DevKeys() {}

    /**
     * Called with whether the game allows its own cheats, which outside its debug mode and off
     * a server it does not: its god mode then reads as off whatever is set. While F6 has the
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
                Log.info("development install: F2 clears out the zombies, F3 goes to the next view, F4 sets early evening, F5 gives a shotgun that never runs dry, F6 makes you invincible, F7 takes the nearest vehicle");
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
            Log.info("F6: invincible " + invincible);
        }
        // The game's own god mode is refused outside its debug mode, so the player is made
        // whole again every frame instead: wounds, bites and infection are gone as they land.
        if (safe != null && (invincible || wasInvincible)) {
            // Health put back each frame is not enough: a crowd pulls a player down and kills
            // outright. The game's own switch for a character that cannot die is behind a
            // role check, so the field is set directly.
            try {
                if (cannotDie == null) {
                    cannotDie = zombie.characters.IsoGameCharacter.class.getDeclaredField("invincible");
                    cannotDie.setAccessible(true);
                }
                cannotDie.setBoolean(safe, invincible);
                // The game's own god mode is one of a set of cheats, and every way the game
                // has of setting one asks first whether cheats are allowed, which they are not
                // outside its debug mode. The set itself is written to.
                if (godModes == null) {
                    Object cheats = zombie.characters.IsoGameCharacter.class.getMethod("getCheats").invoke(safe);
                    java.lang.reflect.Field held = cheats.getClass().getDeclaredField("cheats");
                    held.setAccessible(true);
                    godModes = held;
                    godMode = Class.forName("zombie.characters.CheatType").getField("GOD_MODE").get(null);
                }
                @SuppressWarnings("unchecked")
                java.util.Set<Object> set = (java.util.Set<Object>) godModes.get(
                        zombie.characters.IsoGameCharacter.class.getMethod("getCheats").invoke(safe));
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
        if (GameKeyboard.isKeyPressed(CLEAR_KEY)) {
            cleared = !cleared;
            Log.info("F2: zombies cleared " + cleared);
        }
        if (cleared && IsoWorld.instance.currentCell != null) {
            java.util.ArrayList<IsoZombie> zombies = IsoWorld.instance.currentCell.getZombieList();
            for (int i = zombies.size() - 1; i >= 0; i--) {
                IsoZombie zombie = zombies.get(i);
                zombie.removeFromWorld();
                zombie.removeFromSquare();
            }
        }
        if (safe != null && safe.getVehicle() == null && GameKeyboard.isKeyPressed(PLACE_KEY)) {
            float[] to = PLACES[place++ % PLACES.length];
            safe.teleportTo(to[0], to[1], 0);
            Log.info("F3: moved to (" + to[0] + ", " + to[1] + ")");
        }
        if (GameKeyboard.isKeyPressed(EVENING_KEY)) {
            float hour = ClimateManager.getInstance().getSeason().getDusk() - BEFORE_DUSK;
            GameTime.getInstance().setTimeOfDay(hour);
            Log.info("F4: the clock set to " + hour);
        }
        if (safe != null && GameKeyboard.isKeyPressed(ARM_KEY)) {
            InventoryItem shotgun = safe.getInventory().AddItem(SHOTGUN);
            safe.setPrimaryHandItem(shotgun);
            safe.setSecondaryHandItem(shotgun);
            armed = true;
            Log.info("F5: a shotgun in hand, kept loaded");
        }
        if (armed && safe != null && safe.getPrimaryHandItem() instanceof HandWeapon held && held.isAimedFirearm()) {
            held.setCurrentAmmoCount(held.getMaxAmmo());
            held.setRoundChambered(true);
            held.setJammed(false);
            held.setCondition(held.getConditionMax());
        }
        if (!GameKeyboard.isKeyPressed(TAKE_VEHICLE_KEY)) {
            return;
        }
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
