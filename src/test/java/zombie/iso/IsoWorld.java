package zombie.iso;

/** The tests' stand-in for the game's world: the loaded map a test puts there, none until it does. See IsoCell. */
public final class IsoWorld {
    public static IsoWorld instance = new IsoWorld();
    public IsoCell currentCell;
}
