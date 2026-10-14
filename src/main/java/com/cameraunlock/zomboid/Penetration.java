package com.cameraunlock.zomboid;

import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.IsoWorld;
import zombie.iso.LosUtil;
import zombie.iso.objects.IsoDoor;
import zombie.iso.objects.IsoThumpable;
import zombie.iso.objects.IsoWindow;
import zombie.scripting.objects.AmmoType;
import zombie.util.list.PZArrayList;

/**
 * How far a firearm's round goes on through what the game says stops it: a stud wall of
 * plasterboard, a wall of boards, a shut door, the glass and curtain of a window. A round carries
 * a budget by its cartridge, and each wall or door it passes spends from it by what the game says
 * the wall is made of (the sprite's MaterialType, the word the game picks the sound of a hit by).
 * Brick, block, concrete, stone, tile, metal plate, sandbags and anything the game names no
 * material for stop every round, as the game stops them all. What is left of the budget when the
 * round reaches a target is the share of the game's damage it does.
 *
 * The game decides everything else about the hit: its chance, its damage, the blood, the fall.
 * Alone only. On a server the server decides what a shot reaches, by the game's own rule.
 * Logic thread.
 */
final class Penetration {
    private Penetration() {}

    /** What a wall stops a round with: no round gets through it. */
    static final float STOPS = Float.POSITIVE_INFINITY;

    /**
     * What a round of each cartridge can spend, in the units of {@link #cost}: one is a stud wall
     * of plasterboard. A handgun's round goes through a few, a hunting rifle's through a wall of
     * boards and a solid door and on, and a shotgun's pellets through one stud wall at most.
     */
    enum Cartridge {
        SHOT(1.5f), PISTOL_38(3f), PISTOL_9MM(4f), PISTOL_45(4f), MAGNUM_357(6f), MAGNUM_44(6f),
        RIFLE_556(8f), RIFLE_3030(9f), RIFLE_308(10f);

        final float budget;

        Cartridge(float budget) {
            this.budget = budget;
        }
    }

    /** The cartridge a weapon fires, or null for one this table does not know, which shoots as the game has it. */
    static Cartridge cartridgeOf(HandWeapon weapon) {
        AmmoType ammo = weapon.getAmmoType();
        if (ammo == null) {
            return null;
        }
        if (ammo == AmmoType.SHOTGUN_SHELLS) return Cartridge.SHOT;
        if (ammo == AmmoType.BULLETS_38) return Cartridge.PISTOL_38;
        if (ammo == AmmoType.BULLETS_9MM) return Cartridge.PISTOL_9MM;
        if (ammo == AmmoType.BULLETS_45) return Cartridge.PISTOL_45;
        if (ammo == AmmoType.BULLETS_357) return Cartridge.MAGNUM_357;
        if (ammo == AmmoType.BULLETS_44) return Cartridge.MAGNUM_44;
        if (ammo == AmmoType.BULLETS_556) return Cartridge.RIFLE_556;
        if (ammo == AmmoType.BULLETS_3030) return Cartridge.RIFLE_3030;
        if (ammo == AmmoType.BULLETS_308) return Cartridge.RIFLE_308;
        return null;
    }

    /**
     * What a round spends going through a wall, a door or a window of a material, by the name the
     * game gives it (MaterialType). Plaster is a stud wall of plasterboard, Wood a wall of boards
     * or a hollow door, Wood_Solid a solid door or logs, Metal_Light sheet metal.
     */
    static float cost(String materialType) {
        if (materialType == null) {
            return STOPS;
        }
        return switch (materialType) {
            case "Fabric" -> 0f;
            case "Glass", "Glass_Light" -> 0.25f;
            case "Plaster", "Plastic" -> 1f;
            case "Wood" -> 2f;
            case "Metal_Light" -> 2.5f;
            case "Wood_Solid" -> 3f;
            default -> STOPS;
        };
    }

    /** The share of a round's damage left after spending `spent` of `budget`: nothing once it is all spent. */
    static float left(float budget, float spent) {
        return spent < budget ? (budget - spent) / budget : 0f;
    }

    /** Whether a shot by this character goes on through walls: a player of this machine, with a firearm, alone. */
    static boolean applies(IsoGameCharacter shooter, HandWeapon weapon) {
        return !Online.joined() && shooter instanceof IsoPlayer player && View.of(player) != null && weapon != null && weapon.isAimedFirearm();
    }

    /** A clear line through nothing that stops a shot: the whole of the round's damage. */
    static final float WHOLE = 1f;

    private static final GridTrace trace = new GridTrace();
    private static IsoCell cell;
    private static int level;
    private static float spent, budget;
    private static final GridTrace.Edges spend = Penetration::spendAt;

