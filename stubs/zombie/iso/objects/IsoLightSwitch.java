package zombie.iso.objects;

import zombie.iso.IsoObject;

public class IsoLightSwitch extends IsoObject {
    public boolean lightRoom;

    public boolean isActivated() {
        return false;
    }

    public boolean hasLightBulb() { throw new UnsupportedOperationException("reference stub"); }
    public void setBulbItemRaw(String item) { throw new UnsupportedOperationException("reference stub"); }
    public boolean setActive(boolean active, boolean setActiveBoolOnly, boolean ignoreSwitchCheck) { throw new UnsupportedOperationException("reference stub"); }
}
