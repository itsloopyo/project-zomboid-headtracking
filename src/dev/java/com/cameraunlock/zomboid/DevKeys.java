package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Random;
import java.util.Set;
import zombie.GameTime;
import zombie.VirtualZombieManager;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.Core;
import zombie.erosion.ErosionMain;
import zombie.erosion.season.ErosionSeason;
import zombie.input.GameKeyboard;
import zombie.inventory.InventoryItem;
import zombie.inventory.InventoryItemFactory;
import zombie.inventory.types.HandWeapon;
import zombie.inventory.types.WeaponPart;
import zombie.iso.IsoChunk;
import zombie.iso.IsoChunkMap;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.objects.IsoFireManager;
import zombie.iso.weather.ClimateManager;
import zombie.network.GameClient;
import zombie.vehicles.BaseVehicle;
import zombie.vehicles.VehiclePart;

/**
 * Keys for whoever is working on the mod, there only in a development build: the jar a
 * player installs is compiled with the empty class of this name in src/shipped in this one's place.
 * Each is on a key the game's default bindings leave alone. The game acts on a key whatever
 * modifier is held with it, so F2 to F6, its pause and speed keys, cannot be shared with it.
 * A key that does something to the player does it to every player at the screen.
 *
 * F7 takes the nearest vehicle within a few paces: mends it, fills its tank, hot-wires it and
 * sits the player in its first free seat, whatever state it was found in. For looking at
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
 * Backspace holds the year at the next of the four seasons, spring, summer, autumn and winter,
 * and round again, for the game's own trees and for what the mod draws alike, and says on the
 * screen which. The game works the season out again each day, so it is held there until the
 * session ends. Winter is a clear sky over ground under snow. The other three give the weather
 * back to the game, unless End holds one, and keep the ground clear of snow, so
 * winter's does not lie on into spring.
 *
 * Home starts a fire where the reticle is, or a few paces ahead of a player not in first
 * person: for seeing the flames, their light and smoke without finding a stove left on. It
 * burns and spreads as any fire in the game does.
 *
 * End holds the weather at the next of clear, rainy, snowy and stormy, and round
 * again, over whatever the season key holds. Snowy lays snow over the whole ground. Stormy is
 * heavy rain and wind with lightning striking round the first player a few seconds apart,
 * from a few hundred tiles to a mile off, as the game's own thunder cloud strikes. The game
 * keeps the weather it is held at in the save.
 *
 * The semicolon puts a pump action shotgun in the player's hands. Whatever firearm is held is
 * kept loaded, sound and unjammed, from the moment the game loads, so a gun the save was left
 * holding fires too: for trying the view as a shooter without first finding a gun and shells.
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
     * 12751 to 12793 by 1512 to 1544, and the second is on its roof. The third is at the gate between two of the
     * cattle pastures of the farm by the corn maze, which has five of the game's cow ranch zones and chicken and sheep
     * ones beside them, so most worlds have animals in sight of it. The next four and the West Point and Louisville ones
     * are spots the picture has been judged from. The rest are the game's own spawn points
     * for each town, so there is a floor to stand on at every one.
     */
    private static final Place[] PLACES = {
            new Place("Louisville, the skyscraper", 12772.5f, 1565.5f),
            new Place("Louisville, the skyscraper's roof", 12785.415f, 1523.9503f, 26),
            new Place("the cattle farm by the corn maze", 13546.5f, 3836.5f),
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
    private static final int SEASON_KEY = 14;

    /** A season as ErosionSeason numbers it, how far through it the year is held, and how much of the ground lies under snow. */
    private record Season(String name, int number, float progression, float lying) {}

    /**
     * Spring is held where most trees have come into leaf, and autumn where most crowns have
     * turned and a few are bare: see Seasons.
     */
    private static final Season[] SEASONS = {
            new Season("Spring", Seasons.SPRING, 0.6f, 0f),
            new Season("Summer", Seasons.SUMMER, 0.5f, 0f),
            new Season("Autumn", Seasons.AUTUMN, 0.55f, 0f),
            new Season("Winter", Seasons.WINTER, 0.5f, 1f),
    };
    /** The season being held, and none before the first press. */
    private static int season = -1;
    /** The held season's progression drifts by more than this before it is set again. */
    private static final float SEASON_DRIFT = 0.01f;
    private static Field curSeason, curSeasonDay, curSeasonProgression, erosionTicks;
    /** Lying snow drifts from what is held by more than this before it is set again. */
    private static final float LYING_DRIFT = 0.05f;
    /** The game's snow strength at which the whole ground lies under snow. */
    private static final float FULL_SNOW_STRENGTH = 7.5f;
    private static Field snowStrength, wasForceSnow;

    /** End. */
    private static final int WEATHER_KEY = 207;
    /** Home, beside End. */
    private static final int FIRE_KEY = 199;
    /** How far ahead of a player not in first person a fire is started, in tiles, and how much it starts with. */
    private static final float FIRE_AHEAD = 3f;
    private static final int FIRE_ENERGY = 100;

    /** A kind of weather as the game's climate has it: each of cloud, rain or snow, and wind from 0 to 1. */
    private record Weather(String name, float cloud, float falling, float wind, boolean snow, boolean thunder) {}

    private static final Weather[] WEATHERS = {
            new Weather("Clear", 0f, 0f, 0.05f, false, false),
            new Weather("Rainy", 0.9f, 0.5f, 0.3f, false, false),
            new Weather("Snowy", 1f, 0.7f, 0.3f, true, false),
            new Weather("Stormy", 1f, 1f, 0.9f, false, true),
    };
    /** The weather being held, and none before the first press. */
    private static int weather = -1;
    /** When the next strike of a held storm falls. */
    private static long nextStrikeNanos;
    private static final Random strikes = new Random();
    /** A held storm strikes this many seconds apart at least and at most, as the game's strongest thunder cloud does. */
    private static final float STRIKES_APART_LEAST = 1.5f, STRIKES_APART_MOST = 6f;
    /** And this far off, in tiles. */
    private static final float STRIKES_NEAREST = 150f, STRIKES_FURTHEST = 1600f;

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

    private static boolean invincible, wasInvincible;
    private static Field cannotDie;
    private static Method cheatsOf;
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

    /** Logic thread, once a frame. Says what a key did, for the screen, or null. */
    static String poll() {
        String told = null;
        if (!said) {
            said = true;
            Log.info("development build: 0 clears out the zombies and pressed again puts some back round you, . goes to the next place and , to the one before, 9 puts the clock on three hours, Backspace holds the next season of spring, summer, autumn and winter, End holds the next weather of clear, rainy, snowy and stormy, Home starts a fire where the reticle is, ; gives a shotgun, any gun held never runs dry, [ gives a hunting rifle with the next of the three scopes on it, / gives a lit flashlight, ' makes you invincible, F7 takes the nearest vehicle");
        }
        // On a server every one of these would be a cheat against the other players.
        if (GameClient.client) {
            invincible = false;
            cleared = false;
            return null;
        }
        ArrayList<IsoPlayer> players = new ArrayList<>();
        for (IsoPlayer each : IsoPlayer.players) {
            if (each != null) {
                players.add(each);
            }
        }
        if (GameKeyboard.isKeyPressed(INVINCIBLE_KEY)) {
            invincible = !invincible;
            Log.info("': invincible " + invincible + " for " + players.size() + " players");
        }
        // The game's own god mode is refused outside its debug mode, so the players are made
        // whole again every frame instead: wounds, bites and infection are gone as they land.
        if (invincible || wasInvincible) {
            for (IsoPlayer each : players) {
                keepWhole(each);
            }
            wasInvincible = invincible;
        }
        if (GameKeyboard.isKeyPressed(CLEAR_KEY)) {
            cleared = !cleared;
            Log.info("0: zombies cleared " + cleared);
            if (!cleared) {
                for (IsoPlayer each : players) {
                    bringZombiesBack(each);
                }
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
        if (back || GameKeyboard.isKeyPressed(NEXT_PLACE_KEY)) {
            place = back ? Math.floorMod(Math.max(place, 0) - 1, PLACES.length) : (place + 1) % PLACES.length;
            Place to = PLACES[place];
            int moved = 0;
            for (IsoPlayer each : players) {
                if (each.getVehicle() == null) {
                    each.teleportTo(to.x(), to.y(), to.level());
                    moved++;
                }
            }
            Log.info((back ? "," : ".") + ": moved " + moved + " of " + players.size() + " players to " + to.name() + " (" + to.x() + ", " + to.y() + ", level " + to.level() + "), place " + (place + 1) + " of " + PLACES.length);
        }
        if (GameKeyboard.isKeyPressed(LATER_KEY)) {
            // Past midnight is left as an hour over 24: the game's clock takes that as the next day.
            float hour = GameTime.getInstance().getTimeOfDay() + LATER;
            GameTime.getInstance().setTimeOfDay(hour);
            Log.info("9: the clock put on to " + hour);
        }
        if (GameKeyboard.isKeyPressed(SEASON_KEY)) {
            season = (season + 1) % SEASONS.length;
            hold(SEASONS[season], true);
            told = "Season: " + SEASONS[season].name();
        } else if (season >= 0) {
            // The game works the season out again as each day begins.
            ErosionSeason trees = ErosionMain.getInstance().getSeasons();
            if (trees.getSeason() != SEASONS[season].number() || Math.abs(trees.getSeasonProgression() - SEASONS[season].progression()) > SEASON_DRIFT) {
                hold(SEASONS[season], false);
            }
        }
        if (GameKeyboard.isKeyPressed(WEATHER_KEY)) {
            weather = (weather + 1) % WEATHERS.length;
            hold(WEATHERS[weather]);
            lie(lying());
            told = "Weather: " + WEATHERS[weather].name();
        }
        if (GameKeyboard.isKeyPressed(FIRE_KEY)) {
            for (IsoPlayer each : players) {
                startFire(each);
            }
        }
        if (weather >= 0 && WEATHERS[weather].thunder() && !players.isEmpty()) {
            strike(players.get(0));
        }
        // The game melts and adds to lying snow on the hour, so it is set again when it has moved.
        if (lying() >= 0f && Math.abs(ClimateManager.getInstance().getSnowFracNow() - lying()) > LYING_DRIFT) {
            lie(lying());
        }
        if (GameKeyboard.isKeyPressed(ARM_KEY)) {
            for (IsoPlayer each : players) {
                InventoryItem shotgun = each.getInventory().AddItem(SHOTGUN);
                each.setPrimaryHandItem(shotgun);
                each.setSecondaryHandItem(shotgun);
            }
            Log.info(";: a shotgun in hand for " + players.size() + " players, kept loaded");
        }
        if (GameKeyboard.isKeyPressed(SCOPED_KEY)) {
            scope = (scope + 1) % ScopeOptics.KNOWN.length;
            String part = ScopeOptics.KNOWN[scope].part();
            for (IsoPlayer each : players) {
                HandWeapon rifle = (HandWeapon) each.getInventory().AddItem(RIFLE);
                rifle.attachWeaponPart(InventoryItemFactory.<WeaponPart>CreateItem(part));
                each.setPrimaryHandItem(rifle);
                each.setSecondaryHandItem(rifle);
            }
            Log.info("[: a hunting rifle in hand for " + players.size() + " players with " + part + " on it, kept loaded");
        }
        if (GameKeyboard.isKeyPressed(TORCH_KEY)) {
            for (IsoPlayer each : players) {
                InventoryItem torch = each.getInventory().AddItem(TORCH);
                torch.setCurrentUsesFloat(1f);
                each.setSecondaryHandItem(torch);
                torch.setActivated(true);
            }
            Log.info("/: a flashlight in the left hand for " + players.size() + " players, charged and lit");
        }
        for (IsoPlayer each : players) {
            if (each.getPrimaryHandItem() instanceof HandWeapon held && held.isAimedFirearm()) {
                held.setCurrentAmmoCount(held.getMaxAmmo());
                held.setRoundChambered(true);
                held.setJammed(false);
                held.setCondition(held.getConditionMax());
            }
        }
        // With two at the screen the key is the split screen's.
        if (IsoPlayer.numPlayers != 2 && GameKeyboard.isKeyPressed(TAKE_VEHICLE_KEY)) {
            for (IsoPlayer each : players) {
                takeNearestVehicle(each);
            }
        }
        return told;
    }

    /**
     * Holds the year at a season for the game's trees (ErosionMain's) and for the sky and the
     * mod (the climate's, which Seasons captures), then has the game's erosion run again on
     * every loaded chunk: it runs only a chunk whose stamp differs from its tick count.
     */
    private static void hold(Season held, boolean pressed) {
        ErosionMain erosion = ErosionMain.getInstance();
        try {
            if (curSeason == null) {
                curSeason = seasonField("curSeason");
                curSeasonDay = seasonField("curSeasonDay");
                curSeasonProgression = seasonField("curSeasonProgression");
                erosionTicks = ErosionMain.class.getDeclaredField("eTicks");
                erosionTicks.setAccessible(true);
            }
            for (ErosionSeason on : new ErosionSeason[] {erosion.getSeasons(), ClimateManager.getInstance().getSeason()}) {
                float days = on.getSeasonDays();
                curSeason.setInt(on, held.number());
                curSeasonDay.setFloat(on, held.progression() * days);
                curSeasonProgression.setFloat(on, held.progression());
            }
            int ticks = erosionTicks.getInt(erosion);
            int chunks = 0;
            for (int player = 0; player < IsoPlayer.numPlayers; player++) {
                IsoChunkMap map = IsoWorld.instance.currentCell.getChunkMap(player);
                for (int x = 0; x < IsoChunkMap.chunkGridWidth; x++) {
                    for (int y = 0; y < IsoChunkMap.chunkGridWidth; y++) {
                        IsoChunk chunk = map.getChunk(x, y);
                        if (chunk != null) {
                            chunk.getErosionData().eTickStamp = ticks - 1;
                            chunks++;
                        }
                    }
                }
            }
            erosion.DebugUpdateMapNow();
            if (weather < 0) {
                clearSky(held.lying() > 0f);
            }
            lie(lying());
            Log.info("Backspace: the year " + (pressed ? "held at " : "held again at ") + held.name() + ", " + (season + 1) + " of " + SEASONS.length
                    + ", erosion run again on " + chunks + " chunks; the trees' season reads " + erosion.getSeasons().getSeason() + " at "
                    + erosion.getSeasons().getSeasonProgression() + " and the climate's " + ClimateManager.getInstance().getSeason().getSeason());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the game's seasons cannot be held this way: " + e, e);
        }
    }

    /**
     * How much of the ground lies under snow: all of it in held snowy weather, else as the held
     * season has it, else none in other held weather, and -1 with neither held.
     */
    private static float lying() {
        if (weather >= 0 && WEATHERS[weather].snow()) {
            return 1f;
        }
        if (season >= 0) {
            return SEASONS[season].lying();
        }
        return weather >= 0 ? 0f : -1f;
    }

    /** Starts a fire where the player's reticle is in first person, else a few paces ahead of them. */
    private static void startFire(IsoPlayer player) {
        View view = View.of(player);
        float[] at = new float[3];
        if (view != null && view.firstPerson.active()) {
            view.controls.aimPoint(at);
        } else {
            at[0] = player.getX() + player.getForwardDirection().x * FIRE_AHEAD;
            at[1] = player.getY() + player.getForwardDirection().y * FIRE_AHEAD;
            at[2] = player.getZ();
        }
        IsoGridSquare square = IsoWorld.instance.currentCell.getGridSquare((int) Math.floor(at[0]), (int) Math.floor(at[1]), (int) Math.floor(at[2]));
        if (square == null) {
            Log.info("Home: no square at (" + at[0] + ", " + at[1] + ", " + at[2] + ") to start a fire on");
            return;
        }
        IsoFireManager.StartFire(IsoWorld.instance.currentCell, square, true, FIRE_ENERGY);
        Log.info("Home: a fire started at " + square.getX() + ", " + square.getY() + ", " + square.getZ());
    }

    /** Holds the game's climate at a kind of weather, with no fog. */
    private static void hold(Weather held) {
        ClimateManager climate = ClimateManager.getInstance();
        hold(climate.getClimateFloat(ClimateManager.FLOAT_CLOUD_INTENSITY), held.cloud());
        hold(climate.getClimateFloat(ClimateManager.FLOAT_PRECIPITATION_INTENSITY), held.falling());
        hold(climate.getClimateFloat(ClimateManager.FLOAT_WIND_INTENSITY), held.wind());
        hold(climate.getClimateFloat(ClimateManager.FLOAT_FOG_INTENSITY), 0f);
        ClimateManager.ClimateBool snow = climate.getClimateBool(ClimateManager.BOOL_IS_SNOW);
        snow.setEnableAdmin(true);
        snow.setAdminValue(held.snow());
        Log.info("End: the weather held at " + held.name() + ", " + (weather + 1) + " of " + WEATHERS.length);
    }

    /**
     * The next strike of a held storm, once its time has come: somewhere round the player, as the
     * game's own thunder cloud strikes, through the game's own thunder so its sound and its light
     * come as they do in any storm. About one in four is thunder alone.
     */
    private static void strike(IsoPlayer player) {
        long now = System.nanoTime();
        if (now < nextStrikeNanos) {
            return;
        }
        nextStrikeNanos = now + (long) ((STRIKES_APART_LEAST + strikes.nextFloat() * (STRIKES_APART_MOST - STRIKES_APART_LEAST)) * 1e9);
        double way = strikes.nextDouble() * Math.PI * 2;
        float off = STRIKES_NEAREST + (float) Math.pow(strikes.nextFloat(), 1.6) * (STRIKES_FURTHEST - STRIKES_NEAREST);
        int x = (int) (player.getX() + Math.cos(way) * off), y = (int) (player.getY() + Math.sin(way) * off);
        boolean seen = strikes.nextFloat() < 0.75f;
        boolean lit = seen && !Core.getInstance().getOptionDisableLightningDuringStorms();
        ClimateManager.getInstance().getThunderStorm().triggerThunderEvent(x, y, seen, lit, !seen || strikes.nextFloat() > 0.4f);
    }

    /** Holds the sky clear, with nothing falling and little wind, or gives the weather back to the game. */
    private static void clearSky(boolean clear) {
        ClimateManager climate = ClimateManager.getInstance();
        if (!clear) {
            climate.resetAdmin();
            return;
        }
        hold(climate.getClimateFloat(ClimateManager.FLOAT_CLOUD_INTENSITY), 0f);
        hold(climate.getClimateFloat(ClimateManager.FLOAT_PRECIPITATION_INTENSITY), 0f);
        hold(climate.getClimateFloat(ClimateManager.FLOAT_WIND_INTENSITY), 0.1f);
        hold(climate.getClimateFloat(ClimateManager.FLOAT_FOG_INTENSITY), 0f);
        ClimateManager.ClimateBool snow = climate.getClimateBool(ClimateManager.BOOL_IS_SNOW);
        snow.setEnableAdmin(true);
        snow.setAdminValue(false);
    }

    private static void hold(ClimateManager.ClimateFloat value, float at) {
        value.setEnableAdmin(true);
        value.setAdminValue(at);
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

    private static Field seasonField(String name) throws NoSuchFieldException {
        Field field = ErosionSeason.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
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
                cheatsOf = IsoGameCharacter.class.getMethod("getCheats");
                Field held = cheatsOf.invoke(safe).getClass().getDeclaredField("cheats");
                held.setAccessible(true);
                godModes = held;
                godMode = Class.forName("zombie.characters.CheatType").getField("GOD_MODE").get(null);
            }
            @SuppressWarnings("unchecked")
            Set<Object> set = (Set<Object>) godModes.get(cheatsOf.invoke(safe));
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

    private static void takeNearestVehicle(IsoPlayer player) {
        if (player.getVehicle() != null) {
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
        int seat = 0;
        while (seat < nearest.getMaxPassengers() && nearest.isSeatOccupied(seat)) {
            seat++;
        }
        if (seat == nearest.getMaxPassengers()) {
            Log.info("the vehicle at (" + nearest.getX() + ", " + nearest.getY() + ") has no free seat for player " + (player.getPlayerNum() + 1));
            return;
        }
        boolean seated = nearest.enter(seat, player);
        Log.info("took the vehicle at (" + nearest.getX() + ", " + nearest.getY() + "), player " + (player.getPlayerNum() + 1) + " seated " + seated + " in seat " + seat);
    }
}