    /**
     * The share of a round's damage left where it reaches (toX, toY) from (fromX, fromY) on one floor
     * level: WHOLE where nothing on the way stops a shot, less for each wall or door it goes through,
     * and 0 where one stops it or the budget runs out. Each edge between squares is the game's own test
     * of what stops a shot (IsoGridSquare.testVisionAdjacent, as LosUtil.lineClear asks it).
     */
    static float share(IsoCell on, int floor, Cartridge cartridge, float fromX, float fromY, float toX, float toY) {
        float wayX = toX - fromX, wayY = toY - fromY;
        float length = (float) Math.hypot(wayX, wayY);
        if (length < 1e-4f) {
            return WHOLE;
        }
        cell = on;
        level = floor;
        spent = 0f;
        budget = cartridge.budget;
        float stopped = trace.firstBlockedEdge(spend, fromX, fromY, wayX / length, wayY / length, length);
        cell = null;
        return stopped == Float.POSITIVE_INFINITY ? left(budget, spent) : 0f;
    }

    /** Takes what the edge costs out of the round's budget, and answers whether that stops it. */
    private static boolean spendAt(int fromX, int fromY, int toX, int toY) {
        spent += edgeCost(cell, level, fromX, fromY, toX, toY);
        return spent >= budget;
    }

    /** What going through the edge between two neighbouring squares costs a round: 0 where the game lets a shot through. */
    static float edgeCost(IsoCell cell, int level, int fromX, int fromY, int toX, int toY) {
        IsoGridSquare from = cell.getGridSquare(fromX, fromY, level);
        IsoGridSquare to = cell.getGridSquare(toX, toY, level);
        if (from == null || to == null) {
            return WorldQuery.stopsAShotBesideAir(from == null ? null : from::has, to == null ? null : to::has, fromX, fromY, toX, toY) ? STOPS : 0f;
        }
        LosUtil.TestResults seen = to.testVisionAdjacent(fromX - toX, fromY - toY, 0, true, false);
        if (seen != LosUtil.TestResults.Blocked && seen != LosUtil.TestResults.ClearThroughClosedDoor) {
            return 0f;
        }
        // An edge is the north or west edge of the square south or east of it, and what stands on it is that square's.
        boolean north = fromX == toX;
        IsoGridSquare owner = north ? (toY < fromY ? from : to) : (toX < fromX ? from : to);
        return costOn(owner, north);
    }

    /** What stands on a square's north or west edge costs a round going through it: a shut door, a window, or a wall. */
    private static float costOn(IsoGridSquare square, boolean north) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoObject object = objects.get(i);
            if (object instanceof IsoDoor door && door.getNorth() == north && !door.IsOpen()
                    || object instanceof IsoThumpable thumpable && thumpable.isDoor() && thumpable.getNorth() == north && !thumpable.IsOpen()) {
                String made = materialOf(object);
                return cost(made == null ? "Wood" : made);
            }
            if (object instanceof IsoWindow window && window.getNorth() == north) {
                // Stopped by its curtain, or by a barricade, which stops a round as the game has it.
                return window.isBarricaded() ? STOPS : cost("Glass");
            }
        }
        IsoObject wall = WorldScene.wallOn(square, north);
        return wall == null ? STOPS : cost(materialOf(wall));
    }

    private static String materialOf(IsoObject object) {
        return object.getSprite() == null ? null : object.getSprite().getProperties().get("MaterialType");
    }

    /** The share of the game's damage each target of the shot being worked out gets, by the target's id, while it is kept. */
    private static final java.util.HashMap<Integer, Float> through = new java.util.HashMap<>();

    /**
     * Called with the game's line of sight test from a shooter to a target as its firearm keeps or drops
     * the target (CombatManager.calculateHitListWeapon). Where the test says a wall or a shut door is in the
     * way and the round goes through it, the target is kept, with the share of damage that gets to it.
     */
    static LosUtil.TestResults lineOfFire(LosUtil.TestResults seen, IsoGameCharacter shooter, HandWeapon weapon, IsoMovingObject target) {
        if (seen != LosUtil.TestResults.Blocked && seen != LosUtil.TestResults.ClearThroughClosedDoor || !applies(shooter, weapon)) {
            return seen;
        }
        Cartridge cartridge = cartridgeOf(weapon);
        int floor = (int) Math.floor(shooter.getZ());
        IsoCell on = IsoWorld.instance.currentCell;
        if (cartridge == null || floor != (int) Math.floor(target.getZ()) || on == null) {
            return seen;
        }
        float share = share(on, floor, cartridge, shooter.getX(), shooter.getY(), target.getX(), target.getY());
        if (share <= 0f) {
            return seen;
        }
        through.put(target.getID(), share);
        return LosUtil.TestResults.Clear;
    }

    /** The game starts working out who a firearm's shot can reach: what was worked out for the last one goes. */
    static void listStarts() {
        through.clear();
    }

    /** Whether a target was kept because the round goes through what is in the way. */
    static boolean kept(int id) {
        return through.containsKey(id);
    }

    /** The share of the game's damage a target gets: less than whole for one a round reaches through a wall. */
    static float shareOf(int id) {
        Float share = through.get(id);
        return share == null ? WHOLE : share;
    }
}
