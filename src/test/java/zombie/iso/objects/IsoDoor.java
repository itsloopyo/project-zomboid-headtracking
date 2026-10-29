package zombie.iso.objects;

import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;

/** The tests' stand-in for a door. No test puts one on a square: RoomMap asks of every thing on a square whether it is one. */
public class IsoDoor extends IsoObject {
    public IsoDoor(IsoSprite sprite) {
        super(sprite);
    }
}
