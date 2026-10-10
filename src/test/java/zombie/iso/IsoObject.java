package zombie.iso;

import zombie.iso.sprite.IsoSprite;

/** The tests' stand-in for a thing on a square of the map: its sprite. See zombie.core.properties.PropertyContainer. */
public class IsoObject {
    public float offsetX;
    public float offsetY;
    private final IsoSprite sprite;

    public IsoObject(IsoSprite sprite) {
        this.sprite = sprite;
    }

    public IsoSprite getSprite() {
        return sprite;
    }

    public IsoDirections getDir() {
        return IsoDirections.N;
    }
}
