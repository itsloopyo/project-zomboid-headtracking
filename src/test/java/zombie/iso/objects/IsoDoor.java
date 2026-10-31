package zombie.iso.objects;

import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;

/** The tests' stand-in for a door: shut, on the edge a test puts it on, never barricaded. RoomMap asks of every thing on a square whether it is one. */
public class IsoDoor extends IsoObject {
    public boolean north;

    public IsoDoor(IsoSprite sprite) {
        super(sprite);
    }

    public boolean getNorth() {
        return north;
    }

    public boolean IsOpen() {
        return false;
    }

    public boolean isBarricaded() {
        return false;
    }
}
