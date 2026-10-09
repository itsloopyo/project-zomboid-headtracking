package zombie.iso.sprite;

import java.util.HashMap;
import java.util.Map;

/** The tests' stand-in for the squares the game lays a piece on, one level of them. See PropertyContainer. */
public final class IsoSpriteGrid {
    private final int width, height;
    private final Map<IsoSprite, int[]> where = new HashMap<>();

    public IsoSpriteGrid(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void put(IsoSprite sprite, int x, int y) {
        where.put(sprite, new int[] {x, y});
        sprite.grid = this;
    }

    public IsoSprite getSprite(int x, int y) {
        for (Map.Entry<IsoSprite, int[]> each : where.entrySet()) {
            if (each.getValue()[0] == x && each.getValue()[1] == y) {
                return each.getKey();
            }
        }
        return null;
    }

    public int getSpriteGridPosX(IsoSprite sprite) {
        return where.get(sprite)[0];
    }

    public int getSpriteGridPosY(IsoSprite sprite) {
        return where.get(sprite)[1];
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getLevels() {
        return 1;
    }
}
