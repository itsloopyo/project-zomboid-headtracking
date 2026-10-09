package zombie.iso.sprite;

import zombie.core.properties.PropertyContainer;
import zombie.iso.IsoDirections;

/** The tests' stand-in for the game's sprite: a name, its properties and the grid of a piece it is a square of. See PropertyContainer. */
public final class IsoSprite {
    private final String name;
    private final PropertyContainer properties;
    IsoSpriteGrid grid;

    public IsoSprite(String name, PropertyContainer properties) {
        this.name = name;
        this.properties = properties;
    }

    public PropertyContainer getProperties() {
        return properties;
    }

    public String getName() {
        return name;
    }

    public int getFrameCount() {
        return 1;
    }

    public IsoSpriteGrid getSpriteGrid() {
        return grid;
    }

    public IsoDirections getFacing() {
        String facing = properties.get("Facing");
        return facing == null ? null : IsoDirections.valueOf(facing);
    }
}
