package zombie.iso.objects;

import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;

/** The tests' stand-in for a light the game switches: one the tests lay is never switched on. */
public class IsoLightSwitch extends IsoObject {
    public IsoLightSwitch(IsoSprite sprite) {
        super(sprite);
    }

    public boolean isActivated() {
        return false;
    }
}
