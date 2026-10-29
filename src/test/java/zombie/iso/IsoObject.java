package zombie.iso;

import zombie.iso.sprite.IsoSprite;

/** The tests' stand-in for a thing on a square of the map: its sprite. See zombie.core.properties.PropertyContainer. */
public class IsoObject {
    public float offsetX;
    public float offsetY;
    private final IsoSprite sprite;

    /** The square it was put on: see IsoGridSquare.add. */
    IsoGridSquare square;

    public IsoObject(IsoSprite sprite) {
        this.sprite = sprite;
    }

    public IsoGridSquare getSquare() {
        return square;
    }

    public IsoSprite getSprite() {
        return sprite;
    }

    public IsoDirections getDir() {
        return IsoDirections.N;
    }

    /** What the game draws over the object, as the map hangs grime on a wall. */
    public IsoSprite overlaySprite;

    public IsoSprite getOverlaySprite() {
        return overlaySprite;
    }

    /** What the game draws with the object, as the map hangs pictures and grime on a wall. */
    public final java.util.ArrayList<zombie.iso.sprite.IsoSpriteInstance> attached = new java.util.ArrayList<>();

    public java.util.ArrayList<zombie.iso.sprite.IsoSpriteInstance> getAttachedAnimSprite() {
        return attached;
    }

    /** What the object holds, as a shop's case holds its cakes, or null for none. */
    public zombie.inventory.ItemContainer container;

    public zombie.inventory.ItemContainer getContainer() {
        return container;
    }
}
