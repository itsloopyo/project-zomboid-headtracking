package zombie.iso.objects;

import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;

/** The tests' stand-in for a hearth or a burn barrel: one the tests lay is never lit. */
public class IsoFireplace extends IsoObject {
    public IsoFireplace(IsoSprite sprite) {
        super(sprite);
    }

    public boolean isLit() {
        return false;
    }
}
