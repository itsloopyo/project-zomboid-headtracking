package zombie.iso.objects;

import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;

public class IsoDoor extends IsoObject {
    public boolean north;
    private IsoSprite closedSprite;

    public boolean IsOpen() { throw new UnsupportedOperationException("reference stub"); }
    public boolean getNorth() { throw new UnsupportedOperationException("reference stub"); }
    public IsoDoor HasCurtains() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isCurtainOpen() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isBarricaded() { throw new UnsupportedOperationException("reference stub"); }
    public IsoBarricade getBarricadeOnSameSquare() { throw new UnsupportedOperationException("reference stub"); }
    public IsoBarricade getBarricadeOnOppositeSquare() { throw new UnsupportedOperationException("reference stub"); }
}
