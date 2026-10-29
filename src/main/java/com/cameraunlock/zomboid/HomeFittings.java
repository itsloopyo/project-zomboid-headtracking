package com.cameraunlock.zomboid;

import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoDirections;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteGrid;

/**
 * Fittings of homes, kitchens and bathrooms that Furniture has a model for besides its own: a
 * china cabinet, low tables, kitchen machines, steel sinks and cell toilets, wall shelving, a
 * fireplace's firebox and hearth, chairs and stoves, and the board the game lays over a counter
 * as its top. Tried before Furniture's own rules, so nothing here may be a piece they tell by the same signs.
 */
final class HomeFittings {
    private HomeFittings() {}

    /** How high the top of a counter's model is, and how thick the board laid over it, in metres: as tools/models/furniture.py builds them. */
    static final float WORKTOP_HIGH = 0.90f, WORKTOP_THICK = 0.035f;
    /** How far the board's top stands over the counter's own, in metres: enough to cover it, so the two are never drawn through each other. */
    static final float WORKTOP_PROUD = 0.005f;
    /** How high, in metres, the part of a model is that the game gives the height of: the china cabinet's cornice, a sink's rim, a stove's top, a rocking chair's seat. */
    static final float CHINA_TOP = 1.88f, SINK_RIM = 35f / 96f * 2.45f, STOVE_TOP = 34f / 96f * 2.45f, ROCKER_SEAT = 0.40f;
    /** How high a drinking fountain's rim is on its wall, in tiles, and how high it is in its model, in metres. */
    static final float HANGING_RIM = 0.85f, FOUNTAIN_RIM = 0.2f;
    /** Where on its sprite, in levels, a chest's lid, an ottoman's cushion, a stove's body and a hearth give their colour. */
    private static final float CHEST_DYE = 0.15f, OTTOMAN_DYE = 0.12f, STOVE_DYE = 0.3f, HEARTH_DYE = 0.02f;
    /** How far out from its wall, as a share of the square, the hearth before a fire gives its colour. */
    private static final float HEARTH_OUT = 0.2f;
    private static final String COUNTER_TOPS = "fixtures_counters_01_", FIREPLACES = "fixtures_fireplaces_01_";
    /** The places on its sheet of the boards the game lays over counters: straight ones, then corners. */
    private static final int FIRST_TOP = 80, LAST_TOP = 142;
    /** The places of the fireplaces on their sheet: the fire and its hearth, before a north wall or a west one. */
    private static final int LAST_FIRE = 5;

    /**
     * The model of the board laid over a counter that has the model `counter`: one built where the counter's
     * own top is in it. Null for a counter that has no such board.
     */
    static String worktopOver(String counter) {
        return switch (counter) {
            case "counter" -> "worktop";
            case "counter_sink" -> "worktop_sink";
            case "counter_corner" -> "worktop_corner";
            default -> null;
        };
    }

