package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoDirections;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;

/**
 * What the game hangs on a wall by the way it faces alone: a chalkboard, a notice, a map, a
 * pay phone, a deer's head, a seat in a cell. Such a sprite is moved as a wall object and given
 * a Facing, and marked for no wall at all, so nothing that reads the edge flags found its wall:
 * it stood in the middle of its square as a picture turned to the eye. Its wall is the one
 * behind the way it faces, and its sprite is painted there.
 *
 * What is flat by nature lies flat on that wall. What stands out from it is a model with its
 * back to that wall.
 */
final class WallObjects {
    private WallObjects() {}

    /** What the game calls something it moves as a thing hung on a wall. */
    private static final String HUNG = "WallObject";
    /** How high the foot of a chalkboard's tray is, in tiles, read off the sprites: a third of a level. */
    private static final float CHALK_FOOT = 0.78f;
    /** How high the foot of a deer's head's plaque is hung, in tiles, read off the sprites: its antlers reach to under the ceiling. */
    private static final float TROPHY_FOOT = 1.42f;
    /** Where on the back of a pay phone's sprite, in levels, the side of its hood gives its colour. */
    private static final float PHONE_DYE = 0.5f;

    /**
     * The wall of its square a sprite lies flat on: the one behind the way it faces, for a
     * thing the game hangs on a wall and marks for no wall and as filling none of its square.
     * WallHung.NONE for anything else: what has a wall flag is laid on that wall already, and
     * what is solid, or to rest on, stands out into its square.
     */
    static int flatOn(PropertyContainer properties, IsoDirections facing) {
        if (!HUNG.equals(properties.get("MoveType")) || properties.has(IsoFlagType.solid) || properties.has(IsoFlagType.solidtrans)
                || properties.has(IsoFlagType.bed) || properties.has(IsoFlagType.attachedN) || properties.has(IsoFlagType.attachedW)
                || properties.has(IsoFlagType.attachedE) || properties.has(IsoFlagType.attachedS) || properties.has(IsoFlagType.attachedFloor)) {
            return WallHung.NONE;
        }
        return WallHung.behind(facing);
    }

    /**
     * The model of a thing hung on a wall that stands out from it, with its back to the wall
     * behind the way it faces, or null.
     *
     * @param turns   quarter turns from facing north
     * @param along   how many squares the piece is long the way it faces
     * @param across  and across that
     */
    static Furniture model(IsoSprite sprite, String called, String group, PropertyContainer properties, int turns, int along, int across) {
        if (!HUNG.equals(properties.get("MoveType"))) {
            return null;
        }
        boolean single = along == 1 && across == 1;
        if (called.equals("Phones") && group.equals("Pay") && single) {
            return new Furniture(sprite, "pay_phone", turns).dyeAtBack(1 << Models.PAINT, PHONE_DYE).stand(0f, 0f, true);
        }
        if (called.equals("Deer Trophy") && group.equals("Mounted") && single) {
            return new Furniture(sprite, "deer_trophy", turns).stand(0f, 0f, true).raise(TROPHY_FOOT);
        }
        // The game gives the height of a cell's seat as where things put on it rest, and of a bunk as its surface.
        // ItemHeight is a mod's to write, and the game's own reading of it leaves anything but 0 to 127 at 0.
        if (called.equals("Seat") && group.equals("Hanging") && single && properties.getItemHeight() > 0) {
            float top = properties.getItemHeight() / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
            Furniture seat = new Furniture(sprite, "cell_seat", turns).stand(0f, 0f, true);
            return seat.raise(top - seat.model.height() * seat.scaleUp);
        }
        if (called.equals("Bed") && group.equals("Prisoners") && along == 1 && across == 2 && properties.getSurface() > 0) {
            float top = properties.getSurface() / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
            Furniture bunk = new Furniture(sprite, "cell_bunk", turns).stand(0f, 0f, true);
            return bunk.raise(top - bunk.model.height() * bunk.scaleUp);
        }
        // A chalkboard that faces north or west is painted from behind, a plain board: its face, and what is chalked on
        // it, the game's view never shows, so it is the model of a blank one. One facing south or east lies flat on its
        // wall as its sprite paints it.
        if (called.equals("Board") && group.equals("Chalk") && along == 1 && (across == 3 || across == 4) && (turns == 0 || turns == 3)) {
            return new Furniture(sprite, across == 3 ? "chalkboard_3" : "chalkboard_4", turns).stand(0f, 0f, true).raise(CHALK_FOOT);
        }
        return null;
    }
}
