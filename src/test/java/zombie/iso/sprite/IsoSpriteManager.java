package zombie.iso.sprite;

/** The tests' stand-in for the game's table of every sprite it has read, by name: empty until a test puts its sprites in. See IsoSprite. */
public final class IsoSpriteManager {
    public static final IsoSpriteManager instance = new IsoSpriteManager();
    public final java.util.HashMap<String, IsoSprite> namedMap = new java.util.HashMap<>();
}
