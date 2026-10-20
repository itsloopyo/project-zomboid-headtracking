package com.cameraunlock.zomboid;

import zombie.iso.BuildingDef;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * Which storeys of the viewer's own building the eye cannot see from where it stands, but up
 * or down a stairwell.
 *
 * A column of the building is the viewer's if its square on the viewer's level is of the
 * building, or is open down to a floor indoors in it: the well of a hall several floors high.
 * Up each such column the first floor or roof over the viewer's level closes it, and the
 * stairs under a square count as its floor, since a stairwell is let through. The storeys
 * from one over the highest such lid up are held. Down each, the floor the viewer's level
 * stands on, or the one the well goes down to, and those from three under the lowest of them
 * down are held. With every storey floored over the next, that is the storeys from two over
 * the viewer's, and from three under it.
 */
final class Lids {
    /**
     * @param from  the lowest storey held over the viewer, or Integer.MAX_VALUE for none
     * @param under the highest storey held under the viewer
     * @param whole whether every column of the building was loaded: if not, those that were not are left out
     */
    record Held(int from, int under, boolean whole) {}

    private Lids() {}

    static Held of(IsoCell cell, BuildingDef def, int level) {
        int over = level + 1, under = level;
        boolean whole = true;
        for (int x = def.getX(); x < def.getX() + def.getW(); x++) {
            for (int y = def.getY(); y < def.getY() + def.getH(); y++) {
                IsoGridSquare here = cell.getGridSquare(x, y, level), ground = cell.getGridSquare(x, y, 0);
                IsoGridSquare known = here != null ? here : ground;
                if (known == null || known.getChunk() == null) {
                    whole = false;
                    continue;
                }
                int lowest = known.getChunk().getMinLevel(), floor = lowest;
                IsoGridSquare floored = null;
                for (int z = level; z >= lowest; z--) {
                    IsoGridSquare square = cell.getGridSquare(x, y, z);
                    if (square != null && (square.getFloor() != null || roofed(square)) || stairsUnder(cell, x, y, z)) {
                        floor = z;
                        floored = square;
                        break;
                    }
                }
                boolean ours = here != null && ofBuilding(here, def)
                        || floored != null && floor < level && !floored.isOutside() && ofBuilding(floored, def);
                if (!ours) {
                    continue;
                }
                under = Math.min(under, floor);
                int lid = Integer.MAX_VALUE;
                for (int z = level + 1; z <= known.getChunk().getMaxLevel(); z++) {
                    IsoGridSquare square = cell.getGridSquare(x, y, z);
                    if (square != null && (square.getFloor() != null || roofed(square)) || stairsUnder(cell, x, y, z)) {
                        lid = z;
                        break;
                    }
                }
                over = Math.max(over, lid);
            }
        }
        return new Held(over == Integer.MAX_VALUE ? Integer.MAX_VALUE : over + 1, under - 3, whole);
    }

    private static boolean ofBuilding(IsoGridSquare square, BuildingDef def) {
        return square.getBuilding() != null && square.getBuilding().getDef() == def;
    }

    private static boolean roofed(IsoGridSquare square) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && Roofs.isTile(sprite.getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean stairsUnder(IsoCell cell, int x, int y, int z) {
        IsoGridSquare below = cell.getGridSquare(x, y, z - 1);
        if (below == null) {
            return false;
        }
        PZArrayList<IsoObject> objects = below.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && WorldScene.isStair(sprite.getType())) {
                return true;
            }
        }
        return false;
    }
}
