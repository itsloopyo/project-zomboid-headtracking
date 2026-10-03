package zombie.iso;

import zombie.core.textures.ColorInfo;
import zombie.util.list.PZArrayList;

public final class IsoGridSquare {
    public interface ILighting {
        ColorInfo lightInfo();
    }

    public final ILighting[] lighting = null;

    public PZArrayList<IsoObject> getObjects() { throw new UnsupportedOperationException("reference stub"); }
    public IsoObject getFloor() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isBlockedTo(IsoGridSquare other) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isWindowTo(IsoGridSquare other) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isSolid() { throw new UnsupportedOperationException("reference stub"); }
    public boolean isOutside() { throw new UnsupportedOperationException("reference stub"); }
    public boolean has(zombie.iso.SpriteDetails.IsoFlagType flag) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isSeen(int playerIndex) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isCouldSee(int playerIndex) { throw new UnsupportedOperationException("reference stub"); }
    public boolean isCanSee(int playerIndex) { throw new UnsupportedOperationException("reference stub"); }
    public ColorInfo getLightInfo(int playerIndex) { throw new UnsupportedOperationException("reference stub"); }
    public java.util.ArrayList<IsoMovingObject> getMovingObjects() { throw new UnsupportedOperationException("reference stub"); }
    public java.util.ArrayList<IsoMovingObject> getStaticMovingObjects() { throw new UnsupportedOperationException("reference stub"); }
}
