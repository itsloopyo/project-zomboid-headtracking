package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoDirections;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;

/**
 * What the game hangs on a wall by the way it faces alone: a chalkboard, a notice, a map, a
 * pay phone, a deer's head, a seat in a cell, a security desk's monitors. Such a sprite is moved
 * as a wall object and given a Facing, and marked for no wall at all, so nothing that reads the
 * edge flags found its wall: it stood in the middle of its square as a picture turned to the eye.
 * Its wall is the one behind the way it faces, and its sprite is painted there.
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
    /** How high the lowest point of a house's satellite dish is hung, in tiles: its wall plate a metre up, read off the sprites. */
    private static final float DISH_FOOT = 0.9f;
    /** How high the lowest point of a shop's shelves on a wall is, the foot of the lower board's brackets, in metres: its boards
     * are where the sprites paint them, 0.89 and 1.58 m up. */
    private static final float SHOP_SHELF_FOOT = 0.63f;
    /** How high over the back of its square the sprite of a shop's shelves paints the top of its upper board, in levels. */
    private static final float SHOP_SHELF_DYE = 0.64f;
    /** How high the foot of a clothes shop's lower rail board is, in metres, and how high over the back of its square its
     * sprite paints the upper board, in levels. */
    private static final float CLOTHES_RAIL_FOOT = 0.82f, CLOTHES_RAIL_DYE = 0.72f;
    /** Where on the back of a pay phone's sprite, in levels, the side of its hood gives its colour. */
    private static final float PHONE_DYE = 0.5f;

    /**
     * The wall of its square a sprite lies flat on: the one behind the way it faces, for a
     * thing the game hangs on a wall and marks for no wall and as filling none of its square, nor
     * as fixed to the floor (FloorMarks).
     * WallHung.NONE for anything else: what has a wall flag is laid on that wall already, and
     * what is solid, or to rest on, stands out into its square.
     */
    static int flatOn(String name, PropertyContainer properties, IsoDirections facing) {
        if (!HUNG.equals(properties.get("MoveType")) || properties.has(IsoFlagType.solid) || properties.has(IsoFlagType.solidtrans)
                || properties.has(IsoFlagType.bed) || properties.has(IsoFlagType.attachedN) || properties.has(IsoFlagType.attachedW)
                || properties.has(IsoFlagType.attachedE) || properties.has(IsoFlagType.attachedS) || FloorMarks.onFloor(name, properties)) {
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
        // The map puts a house's dish on a wall, and as often on a roof with no wall at all, painted where it would
        // hang on the wall behind it over the roof's level. There it is on a post, stood on the roof as the game's
        // view draws it.
        if (called.equals("Dish") && group.equals("Satellite") && single) {
            Furniture dish = new Furniture(sprite, "satellite_dish", turns).stand(0f, 0f, true).raise(DISH_FOOT);
            dish.onRoof = true;
            dish.posted = Models.get("satellite_dish_post");
            return dish;
        }
        // A shop's shelves on its wall, the lower board's foot under it on its brackets.
        if (called.equals("Shelves") && (group.equals("Beige Wall Shop") || group.equals("Zippee Wall")) && single) {
            return new Furniture(sprite, group.equals("Zippee Wall") ? "zippee_wall_shelves" : "shop_wall_shelves", turns).stand(0f, 0f, true)
                    .raise(SHOP_SHELF_FOOT).dyeAtBack(1 << Models.PAINT, SHOP_SHELF_DYE);
        }
        // A clothes shop's rails on its wall, the lower board's foot under it.
        if (called.equals("Shelves") && group.equals("Clothing Shop") && single) {
            return new Furniture(sprite, "clothes_wall_rails", turns).stand(0f, 0f, true).raise(CLOTHES_RAIL_FOOT)
                    .dyeAtBack(1 << Models.WOOD, CLOTHES_RAIL_DYE);
        }
        if (called.equals("Monitors") && group.equals("Security Wall") && single) {
            return new Furniture(sprite, "security_monitors", turns).stand(0f, 0f, true);
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
