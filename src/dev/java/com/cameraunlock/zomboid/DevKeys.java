package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Set;
import zombie.GameTime;
import zombie.VirtualZombieManager;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.Core;
import zombie.input.GameKeyboard;
import zombie.inventory.InventoryItem;
import zombie.inventory.InventoryItemFactory;
import zombie.inventory.types.HandWeapon;
import zombie.inventory.types.WeaponPart;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.weather.ClimateManager;
import zombie.network.GameClient;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehiclePart;

/**
 * Keys for whoever is working on the mod, there only in a development build: the jar a
 * player installs is compiled with the empty class of this name in src/shipped in this one's place.
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
 * 9 puts the clock on by three hours, into the next day if that is where they end: for
 * seeing the picture at any hour without waiting for it.
 *
 * Backspace holds the weather at the next of its kinds, from a clear sky through cloud, rain
 * and fog to snow half melted and a blizzard over lying snow, and after the last gives it back
 * to the game. The game keeps what it is held at in the save.
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
public final class DevKeys {
    static final boolean PRESENT = true;
    /** F7 in the key codes the game's keyboard uses. */
    private static final int TAKE_VEHICLE_KEY = 65;
    private static final float TAKE_WITHIN = 5f;
    /** The apostrophe. */
    private static final int INVINCIBLE_KEY = 40;

    /** 0 on the number row. */
    private static final int CLEAR_KEY = 11;
    private static boolean cleared;
    /** Zombies put back when the clearing is switched off: how many places are tried, the nearest in tiles, and how much further each next ring is. */
    private static final int BACK_TRIED = 18;
    private static final float BACK_NEAREST = 9f, BACK_STEP = 4f;
    /** The full stop and the comma. */
    private static final int NEXT_PLACE_KEY = 52, LAST_PLACE_KEY = 51;

    /** A tile and the level it is on. */
    private record Place(String name, float x, float y, int level) {
        Place(String name, float x, float y) {
            this(name, x, y, 0);
        }
    }

    /**
     * The first is the street south of the map's tallest building, 28 floors over tiles
     * 12751 to 12793 by 1512 to 1544, and the second is on its roof. The next four and the West Point and Louisville ones
     * are spots the picture has been judged from. The rest are the game's own spawn points
     * for each town, so there is a floor to stand on at every one.
     */
    private static final Place[] PLACES = {
            new Place("Louisville, the skyscraper", 12772.5f, 1565.5f),
            new Place("Louisville, the skyscraper's roof", 12785.415f, 1523.9503f, 26),
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
    private static final int LATER_KEY = 10;
    /** How far a press puts the clock on, in hours. */
    private static final float LATER = 3f;
    /** Backspace: of the keys the game leaves alone, the nearest to 9 on its row. */
    private static final int WEATHER_KEY = 14;

    /**
     * A kind of weather as the game's climate has it: each of cloud, rain or snow, wind and fog
     * from 0 to 1, and how much of the ground lies under snow, from 0 to 1.
     */
    private record Weather(String name, float cloud, float falling, float wind, float fog, boolean snow, float lying) {}

    private static final Weather[] WEATHERS = {
            new Weather("a clear sky", 0f, 0f, 0.05f, 0f, false, 0f),
            new Weather("some clouds", 0.35f, 0f, 0.1f, 0f, false, 0f),
            new Weather("cloudy", 0.85f, 0f, 0.15f, 0f, false, 0f),
            new Weather("raining", 0.85f, 0.4f, 0.1f, 0f, false, 0f),
            new Weather("rain with some wind", 0.9f, 0.5f, 0.4f, 0f, false, 0f),
            new Weather("a storm of rain", 1f, 1f, 0.9f, 0f, false, 0f),
            new Weather("a light fog", 0.6f, 0f, 0.05f, 0.3f, false, 0f),
            new Weather("a thick fog", 0.7f, 0f, 0f, 1f, false, 0f),
            new Weather("snow half melted", 0.5f, 0f, 0.1f, 0f, false, 0.3f),
            new Weather("snow on the ground", 0.35f, 0f, 0.1f, 0f, false, 1f),
            new Weather("fog over snow", 0.7f, 0f, 0f, 0.7f, false, 0.7f),
            new Weather("light snow falling", 0.85f, 0.3f, 0.15f, 0f, true, 0.7f),
            new Weather("a blizzard", 1f, 1f, 0.9f, 0f, true, 1f),
    };
    /** The weather being held, and none while it is the game's own. */
    private static int weather = -1;
    /** Lying snow drifts from what is held by more than this before it is set again. */
    private static final float LYING_DRIFT = 0.05f;
    /** The game's snow strength at which the whole ground lies under snow. */
    private static final float FULL_SNOW_STRENGTH = 7.5f;
    private static Field snowStrength, wasForceSnow;
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
    private static boolean said;

    private DevKeys() {}

    /**
     * Called with whether the game allows its own cheats, which outside its debug mode and off
     * a server it does not: its god mode then reads as off whatever is set. While the key has the
     * player invincible they are allowed. A game joined to a server allows them by its own
     * rules already, so this never adds anything there.
     */
    public static boolean cheatsAllowed(boolean stock) {
        return stock || invincible;
    }

    /** Logic thread, once a frame. */
    static void poll() {
        if (!said) {
            said = true;
            Log.info("development build: 0 clears out the zombies and pressed again puts some back round you, . goes to the next place and , to the one before, 9 puts the clock on three hours, Backspace holds the next kind of weather, ; gives a shotgun that never runs dry, [ gives a hunting rifle with the next of the three scopes on it, / gives a lit flashlight, ' makes you invincible, F7 takes the nearest vehicle");
        }
        // On a server every one of these would be a cheat against the other players.
        if (GameClient.client) {
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
            if (!cleared && safe != null) {
                bringZombiesBack(safe);
            }
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
            safe.teleportTo(to.x(), to.y(), to.level());
            Log.info((back ? "," : ".") + ": moved to " + to.name() + " (" + to.x() + ", " + to.y() + ", level " + to.level() + "), place " + (place + 1) + " of " + PLACES.length);
        }
        if (GameKeyboard.isKeyPressed(LATER_KEY)) {
            // Past midnight is left as an hour over 24: the game's clock takes that as the next day.
            float hour = GameTime.getInstance().getTimeOfDay() + LATER;
            GameTime.getInstance().setTimeOfDay(hour);
            Log.info("9: the clock put on to " + hour);
        }
        if (GameKeyboard.isKeyPressed(WEATHER_KEY)) {
            weather = weather + 1 < WEATHERS.length ? weather + 1 : -1;
            hold(weather < 0 ? null : WEATHERS[weather]);
        }
        // The game melts and adds to lying snow on the hour, so it is set again when it has moved.
        if (weather >= 0 && Math.abs(ClimateManager.getInstance().getSnowFracNow() - WEATHERS[weather].lying()) > LYING_DRIFT) {
            lie(WEATHERS[weather].lying());
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
        // With two at the screen the key is the split screen's.
        if (IsoPlayer.numPlayers != 2 && GameKeyboard.isKeyPressed(TAKE_VEHICLE_KEY)) {
            takeNearestVehicle();
        }
    }

    /** Holds the game's climate at a kind of weather, or with none gives it back to the game. */
    private static void hold(Weather held) {
        ClimateManager climate = ClimateManager.getInstance();
        if (held == null) {
            climate.resetAdmin();
            Core.getInstance().setForceSnow(false);
            Log.info("Backspace: the weather is the game's own again");
            return;
        }
        hold(climate.getClimateFloat(ClimateManager.FLOAT_CLOUD_INTENSITY), held.cloud());
        hold(climate.getClimateFloat(ClimateManager.FLOAT_PRECIPITATION_INTENSITY), held.falling());
        hold(climate.getClimateFloat(ClimateManager.FLOAT_WIND_INTENSITY), held.wind());
        hold(climate.getClimateFloat(ClimateManager.FLOAT_FOG_INTENSITY), held.fog());
        ClimateManager.ClimateBool snow = climate.getClimateBool(ClimateManager.BOOL_IS_SNOW);
        snow.setEnableAdmin(true);
        snow.setAdminValue(held.snow());
        lie(held.lying());
        Log.info("Backspace: the weather held at " + held.name() + ", " + (weather + 1) + " of " + WEATHERS.length);
    }

    /**
     * Lays snow over that share of the ground. The game's forced snow lies at 0.7 whatever is
     * asked, so the strength it works lying snow out from is set instead, and the field that
     * makes its next tick work it out from that strength.
     */
    private static void lie(float lying) {
        ClimateManager climate = ClimateManager.getInstance();
        Core.getInstance().setForceSnow(false);
        try {
            if (snowStrength == null) {
                snowStrength = ClimateManager.class.getDeclaredField("snowStrength");
                snowStrength.setAccessible(true);
                wasForceSnow = ClimateManager.class.getDeclaredField("wasForceSnow");
                wasForceSnow.setAccessible(true);
            }
            snowStrength.setFloat(climate, FULL_SNOW_STRENGTH * lying);
            wasForceSnow.setBoolean(climate, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the game's climate has no snowStrength or wasForceSnow to set lying snow with", e);
        }
    }

    private static void hold(ClimateManager.ClimateFloat value, float at) {
        value.setEnableAdmin(true);
        value.setAdminValue(at);
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

    /**
     * The game brings zombies back into a place by itself only over hours, so switching the
     * clearing off puts a ring of them round the player, out of doors and out of arm's reach.
     */
    private static void bringZombiesBack(IsoPlayer player) {
        int made = 0;
        for (int i = 0; i < BACK_TRIED; i++) {
            double angle = 2 * Math.PI * i / BACK_TRIED;
            float away = BACK_NEAREST + (i % 3) * BACK_STEP;
            float x = player.getX() + (float) Math.cos(angle) * away, y = player.getY() + (float) Math.sin(angle) * away;
            IsoGridSquare square = IsoWorld.instance.currentCell.getGridSquare((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(player.getZ()));
            if (square != null && square.isOutside() && !square.isSolid() && !square.isSolidTrans()) {
                VirtualZombieManager.instance.createRealZombieNow(x, y, player.getZ());
                made++;
            }
        }
        Log.info("0: " + made + " zombies put back round the player");
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
            Log.info("no vehicle within " + TAKE_WITHIN + " tiles");
            return;
        }
        nearest.repair();
        VehiclePart tank = nearest.getParts().getPartById("GasTank");
        if (tank != null) {
            tank.setContainerContentAmount(tank.getContainerCapacity());
        }
        nearest.setHotwired(true);
        boolean seated = nearest.enter(0, player);
        Log.info("took the vehicle at (" + nearest.getX() + ", " + nearest.getY() + "), seated " + seated);
    }
}
