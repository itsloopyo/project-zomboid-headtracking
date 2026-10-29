package zombie.iso.objects;

import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;

/** The tests' stand-in for a thing built by a player, such as a barricade or a built wall. No test puts one on a square: RoomMap asks of every thing on a square whether it is one. */
public class IsoThumpable extends IsoObject {
    public IsoThumpable(IsoSprite sprite) {
        super(sprite);
    }
}
