package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.SpriteDetails.IsoObjectType;

/** Which edge of its square a sprite stands on, by what the game marks it with: as a wall, a fence that is hopped over, a door, a window or a thing fixed to it. */
final class Edges {
    private Edges() {}

    static boolean west(PropertyContainer properties, IsoObjectType type) {
        return properties.has(IsoFlagType.collideW) || properties.has(IsoFlagType.WallW)
                || properties.has(IsoFlagType.WallWTrans) || properties.has(IsoFlagType.DoorWallW)
                || properties.has(IsoFlagType.WindowW) || properties.has(IsoFlagType.windowW)
                || properties.has(IsoFlagType.doorW) || properties.has(IsoFlagType.cutW)
                || properties.has(IsoFlagType.HoppableW) || properties.has(IsoFlagType.TallHoppableW)
                || properties.has(IsoFlagType.attachedW)
                || type == IsoObjectType.doorFrW || type == IsoObjectType.doorW
                || type == IsoObjectType.windowFW || type == IsoObjectType.curtainW;
    }

    static boolean north(PropertyContainer properties, IsoObjectType type) {
        return properties.has(IsoFlagType.collideN) || properties.has(IsoFlagType.WallN)
                || properties.has(IsoFlagType.WallNTrans) || properties.has(IsoFlagType.DoorWallN)
                || properties.has(IsoFlagType.WindowN) || properties.has(IsoFlagType.windowN)
                || properties.has(IsoFlagType.doorN) || properties.has(IsoFlagType.cutN)
                || properties.has(IsoFlagType.HoppableN) || properties.has(IsoFlagType.TallHoppableN)
                || properties.has(IsoFlagType.attachedN)
                || type == IsoObjectType.doorFrN || type == IsoObjectType.doorN
                || type == IsoObjectType.windowFN || type == IsoObjectType.curtainN;
    }
}
