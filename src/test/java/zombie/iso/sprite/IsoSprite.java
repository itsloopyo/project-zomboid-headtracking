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

    /** As the game reads Facing: N, S, W or E, and null for anything else. */
    public IsoDirections getFacing() {
        String facing = properties.get("Facing");
        return facing == null ? null : switch (facing) {
            case "N", "S", "W", "E" -> IsoDirections.valueOf(facing);
            default -> null;
        };
    }
}