    /** What a sprite is of these, or null. called is the game's CustomName, null where it gives none. */
    static Furniture of(IsoSprite sprite, String name, String called, PropertyContainer properties) {
        if (name == null) {
            return null;
        }
        IsoSpriteGrid grid = sprite.getSpriteGrid();
        int at = Sheets.place(name);
        if (called == null && grid == null && name.startsWith(COUNTER_TOPS) && at >= FIRST_TOP && at <= LAST_TOP && properties.has("Countertop")) {
            // Nothing of its own: it is the board over the counter it is laid on, which FurnishingBuilder.addCountertop stands.
            Furniture top = new Furniture(sprite, null, 0);
            top.countertop = true;
            return top;
        }
        if ("Fireplace".equals(called) && grid == null && name.startsWith(FIREPLACES) && at >= 0 && at <= LAST_FIRE
                && (properties.has(IsoFlagType.collideN) || properties.has(IsoFlagType.collideW))) {
            // The game gives the fire no facing: it is before the wall it is let into.
            boolean north = properties.has(IsoFlagType.collideN);
            return new Furniture(sprite, "fireplace", north ? 2 : 1)
                    .dye(1 << Models.WORKTOP, north ? 0.5f : HEARTH_OUT, north ? HEARTH_OUT : 0.5f, HEARTH_DYE).stand(0f, 0f, true);
        }
        IsoDirections facing = sprite.getFacing();
        if (called == null || facing == null || grid != null && grid.getLevels() != 1) {
            return null;
        }
        int turns = Furniture.turnsOf(facing);
        boolean alongY = (turns & 1) == 0;
        int width = grid == null ? 1 : grid.getWidth(), height = grid == null ? 1 : grid.getHeight();
        int along = alongY ? height : width, across = alongY ? width : height;
        boolean single = grid == null, pair = along == 1 && across == 2;
        String group = properties.get("GroupName") == null ? "" : properties.get("GroupName");
        String holds = properties.get("container") == null ? "" : properties.get("container");
        int surface = Surfaces.surface(properties);
        float surfaceUp = surface / WorldScene.SURFACE_PIXELS_PER_LEVEL * ViewCamera.LEVEL_HEIGHT;
        boolean onTable = properties.has("IsTableTop");
        if (called.equals("Cabinet") && group.equals("China") && holds.equals("dishescabinet") && pair && surface > 0) {
            return new Furniture(sprite, "china_cabinet", turns).stand(surfaceUp, CHINA_TOP, true);
        }
        if (called.equals("Table") && group.equals("Fancy Low Glass") && pair && surface > 0) {
            Furniture table = new Furniture(sprite, "low_glass_table", turns);
            return table.stand(surfaceUp, table.model.height(), false);
        }
        if (called.equals("Low Table") && group.equals("Long Fancy") && pair && surface > 0) {
            Furniture table = new Furniture(sprite, "sabre_table", turns);
            return table.stand(surfaceUp, table.model.height(), false);
        }
        // A trailer's table lies along the way it faces, and the model lies across: it is turned a quarter more.
        if (called.equals("Table") && group.equals("Lightwood Trailer") && along == 2 && across == 1 && surface > 0) {
            Furniture table = new Furniture(sprite, "trailer_table", turns + 1 & 3);
            return table.stand(surfaceUp, table.model.height(), false);
        }
        // What stands on a counter says how high the counter is.
        if (onTable && single && surface > 0) {
            String machine = called.equals("X-press") && group.equals("Coffee") ? "coffee_maker" : called.equals("Toaster") ? "toaster"
                    : called.equals("Deluxe") && group.equals("Espresso") ? "espresso_machine" : null;
            if (machine != null) {
                return new Furniture(sprite, machine, turns).stand(0f, 0f, false).raise(surfaceUp);
            }
        }
        if (called.equals("Chest") && group.equals("Small") && holds.equals("locker") && single && surface > 0) {
            Furniture chest = new Furniture(sprite, "foot_locker", turns).dye(1 << Models.PAINT, CHEST_DYE);
            return chest.stand(surfaceUp, chest.model.height(), false);
        }
        // The sprites facing south and west are painted with the bowl where the model's -x side turns to, those facing north and east with it at +x.
        if (called.equals("Toilet") && group.equals("Chemical") && single) {
            return new Furniture(sprite, turns == 2 || turns == 3 ? "steel_toilet_left" : "steel_toilet", turns).stand(0f, 0f, true);
        }
        if (called.equals("Toilet") && group.equals("Wooden") && single && surface > 0) {
            Furniture seat = new Furniture(sprite, "outhouse_seat", turns);
            return seat.stand(surfaceUp, seat.model.height(), true);
        }
        if (called.equals("Shelves") && holds.equals("shelves") && single && name.startsWith("furniture_shelving") && surface == 0
                && (group.equals("Big Wall") || group.equals("Big Wall Corner"))) {
            return new Furniture(sprite, group.equals("Big Wall") ? "wall_shelving" : "wall_shelving_corner", turns)
                    .meet(0f, 0f, group.equals("Big Wall"));
        }
        if (called.equals("Oven") && group.equals("Large Modern") && holds.equals("stove") && pair) {
            return new Furniture(sprite, "double_oven", turns).stand(0f, 0f, true);
        }
        if (called.equals("Sink") && group.equals("Large Industrial") && single && surface > 0) {
            return new Furniture(sprite, "utility_sink", turns).stand(surfaceUp, SINK_RIM, true);
        }
        // The game's Fancy Hanging sinks are stainless drinking fountains on a wall.
        if (called.equals("Sink") && group.equals("Fancy Hanging") && single && surface == 0) {
            Furniture fountain = new Furniture(sprite, "drinking_fountain", turns).stand(0f, 0f, true);
            return fountain.raise(HANGING_RIM - FOUNTAIN_RIM * fountain.scaleUp);
        }
        if (called.equals("Ottoman") && single && surface > 0) {
            Furniture ottoman = new Furniture(sprite, "ottoman", turns).dye(1 << Models.CLOTH, OTTOMAN_DYE);
            return ottoman.stand(surfaceUp, ottoman.model.height(), false);
        }
        if (called.equals("Industrial 2000") && group.equals("Fryers Club") && single) {
            return new Furniture(sprite, "deep_fryer", turns).stand(0f, 0f, true);
        }
        if (called.equals("Industrial") && group.equals("Bake-O-Matic") && single) {
            return new Furniture(sprite, "hood_dishwasher", turns).stand(0f, 0f, true);
        }
        if (called.equals("Freezer") && group.equals("Chest") && single && surface > 0) {
            Furniture freezer = new Furniture(sprite, "freezer_chest", turns);
            return freezer.stand(surfaceUp, freezer.model.height(), false);
        }
        if (called.equals("Oven") && group.equals("Antique") && holds.equals("woodstove") && single && surface > 0) {
            return new Furniture(sprite, "wood_stove", turns).dye(1 << Models.PAINT, STOVE_DYE).stand(surfaceUp, STOVE_TOP, false);
        }
        if (called.equals("Chair") && (group.equals("Rocking") || group.equals("Rocking 2")) && single && surface > 0) {
            return new Furniture(sprite, group.equals("Rocking") ? "rocking_chair" : "rocking_chair_black", turns).stand(0f, 0f, false)
                    .seat(surfaceUp, ROCKER_SEAT);
        }
        return null;
    }
}
