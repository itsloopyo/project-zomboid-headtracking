package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.iso.SpriteDetails.IsoFlagType;

/**
 * Whether a sprite is fixed to the floor. The game's tile loading (IsoWorld) marks every sprite
 * whose name holds "damaged" or "trash_" attachedFloor, whatever it is: a torn fence on its edge,
 * a broken length of wall and the notices of trash_walls_01 on a wall among them. On such a
 * sprite the mark is the name's and not the tile's where the sprite stands on an edge or hangs on
 * a wall, and it stands or hangs there. A billboard's board, catwalk and post are marked so by
 * their tiles, levels up in the air: laid on a floor that is not there, the board was a black
 * slab under its lettering and the post was not seen at all.
 */
final class FloorMarks {
    private FloorMarks() {}

    static boolean onFloor(String name, PropertyContainer properties) {
        if (!properties.has(IsoFlagType.attachedFloor) || name != null && name.startsWith("advertising_")) {
            return false;
        }
        boolean byName = name != null && (name.contains("damaged") || name.contains("trash_"));
        return !byName || !(onEdge(properties) || properties.has(IsoFlagType.WallOverlay) || "WallObject".equals(properties.get("MoveType")));
    }

    private static boolean onEdge(PropertyContainer properties) {
        return properties.has(IsoFlagType.WallN) || properties.has(IsoFlagType.WallW) || properties.has(IsoFlagType.WallNW)
                || properties.has(IsoFlagType.WallNTrans) || properties.has(IsoFlagType.WallWTrans) || properties.has(IsoFlagType.HoppableN)
                || properties.has(IsoFlagType.HoppableW) || properties.has(IsoFlagType.TallHoppableN) || properties.has(IsoFlagType.TallHoppableW);
    }
}
