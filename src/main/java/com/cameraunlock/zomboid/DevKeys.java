package com.cameraunlock.zomboid;

import java.nio.file.Files;
import java.nio.file.Path;
import zombie.characters.IsoPlayer;
import zombie.input.GameKeyboard;
import zombie.iso.IsoWorld;
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
 */
final class DevKeys {
    static final String MARKER = "ProjectZomboidHeadTracking.dev";
    /** F7 in the key codes the game's keyboard uses. */
    private static final int TAKE_VEHICLE_KEY = 65;
    private static final float TAKE_WITHIN = 5f;
    /** F6. */
    private static final int INVINCIBLE_KEY = 64;

    private static boolean invincible, wasInvincible;
    private static java.lang.reflect.Field cannotDie;
    private static boolean looked;
    private static boolean enabled;

    private DevKeys() {}

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
                Log.info("development install: F6 makes you invincible, F7 takes the nearest vehicle");
            }
        }
        if (!enabled) {
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
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("the game's character has no invincible field: " + e, e);
            }
            safe.setAvoidDamage(invincible);
            if (invincible && !safe.isDead()) {
                safe.getBodyDamage().RestoreToFullHealth();
            }
            wasInvincible = invincible;
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
