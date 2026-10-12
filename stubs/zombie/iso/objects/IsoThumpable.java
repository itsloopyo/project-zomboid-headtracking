package zombie.iso.objects;

import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;

public class IsoThumpable extends IsoObject {
    public boolean north;
    protected IsoSprite closedSprite;

    public boolean isDoor() { throw new UnsupportedOperationException("reference stub"); }
    public boolean IsOpen() { throw new UnsupportedOperationException("reference stub"); }
    public boolean getNorth() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isWindowN() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isWindowW() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isBarricaded() { throw new UnsupportedOperationException("reference stub"); }
    public IsoBarricade getBarricadeOnSameSquare() { throw new UnsupportedOperationException("reference stub"); }
    public IsoBarricade getBarricadeOnOppositeSquare() { throw new UnsupportedOperationException("reference stub"); }
}
