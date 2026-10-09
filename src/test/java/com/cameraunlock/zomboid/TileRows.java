package com.cameraunlock.zomboid;

import java.util.HashMap;
import java.util.Map;
import zombie.core.properties.PropertyContainer;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteGrid;

/** Sprites made from rows of the game's tile definitions, for the tests that ask what the mod makes of one. */
final class TileRows {
    private TileRows() {}

    /** A sprite from a row: its name, then each property and its value, a flag with none, all split by bars. */
    static IsoSprite sprite(String row) {
        String[] cells = row.split("\\|", -1);
        Map<String, String> all = new HashMap<>();
        for (int i = 1; i + 1 < cells.length; i += 2) {
            all.put(cells[i], cells[i + 1]);
        }
        return new IsoSprite(cells[0], new PropertyContainer(all));
    }

    /** Lays the sprites of one piece on its squares, each at the SpriteGridPos its row gives, as the game lays a piece it reads. */
    static void piece(int width, int height, IsoSprite... squares) {
        IsoSpriteGrid grid = new IsoSpriteGrid(width, height);
        for (IsoSprite square : squares) {
            String[] at = square.getProperties().get("SpriteGridPos").split(",");
            grid.put(square, Integer.parseInt(at[0]), Integer.parseInt(at[1]));
        }
    }
}
