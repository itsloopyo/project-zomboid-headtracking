package zombie.iso.sprite;

/** The tests' stand-in for a sprite the game draws with an object: see zombie.iso.IsoObject. */
public final class IsoSpriteInstance {
    public IsoSprite parentSprite;

    public IsoSpriteInstance(IsoSprite parentSprite) {
        this.parentSprite = parentSprite;
    }
}
