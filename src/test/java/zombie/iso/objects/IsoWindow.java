package zombie.iso.objects;

import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;

/** The tests' stand-in for a window. No test puts one on a square: RoomMap asks of every thing on a square whether it is one. */
public class IsoWindow extends IsoObject {
    public IsoWindow(IsoSprite sprite) {
        super(sprite);
    }
}
